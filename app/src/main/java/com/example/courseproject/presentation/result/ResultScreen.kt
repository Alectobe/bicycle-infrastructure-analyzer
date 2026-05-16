package com.example.courseproject.presentation.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.courseproject.di.AppContainer
import com.example.courseproject.domain.model.AreaEvaluation
import com.example.courseproject.domain.model.QualityScore
import com.example.courseproject.presentation.components.EvaluationRow
import com.example.courseproject.presentation.components.formatScore
import com.example.courseproject.presentation.components.scoreColor
import com.example.courseproject.presentation.evaluation.EvaluationUiState
import com.example.courseproject.presentation.evaluation.EvaluationViewModel

/** Маршрут экрана результата: связывает [EvaluationViewModel] с UI. */
@Composable
fun ResultRoute(
    container: AppContainer,
    evaluationId: String,
    onBack: () -> Unit,
    onOpenDetail: () -> Unit,
    onOpenResult: (String) -> Unit,
) {
    val viewModel: EvaluationViewModel = viewModel(key = evaluationId) {
        EvaluationViewModel(container.historyRepository, evaluationId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ResultScreen(
        state = state,
        onBack = onBack,
        onOpenDetail = onOpenDetail,
        onOpenResult = onOpenResult,
    )
}

/** Экран итогового результата анализа территории. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    state: EvaluationUiState,
    onBack: () -> Unit,
    onOpenDetail: () -> Unit,
    onOpenResult: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Результат анализа") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        val evaluation = state.evaluation
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScoreHeaderCard(evaluation)
            evaluation.score.dataWarning?.let { WarningCard(it) }
            SummaryCard(evaluation.score.summary)
            Button(onClick = onOpenDetail, modifier = Modifier.fillMaxWidth()) {
                Text("Подробнее по критериям")
            }
            SimilarAreasSection(similar = state.similar, onOpenResult = onOpenResult)
        }
    }
}

@Composable
private fun ScoreHeaderCard(evaluation: AreaEvaluation) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(evaluation.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatScore(evaluation.score.total),
                style = MaterialTheme.typography.displayMedium,
                color = scoreColor(evaluation.score.total),
            )
            Text(
                text = "Качество велоинфраструктуры — ${QualityScore.band(evaluation.score.total)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { evaluation.score.total.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = scoreColor(evaluation.score.total),
            )
        }
    }
}

@Composable
private fun WarningCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun SummaryCard(summary: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Пояснение к оценке", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(summary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SimilarAreasSection(
    similar: List<AreaEvaluation>,
    onOpenResult: (String) -> Unit,
) {
    Column {
        Text("Похожие районы", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        if (similar.isEmpty()) {
            Text(
                text = "Оцените другие районы, чтобы сравнить их между собой.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                similar.forEach { item ->
                    EvaluationRow(evaluation = item, onClick = { onOpenResult(item.id) })
                }
            }
        }
    }
}
