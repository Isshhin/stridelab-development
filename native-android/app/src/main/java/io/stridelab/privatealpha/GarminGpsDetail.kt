package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import kotlin.math.abs
import kotlin.math.max

/** Verified FIT records become an ActivityDetail for the existing Garmin summary ID. */
internal object GarminGpsDetail {
    fun payload(account:String,summary:ActivityRecord,fit:FitActivityDecoder.Activity,
                fetchedAt:Long):JSONObject {
        require(summary.source=="garmin")
        require(fit.identity.accountId==account)
        require(DashboardSummary.isRun(summary) &&
            fit.sport.lowercase(java.util.Locale.ROOT) in setOf("running","trail_running","walking","hiking")) {
            "FIT sport does not match the cached Garmin running summary"
        }
        require(abs(Duration.between(summary.startedAt,fit.startedAt).seconds)<=120) {
            "Downloaded FIT activity does not match the Garmin start time"
        }
        val sourceDistance=summary.distanceM
        val fitDistance=fit.distanceM
        if(sourceDistance!=null && fitDistance!=null) {
            require(abs(sourceDistance-fitDistance)<=max(200.0,sourceDistance*0.03)) {
                "Downloaded FIT distance does not match Garmin's saved summary"
            }
        }
        val sourceDuration=summary.durationS
        val fitDuration=fit.timerS ?: fit.elapsedS
        if(sourceDuration!=null && fitDuration!=null) {
            require(abs(sourceDuration-fitDuration)<=max(120.0,sourceDuration*0.03)) {
                "Downloaded FIT duration does not match Garmin's saved summary"
            }
        }
        fun optional(value:Double?):Any=value?.takeIf { it.isFinite() } ?: JSONObject.NULL
        val samples=JSONArray()
        fit.points.forEachIndexed { i,p ->
            val elapsed=Duration.between(fit.startedAt,p.at).toMillis()/1000.0
            samples.put(JSONObject()
                .put("activity_id",summary.id)
                .put("sample_index",i)
                .put("elapsed_s",elapsed)
                .put("pace_sec_per_km",optional(p.speedMps?.takeIf{it>0}?.let{1000.0/it}))
                .put("heart_rate",optional(p.heartRate))
                .put("altitude_m",optional(p.altitudeM))
                // Garmin FIT record cadence is strides/minute for running.
                // The Activity Detail chart uses total steps/minute (spm).
                // No conversion is applied when reading already-normalized
                // cadence_spm from other saved source formats.
                .put("cadence_spm", optional(p.cadenceRpm?.let { it * 2.0 }))
                .put("latitude",optional(p.latitude))
                .put("longitude",optional(p.longitude)))
        }
        val laps=JSONArray()
        fit.laps.forEachIndexed { i,lap ->
            laps.put(JSONObject()
                .put("activity_id",summary.id)
                .put("lap_index",i)
                .put("start_s", optional(
                    lap.startedAt?.let {
                        Duration.between(fit.startedAt, it).toMillis() / 1000.0
                    }
                ))
                .put("distance_m",optional(lap.distanceM))
                .put("duration_s",optional(lap.timerS ?: lap.elapsedS))
                .put("avg_pace_sec_per_km",optional(
                    lap.distanceM?.takeIf { it>0 }?.let { meters ->
                        (lap.timerS ?: lap.elapsedS)?.takeIf{it>0}?.let{1000.0*it/meters}
                    }))
                .put("avg_hr",optional(lap.averageHr)))
        }
        val pauseIntervals = JSONArray()
        fit.timerPauses.forEach { pause ->
            val startS = Duration.between(fit.startedAt, pause.startedAt).toMillis() / 1000.0
            val endS = Duration.between(fit.startedAt, pause.endedAt).toMillis() / 1000.0
            if (startS >= 0.0 && endS > startS) {
                pauseIntervals.put(
                    JSONObject()
                        .put("start_s", startS)
                        .put("end_s", endS)
                )
            }
        }
        val sourceZones = fit.sourceHeartRateZones?.let { zones ->
            JSONObject()
                .put("maximum_hr", zones.maximumHr ?: JSONObject.NULL)
                .put("threshold_hr", zones.thresholdHr ?: JSONObject.NULL)
                .put("resting_hr", zones.restingHr ?: JSONObject.NULL)
                .put("calculation_type", zones.calculationType ?: JSONObject.NULL)
                .put("high_bpms", JSONArray(zones.highBpms))
        }
        val raw=JSONObject()
            .put("user_id",account)
            .put("activity_id",summary.id)
            .put("fetched_at",fetchedAt)
            .put("samples",samples)
            .put("laps",laps)
            .put("pause_intervals", pauseIntervals)
        if (sourceZones != null) raw.put("source_hr_zones", sourceZones)
        val validated=ActivityDetail.parse(raw,account,summary.id)
        // An indoor/treadmill FIT often has no GPS but can still contain
        // genuine laps, pace, heart-rate and cadence streams. Never discard
        // those measurements just because the route is absent.
        require(validated.samples.size >= 2 || validated.laps.isNotEmpty()) {
            "Garmin FIT contains no recorded samples or laps to import"
        }
        return raw
    }
    /**
     * Upgrade only cadence in an already persisted Garmin recording. All
     * previously saved samples, GPS, lap metrics and source metadata survive
     * unchanged. A differently sampled FIT is NOT merged by index.
     *
     * Returns null when the refreshed FIT has no recorded cadence.
     */
    fun mergeRecordedCadence(
        existing: JSONObject, refreshed: JSONObject, account: String, id: String
    ): JSONObject? {
        val old = ActivityDetail.parse(existing, account, id)
        val latest = ActivityDetail.parse(refreshed, account, id)
        require(old.samples.size == latest.samples.size && old.laps.size == latest.laps.size) {
            "Garmin FIT point count changed; saved recording was preserved"
        }
        require(old.samples.zip(latest.samples).all { (a,b) ->
            a.index == b.index &&
                abs(a.seconds - b.seconds) <= 0.05 &&
                a.latitude == b.latitude && a.longitude == b.longitude
        }) {
            "Garmin FIT sample positions changed; saved recording was preserved"
        }
        if (latest.samples.none { it.cadenceSpm != null }) return null
        val merged = JSONObject(existing.toString())
        val rows = merged.getJSONArray("samples")
        latest.samples.forEachIndexed { i, sample ->
            val cadence = sample.cadenceSpm
            if (cadence != null) rows.getJSONObject(i).put("cadence_spm", cadence)
        }
        // Validate the merged result before it is offered to persistence.
        ActivityDetail.parse(merged, account, id)
        return merged
    }

}
