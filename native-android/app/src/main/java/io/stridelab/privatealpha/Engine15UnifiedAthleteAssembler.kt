package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure input bundle for uniting M1-A..F and M2-A..C evidence into a single deterministic
 * athlete assessment without mutating SQLite stores or leaking cross-owner records.
 */
internal data class UnifiedAthleteAssemblyInput(
    val accountId: String,
    val evaluatedAtUtc: Instant,
    val zoneId: ZoneId,
    val activityRecords: List<ActivityRecord> = emptyList(),
    val activityDetails: Map<String, ActivityDetail> = emptyMap(),
    val freeformWorkouts: List<FreeformWorkout> = emptyList(),
    val existingPlans: List<PlanSnapshot> = emptyList(),
    val racePerformances: List<M2ARacePerformanceInput> = emptyList(),
    val hrZoneProfile: ExecutionHrZoneProfile? = null,
    val provisionalSelfReport: Map<AthleteDiscipline, ProvisionalSelfReportWorkload> = emptyMap(),
    val weeklyConfirmations: List<M2CWeeklyConfirmationInput> = emptyList(),
    val confirmedInterruption: M2CConfirmedInterruptionInput? = null,
    val longRunDurationCap: Seconds = Seconds.of(Engine15M2CPolicyRegistry.POL_030_SOFT_LONG_RUN_DURATION_CAP_SECONDS),
    val ageYears: Int? = null,
    val localStore: Engine15M1LocalStore? = null,
    val persistDerivedAssessment: Boolean = true
) {
    init {
        RecordPolicy.identifier(accountId)
    }
}

/**
 * Immutable result combining M1 canonical evidence, persistent safety restrictions,
 * observed vs. projected workload separation, M2-A performance calibration, M2-B
 * intensity calibration, and M2-C multidimensional workload intelligence.
 */
internal data class UnifiedAthleteAssessment(
    val accountId: String,
    val evaluatedAtUtc: Instant,
    val asOfDate: LocalDate,
    val zoneId: ZoneId,
    val canonicalActivities: List<CanonicalActivity>,
    val unresolvedAmbiguityCount: Int,
    val observedWorkload: ObservedWorkloadState,
    val projectedWorkload: ProjectedWorkloadState,
    val restrictionLoadResult: M1RestrictionLoadResult,
    val athleteSnapshot: AthleteStateSnapshot,
    val performanceCalibration: M2APerformanceCalibrationResult,
    val intensityCalibration: Engine15M2BIntensityCalibrator,
    val workloadAssessment: Engine15M2CWorkloadAssessment,
    val persistedAssessmentRecord: M1AssessmentRecord?
) {
    val activeRestrictions: AthleteSafetyRestrictionSet
        get() = restrictionLoadResult.restrictions

    val restrictionsFailClosed: Boolean
        get() = restrictionLoadResult.failClosedSafetyActive

    fun hasHardBlock(discipline: AthleteDiscipline): Boolean =
        restrictionsFailClosed || activeRestrictions.hasHardBlock(discipline, evaluatedAtUtc)
}

/**
 * Deterministic M1-A..F + M2-A..C evidence assembly adapter and plan-request safety boundary.
 *
 * Safeguards:
 * - Strict owner isolation (INV-03) and local-only assessment caching via [Engine15M1LocalStore].
 * - Reads completed Freeform/Custom workouts read-only without mutating [ManualDisciplineStore].
 * - Never fabricates 220 - age HR zones or synthetic weekly running baselines.
 */
internal object Engine15UnifiedAthleteAssembler {

    fun assemble(input: UnifiedAthleteAssemblyInput): UnifiedAthleteAssessment {
        val owner = input.accountId
        val asOfDate = input.evaluatedAtUtc.atZone(input.zoneId).toLocalDate()

        val restrictionLoad = input.localStore?.loadRestrictions(owner)
            ?: M1RestrictionLoadResult(
                restrictions = AthleteSafetyRestrictionSet.empty(owner),
                corruptRecordIds = emptyList(),
                unsupportedSchemaRecordIds = emptyList(),
                olderPolicyRecordIds = emptyList()
            )
        val activeRestrictionIds = restrictionLoad.restrictions.activeRestrictionIdsAt(input.evaluatedAtUtc)

        val observations = buildList {
            for (record in input.activityRecords) {
                if (record.source == "demo") continue
                val detail = input.activityDetails[record.id]
                add(
                    RawActivityObservation(
                        accountId = owner,
                        record = record,
                        detail = detail
                    )
                )
            }
            for (workout in input.freeformWorkouts) {
                toFreeformObservation(owner, workout, input.evaluatedAtUtc)?.let(::add)
            }
        }

        val projection = Engine15CanonicalActivityProjector.project(observations)
        val ownedCanonical = projection.activities.filter {
            it.accountId == owner && !it.startedAtUtc.isAfter(input.evaluatedAtUtc)
        }

        val completedPlanSessions = input.existingPlans
            .asSequence()
            .filter { it.owner == owner }
            .flatMap { it.sessions.asSequence() }
            .filter { it.status == "completed" && it.logRevision > 0 }
            .toList()

        val observedWorkload = Engine15ObservedWorkloadAssembler.assemble(
            accountId = owner,
            asOfDate = asOfDate,
            zoneId = input.zoneId,
            canonicalActivities = ownedCanonical,
            completedPlanSessions = completedPlanSessions,
            provisionalSelfReport = input.provisionalSelfReport,
            asOfUtc = input.evaluatedAtUtc
        )

        val projectedWorkload = Engine15ProjectedWorkloadAssembler.assembleFromPlans(
            accountId = owner,
            asOfDate = asOfDate,
            plans = input.existingPlans
        )

        val evidenceIds = ownedCanonical.mapTo(LinkedHashSet()) { it.canonicalId }
        val snapshot = AthleteStateSnapshot.fromWorkloads(
            accountId = owner,
            evaluatedAtUtc = input.evaluatedAtUtc,
            observedWorkload = observedWorkload,
            projectedWorkload = projectedWorkload,
            evidenceIds = evidenceIds,
            activeRestrictionIds = activeRestrictionIds
        )

        val derivedRecord = if (input.persistDerivedAssessment && input.localStore != null) {
            M1AssessmentRecord.fromSnapshot(snapshot).also { record ->
                input.localStore.saveDerivedAssessment(owner, record, input.evaluatedAtUtc.toEpochMilli())
            }
        } else {
            null
        }

        val ownedPerformances = input.racePerformances.filter {
            it.accountId == owner && it.discipline == AthleteDiscipline.RUN
        }
        val m2aRequest = M2APerformanceCalibrationRequest(
            accountId = owner,
            asOfDate = asOfDate,
            zoneId = input.zoneId,
            observedWorkload = observedWorkload,
            racePerformances = ownedPerformances,
            asOfUtc = input.evaluatedAtUtc,
            athleteStateSnapshot = snapshot
        )
        val performanceCalibration = Engine15M2APerformanceCalibrator.calibrate(m2aRequest)

        val m2bContext = M2BEvaluationContext(
            accountId = owner,
            discipline = AthleteDiscipline.RUN,
            asOf = input.evaluatedAtUtc,
            zone = input.zoneId,
            physiologicalModality = M2BPhysiologicalModality.RUNNING
        )
        val extraRestrictions = buildList {
            if (restrictionLoad.failClosedSafetyActive) {
                add(M2BRestrictionStatus.StatusUnavailable)
            }
            if (restrictionLoad.restrictions.hasHardBlock(AthleteDiscipline.RUN, input.evaluatedAtUtc)) {
                add(M2BRestrictionStatus.RunningSpecific(m2bContext))
            }
            val intensityCap = restrictionLoad.restrictions.effectiveIntensityCap(
                AthleteDiscipline.RUN,
                input.evaluatedAtUtc
            )
            if (intensityCap == SafetyIntensityCap.REST_ONLY) {
                add(M2BRestrictionStatus.RunningSpecific(m2bContext))
            } else if (intensityCap == SafetyIntensityCap.EASY_AEROBIC_ONLY) {
                add(M2BRestrictionStatus.RecoveryIntensity(m2bContext))
            }
        }
        val hrEvidence = buildHrZoneEvidence(owner, input.evaluatedAtUtc, input.hrZoneProfile)
        val intensityCalibration = Engine15M2BIntensityCalibrator.calibrate(
            context = m2bContext,
            ageYears = input.ageYears,
            performance = m2aRequest,
            additionalRestrictions = extraRestrictions,
            heartRateEvidence = hrEvidence,
            athleteSnapshot = snapshot
        )

        val supplementaryActivities = ownedCanonical.mapNotNull { canAct ->
            val durS = canAct.normalizedMetrics.duration?.value ?: 0.0
            val completedAt = Engine15M2CCanonical.completedInstant(canAct.startedAtUtc, durS)
            if (completedAt == null || completedAt.isAfter(input.evaluatedAtUtc)) {
                null
            } else {
                M2CCompletedActivityInput(canonicalActivity = canAct)
            }
        }
        val m2cRequest = M2CWorkloadIntelligenceRequest(
            context = m2bContext,
            observedWorkload = observedWorkload,
            intensityCalibration = intensityCalibration,
            athleteSnapshot = snapshot,
            projectedWorkload = projectedWorkload,
            supplementaryActivities = supplementaryActivities,
            weeklyConfirmations = input.weeklyConfirmations,
            confirmedInterruption = input.confirmedInterruption,
            longRunDurationCap = input.longRunDurationCap
        )
        val workloadAssessment = Engine15M2CWorkloadIntelligence.assess(m2cRequest)

        return UnifiedAthleteAssessment(
            accountId = owner,
            evaluatedAtUtc = input.evaluatedAtUtc,
            asOfDate = asOfDate,
            zoneId = input.zoneId,
            canonicalActivities = ownedCanonical,
            unresolvedAmbiguityCount = projection.unresolvedAmbiguityCount,
            observedWorkload = observedWorkload,
            projectedWorkload = projectedWorkload,
            restrictionLoadResult = restrictionLoad,
            athleteSnapshot = snapshot,
            performanceCalibration = performanceCalibration,
            intensityCalibration = intensityCalibration,
            workloadAssessment = workloadAssessment,
            persistedAssessmentRecord = derivedRecord
        )
    }

    /**
     * Projects verified M1 safety caps/holds and M2 calibration into [PlanRequest] prior to
     * generation so `PlanSnapshot.request` remains self-contained and `validateSaved(plan)` stays
     * 100% deterministic from `plan.request` alone.
     */
    fun applySafetyAndAssessmentToRequest(
        request: PlanRequest,
        assessment: UnifiedAthleteAssessment
    ): PlanRequest {
        var updated = request
        val evaluatedAt = assessment.evaluatedAtUtc

        if (updated.runDays > 0) {
            val m2cRestrictions = assessment.workloadAssessment.handoff.effectiveRestrictionState
            val runIntensityCap = assessment.activeRestrictions.effectiveIntensityCap(
                AthleteDiscipline.RUN,
                evaluatedAt
            )
            val runHardBlocked = assessment.hasHardBlock(AthleteDiscipline.RUN) ||
                runIntensityCap == SafetyIntensityCap.REST_ONLY ||
                M2BRestrictionReason.UNKNOWN_STATE in m2cRestrictions.effectiveReasons ||
                M2BRestrictionReason.RUNNING_RESTRICTION in m2cRestrictions.effectiveReasons ||
                M2BRestrictionReason.CLEARANCE_CLAIMED in m2cRestrictions.effectiveReasons

            if (runHardBlocked) {
                updated = updated.copy(runningRecoveryHold = true)
            }

            if (runIntensityCap == SafetyIntensityCap.EASY_AEROBIC_ONLY || m2cRestrictions.recoveryOnly) {
                updated = updated.copy(qualityCount = 0, qualityDays = emptySet())
            } else if (runIntensityCap == SafetyIntensityCap.NO_HIGH_INTENSITY_Z3) {
                updated = updated.copy(qualityCount = min(updated.qualityCount, 1))
            }

            val maxWeeklyMeters = assessment.activeRestrictions.effectiveMaxWeeklyVolumeCap(
                AthleteDiscipline.RUN,
                evaluatedAt
            )
            if (maxWeeklyMeters != null) {
                val capKm = maxWeeklyMeters.value / Engine15M2CPolicyRegistry.METERS_PER_KILOMETER
                if (capKm < 0.1) {
                    updated = updated.copy(runningRecoveryHold = true)
                } else {
                    val cappedWeeklyKm = if (updated.weeklyKm > capKm) capKm else updated.weeklyKm
                    val cappedLongestKm = if (updated.longestKm > capKm) capKm else updated.longestKm
                    val cappedTargetWeeklyKm = updated.targetWeeklyKm?.let { target ->
                        if (target > capKm) capKm else target
                    }
                    updated = updated.copy(
                        weeklyKm = cappedWeeklyKm,
                        longestKm = cappedLongestKm,
                        targetWeeklyKm = cappedTargetWeeklyKm
                    )
                }
            }

            // Never fabricate weekly mileage over CONFIRMED_ZERO (Time-Only Run/Walk guard).
            val isConfirmedZero = updated.weeklyKm == 0.0 && updated.recentMileageBasis == "CONFIRMED_ZERO"
            if (!isConfirmedZero &&
                !updated.runningRecoveryHold &&
                updated.benchmarkDistanceMeters == null &&
                updated.benchmarkDurationSeconds == null &&
                assessment.workloadAssessment.handoff.paceCalibrationEligible
            ) {
                val benchmark = assessment.performanceCalibration.operationalThresholdBenchmark
                if (benchmark != null &&
                    assessment.performanceCalibration.authorityMode in setOf(
                        M2ACalibrationAuthorityMode.VERIFIED_RECENT_BENCHMARK,
                        M2ACalibrationAuthorityMode.VERIFIED_AGING_BENCHMARK
                    )
                ) {
                    val distM = benchmark.distance.value.roundToInt()
                    val durS = benchmark.duration.value.roundToInt()
                    if (distM in 1000..100000 && durS in 120..43200) {
                        updated = updated.copy(
                            benchmarkDistanceMeters = distM,
                            benchmarkDurationSeconds = durS
                        )
                    }
                }
            }
        }

        val projectedCaps = LinkedHashMap(updated.safetyIntensityCaps)
        val nonRunSpecs = listOf(
            Triple("str", updated.strengthDays > 0, AthleteDiscipline.STRENGTH),
            Triple("cal", updated.calDays > 0, AthleteDiscipline.CALISTHENICS),
            Triple("cond", updated.conditioningDays > 0, AthleteDiscipline.CONDITIONING)
        )
        for ((key, requested, disc) in nonRunSpecs) {
            if (!requested) continue
            val liveCap = assessment.activeRestrictions.effectiveIntensityCap(disc, evaluatedAt)
            if (liveCap != null && liveCap != SafetyIntensityCap.REST_ONLY) {
                val existing = projectedCaps[key]?.let { raw -> SafetyIntensityCap.entries.firstOrNull { it.name == raw } }
                val stricter = listOfNotNull(existing, liveCap).minByOrNull { it.strictnessRank }
                if (stricter != null) projectedCaps[key] = stricter.name
            }
        }
        if (projectedCaps != updated.safetyIntensityCaps) {
            updated = updated.copy(safetyIntensityCaps = projectedCaps)
        }

        return updated
    }

    /**
     * Checks requested non-running disciplines against active M1-D hard blocks, REST_ONLY caps,
     * or fail-closed restriction storage before draft generation.
     */
    fun requireRequestedDisciplinesUnblocked(
        request: PlanRequest,
        assessment: UnifiedAthleteAssessment
    ) {
        val evaluatedAt = assessment.evaluatedAtUtc
        val requested = buildList {
            if (request.strengthDays > 0) add(AthleteDiscipline.STRENGTH to "Strength")
            if (request.calDays > 0) add(AthleteDiscipline.CALISTHENICS to "Calisthenics")
            if (request.conditioningDays > 0) add(AthleteDiscipline.CONDITIONING to "Conditioning")
        }
        val blocked = requested.firstOrNull { (disc, _) ->
            assessment.hasHardBlock(disc) ||
                assessment.activeRestrictions.effectiveIntensityCap(disc, evaluatedAt) == SafetyIntensityCap.REST_ONLY
        }
        if (blocked != null) {
            throw PlanConstraint(
                "SAFETY_RESTRICTION_HOLD: ${blocked.second} planning is paused while an active safety restriction is unresolved."
            )
        }
    }

    private fun buildHrZoneEvidence(
        owner: String,
        evaluatedAtUtc: Instant,
        hrZoneProfile: ExecutionHrZoneProfile?
    ): List<M2BHeartRateEvidence> {
        if (hrZoneProfile == null) return emptyList()
        val floors = listOf(
            hrZoneProfile.z1FloorBpm,
            hrZoneProfile.z2FloorBpm,
            hrZoneProfile.z3FloorBpm,
            hrZoneProfile.z4FloorBpm,
            hrZoneProfile.z5FloorBpm
        ).map { BeatsPerSecond.fromBpm(it.toDouble()) }
        return listOf(
            M2BHeartRateEvidence.Zones(
                scope = M2BHeartRateEvidenceScope(
                    accountId = owner,
                    discipline = AthleteDiscipline.RUN,
                    physiologicalModality = M2BPhysiologicalModality.RUNNING,
                    source = M2ASourceQualifiedAlias("garmin", "athlete-hr-profile"),
                    completedAt = evaluatedAtUtc
                ),
                imported = true,
                floors = floors
            )
        )
    }

    private fun toFreeformObservation(
        owner: String,
        workout: FreeformWorkout,
        evaluatedAtUtc: Instant
    ): RawActivityObservation? {
        if (workout.owner != owner || !workout.isComplete) return null
        // Running runs come authoritatively from TrainingStore / Garmin / plan logs in Engine 1.5.
        if (workout.discipline == ManualDiscipline.RUNNING) return null
        val completedAtMs = workout.completedAt ?: return null
        val completedInstant = Instant.ofEpochMilli(completedAtMs)
        if (completedInstant.isAfter(evaluatedAtUtc)) return null

        val startedMs = workout.startedAt.takeIf { it in 1L..completedAtMs } ?: completedAtMs
        val startedInstant = Instant.ofEpochMilli(startedMs)
        val rawId = "ff-${workout.id}"
        if (rawId.length > 128) return null

        val type = when (workout.discipline) {
            ManualDiscipline.STRENGTH -> "strength"
            ManualDiscipline.CALISTHENICS -> "calisthenics"
            ManualDiscipline.PLYOMETRICS,
            ManualDiscipline.ISOMETRICS,
            ManualDiscipline.CONDITIONING -> "conditioning"
            ManualDiscipline.RUNNING -> return null
        }
        val durationS = workout.durationMinutes
            .trim()
            .toDoubleOrNull()
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?.times(60.0)
        val source = if (workout.custom != null) "custom" else "freeform"

        return RawActivityObservation(
            accountId = owner,
            record = ActivityRecord(
                id = rawId,
                title = workout.title.ifBlank { workout.discipline.label },
                type = type,
                source = source,
                startedAt = startedInstant,
                distanceM = null,
                durationS = durationS,
                movingTimeS = null,
                recordedPace = null,
                averageHr = null,
                elevationM = null
            )
        )
    }
}
