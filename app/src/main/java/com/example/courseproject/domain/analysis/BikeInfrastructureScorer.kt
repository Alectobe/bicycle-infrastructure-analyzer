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
 * Расчёт идёт по конвейеру (метод [score]):
 *   1. Из всех путей OpenStreetMap отбираются те, что образуют веломаршрутную
 *      сеть (велодорожки, улицы и дороги, кроме тротуаров и служебных проездов).
 *   2. Строится пространственный индекс узлов выделенных велодорожек —
 *      он позволяет понять, проходит ли вдоль обычной дороги отдельная велодорожка.
 *   3. Для каждого пути сети предвычисляются длина, разрешённая скорость,
 *      наличие параллельной велодорожки, защищённость и комфортность.
 *   4. По полученным данным считаются пять частных критериев и итоговое Q.
 *
 * Если для какого-либо критерия в области недостаточно данных (например,
 * отсутствуют скоростные дороги), он исключается из расчёта, а веса оставшихся
 * критериев нормируются — это согласуется с консервативной стратегией
 * обработки неполной разметки OSM, принятой в модели.
 *
 * Модель учитывает, что в OpenStreetMap велоинфраструктура нанесена
 * преимущественно отдельными путями (highway=cycleway), а комфорт движения
 * для начинающего велосипедиста определяется не только выделенными
 * велодорожками, но и спокойными улицами с низкой разрешённой скоростью.
 */
class BikeInfrastructureScorer {

    /**
     * Путь веломаршрутной сети с предрасчётом признаков, нужных для оценки.
     * Имеет два ключевых вычисляемых признака:
     *   – [isProtected] — у пути есть выделенная велоинфраструктура (своя или
     *     параллельная); используется в критериях V (скоростные дороги) и P (покрытие);
     *   – [isLowStress] — путь комфортен для начинающего велосипедиста;
     *     используется в критериях S (безопасность) и N (непрерывность сети).
     */
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
        //    Тротуары (footway), служебные проезды (service), лестницы и подобное
        //    не входят: они либо не предназначены для велосипеда, либо вносят шум
        //    в расчёт (см. список разрешённых типов в OsmTags).
        val networkWays = data.ways.filter { OsmTags.isNetworkWay(it) }
        if (networkWays.isEmpty()) return emptyResult()

        // 2. Строим пространственный индекс узлов всех обособленных велодорожек.
        //    Он нужен, чтобы для каждой обычной дороги быстро ответить на вопрос:
        //    «проходит ли вдоль неё параллельная велодорожка?» В OpenStreetMap
        //    велоинфраструктура вдоль улицы обычно нанесена самостоятельным путём,
        //    а не тегом на самой улице, поэтому такая геометрическая проверка
        //    необходима.
        val cyclewayWays = data.ways.filter { OsmTags.isStandaloneCycleway(it) }
        val proximityIndex = CyclewayProximityIndex(cyclewayWays, data.nodes)

        // 3. Для каждого пути сети считаем длину, разрешённую скорость и
        //    наличие параллельной велодорожки. Признаки isProtected и isLowStress
        //    вычисляются внутри NetworkWay и в дальнейшем используются критериями.
        val ways = networkWays.map { way ->
            // Параллельную велодорожку имеет смысл искать только у дорог без
            // собственной велоинфраструктуры — у самих велодорожек это понятие
            // вырождено. Дорога считается «накрытой» параллельной велодорожкой,
            // если доля её узлов рядом с велодорожкой достигает порога.
            val hasParallel = !OsmTags.hasOwnBikeInfrastructure(way) &&
                proximityIndex.cyclewayCoverage(way) >= PARALLEL_COVERAGE_FRACTION
            NetworkWay(
                way = way,
                length = GeoUtils.wayLengthMeters(way, data.nodes),
                maxSpeed = OsmTags.maxSpeedKmh(way),
                hasParallelCycleway = hasParallel,
            )
        }

        // 4. Считаем пять частных критериев и итоговый показатель Q с учётом
        //    применимости каждого критерия.
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

    /**
     * S — доля протяжённости низкострессовой (комфортной) сети относительно
     * всей сети. Чем больше доля комфортных участков, тем безопаснее район
     * для начинающего велосипедиста.
     */
    private fun computeSafety(ways: List<NetworkWay>): CriterionScore {
        // Знаменатель — суммарная длина всей веломаршрутной сети области.
        val totalLength = ways.sumOf { it.length }
        // Числитель — суммарная длина её низкострессовых сегментов.
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

    /**
     * N — доля протяжённости крупнейшей связной компоненты низкострессовой сети
     * относительно всей низкострессовой сети. Чем выше N, тем меньше у
     * велосипедиста разрывов в маршруте.
     */
    private fun computeContinuity(ways: List<NetworkWay>): CriterionScore {
        val lowStress = ways.filter { it.isLowStress }
        val lowStressLength = lowStress.sumOf { it.length }
        if (lowStress.isEmpty() || lowStressLength <= 0.0) {
            // Низкострессовой сети в области нет — измерять непрерывность нечего.
            return CriterionScore(
                criterion = CriterionId.CONTINUITY,
                value = 0.0,
                applicable = false,
                detail = "Комфортная велосеть в области отсутствует — непрерывность не определяется.",
            )
        }
        // Шаг А: объединяем все узлы каждого пути в одно множество системы
        // непересекающихся множеств (Union-Find). После этой операции два пути
        // оказываются в одной компоненте тогда и только тогда, когда у них есть
        // хотя бы один общий узел — то есть они физически соединены в OSM.
        val disjointSet = DisjointSet()
        for (networkWay in lowStress) {
            val ids = networkWay.way.nodeIds
            for (k in 1 until ids.size) disjointSet.union(ids[0], ids[k])
        }
        // Шаг Б: суммируем длины путей внутри каждой компоненты. Идентификатор
        // компоненты — представитель множества, полученный через find().
        val componentLength = HashMap<Long, Double>()
        for (networkWay in lowStress) {
            val firstNode = networkWay.way.nodeIds.firstOrNull() ?: continue
            val root = disjointSet.find(firstNode)
            componentLength[root] = (componentLength[root] ?: 0.0) + networkWay.length
        }
        // Шаг В: берём наибольшую компоненту и делим её длину на общую длину
        // низкострессовой сети — получаем долю крупнейшего связного участка.
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

    /**
     * I — доля перекрёстков и пешеходных переходов, которые «организованы»:
     * регулируются светофором или обозначены разметкой.
     *
     * В OpenStreetMap нет надёжного тега «велопереезд» — например, bicycle=yes
     * на узле crossing практически не ставится. Поэтому в качестве показателя
     * безопасности перекрёстков используется их общая организованность: этот
     * сигнал в данных OSM присутствует существенно чаще и хорошо коррелирует
     * с реальной безопасностью пересечений для всех уязвимых участников движения.
     */
    private fun computeIntersections(data: OsmData): CriterionScore {
        // Берём все узлы с highway=crossing — это перекрёстки и пешеходные переходы.
        val crossings = data.nodes.values.filter { OsmTags.isCrossing(it) }
        if (crossings.isEmpty()) {
            // В области не размечен ни один перекрёсток — критерий не вычислим.
            return CriterionScore(
                criterion = CriterionId.INTERSECTIONS,
                value = 1.0,
                applicable = false,
                detail = "Перекрёстки и пешеходные переходы в области не размечены.",
            )
        }
        // «Организованным» считается перекрёсток с crossing=traffic_signals,
        // marked или zebra (см. OsmTags.isOrganizedCrossing).
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

    /**
     * V — доля протяжённости скоростных дорог (с разрешённой скоростью свыше
     * 40 км/ч), обеспеченных выделенной велоинфраструктурой. Чем выше V, тем
     * безопаснее движение по «опасным» дорогам района.
     */
    private fun computeHighSpeed(ways: List<NetworkWay>): CriterionScore {
        // Скоростной считается дорога с разрешённой скоростью больше 40 км/ч.
        // Парсер OsmTags.maxSpeedKmh понимает как числовые значения, так и
        // зональные обозначения OSM — например, RU:urban распознаётся как 60 км/ч.
        val fastRoads = ways.filter { it.maxSpeed != null && it.maxSpeed > HIGH_SPEED_THRESHOLD_KMH }
        val fastLength = fastRoads.sumOf { it.length }
        if (fastRoads.isEmpty() || fastLength <= 0.0) {
            // В области нет скоростных дорог — критерий не имеет смысла, исключаем.
            return CriterionScore(
                criterion = CriterionId.HIGH_SPEED,
                value = 1.0,
                applicable = false,
                detail = "Дорог с разрешённой скоростью свыше $HIGH_SPEED_THRESHOLD_KMH км/ч в области нет.",
            )
        }
        // «Защищённой» дорога считается, если у неё есть собственная велополоса
        // или вдоль неё проходит параллельная обособленная велодорожка
        // (см. NetworkWay.isProtected).
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

    /**
     * P — доля объектов велоинфраструктуры с приемлемым покрытием от числа
     * объектов, у которых задан тег surface. Объекты без surface-тега в расчёт
     * не входят: для них качество покрытия неизвестно.
     */
    private fun computeSurface(ways: List<NetworkWay>): CriterionScore {
        // Покрытие имеет смысл оценивать только для объектов велоинфраструктуры;
        // у обычных автомобильных дорог оно в этом критерии не учитывается.
        val infrastructure = ways.filter { it.isProtected }
        val tagged = infrastructure.filter { OsmTags.hasSurfaceTag(it.way) }
        if (tagged.isEmpty()) {
            // Ни у одного объекта инфраструктуры покрытие не размечено —
            // данных для расчёта критерия нет, исключаем его.
            return CriterionScore(
                criterion = CriterionId.SURFACE,
                value = 1.0,
                applicable = false,
                detail = "Тег покрытия не указан ни для одного объекта велоинфраструктуры.",
            )
        }
        // К приемлемым покрытиям относятся asphalt, paved, concrete и paving_stones
        // (см. OsmTags.hasGoodSurface).
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

    /**
     * Взвешенная сумма с нормировкой весов по применимым критериям.
     *
     * Когда какие-то критерии исключены из-за недостатка данных, их веса
     * не учитываются ни в числителе, ни в знаменателе. Пример: если в области
     * нет скоростных дорог и критерий V исключён, итог считается как
     * Q = (0.30·S + 0.25·N + 0.25·I + 0.05·P) / (0.30 + 0.25 + 0.25 + 0.05).
     * Это гарантирует Q ∈ [0; 1] независимо от того, сколько критериев применимо.
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
     * Краткое пояснение к итоговой оценке: называет качественный диапазон
     * (низкое/среднее/хорошее и т. д.) и указывает критерии, давшие наибольший
     * положительный и отрицательный вклад в Q.
     */
    private fun buildSummary(total: Double, criteria: List<CriterionScore>): String {
        val applicable = criteria.filter { it.applicable }
        if (applicable.isEmpty()) {
            return "Итоговую оценку рассчитать не удалось: недостаточно данных."
        }
        val header = "Качество велоинфраструктуры — ${QualityScore.band(total)} (Q = ${fmt(total)})."
        // best — критерий с самым высоким значением (тянет оценку вверх);
        // worst — критерий с самым низким значением (тянет оценку вниз).
        val best = applicable.maxByOrNull { it.value }!!
        val worst = applicable.minByOrNull { it.value }!!
        if (best.criterion == worst.criterion) {
            return "$header Оценённые критерии примерно равнозначны."
        }
        return "$header Сильнее всего оценку повышает критерий «${best.criterion.title}» " +
            "(${fmt(best.value)}), снижает — «${worst.criterion.title}» (${fmt(worst.value)})."
    }

    /**
     * Предупреждение о возможной нерепрезентативности оценки. Возникает,
     * когда в области слишком мало дорог либо большинство критериев исключено.
     * Возвращает null, если оценка считается репрезентативной.
     */
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

    /**
     * Особый случай: в области не нашлось ни одного пути сети.
     * Возвращаем нулевой Q с предупреждением, не пытаясь вычислять критерии.
     */
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

    /** Доля в виде процента: 0.42 → «42%». */
    private fun percent(fraction: Double): String = "${(fraction * 100).roundToInt()}%"

    /** Длина в метрах — в виде «12.3 км» с одним знаком после точки. */
    private fun km(meters: Double): String = String.format(Locale.ROOT, "%.1f км", meters / 1000.0)

    /** Численное значение Q или критерия с двумя знаками после точки. */
    private fun fmt(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

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
