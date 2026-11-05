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

internal data class RecordedGpsRoute(val segments:List<List<RecordedGpsPoint>>) {
    val pointCount:Int get()=segments.sumOf { it.size }
    val allPoints:List<RecordedGpsPoint> get()=segments.flatten()

    /**
     * A separate LineString is used for each continuous section. Avoid
     * drawing invented paths across missing fixes, long gaps and the dateline.
     */
    fun toGeoJson():String {
        val lines=JSONArray()
        segments.forEach { segment ->
            val line=JSONArray()
            segment.forEach { point ->
                line.put(JSONArray().put(point.longitude).put(point.latitude))
            }
            lines.put(line)
        }
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

    /** An antimeridian-spanning route is framed to its longest contiguous section. */
    fun cameraPoints():List<RecordedGpsPoint> {
        val points=allPoints
        if(points.isEmpty())return emptyList()
        if(points.maxOf { it.longitude }-points.minOf { it.longitude } > 180.0)
            return segments.maxByOrNull { it.size }.orEmpty()
        return points
    }

    companion object {
        fun from(samples:List<ActivitySample>):RecordedGpsRoute {
            val output=ArrayList<List<RecordedGpsPoint>>()
            var current=ArrayList<RecordedGpsPoint>()
            fun flush() {
                if(current.size>=2)output.add(current.toList())
                current=ArrayList()
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
                    abs(point.longitude-last.longitude)>=180.0))flush()
                current.add(point)
            }
            flush()
            return RecordedGpsRoute(output)
        }
    }
}
