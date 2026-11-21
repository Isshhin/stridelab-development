package io.stridelab.privatealpha

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

class Engine15M28CoachingAuditTest {
    private val now = Instant.parse("2026-11-21T06:00:00Z")
    private val planStart = LocalDate.of(2026, 11, 23) // Monday

    private data class AuditScenario(
        val id: String,
        val label: String,
        val request: PlanRequest,
        val activities: List<ActivityRecord>
    )

    private fun runAct(id: String, daysAgo: Long, distKm: Double, paceSecPerKm: Double, hr: Double = 148.0) = ActivityRecord(
        id = id,
        title = "Run $id",
        type = "running",
        source = "garmin",
        startedAt = now.minus(daysAgo, ChronoUnit.DAYS),
        distanceM = distKm * 1000.0,
        durationS = distKm * paceSecPerKm,
        movingTimeS = distKm * paceSecPerKm,
        recordedPace = paceSecPerKm,
        averageHr = hr,
        elevationM = 20.0
    )

    private fun buildHistory(weeklyKm: Double, runsPerWeek: Int, easyPace: Double, fastPace: Double? = null): List<ActivityRecord> {
        val list = mutableListOf<ActivityRecord>()
        val perRun = weeklyKm / runsPerWeek
        for (w in 0 until 6) {
            for (r in 0 until runsPerWeek) {
                val daysAgo = (w * 7 + r * (7 / runsPerWeek) + 1).toLong()
                val isLong = r == runsPerWeek - 1
                val isFast = fastPace != null && r == 1
                val dist = when {
                    isLong -> (perRun * 1.45).coerceAtLeast(5.0)
                    isFast -> (perRun * 0.90).coerceAtLeast(4.5)
                    else -> perRun * 0.85
                }
                val pace = if (isFast) fastPace!! else easyPace
                list += runAct("h-${w}-${r}", daysAgo, dist, pace, if (isFast) 168.0 else 142.0)
            }
        }
        return list
    }

    private fun scenarios(): List<AuditScenario> {
        val out = mutableListOf<AuditScenario>()
        val goals = listOf("Improve 5K", "Improve 10K", "Half marathon", "Marathon")

        // 12 Matrix Personas: Beginner (0), Intermediate (1), Advanced (2) x 4 goals
        for (exp in listOf(0, 1, 2)) {
            val expName = when (exp) {
                0 -> "Beginner"
                1 -> "Intermediate"
                else -> "Advanced"
            }
            for (goal in goals) {
                val runDays = when (exp) {
                    0 -> if (goal == "Marathon") 4 else 3
                    1 -> if (goal in listOf("Half marathon", "Marathon")) 5 else 4
                    else -> if (goal == "Marathon") 6 else 5
                }
                val weeklyKm = when (exp) {
                    0 -> when (goal) { "Improve 5K" -> 18.0; "Improve 10K" -> 22.0; "Half marathon" -> 26.0; else -> 32.0 }
                    1 -> when (goal) { "Improve 5K" -> 38.0; "Improve 10K" -> 42.0; "Half marathon" -> 48.0; else -> 55.0 }
                    else -> when (goal) { "Improve 5K" -> 60.0; "Improve 10K" -> 68.0; "Half marathon" -> 75.0; else -> 82.0 }
                }
                val longestKm = (weeklyKm * 0.32).coerceAtLeast(6.0)
                val easyPace = when (exp) { 0 -> 435.0; 1 -> 370.0; else -> 310.0 }
                val fastPace = when (exp) { 0 -> null; 1 -> 305.0; else -> 255.0 }
                val days = when (runDays) {
                    3 -> setOf(2, 4, 7)
                    4 -> setOf(2, 4, 6, 7)
                    5 -> setOf(1, 2, 4, 6, 7)
                    else -> setOf(1, 2, 3, 5, 6, 7)
                }
                val qualityDays = setOf(2)
                val req = PlanRequest(
                    policyChoice = "engine15",
                    goal = goal,
                    experience = exp,
                    start = planStart,
                    weeks = 8,
                    days = days,
                    runDays = runDays,
                    longDay = 7,
                    qualityDays = qualityDays,
                    qualityCount = 1,
                    weeklyKm = weeklyKm,
                    longestKm = longestKm,
                    planningPace = easyPace,
                    recentMileageBasis = "SELF_REPORTED_AVERAGE",
                    timeBudgetEnabled = false,
                    preferredQuality = if (goal in listOf("Improve 5K", "Improve 10K")) "Threshold" else "Tempo"
                )
                out += AuditScenario(
                    id = "${expName.lowercase(Locale.ROOT)}_${goal.lowercase(Locale.ROOT).replace(" ", "_")}",
                    label = "$expName — $goal (${weeklyKm.toInt()} km/wk, ${runDays}d)",
                    request = req,
                    activities = buildHistory(weeklyKm, runDays, easyPace, fastPace)
                )
            }
        }

        // 3 Messy Real-World Histories
        // 13. Easy-heavy runner returning from a dip
        val easyHeavyActs = buildList {
            for (w in 0 until 6) {
                val wkKm = if (w == 1) 28.0 else 58.0
                val per = wkKm / 4.0
                for (r in 0 until 4) {
                    val d = (w * 7 + r * 2 + 1).toLong()
                    val pace = if (w == 0 && r == 1) 335.0 else 395.0
                    add(runAct("eh-$w-$r", d, if (r == 3) per * 1.4 else per * 0.87, pace))
                }
            }
        }
        out += AuditScenario(
            id = "messy_easy_heavy_dip",
            label = "Messy 1: Easy-heavy runner returning from dip (HM, 55 km/wk)",
            request = PlanRequest(
                policyChoice = "engine15",
                goal = "Half marathon",
                experience = 1,
                start = planStart,
                weeks = 8,
                days = setOf(1, 3, 5, 7),
                runDays = 4,
                longDay = 7,
                qualityDays = setOf(3),
                qualityCount = 1,
                weeklyKm = 55.0,
                longestKm = 18.0,
                planningPace = 395.0,
                recentMileageBasis = "SELF_REPORTED_AVERAGE",
                timeBudgetEnabled = false,
                preferredQuality = "Tempo"
            ),
            activities = easyHeavyActs
        )

        // 14. Double-run + GPS glitch spike
        val glitchActs = buildHistory(45.0, 4, 385.0, 325.0) + listOf(
            runAct("gps-spike", 9, 4.2, 195.0, 145.0),
            runAct("double-pm", 4, 3.5, 410.0, 138.0)
        )
        out += AuditScenario(
            id = "messy_gps_spike",
            label = "Messy 2: Double-run + GPS spike outlier (10K, 45 km/wk)",
            request = PlanRequest(
                policyChoice = "engine15",
                goal = "Improve 10K",
                experience = 1,
                start = planStart,
                weeks = 8,
                days = setOf(2, 4, 6, 7),
                runDays = 4,
                longDay = 7,
                qualityDays = setOf(2),
                qualityCount = 1,
                weeklyKm = 45.0,
                longestKm = 14.0,
                planningPace = 385.0,
                recentMileageBasis = "SELF_REPORTED_AVERAGE",
                timeBudgetEnabled = false,
                preferredQuality = "Threshold"
            ),
            activities = glitchActs
        )

        // 15. Zero-history beginner
        out += AuditScenario(
            id = "messy_zero_history_beginner",
            label = "Messy 3: Zero-history beginner (5K, 0 km prior)",
            request = PlanRequest(
                policyChoice = "engine15",
                goal = "Improve 5K",
                experience = 0,
                start = planStart,
                weeks = 8,
                days = setOf(2, 4, 7),
                runDays = 3,
                longDay = 7,
                qualityDays = setOf(2),
                qualityCount = 1,
                weeklyKm = 0.0,
                longestKm = 0.0,
                planningPace = 450.0,
                recentMileageBasis = "CONFIRMED_ZERO",
                timeBudgetEnabled = false,
                preferredQuality = "Tempo"
            ),
            activities = emptyList()
        )

        return out
    }

    @Test
    fun runM28ACoachingAuditAndWriteReport() {
        assumeTrue(
            "M28 coaching audit evaluates Candidate Engine 1.5 plan generation",
            BuildConfig.ENGINE15_INDEPENDENT_RUNNING_ACTIVE
        )
        val sb = StringBuilder()
        sb.appendLine("# M28A — Engine 1.5 Systematic Coaching Audit")
        sb.appendLine()
        sb.appendLine("Generated across 15 scenarios (12 standard matrix personas + 3 messy real-world histories).")
        sb.appendLine()

        var totalFindings = 0
        val findingsSummary = mutableListOf<String>()

        for (sc in scenarios()) {
            val decision = Engine15IndependentPlanGenerator.previewAutoReconciled(
                request = sc.request,
                owner = "m28-audit",
                id = "plan-${sc.id}",
                now = now,
                activities = sc.activities
            )
            val zones = Engine15PaceZoneEstimator.estimate(sc.request, activities = sc.activities)

            sb.appendLine("## ${sc.label}")
            sb.appendLine("- **Pace Zones:** Easy ${zones.easy.formatRange()} | Tempo ${zones.tempo.formatRange()} | Thr ${zones.threshold.formatRange()} | Int ${zones.intervals.formatRange()}")

            if (decision is Engine15IndependentPlanDecision.Deferred) {
                sb.appendLine("- **RESULT:** DEFERRED (`${decision.reasonCode}`): ${decision.explanation}")
                findingsSummary += "[${sc.id}] DEFERRED: ${decision.reasonCode}"
                totalFindings++
                sb.appendLine()
                continue
            }

            val cand = decision as Engine15IndependentPlanDecision.Candidate
            val sessions = cand.plan.sessions.sortedBy { it.date }
            val byWeek = sessions.groupBy {
                (ChronoUnit.DAYS.between(planStart, it.date) / 7).toInt() + 1
            }.toSortedMap()

            val weeklyKms = mutableListOf<Double>()
            val qualityWorkMins = mutableListOf<Int>()
            val flags = mutableListOf<String>()

            sb.appendLine("| Wk | Sessions | Total km | Long run km (% wk) | Quality workout (work min) | Spacing |")
            sb.appendLine("|---|---|---|---|---|---|")

            var prevWeekKm: Double? = null
            for ((wk, wkSessions) in byWeek) {
                val wkKm = wkSessions.sumOf { it.distanceKm ?: it.runningPrescription?.estimatedDistanceKm ?: 0.0 }
                weeklyKms += wkKm
                val longRun = wkSessions.firstOrNull { it.kind == "long" }
                val longKm = longRun?.let { it.distanceKm ?: it.runningPrescription?.estimatedDistanceKm ?: 0.0 } ?: 0.0
                val longShare = if (wkKm > 0.0) (longKm / wkKm * 100.0) else 0.0

                val qualSessions = wkSessions.filter { it.quality }
                val qualDesc = if (qualSessions.isEmpty()) "—" else qualSessions.joinToString("; ") { q ->
                    val workSec = q.runningPrescription?.mixedSegments
                        ?.filter { it.purpose == "WORK" }
                        ?.sumOf { it.seconds ?: 0 } ?: 0
                    val workMin = workSec / 60
                    qualityWorkMins += workMin
                    "${q.runningPrescription?.family ?: q.title} (${workMin}m work / ${q.minutes}m tot)"
                }

                val hardDates = wkSessions.filter { it.quality || it.kind == "long" }.map { it.date }.sorted()
                val backToBack = hardDates.zipWithNext().any { (a, b) -> ChronoUnit.DAYS.between(a, b) <= 1 }
                val spacingNote = if (backToBack) "WARN: back-to-back hard" else "OK"
                if (backToBack) flags += "Wk $wk: back-to-back hard/long days"

                if (prevWeekKm != null && prevWeekKm > 0.0) {
                    val jumpPct = (wkKm - prevWeekKm) / prevWeekKm * 100.0
                    if (jumpPct > 22.0 && wk != 5) {
                        flags += "Wk $wk: volume jump +${String.format(Locale.US, "%.0f", jumpPct)}% (${String.format(Locale.US, "%.1f", prevWeekKm)} -> ${String.format(Locale.US, "%.1f", wkKm)} km)"
                    }
                }
                if (sc.request.runDays >= 4 && longShare > 48.0) {
                    flags += "Wk $wk: long run ${String.format(Locale.US, "%.0f", longShare)}% of weekly km"
                }
                if (wkSessions.any { it.minutes <= 0 }) {
                    flags += "Wk $wk: zero-minute session"
                }

                prevWeekKm = wkKm
                sb.appendLine(
                    "| W$wk | ${wkSessions.size} | ${String.format(Locale.US, "%.1f", wkKm)} | " +
                        "${String.format(Locale.US, "%.1f", longKm)} (${String.format(Locale.US, "%.0f", longShare)}%) | " +
                        "$qualDesc | $spacingNote |"
                )
            }

            if (sc.request.qualityCount > 0 && qualityWorkMins.isEmpty()) {
                flags += "Requested qualityCount=${sc.request.qualityCount}, but 0 quality sessions generated (silently dropped to aerobic)"
            } else if (qualityWorkMins.size >= 4 && qualityWorkMins.distinct().size == 1) {
                flags += "Quality work dose is completely FLAT (${qualityWorkMins.first()}m every single week)"
            }

            if (sc.request.experience == 0 && qualityWorkMins.isNotEmpty() && qualityWorkMins.first() > 20) {
                flags += "Beginner W1 quality work dose too high (${qualityWorkMins.first()}m > 20m)"
            }

            if (weeklyKms.size >= 8) {
                val peak = weeklyKms.maxOrNull() ?: 0.0
                val last = weeklyKms.last()
                val hasDip = weeklyKms.drop(1).dropLast(1).any { it < peak * 0.90 }
                if (!hasDip) flags += "No mid-plan recovery/deload dip (<90% of peak)"
                if (sc.request.goal in listOf("Half marathon", "Marathon") && last > peak * 0.85) {
                    flags += "Final week (${String.format(Locale.US, "%.1f", last)} km) does not taper below 85% of peak (${String.format(Locale.US, "%.1f", peak)} km)"
                }
            }

            if (!(zones.easy.midpointSecondsPerKm > zones.tempo.midpointSecondsPerKm &&
                    zones.tempo.midpointSecondsPerKm > zones.threshold.midpointSecondsPerKm &&
                    zones.threshold.midpointSecondsPerKm > zones.intervals.midpointSecondsPerKm)) {
                flags += "Pace zones out of order!"
            }

            if (flags.isEmpty()) {
                sb.appendLine("- **Audit Status:** PASS (0 rubric flags)")
            } else {
                sb.appendLine("- **Audit Flags (${flags.size}):**")
                flags.forEach { f ->
                    sb.appendLine("  - $f")
                    findingsSummary += "[${sc.id}] $f"
                    totalFindings++
                }
            }
            sb.appendLine()
        }

        sb.appendLine("---")
        sb.appendLine("## Summary: $totalFindings total audit flags across 15 scenarios")
        findingsSummary.forEach { sb.appendLine("- $it") }

        val reportFile = File("F:\\StrideLab\\Current-App\\stridelab-engine15-dev\\research\\engine15\\M28A_COACHING_AUDIT_20261121.md")
        reportFile.parentFile?.mkdirs()
        reportFile.writeText(sb.toString())
        println("M28A Audit written to ${reportFile.absolutePath} with $totalFindings flags.")
        assertEquals(
            "M28A coaching audit detected rubric violations:\n${findingsSummary.joinToString("\n")}",
            0,
            totalFindings
        )
    }
}
