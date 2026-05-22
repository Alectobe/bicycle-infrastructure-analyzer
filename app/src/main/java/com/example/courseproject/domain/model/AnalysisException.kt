package com.example.courseproject.domain.model

/**
 * Ошибка анализа территории. Несёт типизированную причину ([error]),
 * по которой presentation-слой формирует локализованное сообщение.
 *
 * Используется вместо строкового сообщения, чтобы слой данных не знал,
 * на каком языке будет показана ошибка пользователю.
 */
class AnalysisException(
    val error: AnalysisError,
    cause: Throwable? = null,
) : Exception("Analysis failed: $error", cause)
