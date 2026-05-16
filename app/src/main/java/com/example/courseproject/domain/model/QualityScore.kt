package com.example.courseproject.domain.model

/**
 * Критерии модели оценки качества велосипедной инфраструктуры.
 * Веса соответствуют формуле Q = 0.30·S + 0.25·N + 0.25·I + 0.15·V + 0.05·P.
 */
enum class CriterionId(val code: String, val title: String, val weight: Double) {
    SAFETY("S", "Безопасность", 0.30),
    CONTINUITY("N", "Непрерывность сети", 0.25),
    INTERSECTIONS("I", "Организация перекрёстков", 0.25),
    HIGH_SPEED("V", "Инфраструктура на скоростных дорогах", 0.15),
    SURFACE("P", "Качество покрытия", 0.05),
}

/** Частная оценка по одному критерию модели. */
data class CriterionScore(
    val criterion: CriterionId,
    /** Нормализованное значение критерия в диапазоне [0; 1]. */
    val value: Double,
    /** false — данных для оценки недостаточно, критерий исключён из расчёта Q. */
    val applicable: Boolean,
    /** Текстовое пояснение, как получено значение критерия. */
    val detail: String,
)

/** Итоговая оценка качества велосипедной инфраструктуры выбранной области. */
data class QualityScore(
    /** Интегральный показатель качества Q в диапазоне [0; 1]. */
    val total: Double,
    val criteria: List<CriterionScore>,
    /** Краткое пояснение к итоговой оценке. */
    val summary: String,
    /** Предупреждение о неполноте исходных данных, либо null. */
    val dataWarning: String? = null,
) {
    fun criterion(id: CriterionId): CriterionScore? = criteria.firstOrNull { it.criterion == id }

    companion object {
        /** Качественная характеристика территории по числовому значению Q. */
        fun band(q: Double): String = when {
            q < 0.2 -> "очень низкое"
            q < 0.4 -> "низкое"
            q < 0.6 -> "среднее"
            q < 0.8 -> "хорошее"
            else -> "высокое"
        }
    }
}
