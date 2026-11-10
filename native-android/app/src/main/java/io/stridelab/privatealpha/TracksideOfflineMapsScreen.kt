package io.stridelab.privatealpha

import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.util.Locale
import kotlin.math.*

private val offlineBlack = Color(0xFF080B0A)
private val offlineCard = Color(0xFF151A18)
private val offlineBorder = Color(0xFF364039)
private val offlineGreen = Color(0xFFD8FB70)
private val offlineSecondary = Color(0xFFAAB4AD)

/**
 * The green rectangle stays fixed over the map. Camera gestures move the
 * map underneath it. Its exact pixel corners are projected to geographic
 * bounds and passed unchanged to the offline-region downloader.
 */
@Composable
private fun OfflineBoxMap(onSelection: (LatLngBounds?) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val preferences = remember(context) {
        context.getSharedPreferences("stridelab-offline-picker", android.content.Context.MODE_PRIVATE)
    }
    val mapView = remember(context) {
        MapLibre.getInstance(context.applicationContext)
        MapView(context, MapLibreMapOptions().textureMode(true)).apply { onCreate(null) }
    }
    var map by remember(mapView) { mutableStateOf<MapLibreMap?>(null) }
    var measured by remember { mutableStateOf(IntSize.Zero) }

    fun projectedBox(m: MapLibreMap): LatLngBounds? {
        val width = mapView.width.toFloat()
        val height = mapView.height.toFloat()
        if (width <= 0f || height <= 0f || measured.width <= 0) return null
        // Must exactly match the Canvas rectangle fractions below.
        val nw = m.projection.fromScreenLocation(PointF(width * .11f, height * .20f))
        val se = m.projection.fromScreenLocation(PointF(width * .89f, height * .80f))
        if (!nw.latitude.isFinite() || !se.latitude.isFinite() ||
            !nw.longitude.isFinite() || !se.longitude.isFinite() ||
            se.longitude <= nw.longitude || nw.latitude <= se.latitude) return null
        return LatLngBounds.from(nw.latitude, se.longitude, se.latitude, nw.longitude)
    }

    DisposableEffect(mapView, owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                mapView.onPause()
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.setRotateGesturesEnabled(false)
            m.uiSettings.setTiltGesturesEnabled(false)
            m.setStyle(Style.Builder().fromUri(TracksideMapTiles.STYLE_URL))
            val latitude = preferences.getFloat("lat", 0f).toDouble()
            val longitude = preferences.getFloat("lon", 0f).toDouble()
            val zoom = preferences.getFloat("zoom", 2.5f).toDouble()
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), zoom))
        }
    }

    DisposableEffect(map, measured) {
        val current = map
        if (current == null || measured.width <= 0) {
            onDispose { }
        } else {
            val update = MapLibreMap.OnCameraIdleListener {
                val bounds = projectedBox(current)
                onSelection(bounds)
                val camera = current.cameraPosition
                // MapLibre can return no camera target while the map initializes.
                // Keep the last saved position rather than dereferencing null.
                val target = camera.target
                if (target != null) {
                    preferences.edit()
                        .putFloat("lat", target.latitude.toFloat())
                        .putFloat("lon", target.longitude.toFloat())
                        .putFloat("zoom", camera.zoom.toFloat())
                        .apply()
                }
            }
            current.addOnCameraIdleListener(update)
            mapView.post { update.onCameraIdle() }
            onDispose { current.removeOnCameraIdleListener(update) }
        }
    }

    Box(
        Modifier.fillMaxWidth().height(310.dp)
            .onSizeChanged { measured = it }
            .background(offlineCard, RoundedCornerShape(18.dp))
            .testTag("offline-selection-map")
    ) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        // Decorative drawing only; does not intercept map gestures.
        Canvas(Modifier.fillMaxSize().testTag("offline-selection-box")) {
            val w = size.width
            val h = size.height
            val left = w * .11f
            val right = w * .89f
            val top = h * .20f
            val bottom = h * .80f
            val tint = Color(0x91000000)
            drawRect(tint, Offset.Zero, Size(w, top))
            drawRect(tint, Offset(0f, bottom), Size(w, h - bottom))
            drawRect(tint, Offset(0f, top), Size(left, bottom - top))
            drawRect(tint, Offset(right, top), Size(w - right, bottom - top))
            drawRect(
                color = offlineGreen,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(width = 2.5.dp.toPx())
            )
        }
        Text(
            "MOVE MAP TO SELECT",
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                .background(Color(0xDF080B0A), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            color = Color.White,
            fontSize = 10.sp
        )
    }
}

@Composable
internal fun TracksideOfflineMapsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { StrideOfflineMapStore.get(context).also { it.initialize() } }
    var selected by remember { mutableStateOf<LatLngBounds?>(null) }
    var name by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<StrideSavedMap?>(null) }
    val tileEstimate = selected?.let(StrideOfflineMapStore::estimatedTiles)
    val canDownload = selected?.let(StrideOfflineMapStore::isSelectionAllowed) == true

    LaunchedEffect(store) { store.refresh() }
    LaunchedEffect(store.activeId) {
        while (store.activeId != null) {
            delay(1500L)
            store.refresh()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(offlineBlack)
            .testTag("offline-maps-screen"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹", color = offlineGreen, fontSize = 26.sp) }
                Text("Offline maps", style = MaterialTheme.typography.headlineMedium,
                    color = Color.White)
            }
        }
        item {
            Text(
                "Move and zoom the map beneath the green box. Only the area inside the box will be downloaded. Save it once and reuse it for any run nearby.",
                color = offlineSecondary, style = MaterialTheme.typography.bodyMedium
            )
        }
        item {
            OfflineBoxMap(onSelection = { selected = it })
        }
        item {
            val bounds = selected
            val size = if (bounds == null) "Move the map to choose an area." else {
                val height = (bounds.latitudeNorth - bounds.latitudeSouth) * 111.0
                val middle = (bounds.latitudeNorth + bounds.latitudeSouth) / 2.0
                val width = (bounds.longitudeEast - bounds.longitudeWest) *
                    111.0 * cos(Math.toRadians(middle))
                String.format(Locale.US, "Selected box: %.1f × %.1f km", width, height)
            }
            Text(size, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (!canDownload) "Zoom in to fit the small-area download limit."
                else "About " + tileEstimate + " map tiles at zoom levels 11–14.",
                color = if (canDownload) offlineSecondary else Color(0xFFFFAA88),
                style = MaterialTheme.typography.bodySmall
            )
        }
        item {
            Surface(color = offlineCard, shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, offlineBorder)) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(60) },
                        label = { Text("Name this map") },
                        placeholder = { Text("My running area") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("offline-map-name")
                    )
                    Button(
                        onClick = { store.save(name.trim(), selected) },
                        enabled = name.isNotBlank() && canDownload && store.activeId == null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = offlineGreen, contentColor = offlineBlack
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("offline-download-selected-box")
                    ) {
                        Text("Download selected area")
                    }
                    Text(
                        "Wi-Fi only · conservatively limited downloads · maps stay on this device",
                        color = offlineSecondary, style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        if (store.notice.isNotBlank()) {
            item {
                Text(store.notice, color = offlineGreen,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            val total = store.maps.sumOf { it.downloadedBytes }
            Text("SAVED MAPS", color = Color.White,
                style = MaterialTheme.typography.titleMedium)
            Text(
                store.maps.size.toString() + " areas · " +
                    (total / 1024 / 1024).toString() + " MB downloaded",
                color = offlineSecondary, style = MaterialTheme.typography.bodySmall
            )
        }
        if (store.maps.isEmpty()) {
            item {
                Text("No downloaded areas yet.", color = offlineSecondary,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        items(store.maps, key = { it.id }) { area ->
            Surface(color = offlineCard, shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, offlineBorder)) {
                Column(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(area.name, color = Color.White,
                        style = MaterialTheme.typography.titleMedium)
                    Text(
                        (area.downloadedBytes / 1024 / 1024).toString() + " MB · " +
                            if (area.completed) "Ready offline"
                            else area.progress.toString() + "% downloaded",
                        color = offlineSecondary, style = MaterialTheme.typography.bodySmall
                    )
                    if (!area.completed) {
                        LinearProgressIndicator(
                            progress = { area.progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = offlineGreen
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!area.completed) {
                            TextButton(onClick = {
                                if (store.activeId == area.id) store.pause()
                                else store.resume(area.id)
                            }) {
                                Text(
                                    if (store.activeId == area.id) "Pause" else "Resume",
                                    color = offlineGreen
                                )
                            }
                        }
                        TextButton(onClick = { deleting = area }) {
                            Text("Delete", color = Color(0xFFFFA09C))
                        }
                    }
                }
            }
        }
        item {
            Text(
                "Map data © OpenStreetMap contributors · OpenMapTiles · OpenFreeMap. " +
                    "Deleting a map never deletes saved running activities.",
                color = offlineSecondary, style = MaterialTheme.typography.labelSmall
            )
        }
    }
    deleting?.let { area ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = offlineCard,
            titleContentColor = Color.White,
            textContentColor = offlineSecondary,
            title = { Text("Delete " + area.name + "?") },
            text = { Text("The downloaded map is removed, but your activities remain saved.") },
            confirmButton = {
                TextButton(onClick = {
                    store.delete(area.id)
                    deleting = null
                }) { Text("Delete map", color = Color(0xFFFFA09C)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text("Cancel", color = offlineGreen)
                }
            }
        )
    }
}
