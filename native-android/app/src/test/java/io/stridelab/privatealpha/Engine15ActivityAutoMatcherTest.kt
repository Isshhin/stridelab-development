package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class Engine15ActivityAutoMatcherTest {
    private val zone = ZoneOffset.UTC
    private val baseDate = LocalDate.of(2026, 11, 24)

    private fun runSession(
        id: String = "s-1",
        date: LocalDate = baseDate,
        distanceKm: Double? = 10.0,
        minutes: Int = 55,
        quality: Boolean = false,
        linkedActivityId: String? = null,
        status: String = "planned"
    ): PlannedSession {
        val rx = if (distanceKm != null) {
            val meters = (distanceKm * 1000).toInt()
            if (quality) {
                RunningPrescription(
                    role = "QUALITY",
                    family = "Threshold",
                    segments = listOf(
                        RunningSegment("WARM_UP", 2000, "Easy warm-up", RunningStepIntent.WARMUP, 335, 360),
                        RunningSegment("WORK", (meters - 4000).coerceAtLeast(1000), "Threshold work", RunningStepIntent.THRESHOLD, 255, 270),
                        RunningSegment("COOL_DOWN", 2000, "Easy cool-down", RunningStepIntent.COOLDOWN, 340, 370)
                    )
                )
            } else {
                RunningPrescription(
                    role = "EASY",
                    family = null,
                    segments = listOf(
                        RunningSegment("EASY", meters, "Aerobic easy", RunningStepIntent.AEROBIC_EASY, 330, 355)
                    )
                )
            }
        } else {
            RunningPrescription(
                role = "EASY",
                family = null,
                segments = emptyList(),
                mixedSegments = listOf(
                    RunningStepV2(
                        purpose = "EASY",
                        effort = "Time-only easy",
                        seconds = minutes * 60,
                        stepIntent = RunningStepIntent.AEROBIC_EASY,
                        targetPaceMinSecPerKm = 330,
                        targetPaceMaxSecPerKm = 355
                    )
                )
            )
        }
        return PlannedSession(
            id = id,
            date = date,
            discipline = "run",
            title = if (quality) "Threshold Run" else "Easy Aerobic Run",
            minutes = minutes,
            distanceKm = distanceKm,
            kind = if (quality) "threshold" else "easy",
            quality = quality,
            status = status,
            linkedActivityId = linkedActivityId,
            runningPrescription = rx
        )
    }

    private fun runActivity(
        id: String = "act-1",
        date: LocalDate = baseDate,
        hour: Int = 7,
        distanceM: Double? = 10_000.0,
        durationS: Double? = 3_300.0,
        recordedPace: Double? = 330.0,
        type: String = "running"
    ): ActivityRecord = ActivityRecord(
        id = id,
        title = "Morning Run",
        type = type,
        source = "garmin",
        startedAt = date.atTime(hour, 0).toInstant(ZoneOffset.UTC),
        distanceM = distanceM,
        durationS = durationS,
        movingTimeS = durationS,
        recordedPace = recordedPace,
        averageHr = 148.0,
        elevationM = 35.0
    )

    @Test
    fun exactSameDayDistanceMatchAutoLinksWithHighConfidence() {
        val session = runSession(id = "s-easy", distanceKm = 10.0)
        val activity = runActivity(id = "a-easy", distanceM = 10_150.0, durationS = 3400.0)

        val result = Engine15ActivityAutoMatcher.match(
            activities = listOf(activity),
            sessions = listOf(session),
            zoneId = zone,
            nowEpochMs = 1_700_000_000_000L
        )
        val match = result["a-easy"]!!
        assertEquals(ActivityMatchStatus.AUTO_LINKED, match.matchStatus)
        assertEquals("s-easy", match.plannedSessionId)
        assertEquals(0, match.dateOffsetDays)
        assertTrue(match.confidenceScore >= 0.80)
        assertFalse(match.isManualOverride)
    }

    @Test
    fun exactSameDayTimeOnlySessionMatchAutoLinks() {
        val session = runSession(id = "s-time", distanceKm = null, minutes = 45)
        val activity = runActivity(id = "a-time", distanceM = 7_600.0, durationS = 44 * 60.0)

        val result = Engine15ActivityAutoMatcher.match(
            activities = listOf(activity),
            sessions = listOf(session),
            zoneId = zone
        )
        val match = result["a-time"]!!
        assertEquals(ActivityMatchStatus.AUTO_LINKED, match.matchStatus)
        assertEquals("s-time", match.plannedSessionId)
        assertTrue(match.confidenceScore >= 0.80)
    }

    @Test
    fun offDayShiftRequiresConfirmation() {
        val session = runSession(id = "s-tue", date = baseDate, distanceKm = 10.0)
        val activity = runActivity(id = "a-wed", date = baseDate.plusDays(1), distanceM = 10_000.0)

        val result = Engine15ActivityAutoMatcher.match(
            activities = listOf(activity),
            sessions = listOf(session),
            zoneId = zone
        )
        val match = result["a-wed"]!!
        assertEquals(ActivityMatchStatus.NEEDS_CONFIRMATION, match.matchStatus)
        assertEquals("s-tue", match.plannedSessionId)
        assertEquals(1, match.dateOffsetDays)
        assertTrue("OFF_DAY_SHIFT_CONFIRMATION" in match.reasonCodes)
    }

    @Test
    fun twoRunsSameDayRequireConfirmation() {
        val session = runSession(id = "s-10k", distanceKm = 10.0)
        val morning = runActivity(id = "a-am", hour = 6, distanceM = 9_800.0, durationS = 3200.0)
        val evening = runActivity(id = "a-pm", hour = 18, distanceM = 9_600.0, durationS = 3150.0)

        val result = Engine15ActivityAutoMatcher.match(
            activities = listOf(morning, evening),
            sessions = listOf(session),
            zoneId = zone
        )
        assertEquals(ActivityMatchStatus.NEEDS_CONFIRMATION, result["a-am"]!!.matchStatus)
        assertEquals(ActivityMatchStatus.NEEDS_CONFIRMATION, result["a-pm"]!!.matchStatus)
        assertTrue("MULTI_RUN_SAME_DAY_TIE" in result["a-am"]!!.reasonCodes)
        assertTrue("MULTI_RUN_SAME_DAY_TIE" in result["a-pm"]!!.reasonCodes)
    }

    @Test
    fun ambiguousTwoSessionTieRequiresConfirmation() {
        val sPrev = runSession(id = "s-prev", date = baseDate.minusDays(1), distanceKm = 10.0)
        val sNext = runSession(id = "s-next", date = baseDate.plusDays(1), distanceKm = 10.0)
        val activity = runActivity(id = "a-mid", date = baseDate, distanceM = 10_000.0)

        val result = Engine15ActivityAutoMatcher.match(
            activities = listOf(activity),
            sessions = listOf(sPrev, sNext),
            zoneId = zone
        )
        val match = result["a-mid"]!!
        assertEquals(ActivityMatchStatus.NEEDS_CONFIRMATION, match.matchStatus)
        assertTrue("AMBIGUOUS_SESSION_TIE" in match.reasonCodes)
        assertEquals(2, match.candidates.size)
    }

    @Test
    fun slowRunOnQualityDayStillAutoMatchesSoExecutionAnalyzerCanGradeMiss() {
        val thresholdSession = runSession(id = "s-thresh", distanceKm = 10.0, quality = true)
        val slowJog = runActivity(
            id = "a-jog",
            distanceM = 10_000.0,
            durationS = 3_900.0,
            recordedPace = 390.0 // 6:30/km easy jog on a 4:15-4:30/km threshold day
        )

        val result = Engine15ActivityAutoMatcher.match(
            activities = listOf(slowJog),
            sessions = listOf(thresholdSession),
            zoneId = zone
        )
        val match = result["a-jog"]!!
        assertEquals(ActivityMatchStatus.AUTO_LINKED, match.matchStatus)
        assertEquals("s-thresh", match.plannedSessionId)
        assertTrue(match.confidenceScore >= 0.80)
    }

    @Test
    fun manualOverrideSupremacyForManuallyLinkedAndManuallyRejected() {
        val session = runSession(id = "s-manual", distanceKm = 10.0, linkedActivityId = "a-manual")
        val manualRun = runActivity(id = "a-manual", distanceM = 12_000.0)
        val rejectedRun = runActivity(id = "a-rejected", distanceM = 10_000.0)

        val openSession = runSession(id = "s-open", date = baseDate.plusDays(3), distanceKm = 10.0)
        val openActivity = runActivity(id = "a-open", date = baseDate.plusDays(3), distanceM = 10_000.0)

        val existing = mapOf(
            "a-open" to ActivityMatchRecord(
                activityId = "a-open",
                plannedSessionId = "s-open",
                matchStatus = ActivityMatchStatus.MANUALLY_REJECTED,
                confidenceScore = 0.0,
                dateOffsetDays = null,
                reasonCodes = listOf("MANUAL_OVERRIDE_REJECTED"),
                isManualOverride = true
            )
        )

        val result = Engine15ActivityAutoMatcher.match(
            activities = listOf(manualRun, rejectedRun, openActivity),
            sessions = listOf(session, openSession),
            existingMatches = existing,
            zoneId = zone
        )

        val manualMatch = result["a-manual"]!!
        assertEquals(ActivityMatchStatus.MANUALLY_LINKED, manualMatch.matchStatus)
        assertEquals("s-manual", manualMatch.plannedSessionId)
        assertTrue(manualMatch.isManualOverride)

        // Rejected pair must never be auto-linked or proposed as confirmation.
        val rejectedMatch = result["a-open"]!!
        assertEquals(ActivityMatchStatus.MANUALLY_REJECTED, rejectedMatch.matchStatus)
        assertTrue(rejectedMatch.isManualOverride)
    }

    @Test
    fun nonRunningDisciplineActivitiesAndSessionsAreIsolated() {
        val runSession = runSession(id = "s-run", distanceKm = 10.0)
        val strengthSession = PlannedSession(
            id = "s-str",
            date = baseDate,
            discipline = "str",
            title = "Strength Lower",
            minutes = 45,
            distanceKm = null,
            kind = "strength"
        )
        val cyclingActivity = runActivity(id = "a-bike", type = "cycling", distanceM = 25_000.0)
        val strengthActivity = runActivity(id = "a-str", type = "strength", distanceM = null)

        val result = Engine15ActivityAutoMatcher.match(
            activities = listOf(cyclingActivity, strengthActivity),
            sessions = listOf(runSession, strengthSession),
            zoneId = zone
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun autoMatchingDoesNotMutateAdherenceOrSessionStatus() {
        val session = runSession(id = "s-1", distanceKm = 10.0, quality = true, status = "planned")
        val activity = runActivity(id = "a-1", distanceM = 10_000.0)

        val result = Engine15ActivityAutoMatcher.match(
            activities = listOf(activity),
            sessions = listOf(session),
            zoneId = zone
        )
        assertEquals(ActivityMatchStatus.AUTO_LINKED, result["a-1"]!!.matchStatus)
        // Session input contract remains strictly unlinked/planned; adherence belongs to ExecutionAnalyzer.
        assertEquals("planned", session.status)
        assertNull(session.linkedActivityId)
    }

    @Test
    fun activityMatchRecordJsonRoundTripPreservesAllFields() {
        val original = ActivityMatchRecord(
            activityId = "garmin-123",
            plannedSessionId = "s-threshold",
            matchStatus = ActivityMatchStatus.AUTO_LINKED,
            confidenceScore = 0.925,
            dateOffsetDays = 0,
            reasonCodes = listOf("DATE_EXACT_MATCH", "VOLUME_STRONG_MATCH"),
            isManualOverride = false,
            candidates = listOf(
                ActivityMatchCandidate("s-threshold", 0.925, 0, listOf("DATE_EXACT_MATCH"))
            ),
            evaluatedAtEpochMs = 1_750_000_000_000L
        )
        val encoded = original.encode(owner = "local-owner-1")
        val decoded = ActivityMatchRecord.decode(encoded, expectedOwner = "local-owner-1")
        assertEquals(original, decoded)
    }
}
