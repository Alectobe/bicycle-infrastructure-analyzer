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
 *
 * Зачем ручной DI вместо Hilt/Koin. Для такого размера проекта явное создание
 * объектов проще и нагляднее любой DI-библиотеки: видно, как именно
 * подключается каждая зависимость. На защите легко показать пальцем строку
 * и сказать «вот здесь репозиторий получает HTTP-клиент и кэш».
 *
 * Зависимости объявлены как val by lazy — объекты создаются по первому
 * обращению. Это даёт два бонуса:
 *   – ленивая инициализация (если объект ни разу не понадобился, его не создадут);
 *   – ровно одна копия каждого объекта (lazy кеширует результат — singleton-семантика).
 */
class AppContainer(context: Context) {

    // Сохраняем именно application-context, чтобы не держать ссылку на Activity
    // и не вызывать утечек памяти, если контейнер переживёт перерисовку.
    private val appContext: Context = context.applicationContext

    /**
     * Репозиторий картографических данных. Тип объявлен как доменный интерфейс,
     * а конкретная реализация (с Overpass + кэшем) скрыта. Доменный слой и
     * presentation-слой работают только с интерфейсом — это и есть инверсия зависимостей.
     */
    val osmRepository: OsmRepository by lazy {
        OsmRepositoryImpl(
            api = OverpassApi(),                              // HTTP-клиент с настроенным User-Agent
            cache = OsmCache(appContext.cacheDir),            // файловый кэш в каталоге cache/
        )
    }

    /** Репозиторий истории. Хранит JSON-файл во внутреннем filesDir. */
    val historyRepository: HistoryRepository by lazy {
        HistoryRepositoryImpl(appContext.filesDir)
    }

    /**
     * Сценарий «Оценить территорию». Принимает два компонента:
     * репозиторий (для загрузки данных) и scorer (для расчёта оценки).
     * BikeInfrastructureScorer создаётся прямо здесь — у него нет внешних зависимостей.
     */
    val analyzeAreaUseCase: AnalyzeAreaUseCase by lazy {
        AnalyzeAreaUseCase(osmRepository, BikeInfrastructureScorer())
    }
}
