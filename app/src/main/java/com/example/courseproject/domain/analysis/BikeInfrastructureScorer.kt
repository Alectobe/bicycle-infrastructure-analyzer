package com.example.courseproject.domain.analysis

import com.example.courseproject.domain.model.CriterionId
import com.example.courseproject.domain.model.CriterionScore
import com.example.courseproject.domain.model.CriterionStats
import com.example.courseproject.domain.model.DataWarning
import com.example.courseproject.domain.model.DataWarningType
import com.example.courseproject.domain.model.NotApplicableReason
import com.example.courseproject.domain.model.OsmData
import com.example.courseproject.domain.model.OsmWay
import com.example.courseproject.domain.model.QualityScore
import com.example.courseproject.domain.model.ScoreBand
import com.example.courseproject.domain.model.ScoreSummary

/**
 * Реализация модели оценки качества велосипедной инфраструктуры.
 *
 * Итоговый показатель — взвешенная сумма пяти нормализованных критериев:
 * Q = 0.30·S + 0.25·N + 0.25·I + 0.15·V + 0.05·P.
 *
 * Расчёт идёт по конвейеру (метод [score]):
 *   1. Из всех путей OpenStreetMap отбираются те, что образуют веломаршрутную
 *      сеть (велодорожки, улицы и дороги, кроме тротуаров и служебных проездов).
 *   2. Строится пространственный индекс узлов выделенных велодорожек —
 *      он позволяет понять, проходит ли вдоль обычной дороги отдельная велодорожка.
 *   3. Для каждого пути сети предвычисляются длина, разрешённая скорость,
 *      наличие параллельной велодорожки, защищённость и комфортность.
 *   4. По полученным данным считаются пять частных критериев и итоговое Q.
 *
 * Алгоритм возвращает только структурированные данные ([CriterionStats],
 * [ScoreSummary], [DataWarning], [NotApplicableReason]) — словесные формулировки
 * для пользовательского интерфейса собираются в presentation-слое из
 * локализованных строковых ресурсов. Благодаря этому доменный слой не
 * зависит от языка интерфейса.
 *
 * Если для какого-либо критерия в области недостаточно данных (например,
 * отсутствуют скоростные дороги), он исключается из расчёта, а веса оставшихся
 * критериев нормируются — это согласуется с консервативной стратегией
 * обработки неполной разметки OSM, принятой в модели.
 */
class BikeInfrastructureScorer {

    /** Путь веломаршрутной сети с предрасчётом признаков, нужных для оценки. */
    private class NetworkWay(
        val way: OsmWay,
        val length: Double,
        val maxSpeed: Int?,
        hasParallelCycleway: Boolean,
    ) {
        /**
         * Путь защищён выделенной велоинфраструктурой, если у него есть
         * собственный тег велоинфраструктуры (highway=cycleway либо
         * cycleway=lane/track) или вдоль него проходит обособленная велодорожка.
         */
        val isProtected: Boolean =
            OsmTags.hasOwnBikeInfrastructure(way) || hasParallelCycleway

        /**
         * Путь комфортен (низкострессовый), если выполняется хотя бы одно из:
         *   – путь защищён велоинфраструктурой ([isProtected]);
         *   – тип highway сам по себе подразумевает отсутствие автодвижения
         *     (cycleway, living_street, path, pedestrian);
         *   – разрешённая скорость не выше 30 км/ч (спокойная улица).
         */
        val isLowStress: Boolean =
            isProtected ||
                OsmTags.isLowStressHighway(way) ||
                (maxSpeed != null && maxSpeed <= OsmTags.CALM_SPEED_KMH)
    }

    /**
     * Главный метод: возвращает интегральную оценку Q и частные оценки по
     * пяти критериям для указанного набора картографических данных.
     */
    fun score(data: OsmData): QualityScore {
        // 1. Отбираем пути, образующие веломаршрутную сеть.
        val networkWays = data.ways.filter { OsmTags.isNetworkWay(it) }
        if (networkWays.isEmpty()) return emptyResult()

        // 2. Строим пространственный индекс узлов выделенных велодорожек.
        val cyclewayWays = data.ways.filter { OsmTags.isStandaloneCycleway(it) }
        val proximityIndex = CyclewayProximityIndex(cyclewayWays, data.nodes)

        // 3. Для каждого пути сети считаем длину, скорость и наличие
        //    параллельной велодорожки. Признаки isProtected и isLowStress
        //    вычисляются внутри NetworkWay.
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

        // 4. Считаем пять частных критериев и итоговый показатель Q.
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
            stats = CriterionStats(
                numerator = lowStressLength,
                denominator = totalLength,
                isCount = false,
            ),
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
                notApplicableReason = NotApplicableReason.NO_LOW_STRESS_NETWORK,
            )
        }
        // Объединяем все узлы каждого пути в одно множество (Union-Find).
        // После этого два пути окажутся в одной компоненте тогда и только
        // тогда, когда у них есть хотя бы один общий узел.
        val disjointSet = DisjointSet()
        for (networkWay in lowStress) {
            val ids = networkWay.way.nodeIds
            for (k in 1 until ids.size) disjointSet.union(ids[0], ids[k])
        }
        // Суммируем длины путей внутри каждой компоненты.
        val componentLength = HashMap<Long, Double>()
        for (networkWay in lowStress) {
            val firstNode = networkWay.way.nodeIds.firstOrNull() ?: continue
            val root = disjointSet.find(firstNode)
            componentLength[root] = (componentLength[root] ?: 0.0) + networkWay.length
        }
        val maxLength = componentLength.values.maxOrNull() ?: 0.0
        val components = componentLength.size
        val value = (maxLength / lowStressLength).coerceIn(0.0, 1.0)
        return CriterionScore(
            criterion = CriterionId.CONTINUITY,
            value = value,
            applicable = true,
            stats = CriterionStats(
                numerator = maxLength,
                denominator = lowStressLength,
                isCount = false,
                componentCount = components,
            ),
        )
    }

    /** I — доля перекрёстков с организованным (регулируемым/обозначенным) пересечением. */
    private fun computeIntersections(data: OsmData): CriterionScore {
        val crossings = data.nodes.values.filter { OsmTags.isCrossing(it) }
        if (crossings.isEmpty()) {
            return CriterionScore(
                criterion = CriterionId.INTERSECTIONS,
                value = 1.0,
                applicable = false,
                notApplicableReason = NotApplicableReason.NO_CROSSINGS,
            )
        }
        val organized = crossings.count { OsmTags.isOrganizedCrossing(it) }
        val value = (organized.toDouble() / crossings.size).coerceIn(0.0, 1.0)
        return CriterionScore(
            criterion = CriterionId.INTERSECTIONS,
            value = value,
            applicable = true,
            stats = CriterionStats(
                numerator = organized.toDouble(),
                denominator = crossings.size.toDouble(),
                isCount = true,
            ),
        )
    }

    /** V — доля протяжённости скоростных дорог (> 40 км/ч), обеспеченных велоинфраструктурой. */
    private fun computeHighSpeed(ways: List<NetworkWay>): CriterionScore {
        val fastRoads = ways.filter {
            it.maxSpeed != null && it.maxSpeed > HIGH_SPEED_THRESHOLD_KMH
        }
        val fastLength = fastRoads.sumOf { it.length }
        if (fastRoads.isEmpty() || fastLength <= 0.0) {
            return CriterionScore(
                criterion = CriterionId.HIGH_SPEED,
                value = 1.0,
                applicable = false,
                notApplicableReason = NotApplicableReason.NO_FAST_ROADS,
            )
        }
        val protectedLength = fastRoads.filter { it.isProtected }.sumOf { it.length }
        val value = (protectedLength / fastLength).coerceIn(0.0, 1.0)
        return CriterionScore(
            criterion = CriterionId.HIGH_SPEED,
            value = value,
            applicable = true,
            stats = CriterionStats(
                numerator = protectedLength,
                denominator = fastLength,
                isCount = false,
            ),
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
                notApplicableReason = NotApplicableReason.NO_SURFACE_DATA,
            )
        }
        val good = tagged.count { OsmTags.hasGoodSurface(it.way) }
        val value = (good.toDouble() / tagged.size).coerceIn(0.0, 1.0)
        return CriterionScore(
            criterion = CriterionId.SURFACE,
            value = value,
            applicable = true,
            stats = CriterionStats(
                numerator = good.toDouble(),
                denominator = tagged.size.toDouble(),
                isCount = true,
            ),
        )
    }

    /**
     * Взвешенная сумма с нормировкой весов по применимым критериям.
     * Если для критерия недостаточно данных, его вес выбывает и из числителя,
     * и из знаменателя — итог Q ∈ [0; 1] остаётся корректным.
     */
    private fun weightedTotal(criteria: List<CriterionScore>): Double {
        val applicable = criteria.filter { it.applicable }
        if (applicable.isEmpty()) return 0.0
        val weightSum = applicable.sumOf { it.criterion.weight }
        if (weightSum <= 0.0) return 0.0
        val weighted = applicable.sumOf { it.criterion.weight * it.value }
        return (weighted / weightSum).coerceIn(0.0, 1.0)
    }

    /**
     * Структурированное резюме: качественный диапазон Q и идентификаторы
     * критериев с наибольшим/наименьшим значением. Текст пояснения собирает
     * presentation-слой из локализованных шаблонов.
     */
    private fun buildSummary(total: Double, criteria: List<CriterionScore>): ScoreSummary {
        val applicable = criteria.filter { it.applicable }
        return ScoreSummary(
            band = ScoreBand.of(total),
            bestCriterion = applicable.maxByOrNull { it.value }?.criterion,
            worstCriterion = applicable.minByOrNull { it.value }?.criterion,
        )
    }

    /**
     * Предупреждение о возможной нерепрезентативности оценки. Возвращает null,
     * если данных в области достаточно. Тип предупреждения и численные
     * параметры заполняются здесь; локализованный текст формируется в UI.
     */
    private fun buildWarning(
        ways: List<NetworkWay>,
        criteria: List<CriterionScore>,
    ): DataWarning? {
        val notApplicable = criteria.count { !it.applicable }
        return when {
            ways.size < MIN_WAYS_FOR_RELIABLE ->
                DataWarning(DataWarningType.FEW_ROADS, wayCount = ways.size)
            notApplicable >= 3 ->
                DataWarning(DataWarningType.MANY_CRITERIA_INAPPLICABLE)
            else -> null
        }
    }

    /**
     * Особый случай: в области не нашлось ни одного пути сети. Возвращаем
     * нулевой Q и «пустые» структуры; UI отрисует соответствующее сообщение.
     */
    private fun emptyResult(): QualityScore {
        val criteria = CriterionId.entries.map {
            CriterionScore(
                criterion = it,
                value = 0.0,
                applicable = false,
                notApplicableReason = NotApplicableReason.INSUFFICIENT_DATA,
            )
        }
        return QualityScore(
            total = 0.0,
            criteria = criteria,
            summary = ScoreSummary(band = ScoreBand.VERY_LOW),
            dataWarning = DataWarning(DataWarningType.NO_NETWORK),
        )
    }

    private companion object {
        /**
         * Порог скоростной дороги в км/ч. Дороги быстрее этого значения
         * считаются потенциально опасными для велосипедиста и попадают
         * в расчёт критерия V.
         */
        const val HIGH_SPEED_THRESHOLD_KMH = 40

        /**
         * Если в области меньше этого числа дорог, итоговая оценка снабжается
         * предупреждением о возможной нерепрезентативности (см. [buildWarning]).
         */
        const val MIN_WAYS_FOR_RELIABLE = 5

        /**
         * Минимальная доля узлов дороги, которые должны находиться рядом
         * с обособленной велодорожкой, чтобы считать дорогу накрытой
         * параллельной велодорожкой (а не лишь пересекающейся с ней).
         */
        const val PARALLEL_COVERAGE_FRACTION = 0.5
    }
}
