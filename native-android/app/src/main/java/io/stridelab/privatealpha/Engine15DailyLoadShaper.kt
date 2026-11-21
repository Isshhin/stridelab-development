package io.stridelab.privatealpha

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Calendar-aware day-to-day running load and recovery shaper.
 *
 * Replaces flat equal division of non-long weekly mileage with hard/easy polarity:
 * days immediately following a quality or long run (or sandwiched between two
 * hard/long sessions) become shorter Recovery runs, while quality sessions and
 * fresher mid-week aerobic days carry proportionally more volume — strictly
 * preserving each week's exact M4B mileage budget and safety ceilings.
 */
internal object Engine15DailyLoadShaper {

    private enum class ShapeRole(val weight: Double) {
        LONG(0.0),
        QUALITY(1.15),
        RECOVERY_SANDWICHED(0.68),
        RECOVERY_POST_HARD(0.76),
        RECOVERY_POST_HARD_CUTBACK(0.82),
        RECOVERY_PRE_LONG(0.76),
        RECOVERY_PRE_LONG_CUTBACK(0.85),
        AEROBIC_EASY(1.10),
        STANDARD_EASY(1.00),
        CUTBACK_EASY(1.05)
    }

    private fun roundTenths(km: Double): Int = (km * 10.0).roundToInt()

    private fun floorTenths(km: Double): Int = floor(km * 10.0 + 1e-8).toInt()

    private fun formatMetersAsKm(meters: Int): String {
        val km = meters / 1000.0
        return if (km < 1.0 && meters % 100 != 0) {
            String.format(Locale.US, "%.2f km", km)
        } else {
            String.format(Locale.US, "%.1f km", km)
        }
    }

    fun shape(
        sessions: List<PlannedSession>,
        volume: Engine15IndependentVolumePlanner.Result,
        request: PlanRequest,
        qualitySessionIds: Set<String>,
        pinnedSessionIds: Set<String> = emptySet()
    ): List<PlannedSession> {
        if (sessions.isEmpty() || request.weeks <= 0) return sessions

        val hardDates = sessions.asSequence()
            .filter { it.kind == "quality" || it.quality || it.id in qualitySessionIds }
            .map { it.date }
            .toSet()
        val longDates = sessions.asSequence()
            .filter { it.kind == "long" }
            .map { it.date }
            .toSet()
        val hardOrLongDates = hardDates + longDates

        val easyCapTenths = if (request.userDirectedPlanning) {
            1200
        } else {
            volume.athleteBaseline.reportedLongestRunKm
                ?.let { floorTenths((it + 1.0) * 0.8) }
                ?: 1200
        }

        val timeBudgetCapTenths = if (request.timeBudgetEnabled) {
            var cap = floorTenths((request.minutes * 60.0) / request.planningPace).coerceAtLeast(1)
            while (cap > 1 && ceil((cap / 10.0) * request.planningPace / 60.0).toInt() > request.minutes) {
                cap--
            }
            cap
        } else {
            Int.MAX_VALUE
        }

        val shapedById = HashMap<String, PlannedSession>(sessions.size)

        for (weekIndex in 0 until request.weeks) {
            val weekStart = request.start.plusWeeks(weekIndex.toLong())
            val weekEnd = weekStart.plusWeeks(1)
            val weekSessions = sessions.filter { it.date >= weekStart && it.date < weekEnd }
            if (weekSessions.isEmpty()) continue

            val weekInfo = volume.weeks.getOrNull(weekIndex) ?: continue
            val weekBudgetKm = weekInfo.proposedKm ?: continue
            val isRecoveryWeek = weekInfo.kind == Engine15IndependentVolumePlanner.WeekKind.RECOVERY
            val weekLongSession = weekSessions.firstOrNull { it.kind == "long" }
            val weekLongDate = weekLongSession?.date
            val weekLongTenths = weekLongSession?.distanceKm?.let(::roundTenths)

            val roles = HashMap<String, ShapeRole>(weekSessions.size)
            for (s in weekSessions) {
                val role = when {
                    s.kind == "long" -> ShapeRole.LONG
                    s.id in qualitySessionIds || s.quality || s.kind == "quality" -> ShapeRole.QUALITY
                    else -> {
                        val prevHardOrLong = s.date.minusDays(1) in hardOrLongDates
                        val nextHardOrLong = s.date.plusDays(1) in hardOrLongDates
                        val isPreLongShakeout = weekSessions.size >= 4 &&
                            weekLongDate != null && s.date.plusDays(1) == weekLongDate
                        when {
                            prevHardOrLong && nextHardOrLong ->
                                if (isRecoveryWeek) ShapeRole.RECOVERY_POST_HARD_CUTBACK else ShapeRole.RECOVERY_SANDWICHED
                            prevHardOrLong ->
                                if (isRecoveryWeek) ShapeRole.RECOVERY_POST_HARD_CUTBACK else ShapeRole.RECOVERY_POST_HARD
                            isPreLongShakeout ->
                                if (isRecoveryWeek) ShapeRole.RECOVERY_PRE_LONG_CUTBACK else ShapeRole.RECOVERY_PRE_LONG
                            !isRecoveryWeek && weekSessions.size >= 4 && !prevHardOrLong && !nextHardOrLong ->
                                ShapeRole.AEROBIC_EASY
                            else -> if (isRecoveryWeek) ShapeRole.CUTBACK_EASY else ShapeRole.STANDARD_EASY
                        }
                    }
                }
                roles[s.id] = role
            }

            // If no day in a regular loading week was classified as recovery, downgrade AEROBIC_EASY
            // back to STANDARD_EASY so non-long easy days stay balanced.
            val hasRecoveryInWeek = roles.values.any { isRecoveryRole(it) }
            if (!hasRecoveryInWeek) {
                for (s in weekSessions) {
                    if (roles[s.id] == ShapeRole.AEROBIC_EASY) {
                        roles[s.id] = ShapeRole.STANDARD_EASY
                    } else if (roles[s.id] == ShapeRole.CUTBACK_EASY) {
                        roles[s.id] = ShapeRole.STANDARD_EASY
                    }
                }
            }

            val adjustable = weekSessions
                .filter { it.kind != "long" && it.id !in pinnedSessionIds && it.distanceKm != null }
                .sortedWith(compareBy<PlannedSession> { it.date }.thenBy { it.id })

            val allocatedTenths = HashMap<String, Int>(weekSessions.size)
            if (adjustable.size >= 2) {
                val totalTenths = roundTenths(weekBudgetKm)
                val fixedTenths = weekSessions
                    .filter { it !in adjustable }
                    .sumOf { roundTenths(it.distanceKm ?: 0.0) }
                val remainingTenths = totalTenths - fixedTenths

                if (remainingTenths >= adjustable.size) {
                    val avgTenths = remainingTenths / adjustable.size.toDouble()
                    val floorFromAvg = floor(avgTenths).toInt().coerceAtLeast(1)
                    val minTenths = minOf(30, floor(avgTenths * 0.65).toInt())
                        .coerceAtLeast(1)
                        .coerceAtMost(floorFromAvg)
                    val longCapBound = if (weekLongTenths != null) {
                        maxOf(ceil(avgTenths).toInt(), weekLongTenths - 5)
                    } else {
                        Int.MAX_VALUE
                    }
                    val existingMax = adjustable.maxOf { roundTenths(it.distanceKm!!) }
                    val rawMaxCap = minOf(easyCapTenths, longCapBound, timeBudgetCapTenths)
                    val maxTenths = maxOf(minTenths, rawMaxCap, existingMax)

                    val distributed = allocateWeightedTenths(
                        sessions = adjustable,
                        weights = adjustable.map { roles.getValue(it.id).weight },
                        targetSum = remainingTenths,
                        minTenths = minTenths,
                        maxTenths = maxTenths
                    )
                    adjustable.forEachIndexed { idx, s ->
                        allocatedTenths[s.id] = distributed[idx]
                    }
                }
            }

            for (s in weekSessions) {
                val role = roles.getValue(s.id)
                val originalKm = s.distanceKm
                val newTenths = allocatedTenths[s.id]
                if (originalKm == null || s.kind == "long" || s.id in pinnedSessionIds) {
                    shapedById[s.id] = s
                    continue
                }
                val finalTenths = newTenths ?: roundTenths(originalKm)
                val finalKm = finalTenths / 10.0
                val finalMeters = finalTenths * 100
                val finalMinutes = ceil(finalKm * request.planningPace / 60.0).toInt().coerceAtLeast(1)
                val distanceChanged = finalTenths != roundTenths(originalKm)

                shapedById[s.id] = when {
                    isRecoveryRole(role) && role != ShapeRole.QUALITY -> {
                        val recoveryReason = buildRecoveryReason(
                            date = s.date,
                            role = role,
                            isRecoveryWeek = isRecoveryWeek,
                            hardDates = hardDates,
                            longDates = longDates
                        )
                        s.copy(
                            title = "Recovery run",
                            distanceKm = finalKm,
                            minutes = finalMinutes,
                            kind = "easy",
                            quality = false,
                            steps = listOf("${formatMetersAsKm(finalMeters)} at very easy recovery pace"),
                            reason = recoveryReason,
                            runningPrescription = RunningPrescription(
                                role = "RECOVERY",
                                family = null,
                                segments = listOf(
                                    RunningSegment(
                                        "RECOVERY",
                                        finalMeters,
                                        "Recovery, very easy conversational pace; keep effort low and legs fresh."
                                    )
                                )
                            )
                        )
                    }
                    role == ShapeRole.AEROBIC_EASY -> {
                        val easyReason = buildString {
                            append(if (isRecoveryWeek) "Recovery week. " else "Loading week. ")
                            append("Fresher mid-week aerobic run carrying slightly higher easy volume between harder days.")
                        }
                        s.copy(
                            distanceKm = finalKm,
                            minutes = finalMinutes,
                            steps = listOf("${formatMetersAsKm(finalMeters)} at easy, conversational effort"),
                            reason = easyReason,
                            runningPrescription = s.runningPrescription?.copy(
                                role = "EASY",
                                segments = listOf(
                                    RunningSegment(
                                        "EASY",
                                        finalMeters,
                                        "Easy, conversational effort; no pace or heart-rate target."
                                    )
                                )
                            )
                        )
                    }
                    distanceChanged -> {
                        s.copy(
                            distanceKm = finalKm,
                            minutes = finalMinutes,
                            steps = listOf("${formatMetersAsKm(finalMeters)} at easy, conversational effort"),
                            runningPrescription = s.runningPrescription?.let { rx ->
                                if (rx.mixedSegments == null && rx.segments.size == 1) {
                                    rx.copy(segments = listOf(rx.segments.single().copy(meters = finalMeters)))
                                } else rx
                            }
                        )
                    }
                    else -> s
                }
            }
        }

        return sessions.map { shapedById[it.id] ?: it }
    }

    private fun isRecoveryRole(role: ShapeRole): Boolean = role in setOf(
        ShapeRole.RECOVERY_SANDWICHED,
        ShapeRole.RECOVERY_POST_HARD,
        ShapeRole.RECOVERY_POST_HARD_CUTBACK,
        ShapeRole.RECOVERY_PRE_LONG,
        ShapeRole.RECOVERY_PRE_LONG_CUTBACK
    )

    private fun buildRecoveryReason(
        date: LocalDate,
        role: ShapeRole,
        isRecoveryWeek: Boolean,
        hardDates: Set<LocalDate>,
        longDates: Set<LocalDate>
    ): String {
        val prefix = if (isRecoveryWeek) "Recovery week. " else "Loading week. "
        val prevWasQuality = date.minusDays(1) in hardDates
        val prevWasLong = date.minusDays(1) in longDates
        val nextIsHard = date.plusDays(1) in hardDates
        val nextIsLong = date.plusDays(1) in longDates
        val detail = when {
            role == ShapeRole.RECOVERY_SANDWICHED -> when {
                prevWasQuality && nextIsHard ->
                    "Short recovery run between two quality workouts to flush fatigue and keep legs fresh."
                prevWasQuality && nextIsLong ->
                    "Short recovery run between yesterday's quality session and tomorrow's long run."
                prevWasLong && nextIsHard ->
                    "Short recovery run after yesterday's long run before tomorrow's quality session."
                else ->
                    "Short recovery run positioned between heavier training days to support adaptation."
            }
            prevWasQuality ->
                "Short, very easy recovery run after yesterday's quality session to promote blood flow and absorb the work."
            prevWasLong ->
                "Short recovery run after yesterday's long run so you absorb endurance work without carrying fatigue."
            role == ShapeRole.RECOVERY_PRE_LONG || role == ShapeRole.RECOVERY_PRE_LONG_CUTBACK ->
                "Short shakeout recovery run ahead of tomorrow's long run so legs arrive fresh."
            else ->
                "Short recovery run balanced within your weekly volume."
        }
        return prefix + detail
    }

    /**
     * Bounded iterative water-filling over weights followed by largest-remainder tenths
     * rounding so every slot lies in [minTenths, maxTenths] and the sum matches [targetSum]
     * to exact 0.1 km precision.
     */
    private fun allocateWeightedTenths(
        sessions: List<PlannedSession>,
        weights: List<Double>,
        targetSum: Int,
        minTenths: Int,
        maxTenths: Int
    ): IntArray {
        val n = sessions.size
        val result = IntArray(n)
        val locked = BooleanArray(n)
        var remaining = targetSum

        while (true) {
            val freeIndices = (0 until n).filter { !locked[it] }
            if (freeIndices.isEmpty()) break
            val weightSum = freeIndices.sumOf { weights[it] }.coerceAtLeast(1e-9)
            var worstIdx = -1
            var worstClamp = 0
            var worstViolation = 0.0
            for (i in freeIndices) {
                val exact = remaining * (weights[i] / weightSum)
                val lowViolation = minTenths - exact
                val highViolation = exact - maxTenths
                if (lowViolation > 1e-9 && lowViolation > worstViolation) {
                    worstIdx = i
                    worstClamp = minTenths
                    worstViolation = lowViolation
                } else if (highViolation > 1e-9 && highViolation > worstViolation) {
                    worstIdx = i
                    worstClamp = maxTenths
                    worstViolation = highViolation
                }
            }
            if (worstIdx < 0) break
            result[worstIdx] = worstClamp
            locked[worstIdx] = true
            remaining -= worstClamp
        }

        val free = (0 until n).filter { !locked[it] }
        if (free.isNotEmpty()) {
            val weightSum = free.sumOf { weights[it] }.coerceAtLeast(1e-9)
            val fractions = HashMap<Int, Double>(free.size)
            for (i in free) {
                val exact = remaining * (weights[i] / weightSum)
                val base = floor(exact + 1e-9).toInt().coerceIn(minTenths, maxTenths)
                result[i] = base
                fractions[i] = exact - base
            }
            val plusOrder = free.sortedWith(
                compareByDescending<Int> { fractions[it] ?: 0.0 }
                    .thenByDescending { weights[it] }
                    .thenBy { sessions[it].date }
            )
            val minusOrder = free.sortedWith(
                compareBy<Int> { fractions[it] ?: 0.0 }
                    .thenBy { weights[it] }
                    .thenByDescending { sessions[it].date }
            )
            var residual = targetSum - result.sum()
            while (residual > 0) {
                val candidate = plusOrder.firstOrNull { result[it] < maxTenths }
                    ?: (0 until n).first { result[it] < maxTenths }
                result[candidate]++
                residual--
            }
            while (residual < 0) {
                val candidate = minusOrder.firstOrNull { result[it] > minTenths }
                    ?: (0 until n).first { result[it] > minTenths }
                result[candidate]--
                residual++
            }
        } else {
            var residual = targetSum - result.sum()
            while (residual > 0) {
                val candidate = (0 until n).first { result[it] < maxTenths }
                result[candidate]++
                residual--
            }
            while (residual < 0) {
                val candidate = (0 until n).first { result[it] > minTenths }
                result[candidate]--
                residual++
            }
        }
        check(result.sum() == targetSum) {
            "Daily load shaping sum ${result.sum()} failed to reconcile with target $targetSum tenths."
        }
        return result
    }
}
