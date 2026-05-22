package com.example.courseproject.domain.model

/**
 * Типизированная ошибка анализа территории. Используется в состоянии
 * экрана; presentation-слой формирует по типу локализованный текст.
 */
sealed class AnalysisError {
    /** Сбой сетевого соединения с Overpass API. */
    data object NetworkUnavailable : AnalysisError()

    /** Сервер Overpass API вернул HTTP-ошибку с указанным кодом. */
    data class HttpError(val code: Int) : AnalysisError()

    /** Сервер Overpass API вернул пустой ответ. */
    data object EmptyResponse : AnalysisError()

    /** Не удалось разобрать ответ Overpass API. */
    data object ParseError : AnalysisError()

    /** Неизвестная или прочая ошибка анализа. */
    data object Unknown : AnalysisError()
}
