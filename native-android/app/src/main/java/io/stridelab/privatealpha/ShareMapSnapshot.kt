package io.stridelab.privatealpha

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A separate offscreen map, using the existing Activity Dark style and recorded route data. */
internal suspend fun shareMapSnapshot(context:Context, activity:ShareRenderableActivity, state:ShareStudioState):Bitmap =
    withContext(Dispatchers.Main.immediate) {
        withTimeout(15_000) {
            suspendCancellableCoroutine { continuation ->
                var snapshotter:MapSnapshotter?=null
                try {
                    MapLibre.getInstance(context.applicationContext)
                    val rect=ShareCardRenderer.geometry(state).route
                    val points=activity.route.cameraPoints()
                    require(points.isNotEmpty())
                    val bounds=LatLngBounds.Builder().includes(points.map { LatLng(it.latitude,it.longitude) }).build()
                    val style=Style.Builder().fromUri(TracksideMapTiles.STYLE_URL)
                        .withSource(GeoJsonSource("share-route",activity.route.toGeoJson()))
                        .withLayer(LineLayer("share-route-line","share-route").withProperties(
                            PropertyFactory.lineColor(state.routeColor.color),PropertyFactory.lineWidth(state.stroke.width),
                            PropertyFactory.lineOpacity(1f)))
                        .withSource(GeoJsonSource("share-isolated",activity.route.isolatedGeoJson()))
                        .withLayer(CircleLayer("share-isolated-points","share-isolated").withProperties(
                            PropertyFactory.circleRadius(4f),PropertyFactory.circleColor(ShareCardPalette.LIME)))
                        .withSource(GeoJsonSource("share-paused",activity.route.pausedPointsGeoJson()))
                        .withLayer(CircleLayer("share-paused-points","share-paused").withProperties(
                            PropertyFactory.circleRadius(3f),PropertyFactory.circleColor(ShareCardPalette.MUTED)))
                    val analysis=activity.analysis[state.routeMode]
                    if(analysis?.minimum!=null && analysis.maximum!=null) {
                        val low=analysis.minimum
                        val high=analysis.maximum.coerceAtLeast(low+.001)
                        val colors=ShareCardPalette.colors(state.routeMode)
                        style.withSource(GeoJsonSource("share-analysis",analysis.geoJson))
                            .withLayer(LineLayer("share-analysis-line","share-analysis").withProperties(
                                PropertyFactory.lineWidth(state.stroke.width),PropertyFactory.lineColor(Expression.interpolate(
                                    Expression.linear(),Expression.get("value"),
                                    Expression.stop(low,Expression.color(colors[0])),
                                    Expression.stop((low+high)/2,Expression.color(colors[1])),
                                    Expression.stop(high,Expression.color(colors[2]))))))
                    }
                    val pad=if(state.zoom==ShareZoom.FIT) 70 else 10
                    val shift=when(state.position) { SharePosition.LEFT -> -50; SharePosition.CENTER -> 0; SharePosition.RIGHT -> 50 }
                    val vertical=when(state.verticalPosition) { ShareVerticalPosition.UP -> -50; ShareVerticalPosition.CENTER -> 0; ShareVerticalPosition.DOWN -> 50 }
                    snapshotter=MapSnapshotter(context.applicationContext,MapSnapshotter.Options(rect.width().toInt(),rect.height().toInt())
                        .withPixelRatio(1f).withStyleBuilder(style).withRegion(bounds)
                        .withPadding((pad+shift).coerceAtLeast(0),(pad+vertical).coerceAtLeast(0),(pad-shift).coerceAtLeast(0),(pad-vertical).coerceAtLeast(0)).withLogo(true).withAttribution(true))
                    val active=snapshotter
                    continuation.invokeOnCancellation { active.cancel() }
                    active.start({ snapshot ->
                        if(continuation.isActive) continuation.resume(snapshot.bitmap)
                    }, { error ->
                        if(continuation.isActive) continuation.resumeWithException(IllegalStateException(error))
                    })
                } catch(error:Exception) {
                    snapshotter?.cancel()
                    if(continuation.isActive) continuation.resumeWithException(error)
                }
            }
        }
    }
