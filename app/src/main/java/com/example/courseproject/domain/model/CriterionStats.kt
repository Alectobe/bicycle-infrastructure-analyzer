package com.example.courseproject.domain.model

/**
 * Структурированная статистика, полученная при расчёте критерия.
 * Хранится в [CriterionScore] и используется presentation-слоем для
 * формирования локализованного пояснения.
 *
 * Числитель и знаменатель имеют разный физический смысл в зависимости
 * от критерия:
 *   – S, N, V — длины в метрах ([isCount] = false);
 *   – I, P — количества ([isCount] = true).
 *
 * [componentCount] используется только критерием N — это количество
 * обособленных участков низкострессовой сети.
 */
data class CriterionStats(
    val numerator: Double,
    val denominator: Double,
    val isCount: Boolean,
    val componentCount: Int = 0,
)
