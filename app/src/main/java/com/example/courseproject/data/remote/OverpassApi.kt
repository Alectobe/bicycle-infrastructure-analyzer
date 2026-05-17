package com.example.courseproject.data.remote

import com.example.courseproject.domain.model.BoundingBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Клиент Overpass API — веб-сервиса запросов к базе данных OpenStreetMap.
 * Возвращает «сырой» JSON-ответ; его разбор выполняется в слое репозитория.
 */
class OverpassApi(
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val client: OkHttpClient = defaultClient(),
) {
    /** Формирует Overpass QL-запрос дорожной сети для ограничивающего прямоугольника. */
    fun buildQuery(bbox: BoundingBox): String {
        val box = "${bbox.south},${bbox.west},${bbox.north},${bbox.east}"
        return """
            [out:json][timeout:90];
            (way["highway"]($box););
            (._;>;);
            out body;
        """.trimIndent()
    }

    /** Выполняет запрос к Overpass API и возвращает текст JSON-ответа. */
    suspend fun fetch(bbox: BoundingBox): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint)
            .header("User-Agent", USER_AGENT)
            .post(FormBody.Builder().add("data", buildQuery(bbox)).build())
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw OverpassException("Overpass API вернул код ${response.code}")
                }
                response.body?.string()
                    ?: throw OverpassException("Overpass API вернул пустой ответ")
            }
        } catch (e: IOException) {
            throw OverpassException("Не удалось получить данные OpenStreetMap: ${e.message}", e)
        }
    }

    private companion object {
        const val DEFAULT_ENDPOINT = "https://overpass-api.de/api/interpreter"

        // Overpass API отклоняет запросы с обобщённым User-Agent (HTTP 406),
        // поэтому указывается содержательный идентификатор приложения.
        const val USER_AGENT = "BicycleInfrastructureAnalyzer/1.0 (Android course project)"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
