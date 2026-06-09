package com.example.courseproject

import android.app.Application
import com.example.courseproject.di.AppContainer
import org.osmdroid.config.Configuration

/**
 * Класс приложения: инициализирует библиотеку osmdroid и контейнер зависимостей.
 *
 * Application — единственный экземпляр на всё приложение, создаётся при запуске
 * процесса и живёт до его завершения. Поэтому здесь удобно делать одноразовую
 * инициализацию глобальных компонентов: библиотеки карт и контейнера DI.
 *
 * Чтобы Android знал использовать этот класс вместо стандартного Application,
 * он указан в AndroidManifest.xml в атрибуте android:name=".CourseProjectApp"
 * у тега <application>.
 */
class CourseProjectApp : Application() {

    // Контейнер зависимостей. lateinit — инициализация переносится из конструктора
    // в onCreate(). private set — снаружи свойство только для чтения.
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Загружаем настройки osmdroid из SharedPreferences (кэш тайлов, история позиций и т. п.).
        // Это требование библиотеки; без вызова библиотека работает с дефолтами и пишет в WARN.
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        // Задаём User-Agent для запросов к тайл-серверу OSM. Политика OSM требует, чтобы
        // приложение представлялось — иначе сервер блокирует запросы.
        Configuration.getInstance().userAgentValue = packageName
        // Собираем граф зависимостей. Один раз на всё приложение.
        container = AppContainer(this)
    }
}
