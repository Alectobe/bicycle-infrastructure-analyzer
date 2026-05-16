package com.example.courseproject.domain.analysis

/**
 * Система непересекающихся множеств (Union-Find) со сжатием путей
 * и объединением по рангу. Применяется для поиска связных компонент
 * графа велосипедной инфраструктуры при расчёте критерия непрерывности.
 */
class DisjointSet {

    private val parent = HashMap<Long, Long>()
    private val rank = HashMap<Long, Int>()

    fun add(element: Long) {
        if (element !in parent) {
            parent[element] = element
            rank[element] = 0
        }
    }

    /** Возвращает представителя множества, которому принадлежит элемент. */
    fun find(element: Long): Long {
        add(element)
        var root = element
        while (parent[root] != root) {
            root = parent.getValue(root)
        }
        var current = element
        while (parent[current] != root) {
            val next = parent.getValue(current)
            parent[current] = root
            current = next
        }
        return root
    }

    /** Объединяет множества, содержащие два элемента. */
    fun union(a: Long, b: Long) {
        val rootA = find(a)
        val rootB = find(b)
        if (rootA == rootB) return
        val rankA = rank.getValue(rootA)
        val rankB = rank.getValue(rootB)
        when {
            rankA < rankB -> parent[rootA] = rootB
            rankA > rankB -> parent[rootB] = rootA
            else -> {
                parent[rootB] = rootA
                rank[rootA] = rankA + 1
            }
        }
    }

    fun connected(a: Long, b: Long): Boolean = find(a) == find(b)
}
