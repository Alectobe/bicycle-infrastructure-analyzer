package com.example.courseproject.presentation.navigation

/** Экраны приложения и параметры навигации между ними. */
sealed interface Screen {

    /** Главный экран с картой и историей расчётов. */
    data object Map : Screen

    /** Экран итогового результата анализа территории. */
    data class Result(val evaluationId: String) : Screen

    /** Экран детализации оценки по отдельным критериям. */
    data class Detail(val evaluationId: String) : Screen
}
