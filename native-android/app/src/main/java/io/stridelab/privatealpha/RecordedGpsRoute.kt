package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

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

    /** Actual lone coordinate fixes, retained even if there was no drawn line. */
    fun isolatedGeoJson():String {
        val coords=JSONArray()
        activeIsolatedPoints.forEach { point ->
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
     * A separate LineString is used for each continuous section. Avoid
     * drawing invented paths across missing fixes, timer pauses, long gaps,
     * and the dateline.
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
            fun crossesPause(start:Double,end:Double):Boolean =
                pauseIntervals.any { pause ->
                    start < pause.endSeconds && end > pause.startSeconds
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
                    crossesPause(last.elapsedSeconds,point.elapsedSeconds) ||
                    abs(point.longitude-last.longitude)>=180.0))flush()
                current.add(point)
            }
            flush()
            return RecordedGpsRoute(output,pauseIntervals)
        }
    }
}
