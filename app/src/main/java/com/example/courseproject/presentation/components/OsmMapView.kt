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

/** Текущая видимая область карты в виде доменного [BoundingBox]. */
fun MapView.visibleBoundingBox(): BoundingBox {
    val box = boundingBox
    return BoundingBox(
        south = box.latSouth,
        west = box.lonWest,
        north = box.latNorth,
        east = box.lonEast,
    )
}
