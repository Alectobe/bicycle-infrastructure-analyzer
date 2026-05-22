package com.example.courseproject.presentation.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.courseproject.R
import com.example.courseproject.domain.model.AnalysisError
import com.example.courseproject.domain.model.CriterionId
import com.example.courseproject.domain.model.CriterionScore
import com.example.courseproject.domain.model.DataWarning
import com.example.courseproject.domain.model.DataWarningType
import com.example.courseproject.domain.model.NotApplicableReason
import com.example.courseproject.domain.model.QualityScore
import com.example.courseproject.domain.model.ScoreBand
import kotlin.math.roundToInt

/**
 * Набор @Composable-помощников, преобразующих структурированные доменные
 * данные в локализованные строки через ресурсы Android.
 *
 * Все функции вызываются только из Compose и автоматически используют
 * текущую локаль устройства как для выбора текстов (res/values/ либо
 * res/values-en/), так и для форматирования чисел (десятичный разделитель).
 */

/** Численное значение Q или критерия с двумя знаками после разделителя. */
@Composable
fun formatScore(value: Double): String =
    stringResource(R.string.format_score, value)

/** Длина в метрах в виде «12.3 km» / «12,3 км» с одним знаком. */
@Composable
fun formatKm(meters: Double): String =
    stringResource(R.string.format_km, meters / 1000.0)

/** Доля [0; 1] в виде целочисленного процента: 0.42 → «42%». */
@Composable
fun formatPercent(fraction: Double): String =
    stringResource(R.string.format_percent, (fraction * 100).roundToInt())

/** Локализованное название качественного диапазона оценки. */
@Composable
fun localizedBand(band: ScoreBand): String = stringResource(
    when (band) {
        ScoreBand.VERY_LOW -> R.string.band_very_low
        ScoreBand.LOW -> R.string.band_low
        ScoreBand.MEDIUM -> R.string.band_medium
        ScoreBand.GOOD -> R.string.band_good
        ScoreBand.HIGH -> R.string.band_high
    },
)

/** Локализованное полное название критерия. */
@Composable
fun localizedCriterionTitle(criterion: CriterionId): String = stringResource(
    when (criterion) {
        CriterionId.SAFETY -> R.string.criterion_safety_title
        CriterionId.CONTINUITY -> R.string.criterion_continuity_title
        CriterionId.INTERSECTIONS -> R.string.criterion_intersections_title
        CriterionId.HIGH_SPEED -> R.string.criterion_high_speed_title
        CriterionId.SURFACE -> R.string.criterion_surface_title
    },
)

/** Локализованное сообщение об ошибке анализа. */
@Composable
fun localizedAnalysisError(error: AnalysisError): String = when (error) {
    AnalysisError.NetworkUnavailable -> stringResource(R.string.error_network)
    is AnalysisError.HttpError -> stringResource(R.string.error_http_format, error.code)
    AnalysisError.EmptyResponse -> stringResource(R.string.error_empty_response)
    AnalysisError.ParseError -> stringResource(R.string.error_parse)
    AnalysisError.Unknown -> stringResource(R.string.error_unknown)
}

/** Локализованное предупреждение о неполноте данных. */
@Composable
fun localizedDataWarning(warning: DataWarning): String = when (warning.type) {
    DataWarningType.FEW_ROADS ->
        stringResource(R.string.warning_few_roads_format, warning.wayCount)
    DataWarningType.MANY_CRITERIA_INAPPLICABLE ->
        stringResource(R.string.warning_many_inapplicable)
    DataWarningType.NO_NETWORK ->
        stringResource(R.string.warning_no_network)
}

/** Локализованное объяснение причины, по которой критерий неприменим. */
@Composable
fun localizedNotApplicableReason(reason: NotApplicableReason): String = stringResource(
    when (reason) {
        NotApplicableReason.NO_LOW_STRESS_NETWORK -> R.string.not_applicable_no_low_stress
        NotApplicableReason.NO_CROSSINGS -> R.string.not_applicable_no_crossings
        NotApplicableReason.NO_FAST_ROADS -> R.string.not_applicable_no_fast_roads
        NotApplicableReason.NO_SURFACE_DATA -> R.string.not_applicable_no_surface_data
        NotApplicableReason.INSUFFICIENT_DATA -> R.string.not_applicable_insufficient_data
    },
)

/**
 * Локализованное пояснение по конкретному критерию.
 *
 * Если критерий неприменим — возвращает соответствующее объяснение
 * причины из ресурсов. Если применим — собирает шаблон детализации
 * для данного критерия с подставленными значениями.
 */
@Composable
fun localizedCriterionDetail(score: CriterionScore): String {
    if (!score.applicable) {
        val reason = score.notApplicableReason ?: NotApplicableReason.INSUFFICIENT_DATA
        return localizedNotApplicableReason(reason)
    }
    val stats = score.stats
        ?: return localizedNotApplicableReason(NotApplicableReason.INSUFFICIENT_DATA)
    return when (score.criterion) {
        CriterionId.SAFETY -> stringResource(
            R.string.detail_safety_format,
            formatPercent(score.value),
            formatKm(stats.numerator),
            formatKm(stats.denominator),
        )
        CriterionId.CONTINUITY -> if (stats.componentCount <= 1) {
            stringResource(
                R.string.detail_continuity_single_format,
                formatKm(stats.numerator),
            )
        } else {
            stringResource(
                R.string.detail_continuity_multi_format,
                formatPercent(score.value),
                formatKm(stats.numerator),
                formatKm(stats.denominator),
                stats.componentCount,
            )
        }
        CriterionId.INTERSECTIONS -> stringResource(
            R.string.detail_intersections_format,
            stats.numerator.toInt(),
            stats.denominator.toInt(),
        )
        CriterionId.HIGH_SPEED -> stringResource(
            R.string.detail_high_speed_format,
            formatPercent(score.value),
            formatKm(stats.numerator),
            formatKm(stats.denominator),
        )
        CriterionId.SURFACE -> stringResource(
            R.string.detail_surface_format,
            stats.numerator.toInt(),
            stats.denominator.toInt(),
        )
    }
}

/**
 * Локализованное краткое резюме итоговой оценки.
 *
 * Включает качественный диапазон, числовое значение Q и (если есть несколько
 * применимых критериев) указание наиболее и наименее значимых.
 */
@Composable
fun localizedScoreSummary(score: QualityScore): String {
    val applicable = score.criteria.filter { it.applicable }
    if (applicable.isEmpty()) {
        return stringResource(R.string.summary_no_data)
    }
    val bandText = localizedBand(score.summary.band)
    val scoreText = formatScore(score.total)
    val header = stringResource(R.string.summary_band_format, bandText, scoreText)
    val best = score.summary.bestCriterion
    val worst = score.summary.worstCriterion
    val tail = if (best == null || worst == null || best == worst) {
        stringResource(R.string.summary_equal_criteria)
    } else {
        val bestTitle = localizedCriterionTitle(best)
        val worstTitle = localizedCriterionTitle(worst)
        val bestValue = score.criterion(best)?.value ?: 0.0
        val worstValue = score.criterion(worst)?.value ?: 0.0
        stringResource(
            R.string.summary_best_worst,
            bestTitle,
            formatScore(bestValue),
            worstTitle,
            formatScore(worstValue),
        )
    }
    return "$header $tail"
}
