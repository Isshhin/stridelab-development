package io.stridelab.privatealpha

import java.time.LocalDate

internal data class TrainingScheduleDate(
    val date: LocalDate,
    val sessions: List<PlannedSession>
)

internal fun tracksideScheduledDates(sessions: List<PlannedSession>): List<TrainingScheduleDate> =
    sessions.asSequence()
        .filter { it.status != "unassigned" }
        .groupBy { it.date }
        .toSortedMap()
        .map { (date, rows) -> TrainingScheduleDate(date, rows) }
