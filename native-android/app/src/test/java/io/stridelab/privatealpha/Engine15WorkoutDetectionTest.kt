package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class Engine15WorkoutDetectionTest {
    private val owner = "detection-test-runner"
    private val asOf = Instant.parse("2026-11-20T12:00:00Z")

    private fun runActivity(
        id: String,
        source: String = "garmin",
        startedAt: Instant = Instant.parse("2026-11-18T06:00:00Z"),
        distanceM: Double = 8_000.0,
        durationS: Double = 2_400.0,
        pace: Double = 300.0
    ) = ActivityRecord(
        id = id,
        title = "Morning Run",
        type = "running",
        source = source,
        startedAt = startedAt,
        distanceM = distanceM,
        durationS = durationS,
        movingTimeS = durationS,
        recordedPace = pace,
        averageHr = 152.0,
        elevationM = 40.0
    )

    private fun lap(index: Int, seconds: Double, pace: Double) =
        ActivityLap(index = index, distance = seconds * 1000.0 / pace, seconds = seconds, pace = pace, hr = 155.0)

    @Test fun lapStructureDetectsIntervalsThresholdTempoAndLongRunAsHeuristicCandidates() {
        val intervalRun = runActivity("act-int", startedAt = Instant.parse("2026-11-14T06:00:00Z"))
        val intervalDetail = ActivityDetail(
            activityId = "act-int",
            samples = emptyList(),
            laps = listOf(
                lap(0, 600.0, 340.0), // WU
                lap(1, 120.0, 240.0), // 2m hard
                lap(2, 120.0, 360.0), // 2m easy
                lap(3, 120.0, 242.0), // 2m hard
                lap(4, 120.0, 365.0), // 2m easy
                lap(5, 120.0, 244.0), // 2m hard
                lap(6, 480.0, 345.0)  // CD
            ),
            fetchedAt = 1L
        )
        val thresholdRun = runActivity("act-thr", startedAt = Instant.parse("2026-11-15T06:00:00Z"))
        val thresholdDetail = ActivityDetail(
            activityId = "act-thr",
            samples = emptyList(),
            laps = listOf(
                lap(0, 600.0, 340.0),
                lap(1, 300.0, 260.0), // 5m threshold
                lap(2, 120.0, 355.0),
                lap(3, 300.0, 262.0), // 5m threshold
                lap(4, 120.0, 360.0),
                lap(5, 300.0, 261.0), // 5m threshold
                lap(6, 480.0, 345.0)
            ),
            fetchedAt = 1L
        )
        val tempoRun = runActivity("act-tmp", startedAt = Instant.parse("2026-11-16T06:00:00Z"))
        val tempoDetail = ActivityDetail(
            activityId = "act-tmp",
            samples = emptyList(),
            laps = listOf(
                lap(0, 600.0, 340.0),
                lap(1, 900.0, 275.0), // 15m sustained tempo
                lap(2, 480.0, 345.0)
            ),
            fetchedAt = 1L
        )
        val longRun = runActivity(
            "act-long",
            startedAt = Instant.parse("2026-11-17T06:00:00Z"),
            distanceM = 16_500.0,
            durationS = 5_800.0,
            pace = 351.0
        )

        val activities = listOf(intervalRun, thresholdRun, tempoRun, longRun)
        val detected = Engine15WorkoutDetection.detectAll(
            owner = owner,
            activities = activities,
            allowedActivityIds = activities.map { it.id }.toSet(),
            detailsByActivityId = mapOf(
                "act-int" to intervalDetail,
                "act-thr" to thresholdDetail,
                "act-tmp" to tempoDetail
            ),
            plans = emptyList(),
            userLabels = emptyMap(),
            asOf = asOf
        ).associateBy { it.activityId }

        assertEquals(ActivityWorkoutKind.QUALITY_RUN, detected.getValue("act-int").kind)
        assertEquals(ActivityQualitySubtype.INTERVALS, detected.getValue("act-int").qualitySubtype)
        assertEquals(Engine15DetectionConfidence.HEURISTIC_CANDIDATE, detected.getValue("act-int").confidence)
        assertEquals(listOf(120, 120, 120), detected.getValue("act-int").candidateWorkSegmentsSeconds)

        assertEquals(ActivityWorkoutKind.QUALITY_RUN, detected.getValue("act-thr").kind)
        assertEquals(ActivityQualitySubtype.THRESHOLD, detected.getValue("act-thr").qualitySubtype)
        assertEquals(listOf(300, 300, 300), detected.getValue("act-thr").candidateWorkSegmentsSeconds)

        assertEquals(ActivityWorkoutKind.QUALITY_RUN, detected.getValue("act-tmp").kind)
        assertEquals(ActivityQualitySubtype.TEMPO, detected.getValue("act-tmp").qualitySubtype)
        assertEquals(listOf(900), detected.getValue("act-tmp").candidateWorkSegmentsSeconds)

        assertEquals(ActivityWorkoutKind.LONG_RUN, detected.getValue("act-long").kind)
        assertEquals(Engine15DetectionConfidence.HEURISTIC_CANDIDATE, detected.getValue("act-long").confidence)

        // Unconfirmed heuristic candidates MUST NOT be promoted into IndependentQualitySegmentEvidence
        val evidence = Engine15WorkoutDetection.toQualitySegmentEvidence(owner, detected.values.toList())
        assertTrue(evidence.isEmpty())
    }

    @Test fun userOverrideTakesPrecedenceAndIsReversibleWhileDeduplicatingGarminCopies() {
        val hashedId = "garmin-" + "a".repeat(64)
        val garminOwned = runActivity(hashedId, source = "garmin")
        val garminLegacy = runActivity("garmin-legacy-1", source = "garmin")
        val detail = ActivityDetail(
            activityId = hashedId,
            samples = emptyList(),
            laps = listOf(lap(0, 600.0, 340.0), lap(1, 900.0, 275.0), lap(2, 480.0, 345.0)),
            fetchedAt = 1L
        )
        val activities = listOf(garminOwned, garminLegacy)
        val allowed = activities.map { it.id }.toSet()

        val overridden = Engine15WorkoutDetection.detectAll(
            owner = owner,
            activities = activities,
            allowedActivityIds = allowed,
            detailsByActivityId = mapOf(hashedId to detail),
            plans = emptyList(),
            userLabels = mapOf(hashedId to UserActivityWorkoutLabel(ActivityWorkoutKind.QUALITY_RUN, ActivityQualitySubtype.THRESHOLD)),
            asOf = asOf
        )
        assertEquals(1, overridden.size)
        val item = overridden.single()
        assertEquals(listOf(hashedId, "garmin-legacy-1").sorted(), item.mergedSourceIds)
        assertTrue(item.isUserOverridden)
        assertEquals(ActivityQualitySubtype.THRESHOLD, item.qualitySubtype)
        assertEquals(ActivityQualitySubtype.TEMPO, item.underlyingAutomaticSubtype)

        val evidence = Engine15WorkoutDetection.toQualitySegmentEvidence(owner, overridden)
        assertEquals(1, evidence.size)
        assertEquals(IndependentQualityFamily.THRESHOLD, evidence.single().family)
        assertEquals(IndependentQualityEvidenceOrigin.SELF_REPORTED_SEGMENTS, evidence.single().origin)

        // Removing the override reverts cleanly to automatic detection
        val reverted = Engine15WorkoutDetection.detectAll(
            owner = owner,
            activities = activities,
            allowedActivityIds = allowed,
            detailsByActivityId = mapOf(hashedId to detail),
            plans = emptyList(),
            userLabels = emptyMap(),
            asOf = asOf
        ).single()
        assertFalse(reverted.isUserOverridden)
        assertEquals(ActivityQualitySubtype.TEMPO, reverted.qualitySubtype)
        assertEquals(Engine15DetectionConfidence.HEURISTIC_CANDIDATE, reverted.confidence)
    }

    @Test fun linkedPlanIntentProducesEvidenceAndAmbiguousLinkageFlagsConflict() {
        val run = runActivity("garmin-linked", startedAt = Instant.parse("2026-11-18T06:00:00Z"))
        val prescription = RunningPrescription(
            role = "QUALITY",
            family = "Threshold",
            segments = emptyList(),
            mixedSegments = listOf(
                RunningStepV2("WARM_UP", "Easy", seconds = 600, distanceEquivalentMeters = 1500),
                RunningStepV2("WORK", "Threshold", seconds = 240, distanceEquivalentMeters = 900),
                RunningStepV2("RECOVERY", "Easy", seconds = 120, distanceEquivalentMeters = 300),
                RunningStepV2("WORK", "Threshold", seconds = 240, distanceEquivalentMeters = 900),
                RunningStepV2("COOL_DOWN", "Easy", seconds = 480, distanceEquivalentMeters = 1200)
            ),
            reconciledDistanceMeters = 4800
        )
        val completedSession = PlannedSession(
            id = "sess-1",
            date = LocalDate.parse("2026-11-18"),
            discipline = "run",
            title = "Experimental Threshold",
            minutes = 28,
            distanceKm = 4.8,
            kind = "quality",
            quality = true,
            status = "completed",
            linkedActivityId = "garmin-linked",
            runningPrescription = prescription
        )
        val plan = PlanSnapshot(
            id = "plan-1",
            owner = owner,
            engineVersion = ENGINE15_VERSION,
            policyId = ENGINE15_INDEPENDENT_RUNNING_POLICY,
            createdAt = asOf,
            request = PlanRequest(start = LocalDate.parse("2026-11-16"), weeks = 4),
            sessions = listOf(completedSession),
            explanations = listOf("Plan"),
            status = "approved"
        )

        val linked = Engine15WorkoutDetection.detectAll(
            owner = owner,
            activities = listOf(run),
            allowedActivityIds = setOf("garmin-linked"),
            detailsByActivityId = emptyMap(),
            plans = listOf(plan),
            userLabels = emptyMap(),
            asOf = asOf
        )
        assertEquals(Engine15DetectionSource.LINKED_PLAN_INTENT, linked.single().source)
        assertEquals(Engine15DetectionConfidence.INTENT_LINKED_UNVERIFIED, linked.single().confidence)
        val evidence = Engine15WorkoutDetection.toQualitySegmentEvidence(owner, linked)
        assertEquals(1, evidence.size)
        assertEquals(listOf(240, 240), evidence.single().performedWorkSegmentsSeconds)
        assertEquals(IndependentQualityEvidenceOrigin.DEVICE_SEGMENTS_ASSERTED, evidence.single().origin)

        // Ambiguous linkage (two sessions claiming the same activity) must fail closed
        val duplicatePlan = plan.copy(
            sessions = listOf(completedSession, completedSession.copy(id = "sess-2"))
        )
        val conflicted = Engine15WorkoutDetection.detectAll(
            owner = owner,
            activities = listOf(run),
            allowedActivityIds = setOf("garmin-linked"),
            detailsByActivityId = emptyMap(),
            plans = listOf(duplicatePlan),
            userLabels = emptyMap(),
            asOf = asOf
        )
        assertEquals(Engine15DetectionConfidence.AMBIGUOUS_CONFLICT, conflicted.single().confidence)
        assertEquals("AMBIGUOUS_PLAN_LINKAGE", conflicted.single().warningCode)
        assertTrue(Engine15WorkoutDetection.toQualitySegmentEvidence(owner, conflicted).isEmpty())
    }
}
