package com.example.courseproject.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Ответ Overpass API в формате JSON.
 *
 * Все поля nullable, потому что Gson при отсутствии поля в JSON просто
 * выставит null — а в выдаче Overpass поля могут отсутствовать в зависимости
 * от типа элемента. Жёсткая валидация выполняется на этапе маппинга в домен.
 */
data class OverpassResponseDto(
    @SerializedName("elements")
    val elements: List<OverpassElementDto>? = null,   // массив объектов: узлы и пути вперемешку
)

/**
 * Элемент ответа Overpass API — узел (node) либо путь (way).
 *
 * В одном JSON-объекте Overpass возвращает и узлы, и пути: тип определяется
 * полем "type". У узла есть lat/lon, но нет nodes; у пути — наоборот.
 * Поэтому большая часть полей объявлена nullable и заполняется по типу элемента.
 */
data class OverpassElementDto(
    @SerializedName("type")
    val type: String? = null,                          // "node" или "way"

    @SerializedName("id")
    val id: Long = 0L,                                  // числовой идентификатор объекта в OSM

    @SerializedName("lat")
    val lat: Double? = null,                            // широта (только у узлов)

    @SerializedName("lon")
    val lon: Double? = null,                            // долгота (только у узлов)

    @SerializedName("nodes")
    val nodes: List<Long>? = null,                      // упорядоченные id узлов (только у путей)

    @SerializedName("tags")
    val tags: Map<String, String>? = null,              // набор тегов «ключ-значение»
)
