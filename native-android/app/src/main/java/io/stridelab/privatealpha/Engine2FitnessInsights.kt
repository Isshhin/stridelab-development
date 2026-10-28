package io.stridelab.privatealpha

/** Human-readable observation only; this is not a race predictor or a coaching verdict. */
internal enum class Engine2InsightConfidence { UNKNOWN, PARTIAL, OBSERVED_ONLY }
internal data class Engine2EvidenceInsight(
    val headline: String,
    val explanation: String,
    val confidence: Engine2InsightConfidence,
    val knownRecentKm: Double?,
    val knownPreviousKm: Double?,
    val citedWorkoutIds: Set<String>,
    val limitations: List<String>
)

/**
 * Reports source-supported comparisons. Does not prescribe a plan, estimate
 * VO2max, diagnose recovery or treat a missing activity as a zero-distance run.
 */
internal object Engine2FitnessInsights {
    fun running(context: Engine2AthleteContext, days: Long = 28): Engine2EvidenceInsight {
        val trend = Engine2WorkloadReview.compare(context, days)
        val ids = context.workouts.filter {
            it.discipline == Engine2Discipline.RUNNING &&
                it.status == Engine2RecordStatus.CONFIRMED
        }.map { it.id }.toSet()
        val recent = trend.recent.running
        val previous = trend.previous.running
        if (recent.confirmedRuns == 0) {
            return Engine2EvidenceInsight(
                "No confirmed running activity in this observation window",
                "The available records do not establish whether running occurred elsewhere or while disconnected.",
                Engine2InsightConfidence.UNKNOWN, null,
                previous.knownDistanceKm, ids, trend.warnings
            )
        }
        if (!trend.comparableRunningDistance) {
            return Engine2EvidenceInsight(
                "Available running records are incomplete",
                "The observed records contain " + recent.confirmedRuns + " confirmed runs; " +
                    recent.runsMissingDistance + " lack recorded distance. " +
                    "A complete period-to-period distance comparison is unsupported.",
                Engine2InsightConfidence.PARTIAL,
                recent.knownDistanceKm, previous.knownDistanceKm, ids, trend.warnings
            )
        }
        val delta = trend.runningDistanceChangeKm!!
        val direction = when {
            delta > 0.0 -> "higher"
            delta < 0.0 -> "lower"
            else -> "unchanged"
        }
        return Engine2EvidenceInsight(
            "Observed running distance was " + direction,
            "Source-backed distance changed by " +
                kotlin.math.abs(delta).toString() + " km across equal observation windows. " +
                "This is descriptive history, not evidence of fitness improvement or a safe future load.",
            Engine2InsightConfidence.OBSERVED_ONLY,
            recent.knownDistanceKm, previous.knownDistanceKm,
            ids, trend.warnings
        )
    }
}
