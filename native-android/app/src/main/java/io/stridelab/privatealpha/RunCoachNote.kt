package io.stridelab.privatealpha

import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

internal data class RunCoachNote(
    val headline: String,
    val observation: String,
    val dayContext: String?,
    val nextFocus: String
)

internal fun runCoachNote(
    run: ActivityRecord,
    recorded: ActivityDetail?,
    history: List<ActivityRecord>,
    workout: ActivityWorkoutAssessment,
    zone: ZoneId = ZoneId.systemDefault()
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
        "You averaged ${ActivityRecord.duration(pace)} per km, $change over " +
            "${comparison.count} similar runs. Was the route or the effort different this time?"
    } else if (pace != null) {
        "You averaged ${ActivityRecord.duration(pace)} per km. Use that as a reference when you run a similar distance at a similar effort."
    } else {
        "I don’t have enough pace data to judge how you paced this one. Did you finish feeling like you could keep going?"
    }

    val quality = workout.intended?.kind == ActivityWorkoutKind.QUALITY_RUN ||
        workout.detected.kind == ActivityWorkoutKind.QUALITY_RUN
    val focus = when {
        quality -> "On your next harder session, compare how the effort felt early and late. If you had to push sooner than planned, try a more controlled start."
        earlierToday.isNotEmpty() -> "Take both the earlier running and this session into account. If the next run is meant to be easy, keep the effort low enough to talk in full sentences."
        character?.contains("Slower finish") == true -> "If this was meant to be steady, try starting a little easier on a similar route. See if you can finish at the same effort without having to push."
        character?.contains("Faster finish") == true -> "Compare how the faster finish felt with the opening kilometers. On easy days, keep the finish conversational even if the pace picks up."
        else -> "Let the purpose of your next run set the effort. On an easy day, use the talk test. If you can speak in full sentences, you’re in the right ballpark."
    }
    val headline = when {
        quality -> "Think about how the harder running felt"
        character?.contains("Slower finish") == true -> "Your later splits were slower"
        character?.contains("Faster finish") == true -> "You picked up the pace late"
        character?.contains("Steady pacing") == true -> "You kept the pace steady"
        earlierToday.isNotEmpty() -> "This run adds to your day"
        else -> "A look at your run"
    }
    return RunCoachNote(headline, observation, dayContext, focus)
}
