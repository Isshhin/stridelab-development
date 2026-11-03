package io.stridelab.privatealpha

import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor

internal const val ENGINE15_VERSION = "1.5.0"
internal const val ENGINE15_FOUNDATION_POLICY = "stridelab.engine15.foundation-20261103.1"
internal const val ENGINE15_POLICY = "stridelab.engine15.bounded-running-20261103.1"
internal const val ENGINE15_MULTI_POLICY = "stridelab.engine15.multi-session-20261103.1"

/** Deterministic, explicitly selected candidate. Does not infer fitness or recovery. */
internal object Engine15 : PlanGenerationEngine {
    override val version = ENGINE15_VERSION
    override val policyId = ENGINE15_POLICY

    internal fun validate(request: PlanRequest) {
        if (request.policyChoice != "engine15")
            throw PlanConstraint("Engine 1.5 must be selected explicitly.")
        if (requiresMulti(request))
            MultiSessionEngine1.validate(request.copy(policyChoice = "multi-session"))
        else Engine1.validate(request)
    }

    private fun requiresMulti(p:PlanRequest) =
        p.runDays + p.strengthDays + p.calDays + p.conditioningDays > p.days.size

    private data class QualityStructure(
        val title: String, val instructions: String, val requiredMinutes: Int
    )

    private fun structure(goal: String): QualityStructure = when (goal) {
        "Improve 5K" -> QualityStructure(
            "Controlled 5K intervals",
            "3 × 3 min comfortably hard (effort 6–7/10); 2 min easy between repetitions; never sprint",
            35
        )
        "Improve 10K" -> QualityStructure(
            "Controlled 10K intervals",
            "2 × 6 min comfortably hard (effort 6–7/10); 3 min easy between repetitions; never sprint",
            35
        )
        "Half marathon" -> QualityStructure(
            "Controlled sustained intervals",
            "2 × 8 min steady controlled effort (6/10); 3 min easy between repetitions",
            40
        )
        "Marathon" -> QualityStructure(
            "Steady aerobic intervals",
            "2 × 8 min steady, not racing (effort 5–6/10); 3 min easy between repetitions",
            40
        )
        else -> QualityStructure(
            "Controlled aerobic intervals",
            "2 × 6 min comfortably steady (effort 5–6/10); 3 min easy between repetitions",
            35
        )
    }

    private fun qualitySteps(minutes: Int, spec: QualityStructure): List<String> = listOf(
        "10 min easy warm-up using conversational effort",
        spec.instructions,
        "At least 8 min easy cool-down; use remaining time for easy running or walking; " +
            "finish within the planned $minutes min"
    )

    private fun easySteps(minutes: Int, beginners: Boolean): List<String> {
        val warmUp = minOf(5, minutes / 3)
        return listOf(
            "Start with $warmUp min comfortable walk or easy jog",
            if (beginners) "Alternate easy running and walking; shorten running portions as needed"
            else "Keep the effort conversational. No unmeasured HR zone or race pace is assumed",
            "Finish with an easy walk or jog; total session $minutes min"
        )
    }

    /**
     * Planned-mileage ceiling from athlete-supplied weekly mileage.
     * Three staged loading weeks followed by a lighter fourth week.
     * No catch-up mileage is added after deload weeks.
     */
    internal fun runningCeiling(week: Int, recentKm: Double): Double {
        require(week >= 0 && recentKm.isFinite() && recentKm >= 0.0)
        val block = week / 4
        val part = week % 4
        val factor = if (part == 3) minOf(0.90, 0.76 + block * 0.04)
            else minOf(1.10, 0.90 + part * 0.05 + block * 0.045)
        return recentKm * factor
    }

    private fun weekIndex(request: PlanRequest, session: PlannedSession) =
        (ChronoUnit.DAYS.between(request.start, session.date) / 7L).toInt()

    private fun roundDownKm(km: Double) = floor(km * 10.0 + 1e-9) / 10.0

    override fun generate(
        request: PlanRequest, owner: String, id: String, now: Instant
    ): PlanSnapshot {
        validate(request)
        // Keep the original generator, calendar, multi-discipline recipes and
        // saved-plan contract unchanged; only refine proposed RUNNING sessions.
        val multi = requiresMulti(request)
        val baseline = if(multi)
            MultiSessionEngine1.generate(request.copy(policyChoice = "multi-session"), owner, id, now)
        else Engine1.generate(request.copy(policyChoice = "baseline"), owner, id, now)
        val weekly = baseline.sessions.groupBy { weekIndex(request, it) }
        val adapted = baseline.sessions.map { original ->
            if (original.discipline != "run") return@map original

            val week = weekIndex(request, original)
            val runs = weekly.getValue(week).filter { it.discipline == "run" }
            val originalKm = runs.sumOf { it.distanceKm ?: 0.0 }
            val ceiling = runningCeiling(week, request.weeklyKm)
            val scale = if (originalKm > 0.0) minOf(1.0, ceiling / originalKm) else 1.0
            var distance = original.distanceKm?.times(scale)

            if (original.kind == "long" && distance != null && request.runDays >= 3) {
                // Cap the long run against the FINAL, not the pre-adjusted,
                // weekly total: long <= 2/3 of other run distances means <=40%.
                val otherKm = runs.filter { it.id != original.id }
                    .sumOf { roundDownKm((it.distanceKm ?: 0.0) * scale) }
                distance = minOf(distance, otherKm * (2.0 / 3.0))
                if (request.longestKm > 0.0) {
                    val increase = if (request.returning && week < 2) 0.0 else 1.0
                    distance = minOf(distance, request.longestKm + increase)
                }
            }
            distance = distance?.let(::roundDownKm)
            val minutes = if (distance == null || original.distanceKm == null ||
                distance >= original.distanceKm
            ) original.minutes else minOf(
                original.minutes,
                maxOf(10, ceil(distance * request.planningPace / 60.0).toInt())
            )

            val spec = structure(request.goal)
            val taper = request.goal in setOf("Half marathon", "Marathon") &&
                request.weeks >= 8 && week >= request.weeks - 2
            val allowedQuality = when {
                request.experience == 0 || request.weeklyKm < 20.0 -> 0
                request.returning && week < 3 -> 0
                week % 4 == 3 || taper -> 0
                request.weeklyKm < 40.0 -> minOf(1, request.qualityCount)
                else -> request.qualityCount
            }
            val position = runs.filter { it.kind == "quality" }.sortedBy { it.id }
                .indexOfFirst { it.id == original.id }
            val quality = original.kind == "quality" && original.quality &&
                position in 0 until allowedQuality && minutes >= spec.requiredMinutes

            when {
                quality -> original.copy(
                    minutes = minutes, distanceKm = distance, title = spec.title,
                    quality = true, steps = qualitySteps(minutes, spec),
                    reason = "Controlled goal-specific quality with easy recovery. " +
                        "Effort is descriptive; no measured threshold or pace was inferred."
                )
                original.kind == "quality" -> original.copy(
                    kind = "easy", title = "Easy aerobic run", quality = false,
                    minutes = minutes, distanceKm = distance,
                    steps = easySteps(minutes, request.experience == 0 || request.weeklyKm == 0.0),
                    reason = "Requested quality was replaced by an easier run because " +
                        "mileage, time, a lighter week, a return or a taper did not support it."
                )
                else -> original.copy(
                    minutes = minutes, distanceKm = distance,
                    steps = if (minutes == original.minutes) original.steps
                        else easySteps(minutes, request.experience == 0 || request.weeklyKm == 0.0),
                    reason = original.reason + if (distance != original.distanceKm)
                        " Distance was bounded against the reported recent mileage; " +
                            "removed distance is not added to another workout." else ""
                )
            }
        }
        check(adapted.map { it.id to it.date } == baseline.sessions.map { it.id to it.date })
        val originalById = baseline.sessions.associateBy { it.id }
        check(adapted.all { s ->
            val previous = originalById.getValue(s.id)
            s.minutes <= previous.minutes &&
                (s.distanceKm == null ||
                    (previous.distanceKm != null && s.distanceKm <= previous.distanceKm + 1e-8)) &&
                (!s.quality || previous.quality)
        })
        return baseline.copy(
            engineVersion = version,
            policyId = if(multi) ENGINE15_MULTI_POLICY else policyId,
            request = request,
            sessions = adapted,
            explanations = listOf(
                "Engine 1.5 policy (${if (multi) ENGINE15_MULTI_POLICY else policyId}): the verified baseline " +
                    "still supplies scheduled days, disciplines and equipment prescriptions.",
                "Running volume has staged loading ceilings and lighter fourth weeks " +
                    "based on manually supplied recent mileage. Removed mileage is never " +
                    "redistributed or made up after a missed workout.",
                "With at least three weekly runs, long-run distance is capped to 40% " +
                    "of final planned weekly distance. Missing mileage remains time-based.",
                "Quality running is limited by supplied mileage, experience, return " +
                    "status, lighter weeks, race taper and available time. Effort " +
                    "descriptions are not measured thresholds, zones or target paces.",
                if(multi) "Multi-session days use the existing conservative easy-run + calisthenics pairing and daily budget. Incompatible schedules are rejected." else "Single-session Engine 1.0 calendar rules remain unchanged.",
                "Review every workout before approval; no existing saved plan, " +
                    "completed activity or athlete record has been changed."
            ) + baseline.explanations.filterNot {
                it.startsWith("Engine 1.0 uses ") ||
                    it.startsWith("Every fourth week is lighter.")
            }
        )
    }
}
