package io.stridelab.privatealpha

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

class CalisthenicsPlanHeadCoachBenchmarkTest {

    private val fixedNow: Instant = Instant.parse("2026-11-27T08:00:00Z")
    private val fixedStart: LocalDate = LocalDate.of(2026, 11, 30)
    private val owner = "cal-head-coach-bench"

    private data class CalProfileSpec(
        val name: String,
        val request: PlanRequest
    )

    private fun buildProfiles(): List<CalProfileSpec> = listOf(
        CalProfileSpec(
            name = "1_Beginner_Foundation_3d_NoBar",
            request = PlanRequest(
                goal = "Calisthenics foundation",
                start = fixedStart,
                weeks = 8,
                days = setOf(1, 3, 5),
                runDays = 0,
                calDays = 3,
                calLevel = 0,
                pullLevel = 0,
                coreLevel = 0,
                minutes = 45,
                timeBudgetEnabled = true,
                equipment = emptyMap(),
                policyChoice = "engine15"
            )
        ),
        CalProfileSpec(
            name = "2_Novice_PullUp_3d_BarBands",
            request = PlanRequest(
                goal = "Pull-up progression",
                start = fixedStart,
                weeks = 8,
                days = setOf(1, 3, 5),
                runDays = 0,
                calDays = 3,
                calLevel = 0,
                pullLevel = 0,
                coreLevel = 0,
                minutes = 45,
                timeBudgetEnabled = true,
                equipment = mapOf("bar" to 1, "bands" to 1),
                policyChoice = "engine15"
            )
        ),
        CalProfileSpec(
            name = "3_Intermediate_PushUp_4d",
            request = PlanRequest(
                goal = "Push-up progression",
                start = fixedStart,
                weeks = 8,
                days = setOf(1, 2, 4, 5),
                runDays = 0,
                calDays = 4,
                calLevel = 1,
                pullLevel = 1,
                coreLevel = 1,
                minutes = 50,
                timeBudgetEnabled = true,
                equipment = mapOf("bar" to 1, "bench" to 1, "wall" to 1),
                policyChoice = "engine15"
            )
        ),
        CalProfileSpec(
            name = "4_Intermediate_Handstand_4d",
            request = PlanRequest(
                goal = "Handstand foundations",
                start = fixedStart,
                weeks = 8,
                days = setOf(1, 2, 4, 5),
                runDays = 0,
                calDays = 4,
                calLevel = 1,
                pullLevel = 1,
                coreLevel = 1,
                minutes = 45,
                timeBudgetEnabled = true,
                equipment = mapOf("bar" to 1, "wall" to 1, "bench" to 1),
                policyChoice = "engine15"
            )
        ),
        CalProfileSpec(
            name = "5_Intermediate_CoreCompression_3d",
            request = PlanRequest(
                goal = "Core strength and control",
                disciplineGoals = mapOf("cal" to "Core strength and control"),
                start = fixedStart,
                weeks = 6,
                days = setOf(1, 3, 5),
                runDays = 0,
                calDays = 3,
                calLevel = 1,
                pullLevel = 1,
                coreLevel = 1,
                minutes = 45,
                timeBudgetEnabled = true,
                equipment = mapOf("bar" to 1, "wall" to 1),
                policyChoice = "engine15"
            )
        ),
        CalProfileSpec(
            name = "6_Intermediate_SingleLeg_3d",
            request = PlanRequest(
                goal = "Single-leg control",
                disciplineGoals = mapOf("cal" to "Single-leg control"),
                start = fixedStart,
                weeks = 6,
                days = setOf(1, 3, 5),
                runDays = 0,
                calDays = 3,
                calLevel = 1,
                pullLevel = 1,
                coreLevel = 1,
                minutes = 45,
                timeBudgetEnabled = true,
                equipment = mapOf("bench" to 1, "wall" to 1),
                policyChoice = "engine15"
            )
        ),
        CalProfileSpec(
            name = "7_Advanced_SkillStaticHolds_5d_Rings",
            request = PlanRequest(
                goal = "Skill and static holds",
                disciplineGoals = mapOf("cal" to "Skill and static holds"),
                start = fixedStart,
                weeks = 8,
                days = setOf(1, 2, 3, 5, 6),
                runDays = 0,
                calDays = 5,
                calLevel = 2,
                pullLevel = 2,
                coreLevel = 2,
                minutes = 60,
                timeBudgetEnabled = true,
                equipment = mapOf("bar" to 1, "rings" to 1, "wall" to 1, "bench" to 1),
                policyChoice = "engine15"
            )
        ),
        CalProfileSpec(
            name = "8_Advanced_Weighted_6d_PPL",
            request = PlanRequest(
                goal = "Weighted calisthenics",
                disciplineGoals = mapOf("cal" to "Weighted calisthenics"),
                start = fixedStart,
                weeks = 8,
                days = setOf(1, 2, 3, 4, 5, 6),
                runDays = 0,
                calDays = 6,
                calLevel = 2,
                pullLevel = 2,
                coreLevel = 2,
                minutes = 60,
                timeBudgetEnabled = true,
                equipment = mapOf("bar" to 1, "dip_belt" to 1, "rings" to 1, "bench" to 1, "wall" to 1),
                policyChoice = "engine15"
            )
        ),
        CalProfileSpec(
            name = "9_TimeCapped_30m_Novice_2d",
            request = PlanRequest(
                goal = "Calisthenics foundation",
                start = fixedStart,
                weeks = 4,
                days = setOf(1, 4),
                runDays = 0,
                calDays = 2,
                calLevel = 0,
                pullLevel = 0,
                coreLevel = 0,
                minutes = 30,
                timeBudgetEnabled = true,
                equipment = mapOf("bar" to 1, "bands" to 1),
                policyChoice = "engine15"
            )
        )
    )

    @Test
    fun benchmarkAllCalisthenicsProfilesAgainstHeadCoachRubric() {
        val nodeById = Engine17CalisthenicsDagCatalog.buildNodes().associateBy { it.exercise.id }
        val allViolations = mutableListOf<String>()
        val scorecardRows = mutableListOf<String>()

        for (spec in buildProfiles()) {
            val profileViolations = mutableListOf<String>()
            val result = Engine17CalisthenicsProgramBuilder.build(
                request = spec.request,
                owner = owner,
                id = "bench-${spec.name.lowercase(Locale.US)}",
                now = fixedNow
            )
            val snap = result.snapshot
            val objective = CalisthenicsObjective.fromGoalString(spec.request.disciplineGoal("cal"))
            val availableTokens = Engine17CalisthenicsDagCatalog.availableEquipmentTokens(spec.request)
            val daysPerWeek = spec.request.calDays

            // 7. Determinism & copy safety
            try {
                Engine17CalisthenicsProgramBuilder.validateSaved(snap)
                Engine15IndependentPlanGenerator.validateSaved(snap)
            } catch (t: Throwable) {
                profileViolations += "validateSaved failed: ${t.message}"
            }
            if (snap.explanations.any { "\u2014" in it }) {
                profileViolations += "Em-dash found in explanations"
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
                    profileViolations += "${s.id}: duplicate exercise IDs"
                }
                // 1. Fresh Skill First
                val skillIndices = s.exercises.mapIndexedNotNull { idx, ex -> if (ex.cue.contains("[Skill")) idx else null }
                if (skillIndices.any { it != 0 }) {
                    profileViolations += "${s.id}: [Skill] block at index $skillIndices (must be index 0)"
                }

                for (ex in s.exercises) {
                    if (ex.id in spec.request.excluded) profileViolations += "${s.id}: excluded ${ex.id}"
                    val node = nodeById[ex.id]
                    if (node == null) {
                        profileViolations += "${s.id}: unknown exercise ${ex.id}"
                        continue
                    }
                    val eqOk = node.exercise.equipmentOptions.any { opt -> opt.isEmpty() || availableTokens.containsAll(opt) }
                    if (!eqOk) {
                        profileViolations += "${s.id}: exercise ${ex.id} violates equipment $availableTokens"
                    }
                    // 4. Isometric vs Dynamic prescription integrity
                    if (node.exercise.isIsometricHold) {
                        if (ex.seconds == null || ex.seconds !in 10..60 || ex.reps != null) {
                            profileViolations += "${s.id}: isometric ${ex.id} has reps=${ex.reps}, seconds=${ex.seconds}"
                        }
                    } else {
                        if (ex.reps == null || ex.reps <= 0 || ex.seconds != null) {
                            profileViolations += "${s.id}: dynamic ${ex.id} has reps=${ex.reps}, seconds=${ex.seconds}"
                        }
                    }
                    // 5. POL-047 novice eccentric cap
                    if (spec.request.calLevel <= 1 && node.isEccentricHighTendonStrain) {
                        if (ex.sets > 2 || (ex.reps ?: 0) > 3) {
                            profileViolations += "${s.id}: novice eccentric ${ex.id} exceeds 2x3 (${ex.sets}x${ex.reps})"
                        }
                    }
                }
            }

            val byWeek = snap.sessions.chunked(daysPerWeek)
            val w1 = byWeek.first()
            val w1AllIds = w1.flatMap { s -> s.exercises.map { it.id } }

            // 1b. Label-to-content fidelity
            for (s in w1) {
                val patterns = s.exercises.mapNotNull { nodeById[it.id]?.exercise?.pattern }
                val isLowerByNode = s.exercises.any { nodeById[it.id]?.exercise?.isLowerBody == true }
                if (s.title.startsWith("Pull") || s.title.startsWith("Upper")) {
                    if ("cal-sl-glute-bridge" in s.exercises.map { it.id } || isLowerByNode) {
                        profileViolations += "${s.id} (${s.title}): contains lower-body exercise ${s.exercises.map { it.id }}"
                    }
                }
                if (s.title.startsWith("Upper & Core")) {
                    val hasPush = patterns.any { it in setOf(StrengthPattern.HORIZONTAL_PUSH, StrengthPattern.VERTICAL_PUSH) }
                    val hasPull = patterns.any { it in setOf(StrengthPattern.HORIZONTAL_PULL, StrengthPattern.VERTICAL_PULL) }
                    val hasCore = patterns.any { it == StrengthPattern.CORE_STABILITY }
                    if (!hasPush || !hasPull || !hasCore) {
                        profileViolations += "${s.id} (${s.title}): missing push=$hasPush, pull=$hasPull, or core=$hasCore"
                    }
                }
                if (s.title.startsWith("Skill & Static Holds") || s.title.startsWith("Legs & Skill B")) {
                    if (s.exercises.firstOrNull()?.cue?.contains("[Skill") != true) {
                        profileViolations += "${s.id} (${s.title}): missing [Skill] block at index 0"
                    }
                }
            }

            // 2. Objective DAG fidelity
            when (objective) {
                CalisthenicsObjective.WEIGHTED_CALISTHENICS -> {
                    if ("cal-weighted-pullup" !in w1AllIds || "cal-weighted-dip" !in w1AllIds) {
                        profileViolations += "Weighted calisthenics W1 missing cal-weighted-pullup / cal-weighted-dip (got ${w1AllIds.distinct()})"
                    }
                }
                CalisthenicsObjective.SKILL_AND_STATIC_HOLDS -> {
                    val staticSkillIds = setOf("cal-front-lever-tuck", "cal-back-lever-tuck", "cal-skin-the-cat", "cal-floor-lsit", "cal-planche-lean", "cal-chest-to-wall-hs")
                    if (w1AllIds.none { it in staticSkillIds }) {
                        profileViolations += "Skill & Static Holds W1 missing dedicated static hold DAG nodes"
                    }
                }
                CalisthenicsObjective.HANDSTAND_FOUNDATIONS -> {
                    if (w1.none { it.exercises.firstOrNull()?.cue?.contains("[Skill") == true }) {
                        profileViolations += "Handstand Foundations missing [Skill] first block"
                    }
                }
                else -> {}
            }

            // 3. Multi-day variety & antagonist shoulder safety
            if (daysPerWeek >= 2) {
                val distinctLineups = w1.map { s -> s.exercises.map { it.id } }.distinct().size
                if (distinctLineups < 2) {
                    profileViolations += "W1 has $distinctLineups distinct lineup(s) across $daysPerWeek days (clone days)"
                }
            }
            if (daysPerWeek >= 3) {
                val w1Patterns = w1AllIds.mapNotNull { nodeById[it]?.exercise?.pattern }.toSet()
                val hasPush = w1Patterns.any { it in setOf(StrengthPattern.HORIZONTAL_PUSH, StrengthPattern.VERTICAL_PUSH) }
                val hasPull = w1Patterns.any { it in setOf(StrengthPattern.HORIZONTAL_PULL, StrengthPattern.VERTICAL_PULL) }
                val hasLower = w1AllIds.any { nodeById[it]?.exercise?.isLowerBody == true }
                if (!hasPush || !hasPull) {
                    profileViolations += "W1 missing antagonist balance (push=$hasPush, pull=$hasPull)"
                }
                if (!hasLower) {
                    profileViolations += "W1 (${daysPerWeek}d) missing lower-body balance"
                }
            }

            // 5b. Build progression & Deload cut
            if (byWeek.size >= 4) {
                val w1Volume = w1.sumOf { s -> s.exercises.sumOf { it.sets * (it.reps ?: it.seconds ?: 1) } }
                val w2Volume = byWeek[1].sumOf { s -> s.exercises.sumOf { it.sets * (it.reps ?: it.seconds ?: 1) } }
                val w3Volume = byWeek[2].sumOf { s -> s.exercises.sumOf { it.sets * (it.reps ?: it.seconds ?: 1) } }
                if (w2Volume <= w1Volume && w3Volume <= w1Volume) {
                    profileViolations += "No build progression W1($w1Volume) -> W2($w2Volume) -> W3($w3Volume)"
                }
                val w1Sets = w1.sumOf { s -> s.exercises.sumOf { it.sets } }
                val w4Sets = byWeek[3].sumOf { s -> s.exercises.sumOf { it.sets } }
                val cut = 1.0 - (w4Sets.toDouble() / w1Sets.toDouble())
                if (cut < 0.20) {
                    profileViolations += "W4 deload cut=${(cut * 100).toInt()}% (< 20%, w1=$w1Sets -> w4=$w4Sets)"
                }
                if (byWeek[3].any { s -> s.exercises.any { "3 to 4 RIR" !in it.cue } }) {
                    profileViolations += "W4 deload missing 3 to 4 RIR"
                }
            }

            // 6. Session density on >= 45m budgets
            if (spec.request.minutes >= 45) {
                for (s in w1) {
                    val sets = s.exercises.sumOf { it.sets }
                    if (s.exercises.size < 3 || sets < 10) {
                        profileViolations += "${s.id}: thin session on ${spec.request.minutes}m budget (ex=${s.exercises.size}, sets=$sets)"
                    }
                }
            }

            val avgMin = w1.map { it.minutes }.average().toInt()
            val totalSets = w1.sumOf { s -> s.exercises.sumOf { it.sets } }
            val lineups = w1.map { s -> s.exercises.map { it.id } }.distinct().size
            val status = if (profileViolations.isEmpty()) "PASS" else "FAIL(${profileViolations.size})"
            scorecardRows += String.format(
                Locale.US,
                "%-36s | %-7s | %2d sets/wk | avg %2dm | lineups:%d/%d",
                spec.name, status, totalSets, avgMin, lineups, daysPerWeek
            )
            profileViolations.forEach { allViolations += "[${spec.name}] $it" }
        }

        println("=== HEAD CALISTHENICS COACH BENCHMARK SCORECARD (${buildProfiles().size} profiles) ===")
        scorecardRows.forEach(::println)
        if (allViolations.isNotEmpty()) {
            println("=== VIOLATIONS (${allViolations.size}) ===")
            allViolations.forEach(::println)
        }
        assertTrue(
            "Head Calisthenics Coach benchmark failed with ${allViolations.size} violation(s):\n${allViolations.joinToString("\n")}",
            allViolations.isEmpty()
        )
    }
}
