package io.stridelab.privatealpha

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class Engine15RunningDebriefAnalyzerTest {
    private val activity = ActivityRecord(
        "run", "Morning run", "running", "garmin", Instant.EPOCH,
        4000.0, 1200.0, 1200.0, 390.0, 135.0, null
    )
    private val zones = ExecutionHrZoneProfile(95, 120, 145, 160, 180, 195)

    private fun samples(
        gap: Int = 10,
        hr: (Int) -> Double? = { if (it < 600) 126.0 else 140.0 },
        pace: (Int) -> Double = { 390.0 },
        cadence: (Int) -> Double? = { null }
    ) = (0..1200 step gap).mapIndexed { index, time ->
        ActivitySample(index, time.toDouble(), pace(time), hr(time), 5.0, null, null, cadence(time))
    }

    private fun recording(
        samples: List<ActivitySample> = samples(),
        laps: List<ActivityLap> = emptyList(),
        pauses: List<ActivityPauseInterval> = emptyList()
    ) = ActivityDetail(activity.id, samples, laps, 0L, pauses)

    @Test fun steadyPaceWithVerifiedProfileSurfacesPulseRiseAndMeasuredZones() {
        val result = requireNotNull(Engine15RunningDebriefAnalyzer.analyze(activity, recording(), verifiedHrZones = zones))
        assertEquals(DebriefTone.CAUTION, result.tone)
        assertEquals(DebriefCategory.PACING_AND_HR, result.insights.first().category)
        assertTrue(result.summary.contains("126"))
        assertTrue(result.summary.contains("140 bpm"))
        val zone = result.insights.single { it.category == DebriefCategory.INTENT_AND_ZONES }
        assertTrue(zone.body.contains("100%"))
        assertTrue(zone.body.contains("20:00"))
    }

    @Test fun missingProfileOrStreamNeverUsesSummaryHrOrCachedCompliance() {
        val execution = execution().copy(hrZoneCompliancePct = 80.0, aerobicDecouplingPct = 12.0)
        for ((detail, profile) in listOf(recording() to null, recording(samples(hr = { null })) to zones, null to zones)) {
            val result = requireNotNull(Engine15RunningDebriefAnalyzer.analyze(activity, detail, execution, profile))
            assertTrue(result.insights.any { it.category == DebriefCategory.EXECUTION })
            assertFalse(result.insights.any {
                it.category == DebriefCategory.PACING_AND_HR || it.category == DebriefCategory.INTENT_AND_ZONES
            })
        }
    }

    @Test fun invalidFitZonesAreIgnoredAndTerrainWeakensPaceHrInference() {
        val invalid = ActivitySourceHeartRateZones(null, null, null, null, listOf(40, 140, 155, 170, 190))
        val unverified = requireNotNull(Engine15RunningDebriefAnalyzer.analyze(activity,
            recording().copy(sourceHeartRateZones = invalid)))
        assertEquals(DebriefCategory.TERRAIN_AND_SPLITS, unverified.insights.single().category)
        val hill = recording(samples().map { it.copy(altitude = it.seconds / 10) })
        val result = requireNotNull(Engine15RunningDebriefAnalyzer.analyze(activity, hill, verifiedHrZones = zones))
        val trend = result.insights.single { it.category == DebriefCategory.PACING_AND_HR }
        assertEquals(DebriefTone.NEUTRAL, trend.tone)
        assertTrue(trend.body.contains("terrain", ignoreCase = true))
    }

    @Test fun linkedComplianceRequiresVerifiedBoundariesAndUsableRecordedCoverage() {
        val execution = execution().copy(hrZoneCompliancePct = 76.0)
        val result = requireNotNull(Engine15RunningDebriefAnalyzer.analyze(activity, recording(), execution, zones))
        val zone = result.insights.single { it.category == DebriefCategory.INTENT_AND_ZONES }
        assertTrue(zone.body.contains("76%"))
    }

    @Test fun gapsPausesAndMissingCoverageAreNotBridged() {
        val records = listOf(
            recording(samples(gap = 60)),
            recording(samples().filter { it.seconds <= 300 || it.seconds >= 1100 }),
            recording(samples().filter { it.seconds <= 400 }),
            recording(pauses = listOf(ActivityPauseInterval(60.0, 1190.0)))
        )
        for (detail in records) assertNull(Engine15RunningDebriefAnalyzer.analyze(activity, detail, verifiedHrZones = zones))
    }

    @Test fun pairedCadenceDescribesCoMovementWithoutHrOrEfficiencyClaim() {
        val detail = recording(samples(pace = { if (it < 600) 399.0 else 376.0 },
            cadence = { if (it < 600) 177.0 else 182.0 }))
        val result = requireNotNull(Engine15RunningDebriefAnalyzer.analyze(activity, detail))
        assertEquals(listOf(DebriefCategory.TERRAIN_AND_SPLITS, DebriefCategory.CADENCE), result.insights.map { it.category })
        val cadenceInsight = result.insights.single { it.category == DebriefCategory.CADENCE }
        assertEquals(DebriefTone.NEUTRAL, cadenceInsight.tone)
        assertTrue(cadenceInsight.body.contains("177") && cadenceInsight.body.contains("182 spm"))
        val text = result.insights.joinToString(" ") { it.body }
        assertFalse(text.contains("bpm") || text.contains("efficiency", ignoreCase = true))
    }

    @Test fun completeEvenKilometerLapsSupportComparisonButPartialOrPausedLapsDoNot() {
        val laps = listOf(400.0, 398.0, 376.0, 376.0).mapIndexed { i, seconds ->
            ActivityLap(i, 1000.0, seconds, null, null)
        }
        val result = requireNotNull(Engine15RunningDebriefAnalyzer.analyze(activity, recording(emptyList(), laps)))
        assertEquals(DebriefCategory.TERRAIN_AND_SPLITS, result.insights.single().category)
        assertTrue(result.summary.contains("23 sec/km") && result.summary.contains("6:39") && result.summary.contains("6:16"))
        assertNull(Engine15RunningDebriefAnalyzer.analyze(activity, recording(emptyList(), laps.dropLast(1))))
        assertNull(Engine15RunningDebriefAnalyzer.analyze(activity,
            recording(emptyList(), laps, listOf(ActivityPauseInterval(1.0, 5.0)))))
    }

    @Test fun longAerobicRecordingKeepsPositiveZonesAndUsesSplitsAndCadence() {
        val longRun = activity.copy(distanceM = 25_390.0, durationS = 10_135.0, movingTimeS = 10_135.0)
        val detail = recording(
            (0..10_135 step 5).mapIndexed { index, time ->
                val later = time >= 5_070
                ActivitySample(index, time.toDouble(), if (later) 380.0 else 410.0,
                    if (time < 1_110) 150.0 else 132.0, 9.0, null, null,
                    if (later) 183.0 else 177.0)
            },
            (0 until 25).map { index ->
                ActivityLap(index, 1000.0, if (index < 12) 410.0 else 380.0, null, null)
            } + ActivityLap(25, 390.0, 153.0, null, null)
        )
        val result = requireNotNull(Engine15DebriefEngine.running(longRun, detail, null, zones, ActivityWorkoutKind.LONG_RUN))
        assertEquals(DebriefTone.POSITIVE, result.tone)
        assertEquals(listOf(DebriefCategory.INTENT_AND_ZONES, DebriefCategory.PACING_AND_HR, DebriefCategory.CADENCE),
            result.insights.map { it.category })
        assertTrue(result.summary.contains("89%"))
        assertTrue(result.summary.contains("2:30:25"))
        assertNotNull(result.nextFocus)
    }

    @Test fun steadyThreeHourZoneTwoRunHasDistinctDurabilityObservations() {
        val longRun = activity.copy(distanceM = 28_000.0, durationS = 10_800.0, movingTimeS = 10_800.0)
        val records = (0..10_800 step 5).mapIndexed { index, seconds ->
            ActivitySample(index, seconds.toDouble(), if (seconds < 5400) 390.0 else 395.0,
                if (seconds < 5400) 132.0 else 137.0, null, null, null, 178.0)
        }
        val result = requireNotNull(Engine15RunningDebriefAnalyzer.analyze(
            longRun, recording(records), verifiedHrZones = zones, sessionIntent = ActivityWorkoutKind.LONG_RUN,
            weather = WeatherContext(26.0, 75.0, 21.0)
        ))
        assertEquals(listOf(DebriefCategory.INTENT_AND_ZONES, DebriefCategory.PACING_AND_HR, DebriefCategory.CADENCE),
            result.insights.map { it.category })
        assertEquals(DebriefTone.POSITIVE, result.tone)
        assertTrue(result.summary.contains("100%"))
        assertTrue(result.insights[1].body.contains("137 bpm"))
        assertTrue(result.paragraphs.isEmpty())
    }

    @Test fun zoneShareUsesTheFullKnownMovingDuration() {
        val partial = recording(samples(hr = { 132.0 }))
        val result = requireNotNull(Engine15RunningDebriefAnalyzer.analyze(
            activity.copy(movingTimeS = 1500.0), partial, verifiedHrZones = zones,
            sessionIntent = ActivityWorkoutKind.EASY_RUN
        ))
        assertTrue(result.summary.contains("80% of this run"))
    }

    @Test fun recordingAndExecutionMustBelongToTheRun() {
        assertNull(Engine15RunningDebriefAnalyzer.analyze(activity,
            recording().copy(activityId = "other"), execution().copy(activityId = "other"), zones))
        assertNull(Engine15RunningDebriefAnalyzer.analyze(activity.copy(type = "walking"), recording(), execution(), zones))
        assertNull(Engine15RunningDebriefAnalyzer.analyze(activity, execution =
            execution().copy(evidenceTier = ExecutionEvidenceTier.SUMMARY_ONLY)))
    }

    @Test fun sharedContractSortsCapsAndKeepsTieOrder() {
        val ranked = requireNotNull(SessionDebrief.fromInsights(listOf(50, 90, 90, 72, 55).mapIndexed { index, score ->
            DebriefInsight(DebriefCategory.EXECUTION, "Session observation $index", "Measured evidence.", DebriefTone.NEUTRAL, score)
        }))
        assertEquals(listOf(90, 90, 72), ranked.insights.map { it.priorityScore })
        assertEquals("Session observation 1", ranked.headline)
        assertEquals("Session observation 2", ranked.insights[1].title)
        assertNull(SessionDebrief.fromInsights(emptyList()))
        assertThrows(IllegalArgumentException::class.java) { ranked.copy(insights = ranked.insights.reversed()) }
        assertThrows(IllegalArgumentException::class.java) { ranked.copy(insights = ranked.insights + ranked.insights.first()) }
        assertThrows(IllegalArgumentException::class.java) { ranked.insights.first().copy(priorityScore = 101) }
    }

    @Test fun activityDetailOnlyUsesAcceptedExecutionForTheExactRecording() {
        val analysis = execution()
        val accepted = ActivityMatchRecord(activity.id, "session", ActivityMatchStatus.AUTO_LINKED, 0.9, 0, emptyList())
        assertSame(analysis, linkedDebriefExecution(activity.id, accepted, mapOf("session" to analysis)))
        assertNull(linkedDebriefExecution(activity.id, accepted.copy(matchStatus = ActivityMatchStatus.NEEDS_CONFIRMATION),
            mapOf("session" to analysis)))
        assertNull(linkedDebriefExecution(activity.id, accepted, mapOf("session" to analysis.copy(activityId = "other"))))
        assertNull(linkedDebriefExecution(activity.id, accepted, mapOf("session" to analysis.copy(plannedSessionId = "other"))))
    }

    @Test fun steadyKilometerLapsCanSpeakWithoutAStream() {
        val laps = List(4) { ActivityLap(it, 1000.0, 390.0, null, null) }
        val result = requireNotNull(Engine15RunningDebriefAnalyzer.analyze(activity, recording(emptyList(), laps)))
        assertTrue(result.summary.contains("6:30/km"))
        assertEquals(DebriefCategory.TERRAIN_AND_SPLITS, result.insights.single().category)
        assertTrue(result.paragraphs.isEmpty())
    }

    @Test fun cachedDriftRequiresCurrentVerifiedHeartRateEvidence() {
        val linked = execution().copy(aerobicDecouplingPct = 6.2)
        fun text(profile: ExecutionHrZoneProfile?): String {
            val result = requireNotNull(Engine15RunningDebriefAnalyzer.analyze(activity, recording(), linked, profile))
            return (result.insights.map { it.body } + listOfNotNull(result.nextFocus)).joinToString(" ")
        }
        assertTrue(text(zones).contains("6.2%"))
        assertFalse(text(null).contains("6.2%"))
    }

    @Test fun richRecordingSynthesizesCoachingWithoutRepeatingRawDynamicsOrHeader() {
        val run = activity.copy(durationS = 1350.0, elevationM = 82.0, maximumHr = 171.0)
        val detail = recording().copy(runningDynamics = RunningDynamics(248.0, 8.1, 1.05, 7.7, 176.0))
        val result = requireNotNull(Engine15RunningDebriefAnalyzer.analyze(run, detail, verifiedHrZones = zones))
        val text = (listOf(result.headline, result.summary) + result.insights.map { it.body } + listOfNotNull(result.nextFocus)).joinToString(" ")
        assertTrue(result.paragraphs.isEmpty())
        assertFalse("Should not repeat header distance", text.contains("4.00 km"))
        assertFalse("Should not repeat RunningDynamicsCard oscillation", text.contains("8.1 cm"))
        assertFalse("Should not repeat RunningDynamicsCard step length", text.contains("1.05 m"))
        assertFalse("Should not repeat RunningDynamicsCard vertical ratio", text.contains("7.7%"))
        assertFalse("Should not use em-dash", text.contains("—") || text.contains("--"))
        assertFalse(result.nextFocus.isNullOrBlank())
    }

    @Test fun easyRunGreyZoneCreepSurfacesCautionAndNextFocus() {
        val detail = recording(samples(hr = { if (it < 600) 150.0 else 164.0 }))
        val result = requireNotNull(Engine15RunningDebriefAnalyzer.analyze(
            activity, detail, verifiedHrZones = zones, sessionIntent = ActivityWorkoutKind.EASY_RUN
        ))
        val lead = result.insights.first()
        assertEquals(DebriefCategory.INTENT_AND_ZONES, lead.category)
        assertEquals(DebriefTone.CAUTION, lead.tone)
        assertTrue(lead.title.contains("Easy day", ignoreCase = true))
        assertTrue(result.nextFocus?.contains("full sentences", ignoreCase = true) == true)
    }

    @Test fun wentOutHotPositiveSplitVsHillExplainedSlowdownAndCadenceDrop() {
        val hotDetail = recording(samples(
            pace = { if (it < 600) 360.0 else 410.0 },
            hr = { 165.0 },
            cadence = { if (it < 600) 180.0 else 174.0 }
        ))
        val hot = requireNotNull(Engine15RunningDebriefAnalyzer.analyze(activity, hotDetail, verifiedHrZones = zones))
        val pacing = hot.insights.first { it.category == DebriefCategory.PACING_AND_HR }
        assertEquals(DebriefTone.CAUTION, pacing.tone)
        assertTrue(pacing.title.contains("Opened too fast", ignoreCase = true))

        val hillDetail = recording(samples(
            pace = { if (it < 600) 360.0 else 410.0 },
            hr = { 165.0 }
        ).map { it.copy(altitude = if (it.seconds < 600) 10.0 else 55.0) })
        val hill = requireNotNull(Engine15RunningDebriefAnalyzer.analyze(activity, hillDetail, verifiedHrZones = zones))
        val hillPacing = hill.insights.first { it.category == DebriefCategory.TERRAIN_AND_SPLITS }
        assertEquals(DebriefTone.POSITIVE, hillPacing.tone)
        assertTrue(hillPacing.title.contains("hills", ignoreCase = true))

        val steadyPaceDropCadence = recording(samples(
            pace = { 390.0 },
            cadence = { if (it < 600) 182.0 else 174.0 }
        ))
        val cad = requireNotNull(Engine15RunningDebriefAnalyzer.analyze(activity, steadyPaceDropCadence))
        assertEquals(DebriefTone.CAUTION, cad.insights.first { it.category == DebriefCategory.CADENCE }.tone)
    }

    @Test fun multiRepIntervalsIdentifyFrontLoadedFadeAndThirtyDayDistanceJump() {
        val fadeDetail = recording(emptyList(), listOf(
            ActivityLap(0, 400.0, 92.0, 230.0, null, workoutStepIndex = 1),
            ActivityLap(1, 200.0, 120.0, 600.0, null, workoutStepIndex = 2),
            ActivityLap(2, 400.0, 100.0, 250.0, null, workoutStepIndex = 1),
            ActivityLap(3, 200.0, 120.0, 600.0, null, workoutStepIndex = 2),
            ActivityLap(4, 400.0, 108.0, 270.0, null, workoutStepIndex = 1)
        )).copy(workoutSteps = listOf(ActivityWorkoutStep(1, null, "active", null, null, null)))
        val fade = requireNotNull(Engine15RunningDebriefAnalyzer.analyze(activity, fadeDetail))
        assertEquals(DebriefTone.CAUTION, fade.insights.first { it.category == DebriefCategory.EXECUTION }.tone)
        assertTrue(fade.headline.contains("Front-loaded", ignoreCase = true))

        val bigRun = activity.copy(distanceM = 18_000.0, startedAt = Instant.parse("2026-11-26T06:00:00Z"))
        val prior1 = activity.copy(id = "p1", distanceM = 10_000.0, startedAt = Instant.parse("2026-11-12T06:00:00Z"))
        val prior2 = activity.copy(id = "p2", distanceM = 12_000.0, startedAt = Instant.parse("2026-11-19T06:00:00Z"))
        val jump = requireNotNull(Engine15RunningDebriefAnalyzer.analyze(
            bigRun, recording(samples(pace = { 390.0 })), history = listOf(prior1, prior2)
        ))
        val load = jump.insights.first { it.category == DebriefCategory.LOAD_AND_RECOVERY }
        assertEquals(DebriefTone.CAUTION, load.tone)
        assertTrue(load.body.contains("50%"))
        assertFalse(listOfNotNull(jump.headline, jump.summary, jump.nextFocus).joinToString(" ").contains("—"))
    }

    @Test fun paceOnlyDebriefDoesNotMentionUnmeasuredEffortOrMechanics() {
        val detail = recording(samples(hr = { null }, pace = { if (it < 600) 410.0 else 370.0 }))
        val result = requireNotNull(Engine15RunningDebriefAnalyzer.analyze(activity, detail, verifiedHrZones = zones))
        val text = (result.insights.map { it.body } + result.paragraphs).joinToString(" ")
        assertTrue(text.contains("sec/km"))
        listOf("bpm", "heart rate", "Zone", "per step", "climbing").forEach { assertFalse(it, text.contains(it)) }
    }

    @Test fun recordedWorkLapsExplainQualityWithoutAPlanLink() {
        val detail = recording(emptyList(), listOf(
            ActivityLap(0, 400.0, 100.0, 250.0, null, workoutStepIndex = 1),
            ActivityLap(1, 200.0, 120.0, 600.0, null, workoutStepIndex = 2),
            ActivityLap(2, 400.0, 96.0, 240.0, null, workoutStepIndex = 1)
        )).copy(workoutSteps = listOf(ActivityWorkoutStep(1, null, "active", null, null, null)))
        val result = requireNotNull(Engine15RunningDebriefAnalyzer.analyze(activity, detail))
        assertEquals(DebriefCategory.EXECUTION, result.insights.first().category)
        assertTrue(result.summary.contains("4:10/km") && result.summary.contains("4:00/km"))
    }

    private fun execution() = WorkoutExecutionAnalysis(
        "session", activity.id, ExecutionEvidenceTier.LAP_AND_RECORD, ExecutionVerdict.ON_TARGET,
        1.0, 4.0, 4.0, 20, 20.0, 90.0, true, ExecutionPaceBias.ON_TARGET,
        null, null, emptyList(), emptyList(), "The work matched the planned pace."
    )
}
