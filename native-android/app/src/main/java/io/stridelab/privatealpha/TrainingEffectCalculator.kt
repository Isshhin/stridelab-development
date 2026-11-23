package io.stridelab.privatealpha

import kotlin.math.abs
import kotlin.math.ln1p
import kotlin.math.roundToInt

internal data class RunningMovingSpan(
    val start: Double, val end: Double, val a: ActivitySample, val b: ActivitySample
) {
    val seconds get() = end - start
}

internal data class RunningMovingStream(
    val duration: Double, val spans: List<RunningMovingSpan>, val runDuration: Double = duration
)

internal fun runningMovingStream(detail: ActivityDetail, expectedMovingSeconds: Double? = null): RunningMovingStream? {
    val samples = detail.samples.filter { it.seconds.isFinite() && it.seconds >= 0.0 }
    if (samples.size < 2 || samples.zipWithNext().any { (a, b) -> b.seconds < a.seconds }) return null
    val axis = ActivityChartMovingTimeAxis.from(samples, detail.pauseIntervals)
    val expected = expectedMovingSeconds?.takeIf { it.isFinite() && it > 0 }
    if (expected != null && axis.durationSeconds < expected * .75) return null
    return RunningMovingStream(axis.durationSeconds, samples.zipWithNext().mapNotNull { (a, b) ->
        val elapsed = b.seconds - a.seconds
        if (elapsed !in .001..30.0 || axis.isPaused(a.seconds) || axis.isPaused(b.seconds)) return@mapNotNull null
        val start = axis.position(a.seconds)
        val end = axis.position(b.seconds)
        if (abs(end - start - elapsed) > .000001) null else RunningMovingSpan(start, end, a, b)
    }, maxOf(expected ?: 0.0, axis.durationSeconds))
}

internal fun runningSpeed(sample: ActivitySample): Double? =
    sample.pace?.takeIf { it in 120.0..900.0 }?.let { 1000.0 / it }

internal fun verifiedRunningZones(detail: ActivityDetail?, fallback: ExecutionHrZoneProfile?): ExecutionHrZoneProfile? {
    val source = detail?.sourceHeartRateZones?.takeIf { (it.highBpms.firstOrNull() ?: 0) >= 45 }
    return ExecutionHrZoneProfile.fromActivitySourceZones(source) ?: fallback
}

internal fun verifiedRunningHr(sample: ActivitySample, zones: ExecutionHrZoneProfile): Double? =
    sample.hr?.takeIf { it in zones.z1FloorBpm.toDouble()..(zones.maxHrBpm ?: 240).toDouble() }

internal data class TrainingEffect(val aerobic: Double?, val anaerobic: Double?)

/** StrideLab estimates from source measurements. Scores do not modify a plan or diagnose recovery. */
internal object TrainingEffectCalculator {
    private val aerobicWeights = doubleArrayOf(1.0, 2.0, 3.0, 5.0, 7.0)

    fun calculate(activity: ActivityRecord, detail: ActivityDetail?, fallback: ExecutionHrZoneProfile? = null): TrainingEffect {
        if (detail?.activityId != activity.id || !DashboardSummary.isRun(activity)) return TrainingEffect(null, null)
        return calculate(runningMovingStream(detail, activity.movingTimeS), verifiedRunningZones(detail, fallback))
    }

    fun calculate(stream: RunningMovingStream?, zones: ExecutionHrZoneProfile?): TrainingEffect {
        if (stream == null || stream.duration < 300) return TrainingEffect(null, null)
        val coveredMinimum = stream.runDuration * .75
        fun RunningMovingSpan.zone(): Int? {
            val profile = zones ?: return null
            val first = verifiedRunningHr(a, profile)?.let(profile::zoneFor) ?: return null
            val last = verifiedRunningHr(b, profile)?.let(profile::zoneFor) ?: return null
            return first.takeIf { it == last }
        }
        fun RunningMovingSpan.speed(): Double? {
            val first = runningSpeed(a) ?: return null
            val last = runningSpeed(b) ?: return null
            return (first + last) / 2
        }
        val zoned = stream.spans.map { it.zone() }
        val speeds = stream.spans.map { it.speed() }
        val coveredHr = stream.spans.indices.sumOf { if (zoned[it] != null) stream.spans[it].seconds else 0.0 }
        val coveredSpeed = stream.spans.indices.sumOf { if (speeds[it] != null) stream.spans[it].seconds else 0.0 }
        val aerobic = if (coveredHr >= coveredMinimum) {
            val weightedMinutes = stream.spans.indices.sumOf { index ->
                val zone = zoned[index] ?: return@sumOf 0.0
                stream.spans[index].seconds * aerobicWeights[zone - 1] / 60
            }
            score(1.9 * ln1p(weightedMinutes / 35))
        } else null
        val baseline = if (coveredSpeed >= coveredMinimum) stream.spans.indices.sumOf {
            (speeds[it] ?: 0.0) * stream.spans[it].seconds
        } / coveredSpeed else null
        if (aerobic == null && baseline == null) return TrainingEffect(null, null)

        // Count distinct short bouts bracketed by measured lower intensity.
        // Long threshold blocks and gaps are excluded from the surge total.
        var armed = false
        var seconds = 0.0
        var hardSeconds = 0.0
        var previousEnd = -1.0
        for ((index, span) in stream.spans.withIndex()) {
            val zone = zones?.takeIf { aerobic != null }?.let { profile ->
                val first = verifiedRunningHr(span.a, profile)
                val last = verifiedRunningHr(span.b, profile)
                if (first != null && last != null) profile.zoneFor((first + last) / 2) else null
            }
            val speed = speeds[index].takeIf { baseline != null }
            if (abs(previousEnd - span.start) > .001 || (zone == null && speed == null)) {
                armed = false
                seconds = 0.0
            }
            val high = (zone != null && zone >= 4) ||
                (speed != null && baseline != null && speed >= baseline * 1.20)
            if (high) {
                if (armed) seconds += span.seconds
            } else if (zone != null || speed != null) {
                if (armed && seconds in 15.0..180.0) hardSeconds += seconds
                seconds = 0.0
                armed = true
            }
            previousEnd = span.end
        }
        return TrainingEffect(aerobic, score(2.2 * ln1p(hardSeconds / 120)))
    }

    private fun score(value: Double) = (value.coerceIn(0.0, 5.0) * 10).roundToInt() / 10.0

    fun label(score: Double): String = when {
        score < 1 -> "No Benefit"
        score < 2 -> "Recovery"
        score < 3 -> "Maintaining"
        score < 4 -> "Improving"
        score < 5 -> "Highly Improving"
        else -> "Overreaching"
    }
}
