package io.stridelab.privatealpha

import java.time.Instant

/**
 * Phase 40 provider-facing domain contracts only. There is NO real Garmin
 * authorization or Garmin API use here; fixture records must never be offered
 * as connected account data.
 */
internal data class WearableActivityInput(
    val accountId: String,
    val provider: String,
    val externalId: String,
    val activityType: String,
    val startedAt: Instant,
    val durationSeconds: Double?,
    val distanceMeters: Double?,
    val title: String? = null,
    val movingDurationSeconds: Double? = null,
    val averageHeartRate: Double? = null,
    /** Optional Garmin source averageSpeed, meters/second. Not a computed moving pace. */
    val averageSpeedMetersPerSecond:Double? = null
) {
    init {
        require(accountId.isNotBlank() && externalId.isNotBlank() && provider.isNotBlank())
        require(durationSeconds==null ||
            (durationSeconds.isFinite() && durationSeconds>=0))
        require(distanceMeters==null ||
            (distanceMeters.isFinite() && distanceMeters>=0))
        require(movingDurationSeconds==null ||
            (movingDurationSeconds.isFinite() && movingDurationSeconds>=0))
        require(averageHeartRate==null ||
            (averageHeartRate.isFinite() && averageHeartRate>0))
        require(averageSpeedMetersPerSecond==null ||
            (averageSpeedMetersPerSecond.isFinite() && averageSpeedMetersPerSecond>0))
    }
}

internal data class WearableWellnessInput(
    val accountId: String,
    val provider: String,
    val externalId: String,
    val kind: String,
    val value: Double,
    val unit: String,
    val at: Instant
)

internal enum class NormalizedWellnessKind {
    RESTING_HEART_RATE, HRV_RMSSD, HRV_SDNN, SLEEP_DURATION, STEPS
}

internal data class NormalizedWellness(
    val source: ImportedSourceIdentity,
    val kind: NormalizedWellnessKind,
    val value: Double,
    val unit: String,
    val at: Instant
)

internal object WearableWellnessNormalizer {
    /**
     * Explicit units and HRV method required; no inferred conversions between
     * RMSSD, SDNN, proprietary stress, recovery, or clinical readiness.
     */
    fun normalize(input:WearableWellnessInput):NormalizedWellness? {
        require(input.accountId.isNotBlank() && input.provider.isNotBlank() &&
            input.externalId.isNotBlank())
        require(input.value.isFinite()) { "Invalid provider wellness value" }
        val kind=when(input.kind.lowercase()) {
            "resting_hr" -> NormalizedWellnessKind.RESTING_HEART_RATE
            "hrv_rmssd" -> NormalizedWellnessKind.HRV_RMSSD
            "hrv_sdnn" -> NormalizedWellnessKind.HRV_SDNN
            "sleep_duration" -> NormalizedWellnessKind.SLEEP_DURATION
            "steps" -> NormalizedWellnessKind.STEPS
            else -> return null
        }
        val expected=when(kind) {
            NormalizedWellnessKind.RESTING_HEART_RATE -> "bpm"
            NormalizedWellnessKind.HRV_RMSSD,
            NormalizedWellnessKind.HRV_SDNN -> "ms"
            NormalizedWellnessKind.SLEEP_DURATION -> "hours"
            NormalizedWellnessKind.STEPS -> "steps"
        }
        require(input.unit==expected) { "Unsupported source units; do not guess." }
        require(input.value>=0.0)
        when(kind) {
            NormalizedWellnessKind.RESTING_HEART_RATE,
            NormalizedWellnessKind.HRV_RMSSD,
            NormalizedWellnessKind.HRV_SDNN -> require(input.value>0.0)
            NormalizedWellnessKind.SLEEP_DURATION -> require(input.value<=24.0)
            NormalizedWellnessKind.STEPS -> require(input.value==input.value.toLong().toDouble())
        }
        return NormalizedWellness(
            ImportedSourceIdentity(input.accountId,input.provider,input.externalId),
            kind,input.value,expected,input.at
        )
    }
}

internal data class WearablePage<T>(
    val accountId: String, val records: List<T>,
    val nextCursor: String?
)

internal interface WearableImportProvider {
    fun activities(accountId:String, cursor:String?, limit:Int):WearablePage<WearableActivityInput>
    fun wellness(accountId:String, cursor:String?, limit:Int):WearablePage<WearableWellnessInput>
}

/** Pure fixture adapter; deliberately not registered in TrainingRepository or UI. */
internal class GarminFixtureProvider(
    private val activitiesByAccount: Map<String,List<WearableActivityInput>>,
    private val wellnessByAccount: Map<String,List<WearableWellnessInput>>
) : WearableImportProvider {
    private fun <T> page(account:String,cursor:String?,limit:Int,records:List<T>):WearablePage<T> {
        require(limit in 1..200)
        val offset=if(cursor==null)0 else cursor.toIntOrNull()
            ?: throw IllegalArgumentException("Malformed fixture cursor")
        require(offset>=0 && offset<=records.size)
        val batch=records.drop(offset).take(limit)
        val next=(offset+batch.size).takeIf { it<records.size }?.toString()
        return WearablePage(account,batch,next)
    }

    override fun activities(accountId:String,cursor:String?,limit:Int):WearablePage<WearableActivityInput> {
        require(accountId.isNotBlank())
        val records=activitiesByAccount[accountId].orEmpty()
        require(records.all { it.accountId==accountId && it.provider=="garmin" })
        return page(accountId,cursor,limit,records)
    }

    override fun wellness(accountId:String,cursor:String?,limit:Int):WearablePage<WearableWellnessInput> {
        require(accountId.isNotBlank())
        val records=wellnessByAccount[accountId].orEmpty()
        require(records.all { it.accountId==accountId && it.provider=="garmin" })
        return page(accountId,cursor,limit,records)
    }
}
