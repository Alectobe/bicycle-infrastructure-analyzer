package com.example.courseproject.data.mapper

import com.example.courseproject.data.remote.dto.OverpassResponseDto
import com.example.courseproject.domain.model.OsmData
import com.example.courseproject.domain.model.OsmNode
import com.example.courseproject.domain.model.OsmWay

/** Преобразование ответа Overpass API в доменную модель данных OSM. */
fun OverpassResponseDto.toOsmData(): OsmData {
    val rawElements = elements ?: emptyList()
    val nodes = rawElements
        .filter { it.type == "node" && it.lat != null && it.lon != null }
        .associate { dto ->
            dto.id to OsmNode(
                id = dto.id,
                lat = dto.lat!!,
                lon = dto.lon!!,
                tags = dto.tags ?: emptyMap(),
            )
        }
    val ways = rawElements
        .filter { it.type == "way" && it.nodes != null }
        .map { dto ->
            OsmWay(
                id = dto.id,
                nodeIds = dto.nodes ?: emptyList(),
                tags = dto.tags ?: emptyMap(),
            )
        }
    return OsmData(nodes = nodes, ways = ways)
}
