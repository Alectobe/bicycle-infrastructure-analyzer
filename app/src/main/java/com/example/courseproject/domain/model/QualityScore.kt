package com.example.courseproject.domain.model

/**
 * Критерии модели оценки качества велосипедной инфраструктуры.
 * Веса соответствуют формуле Q = 0.30·S + 0.25·N + 0.25·I + 0.15·V + 0.05·P.
 *
 * Хранятся только код критерия (S/N/I/V/P) и вес. Отображаемое название
 * формируется в presentation-слое из локализованных ресурсов, поэтому
 * домен независим от языка интерфейса.
 */
enum class CriterionId(val code: String, val weight: Double) {
    SAFETY("S", 0.30),
    CONTINUITY("N", 0.25),
    INTERSECTIONS("I", 0.25),
    HIGH_SPEED("V", 0.15),
    SURFACE("P", 0.05),
}

/**
 * Частная оценка по одному критерию модели.
 *
 * Содержит только структурированные данные: само значение, признак
 * применимости, числовая статистика и (если критерий неприменим) причина
 * исключения. Словесное пояснение собирается presentation-слоем на основе
 * [stats] и [notApplicableReason] из локализованных строковых ресурсов.
 */
data class CriterionScore(
    val criterion: CriterionId,
    /** Нормализованное значение критерия в диапазоне [0; 1]. */
    val value: Double,
    /** false — данных недостаточно, критерий исключён из расчёта Q. */
    val applicable: Boolean,
    /** Числовая статистика расчёта; null — если критерий неприменим. */
    val stats: CriterionStats? = null,
    /** Причина исключения критерия из расчёта; null — если критерий применим. */
    val notApplicableReason: NotApplicableReason? = null,
)

/**
 * Итоговая оценка качества велоинфраструктуры выбранной области.
 *
 * Хранит только структурированные данные. Текстовые формулировки
 * (резюме, пояснения, предупреждения) собираются в presentation-слое
 * из локализованных ресурсов.
 */
data class QualityScore(
    /** Интегральный показатель качества Q в диапазоне [0; 1]. */
    val total: Double,
    val criteria: List<CriterionScore>,
    /** Структурированное резюме оценки. */
    val summary: ScoreSummary,
    /** Структурированное предупреждение о неполноте данных; null — нет предупреждения. */
    val dataWarning: DataWarning? = null,
) {
    fun criterion(id: CriterionId): CriterionScore? =
        criteria.firstOrNull { it.criterion == id }
}
