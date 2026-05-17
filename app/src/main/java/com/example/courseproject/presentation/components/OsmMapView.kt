package com.example.courseproject.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.courseproject.domain.model.BoundingBox
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import kotlin.math.max
import kotlin.math.min

/** Стартовое положение карты — центр Москвы. */
private val DEFAULT_CENTER = GeoPoint(55.751244, 37.618423)
private const val DEFAULT_ZOOM = 14.0

/**
 * Компонент карты OpenStreetMap на основе библиотеки osmdroid, встроенный
 * в Compose через [AndroidView]. Управляет жизненным циклом MapView.
 */
@Composable
fun OsmMapView(
    modifier: Modifier = Modifier,
    onMapInitialized: (MapView) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setUseDataConnection(true)
            isTilesScaledToDpi = true
            controller.setZoom(DEFAULT_ZOOM)
            controller.setCenter(DEFAULT_CENTER)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    LaunchedEffect(mapView) { onMapInitialized(mapView) }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/**
 * Географические границы прямоугольной рамки выбора — области карты,
 * отстоящей от каждого края на [insetPx] пикселей.
 */
fun MapView.selectedBoundingBox(insetPx: Int): BoundingBox {
    val mapProjection = projection
    val topLeft = mapProjection.fromPixels(insetPx, insetPx)
    val bottomRight = mapProjection.fromPixels(width - insetPx, height - insetPx)
    return BoundingBox(
        south = min(topLeft.latitude, bottomRight.latitude),
        west = min(topLeft.longitude, bottomRight.longitude),
        north = max(topLeft.latitude, bottomRight.latitude),
        east = max(topLeft.longitude, bottomRight.longitude),
    )
}
