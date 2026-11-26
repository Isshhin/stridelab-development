package io.stridelab.privatealpha

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Collections
import java.util.Locale

internal data class M2BEvaluationContext(
    val accountId: String,
    val discipline: AthleteDiscipline,
    val asOf: Instant,
    val zone: ZoneId
) {
    init {
        RecordPolicy.identifier(accountId)
    }

    val localDate get() = asOf.atZone(zone).toLocalDate()
}

internal enum class M2BSurface {
    NON_TECHNICAL_ROAD,
    TREADMILL,
    TECHNICAL_OR_UNKNOWN
}

internal data class M2BRunningSegment(
    val origin: M2BStreamIdentity,
    val start: Instant,
    val end: Instant,
    val horizontalDistance: Meters,
    val riseMeters: Double
)

internal data class M2BStreamIdentity(
    val accountId: String,
    val discipline: AthleteDiscipline,
    val provider: String,
    val recordId: String,
    val externalId: String?,
    val sha256: String?,
    val recordingStart: Instant
) {
    companion object {
        fun of(activity: CanonicalActivity, component: RawSourceComponent) = M2BStreamIdentity(
            activity.accountId, activity.discipline, component.provider.trim().lowercase(Locale.ROOT),
            component.recordId, component.externalId, component.sha256?.lowercase(Locale.ROOT),
            component.startedAtUtc
        )
    }
}

/** Segment data is supplied with its originating stream. No separately computed GAP is accepted. */
internal class M2BRunningStream(
    val activity: CanonicalActivity,
    source: M2ASourceQualifiedAlias,
    val surface: M2BSurface,
    segments: List<M2BRunningSegment>
) {
    val source = M2ASourceQualifiedAlias(source.normalizedProvider, source.externalId)
    val segments: List<M2BRunningSegment> = Collections.unmodifiableList(
        segments.sortedWith(compareBy(M2BRunningSegment::start, M2BRunningSegment::end))
    )

    fun sourceRecord(): RawSourceComponent? = activity.rawComponents.singleOrNull {
        it.provider.trim().lowercase(Locale.ROOT) == source.normalizedProvider &&
            (it.recordId == source.externalId || it.externalId == source.externalId)
    }
}

internal class M2BSourceProvenance private constructor(
    private val aliases: Set<M2ASourceQualifiedAlias>
) : Set<M2ASourceQualifiedAlias> by aliases {
    override fun equals(other: Any?): Boolean = other is Set<*> && aliases == other
    override fun hashCode(): Int = aliases.hashCode()

    companion object {
        fun of(aliases: Collection<M2ASourceQualifiedAlias>) =
            M2BSourceProvenance(Collections.unmodifiableSet(aliases.sorted().toCollection(LinkedHashSet())))
    }
}

internal data class M2BObservedSpeed(
    val canonicalActivityId: String,
    val source: M2ASourceQualifiedAlias,
    val provenance: M2BSourceProvenance,
    val completed: Instant,
    val duration: Seconds,
    val rawSpeed: MetersPerSecond,
    val gradeAdjustedSpeed: MetersPerSecond?
)

internal class Engine15M2BPerformanceLowerEnvelope private constructor(
    val context: M2BEvaluationContext,
    bounds: List<M2BObservedSpeed>,
    elapsedAverages: List<M2BObservedSpeed>
) {
    val bounds: List<M2BObservedSpeed> = Collections.unmodifiableList(bounds)
    val elapsedAverages: List<M2BObservedSpeed> = Collections.unmodifiableList(elapsedAverages)

    fun at(duration: Seconds): M2BObservedSpeed? = bounds.firstOrNull { it.duration == duration }

    companion object {
        fun build(
            context: M2BEvaluationContext,
            streams: List<M2BRunningStream>
        ): Engine15M2BPerformanceLowerEnvelope {
            val eligible = streams.filter { stream ->
                val activity = stream.activity
                activity.accountId == context.accountId && activity.discipline == context.discipline &&
                    context.discipline == AthleteDiscipline.RUN &&
                    activity.duplicateResolutionConfidence in setOf(
                        DuplicateResolutionConfidence.VERIFIED_UNIQUE,
                        DuplicateResolutionConfidence.VERIFIED_EXACT_DUPLICATE,
                        DuplicateResolutionConfidence.VERIFIED_CORROBORATED_MULTI_SOURCE
                    ) &&
                    activity.telemetryQuality.distanceQuality == TelemetryQualityStatus.VALID_OBSERVED &&
                    activity.telemetryQuality.durationQuality == TelemetryQualityStatus.VALID_OBSERVED &&
                    stream.sourceRecord() != null
            }
            // Colliding aliases/fingerprints between separate canonical projections need upstream reconciliation.
            val claims = eligible.groupBy { it.activity.canonicalId }
            val identityClaims = claims.flatMap { (id, group) ->
                group.flatMap { stream ->
                    aliases(stream.activity).map { "source/" + it.canonicalToken() } +
                        stream.activity.rawComponents.mapNotNull { it.sha256?.lowercase(Locale.ROOT) }
                }.distinct().map { it to id }
            }.groupBy({ it.first }, { it.second })
            val collisions = identityClaims.values.filter { it.size > 1 }.flatten().toSet()
            val averages = mutableListOf<M2BObservedSpeed>()
            val observations = mutableListOf<M2BObservedSpeed>()
            for ((id, group) in claims.toSortedMap()) {
                if (id in collisions) continue
                val distinct = group.distinctBy { Triple(it.activity.contentHash, it.source, it.segments to it.surface) }
                val stream = distinct.singleOrNull() ?: continue
                val source = stream.sourceRecord() ?: continue
                val duration = source.rawDurationS ?: continue
                val distance = source.rawDistanceM ?: continue
                if (!duration.isFinite() || duration <= 0 || !distance.isFinite() || distance <= 0) continue
                // Convert fractional seconds without using the process clock or a calendar assumption.
                val wholeSeconds = duration.toLong()
                val nanos = Math.round((duration - wholeSeconds) * 1_000_000_000)
                val end = runCatching { source.startedAtUtc.plusSeconds(wholeSeconds).plusNanos(nanos) }
                    .getOrNull() ?: continue
                if (!fresh(end, context.asOf)) continue
                val provenance = aliases(stream.activity)
                val average = speed(distance, duration) ?: continue
                averages += M2BObservedSpeed(
                    id, stream.source, provenance, end, Seconds.of(duration), average, null
                )
                observeWindow(stream, source, end, provenance, context.asOf)?.let(observations::add)
            }
            val bounds = observations.groupBy(M2BObservedSpeed::duration).toSortedMap().values.map { sameDuration ->
                sameDuration.sortedWith(
                    compareByDescending<M2BObservedSpeed> { it.rawSpeed }
                        .thenByDescending { it.completed }
                        .thenBy { it.canonicalActivityId }
                ).first()
            }
            return Engine15M2BPerformanceLowerEnvelope(context, bounds, averages.toList())
        }

        private fun observeWindow(
            stream: M2BRunningStream,
            source: RawSourceComponent,
            sessionEnd: Instant,
            provenance: M2BSourceProvenance,
            asOf: Instant
        ): M2BObservedSpeed? {
            val first = stream.segments.firstOrNull() ?: return null
            val last = stream.segments.last()
            if (first.start < source.startedAtUtc || last.end > sessionEnd || !fresh(last.end, asOf)) return null
            val expectedOrigin = M2BStreamIdentity.of(stream.activity, source)
            var nextStart = first.start
            var pathDistance = 0.0
            var equivalentDistance = 0.0
            var adjustedAvailable = stream.surface != M2BSurface.TECHNICAL_OR_UNKNOWN
            for (segment in stream.segments) {
                if (segment.origin != expectedOrigin || segment.start != nextStart || segment.end <= segment.start ||
                    segment.horizontalDistance.value <= 0 || !segment.riseMeters.isFinite()
                ) return null
                nextStart = segment.end
                pathDistance += Math.hypot(segment.horizontalDistance.value, segment.riseMeters)
                if (adjustedAvailable) {
                    val adjustment = Engine15M2BIntensityModels.minetti(
                        segment.horizontalDistance, segment.riseMeters, Seconds.of(seconds(segment.start, segment.end))
                    )
                    if (adjustment is M2BGradeAdjustment.Computed) {
                        equivalentDistance += adjustment.adjustedDistance.value
                    } else {
                        adjustedAvailable = false
                    }
                }
            }
            val duration = seconds(first.start, last.end)
            return M2BObservedSpeed(
                stream.activity.canonicalId, stream.source, provenance, last.end, Seconds.of(duration),
                speed(pathDistance, duration) ?: return null,
                if (adjustedAvailable) speed(equivalentDistance, duration) else null
            )
        }

        private fun fresh(completed: Instant, asOf: Instant): Boolean =
            completed <= asOf && Duration.between(completed, asOf) <=
                Duration.ofDays(Engine15M2BPolicyRegistry.ENVELOPE_MAX_AGE_DAYS)

        private fun seconds(start: Instant, end: Instant): Double =
            Duration.between(start, end).let { it.seconds + it.nano / 1_000_000_000.0 }

        private fun speed(distance: Double, duration: Double): MetersPerSecond? =
            (distance / duration).takeIf { it.isFinite() && it > 0 }?.let(MetersPerSecond::of)

        private fun aliases(activity: CanonicalActivity): M2BSourceProvenance =
            M2BSourceProvenance.of(activity.rawComponents.flatMap { component ->
                val provider = component.provider.trim().lowercase(Locale.ROOT)
                listOfNotNull(component.recordId, component.externalId).mapNotNull {
                    runCatching { M2ASourceQualifiedAlias(provider, it) }.getOrNull()
                }
            })
    }
}
