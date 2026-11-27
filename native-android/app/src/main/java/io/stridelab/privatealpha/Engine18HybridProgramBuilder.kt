package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Engine 1.8 Hybrid Program Builder (`ENGINE18_VERSION = "1.8.0"`, `ENGINE18_HYBRID_POLICY`):
 * Unites Engine 1.5 Running, Engine 1.6 Strength, Engine 1.7 Calisthenics, and Engine 1.8
 * 8-Family Conditioning into a single coordinated multi-discipline `PlanSnapshot` (`PLAN_SCHEMA = 1`) with:
 * - POL-036 bidirectional spacing & optional AM/PM High/Low consolidation
 * - Cross-discipline UnifiedLocalMuscleLedger deduplication (blocking redundant conditioning muscle overlap)
 * - Running taper lower-body maintenance/drop coordination (MD-15 / S09)
 * - Exact preserved ID/slot contracts & deterministic validateSaved(plan)
 */
internal object Engine18HybridProgramBuilder {

    private val disciplines = listOf("run", "str", "cal", "cond")

    fun soloRequest(request: PlanRequest, discipline: String): PlanRequest = request.copy(
        goal = request.disciplineGoal(discipline),
        runDays = if (discipline == "run") request.runDays else 0,
        qualityCount = if (discipline == "run") request.qualityCount else 0,
        qualityDays = if (discipline == "run") request.qualityDays else emptySet(),
        strengthDays = if (discipline == "str") request.strengthDays else 0,
        calDays = if (discipline == "cal") request.calDays else 0,
        conditioningDays = if (discipline == "cond") request.conditioningDays else 0,
        weeklySlots = request.weeklySlots.filterKeys { it == discipline },
        lockedSessionDays = request.lockedSessionDays.filterKeys { Regex(".*-$discipline(0|[1-9][0-9]*)$").matches(it) },
        allowDoubleDays = discipline != "run" && request.allowDoubleDays,
        disciplineGoals = emptyMap()
    )

    fun validateRequest(
        request: PlanRequest,
        safetyRestrictions: AthleteSafetyRestrictionSet? = null,
        evaluatedAtUtc: Instant = Instant.now()
    ) {
        val active = disciplines.filter { request.disciplineSessionCount(it) > 0 }
        if (request.policyChoice != "engine15" || active.size < 2 || request.weeks !in 1..16 ||
            request.days.isEmpty() || request.days.any { it !in 1..7 } ||
            request.disciplineGoals.keys.any { it !in active } ||
            request.weeklySlots.keys.any { it !in active } ||
            (request.timeBudgetEnabled && request.dailyMinutes !in 15..240)
        ) {
            throw PlanConstraint("ENGINE18_HYBRID_REQUEST_UNSUPPORTED: Choose at least two supported disciplines, valid weekdays, and a valid daily time limit.")
        }
        active.forEach { discipline ->
            val solo = soloRequest(request, discipline)
            when (discipline) {
                "run" -> if (Engine15TimeOnlyRunWalkBuilder.isEligible(solo)) {
                    Engine15TimeOnlyRunWalkBuilder.compose(solo, "hybrid-val", "hybrid-val", Instant.EPOCH)
                } else {
                    when (val outcome = Engine15IndependentPlanGenerator.previewAutoReconciled(solo, "hybrid-val", "hybrid-val", Instant.EPOCH)) {
                        is Engine15IndependentPlanDecision.Deferred -> throw PlanConstraint("${outcome.reasonCode}: ${outcome.explanation}")
                        is Engine15IndependentPlanDecision.Candidate -> Unit
                    }
                }
                "str" -> if (FeatureFlags.USE_ENGINE16_STRENGTH_V1 && strengthTrack(solo) == "str") {
                    Engine16StrengthProgramBuilder.validateRequest(solo, safetyRestrictions, evaluatedAtUtc)
                } else {
                    Engine15StrengthWorkoutComposer.validateRequest(solo)
                }
                "cal" -> if (FeatureFlags.USE_ENGINE17_CALISTHENICS_V1) {
                    Engine17CalisthenicsProgramBuilder.validateRequest(solo, safetyRestrictions, evaluatedAtUtc)
                } else {
                    Engine15CalisthenicsWorkoutComposer.validateRequest(solo)
                }
                "cond" -> Engine18ConditioningWorkoutConstructor.validateSoloRequest(solo, safetyRestrictions, evaluatedAtUtc)
            }
        }
        validateWeeklySlots(request, active)
    }

    fun build(
        request: PlanRequest,
        owner: String,
        id: String,
        now: Instant,
        existingPlans: List<PlanSnapshot>,
        library: ExerciseLibraryV2? = null,
        activities: List<ActivityRecord> = emptyList(),
        detections: List<Engine15DetectedWorkout> = emptyList(),
        safetyRestrictions: AthleteSafetyRestrictionSet? = null
    ): PlanSnapshot {
        validateRequest(request, safetyRestrictions, now)
        RecordPolicy.identifier(owner)
        RecordPolicy.identifier(id)

        val start = request.start.minusDays(1)
        val end = request.start.plusWeeks(request.weeks.toLong())
        val externalActive = existingPlans.asSequence()
            .filter { it.owner == owner && it.status == "approved" }
            .flatMap { plan ->
                plan.sessions.asSequence()
                    .filter { it.status in setOf("planned", "in_progress") && it.date in start..end }
                    .map { plan.policyId to it }
            }.toList()

        val active = disciplines.filter { request.disciplineSessionCount(it) > 0 }
        var runCandidatePlan: PlanSnapshot? = null
        var runCandidateVolume: Engine15IndependentVolumePlanner.Result? = null
        var strResult: Engine16ProgramResult? = null
        var calResult: Engine17ProgramResult? = null

        val nonCondSessions = mutableListOf<PlannedSession>()
        var runMajorStepWeeks = emptySet<Int>()
        for (discipline in active.filter { it != "cond" }) {
            val solo = soloRequest(request, discipline)
            when (discipline) {
                "run" -> {
                    val runRows = if (Engine15TimeOnlyRunWalkBuilder.isEligible(solo)) {
                        Engine15TimeOnlyRunWalkBuilder.compose(solo, owner, id, now).sessions
                    } else {
                        val cand = Engine15IndependentPlanGenerator.previewAutoReconciled(
                            solo, owner, id, now, activities = activities, detections = detections
                        ) as? Engine15IndependentPlanDecision.Candidate
                            ?: throw PlanConstraint("RUNNING_COMPONENT_UNAVAILABLE: No independent running candidate was produced.")
                        runCandidatePlan = cand.plan
                        runCandidateVolume = cand.volume
                        cand.plan.sessions
                    }
                    runMajorStepWeeks = computeRunningHoldWeeks(runRows, request)
                    nonCondSessions += runRows
                }
                "str" -> if (FeatureFlags.USE_ENGINE16_STRENGTH_V1 && strengthTrack(solo) == "str") {
                    strResult = Engine16StrengthProgramBuilder.build(
                        solo, owner, id, now,
                        allowCalendarReschedule = true,
                        safetyRestrictions = safetyRestrictions,
                        runningMajorStepWeeks = runMajorStepWeeks
                    )
                    nonCondSessions += strResult.snapshot.sessions
                } else {
                    val resolvedLib = library ?: throw PlanConstraint(
                        "ENGINE18_COMBINED_CATALOG_REQUIRED: The offline exercise catalog is required for auxiliary strength tracks."
                    )
                    nonCondSessions += Engine15StrengthWorkoutComposer.compose(solo, id, resolvedLib, allowCalendarReschedule = true)
                }
                "cal" -> if (FeatureFlags.USE_ENGINE17_CALISTHENICS_V1) {
                    calResult = Engine17CalisthenicsProgramBuilder.build(solo, owner, id, now, allowCalendarReschedule = true, safetyRestrictions = safetyRestrictions)
                    nonCondSessions += calResult.snapshot.sessions
                } else {
                    val resolvedLib = library ?: throw PlanConstraint(
                        "ENGINE18_COMBINED_CATALOG_REQUIRED: The offline exercise catalog is required for auxiliary calisthenics tracks."
                    )
                    nonCondSessions += Engine15CalisthenicsWorkoutComposer.compose(solo, id, resolvedLib, allowCalendarReschedule = true)
                }
            }
        }

        // MD-06: Aggregate week-1 muscle ledgers from Strength + Calisthenics to guard Conditioning overlap (Scenario S12)
        val blockedForCond = if ("cond" in active) {
            val w1Ledgers = listOfNotNull(
                strResult?.weeklyMuscleLedgers?.firstOrNull(),
                calResult?.weeklyMuscleLedgers?.firstOrNull()
            )
            Engine18HybridCoordinationEngine.aggregateWeeklyUnifiedLedger(
                accountId = owner,
                weekStart = request.start,
                weekEnd = request.start.plusDays(6),
                ledgers = w1Ledgers
            ).second
        } else emptySet()

        val condSessions = if ("cond" in active) {
            Engine18ConditioningWorkoutConstructor.composeSessions(
                request = soloRequest(request, "cond"),
                id = id,
                allowCalendarReschedule = true,
                blockedOverlappingMuscles = blockedForCond,
                safetyRestrictions = safetyRestrictions,
                evaluatedAtUtc = now
            )
        } else emptyList()

        val allRawSessions = nonCondSessions + condSessions
        val effectiveQualityDays = runCandidatePlan?.takeIf { cand -> cand.sessions.any { it.quality } }
            ?.request?.qualityDays.orEmpty().ifEmpty { request.qualityDays }
        val placed = placeSessions(request, allRawSessions, externalActive, effectiveQualityDays)
        val shaped = runCandidateVolume?.let { volume -> reshapeCombinedRuns(placed, volume, request) } ?: placed
        val annotated = annotateHybridNotes(shaped, request.goal)

        validateCalendar(request, annotated)
        if (annotated.any { cand -> externalActive.any { (policy, s) -> PlanScheduleValidation.acrossPlansConflict(cand, s, ENGINE18_HYBRID_POLICY, policy) } }) {
            throw PlanConstraint("The preview does not leave enough recovery around active plans.")
        }
        val expectedCount = request.weeks * active.sumOf { request.disciplineSessionCount(it) }
        if (annotated.size != expectedCount || annotated.map { it.id }.distinct().size != expectedCount) {
            throw PlanConstraint("ENGINE18_HYBRID_PLAN_INCOMPLETE: A hybrid slot is missing or duplicated.")
        }

        val stackedDays = annotated.groupBy { it.date }.count { it.value.size > 1 }
        val runQualityMeta = runCandidatePlan?.takeIf { cand -> cand.sessions.any { it.quality } }
        val secondaryEmphasis = Engine18HybridGoalOptimizer.evaluateSecondaryEmphasis(
            primaryShortfall = 0.0,
            remainingWeeklyMinutes = request.dailyMinutes * request.days.size,
            secondaryDevelopmentMinMinutes = 60
        )

        return PlanSnapshot(
            id = id,
            owner = owner,
            engineVersion = ENGINE18_VERSION,
            policyId = ENGINE18_HYBRID_POLICY,
            createdAt = now,
            request = if (runQualityMeta != null) {
                request.copy(
                    qualityDays = runQualityMeta.request.qualityDays,
                    qualityDistanceClaims = runQualityMeta.request.qualityDistanceClaims
                )
            } else request,
            sessions = annotated,
            explanations = buildList {
                add("Engine 1.8 Hybrid Intelligence plan uniting Running (1.5), Strength (1.6), Calisthenics (1.7), and 8-Family Conditioning (1.8).")
                add("Secondary focus emphasis: ${secondaryEmphasis.name.lowercase()}; unified local muscle ledgers and POL-036 bidirectional spacing active.")
                if (blockedForCond.isNotEmpty()) {
                    add("Conditioning avoided heavy overlap on high-load muscles: ${blockedForCond.sorted().joinToString(", ")}.")
                }
                if (stackedDays > 0) {
                    add("Adapted $stackedDays stacked day(s) using High/Low separation (>= 6 h AM/PM gap with primary goal first).")
                }
                add("This is a draft. Review all workouts and schedules before explicit approval.")
            },
            name = "Engine 1.8 Hybrid Plan",
            experimentalQualityPolicy = runQualityMeta?.experimentalQualityPolicy,
            experimentalQualityExposureSeconds = runQualityMeta?.experimentalQualityExposureSeconds
        )
    }

    fun validateSaved(plan: PlanSnapshot) {
        if (plan.engineVersion != ENGINE18_VERSION || plan.policyId != ENGINE18_HYBRID_POLICY) {
            throw PlanConstraint("The saved Engine 1.8 hybrid plan has an unsupported engine identity.")
        }
        val request = plan.request
        val active = disciplines.filter { request.disciplineSessionCount(it) > 0 }
        validateWeeklySlots(request, active)
        val expectedCount = request.weeks * active.sumOf { request.disciplineSessionCount(it) }
        if (active.size < 2 || plan.sessions.size != expectedCount || plan.sessions.any { session ->
                val match = Regex(".*-w([1-9][0-9]*)-(run|str|cal|cond)(0|[1-9][0-9]*)$").matchEntire(session.id)
                val week = match?.groupValues?.get(1)?.toIntOrNull()
                val discipline = match?.groupValues?.get(2)
                val slot = match?.groupValues?.get(3)?.toIntOrNull()
                week == null || week !in 1..request.weeks || discipline == null || discipline !in active || slot == null ||
                    slot >= request.disciplineSessionCount(discipline) || session.discipline != discipline ||
                    session.date !in request.start.plusWeeks((week - 1).toLong())..request.start.plusWeeks(week.toLong()).minusDays(1) ||
                    session.date.dayOfWeek.value !in request.days || session.distanceKm?.let { !it.isFinite() || it <= 0.0 } == true
            } || plan.sessions.map { it.id }.distinct().size != expectedCount) {
            throw PlanConstraint("The saved Engine 1.8 hybrid plan no longer matches its discipline and calendar contract.")
        }

        val scheduled = plan.sessions.sortedWith(compareBy<PlannedSession> { it.date }.thenBy { it.id })
        for (week in 1..request.weeks) for (discipline in active) {
            val rows = scheduled.filter { s ->
                val m = Regex(".*-w([1-9][0-9]*)-(run|str|cal|cond)(0|[1-9][0-9]*)$").matchEntire(s.id)
                m?.groupValues?.get(1)?.toIntOrNull() == week && m.groupValues[2] == discipline
            }
            val assigned = request.weeklySlots[discipline]
            if (assigned != null && rows.any { assigned.getOrNull(slotIndex(it)) != it.date.dayOfWeek.value }) {
                throw PlanConstraint("The saved Engine 1.8 hybrid plan moved a session off its fixed weekday.")
            }
            if (discipline == "run" && rows.any { it.baselineSession().kind == "long" && it.date.dayOfWeek.value != request.longDay }) {
                throw PlanConstraint("The saved Engine 1.8 hybrid plan moved the long run off its long-run weekday.")
            }
        }
        validateCalendar(request, scheduled)
        Engine15SessionLocks.validateOutput(request, scheduled)

        val runDatesById = scheduled.filter { it.discipline == "run" }.associate { it.id to it.date }
        val otherSessions = scheduled.filter { it.discipline != "run" }
        val externalHardDates = otherSessions.filter { isHard(it) }.map { it.date }.toSet()
        val maxMinutesById = if (request.timeBudgetEnabled) {
            scheduled.filter { it.discipline == "run" }.mapNotNull { run ->
                val otherMin = otherSessions.filter { it.date == run.date }.sumOf { it.effectiveMinutes }
                if (otherMin > 0) run.id to (request.dailyMinutes - otherMin).coerceAtLeast(1) else null
            }.toMap()
        } else emptyMap()

        val runMajorStepWeeks = computeRunningHoldWeeks(scheduled.filter { it.discipline == "run" }, request)
        active.forEach { discipline ->
            val solo = soloRequest(request, discipline)
            val rows = scheduled.filter { it.discipline == discipline }
            when (discipline) {
                "run" -> Engine15IndependentPlanGenerator.validateSaved(
                    plan.copy(
                        engineVersion = ENGINE15_VERSION,
                        policyId = ENGINE15_INDEPENDENT_RUNNING_POLICY,
                        request = solo.copy(qualityCount = if (rows.any { it.baselineSession().quality }) solo.qualityCount else 0),
                        sessions = rows
                    ),
                    combinedRunDatesById = runDatesById,
                    combinedExternalHardDates = externalHardDates,
                    combinedMaxMinutesById = maxMinutesById
                )
                "str" -> if (rows.any { Engine15DisciplineProgression.isStructured(it) } || strengthTrack(solo) != "str") {
                    Engine15StrengthWorkoutComposer.validateSaved(
                        plan.copy(engineVersion = ENGINE15_VERSION, policyId = ENGINE15_INDEPENDENT_STRENGTH_POLICY, request = solo, sessions = rows),
                        allowCalendarReschedule = true
                    )
                } else {
                    Engine16StrengthProgramBuilder.validateSaved(
                        plan.copy(engineVersion = ENGINE16_VERSION, policyId = ENGINE16_STRENGTH_POLICY, request = solo, sessions = rows),
                        allowCalendarReschedule = true,
                        runningMajorStepWeeks = runMajorStepWeeks
                    )
                }
                "cal" -> Engine17CalisthenicsProgramBuilder.validateSaved(
                    plan.copy(engineVersion = ENGINE17_VERSION, policyId = ENGINE17_CALISTHENICS_POLICY, request = solo, sessions = rows),
                    allowCalendarReschedule = true
                )
                "cond" -> Engine18ConditioningWorkoutConstructor.validateHybridConditioningRows(solo, rows)
            }
        }
    }

    private fun placeSessions(
        request: PlanRequest,
        sessions: List<PlannedSession>,
        external: List<Pair<String, PlannedSession>>,
        qualityDays: Set<Int> = emptySet()
    ): List<PlannedSession> {
        val placed = mutableListOf<PlannedSession>()
        val active = disciplines.filter { request.disciplineSessionCount(it) > 0 }
        validateWeeklySlots(request, active)

        for (weekIndex in 0 until request.weeks) {
            val first = request.start.plusWeeks(weekIndex.toLong())
            val last = first.plusDays(6)
            val weekSessions = sessions.filter { it.date in first..last }.sortedWith(
                compareBy<PlannedSession> { request.lockedSessionDays[it.id] == null }
                    .thenBy { if (it.kind == "long") 0 else if (isHard(it)) 1 else 2 }
                    .thenBy { it.id }
            )

            fun tryTier(tier: Int): List<PlannedSession>? {
                val weekPlaced = mutableListOf<PlannedSession>()
                var attempts = 0
                fun assign(index: Int): Boolean {
                    if (index == weekSessions.size) return true
                    if (++attempts > 60_000) return false
                    val template = weekSessions[index]
                    val locked = request.lockedSessionDays[template.id]?.let(LocalDate::parse)
                    val fixedDay = request.weeklySlots[template.discipline]?.getOrNull(slotIndex(template))
                    val eligible = (0L..6L).map(first::plusDays).filter { date ->
                        date.dayOfWeek.value in request.days &&
                            (locked == null || locked == date) &&
                            (fixedDay == null || fixedDay == date.dayOfWeek.value) &&
                            (template.kind != "long" || date.dayOfWeek.value == request.longDay) &&
                            (template.discipline != "run" || !template.quality || qualityDays.isEmpty() || date.dayOfWeek.value in qualityDays)
                    }.sortedWith(
                        compareBy<LocalDate> { date ->
                            val sameDay = (placed + weekPlaced).filter { it.date == date }
                            sameDayPenalty(template.copy(date = date), sameDay)
                        }
                            .thenBy { date -> (placed + weekPlaced).count { it.date == date } }
                            .thenBy { date ->
                                if ((placed + weekPlaced).any { it.date != date && Engine15IndependentPlanGenerator.conflict(template.copy(date = date), it) }) 1 else 0
                            }
                            .thenBy { it }
                    )

                    for (date in eligible) {
                        val candidate = template.copy(date = date)
                        if (external.any { (policy, s) -> PlanScheduleValidation.acrossPlansConflict(candidate, s, ENGINE18_HYBRID_POLICY, policy) }) continue
                        val sameDay = (placed + weekPlaced).filter { it.date == date }
                        if (sameDay.any { it.discipline == candidate.discipline }) continue
                        if ((placed + weekPlaced).any { Engine15DisciplineProgression.spacingConflict(candidate, it) }) continue
                        when (tier) {
                            1 -> {
                                if (sameDay.isNotEmpty()) continue
                                if (request.timeBudgetEnabled && candidate.effectiveMinutes > request.dailyMinutes) continue
                                if ((placed + weekPlaced).any { it.date != date && Engine15IndependentPlanGenerator.conflict(candidate, it) }) continue
                            }
                            2 -> {
                                if (sameDay.size >= 2) continue
                                if (sameDay.any { !isComplementaryPair(candidate, it) }) continue
                                if (request.timeBudgetEnabled && sameDay.sumOf { it.effectiveMinutes } + candidate.effectiveMinutes > request.dailyMinutes) continue
                                if ((placed + weekPlaced).any { it.date != date && Engine15IndependentPlanGenerator.conflict(candidate, it) }) continue
                            }
                            else -> {
                                if (sameDay.size >= 2) continue
                                if (request.timeBudgetEnabled && sameDay.sumOf { it.effectiveMinutes } + candidate.effectiveMinutes > request.dailyMinutes) continue
                            }
                        }
                        weekPlaced += candidate
                        if (assign(index + 1)) return true
                        weekPlaced.removeAt(weekPlaced.lastIndex)
                    }
                    return false
                }
                return if (assign(0)) weekPlaced else null
            }

            placed += tryTier(1) ?: tryTier(2) ?: tryTier(3) ?: throw PlanConstraint(
                "ENGINE18_HYBRID_SCHEDULE_CONFLICT: Week ${weekIndex + 1} cannot fit these workouts safely. Add available days or relax fixed slots."
            )
        }
        return placed.sortedWith(compareBy<PlannedSession> { it.date }.thenBy { it.id })
    }

    private fun reshapeCombinedRuns(
        placedSessions: List<PlannedSession>,
        volume: Engine15IndependentVolumePlanner.Result,
        request: PlanRequest
    ): List<PlannedSession> {
        val runSessions = placedSessions.filter { it.discipline == "run" }
        if (runSessions.isEmpty()) return placedSessions
        val otherSessions = placedSessions.filter { it.discipline != "run" }
        val externalHardDates = otherSessions.filter { isHard(it) }.map { it.date }.toSet()
        val qualityIds = runSessions.filter { it.quality }.map { it.id }.toSet()
        val maxMinutesBySessionId = if (request.timeBudgetEnabled) {
            runSessions.mapNotNull { run ->
                val otherMin = otherSessions.filter { it.date == run.date }.sumOf { it.effectiveMinutes }
                if (otherMin > 0) run.id to (request.dailyMinutes - otherMin).coerceAtLeast(1) else null
            }.toMap()
        } else emptyMap()
        val soloRun = soloRequest(request, "run")
        val shapedRuns = Engine15DailyLoadShaper.shape(
            sessions = runSessions,
            volume = volume,
            request = soloRun,
            qualitySessionIds = qualityIds,
            pinnedSessionIds = qualityIds,
            externalHardDates = externalHardDates,
            maxMinutesBySessionId = maxMinutesBySessionId
        )
        val enrichedRuns = if (runSessions.any { it.runningPrescription?.archetypeId != null }) {
            Engine15IndependentPlanGenerator.enrichNonQualitySessions(shapedRuns, soloRun, volume)
        } else shapedRuns
        return (enrichedRuns + otherSessions).sortedWith(compareBy<PlannedSession> { it.date }.thenBy { it.id })
    }

    private fun computeRunningHoldWeeks(runSessions: List<PlannedSession>, request: PlanRequest): Set<Int> {
        if (runSessions.isEmpty()) return emptySet()
        val weekKm = (1..request.weeks).associateWith { week ->
            val wStart = request.start.plusWeeks((week - 1).toLong())
            val wEnd = wStart.plusDays(6)
            runSessions.filter { it.date in wStart..wEnd }.sumOf { it.baselineSession().distanceKm ?: 0.0 }
        }
        return buildSet {
            for (week in 2..request.weeks) {
                val prev = weekKm[week - 1] ?: 0.0
                val cur = weekKm[week] ?: 0.0
                if (prev > 0.0 && cur >= prev * 1.08) add(week)
                if (week == request.weeks && request.weeks >= 4 && prev > 0.0 && cur <= prev * 0.90) add(week)
            }
        }
    }

    private fun annotateHybridNotes(sessions: List<PlannedSession>, primaryGoal: String): List<PlannedSession> {
        val byDate = sessions.groupBy { it.date }
        val hasRunInPlan = sessions.any { it.discipline == "run" }
        val hasStrengthInPlan = sessions.any { it.discipline == "str" }
        val nonRunPrimaryLabel = when {
            hasStrengthInPlan -> "strength"
            sessions.any { it.discipline == "cal" } -> "calisthenics"
            else -> "conditioning"
        }
        return sessions.map { session ->
            val peers = byDate[session.date].orEmpty().filter { it.id != session.id }
            val tightSpacing = sessions.any { it.id != session.id && it.date != session.date && Engine15IndependentPlanGenerator.conflict(session, it) }
            val note = buildList {
                if (peers.isNotEmpty()) {
                    val hasRunOnDay = session.discipline == "run" || peers.any { it.discipline == "run" }
                    val leadLabel = if (hasRunOnDay || hasRunInPlan && (session.discipline == "run" || peers.any { it.discipline == "run" })) {
                        "running"
                    } else {
                        nonRunPrimaryLabel
                    }
                    add("Stacked day (${peers.joinToString { it.discipline.uppercase() }}): separate by 6 hours AM/PM and prioritize $leadLabel first ($primaryGoal).")
                }
                if (tightSpacing) {
                    add("Back-to-back load (< 48 h buffer): keep non-primary effort at 2 to 3 RIR and monitor recovery.")
                }
            }.joinToString(" ")
            if (note.isNotBlank() && !session.reason.contains(note)) {
                session.copy(reason = (session.reason.trimEnd() + " " + note).trim())
            } else session
        }.sortedWith(compareBy<PlannedSession> { it.date }.thenBy { it.id })
    }

    private fun validateCalendar(request: PlanRequest, sessions: List<PlannedSession>) {
        for (i in sessions.indices) for (k in i + 1 until sessions.size) {
            val a = sessions[i]; val b = sessions[k]
            if (Engine15DisciplineProgression.spacingConflict(a, b)) {
                throw PlanConstraint("Leave a full day between hard lower-body, speed and high intensity sessions.")
            }
            if (a.date == b.date && a.discipline == b.discipline) {
                throw PlanConstraint("ENGINE18_HYBRID_SCHEDULE_CONFLICT: Two sessions of the same discipline cannot share a date.")
            }
        }
        val byDate = sessions.groupBy { it.date }
        if (byDate.values.any { it.size > 2 }) {
            throw PlanConstraint("ENGINE18_HYBRID_SCHEDULE_CONFLICT: At most two sessions can share a date.")
        }
        if (request.timeBudgetEnabled && byDate.values.any { day -> day.sumOf { it.effectiveMinutes } > request.dailyMinutes }) {
            throw PlanConstraint("ENGINE18_HYBRID_TIME_BUDGET_CONFLICT: Combined daily duration exceeds your daily time budget.")
        }
    }

    private fun sameDayPenalty(candidate: PlannedSession, sameDay: List<PlannedSession>): Int {
        if (sameDay.isEmpty()) return 0
        val candHard = isHard(candidate)
        return sameDay.maxOf { peer ->
            val peerHard = isHard(peer)
            when {
                isComplementaryPair(candidate, peer) -> 1
                candHard && peerHard -> 3
                else -> 2
            }
        }
    }

    private fun isComplementaryPair(a: PlannedSession, b: PlannedSession): Boolean {
        if (a.discipline == b.discipline) return false
        val hasEasyRun = listOf(a, b).any { it.discipline == "run" && it.kind == "easy" && !it.quality }
        val hasNonLowerOther = listOf(a, b).any { it.discipline != "run" && !it.lower }
        return hasEasyRun && hasNonLowerOther
    }

    private fun isHard(session: PlannedSession): Boolean = if (Engine15DisciplineProgression.isStructured(session)) {
        session.quality || session.lower
    } else {
        session.quality || session.kind == "long" ||
            (session.discipline == "cal" && (session.lower || session.minutes >= 35)) ||
            (session.discipline in setOf("str", "cond") && session.lower)
    }

    private fun slotIndex(session: PlannedSession): Int {
        val suffix = Regex(".*-(?:run|str|cal|cond)(0|[1-9][0-9]*)$").matchEntire(session.id)
            ?: throw PlanConstraint("ENGINE18_HYBRID_SLOT_ASSIGNMENT_INVALID: Session ID does not identify a discipline slot.")
        return suffix.groupValues[1].toInt()
    }

    private fun validateWeeklySlots(request: PlanRequest, active: List<String>) {
        active.forEach { discipline ->
            val assigned = request.weeklySlots[discipline] ?: return@forEach
            val count = request.disciplineSessionCount(discipline)
            if (assigned.size != count || assigned.any { it !in request.days } || assigned.distinct().size != count ||
                (discipline == "run" && assigned.firstOrNull() != request.longDay)
            ) {
                throw PlanConstraint("ENGINE18_HYBRID_SLOT_ASSIGNMENT_INVALID: Fixed slots must match session count and distinct weekdays.")
            }
        }
    }
}
