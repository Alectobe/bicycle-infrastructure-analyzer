package com.example.courseproject.data.remote

import com.example.courseproject.domain.model.AnalysisError
import com.example.courseproject.domain.model.AnalysisException
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
 *
 * Сбои оборачиваются в [AnalysisException] с типизированной причиной
 * [AnalysisError], без формирования пользовательских сообщений — это
 * остаётся ответственностью presentation-слоя.
 *
 * Что такое Overpass API. Это бесплатный публичный сервис, который умеет
 * выполнять произвольные запросы к данным OSM на специальном языке Overpass QL.
 * Мы шлём ему запрос «дай все highway в этом прямоугольнике плюс их узлы» —
 * он возвращает JSON-ответ с нужной структурой.
 */
class OverpassApi(
    private val endpoint: String = DEFAULT_ENDPOINT,    // URL сервиса Overpass API
    private val client: OkHttpClient = defaultClient(), // HTTP-клиент с настроенными таймаутами
) {
    /**
     * Формирует Overpass QL-запрос дорожной сети для ограничивающего прямоугольника.
     *
     * Запрос состоит из четырёх частей:
     *   – заголовок [out:json][timeout:90] — формат ответа JSON, серверный таймаут 90 с;
     *   – тело (way["highway"](bbox)); — все пути с тегом highway в указанной области;
     *   – «спуск» (._;>;); — добавить к результату все узлы, на которые ссылаются эти пути;
     *   – out body; — вернуть тело каждого элемента (теги, координаты).
     */
    fun buildQuery(bbox: BoundingBox): String {
        // Собираем координаты bbox в формат Overpass: «south,west,north,east».
        val box = "${bbox.south},${bbox.west},${bbox.north},${bbox.east}"
        // Шаблон Overpass QL — обычная многострочная строка с подстановкой bbox.
        return """
            [out:json][timeout:90];
            (way["highway"]($box););
            (._;>;);
            out body;
        """.trimIndent()
    }

    /**
     * Выполняет запрос к Overpass API и возвращает текст JSON-ответа.
     *
     * Сетевая операция выполняется на пуле потоков ввода-вывода (Dispatchers.IO) —
     * это требование для блокирующих сетевых вызовов из корутины, чтобы не
     * заблокировать главный поток приложения.
     */
    suspend fun fetch(bbox: BoundingBox): String = withContext(Dispatchers.IO) {
        // Собираем HTTP-запрос: метод POST на endpoint с заголовком User-Agent
        // и телом в формате application/x-www-form-urlencoded (FormBody).
        // В теле один параметр data=<текст Overpass QL-запроса>.
        val request = Request.Builder()
            .url(endpoint)
            .header("User-Agent", USER_AGENT)               // обязательное условие сервера Overpass
            .post(FormBody.Builder().add("data", buildQuery(bbox)).build())
            .build()
        try {
            // .execute().use { … } — выполняет запрос и автоматически закрывает Response в конце.
            client.newCall(request).execute().use { response ->
                // Если код ответа не 2xx — бросаем типизированную ошибку с HTTP-кодом.
                if (!response.isSuccessful) {
                    throw AnalysisException(AnalysisError.HttpError(response.code))
                }
                // Тело ответа — текст JSON. Если оно null (что почти невозможно при 2xx),
                // тоже бросаем типизированную ошибку.
                response.body?.string()
                    ?: throw AnalysisException(AnalysisError.EmptyResponse)
            }
        } catch (e: IOException) {
            // IOException ловит «нет интернета» / «таймаут» / «DNS не разрешился» —
            // оборачиваем в нашу типизированную ошибку сетевого сбоя.
            throw AnalysisException(AnalysisError.NetworkUnavailable, e)
        }
    }

    private companion object {
        /** Адрес основного публичного сервера Overpass API. */
        const val DEFAULT_ENDPOINT = "https://overpass-api.de/api/interpreter"

        /**
         * User-Agent с осмысленным идентификатором приложения. Без этого Overpass API
         * отклоняет запросы с HTTP 406 (защита сервера от безымянных ботов).
         */
        const val USER_AGENT = "BicycleInfrastructureAnalyzer/1.0 (Android course project)"

        /** Конструктор HTTP-клиента по умолчанию с настроенными таймаутами. */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)           // 30 с на установление TCP-соединения
            .readTimeout(120, TimeUnit.SECONDS)             // 120 с на чтение ответа (большие области отвечают долго)
            .build()
    }
}
