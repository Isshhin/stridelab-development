package io.stridelab.privatealpha

import java.util.Locale

/** The only athlete-facing copy for the shared debrief and its six analyzers. */
internal object DebriefCopyTemplates {
    const val eyebrow = "COACH'S DEBRIEF"

    fun pulse(first: Int, last: Int, tone: DebriefTone, priority: Int) =
        insight(DebriefCategory.PACING_AND_HR, tone, priority, "Heart rate rose late",
            "Your heart rate climbed from $first to $last bpm while your pace stayed about the same." +
                if (tone == DebriefTone.CAUTION) " Ease into the next run and let effort guide your pace." else "")

    fun aerobic(percent: Int, seconds: Double) =
        insight(DebriefCategory.INTENT_AND_ZONES, DebriefTone.POSITIVE, 92, "Disciplined aerobic effort",
            "You spent ${duration(seconds)} in Zones 1–2, or $percent% of the run. That’s the kind of effort that lets you put time on your feet without turning an endurance day into a hard workout.")

    fun steadyPace(first: Double, last: Double) =
        insight(DebriefCategory.TERRAIN_AND_SPLITS, DebriefTone.POSITIVE, 68, "Even pacing",
            "Your two halves came in at ${duration(first)}/km and ${duration(last)}/km. You found a pace early that you could still hold on the way home.")

    fun stablePulse(first: Int, last: Int) =
        insight(DebriefCategory.PACING_AND_HR, DebriefTone.POSITIVE, 71, "Heart rate held steady",
            "At much the same pace, your heart rate averaged $first bpm early and $last bpm late. Holding that speed took about the same effort even after more time on your feet.")

    fun stableCadence(first: Int, last: Int) =
        insight(DebriefCategory.CADENCE, DebriefTone.NEUTRAL, 57, "Turnover stayed steady",
            "At a similar pace, your cadence averaged $first spm early and $last spm late.")

    fun contactTime(first: Int, last: Int, rose: Boolean) =
        insight(DebriefCategory.RUNNING_DYNAMICS, if (rose) DebriefTone.CAUTION else DebriefTone.NEUTRAL,
            if (rose) 82 else 69, if (rose) "Longer ground contact" else "Steady ground contact",
            "At a similar pace, ground contact averaged $first ms early and $last ms late." +
                if (rose) " Keep the next long run comfortable through the finish." else "")

    fun plannedHeartRate(percent: Int) =
        insight(DebriefCategory.INTENT_AND_ZONES, DebriefTone.NEUTRAL, 77, "Time near target",
            "While your watch tracked your heart rate, you spent $percent% of the time in or near the range your workout called for.")

    fun dominantZone(zone: Int, percent: Int, seconds: Double) =
        insight(DebriefCategory.INTENT_AND_ZONES, DebriefTone.NEUTRAL, 65, "Longest in Zone $zone",
            "You spent ${duration(seconds)} in Zone $zone. That’s $percent% of the run.")

    fun splits(first: Double, last: Double, difference: Int, fullHalves: Boolean) =
        insight(DebriefCategory.TERRAIN_AND_SPLITS, DebriefTone.NEUTRAL, 70,
            if (last < first) "Pace picked up" else "Pace eased late",
            "${if (fullHalves) "You ran the second half" else "You ran the later kilometers"} " +
                "$difference sec/km ${if (last < first) "faster" else "slower"} (${duration(first)} → ${duration(last)}/km).")

    fun cadence(first: Int, last: Int, faster: Boolean) =
        insight(DebriefCategory.CADENCE, DebriefTone.NEUTRAL, 55, if (faster) "Quicker steps late" else "Turnover eased late",
            "As you ${if (faster) "picked up the pace" else "eased the pace"}, your step rate went from $first to $last spm.")

    fun distance(actual: Double, planned: Double) =
        insight(DebriefCategory.EXECUTION, DebriefTone.NEUTRAL, 75, "Distance against plan",
            "You ran ${km(actual)} km with ${km(planned)} km on the plan. How hard you ran those kilometers is still unclear.")

    fun workPace(verdict: ExecutionVerdict, adherence: Int): DebriefInsight? {
        val (title, tone, score) = when (verdict) {
            ExecutionVerdict.ON_TARGET -> Triple("Work pace on target", DebriefTone.POSITIVE, 90)
            ExecutionVerdict.PARTIALLY_EXECUTED -> Triple("Parts of the workout landed", DebriefTone.NEUTRAL, 78)
            ExecutionVerdict.INTENSITY_MISMATCH_TOO_FAST -> Triple("Faster than planned", DebriefTone.CAUTION, 95)
            ExecutionVerdict.INTENSITY_MISMATCH_TOO_SLOW -> Triple("Slower than planned", DebriefTone.CAUTION, 95)
            else -> return null
        }
        return insight(DebriefCategory.EXECUTION, tone, score, title,
            "You ran within your pace range for $adherence% of the work your watch captured." + when (verdict) {
                ExecutionVerdict.INTENSITY_MISMATCH_TOO_FAST -> " Ease into the next work interval at the slower end of your range."
                ExecutionVerdict.INTENSITY_MISMATCH_TOO_SLOW -> " Next time, aim for the slower end of your range first and settle there."
                ExecutionVerdict.PARTIALLY_EXECUTED -> " Some of the planned work came through, though the full session didn’t line up."
                else -> " That’s the rhythm this workout called for."
            })
    }

    fun dose(discipline: DebriefDiscipline, evidence: DebriefDose, tone: DebriefTone): DebriefInsight {
        val title = when (discipline) {
            DebriefDiscipline.STRENGTH -> "Your strength sets"
            DebriefDiscipline.ISOMETRICS -> "Time under tension"
            DebriefDiscipline.CALISTHENICS -> "Your bodyweight sets"
            DebriefDiscipline.CONDITIONING -> "Your conditioning rounds"
            else -> error("Unsupported dose discipline")
        }
        val unit = if (discipline == DebriefDiscipline.CONDITIONING) "rounds" else "sets"
        val total = if (evidence.plannedSeconds > 0) {
            val time = if (discipline == DebriefDiscipline.ISOMETRICS) "hold time" else "work time"
            "your $time added up to ${duration(evidence.loggedSeconds.toDouble())} of the planned ${duration(evidence.plannedSeconds.toDouble())}"
        } else "the plan called for ${evidence.planned} $unit"
        val loggedUnit = if (evidence.logged == 1) unit.dropLast(1) else unit
        val body = "You hit your target in ${evidence.met} of the ${evidence.logged} $loggedUnit you logged, and $total." +
            when (tone) {
                DebriefTone.CAUTION -> " Keep the same target next time and take enough rest between $unit."
                DebriefTone.POSITIVE -> " You got the full session in."
                DebriefTone.NEUTRAL -> ""
            }
        return insight(DebriefCategory.EXECUTION, tone, 80, title, body)
    }

    fun contacts(actual: Int, cap: Int, allLogged: Boolean): DebriefInsight {
        val exceeded = actual > cap
        return insight(DebriefCategory.EXECUTION,
            if (exceeded) DebriefTone.CAUTION else if (allLogged) DebriefTone.POSITIVE else DebriefTone.NEUTRAL,
            if (exceeded) 96 else 80,
            if (exceeded) "Extra jump contacts" else if (allLogged) "Jumps within plan" else "Jump contacts so far",
            "You logged $actual ground contacts with a cap of $cap." +
                if (exceeded) " Next time, stop at the cap even if your legs still feel fresh." else "")
    }

    fun effort(actual: Int?, limit: Int, harder: Boolean, reportedHarder: Boolean): DebriefInsight {
        val detail = when {
            reportedHarder && actual != null -> "You rated this session $actual/10 and it felt harder than planned."
            actual != null -> "You rated this session $actual/10 on a day meant to feel no harder than $limit/10."
            else -> "This session felt harder than you expected."
        }
        return insight(DebriefCategory.LOAD_AND_RECOVERY,
            if (harder) DebriefTone.CAUTION else DebriefTone.POSITIVE,
            if (harder) 89 else 62, if (harder) "Harder than planned" else "Effort under control",
            detail + if (harder) " Start a little easier next time so the effort stays manageable." else "")
    }

    fun runnerRepsInReserve(actual: Int, target: Int) =
        insight(DebriefCategory.LOAD_AND_RECOVERY,
            if (actual < target) DebriefTone.CAUTION else DebriefTone.POSITIVE,
            if (actual < target) 89 else 62,
            if (actual < target) "Leave a little more" else "Strength in reserve",
            "You felt you had $actual ${if (actual == 1) "rep" else "reps"} left, with $target in reserve as the goal." +
                if (actual < target) " Use a lighter load next time so you can finish with that margin." else "")

    fun hardRunSpacing(days: Long, plyometric: Boolean): DebriefInsight {
        val whenText = if (days == 0L) "the same day as" else "the day after"
        val work = if (plyometric) "jump session" else "lower-body session"
        return insight(DebriefCategory.LOAD_AND_RECOVERY, DebriefTone.CAUTION, 94, "Hard sessions close together",
            "You did this $work $whenText a speed or long run. Leave an easier day between them next time so your legs have time to recover.")
    }

    fun paceAndPulse(early: Double, late: Double, first: Int, last: Int): DebriefInsight {
        val change = when {
            last > first + 3 -> "your heart rate rose from $first to $last bpm"
            first > last + 3 -> "your heart rate came down from $first to $last bpm"
            else -> "your heart rate barely changed ($first to $last bpm)"
        }
        return insight(DebriefCategory.PACING_AND_HR, DebriefTone.NEUTRAL, 66, "Pace and effort together",
            "As your pace ${if (late < early) "quickened" else "eased"} from ${duration(early)} to ${duration(late)}/km, $change. " +
                when {
                    late < early && last > first + 3 -> "The faster finish asked more of you."
                    late < early && last <= first + 3 -> "You found more speed in the second half without a matching rise in heart rate."
                    late > early && last > first + 3 -> "Even as you slowed, the second half took more effort."
                    last < first - 3 -> "Your effort eased along with the pace in the second half."
                    else -> "Slowing down brought little change in heart rate."
                })
    }

    fun measuredPulse(run: ActivityRecord, zones: ExecutionHrZoneProfile, decoupling: Double?): DebriefInsight? {
        val range = zones.z1FloorBpm.toDouble()..(zones.maxHrBpm ?: 240).toDouble()
        val avg = run.averageHr?.takeIf { it in range }?.let { kotlin.math.round(it).toInt() }
        val peak = run.maximumHr?.takeIf { it in range && (avg == null || it >= avg) }?.let { kotlin.math.round(it).toInt() }
        if (peak == null && avg == null && decoupling == null) return null
        val body = when {
            peak != null && avg != null -> "You averaged $avg bpm overall and topped out at $peak bpm."
            avg != null -> "Across the whole run your heart rate averaged $avg bpm."
            peak != null -> "Your highest recorded heart rate was $peak bpm."
            else -> ""
        }
        val drift = decoupling?.let {
            if (kotlin.math.abs(it) < 0.1) "You covered about the same ground per heartbeat in both halves."
            else "In the second half you covered ${km(kotlin.math.abs(it))}% ${if (it > 0) "less" else "more"} ground per heartbeat than in the first."
        }
        return insight(DebriefCategory.PACING_AND_HR, DebriefTone.NEUTRAL, 48, "Your overall effort",
            listOfNotNull(body.takeIf { it.isNotEmpty() }, drift).joinToString(" "))
    }

    fun runOpening(run: ActivityRecord): String? {
        val distance = run.distanceM?.takeIf { it.isFinite() && it > 0 } ?: return null
        val moving = run.movingTimeS?.takeIf { it.isFinite() && it > 0 }
        val pace = (run.recordedPace ?: run.garminMovingPaceSPerKm)?.takeIf { it in 120.0..900.0 }
        return buildString {
            append("You covered ${String.format(Locale.ROOT, "%.2f", distance / 1000)} km")
            if (moving != null) append(" in ${duration(moving)} of moving time")
            else run.durationS?.takeIf { it.isFinite() && it > 0 }?.let { append(" in ${duration(it)} from start to finish") }
            pace?.let { append(", averaging ${duration(it)}/km") }
            append(".")
        }
    }

    fun elevationChange(change: Int, gain: Double?): DebriefInsight {
        val climb = gain?.takeIf { it.isFinite() && it >= 1 }?.let { " You gained ${kotlin.math.round(it).toInt()} m over the whole route." }.orEmpty()
        return insight(DebriefCategory.TERRAIN_AND_SPLITS, DebriefTone.NEUTRAL, 50, "The route changed",
            "The second half averaged ${kotlin.math.abs(change)} m ${if (change > 0) "higher" else "lower"} than the first.$climb On a route like this, a change in pace alone tells only part of the story.")
    }

    fun terrain(run: ActivityRecord): DebriefInsight? {
        val climb = run.elevationM?.takeIf { it.isFinite() && it >= 1 } ?: return null
        return insight(DebriefCategory.TERRAIN_AND_SPLITS, DebriefTone.NEUTRAL, 42, "The ground you covered",
            "You accumulated ${kotlin.math.round(climb).toInt()} m of climbing. That uphill work is part of the run even when it doesn’t show up in your average pace.")
    }

    fun workIntervals(execution: WorkoutExecutionAnalysis?): DebriefInsight? {
        if (execution == null || execution.evidenceTier == ExecutionEvidenceTier.SUMMARY_ONLY) return null
        val work = execution.stepBreakdowns.filter { it.purpose == "WORK" }.sortedBy { it.stepIndex }
        if (work.size < 2) return null
        val first = work.first().actualPaceSecPerKm?.takeIf { it in 120.0..900.0 } ?: return null
        val last = work.last().actualPaceSecPerKm?.takeIf { it in 120.0..900.0 } ?: return null
        return intervalPaces(first, last)
    }

    fun mixedEffort(easy: Int, hard: Int) =
        insight(DebriefCategory.INTENT_AND_ZONES, DebriefTone.NEUTRAL, 64, "A mix of efforts",
            "You spent $easy% of the run in the easier two zones and $hard% in Zones 4–5. A single average smooths over that change in effort.")

    fun timeOnFeet(activity: ActivityRecord): DebriefInsight? {
        val elapsed = activity.durationS?.takeIf { it.isFinite() } ?: return null
        val moving = activity.movingTimeS?.takeIf { it.isFinite() && it > 0 } ?: return null
        if (elapsed - moving < 60 || elapsed - moving < elapsed * .05) return null
        return insight(DebriefCategory.TERRAIN_AND_SPLITS, DebriefTone.NEUTRAL, 41, "Time on your feet",
            "Of the ${duration(elapsed)} from start to finish, ${duration(elapsed - moving)} was outside your moving time. Moving pace reflects the stretches when you were actually running.")
    }

    fun intervalPaces(first: Double, last: Double) =
        insight(DebriefCategory.EXECUTION, DebriefTone.NEUTRAL, 65, "Inside the work intervals",
            "Your first work interval averaged ${duration(first)}/km and your last averaged ${duration(last)}/km. Those paces leave out the recoveries between efforts.")

    fun form(summary: RunningDynamicsSummary?, findings: List<DebriefInsight>): DebriefInsight? {
        val values = summary?.values ?: return null
        val parts = buildList {
            val cadence = values.cadenceSpm?.takeIf { it in 100.0..250.0 && findings.none { f -> f.category == DebriefCategory.CADENCE } }
            val length = values.stepLengthM
            if (cadence != null) add("You averaged ${kotlin.math.round(cadence).toInt()} steps a minute" +
                if (length != null) ", covering ${if (summary.estimatedStepLength) "an estimated " else ""}${String.format(Locale.ROOT, "%.2f", length)} m per step." else ".")
            else if (length != null) add("Each step covered ${if (summary.estimatedStepLength) "an estimated " else ""}${String.format(Locale.ROOT, "%.2f", length)} m on average.")
            values.groundContactMs?.takeIf { findings.none { f -> f.category == DebriefCategory.RUNNING_DYNAMICS } }?.let {
                add("Your foot spent about ${kotlin.math.round(it).toInt()} ms on the ground with each step.")
            }
            val bounce = values.verticalOscillationCm
            val ratio = values.verticalRatioPct
            if (bounce != null) add("You moved up and down ${km(bounce)} cm per step" +
                if (ratio != null) ", about ${km(ratio)}% of the distance you moved forward." else ".")
            else if (ratio != null) add("Your up-and-down movement was about ${km(ratio)}% of your step length.")
        }
        return if (parts.isEmpty()) null else insight(DebriefCategory.RUNNING_DYNAMICS, DebriefTone.NEUTRAL, 45,
            "Inside your stride", parts.joinToString(" "))
    }

    fun trainingLoad(effect: TrainingEffect): DebriefInsight? {
        val aerobic = effect.aerobic
        val anaerobic = effect.anaerobic
        val body = listOfNotNull(
            aerobic?.let { "StrideLab estimates the endurance load at ${km(it)}/5 from your time on your feet and where your heart rate settled." },
            anaerobic?.let { when {
                it == 0.0 -> "StrideLab picked up no short, hard bursts followed by an easier stretch."
                else -> "Short bursts added a separate surge score of ${km(it)}/5, which captures changes a whole-run average can hide."
            } }
        )
        return if (body.isEmpty()) null else insight(DebriefCategory.LOAD_AND_RECOVERY, DebriefTone.NEUTRAL, 40,
            "What the effort added", body.joinToString(" "))
    }

    private fun duration(seconds: Double) = ActivityRecord.duration(seconds)
    private fun km(value: Double) = String.format(Locale.ROOT, "%.1f", value)
    private fun insight(category: DebriefCategory, tone: DebriefTone, score: Int, title: String, body: String) =
        DebriefInsight(category, title, body, tone, score)
}
