package io.stridelab.privatealpha

import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

internal data class RunCoachNote(
    val headline: String,
    val observation: String,
    val dayContext: String?,
    val nextFocus: String,
    val finding: CoachingFinding? = null
)

internal fun runCoachNote(
    run: ActivityRecord,
    recorded: ActivityDetail?,
    history: List<ActivityRecord>,
    workout: ActivityWorkoutAssessment,
    zone: ZoneId = ZoneId.systemDefault(),
    correction: UserActivityWorkoutLabel? = null
): RunCoachNote {
    val day = run.startedAt.atZone(zone).toLocalDate()
    val earlierToday = history.asSequence()
        .filter { it.id != run.id && it.startedAt < run.startedAt && DashboardSummary.isRun(it) }
        .filter { it.startedAt.atZone(zone).toLocalDate() == day }
        .distinctBy { it.id }.toList()
    val dayContext = earlierToday.takeIf { it.isNotEmpty() }?.let { runs ->
        val distances = (runs + run).map { it.distanceM?.takeIf { m -> m.isFinite() && m >= 0 } }
        val count = runs.size + 1
        if (distances.all { it != null }) {
            String.format(Locale.getDefault(),
                "This was run %d that day, bringing you to %.1f km.",
                count, distances.filterNotNull().sum() / 1000)
        } else "This was run $count that day."
    }

    val comparison = ActivityIntelligence.comparison(run, history)
    val pace = run.recordedPace?.takeIf { it.isFinite() && it > 0 }
    val character = ActivityIntelligence.workoutCharacter(run, recorded)
    val observation = if (comparison != null && pace != null) {
        val delta = pace - comparison.medianPace
        val change = if (abs(comparison.changePercent) < 1) "close to your usual pace"
            else "${ActivityRecord.duration(abs(delta))} per km " +
                if (delta < 0) "quicker than usual" else "slower than usual"
        "You averaged ${ActivityRecord.duration(pace)}/km, $change across ${comparison.count} similar runs."
    } else if (pace != null) {
        "Your average of ${ActivityRecord.duration(pace)}/km gives you a reference for runs of similar distance and effort."
    } else {
        "Use how the run felt as your guide when pace is missing."
    }

    val kind = correction?.kind ?: workout.intended?.kind ?: workout.detected.kind
    val subtype = correction?.qualitySubtype ?: workout.intended?.qualitySubtype ?: workout.detected.qualitySubtype
    val quality = kind == ActivityWorkoutKind.QUALITY_RUN
    val workSteps = recorded?.workoutSteps.orEmpty().filter { it.role == "WORK" }.map { it.index }.toSet()
    val comparable = recorded?.takeIf { it.pauseIntervals.isEmpty() }?.laps.orEmpty()
        .filter { it.workoutStepIndex in workSteps && it.pace?.let { pace -> pace.isFinite() && pace in 120.0..900.0 } == true &&
            it.distance?.let { distance -> distance.isFinite() && distance >= 100 } == true &&
            it.seconds?.let { seconds -> seconds.isFinite() && seconds >= 30 } == true }
        .groupBy { it.workoutStepIndex }.values.maxByOrNull { it.size }.orEmpty()
    val repeats = comparable.takeIf { laps ->
        laps.size >= 3 && laps.maxOf { it.distance!! } <= laps.minOf { it.distance!! } * 1.15
    }?.map { it.pace!! }
    val finding = if (quality && repeats != null) {
        val spread = (repeats.max() - repeats.min()) / repeats.average()
        CoachingFinding(
            observation = if (spread <= .05) "You kept the repeats close together" else "Your repeat paces varied",
            interpretation = if (spread <= .05)
                "Across ${repeats.size} comparable repeats, the recorded paces stayed close."
            else "Across ${repeats.size} comparable repeats, there was a noticeable spread from quickest to slowest.",
            evidence = repeats.mapIndexed { i, value -> "Repeat ${i + 1}: ${ActivityRecord.duration(value)}/km" },
            recommendation = if (spread <= .05) "Keep that rhythm next time and check that the last repeat feels as controlled as the first."
                else "If these repeats had the same target, use a controlled first rep and try to match that effort on the rest.",
            confidence = "Compared laps from the same recorded work step. Pace doesn’t verify effort or target intensity."
        )
    } else null
    val focus = when {
        kind == ActivityWorkoutKind.RECOVERY_RUN -> "Keep your next recovery run relaxed from the start. Use full sentences as a check and let the pace drop on hills."
        kind == ActivityWorkoutKind.EASY_RUN -> "On your next easy run, check that you can still talk in full sentences near the finish."
        kind == ActivityWorkoutKind.LONG_RUN -> "On your next long run, compare the effort in the last stretch with the opening. If it got harder sooner than planned, try a gentler start."
        quality && subtype == ActivityQualitySubtype.THRESHOLD -> "On your next threshold session, aim for a controlled effort you can hold through the final work block."
        quality -> "On your next harder session, compare how the effort felt early and late. If you had to push sooner than planned, try a more controlled start."
        earlierToday.isNotEmpty() -> "Take both the earlier running and this session into account. If the next run is meant to be easy, keep the effort low enough to talk in full sentences."
        character?.contains("Slower finish") == true -> "If this was meant to be steady, try starting a little easier on a similar route. See if you can finish at the same effort without having to push."
        character?.contains("Faster finish") == true -> "Compare how the faster finish felt with the opening kilometers. On easy days, keep the finish conversational even if the pace picks up."
        else -> "Let the purpose of your next run set the effort. On an easy day, use the talk test. If you can speak in full sentences, you’re in the right ballpark."
    }
    val headline = when {
        character?.contains("Slower finish") == true -> "Your later splits were slower"
        character?.contains("Faster finish") == true -> "You picked up the pace late"
        character?.contains("Steady pacing") == true -> "You kept the pace steady"
        earlierToday.isNotEmpty() -> "This run adds to your day"
        pace != null -> "Average pace ${ActivityRecord.duration(pace)}/km"
        else -> "Run effort notes"
    }
    return RunCoachNote(finding?.observation ?: headline, finding?.interpretation ?: observation,
        dayContext, finding?.recommendation ?: focus, finding)
}
