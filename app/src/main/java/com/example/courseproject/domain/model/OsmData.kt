package com.example.courseproject.domain.model

/**
 * Узел OpenStreetMap — точка с географическими координатами и набором тегов.
 * Используется и как перекрёсток (теги highway=crossing, crossing=*),
 * и просто как геометрическая опорная точка путей.
 */
data class OsmNode(
    val id: Long,                                // уникальный идентификатор узла в OSM
    val lat: Double,                             // широта в градусах WGS84
    val lon: Double,                             // долгота в градусах WGS84
    val tags: Map<String, String> = emptyMap(),  // набор пар «ключ–значение» (теги OSM)
)

/**
 * Путь OpenStreetMap — упорядоченная последовательность узлов с набором тегов.
 * Описывает линейные объекты: дороги, велодорожки, тропы и т. п.
 */
data class OsmWay(
    val id: Long,                                // уникальный идентификатор пути в OSM
    val nodeIds: List<Long>,                     // упорядоченные id узлов, образующих путь
    val tags: Map<String, String> = emptyMap(),  // теги: highway=*, surface=*, maxspeed=*…
)

/**
 * Набор картографических данных выбранной области: узлы, индексированные
 * по идентификатору, и список путей дорожной сети.
 */
data class OsmData(
    val nodes: Map<Long, OsmNode>,               // словарь: id узла → узел (для быстрого доступа)
    val ways: List<OsmWay>,                      // список всех путей в области
) {
    /** В области нет ни одного пути — анализировать нечего. */
    val isEmpty: Boolean get() = ways.isEmpty()

    companion object {
        /** Удобная константа «пусто»: используется как fallback и в тестах. */
        val EMPTY = OsmData(emptyMap(), emptyList())
    }
}
