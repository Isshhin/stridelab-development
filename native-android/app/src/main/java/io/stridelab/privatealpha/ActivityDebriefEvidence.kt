package io.stridelab.privatealpha

/** Only accepted links for this exact recording may supply planned-work evidence. */
internal fun linkedDebriefExecution(
    activityId: String,
    match: ActivityMatchRecord?,
    analyses: Map<String, WorkoutExecutionAnalysis>
): WorkoutExecutionAnalysis? {
    if (match?.activityId != activityId || match.matchStatus !in
        setOf(ActivityMatchStatus.AUTO_LINKED, ActivityMatchStatus.MANUALLY_LINKED)) return null
    val sessionId = match.plannedSessionId ?: return null
    return analyses[sessionId]?.takeIf { it.activityId == activityId && it.plannedSessionId == sessionId }
}
