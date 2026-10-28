package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate

/**
 * Phase 34 contract: view-model projections only, no persistence or synthesized
 * measurements. Native FIT and remote provider adapters can target the same
 * ActivityRecord/ActivityDetail sources without changing the screen.
 */
internal sealed interface ActivityDetailPresentation {
    val accountId: String
    val title: String
    val kind: String
    val provenanceLabel: String

    data class SourceRecorded(
        override val accountId: String,
        override val title: String,
        override val kind: String,
        override val provenanceLabel: String,
        val originalId: String,
        val startedAt: Instant,
        val distanceMeters: Double?,
        val durationSeconds: Double?,
        val recordedPaceSecondsPerKm: Double?,
        val averageHeartRate: Double?,
        val elevationGainMeters: Double?,
        val laps: List<ActivityLap>,
        val samples: List<ActivitySample>,
        val gpsCoordinates: List<Pair<Double,Double>>,
        val fetchedAtMillis: Long?
    ) : ActivityDetailPresentation {
        val hasGpsTrace: Boolean get() = gpsCoordinates.size >= 2
        val hasPaceChart: Boolean get() = samples.count { it.pace != null } >= 2
        val hasHrChart: Boolean get() = samples.count { it.hr != null } >= 2
    }

    data class LocallyLogged(
        override val accountId: String,
        override val title: String,
        override val kind: String,
        override val provenanceLabel: String = "User-entered workout log",
        val planId: String,
        val sessionId: String,
        val performedOn: LocalDate?,
        val recordedAt: Instant?,
        val status: String,
        val minutes: Double?,
        val distanceKm: Double?,
        val effort: Int?,
        val sets: List<SetRecord>,
        val notes: String,
        val linkedActivityId: String?
    ) : ActivityDetailPresentation
}

internal object ActivityDetailPresentationFactory {
    fun recorded(account: String, summary: ActivityRecord,
                 recordedDetail: ActivityDetail?): ActivityDetailPresentation.SourceRecorded {
        require(account.isNotBlank())
        require(recordedDetail == null || recordedDetail.activityId == summary.id) {
            "Activity samples cannot be attached to another source record."
        }
        val gps = recordedDetail?.samples?.mapNotNull { sample ->
            val latitude=sample.latitude
            val longitude=sample.longitude
            if(latitude == null || longitude == null) null else latitude to longitude
        } ?: emptyList()
        return ActivityDetailPresentation.SourceRecorded(
            account,title=summary.title,kind=summary.type,
            provenanceLabel=summary.sourceLabel,originalId=summary.id,
            startedAt=summary.startedAt,distanceMeters=summary.distanceM,
            durationSeconds=summary.durationS,recordedPaceSecondsPerKm=summary.recordedPace,
            averageHeartRate=summary.averageHr,elevationGainMeters=summary.elevationM,
            laps=recordedDetail?.laps ?: emptyList(),
            samples=recordedDetail?.samples ?: emptyList(),
            gpsCoordinates=gps,fetchedAtMillis=recordedDetail?.fetchedAt
        )
    }

    fun logged(plan: PlanSnapshot, sessionId: String): ActivityDetailPresentation.LocallyLogged {
        val session=plan.sessions.singleOrNull { it.id==sessionId }
            ?: throw IllegalArgumentException("Session is missing or ambiguous.")
        return ActivityDetailPresentation.LocallyLogged(
            accountId=plan.owner,title=session.title,kind=session.discipline,
            planId=plan.id,sessionId=session.id,
            performedOn=session.log?.performedOn,recordedAt=session.log?.at,
            status=session.status,minutes=session.log?.minutes,
            distanceKm=session.log?.distanceKm,effort=session.log?.effort,
            sets=session.log?.sets ?: emptyList(),notes=session.log?.notes ?: "",
            linkedActivityId=session.linkedActivityId
        )
    }
}
