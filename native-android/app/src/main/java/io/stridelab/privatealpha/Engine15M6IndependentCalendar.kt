package io.stridelab.privatealpha

import java.time.LocalDate
import kotlin.math.abs

/**
 * M6 Stage A — independent, shadow-only placement of M5A EASY/LONG slots.
 * This produces calendar previews, NOT executable PlannedSession instances.
 * Quality previews remain entirely outside this calendar decision.
 */
internal data class Engine15M6CalendarInput(
    val startDate: LocalDate,
    val availableIsoDays: Set<Int>,
    val preferredLongIsoDay: Int,
    val blockedDates: Set<LocalDate> = emptySet(),
    val preferredEasyIsoDays: Set<Int> = emptySet(),
    val lockedSlotDates: Map<Pair<Int, Int>, LocalDate> = emptyMap()
) {
    init {
        require(availableIsoDays.all { it in 1..7 })
        require(preferredLongIsoDay in 1..7)
        require(preferredEasyIsoDays.all { it in 1..7 })
    }
}

internal data class Engine15M6PlacedRun(
    val slot: Engine15M5WorkoutSlot,
    val date: LocalDate
) {
    val isoDay: Int get() = date.dayOfWeek.value
    val distanceKm: Double get() = slot.distanceKm
    val role: Engine15M5RunRole get() = slot.role

    init {
        require(date.dayOfWeek.value in 1..7)
        require(slot.distanceKm > 0.0)
    }

    val executable: Boolean get() = false
    val qualityAuthorized: Boolean get() = false
}

internal data class Engine15M6CalendarWeek(
    val weekNumber: Int,
    val kind: Engine15IndependentVolumePlanner.WeekKind,
    val budgetKm: Double,
    val runs: List<Engine15M6PlacedRun>
) {
    init {
        require(weekNumber > 0 && budgetKm.isFinite() && budgetKm > 0.0)
        require(runs.isNotEmpty())
        require(runs.all { it.slot.weekNumber == weekNumber })
        require(runs.map { it.date }.distinct().size == runs.size)
        require(runs.map { it.slot.slotNumber }.distinct().size == runs.size)
        require(abs(runs.sumOf { it.distanceKm } - budgetKm) < 1e-6)
    }
}

internal sealed interface Engine15M6CalendarDecision {
    data class Deferred(
        val reasonCode: String,
        val explanation: String,
        val weekNumber: Int? = null
    ) : Engine15M6CalendarDecision

    data class Candidate(
        val policyId: String,
        val weeks: List<Engine15M6CalendarWeek>,
        val ruleIds: List<String>,
        val calendarAssigned: Boolean = true,
        val qualityScheduled: Boolean = false,
        val sourceEvidenceVerified: Boolean = false,
        val executable: Boolean = false,
        val authorizedForProduction: Boolean = false
    ) : Engine15M6CalendarDecision {
        init {
            require(weeks.isNotEmpty())
            require(weeks.map { it.weekNumber } == (1..weeks.size).toList())
            require(ruleIds.distinct() == ruleIds)
            require(calendarAssigned && !qualityScheduled &&
                !sourceEvidenceVerified && !executable && !authorizedForProduction)
        }
    }
}

/**
 * M6A does NOT create or authorize quality workouts, pace estimates,
 * double-session days, cross-discipline plans, or production approvals.
 * It never invokes Engine1 or the existing Engine15 generation path.
 */
internal object Engine15M6IndependentCalendar {
    const val POLICY_ID = "engine15.m6a.m4b-distance-calendar-shadow-20261117.1"

    private fun cycleGaps(isoDays: List<Int>): List<Int> =
        isoDays.zipWithNext { a, b -> b - a } +
            listOf(isoDays.first() + 7 - isoDays.last())

    private fun chooseDays(
        possible: Set<Int>,
        count: Int,
        longDay: Int?,
        preferredEasy: Set<Int>,
        requiredDays: Set<Int>
    ): List<Int>? {
        val others = possible.filter { it != longDay }.sorted()
        val required = count - if (longDay == null) 0 else 1
        if (others.size < required || required <= 0) return null
        val combinations = (0 until (1 shl others.size)).mapNotNull { bits ->
            if (Integer.bitCount(bits) != required) null
            else {
                val days = others.indices.filter { index -> bits and (1 shl index) != 0 }
                    .map { others[it] }
                (days + listOfNotNull(longDay)).sorted().takeIf { requiredDays.all(it::contains) }
            }
        }
        val ordering = compareByDescending<List<Int>> {
            it.count { day -> day != longDay && day in preferredEasy }
        }.thenByDescending {
            cycleGaps(it).minOrNull() ?: 0
        }.thenBy {
            val ideal = 7.0 / it.size
            cycleGaps(it).sumOf { gap -> (gap - ideal) * (gap - ideal) }
        }.thenBy { it.joinToString(",") }
        return combinations.sortedWith(ordering).firstOrNull()
    }

    fun schedule(
        source: Engine15IndependentVolumePlanner.Result,
        calendar: Engine15M6CalendarInput
    ): Engine15M6CalendarDecision {
        fun defer(code: String, explanation: String, week: Int? = null) =
            Engine15M6CalendarDecision.Deferred(code, explanation, week)

        // Recompute M4B and M5A at this boundary so a caller-supplied budget
        // cannot bypass prior weekly, recovery or session exposure checks.
        val base = when (val decided = Engine15M5WorkoutComposer.compose(source)) {
            is Engine15M5CompositionDecision.Deferred ->
                return defer("BASE_" + decided.reasonCode,
                    decided.explanation, decided.weekNumber)
            is Engine15M5CompositionDecision.Candidate -> decided
        }

        val days = source.input.runningDays
        if (calendar.availableIsoDays.size < days)
            return defer("INSUFFICIENT_AVAILABLE_DAYS",
                "The number of available weekdays cannot fit the requested run frequency.")

        val weeks = mutableListOf<Engine15M6CalendarWeek>()
        for (week in base.weeks) {
            val weekStart = calendar.startDate.plusWeeks((week.weekNumber - 1).toLong())
            val longSlot = week.slots.singleOrNull { it.role == Engine15M5RunRole.LONG }
            val lockedLong = longSlot?.let { calendar.lockedSlotDates[week.weekNumber to it.slotNumber] }
            val longDay: Int? = if (days >= 3) lockedLong?.dayOfWeek?.value ?: calendar.preferredLongIsoDay else null
            if (longDay != null && longDay !in calendar.availableIsoDays)
                return defer("LONG_DAY_UNAVAILABLE",
                    "The preferred long-run day is not in the stated training availability.")
            val possible = calendar.availableIsoDays.filter { day ->
                weekStart.plusDays(((day - weekStart.dayOfWeek.value + 7) % 7).toLong()) !in calendar.blockedDates
            }.toSet()
            if (longDay != null && longDay !in possible)
                return defer("LONG_DAY_BLOCKED",
                    "The preferred long-run day is blocked; no silent long-run relocation.",
                    week.weekNumber)
            if (possible.size < days)
                return defer("INSUFFICIENT_WEEK_AVAILABILITY",
                    "Blocked dates leave fewer running days than the M4B/M5A plan requires.",
                    week.weekNumber)

            val weekLocks = calendar.lockedSlotDates.filterKeys { it.first == week.weekNumber }
            if (weekLocks.keys.any { key -> week.slots.none { it.slotNumber == key.second } } ||
                weekLocks.values.any { date ->
                    val delta = ((date.dayOfWeek.value - weekStart.dayOfWeek.value + 7) % 7).toLong()
                    date != weekStart.plusDays(delta) || date in calendar.blockedDates ||
                        date.dayOfWeek.value !in calendar.availableIsoDays
                })
                return defer("LOCKED_SLOT_UNAVAILABLE", "A locked workout date cannot be used in this plan week.", week.weekNumber)
            val lockedEasyDays = weekLocks.filterKeys { it.second != longSlot?.slotNumber }.values
                .map { it.dayOfWeek.value }.toSet()
            if (lockedLong != null && lockedLong.dayOfWeek.value !in possible)
                return defer("LOCKED_LONG_SLOT_UNAVAILABLE", "The locked long-run date is unavailable.", week.weekNumber)
            val chosen = chooseDays(
                possible, days, longDay, calendar.preferredEasyIsoDays, lockedEasyDays
            ) ?: return defer("NO_WEEKDAY_ASSIGNMENT",
                "Could not place every independent distance-only slot in available days.",
                week.weekNumber)

            val easy = week.slots.filter { it.role == Engine15M5RunRole.EASY }
            val easyDays = chosen.filter { it != longDay }
            if (easy.size != easyDays.size)
                return defer("SLOT_ROLE_COUNT_MISMATCH",
                    "The selected day count disagrees with the M5A easy/long roles.",
                    week.weekNumber)

            val pairs = mutableListOf<Pair<Engine15M5WorkoutSlot, Int>>()
            val lockedEasy = easy.filter { (week.weekNumber to it.slotNumber) in weekLocks }
            for (slot in lockedEasy) pairs += slot to weekLocks.getValue(week.weekNumber to slot.slotNumber).dayOfWeek.value
            val freeDays = easyDays - lockedEasy.map { weekLocks.getValue(week.weekNumber to it.slotNumber).dayOfWeek.value }.toSet()
            pairs += easy.filterNot { it in lockedEasy }.zip(freeDays)
            if (longDay != null) {
                val longSlot = longSlot ?: return defer("LONG_SLOT_NOT_FOUND",
                    "M5A did not supply the one required long-run slot.",
                    week.weekNumber)
                pairs.add(longSlot to longDay)
            }
            val placed = pairs.map { (slot, iso) ->
                Engine15M6PlacedRun(slot, weekStart.plusDays(((iso - weekStart.dayOfWeek.value + 7) % 7).toLong()))
            }.sortedWith(compareBy({ it.date }, { it.slot.slotNumber }))
            if (placed.size != days || placed.map { it.date }.toSet().size != days)
                return defer("DUPLICATE_OR_MISSING_PLACEMENT",
                    "Each M5A slot must occupy one distinct permitted calendar date.",
                    week.weekNumber)
            if (placed.map { it.slot }.toSet() != week.slots.toSet())
                return defer("SOURCE_SLOT_ALTERED",
                    "Placed sessions must preserve every original typed M5A slot.",
                    week.weekNumber)

            weeks += Engine15M6CalendarWeek(
                weekNumber = week.weekNumber,
                kind = week.kind,
                budgetKm = week.budgetKm,
                runs = placed
            )
        }

        return Engine15M6CalendarDecision.Candidate(
            policyId = POLICY_ID,
            weeks = weeks,
            ruleIds = listOf(
                "M4B_AND_M5A_RECOMPUTED",
                "EXPLICIT_AVAILABILITY_AND_BLOCKED_DATES",
                "PREFERRED_LONG_DAY_PRESERVED",
                "EXACT_SLOT_AND_WEEKLY_KILOMETERS_RETAINED",
                "DETERMINISTIC_CALENDAR_HEURISTIC",
                "QUALITY_SPACING_AND_CROSS_DISCIPLINE_REVIEW_PENDING",
                "NO_EXECUTABLE_OR_PRODUCTION_APPROVAL"
            )
        )
    }
}
