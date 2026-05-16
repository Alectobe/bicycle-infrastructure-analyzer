package com.example.courseproject.domain.analysis

import com.example.courseproject.domain.model.OsmNode
import com.example.courseproject.domain.model.OsmWay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Геометрические расчёты на сфере для географических координат. */
object GeoUtils {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Расстояние между двумя точками по формуле гаверсинуса, в метрах. */
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Длина пути — сумма расстояний между соседними узлами, в метрах. */
    fun wayLengthMeters(way: OsmWay, nodes: Map<Long, OsmNode>): Double {
        var length = 0.0
        for (i in 0 until way.nodeIds.size - 1) {
            val from = nodes[way.nodeIds[i]] ?: continue
            val to = nodes[way.nodeIds[i + 1]] ?: continue
            length += haversineMeters(from.lat, from.lon, to.lat, to.lon)
        }
        return length
    }
}
