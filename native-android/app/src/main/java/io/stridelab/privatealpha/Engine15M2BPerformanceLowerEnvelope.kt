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
    val zone: ZoneId,
    val physiologicalModality: M2BPhysiologicalModality =
        if (discipline == AthleteDiscipline.RUN) M2BPhysiologicalModality.RUNNING
        else M2BPhysiologicalModality.UNSPECIFIED
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

internal enum class M2BEffortClassification {
    MAXIMAL_RACE_OR_TT, NEAR_MAXIMAL_QUALITY, SUBMAXIMAL_EASY_OR_STEADY, UNKNOWN
}

internal enum class M2BSourceClass {
    CERTIFIED_COURSE_TIMING, CHEST_ECG, WATCH_FIT_DIRECT, HEALTH_CONNECT_WATCH, OPTICAL_PPG_STEADY,
    PHONE_GPS, OPTICAL_PPG_DYNAMIC, VERIFIED_EXTERNAL_LOAD, MANUAL_WORKOUT_LOG,
    SELF_REPORT_QUANTITATIVE, SELF_REPORT_QUALITATIVE, AGE_EQUATION_PRIOR, UNKNOWN
}

internal enum class M2BContinuityEvidence { ACTIVE_CONTIGUOUS, PAUSE, UNKNOWN }
internal enum class M2BDistanceSemantics { HORIZONTAL, SLOPE_PATH }
internal enum class M2BObservationBasis { ELAPSED_SUMMARY, RECONSTRUCTED_DESCRIPTION, RECORDING_DERIVED_DESCRIPTION }

/** Direct recording values. Continuity refers to the interval ending at this sample. */
internal data class M2BRecordingSample(
    val at: Instant,
    val cumulativeHorizontalDistance: Meters,
    val elevationMeters: Double,
    val continuity: M2BContinuityEvidence
)

internal class M2BRecordingWindow(
    val origin: M2BStreamIdentity,
    val distanceSemantics: M2BDistanceSemantics,
    val sourceQuality: TelemetryQualityAssessment,
    samples: List<M2BRecordingSample>,
    val distanceTolerance: Meters? = null
) {
    val samples: List<M2BRecordingSample> = Collections.unmodifiableList(ArrayList(samples))

    fun deriveSegments(): List<M2BRunningSegment>? {
        if (samples.size < 2 || samples.any { !it.elevationMeters.isFinite() }) return null
        return samples.zipWithNext().map { (a, b) ->
            if (b.at <= a.at || b.continuity != M2BContinuityEvidence.ACTIVE_CONTIGUOUS) return null
            val horizontal = b.cumulativeHorizontalDistance.value - a.cumulativeHorizontalDistance.value
            val rise = b.elevationMeters - a.elevationMeters
            if (horizontal <= 0 || !rise.isFinite()) return null
            M2BRunningSegment(origin, a.at, b.at, Meters.of(horizontal), rise)
        }
    }
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
    segments: List<M2BRunningSegment>,
    val recording: M2BRecordingWindow? = null,
    val sensorTolerance: MetersPerSecond? = null,
    val effort: M2BEffortClassification = M2BEffortClassification.UNKNOWN,
    val sourceClass: M2BSourceClass = M2BSourceClass.UNKNOWN
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

internal data class M2BDownhillExposure(
    val start: Instant,
    val end: Instant,
    val elevationLoss: Meters,
    val horizontalDistance: Meters,
    val pathDistance: Meters,
    val duration: Seconds
)

internal enum class M2BGAPRejectionReason {
    GAP_OUT_OF_DOMAIN, ECCENTRIC_DOMINATED_QUARANTINE, UNSUPPORTED_SURFACE, INVALID_NUMERIC_INPUT
}

internal data class M2BGAPRejection(val start: Instant, val end: Instant, val reason: M2BGAPRejectionReason)

internal data class M2BObservedSpeed(
    val canonicalActivityId: String,
    val source: M2ASourceQualifiedAlias,
    val provenance: M2BSourceProvenance,
    val completed: Instant,
    val duration: Seconds,
    val rawSpeed: MetersPerSecond,
    val gradeAdjustedSpeed: MetersPerSecond?,
    val expiresAt: Instant,
    val sensorTolerance: MetersPerSecond?,
    val effort: M2BEffortClassification,
    val sourceClass: M2BSourceClass,
    val basis: M2BObservationBasis,
    val downhillExposure: List<M2BDownhillExposure> = emptyList(),
    val gapRejections: List<M2BGAPRejection> = emptyList()
) {
    val verifiedContinuousPerformance: Boolean get() = false
}

internal data class M2BExactDurationReference(
    val duration: Seconds,
    val raw: M2BObservedSpeed,
    val gradeAdjusted: M2BObservedSpeed?
)

internal data class M2BRecordingTrace(
    val canonicalActivityId: String,
    val window: M2BRecordingWindow
)

internal class Engine15M2BPerformanceLowerEnvelope private constructor(
    val context: M2BEvaluationContext,
    observations: List<M2BObservedSpeed>,
    elapsedAverages: List<M2BObservedSpeed>,
    recordingTraces: List<M2BRecordingTrace>
) {
    val recordingTraces: List<M2BRecordingTrace> = Collections.unmodifiableList(recordingTraces)
    val exactObservations: List<M2BObservedSpeed> = Collections.unmodifiableList(
        observations.sortedWith(compareBy(M2BObservedSpeed::duration, M2BObservedSpeed::canonicalActivityId))
    )
    val recordingDerivedObservations: List<M2BObservedSpeed> = Collections.unmodifiableList(
        exactObservations.filter { it.basis == M2BObservationBasis.RECORDING_DERIVED_DESCRIPTION }
    )
    val exactReferences: List<M2BExactDurationReference> = Collections.unmodifiableList(
        exactObservations.groupBy(M2BObservedSpeed::duration).values.map { group ->
            val raw = group.minWith(speedOrder { it.rawSpeed })
            val gap = group.filter { it.gradeAdjustedSpeed != null }.minWithOrNull(speedOrder { it.gradeAdjustedSpeed!! })
            M2BExactDurationReference(raw.duration, raw, gap)
        }
    )
    val rawFrontier: List<M2BObservedSpeed> = frontier(exactReferences.map { it.raw }) { it.rawSpeed }
    val gapFrontier: List<M2BObservedSpeed> =
        frontier(exactReferences.mapNotNull { it.gradeAdjusted }) { it.gradeAdjustedSpeed!! }
    val bounds: List<M2BObservedSpeed> = Collections.unmodifiableList(exactReferences.map { it.raw })
    val elapsedAverages: List<M2BObservedSpeed> = Collections.unmodifiableList(elapsedAverages)

    fun at(duration: Seconds): M2BObservedSpeed? = bounds.firstOrNull { it.duration == duration }

    fun exactAt(duration: Seconds): M2BExactDurationReference? = exactReferences.firstOrNull { it.duration == duration }

    companion object {
        private fun speedOrder(speed: (M2BObservedSpeed) -> MetersPerSecond): Comparator<M2BObservedSpeed> =
            compareByDescending(speed).thenByDescending { it.completed }.thenBy { it.canonicalActivityId }

        private fun frontier(
            exact: List<M2BObservedSpeed>,
            speed: (M2BObservedSpeed) -> MetersPerSecond
        ): List<M2BObservedSpeed> {
            // A longer observation can dominate on the frontier without proving an exact shorter one.
            var bestLonger: MetersPerSecond? = null
            val retained = exact.asReversed().filter {
                val current = speed(it)
                val longer = bestLonger
                if (longer != null && longer >= current) false else {
                    bestLonger = current
                    true
                }
            }
            return Collections.unmodifiableList(retained.asReversed())
        }

        fun build(
            context: M2BEvaluationContext,
            streams: List<M2BRunningStream>
        ): Engine15M2BPerformanceLowerEnvelope {
            val scoped = streams.filter { stream ->
                val activity = stream.activity
                activity.accountId == context.accountId && activity.discipline == context.discipline &&
                    context.discipline == AthleteDiscipline.RUN &&
                    context.physiologicalModality == M2BPhysiologicalModality.RUNNING &&
                    stream.sourceRecord()?.let {
                        val end = m2bSessionEnd(it)
                        end != null && fresh(end, context.asOf) &&
                            (stream.recording == null || stream.recording.origin == M2BStreamIdentity.of(activity, it) &&
                                stream.recording.samples.all { sample -> sample.at in it.startedAtUtc..end })
                    } == true
            }
            val traces = scoped.mapNotNull {
                it.recording?.let { window -> M2BRecordingTrace(it.activity.canonicalId, window) }
            }.distinctBy(::m2bRecordingTraceKey).sortedBy(::m2bRecordingTraceKey)
            val eligible = scoped.filter { stream ->
                val activity = stream.activity
                activity.duplicateResolutionConfidence in setOf(
                        DuplicateResolutionConfidence.VERIFIED_UNIQUE,
                        DuplicateResolutionConfidence.VERIFIED_EXACT_DUPLICATE,
                        DuplicateResolutionConfidence.VERIFIED_CORROBORATED_MULTI_SOURCE
                    )
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
                val distinct = group.distinctBy {
                    listOf(it.activity.contentHash, it.source, it.segments, it.surface, it.recording?.origin,
                        it.recording?.samples, it.recording?.sourceQuality, it.recording?.distanceSemantics,
                        it.recording?.distanceTolerance, it.sensorTolerance, it.effort, it.sourceClass)
                }
                val stream = distinct.singleOrNull() ?: continue
                val source = stream.sourceRecord() ?: continue
                val duration = source.rawDurationS ?: continue
                val distance = source.rawDistanceM ?: continue
                if (!duration.isFinite() || duration <= 0 || !distance.isFinite() || distance <= 0) continue
                val end = m2bSessionEnd(source) ?: continue
                val provenance = aliases(stream.activity)
                val average = speed(distance, duration) ?: continue
                averages += M2BObservedSpeed(
                    id, stream.source, provenance, end, Seconds.of(duration), average, null, expires(end),
                    stream.sensorTolerance, stream.effort, stream.sourceClass, M2BObservationBasis.ELAPSED_SUMMARY
                )
                val quality = stream.recording?.sourceQuality ?: stream.activity.telemetryQuality
                if (quality.distanceQuality == TelemetryQualityStatus.VALID_OBSERVED &&
                    quality.durationQuality == TelemetryQualityStatus.VALID_OBSERVED
                ) {
                    observeWindow(stream, source, end, provenance, context.asOf)?.let(observations::add)
                }
            }
            return Engine15M2BPerformanceLowerEnvelope(context, observations, averages.toList(), traces)
        }

        private fun observeWindow(
            stream: M2BRunningStream,
            source: RawSourceComponent,
            sessionEnd: Instant,
            provenance: M2BSourceProvenance,
            asOf: Instant
        ): M2BObservedSpeed? {
            val expectedOrigin = M2BStreamIdentity.of(stream.activity, source)
            val recording = stream.recording
            val segments = if (recording == null) stream.segments else {
                if (recording.origin != expectedOrigin || stream.segments.isNotEmpty()) return null
                recording.deriveSegments() ?: return null
            }
            val first = segments.firstOrNull() ?: return null
            val last = segments.last()
            if (first.start < source.startedAtUtc || last.end > sessionEnd || !fresh(last.end, asOf)) return null
            var nextStart = first.start
            var pathDistance = 0.0
            var equivalentDistance = 0.0
            val rejections = mutableListOf<M2BGAPRejection>()
            val downhill = mutableListOf<M2BDownhillExposure>()
            if (stream.surface == M2BSurface.TECHNICAL_OR_UNKNOWN) {
                rejections += M2BGAPRejection(first.start, last.end, M2BGAPRejectionReason.UNSUPPORTED_SURFACE)
            }
            for (segment in segments) {
                if (segment.origin != expectedOrigin || segment.start != nextStart || segment.end <= segment.start ||
                    segment.horizontalDistance.value <= 0 || !segment.riseMeters.isFinite()
                ) return null
                nextStart = segment.end
                val length = Math.hypot(segment.horizontalDistance.value, segment.riseMeters)
                if (!length.isFinite()) return null
                pathDistance += length
                val duration = Seconds.of(seconds(segment.start, segment.end))
                if (segment.riseMeters < 0.0) {
                    downhill += M2BDownhillExposure(segment.start, segment.end, Meters.of(-segment.riseMeters),
                        segment.horizontalDistance, Meters.of(length), duration)
                }
                when (val adjustment = Engine15M2BIntensityModels.minetti(
                    segment.horizontalDistance, segment.riseMeters, duration
                )) {
                    is M2BGradeAdjustment.Computed -> equivalentDistance += adjustment.adjustedDistance.value
                    is M2BGradeAdjustment.Abstained -> rejections += M2BGAPRejection(segment.start, segment.end,
                        when (adjustment) {
                            M2BGradeAdjustment.Abstained.OUT_OF_DOMAIN -> M2BGAPRejectionReason.GAP_OUT_OF_DOMAIN
                            M2BGradeAdjustment.Abstained.ECCENTRIC_DOMINATED ->
                                M2BGAPRejectionReason.ECCENTRIC_DOMINATED_QUARANTINE
                            M2BGradeAdjustment.Abstained.INVALID_SEGMENT -> M2BGAPRejectionReason.INVALID_NUMERIC_INPUT
                        })
                }
            }
            if (recording != null) {
                val observedDistance = when (recording.distanceSemantics) {
                    M2BDistanceSemantics.HORIZONTAL -> segments.sumOf { it.horizontalDistance.value }
                    M2BDistanceSemantics.SLOPE_PATH -> pathDistance
                }
                val recordedDistance = source.rawDistanceM ?: return null
                val difference = observedDistance - recordedDistance
                val tolerance = recording.distanceTolerance?.value ?: 0.0
                if (!difference.isFinite() || difference > tolerance ||
                    recording.samples.last().cumulativeHorizontalDistance.value - recordedDistance > tolerance ||
                    (first.start == source.startedAtUtc && recording.samples.first().cumulativeHorizontalDistance.value > tolerance) ||
                    (first.start == source.startedAtUtc && last.end == sessionEnd && difference < -tolerance)
                ) return null
            }
            val duration = seconds(first.start, last.end)
            val adjustedSpeed = if (rejections.isEmpty()) speed(equivalentDistance, duration) else null
            if (adjustedSpeed == null && rejections.isEmpty()) {
                rejections += M2BGAPRejection(first.start, last.end, M2BGAPRejectionReason.INVALID_NUMERIC_INPUT)
            }
            return M2BObservedSpeed(
                stream.activity.canonicalId, stream.source, provenance, last.end, Seconds.of(duration),
                speed(pathDistance, duration) ?: return null,
                adjustedSpeed, expires(last.end),
                stream.sensorTolerance, stream.effort, stream.sourceClass,
                if (recording == null) M2BObservationBasis.RECONSTRUCTED_DESCRIPTION
                else M2BObservationBasis.RECORDING_DERIVED_DESCRIPTION,
                Collections.unmodifiableList(downhill), Collections.unmodifiableList(rejections)
            )
        }

        private fun fresh(completed: Instant, asOf: Instant): Boolean =
            completed <= asOf && Duration.between(completed, asOf) <=
                Duration.ofDays(Engine15M2BPolicyRegistry.ENVELOPE_MAX_AGE_DAYS)

        private fun expires(completed: Instant): Instant = runCatching {
            completed.plus(Duration.ofDays(Engine15M2BPolicyRegistry.ENVELOPE_MAX_AGE_DAYS))
        }.getOrDefault(Instant.MAX)

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

internal fun m2bSessionEnd(source: RawSourceComponent): Instant? {
    val duration = source.rawDurationS ?: return null
    if (!duration.isFinite() || duration <= 0.0) return null
    val wholeSeconds = duration.toLong()
    val nanos = Math.round((duration - wholeSeconds) * 1_000_000_000)
    return runCatching { source.startedAtUtc.plusSeconds(wholeSeconds).plusNanos(nanos) }.getOrNull()
}
