package com.example.courseproject.di

import android.content.Context
import com.example.courseproject.data.cache.OsmCache
import com.example.courseproject.data.remote.OverpassApi
import com.example.courseproject.data.repository.HistoryRepositoryImpl
import com.example.courseproject.data.repository.OsmRepositoryImpl
import com.example.courseproject.domain.analysis.BikeInfrastructureScorer
import com.example.courseproject.domain.repository.HistoryRepository
import com.example.courseproject.domain.repository.OsmRepository
import com.example.courseproject.domain.usecase.AnalyzeAreaUseCase

/**
 * Контейнер зависимостей приложения — ручная реализация Dependency Injection.
 * Создаёт и связывает компоненты слоёв данных и предметной области.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val osmRepository: OsmRepository by lazy {
        OsmRepositoryImpl(
            api = OverpassApi(),
            cache = OsmCache(appContext.cacheDir),
        )
    }

    val historyRepository: HistoryRepository by lazy {
        HistoryRepositoryImpl(appContext.filesDir)
    }

    val analyzeAreaUseCase: AnalyzeAreaUseCase by lazy {
        AnalyzeAreaUseCase(osmRepository, BikeInfrastructureScorer())
    }
}
