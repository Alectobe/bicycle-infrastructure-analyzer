package com.example.courseproject.domain.model

/**
 * Сохранённый результат анализа территории — элемент истории расчётов.
 * Используется для функции сравнения районов между собой.
 */
data class AreaEvaluation(
    val id: String,
    val name: String,
    val boundingBox: BoundingBox,
    val score: QualityScore,
    val timestamp: Long,
)
