package com.example.courseproject.data.cache

import com.example.courseproject.domain.model.BoundingBox
import java.io.File

/**
 * Локальный файловый кэш «сырых» ответов Overpass API.
 *
 * Зачем это нужно. Запросы к Overpass API занимают секунды и потребляют
 * сетевой трафик. Если пользователь повторно анализирует ту же область
 * (или возвращается к ней после переключения экранов) — повторно тянуть
 * данные нерационально. Кэш хранит JSON-ответ на диске и отдаёт его
 * без обращения к сети при следующем запросе той же области.
 *
 * Ключ кэша — стабильный хеш bounding box (см. BoundingBox.cacheKey()),
 * который округляет координаты до ~1 м, чтобы крошечные сдвиги карты
 * не били мимо кэша.
 */
class OsmCache(cacheRoot: File) {

    // Каталог «osm/» внутри cacheRoot. apply { mkdirs() } сразу создаёт его,
    // если ещё нет (mkdirs молча возвращает, если каталог уже существует).
    private val directory: File = File(cacheRoot, "osm").apply { mkdirs() }

    /** Читает JSON-ответ из кэша; возвращает null, если для этой области файла ещё нет. */
    fun read(bbox: BoundingBox): String? {
        val file = fileFor(bbox)
        // Если файла ещё нет — кэш-промах, возвращаем null. Репозиторий пойдёт в сеть.
        return if (file.exists()) file.readText() else null
    }

    /** Сохраняет JSON-ответ в кэш. Ошибки записи (полный диск и т. п.) тихо игнорируем. */
    fun write(bbox: BoundingBox, json: String) {
        runCatching { fileFor(bbox).writeText(json) }
    }

    /** Возвращает файл, в который пишется/из которого читается кэш для данной области. */
    private fun fileFor(bbox: BoundingBox): File = File(directory, "osm_${bbox.cacheKey()}.json")
}
