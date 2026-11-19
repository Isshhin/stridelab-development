package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

/** Source-link and elapsed-lap reconciliation, not quality-intensity verification. */
class IndependentQualitySourceBindingTest {
    private val zone = ZoneId.of("Asia/Manila")
    private val today = LocalDate.of(2026, 11, 14)
    private val workoutDay = today.minusDays(2)
    private val owner = "synthetic-runner"
    private val sourceId = "garmin-source-1"

    private fun plan(
        ownerId: String = owner,
        linked: String? = sourceId,
        sessionStatus: String = "completed",
        quality: Boolean = true,
        sessionId: String = "planned-1"
    ) = PlanSnapshot(
        id = "plan-1", owner = ownerId, engineVersion = ENGINE15_VERSION,
        policyId = ENGINE15_POLICY, createdAt = Instant.EPOCH,
        request = PlanRequest(policyChoice = "engine15"),
        sessions = listOf(PlannedSession(
            id = sessionId, date = workoutDay, discipline = "run",
            title = "Quality session", minutes = 45, distanceKm = 7.0,
            kind = "quality", quality = quality, status = sessionStatus,
            linkedActivityId = linked
        )),
        explanations = emptyList(), status = "approved"
    )

    private fun activity(
        source: String = "garmin",
        type: String = "running",
        id: String = sourceId,
        date: LocalDate = workoutDay
    ) = ActivityRecord(
        id = id, title = "Recorded run", type = type, source = source,
        startedAt = date.atTime(7, 0).atZone(zone).toInstant(),
        distanceM = 7000.0, durationS = 2400.0, movingTimeS = null,
        recordedPace = null, averageHr = null, elevationM = null
    )

    private fun detail(id: String = sourceId) = ActivityDetail(
        activityId = id, samples = emptyList(), fetchedAt = 0L,
        laps = listOf(
            ActivityLap(0, 1000.0, 240.0, null, null, 600.0),
            ActivityLap(1, 300.0, 120.0, null, null, 840.0),
            ActivityLap(2, 1000.0, 240.0, null, null, 960.0)
        )
    )

    private fun claim(
        origin: IndependentQualityEvidenceOrigin = IndependentQualityEvidenceOrigin.DEVICE_SEGMENTS_ASSERTED,
        matched: Boolean = true,
        source: String = sourceId,
        segments: List<Int>? = listOf(240, 240),
        lapIndices: List<Int> = listOf(0, 2),
        date: LocalDate = workoutDay
    ) = IndependentQualityLapClaim(
        IndependentQualitySegmentEvidence(
            ownerId = owner, sourceRecordId = source, sessionId = "planned-1",
            performedOn = date, family = IndependentQualityFamily.THRESHOLD,
            origin = origin, sourceSessionMatchAsserted = matched,
            performedWorkSegmentsSeconds = segments
        ),
        recordedWorkLapIndices = lapIndices
    )

    private fun inspect(
        record: IndependentQualityLapClaim = claim(),
        plans: List<PlanSnapshot> = listOf(plan()),
        activities: List<ActivityRecord> = listOf(activity()),
        sourceDetail: ActivityDetail? = detail()
    ) = IndependentQualitySourceBinding.inspect(
        claim = record, plans = plans, sourceActivities = activities,
        detail = sourceDetail, asOf = today, zone = zone
    )

    private fun deferred(
        expected: String,
        result: IndependentQualitySourceBindingDecision
    ) {
        assertTrue(result.toString(), result is IndependentQualitySourceBindingDecision.Deferred)
        assertEquals(expected, (result as IndependentQualitySourceBindingDecision.Deferred).ruleId)
    }

    @Test fun exactLinkedSourceLapsReconcileButCannotVerifyIntensity() {
        val outcome = inspect()
        assertTrue(outcome.toString(), outcome is IndependentQualitySourceBindingDecision.LapDurationsReconciled)
        val accepted = outcome as IndependentQualitySourceBindingDecision.LapDurationsReconciled
        assertEquals(480.0, accepted.recordedWorkSeconds, 0.0)
        assertEquals(listOf(0, 2), accepted.workLapIndices)
        assertEquals(sourceId, accepted.activityId)
        assertFalse(accepted.qualityClassificationVerified)
        assertFalse(accepted.authorizedForTemplateSelection)
        assertTrue("SEGMENT_INTENSITY_UNVERIFIED" in accepted.ruleIds)
    }

    @Test fun manualSourceAndUnassertedSourceLinkAreRejected() {
        deferred("UNSUPPORTED_SEGMENT_ORIGIN",
            inspect(record = claim(origin = IndependentQualityEvidenceOrigin.SELF_REPORTED_SEGMENTS)))
        deferred("UNSUPPORTED_SEGMENT_ORIGIN",
            inspect(record = claim(matched = false)))
        deferred("UNSUPPORTED_SOURCE", inspect(activities = listOf(activity(source = "manual"))))
    }

    @Test fun missingOrDuplicatePlanLinksCannotAuthorizeExposure() {
        deferred("SOURCE_LINK_NOT_UNIQUE", inspect(plans = listOf(plan(linked = null))))
        val secondPlan = plan(sessionId = "planned-2").copy(id = "plan-2")
        deferred("SOURCE_LINK_NOT_UNIQUE", inspect(plans = listOf(plan(), secondPlan)))
        deferred("PLANNED_SESSION_MISMATCH", inspect(plans = listOf(plan(quality = false))))
        deferred("PLANNED_SESSION_MISMATCH", inspect(plans = listOf(plan(sessionStatus = "planned"))))
    }

    @Test fun mixedOwnersAndMismatchedRecordedDatesAreRejected() {
        deferred("PLAN_OWNER_SCOPE_CONFLICT", inspect(plans = listOf(plan(ownerId = "different-owner"))))
        deferred("PERFORMED_DATE_MISMATCH", inspect(activities = listOf(activity(date = workoutDay.minusDays(1)))))
        deferred("OUTSIDE_RECENT_WINDOW", inspect(record = claim(date = today.plusDays(1))))
    }

    @Test fun duplicatedOrMissingSourceSummariesAndDetailsAreRejected() {
        deferred("SOURCE_RECORD_NOT_UNIQUE",
            inspect(activities = listOf(activity(), activity())))
        deferred("SOURCE_RECORD_NOT_UNIQUE", inspect(activities = emptyList()))
        deferred("SOURCE_DETAIL_UNAVAILABLE", inspect(sourceDetail = null))
        deferred("SOURCE_DETAIL_UNAVAILABLE", inspect(sourceDetail = detail(id = "another-activity")))
        deferred("UNSUPPORTED_SOURCE", inspect(activities = listOf(activity(type = "walking"))))
    }

    @Test fun missingLapOrMissingTimingCannotBeTreatedAsPerformedQuality() {
        deferred("SOURCE_LAP_NOT_FOUND", inspect(record = claim(lapIndices = listOf(0, 4))))
        val missingTiming = detail().copy(laps = detail().laps.map {
            if (it.index == 2) it.copy(startSeconds = null) else it
        })
        deferred("SOURCE_LAP_TIMING_UNKNOWN", inspect(sourceDetail = missingTiming))
        deferred("WORK_SEGMENTS_UNKNOWN", inspect(record = claim(segments = null)))
        deferred("WORK_LAP_COUNT_MISMATCH", inspect(record = claim(segments = listOf(240))))
    }

    @Test fun claimedDurationsMustAgreeWithRealRecordedLapDurations() {
        deferred("RECORDED_WORK_DURATION_MISMATCH",
            inspect(record = claim(segments = listOf(240, 300))))
        deferred("RECORDED_WORK_DURATION_MISMATCH",
            inspect(record = claim(segments = listOf(240, 243))))
        assertTrue(inspect(record = claim(segments = listOf(241, 240)))
            is IndependentQualitySourceBindingDecision.LapDurationsReconciled)
    }

    @Test fun pausesAndOverlappingOrOutOfBoundsLapsFailClosed() {
        val paused = detail().copy(pauseIntervals = listOf(ActivityPauseInterval(1000.0, 1030.0)))
        deferred("SOURCE_WORK_OVERLAPS_PAUSE", inspect(sourceDetail = paused))
        val overlap = detail().copy(laps = detail().laps.map {
            if (it.index == 2) it.copy(startSeconds = 700.0) else it
        })
        deferred("SOURCE_WORK_LAPS_OVERLAP", inspect(sourceDetail = overlap))
        val outside = detail().copy(laps = detail().laps.map {
            if (it.index == 2) it.copy(startSeconds = 2300.0) else it
        })
        deferred("SOURCE_LAP_OUTSIDE_ACTIVITY", inspect(sourceDetail = outside))
    }

    @Test fun invalidLapSelectionsFailBeforeSourceLookup() {
        assertThrows(IllegalArgumentException::class.java) {
            claim(lapIndices = listOf(0, 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            claim(lapIndices = listOf(2, 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            claim(lapIndices = emptyList())
        }
    }
}
