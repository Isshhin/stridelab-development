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
import androidx.compose.ui.graphics.PathEffect
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
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
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
    // Liberty is OpenFreeMap's actively maintained general-purpose style.
    // StrideLab applies its own activity-map palette after the provider style
    // loads, keeping the keyless OpenFreeMap tile infrastructure unchanged.
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
    const val ATTRIBUTION = "© OpenStreetMap contributors · © OpenMapTiles · OpenFreeMap"
}

/**
 * Light activity-map palette inspired by the visual hierarchy of modern
 * running maps: quiet neutral land, visible greenspace, muted blue water,
 * bright roads, restrained labels and very low POI noise.
 *
 * The base is OpenFreeMap Liberty. These IDs are Liberty's real published
 * layer IDs, so styling is applied to existing geometry rather than drawing
 * an opaque tint over the map.
 */
internal object TracksideReadableMapPalette {
    val canvas = AndroidColor.rgb(239, 241, 238)        // #EFF1EE
    val residential = AndroidColor.rgb(234, 236, 233)  // #EAECE9
    val buildings = AndroidColor.rgb(220, 223, 219)    // #DCDFDB
    val buildingEdges = AndroidColor.rgb(207, 211, 206)
    val park = AndroidColor.rgb(202, 225, 191)          // soft activity-map green
    val grass = AndroidColor.rgb(211, 229, 201)
    val wood = AndroidColor.rgb(190, 216, 180)
    val pitch = AndroidColor.rgb(215, 230, 205)
    val sand = AndroidColor.rgb(238, 232, 205)
    val water = AndroidColor.rgb(183, 211, 226)
    val waterways = AndroidColor.rgb(157, 196, 218)
    val minorRoads = AndroidColor.rgb(255, 255, 255)
    val secondaryRoads = AndroidColor.rgb(250, 250, 248)
    val primaryRoads = AndroidColor.rgb(247, 247, 244)
    val motorwayRoads = AndroidColor.rgb(242, 239, 229)
    val roadCasings = AndroidColor.rgb(214, 217, 212)
    val paths = AndroidColor.rgb(229, 229, 224)
    val rail = AndroidColor.rgb(190, 194, 190)
    val roadLabels = AndroidColor.rgb(91, 96, 92)
    val placeLabels = AndroidColor.rgb(54, 59, 56)
    val labelHalo = AndroidColor.rgb(248, 249, 247)
    val waterLabels = AndroidColor.rgb(91, 139, 166)

    internal val recoloredLayerIds = setOf(
        "background", "park", "park_outline", "landuse_residential",
        "landcover_wood", "landcover_grass", "landcover_sand",
        "landuse_pitch", "landuse_track", "landuse_cemetery",
        "landuse_hospital", "landuse_school", "water",
        "waterway_river", "waterway_other", "aeroway_fill",
        "aeroway_runway", "aeroway_taxiway", "road_minor_casing",
        "road_minor", "road_secondary_tertiary_casing",
        "road_secondary_tertiary", "road_trunk_primary_casing",
        "road_trunk_primary", "road_motorway_casing", "road_motorway",
        "road_service_track", "road_path_pedestrian", "road_major_rail",
        "tunnel_service_track_casing", "tunnel_link_casing",
        "tunnel_street_casing", "tunnel_secondary_tertiary_casing",
        "tunnel_trunk_primary_casing", "tunnel_motorway_casing",
        "tunnel_path_pedestrian", "tunnel_service_track", "tunnel_link",
        "tunnel_minor", "tunnel_secondary_tertiary", "tunnel_trunk_primary",
        "tunnel_motorway", "bridge_service_track_casing", "bridge_link_casing",
        "bridge_street_casing", "bridge_path_pedestrian_casing",
        "bridge_secondary_tertiary_casing", "bridge_trunk_primary_casing",
        "bridge_motorway_casing", "bridge_path_pedestrian",
        "bridge_service_track", "bridge_link", "bridge_street",
        "bridge_secondary_tertiary", "bridge_trunk_primary", "bridge_motorway",
        "building", "waterway_line_label", "water_name_point_label",
        "water_name_line_label", "highway-name-path", "highway-name-minor",
        "highway-name-major", "label_other", "label_village", "label_town",
        "label_state", "label_city", "label_city_capital"
    )

    internal val hiddenClutterLayerIds = setOf(
        "natural_earth", "building-3d",
        "poi_r20", "poi_r7", "poi_r1", "poi_transit"
    )

    fun apply(style: Style) {
        fun background(id: String, color: Int) {
            (style.getLayer(id) as? BackgroundLayer)?.setProperties(
                PropertyFactory.backgroundColor(color))
        }
        fun fill(id: String, color: Int) {
            (style.getLayer(id) as? FillLayer)?.setProperties(
                PropertyFactory.fillColor(color))
        }
        fun outline(id: String, color: Int) {
            (style.getLayer(id) as? FillLayer)?.setProperties(
                PropertyFactory.fillOutlineColor(color))
        }
        fun line(id: String, color: Int) {
            (style.getLayer(id) as? LineLayer)?.setProperties(
                PropertyFactory.lineColor(color))
        }
        fun text(id: String, color: Int) {
            (style.getLayer(id) as? SymbolLayer)?.setProperties(
                PropertyFactory.textColor(color),
                PropertyFactory.textHaloColor(labelHalo),
                PropertyFactory.textHaloWidth(1.15f)
            )
        }

        background("background", canvas)
        fill("park", park)
        line("park_outline", park)
        fill("landuse_residential", residential)
        fill("landcover_wood", wood)
        fill("landcover_grass", grass)
        fill("landcover_sand", sand)
        for (id in listOf(
            "landuse_pitch", "landuse_track", "landuse_cemetery"
        )) fill(id, pitch)
        fill("landuse_hospital", residential)
        fill("landuse_school", residential)

        fill("water", water)
        line("waterway_river", waterways)
        line("waterway_other", waterways)

        fill("aeroway_fill", residential)
        line("aeroway_runway", secondaryRoads)
        line("aeroway_taxiway", paths)

        // Preserve Liberty's zoom-dependent widths; change only visual tone.
        line("road_minor_casing", canvas)
        line("road_minor", minorRoads)
        line("road_secondary_tertiary_casing", roadCasings)
        line("road_secondary_tertiary", secondaryRoads)
        line("road_trunk_primary_casing", roadCasings)
        line("road_trunk_primary", primaryRoads)
        line("road_motorway_casing", roadCasings)
        line("road_motorway", motorwayRoads)
        line("road_service_track", minorRoads)
        line("road_path_pedestrian", paths)
        line("road_major_rail", rail)

        // Bridges and tunnels keep the same hierarchy so route context does
        // not suddenly change color when a road crosses or passes underground.
        for (id in listOf(
            "tunnel_service_track_casing", "tunnel_link_casing",
            "tunnel_street_casing", "tunnel_secondary_tertiary_casing",
            "tunnel_trunk_primary_casing", "tunnel_motorway_casing",
            "bridge_service_track_casing", "bridge_link_casing",
            "bridge_street_casing", "bridge_path_pedestrian_casing",
            "bridge_secondary_tertiary_casing", "bridge_trunk_primary_casing",
            "bridge_motorway_casing"
        )) line(id, roadCasings)
        for (id in listOf(
            "tunnel_service_track", "tunnel_link", "tunnel_minor",
            "bridge_service_track", "bridge_link", "bridge_street"
        )) line(id, minorRoads)
        for (id in listOf(
            "tunnel_secondary_tertiary", "bridge_secondary_tertiary"
        )) line(id, secondaryRoads)
        for (id in listOf(
            "tunnel_trunk_primary", "bridge_trunk_primary"
        )) line(id, primaryRoads)
        for (id in listOf(
            "tunnel_motorway", "bridge_motorway"
        )) line(id, motorwayRoads)
        line("tunnel_path_pedestrian", paths)
        line("bridge_path_pedestrian", paths)

        fill("building", buildings)
        outline("building", buildingEdges)

        for (id in listOf(
            "waterway_line_label", "water_name_point_label",
            "water_name_line_label"
        )) text(id, waterLabels)
        for (id in listOf(
            "highway-name-path", "highway-name-minor", "highway-name-major"
        )) text(id, roadLabels)
        for (id in listOf(
            "label_other", "label_village", "label_town", "label_state",
            "label_city", "label_city_capital"
        )) text(id, placeLabels)

        // Activity maps benefit from route/context, not dense POI chrome.
        hiddenClutterLayerIds.forEach { id ->
            style.getLayer(id)?.setProperties(PropertyFactory.visibility(Property.NONE))
        }
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
    modifier:Modifier=Modifier,
    selectedSeconds:Double?=null
) {
    var styleReady by remember(route,online) { mutableStateOf(false) }
    Box(modifier.background(Color(0xFFEFF1EE)).testTag("activity-geographic-map")) {
        if(online && route.pointCount > 0) {
            MapLibreRouteView(
                route = route,
                modifier = Modifier.fillMaxSize(),
                selectedSeconds = selectedSeconds
            ) { styleReady=true }
        }
        if(!online || !styleReady) {
            RecordedGpsFallback(
                route, Modifier.fillMaxSize(), selectedSeconds
            )
        }
    }
}

@Composable
private fun MapLibreRouteView(
    route:RecordedGpsRoute,
    modifier:Modifier=Modifier,
    selectedSeconds:Double?=null,
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
    val gapJson=remember(route) { route.gapsGeoJson() }
    val pauseGapJson=remember(route) { route.pauseGapsGeoJson() }
    val selectionJson=remember(route,selectedSeconds) {
        route.selectedPointGeoJson(selectedSeconds)
    }
    var selectedPointSource by remember(mapView) {
        mutableStateOf<GeoJsonSource?>(null)
    }

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
    LaunchedEffect(mapView,geoJson,isolatedJson,gapJson,pauseGapJson) {
        mapView.getMapAsync { map ->
            map.uiSettings.setTiltGesturesEnabled(false)
            map.uiSettings.setRotateGesturesEnabled(false)
            map.uiSettings.setAttributionEnabled(false)
            map.uiSettings.setLogoEnabled(false)
            map.setStyle(TracksideMapTiles.STYLE_URL) { style ->
                // Liberty layers exist at this point. Apply StrideLab's
                // light activity-map palette before drawing the GPS overlay.
                TracksideReadableMapPalette.apply(style)
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
                    style.addLayer(
                        LineLayer("stride-gps-line","stride-actual-gps").withProperties(
                            PropertyFactory.lineColor(AndroidColor.rgb(216,251,112)),
                            PropertyFactory.lineWidth(4f),
                            PropertyFactory.lineOpacity(0.95f)
                        )
                    )
                }
                if(route.isolatedPoints.isNotEmpty()) {
                    // Single saved fixes remain visible as isolated points.
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
    LaunchedEffect(selectionJson, selectedPointSource) {
        selectedPointSource?.setGeoJson(selectionJson)
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
        route.isolatedPoints.forEach { point ->
            drawCircle(Color(0xFFD8FB70),radius=4.dp.toPx(),center=position(point))
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
