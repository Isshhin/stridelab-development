package io.stridelab.privatealpha

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Collections
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal data class M2CRollingExposureOutput(
    val running7d: M2CRunningWindowExposure,
    val running28d: M2CRunningWindowExposure,
    val running30d: M2CRunningWindowExposure,
    val anchor30d: M2CVerifiedSingleSessionAnchor,
    val anchor90d: M2CVerifiedSingleSessionAnchor,
    val sameDayCompletedRunningSessions: List<M2CVerifiedRunningSessionRecord>,
    val baseline: M2CCoverageAwareBaseline,
    val weeklyDistribution28d: M2CWeeklyDistributionSummary,
    val diagnosticFlags: Set<String>
)

/**
 * Pure, deterministic M2-C rolling exposure, POL-011..013 baseline arbitration,
 * MOD-10 single-session r_30 assessment, and descriptive 4-week distribution engine.
 *
 * Operates strictly over validated M2CReconciledEvidenceInventory + authoritative frozen M1C
 * ObservedWorkloadState (M2C-A06, M2C-B01..B03, M2C-B06, M2C-C01, M2C-C05, FINAL-02, FINAL-04).
 */
internal object Engine15M2CRollingExposureEngine {

    fun boundsFor(context: M2BEvaluationContext, kind: M2CRollingWindowKind): M2CWindowBounds {
        val evalDate = context.localDate
        return M2CWindowBounds(
            kind = kind,
            startDateInclusive = evalDate.minusDays(kind.spanDays.toLong()),
            endDateInclusive = evalDate.minusDays(1),
            evaluatedLocalDate = evalDate,
            evaluatedAtUtc = context.asOf,
            zoneId = context.zone
        )
    }

    fun computeRollingExposureFromInventory(
        context: M2BEvaluationContext,
        observedWorkload: ObservedWorkloadState,
        inventory: M2CReconciledEvidenceInventory,
        weeklyConfirmations: List<M2CWeeklyConfirmationInput> = emptyList()
    ): M2CRollingExposureOutput {
        val alignment = M2CInventoryValidator.validateAgainstObservedWorkload(
            context = context,
            observedWorkload = observedWorkload,
            inventory = inventory
        )

        val flags = linkedSetOf<String>()
        val bounds7 = boundsFor(context, M2CRollingWindowKind.TRAILING_7D)
        val bounds28 = boundsFor(context, M2CRollingWindowKind.TRAILING_28D)
        val bounds30 = boundsFor(context, M2CRollingWindowKind.PRECEDING_30D)

        val runWorkload = observedWorkload.disciplines[AthleteDiscipline.RUN]
        val rawCoverage = runWorkload?.coverage
            ?: inferEmptyCoverage(observedWorkload.provisionalSelfReport[AthleteDiscipline.RUN])
        val anyExcluded30d = alignment.excludedPinnedRunExps30d.isNotEmpty()
        val anyExcluded28d = alignment.excludedPinnedRunExps28d.isNotEmpty()
        if (anyExcluded30d) {
            flags += "EXCLUDED_M1C_30D_EXPOSURE_RECALCULATED_FROM_RETAINED_INVENTORY"
        }
        if (rawCoverage == HistoryCoverageStatus.CONTINUOUS_COVERAGE) {
            flags += "CONTINUOUS_28D_COVERAGE_DOES_NOT_CERTIFY_COMPLETE_30D_HISTORY"
        }

        val effectiveWindowCoverage = if (anyExcluded28d && rawCoverage == HistoryCoverageStatus.CONTINUOUS_COVERAGE) {
            HistoryCoverageStatus.PARTIAL_COVERAGE
        } else {
            rawCoverage
        }

        val prior30Records = inventory.pinned30dRunningEntries.map { it.record }
        // Resolve missing alternatives before slicing dates or dropping unfinished observations.
        val incompleteDistanceGroups = M2CInventoryValidator.incompleteAmbiguousDistanceGroups(
            prior30Records, alignment.excludedPinnedRunExps30d
        )

        val exposure7d = buildWindowExposure(
            bounds = bounds7,
            records = prior30Records.filter { bounds7.contains(it.attributedLocalDate) },
            authoritativeDistanceBelief = if (anyExcluded28d) null else runWorkload?.rolling7dDistanceBelief,
            authoritativeLongestBelief = null,
            coverage = effectiveWindowCoverage,
            incompleteDistanceGroups = incompleteDistanceGroups
        )
        val exposure28d = buildWindowExposure(
            bounds = bounds28,
            records = prior30Records.filter { bounds28.contains(it.attributedLocalDate) },
            authoritativeDistanceBelief = if (anyExcluded28d) null else runWorkload?.rolling28dDistanceBelief,
            authoritativeLongestBelief = null,
            coverage = effectiveWindowCoverage,
            incompleteDistanceGroups = incompleteDistanceGroups
        )
        val exposure30d = buildWindowExposure(
            bounds = bounds30,
            records = prior30Records,
            authoritativeDistanceBelief = null,
            authoritativeLongestBelief = if (anyExcluded30d) null else runWorkload?.preceding30dLongestSessionBelief,
            coverage = effectiveWindowCoverage,
            incompleteDistanceGroups = incompleteDistanceGroups
        )

        val anchor30d = M2CVerifiedSingleSessionAnchor.fromReconciled30dInventory(
            context = context,
            observedWorkload = observedWorkload,
            inventory = inventory
        )
        val anchor90d = M2CVerifiedSingleSessionAnchor.fromReconciled90dInventory(
            context = context,
            observedWorkload = observedWorkload,
            inventory = inventory
        )

        val baseline = arbitrateBaseline(
            context = context,
            observedWorkload = observedWorkload,
            exposure28d = exposure28d,
            excludedPinnedRunExps28d = alignment.excludedPinnedRunExps28d,
            extraFlags = flags,
            incompleteDistanceGroups = incompleteDistanceGroups
        )
        val weeklyDist = summarizeWeeklyDistribution(
            evalDate = context.localDate,
            records28d = exposure28d.sessions,
            coverage = baseline.coverage,
            weeklyConfirmations = weeklyConfirmations,
            incompleteDistanceGroups = incompleteDistanceGroups
        )
        flags += baseline.diagnosticFlags
        flags += weeklyDist.diagnosticFlags

        return M2CRollingExposureOutput(
            running7d = exposure7d,
            running28d = exposure28d,
            running30d = exposure30d,
            anchor30d = anchor30d,
            anchor90d = anchor90d,
            sameDayCompletedRunningSessions = Collections.unmodifiableList(
                ArrayList(inventory.sameDayCompletedRunningEntries.map { it.record }.sorted())
            ),
            baseline = baseline,
            weeklyDistribution28d = weeklyDist,
            diagnosticFlags = Collections.unmodifiableSet(LinkedHashSet(flags.sorted()))
        )
    }

    /**
     * POL-011, POL-012, and POL-013 coverage-aware baseline arbitration with FINAL-04 consistency.
     * When any 28-day pinned M1C exposure was excluded due to future completion or invalid duration,
     * excluded workload is never retained in the effective baseline and exact CONTINUOUS_RECORDED
     * authority is withheld in favor of a conservative retained lower bound or Unknown.
     */
    fun arbitrateBaseline(
        context: M2BEvaluationContext,
        observedWorkload: ObservedWorkloadState,
        exposure28d: M2CRunningWindowExposure,
        excludedPinnedRunExps28d: List<DatedObservedSessionExposure>,
        extraFlags: MutableSet<String> = linkedSetOf(),
        incompleteDistanceGroups: Set<String> = M2CInventoryValidator.incompleteAmbiguousDistanceGroups(
            exposure28d.sessions, excludedPinnedRunExps28d
        )
    ): M2CCoverageAwareBaseline {
        val incompleteAmbiguousDistance = exposure28d.sessions.any {
            it.workloadEligibility == WorkloadAggregationEligibility.AMBIGUOUS_BOUNDED_ONLY &&
                (it.ambiguityGroupKey ?: it.canonicalActivityId) in incompleteDistanceGroups
        }
        if (excludedPinnedRunExps28d.isEmpty() && !incompleteAmbiguousDistance) {
            return arbitrateBaseline(observedWorkload, extraFlags)
        }

        val flags = linkedSetOf<String>()
        if (excludedPinnedRunExps28d.isNotEmpty()) flags += "EXCLUDED_M1C_WORKLOAD_WITHHOLDS_EXACT_BASELINE_AUTHORITY"
        if (incompleteAmbiguousDistance) flags += "AMBIGUOUS_MISSING_DISTANCE_WITHHOLDS_EXACT_BASELINE_AUTHORITY"
        val sr = observedWorkload.provisionalSelfReport[AthleteDiscipline.RUN]
        val srWeekly = sr?.weeklyVolume?.takeIf { it.value > 0.0 }

        val retained = exposure28d.sessions
        val activeRetained = retained.filter {
            it.workloadEligibility != WorkloadAggregationEligibility.BLOCKED_COLLISION
        }
        val verifiedCount28 = retained.count {
            it.workloadEligibility == WorkloadAggregationEligibility.ELIGIBLE_VERIFIED_OBSERVED &&
                (!it.fromCompletedPlanLogOnly || it.distance != null)
        }
        val dates28 = activeRetained.map { it.attributedLocalDate }.distinct().sorted()
        val spanDays = if (dates28.isEmpty()) {
            0
        } else {
            ChronoUnit.DAYS.between(dates28.first(), context.localDate).toInt().coerceAtLeast(0)
        }
        val maxGapDays = if (dates28.isEmpty()) {
            null
        } else {
            var maxGap = 0
            for (i in 0 until dates28.size - 1) {
                val gap = ChronoUnit.DAYS.between(dates28[i], dates28[i + 1]).toInt()
                if (gap > maxGap) maxGap = gap
            }
            val trailingGap = ChronoUnit.DAYS.between(dates28.last(), context.localDate).toInt() - 1
            if (trailingGap > maxGap) maxGap = trailingGap
            maxGap.coerceAtLeast(0)
        }

        val retainedDistance = aggregateDistanceForRecords(
            retained, M2CConfirmedWeekStatus.UNCONFIRMED_MISSING_DATA_GAP, incompleteDistanceGroups
        )
        val retained28dLowerM = when (val b = retainedDistance) {
            is BeliefState.Estimated -> b.estimate.value.takeIf { it.isFinite() && it > 0.0 }
            is BeliefState.Bounded -> b.lower?.value?.takeIf { it.isFinite() && it > 0.0 }
            else -> null
        }
        val retainedWeeklyLower = retained28dLowerM?.let { Meters.of(it / 4.0) }

        val (coverage, status, weeklyMeanBelief) = when {
            retainedWeeklyLower != null && srWeekly != null -> {
                flags += "PARTIAL_COVERAGE_PRESERVES_PROVISIONAL_SELF_REPORT"
                Triple(
                    HistoryCoverageStatus.PARTIAL_COVERAGE,
                    M2CBaselineArbitrationStatus.PARTIAL_RECORDED_WITH_SELF_REPORT,
                    BeliefState.Bounded(
                        lower = retainedWeeklyLower,
                        upper = null,
                        authority = EvidenceAuthorityClass.SENSOR_SUPPORTED,
                        uncertaintyKind = UncertaintyKind.COACHING_GUIDANCE_BAND
                    )
                )
            }
            retainedWeeklyLower != null -> {
                flags += "PARTIAL_COVERAGE_OBSERVED_LOWER_BOUND_ONLY"
                Triple(
                    HistoryCoverageStatus.PARTIAL_COVERAGE,
                    M2CBaselineArbitrationStatus.PARTIAL_RECORDED_ONLY,
                    BeliefState.Bounded(
                        lower = retainedWeeklyLower,
                        upper = null,
                        authority = EvidenceAuthorityClass.SENSOR_SUPPORTED,
                        uncertaintyKind = UncertaintyKind.COACHING_GUIDANCE_BAND
                    )
                )
            }
            srWeekly != null -> {
                flags += "BASELINE_PROVISIONAL_SELF_REPORT_PRESERVED"
                Triple(
                    HistoryCoverageStatus.PROVISIONAL_SELF_REPORT,
                    M2CBaselineArbitrationStatus.PROVISIONAL_SELF_REPORT,
                    BeliefState.Unknown("EXCLUDED_FUTURE_OR_INVALID_M1C_WORKLOAD")
                )
            }
            else -> {
                flags += "BASELINE_MISSING_UNOBSERVED_NEVER_ZERO"
                Triple(
                    HistoryCoverageStatus.MISSING_UNOBSERVED,
                    M2CBaselineArbitrationStatus.MISSING_UNOBSERVED,
                    BeliefState.Unknown("EXCLUDED_FUTURE_OR_INVALID_M1C_WORKLOAD")
                )
            }
        }

        extraFlags += flags
        return M2CCoverageAwareBaseline(
            coverage = coverage,
            arbitrationStatus = status,
            observedSpanDays = spanDays,
            verifiedSessionCount28d = verifiedCount28,
            maxGapDays = maxGapDays,
            observedWeeklyMean = weeklyMeanBelief,
            provisionalSelfReportWeekly = srWeekly,
            observedLowerBoundWeekly = retainedWeeklyLower,
            conservativePolicyCeilingWeekly = null,
            pol013RecordedToSelfReportRatio = null,
            conflictDetected = false,
            selfReportReplacementAuthorized = false,
            diagnosticFlags = flags
        )
    }

    /**
     * POL-011, POL-012, and POL-013 coverage-aware baseline arbitration with exact M1C & M2-A R3 parity
     * when no 28-day pinned exposures were excluded.
     */
    fun arbitrateBaseline(
        observedWorkload: ObservedWorkloadState,
        extraFlags: MutableSet<String> = linkedSetOf()
    ): M2CCoverageAwareBaseline {
        val flags = linkedSetOf<String>()
        val run = observedWorkload.disciplines[AthleteDiscipline.RUN]
        val sr = observedWorkload.provisionalSelfReport[AthleteDiscipline.RUN]
        val srWeekly = sr?.weeklyVolume?.takeIf { it.value > 0.0 }

        if (run == null) {
            return when {
                sr?.confirmedZero == true -> {
                    flags += "BASELINE_CONFIRMED_ZERO"
                    M2CCoverageAwareBaseline(
                        coverage = HistoryCoverageStatus.CONFIRMED_ZERO,
                        arbitrationStatus = M2CBaselineArbitrationStatus.CONFIRMED_ZERO,
                        observedSpanDays = 0, verifiedSessionCount28d = 0, maxGapDays = null,
                        observedWeeklyMean = BeliefState.Estimated(Meters.ZERO, EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, UncertaintyKind.NONE),
                        provisionalSelfReportWeekly = null, observedLowerBoundWeekly = null,
                        conservativePolicyCeilingWeekly = null, pol013RecordedToSelfReportRatio = null,
                        conflictDetected = false, selfReportReplacementAuthorized = false, diagnosticFlags = flags
                    )
                }
                srWeekly != null -> {
                    flags += "BASELINE_PROVISIONAL_SELF_REPORT_PRESERVED"
                    M2CCoverageAwareBaseline(
                        coverage = HistoryCoverageStatus.PROVISIONAL_SELF_REPORT,
                        arbitrationStatus = M2CBaselineArbitrationStatus.PROVISIONAL_SELF_REPORT,
                        observedSpanDays = 0, verifiedSessionCount28d = 0, maxGapDays = null,
                        observedWeeklyMean = BeliefState.Unknown("NO_RECORDED_RUNNING_HISTORY"),
                        provisionalSelfReportWeekly = srWeekly, observedLowerBoundWeekly = null,
                        conservativePolicyCeilingWeekly = null, pol013RecordedToSelfReportRatio = null,
                        conflictDetected = false, selfReportReplacementAuthorized = false, diagnosticFlags = flags
                    )
                }
                else -> {
                    flags += "BASELINE_MISSING_UNOBSERVED_NEVER_ZERO"
                    M2CCoverageAwareBaseline(
                        coverage = HistoryCoverageStatus.MISSING_UNOBSERVED,
                        arbitrationStatus = M2CBaselineArbitrationStatus.MISSING_UNOBSERVED,
                        observedSpanDays = 0, verifiedSessionCount28d = 0, maxGapDays = null,
                        observedWeeklyMean = BeliefState.Unknown("MISSING_UNOBSERVED_RUNNING_HISTORY"),
                        provisionalSelfReportWeekly = null, observedLowerBoundWeekly = null,
                        conservativePolicyCeilingWeekly = null, pol013RecordedToSelfReportRatio = null,
                        conflictDetected = false, selfReportReplacementAuthorized = false, diagnosticFlags = flags
                    )
                }
            }
        }

        if (run.coverage == HistoryCoverageStatus.CONFIRMED_ZERO) {
            flags += "BASELINE_CONFIRMED_ZERO"
            return M2CCoverageAwareBaseline(
                coverage = HistoryCoverageStatus.CONFIRMED_ZERO,
                arbitrationStatus = M2CBaselineArbitrationStatus.CONFIRMED_ZERO,
                observedSpanDays = run.observedSpanDays, verifiedSessionCount28d = run.verifiedSessionCount28d, maxGapDays = run.maxGapDays,
                observedWeeklyMean = run.rolling28dWeeklyAverageBelief,
                provisionalSelfReportWeekly = null, observedLowerBoundWeekly = null,
                conservativePolicyCeilingWeekly = null, pol013RecordedToSelfReportRatio = null,
                conflictDetected = false, selfReportReplacementAuthorized = false, diagnosticFlags = flags
            )
        }

        val pol011012Met = run.coverage == HistoryCoverageStatus.CONTINUOUS_COVERAGE &&
            run.observedSpanDays >= Engine15M2CPolicyRegistry.POL_011_SELF_REPORT_REPLACE_MIN_SPAN_DAYS &&
            run.verifiedSessionCount28d >= Engine15M2CPolicyRegistry.POL_012_SELF_REPORT_REPLACE_MIN_SESSIONS &&
            (run.maxGapDays == null || run.maxGapDays <= Engine15M2CPolicyRegistry.POL_012_SELF_REPORT_REPLACE_MAX_GAP_DAYS)

        if (pol011012Met && run.rolling28dWeeklyAverageBelief is BeliefState.Estimated &&
            run.rolling28dWeeklyAverageBelief.authority.isObservedAuthoritative
        ) {
            val recM = run.rolling28dWeeklyAverageBelief.estimate.value
            if (srWeekly != null) {
                val srM = srWeekly.value
                val ratio = recM / srM
                val reportedRatio = ratio.takeIf { it.isFinite() }?.let(DimensionlessRatio::nonNegative)
                if (reportedRatio == null) flags += "RECORDED_TO_SELF_REPORT_RATIO_OVERFLOW"
                val lowConflict = ratio < Engine15M2CPolicyRegistry.POL_013_CONFLICT_LOW_RATIO
                val highConflict = ratio > Engine15M2CPolicyRegistry.POL_013_CONFLICT_HIGH_RATIO
                if (run.selfReportConflictFlag || lowConflict || highConflict) {
                    flags += "POL013_WEEKLY_MILEAGE_CONFLICT_DETECTED"
                    if (recM < srM) {
                        val cap = min(
                            Engine15M2CPolicyRegistry.POL_013_CONSERVATIVE_SELF_REPORT_FACTOR * srM,
                            max(
                                Engine15M2CPolicyRegistry.POL_013_CONSERVATIVE_RECORDED_FACTOR * recM,
                                recM + Engine15M2CPolicyRegistry.POL_013_CONSERVATIVE_RECORDED_BONUS_METERS
                            )
                        )
                        flags += "POL013_CONSERVATIVE_CEILING_APPLIED"
                        extraFlags += flags
                        return M2CCoverageAwareBaseline(
                            coverage = run.coverage,
                            arbitrationStatus = M2CBaselineArbitrationStatus.CONFLICT_CONSERVATIVE_CEILING,
                            observedSpanDays = run.observedSpanDays, verifiedSessionCount28d = run.verifiedSessionCount28d, maxGapDays = run.maxGapDays,
                            observedWeeklyMean = run.rolling28dWeeklyAverageBelief,
                            provisionalSelfReportWeekly = srWeekly,
                            observedLowerBoundWeekly = Meters.of(recM),
                            conservativePolicyCeilingWeekly = Meters.of(cap),
                            pol013RecordedToSelfReportRatio = reportedRatio,
                            conflictDetected = true, selfReportReplacementAuthorized = true, diagnosticFlags = flags
                        )
                    } else {
                        flags += "POL013_REVERSE_RECORDED_ABOVE_SELF_REPORT_CONFLICT_CLARIFY"
                        extraFlags += flags
                        return M2CCoverageAwareBaseline(
                            coverage = run.coverage,
                            arbitrationStatus = M2CBaselineArbitrationStatus.CONTINUOUS_RECORDED_CONFLICT_ABOVE_SELF_REPORT,
                            observedSpanDays = run.observedSpanDays, verifiedSessionCount28d = run.verifiedSessionCount28d, maxGapDays = run.maxGapDays,
                            observedWeeklyMean = run.rolling28dWeeklyAverageBelief,
                            provisionalSelfReportWeekly = srWeekly,
                            observedLowerBoundWeekly = Meters.of(recM),
                            conservativePolicyCeilingWeekly = null,
                            pol013RecordedToSelfReportRatio = reportedRatio,
                            conflictDetected = true, selfReportReplacementAuthorized = true, diagnosticFlags = flags
                        )
                    }
                }
            }
            flags += "BASELINE_CONTINUOUS_RECORDED_AUTHORIZED"
            return M2CCoverageAwareBaseline(
                coverage = run.coverage,
                arbitrationStatus = M2CBaselineArbitrationStatus.CONTINUOUS_RECORDED,
                observedSpanDays = run.observedSpanDays, verifiedSessionCount28d = run.verifiedSessionCount28d, maxGapDays = run.maxGapDays,
                observedWeeklyMean = run.rolling28dWeeklyAverageBelief,
                provisionalSelfReportWeekly = srWeekly,
                observedLowerBoundWeekly = Meters.of(recM),
                conservativePolicyCeilingWeekly = null,
                pol013RecordedToSelfReportRatio = srWeekly?.let { DimensionlessRatio.nonNegative(recM / it.value) },
                conflictDetected = false, selfReportReplacementAuthorized = true, diagnosticFlags = flags
            )
        }

        val lowerBound = when (val b = run.rolling28dWeeklyAverageBelief) {
            is BeliefState.Bounded -> b.lower?.takeIf { it.value > 0.0 }
            is BeliefState.Estimated -> b.estimate.takeIf { it.value > 0.0 }
            else -> null
        }
        val status = when {
            lowerBound != null && srWeekly != null -> {
                flags += "PARTIAL_COVERAGE_PRESERVES_PROVISIONAL_SELF_REPORT"
                M2CBaselineArbitrationStatus.PARTIAL_RECORDED_WITH_SELF_REPORT
            }
            lowerBound != null -> {
                flags += "PARTIAL_COVERAGE_OBSERVED_LOWER_BOUND_ONLY"
                M2CBaselineArbitrationStatus.PARTIAL_RECORDED_ONLY
            }
            srWeekly != null -> {
                flags += "BASELINE_PROVISIONAL_SELF_REPORT_PRESERVED"
                M2CBaselineArbitrationStatus.PROVISIONAL_SELF_REPORT
            }
            else -> {
                flags += "BASELINE_MISSING_UNOBSERVED_NEVER_ZERO"
                M2CBaselineArbitrationStatus.MISSING_UNOBSERVED
            }
        }
        return M2CCoverageAwareBaseline(
            coverage = run.coverage,
            arbitrationStatus = status,
            observedSpanDays = run.observedSpanDays, verifiedSessionCount28d = run.verifiedSessionCount28d, maxGapDays = run.maxGapDays,
            observedWeeklyMean = run.rolling28dWeeklyAverageBelief,
            provisionalSelfReportWeekly = srWeekly,
            observedLowerBoundWeekly = lowerBound,
            conservativePolicyCeilingWeekly = null,
            pol013RecordedToSelfReportRatio = null,
            conflictDetected = false, selfReportReplacementAuthorized = false, diagnosticFlags = flags
        )
    }

    /**
     * MOD-10 single-session r_30 assessment (POL-022 & POL-023; M2C-B01, M2C-C01, D2-04, FINAL-02).
     * Requires both anchors to bind to the exact same reconciledInventoryContentHash and context,
     * and binds effectiveRestrictionState to the exact context, workload hash, and policy version.
     */
    fun assessSingleSessionExposure(
        context: M2BEvaluationContext,
        evaluatedDistance: Meters,
        anchor30d: M2CVerifiedSingleSessionAnchor,
        anchor90d: M2CVerifiedSingleSessionAnchor,
        effectiveRestrictionState: M2CEffectiveRestrictionState,
        overrideAcknowledgment: M2CSingleSessionOverrideAcknowledgment? = null
    ): M2CSingleSessionExposureAssessment {
        require(anchor30d.context == context && anchor30d.bounds.kind == M2CRollingWindowKind.PRECEDING_30D) {
            "FINAL-02: anchor30d must match evaluation context and PRECEDING_30D bounds"
        }
        require(anchor90d.context == context && anchor90d.bounds.kind == M2CRollingWindowKind.PRECEDING_90D) {
            "FINAL-02: anchor90d must match evaluation context and PRECEDING_90D bounds"
        }
        require(anchor30d.observedWorkloadContentHash == anchor90d.observedWorkloadContentHash) {
            "FINAL-02: anchor30d and anchor90d must bind to the same observedWorkloadContentHash"
        }
        require(anchor30d.reconciledInventoryContentHash == anchor90d.reconciledInventoryContentHash) {
            "FINAL-02: anchor30d and anchor90d must bind to the same reconciledInventoryContentHash"
        }
        require(anchor30d.policyVersion == Engine15M2CPolicyRegistry.POLICY_VERSION &&
            anchor90d.policyVersion == Engine15M2CPolicyRegistry.POLICY_VERSION) {
            "FINAL-02: anchor policyVersion mismatch"
        }
        require(effectiveRestrictionState.context == context &&
            effectiveRestrictionState.observedWorkloadContentHash == anchor30d.observedWorkloadContentHash &&
            effectiveRestrictionState.policyVersion == Engine15M2CPolicyRegistry.POLICY_VERSION) {
            "D2-04: effectiveRestrictionState must match exact assessment context, workload hash, and policy version"
        }

        val notes = mutableListOf(
            "MOD10_FLOOR_BOUND_TO_POL023_PER_CANONICAL_ERRATA",
            "ANCHOR_30D_COVERAGE_NOT_INDEPENDENTLY_CONFIRMED_COMPLETE_30D_PRE_M9"
        )
        if (anchor30d.completenessKind == M2CAnchorCompletenessKind.VERIFIED_MAX_AMONG_AVAILABLE_RECORDINGS) {
            notes += "ANCHOR_30D_VERIFIED_MAX_AMONG_AVAILABLE_RECORDINGS_ONLY"
        }
        if (anchor90d.completenessKind == M2CAnchorCompletenessKind.VERIFIED_MAX_AMONG_AVAILABLE_RECORDINGS) {
            notes += "ANCHOR_90D_VERIFIED_MAX_AMONG_AVAILABLE_RECORDINGS_ONLY"
        }

        val healthBlocksOverride = effectiveRestrictionState.hold ||
            effectiveRestrictionState.recoveryOnly ||
            M2BRestrictionReason.EVALUATION_CONTEXT_MISMATCH in effectiveRestrictionState.effectiveReasons ||
            M2BRestrictionReason.UNKNOWN_STATE in effectiveRestrictionState.effectiveReasons

        val ackValid = overrideAcknowledgment != null &&
            overrideAcknowledgment.accountId == context.accountId &&
            overrideAcknowledgment.evaluatedFor == context &&
            overrideAcknowledgment.proposedDistance == evaluatedDistance &&
            overrideAcknowledgment.policyVersion == Engine15M2CPolicyRegistry.POLICY_VERSION &&
            overrideAcknowledgment.acknowledgedRestrictionReasons == effectiveRestrictionState.effectiveReasons
        if (overrideAcknowledgment != null && !ackValid) {
            notes += "OVERRIDE_ACKNOWLEDGMENT_BINDING_MISMATCH_REJECTED"
        }

        if (!anchor30d.isObservedAuthoritativeExact) {
            val band = when (anchor30d.distanceBelief) {
                is BeliefState.Bounded -> {
                    notes += "AMBIGUOUS_BOUNDED_30D_BASELINE_NOT_PROMOTED_TO_EXACT"
                    M2CSingleSessionSpikeBand.NOT_APPLICABLE_AMBIGUOUS_BOUNDED_BASELINE
                }
                is BeliefState.Estimated -> {
                    notes += "UNVERIFIED_AUTHORITY_30D_BASELINE_REJECTED"
                    M2CSingleSessionSpikeBand.NOT_APPLICABLE_UNVERIFIED_AUTHORITY
                }
                else -> {
                    notes += "NO_VERIFIED_30D_LONGEST_RUN_BASELINE"
                    M2CSingleSessionSpikeBand.NOT_APPLICABLE_MISSING_BASELINE
                }
            }
            val overrideElig = if (healthBlocksOverride) {
                M2CSingleSessionOverrideEligibility.INELIGIBLE_ACTIVE_HEALTH_RESTRICTION
            } else {
                M2CSingleSessionOverrideEligibility.INELIGIBLE_NO_VERIFIED_30D_ANCHOR
            }
            return M2CSingleSessionExposureAssessment(
                evaluatedFor = context,
                observedWorkloadContentHash = anchor30d.observedWorkloadContentHash,
                reconciledInventoryContentHash = anchor30d.reconciledInventoryContentHash,
                evaluatedDistance = evaluatedDistance,
                anchor30d = anchor30d,
                anchor90d = anchor90d,
                r30Ratio = null,
                defaultGuardrailCapMeters = null,
                spikeBand = band,
                policyGuardrailExceeded = false,
                observationalRiskSignalActive = false,
                overrideEligibility = overrideElig,
                overrideAcknowledgmentMatched = ackValid,
                overrideEligibleAndAcknowledgedPreM9 = false,
                healthRestrictionBlocksOverride = healthBlocksOverride,
                notes = notes
            )
        }

        val base30M = anchor30d.exactObservedMeters!!
        if (base30M < Engine15M2CPolicyRegistry.POL_023_SINGLE_SESSION_BASELINE_FLOOR_METERS) {
            notes += "BELOW_POL023_4000M_BASELINE_FLOOR_STARTER_RAMP_GOVERNS"
            val overrideElig = if (healthBlocksOverride) {
                M2CSingleSessionOverrideEligibility.INELIGIBLE_ACTIVE_HEALTH_RESTRICTION
            } else {
                M2CSingleSessionOverrideEligibility.INELIGIBLE_BELOW_BASELINE_FLOOR
            }
            return M2CSingleSessionExposureAssessment(
                evaluatedFor = context,
                observedWorkloadContentHash = anchor30d.observedWorkloadContentHash,
                reconciledInventoryContentHash = anchor30d.reconciledInventoryContentHash,
                evaluatedDistance = evaluatedDistance,
                anchor30d = anchor30d,
                anchor90d = anchor90d,
                r30Ratio = null,
                defaultGuardrailCapMeters = null,
                spikeBand = M2CSingleSessionSpikeBand.NOT_APPLICABLE_BELOW_4000M_FLOOR,
                policyGuardrailExceeded = false,
                observationalRiskSignalActive = false,
                overrideEligibility = overrideElig,
                overrideAcknowledgmentMatched = ackValid,
                overrideEligibleAndAcknowledgedPreM9 = false,
                healthRestrictionBlocksOverride = healthBlocksOverride,
                notes = notes
            )
        }

        val ratio = evaluatedDistance.value / base30M
        val defaultCap = base30M * Engine15M2CPolicyRegistry.POL_022_SINGLE_SESSION_GUARDRAIL_RATIO
        if (!defaultCap.isFinite()) notes += "SINGLE_SESSION_DISTANCE_CAP_OVERFLOW"
        val exceeded = ratio > Engine15M2CPolicyRegistry.POL_022_SINGLE_SESSION_GUARDRAIL_RATIO
        val band = when {
            !exceeded -> M2CSingleSessionSpikeBand.WITHIN_DEFAULT_GUARDRAIL_LE_1_10
            ratio <= Engine15M2CPolicyRegistry.MOD_10_OBSERVATIONAL_SMALL_SPIKE_MAX_RATIO -> M2CSingleSessionSpikeBand.SMALL_SPIKE_GT_1_10_TO_1_30
            ratio <= Engine15M2CPolicyRegistry.MOD_10_OBSERVATIONAL_MODERATE_SPIKE_MAX_RATIO -> M2CSingleSessionSpikeBand.MODERATE_SPIKE_GT_1_30_TO_2_00
            else -> M2CSingleSessionSpikeBand.LARGE_SPIKE_GT_2_00
        }
        if (exceeded) notes += "SINGLE_SESSION_PROGRESSION_ABOVE_RATIO_L7_SIGNAL"

        val max90M = anchor90d.exactObservedMeters
        val overrideElig = when {
            !exceeded -> M2CSingleSessionOverrideEligibility.NOT_NEEDED
            healthBlocksOverride -> M2CSingleSessionOverrideEligibility.INELIGIBLE_ACTIVE_HEALTH_RESTRICTION
            max90M == null || max90M <= 0.0 -> M2CSingleSessionOverrideEligibility.INELIGIBLE_NO_VERIFIED_90D_ANCHOR
            evaluatedDistance.value <= max90M -> M2CSingleSessionOverrideEligibility.ELIGIBLE_WITHIN_90D_VERIFIED_MAX_EXPLICIT_ACK_REQUIRED
            else -> M2CSingleSessionOverrideEligibility.INELIGIBLE_EXCEEDS_90D_VERIFIED_MAX
        }
        val eligibleAndAcked = ackValid && !healthBlocksOverride &&
            overrideElig == M2CSingleSessionOverrideEligibility.ELIGIBLE_WITHIN_90D_VERIFIED_MAX_EXPLICIT_ACK_REQUIRED
        if (eligibleAndAcked) notes += "POL022_OVERRIDE_ELIGIBLE_PRE_M9_NON_PRODUCTION_RISK_SIGNAL_RETAINED"

        return M2CSingleSessionExposureAssessment(
            evaluatedFor = context,
            observedWorkloadContentHash = anchor30d.observedWorkloadContentHash,
            reconciledInventoryContentHash = anchor30d.reconciledInventoryContentHash,
            evaluatedDistance = evaluatedDistance,
            anchor30d = anchor30d,
            anchor90d = anchor90d,
            r30Ratio = DimensionlessRatio.nonNegative(ratio),
            defaultGuardrailCapMeters = defaultCap.takeIf { it.isFinite() }?.let(Meters::of),
            spikeBand = band,
            policyGuardrailExceeded = exceeded,
            observationalRiskSignalActive = exceeded,
            overrideEligibility = overrideElig,
            overrideAcknowledgmentMatched = ackValid,
            overrideEligibleAndAcknowledgedPreM9 = eligibleAndAcked,
            healthRestrictionBlocksOverride = healthBlocksOverride,
            notes = notes
        )
    }

    /**
     * Descriptive 4-week distribution & zero-mean-guarded CV summary (M2C-B03, M2C-B06, D2-03, FINAL-04).
     * Distinguishes exact recorded subtotals from complete actual weekly workload via coverage,
     * excludes partial/bounded weeks and unconfirmed missing-data gaps from point statistics,
     * and guards all midpoints/variances against IEEE-754 overflow.
     */
    fun summarizeWeeklyDistribution(
        evalDate: LocalDate,
        records28d: List<M2CVerifiedRunningSessionRecord>,
        coverage: HistoryCoverageStatus,
        weeklyConfirmations: List<M2CWeeklyConfirmationInput> = emptyList(),
        incompleteDistanceGroups: Set<String> = M2CInventoryValidator.incompleteAmbiguousDistanceGroups(records28d, emptyList())
    ): M2CWeeklyDistributionSummary {
        val flags = linkedSetOf<String>()
        val confirmationsByStart = linkedMapOf<LocalDate, Boolean>()
        for ((weekStart, grp) in weeklyConfirmations.groupBy { it.weekStartDate }.toSortedMap()) {
            val distinctDecisions = grp.map { it.confirmedSparseOrRest }.distinct()
            if (distinctDecisions.size > 1) {
                flags += "CONFLICTING_WEEKLY_CONFIRMATION_TREATED_AS_UNCONFIRMED_GAP"
                confirmationsByStart[weekStart] = false
            } else {
                confirmationsByStart[weekStart] = distinctDecisions.single()
            }
        }

        val buckets = (0 until Engine15M2CPolicyRegistry.TRAILING_FOUR_WEEK_COUNT).map { offset ->
            val end = evalDate.minusDays(1L + offset * 7L)
            val start = end.minusDays(6L)
            val inWeek = records28d.filter { it.attributedLocalDate in start..end }
            val confirmedSparse = confirmationsByStart[start] == true || coverage == HistoryCoverageStatus.CONFIRMED_ZERO
            val status = when {
                inWeek.isNotEmpty() -> M2CConfirmedWeekStatus.OBSERVED_ACTIVE_WEEK
                confirmedSparse -> M2CConfirmedWeekStatus.CONFIRMED_SPARSE_OR_REST_WEEK
                else -> M2CConfirmedWeekStatus.UNCONFIRMED_MISSING_DATA_GAP
            }
            val distBelief = aggregateDistanceForRecords(inWeek, status, incompleteDistanceGroups)
            val durBelief = aggregateDurationForRecords(inWeek, status)
            val activeDays = inWeek
                .filter { it.workloadEligibility != WorkloadAggregationEligibility.BLOCKED_COLLISION }
                .map { it.attributedLocalDate }.distinct().size
            val verifiedCount = inWeek.count { it.workloadEligibility == WorkloadAggregationEligibility.ELIGIBLE_VERIFIED_OBSERVED }
            M2CWeeklyBucketTotal(offset, start, end, distBelief, durBelief, verifiedCount, activeDays, status)
        }

        val activeCount = buckets.count { it.confirmationStatus == M2CConfirmedWeekStatus.OBSERVED_ACTIVE_WEEK }
        val sparseCount = buckets.count { it.confirmationStatus == M2CConfirmedWeekStatus.CONFIRMED_SPARSE_OR_REST_WEEK }
        val gapCount = buckets.count { it.confirmationStatus == M2CConfirmedWeekStatus.UNCONFIRMED_MISSING_DATA_GAP }
        val partialOrBoundedCount = buckets.count {
            it.confirmationStatus == M2CConfirmedWeekStatus.OBSERVED_ACTIVE_WEEK && it.distanceBelief !is BeliefState.Estimated
        }

        val exactTotals = buckets
            .filter { it.confirmationStatus != M2CConfirmedWeekStatus.UNCONFIRMED_MISSING_DATA_GAP }
            .mapNotNull { (it.distanceBelief as? BeliefState.Estimated)?.estimate?.value }
            .sorted()

        val n = exactTotals.size
        val sumExact = if (n >= 1) Engine15M2CCanonical.compensatedSum(exactTotals) else Double.NaN
        val meanVal = if (n >= 1 && sumExact.isFinite()) (sumExact / n.toDouble()).takeIf { it.isFinite() } else null
        if (n >= 1 && meanVal == null) {
            flags += "WEEKLY_DISTRIBUTION_NUMERICAL_OVERFLOW_EXCLUDED"
        }

        val medianVal = when {
            n == 0 -> null
            n % 2 == 1 -> exactTotals[n / 2]
            else -> ((exactTotals[n / 2 - 1] / 2.0) + (exactTotals[n / 2] / 2.0)).takeIf { it.isFinite() }
        }
        val trimmedVal = when {
            n == 4 -> ((exactTotals[1] / 2.0) + (exactTotals[2] / 2.0)).takeIf { it.isFinite() }
            n >= 1 -> medianVal
            else -> null
        }

        val sampleSdVal = if (n >= 2 && meanVal != null) {
            val sqDiffs = exactTotals.map { v -> val d = v - meanVal; d * d }
            val sumSq = Engine15M2CCanonical.compensatedSum(sqDiffs)
            if (sumSq.isFinite() && sumSq >= 0.0) {
                sqrt(sumSq / (n - 1).toDouble()).takeIf { it.isFinite() }
            } else {
                flags += "WEEKLY_DISTRIBUTION_NUMERICAL_OVERFLOW_EXCLUDED"
                null
            }
        } else null

        val cvVal = if (sampleSdVal != null && meanVal != null && meanVal > Engine15M2CPolicyRegistry.ZERO_MEAN_CV_GUARD_METERS) {
            val cv = sampleSdVal / meanVal
            if (cv.isFinite() && cv >= 0.0) DimensionlessRatio.nonNegative(cv) else null
        } else {
            if (n >= 2 && meanVal != null && meanVal <= Engine15M2CPolicyRegistry.ZERO_MEAN_CV_GUARD_METERS) {
                flags += "ZERO_MEAN_CV_GUARD_APPLIED"
            }
            null
        }

        val completeActualCount = 0 // No per-week complete-capture evidence in the frozen contract.
        if (n > 0) {
            flags += "WEEKLY_TOTALS_RECORDED_SUBTOTALS_ONLY_COVERAGE_INCOMPLETE"
        }

        val isolatedSparse = activeCount == 3 && sparseCount == 1 && gapCount == 0 && partialOrBoundedCount == 0
        if (isolatedSparse) flags += "ISOLATED_CONFIRMED_SPARSE_WEEK_DISTINGUISHED"
        if (gapCount > 0) flags += "UNCONFIRMED_MISSING_DATA_GAP_WEEKS_EXCLUDED_FROM_CV"
        if (partialOrBoundedCount > 0) flags += "PARTIAL_OR_BOUNDED_WEEKS_EXCLUDED_FROM_POINT_STATISTICS"
        flags += "WEEKLY_CV_THRESHOLD_UNAPPROVED_DESCRIPTIVE_ONLY"

        return M2CWeeklyDistributionSummary(
            weeklyBuckets = buckets,
            exactRecordedSubtotalWeekCount = n,
            completeActualWeeklyWorkloadCount = completeActualCount,
            partialOrBoundedWeekCount = partialOrBoundedCount,
            activeWeekCount = activeCount,
            confirmedSparseWeekCount = sparseCount,
            unconfirmedGapWeekCount = gapCount,
            coverageQualification = coverage,
            meanWeeklyDistance = meanVal?.let(Meters::of),
            medianWeeklyDistance = medianVal?.let(Meters::of),
            trimmedMeanWeeklyDistance = trimmedVal?.let(Meters::of),
            sampleStdDevWeeklyDistance = sampleSdVal?.let(Meters::of),
            coefficientOfVariation = cvVal,
            isolatedConfirmedSparseWeekPattern = isolatedSparse,
            diagnosticFlags = flags
        )
    }

    private fun buildWindowExposure(
        bounds: M2CWindowBounds,
        records: List<M2CVerifiedRunningSessionRecord>,
        authoritativeDistanceBelief: BeliefState<Meters>?,
        authoritativeLongestBelief: BeliefState<Meters>?,
        coverage: HistoryCoverageStatus,
        incompleteDistanceGroups: Set<String>
    ): M2CRunningWindowExposure {
        val verified = records.filter { it.workloadEligibility == WorkloadAggregationEligibility.ELIGIBLE_VERIFIED_OBSERVED }
        val ambiguous = records.filter { it.workloadEligibility == WorkloadAggregationEligibility.AMBIGUOUS_BOUNDED_ONLY }
        val blocked = records.filter { it.workloadEligibility == WorkloadAggregationEligibility.BLOCKED_COLLISION }
        val unknownDist = verified.count { it.distance == null }
        val hrQuarValidDist = verified.count {
            it.distance != null && it.distanceQuality == TelemetryQualityStatus.VALID_OBSERVED &&
                (it.hrQuality == TelemetryQualityStatus.QUARANTINED_CADENCE_LOCK ||
                    it.hrQuality == TelemetryQualityStatus.SUSPECTED_CADENCE_LOCK ||
                    it.hrQuality == TelemetryQualityStatus.QUARANTINED_UNRELIABLE)
        }
        val activeDays = records
            .filter { it.workloadEligibility != WorkloadAggregationEligibility.BLOCKED_COLLISION }
            .map { it.attributedLocalDate }.distinct().size

        val weekStatus = if (records.isNotEmpty()) M2CConfirmedWeekStatus.OBSERVED_ACTIVE_WEEK
        else if (coverage == HistoryCoverageStatus.CONFIRMED_ZERO) M2CConfirmedWeekStatus.CONFIRMED_SPARSE_OR_REST_WEEK
        else M2CConfirmedWeekStatus.UNCONFIRMED_MISSING_DATA_GAP

        val incompleteGroup = ambiguous.any { (it.ambiguityGroupKey ?: it.canonicalActivityId) in incompleteDistanceGroups }
        val distBelief = authoritativeDistanceBelief?.takeUnless { incompleteGroup }
            ?: aggregateDistanceForRecords(records, weekStatus, incompleteDistanceGroups)
        val durBelief = aggregateDurationForRecords(records, weekStatus)
        val longestDistBelief = authoritativeLongestBelief?.takeUnless { incompleteGroup }
            ?: aggregateLongestDistance(records, coverage, incompleteDistanceGroups)
        val longestDurBelief = aggregateLongestDuration(records, coverage)

        return M2CRunningWindowExposure(
            bounds = bounds,
            totalDistanceBelief = distBelief,
            totalDurationBelief = durBelief,
            longestSessionDistanceBelief = longestDistBelief,
            longestSessionDurationBelief = longestDurBelief,
            verifiedSessionCount = verified.size,
            distinctActiveDayCount = activeDays,
            ambiguousSessionCount = ambiguous.size,
            blockedCollisionCount = blocked.size,
            unknownDistanceSessionCount = unknownDist,
            hrQuarantinedButDistanceValidCount = hrQuarValidDist,
            sessions = records
        )
    }

    private fun aggregateDistanceForRecords(
        records: List<M2CVerifiedRunningSessionRecord>,
        status: M2CConfirmedWeekStatus,
        incompleteDistanceGroups: Set<String>
    ): BeliefState<Meters> {
        if (records.isEmpty()) {
            return if (status == M2CConfirmedWeekStatus.CONFIRMED_SPARSE_OR_REST_WEEK) {
                BeliefState.Estimated(Meters.ZERO, EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, UncertaintyKind.NONE)
            } else BeliefState.Unknown("NO_OBSERVED_RUNNING_DISTANCE")
        }
        val verified = records.filter { it.workloadEligibility == WorkloadAggregationEligibility.ELIGIBLE_VERIFIED_OBSERVED }
        val ambiguous = records.filter { it.workloadEligibility == WorkloadAggregationEligibility.AMBIGUOUS_BOUNDED_ONLY }
        val blockedCount = records.count { it.workloadEligibility == WorkloadAggregationEligibility.BLOCKED_COLLISION }
        val unknownDistCount = verified.count { it.distance == null || it.distanceQuality != TelemetryQualityStatus.VALID_OBSERVED }

        val validVerifiedDists = verified.sorted()
            .filter { it.distanceQuality == TelemetryQualityStatus.VALID_OBSERVED }
            .mapNotNull { it.distance?.value }
        val verifiedSum = Engine15M2CCanonical.compensatedSum(validVerifiedDists)
        if (!verifiedSum.isFinite()) return BeliefState.Unknown("NUMERICAL_OVERFLOW_EXCLUDED")

        val ambigGroups = ambiguous.groupBy { it.ambiguityGroupKey ?: it.canonicalActivityId }.toSortedMap()
        val groupMins = mutableListOf<Double>()
        val groupMaxs = mutableListOf<Double>()
        var ambigMissing = false
        for ((key, grp) in ambigGroups) {
            val ds = grp.filter { it.distanceQuality == TelemetryQualityStatus.VALID_OBSERVED }
                .mapNotNull { it.distance?.value }
            val incomplete = key in incompleteDistanceGroups || ds.size < grp.size
            if (incomplete) ambigMissing = true
            if (ds.isNotEmpty()) {
                if (!incomplete) groupMins += ds.min()
                val gSum = Engine15M2CCanonical.compensatedSum(ds.sorted())
                if (!gSum.isFinite()) return BeliefState.Unknown("NUMERICAL_OVERFLOW_EXCLUDED")
                groupMaxs += gSum
            }
        }
        val ambigMin = Engine15M2CCanonical.compensatedSum(groupMins.sorted())
        val ambigMax = Engine15M2CCanonical.compensatedSum(groupMaxs.sorted())
        if (!ambigMin.isFinite()) return BeliefState.Unknown("NUMERICAL_OVERFLOW_EXCLUDED")

        if (ambiguous.isEmpty() && blockedCount == 0 && unknownDistCount == 0) {
            return if (validVerifiedDists.isNotEmpty()) {
                BeliefState.Estimated(Meters.of(verifiedSum), EvidenceAuthorityClass.SENSOR_SUPPORTED, UncertaintyKind.NONE)
            } else BeliefState.Unknown("NO_VALID_RUNNING_DISTANCE")
        }
        val lo = verifiedSum + ambigMin
        if (!lo.isFinite()) return BeliefState.Unknown("NUMERICAL_OVERFLOW_EXCLUDED")
        val hiRaw = if (ambigMax.isFinite()) verifiedSum + ambigMax else Double.NaN
        val hi = if (blockedCount > 0 || unknownDistCount > 0 || ambigMissing || !hiRaw.isFinite()) null else Meters.of(hiRaw)
        return if (lo > 0.0 || hi != null) {
            BeliefState.Bounded(Meters.of(lo), hi, EvidenceAuthorityClass.SENSOR_SUPPORTED, UncertaintyKind.COACHING_GUIDANCE_BAND)
        } else BeliefState.Unknown("BLOCKED_OR_UNKNOWN_RUNNING_DISTANCE")
    }

    private fun aggregateDurationForRecords(
        records: List<M2CVerifiedRunningSessionRecord>,
        status: M2CConfirmedWeekStatus
    ): BeliefState<Seconds> {
        if (records.isEmpty()) {
            return if (status == M2CConfirmedWeekStatus.CONFIRMED_SPARSE_OR_REST_WEEK) {
                BeliefState.Estimated(Seconds.ZERO, EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, UncertaintyKind.NONE)
            } else BeliefState.Unknown("NO_OBSERVED_RUNNING_DURATION")
        }
        val verified = records.filter { it.workloadEligibility == WorkloadAggregationEligibility.ELIGIBLE_VERIFIED_OBSERVED }
        val ambiguous = records.filter { it.workloadEligibility == WorkloadAggregationEligibility.AMBIGUOUS_BOUNDED_ONLY }
        val blockedCount = records.count { it.workloadEligibility == WorkloadAggregationEligibility.BLOCKED_COLLISION }
        val unknownDurCount = verified.count { it.duration == null || it.durationQuality != TelemetryQualityStatus.VALID_OBSERVED }

        val validVerifiedDurs = verified.sorted()
            .filter { it.durationQuality == TelemetryQualityStatus.VALID_OBSERVED }
            .mapNotNull { it.duration?.value }
        val verifiedSum = Engine15M2CCanonical.compensatedSum(validVerifiedDurs)
        if (!verifiedSum.isFinite()) return BeliefState.Unknown("NUMERICAL_OVERFLOW_EXCLUDED")

        val ambigGroups = ambiguous.groupBy { it.ambiguityGroupKey ?: it.canonicalActivityId }.toSortedMap().values
        val groupMins = mutableListOf<Double>()
        val groupMaxs = mutableListOf<Double>()
        var ambigMissing = false
        for (grp in ambigGroups) {
            val ts = grp.filter { it.durationQuality == TelemetryQualityStatus.VALID_OBSERVED }
                .mapNotNull { it.duration?.value }
            if (ts.size < grp.size) ambigMissing = true
            if (ts.isNotEmpty()) {
                if (ts.size == grp.size) groupMins += ts.min()
                val gSum = Engine15M2CCanonical.compensatedSum(ts.sorted())
                if (!gSum.isFinite()) return BeliefState.Unknown("NUMERICAL_OVERFLOW_EXCLUDED")
                groupMaxs += gSum
            }
        }
        val ambigMin = Engine15M2CCanonical.compensatedSum(groupMins.sorted())
        val ambigMax = Engine15M2CCanonical.compensatedSum(groupMaxs.sorted())
        if (!ambigMin.isFinite()) return BeliefState.Unknown("NUMERICAL_OVERFLOW_EXCLUDED")

        if (ambiguous.isEmpty() && blockedCount == 0 && unknownDurCount == 0) {
            return if (validVerifiedDurs.isNotEmpty()) {
                BeliefState.Estimated(Seconds.of(verifiedSum), EvidenceAuthorityClass.SENSOR_SUPPORTED, UncertaintyKind.NONE)
            } else BeliefState.Unknown("NO_VALID_RUNNING_DURATION")
        }
        val lo = verifiedSum + ambigMin
        if (!lo.isFinite()) return BeliefState.Unknown("NUMERICAL_OVERFLOW_EXCLUDED")
        val hiRaw = if (ambigMax.isFinite()) verifiedSum + ambigMax else Double.NaN
        val hi = if (blockedCount > 0 || unknownDurCount > 0 || ambigMissing || !hiRaw.isFinite()) null else Seconds.of(hiRaw)
        return if (lo > 0.0 || hi != null) {
            BeliefState.Bounded(Seconds.of(lo), hi, EvidenceAuthorityClass.SENSOR_SUPPORTED, UncertaintyKind.COACHING_GUIDANCE_BAND)
        } else BeliefState.Unknown("BLOCKED_OR_UNKNOWN_RUNNING_DURATION")
    }

    private fun aggregateLongestDistance(
        records: List<M2CVerifiedRunningSessionRecord>,
        coverage: HistoryCoverageStatus,
        incompleteDistanceGroups: Set<String>
    ): BeliefState<Meters> {
        if (records.isEmpty()) {
            return if (coverage == HistoryCoverageStatus.CONFIRMED_ZERO) {
                BeliefState.Estimated(Meters.ZERO, EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, UncertaintyKind.NONE)
            } else BeliefState.Unknown("NO_RUNNING_SESSIONS_IN_WINDOW")
        }
        val verifiedMax = records.filter {
            it.workloadEligibility == WorkloadAggregationEligibility.ELIGIBLE_VERIFIED_OBSERVED &&
                it.distanceQuality == TelemetryQualityStatus.VALID_OBSERVED
        }.mapNotNull { it.distance?.value?.takeIf { d -> d > 0.0 } }.maxOrNull()

        val ambigRecords = records.filter { it.workloadEligibility == WorkloadAggregationEligibility.AMBIGUOUS_BOUNDED_ONLY }
        val groupMins = mutableListOf<Double>()
        val groupMaxs = mutableListOf<Double>()
        for ((key, grp) in ambigRecords.groupBy { it.ambiguityGroupKey ?: it.canonicalActivityId }) {
            val validDists = grp.filter { it.distanceQuality == TelemetryQualityStatus.VALID_OBSERVED }
                .mapNotNull { it.distance?.value?.takeIf { d -> d > 0.0 } }
            if (key !in incompleteDistanceGroups && validDists.size == grp.size && validDists.isNotEmpty()) {
                groupMins += validDists.min()
                groupMaxs += validDists.max()
            } else if (validDists.isNotEmpty()) {
                groupMaxs += validDists.max()
            }
        }
        val blockedOrUnknown = records.any {
            it.workloadEligibility == WorkloadAggregationEligibility.BLOCKED_COLLISION ||
                (it.workloadEligibility != WorkloadAggregationEligibility.BLOCKED_COLLISION &&
                    (it.distance == null || it.distanceQuality != TelemetryQualityStatus.VALID_OBSERVED))
        }
        if (ambigRecords.isEmpty() && !blockedOrUnknown) {
            return verifiedMax?.let { BeliefState.Estimated(Meters.of(it), EvidenceAuthorityClass.SENSOR_SUPPORTED, UncertaintyKind.NONE) }
                ?: BeliefState.Unknown("NO_LONGEST_SESSION_DISTANCE")
        }
        val lo = listOfNotNull(verifiedMax, groupMins.maxOrNull()).maxOrNull()
        val hi = if (blockedOrUnknown || ambigRecords.any {
                (it.ambiguityGroupKey ?: it.canonicalActivityId) in incompleteDistanceGroups
            }) null else listOfNotNull(verifiedMax, groupMaxs.maxOrNull()).maxOrNull()
        return lo?.let {
            BeliefState.Bounded(Meters.of(it), hi?.let { h -> Meters.of(max(it, h)) }, EvidenceAuthorityClass.SENSOR_SUPPORTED, UncertaintyKind.COACHING_GUIDANCE_BAND)
        } ?: BeliefState.Unknown("UNKNOWN_LONGEST_SESSION_DISTANCE")
    }

    private fun aggregateLongestDuration(
        records: List<M2CVerifiedRunningSessionRecord>,
        coverage: HistoryCoverageStatus
    ): BeliefState<Seconds> {
        if (records.isEmpty()) {
            return if (coverage == HistoryCoverageStatus.CONFIRMED_ZERO) {
                BeliefState.Estimated(Seconds.ZERO, EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, UncertaintyKind.NONE)
            } else BeliefState.Unknown("NO_RUNNING_SESSIONS_IN_WINDOW")
        }
        val verifiedMax = records.filter {
            it.workloadEligibility == WorkloadAggregationEligibility.ELIGIBLE_VERIFIED_OBSERVED &&
                it.durationQuality == TelemetryQualityStatus.VALID_OBSERVED
        }.mapNotNull { it.duration?.value?.takeIf { d -> d > 0.0 } }.maxOrNull()

        val ambigRecords = records.filter { it.workloadEligibility == WorkloadAggregationEligibility.AMBIGUOUS_BOUNDED_ONLY }
        val groupMins = mutableListOf<Double>()
        val groupMaxs = mutableListOf<Double>()
        for (grp in ambigRecords.groupBy { it.ambiguityGroupKey ?: it.canonicalActivityId }.values) {
            val validDurs = grp.filter { it.durationQuality == TelemetryQualityStatus.VALID_OBSERVED }
                .mapNotNull { it.duration?.value?.takeIf { d -> d > 0.0 } }
            if (validDurs.size == grp.size && validDurs.isNotEmpty()) {
                groupMins += validDurs.min()
                groupMaxs += validDurs.max()
            } else if (validDurs.isNotEmpty()) {
                groupMaxs += validDurs.max()
            }
        }
        val blockedOrUnknown = records.any {
            it.workloadEligibility == WorkloadAggregationEligibility.BLOCKED_COLLISION ||
                (it.workloadEligibility != WorkloadAggregationEligibility.BLOCKED_COLLISION &&
                    (it.duration == null || it.durationQuality != TelemetryQualityStatus.VALID_OBSERVED))
        }
        if (ambigRecords.isEmpty() && !blockedOrUnknown) {
            return verifiedMax?.let { BeliefState.Estimated(Seconds.of(it), EvidenceAuthorityClass.SENSOR_SUPPORTED, UncertaintyKind.NONE) }
                ?: BeliefState.Unknown("NO_LONGEST_SESSION_DURATION")
        }
        val lo = listOfNotNull(verifiedMax, groupMins.maxOrNull()).maxOrNull()
        val hi = if (blockedOrUnknown) null else listOfNotNull(verifiedMax, groupMaxs.maxOrNull()).maxOrNull()
        return lo?.let {
            BeliefState.Bounded(Seconds.of(it), hi?.let { h -> Seconds.of(max(it, h)) }, EvidenceAuthorityClass.SENSOR_SUPPORTED, UncertaintyKind.COACHING_GUIDANCE_BAND)
        } ?: BeliefState.Unknown("UNKNOWN_LONGEST_SESSION_DURATION")
    }

    private fun inferEmptyCoverage(sr: ProvisionalSelfReportWorkload?): HistoryCoverageStatus = when {
        sr?.confirmedZero == true -> HistoryCoverageStatus.CONFIRMED_ZERO
        sr?.weeklyVolume != null && sr.basis == RecentMileageBasis.SELF_REPORTED_AVERAGE -> HistoryCoverageStatus.PROVISIONAL_SELF_REPORT
        sr?.longestSession != null && sr.longestSession.value > 0.0 && sr.basis == RecentMileageBasis.SELF_REPORTED_AVERAGE ->
            HistoryCoverageStatus.PROVISIONAL_SELF_REPORT
        else -> HistoryCoverageStatus.MISSING_UNOBSERVED
    }
}
