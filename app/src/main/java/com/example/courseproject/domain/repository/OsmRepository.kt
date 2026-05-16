package com.example.courseproject.domain.repository

import com.example.courseproject.domain.model.BoundingBox
import com.example.courseproject.domain.model.OsmData

/** Источник картографических данных OpenStreetMap для выбранной области. */
interface OsmRepository {

    /**
     * Загружает данные OSM для указанной области.
     *
     * @param bbox область анализа.
     * @param forceRefresh true — игнорировать кэш и запросить данные заново.
     */
    suspend fun loadOsmData(bbox: BoundingBox, forceRefresh: Boolean = false): OsmData
}
