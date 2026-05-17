package com.example.courseproject.domain.analysis

import com.example.courseproject.domain.model.OsmNode
import com.example.courseproject.domain.model.OsmWay

/**
 * Пространственный индекс узлов выделенных велодорожек.
 *
 * В OpenStreetMap велоинфраструктура вдоль дороги обычно нанесена отдельным
 * путём (highway=cycleway), а не тегом на самой дороге. Индекс позволяет
 * определить, проходит ли вдоль дороги такая параллельная велодорожка.
 * Узлы велодорожек распределяются по равномерной сетке ячеек, что даёт
 * поиск ближайшего узла за время, близкое к константному.
 */
class CyclewayProximityIndex(
    cyclewayWays: List<OsmWay>,
    private val nodes: Map<Long, OsmNode>,
) {
    private val grid = HashMap<Long, MutableList<OsmNode>>()

    init {
        for (way in cyclewayWays) {
            for (nodeId in way.nodeIds) {
                val node = nodes[nodeId] ?: continue
                grid.getOrPut(cellKey(cellIndex(node.lat), cellIndex(node.lon))) {
                    mutableListOf()
                }.add(node)
            }
        }
    }

    /**
     * Доля узлов пути, рядом с которыми (в пределах [PROXIMITY_METERS] метров)
     * проходит велодорожка. Значение, близкое к 1, означает, что велодорожка
     * идёт вдоль дороги параллельно.
     */
    fun cyclewayCoverage(way: OsmWay): Double {
        var total = 0
        var covered = 0
        for (nodeId in way.nodeIds) {
            val node = nodes[nodeId] ?: continue
            total++
            if (hasCyclewayNear(node)) covered++
        }
        return if (total > 0) covered.toDouble() / total else 0.0
    }

    private fun hasCyclewayNear(point: OsmNode): Boolean {
        val baseI = cellIndex(point.lat)
        val baseJ = cellIndex(point.lon)
        for (di in -1..1) {
            for (dj in -1..1) {
                val bucket = grid[cellKey(baseI + di, baseJ + dj)] ?: continue
                for (node in bucket) {
                    val distance =
                        GeoUtils.haversineMeters(point.lat, point.lon, node.lat, node.lon)
                    if (distance <= PROXIMITY_METERS) return true
                }
            }
        }
        return false
    }

    private fun cellIndex(degrees: Double): Long =
        Math.floor(degrees / CELL_DEGREES).toLong()

    /** Упаковка индексов ячейки (каждый укладывается в 32 бита) в один ключ. */
    private fun cellKey(cellI: Long, cellJ: Long): Long =
        (cellI shl 32) xor (cellJ and 0xFFFFFFFFL)

    companion object {
        /** Порог расстояния, в пределах которого велодорожка считается параллельной, м. */
        const val PROXIMITY_METERS = 25.0
        private const val CELL_DEGREES = 0.0003
    }
}
