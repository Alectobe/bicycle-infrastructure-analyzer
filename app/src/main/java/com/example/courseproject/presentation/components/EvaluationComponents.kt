package com.example.courseproject.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.courseproject.R
import com.example.courseproject.domain.model.AreaEvaluation
import com.example.courseproject.presentation.format.formatScore
import com.example.courseproject.presentation.format.localizedBand

/** Круглый бейдж с числовым значением показателя качества. */
@Composable
fun ScoreBadge(value: Double, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(scoreColor(value)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = formatScore(value),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** Строка списка с краткой информацией о рассчитанной территории. */
@Composable
fun EvaluationRow(
    evaluation: AreaEvaluation,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = stringResource(R.string.area_name_format, evaluation.index)
    val bandText = localizedBand(evaluation.score.summary.band)
    val qualityLabel = stringResource(R.string.quality_label_format, bandText)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScoreBadge(value = evaluation.score.total)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = qualityLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
