package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

internal data class Engine17ProgramResult(
    val snapshot: PlanSnapshot,
    val weeklyMuscleLedgers: List<UnifiedLocalMuscleLedger>,
    val sessionDurationIntervals: Map<String, CalisthenicsDurationInterval>,
    val coachNotesBySession: Map<String, List<String>>
)

/**
 * Engine 1.7 Calisthenics Intelligence Multi-Week Journey Builder (`ENGINE17_VERSION = "1.7.0"`):
 * - Freeletics-inspired Journey progression across all 8 Core Track 6 Domains
 * - Unblocks "Handstand foundations" & routes all 30 ExpandedExerciseCatalog progressions
 * - Separates Fresh Skill Practice FIRST from Strength / Isometric / Technique sets
 * - Enforces POL-047 novice eccentric tendon caps & Oranchuk 2019 isometric progressions
 * - Exports into Engine 1.6 UnifiedLocalMuscleLedger (Observed vs. Projected)
 * - Deterministic validateSaved(plan) strictly from plan.request
 */
internal object Engine17CalisthenicsProgramBuilder {

    private val lockRegex = Regex(""".*-w([1-9][0-9]*)-cal(0|[1-9][0-9]*)$""")

    fun validateRequest(
        request: PlanRequest,
        safetyRestrictions: AthleteSafetyRestrictionSet? = null,
        evaluatedAtUtc: Instant = Instant.now()
    ) {
        if (safetyRestrictions?.hasHardBlock(AthleteDiscipline.CALISTHENICS, evaluatedAtUtc) == true ||
            request.effectiveSafetyCap("cal", safetyRestrictions, evaluatedAtUtc) == SafetyIntensityCap.REST_ONLY
        ) {
            throw PlanConstraint("SAFETY_RESTRICTION_HOLD: Calisthenics planning is paused while a safety restriction is active.")
        }
        if (request.policyChoice != "engine15" ||
            request.runDays != 0 ||
            request.strengthDays != 0 ||
            request.conditioningDays != 0 ||
            request.calDays !in 1..6 ||
            request.weeks !in 1..16 ||
            request.qualityCount != 0 ||
            request.days.isEmpty() ||
            request.days.any { it !in 1..7 } ||
            request.days.size < request.calDays ||
            request.calLevel !in 0..2 ||
            request.pullLevel !in 0..2 ||
            request.coreLevel !in 0..2 ||
            (request.timeBudgetEnabled && request.minutes !in 15..120) ||
            request.equipment.values.any { it !in 0..20 } ||
            request.disciplineGoals.keys.any { it != "cal" } ||
            request.weeklySlots.keys.any { it != "cal" } ||
            request.limitations.isNotBlank() ||
            request.recoveryNotes.isNotBlank()
        ) {
            throw PlanConstraint("CALISTHENICS_REQUEST_UNSUPPORTED: Choose 1 to 6 calisthenics sessions, valid weekdays, and resolve notes before generating.")
        }
        val goal = request.disciplineGoal("cal")
        if (goal == "Pull-up progression" &&
            (request.equipment["bar"] ?: 0) == 0 &&
            (request.equipment["pullup_bar"] ?: 0) == 0
        ) {
            throw PlanConstraint("CALISTHENICS_PULLUP_BAR_REQUIRED: Pull-up progression requires an explicitly reported secure bar.")
        }
        val explicit = request.weeklySlots["cal"]
        if (explicit != null && (explicit.size != request.calDays ||
                explicit.distinct().size != request.calDays ||
                explicit.any { it !in request.days })
        ) {
            throw PlanConstraint("CALISTHENICS_WEEKDAY_ASSIGNMENTS_INVALID: Assign each session to a distinct available weekday.")
        }
        if (request.lockedSessionDays.keys.any { !lockRegex.matches(it) }) {
            throw PlanConstraint("CALISTHENICS_SESSION_LOCK_UNSUPPORTED: A lock identifies an invalid calisthenics week or slot.")
        }
        Engine15SessionLocks.validateRequest(request)
    }

    fun build(
        request: PlanRequest,
        owner: String,
        id: String,
        now: Instant,
        allowCalendarReschedule: Boolean = false,
        safetyRestrictions: AthleteSafetyRestrictionSet? = null,
        evaluatedAtUtc: Instant = now
    ): Engine17ProgramResult {
        validateRequest(request, safetyRestrictions, evaluatedAtUtc)
        RecordPolicy.identifier(owner)
        RecordPolicy.identifier(id)

        val objective = CalisthenicsObjective.fromGoalString(request.disciplineGoal("cal"))
        val spec = Engine17CalisthenicsPolicyRegistry.specFor(objective)
        val availableTokens = Engine17CalisthenicsDagCatalog.availableEquipmentTokens(request)
        val nodes = Engine17CalisthenicsDagCatalog.buildNodes()
        val weekdays = request.weeklySlots["cal"] ?: spacedWeekdays(request.days, request.calDays)
        val locks = lockSlots(request)

        val safetyCap = request.effectiveSafetyCap("cal", safetyRestrictions, evaluatedAtUtc)
        val dailyBudget = if (request.timeBudgetEnabled) request.minutes else null

        val sessions = mutableListOf<PlannedSession>()
        val weeklyLedgers = mutableListOf<UnifiedLocalMuscleLedger>()
        val durationMap = LinkedHashMap<String, CalisthenicsDurationInterval>()
        val notesBySession = LinkedHashMap<String, List<String>>()

        for (week in 1..request.weeks) {
            val weekStart = request.start.plusWeeks((week - 1).toLong())
            val weekStartDow = weekStart.dayOfWeek.value
            val isRecoveryOrDeload = (week % 4 == 0) || (request.returning && week <= 2)
            val rung = (week - 1) % 4
            val blockIndex = (week - 1) / 4
            val setMult = when {
                request.returning && week == 1 -> 0.65
                isRecoveryOrDeload -> 0.60
                else -> 1.0
            }
            val rirOverride = if (isRecoveryOrDeload) {
                Engine17CalisthenicsPolicyRegistry.POL_049_DELOAD_TARGET_RIR_MIN..
                    Engine17CalisthenicsPolicyRegistry.POL_049_DELOAD_TARGET_RIR_MAX
            } else null
            val isoSecs = when {
                isRecoveryOrDeload -> Engine17CalisthenicsPolicyRegistry.ISOMETRIC_STAGE_1_SECONDS
                rung == 1 && blockIndex == 0 -> Engine17CalisthenicsPolicyRegistry.ISOMETRIC_STAGE_2_SECONDS
                rung >= 2 || blockIndex >= 1 ->
                    if (request.coreLevel >= 1) Engine17CalisthenicsPolicyRegistry.ISOMETRIC_STAGE_3_SECONDS
                    else Engine17CalisthenicsPolicyRegistry.ISOMETRIC_STAGE_2_SECONDS
                else -> Engine17CalisthenicsPolicyRegistry.ISOMETRIC_STAGE_1_SECONDS
            }

            val weekSlots = mutableListOf<CalisthenicsWorkoutSlot>()
            for (slot in 0 until request.calDays) {
                val targetDow = weekdays[slot]
                val naturalDate = weekStart.plusDays(((targetDow - weekStartDow + 7) % 7).toLong())
                val lockDate = locks[week to slot]
                if (lockDate != null && ((!allowCalendarReschedule && lockDate != naturalDate) ||
                        lockDate.dayOfWeek.value !in request.days)
                ) {
                    throw PlanConstraint("CALISTHENICS_SESSION_LOCK_CONFLICT: A locked date conflicts with its weekday or plan week.")
                }

                val calVariant = calVariantIndexFor(request.calDays, slot)
                val constructed = Engine17CalisthenicsWorkoutConstructor.construct(
                    input = CalisthenicsSessionConstructionInput(
                        objective = objective,
                        calDaysPerWeek = request.calDays,
                        slotIndex = slot,
                        experience = request.calLevel,
                        pullLevel = request.pullLevel,
                        coreLevel = request.coreLevel,
                        availableEquipment = availableTokens,
                        excludedExerciseIds = request.excluded,
                        dailyTimeBudgetMinutes = dailyBudget,
                        safetyIntensityCap = safetyCap,
                        setMultiplier = setMult,
                        isometricOverrideSeconds = isoSecs,
                        targetRirOverride = rirOverride,
                        isDeloadOrRecovery = isRecoveryOrDeload,
                        variantIndex = calVariant
                    ),
                    allNodes = nodes
                )

                val blockRepBoost = if (!isRecoveryOrDeload) blockIndex.coerceAtMost(2) else 0
                var progressedSlots = constructed.slots.map { s ->
                    if (s.holdSeconds != null || s.blockRole == CalisthenicsBlockRole.FRESH_SKILL_PRACTICE ||
                        s.node.isEccentricHighTendonStrain || isRecoveryOrDeload
                    ) {
                        s
                    } else {
                        val repSpan = (spec.defaultRepRange.last - spec.defaultRepRange.first).coerceAtLeast(1)
                        val bumpedReps = ((s.reps ?: spec.defaultRepRange.first) + (rung + blockRepBoost).coerceAtMost(repSpan))
                            .coerceAtMost(spec.defaultRepRange.last)
                        s.copy(reps = bumpedReps)
                    }
                }
                var finalInterval = Engine17CalisthenicsDurationCalculator.estimateSessionDuration(
                    progressedSlots,
                    constructed.isCompactFallback
                )
                if (dailyBudget != null && finalInterval.maxSeconds > dailyBudget * 60) {
                    progressedSlots = constructed.slots
                    finalInterval = constructed.durationInterval
                }

                weekSlots += progressedSlots
                val sessionId = matchingLockId(request, week, slot) ?: "$id-w$week-cal$slot"
                durationMap[sessionId] = finalInterval
                notesBySession[sessionId] = constructed.coachNotes

                val exercises = progressedSlots.map { sl ->
                    val ex = sl.node.exercise
                    val eqText = ex.equipmentOptions.firstOrNull { opt ->
                        opt.isEmpty() || availableTokens.containsAll(opt)
                    }?.sorted()?.joinToString(" + ") { it.replace('_', ' ') }
                        ?.ifBlank { "Bodyweight" } ?: "Bodyweight"
                    val baseCue = ex.techniqueCues.firstOrNull().orEmpty()
                    val roleTag = when (sl.blockRole) {
                        CalisthenicsBlockRole.FRESH_SKILL_PRACTICE -> "[Skill ${sl.timeBoxMinutes ?: 6}m] "
                        CalisthenicsBlockRole.PREREQUISITE_CLUSTER -> "[Prereq] "
                        CalisthenicsBlockRole.ISOMETRIC_HOLD -> "[Hold] "
                        CalisthenicsBlockRole.TECHNIQUE_INTERVAL_FINISHER -> "[Technique] "
                        else -> ""
                    }
                    ExercisePrescription(
                        id = ex.id,
                        name = ex.name,
                        sets = sl.sets,
                        reps = sl.reps,
                        seconds = sl.holdSeconds,
                        rest = sl.restSeconds,
                        equipment = eqText,
                        cue = "${roleTag}${baseCue} Keep ${sl.targetRir.first} to ${sl.targetRir.last} RIR and stop before form breaks.".trim()
                    )
                }

                val steps = buildList {
                    add(
                        if (constructed.isLowerBodySession) {
                            if (constructed.isCompactFallback) "3 min ankle, hip, and glute prep"
                            else "5 min ankle rocks, hip switches, and single-leg stability prep"
                        } else {
                            if (constructed.isCompactFallback) "3 min wrist, shoulder, and scapular warm-up"
                            else "5 min wrist, shoulder, and scapular activation prep"
                        }
                    )
                    if (progressedSlots.any { it.blockRole == CalisthenicsBlockRole.FRESH_SKILL_PRACTICE }) {
                        add("Perform the skill balance block FIRST while fresh before strength sets.")
                    }
                    add("Technical failure ends the set early: only promote progressions after 2 consecutive clean sessions.")
                    add("2 to 4 min cool-down and light mobility decompression")
                }

                val reason = if (isRecoveryOrDeload) {
                    "Deload and consolidation week: trimmed sets preserving clean movement control at 3 to 4 RIR."
                } else {
                    "Freeletics-inspired calisthenics journey: progress reps or hold seconds cleanly before advancing DAG nodes."
                }

                sessions += PlannedSession(
                    id = sessionId,
                    date = naturalDate,
                    discipline = "cal",
                    title = "${constructed.dayLabel} (${objective.label})",
                    minutes = finalInterval.conservativeMinutesCeiling,
                    distanceKm = null,
                    kind = "calisthenics",
                    lower = constructed.isLowerBodySession,
                    quality = false,
                    exercises = exercises,
                    steps = steps,
                    reason = reason
                )
            }

            weeklyLedgers += Engine17CalisthenicsProgressionEngine.exportToSharedLocalLedger(
                accountId = owner,
                weekStartInclusive = weekStart,
                weekEndInclusive = weekStart.plusDays(6),
                slots = weekSlots
            )
        }

        val sorted = sessions.sortedWith(compareBy<PlannedSession> { it.date }.thenBy { it.id })
        if (!allowCalendarReschedule) Engine15SessionLocks.validateOutput(request, sorted)
        if (sorted.size != request.weeks * request.calDays || sorted.map { it.date }.distinct().size != sorted.size) {
            throw PlanConstraint("CALISTHENICS_PLAN_INCOMPLETE: Every requested calisthenics slot must occupy a distinct weekday.")
        }

        val snapshot = PlanSnapshot(
            id = id,
            owner = owner,
            engineVersion = ENGINE17_VERSION,
            policyId = ENGINE17_CALISTHENICS_POLICY,
            createdAt = now,
            request = request,
            sessions = sorted,
            explanations = listOf(
                "Engine 1.7 Calisthenics Intelligence journey using an AND/OR prerequisite DAG across all 8 movement domains.",
                "Fresh skill practice precedes strength leverage sets; slow eccentrics stay tendon-safe (max 2 sets x 3 reps for novices).",
                "DAG advancements require 2 consecutive clean sessions with zero pain or form collapse.",
                "This is a draft. Review movements, equipment, and technical stop rules before explicit approval."
            ),
            name = "Engine 1.7 ${objective.label} Plan"
        )
        return Engine17ProgramResult(snapshot, weeklyLedgers, durationMap, notesBySession)
    }

    fun validateSaved(plan: PlanSnapshot, allowCalendarReschedule: Boolean = false) {
        if (plan.engineVersion != ENGINE17_VERSION || plan.policyId != ENGINE17_CALISTHENICS_POLICY) {
            throw PlanConstraint("The saved Engine 1.7 calisthenics plan has an unsupported engine identity.")
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
            throw PlanConstraint("The saved Engine 1.7 calisthenics plan no longer matches its session slots.")
        }
        for (session in plan.sessions) {
            val expectedSession = expectedById.getValue(session.id)
            val base = session.baselineSession()
            if (base.discipline != "cal" ||
                base.kind != "calisthenics" ||
                base.quality ||
                base.distanceKm != null ||
                session.date.dayOfWeek.value !in plan.request.days ||
                (!allowCalendarReschedule && session.date != expectedSession.date) ||
                base.exercises.map { it.id } != expectedSession.exercises.map { it.id } ||
                base.exercises.map { it.sets } != expectedSession.exercises.map { it.sets } ||
                base.exercises.map { it.reps } != expectedSession.exercises.map { it.reps } ||
                base.exercises.map { it.seconds } != expectedSession.exercises.map { it.seconds } ||
                !Engine15WorkoutDifficultyAdjuster.matchesAdjusted(session, plan.request)
            ) {
                throw PlanConstraint("The saved Engine 1.7 calisthenics plan no longer matches its workout and calendar contract.")
            }
        }
        Engine15SessionLocks.validateOutput(plan.request, plan.sessions)
    }

    private fun calVariantIndexFor(calDays: Int, slot: Int): Int = when (calDays) {
        4 -> slot / 2
        5 -> if (slot >= 3) 1 else 0
        6 -> slot / 3
        else -> slot
    }

    private fun matchingLockId(request: PlanRequest, week: Int, slot: Int): String? =
        request.lockedSessionDays.keys.firstOrNull { key ->
            val match = lockRegex.matchEntire(key) ?: return@firstOrNull false
            match.groupValues[1].toInt() == week && match.groupValues[2].toInt() == slot
        }

    private fun lockSlots(request: PlanRequest): Map<Pair<Int, Int>, LocalDate> =
        request.lockedSessionDays.map { (key, rawDate) ->
            val match = lockRegex.matchEntire(key)
                ?: throw PlanConstraint("CALISTHENICS_SESSION_LOCK_UNSUPPORTED: Invalid lock format $key.")
            (match.groupValues[1].toInt() to match.groupValues[2].toInt()) to LocalDate.parse(rawDate)
        }.toMap()

    private fun spacedWeekdays(available: Set<Int>, count: Int): List<Int> {
        if (available.size < count) {
            throw PlanConstraint("CALISTHENICS_AVAILABILITY_UNSUPPORTED: Available days must cover all sessions.")
        }
        val choices = available.sorted()
        val result = mutableListOf<List<Int>>()
        fun add(start: Int, chosen: List<Int>) {
            if (chosen.size == count) {
                result += chosen
                return
            }
            for (i in start until choices.size) add(i + 1, chosen + choices[i])
        }
        add(0, emptyList())
        return result.maxWithOrNull(
            compareBy<List<Int>> { days ->
                (days.zipWithNext().map { (a, b) -> b - a } + (days.first() + 7 - days.last())).minOrNull() ?: 7
            }.thenBy { it.last() - it.first() }
        ) ?: choices.take(count)
    }
}
