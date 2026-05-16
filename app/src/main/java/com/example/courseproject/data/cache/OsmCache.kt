package com.example.courseproject.data.cache

import com.example.courseproject.domain.model.BoundingBox
import java.io.File

/**
 * Локальный файловый кэш «сырых» ответов Overpass API.
 * Позволяет повторно анализировать территорию без обращения к сети.
 */
class OsmCache(cacheRoot: File) {

    private val directory: File = File(cacheRoot, "osm").apply { mkdirs() }

    fun read(bbox: BoundingBox): String? {
        val file = fileFor(bbox)
        return if (file.exists()) file.readText() else null
    }

    fun write(bbox: BoundingBox, json: String) {
        runCatching { fileFor(bbox).writeText(json) }
    }

    private fun fileFor(bbox: BoundingBox): File = File(directory, "osm_${bbox.cacheKey()}.json")
}
