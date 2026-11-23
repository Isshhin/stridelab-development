package io.stridelab.privatealpha

enum class DebriefTone { POSITIVE, NEUTRAL, CAUTION }

enum class DebriefCategory {
    PACING_AND_HR,
    INTENT_AND_ZONES,
    TERRAIN_AND_SPLITS,
    CADENCE,
    RUNNING_DYNAMICS,
    EXECUTION,
    LOAD_AND_RECOVERY
}

data class DebriefInsight(
    val category: DebriefCategory,
    val title: String,
    val body: String,
    val tone: DebriefTone,
    val priorityScore: Int
) {
    init {
        require(priorityScore in 0..100)
        require(title.isNotBlank() && body.isNotBlank())
    }
}

data class SessionDebrief(
    val headline: String,
    val summary: String,
    val tone: DebriefTone,
    val insights: List<DebriefInsight>,
    val paragraphs: List<String> = emptyList()
) {
    init {
        require(headline.isNotBlank() && summary.isNotBlank())
        require(insights.size <= 3)
        require(insights.zipWithNext().all { (a, b) -> a.priorityScore >= b.priorityScore })
    }

    companion object {
        /** Stable ties preserve the producer's order. Sparse sessions may produce no debrief. */
        fun fromInsights(candidates: Iterable<DebriefInsight>): SessionDebrief? {
            val ranked = candidates.sortedByDescending { it.priorityScore }.take(3)
            val lead = ranked.firstOrNull() ?: return null
            return SessionDebrief(lead.title, lead.body, lead.tone, ranked)
        }
    }
}
