package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject

internal data class RecordedAnalysisEdge(val start:RecordedGpsPoint, val end:RecordedGpsPoint, val value:Double)

internal data class TracksideAnalysisRoute(
    val geoJson:String,
    val minimum:Double?,
    val maximum:Double?,
    val edges:List<RecordedAnalysisEdge> = emptyList()
)

internal fun activityAnalysisRoute(
    samples:List<ActivitySample>,
    pauses:List<ActivityPauseInterval>,
    mode:TracksideActivityMapMode
):TracksideAnalysisRoute {
    if (mode == TracksideActivityMapMode.ROUTE) {
        return TracksideAnalysisRoute(
            JSONObject().put("type","FeatureCollection")
                .put("features",JSONArray()).toString(),
            null,null
        )
    }
    fun value(sample:ActivitySample):Double?=when(mode) {
        TracksideActivityMapMode.PACE -> sample.pace
        TracksideActivityMapMode.ELEVATION -> sample.altitude
        TracksideActivityMapMode.HEART_RATE -> sample.hr
        TracksideActivityMapMode.ROUTE -> null
    }?.takeIf { it.isFinite() }

    fun crossesPause(a:Double,b:Double)=pauses.any {
        a < it.endSeconds && b > it.startSeconds
    }
    val features=JSONArray()
    val edges=ArrayList<RecordedAnalysisEdge>()
    val values=ArrayList<Double>()
    samples.zipWithNext().forEach { (a,b) ->
        val av=value(a)
        val bv=value(b)
        val alat=a.latitude
        val alon=a.longitude
        val blat=b.latitude
        val blon=b.longitude
        if(av==null || bv==null || alat==null || alon==null ||
            blat==null || blon==null ||
            b.seconds-a.seconds > 120.0 ||
            crossesPause(a.seconds,b.seconds) ||
            kotlin.math.abs(alon-blon) >= 180.0
        ) return@forEach
        val metric=(av+bv)/2.0
        values.add(metric)
        edges.add(RecordedAnalysisEdge(RecordedGpsPoint(alat,alon,a.seconds), RecordedGpsPoint(blat,blon,b.seconds), metric))
        features.put(
            JSONObject().put("type","Feature")
                .put("properties",JSONObject().put("value",metric))
                .put("geometry",JSONObject()
                    .put("type","LineString")
                    .put("coordinates",JSONArray()
                        .put(JSONArray().put(alon).put(alat))
                        .put(JSONArray().put(blon).put(blat))))
        )
    }
    return TracksideAnalysisRoute(
        JSONObject().put("type","FeatureCollection").put("features",features).toString(),
        values.minOrNull(),values.maxOrNull(),edges
    )
}
