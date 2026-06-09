package com.example.courseproject.data.repository

import com.example.courseproject.data.cache.OsmCache
import com.example.courseproject.data.mapper.toOsmData
import com.example.courseproject.data.remote.OverpassApi
import com.example.courseproject.data.remote.dto.OverpassResponseDto
import com.example.courseproject.domain.model.AnalysisError
import com.example.courseproject.domain.model.AnalysisException
import com.example.courseproject.domain.model.BoundingBox
import com.example.courseproject.domain.model.OsmData
import com.example.courseproject.domain.repository.OsmRepository
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

/**
 * Реализация [OsmRepository]: данные берутся из локального кэша, а при его
 * отсутствии (или при forceRefresh) запрашиваются у Overpass API и кэшируются.
 *
 * Ошибки парсинга и сетевые сбои оборачиваются в [AnalysisException]
 * с типизированной причиной [AnalysisError]; presentation-слой по типу
 * формирует локализованное сообщение для пользователя.
 *
 * Реализация подключается к доменному интерфейсу через DI ([com.example.courseproject.di.AppContainer]):
 * сам алгоритм оценки про эту конкретную реализацию ничего не знает.
 */
class OsmRepositoryImpl(
    private val api: OverpassApi,        // HTTP-клиент к Overpass API
    private val cache: OsmCache,         // локальный файловый кэш
    private val gson: Gson = Gson(),     // парсер JSON
) : OsmRepository {

    override suspend fun loadOsmData(bbox: BoundingBox, forceRefresh: Boolean): OsmData {
        // [1] Если forceRefresh не запрошен — пробуем взять JSON из кэша по ключу области.
        //     Если в кэше есть запись — берём её и пропускаем сеть.
        val cached = if (forceRefresh) null else cache.read(bbox)
        // [2] Если в кэше пусто — запрашиваем у Overpass API и параллельно сохраняем в кэш.
        //     also { … } выполняет блок-побочный эффект, возвращая исходное значение строки.
        val rawJson = cached ?: api.fetch(bbox).also { cache.write(bbox, it) }
        // [3] Парсим текст JSON в DTO через Gson. Если JSON битый — кидаем типизированную
        //     ошибку разбора, чтобы UI показал понятное сообщение.
        val dto = try {
            gson.fromJson(rawJson, OverpassResponseDto::class.java)
        } catch (e: JsonSyntaxException) {
            throw AnalysisException(AnalysisError.ParseError, e)
        }
        // [4] Преобразуем DTO в доменную модель через extension-функцию toOsmData().
        //     Если Gson вернул null (пустой ответ) — возвращаем пустую модель.
        return dto?.toOsmData() ?: OsmData.EMPTY
    }
}
