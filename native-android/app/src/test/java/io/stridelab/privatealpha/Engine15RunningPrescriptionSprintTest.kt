package io.stridelab.privatealpha

import com.garmin.fit.Decode
import com.garmin.fit.MesgListener
import com.garmin.fit.MesgNum
import com.garmin.fit.Sport
import com.garmin.fit.WktStepDuration
import com.garmin.fit.WktStepTarget
import com.garmin.fit.WorkoutMesg
import com.garmin.fit.WorkoutStepMesg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

class Engine15RunningPrescriptionSprintTest {
    private val now = Instant.parse("2026-11-21T12:00:00Z")
    private val start = LocalDate.parse("2026-11-23")
    private val owner = "running-prescription-sprint"
    private val benchmark = PlanRequest(
        goal = "Improve 10K", start = start, weeks = 12, experience = 2,
        days = setOf(1, 2, 3, 4, 5, 6), runDays = 6, longDay = 6,
        qualityDays = setOf(2, 4), qualityCount = 2, recentQualitySessionsPerWeek = 2,
        weeklyKm = 68.0, targetWeeklyKm = 75.0, longestKm = 18.0, planningPace = 310.0,
        recentMileageBasis = "SELF_REPORTED_AVERAGE", timeBudgetEnabled = false,
        benchmarkDistanceMeters = 10_000, benchmarkDurationSeconds = 2_400
    )

    private fun doseEvidence(vararg daysAgo: Long) = daysAgo.map { age ->
        Engine15DetectedWorkout(
            activityId = "threshold-$age", mergedSourceIds = listOf("threshold-$age"),
            performedOn = start.minusDays(age), kind = ActivityWorkoutKind.QUALITY_RUN,
            qualitySubtype = ActivityQualitySubtype.THRESHOLD,
            source = Engine15DetectionSource.LAP_STRUCTURE_HEURISTIC,
            confidence = Engine15DetectionConfidence.HEURISTIC_CANDIDATE,
            underlyingAutomaticKind = ActivityWorkoutKind.QUALITY_RUN,
            candidateWorkSegmentsSeconds = listOf(360, 360, 360, 360),
            evidenceNotes = listOf("Synthetic observed interval durations")
        )
    }

    @Test fun equalMileageDoesNotEraseExperienceDifferencesInQualityEntry() {
        fun anchor(exp: Int) = Engine15QualityProgressionPolicy.computeAdaptiveExposureAnchorSeconds(
            benchmark.copy(experience = exp), IndependentQualityFamily.THRESHOLD,
            detections = doseEvidence(5, 12, 19), now = now
        )
        assertTrue("Advanced entry should exceed intermediate entry for the same benchmark and history", anchor(2) > anchor(1))
    }

    @Test fun staleQualityDoesNotRaiseTheEntryDose() {
        fun anchor(detections: List<Engine15DetectedWorkout>) =
            Engine15QualityProgressionPolicy.computeAdaptiveExposureAnchorSeconds(
                benchmark, IndependentQualityFamily.THRESHOLD, detections = detections, now = now
            )
        assertEquals(anchor(emptyList()), anchor(doseEvidence(70, 77, 84)))
        assertTrue(anchor(doseEvidence(5, 12, 19)) > anchor(emptyList()))
    }

    @Test fun oneRecordingCannotEstablishRepeatExposureAndFutureMileageCannotRaiseTheEntry() {
        val observation = doseEvidence(5).single()
        val duplicate = observation.copy(activityId = "fit-copy")
        fun anchor(request: PlanRequest, detections: List<Engine15DetectedWorkout>) =
            Engine15QualityProgressionPolicy.computeAdaptiveExposureAnchorSeconds(
                request, IndependentQualityFamily.THRESHOLD, detections = detections, now = now
            )
        assertEquals(anchor(benchmark, emptyList()), anchor(benchmark, listOf(observation, duplicate)))
        assertEquals(anchor(benchmark, emptyList()), anchor(benchmark.copy(targetWeeklyKm = 100.0), emptyList()))
    }

    @Test fun advanced10kBenchmarkConservesMileageAndFreshensThroughTaper() {
        val candidate = preview(benchmark)
        val plan = candidate.plan
        val phases = Engine15MacrocyclePeriodization.assignPhases(plan.request, candidate.volume)
        val weeks = validateFullPlan(benchmark, candidate)
        assertEquals(Engine15MacroPhase.entries.toSet(), phases.values.toSet())
        assertEquals(75.0, candidate.volume.weeks.maxOf { it.proposedKm!! }, 1e-6)
        assertTrue(weeks.getValue(11).sumOf { it.distanceKm!! } < weeks.getValue(10).sumOf { it.distanceKm!! })
        assertTrue(weeks.getValue(12).sumOf { it.distanceKm!! } < weeks.getValue(11).sumOf { it.distanceKm!! })
        for ((week, sessions) in weeks) {
            val phase = phases.getValue(week)
            val quality = sessions.filter { it.quality }
            assertEquals("Week $week $phase", when (phase) {
                Engine15MacroPhase.RECOVERY -> 0
                Engine15MacroPhase.TAPER -> if (week == benchmark.weeks) 0 else 1
                else -> 2
            }, quality.size)
            assertTrue(quality.all { it.date.dayOfWeek.value in benchmark.qualityDays })
            assertEquals(6, sessions.single { it.kind == "long" }.date.dayOfWeek.value)
            val weeklyKm = sessions.sumOf { it.distanceKm!! }
            if (quality.size == 2) assertTrue("Loading week $week: $weeklyKm km", weeklyKm in 65.0..75.000001)
            val weeklyWorkMeters = quality.sumOf { it.runningPrescription!!.workMeters }
            assertTrue(weeklyWorkMeters <= weeklyKm * 200)
            println("BENCHMARK | $week | $phase | $weeklyKm | " +
                quality.joinToString(" | ") { s ->
                    "${s.title}: " + s.runningPrescription!!.mixedSegments!!.filter { it.purpose == "WORK" }
                        .joinToString("+") { it.meters?.let { m -> "${m}m" } ?: "${it.seconds}s" }
                } + " | Long ${sessions.single { it.kind == "long" }.distanceKm}")
        }
        val allQuality = plan.sessions.filter { it.quality }.map { it.runningPrescription!! }
        assertTrue(allQuality.mapNotNull { it.archetypeId }.distinct().size >= 6)
        assertTrue(allQuality.any { rx -> rx.mixedSegments!!.any { it.purpose == "WORK" && it.meters != null } })
        assertTrue(allQuality.any { rx -> rx.mixedSegments!!.any { it.purpose == "WORK" && it.seconds != null } })
        assertTrue(weeks.getValue(11).sumOf { it.runningPrescription!!.workMeters } <
            weeks.getValue(10).sumOf { it.runningPrescription!!.workMeters })
    }

    @Test fun beginnerAndIntermediateCompleteTheirOwnFullPlans() {
        val beginner = benchmark.copy(
            experience = 0, weeks = 8, weeklyKm = 22.0, targetWeeklyKm = 26.0, longestKm = 7.5,
            days = setOf(2, 4, 7), longDay = 7, runDays = 3, qualityCount = 1, qualityDays = setOf(2),
            recentQualitySessionsPerWeek = 0, planningPace = 410.0,
            benchmarkDistanceMeters = null, benchmarkDurationSeconds = null
        )
        val intermediate = benchmark.copy(
            experience = 1, weeks = 16, weeklyKm = 45.0, targetWeeklyKm = 52.0, longestKm = 14.0,
            days = setOf(1, 2, 4, 5, 7), longDay = 7, runDays = 5, qualityCount = 1, qualityDays = setOf(2),
            recentQualitySessionsPerWeek = 1, planningPace = 360.0,
            benchmarkDistanceMeters = null, benchmarkDurationSeconds = null
        )
        val novicePlan = preview(beginner, emptyList())
        validateFullPlan(beginner, novicePlan)
        assertTrue(novicePlan.plan.sessions.none { session ->
            session.runningPrescription?.mixedSegments.orEmpty().any { it.stepIntent == RunningStepIntent.VO2_INTERVAL }
        })
        val intermediatePlan = preview(intermediate, emptyList())
        validateFullPlan(intermediate, intermediatePlan)
        assertTrue(intermediatePlan.plan.sessions.filter { it.quality }
            .map { it.runningPrescription!!.workMeters }.distinct().size >= 3)
    }

    @Test fun sameMileagePlansProduceDifferentPrescribedQualityWork() {
        val lessExperienced = benchmark.copy(experience = 1)
        val intermediate = validateFullPlan(lessExperienced, preview(lessExperienced, emptyList()))
        val advanced = validateFullPlan(benchmark, preview(benchmark))
        fun entryWork(weeks: Map<Int, List<PlannedSession>>) =
            weeks.getValue(1).filter { it.quality }.sumOf { it.runningPrescription!!.workMeters }
        assertTrue("Intermediate ${entryWork(intermediate)} m versus advanced ${entryWork(advanced)} m",
            entryWork(intermediate) < entryWork(advanced))
    }

    @Test fun benchmarkStructuredSessionsRoundTripThroughGarminFitDecoder() {
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(
            benchmark, owner, emptyList(), "sprint-export", now, detections = doseEvidence(5, 12, 19)
        )
        assertEquals(preview(benchmark).plan.sessions.map { it.runningPrescription },
            plan.sessions.map { it.runningPrescription })
        for (session in plan.sessions.filter { !it.runningPrescription?.mixedSegments.isNullOrEmpty() }) {
            val rx = session.runningPrescription!!
            val bytes = GarminFitWorkoutEncoder.encode(session.title, rx, now)
            assertTrue(session.title, Decode().checkFileIntegrity(ByteArrayInputStream(bytes)))
            val workout = mutableListOf<WorkoutMesg>()
            val steps = mutableListOf<WorkoutStepMesg>()
            val decoder = Decode()
            decoder.addListener(MesgListener { msg ->
                when (msg.num) {
                    MesgNum.WORKOUT -> workout += WorkoutMesg(msg)
                    MesgNum.WORKOUT_STEP -> steps += WorkoutStepMesg(msg)
                }
            })
            assertTrue(decoder.read(ByteArrayInputStream(bytes)))
            assertEquals(Sport.RUNNING, workout.single().sport)
            assertEquals(steps.size, workout.single().numValidSteps.toInt())
            assertEquals(steps.indices.toList(), steps.map { it.messageIndex })
            assertTrue(steps.none { it.targetType == WktStepTarget.HEART_RATE })
            val expanded = mutableListOf<WorkoutStepMesg>()
            for (step in steps) {
                if (step.durationType == WktStepDuration.REPEAT_UNTIL_STEPS_CMPLT) {
                    val body = steps.subList(step.durationStep.toInt(), step.messageIndex)
                    assertTrue(body.isNotEmpty())
                    repeat(step.targetValue.toInt() - 1) { expanded.addAll(body) }
                } else expanded += step
            }
            assertEquals(rx.mixedSegments!!.map { part ->
                part.meters?.let { WktStepDuration.DISTANCE to it * 100L }
                    ?: (WktStepDuration.TIME to part.seconds!! * 1000L)
            }, expanded.map { it.durationType to it.durationValue.toLong() })
        }
    }

    @Test fun qualityFittingDoesNotTradeAwayBookendsForAnImpossibleBudget() {
        val decision = Engine15IndependentPlanGenerator.previewAutoReconciled(
            benchmark.copy(timeBudgetEnabled = true, minutes = 15), owner, "sprint-time-cap", now
        )
        assertTrue("Expected a deferral, got $decision", decision is Engine15IndependentPlanDecision.Deferred)
    }

    private fun preview(request: PlanRequest, detections: List<Engine15DetectedWorkout> = doseEvidence(5, 12, 19)):
        Engine15IndependentPlanDecision.Candidate {
        val result = Engine15IndependentPlanGenerator.previewAutoReconciled(request, owner, "sprint", now,
            detections = detections)
        check(result is Engine15IndependentPlanDecision.Candidate) { result }
        return result
    }

    private fun validateFullPlan(
        request: PlanRequest,
        candidate: Engine15IndependentPlanDecision.Candidate
    ): Map<Int, List<PlannedSession>> {
        val sessions = candidate.plan.sessions.sortedBy { it.date }
        val stored = PlanSnapshot.decode(JSONObject(candidate.plan.copy(
            policyId = Engine15IndependentPlanGenerator.policyId
        ).encode().toString()), owner)
        Engine15IndependentPlanGenerator.validateSaved(stored)
        val weeks = sessions.groupBy { (ChronoUnit.DAYS.between(start, it.date) / 7).toInt() + 1 }
        assertEquals((1..request.weeks).toList(), weeks.keys.toList())
        val hard = sessions.filter { it.quality || it.kind == "long" }
        assertTrue(hard.zipWithNext().all { (a, b) -> ChronoUnit.DAYS.between(a.date, b.date) >= 2 })
        for ((week, runs) in weeks) {
            assertEquals(request.runDays, runs.size)
            assertEquals(candidate.volume.weeks[week - 1].proposedKm!!, runs.sumOf { it.distanceKm!! }, 1e-6)
            for (session in runs) {
                val rx = session.runningPrescription!!
                assertEquals((session.distanceKm!! * 1000).roundToInt(), rx.meters)
                if (!session.quality) continue
                val steps = rx.mixedSegments!!
                assertTrue(steps.filter { it.purpose == "WARM_UP" }.sumOf { it.seconds!! } >= 480)
                assertTrue(steps.filter { it.purpose == "COOL_DOWN" }.sumOf { it.seconds!! } >= 300)
                assertTrue(rx.workMeters > 0)
            }
        }
        return weeks
    }
}
