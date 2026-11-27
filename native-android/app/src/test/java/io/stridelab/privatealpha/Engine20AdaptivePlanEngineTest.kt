package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.junit.Assert.*
import org.junit.Test

class Engine20AdaptivePlanEngineTest {
    private val owner = "engine20-coach-tester"
    private val t0 = Instant.parse("2026-11-30T08:00:00Z")
    private val d0 = LocalDate.parse("2026-11-30")
    private val fp = OccInputFingerprint("a1", "s0", "r0", "av1", "10k", 1, "cal1", Engine20AdaptivePolicyRegistry.POLICY_VERSION)

    @Test fun foundationBuilderWithoutWearablesGetsAccessibleEffortGuidedCoachingAndLocalPersistence() {
        val backend = InMemoryM1RecordBackend()
        val store = Engine20AdaptiveProfileLocalStore(backend)
        val coldStart = AthleteAdaptiveResponseProfile(
            accountId = owner,
            updatedAtUtc = t0,
            consistentHistoryDays = 10
        )
        store.saveProfile(owner, coldStart, t0.toEpochMilli())
        assertEquals(0, backend.outboxWriteCount)
        val reloaded = store.loadProfile(owner, t0)

        val result = Engine20AdaptivePlanEngine.synthesizeAdaptiveAdjustments(
            profile = reloaded,
            decisionState = DecisionStateSnapshot(),
            basePlanVersion = 1,
            occFingerprint = fp,
            nowUtc = t0
        )
        assertEquals(AthleteResponseArchetype.FOUNDATION_BUILDER, result.archetype)
        assertEquals(0, result.autonomyAppliedCount)
        assertFalse(result.coachBriefingSummary.contains("—"))
        assertFalse(result.coachBriefingSummary.contains("--"))
        assertTrue(result.accessibleNextStep.isNotBlank())
    }

    @Test fun optInBoundedAutonomyAppliesOnlyDownwardRecoveryNeverUpwardEscalationOrHealthBypass() {
        val highFatigueProfile = AthleteAdaptiveResponseProfile(
            accountId = owner,
            updatedAtUtc = t0,
            authorityMode = AdaptationAuthorityMode.OPT_IN_BOUNDED_AUTONOMY,
            consistentHistoryDays = 42,
            csDPrimeBelief = JointCsDPrimeBelief(4.2, 220.0, 0.05, 10.0, -0.1, 3.8, 3, valid = true),
            fatiguePosterior = FatiguePosteriorState(0.05, 0.20, 0.75, 0.55, false, false, "High fatigue posterior"),
            promotionVerdicts = mapOf(
                "E2-05" to ModelPromotionVerdict.ACTIVE_PERSONALIZED,
                "MOD-B1" to ModelPromotionVerdict.ACTIVE_PERSONALIZED,
                "MOD-B4" to ModelPromotionVerdict.ACTIVE_PERSONALIZED
            )
        )

        // 1. Eligible opt-in athlete -> downward fatigue trim auto-applied with undo receipt!
        val autoResult = Engine20AdaptivePlanEngine.synthesizeAdaptiveAdjustments(
            profile = highFatigueProfile,
            decisionState = DecisionStateSnapshot(),
            basePlanVersion = 3,
            occFingerprint = fp,
            nowUtc = t0
        )
        assertEquals(AthleteResponseArchetype.FATIGUE_SENSITIVE_OR_OVERREACHING, autoResult.archetype)
        assertTrue(autoResult.autonomyAppliedCount >= 1)
        val trimAction = autoResult.actions.first { it.kind == AdaptiveAdjustmentKind.DOWNWARD_RECOVERY_TRIM }
        assertTrue(trimAction.autoAppliedUnderBoundedAutonomy)
        assertNotNull(trimAction.undoReceiptId)
        assertTrue(trimAction.volumeMultiplier >= 0.90)

        // 2. Active H2 health hold suspends ALL autonomous or adaptive changes immediately!
        val activeHold = HealthHoldRecord(
            restrictionId = "hold-shin",
            accountId = owner,
            trigger = "Focal shin pain",
            scope = HealthRestrictionScope.Localized(setOf("shin"), setOf(AthleteDiscipline.RUN)),
            tier = HealthEscalationTier.H2_CLINICAL_EVALUATION_REQUIRED,
            createdAtUtc = t0
        )
        val heldState = DecisionStateSnapshot(activeHealthHolds = mapOf("hold-shin" to activeHold))
        val blockedResult = Engine20AdaptivePlanEngine.synthesizeAdaptiveAdjustments(
            profile = highFatigueProfile,
            decisionState = heldState,
            basePlanVersion = 3,
            occFingerprint = fp,
            nowUtc = t0
        )
        assertEquals(0, blockedResult.autonomyAppliedCount)
        assertEquals("ACTIVE_HEALTH_HOLD_SUPERSEDES_ADAPTATION", blockedResult.fallbackToTierAReasons.single())

        // 3. Fast responder upward progression ALWAYS requires SUPERVISORY_APPROVAL OCC (never auto-applied!)
        val fastProfile = highFatigueProfile.copy(
            fatiguePosterior = FatiguePosteriorState(0.85, 0.12, 0.03, 0.40, false, false, "Fresh adapting")
        )
        val upResult = Engine20AdaptivePlanEngine.synthesizeAdaptiveAdjustments(
            profile = fastProfile,
            decisionState = DecisionStateSnapshot(),
            basePlanVersion = 3,
            occFingerprint = fp,
            nowUtc = t0
        )
        assertEquals(AthleteResponseArchetype.FAST_RESPONDER, upResult.archetype)
        val upAction = upResult.actions.single { it.kind == AdaptiveAdjustmentKind.PERFORMANCE_PROGRESSION_PROPOSAL }
        assertTrue(upAction.requiresApproval)
        assertFalse(upAction.autoAppliedUnderBoundedAutonomy)
        assertNotNull(upResult.occCandidate)
    }

    @Test fun hybridBalancerArchetypeCoordinatesLowerOverlapAndEarlyDurabilityDecoupling() {
        val hybridProfile = AthleteAdaptiveResponseProfile(
            accountId = owner,
            updatedAtUtc = t0,
            authorityMode = AdaptationAuthorityMode.SUPERVISORY_APPROVAL,
            consistentHistoryDays = 35,
            durabilityProfile = DurabilityProfile(DurabilityTier.EARLY_DECOUPLING, 6.2, 70, 2, 0, "Early drift"),
            muscleRecoverableBands = mapOf(
                "quadriceps" to MuscleRecoverableBand("quadriceps", MuscleVolumeToleranceTier.LOW_TOLERANCE, 10, 8, 14.0, 4.0, 10.0, "High quad soreness")
            ),
            fatiguePosterior = FatiguePosteriorState(0.15, 0.30, 0.55, 0.65, false, false, "Elevated lower strain"),
            kineticsProfile = IndividualKineticsProfile(true, 42.0, 9.0, 11, 220.0, 90.0, 40.0, 4, 65),
            promotionVerdicts = mapOf("MOD-B4" to ModelPromotionVerdict.ACTIVE_PERSONALIZED)
        )
        val eval = Engine20AdaptivePlanEngine.synthesizeAdaptiveAdjustments(
            profile = hybridProfile,
            decisionState = DecisionStateSnapshot(),
            basePlanVersion = 4,
            occFingerprint = fp,
            nowUtc = t0
        )
        assertEquals(AthleteResponseArchetype.HYBRID_BALANCER, eval.archetype)
        assertTrue(eval.actions.any { it.kind == AdaptiveAdjustmentKind.DURABILITY_ENDURANCE_HOLD })
        assertTrue(eval.actions.any { it.kind == AdaptiveAdjustmentKind.MUSCLE_VOLUME_TRIM })
        assertTrue(eval.actions.any { it.kind == AdaptiveAdjustmentKind.HYBRID_INTERFERENCE_DAMPING })
        assertTrue(eval.actions.any { it.kind == AdaptiveAdjustmentKind.INDIVIDUAL_TAPER_DURATION && it.taperDaysSuggestion == 11 })
        assertEquals(0, eval.autonomyAppliedCount)
        assertNotNull(eval.occCandidate)
    }
}
