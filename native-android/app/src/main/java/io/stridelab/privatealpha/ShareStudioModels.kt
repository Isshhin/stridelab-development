package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal enum class ShareTemplate(val label:String) { ROUTE("Route Focus"), STATS("Stats Focus"), MINIMAL("Minimal"), WORKOUT("Workout Recap");
    val variants:List<ShareVariant> get() = when(this) {
        ROUTE -> listOf(ShareVariant.CLEAN,ShareVariant.PERFORMANCE,ShareVariant.STORY)
        STATS -> listOf(ShareVariant.BALANCED,ShareVariant.RUN_RECAP,ShareVariant.PERFORMANCE)
        MINIMAL -> listOf(ShareVariant.POSTER,ShareVariant.SILHOUETTE,ShareVariant.DARK_CLEAN)
        WORKOUT -> listOf(ShareVariant.RUN_RECAP,ShareVariant.BALANCED,ShareVariant.PERFORMANCE)
    }
}
internal enum class ShareVariant(val label:String) {
    CLEAN("Clean"), PERFORMANCE("Performance"), STORY("Story"), BALANCED("Balanced"),
    RUN_RECAP("Run Recap"), POSTER("Poster"), SILHOUETTE("Silhouette"), DARK_CLEAN("Dark Clean")
}
internal enum class ShareRatio(val label:String, val height:Int) {
    STORY("9:16",1920), PORTRAIT("4:5",1350), SQUARE("1:1",1080);
    val dimensions:Pair<Int,Int> get() = 1080 to height
}
internal enum class ShareRouteMode(val label:String, val mapMode:TracksideActivityMapMode?) {
    ROUTE("Route",TracksideActivityMapMode.ROUTE), PACE("Pace",TracksideActivityMapMode.PACE),
    ELEVATION("Elevation",TracksideActivityMapMode.ELEVATION), HR("HR",TracksideActivityMapMode.HEART_RATE), HIDDEN("Hidden",null)
}
internal enum class ShareMetric(val label:String) {
    TITLE("Activity title"), DATE("Date"), LOCATION("Location"), DISTANCE("Distance"),
    DURATION("Duration"), PACE("Average pace"), HR("Average HR"), ELEVATION("Elevation gain")
}
internal enum class ShareBackground(val label:String) { MAP("Activity Dark Map"), DARK("Clean Dark"), GRADIENT("Soft Gradient") }
internal enum class ShareBranding(val label:String) { SUBTLE("Subtle"), STANDARD("Standard"), MINIMAL("Minimal") }
internal enum class SharePrivacy(val label:String, val meters:Double) {
    STANDARD("200 m",200.0), EXTRA("400 m",400.0), MAXIMUM("800 m",800.0),
    OFF("Off (full route)",0.0)
}
internal enum class ShareLayout(val label:String) { AIRY("Airy"), COMPACT("Compact") }

internal data class ShareStudioState(
    val template:ShareTemplate = ShareTemplate.ROUTE,
    val variant:ShareVariant = ShareVariant.CLEAN,
    val ratio:ShareRatio = ShareRatio.STORY,
    val routeMode:ShareRouteMode = ShareRouteMode.ROUTE,
    val metrics:Set<ShareMetric> = setOf(ShareMetric.TITLE,ShareMetric.DATE,ShareMetric.DISTANCE,ShareMetric.DURATION,ShareMetric.PACE,ShareMetric.HR),
    val background:ShareBackground = ShareBackground.DARK,
    val branding:ShareBranding = ShareBranding.SUBTLE,
    val layout:ShareLayout = ShareLayout.AIRY,
    val privacy:SharePrivacy = SharePrivacy.STANDARD
) {
    fun selectTemplate(value:ShareTemplate):ShareStudioState = copy(template=value).selectVariant(value.variants.first())
    fun selectVariant(value:ShareVariant):ShareStudioState {
        require(value in template.variants)
        val sparse = template==ShareTemplate.MINIMAL || value==ShareVariant.STORY
        return copy(variant=value,
            routeMode=if(value==ShareVariant.PERFORMANCE) ShareRouteMode.PACE else ShareRouteMode.ROUTE,
            metrics=if(template==ShareTemplate.WORKOUT)
                setOf(ShareMetric.TITLE,ShareMetric.DATE,ShareMetric.DISTANCE,ShareMetric.DURATION,ShareMetric.PACE)
                else if(value==ShareVariant.SILHOUETTE) setOf(ShareMetric.DISTANCE)
                else if(sparse) setOf(ShareMetric.TITLE,ShareMetric.DISTANCE,ShareMetric.PACE)
                else ShareStudioState().metrics + if(template==ShareTemplate.STATS) setOf(ShareMetric.ELEVATION) else emptySet(),
            background=ShareBackground.DARK,
            layout=if(template==ShareTemplate.WORKOUT || value==ShareVariant.RUN_RECAP || value==ShareVariant.DARK_CLEAN) ShareLayout.COMPACT else ShareLayout.AIRY)
    }

    /** Resolve unavailable fields for this render only; never rewrite a reusable preset. */
    fun forActivity(activity:ShareRenderableActivity):ShareStudioState {
        val available=metrics.intersect(activity.metrics.keys).toMutableSet()
        val stats=activity.metrics.keys.filter { it !in setOf(ShareMetric.TITLE,ShareMetric.DATE,ShareMetric.LOCATION) }
        if(available.intersect(stats.toSet()).isEmpty()) available.add(stats.firstOrNull() ?: ShareMetric.TITLE)
        return copy(metrics=available,routeMode=when {
            routeMode in activity.availableModes -> routeMode
            ShareRouteMode.ROUTE in activity.availableModes -> ShareRouteMode.ROUTE
            else -> ShareRouteMode.HIDDEN
        })
    }
}

/** Recorded laps are factual, but FIT lap records do not classify work or recovery steps. */
internal enum class ShareWorkoutSource(val label:String) {
    USER_LABEL("User labeled"), DETECTED("Detected from activity"), LINKED_PLAN("Planned intent"),
    RECORDED_ONLY("Unclassified recorded laps")
}
internal data class ShareWorkoutLap(val index:Int, val distance:String?, val duration:String?, val pace:String?)
internal data class ShareWorkoutRecap(
    val label:String?,
    val provenance:ShareWorkoutSource,
    val recordingSource:String,
    val totalLaps:Int,
    val laps:List<ShareWorkoutLap>
) {
    companion object {
        fun from(activity:ActivityRecord, detail:ActivityDetail?, assessment:ActivityWorkoutAssessment?,
                 userLabel:UserActivityWorkoutLabel?):ShareWorkoutRecap {
            val eligible=DashboardSummary.isRun(activity)
            val declared=if(eligible) userLabel else null
            val detected=if(eligible && declared==null)
                assessment?.detected?.takeIf { it.kind!=ActivityWorkoutKind.UNKNOWN } else null
            val intended=if(eligible && declared==null && detected==null)
                assessment?.intended?.takeIf { it.kind!=ActivityWorkoutKind.UNKNOWN } else null
            val source=when {
                declared!=null -> ShareWorkoutSource.USER_LABEL
                detected!=null -> ShareWorkoutSource.DETECTED
                intended!=null -> ShareWorkoutSource.LINKED_PLAN
                else -> ShareWorkoutSource.RECORDED_ONLY
            }
            val name=declared?.qualitySubtype?.label ?: declared?.kind?.label
                ?: detected?.qualitySubtype?.label ?: detected?.kind?.label
                ?: intended?.qualitySubtype?.label ?: intended?.kind?.label
            val raw=detail?.laps.orEmpty()
            val laps=raw.take(6).map { lap ->
                ShareWorkoutLap(lap.index+1,
                    lap.distance?.takeIf { it.isFinite() && it>0.0 }?.let {
                        String.format(Locale.ROOT,"%.2f km",it/1000.0)
                    },
                    lap.seconds?.takeIf { it.isFinite() && it>0.0 }?.let { ActivityRecord.duration(it) },
                    lap.pace?.takeIf { it.isFinite() && it>0.0 }?.let {
                        ActivityRecord.duration(it)+" /km"
                    })
            }
            return ShareWorkoutRecap(name,source,activity.sourceLabel,raw.size,laps)
        }
    }
}

internal data class ShareRenderableActivity(
    val id:String,
    val activityType:String,
    val metrics:Map<ShareMetric,String>,
    val route:RecordedGpsRoute,
    val analysis:Map<ShareRouteMode,TracksideAnalysisRoute>,
    val availableModes:Set<ShareRouteMode>,
    val privacy:SharePrivacy,
    val workout:ShareWorkoutRecap? = null
) {
    companion object {
        fun from(activity:ActivityRecord, detail:ActivityDetail?, privacy:SharePrivacy=SharePrivacy.STANDARD,
                 assessment:ActivityWorkoutAssessment?=null, userLabel:UserActivityWorkoutLabel?=null):ShareRenderableActivity {
            val values=linkedMapOf(ShareMetric.TITLE to activity.title,ShareMetric.DATE to activity.dateLabel)
            fun add(metric:ShareMetric, value:Double?, positive:Boolean=false, format:(Double)->String) {
                value?.takeIf { it.isFinite() && if(positive) it>0 else it>=0 }?.let { values[metric]=format(it) }
            }
            add(ShareMetric.DISTANCE,activity.distanceM) { String.format(Locale.getDefault(),"%.2f km",it/1000) }
            add(ShareMetric.DURATION,activity.durationS) { ActivityRecord.duration(it) }
            if(activity.supportsPace) add(ShareMetric.PACE,activity.recordedPace,true) { ActivityRecord.duration(it)+" /km" }
            add(ShareMetric.HR,activity.averageHr,true) { String.format(Locale.getDefault(),"%.0f bpm",it) }
            add(ShareMetric.ELEVATION,activity.elevationM) { String.format(Locale.getDefault(),"%.0f m",it) }
            // No location field exists in the source contract; do not geocode or infer one.
            val samples=detail?.samples.orEmpty()
            val pauses=detail?.pauseIntervals.orEmpty()
            // Render/export only privacy-filtered coordinates. Never modify the FIT source.
            val presentation=shareRoutePresentation(samples,pauses,privacy.meters,activity.supportsPace)
            val route=presentation.route
            val analysis=presentation.analysis
            val modes=linkedSetOf<ShareRouteMode>()
            if(route.pointCount>0) modes.add(ShareRouteMode.ROUTE)
            modes.addAll(analysis.filterValues { it.edges.isNotEmpty() }.keys)
            modes.add(ShareRouteMode.HIDDEN)
            return ShareRenderableActivity(activity.id,activity.type,values,route,analysis,modes,privacy,
                ShareWorkoutRecap.from(activity,detail,assessment,userLabel))
        }
    }
}

internal data class SharePreset(val id:String, val name:String, val state:ShareStudioState)

internal object SharePresetCodec {
    fun encode(presets:List<SharePreset>):String = JSONObject().put("version",1).put("presets",JSONArray().apply {
        presets.forEach { preset -> put(JSONObject().apply {
            put("id",preset.id); put("name",preset.name)
            val s=preset.state
            put("template",s.template.name); put("variant",s.variant.name); put("ratio",s.ratio.name)
            put("route",s.routeMode.name); put("metrics",JSONArray(s.metrics.map { it.name }))
            put("background",s.background.name); put("branding",s.branding.name); put("layout",s.layout.name); put("privacy",s.privacy.name)
        }) }
    }).toString()

    fun decode(raw:String):List<SharePreset> {
        val root=JSONObject(raw)
        require(root.getInt("version")==1) { "Unsupported share preset version" }
        val rows=root.getJSONArray("presets")
        require(rows.length()<=100) { "Too many presets" }
        val result=(0 until rows.length()).map { i ->
            val row=rows.getJSONObject(i)
            val template=ShareTemplate.valueOf(row.getString("template"))
            val variant=ShareVariant.valueOf(row.getString("variant"))
            require(variant in template.variants)
            val fields=row.getJSONArray("metrics")
            val metrics=(0 until fields.length()).map { ShareMetric.valueOf(fields.getString(it)) }.toSet()
            val id=row.getString("id"); val name=row.getString("name")
            require(id.isNotBlank() && id.length<=100 && name.isNotBlank() && name.length<=60)
            SharePreset(id,name,ShareStudioState(template,variant,ShareRatio.valueOf(row.getString("ratio")),
                ShareRouteMode.valueOf(row.getString("route")),metrics,ShareBackground.valueOf(row.getString("background")),
                ShareBranding.valueOf(row.getString("branding")),ShareLayout.valueOf(row.getString("layout")),
                SharePrivacy.valueOf(row.optString("privacy",SharePrivacy.STANDARD.name))))
        }
        require(result.map { it.id }.distinct().size==result.size)
        return result
    }
}
