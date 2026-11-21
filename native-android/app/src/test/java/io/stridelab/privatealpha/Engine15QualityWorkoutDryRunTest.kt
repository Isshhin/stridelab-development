package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class Engine15QualityWorkoutDryRunTest {
    private val owner = "tempo-dry-run-owner"
    private val now = Instant.parse("2026-11-17T00:00:00Z")
    private val monday = LocalDate.parse("2026-11-16")

    private fun request(weeks: Int = 8) = PlanRequest(
        goal = "Aerobic foundation", experience = 0, start = monday, weeks = weeks,
        days = setOf(1, 2, 3, 5, 6, 7), runDays = 6, longDay = 6,
        qualityCount = 1, qualityDays = setOf(2), weeklyKm = 75.0,
        longestKm = 20.0, targetWeeklyKm = 80.0,
        timeBudgetEnabled = false, policyChoice = "engine15",
        recentMileageBasis = "SELF_REPORTED_AVERAGE"
    )

    private fun deferred(input: PlanRequest): Engine15IndependentPlanDecision.Deferred {
        val result = Engine15IndependentPlanGenerator.preview(input, owner, "tempo-dry-run", now)
        assertTrue("Expected a deferred, non-persistable quality result, got $result",
            result is Engine15IndependentPlanDecision.Deferred)
        return result as Engine15IndependentPlanDecision.Deferred
    }

    private fun userAssertions(input: PlanRequest): List<Engine15M5SegmentDistanceClaim> {
        val slots = deferred(input).qualitySelection!!.candidateSlots
        return slots.map { slot ->
            val meters = slot.originalEasyDistanceKm * 1_000.0
            val warm = meters * 0.3
            val work = meters * 0.4
            Engine15M5SegmentDistanceClaim(
                weekNumber = slot.weekNumber,
                slotNumber = slot.slotNumber,
                assertedMetersByTimedSegment = listOf(warm, work, meters - warm - work),
                externalReviewReference = null,
                provenance = Engine15M5DistanceClaimProvenance.USER_ASSERTED_EQUIVALENT
            )
        }
    }

    @Test fun eightWeekBeginnerWithoutQualityHistoryGetsOnlyACompleteTimedTempoStructurePreview() {
        val result = deferred(request())
        val selection = result.qualitySelection!!
        val dryRun = result.qualityDryRun!!
        val template = IndependentQualityTemplates.get(
            IndependentQualityFamily.TEMPO, IndependentQualityStage.INTRO
        )

        assertEquals("QUALITY_GENERATION_NOT_QUALIFIED", result.reasonCode)
        assertEquals(Engine15QualitySelectionDecision.FAMILY_CANDIDATE, selection.decision)
        assertEquals(listOf(1, 2, 3, 5, 6, 7), selection.candidateSlots.map { it.weekNumber })
        assertEquals(Engine15FamilyPolicyQualification.PROVISIONAL_ENGINEERING_RULE,
            selection.familyPolicyQualification)
        assertEquals(IndependentQualityExposureConfidence.UNAVAILABLE, selection.exposureConfidence)
        assertEquals(template.id, dryRun.templateId)
        assertEquals("HEURISTIC", dryRun.templateEvidence)
        assertEquals(template.workout, dryRun.workout)
        assertEquals(template.workout, IndependentWorkoutComposition.decode(dryRun.workout.encode()))
        assertEquals(3, dryRun.workout.elements.size)
        val warmUp = (dryRun.workout.elements[0] as IndependentWorkoutElement.Step).value
        val work = (dryRun.workout.elements[1] as IndependentWorkoutElement.Step).value
        val coolDown = (dryRun.workout.elements[2] as IndependentWorkoutElement.Step).value
        assertEquals(WorkoutPurpose.WARM_UP, warmUp.purpose)
        assertEquals(600.0, warmUp.amount, 0.0)
        assertEquals(WorkoutTarget.EASY, warmUp.target)
        assertEquals(WorkoutPurpose.WORK, work.purpose)
        assertEquals(720.0, work.amount, 0.0)
        assertEquals(WorkoutTarget.TEMPO, work.target)
        assertEquals(WorkoutPurpose.COOL_DOWN, coolDown.purpose)
        assertEquals(480.0, coolDown.amount, 0.0)
        assertEquals(WorkoutTarget.EASY, coolDown.target)
        assertEquals(1_800, dryRun.totalSeconds)
        assertEquals(720, dryRun.workSeconds)
        assertTrue(dryRun.baseWeeklyAccountingReconciled)
        assertFalse(dryRun.qualityDistanceReconciled)
        assertFalse(dryRun.completePlanDistanceAccounting)
        assertNull(dryRun.qualitySessionDistanceKm)
        assertNull(dryRun.weeklyDistanceAfterReplacementKm)
        assertFalse(dryRun.includedInPlan)
        assertFalse(dryRun.scheduled)
        assertFalse(dryRun.executable)
        assertTrue(result.explanation.contains("weekly mileage remain unknown"))
    }

    @Test fun sixteenWeekEnduranceDryRunAccountsForEveryBaseAndRecoveryWeekButNotTempoKilometres() {
        val result = deferred(request(16).copy(goal = "Marathon", experience = 2))
        val dryRun = result.qualityDryRun!!

        assertEquals(16, dryRun.weeklyDistance.size)
        assertEquals(600, dryRun.workSeconds)
        assertEquals((1..16).toList(), dryRun.weeklyDistance.map { it.weekNumber })
        assertEquals(listOf(4, 8, 12, 16), dryRun.weeklyDistance
            .filter { it.kind == Engine15IndependentVolumePlanner.WeekKind.RECOVERY }
            .map { it.weekNumber })
        assertEquals(listOf(1, 2, 3, 5, 6, 7, 9, 10, 11, 13, 14, 15),
            result.qualitySelection!!.candidateSlots.map { it.weekNumber })
        assertTrue(dryRun.weeklyDistance.all { kotlin.math.abs(it.budgetKm - it.scheduledRunKm) < 1e-6 })
        assertEquals(1, dryRun.weekNumber)
        assertEquals(Engine15IndependentVolumePlanner.WeekKind.LOADING,
            dryRun.weeklyDistance.single { it.weekNumber == dryRun.weekNumber }.kind)
        assertEquals(Engine15QualityDryRunStatus.STRUCTURE_VALIDATED_DISTANCE_UNRECONCILED,
            dryRun.status)
    }

    @Test fun thresholdCandidateDefersUntilEverySegmentHasAnEquivalent() {
        val result = deferred(request().copy(goal = "Improve 10K", experience = 2))
        assertEquals(IndependentQualityFamily.THRESHOLD, result.qualitySelection!!.selectedFamily)
        assertEquals(IndependentQualityFamily.THRESHOLD, result.qualityDryRun!!.family)
        assertFalse(result.qualityDryRun.completePlanDistanceAccounting)
        assertEquals("QUALITY_GENERATION_NOT_QUALIFIED", result.reasonCode)
    }

    @Test fun thresholdIntroFitsEightWeeksWithoutInventingPriorExposure() {
        org.junit.Assume.assumeTrue(BuildConfig.ENGINE15_INDEPENDENT_RUNNING_ACTIVE)
        val input = request().copy(goal = "Improve 10K", experience = 2, weeklyKm = 30.0,
            targetWeeklyKm = 30.0, longestKm = 10.0)
        val reviewed = deferred(input)
        assertEquals(IndependentQualityExposureConfidence.UNAVAILABLE, reviewed.qualitySelection!!.exposureConfidence)
        assertNull(deferred(input.copy(runDays = 3)).qualityDryRun)
        val claims = reviewed.qualitySelection.candidateSlots.map { slot ->
            val total = kotlin.math.round(slot.originalEasyDistanceKm * 1000).toInt()
            val metres = listOf(600, 300, 100, 300, 100, 300).map { (it * total / 2180).toDouble() }
            Engine15M5SegmentDistanceClaim(slot.weekNumber, slot.slotNumber,
                metres + (total - metres.sum()), null, Engine15M5DistanceClaimProvenance.USER_ASSERTED_EQUIVALENT)
        }
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(
            input.copy(qualityDistanceClaims = claims), owner, emptyList(), "tempo-dry-run", now)
        assertEquals(plan, Engine15IndependentPlanGenerator.generateWithHistory(
            input.copy(qualityDistanceClaims = claims), owner, emptyList(), "tempo-dry-run", now))
        assertTrue(plan.sessions.filter { it.quality }.all { session ->
            session.runningPrescription!!.mixedSegments!!.filter { it.purpose == "WORK" }
                .sumOf { it.seconds!! } == 720
        })
        val inconsistent = claims.first().assertedMetersByTimedSegment.toMutableList()
        inconsistent[0] += inconsistent[1] - 1.0
        inconsistent[1] = 1.0
        val declined = deferred(input.copy(qualityDistanceClaims =
            listOf(claims.first().copy(assertedMetersByTimedSegment = inconsistent)) + claims.drop(1)))
        assertTrue(declined.explanation, declined.explanation.contains("faster than Threshold work"))
        val base = Engine15IndependentPlanGenerator.generateWithHistory(
            input.copy(qualityCount = 0), owner, emptyList(), "tempo-dry-run", now)
        assertEquals(base.sessions.map { it.id to it.date },
            plan.sessions.map { it.id to it.date })
        assertEquals(base.sessions.filter { it.kind == "long" }.map { it.date to it.distanceKm },
            plan.sessions.filter { it.kind == "long" }.map { it.date to it.distanceKm })
        val baseWeeksT = base.sessions.groupBy { java.time.temporal.ChronoUnit.DAYS.between(monday, it.date) / 7L }
        val planWeeksT = plan.sessions.groupBy { java.time.temporal.ChronoUnit.DAYS.between(monday, it.date) / 7L }
        assertEquals(baseWeeksT.keys, planWeeksT.keys)
        baseWeeksT.forEach { (w, rows) ->
            assertEquals(rows.sumOf { it.distanceKm!! }, planWeeksT.getValue(w).sumOf { it.distanceKm!! }, 1e-6)
        }
        assertEquals(listOf(1L, 2L, 3L, 5L, 6L, 7L), plan.sessions.filter { it.quality }.map {
            java.time.temporal.ChronoUnit.DAYS.between(monday, it.date) / 7 + 1
        })
        val workout = plan.sessions.first { it.quality }.runningPrescription!!
        assertEquals("Threshold", workout.family)
        assertEquals(listOf("WARM_UP", "WORK", "RECOVERY", "WORK", "RECOVERY", "WORK", "COOL_DOWN"),
            workout.mixedSegments!!.map { it.purpose })
        assertEquals(listOf(600, 240, 120, 240, 120, 240, 480), workout.mixedSegments.map { it.seconds })
        val approved = PlanOperations.approve(plan, listOf(plan))
        assertEquals(approved, PlanSnapshot.decode(approved.encode(), owner))
        val quality = plan.sessions.first { it.quality }
        val friday = plan.sessions.single { it.date == monday.plusDays(4) }
        val moved = plan.copy(sessions = plan.sessions.map {
            when (it.id) {
                quality.id -> it.copy(date = friday.date)
                friday.id -> it.copy(date = quality.date)
                else -> it
            }
        })
        val rejected = assertThrows(PlanConstraint::class.java) {
            Engine15IndependentPlanGenerator.validateSaved(moved)
        }
        assertTrue(rejected.message.orEmpty().contains("spaced"))
        fun swapped(a: PlannedSession, b: PlannedSession) = plan.copy(sessions = plan.sessions.map {
            when (it.id) {
                a.id -> it.copy(date = b.date)
                b.id -> it.copy(date = a.date)
                else -> it
            }
        })
        fun rejectedBy(edited: PlanSnapshot, reason: String) {
            val error = assertThrows(PlanConstraint::class.java) {
                Engine15IndependentPlanGenerator.validateSaved(edited)
            }
            assertTrue(error.message, error.message.orEmpty().contains(reason))
        }
        val laterQuality = plan.sessions.filter { it.quality }
        rejectedBy(swapped(laterQuality[4], laterQuality[5]), "reviewed loading week")
        rejectedBy(swapped(quality, plan.sessions.single { it.date == monday.plusDays(2) }), "selected quality weekday")
        rejectedBy(plan.copy(sessions = plan.sessions.dropLast(1)), "missing or duplicating")
        rejectedBy(plan.copy(sessions = plan.sessions.dropLast(1) + quality), "missing or duplicating")
        val rewritten = workout.copy(mixedSegments = workout.mixedSegments.mapIndexed { index, step ->
            if (index == 1) step.copy(seconds = 300) else step
        })
        rejectedBy(plan.copy(sessions = plan.sessions.map {
            if (it.id == quality.id) it.copy(runningPrescription = rewritten) else it
        }), "reviewed workload")
    }

    @Test fun intervalsCandidateDefersUntilEverySegmentHasAnEquivalent() {
        val result = deferred(request().copy(goal = "Improve 5K", experience = 2))
        assertEquals(IndependentQualityFamily.INTERVALS, result.qualitySelection!!.selectedFamily)
        assertEquals(IndependentQualityFamily.INTERVALS, result.qualityDryRun!!.family)
        assertEquals("engine15-intervals-intro-v1", result.qualityDryRun.templateId)
        assertEquals(1920, result.qualityDryRun.totalSeconds)
        assertEquals(480, result.qualityDryRun.workSeconds)
        assertFalse(result.qualityDryRun.completePlanDistanceAccounting)
        assertEquals("QUALITY_GENERATION_NOT_QUALIFIED", result.reasonCode)
    }

    @Test fun intervalsIntroFitsEightWeeksWithoutInventingPriorExposure() {
        org.junit.Assume.assumeTrue(BuildConfig.ENGINE15_INDEPENDENT_RUNNING_ACTIVE)
        val input = request().copy(goal = "Improve 5K", experience = 2, weeklyKm = 30.0,
            targetWeeklyKm = 30.0, longestKm = 10.0)
        val reviewed = deferred(input)
        assertEquals(IndependentQualityFamily.INTERVALS, reviewed.qualitySelection!!.selectedFamily)
        assertEquals(IndependentQualityExposureConfidence.UNAVAILABLE, reviewed.qualitySelection.exposureConfidence)
        assertNull(deferred(input.copy(runDays = 3)).qualityDryRun)
        val claims = reviewed.qualitySelection.candidateSlots.map { slot ->
            val total = kotlin.math.round(slot.originalEasyDistanceKm * 1000).toInt()
            // 9 timed segments: 10m WU, 4x(2m W, 2m R) minus last R, 8m CD -> weights sum to 2280
            val weights = listOf(600, 180, 100, 180, 100, 180, 100, 180)
            val metres = weights.map { (it * total / 2100).toDouble() }
            Engine15M5SegmentDistanceClaim(slot.weekNumber, slot.slotNumber,
                metres + (total - metres.sum()), null, Engine15M5DistanceClaimProvenance.USER_ASSERTED_EQUIVALENT)
        }
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(
            input.copy(qualityDistanceClaims = claims), owner, emptyList(), "intervals-dry-run", now)
        assertEquals(plan, Engine15IndependentPlanGenerator.generateWithHistory(
            input.copy(qualityDistanceClaims = claims), owner, emptyList(), "intervals-dry-run", now))
        assertTrue(plan.sessions.filter { it.quality }.all { session ->
            session.runningPrescription!!.mixedSegments!!.filter { it.purpose == "WORK" }
                .sumOf { it.seconds!! } == 480
        })
        val inconsistent = claims.first().assertedMetersByTimedSegment.toMutableList()
        inconsistent[0] += inconsistent[1] - 1.0
        inconsistent[1] = 1.0
        val declined = deferred(input.copy(qualityDistanceClaims =
            listOf(claims.first().copy(assertedMetersByTimedSegment = inconsistent)) + claims.drop(1)))
        assertTrue(declined.explanation, declined.explanation.contains("faster than Intervals work"))
        val base = Engine15IndependentPlanGenerator.generateWithHistory(
            input.copy(qualityCount = 0), owner, emptyList(), "intervals-dry-run", now)
        assertEquals(base.sessions.map { it.id to it.date },
            plan.sessions.map { it.id to it.date })
        assertEquals(base.sessions.filter { it.kind == "long" }.map { it.date to it.distanceKm },
            plan.sessions.filter { it.kind == "long" }.map { it.date to it.distanceKm })
        val baseWeeksI = base.sessions.groupBy { java.time.temporal.ChronoUnit.DAYS.between(monday, it.date) / 7L }
        val planWeeksI = plan.sessions.groupBy { java.time.temporal.ChronoUnit.DAYS.between(monday, it.date) / 7L }
        assertEquals(baseWeeksI.keys, planWeeksI.keys)
        baseWeeksI.forEach { (w, rows) ->
            assertEquals(rows.sumOf { it.distanceKm!! }, planWeeksI.getValue(w).sumOf { it.distanceKm!! }, 1e-6)
        }
        val workout = plan.sessions.first { it.quality }.runningPrescription!!
        assertEquals("Intervals", workout.family)
        assertEquals(listOf("WARM_UP", "WORK", "RECOVERY", "WORK", "RECOVERY", "WORK", "RECOVERY", "WORK", "COOL_DOWN"),
            workout.mixedSegments!!.map { it.purpose })
        assertEquals(listOf(600, 120, 120, 120, 120, 120, 120, 120, 480), workout.mixedSegments.map { it.seconds })
        val approved = PlanOperations.approve(plan, listOf(plan))
        assertEquals(approved, PlanSnapshot.decode(approved.encode(), owner))
        Engine15IndependentPlanGenerator.validateSaved(approved)
    }

    @Test fun explicitTempoPreferenceIsNotLabeledCoachGenerated() {
        val result = deferred(request().copy(
            preferredQuality = "Tempo", preferredQualityExplicit = true
        ))
        assertEquals(Engine15QualitySelectionBasis.EXPLICIT_USER_PREFERENCE,
            result.qualitySelection!!.selectionBasis)
        assertNull(result.qualityDryRun)
        assertTrue(result.explanation.contains("explicit user direction"))
    }

    @Test fun unavailableQualityWeekdayDefersWithoutCreatingADryRun() {
        val result = deferred(request().copy(qualityDays = setOf(6)))
        assertEquals("QUALITY_SLOT_UNAVAILABLE", result.qualitySelection!!.reasonCode)
        assertNull(result.qualityDryRun)
    }

    @Test fun dryRunPreservesSelectionAndAuthorizationBoundaries() {
        val result = deferred(request())
        val dryRun = result.qualityDryRun!!

        assertEquals(Engine15QualityFamilySelection.POLICY_ID, dryRun.selectionPolicyId)
        assertEquals(Engine15QualityFamilySelection.POLICY_ID, result.qualitySelection!!.policyId)
        assertTrue(dryRun.developmentOnly)
        assertFalse(dryRun.sourceEvidenceVerified)
        assertFalse(dryRun.templateAuthorized)
        assertFalse(dryRun.doseAuthorized)
        assertFalse(dryRun.intensityAuthorized)
        assertFalse(dryRun.scheduled)
        assertFalse(dryRun.includedInPlan)
        assertFalse(dryRun.executable)
        assertEquals(result.qualitySelection!!.candidateSlots.first().sessionId, dryRun.sessionId)
        assertEquals(result.qualitySelection!!.candidateSlots.first().date, dryRun.date)
    }

    @Test fun everyEightWeekLoadingWeekHasItsOwnReconciledUnscheduledAlternative() {
        val input = request()
        val claims = userAssertions(input)
        val result = deferred(input.copy(qualityDistanceClaims = claims))
        val dryRun = result.qualityDryRun!!

        assertEquals(listOf(1, 2, 3, 5, 6, 7), dryRun.alternatives.map { it.weekNumber })
        assertEquals(6, dryRun.alternatives.size)
        assertTrue(dryRun.alternatives.all {
            it.structurallyValid &&
                it.distanceStatus == Engine15QualityDistanceStatus.USER_ASSERTED_ARITHMETIC_RECONCILED &&
                it.distanceClaimProvenance == Engine15M5DistanceClaimProvenance.USER_ASSERTED_EQUIVALENT &&
                !it.sourceDistancesVerified && !it.coachDistanceVerified &&
                !it.intensityQualified && !it.doseQualified && !it.executable
        })
        assertTrue(dryRun.completePlanDistanceAccounting)
        assertTrue(dryRun.alternatives.all { it.weeklyDistanceAfterReplacementKm ==
            dryRun.weeklyDistance.single { week -> week.weekNumber == it.weekNumber }.budgetKm })
        assertEquals(claims.map { it.weekNumber to it.slotNumber },
            dryRun.alternatives.map { it.weekNumber to it.slotNumber })
        assertTrue(dryRun.alternatives.map { it.sessionId }.distinct().size == 6)
        assertFalse(dryRun.alternativesAreAuthorizedProgression)
        assertFalse(dryRun.executable)
        assertEquals(Engine15QualityDryRunStatus.STRUCTURE_VALIDATED_DISTANCE_RECONCILED_FROM_ASSERTIONS,
            dryRun.status)
    }

    @Test fun sixteenWeekAlternativeSetKeepsRecoveryWeeksOutAndSupportsDifferentMileageBudget() {
        val input = request(16).copy(goal = "Marathon", experience = 2,
            weeklyKm = 40.0, targetWeeklyKm = 48.0, longestKm = 12.0)
        val preview = Engine15IndependentPlanGenerator.preview(input, owner, "sixteen-dry", now)
        val dryRun = (preview as Engine15IndependentPlanDecision.Deferred).qualityDryRun!!
        assertEquals((1..16).filterNot { it % 4 == 0 }, dryRun.alternatives.map { it.weekNumber })
        assertEquals(listOf(4, 8, 12, 16), dryRun.weeklyDistance
            .filter { it.kind == Engine15IndependentVolumePlanner.WeekKind.RECOVERY }
            .map { it.weekNumber })
        assertTrue(dryRun.alternatives.all { it.originalEasyDistanceKm > 0.0 })

        // With supplied claims, 16-week Marathon quality plan builds cleanly as a Candidate
        val claims = userAssertions(input)
        val candidate = Engine15IndependentPlanGenerator.preview(input.copy(qualityDistanceClaims = claims), owner, "sixteen-cand", now)
        assertTrue("Expected candidate or reconciled dry-run, got $candidate",
            candidate is Engine15IndependentPlanDecision.Candidate ||
                (candidate as? Engine15IndependentPlanDecision.Deferred)?.qualityDryRun?.completePlanDistanceAccounting == true)
    }

    @Test fun missingOrContradictorySegmentClaimsStayVisibleAndDoNotReconcileWholeHorizon() {
        val input = request()
        val claims = userAssertions(input)
        val missing = deferred(input.copy(qualityDistanceClaims = claims.drop(1))).qualityDryRun!!
        assertFalse(missing.completePlanDistanceAccounting)
        assertEquals(Engine15QualityDistanceStatus.DISTANCE_EQUIVALENTS_NOT_SUPPLIED,
            missing.alternatives.first().distanceStatus)
        assertNull(missing.alternatives.first().distanceMatch)

        val first = claims.first()
        val contradictory = deferred(input.copy(qualityDistanceClaims = claims.map {
            if (it.weekNumber == first.weekNumber) it.copy(
                assertedMetersByTimedSegment = it.assertedMetersByTimedSegment.dropLast(1) +
                    (it.assertedMetersByTimedSegment.last() - 10.0)
            ) else it
        })).qualityDryRun!!
        assertFalse(contradictory.completePlanDistanceAccounting)
        assertEquals("SESSION_DISTANCE_SUM_MISMATCH",
            contradictory.alternatives.first().distanceFailureCode)
        assertTrue(contradictory.alternatives.drop(1).all {
            it.distanceStatus == Engine15QualityDistanceStatus.USER_ASSERTED_ARITHMETIC_RECONCILED
        })
    }

    @Test fun duplicateOrOutOfHorizonClaimsInvalidateOnlyTheClaimSetAccounting() {
        val input = request()
        val claims = userAssertions(input)
        val duplicate = deferred(input.copy(qualityDistanceClaims = claims + claims.first())).qualityDryRun!!
        assertFalse(duplicate.completePlanDistanceAccounting)
        assertTrue(duplicate.alternatives.all {
            it.distanceStatus == Engine15QualityDistanceStatus.CLAIM_SET_CONFLICT
        })

        val extra = claims + claims.first().copy(weekNumber = 9)
        val outOfHorizon = deferred(input.copy(qualityDistanceClaims = extra)).qualityDryRun!!
        assertFalse(outOfHorizon.completePlanDistanceAccounting)
        assertTrue(outOfHorizon.alternatives.all {
            it.distanceStatus == Engine15QualityDistanceStatus.CLAIM_SET_CONFLICT
        })
    }

    @Test fun nonfiniteAndNonpositiveAssertionsAreRejectedWithoutPacePlausibilityCutoffs() {
        val input = request()
        val claims = userAssertions(input)
        val invalid = claims.mapIndexed { index, claim ->
            if (index == 0) claim.copy(assertedMetersByTimedSegment =
                listOf(Double.NaN, 1_000.0, 1_000.0)) else claim
        }
        val result = deferred(input.copy(qualityDistanceClaims = invalid)).qualityDryRun!!
        assertEquals("INVALID_ASSERTED_METERS", result.alternatives.first().distanceFailureCode)
        assertFalse(result.completePlanDistanceAccounting)
        assertTrue(result.alternatives.drop(1).all {
            it.distanceStatus == Engine15QualityDistanceStatus.USER_ASSERTED_ARITHMETIC_RECONCILED
        })
    }

    @Test fun structurallyFeasibleClaimsCannotBypassLockedOrConflictingQualitySlots() {
        val input = request()
        val first = deferred(input)
        val lockedId = first.qualitySelection!!.candidateSlots.first().sessionId
        val locked = deferred(input.copy(lockedSessionDays = mapOf(lockedId to "2026-11-17")))
        assertEquals("QUALITY_SLOT_UNAVAILABLE", locked.qualitySelection!!.reasonCode)
        assertNull(locked.qualityDryRun)
    }
    @Test fun tempoAndMileageBuildSucceedTogetherWithAdvisoryNote() {
        org.junit.Assume.assumeTrue(BuildConfig.ENGINE15_INDEPENDENT_RUNNING_ACTIVE)
        val input = request().copy(experience = 1, weeklyKm = 30.0, targetWeeklyKm = 35.0, longestKm = 12.0)
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(input, owner, emptyList(), "tempo-build", now)
        assertEquals("draft", plan.status)
        assertTrue(plan.sessions.any { it.quality })
        assertTrue(plan.explanations.any { it.contains("build", ignoreCase = true) || it.contains("effort", ignoreCase = true) })
        val approved = PlanOperations.approve(plan, listOf(plan))
        Engine15IndependentPlanGenerator.validateSaved(approved)
    }

    @Test fun privateTempoDraftFitsEveryWeekAndRoundTripsThroughApproval() {
        org.junit.Assume.assumeTrue(BuildConfig.ENGINE15_INDEPENDENT_RUNNING_ACTIVE)
        val input = request().copy(experience = 1, weeklyKm = 30.0, targetWeeklyKm = 30.0, longestKm = 10.0)
        val claims = userAssertions(input).map { claim ->
            val total = kotlin.math.round(claim.assertedMetersByTimedSegment.sum()).toInt()
            val warm = total * 31 / 100
            val cool = total * 24 / 100
            claim.copy(assertedMetersByTimedSegment = listOf(warm.toDouble(),
                (total - warm - cool).toDouble(), cool.toDouble()))
        }
        val suspicious = claims.first().copy(assertedMetersByTimedSegment = listOf(1266.0, 1268.0, 1266.0))
        val declined = deferred(input.copy(qualityDistanceClaims = listOf(suspicious) + claims.drop(1)))
        assertTrue(declined.explanation, declined.explanation.contains("cool down implies"))
        assertEquals(suspicious.assertedMetersByTimedSegment,
            declined.qualityDryRun!!.alternatives.first().distanceMatch!!.segments.map { it.assertedMeters })

        val base = Engine15IndependentPlanGenerator.generateWithHistory(input.copy(qualityCount = 0),
            owner, emptyList(), "tempo-dry-run", now)
        val draft = Engine15IndependentPlanGenerator.generateWithHistory(input.copy(qualityDistanceClaims = claims),
            owner, emptyList(), "tempo-dry-run", now)
        assertEquals("draft", draft.status)
        assertEquals(base.sessions.map { it.id to it.date },
            draft.sessions.map { it.id to it.date })
        assertEquals(base.sessions.filter { it.kind == "long" }.map { it.date to it.distanceKm },
            draft.sessions.filter { it.kind == "long" }.map { it.date to it.distanceKm })
        val baseWeeksP = base.sessions.groupBy { java.time.temporal.ChronoUnit.DAYS.between(monday, it.date) / 7L }
        val draftWeeksP = draft.sessions.groupBy { java.time.temporal.ChronoUnit.DAYS.between(monday, it.date) / 7L }
        assertEquals(baseWeeksP.keys, draftWeeksP.keys)
        baseWeeksP.forEach { (w, rows) ->
            assertEquals(rows.sumOf { it.distanceKm!! }, draftWeeksP.getValue(w).sumOf { it.distanceKm!! }, 1e-6)
        }
        assertEquals(listOf(1L, 2L, 3L, 5L, 6L, 7L), draft.sessions.filter { it.quality }.map {
            java.time.temporal.ChronoUnit.DAYS.between(monday, it.date) / 7 + 1
        })
        val tempo = draft.sessions.first { it.quality }
        assertEquals(listOf(600, 600, 480), tempo.runningPrescription!!.mixedSegments!!.map { it.seconds })
        draft.sessions.chunked(6).forEachIndexed { week, sessions ->
            println("TEMPO_FIXTURE week ${week + 1}: " + sessions.joinToString {
                "${it.date.dayOfWeek} ${it.kind} ${it.distanceKm} km"
            })
        }
        println("TEMPO_FIXTURE equivalents: " + claims.map { it.assertedMetersByTimedSegment })
        val approved = PlanOperations.approve(draft, listOf(draft))
        assertEquals("approved", approved.status)
        assertEquals(approved, PlanSnapshot.decode(approved.encode(), owner))
        assertEquals("stridelab.engine15.experimental-quality-v1", approved.experimentalQualityPolicy)
        Engine15IndependentPlanGenerator.validateSaved(approved.copy(explanations = listOf("Edited display copy")))
        val historical = PlanSnapshot.decode(approved.encode().apply { remove("experimentalQualityPolicy") }, owner)
        assertNull(historical.experimentalQualityPolicy)
        Engine15IndependentPlanGenerator.validateSaved(historical)
    }
}
