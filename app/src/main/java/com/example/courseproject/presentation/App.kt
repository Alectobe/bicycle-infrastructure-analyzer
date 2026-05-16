package com.example.courseproject.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.courseproject.di.AppContainer
import com.example.courseproject.presentation.detail.DetailRoute
import com.example.courseproject.presentation.map.MapRoute
import com.example.courseproject.presentation.navigation.Screen
import com.example.courseproject.presentation.result.ResultRoute

/**
 * Корневой composable приложения. Реализует навигацию между экранами
 * на основе состояния (главный экран → результат → детализация).
 */
@Composable
fun App(container: AppContainer) {
    var screen: Screen by remember { mutableStateOf<Screen>(Screen.Map) }

    when (val current = screen) {
        Screen.Map -> MapRoute(
            container = container,
            onOpenResult = { id -> screen = Screen.Result(id) },
        )

        is Screen.Result -> {
            BackHandler { screen = Screen.Map }
            ResultRoute(
                container = container,
                evaluationId = current.evaluationId,
                onBack = { screen = Screen.Map },
                onOpenDetail = { screen = Screen.Detail(current.evaluationId) },
                onOpenResult = { id -> screen = Screen.Result(id) },
            )
        }

        is Screen.Detail -> {
            BackHandler { screen = Screen.Result(current.evaluationId) }
            DetailRoute(
                container = container,
                evaluationId = current.evaluationId,
                onBack = { screen = Screen.Result(current.evaluationId) },
            )
        }
    }
}
