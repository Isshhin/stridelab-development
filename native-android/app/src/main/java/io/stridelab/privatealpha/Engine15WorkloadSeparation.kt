package io.stridelab.privatealpha

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.TreeMap
import kotlin.math.abs
import kotlin.math.max

/**
 * Layer-1 / M1C strictly separated Observed vs. Projected workload contracts (INV-02, INV-09).
 * ObservedWorkloadState and ProjectedWorkloadState share no common supertype, making
 * compile-time leakage of planned or hypothetical sessions into observed capacity impossible.
 */
internal enum class ObservedExposureProvenance {
    CANONICAL_ACTIVITY_VERIFIED,
    CANONICAL_ACTIVITY_AMBIGUOUS_BOUNDED,
    CANONICAL_ACTIVITY_COLLISION_BLOCKED,
    COMPLETED_WORKOUT_LOG_VERIFIED,
    COMPLETED_WORKOUT_LOG_UNKNOWN_DISTANCE
}

internal class DatedObservedSessionExposure(
    val exposureId: String,
    val accountId: String,
    val discipline: AthleteDiscipline,
    val localDate: LocalDate,
    val startedAtUtc: Instant,
    val distance: Meters?,
    val duration: Seconds?,
    val provenance: ObservedExposureProvenance,
    sourceRecordIds: Set<String>,
    val ambiguityGroupKey: String? = null
) : Comparable<DatedObservedSessionExposure> {
    val sourceRecordIds: Set<String>

    init {
        RecordPolicy.identifier(exposureId)
        RecordPolicy.identifier(accountId)
        require(sourceRecordIds.isNotEmpty()) { "DatedObservedSessionExposure requires sourceRecordIds" }
        val sorted = sourceRecordIds.map { id ->
            RecordPolicy.identifier(id)
            id
        }.sorted()
        this.sourceRecordIds = Collections.unmodifiableSet(LinkedHashSet(sorted))
        if (ambiguityGroupKey != null) {
            RecordPolicy.identifier(ambiguityGroupKey)
        }
        if (provenance == ObservedExposureProvenance.COMPLETED_WORKOUT_LOG_UNKNOWN_DISTANCE) {
            require(distance == null) {
                "COMPLETED_WORKOUT_LOG_UNKNOWN_DISTANCE must not hold an invented distance"
            }
        }
    }

    fun canonicalRepresentation(): String = buildString {
        append("id=").append(AthleteStateSnapshot.canonicalSegment(exposureId))
        append("|acct=").append(AthleteStateSnapshot.canonicalSegment(accountId))
        append("|disc=").append(AthleteStateSnapshot.canonicalSegment(discipline.code))
        append("|date=").append(localDate.toString())
        append("|utc=").append(AthleteStateSnapshot.canonicalSegment(startedAtUtc.toString()))
        append("|dist=").append(distance?.canonicalToken() ?: "NONE")
        append("|dur=").append(duration?.canonicalToken() ?: "NONE")
        append("|prov=").append(provenance.name)
        append("|ambig=").append(ambiguityGroupKey?.let { AthleteStateSnapshot.canonicalSegment(it) } ?: "0:NONE")
        append("|srcs=").append(AthleteStateSnapshot.canonicalList(sourceRecordIds))
    }

    override fun compareTo(other: DatedObservedSessionExposure): Int =
        compareBy<DatedObservedSessionExposure> { it.localDate }
            .thenBy { it.startedAtUtc }
            .thenBy { it.discipline.code }
            .thenBy { it.exposureId }
            .compare(this, other)

    override fun equals(other: Any?): Boolean =
        other is DatedObservedSessionExposure &&
            exposureId == other.exposureId &&
            accountId == other.accountId &&
            discipline == other.discipline &&
            localDate == other.localDate &&
            startedAtUtc == other.startedAtUtc &&
            distance == other.distance &&
            duration == other.duration &&
            provenance == other.provenance &&
            sourceRecordIds == other.sourceRecordIds &&
            ambiguityGroupKey == other.ambiguityGroupKey

    override fun hashCode(): Int {
        var r = exposureId.hashCode()
        r = 31 * r + accountId.hashCode()
        r = 31 * r + discipline.hashCode()
        r = 31 * r + localDate.hashCode()
        r = 31 * r + startedAtUtc.hashCode()
        r = 31 * r + (distance?.hashCode() ?: 0)
        r = 31 * r + (duration?.hashCode() ?: 0)
        r = 31 * r + provenance.hashCode()
        r = 31 * r + sourceRecordIds.hashCode()
        r = 31 * r + (ambiguityGroupKey?.hashCode() ?: 0)
        return r
    }
}

internal data class ProvisionalSelfReportWorkload(
    val discipline: AthleteDiscipline,
    val basis: RecentMileageBasis,
    val weeklyVolume: Meters? = null,
    val longestSession: Meters? = null,
    val confirmedZero: Boolean = false
) {
    init {
        if (basis == RecentMileageBasis.CONFIRMED_ZERO) {
            require(confirmedZero) { "CONFIRMED_ZERO basis requires confirmedZero = true" }
        }
        if (confirmedZero) {
            require(weeklyVolume == null || weeklyVolume.value == 0.0) {
                "Confirmed zero self-report cannot hold positive weeklyVolume"
            }
            require(longestSession == null || longestSession.value == 0.0) {
                "Confirmed zero self-report cannot hold positive longestSession"
            }
        }
    }

    fun canonicalRepresentation(): String =
        "disc=${discipline.code}|basis=${basis.name}|zero=$confirmedZero|" +
            "wk=${weeklyVolume?.canonicalToken() ?: "NONE"}|long=${longestSession?.canonicalToken() ?: "NONE"}"
}

internal class DisciplineObservedWorkload(
    val discipline: AthleteDiscipline,
    val coverage: HistoryCoverageStatus,
    val observedSpanDays: Int,
    val maxGapDays: Int?,
    val rolling7dDistanceBelief: BeliefState<Meters>,
    val rolling28dDistanceBelief: BeliefState<Meters>,
    val rolling28dWeeklyAverageBelief: BeliefState<Meters>,
    val preceding30dLongestSessionBelief: BeliefState<Meters>,
    val verifiedSessionCount28d: Int,
    val ambiguousCandidateCount28d: Int,
    val unknownDistanceCompletedCount28d: Int,
    val selfReportConflictFlag: Boolean,
    exposures: List<DatedObservedSessionExposure>
) {
    val exposures: List<DatedObservedSessionExposure>

    init {
        require(observedSpanDays >= 0) { "observedSpanDays must be >= 0" }
        require(maxGapDays == null || maxGapDays >= 0) { "maxGapDays must be >= 0" }
        require(verifiedSessionCount28d >= 0)
        require(ambiguousCandidateCount28d >= 0)
        require(unknownDistanceCompletedCount28d >= 0)
        require(exposures.all { it.discipline == discipline }) {
            "All exposures inside DisciplineObservedWorkload must match discipline $discipline"
        }
        val sorted = exposures.distinct().sorted()
        this.exposures = Collections.unmodifiableList(ArrayList(sorted))
    }

    fun canonicalRepresentation(): String = buildString {
        append("disc=").append(AthleteStateSnapshot.canonicalSegment(discipline.code))
        append("|cov=").append(coverage.name)
        append("|span=").append(observedSpanDays)
        append("|gap=").append(maxGapDays?.toString() ?: "NONE")
        append("|r7=").append(AthleteStateSnapshot.canonicalSegment(rolling7dDistanceBelief.canonicalRepresentation()))
        append("|r28=").append(AthleteStateSnapshot.canonicalSegment(rolling28dDistanceBelief.canonicalRepresentation()))
        append("|wkAvg=").append(AthleteStateSnapshot.canonicalSegment(rolling28dWeeklyAverageBelief.canonicalRepresentation()))
        append("|l30=").append(AthleteStateSnapshot.canonicalSegment(preceding30dLongestSessionBelief.canonicalRepresentation()))
        append("|vCnt=").append(verifiedSessionCount28d)
        append("|aCnt=").append(ambiguousCandidateCount28d)
        append("|uCnt=").append(unknownDistanceCompletedCount28d)
        append("|srConf=").append(selfReportConflictFlag)
        append("|exps=").append(AthleteStateSnapshot.canonicalList(exposures.map { it.canonicalRepresentation() }))
    }

    override fun equals(other: Any?): Boolean =
        other is DisciplineObservedWorkload &&
            discipline == other.discipline &&
            coverage == other.coverage &&
            observedSpanDays == other.observedSpanDays &&
            maxGapDays == other.maxGapDays &&
            rolling7dDistanceBelief == other.rolling7dDistanceBelief &&
            rolling28dDistanceBelief == other.rolling28dDistanceBelief &&
            rolling28dWeeklyAverageBelief == other.rolling28dWeeklyAverageBelief &&
            preceding30dLongestSessionBelief == other.preceding30dLongestSessionBelief &&
            verifiedSessionCount28d == other.verifiedSessionCount28d &&
            ambiguousCandidateCount28d == other.ambiguousCandidateCount28d &&
            unknownDistanceCompletedCount28d == other.unknownDistanceCompletedCount28d &&
            selfReportConflictFlag == other.selfReportConflictFlag &&
            exposures == other.exposures

    override fun hashCode(): Int {
        var r = discipline.hashCode()
        r = 31 * r + coverage.hashCode()
        r = 31 * r + observedSpanDays
        r = 31 * r + (maxGapDays ?: -1)
        r = 31 * r + rolling7dDistanceBelief.hashCode()
        r = 31 * r + rolling28dDistanceBelief.hashCode()
        r = 31 * r + rolling28dWeeklyAverageBelief.hashCode()
        r = 31 * r + preceding30dLongestSessionBelief.hashCode()
        r = 31 * r + exposures.hashCode()
        return r
    }
}

/**
 * Strictly observed workload summary for a single account as of [asOfDate] in [zoneId].
 */
internal class ObservedWorkloadState(
    val accountId: String,
    val asOfDate: LocalDate,
    val zoneId: ZoneId,
    disciplines: Map<AthleteDiscipline, DisciplineObservedWorkload>,
    provisionalSelfReport: Map<AthleteDiscipline, ProvisionalSelfReportWorkload> = emptyMap(),
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val policyVersion: String = Engine15M1PolicyRegistry.POLICY_VERSION
) {
    val disciplines: Map<AthleteDiscipline, DisciplineObservedWorkload>
    val provisionalSelfReport: Map<AthleteDiscipline, ProvisionalSelfReportWorkload>
    val contentHash: String

    init {
        RecordPolicy.identifier(accountId)
        RecordPolicy.identifier(policyVersion)
        require(schemaVersion > 0) { "schemaVersion must be positive" }

        val sortedDisc = TreeMap<AthleteDiscipline, DisciplineObservedWorkload>()
        for ((k, v) in disciplines) {
            require(v.discipline == k) { "Map key $k != DisciplineObservedWorkload.discipline ${v.discipline}" }
            require(v.exposures.all { it.accountId == accountId }) {
                "INV-03 violation: exposure accountId must match ObservedWorkloadState.accountId"
            }
            sortedDisc[k] = v
        }
        this.disciplines = Collections.unmodifiableMap(sortedDisc)

        val sortedSr = TreeMap<AthleteDiscipline, ProvisionalSelfReportWorkload>()
        for ((k, v) in provisionalSelfReport) {
            require(v.discipline == k) { "Map key $k != ProvisionalSelfReportWorkload.discipline ${v.discipline}" }
            sortedSr[k] = v
        }
        this.provisionalSelfReport = Collections.unmodifiableMap(sortedSr)

        this.contentHash = computeSha256(canonicalPayload())
    }

    /**
     * Authoritative M1C query for preceding 30-day observed longest run ([asOfDate - 30, asOfDate - 1]).
     */
    fun observedLongestRunMeters30d(): BeliefState<Meters> =
        disciplines[AthleteDiscipline.RUN]?.preceding30dLongestSessionBelief
            ?: BeliefState.Unknown("NO_OBSERVED_RUN_HISTORY")

    internal fun canonicalPayload(): String = buildString {
        append("schema=").append(schemaVersion)
        append("|policy=").append(AthleteStateSnapshot.canonicalSegment(policyVersion))
        append("|acct=").append(AthleteStateSnapshot.canonicalSegment(accountId))
        append("|asOf=").append(asOfDate.toString())
        append("|tz=").append(AthleteStateSnapshot.canonicalSegment(zoneId.id))
        append("|discs=").append(AthleteStateSnapshot.canonicalList(disciplines.values.map { it.canonicalRepresentation() }))
        append("|sr=").append(AthleteStateSnapshot.canonicalList(provisionalSelfReport.values.map { it.canonicalRepresentation() }))
    }

    override fun equals(other: Any?): Boolean =
        other is ObservedWorkloadState &&
            schemaVersion == other.schemaVersion &&
            policyVersion == other.policyVersion &&
            accountId == other.accountId &&
            asOfDate == other.asOfDate &&
            zoneId == other.zoneId &&
            disciplines == other.disciplines &&
            provisionalSelfReport == other.provisionalSelfReport

    override fun hashCode(): Int {
        var r = accountId.hashCode()
        r = 31 * r + asOfDate.hashCode()
        r = 31 * r + zoneId.hashCode()
        r = 31 * r + disciplines.hashCode()
        r = 31 * r + provisionalSelfReport.hashCode()
        return r
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        private fun computeSha256(payload: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(payload.toByteArray(StandardCharsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        }
    }
}

internal enum class ProjectedWorkloadAuthority {
    ACCEPTED_PLAN_FUTURE,
    PROPOSED_PLAN_PREVIEW,
    CONDITIONAL_FUTURE_PROGRESSION,
    HYPOTHETICAL_FEASIBILITY
}

internal data class DatedProjectedSessionExposure(
    val projectionId: String,
    val accountId: String,
    val planId: String,
    val sessionId: String,
    val discipline: AthleteDiscipline,
    val scheduledDate: LocalDate,
    val authority: ProjectedWorkloadAuthority,
    val expectedDistanceMin: Meters? = null,
    val expectedDistanceMax: Meters? = null,
    val expectedDurationMin: Seconds? = null,
    val expectedDurationMax: Seconds? = null
) : Comparable<DatedProjectedSessionExposure> {
    init {
        RecordPolicy.identifier(projectionId)
        RecordPolicy.identifier(accountId)
        RecordPolicy.identifier(planId)
        RecordPolicy.identifier(sessionId)
        if (expectedDistanceMin != null && expectedDistanceMax != null) {
            require(expectedDistanceMin.value <= expectedDistanceMax.value) {
                "expectedDistanceMin (${expectedDistanceMin.value}) > expectedDistanceMax (${expectedDistanceMax.value})"
            }
        }
        if (expectedDurationMin != null && expectedDurationMax != null) {
            require(expectedDurationMin.value <= expectedDurationMax.value) {
                "expectedDurationMin (${expectedDurationMin.value}) > expectedDurationMax (${expectedDurationMax.value})"
            }
        }
    }

    fun canonicalRepresentation(): String = buildString {
        append("id=").append(AthleteStateSnapshot.canonicalSegment(projectionId))
        append("|acct=").append(AthleteStateSnapshot.canonicalSegment(accountId))
        append("|plan=").append(AthleteStateSnapshot.canonicalSegment(planId))
        append("|sess=").append(AthleteStateSnapshot.canonicalSegment(sessionId))
        append("|disc=").append(AthleteStateSnapshot.canonicalSegment(discipline.code))
        append("|date=").append(scheduledDate.toString())
        append("|auth=").append(authority.name)
        append("|dMin=").append(expectedDistanceMin?.canonicalToken() ?: "NONE")
        append("|dMax=").append(expectedDistanceMax?.canonicalToken() ?: "NONE")
        append("|tMin=").append(expectedDurationMin?.canonicalToken() ?: "NONE")
        append("|tMax=").append(expectedDurationMax?.canonicalToken() ?: "NONE")
    }

    override fun compareTo(other: DatedProjectedSessionExposure): Int =
        compareBy<DatedProjectedSessionExposure> { it.scheduledDate }
            .thenBy { it.authority.name }
            .thenBy { it.planId }
            .thenBy { it.discipline.code }
            .thenBy { it.sessionId }
            .thenBy { it.projectionId }
            .compare(this, other)
}

/**
 * Strictly non-authoritative projected workload container. Must never upgrade ObservedWorkloadState
 * or observed athlete capacity (INV-02).
 */
internal class ProjectedWorkloadState(
    val accountId: String,
    val asOfDate: LocalDate,
    exposures: List<DatedProjectedSessionExposure>,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val policyVersion: String = Engine15M1PolicyRegistry.POLICY_VERSION
) {
    val exposures: List<DatedProjectedSessionExposure>
    val contentHash: String

    init {
        RecordPolicy.identifier(accountId)
        RecordPolicy.identifier(policyVersion)
        require(schemaVersion > 0) { "schemaVersion must be positive" }
        require(exposures.all { it.accountId == accountId }) {
            "INV-03 violation: all projected exposures must match accountId"
        }
        val sorted = exposures.distinct().sorted()
        this.exposures = Collections.unmodifiableList(ArrayList(sorted))
        this.contentHash = computeSha256(canonicalPayload())
    }

    internal fun canonicalPayload(): String = buildString {
        append("schema=").append(schemaVersion)
        append("|policy=").append(AthleteStateSnapshot.canonicalSegment(policyVersion))
        append("|acct=").append(AthleteStateSnapshot.canonicalSegment(accountId))
        append("|asOf=").append(asOfDate.toString())
        append("|exps=").append(AthleteStateSnapshot.canonicalList(exposures.map { it.canonicalRepresentation() }))
    }

    override fun equals(other: Any?): Boolean =
        other is ProjectedWorkloadState &&
            schemaVersion == other.schemaVersion &&
            policyVersion == other.policyVersion &&
            accountId == other.accountId &&
            asOfDate == other.asOfDate &&
            exposures == other.exposures

    override fun hashCode(): Int {
        var r = accountId.hashCode()
        r = 31 * r + asOfDate.hashCode()
        r = 31 * r + exposures.hashCode()
        return r
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        private fun computeSha256(payload: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(payload.toByteArray(StandardCharsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        }
    }
}

internal object Engine15ObservedWorkloadAssembler {

    fun assemble(
        accountId: String,
        asOfDate: LocalDate,
        zoneId: ZoneId,
        canonicalActivities: List<CanonicalActivity>,
        completedPlanSessions: List<PlannedSession> = emptyList(),
        provisionalSelfReport: Map<AthleteDiscipline, ProvisionalSelfReportWorkload> = emptyMap(),
        asOfUtc: Instant? = null
    ): ObservedWorkloadState {
        RecordPolicy.identifier(accountId)

        val window7Start = asOfDate.minusDays(7)
        val window28Start = asOfDate.minusDays(28)
        val window30Start = asOfDate.minusDays(30)
        val windowEndInclusive = asOfDate.minusDays(1)

        // INV-03: Only inspect CanonicalActivities owned by accountId before asOfDate & not future UTC.
        val ownedActivities = canonicalActivities.filter { act ->
            act.accountId == accountId &&
                (asOfUtc == null || !act.startedAtUtc.isAfter(asOfUtc)) &&
                act.startedAtUtc.atZone(zoneId).toLocalDate() <= windowEndInclusive
        }

        // Collect all raw record IDs and external IDs already represented by owned canonical activities.
        val linkedRawIds = ownedActivities.flatMap { act ->
            act.rawComponents.flatMap { comp -> listOfNotNull(comp.recordId, comp.externalId) }
        }.toSet()

        // Build canonical exposures (preserving connected ambiguity group keys when unresolved).
        val ambiguityComponents = buildConnectedAmbiguityGroups(ownedActivities)
        val exposures = mutableListOf<DatedObservedSessionExposure>()

        for (act in ownedActivities) {
            val localDate = act.startedAtUtc.atZone(zoneId).toLocalDate()
            val provenance = when (act.workloadAggregationEligibility) {
                WorkloadAggregationEligibility.ELIGIBLE_VERIFIED_OBSERVED ->
                    ObservedExposureProvenance.CANONICAL_ACTIVITY_VERIFIED
                WorkloadAggregationEligibility.AMBIGUOUS_BOUNDED_ONLY ->
                    ObservedExposureProvenance.CANONICAL_ACTIVITY_AMBIGUOUS_BOUNDED
                WorkloadAggregationEligibility.BLOCKED_COLLISION ->
                    ObservedExposureProvenance.CANONICAL_ACTIVITY_COLLISION_BLOCKED
            }
            val srcIds = act.rawComponents.map { it.recordId }.toSet()
            val usableDist = if (act.telemetryQuality.distanceQuality != TelemetryQualityStatus.QUARANTINED_UNRELIABLE) {
                act.normalizedMetrics.distance
            } else {
                null
            }
            val usableDur = if (act.telemetryQuality.durationQuality != TelemetryQualityStatus.QUARANTINED_UNRELIABLE) {
                act.normalizedMetrics.duration
            } else {
                null
            }
            exposures += DatedObservedSessionExposure(
                exposureId = act.canonicalId,
                accountId = accountId,
                discipline = act.discipline,
                localDate = localDate,
                startedAtUtc = act.startedAtUtc,
                distance = usableDist,
                duration = usableDur,
                provenance = provenance,
                sourceRecordIds = srcIds,
                ambiguityGroupKey = ambiguityComponents[act.canonicalId]
            )
        }

        // Completed PlannedSessions ONLY when explicitly completed, logged, non-future, and not already linked.
        for (session in completedPlanSessions) {
            if (session.status != "completed" || session.logRevision <= 0) continue
            val linked = session.linkedActivityId
            if (linked != null && linked in linkedRawIds) continue
            val log = session.coachingLog() ?: continue
            val performedOn = log.performedOn ?: continue
            if (performedOn > windowEndInclusive) continue
            if (asOfUtc != null && log.at.isAfter(asOfUtc)) continue

            val disc = AthleteDiscipline.fromCode(session.discipline)
            val actualDist = log.distanceKm?.takeIf { it.isFinite() && it >= 0.0 }?.let(Meters::fromKilometers)
            val actualDur = log.minutes?.takeIf { it.isFinite() && it >= 0.0 }?.let(Seconds::fromMinutes)
            val prov = if (actualDist != null) {
                ObservedExposureProvenance.COMPLETED_WORKOUT_LOG_VERIFIED
            } else {
                ObservedExposureProvenance.COMPLETED_WORKOUT_LOG_UNKNOWN_DISTANCE
            }
            exposures += DatedObservedSessionExposure(
                exposureId = "wlog-${session.id}-${performedOn}",
                accountId = accountId,
                discipline = disc,
                localDate = performedOn,
                startedAtUtc = log.at,
                distance = actualDist,
                duration = actualDur,
                provenance = prov,
                sourceRecordIds = setOf(session.id)
            )
        }

        val allDisciplines = (exposures.map { it.discipline } + provisionalSelfReport.keys).toSet().sorted()
        val discMap = TreeMap<AthleteDiscipline, DisciplineObservedWorkload>()

        for (disc in allDisciplines) {
            val discExps = exposures.filter { it.discipline == disc }
            val sr = provisionalSelfReport[disc]

            val exps30 = discExps.filter { it.localDate in window30Start..windowEndInclusive }
            val exps28 = discExps.filter { it.localDate in window28Start..windowEndInclusive }
            val exps7 = discExps.filter { it.localDate in window7Start..windowEndInclusive }

            val activeExps28 = exps28.filter {
                it.provenance != ObservedExposureProvenance.CANONICAL_ACTIVITY_COLLISION_BLOCKED
            }
            val verifiedCount28 = activeExps28.count {
                it.provenance == ObservedExposureProvenance.CANONICAL_ACTIVITY_VERIFIED ||
                    it.provenance == ObservedExposureProvenance.COMPLETED_WORKOUT_LOG_VERIFIED
            }
            val ambigCount28 = exps28.count {
                it.provenance == ObservedExposureProvenance.CANONICAL_ACTIVITY_AMBIGUOUS_BOUNDED ||
                    it.provenance == ObservedExposureProvenance.CANONICAL_ACTIVITY_COLLISION_BLOCKED
            }
            val unknownDistCount28 = exps28.count {
                it.provenance == ObservedExposureProvenance.COMPLETED_WORKOUT_LOG_UNKNOWN_DISTANCE ||
                    (it.provenance == ObservedExposureProvenance.CANONICAL_ACTIVITY_VERIFIED && it.distance == null && disc == AthleteDiscipline.RUN)
            }

            val dates28 = activeExps28.map { it.localDate }.distinct().sorted()
            val spanDays = if (dates28.isEmpty()) 0 else ChronoUnit.DAYS.between(dates28.first(), asOfDate).toInt()
            val maxGapDays = if (dates28.isEmpty()) {
                null
            } else {
                var maxGap = 0
                for (i in 0 until dates28.size - 1) {
                    val gap = ChronoUnit.DAYS.between(dates28[i], dates28[i + 1]).toInt()
                    if (gap > maxGap) maxGap = gap
                }
                val trailingGap = ChronoUnit.DAYS.between(dates28.last(), asOfDate).toInt() - 1
                if (trailingGap > maxGap) maxGap = trailingGap
                maxGap.coerceAtLeast(0)
            }

            val coverage = when {
                discExps.isEmpty() && sr?.confirmedZero == true -> HistoryCoverageStatus.CONFIRMED_ZERO
                discExps.isEmpty() && sr?.weeklyVolume != null && sr.basis == RecentMileageBasis.SELF_REPORTED_AVERAGE ->
                    HistoryCoverageStatus.PROVISIONAL_SELF_REPORT
                discExps.isEmpty() -> HistoryCoverageStatus.MISSING_UNOBSERVED
                spanDays >= 21 && verifiedCount28 >= 6 && (maxGapDays == null || maxGapDays <= 10) ->
                    HistoryCoverageStatus.CONTINUOUS_COVERAGE
                else -> HistoryCoverageStatus.PARTIAL_COVERAGE
            }

            val r7Belief = aggregateDistanceBelief(exps7, disc, coverage, sr)
            val r28Belief = aggregateDistanceBelief(exps28, disc, coverage, sr)
            val wkAvgBelief = aggregateWeeklyAverageBelief(exps28, disc, coverage, r28Belief)
            val l30Belief = aggregateLongestSessionBelief(exps30, coverage)

            var srConflict = false
            if (coverage == HistoryCoverageStatus.CONTINUOUS_COVERAGE &&
                sr?.weeklyVolume != null &&
                sr.weeklyVolume.value > 0.0 &&
                wkAvgBelief is BeliefState.Estimated
            ) {
                val ratio = wkAvgBelief.estimate.value / sr.weeklyVolume.value
                if (ratio < 0.65 || ratio > 1.50) {
                    srConflict = true
                }
            }

            discMap[disc] = DisciplineObservedWorkload(
                discipline = disc,
                coverage = coverage,
                observedSpanDays = spanDays,
                maxGapDays = maxGapDays,
                rolling7dDistanceBelief = r7Belief,
                rolling28dDistanceBelief = r28Belief,
                rolling28dWeeklyAverageBelief = wkAvgBelief,
                preceding30dLongestSessionBelief = l30Belief,
                verifiedSessionCount28d = verifiedCount28,
                ambiguousCandidateCount28d = ambigCount28,
                unknownDistanceCompletedCount28d = unknownDistCount28,
                selfReportConflictFlag = srConflict,
                exposures = discExps.filter { it.localDate in window30Start..windowEndInclusive }
            )
        }

        return ObservedWorkloadState(
            accountId = accountId,
            asOfDate = asOfDate,
            zoneId = zoneId,
            disciplines = discMap,
            provisionalSelfReport = provisionalSelfReport
        )
    }

    private fun buildConnectedAmbiguityGroups(activities: List<CanonicalActivity>): Map<String, String> {
        val ambiguous = activities.filter {
            it.workloadAggregationEligibility == WorkloadAggregationEligibility.AMBIGUOUS_BOUNDED_ONLY
        }
        if (ambiguous.isEmpty()) return emptyMap()

        val byRawId = mutableMapOf<String, MutableList<CanonicalActivity>>()
        for (act in ambiguous) {
            for (comp in act.rawComponents) {
                byRawId.getOrPut(comp.recordId) { mutableListOf() }.add(act)
            }
        }
        val visited = mutableSetOf<String>()
        val result = mutableMapOf<String, String>()

        for (act in ambiguous.sortedBy { it.canonicalId }) {
            if (act.canonicalId in visited) continue
            val queue = ArrayDeque<CanonicalActivity>()
            val component = mutableListOf<CanonicalActivity>()
            queue.add(act)
            visited += act.canonicalId

            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst()
                component += cur
                for (notice in cur.ambiguities) {
                    if (notice.kind != ActivityAmbiguityKind.AMBIGUOUS_OVERLAPPING_DUPLICATE) continue
                    for (neighbor in byRawId[notice.relatedRawRecordId].orEmpty()) {
                        if (neighbor.canonicalId !in visited) {
                            visited += neighbor.canonicalId
                            queue.add(neighbor)
                        }
                    }
                }
            }
            val groupKey = "ambgrp-${component.minOf { it.canonicalId }}"
            for (member in component) {
                result[member.canonicalId] = groupKey
            }
        }
        return result
    }

    private fun aggregateDistanceBelief(
        exps: List<DatedObservedSessionExposure>,
        disc: AthleteDiscipline,
        coverage: HistoryCoverageStatus,
        sr: ProvisionalSelfReportWorkload?
    ): BeliefState<Meters> {
        if (exps.isEmpty()) {
            return when (coverage) {
                HistoryCoverageStatus.CONFIRMED_ZERO -> BeliefState.Estimated(
                    estimate = Meters.ZERO,
                    authority = EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL,
                    uncertaintyKind = UncertaintyKind.NONE
                )
                HistoryCoverageStatus.PROVISIONAL_SELF_REPORT -> sr?.weeklyVolume?.let {
                    BeliefState.PriorOnly(it, "PROVISIONAL_SELF_REPORT")
                } ?: BeliefState.Unknown("NO_OBSERVED_EXPOSURE")
                else -> BeliefState.Unknown("NO_OBSERVED_EXPOSURE")
            }
        }

        val verified = exps.filter {
            it.provenance == ObservedExposureProvenance.CANONICAL_ACTIVITY_VERIFIED ||
                it.provenance == ObservedExposureProvenance.COMPLETED_WORKOUT_LOG_VERIFIED
        }
        val ambiguous = exps.filter {
            it.provenance == ObservedExposureProvenance.CANONICAL_ACTIVITY_AMBIGUOUS_BOUNDED
        }
        val blockedCount = exps.count {
            it.provenance == ObservedExposureProvenance.CANONICAL_ACTIVITY_COLLISION_BLOCKED
        }
        val unknownDistCount = exps.count {
            it.provenance == ObservedExposureProvenance.COMPLETED_WORKOUT_LOG_UNKNOWN_DISTANCE ||
                (it.provenance == ObservedExposureProvenance.CANONICAL_ACTIVITY_VERIFIED && it.distance == null && disc == AthleteDiscipline.RUN)
        }

        val verifiedSumM = verified.mapNotNull { it.distance?.value }.sum()
        val ambigGroups = ambiguous.groupBy { it.ambiguityGroupKey ?: it.exposureId }.values
        var ambigMinM = 0.0
        var ambigMaxM = 0.0
        var ambigMissingDist = false

        for (group in ambigGroups) {
            val dists = group.mapNotNull { it.distance?.value }
            if (dists.size < group.size) {
                ambigMissingDist = true
            }
            if (dists.isNotEmpty()) {
                ambigMinM += dists.min()
                ambigMaxM += dists.sum()
            }
        }

        if (ambiguous.isEmpty() && blockedCount == 0 && unknownDistCount == 0) {
            return if (verified.isNotEmpty()) {
                BeliefState.Estimated(
                    estimate = Meters.of(verifiedSumM),
                    authority = EvidenceAuthorityClass.SENSOR_SUPPORTED,
                    uncertaintyKind = UncertaintyKind.NONE
                )
            } else {
                BeliefState.Unknown("NO_DISTANCE_TELEMETRY")
            }
        }

        val lo = verifiedSumM + ambigMinM
        val hi: Meters? = if (blockedCount > 0 || unknownDistCount > 0 || ambigMissingDist) {
            null
        } else {
            Meters.of(verifiedSumM + ambigMaxM)
        }
        return if (lo > 0.0 || hi != null) {
            BeliefState.Bounded(
                lower = Meters.of(lo),
                upper = hi,
                authority = EvidenceAuthorityClass.SENSOR_SUPPORTED,
                uncertaintyKind = UncertaintyKind.COACHING_GUIDANCE_BAND
            )
        } else {
            BeliefState.Unknown("UNKNOWN_DISTANCE_OR_BLOCKED_COLLISION")
        }
    }

    private fun aggregateWeeklyAverageBelief(
        exps28: List<DatedObservedSessionExposure>,
        disc: AthleteDiscipline,
        coverage: HistoryCoverageStatus,
        r28Belief: BeliefState<Meters>
    ): BeliefState<Meters> = when {
        coverage == HistoryCoverageStatus.CONFIRMED_ZERO -> BeliefState.Estimated(
            estimate = Meters.ZERO,
            authority = EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL,
            uncertaintyKind = UncertaintyKind.NONE
        )
        coverage == HistoryCoverageStatus.CONTINUOUS_COVERAGE && r28Belief is BeliefState.Estimated ->
            BeliefState.Estimated(
                estimate = Meters.of(r28Belief.estimate.value / 4.0),
                authority = EvidenceAuthorityClass.SENSOR_SUPPORTED,
                uncertaintyKind = UncertaintyKind.COACHING_GUIDANCE_BAND,
                guidanceSpreadFactor = 1.0
            )
        r28Belief is BeliefState.Estimated -> {
            // Partial history MUST NEVER collapse to an exact complete-week average.
            if (r28Belief.estimate.value > 0.0) {
                BeliefState.Bounded(
                    lower = Meters.of(r28Belief.estimate.value / 4.0),
                    upper = null,
                    authority = EvidenceAuthorityClass.SENSOR_SUPPORTED,
                    uncertaintyKind = UncertaintyKind.COACHING_GUIDANCE_BAND
                )
            } else {
                BeliefState.Unknown("PARTIAL_HISTORY_ZERO_DISTANCE")
            }
        }
        r28Belief is BeliefState.Bounded -> BeliefState.Bounded(
            lower = r28Belief.lower?.let { Meters.of(it.value / 4.0) },
            upper = if (coverage == HistoryCoverageStatus.CONTINUOUS_COVERAGE) {
                r28Belief.upper?.let { Meters.of(it.value / 4.0) }
            } else {
                null
            },
            authority = r28Belief.authority,
            uncertaintyKind = r28Belief.uncertaintyKind
        )
        else -> {
            val verifiedRunSum = exps28.filter {
                it.discipline == disc && it.provenance == ObservedExposureProvenance.CANONICAL_ACTIVITY_VERIFIED
            }.mapNotNull { it.distance?.value }.sum()
            if (verifiedRunSum > 0.0) {
                BeliefState.Bounded(
                    lower = Meters.of(verifiedRunSum / 4.0),
                    upper = null,
                    authority = EvidenceAuthorityClass.SENSOR_SUPPORTED,
                    uncertaintyKind = UncertaintyKind.COACHING_GUIDANCE_BAND
                )
            } else {
                BeliefState.Unknown("INSUFFICIENT_OBSERVED_WEEKLY_HISTORY")
            }
        }
    }

    private fun aggregateLongestSessionBelief(
        exps30: List<DatedObservedSessionExposure>,
        coverage: HistoryCoverageStatus
    ): BeliefState<Meters> {
        if (exps30.isEmpty()) {
            return if (coverage == HistoryCoverageStatus.CONFIRMED_ZERO) {
                BeliefState.Estimated(
                    estimate = Meters.ZERO,
                    authority = EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL,
                    uncertaintyKind = UncertaintyKind.NONE
                )
            } else {
                BeliefState.Unknown("NO_PRECEDING_30D_OBSERVED_SESSIONS")
            }
        }

        val verifiedMax = exps30.filter {
            it.provenance == ObservedExposureProvenance.CANONICAL_ACTIVITY_VERIFIED ||
                it.provenance == ObservedExposureProvenance.COMPLETED_WORKOUT_LOG_VERIFIED
        }.mapNotNull { it.distance?.value }.maxOrNull()

        val ambigDistances = exps30.filter {
            it.provenance == ObservedExposureProvenance.CANONICAL_ACTIVITY_AMBIGUOUS_BOUNDED
        }.mapNotNull { it.distance?.value }

        val blockedCount = exps30.count {
            it.provenance == ObservedExposureProvenance.CANONICAL_ACTIVITY_COLLISION_BLOCKED
        }
        val unknownCount = exps30.count {
            it.provenance == ObservedExposureProvenance.COMPLETED_WORKOUT_LOG_UNKNOWN_DISTANCE
        }

        val ambigMin = ambigDistances.minOrNull()
        val ambigMax = ambigDistances.maxOrNull()

        if (ambigDistances.isEmpty() && blockedCount == 0 && unknownCount == 0) {
            return if (verifiedMax != null) {
                BeliefState.Estimated(
                    estimate = Meters.of(verifiedMax),
                    authority = EvidenceAuthorityClass.SENSOR_SUPPORTED,
                    uncertaintyKind = UncertaintyKind.NONE
                )
            } else {
                BeliefState.Unknown("NO_LONGEST_SESSION_DISTANCE")
            }
        }

        val loVal = listOfNotNull(verifiedMax, ambigMin).maxOrNull()
        val hiVal = if (blockedCount > 0 || unknownCount > 0) null else listOfNotNull(verifiedMax, ambigMax).maxOrNull()
        return if (loVal != null) {
            BeliefState.Bounded(
                lower = Meters.of(loVal),
                upper = hiVal?.let { Meters.of(max(loVal, it)) },
                authority = EvidenceAuthorityClass.SENSOR_SUPPORTED,
                uncertaintyKind = UncertaintyKind.COACHING_GUIDANCE_BAND
            )
        } else {
            BeliefState.Unknown("UNKNOWN_LONGEST_SESSION_DISTANCE")
        }
    }
}

internal object Engine15ProjectedWorkloadAssembler {

    fun assembleFromPlans(
        accountId: String,
        asOfDate: LocalDate,
        plans: List<PlanSnapshot>
    ): ProjectedWorkloadState {
        RecordPolicy.identifier(accountId)
        val projected = mutableListOf<DatedProjectedSessionExposure>()

        for (plan in plans) {
            if (plan.owner != accountId) continue
            val authority = when (plan.status) {
                "approved" -> ProjectedWorkloadAuthority.ACCEPTED_PLAN_FUTURE
                "draft" -> ProjectedWorkloadAuthority.PROPOSED_PLAN_PREVIEW
                else -> ProjectedWorkloadAuthority.HYPOTHETICAL_FEASIBILITY
            }
            for (session in plan.sessions) {
                if (session.status == "completed" || session.status == "skipped" || session.status == "missed") continue
                if (session.date < asOfDate) continue
                val disc = AthleteDiscipline.fromCode(session.discipline)
                val distMeters = session.distanceKm?.takeIf { it.isFinite() && it >= 0.0 }?.let(Meters::fromKilometers)
                val durSeconds = session.minutes.takeIf { it >= 0 }?.let { Seconds.fromMinutes(it.toDouble()) }
                projected += DatedProjectedSessionExposure(
                    projectionId = "${plan.id}:${session.id}:${session.date}",
                    accountId = accountId,
                    planId = plan.id,
                    sessionId = session.id,
                    discipline = disc,
                    scheduledDate = session.date,
                    authority = authority,
                    expectedDistanceMin = distMeters,
                    expectedDistanceMax = distMeters,
                    expectedDurationMin = durSeconds,
                    expectedDurationMax = durSeconds
                )
            }
        }

        return ProjectedWorkloadState(
            accountId = accountId,
            asOfDate = asOfDate,
            exposures = projected
        )
    }
}
