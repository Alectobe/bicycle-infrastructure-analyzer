package com.example.courseproject.domain.repository

import com.example.courseproject.domain.model.BoundingBox
import com.example.courseproject.domain.model.OsmData

/**
 * Источник картографических данных OpenStreetMap для выбранной области.
 *
 * Интерфейс объявлен в доменном слое; реализация ([com.example.courseproject.data])
 * подгружается из data-слоя через контейнер зависимостей. Такой приём
 * (Dependency Inversion) гарантирует, что доменный слой не зависит от деталей
 * сети, кэша или формата ответа — он работает только с этим контрактом.
 */
interface OsmRepository {

    /**
     * Загружает данные OSM для указанной области.
     *
     * @param bbox область анализа.
     * @param forceRefresh true — игнорировать кэш и запросить данные заново.
     */
    suspend fun loadOsmData(bbox: BoundingBox, forceRefresh: Boolean = false): OsmData
}
