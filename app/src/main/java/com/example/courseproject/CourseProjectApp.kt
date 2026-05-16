package com.example.courseproject

import android.app.Application
import com.example.courseproject.di.AppContainer
import org.osmdroid.config.Configuration

/** Класс приложения: инициализирует библиотеку osmdroid и контейнер зависимостей. */
class CourseProjectApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        container = AppContainer(this)
    }
}
