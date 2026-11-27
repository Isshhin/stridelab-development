package io.stridelab.privatealpha

import java.time.Instant
import java.time.ZoneOffset

import kotlin.math.roundToInt

internal enum class Engine15WorkoutArchetype(
    val id: String,
    val displayTitle: String,
    val family: IndependentQualityFamily?
) {
    RECOVERY_RUN("recovery_run", "Recovery run", null),
    EASY_AEROBIC_RUN("easy_aerobic_run", "Easy Aerobic Run", null),
    EASY_RUN_WITH_STRIDES("easy_run_with_strides", "Easy Run + Strides", null),
    AEROBIC_FARTLEK("aerobic_fartlek", "Aerobic Fartlek", IndependentQualityFamily.FARTLEK),
    SHORT_HILL_SPRINTS("short_hill_sprints", "Short Hill Sprints", IndependentQualityFamily.HILLS),
    LONG_AEROBIC_HILLS("long_aerobic_hills", "Long Aerobic Hills", IndependentQualityFamily.HILLS),
    CONTINUOUS_TEMPO("continuous_tempo", "Continuous Tempo", IndependentQualityFamily.TEMPO),
    BROKEN_TEMPO("broken_tempo", "Broken Tempo", IndependentQualityFamily.TEMPO),
    THRESHOLD_CRUISE_INTERVALS("threshold_cruise_intervals", "Threshold Cruise Intervals", IndependentQualityFamily.THRESHOLD),
    SHORT_TRACK_REPEATS("short_track_repeats", "Short Track Repeats", IndependentQualityFamily.INTERVALS),
    CLASSIC_VO2MAX_INTERVALS("classic_vo2max_intervals", "Classic VO2max Intervals", IndependentQualityFamily.INTERVALS),
    SPEED_ENDURANCE_PYRAMID("speed_endurance_pyramid", "Speed-Endurance Pyramid", IndependentQualityFamily.INTERVALS),
    STEADY_AEROBIC_LONG_RUN("steady_aerobic_long_run", "Steady Aerobic Long Run", null),
    FAST_FINISH_PROGRESSIVE_LONG_RUN("fast_finish_progressive_long_run", "Fast-Finish Long Run", null);

    companion object {
        fun fromId(id: String?): Engine15WorkoutArchetype? = entries.firstOrNull { it.id == id }
    }
}

internal enum class Engine15QualityProgressionBasis {
    HOLD_NO_REPEATED_EXPOSURE,
    EVIDENCE_BOUNDED_PROGRESSION,
    RACE_TAPER_REDUCTION,
    TIME_BUDGET_CAPPED
}

internal data class Engine15QualityDoseStep(
    val stepIndex: Int,
    val stageLabel: String,
    val workout: IndependentWorkoutComposition,
    val workSeconds: Int,
    val recoverySeconds: Int,
    val totalSeconds: Int,
    val archetype: Engine15WorkoutArchetype? = null,
    val workMeters: Int = 0,
    val recoveryMeters: Int = 0
) {
    init {
        require(stepIndex >= 0 && stageLabel.isNotBlank())
        require(workSeconds > 0 && recoverySeconds >= 0 && workMeters >= 0 && recoveryMeters >= 0)
        require(totalSeconds >= workSeconds + recoverySeconds)
        if (workMeters == 0 && recoveryMeters == 0) {
            require(workout.total(WorkoutUnit.SECONDS)?.toInt() == totalSeconds)
            require(workout.dose(WorkoutUnit.SECONDS, WorkoutPurpose.WORK)?.toInt() == workSeconds)
        }
    }
}

internal data class Engine15QualityWeekPrescription(
    val policyId: String,
    val weekNumber: Int,
    val family: IndependentQualityFamily,
    val step: Engine15QualityDoseStep,
    val basis: Engine15QualityProgressionBasis,
    val ruleIds: List<String>,
    val rationale: String,
    val sourceEvidenceVerified: Boolean = false,
    val slotNumber: Int = 1
) {
    init {
        require(policyId == Engine15QualityProgressionPolicy.POLICY_ID)
        require(weekNumber > 0 && rationale.isNotBlank())
        require(ruleIds.distinct() == ruleIds && ruleIds.isNotEmpty())
        require(!sourceEvidenceVerified)
    }
}

/**
 * Stage 3 evidence-bounded & 50/50 history-fitness adaptive multi-week quality dosage
 * and progression policy.
 *
 * - Preserves strict unit-test behavior when called without an adaptive anchor override.
 * - For app plan generation, blends 50% Auto-Detected Recent Quality Workouts +
 *   50% Historical Fitness & Volume Capacity so fit runners never receive "junk"
 *   10-minute workouts, while progressing across build blocks and tapering before races.
 */
internal object Engine15QualityProgressionPolicy {
    const val POLICY_ID = "stridelab.engine15.quality-progression-v1"
    private const val WARM_UP_SECONDS = 10 * 60
    private const val INTRO_COOL_DOWN_SECONDS = 8 * 60
    private const val EXTENDED_COOL_DOWN_SECONDS = 10 * 60
    private const val RECOVERY_SECONDS = 2 * 60
    private const val MAX_EXPOSURE_INCREMENT_SECONDS = 4 * 60 // +4 min strict evidence cap

    private fun step(purpose: WorkoutPurpose, seconds: Int, target: WorkoutTarget) =
        WorkoutStep(purpose, WorkoutUnit.SECONDS, seconds.toDouble(), 1, target)

    private fun meterStep(purpose: WorkoutPurpose, meters: Int, target: WorkoutTarget) =
        WorkoutStep(purpose, WorkoutUnit.METERS, meters.toDouble(), 1, target)

    private fun coolDown(workSeconds: Int) =
        if (workSeconds >= 20 * 60) EXTENDED_COOL_DOWN_SECONDS else INTRO_COOL_DOWN_SECONDS

    private fun continuousStep(
        stepIndex: Int,
        stageLabel: String,
        workSeconds: Int,
        target: WorkoutTarget,
        archetype: Engine15WorkoutArchetype? = null
    ): Engine15QualityDoseStep {
        val cool = coolDown(workSeconds)
        val workout = IndependentWorkoutComposition(listOf(
            IndependentWorkoutElement.Step(step(WorkoutPurpose.WARM_UP, WARM_UP_SECONDS, WorkoutTarget.EASY)),
            IndependentWorkoutElement.Step(step(WorkoutPurpose.WORK, workSeconds, target)),
            IndependentWorkoutElement.Step(step(WorkoutPurpose.COOL_DOWN, cool, WorkoutTarget.EASY))
        ))
        return Engine15QualityDoseStep(
            stepIndex = stepIndex,
            stageLabel = stageLabel,
            workout = workout,
            workSeconds = workSeconds,
            recoverySeconds = 0,
            totalSeconds = WARM_UP_SECONDS + workSeconds + cool,
            archetype = archetype
        )
    }

    private fun intervalStep(
        stepIndex: Int,
        stageLabel: String,
        repetitions: Int,
        repWorkSeconds: Int,
        target: WorkoutTarget,
        repRecoverySeconds: Int = RECOVERY_SECONDS,
        archetype: Engine15WorkoutArchetype? = null
    ): Engine15QualityDoseStep {
        val workSeconds = repetitions * repWorkSeconds
        val recoverySeconds = (repetitions - 1) * repRecoverySeconds
        val cool = coolDown(workSeconds)
        val workout = IndependentWorkoutComposition(listOf(
            IndependentWorkoutElement.Step(step(WorkoutPurpose.WARM_UP, WARM_UP_SECONDS, WorkoutTarget.EASY)),
            IndependentWorkoutElement.Intervals(IndependentIntervalGroup(
                repetitions = repetitions,
                work = step(WorkoutPurpose.WORK, repWorkSeconds, target),
                recovery = step(WorkoutPurpose.RECOVERY, repRecoverySeconds, WorkoutTarget.EASY)
            )),
            IndependentWorkoutElement.Step(step(WorkoutPurpose.COOL_DOWN, cool, WorkoutTarget.EASY))
        ))
        return Engine15QualityDoseStep(
            stepIndex = stepIndex,
            stageLabel = stageLabel,
            workout = workout,
            workSeconds = workSeconds,
            recoverySeconds = recoverySeconds,
            totalSeconds = WARM_UP_SECONDS + workSeconds + recoverySeconds + cool,
            archetype = archetype
        )
    }

    private fun distanceIntervalStep(
        stepIndex: Int,
        stageLabel: String,
        repetitions: Int,
        repWorkMeters: Int,
        repRecoverySeconds: Int?,
        repRecoveryMeters: Int?,
        target: WorkoutTarget,
        zones: Engine15ProvisionalPaceZones,
        archetype: Engine15WorkoutArchetype
    ): Engine15QualityDoseStep {
        val workPace = zones.forTarget(target, archetype).midpointSecondsPerKm
        val easyPace = zones.easy.midpointSecondsPerKm
        val repWorkSec = kotlin.math.round(repWorkMeters * workPace / 1000.0).toInt().coerceAtLeast(30)
        val repRecSec = repRecoverySeconds ?: kotlin.math.round((repRecoveryMeters ?: 200) * easyPace / 1000.0).toInt().coerceAtLeast(30)
        val workSec = repetitions * repWorkSec
        val recSec = (repetitions - 1) * repRecSec
        val cool = coolDown(workSec)
        val workWorkoutStep = meterStep(WorkoutPurpose.WORK, repWorkMeters, target)
        val recWorkoutStep = if (repRecoveryMeters != null) {
            meterStep(WorkoutPurpose.RECOVERY, repRecoveryMeters, WorkoutTarget.EASY)
        } else {
            step(WorkoutPurpose.RECOVERY, repRecoverySeconds!!, WorkoutTarget.EASY)
        }
        val workout = IndependentWorkoutComposition(listOf(
            IndependentWorkoutElement.Step(step(WorkoutPurpose.WARM_UP, WARM_UP_SECONDS, WorkoutTarget.EASY)),
            IndependentWorkoutElement.Intervals(IndependentIntervalGroup(
                repetitions = repetitions,
                work = workWorkoutStep,
                recovery = recWorkoutStep
            )),
            IndependentWorkoutElement.Step(step(WorkoutPurpose.COOL_DOWN, cool, WorkoutTarget.EASY))
        ))
        return Engine15QualityDoseStep(
            stepIndex = stepIndex,
            stageLabel = stageLabel,
            workout = workout,
            workSeconds = workSec,
            recoverySeconds = recSec,
            totalSeconds = WARM_UP_SECONDS + workSec + recSec + cool,
            archetype = archetype,
            workMeters = repetitions * repWorkMeters,
            recoveryMeters = (repetitions - 1) * (repRecoveryMeters ?: 0)
        )
    }

    private data class PyramidLeg(
        val workMeters: Int,
        val target: WorkoutTarget,
        val recoveryMeters: Int? = null,
        val recoverySeconds: Int? = null
    )

    private fun pyramidStep(
        stepIndex: Int,
        stageLabel: String,
        legs: List<PyramidLeg>,
        zones: Engine15ProvisionalPaceZones
    ): Engine15QualityDoseStep {
        val easyPace = zones.easy.midpointSecondsPerKm
        var workSec = 0
        var recSec = 0
        var workM = 0
        var recM = 0
        val elements = buildList {
            add(IndependentWorkoutElement.Step(step(WorkoutPurpose.WARM_UP, WARM_UP_SECONDS, WorkoutTarget.EASY)))
            legs.forEachIndexed { idx, leg ->
                val pace = zones.forTarget(leg.target).midpointSecondsPerKm
                workSec += kotlin.math.round(leg.workMeters * pace / 1000.0).toInt().coerceAtLeast(30)
                workM += leg.workMeters
                add(IndependentWorkoutElement.Step(meterStep(WorkoutPurpose.WORK, leg.workMeters, leg.target)))
                if (idx < legs.lastIndex) {
                    if (leg.recoveryMeters != null) {
                        recSec += kotlin.math.round(leg.recoveryMeters * easyPace / 1000.0).toInt().coerceAtLeast(30)
                        recM += leg.recoveryMeters
                        add(IndependentWorkoutElement.Step(meterStep(WorkoutPurpose.RECOVERY, leg.recoveryMeters, WorkoutTarget.EASY)))
                    } else if (leg.recoverySeconds != null) {
                        recSec += leg.recoverySeconds
                        add(IndependentWorkoutElement.Step(step(WorkoutPurpose.RECOVERY, leg.recoverySeconds, WorkoutTarget.EASY)))
                    }
                }
            }
            val cool = coolDown(workSec)
            add(IndependentWorkoutElement.Step(step(WorkoutPurpose.COOL_DOWN, cool, WorkoutTarget.EASY)))
        }
        val cool = coolDown(workSec)
        return Engine15QualityDoseStep(
            stepIndex = stepIndex,
            stageLabel = stageLabel,
            workout = IndependentWorkoutComposition(elements),
            workSeconds = workSec,
            recoverySeconds = recSec,
            totalSeconds = WARM_UP_SECONDS + workSec + recSec + cool,
            archetype = Engine15WorkoutArchetype.SPEED_ENDURANCE_PYRAMID,
            workMeters = workM,
            recoveryMeters = recM
        )
    }

    fun ladder(family: IndependentQualityFamily, experience: Int): List<Engine15QualityDoseStep> =
        when (family) {
            IndependentQualityFamily.TEMPO -> {
                val introWork = if (experience in 1..2) 10 * 60 else 12 * 60
                buildList {
                    add(continuousStep(0, "INTRO", introWork, WorkoutTarget.TEMPO))
                    if (introWork < 12 * 60) {
                        add(continuousStep(size, "BUILD_1", 12 * 60, WorkoutTarget.TEMPO))
                    }
                    add(continuousStep(size, "BUILD_2", 15 * 60, WorkoutTarget.TEMPO))
                    add(continuousStep(size, "BUILD_3", 18 * 60, WorkoutTarget.TEMPO))
                    add(continuousStep(size, "EXTENDED", 20 * 60, WorkoutTarget.TEMPO))
                    add(continuousStep(size, "BUILD_4", 24 * 60, WorkoutTarget.TEMPO))
                    add(continuousStep(size, "BUILD_5", 28 * 60, WorkoutTarget.TEMPO))
                    add(continuousStep(size, "PEAK_1", 32 * 60, WorkoutTarget.TEMPO))
                    add(continuousStep(size, "PEAK_2", 36 * 60, WorkoutTarget.TEMPO))
                    add(continuousStep(size, "PEAK_3", 40 * 60, WorkoutTarget.TEMPO))
                }
            }
            IndependentQualityFamily.THRESHOLD -> listOf(
                intervalStep(0, "INTRO", 3, 4 * 60, WorkoutTarget.THRESHOLD),
                intervalStep(1, "BUILD_1", 3, 5 * 60, WorkoutTarget.THRESHOLD),
                intervalStep(2, "BUILD_2", 4, 4 * 60, WorkoutTarget.THRESHOLD),
                intervalStep(3, "BUILD_3", 3, 6 * 60, WorkoutTarget.THRESHOLD),
                intervalStep(4, "EXTENDED", 4, 5 * 60, WorkoutTarget.THRESHOLD),
                intervalStep(5, "BUILD_4", 4, 6 * 60, WorkoutTarget.THRESHOLD),
                intervalStep(6, "BUILD_5", 4, 7 * 60, WorkoutTarget.THRESHOLD),
                intervalStep(7, "PEAK_1", 4, 8 * 60, WorkoutTarget.THRESHOLD),
                intervalStep(8, "PEAK_2", 5, 7 * 60, WorkoutTarget.THRESHOLD),
                intervalStep(9, "PEAK_3", 4, 10 * 60, WorkoutTarget.THRESHOLD)
            )
            IndependentQualityFamily.INTERVALS -> listOf(
                intervalStep(0, "INTRO", 4, 2 * 60, WorkoutTarget.INTERVALS),
                intervalStep(1, "BUILD_1", 5, 2 * 60, WorkoutTarget.INTERVALS),
                intervalStep(2, "BUILD_2", 4, 3 * 60, WorkoutTarget.INTERVALS),
                intervalStep(3, "BUILD_3", 6, 2 * 60, WorkoutTarget.INTERVALS),
                intervalStep(4, "EXTENDED", 5, 3 * 60, WorkoutTarget.INTERVALS),
                intervalStep(5, "BUILD_4", 6, 3 * 60, WorkoutTarget.INTERVALS),
                intervalStep(6, "BUILD_5", 5, 4 * 60, WorkoutTarget.INTERVALS),
                intervalStep(7, "PEAK_1", 6, 4 * 60, WorkoutTarget.INTERVALS),
                intervalStep(8, "PEAK_2", 7, 4 * 60, WorkoutTarget.INTERVALS)
            )
            IndependentQualityFamily.HILLS -> listOf(
                intervalStep(0, "INTRO", 5, 60, WorkoutTarget.HILLS),
                intervalStep(1, "BUILD_1", 6, 60, WorkoutTarget.HILLS),
                intervalStep(2, "BUILD_2", 5, 90, WorkoutTarget.HILLS),
                intervalStep(3, "BUILD_3", 8, 60, WorkoutTarget.HILLS),
                intervalStep(4, "EXTENDED", 6, 90, WorkoutTarget.HILLS),
                intervalStep(5, "BUILD_4", 8, 90, WorkoutTarget.HILLS),
                intervalStep(6, "PEAK_1", 10, 90, WorkoutTarget.HILLS),
                intervalStep(7, "PEAK_2", 8, 120, WorkoutTarget.HILLS)
            )
            IndependentQualityFamily.FARTLEK -> listOf(
                intervalStep(0, "INTRO", 5, 2 * 60, WorkoutTarget.FARTLEK),
                intervalStep(1, "BUILD_1", 6, 2 * 60, WorkoutTarget.FARTLEK),
                intervalStep(2, "BUILD_2", 5, 3 * 60, WorkoutTarget.FARTLEK),
                intervalStep(3, "BUILD_3", 7, 2 * 60, WorkoutTarget.FARTLEK),
                intervalStep(4, "EXTENDED", 6, 3 * 60, WorkoutTarget.FARTLEK),
                intervalStep(5, "BUILD_4", 7, 3 * 60, WorkoutTarget.FARTLEK),
                intervalStep(6, "PEAK_1", 8, 3 * 60, WorkoutTarget.FARTLEK),
                intervalStep(7, "PEAK_2", 7, 4 * 60, WorkoutTarget.FARTLEK)
            )
            IndependentQualityFamily.PROGRESSION -> listOf(
                continuousStep(0, "INTRO", 12 * 60, WorkoutTarget.PROGRESSION),
                continuousStep(1, "BUILD_1", 15 * 60, WorkoutTarget.PROGRESSION),
                continuousStep(2, "BUILD_2", 18 * 60, WorkoutTarget.PROGRESSION),
                continuousStep(3, "EXTENDED", 20 * 60, WorkoutTarget.PROGRESSION),
                continuousStep(4, "BUILD_3", 25 * 60, WorkoutTarget.PROGRESSION),
                continuousStep(5, "BUILD_4", 30 * 60, WorkoutTarget.PROGRESSION),
                continuousStep(6, "PEAK_1", 35 * 60, WorkoutTarget.PROGRESSION),
                continuousStep(7, "PEAK_2", 40 * 60, WorkoutTarget.PROGRESSION)
            )
        }

    /**
     * Computes the 50/50 Auto-Detected Workout + Historical Fitness quality work anchor (in seconds).
     * Seeds new plans from recent volume and provisional quality evidence.
     * Saved plans replay the persisted anchor without reading today’s history.
     */
    fun computeAdaptiveExposureAnchorSeconds(
        request: PlanRequest,
        family: IndependentQualityFamily,
        activities: List<ActivityRecord> = emptyList(),
        plans: List<PlanSnapshot> = emptyList(),
        detections: List<Engine15DetectedWorkout> = emptyList(),
        now: Instant = Instant.now()
    ): Int {
        val cutoff = now.minusSeconds(56L * 86_400)
        val recentActivities = activities.filter { it.startedAt in cutoff..now }
        val zones = Engine15PaceZoneEstimator.estimate(request, activities = recentActivities)
        val effectiveWeeklyKm = maxOf(
            request.weeklyKm,
            automaticRunningBaseline(recentActivities, now = now, plans = plans)?.weeklyKm ?: 0.0
        ).coerceIn(12.0, 120.0)
        val sessionsPerWeek = request.qualityCount.coerceIn(1, 2)

        // 1. 50% Weight: Historical Fitness & Volume Capacity
        // Active quality work share of weekly volume split evenly across requested quality sessions:
        // - Tempo / Threshold / Progression / Fartlek: ~15.5-16.5% (1 session) or ~18-19% total (2 sessions)
        // - Intervals / Hills: ~8.5-11.5% (1 session) or ~11-14% total (2 sessions)
        val weeklyWorkShare = when (family) {
            IndependentQualityFamily.TEMPO,
            IndependentQualityFamily.PROGRESSION -> if (sessionsPerWeek == 2) 0.19 else 0.165
            IndependentQualityFamily.THRESHOLD,
            IndependentQualityFamily.FARTLEK -> if (sessionsPerWeek == 2) 0.18 else 0.155
            IndependentQualityFamily.INTERVALS -> if (sessionsPerWeek == 2) 0.14 else 0.115
            IndependentQualityFamily.HILLS -> if (sessionsPerWeek == 2) 0.11 else 0.085
        }
        val perSessionWorkShare = weeklyWorkShare / sessionsPerWeek
        val maxSessionWorkKm = if (sessionsPerWeek == 2) 7.0 else 11.0
        val targetWorkKm = (effectiveWeeklyKm * perSessionWorkShare).coerceIn(1.8, maxSessionWorkKm)
        val workPaceSecPerKm = when (family) {
            IndependentQualityFamily.TEMPO,
            IndependentQualityFamily.PROGRESSION -> zones.tempo.midpointSecondsPerKm
            IndependentQualityFamily.THRESHOLD -> zones.threshold.midpointSecondsPerKm
            IndependentQualityFamily.INTERVALS,
            IndependentQualityFamily.HILLS,
            IndependentQualityFamily.FARTLEK -> zones.intervals.midpointSecondsPerKm
        }
        val rawFitnessWorkSec = (targetWorkKm * workPaceSecPerKm).roundToInt()

        // No-Junk-Workout Floor scaled by weekly mileage, experience, and weekly quality session count
        val meaningfulFloorSec = when (family) {
            IndependentQualityFamily.TEMPO,
            IndependentQualityFamily.THRESHOLD,
            IndependentQualityFamily.PROGRESSION -> when {
                request.experience == 0 -> if (effectiveWeeklyKm < 25.0) 10 * 60 else 12 * 60
                effectiveWeeklyKm >= 55.0 -> if (sessionsPerWeek == 2) 18 * 60 else 26 * 60
                effectiveWeeklyKm >= 40.0 -> if (sessionsPerWeek == 2) 14 * 60 else 20 * 60
                effectiveWeeklyKm >= 30.0 -> if (sessionsPerWeek == 2) 12 * 60 else 16 * 60
                else -> if (sessionsPerWeek == 2) 10 * 60 else 12 * 60
            }
            IndependentQualityFamily.INTERVALS,
            IndependentQualityFamily.FARTLEK -> when {
                request.experience == 0 -> 8 * 60
                effectiveWeeklyKm >= 55.0 -> if (sessionsPerWeek == 2) 14 * 60 else 18 * 60
                effectiveWeeklyKm >= 40.0 -> if (sessionsPerWeek == 2) 12 * 60 else 15 * 60
                effectiveWeeklyKm >= 30.0 -> if (sessionsPerWeek == 2) 10 * 60 else 12 * 60
                else -> 8 * 60
            }
            IndependentQualityFamily.HILLS -> when {
                request.experience == 0 -> 5 * 60
                effectiveWeeklyKm >= 55.0 -> if (sessionsPerWeek == 2) 8 * 60 else 12 * 60
                effectiveWeeklyKm >= 35.0 -> if (sessionsPerWeek == 2) 6 * 60 else 9 * 60
                else -> 5 * 60
            }
        }
        val fitnessCapacitySec = maxOf(rawFitnessWorkSec, meaningfulFloorSec).coerceIn(300, 2400)

        // Prefer repeated same-family work evidence over a whole-run pace proxy.
        val dates = cutoff.atOffset(ZoneOffset.UTC).toLocalDate()..now.atOffset(ZoneOffset.UTC).toLocalDate()
        val recentDetections = detections.filter { it.performedOn in dates }
        val coveredIds = recentDetections.flatMap { it.mergedSourceIds + it.activityId }.toSet()
        val countedIds = mutableSetOf<String>()
        val lapOrLinkedDurations = recentDetections.asSequence()
            .filter { it.kind == ActivityWorkoutKind.QUALITY_RUN &&
                it.qualitySubtype?.name == family.name &&
                it.confidence != Engine15DetectionConfidence.AMBIGUOUS_CONFLICT }
            .sortedWith(compareByDescending<Engine15DetectedWorkout> { it.performedOn }.thenBy { it.activityId })
            .mapNotNull { detection ->
                val workSeconds = detection.candidateWorkSegmentsSeconds.sum().takeIf { it in 300..3600 }
                    ?: return@mapNotNull null
                val ids = detection.mergedSourceIds + detection.activityId
                val duplicate = ids.any { it in countedIds }
                countedIds.addAll(ids)
                workSeconds.takeUnless { duplicate }
            }.take(4).toList()

        val easyPace = zones.easy.midpointSecondsPerKm
        val paceDetectedDurations = ActivityDisplayConsolidation.groups(recentActivities)
            .filter { group -> group.primary.id !in coveredIds && group.additionalSources.none { it.id in coveredIds } }
            .map { it.primary }
            .filter { DashboardSummary.isRun(it) }
            .sortedByDescending { it.startedAt }
            .mapNotNull { run ->
                val distM = run.distanceM?.takeIf { it.isFinite() && it >= 4_000.0 } ?: return@mapNotNull null
                val durS = run.durationS?.takeIf { it.isFinite() && it >= 900.0 } ?: return@mapNotNull null
                val pace = (run.recordedPace ?: run.garminMovingPaceSPerKm ?: (durS * 1000.0 / distM))
                // A run whose overall average pace is >= 8% faster than easy midpoint is a quality/fast effort
                if (pace.isFinite() && pace <= easyPace * 0.92) {
                    (durS * 0.65).roundToInt().coerceIn(600, 2700)
                } else null
            }

        // When an athlete has ZERO recent quality efforts detected (e.g., purely easy-mileage base),
        // don't drop straight into max-ceiling 40-min quality in Week 1 — introduce at controlled fitness dose.
        val unverifiedCapSec = when (family) {
            IndependentQualityFamily.TEMPO,
            IndependentQualityFamily.THRESHOLD,
            IndependentQualityFamily.PROGRESSION -> if (sessionsPerWeek == 2) 18 * 60 else 24 * 60
            IndependentQualityFamily.INTERVALS,
            IndependentQualityFamily.FARTLEK -> if (sessionsPerWeek == 2) 14 * 60 else 18 * 60
            IndependentQualityFamily.HILLS -> if (sessionsPerWeek == 2) 9 * 60 else 12 * 60
        }
        val detectedWorkSec = when {
            lapOrLinkedDurations.size >= 2 -> lapOrLinkedDurations.average().roundToInt()
            paceDetectedDurations.isNotEmpty() -> paceDetectedDurations.take(4).average().roundToInt()
                .coerceAtMost(unverifiedCapSec)
            else -> minOf(fitnessCapacitySec, unverifiedCapSec)
        }

        // 3. 50 / 50 Blend + Week-1 Progressive Headroom (~78% of peak capacity, bounded safely by experience)
        val blendedPeakSec = (0.50 * detectedWorkSec + 0.50 * fitnessCapacitySec).roundToInt()
        val progressiveStartSec = if (request.weeks >= 6) (blendedPeakSec * 0.78).roundToInt() else blendedPeakSec
        val w1ExperienceCeiling = when {
            request.experience == 0 && family in setOf(IndependentQualityFamily.TEMPO, IndependentQualityFamily.PROGRESSION) -> 15 * 60
            request.experience == 0 -> 12 * 60
            family == IndependentQualityFamily.INTERVALS -> if (sessionsPerWeek == 2) 16 * 60 else 22 * 60
            family == IndependentQualityFamily.HILLS -> if (sessionsPerWeek == 2) 10 * 60 else 12 * 60
            sessionsPerWeek == 2 -> 20 * 60
            else -> 32 * 60
        }
        val proposedEntry = maxOf(minOf(progressiveStartSec, w1ExperienceCeiling), meaningfulFloorSec)
        val experienceAdjustedEntry = if (request.experience == 1 || request.returning) {
            // Keep one rung of headroom for runners with less experience or returning from a break.
            val entryRungSeconds = ladder(family, request.experience).first().workSeconds
            minOf(proposedEntry, maxOf(entryRungSeconds, (proposedEntry * 0.85).roundToInt()))
        } else proposedEntry
        return experienceAdjustedEntry.coerceIn(300, 2400)
    }

    internal fun qualityFamilyRotation(
        request: PlanRequest,
        primaryFamily: IndependentQualityFamily
    ): List<IndependentQualityFamily> {
        val base = when (request.goal) {
            "Improve 5K" -> when (request.experience) {
                0 -> listOf(IndependentQualityFamily.TEMPO, IndependentQualityFamily.FARTLEK, IndependentQualityFamily.HILLS)
                1 -> listOf(IndependentQualityFamily.INTERVALS, IndependentQualityFamily.THRESHOLD, IndependentQualityFamily.TEMPO, IndependentQualityFamily.HILLS)
                else -> listOf(IndependentQualityFamily.INTERVALS, IndependentQualityFamily.THRESHOLD, IndependentQualityFamily.HILLS, IndependentQualityFamily.TEMPO)
            }
            "Improve 10K" -> when (request.experience) {
                0 -> listOf(IndependentQualityFamily.TEMPO, IndependentQualityFamily.PROGRESSION, IndependentQualityFamily.FARTLEK)
                1 -> listOf(IndependentQualityFamily.THRESHOLD, IndependentQualityFamily.TEMPO, IndependentQualityFamily.INTERVALS, IndependentQualityFamily.PROGRESSION)
                else -> listOf(IndependentQualityFamily.THRESHOLD, IndependentQualityFamily.INTERVALS, IndependentQualityFamily.TEMPO, IndependentQualityFamily.PROGRESSION)
            }
            "Half marathon" -> when (request.experience) {
                0 -> listOf(IndependentQualityFamily.TEMPO, IndependentQualityFamily.PROGRESSION, IndependentQualityFamily.FARTLEK)
                1 -> listOf(IndependentQualityFamily.TEMPO, IndependentQualityFamily.THRESHOLD, IndependentQualityFamily.PROGRESSION, IndependentQualityFamily.FARTLEK)
                else -> listOf(IndependentQualityFamily.THRESHOLD, IndependentQualityFamily.TEMPO, IndependentQualityFamily.PROGRESSION, IndependentQualityFamily.INTERVALS)
            }
            else -> when (request.experience) {
                0 -> listOf(IndependentQualityFamily.TEMPO, IndependentQualityFamily.PROGRESSION, IndependentQualityFamily.FARTLEK)
                1 -> listOf(IndependentQualityFamily.TEMPO, IndependentQualityFamily.PROGRESSION, IndependentQualityFamily.THRESHOLD, IndependentQualityFamily.HILLS)
                else -> listOf(IndependentQualityFamily.TEMPO, IndependentQualityFamily.THRESHOLD, IndependentQualityFamily.PROGRESSION, IndependentQualityFamily.HILLS)
            }
        }
        val filtered = if (request.allowedQualityTypes.isEmpty()) base else base.filter { fam ->
            request.allowedQualityTypes.any { it.equals(fam.name, ignoreCase = true) }
        }
        val pool = if (filtered.isNotEmpty() || request.allowedQualityTypes.isEmpty()) {
            filtered
        } else {
            IndependentQualityFamily.entries.filter { fam ->
                request.allowedQualityTypes.any { it.equals(fam.name, ignoreCase = true) }
            }
        }
        return if (pool.isEmpty()) listOf(primaryFamily)
        else (listOf(primaryFamily) + pool).distinct()
    }

    private fun familyMaxWorkSeconds(
        family: IndependentQualityFamily,
        experience: Int,
        qualityCount: Int
    ): Int = when (family) {
        IndependentQualityFamily.INTERVALS ->
            if (experience == 0) 14 * 60 else if (qualityCount >= 2) 18 * 60 else 24 * 60
        IndependentQualityFamily.HILLS ->
            if (experience == 0) 9 * 60 else if (qualityCount >= 2) 10 * 60 else 14 * 60
        IndependentQualityFamily.FARTLEK ->
            if (experience == 0) 16 * 60 else if (qualityCount >= 2) 20 * 60 else 24 * 60
        IndependentQualityFamily.THRESHOLD ->
            if (experience == 0) 18 * 60 else if (qualityCount >= 2) 24 * 60 else 36 * 60
        IndependentQualityFamily.TEMPO,
        IndependentQualityFamily.PROGRESSION ->
            if (experience == 0) 20 * 60 else if (qualityCount >= 2) 28 * 60 else 40 * 60
    }

    internal fun adaptiveArchetypeRotation(
        request: PlanRequest,
        primaryFamily: IndependentQualityFamily
    ): List<Engine15WorkoutArchetype> {
        val allQualityArchetypes = listOf(
            Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS,
            Engine15WorkoutArchetype.BROKEN_TEMPO,
            Engine15WorkoutArchetype.CONTINUOUS_TEMPO,
            Engine15WorkoutArchetype.CLASSIC_VO2MAX_INTERVALS,
            Engine15WorkoutArchetype.SHORT_TRACK_REPEATS,
            Engine15WorkoutArchetype.SPEED_ENDURANCE_PYRAMID,
            Engine15WorkoutArchetype.AEROBIC_FARTLEK,
            Engine15WorkoutArchetype.SHORT_HILL_SPRINTS,
            Engine15WorkoutArchetype.LONG_AEROBIC_HILLS
        )
        if (request.preferredQualityExplicit) {
            return when (primaryFamily) {
                IndependentQualityFamily.THRESHOLD -> listOf(
                    Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS,
                    Engine15WorkoutArchetype.SPEED_ENDURANCE_PYRAMID
                ).filter { request.experience >= 2 || it != Engine15WorkoutArchetype.SPEED_ENDURANCE_PYRAMID }
                IndependentQualityFamily.TEMPO, IndependentQualityFamily.PROGRESSION -> listOf(
                    Engine15WorkoutArchetype.CONTINUOUS_TEMPO,
                    Engine15WorkoutArchetype.BROKEN_TEMPO
                )
                IndependentQualityFamily.INTERVALS -> listOf(
                    Engine15WorkoutArchetype.CLASSIC_VO2MAX_INTERVALS,
                    Engine15WorkoutArchetype.SHORT_TRACK_REPEATS,
                    Engine15WorkoutArchetype.SPEED_ENDURANCE_PYRAMID
                ).filter { request.experience >= 2 || it != Engine15WorkoutArchetype.SPEED_ENDURANCE_PYRAMID }
                IndependentQualityFamily.HILLS -> listOf(
                    Engine15WorkoutArchetype.SHORT_HILL_SPRINTS,
                    Engine15WorkoutArchetype.LONG_AEROBIC_HILLS
                )
                IndependentQualityFamily.FARTLEK -> listOf(Engine15WorkoutArchetype.AEROBIC_FARTLEK)
            }
        }
        val base = when {
            request.experience == 0 || request.returning -> when (primaryFamily) {
                IndependentQualityFamily.TEMPO, IndependentQualityFamily.PROGRESSION -> listOf(
                    Engine15WorkoutArchetype.CONTINUOUS_TEMPO,
                    Engine15WorkoutArchetype.AEROBIC_FARTLEK,
                    Engine15WorkoutArchetype.SHORT_HILL_SPRINTS
                )
                IndependentQualityFamily.HILLS -> listOf(
                    Engine15WorkoutArchetype.SHORT_HILL_SPRINTS,
                    Engine15WorkoutArchetype.AEROBIC_FARTLEK,
                    Engine15WorkoutArchetype.CONTINUOUS_TEMPO
                )
                else -> listOf(
                    Engine15WorkoutArchetype.AEROBIC_FARTLEK,
                    Engine15WorkoutArchetype.SHORT_HILL_SPRINTS,
                    Engine15WorkoutArchetype.CONTINUOUS_TEMPO
                )
            }
            request.goal == "Improve 5K" -> if (request.experience == 1) listOf(
                Engine15WorkoutArchetype.CLASSIC_VO2MAX_INTERVALS,
                Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS,
                Engine15WorkoutArchetype.SHORT_TRACK_REPEATS,
                Engine15WorkoutArchetype.AEROBIC_FARTLEK,
                Engine15WorkoutArchetype.SHORT_HILL_SPRINTS
            ) else listOf(
                Engine15WorkoutArchetype.CLASSIC_VO2MAX_INTERVALS,
                Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS,
                Engine15WorkoutArchetype.SHORT_TRACK_REPEATS,
                Engine15WorkoutArchetype.SPEED_ENDURANCE_PYRAMID,
                Engine15WorkoutArchetype.SHORT_HILL_SPRINTS
            )
            request.goal == "Improve 10K" -> if (request.experience == 1) listOf(
                Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS,
                Engine15WorkoutArchetype.CLASSIC_VO2MAX_INTERVALS,
                Engine15WorkoutArchetype.BROKEN_TEMPO,
                Engine15WorkoutArchetype.CONTINUOUS_TEMPO,
                Engine15WorkoutArchetype.LONG_AEROBIC_HILLS
            ) else listOf(
                Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS,
                Engine15WorkoutArchetype.CLASSIC_VO2MAX_INTERVALS,
                Engine15WorkoutArchetype.SPEED_ENDURANCE_PYRAMID,
                Engine15WorkoutArchetype.BROKEN_TEMPO,
                Engine15WorkoutArchetype.LONG_AEROBIC_HILLS
            )
            request.goal == "Half marathon" -> if (request.experience == 1) listOf(
                Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS,
                Engine15WorkoutArchetype.BROKEN_TEMPO,
                Engine15WorkoutArchetype.CONTINUOUS_TEMPO,
                Engine15WorkoutArchetype.LONG_AEROBIC_HILLS
            ) else listOf(
                Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS,
                Engine15WorkoutArchetype.BROKEN_TEMPO,
                Engine15WorkoutArchetype.SPEED_ENDURANCE_PYRAMID,
                Engine15WorkoutArchetype.CONTINUOUS_TEMPO,
                Engine15WorkoutArchetype.LONG_AEROBIC_HILLS
            )
            request.goal == "Marathon" -> if (request.experience == 1) listOf(
                Engine15WorkoutArchetype.CONTINUOUS_TEMPO,
                Engine15WorkoutArchetype.BROKEN_TEMPO,
                Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS,
                Engine15WorkoutArchetype.LONG_AEROBIC_HILLS
            ) else listOf(
                Engine15WorkoutArchetype.BROKEN_TEMPO,
                Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS,
                Engine15WorkoutArchetype.CONTINUOUS_TEMPO,
                Engine15WorkoutArchetype.LONG_AEROBIC_HILLS
            )
            request.goal == "Aerobic foundation" -> listOf(
                Engine15WorkoutArchetype.AEROBIC_FARTLEK,
                Engine15WorkoutArchetype.SHORT_HILL_SPRINTS,
                Engine15WorkoutArchetype.LONG_AEROBIC_HILLS,
                Engine15WorkoutArchetype.CONTINUOUS_TEMPO
            )
            else -> listOf( // "General fitness" and Train Your Way default
                Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS,
                Engine15WorkoutArchetype.BROKEN_TEMPO,
                Engine15WorkoutArchetype.AEROBIC_FARTLEK,
                Engine15WorkoutArchetype.CLASSIC_VO2MAX_INTERVALS,
                Engine15WorkoutArchetype.LONG_AEROBIC_HILLS
            )
        }
        val filtered = if (request.allowedQualityTypes.isEmpty()) base else base.filter { arch ->
            val famName = arch.family?.name ?: return@filter false
            request.allowedQualityTypes.any { it.equals(famName, ignoreCase = true) }
        }
        val pool = if (filtered.isNotEmpty() || request.allowedQualityTypes.isEmpty()) {
            filtered
        } else {
            allQualityArchetypes.filter { arch ->
                val famName = arch.family?.name ?: return@filter false
                request.allowedQualityTypes.any { it.equals(famName, ignoreCase = true) }
            }
        }
        return pool.ifEmpty {
            listOfNotNull(allQualityArchetypes.firstOrNull { it.family == primaryFamily })
                .ifEmpty { listOf(Engine15WorkoutArchetype.CONTINUOUS_TEMPO) }
        }.distinct()
    }

    internal fun adaptiveLadder(
        archetype: Engine15WorkoutArchetype,
        experience: Int,
        zones: Engine15ProvisionalPaceZones,
        preferDistanceBased: Boolean,
        useTimedRecoveryForShortTrack: Boolean = false
    ): List<Engine15QualityDoseStep> = when (archetype) {
        Engine15WorkoutArchetype.AEROBIC_FARTLEK -> listOf(
            intervalStep(0, "INTRO", 4, 2 * 60, WorkoutTarget.FARTLEK, repRecoverySeconds = 90, archetype = archetype),
            intervalStep(1, "BUILD_1", 5, 2 * 60, WorkoutTarget.FARTLEK, repRecoverySeconds = 90, archetype = archetype),
            intervalStep(2, "BUILD_2", 6, 2 * 60, WorkoutTarget.FARTLEK, repRecoverySeconds = 90, archetype = archetype),
            intervalStep(3, "EXTENDED", 5, 3 * 60, WorkoutTarget.FARTLEK, repRecoverySeconds = 120, archetype = archetype),
            intervalStep(4, "BUILD_3", 6, 3 * 60, WorkoutTarget.FARTLEK, repRecoverySeconds = 120, archetype = archetype),
            intervalStep(5, "PEAK_1", 7, 3 * 60, WorkoutTarget.FARTLEK, repRecoverySeconds = 120, archetype = archetype)
        )
        Engine15WorkoutArchetype.SHORT_HILL_SPRINTS -> listOf(
            intervalStep(0, "INTRO", 6, 45, WorkoutTarget.HILLS, repRecoverySeconds = 90, archetype = archetype),
            intervalStep(1, "BUILD_1", 7, 45, WorkoutTarget.HILLS, repRecoverySeconds = 90, archetype = archetype),
            intervalStep(2, "BUILD_2", 6, 60, WorkoutTarget.HILLS, repRecoverySeconds = 120, archetype = archetype),
            intervalStep(3, "BUILD_3", 7, 60, WorkoutTarget.HILLS, repRecoverySeconds = 120, archetype = archetype),
            intervalStep(4, "PEAK_1", 8, 60, WorkoutTarget.HILLS, repRecoverySeconds = 120, archetype = archetype)
        )
        Engine15WorkoutArchetype.LONG_AEROBIC_HILLS -> listOf(
            intervalStep(0, "INTRO", 4, 90, WorkoutTarget.HILLS, repRecoverySeconds = 120, archetype = archetype),
            intervalStep(1, "BUILD_1", 5, 90, WorkoutTarget.HILLS, repRecoverySeconds = 150, archetype = archetype),
            intervalStep(2, "BUILD_2", 4, 120, WorkoutTarget.HILLS, repRecoverySeconds = 150, archetype = archetype),
            intervalStep(3, "EXTENDED", 6, 90, WorkoutTarget.HILLS, repRecoverySeconds = 150, archetype = archetype),
            intervalStep(4, "BUILD_3", 5, 120, WorkoutTarget.HILLS, repRecoverySeconds = 150, archetype = archetype),
            intervalStep(5, "PEAK_1", 6, 120, WorkoutTarget.HILLS, repRecoverySeconds = 150, archetype = archetype)
        )
        Engine15WorkoutArchetype.CONTINUOUS_TEMPO -> {
            val baseRungs = if (experience == 0) listOf(12, 15, 18, 20) else listOf(15, 18, 20, 24, 28, 30)
            baseRungs.mapIndexed { idx, min ->
                val label = if (idx == 0) "INTRO" else "BUILD_$idx"
                continuousStep(idx, label, min * 60, WorkoutTarget.TEMPO, archetype = archetype)
            }
        }
        Engine15WorkoutArchetype.BROKEN_TEMPO -> listOf(
            intervalStep(0, "INTRO", 2, 10 * 60, WorkoutTarget.TEMPO, repRecoverySeconds = 90, archetype = archetype),
            intervalStep(1, "BUILD_1", 2, 12 * 60, WorkoutTarget.TEMPO, repRecoverySeconds = 120, archetype = archetype),
            intervalStep(2, "BUILD_2", 3, 10 * 60, WorkoutTarget.TEMPO, repRecoverySeconds = 90, archetype = archetype),
            intervalStep(3, "EXTENDED", 2, 15 * 60, WorkoutTarget.TEMPO, repRecoverySeconds = 120, archetype = archetype),
            intervalStep(4, "BUILD_3", 3, 12 * 60, WorkoutTarget.TEMPO, repRecoverySeconds = 120, archetype = archetype),
            intervalStep(5, "PEAK_1", 3, 15 * 60, WorkoutTarget.TEMPO, repRecoverySeconds = 120, archetype = archetype)
        )
        Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS -> if (preferDistanceBased) listOf(
            distanceIntervalStep(0, "INTRO", 3, 1000, repRecoverySeconds = 60, repRecoveryMeters = null, WorkoutTarget.THRESHOLD, zones, archetype),
            distanceIntervalStep(1, "BUILD_1", 4, 1000, repRecoverySeconds = 60, repRecoveryMeters = null, WorkoutTarget.THRESHOLD, zones, archetype),
            distanceIntervalStep(2, "BUILD_2", 5, 1000, repRecoverySeconds = 60, repRecoveryMeters = null, WorkoutTarget.THRESHOLD, zones, archetype),
            distanceIntervalStep(3, "EXTENDED", 3, 2000, repRecoverySeconds = 90, repRecoveryMeters = null, WorkoutTarget.THRESHOLD, zones, archetype),
            distanceIntervalStep(4, "BUILD_3", 6, 1000, repRecoverySeconds = 60, repRecoveryMeters = null, WorkoutTarget.THRESHOLD, zones, archetype),
            distanceIntervalStep(5, "PEAK_1", 4, 2000, repRecoverySeconds = 90, repRecoveryMeters = null, WorkoutTarget.THRESHOLD, zones, archetype)
        ) else listOf(
            intervalStep(0, "INTRO", 3, 4 * 60, WorkoutTarget.THRESHOLD, repRecoverySeconds = 60, archetype = archetype),
            intervalStep(1, "BUILD_0", 3, 5 * 60, WorkoutTarget.THRESHOLD, repRecoverySeconds = 60, archetype = archetype),
            intervalStep(2, "BUILD_1", 4, 5 * 60, WorkoutTarget.THRESHOLD, repRecoverySeconds = 60, archetype = archetype),
            intervalStep(3, "BUILD_2", 5, 5 * 60, WorkoutTarget.THRESHOLD, repRecoverySeconds = 60, archetype = archetype),
            intervalStep(4, "EXTENDED", 4, 7 * 60, WorkoutTarget.THRESHOLD, repRecoverySeconds = 90, archetype = archetype),
            intervalStep(5, "BUILD_3", 4, 8 * 60, WorkoutTarget.THRESHOLD, repRecoverySeconds = 90, archetype = archetype)
        )
        Engine15WorkoutArchetype.SHORT_TRACK_REPEATS -> {
            val recSec = if (useTimedRecoveryForShortTrack) 70 else null
            val recM = if (useTimedRecoveryForShortTrack) null else 200
            listOf(
                distanceIntervalStep(0, "INTRO", 6, 400, recSec, recM, WorkoutTarget.INTERVALS, zones, archetype),
                distanceIntervalStep(1, "BUILD_1", 8, 400, recSec, recM, WorkoutTarget.INTERVALS, zones, archetype),
                distanceIntervalStep(2, "BUILD_2", 10, 400, recSec, recM, WorkoutTarget.INTERVALS, zones, archetype),
                distanceIntervalStep(3, "PEAK_1", 12, 400, recSec, recM, WorkoutTarget.INTERVALS, zones, archetype)
            )
        }
        Engine15WorkoutArchetype.CLASSIC_VO2MAX_INTERVALS -> if (preferDistanceBased) listOf(
            distanceIntervalStep(0, "INTRO", 4, 800, repRecoverySeconds = null, repRecoveryMeters = 400, WorkoutTarget.INTERVALS, zones, archetype),
            distanceIntervalStep(1, "BUILD_1", 5, 800, repRecoverySeconds = null, repRecoveryMeters = 400, WorkoutTarget.INTERVALS, zones, archetype),
            distanceIntervalStep(2, "BUILD_2", 4, 1000, repRecoverySeconds = null, repRecoveryMeters = 400, WorkoutTarget.INTERVALS, zones, archetype),
            distanceIntervalStep(3, "EXTENDED", 6, 800, repRecoverySeconds = null, repRecoveryMeters = 400, WorkoutTarget.INTERVALS, zones, archetype),
            distanceIntervalStep(4, "PEAK_1", 5, 1000, repRecoverySeconds = null, repRecoveryMeters = 400, WorkoutTarget.INTERVALS, zones, archetype)
        ) else listOf(
            intervalStep(0, "INTRO", 4, 3 * 60, WorkoutTarget.INTERVALS, repRecoverySeconds = 120, archetype = archetype),
            intervalStep(1, "BUILD_1", 5, 3 * 60, WorkoutTarget.INTERVALS, repRecoverySeconds = 120, archetype = archetype),
            intervalStep(2, "BUILD_2", 4, 4 * 60, WorkoutTarget.INTERVALS, repRecoverySeconds = 150, archetype = archetype),
            intervalStep(3, "EXTENDED", 6, 3 * 60, WorkoutTarget.INTERVALS, repRecoverySeconds = 120, archetype = archetype),
            intervalStep(4, "PEAK_1", 5, 4 * 60, WorkoutTarget.INTERVALS, repRecoverySeconds = 150, archetype = archetype)
        )
        Engine15WorkoutArchetype.SPEED_ENDURANCE_PYRAMID -> listOf(
            pyramidStep(
                0, "INTRO",
                listOf(
                    PyramidLeg(400, WorkoutTarget.INTERVALS, recoveryMeters = 200),
                    PyramidLeg(800, WorkoutTarget.INTERVALS, recoveryMeters = 400),
                    PyramidLeg(1200, WorkoutTarget.THRESHOLD, recoveryMeters = 400),
                    PyramidLeg(800, WorkoutTarget.INTERVALS, recoveryMeters = 200),
                    PyramidLeg(400, WorkoutTarget.INTERVALS)
                ),
                zones
            ),
            pyramidStep(
                1, "PEAK_1",
                listOf(
                    PyramidLeg(1000, WorkoutTarget.THRESHOLD, recoveryMeters = 400),
                    PyramidLeg(1600, WorkoutTarget.THRESHOLD, recoveryMeters = 600),
                    PyramidLeg(1000, WorkoutTarget.INTERVALS, recoveryMeters = 400),
                    PyramidLeg(800, WorkoutTarget.INTERVALS)
                ),
                zones
            )
        )
        else -> ladder(archetype.family ?: IndependentQualityFamily.TEMPO, experience)
    }

    private fun doseCeilingForArchetype(
        archetype: Engine15WorkoutArchetype,
        weeklyKm: Double,
        experience: Int,
        qualityCount: Int
    ): Pair<Int, Int> { // maxWorkMeters to maxWorkSeconds
        val exp = experience.coerceIn(0, 2)
        val perSessionShareFactor = if (qualityCount >= 2) 0.65 else 1.0
        val (pct, maxSec, floorMeters) = when (archetype) {
            Engine15WorkoutArchetype.SHORT_TRACK_REPEATS,
            Engine15WorkoutArchetype.CLASSIC_VO2MAX_INTERVALS,
            Engine15WorkoutArchetype.SPEED_ENDURANCE_PYRAMID -> Triple(
                when { exp >= 2 -> 0.09; exp == 1 -> 0.08; else -> 0.07 },
                if (qualityCount >= 2) 18 * 60 else if (exp >= 2) 26 * 60 else 22 * 60,
                3200
            )
            Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS -> Triple(
                when { exp >= 2 -> 0.13; exp == 1 -> 0.11; else -> 0.09 },
                if (exp >= 2) 40 * 60 else 32 * 60,
                if (exp >= 1) 4000 else 3000
            )
            Engine15WorkoutArchetype.CONTINUOUS_TEMPO,
            Engine15WorkoutArchetype.BROKEN_TEMPO,
            Engine15WorkoutArchetype.AEROBIC_FARTLEK -> Triple(
                when { exp >= 2 -> 0.15; exp == 1 -> 0.13; else -> 0.11 },
                when (exp) { 0 -> 20 * 60; 1 -> 38 * 60; else -> 45 * 60 },
                2500
            )
            Engine15WorkoutArchetype.SHORT_HILL_SPRINTS -> Triple(0.06, 8 * 60, 1200)
            Engine15WorkoutArchetype.LONG_AEROBIC_HILLS -> Triple(0.07, if (qualityCount >= 2) 10 * 60 else 12 * 60, 1500)
            else -> Triple(0.11, 32 * 60, 2500)
        }
        val cappedMeters = maxOf(floorMeters, kotlin.math.round(weeklyKm * 1000.0 * pct * perSessionShareFactor).toInt())
        return cappedMeters to maxSec
    }

    private fun familyWorkWeight(family: IndependentQualityFamily): Double = when (family) {
        IndependentQualityFamily.TEMPO,
        IndependentQualityFamily.PROGRESSION -> 1.00
        IndependentQualityFamily.THRESHOLD -> 0.85
        IndependentQualityFamily.FARTLEK -> 0.75
        IndependentQualityFamily.INTERVALS -> 0.65
        IndependentQualityFamily.HILLS -> 0.40
    }

    internal fun workTargetForFamily(family: IndependentQualityFamily): WorkoutTarget = when (family) {
        IndependentQualityFamily.TEMPO -> WorkoutTarget.TEMPO
        IndependentQualityFamily.PROGRESSION -> WorkoutTarget.PROGRESSION
        IndependentQualityFamily.THRESHOLD -> WorkoutTarget.THRESHOLD
        IndependentQualityFamily.INTERVALS -> WorkoutTarget.INTERVALS
        IndependentQualityFamily.HILLS -> WorkoutTarget.HILLS
        IndependentQualityFamily.FARTLEK -> WorkoutTarget.FARTLEK
    }

    internal fun recommendedMaxQualitySlotKm(
        request: PlanRequest,
        selectedFamily: IndependentQualityFamily,
        zones: Engine15ProvisionalPaceZones,
        adaptiveExposureSec: Int? = null
    ): Double {
        val anchorSec = adaptiveExposureSec
            ?: computeAdaptiveExposureAnchorSeconds(request, selectedFamily)
        val ceilingSec = maxOf(anchorSec + MAX_EXPOSURE_INCREMENT_SECONDS, (anchorSec * 1.35).roundToInt())
        val archPool = (adaptiveArchetypeRotation(request, selectedFamily) +
            phaseArchetypePool(request, selectedFamily, Engine15MacroPhase.BUILD)).distinct()
        val easyPace = zones.easy.midpointSecondsPerKm
        val slotKms = archPool.map { arch ->
            val fam = arch.family ?: selectedFamily
            val target = workTargetForFamily(fam)
            val workPace = zones.forTarget(target, arch).midpointSecondsPerKm
            val (_, archCeilingSec) = doseCeilingForArchetype(arch, request.weeklyKm.coerceAtLeast(20.0), request.experience, request.qualityCount.coerceIn(1, 2))
            val steps = adaptiveLadder(arch, request.experience, zones, preferDistanceBased = false)
            val chosen = steps.lastOrNull { it.workSeconds <= minOf(ceilingSec, archCeilingSec) } ?: steps.first()
            val workKm = if (chosen.workMeters > 0) chosen.workMeters / 1000.0 else chosen.workSeconds / workPace
            val recKm = (chosen.recoveryMeters / 1000.0) +
                (if (chosen.recoveryMeters > 0) 0.0 else chosen.recoverySeconds / easyPace)
            val bookendMin = if (chosen.workSeconds <= 15 * 60) 16 else 20
            workKm + recKm + (bookendMin * 60.0 / easyPace)
        }.sorted()
        return slotKms[slotKms.size / 2].coerceAtLeast(3.5)
    }

    private fun fitWarmupCooldownToSlotDistance(
        doseStep: Engine15QualityDoseStep,
        workTarget: WorkoutTarget,
        slotDistanceKm: Double,
        zones: Engine15ProvisionalPaceZones,
        timeBudgetSeconds: Int?,
        experience: Int
    ): Engine15QualityDoseStep {
        val minWarmupMin = 8
        val minCooldownMin = 5
        val minCombinedMin = minWarmupMin + minCooldownMin
        val isShortTimeCap = timeBudgetSeconds != null && timeBudgetSeconds <= 45 * 60
        val coreWorkRecMin = (doseStep.workSeconds + doseStep.recoverySeconds) / 60.0
        val maxWarmupMin = when {
            isShortTimeCap -> 10
            experience >= 1 && coreWorkRecMin < 18.0 -> 10
            coreWorkRecMin < 21.0 -> 12
            else -> 15
        }
        val maxCooldownMin = when {
            isShortTimeCap -> 6
            experience >= 1 && coreWorkRecMin < 18.0 -> 7
            coreWorkRecMin < 21.0 -> 8
            else -> 10
        }
        val hardMaxCombinedMin = maxWarmupMin + maxCooldownMin
        val easyPace = zones.easy.midpointSecondsPerKm
        val workPace = zones.forTarget(workTarget, doseStep.archetype).midpointSecondsPerKm
        val coreWorkMeters = if (doseStep.workMeters > 0) {
            doseStep.workMeters.toDouble()
        } else {
            doseStep.workSeconds * (1000.0 / workPace)
        }
        val timedRecSeconds = if (doseStep.recoveryMeters > 0) 0 else doseStep.recoverySeconds
        val coreRecMeters = doseStep.recoveryMeters.toDouble() + timedRecSeconds * (1000.0 / easyPace)
        val coreSeconds = doseStep.workSeconds + doseStep.recoverySeconds
        val targetEasyMeters = (slotDistanceKm * 1000.0 - coreWorkMeters - coreRecMeters).coerceAtLeast(0.0)
        val workRange = zones.forTarget(workTarget, doseStep.archetype)
        val maxAbsorbableWorkMeters = if (doseStep.workMeters > 0) {
            doseStep.workMeters.toDouble()
        } else {
            doseStep.workSeconds * (1000.0 / (workRange.fastSecondsPerKm * 0.83))
        }
        val maxAbsorbableRecMeters = doseStep.recoveryMeters.toDouble() +
            timedRecSeconds * (1000.0 / (zones.easy.fastSecondsPerKm * 0.83))
        val maxAbsorbableEasyMeters = (hardMaxCombinedMin * 60.0) * (1000.0 / (zones.easy.fastSecondsPerKm * 0.83))
        val maxTotalReachableMeters = maxAbsorbableWorkMeters + maxAbsorbableRecMeters + maxAbsorbableEasyMeters
        val slotMeters = slotDistanceKm * 1000.0
        val upperCombinedMin = if (slotMeters > maxTotalReachableMeters) {
            val remainingForEasy = (slotMeters - maxAbsorbableWorkMeters - maxAbsorbableRecMeters).coerceAtLeast(0.0)
            val overflowNeededMin = kotlin.math.ceil(
                remainingForEasy * (zones.easy.fastSecondsPerKm * 0.83) / 60_000.0
            ).toInt()
            maxOf(hardMaxCombinedMin, overflowNeededMin)
        } else {
            hardMaxCombinedMin
        }
        val maxCombinedMin = timeBudgetSeconds?.let {
            minOf(upperCombinedMin, maxOf(minCombinedMin, (it - coreSeconds) / 60))
        } ?: upperCombinedMin
        val desiredEasyMin = (targetEasyMeters * easyPace / 60_000.0).roundToInt()
            .coerceIn(minCombinedMin, maxCombinedMin)
        val wuCap = if (desiredEasyMin > hardMaxCombinedMin) (desiredEasyMin * 0.60).roundToInt().coerceAtLeast(maxWarmupMin) else maxWarmupMin
        val cdCap = if (desiredEasyMin > hardMaxCombinedMin) (desiredEasyMin - wuCap).coerceAtLeast(maxCooldownMin) else maxCooldownMin
        val wuMin = (desiredEasyMin * 0.60).roundToInt()
            .coerceIn(minWarmupMin, minOf(wuCap, desiredEasyMin - minCooldownMin).coerceAtLeast(minWarmupMin))
        val cdMin = (desiredEasyMin - wuMin).coerceIn(minCooldownMin, cdCap)
        val wuSec = wuMin * 60
        val cdSec = cdMin * 60
        val elements = doseStep.workout.elements
        if (elements.size < 2) return doseStep
        val updatedElements = buildList {
            add(IndependentWorkoutElement.Step(step(WorkoutPurpose.WARM_UP, wuSec, WorkoutTarget.EASY)))
            addAll(elements.subList(1, elements.size - 1))
            add(IndependentWorkoutElement.Step(step(WorkoutPurpose.COOL_DOWN, cdSec, WorkoutTarget.EASY)))
        }
        return doseStep.copy(
            workout = IndependentWorkoutComposition(updatedElements),
            totalSeconds = wuSec + coreSeconds + cdSec
        )
    }

    internal fun phaseArchetypePool(
        request: PlanRequest,
        primaryFamily: IndependentQualityFamily,
        phase: Engine15MacroPhase
    ): List<Engine15WorkoutArchetype> {
        val fallbackPool = adaptiveArchetypeRotation(request, primaryFamily)
        if (request.preferredQualityExplicit) return fallbackPool
        if (request.experience == 0 || request.returning) return fallbackPool

        val raw = when (phase) {
            Engine15MacroPhase.BASE -> when (request.goal) {
                "Improve 5K", "Aerobic foundation" -> listOf(
                    Engine15WorkoutArchetype.AEROBIC_FARTLEK,
                    Engine15WorkoutArchetype.SHORT_HILL_SPRINTS,
                    Engine15WorkoutArchetype.CONTINUOUS_TEMPO,
                    Engine15WorkoutArchetype.LONG_AEROBIC_HILLS
                )
                else -> listOf(
                    Engine15WorkoutArchetype.CONTINUOUS_TEMPO,
                    Engine15WorkoutArchetype.AEROBIC_FARTLEK,
                    Engine15WorkoutArchetype.LONG_AEROBIC_HILLS,
                    Engine15WorkoutArchetype.SHORT_HILL_SPRINTS
                )
            }
            Engine15MacroPhase.BUILD -> when (request.goal) {
                "Improve 5K" -> listOf(
                    Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS,
                    Engine15WorkoutArchetype.SHORT_TRACK_REPEATS,
                    Engine15WorkoutArchetype.LONG_AEROBIC_HILLS,
                    Engine15WorkoutArchetype.CLASSIC_VO2MAX_INTERVALS,
                    Engine15WorkoutArchetype.BROKEN_TEMPO
                )
                "Improve 10K" -> listOf(
                    Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS,
                    Engine15WorkoutArchetype.BROKEN_TEMPO,
                    Engine15WorkoutArchetype.CLASSIC_VO2MAX_INTERVALS,
                    Engine15WorkoutArchetype.CONTINUOUS_TEMPO
                )
                "Half marathon" -> listOf(
                    Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS,
                    Engine15WorkoutArchetype.BROKEN_TEMPO,
                    Engine15WorkoutArchetype.CONTINUOUS_TEMPO,
                    Engine15WorkoutArchetype.LONG_AEROBIC_HILLS
                )
                "Marathon" -> listOf(
                    Engine15WorkoutArchetype.BROKEN_TEMPO,
                    Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS,
                    Engine15WorkoutArchetype.CONTINUOUS_TEMPO,
                    Engine15WorkoutArchetype.LONG_AEROBIC_HILLS
                )
                "Aerobic foundation" -> listOf(
                    Engine15WorkoutArchetype.AEROBIC_FARTLEK,
                    Engine15WorkoutArchetype.LONG_AEROBIC_HILLS,
                    Engine15WorkoutArchetype.SHORT_HILL_SPRINTS,
                    Engine15WorkoutArchetype.CONTINUOUS_TEMPO
                )
                else -> listOf( // "General fitness"
                    Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS,
                    Engine15WorkoutArchetype.BROKEN_TEMPO,
                    Engine15WorkoutArchetype.AEROBIC_FARTLEK,
                    Engine15WorkoutArchetype.CLASSIC_VO2MAX_INTERVALS,
                    Engine15WorkoutArchetype.LONG_AEROBIC_HILLS
                )
            }
            Engine15MacroPhase.PEAK -> when (request.goal) {
                "Improve 5K" -> if (request.experience >= 2) listOf(
                    Engine15WorkoutArchetype.SPEED_ENDURANCE_PYRAMID,
                    Engine15WorkoutArchetype.SHORT_TRACK_REPEATS,
                    Engine15WorkoutArchetype.CLASSIC_VO2MAX_INTERVALS
                ) else listOf(
                    Engine15WorkoutArchetype.SHORT_TRACK_REPEATS,
                    Engine15WorkoutArchetype.CLASSIC_VO2MAX_INTERVALS,
                    Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS
                )
                "Improve 10K" -> if (request.experience >= 2) listOf(
                    Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS,
                    Engine15WorkoutArchetype.SPEED_ENDURANCE_PYRAMID,
                    Engine15WorkoutArchetype.CLASSIC_VO2MAX_INTERVALS
                ) else listOf(
                    Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS,
                    Engine15WorkoutArchetype.CLASSIC_VO2MAX_INTERVALS,
                    Engine15WorkoutArchetype.BROKEN_TEMPO
                )
                "Half marathon" -> if (request.experience >= 2) listOf(
                    Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS,
                    Engine15WorkoutArchetype.BROKEN_TEMPO,
                    Engine15WorkoutArchetype.SPEED_ENDURANCE_PYRAMID
                ) else listOf(
                    Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS,
                    Engine15WorkoutArchetype.BROKEN_TEMPO,
                    Engine15WorkoutArchetype.CONTINUOUS_TEMPO
                )
                "Marathon" -> listOf(
                    Engine15WorkoutArchetype.BROKEN_TEMPO,
                    Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS,
                    Engine15WorkoutArchetype.CONTINUOUS_TEMPO
                )
                else -> fallbackPool
            }
            Engine15MacroPhase.TAPER, Engine15MacroPhase.RECOVERY -> when (request.goal) {
                "Improve 5K" -> listOf(
                    Engine15WorkoutArchetype.SHORT_TRACK_REPEATS,
                    Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS
                )
                else -> listOf(
                    Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS,
                    Engine15WorkoutArchetype.CONTINUOUS_TEMPO
                )
            }
        }
        val filtered = if (request.allowedQualityTypes.isEmpty()) raw else raw.filter { arch ->
            val famName = arch.family?.name ?: return@filter false
            request.allowedQualityTypes.any { it.equals(famName, ignoreCase = true) }
        }
        return filtered.ifEmpty { fallbackPool }.distinct()
    }

    fun plan(
        request: PlanRequest,
        selection: Engine15QualityFamilySelectionResult,
        assertedExposureSecondsOverride: Int? = null,
        adaptiveQualityMode: Boolean = false,
        taperWeeks: Set<Int> = emptySet(),
        weeklyBudgetsByWeek: Map<Int, Double> = emptyMap(),
        phasesByWeek: Map<Int, Engine15MacroPhase> = emptyMap()
    ): List<Engine15QualityWeekPrescription> {
        val family = selection.selectedFamily ?: return emptyList()
        val slots = selection.candidateSlots
        if (slots.isEmpty()) return emptyList()
        val steps = ladder(family, request.experience)
        val intro = steps.first()
        val timeBudgetSeconds = if (request.timeBudgetEnabled) request.minutes * 60 else null
        val isRaceGoal = request.goal != "Aerobic foundation" && request.goal != "General fitness"
        val lastLoadingWeek = slots.maxOf { it.weekNumber }
        val repeatedBaseline = when {
            assertedExposureSecondsOverride != null && assertedExposureSecondsOverride in 1..14400 &&
                (selection.assertedRepeatedWorkSeconds == null ||
                    assertedExposureSecondsOverride <= selection.assertedRepeatedWorkSeconds) ->
                assertedExposureSecondsOverride
            selection.exposureConfidence == IndependentQualityExposureConfidence.REPEATED_DEVICE_RECORDS_ASSERTED &&
                selection.assertedRepeatedWorkSeconds != null -> selection.assertedRepeatedWorkSeconds
            assertedExposureSecondsOverride != null && assertedExposureSecondsOverride in 1..14400 ->
                assertedExposureSecondsOverride
            else -> null
        }

        if (repeatedBaseline == null) {
            return slots.map { slot ->
                Engine15QualityWeekPrescription(
                    policyId = POLICY_ID,
                    weekNumber = slot.weekNumber,
                    family = family,
                    step = intro,
                    basis = Engine15QualityProgressionBasis.HOLD_NO_REPEATED_EXPOSURE,
                    ruleIds = listOf(
                        "HOLD_INTRO_UNVERIFIED_EXPOSURE",
                        "NO_AUTOMATIC_PROGRESSION_FROM_ELAPSED_WEEKS"
                    ),
                    rationale = "Held at ${intro.stageLabel} (${intro.workSeconds / 60} min work) because repeated same-family exposure is unavailable or unverified.",
                    slotNumber = slot.slotNumber
                )
            }
        }

        val adaptiveOverrideActive = adaptiveQualityMode &&
            assertedExposureSecondsOverride != null &&
            selection.exposureConfidence != IndependentQualityExposureConfidence.REPEATED_DEVICE_RECORDS_ASSERTED
        val maxPermittedWork = if (adaptiveOverrideActive) {
            maxOf(repeatedBaseline + MAX_EXPOSURE_INCREMENT_SECONDS, (repeatedBaseline * 1.35).roundToInt())
        } else {
            repeatedBaseline + MAX_EXPOSURE_INCREMENT_SECONDS
        }
        val anchorIndex = steps.indexOfLast { it.workSeconds <= repeatedBaseline }.coerceAtLeast(0)
        val anchorRatio = if (steps.lastIndex > 0) anchorIndex.toDouble() / steps.lastIndex.toDouble() else 0.0
        val deterministicZones = if (adaptiveQualityMode) Engine15PaceZoneEstimator.estimate(request) else null

        if (adaptiveQualityMode && deterministicZones != null) {
            val archRotation = adaptiveArchetypeRotation(request, family)
            val isConservativeAthlete = request.experience == 0 || request.returning
            val distinctLoadingWeeks = slots.map { it.weekNumber }.distinct().sorted()
            val ordinalByWeek = distinctLoadingWeeks.withIndex().associate { (i, w) -> w to i }
            val archCounts = mutableMapOf<Engine15WorkoutArchetype, Int>()
            val phaseSlotCounts = mutableMapOf<Engine15MacroPhase, Int>()
            val usedFamiliesByWeek = mutableMapOf<Int, MutableSet<IndependentQualityFamily>>()

            return slots.mapIndexed { slotIndex, slot ->
                val weekOrdinal = ordinalByWeek[slot.weekNumber] ?: slotIndex
                val weekPhase = phasesByWeek[slot.weekNumber]
                    ?: if ((slot.weekNumber in taperWeeks) ||
                        (isRaceGoal && request.weeks >= 8 && slot.weekNumber == lastLoadingWeek)
                    ) Engine15MacroPhase.TAPER else Engine15MacroPhase.BUILD
                val isRaceTaperWeek = weekPhase == Engine15MacroPhase.TAPER
                val phasePool = if (phasesByWeek.isNotEmpty()) {
                    phaseArchetypePool(request, family, weekPhase)
                } else {
                    archRotation
                }
                val phaseSlotIdx = phaseSlotCounts[weekPhase] ?: 0
                phaseSlotCounts[weekPhase] = phaseSlotIdx + 1

                val rawPrimaryArch = when {
                    phasePool.size == 1 -> phasePool.first()
                    isRaceTaperWeek -> phasePool.first()
                    isConservativeAthlete && weekOrdinal < 2 -> phasePool.first()
                    isConservativeAthlete -> phasePool[((slotIndex - 1).coerceAtLeast(0)) % phasePool.size]
                    phasesByWeek.isNotEmpty() -> phasePool[phaseSlotIdx % phasePool.size]
                    slotIndex == 0 -> phasePool.first()
                    else -> phasePool[slotIndex % phasePool.size]
                }
                val usedInWeek = usedFamiliesByWeek.getOrPut(slot.weekNumber) { mutableSetOf() }
                val rawFam = rawPrimaryArch.family ?: family
                val primaryArch = if (rawFam in usedInWeek) {
                    (phasePool + archRotation).distinct().firstOrNull { cand ->
                        (cand.family ?: family) !in usedInWeek
                    } ?: rawPrimaryArch
                } else {
                    rawPrimaryArch
                }
                val occurrenceIdx = archCounts[primaryArch] ?: 0
                archCounts[primaryArch] = occurrenceIdx + 1

                fun fitsSlot(cand: Engine15QualityDoseStep, arch: Engine15WorkoutArchetype): Boolean {
                    val target = workTargetForFamily(arch.family ?: family)
                    val easyPace = deterministicZones.easy.midpointSecondsPerKm
                    val workPace = deterministicZones.forTarget(target, arch).midpointSecondsPerKm
                    val minEasyMeters = 480.0 * (1000.0 / easyPace)
                    val workM = if (cand.workMeters > 0) cand.workMeters.toDouble() else cand.workSeconds * (1000.0 / workPace)
                    val recM = cand.recoveryMeters.toDouble() +
                        (if (cand.recoveryMeters > 0) 0 else cand.recoverySeconds) * (1000.0 / easyPace)
                    val slotMeters = slot.originalEasyDistanceKm * 1000.0
                    if (workM + recM + minEasyMeters > slotMeters * 1.05) return false
                    if (timeBudgetSeconds != null && (780 + cand.workSeconds + cand.recoverySeconds > timeBudgetSeconds)) {
                        return false
                    }
                    return true
                }

                fun canReachSlot(cand: Engine15QualityDoseStep, arch: Engine15WorkoutArchetype): Boolean {
                    val target = workTargetForFamily(arch.family ?: family)
                    val easyFastPace = deterministicZones.easy.fastSecondsPerKm
                    val easyMidPace = deterministicZones.easy.midpointSecondsPerKm
                    val workFastPace = deterministicZones.forTarget(target, arch).fastSecondsPerKm
                    val coreMin = (cand.workSeconds + cand.recoverySeconds) / 60.0
                    val maxBookendSec = when {
                        timeBudgetSeconds != null && timeBudgetSeconds <= 45 * 60 -> 16 * 60.0
                        coreMin < 21.0 -> 20 * 60.0
                        else -> 25 * 60.0
                    }
                    val maxEasyMeters = maxBookendSec * (1000.0 / (easyFastPace * 0.86))
                    val workM = if (cand.workMeters > 0) cand.workMeters.toDouble() else cand.workSeconds * (1000.0 / (workFastPace * 0.96))
                    val recM = cand.recoveryMeters.toDouble() +
                        (if (cand.recoveryMeters > 0) 0 else cand.recoverySeconds) * (1000.0 / easyMidPace)
                    val slotMeters = slot.originalEasyDistanceKm * 1000.0
                    return workM + recM + maxEasyMeters >= slotMeters * 0.96
                }

                val preferDist = occurrenceIdx % 2 == 0
                val useTimedShortTrack = occurrenceIdx % 2 == 1
                var chosenArch = primaryArch
                var archSteps = adaptiveLadder(
                    chosenArch, request.experience, deterministicZones,
                    preferDistanceBased = preferDist,
                    useTimedRecoveryForShortTrack = useTimedShortTrack
                )
                // If distance-based entry rung does not fit physical slot/time budget, try timed ladder then fallback archetype.
                if (!fitsSlot(archSteps.first(), chosenArch)) {
                    if (chosenArch in setOf(
                            Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS,
                            Engine15WorkoutArchetype.CLASSIC_VO2MAX_INTERVALS
                        ) && preferDist
                    ) {
                        val timedAlt = adaptiveLadder(chosenArch, request.experience, deterministicZones, preferDistanceBased = false)
                        if (fitsSlot(timedAlt.first(), chosenArch)) {
                            archSteps = timedAlt
                        }
                    }
                    if (!fitsSlot(archSteps.first(), chosenArch)) {
                        val sameFamAlt = (phasePool + archRotation).distinct().firstOrNull { candArch ->
                            candArch.family == chosenArch.family &&
                                fitsSlot(adaptiveLadder(candArch, request.experience, deterministicZones, false).first(), candArch)
                        } ?: (phasePool + archRotation).distinct().firstOrNull { candArch ->
                            fitsSlot(adaptiveLadder(candArch, request.experience, deterministicZones, false).first(), candArch)
                        }
                        if (sameFamAlt != null) {
                            chosenArch = sameFamAlt
                            archSteps = adaptiveLadder(chosenArch, request.experience, deterministicZones, false)
                        }
                    }
                }
                val slotWeekKm = weeklyBudgetsByWeek[slot.weekNumber] ?: request.weeklyKm
                val (_, chosenCeilSec) = doseCeilingForArchetype(
                    chosenArch, slotWeekKm, request.experience, request.qualityCount
                )
                if (archSteps.none { it.workSeconds <= chosenCeilSec && fitsSlot(it, chosenArch) && canReachSlot(it, chosenArch) }) {
                    val reachPool = (phasePool + archRotation + listOf(
                        Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS,
                        Engine15WorkoutArchetype.CONTINUOUS_TEMPO,
                        Engine15WorkoutArchetype.BROKEN_TEMPO
                    )).distinct()
                    val reachableAlt = reachPool.firstOrNull { candArch ->
                        val (_, candCeilSec) = doseCeilingForArchetype(
                            candArch, slotWeekKm, request.experience, request.qualityCount
                        )
                        (candArch.family ?: family) !in usedInWeek &&
                            adaptiveLadder(candArch, request.experience, deterministicZones, false)
                                .any { it.workSeconds <= candCeilSec && fitsSlot(it, candArch) && canReachSlot(it, candArch) }
                    } ?: reachPool.firstOrNull { candArch ->
                        val (_, candCeilSec) = doseCeilingForArchetype(
                            candArch, slotWeekKm, request.experience, request.qualityCount
                        )
                        adaptiveLadder(candArch, request.experience, deterministicZones, false)
                            .any { it.workSeconds <= candCeilSec && fitsSlot(it, candArch) && canReachSlot(it, candArch) }
                    }
                    if (reachableAlt != null) {
                        chosenArch = reachableAlt
                        archSteps = adaptiveLadder(chosenArch, request.experience, deterministicZones, false)
                    }
                }

                val weekFamily = chosenArch.family ?: family
                usedInWeek.add(weekFamily)
                val weekIntro = archSteps.first()
                val weekAnchorIdx = (anchorRatio * archSteps.lastIndex).roundToInt().coerceIn(0, archSteps.lastIndex)
                val blockIndex = (slot.weekNumber - 1) / 4
                val weekInBlock = (slot.weekNumber - 1) % 4
                val minReachIdx = archSteps.indexOfFirst { fitsSlot(it, chosenArch) && canReachSlot(it, chosenArch) }
                    .takeIf { it >= 0 } ?: 0
                val minNonNoviceIdx = if (request.experience >= 1 && !isRaceTaperWeek &&
                    weekFamily in setOf(
                        IndependentQualityFamily.TEMPO,
                        IndependentQualityFamily.THRESHOLD,
                        IndependentQualityFamily.FARTLEK,
                        IndependentQualityFamily.INTERVALS
                    )
                ) {
                    maxOf(
                        minReachIdx,
                        archSteps.indexOfFirst { (it.workSeconds + it.recoverySeconds) >= 18 * 60 }
                            .takeIf { it >= 0 } ?: 0
                    )
                } else {
                    minReachIdx
                }
                val desiredIndex = when {
                    isRaceTaperWeek -> maxOf(minReachIdx, minOf(weekAnchorIdx, 1).coerceAtLeast(0))
                        .coerceAtMost(archSteps.lastIndex)
                    isConservativeAthlete && weekOrdinal < 2 -> minReachIdx
                    isConservativeAthlete -> ((weekOrdinal - 1) / 2).coerceIn(maxOf(1, minReachIdx), archSteps.lastIndex)
                    else -> (weekAnchorIdx + blockIndex + weekInBlock).coerceIn(minNonNoviceIdx, archSteps.lastIndex)
                }

                val weekKm = weeklyBudgetsByWeek[slot.weekNumber] ?: request.weeklyKm
                val (ceilingMeters, ceilingSeconds) = doseCeilingForArchetype(
                    chosenArch, weekKm, request.experience, request.qualityCount
                )
                val exposureCapSec = if (isConservativeAthlete && weekOrdinal < 2) {
                    weekIntro.workSeconds
                } else {
                    minOf(
                        maxOf(weekIntro.workSeconds, (maxPermittedWork * (familyWorkWeight(weekFamily) / familyWorkWeight(family)).coerceAtMost(1.15)).roundToInt()),
                        ceilingSeconds
                    )
                }
                val workTarget = workTargetForFamily(weekFamily)
                val workPace = deterministicZones.forTarget(workTarget, chosenArch).midpointSecondsPerKm
                val candidatesInWindow = archSteps.take(desiredIndex + 1).filter { cand ->
                    val candMeters = if (cand.workMeters > 0) cand.workMeters else kotlin.math.round(cand.workSeconds * 1000.0 / workPace).toInt()
                    cand.stepIndex == 0 || (cand.workSeconds <= exposureCapSec && candMeters <= ceilingMeters)
                }.ifEmpty { listOf(weekIntro) }
                val reachableCandidates = if (candidatesInWindow.none { canReachSlot(it, chosenArch) }) {
                    archSteps.firstOrNull { it.workSeconds <= ceilingSeconds && fitsSlot(it, chosenArch) && canReachSlot(it, chosenArch) }
                        ?.let { listOf(it) } ?: candidatesInWindow
                } else {
                    candidatesInWindow
                }

                val timeBounded = if (timeBudgetSeconds != null) {
                    reachableCandidates.filter { cand ->
                        780 + cand.workSeconds + cand.recoverySeconds <= timeBudgetSeconds
                    }.ifEmpty { listOf(weekIntro) }
                } else reachableCandidates

                val distanceFitted = timeBounded.lastOrNull { cand ->
                    val minCoreSec = if (request.experience >= 1 && !isRaceTaperWeek &&
                        weekFamily in setOf(
                            IndependentQualityFamily.TEMPO,
                            IndependentQualityFamily.THRESHOLD,
                            IndependentQualityFamily.FARTLEK,
                            IndependentQualityFamily.INTERVALS
                        )
                    ) 18 * 60 else 0
                    (cand.workSeconds + cand.recoverySeconds) >= minCoreSec &&
                        fitsSlot(cand, chosenArch) && canReachSlot(cand, chosenArch)
                } ?: timeBounded.lastOrNull { cand ->
                    fitsSlot(cand, chosenArch) && canReachSlot(cand, chosenArch)
                } ?: timeBounded.lastOrNull { cand -> fitsSlot(cand, chosenArch) } ?: weekIntro
                val finalStep = fitWarmupCooldownToSlotDistance(
                    doseStep = distanceFitted,
                    workTarget = workTarget,
                    slotDistanceKm = slot.originalEasyDistanceKm,
                    zones = deterministicZones,
                    timeBudgetSeconds = timeBudgetSeconds,
                    experience = request.experience
                )
                val timeCapped = timeBounded.last().stepIndex < candidatesInWindow.last().stepIndex
                val basis = when {
                    isRaceTaperWeek -> Engine15QualityProgressionBasis.RACE_TAPER_REDUCTION
                    timeCapped -> Engine15QualityProgressionBasis.TIME_BUDGET_CAPPED
                    else -> Engine15QualityProgressionBasis.EVIDENCE_BOUNDED_PROGRESSION
                }
                val rules = buildList {
                    add("REPEATED_SAME_FAMILY_EXPOSURE_ASSERTED")
                    add("BOUNDED_INCREMENT_CEILING_APPLIED")
                    add("ENERGY_SYSTEM_RECOVERY_RATIOS_APPLIED")
                    add("MACRO_PHASE_${weekPhase.name}")
                    if (isRaceTaperWeek) add("RACE_TAPER_QUALITY_HOLD")
                    if (timeCapped) add("SESSION_TIME_BUDGET_CAP_APPLIED")
                    if (isConservativeAthlete) add("CONSERVATIVE_ENTRY_HOLD_APPLIED")
                }
                val workLabel = if (finalStep.workMeters > 0) {
                    "${finalStep.workMeters} m work"
                } else {
                    "${(finalStep.workSeconds / 60.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(java.util.Locale.US, it) }} min work"
                }
                val rationale = when (basis) {
                    Engine15QualityProgressionBasis.RACE_TAPER_REDUCTION ->
                        "[${weekPhase.name}] Taper hold at ${chosenArch.displayTitle} (${finalStep.stageLabel}, $workLabel) before '${request.goal}'."
                    Engine15QualityProgressionBasis.TIME_BUDGET_CAPPED ->
                        "[${weekPhase.name}] ${chosenArch.displayTitle} (${finalStep.stageLabel}, $workLabel) capped to fit the ${request.minutes}-minute session budget."
                    else ->
                        "[${weekPhase.name}] ${chosenArch.displayTitle} (${finalStep.stageLabel}, $workLabel) dosed within weekly energy-system ceilings."
                }
                Engine15QualityWeekPrescription(
                    policyId = POLICY_ID,
                    weekNumber = slot.weekNumber,
                    family = weekFamily,
                    step = finalStep,
                    basis = basis,
                    ruleIds = rules,
                    rationale = rationale,
                    slotNumber = slot.slotNumber
                )
            }
        }

        val rotateFamilies = false
        val rotation = listOf(family)

        return slots.mapIndexed { slotIndex, slot ->
            val weekFamily = rotation[slotIndex % rotation.size]
            val weekSteps = if (weekFamily == family) steps else ladder(weekFamily, request.experience)
            val weekIntro = weekSteps.first()
            val weekAnchorIndex = if (weekFamily == family) anchorIndex else {
                (anchorRatio * weekSteps.lastIndex).roundToInt().coerceIn(0, weekSteps.lastIndex)
            }
            val blockIndex = (slot.weekNumber - 1) / 4
            val weekInBlock = (slot.weekNumber - 1) % 4
            val isRaceTaperWeek = (slot.weekNumber in taperWeeks) ||
                (isRaceGoal && request.weeks >= 8 && slot.weekNumber == lastLoadingWeek)
            val desiredIndex = if (isRaceTaperWeek) {
                (weekAnchorIndex - 1).coerceAtLeast(0)
            } else {
                (weekAnchorIndex + blockIndex + (weekInBlock / 2)).coerceAtMost(weekSteps.lastIndex)
            }
            val rotatedCap = if (weekFamily == family) {
                maxPermittedWork
            } else {
                (maxPermittedWork * (familyWorkWeight(weekFamily) / familyWorkWeight(family)).coerceAtMost(1.0)).roundToInt()
            }
            val boundedCap = if (weekFamily != family) {
                minOf(rotatedCap, familyMaxWorkSeconds(weekFamily, request.experience, request.qualityCount))
            } else {
                maxPermittedWork
            }
            val weekMaxWork = maxOf(weekIntro.workSeconds, boundedCap)
            val exposureBounded = weekSteps.take(desiredIndex + 1)
                .lastOrNull { it.workSeconds <= weekMaxWork } ?: weekIntro
            val budgetBounded = if (timeBudgetSeconds != null) {
                weekSteps.take(exposureBounded.stepIndex + 1)
                    .lastOrNull { it.totalSeconds <= timeBudgetSeconds } ?: weekIntro
            } else {
                exposureBounded
            }
            val finalStep = budgetBounded
            val timeCapped = budgetBounded.stepIndex < exposureBounded.stepIndex
            val basis = when {
                isRaceTaperWeek -> Engine15QualityProgressionBasis.RACE_TAPER_REDUCTION
                timeCapped -> Engine15QualityProgressionBasis.TIME_BUDGET_CAPPED
                else -> Engine15QualityProgressionBasis.EVIDENCE_BOUNDED_PROGRESSION
            }
            val rules = buildList {
                add("REPEATED_SAME_FAMILY_EXPOSURE_ASSERTED")
                add("BOUNDED_INCREMENT_CEILING_APPLIED")
                if (isRaceTaperWeek) add("RACE_TAPER_QUALITY_HOLD")
                if (timeCapped) add("SESSION_TIME_BUDGET_CAP_APPLIED")
                add("NO_PACE_OR_HR_TARGET_INFERRED")
            }
            val rationale = when (basis) {
                Engine15QualityProgressionBasis.RACE_TAPER_REDUCTION ->
                    "Taper hold at ${finalStep.stageLabel} (${finalStep.workSeconds / 60} min work) for the final loading week before '${request.goal}'."
                Engine15QualityProgressionBasis.TIME_BUDGET_CAPPED ->
                    "Capped at ${finalStep.stageLabel} (${finalStep.workSeconds / 60} min work) to fit the ${request.minutes}-minute session budget."
                else ->
                    "Adaptive 50/50 history & fitness ${finalStep.stageLabel} (${finalStep.workSeconds / 60} min work) anchored to ${repeatedBaseline / 60} min baseline."
            }
            Engine15QualityWeekPrescription(
                policyId = POLICY_ID,
                weekNumber = slot.weekNumber,
                family = weekFamily,
                step = finalStep,
                basis = basis,
                ruleIds = rules,
                rationale = rationale,
                slotNumber = slot.slotNumber
            )
        }
    }
}
