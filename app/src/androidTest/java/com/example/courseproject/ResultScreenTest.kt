package com.example.courseproject

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.courseproject.domain.model.AreaEvaluation
import com.example.courseproject.domain.model.BoundingBox
import com.example.courseproject.domain.model.CriterionId
import com.example.courseproject.domain.model.CriterionScore
import com.example.courseproject.domain.model.QualityScore
import com.example.courseproject.presentation.evaluation.EvaluationUiState
import com.example.courseproject.presentation.result.ResultScreen
import com.example.courseproject.ui.theme.CourseProjectTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI-тесты экрана результата анализа территории. */
@RunWith(AndroidJUnit4::class)
class ResultScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun evaluation(
        id: String,
        name: String,
        total: Double,
        summary: String = "Краткое пояснение к итоговой оценке территории.",
    ): AreaEvaluation {
        val criteria = CriterionId.entries.map { criterion ->
            CriterionScore(
                criterion = criterion,
                value = total,
                applicable = true,
                detail = "Пояснение по критерию ${criterion.code}.",
            )
        }
        return AreaEvaluation(
            id = id,
            name = name,
            boundingBox = BoundingBox(55.0, 37.0, 55.1, 37.1),
            score = QualityScore(total = total, criteria = criteria, summary = summary),
            timestamp = 0L,
        )
    }

    @Test
    fun resultScreen_displaysAreaNameScoreAndSummary() {
        val current = evaluation(id = "a", name = "Тестовый район", total = 0.62)
        composeRule.setContent {
            CourseProjectTheme {
                ResultScreen(
                    state = EvaluationUiState(evaluation = current, similar = emptyList()),
                    onBack = {},
                    onOpenDetail = {},
                    onOpenResult = {},
                )
            }
        }
        composeRule.onNodeWithText("Тестовый район").assertIsDisplayed()
        composeRule.onNodeWithText("0.62").assertIsDisplayed()
        composeRule.onNodeWithText("Краткое пояснение к итоговой оценке территории.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Подробнее по критериям")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun resultScreen_detailButtonTriggersNavigationCallback() {
        var detailOpened = false
        val current = evaluation(id = "a", name = "Район A", total = 0.5)
        composeRule.setContent {
            CourseProjectTheme {
                ResultScreen(
                    state = EvaluationUiState(evaluation = current, similar = emptyList()),
                    onBack = {},
                    onOpenDetail = { detailOpened = true },
                    onOpenResult = {},
                )
            }
        }
        composeRule.onNodeWithText("Подробнее по критериям").performScrollTo().performClick()
        assertTrue(detailOpened)
    }

    @Test
    fun resultScreen_opensSimilarAreaOnClick() {
        var openedId: String? = null
        val current = evaluation(id = "a", name = "Район A", total = 0.60)
        val similar = evaluation(id = "b", name = "Район Б", total = 0.58)
        composeRule.setContent {
            CourseProjectTheme {
                ResultScreen(
                    state = EvaluationUiState(evaluation = current, similar = listOf(similar)),
                    onBack = {},
                    onOpenDetail = {},
                    onOpenResult = { openedId = it },
                )
            }
        }
        composeRule.onNodeWithText("Район Б").performScrollTo().performClick()
        assertEquals("b", openedId)
    }

    @Test
    fun resultScreen_showsNotFoundMessageWhenEvaluationMissing() {
        composeRule.setContent {
            CourseProjectTheme {
                ResultScreen(
                    state = EvaluationUiState(evaluation = null, similar = emptyList()),
                    onBack = {},
                    onOpenDetail = {},
                    onOpenResult = {},
                )
            }
        }
        composeRule.onNodeWithText("Запись анализа не найдена.").assertIsDisplayed()
    }
}
