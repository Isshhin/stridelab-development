package io.stridelab.privatealpha

import java.time.Instant

/**
 * Authorized provider boundary. No Garmin endpoints or credentials are implemented here.
 * Caller must obtain explicit access and supply genuine documented provider data.
 */
internal data class Engine2ProviderActivity(
    val sourceId: String,
    val sport: Engine2Discipline,
    val startedAt: Instant,
    val distanceMeters: Double? = null,
    val durationSeconds: Double? = null,
    val averageHeartRateBpm: Double? = null
)

internal data class Engine2ProviderPage(
    val provider: String,
    val activities: List<Engine2ProviderActivity>,
    val nextCursor: String?,
    val fetchedAt: Instant
)

internal interface Engine2AuthorizedProviderPort {
    val providerName: String
    /** Network/cache errors must propagate; never replace failure with an empty activity history. */
    fun readPage(cursor: String?, maxItems: Int): Engine2ProviderPage
}

/** Converts known source units to internal units; unknown fields remain null/omitted. */
internal object Engine2ProviderNormalizer {
    fun normalize(
        accountId: String,
        page: Engine2ProviderPage,
        now: Instant
    ): List<Engine2IncomingWorkout> {
        require(accountId.isNotBlank() && page.provider.isNotBlank())
        require(!page.fetchedAt.isAfter(now))
        return page.activities.map { raw ->
            require(raw.sourceId.isNotBlank())
            require(!raw.startedAt.isAfter(page.fetchedAt))
            val source = Engine2Source(accountId, page.provider,
                raw.sourceId, raw.startedAt, page.fetchedAt)
            val metrics = mutableListOf<Engine2RawMetric>()
            raw.distanceMeters?.let {
                require(it.isFinite() && it >= 0.0)
                metrics += Engine2RawMetric(Engine2Metric.DISTANCE_KM, it / 1000.0)
            }
            raw.durationSeconds?.let {
                require(it.isFinite() && it >= 0.0)
                metrics += Engine2RawMetric(Engine2Metric.DURATION_SECONDS, it)
            }
            raw.averageHeartRateBpm?.let {
                require(it.isFinite() && it > 0.0 && it <= 250.0)
                metrics += Engine2RawMetric(Engine2Metric.AVERAGE_HR_BPM, it)
            }
            Engine2IncomingWorkout(
                id = "source:" + page.provider + ":" + raw.sourceId,
                discipline = raw.sport, occurredAt = raw.startedAt,
                status = Engine2RecordStatus.CONFIRMED, source = source,
                measurements = metrics
            )
        }
    }
}
