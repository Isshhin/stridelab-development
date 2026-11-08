package io.stridelab.privatealpha

/** Presentation-only evidence for a failed search. Does not change recovery rules. */
internal object ScheduleConflictExplanation {
    fun observed(first: PlannedSession, second: PlannedSession): String {
        val ordered = listOf(first, second).sortedWith(
            compareBy<PlannedSession> { it.date }.thenBy { it.id }
        )
        fun describe(session: PlannedSession): String =
            "${session.date.dayOfWeek} ${session.date} (${session.title})"
        return " One recovery conflict encountered during placement: " +
            describe(ordered[0]) + " and " + describe(ordered[1]) + "." +
            " Other schedule or time constraints may also apply."
    }
}
