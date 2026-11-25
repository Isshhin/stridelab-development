package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/**
 * M1B Canonical Activity Identity, Deduplication & Telemetry Quality tests.
 * Includes clearly labeled deterministic synthetic Track 1A fixtures (F01/F02/F03/F08/F10/F11).
 */
class Engine15M1BCanonicalActivityTest {

    private val accountA = "owner-m1b-alpha"
    private val accountB = "owner-m1b-beta"

    private fun runRecord(
        id: String,
        source: String,
        startedAt: String = "2026-11-20T07:00:00Z",
        distanceM: Double? = 10020.0,
        durationS: Double? = 3000.0,
        movingTimeS: Double? = 2990.0,
        avgPace: Double? = 299.4,
        avgHr: Double? = 152.0,
        maxHr: Double? = 171.0,
        title: String = "Morning 10K",
        type: String = "running"
    ): ActivityRecord = ActivityRecord(
        id = id,
        title = title,
        type = type,
        source = source,
        startedAt = Instant.parse(startedAt),
        distanceM = distanceM,
        durationS = durationS,
        movingTimeS = movingTimeS,
        recordedPace = avgPace,
        averageHr = avgHr,
        elevationM = 45.0,
        maximumHr = maxHr
    )

    private fun detailWithSamples(
        activityId: String,
        samples: List<ActivitySample>
    ): ActivityDetail = ActivityDetail(
        activityId = activityId,
        samples = samples,
        laps = emptyList(),
        fetchedAt = 1_795_590_000_000L
    )

    @Test fun exactDuplicateImportIsStrictlyIdempotent() {
        val fitObs = RawActivityObservation(
            accountId = accountA,
            record = runRecord(id = "fit-1001", source = "fit"),
            sourceIdentity = ImportedSourceIdentity(accountA, "fit", externalId = "fit-1001", sha256 = "a".repeat(64))
        )

        val singlePass = Engine15CanonicalActivityProjector.project(listOf(fitObs))
        val repeatedPass = Engine15CanonicalActivityProjector.project(listOf(fitObs, fitObs, fitObs))

        assertEquals(1, singlePass.activities.size)
        assertEquals(1, repeatedPass.activities.size)
        val a1 = singlePass.activities.single()
        val a2 = repeatedPass.activities.single()
        assertEquals(CanonicalDeduplicationStatus.SINGLE_SOURCE, a1.deduplicationStatus)
        assertEquals(CanonicalDeduplicationStatus.EXACT_DUPLICATE_COLLAPSED, a2.deduplicationStatus)
        assertEquals(a1.canonicalId, a2.canonicalId)
        assertEquals(a1.contentHash, a2.contentHash)
        assertEquals(1, a2.rawComponents.size)
    }

    @Test fun canonicalIdentityAndComponentOrderAreStableUnderReorderedImports() {
        // Synthetic Track 1A-F08 duplicate multi-source run.
        val sharedSha = "b".repeat(64)
        val hcObs = RawActivityObservation(
            accountId = accountA,
            record = runRecord(id = "hc-10k", source = "health_connect", distanceM = 9950.0, durationS = 3005.0, avgHr = 152.0),
            sourceIdentity = ImportedSourceIdentity(accountA, "health_connect", externalId = "hc-10k", sha256 = sharedSha)
        )
        val phoneObs = RawActivityObservation(
            accountId = accountA,
            record = runRecord(id = "manual-phone-10k", source = "manual", distanceM = 10000.0, durationS = 3010.0, avgHr = null, maxHr = null),
            corroborationToken = "synthetic-f08-corroborated"
        )
        val fitObs = RawActivityObservation(
            accountId = accountA,
            record = runRecord(id = "fit-10k", source = "fit", distanceM = 10020.0, durationS = 3000.0, avgHr = 153.0),
            sourceIdentity = ImportedSourceIdentity(accountA, "fit", externalId = "fit-10k", sha256 = sharedSha),
            corroborationToken = "synthetic-f08-corroborated"
        )

        val order1 = Engine15CanonicalActivityProjector.project(listOf(fitObs, hcObs, phoneObs))
        val order2 = Engine15CanonicalActivityProjector.project(listOf(phoneObs, fitObs, hcObs))

        assertEquals(1, order1.activities.size)
        assertEquals(1, order2.activities.size)
        val c1 = order1.activities.single()
        val c2 = order2.activities.single()
        assertEquals(c1.canonicalId, c2.canonicalId)
        assertEquals(c1.contentHash, c2.contentHash)
        assertEquals("fit", c1.primarySource)
        assertEquals(3, c1.rawComponents.size)
        assertEquals(c1.rawComponents, c2.rawComponents)
        assertEquals(10020.0, c1.normalizedMetrics.distance!!.value, 0.001)
        assertEquals(CanonicalDeduplicationStatus.VERIFIED_MULTI_SOURCE_DUPLICATE, c1.deduplicationStatus)
    }

    @Test fun repeatedSyncAndDelayedOutOfOrderIngestionPreserveExistingIdentities() {
        val day3 = RawActivityObservation(
            accountId = accountA,
            record = runRecord(id = "garmin-day3", source = "garmin", startedAt = "2026-11-23T07:00:00Z", distanceM = 8000.0, durationS = 2400.0)
        )
        val day1Delayed = RawActivityObservation(
            accountId = accountA,
            record = runRecord(id = "garmin-day1", source = "garmin", startedAt = "2026-11-21T07:00:00Z", distanceM = 6000.0, durationS = 1800.0)
        )

        val initialSync = Engine15CanonicalActivityProjector.project(listOf(day3))
        val delayedSync = Engine15CanonicalActivityProjector.project(listOf(day3, day1Delayed, day3))

        assertEquals(1, initialSync.activities.size)
        assertEquals(2, delayedSync.activities.size)
        val initialDay3 = initialSync.activities.single()
        val afterDay3 = delayedSync.activities.first { it.rawComponents.any { c -> c.recordId == "garmin-day3" } }
        assertEquals(initialDay3.canonicalId, afterDay3.canonicalId)
        assertEquals(initialDay3.contentHash, afterDay3.contentHash)
    }

    @Test fun distinctLegitimateAndAmbiguousOverlappingActivitiesAreNeverMergedSilently() {
        // Distinct disciplines overlapping at same time.
        val overlapRun = RawActivityObservation(
            accountId = accountA,
            record = runRecord(id = "overlap-run", source = "garmin", type = "running", distanceM = 8000.0, durationS = 2400.0)
        )
        val overlapBike = RawActivityObservation(
            accountId = accountA,
            record = runRecord(id = "overlap-bike", source = "intervals", type = "cycling", distanceM = 18000.0, durationS = 2400.0)
        )
        // Second uncorroborated same-discipline run passing POL-001 + POL-002 (no GPS/sha/token corroboration).
        val uncorroboratedRun = RawActivityObservation(
            accountId = accountA,
            record = runRecord(id = "uncorr-hc-run", source = "health_connect", startedAt = "2026-11-20T07:01:00Z", distanceM = 8050.0, durationS = 2410.0)
        )

        val result = Engine15CanonicalActivityProjector.project(listOf(overlapRun, overlapBike, uncorroboratedRun))

        assertEquals(3, result.activities.size)
        assertEquals(2, result.unresolvedAmbiguityCount)

        val run1 = result.activities.first { it.rawComponents.any { c -> c.recordId == "overlap-run" } }
        val run2 = result.activities.first { it.rawComponents.any { c -> c.recordId == "uncorr-hc-run" } }
        assertEquals(CanonicalDeduplicationStatus.AMBIGUOUS_DUPLICATE_CANDIDATE, run1.deduplicationStatus)
        assertEquals(CanonicalDeduplicationStatus.AMBIGUOUS_DUPLICATE_CANDIDATE, run2.deduplicationStatus)
        assertTrue(run1.ambiguities.any { it.kind == ActivityAmbiguityKind.AMBIGUOUS_OVERLAPPING_DUPLICATE && it.relatedRawRecordId == "uncorr-hc-run" })
        assertTrue(run2.ambiguities.any { it.kind == ActivityAmbiguityKind.AMBIGUOUS_OVERLAPPING_DUPLICATE && it.relatedRawRecordId == "overlap-run" })
    }

    @Test fun potentialSplitRecordingsWithinPol009WindowAreFlaggedAndPreservedSeparate() {
        val warmUpPart = RawActivityObservation(
            accountId = accountA,
            record = runRecord(id = "split-1", source = "garmin", startedAt = "2026-11-22T07:00:00Z", distanceM = 3000.0, durationS = 900.0)
        )
        val workoutPart = RawActivityObservation(
            accountId = accountA,
            // Starts 8 minutes after split-1 ends (within 15 min POL-009 window).
            record = runRecord(id = "split-2", source = "garmin", startedAt = "2026-11-22T07:23:00Z", distanceM = 7000.0, durationS = 2100.0)
        )

        val result = Engine15CanonicalActivityProjector.project(listOf(warmUpPart, workoutPart))
        assertEquals(2, result.activities.size)
        assertTrue(result.activities.all { it.deduplicationStatus == CanonicalDeduplicationStatus.POTENTIAL_SPLIT_RECORDING })
        assertTrue(result.activities.all { act -> act.ambiguities.any { it.kind == ActivityAmbiguityKind.POTENTIAL_SPLIT_RECORDING } })
    }

    @Test fun accountAndDisciplineIsolationHoldStrictly() {
        // Synthetic Track 1A-F11 multi-discipline athlete vs. cross-account identical activity.
        val sharedSha = "c".repeat(64)
        val obsAcctA = RawActivityObservation(
            accountId = accountA,
            record = runRecord(id = "acctA-run", source = "fit", distanceM = 12000.0, durationS = 3600.0),
            sourceIdentity = ImportedSourceIdentity(accountA, "fit", externalId = "shared-ext-1", sha256 = sharedSha)
        )
        val obsAcctB = RawActivityObservation(
            accountId = accountB,
            record = runRecord(id = "acctB-run", source = "fit", distanceM = 12000.0, durationS = 3600.0),
            sourceIdentity = ImportedSourceIdentity(accountB, "fit", externalId = "shared-ext-1", sha256 = sharedSha)
        )
        val strengthA = RawActivityObservation(
            accountId = accountA,
            record = runRecord(id = "acctA-str", source = "manual", type = "strength", distanceM = null, durationS = 2700.0, movingTimeS = null, avgPace = null, avgHr = 115.0)
        )
        val calA = RawActivityObservation(
            accountId = accountA,
            record = runRecord(id = "acctA-cal", source = "manual", type = "calisthenics", distanceM = null, durationS = 1800.0, movingTimeS = null, avgPace = null, avgHr = null, maxHr = null)
        )
        val cycleA = RawActivityObservation(
            accountId = accountA,
            record = runRecord(id = "acctA-cyc", source = "garmin", type = "cycling", distanceM = 40000.0, durationS = 5400.0, avgPace = null, avgHr = 138.0)
        )

        val projected = Engine15CanonicalActivityProjector.project(listOf(obsAcctA, obsAcctB, strengthA, calA, cycleA))

        assertEquals(5, projected.activities.size)
        val acctARecords = projected.activities.filter { it.accountId == accountA }
        val acctBRecords = projected.activities.filter { it.accountId == accountB }
        assertEquals(4, acctARecords.size)
        assertEquals(1, acctBRecords.size)
        assertNotEquals(
            acctARecords.first { it.discipline == AthleteDiscipline.RUN }.canonicalId,
            acctBRecords.single().canonicalId
        )
        assertEquals(12000.0, acctARecords.filter { it.discipline == AthleteDiscipline.RUN }.sumOf { it.normalizedMetrics.distance!!.value }, 0.001)
    }

    @Test fun telemetryQualityDistinguishesMissingConfirmedInvalidAndSummaryOnly() {
        val missingObs = RawActivityObservation(
            accountId = accountA,
            record = runRecord(id = "tele-missing", source = "manual", distanceM = 5000.0, durationS = 1500.0, avgHr = null, maxHr = null)
        )
        val badSamples = (0 until 20).map { idx ->
            ActivitySample(
                index = idx,
                seconds = idx * 30.0,
                pace = 300.0,
                // 2/20 = 10% > POL-005 5% cap out of range (> 235 bpm).
                hr = if (idx < 2) 245.0 else 150.0 + (idx % 5),
                altitude = 100.0,
                latitude = null,
                longitude = null,
                cadenceSpm = 170.0
            )
        }
        val badHrObs = RawActivityObservation(
            accountId = accountA,
            record = runRecord(id = "tele-bad-hr", source = "garmin", distanceM = 5000.0, durationS = 1500.0, avgHr = 160.0),
            detail = detailWithSamples("tele-bad-hr", badSamples)
        )
        val summaryOnlyObs = RawActivityObservation(
            accountId = accountA,
            record = runRecord(id = "tele-summary", source = "garmin", distanceM = 5000.0, durationS = 1500.0, avgHr = 155.0)
        )
        val flatlineSamples = (0 until 12).map { idx ->
            ActivitySample(
                index = idx,
                seconds = idx * 10.0,
                pace = 300.0,
                // 4/12 = 33.3% flatlined (>= POL-006 25%).
                hr = if (idx < 4) 142.0 else 150.0 + idx,
                altitude = null,
                latitude = null,
                longitude = null
            )
        }
        val flatlineObs = RawActivityObservation(
            accountId = accountA,
            record = runRecord(id = "tele-flat", source = "garmin", distanceM = 4000.0, durationS = 1200.0, avgHr = 150.0),
            detail = detailWithSamples("tele-flat", flatlineSamples)
        )

        val res = Engine15CanonicalActivityProjector.project(listOf(missingObs, badHrObs, summaryOnlyObs, flatlineObs))
        val byId = res.activities.associateBy { it.rawComponents.single().recordId }

        assertEquals(TelemetryQualityStatus.MISSING_TELEMETRY, byId.getValue("tele-missing").telemetryQuality.hrQuality)
        assertNull(byId.getValue("tele-missing").normalizedMetrics.averageHr)

        assertEquals(TelemetryQualityStatus.QUARANTINED_UNRELIABLE, byId.getValue("tele-bad-hr").telemetryQuality.hrQuality)
        assertNull(byId.getValue("tele-bad-hr").normalizedMetrics.averageHr)
        assertEquals(160.0, byId.getValue("tele-bad-hr").rawComponents.single().rawAverageHrBpm!!, 0.001)

        assertEquals(TelemetryQualityStatus.UNSUPPORTED_OR_UNAVAILABLE_ANALYSIS, byId.getValue("tele-summary").telemetryQuality.hrQuality)
        assertEquals(TelemetryQualityStatus.QUARANTINED_UNRELIABLE, byId.getValue("tele-flat").telemetryQuality.hrQuality)
    }

    @Test fun cadenceLockQuarantinesOpticalPpgWithoutDestroyingValidDistanceAndRespectsUncertainty() {
        // Synthetic Track 1A-F10 cadence lock fixture (65% of active samples |HR - cad| <= 4 bpm/spm).
        val samples = (0 until 20).map { idx ->
            val locked = idx < 13 // 65% >= POL-004 40%
            val cadence = 170.0 + (idx % 4)
            ActivitySample(
                index = idx,
                seconds = idx * 60.0,
                pace = 300.0,
                hr = if (locked) cadence + 1.0 else 148.0 + (idx % 4),
                altitude = 50.0,
                latitude = 37.7749 + idx * 0.0008,
                longitude = -122.4194,
                cadenceSpm = cadence
            )
        }
        val opticalPpg = RawActivityObservation(
            accountId = accountA,
            record = runRecord(id = "f10-ppg", source = "garmin", distanceM = 8000.0, durationS = 1200.0, avgHr = 165.0),
            detail = detailWithSamples("f10-ppg", samples),
            hrSensorModality = HeartRateSensorModality.OPTICAL_WRIST_PPG
        )
        val chestStrap = RawActivityObservation(
            accountId = accountA,
            record = runRecord(id = "f10-ecg", source = "garmin", distanceM = 8000.0, durationS = 1200.0, avgHr = 165.0),
            detail = detailWithSamples("f10-ecg", samples),
            hrSensorModality = HeartRateSensorModality.CHEST_STRAP_ECG
        )
        val unknownSensor = RawActivityObservation(
            accountId = accountA,
            record = runRecord(id = "f10-unk", source = "garmin", distanceM = 8000.0, durationS = 1200.0, avgHr = 165.0),
            detail = detailWithSamples("f10-unk", samples),
            hrSensorModality = HeartRateSensorModality.UNKNOWN
        )

        val ppgRes = Engine15CanonicalActivityProjector.project(listOf(opticalPpg)).activities.single()
        val ecgRes = Engine15CanonicalActivityProjector.project(listOf(chestStrap)).activities.single()
        val unkRes = Engine15CanonicalActivityProjector.project(listOf(unknownSensor)).activities.single()

        assertEquals(TelemetryQualityStatus.QUARANTINED_CADENCE_LOCK, ppgRes.telemetryQuality.hrQuality)
        assertNull(ppgRes.normalizedMetrics.averageHr)
        assertEquals(165.0, ppgRes.rawComponents.single().rawAverageHrBpm!!, 0.001)
        assertEquals(TelemetryQualityStatus.VALID_OBSERVED, ppgRes.telemetryQuality.distanceQuality)
        assertEquals(TelemetryQualityStatus.VALID_OBSERVED, ppgRes.telemetryQuality.durationQuality)
        assertEquals(8000.0, ppgRes.normalizedMetrics.distance!!.value, 0.001)
        assertEquals(1200.0, ppgRes.normalizedMetrics.duration!!.value, 0.001)

        // Matching HR/cadence numbers alone do NOT falsely quarantine ECG or UNKNOWN provenance as PPG lock.
        assertEquals(TelemetryQualityStatus.VALID_OBSERVED, ecgRes.telemetryQuality.hrQuality)
        assertEquals(TelemetryQualityStatus.UNSUPPORTED_OR_UNAVAILABLE_ANALYSIS, unkRes.telemetryQuality.hrQuality)
        assertTrue(ecgRes.telemetryQuality.flags.contains("HR_CADENCE_PROXIMITY_NON_CONCLUSIVE"))
        assertTrue(unkRes.telemetryQuality.flags.contains("HR_CADENCE_PROXIMITY_NON_CONCLUSIVE"))
    }

    @Test fun gnssSpeedSpikeAndDropoutDegradeDistanceQualityWithoutDeletingRawRecord() {
        val spikeSamples = listOf(
            ActivitySample(0, 0.0, 300.0, 145.0, 10.0, 37.7700, -122.4100),
            ActivitySample(1, 3.0, 300.0, 147.0, 10.0, 37.7701, -122.4100),
            // > 12.5 m/s sustained for 6.0 s (> POL-007 5.0 s).
            ActivitySample(2, 6.0, 70.0, 149.0, 10.0, 37.7710, -122.4100),
            ActivitySample(3, 9.0, 70.0, 151.0, 10.0, 37.7720, -122.4100)
        )
        val dropoutSamples = listOf(
            ActivitySample(0, 0.0, 300.0, 145.0, 10.0, 37.7700, -122.4100),
            ActivitySample(1, 5.0, 300.0, 146.0, 10.0, null, null), // 25% >= POL-008 20%
            ActivitySample(2, 10.0, 300.0, 147.0, 10.0, 37.7702, -122.4100),
            ActivitySample(3, 15.0, 300.0, 148.0, 10.0, 37.7703, -122.4100)
        )

        val spikeObs = RawActivityObservation(
            accountA,
            runRecord("gnss-spike", "garmin", distanceM = 5000.0, durationS = 900.0, avgHr = 148.0),
            detailWithSamples("gnss-spike", spikeSamples),
            isOutdoorGnss = true
        )
        val dropObs = RawActivityObservation(
            accountA,
            runRecord("gnss-drop", "garmin", startedAt = "2026-11-21T07:00:00Z", distanceM = 5000.0, durationS = 900.0, avgHr = 146.0),
            detailWithSamples("gnss-drop", dropoutSamples),
            isOutdoorGnss = true
        )

        val res = Engine15CanonicalActivityProjector.project(listOf(spikeObs, dropObs))
        val byId = res.activities.associateBy { it.rawComponents.single().recordId }
        assertEquals(TelemetryQualityStatus.DEGRADED, byId.getValue("gnss-spike").telemetryQuality.distanceQuality)
        assertTrue(byId.getValue("gnss-spike").telemetryQuality.flags.contains("GNSS_SUSTAINED_SPEED_SPIKE"))
        assertEquals(TelemetryQualityStatus.DEGRADED, byId.getValue("gnss-drop").telemetryQuality.distanceQuality)
        assertTrue(byId.getValue("gnss-drop").telemetryQuality.flags.contains("GNSS_DROPOUT"))
        assertEquals(5000.0, byId.getValue("gnss-spike").rawComponents.single().rawDistanceM!!, 0.001)
    }

    @Test fun sourceIdentifierCollisionAndHistoricalCorrectionBehaveSafely() {
        val unaffected = RawActivityObservation(
            accountA,
            runRecord(id = "untouched-run", source = "garmin", startedAt = "2026-11-10T07:00:00Z", distanceM = 7000.0, durationS = 2100.0)
        )
        val originalRun = RawActivityObservation(
            accountA,
            runRecord(id = "corr-run", source = "garmin", startedAt = "2026-11-18T07:00:00Z", distanceM = 10000.0, durationS = 3000.0),
            sourceIdentity = ImportedSourceIdentity(accountA, "garmin", externalId = "ext-99")
        )
        val correctedResync = RawActivityObservation(
            accountA,
            runRecord(id = "corr-run", source = "garmin", startedAt = "2026-11-18T07:00:00Z", distanceM = 10050.0, durationS = 3000.0),
            sourceIdentity = ImportedSourceIdentity(accountA, "garmin", externalId = "ext-99")
        )
        val collisionDifferentDay = RawActivityObservation(
            accountA,
            runRecord(id = "collision-run", source = "garmin", startedAt = "2026-11-19T07:00:00Z", distanceM = 5000.0, durationS = 1500.0),
            sourceIdentity = ImportedSourceIdentity(accountA, "garmin", externalId = "ext-99")
        )

        val before = Engine15CanonicalActivityProjector.project(listOf(unaffected, originalRun))
        val afterCorrection = Engine15CanonicalActivityProjector.project(listOf(unaffected, originalRun, correctedResync))
        val withCollision = Engine15CanonicalActivityProjector.project(listOf(unaffected, originalRun, collisionDifferentDay))

        val untouchedBefore = before.activities.first { it.rawComponents.any { c -> c.recordId == "untouched-run" } }
        val untouchedAfter = afterCorrection.activities.first { it.rawComponents.any { c -> c.recordId == "untouched-run" } }
        assertEquals(untouchedBefore.canonicalId, untouchedAfter.canonicalId)

        val corrected = afterCorrection.activities.first { it.rawComponents.any { c -> c.recordId == "corr-run" } }
        assertEquals(10050.0, corrected.normalizedMetrics.distance!!.value, 0.001)
        assertEquals(2, corrected.rawComponents.size)

        // Collision preserves both distinct contradictory activities explicitly.
        assertEquals(3, withCollision.activities.size)
        val colliding = withCollision.activities.filter { it.rawComponents.any { c -> c.externalId == "ext-99" } }
        assertEquals(2, colliding.size)
        assertTrue(colliding.all { c -> c.ambiguities.any { it.kind == ActivityAmbiguityKind.SOURCE_IDENTIFIER_COLLISION } })
    }

    @Test fun backwardCompatibleJsonParsingAndSyntheticCoverageFixturesHold() {
        val rawRecordJson = JSONObject()
            .put("id", "legacy-garmin-row-1")
            .put("user_id", accountA)
            .put("title", "Parsed Legacy Run")
            .put("activity_type", "running")
            .put("source", "garmin")
            .put("started_at", "2026-11-15T06:30:00Z")
            .put("distance_m", 9100.0)
            .put("duration_s", 2750.0)
            .put("moving_time_s", 2730.0)
            .put("avg_pace_sec_per_km", 300.0)
            .put("avg_hr", 154.0)
            .put("elevation_gain_m", 32.0)
            .toString()
        val parsedRecord = ActivityRecord.parse(rawRecordJson, accountA)

        val detailJson = JSONObject()
            .put("user_id", accountA)
            .put("activity_id", "legacy-garmin-row-1")
            .put("fetched_at", 1_795_000_000_000L)
            .put("samples", JSONArray().apply {
                put(JSONObject().put("activity_id", "legacy-garmin-row-1").put("sample_index", 0).put("elapsed_s", 0.0).put("pace_sec_per_km", 300.0).put("heart_rate", 150.0))
                put(JSONObject().put("activity_id", "legacy-garmin-row-1").put("sample_index", 1).put("elapsed_s", 30.0).put("pace_sec_per_km", 298.0).put("heart_rate", 155.0))
            })
            .put("laps", JSONArray())
        val parsedDetail = ActivityDetail.parse(detailJson, accountA, "legacy-garmin-row-1")

        val canonical = Engine15CanonicalActivityProjector.project(
            listOf(RawActivityObservation(accountA, parsedRecord, parsedDetail))
        ).activities.single()

        assertEquals(AthleteDiscipline.RUN, canonical.discipline)
        assertEquals(9100.0, canonical.normalizedMetrics.distance!!.value, 0.001)
        assertEquals(TelemetryQualityStatus.VALID_OBSERVED, canonical.telemetryQuality.hrQuality)

        // Collections are strictly immutable.
        @Suppress("UNCHECKED_CAST")
        val comps = canonical.rawComponents as MutableList<RawSourceComponent>
        try {
            comps.clear()
            fail("Expected UnsupportedOperationException")
        } catch (_: UnsupportedOperationException) { }

        // Synthetic Track 1A F01 (CONFIRMED_ZERO) vs F02 (MISSING_UNOBSERVED) vs F03 (PARTIAL_COVERAGE).
        assertEquals(HistoryCoverageStatus.CONFIRMED_ZERO, HistoryCoverageStatus.fromRecentMileageBasis(RecentMileageBasis.CONFIRMED_ZERO))
        assertEquals(HistoryCoverageStatus.MISSING_UNOBSERVED, HistoryCoverageStatus.fromRecentMileageBasis(RecentMileageBasis.UNKNOWN))
        assertEquals(HistoryCoverageStatus.PARTIAL_COVERAGE, HistoryCoverageStatus.fromRecentMileageBasis(RecentMileageBasis.RECORDED_PARTIAL))
        assertFalse(FeatureFlags.USE_UNIFIED_ATHLETE_STATE_V1)
    }
}
