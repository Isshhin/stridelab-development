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

    private fun minutesToCapTenths(minutes: Int, planningPace: Double): Int {
        var cap = floorTenths((minutes * 60.0) / planningPace).coerceAtLeast(1)
        while (cap > 1 && ceil((cap / 10.0) * planningPace / 60.0).toInt() > minutes) {
            cap--
        }
        return cap
    }

    fun shape(
        sessions: List<PlannedSession>,
        volume: Engine15IndependentVolumePlanner.Result,
        request: PlanRequest,
        qualitySessionIds: Set<String>,
        pinnedSessionIds: Set<String> = emptySet(),
        externalHardDates: Set<LocalDate> = emptySet(),
        maxMinutesBySessionId: Map<String, Int> = emptyMap(),
        minQualitySlotKm: Double? = null,
        maxQualitySlotKm: Double? = null
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
        val hardOrLongDates = hardDates + longDates + externalHardDates

        val timeBudgetCapTenths = if (request.timeBudgetEnabled) {
            minutesToCapTenths(request.minutes, request.planningPace)
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
            val easyCapTenths = if (request.userDirectedPlanning) {
                1200
            } else {
                (weekInfo.longRunExposureCapKm ?: volume.athleteBaseline.reportedLongestRunKm?.plus(1.0))
                    ?.let { floorTenths(it * 0.8) }
                    ?: 1200
            }
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
                    val baseMaxTenths = maxOf(minTenths, rawMaxCap, existingMax)
                    val maxTenthsByIndex = IntArray(adjustable.size) { idx ->
                        val maxMin = maxMinutesBySessionId[adjustable[idx].id]
                        if (maxMin != null && request.timeBudgetEnabled) {
                            val sessionCap = minutesToCapTenths(maxMin, request.planningPace)
                            maxOf(minTenths, minOf(baseMaxTenths, sessionCap))
                        } else {
                            baseMaxTenths
                        }
                    }

                    val minTenthsByIndex = IntArray(adjustable.size) { minTenths }
                    if (minQualitySlotKm != null && minQualitySlotKm.isFinite() && minQualitySlotKm > 0.0) {
                        val targetQualityTenths = ceil(minQualitySlotKm * 10.0 - 1e-8).toInt()
                        for (idx in adjustable.indices) {
                            if (roles[adjustable[idx].id] == ShapeRole.QUALITY) {
                                val othersMin = minTenthsByIndex.sum() - minTenthsByIndex[idx]
                                val feasibleMax = (remainingTenths - othersMin).coerceAtLeast(minTenths)
                                minTenthsByIndex[idx] = minOf(targetQualityTenths, maxTenthsByIndex[idx], feasibleMax)
                                    .coerceAtLeast(minTenths)
                            }
                        }
                    }

                    if (maxQualitySlotKm != null && maxQualitySlotKm.isFinite() && maxQualitySlotKm > 0.0) {
                        val targetQualityMaxTenths = ceil(maxQualitySlotKm * 10.0 - 1e-8).toInt()
                        val effectiveQualityCap = maxOf(targetQualityMaxTenths, ceil(avgTenths).toInt())
                        val qualityIndices = adjustable.indices.filter { roles[adjustable[it].id] == ShapeRole.QUALITY }
                        if (qualityIndices.isNotEmpty() && adjustable.size > qualityIndices.size) {
                            val preCapMax = maxTenthsByIndex.copyOf()
                            for (qIdx in qualityIndices) {
                                maxTenthsByIndex[qIdx] = effectiveQualityCap.coerceIn(minTenthsByIndex[qIdx], preCapMax[qIdx])
                            }
                            for (idx in adjustable.indices) {
                                if (idx !in qualityIndices) {
                                    val role = roles.getValue(adjustable[idx].id)
                                    val bound = if (isRecoveryRole(role)) effectiveQualityCap - 1 else effectiveQualityCap
                                    maxTenthsByIndex[idx] = minOf(
                                        maxTenthsByIndex[idx],
                                        bound.coerceAtLeast(minTenthsByIndex[idx])
                                    )
                                }
                            }
                            var deficit = remainingTenths - maxTenthsByIndex.sum()
                            if (deficit > 0) {
                                val recoveryIndices = adjustable.indices.filter {
                                    it !in qualityIndices && isRecoveryRole(roles.getValue(adjustable[it].id))
                                }
                                val easyIndices = adjustable.indices.filter {
                                    it !in qualityIndices && !isRecoveryRole(roles.getValue(adjustable[it].id))
                                }
                                for (idx in qualityIndices + easyIndices + recoveryIndices) {
                                    if (deficit <= 0) break
                                    val headroom = (preCapMax[idx] - maxTenthsByIndex[idx]).coerceAtLeast(0)
                                    val add = minOf(deficit, headroom)
                                    maxTenthsByIndex[idx] += add
                                    deficit -= add
                                }
                            }
                        }
                    }

                    val distributed = allocateWeightedTenths(
                        sessions = adjustable,
                        weights = adjustable.map { roles.getValue(it.id).weight },
                        targetSum = remainingTenths,
                        minTenthsByIndex = minTenthsByIndex,
                        maxTenthsByIndex = maxTenthsByIndex
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

                val hrProfile = if (request.runningTargetMode == "HR") request.resolvedHrZoneProfile() else null
                val recHr = Engine15PaceZoneEstimator.hrBoundsForProfile(hrProfile, RunningStepIntent.RECOVERY)
                val easyHr = Engine15PaceZoneEstimator.hrBoundsForProfile(hrProfile, RunningStepIntent.AEROBIC_EASY)
                shapedById[s.id] = when {
                    isRecoveryRole(role) && role != ShapeRole.QUALITY -> {
                        val recoveryReason = buildRecoveryReason(
                            date = s.date,
                            role = role,
                            isRecoveryWeek = isRecoveryWeek,
                            hardDates = hardDates,
                            longDates = longDates,
                            externalHardDates = externalHardDates
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
                                        "Recovery, very easy conversational pace; keep effort low and legs fresh.",
                                        stepIntent = if (hrProfile != null) RunningStepIntent.RECOVERY else null,
                                        targetHrMinBpm = recHr?.first,
                                        targetHrMaxBpm = recHr?.second
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
                            runningPrescription = RunningPrescription(
                                role = "EASY",
                                family = null,
                                segments = listOf(
                                    RunningSegment(
                                        "EASY",
                                        finalMeters,
                                        "Easy, conversational effort; no pace or heart-rate target.",
                                        stepIntent = if (hrProfile != null) RunningStepIntent.AEROBIC_EASY else null,
                                        targetHrMinBpm = easyHr?.first,
                                        targetHrMaxBpm = easyHr?.second
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
                            runningPrescription = RunningPrescription(
                                role = "EASY",
                                family = null,
                                segments = listOf(
                                    RunningSegment(
                                        "EASY",
                                        finalMeters,
                                        "Easy, conversational effort; no pace or heart-rate target.",
                                        stepIntent = if (hrProfile != null) RunningStepIntent.AEROBIC_EASY else null,
                                        targetHrMinBpm = easyHr?.first,
                                        targetHrMaxBpm = easyHr?.second
                                    )
                                )
                            )
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
        longDates: Set<LocalDate>,
        externalHardDates: Set<LocalDate> = emptySet()
    ): String {
        val prefix = if (isRecoveryWeek) "Recovery week. " else "Loading week. "
        val prevWasQuality = date.minusDays(1) in hardDates
        val prevWasLong = date.minusDays(1) in longDates
        val prevWasExternalHard = date.minusDays(1) in externalHardDates
        val nextIsHard = date.plusDays(1) in (hardDates + externalHardDates)
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
            prevWasExternalHard ->
                "Short recovery run after yesterday's lower-body or full-body cross-training session so legs absorb the load."
            role == ShapeRole.RECOVERY_PRE_LONG || role == ShapeRole.RECOVERY_PRE_LONG_CUTBACK ->
                "Short shakeout recovery run ahead of tomorrow's long run so legs arrive fresh."
            else ->
                "Short recovery run balanced within your weekly volume."
        }
        return prefix + detail
    }

    /**
     * Bounded iterative water-filling over weights followed by largest-remainder tenths
     * rounding so every slot lies in [minTenths, maxTenthsByIndex[i]] and the sum matches
     * [targetSum] to exact 0.1 km precision.
     */
    private fun allocateWeightedTenths(
        sessions: List<PlannedSession>,
        weights: List<Double>,
        targetSum: Int,
        minTenthsByIndex: IntArray,
        maxTenthsByIndex: IntArray
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
                val lowViolation = minTenthsByIndex[i] - exact
                val highViolation = exact - maxTenthsByIndex[i]
                if (lowViolation > 1e-9 && lowViolation > worstViolation) {
                    worstIdx = i
                    worstClamp = minTenthsByIndex[i]
                    worstViolation = lowViolation
                } else if (highViolation > 1e-9 && highViolation > worstViolation) {
                    worstIdx = i
                    worstClamp = maxTenthsByIndex[i]
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
                val base = floor(exact + 1e-9).toInt().coerceIn(minTenthsByIndex[i], maxTenthsByIndex[i])
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
                val candidate = plusOrder.firstOrNull { result[it] < maxTenthsByIndex[it] }
                    ?: (0 until n).first { result[it] < maxTenthsByIndex[it] }
                result[candidate]++
                residual--
            }
            while (residual < 0) {
                val candidate = minusOrder.firstOrNull { result[it] > minTenthsByIndex[it] }
                    ?: (0 until n).first { result[it] > minTenthsByIndex[it] }
                result[candidate]--
                residual++
            }
        } else {
            var residual = targetSum - result.sum()
            while (residual > 0) {
                val candidate = (0 until n).first { result[it] < maxTenthsByIndex[it] }
                result[candidate]++
                residual--
            }
            while (residual < 0) {
                val candidate = (0 until n).first { result[it] > minTenthsByIndex[it] }
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
