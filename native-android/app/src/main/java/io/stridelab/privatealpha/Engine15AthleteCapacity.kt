package io.stridelab.privatealpha

/**
 * M3 evidence contract shared with the future independent M4 volume skeleton.
 *
 * Describes what is known about recent workload; it does NOT choose a weekly
 * plan, infer quality tolerance, certify fitness, or change Engine 1.5 doses.
 * All values are km/week unless explicitly named as a single-run distance.
 */
internal enum class RecentMileageBasis {
    LEGACY_UNSPECIFIED,     // Old plans: 0 may mean missing information.
    UNKNOWN,                // Explicitly no reliable usual weekly average.
    SELF_REPORTED_AVERAGE,  // A typical/recent average supplied by the runner.
    PEAK_WEEK_ONLY,         // A peak is not a typical training week.
    RECORDED_PARTIAL,       // Incomplete activity coverage; not a verified average.
    CONFIRMED_ZERO;         // Runner explicitly reports no recent running.

    companion object {
        fun parse(raw:String):RecentMileageBasis =
            entries.firstOrNull { it.name==raw } ?: LEGACY_UNSPECIFIED
    }
}

internal enum class AthleteBaselineConfidence {
    NONE,
    LEGACY_UNVERIFIED,
    SELF_REPORTED_PROVISIONAL,
    PARTIAL_ACTIVITY_ESTIMATE,
    COMPLETE_FOUR_WEEK_HISTORY
}

internal enum class AthleteBaselineStatus {
    UNKNOWN, CONFIRMED_ZERO, PROVISIONAL_AVERAGE, PEAK_ONLY,
    COMPLETE_HISTORY, NEEDS_REVIEW
}

internal data class Engine15AthleteCapacity(
    /** Feasible input to consider in M4, NOT permission to prescribe this load. */
    val typicalWeeklyKm:Double?,
    val reportedTypicalWeeklyKm:Double?,
    val reportedPeakWeeklyKm:Double?,
    val reportedLongestRunKm:Double?,
    val verifiedFourWeekAverageKm:Double?,
    val basis:RecentMileageBasis,
    val confidence:AthleteBaselineConfidence,
    val status:AthleteBaselineStatus,
    /** Hard evidence/data contradictions; unresolved profile must not drive M4. */
    val reviewReasons:List<String>,
    /** Structural property only. No fitness or readiness inference. */
    val completeWeekCount:Int
) {
    val readyForVolumeSkeleton:Boolean get()=
        typicalWeeklyKm!=null && reviewReasons.isEmpty() &&
            status !in setOf(AthleteBaselineStatus.UNKNOWN,AthleteBaselineStatus.PEAK_ONLY)
}

/**
 * No sparse records are promoted to full training coverage. A complete-week
 * average comes only from IndependentRunningHistory's explicit coverage proof.
 *
 * When a reported average and a verified mean both exist, keep BOTH values
 * instead of silently replacing either with an arbitrary confidence formula.
 * The independent M4 module will own the progression envelope and resolution.
 */
internal object Engine15AthleteBaseline {
    private fun validKm(value:Double?):Boolean =
        value!=null && value.isFinite() && value>=0.0 && value<=120.0

    fun assess(
        request:PlanRequest,
        history:IndependentRunningHistoryEvidence?=null
    ):Engine15AthleteCapacity {
        val rawBasis=request.recentMileageBasis
        val basis=RecentMileageBasis.parse(rawBasis)
        val notes=mutableListOf<String>()
        if(basis==RecentMileageBasis.LEGACY_UNSPECIFIED &&
            rawBasis!=RecentMileageBasis.LEGACY_UNSPECIFIED.name)
            notes+="UNRECOGNIZED_MILEAGE_BASIS"
        val recent=request.weeklyKm
        if(!validKm(recent))notes+="INVALID_RECENT_KM"
        val peak=request.reportedPeakWeeklyKm
        if(peak!=null && !validKm(peak))notes+="INVALID_PEAK_KM"
        // Legacy longestKm has no recency/source proof; keep it provisional.
        val longest=request.longestKm
        if(!validKm(longest))notes+="INVALID_LONGEST_KM"

        val fourWeek=history?.confirmedFourWeekMeanKm
        if(fourWeek!=null && !validKm(fourWeek))notes+="INVALID_COMPLETE_HISTORY_MEAN"
        val confirmed=fourWeek?.takeIf(::validKm)
        val reportedTypical=when(basis) {
            RecentMileageBasis.SELF_REPORTED_AVERAGE,
            RecentMileageBasis.RECORDED_PARTIAL ->
                recent.takeIf { validKm(it) && it>0.0 }
            RecentMileageBasis.LEGACY_UNSPECIFIED ->
                recent.takeIf { validKm(it) && it>0.0 }
            else -> null
        }
        if(basis in setOf(RecentMileageBasis.SELF_REPORTED_AVERAGE,
                RecentMileageBasis.RECORDED_PARTIAL) && reportedTypical==null)
            notes+="MISSING_REPORTED_AVERAGE"
        if(basis==RecentMileageBasis.CONFIRMED_ZERO && recent!=0.0)
            notes+="ZERO_CONTRADICTS_LEGACY_WEEKLY_KM"
        if(basis in setOf(RecentMileageBasis.UNKNOWN,RecentMileageBasis.PEAK_WEEK_ONLY) &&
            recent>0.0)notes+="UNRESOLVED_WEEKLY_KM_FOR_BASIS"
        if(basis==RecentMileageBasis.PEAK_WEEK_ONLY &&
            (peak==null || !validKm(peak) || peak<=0.0))
            notes+="MISSING_PEAK_WEEK_KM"
        if(reportedTypical!=null && validKm(peak) &&
            peak!!+1e-8<reportedTypical)
            notes+="PEAK_BELOW_REPORTED_AVERAGE"
        if(basis==RecentMileageBasis.CONFIRMED_ZERO && confirmed!=null &&
            confirmed>0.0)notes+="ZERO_CONTRADICTS_COMPLETE_HISTORY"

        val primary=when(basis) {
            RecentMileageBasis.CONFIRMED_ZERO -> 0.0.takeIf { recent==0.0 }
            RecentMileageBasis.SELF_REPORTED_AVERAGE,
            RecentMileageBasis.RECORDED_PARTIAL,
            RecentMileageBasis.LEGACY_UNSPECIFIED -> reportedTypical ?: confirmed
            RecentMileageBasis.UNKNOWN,
            RecentMileageBasis.PEAK_WEEK_ONLY -> confirmed
        }
        // A recorded mean is distinct from an athlete's reported typical week.
        // Never imply either one proves demonstrated tolerance.
        val confidence=when {
            primary==null -> AthleteBaselineConfidence.NONE
            reportedTypical!=null -> when(basis) {
                RecentMileageBasis.SELF_REPORTED_AVERAGE -> AthleteBaselineConfidence.SELF_REPORTED_PROVISIONAL
                RecentMileageBasis.RECORDED_PARTIAL -> AthleteBaselineConfidence.PARTIAL_ACTIVITY_ESTIMATE
                else -> AthleteBaselineConfidence.LEGACY_UNVERIFIED
            }
            basis==RecentMileageBasis.CONFIRMED_ZERO -> AthleteBaselineConfidence.SELF_REPORTED_PROVISIONAL
            confirmed!=null -> AthleteBaselineConfidence.COMPLETE_FOUR_WEEK_HISTORY
            else -> AthleteBaselineConfidence.NONE
        }
        val status=when {
            notes.isNotEmpty() -> AthleteBaselineStatus.NEEDS_REVIEW
            basis==RecentMileageBasis.CONFIRMED_ZERO -> AthleteBaselineStatus.CONFIRMED_ZERO
            primary!=null && confidence==AthleteBaselineConfidence.COMPLETE_FOUR_WEEK_HISTORY ->
                AthleteBaselineStatus.COMPLETE_HISTORY
            reportedTypical!=null -> AthleteBaselineStatus.PROVISIONAL_AVERAGE
            basis==RecentMileageBasis.PEAK_WEEK_ONLY -> AthleteBaselineStatus.PEAK_ONLY
            else -> AthleteBaselineStatus.UNKNOWN
        }
        return Engine15AthleteCapacity(
            typicalWeeklyKm=primary.takeIf { notes.isEmpty() },
            reportedTypicalWeeklyKm=reportedTypical,
            reportedPeakWeeklyKm=peak?.takeIf(::validKm),
            reportedLongestRunKm=longest.takeIf { validKm(it) && it>0.0 },
            verifiedFourWeekAverageKm=confirmed,
            basis=basis,confidence=confidence,status=status,
            reviewReasons=notes.distinct(),completeWeekCount=history?.completeWeekCount ?: 0
        )
    }
}
