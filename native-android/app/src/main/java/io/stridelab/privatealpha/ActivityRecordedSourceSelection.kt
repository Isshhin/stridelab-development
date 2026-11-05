package io.stridelab.privatealpha

/**
 * Selects recorded streams without changing the Garmin-first summary, stored
 * source IDs or volume calculations. Garmin GPS takes precedence whenever it
 * exists; a matched Intervals.icu route remains a truthful fallback.
 */
internal object ActivityRecordedSourceSelection {
    fun hasGps(detail:ActivityDetail?):Boolean =
        detail != null && RecordedGpsRoute.from(detail.samples).pointCount>=2

    fun preferred(
        primary:ActivityRecord,
        supplemental:ActivityRecord?,
        details:Map<String,ActivityDetail>
    ):ActivityRecord {
        val garminDetail=details[primary.id]
        val secondaryDetail=supplemental?.let { details[it.id] }
        // Prefer Garmin's own *recorded* route, not merely a summary.
        if(primary.source=="garmin" && hasGps(garminDetail))return primary
        // Intervals.icu still supplies a real route if Garmin is summary-only.
        if(supplemental!=null && hasGps(secondaryDetail))return supplemental
        if(garminDetail!=null)return primary
        if(supplemental!=null && secondaryDetail!=null)return supplemental
        return primary
    }
}
