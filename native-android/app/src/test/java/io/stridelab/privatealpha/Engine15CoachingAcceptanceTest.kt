package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Step 01 Automated Coaching Acceptance Suite:
 * - Tier 1: Structural & mathematical invariants across 8 diverse athlete profiles.
 * - Tier 2: Periodization, non-stagnation, taper, and progression quality invariants.
 * - Persisted Targets: Verification of RunningStepV2 stepIntent, pace bounds, and optional HR bounds.
 * - Tier 3 Output: Prints week-by-week summaries for the 4 anchor profiles.
 */
class Engine15CoachingAcceptanceTest {
    private val owner = "step01-acceptance-runner"
    private val monday = LocalDate.of(2026, 11, 23)
    private val now = Instant.parse("2026-11-23T06:00:00Z")

    private data class AthleteProfile(
        val name: String,
        val request: PlanRequest
    )

    private fun profiles(): List<AthleteProfile> = listOf(
        AthleteProfile(
            name = "Beginner_5K",
            request = PlanRequest(
                goal = "Improve 5K",
                experience = 0,
                start = monday,
                weeks = 8,
                runDays = 3,
                days = setOf(2, 4, 6),
                longDay = 6,
                qualityCount = 1,
                qualityDays = setOf(2),
                weeklyKm = 18.0,
                longestKm = 7.0,
                targetWeeklyKm = 24.0,
                planningPace = 420.0,
                policyChoice = "engine15",
                recentMileageBasis = "SELF_REPORTED_AVERAGE",
                timeBudgetEnabled = false
            )
        ),
        AthleteProfile(
            name = "Intermediate_10K",
            request = PlanRequest(
                goal = "Improve 10K",
                experience = 1,
                start = monday,
                weeks = 10,
                runDays = 4,
                days = setOf(1, 2, 4, 6),
                longDay = 6,
                qualityCount = 1,
                qualityDays = setOf(2),
                weeklyKm = 35.0,
                longestKm = 12.0,
                targetWeeklyKm = 46.0,
                planningPace = 345.0,
                policyChoice = "engine15",
                recentMileageBasis = "SELF_REPORTED_AVERAGE",
                timeBudgetEnabled = false
            )
        ),
        AthleteProfile(
            name = "Advanced_5K",
            request = PlanRequest(
                goal = "Improve 5K",
                experience = 2,
                start = monday,
                weeks = 8,
                runDays = 5,
                days = setOf(1, 2, 3, 5, 7),
                longDay = 7,
                qualityCount = 2,
                qualityDays = setOf(2, 5),
                weeklyKm = 55.0,
                longestKm = 16.0,
                targetWeeklyKm = 64.0,
                planningPace = 295.0,
                benchmarkDistanceMeters = 5000,
                benchmarkDurationSeconds = 1140,
                policyChoice = "engine15",
                recentMileageBasis = "SELF_REPORTED_AVERAGE",
                timeBudgetEnabled = false
            )
        ),
        AthleteProfile(
            name = "Half_Marathon_Build",
            request = PlanRequest(
                goal = "Half marathon",
                experience = 1,
                start = monday,
                weeks = 12,
                runDays = 4,
                days = setOf(2, 4, 5, 7),
                longDay = 7,
                qualityCount = 1,
                qualityDays = setOf(2),
                weeklyKm = 35.0,
                longestKm = 13.0,
                targetWeeklyKm = 52.0,
                planningPace = 350.0,
                policyChoice = "engine15",
                recentMileageBasis = "SELF_REPORTED_AVERAGE",
                timeBudgetEnabled = false
            )
        ),
        AthleteProfile(
            name = "Marathon_Build",
            request = PlanRequest(
                goal = "Marathon",
                experience = 2,
                start = monday,
                weeks = 16,
                runDays = 5,
                days = setOf(1, 2, 4, 5, 7),
                longDay = 7,
                qualityCount = 1,
                qualityDays = setOf(2),
                weeklyKm = 45.0,
                longestKm = 18.0,
                targetWeeklyKm = 72.0,
                planningPace = 335.0,
                benchmarkDistanceMeters = 21097,
                benchmarkDurationSeconds = 5880,
                policyChoice = "engine15",
                recentMileageBasis = "SELF_REPORTED_AVERAGE",
                timeBudgetEnabled = false
            )
        ),
        AthleteProfile(
            name = "Returning_Runner",
            request = PlanRequest(
                goal = "Aerobic foundation",
                experience = 1,
                returning = true,
                start = monday,
                weeks = 8,
                runDays = 4,
                days = setOf(1, 3, 5, 7),
                longDay = 7,
                qualityCount = 0,
                weeklyKm = 40.0,
                longestKm = 14.0,
                targetWeeklyKm = 40.0,
                planningPace = 370.0,
                policyChoice = "engine15",
                recentMileageBasis = "SELF_REPORTED_AVERAGE",
                timeBudgetEnabled = false
            )
        ),
        AthleteProfile(
            name = "High_Mileage_Builder",
            request = PlanRequest(
                goal = "Aerobic foundation",
                experience = 2,
                start = monday,
                weeks = 12,
                runDays = 6,
                days = setOf(1, 2, 3, 4, 5, 7),
                longDay = 7,
                qualityCount = 1,
                qualityDays = setOf(2),
                weeklyKm = 60.0,
                longestKm = 20.0,
                targetWeeklyKm = 74.0,
                planningPace = 325.0,
                policyChoice = "engine15",
                recentMileageBasis = "SELF_REPORTED_AVERAGE",
                timeBudgetEnabled = false
            )
        ),
        AthleteProfile(
            name = "Time_Constrained_Runner",
            request = PlanRequest(
                goal = "Improve 10K",
                experience = 1,
                start = monday,
                weeks = 10,
                runDays = 4,
                days = setOf(1, 2, 4, 6),
                longDay = 6,
                qualityCount = 1,
                qualityDays = setOf(2),
                weeklyKm = 26.0,
                longestKm = 10.0,
                targetWeeklyKm = 32.0,
                planningPace = 350.0,
                minutes = 45,
                longRunMinutes = 90,
                dailyMinutes = 90,
                policyChoice = "engine15",
                recentMileageBasis = "SELF_REPORTED_AVERAGE",
                timeBudgetEnabled = true
            )
        )
    )

    private fun weekSessions(plan: PlanSnapshot, weekIndex: Int): List<PlannedSession> {
        val ws = plan.request.start.plusWeeks(weekIndex.toLong())
        val we = ws.plusWeeks(1)
        return plan.sessions.filter { it.date >= ws && it.date < we }.sortedBy { it.date }
    }

    @Test
    fun tier1StructuralInvariantsHoldAcrossAllEightProfiles() {
        val failures = mutableListOf<String>()
        for (profile in profiles()) {
            val req = profile.request
            val plan = try {
                Engine15IndependentPlanGenerator.generateWithHistory(
                    req, owner, emptyList(), "accept-${profile.name}", now
                )
            } catch (t: Throwable) {
                failures += "[${profile.name}] generateWithHistory failed: ${t.message}"
                continue
            }

            try {
                val approved = PlanOperations.approve(plan, listOf(plan))
                Engine15IndependentPlanGenerator.validateSaved(approved)
                val decoded = PlanSnapshot.decode(approved.encode(), owner)
                if (decoded != approved) {
                    failures += "[${profile.name}] JSON round-trip mismatch"
                }
            } catch (t: Throwable) {
                failures += "[${profile.name}] approve/validateSaved failed: ${t.message}"
            }

            // Single run per date, valid date range, allowed weekday
            val byDate = plan.sessions.groupBy { it.date }
            if (byDate.values.any { it.size > 1 }) {
                failures += "[${profile.name}] multiple runs scheduled on the same date"
            }
            if (plan.sessions.any {
                    it.date < req.start ||
                        it.date >= req.start.plusWeeks(req.weeks.toLong()) ||
                        it.date.dayOfWeek.value !in req.days
                }
            ) {
                failures += "[${profile.name}] session outside valid date/weekday bounds"
            }

            // Hard-session spacing >= 2 days
            val sorted = plan.sessions.sortedBy { it.date }
            for (i in sorted.indices) {
                for (j in i + 1 until sorted.size) {
                    if (Engine15IndependentPlanGenerator.conflict(sorted[i], sorted[j])) {
                        failures += "[${profile.name}] hard/long spacing conflict between ${sorted[i].id} and ${sorted[j].id}"
                    }
                }
            }

            // Baseline & reversible -2..+2 difficulty scaling
            if (plan.sessions.any { it.difficultyStep != 0 || it.difficultyBaseline != null }) {
                failures += "[${profile.name}] initial plan has non-zero difficultyStep or non-null difficultyBaseline"
            }
            val sampleSession = plan.sessions.firstOrNull { it.quality } ?: plan.sessions.first()
            for (delta in listOf(-2, -1, 0, 1, 2)) {
                val scaled = Engine15WorkoutDifficultyAdjuster.adjust(sampleSession, req, delta)
                if (!Engine15WorkoutDifficultyAdjuster.matchesAdjusted(scaled, req)) {
                    failures += "[${profile.name}] matchesAdjusted failed at delta=$delta"
                }
            }

            // Weekly mileage conservation + non-zero quality when requested
            val taperLen = Engine15IndependentVolumePlanner.raceTaperLength(req.goal, req.weeks)
            for (w in 0 until req.weeks) {
                val ws = weekSessions(plan, w)
                if (ws.size != req.runDays) {
                    failures += "[${profile.name}] W${w + 1} expected ${req.runDays} runs, got ${ws.size}"
                }
                val sumKm = ws.sumOf { it.distanceKm ?: 0.0 }
                if (sumKm <= 0.0 || abs(sumKm - ws.sumOf { (it.runningPrescription?.meters ?: 0) / 1000.0 }) > 0.25) {
                    failures += "[${profile.name}] W${w + 1} distance mismatch sumKm=$sumKm"
                }

                val isSuppressedPreTaper = Engine15IndependentVolumePlanner.isSuppressedPreTaperRecovery(req.goal, req.weeks, w)
                val isRecovery = (w % 4 == 3) && !isSuppressedPreTaper
                val isTaper = taperLen > 0 && w >= req.weeks - taperLen
                val isFinalTaper = taperLen > 0 && w == req.weeks - 1
                if (req.qualityCount > 0 && !isRecovery && !isFinalTaper) {
                    val expectedQ = if (isTaper) minOf(1, req.qualityCount) else req.qualityCount
                    val actualQ = ws.count { it.quality }
                    if (actualQ != expectedQ) {
                        failures += "[${profile.name}] W${w + 1} quality erased/mismatched: expected $expectedQ got $actualQ"
                    }
                }
            }

            if (profile.name == "Time_Constrained_Runner") {
                val overWeekday = plan.sessions.filter { it.kind != "long" && it.minutes > 45 }
                val overLong = plan.sessions.filter { it.kind == "long" && it.minutes > 90 }
                if (overWeekday.isNotEmpty() || overLong.isNotEmpty()) {
                    failures += "[${profile.name}] exceeded duration limits (weekday=${overWeekday.map { it.minutes }}, long=${overLong.map { it.minutes }})"
                }
            }

            // Safe warm-up (>= 8 min) and cool-down (>= 5 min) floors on every quality session
            plan.sessions.filter { it.quality }.forEach { qSession ->
                val segs = qSession.runningPrescription?.mixedSegments.orEmpty()
                val wuSec = segs.firstOrNull { it.purpose == "WARM_UP" }?.seconds ?: 0
                val cdSec = segs.lastOrNull { it.purpose == "COOL_DOWN" }?.seconds ?: 0
                if (wuSec < 8 * 60 || cdSec < 5 * 60) {
                    failures += "[${profile.name}] ${qSession.id} violated WU/CD floor (wu=${wuSec}s, cd=${cdSec}s)"
                }
            }
        }
        if (failures.isNotEmpty()) {
            fail("Tier 1 structural invariant failures:\n${failures.joinToString("\n")}")
        }
    }

    @Test
    fun tier2CoachingInvariantsHoldAcrossAllEightProfiles() {
        val failures = mutableListOf<String>()
        val plansByName = profiles().mapNotNull { profile ->
            runCatching {
                profile.name to Engine15IndependentPlanGenerator.generateWithHistory(
                    profile.request, owner, emptyList(), "coach-${profile.name}", now
                )
            }.onFailure {
                failures += "[${profile.name}] failed to generate: ${it.message}"
            }.getOrNull()
        }.toMap()

        // 1. Non-stagnation: quality work doses progress across build blocks
        for (name in listOf("Intermediate_10K", "Advanced_5K", "Half_Marathon_Build", "Marathon_Build", "High_Mileage_Builder")) {
            val plan = plansByName[name] ?: continue
            val loadingQuality = plan.sessions.filter { it.quality }
                .filter { s ->
                    val w = (ChronoUnit.DAYS.between(plan.request.start, s.date) / 7L).toInt()
                    val taperLen = Engine15IndependentVolumePlanner.raceTaperLength(plan.request.goal, plan.request.weeks)
                    taperLen == 0 || w < plan.request.weeks - taperLen
                }
            val workSeconds = loadingQuality.map { s ->
                s.runningPrescription?.mixedSegments?.filter { it.purpose == "WORK" }?.sumOf { it.seconds ?: 0 } ?: 0
            }
            val distinctDoses = loadingQuality.mapIndexed { idx, s ->
                "${s.runningPrescription?.family}:${workSeconds[idx]}"
            }.distinct()
            if (distinctDoses.size < 2 || workSeconds.maxOrNull()!! <= workSeconds.first()) {
                failures += "[$name] quality prescriptions stagnated across loading weeks: $distinctDoses"
            }
        }

        // 2. Half_Marathon_Build progression, long-run build & taper
        plansByName["Half_Marathon_Build"]?.let { hm ->
            val weeklyKm = (0 until hm.request.weeks).map { w -> weekSessions(hm, w).sumOf { it.distanceKm ?: 0.0 } }
            val peakKm = weeklyKm.maxOrNull() ?: 0.0
            val peakLong = hm.sessions.filter { it.kind == "long" }.maxOfOrNull { it.distanceKm ?: 0.0 } ?: 0.0
            if (peakKm < 46.0) failures += "[Half_Marathon_Build] peak weekly km too low ($peakKm < 46.0)"
            if (peakLong < 16.0) failures += "[Half_Marathon_Build] peak long run too short ($peakLong < 16.0)"
            if (weeklyKm[10] <= weeklyKm[11] || weeklyKm[11] >= peakKm * 0.70) {
                failures += "[Half_Marathon_Build] improper 2-week taper (W11=${weeklyKm[10]}, W12=${weeklyKm[11]}, peak=$peakKm)"
            }
        }

        // 3. Marathon_Build progression, long-run build & 3-week taper
        plansByName["Marathon_Build"]?.let { fm ->
            val weeklyKm = (0 until fm.request.weeks).map { w -> weekSessions(fm, w).sumOf { it.distanceKm ?: 0.0 } }
            val peakKm = weeklyKm.maxOrNull() ?: 0.0
            val peakLong = fm.sessions.filter { it.kind == "long" }.maxOfOrNull { it.distanceKm ?: 0.0 } ?: 0.0
            if (peakKm < 64.0) failures += "[Marathon_Build] peak weekly km too low ($peakKm < 64.0)"
            if (peakLong < 26.0) failures += "[Marathon_Build] peak long run too short ($peakLong < 26.0)"
            if (!(weeklyKm[13] > weeklyKm[14] && weeklyKm[14] > weeklyKm[15]) || weeklyKm[15] >= peakKm * 0.65) {
                failures += "[Marathon_Build] improper 3-week taper (W14=${weeklyKm[13]}, W15=${weeklyKm[14]}, W16=${weeklyKm[15]}, peak=$peakKm)"
            }
        }

        // 4. Intermediate_10K build & 2-week taper
        plansByName["Intermediate_10K"]?.let { p10k ->
            val weeklyKm = (0 until p10k.request.weeks).map { w -> weekSessions(p10k, w).sumOf { it.distanceKm ?: 0.0 } }
            val peakKm = weeklyKm.maxOrNull() ?: 0.0
            if (peakKm < 42.0) failures += "[Intermediate_10K] peak weekly km too low ($peakKm < 42.0)"
            if (weeklyKm[8] <= weeklyKm[9]) failures += "[Intermediate_10K] W9 (${weeklyKm[8]}) should exceed W10 taper (${weeklyKm[9]})"
        }

        // 5. Beginner_5K & Advanced_5K final taper week
        for (k in listOf("Beginner_5K", "Advanced_5K")) {
            plansByName[k]?.let { p5k ->
                val weeklyKm = (0 until p5k.request.weeks).map { w -> weekSessions(p5k, w).sumOf { it.distanceKm ?: 0.0 } }
                val peakKm = weeklyKm.maxOrNull() ?: 0.0
                if (weeklyKm.last() >= peakKm * 0.90) {
                    failures += "[$k] missing 5K final taper cut (W8=${weeklyKm.last()}, peak=$peakKm)"
                }
            }
        }

        // 6. High_Mileage_Builder: no arbitrary initial regression & realistic build
        plansByName["High_Mileage_Builder"]?.let { hi ->
            val weeklyKm = (0 until hi.request.weeks).map { w -> weekSessions(hi, w).sumOf { it.distanceKm ?: 0.0 } }
            if (weeklyKm.first() < 56.0) failures += "[High_Mileage_Builder] W1 dropped too far below 60 km (${weeklyKm.first()})"
            if ((weeklyKm.maxOrNull() ?: 0.0) < 68.0) failures += "[High_Mileage_Builder] peak km too low (${weeklyKm.maxOrNull()})"
        }

        // 7. Returning_Runner: conservative re-entry & ramp cap
        plansByName["Returning_Runner"]?.let { ret ->
            val weeklyKm = (0 until ret.request.weeks).map { w -> weekSessions(ret, w).sumOf { it.distanceKm ?: 0.0 } }
            if (weeklyKm.first() > 26.0) failures += "[Returning_Runner] W1 too aggressive (${weeklyKm.first()} > 26.0)"
            if ((weeklyKm.maxOrNull() ?: 99.0) > 32.0) failures += "[Returning_Runner] peak too high (${weeklyKm.maxOrNull()} > 32.0)"
        }

        if (failures.isNotEmpty()) {
            fail("Tier 2 coaching invariant failures:\n${failures.joinToString("\n")}")
        }
    }

    @Test
    fun tier1PersistedTargetsCheck() {
        val profile = profiles().first { it.name == "Marathon_Build" }
        val plan = Engine15IndependentPlanGenerator.generateWithHistory(
            profile.request, owner, emptyList(), "persist-targets", now
        )
        val approved = PlanOperations.approve(plan, listOf(plan))
        val qualitySteps = approved.sessions.filter { it.quality }.flatMap { it.runningPrescription!!.mixedSegments!! }
        assertTrue(qualitySteps.isNotEmpty())
        assertTrue(
            "Expected persisted stepIntent on every quality step",
            qualitySteps.all { it.stepIntent != null }
        )
        assertTrue(
            "Expected persisted min<=max pace bounds on every quality step",
            qualitySteps.all {
                it.targetPaceMinSecPerKm != null &&
                    it.targetPaceMaxSecPerKm != null &&
                    it.targetPaceMinSecPerKm!! <= it.targetPaceMaxSecPerKm!!
            }
        )
        assertTrue(
            "HR targets must remain strictly null without athlete HR zone profile",
            qualitySteps.all { it.targetHrMinBpm == null && it.targetHrMaxBpm == null }
        )

        // Full JSON serialization / SQLite restoration lifecycle check
        val decoded = PlanSnapshot.decode(JSONObject(approved.encode().toString()), owner)
        assertEquals(approved, decoded)
        Engine15IndependentPlanGenerator.validateSaved(decoded)

        // Legacy pre-Step-01 JSON fixture compatibility check (strip new optional RunningStepV2 fields)
        val legacyJson = JSONObject(approved.encode().toString())
        val rawSessions = legacyJson.getJSONArray("sessions")
        for (i in 0 until rawSessions.length()) {
            val sJson = rawSessions.getJSONObject(i)
            val rxJson = sJson.optJSONObject("runningPrescription") ?: continue
            val segs = rxJson.optJSONArray("segments") ?: continue
            for (j in 0 until segs.length()) {
                val seg = segs.getJSONObject(j)
                seg.remove("stepIntent")
                seg.remove("targetPaceMinSecPerKm")
                seg.remove("targetPaceMaxSecPerKm")
                seg.remove("targetHrMinBpm")
                seg.remove("targetHrMaxBpm")
            }
        }
        val decodedLegacy = PlanSnapshot.decode(legacyJson, owner)
        val legacyQualitySteps = decodedLegacy.sessions.filter { it.quality }
            .flatMap { it.runningPrescription!!.mixedSegments!! }
        assertTrue(legacyQualitySteps.all {
            it.stepIntent == null && it.targetPaceMinSecPerKm == null && it.targetHrMinBpm == null
        })
        Engine15IndependentPlanGenerator.validateSaved(decodedLegacy)

        // Difficulty adjuster (-2..+2) preserves stepIntent & pace bounds on scaled workouts
        val qualitySession = approved.sessions.first { it.quality }
        for (delta in listOf(-1, 1, 2)) {
            val scaled = Engine15WorkoutDifficultyAdjuster.adjust(qualitySession, profile.request, delta)
            val scaledSteps = scaled.runningPrescription!!.mixedSegments!!
            assertTrue(scaledSteps.all {
                it.stepIntent != null &&
                    it.targetPaceMinSecPerKm != null &&
                    it.targetPaceMaxSecPerKm != null &&
                    it.targetPaceMinSecPerKm!! <= it.targetPaceMaxSecPerKm!! &&
                    it.targetHrMinBpm == null &&
                    it.targetHrMaxBpm == null
            })
            assertTrue(Engine15WorkoutDifficultyAdjuster.matchesAdjusted(scaled, profile.request))
        }
        val reset = Engine15WorkoutDifficultyAdjuster.adjust(
            Engine15WorkoutDifficultyAdjuster.adjust(qualitySession, profile.request, 2),
            profile.request,
            0
        )
        assertEquals(qualitySession, reset)

        // Verified 5-zone HR profile populates optional HR bounds; missing profile keeps them strictly null
        assertNull(Engine15PaceZoneEstimator.hrBoundsForIntent(null, RunningStepIntent.THRESHOLD))
        val verifiedZones = ActivitySourceHeartRateZones(190, 172, 50, "percent_hrr", listOf(130, 148, 162, 174, 190))
        val thresholdHr = Engine15PaceZoneEstimator.hrBoundsForIntent(verifiedZones, RunningStepIntent.THRESHOLD)
        assertEquals(163 to 174, thresholdHr)
    }

    @Test
    fun tier1QualityReconciliationLadderStepDownAndExplicitDeferral() {
        // Step 3: tight 34-min weekday limit forces lower-dose rung step-down in the same family,
        // preserving quality workouts and >= 8 min WU / >= 5 min CD without erasing qualityCount to 0.
        val tightStepDownReq = PlanRequest(
            goal = "Improve 10K",
            experience = 1,
            start = monday,
            weeks = 6,
            runDays = 4,
            days = setOf(1, 2, 4, 6),
            longDay = 6,
            qualityCount = 1,
            qualityDays = setOf(2),
            weeklyKm = 30.0,
            longestKm = 11.0,
            targetWeeklyKm = 30.0,
            planningPace = 300.0,
            minutes = 36,
            longRunMinutes = 75,
            dailyMinutes = 90,
            policyChoice = "engine15",
            recentMileageBasis = "SELF_REPORTED_AVERAGE",
            timeBudgetEnabled = true
        )
        val steppedPlan = Engine15IndependentPlanGenerator.generateWithHistory(
            tightStepDownReq, owner, emptyList(), "ladder-stepdown", now
        )
        val approvedStepped = PlanOperations.approve(steppedPlan, listOf(steppedPlan))
        Engine15IndependentPlanGenerator.validateSaved(approvedStepped)
        val steppedQuality = approvedStepped.sessions.filter { it.quality }
        assertTrue("Expected quality sessions preserved via Step 3 step-down", steppedQuality.isNotEmpty())
        steppedQuality.forEach { q ->
            val segs = q.runningPrescription!!.mixedSegments!!
            val totalSec = segs.sumOf { it.seconds ?: 0 }
            val wuSec = segs.first { it.purpose == "WARM_UP" }.seconds!!
            val cdSec = segs.last { it.purpose == "COOL_DOWN" }.seconds!!
            assertTrue("Expected WU >= 8m, got ${wuSec}s", wuSec >= 8 * 60)
            assertTrue("Expected CD >= 5m, got ${cdSec}s", cdSec >= 5 * 60)
            assertTrue("Expected session total ${totalSec}s <= 36m", totalSec <= 36 * 60)
        }

        // Step 4: impossible 18-min quality session limit (while easy distance still fits 18m) must explicitly defer instead of erasing quality to 0.
        val impossibleQualityReq = tightStepDownReq.copy(
            weeklyKm = 12.0,
            longestKm = 6.0,
            targetWeeklyKm = 12.0,
            minutes = 18
        )
        val deferred = Engine15IndependentPlanGenerator.previewAutoReconciled(
            impossibleQualityReq, owner, "ladder-defer", now
        )
        assertTrue("Expected explicit Deferred result, got $deferred", deferred is Engine15IndependentPlanDecision.Deferred)
        assertEquals(
            "QUALITY_RECONCILIATION_UNSAFE_OR_INFEASIBLE",
            (deferred as Engine15IndependentPlanDecision.Deferred).reasonCode
        )
        val err = assertThrows(PlanConstraint::class.java) {
            Engine15IndependentPlanGenerator.generateWithHistory(
                impossibleQualityReq, owner, emptyList(), "ladder-defer-gen", now
            )
        }
        assertTrue(err.message.orEmpty().contains("QUALITY_RECONCILIATION_UNSAFE_OR_INFEASIBLE"))
    }

    @Test
    fun step02EnergySystemRecoveryRatiosAndUphillHillCuesAreEnforced() {
        val gfReq = PlanRequest(
            goal = "General fitness",
            experience = 1,
            start = monday,
            weeks = 12,
            runDays = 4,
            days = setOf(1, 3, 5, 7),
            longDay = 7,
            qualityCount = 1,
            qualityDays = setOf(3),
            weeklyKm = 36.0,
            longestKm = 12.0,
            targetWeeklyKm = 44.0,
            planningPace = 345.0,
            policyChoice = "engine15",
            recentMileageBasis = "SELF_REPORTED_AVERAGE",
            timeBudgetEnabled = false
        )
        val allReqs = (profiles().filter {
            it.name in setOf("Beginner_5K", "Intermediate_10K", "Advanced_5K", "Half_Marathon_Build", "Marathon_Build")
        }.map { it.name to it.request }) + ("Train_Your_Way_General_Fitness" to gfReq)

        var sawHill = false
        var sawShortTrack = false
        var sawThreshold = false
        var sawBrokenTempo = false
        var sawStrides = false
        var sawRecovery = false

        for ((name, req) in allReqs) {
            val plan = Engine15IndependentPlanGenerator.generateWithHistory(
                req, owner, emptyList(), "step02-ratios-$name", now
            )
            val zones = Engine15PaceZoneEstimator.estimate(req)

            // Recovery Runs: Easy max + 15-30 s/km
            plan.sessions.filter { it.runningPrescription?.archetypeId == Engine15WorkoutArchetype.RECOVERY_RUN.id }
                .forEach { rec ->
                    sawRecovery = true
                    val seg = rec.runningPrescription!!.segments.single()
                    assertEquals(RunningStepIntent.RECOVERY, seg.stepIntent)
                    assertTrue(
                        "[$name] Recovery run paceMin ${seg.targetPaceMinSecPerKm} must be >= easyMax (${zones.easy.slowSecondsPerKm}) + 14s",
                        seg.targetPaceMinSecPerKm!! >= zones.easy.slowSecondsPerKm.toInt() + 14
                    )
                }

            // Easy Run + Strides: 20s stride with 60-90s recovery (1:3..1:4.5)
            plan.sessions.filter { it.runningPrescription?.archetypeId == Engine15WorkoutArchetype.EASY_RUN_WITH_STRIDES.id }
                .forEach { st ->
                    sawStrides = true
                    val mixed = st.runningPrescription!!.mixedSegments!!
                    val strides = mixed.filter { it.stepIntent == RunningStepIntent.STRIDE }
                    val recs = mixed.filter { it.purpose == "RECOVERY" }
                    assertTrue("[$name] Expected 4..8 strides, got ${strides.size}", strides.size in 4..8)
                    assertTrue(strides.all { it.seconds == 20 })
                    assertTrue("[$name] Expected 60-90s stride recovery, got ${recs.map { it.seconds }}", recs.all { it.seconds in 60..90 })
                }

            for (q in plan.sessions.filter { it.quality }) {
                val arch = Engine15WorkoutArchetype.fromId(q.runningPrescription?.archetypeId)
                val segs = q.runningPrescription!!.mixedSegments!!
                val works = segs.filter { it.purpose == "WORK" }
                val recs = segs.filter { it.purpose == "RECOVERY" }

                when (arch) {
                    Engine15WorkoutArchetype.SHORT_TRACK_REPEATS -> {
                        sawShortTrack = true
                        assertTrue("[$name] Expected 400m repeats", works.all { it.meters == 400 })
                        assertTrue(
                            "[$name] Short Track Repeats must use 200m or 60-75s recovery (never flat 120s), got $recs",
                            recs.all { it.meters == 200 || (it.seconds in 60..75) }
                        )
                    }
                    Engine15WorkoutArchetype.CLASSIC_VO2MAX_INTERVALS -> {
                        val allDist = works.all { it.meters != null }
                        if (allDist) {
                            assertTrue(works.all { it.meters in setOf(800, 1000) })
                            assertTrue(recs.all { it.meters == 400 || (it.seconds in 90..150) })
                        } else {
                            assertTrue(works.all { it.seconds in 180..240 })
                            assertTrue(recs.all { it.seconds in 120..150 })
                        }
                    }
                    Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS -> {
                        sawThreshold = true
                        val firstWork = works.first()
                        if (firstWork.meters == 1000 || firstWork.seconds in 240..360) {
                            assertTrue(
                                "[$name] 1km / 4-6m threshold intervals must use 60s recovery (never flat 120s), got ${recs.map { it.seconds }}",
                                recs.all { it.seconds == 60 }
                            )
                        } else {
                            assertTrue(
                                "[$name] 2km / 7-10m threshold intervals must use 90s recovery (never flat 120s), got ${recs.map { it.seconds }}",
                                recs.all { it.seconds == 90 }
                            )
                        }
                    }
                    Engine15WorkoutArchetype.BROKEN_TEMPO -> {
                        sawBrokenTempo = true
                        assertTrue(recs.isNotEmpty() && recs.all { it.seconds in 90..120 })
                    }
                    Engine15WorkoutArchetype.SHORT_HILL_SPRINTS -> {
                        sawHill = true
                        assertTrue(works.all { it.seconds in 45..60 })
                        assertTrue(recs.all { it.seconds in 90..120 })
                        assertTrue(works.all { "uphill" in it.effort.lowercase() && "grade" in it.effort.lowercase() })
                        assertTrue(works.all { it.targetPaceMinSecPerKm!! > zones.intervals.slowSecondsPerKm.toInt() })
                    }
                    Engine15WorkoutArchetype.LONG_AEROBIC_HILLS -> {
                        sawHill = true
                        assertTrue(works.all { it.seconds in 90..120 })
                        assertTrue(recs.all { it.seconds in 120..150 })
                        assertTrue(works.all { "uphill" in it.effort.lowercase() && "grade" in it.effort.lowercase() })
                        assertTrue(works.all { it.targetPaceMinSecPerKm!! > zones.intervals.slowSecondsPerKm.toInt() })
                    }
                    Engine15WorkoutArchetype.AEROBIC_FARTLEK -> {
                        assertTrue(works.all { it.seconds in 120..180 })
                        assertTrue(recs.all { it.seconds in 90..120 })
                    }
                    else -> {}
                }
            }
        }
        assertTrue("Expected hills to be exercised", sawHill)
        assertTrue("Expected short track repeats to be exercised", sawShortTrack)
        assertTrue("Expected threshold cruise intervals to be exercised", sawThreshold)
        assertTrue("Expected broken tempo to be exercised", sawBrokenTempo)
        assertTrue("Expected easy run + strides to be exercised", sawStrides)
        assertTrue("Expected recovery runs to be exercised", sawRecovery)
    }

    @Test
    fun step02DistanceBasedWorkoutsAndPyramidsReconcileExactMetersAndScaleCleanly() {
        val adv5kReq = profiles().first { it.name == "Advanced_5K" }.request
        val plan5k = Engine15IndependentPlanGenerator.generateWithHistory(
            adv5kReq, owner, emptyList(), "step02-dist-5k", now
        )
        val distSessions = plan5k.sessions.filter { q ->
            q.quality && q.runningPrescription?.mixedSegments?.any { it.meters != null } == true
        }
        assertTrue("Expected distance-based quality workouts in Advanced_5K", distSessions.isNotEmpty())
        assertTrue(
            "Expected Speed-Endurance Pyramid in Advanced_5K",
            distSessions.any { it.runningPrescription?.archetypeId == Engine15WorkoutArchetype.SPEED_ENDURANCE_PYRAMID.id }
        )

        val pyramid = distSessions.first {
            it.runningPrescription?.archetypeId == Engine15WorkoutArchetype.SPEED_ENDURANCE_PYRAMID.id
        }
        val pyramidWorks = pyramid.runningPrescription!!.mixedSegments!!.filter { it.purpose == "WORK" }
        assertTrue(pyramidWorks.any { it.stepIntent == RunningStepIntent.VO2_INTERVAL })
        assertTrue(pyramidWorks.any { it.stepIntent == RunningStepIntent.THRESHOLD })

        for (session in distSessions) {
            val rx = session.runningPrescription!!
            val totalM = rx.mixedSegments!!.sumOf { it.meters ?: it.distanceEquivalentMeters!! }
            assertEquals((session.distanceKm!! * 1000.0).toInt(), totalM)
            assertTrue(rx.mixedSegments.all { it.stepIntent != null && it.targetPaceMinSecPerKm != null && it.targetPaceMaxSecPerKm != null })
            for (delta in listOf(-2, -1, 0, 1, 2)) {
                val scaled = Engine15WorkoutDifficultyAdjuster.adjust(session, adv5kReq, delta)
                assertTrue(
                    "Difficulty adjust delta=$delta failed matchesAdjusted on ${session.title}",
                    Engine15WorkoutDifficultyAdjuster.matchesAdjusted(scaled, adv5kReq)
                )
            }
        }

        // Verify Easy Run + Strides & Fast-Finish/MP Long Run also scale -2..+2 and validate
        val hmReq = profiles().first { it.name == "Half_Marathon_Build" }.request
        val planHm = Engine15IndependentPlanGenerator.generateWithHistory(
            hmReq, owner, emptyList(), "step02-dist-hm", now
        )
        val stridesSession = planHm.sessions.first {
            it.runningPrescription?.archetypeId == Engine15WorkoutArchetype.EASY_RUN_WITH_STRIDES.id
        }
        val progLong = planHm.sessions.first {
            it.runningPrescription?.archetypeId == Engine15WorkoutArchetype.FAST_FINISH_PROGRESSIVE_LONG_RUN.id
        }
        for (targetSession in listOf(stridesSession, progLong)) {
            for (delta in listOf(-2, -1, 0, 1, 2)) {
                val scaled = Engine15WorkoutDifficultyAdjuster.adjust(targetSession, hmReq, delta)
                assertTrue(
                    "Difficulty adjust delta=$delta failed on ${targetSession.title}",
                    Engine15WorkoutDifficultyAdjuster.matchesAdjusted(scaled, hmReq)
                )
            }
        }
        val adjustedPlan = PlanOperations.adjustDifficulty(plan5k, pyramid.id, 1)
        val approved = PlanOperations.approve(adjustedPlan, listOf(adjustedPlan))
        Engine15IndependentPlanGenerator.validateSaved(approved)
        assertEquals(approved, PlanSnapshot.decode(JSONObject(approved.encode().toString()), owner))
    }

    @Test
    fun step02ProgressiveAndRacePaceLongRunsRespectBuildPeakAndSafetyGuardrails() {
        val hmPlan = Engine15IndependentPlanGenerator.generateWithHistory(
            profiles().first { it.name == "Half_Marathon_Build" }.request,
            owner, emptyList(), "step02-long-hm", now
        )
        val fmPlan = Engine15IndependentPlanGenerator.generateWithHistory(
            profiles().first { it.name == "Marathon_Build" }.request,
            owner, emptyList(), "step02-long-fm", now
        )
        val retPlan = Engine15IndependentPlanGenerator.generateWithHistory(
            profiles().first { it.name == "Returning_Runner" }.request,
            owner, emptyList(), "step02-long-ret", now
        )

        val hmRacePaceLongs = hmPlan.sessions.filter { it.title == "HM-Pace Long Run" }
        assertTrue("Expected HM-Pace Long Runs in Half_Marathon_Build", hmRacePaceLongs.isNotEmpty())
        hmRacePaceLongs.forEach { longRun ->
            val wkIdx = (ChronoUnit.DAYS.between(hmPlan.request.start, longRun.date) / 7L).toInt()
            val taperLen = Engine15IndependentVolumePlanner.raceTaperLength(hmPlan.request.goal, hmPlan.request.weeks)
            assertFalse("HM-Pace Long Run must never land on recovery week W${wkIdx + 1}", wkIdx % 4 == 3)
            assertTrue("HM-Pace Long Run must never land on taper week W${wkIdx + 1}", wkIdx < hmPlan.request.weeks - taperLen)
            val finish = longRun.runningPrescription!!.segments.last()
            assertEquals(RunningStepIntent.RACE_PACE, finish.stepIntent)
            val ratio = finish.meters.toDouble() / (longRun.distanceKm!! * 1000.0)
            assertTrue("HM-Pace finish share $ratio out of [0.24, 0.41]", ratio in 0.24..0.41)
            assertTrue("HM-Pace finish ${finish.meters}m out of [4000, 10000]", finish.meters in 4000..10000)
        }

        val fmRacePaceLongs = fmPlan.sessions.filter { it.title == "Marathon-Pace Long Run" }
        assertTrue("Expected Marathon-Pace Long Runs in Marathon_Build", fmRacePaceLongs.isNotEmpty())
        fmRacePaceLongs.forEach { longRun ->
            val wkIdx = (ChronoUnit.DAYS.between(fmPlan.request.start, longRun.date) / 7L).toInt()
            val taperLen = Engine15IndependentVolumePlanner.raceTaperLength(fmPlan.request.goal, fmPlan.request.weeks)
            assertFalse("MP Long Run must never land on recovery week W${wkIdx + 1}", wkIdx % 4 == 3)
            assertTrue("MP Long Run must never land on taper week W${wkIdx + 1}", wkIdx < fmPlan.request.weeks - taperLen)
            val finish = longRun.runningPrescription!!.segments.last()
            assertEquals(RunningStepIntent.RACE_PACE, finish.stepIntent)
            val ratio = finish.meters.toDouble() / (longRun.distanceKm!! * 1000.0)
            assertTrue("MP finish share $ratio out of [0.24, 0.41]", ratio in 0.24..0.41)
            assertTrue("MP finish ${finish.meters}m out of [6000, 14000]", finish.meters in 6000..14000)
        }

        // Recovery & taper weeks across HM & Marathon must be strictly Steady Aerobic Long Run
        for (plan in listOf(hmPlan, fmPlan)) {
            val taperLen = Engine15IndependentVolumePlanner.raceTaperLength(plan.request.goal, plan.request.weeks)
            plan.sessions.filter { it.kind == "long" }.forEach { longRun ->
                val wkIdx = (ChronoUnit.DAYS.between(plan.request.start, longRun.date) / 7L).toInt()
                val isSuppressed = Engine15IndependentVolumePlanner.isSuppressedPreTaperRecovery(plan.request.goal, plan.request.weeks, wkIdx)
                val isRecovery = wkIdx % 4 == 3 && !isSuppressed
                val isTaper = wkIdx >= plan.request.weeks - taperLen
                if (isRecovery || isTaper) {
                    assertEquals(
                        "Recovery/taper long run W${wkIdx + 1} must be Steady Aerobic Long Run",
                        Engine15WorkoutArchetype.STEADY_AEROBIC_LONG_RUN.id,
                        longRun.runningPrescription?.archetypeId
                    )
                }
            }
        }
        assertTrue(retPlan.sessions.none {
            it.runningPrescription?.archetypeId == Engine15WorkoutArchetype.FAST_FINISH_PROGRESSIVE_LONG_RUN.id
        })
    }

    @Test
    fun step02BeginnerAndReturningProgressionHoldAndTrackVo2Exclusion() {
        val beginner2qReq = profiles().first { it.name == "Beginner_5K" }.request.copy(
            runDays = 5,
            days = setOf(1, 2, 3, 5, 7),
            longDay = 7,
            qualityCount = 2,
            qualityDays = setOf(2, 5),
            weeklyKm = 24.0,
            longestKm = 8.0,
            targetWeeklyKm = 30.0
        )
        val returning2qReq = beginner2qReq.copy(
            goal = "Improve 10K",
            experience = 1,
            returning = true,
            weeklyKm = 40.0,
            longestKm = 13.0,
            targetWeeklyKm = 40.0
        )
        val trackVo2Ids = setOf(
            Engine15WorkoutArchetype.SHORT_TRACK_REPEATS.id,
            Engine15WorkoutArchetype.CLASSIC_VO2MAX_INTERVALS.id,
            Engine15WorkoutArchetype.SPEED_ENDURANCE_PYRAMID.id
        )

        for ((label, req) in listOf("Beginner_2Q" to beginner2qReq, "Returning_2Q" to returning2qReq)) {
            val plan = Engine15IndependentPlanGenerator.generateWithHistory(
                req, owner, emptyList(), "step02-cons-$label", now
            )
            Engine15IndependentPlanGenerator.validateSaved(plan)
            for (w in 0 until req.weeks) {
                val qCount = weekSessions(plan, w).count { it.quality }
                assertTrue("[$label] W${w + 1} exceeded 1 quality session cap (got $qCount)", qCount <= 1)
            }
            val quality = plan.sessions.filter { it.quality }
            assertTrue("[$label] Expected quality sessions", quality.size >= 4)
            assertTrue(
                "[$label] Track VO2max archetypes must be excluded",
                quality.none { it.runningPrescription?.archetypeId in trackVo2Ids }
            )
            val w1Work = quality[0].runningPrescription!!.mixedSegments!!.filter { it.purpose == "WORK" }.sumOf { it.seconds ?: 0 }
            val w2Work = quality[1].runningPrescription!!.mixedSegments!!.filter { it.purpose == "WORK" }.sumOf { it.seconds ?: 0 }
            assertEquals("[$label] Weeks 1-2 must hold entry dose before stepping up", w1Work, w2Work)
        }
    }

    @Test
    fun step02TrainYourWayGeneralFitnessAndAerobicFoundationCoverage() {
        val gfReq = PlanRequest(
            goal = "General fitness",
            experience = 1,
            start = monday,
            weeks = 12,
            runDays = 4,
            days = setOf(1, 3, 5, 7),
            longDay = 7,
            qualityCount = 1,
            qualityDays = setOf(3),
            weeklyKm = 36.0,
            longestKm = 12.0,
            targetWeeklyKm = 44.0,
            planningPace = 345.0,
            policyChoice = "engine15",
            recentMileageBasis = "SELF_REPORTED_AVERAGE",
            timeBudgetEnabled = false
        )
        val afReq = PlanRequest(
            goal = "Aerobic foundation",
            experience = 1,
            start = monday,
            weeks = 8,
            runDays = 4,
            days = setOf(1, 3, 5, 7),
            longDay = 7,
            qualityCount = 1,
            qualityDays = setOf(3),
            weeklyKm = 32.0,
            longestKm = 11.0,
            targetWeeklyKm = 40.0,
            planningPace = 360.0,
            policyChoice = "engine15",
            recentMileageBasis = "SELF_REPORTED_AVERAGE",
            timeBudgetEnabled = false
        )

        val gfPlan = Engine15IndependentPlanGenerator.generateWithHistory(gfReq, owner, emptyList(), "tyw-gf", now)
        val afPlan = Engine15IndependentPlanGenerator.generateWithHistory(afReq, owner, emptyList(), "tyw-af", now)

        for (plan in listOf(gfPlan, afPlan)) {
            val approved = PlanOperations.approve(plan, listOf(plan))
            Engine15IndependentPlanGenerator.validateSaved(approved)
            assertEquals(approved, PlanSnapshot.decode(JSONObject(approved.encode().toString()), owner))
            assertTrue(plan.sessions.any {
                it.runningPrescription?.archetypeId == Engine15WorkoutArchetype.EASY_RUN_WITH_STRIDES.id
            })
            // 4-week recovery cycles must hold zero quality sessions
            for (w in 0 until plan.request.weeks) {
                val ws = weekSessions(plan, w)
                if (w % 4 == 3) assertTrue("W${w + 1} recovery must have 0 quality", ws.none { it.quality })
                else assertEquals("W${w + 1} loading must preserve 1 quality", 1, ws.count { it.quality })
            }
        }

        val gfArchetypes = gfPlan.sessions.mapNotNull { it.runningPrescription?.archetypeId }.toSet()
        assertTrue(Engine15WorkoutArchetype.THRESHOLD_CRUISE_INTERVALS.id in gfArchetypes)
        assertTrue(Engine15WorkoutArchetype.BROKEN_TEMPO.id in gfArchetypes)
        assertTrue(Engine15WorkoutArchetype.AEROBIC_FARTLEK.id in gfArchetypes)
        assertTrue(Engine15WorkoutArchetype.CLASSIC_VO2MAX_INTERVALS.id in gfArchetypes)
        assertTrue(Engine15WorkoutArchetype.FAST_FINISH_PROGRESSIVE_LONG_RUN.id in gfArchetypes)

        val afArchetypes = afPlan.sessions.mapNotNull { it.runningPrescription?.archetypeId }.toSet()
        assertTrue(Engine15WorkoutArchetype.AEROBIC_FARTLEK.id in afArchetypes)
        assertTrue(Engine15WorkoutArchetype.SHORT_HILL_SPRINTS.id in afArchetypes)
        assertTrue(afPlan.sessions.filter { it.kind == "long" }.all {
            it.runningPrescription?.archetypeId == Engine15WorkoutArchetype.STEADY_AEROBIC_LONG_RUN.id
        })
    }

    @Test
    fun printAnchorProfilesSummary() {
        val anchorNames = setOf("Beginner_5K", "Intermediate_10K", "Half_Marathon_Build", "Marathon_Build")
        val gfProfile = AthleteProfile(
            name = "Train_Your_Way_General_Fitness",
            request = PlanRequest(
                goal = "General fitness",
                experience = 1,
                start = monday,
                weeks = 12,
                runDays = 4,
                days = setOf(1, 3, 5, 7),
                longDay = 7,
                qualityCount = 1,
                qualityDays = setOf(3),
                weeklyKm = 36.0,
                longestKm = 12.0,
                targetWeeklyKm = 44.0,
                planningPace = 345.0,
                policyChoice = "engine15",
                recentMileageBasis = "SELF_REPORTED_AVERAGE",
                timeBudgetEnabled = false
            )
        )
        val summaryProfiles = profiles().filter { it.name in anchorNames } + gfProfile
        for (profile in summaryProfiles) {
            val plan = Engine15IndependentPlanGenerator.generateWithHistory(
                profile.request, owner, emptyList(), "summary-${profile.name}", now
            )
            println("=== ${profile.name} (${profile.request.goal}, ${profile.request.weeks}w, ${profile.request.runDays}d, start=${profile.request.weeklyKm}km -> target=${profile.request.targetWeeklyKm}km) ===")
            val taperLen = Engine15IndependentVolumePlanner.raceTaperLength(profile.request.goal, profile.request.weeks)
            for (w in 0 until profile.request.weeks) {
                val ws = weekSessions(plan, w)
                val totalKm = ws.sumOf { it.distanceKm ?: 0.0 }
                val longSession = ws.firstOrNull { it.kind == "long" }
                val longKm = longSession?.distanceKm
                val isSuppressed = Engine15IndependentVolumePlanner.isSuppressedPreTaperRecovery(profile.request.goal, profile.request.weeks, w)
                val tag = when {
                    taperLen > 0 && w >= profile.request.weeks - taperLen -> "TAPER"
                    w % 4 == 3 && !isSuppressed -> "RECOVERY"
                    else -> "BUILD"
                }
                val qSummary = ws.filter { it.quality }.joinToString("; ") { q ->
                    val segs = q.runningPrescription?.mixedSegments.orEmpty()
                    val works = segs.filter { it.purpose == "WORK" }
                    val recs = segs.filter { it.purpose == "RECOVERY" }
                    val wuMin = segs.firstOrNull { it.purpose == "WARM_UP" }?.seconds?.div(60) ?: 0
                    val cdMin = segs.lastOrNull { it.purpose == "COOL_DOWN" }?.seconds?.div(60) ?: 0
                    val workDesc = when {
                        works.map { it.meters to it.seconds }.distinct().size > 1 ->
                            works.joinToString("-") { "${it.meters ?: it.distanceEquivalentMeters}m" }
                        works.firstOrNull()?.meters != null -> {
                            val rStr = recs.firstOrNull()?.let { r ->
                                if (r.meters != null) "/${r.meters}m" else "/${r.seconds}s"
                            }.orEmpty()
                            "${works.size}x${works.first().meters}m$rStr"
                        }
                        works.size > 1 -> {
                            val wS = works.first().seconds!!
                            val wTxt = if (wS % 60 == 0) "${wS / 60}m" else "${wS}s"
                            val rS = recs.firstOrNull()?.seconds ?: 0
                            val rTxt = if (rS > 0) "/${rS}s" else ""
                            "${works.size}x$wTxt$rTxt"
                        }
                        else -> "${(works.firstOrNull()?.seconds ?: 0) / 60}m"
                    }
                    val workPace = works.firstOrNull()?.let { s ->
                        if (s.targetPaceMinSecPerKm != null && s.targetPaceMaxSecPerKm != null) {
                            "${Engine15PaceRange.formatPace(s.targetPaceMinSecPerKm!!.toDouble())}-${Engine15PaceRange.formatPace(s.targetPaceMaxSecPerKm!!.toDouble())}/km"
                        } else "-"
                    } ?: "-"
                    "${q.title} [$workDesc] (WU ${wuMin}m/CD ${cdMin}m, $workPace)"
                }.ifEmpty { "Easy/Recovery only" }
                val longStr = longKm?.let { km ->
                    val segs = longSession.runningPrescription?.segments.orEmpty()
                    val finishNote = if (segs.size >= 2) {
                        val finishM = segs.last().meters
                        val fTag = if (segs.last().stepIntent == RunningStepIntent.RACE_PACE) "RP" else "Steady"
                        " (${longSession.title} +${String.format(Locale.US, "%.1f", finishM / 1000.0)}km $fTag)"
                    } else ""
                    String.format(Locale.US, "%.1f km%s", km, finishNote)
                } ?: "-"
                val stridesCount = ws.count { it.runningPrescription?.archetypeId == Engine15WorkoutArchetype.EASY_RUN_WITH_STRIDES.id }
                val recCount = ws.count { it.runningPrescription?.archetypeId == Engine15WorkoutArchetype.RECOVERY_RUN.id }
                println(String.format(Locale.US, "W%02d [%s]: %.1f km | Long: %s | Strides:%d Rec:%d | %s", w + 1, tag, totalKm, longStr, stridesCount, recCount, qSummary))
            }
        }
    }
}
