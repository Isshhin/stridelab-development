package io.stridelab.privatealpha

import java.time.Instant
import java.util.Collections
import java.util.Locale

internal enum class M2BTalkTestStage { COMFORTABLE_SENTENCES, SHORT_OR_EQUIVOCAL_SPEECH, UNABLE_TO_SPEAK }
internal enum class M2BEffortDomain { MODERATE, MODERATE_HEAVY_TRANSITION, HEAVY, SEVERE }
internal enum class M2BPhysiologicalModality { RUNNING, CYCLING, SWIMMING, OTHER, UNSPECIFIED }

internal data class M2BEffortCue(
    val domain: M2BEffortDomain,
    val talkTest: M2BTalkTestStage,
    val minimumCr10: Double,
    val maximumCr10: Double
)

internal object M2BEffortDescriptors {
    val recovery = M2BEffortCue(M2BEffortDomain.MODERATE, M2BTalkTestStage.COMFORTABLE_SENTENCES, 1.0, 2.0)
    val easy = M2BEffortCue(M2BEffortDomain.MODERATE, M2BTalkTestStage.COMFORTABLE_SENTENCES, 2.0, 3.0)
    val steady = M2BEffortCue(M2BEffortDomain.MODERATE_HEAVY_TRANSITION,
        M2BTalkTestStage.SHORT_OR_EQUIVOCAL_SPEECH, 4.0, 5.0)
    val tempo = M2BEffortCue(M2BEffortDomain.HEAVY, M2BTalkTestStage.SHORT_OR_EQUIVOCAL_SPEECH, 6.5, 7.0)
    val severe = M2BEffortCue(M2BEffortDomain.SEVERE, M2BTalkTestStage.UNABLE_TO_SPEAK, 8.5, 9.0)
    val all: List<M2BEffortCue> = Collections.unmodifiableList(listOf(recovery, easy, steady, tempo, severe))
}

internal enum class M2BEffortRestriction { WITHHELD, RECOVERY_ONLY, EFFORT_FIRST, STANDARD }

/** A supplied cap is a downstream limit, never a prescribed workout duration. */
internal data class M2BDurationExposureConstraint(
    val evaluatedFor: M2BEvaluationContext,
    val maximumContinuousDuration: Seconds,
    val source: M2ASourceQualifiedAlias
)

internal class M2BEffortGuidance(
    val restriction: M2BEffortRestriction,
    val durationExposureConstraint: M2BDurationExposureConstraint?
) {
    val offeredCues: List<M2BEffortCue> = when (restriction) {
        M2BEffortRestriction.WITHHELD -> emptyList()
        M2BEffortRestriction.RECOVERY_ONLY -> Collections.singletonList(M2BEffortDescriptors.recovery)
        M2BEffortRestriction.EFFORT_FIRST ->
            Collections.unmodifiableList(listOf(M2BEffortDescriptors.recovery, M2BEffortDescriptors.easy))
        M2BEffortRestriction.STANDARD -> Collections.singletonList(M2BEffortDescriptors.easy)
    }
}

internal enum class M2BTrainingPathway {
    COMFORTABLE_AEROBIC, SUCCESSFUL_STRUCTURED, CONTROLLED_PROGRESSION, PACE_HR_RPE_CONVERGENCE, HISTORICAL_CONFIRMATION
}

internal enum class M2BTrainingRestriction {
    NEEDS_INDEPENDENT_EXPOSURES, CONSISTENCY_POLICY_PENDING, ATHLETE_CONFIRMED_PROVISIONAL
}

internal enum class M2BTrainingHeartRate { MISSING, CLEAN_STABLE, UNRELIABLE_OR_DRIFTING }

internal data class M2BTrainingObservation(
    val activity: CanonicalActivity,
    val source: M2ASourceQualifiedAlias,
    val family: M2BRunningReferenceFamily,
    val endedAt: Instant,
    val speed: MetersPerSecond,
    val cr10: Double,
    val talkTest: M2BTalkTestStage,
    val heartRate: M2BTrainingHeartRate,
    val flatOrRolling: Boolean,
    val confounded: Boolean,
    val completedAsIntended: Boolean = false,
    val splitExecutionStable: Boolean = false,
    val matchedIntendedRpe: Boolean = false,
    val declaredControlledProgression: Boolean = false,
    val steadyPaceWithAlignedEffort: Boolean = false,
    val sourceDistanceQuality: TelemetryQualityStatus = TelemetryQualityStatus.MISSING_TELEMETRY,
    val sourceHrQuality: TelemetryQualityStatus? = null
)

internal data class M2BDescriptivePaceDistribution(
    val q1SecondsPerKm: Double,
    val medianSecondsPerKm: Double,
    val q3SecondsPerKm: Double
) {
    val interquartileRangeSecondsPerKm: Double get() = q3SecondsPerKm - q1SecondsPerKm
}

internal data class M2BTrainingReferencePoint(
    val canonicalActivityId: String?,
    val source: M2ASourceQualifiedAlias,
    val observedAt: Instant,
    val speed: MetersPerSecond
)

internal class M2BTrainingReference private constructor(
    val pathway: M2BTrainingPathway,
    val family: M2BRunningReferenceFamily,
    val distribution: M2BDescriptivePaceDistribution,
    val restriction: M2BTrainingRestriction,
    val canonicalActivityIds: Set<String>,
    val sourceEvidence: M2BSourceProvenance,
    val observations: List<M2BTrainingReferencePoint>
) {
    companion object {
        fun historical(confirmed: M2BConfirmedTrainingReference): M2BTrainingReference? {
            val pace = confirmed.observedSpeed.toPaceSecondsPerKm()
            if (!pace.isFinite()) return null
            return M2BTrainingReference(M2BTrainingPathway.HISTORICAL_CONFIRMATION, confirmed.family,
                M2BDescriptivePaceDistribution(pace, pace, pace), M2BTrainingRestriction.ATHLETE_CONFIRMED_PROVISIONAL,
                emptySet(), M2BSourceProvenance.of(listOf(confirmed.source)),
                Collections.singletonList(M2BTrainingReferencePoint(null,
                    confirmed.source, confirmed.confirmedAt, confirmed.observedSpeed)))
        }

        fun descriptive(
            pathway: M2BTrainingPathway,
            family: M2BRunningReferenceFamily,
            evidence: List<M2BTrainingObservation>
        ): M2BTrainingReference? {
            if (evidence.isEmpty()) return null
            val paces = evidence.map { it.speed.toPaceSecondsPerKm() }.sorted()
            if (paces.any { !it.isFinite() }) return null
            // Linear interpolation on zero-based rank, a descriptive convention without an eligibility threshold.
            fun quantile(fraction: Double): Double {
                val position = fraction * (paces.size - 1)
                val low = position.toInt()
                return paces[low] + (paces[minOf(low + 1, paces.lastIndex)] - paces[low]) * (position - low)
            }
            val ids = evidence.map { it.activity.canonicalId }.toSortedSet()
            return M2BTrainingReference(pathway, family,
                M2BDescriptivePaceDistribution(quantile(0.25), quantile(0.5), quantile(0.75)),
                if (ids.size <= 1) M2BTrainingRestriction.NEEDS_INDEPENDENT_EXPOSURES
                else M2BTrainingRestriction.CONSISTENCY_POLICY_PENDING,
                Collections.unmodifiableSet(ids),
                M2BSourceProvenance.of(evidence.flatMap(::aliases)),
                Collections.unmodifiableList(evidence.map {
                    M2BTrainingReferencePoint(it.activity.canonicalId, it.source, it.endedAt, it.speed)
                }))
        }
    }
}

internal object Engine15M2BTrainingEvidence {
    fun extract(
        context: M2BEvaluationContext,
        observations: List<M2BTrainingObservation>,
        streams: List<M2BRunningStream>,
        hrQuarantined: Boolean
    ): List<M2BTrainingReference> {
        if (context.discipline != AthleteDiscipline.RUN || context.physiologicalModality != M2BPhysiologicalModality.RUNNING) {
            return emptyList()
        }
        val eligible = observations.filter {
            val activity = it.activity
            activity.accountId == context.accountId && activity.discipline == context.discipline &&
                activity.duplicateResolutionConfidence in setOf(
                    DuplicateResolutionConfidence.VERIFIED_UNIQUE,
                    DuplicateResolutionConfidence.VERIFIED_EXACT_DUPLICATE,
                    DuplicateResolutionConfidence.VERIFIED_CORROBORATED_MULTI_SOURCE
                ) && activity.telemetryQuality.durationQuality == TelemetryQualityStatus.VALID_OBSERVED &&
                it.sourceDistanceQuality == TelemetryQualityStatus.VALID_OBSERVED &&
                !it.confounded && it.cr10 in 0.0..10.0 && completedSourceContains(it, context.asOf)
        }.distinct()
        val identityOwners = eligible.flatMap { observation ->
            (aliases(observation).map { it.canonicalToken() } +
                observation.activity.rawComponents.mapNotNull { it.sha256?.lowercase(Locale.ROOT) }).map {
                it to observation.activity.canonicalId
            }
        }.groupBy({ it.first }, { it.second })
        val collisions = identityOwners.values.filter { it.distinct().size > 1 }.flatten().toSet()
        val clean = eligible.filterNot { it.activity.canonicalId in collisions }
        val recordingsByActivity = streams.mapNotNull { stream ->
            val recording = stream.recording ?: return@mapNotNull null
            val source = stream.sourceRecord() ?: return@mapNotNull null
            if (recording.origin == M2BStreamIdentity.of(stream.activity, source)) {
                stream.activity.canonicalId to recording
            } else null
        }.groupBy({ it.first }, { it.second })
        val usableHr = clean.filterTo(mutableSetOf()) { row ->
            !hrQuarantined && hasUsableHr(row, recordingsByActivity[row.activity.canonicalId].orEmpty())
        }
        val progressionIds = clean.filter { it.declaredControlledProgression && it.flatOrRolling }
            .groupBy { it.activity.canonicalId to it.source.canonicalToken() }.filterValues { rows ->
                val stages = rows.sortedBy { it.endedAt }
                stages.map { it.family } == listOf(M2BRunningReferenceFamily.EASY,
                    M2BRunningReferenceFamily.STEADY, M2BRunningReferenceFamily.TEMPO) &&
                    stages.zipWithNext().all { (a, b) ->
                        a.endedAt < b.endedAt && a.cr10 < b.cr10 && a.speed < b.speed &&
                            a.talkTest.ordinal <= b.talkTest.ordinal
                    } && stages.none { it.talkTest == M2BTalkTestStage.UNABLE_TO_SPEAK } &&
                    stages.zip(listOf(M2BEffortDescriptors.easy, M2BEffortDescriptors.steady, M2BEffortDescriptors.tempo))
                        .all { (row, cue) -> row.cr10 in cue.minimumCr10..cue.maximumCr10 }
            }.keys
        val byPathway = mapOf(
            M2BTrainingPathway.COMFORTABLE_AEROBIC to clean.filter {
                !it.declaredControlledProgression && it.family == M2BRunningReferenceFamily.EASY &&
                    it.flatOrRolling && it.cr10 in 2.0..3.0 &&
                    it.talkTest == M2BTalkTestStage.COMFORTABLE_SENTENCES &&
                    (it !in usableHr || it.heartRate != M2BTrainingHeartRate.UNRELIABLE_OR_DRIFTING)
            },
            M2BTrainingPathway.SUCCESSFUL_STRUCTURED to clean.filter {
                it.family in setOf(M2BRunningReferenceFamily.TEMPO, M2BRunningReferenceFamily.CRUISE,
                    M2BRunningReferenceFamily.INTERVALS) && it.completedAsIntended &&
                    it.splitExecutionStable && it.matchedIntendedRpe
            },
            M2BTrainingPathway.CONTROLLED_PROGRESSION to clean.filter {
                it.declaredControlledProgression &&
                    (it.activity.canonicalId to it.source.canonicalToken()) in progressionIds
            },
            M2BTrainingPathway.PACE_HR_RPE_CONVERGENCE to clean.filter {
                it.family in setOf(M2BRunningReferenceFamily.EASY, M2BRunningReferenceFamily.STEADY) &&
                    it.steadyPaceWithAlignedEffort && it.heartRate == M2BTrainingHeartRate.CLEAN_STABLE && it in usableHr
            }
        )
        return Collections.unmodifiableList(byPathway.flatMap { (pathway, evidence) ->
            evidence.groupBy { it.family }.toSortedMap().mapNotNull { (family, rows) ->
                val unambiguous = rows.groupBy { it.activity.canonicalId }.toSortedMap().values.mapNotNull {
                    it.singleOrNull()
                }
                M2BTrainingReference.descriptive(pathway, family, unambiguous)
            }
        })
    }

    private fun hasUsableHr(row: M2BTrainingObservation, recordings: List<M2BRecordingWindow>): Boolean {
        if (row.activity.telemetryQuality.hrQuality != TelemetryQualityStatus.VALID_OBSERVED) return false
        val sourceStatuses = recordings.filter {
            val origin = it.origin
            origin.provider == row.source.normalizedProvider &&
                (origin.recordId == row.source.externalId || origin.externalId == row.source.externalId)
        }.map { it.sourceQuality.hrQuality } + listOfNotNull(row.sourceHrQuality)
        // A merged activity's primary HR quality cannot establish a different component's quality.
        return (sourceStatuses.isNotEmpty() || row.activity.rawComponents.size == 1) &&
            sourceStatuses.all { it == TelemetryQualityStatus.VALID_OBSERVED }
    }
}

private fun completedSourceContains(value: M2BTrainingObservation, asOf: Instant): Boolean {
    val component = value.activity.rawComponents.singleOrNull {
        it.provider.trim().lowercase(Locale.ROOT) == value.source.normalizedProvider &&
            (it.recordId == value.source.externalId || it.externalId == value.source.externalId)
    } ?: return false
    val end = m2bSessionEnd(component) ?: return false
    return end <= asOf && value.endedAt > component.startedAtUtc && value.endedAt <= end &&
        (value.declaredControlledProgression || value.endedAt == end)
}

private fun aliases(observation: M2BTrainingObservation): List<M2ASourceQualifiedAlias> =
    observation.activity.rawComponents.flatMap { component ->
        listOfNotNull(component.recordId, component.externalId).mapNotNull { id ->
            runCatching { M2ASourceQualifiedAlias(component.provider.trim().lowercase(Locale.ROOT), id) }.getOrNull()
        }
    }
