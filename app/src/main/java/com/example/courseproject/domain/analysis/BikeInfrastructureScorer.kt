package com.example.courseproject.domain.analysis

import com.example.courseproject.domain.model.CriterionId
import com.example.courseproject.domain.model.CriterionScore
import com.example.courseproject.domain.model.OsmData
import com.example.courseproject.domain.model.OsmNode
import com.example.courseproject.domain.model.OsmWay
import com.example.courseproject.domain.model.QualityScore
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Реализация модели оценки качества велосипедной инфраструктуры.
 *
 * Итоговый показатель — взвешенная сумма пяти нормализованных критериев:
 * Q = 0.30·S + 0.25·N + 0.25·I + 0.15·V + 0.05·P.
 *
 * Если для критерия недостаточно данных (например, в области нет дорог
 * с разрешённой скоростью свыше 40 км/ч), критерий исключается из расчёта,
 * а веса оставшихся критериев нормируются. Это согласуется с консервативной
 * стратегией обработки неполной разметки OSM, принятой в модели.
 */
class BikeInfrastructureScorer {

    fun score(data: OsmData): QualityScore {
        val cyclableRoads = data.ways.filter { OsmTags.isCyclableRoad(it) }
        if (cyclableRoads.isEmpty()) return emptyResult()

        val criteria = listOf(
            computeSafety(cyclableRoads, data.nodes),
            computeContinuity(cyclableRoads, data.nodes),
            computeIntersections(data),
            computeHighSpeed(cyclableRoads, data.nodes),
            computeSurface(cyclableRoads),
        )
        val total = weightedTotal(criteria)
        return QualityScore(
            total = total,
            criteria = criteria,
            summary = buildSummary(total, criteria),
            dataWarning = buildWarning(cyclableRoads, criteria),
        )
    }

    /** S — доля длины дорог, оборудованных выделенной велоинфраструктурой. */
    private fun computeSafety(roads: List<OsmWay>, nodes: Map<Long, OsmNode>): CriterionScore {
        val totalLength = roads.sumOf { GeoUtils.wayLengthMeters(it, nodes) }
        val veloLength = roads.filter { OsmTags.hasBikeInfrastructure(it) }
            .sumOf { GeoUtils.wayLengthMeters(it, nodes) }
        val value = if (totalLength > 0.0) (veloLength / totalLength).coerceIn(0.0, 1.0) else 0.0
        return CriterionScore(
            criterion = CriterionId.SAFETY,
            value = value,
            applicable = true,
            detail = "Выделенной велоинфраструктурой оборудовано ${percent(value)} " +
                "дорожной сети (${km(veloLength)} из ${km(totalLength)}).",
        )
    }

    /** N — доля длины крупнейшей связной компоненты от всей велоинфраструктуры. */
    private fun computeContinuity(roads: List<OsmWay>, nodes: Map<Long, OsmNode>): CriterionScore {
        val bikeWays = roads.filter { OsmTags.hasBikeInfrastructure(it) }
        val veloLength = bikeWays.sumOf { GeoUtils.wayLengthMeters(it, nodes) }
        if (bikeWays.isEmpty() || veloLength <= 0.0) {
            return CriterionScore(
                criterion = CriterionId.CONTINUITY,
                value = 0.0,
                applicable = false,
                detail = "Велоинфраструктура в области отсутствует — непрерывность не определяется.",
            )
        }
        val disjointSet = DisjointSet()
        for (way in bikeWays) {
            val ids = way.nodeIds
            for (k in 1 until ids.size) disjointSet.union(ids[0], ids[k])
        }
        val componentLength = HashMap<Long, Double>()
        for (way in bikeWays) {
            val firstNode = way.nodeIds.firstOrNull() ?: continue
            val root = disjointSet.find(firstNode)
            componentLength[root] = (componentLength[root] ?: 0.0) +
                GeoUtils.wayLengthMeters(way, nodes)
        }
        val maxLength = componentLength.values.maxOrNull() ?: 0.0
        val components = componentLength.size
        val value = (maxLength / veloLength).coerceIn(0.0, 1.0)
        val detail = if (components <= 1) {
            "Велосеть полностью связна — один непрерывный участок (${km(maxLength)})."
        } else {
            "Крупнейший связный участок объединяет ${percent(value)} велоинфраструктуры " +
                "(${km(maxLength)} из ${km(veloLength)}). Всего обособленных участков: $components."
        }
        return CriterionScore(CriterionId.CONTINUITY, value, applicable = true, detail = detail)
    }

    /** I — доля пересечений велодорожек с автодорогами, оборудованных велопереездом. */
    private fun computeIntersections(data: OsmData): CriterionScore {
        val cyclewayNodes = HashSet<Long>()
        val carNodes = HashSet<Long>()
        for (way in data.ways) {
            if (OsmTags.isStandaloneCycleway(way)) cyclewayNodes.addAll(way.nodeIds)
            if (OsmTags.isCarRoad(way)) carNodes.addAll(way.nodeIds)
        }
        val intersections = cyclewayNodes.filter { it in carNodes }
        if (intersections.isEmpty()) {
            return CriterionScore(
                criterion = CriterionId.INTERSECTIONS,
                value = 1.0,
                applicable = false,
                detail = "Пересечений велодорожек с автомобильными дорогами не обнаружено.",
            )
        }
        val withCrossing = intersections.count { id ->
            data.nodes[id]?.let { OsmTags.isBikeCrossing(it) } == true
        }
        val value = (withCrossing.toDouble() / intersections.size).coerceIn(0.0, 1.0)
        return CriterionScore(
            criterion = CriterionId.INTERSECTIONS,
            value = value,
            applicable = true,
            detail = "Велопереездом оборудовано $withCrossing из ${intersections.size} " +
                "пересечений велодорожек с автомобильными дорогами.",
        )
    }

    /** V — доля длины скоростных дорог (> 40 км/ч), обеспеченных велоинфраструктурой. */
    private fun computeHighSpeed(roads: List<OsmWay>, nodes: Map<Long, OsmNode>): CriterionScore {
        val speedRoads = roads.filter {
            val limit = OsmTags.maxSpeedKmh(it)
            limit != null && limit > HIGH_SPEED_THRESHOLD_KMH
        }
        val speedLength = speedRoads.sumOf { GeoUtils.wayLengthMeters(it, nodes) }
        if (speedRoads.isEmpty() || speedLength <= 0.0) {
            return CriterionScore(
                criterion = CriterionId.HIGH_SPEED,
                value = 1.0,
                applicable = false,
                detail = "Дорог с разрешённой скоростью свыше $HIGH_SPEED_THRESHOLD_KMH км/ч в области нет.",
            )
        }
        val protectedLength = speedRoads.filter { OsmTags.hasBikeInfrastructure(it) }
            .sumOf { GeoUtils.wayLengthMeters(it, nodes) }
        val value = (protectedLength / speedLength).coerceIn(0.0, 1.0)
        return CriterionScore(
            criterion = CriterionId.HIGH_SPEED,
            value = value,
            applicable = true,
            detail = "На скоростных дорогах велоинфраструктурой обеспечено ${percent(value)} " +
                "протяжённости (${km(protectedLength)} из ${km(speedLength)}).",
        )
    }

    /** P — доля объектов велоинфраструктуры с приемлемым покрытием. */
    private fun computeSurface(roads: List<OsmWay>): CriterionScore {
        val bikeWays = roads.filter { OsmTags.hasBikeInfrastructure(it) }
        val tagged = bikeWays.filter { OsmTags.hasSurfaceTag(it) }
        if (tagged.isEmpty()) {
            return CriterionScore(
                criterion = CriterionId.SURFACE,
                value = 1.0,
                applicable = false,
                detail = "Тег покрытия не указан ни для одного объекта велоинфраструктуры.",
            )
        }
        val good = tagged.count { OsmTags.hasGoodSurface(it) }
        val value = (good.toDouble() / tagged.size).coerceIn(0.0, 1.0)
        return CriterionScore(
            criterion = CriterionId.SURFACE,
            value = value,
            applicable = true,
            detail = "Приемлемое покрытие у $good из ${tagged.size} объектов велоинфраструктуры " +
                "с указанным тегом surface.",
        )
    }

    /** Взвешенная сумма с нормировкой весов по применимым критериям. */
    private fun weightedTotal(criteria: List<CriterionScore>): Double {
        val applicable = criteria.filter { it.applicable }
        if (applicable.isEmpty()) return 0.0
        val weightSum = applicable.sumOf { it.criterion.weight }
        if (weightSum <= 0.0) return 0.0
        val weighted = applicable.sumOf { it.criterion.weight * it.value }
        return (weighted / weightSum).coerceIn(0.0, 1.0)
    }

    private fun buildSummary(total: Double, criteria: List<CriterionScore>): String {
        val applicable = criteria.filter { it.applicable }
        if (applicable.isEmpty()) {
            return "Итоговую оценку рассчитать не удалось: недостаточно данных."
        }
        val header = "Качество велоинфраструктуры — ${QualityScore.band(total)} (Q = ${fmt(total)})."
        val best = applicable.maxByOrNull { it.value }!!
        val worst = applicable.minByOrNull { it.value }!!
        if (best.criterion == worst.criterion) {
            return "$header Оценённые критерии примерно равнозначны."
        }
        return "$header Сильнее всего оценку повышает критерий «${best.criterion.title}» " +
            "(${fmt(best.value)}), снижает — «${worst.criterion.title}» (${fmt(worst.value)})."
    }

    private fun buildWarning(roads: List<OsmWay>, criteria: List<CriterionScore>): String? {
        val notApplicable = criteria.count { !it.applicable }
        return when {
            roads.size < MIN_ROADS_FOR_RELIABLE ->
                "В выбранной области мало дорог (${roads.size}); оценка может быть нерепрезентативной."
            notApplicable >= 3 ->
                "Для нескольких критериев недостаточно данных OSM — они исключены из расчёта Q."
            else -> null
        }
    }

    private fun emptyResult(): QualityScore {
        val criteria = CriterionId.entries.map {
            CriterionScore(it, value = 0.0, applicable = false, detail = "Недостаточно данных для оценки.")
        }
        return QualityScore(
            total = 0.0,
            criteria = criteria,
            summary = "В выбранной области не найдено дорог для анализа велоинфраструктуры.",
            dataWarning = "Выберите область с дорожной сетью или измените масштаб карты.",
        )
    }

    private fun percent(fraction: Double): String = "${(fraction * 100).roundToInt()}%"

    private fun km(meters: Double): String = String.format(Locale.ROOT, "%.1f км", meters / 1000.0)

    private fun fmt(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

    private companion object {
        const val HIGH_SPEED_THRESHOLD_KMH = 40
        const val MIN_ROADS_FOR_RELIABLE = 5
    }
}
