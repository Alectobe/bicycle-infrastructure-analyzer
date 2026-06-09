package com.example.courseproject.domain.analysis

import com.example.courseproject.domain.model.OsmNode
import com.example.courseproject.domain.model.OsmWay

/**
 * Классификация объектов OpenStreetMap по тегам в терминах модели оценки
 * качества велосипедной инфраструктуры. Инкапсулирует знание о схеме тегов OSM:
 * остальной алгоритм оперирует уже готовыми ответами «это велодорожка?»,
 * «это спокойная улица?» и т. п., не зная конкретных названий и значений тегов.
 */
object OsmTags {

    /** Скорость, не выше которой улица считается спокойной (низкострессовой), км/ч. */
    const val CALM_SPEED_KMH = 30

    /**
     * Типы highway, образующие веломаршрутную сеть (без тротуаров и служебных проездов).
     * Сюда входят сами велодорожки, городские улицы и дороги; не входят footway
     * (тротуары — не для велосипеда), service (служебные проезды), steps (лестницы) и т. п.
     */
    private val NETWORK_HIGHWAYS = setOf(
        "cycleway", "residential", "living_street", "unclassified", "road",
        "tertiary", "tertiary_link", "secondary", "secondary_link",
        "primary", "primary_link", "path", "pedestrian",
    )

    /**
     * Типы highway, изначально комфортные для велосипедиста (без интенсивного автодвижения).
     * Велодорожка — очевидно; living_street — жилая зона со скоростью пешехода;
     * path — тропа без машин; pedestrian — пешеходная улица.
     */
    private val LOW_STRESS_HIGHWAYS = setOf("cycleway", "living_street", "path", "pedestrian")

    /** Значения тегов cycleway*, означающие выделенную велополосу. */
    private val BIKE_LANE_VALUES = setOf("lane", "track", "opposite_lane", "opposite_track")

    /**
     * Ключи тегов, под которыми может быть указана велоинфраструктура на дороге.
     * cycleway:left/right/both — велополоса с конкретной стороны дороги.
     */
    private val BIKE_CYCLEWAY_KEYS = listOf(
        "cycleway", "cycleway:left", "cycleway:right", "cycleway:both",
    )

    /** Приемлемые значения тега surface (твёрдые ровные покрытия). */
    private val GOOD_SURFACES = setOf("asphalt", "paved", "concrete", "paving_stones")

    /**
     * Значения тега crossing, означающие организованное пересечение
     * (со светофором или явной разметкой). uncontrolled и unmarked
     * — наоборот, неорганизованные — сюда не попадают.
     */
    private val ORGANIZED_CROSSINGS = setOf("traffic_signals", "marked", "zebra")

    /** Путь входит в веломаршрутную сеть области. */
    fun isNetworkWay(way: OsmWay): Boolean = way.tags["highway"] in NETWORK_HIGHWAYS

    /** Путь является обособленной велодорожкой (highway=cycleway). */
    fun isStandaloneCycleway(way: OsmWay): Boolean = way.tags["highway"] == "cycleway"

    /** Путь относится к изначально комфортным типам (велодорожка, спокойная улица, дорожка). */
    fun isLowStressHighway(way: OsmWay): Boolean = way.tags["highway"] in LOW_STRESS_HIGHWAYS

    /** На дороге размечена выделенная велополоса (любой из cycleway:*-тегов). */
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
        // Берём значение тега, обрезаем пробелы, приводим к нижнему регистру.
        // Если тега нет — возвращаем null (скорость неизвестна).
        val raw = way.tags["maxspeed"]?.trim()?.lowercase() ?: return null
        // Зональные обозначения вида RU:living_street или *:walk → пешеходная зона ≈ 20 км/ч.
        if (raw.contains("living_street") || raw.contains("walk")) return 20
        // *:urban — городская зона. В России лимит 60 км/ч, в большинстве стран ≤ 50.
        // Берём 60 как верхнюю оценку: для модели (граница «скоростная дорога» > 40 км/ч)
        // это всё равно классифицируется как скоростная.
        if (raw.contains("urban")) return 60
        // *:rural, motorway, trunk → загородная или магистральная зона, обычно 90+ км/ч.
        if (raw.contains("rural") || raw.contains("motorway")) return 90
        // Числовое значение: ищем первую группу цифр в строке («60», «60 km/h», «zone:30»).
        val digits = raw.dropWhile { !it.isDigit() }.takeWhile { it.isDigit() }
        val number = digits.toIntOrNull()
        if (number != null) {
            // Если рядом стоит «mph» — это мили в час, переводим в км/ч.
            return if (raw.contains("mph")) (number * 1.609).toInt() else number
        }
        // *:none → ограничения нет (например, немецкие автобаны). Возвращаем 100 как «много».
        if (raw.contains("none")) return 100
        // Что-то непонятное — считаем, что скорость неизвестна.
        return null
    }

    /** У пути задан тег surface (какое-то покрытие указано). */
    fun hasSurfaceTag(way: OsmWay): Boolean = way.tags.containsKey("surface")

    /** Покрытие пути приемлемо (имеет смысл только при заданном теге surface). */
    fun hasGoodSurface(way: OsmWay): Boolean = way.tags["surface"] in GOOD_SURFACES

    /** Узел является перекрёстком либо пешеходным/велосипедным переходом. */
    fun isCrossing(node: OsmNode): Boolean = node.tags["highway"] == "crossing"

    /** Пересечение организовано — регулируемое светофором либо обозначенное разметкой. */
    fun isOrganizedCrossing(node: OsmNode): Boolean =
        // Перекрёсток должен быть отмечен как crossing…
        isCrossing(node) &&
            // …и тег crossing должен указывать на организацию: traffic_signals/marked/zebra.
            node.tags["crossing"] in ORGANIZED_CROSSINGS
}
