package io.stridelab.privatealpha

import android.graphics.Color as AndroidColor
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory
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
internal enum class TracksideActivityMapMode(val label:String) {
    ROUTE("Route"),
    PACE("Pace"),
    ELEVATION("Elevation"),
    HEART_RATE("HR")
}

internal object TracksideMapTiles {
    // Dedicated StrideLab activity-map style. It uses OpenFreeMap/OpenMapTiles
    // sources but owns the presentation: dark slate land, differentiated green
    // spaces, blue water, runner-focused paths and zoom-aware labels.
    const val STYLE_URL = "asset://stridelab_activity_dark.json"
    const val ATTRIBUTION = "© OpenStreetMap contributors · © OpenMapTiles · OpenFreeMap"
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
    modifier:Modifier=Modifier,
    selectedSeconds:Double?=null,
    laps:List<ActivityLap> = emptyList(),
    samples:List<ActivitySample> = emptyList(),
    mapMode:TracksideActivityMapMode = TracksideActivityMapMode.ROUTE,
    selectedRange:Pair<Double,Double>? = null,
    onSelectRange:(Pair<Double,Double>?) -> Unit = {}
) {
    var styleReady by remember(route,online) { mutableStateOf(false) }
    var recenterRequest by remember(route) { mutableIntStateOf(0) }
    Box(modifier.background(Color(0xFF2D343A)).testTag("activity-geographic-map")) {
        if(online && route.pointCount > 0) {
            MapLibreRouteView(
                route = route,
                modifier = Modifier.fillMaxSize(),
                selectedSeconds = selectedSeconds,
                laps = laps,
                samples = samples,
                mapMode = mapMode,
                selectedRange = selectedRange,
                recenterRequest = recenterRequest,
                onSelectRange = onSelectRange
            ) { styleReady=true }
        }
        if(!online || !styleReady) {
            RecordedGpsFallback(
                route, Modifier.fillMaxSize(), selectedSeconds
            )
        }
        if (online && route.pointCount > 0 && styleReady) {
            Box(
                Modifier.align(Alignment.TopEnd)
                    .padding(top = 68.dp, end = 14.dp)
                    .size(42.dp)
                    .background(Color(0xDD080E0C), RoundedCornerShape(22.dp))
                    .clickable { recenterRequest++ }
                    .testTag("activity-map-recenter")
                    .semantics { contentDescription = "Recenter activity route" },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(19.dp)) {
                    val stroke=1.8.dp.toPx()
                    drawCircle(
                        Color(0xFFD8FB70),radius=size.minDimension*.31f,
                        center=Offset(size.width/2,size.height/2),
                        style=androidx.compose.ui.graphics.drawscope.Stroke(stroke)
                    )
                    drawLine(Color(0xFFD8FB70),
                        Offset(size.width*.5f,0f),Offset(size.width*.5f,size.height*.22f),stroke)
                    drawLine(Color(0xFFD8FB70),
                        Offset(size.width*.5f,size.height*.78f),Offset(size.width*.5f,size.height),stroke)
                    drawLine(Color(0xFFD8FB70),
                        Offset(0f,size.height*.5f),Offset(size.width*.22f,size.height*.5f),stroke)
                    drawLine(Color(0xFFD8FB70),
                        Offset(size.width*.78f,size.height*.5f),Offset(size.width,size.height*.5f),stroke)
                }
            }
        }
    }
}

@Composable
private fun MapLibreRouteView(
    route:RecordedGpsRoute,
    modifier:Modifier=Modifier,
    selectedSeconds:Double?=null,
    laps:List<ActivityLap> = emptyList(),
    samples:List<ActivitySample> = emptyList(),
    mapMode:TracksideActivityMapMode = TracksideActivityMapMode.ROUTE,
    selectedRange:Pair<Double,Double>? = null,
    recenterRequest:Int = 0,
    onSelectRange:(Pair<Double,Double>?) -> Unit = {},
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
    val isolatedJson=remember(route) { route.isolatedGeoJson() }
    val pausedPointsJson=remember(route) { route.pausedPointsGeoJson() }
    val startPointJson=remember(route) { route.startPointGeoJson() }
    val finishPointJson=remember(route) { route.finishPointGeoJson() }
    val directionJson=remember(route) { route.directionMarkersGeoJson() }
    val splitMarkersJson=remember(route,laps) { route.splitMarkersGeoJson(laps) }
    val analysisRoute=remember(samples,route.pauseIntervals,mapMode) {
        activityAnalysisRoute(samples,route.pauseIntervals,mapMode)
    }
    val selectedRangeJson=remember(route,selectedRange) { route.rangeGeoJson(selectedRange) }
    val gapJson=remember(route) { route.gapsGeoJson() }
    val pauseGapJson=remember(route) { route.pauseGapsGeoJson() }
    val selectionJson=remember(route,selectedSeconds) {
        route.selectedPointGeoJson(selectedSeconds)
    }
    var selectedPointSource by remember(mapView) {
        mutableStateOf<GeoJsonSource?>(null)
    }
    var selectedRangeSource by remember(mapView) {
        mutableStateOf<GeoJsonSource?>(null)
    }
    var activeRouteLayer by remember(mapView) {
        mutableStateOf<LineLayer?>(null)
    }
    var liveMap by remember(mapView) {
        mutableStateOf<MapLibreMap?>(null)
    }
    val currentOnSelectRange by rememberUpdatedState(onSelectRange)
    val currentSelectedRange by rememberUpdatedState(selectedRange)

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

    DisposableEffect(liveMap,laps) {
        val map=liveMap
        if(map == null) {
            onDispose {}
        } else {
            val listener=MapLibreMap.OnMapClickListener { point ->
                val screen=map.projection.toScreenLocation(point)
                val feature=map.queryRenderedFeatures(
                    screen,
                    "stride-split-markers-circle",
                    "stride-split-markers-label"
                ).firstOrNull() ?: return@OnMapClickListener false
                val boundary=feature.properties()?.get("label")?.asString?.toIntOrNull()
                    ?: return@OnMapClickListener false
                val splitIndex=boundary-1
                val start=if(splitIndex == 0) {
                    laps.firstOrNull()?.startSeconds ?: 0.0
                } else {
                    laps.getOrNull(splitIndex)?.startSeconds
                        ?: return@OnMapClickListener false
                }
                val end=laps.getOrNull(splitIndex+1)?.startSeconds
                    ?: return@OnMapClickListener false
                if(end <= start) return@OnMapClickListener false
                val range=start to end
                currentOnSelectRange(
                    if (currentSelectedRange == range) null else range
                )
                true
            }
            map.addOnMapClickListener(listener)
            onDispose { map.removeOnMapClickListener(listener) }
        }
    }

    AndroidView(
        factory={mapView},
        modifier=modifier.testTag("activity-maplibre-view")
    )
    LaunchedEffect(
        mapView, geoJson, isolatedJson, pausedPointsJson,
        startPointJson, finishPointJson, directionJson, splitMarkersJson,
        analysisRoute.geoJson, mapMode, gapJson, pauseGapJson
    ) {
        mapView.getMapAsync { map ->
            liveMap = map
            map.uiSettings.setTiltGesturesEnabled(false)
            map.uiSettings.setRotateGesturesEnabled(false)
            map.uiSettings.setAttributionEnabled(false)
            map.uiSettings.setLogoEnabled(false)
            map.setStyle(Style.Builder().fromUri(TracksideMapTiles.STYLE_URL)) { style ->
                // A lone FIT fix has no line geometry; it is still rendered
                // below as a recorded dot instead of an empty line source.
                if(route.segments.any { it.size >= 2 }) {
                    style.addSource(GeoJsonSource("stride-actual-gps",geoJson))
                }
                if(route.unknownGaps.isNotEmpty()) {
                    // Gray dashed = unknown tracking interval, not measured motion.
                    style.addSource(GeoJsonSource("stride-gps-gaps",gapJson))
                    style.addLayer(
                        LineLayer("stride-gps-gap-indicator","stride-gps-gaps").withProperties(
                            PropertyFactory.lineColor(AndroidColor.rgb(165,180,174)),
                            PropertyFactory.lineWidth(1.5f),
                            PropertyFactory.lineOpacity(0.65f),
                            PropertyFactory.lineDasharray(arrayOf(2f,3f))
                        )
                    )
                }
                if(route.pauseGaps.isNotEmpty()) {
                    // Amber dashed = FIT timer was explicitly stopped, then resumed.
                    style.addSource(GeoJsonSource("stride-pause-gaps",pauseGapJson))
                    style.addLayer(
                        LineLayer("stride-pause-gap-indicator","stride-pause-gaps").withProperties(
                            PropertyFactory.lineColor(AndroidColor.rgb(255,190,122)),
                            PropertyFactory.lineWidth(2.2f),
                            PropertyFactory.lineOpacity(0.92f),
                            PropertyFactory.lineDasharray(arrayOf(2f,2f))
                        )
                    )
                }
                if(route.segments.any { it.size >= 2 }) {
                    val routeWidth = Expression.interpolate(
                        Expression.linear(), Expression.zoom(),
                        Expression.stop(10f, 3.2f),
                        Expression.stop(14f, 4.0f),
                        Expression.stop(18f, 5.2f)
                    )
                    val routeOutlineWidth = Expression.interpolate(
                        Expression.linear(), Expression.zoom(),
                        Expression.stop(10f, 5.0f),
                        Expression.stop(14f, 6.2f),
                        Expression.stop(18f, 7.8f)
                    )
                    // Dark casing preserves route readability over green parks,
                    // blue water and brighter road geometry at every zoom.
                    style.addLayer(
                        LineLayer("stride-gps-line-outline","stride-actual-gps").withProperties(
                            PropertyFactory.lineColor(AndroidColor.rgb(24,31,27)),
                            PropertyFactory.lineWidth(routeOutlineWidth),
                            PropertyFactory.lineOpacity(0.82f)
                        )
                    )
                    val line=LineLayer("stride-gps-line","stride-actual-gps").withProperties(
                        PropertyFactory.lineColor(AndroidColor.rgb(216,251,112)),
                        PropertyFactory.lineWidth(routeWidth),
                        PropertyFactory.lineOpacity(
                            when {
                                selectedRange != null -> 0.36f
                                mapMode != TracksideActivityMapMode.ROUTE -> 0.18f
                                else -> 0.97f
                            }
                        )
                    )
                    style.addLayer(line)
                    activeRouteLayer = line

                    if (
                        mapMode != TracksideActivityMapMode.ROUTE &&
                        analysisRoute.minimum != null &&
                        analysisRoute.maximum != null
                    ) {
                        style.addSource(
                            GeoJsonSource("stride-analysis-route",analysisRoute.geoJson)
                        )
                        val low=analysisRoute.minimum
                        val high=analysisRoute.maximum.coerceAtLeast(low + 0.001)
                        val mid=(low+high)/2.0
                        val lineColors=when(mapMode) {
                            TracksideActivityMapMode.PACE -> arrayOf(
                                AndroidColor.rgb(216,251,112),
                                AndroidColor.rgb(255,190,122),
                                AndroidColor.rgb(255,140,135)
                            )
                            TracksideActivityMapMode.ELEVATION -> arrayOf(
                                AndroidColor.rgb(94,171,214),
                                AndroidColor.rgb(194,168,255),
                                AndroidColor.rgb(255,190,122)
                            )
                            TracksideActivityMapMode.HEART_RATE -> arrayOf(
                                AndroidColor.rgb(121,201,179),
                                AndroidColor.rgb(216,251,112),
                                AndroidColor.rgb(255,140,135)
                            )
                            TracksideActivityMapMode.ROUTE -> arrayOf(
                                AndroidColor.rgb(216,251,112),
                                AndroidColor.rgb(216,251,112),
                                AndroidColor.rgb(216,251,112)
                            )
                        }
                        // Pace intentionally maps lower sec/km to the first,
                        // faster color. Elevation and HR map low->high directly.
                        style.addLayer(
                            LineLayer("stride-analysis-route-line","stride-analysis-route")
                                .withProperties(
                                    PropertyFactory.lineColor(
                                        Expression.interpolate(
                                            Expression.linear(),
                                            Expression.get("value"),
                                            Expression.stop(low,Expression.color(lineColors[0])),
                                            Expression.stop(mid,Expression.color(lineColors[1])),
                                            Expression.stop(high,Expression.color(lineColors[2]))
                                        )
                                    ),
                                    PropertyFactory.lineWidth(routeWidth),
                                    PropertyFactory.lineOpacity(0.98f)
                                )
                        )
                    }

                    val rangeSource=GeoJsonSource("stride-selected-split",selectedRangeJson)
                    style.addSource(rangeSource)
                    selectedRangeSource=rangeSource
                    style.addLayer(
                        LineLayer("stride-selected-split-line","stride-selected-split").withProperties(
                            PropertyFactory.lineColor(AndroidColor.rgb(236,255,174)),
                            PropertyFactory.lineWidth(routeWidth),
                            PropertyFactory.lineOpacity(0.99f)
                        )
                    )
                }
                style.addSource(GeoJsonSource("stride-direction-markers",directionJson))
                val directionLayer=SymbolLayer("stride-direction-markers-layer","stride-direction-markers")
                    .withProperties(
                        PropertyFactory.textField("▲"),
                        PropertyFactory.textSize(10.5f),
                        PropertyFactory.textColor(AndroidColor.rgb(216,251,112)),
                        PropertyFactory.textOpacity(0.72f),
                        PropertyFactory.textRotate(Expression.get("bearing")),
                        PropertyFactory.textRotationAlignment("map"),
                        PropertyFactory.textAllowOverlap(true),
                        PropertyFactory.textIgnorePlacement(true),
                        PropertyFactory.textHaloColor(AndroidColor.rgb(45,52,58)),
                        PropertyFactory.textHaloWidth(1.2f)
                    )
                directionLayer.setMinZoom(15f)
                style.addLayer(directionLayer)

                style.addSource(GeoJsonSource("stride-split-markers",splitMarkersJson))
                val splitCircle=CircleLayer("stride-split-markers-circle","stride-split-markers")
                    .withProperties(
                        PropertyFactory.circleColor(AndroidColor.rgb(45,52,58)),
                        PropertyFactory.circleRadius(8f),
                        PropertyFactory.circleStrokeColor(AndroidColor.rgb(216,251,112)),
                        PropertyFactory.circleStrokeWidth(1.6f)
                    )
                splitCircle.setMinZoom(14f)
                style.addLayer(splitCircle)
                val splitLabels=SymbolLayer("stride-split-markers-label","stride-split-markers")
                    .withProperties(
                        PropertyFactory.textField(Expression.get("label")),
                        PropertyFactory.textSize(9f),
                        PropertyFactory.textColor(AndroidColor.rgb(216,251,112)),
                        PropertyFactory.textAllowOverlap(true),
                        PropertyFactory.textIgnorePlacement(true)
                    )
                splitLabels.setMinZoom(14f)
                style.addLayer(splitLabels)

                if(route.activeIsolatedPoints.isNotEmpty()) {
                    // Single active fixes remain visible without implying a line.
                    style.addSource(GeoJsonSource("stride-gps-isolated",isolatedJson))
                    style.addLayer(
                        CircleLayer("stride-gps-isolated-fixes","stride-gps-isolated").withProperties(
                            PropertyFactory.circleColor(AndroidColor.rgb(216,251,112)),
                            PropertyFactory.circleRadius(4f),
                            PropertyFactory.circleStrokeColor(AndroidColor.rgb(25,47,39)),
                            PropertyFactory.circleStrokeWidth(1.5f)
                        )
                    )
                }
                if(route.pausedPoints.isNotEmpty()) {
                    // These are real FIT GPS fixes recorded while TIMER was
                    // stopped. Amber distinguishes them from active-route GPS.
                    style.addSource(GeoJsonSource("stride-paused-gps-fixes",pausedPointsJson))
                    style.addLayer(
                        CircleLayer("stride-paused-gps-fixes-layer","stride-paused-gps-fixes")
                            .withProperties(
                                PropertyFactory.circleColor(AndroidColor.rgb(255,190,122)),
                                PropertyFactory.circleRadius(3.2f),
                                PropertyFactory.circleOpacity(0.92f),
                                PropertyFactory.circleStrokeColor(AndroidColor.rgb(45,52,58)),
                                PropertyFactory.circleStrokeWidth(1.2f)
                            )
                    )
                }

                // Minimal start/finish markers use only recorded endpoint fixes.
                style.addSource(GeoJsonSource("stride-start-point",startPointJson))
                style.addLayer(
                    CircleLayer("stride-start-point-layer","stride-start-point").withProperties(
                        PropertyFactory.circleColor(AndroidColor.rgb(45,52,58)),
                        PropertyFactory.circleRadius(5.2f),
                        PropertyFactory.circleStrokeColor(AndroidColor.rgb(216,251,112)),
                        PropertyFactory.circleStrokeWidth(2.2f)
                    )
                )
                style.addSource(GeoJsonSource("stride-finish-point",finishPointJson))
                style.addLayer(
                    CircleLayer("stride-finish-point-layer","stride-finish-point").withProperties(
                        PropertyFactory.circleColor(AndroidColor.rgb(216,251,112)),
                        PropertyFactory.circleRadius(5.2f),
                        PropertyFactory.circleStrokeColor(AndroidColor.rgb(45,52,58)),
                        PropertyFactory.circleStrokeWidth(2.2f)
                    )
                )
                style.addLayer(
                    CircleLayer("stride-finish-point-center","stride-finish-point").withProperties(
                        PropertyFactory.circleColor(AndroidColor.rgb(45,52,58)),
                        PropertyFactory.circleRadius(1.9f)
                    )
                )

                val selectedSource=GeoJsonSource("stride-selected-point",selectionJson)
                style.addSource(selectedSource)
                selectedPointSource=selectedSource
                style.addLayer(
                    CircleLayer("stride-selected-point-layer","stride-selected-point").withProperties(
                        PropertyFactory.circleColor(AndroidColor.rgb(8,13,12)),
                        PropertyFactory.circleRadius(7f),
                        PropertyFactory.circleStrokeColor(AndroidColor.rgb(216,251,112)),
                        PropertyFactory.circleStrokeWidth(3f)
                    )
                )
                val gps=route.cameraPoints()
                if(gps.isNotEmpty()) {
                    fun fitCamera()=fitRecordedRouteCamera(map,mapView,gps)
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
    LaunchedEffect(selectionJson, selectedPointSource) {
        selectedPointSource?.setGeoJson(selectionJson)
    }
    LaunchedEffect(selectedRangeJson, selectedRangeSource, activeRouteLayer) {
        selectedRangeSource?.setGeoJson(selectedRangeJson)
        activeRouteLayer?.setProperties(
            PropertyFactory.lineOpacity(
                when {
                    selectedRange != null -> 0.36f
                    mapMode != TracksideActivityMapMode.ROUTE -> 0.18f
                    else -> 0.97f
                }
            )
        )
    }
    LaunchedEffect(recenterRequest, liveMap) {
        if (recenterRequest > 0) {
            val map=liveMap ?: return@LaunchedEffect
            val gps=route.cameraPoints()
            if (gps.isNotEmpty()) mapView.post {
                fitRecordedRouteCamera(map,mapView,gps)
            }
        }
    }
}

private fun fitRecordedRouteCamera(
    map:MapLibreMap,
    mapView:MapView,
    gps:List<RecordedGpsPoint>
) {
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
            bounds.build(),72,72,72,156
        ))
    }
}

/** Accurate relative shape/position only: no tiles, labels or invented road geometry. */
@Composable
private fun RecordedGpsFallback(
    route:RecordedGpsRoute,
    modifier:Modifier=Modifier,
    selectedSeconds:Double?=null
) {
    val points=route.allPoints
    if(points.isEmpty())return
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
        route.unknownGaps.forEach { (a,b) ->
            drawLine(Color(0xFFA5B4AE).copy(alpha = .7f), position(a), position(b),
                1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f,7f)))
        }
        route.pauseGaps.forEach { (a,b) ->
            drawLine(Color(0xFFFFBE7A).copy(alpha = .92f), position(a), position(b),
                2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f,6f)))
        }
        route.segments.forEach { segment ->
            segment.zipWithNext().forEach { (a,b) ->
                drawLine(Color(0xFFD8FB70),position(a),position(b),3.dp.toPx())
            }
        }
        route.activeIsolatedPoints.forEach { point ->
            drawCircle(Color(0xFFD8FB70),radius=4.dp.toPx(),center=position(point))
        }
        route.pausedPoints.forEach { point ->
            drawCircle(
                Color(0xFFFFBE7A).copy(alpha=.92f),
                radius=3.2.dp.toPx(),center=position(point)
            )
        }
        route.allPoints.firstOrNull()?.let { point ->
            val center=position(point)
            drawCircle(Color(0xFF2D343A),radius=5.dp.toPx(),center=center)
            drawCircle(
                Color(0xFFD8FB70),radius=5.dp.toPx(),center=center,
                style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())
            )
        }
        route.allPoints.lastOrNull()?.let { point ->
            val center=position(point)
            drawCircle(Color(0xFFD8FB70),radius=5.dp.toPx(),center=center)
            drawCircle(
                Color(0xFF2D343A),radius=5.dp.toPx(),center=center,
                style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())
            )
        }
        route.selectedPoint(selectedSeconds)?.let { point ->
            val center=position(point)
            drawCircle(Color(0xFF080D0C),radius=7.dp.toPx(),center=center)
            drawCircle(
                Color(0xFFD8FB70),radius=7.dp.toPx(),center=center,
                style=androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx())
            )
        }
    }
}
