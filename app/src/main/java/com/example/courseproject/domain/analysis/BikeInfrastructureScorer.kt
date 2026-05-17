package com.example.courseproject.domain.analysis

import com.example.courseproject.domain.model.CriterionId
import com.example.courseproject.domain.model.CriterionScore
import com.example.courseproject.domain.model.OsmData
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
 * Модель учитывает, что в OpenStreetMap велоинфраструктура нанесена
 * преимущественно отдельными путями (highway=cycleway), а комфорт движения
 * для начинающего велосипедиста определяется не только выделенными
 * велодорожками, но и спокойными улицами с низкой разрешённой скоростью.
 * Критерий, для которого недостаточно данных, исключается из расчёта, а веса
 * оставшихся критериев нормируются.
 */
class BikeInfrastructureScorer {

    /** Путь веломаршрутной сети с предрасчётом признаков, нужных для оценки. */
    private class NetworkWay(
        val way: OsmWay,
        val length: Double,
        val maxSpeed: Int?,
        hasParallelCycleway: Boolean,
    ) {
        /** Путь защищён выделенной велоинфраструктурой — собственной или параллельной. */
        val isProtected: Boolean =
            OsmTags.hasOwnBikeInfrastructure(way) || hasParallelCycleway

        /** Путь комфортен (низкострессовый) для начинающего велосипедиста. */
        val isLowStress: Boolean =
            isProtected ||
                OsmTags.isLowStressHighway(way) ||
                (maxSpeed != null && maxSpeed <= OsmTags.CALM_SPEED_KMH)
    }

    fun score(data: OsmData): QualityScore {
        val networkWays = data.ways.filter { OsmTags.isNetworkWay(it) }
        if (networkWays.isEmpty()) return emptyResult()

        val cyclewayWays = data.ways.filter { OsmTags.isStandaloneCycleway(it) }
        val proximityIndex = CyclewayProximityIndex(cyclewayWays, data.nodes)
        val ways = networkWays.map { way ->
            val hasParallel = !OsmTags.hasOwnBikeInfrastructure(way) &&
                proximityIndex.cyclewayCoverage(way) >= PARALLEL_COVERAGE_FRACTION
            NetworkWay(
                way = way,
                length = GeoUtils.wayLengthMeters(way, data.nodes),
                maxSpeed = OsmTags.maxSpeedKmh(way),
                hasParallelCycleway = hasParallel,
            )
        }

        val criteria = listOf(
            computeSafety(ways),
            computeContinuity(ways),
            computeIntersections(data),
            computeHighSpeed(ways),
            computeSurface(ways),
        )
        val total = weightedTotal(criteria)
        return QualityScore(
            total = total,
            criteria = criteria,
            summary = buildSummary(total, criteria),
            dataWarning = buildWarning(ways, criteria),
        )
    }

    /** S — доля протяжённости низкострессовой (комфортной) сети. */
    private fun computeSafety(ways: List<NetworkWay>): CriterionScore {
        val totalLength = ways.sumOf { it.length }
        val lowStressLength = ways.filter { it.isLowStress }.sumOf { it.length }
        val value = if (totalLength > 0.0) {
            (lowStressLength / totalLength).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        return CriterionScore(
            criterion = CriterionId.SAFETY,
            value = value,
            applicable = true,
            detail = "Комфортная для велосипедиста сеть (велодорожки, велополосы, " +
                "спокойные улицы) — ${percent(value)} протяжённости " +
                "(${km(lowStressLength)} из ${km(totalLength)}).",
        )
    }

    /** N — доля протяжённости крупнейшей связной компоненты низкострессовой сети. */
    private fun computeContinuity(ways: List<NetworkWay>): CriterionScore {
        val lowStress = ways.filter { it.isLowStress }
        val lowStressLength = lowStress.sumOf { it.length }
        if (lowStress.isEmpty() || lowStressLength <= 0.0) {
            return CriterionScore(
                criterion = CriterionId.CONTINUITY,
                value = 0.0,
                applicable = false,
                detail = "Комфортная велосеть в области отсутствует — непрерывность не определяется.",
            )
        }
        val disjointSet = DisjointSet()
        for (networkWay in lowStress) {
            val ids = networkWay.way.nodeIds
            for (k in 1 until ids.size) disjointSet.union(ids[0], ids[k])
        }
        val componentLength = HashMap<Long, Double>()
        for (networkWay in lowStress) {
            val firstNode = networkWay.way.nodeIds.firstOrNull() ?: continue
            val root = disjointSet.find(firstNode)
            componentLength[root] = (componentLength[root] ?: 0.0) + networkWay.length
        }
        val maxLength = componentLength.values.maxOrNull() ?: 0.0
        val components = componentLength.size
        val value = (maxLength / lowStressLength).coerceIn(0.0, 1.0)
        val detail = if (components <= 1) {
            "Комфортная велосеть полностью связна — один непрерывный участок (${km(maxLength)})."
        } else {
            "Крупнейший связный участок комфортной сети — ${percent(value)} её протяжённости " +
                "(${km(maxLength)} из ${km(lowStressLength)}). Обособленных участков: $components."
        }
        return CriterionScore(CriterionId.CONTINUITY, value, applicable = true, detail = detail)
    }

    /** I — доля перекрёстков с организованным (регулируемым или обозначенным) пересечением. */
    private fun computeIntersections(data: OsmData): CriterionScore {
        val crossings = data.nodes.values.filter { OsmTags.isCrossing(it) }
        if (crossings.isEmpty()) {
            return CriterionScore(
                criterion = CriterionId.INTERSECTIONS,
                value = 1.0,
                applicable = false,
                detail = "Перекрёстки и пешеходные переходы в области не размечены.",
            )
        }
        val organized = crossings.count { OsmTags.isOrganizedCrossing(it) }
        val value = (organized.toDouble() / crossings.size).coerceIn(0.0, 1.0)
        return CriterionScore(
            criterion = CriterionId.INTERSECTIONS,
            value = value,
            applicable = true,
            detail = "Организованным пересечением (светофор или разметка) оборудовано " +
                "$organized из ${crossings.size} перекрёстков области.",
        )
    }

    /** V — доля протяжённости скоростных дорог (> 40 км/ч), обеспеченных велоинфраструктурой. */
    private fun computeHighSpeed(ways: List<NetworkWay>): CriterionScore {
        val fastRoads = ways.filter { it.maxSpeed != null && it.maxSpeed > HIGH_SPEED_THRESHOLD_KMH }
        val fastLength = fastRoads.sumOf { it.length }
        if (fastRoads.isEmpty() || fastLength <= 0.0) {
            return CriterionScore(
                criterion = CriterionId.HIGH_SPEED,
                value = 1.0,
                applicable = false,
                detail = "Дорог с разрешённой скоростью свыше $HIGH_SPEED_THRESHOLD_KMH км/ч в области нет.",
            )
        }
        val protectedLength = fastRoads.filter { it.isProtected }.sumOf { it.length }
        val value = (protectedLength / fastLength).coerceIn(0.0, 1.0)
        return CriterionScore(
            criterion = CriterionId.HIGH_SPEED,
            value = value,
            applicable = true,
            detail = "На скоростных дорогах велоинфраструктурой обеспечено ${percent(value)} " +
                "протяжённости (${km(protectedLength)} из ${km(fastLength)}).",
        )
    }

    /** P — доля объектов велоинфраструктуры с приемлемым покрытием. */
    private fun computeSurface(ways: List<NetworkWay>): CriterionScore {
        val infrastructure = ways.filter { it.isProtected }
        val tagged = infrastructure.filter { OsmTags.hasSurfaceTag(it.way) }
        if (tagged.isEmpty()) {
            return CriterionScore(
                criterion = CriterionId.SURFACE,
                value = 1.0,
                applicable = false,
                detail = "Тег покрытия не указан ни для одного объекта велоинфраструктуры.",
            )
        }
        val good = tagged.count { OsmTags.hasGoodSurface(it.way) }
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

    private fun buildWarning(ways: List<NetworkWay>, criteria: List<CriterionScore>): String? {
        val notApplicable = criteria.count { !it.applicable }
        return when {
            ways.size < MIN_WAYS_FOR_RELIABLE ->
                "В выбранной области мало дорог (${ways.size}); оценка может быть нерепрезентативной."
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
        const val MIN_WAYS_FOR_RELIABLE = 5
        const val PARALLEL_COVERAGE_FRACTION = 0.5
    }
}
