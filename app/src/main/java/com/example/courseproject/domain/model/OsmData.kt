package com.example.courseproject.domain.model

/** Узел OpenStreetMap — точка с географическими координатами и набором тегов. */
data class OsmNode(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val tags: Map<String, String> = emptyMap(),
)

/** Путь OpenStreetMap — упорядоченная последовательность узлов с набором тегов. */
data class OsmWay(
    val id: Long,
    val nodeIds: List<Long>,
    val tags: Map<String, String> = emptyMap(),
)

/**
 * Набор картографических данных выбранной области: узлы, индексированные
 * по идентификатору, и список путей дорожной сети.
 */
data class OsmData(
    val nodes: Map<Long, OsmNode>,
    val ways: List<OsmWay>,
) {
    val isEmpty: Boolean get() = ways.isEmpty()

    companion object {
        val EMPTY = OsmData(emptyMap(), emptyList())
    }
}
