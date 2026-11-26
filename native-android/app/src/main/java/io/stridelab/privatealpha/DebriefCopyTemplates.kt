package io.stridelab.privatealpha

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class IntervalPattern {
    CONSISTENT,
    NEGATIVE_SPLIT,
    FRONT_LOADED_FADE,
    VARIABLE
}

/**
 * Athlete-facing copy for Coach's Debrief across all six disciplines.
 * Every sentence interprets signals and explains why they matter without repeating raw summary headers
 * or using em-dashes.
 */
internal object DebriefCopyTemplates {
    const val eyebrow = "COACH'S DEBRIEF"

    fun aerobicControlled(percent: Int, seconds: Double, isLongRun: Boolean) = insight(
        DebriefCategory.INTENT_AND_ZONES,
        DebriefTone.POSITIVE,
        92,
        if (isLongRun) "Controlled aerobic long run" else "Disciplined easy effort",
        "You kept $percent% of this run (${duration(seconds)}) in Zones 1 to 2. Staying genuinely aerobic builds endurance capacity while leaving your legs fresh for harder sessions.",
        if (isLongRun) "Carry this same calm opening into your next long run so you finish with energy in reserve."
        else "Keep easy days conversational like this so your body absorbs the training and stays sharp for quality work."
    )

    fun easyDayTooHard(aboveEasyPct: Int, hardPct: Int, isRecovery: Boolean): DebriefInsight {
        val label = if (isRecovery) "recovery" else "easy"
        val title = if (isRecovery) "Recovery run pushed too hard" else "Easy day drifted too hard"
        val hardTail = if (hardPct >= 15) ", including $hardPct% in Zones 4 to 5" else ""
        return insight(
            DebriefCategory.INTENT_AND_ZONES,
            DebriefTone.CAUTION,
            93,
            title,
            "$aboveEasyPct% of this $label run sat in Zone 3 or higher$hardTail. Running moderate effort on an easy day adds fatigue without delivering true speed-session gains.",
            "Slow the opening 2 km until you can speak in full sentences, and let heart rate settle in Zones 1 to 2."
        )
    }

    fun plannedHeartRate(percent: Int): DebriefInsight {
        val (title, tone, explanation, focus) = when {
            percent >= 80 -> listOf(
                "Effort matched the plan",
                "POSITIVE",
                "That kept the physiological load aligned with what the workout was designed to build.",
                "Use this same opening restraint on your next planned session."
            )
            percent >= 60 -> listOf(
                "Mostly near target effort",
                "NEUTRAL",
                "Most of the session hit the intended stimulus, with brief drifts outside range.",
                "Check in on breathing and heart rate early so you settle into target effort sooner."
            )
            else -> listOf(
                "Effort drifted from target",
                "CAUTION",
                "Spending much of the run outside target range changes the training stimulus you get from the session.",
                "Let breathing and heart rate guide the opening kilometers before chasing pace."
            )
        }
        return insight(
            DebriefCategory.INTENT_AND_ZONES,
            DebriefTone.valueOf(tone),
            78,
            title,
            "You held your planned heart-rate range for $percent% of tracked running time. $explanation",
            focus
        )
    }

    fun mixedStimulus(easyPct: Int, hardPct: Int) = insight(
        DebriefCategory.INTENT_AND_ZONES,
        DebriefTone.NEUTRAL,
        66,
        "Polarized effort across the run",
        "You paired $easyPct% easy running in Zones 1 to 2 with $hardPct% hard work in Zones 4 to 5. That contrast trains speed alongside aerobic recovery in the same session, which a single average pace hides.",
        "Give yourself an easy or recovery day next so you absorb the harder segments cleanly."
    )

    fun dominantZoneProfile(zone: Int, percent: Int, seconds: Double): DebriefInsight {
        val (title, meaning, focus) = when (zone) {
            1, 2 -> Triple(
                "Steady aerobic base",
                "Low-stress miles like this expand capillaries and aerobic efficiency without draining recovery.",
                "Keep building consistency on aerobic days before adding extra intensity."
            )
            3 -> Triple(
                "Sustained moderate effort",
                "Time in Zone 3 builds steady aerobic strength, though stacking too many moderate days can blur easy and hard work.",
                "Balance moderate runs with genuinely relaxed Zone 1 to 2 days so fatigue stays low."
            )
            else -> Triple(
                "High-intensity workload",
                "Sustained time in Zone $zone drives top-end aerobic and lactate tolerance and asks for extra recovery afterward.",
                "Keep your next run short and genuinely easy to clear the fatigue from this effort."
            )
        }
        return insight(
            DebriefCategory.INTENT_AND_ZONES,
            DebriefTone.NEUTRAL,
            64,
            title,
            "You spent $percent% of this run (${duration(seconds)}) in Zone $zone. $meaning",
            focus
        )
    }

    fun negativeSplitControlled(
        earlyPace: Double, latePace: Double, diffSec: Int, earlyHr: Int, lateHr: Int
    ) = insight(
        DebriefCategory.PACING_AND_HR,
        DebriefTone.POSITIVE,
        88,
        "Strong negative split under control",
        "You closed $diffSec sec/km faster (${duration(earlyPace)} to ${duration(latePace)}/km) while heart rate stayed composed ($earlyHr to $lateHr bpm). Finishing quicker without a disproportionate cardiac cost points to patient pacing and aerobic headroom.",
        "Keep starting conservatively like this. Holding gears in reserve for the second half pays off in both training and races."
    )

    fun negativeSplitEarnedEffort(
        earlyPace: Double, latePace: Double, diffSec: Int, earlyHr: Int, lateHr: Int, isEasyIntent: Boolean
    ) = insight(
        DebriefCategory.PACING_AND_HR,
        if (isEasyIntent) DebriefTone.CAUTION else DebriefTone.NEUTRAL,
        if (isEasyIntent) 80 else 72,
        if (isEasyIntent) "Fast finish pushed easy effort" else "Strong finish raised the effort",
        "Speeding up by $diffSec sec/km in the second half (${duration(earlyPace)} to ${duration(latePace)}/km) lifted heart rate from $earlyHr to $lateHr bpm. " +
            if (isEasyIntent) "That late surge turned the back half of an easy day into harder work."
            else "The extra heartbeats were earned by running faster, not unexplained fatigue.",
        if (isEasyIntent) "On easy days, keep the final kilometers as relaxed as the opening ones even when your legs feel good."
        else "If you meant to finish fast, keep that controlled build. Otherwise, settle into an even rhythm from start to finish."
    )

    fun aerobicDurabilitySteady(earlyHr: Int, lateHr: Int, decouplingPct: Double?): DebriefInsight {
        val driftText = decouplingPct?.let { ", with ${km(abs(it))}% cardiac drift" }.orEmpty()
        return insight(
            DebriefCategory.PACING_AND_HR,
            DebriefTone.POSITIVE,
            82,
            "Solid aerobic durability",
            "Past 45 minutes at steady pace, heart rate stayed composed ($earlyHr bpm early, $lateHr bpm late)$driftText. Your body absorbed this duration efficiently without losing aerobic control.",
            "This kind of steady efficiency is your green light to keep building endurance gradually."
        )
    }

    fun cardiacDriftFlat(earlyHr: Int, lateHr: Int, decouplingPct: Double?): DebriefInsight {
        val driftText = decouplingPct?.let { " (${km(abs(it))}% drift)" }.orEmpty()
        return insight(
            DebriefCategory.PACING_AND_HR,
            DebriefTone.CAUTION,
            86,
            "Cardiac drift on flat ground",
            "Holding the same pace cost noticeably more effort late, with heart rate climbing from $earlyHr to $lateHr bpm on flat ground$driftText. Drift like this usually points to accumulated fatigue, heat or dehydration, or starting slightly ambitious for today.",
            "On your next aerobic run, let effort cap your pace late and prioritize fluids if conditions are warm."
        )
    }

    fun hillsDroveLateEffort(earlyHr: Int, lateHr: Int, climbChangeM: Int, totalGainM: Double?): DebriefInsight {
        val totalText = totalGainM?.takeIf { it >= 1 }?.let { " (${it.roundToInt()} m total gain)" }.orEmpty()
        return insight(
            DebriefCategory.PACING_AND_HR,
            DebriefTone.NEUTRAL,
            68,
            "Climbing drove the late effort",
            "Heart rate rose from $earlyHr to $lateHr bpm at similar pace, while the second half averaged ${abs(climbChangeM)} m higher$totalText. The terrain explains the extra strain rather than fading fitness.",
            "On hilly routes, judge effort by breathing or heart rate rather than forcing flat-ground splits uphill."
        )
    }

    fun wentOutHotFaded(
        earlyPace: Double, latePace: Double, diffSec: Int, earlyHr: Int?, lateHr: Int?
    ): DebriefInsight {
        val body = if (earlyHr != null && lateHr != null) {
            "Pace slowed by $diffSec sec/km (${duration(earlyPace)} to ${duration(latePace)}/km) while heart rate stayed elevated ($earlyHr to $lateHr bpm). Paying high effort for slower kilometers points to an aggressive opening half."
        } else {
            "You slowed by $diffSec sec/km in the second half (${duration(earlyPace)} to ${duration(latePace)}/km), suggesting the early kilometers asked more than you could hold cleanly."
        }
        return insight(
            if (earlyHr != null && lateHr != null) DebriefCategory.PACING_AND_HR else DebriefCategory.TERRAIN_AND_SPLITS,
            DebriefTone.CAUTION,
            84,
            "Opened too fast and faded late",
            body,
            "Give yourself a calmer first third next time, about 10 to 15 sec/km easier, so you finish strong instead of grinding."
        )
    }

    fun smartHillPacing(earlyPace: Double, latePace: Double, diffSec: Int, climbChangeM: Int) = insight(
        DebriefCategory.TERRAIN_AND_SPLITS,
        DebriefTone.POSITIVE,
        74,
        "Smart pacing on hills",
        "Your second half ran $diffSec sec/km slower (${duration(earlyPace)} to ${duration(latePace)}/km), matching a route averaging ${abs(climbChangeM)} m higher. Letting pace float uphill kept your effort controlled.",
        "Keep running climbs by effort instead of chasing flat-ground pace targets."
    )

    fun easedLateControlled(earlyPace: Double, latePace: Double, diffSec: Int, earlyHr: Int, lateHr: Int) = insight(
        DebriefCategory.PACING_AND_HR,
        DebriefTone.NEUTRAL,
        65,
        "Eased off smoothly late",
        "Pace eased by $diffSec sec/km (${duration(earlyPace)} to ${duration(latePace)}/km) and heart rate came down alongside it ($earlyHr to $lateHr bpm). You dialed back intensity cleanly instead of fighting fatigue.",
        "Keep trusting that feedback when you choose to ease off on aerobic days."
    )

    fun steadyPace(first: Double, last: Double) = insight(
        DebriefCategory.TERRAIN_AND_SPLITS,
        DebriefTone.POSITIVE,
        70,
        "Disciplined even pacing",
        "Both halves landed right together (${duration(first)} and ${duration(last)}/km). Locking into a rhythm early that you can hold all the way home is a hallmark of smart pacing.",
        "Use this same opening feel as your pacing reference on similar routes."
    )

    fun paceShiftOnly(first: Double, last: Double, diffSec: Int, fullHalves: Boolean): DebriefInsight {
        val faster = last < first
        val lead = if (fullHalves) "Your second half" else "Your later kilometers"
        return insight(
            DebriefCategory.TERRAIN_AND_SPLITS,
            if (faster) DebriefTone.POSITIVE else DebriefTone.NEUTRAL,
            70,
            if (faster) "Finished stronger late" else "Pace eased in the back half",
            if (faster) "$lead ran $diffSec sec/km quicker (${duration(first)} to ${duration(last)}/km). Starting patient left enough in the tank to close fast."
            else "$lead ran $diffSec sec/km slower (${duration(first)} to ${duration(last)}/km). Check whether terrain, heat, or an ambitious start drove the fade.",
            if (faster) "Keep protecting the opening kilometers so you can close with control."
            else "Try easing slightly into the first few kilometers next time to see if your back half holds steadier."
        )
    }

    fun distance(actual: Double, planned: Double) = insight(
        DebriefCategory.EXECUTION,
        DebriefTone.NEUTRAL,
        75,
        "Distance matched, intensity unverified",
        "You logged ${km(actual)} km against ${km(planned)} km planned, though pace and heart-rate detail were too sparse to evaluate workout intensity.",
        "Wear your watch with steady GPS or heart-rate tracking on key sessions so intensity can be checked."
    )

    fun workPace(verdict: ExecutionVerdict, adherence: Int): DebriefInsight? {
        val (title, tone, score, explanation, focus) = when (verdict) {
            ExecutionVerdict.ON_TARGET -> listOf(
                "Work intervals hit the mark",
                "POSITIVE",
                "90",
                "You stayed inside target pace for $adherence% of the work segments, delivering the exact physiological stimulus planned for today.",
                "Repeat this controlled opening effort on your next quality workout."
            )
            ExecutionVerdict.INTENSITY_MISMATCH_TOO_FAST -> listOf(
                "Work reps ran faster than target",
                "CAUTION",
                "95",
                "You hit the prescribed range for $adherence% of the work and skewed faster. Overrunning pace targets raises recovery cost without adding extra aerobic return.",
                "Aim for the slower end of your pace target on rep 1 and earn any speed late."
            )
            ExecutionVerdict.INTENSITY_MISMATCH_TOO_SLOW -> listOf(
                "Work reps ran slower than target",
                "CAUTION",
                "95",
                "You hit the prescribed range for $adherence% of the work and ran slower overall. Fatigue, hills, or heat often drive that gap, so treat effort as feedback rather than forcing pace.",
                "Start near the gentler end of target range next session and check recovery before pushing."
            )
            ExecutionVerdict.PARTIALLY_EXECUTED -> listOf(
                "Part of the workout landed",
                "NEUTRAL",
                "78",
                "You held target pace for $adherence% of captured work segments, though parts of the session did not match the full structure.",
                "Review where the session shifted so you can dial in the opening reps next time."
            )
            else -> return null
        }
        return insight(DebriefCategory.EXECUTION, DebriefTone.valueOf(tone), score.toInt(), title, explanation, focus)
    }

    fun intervalShape(
        repCount: Int,
        firstPace: Double,
        lastPace: Double,
        fastestPace: Double,
        slowestPace: Double,
        pattern: IntervalPattern
    ): DebriefInsight = when (pattern) {
        IntervalPattern.NEGATIVE_SPLIT -> insight(
            DebriefCategory.EXECUTION,
            DebriefTone.POSITIVE,
            90,
            "Strongest on the final rep",
            "Across $repCount work reps, you opened controlled at ${duration(firstPace)}/km and closed at ${duration(lastPace)}/km. Finishing quickest means you paced the early reps smartly and got the full stimulus.",
            "Keep that patient rep 1 restraint. Closing fast is the cleanest sign of well-judged interval pacing."
        )
        IntervalPattern.CONSISTENT -> insight(
            DebriefCategory.EXECUTION,
            DebriefTone.POSITIVE,
            88,
            "Repeats stayed consistent",
            "Across $repCount work reps, pacing stayed tight from ${duration(firstPace)}/km on rep 1 to ${duration(lastPace)}/km on the final rep. Even splits across every interval maximize workout quality without blowing up early.",
            "Use this exact rhythm as your benchmark when repeating this session."
        )
        IntervalPattern.FRONT_LOADED_FADE -> insight(
            DebriefCategory.EXECUTION,
            DebriefTone.CAUTION,
            91,
            "Front-loaded the early reps",
            "Opening at ${duration(firstPace)}/km and fading to ${duration(lastPace)}/km across $repCount reps drained speed from the back half of the set. Going out too hot turns controlled quality work into a late grind.",
            "Hold rep 1 a touch calmer next time so rep $repCount feels as crisp as rep 1."
        )
        IntervalPattern.VARIABLE -> insight(
            DebriefCategory.EXECUTION,
            DebriefTone.NEUTRAL,
            74,
            "Interval paces varied across reps",
            "Across $repCount work reps, paces ranged from ${duration(fastestPace)} to ${duration(slowestPace)}/km (opening ${duration(firstPace)}/km, closing ${duration(lastPace)}/km). Tighter rep-to-rep pacing locks in the intended stimulus.",
            "Treat rep 1 as a pacing dial-in so every rep sits in a narrow band."
        )
    }

    fun contactTime(first: Int, last: Int, rose: Boolean) = insight(
        DebriefCategory.RUNNING_DYNAMICS,
        if (rose) DebriefTone.CAUTION else DebriefTone.POSITIVE,
        if (rose) 82 else 73,
        if (rose) "Form softened late under fatigue" else "Mechanics held up late",
        if (rose) "At a similar pace, ground contact lengthened from $first ms early to $last ms late. Longer contact late is an early sign of muscular fatigue softening your elastic spring."
        else "At a similar pace past 45 minutes, ground contact stayed consistent ($first ms early, $last ms late). Holding your spring late signals solid muscular durability.",
        if (rose) "Focus on staying tall with quick, light steps in the final third, and protect recovery before your next long run."
        else "Your legs are absorbing sustained mileage cleanly. Keep runner strength work consistent to preserve this late-run form."
    )

    fun cadenceDropAtSteadyPace(first: Int, last: Int) = insight(
        DebriefCategory.CADENCE,
        DebriefTone.CAUTION,
        76,
        "Stride lengthened as legs tired",
        "At a steady pace, cadence dipped from $first to $last spm late. A dropping step rate at the same speed often means overstriding under fatigue, which adds braking stress.",
        "When legs feel heavy late, think quick, compact steps under your hips rather than reaching forward."
    )

    fun stableCadence(first: Int, last: Int) = insight(
        DebriefCategory.CADENCE,
        DebriefTone.POSITIVE,
        62,
        "Turnover stayed locked in",
        "Past 45 minutes at similar pace, your cadence barely moved ($first spm early, $last spm late), showing steady neuromuscular rhythm under fatigue.",
        "Keep this relaxed, consistent rhythm on sustained runs."
    )

    fun cadence(first: Int, last: Int, faster: Boolean) = insight(
        DebriefCategory.CADENCE,
        DebriefTone.NEUTRAL,
        55,
        if (faster) "Quicker turnover with speed" else "Cadence eased with pace",
        "As you ${if (faster) "picked up the pace" else "eased off"}, your step rate shifted naturally from $first to $last spm.",
        if (faster) "Notice how quicker, lighter steps help generate speed without forcing stride length."
        else "Keep steps light and relaxed when dialing back pace."
    )

    fun distanceJump(currentKm: Double, priorLongestKm: Double, jumpPct: Int) = insight(
        DebriefCategory.LOAD_AND_RECOVERY,
        DebriefTone.CAUTION,
        84,
        "Biggest run of the month",
        "At ${km(currentKm)} km, this run jumped $jumpPct% past your longest run of the last 30 days (${km(priorLongestKm)} km). Big single-session distance jumps increase recovery demand on tendons and joints.",
        "Give your legs 24 to 48 hours of easy effort or rest before your next hard session so tissues adapt safely."
    )

    fun doubleRunDay(runCount: Int, totalKm: Double?) = insight(
        DebriefCategory.LOAD_AND_RECOVERY,
        DebriefTone.NEUTRAL,
        60,
        if (runCount == 2) "Second run of the day" else "Run $runCount of the day",
        "This session adds to earlier running today${totalKm?.let { ", bringing daily volume to ${km(it)} km" }.orEmpty()}. Account for the combined load when gauging tomorrow's effort.",
        "Keep tomorrow's effort honest to your total daily load, not just this single run."
    )

    fun dose(discipline: DebriefDiscipline, evidence: DebriefDose, tone: DebriefTone): DebriefInsight {
        val title = when (discipline) {
            DebriefDiscipline.STRENGTH -> when (tone) {
                DebriefTone.POSITIVE -> "Full strength dose completed"
                DebriefTone.CAUTION -> "Strength targets ran short"
                DebriefTone.NEUTRAL -> "Partial strength session logged"
            }
            DebriefDiscipline.ISOMETRICS -> when (tone) {
                DebriefTone.POSITIVE -> "Isometric holds met target"
                DebriefTone.CAUTION -> "Hold durations came up short"
                DebriefTone.NEUTRAL -> "Partial isometric holds logged"
            }
            DebriefDiscipline.CALISTHENICS -> when (tone) {
                DebriefTone.POSITIVE -> "Bodyweight sets hit target"
                DebriefTone.CAUTION -> "Bodyweight sets missed reps"
                DebriefTone.NEUTRAL -> "Partial bodyweight sets logged"
            }
            DebriefDiscipline.CONDITIONING -> when (tone) {
                DebriefTone.POSITIVE -> "Conditioning work met plan"
                DebriefTone.CAUTION -> "Conditioning rounds fell short"
                DebriefTone.NEUTRAL -> "Partial conditioning rounds logged"
            }
            else -> error("Unsupported dose discipline")
        }
        val unit = if (discipline == DebriefDiscipline.CONDITIONING) "rounds" else "sets"
        val total = if (evidence.plannedSeconds > 0) {
            val time = if (discipline == DebriefDiscipline.ISOMETRICS) "hold time" else "work time"
            "your $time totaled ${duration(evidence.loggedSeconds.toDouble())} of the planned ${duration(evidence.plannedSeconds.toDouble())}"
        } else {
            "the plan called for ${evidence.planned} $unit"
        }
        val loggedUnit = if (evidence.logged == 1) unit.dropLast(1) else unit
        val takeaway = when (tone) {
            DebriefTone.POSITIVE -> "Clean execution builds durability without rushing progression."
            DebriefTone.CAUTION -> "Missing target reps or hold time usually signals fatigue or short rest between $unit."
            DebriefTone.NEUTRAL -> "Only logged sets count toward progression, so confirm every working set you complete."
        }
        val focus = when (tone) {
            DebriefTone.POSITIVE -> "Keep form crisp on this baseline before adding load or reps next week."
            DebriefTone.CAUTION -> "Repeat this same target next session and take full rest between $unit so quality stays high."
            DebriefTone.NEUTRAL -> "Pick up with full set coverage next session when your schedule allows."
        }
        val body = "You hit target in ${evidence.met} of the ${evidence.logged} $loggedUnit logged, and $total. $takeaway"
        return insight(DebriefCategory.EXECUTION, tone, 80, title, body, focus)
    }

    fun contacts(actual: Int, cap: Int, allLogged: Boolean): DebriefInsight {
        val exceeded = actual > cap
        val (title, tone, score, meaning, focus) = when {
            exceeded -> listOf(
                "Extra jump contacts logged",
                "CAUTION",
                "96",
                "Exceeding the contact cap adds tendon stress without improving reactive stiffness.",
                "Stop right at the cap next session even when your legs still feel bouncy."
            )
            allLogged -> listOf(
                "Reactive contacts within cap",
                "POSITIVE",
                "80",
                "Staying within the contact ceiling builds tendon elasticity while keeping recovery low.",
                "Keep crisp ground contact quality as your priority before increasing volume."
            )
            else -> listOf(
                "Jump contacts logged so far",
                "NEUTRAL",
                "80",
                "Partial reactive work still primes stiffness without accumulating unnecessary fatigue.",
                "Complete full sets only when your Achilles and calves feel springy."
            )
        }
        return insight(
            DebriefCategory.EXECUTION,
            DebriefTone.valueOf(tone),
            score.toInt(),
            title,
            "You logged $actual ground contacts against a cap of $cap. $meaning",
            focus
        )
    }

    fun effort(actual: Int?, limit: Int, harder: Boolean, reportedHarder: Boolean): DebriefInsight {
        val detail = when {
            reportedHarder && actual != null -> "You rated this session $actual/10 and it felt harder than planned."
            actual != null -> "You rated this session $actual/10 on a day meant to stay at or below $limit/10."
            else -> "This session felt harder than expected."
        }
        val meaning = if (harder) {
            "Pushing support work harder than intended can carry fatigue into your running days."
        } else {
            "Controlled effort keeps support work assisting your running rather than competing with it."
        }
        return insight(
            DebriefCategory.LOAD_AND_RECOVERY,
            if (harder) DebriefTone.CAUTION else DebriefTone.POSITIVE,
            if (harder) 89 else 62,
            if (harder) "Effort ran harder than planned" else "Effort stayed under control",
            "$detail $meaning",
            if (harder) "Trim load or start a touch easier next session so support work leaves your legs fresh."
            else "Keep this controlled effort ceiling on support days."
        )
    }

    fun runnerRepsInReserve(actual: Int, target: Int) = insight(
        DebriefCategory.LOAD_AND_RECOVERY,
        if (actual < target) DebriefTone.CAUTION else DebriefTone.POSITIVE,
        if (actual < target) 89 else 62,
        if (actual < target) "Pushed too close to failure" else "Strength kept in reserve",
        "You finished with $actual ${if (actual == 1) "rep" else "reps"} left, against a target of $target in reserve. " +
            if (actual < target) "Grinding near failure increases soreness that lingers into key runs."
            else "Stopping with reps in reserve builds power while protecting running freshness.",
        if (actual < target) "Drop the load slightly next session so you finish sets with $target reps in reserve."
        else "Keep leaving that $target-rep margin so strength supports your running."
    )

    fun hardRunSpacing(days: Long, plyometric: Boolean): DebriefInsight {
        val whenText = if (days == 0L) "the same day as" else "the day after"
        val work = if (plyometric) "jump session" else "lower-body session"
        return insight(
            DebriefCategory.LOAD_AND_RECOVERY,
            DebriefTone.CAUTION,
            94,
            "Hard sessions stacked closely",
            "You placed this $work $whenText a speed or long run. Back-to-back high-impact stress raises lower-leg fatigue and slows recovery.",
            "Leave at least one easy or rest day between heavy lower-body work and key runs."
        )
    }

    private fun duration(seconds: Double) = ActivityRecord.duration(seconds)
    private fun km(value: Double) = String.format(Locale.ROOT, "%.1f", value)
    private fun insight(
        category: DebriefCategory,
        tone: DebriefTone,
        score: Int,
        title: String,
        body: String,
        nextFocus: String? = null
    ) = DebriefInsight(category, title, body, tone, score, nextFocus)
}
