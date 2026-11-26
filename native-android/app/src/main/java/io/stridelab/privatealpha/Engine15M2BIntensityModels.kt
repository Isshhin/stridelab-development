package io.stridelab.privatealpha

internal sealed interface M2BGradeAdjustment {
    data class Computed(
        val grade: DimensionlessRatio,
        val pathDistance: Meters,
        val observedSpeed: MetersPerSecond,
        val adjustedDistance: Meters,
        val adjustedSpeed: MetersPerSecond
    ) : M2BGradeAdjustment

    enum class Abstained : M2BGradeAdjustment {
        INVALID_SEGMENT,
        OUT_OF_DOMAIN,
        ECCENTRIC_DOMINATED
    }
}

internal class M2BTanakaPrior private constructor(
    val belief: BeliefState<BeatsPerSecond>,
    val residualFloor: BeatsPerSecond?
) {
    companion object {
        fun estimate(ageYears: Int?): M2BTanakaPrior {
            val policy = Engine15M2BPolicyRegistry
            if (ageYears == null || ageYears !in policy.TANAKA_MIN_AGE_YEARS..policy.TANAKA_MAX_AGE_YEARS) {
                return M2BTanakaPrior(BeliefState.Unknown("AGE_MISSING_OR_OUT_OF_DOMAIN"), null)
            }
            return M2BTanakaPrior(
                BeliefState.PriorOnly(BeatsPerSecond.fromBpm(208.0 - 0.7 * ageYears), "MOD-07"),
                BeatsPerSecond.of(policy.TANAKA_RESIDUAL_FLOOR_BPM / 60.0)
            )
        }
    }
}

internal object Engine15M2BIntensityModels {
    /** MOD-02. Grade uses horizontal run. Observed speed uses distance on the incline. */
    fun minetti(horizontalDistance: Meters, riseMeters: Double, duration: Seconds): M2BGradeAdjustment {
        if (horizontalDistance.value <= 0 || !riseMeters.isFinite() || duration.value <= 0) {
            return M2BGradeAdjustment.Abstained.INVALID_SEGMENT
        }
        val policy = Engine15M2BPolicyRegistry
        val grade = riseMeters / horizontalDistance.value
        if (grade !in policy.GAP_MIN_GRADE..policy.GAP_MAX_GRADE) {
            return M2BGradeAdjustment.Abstained.OUT_OF_DOMAIN
        }
        if (grade < policy.GAP_MIN_METABOLIC_GRADE) {
            return M2BGradeAdjustment.Abstained.ECCENTRIC_DOMINATED
        }
        val cost = ((((155.4 * grade - 30.4) * grade - 43.3) * grade + 46.3) * grade + 19.5) *
            grade + 3.6
        val path = Math.hypot(horizontalDistance.value, riseMeters)
        val observed = path / duration.value
        val equivalentDistance = path * (cost / 3.6)
        val equivalentSpeed = equivalentDistance / duration.value
        if (!path.isFinite() || !equivalentDistance.isFinite() ||
            !observed.isFinite() || observed <= 0 || !equivalentSpeed.isFinite() || equivalentSpeed <= 0
        ) {
            return M2BGradeAdjustment.Abstained.INVALID_SEGMENT
        }
        return M2BGradeAdjustment.Computed(
            DimensionlessRatio.of(grade), Meters.of(path), MetersPerSecond.of(observed),
            Meters.of(equivalentDistance), MetersPerSecond.of(equivalentSpeed)
        )
    }

    fun tanaka(ageYears: Int?): M2BTanakaPrior = M2BTanakaPrior.estimate(ageYears)

    /** MOD-08 arithmetic only. The calibrator evaluates evidence before invoking it. */
    fun hrrRatio(
        current: BeatsPerSecond,
        rest: BeatsPerSecond,
        maximum: BeatsPerSecond
    ): DimensionlessRatio? {
        if (rest.value <= 0 || maximum <= rest) return null
        val reserve = (current.value - rest.value) / (maximum.value - rest.value)
        return if (reserve.isFinite()) DimensionlessRatio.of(reserve) else null
    }
}
