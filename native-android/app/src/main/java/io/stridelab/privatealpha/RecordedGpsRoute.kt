package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Only recorded GPS samples; no inferred locations or gap interpolation. */
internal data class RecordedGpsPoint(
    val latitude:Double,
    val longitude:Double,
    val elapsedSeconds:Double
)

internal data class RecordedGpsRoute(
    val segments:List<List<RecordedGpsPoint>>,
    val pauseIntervals:List<ActivityPauseInterval> = emptyList()
) {
    val pointCount:Int get()=segments.sumOf { it.size }
    val allPoints:List<RecordedGpsPoint> get()=segments.flatten()
    val isolatedPoints:List<RecordedGpsPoint> get()=segments.filter { it.size == 1 }.flatten()

    private fun isPaused(seconds: Double): Boolean =
        pauseIntervals.any { seconds >= it.startSeconds && seconds <= it.endSeconds }

    /** Real coordinate fixes whose FIT timestamps fall inside a timer pause. */
    val pausedPoints:List<RecordedGpsPoint> get()=allPoints.filter { isPaused(it.elapsedSeconds) }

    /** Lone fixes excluding points already represented as paused-source evidence. */
    val activeIsolatedPoints:List<RecordedGpsPoint> get()=
        isolatedPoints.filterNot { isPaused(it.elapsedSeconds) }


    private val segmentGaps:List<Pair<RecordedGpsPoint,RecordedGpsPoint>> get()=
        segments.zipWithNext().mapNotNull { (a,b) ->
            val start=a.lastOrNull() ?: return@mapNotNull null
            val end=b.firstOrNull() ?: return@mapNotNull null
            if (abs(start.longitude-end.longitude)>=180.0) null else start to end
        }

    /** Known timer pauses from FIT stop/start events. No path is inferred. */
    val pauseGaps:List<Pair<RecordedGpsPoint,RecordedGpsPoint>> get()=
        segmentGaps.filter { (start,end) -> crossesPause(start.elapsedSeconds,end.elapsedSeconds) }

    /** Missing/long GPS gaps not explicitly explained by a timer pause. */
    val unknownGaps:List<Pair<RecordedGpsPoint,RecordedGpsPoint>> get()=
        segmentGaps.filterNot { (start,end) -> crossesPause(start.elapsedSeconds,end.elapsedSeconds) }

    /** Backward-compatible union for tests/legacy callers. */
    val gaps:List<Pair<RecordedGpsPoint,RecordedGpsPoint>> get()=segmentGaps

    val gpsContinuityLabel:String get()=when {
        pointCount == 0 -> "No recorded GPS"
        gaps.isEmpty() -> "Continuous GPS"
        gaps.size == 1 -> "1 GPS gap"
        else -> gaps.size.toString() + " GPS gaps"
    }

    private fun crossesPause(start:Double,end:Double):Boolean =
        pauseIntervals.any { pause ->
            start < pause.endSeconds && end > pause.startSeconds
        }

    private fun multiLineFeature(lines:JSONArray):String {
        val feature=JSONObject()
            .put("type","Feature")
            .put("properties",JSONObject())
            .put("geometry",JSONObject()
                .put("type","MultiLineString")
                .put("coordinates",lines))
        return JSONObject()
            .put("type","FeatureCollection")
            .put("features",JSONArray().put(feature))
            .toString()
    }

    /** Source lone fixes use normal route color, including during a pause. */
    fun isolatedGeoJson():String {
        val coords=JSONArray()
        isolatedPoints.forEach { point ->
            coords.put(JSONArray().put(point.longitude).put(point.latitude))
        }
        return JSONObject()
            .put("type","Feature")
            .put("properties",JSONObject())
            .put("geometry",JSONObject().put("type","MultiPoint").put("coordinates",coords))
            .toString()
    }

    /** Actual GPS fixes recorded while the source timer was paused. */
    fun pausedPointsGeoJson():String {
        val coords=JSONArray()
        pausedPoints.forEach { point ->
            coords.put(JSONArray().put(point.longitude).put(point.latitude))
        }
        return JSONObject()
            .put("type","Feature")
            .put("properties",JSONObject())
            .put("geometry",JSONObject().put("type","MultiPoint").put("coordinates",coords))
            .toString()
    }

    private fun endpointGeoJson(point: RecordedGpsPoint?): String =
        if (point == null) {
            JSONObject().put("type","FeatureCollection")
                .put("features",JSONArray()).toString()
        } else {
            JSONObject()
                .put("type","Feature")
                .put("properties",JSONObject())
                .put("geometry",JSONObject()
                    .put("type","Point")
                    .put("coordinates",JSONArray().put(point.longitude).put(point.latitude)))
                .toString()
        }

    fun startPointGeoJson():String = endpointGeoJson(allPoints.firstOrNull())
    fun finishPointGeoJson():String = endpointGeoJson(allPoints.lastOrNull())


    /** Sparse real route points with recorded-direction bearings for close zoom. */
    fun directionMarkersGeoJson():String {
        val features=JSONArray()
        fun bearing(a:RecordedGpsPoint,b:RecordedGpsPoint):Double {
            val lat1=Math.toRadians(a.latitude)
            val lat2=Math.toRadians(b.latitude)
            val dLon=Math.toRadians(b.longitude-a.longitude)
            val y=sin(dLon)*cos(lat2)
            val x=cos(lat1)*sin(lat2)-sin(lat1)*cos(lat2)*cos(dLon)
            return (Math.toDegrees(atan2(y,x))+360.0)%360.0
        }
        segments.filter { it.size >= 3 }.forEach { segment ->
            val targetMarkers=(segment.size/80).coerceIn(1,4)
            val step=((segment.size-1)/(targetMarkers+1)).coerceAtLeast(1)
            var i=step
            while(i < segment.lastIndex && features.length() < 16) {
                val point=segment[i]
                val next=segment[(i+1).coerceAtMost(segment.lastIndex)]
                features.put(
                    JSONObject().put("type","Feature")
                        .put("properties",JSONObject().put("bearing",bearing(point,next)))
                        .put("geometry",JSONObject()
                            .put("type","Point")
                            .put("coordinates",JSONArray().put(point.longitude).put(point.latitude)))
                )
                i+=step
            }
        }
        return JSONObject().put("type","FeatureCollection")
            .put("features",features).toString()
    }

    /**
     * Source-lap markers use the next lap's recorded start timestamp as the
     * prior split boundary. No distance point or coordinate is interpolated.
     */
    fun splitMarkersGeoJson(laps:List<ActivityLap>):String {
        val features=JSONArray()
        laps.drop(1).forEachIndexed { offset,lap ->
            val at=lap.startSeconds ?: return@forEachIndexed
            val point=selectedPoint(at) ?: return@forEachIndexed
            features.put(
                JSONObject().put("type","Feature")
                    .put("properties",JSONObject().put("label",(offset+1).toString()))
                    .put("geometry",JSONObject()
                        .put("type","Point")
                        .put("coordinates",JSONArray().put(point.longitude).put(point.latitude)))
            )
        }
        return JSONObject().put("type","FeatureCollection")
            .put("features",features).toString()
    }

    /** Highlight only real recorded route points within a selected split range. */
    fun rangeGeoJson(range:Pair<Double,Double>?):String {
        val lines=JSONArray()
        if(range != null) {
            segments.forEach { segment ->
                val selected=segment.filter {
                    it.elapsedSeconds >= range.first && it.elapsedSeconds <= range.second
                }
                if(selected.size >= 2) {
                    val line=JSONArray()
                    selected.forEach { point ->
                        line.put(JSONArray().put(point.longitude).put(point.latitude))
                    }
                    lines.put(line)
                }
            }
        }
        return multiLineFeature(lines)
    }

    private fun gapGeoJson(gaps:List<Pair<RecordedGpsPoint,RecordedGpsPoint>>):String {
        val lines=JSONArray()
        gaps.forEach { (a,b) ->
            lines.put(JSONArray()
                .put(JSONArray().put(a.longitude).put(a.latitude))
                .put(JSONArray().put(b.longitude).put(b.latitude)))
        }
        return multiLineFeature(lines)
    }

    /** Straight dashed visual connector means unknown tracking, NOT distance. */
    fun gapsGeoJson():String = gapGeoJson(unknownGaps)

    /** Amber dashed connector marks a real FIT timer pause/resume interval. */
    fun pauseGapsGeoJson():String = gapGeoJson(pauseGaps)

    /**
     * A separate LineString is used for each recorded continuous section.
     * Timer state alone does not remove GPS that the watch really recorded.
     * Unrecorded pause intervals, missing fixes, long gaps and the dateline
     * remain disconnected rather than being reconstructed.
     */
    fun toGeoJson():String {
        val lines=JSONArray()
        segments.filter { it.size >= 2 }.forEach { segment ->
            val line=JSONArray()
            segment.forEach { point ->
                line.put(JSONArray().put(point.longitude).put(point.latitude))
            }
            lines.put(line)
        }
        return multiLineFeature(lines)
    }

    fun selectedPoint(seconds:Double?):RecordedGpsPoint? {
        if(seconds == null || !seconds.isFinite())return null
        return allPoints.minByOrNull { abs(it.elapsedSeconds-seconds) }
            ?.takeIf { abs(it.elapsedSeconds-seconds) <= 90.0 }
    }

    fun selectedPointGeoJson(seconds:Double?):String {
        val point=selectedPoint(seconds)
        return if(point==null) {
            JSONObject().put("type","FeatureCollection")
                .put("features",JSONArray()).toString()
        } else {
            JSONObject()
                .put("type","Feature")
                .put("properties",JSONObject())
                .put("geometry",JSONObject()
                    .put("type","Point")
                    .put("coordinates",JSONArray()
                        .put(point.longitude).put(point.latitude)))
                .toString()
        }
    }

    /** An antimeridian-spanning route is framed to its longest contiguous section. */
    fun cameraPoints():List<RecordedGpsPoint> {
        val points=allPoints
        if(points.isEmpty())return emptyList()
        if(points.maxOf { it.longitude }-points.minOf { it.longitude } > 180.0)
            return segments.maxByOrNull { it.size }.orEmpty()
        return points
    }

    companion object {
        fun from(
            samples:List<ActivitySample>,
            pauseIntervals:List<ActivityPauseInterval> = emptyList()
        ):RecordedGpsRoute {
            val output=ArrayList<List<RecordedGpsPoint>>()
            var current=ArrayList<RecordedGpsPoint>()
            fun flush() {
                if(current.isNotEmpty())output.add(current.toList())
                current=ArrayList()
            }
            // Consecutive fixes outside a complete timer pause do not prove
            // where the athlete travelled during that unrecorded interval.
            // Fixes actually recorded inside the pause are retained on the
            // same solid line, with the normal GPS continuity guards.
            fun unrecordedPauseBetween(start:Double,end:Double):Boolean =
                pauseIntervals.any { pause ->
                    start <= pause.startSeconds && end >= pause.endSeconds
                }
            for(sample in samples) {
                val lat=sample.latitude
                val lon=sample.longitude
                // Web Mercator cannot display a track at the poles.
                if(lat==null || lon==null || !lat.isFinite() || !lon.isFinite() ||
                    lat !in -85.0..85.0 || lon !in -180.0..180.0 ||
                    !sample.seconds.isFinite()) {
                    flush()
                    continue
                }
                val point=RecordedGpsPoint(lat,lon,sample.seconds)
                val last=current.lastOrNull()
                if(last!=null && (point.elapsedSeconds-last.elapsedSeconds>120.0 ||
                    point.elapsedSeconds<last.elapsedSeconds ||
                    unrecordedPauseBetween(last.elapsedSeconds,point.elapsedSeconds) ||
                    abs(point.longitude-last.longitude)>=180.0))flush()
                current.add(point)
            }
            flush()
            return RecordedGpsRoute(output,pauseIntervals)
        }
    }
}
