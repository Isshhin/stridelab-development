package io.stridelab.privatealpha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ActivityWorkoutIntelligenceTest {
    private val started = Instant.parse("2026-11-17T06:00:00Z")

    private fun activity(id: String = "run") = ActivityRecord(
        id, "Morning Run", "run", "garmin", started, 10000.0, 3600.0,
        3500.0, 360.0, 190.0, null
    )

    @Test fun highAverageHeartRateAndUnlabelledLapsDoNotClassifyWorkoutPurpose() {
        val activity = activity()
        val detail = ActivityDetail(
            activity.id,
            listOf(
                ActivitySample(0, 0.0, 360.0, 188.0, null, null, null),
                ActivitySample(1, 10.0, 350.0, 192.0, null, null, null)
            ),
            listOf(ActivityLap(0, 1000.0, 360.0, 360.0, 190.0)), 0L
        )

        val result = ActivityWorkoutIntelligence.assess(
            activity, detail, emptyList(), setOf(activity.id), setOf("owner")
        )

        assertEquals(ActivityWorkoutKind.UNKNOWN, result.detected.kind)
        assertNull(result.detected.qualitySubtype)
        assertTrue(result.detected.evidence.any { it.contains("WORK/RECOVERY") })
    }

    @Test fun linkedPrescriptionIsReportedAsIntentNotDetectedExecution() {
        val activity = activity()
        val session = PlannedSession(
            id = "planned-threshold", date = LocalDate.parse("2026-11-17"),
            discipline = "run", title = "Threshold", minutes = 60, distanceKm = 10.0,
            kind = "quality", quality = true, status = "completed",
            linkedActivityId = activity.id,
            runningPrescription = RunningPrescription(
                role = "QUALITY", family = "Threshold",
                segments = listOf(
                    RunningSegment("WARM_UP", 2000, "easy"),
                    RunningSegment("WORK", 6000, "threshold"),
                    RunningSegment("COOL_DOWN", 2000, "easy")
                )
            )
        )
        val plan = PlanSnapshot(
            "plan", "owner", ENGINE15_VERSION, ENGINE15_POLICY, started,
            PlanRequest(policyChoice = "engine15"), listOf(session), emptyList(),
            status = "approved"
        )

        val result = ActivityWorkoutIntelligence.assess(
            activity, null, listOf(plan), setOf(activity.id), setOf("owner")
        )

        assertEquals(ActivityWorkoutKind.QUALITY_RUN, result.intended?.kind)
        assertEquals(ActivityQualitySubtype.THRESHOLD, result.intended?.qualitySubtype)
        assertEquals(ActivityWorkoutKind.UNKNOWN, result.detected.kind)
        assertTrue(result.intended!!.evidence.single().contains("does not confirm"))
    }

    @Test fun conflictingPlanLinksDoNotSupplyWorkoutIntent() {
        val activity = activity()
        fun plan(id: String, kind: String) = PlanSnapshot(
            id, "owner", ENGINE15_VERSION, ENGINE15_POLICY, started,
            PlanRequest(policyChoice = "engine15"),
            listOf(PlannedSession(
                id = "session-$id", date = LocalDate.parse("2026-11-17"),
                discipline = "run", title = kind, minutes = 45, distanceKm = 8.0,
                kind = kind, status = "completed", linkedActivityId = activity.id
            )), emptyList(), status = "approved"
        )

        val result = ActivityWorkoutIntelligence.assess(
            activity, null, listOf(plan("a", "easy"), plan("b", "long")),
            setOf(activity.id), setOf("owner")
        )

        assertNull(result.intended)
        assertEquals(ActivityWorkoutKind.UNKNOWN, result.detected.kind)
        assertTrue(result.intentWarning.orEmpty().contains("ambiguous"))
    }

    @Test fun userLabelCodecRetainsQualitySubtypeAndProvenance() {
        val expected = UserActivityWorkoutLabel(
            ActivityWorkoutKind.QUALITY_RUN, ActivityQualitySubtype.THRESHOLD
        )

        val encoded = UserActivityWorkoutLabelCodec.encode(expected)

        assertEquals(expected, UserActivityWorkoutLabelCodec.decode(encoded))
        assertTrue(encoded.contains("USER_REPORTED"))
        assertNull(UserActivityWorkoutLabelCodec.decode(encoded.replace("\"version\":1", "\"version\":9")))
    }

    @Test fun activityCorrectionOwnerKeepsLocalGarminAndPlanScopesSeparate() {
        val local = TrainingState(
            account = "cloud-account", localGarminOwner = "garmin-owner",
            localGarminIds = setOf("local-run"), localPlanOwner = "local-plan-owner"
        )
        assertEquals("garmin-owner", activityCorrectionOwner("local-run", local))
        assertEquals("cloud-account", activityCorrectionOwner("cloud-run", local))
        assertEquals("local-plan-owner", activityCorrectionOwner(
            "offline-run", local.copy(account = null, localGarminIds = emptySet())
        ))
        assertNull(activityCorrectionOwner(
            "unowned-run", local.copy(account = null, localPlanOwner = null, localGarminIds = emptySet())
        ))
    }
}
