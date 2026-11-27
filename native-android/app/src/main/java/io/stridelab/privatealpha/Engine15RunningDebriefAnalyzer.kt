package io.stridelab.privatealpha

import java.time.Duration
import java.time.ZoneId
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
        weather: WeatherContext? = null,
        history: List<ActivityRecord> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault()
    ): SessionDebrief? {
        return report(activity, detail, execution, verifiedHrZones, sessionIntent, history, zone).debrief
    }

    fun report(
        activity: ActivityRecord,
        detail: ActivityDetail?,
        execution: WorkoutExecutionAnalysis?,
        verifiedHrZones: ExecutionHrZoneProfile?,
        sessionIntent: ActivityWorkoutKind?,
        history: List<ActivityRecord> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault()
    ): RunningAnalysisReport {
        if (!DashboardSummary.isRun(activity)) return RunningAnalysisReport(null, TrainingEffect(null, null), null)
        val recording = detail?.takeIf { it.activityId == activity.id }
        val linked = execution?.takeIf { it.activityId == activity.id }
        val zones = verifiedRunningZones(recording, verifiedHrZones)
        val stream = recording?.let { runningMovingStream(it, activity.movingTimeS) }
        val effect = TrainingEffectCalculator.calculate(stream, zones)
        val dynamics = recording?.let { summarizeRunningDynamics(it, stream) }

        val candidates = listOfNotNull(
            executionInsight(linked, recording),
            if (stream != null && zones != null) zoneInsight(stream, zones, linked, sessionIntent) else null,
            pacingAndEffortInsight(activity, recording, stream, zones, linked, sessionIntent),
            mechanicsInsight(stream),
            loadContextInsight(activity, history, zone)
        ).distinctBy { it.category }

        val debrief = SessionDebrief.fromInsights(candidates)
            ?: return RunningAnalysisReport(null, effect, dynamics)
        return RunningAnalysisReport(debrief, effect, dynamics)
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

    private fun isFlatStream(activity: ActivityRecord, stream: RunningMovingStream): Boolean {
        if (activity.type.equals("treadmill", ignoreCase = true)) return true
        val gentleCoverage = stream.spans.filter { span ->
            span.a.altitude?.isFinite() == true && span.b.altitude?.isFinite() == true &&
                abs(span.b.altitude - span.a.altitude) <= span.seconds * .08
        }.sumOf { it.seconds } >= stream.duration * .9
        val altitudes = stream.spans.mapNotNull { it.a.altitude?.takeIf(Double::isFinite) }
        return gentleCoverage && altitudes.isNotEmpty() && altitudes.max() - altitudes.min() <= 15.0
    }

    private fun altitudeDeltaHalves(stream: RunningMovingStream?): Int? {
        val halves = stream?.let { pairedHalves(it, 1200.0) { sample -> sample.altitude?.takeIf(Double::isFinite) } }
            ?: return null
        return (halves.second.paired - halves.first.paired).roundToInt()
    }

    private fun splitHalvesFromLaps(activity: ActivityRecord, detail: ActivityDetail): Triple<Double, Double, Boolean>? {
        val distance = activity.distanceM?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val all = detail.laps
        if (all.any { it.distance?.let { meters -> meters.isFinite() && meters > 0 } != true } ||
            all.zipWithNext().any { (a, b) -> b.index != a.index + 1 }) return null
        if (abs(all.sumOf { it.distance!! } / distance - 1) > .05) return null
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
        return Triple(first, last, laps.size == all.size && laps.size % 2 == 0)
    }

    private fun pacingAndEffortInsight(
        activity: ActivityRecord,
        recording: ActivityDetail?,
        stream: RunningMovingStream?,
        zones: ExecutionHrZoneProfile?,
        linked: WorkoutExecutionAnalysis?,
        intent: ActivityWorkoutKind?
    ): DebriefInsight? {
        val lapHalves = recording?.let { splitHalvesFromLaps(activity, it) }
        val hrHalves = if (stream != null && zones != null) {
            pairedHalves(stream, 1_200.0) { verifiedRunningHr(it, zones) }
        } else null
        val speedHalves = stream?.let { pairedHalves(it, 1_200.0, ::runningSpeed) }
        val climbDelta = altitudeDeltaHalves(stream)
        val decouplingPct = if (stream != null && zones != null) {
            linked?.takeUnless { it.evidenceTier == ExecutionEvidenceTier.SUMMARY_ONLY }?.aerobicDecouplingPct
        } else null

        if (hrHalves != null) {
            val (early, late) = hrHalves
            val earlyHr = early.paired.roundToInt()
            val lateHr = late.paired.roundToInt()
            val hrDiff = late.paired - early.paired
            val earlyPace = lapHalves?.first ?: (1000.0 / early.speed)
            val latePace = lapHalves?.second ?: (1000.0 / late.speed)
            val paceDiffSec = abs(latePace - earlyPace).roundToInt()
            val steadySpeed = abs(late.speed / early.speed - 1.0) <= 0.03 && paceDiffSec < 8

            if (steadySpeed) {
                val flat = stream != null && isFlatStream(activity, stream)
                if (hrDiff >= 5.0 && late.paired / early.paired >= 1.05) {
                    return if (!flat && (climbDelta ?: 0) >= 20) {
                        DebriefCopyTemplates.hillsDroveLateEffort(earlyHr, lateHr, climbDelta ?: 0, activity.elevationM)
                    } else if (flat) {
                        DebriefCopyTemplates.cardiacDriftFlat(earlyHr, lateHr, decouplingPct)
                    } else {
                        DebriefCopyTemplates.hillsDroveLateEffort(earlyHr, lateHr, climbDelta ?: 0, activity.elevationM)
                    }
                }
                if (stream != null && stream.duration >= 2_700.0 && abs(hrDiff) <= 8.0) {
                    return DebriefCopyTemplates.aerobicDurabilitySteady(earlyHr, lateHr, decouplingPct)
                }
            }

            if (paceDiffSec >= 8) {
                val fasterLate = latePace < earlyPace
                val hrRose = lateHr > earlyHr + 4
                val hrDropped = lateHr < earlyHr - 3
                val easyIntent = intent in setOf(ActivityWorkoutKind.EASY_RUN, ActivityWorkoutKind.RECOVERY_RUN)
                return when {
                    fasterLate && !hrRose ->
                        DebriefCopyTemplates.negativeSplitControlled(earlyPace, latePace, paceDiffSec, earlyHr, lateHr)
                    fasterLate ->
                        DebriefCopyTemplates.negativeSplitEarnedEffort(earlyPace, latePace, paceDiffSec, earlyHr, lateHr, easyIntent)
                    !fasterLate && (climbDelta ?: 0) >= 20 ->
                        DebriefCopyTemplates.smartHillPacing(earlyPace, latePace, paceDiffSec, climbDelta ?: 0)
                    !fasterLate && hrDropped ->
                        DebriefCopyTemplates.easedLateControlled(earlyPace, latePace, paceDiffSec, earlyHr, lateHr)
                    else ->
                        DebriefCopyTemplates.wentOutHotFaded(earlyPace, latePace, paceDiffSec, earlyHr, lateHr)
                }
            }
        }

        if (lapHalves != null) {
            val (first, last, fullHalves) = lapHalves
            val difference = abs(last - first).roundToInt()
            if (difference < 8) {
                val totalSeconds = recording.laps.sumOf { it.seconds ?: 0.0 }
                return if (totalSeconds >= 1200.0) DebriefCopyTemplates.steadyPace(first, last) else null
            }
            return if (last > first && (climbDelta ?: 0) >= 20) {
                DebriefCopyTemplates.smartHillPacing(first, last, difference, climbDelta ?: 0)
            } else if (last > first && difference >= 15 && fullHalves) {
                DebriefCopyTemplates.wentOutHotFaded(first, last, difference, null, null)
            } else {
                DebriefCopyTemplates.paceShiftOnly(first, last, difference, fullHalves)
            }
        }

        if (speedHalves != null) {
            val early = 1000.0 / speedHalves.first.speed
            val late = 1000.0 / speedHalves.second.speed
            val diff = abs(early - late).roundToInt()
            return when {
                diff < 10 -> DebriefCopyTemplates.steadyPace(early, late)
                late > early && (climbDelta ?: 0) >= 20 -> DebriefCopyTemplates.smartHillPacing(early, late, diff, climbDelta ?: 0)
                late > early && diff >= 15 -> DebriefCopyTemplates.wentOutHotFaded(early, late, diff, null, null)
                else -> DebriefCopyTemplates.paceShiftOnly(early, late, diff, true)
            }
        }
        return null
    }

    private fun zoneInsight(
        stream: RunningMovingStream,
        zones: ExecutionHrZoneProfile,
        linked: WorkoutExecutionAnalysis?,
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
        val hard = seconds[4] + seconds[5]
        val aboveEasy = seconds[3] + hard
        val easyPct = (100 * aerobic / stream.runDuration).roundToInt()
        val hardPct = (100 * hard / stream.runDuration).roundToInt()
        val aboveEasyPct = (100 * aboveEasy / stream.runDuration).roundToInt()

        if (intent in setOf(ActivityWorkoutKind.EASY_RUN, ActivityWorkoutKind.RECOVERY_RUN)) {
            if (aboveEasy / stream.runDuration >= .35 || hard / stream.runDuration >= .15) {
                return DebriefCopyTemplates.easyDayTooHard(aboveEasyPct, hardPct, intent == ActivityWorkoutKind.RECOVERY_RUN)
            }
            if (aerobic / stream.runDuration > .5) {
                return DebriefCopyTemplates.aerobicControlled(easyPct, aerobic, isLongRun = false)
            }
        }
        if (intent == ActivityWorkoutKind.LONG_RUN && aerobic / stream.runDuration > .5 && hard / stream.runDuration < .20) {
            return DebriefCopyTemplates.aerobicControlled(easyPct, aerobic, isLongRun = true)
        }
        val compliance = linked?.hrZoneCompliancePct?.takeIf {
            linked.evidenceTier != ExecutionEvidenceTier.SUMMARY_ONLY
        }
        if (compliance != null) return DebriefCopyTemplates.plannedHeartRate(compliance.roundToInt())

        val dominant = (1..5).maxBy { seconds[it] }
        val fraction = seconds[dominant] / covered
        if (fraction < .5) {
            return DebriefCopyTemplates.mixedStimulus(easyPct, hardPct)
        }
        return DebriefCopyTemplates.dominantZoneProfile(dominant, (seconds[dominant] / stream.runDuration * 100).roundToInt(), seconds[dominant])
    }

    private fun mechanicsInsight(stream: RunningMovingStream?): DebriefInsight? {
        stream ?: return null
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
        val steadyCadence = pairedHalves(stream, 1200.0) { it.cadenceSpm?.takeIf { spm -> spm in 100.0..240.0 } }
        if (steadyCadence != null) {
            val (first, last) = steadyCadence
            if (abs(last.speed / first.speed - 1) <= .03) {
                val drop = first.paired - last.paired
                if (drop >= 4.0) {
                    return DebriefCopyTemplates.cadenceDropAtSteadyPace(first.paired.roundToInt(), last.paired.roundToInt())
                }
                if (stream.duration >= 2700.0 && abs(drop) <= 3.0) {
                    return DebriefCopyTemplates.stableCadence(first.paired.roundToInt(), last.paired.roundToInt())
                }
            }
        }
        val shiftedCadence = pairedHalves(stream, 600.0) { it.cadenceSpm?.takeIf { cadence -> cadence in 100.0..240.0 } }
            ?: return null
        if (abs(shiftedCadence.second.speed / shiftedCadence.first.speed - 1.0) < .05 ||
            abs(shiftedCadence.second.paired - shiftedCadence.first.paired) < 4) return null
        if ((shiftedCadence.second.speed > shiftedCadence.first.speed) !=
            (shiftedCadence.second.paired > shiftedCadence.first.paired)) return null
        return DebriefCopyTemplates.cadence(
            shiftedCadence.first.paired.roundToInt(),
            shiftedCadence.second.paired.roundToInt(),
            shiftedCadence.second.speed > shiftedCadence.first.speed
        )
    }

    private fun executionInsight(execution: WorkoutExecutionAnalysis?, detail: ActivityDetail?): DebriefInsight? {
        if (execution != null && execution.verdict != ExecutionVerdict.INSUFFICIENT_TELEMETRY) {
            if (execution.verdict == ExecutionVerdict.VOLUME_ONLY_MATCH) {
                val planned = execution.plannedKm?.takeIf { it > 0 } ?: return null
                val actual = execution.actualKm?.takeIf { it > 0 } ?: return null
                return DebriefCopyTemplates.distance(actual, planned)
            }
            if (execution.evidenceTier != ExecutionEvidenceTier.SUMMARY_ONLY) {
                val adherence = execution.workStepPaceAdherencePct?.roundToInt()
                if (adherence != null) return DebriefCopyTemplates.workPace(execution.verdict, adherence)
            }
        }
        val workPaces = workRepPaces(execution, detail) ?: return null
        return classifyIntervalShape(workPaces)
    }

    private fun workRepPaces(execution: WorkoutExecutionAnalysis?, detail: ActivityDetail?): List<Double>? {
        val fromExecution = execution?.takeIf { it.evidenceTier != ExecutionEvidenceTier.SUMMARY_ONLY }
            ?.stepBreakdowns?.filter { it.purpose == "WORK" }?.sortedBy { it.stepIndex }
            ?.mapNotNull { it.actualPaceSecPerKm?.takeIf { pace -> pace in 120.0..900.0 } }
        if (fromExecution != null && fromExecution.size >= 2) return fromExecution

        detail ?: return null
        val work = detail.workoutSteps.filter { it.role == "WORK" }.map { it.index }.toSet()
        if (work.isEmpty()) return null
        val laps = detail.laps.filter { lap ->
            lap.workoutStepIndex in work && (detail.pauseIntervals.isEmpty() || run {
                val start = lap.startSeconds?.takeIf { it.isFinite() && it >= 0 } ?: return@run false
                val elapsed = lap.seconds?.takeIf { it.isFinite() && it > 0 } ?: return@run false
                detail.pauseIntervals.none { it.startSeconds < start + elapsed && it.endSeconds > start }
            })
        }.sortedBy { it.index }
        val paces = laps.mapNotNull { lap ->
            (lap.pace ?: lap.seconds?.let { seconds ->
                lap.distance?.takeIf { it > 0 }?.let { seconds * 1000 / it }
            })?.takeIf { it in 120.0..900.0 }
        }
        return paces.takeIf { it.size >= 2 }
    }

    private fun classifyIntervalShape(paces: List<Double>): DebriefInsight {
        val first = paces.first()
        val last = paces.last()
        val fastest = paces.min()
        val slowest = paces.max()
        val avg = paces.average()
        val spreadRatio = (slowest - fastest) / avg
        val pattern = when {
            last <= first * 0.97 && last <= avg -> IntervalPattern.NEGATIVE_SPLIT
            last >= first * 1.05 && last > avg -> IntervalPattern.FRONT_LOADED_FADE
            spreadRatio <= 0.04 -> IntervalPattern.CONSISTENT
            else -> IntervalPattern.VARIABLE
        }
        return DebriefCopyTemplates.intervalShape(paces.size, first, last, fastest, slowest, pattern)
    }

    private fun loadContextInsight(
        activity: ActivityRecord,
        history: List<ActivityRecord>,
        zone: ZoneId
    ): DebriefInsight? {
        if (history.isEmpty()) return null
        val currentKm = activity.distanceM?.takeIf { it.isFinite() && it > 0.0 }?.div(1000.0)
        if (currentKm != null && currentKm >= 5.0) {
            val windowStart = activity.startedAt.minus(Duration.ofDays(30))
            val priorDistancesKm = history.asSequence()
                .distinctBy { it.id }
                .filter {
                    it.id != activity.id && DashboardSummary.isRun(it) &&
                        it.startedAt.isBefore(activity.startedAt) && !it.startedAt.isBefore(windowStart)
                }
                .mapNotNull { it.distanceM?.takeIf { m -> m.isFinite() && m >= 500.0 }?.div(1000.0) }
                .toList()
            val priorLongest = priorDistancesKm.maxOrNull()
            if (priorDistancesKm.size >= 2 && priorLongest != null &&
                currentKm >= priorLongest * 1.15 && currentKm - priorLongest >= 2.0
            ) {
                val jumpPct = ((currentKm / priorLongest - 1.0) * 100.0).roundToInt()
                return DebriefCopyTemplates.distanceJump(currentKm, priorLongest, jumpPct)
            }
        }
        val day = activity.startedAt.atZone(zone).toLocalDate()
        val earlierToday = history.asSequence()
            .distinctBy { it.id }
            .filter {
                it.id != activity.id && DashboardSummary.isRun(it) &&
                    it.startedAt.isBefore(activity.startedAt) &&
                    it.startedAt.atZone(zone).toLocalDate() == day
            }
            .toList()
        if (earlierToday.isNotEmpty()) {
            val totalKm = (earlierToday + activity).map { it.distanceM?.takeIf { m -> m.isFinite() && m >= 0 } }
                .takeIf { list -> list.all { it != null } }
                ?.filterNotNull()?.sum()?.div(1000.0)
            return DebriefCopyTemplates.doubleRunDay(earlierToday.size + 1, totalKm)
        }
        return null
    }
}
