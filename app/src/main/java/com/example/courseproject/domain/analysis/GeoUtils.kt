package com.example.courseproject.domain.analysis

import com.example.courseproject.domain.model.OsmNode
import com.example.courseproject.domain.model.OsmWay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Геометрические расчёты на сфере для географических координат.
 *
 * Все расчёты ведутся в метрах. Земля моделируется сферой среднего радиуса
 * 6 371 000 м — для задачи «попадает ли узел в радиус 25 м» этого
 * приближения с лихвой достаточно (погрешность сферы относительно эллипсоида
 * меньше промилле).
 */
object GeoUtils {

    /** Средний радиус Земли в метрах. */
    private const val EARTH_RADIUS_M = 6_371_000.0

    /**
     * Расстояние между двумя точками по формуле гаверсинуса, в метрах.
     *
     * Формула гаверсинуса даёт длину кратчайшей дуги между двумя точками
     * на сфере. В отличие от прямой подстановки в Пифагора, она корректно
     * работает на любых широтах и долготах, включая полюса и переход через 180°.
     */
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        // Разности широты и долготы переводим из градусов в радианы — sin/cos работают именно с ними.
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        // Промежуточная величина a — квадрат «полухорды» в радианах.
        // Формула: a = sin²(Δφ/2) + cos(φ₁)·cos(φ₂)·sin²(Δλ/2).
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        // Угол между точками: 2·atan2(√a, √(1−a)). Расстояние = радиус × угол.
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Длина пути — сумма расстояний между соседними узлами, в метрах.
     *
     * Путь в OSM хранится как упорядоченный список идентификаторов узлов;
     * длина считается как сумма дуг между каждой парой соседей.
     */
    fun wayLengthMeters(way: OsmWay, nodes: Map<Long, OsmNode>): Double {
        var length = 0.0
        // Идём по парам соседних узлов: (0,1), (1,2), …, (n−2, n−1).
        for (i in 0 until way.nodeIds.size - 1) {
            // Подгружаем оба узла по их id; если какого-то нет в словаре — пропускаем сегмент.
            val from = nodes[way.nodeIds[i]] ?: continue
            val to = nodes[way.nodeIds[i + 1]] ?: continue
            // Прибавляем длину сегмента, посчитанную через гаверсинус.
            length += haversineMeters(from.lat, from.lon, to.lat, to.lon)
        }
        return length
    }
}
