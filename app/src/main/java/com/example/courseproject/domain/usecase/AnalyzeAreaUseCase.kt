package com.example.courseproject.domain.usecase

import com.example.courseproject.domain.analysis.BikeInfrastructureScorer
import com.example.courseproject.domain.model.BoundingBox
import com.example.courseproject.domain.model.QualityScore
import com.example.courseproject.domain.repository.OsmRepository

/**
 * Сценарий «Оценить территорию»: загружает данные OSM для выбранной
 * области и рассчитывает интегральную оценку качества велоинфраструктуры.
 */
class AnalyzeAreaUseCase(
    private val osmRepository: OsmRepository,
    private val scorer: BikeInfrastructureScorer,
) {
    suspend operator fun invoke(bbox: BoundingBox, forceRefresh: Boolean = false): QualityScore {
        val data = osmRepository.loadOsmData(bbox, forceRefresh)
        return scorer.score(data)
    }
}
