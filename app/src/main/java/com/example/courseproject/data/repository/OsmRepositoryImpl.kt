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
 * с типизированной причиной [AnalysisError].
 */
class OsmRepositoryImpl(
    private val api: OverpassApi,
    private val cache: OsmCache,
    private val gson: Gson = Gson(),
) : OsmRepository {

    override suspend fun loadOsmData(bbox: BoundingBox, forceRefresh: Boolean): OsmData {
        val cached = if (forceRefresh) null else cache.read(bbox)
        val rawJson = cached ?: api.fetch(bbox).also { cache.write(bbox, it) }
        val dto = try {
            gson.fromJson(rawJson, OverpassResponseDto::class.java)
        } catch (e: JsonSyntaxException) {
            throw AnalysisException(AnalysisError.ParseError, e)
        }
        return dto?.toOsmData() ?: OsmData.EMPTY
    }
}
