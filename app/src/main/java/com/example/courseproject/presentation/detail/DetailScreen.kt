package com.example.courseproject.presentation.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.courseproject.di.AppContainer
import com.example.courseproject.domain.model.AreaEvaluation
import com.example.courseproject.domain.model.CriterionScore
import com.example.courseproject.presentation.components.formatScore
import com.example.courseproject.presentation.components.scoreColor
import com.example.courseproject.presentation.evaluation.EvaluationViewModel

/** Маршрут экрана детализации: связывает [EvaluationViewModel] с UI. */
@Composable
fun DetailRoute(
    container: AppContainer,
    evaluationId: String,
    onBack: () -> Unit,
) {
    val viewModel: EvaluationViewModel = viewModel(key = evaluationId) {
        EvaluationViewModel(container.historyRepository, evaluationId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    DetailScreen(evaluation = state.evaluation, onBack = onBack)
}

/** Экран детализации итоговой оценки по пяти критериям модели. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    evaluation: AreaEvaluation?,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Детализация по критериям") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        if (evaluation == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Запись анализа не найдена.")
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text(evaluation.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Итоговая оценка Q = ${formatScore(evaluation.score.total)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(evaluation.score.criteria) { criterion ->
                CriterionCard(criterion)
            }
        }
    }
}

@Composable
private fun CriterionCard(score: CriterionScore) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = score.criterion.code,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(score.criterion.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Вес критерия — ${formatScore(score.criterion.weight)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = if (score.applicable) formatScore(score.value) else "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (score.applicable) {
                        scoreColor(score.value)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            if (score.applicable) {
                LinearProgressIndicator(
                    progress = { score.value.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                    color = scoreColor(score.value),
                )
                Spacer(Modifier.height(10.dp))
            }
            Text(score.detail, style = MaterialTheme.typography.bodyMedium)
            if (!score.applicable) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Критерий исключён из расчёта Q из-за недостатка данных.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
