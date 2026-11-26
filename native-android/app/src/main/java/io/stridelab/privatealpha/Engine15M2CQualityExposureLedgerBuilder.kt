package io.stridelab.privatealpha

import java.time.Instant
import java.time.LocalDate
import java.util.TreeMap
import kotlin.math.abs

/**
 * Pure, deterministic builder for the 10-family independent quality exposure ledger
 * (CON-06; Track 2 Refinement §3; closes M2C-A01, M2C-A02, M2C-B03, M2C-C02, M2C-C05, M2C-C06).
 *
 * Guarantees:
 * - Exact membership in the reconciled inventory is enforced for BOTH pinned [d-30..d-1]
 *   and extended [d-56..d-31] quality observations (matching contentHash, account, discipline,
 *   reconciled source aliases, actual duration, and completion chronology).
 * - Arbitrary caller-supplied aliases are never appended as reconciled provenance.
 * - Family self-reports require account and non-future timestamp validation.
 * - No caller-supplied Boolean can assert 56-day verified absence of all 10 families.
 * - Duplicate conflicts check totalWorkDuration, longestBoutDuration, executionOutcome, and recovery,
 *   and preserve adverse recovery evidence conservatively rather than dropping safety signals.
 * - Cross-family duration allocations on the same session cannot exceed actual session duration.
 * - Authenticated SUCCESSFUL_EXPOSURE stays disabled pre-M9 (TRUSTED_M9_EXECUTION_CUSTODY_ACTIVE = false).
 * - Zero numerical-overflow-to-zero fallbacks.
 */
internal object Engine15M2CQualityExposureLedgerBuilder {

    private data class ValidQualityObs(
        val input: M2CQualityFamilyObservationInput,
        val canonicalActivityId: String,
        val attributedLocalDate: LocalDate,
        val completedAtUtc: Instant,
        val sessionDurationSeconds: Double,
        val effectiveRecovery: M2CRecoveryEvidenceState,
        val isAuthenticatedSuccess: Boolean,
        val isUnsuccessfulOrAdverse: Boolean,
        val reconciledAliases: Set<M2ASourceQualifiedAlias>
    )

    fun build(
        context: M2BEvaluationContext,
        inventory: M2CReconciledEvidenceInventory,
        observations: List<M2CQualityFamilyObservationInput>,
        selfReports: List<M2CFamilySelfReportInput>,
        runningCoverage: HistoryCoverageStatus
    ): M2CQualityExposureLedger {
        require(inventory.context == context) { "M2C-C02: inventory context mismatch" }

        val bounds56 = Engine15M2CRollingExposureEngine.boundsFor(context, M2CRollingWindowKind.TRAILING_56D)
        val flags = linkedSetOf<String>()
        if (runningCoverage == HistoryCoverageStatus.CONFIRMED_ZERO) {
            flags += "RECENT_ZERO_DOES_NOT_CERTIFY_56D_FAMILY_ABSENCE"
        }
        var excludedProjectedCount = 0

        // Single reconciled lookup across BOTH pinned [d-30..d-1] and extended [d-56..d-31] running entries.
        val reconciledByCanonId = (inventory.pinned30dRunningEntries +
            inventory.extendedHistorical31to90RunningEntries.filter { bounds56.contains(it.record.attributedLocalDate) })
            .associateBy { it.record.canonicalActivityId }

        val eligible = mutableListOf<ValidQualityObs>()
        for (obs in observations) {
            if (obs.isPlannedOrProjected) {
                excludedProjectedCount += 1
                flags += "PROJECTED_OR_PLANNED_QUALITY_OBSERVATION_EXCLUDED"
                continue
            }
            val act = obs.canonicalActivity
            if (obs.accountId != context.accountId || act.accountId != context.accountId) {
                flags += "FOREIGN_ACCOUNT_QUALITY_OBSERVATION_EXCLUDED"
                continue
            }
            if (act.discipline != AthleteDiscipline.RUN) {
                flags += "NON_RUNNING_QUALITY_OBSERVATION_EXCLUDED"
                continue
            }
            // M2C-C02: Require exact membership in reconciled inventory for BOTH 30d and 31-56d observations!
            val reconciled = reconciledByCanonId[act.canonicalId]
            if (reconciled == null) {
                flags += "QUALITY_OBSERVATION_ABSENT_FROM_RECONCILED_INVENTORY_EXCLUDED"
                continue
            }
            val reconciledAct = reconciled.canonicalActivity
            if (reconciledAct == null || reconciledAct.contentHash != act.contentHash) {
                flags += "QUALITY_OBSERVATION_CONTENT_HASH_MISMATCH_EXCLUDED"
                continue
            }
            val rec = reconciled.record
            if (rec.workloadEligibility != WorkloadAggregationEligibility.ELIGIBLE_VERIFIED_OBSERVED) {
                flags += "AMBIGUOUS_OR_COLLIDED_QUALITY_OBSERVATION_EXCLUDED"
                continue
            }
            val sessDur = rec.duration?.takeIf { it.value > 0.0 }
            if (sessDur == null || rec.durationQuality != TelemetryQualityStatus.VALID_OBSERVED) {
                flags += "INVALID_DURATION_QUALITY_OBSERVATION_EXCLUDED"
                continue
            }
            if (!bounds56.contains(rec.attributedLocalDate)) {
                flags += "OUT_OF_56D_WINDOW_QUALITY_OBSERVATION_EXCLUDED"
                continue
            }
            if (obs.totalWorkDuration.value <= 0.0 ||
                obs.totalWorkDuration.value > sessDur.value + Engine15M2CPolicyRegistry.DURATION_CONSERVATION_TOLERANCE_SECONDS
            ) {
                flags += "WORK_DURATION_EXCEEDS_SESSION_QUALITY_OBSERVATION_EXCLUDED"
                continue
            }
            if (obs.recordedAtUtc.isAfter(context.asOf) || obs.recordedAtUtc.isBefore(rec.completedAtUtc)) {
                flags += "INVALID_QUALITY_RECORDED_TIMESTAMP_EXCLUDED"
                continue
            }
            // M2C-C02: Supplied sourceAlias must belong to reconciled session provenance (never append arbitrary aliases!).
            val normAlias = Engine15M2CCanonical.normalizeAlias(obs.sourceAlias)
            if (rec.sourceAliases.isEmpty() || normAlias !in rec.sourceAliases) {
                flags += "QUALITY_OBSERVATION_UNRECONCILED_SOURCE_ALIAS_EXCLUDED"
                continue
            }

            // M2C-A01: Pre-M9 caller claims never self-authenticate into VERIFIED_ACCEPTABLE / SUCCESSFUL_EXPOSURE.
            val effectiveRecovery = when (obs.recoveryEvidence) {
                M2CRecoveryEvidenceState.MISSING_UNREPORTED -> M2CRecoveryEvidenceState.MISSING_UNREPORTED
                M2CRecoveryEvidenceState.ADVERSE_SYMPTOM_OR_FATIGUE -> M2CRecoveryEvidenceState.ADVERSE_SYMPTOM_OR_FATIGUE
                M2CRecoveryEvidenceState.REPORTED_ACCEPTABLE_UNAUTHENTICATED_PRE_M9 ->
                    M2CRecoveryEvidenceState.REPORTED_ACCEPTABLE_UNAUTHENTICATED_PRE_M9
                M2CRecoveryEvidenceState.VERIFIED_ACCEPTABLE ->
                    if (obs.recoveryTrustTier.isIndependentlyAuthenticated) M2CRecoveryEvidenceState.VERIFIED_ACCEPTABLE
                    else {
                        flags += "UNAUTHENTICATED_ACCEPTABLE_RECOVERY_HELD_PROVISIONAL_PRE_M9"
                        M2CRecoveryEvidenceState.REPORTED_ACCEPTABLE_UNAUTHENTICATED_PRE_M9
                    }
            }
            val isUnsuccessful = obs.executionOutcome == M2CQualityExecutionOutcome.OVERSHOT_OR_FADED ||
                obs.executionOutcome == M2CQualityExecutionOutcome.ABORTED_OR_UNSUCCESSFUL ||
                effectiveRecovery == M2CRecoveryEvidenceState.ADVERSE_SYMPTOM_OR_FATIGUE
            val isAuthSuccess = !isUnsuccessful &&
                obs.executionOutcome == M2CQualityExecutionOutcome.COMPLETED_ON_TARGET &&
                effectiveRecovery == M2CRecoveryEvidenceState.VERIFIED_ACCEPTABLE &&
                obs.executionTrustTier.isIndependentlyAuthenticated &&
                obs.recoveryTrustTier.isIndependentlyAuthenticated
            if (!isUnsuccessful && obs.executionOutcome == M2CQualityExecutionOutcome.COMPLETED_ON_TARGET &&
                !obs.executionTrustTier.isIndependentlyAuthenticated
            ) {
                flags += "UNAUTHENTICATED_ON_TARGET_EXECUTION_HELD_OBSERVED_COMPLETED_PRE_M9"
            }

            eligible += ValidQualityObs(
                input = obs,
                canonicalActivityId = rec.canonicalActivityId,
                attributedLocalDate = rec.attributedLocalDate,
                completedAtUtc = rec.completedAtUtc,
                sessionDurationSeconds = sessDur.value,
                effectiveRecovery = effectiveRecovery,
                isAuthenticatedSuccess = isAuthSuccess,
                isUnsuccessfulOrAdverse = isUnsuccessful,
                reconciledAliases = rec.sourceAliases
            )
        }

        // 2. Per-(canonicalActivityId, family) duplicate reconciliation with longestBoutDuration check & adverse preservation (M2C-C02, M2C-C06).
        val conflictCountByFamily = TreeMap<M2CQualityWorkoutFamily, Int>()
        val adverseConflictFamilies = linkedSetOf<M2CQualityWorkoutFamily>()
        val conflictCanonIdsByFamily = TreeMap<M2CQualityWorkoutFamily, MutableSet<String>>()
        val conflictAliasesByFamily = TreeMap<M2CQualityWorkoutFamily, MutableSet<M2ASourceQualifiedAlias>>()

        val reconciledSingletons = mutableListOf<ValidQualityObs>()
        val byCanonAndFamily = eligible.groupBy { it.canonicalActivityId to it.input.family }
            .toSortedMap(compareBy<Pair<String, M2CQualityWorkoutFamily>> { it.first }.thenBy { it.second.ordinal })

        for ((key, group) in byCanonAndFamily) {
            val (canonId, family) = key
            val workVals = group.map { it.input.totalWorkDuration.value }
            val workConflict = (workVals.max() - workVals.min()) > Engine15M2CPolicyRegistry.DURATION_CONSERVATION_TOLERANCE_SECONDS
            val boutVals = group.map { it.input.longestBoutDuration.value }
            val boutConflict = (boutVals.max() - boutVals.min()) > Engine15M2CPolicyRegistry.DURATION_CONSERVATION_TOLERANCE_SECONDS
            val outcomeConflict = group.map { it.input.executionOutcome }.distinct().size >= 2
            val nonMissingRec = group.map { it.effectiveRecovery }.filter { it != M2CRecoveryEvidenceState.MISSING_UNREPORTED }.distinct()
            val recoveryConflict = nonMissingRec.size >= 2

            if (workConflict || boutConflict || outcomeConflict || recoveryConflict) {
                conflictCountByFamily[family] = (conflictCountByFamily[family] ?: 0) + 1
                conflictCanonIdsByFamily.getOrPut(family) { linkedSetOf() }.add(canonId)
                conflictAliasesByFamily.getOrPut(family) { linkedSetOf() }.addAll(group.flatMap { it.reconciledAliases })
                // M2C-C02: Never drop adverse recovery or unsuccessful outcome evidence during duplicate conflict!
                if (group.any { it.effectiveRecovery == M2CRecoveryEvidenceState.ADVERSE_SYMPTOM_OR_FATIGUE || it.isUnsuccessfulOrAdverse }) {
                    adverseConflictFamilies += family
                    flags += "CONTRADICTORY_QUALITY_RECOVERY_ADVERSE_PRESERVED"
                } else {
                    flags += "CONTRADICTORY_DUPLICATE_QUALITY_OBSERVATION_EXCLUDED"
                }
                continue
            }
            if (group.size > 1) flags += "DUPLICATE_QUALITY_OBSERVATION_DEDUPLICATED"

            val mergedRecovery = nonMissingRec.singleOrNull() ?: M2CRecoveryEvidenceState.MISSING_UNREPORTED
            val rep = group.sortedWith(
                compareByDescending<ValidQualityObs> { it.effectiveRecovery != M2CRecoveryEvidenceState.MISSING_UNREPORTED }
                    .thenByDescending { it.input.executionTrustTier.ordinal }
                    .thenByDescending { it.input.recoveryTrustTier.ordinal }
                    .thenByDescending { it.input.recordedAtUtc }
                    .thenBy { it.input.totalWorkDuration.value }
                    .thenBy { it.input.longestBoutDuration.value }
                    .thenBy { Engine15M2CCanonical.normalizeAlias(it.input.sourceAlias) }
                    .thenBy { it.canonicalActivityId }
            ).first()
            reconciledSingletons += rep.copy(
                effectiveRecovery = mergedRecovery,
                isAuthenticatedSuccess = group.any { it.isAuthenticatedSuccess },
                isUnsuccessfulOrAdverse = group.any { it.isUnsuccessfulOrAdverse }
            )
        }

        // 3. M2C-C02: Cross-family work-duration conservation on the same canonicalActivityId (never fabricate extra work!).
        val validAcrossFamilies = TreeMap<M2CQualityWorkoutFamily, MutableList<ValidQualityObs>>()
        for ((canonId, sessionRows) in reconciledSingletons.groupBy { it.canonicalActivityId }.toSortedMap()) {
            val sessionDur = sessionRows.first().sessionDurationSeconds
            val totalCrossFamilyWork = Engine15M2CCanonical.compensatedSum(sessionRows.map { it.input.totalWorkDuration.value }.sorted())
            if (!totalCrossFamilyWork.isFinite() ||
                totalCrossFamilyWork > sessionDur + Engine15M2CPolicyRegistry.DURATION_CONSERVATION_TOLERANCE_SECONDS
            ) {
                flags += "CROSS_FAMILY_SESSION_WORK_DURATION_EXCEEDS_SESSION_EXCLUDED"
                for (row in sessionRows) {
                    val fam = row.input.family
                    conflictCountByFamily[fam] = (conflictCountByFamily[fam] ?: 0) + 1
                    conflictCanonIdsByFamily.getOrPut(fam) { linkedSetOf() }.add(canonId)
                    conflictAliasesByFamily.getOrPut(fam) { linkedSetOf() }.addAll(row.reconciledAliases)
                    if (row.effectiveRecovery == M2CRecoveryEvidenceState.ADVERSE_SYMPTOM_OR_FATIGUE || row.isUnsuccessfulOrAdverse) {
                        adverseConflictFamilies += fam
                    }
                }
            } else {
                for (row in sessionRows) {
                    validAcrossFamilies.getOrPut(row.input.family) { mutableListOf() }.add(row)
                }
            }
        }

        // 4. Reconcile account- & chronology-bound self-reports deterministically (M2C-C02, M2C-C06).
        val validSelfReports = selfReports.filter { sr ->
            when {
                sr.accountId != context.accountId -> { flags += "FOREIGN_ACCOUNT_FAMILY_SELF_REPORT_EXCLUDED"; false }
                sr.recordedAtUtc.isAfter(context.asOf) -> { flags += "FUTURE_FAMILY_SELF_REPORT_EXCLUDED"; false }
                else -> true
            }
        }
        val selfReportsByFamily = TreeMap<M2CQualityWorkoutFamily, M2CFamilySelfReportInput?>()
        for ((fam, grp) in validSelfReports.groupBy { it.family }.toSortedMap()) {
            val distinctModes = grp.map { it.confirmedNoExperience to it.declaredRecentFamiliarity }.distinct()
            if (distinctModes.size > 1) {
                flags += "CONFLICTING_FAMILY_SELF_REPORT_EXCLUDED"
                selfReportsByFamily[fam] = null
            } else {
                selfReportsByFamily[fam] = grp.sortedWith(
                    compareByDescending<M2CFamilySelfReportInput> { it.recordedAtUtc }
                        .thenBy { Engine15M2CCanonical.normalizeAlias(it.sourceAlias) }
                ).first()
            }
        }

        // 5. Pass 1: Resolve all 10 families independently (zero caller confirmed56dContinuousCoverage bypass!).
        data class DraftFamily(
            val family: M2CQualityWorkoutFamily,
            val state: M2CFamilyExposureState,
            val recovery: M2CRecoveryEvidenceState,
            val obsCount: Int,
            val authSuccessCount: Int,
            val unsuccessfulCount: Int,
            val conflictCount: Int,
            val adverseConflictPreserved: Boolean,
            val totalWorkBelief: BeliefState<Seconds>,
            val longestBout: Seconds?,
            val mostRecentDate: LocalDate?,
            val canonIds: Set<String>,
            val aliases: Set<M2ASourceQualifiedAlias>
        )

        val drafts = TreeMap<M2CQualityWorkoutFamily, DraftFamily>()
        for (family in M2CQualityWorkoutFamily.entries) {
            val items = validAcrossFamilies[family].orEmpty().sortedWith(
                compareByDescending<ValidQualityObs> { it.completedAtUtc }
                    .thenBy { it.canonicalActivityId }
            )
            val sr = selfReportsByFamily[family]
            val confCount = conflictCountByFamily[family] ?: 0
            val adverseConf = family in adverseConflictFamilies
            val confCanonIds = conflictCanonIdsByFamily[family].orEmpty()
            val confAliases = conflictAliasesByFamily[family].orEmpty()

            if (items.isNotEmpty()) {
                val latest = items.first()
                val authSuccess = items.count { it.isAuthenticatedSuccess }
                val unsuccCount = items.count { it.isUnsuccessfulOrAdverse } + if (adverseConf) 1 else 0
                val workSum = Engine15M2CCanonical.compensatedSum(items.map { it.input.totalWorkDuration.value }.sorted())
                val workBelief: BeliefState<Seconds> = when {
                    !workSum.isFinite() || workSum < 0.0 -> {
                        flags += "QUALITY_WORK_DURATION_OVERFLOW_EXCLUDED"
                        BeliefState.Unknown("NUMERICAL_OVERFLOW_EXCLUDED")
                    }
                    confCount > 0 -> BeliefState.Bounded(Seconds.of(workSum), null, EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, UncertaintyKind.COACHING_GUIDANCE_BAND)
                    else -> BeliefState.Estimated(Seconds.of(workSum), EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, UncertaintyKind.COACHING_GUIDANCE_BAND)
                }
                val longestBout = items.maxOfOrNull { it.input.longestBoutDuration.value }?.let(Seconds::of)
                val effectiveRec = if (adverseConf) M2CRecoveryEvidenceState.ADVERSE_SYMPTOM_OR_FATIGUE else latest.effectiveRecovery
                val expState = when {
                    adverseConf || latest.isUnsuccessfulOrAdverse -> M2CFamilyExposureState.EXCESSIVE_OR_UNSUCCESSFUL
                    authSuccess >= 1 && effectiveRec == M2CRecoveryEvidenceState.VERIFIED_ACCEPTABLE ->
                        M2CFamilyExposureState.SUCCESSFUL_EXPOSURE
                    else -> M2CFamilyExposureState.OBSERVED_COMPLETED
                }
                drafts[family] = DraftFamily(
                    family = family, state = expState, recovery = effectiveRec,
                    obsCount = items.size, authSuccessCount = authSuccess, unsuccessfulCount = unsuccCount,
                    conflictCount = confCount, adverseConflictPreserved = adverseConf,
                    totalWorkBelief = workBelief, longestBout = longestBout, mostRecentDate = latest.attributedLocalDate,
                    canonIds = items.map { it.canonicalActivityId }.toSet() + confCanonIds,
                    aliases = items.flatMap { it.reconciledAliases }.toSet() + confAliases
                )
            } else if (adverseConf) {
                drafts[family] = DraftFamily(
                    family = family, state = M2CFamilyExposureState.EXCESSIVE_OR_UNSUCCESSFUL,
                    recovery = M2CRecoveryEvidenceState.ADVERSE_SYMPTOM_OR_FATIGUE,
                    obsCount = 0, authSuccessCount = 0, unsuccessfulCount = 1, conflictCount = confCount,
                    adverseConflictPreserved = true,
                    totalWorkBelief = BeliefState.Unknown("CONFLICTING_QUALITY_WORK_DURATION_EXCLUDED"),
                    longestBout = null, mostRecentDate = null,
                    canonIds = confCanonIds, aliases = confAliases
                )
            } else {
                val (state, srAliases, workBelief) = when {
                    confCount > 0 -> Triple(
                        M2CFamilyExposureState.UNKNOWN_EXPOSURE, confAliases,
                        BeliefState.Unknown("CONFLICTING_QUALITY_OBSERVATIONS_ONLY")
                    )
                    sr?.declaredRecentFamiliarity == true -> Triple(
                        M2CFamilyExposureState.SELF_REPORTED_EXPERIENCE, setOf(sr.sourceAlias),
                        BeliefState.Unknown("SELF_REPORTED_FAMILIARITY_ONLY")
                    )
                    sr?.confirmedNoExperience == true -> Triple(
                        M2CFamilyExposureState.CONFIRMED_ABSENT, setOf(sr.sourceAlias),
                        BeliefState.Estimated(Seconds.ZERO, EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, UncertaintyKind.NONE)
                    )
                    else -> Triple(
                        M2CFamilyExposureState.UNKNOWN_EXPOSURE, emptySet(),
                        BeliefState.Unknown("NO_OBSERVED_QUALITY_EXPOSURE_NOT_CONFIRMED_ABSENT")
                    )
                }
                drafts[family] = DraftFamily(
                    family = family, state = state, recovery = M2CRecoveryEvidenceState.MISSING_UNREPORTED,
                    obsCount = 0, authSuccessCount = 0, unsuccessfulCount = 0, conflictCount = confCount,
                    adverseConflictPreserved = false, totalWorkBelief = workBelief, longestBout = null,
                    mostRecentDate = null, canonIds = confCanonIds, aliases = srAliases
                )
            }
        }

        // 6. Pass 2: Attach observed partial-transfer metadata without fabricating completed exposure or authorizing dosing.
        val records = TreeMap<M2CQualityWorkoutFamily, M2CQualityFamilyExposureRecord>()
        for (family in M2CQualityWorkoutFamily.entries) {
            val d = drafts.getValue(family)
            val observedTransfers = family.partialTransferSources
                .mapNotNull { name -> M2CQualityWorkoutFamily.entries.firstOrNull { it.name == name } }
                .filter { srcFam ->
                    val s = drafts.getValue(srcFam).state
                    s == M2CFamilyExposureState.OBSERVED_COMPLETED || s == M2CFamilyExposureState.SUCCESSFUL_EXPOSURE
                }.toSet()

            records[family] = M2CQualityFamilyExposureRecord(
                family = family,
                exposureState = d.state,
                recentRecoveryEvidence = d.recovery,
                observedSessionCount56d = d.obsCount,
                authenticatedSuccessfulSessionCount56d = d.authSuccessCount,
                unsuccessfulSessionCount56d = d.unsuccessfulCount,
                conflictingObservationCount56d = d.conflictCount,
                adverseRecoveryConflictPreserved = d.adverseConflictPreserved,
                totalObservedWorkDuration56d = d.totalWorkBelief,
                longestObservedBoutDuration56d = d.longestBout,
                mostRecentObservedDate = d.mostRecentDate,
                requiresIndependentIntroExposure = family.requiresIndependentIntroExposure,
                partialTransferSourceFamilies = observedTransfers,
                canonicalActivityIds = d.canonIds,
                sourceAliases = d.aliases,
                transferFabricatesCompletedExposure = false,
                authorizesWorkoutDose = false
            )
        }

        return M2CQualityExposureLedger(
            bounds = bounds56,
            recordsByFamily = records,
            excludedProjectedOrPlannedCount = excludedProjectedCount,
            diagnosticFlags = flags
        )
    }
}
