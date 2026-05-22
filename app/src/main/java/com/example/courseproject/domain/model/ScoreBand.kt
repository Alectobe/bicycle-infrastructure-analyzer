package com.example.courseproject.domain.model

/**
 * Качественный диапазон интегральной оценки Q.
 *
 * Доменное представление — без текстового названия: словесная форма
 * («низкое», «высокое», «low», «high») формируется в presentation-слое
 * из локализованных ресурсов.
 */
enum class ScoreBand {
    VERY_LOW,
    LOW,
    MEDIUM,
    GOOD,
    HIGH;

    companion object {
        /** Возвращает диапазон, соответствующий значению Q ∈ [0; 1]. */
        fun of(q: Double): ScoreBand = when {
            q < 0.2 -> VERY_LOW
            q < 0.4 -> LOW
            q < 0.6 -> MEDIUM
            q < 0.8 -> GOOD
            else -> HIGH
        }
    }
}
