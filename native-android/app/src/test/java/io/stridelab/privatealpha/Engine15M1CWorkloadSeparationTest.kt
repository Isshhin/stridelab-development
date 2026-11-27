package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * M1C Observed vs. Projected Workload Separation tests (INV-02, INV-03, INV-04, INV-09).
 * Covers all 21 required regression scenarios, GAP-02 reproduction vs. M1C remediation,
 * and clearly labeled deterministic synthetic Track 1A fixtures.
 */
class Engine15M1CWorkloadSeparationTest {

    private val accountA = "owner-m1c-alpha"
    private val accountB = "owner-m1c-beta"
    private val utc = ZoneOffset.UTC
    private val asOfDate = LocalDate.of(2026, 11, 25)
    private val asOfInstant = asOfDate.atTime(12, 0).toInstant(utc)

    private fun syntheticRunObs(
        accountId: String = accountA,
        id: String,
        date: LocalDate,
        hourUtc: Int = 7,
        distanceM: Double? = 10000.0,
        durationS: Double? = 3000.0,
        source: String = "garmin",
        type: String = "running",
        corroborationToken: String? = null,
        sha256: String? = null
    ): RawActivityObservation = RawActivityObservation(
        accountId = accountId,
        record = ActivityRecord(
            id = id,
            title = "Synthetic $id",
            type = type,
            source = source,
            startedAt = date.atTime(hourUtc, 0).toInstant(utc),
            distanceM = distanceM,
            durationS = durationS,
            movingTimeS = durationS,
            recordedPace = if (distanceM != null && durationS != null && distanceM > 0) durationS / (distanceM / 1000.0) else null,
            averageHr = 150.0,
            elevationM = 20.0,
            maximumHr = 168.0
        ),
        sourceIdentity = ImportedSourceIdentity(accountId, source, externalId = id, sha256 = sha256),
        corroborationToken = corroborationToken
    )

    @Test fun plannedOnlyAndFutureProjectedWorkoutsNeverEnterObservedWorkload() {
        val futureDate = asOfDate.plusDays(3)
        val approvedPlan = PlanSnapshot(
            id = "plan-approved-1",
            owner = accountA,
            engineVersion = ENGINE15_VERSION,
            policyId = ENGINE15_POLICY,
            createdAt = asOfInstant,
            request = PlanRequest(policyChoice = "engine15"),
            sessions = listOf(
                PlannedSession("past-planned-only", asOfDate.minusDays(5), "run", "Planned Long", 90, 18.0, "long", status = "planned"),
                PlannedSession("future-approved-1", futureDate, "run", "Future Tempo", 50, 10.0, "tempo", status = "planned")
            ),
            explanations = emptyList(),
            status = "approved"
        )
        val draftPlan = approvedPlan.copy(
            id = "plan-draft-1",
            status = "draft",
            sessions = listOf(
                PlannedSession("future-draft-1", futureDate, "run", "Draft Long", 110, 22.0, "long", status = "planned")
            )
        )

        val observed = Engine15ObservedWorkloadAssembler.assemble(
            accountId = accountA,
            asOfDate = asOfDate,
            zoneId = utc,
            canonicalActivities = emptyList(),
            completedPlanSessions = approvedPlan.sessions + draftPlan.sessions,
            asOfUtc = asOfInstant
        )
        val projected = Engine15ProjectedWorkloadAssembler.assembleFromPlans(
            accountId = accountA,
            asOfDate = asOfDate,
            plans = listOf(approvedPlan, draftPlan)
        )

        assertTrue(observed.disciplines.isEmpty())
        assertTrue(observed.observedLongestRunMeters30d() is BeliefState.Unknown)

        // Accepted vs. unaccepted plans remain distinct in ProjectedWorkloadState.
        assertEquals(2, projected.exposures.size)
        val authorities = projected.exposures.associate { it.planId to it.authority }
        assertEquals(ProjectedWorkloadAuthority.ACCEPTED_PLAN_FUTURE, authorities["plan-approved-1"])
        assertEquals(ProjectedWorkloadAuthority.PROPOSED_PLAN_PREVIEW, authorities["plan-draft-1"])
    }

    @Test fun completedWorkoutWithUnknownActualDistanceNeverUsesPlannedTargetDistance() {
        val completedUnknownDist = PlannedSession(
            id = "sess-completed-nodist",
            date = asOfDate.minusDays(2),
            discipline = "run",
            title = "Planned 14 km Long",
            minutes = 84,
            distanceKm = 14.0,
            kind = "long",
            status = "completed",
            logRevision = 1,
            log = WorkoutLog(
                at = asOfDate.minusDays(2).atTime(9, 0).toInstant(utc),
                minutes = 80.0,
                distanceKm = null,
                effort = 4,
                performedOn = asOfDate.minusDays(2)
            )
        )

        val observed = Engine15ObservedWorkloadAssembler.assemble(
            accountId = accountA,
            asOfDate = asOfDate,
            zoneId = utc,
            canonicalActivities = emptyList(),
            completedPlanSessions = listOf(completedUnknownDist),
            asOfUtc = asOfInstant
        )
        val run = observed.disciplines.getValue(AthleteDiscipline.RUN)
        assertEquals(1, run.unknownDistanceCompletedCount28d)
        assertEquals(ObservedExposureProvenance.COMPLETED_WORKOUT_LOG_UNKNOWN_DISTANCE, run.exposures.single().provenance)
        assertNull(run.exposures.single().distance)
        assertTrue(run.preceding30dLongestSessionBelief is BeliefState.Unknown)
    }

    @Test fun exactWindowBoundariesAndSameDayDistinctSessionsHold() {
        val exactlyMinus30 = syntheticRunObs(id = "day-minus-30", date = asOfDate.minusDays(30), distanceM = 17000.0)
        val excludedMinus31 = syntheticRunObs(id = "day-minus-31", date = asOfDate.minusDays(31), distanceM = 30000.0)
        val sameDayMorning = syntheticRunObs(id = "same-day-am", date = asOfDate.minusDays(3), hourUtc = 6, distanceM = 8000.0)
        val sameDayEvening = syntheticRunObs(id = "same-day-pm", date = asOfDate.minusDays(3), hourUtc = 18, distanceM = 6000.0)
        val evaluationDayRun = syntheticRunObs(id = "eval-day-run", date = asOfDate, hourUtc = 6, distanceM = 25000.0)

        // Midnight timezone shift test: UTC 23:30 on asOfDate-1 is already asOfDate in +02:00!
        val timezonePlus2 = java.time.ZoneOffset.ofHours(2)
        val lateUtcObs = syntheticRunObs(id = "midnight-tz-run", date = asOfDate.minusDays(1), hourUtc = 23, distanceM = 22000.0)

        val canonical = Engine15CanonicalActivityProjector.project(
            listOf(exactlyMinus30, excludedMinus31, sameDayMorning, sameDayEvening, evaluationDayRun)
        ).activities
        val observed = Engine15ObservedWorkloadAssembler.assemble(
            accountId = accountA,
            asOfDate = asOfDate,
            zoneId = utc,
            canonicalActivities = canonical
        )
        val run = observed.disciplines.getValue(AthleteDiscipline.RUN)
        // [d-30, d-1] max individual session = 17,000 m (excludes d-31=30km and evalDay=25km; does not sum 8+6=14km over 17km).
        val longest = run.preceding30dLongestSessionBelief as BeliefState.Estimated
        assertEquals(17000.0, longest.estimate.value, 0.001)
        // Rolling 28d sum excludes d-30 and d-31 and evalDay -> 8,000 + 6,000 = 14,000 m!
        val r28 = run.rolling28dDistanceBelief as BeliefState.Estimated
        assertEquals(14000.0, r28.estimate.value, 0.001)

        // With same-day two runs only: max individual session = 8,000 m, NEVER 14,000 m daily sum.
        val sameDayOnly = Engine15ObservedWorkloadAssembler.assemble(
            accountId = accountA,
            asOfDate = asOfDate,
            zoneId = utc,
            canonicalActivities = Engine15CanonicalActivityProjector.project(listOf(sameDayMorning, sameDayEvening)).activities
        )
        assertEquals(8000.0, (sameDayOnly.observedLongestRunMeters30d() as BeliefState.Estimated).estimate.value, 0.001)

        // In +02:00 timezone, 23:00Z on asOfDate-1 falls on asOfDate 01:00+02 -> excluded from preceding window.
        val tzShifted = Engine15ObservedWorkloadAssembler.assemble(
            accountId = accountA,
            asOfDate = asOfDate,
            zoneId = timezonePlus2,
            canonicalActivities = Engine15CanonicalActivityProjector.project(listOf(lateUtcObs)).activities
        )
        assertTrue(tzShifted.observedLongestRunMeters30d() is BeliefState.Unknown)
    }

    @Test fun duplicatesAmbiguousOverlapsAndCoverageStatesBehaveConservatively() {
        // 1. Ambiguous overlapping duplicates yield Bounded(lower = min, upper = sum), never falsely exact sum.
        val ambigA = syntheticRunObs(id = "amb-garmin", date = asOfDate.minusDays(4), distanceM = 8000.0, durationS = 2400.0, source = "garmin")
        val ambigB = syntheticRunObs(id = "amb-hc", date = asOfDate.minusDays(4), distanceM = 8050.0, durationS = 2410.0, source = "health_connect")
        val ambigObserved = Engine15ObservedWorkloadAssembler.assemble(
            accountId = accountA,
            asOfDate = asOfDate,
            zoneId = utc,
            canonicalActivities = Engine15CanonicalActivityProjector.project(listOf(ambigA, ambigB)).activities
        )
        val ambigRun = ambigObserved.disciplines.getValue(AthleteDiscipline.RUN)
        val ambig28 = ambigRun.rolling28dDistanceBelief as BeliefState.Bounded
        assertEquals(8000.0, ambig28.lower!!.value, 0.001)
        assertEquals(16050.0, ambig28.upper!!.value, 0.001)
        assertEquals(2, ambigRun.ambiguousCandidateCount28d)

        // 2. Confirmed-zero (T1A-F01) vs. missing-unobserved (T1A-F02).
        val zeroObs = Engine15ObservedWorkloadAssembler.assemble(
            accountId = accountA,
            asOfDate = asOfDate,
            zoneId = utc,
            canonicalActivities = emptyList(),
            provisionalSelfReport = mapOf(
                AthleteDiscipline.RUN to ProvisionalSelfReportWorkload(
                    AthleteDiscipline.RUN, RecentMileageBasis.CONFIRMED_ZERO, confirmedZero = true
                )
            )
        )
        val missingObs = Engine15ObservedWorkloadAssembler.assemble(
            accountId = accountA,
            asOfDate = asOfDate,
            zoneId = utc,
            canonicalActivities = emptyList()
        )
        assertEquals(HistoryCoverageStatus.CONFIRMED_ZERO, zeroObs.disciplines.getValue(AthleteDiscipline.RUN).coverage)
        assertEquals(0.0, (zeroObs.observedLongestRunMeters30d() as BeliefState.Estimated).estimate.value, 0.001)
        assertTrue(missingObs.observedLongestRunMeters30d() is BeliefState.Unknown)

        // 3. Partial 9-day history (T1A-F03: 3 runs = 22 km) never collapses to 5.5 km/wk Estimated!
        val partialActs = Engine15CanonicalActivityProjector.project(listOf(
            syntheticRunObs(id = "f03-1", date = asOfDate.minusDays(9), distanceM = 7000.0),
            syntheticRunObs(id = "f03-2", date = asOfDate.minusDays(5), distanceM = 6000.0),
            syntheticRunObs(id = "f03-3", date = asOfDate.minusDays(2), distanceM = 9000.0)
        )).activities
        val partialState = Engine15ObservedWorkloadAssembler.assemble(accountA, asOfDate, utc, partialActs)
        val partialRun = partialState.disciplines.getValue(AthleteDiscipline.RUN)
        assertEquals(HistoryCoverageStatus.PARTIAL_COVERAGE, partialRun.coverage)
        assertTrue(partialRun.rolling28dWeeklyAverageBelief is BeliefState.Bounded)
        assertNull((partialRun.rolling28dWeeklyAverageBelief as BeliefState.Bounded).upper)

        // 4. Continuous 28d history (T1A-F04: 20 runs = 288 km total = 72 km/wk) vs conflicting self-report (T1A-F09).
        val continuousObs = (1..20).map { i ->
            val dayOffset = (28L - ((i - 1) * 28L / 20L)).coerceIn(1L, 28L)
            syntheticRunObs(id = "f04-$i", date = asOfDate.minusDays(dayOffset), distanceM = 14400.0, durationS = 3900.0)
        }
        val continuousState = Engine15ObservedWorkloadAssembler.assemble(
            accountId = accountA,
            asOfDate = asOfDate,
            zoneId = utc,
            canonicalActivities = Engine15CanonicalActivityProjector.project(continuousObs).activities,
            provisionalSelfReport = mapOf(
                AthleteDiscipline.RUN to ProvisionalSelfReportWorkload(
                    AthleteDiscipline.RUN, RecentMileageBasis.SELF_REPORTED_AVERAGE,
                    weeklyVolume = Meters.fromKilometers(35.0) // 72 / 35 = 2.06 > 1.50 (POL-013)
                )
            )
        )
        val contRun = continuousState.disciplines.getValue(AthleteDiscipline.RUN)
        assertEquals(HistoryCoverageStatus.CONTINUOUS_COVERAGE, contRun.coverage)
        assertEquals(72000.0, (contRun.rolling28dWeeklyAverageBelief as BeliefState.Estimated).estimate.value, 0.001)
        assertTrue(contRun.selfReportConflictFlag)
    }

    @Test fun crossAccountCrossDisciplineDelayedCorrectionsAndSnapshotIntegrationHold() {
        val runA1 = syntheticRunObs(accountId = accountA, id = "a-run-1", date = asOfDate.minusDays(6), distanceM = 12000.0)
        val runA2 = syntheticRunObs(accountId = accountA, id = "a-run-2", date = asOfDate.minusDays(2), distanceM = 8000.0)
        val strA = syntheticRunObs(accountId = accountA, id = "a-str-1", date = asOfDate.minusDays(3), type = "strength", distanceM = null, durationS = 2700.0)
        val foreignB = syntheticRunObs(accountId = accountB, id = "b-run-huge", date = asOfDate.minusDays(1), distanceM = 42195.0)

        val order1 = Engine15ObservedWorkloadAssembler.assemble(
            accountA, asOfDate, utc,
            Engine15CanonicalActivityProjector.project(listOf(runA1, runA2, strA, foreignB)).activities
        )
        val order2 = Engine15ObservedWorkloadAssembler.assemble(
            accountA, asOfDate, utc,
            Engine15CanonicalActivityProjector.project(listOf(foreignB, strA, runA2, runA1)).activities
        )

        // Permutation invariance + account isolation + discipline isolation.
        assertEquals(order1.contentHash, order2.contentHash)
        assertEquals(20000.0, (order1.disciplines.getValue(AthleteDiscipline.RUN).rolling28dDistanceBelief as BeliefState.Estimated).estimate.value, 0.001)
        assertEquals(12000.0, (order1.observedLongestRunMeters30d() as BeliefState.Estimated).estimate.value, 0.001)

        // Historical correction updates only affected hash cleanly.
        val correctedA1 = syntheticRunObs(accountId = accountA, id = "a-run-1", date = asOfDate.minusDays(6), distanceM = 13500.0)
        val corrected = Engine15ObservedWorkloadAssembler.assemble(
            accountA, asOfDate, utc,
            Engine15CanonicalActivityProjector.project(listOf(runA1, correctedA1, runA2, strA)).activities
        )
        assertNotEquals(order1.contentHash, corrected.contentHash)
        assertEquals(13500.0, (corrected.observedLongestRunMeters30d() as BeliefState.Estimated).estimate.value, 0.001)

        // Immutable collections and Snapshot integration.
        val runWorkload = corrected.disciplines.getValue(AthleteDiscipline.RUN)
        @Suppress("UNCHECKED_CAST")
        val mutableExps = runWorkload.exposures as MutableList<DatedObservedSessionExposure>
        try {
            mutableExps.clear()
            fail("Expected UnsupportedOperationException")
        } catch (_: UnsupportedOperationException) { }

        val snap1 = AthleteStateSnapshot.fromWorkloads(
            accountId = accountA,
            evaluatedAtUtc = asOfInstant,
            observedWorkload = corrected,
            projectedWorkload = ProjectedWorkloadState(accountA, asOfDate, emptyList())
        )
        val snap2 = AthleteStateSnapshot.fromWorkloads(
            accountId = accountA,
            evaluatedAtUtc = asOfInstant.plusSeconds(3600),
            observedWorkload = corrected,
            projectedWorkload = ProjectedWorkloadState(accountA, asOfDate, emptyList())
        )
        assertEquals(snap1.contentHash, snap2.contentHash)
        assertNotEquals(snap1.evaluationAuthorizationKey(), snap2.evaluationAuthorizationKey())
    }

    @Test fun gap02UncompletedPriorLongRunContaminatesWeeklyReviewButIsBlockedInM1cFoundation() {
        val reviewDay = LocalDate.of(2026, 11, 16)
        val reviewNow = Instant.parse("2026-11-16T12:00:00Z")

        val completedEasy = (1..3).map { d ->
            PlannedSession(
                id = "easy-$d",
                date = reviewDay.minusDays(d.toLong()),
                discipline = "run",
                title = "Easy Run",
                minutes = 45,
                distanceKm = 8.0,
                kind = "easy",
                status = "completed",
                logRevision = 1,
                log = WorkoutLog(
                    at = reviewNow.minusSeconds(d * 86400L),
                    minutes = 45.0,
                    distanceKm = 8.0,
                    effort = 3,
                    performedOn = reviewDay.minusDays(d.toLong())
                )
            )
        }
        // Uncompleted prior planned long run (20.0 km) while true observed max is only 10.0 km!
        val uncompletedPriorLong = PlannedSession(
            id = "prior-planned-long",
            date = reviewDay.minusDays(9),
            discipline = "run",
            title = "Planned Prior Long",
            minutes = 120,
            distanceKm = 20.0,
            kind = "long",
            status = "planned"
        )
        val upcomingLong = PlannedSession(
            id = "upcoming-long",
            date = reviewDay.plusDays(5),
            discipline = "run",
            title = "Next Long",
            minutes = 108,
            distanceKm = 18.0,
            kind = "long",
            status = "planned"
        )
        val plan = PlanSnapshot(
            id = "gap02-plan",
            owner = accountA,
            engineVersion = ENGINE15_VERSION,
            policyId = ENGINE15_POLICY,
            createdAt = reviewNow.minusSeconds(14 * 86400L),
            request = PlanRequest(policyChoice = "engine15", longestKm = 10.0, dailyMinutes = 180),
            sessions = completedEasy + listOf(uncompletedPriorLong, upcomingLong),
            explanations = emptyList(),
            status = "approved"
        )
        val trueObserved10k = syntheticRunObs(
            accountId = accountA,
            id = "obs-10k",
            date = reviewDay.minusDays(8),
            distanceM = 10000.0,
            durationS = 3000.0
        )

        // 1. Reproduce GAP-02 in current production path (Engine15WeeklyReview reads uncompleted prior.distanceKm = 20.0).
        val legacyReview = Engine15WeeklyReview.preview(
            plan = plan,
            asOf = reviewDay,
            now = reviewNow,
            activities = listOf(trueObserved10k.record)
        )
        val legacyLongProgress = legacyReview.changes.firstOrNull { it.sessionId == "upcoming-long" }
        assertNotNull("Expected GAP-02 contamination to permit progression on upcoming 18km long run", legacyLongProgress)
        assertTrue(legacyLongProgress!!.minutes > 108)

        // 2. Verify M1C foundation strictly rejects the uncompleted 20.0 km / 18.0 km planned targets.
        val canonical = Engine15CanonicalActivityProjector.project(listOf(trueObserved10k)).activities
        val observed = Engine15ObservedWorkloadAssembler.assemble(
            accountId = accountA,
            asOfDate = reviewDay,
            zoneId = utc,
            canonicalActivities = canonical,
            completedPlanSessions = plan.sessions,
            asOfUtc = reviewNow
        )
        assertEquals(10000.0, (observed.observedLongestRunMeters30d() as BeliefState.Estimated).estimate.value, 0.001)
        assertTrue(FeatureFlags.USE_UNIFIED_ATHLETE_STATE_V1)
    }

    @Test fun linkedActivityDoesNotDoubleCountAndInvalidInputsFailClosed() {
        val runObs = syntheticRunObs(id = "garmin-linked-1", date = asOfDate.minusDays(2), distanceM = 10000.0)
        val linkedSession = PlannedSession(
            id = "sess-linked",
            date = asOfDate.minusDays(2),
            discipline = "run",
            title = "Coached Run",
            minutes = 50,
            distanceKm = 10.0,
            kind = "easy",
            status = "completed",
            linkedActivityId = "garmin-linked-1",
            logRevision = 1,
            log = WorkoutLog(asOfDate.minusDays(2).atTime(8, 0).toInstant(utc), 50.0, 10.0, 4, performedOn = asOfDate.minusDays(2))
        )
        val assembled = Engine15ObservedWorkloadAssembler.assemble(
            accountId = accountA,
            asOfDate = asOfDate,
            zoneId = utc,
            canonicalActivities = Engine15CanonicalActivityProjector.project(listOf(runObs)).activities,
            completedPlanSessions = listOf(linkedSession)
        )
        val run = assembled.disciplines.getValue(AthleteDiscipline.RUN)
        assertEquals(10000.0, (run.rolling28dDistanceBelief as BeliefState.Estimated).estimate.value, 0.001)
        assertEquals(1, run.exposures.size)

        // Non-finite and negative quantities rejected at L1 boundary.
        assertThrows(IllegalArgumentException::class.java) { Meters.of(Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { Meters.of(-1.0) }
        assertThrows(IllegalArgumentException::class.java) {
            ProvisionalSelfReportWorkload(
                AthleteDiscipline.RUN, RecentMileageBasis.CONFIRMED_ZERO,
                weeklyVolume = Meters.fromKilometers(10.0), confirmedZero = true
            )
        }
    }
}
