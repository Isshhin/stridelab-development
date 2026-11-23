package io.stridelab.privatealpha

import kotlin.math.abs
import kotlin.math.roundToInt

/** Descriptive, read-only analysis of one source run and optional linked execution evidence. */
internal data class WeatherContext(val tempC: Double?, val humidityPct: Double?, val dewPointC: Double?)

internal data class RunningAnalysisReport(
    val debrief: SessionDebrief?, val effect: TrainingEffect, val dynamics: RunningDynamicsSummary?
)

internal object Engine15RunningDebriefAnalyzer {
    @Suppress("UNUSED_PARAMETER") // Reserved for future measured weather context.
    fun analyze(
        activity: ActivityRecord,
        detail: ActivityDetail? = null,
        execution: WorkoutExecutionAnalysis? = null,
        verifiedHrZones: ExecutionHrZoneProfile? = null,
        sessionIntent: ActivityWorkoutKind? = null,
        weather: WeatherContext? = null
    ): SessionDebrief? {
        return report(activity, detail, execution, verifiedHrZones, sessionIntent).debrief
    }

    fun report(activity: ActivityRecord, detail: ActivityDetail?, execution: WorkoutExecutionAnalysis?,
               verifiedHrZones: ExecutionHrZoneProfile?, sessionIntent: ActivityWorkoutKind?): RunningAnalysisReport {
        if (!DashboardSummary.isRun(activity)) return RunningAnalysisReport(null, TrainingEffect(null, null), null)
        val recording = detail?.takeIf { it.activityId == activity.id }
        val linked = execution?.takeIf { it.activityId == activity.id }
        val zones = verifiedRunningZones(recording, verifiedHrZones)
        val stream = recording?.let { runningMovingStream(it, activity.movingTimeS) }
        val effect = TrainingEffectCalculator.calculate(stream, zones)
        val dynamics = recording?.let { summarizeRunningDynamics(it, stream) }
        val candidates = listOfNotNull(
            linked?.let(::executionInsight),
            if (stream != null && zones != null) heartRateInsight(stream, zones, activity) else null,
            if (stream != null && zones != null) zoneInsight(stream, zones, linked, sessionIntent) else null,
            recording?.let { splitInsight(activity, it) } ?: stream?.let(::steadyPacing),
            stream?.let(::dynamicsInsight),
            stream?.let(::cadenceInsight)
        )
        val context = listOfNotNull(
            stream?.let { changingPace(it, zones, candidates) },
            if (effect.aerobic != null && zones != null) DebriefCopyTemplates.measuredPulse(activity, zones,
                linked?.takeUnless { it.evidenceTier == ExecutionEvidenceTier.SUMMARY_ONLY }?.aerobicDecouplingPct) else null,
            DebriefCopyTemplates.workIntervals(linked) ?: recording?.let(::recordedIntervals),
            DebriefCopyTemplates.timeOnFeet(activity),
            terrainInsight(activity, stream),
            DebriefCopyTemplates.form(dynamics, candidates),
            DebriefCopyTemplates.trainingLoad(effect)
        )
        val highlights = SessionDebrief.fromInsights(candidates)
            ?: SessionDebrief.fromInsights(context.take(1))
            ?: return RunningAnalysisReport(null, effect, dynamics)
        val extras = (candidates + context).filterNot { it in highlights.insights }
        val summary = listOfNotNull(DebriefCopyTemplates.runOpening(activity), highlights.summary).joinToString(" ")
        val debrief = highlights.copy(summary = summary,
            insights = listOf(highlights.insights.first().copy(body = summary)) + highlights.insights.drop(1),
            paragraphs = extras.map { it.body })
        return RunningAnalysisReport(debrief, effect, dynamics)
    }

    private fun terrainInsight(activity: ActivityRecord, stream: RunningMovingStream?): DebriefInsight? {
        val halves = stream?.let { pairedHalves(it, 1200.0) { sample -> sample.altitude?.takeIf(Double::isFinite) } }
        if (halves != null) {
            val (first, last) = halves
            val heightChange = (last.paired - first.paired).roundToInt()
            if (abs(heightChange) >= 20) return DebriefCopyTemplates.elevationChange(heightChange, activity.elevationM)
        }
        return DebriefCopyTemplates.terrain(activity)
    }

    private fun recordedIntervals(detail: ActivityDetail): DebriefInsight? {
        val work = detail.workoutSteps.filter { it.role == "WORK" }.map { it.index }.toSet()
        val laps = detail.laps.filter { it.workoutStepIndex in work }.sortedBy { it.index }
        if (laps.size < 2) return null
        fun pace(lap: ActivityLap): Double? = (lap.pace ?: lap.seconds?.let { seconds ->
            lap.distance?.takeIf { it > 0 }?.let { seconds * 1000 / it }
        })?.takeIf { it in 120.0..900.0 }
        return DebriefCopyTemplates.intervalPaces(pace(laps.first()) ?: return null, pace(laps.last()) ?: return null)
    }

    private data class Window(val speed: Double, val paired: Double)

    private fun pairedHalves(
        stream: RunningMovingStream, minimumSeconds: Double, metric: (ActivitySample) -> Double?
    ): Pair<Window, Window>? {
        if (stream.duration < minimumSeconds) return null
        val middle = stream.duration / 2.0
        fun window(from: Double, to: Double): Window? {
            var measured = 0.0
            var speeds = 0.0
            var values = 0.0
            for (span in stream.spans) {
                val seconds = minOf(to, span.end) - maxOf(from, span.start)
                if (seconds <= 0.0) continue
                val aSpeed = runningSpeed(span.a) ?: continue
                val bSpeed = runningSpeed(span.b) ?: continue
                val a = metric(span.a) ?: continue
                val b = metric(span.b) ?: continue
                measured += seconds
                speeds += (aSpeed + bSpeed) / 2 * seconds
                values += (a + b) / 2 * seconds
            }
            if (measured < (to - from) * 0.75) return null
            return Window(speeds / measured, values / measured)
        }
        return (window(0.0, middle) ?: return null) to (window(middle, stream.duration) ?: return null)
    }

    private fun heartRateInsight(
        stream: RunningMovingStream, zones: ExecutionHrZoneProfile, activity: ActivityRecord
    ): DebriefInsight? {
        // A pulse increase during roughly constant pace is descriptive. Hills
        // are a confound, so use caution language only with flat terrain evidence.
        val (first, last) = pairedHalves(stream, 1_200.0) { verifiedRunningHr(it, zones) } ?: return null
        if (abs(last.speed / first.speed - 1.0) > 0.03) return null
        if (stream.duration >= 2700 && abs(last.paired - first.paired) <= 8.0) {
            return DebriefCopyTemplates.stablePulse(first.paired.roundToInt(), last.paired.roundToInt())
        }
        if (last.paired - first.paired < 5.0 || last.paired / first.paired < 1.05) return null
        val treadmill = activity.type.equals("treadmill", ignoreCase = true)
        val flat = treadmill || stream.spans.filter { span ->
            span.a.altitude?.isFinite() == true && span.b.altitude?.isFinite() == true &&
                abs(span.b.altitude - span.a.altitude) <= span.seconds * .08
        }.sumOf { it.seconds } >= stream.duration * .9 &&
            stream.spans.mapNotNull { it.a.altitude?.takeIf(Double::isFinite) }.let { altitudes ->
                altitudes.isNotEmpty() && altitudes.max() - altitudes.min() <= 15
            }
        return DebriefCopyTemplates.pulse(first.paired.roundToInt(), last.paired.roundToInt(),
            if (flat) DebriefTone.CAUTION else DebriefTone.NEUTRAL, if (flat) 85 else 62)
    }

    private fun zoneInsight(
        stream: RunningMovingStream, zones: ExecutionHrZoneProfile, linked: WorkoutExecutionAnalysis?,
        intent: ActivityWorkoutKind?
    ): DebriefInsight? {
        if (stream.duration < 300.0) return null
        val seconds = DoubleArray(6)
        for (span in stream.spans) {
            val a = verifiedRunningHr(span.a, zones) ?: continue
            val b = verifiedRunningHr(span.b, zones) ?: continue
            // A boundary crossing has no exact timestamp in this interval.
            if (zones.zoneFor(a) == zones.zoneFor(b)) seconds[zones.zoneFor(a)] += span.seconds
        }
        val covered = seconds.sum()
        if (covered < stream.runDuration * .75) return null
        val aerobic = seconds[1] + seconds[2]
        if (intent in setOf(ActivityWorkoutKind.EASY_RUN, ActivityWorkoutKind.LONG_RUN) && aerobic / stream.runDuration > .5)
            return DebriefCopyTemplates.aerobic((100 * aerobic / stream.runDuration).roundToInt(), aerobic)
        val compliance = linked?.hrZoneCompliancePct?.takeIf {
            linked.evidenceTier != ExecutionEvidenceTier.SUMMARY_ONLY
        }
        if (compliance != null) return DebriefCopyTemplates.plannedHeartRate(compliance.roundToInt())
        val dominant = (1..5).maxBy { seconds[it] }
        val fraction = seconds[dominant] / covered
        if (fraction < .5) {
            val easy = (100 * aerobic / stream.runDuration).roundToInt()
            val hard = (100 * (seconds[4] + seconds[5]) / stream.runDuration).roundToInt()
            return DebriefCopyTemplates.mixedEffort(easy, hard)
        }
        return DebriefCopyTemplates.dominantZone(dominant, (seconds[dominant] / stream.runDuration * 100).roundToInt(), seconds[dominant])
    }

    private fun splitInsight(activity: ActivityRecord, detail: ActivityDetail): DebriefInsight? {
        val distance = activity.distanceM?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val all = detail.laps
        if (all.any { it.distance?.let { meters -> meters.isFinite() && meters > 0 } != true } ||
            all.zipWithNext().any { (a, b) -> b.index != a.index + 1 }) return null
        if (abs(all.sumOf { it.distance!! } / distance - 1) > .05) return null
        // A short trailing split is common on long runs. Compare only similar recorded laps.
        val fullLaps = if ((all.lastOrNull()?.distance ?: 1000.0) < 950) all.dropLast(1) else all
        val laps = if (detail.pauseIntervals.isEmpty()) fullLaps else fullLaps.filter { lap ->
            val start = lap.startSeconds?.takeIf { it.isFinite() && it >= 0 } ?: return@filter false
            val elapsed = lap.seconds?.takeIf { it.isFinite() && it > 0 } ?: return@filter false
            detail.pauseIntervals.none { it.startSeconds < start + elapsed && it.endSeconds > start }
        }
        if (laps.size < 4 || laps.sumOf { it.distance!! } < distance * .8) return null
        if (laps.any { (it.distance ?: 0.0) !in 950.0..1050.0 || (it.seconds ?: 0.0) !in 114.0..945.0 }) return null
        val midpoint = laps.size / 2
        fun List<ActivityLap>.pace() = sumOf { it.seconds!! } * 1000.0 / sumOf { it.distance!! }
        val first = laps.take(midpoint).pace()
        val last = laps.takeLast(midpoint).pace()
        val difference = abs(last - first).roundToInt()
        if (difference < 8) return if (laps.sumOf { it.seconds!! } >= 1200)
            DebriefCopyTemplates.steadyPace(first, last) else null
        return DebriefCopyTemplates.splits(first, last, difference, laps.size == all.size && laps.size % 2 == 0)
    }

    private fun changingPace(stream: RunningMovingStream, zones: ExecutionHrZoneProfile?,
                             findings: List<DebriefInsight>): DebriefInsight? {
        val halves = zones?.let { pairedHalves(stream, 1200.0) { sample -> verifiedRunningHr(sample, it) } }
        if (halves != null) {
            val (first, last) = halves
            if (abs(last.speed / first.speed - 1) > .03) return DebriefCopyTemplates.paceAndPulse(
                1000 / first.speed, 1000 / last.speed, first.paired.roundToInt(), last.paired.roundToInt())
        }
        if (findings.any { it.category == DebriefCategory.TERRAIN_AND_SPLITS }) return null
        val (first, last) = pairedHalves(stream, 1200.0, ::runningSpeed) ?: return null
        val early = 1000 / first.speed
        val late = 1000 / last.speed
        return if (abs(early - late) >= 10) DebriefCopyTemplates.splits(early, late, abs(early - late).roundToInt(), true) else null
    }

    private fun steadyPacing(stream: RunningMovingStream): DebriefInsight? {
        val (first, last) = pairedHalves(stream, 1200.0, ::runningSpeed) ?: return null
        val early = 1000 / first.speed
        val late = 1000 / last.speed
        return if (abs(early - late) < 10.0) DebriefCopyTemplates.steadyPace(early, late) else null
    }

    private fun dynamicsInsight(stream: RunningMovingStream): DebriefInsight? {
        val contact = pairedHalves(stream, 2700.0) { it.runningDynamics?.groundContactMs?.takeIf { ms -> ms in 80.0..600.0 } }
        if (contact != null) {
            val (first, last) = contact
            if (abs(last.speed / first.speed - 1) <= .03) {
                val change = last.paired - first.paired
                if (change >= 15 || abs(change) <= 10) {
                    return DebriefCopyTemplates.contactTime(first.paired.roundToInt(), last.paired.roundToInt(), change >= 15)
                }
            }
        }
        val (first, last) = pairedHalves(stream, 2700.0) { it.cadenceSpm?.takeIf { spm -> spm in 100.0..240.0 } }
            ?: return null
        return if (abs(last.speed / first.speed - 1) <= .03 && abs(last.paired - first.paired) <= 3) {
            DebriefCopyTemplates.stableCadence(first.paired.roundToInt(), last.paired.roundToInt())
        } else null
    }

    private fun cadenceInsight(stream: RunningMovingStream): DebriefInsight? {
        val (first, last) = pairedHalves(stream, 600.0) { it.cadenceSpm?.takeIf { cadence -> cadence in 100.0..240.0 } }
            ?: return null
        if (abs(last.speed / first.speed - 1.0) < .05 || abs(last.paired - first.paired) < 4) return null
        if ((last.speed > first.speed) != (last.paired > first.paired)) return null
        return DebriefCopyTemplates.cadence(first.paired.roundToInt(), last.paired.roundToInt(), last.speed > first.speed)
    }

    private fun executionInsight(execution: WorkoutExecutionAnalysis): DebriefInsight? {
        if (execution.verdict == ExecutionVerdict.INSUFFICIENT_TELEMETRY) return null
        if (execution.verdict == ExecutionVerdict.VOLUME_ONLY_MATCH) {
            val planned = execution.plannedKm?.takeIf { it > 0 } ?: return null
            val actual = execution.actualKm?.takeIf { it > 0 } ?: return null
            return DebriefCopyTemplates.distance(actual, planned)
        }
        if (execution.evidenceTier == ExecutionEvidenceTier.SUMMARY_ONLY) return null
        val adherence = execution.workStepPaceAdherencePct?.roundToInt() ?: return null
        return DebriefCopyTemplates.workPace(execution.verdict, adherence)
    }
}
