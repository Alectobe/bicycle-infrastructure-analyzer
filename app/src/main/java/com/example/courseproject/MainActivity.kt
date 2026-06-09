package com.example.courseproject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.courseproject.presentation.App
import com.example.courseproject.ui.theme.CourseProjectTheme

/**
 * Единственная Activity приложения — точка входа в Compose-интерфейс.
 *
 * Принцип «одна Activity» (single-activity architecture) — это рекомендация Google
 * для приложений на Jetpack Compose: всё навигируется внутри Compose, а Activity
 * отвечает только за инициализацию темы и подключение корневого composable.
 *
 * Класс наследуется от ComponentActivity — она предоставляет всё необходимое
 * для Compose: жизненный цикл, ViewModel-фабрику, savedStateRegistry и т. п.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge: системные панели (статус-бар и навигация) становятся прозрачными,
        // а контент может рисоваться под ними. Современный стандартный стиль Android.
        enableEdgeToEdge()
        // Достаём контейнер зависимостей из Application — он был создан в CourseProjectApp.onCreate().
        val container = (application as CourseProjectApp).container
        // setContent { … } — это «корень» Compose-интерфейса.
        // Всё, что внутри лямбды, будет автоматически перерисовано при изменении состояния.
        setContent {
            CourseProjectTheme {                  // подключаем Material-3 тему приложения
                App(container)                    // корневой composable с навигацией между экранами
            }
        }
    }
}
