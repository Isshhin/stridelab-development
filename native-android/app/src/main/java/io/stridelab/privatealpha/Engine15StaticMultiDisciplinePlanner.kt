package io.stridelab.privatealpha

import java.time.LocalDate

/** Engine 1.5 static cross-discipline coordination for composed, already constructed sessions. */
internal object Engine15StaticMultiDisciplinePlanner {
    private val disciplines = listOf("run", "str", "cal", "cond")

    fun validateRequest(request: PlanRequest) {
        val active = disciplines.filter { request.disciplineSessionCount(it) > 0 }
        if (request.policyChoice != "engine15" || active.size < 2 || request.weeks !in 1..16 ||
            request.days.isEmpty() || request.days.any { it !in 1..7 } ||
            request.disciplineGoals.keys.any { it !in active } ||
            request.weeklySlots.keys.any { it !in active } ||
            request.timeBudgetEnabled && request.dailyMinutes !in 15..240)
            throw PlanConstraint("ENGINE15_COMBINED_REQUEST_UNSUPPORTED: Choose at least two supported disciplines, valid available weekdays and a valid daily time limit.")
        active.forEach { discipline ->
            val solo = soloRequest(request, discipline)
            when (discipline) {
                "run" -> if (Engine15TimeOnlyRunWalkBuilder.isEligible(solo)) {
                    Engine15TimeOnlyRunWalkBuilder.compose(solo, "combined-validation", "combined-validation", java.time.Instant.EPOCH)
                } else {
                    when (val outcome = Engine15IndependentPlanGenerator.previewAutoReconciled(solo, "combined-validation", "combined-validation", java.time.Instant.EPOCH)) {
                        is Engine15IndependentPlanDecision.Deferred -> throw PlanConstraint("${outcome.reasonCode}: ${outcome.explanation}")
                        is Engine15IndependentPlanDecision.Candidate -> Unit
                    }
                }
                "str" -> Engine15StrengthWorkoutComposer.validateRequest(solo)
                "cal" -> Engine15CalisthenicsWorkoutComposer.validateRequest(solo)
                "cond" -> Engine15ConditioningWorkoutComposer.validateRequest(solo)
            }
        }
        validateWeeklySlots(request, active)
    }

    fun compose(request: PlanRequest, owner: String, id: String, now: java.time.Instant,
                existingPlans: List<PlanSnapshot>, library: ExerciseLibraryV2,
                activities: List<ActivityRecord> = emptyList(),
                detections: List<Engine15DetectedWorkout> = emptyList()): PlanSnapshot {
        validateRequest(request)
        val start = request.start.minusDays(1)
        val end = request.start.plusWeeks(request.weeks.toLong())
        val activeSessions = existingPlans.asSequence().filter {
            it.owner == owner && it.status == "approved"
        }.flatMap { plan -> plan.sessions.asSequence()
            .filter { it.status in setOf("planned", "in_progress") && it.date in start..end }
            .map { plan.policyId to it }
        }.toList()
        RecordPolicy.identifier(owner)
        RecordPolicy.identifier(id)
        var runCandidatePlan: PlanSnapshot? = null
        var runCandidateVolume: Engine15IndependentVolumePlanner.Result? = null
        val sessions = disciplines.filter { request.disciplineSessionCount(it) > 0 }.flatMap { discipline ->
            val solo = soloRequest(request, discipline)
            when (discipline) {
                "run" -> if (Engine15TimeOnlyRunWalkBuilder.isEligible(solo)) {
                    Engine15TimeOnlyRunWalkBuilder.compose(solo, owner, id, now).sessions
                } else {
                    (Engine15IndependentPlanGenerator.previewAutoReconciled(
                        solo, owner, id, now, activities = activities, detections = detections
                    ) as? Engine15IndependentPlanDecision.Candidate)
                        ?.also {
                            runCandidatePlan = it.plan
                            runCandidateVolume = it.volume
                        }?.plan?.sessions
                        ?: throw PlanConstraint("RUNNING_COMPONENT_UNAVAILABLE: No independent running candidate was produced.")
                }
                "str" -> Engine15StrengthWorkoutComposer.compose(solo, id, library, allowCalendarReschedule = true)
                "cal" -> Engine15CalisthenicsWorkoutComposer.compose(solo, id, library, allowCalendarReschedule = true)
                else -> Engine15ConditioningWorkoutComposer.compose(solo, id, library, allowCalendarReschedule = true)
            }
        }
        val placed = place(request, sessions, activeSessions)
        val shaped = runCandidateVolume?.let { volume ->
            reshapeCombinedRuns(placed, volume, request)
        } ?: placed
        val scheduled = annotateCombinedNotes(shaped)
        validateCalendar(request, scheduled)
        if (scheduled.any { candidate -> activeSessions.any { (policy, session) ->
                PlanScheduleValidation.acrossPlansConflict(candidate, session, ENGINE15_INDEPENDENT_COMBINED_POLICY, policy)
            } })
            throw PlanConstraint("The preview does not leave enough recovery around active plans.")
        if (scheduled.size != request.weeks * disciplines.sumOf { request.disciplineSessionCount(it) } ||
            scheduled.map { it.id }.distinct().size != scheduled.size)
            throw PlanConstraint("ENGINE15_COMBINED_PLAN_INCOMPLETE: A combined discipline slot is missing or duplicated.")
        val stackedDays = scheduled.groupBy { it.date }.count { it.value.size > 1 }
        val runQualityMeta = runCandidatePlan?.takeIf { candidate -> candidate.sessions.any { it.quality } }
        return PlanSnapshot(
            id = id, owner = owner, engineVersion = ENGINE15_VERSION,
            policyId = ENGINE15_INDEPENDENT_COMBINED_POLICY, createdAt = now,
            request = if (runQualityMeta != null) {
                request.copy(
                    qualityDays = runQualityMeta.request.qualityDays,
                    qualityDistanceClaims = runQualityMeta.request.qualityDistanceClaims
                )
            } else request,
            sessions = scheduled,
            explanations = buildList {
                add("Independent Engine 1.5 static multi-discipline plan. Every requested session was constructed by its Engine 1.5 discipline composer; no legacy generator or coaching validator was used.")
                add("The calendar preserves selected long-run days, explicit weekday assignments and session locks, preferring optimal recovery spacing and adapting automatically when disciplines share a day.")
                if (stackedDays > 0) {
                    add("Adapted $stackedDays stacked multi-discipline day(s) to honor your chosen schedule. Separate paired sessions by 4–6 hours (AM/PM) where possible and prioritize running first.")
                }
                add("This is a static review draft. It does not adapt training load from completed or missed sessions. Review all sessions before explicit approval.")
            },
            name = "Engine 1.5 independent combined plan",
            experimentalQualityPolicy = runQualityMeta?.experimentalQualityPolicy,
            experimentalQualityExposureSeconds = runQualityMeta?.experimentalQualityExposureSeconds
        )
    }

    fun validateSaved(plan: PlanSnapshot) {
        val request = plan.request
        val active = disciplines.filter { request.disciplineSessionCount(it) > 0 }
        validateWeeklySlots(request, active)
        val expected = request.weeks * active.sumOf { request.disciplineSessionCount(it) }
        if (plan.engineVersion != ENGINE15_VERSION || plan.policyId != ENGINE15_INDEPENDENT_COMBINED_POLICY ||
            active.size < 2 || plan.sessions.size != expected || plan.sessions.any { session ->
                val match = Regex(".*-w([1-9][0-9]*)-(run|str|cal|cond)(0|[1-9][0-9]*)$").matchEntire(session.id)
                val week = match?.groupValues?.get(1)?.toIntOrNull()
                val discipline = match?.groupValues?.get(2)
                val slot = match?.groupValues?.get(3)?.toIntOrNull()
                week == null || week !in 1..request.weeks || discipline == null || discipline !in active || slot == null ||
                    slot >= request.disciplineSessionCount(discipline.orEmpty()) || session.discipline != discipline ||
                    session.date !in request.start.plusWeeks((week - 1).toLong())..request.start.plusWeeks(week.toLong()).minusDays(1) ||
                    session.date.dayOfWeek.value !in request.days || session.distanceKm?.let { !it.isFinite() || it <= 0.0 } == true
            } || plan.sessions.map { it.id }.distinct().size != expected)
            throw PlanConstraint("The saved independent combined plan no longer matches its discipline and calendar contract.")
        for (week in 1..request.weeks) for (discipline in active) {
            val sessions = plan.sessions.filter { session ->
                val match = Regex(".*-w([1-9][0-9]*)-(run|str|cal|cond)(0|[1-9][0-9]*)$").matchEntire(session.id)
                match?.groupValues?.get(1)?.toIntOrNull() == week && match.groupValues[2] == discipline
            }
            if (sessions.size != request.disciplineSessionCount(discipline) ||
                sessions.mapNotNull { Regex(".*-(?:run|str|cal|cond)(0|[1-9][0-9]*)$").matchEntire(it.id)?.groupValues?.get(1)?.toIntOrNull() }.distinct().size != sessions.size)
                throw PlanConstraint("The saved independent combined plan is missing a discipline slot.")
            val assignedDays = request.weeklySlots[discipline]
            if (assignedDays != null && sessions.any { session -> assignedDays.getOrNull(slotIndex(session)) != session.date.dayOfWeek.value })
                throw PlanConstraint("The saved independent combined plan no longer matches its fixed weekday assignments.")
            if (discipline == "run" && sessions.any { it.baselineSession().kind == "long" && it.date.dayOfWeek.value != request.longDay })
                throw PlanConstraint("The saved independent combined plan moved the long run away from its selected weekday.")
        }
        val scheduled = plan.sessions.sortedWith(compareBy<PlannedSession> { it.date }.thenBy { it.id })
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
        active.forEach { discipline ->
            val candidate = plan.copy(
                policyId = when (discipline) {
                    "run" -> ENGINE15_INDEPENDENT_RUNNING_POLICY
                    "str" -> ENGINE15_INDEPENDENT_STRENGTH_POLICY
                    "cal" -> ENGINE15_INDEPENDENT_CALISTHENICS_POLICY
                    else -> ENGINE15_INDEPENDENT_CONDITIONING_POLICY
                },
                request = soloRequest(request, discipline),
                sessions = scheduled.filter { it.discipline == discipline }
            )
            when (discipline) {
                "run" -> Engine15IndependentPlanGenerator.validateSaved(
                    candidate.copy(
                        request = candidate.request.copy(
                            qualityCount = if (candidate.sessions.any { it.baselineSession().quality }) candidate.request.qualityCount else 0
                        )
                    ),
                    combinedRunDatesById = runDatesById,
                    combinedExternalHardDates = externalHardDates,
                    combinedMaxMinutesById = maxMinutesById
                )
                "str" -> Engine15StrengthWorkoutComposer.validateSaved(candidate, allowCalendarReschedule = true)
                "cal" -> Engine15CalisthenicsWorkoutComposer.validateSaved(candidate, allowCalendarReschedule = true)
                "cond" -> Engine15ConditioningWorkoutComposer.validateSaved(candidate, allowCalendarReschedule = true)
            }
        }
    }

    fun conflict(a: PlannedSession, b: PlannedSession, request: PlanRequest): Boolean =
        a.date == b.date && a.discipline == b.discipline

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

    private fun place(request: PlanRequest, sessions: List<PlannedSession>,
                      external: List<Pair<String, PlannedSession>>): List<PlannedSession> {
        validateWeeklySlots(request, disciplines.filter { request.disciplineSessionCount(it) > 0 })
        val placed = mutableListOf<PlannedSession>()
        repeat(request.weeks) { weekIndex ->
            val first = request.start.plusWeeks(weekIndex.toLong())
            val last = first.plusDays(6)
            val weekSessions = sessions.filter { it.date in first..last }.sortedWith(
                compareBy<PlannedSession> { request.lockedSessionDays[it.id] == null }
                    .thenBy { if (it.kind == "long") 0 else if (isHard(it)) 1 else 2 }
                    .thenBy { it.id }
            )

            /**
             * User-first 3-tier adaptive scheduler:
             * - Tier 1: Separate days + >=2-day hard-session spacing + daily time budget.
             * - Tier 2: Smart complementary double days (distinct disciplines, up to 2/day) + hard spacing.
             * - Tier 3: Any distinct-discipline double day (<= 2/day, respecting dailyMinutes) minimizing hard collisions.
             */
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
                        date.dayOfWeek.value in request.days && (locked == null || locked == date) &&
                            (fixedDay == null || fixedDay == date.dayOfWeek.value) &&
                            (template.kind != "long" || date.dayOfWeek.value == request.longDay)
                    }.sortedWith(compareBy<LocalDate> { date ->
                        val sameDay = (placed + weekPlaced).filter { it.date == date }
                        sameDayPenalty(template.copy(date = date), sameDay)
                    }.thenBy { date ->
                        (placed + weekPlaced).count { it.date == date }
                    }.thenBy { date ->
                        if ((placed + weekPlaced).any { it.date != date && Engine15IndependentPlanGenerator.conflict(template.copy(date = date), it) }) 1 else 0
                    }.thenBy { it })
                    for (date in eligible) {
                        val candidate = template.copy(date = date)
                        if (external.any { (policy, session) ->
                            PlanScheduleValidation.acrossPlansConflict(candidate, session, ENGINE15_INDEPENDENT_COMBINED_POLICY, policy)
                        }) continue
                        val sameDay = (placed + weekPlaced).filter { it.date == date }
                        // Never place two sessions of the exact same discipline on the same day.
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

            val weekResult = tryTier(1) ?: tryTier(2) ?: tryTier(3) ?: throw PlanConstraint(
                "ENGINE15_COMBINED_SCHEDULE_CONFLICT: Week ${weekIndex + 1} cannot fit these workouts safely. Add available days, switch fixed workouts to automatic placement, or build separate plans for each discipline."
            )
            placed += weekResult
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
        } else {
            shapedRuns
        }
        return (enrichedRuns + otherSessions).sortedWith(compareBy<PlannedSession> { it.date }.thenBy { it.id })
    }

    private fun annotateCombinedNotes(sessions: List<PlannedSession>): List<PlannedSession> {
        val byDate = sessions.groupBy { it.date }
        return sessions.map { session ->
            val peers = byDate[session.date].orEmpty().filter { it.id != session.id }
            val tightSpacing = sessions.any {
                it.id != session.id && it.date != session.date && Engine15IndependentPlanGenerator.conflict(session, it)
            }
            val note = buildList {
                if (peers.isNotEmpty()) add("Stacked day (${peers.joinToString { it.discipline.uppercase() }}): separate AM/PM (4–6h gap) and prioritize running first.")
                if (tightSpacing) add("Back-to-back training load: keep effort controlled and prioritize sleep and hydration.")
            }.joinToString(" ")
            if (note.isNotBlank() && !session.reason.contains(note)) {
                session.copy(reason = (session.reason.trimEnd() + " " + note).trim())
            } else session
        }.sortedWith(compareBy<PlannedSession> { it.date }.thenBy { it.id })
    }

    private fun validateCalendar(request: PlanRequest, sessions: List<PlannedSession>) {
        // Enforce genuine data/integrity invariants and PlanScheduleValidation limits.
        for (index in sessions.indices) for (other in index + 1 until sessions.size) {
            val a = sessions[index]
            val b = sessions[other]
            if (Engine15DisciplineProgression.spacingConflict(a, b))
                throw PlanConstraint("Leave a full day between hard lower-body, speed and high intensity sessions.")
            if (a.date == b.date && a.discipline == b.discipline) {
                throw PlanConstraint("ENGINE15_COMBINED_SCHEDULE_CONFLICT: Two sessions of the same discipline cannot share a date.")
            }
        }
        val byDate = sessions.groupBy { it.date }
        if (byDate.values.any { it.size > 2 }) {
            throw PlanConstraint("ENGINE15_COMBINED_SCHEDULE_CONFLICT: At most two sessions can share a date.")
        }
        if (request.timeBudgetEnabled && byDate.values.any { day -> day.sumOf { it.effectiveMinutes } > request.dailyMinutes }) {
            throw PlanConstraint("ENGINE15_COMBINED_TIME_BUDGET_CONFLICT: Combined daily duration exceeds the requested daily time budget.")
        }
    }

    private fun isComplementaryPair(a: PlannedSession, b: PlannedSession): Boolean {
        if (a.discipline == b.discipline) return false
        val hasEasyRun = listOf(a, b).any { it.discipline == "run" && it.kind == "easy" && !it.quality }
        val hasNonLowerOther = listOf(a, b).any { it.discipline != "run" && !it.lower }
        return hasEasyRun && hasNonLowerOther
    }

    private fun sameDayCompatible(a: PlannedSession, b: PlannedSession, request: PlanRequest): Boolean =
        a.discipline != b.discipline

    private fun isHard(session: PlannedSession) = if (Engine15DisciplineProgression.isStructured(session))
        session.quality || session.lower
    else session.quality || session.kind == "long" ||
        session.discipline == "cal" || session.discipline in setOf("str", "cond") && session.lower

    private fun slotIndex(session: PlannedSession): Int {
        val suffix = Regex(".*-(?:run|str|cal|cond)(0|[1-9][0-9]*)$").matchEntire(session.id)
            ?: throw PlanConstraint("ENGINE15_COMBINED_SLOT_ASSIGNMENT_INVALID: A session ID does not identify a discipline slot.")
        return suffix.groupValues[1].toInt()
    }

    private fun validateWeeklySlots(request: PlanRequest, active: List<String>) {
        active.forEach { discipline ->
            val assigned = request.weeklySlots[discipline] ?: return@forEach
            val count = request.disciplineSessionCount(discipline)
            if (assigned.size != count || assigned.any { it !in request.days } || assigned.distinct().size != count ||
                discipline == "run" && assigned.firstOrNull() != request.longDay)
                throw PlanConstraint("ENGINE15_COMBINED_SLOT_ASSIGNMENT_INVALID: Each fixed discipline slot needs a distinct available weekday and running slot one must match the long-run day.")
        }
    }

    private fun soloRequest(request: PlanRequest, discipline: String) = request.copy(
        goal = request.disciplineGoal(discipline),
        runDays = if (discipline == "run") request.runDays else 0,
        strengthDays = if (discipline == "str") request.strengthDays else 0,
        calDays = if (discipline == "cal") request.calDays else 0,
        conditioningDays = if (discipline == "cond") request.conditioningDays else 0,
        weeklySlots = request.weeklySlots.filterKeys { it == discipline },
        lockedSessionDays = request.lockedSessionDays.filterKeys { Regex(".*-$discipline(0|[1-9][0-9]*)$").matches(it) },
        allowDoubleDays = discipline != "run" && request.allowDoubleDays,
        disciplineGoals = emptyMap()
    )

}
