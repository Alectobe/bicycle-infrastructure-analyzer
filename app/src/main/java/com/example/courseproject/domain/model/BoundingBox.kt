package com.example.courseproject.domain.model

/**
 * Прямоугольная область анализа в географических координатах (WGS84).
 * Используется как входной параметр для загрузки данных OSM и расчёта оценки.
 */
data class BoundingBox(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    val centerLat: Double get() = (south + north) / 2.0
    val centerLon: Double get() = (west + east) / 2.0

    /** Стабильный ключ области для кэширования (координаты округляются до 5 знаков). */
    fun cacheKey(): String {
        fun part(value: Double): String = (value * 1e5).toLong().toString()
        return "${part(south)}_${part(west)}_${part(north)}_${part(east)}"
    }
}
