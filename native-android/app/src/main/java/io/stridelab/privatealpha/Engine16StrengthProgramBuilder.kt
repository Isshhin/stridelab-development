package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

internal enum class StrengthMesocyclePhase(val displayLabel: String) {
    ACCUMULATION("Accumulation"),
    TRANSMUTATION("Strength focus"),
    REALIZATION("Peak expression"),
    MAINTENANCE("Maintenance"),
    CONDITIONAL_DELOAD("Recovery deload"),
    TAPER("Peak taper")
}

internal data class Engine16ProgramResult(
    val snapshot: PlanSnapshot,
    val weeklyMuscleLedgers: List<UnifiedLocalMuscleLedger>,
    val sessionDurationIntervals: Map<String, StrengthDurationInterval>,
    val weeklyPhaseByWeek: Map<Int, StrengthMesocyclePhase>
)

/**
 * Orchestrates Engine 1.6 multi-week Hevy-style strength training plans with:
 * - Independent objective-driven mesocycle phasing (Accumulation -> Transmutation -> Realization/Deload/Taper)
 * - Deterministic 1 to 6 days/week split construction and MOD-13 time compliance
 * - Non-failure RIR defaults, ramp-set separation, and zero em-dashes in copy
 * - Unified projected local muscle-group ledgers per week
 * - Pure deterministic validateSaved(plan) replay strictly from plan.request
 */
internal object Engine16StrengthProgramBuilder {

    private val lockRegex = Regex(""".*-w([1-9][0-9]*)-str(0|[1-9][0-9]*)$""")

    fun validateRequest(
        request: PlanRequest,
        safetyRestrictions: AthleteSafetyRestrictionSet? = null,
        evaluatedAtUtc: Instant = Instant.now()
    ) {
        if (safetyRestrictions?.hasHardBlock(AthleteDiscipline.STRENGTH, evaluatedAtUtc) == true ||
            safetyRestrictions?.effectiveIntensityCap(AthleteDiscipline.STRENGTH, evaluatedAtUtc) == SafetyIntensityCap.REST_ONLY
        ) {
            throw PlanConstraint("SAFETY_RESTRICTION_HOLD: Strength planning is paused while a safety restriction is active.")
        }
        if (request.policyChoice != "engine15" ||
            request.runDays != 0 ||
            request.calDays != 0 ||
            request.conditioningDays != 0 ||
            request.strengthDays !in 1..6 ||
            request.weeks !in 1..16 ||
            request.qualityCount != 0 ||
            request.days.isEmpty() ||
            request.days.any { it !in 1..7 } ||
            request.days.size < request.strengthDays ||
            request.strengthExperience !in 0..2 ||
            (request.timeBudgetEnabled && request.minutes !in 15..120) ||
            request.equipment.values.any { it !in 0..20 } ||
            request.disciplineGoals.keys.any { it != "str" } ||
            request.weeklySlots.keys.any { it != "str" } ||
            request.limitations.isNotBlank() ||
            request.recoveryNotes.isNotBlank()
        ) {
            throw PlanConstraint(
                "STRENGTH_REQUEST_UNSUPPORTED: Choose 1 to 6 strength sessions, valid available weekdays, and resolve any notes before generating."
            )
        }
        val explicit = request.weeklySlots["str"]
        if (explicit != null && (explicit.size != request.strengthDays ||
                explicit.distinct().size != request.strengthDays ||
                explicit.any { it !in request.days })
        ) {
            throw PlanConstraint("STRENGTH_WEEKDAY_ASSIGNMENTS_INVALID: Each strength session needs a distinct available weekday.")
        }
        if (request.lockedSessionDays.keys.any { !lockRegex.matches(it) }) {
            throw PlanConstraint("STRENGTH_SESSION_LOCK_UNSUPPORTED: A lock must identify a strength week and slot.")
        }
        Engine15SessionLocks.validateRequest(request)
    }

    fun assignMesocyclePhases(
        objective: StrengthObjective,
        weeks: Int,
        experience: Int
    ): Map<Int, StrengthMesocyclePhase> {
        require(weeks in 1..16)
        return (1..weeks).associateWith { week ->
            when (objective) {
                StrengthObjective.STRENGTH_MAINTENANCE -> StrengthMesocyclePhase.MAINTENANCE
                StrengthObjective.RUNNING_SUPPORTIVE -> when {
                    weeks >= 4 && week == weeks -> StrengthMesocyclePhase.MAINTENANCE
                    weeks >= 4 && week % 4 == 0 -> StrengthMesocyclePhase.CONDITIONAL_DELOAD
                    else -> StrengthMesocyclePhase.ACCUMULATION
                }
                StrengthObjective.MAXIMAL_STRENGTH -> when {
                    weeks >= 6 && week == weeks -> StrengthMesocyclePhase.TAPER
                    week % 4 == 0 && week < weeks -> StrengthMesocyclePhase.CONDITIONAL_DELOAD
                    experience >= 1 && weeks >= 6 && week >= weeks - 2 -> StrengthMesocyclePhase.REALIZATION
                    weeks >= 4 && week > weeks / 2 -> StrengthMesocyclePhase.TRANSMUTATION
                    else -> StrengthMesocyclePhase.ACCUMULATION
                }
                StrengthObjective.HYPERTROPHY,
                StrengthObjective.GENERAL_STRENGTH,
                StrengthObjective.STRENGTH_ENDURANCE,
                StrengthObjective.POWER_DEVELOPMENT -> when {
                    weeks >= 4 && week % 4 == 0 -> StrengthMesocyclePhase.CONDITIONAL_DELOAD
                    weeks >= 6 && week > (weeks * 2) / 3 -> StrengthMesocyclePhase.TRANSMUTATION
                    else -> StrengthMesocyclePhase.ACCUMULATION
                }
            }
        }
    }

    fun build(
        request: PlanRequest,
        owner: String,
        id: String,
        now: Instant,
        allowCalendarReschedule: Boolean = false,
        safetyRestrictions: AthleteSafetyRestrictionSet? = null,
        runningMajorStepWeeks: Set<Int> = emptySet()
    ): Engine16ProgramResult {
        RecordPolicy.identifier(owner)
        RecordPolicy.identifier(id)
        validateRequest(request, safetyRestrictions, now)

        val objective = StrengthObjective.fromRequestGoal(request.disciplineGoal("str"))
        val spec = Engine16StrengthPolicyRegistry.defaultsFor(objective, request.strengthExperience)
        val availableTokens = Engine16StrengthExerciseCatalog.availableEquipmentTokens(request)
        val catalog = Engine16StrengthExerciseCatalog.allMetadata(library = null)
        val safetyCap = safetyRestrictions?.effectiveIntensityCap(AthleteDiscipline.STRENGTH, now)

        val weekdays = request.weeklySlots["str"] ?: evenlySpacedWeekdays(
            start = request.start,
            available = request.days,
            count = request.strengthDays
        )
        val locks = parseLocks(request)
        val phases = assignMesocyclePhases(objective, request.weeks, request.strengthExperience)
        val dailyBudget = if (request.timeBudgetEnabled) request.minutes else null

        val sessions = mutableListOf<PlannedSession>()
        val weeklyLedgers = mutableListOf<UnifiedLocalMuscleLedger>()
        val durationMap = linkedMapOf<String, StrengthDurationInterval>()

        for (week in 1..request.weeks) {
            val phase = phases.getValue(week)
            val weekStart = request.start.plusWeeks((week - 1).toLong())
            val weekStartDow = weekStart.dayOfWeek.value
            val weekEnd = weekStart.plusDays(6)
            val rungInBlock = (week - 1) % 4
            val runningHold = week in runningMajorStepWeeks

            val setMult = when (phase) {
                StrengthMesocyclePhase.CONDITIONAL_DELOAD ->
                    Engine16StrengthPolicyRegistry.DELOAD_DEFAULT_SET_RETAIN_FRACTION
                StrengthMesocyclePhase.TAPER -> 0.50
                StrengthMesocyclePhase.ACCUMULATION -> if (objective == StrengthObjective.HYPERTROPHY && rungInBlock == 2 && request.strengthExperience > 0) 1.25 else 1.0
                else -> 1.0
            }
            val repOverride = when {
                phase == StrengthMesocyclePhase.REALIZATION && objective == StrengthObjective.MAXIMAL_STRENGTH ->
                    3..4
                phase == StrengthMesocyclePhase.TRANSMUTATION && objective == StrengthObjective.MAXIMAL_STRENGTH ->
                    3..5
                else -> null
            }
            val rirOverride = when (phase) {
                StrengthMesocyclePhase.CONDITIONAL_DELOAD ->
                    Engine16StrengthPolicyRegistry.DELOAD_TARGET_RIR_MIN..Engine16StrengthPolicyRegistry.DELOAD_TARGET_RIR_MAX
                StrengthMesocyclePhase.TAPER -> 2..3
                else -> null
            }

            val weekProjectedPlanned = mutableListOf<Triple<StrengthExerciseMetadata, Int, IntRange>>()

            for (slot in 0 until request.strengthDays) {
                val targetDow = weekdays[slot]
                val naturalDate = weekStart.plusDays(((targetDow - weekStartDow + 7) % 7).toLong())
                val lockDate = locks[week to slot]
                if (lockDate != null && ((!allowCalendarReschedule && lockDate != naturalDate) ||
                        lockDate.dayOfWeek.value !in request.days)
                ) {
                    throw PlanConstraint("STRENGTH_SESSION_LOCK_CONFLICT: A locked date conflicts with its fixed weekday or plan week.")
                }

                val constructed = Engine16StrengthWorkoutConstructor.construct(
                    input = StrengthWorkoutConstructionInput(
                        objective = objective,
                        strengthDaysPerWeek = request.strengthDays,
                        slotIndex = slot,
                        experience = request.strengthExperience,
                        availableEquipment = availableTokens,
                        excludedExerciseIds = request.excluded,
                        dailyTimeBudgetMinutes = dailyBudget,
                        safetyIntensityCap = safetyCap,
                        setMultiplier = setMult,
                        repOverride = repOverride,
                        targetRirOverride = rirOverride,
                        preferAlternatingVariant = (slot % 2 == 1),
                        isDeloadOrRecovery = phase in setOf(
                            StrengthMesocyclePhase.CONDITIONAL_DELOAD,
                            StrengthMesocyclePhase.TAPER
                        )
                    ),
                    catalog = catalog
                )

                val sessionId = matchingLockId(request, week, slot) ?: "$id-w$week-str$slot"
                val finalSlots = applyWeekProgression(
                    slots = constructed.slots,
                    phase = phase,
                    rung = rungInBlock,
                    runningHold = runningHold
                )

                finalSlots.forEach { s ->
                    weekProjectedPlanned += Triple(s.exercise, s.workingSets, s.targetRir)
                }

                val prescriptions = finalSlots.map { slotItem ->
                    val ex = slotItem.exercise
                    val eqText = ex.equipmentOptions.firstOrNull { opt ->
                        opt.isEmpty() || availableTokens.containsAll(opt)
                    }?.sorted()?.joinToString(" + ") { it.replace('_', ' ') }
                        ?.ifBlank { "Bodyweight" } ?: "Bodyweight"
                    val baseCue = ex.techniqueCues.firstOrNull().orEmpty()
                    val rirLabel = if (slotItem.targetRir.first == slotItem.targetRir.last) {
                        "${slotItem.targetRir.first} RIR"
                    } else {
                        "${slotItem.targetRir.first} to ${slotItem.targetRir.last} RIR"
                    }
                    val supersetPrefix = slotItem.supersetGroupLabel?.let { "[$it] " } ?: ""
                    val rampSuffix = if (slotItem.rampSets > 0) " Complete 1 light warm-up ramp set first." else ""
                    val holdNote = if (runningHold && ex.isLowerBody) " Hold steady alongside high running load." else ""
                    ExercisePrescription(
                        id = ex.id,
                        name = ex.name,
                        sets = slotItem.workingSets,
                        reps = slotItem.prescribedReps,
                        seconds = slotItem.prescribedSeconds,
                        rest = slotItem.restSeconds,
                        equipment = eqText,
                        cue = "${supersetPrefix}${baseCue} Keep $rirLabel.$rampSuffix$holdNote".trim()
                    )
                }

                val steps = buildList {
                    add(
                        if (constructed.isMedFallback) "3 to 4 min compact warm-up and joint prep"
                        else "5 to 8 min general warm-up and movement prep"
                    )
                    if (constructed.rampNotes.isNotEmpty()) {
                        addAll(constructed.rampNotes)
                    }
                    if (constructed.pairingMode == StrengthPairingMode.ANTAGONIST_SUPERSET) {
                        add("Alternate paired antagonist superset exercises with 20 s transitions and listed round rest.")
                    } else {
                        add("Complete each movement with controlled tempo and listed rest between working sets.")
                    }
                    add("2 to 5 min unhurried cool-down")
                }

                val reason = buildString {
                    append(phase.displayLabel).append(" week. ")
                    when (phase) {
                        StrengthMesocyclePhase.CONDITIONAL_DELOAD ->
                            append("Working sets trimmed while maintaining comfortable load at 3 to 4 RIR.")
                        StrengthMesocyclePhase.TAPER ->
                            append("Lower volume taper preserving strength readiness.")
                        StrengthMesocyclePhase.MAINTENANCE ->
                            append("Low-volume heavy maintenance preserving top-end strength cleanly.")
                        else ->
                            append("Non-failure double progression anchored to submaximal working sets and RIR.")
                    }
                    if (runningHold && constructed.isLowerBodySession) {
                        append(" Lower-body volume held steady for concurrent running load.")
                    }
                }

                val isRunnerSupportGoal = request.disciplineGoal("str") == "Strength for Runners"
                val plannedMinutes = constructed.durationInterval.conservativeMinutesCeiling
                val sessionTitle = if (isRunnerSupportGoal) "Strength for Runners"
                    else "${constructed.dayLabel} (${objective.label})"
                durationMap[sessionId] = constructed.durationInterval
                sessions += PlannedSession(
                    id = sessionId,
                    date = naturalDate,
                    discipline = "str",
                    title = sessionTitle,
                    minutes = plannedMinutes,
                    distanceKm = null,
                    kind = "strength",
                    lower = constructed.isLowerBodySession,
                    quality = false,
                    exercises = prescriptions,
                    steps = steps,
                    reason = reason
                )
            }

            weeklyLedgers += Engine16LocalMuscleLedgerBuilder.buildProjectedForWeek(
                accountId = owner,
                weekStartInclusive = weekStart,
                weekEndInclusive = weekEnd,
                plannedExercises = weekProjectedPlanned
            )
        }

        val sortedSessions = sessions.sortedWith(compareBy<PlannedSession> { it.date }.thenBy { it.id })
        if (!allowCalendarReschedule) {
            Engine15SessionLocks.validateOutput(request, sortedSessions)
        }
        if (sortedSessions.size != request.weeks * request.strengthDays ||
            sortedSessions.map { it.date }.distinct().size != sortedSessions.size
        ) {
            throw PlanConstraint("STRENGTH_PLAN_INCOMPLETE: Every strength slot must have one distinct dated workout.")
        }

        val explanations = buildList {
            add("Engine 1.6 Strength Intelligence plan built from your goal, equipment, schedule, and conservative MOD-13 duration bounds.")
            add("Split: ${Engine16StrengthWorkoutConstructor.dayBlueprint(objective, request.strengthDays, 0, request.strengthExperience).splitKind.name.lowercase().replace('_', ' ')}; sets stay non-failure at ${spec.targetRir.first} to ${spec.targetRir.last} RIR with explicit warm-up ramp separation.")
            add("Progressive overload climbs reps cleanly inside goal ranges and bridges coarse fixed-equipment steps instead of forcing abrupt load jumps.")
            add("This is a draft. Review movements, equipment, and effort guidelines before explicit approval.")
        }

        val snapshot = PlanSnapshot(
            id = id,
            owner = owner,
            engineVersion = ENGINE16_VERSION,
            policyId = ENGINE16_STRENGTH_POLICY,
            createdAt = now,
            request = request,
            sessions = sortedSessions,
            explanations = explanations,
            name = "Engine 1.6 ${objective.label} Plan"
        )
        return Engine16ProgramResult(
            snapshot = snapshot,
            weeklyMuscleLedgers = weeklyLedgers,
            sessionDurationIntervals = durationMap,
            weeklyPhaseByWeek = phases
        )
    }

    fun validateSaved(plan: PlanSnapshot, allowCalendarReschedule: Boolean = false) {
        if (plan.engineVersion != ENGINE16_VERSION || plan.policyId != ENGINE16_STRENGTH_POLICY) {
            throw PlanConstraint("The saved Engine 1.6 strength plan has an unsupported engine identity.")
        }
        validateRequest(plan.request)
        val expected = build(
            request = plan.request,
            owner = plan.owner,
            id = plan.id,
            now = plan.createdAt,
            allowCalendarReschedule = allowCalendarReschedule
        ).snapshot
        val expectedById = expected.sessions.associateBy { it.id }
        if (plan.sessions.size != expectedById.size ||
            plan.sessions.map { it.id }.toSet() != expectedById.keys
        ) {
            throw PlanConstraint("The saved Engine 1.6 strength plan no longer matches its expected session count.")
        }
        for (session in plan.sessions) {
            val target = expectedById.getValue(session.id)
            val base = session.baselineSession()
            if (base.discipline != "str" ||
                base.kind != "strength" ||
                base.quality ||
                base.exercises != target.exercises ||
                base.lower != target.lower ||
                (!allowCalendarReschedule && session.date != target.date) ||
                session.date < plan.request.start ||
                session.date >= plan.request.start.plusWeeks(plan.request.weeks.toLong()) ||
                session.date.dayOfWeek.value !in plan.request.days ||
                !Engine15WorkoutDifficultyAdjuster.matchesAdjusted(session, plan.request)
            ) {
                throw PlanConstraint("The saved Engine 1.6 strength plan no longer matches its exercise and calendar contract.")
            }
        }
        if (!allowCalendarReschedule) {
            Engine15SessionLocks.validateOutput(plan.request, plan.sessions)
        }
    }

    private fun applyWeekProgression(
        slots: List<ConstructedExerciseSlot>,
        phase: StrengthMesocyclePhase,
        rung: Int,
        runningHold: Boolean
    ): List<ConstructedExerciseSlot> = slots.map { slot ->
        if (phase in setOf(
                StrengthMesocyclePhase.CONDITIONAL_DELOAD,
                StrengthMesocyclePhase.TAPER,
                StrengthMesocyclePhase.MAINTENANCE
            )
        ) {
            return@map slot
        }
        if (runningHold && slot.exercise.isLowerBody) {
            return@map slot
        }
        if (slot.prescribedSeconds != null) {
            val targetSec = (slot.prescribedSeconds + rung * 5).coerceAtMost(slot.repRange.last)
            slot.copy(prescribedSeconds = targetSec)
        } else if (slot.prescribedReps != null) {
            val targetReps = (slot.prescribedReps + rung).coerceAtMost(slot.repRange.last)
            slot.copy(prescribedReps = targetReps)
        } else slot
    }

    private fun matchingLockId(request: PlanRequest, week: Int, slot: Int): String? =
        request.lockedSessionDays.keys.firstOrNull { key ->
            lockRegex.matchEntire(key)?.let {
                it.groupValues[1].toInt() == week && it.groupValues[2].toInt() == slot
            } == true
        }

    private fun parseLocks(request: PlanRequest): Map<Pair<Int, Int>, LocalDate> {
        val out = linkedMapOf<Pair<Int, Int>, LocalDate>()
        for ((sessionId, rawDate) in request.lockedSessionDays) {
            val match = lockRegex.matchEntire(sessionId)
                ?: throw PlanConstraint("STRENGTH_SESSION_LOCK_UNSUPPORTED: Invalid strength session lock.")
            val week = match.groupValues[1].toInt()
            val slot = match.groupValues[2].toInt()
            if (week !in 1..request.weeks || slot !in 0 until request.strengthDays) {
                throw PlanConstraint("STRENGTH_SESSION_LOCK_UNSUPPORTED: Lock points outside requested strength weeks/slots.")
            }
            val key = week to slot
            if (key in out) throw PlanConstraint("STRENGTH_SESSION_LOCK_UNSUPPORTED: Duplicate slot lock.")
            out[key] = LocalDate.parse(rawDate)
        }
        return out
    }

    private fun evenlySpacedWeekdays(start: LocalDate, available: Set<Int>, count: Int): List<Int> {
        if (available.size < count) {
            throw PlanConstraint("STRENGTH_AVAILABILITY_UNSUPPORTED: Not enough available weekdays for $count strength sessions.")
        }
        val orderedOffsets = (0..6)
            .map { start.plusDays(it.toLong()).dayOfWeek.value }
            .filter { it in available }
            .mapIndexed { _, dow -> ((dow - start.dayOfWeek.value) + 7) % 7 }
        var bestOffsets: List<Int>? = null
        var bestMinGap = -1
        var bestSpan = -1
        val maskLimit = 1 shl orderedOffsets.size
        for (mask in 1 until maskLimit) {
            if (Integer.bitCount(mask) != count) continue
            val chosen = orderedOffsets.filterIndexed { idx, _ -> (mask and (1 shl idx)) != 0 }
            val minGap = if (chosen.size <= 1) 7 else {
                (chosen.zipWithNext { a, b -> b - a } + (7 + chosen.first() - chosen.last())).min()
            }
            val span = chosen.last() - chosen.first()
            if (minGap > bestMinGap || (minGap == bestMinGap && span > bestSpan)) {
                bestMinGap = minGap
                bestSpan = span
                bestOffsets = chosen
            }
        }
        return bestOffsets!!.map { start.plusDays(it.toLong()).dayOfWeek.value }
    }
}
