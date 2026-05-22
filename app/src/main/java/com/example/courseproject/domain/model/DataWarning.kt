package com.example.courseproject.domain.model

/**
 * Предупреждение о возможной нерепрезентативности итоговой оценки.
 * Текст предупреждения формирует presentation-слой по полю [type].
 */
data class DataWarning(
    val type: DataWarningType,
    /** Количество дорог в области; имеет смысл при [DataWarningType.FEW_ROADS]. */
    val wayCount: Int = 0,
)

/** Тип предупреждения о данных. */
enum class DataWarningType {
    /** В области слишком мало дорог для надёжной оценки. */
    FEW_ROADS,

    /** Большинство критериев исключено из расчёта Q из-за нехватки данных OSM. */
    MANY_CRITERIA_INAPPLICABLE,

    /** В области не найдено путей, образующих веломаршрутную сеть. */
    NO_NETWORK,
}
