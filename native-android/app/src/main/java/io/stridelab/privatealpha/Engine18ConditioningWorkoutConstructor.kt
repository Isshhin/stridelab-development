package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlin.math.floor

/** All 8 Conditioning Families from Track 6 §8. */
internal enum class ConditioningFamily8(
    val displayLabel: String,
    val isHighStress: Boolean,
    val isLowerBodyHeavy: Boolean
) {
    CONTINUOUS_AEROBIC("Continuous aerobic", false, false),
    MODALITY_HIIT("Modality HIIT intervals", true, true),
    STRUCTURED_CIRCUITS("Structured circuits", false, false),
    EMOM("EMOM work capacity", false, false),
    QUALITY_PACED_AMRAP("Quality-paced AMRAP", false, false),
    WORK_REST_INTERVALS("Work-rest intervals", false, true),
    MIXED_MODAL_CONDITIONING("Mixed-modal conditioning", true, true),
    MUSCULAR_ENDURANCE_CIRCUITS("Muscular endurance circuit", false, false)
}

internal enum class ConditioningModality {
    BIKE,
    ROW,
    SKI_ERG,
    SWIM,
    ELLIPTICAL,
    BODYWEIGHT_CIRCUIT,
    MIXED_MODAL
}

internal data class ConditioningExerciseDescriptor(
    val id: String,
    val name: String,
    val modality: ConditioningModality,
    val difficulty: String,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val equipmentOptions: List<Set<String>>,
    val isComplexSkillOrAxial: Boolean,
    val cue: String
)

internal data class EmomScalingDecision(
    val scaled: Boolean,
    val newRepsPerMinute: Int?,
    val estimatedWorkSecondsPerMinute: Double,
    val switchedToWorkRestFormat: Boolean,
    val cadenceProvenance: String,
    val liveStopRule: String
)

internal data class ConditioningSingleAxisEvaluation(
    val valid: Boolean,
    val axesChanged: List<String>,
    val reason: String?
)

internal data class ConditioningBlockDuration(
    val minSeconds: Int,
    val maxSeconds: Int
) {
    val conservativeMinutes: Int get() = (maxSeconds + 59) / 60
}

/**
 * Engine 1.8 Complete 8-Family Conditioning Constructor & Solo Conditioning Builder:
 * - Supports all 8 Track 6 §8 conditioning families without running benchmarks or HR
 * - Repaired POL-048 (> 40.0 s/min EMOM trigger) + MOD-17 cadence-qualified rep scaling
 * - MD-08 proficiency blocking of complex movements for novices
 * - MD-09 strict single-axis progression validation
 * - MD-10 strict modality HR isolation (zero cross-application of running HR to bike/row/swim)
 * - MD-06 shared UnifiedLocalMuscleLedger export & blockedOverlappingMuscles deduplication
 */
internal object Engine18ConditioningWorkoutConstructor {

    private val lockRegex = Regex(""".*-w([1-9][0-9]*)-cond(0|[1-9][0-9]*)$""")

    private val complexHighSkillIds = setOf(
        "cond-double-unders",
        "str-conv-deadlift",
        "str-back-squat",
        "str-front-squat",
        "str-overhead-press",
        "str-dumbbell-snatch"
    )

    fun availableEquipmentTokens(request: PlanRequest): Set<String> = buildSet {
        if ((request.equipment["erg"] ?: 0) >= 1 || (request.equipment["rower"] ?: 0) >= 1 ||
            (request.equipment["bike"] ?: 0) >= 1
        ) add("erg")
        if ((request.equipment["rope"] ?: 0) >= 1) add("rope")
        if ((request.equipment["ropes"] ?: 0) >= 1) add("ropes")
        if ((request.equipment["kb"] ?: 0) >= 1 || (request.equipment["kettlebells"] ?: 0) >= 1) add("kettlebell")
        if ((request.equipment["sled"] ?: 0) >= 1) add("sled")
        if ((request.equipment["db"] ?: 0) >= 1 || (request.equipment["dumbbells"] ?: 0) >= 1) add("dumbbells")
        if ((request.equipment["box"] ?: 0) >= 1 || (request.equipment["bench"] ?: 0) >= 1) add("box")
    }

    /** MD-10: Returns null whenever HR profile comes from RUNNING or a different physiological modality. */
    fun resolveModalityHrZones(
        targetModality: ConditioningModality,
        hrProfileModality: M2BPhysiologicalModality,
        verifiedProfile: ExecutionHrZoneProfile?
    ): ExecutionHrZoneProfile? {
        if (verifiedProfile == null) return null
        if (hrProfileModality == M2BPhysiologicalModality.RUNNING) return null
        val matches = when (targetModality) {
            ConditioningModality.BIKE -> hrProfileModality == M2BPhysiologicalModality.CYCLING
            ConditioningModality.SWIM -> hrProfileModality == M2BPhysiologicalModality.SWIMMING
            else -> false
        }
        return if (matches) verifiedProfile else null
    }

    /**
     * MOD-17 / CON-22 + Repaired POL-048 (> 40.0 s/min EMOM trigger protecting >= 20.0 s rest).
     */
    fun scaleEmomReps(
        oldReps: Int,
        observedWorkSeconds: Double,
        targetRestSeconds: Double = Engine18HybridPolicyRegistry.MOD_17_EMOM_DEFAULT_TARGET_REST_SECONDS
    ): EmomScalingDecision {
        require(oldReps >= 1 && observedWorkSeconds.isFinite() && observedWorkSeconds > 0.0 && observedWorkSeconds <= 60.0)
        require(
            targetRestSeconds.isFinite() &&
                targetRestSeconds in Engine18HybridPolicyRegistry.MOD_17_EMOM_MIN_TARGET_REST_SECONDS..Engine18HybridPolicyRegistry.MOD_17_EMOM_MAX_TARGET_REST_SECONDS
        )
        if (observedWorkSeconds <= Engine18HybridPolicyRegistry.POL_048_EMOM_MAX_WORK_SECONDS_PER_MIN + 1e-9) {
            return EmomScalingDecision(
                scaled = false,
                newRepsPerMinute = oldReps,
                estimatedWorkSecondsPerMinute = observedWorkSeconds,
                switchedToWorkRestFormat = false,
                cadenceProvenance = "WITHIN_EMOM_REST_FLOOR",
                liveStopRule = "Stop minute at 40 s if reps remain unfinished to preserve 20 s recovery."
            )
        }
        val rNew = floor(oldReps.toDouble() * ((60.0 - targetRestSeconds) / observedWorkSeconds)).toInt()
        if (rNew < 1) {
            return EmomScalingDecision(
                scaled = true,
                newRepsPerMinute = null,
                estimatedWorkSecondsPerMinute = 30.0,
                switchedToWorkRestFormat = true,
                cadenceProvenance = "APPROXIMATE_CADENCE",
                liveStopRule = "Switch to 30 s work / 30 s rest intervals to protect movement quality."
            )
        }
        val estNew = observedWorkSeconds * (rNew.toDouble() / oldReps.toDouble())
        return EmomScalingDecision(
            scaled = true,
            newRepsPerMinute = rNew,
            estimatedWorkSecondsPerMinute = estNew,
            switchedToWorkRestFormat = false,
            cadenceProvenance = "APPROXIMATE_CADENCE",
            liveStopRule = "Scaled to $rNew reps/min (~${estNew.toInt()} s work). Stop at 40 s if pace slows."
        )
    }

    /** MD-08 proficiency gate: blocks advanced/complex conditioning movements when novice. */
    fun isMovementAllowedForConditioning(
        exerciseId: String,
        difficulty: String,
        experience: Int
    ): Boolean {
        if (experience <= 0 && (exerciseId in complexHighSkillIds || difficulty == "advanced")) {
            return false
        }
        return true
    }

    /** MD-09 single-axis conditioning progression invariant. */
    fun validateSingleAxisConditioningProgression(
        deltaDurationRel: Double,
        deltaRounds: Int,
        deltaLoadRel: Double,
        densityIncreased: Boolean,
        switchedToNewHiitOrMixedFamily: Boolean
    ): ConditioningSingleAxisEvaluation {
        val axes = buildList {
            if (deltaDurationRel > 1e-6) add("DURATION")
            if (deltaRounds > 0) add("ROUNDS")
            if (deltaLoadRel > 1e-6) add("LOAD")
            if (densityIncreased) add("DENSITY")
            if (switchedToNewHiitOrMixedFamily) add("NEW_HIGH_STRESS_FAMILY")
        }
        val overSingleAxis = axes.size > 1
        val durTooBig = deltaDurationRel > Engine18HybridPolicyRegistry.POL_059_COND_MINOR_DUR_REL_CAP + 1e-6
        val valid = !overSingleAxis && !durTooBig
        val reason = when {
            overSingleAxis -> "MD-09 violation: conditioning changed multiple axes concurrently (${axes.joinToString(", ")})."
            durTooBig -> "Conditioning duration jump exceeds +10% weekly guardrail."
            else -> null
        }
        return ConditioningSingleAxisEvaluation(valid, axes, reason)
    }

    fun catalogDescriptors(): List<ConditioningExerciseDescriptor> {
        val builtIns = listOf(
            ConditioningExerciseDescriptor(
                id = "cond-step-jack",
                name = "Low-impact step jacks",
                modality = ConditioningModality.BODYWEIGHT_CIRCUIT,
                difficulty = "foundation",
                primaryMuscles = listOf("calves", "gluteus_medius"),
                secondaryMuscles = listOf("anterior_deltoid", "abdominals"),
                equipmentOptions = listOf(emptySet()),
                isComplexSkillOrAxial = false,
                cue = "Step briskly side to side at conversational Talk Test effort without jarring impact."
            ),
            ConditioningExerciseDescriptor(
                id = "cond-tempo-march",
                name = "Brisk low-impact aerobic march and shadow boxing",
                modality = ConditioningModality.BODYWEIGHT_CIRCUIT,
                difficulty = "foundation",
                primaryMuscles = listOf("quadriceps", "hip_flexors"),
                secondaryMuscles = listOf("anterior_deltoid", "trunk_stabilizers"),
                equipmentOptions = listOf(emptySet()),
                isComplexSkillOrAxial = false,
                cue = "Keep nasal/conversational breathing steady at RPE 3 to 4."
            ),
            ConditioningExerciseDescriptor(
                id = "cond-kb-dead-bug-circuit",
                name = "Alternating lower-hinge and core circuit",
                modality = ConditioningModality.BODYWEIGHT_CIRCUIT,
                difficulty = "foundation",
                primaryMuscles = listOf("gluteus_maximus", "abdominals"),
                secondaryMuscles = listOf("hamstrings", "trunk_stabilizers"),
                equipmentOptions = listOf(emptySet()),
                isComplexSkillOrAxial = false,
                cue = "Move deliberately between stations; pause early if trunk alignment slips."
            )
        )
        val expanded = ExpandedExerciseCatalog.conditioningRecords.map { rec ->
            val mod = when {
                "erg" in rec.id || "row" in rec.id || "ski" in rec.id || "bike" in rec.id -> ConditioningModality.BIKE
                "sled" in rec.id || "kettlebell" in rec.id || "carry" in rec.id || "thruster" in rec.id -> ConditioningModality.MIXED_MODAL
                else -> ConditioningModality.BODYWEIGHT_CIRCUIT
            }
            ConditioningExerciseDescriptor(
                id = rec.id,
                name = rec.name,
                modality = mod,
                difficulty = rec.difficulty,
                primaryMuscles = rec.primaryMuscles,
                secondaryMuscles = rec.secondaryMuscles,
                equipmentOptions = rec.equipmentOptions,
                isComplexSkillOrAxial = rec.id in complexHighSkillIds || rec.difficulty == "advanced",
                cue = rec.technique.firstOrNull() ?: "Maintain clean technique at controlled effort."
            )
        }
        return (builtIns + expanded).distinctBy { it.id }
    }

    fun familyFromGoal(goal: String?, experience: Int, hasErgOrExternalEquipment: Boolean): ConditioningFamily8 {
        val g = goal?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return when {
            "emom" in g -> ConditioningFamily8.EMOM
            "amrap" in g -> ConditioningFamily8.QUALITY_PACED_AMRAP
            "work rest" in g || "work-rest" in g -> ConditioningFamily8.WORK_REST_INTERVALS
            "threshold" in g || "hiit" in g || "interval" in g ->
                if (experience >= 1 && hasErgOrExternalEquipment) ConditioningFamily8.MODALITY_HIIT
                else ConditioningFamily8.WORK_REST_INTERVALS
            "mixed" in g -> ConditioningFamily8.MIXED_MODAL_CONDITIONING
            "circuit" in g -> ConditioningFamily8.STRUCTURED_CIRCUITS
            "muscular endurance" in g -> ConditioningFamily8.MUSCULAR_ENDURANCE_CIRCUITS
            "steady" in g || "continuous" in g || "aerobic" in g -> ConditioningFamily8.CONTINUOUS_AEROBIC
            else -> if (experience == 0) ConditioningFamily8.CONTINUOUS_AEROBIC else ConditioningFamily8.STRUCTURED_CIRCUITS
        }
    }

    fun validateSoloRequest(
        request: PlanRequest,
        safetyRestrictions: AthleteSafetyRestrictionSet? = null,
        evaluatedAtUtc: Instant = Instant.now()
    ) {
        if (safetyRestrictions?.hasHardBlock(AthleteDiscipline.CONDITIONING, evaluatedAtUtc) == true ||
            safetyRestrictions?.effectiveIntensityCap(AthleteDiscipline.CONDITIONING, evaluatedAtUtc) == SafetyIntensityCap.REST_ONLY
        ) {
            throw PlanConstraint("SAFETY_RESTRICTION_HOLD: Conditioning planning is paused while an active safety restriction is unresolved.")
        }
        if (request.policyChoice != "engine15" ||
            request.runDays != 0 ||
            request.strengthDays != 0 ||
            request.calDays != 0 ||
            request.conditioningDays !in 1..6 ||
            request.weeks !in 1..16 ||
            request.qualityCount != 0 ||
            request.days.isEmpty() ||
            request.days.any { it !in 1..7 } ||
            request.days.size < request.conditioningDays ||
            request.experience !in 0..2 ||
            (request.timeBudgetEnabled && request.minutes !in 15..120) ||
            request.disciplineGoals.keys.any { it != "cond" } ||
            request.weeklySlots.keys.any { it != "cond" } ||
            request.limitations.isNotBlank() ||
            request.recoveryNotes.isNotBlank()
        ) {
            throw PlanConstraint("CONDITIONING_REQUEST_UNSUPPORTED: Choose 1 to 6 conditioning sessions, valid weekdays, and resolve notes.")
        }
        val assigned = request.weeklySlots["cond"]
        if (assigned != null && (assigned.size != request.conditioningDays ||
                assigned.distinct().size != request.conditioningDays ||
                assigned.any { it !in request.days })
        ) {
            throw PlanConstraint("CONDITIONING_WEEKDAY_ASSIGNMENTS_INVALID: Assign each session to a distinct available weekday.")
        }
        if (request.lockedSessionDays.keys.any { !lockRegex.matches(it) }) {
            throw PlanConstraint("CONDITIONING_SESSION_LOCK_UNSUPPORTED: Lock ID does not match conditioning week and slot.")
        }
        Engine15SessionLocks.validateRequest(request)
    }

    fun composeSessions(
        request: PlanRequest,
        id: String,
        allowCalendarReschedule: Boolean = false,
        blockedOverlappingMuscles: Set<String> = emptySet(),
        safetyRestrictions: AthleteSafetyRestrictionSet? = null,
        evaluatedAtUtc: Instant = Instant.now()
    ): List<PlannedSession> {
        validateSoloRequest(request, safetyRestrictions, evaluatedAtUtc)
        val tokens = availableEquipmentTokens(request)
        val hasEq = tokens.isNotEmpty()
        val goal = request.disciplineGoal("cond")
        val family = familyFromGoal(goal, request.experience, hasEq)

        val candidates = catalogDescriptors().filter { desc ->
            desc.id !in request.excluded &&
                isMovementAllowedForConditioning(desc.id, desc.difficulty, request.experience) &&
                desc.equipmentOptions.any { opt -> opt.isEmpty() || tokens.containsAll(opt) } &&
                (blockedOverlappingMuscles.isEmpty() || desc.primaryMuscles.none { it in blockedOverlappingMuscles })
        }.ifEmpty {
            catalogDescriptors().filter { it.id !in request.excluded && it.equipmentOptions.any { opt -> opt.isEmpty() } }
        }
        if (candidates.isEmpty()) {
            throw PlanConstraint("CONDITIONING_EXERCISE_UNAVAILABLE: All eligible conditioning movements are excluded.")
        }

        val weekdays = request.weeklySlots["cond"] ?: spacedWeekdays(request.days, request.conditioningDays)
        val locks = lockSlots(request)
        val isSafetyEasy = safetyRestrictions?.effectiveIntensityCap(AthleteDiscipline.CONDITIONING, evaluatedAtUtc) in
            setOf(SafetyIntensityCap.EASY_AEROBIC_ONLY, SafetyIntensityCap.NO_HIGH_INTENSITY_Z3)

        val primaryMove = candidates.first()
        val secondaryMove = candidates.getOrElse(1) { primaryMove }

        return buildList {
            for (week in 1..request.weeks) {
                val weekStart = request.start.plusWeeks((week - 1).toLong())
                val recovery = (week % 4 == 0) || (request.returning && week <= 2)
                val step = ((week - 1) % 4).coerceAtMost(2)
                val effFamily = if (isSafetyEasy) ConditioningFamily8.CONTINUOUS_AEROBIC else family

                val (mainWorkMinutes, roundsCount, rpeCue, blockSteps) = when (effFamily) {
                    ConditioningFamily8.CONTINUOUS_AEROBIC -> {
                        val workM = if (recovery) 15 else (18 + step * 3)
                        Tuple4(workM, 1, "RPE 3 to 4 conversational pace (modality HR only if verified for same erg)", listOf(
                            "5 min progressive aerobic warm-up",
                            "$workM min continuous steady effort at Talk Test pace; stop on joint discomfort",
                            "4 min cool-down walk"
                        ))
                    }
                    ConditioningFamily8.EMOM -> {
                        val emomMins = if (recovery) 10 else (12 + step * 2)
                        Tuple4(emomMins, emomMins, "RPE 7 to 8 paced repeatability (at least 20 s rest every minute)", listOf(
                            "6 min movement prep warm-up",
                            "$emomMins min EMOM: stop each minute cleanly at 40 s if pace slows so >= 20 s rest holds",
                            "4 min relaxed breathing cool-down"
                        ))
                    }
                    ConditioningFamily8.QUALITY_PACED_AMRAP -> {
                        val amrapMins = if (recovery) 8 else (10 + step * 2)
                        Tuple4(amrapMins, 1, "Quality-Paced AMRAP at RPE 7 to 8 (zero maximal failure)", listOf(
                            "6 min warm-up",
                            "$amrapMins min quality-paced AMRAP: pause cleanly whenever form begins to drift",
                            "4 min cool-down"
                        ))
                    }
                    ConditioningFamily8.MODALITY_HIIT -> {
                        val blockM = if (recovery) 10 else (12 + step * 2)
                        Tuple4(blockM, 4 + step, "RPE 8 to 9 controlled modality intervals (stop if power drops > 15%)", listOf(
                            "10 min aerobic modality warm-up",
                            "$blockM min modality intervals (${4 + step} rounds, 1:1 work-to-recovery)",
                            "5 min easy spin or row cool-down"
                        ))
                    }
                    ConditioningFamily8.STRUCTURED_CIRCUITS,
                    ConditioningFamily8.MUSCULAR_ENDURANCE_CIRCUITS -> {
                        val rds = if (recovery) 2 else (3 + (step / 2))
                        Tuple4(rds * 4, rds, "RPE 6 to 7 controlled stations, 2 to 3 RIR", listOf(
                            "5 min dynamic warm-up",
                            "$rds rounds alternating push/pull/lower/core stations (30 s work / 30 s transition)",
                            "3 min easy walk cool-down"
                        ))
                    }
                    else -> { // WORK_REST_INTERVALS or MIXED_MODAL
                        val blockM = if (recovery) 12 else (14 + step * 2)
                        Tuple4(blockM, blockM, "Controlled RPE 7 to 8 on 30 s work / 30 s rest clock", listOf(
                            "6 min joint prep warm-up",
                            "$blockM min work-rest intervals (30 s controlled work / 30 s recovery)",
                            "4 min cool-down"
                        ))
                    }
                }

                val wuCdMin = if (effFamily == ConditioningFamily8.MODALITY_HIIT) 15 else 9
                val rawMin = wuCdMin + mainWorkMinutes
                val finalMin = if (request.timeBudgetEnabled) rawMin.coerceAtMost(request.minutes) else rawMin
                val effWorkSec = ((finalMin - wuCdMin).coerceAtLeast(5)) * 60

                for (slot in 0 until request.conditioningDays) {
                    val targetDow = weekdays[slot]
                    val naturalDate = weekStart.plusDays(((targetDow - weekStart.dayOfWeek.value + 7) % 7).toLong())
                    val lockDate = locks[week to slot]
                    if (lockDate != null && !allowCalendarReschedule && lockDate != naturalDate) {
                        throw PlanConstraint("CONDITIONING_SESSION_LOCK_CONFLICT: Locked date conflicts with weekday.")
                    }
                    val chosen = if (slot % 2 == 0) primaryMove else secondaryMove
                    val sessionId = matchingLockId(request, week, slot) ?: "$id-w$week-cond$slot"
                    val eqText = chosen.equipmentOptions.firstOrNull { opt -> opt.isEmpty() || tokens.containsAll(opt) }
                        ?.sorted()?.joinToString(" + ")?.ifBlank { "None" } ?: "None"
                    val exList = if (effFamily == ConditioningFamily8.EMOM) {
                        listOf(ExercisePrescription(
                            id = chosen.id,
                            name = chosen.name,
                            sets = roundsCount,
                            reps = 8,
                            rest = 25,
                            equipment = eqText,
                            cue = "${chosen.cue} $rpeCue. Stop by 40 s each minute."
                        ))
                    } else {
                        listOf(ExercisePrescription(
                            id = chosen.id,
                            name = chosen.name,
                            sets = roundsCount,
                            seconds = effWorkSec / roundsCount,
                            rest = if (roundsCount > 1) 30 else 0,
                            equipment = eqText,
                            cue = "${chosen.cue} $rpeCue."
                        ))
                    }
                    add(PlannedSession(
                        id = sessionId,
                        date = naturalDate,
                        discipline = "cond",
                        title = "Conditioning: ${effFamily.displayLabel}",
                        minutes = finalMin,
                        distanceKm = null,
                        kind = "conditioning",
                        lower = effFamily.isLowerBodyHeavy || chosen.primaryMuscles.any { it in setOf("quadriceps", "hamstrings", "gluteus_maximus", "calves") },
                        quality = effFamily.isHighStress || (!isSafetyEasy && goal.lowercase(Locale.ROOT).let { "threshold" in it || "interval" in it }),
                        exercises = exList,
                        steps = blockSteps,
                        reason = if (recovery) {
                            "Deload conditioning week: volume trimmed to support systemic recovery."
                        } else {
                            "Engine 1.8 ${effFamily.displayLabel} block progressing 1 axis cleanly within recovery limits."
                        }
                    ))
                }
            }
        }.sortedWith(compareBy<PlannedSession> { it.date }.thenBy { it.id })
    }

    fun buildSoloPlan(
        request: PlanRequest,
        owner: String,
        id: String,
        now: Instant,
        allowCalendarReschedule: Boolean = false
    ): PlanSnapshot {
        RecordPolicy.identifier(owner)
        RecordPolicy.identifier(id)
        val sessions = composeSessions(request, id, allowCalendarReschedule)
        return PlanSnapshot(
            id = id,
            owner = owner,
            engineVersion = ENGINE18_VERSION,
            policyId = ENGINE18_CONDITIONING_POLICY,
            createdAt = now,
            request = request,
            sessions = sessions,
            explanations = listOf(
                "Engine 1.8 8-Family Conditioning plan built without assuming running pace or running HR zones.",
                "EMOM blocks enforce a 40 s work cutoff protecting >= 20 s recovery per minute (POL-048 / MOD-17).",
                "Complex skills are filtered for novice proficiency (MD-08) and progression advances 1 axis at a time (MD-09)."
            ),
            name = "Engine 1.8 ${familyFromGoal(request.disciplineGoal("cond"), request.experience, availableEquipmentTokens(request).isNotEmpty()).displayLabel} Plan"
        )
    }

    fun validateSavedSolo(plan: PlanSnapshot, allowCalendarReschedule: Boolean = false) {
        if (plan.engineVersion != ENGINE18_VERSION || plan.policyId != ENGINE18_CONDITIONING_POLICY) {
            throw PlanConstraint("The saved Engine 1.8 conditioning plan has an unsupported identity.")
        }
        val expected = composeSessions(plan.request, plan.id, allowCalendarReschedule).associateBy { it.id }
        if (plan.sessions.size != expected.size || plan.sessions.map { it.id }.toSet() != expected.keys) {
            throw PlanConstraint("The saved Engine 1.8 conditioning plan no longer matches its expected sessions.")
        }
        for (session in plan.sessions) {
            val target = expected.getValue(session.id)
            val base = session.baselineSession()
            if (base.discipline != "cond" || base.kind != "conditioning" || base.distanceKm != null ||
                base.exercises != target.exercises ||
                (!allowCalendarReschedule && session.date != target.date) ||
                session.date.dayOfWeek.value !in plan.request.days ||
                !Engine15WorkoutDifficultyAdjuster.matchesAdjusted(session, plan.request)
            ) {
                throw PlanConstraint("The saved Engine 1.8 conditioning plan no longer matches its contract.")
            }
        }
        if (!allowCalendarReschedule) Engine15SessionLocks.validateOutput(plan.request, plan.sessions)
    }

    fun validateHybridConditioningRows(soloRequest: PlanRequest, rows: List<PlannedSession>) {
        validateSoloRequest(soloRequest)
        for (session in rows) {
            val base = session.baselineSession()
            if (base.discipline != "cond" || base.kind != "conditioning" || base.distanceKm != null ||
                base.exercises.isEmpty() || base.exercises.any { it.id in soloRequest.excluded || it.sets !in 1..30 } ||
                base.steps.isEmpty() || session.date.dayOfWeek.value !in soloRequest.days ||
                !Engine15WorkoutDifficultyAdjuster.matchesAdjusted(session, soloRequest)
            ) {
                throw PlanConstraint("The saved Engine 1.8 conditioning rows no longer match their contract.")
            }
        }
    }

    fun exportToSharedLocalLedger(
        accountId: String,
        weekStart: LocalDate,
        weekEnd: LocalDate,
        conditioningSessions: List<PlannedSession>
    ): UnifiedLocalMuscleLedger {
        val inputs = conditioningSessions.flatMap { s ->
            s.exercises.map { ex ->
                ObservedStrengthSetExposureInput(
                    accountId = accountId,
                    performedOn = s.date,
                    exerciseId = ex.id,
                    exerciseName = ex.name,
                    hardSetCount = ex.sets.coerceAtLeast(1),
                    isHighRepCircuit = true,
                    rir = 2
                )
            }
        }
        val spanDays = (java.time.temporal.ChronoUnit.DAYS.between(weekStart, weekEnd).toInt() + 1).coerceAtLeast(1)
        val raw = Engine16LocalMuscleLedgerBuilder.buildObserved(
            accountId = accountId,
            asOfDate = weekEnd,
            windowDays = spanDays,
            explicitExposures = inputs
        )
        return UnifiedLocalMuscleLedger(accountId, weekStart, weekEnd, isProjected = true, byMuscle = raw.byMuscle)
    }

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    private fun matchingLockId(request: PlanRequest, week: Int, slot: Int): String? =
        request.lockedSessionDays.keys.firstOrNull { key ->
            val match = lockRegex.matchEntire(key) ?: return@firstOrNull false
            match.groupValues[1].toInt() == week && match.groupValues[2].toInt() == slot
        }

    private fun lockSlots(request: PlanRequest): Map<Pair<Int, Int>, LocalDate> =
        request.lockedSessionDays.map { (key, rawDate) ->
            val match = lockRegex.matchEntire(key)
                ?: throw PlanConstraint("CONDITIONING_SESSION_LOCK_UNSUPPORTED: Invalid lock key $key.")
            (match.groupValues[1].toInt() to match.groupValues[2].toInt()) to LocalDate.parse(rawDate)
        }.toMap()

    private fun spacedWeekdays(available: Set<Int>, count: Int): List<Int> {
        val days = available.sorted()
        if (days.size < count) throw PlanConstraint("CONDITIONING_AVAILABILITY_UNSUPPORTED: Not enough available days.")
        val options = mutableListOf<List<Int>>()
        fun pick(from: Int, chosen: List<Int>) {
            if (chosen.size == count) { options += chosen; return }
            for (i in from until days.size) pick(i + 1, chosen + days[i])
        }
        pick(0, emptyList())
        return options.maxWithOrNull(
            compareBy<List<Int>> { slots ->
                (slots.zipWithNext().map { (a, b) -> b - a } + (slots.first() + 7 - slots.last())).minOrNull() ?: 7
            }.thenBy { it.last() - it.first() }
        )!!
    }
}
