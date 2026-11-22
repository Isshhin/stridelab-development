package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import kotlin.math.ceil

/**
 * Progressive time-only Run/Walk onboarding builder for beginners with CONFIRMED_ZERO
 * recent running mileage. Builds duration-based walk/jog sessions without fabricating
 * synthetic weekly kilometres or paces.
 */
internal object Engine15TimeOnlyRunWalkBuilder {

    fun isEligible(request: PlanRequest): Boolean =
        request.policyChoice == "engine15" &&
            request.runDays in 1..6 &&
            request.strengthDays == 0 &&
            request.calDays == 0 &&
            request.conditioningDays == 0 &&
            request.recentMileageBasis == "CONFIRMED_ZERO" &&
            request.weeklyKm == 0.0 &&
            request.longestKm == 0.0 &&
            (request.targetWeeklyKm == null || (request.targetWeeklyKm.isFinite() && request.targetWeeklyKm > 0.0)) &&
            request.reportedPeakWeeklyKm == null &&
            request.userDefinedQualityWorkouts.isEmpty() &&
            !request.userDirectedPlanning &&
            !request.runningRecoveryHold

    private data class WeekTemplate(
        val warmupMin: Int,
        val reps: Int,
        val jogMin: Int,
        val walkMin: Int,
        val continuousJogMin: Int? = null,
        val cooldownMin: Int
    )

    private fun weekTemplate(weekIndex: Int): WeekTemplate = when (weekIndex) {
        0 -> WeekTemplate(5, 6, 1, 2, null, 5)
        1 -> WeekTemplate(5, 6, 2, 2, null, 5)
        2 -> WeekTemplate(5, 5, 3, 2, null, 5)
        3 -> WeekTemplate(5, 5, 2, 2, null, 5) // Recovery week 4
        4 -> WeekTemplate(5, 4, 5, 2, null, 5)
        5 -> WeekTemplate(5, 3, 8, 2, null, 5)
        6 -> WeekTemplate(5, 2, 12, 2, null, 5)
        7 -> WeekTemplate(5, 3, 6, 2, null, 5) // Recovery week 8
        else -> if (weekIndex % 4 == 3) {
            WeekTemplate(5, 1, 20, 0, 20, 5)
        } else {
            val continuous = 25 + minOf(10, (weekIndex - 8) * 2)
            WeekTemplate(5, 1, continuous, 0, continuous, 5)
        }
    }

    fun compose(
        request: PlanRequest,
        owner: String,
        id: String,
        now: Instant,
        blockedDates: Set<LocalDate> = emptySet()
    ): PlanSnapshot {
        require(isEligible(request)) { "Request is not eligible for TIME_ONLY run/walk generation." }
        if (request.weeks !in 1..16 || request.runDays !in 1..6) {
            throw PlanConstraint("TIME_ONLY run/walk plans support 1 to 6 running days across 1 to 16 weeks.")
        }
        if (request.days.isEmpty() || request.days.any { it !in 1..7 } || request.days.size < request.runDays ||
            ((request.runDays == 1 || request.runDays >= 3) && request.longDay !in request.days)
        ) {
            throw PlanConstraint("Available weekdays must fit every run/walk session and the selected long-run day.")
        }
        if (request.weeklySlots.keys.any { it != "run" }) {
            throw PlanConstraint("Mixed discipline slots are not supported in TIME_ONLY running plans.")
        }
        val assignedDays = request.weeklySlots["run"]
        if (assignedDays != null && (assignedDays.size != request.runDays ||
                assignedDays.any { it !in request.days } ||
                assignedDays.distinct().size != assignedDays.size ||
                (request.runDays != 2 && assignedDays.firstOrNull() != request.longDay))
        ) {
            throw PlanConstraint("Fixed running weekdays must fit your available days cleanly.")
        }
        Engine15SessionLocks.validateRequest(request)
        val lockPattern = Regex(".*-w([1-9][0-9]*)-run(0|[1-9][0-9]*)$")
        val locksBySlot = HashMap<Pair<Int, Int>, Pair<String, LocalDate>>()
        for ((sessionId, rawDate) in request.lockedSessionDays) {
            val match = lockPattern.matchEntire(sessionId)
                ?: throw PlanConstraint("A locked workout ID does not identify a valid week and slot.")
            val weekNum = match.groupValues[1].toInt()
            val runIdx = match.groupValues[2].toInt()
            if (weekNum !in 1..request.weeks || runIdx !in 0 until request.runDays) {
                throw PlanConstraint("A locked workout refers to a week or slot outside this plan.")
            }
            val parsedDate = LocalDate.parse(rawDate)
            if (parsedDate in blockedDates) {
                throw PlanConstraint("A locked run/walk date conflicts with an existing scheduled workout.")
            }
            locksBySlot[weekNum to runIdx] = sessionId to parsedDate
        }

        val usesLongRole = request.runDays == 1 || request.runDays >= 3
        val selectedDatesByWeek = ArrayList<List<Pair<Int, LocalDate>>>(request.weeks)
        for (weekIndex in 0 until request.weeks) {
            val weekNum = weekIndex + 1
            val weekStart = request.start.plusWeeks(weekIndex.toLong())
            val weekDates = (0L..6L).map { weekStart.plusDays(it) }
                .filter { it.dayOfWeek.value in request.days && it !in blockedDates }
            if (weekDates.size < request.runDays) {
                throw PlanConstraint("Week $weekNum does not have enough unblocked available days for ${request.runDays} run/walk sessions.")
            }
            val lockedThisWeek = (0 until request.runDays).mapNotNull { runIdx ->
                locksBySlot[weekNum to runIdx]?.let { (_, date) ->
                    if (date < weekStart || date >= weekStart.plusWeeks(1) || date !in weekDates) {
                        throw PlanConstraint("Locked date $date is outside Week $weekNum availability.")
                    }
                    runIdx to date
                }
            }.toMap()
            if (lockedThisWeek.values.distinct().size != lockedThisWeek.size) {
                throw PlanConstraint("Two locked run/walk sessions land on the same date.")
            }

            val assignedForWeek = HashMap<Int, LocalDate>(request.runDays)
            assignedForWeek.putAll(lockedThisWeek)

            if (assignedDays != null) {
                val mappedDates = assignedDays.mapIndexed { idx, iso ->
                    val d = weekDates.firstOrNull { it.dayOfWeek.value == iso }
                        ?: throw PlanConstraint("Assigned day $iso is blocked or unavailable in Week $weekNum.")
                    idx to d
                }
                for ((idx, d) in mappedDates) {
                    val locked = assignedForWeek[idx]
                    if (locked != null && locked != d) {
                        throw PlanConstraint("Locked workout conflicts with your fixed weekday assignment.")
                    }
                    assignedForWeek[idx] = d
                }
            } else {
                val usedDates = assignedForWeek.values.toMutableSet()
                if (usesLongRole && 0 !in assignedForWeek) {
                    val longDate = weekDates.firstOrNull { it.dayOfWeek.value == request.longDay && it !in usedDates }
                        ?: throw PlanConstraint("Selected long-run day is blocked in Week $weekNum.")
                    assignedForWeek[0] = longDate
                    usedDates += longDate
                }
                val remainingIdx = (0 until request.runDays).filter { it !in assignedForWeek }
                val remainingDates = weekDates.filter { it !in usedDates }
                val chosenDates = selectSpacedDates(remainingDates, remainingIdx.size)
                remainingIdx.zip(chosenDates).forEach { (idx, d) ->
                    assignedForWeek[idx] = d
                }
            }
            if (assignedForWeek.values.distinct().size != request.runDays) {
                throw PlanConstraint("Cannot schedule more than one run/walk session per date.")
            }
            selectedDatesByWeek += (0 until request.runDays).map { idx -> idx to assignedForWeek.getValue(idx) }
        }

        val longDates = selectedDatesByWeek.flatten()
            .filter { (runIdx, _) -> usesLongRole && runIdx == 0 }
            .map { it.second }
            .toSet()

        val sessions = ArrayList<PlannedSession>(request.weeks * request.runDays)
        for (weekIndex in 0 until request.weeks) {
            val weekNum = weekIndex + 1
            val isRecoveryWeek = weekIndex % 4 == 3
            val baseTemplate = weekTemplate(weekIndex)
            val weekPairs = selectedDatesByWeek[weekIndex].sortedBy { it.second }

            for ((runIdx, date) in weekPairs) {
                val isLong = usesLongRole && runIdx == 0
                val isRecoveryAdj = !isLong && date.minusDays(1) in longDates
                val slotTemplate = adjustTemplateForSlot(
                    base = baseTemplate,
                    isLong = isLong,
                    isRecovery = isRecoveryAdj,
                    isRecoveryWeek = isRecoveryWeek,
                    budgetMinutes = if (request.timeBudgetEnabled) request.minutes else null
                )
                val segments = buildSegments(slotTemplate, isRecoveryAdj)
                val totalMin = ceil(segments.sumOf { it.seconds ?: 0 } / 60.0).toInt().coerceAtLeast(10)
                val rxRole = when {
                    isLong -> "LONG"
                    isRecoveryAdj -> "RECOVERY"
                    else -> "EASY"
                }
                val title = when {
                    isLong -> "Long run/walk session"
                    isRecoveryAdj -> "Easy walk/jog recovery"
                    else -> "Run/walk foundation"
                }
                val kind = if (isLong) "long" else "easy"
                val reason = buildString {
                    append(if (isRecoveryWeek) "Recovery week ($weekNum). " else "Foundation week ($weekNum). ")
                    when {
                        isLong -> append("Time-based longer aerobic run/walk outing; keep jogging conversational and walk as scheduled.")
                        isRecoveryAdj -> append("Shortened walk/jog recovery after yesterday's longer session; focus on fresh legs, not distance.")
                        else -> append("Progressive time-based run/walk session building consistency from zero recent mileage.")
                    }
                }
                val sessionId = locksBySlot[weekNum to runIdx]?.first ?: "$id-w${weekNum}-run$runIdx"
                sessions += PlannedSession(
                    id = sessionId,
                    date = date,
                    discipline = "run",
                    title = title,
                    minutes = totalMin,
                    distanceKm = null,
                    kind = kind,
                    quality = false,
                    steps = segments.mapIndexed { idx, step ->
                        "${idx + 1}. ${formatDurationLabel(step.seconds!!)} ${step.purpose.lowercase().replace('_', ' ')} — ${step.effort}"
                    },
                    reason = reason,
                    runningPrescription = RunningPrescription(
                        role = rxRole,
                        family = null,
                        segments = emptyList(),
                        mixedSegments = segments,
                        estimatedDistanceKm = null,
                        reconciledDistanceMeters = null
                    )
                )
            }
        }

        val sortedSessions = sessions.sortedWith(compareBy<PlannedSession> { it.date }.thenBy { it.id })
        Engine15SessionLocks.validateOutput(request, sortedSessions)
        return PlanSnapshot(
            id = id,
            owner = owner,
            engineVersion = ENGINE15_VERSION,
            policyId = ENGINE15_INDEPENDENT_RUNNING_POLICY,
            createdAt = now,
            request = request,
            sessions = sortedSessions,
            explanations = buildList {
                add("Time-only beginner Run/Walk plan for CONFIRMED_ZERO baseline; zero synthetic distances were fabricated.")
                add("Durations progress conservatively across 4-week build/recovery cycles using conversational jog/walk intervals.")
                add("Every session supports on-demand - Easier / + Harder / Reset scaling while remaining strictly time-based.")
                if (request.qualityCount > 0) {
                    add("Quality sessions are adapted to conversational walk/jog intervals until a running foundation is established.")
                }
                if (request.targetWeeklyKm != null) {
                    add("Weekly distance target is deferred until walk/jog consistency is established.")
                }
            },
            name = "StrideLab Run/Walk Foundation"
        )
    }

    private fun selectSpacedDates(candidates: List<LocalDate>, count: Int): List<LocalDate> {
        if (count <= 0) return emptyList()
        if (candidates.size <= count) return candidates.take(count)
        if (count == 1) return listOf(candidates[candidates.size / 2])
        val chosen = ArrayList<LocalDate>(count)
        val step = (candidates.size - 1).toDouble() / (count - 1).toDouble()
        val usedIndices = HashSet<Int>()
        for (i in 0 until count) {
            var idx = kotlin.math.round(i * step).toInt().coerceIn(0, candidates.lastIndex)
            while (idx in usedIndices && idx < candidates.lastIndex) idx++
            while (idx in usedIndices && idx > 0) idx--
            usedIndices += idx
            chosen += candidates[idx]
        }
        return chosen.sorted()
    }

    private fun adjustTemplateForSlot(
        base: WeekTemplate,
        isLong: Boolean,
        isRecovery: Boolean,
        isRecoveryWeek: Boolean,
        budgetMinutes: Int?
    ): WeekTemplate {
        var t = when {
            isLong && !isRecoveryWeek -> {
                if (base.continuousJogMin != null) base.copy(continuousJogMin = base.continuousJogMin + 4)
                else base.copy(reps = base.reps + 1)
            }
            isRecovery -> {
                if (base.continuousJogMin != null) base.copy(continuousJogMin = (base.continuousJogMin - 5).coerceAtLeast(12))
                else base.copy(reps = (base.reps - 1).coerceAtLeast(3))
            }
            else -> base
        }
        if (budgetMinutes != null) {
            fun totalMinutes(x: WeekTemplate): Int {
                val main = if (x.continuousJogMin != null) x.continuousJogMin
                else x.reps * x.jogMin + maxOf(0, x.reps - 1) * x.walkMin
                return x.warmupMin + main + x.cooldownMin
            }
            while (totalMinutes(t) > budgetMinutes && t.reps > 1 && t.continuousJogMin == null) {
                t = t.copy(reps = t.reps - 1)
            }
            if (totalMinutes(t) > budgetMinutes && t.continuousJogMin != null) {
                val maxCont = (budgetMinutes - t.warmupMin - t.cooldownMin).coerceAtLeast(6)
                t = t.copy(continuousJogMin = minOf(t.continuousJogMin, maxCont))
            }
            if (totalMinutes(t) > budgetMinutes) {
                val warm = maxOf(2, minOf(t.warmupMin, budgetMinutes / 5))
                val cool = warm
                val avail = (budgetMinutes - warm - cool).coerceAtLeast(4)
                t = if (t.continuousJogMin != null) {
                    t.copy(warmupMin = warm, cooldownMin = cool, continuousJogMin = avail)
                } else {
                    val j = avail.coerceAtMost(t.jogMin).coerceAtLeast(1)
                    t.copy(warmupMin = warm, cooldownMin = cool, reps = 1, jogMin = j, walkMin = 0)
                }
            }
        }
        return t
    }

    private fun buildSegments(template: WeekTemplate, isRecovery: Boolean): List<RunningStepV2> {
        val out = ArrayList<RunningStepV2>()
        out += RunningStepV2(
            purpose = "WARM_UP",
            effort = "Brisk easy walk warm-up",
            seconds = (template.warmupMin * 60).coerceIn(60, 1800),
            stepIntent = RunningStepIntent.WARMUP
        )
        if (template.continuousJogMin != null) {
            out += RunningStepV2(
                purpose = if (isRecovery) "RECOVERY" else "EASY",
                effort = if (isRecovery) "Very easy recovery jog/walk by feel" else "Easy conversational continuous jog",
                seconds = (template.continuousJogMin * 60).coerceIn(120, 7200),
                stepIntent = if (isRecovery) RunningStepIntent.RECOVERY else RunningStepIntent.AEROBIC_EASY
            )
        } else {
            val stepPurpose = if (isRecovery) "RECOVERY" else "EASY"
            for (r in 1..template.reps) {
                out += RunningStepV2(
                    purpose = stepPurpose,
                    effort = if (isRecovery) "Gentle easy jog ($r/${template.reps}); take walk breaks anytime"
                    else "Easy conversational jog ($r/${template.reps})",
                    seconds = (template.jogMin * 60).coerceIn(30, 3600),
                    stepIntent = if (isRecovery) RunningStepIntent.RECOVERY else RunningStepIntent.AEROBIC_EASY
                )
                if (r < template.reps && template.walkMin > 0) {
                    out += RunningStepV2(
                        purpose = "RECOVERY",
                        effort = "Relaxed recovery walk",
                        seconds = (template.walkMin * 60).coerceIn(30, 1800),
                        stepIntent = RunningStepIntent.RECOVERY
                    )
                }
            }
        }
        out += RunningStepV2(
            purpose = "COOL_DOWN",
            effort = "Easy walk cool-down",
            seconds = (template.cooldownMin * 60).coerceIn(60, 1800),
            stepIntent = RunningStepIntent.COOLDOWN
        )
        return out
    }

    private fun formatDurationLabel(seconds: Int): String =
        if (seconds % 60 == 0) "${seconds / 60} min" else "${seconds} s"
}
