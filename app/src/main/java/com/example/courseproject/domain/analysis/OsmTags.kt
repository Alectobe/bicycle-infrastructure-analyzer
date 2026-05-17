package com.example.courseproject.domain.analysis

import com.example.courseproject.domain.model.OsmNode
import com.example.courseproject.domain.model.OsmWay

/**
 * Классификация объектов OpenStreetMap по тегам в терминах модели оценки
 * качества велосипедной инфраструктуры. Инкапсулирует знание о схеме тегов OSM.
 */
object OsmTags {

    /** Скорость, не выше которой улица считается спокойной (низкострессовой), км/ч. */
    const val CALM_SPEED_KMH = 30

    /** Типы highway, образующие веломаршрутную сеть (без тротуаров и служебных проездов). */
    private val NETWORK_HIGHWAYS = setOf(
        "cycleway", "residential", "living_street", "unclassified", "road",
        "tertiary", "tertiary_link", "secondary", "secondary_link",
        "primary", "primary_link", "path", "pedestrian",
    )

    /** Типы highway, изначально комфортные для велосипедиста (без интенсивного автодвижения). */
    private val LOW_STRESS_HIGHWAYS = setOf("cycleway", "living_street", "path", "pedestrian")

    /** Значения тегов cycleway*, означающие выделенную велополосу. */
    private val BIKE_LANE_VALUES = setOf("lane", "track", "opposite_lane", "opposite_track")

    private val BIKE_CYCLEWAY_KEYS = listOf(
        "cycleway", "cycleway:left", "cycleway:right", "cycleway:both",
    )

    /** Приемлемые значения тега surface. */
    private val GOOD_SURFACES = setOf("asphalt", "paved", "concrete", "paving_stones")

    /** Значения тега crossing, означающие организованное пересечение. */
    private val ORGANIZED_CROSSINGS = setOf("traffic_signals", "marked", "zebra")

    fun isNetworkWay(way: OsmWay): Boolean = way.tags["highway"] in NETWORK_HIGHWAYS

    /** Путь является обособленной велодорожкой (highway=cycleway). */
    fun isStandaloneCycleway(way: OsmWay): Boolean = way.tags["highway"] == "cycleway"

    /** Путь относится к изначально комфортным типам (велодорожка, спокойная улица, дорожка). */
    fun isLowStressHighway(way: OsmWay): Boolean = way.tags["highway"] in LOW_STRESS_HIGHWAYS

    /** На дороге размечена выделенная велополоса. */
    fun hasBikeLane(way: OsmWay): Boolean =
        BIKE_CYCLEWAY_KEYS.any { way.tags[it] in BIKE_LANE_VALUES }

    /** Путь имеет собственную велоинфраструктуру: это велодорожка либо дорога с велополосой. */
    fun hasOwnBikeInfrastructure(way: OsmWay): Boolean =
        isStandaloneCycleway(way) || hasBikeLane(way)

    /**
     * Разрешённая скорость движения в км/ч. Распознаёт числовые значения и
     * зональные обозначения OSM (например, RU:urban, *:living_street),
     * либо null, если тег отсутствует или не интерпретируется.
     */
    fun maxSpeedKmh(way: OsmWay): Int? {
        val raw = way.tags["maxspeed"]?.trim()?.lowercase() ?: return null
        if (raw.contains("living_street") || raw.contains("walk")) return 20
        if (raw.contains("urban")) return 60
        if (raw.contains("rural") || raw.contains("motorway")) return 90
        val digits = raw.dropWhile { !it.isDigit() }.takeWhile { it.isDigit() }
        val number = digits.toIntOrNull()
        if (number != null) {
            return if (raw.contains("mph")) (number * 1.609).toInt() else number
        }
        if (raw.contains("none")) return 100
        return null
    }

    fun hasSurfaceTag(way: OsmWay): Boolean = way.tags.containsKey("surface")

    /** Покрытие пути приемлемо (имеет смысл только при заданном теге surface). */
    fun hasGoodSurface(way: OsmWay): Boolean = way.tags["surface"] in GOOD_SURFACES

    /** Узел является перекрёстком либо пешеходным/велосипедным переходом. */
    fun isCrossing(node: OsmNode): Boolean = node.tags["highway"] == "crossing"

    /** Пересечение организовано — регулируемое светофором либо обозначенное разметкой. */
    fun isOrganizedCrossing(node: OsmNode): Boolean =
        isCrossing(node) && node.tags["crossing"] in ORGANIZED_CROSSINGS
}
