package com.example.courseproject.domain.analysis

import com.example.courseproject.domain.model.OsmNode
import com.example.courseproject.domain.model.OsmWay

/**
 * Классификация объектов OpenStreetMap по тегам в терминах модели оценки
 * качества велосипедной инфраструктуры. Инкапсулирует знание о схеме тегов OSM.
 */
object OsmTags {

    /** Типы highway, по которым возможно велосипедное движение. */
    private val CYCLABLE_HIGHWAYS = setOf(
        "cycleway", "residential", "living_street", "unclassified", "road",
        "tertiary", "tertiary_link", "secondary", "secondary_link",
        "primary", "primary_link", "service", "track", "path",
    )

    /** Типы highway с автомобильным движением. */
    private val CAR_HIGHWAYS = setOf(
        "residential", "living_street", "unclassified", "road", "service",
        "tertiary", "tertiary_link", "secondary", "secondary_link",
        "primary", "primary_link", "trunk", "trunk_link",
    )

    /** Значения тегов cycleway*, означающие выделенную велоинфраструктуру. */
    private val BIKE_LANE_VALUES = setOf("lane", "track", "opposite_lane", "opposite_track")

    private val BIKE_CYCLEWAY_KEYS = listOf(
        "cycleway", "cycleway:left", "cycleway:right", "cycleway:both",
    )

    /** Приемлемые значения тега surface. */
    private val GOOD_SURFACES = setOf("asphalt", "paved", "concrete")

    fun isCyclableRoad(way: OsmWay): Boolean = way.tags["highway"] in CYCLABLE_HIGHWAYS

    fun isCarRoad(way: OsmWay): Boolean = way.tags["highway"] in CAR_HIGHWAYS

    /** Путь является обособленной велодорожкой (highway=cycleway). */
    fun isStandaloneCycleway(way: OsmWay): Boolean = way.tags["highway"] == "cycleway"

    /** Путь оборудован выделенной велоинфраструктурой. */
    fun hasBikeInfrastructure(way: OsmWay): Boolean {
        if (isStandaloneCycleway(way)) return true
        return BIKE_CYCLEWAY_KEYS.any { way.tags[it] in BIKE_LANE_VALUES }
    }

    /** Узел является велопереездом — организованным пересечением для велосипедистов. */
    fun isBikeCrossing(node: OsmNode): Boolean {
        val tags = node.tags
        if (tags["cycleway"] == "crossing") return true
        if (tags["highway"] == "crossing") {
            return tags["bicycle"] == "yes" || tags["crossing"] == "cyclist"
        }
        return false
    }

    /**
     * Разрешённая скорость движения в км/ч, либо null, если тег maxspeed
     * отсутствует или не может быть однозначно интерпретирован.
     */
    fun maxSpeedKmh(way: OsmWay): Int? {
        val raw = way.tags["maxspeed"]?.trim()?.lowercase() ?: return null
        val isMph = raw.contains("mph")
        val number = raw.takeWhile { it.isDigit() }.toIntOrNull() ?: return null
        return if (isMph) (number * 1.609).toInt() else number
    }

    fun hasSurfaceTag(way: OsmWay): Boolean = way.tags.containsKey("surface")

    /** Покрытие пути приемлемо (имеет смысл только при заданном теге surface). */
    fun hasGoodSurface(way: OsmWay): Boolean = way.tags["surface"] in GOOD_SURFACES
}
