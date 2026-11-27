package io.stridelab.privatealpha

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

class StrengthPlanHeadCoachBenchmarkTest {

    private val fixedNow: Instant = Instant.parse("2026-11-27T08:00:00Z")
    private val fixedStart: LocalDate = LocalDate.of(2026, 11, 30)
    private val owner = "str-head-coach-bench"
    private val fullGym = mapOf(
        "db" to 1,
        "bb" to 1,
        "rack" to 1,
        "bench" to 1,
        "cable" to 1,
        "machine" to 1,
        "kb" to 1,
        "bands" to 1
    )

    private data class StrengthProfileSpec(
        val name: String,
        val request: PlanRequest,
        val isBodyweightOnly: Boolean = false
    )

    private fun buildProfiles(): List<StrengthProfileSpec> = listOf(
        StrengthProfileSpec(
            name = "1_Beginner_FullBody_3d",
            request = PlanRequest(
                goal = "General strength",
                start = fixedStart,
                weeks = 8,
                minutes = 60,
                days = setOf(1, 3, 5),
                runDays = 0,
                strengthDays = 3,
                strengthExperience = 0,
                equipment = fullGym,
                timeBudgetEnabled = true,
                policyChoice = "engine15"
            )
        ),
        StrengthProfileSpec(
            name = "2_Intermediate_Hypertrophy_3d_PPL",
            request = PlanRequest(
                goal = "Muscle development",
                start = fixedStart,
                weeks = 8,
                minutes = 75,
                days = setOf(1, 3, 5),
                runDays = 0,
                strengthDays = 3,
                strengthExperience = 1,
                equipment = fullGym,
                timeBudgetEnabled = true,
                policyChoice = "engine15"
            )
        ),
        StrengthProfileSpec(
            name = "3_Intermediate_Hypertrophy_4d_UL",
            request = PlanRequest(
                goal = "Muscle development",
                start = fixedStart,
                weeks = 8,
                minutes = 75,
                days = setOf(1, 2, 4, 5),
                runDays = 0,
                strengthDays = 4,
                strengthExperience = 1,
                equipment = fullGym,
                timeBudgetEnabled = true,
                policyChoice = "engine15"
            )
        ),
        StrengthProfileSpec(
            name = "4_Advanced_Hypertrophy_6d_PPL",
            request = PlanRequest(
                goal = "Muscle development",
                start = fixedStart,
                weeks = 8,
                minutes = 75,
                days = setOf(1, 2, 3, 4, 5, 6),
                runDays = 0,
                strengthDays = 6,
                strengthExperience = 2,
                equipment = fullGym,
                timeBudgetEnabled = true,
                policyChoice = "engine15"
            )
        ),
        StrengthProfileSpec(
            name = "5_Advanced_MaxStrength_4d_12w",
            request = PlanRequest(
                goal = "Maximal strength",
                start = fixedStart,
                weeks = 12,
                minutes = 90,
                days = setOf(1, 2, 4, 5),
                runDays = 0,
                strengthDays = 4,
                strengthExperience = 2,
                equipment = fullGym,
                timeBudgetEnabled = true,
                policyChoice = "engine15"
            )
        ),
        StrengthProfileSpec(
            name = "6_Intermediate_Power_3d",
            request = PlanRequest(
                goal = "Power development",
                start = fixedStart,
                weeks = 8,
                minutes = 60,
                days = setOf(1, 3, 5),
                runDays = 0,
                strengthDays = 3,
                strengthExperience = 1,
                equipment = fullGym,
                timeBudgetEnabled = true,
                policyChoice = "engine15"
            )
        ),
        StrengthProfileSpec(
            name = "7_DumbbellsOnly_5d_ULPPL",
            request = PlanRequest(
                goal = "Muscle development",
                start = fixedStart,
                weeks = 8,
                minutes = 60,
                days = setOf(1, 2, 3, 5, 6),
                runDays = 0,
                strengthDays = 5,
                strengthExperience = 1,
                equipment = mapOf("db" to 1, "bench" to 1),
                timeBudgetEnabled = true,
                policyChoice = "engine15"
            )
        ),
        StrengthProfileSpec(
            name = "8_BodyweightOnly_Beginner_3d",
            request = PlanRequest(
                goal = "General strength",
                start = fixedStart,
                weeks = 6,
                minutes = 45,
                days = setOf(1, 3, 5),
                runDays = 0,
                strengthDays = 3,
                strengthExperience = 0,
                equipment = emptyMap(),
                timeBudgetEnabled = true,
                policyChoice = "engine15"
            ),
            isBodyweightOnly = true
        ),
        StrengthProfileSpec(
            name = "9a_RunnerSupport_2d_45m",
            request = PlanRequest(
                goal = "Strength for runners",
                start = fixedStart,
                weeks = 8,
                minutes = 45,
                days = setOf(2, 5),
                runDays = 0,
                strengthDays = 2,
                strengthExperience = 1,
                equipment = mapOf("db" to 1, "bench" to 1, "bands" to 1),
                timeBudgetEnabled = true,
                policyChoice = "engine15"
            )
        ),
        StrengthProfileSpec(
            name = "9b_TimeCapped_30m_2d",
            request = PlanRequest(
                goal = "General strength",
                start = fixedStart,
                weeks = 4,
                minutes = 30,
                days = setOf(1, 4),
                runDays = 0,
                strengthDays = 2,
                strengthExperience = 1,
                equipment = fullGym,
                timeBudgetEnabled = true,
                policyChoice = "engine15"
            )
        )
    )

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() && b.isEmpty()) return 0.0
        return a.intersect(b).size.toDouble() / a.union(b).size.toDouble()
    }

    @Test
    fun benchmarkAllStrengthProfilesAgainstHeadCoachRubric() {
        val catalog = Engine16StrengthExerciseCatalog.allMetadata(library = null)
        val byId = catalog.associateBy { it.id }
        val allViolations = mutableListOf<String>()
        val scorecardRows = mutableListOf<String>()

        for (spec in buildProfiles()) {
            val profileViolations = mutableListOf<String>()
            val result = Engine16StrengthProgramBuilder.build(
                request = spec.request,
                owner = owner,
                id = "bench-${spec.name.lowercase(Locale.US)}",
                now = fixedNow
            )
            val snap = result.snapshot
            val objective = StrengthObjective.fromRequestGoal(spec.request.disciplineGoal("str"))
            val availableTokens = Engine16StrengthExerciseCatalog.availableEquipmentTokens(spec.request)
            val daysPerWeek = spec.request.strengthDays

            // 7. Determinism, equipment, exclusion, em-dash, and axial RIR checks
            try {
                Engine16StrengthProgramBuilder.validateSaved(snap)
            } catch (t: Throwable) {
                profileViolations += "validateSaved failed: ${t.message}"
            }
            if (snap.explanations.any { "\u2014" in it }) {
                profileViolations += "Em-dash found in plan explanations"
            }

            for (s in snap.sessions) {
                if ("\u2014" in s.title || "\u2014" in s.reason || s.steps.any { "\u2014" in it } ||
                    s.exercises.any { "\u2014" in it.name || "\u2014" in it.cue }
                ) {
                    profileViolations += "${s.id}: em-dash found in session text"
                }
                if (spec.request.timeBudgetEnabled && s.minutes > spec.request.minutes) {
                    profileViolations += "${s.id}: minutes=${s.minutes} exceeds budget=${spec.request.minutes}"
                }
                if (s.exercises.map { it.id }.distinct().size != s.exercises.size) {
                    profileViolations += "${s.id}: duplicate exercise IDs in session"
                }
                for (ex in s.exercises) {
                    if (ex.id in spec.request.excluded) {
                        profileViolations += "${s.id}: excluded exercise ${ex.id}"
                    }
                    val meta = byId[ex.id]
                    if (meta == null) {
                        profileViolations += "${s.id}: unknown exercise ${ex.id}"
                        continue
                    }
                    if (!Engine16StrengthExerciseCatalog.matchesEquipment(meta, availableTokens)) {
                        profileViolations += "${s.id}: exercise ${ex.id} violates equipment $availableTokens"
                    }
                    if (meta.isAxialCompound && ex.cue.contains("0 RIR")) {
                        profileViolations += "${s.id}: axial compound ${ex.id} has 0 RIR"
                    }
                }
                // 3. Intra-session canonical ordering
                val ranks = s.exercises.mapNotNull { byId[it.id]?.slotOrder?.rank }
                if (ranks != ranks.sorted()) {
                    profileViolations += "${s.id}: non-canonical slot ordering $ranks"
                }
            }

            // Group sessions by 1-based week
            val byWeek = snap.sessions.chunked(daysPerWeek)
            val w1 = byWeek.first()

            // 1. Structural & Movement Balance (Week 1)
            val pushPatterns = setOf(StrengthPattern.HORIZONTAL_PUSH, StrengthPattern.VERTICAL_PUSH)
            val pullPatterns = setOf(StrengthPattern.HORIZONTAL_PULL, StrengthPattern.VERTICAL_PULL)
            val quadPatterns = setOf(StrengthPattern.SQUAT, StrengthPattern.UNILATERAL_LOWER, StrengthPattern.QUAD_ISOLATION)
            val hingePatterns = setOf(StrengthPattern.HINGE, StrengthPattern.HAMSTRING_ISOLATION)

            var w1PushSets = 0
            var w1PullSets = 0
            var w1QuadCount = 0
            var w1HingeCount = 0
            var w1CoreSessions = 0
            for (s in w1) {
                var hasCore = false
                for (ex in s.exercises) {
                    val p = byId[ex.id]?.pattern ?: continue
                    if (p in pushPatterns) w1PushSets += ex.sets
                    if (p in pullPatterns) w1PullSets += ex.sets
                    if (p in quadPatterns) w1QuadCount++
                    if (p in hingePatterns) w1HingeCount++
                    if (p == StrengthPattern.CORE_STABILITY) hasCore = true
                }
                if (hasCore) w1CoreSessions++
            }
            if (!spec.isBodyweightOnly && daysPerWeek >= 2) {
                val ratio = if (w1PullSets == 0) 99.0 else w1PushSets.toDouble() / w1PullSets.toDouble()
                if (ratio !in 0.60..1.65) {
                    profileViolations += "W1 push/pull ratio ${String.format(Locale.US, "%.2f", ratio)} ($w1PushSets:$w1PullSets) outside 0.60..1.65"
                }
            }
            if (w1QuadCount == 0 || w1HingeCount == 0) {
                profileViolations += "W1 missing quad ($w1QuadCount) or hinge ($w1HingeCount) pattern"
            }
            if (daysPerWeek >= 5 && w1CoreSessions > 3) {
                profileViolations += "W1 CORE_STABILITY appears on $w1CoreSessions/$daysPerWeek sessions (max 3)"
            }
            if (daysPerWeek == 4 && objective != StrengthObjective.RUNNING_SUPPORTIVE) {
                val upperA = w1.firstOrNull { it.title.startsWith("Upper A") }
                val upperB = w1.firstOrNull { it.title.startsWith("Upper B") }
                if (upperA != null && upperB != null) {
                    val aHasHoriPush = upperA.exercises.any { byId[it.id]?.pattern == StrengthPattern.HORIZONTAL_PUSH }
                    val bHasHoriPush = upperB.exercises.any { byId[it.id]?.pattern == StrengthPattern.HORIZONTAL_PUSH }
                    val aHasPull = upperA.exercises.any { byId[it.id]?.pattern in pullPatterns }
                    val bHasPull = upperB.exercises.any { byId[it.id]?.pattern in pullPatterns }
                    if (!aHasHoriPush || !bHasHoriPush || !aHasPull || !bHasPull) {
                        profileViolations += "4d Upper/Lower imbalance: UpperA(push=$aHasHoriPush,pull=$aHasPull) UpperB(push=$bHasHoriPush,pull=$bHasPull)"
                    }
                }
            }
            if (spec.isBodyweightOnly) {
                // Ensure push/hinge slots didn't get polluted by CORE_STABILITY when real bw push/hinge exists
                for (s in w1) {
                    val coreCount = s.exercises.count { byId[it.id]?.pattern == StrengthPattern.CORE_STABILITY }
                    if (coreCount > 2) {
                        profileViolations += "${s.id}: bodyweight session has $coreCount core exercises"
                    }
                }
            }

            // 2. Weekly Direct Volume & Goal Fidelity
            val directSetsByMuscle = linkedMapOf<String, Int>()
            val freqByMuscle = linkedMapOf<String, Int>()
            for (s in w1) {
                val touchedInSession = hashSetOf<String>()
                for (ex in s.exercises) {
                    val meta = byId[ex.id] ?: continue
                    for (m in meta.primaryMuscles) {
                        directSetsByMuscle[m] = (directSetsByMuscle[m] ?: 0) + ex.sets
                        touchedInSession += m
                    }
                }
                for (m in touchedInSession) {
                    freqByMuscle[m] = (freqByMuscle[m] ?: 0) + 1
                }
            }
            if (objective == StrengthObjective.HYPERTROPHY && spec.request.minutes >= 60) {
                if (daysPerWeek >= 4) {
                    val minSets = if (daysPerWeek >= 6) 8 else 6
                    for (m in listOf("pectoralis_major", "latissimus_dorsi", "quadriceps", "hamstrings")) {
                        val f = freqByMuscle[m] ?: 0
                        val sets = directSetsByMuscle[m] ?: 0
                        if (f < 2 || sets < minSets) {
                            profileViolations += "W1 hypertrophy $m freq=$f sets=$sets (need >=2x/wk & >=$minSets sets)"
                        }
                    }
                } else if (daysPerWeek == 3) {
                    val push = w1.firstOrNull { it.title.startsWith("Push") }
                    val pull = w1.firstOrNull { it.title.startsWith("Pull") }
                    if (push == null || pull == null) {
                        profileViolations += "3d Hypertrophy missing Push/Pull session titles"
                    } else {
                        val pushSets = push.exercises.sumOf { it.sets }
                        val pullSets = pull.exercises.sumOf { it.sets }
                        val pushHasCore = push.exercises.any { byId[it.id]?.pattern == StrengthPattern.CORE_STABILITY }
                        val pullHasCore = pull.exercises.any { byId[it.id]?.pattern == StrengthPattern.CORE_STABILITY }
                        if (pushSets < 14 || pullSets < 14 || pushHasCore || pullHasCore) {
                            profileViolations += "3d Hypertrophy PPL density/core issue: pushSets=$pushSets(core=$pushHasCore) pullSets=$pullSets(core=$pullHasCore)"
                        }
                    }
                }
                for ((m, sets) in directSetsByMuscle) {
                    if (sets > 22) {
                        profileViolations += "W1 hypertrophy $m direct sets=$sets exceeds MRV cap 22"
                    }
                }
            }
            if (objective == StrengthObjective.MAXIMAL_STRENGTH) {
                val primaryCompounds = w1.flatMap { it.exercises }.filter { byId[it.id]?.slotOrder == StrengthSlotOrder.PRIMARY_COMPOUND }
                if (primaryCompounds.any { (it.reps ?: 0) !in 3..6 }) {
                    profileViolations += "MaxStrength W1 primary compound reps outside 3..6"
                }
                if (spec.request.minutes >= 75 && primaryCompounds.any { it.rest < 120 }) {
                    profileViolations += "MaxStrength W1 primary compound rest < 120s"
                }
                if (spec.request.weeks >= 12) {
                    val phases = result.weeklyPhaseByWeek.values.toSet()
                    if (!phases.containsAll(listOf(StrengthMesocyclePhase.ACCUMULATION, StrengthMesocyclePhase.TRANSMUTATION, StrengthMesocyclePhase.REALIZATION))) {
                        profileViolations += "12w MaxStrength missing phase wave: $phases"
                    }
                }
            }
            if (objective == StrengthObjective.POWER_DEVELOPMENT) {
                val w1HasPowerFirst = w1.any { s -> byId[s.exercises.firstOrNull()?.id]?.pattern == StrengthPattern.POWER_BALLISTIC }
                val w4 = byWeek.getOrNull(3).orEmpty()
                val w4HasBallistic = w4.any { s -> s.exercises.any { MechanicalTag.BALLISTIC in (byId[it.id]?.mechanicalTags ?: emptySet()) } }
                if (!w1HasPowerFirst) profileViolations += "Power W1 missing POWER_BALLISTIC lead lift"
                if (w4HasBallistic) profileViolations += "Power W4 deload should exclude BALLISTIC exercises"
            }

            // 3b & 6. Ramp notes + session density on >= 60m non-deload
            for (s in w1) {
                val lead = s.exercises.firstOrNull()?.let { byId[it.id] }
                if (lead != null &&
                    lead.slotOrder == StrengthSlotOrder.PRIMARY_COMPOUND &&
                    lead.loadSemanticsKind != ExternalLoadSemanticsKind.UNLOADED_BODYWEIGHT &&
                    objective in setOf(StrengthObjective.MAXIMAL_STRENGTH, StrengthObjective.GENERAL_STRENGTH, StrengthObjective.HYPERTROPHY) &&
                    spec.request.minutes >= 60
                ) {
                    if (s.steps.none { "ramp" in it.lowercase(Locale.US) }) {
                        profileViolations += "${s.id}: missing warm-up ramp guidance for ${lead.name}"
                    }
                }
                if (spec.request.minutes >= 60 && !spec.isBodyweightOnly) {
                    val sets = s.exercises.sumOf { it.sets }
                    if (s.exercises.size < 4 || sets < 12) {
                        profileViolations += "${s.id}: thin density on ${spec.request.minutes}m budget (ex=${s.exercises.size}, sets=$sets)"
                    }
                }
            }

            // 4. A/B day variety
            if (daysPerWeek >= 2 && !spec.isBodyweightOnly) {
                val pairs = listOf("Upper A" to "Upper B", "Lower A" to "Lower B", "Full Body A" to "Full Body B", "Push A" to "Push B", "Pull A" to "Pull B", "Legs A" to "Legs B")
                for ((aPrefix, bPrefix) in pairs) {
                    val a = w1.firstOrNull { it.title.startsWith(aPrefix) }
                    val b = w1.firstOrNull { it.title.startsWith(bPrefix) }
                    if (a != null && b != null) {
                        val overlap = jaccard(a.exercises.map { it.id }.toSet(), b.exercises.map { it.id }.toSet())
                        if (overlap >= 0.75) {
                            profileViolations += "$aPrefix vs $bPrefix Jaccard overlap=${String.format(Locale.US, "%.2f", overlap)} >= 0.75"
                        }
                    }
                }
            }

            // 5. Progression & Deload Integrity
            if (byWeek.size >= 4) {
                val w1Work = w1.sumOf { s -> s.exercises.sumOf { it.sets * (it.reps ?: it.seconds ?: 1) } }
                val w2Work = byWeek[1].sumOf { s -> s.exercises.sumOf { it.sets * (it.reps ?: it.seconds ?: 1) } }
                val w3Work = byWeek[2].sumOf { s -> s.exercises.sumOf { it.sets * (it.reps ?: it.seconds ?: 1) } }
                if (w2Work <= w1Work || w3Work < w2Work) {
                    profileViolations += "Intra-block progression stall: w1=$w1Work, w2=$w2Work, w3=$w3Work"
                }
                val w1Sets = w1.sumOf { s -> s.exercises.sumOf { it.sets } }
                val w4Sets = byWeek[3].sumOf { s -> s.exercises.sumOf { it.sets } }
                val deloadCut = 1.0 - (w4Sets.toDouble() / w1Sets.toDouble())
                if (deloadCut < 0.25 || deloadCut > 0.60) {
                    profileViolations += "W4 deload set cut=${(deloadCut * 100).toInt()}% (w1=$w1Sets -> w4=$w4Sets, expected 25..55%)"
                }
                if (byWeek[3].any { s -> s.exercises.any { "3 to 4 RIR" !in it.cue && "3 RIR" !in it.cue } }) {
                    profileViolations += "W4 deload cue missing 3 to 4 RIR"
                }
            }

            val avgMin = w1.map { it.minutes }.average().toInt()
            val w1TotalSets = w1.sumOf { s -> s.exercises.sumOf { it.sets } }
            val chestSets = directSetsByMuscle["pectoralis_major"] ?: 0
            val latSets = directSetsByMuscle["latissimus_dorsi"] ?: 0
            val quadSets = directSetsByMuscle["quadriceps"] ?: 0
            val status = if (profileViolations.isEmpty()) "PASS" else "FAIL(${profileViolations.size})"
            scorecardRows += String.format(
                Locale.US,
                "%-34s | %-7s | %2d sets/wk | avg %2dm | P:P %2d:%2d | Chest:%2d Lat:%2d Quad:%2d | CoreDays:%d",
                spec.name, status, w1TotalSets, avgMin, w1PushSets, w1PullSets, chestSets, latSets, quadSets, w1CoreSessions
            )
            profileViolations.forEach { allViolations += "[${spec.name}] $it" }
        }

        println("=== HEAD STRENGTH COACH BENCHMARK SCORECARD (${buildProfiles().size} profiles) ===")
        scorecardRows.forEach(::println)
        if (allViolations.isNotEmpty()) {
            println("=== VIOLATIONS (${allViolations.size}) ===")
            allViolations.forEach(::println)
        }
        assertTrue(
            "Head Strength Coach benchmark failed with ${allViolations.size} violation(s):\n${allViolations.joinToString("\n")}",
            allViolations.isEmpty()
        )
    }
}
