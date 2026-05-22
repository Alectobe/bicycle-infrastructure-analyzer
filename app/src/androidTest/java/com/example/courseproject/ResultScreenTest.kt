package com.example.courseproject

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.courseproject.domain.model.AreaEvaluation
import com.example.courseproject.domain.model.BoundingBox
import com.example.courseproject.domain.model.CriterionId
import com.example.courseproject.domain.model.CriterionScore
import com.example.courseproject.domain.model.CriterionStats
import com.example.courseproject.domain.model.QualityScore
import com.example.courseproject.domain.model.ScoreBand
import com.example.courseproject.domain.model.ScoreSummary
import com.example.courseproject.presentation.evaluation.EvaluationUiState
import com.example.courseproject.presentation.result.ResultScreen
import com.example.courseproject.ui.theme.CourseProjectTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-тесты экрана результата анализа территории.
 *
 * Ожидаемые строки берутся из тех же ресурсов Android, что использует UI,
 * поэтому тесты не зависят от текущей локали устройства.
 */
@RunWith(AndroidJUnit4::class)
class ResultScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun str(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun evaluation(id: String, index: Int, total: Double): AreaEvaluation {
        val criteria = CriterionId.entries.map { criterionId ->
            CriterionScore(
                criterion = criterionId,
                value = total,
                applicable = true,
                stats = CriterionStats(
                    numerator = total,
                    denominator = 1.0,
                    isCount = false,
                ),
            )
        }
        return AreaEvaluation(
            id = id,
            index = index,
            boundingBox = BoundingBox(55.0, 37.0, 55.1, 37.1),
            score = QualityScore(
                total = total,
                criteria = criteria,
                summary = ScoreSummary(band = ScoreBand.of(total)),
            ),
            timestamp = 0L,
        )
    }

    @Test
    fun resultScreen_displaysAreaNameScoreAndSummary() {
        val current = evaluation(id = "a", index = 1, total = 0.62)
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
        composeRule.onNodeWithText(str(R.string.area_name_format, 1)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.format_score, 0.62)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.result_summary_header))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.action_open_details))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun resultScreen_detailButtonTriggersNavigationCallback() {
        var detailOpened = false
        val current = evaluation(id = "a", index = 1, total = 0.5)
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
        composeRule.onNodeWithText(str(R.string.action_open_details))
            .performScrollTo()
            .performClick()
        assertTrue(detailOpened)
    }

    @Test
    fun resultScreen_opensSimilarAreaOnClick() {
        var openedId: String? = null
        val current = evaluation(id = "a", index = 1, total = 0.60)
        val similar = evaluation(id = "b", index = 2, total = 0.58)
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
        composeRule.onNodeWithText(str(R.string.area_name_format, 2))
            .performScrollTo()
            .performClick()
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
        composeRule.onNodeWithText(str(R.string.result_not_found)).assertIsDisplayed()
    }
}
