package io.stridelab.privatealpha

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class Engine17CalisthenicsDagAndProgressionTest {

    private val nodes by lazy { Engine17CalisthenicsDagCatalog.buildNodes() }
    private fun node(id: String) = nodes.first { it.exercise.id == id }

    @Test fun dagCoversAll8CoreDomains30CatalogMovesAndAndOrGates() {
        val domainsPresent = nodes.map { it.domain }.toSet()
        assertEquals(CalisthenicsDomain.entries.toSet(), domainsPresent)
        assertTrue(nodes.size >= 38)

        // AND gate: Pistol squat requires both RFESS and Cossack mobility/strength + ankle check
        val pistol = node("cal-pistol-squat")
        assertFalse(Engine17CalisthenicsProgressionEngine.checkGateSatisfied(
            pistol, setOf("cal-rfess"), emptyMap(), emptySet()
        ))
        assertTrue(Engine17CalisthenicsProgressionEngine.checkGateSatisfied(
            pistol, setOf("cal-rfess", "cal-cossack-squat"), emptyMap(), setOf("Ankle dorsiflexion and hip flexion check")
        ))

        // POL-061 direct entry bypass: >= 3 verified clean reps unlocks Strict Pull-Up directly
        val pullUp = node("e2")
        assertTrue(Engine17CalisthenicsProgressionEngine.checkGateSatisfied(
            pullUp, emptySet(), mapOf("e2" to 4), emptySet()
        ))
        // OR gate: Band-Assisted OR Low-Vol Eccentric OR Ring Row also unlocks Pull-Up
        assertTrue(Engine17CalisthenicsProgressionEngine.checkGateSatisfied(
            pullUp, setOf("cal-ring-row"), emptyMap(), emptySet()
        ))
    }

    @Test fun pol047Requires2CleanSessionsRegressesOnBreakdownAndCapsBeginnerEccentrics() {
        val spec = Engine17CalisthenicsPolicyRegistry.specFor(CalisthenicsObjective.PUSH_UP_PROGRESSION)
        val e4 = node("e4")
        val decline = node("cal-decline-pushup")

        // Only 1 clean top-rep session -> MUST consolidate/hold, NOT promote automatically
        val oneSession = listOf(
            CalisthenicsNodeObservation("e4", LocalDate.of(2026, 11, 20), 12, null, 3, TechniqueQuality.CLEAN, TechniqueProvenance.ATHLETE_SELF_REPORTED_TECHNIQUE, 2, true)
        )
        val d1 = Engine17CalisthenicsProgressionEngine.evaluateNodeProgression(
            e4, decline, oneSession, spec, false, setOf("bench")
        )
        assertNotEquals(CalisthenicsDecisionAction.PROMOTE_NEXT_DAG_NODE, d1.action)

        // 2 consecutive clean sessions at top of range -> promotes to Decline Push-up
        val twoClean = oneSession + CalisthenicsNodeObservation(
            "e4", LocalDate.of(2026, 11, 23), 12, null, 3, TechniqueQuality.CLEAN, TechniqueProvenance.ATHLETE_SELF_REPORTED_TECHNIQUE, 2, true
        )
        val d2 = Engine17CalisthenicsProgressionEngine.evaluateNodeProgression(
            e4, decline, twoClean, spec, false, setOf("bench")
        )
        assertEquals(CalisthenicsDecisionAction.PROMOTE_NEXT_DAG_NODE, d2.action)
        assertEquals("cal-decline-pushup", d2.targetNodeId)

        // Technical failure / anterior shoulder pain on dips regresses to push-up parent
        val dip = node("cal-dip-assisted")
        val painObs = listOf(
            CalisthenicsNodeObservation("cal-dip-assisted", LocalDate.of(2026, 11, 24), 4, null, 3, TechniqueQuality.BREAKDOWN, TechniqueProvenance.ATHLETE_SELF_REPORTED_TECHNIQUE, 1, true, anteriorShoulderPain = true)
        )
        val reg = Engine17CalisthenicsProgressionEngine.evaluateNodeProgression(
            dip, null, painObs, spec, false, emptySet()
        )
        assertEquals(CalisthenicsDecisionAction.REGRESS_DAG_PARENT, reg.action)
        assertEquals("e4", reg.targetNodeId)

        // POL-047 / McHugh 2003 slow eccentric pull-up & Nordic curl cap for novices: <= 2 sets x 2-3 reps
        val negPull = node("cal-negative-pullup")
        val eccCap = Engine17CalisthenicsProgressionEngine.evaluateNodeProgression(
            negPull, null, emptyList(), spec, isNoviceOrUnaccustomed = true, availableEquipment = setOf("pullup_bar")
        )
        assertEquals(CalisthenicsDecisionAction.REDUCE_ECCENTRIC_TENDON_CAP, eccCap.action)
        assertEquals(2, eccCap.prescribedSets)
        assertEquals(3, eccCap.prescribedReps)
    }

    @Test fun scenarioS05ClusterOranchukIsometricsWeightedBridgeAndSharedLedger() {
        val pullSpec = Engine17CalisthenicsPolicyRegistry.specFor(CalisthenicsObjective.PULL_UP_PROGRESSION)
        // Scenario S05: 0 strict pull-ups prescribes Prerequisite Cluster (ap + negative-pullup + br)
        val s05 = Engine17CalisthenicsProgressionEngine.evaluateNodeProgression(
            node("e2"), null, emptyList(), pullSpec, true, setOf("pullup_bar")
        )
        assertEquals(CalisthenicsDecisionAction.PRESCRIBE_PREREQUISITE_CLUSTER, s05.action)
        assertEquals(listOf("ap", "cal-negative-pullup", "br"), s05.prerequisiteClusterNodeIds)

        // Oranchuk 2019 isometric 15 -> 30 -> 45 s and -30% tremor regression
        val hollow = node("core-hollow")
        val tremorObs = listOf(
            CalisthenicsNodeObservation("core-hollow", LocalDate.of(2026, 11, 22), null, 30, 3, TechniqueQuality.BREAKDOWN, TechniqueProvenance.ATHLETE_SELF_REPORTED_TECHNIQUE, 1, true, holdTremorOrCollapse = true)
        )
        val tremorDec = Engine17CalisthenicsProgressionEngine.evaluateNodeProgression(
            hollow, null, tremorObs, pullSpec, false, emptySet()
        )
        assertEquals(CalisthenicsDecisionAction.REDUCE_HOLD_DURATION_TREMOR, tremorDec.action)
        assertEquals(21, tremorDec.prescribedHoldSeconds)

        // Weighted vertical suspended pull-up tracks addedKg separately in ExternalAddedMass
        val weightedPu = node("cal-weighted-pullup")
        assertTrue(weightedPu.isVerticalSuspendedWeightedEligible)
        assertFalse(node("e4").isVerticalSuspendedWeightedEligible)
        val weightedObs = listOf(
            CalisthenicsNodeObservation("cal-weighted-pullup", LocalDate.of(2026, 11, 24), 8, null, 3, TechniqueQuality.CLEAN, TechniqueProvenance.ATHLETE_SELF_REPORTED_TECHNIQUE, 2, true, bodyweightMassKg = 72.0, externalAddedKg = 10.0)
        )
        val wDec = Engine17CalisthenicsProgressionEngine.evaluateNodeProgression(
            weightedPu, null, weightedObs, pullSpec, false, setOf("pullup_bar")
        )
        assertEquals(CalisthenicsDecisionAction.PROGRESS_ADDED_MASS, wDec.action)
        assertEquals(12.5, wDec.weightedSemantics!!.addedKg.value, 1e-6)
        assertEquals(72.0, wDec.weightedSemantics!!.recordedBodyMassKg!!.value, 1e-6)

        // Shared UnifiedLocalMuscleLedger excludes FRESH_SKILL_PRACTICE from hard sets & accumulates hold seconds
        val ledger = Engine17CalisthenicsProgressionEngine.exportToSharedLocalLedger(
            accountId = "cal-athlete",
            weekStartInclusive = LocalDate.of(2026, 11, 23),
            weekEndInclusive = LocalDate.of(2026, 11, 29),
            slots = listOf(
                CalisthenicsWorkoutSlot(node("cal-wall-plank"), CalisthenicsBlockRole.FRESH_SKILL_PRACTICE, 3, null, 25, 60, 2..3, timeBoxMinutes = 8),
                CalisthenicsWorkoutSlot(node("e4"), CalisthenicsBlockRole.PRIMARY_LEVERAGE_STRENGTH, 3, 8, null, 90, 2..3),
                CalisthenicsWorkoutSlot(hollow, CalisthenicsBlockRole.ISOMETRIC_HOLD, 3, null, 30, 60, 2..3)
            )
        )
        assertEquals(3, ledger.byMuscle["pectoralis_major"]?.directHardSets)
        assertEquals(90, ledger.byMuscle["abdominals"]?.isometricHoldSeconds)
        assertEquals(0, ledger.byMuscle["anterior_deltoid"]?.directHardSets ?: 0)
    }
}
