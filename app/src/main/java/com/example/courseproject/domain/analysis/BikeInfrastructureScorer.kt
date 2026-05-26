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

    /**
     * Путь веломаршрутной сети с предрасчётом признаков, нужных для оценки.
     * Создаётся один раз для каждого пути в начале расчёта и дальше используется
     * всеми пятью методами computeXxx — это экономит повторные обходы тегов.
     */
    private class NetworkWay(
        val way: OsmWay,                              // исходный объект OSM с тегами и узлами
        val length: Double,                           // длина пути в метрах (формула гаверсинуса)
        val maxSpeed: Int?,                           // разрешённая скорость, км/ч; null — тег не задан
        hasParallelCycleway: Boolean,                 // признак: вдоль пути идёт отдельная велодорожка
    ) {
        /**
         * Путь защищён выделенной велоинфраструктурой, если у него есть
         * собственный тег велоинфраструктуры (highway=cycleway либо
         * cycleway=lane/track) или вдоль него проходит обособленная велодорожка.
         */
        val isProtected: Boolean =
            // защищён, если у пути есть собственный тег велоинфраструктуры…
            OsmTags.hasOwnBikeInfrastructure(way) ||
                // …или рядом проходит отдельная велодорожка (определяется по геометрии).
                hasParallelCycleway

        /**
         * Путь комфортен (низкострессовый), если выполняется хотя бы одно из:
         *   – путь защищён велоинфраструктурой ([isProtected]);
         *   – тип highway сам по себе подразумевает отсутствие автодвижения
         *     (cycleway, living_street, path, pedestrian);
         *   – разрешённая скорость не выше 30 км/ч (спокойная улица).
         */
        val isLowStress: Boolean =
            // комфортно, если путь защищён велоинфраструктурой (см. выше)…
            isProtected ||
                // …или это тип, в принципе не предполагающий автомобилей…
                OsmTags.isLowStressHighway(way) ||
                // …или это улица со спокойной разрешённой скоростью (≤ 30 км/ч).
                (maxSpeed != null && maxSpeed <= OsmTags.CALM_SPEED_KMH)
    }

    /**
     * Главный метод: возвращает интегральную оценку Q и частные оценки по
     * пяти критериям для указанного набора картографических данных.
     */
    fun score(data: OsmData): QualityScore {
        // [1] Из всех путей OSM выделяем только те, что относятся к веломаршрутной
        //     сети: велодорожки, обычные улицы, дороги. Тротуары, лестницы и служебные
        //     проезды отсекаются — они либо непригодны для велосипеда, либо вносят шум.
        val networkWays = data.ways.filter { OsmTags.isNetworkWay(it) }
        // Если сети нет вовсе — возвращаем «пустой» результат с предупреждением.
        if (networkWays.isEmpty()) return emptyResult()

        // [2] Отдельно собираем все обособленные велодорожки (highway=cycleway)…
        val cyclewayWays = data.ways.filter { OsmTags.isStandaloneCycleway(it) }
        // …и строим по их узлам пространственный индекс. Он позволяет для любой
        // обычной дороги быстро узнать, проходит ли рядом отдельная велодорожка —
        // именно так велоинфраструктура чаще всего представлена в OSM.
        val proximityIndex = CyclewayProximityIndex(cyclewayWays, data.nodes)

        // [3] Преобразуем каждый путь сети в обогащённый объект NetworkWay.
        //     В нём заранее посчитаны длина, скорость и признак параллельной велодорожки —
        //     дальше критерии работают с этим объектом, а не с сырыми OSM-данными.
        val ways = networkWays.map { way ->
            // Параллельную велодорожку ищем только у дорог без собственной велоинфраструктуры:
            // у самих велодорожек этот признак вырожден (они сами себе параллельны).
            val hasParallel = !OsmTags.hasOwnBikeInfrastructure(way) &&
                // Дорога считается «накрытой» параллельной велодорожкой, когда доля её
                // узлов рядом с велодорожкой достигает порога. Это отделяет идущую вдоль
                // велодорожку от случайно пересекающейся в одной точке.
                proximityIndex.cyclewayCoverage(way) >= PARALLEL_COVERAGE_FRACTION
            NetworkWay(
                way = way,
                length = GeoUtils.wayLengthMeters(way, data.nodes),   // сумма расстояний по гаверсинусу
                maxSpeed = OsmTags.maxSpeedKmh(way),                  // парсер понимает RU:urban → 60
                hasParallelCycleway = hasParallel,
            )
        }

        // [4] Считаем все пять частных критериев. Каждый возвращает CriterionScore
        //     со значением, признаком применимости и числовой статистикой.
        val criteria = listOf(
            computeSafety(ways),          // S — безопасность (доля комфортной сети)
            computeContinuity(ways),      // N — непрерывность комфортной сети
            computeIntersections(data),   // I — организация перекрёстков
            computeHighSpeed(ways),       // V — велоинфраструктура на скоростных дорогах
            computeSurface(ways),         // P — качество покрытия
        )
        // [5] Считаем итоговое Q как взвешенную сумму. Если какой-то критерий неприменим,
        //     его вес исключается и из числителя, и из знаменателя (нормировка весов).
        val total = weightedTotal(criteria)
        // [6] Собираем итоговый объект: значение Q, частные оценки, структурированное
        //     резюме (для текста пояснения в UI) и опциональное предупреждение.
        return QualityScore(
            total = total,
            criteria = criteria,
            summary = buildSummary(total, criteria),
            dataWarning = buildWarning(ways, criteria),
        )
    }

    /** S — доля протяжённости низкострессовой (комфортной) сети. */
    private fun computeSafety(ways: List<NetworkWay>): CriterionScore {
        // Суммарная длина всей веломаршрутной сети области — знаменатель формулы S.
        val totalLength = ways.sumOf { it.length }
        // Суммарная длина только комфортных (низкострессовых) участков — числитель.
        val lowStressLength = ways.filter { it.isLowStress }.sumOf { it.length }
        // Делим одно на другое. Если знаменатель равен нулю, безопасно возвращаем 0.
        // coerceIn(0, 1) гарантирует, что из-за погрешности плавающей точки результат
        // не выйдет за допустимый диапазон [0; 1].
        val value = if (totalLength > 0.0) {
            (lowStressLength / totalLength).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        // Собираем результат критерия: само значение + сырые числа для UI.
        return CriterionScore(
            criterion = CriterionId.SAFETY,
            value = value,
            applicable = true,                  // сеть проверена выше; критерий всегда применим
            stats = CriterionStats(
                numerator = lowStressLength,    // длина комфортной части сети
                denominator = totalLength,      // длина всей сети
                isCount = false,                // числа — это длины в метрах, не количества
            ),
        )
    }

    /** N — доля протяжённости крупнейшей связной компоненты низкострессовой сети. */
    private fun computeContinuity(ways: List<NetworkWay>): CriterionScore {
        // Оставляем только комфортные участки — связность считается именно по ним.
        val lowStress = ways.filter { it.isLowStress }
        // Их суммарная длина — знаменатель критерия N.
        val lowStressLength = lowStress.sumOf { it.length }
        // Если комфортной сети нет вовсе — критерий не применим и исключается из Q.
        if (lowStress.isEmpty() || lowStressLength <= 0.0) {
            return CriterionScore(
                criterion = CriterionId.CONTINUITY,
                value = 0.0,
                applicable = false,
                notApplicableReason = NotApplicableReason.NO_LOW_STRESS_NETWORK,
            )
        }
        // [A] Применяем структуру «система непересекающихся множеств» (Union-Find).
        //     Идея: объединяем все узлы каждого пути в одно множество. После такого
        //     прохода два пути окажутся в одной компоненте тогда и только тогда,
        //     когда у них есть хотя бы один общий узел — то есть они физически соединены
        //     в дорожной сети.
        val disjointSet = DisjointSet()
        for (networkWay in lowStress) {
            val ids = networkWay.way.nodeIds                  // список узлов одного пути
            // Соединяем первый узел пути с каждым следующим — путь становится одной компонентой.
            for (k in 1 until ids.size) disjointSet.union(ids[0], ids[k])
        }
        // [B] Подсчитываем суммарную длину путей внутри каждой компоненты.
        //     Ключ Map — представитель множества (root), который возвращает find().
        val componentLength = HashMap<Long, Double>()
        for (networkWay in lowStress) {
            val firstNode = networkWay.way.nodeIds.firstOrNull() ?: continue
            val root = disjointSet.find(firstNode)            // к какой компоненте принадлежит путь
            componentLength[root] = (componentLength[root] ?: 0.0) + networkWay.length
        }
        // [C] Находим самую большую компоненту и делим её длину на общую — это и есть N.
        val maxLength = componentLength.values.maxOrNull() ?: 0.0
        val components = componentLength.size                  // общее число компонент (для UI)
        val value = (maxLength / lowStressLength).coerceIn(0.0, 1.0)
        return CriterionScore(
            criterion = CriterionId.CONTINUITY,
            value = value,
            applicable = true,
            stats = CriterionStats(
                numerator = maxLength,                         // длина крупнейшей компоненты
                denominator = lowStressLength,                 // длина всей комфортной сети
                isCount = false,
                componentCount = components,                   // используется в тексте пояснения
            ),
        )
    }

    /** I — доля перекрёстков с организованным (регулируемым/обозначенным) пересечением. */
    private fun computeIntersections(data: OsmData): CriterionScore {
        // Все узлы с тегом highway=crossing — это перекрёстки и пешеходные переходы.
        // Тег bicycle=yes (велопереезд) в OSM ставят крайне редко, поэтому в качестве
        // показателя безопасности перекрёстков мы используем общую организованность.
        val crossings = data.nodes.values.filter { OsmTags.isCrossing(it) }
        // Если разметки переходов в области нет — критерий не применим, исключаем.
        if (crossings.isEmpty()) {
            return CriterionScore(
                criterion = CriterionId.INTERSECTIONS,
                value = 1.0,
                applicable = false,
                notApplicableReason = NotApplicableReason.NO_CROSSINGS,
            )
        }
        // Считаем, сколько переходов из общего числа — «организованные» (со светофором
        // либо явной разметкой — zebra, marked); см. OsmTags.isOrganizedCrossing.
        val organized = crossings.count { OsmTags.isOrganizedCrossing(it) }
        // Доля организованных от общего числа переходов — это и есть значение критерия I.
        val value = (organized.toDouble() / crossings.size).coerceIn(0.0, 1.0)
        return CriterionScore(
            criterion = CriterionId.INTERSECTIONS,
            value = value,
            applicable = true,
            stats = CriterionStats(
                numerator = organized.toDouble(),
                denominator = crossings.size.toDouble(),
                isCount = true,                                // числа — это количества, не длины
            ),
        )
    }

    /** V — доля протяжённости скоростных дорог (> 40 км/ч), обеспеченных велоинфраструктурой. */
    private fun computeHighSpeed(ways: List<NetworkWay>): CriterionScore {
        // Скоростные дороги — те, где разрешённая скорость больше 40 км/ч.
        // Парсер скорости понимает и числа («60»), и зональные обозначения OSM:
        // RU:urban распознаётся как 60 км/ч, поэтому московские улицы корректно попадают сюда.
        val fastRoads = ways.filter {
            it.maxSpeed != null && it.maxSpeed > HIGH_SPEED_THRESHOLD_KMH
        }
        // Суммарная длина скоростных дорог — знаменатель критерия V.
        val fastLength = fastRoads.sumOf { it.length }
        // Если скоростных дорог в области нет — критерий не применим и исключается.
        if (fastRoads.isEmpty() || fastLength <= 0.0) {
            return CriterionScore(
                criterion = CriterionId.HIGH_SPEED,
                value = 1.0,
                applicable = false,
                notApplicableReason = NotApplicableReason.NO_FAST_ROADS,
            )
        }
        // Среди скоростных дорог выбираем «защищённые»: те, у которых есть собственная
        // велоинфраструктура (велополоса) или рядом проходит параллельная велодорожка.
        // Признак isProtected заранее вычислен при создании NetworkWay.
        val protectedLength = fastRoads.filter { it.isProtected }.sumOf { it.length }
        // Доля защищённой протяжённости от общей скоростной — это значение V.
        val value = (protectedLength / fastLength).coerceIn(0.0, 1.0)
        return CriterionScore(
            criterion = CriterionId.HIGH_SPEED,
            value = value,
            applicable = true,
            stats = CriterionStats(
                numerator = protectedLength,                   // длина защищённых скоростных дорог
                denominator = fastLength,                      // длина всех скоростных дорог
                isCount = false,
            ),
        )
    }

    /** P — доля объектов велоинфраструктуры с приемлемым покрытием. */
    private fun computeSurface(ways: List<NetworkWay>): CriterionScore {
        // Покрытие имеет смысл оценивать только для объектов велоинфраструктуры —
        // у обычных автомобильных дорог оно в данном критерии не учитывается.
        val infrastructure = ways.filter { it.isProtected }
        // Из велоинфраструктуры берём только те объекты, у которых задан тег surface.
        // Объекты без тега покрытия в расчёт не входят — про их покрытие мы попросту не знаем.
        val tagged = infrastructure.filter { OsmTags.hasSurfaceTag(it.way) }
        // Если ни у одного объекта тег surface не задан — критерий не применим.
        if (tagged.isEmpty()) {
            return CriterionScore(
                criterion = CriterionId.SURFACE,
                value = 1.0,
                applicable = false,
                notApplicableReason = NotApplicableReason.NO_SURFACE_DATA,
            )
        }
        // Считаем, у скольких объектов покрытие признано приемлемым
        // (asphalt / paved / concrete / paving_stones).
        val good = tagged.count { OsmTags.hasGoodSurface(it.way) }
        // Доля «хороших» от размеченных surface-объектов — это значение критерия P.
        val value = (good.toDouble() / tagged.size).coerceIn(0.0, 1.0)
        return CriterionScore(
            criterion = CriterionId.SURFACE,
            value = value,
            applicable = true,
            stats = CriterionStats(
                numerator = good.toDouble(),                   // объекты с хорошим покрытием
                denominator = tagged.size.toDouble(),          // объекты с заданным тегом surface
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
        // Берём только те критерии, для которых данных хватило.
        val applicable = criteria.filter { it.applicable }
        // Если ни один критерий не применим — рассчитать Q нечем, возвращаем 0.
        if (applicable.isEmpty()) return 0.0
        // Суммарный вес применимых критериев — знаменатель нормировки.
        val weightSum = applicable.sumOf { it.criterion.weight }
        // Защита от деления на ноль — на случай нулевых весов (теоретический случай).
        if (weightSum <= 0.0) return 0.0
        // Сумма «вес × значение» по применимым критериям — числитель нормировки.
        val weighted = applicable.sumOf { it.criterion.weight * it.value }
        // Деление даёт значение Q ∈ [0; 1]; coerceIn — страховка от плавающей точки.
        return (weighted / weightSum).coerceIn(0.0, 1.0)
    }

    /**
     * Структурированное резюме: качественный диапазон Q и идентификаторы
     * критериев с наибольшим/наименьшим значением. Текст пояснения собирает
     * presentation-слой из локализованных шаблонов.
     */
    private fun buildSummary(total: Double, criteria: List<CriterionScore>): ScoreSummary {
        // Резюме строим только по применимым критериям — иначе сравнение бессмысленно.
        val applicable = criteria.filter { it.applicable }
        return ScoreSummary(
            // Качественный диапазон Q: VERY_LOW / LOW / MEDIUM / GOOD / HIGH (см. ScoreBand.of).
            band = ScoreBand.of(total),
            // Критерий с самым высоким значением — он сильнее всего тянет Q вверх.
            bestCriterion = applicable.maxByOrNull { it.value }?.criterion,
            // Критерий с самым низким значением — он сильнее всего тянет Q вниз.
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
        // Количество критериев, которые пришлось исключить из расчёта (нет данных).
        val notApplicable = criteria.count { !it.applicable }
        return when {
            // Если дорог в области меньше порога — отдельный тип предупреждения с числом дорог.
            ways.size < MIN_WAYS_FOR_RELIABLE ->
                DataWarning(DataWarningType.FEW_ROADS, wayCount = ways.size)
            // Если исключено три и больше критериев — оценка по сути собрана из крошек.
            notApplicable >= 3 ->
                DataWarning(DataWarningType.MANY_CRITERIA_INAPPLICABLE)
            // Иначе предупреждение не нужно.
            else -> null
        }
    }

    /**
     * Особый случай: в области не нашлось ни одного пути сети. Возвращаем
     * нулевой Q и «пустые» структуры; UI отрисует соответствующее сообщение.
     */
    private fun emptyResult(): QualityScore {
        // Все пять критериев помечаем как неприменимые с общей причиной (нет данных).
        val criteria = CriterionId.entries.map {
            CriterionScore(
                criterion = it,
                value = 0.0,
                applicable = false,
                notApplicableReason = NotApplicableReason.INSUFFICIENT_DATA,
            )
        }
        return QualityScore(
            total = 0.0,                                       // Q = 0 — считать нечего
            criteria = criteria,
            summary = ScoreSummary(band = ScoreBand.VERY_LOW), // резюме без best/worst
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
