package io.stridelab.privatealpha

import android.view.Gravity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mapbox.bindgen.Value
import com.mapbox.common.MapboxOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.generated.circleLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.style
import com.mapbox.maps.plugin.attribution.attribution
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.logo.logo
import com.mapbox.maps.plugin.scalebar.scalebar
import kotlin.math.cos

/**
 * Mapbox Standard is the StrideLab Activity basemap. Recorded coordinates and
 * every route overlay remain source-owned; Mapbox only renders the basemap.
 */
internal object TracksideMapTiles {
    val STYLE_URL: String = Style.STANDARD
    const val ATTRIBUTION = "© Mapbox · © OpenStreetMap"
    val configured: Boolean get() = BuildConfig.MAPBOX_PUBLIC_TOKEN.isNotBlank()
}

private const val ROUTE_SOURCE = "stride-actual-gps"
private const val ROUTE_LAYER = "stride-gps-line"
private const val UNKNOWN_GAP_SOURCE = "stride-gps-gaps"
private const val UNKNOWN_GAP_LAYER = "stride-gps-gap-indicator"
private const val PAUSE_GAP_SOURCE = "stride-pause-gaps"
private const val PAUSE_GAP_LAYER = "stride-pause-gap-indicator"
private const val ISOLATED_SOURCE = "stride-gps-isolated"
private const val ISOLATED_LAYER = "stride-gps-isolated-fixes"
private const val SELECTED_SOURCE = "stride-selected-point"
private const val SELECTED_LAYER = "stride-selected-point-layer"

/**
 * Online: Mapbox Standard with real FIT-derived GPS overlays and gestures.
 * Offline, missing token, or initial loading: the exact coordinate trace on a
 * neutral surface. The fallback never fabricates road geometry or locations.
 */
@Composable
internal fun TracksideGeographicMap(
    route: RecordedGpsRoute,
    online: Boolean,
    modifier: Modifier = Modifier,
    selectedSeconds: Double? = null
) {
    var styleReady by remember(route, online) { mutableStateOf(false) }
    val useMapbox = online && route.pointCount > 0 && TracksideMapTiles.configured
    Box(modifier.background(Color(0xFFE9ECEA)).testTag("activity-geographic-map")) {
        if (useMapbox) {
            MapboxRouteView(
                route = route,
                modifier = Modifier.fillMaxSize(),
                selectedSeconds = selectedSeconds
            ) { styleReady = true }
        }
        if (!useMapbox || !styleReady) {
            RecordedGpsFallback(
                route = route,
                modifier = Modifier.fillMaxSize(),
                selectedSeconds = selectedSeconds
            )
        }
    }
}

@Composable
private fun MapboxRouteView(
    route: RecordedGpsRoute,
    modifier: Modifier = Modifier,
    selectedSeconds: Double? = null,
    onStyleReady: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember(context) {
        MapboxOptions.accessToken = BuildConfig.MAPBOX_PUBLIC_TOKEN
        MapView(
            context,
            MapInitOptions(
                context = context,
                textureView = true
            )
        ).apply {
            gestures.rotateEnabled = false
            gestures.pitchEnabled = false
            scalebar.enabled = false
            compass.enabled = false

            // Keep required Mapbox ornaments visible on the actual map while
            // avoiding the draggable Activity sheet at the bottom.
            logo.position = Gravity.START or Gravity.TOP
            logo.marginLeft = 8f
            logo.marginTop = 72f
            attribution.position = Gravity.END or Gravity.TOP
            attribution.marginRight = 8f
            attribution.marginTop = 72f
        }
    }

    val routeJson = remember(route) { route.toGeoJson() }
    val isolatedJson = remember(route) { route.isolatedGeoJson() }
    val gapJson = remember(route) { route.gapsGeoJson() }
    val pauseGapJson = remember(route) { route.pauseGapsGeoJson() }
    val selectionJson = remember(route, selectedSeconds) {
        route.selectedPointGeoJson(selectedSeconds)
    }

    DisposableEffect(mapView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> Unit
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { mapView.onStop() }
            runCatching { mapView.onDestroy() }
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.testTag("activity-mapbox-view")
    )

    LaunchedEffect(mapView, routeJson, isolatedJson, gapJson, pauseGapJson) {
        val mapboxMap = mapView.mapboxMap
        mapboxMap.loadStyle(
            style(style = TracksideMapTiles.STYLE_URL) {
                if (route.segments.any { it.size >= 2 }) {
                    +geoJsonSource(ROUTE_SOURCE) { data(routeJson) }
                    +lineLayer(ROUTE_LAYER, ROUTE_SOURCE) {
                        lineColor("#D8FB70")
                        lineWidth(4.0)
                        lineOpacity(0.96)
                        lineCap(LineCap.ROUND)
                        lineJoin(LineJoin.ROUND)
                        slot("top")
                    }
                }
                if (route.unknownGaps.isNotEmpty()) {
                    +geoJsonSource(UNKNOWN_GAP_SOURCE) { data(gapJson) }
                    +lineLayer(UNKNOWN_GAP_LAYER, UNKNOWN_GAP_SOURCE) {
                        lineColor("#88928D")
                        lineWidth(1.6)
                        lineOpacity(0.72)
                        lineDasharray(listOf(2.0, 3.0))
                        lineCap(LineCap.ROUND)
                        slot("top")
                    }
                }
                if (route.pauseGaps.isNotEmpty()) {
                    +geoJsonSource(PAUSE_GAP_SOURCE) { data(pauseGapJson) }
                    +lineLayer(PAUSE_GAP_LAYER, PAUSE_GAP_SOURCE) {
                        lineColor("#FFBE7A")
                        lineWidth(2.2)
                        lineOpacity(0.94)
                        lineDasharray(listOf(2.0, 2.0))
                        lineCap(LineCap.ROUND)
                        slot("top")
                    }
                }
                if (route.isolatedPoints.isNotEmpty()) {
                    +geoJsonSource(ISOLATED_SOURCE) { data(isolatedJson) }
                    +circleLayer(ISOLATED_LAYER, ISOLATED_SOURCE) {
                        circleColor("#D8FB70")
                        circleRadius(4.0)
                        circleStrokeColor("#193027")
                        circleStrokeWidth(1.5)
                        slot("top")
                    }
                }
                +geoJsonSource(SELECTED_SOURCE) { data(selectionJson) }
                +circleLayer(SELECTED_LAYER, SELECTED_SOURCE) {
                    circleColor("#080D0C")
                    circleRadius(7.0)
                    circleStrokeColor("#D8FB70")
                    circleStrokeWidth(3.0)
                    slot("top")
                }
            }
        ) { loadedStyle ->
            // A light, low-clutter Standard treatment is closer to Strava's
            // readable activity maps than the old near-black provider style.
            loadedStyle.setStyleImportConfigProperty(
                "basemap", "lightPreset", Value.valueOf("day")
            )
            loadedStyle.setStyleImportConfigProperty(
                "basemap", "theme", Value.valueOf("faded")
            )
            loadedStyle.setStyleImportConfigProperty(
                "basemap", "show3dObjects", Value.valueOf(false)
            )
            loadedStyle.setStyleImportConfigProperty(
                "basemap", "showPointOfInterestLabels", Value.valueOf(false)
            )
            loadedStyle.setStyleImportConfigProperty(
                "basemap", "showTransitLabels", Value.valueOf(false)
            )

            val cameraPoints = route.cameraPoints().map {
                Point.fromLngLat(it.longitude, it.latitude)
            }
            mapView.post {
                when (cameraPoints.size) {
                    0 -> Unit
                    1 -> mapboxMap.setCamera(
                        CameraOptions.Builder()
                            .center(cameraPoints.first())
                            .zoom(15.0)
                            .pitch(0.0)
                            .bearing(0.0)
                            .build()
                    )
                    else -> mapboxMap.cameraForCoordinates(
                        coordinates = cameraPoints,
                        camera = CameraOptions.Builder().pitch(0.0).bearing(0.0).build(),
                        coordinatesPadding = EdgeInsets(72.0, 64.0, 72.0, 64.0),
                        maxZoom = 17.0,
                        offset = null
                    ) { camera ->
                        mapboxMap.setCamera(camera)
                    }
                }
            }
            onStyleReady()
        }
    }

    LaunchedEffect(mapView, selectionJson) {
        mapView.mapboxMap.getStyle { loadedStyle ->
            loadedStyle.setStyleSourceProperty(
                SELECTED_SOURCE,
                "data",
                Value.valueOf(selectionJson)
            )
        }
    }
}

/** Accurate relative shape/position only: no tiles, labels or invented road geometry. */
@Composable
private fun RecordedGpsFallback(
    route: RecordedGpsRoute,
    modifier: Modifier = Modifier,
    selectedSeconds: Double? = null
) {
    val points = route.allPoints
    if (points.isEmpty()) return
    val projection = cos(Math.toRadians(points.map { it.latitude }.average()))
        .coerceAtLeast(0.01)
    val minX = points.minOf { it.longitude * projection }
    val maxX = points.maxOf { it.longitude * projection }
    val minY = points.minOf { it.latitude }
    val maxY = points.maxOf { it.latitude }
    Canvas(modifier.testTag("activity-offline-route")) {
        val spanX = (maxX - minX).coerceAtLeast(0.00001)
        val spanY = (maxY - minY).coerceAtLeast(0.00001)
        val scale = minOf((size.width - 64f) / spanX, (size.height - 64f) / spanY)
        if (!scale.isFinite() || scale <= 0) return@Canvas
        fun position(p: RecordedGpsPoint) = Offset(
            ((size.width - spanX * scale) / 2 +
                (p.longitude * projection - minX) * scale).toFloat(),
            ((size.height - spanY * scale) / 2 +
                (maxY - p.latitude) * scale).toFloat()
        )
        route.unknownGaps.forEach { (a, b) ->
            drawLine(
                Color(0xFF88928D).copy(alpha = .75f), position(a), position(b),
                1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 7f))
            )
        }
        route.pauseGaps.forEach { (a, b) ->
            drawLine(
                Color(0xFFFFBE7A).copy(alpha = .92f), position(a), position(b),
                2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f))
            )
        }
        route.segments.forEach { segment ->
            segment.zipWithNext().forEach { (a, b) ->
                drawLine(Color(0xFFD8FB70), position(a), position(b), 3.dp.toPx())
            }
        }
        route.isolatedPoints.forEach { point ->
            drawCircle(Color(0xFFD8FB70), radius = 4.dp.toPx(), center = position(point))
        }
        route.selectedPoint(selectedSeconds)?.let { point ->
            val center = position(point)
            drawCircle(Color(0xFF080D0C), radius = 7.dp.toPx(), center = center)
            drawCircle(
                Color(0xFFD8FB70),
                radius = 7.dp.toPx(),
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx())
            )
        }
    }
}
