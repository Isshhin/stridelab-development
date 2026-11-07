package io.stridelab.privatealpha

import android.graphics.Color as AndroidColor
import android.view.View
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.maps.Style
import kotlin.math.cos

/**
 * Real map coordinates supplied exclusively by recorded ActivityDetail samples.
 *
 * OpenFreeMap hosts OSM-derived vector tiles, including mandatory attribution.
 * Unlike the OSM Foundation public raster service this endpoint is intended
 * for interactive app maps, with no API key and no bulk/offline prefetch.
 * https://openfreemap.org/
 */
internal object TracksideMapTiles {
    // OpenFreeMap's real vector tile style supplies geographic geometry and
    // road widths/zoom behavior. We recolor its existing layers locally for
    // a readable, slate-blue twilight map instead of masking it with a tint.
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/dark"
    const val ATTRIBUTION = "© OpenStreetMap contributors · © OpenMapTiles · OpenFreeMap"
}

/**
 * StrideLab twilight map. Inspired by readable athletic navigation maps and
 * Google Maps' differentiated land/water/road contrast, without copying either.
 *
 * Colors are applied ONCE when the hosted style loads. MapLibre retains the
 * original source filters, zoom-based road widths, text placement and sprites.
 * Missing or renamed provider layers are ignored safely rather than causing
 * Activity Detail to fail.
 */
internal object TracksideReadableMapPalette {
    val canvas = AndroidColor.rgb(44, 58, 73)           // slate-blue ground
    val residential = AndroidColor.rgb(50, 67, 81)
    val buildings = AndroidColor.rgb(55, 72, 85)
    val sand = AndroidColor.rgb(76, 81, 78)
    val grass = AndroidColor.rgb(55, 89, 72)            // visible, muted parks
    val wood = AndroidColor.rgb(47, 77, 64)
    val water = AndroidColor.rgb(38, 76, 101)
    val waterways = AndroidColor.rgb(62, 105, 130)
    val roads = AndroidColor.rgb(119, 140, 157)         // brighter, legible roads
    val paths = AndroidColor.rgb(100, 120, 135)
    val rail = AndroidColor.rgb(86, 110, 127)
    val roadLabels = AndroidColor.rgb(221, 231, 239)
    val placeLabels = AndroidColor.rgb(242, 245, 248)
    val labelHalo = AndroidColor.rgb(43, 57, 70)

    fun apply(style: Style) {
        fun background(id: String, color: Int) {
            (style.getLayer(id) as? BackgroundLayer)?.setProperties(
                PropertyFactory.backgroundColor(color))
        }
        fun fill(id: String, color: Int) {
            (style.getLayer(id) as? FillLayer)?.setProperties(
                PropertyFactory.fillColor(color))
        }
        fun line(id: String, color: Int) {
            (style.getLayer(id) as? LineLayer)?.setProperties(
                PropertyFactory.lineColor(color))
        }
        fun text(id: String, color: Int) {
            (style.getLayer(id) as? SymbolLayer)?.setProperties(
                PropertyFactory.textColor(color),
                PropertyFactory.textHaloColor(labelHalo))
        }

        background("Background", canvas)
        fill("Residential", residential)
        fill("Building", buildings)
        fill("Sand", sand)
        fill("Grass", grass)
        fill("Wood", wood)
        fill("Water", water)
        fill("Bridge", residential)
        fill("Pier", residential)
        line("River", waterways)
        line("River intermittent", waterways)
        line("Road network", roads)
        line("Path", paths)
        line("Railway", rail)
        line("Transit", rail)
        line("Transit tunnel", rail)

        text("Road labels", roadLabels)
        text("Place labels", placeLabels)
        text("City labels", placeLabels)
        text("Country labels", placeLabels)
        text("Continent labels", placeLabels)
        text("Station labels", roadLabels)
        text("Airport labels", roadLabels)
    }
}

/**
 * Online: native MapLibre map with real GPS GeoJSON overlay and gestures.
 * Offline/initial loading: true coordinate trace on a neutral background.
 * The fallback is NOT represented as a street map and never fabricates GPS.
 */
@Composable
internal fun TracksideGeographicMap(
    route:RecordedGpsRoute,
    online:Boolean,
    modifier:Modifier=Modifier
) {
    var styleReady by remember(route,online) { mutableStateOf(false) }
    Box(modifier.background(Color(0xFF2C3A49)).testTag("activity-geographic-map")) {
        if(online && route.pointCount>=2) {
            MapLibreRouteView(route,Modifier.fillMaxSize()) { styleReady=true }
        }
        if(!online || !styleReady) {
            RecordedGpsFallback(route,Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun MapLibreRouteView(
    route:RecordedGpsRoute,
    modifier:Modifier=Modifier,
    onStyleReady:()->Unit
) {
    val context=LocalContext.current
    val lifecycleOwner=LocalLifecycleOwner.current
    val mapView=remember(context) {
        // MapLibre must be initialized before MapView is constructed.
        MapLibre.getInstance(context.applicationContext)
        // TextureView is required for correct Compose bottom-sheet overlap.
        MapView(
            context,
            MapLibreMapOptions().textureMode(true)
                // One custom, accessible source credit is attached to the
                // moving sheet edge. Avoid a second MapLibre logo/credit
                // covering the full-map grip at the bottom of the screen.
                .attributionEnabled(false).logoEnabled(false)
        )
            .apply { onCreate(null) }
    }
    val geoJson=remember(route) { route.toGeoJson() }

    DisposableEffect(mapView,lifecycleOwner) {
        val observer=LifecycleEventObserver { _,event ->
            when(event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        // Lifecycle catch-up will dispatch ON_START/ON_RESUME if already active.
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                mapView.onPause()
            if(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory={mapView},
        modifier=modifier.testTag("activity-maplibre-view")
    )
    LaunchedEffect(mapView,geoJson) {
        mapView.getMapAsync { map ->
            map.uiSettings.setTiltGesturesEnabled(false)
            map.uiSettings.setRotateGesturesEnabled(false)
            map.uiSettings.setAttributionEnabled(false)
            map.uiSettings.setLogoEnabled(false)
            map.setStyle(TracksideMapTiles.STYLE_URL) { style ->
                // Provider style layers exist at this point. Recolor the map
                // before presenting the real GPS line and fitting the camera.
                TracksideReadableMapPalette.apply(style)
                style.addSource(GeoJsonSource("stride-actual-gps",geoJson))
                style.addLayer(
                    LineLayer("stride-gps-line","stride-actual-gps").withProperties(
                        PropertyFactory.lineColor(AndroidColor.rgb(216,251,112)),
                        PropertyFactory.lineWidth(4f),
                        PropertyFactory.lineOpacity(0.95f)
                    )
                )
                val gps=route.cameraPoints()
                if(gps.isNotEmpty()) {
                    fun fitCamera() {
                        if(mapView.width<=0 || mapView.height<=0)return
                        val latRange=gps.maxOf{it.latitude}-gps.minOf{it.latitude}
                        val lonRange=gps.maxOf{it.longitude}-gps.minOf{it.longitude}
                        if(latRange<0.00001 && lonRange<0.00001) {
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                LatLng(gps.first().latitude,gps.first().longitude),15.0))
                        } else {
                            val bounds=LatLngBounds.Builder()
                            gps.forEach { bounds.include(LatLng(it.latitude,it.longitude)) }
                            map.moveCamera(CameraUpdateFactory.newLatLngBounds(
                                bounds.build(),64))
                        }
                    }
                    // Style loading can finish before Compose measures AndroidView.
                    // Fit only after layout, not to a zero-sized map at (0,0).
                    if(mapView.width>0 && mapView.height>0)mapView.post { fitCamera() }
                    else mapView.addOnLayoutChangeListener(object:View.OnLayoutChangeListener {
                        override fun onLayoutChange(
                            view:View,left:Int,top:Int,right:Int,bottom:Int,
                            oldLeft:Int,oldTop:Int,oldRight:Int,oldBottom:Int
                        ) {
                            if(right>left && bottom>top) {
                                mapView.removeOnLayoutChangeListener(this)
                                fitCamera()
                            }
                        }
                    })
                }
                onStyleReady()
            }
        }
    }
}

/** Accurate relative shape/position only: no tiles, labels or invented road geometry. */
@Composable
private fun RecordedGpsFallback(route:RecordedGpsRoute,modifier:Modifier=Modifier) {
    val points=route.allPoints
    if(points.size<2)return
    val projection=cos(Math.toRadians(points.map{it.latitude}.average()))
        .coerceAtLeast(0.01)
    val minX=points.minOf { it.longitude*projection }
    val maxX=points.maxOf { it.longitude*projection }
    val minY=points.minOf { it.latitude }
    val maxY=points.maxOf { it.latitude }
    Canvas(modifier.testTag("activity-offline-route")) {
        val spanX=(maxX-minX).coerceAtLeast(0.00001)
        val spanY=(maxY-minY).coerceAtLeast(0.00001)
        val scale=minOf((size.width-64f)/spanX,(size.height-64f)/spanY)
        if(!scale.isFinite() || scale<=0)return@Canvas
        fun position(p:RecordedGpsPoint)=Offset(
            ((size.width-spanX*scale)/2+(p.longitude*projection-minX)*scale).toFloat(),
            ((size.height-spanY*scale)/2+(maxY-p.latitude)*scale).toFloat()
        )
        route.segments.forEach { segment ->
            segment.zipWithNext().forEach { (a,b) ->
                drawLine(Color(0xFFD8FB70),position(a),position(b),3.dp.toPx())
            }
        }
    }
}
