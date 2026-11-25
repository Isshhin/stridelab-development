package io.stridelab.privatealpha

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal enum class CanonicalDeduplicationStatus {
    SINGLE_SOURCE,
    EXACT_DUPLICATE_COLLAPSED,
    VERIFIED_MULTI_SOURCE_DUPLICATE,
    AMBIGUOUS_DUPLICATE_CANDIDATE,
    POTENTIAL_SPLIT_RECORDING
}

internal enum class DuplicateResolutionConfidence {
    VERIFIED_UNIQUE,
    VERIFIED_EXACT_DUPLICATE,
    VERIFIED_CORROBORATED_MULTI_SOURCE,
    UNRESOLVED_AMBIGUOUS_OVERLAP,
    UNRESOLVED_SPLIT_CANDIDATE,
    UNRESOLVED_SOURCE_ID_COLLISION
}

internal enum class WorkloadAggregationEligibility {
    ELIGIBLE_VERIFIED_OBSERVED,
    AMBIGUOUS_BOUNDED_ONLY,
    BLOCKED_COLLISION
}

internal enum class CalibrationEligibility {
    ELIGIBLE_OBSERVED,
    ELIGIBLE_NON_HR_ONLY,
    INELIGIBLE_UNRESOLVED_AMBIGUITY,
    INELIGIBLE_INSUFFICIENT_QUALITY
}

internal enum class ActivityAmbiguityKind {
    AMBIGUOUS_OVERLAPPING_DUPLICATE,
    POTENTIAL_SPLIT_RECORDING,
    SOURCE_IDENTIFIER_COLLISION
}

internal data class ActivityAmbiguityNotice(
    val kind: ActivityAmbiguityKind,
    val relatedRawRecordId: String,
    val relatedSource: String,
    val reasonCode: String
) : Comparable<ActivityAmbiguityNotice> {
    init {
        RecordPolicy.identifier(relatedRawRecordId)
        require(relatedSource.isNotBlank() && relatedSource.length <= 128) {
            "relatedSource must be non-blank and <= 128 chars"
        }
        RecordPolicy.identifier(reasonCode)
    }

    fun canonicalRepresentation(): String =
        "kind=${kind.name}|relId=${AthleteStateSnapshot.canonicalSegment(relatedRawRecordId)}|" +
            "relSrc=${AthleteStateSnapshot.canonicalSegment(relatedSource)}|" +
            "reason=${AthleteStateSnapshot.canonicalSegment(reasonCode)}"

    override fun compareTo(other: ActivityAmbiguityNotice): Int =
        compareBy<ActivityAmbiguityNotice> { it.kind.name }
            .thenBy { it.relatedSource }
            .thenBy { it.relatedRawRecordId }
            .thenBy { it.reasonCode }
            .compare(this, other)
}

internal data class RawSourceComponent(
    val recordId: String,
    val provider: String,
    val externalId: String?,
    val sha256: String?,
    val startedAtUtc: Instant,
    val rawActivityType: String,
    val rawTitle: String,
    val rawDistanceM: Double?,
    val rawDurationS: Double?,
    val rawMovingTimeS: Double?,
    val rawRecordedPaceSecPerKm: Double?,
    val rawAverageHrBpm: Double?,
    val rawMaximumHrBpm: Double?,
    val rawElevationM: Double?
) : Comparable<RawSourceComponent> {
    init {
        RecordPolicy.identifier(recordId)
        require(provider.isNotBlank() && provider.length <= 128)
        require(rawActivityType.isNotBlank() && rawActivityType.length <= 128)
    }

    fun canonicalRepresentation(): String = buildString {
        append("id=").append(AthleteStateSnapshot.canonicalSegment(recordId))
        append("|prov=").append(AthleteStateSnapshot.canonicalSegment(provider))
        append("|ext=").append(externalId?.let { AthleteStateSnapshot.canonicalSegment(it) } ?: "0:NONE")
        append("|sha=").append(sha256?.let { AthleteStateSnapshot.canonicalSegment(it.lowercase(Locale.ROOT)) } ?: "0:NONE")
        append("|start=").append(AthleteStateSnapshot.canonicalSegment(startedAtUtc.toString()))
        append("|type=").append(AthleteStateSnapshot.canonicalSegment(rawActivityType))
        append("|dist=").append(fmtOptDouble(rawDistanceM))
        append("|dur=").append(fmtOptDouble(rawDurationS))
        append("|mov=").append(fmtOptDouble(rawMovingTimeS))
        append("|pace=").append(fmtOptDouble(rawRecordedPaceSecPerKm))
        append("|avgHr=").append(fmtOptDouble(rawAverageHrBpm))
        append("|maxHr=").append(fmtOptDouble(rawMaximumHrBpm))
        append("|elev=").append(fmtOptDouble(rawElevationM))
    }

    override fun compareTo(other: RawSourceComponent): Int =
        compareBy<RawSourceComponent> { it.startedAtUtc }
            .thenBy { providerPriority(it.provider) }
            .thenBy { it.provider.lowercase(Locale.ROOT) }
            .thenBy { it.recordId }
            .thenBy { it.externalId ?: "" }
            .thenBy { it.sha256 ?: "" }
            .compare(this, other)

    private fun fmtOptDouble(v: Double?): String =
        v?.let { CanonicalSiQuantity.canonicalDecimalString(it) } ?: "NONE"

    companion object {
        internal fun providerPriority(provider: String): Int =
            when (provider.lowercase(Locale.ROOT)) {
                "fit" -> 0
                "garmin" -> 1
                "intervals" -> 2
                "health_connect" -> 3
                "manual" -> 4
                else -> 5
            }
    }
}

internal data class NormalizedActivityMetrics(
    val distance: Meters?,
    val duration: Seconds?,
    val movingTime: Seconds?,
    val averageSpeed: MetersPerSecond?,
    val averageHr: BeatsPerSecond?,
    val maximumHr: BeatsPerSecond?
) {
    fun canonicalRepresentation(): String = buildString {
        append("dist=").append(distance?.canonicalToken() ?: "NONE")
        append("|dur=").append(duration?.canonicalToken() ?: "NONE")
        append("|mov=").append(movingTime?.canonicalToken() ?: "NONE")
        append("|spd=").append(averageSpeed?.canonicalToken() ?: "NONE")
        append("|avgHr=").append(averageHr?.canonicalToken() ?: "NONE")
        append("|maxHr=").append(maximumHr?.canonicalToken() ?: "NONE")
    }
}

internal data class RawActivityObservation(
    val accountId: String,
    val record: ActivityRecord,
    val detail: ActivityDetail? = null,
    val sourceIdentity: ImportedSourceIdentity? = null,
    val hrSensorModality: HeartRateSensorModality = HeartRateSensorModality.UNKNOWN,
    val isOutdoorGnss: Boolean? = null,
    val corroborationToken: String? = null,
    val hasCorroboratingCadenceLockSignal: Boolean = false
) {
    val resolvedIdentity: ImportedSourceIdentity = sourceIdentity ?: ImportedSourceIdentity(
        accountId = accountId,
        provider = record.source,
        externalId = record.id
    )

    init {
        RecordPolicy.identifier(accountId)
        require(resolvedIdentity.accountId == accountId) {
            "INV-03 violation: observation accountId ($accountId) != sourceIdentity.accountId (${resolvedIdentity.accountId})"
        }
        require(detail == null || detail.activityId == record.id) {
            "ActivityDetail.activityId (${detail?.activityId}) must match ActivityRecord.id (${record.id})"
        }
    }

    internal fun toRawComponent(): RawSourceComponent = RawSourceComponent(
        recordId = record.id,
        provider = record.source.lowercase(Locale.ROOT),
        externalId = resolvedIdentity.externalId,
        sha256 = resolvedIdentity.sha256?.lowercase(Locale.ROOT),
        startedAtUtc = record.startedAt,
        rawActivityType = record.type,
        rawTitle = record.title,
        rawDistanceM = record.distanceM,
        rawDurationS = record.durationS,
        rawMovingTimeS = record.movingTimeS,
        rawRecordedPaceSecPerKm = record.recordedPace,
        rawAverageHrBpm = record.averageHr,
        rawMaximumHrBpm = record.maximumHr,
        rawElevationM = record.elevationM
    )
}

internal class CanonicalActivity(
    val accountId: String,
    val discipline: AthleteDiscipline,
    val rawActivityType: String,
    val startedAtUtc: Instant,
    val primarySource: String,
    val normalizedMetrics: NormalizedActivityMetrics,
    val telemetryQuality: TelemetryQualityAssessment,
    val deduplicationStatus: CanonicalDeduplicationStatus,
    rawComponents: List<RawSourceComponent>,
    ambiguities: List<ActivityAmbiguityNotice> = emptyList(),
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val policyVersion: String = Engine15M1PolicyRegistry.POLICY_VERSION
) {
    val rawComponents: List<RawSourceComponent>
    val ambiguities: List<ActivityAmbiguityNotice>
    val duplicateResolutionConfidence: DuplicateResolutionConfidence
    val workloadAggregationEligibility: WorkloadAggregationEligibility
    val calibrationEligibility: CalibrationEligibility
    val contentHash: String
    val canonicalId: String

    init {
        RecordPolicy.identifier(accountId)
        RecordPolicy.identifier(policyVersion)
        require(schemaVersion > 0) { "schemaVersion must be positive" }
        require(rawActivityType.isNotBlank()) { "rawActivityType must be non-blank" }
        require(primarySource.isNotBlank()) { "primarySource must be non-blank" }
        require(rawComponents.isNotEmpty()) { "CanonicalActivity requires at least one RawSourceComponent" }

        val sortedComponents = rawComponents.distinct().sorted()
        this.rawComponents = Collections.unmodifiableList(ArrayList(sortedComponents))

        val sortedAmbiguities = ambiguities.distinct().sorted()
        this.ambiguities = Collections.unmodifiableList(ArrayList(sortedAmbiguities))

        this.duplicateResolutionConfidence = when {
            this.ambiguities.any { it.kind == ActivityAmbiguityKind.SOURCE_IDENTIFIER_COLLISION } ->
                DuplicateResolutionConfidence.UNRESOLVED_SOURCE_ID_COLLISION
            this.ambiguities.any { it.kind == ActivityAmbiguityKind.AMBIGUOUS_OVERLAPPING_DUPLICATE } ->
                DuplicateResolutionConfidence.UNRESOLVED_AMBIGUOUS_OVERLAP
            this.ambiguities.any { it.kind == ActivityAmbiguityKind.POTENTIAL_SPLIT_RECORDING } ->
                DuplicateResolutionConfidence.UNRESOLVED_SPLIT_CANDIDATE
            deduplicationStatus == CanonicalDeduplicationStatus.VERIFIED_MULTI_SOURCE_DUPLICATE ->
                DuplicateResolutionConfidence.VERIFIED_CORROBORATED_MULTI_SOURCE
            deduplicationStatus == CanonicalDeduplicationStatus.EXACT_DUPLICATE_COLLAPSED ->
                DuplicateResolutionConfidence.VERIFIED_EXACT_DUPLICATE
            else -> DuplicateResolutionConfidence.VERIFIED_UNIQUE
        }

        this.workloadAggregationEligibility = when (this.duplicateResolutionConfidence) {
            DuplicateResolutionConfidence.UNRESOLVED_SOURCE_ID_COLLISION ->
                WorkloadAggregationEligibility.BLOCKED_COLLISION
            DuplicateResolutionConfidence.UNRESOLVED_AMBIGUOUS_OVERLAP ->
                WorkloadAggregationEligibility.AMBIGUOUS_BOUNDED_ONLY
            DuplicateResolutionConfidence.UNRESOLVED_SPLIT_CANDIDATE,
            DuplicateResolutionConfidence.VERIFIED_UNIQUE,
            DuplicateResolutionConfidence.VERIFIED_EXACT_DUPLICATE,
            DuplicateResolutionConfidence.VERIFIED_CORROBORATED_MULTI_SOURCE ->
                WorkloadAggregationEligibility.ELIGIBLE_VERIFIED_OBSERVED
        }

        val hrAuthoritative = telemetryQuality.hrQuality == TelemetryQualityStatus.VALID_OBSERVED
        val distanceOrDurationUsable =
            telemetryQuality.distanceQuality == TelemetryQualityStatus.VALID_OBSERVED ||
                telemetryQuality.durationQuality == TelemetryQualityStatus.VALID_OBSERVED
        this.calibrationEligibility = when {
            this.workloadAggregationEligibility != WorkloadAggregationEligibility.ELIGIBLE_VERIFIED_OBSERVED ->
                CalibrationEligibility.INELIGIBLE_UNRESOLVED_AMBIGUITY
            !distanceOrDurationUsable && !hrAuthoritative ->
                CalibrationEligibility.INELIGIBLE_INSUFFICIENT_QUALITY
            hrAuthoritative &&
                telemetryQuality.distanceQuality == TelemetryQualityStatus.VALID_OBSERVED &&
                telemetryQuality.durationQuality == TelemetryQualityStatus.VALID_OBSERVED ->
                CalibrationEligibility.ELIGIBLE_OBSERVED
            distanceOrDurationUsable ->
                CalibrationEligibility.ELIGIBLE_NON_HR_ONLY
            else ->
                CalibrationEligibility.INELIGIBLE_INSUFFICIENT_QUALITY
        }

        this.contentHash = computeSha256(canonicalPayload())
        this.canonicalId = "canact-v$schemaVersion-${contentHash.take(16)}"
    }

    /**
     * Canonical payload defining physical activity identity and preserved evidence.
     * Exact single-record vs. repeated identical re-import status is reflected by
     * `deduplicationStatus` operationally while preserving identical `canonicalId` /
     * `contentHash` across byte-for-byte duplicate imports.
     */
    internal fun canonicalPayload(): String = buildString {
        append("schema=").append(schemaVersion)
        append("|policy=").append(AthleteStateSnapshot.canonicalSegment(policyVersion))
        append("|account=").append(AthleteStateSnapshot.canonicalSegment(accountId))
        append("|disc=").append(AthleteStateSnapshot.canonicalSegment(discipline.code))
        append("|rawType=").append(AthleteStateSnapshot.canonicalSegment(rawActivityType))
        append("|start=").append(AthleteStateSnapshot.canonicalSegment(startedAtUtc.toString()))
        append("|primarySrc=").append(AthleteStateSnapshot.canonicalSegment(primarySource))
        append("|metrics=").append(AthleteStateSnapshot.canonicalSegment(normalizedMetrics.canonicalRepresentation()))
        append("|qual=").append(AthleteStateSnapshot.canonicalSegment(telemetryQuality.canonicalRepresentation()))
        append("|wElig=").append(workloadAggregationEligibility.name)
        append("|calElig=").append(calibrationEligibility.name)
        append("|comps=").append(AthleteStateSnapshot.canonicalList(rawComponents.map { it.canonicalRepresentation() }))
        append("|ambig=").append(AthleteStateSnapshot.canonicalList(ambiguities.map { it.canonicalRepresentation() }))
    }

    override fun equals(other: Any?): Boolean =
        other is CanonicalActivity &&
            contentHash == other.contentHash &&
            deduplicationStatus == other.deduplicationStatus

    override fun hashCode(): Int = 31 * contentHash.hashCode() + deduplicationStatus.hashCode()

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        private fun computeSha256(payload: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(payload.toByteArray(StandardCharsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        }
    }
}

internal data class CanonicalActivityProjectionResult(
    val activities: List<CanonicalActivity>,
    val unresolvedAmbiguityCount: Int
) {
    val safeActivities: List<CanonicalActivity> = Collections.unmodifiableList(ArrayList(activities))
}

/**
 * Pure, deterministic Layer-1 projector converting raw activity observations into
 * account-scoped, deduplicated (only when verified), quality-classified CanonicalActivity
 * contracts without mutating SQLite stores or generating plans.
 */
internal object Engine15CanonicalActivityProjector {

    private data class MutableCluster(
        val accountId: String,
        val discipline: AthleteDiscipline,
        val members: MutableList<RawActivityObservation>,
        var collapsedExactDuplicate: Boolean = false,
        val ambiguityNotices: MutableSet<ActivityAmbiguityNotice> = linkedSetOf(),
        var statusOverride: CanonicalDeduplicationStatus? = null
    )

    fun project(observations: List<RawActivityObservation>): CanonicalActivityProjectionResult {
        if (observations.isEmpty()) {
            return CanonicalActivityProjectionResult(emptyList(), 0)
        }

        // INV-03: Partition strictly by accountId before any inspection.
        val byAccount = observations.groupBy { it.accountId }.toSortedMap()
        val results = mutableListOf<CanonicalActivity>()

        for ((accountId, accountObs) in byAccount) {
            val sortedObs = accountObs.sortedWith(
                compareBy<RawActivityObservation> { it.record.startedAt }
                    .thenBy { mapDiscipline(it.record).code }
                    .thenBy { RawSourceComponent.providerPriority(it.record.source) }
                    .thenBy { it.record.source.lowercase(Locale.ROOT) }
                    .thenBy { it.record.id }
                    .thenBy { it.resolvedIdentity.externalId ?: "" }
                    .thenBy { it.resolvedIdentity.sha256 ?: "" }
            )

            val clusters = mutableListOf<MutableCluster>()

            for (obs in sortedObs) {
                val obsDiscipline = mapDiscipline(obs.record)

                // Step 1: Check same source identity (exact duplicate vs. historical correction vs. collision).
                val exactIdentityMatches = clusters.filter { cluster ->
                    cluster.members.any { it.resolvedIdentity.sameOriginal(obs.resolvedIdentity) }
                }

                var handled = false
                if (exactIdentityMatches.isNotEmpty()) {
                    val compatibleCluster = exactIdentityMatches.firstOrNull { cluster ->
                        cluster.members.any { existing -> isCompatibleSameSourceUpdate(existing, obs) }
                    }
                    if (compatibleCluster != null) {
                        compatibleCluster.members += obs
                        compatibleCluster.collapsedExactDuplicate = true
                        handled = true
                    } else {
                        // SOURCE_IDENTIFIER_COLLISION: same provider/id (or sha) reused on distinct activity!
                        val newCluster = MutableCluster(
                            accountId = accountId,
                            discipline = obsDiscipline,
                            members = mutableListOf(obs),
                            statusOverride = CanonicalDeduplicationStatus.AMBIGUOUS_DUPLICATE_CANDIDATE
                        )
                        for (otherCluster in exactIdentityMatches) {
                            val otherRep = otherCluster.members.first()
                            otherCluster.ambiguityNotices += ActivityAmbiguityNotice(
                                kind = ActivityAmbiguityKind.SOURCE_IDENTIFIER_COLLISION,
                                relatedRawRecordId = obs.record.id,
                                relatedSource = obs.record.source.lowercase(Locale.ROOT),
                                reasonCode = "SOURCE_ID_COLLISION_CONTRADICTORY_ACTIVITY"
                            )
                            newCluster.ambiguityNotices += ActivityAmbiguityNotice(
                                kind = ActivityAmbiguityKind.SOURCE_IDENTIFIER_COLLISION,
                                relatedRawRecordId = otherRep.record.id,
                                relatedSource = otherRep.record.source.lowercase(Locale.ROOT),
                                reasonCode = "SOURCE_ID_COLLISION_CONTRADICTORY_ACTIVITY"
                            )
                            otherCluster.statusOverride = CanonicalDeduplicationStatus.AMBIGUOUS_DUPLICATE_CANDIDATE
                        }
                        clusters += newCluster
                        handled = true
                    }
                }

                if (handled) continue

                // Step 2: Check multi-source duplicate eligibility (POL-001 + POL-002 + corroboration).
                val verifiedMatches = mutableListOf<MutableCluster>()
                val ambiguousMatches = mutableListOf<MutableCluster>()

                for (cluster in clusters) {
                    if (cluster.discipline != obsDiscipline) continue
                    val rep = choosePrimaryObservation(cluster.members)
                    if (!compatibleRawType(rep.record, obs.record)) continue
                    if (passesPol001AndPol002(rep.record, obs.record)) {
                        if (hasMultiSourceCorroboration(rep, obs)) {
                            verifiedMatches += cluster
                        } else {
                            ambiguousMatches += cluster
                        }
                    }
                }

                if (verifiedMatches.size == 1 && ambiguousMatches.isEmpty()) {
                    verifiedMatches.single().members += obs
                } else {
                    val newCluster = MutableCluster(
                        accountId = accountId,
                        discipline = obsDiscipline,
                        members = mutableListOf(obs)
                    )
                    val allCandidates = (verifiedMatches + ambiguousMatches)
                    if (allCandidates.isNotEmpty()) {
                        val reason = if (verifiedMatches.size > 1) {
                            "MULTIPLE_CORROBORATED_CANDIDATES"
                        } else {
                            "POL001_002_OVERLAP_UNCORROBORATED"
                        }
                        for (other in allCandidates) {
                            val otherRep = choosePrimaryObservation(other.members)
                            newCluster.ambiguityNotices += ActivityAmbiguityNotice(
                                kind = ActivityAmbiguityKind.AMBIGUOUS_OVERLAPPING_DUPLICATE,
                                relatedRawRecordId = otherRep.record.id,
                                relatedSource = otherRep.record.source.lowercase(Locale.ROOT),
                                reasonCode = reason
                            )
                            other.ambiguityNotices += ActivityAmbiguityNotice(
                                kind = ActivityAmbiguityKind.AMBIGUOUS_OVERLAPPING_DUPLICATE,
                                relatedRawRecordId = obs.record.id,
                                relatedSource = obs.record.source.lowercase(Locale.ROOT),
                                reasonCode = reason
                            )
                            if (other.statusOverride == null) {
                                other.statusOverride = CanonicalDeduplicationStatus.AMBIGUOUS_DUPLICATE_CANDIDATE
                            }
                        }
                        newCluster.statusOverride = CanonicalDeduplicationStatus.AMBIGUOUS_DUPLICATE_CANDIDATE
                    }
                    clusters += newCluster
                }
            }

            // Step 3: Non-merging POL-009 potential split-recording flagging.
            flagPotentialSplitRecordings(clusters)

            // Step 4: Materialize immutable CanonicalActivity objects deterministically.
            for (cluster in clusters) {
                val primary = choosePrimaryObservation(cluster.members)
                val quality = Engine15TelemetryQualityClassifier.classify(
                    record = primary.record,
                    detail = primary.detail,
                    discipline = cluster.discipline,
                    hrSensorModality = primary.hrSensorModality,
                    isOutdoorGnss = primary.isOutdoorGnss,
                    hasCorroboratingCadenceLockSignal = primary.hasCorroboratingCadenceLockSignal
                )
                val normalizedMetrics = normalizeMetrics(primary.record, quality)

                val distinctComponents = cluster.members.map { it.toRawComponent() }.distinct().sorted()
                val dedupStatus = when {
                    cluster.statusOverride != null -> cluster.statusOverride!!
                    distinctComponents.size > 1 &&
                        distinctComponents.map { it.provider to (it.externalId ?: it.recordId) }.distinct().size > 1 ->
                        CanonicalDeduplicationStatus.VERIFIED_MULTI_SOURCE_DUPLICATE
                    cluster.collapsedExactDuplicate || cluster.members.size > 1 ->
                        CanonicalDeduplicationStatus.EXACT_DUPLICATE_COLLAPSED
                    else -> CanonicalDeduplicationStatus.SINGLE_SOURCE
                }

                results += CanonicalActivity(
                    accountId = accountId,
                    discipline = cluster.discipline,
                    rawActivityType = primary.record.type,
                    startedAtUtc = primary.record.startedAt,
                    primarySource = primary.record.source.lowercase(Locale.ROOT),
                    normalizedMetrics = normalizedMetrics,
                    telemetryQuality = quality,
                    deduplicationStatus = dedupStatus,
                    rawComponents = distinctComponents,
                    ambiguities = cluster.ambiguityNotices.toList()
                )
            }
        }

        val orderedResults = results.sortedWith(
            compareBy<CanonicalActivity> { it.accountId }
                .thenByDescending { it.startedAtUtc }
                .thenBy { it.discipline.code }
                .thenBy { it.canonicalId }
        )
        val ambiguityCount = orderedResults.sumOf { it.ambiguities.size }
        return CanonicalActivityProjectionResult(
            activities = orderedResults,
            unresolvedAmbiguityCount = ambiguityCount
        )
    }

    private fun isCompatibleSameSourceUpdate(a: RawActivityObservation, b: RawActivityObservation): Boolean {
        if (mapDiscipline(a.record) != mapDiscipline(b.record)) return false
        val startDiffSec = abs(Duration.between(a.record.startedAt, b.record.startedAt).seconds)
        return startDiffSec <= Engine15M1PolicyRegistry.SOURCE_ID_COLLISION_MIN_START_DIFF_SECONDS
    }

    private fun passesPol001AndPol002(a: ActivityRecord, b: ActivityRecord): Boolean {
        val durA = a.durationS?.takeIf { it.isFinite() && it > 0.0 } ?: return false
        val durB = b.durationS?.takeIf { it.isFinite() && it > 0.0 } ?: return false

        val startA = a.startedAt.toEpochMilli() / 1000.0
        val endA = startA + durA
        val startB = b.startedAt.toEpochMilli() / 1000.0
        val endB = startB + durB

        val overlap = (min(endA, endB) - max(startA, startB)).coerceAtLeast(0.0)
        val minDur = min(durA, durB)
        if (minDur <= 0.0) return false
        val overlapRatio = overlap / minDur
        if (overlapRatio < Engine15M1PolicyRegistry.POL_001_MIN_TEMPORAL_OVERLAP_RATIO) return false

        val durDiffRatio = abs(durA - durB) / max(durA, durB)
        if (durDiffRatio > Engine15M1PolicyRegistry.POL_002_MAX_DURATION_DIFF_RATIO) return false

        val distA = a.distanceM?.takeIf { it.isFinite() && it > 0.0 }
        val distB = b.distanceM?.takeIf { it.isFinite() && it > 0.0 }
        if (distA != null && distB != null) {
            val distDiffRatio = abs(distA - distB) / max(distA, distB)
            if (distDiffRatio > Engine15M1PolicyRegistry.POL_002_MAX_DISTANCE_DIFF_RATIO) return false
        }
        return true
    }

    private fun hasMultiSourceCorroboration(a: RawActivityObservation, b: RawActivityObservation): Boolean {
        val shaA = a.resolvedIdentity.sha256?.lowercase(Locale.ROOT)
        val shaB = b.resolvedIdentity.sha256?.lowercase(Locale.ROOT)
        if (shaA != null && shaB != null && shaA == shaB) return true

        if (!a.corroborationToken.isNullOrBlank() && a.corroborationToken == b.corroborationToken) {
            return true
        }

        if (hasSpatialRouteAgreement(a.detail, b.detail)) {
            return true
        }

        // Reuse existing legacy-vs-source-owned Garmin corroboration criteria (tight title + metric agreement).
        if (isCorroboratedGarminLegacyPair(a.record, b.record)) {
            return true
        }

        return false
    }

    private fun hasSpatialRouteAgreement(a: ActivityDetail?, b: ActivityDetail?): Boolean {
        val gpsA = a?.samples.orEmpty().filter { it.latitude != null && it.longitude != null }
        val gpsB = b?.samples.orEmpty().filter { it.latitude != null && it.longitude != null }
        if (gpsA.size < 3 || gpsB.size < 3) return false

        val startDist = Engine15TelemetryQualityClassifier.haversineMeters(
            gpsA.first().latitude!!, gpsA.first().longitude!!,
            gpsB.first().latitude!!, gpsB.first().longitude!!
        )
        val endDist = Engine15TelemetryQualityClassifier.haversineMeters(
            gpsA.last().latitude!!, gpsA.last().longitude!!,
            gpsB.last().latitude!!, gpsB.last().longitude!!
        )
        return startDist <= Engine15M1PolicyRegistry.CORROBORATION_MAX_ENDPOINT_DISTANCE_METERS &&
            endDist <= Engine15M1PolicyRegistry.CORROBORATION_MAX_ENDPOINT_DISTANCE_METERS
    }

    private fun isCorroboratedGarminLegacyPair(a: ActivityRecord, b: ActivityRecord): Boolean {
        if (!a.source.equals("garmin", ignoreCase = true) ||
            !b.source.equals("garmin", ignoreCase = true)
        ) return false
        val sourceOwned = Regex("garmin-[0-9a-f]{64}")
        val aOwned = a.id.matches(sourceOwned)
        val bOwned = b.id.matches(sourceOwned)
        if (aOwned == bOwned) return false
        val normA = a.title.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
        val normB = b.title.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
        if (normA != normB) return false
        val durA = a.durationS ?: return false
        val durB = b.durationS ?: return false
        val distA = a.distanceM ?: return false
        val distB = b.distanceM ?: return false
        return abs(durA - durB) <= max(5.0, max(durA, durB) * 0.002) &&
            abs(distA - distB) <= max(15.0, max(distA, distB) * 0.002)
    }

    private fun flagPotentialSplitRecordings(clusters: List<MutableCluster>) {
        if (clusters.size < 2) return
        val ordered = clusters.sortedBy { choosePrimaryObservation(it.members).record.startedAt }
        for (i in 0 until ordered.size - 1) {
            val firstCluster = ordered[i]
            val secondCluster = ordered[i + 1]
            if (firstCluster.discipline != secondCluster.discipline) continue

            val first = choosePrimaryObservation(firstCluster.members).record
            val second = choosePrimaryObservation(secondCluster.members).record
            if (!compatibleRawType(first, second)) continue

            val durFirst = first.durationS?.takeIf { it.isFinite() && it > 0.0 } ?: continue
            val firstEndSec = (first.startedAt.toEpochMilli() / 1000.0) + durFirst
            val secondStartSec = second.startedAt.toEpochMilli() / 1000.0
            val gapSeconds = secondStartSec - firstEndSec

            if (gapSeconds > 0.0 && gapSeconds <= Engine15M1PolicyRegistry.POL_009_SPLIT_CANDIDATE_MAX_GAP_SECONDS) {
                firstCluster.ambiguityNotices += ActivityAmbiguityNotice(
                    kind = ActivityAmbiguityKind.POTENTIAL_SPLIT_RECORDING,
                    relatedRawRecordId = second.id,
                    relatedSource = second.source.lowercase(Locale.ROOT),
                    reasonCode = "POL009_SHORT_GAP_SPLIT_CANDIDATE"
                )
                secondCluster.ambiguityNotices += ActivityAmbiguityNotice(
                    kind = ActivityAmbiguityKind.POTENTIAL_SPLIT_RECORDING,
                    relatedRawRecordId = first.id,
                    relatedSource = first.source.lowercase(Locale.ROOT),
                    reasonCode = "POL009_SHORT_GAP_SPLIT_CANDIDATE"
                )
                if (firstCluster.statusOverride == null) {
                    firstCluster.statusOverride = CanonicalDeduplicationStatus.POTENTIAL_SPLIT_RECORDING
                }
                if (secondCluster.statusOverride == null) {
                    secondCluster.statusOverride = CanonicalDeduplicationStatus.POTENTIAL_SPLIT_RECORDING
                }
            }
        }
    }

    private fun choosePrimaryObservation(members: List<RawActivityObservation>): RawActivityObservation =
        members.sortedWith(
            compareBy<RawActivityObservation> { RawSourceComponent.providerPriority(it.record.source) }
                .thenByDescending { if (it.detail != null) 1 else 0 }
                .thenByDescending { it.detail?.samples?.size ?: 0 }
                .thenByDescending { it.record.distanceM ?: -1.0 }
                .thenByDescending { it.record.durationS ?: -1.0 }
                .thenBy { it.record.id }
        ).first()

    private fun normalizeMetrics(
        record: ActivityRecord,
        quality: TelemetryQualityAssessment
    ): NormalizedActivityMetrics {
        val distance = record.distanceM?.takeIf { it.isFinite() && it >= 0.0 }?.let(Meters::of)
        val duration = record.durationS?.takeIf { it.isFinite() && it >= 0.0 }?.let(Seconds::of)
        val movingTime = record.movingTimeS?.takeIf { it.isFinite() && it >= 0.0 }?.let(Seconds::of)

        val avgSpeed = when {
            record.supportsPace && record.recordedPace != null && record.recordedPace.isFinite() && record.recordedPace > 0.0 ->
                MetersPerSecond.fromPaceSecondsPerKm(record.recordedPace)
            record.supportsPace && distance != null && distance.value > 0.0 -> {
                val activeTime = movingTime?.takeIf { it.value > 0.0 } ?: duration?.takeIf { it.value > 0.0 }
                activeTime?.let { MetersPerSecond.of(distance.value / it.value) }
            }
            else -> null
        }

        val hrAllowed = quality.hrQuality != TelemetryQualityStatus.QUARANTINED_CADENCE_LOCK &&
            quality.hrQuality != TelemetryQualityStatus.SUSPECTED_CADENCE_LOCK &&
            quality.hrQuality != TelemetryQualityStatus.QUARANTINED_UNRELIABLE

        val avgHr = if (hrAllowed) {
            record.averageHr?.takeIf { it.isFinite() && it in 20.0..260.0 }?.let(BeatsPerSecond::fromBpm)
        } else {
            null
        }
        val maxHr = if (hrAllowed) {
            record.maximumHr?.takeIf { it.isFinite() && it in 20.0..260.0 }?.let(BeatsPerSecond::fromBpm)
        } else {
            null
        }

        return NormalizedActivityMetrics(
            distance = distance,
            duration = duration,
            movingTime = movingTime,
            averageSpeed = avgSpeed,
            averageHr = avgHr,
            maximumHr = maxHr
        )
    }

    internal fun mapDiscipline(record: ActivityRecord): AthleteDiscipline {
        if (DashboardSummary.isRun(record)) return AthleteDiscipline.RUN
        val norm = record.type.trim().lowercase(Locale.ROOT).replace(Regex("[ _-]"), "")
        return when {
            norm in setOf("strength", "training", "weighttraining", "weights", "gym", "lifting") ->
                AthleteDiscipline.STRENGTH
            norm.contains("calisthenics") || norm.contains("bodyweight") ->
                AthleteDiscipline.CALISTHENICS
            else -> AthleteDiscipline.CONDITIONING
        }
    }

    private fun compatibleRawType(a: ActivityRecord, b: ActivityRecord): Boolean {
        val discA = mapDiscipline(a)
        val discB = mapDiscipline(b)
        if (discA != discB) return false
        if (discA != AthleteDiscipline.CONDITIONING) return true
        // Prevent cycling and swimming (both under generic non-run conditioning/other) from colliding.
        val normA = a.type.trim().lowercase(Locale.ROOT).replace(Regex("[ _-]"), "")
        val normB = b.type.trim().replace(Regex("[ _-]"), "").lowercase(Locale.ROOT)
        return normA == normB
    }
}
