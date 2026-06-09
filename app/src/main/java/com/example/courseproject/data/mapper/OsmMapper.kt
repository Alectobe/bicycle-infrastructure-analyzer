package com.example.courseproject.data.mapper

import com.example.courseproject.data.remote.dto.OverpassResponseDto
import com.example.courseproject.domain.model.OsmData
import com.example.courseproject.domain.model.OsmNode
import com.example.courseproject.domain.model.OsmWay

/**
 * Преобразование ответа Overpass API в доменную модель данных OSM.
 *
 * Слой данных получает «сырые» DTO с nullable-полями (потому что JSON
 * может быть любым); доменный слой работает с уже валидированными
 * не-nullable объектами OsmNode и OsmWay. Этот маппер — граница, на которой
 * происходит фильтрация и валидация.
 *
 * Реализован как extension-функция на DTO — это позволяет писать
 * dto.toOsmData() прямо в репозитории, без отдельного класса-преобразователя.
 */
fun OverpassResponseDto.toOsmData(): OsmData {
    // Если elements в ответе nulled (например, ответ был пустым или некорректным),
    // используем пустой список, чтобы дальнейшие фильтры не упали.
    val rawElements = elements ?: emptyList()

    // Узлы: фильтруем только type=="node" с валидными координатами,
    // и собираем в Map (id → OsmNode) — так доступ по id будет O(1).
    val nodes = rawElements
        .filter { it.type == "node" && it.lat != null && it.lon != null }
        .associate { dto ->
            dto.id to OsmNode(
                id = dto.id,
                lat = dto.lat!!,                       // выше уже проверили на null
                lon = dto.lon!!,
                tags = dto.tags ?: emptyMap(),
            )
        }

    // Пути: фильтруем только type=="way" с непустым списком nodes,
    // и собираем в List в исходном порядке появления в ответе.
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
