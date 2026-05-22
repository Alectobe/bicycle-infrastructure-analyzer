package com.example.courseproject.domain.model

/**
 * Причина, по которой критерий не может быть рассчитан в данной области.
 * Используется в [CriterionScore], когда [CriterionScore.applicable] == false.
 */
enum class NotApplicableReason {
    /** Низкострессовая (комфортная) сеть в области отсутствует. */
    NO_LOW_STRESS_NETWORK,

    /** В области не размечены перекрёстки и пешеходные переходы. */
    NO_CROSSINGS,

    /** В области нет дорог с разрешённой скоростью свыше 40 км/ч. */
    NO_FAST_ROADS,

    /** Тег покрытия не указан ни для одного объекта велоинфраструктуры. */
    NO_SURFACE_DATA,

    /** В выбранной области недостаточно данных для оценки. */
    INSUFFICIENT_DATA,
}
