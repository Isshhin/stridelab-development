package io.stridelab.privatealpha

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Pure, reversible per-workout difficulty scaler (`-2` Much easier .. `0` Coached .. `+2` Much harder).
 *
 * Always derives the adjusted workout deterministically from the session's immutable
 * [PlannedSession.difficultyBaseline], guaranteeing zero rounding drift across repeated taps
 * and exact restoration on Reset (`targetStep = 0`).
 */
internal object Engine15WorkoutDifficultyAdjuster {

    fun labelFor(step: Int): String = when (step.coerceIn(-2, 2)) {
        -2 -> "Much easier"
        -1 -> "Easier"
        0 -> "Coached"
        1 -> "Harder"
        else -> "Much harder"
    }

    fun changeSummary(session: PlannedSession): String? {
        if (session.difficultyStep == 0) return null
        val base = session.baselineSession()
        return when (base.discipline) {
            "run" -> {
                val orig = base.distanceKm?.let { String.format(Locale.US, "%.1f km", it) }
                    ?: "${base.minutes} min"
                val cur = session.distanceKm?.let { String.format(Locale.US, "%.1f km", it) }
                    ?: "${session.minutes} min"
                when {
                    base.quality && session.difficultyStep == -2 ->
                        "Coached target: ${base.title} ($orig) · Switched to easy recovery run ($cur)"
                    base.quality -> {
                        val origReps = base.runningPrescription?.mixedSegments?.count { it.purpose == "WORK" }
                        val curReps = session.runningPrescription?.mixedSegments?.count { it.purpose == "WORK" }
                        val detail = if (origReps != null && curReps != null && origReps != curReps) {
                            "$origReps → $curReps reps · $orig → $cur"
                        } else {
                            "$orig (${base.minutes} min) → $cur (${session.minutes} min)"
                        }
                        "Coached target: $detail"
                    }
                    else -> "Coached target: $orig (${base.minutes} min) → $cur (${session.minutes} min)"
                }
            }
            "str", "cal" -> {
                val baseFirst = base.exercises.firstOrNull()
                val curFirst = session.exercises.firstOrNull()
                if (baseFirst != null && curFirst != null) {
                    val bUnit = baseFirst.reps?.let { "${baseFirst.sets}×$it" }
                        ?: "${baseFirst.sets}×${baseFirst.seconds}s"
                    val cUnit = curFirst.reps?.let { "${curFirst.sets}×$it" }
                        ?: "${curFirst.sets}×${curFirst.seconds}s"
                    "Coached target: $bUnit (${base.minutes} min) → $cUnit (${session.minutes} min)"
                } else {
                    "Coached target: ${base.minutes} min → ${session.minutes} min"
                }
            }
            else -> "Coached target: ${base.minutes} min → ${session.minutes} min"
        }
    }

    fun matchesAdjusted(session: PlannedSession, request: PlanRequest): Boolean {
        if (session.difficultyStep == 0) return session.difficultyBaseline == null
        if (session.difficultyBaseline == null) return false
        val expected = adjust(session.baselineSession(), request, session.difficultyStep)
        return session.title == expected.title &&
            session.minutes == expected.minutes &&
            session.distanceKm == expected.distanceKm &&
            session.kind == expected.kind &&
            session.quality == expected.quality &&
            session.exercises == expected.exercises &&
            session.runningPrescription == expected.runningPrescription &&
            session.difficultyBaseline == expected.difficultyBaseline
    }

    fun adjust(session: PlannedSession, request: PlanRequest, targetStep: Int): PlannedSession {
        require(targetStep in -2..2) { "Difficulty step must be between -2 and +2." }
        val base = session.baselineSession()
        if (targetStep == 0) return base
        val baseline = SessionDifficultyBaseline(
            title = base.title,
            minutes = base.minutes,
            distanceKm = base.distanceKm,
            kind = base.kind,
            quality = base.quality,
            exercises = base.exercises,
            steps = base.steps,
            reason = base.reason,
            runningPrescription = base.runningPrescription
        )
        return when {
            base.discipline == "run" && base.quality -> adjustQualityRun(base, request, targetStep, baseline)
            base.discipline == "run" -> adjustEasyOrLongRun(base, request, targetStep, baseline)
            base.discipline in setOf("str", "cal") -> adjustResistance(base, request, targetStep, baseline)
            base.discipline == "cond" -> adjustConditioning(base, request, targetStep, baseline)
            else -> base.copy(difficultyStep = targetStep, difficultyBaseline = baseline)
        }
    }

    private fun adjustQualityRun(
        base: PlannedSession,
        request: PlanRequest,
        targetStep: Int,
        baseline: SessionDifficultyBaseline
    ): PlannedSession {
        val pace = request.planningPace.coerceAtLeast(180.0)
        if (targetStep == -2) {
            val baseKm = base.distanceKm
                ?: base.runningPrescription?.estimatedDistanceKm
                ?: (base.minutes * 60.0 / pace)
            val maxKm = if (request.timeBudgetEnabled) maxOf(2.0, timeBudgetMaxKm(request)) else 20.0
            val newKm = roundTenths((baseKm * 0.75).coerceIn(minOf(2.0, baseKm), minOf(baseKm, maxKm)))
            val meters = (newKm * 1000.0).roundToInt().coerceIn(100, 120000)
            val finalKm = meters / 1000.0
            val rawMinutes = ceil(finalKm * pace / 60.0).toInt().coerceAtLeast(10)
            val newMinutes = if (request.timeBudgetEnabled) minOf(rawMinutes, request.minutes) else rawMinutes
            return base.copy(
                title = "Recovery run (eased)",
                minutes = newMinutes.coerceAtLeast(1),
                distanceKm = finalKm,
                kind = "easy",
                quality = false,
                steps = listOf("${formatMetersAsKm(meters)} at easy recovery effort (eased from ${base.title})"),
                reason = appendNote(
                    base.reason,
                    "Adjusted to ${labelFor(targetStep)}: converted structured quality workout to an easy recovery run."
                ),
                runningPrescription = RunningPrescription(
                    role = "RECOVERY",
                    family = null,
                    segments = listOf(
                        RunningSegment(
                            "RECOVERY",
                            meters,
                            "Recovery, very easy conversational pace (eased from ${base.title})."
                        )
                    )
                ),
                difficultyStep = targetStep,
                difficultyBaseline = baseline
            )
        }

        val rx = base.runningPrescription ?: return adjustEasyOrLongRun(base, request, targetStep, baseline)
        val distScale = when (targetStep) {
            -1 -> 0.90
            1 -> 1.10
            else -> 1.18
        }

        val mixed = rx.mixedSegments
        if (mixed != null) {
            val hasWarmUp = mixed.firstOrNull()?.purpose == "WARM_UP" && mixed.size >= 2
            val hasCoolDown = mixed.lastOrNull()?.purpose == "COOL_DOWN" && mixed.size >= (if (hasWarmUp) 3 else 2)
            val warmUp = if (hasWarmUp) mixed.first() else null
            val coolDown = if (hasCoolDown) mixed.last() else null
            val interior = mixed.subList(if (hasWarmUp) 1 else 0, mixed.size - if (hasCoolDown) 1 else 0)
            val workSteps = interior.filter { it.purpose == "WORK" }

            val newInterior: List<RunningStepV2> = if (workSteps.size >= 2) {
                val sampleWork = workSteps.first()
                val sampleRec = interior.firstOrNull { it.purpose == "RECOVERY" }
                if (workSteps.size == 2 && targetStep < 0) {
                    interior.map { step ->
                        if (step.purpose == "WORK" && step.seconds != null) {
                            val sec = (((step.seconds * 0.80) / 30.0).roundToInt() * 30).coerceIn(60, 14400)
                            step.copy(seconds = sec, distanceEquivalentMeters = null)
                        } else {
                            step.copy(distanceEquivalentMeters = null)
                        }
                    }
                } else {
                    val deltaReps = when (targetStep) {
                        -1 -> -1
                        1 -> 1
                        else -> 2
                    }
                    var targetReps = (workSteps.size + deltaReps).coerceIn(2, 12)
                    if (request.timeBudgetEnabled && targetStep > 0) {
                        val fixedSec = (warmUp?.seconds ?: 0) + (coolDown?.seconds ?: 0)
                        val workSec = sampleWork.seconds ?: 0
                        val recSec = sampleRec?.seconds ?: 0
                        while (targetReps > workSteps.size &&
                            fixedSec + targetReps * workSec + (targetReps - 1) * recSec > request.minutes * 60
                        ) {
                            targetReps--
                        }
                    }
                    buildList {
                        repeat(targetReps) { idx ->
                            add(sampleWork.copy(distanceEquivalentMeters = null))
                            if (idx < targetReps - 1 && sampleRec != null) {
                                add(sampleRec.copy(distanceEquivalentMeters = null))
                            }
                        }
                    }
                }
            } else {
                val mult = when (targetStep) {
                    -1 -> 0.80
                    1 -> 1.20
                    else -> 1.35
                }
                val oldWorkSec = workSteps.singleOrNull()?.seconds ?: 600
                var newWorkSec = (((oldWorkSec * mult) / 60.0).roundToInt() * 60).coerceIn(180, 3000)
                if (targetStep < 0 && newWorkSec >= oldWorkSec && oldWorkSec > 180) {
                    newWorkSec = (oldWorkSec - 120).coerceAtLeast(180)
                } else if (targetStep > 0 && newWorkSec <= oldWorkSec && oldWorkSec < 3000) {
                    newWorkSec = (oldWorkSec + 120).coerceAtMost(3000)
                }
                if (request.timeBudgetEnabled && targetStep > 0) {
                    val maxWorkSec = (request.minutes * 60 - (warmUp?.seconds ?: 0) - (coolDown?.seconds ?: 0))
                        .coerceAtLeast(oldWorkSec)
                    newWorkSec = newWorkSec.coerceAtMost(maxWorkSec)
                }
                interior.map { step ->
                    if (step.purpose == "WORK" && step.seconds != null) {
                        step.copy(seconds = newWorkSec, distanceEquivalentMeters = null)
                    } else {
                        step.copy(distanceEquivalentMeters = null)
                    }
                }
            }

            val assembledTemplate = buildList {
                warmUp?.let { add(it.copy(distanceEquivalentMeters = null)) }
                addAll(newInterior)
                coolDown?.let { add(it.copy(distanceEquivalentMeters = null)) }
            }

            if (rx.reconciledDistanceMeters != null) {
                val baseKm = base.distanceKm ?: (rx.reconciledDistanceMeters / 1000.0)
                val maxKm = if (request.timeBudgetEnabled && targetStep > 0) {
                    maxOf(baseKm, minOf(30.0, timeBudgetMaxKm(request)))
                } else 30.0
                val rawNewKm = roundTenths((baseKm * distScale).coerceIn(minOf(baseKm, 3.0), maxKm))
                val fixedMeters = assembledTemplate.sumOf { it.meters ?: 0 }
                val timedCount = assembledTemplate.count { it.seconds != null }
                val newTotalMeters = maxOf((rawNewKm * 1000.0).roundToInt(), fixedMeters + timedCount)

                val workSpeeds = mixed.filter { it.purpose == "WORK" && it.seconds != null && it.distanceEquivalentMeters != null }
                    .map { it.distanceEquivalentMeters!!.toDouble() / it.seconds!! }
                val easySpeeds = mixed.filter { it.purpose != "WORK" && it.seconds != null && it.distanceEquivalentMeters != null }
                    .map { it.distanceEquivalentMeters!!.toDouble() / it.seconds!! }
                val defaultEasySpeed = 1000.0 / pace
                val avgEasySpeed = easySpeeds.takeIf { it.isNotEmpty() }?.average() ?: defaultEasySpeed
                val avgWorkSpeed = workSpeeds.takeIf { it.isNotEmpty() }?.average() ?: (avgEasySpeed * 1.20)

                val timedIndices = assembledTemplate.indices.filter { assembledTemplate[it].seconds != null }
                val rawWeights = timedIndices.map { idx ->
                    val st = assembledTemplate[idx]
                    val speed = if (st.purpose == "WORK") avgWorkSpeed else avgEasySpeed
                    st.seconds!! * speed
                }
                val allocatedTimed = distributePositiveInts(
                    targetSum = newTotalMeters - fixedMeters,
                    weights = rawWeights
                )
                val eqByIndex = HashMap<Int, Int>(timedIndices.size)
                timedIndices.forEachIndexed { pos, stepIdx ->
                    eqByIndex[stepIdx] = allocatedTimed[pos]
                }
                val finalSteps = assembledTemplate.mapIndexed { idx, step ->
                    if (step.meters != null) step
                    else step.copy(distanceEquivalentMeters = eqByIndex.getValue(idx))
                }
                val finalRx = RunningPrescription(
                    role = "QUALITY",
                    family = rx.family,
                    segments = emptyList(),
                    mixedSegments = finalSteps,
                    reconciledDistanceMeters = newTotalMeters
                )
                val newMinutes = (
                    finalSteps.sumOf { it.seconds ?: 0 } / 60 +
                        ceil(finalSteps.sumOf { it.meters ?: 0 } * pace / 60_000.0).toInt()
                    ).coerceAtLeast(10)
                return base.copy(
                    minutes = newMinutes,
                    distanceKm = newTotalMeters / 1000.0,
                    steps = finalSteps.map { step ->
                        val timeText = step.seconds?.let { s ->
                            if (s % 60 == 0) "${s / 60} min" else "$s s"
                        } ?: formatMetersAsKm(step.meters ?: 0)
                        val eqText = formatMetersAsKm(step.meters ?: step.distanceEquivalentMeters ?: 0)
                        "$timeText ${step.purpose.lowercase().replace('_', ' ')} · $eqText · ${step.effort}"
                    },
                    reason = appendNote(
                        base.reason,
                        "Adjusted to ${labelFor(targetStep)}: scaled quality volume and reps while preserving progression structure."
                    ),
                    runningPrescription = finalRx,
                    difficultyStep = targetStep,
                    difficultyBaseline = baseline
                )
            } else {
                val baseEst = rx.estimatedDistanceKm ?: 5.0
                val newEst = roundTenths((baseEst * distScale).coerceIn(1.5, 35.0))
                val finalRx = RunningPrescription(
                    role = "QUALITY",
                    family = rx.family,
                    segments = emptyList(),
                    mixedSegments = assembledTemplate,
                    estimatedDistanceKm = newEst
                )
                val newMinutes = (assembledTemplate.sumOf { it.seconds ?: 0 } / 60).coerceAtLeast(10)
                return base.copy(
                    minutes = newMinutes,
                    distanceKm = null,
                    steps = assembledTemplate.map { step ->
                        val extent = step.seconds?.let { "${it / 60} min" } ?: formatMetersAsKm(step.meters ?: 0)
                        "$extent ${step.purpose.lowercase().replace('_', ' ')} · ${step.effort}"
                    },
                    reason = appendNote(
                        base.reason,
                        "Adjusted to ${labelFor(targetStep)}: scaled quality work steps."
                    ),
                    runningPrescription = finalRx,
                    difficultyStep = targetStep,
                    difficultyBaseline = baseline
                )
            }
        }

        // Distance-based quality segments (e.g., user-defined quality workouts)
        val baseKm = base.distanceKm ?: (rx.segments.sumOf { it.meters } / 1000.0)
        val maxKm = if (request.timeBudgetEnabled && targetStep > 0) {
            maxOf(baseKm, minOf(30.0, timeBudgetMaxKm(request)))
        } else 30.0
        val newKm = roundTenths((baseKm * distScale).coerceIn(minOf(baseKm, 3.0), maxKm))
        val newTotalMeters = maxOf((newKm * 1000.0).roundToInt(), rx.segments.size * 100)
        val workMultiplier = when (targetStep) {
            -1 -> 0.85
            1 -> 1.18
            else -> 1.30
        }
        val segWeights = rx.segments.map { seg ->
            seg.meters * if (seg.purpose == "WORK") workMultiplier else 1.0
        }
        val scaledMeters = distributePositiveInts(newTotalMeters, segWeights, minEach = 100)
        val newSegments = rx.segments.mapIndexed { idx, seg ->
            seg.copy(meters = scaledMeters[idx])
        }
        val finalMinutes = ceil(newTotalMeters / 1000.0 * pace / 60.0).toInt().coerceAtLeast(10)
        return base.copy(
            minutes = finalMinutes,
            distanceKm = newTotalMeters / 1000.0,
            steps = newSegments.mapIndexed { i, step ->
                "${i + 1}. ${formatMetersAsKm(step.meters)} ${step.purpose.lowercase().replace('_', ' ')} — ${step.effort}."
            },
            reason = appendNote(
                base.reason,
                "Adjusted to ${labelFor(targetStep)}: scaled workout distances."
            ),
            runningPrescription = rx.copy(segments = newSegments),
            difficultyStep = targetStep,
            difficultyBaseline = baseline
        )
    }

    private fun adjustEasyOrLongRun(
        base: PlannedSession,
        request: PlanRequest,
        targetStep: Int,
        baseline: SessionDifficultyBaseline
    ): PlannedSession {
        if (base.distanceKm == null) {
            return adjustTimeOnlyRun(base, request, targetStep, baseline)
        }
        val pace = request.planningPace.coerceAtLeast(180.0)
        val mult = when (targetStep) {
            -2 -> 0.70
            -1 -> 0.85
            1 -> 1.15
            else -> 1.30
        }
        val baseKm = base.distanceKm ?: (base.minutes * 60.0 / pace)
        val hardCapKm = if (base.kind == "long") 42.2 else 25.0
        val maxKm = if (request.timeBudgetEnabled && targetStep > 0) {
            maxOf(baseKm, minOf(hardCapKm, timeBudgetMaxKm(request)))
        } else {
            maxOf(baseKm, hardCapKm)
        }
        var newKm = if (targetStep < 0) {
            roundTenths((baseKm * mult).coerceAtLeast(minOf(2.0, baseKm)))
        } else {
            roundTenths((baseKm * mult).coerceAtMost(maxKm))
        }
        if (targetStep < 0 && newKm >= baseKm && baseKm > 2.0) {
            newKm = roundTenths((baseKm - 0.1).coerceAtLeast(2.0))
        } else if (targetStep > 0 && newKm <= baseKm && maxKm >= baseKm + 0.1) {
            newKm = roundTenths((baseKm + 0.1).coerceAtMost(maxKm))
        }

        val meters = (newKm * 1000.0).roundToInt().coerceIn(100, 120000)
        val finalKm = meters / 1000.0
        val rawMinutes = ceil(finalKm * pace / 60.0).toInt().coerceAtLeast(5)
        val newMinutes = if (request.timeBudgetEnabled && targetStep > 0) {
            minOf(rawMinutes, request.minutes.coerceAtLeast(base.minutes))
        } else rawMinutes

        val rx = base.runningPrescription
        val newRx = if (rx != null && rx.mixedSegments == null && rx.segments.isNotEmpty()) {
            val scaled = distributePositiveInts(meters, rx.segments.map { it.meters.toDouble() }, minEach = 100)
            rx.copy(segments = rx.segments.mapIndexed { idx, seg -> seg.copy(meters = scaled[idx]) })
        } else if (rx != null && rx.reconciledDistanceMeters != null && rx.mixedSegments != null) {
            val fixedMeters = rx.mixedSegments.sumOf { it.meters ?: 0 }
            val timedIndices = rx.mixedSegments.indices.filter { rx.mixedSegments[it].seconds != null }
            val scaled = distributePositiveInts(
                (meters - fixedMeters).coerceAtLeast(timedIndices.size),
                timedIndices.map { (rx.mixedSegments[it].distanceEquivalentMeters ?: 100).toDouble() }
            )
            val eqByIdx = timedIndices.withIndex().associate { (p, idx) -> idx to scaled[p] }
            rx.copy(
                mixedSegments = rx.mixedSegments.mapIndexed { idx, step ->
                    if (step.meters != null) step else step.copy(distanceEquivalentMeters = eqByIdx.getValue(idx))
                },
                reconciledDistanceMeters = meters
            )
        } else {
            rx
        }

        val effortText = when {
            base.kind == "long" -> "easy, conversational endurance effort"
            newRx?.role == "RECOVERY" -> "very easy recovery pace"
            else -> "easy, conversational effort"
        }
        return base.copy(
            minutes = newMinutes,
            distanceKm = finalKm,
            steps = listOf("${formatMetersAsKm(meters)} at $effortText"),
            reason = appendNote(
                base.reason,
                "Adjusted to ${labelFor(targetStep)}: scaled distance from ${formatMetersAsKm((baseKm * 1000).roundToInt())} to ${formatMetersAsKm(meters)}."
            ),
            runningPrescription = newRx,
            difficultyStep = targetStep,
            difficultyBaseline = baseline
        )
    }

    private fun adjustTimeOnlyRun(
        base: PlannedSession,
        request: PlanRequest,
        targetStep: Int,
        baseline: SessionDifficultyBaseline
    ): PlannedSession {
        val mult = when (targetStep) {
            -2 -> 0.70
            -1 -> 0.85
            1 -> 1.15
            else -> 1.30
        }
        val maxMin = if (request.timeBudgetEnabled && targetStep > 0) {
            maxOf(base.minutes, request.minutes)
        } else 120
        var newMinutes = (base.minutes * mult).roundToInt().coerceIn(minOf(10, base.minutes), minOf(120, maxMin))
        if (targetStep < 0 && newMinutes >= base.minutes && base.minutes > 10) {
            newMinutes = (base.minutes - 2).coerceAtLeast(10)
        } else if (targetStep > 0 && newMinutes <= base.minutes && maxMin >= base.minutes + 2) {
            newMinutes = (base.minutes + 2).coerceAtMost(maxMin)
        }
        val rx = base.runningPrescription
        val newRx = if (rx != null && rx.mixedSegments != null && rx.mixedSegments.isNotEmpty()) {
            val totalSeconds = newMinutes * 60
            val warmIdx = rx.mixedSegments.indexOfFirst { it.purpose == "WARM_UP" }
            val coolIdx = rx.mixedSegments.indexOfLast { it.purpose == "COOL_DOWN" }
            val weights = rx.mixedSegments.mapIndexed { idx, step ->
                val baseSec = (step.seconds ?: 60).toDouble()
                if (idx == warmIdx || idx == coolIdx) baseSec * 0.65 else baseSec
            }
            val scaledSec = distributePositiveInts(totalSeconds, weights, minEach = 30)
            val updatedMixed = rx.mixedSegments.mapIndexed { idx, step ->
                step.copy(seconds = scaledSec[idx].coerceIn(30, 14400))
            }
            rx.copy(mixedSegments = updatedMixed)
        } else rx
        val updatedSteps = newRx?.mixedSegments?.mapIndexed { idx, step ->
            val sec = step.seconds ?: 60
            val durText = if (sec % 60 == 0) "${sec / 60} min" else "$sec s"
            "${idx + 1}. $durText ${step.purpose.lowercase().replace('_', ' ')} — ${step.effort}"
        } ?: listOf("$newMinutes min time-based run/walk at easy conversational effort")
        return base.copy(
            minutes = newMinutes,
            distanceKm = null,
            steps = updatedSteps,
            reason = appendNote(
                base.reason,
                "Adjusted to ${labelFor(targetStep)}: scaled time-based duration from ${base.minutes} min to $newMinutes min."
            ),
            runningPrescription = newRx,
            difficultyStep = targetStep,
            difficultyBaseline = baseline
        )
    }

    private fun adjustResistance(
        base: PlannedSession,
        request: PlanRequest,
        targetStep: Int,
        baseline: SessionDifficultyBaseline
    ): PlannedSession {
        val scaledExercises = base.exercises.map { ex ->
            val newSets: Int
            val newReps: Int?
            val newSeconds: Int?
            when (targetStep) {
                -2 -> {
                    newSets = (ex.sets - 1).coerceAtLeast(1)
                    newReps = ex.reps?.let { r -> (r * 0.75).roundToInt().coerceIn(minOf(r, 4), 50) }
                    newSeconds = ex.seconds?.let { s -> (s * 0.70).roundToInt().coerceIn(minOf(s, 8), 180) }
                }
                -1 -> {
                    if (ex.sets >= 3) {
                        newSets = ex.sets - 1
                        newReps = ex.reps
                        newSeconds = ex.seconds
                    } else {
                        newSets = ex.sets
                        newReps = ex.reps?.let { r -> (r * 0.85).roundToInt().coerceAtMost((r - 1).coerceAtLeast(1)).coerceIn(minOf(r, 4), 50) }
                        newSeconds = ex.seconds?.let { s -> (s * 0.85).roundToInt().coerceAtMost((s - 2).coerceAtLeast(5)).coerceIn(minOf(s, 8), 180) }
                    }
                }
                1 -> {
                    if (ex.sets == 1) {
                        newSets = 2
                        newReps = ex.reps
                        newSeconds = ex.seconds
                    } else {
                        newSets = ex.sets
                        newReps = ex.reps?.let { r -> (r * 1.15).roundToInt().coerceAtLeast(r + 1).coerceIn(4, 50) }
                        newSeconds = ex.seconds?.let { s -> (s * 1.20).roundToInt().coerceAtLeast(s + 5).coerceIn(15, 180) }
                    }
                }
                else -> {
                    newSets = (ex.sets + 1).coerceIn(1, 5)
                    newReps = ex.reps?.let { r -> (r * 1.15).roundToInt().coerceAtLeast(r + 1).coerceIn(4, 50) }
                    newSeconds = ex.seconds?.let { s -> (s * 1.20).roundToInt().coerceAtLeast(s + 5).coerceIn(15, 180) }
                }
            }
            val rirCue = when (targetStep) {
                -2 -> "Target easy technique effort (4+ reps in reserve; lighten load if needed)."
                -1 -> "Target controlled moderate effort (3 reps in reserve)."
                1 -> "Target challenging effort (1–2 reps in reserve with clean form)."
                else -> "Target progressive overload effort (1 rep in reserve; add a small load increment or controlled pause if form is crisp)."
            }
            val updatedCue = if (ex.cue.isBlank()) rirCue else "${ex.cue.trimEnd()} $rirCue"
            ex.copy(sets = newSets, reps = newReps, seconds = newSeconds, cue = updatedCue)
        }

        val workMinutes = scaledExercises.sumOf { ex ->
            val tut = ex.seconds ?: ((ex.reps ?: 0) * 3)
            (ex.sets * tut + (ex.sets - 1) * ex.rest) / 60.0 + 1.0
        }
        val newMinutes = ceil(8.0 + workMinutes).toInt().coerceIn(10, 180)
        return base.copy(
            minutes = newMinutes,
            exercises = scaledExercises,
            reason = appendNote(
                base.reason,
                "Adjusted to ${labelFor(targetStep)}: scaled sets and repetition/hold volume."
            ),
            difficultyStep = targetStep,
            difficultyBaseline = baseline
        )
    }

    private fun adjustConditioning(
        base: PlannedSession,
        request: PlanRequest,
        targetStep: Int,
        baseline: SessionDifficultyBaseline
    ): PlannedSession {
        val mult = when (targetStep) {
            -2 -> 0.70
            -1 -> 0.85
            1 -> 1.15
            else -> 1.30
        }
        val maxMin = if (request.timeBudgetEnabled && targetStep > 0) {
            maxOf(base.minutes, request.minutes)
        } else 90
        var newMinutes = (base.minutes * mult).roundToInt().coerceIn(minOf(10, base.minutes), minOf(90, maxMin))
        if (targetStep < 0 && newMinutes >= base.minutes && base.minutes > 10) {
            newMinutes = (base.minutes - 2).coerceAtLeast(10)
        } else if (targetStep > 0 && newMinutes <= base.minutes && maxMin >= base.minutes + 2) {
            newMinutes = (base.minutes + 2).coerceAtMost(maxMin)
        }
        val mainMinutes = (newMinutes - 10).coerceAtLeast(2)
        val warmupMinutes = if (newMinutes >= 12) 5 else 2
        val cooldownMinutes = (newMinutes - warmupMinutes - mainMinutes).coerceAtLeast(1)
        val adjustedExercises = base.exercises.map { ex ->
            ex.copy(seconds = newMinutes * 60)
        }
        return base.copy(
            minutes = newMinutes,
            exercises = adjustedExercises,
            steps = listOf(
                "$warmupMinutes min easy walking or marching",
                "$mainMinutes min comfortable low-impact conditioning; take breaks to stay conversational",
                "$cooldownMinutes min easy walking or marching"
            ),
            reason = appendNote(
                base.reason,
                "Adjusted to ${labelFor(targetStep)}: scaled total conditioning duration to $newMinutes min."
            ),
            difficultyStep = targetStep,
            difficultyBaseline = baseline
        )
    }

    private fun distributePositiveInts(
        targetSum: Int,
        weights: List<Double>,
        minEach: Int = 1
    ): IntArray {
        val n = weights.size
        require(n > 0 && targetSum >= n * minEach)
        val result = IntArray(n) { minEach }
        val pool = targetSum - n * minEach
        if (pool == 0) return result
        val weightSum = weights.sum().coerceAtLeast(1e-9)
        val fractions = DoubleArray(n)
        var assigned = 0
        for (i in 0 until n) {
            val exact = pool * (weights[i] / weightSum)
            val whole = floor(exact).toInt().coerceAtMost(120000 - result[i])
            result[i] += whole
            fractions[i] = exact - whole
            assigned += whole
        }
        var leftover = pool - assigned
        val order = (0 until n).sortedWith(compareByDescending<Int> { fractions[it] }.thenByDescending { weights[it] })
        var cursor = 0
        while (leftover > 0) {
            val idx = order[cursor % n]
            if (result[idx] < 120000) {
                result[idx]++
                leftover--
            }
            cursor++
        }
        return result
    }

    private fun timeBudgetMaxKm(request: PlanRequest): Double {
        var capTenths = floor((request.minutes * 60.0) / request.planningPace.coerceAtLeast(180.0) * 10.0 + 1e-8)
            .toInt().coerceAtLeast(10)
        while (capTenths > 10 && ceil((capTenths / 10.0) * request.planningPace / 60.0).toInt() > request.minutes) {
            capTenths--
        }
        return capTenths / 10.0
    }

    private fun roundTenths(km: Double): Double = (km * 10.0).roundToInt() / 10.0

    private fun formatMetersAsKm(meters: Int): String {
        val km = meters / 1000.0
        return if (km < 1.0 && meters % 100 != 0) {
            String.format(Locale.US, "%.2f km", km)
        } else {
            String.format(Locale.US, "%.1f km", km)
        }
    }

    private fun appendNote(baseReason: String, note: String): String =
        if (baseReason.isBlank()) note else "${baseReason.trimEnd()} $note"
}
