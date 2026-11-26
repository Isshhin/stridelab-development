package io.stridelab.privatealpha

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class Engine15M1SafeguardsTest {

    private val createdAt = Instant.parse("2026-11-20T08:00:00Z")
    private val checkTime = Instant.parse("2026-11-26T12:00:00Z")

    @Test
    fun m1dSafetyRestrictionAggregationAndSnapshotIntegrationAreDeterministic() {
        val account = "local-plan-11111111-2222-3333-4444-555555555555"

        val runCap = AthleteSafetyRestriction(
            restrictionId = "rest-achilles-vol",
            accountId = account,
            kind = SafetyRestrictionKind.MAX_WEEKLY_VOLUME_CAP,
            disciplines = setOf(AthleteDiscipline.RUN),
            severity = SafetyRestrictionSeverity.CAP_LIMITED,
            provenance = SafetyRestrictionProvenance.COACH_GUIDED_RETURN,
            maxWeeklyVolumeMeters = Meters.fromKilometers(35.0),
            maxSessionIntensityCap = SafetyIntensityCap.NO_HIGH_INTENSITY_Z3,
            note = "Return from mild Achilles soreness",
            createdAtUtc = createdAt
        )
        val globalMedicalRest = AthleteSafetyRestriction(
            restrictionId = "rest-medical-easy",
            accountId = account,
            kind = SafetyRestrictionKind.MAX_INTENSITY_CAP,
            disciplines = emptySet(), // All disciplines
            severity = SafetyRestrictionSeverity.CAP_LIMITED,
            provenance = SafetyRestrictionProvenance.MEDICAL_CLEARANCE_REQUIRED,
            maxWeeklyVolumeMeters = Meters.fromKilometers(20.0),
            maxSessionIntensityCap = SafetyIntensityCap.EASY_AEROBIC_ONLY,
            note = "Physician aerobic-only window",
            createdAtUtc = createdAt,
            expiresAtUtc = Instant.parse("2026-12-01T00:00:00Z")
        )
        val clearedOldBlock = AthleteSafetyRestriction(
            restrictionId = "rest-old-block",
            accountId = account,
            kind = SafetyRestrictionKind.MEDICAL_OR_REST_OVERRIDE,
            disciplines = setOf(AthleteDiscipline.RUN),
            severity = SafetyRestrictionSeverity.HARD_BLOCK,
            createdAtUtc = createdAt
        ).clear(atUtc = Instant.parse("2026-11-22T09:00:00Z"), reason = "Cleared after pain-free walk test")

        val set = AthleteSafetyRestrictionSet(account, listOf(runCap, globalMedicalRest, clearedOldBlock))

        assertFalse(set.hasHardBlock(AthleteDiscipline.RUN, checkTime))
        assertEquals(Meters.fromKilometers(20.0), set.effectiveMaxWeeklyVolumeCap(AthleteDiscipline.RUN, checkTime))
        assertEquals(SafetyIntensityCap.EASY_AEROBIC_ONLY, set.effectiveIntensityCap(AthleteDiscipline.RUN, checkTime))
        assertEquals(SafetyIntensityCap.EASY_AEROBIC_ONLY, set.effectiveIntensityCap(AthleteDiscipline.STRENGTH, checkTime))
        assertEquals(setOf("rest-achilles-vol", "rest-medical-easy"), set.activeRestrictionIdsAt(checkTime))

        // After globalMedicalRest expires, runCap (35 km, NO_HIGH_INTENSITY_Z3) remains active.
        val afterExpiry = Instant.parse("2026-12-02T00:00:00Z")
        assertEquals(Meters.fromKilometers(35.0), set.effectiveMaxWeeklyVolumeCap(AthleteDiscipline.RUN, afterExpiry))
        assertEquals(SafetyIntensityCap.NO_HIGH_INTENSITY_Z3, set.effectiveIntensityCap(AthleteDiscipline.RUN, afterExpiry))

        // Integration into AthleteStateSnapshot deterministically changes snapshot identity.
        val unrestrictedSnap = AthleteStateSnapshot(
            accountId = account,
            evaluatedAtUtc = checkTime,
            disciplines = emptyMap(),
            activeRestrictionIds = emptySet()
        )
        val restrictedSnap = AthleteStateSnapshot(
            accountId = account,
            evaluatedAtUtc = checkTime,
            disciplines = emptyMap(),
            activeRestrictionIds = set.activeRestrictionIdsAt(checkTime)
        )
        assertNotEquals(unrestrictedSnap.contentHash, restrictedSnap.contentHash)
        assertEquals(setOf("rest-achilles-vol", "rest-medical-easy"), restrictedSnap.activeRestrictionIds)
    }

    @Test
    fun m1dOfflineOwnerRoundTripAndAuditClearWorkWithoutAccount() {
        val backend = InMemoryM1RecordBackend()
        val localStore = Engine15M1LocalStore(backend)
        val localOwner = "local-plan-aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"

        val resolved = localStore.resolveOwner(account = null, localPlanOwner = localOwner)
        assertEquals(localOwner, resolved)

        val restriction = AthleteSafetyRestriction(
            restrictionId = "rest-calf-pain",
            accountId = resolved,
            kind = SafetyRestrictionKind.INJURY_OR_PAIN,
            disciplines = setOf(AthleteDiscipline.RUN, AthleteDiscipline.CONDITIONING),
            severity = SafetyRestrictionSeverity.HARD_BLOCK,
            note = "Right soleus strain",
            createdAtUtc = createdAt
        )
        localStore.saveRestriction(resolved, restriction, 1_795_700_000_000L)

        // Verify local-only kind = "workout" and zero outbox interactions.
        assertEquals(listOf("workout"), backend.recordedKinds)
        assertEquals(0, backend.outboxWriteCount)

        val loaded = localStore.loadRestrictions(resolved)
        assertFalse(loaded.failClosedSafetyActive)
        assertTrue(loaded.restrictions.hasHardBlock(AthleteDiscipline.RUN, checkTime))
        assertFalse(loaded.restrictions.hasHardBlock(AthleteDiscipline.STRENGTH, checkTime))

        // Clearing updates in place with audit metadata rather than erasing the record.
        val cleared = localStore.clearRestriction(
            owner = resolved,
            restrictionId = "rest-calf-pain",
            clearedAtUtc = checkTime,
            reason = "Physio cleared gradual return",
            nowMs = 1_795_705_000_000L
        )
        assertNotNull(cleared)
        assertTrue(cleared!!.isCleared)
        val reloaded = localStore.loadRestrictions(resolved)
        assertEquals(1, reloaded.restrictions.restrictions.size)
        assertFalse(reloaded.restrictions.hasHardBlock(AthleteDiscipline.RUN, checkTime))
    }

    @Test
    fun m1eVersioningHandlesUnknownFieldsOlderPoliciesAndFutureSchemasGracefully() {
        val account = "athlete-123"
        val r = AthleteSafetyRestriction(
            restrictionId = "rest-v0",
            accountId = account,
            kind = SafetyRestrictionKind.RETURN_TO_RUNNING,
            disciplines = setOf(AthleteDiscipline.RUN),
            severity = SafetyRestrictionSeverity.CAP_LIMITED,
            maxWeeklyVolumeMeters = Meters.fromKilometers(18.0),
            maxSessionIntensityCap = SafetyIntensityCap.EASY_AEROBIC_ONLY,
            createdAtUtc = createdAt,
            policyVersion = "policy-registry-1.5.0-v1.0-legacy"
        )
        val json = M1SafetyRestrictionCodec.encode(r)
        // Simulate newer forward-compatible extra fields added in a later client.
        json.put("futureClinicianTag", "return-phase-1")
        // Recompute nothing; contentHash still matches canonical fields and extra keys are ignored.
        val outcome = M1SafetyRestrictionCodec.decode(json.toString(), expectedAccountId = account)
        assertTrue(outcome is M1DecodeOutcome.Decoded)
        val decoded = outcome as M1DecodeOutcome.Decoded
        assertEquals(M1CompatibilityStatus.OLDER_POLICY_COMPATIBLE, decoded.compatibility)
        assertEquals(r, decoded.value)
        assertEquals(Meters.fromKilometers(18.0), decoded.value.maxWeeklyVolumeMeters)

        // Simulate future schemaVersion (e.g. v99 written by a newer release) -> must not crash.
        val futureJson = JSONObject(json.toString()).put("schemaVersion", 99)
        val futureOutcome = M1SafetyRestrictionCodec.decode(futureJson.toString(), expectedAccountId = account)
        assertTrue(futureOutcome is M1DecodeOutcome.UnsupportedFutureSchema)
        assertEquals(99, (futureOutcome as M1DecodeOutcome.UnsupportedFutureSchema).schemaVersion)
    }

    @Test
    fun m1fCorruptDerivedAssessmentRebuildsSafelyWithoutTouchingOrDroppingRestrictions() {
        val backend = InMemoryM1RecordBackend()
        val store = Engine15M1LocalStore(backend)
        val owner = "local-plan-99999999-aaaa-4bbb-8ccc-dddddddddddd"

        // Save one valid safety restriction + one derived assessment cache.
        val validRestriction = AthleteSafetyRestriction(
            restrictionId = "rest-valid-knee",
            accountId = owner,
            kind = SafetyRestrictionKind.MAX_WEEKLY_VOLUME_CAP,
            disciplines = setOf(AthleteDiscipline.RUN),
            severity = SafetyRestrictionSeverity.CAP_LIMITED,
            maxWeeklyVolumeMeters = Meters.fromKilometers(25.0),
            createdAtUtc = createdAt
        )
        store.saveRestriction(owner, validRestriction, 1_795_700_000_000L)

        val snap = AthleteStateSnapshot(
            accountId = owner,
            evaluatedAtUtc = checkTime,
            disciplines = mapOf(
                AthleteDiscipline.RUN to DisciplineStateSummary(
                    discipline = AthleteDiscipline.RUN,
                    coverage = HistoryCoverageStatus.PARTIAL_COVERAGE,
                    weeklyVolumeBelief = BeliefState.Estimated(
                        estimate = Meters.fromKilometers(24.0),
                        authority = EvidenceAuthorityClass.SENSOR_SUPPORTED
                    ),
                    longestSessionBelief = BeliefState.Estimated(
                        estimate = Meters.fromKilometers(12.0),
                        authority = EvidenceAuthorityClass.SENSOR_SUPPORTED
                    ),
                    evidenceIds = setOf("act-1")
                )
            ),
            activeRestrictionIds = setOf("rest-valid-knee")
        )
        store.saveDerivedAssessment(owner, M1AssessmentRecord.fromSnapshot(snap), 1_795_700_000_000L)

        val loadedAssessment = store.loadDerivedAssessment(owner)
        assertTrue(loadedAssessment is M1AssessmentLoadResult.Valid)
        assertEquals(snap.snapshotId, (loadedAssessment as M1AssessmentLoadResult.Valid).record.snapshotId)

        // Corrupt only the derived assessment cache payload -> fails safely to NeedsRebuild.
        backend.injectRawForTest(owner, Engine15M1LocalStore.assessmentRecordId(owner), "{broken-json")
        val rebuildResult = store.loadDerivedAssessment(owner)
        assertTrue(rebuildResult is M1AssessmentLoadResult.NeedsRebuild)
        assertTrue((rebuildResult as M1AssessmentLoadResult.NeedsRebuild).corruptDetected)

        // Restriction remains intact and valid.
        val restrictionsAfterAssessmentCorruption = store.loadRestrictions(owner)
        assertFalse(restrictionsAfterAssessmentCorruption.failClosedSafetyActive)
        assertEquals(1, restrictionsAfterAssessmentCorruption.restrictions.restrictions.size)

        // Inject a corrupt restriction row + future-schema restriction row -> retained, never wiped!
        backend.injectRawForTest(owner, "m1-restriction-corruptrow", "{not-valid-json")
        val futurePayload = JSONObject(M1SafetyRestrictionCodec.encode(validRestriction).toString())
            .put("schemaVersion", 42)
            .toString()
        backend.injectRawForTest(owner, "m1-restriction-futurerow", futurePayload)

        val loadWithCorrupt = store.loadRestrictions(owner)
        assertTrue(loadWithCorrupt.failClosedSafetyActive)
        assertEquals(listOf("m1-restriction-corruptrow"), loadWithCorrupt.corruptRecordIds)
        assertEquals(listOf("m1-restriction-futurerow"), loadWithCorrupt.unsupportedSchemaRecordIds)
        assertEquals(1, loadWithCorrupt.restrictions.restrictions.size)
        // Confirm all 3 restriction rows remain retained in storage (never deleted silently).
        assertEquals(3, backend.rawRowCount(owner, Engine15M1LocalStore.RESTRICTION_PREFIX))
        assertEquals(0, backend.outboxWriteCount)
    }
}
