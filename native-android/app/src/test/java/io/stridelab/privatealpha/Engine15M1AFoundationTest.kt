package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class Engine15M1AFoundationTest {

    @Test fun nonFiniteAndInvalidDomainSiQuantitiesAreRejected() {
        val nonFinite = listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)
        for (bad in nonFinite) {
            assertThrows(IllegalArgumentException::class.java) { Meters.of(bad) }
            assertThrows(IllegalArgumentException::class.java) { Meters.fromKilometers(bad) }
            assertThrows(IllegalArgumentException::class.java) { Seconds.of(bad) }
            assertThrows(IllegalArgumentException::class.java) { Seconds.fromMinutes(bad) }
            assertThrows(IllegalArgumentException::class.java) { Kilograms.of(bad) }
            assertThrows(IllegalArgumentException::class.java) { MetersPerSecond.of(bad) }
            assertThrows(IllegalArgumentException::class.java) { MetersPerSecond.fromPaceSecondsPerKm(bad) }
            assertThrows(IllegalArgumentException::class.java) { BeatsPerSecond.of(bad) }
            assertThrows(IllegalArgumentException::class.java) { BeatsPerSecond.fromBpm(bad) }
            assertThrows(IllegalArgumentException::class.java) { DimensionlessRatio.of(bad) }
        }
        assertThrows(IllegalArgumentException::class.java) { Meters.of(-0.01) }
        assertThrows(IllegalArgumentException::class.java) { Seconds.of(-1.0) }
        assertThrows(IllegalArgumentException::class.java) { Kilograms.of(-5.0) }
        assertThrows(IllegalArgumentException::class.java) { MetersPerSecond.of(0.0) }
        assertThrows(IllegalArgumentException::class.java) { MetersPerSecond.of(-2.0) }
        assertThrows(IllegalArgumentException::class.java) { BeatsPerSecond.of(0.0) }
        assertThrows(IllegalArgumentException::class.java) { BeatsPerSecond.fromBpm(19.0) }
        assertThrows(IllegalArgumentException::class.java) { BeatsPerSecond.fromBpm(261.0) }
    }

    @Test fun negativeZeroCanonicalizesIdenticallyToPositiveZero() {
        val negZeroMeters = Meters.of(-0.0)
        val posZeroMeters = Meters.of(0.0)
        assertEquals(posZeroMeters, negZeroMeters)
        assertEquals("0", negZeroMeters.canonicalDecimalString())
        assertEquals(posZeroMeters.canonicalToken(), negZeroMeters.canonicalToken())

        val negZeroSeconds = Seconds.of(-0.0)
        assertEquals(Seconds.ZERO, negZeroSeconds)
        assertEquals("0", negZeroSeconds.canonicalDecimalString())

        val negZeroRatio = DimensionlessRatio.of(-0.0)
        assertEquals("0", negZeroRatio.canonicalDecimalString())
    }

    @Test fun beliefStateVariantsEnforceAuthorityBoundariesAndRejectImplicitPromotion() {
        val unknown = BeliefState.Unknown("NO_RECENT_HISTORY")
        val bounded = BeliefState.Bounded(
            lower = Meters.fromKilometers(20.0),
            upper = Meters.fromKilometers(30.0),
            authority = EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL,
            uncertaintyKind = UncertaintyKind.COACHING_GUIDANCE_BAND
        )
        val prior = BeliefState.PriorOnly(
            prior = BeatsPerSecond.fromBpm(180.0),
            priorSource = "TANAKA_2001",
            authorizesZones = false
        )
        val estimated = BeliefState.Estimated(
            estimate = Meters.fromKilometers(42.0),
            authority = EvidenceAuthorityClass.COACH_OR_RACE_VERIFIED,
            uncertaintyKind = UncertaintyKind.COACHING_GUIDANCE_BAND,
            guidanceSpreadFactor = 1.0
        )
        val stale = BeliefState.Stale(
            lastValue = Meters.fromKilometers(35.0),
            ageDays = 190,
            authority = EvidenceAuthorityClass.COACH_OR_RACE_VERIFIED
        )
        val conflicting = BeliefState.Conflicting(
            candidates = listOf(Meters.fromKilometers(40.0), Meters.fromKilometers(15.0)),
            conflictReason = "SELF_REPORT_VS_RECORDED_DISCREPANCY"
        )

        val allStates: List<BeliefState<CanonicalSiQuantity>> = listOf(
            unknown, bounded, prior, estimated, stale, conflicting
        )
        assertEquals(6, allStates.map { it.canonicalRepresentation() }.distinct().size)

        // INV-05: PriorOnly cannot authorize zones.
        assertThrows(IllegalArgumentException::class.java) {
            BeliefState.PriorOnly(BeatsPerSecond.fromBpm(180.0), "TANAKA_2001", authorizesZones = true)
        }
        // INV-02: Projected or unobserved values cannot become Estimated observed capacity.
        assertThrows(IllegalArgumentException::class.java) {
            BeliefState.Estimated(Meters.fromKilometers(50.0), EvidenceAuthorityClass.PROJECTED_NON_AUTHORITATIVE)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BeliefState.Estimated(Meters.fromKilometers(50.0), EvidenceAuthorityClass.UNOBSERVED)
        }
        // Tier A Estimated cannot claim statistical intervals.
        assertThrows(IllegalArgumentException::class.java) {
            BeliefState.Estimated(
                Meters.fromKilometers(40.0),
                EvidenceAuthorityClass.COACH_OR_RACE_VERIFIED,
                UncertaintyKind.STATISTICAL_INTERVAL_RESERVED
            )
        }
        // Bounded requires lower <= upper and at least one bound.
        assertThrows(IllegalArgumentException::class.java) {
            BeliefState.Bounded<Meters>(null, null, EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BeliefState.Bounded(Meters.of(5000.0), Meters.of(4000.0), EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL)
        }
        // Stale requires ageDays > 0; Conflicting requires >= 2 candidates.
        assertThrows(IllegalArgumentException::class.java) {
            BeliefState.Stale(Meters.of(1000.0), 0, EvidenceAuthorityClass.SENSOR_SUPPORTED)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BeliefState.Conflicting(listOf(Meters.of(1000.0)), "ONLY_ONE")
        }
    }

    @Test fun missingEvidenceNeverCollapsesToConfirmedZero() {
        assertEquals(
            HistoryCoverageStatus.MISSING_UNOBSERVED,
            HistoryCoverageStatus.fromRecentMileageBasis(RecentMileageBasis.UNKNOWN)
        )
        assertEquals(
            HistoryCoverageStatus.MISSING_UNOBSERVED,
            HistoryCoverageStatus.fromRecentMileageBasis(RecentMileageBasis.LEGACY_UNSPECIFIED)
        )
        assertEquals(
            HistoryCoverageStatus.CONFIRMED_ZERO,
            HistoryCoverageStatus.fromRecentMileageBasis(RecentMileageBasis.CONFIRMED_ZERO)
        )

        val missingSnap = AthleteStateSnapshot(
            accountId = "local-plan-11111111-1111-4111-8111-111111111111",
            evaluatedAtUtc = Instant.parse("2026-11-25T00:00:00Z"),
            disciplines = mapOf(
                AthleteDiscipline.RUN to DisciplineStateSummary(
                    discipline = AthleteDiscipline.RUN,
                    coverage = HistoryCoverageStatus.MISSING_UNOBSERVED,
                    weeklyVolumeBelief = BeliefState.Unknown("NO_HISTORY"),
                    longestSessionBelief = BeliefState.Unknown("NO_HISTORY")
                )
            )
        )
        val zeroSnap = AthleteStateSnapshot(
            accountId = "local-plan-11111111-1111-4111-8111-111111111111",
            evaluatedAtUtc = Instant.parse("2026-11-25T00:00:00Z"),
            disciplines = mapOf(
                AthleteDiscipline.RUN to DisciplineStateSummary(
                    discipline = AthleteDiscipline.RUN,
                    coverage = HistoryCoverageStatus.CONFIRMED_ZERO,
                    weeklyVolumeBelief = BeliefState.Estimated(
                        Meters.ZERO,
                        EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL,
                        UncertaintyKind.NONE
                    ),
                    longestSessionBelief = BeliefState.Estimated(
                        Meters.ZERO,
                        EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL,
                        UncertaintyKind.NONE
                    )
                )
            )
        )
        assertNotEquals(missingSnap.contentHash, zeroSnap.contentHash)
        assertNotEquals(missingSnap.snapshotId, zeroSnap.snapshotId)
    }

    @Test fun snapshotHashIsDeterministicUnderPermutationsAndIndependentOfEvaluationTimestamp() {
        val runA = DisciplineStateSummary(
            discipline = AthleteDiscipline.RUN,
            coverage = HistoryCoverageStatus.CONTINUOUS_COVERAGE,
            weeklyVolumeBelief = BeliefState.Estimated(
                Meters.fromKilometers(45.0),
                EvidenceAuthorityClass.SENSOR_SUPPORTED,
                UncertaintyKind.COACHING_GUIDANCE_BAND,
                guidanceSpreadFactor = 1.0
            ),
            longestSessionBelief = BeliefState.Estimated(
                Meters.fromKilometers(16.0),
                EvidenceAuthorityClass.SENSOR_SUPPORTED,
                UncertaintyKind.NONE
            ),
            evidenceIds = linkedSetOf("act-2", "act-1")
        )
        val strA = DisciplineStateSummary(
            discipline = AthleteDiscipline.STRENGTH,
            coverage = HistoryCoverageStatus.PARTIAL_COVERAGE,
            weeklyVolumeBelief = BeliefState.Unknown("STRENGTH_TIME_NOT_METERS"),
            longestSessionBelief = BeliefState.Unknown("STRENGTH_TIME_NOT_METERS"),
            evidenceIds = setOf("str-1")
        )

        val snap1 = AthleteStateSnapshot(
            accountId = "athlete-account-1",
            evaluatedAtUtc = Instant.parse("2026-11-25T08:00:00Z"),
            disciplines = linkedMapOf(
                AthleteDiscipline.STRENGTH to strA,
                AthleteDiscipline.RUN to runA
            ),
            evidenceIds = linkedSetOf("ev-b", "ev-a"),
            activeRestrictionIds = linkedSetOf("res-2", "res-1")
        )
        val snap2 = AthleteStateSnapshot(
            accountId = "athlete-account-1",
            evaluatedAtUtc = Instant.parse("2026-11-26T12:30:00Z"),
            disciplines = linkedMapOf(
                AthleteDiscipline.RUN to runA.let {
                    DisciplineStateSummary(
                        it.discipline, it.coverage, it.weeklyVolumeBelief, it.longestSessionBelief,
                        linkedSetOf("act-1", "act-2")
                    )
                },
                AthleteDiscipline.STRENGTH to strA
            ),
            evidenceIds = linkedSetOf("ev-a", "ev-b"),
            activeRestrictionIds = linkedSetOf("res-1", "res-2")
        )

        assertEquals(snap1.contentHash, snap2.contentHash)
        assertEquals(snap1.snapshotId, snap2.snapshotId)
        assertNotEquals(snap1, snap2) // evaluatedAtUtc differs
    }

    @Test fun meaningfulChangesInvalidateHashAndPreserveAccountAndDisciplineIsolation() {
        val baseRun = DisciplineStateSummary(
            discipline = AthleteDiscipline.RUN,
            coverage = HistoryCoverageStatus.PROVISIONAL_SELF_REPORT,
            weeklyVolumeBelief = BeliefState.Estimated(
                Meters.fromKilometers(30.0),
                EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL
            ),
            longestSessionBelief = BeliefState.Estimated(
                Meters.fromKilometers(12.0),
                EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL
            ),
            evidenceIds = setOf("sr-1")
        )
        val base = AthleteStateSnapshot(
            accountId = "owner-a",
            evaluatedAtUtc = Instant.parse("2026-11-25T00:00:00Z"),
            disciplines = mapOf(AthleteDiscipline.RUN to baseRun),
            evidenceIds = setOf("sr-1")
        )
        val diffAccount = AthleteStateSnapshot(
            accountId = "owner-b",
            evaluatedAtUtc = base.evaluatedAtUtc,
            disciplines = mapOf(AthleteDiscipline.RUN to baseRun),
            evidenceIds = setOf("sr-1")
        )
        val diffVolume = AthleteStateSnapshot(
            accountId = "owner-a",
            evaluatedAtUtc = base.evaluatedAtUtc,
            disciplines = mapOf(
                AthleteDiscipline.RUN to DisciplineStateSummary(
                    discipline = AthleteDiscipline.RUN,
                    coverage = HistoryCoverageStatus.PROVISIONAL_SELF_REPORT,
                    weeklyVolumeBelief = BeliefState.Estimated(
                        Meters.fromKilometers(30.1),
                        EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL
                    ),
                    longestSessionBelief = baseRun.longestSessionBelief,
                    evidenceIds = setOf("sr-1")
                )
            ),
            evidenceIds = setOf("sr-1")
        )
        val diffPolicy = AthleteStateSnapshot(
            accountId = "owner-a",
            evaluatedAtUtc = base.evaluatedAtUtc,
            disciplines = mapOf(AthleteDiscipline.RUN to baseRun),
            evidenceIds = setOf("sr-1"),
            policyVersion = "policy-registry-1.5.0-v2"
        )
        val extraRestriction = AthleteStateSnapshot(
            accountId = "owner-a",
            evaluatedAtUtc = base.evaluatedAtUtc,
            disciplines = mapOf(AthleteDiscipline.RUN to baseRun),
            evidenceIds = setOf("sr-1"),
            activeRestrictionIds = setOf("hold-h2-knee")
        )

        val hashes = setOf(
            base.contentHash,
            diffAccount.contentHash,
            diffVolume.contentHash,
            diffPolicy.contentHash,
            extraRestriction.contentHash
        )
        assertEquals(5, hashes.size)
    }

    @Test fun snapshotAndConflictingCollectionsCannotBeMutatedIndirectly() {
        val callerCands = mutableListOf(Meters.fromKilometers(20.0), Meters.fromKilometers(35.0))
        val conflicting = BeliefState.Conflicting(callerCands, "TEST_CONFLICT")
        callerCands.add(Meters.fromKilometers(99.0))
        assertEquals(2, conflicting.candidates.size)

        val callerDiscEv = mutableSetOf("ev-1")
        val summary = DisciplineStateSummary(
            discipline = AthleteDiscipline.RUN,
            coverage = HistoryCoverageStatus.PARTIAL_COVERAGE,
            weeklyVolumeBelief = conflicting,
            longestSessionBelief = BeliefState.Unknown("UNKNOWN"),
            evidenceIds = callerDiscEv
        )
        callerDiscEv.add("ev-mutated")
        assertEquals(setOf("ev-1"), summary.evidenceIds)

        val callerMap = mutableMapOf(AthleteDiscipline.RUN to summary)
        val callerEv = mutableSetOf("snap-ev-1")
        val callerRes = mutableSetOf("hold-1")
        val snapshot = AthleteStateSnapshot(
            accountId = "owner-immut",
            evaluatedAtUtc = Instant.parse("2026-11-25T00:00:00Z"),
            disciplines = callerMap,
            evidenceIds = callerEv,
            activeRestrictionIds = callerRes
        )
        callerMap.clear()
        callerEv.add("snap-ev-mutated")
        callerRes.clear()

        assertEquals(1, snapshot.disciplines.size)
        assertEquals(setOf("snap-ev-1"), snapshot.evidenceIds)
        assertEquals(setOf("hold-1"), snapshot.activeRestrictionIds)
    }

    @Test fun featureFlagDefaultsFalseAndBaselineGenerationRemainsIdentical() {
        assertFalse(FeatureFlags.USE_UNIFIED_ATHLETE_STATE_V1)
        val request = PlanRequest(
            goal = "Improve 10K",
            experience = 1,
            start = LocalDate.of(2026, 11, 30),
            weeks = 4,
            days = setOf(1, 2, 4, 6),
            runDays = 4,
            longDay = 6,
            minutes = 90,
            qualityCount = 1,
            weeklyKm = 32.0,
            longestKm = 12.0,
            recentMileageBasis = "SELF_REPORTED_AVERAGE",
            policyChoice = "engine15"
        )
        val fixedNow = Instant.parse("2026-11-25T00:00:00Z")
        val first = Engine15IndependentPlanGenerator.previewAutoReconciled(
            request, "owner-flag-check", "plan-flag-check", fixedNow
        )
        val second = Engine15IndependentPlanGenerator.previewAutoReconciled(
            request, "owner-flag-check", "plan-flag-check", fixedNow
        )
        assertTrue(first is Engine15IndependentPlanDecision.Candidate)
        assertEquals(first, second)
    }
}
