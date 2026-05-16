package com.example.courseproject.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Ответ Overpass API в формате JSON. */
data class OverpassResponseDto(
    @SerializedName("elements") val elements: List<OverpassElementDto>? = null,
)

/** Элемент ответа Overpass API — узел (node) либо путь (way). */
data class OverpassElementDto(
    @SerializedName("type") val type: String? = null,
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("lat") val lat: Double? = null,
    @SerializedName("lon") val lon: Double? = null,
    @SerializedName("nodes") val nodes: List<Long>? = null,
    @SerializedName("tags") val tags: Map<String, String>? = null,
)
