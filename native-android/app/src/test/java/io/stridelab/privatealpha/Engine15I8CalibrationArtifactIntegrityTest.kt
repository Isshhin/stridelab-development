package io.stridelab.privatealpha

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

/** I8 synthetic record tests: same-caller hash does not attest issuer. */
class Engine15I8CalibrationArtifactIntegrityTest {
    private val monday = LocalDate.of(2026, 11, 16)
    private val asOf = LocalDate.of(2026, 11, 17)
    private val zone = ZoneId.of("Asia/Manila")
    private val owner = "synthetic-runner"
    private val recent = asOf.minusDays(3)
    private val older = asOf.minusDays(10)

    private fun source(): Engine15IndependentVolumePlanner.Result {
        val p = PlanRequest(
            goal = "Aerobic foundation", experience = 2,
            start = monday, weeks = 8, days = (1..7).toSet(),
            runDays = 4, longDay = 7, qualityCount = 0,
            weeklyKm = 30.0, targetWeeklyKm = 35.0, longestKm = 10.0,
            returning = false, policyChoice = "engine15", trainYourWay = true,
            timeBudgetEnabled = false,
            recentMileageBasis = "SELF_REPORTED_AVERAGE"
        )
        return Engine15IndependentVolumePlanner.propose(
            Engine15AthleteBaseline.assess(p),
            Engine15IndependentVolumePlanner.Input(8, 4, 35.0, false, false)
        )
    }

    private fun baseInput(
        longDay: Int = 7,
        externalSessions: List<Engine15M6CExternalSession> = emptyList(),
        allowEasyUpperPair: Boolean = false
    ) = Engine15I9BasePlanAssemblyInput(
            interferenceReview = Engine15M6CReviewInput(
                calendar = Engine15M6CalendarInput(
                    startMonday = monday,
                    availableIsoDays = if (longDay == 6)
                        setOf(1, 3, 5, 6, 7) else setOf(1, 3, 5, 7),
                    preferredLongIsoDay = longDay
                ),
                externalSessions = externalSessions,
                externalCalendarCoverageAsserted = true,
                allowEasyUpperPair = allowEasyUpperPair
            ),
            explicitBaseOnlyReview = true
        )

    private fun qualityInput(
        source: Engine15IndependentVolumePlanner.Result = source(),
        artifactBytes: ByteArray? = originalBytes()
    ): Engine15I10QualityReviewInput {
        val request = quality()
        return Engine15I10QualityReviewInput(
            request = request,
            sourceEvidence = sourceEvidence(),
            feasibilityInput = speedWindows(source, request),
            calibrationInput = Engine15I7CalibrationReviewInput(
                claim().copy(artifactSha256Claim = hex(originalBytes()))
            ),
            artifactBytes = artifactBytes
        )
    }

    private fun quality(reviewed: Boolean = true) = Engine15M5QualityPreviewRequest(
        weekNumber = 1, slotNumber = 1, family = IndependentQualityFamily.THRESHOLD,
        fitnessInput = IndependentQualityFitnessInput(
            reportedRecentWorkSeconds = 480,
            approvedWorkCeilingSeconds = 1200,
            approvedIncreaseSeconds = 0,
            availableSessionSeconds = 3600,
            qualityAuthorized = true
        ),
        explicitlyReviewedDose = reviewed,
        reviewReference = "synthetic-caller-review"
    )

    private fun originalPlan() = PlanSnapshot(
        id = "saved-synthetic-plan", owner = owner,
        engineVersion = ENGINE15_VERSION, policyId = ENGINE15_POLICY,
        createdAt = Instant.EPOCH,
        request = PlanRequest(policyChoice = "engine15"),
        sessions = listOf(
            PlannedSession(
                id = "session-older", date = older,
                discipline = "run", title = "Older synthetic quality",
                minutes = 40, distanceKm = 6.0, kind = "quality",
                quality = true, status = "completed",
                linkedActivityId = "record-older"
            ),
            PlannedSession(
                id = "session-recent", date = recent,
                discipline = "run", title = "Recent synthetic quality",
                minutes = 40, distanceKm = 6.0, kind = "quality",
                quality = true, status = "completed",
                linkedActivityId = "record-recent"
            )
        ),
        explanations = emptyList(), status = "approved"
    )

    private fun activity(id: String, on: LocalDate, source: String) = ActivityRecord(
        id = id, title = "Synthetic run", type = "running", source = source,
        startedAt = on.atTime(7, 0).atZone(zone).toInstant(),
        distanceM = 6000.0, durationS = 2400.0,
        movingTimeS = null, recordedPace = null,
        averageHr = null, elevationM = null
    )

    private fun detail(id: String): ActivityDetail {
        val times = (0..24).map { 300.0 + it * 10.0 } +
            (0..24).map { 900.0 + it * 10.0 }
        return ActivityDetail(
            activityId = id,
            samples = times.mapIndexed { i, time ->
                ActivitySample(i, time, 240.0, 150.0, null, null, null)
            },
            laps = listOf(
                ActivityLap(0, 1000.0, 240.0, 240.0, 150.0, 300.0),
                ActivityLap(1, 300.0, 120.0, null, null, 600.0),
                ActivityLap(2, 1000.0, 240.0, 240.0, 150.0, 900.0)
            ),
            fetchedAt = 0L
        )
    }

    private fun sourceEvidence(
        badPace: Boolean = false
    ): Engine15I3QualityDurationInput {
        fun entry(id: String, session: String, date: LocalDate) =
            Engine15I3LinkedQualityObservation(
                lapClaim = IndependentQualityLapClaim(
                    evidence = IndependentQualitySegmentEvidence(
                        ownerId = owner, sourceRecordId = id,
                        sessionId = session, performedOn = date,
                        family = IndependentQualityFamily.THRESHOLD,
                        origin = IndependentQualityEvidenceOrigin.DEVICE_SEGMENTS_ASSERTED,
                        sourceSessionMatchAsserted = true,
                        performedWorkSegmentsSeconds = listOf(240, 240)
                    ),
                    recordedWorkLapIndices = listOf(0, 2)
                ),
                detail = detail(id).let { d ->
                    if (badPace && id == "record-recent")
                        d.copy(samples = d.samples.map { it.copy(pace = null) })
                    else d
                }
            )
        return Engine15I3QualityDurationInput(
            assertedOwnerId = owner, asOf = asOf, zone = zone,
            plans = listOf(originalPlan()),
            sourceActivities = listOf(
                activity("record-recent", recent, "garmin"),
                activity("record-older", older, "fit")
            ),
            observations = listOf(
                entry("record-recent", "session-recent", recent),
                entry("record-older", "session-older", older)
            )
        )
    }

    private fun speedWindows(
        s: Engine15IndependentVolumePlanner.Result = source(),
        q: Engine15M5QualityPreviewRequest = quality()
    ): Engine15I5DistanceFeasibilityInput {
        val preview = (
            Engine15M5QualityPreviewComposer.compose(s, listOf(q))
                as Engine15M5QualityDecision.Candidate
            ).qualityPreviews.single()
        val phases = buildList<WorkoutStep> {
            for (part in preview.workout.elements) when (part) {
                is IndependentWorkoutElement.Step ->
                    repeat(part.value.repetitions) { add(part.value) }
                is IndependentWorkoutElement.Intervals ->
                    repeat(part.value.repetitions) { index ->
                        add(part.value.work)
                        if (index < part.value.repetitions - 1) add(part.value.recovery)
                    }
            }
        }
        return Engine15I5DistanceFeasibilityInput(
            calendar = Engine15M6QualitySpacingInput(
                calendar = Engine15M6CalendarInput(
                    startMonday = monday,
                    availableIsoDays = setOf(1, 3, 5, 7),
                    preferredLongIsoDay = 7
                )
            ),
            phaseSpeedWindows = phases.mapIndexed { index, step ->
                Engine15I5PhaseSpeedWindow(
                    phaseIndex = index, purpose = step.purpose, target = step.target,
                    minimumMetersPerSecond =
                        if (step.purpose == WorkoutPurpose.WORK) 2.8 else 2.4,
                    maximumMetersPerSecond =
                        if (step.purpose == WorkoutPurpose.WORK) 5.0 else 4.2
                )
            },
            assertedExternalSpeedReviewReference = "synthetic-speed-review"
        )
    }

    private fun claim() = Engine15I7CalibrationClaim(
        assertedOwnerId = owner,
        family = IndependentQualityFamily.THRESHOLD,
        measuredOn = asOf.minusDays(2),
        origin = Engine15I7CalibrationOrigin.STRUCTURED_FIELD_TEST_ASSERTED,
        subjectSpecificSourceReference = "synthetic-source-ref",
        testingProtocolReference = "synthetic-test-protocol",
        artifactSha256Claim = "0".repeat(64),
        claimedThresholdHrBpm = 172,
        claimedThresholdPaceSecondsPerKm = 240.0,
        assertedRestingHrBpm = 52,
        assertedMaximumHrBpm = 198
    )

    private fun hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }

    private fun originalBytes() =
        Engine15I8CalibrationArtifactIntegrity.canonicalRecordBytes(claim())

    private fun review(
        bytes: ByteArray? = originalBytes(),
        candidate: Engine15I7CalibrationClaim = claim().copy(
            artifactSha256Claim = hex(originalBytes())
        ),
        q: Engine15M5QualityPreviewRequest = quality(),
        evidence: Engine15I3QualityDurationInput = sourceEvidence()
    ): Engine15I8ArtifactIntegrityDecision {
        val s = source()
        return Engine15I8CalibrationArtifactIntegrity.inspect(
            s, q, evidence, speedWindows(s, quality()),
            Engine15I7CalibrationReviewInput(candidate), bytes
        )
    }

    private fun accepted(
        bytes: ByteArray? = originalBytes(),
        candidate: Engine15I7CalibrationClaim = claim().copy(
            artifactSha256Claim = hex(originalBytes())
        )
    ): Engine15I8ArtifactIntegrityDecision.ArtifactConsistentForExternalReview {
        val result = review(bytes, candidate)
        assertTrue("Expected I8 byte-only review, got $result",
            result is Engine15I8ArtifactIntegrityDecision.ArtifactConsistentForExternalReview)
        return result as Engine15I8ArtifactIntegrityDecision.ArtifactConsistentForExternalReview
    }

    private fun deferred(
        code: String,
        bytes: ByteArray? = originalBytes(),
        candidate: Engine15I7CalibrationClaim = claim().copy(
            artifactSha256Claim = hex(originalBytes()),
        ),
        q: Engine15M5QualityPreviewRequest = quality(),
        evidence: Engine15I3QualityDurationInput = sourceEvidence()
    ) {
        val result = review(bytes, candidate, q, evidence)
        assertTrue("Expected I8 $code, got $result",
            result is Engine15I8ArtifactIntegrityDecision.Deferred)
        assertEquals(code,
            (result as Engine15I8ArtifactIntegrityDecision.Deferred).reasonCode)
    }

    @Test fun exactShaAndCanonicalFieldsStillCannotAuthenticateIssuerOrDose() {
        val outcome = accepted()
        assertEquals(hex(originalBytes()), outcome.matchedSha256)
        assertEquals(Engine15I8CalibrationArtifactIntegrity.SCHEMA,
            outcome.schemaVersion)
        assertTrue(outcome.fieldsAgreeWithClaim)
        assertTrue(outcome.bytesMatchClaimedFingerprint)
        assertFalse(outcome.sourceOwnerAuthenticated)
        assertFalse(outcome.externallyAuthenticated)
        assertFalse(outcome.protocolVerified)
        assertFalse(outcome.thresholdIntensityVerified)
        assertFalse(outcome.doseAuthorized)
        assertFalse(outcome.athleteResponseVerified)
        assertFalse(outcome.executable)
        assertFalse(outcome.authorizedForProduction)
        assertFalse(outcome.originalI7.calibrationArtifactVerified)
        assertFalse(outcome.originalI7.thresholdIntensityVerified)

        val s = source()
        val assembled = Engine15I10ReadOnlyCandidateAssembly.assemble(
            Engine15I10ReadOnlyCandidateInput(s, baseInput(), qualityInput(s))
        )
        assertTrue(assembled.toString(),
            assembled is Engine15I10ReadOnlyCandidateDecision.Candidate)
        val candidate = assembled as Engine15I10ReadOnlyCandidateDecision.Candidate
        val alternative = requireNotNull(candidate.qualityAlternative)
        val opportunity = outcome.originalI7.originalI6.originalI5.selectedOpportunity
        assertEquals(opportunity, alternative)
        assertEquals(opportunity.proposedDate, alternative.proposedDate)
        assertEquals(opportunity.existingRun.slot.slotNumber,
            alternative.existingRun.slot.slotNumber)
        assertEquals(opportunity.existingRun.distanceKm,
            alternative.existingRun.distanceKm, 0.0)
        assertFalse(candidate.qualityIncludedInBaseMileage)
        assertFalse(alternative.scheduledQualityWorkout)
        assertFalse(candidate.executable)
        assertFalse(candidate.authorizedForProduction)
    }

    @Test fun i10PropagatesI8DeferralAndRejectsBaseSlotMismatch() {
        val s = source()
        val deferred = Engine15I10ReadOnlyCandidateAssembly.assemble(
            Engine15I10ReadOnlyCandidateInput(
                s, baseInput(), qualityInput(s, artifactBytes = null)
            )
        )
        assertTrue(deferred.toString(),
            deferred is Engine15I10ReadOnlyCandidateDecision.Deferred)
        assertEquals("QUALITY_ARTIFACT_BYTES_MISSING",
            (deferred as Engine15I10ReadOnlyCandidateDecision.Deferred).reasonCode)

        val mismatch = Engine15I10ReadOnlyCandidateAssembly.assemble(
            Engine15I10ReadOnlyCandidateInput(
                s, baseInput(longDay = 6), qualityInput(s)
            )
        )
        assertTrue(mismatch.toString(),
            mismatch is Engine15I10ReadOnlyCandidateDecision.Deferred)
        assertEquals("BASE_QUALITY_CALENDAR_MISMATCH",
            (mismatch as Engine15I10ReadOnlyCandidateDecision.Deferred).reasonCode)
    }

    @Test fun i10CanReturnAnExplicitBaseOnlyCandidateWithoutQuality() {
        val result = Engine15I10ReadOnlyCandidateAssembly.assemble(
            Engine15I10ReadOnlyCandidateInput(source(), baseInput())
        )
        assertTrue(result.toString(),
            result is Engine15I10ReadOnlyCandidateDecision.Candidate)
        val candidate = result as Engine15I10ReadOnlyCandidateDecision.Candidate
        assertNull(candidate.qualityReview)
        assertNull(candidate.qualityAlternative)
        assertTrue("BASE_ONLY_PREVIEW" in candidate.ruleIds)
        assertFalse(candidate.qualityIncludedInBaseMileage)
        assertFalse(candidate.qualityScheduled)
        assertFalse(candidate.executable)
        assertFalse(candidate.authorizedForProduction)
    }

    @Test fun i10RejectsQualityOnOrTooCloseToAssertedExternalHardSessions() {
        val s = source()
        val sameDayUpper = Engine15M6CExternalSession(
            "same-day-upper", monday, Engine15M6CDiscipline.STRENGTH,
            Engine15M6CLoad.UPPER_BODY
        )
        val sameDay = Engine15I10ReadOnlyCandidateAssembly.assemble(
            Engine15I10ReadOnlyCandidateInput(
                s, baseInput(externalSessions = listOf(sameDayUpper),
                    allowEasyUpperPair = true), qualityInput(s)
            )
        )
        assertTrue(sameDay.toString(),
            sameDay is Engine15I10ReadOnlyCandidateDecision.Deferred)
        assertEquals("QUALITY_EXTERNAL_SAME_DAY_CONFLICT",
            (sameDay as Engine15I10ReadOnlyCandidateDecision.Deferred).reasonCode)

        val nextDayHard = Engine15M6CExternalSession(
            "next-day-hard", monday.plusDays(1), Engine15M6CDiscipline.STRENGTH,
            Engine15M6CLoad.LOWER_BODY
        )
        val tooClose = Engine15I10ReadOnlyCandidateAssembly.assemble(
            Engine15I10ReadOnlyCandidateInput(
                s, baseInput(externalSessions = listOf(nextDayHard)), qualityInput(s)
            )
        )
        assertTrue(tooClose.toString(),
            tooClose is Engine15I10ReadOnlyCandidateDecision.Deferred)
        assertEquals("QUALITY_EXTERNAL_HARD_TOO_CLOSE",
            (tooClose as Engine15I10ReadOnlyCandidateDecision.Deferred).reasonCode)
    }

    @Test fun missingAndOversizedBytesCannotBeReadAsValidArtifacts() {
        deferred("ARTIFACT_BYTES_MISSING", bytes = null)
        deferred("ARTIFACT_BYTES_MISSING", bytes = byteArrayOf())
        deferred("ARTIFACT_BYTES_TOO_LARGE", bytes = ByteArray(16_385) { 65 })
    }

    @Test fun changedBytesWithoutMatchingDigestMustBeRejectedFirst() {
        val altered = originalBytes().copyOf()
        altered[altered.lastIndex - 1] = '9'.code.toByte()
        deferred("ARTIFACT_SHA256_MISMATCH", bytes = altered)
    }

    @Test fun invalidStrictUtf8CannotBeDecodedEvenWithMatchingDigest() {
        val raw = byteArrayOf(0xC3.toByte(), 0x28.toByte())
        deferred("ARTIFACT_UTF8_INVALID", bytes = raw,
            candidate = claim().copy(artifactSha256Claim = hex(raw)))
    }

    @Test fun canonicalLineEndingsAndNoBomAreRequiredEvenIfHashMatches() {
        val crlf = String(originalBytes(), StandardCharsets.UTF_8)
            .replace("\n", "\r\n").toByteArray(StandardCharsets.UTF_8)
        deferred("ARTIFACT_SCHEMA_NOT_CANONICAL", bytes = crlf,
            candidate = claim().copy(artifactSha256Claim = hex(crlf)))
        val noTrailingNewline = originalBytes().dropLast(1).toByteArray()
        deferred("ARTIFACT_SCHEMA_NOT_CANONICAL", bytes = noTrailingNewline,
            candidate = claim().copy(artifactSha256Claim = hex(noTrailingNewline)))
    }

    @Test fun missingExtraReorderedOrDuplicateKeysFailWithMatchedHash() {
        val content = String(originalBytes(), StandardCharsets.UTF_8)
        val lines = content.removeSuffix("\n").split("\n")
        val cases = listOf(
            (lines.dropLast(1).joinToString("\n") + "\n"),
            (lines + "unknown=extra").joinToString("\n") + "\n",
            (lines.take(3) + lines[3] + lines.drop(3)).joinToString("\n") + "\n",
            (lines.take(2) + lines[3] + lines[2] + lines.drop(4)).joinToString("\n") + "\n"
        )
        cases.forEach { malformed ->
            val bytes = malformed.toByteArray(StandardCharsets.UTF_8)
            deferred("ARTIFACT_SCHEMA_INVALID", bytes = bytes,
                candidate = claim().copy(artifactSha256Claim = hex(bytes)))
        }
    }

    @Test fun metadataContradictionCannotBeHiddenByRehashingChangedRecord() {
        val alteredClaims = listOf(
            claim().copy(assertedOwnerId = "other-owner"),
            claim().copy(family = IndependentQualityFamily.TEMPO),
            claim().copy(measuredOn = asOf.minusDays(1)),
            claim().copy(origin = Engine15I7CalibrationOrigin.LAB_TEST_ASSERTED),
            claim().copy(subjectSpecificSourceReference = "different-source"),
            claim().copy(testingProtocolReference = "different-protocol"),
            claim().copy(claimedThresholdHrBpm = 173),
            claim().copy(claimedThresholdPaceSecondsPerKm = 245.0),
            claim().copy(assertedRestingHrBpm = 50),
            claim().copy(assertedMaximumHrBpm = 200)
        )
        alteredClaims.forEach { variant ->
            val altered = Engine15I8CalibrationArtifactIntegrity.canonicalRecordBytes(
                variant
            )
            deferred("ARTIFACT_FIELDS_CONTRADICT_CLAIM",
                bytes = altered,
                candidate = claim().copy(artifactSha256Claim = hex(altered)))
        }
    }

    @Test fun invalidReferenceTokenCannotProduceUnambiguousI8Record() {
        val changed = claim().copy(
            subjectSpecificSourceReference = "synthetic ref with spaces",
            artifactSha256Claim = hex(originalBytes())
        )
        deferred("ARTIFACT_CLAIM_TOKEN_INVALID", candidate = changed)
    }

    @Test fun upstreamInvalidOriginAndBrokenSourcePaceStillDefer() {
        deferred("UPSTREAM_CALIBRATION_ORIGIN_NOT_ELIGIBLE_FOR_REVIEW",
            candidate = claim().copy(
                origin = Engine15I7CalibrationOrigin.DEVICE_ESTIMATE,
                artifactSha256Claim = hex(originalBytes())
            ))
        deferred("UPSTREAM_I6_INSUFFICIENT_WORK_LAP_PACE_COVERAGE",
            evidence = sourceEvidence(badPace = true))
        deferred("UPSTREAM_I6_UPSTREAM_I4_UPSTREAM_UPSTREAM_QUALITY_EXTERNAL_REVIEW_MISSING",
            q = quality(reviewed = false))
    }

    @Test fun replayIsDeterministicAndOriginalI7ChainRemainsUnmodified() {
        val one = accepted()
        val two = accepted()
        assertEquals(one, two)
        assertEquals(
            source().weeks.first().proposedKm,
            one.originalI7.originalI6.originalI5.originalM6B
                .baseCalendar.first().budgetKm
        )
        assertFalse(one.originalI7.originalI6.originalI5.originalI4
            .originalI3.originalM5B.executable)
    }
}
