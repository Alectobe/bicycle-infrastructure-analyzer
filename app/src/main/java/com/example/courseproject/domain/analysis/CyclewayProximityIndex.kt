package com.example.courseproject.domain.analysis

import com.example.courseproject.domain.model.OsmNode
import com.example.courseproject.domain.model.OsmWay

/**
 * Пространственный индекс узлов выделенных велодорожек.
 *
 * В OpenStreetMap велоинфраструктура вдоль дороги обычно нанесена отдельным
 * путём (highway=cycleway), а не тегом на самой дороге. Индекс позволяет
 * определить, проходит ли вдоль дороги такая параллельная велодорожка.
 *
 * Реализация. Узлы всех велодорожек распределяются по равномерной сетке
 * квадратных ячеек ~30 м (CELL_DEGREES). Для проверки «есть ли велодорожка
 * рядом с точкой» достаточно посмотреть саму ячейку и 8 соседних — это
 * быстро (константное число бакетов) и не требует расчёта расстояния
 * до каждого узла велодорожки в области.
 */
class CyclewayProximityIndex(
    cyclewayWays: List<OsmWay>,                  // велодорожки, узлы которых индексируем
    private val nodes: Map<Long, OsmNode>,       // словарь узлов по id (нужен для координат)
) {
    // Сетка: ключ ячейки → список узлов велодорожек, попавших в эту ячейку.
    private val grid = HashMap<Long, MutableList<OsmNode>>()

    init {
        // Построение индекса: проходим все узлы всех велодорожек один раз
        // и раскладываем их по ячейкам сетки. Время — O(N), где N — число узлов.
        for (way in cyclewayWays) {
            for (nodeId in way.nodeIds) {
                // Если узел почему-то отсутствует в словаре — пропускаем.
                val node = nodes[nodeId] ?: continue
                // Кладём узел в ячейку, в которую он попадает по своим координатам.
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
        var total = 0                                    // общее число узлов пути
        var covered = 0                                  // из них — рядом с велодорожкой
        for (nodeId in way.nodeIds) {
            val node = nodes[nodeId] ?: continue
            total++
            if (hasCyclewayNear(node)) covered++
        }
        // Если у пути ни одного узла — возвращаем 0 (заодно избегаем деления на ноль).
        return if (total > 0) covered.toDouble() / total else 0.0
    }

    /**
     * Проверяет, есть ли в радиусе [PROXIMITY_METERS] метров от точки
     * хотя бы один узел велодорожки.
     */
    private fun hasCyclewayNear(point: OsmNode): Boolean {
        // Определяем «свою» ячейку для точки.
        val baseI = cellIndex(point.lat)
        val baseJ = cellIndex(point.lon)
        // Перебираем 9 ячеек: «свою» (di=dj=0) и 8 соседних. Этого хватает,
        // потому что 25 м (порог) меньше, чем размер ячейки (~30 м).
        for (di in -1..1) {
            for (dj in -1..1) {
                // Если ячейка пустая — пропускаем, идём к следующей.
                val bucket = grid[cellKey(baseI + di, baseJ + dj)] ?: continue
                // Для каждого узла велодорожки в ячейке считаем настоящее
                // расстояние по гаверсинусу — сетка лишь сужает кандидатов.
                for (node in bucket) {
                    val distance =
                        GeoUtils.haversineMeters(point.lat, point.lon, node.lat, node.lon)
                    if (distance <= PROXIMITY_METERS) return true   // нашли — выходим
                }
            }
        }
        return false                                     // во всех 9 ячейках ничего подходящего нет
    }

    /** Индекс ячейки по одной координате: floor от деления на размер ячейки. */
    private fun cellIndex(degrees: Double): Long =
        Math.floor(degrees / CELL_DEGREES).toLong()

    /**
     * Упаковка индексов ячейки (каждый укладывается в 32 бита) в один Long-ключ.
     * Используется как ключ HashMap — так избегаем создания вспомогательного объекта-пары.
     */
    private fun cellKey(cellI: Long, cellJ: Long): Long =
        (cellI shl 32) xor (cellJ and 0xFFFFFFFFL)

    companion object {
        /** Порог расстояния, в пределах которого велодорожка считается параллельной, м. */
        const val PROXIMITY_METERS = 25.0

        /**
         * Размер ячейки сетки в градусах. 0.0003° по широте ≈ 33 м;
         * на широте Москвы по долготе ≈ 20 м. Размер чуть больше порога
         * PROXIMITY_METERS, чтобы поиск по 3×3 ячейкам гарантированно
         * охватывал все узлы в пределах радиуса.
         */
        private const val CELL_DEGREES = 0.0003
    }
}
