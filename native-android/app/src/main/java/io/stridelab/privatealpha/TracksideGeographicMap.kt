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
 * Readable blue-gray MapLibre palette inspired by the supplied Google Maps
 * screenshot. Crucially these are the real layer IDs of OpenFreeMap's hosted
 * Dark Matter style (hyperknot/openfreemap-styles/styles/dark/style.json).
 *
 * The previous palette used layer IDs from a DIFFERENT style ("Road network",
 * "Background", etc.), so every attempted color override was silently skipped
 * and the near-black provider defaults remained visible.
 *
 * Applied once after MapLibre loads the style, never on a drag/frame.
 */
internal object TracksideReadableMapPalette {
    val canvas = AndroidColor.rgb(37, 53, 76)        // #25354C, blue-gray base
    val residential = AndroidColor.rgb(43, 60, 83)
    val buildings = AndroidColor.rgb(49, 67, 87)
    val buildingEdges = AndroidColor.rgb(68, 86, 105)
    val sand = AndroidColor.rgb(77, 79, 76)
    val grass = AndroidColor.rgb(47, 82, 70)
    val wood = AndroidColor.rgb(44, 73, 66)
    val water = AndroidColor.rgb(26, 66, 92)
    val waterways = AndroidColor.rgb(62, 113, 142)
    val minorRoads = AndroidColor.rgb(88, 111, 133)
    val majorRoads = AndroidColor.rgb(120, 143, 161)
    val roadCasings = AndroidColor.rgb(64, 88, 111)
    val motorways = AndroidColor.rgb(143, 164, 180)
    val paths = AndroidColor.rgb(79, 103, 121)
    val rail = AndroidColor.rgb(79, 101, 124)
    val roadLabels = AndroidColor.rgb(214, 227, 237)
    val placeLabels = AndroidColor.rgb(237, 242, 246)
    val labelHalo = AndroidColor.rgb(31, 47, 65)
    val waterLabels = AndroidColor.rgb(163, 203, 223)

    // Stable reference IDs verified against the actual OpenFreeMap dark style.
    // If the provider renames them, an app-side source update should be made.
    internal val recoloredLayerIds = setOf(
        "background", "landuse_residential", "building", "landuse_park",
        "landcover_wood", "landcover_ice_shelf", "landcover_glacier",
        "water", "waterway", "water_name", "road_area_pier", "road_pier",
        "highway_path", "highway_minor", "highway_major_casing",
        "highway_major_inner", "highway_major_subtle",
        "highway_motorway_casing", "highway_motorway_inner",
        "highway_motorway_subtle", "aeroway-area", "aeroway-runway",
        "aeroway-runway-casing", "aeroway-taxiway",
        "railway", "railway_minor", "railway_transit",
        "railway_transit_dashline", "railway_minor_dashline",
        "railway_dashline", "highway_name_other",
        "highway_name_motorway", "place_other", "place_suburb",
        "place_village", "place_town", "place_city", "place_city_large",
        "place_state", "place_country_other", "place_country_minor",
        "place_country_major", "boundary_state",
        "boundary_country_z0-4", "boundary_country_z5-"
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
        fun line(id: String, color: Int) {
            (style.getLayer(id) as? LineLayer)?.setProperties(
                PropertyFactory.lineColor(color))
        }
        fun text(id: String, color: Int, sizeSp: Float? = null) {
            (style.getLayer(id) as? SymbolLayer)?.let { layer ->
                layer.setProperties(
                    PropertyFactory.textColor(color),
                    PropertyFactory.textHaloColor(labelHalo),
                    PropertyFactory.textHaloWidth(1.15f))
                if (sizeSp != null) {
                    // The provider defaults to tiny 10px labels. Improve
                    // legibility without increasing MapLibre's work each frame.
                    layer.setProperties(PropertyFactory.textSize(sizeSp))
                }
            }
        }

        background("background", canvas)
        fill("landuse_residential", residential)
        fill("building", buildings)
        // Dark Matter's default near-black outlines would still obscure
        // buildings against their new fill if not recolored too.
        (style.getLayer("building") as? FillLayer)?.setProperties(
            PropertyFactory.fillOutlineColor(buildingEdges))
        fill("landuse_park", grass)
        // The wood layer uses a patterned fill; its pattern may retain
        // contrast beyond the solid color. Keep the provider's geometry.
        fill("landcover_wood", wood)
        fill("landcover_ice_shelf", residential)
        fill("landcover_glacier", residential)
        fill("water", water)
        line("waterway", waterways)
        text("water_name", waterLabels)
        fill("road_area_pier", residential)
        line("road_pier", majorRoads)

        // Respect the provider's zoom-dependent width and road hierarchy.
        line("highway_path", paths)
        line("highway_minor", minorRoads)
        line("highway_major_casing", roadCasings)
        line("highway_major_inner", majorRoads)
        line("highway_major_subtle", minorRoads)
        line("highway_motorway_casing", roadCasings)
        line("highway_motorway_inner", motorways)
        line("highway_motorway_subtle", majorRoads)

        fill("aeroway-area", residential)
        line("aeroway-taxiway", paths)
        line("aeroway-runway", majorRoads)
        line("aeroway-runway-casing", roadCasings)
        for (layer in listOf(
            "railway", "railway_minor", "railway_transit",
            "railway_transit_dashline", "railway_minor_dashline",
            "railway_dashline"
        )) line(layer, rail)

        text("highway_name_other", roadLabels, sizeSp = 11.5f)
        text("highway_name_motorway", roadLabels, sizeSp = 11f)
        text("place_other", placeLabels, sizeSp = 11f)
        text("place_suburb", placeLabels, sizeSp = 12.5f)
        text("place_village", placeLabels)
        text("place_town", placeLabels)
        text("place_city", placeLabels)
        text("place_city_large", placeLabels)
        text("place_state", placeLabels)
        text("place_country_other", placeLabels)
        text("place_country_minor", placeLabels)
        text("place_country_major", placeLabels)

        line("boundary_state", roadCasings)
        line("boundary_country_z0-4", roadCasings)
        line("boundary_country_z5-", roadCasings)
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
    Box(modifier.background(Color(0xFF25354C)).testTag("activity-geographic-map")) {
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
                // Provider style layers exist at this point. Recolor the map
                // before presenting the real GPS line and fitting the camera.
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
