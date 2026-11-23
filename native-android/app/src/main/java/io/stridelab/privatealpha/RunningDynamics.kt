package io.stridelab.privatealpha

internal data class RunningDynamics(
    val groundContactMs: Double? = null,
    val verticalOscillationCm: Double? = null,
    val stepLengthM: Double? = null,
    val verticalRatioPct: Double? = null,
    val cadenceSpm: Double? = null
) {
    fun present(): RunningDynamics? = takeIf {
        listOf(groundContactMs, verticalOscillationCm, stepLengthM, verticalRatioPct, cadenceSpm).any { it != null }
    }
}

/** The FIT SDK applies the profile scaling. Oscillation and step length are returned in mm. */
internal fun fitRunningDynamics(
    stanceMs: Number?, oscillationMm: Number?, stepMm: Number?, ratioPct: Number?, cadenceSpm: Double? = null
): RunningDynamics? {
    fun positive(value: Number?) = value?.toDouble()?.takeIf { it.isFinite() && it > 0 }
    return RunningDynamics(positive(stanceMs), positive(oscillationMm)?.div(10),
        positive(stepMm)?.div(1000), positive(ratioPct), positive(cadenceSpm)).present()
}

internal data class RunningDynamicsSummary(
    val values: RunningDynamics,
    val estimatedStepLength: Boolean,
    val estimatedVerticalRatio: Boolean
)

internal fun summarizeRunningDynamics(detail: ActivityDetail, stream: RunningMovingStream?): RunningDynamicsSummary? {
    val saved = detail.runningDynamics ?: RunningDynamics()
    fun cadence(sample: ActivitySample) = sample.cadenceSpm?.takeIf { it in 100.0..250.0 }
    fun stepEstimate(sample: ActivitySample): Double? {
        val speed = runningSpeed(sample) ?: return null
        return cadence(sample)?.let { speed * 60 / it }
    }
    fun mean(value: (ActivitySample) -> Double?): Double? {
        var total = 0.0
        var covered = 0.0
        for (span in stream?.spans.orEmpty()) {
            val a = value(span.a) ?: continue
            val b = value(span.b) ?: continue
            total += (a + b) / 2 * span.seconds
            covered += span.seconds
        }
        return if (covered > 0) total / covered else null
    }
    val recordedLength = saved.stepLengthM ?: mean { it.runningDynamics?.stepLengthM }
    val recordedRatio = saved.verticalRatioPct ?: mean { it.runningDynamics?.verticalRatioPct }
    val ratio = recordedRatio ?: mean { sample ->
        val oscillation = sample.runningDynamics?.verticalOscillationCm ?: return@mean null
        val step = sample.runningDynamics.stepLengthM ?: stepEstimate(sample) ?: return@mean null
        oscillation / step
    } ?: saved.verticalOscillationCm?.let { oscillation -> saved.stepLengthM?.let { oscillation / it } }
    val values = RunningDynamics(
        saved.groundContactMs ?: mean { it.runningDynamics?.groundContactMs },
        saved.verticalOscillationCm ?: mean { it.runningDynamics?.verticalOscillationCm },
        recordedLength ?: mean(::stepEstimate),
        ratio,
        saved.cadenceSpm ?: mean(::cadence)
    ).present() ?: return null
    return RunningDynamicsSummary(values, recordedLength == null && values.stepLengthM != null,
        recordedRatio == null && ratio != null)
}
