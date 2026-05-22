package com.example.courseproject.domain.model

/**
 * Структурированное резюме итоговой оценки. Presentation-слой формирует по
 * нему локализованный текст пояснения (например, «Сильнее всего оценку
 * повышает критерий … снижает — …»).
 */
data class ScoreSummary(
    /** Качественный диапазон итогового показателя Q. */
    val band: ScoreBand,
    /** Критерий с наибольшим значением; null — если нет применимых критериев. */
    val bestCriterion: CriterionId? = null,
    /** Критерий с наименьшим значением; null — если нет применимых критериев. */
    val worstCriterion: CriterionId? = null,
)
