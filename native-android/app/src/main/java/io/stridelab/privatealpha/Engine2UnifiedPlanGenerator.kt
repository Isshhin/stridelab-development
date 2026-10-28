package io.stridelab.privatealpha

import java.time.LocalDate
import java.time.DayOfWeek

/** Deterministic skeleton only: no inferred pace, 1RM, personal readiness or automatic progression. */
internal data class Engine2PlanningRequest(
    val startMonday: LocalDate,
    val weeks: Int,
    val availableDays: Set<Int>,
    val sessionsPerWeek: Map<Engine2Discipline, Int>,
    val preferredLongRunDay: Int? = null,
    val preferredQualityDays: Set<Int> = emptySet(),
    val maximumDailyMinutes: Int = 90,
    val requireRestDay: Boolean = true
) {
    init {
        require(startMonday.dayOfWeek == DayOfWeek.MONDAY)
        require(weeks in 1..16)
        require(availableDays.isNotEmpty() && availableDays.all { it in 1..7 })
        require(sessionsPerWeek.keys.all { it in Engine2Discipline.values() })
        require(sessionsPerWeek.values.all { it in 0..7 })
        require(sessionsPerWeek.values.sum() > 0)
        require(preferredLongRunDay == null || preferredLongRunDay in availableDays)
        require(preferredQualityDays.all { it in availableDays })
        require(preferredQualityDays.size <= 1) {
            "This first Engine 2.0 generator supports at most one quality day; never silently drop a requested session."
        }
        require(preferredQualityDays.isEmpty() || (sessionsPerWeek[Engine2Discipline.RUNNING] ?: 0) >= 2) {
            "Quality running requires at least two weekly runs in this conservative planner."
        }
        require(preferredLongRunDay == null || preferredLongRunDay !in preferredQualityDays) {
            "The long-run and quality-day preferences cannot silently overlap."
        }
        require(maximumDailyMinutes in 20..240)
        if (requireRestDay) require(availableDays.size < 7 || sessionsPerWeek.values.sum() <= 6)
        require(sessionsPerWeek.values.sum() <= availableDays.size) {
            "This conservative generator supports one session per available day; request fewer sessions or a later multi-session policy."
        }
        require(preferredLongRunDay == null || (sessionsPerWeek[Engine2Discipline.RUNNING] ?: 0) > 0)
    }
}

internal data class Engine2PlannedOutline(
    val plan: Engine2PlanView,
    val demands: List<Engine2SessionDemand>,
    val warnings: List<String>,
    val assumptions: List<String>
)

internal object Engine2UnifiedPlanGenerator {
    private val disciplineOrder = listOf(
        Engine2Discipline.RUNNING,
        Engine2Discipline.STRENGTH,
        Engine2Discipline.CALISTHENICS,
        Engine2Discipline.CONDITIONING
    )
    private val defaultMinutes = mapOf(
        Engine2Discipline.RUNNING to 40,
        Engine2Discipline.STRENGTH to 35,
        Engine2Discipline.CALISTHENICS to 25,
        Engine2Discipline.CONDITIONING to 30
    )

    fun generate(ownerId: String, planId: String, request: Engine2PlanningRequest): Engine2PlannedOutline {
        require(ownerId.isNotBlank() && planId.isNotBlank())
        val runs = request.sessionsPerWeek[Engine2Discipline.RUNNING] ?: 0
        val slots = request.availableDays.sorted()
        val fixedLong = request.preferredLongRunDay
        val hardDay = request.preferredQualityDays.sorted().firstOrNull {
            it != fixedLong && runs >= 2
        }
        val occupied = linkedSetOf<Int>()
        val placements = mutableListOf<Triple<Int, Engine2Discipline, Engine2StressTag>>()
        if (fixedLong != null) {
            placements += Triple(fixedLong, Engine2Discipline.RUNNING, Engine2StressTag.EASY)
            occupied += fixedLong
        }
        if (hardDay != null) {
            placements += Triple(hardDay, Engine2Discipline.RUNNING, Engine2StressTag.HARD)
            occupied += hardDay
        }
        val reservedRunning = placements.count { it.second == Engine2Discipline.RUNNING }
        for (discipline in disciplineOrder) {
            val count = request.sessionsPerWeek[discipline] ?: 0
            val needed = count - if (discipline == Engine2Discipline.RUNNING) reservedRunning else 0
            repeat(needed) {
                val day = slots.firstOrNull { it !in occupied }
                    ?: throw IllegalArgumentException("Insufficient available training days")
                occupied += day
                placements += Triple(day, discipline, when (discipline) {
                    Engine2Discipline.RUNNING -> Engine2StressTag.EASY
                    else -> Engine2StressTag.UNKNOWN
                })
            }
        }
        val byDay = placements.sortedWith(compareBy<Triple<Int, Engine2Discipline, Engine2StressTag>> {
            it.first
        }.thenBy { it.second.ordinal })
        val sessions = mutableListOf<Engine2SessionTarget>()
        val demands = mutableListOf<Engine2SessionDemand>()
        for (week in 0 until request.weeks) {
            for ((day, discipline, stress) in byDay) {
                val duration = defaultMinutes.getValue(discipline)
                require(duration <= request.maximumDailyMinutes) {
                    "The requested time budget cannot accommodate the current conservative session template."
                }
                val id = "w" + week.toString().padStart(2, '0') + "-d" + day + "-" + discipline.name.lowercase()
                sessions += Engine2SessionTarget(id,
                    request.startMonday.plusWeeks(week.toLong()).plusDays((day - 1).toLong()),
                    discipline, duration)
                demands += Engine2SessionDemand(
                    id, stress,
                    lowerBody = discipline == Engine2Discipline.STRENGTH,
                    longRun = fixedLong == day && discipline == Engine2Discipline.RUNNING
                )
            }
        }
        val plan = Engine2PlanView(ownerId, planId, 1, Engine2PlanStatus.DRAFT, sessions)
        val warnings = Engine2ScheduleAuditor.inspect(plan, demands, request.maximumDailyMinutes)
            .map { it.explanation }.distinct()
        return Engine2PlannedOutline(
            plan, demands, warnings,
            listOf(
                "Unapproved draft: no calendar changes are persisted automatically.",
                "Session durations are generic placeholders, not prescriptions calibrated to training history.",
                "No automatic mileage, pace, resistance load or skill-level progression is inferred.",
                "Strength sessions are conservatively flagged as potentially lower-body until a reviewed exercise template is selected."
            )
        )
    }
}
