package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

internal data class ShareRoutePresentation(val route:RecordedGpsRoute, val analysis:Map<ShareRouteMode,TracksideAnalysisRoute>)

/** Filter first; no source samples or pause intervals are changed. */
internal fun shareRoutePresentation(samples:List<ActivitySample>, pauses:List<ActivityPauseInterval>, trimMeters:Double, supportsPace:Boolean):ShareRoutePresentation {
    require(trimMeters.isFinite() && trimMeters > 0)
    val recorded=RecordedGpsRoute.from(samples,pauses)
    val original=recorded.segments
    val all=recorded.allPoints
    if(all.size<2) return ShareRoutePresentation(RecordedGpsRoute(emptyList()),emptyMap())
    // Cartesian chord distances slightly underestimate surface distances, keeping the exclusion conservative.
    fun xyz(p:RecordedGpsPoint):DoubleArray {
        val lat=Math.toRadians(p.latitude); val lon=Math.toRadians(p.longitude); val r=6_371_000.0
        return doubleArrayOf(r*cos(lat)*cos(lon),r*cos(lat)*sin(lon),r*sin(lat))
    }
    fun distance(a:RecordedGpsPoint,b:RecordedGpsPoint):Double {
        val x=xyz(a); val y=xyz(b)
        return sqrt((0..2).sumOf { (x[it]-y[it]).pow(2) })
    }
    fun edgeDistance(a:RecordedGpsPoint,b:RecordedGpsPoint,center:RecordedGpsPoint):Double {
        val x=xyz(a); val y=xyz(b); val c=xyz(center)
        val v=DoubleArray(3) { y[it]-x[it] }; val w=DoubleArray(3) { c[it]-x[it] }
        val length=v.sumOf { it*it }
        val t=if(length==0.0) 0.0 else ((0..2).sumOf { w[it]*v[it] }/length).coerceIn(0.0,1.0)
        return sqrt((0..2).sumOf { (w[it]-t*v[it]).pow(2) })
    }
    val cumulative=HashMap<RecordedGpsPoint,Double>()
    var total=0.0
    original.forEach { segment ->
        segment.forEachIndexed { i,p ->
            if(i>0) total+=distance(segment[i-1],p)
            cumulative[p]=total
        }
    }
    val first=all.first(); val last=all.last()
    fun safePoint(p:RecordedGpsPoint)=cumulative.getValue(p)>=trimMeters && total-cumulative.getValue(p)>=trimMeters &&
        distance(p,first)>=trimMeters && distance(p,last)>=trimMeters
    fun safeEdge(a:RecordedGpsPoint,b:RecordedGpsPoint)=edgeDistance(a,b,first)>=trimMeters && edgeDistance(a,b,last)>=trimMeters
    val connected=mutableListOf<MutableList<RecordedGpsPoint>>()
    original.forEachIndexed { i,segment ->
        val previous=original.getOrNull(i-1)?.lastOrNull(); val next=segment.firstOrNull()
        val pause=previous!=null && next!=null && abs(previous.longitude-next.longitude)<180 &&
            pauses.any { previous.elapsedSeconds<it.endSeconds && next.elapsedSeconds>it.startSeconds }
        if(!pause || connected.isEmpty()) connected.add(mutableListOf())
        connected.last().addAll(segment)
    }
    val filtered=mutableListOf<List<RecordedGpsPoint>>()
    connected.forEach { segment ->
        var run=mutableListOf<RecordedGpsPoint>()
        fun flush() { if(run.size>=2) filtered.add(run); run=mutableListOf() }
        segment.forEach { p ->
            if(!safePoint(p)) flush()
            else {
                if(run.isNotEmpty() && !safeEdge(run.last(),p)) flush()
                run.add(p)
            }
        }
        flush()
    }
    // Decimate only after cutting; never replace a safe path with an edge through an exclusion zone.
    val rendered=filtered.map { segment ->
        if(segment.size<=2000) segment else {
            val candidate=(0 until 2000).map { segment[round(it.toDouble()*segment.lastIndex/1999).toInt()] }
            if(candidate.zipWithNext().all { (a,b) -> safeEdge(a,b) }) candidate else segment
        }
    }
    val byPoint=samples.mapNotNull { s ->
        if(s.latitude==null || s.longitude==null) null else RecordedGpsPoint(s.latitude,s.longitude,s.seconds) to s
    }.toMap()
    val analysis=ShareRouteMode.entries.filter { it in setOf(ShareRouteMode.PACE,ShareRouteMode.HR,ShareRouteMode.ELEVATION) && (it!=ShareRouteMode.PACE || supportsPace) }.associateWith { mode ->
        fun metric(p:RecordedGpsPoint):Double? = byPoint[p]?.let { s -> when(mode) {
            ShareRouteMode.PACE -> s.pace; ShareRouteMode.HR -> s.hr; ShareRouteMode.ELEVATION -> s.altitude; else -> null
        } }?.takeIf { it.isFinite() }
        val edges=rendered.flatMap { segment -> segment.zipWithNext().mapNotNull { (a,b) ->
            val av=metric(a); val bv=metric(b)
            if(av==null || bv==null || pauses.any { a.elapsedSeconds<it.endSeconds && b.elapsedSeconds>it.startSeconds }) null
            else RecordedAnalysisEdge(a,b,(av+bv)/2)
        } }
        val features=JSONArray()
        edges.forEach { e -> features.put(JSONObject().put("type","Feature").put("properties",JSONObject().put("value",e.value))
            .put("geometry",JSONObject().put("type","LineString").put("coordinates",JSONArray()
                .put(JSONArray().put(e.start.longitude).put(e.start.latitude)).put(JSONArray().put(e.end.longitude).put(e.end.latitude))))) }
        TracksideAnalysisRoute(JSONObject().put("type","FeatureCollection").put("features",features).toString(),
            edges.minOfOrNull { it.value },edges.maxOfOrNull { it.value },edges)
    }
    return ShareRoutePresentation(RecordedGpsRoute(rendered),analysis)
}
