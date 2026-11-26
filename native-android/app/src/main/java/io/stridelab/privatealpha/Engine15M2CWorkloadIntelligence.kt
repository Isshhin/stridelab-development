package io.stridelab.privatealpha

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.TreeMap

/**
 * Single authoritative reconciled evidence inventory builder shared across all M2-C sub-engines
 * (closes M2C-B02, M2C-B03, M2C-B05, M2C-C05, M2C-C06).
 *
 * Guarantees:
 * - Pinned [d-30..d-1] running exposures and [d-28..d-1] non-running exposures come strictly from
 *   frozen ObservedWorkloadState without losing pinned sessions that lack supplementary enrichment.
 * - Opaque upstream record IDs are preserved explicitly without fabricating provider-qualified aliases.
 * - Independent per-metric duplicate conflict detection (RPE, intensity breakdown, downhill stream)
 *   quarantines only the conflicting enrichment while preserving valid distance & duration.
 * - Total deterministic comparators over all retained metadata ensure strict permutation invariance.
 */
internal object Engine15M2CReconciledEvidenceInventoryBuilder {

    private data class MergedSupplementaryGroup(
        val canonicalActivity: CanonicalActivity,
        val modality: M2CModalityKind,
        val internalLoad: M2CSessionInternalLoadInput?,
        val internalLoadConflict: Boolean,
        val domainBreakdown: M2CWorkoutDomainBreakdownInput?,
        val domainBreakdownConflict: Boolean,
        val runningStream: M2BRunningStream?,
        val downhillStreamConflict: Boolean
    )

    fun reconcile(
        context: M2BEvaluationContext,
        observedWorkload: ObservedWorkloadState,
        supplementaryActivities: List<M2CCompletedActivityInput>,
        supplementaryDownhillStreams: List<M2BRunningStream>
    ): M2CReconciledEvidenceInventory {
        val flags = linkedSetOf<String>()
        val bounds28 = Engine15M2CRollingExposureEngine.boundsFor(context, M2CRollingWindowKind.TRAILING_28D)
        val bounds30 = Engine15M2CRollingExposureEngine.boundsFor(context, M2CRollingWindowKind.PRECEDING_30D)
        val bounds90 = Engine15M2CRollingExposureEngine.boundsFor(context, M2CRollingWindowKind.PRECEDING_90D)

        val allInputs = supplementaryActivities + supplementaryDownhillStreams.mapNotNull { stream ->
            when {
                stream.activity.accountId != context.accountId -> {
                    flags += "FOREIGN_ACCOUNT_DOWNHILL_STREAM_EXCLUDED"; null
                }
                stream.sourceRecord() == null -> {
                    flags += "UNRESOLVABLE_DOWNHILL_SOURCE_RECORD_EXCLUDED"; null
                }
                else -> M2CCompletedActivityInput(canonicalActivity = stream.activity, runningStream = stream)
            }
        }

        val scoped = allInputs.filter { item ->
            when {
                item.canonicalActivity.accountId != context.accountId -> { flags += "FOREIGN_ACCOUNT_ACTIVITY_EXCLUDED"; false }
                item.isProjected -> { flags += "PROJECTED_ACTIVITY_EXCLUDED_FROM_OBSERVED"; false }
                else -> {
                    val act = item.canonicalActivity
                    val completed = Engine15M2CCanonical.completedInstant(
                        act.startedAtUtc, act.normalizedMetrics.duration?.value ?: 0.0
                    )
                    val date = act.startedAtUtc.atZone(context.zone).toLocalDate()
                    when {
                        completed == null -> { flags += "INVALID_SUPPLEMENTARY_DURATION_EXCLUDED"; false }
                        completed.isAfter(context.asOf) -> { flags += "FUTURE_COMPLETION_ACTIVITY_EXCLUDED"; false }
                        act.discipline == AthleteDiscipline.RUN -> date == context.localDate || bounds90.contains(date)
                        else -> bounds28.contains(date)
                    }
                }
            }
        }

        // Cross-canonical source-identity collision detection (aliases + sha256).
        val byCanon = scoped.groupBy { it.canonicalActivity.canonicalId }
        val identityOwners = byCanon.flatMap { (cid, group) ->
            group.flatMap { item ->
                Engine15M2CCanonical.sourceIdentityTokens(item.canonicalActivity)
            }.distinct().map { it to cid }
        }.groupBy({ it.first }, { it.second })
        val collidedCanonIds = identityOwners.values.filter { it.distinct().size > 1 }.flatten().toSortedSet()
        if (collidedCanonIds.isNotEmpty()) flags += "CROSS_CANONICAL_SOURCE_IDENTITY_COLLISION_EXCLUDED"

        // Independent per-metric conflict detection + deterministic total-order merge (M2C-B03, M2C-C06).
        val mergedByCanon = TreeMap<String, MergedSupplementaryGroup>()
        for ((cid, group) in byCanon.toSortedMap()) {
            if (cid in collidedCanonIds) continue
            if (group.map { it.canonicalActivity.contentHash }.distinct().size > 1) {
                flags += "CONTRADICTORY_CANONICAL_PROJECTION_EXCLUDED"
                collidedCanonIds += cid
                continue
            }
            if (group.size > 1) flags += "DUPLICATE_CANONICAL_INPUT_RECONCILED"
            val act = group.first().canonicalActivity

            val distinctMods = group.map { it.resolvedModality }.distinct()
            val resolvedMod = if (distinctMods.size == 1) distinctMods.single()
            else {
                flags += "CONTRADICTORY_MODALITY_REFINEMENT_REVERTED_TO_INFERRED"
                M2CModalityKind.infer(act.discipline, act.rawActivityType)
            }

            // 1. Session RPE reconciliation (total comparator across all retained metadata!)
            val rpeWithVal = group.mapNotNull { it.internalLoad }.filter { it.cr10Rpe != null }
            val distinctRpeVals = rpeWithVal.map { it.cr10Rpe!! }.distinct()
            val (mergedRpe, rpeConflict) = when {
                distinctRpeVals.size >= 2 -> {
                    flags += "CONTRADICTORY_DUPLICATE_RPE_EXCLUDED"
                    null to true
                }
                rpeWithVal.isNotEmpty() -> {
                    rpeWithVal.sortedWith(
                        compareByDescending<M2CSessionInternalLoadInput> { it.trustTier.ordinal }
                            .thenByDescending { it.recordedAtUtc }
                            .thenBy { Engine15M2CCanonical.normalizeAlias(it.sourceAlias) }
                            .thenBy { it.canonicalRepresentation() }
                    ).first() to false
                }
                else -> group.mapNotNull { it.internalLoad }.sortedWith(
                    compareByDescending<M2CSessionInternalLoadInput> { it.trustTier.ordinal }
                        .thenByDescending { it.recordedAtUtc }
                        .thenBy { Engine15M2CCanonical.normalizeAlias(it.sourceAlias) }
                        .thenBy { it.canonicalRepresentation() }
                ).firstOrNull() to false
            }

            // 2. Intensity-domain breakdown reconciliation (total comparator across all 10 metrics + metadata!)
            val bdInputs = group.mapNotNull { it.domainBreakdown }
            val distinctBdMetrics = bdInputs.map {
                listOf(
                    it.moderateWorkSeconds, it.heavyWorkSeconds, it.severeWorkSeconds,
                    it.neuromuscularWorkSeconds, it.warmUpCoolDownRecoverySeconds,
                    it.moderateWorkMeters, it.heavyWorkMeters, it.severeWorkMeters,
                    it.neuromuscularWorkMeters, it.warmUpCoolDownRecoveryMeters
                )
            }.distinct()
            val (mergedBd, bdConflict) = when {
                distinctBdMetrics.size >= 2 -> {
                    flags += "CONTRADICTORY_DUPLICATE_DOMAIN_BREAKDOWN_EXCLUDED"
                    null to true
                }
                bdInputs.isNotEmpty() -> {
                    bdInputs.sortedWith(
                        compareByDescending<M2CWorkoutDomainBreakdownInput> { it.trustTier.ordinal }
                            .thenByDescending { it.recordedAtUtc }
                            .thenBy { Engine15M2CCanonical.normalizeAlias(it.sourceAlias) }
                            .thenBy { it.canonicalRepresentation() }
                    ).first() to false
                }
                else -> null to false
            }

            // 3. Downhill stream reconciliation (M2C-B05: full structural key including sourceQuality, distanceSemantics, distanceTolerance!)
            val streamInputs = group.mapNotNull { it.runningStream }
            val distinctStreams = streamInputs.distinctBy { s ->
                listOf(
                    s.activity.contentHash, s.source.canonicalToken(), s.surface, s.segments,
                    s.recording?.origin, s.recording?.samples, s.recording?.sourceQuality,
                    s.recording?.distanceSemantics, s.recording?.distanceTolerance
                )
            }
            val (mergedStream, streamConflict) = when {
                distinctStreams.size >= 2 -> {
                    flags += "CONTRADICTORY_DUPLICATE_DOWNHILL_STREAM_EXCLUDED"
                    null to true
                }
                distinctStreams.size == 1 -> distinctStreams.single() to false
                else -> null to false
            }

            mergedByCanon[cid] = MergedSupplementaryGroup(
                canonicalActivity = act, modality = resolvedMod,
                internalLoad = mergedRpe, internalLoadConflict = rpeConflict,
                domainBreakdown = mergedBd, domainBreakdownConflict = bdConflict,
                runningStream = mergedStream, downhillStreamConflict = streamConflict
            )
        }

        // 4. Reconcile pinned M1C [d-30..d-1] running exposures (M2C-B02, M2C-C05).
        val m1cRunExposures = observedWorkload.disciplines[AthleteDiscipline.RUN]?.exposures.orEmpty()
        val pinnedRunIds = m1cRunExposures.map { it.exposureId }.toSet()
        val pinnedRunRawIds = m1cRunExposures.flatMap { it.sourceRecordIds }.toSet()
        val pinned30Entries = mutableListOf<M2CReconciledRunningEntry>()

        for (exp in m1cRunExposures) {
            if (!bounds30.contains(exp.localDate)) continue
            val matched = reconcilePinned(exp, mergedByCanon[exp.exposureId], context, flags)
            val act = matched?.canonicalActivity
            val completedUtc = M2CInventoryValidator.completedInstantForPinnedExposure(exp)
            if (completedUtc == null) {
                flags += "INVALID_PINNED_DURATION_EXCLUDED"
                continue
            }
            if (completedUtc.isAfter(context.asOf)) {
                flags += "M1C_EXPOSURE_COMPLETION_AFTER_EVALUATION_EXCLUDED"
                continue
            }
            val compLocalDate = completedUtc.atZone(context.zone).toLocalDate()
            val crossMidnight = compLocalDate != exp.localDate
            if (crossMidnight) flags += "CROSS_MIDNIGHT_DATE_ATTRIBUTION_PRESERVED_PER_M1C_START_DATE"

            val fromPlanLog = exp.provenance == ObservedExposureProvenance.COMPLETED_WORKOUT_LOG_VERIFIED ||
                exp.provenance == ObservedExposureProvenance.COMPLETED_WORKOUT_LOG_UNKNOWN_DISTANCE
            val eligibility = M2CInventoryValidator.eligibilityFromProvenance(exp.provenance)
            val trueAliases = act?.let(Engine15M2CCanonical::aliasesFromCanonicalActivity).orEmpty()
            val opaqueIds = if (trueAliases.isEmpty()) exp.sourceRecordIds else emptySet()

            val record = M2CVerifiedRunningSessionRecord(
                canonicalActivityId = exp.exposureId,
                accountId = context.accountId,
                startedAtUtc = exp.startedAtUtc,
                completedAtUtc = completedUtc,
                attributedLocalDate = exp.localDate,
                completedLocalDate = compLocalDate,
                crossMidnightStartToEnd = crossMidnight,
                distance = exp.distance,
                duration = exp.duration,
                distanceQuality = act?.telemetryQuality?.distanceQuality
                    ?: if (exp.distance != null) TelemetryQualityStatus.VALID_OBSERVED else TelemetryQualityStatus.MISSING_TELEMETRY,
                durationQuality = act?.telemetryQuality?.durationQuality
                    ?: if (exp.duration != null) TelemetryQualityStatus.VALID_OBSERVED else TelemetryQualityStatus.MISSING_TELEMETRY,
                hrQuality = act?.telemetryQuality?.hrQuality ?: TelemetryQualityStatus.MISSING_TELEMETRY,
                workloadEligibility = eligibility,
                fromCompletedPlanLogOnly = fromPlanLog,
                sameDayAsEvaluation = false,
                ambiguityGroupKey = exp.ambiguityGroupKey,
                sourceAliases = trueAliases,
                opaqueUpstreamRecordIds = opaqueIds
            )
            pinned30Entries += M2CReconciledRunningEntry(
                record = record, canonicalActivity = act,
                internalLoad = matched?.internalLoad, internalLoadConflict = matched?.internalLoadConflict ?: false,
                domainBreakdown = matched?.domainBreakdown, domainBreakdownConflict = matched?.domainBreakdownConflict ?: false,
                runningStream = matched?.runningStream, downhillStreamConflict = matched?.downhillStreamConflict ?: false
            )
        }

        // 5. Reconcile ALL pinned non-running M1C exposures [d-28..d-1] (M2C-C05: never drop unenriched pinned sessions!).
        val nonRunningEntries = mutableListOf<M2CReconciledNonRunningEntry>()
        val pinnedNonRunIdsByDisc = mutableMapOf<AthleteDiscipline, MutableSet<String>>()
        val pinnedNonRunRawByDisc = mutableMapOf<AthleteDiscipline, MutableSet<String>>()

        for (disc in listOf(AthleteDiscipline.STRENGTH, AthleteDiscipline.CALISTHENICS, AthleteDiscipline.CONDITIONING)) {
            val discExps = observedWorkload.disciplines[disc]?.exposures.orEmpty()
            val idSet = pinnedNonRunIdsByDisc.getOrPut(disc) { linkedSetOf() }
            val rawSet = pinnedNonRunRawByDisc.getOrPut(disc) { linkedSetOf() }
            discExps.forEach { e -> idSet += e.exposureId; rawSet += e.sourceRecordIds }

            for (exp in discExps) {
                if (!bounds28.contains(exp.localDate)) continue
                val matched = reconcilePinned(exp, mergedByCanon[exp.exposureId], context, flags)
                val act = matched?.canonicalActivity
                val completedUtc = M2CInventoryValidator.completedInstantForPinnedExposure(exp)
                if (completedUtc == null) {
                    flags += "INVALID_PINNED_DURATION_EXCLUDED"
                    continue
                }
                if (completedUtc.isAfter(context.asOf)) {
                    flags += "M1C_NON_RUNNING_COMPLETION_AFTER_EVALUATION_EXCLUDED"
                    continue
                }
                val trueAliases = act?.let(Engine15M2CCanonical::aliasesFromCanonicalActivity).orEmpty()
                val opaqueIds = if (trueAliases.isEmpty()) exp.sourceRecordIds else emptySet()
                val modality = matched?.modality ?: when (disc) {
                    AthleteDiscipline.STRENGTH -> M2CModalityKind.STRENGTH
                    AthleteDiscipline.CALISTHENICS -> M2CModalityKind.CALISTHENICS
                    else -> M2CModalityKind.OTHER_CONDITIONING
                }
                nonRunningEntries += M2CReconciledNonRunningEntry(
                    exposureId = exp.exposureId, accountId = context.accountId, discipline = disc, modality = modality,
                    startedAtUtc = exp.startedAtUtc, completedAtUtc = completedUtc, attributedLocalDate = exp.localDate,
                    distance = exp.distance, duration = exp.duration,
                    distanceQuality = act?.telemetryQuality?.distanceQuality
                        ?: if (exp.distance != null) TelemetryQualityStatus.VALID_OBSERVED else TelemetryQualityStatus.MISSING_TELEMETRY,
                    durationQuality = act?.telemetryQuality?.durationQuality
                        ?: if (exp.duration != null) TelemetryQualityStatus.VALID_OBSERVED else TelemetryQualityStatus.MISSING_TELEMETRY,
                    workloadEligibility = M2CInventoryValidator.eligibilityFromProvenance(exp.provenance),
                    ambiguityGroupKey = exp.ambiguityGroupKey,
                    canonicalActivity = act,
                    internalLoad = matched?.internalLoad,
                    internalLoadConflict = matched?.internalLoadConflict ?: false,
                    pinnedInObservedWorkload = true,
                    sourceAliases = trueAliases,
                    opaqueUpstreamRecordIds = opaqueIds
                )
            }
        }

        // 6. Partition remaining supplementary inputs cleanly (same-day, extended [d-90..d-31], or unpinned-excluded).
        val sameDayEntries = mutableListOf<M2CReconciledRunningEntry>()
        val extendedEntries = mutableListOf<M2CReconciledRunningEntry>()
        val unpinnedExcluded = linkedSetOf<String>()

        val pinnedIds = pinnedRunIds + pinnedNonRunIdsByDisc.values.flatten()
        val pinnedRawIds = pinnedRunRawIds + pinnedNonRunRawByDisc.values.flatten()
        val ambiguityGroups = buildConnectedAmbiguityGroups(mergedByCanon.values.map { it.canonicalActivity })
        for ((cid, group) in mergedByCanon) {
            val act = group.canonicalActivity
            if (cid in pinnedIds || act.rawComponents.any { it.recordId in pinnedRawIds }) continue
            val dur = act.normalizedMetrics.duration
            val completedUtc = if (dur != null && dur.value > 0.0) {
                Engine15M2CCanonical.completedInstant(act.startedAtUtc, dur.value) ?: continue
            } else act.startedAtUtc
            val startLocal = act.startedAtUtc.atZone(context.zone).toLocalDate()
            val compLocal = completedUtc.atZone(context.zone).toLocalDate()

            if (act.discipline == AthleteDiscipline.RUN) {
                if (startLocal == context.localDate) {
                    val rec = buildRunningRecordFromCanonical(act, context.accountId, startLocal, compLocal, completedUtc, ambiguityGroups[cid], sameDay = true)
                    sameDayEntries += M2CReconciledRunningEntry(
                        rec, act, group.internalLoad, group.internalLoadConflict,
                        group.domainBreakdown, group.domainBreakdownConflict,
                        group.runningStream, group.downhillStreamConflict
                    )
                } else if (bounds30.contains(startLocal)) {
                    unpinnedExcluded += cid
                    flags += "SUPPLEMENTARY_RUNNING_ABSENT_FROM_PINNED_OBSERVED_WORKLOAD"
                } else if (bounds90.contains(startLocal)) {
                    val rec = buildRunningRecordFromCanonical(act, context.accountId, startLocal, compLocal, completedUtc, ambiguityGroups[cid], sameDay = false)
                    extendedEntries += M2CReconciledRunningEntry(
                        rec, act, group.internalLoad, group.internalLoadConflict,
                        group.domainBreakdown, group.domainBreakdownConflict,
                        group.runningStream, group.downhillStreamConflict
                    )
                }
            } else if (bounds28.contains(startLocal)) {
                val discIds = pinnedNonRunIdsByDisc[act.discipline].orEmpty()
                if (discIds.isNotEmpty()) {
                    unpinnedExcluded += cid
                    flags += "UNPINNED_NON_RUNNING_ACTIVITY_EXCLUDED"
                } else {
                    flags += "UNPINNED_NON_RUNNING_DISCIPLINE_ENRICHMENT_DESCRIPTIVE"
                    nonRunningEntries += M2CReconciledNonRunningEntry(
                        exposureId = cid, accountId = context.accountId, discipline = act.discipline,
                        modality = group.modality, startedAtUtc = act.startedAtUtc, completedAtUtc = completedUtc,
                        attributedLocalDate = startLocal,
                        distance = if (act.telemetryQuality.distanceQuality != TelemetryQualityStatus.QUARANTINED_UNRELIABLE) act.normalizedMetrics.distance else null,
                        duration = if (act.telemetryQuality.durationQuality != TelemetryQualityStatus.QUARANTINED_UNRELIABLE) dur else null,
                        distanceQuality = act.telemetryQuality.distanceQuality,
                        durationQuality = act.telemetryQuality.durationQuality,
                        workloadEligibility = act.workloadAggregationEligibility,
                        ambiguityGroupKey = ambiguityGroups[cid], canonicalActivity = act,
                        internalLoad = group.internalLoad, internalLoadConflict = group.internalLoadConflict,
                        pinnedInObservedWorkload = false,
                        sourceAliases = Engine15M2CCanonical.aliasesFromCanonicalActivity(act),
                        opaqueUpstreamRecordIds = emptySet()
                    )
                }
            }
        }

        return M2CReconciledEvidenceInventory.create(
            context = context,
            observedWorkloadContentHash = observedWorkload.contentHash,
            pinned30dRunningEntries = pinned30Entries,
            sameDayCompletedRunningEntries = sameDayEntries,
            extendedHistorical31to90RunningEntries = extendedEntries,
            nonRunningEntries28d = nonRunningEntries,
            collidedCanonicalActivityIds = collidedCanonIds,
            unpinnedExcludedCanonicalIds = unpinnedExcluded,
            diagnosticFlags = flags
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

    private fun buildRunningRecordFromCanonical(
        act: CanonicalActivity,
        accountId: String,
        startLocal: LocalDate,
        compLocal: LocalDate,
        completedUtc: Instant,
        ambiguityGroupKey: String?,
        sameDay: Boolean
    ): M2CVerifiedRunningSessionRecord = M2CVerifiedRunningSessionRecord(
        canonicalActivityId = act.canonicalId, accountId = accountId,
        startedAtUtc = act.startedAtUtc, completedAtUtc = completedUtc,
        attributedLocalDate = startLocal, completedLocalDate = compLocal,
        crossMidnightStartToEnd = compLocal != startLocal,
        distance = if (act.telemetryQuality.distanceQuality != TelemetryQualityStatus.QUARANTINED_UNRELIABLE) act.normalizedMetrics.distance else null,
        duration = if (act.telemetryQuality.durationQuality != TelemetryQualityStatus.QUARANTINED_UNRELIABLE) act.normalizedMetrics.duration else null,
        distanceQuality = act.telemetryQuality.distanceQuality,
        durationQuality = act.telemetryQuality.durationQuality,
        hrQuality = act.telemetryQuality.hrQuality,
        workloadEligibility = act.workloadAggregationEligibility,
        fromCompletedPlanLogOnly = false, sameDayAsEvaluation = sameDay, ambiguityGroupKey = ambiguityGroupKey,
        sourceAliases = Engine15M2CCanonical.aliasesFromCanonicalActivity(act)
    )

    private fun reconcilePinned(
        exposure: DatedObservedSessionExposure,
        candidate: MergedSupplementaryGroup?,
        context: M2BEvaluationContext,
        flags: MutableSet<String>
    ): MergedSupplementaryGroup? {
        if (candidate == null) return null
        val activity = candidate.canonicalActivity
        if (activity.canonicalId == exposure.exposureId &&
            M2CInventoryValidator.canonicalMatchesPinnedExposure(activity, exposure, context.zone)
        ) return candidate
        flags += "CONTRADICTORY_SUPPLEMENTARY_ENRICHMENT_QUARANTINED"
        return null
    }
}

// =============================================================================
// SECTION 2: REQUEST, IMMUTABLE ASSESSMENT & ORCHESTRATOR
// =============================================================================

internal data class M2CWorkloadIntelligenceRequest(
    val context: M2BEvaluationContext,
    val observedWorkload: ObservedWorkloadState,
    val intensityCalibration: Engine15M2BIntensityCalibrator,
    val athleteSnapshot: AthleteStateSnapshot? = null,
    val projectedWorkload: ProjectedWorkloadState? = null,
    val supplementaryActivities: List<M2CCompletedActivityInput> = emptyList(),
    val supplementaryDownhillStreams: List<M2BRunningStream> = emptyList(),
    val qualityObservations: List<M2CQualityFamilyObservationInput> = emptyList(),
    val familySelfReports: List<M2CFamilySelfReportInput> = emptyList(),
    val weeklyConfirmations: List<M2CWeeklyConfirmationInput> = emptyList(),
    val confirmedInterruption: M2CConfirmedInterruptionInput? = null,
    val evaluatedSingleSessionDistance: Meters? = null,
    val singleSessionOverrideAcknowledgment: M2CSingleSessionOverrideAcknowledgment? = null,
    val longRunDurationCap: Seconds = Seconds.of(Engine15M2CPolicyRegistry.POL_030_SOFT_LONG_RUN_DURATION_CAP_SECONDS),
    val longRunDistanceCap: Meters? = null,
    val longRunStoppingEnforcementMode: M2CStoppingEnforcementMode = M2CStoppingEnforcementMode.PRESCRIBED_INSTRUCTION,
    val longRunStoppingEvidence: M2CStoppingVerificationEvidence = M2CStoppingVerificationEvidence.NonePendingExecution
) {
    init {
        require(observedWorkload.accountId == context.accountId) { "INV-03: observedWorkload accountId mismatch" }
        require(observedWorkload.asOfDate == context.localDate && observedWorkload.zoneId == context.zone) {
            "M2C-A06: observedWorkload (asOfDate, zoneId) must match context"
        }
        require(intensityCalibration.context == context) { "M2-B calibration context must match M2-C context" }
        if (intensityCalibration.observedWorkloadContentHash != null) {
            require(intensityCalibration.observedWorkloadContentHash == observedWorkload.contentHash) {
                "M2C-B02: M2-B observedWorkloadContentHash contradicts request.observedWorkload"
            }
        }
        if (athleteSnapshot != null) {
            require(athleteSnapshot.accountId == context.accountId && athleteSnapshot.evaluatedAtUtc == context.asOf)
            if (athleteSnapshot.observedWorkload != null) {
                require(athleteSnapshot.observedWorkload.contentHash == observedWorkload.contentHash)
            }
        }
        if (projectedWorkload != null) {
            require(projectedWorkload.accountId == context.accountId)
        }
    }
}

internal class Engine15M2CWorkloadAssessment private constructor(
    val handoff: M2CDownstreamWorkloadHandoff,
    val reconciledInventory: M2CReconciledEvidenceInventory,
    val singleSessionAssessment: M2CSingleSessionExposureAssessment?,
    val contentHash: String
) {
    val productionAuthorized: Boolean get() = false
    val workoutPrescriptionAuthorized: Boolean get() = false
    val hrZoneAuthorized: Boolean get() = false
    val independentlyClearsPersistentHold: Boolean get() = false

    internal companion object {
        internal fun create(
            handoff: M2CDownstreamWorkloadHandoff,
            inventory: M2CReconciledEvidenceInventory,
            singleSessionAssessment: M2CSingleSessionExposureAssessment?
        ): Engine15M2CWorkloadAssessment {
            require(handoff.context == inventory.context &&
                handoff.upstreamObservedWorkloadHash == inventory.observedWorkloadContentHash &&
                handoff.reconciledInventoryContentHash == inventory.contentHash &&
                handoff.workloadVector.running90dAnchor.reconciledInventoryContentHash == inventory.contentHash
            ) { "Handoff and running anchor must describe the reconciled inventory" }
            if (singleSessionAssessment != null) {
                require(singleSessionAssessment.reconciledInventoryContentHash == inventory.contentHash &&
                    singleSessionAssessment.evaluatedFor == inventory.context &&
                    singleSessionAssessment.observedWorkloadContentHash == inventory.observedWorkloadContentHash
                ) { "Single-session assessment belongs to a different inventory" }
            }
            val combinedHash = Engine15M2CCanonical.sha256Hex(
                Engine15M2CCanonical.frame(
                    "M2C-ASSESS", handoff.contentHash, inventory.contentHash,
                    singleSessionAssessment?.canonicalRepresentation()
                )
            )
            return Engine15M2CWorkloadAssessment(handoff, inventory, singleSessionAssessment, combinedHash)
        }
    }

    override fun equals(other: Any?): Boolean = other is Engine15M2CWorkloadAssessment && contentHash == other.contentHash
    override fun hashCode(): Int = contentHash.hashCode()
}

/**
 * Pure, deterministic M2-C Exact-Date Multidimensional Workload Intelligence orchestrator.
 * Consumes pinned M1 ObservedWorkloadState, AthleteStateSnapshot, and accepted M2-B calibration,
 * preserving monotonic restrictions (M2C-C04), complete multimodal reconciliation (M2C-C05),
 * and full canonical provenance (M2C-C06) without granting production authority.
 */
internal object Engine15M2CWorkloadIntelligence {

    private val UNRESOLVED_POLICY_DEPS: Set<String> = setOf(
        "M1D_PERSISTENT_H1_H4_RESTRICTION_STORE_UNCONFIRMED",
        "M1E_IMMUTABLE_PLAN_VERSION_OCC_STORE_UNCONFIRMED",
        "M1F_PERSISTENCE_INTEGRATION_UNCONFIRMED",
        "M2C_WEEKLY_CV_VOLATILITY_THRESHOLD_UNAPPROVED",
        "M2C_DOWNHILL_ELEVATION_SMOOTHING_AND_GAP_POLICY_UNAPPROVED",
        "M2C_QUALITY_FAMILY_UNLOCK_COUNT_POLICY_UNAPPROVED",
        "M2C_INTERNAL_LOAD_SYSTEMIC_CEILING_UNAPPROVED",
        "M2D_HORIZON_OBSERVED_EVIDENCE_SHARE_DEFERRED",
        "M9_TRUSTED_EXECUTION_CUSTODY_AND_CLEARANCE_PENDING"
    )

    fun assess(request: M2CWorkloadIntelligenceRequest): Engine15M2CWorkloadAssessment {
        val ctx = request.context
        val m2b = request.intensityCalibration
        val flags = linkedSetOf<String>()

        // 1. Single unified reconciled evidence inventory shared across all M2-C engines (M2C-B02, M2C-C05, M2C-C06).
        val inventory = Engine15M2CReconciledEvidenceInventoryBuilder.reconcile(
            context = ctx,
            observedWorkload = request.observedWorkload,
            supplementaryActivities = request.supplementaryActivities,
            supplementaryDownhillStreams = request.supplementaryDownhillStreams
        )
        flags += inventory.diagnosticFlags

        // 2. Monotonic restriction preservation (M2C-C04: preserves ALL upstream M2-B reasons incl. EVALUATION_CONTEXT_MISMATCH!).
        val mergedPersistentIds = (m2b.persistentRestrictionIds + request.athleteSnapshot?.activeRestrictionIds.orEmpty()).toSortedSet()
        val interruptionSummary = classifyInterruption(ctx, request.confirmedInterruption, request.observedWorkload)
        val addedReasons = linkedSetOf<M2BRestrictionReason>()
        if (mergedPersistentIds.isNotEmpty()) addedReasons += M2BRestrictionReason.PERSISTENT_HOLD
        if (interruptionSummary.postIllnessConservativeAndHrRestrictionRequired) {
            // M2C-C04 / POL-034 / CON-02: Unresolved post-illness enforces BOTH recovery-only intensity AND HR quarantine!
            addedReasons += M2BRestrictionReason.RECOVERY_INTENSITY
            addedReasons += M2BRestrictionReason.HR_QUARANTINE
            flags += "POST_ILLNESS_ENFORCES_RECOVERY_INTENSITY_AND_HR_QUARANTINE"
        }
        if (request.confirmedInterruption?.kind == M2CInterruptionKind.POST_INJURY_USER_REPORTED) {
            if (!request.confirmedInterruption.userReportedClearanceToResumeRunning) {
                addedReasons += M2BRestrictionReason.RUNNING_RESTRICTION
                flags += "POST_INJURY_WITHOUT_USER_REPORTED_CLEARANCE_BLOCKS_RUNNING"
            } else {
                addedReasons += M2BRestrictionReason.RECOVERY_INTENSITY
                flags += "POST_INJURY_USER_REPORTED_CLEARANCE_CONSERVATIVE_RECOVERY_ONLY"
            }
        }
        val effectiveRestrictions = M2CEffectiveRestrictionState.strengthen(
            ctx, request.observedWorkload.contentHash, m2b.restrictionDecision, addedReasons
        )

        // 3. Rolling exposures, POL-011..013 baseline & weekly distribution strictly from inventory + pinned M1C (D2-04).
        val rollingOut = Engine15M2CRollingExposureEngine.computeRollingExposureFromInventory(
            context = ctx,
            observedWorkload = request.observedWorkload,
            inventory = inventory,
            weeklyConfirmations = request.weeklyConfirmations
        )
        flags += rollingOut.diagnosticFlags

        // 4. Optional MOD-10 single-session r_30 assessment using reconciled 30d & 90d anchors (M2C-C01, D2-04).
        val singleSessionAssessment = request.evaluatedSingleSessionDistance?.let { evalDist ->
            Engine15M2CRollingExposureEngine.assessSingleSessionExposure(
                context = ctx,
                evaluatedDistance = evalDist,
                anchor30d = rollingOut.anchor30d,
                anchor90d = rollingOut.anchor90d,
                effectiveRestrictionState = effectiveRestrictions,
                overrideAcknowledgment = request.singleSessionOverrideAcknowledgment
            )
        }

        // 5. Controlled MOD-11 stopping-condition contract bound to reconciled session (M2C-B04).
        val retroTarget = (request.longRunStoppingEvidence as? M2CStoppingVerificationEvidence.RetrospectiveCompletedObservation)?.let { obs ->
            (inventory.pinned30dRunningEntries + inventory.sameDayCompletedRunningEntries)
                .map { it.record }
                .firstOrNull { it.canonicalActivityId == obs.canonicalActivityId }
        }
        val stoppingContract = M2CStoppingConditionContract.createValidated(
            context = ctx,
            capIdentity = "pol030-long-run-cap-${ctx.localDate}",
            durationCap = request.longRunDurationCap,
            distanceCap = request.longRunDistanceCap,
            enforcementMode = request.longRunStoppingEnforcementMode,
            verificationEvidence = request.longRunStoppingEvidence,
            reconciledSession = retroTarget
        )

        // 6. Reconciled MOD-09 internal load, multimodal completeness & partial-aware intensity exposure (M2C-C03, M2C-C05).
        val bounds7 = rollingOut.running7d.bounds
        val bounds28 = rollingOut.running28d.bounds
        val bounds30 = rollingOut.running30d.bounds
        val pinned7Entries = inventory.pinned30dRunningEntries.filter { bounds7.contains(it.record.attributedLocalDate) }
        val pinned28Entries = inventory.pinned30dRunningEntries.filter { bounds28.contains(it.record.attributedLocalDate) }

        val (internalLoad28d, multimodal28d) = Engine15M2CInternalAndDownhillEngine.computeReconciledInternalLoadAndMultimodal(
            context = ctx, bounds28 = bounds28, pinnedRunning28d = pinned28Entries,
            nonRunning28d = inventory.nonRunningEntries28d, runningExposure28d = rollingOut.running28d, flags = flags
        )
        val intensityExposure28d = Engine15M2CInternalAndDownhillEngine.computeReconciledIntensityDomainExposure(
            context = ctx, bounds28 = bounds28, pinnedRunning28d = pinned28Entries,
            runningCoverage = rollingOut.baseline.coverage, flags = flags
        )

        // 7. 7-day, 28-day, and 30-day chronological downhill exposures strictly over reconciled entries (M2C-A04, M2C-B05).
        val downhill7d = Engine15M2CInternalAndDownhillEngine.aggregateRollingDownhill(ctx, bounds7, pinned7Entries, flags)
        val downhill28d = Engine15M2CInternalAndDownhillEngine.aggregateRollingDownhill(ctx, bounds28, pinned28Entries, flags)
        val downhill30d = Engine15M2CInternalAndDownhillEngine.aggregateRollingDownhill(ctx, bounds30, inventory.pinned30dRunningEntries, flags)

        // 8. 10-family quality exposure ledger strictly over reconciled inventory (M2C-C02).
        val qualityLedger = Engine15M2CQualityExposureLedgerBuilder.build(
            context = ctx, inventory = inventory, observations = request.qualityObservations,
            selfReports = request.familySelfReports, runningCoverage = rollingOut.baseline.coverage
        )
        flags += qualityLedger.diagnosticFlags

        // 9. Structurally isolated conditional projected boundary (INV-02, M2C-C06: horizon share deferred to M2-D).
        val projectedContract = M2CConditionalProjectedAnchorContract(
            projectedWorkloadContentHash = request.projectedWorkload?.contentHash,
            projectedSessionCount = request.projectedWorkload?.exposures?.size ?: 0,
            horizonObservedEvidenceShareDeferredToM2D = true
        )

        val workloadVector = M2COrthogonalWorkloadVector(
            running7d = rollingOut.running7d, running28d = rollingOut.running28d, running30d = rollingOut.running30d,
            running90dAnchor = rollingOut.anchor90d,
            sameDayCompletedRunningSessions = rollingOut.sameDayCompletedRunningSessions,
            downhill7d = downhill7d, downhill28d = downhill28d, downhill30d = downhill30d,
            intensityExposure28d = intensityExposure28d, qualityLedger56d = qualityLedger,
            internalLoad28d = internalLoad28d, systemicMultimodal28d = multimodal28d,
            weeklyDistribution28d = rollingOut.weeklyDistribution28d,
            collapsesToSingleCompositeScore = false
        )

        val allAliases = linkedSetOf<M2ASourceQualifiedAlias>()
        val allOpaqueIds = linkedSetOf<String>()
        (inventory.pinned30dRunningEntries + inventory.sameDayCompletedRunningEntries + inventory.extendedHistorical31to90RunningEntries).forEach {
            allAliases += it.record.sourceAliases
            allOpaqueIds += it.record.opaqueUpstreamRecordIds
        }
        inventory.nonRunningEntries28d.forEach {
            allAliases += it.sourceAliases
            allOpaqueIds += it.opaqueUpstreamRecordIds
        }
        qualityLedger.recordsByFamily.values.forEach { allAliases += it.sourceAliases }
        downhill30d.intervals.forEach { allAliases += it.provenance }

        // M2C-C04: Recovery-only (e.g., unresolved post-illness) or hold ALWAYS withholds ordinary paceCalibrationEligible!
        val paceEligible = !effectiveRestrictions.hold && !effectiveRestrictions.recoveryOnly && m2b.paceCalibrationEligible

        val handoff = M2CDownstreamWorkloadHandoff(
            context = ctx,
            policyVersion = Engine15M2CPolicyRegistry.POLICY_VERSION,
            upstreamObservedWorkloadHash = request.observedWorkload.contentHash,
            upstreamAthleteSnapshotId = request.athleteSnapshot?.snapshotId ?: m2b.upstreamSnapshotId,
            upstreamM2BContentHash = m2b.contentHash,
            upstreamM2BPolicyVersion = m2b.policyVersion,
            reconciledInventoryContentHash = inventory.contentHash,
            baseline = rollingOut.baseline,
            workloadVector = workloadVector,
            defaultLongRunStoppingContract = stoppingContract,
            interruptionEvidence = interruptionSummary,
            effectiveRestrictionState = effectiveRestrictions,
            persistentRestrictionIds = mergedPersistentIds,
            paceConflicts = m2b.paceConflicts,
            operationalThresholdCalibration = m2b.operationalThresholdCalibration,
            paceCalibrationEligible = paceEligible,
            hrZoneAuthorized = false,
            projectedBoundaryContract = projectedContract,
            unresolvedPolicyDependencies = UNRESOLVED_POLICY_DEPS,
            sourceProvenance = allAliases,
            opaqueUpstreamRecordIds = allOpaqueIds,
            diagnosticFlags = flags,
            productionAuthorized = false,
            workoutPrescriptionAuthorized = false,
            independentlyClearsPersistentHold = false
        )

        return Engine15M2CWorkloadAssessment.create(handoff, inventory, singleSessionAssessment)
    }

    private fun classifyInterruption(
        context: M2BEvaluationContext,
        input: M2CConfirmedInterruptionInput?,
        observedWorkload: ObservedWorkloadState
    ): M2CInterruptionEvidenceSummary {
        if (input == null || input.kind == M2CInterruptionKind.NONE) {
            val run = observedWorkload.disciplines[AthleteDiscipline.RUN]
            val missingSyncOnly = run == null || run.coverage == HistoryCoverageStatus.MISSING_UNOBSERVED || run.coverage == HistoryCoverageStatus.PARTIAL_COVERAGE
            return M2CInterruptionEvidenceSummary(
                kind = M2CInterruptionKind.NONE,
                bucket = if (missingSyncOnly) M2CInterruptionBucket.UNCONFIRMED_MISSING_SYNC else M2CInterruptionBucket.DAYS_0_TO_6,
                confirmedBreakDays = if (missingSyncOnly) null else 0,
                postIllnessConservativeAndHrRestrictionRequired = false
            )
        }
        val bucket = M2CInterruptionBucket.fromConfirmedBreakDays(input.confirmedBreakDays)
        val postIllnessRestricted = if (input.kind == M2CInterruptionKind.POST_ILLNESS) {
            val resolved = input.illnessResolvedAtUtc
            val minDaysPassed = resolved != null && !resolved.isAfter(context.asOf) &&
                Duration.between(resolved, context.asOf) >= Duration.ofDays(Engine15M2CPolicyRegistry.POL_034_POST_ILLNESS_MIN_HR_QUARANTINE_DAYS)
            !(input.symptomFreeConfirmed && input.restingAndSubmaxHrStabilityConfirmed && minDaysPassed)
        } else false

        return M2CInterruptionEvidenceSummary(
            kind = input.kind,
            bucket = bucket,
            confirmedBreakDays = input.confirmedBreakDays,
            postIllnessConservativeAndHrRestrictionRequired = postIllnessRestricted
        )
    }
}
