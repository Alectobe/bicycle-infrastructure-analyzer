package com.example.courseproject.presentation.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.courseproject.di.AppContainer
import com.example.courseproject.domain.model.AreaEvaluation
import com.example.courseproject.domain.model.BoundingBox
import com.example.courseproject.presentation.components.EvaluationRow
import com.example.courseproject.presentation.components.OsmMapView
import com.example.courseproject.presentation.components.visibleBoundingBox
import org.osmdroid.views.MapView

/** Маршрут главного экрана: связывает [MapViewModel] с UI. */
@Composable
fun MapRoute(
    container: AppContainer,
    onOpenResult: (String) -> Unit,
) {
    val viewModel: MapViewModel = viewModel {
        MapViewModel(container.analyzeAreaUseCase, container.historyRepository)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.analysisReady.collect { id -> onOpenResult(id) }
    }

    MapScreen(
        uiState = uiState,
        history = history,
        onAnalyze = viewModel::analyze,
        onOpenEvaluation = onOpenResult,
        onDismissError = viewModel::dismissError,
    )
}

/** Главный экран: карта OpenStreetMap, выбор области и история расчётов. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    uiState: MapUiState,
    history: List<AreaEvaluation>,
    onAnalyze: (BoundingBox) -> Unit,
    onOpenEvaluation: (String) -> Unit,
    onDismissError: () -> Unit,
) {
    var mapView by remember { mutableStateOf<MapView?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Оценка велоинфраструктуры") }) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    OsmMapView(
                        modifier = Modifier.fillMaxSize(),
                        onMapInitialized = { mapView = it },
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(8.dp),
                            ),
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(12.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        tonalElevation = 3.dp,
                    ) {
                        Text(
                            text = "Наведите карту на район — анализируется видимая область внутри рамки",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                MapBottomPanel(
                    history = history,
                    analyzeEnabled = !uiState.isLoading && mapView != null,
                    onAnalyze = { mapView?.let { onAnalyze(it.visibleBoundingBox()) } },
                    onOpenEvaluation = onOpenEvaluation,
                )
            }

            if (uiState.isLoading) {
                LoadingOverlay()
            }
        }
    }

    uiState.error?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissError,
            confirmButton = {
                TextButton(onClick = onDismissError) { Text("Понятно") }
            },
            title = { Text("Не удалось выполнить анализ") },
            text = { Text(message) },
        )
    }
}

@Composable
private fun MapBottomPanel(
    history: List<AreaEvaluation>,
    analyzeEnabled: Boolean,
    onAnalyze: () -> Unit,
    onOpenEvaluation: (String) -> Unit,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Button(
                onClick = onAnalyze,
                enabled = analyzeEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Анализировать видимую область")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "История расчётов",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(8.dp))
            if (history.isEmpty()) {
                Text(
                    text = "Пока нет сохранённых оценок. Выберите район и запустите анализ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(history, key = { it.id }) { evaluation ->
                        EvaluationRow(
                            evaluation = evaluation,
                            onClick = { onOpenEvaluation(evaluation.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 6.dp) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Загрузка данных OpenStreetMap…")
            }
        }
    }
}
