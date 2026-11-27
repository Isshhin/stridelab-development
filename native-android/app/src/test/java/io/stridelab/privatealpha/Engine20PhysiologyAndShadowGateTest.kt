package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.junit.Assert.*
import org.junit.Test

class Engine20PhysiologyAndShadowGateTest {
    private val t0 = Instant.parse("2026-11-30T08:00:00Z")
    private val d0 = LocalDate.parse("2026-11-30")

    @Test fun modB1ScaledQrComputesLowConditionNumberAndRejectsSubmaxOrNarrowTrials() {
        // Known synthetic athlete: v_CS = 4.00 m/s (4:10/km), D' = 200 m across 180s, 480s, 900s
        val trials = listOf(
            SevereTrialObservation("t1", 180.0, 4.0 * 180.0 + 202.0, "track", "supershoe", true, t0),
            SevereTrialObservation("t2", 480.0, 4.0 * 480.0 + 197.0, "track", "supershoe", true, t0),
            SevereTrialObservation("t3", 900.0, 4.0 * 900.0 + 201.0, "track", "supershoe", true, t0)
        )
        val belief = Engine20RunningPhysiologyAdapter.fitScaledQrCriticalSpeed(trials)
        assertTrue(belief.valid)
        assertEquals(4.0, belief.vCsMps!!, 0.02)
        assertEquals(200.0, belief.dPrimeMeters!!, 10.0)
        assertTrue("Scaled QR condition number should stay small (< 10): ${belief.conditionNumberScaled}",
            belief.conditionNumberScaled!! < 10.0)

        // Mismatched surface abstains cleanly
        val mixedSurface = listOf(trials[0], trials[1], trials[2].copy(surfaceTag = "trail"))
        val aborted = Engine20RunningPhysiologyAdapter.fitScaledQrCriticalSpeed(mixedSurface)
        assertFalse(aborted.valid)
        assertNotNull(aborted.abstentionReason)

        // Non-maximal trial abstains
        val nonMax = listOf(trials[0], trials[1], trials[2].copy(isVerifiedMaximal = false))
        assertFalse(Engine20RunningPhysiologyAdapter.fitScaledQrCriticalSpeed(nonMax).valid)

        // Narrow trial span (300s, 360s, 420s -> only 120s span) abstains cleanly
        val narrow = listOf(
            trials[0].copy(durationSeconds = 300.0, distanceMeters = 1400.0),
            trials[1].copy(durationSeconds = 360.0, distanceMeters = 1640.0),
            trials[2].copy(durationSeconds = 420.0, distanceMeters = 1880.0)
        )
        assertFalse(Engine20RunningPhysiologyAdapter.fitScaledQrCriticalSpeed(narrow).valid)
    }

    @Test fun modB2EnduranceExtensionIsC0ContinuousAndModB3QuarantinesHeatAndPpg() {
        val left = Engine20RunningPhysiologyAdapter.predictContinuousEnduranceSpeed(4.0, 200.0, 900.0, 0.06, 900.0)
        val right = Engine20RunningPhysiologyAdapter.predictContinuousEnduranceSpeed(4.0, 200.0, 900.0, 0.06, 900.0001)
        assertEquals(left, right, 1e-4)

        val longer = Engine20RunningPhysiologyAdapter.predictContinuousEnduranceSpeed(4.0, 200.0, 900.0, 0.06, 7200.0)
        assertTrue(longer < left)

        // Optical PPG & heat-confounded long runs MUST be quarantined cleanly without false EARLY_DECOUPLING flags!
        val noisySamples = listOf(
            LongRunDurabilitySample("lr-ppg", 110, 140.0, 165.0, 3.2, 3.0, isCleanEcg = false, isHeatConfounded = false, isFuelingDeficit = false),
            LongRunDurabilitySample("lr-heat", 100, 145.0, 170.0, 3.2, 3.0, isCleanEcg = true, isHeatConfounded = true, isFuelingDeficit = false)
        )
        val quarantined = Engine20RunningPhysiologyAdapter.evaluateDurability(noisySamples)
        assertEquals(DurabilityTier.UNOBSERVED, quarantined.tier)
        assertEquals(0, quarantined.cleanSampleCount)
        assertEquals(2, quarantined.quarantinedCount)

        // Clean ECG + cool + fueled run with high SI cardiac drift -> EARLY_DECOUPLING
        val cleanEarlyDrift = Engine20RunningPhysiologyAdapter.evaluateDurability(
            listOf(LongRunDurabilitySample("lr-clean", 105, 142.0, 156.0, 3.30, 3.20, isCleanEcg = true, isHeatConfounded = false, isFuelingDeficit = false))
        )
        assertEquals(DurabilityTier.EARLY_DECOUPLING, cleanEarlyDrift.tier)
        assertEquals(1, cleanEarlyDrift.cleanSampleCount)
    }

    @Test fun e203BayesianE1rmUsesHuberDampingRejectsOver12EffRepsAndModB4AdaptsMuscleBands() {
        val obs1 = StrengthSetObservation("str-squat", 100.0, reps = 6, rir = 2, techniqueClean = true, observedOn = d0)
        val st1 = Engine20StrengthResponseAdapter.updateBayesianE1rm(null, obs1)!!
        assertEquals(1, st1.observationCount)

        // r + RIR = 10 + 3 = 13 > 12 -> rejected cleanly (prior unchanged)
        val rejectedHighRep = StrengthSetObservation("str-squat", 80.0, reps = 10, rir = 3, techniqueClean = true, observedOn = d0.plusDays(3))
        assertEquals(st1, Engine20StrengthResponseAdapter.updateBayesianE1rm(st1, rejectedHighRep))

        // Outliers are damped via Huber weighting rather than spiking working load violently
        val spike = StrengthSetObservation("str-squat", 160.0, reps = 5, rir = 1, techniqueClean = true, observedOn = d0.plusDays(4))
        val st2 = Engine20StrengthResponseAdapter.updateBayesianE1rm(st1, spike)!!
        assertTrue(st2.conservativeLowerKg < st2.medianE1rmKg)
        assertTrue(st2.sdLog >= Engine20AdaptivePolicyRegistry.BAYESIAN_E1RM_SIGMA_FLOOR - 1e-9)

        // MOD-B4: 8 direct + 8 indirect sets = 12.0 fractional sets; repeated soreness & stall triggers LOW_TOLERANCE
        val band = Engine20StrengthResponseAdapter.evaluateMuscleRecoverableBand(
            "quadriceps",
            listOf(
                WeeklyMuscleOutcome("quadriceps", directHardSets = 8, indirectHardSets = 8, sorenessHigh = true, repsStalledOrRegressed = true),
                WeeklyMuscleOutcome("quadriceps", directHardSets = 8, indirectHardSets = 8, sorenessHigh = true, repsStalledOrRegressed = false)
            )
        )
        assertEquals(12.0, band.fractionalWeeklySetsLatest, 1e-6)
        assertEquals(MuscleVolumeToleranceTier.LOW_TOLERANCE, band.tier)
    }

    @Test fun e205FatigueFilterModC1KineticsAndTrack8ShadowPromotionGatesWorkDeterministically() {
        // E2-05: Missing signals -> abstains honestly without claiming low fatigue
        val emptyPost = Engine20FatigueAndDoseResponseEngine.updateBayesianFatigueFilter(null, FatigueFilterInput())
        assertTrue(emptyPost.abstainedInsufficientSignals)

        // High fatigue + adverse execution + valid low HRV -> elevated NON_FUNCTIONAL_FATIGUE_RISK, health hold flagged
        val fullHrv = (0 until 20).map { i ->
            HrvReading(d0.minusDays(i.toLong()), if (i < 4) 35.0 else 60.0, HrvProtocol.WAKING_SUPINE, HrvMetricType.RMSSD_MS)
        }
        val hrvEval = Engine19ExecutionAndDifferentialEngine.evaluateStandardizedHrv(fullHrv, d0)
        val day1Fatigue = Engine20FatigueAndDoseResponseEngine.updateBayesianFatigueFilter(
            prior = emptyPost,
            input = FatigueFilterInput(5, 5, executionDriftAdverse = true, hrvEvaluation = hrvEval, activeHealthHoldPresent = true)
        )
        assertFalse(day1Fatigue.abstainedInsufficientSignals)
        assertTrue(day1Fatigue.overrideBlockedByHealthHold)
        assertEquals(FatigueStateKind.NON_FUNCTIONAL_FATIGUE_RISK, day1Fatigue.dominantState)
        assertTrue(day1Fatigue.probNonFunctionalRisk > 0.45)

        // Two consecutive adverse days cross NON_FUNCTIONAL_FATIGUE_ACTION_THRESHOLD (>= 0.65) cleanly without single-day noise overreaction
        val day2Fatigue = Engine20FatigueAndDoseResponseEngine.updateBayesianFatigueFilter(
            prior = day1Fatigue,
            input = FatigueFilterInput(5, 5, executionDriftAdverse = true, hrvEvaluation = hrvEval, activeHealthHoldPresent = true)
        )
        assertTrue(day2Fatigue.probNonFunctionalRisk >= Engine20AdaptivePolicyRegistry.NON_FUNCTIONAL_FATIGUE_ACTION_THRESHOLD)

        // MOD-C1: Sparse < 56 d history -> non-identifiable fallback (`Hellard 2006`)
        val sparseKinetics = Engine20FatigueAndDoseResponseEngine.fitIndividualDoseResponse(
            (0 until 30).map { DailyImpulseRecord(d0.minusDays(it.toLong()), 60.0, if (it % 10 == 0) 100.0 else null) }
        )
        assertFalse(sparseKinetics.identifiable)

        // Dense 70-day log with 4 anchors -> identifiable individualized kinetics
        val denseKinetics = Engine20FatigueAndDoseResponseEngine.fitIndividualDoseResponse(
            (69 downTo 0).map { offset ->
                val load = if (offset % 7 == 0) 90.0 else 40.0 + (offset % 5) * 8.0
                val anchor = if (offset in setOf(60, 40, 20, 2)) 100.0 + (70 - offset) * 0.25 else null
                DailyImpulseRecord(d0.minusDays(offset.toLong()), load, anchor)
            }
        )
        assertTrue(denseKinetics.identifiable)
        assertTrue(denseKinetics.recommendedTaperDays in 7..21)

        // Track 8 §11 Shadow Promotion Gate: promotes calibrated superior model; demotes over-optimistic model
        val goodPoints = (1..10).map { i ->
            val obsOffset = if (i == 10) 5.5 else (i % 3) - 1.0 // 9/10 inside 1.645 * 3.0 = 4.93 -> exact 90% coverage
            DistributionalHoldoutPoint(observed = 200.0 + i + obsOffset, predictedMean = 200.0 + i, predictedSd = 3.0, tierABaselinePrediction = 192.0 + i)
        }
        val promoted = Engine20ShadowPromotionEvaluator.evaluateDistributionalModel("MOD-B1", goodPoints)
        assertEquals(ModelPromotionVerdict.ACTIVE_PERSONALIZED, promoted.verdict)

        val unsafePoints = (1..6).map { i ->
            DistributionalHoldoutPoint(observed = 200.0 + i, predictedMean = 225.0 + i, predictedSd = 2.0, tierABaselinePrediction = 200.0 + i)
        }
        val demoted = Engine20ShadowPromotionEvaluator.evaluateDistributionalModel("E2-03", unsafePoints)
        assertEquals(ModelPromotionVerdict.DEMOTED_FALLBACK_TO_TIER_A, demoted.verdict)

        // Deterministic MOD-B4 decision-usefulness gate (CRPS/NLL are null / N/A)
        val b4Report = Engine20ShadowPromotionEvaluator.evaluateDeterministicDecisionUsefulness("MOD-B4", 1, 4, 8)
        assertEquals(ModelPromotionVerdict.ACTIVE_PERSONALIZED, b4Report.verdict)
        assertNull(b4Report.crps)
    }
}
