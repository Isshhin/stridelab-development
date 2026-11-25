package io.stridelab.privatealpha

import java.util.Collections
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/**
 * Deeply immutable, exhaustive result contract for pure M2-A mathematical models (DEF-08).
 * Constructor collections are defensively copied and wrapped unmodifiable while preserving
 * structural equals() and hashCode().
 */
internal sealed interface M2AModelResult<out T> {

    class Computed<out T>(
        val value: T,
        val modelId: String,
        notes: List<String> = emptyList()
    ) : M2AModelResult<T> {
        val notes: List<String>

        init {
            RecordPolicy.identifier(modelId)
            val cleaned = notes.map { note ->
                RecordPolicy.identifier(note)
                note
            }.distinct().sorted()
            this.notes = Collections.unmodifiableList(ArrayList(cleaned))
        }

        override fun equals(other: Any?): Boolean =
            other is Computed<*> &&
                value == other.value &&
                modelId == other.modelId &&
                notes == other.notes

        override fun hashCode(): Int {
            var r = value?.hashCode() ?: 0
            r = 31 * r + modelId.hashCode()
            r = 31 * r + notes.hashCode()
            return r
        }

        override fun toString(): String = "Computed(modelId=$modelId, value=$value, notes=$notes)"
    }

    data class Abstained(
        val modelId: String,
        val reasonCode: String,
        val detail: String
    ) : M2AModelResult<Nothing> {
        init {
            RecordPolicy.identifier(modelId)
            RecordPolicy.identifier(reasonCode)
        }
    }

    data class InvalidInput(
        val modelId: String,
        val reasonCode: String,
        val detail: String
    ) : M2AModelResult<Nothing> {
        init {
            RecordPolicy.identifier(modelId)
            RecordPolicy.identifier(reasonCode)
        }
    }
}

internal enum class VickersCourseDifficulty {
    AVERAGE,
    DIFFICULT,
    FAST,
    VERY_DIFFICULT_EXCLUDED,
    VERY_FAST_EXCLUDED
}

internal data class SiDurationInterval(
    val min: Seconds,
    val max: Seconds
) {
    init {
        require(min.value <= max.value) {
            "STRUCTURAL_INPUT_INVALID: duration min (${min.value}) > max (${max.value})"
        }
    }
}

internal data class SiDistanceInterval(
    val low: Meters,
    val high: Meters
) {
    init {
        require(low.value <= high.value) {
            "STRUCTURAL_INPUT_INVALID: distance low (${low.value}) > high (${high.value})"
        }
    }
}

internal data class SiSpeedInterval(
    val low: MetersPerSecond,
    val high: MetersPerSecond
) {
    init {
        require(low.value <= high.value) {
            "STRUCTURAL_INPUT_INVALID: speed low (${low.value}) > high (${high.value})"
        }
    }
}

/**
 * MOD-01 — Speed-Pace Reciprocal & Conservative Interval Propagation.
 * Exact kinematic identities over SI base units (m, s, m/s).
 */
internal object Engine15M2ASpeedPaceMath {
    const val MODEL_ID: String = "MOD-01"

    fun speedToPaceSecondsPerKm(speed: MetersPerSecond): Double = speed.toPaceSecondsPerKm()

    fun paceSecondsPerKmToSpeed(paceSecondsPerKm: Double): M2AModelResult<MetersPerSecond> {
        if (!paceSecondsPerKm.isFinite() || paceSecondsPerKm <= 0.0) {
            return M2AModelResult.InvalidInput(
                MODEL_ID,
                "NON_POSITIVE_OR_NON_FINITE_PACE",
                "paceSecondsPerKm must be finite and > 0, was $paceSecondsPerKm"
            )
        }
        val mps = 1000.0 / paceSecondsPerKm
        if (!mps.isFinite() || mps <= 0.0) {
            return M2AModelResult.InvalidInput(
                MODEL_ID,
                "SPEED_OVERFLOW_OR_UNDERFLOW",
                "1000.0 / $paceSecondsPerKm produced non-positive or non-finite speed $mps"
            )
        }
        return M2AModelResult.Computed(MetersPerSecond.of(mps), MODEL_ID)
    }

    fun propagateDurationInterval(
        distance: Meters,
        speedInterval: SiSpeedInterval
    ): M2AModelResult<SiDurationInterval> {
        val minS = distance.value / speedInterval.high.value
        val maxS = distance.value / speedInterval.low.value
        if (!minS.isFinite() || !maxS.isFinite() || minS < 0.0 || maxS < minS) {
            return M2AModelResult.InvalidInput(
                MODEL_ID,
                "DURATION_INTERVAL_OVERFLOW",
                "Duration interval propagation produced non-finite or reversed bounds [$minS, $maxS]"
            )
        }
        return M2AModelResult.Computed(
            SiDurationInterval(Seconds.of(minS), Seconds.of(maxS)),
            MODEL_ID
        )
    }

    fun propagateDistanceInterval(
        duration: Seconds,
        speedInterval: SiSpeedInterval
    ): M2AModelResult<SiDistanceInterval> {
        val lowM = duration.value * speedInterval.low.value
        val highM = duration.value * speedInterval.high.value
        if (!lowM.isFinite() || !highM.isFinite() || lowM < 0.0 || highM < lowM) {
            return M2AModelResult.InvalidInput(
                MODEL_ID,
                "DISTANCE_INTERVAL_OVERFLOW",
                "Distance interval propagation produced non-finite or reversed bounds [$lowM, $highM]"
            )
        }
        return M2AModelResult.Computed(
            SiDistanceInterval(Meters.of(lowM), Meters.of(highM)),
            MODEL_ID
        )
    }
}

internal data class RiegelEquivalenceOutput(
    val sourceDistance: Meters,
    val sourceDuration: Seconds,
    val targetDistance: Meters,
    val predictedDuration: Seconds,
    val predictedSpeed: MetersPerSecond,
    val exponentUsed: DimensionlessRatio
) {
    init {
        val implied = targetDistance.value / predictedDuration.value
        require(abs(implied - predictedSpeed.value) <= Engine15M2APolicyRegistry.KINEMATIC_IDENTITY_TOLERANCE_MPS) {
            "DEF-07 kinematic identity violated: $implied != ${predictedSpeed.value}"
        }
    }
}

/**
 * MOD-03 — Adjacent-Distance Riegel Equivalence (k = 1.07, POL-017).
 * Strictly bounded to [3,000 m, 21,097.5 m] and distance ratio <= 4.25.
 * Prohibited for Marathon (42,195 m) prediction.
 */
internal object Engine15M2ARiegelModel {
    const val MODEL_ID: String = "MOD-03"

    fun predictAdjacent(
        sourceDistance: Meters,
        sourceDuration: Seconds,
        targetDistance: Meters,
        exponent: Double = Engine15M2APolicyRegistry.POL_017_RIEGEL_EXPONENT
    ): M2AModelResult<RiegelEquivalenceOutput> {
        if (sourceDistance.value <= 0.0 || sourceDuration.value <= 0.0 || targetDistance.value <= 0.0) {
            return M2AModelResult.InvalidInput(
                MODEL_ID,
                "NON_POSITIVE_RACE_INPUT",
                "sourceDistance, sourceDuration, and targetDistance must be strictly positive"
            )
        }
        if (!exponent.isFinite() || exponent <= 1.0) {
            return M2AModelResult.InvalidInput(
                MODEL_ID,
                "INVALID_RIEGEL_EXPONENT",
                "Riegel exponent must be finite and > 1.0, was $exponent"
            )
        }
        if (exponent != Engine15M2APolicyRegistry.POL_017_RIEGEL_EXPONENT) {
            return M2AModelResult.Abstained(
                MODEL_ID,
                "NON_POLICY_RIEGEL_EXPONENT",
                "Tier A POL-017 authorizes only exponent ${Engine15M2APolicyRegistry.POL_017_RIEGEL_EXPONENT}, received $exponent"
            )
        }
        val sourceSpeedMps = sourceDistance.value / sourceDuration.value
        if (!sourceSpeedMps.isFinite() ||
            sourceSpeedMps < Engine15M2APolicyRegistry.MIN_PLAUSIBLE_RUNNING_SPEED_MPS ||
            sourceSpeedMps > Engine15M2APolicyRegistry.MAX_PLAUSIBLE_RUNNING_SPEED_MPS
        ) {
            return M2AModelResult.InvalidInput(
                MODEL_ID,
                "IMPLAUSIBLE_SOURCE_SPEED",
                "Source speed $sourceSpeedMps m/s outside plausible running bounds"
            )
        }
        if (targetDistance.value > Engine15M2APolicyRegistry.POL_017_MAX_DISTANCE_METERS) {
            return M2AModelResult.Abstained(
                MODEL_ID,
                "PROHIBITED_MARATHON_OR_EXTENDED_DISTANCE",
                "MOD-03 is prohibited above ${Engine15M2APolicyRegistry.POL_017_MAX_DISTANCE_METERS} m; route Marathon to MOD-04/MOD-05"
            )
        }
        if (sourceDistance.value < Engine15M2APolicyRegistry.POL_017_MIN_DISTANCE_METERS ||
            sourceDistance.value > Engine15M2APolicyRegistry.POL_017_MAX_DISTANCE_METERS ||
            targetDistance.value < Engine15M2APolicyRegistry.POL_017_MIN_DISTANCE_METERS
        ) {
            return M2AModelResult.Abstained(
                MODEL_ID,
                "OUT_OF_MOD03_DISTANCE_DOMAIN",
                "Distances must lie in [${Engine15M2APolicyRegistry.POL_017_MIN_DISTANCE_METERS}, ${Engine15M2APolicyRegistry.POL_017_MAX_DISTANCE_METERS}] m"
            )
        }
        val ratio = max(targetDistance.value / sourceDistance.value, sourceDistance.value / targetDistance.value)
        if (!ratio.isFinite() || ratio > Engine15M2APolicyRegistry.POL_017_MAX_DISTANCE_RATIO) {
            return M2AModelResult.Abstained(
                MODEL_ID,
                "EXTRAPOLATION_RATIO_EXCEEDED",
                "Distance ratio $ratio exceeds POL-017 limit ${Engine15M2APolicyRegistry.POL_017_MAX_DISTANCE_RATIO}"
            )
        }

        val scale = (targetDistance.value / sourceDistance.value).pow(exponent)
        val predictedSeconds = sourceDuration.value * scale
        val predictedSpeedMps = targetDistance.value / predictedSeconds
        if (!predictedSeconds.isFinite() || predictedSeconds <= 0.0 ||
            !predictedSpeedMps.isFinite() || predictedSpeedMps <= 0.0
        ) {
            return M2AModelResult.InvalidInput(
                MODEL_ID,
                "NUMERICAL_OVERFLOW",
                "MOD-03 produced non-finite or non-positive prediction"
            )
        }
        return M2AModelResult.Computed(
            value = RiegelEquivalenceOutput(
                sourceDistance = sourceDistance,
                sourceDuration = sourceDuration,
                targetDistance = targetDistance,
                predictedDuration = Seconds.of(predictedSeconds),
                predictedSpeed = MetersPerSecond.of(predictedSpeedMps),
                exponentUsed = DimensionlessRatio.of(exponent)
            ),
            modelId = MODEL_ID
        )
    }
}

/**
 * Dimensionally exact output contract for MOD-04 (Vickers & Vertosick Model 1 — DEF-09).
 * Stores riegelBaseMarathonSpeed strictly as MetersPerSecond (m/s), and weekly volume explicitly
 * as weeklyRunningMetersPerWeek (m/week) with documented model conversion to miles/week.
 */
internal data class VickersModel1Output(
    val predictedMarathonDuration: Seconds,
    val predictedMarathonSpeed: MetersPerSecond,
    val riegelBaseMarathonSpeed: MetersPerSecond,
    val weeklyRunningMetersPerWeek: Meters,
    val modelInputWeeklyMilesPerWeek: Double,
    val courseAdjustmentApplied: Boolean
) {
    init {
        val impliedSpeed = Engine15M2APolicyRegistry.DISTANCE_MARATHON_METERS / predictedMarathonDuration.value
        require(abs(impliedSpeed - predictedMarathonSpeed.value) <= Engine15M2APolicyRegistry.KINEMATIC_IDENTITY_TOLERANCE_MPS) {
            "DEF-07 kinematic identity violated: $impliedSpeed != ${predictedMarathonSpeed.value}"
        }
        require(modelInputWeeklyMilesPerWeek.isFinite() && modelInputWeeklyMilesPerWeek >= 0.0) {
            "modelInputWeeklyMilesPerWeek must be finite and >= 0"
        }
    }
}

/**
 * Dimensionally exact output contract for MOD-05 (Vickers & Vertosick Model 2 — DEF-09).
 * Stores empiricalKBetween and predictedKMarathon strictly as DimensionlessRatio (1),
 * never sharing a slot with a velocity quantity.
 */
internal data class VickersModel2Output(
    val predictedMarathonDuration: Seconds,
    val predictedMarathonSpeed: MetersPerSecond,
    val empiricalKBetween: DimensionlessRatio,
    val predictedKMarathon: DimensionlessRatio,
    val weeklyRunningMetersPerWeek: Meters,
    val modelInputWeeklyMilesPerWeek: Double,
    val courseAdjustmentApplied: Boolean
) {
    init {
        val impliedSpeed = Engine15M2APolicyRegistry.DISTANCE_MARATHON_METERS / predictedMarathonDuration.value
        require(abs(impliedSpeed - predictedMarathonSpeed.value) <= Engine15M2APolicyRegistry.KINEMATIC_IDENTITY_TOLERANCE_MPS) {
            "DEF-07 kinematic identity violated: $impliedSpeed != ${predictedMarathonSpeed.value}"
        }
        require(modelInputWeeklyMilesPerWeek.isFinite() && modelInputWeeklyMilesPerWeek >= 0.0) {
            "modelInputWeeklyMilesPerWeek must be finite and >= 0"
        }
    }
}

internal object Engine15M2AVickersCourseAdjuster {

    fun adjustVelocity(
        distance: Meters,
        duration: Seconds,
        difficulty: VickersCourseDifficulty,
        modelId: String,
        allowUnverifiedSecondaryCourseAdjustments: Boolean =
            Engine15M2APolicyRegistry.VV_COURSE_ADJUSTMENTS_PRIMARY_VERIFIED
    ): M2AModelResult<Pair<MetersPerSecond, Boolean>> {
        if (distance.value <= 0.0 || duration.value <= 0.0) {
            return M2AModelResult.InvalidInput(modelId, "NON_POSITIVE_RACE_INPUT", "Distance and duration must be > 0")
        }
        val rawSpeed = distance.value / duration.value
        if (!rawSpeed.isFinite() ||
            rawSpeed < Engine15M2APolicyRegistry.MIN_PLAUSIBLE_RUNNING_SPEED_MPS ||
            rawSpeed > Engine15M2APolicyRegistry.MAX_PLAUSIBLE_RUNNING_SPEED_MPS
        ) {
            return M2AModelResult.InvalidInput(modelId, "IMPLAUSIBLE_SOURCE_SPEED", "Speed $rawSpeed m/s out of bounds")
        }
        when (difficulty) {
            VickersCourseDifficulty.AVERAGE ->
                return M2AModelResult.Computed(MetersPerSecond.of(rawSpeed) to false, modelId)
            VickersCourseDifficulty.VERY_DIFFICULT_EXCLUDED,
            VickersCourseDifficulty.VERY_FAST_EXCLUDED ->
                return M2AModelResult.Abstained(
                    modelId,
                    "COURSE_DIFFICULTY_EXCLUDED_BY_MODEL",
                    "Vickers & Vertosick (2016) excluded VERY_DIFFICULT and VERY_FAST courses from regression"
                )
            VickersCourseDifficulty.DIFFICULT,
            VickersCourseDifficulty.FAST -> {
                if (!allowUnverifiedSecondaryCourseAdjustments) {
                    return M2AModelResult.Abstained(
                        modelId,
                        "COURSE_ADJUSTMENT_UNVERIFIED_AGAINST_PRIMARY_DOCX",
                        "Non-average course adjustments are disabled until primary Additional File 1 (.docx) coefficients are independently verified"
                    )
                }
                val delta = lookupCourseDeltaMps(distance.value, difficulty)
                    ?: return M2AModelResult.Abstained(
                        modelId,
                        "COURSE_ADJUSTMENT_UNSUPPORTED_FOR_DISTANCE",
                        "No published Vickers & Vertosick course adjustment exists for distance ${distance.value} m"
                    )
                val adjusted = rawSpeed + delta
                if (!adjusted.isFinite() || adjusted <= 0.0) {
                    return M2AModelResult.Abstained(
                        modelId,
                        "ADJUSTED_VELOCITY_NON_POSITIVE",
                        "Course adjustment produced non-positive velocity $adjusted m/s"
                    )
                }
                return M2AModelResult.Computed(MetersPerSecond.of(adjusted) to true, modelId)
            }
        }
    }

    private fun lookupCourseDeltaMps(distanceM: Double, difficulty: VickersCourseDifficulty): Double? {
        fun exactNominal(target: Double): Boolean =
            abs(distanceM - target) <= Engine15M2APolicyRegistry.COURSE_TABLE_EXACT_DISTANCE_TOLERANCE_METERS
        val isDifficult = difficulty == VickersCourseDifficulty.DIFFICULT
        return when {
            exactNominal(Engine15M2APolicyRegistry.DISTANCE_5K_METERS) ->
                if (isDifficult) Engine15M2APolicyRegistry.VV_COURSE_5K_DIFFICULT_MPS else Engine15M2APolicyRegistry.VV_COURSE_5K_FAST_MPS
            exactNominal(Engine15M2APolicyRegistry.DISTANCE_5MI_METERS) ->
                if (isDifficult) Engine15M2APolicyRegistry.VV_COURSE_5MI_DIFFICULT_MPS else Engine15M2APolicyRegistry.VV_COURSE_5MI_FAST_MPS
            exactNominal(Engine15M2APolicyRegistry.DISTANCE_10K_METERS) ->
                if (isDifficult) Engine15M2APolicyRegistry.VV_COURSE_10K_DIFFICULT_MPS else Engine15M2APolicyRegistry.VV_COURSE_10K_FAST_MPS
            exactNominal(Engine15M2APolicyRegistry.DISTANCE_10MI_METERS) ->
                if (isDifficult) Engine15M2APolicyRegistry.VV_COURSE_10MI_DIFFICULT_MPS else Engine15M2APolicyRegistry.VV_COURSE_10MI_FAST_MPS
            exactNominal(Engine15M2APolicyRegistry.DISTANCE_HALF_MARATHON_METERS) ->
                if (isDifficult) Engine15M2APolicyRegistry.VV_COURSE_HM_DIFFICULT_MPS else Engine15M2APolicyRegistry.VV_COURSE_HM_FAST_MPS
            else -> null
        }
    }
}

/**
 * MOD-04 — Vickers & Vertosick (2016) Model 1 (One Prior Non-Marathon Race + Typical Weekly Mileage).
 * Resolves EQ-DEF-01 & DEF-09 with strict SI output typing and unaltered published coefficients.
 */
internal object Engine15M2AVickersModel1 {
    const val MODEL_ID: String = "MOD-04"

    fun predictMarathon(
        raceDistance: Meters,
        raceDuration: Seconds,
        weeklyVolumePerWeek: Meters,
        courseDifficulty: VickersCourseDifficulty = VickersCourseDifficulty.AVERAGE,
        allowUnverifiedSecondaryCourseAdjustments: Boolean =
            Engine15M2APolicyRegistry.VV_COURSE_ADJUSTMENTS_PRIMARY_VERIFIED
    ): M2AModelResult<VickersModel1Output> {
        val adjResult = Engine15M2AVickersCourseAdjuster.adjustVelocity(
            distance = raceDistance,
            duration = raceDuration,
            difficulty = courseDifficulty,
            modelId = MODEL_ID,
            allowUnverifiedSecondaryCourseAdjustments = allowUnverifiedSecondaryCourseAdjustments
        )
        val (adjustedSpeed, courseAdjusted) = when (adjResult) {
            is M2AModelResult.Computed -> adjResult.value
            is M2AModelResult.Abstained -> return adjResult
            is M2AModelResult.InvalidInput -> return adjResult
        }

        if (raceDistance.value < Engine15M2APolicyRegistry.POL_015_VV1_MIN_DISTANCE_METERS ||
            raceDistance.value > Engine15M2APolicyRegistry.POL_015_VV1_MAX_DISTANCE_METERS
        ) {
            return M2AModelResult.Abstained(
                MODEL_ID,
                "OUT_OF_VV1_DISTANCE_DOMAIN",
                "MOD-04 requires input race in [${Engine15M2APolicyRegistry.POL_015_VV1_MIN_DISTANCE_METERS}, ${Engine15M2APolicyRegistry.POL_015_VV1_MAX_DISTANCE_METERS}] m"
            )
        }

        val weeklyMiles = weeklyVolumePerWeek.value / Engine15M2APolicyRegistry.METERS_PER_MILE
        if (!weeklyMiles.isFinite() ||
            weeklyMiles < Engine15M2APolicyRegistry.POL_015_VV1_MIN_WEEKLY_MILES ||
            weeklyMiles > Engine15M2APolicyRegistry.POL_015_VV1_MAX_WEEKLY_MILES
        ) {
            return M2AModelResult.Abstained(
                MODEL_ID,
                "WEEKLY_MILEAGE_OUT_OF_POL015_DOMAIN",
                "Weekly mileage $weeklyMiles mi/wk outside POL-015 domain [${Engine15M2APolicyRegistry.POL_015_VV1_MIN_WEEKLY_MILES}, ${Engine15M2APolicyRegistry.POL_015_VV1_MAX_WEEKLY_MILES}]"
            )
        }

        val adjustedDurationS = raceDistance.value / adjustedSpeed.value
        val riegelMarathonS = adjustedDurationS * (Engine15M2APolicyRegistry.DISTANCE_MARATHON_METERS / raceDistance.value)
            .pow(Engine15M2APolicyRegistry.POL_017_RIEGEL_EXPONENT)
        val vRiegelMps = Engine15M2APolicyRegistry.DISTANCE_MARATHON_METERS / riegelMarathonS

        val vMarathonMps = Engine15M2APolicyRegistry.VV1_INTERCEPT_MPS +
            Engine15M2APolicyRegistry.VV1_RIEGEL_VELOCITY_COEFF * vRiegelMps +
            Engine15M2APolicyRegistry.VV1_MILEAGE_COEFF_PER_10MI * (weeklyMiles / 10.0)

        if (!vMarathonMps.isFinite() || vMarathonMps <= 0.0) {
            return M2AModelResult.Abstained(
                MODEL_ID,
                "NON_POSITIVE_MARATHON_VELOCITY",
                "MOD-04 produced non-positive marathon speed $vMarathonMps m/s"
            )
        }
        if (vMarathonMps > Engine15M2APolicyRegistry.POL_015_VV1_MAX_SPEED_RATIO * adjustedSpeed.value) {
            return M2AModelResult.Abstained(
                MODEL_ID,
                "VELOCITY_INVERSION_GUARD_TRIGGERED",
                "MOD-04 marathon speed $vMarathonMps m/s exceeds ${Engine15M2APolicyRegistry.POL_015_VV1_MAX_SPEED_RATIO}x input speed ${adjustedSpeed.value} m/s (EQ-DEF-01)"
            )
        }
        val tMarathonS = Engine15M2APolicyRegistry.DISTANCE_MARATHON_METERS / vMarathonMps
        if (!tMarathonS.isFinite() || tMarathonS <= 0.0 || !vRiegelMps.isFinite() || vRiegelMps <= 0.0) {
            return M2AModelResult.InvalidInput(MODEL_ID, "NUMERICAL_OVERFLOW", "Non-finite marathon duration or Riegel velocity")
        }

        return M2AModelResult.Computed(
            value = VickersModel1Output(
                predictedMarathonDuration = Seconds.of(tMarathonS),
                predictedMarathonSpeed = MetersPerSecond.of(vMarathonMps),
                riegelBaseMarathonSpeed = MetersPerSecond.of(vRiegelMps),
                weeklyRunningMetersPerWeek = weeklyVolumePerWeek,
                modelInputWeeklyMilesPerWeek = weeklyMiles,
                courseAdjustmentApplied = courseAdjusted
            ),
            modelId = MODEL_ID,
            notes = listOf("VICKERS_VERTOSICK_2016_MODEL_1_WIDE_INDIVIDUAL_RESIDUAL_SCATTER")
        )
    }
}

/**
 * MOD-05 — Vickers & Vertosick (2016) Model 2 (Two Prior Non-Marathon Races + Typical Weekly Mileage).
 * Resolves EQ-DEF-02 & DEF-09 with strictly typed dimensionless exponents and unaltered published coefficients.
 */
internal object Engine15M2AVickersModel2 {
    const val MODEL_ID: String = "MOD-05"

    fun predictMarathon(
        shorterDistance: Meters,
        shorterDuration: Seconds,
        longerDistance: Meters,
        longerDuration: Seconds,
        weeklyVolumePerWeek: Meters,
        shorterCourseDifficulty: VickersCourseDifficulty = VickersCourseDifficulty.AVERAGE,
        longerCourseDifficulty: VickersCourseDifficulty = VickersCourseDifficulty.AVERAGE,
        allowUnverifiedSecondaryCourseAdjustments: Boolean =
            Engine15M2APolicyRegistry.VV_COURSE_ADJUSTMENTS_PRIMARY_VERIFIED
    ): M2AModelResult<VickersModel2Output> {
        if (shorterDistance.value <= 0.0 || longerDistance.value <= 0.0 ||
            shorterDuration.value <= 0.0 || longerDuration.value <= 0.0
        ) {
            return M2AModelResult.InvalidInput(MODEL_ID, "NON_POSITIVE_RACE_INPUT", "Distances and durations must be > 0")
        }
        if (longerDistance.value <= shorterDistance.value) {
            return M2AModelResult.InvalidInput(
                MODEL_ID,
                "NON_INCREASING_RACE_DISTANCES",
                "MOD-05 requires shorterDistance (${shorterDistance.value}) < longerDistance (${longerDistance.value})"
            )
        }

        val adj1 = Engine15M2AVickersCourseAdjuster.adjustVelocity(
            shorterDistance, shorterDuration, shorterCourseDifficulty, MODEL_ID, allowUnverifiedSecondaryCourseAdjustments
        )
        val (v1Adj, c1) = when (adj1) {
            is M2AModelResult.Computed -> adj1.value
            is M2AModelResult.Abstained -> return adj1
            is M2AModelResult.InvalidInput -> return adj1
        }
        val adj2 = Engine15M2AVickersCourseAdjuster.adjustVelocity(
            longerDistance, longerDuration, longerCourseDifficulty, MODEL_ID, allowUnverifiedSecondaryCourseAdjustments
        )
        val (v2Adj, c2) = when (adj2) {
            is M2AModelResult.Computed -> adj2.value
            is M2AModelResult.Abstained -> return adj2
            is M2AModelResult.InvalidInput -> return adj2
        }

        if (shorterDistance.value < Engine15M2APolicyRegistry.POL_015_VV1_MIN_DISTANCE_METERS ||
            longerDistance.value > Engine15M2APolicyRegistry.POL_015_VV1_MAX_DISTANCE_METERS
        ) {
            return M2AModelResult.Abstained(
                MODEL_ID,
                "OUT_OF_VV2_DISTANCE_DOMAIN",
                "Both races must lie in [${Engine15M2APolicyRegistry.POL_015_VV1_MIN_DISTANCE_METERS}, ${Engine15M2APolicyRegistry.POL_015_VV1_MAX_DISTANCE_METERS}] m"
            )
        }
        val distRatio = longerDistance.value / shorterDistance.value
        if (distRatio < Engine15M2APolicyRegistry.POL_016_VV2_MIN_DISTANCE_RATIO) {
            return M2AModelResult.Abstained(
                MODEL_ID,
                "DISTANCE_RATIO_BELOW_POL016_MINIMUM",
                "D2/D1 = $distRatio is below POL-016 minimum ${Engine15M2APolicyRegistry.POL_016_VV2_MIN_DISTANCE_RATIO}"
            )
        }

        val weeklyMiles = weeklyVolumePerWeek.value / Engine15M2APolicyRegistry.METERS_PER_MILE
        if (!weeklyMiles.isFinite() ||
            weeklyMiles < Engine15M2APolicyRegistry.POL_015_VV1_MIN_WEEKLY_MILES ||
            weeklyMiles > Engine15M2APolicyRegistry.POL_015_VV1_MAX_WEEKLY_MILES
        ) {
            return M2AModelResult.Abstained(
                MODEL_ID,
                "WEEKLY_MILEAGE_OUT_OF_POL015_DOMAIN",
                "Weekly mileage $weeklyMiles mi/wk outside POL-015 domain"
            )
        }

        val t1Adj = shorterDistance.value / v1Adj.value
        val t2Adj = longerDistance.value / v2Adj.value
        val lnDistRatio = ln(distRatio)
        if (!lnDistRatio.isFinite() || lnDistRatio <= 0.0) {
            return M2AModelResult.InvalidInput(MODEL_ID, "ZERO_LOG_DISTANCE_DENOMINATOR", "ln(D2/D1) must be > 0")
        }
        val kBetween = ln(t2Adj / t1Adj) / lnDistRatio
        if (!kBetween.isFinite() ||
            kBetween < Engine15M2APolicyRegistry.POL_016_VV2_MIN_K_BETWEEN ||
            kBetween > Engine15M2APolicyRegistry.POL_016_VV2_MAX_K_BETWEEN
        ) {
            return M2AModelResult.Abstained(
                MODEL_ID,
                "K_BETWEEN_OUT_OF_POL016_RANGE",
                "Empirical k_between = $kBetween outside POL-016 [${Engine15M2APolicyRegistry.POL_016_VV2_MIN_K_BETWEEN}, ${Engine15M2APolicyRegistry.POL_016_VV2_MAX_K_BETWEEN}] (EQ-DEF-02)"
            )
        }

        val kMarathon = Engine15M2APolicyRegistry.VV2_INTERCEPT +
            Engine15M2APolicyRegistry.VV2_K_BETWEEN_COEFF * kBetween +
            Engine15M2APolicyRegistry.VV2_MILEAGE_COEFF_PER_10MI * (weeklyMiles / 10.0)
        if (!kMarathon.isFinite() || kMarathon < Engine15M2APolicyRegistry.POL_016_VV2_MIN_K_MARATHON) {
            return M2AModelResult.Abstained(
                MODEL_ID,
                "K_MARATHON_INVERSION_GUARD",
                "Predicted k_marathon = $kMarathon below POL-016 minimum ${Engine15M2APolicyRegistry.POL_016_VV2_MIN_K_MARATHON} (EQ-DEF-02)"
            )
        }

        val tMarathonS = t2Adj * (Engine15M2APolicyRegistry.DISTANCE_MARATHON_METERS / longerDistance.value).pow(kMarathon)
        val vMarathonMps = Engine15M2APolicyRegistry.DISTANCE_MARATHON_METERS / tMarathonS
        if (!tMarathonS.isFinite() || tMarathonS <= 0.0 ||
            !vMarathonMps.isFinite() || vMarathonMps <= 0.0 ||
            vMarathonMps >= v2Adj.value
        ) {
            return M2AModelResult.Abstained(
                MODEL_ID,
                "MARATHON_VELOCITY_PLAUSIBILITY_FAILED",
                "MOD-05 produced non-plausible marathon velocity $vMarathonMps m/s vs longer race ${v2Adj.value} m/s"
            )
        }

        return M2AModelResult.Computed(
            value = VickersModel2Output(
                predictedMarathonDuration = Seconds.of(tMarathonS),
                predictedMarathonSpeed = MetersPerSecond.of(vMarathonMps),
                empiricalKBetween = DimensionlessRatio.of(kBetween),
                predictedKMarathon = DimensionlessRatio.of(kMarathon),
                weeklyRunningMetersPerWeek = weeklyVolumePerWeek,
                modelInputWeeklyMilesPerWeek = weeklyMiles,
                courseAdjustmentApplied = c1 || c2
            ),
            modelId = MODEL_ID,
            notes = listOf("VICKERS_VERTOSICK_2016_MODEL_2_WIDE_INDIVIDUAL_RESIDUAL_SCATTER")
        )
    }
}

internal data class OperationalThresholdOutput(
    val operationalThresholdSpeed: MetersPerSecond,
    val operationalThresholdPaceSecPerKm: Double,
    val referenceDuration: Seconds,
    val sourceDistance: Meters,
    val sourceDuration: Seconds,
    val isMeasuredPhysiologicalThreshold: Boolean = false
) {
    init {
        require(!isMeasuredPhysiologicalThreshold) {
            "IR-18 / INV-05 violation: MOD-06 operational threshold MUST NEVER claim to be measured LT2, MLSS, or Critical Speed"
        }
    }
}

/**
 * MOD-06 — Duration-Anchored Operational Threshold Reference (POL-020).
 * Operational coaching reference ONLY (45-60 min sustainable pacing anchor, default 54 min = 3,240 s).
 * Explicitly NOT a measurement of LT2, MLSS, or Critical Speed.
 */
internal object Engine15M2AOperationalThresholdModel {
    const val MODEL_ID: String = "MOD-06"

    fun estimate(
        raceDistance: Meters,
        raceDuration: Seconds,
        referenceDuration: Seconds = Seconds.of(Engine15M2APolicyRegistry.POL_020_OPERATIONAL_THRESHOLD_REF_SECONDS)
    ): M2AModelResult<OperationalThresholdOutput> {
        if (raceDistance.value <= 0.0 || raceDuration.value <= 0.0 || referenceDuration.value <= 0.0) {
            return M2AModelResult.InvalidInput(MODEL_ID, "NON_POSITIVE_INPUT", "Distance, duration, and referenceDuration must be > 0")
        }
        if (referenceDuration.value !in
            Engine15M2APolicyRegistry.POL_020_MIN_REFERENCE_DURATION_SECONDS..Engine15M2APolicyRegistry.POL_020_MAX_REFERENCE_DURATION_SECONDS
        ) {
            return M2AModelResult.InvalidInput(
                MODEL_ID,
                "REFERENCE_DURATION_OUT_OF_45_60_MIN_WINDOW",
                "Operational threshold referenceDuration must be in [2700, 3600] s, was ${referenceDuration.value} s"
            )
        }
        val sourceSpeedMps = raceDistance.value / raceDuration.value
        if (!sourceSpeedMps.isFinite() ||
            sourceSpeedMps < Engine15M2APolicyRegistry.MIN_PLAUSIBLE_RUNNING_SPEED_MPS ||
            sourceSpeedMps > Engine15M2APolicyRegistry.MAX_PLAUSIBLE_RUNNING_SPEED_MPS
        ) {
            return M2AModelResult.InvalidInput(MODEL_ID, "IMPLAUSIBLE_SOURCE_SPEED", "Source speed $sourceSpeedMps m/s out of bounds")
        }
        if (raceDuration.value < Engine15M2APolicyRegistry.POL_020_MIN_BENCHMARK_DURATION_SECONDS ||
            raceDuration.value > Engine15M2APolicyRegistry.POL_020_MAX_BENCHMARK_DURATION_SECONDS
        ) {
            return M2AModelResult.Abstained(
                MODEL_ID,
                "BENCHMARK_DURATION_OUT_OF_POL020_DOMAIN",
                "Benchmark duration ${raceDuration.value} s outside POL-020 [${Engine15M2APolicyRegistry.POL_020_MIN_BENCHMARK_DURATION_SECONDS}, ${Engine15M2APolicyRegistry.POL_020_MAX_BENCHMARK_DURATION_SECONDS}] s"
            )
        }

        val scaleFactor = (referenceDuration.value / raceDuration.value)
            .pow(-Engine15M2APolicyRegistry.MOD_06_GAMMA_FROM_POL_017)
        val thrSpeedMps = sourceSpeedMps * scaleFactor
        if (!thrSpeedMps.isFinite() || thrSpeedMps <= 0.0) {
            return M2AModelResult.InvalidInput(MODEL_ID, "NUMERICAL_OVERFLOW", "Non-finite operational threshold speed")
        }
        val thrSpeed = MetersPerSecond.of(thrSpeedMps)
        return M2AModelResult.Computed(
            value = OperationalThresholdOutput(
                operationalThresholdSpeed = thrSpeed,
                operationalThresholdPaceSecPerKm = thrSpeed.toPaceSecondsPerKm(),
                referenceDuration = referenceDuration,
                sourceDistance = raceDistance,
                sourceDuration = raceDuration,
                isMeasuredPhysiologicalThreshold = false
            ),
            modelId = MODEL_ID,
            notes = listOf("OPERATIONAL_COACHING_REFERENCE_NOT_MEASURED_LT2_MLSS_OR_CS")
        )
    }
}
