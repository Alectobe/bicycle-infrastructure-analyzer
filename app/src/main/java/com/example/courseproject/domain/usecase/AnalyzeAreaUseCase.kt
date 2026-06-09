package com.example.courseproject.domain.usecase

import com.example.courseproject.domain.analysis.BikeInfrastructureScorer
import com.example.courseproject.domain.model.BoundingBox
import com.example.courseproject.domain.model.QualityScore
import com.example.courseproject.domain.repository.OsmRepository

/**
 * Сценарий «Оценить территорию»: загружает данные OSM для выбранной
 * области и рассчитывает интегральную оценку качества велоинфраструктуры.
 *
 * Реализует архитектурный паттерн «Use Case» (сценарий использования)
 * из Clean Architecture: бизнес-операция инкапсулирована в одном классе
 * и состоит из двух шагов — получить картографические данные и применить
 * к ним модель оценки. Класс ничего не знает ни про источник данных
 * (это абстрагировано репозиторием), ни про внутренние детали алгоритма
 * (это инкапсулировано в scorer) — он только связывает их в нужном порядке.
 */
class AnalyzeAreaUseCase(
    private val osmRepository: OsmRepository,        // источник картографических данных
    private val scorer: BikeInfrastructureScorer,    // реализация модели оценки качества
) {
    /**
     * Запускает сценарий для указанной области. Помечен оператором invoke —
     * объект можно вызывать как функцию: useCase(bbox).
     *
     * @param bbox прямоугольная область, по которой нужно посчитать оценку.
     * @param forceRefresh true — игнорировать кэш и запросить данные у Overpass API заново.
     */
    suspend operator fun invoke(bbox: BoundingBox, forceRefresh: Boolean = false): QualityScore {
        // [1] Через репозиторий получаем картографические данные выбранной области.
        //     Конкретный механизм (сеть + кэш) реализован в data-слое и здесь не важен.
        val data = osmRepository.loadOsmData(bbox, forceRefresh)
        // [2] Передаём данные алгоритму оценки и возвращаем результат вызывающему коду.
        return scorer.score(data)
    }
}
