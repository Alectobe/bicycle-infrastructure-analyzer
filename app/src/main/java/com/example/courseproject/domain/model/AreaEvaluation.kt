package com.example.courseproject.domain.model

/**
 * Сохранённый результат анализа территории — элемент истории расчётов.
 * Используется для функции сравнения районов между собой.
 *
 * Хранит только порядковый номер сохранения; читаемое название («Область N»
 * либо «Area N» — в зависимости от языка интерфейса) формируется
 * presentation-слоем из локализованного шаблона.
 */
data class AreaEvaluation(
    val id: String,
    /** Порядковый номер сохранения; UI использует его для формирования имени. */
    val index: Int,
    val boundingBox: BoundingBox,
    val score: QualityScore,
    val timestamp: Long,
)
