package io.stridelab.privatealpha

import java.time.Duration
import java.time.Instant
import java.util.TreeMap
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Pure, deterministic M2-C engines operating strictly over M2CReconciledEvidenceInventory:
 *  1. MOD-09 Session-RPE internal load & pinned/unpinned multimodal completeness (M2C-A05, M2C-B06, M2C-C05, D2-01, D3-02, FINAL-01, FINAL-03).
 *  2. Ambiguity-aware & partial-aware running intensity-domain duration & distance exposure (M2C-A05, M2C-B06, M2C-C03, D2-02, FINAL-01).
 *  3. Chronological source-qualified E_down downhill aggregation (M2C-A04, M2C-B05, M2C-C05).
 */
internal object Engine15M2CInternalAndDownhillEngine {

    // =========================================================================
    // 1. MOD-09 SESSION-RPE INTERNAL LOAD & MULTIMODAL COMPLETENESS
    // =========================================================================

    fun computeReconciledInternalLoadAndMultimodal(
        context: M2BEvaluationContext,
        bounds28: M2CWindowBounds,
        pinnedRunning28d: List<M2CReconciledRunningEntry>,
        nonRunning28d: List<M2CReconciledNonRunningEntry>,
        runningExposure28d: M2CRunningWindowExposure,
        flags: MutableSet<String>
    ): Pair<M2CRollingInternalLoadSummary, M2CSystemicMultimodalExposure> {
        val records = mutableListOf<M2CSessionRpeLoadRecord>()
        var validRunRpe = 0
        var missingRunRpe = 0
        var validNonRunRpe = 0
        var missingNonRunRpe = 0
        var invalidRpeExcluded = 0

        val verifiedRunLoads = mutableListOf<Double>()
        val ambigRunLoads = mutableListOf<Pair<String, Double>>()
        var anyRunMissingOrBlockedRpe = false

        for (entry in pinnedRunning28d) {
            val rec = entry.record
            if (rec.workloadEligibility == WorkloadAggregationEligibility.BLOCKED_COLLISION) {
                if (rec.workloadEligibility == WorkloadAggregationEligibility.AMBIGUOUS_BOUNDED_ONLY) {
                    ambigRunLoads += (rec.ambiguityGroupKey ?: rec.canonicalActivityId) to 0.0
                }
                missingRunRpe += 1
                anyRunMissingOrBlockedRpe = true
                continue
            }
            val dur = rec.duration?.takeIf { it.value > 0.0 }
            if (dur == null || rec.durationQuality != TelemetryQualityStatus.VALID_OBSERVED) {
                if (rec.workloadEligibility == WorkloadAggregationEligibility.AMBIGUOUS_BOUNDED_ONLY) {
                    ambigRunLoads += (rec.ambiguityGroupKey ?: rec.canonicalActivityId) to 0.0
                }
                missingRunRpe += 1
                anyRunMissingOrBlockedRpe = true
                continue
            }
            val il = if (entry.internalLoadConflict) {
                invalidRpeExcluded += 1
                null
            } else {
                entry.internalLoad
            }

            val (validRpe, loadAu, acceptedRpeAlias) = validatePostSessionRpe(
                il = il,
                sessionAliases = rec.sourceAliases,
                completedAtUtc = rec.completedAtUtc,
                asOf = context.asOf,
                duration = dur,
                flags = flags
            ) { invalidRpeExcluded += 1 }

            // FINAL-03: Rejected RPE aliases never attach; opaqueUpstreamRecordIds stay preserved separately.
            val resolvedAlias = acceptedRpeAlias ?: rec.sourceAliases.firstOrNull()
            val opaqueIds = rec.opaqueUpstreamRecordIds

            records += M2CSessionRpeLoadRecord(
                canonicalActivityId = rec.canonicalActivityId,
                sourceAlias = resolvedAlias,
                opaqueUpstreamRecordIds = opaqueIds,
                discipline = AthleteDiscipline.RUN,
                modality = M2CModalityKind.RUNNING,
                completedLocalDate = rec.completedLocalDate,
                completedAtUtc = rec.completedAtUtc,
                duration = dur,
                cr10Rpe = validRpe,
                loadAu = loadAu
            )

            if (loadAu != null) {
                if (rec.workloadEligibility == WorkloadAggregationEligibility.ELIGIBLE_VERIFIED_OBSERVED) {
                    validRunRpe += 1
                    verifiedRunLoads += loadAu.value
                } else {
                    ambigRunLoads += (rec.ambiguityGroupKey ?: rec.canonicalActivityId) to loadAu.value
                }
            } else {
                if (rec.workloadEligibility == WorkloadAggregationEligibility.AMBIGUOUS_BOUNDED_ONLY) {
                    ambigRunLoads += (rec.ambiguityGroupKey ?: rec.canonicalActivityId) to 0.0
                }
                missingRunRpe += 1
                anyRunMissingOrBlockedRpe = true
            }
        }

        // Non-running multimodal reconciliation preserving pinned vs unpinned separation and ambiguity bounds (D3-02, FINAL-01, FINAL-03).
        val modalityMap = TreeMap<M2CModalityKind, M2CModalityExposureSummary>()
        val nonRunByMod = nonRunning28d.groupBy { it.modality }

        val allPinnedVerNonRunDurs = mutableListOf<Double>()
        val allPinnedAmbigNonRunDurs = mutableListOf<Pair<String, Double>>()
        var anyPinnedNonRunDurMissingOrBlocked = false

        val allUnpinnedVerNonRunDurs = mutableListOf<Double>()
        val allUnpinnedAmbigNonRunDurs = mutableListOf<Pair<String, Double>>()
        var anyUnpinnedNonRunEntries = false

        val allPinnedVerNonRunLoads = mutableListOf<Double>()
        val allPinnedAmbigNonRunLoads = mutableListOf<Pair<String, Double>>()
        var anyPinnedNonRunMissingOrBlockedRpe = false

        val allUnpinnedVerNonRunLoads = mutableListOf<Double>()
        val allUnpinnedAmbigNonRunLoads = mutableListOf<Pair<String, Double>>()

        for ((mod, items) in nonRunByMod.toSortedMap()) {
            val modPinnedVerDurs = mutableListOf<Double>()
            val modPinnedAmbigDurs = mutableListOf<Pair<String, Double>>()
            var modPinnedMissingOrBlockedDur = false

            val modPinnedVerDists = mutableListOf<Double>()
            val modPinnedAmbigDists = mutableListOf<Pair<String, Double>>()
            var modPinnedMissingOrBlockedDist = false

            val modPinnedVerLoads = mutableListOf<Double>()
            val modPinnedAmbigLoads = mutableListOf<Pair<String, Double>>()
            var modPinnedMissingOrBlockedRpe = false

            val modUnpinnedVerDurs = mutableListOf<Double>()
            val modUnpinnedAmbigDurs = mutableListOf<Pair<String, Double>>()
            val modUnpinnedVerDists = mutableListOf<Double>()
            val modUnpinnedAmbigDists = mutableListOf<Pair<String, Double>>()
            val modUnpinnedVerLoads = mutableListOf<Double>()
            val modUnpinnedAmbigLoads = mutableListOf<Pair<String, Double>>()

            var verPinnedCount = 0
            var ambPinnedCount = 0
            var unpinnedDescCount = 0

            for (nr in items) {
                val ambigKey = nr.ambiguityGroupKey ?: nr.exposureId
                if (!nr.pinnedInObservedWorkload) {
                    unpinnedDescCount += 1
                    anyUnpinnedNonRunEntries = true
                    flags += "UNPINNED_NON_RUNNING_DESCRIPTIVE_EXCLUDED_FROM_EXACT_SYSTEMIC"

                    if (nr.workloadEligibility == WorkloadAggregationEligibility.BLOCKED_COLLISION) {
                        missingNonRunRpe += 1
                        continue
                    }
                    val isAmbig = nr.workloadEligibility == WorkloadAggregationEligibility.AMBIGUOUS_BOUNDED_ONLY
                    val durValid = nr.durationQuality == TelemetryQualityStatus.VALID_OBSERVED &&
                        nr.duration != null && nr.duration.value > 0.0
                    val distValid = nr.distanceQuality == TelemetryQualityStatus.VALID_OBSERVED &&
                        nr.distance != null && nr.distance.value > 0.0

                    if (isAmbig && !durValid) {

                        modUnpinnedAmbigDurs += ambigKey to 0.0

                        allUnpinnedAmbigNonRunDurs += ambigKey to 0.0

                        modUnpinnedAmbigLoads += ambigKey to 0.0

                        allUnpinnedAmbigNonRunLoads += ambigKey to 0.0

                    }
                    if (durValid) {
                        val dVal = nr.duration!!.value
                        if (isAmbig) {
                            modUnpinnedAmbigDurs += ambigKey to dVal
                            allUnpinnedAmbigNonRunDurs += ambigKey to dVal
                        } else {
                            modUnpinnedVerDurs += dVal
                            allUnpinnedVerNonRunDurs += dVal
                        }
                    }
                    if (isAmbig && !distValid) {
                        modUnpinnedAmbigDists += ambigKey to 0.0
                    }
                    if (distValid) {
                        val distVal = nr.distance!!.value
                        if (isAmbig) {
                            modUnpinnedAmbigDists += ambigKey to distVal
                        } else {
                            modUnpinnedVerDists += distVal
                        }
                    }

                    if (durValid) {
                        val il = if (nr.internalLoadConflict) {
                            invalidRpeExcluded += 1
                            null
                        } else {
                            nr.internalLoad
                        }
                        val (validRpe, loadAu, acceptedRpeAlias) = validatePostSessionRpe(
                            il = il,
                            sessionAliases = nr.sourceAliases,
                            completedAtUtc = nr.completedAtUtc,
                            asOf = context.asOf,
                            duration = nr.duration!!,
                            flags = flags
                        ) { invalidRpeExcluded += 1 }

                        val resolvedAlias = acceptedRpeAlias ?: nr.sourceAliases.firstOrNull()
                        val opaqueIds = nr.opaqueUpstreamRecordIds

                        records += M2CSessionRpeLoadRecord(
                            canonicalActivityId = nr.exposureId,
                            sourceAlias = resolvedAlias,
                            opaqueUpstreamRecordIds = opaqueIds,
                            discipline = nr.discipline,
                            modality = mod,
                            completedLocalDate = nr.completedAtUtc.atZone(context.zone).toLocalDate(),
                            completedAtUtc = nr.completedAtUtc,
                            duration = nr.duration,
                            cr10Rpe = validRpe,
                            loadAu = loadAu
                        )
                        if (loadAu != null) {
                            if (isAmbig) {
                                modUnpinnedAmbigLoads += ambigKey to loadAu.value
                                allUnpinnedAmbigNonRunLoads += ambigKey to loadAu.value
                            } else {
                                modUnpinnedVerLoads += loadAu.value
                                allUnpinnedVerNonRunLoads += loadAu.value
                            }
                        } else {
                            if (isAmbig) {
                                modUnpinnedAmbigLoads += ambigKey to 0.0
                                allUnpinnedAmbigNonRunLoads += ambigKey to 0.0
                            }
                            missingNonRunRpe += 1
                        }
                    } else {
                        missingNonRunRpe += 1
                    }
                    continue
                }

                // Pinned in ObservedWorkloadState:
                if (nr.workloadEligibility == WorkloadAggregationEligibility.BLOCKED_COLLISION) {
                    modPinnedMissingOrBlockedDur = true
                    modPinnedMissingOrBlockedDist = true
                    anyPinnedNonRunDurMissingOrBlocked = true
                    modPinnedMissingOrBlockedRpe = true
                    anyPinnedNonRunMissingOrBlockedRpe = true
                    missingNonRunRpe += 1
                    continue
                }
                val isAmbig = nr.workloadEligibility == WorkloadAggregationEligibility.AMBIGUOUS_BOUNDED_ONLY
                if (isAmbig) ambPinnedCount += 1 else verPinnedCount += 1

                val durValid = nr.durationQuality == TelemetryQualityStatus.VALID_OBSERVED &&
                    nr.duration != null && nr.duration.value > 0.0
                if (isAmbig && !durValid) {
                    modPinnedAmbigDurs += ambigKey to 0.0
                    allPinnedAmbigNonRunDurs += ambigKey to 0.0
                    modPinnedAmbigLoads += ambigKey to 0.0
                    allPinnedAmbigNonRunLoads += ambigKey to 0.0
                }
                if (durValid) {
                    val dVal = nr.duration!!.value
                    if (isAmbig) {
                        modPinnedAmbigDurs += ambigKey to dVal
                        allPinnedAmbigNonRunDurs += ambigKey to dVal
                    } else {
                        modPinnedVerDurs += dVal
                        allPinnedVerNonRunDurs += dVal
                    }
                } else {
                    modPinnedMissingOrBlockedDur = true
                    anyPinnedNonRunDurMissingOrBlocked = true
                }

                val distValid = nr.distanceQuality == TelemetryQualityStatus.VALID_OBSERVED &&
                    nr.distance != null && nr.distance.value > 0.0
                if (isAmbig && !distValid) {
                    modPinnedAmbigDists += ambigKey to 0.0
                }
                if (distValid) {
                    val distVal = nr.distance!!.value
                    if (isAmbig) {
                        modPinnedAmbigDists += ambigKey to distVal
                    } else {
                        modPinnedVerDists += distVal
                    }
                } else {
                    modPinnedMissingOrBlockedDist = true
                }

                if (durValid) {
                    val il = if (nr.internalLoadConflict) {
                        invalidRpeExcluded += 1
                        null
                    } else {
                        nr.internalLoad
                    }
                    val (validRpe, loadAu, acceptedRpeAlias) = validatePostSessionRpe(
                        il = il,
                        sessionAliases = nr.sourceAliases,
                        completedAtUtc = nr.completedAtUtc,
                        asOf = context.asOf,
                        duration = nr.duration!!,
                        flags = flags
                    ) { invalidRpeExcluded += 1 }

                    val resolvedAlias = acceptedRpeAlias ?: nr.sourceAliases.firstOrNull()
                    val opaqueIds = nr.opaqueUpstreamRecordIds

                    records += M2CSessionRpeLoadRecord(
                        canonicalActivityId = nr.exposureId,
                        sourceAlias = resolvedAlias,
                        opaqueUpstreamRecordIds = opaqueIds,
                        discipline = nr.discipline,
                        modality = mod,
                        completedLocalDate = nr.completedAtUtc.atZone(context.zone).toLocalDate(),
                        completedAtUtc = nr.completedAtUtc,
                        duration = nr.duration,
                        cr10Rpe = validRpe,
                        loadAu = loadAu
                    )
                    if (loadAu != null) {
                        if (isAmbig) {
                            modPinnedAmbigLoads += ambigKey to loadAu.value
                            allPinnedAmbigNonRunLoads += ambigKey to loadAu.value
                        } else {
                            validNonRunRpe += 1
                            modPinnedVerLoads += loadAu.value
                            allPinnedVerNonRunLoads += loadAu.value
                        }
                    } else {
                        if (isAmbig) {
                            modPinnedAmbigLoads += ambigKey to 0.0
                            allPinnedAmbigNonRunLoads += ambigKey to 0.0
                        }
                        modPinnedMissingOrBlockedRpe = true
                        anyPinnedNonRunMissingOrBlockedRpe = true
                        missingNonRunRpe += 1
                    }
                } else {
                    modPinnedMissingOrBlockedRpe = true
                    anyPinnedNonRunMissingOrBlockedRpe = true
                    missingNonRunRpe += 1
                }
            }

            val hasUnpinnedInMod = unpinnedDescCount > 0
            modalityMap[mod] = M2CModalityExposureSummary(
                modality = mod,
                verifiedPinnedSessionCount28d = verPinnedCount,
                ambiguousPinnedSessionCount28d = ambPinnedCount,
                unpinnedDescriptiveSessionCount28d = unpinnedDescCount,
                totalDuration28d = assemblePinnedAndUnpinnedSecondsBelief(
                    pinnedVerified = modPinnedVerDurs,
                    pinnedAmbiguous = modPinnedAmbigDurs,
                    pinnedMissingOrBlocked = modPinnedMissingOrBlockedDur,
                    unpinnedVerified = modUnpinnedVerDurs,
                    unpinnedAmbiguous = modUnpinnedAmbigDurs,
                    hasUnpinnedEntries = hasUnpinnedInMod,
                    emptyCode = "NO_VALID_MODALITY_DURATION"
                ),
                totalDistance28d = assemblePinnedAndUnpinnedMetersBelief(
                    pinnedVerified = modPinnedVerDists,
                    pinnedAmbiguous = modPinnedAmbigDists,
                    pinnedMissingOrBlocked = modPinnedMissingOrBlockedDist,
                    unpinnedVerified = modUnpinnedVerDists,
                    unpinnedAmbiguous = modUnpinnedAmbigDists,
                    hasUnpinnedEntries = hasUnpinnedInMod,
                    emptyCode = "NO_VALID_MODALITY_DISTANCE"
                ),
                internalLoadAu28d = assemblePinnedAndUnpinnedInternalLoadBelief(
                    pinnedVerified = modPinnedVerLoads,
                    pinnedAmbiguous = modPinnedAmbigLoads,
                    pinnedMissingOrBlocked = modPinnedMissingOrBlockedRpe,
                    unpinnedVerified = modUnpinnedVerLoads,
                    unpinnedAmbiguous = modUnpinnedAmbigLoads,
                    hasUnpinnedEntries = hasUnpinnedInMod,
                    emptyCode = "NO_MODALITY_SRPE"
                )
            )
        }

        val runBelief = assembleInternalLoadBelief(
            verifiedLoads = verifiedRunLoads,
            ambiguousLoads = ambigRunLoads,
            anyMissingOrBlocked = anyRunMissingOrBlockedRpe,
            emptyCode = "NO_OBSERVED_RUNNING_SRPE"
        )
        val nonRunBelief = assemblePinnedAndUnpinnedInternalLoadBelief(
            pinnedVerified = allPinnedVerNonRunLoads,
            pinnedAmbiguous = allPinnedAmbigNonRunLoads,
            pinnedMissingOrBlocked = anyPinnedNonRunMissingOrBlockedRpe,
            unpinnedVerified = allUnpinnedVerNonRunLoads,
            unpinnedAmbiguous = allUnpinnedAmbigNonRunLoads,
            hasUnpinnedEntries = anyUnpinnedNonRunEntries,
            emptyCode = "NO_OBSERVED_NON_RUNNING_SRPE"
        )
        val combinedBelief = assemblePinnedAndUnpinnedInternalLoadBelief(
            pinnedVerified = verifiedRunLoads + allPinnedVerNonRunLoads,
            pinnedAmbiguous = ambigRunLoads + allPinnedAmbigNonRunLoads,
            pinnedMissingOrBlocked = anyRunMissingOrBlockedRpe || anyPinnedNonRunMissingOrBlockedRpe,
            unpinnedVerified = allUnpinnedVerNonRunLoads,
            unpinnedAmbiguous = allUnpinnedAmbigNonRunLoads,
            hasUnpinnedEntries = anyUnpinnedNonRunEntries,
            emptyCode = "NO_OBSERVED_SYSTEMIC_SRPE"
        )

        val internalSummary = M2CRollingInternalLoadSummary(
            bounds = bounds28,
            runningLoadAu = runBelief,
            nonRunningSystemicLoadAu = nonRunBelief,
            combinedSystemicLoadAu = combinedBelief,
            validRunningRpeCount = validRunRpe,
            missingRunningRpeCount = missingRunRpe,
            validNonRunningRpeCount = validNonRunRpe,
            missingNonRunningRpeCount = missingNonRunRpe,
            invalidRpeExcludedCount = invalidRpeExcluded,
            records = records
        )

        val totalNonRunDurBelief = assemblePinnedAndUnpinnedSecondsBelief(
            pinnedVerified = allPinnedVerNonRunDurs,
            pinnedAmbiguous = allPinnedAmbigNonRunDurs,
            pinnedMissingOrBlocked = anyPinnedNonRunDurMissingOrBlocked,
            unpinnedVerified = allUnpinnedVerNonRunDurs,
            unpinnedAmbiguous = allUnpinnedAmbigNonRunDurs,
            hasUnpinnedEntries = anyUnpinnedNonRunEntries,
            emptyCode = "NO_NON_RUNNING_DURATION_28D"
        )
        val aerobicModalities = setOf(M2CModalityKind.CYCLING, M2CModalityKind.SWIMMING, M2CModalityKind.ROWING)
        val pinnedAerobicTime = nonRunning28d.any {
            it.pinnedInObservedWorkload && it.modality in aerobicModalities &&
                it.workloadEligibility == WorkloadAggregationEligibility.ELIGIBLE_VERIFIED_OBSERVED &&
                it.durationQuality == TelemetryQualityStatus.VALID_OBSERVED && (it.duration?.value ?: 0.0) > 0.0
        }
        val crossModalMismatch = pinnedAerobicTime && runningExposure28d.verifiedSessionCount == 0
        if (crossModalMismatch) flags += "CROSS_MODAL_RUNNING_TISSUE_MISMATCH"

        val multimodal = M2CSystemicMultimodalExposure(
            byModality = modalityMap,
            totalNonRunningDuration28d = totalNonRunDurBelief,
            crossModalRunningTissueMismatchSignal = crossModalMismatch,
            nonRunningFabricatesRunningImpactTolerance = false
        )
        return internalSummary to multimodal
    }

    // =========================================================================
    // 2. AMBIGUITY-AWARE & PARTIAL-AWARE INTENSITY DOMAIN EXPOSURE (D2-02, FINAL-01)
    // =========================================================================

    fun computeReconciledIntensityDomainExposure(
        context: M2BEvaluationContext,
        bounds28: M2CWindowBounds,
        pinnedRunning28d: List<M2CReconciledRunningEntry>,
        runningCoverage: HistoryCoverageStatus,
        flags: MutableSet<String>
    ): M2CIntensityDomainExposure {
        val modDurs = mutableListOf<Double>(); val hvyDurs = mutableListOf<Double>()
        val sevDurs = mutableListOf<Double>(); val nmDurs = mutableListOf<Double>()
        val wuCdDurs = mutableListOf<Double>()

        val modDists = mutableListOf<Double>(); val hvyDists = mutableListOf<Double>()
        val sevDists = mutableListOf<Double>(); val nmDists = mutableListOf<Double>()
        val wuCdDists = mutableListOf<Double>()

        // D2-02 / FINAL-01: Keep verified unclassified, ambiguous duplicate groups, and reported partial remainders strictly separate!
        val unclVerDurs = mutableListOf<Double>()
        val unclAmbigDurs = mutableListOf<Pair<String, Double>>()
        val unclPartialRemDurs = mutableListOf<Double>()

        val unclVerDists = mutableListOf<Double>()
        val unclAmbigDists = mutableListOf<Pair<String, Double>>()
        val unclPartialRemDists = mutableListOf<Double>()

        var fullyClassifiedDur = 0
        var partiallyClassifiedDur = 0
        var fullyClassifiedDist = 0
        var unclassifiedOrIncomplete = 0
        var conflictingBreakdowns = 0

        val totalSessions = pinnedRunning28d.size

        for (entry in pinnedRunning28d) {
            val rec = entry.record
            val isVerified = rec.workloadEligibility == WorkloadAggregationEligibility.ELIGIBLE_VERIFIED_OBSERVED
            val isAmbiguous = rec.workloadEligibility == WorkloadAggregationEligibility.AMBIGUOUS_BOUNDED_ONLY
            val isBlocked = rec.workloadEligibility == WorkloadAggregationEligibility.BLOCKED_COLLISION
            val durValid = rec.durationQuality == TelemetryQualityStatus.VALID_OBSERVED && rec.duration != null && rec.duration.value > 0.0
            val distValid = rec.distanceQuality == TelemetryQualityStatus.VALID_OBSERVED && rec.distance != null && rec.distance.value > 0.0

            if (isBlocked) {
                unclassifiedOrIncomplete += 1
                continue
            }

            if (isAmbiguous) {
                unclassifiedOrIncomplete += 1
                val grpKey = rec.ambiguityGroupKey ?: rec.canonicalActivityId
                unclAmbigDurs += grpKey to (if (durValid) rec.duration!!.value else 0.0)
                unclAmbigDists += grpKey to (if (distValid) rec.distance!!.value else 0.0)
                continue
            }

            if (!isVerified || !durValid) {
                unclassifiedOrIncomplete += 1
                if (distValid) {
                    unclVerDists += rec.distance!!.value
                }
                continue
            }

            val durS = rec.duration!!.value
            val sessDistM = if (distValid) rec.distance!!.value else null

            if (entry.domainBreakdownConflict) {
                conflictingBreakdowns += 1
                unclassifiedOrIncomplete += 1
                unclVerDurs += durS
                sessDistM?.let { unclVerDists += it }
                continue
            }
            val bd = entry.domainBreakdown
            if (bd == null) {
                unclassifiedOrIncomplete += 1
                unclVerDurs += durS
                sessDistM?.let { unclVerDists += it }
                continue
            }
            val bdAlias = Engine15M2CCanonical.normalizeAlias(bd.sourceAlias)
            val sourceMatches = rec.sourceAliases.isNotEmpty() && bdAlias in rec.sourceAliases
            if (!sourceMatches || bd.recordedAtUtc.isAfter(context.asOf) || bd.recordedAtUtc.isBefore(rec.completedAtUtc)) {
                flags += if (!sourceMatches) "DOMAIN_BREAKDOWN_SOURCE_IDENTITY_UNVERIFIED_EXCLUDED" else "INVALID_OR_FUTURE_DOMAIN_BREAKDOWN_EXCLUDED"
                unclassifiedOrIncomplete += 1
                unclVerDurs += durS
                sessDistM?.let { unclVerDists += it }
                continue
            }

            // Null domain durations are NOT measured zeros; partial durations keep remainder unclassified under SELF_REPORTED_PROVISIONAL.
            val durFields = listOf(
                bd.moderateWorkSeconds, bd.heavyWorkSeconds, bd.severeWorkSeconds,
                bd.neuromuscularWorkSeconds, bd.warmUpCoolDownRecoverySeconds
            )
            val nonNullDurs = durFields.filterNotNull()
            val presentDurSum = Engine15M2CCanonical.compensatedSum(nonNullDurs)
            val durInputsValid = nonNullDurs.isNotEmpty() &&
                nonNullDurs.all { it.isFinite() && it >= 0.0 } &&
                presentDurSum.isFinite() &&
                presentDurSum <= durS + Engine15M2CPolicyRegistry.DURATION_CONSERVATION_TOLERANCE_SECONDS

            if (!durInputsValid) {
                flags += "MALFORMED_DOMAIN_DURATION_BREAKDOWN_EXCLUDED"
                unclassifiedOrIncomplete += 1
                unclVerDurs += durS
                sessDistM?.let { unclVerDists += it }
                continue
            }

            bd.moderateWorkSeconds?.let { modDurs += it }
            bd.heavyWorkSeconds?.let { hvyDurs += it }
            bd.severeWorkSeconds?.let { sevDurs += it }
            bd.neuromuscularWorkSeconds?.let { nmDurs += it }
            bd.warmUpCoolDownRecoverySeconds?.let { wuCdDurs += it }

            val durRemainder = (durS - presentDurSum).coerceAtLeast(0.0)
            val isFullDurConservation = durFields.all { it != null } &&
                abs(presentDurSum - durS) <= Engine15M2CPolicyRegistry.DURATION_CONSERVATION_TOLERANCE_SECONDS
            if (isFullDurConservation) {
                fullyClassifiedDur += 1
            } else {
                partiallyClassifiedDur += 1
                if (durRemainder > Engine15M2CPolicyRegistry.DURATION_CONSERVATION_TOLERANCE_SECONDS) {
                    unclPartialRemDurs += durRemainder
                }
            }

            // Distance conservation uses IEEE-754 1e-6 m tolerance; nulls never become 0.
            val distFields = listOf(
                bd.moderateWorkMeters, bd.heavyWorkMeters, bd.severeWorkMeters,
                bd.neuromuscularWorkMeters, bd.warmUpCoolDownRecoveryMeters
            )
            val nonNullDists = distFields.filterNotNull()
            if (nonNullDists.isNotEmpty() && sessDistM != null) {
                val presentDistSum = Engine15M2CCanonical.compensatedSum(nonNullDists)
                val distInputsValid = nonNullDists.all { it.isFinite() && it >= 0.0 } &&
                    presentDistSum.isFinite() &&
                    presentDistSum <= sessDistM + Engine15M2CPolicyRegistry.DISTANCE_CONSERVATION_TOLERANCE_METERS
                if (distInputsValid) {
                    bd.moderateWorkMeters?.let { modDists += it }
                    bd.heavyWorkMeters?.let { hvyDists += it }
                    bd.severeWorkMeters?.let { sevDists += it }
                    bd.neuromuscularWorkMeters?.let { nmDists += it }
                    bd.warmUpCoolDownRecoveryMeters?.let { wuCdDists += it }
                    val isFullDistConservation = distFields.all { it != null } &&
                        abs(presentDistSum - sessDistM) <= Engine15M2CPolicyRegistry.DISTANCE_CONSERVATION_TOLERANCE_METERS
                    if (isFullDistConservation) {
                        fullyClassifiedDist += 1
                    } else {
                        val remD = (sessDistM - presentDistSum).coerceAtLeast(0.0)
                        if (remD > Engine15M2CPolicyRegistry.DISTANCE_CONSERVATION_TOLERANCE_METERS) {
                            unclPartialRemDists += remD
                        }
                    }
                } else {
                    flags += "MALFORMED_DOMAIN_DISTANCE_BREAKDOWN_EXCLUDED"
                    unclVerDists += sessDistM
                }
            } else if (sessDistM != null) {
                unclVerDists += sessDistM
            }
        }

        if (fullyClassifiedDur > 0 || partiallyClassifiedDur > 0) {
            flags += "DOMAIN_EXPOSURE_DESCRIPTIVE_PRE_M9_UNAUTHENTICATED"
        }

        fun durationDomainBelief(vals: List<Double>, missingCode: String): BeliefState<Seconds> {
            if (totalSessions == 0 && runningCoverage == HistoryCoverageStatus.CONFIRMED_ZERO) {
                return BeliefState.Estimated(Seconds.ZERO, EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, UncertaintyKind.NONE)
            }
            if (vals.isEmpty()) return BeliefState.Unknown(missingCode)
            val sum = Engine15M2CCanonical.compensatedSum(vals.sorted())
            if (!sum.isFinite() || sum < 0.0) return BeliefState.Unknown("NUMERICAL_OVERFLOW_EXCLUDED")
            return when {
                sum > 0.0 -> BeliefState.Bounded(Seconds.of(sum), null, EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, UncertaintyKind.COACHING_GUIDANCE_BAND)
                else -> BeliefState.Unknown("PARTIAL_INTENSITY_CLASSIFICATION_CANNOT_CONFIRM_ZERO")
            }
        }

        fun distanceDomainBelief(vals: List<Double>, missingCode: String): BeliefState<Meters> {
            if (totalSessions == 0 && runningCoverage == HistoryCoverageStatus.CONFIRMED_ZERO) {
                return BeliefState.Estimated(Meters.ZERO, EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, UncertaintyKind.NONE)
            }
            if (vals.isEmpty()) return BeliefState.Unknown(missingCode)
            val sum = Engine15M2CCanonical.compensatedSum(vals.sorted())
            if (!sum.isFinite() || sum < 0.0) return BeliefState.Unknown("NUMERICAL_OVERFLOW_EXCLUDED")
            return when {
                sum > 0.0 -> BeliefState.Bounded(Meters.of(sum), null, EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, UncertaintyKind.COACHING_GUIDANCE_BAND)
                else -> BeliefState.Unknown("PARTIAL_INTENSITY_CLASSIFICATION_CANNOT_CONFIRM_ZERO")
            }
        }

        val unclDurBelief: BeliefState<Seconds> = if (unclVerDurs.isEmpty() && unclAmbigDurs.isEmpty() && unclPartialRemDurs.isEmpty()) {
            if (totalSessions == 0 && runningCoverage == HistoryCoverageStatus.CONFIRMED_ZERO) {
                BeliefState.Estimated(Seconds.ZERO, EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, UncertaintyKind.NONE)
            } else {
                BeliefState.Unknown("NO_UNCLASSIFIED_DURATION_RECORDED")
            }
        } else {
            val unclDurAuthority = if (unclPartialRemDurs.isNotEmpty()) {
                EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL
            } else {
                EvidenceAuthorityClass.SENSOR_SUPPORTED
            }
            assembleSecondsBelief(
                verifiedVals = unclVerDurs + unclPartialRemDurs,
                ambiguousVals = unclAmbigDurs,
                anyMissingOrBlocked = true, // Historical capture is unverified.
                emptyCode = "NO_UNCLASSIFIED_DURATION_RECORDED",
                authority = unclDurAuthority
            )
        }

        val unclDistBelief: BeliefState<Meters> = if (unclVerDists.isEmpty() && unclAmbigDists.isEmpty() && unclPartialRemDists.isEmpty()) {
            if (totalSessions == 0 && runningCoverage == HistoryCoverageStatus.CONFIRMED_ZERO) {
                BeliefState.Estimated(Meters.ZERO, EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, UncertaintyKind.NONE)
            } else {
                BeliefState.Unknown("NO_UNCLASSIFIED_DISTANCE_RECORDED")
            }
        } else {
            val unclDistAuthority = if (unclPartialRemDists.isNotEmpty()) {
                EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL
            } else {
                EvidenceAuthorityClass.SENSOR_SUPPORTED
            }
            assembleMetersBelief(
                verifiedVals = unclVerDists + unclPartialRemDists,
                ambiguousVals = unclAmbigDists,
                anyMissingOrBlocked = true, // Historical capture is unverified.
                emptyCode = "NO_UNCLASSIFIED_DISTANCE_RECORDED",
                authority = unclDistAuthority
            )
        }

        return M2CIntensityDomainExposure(
            bounds = bounds28,
            moderateWorkDuration = durationDomainBelief(modDurs, "UNCLASSIFIED_MODERATE_DURATION_NOT_ZERO"),
            heavyWorkDuration = durationDomainBelief(hvyDurs, "UNCLASSIFIED_HEAVY_DURATION_NOT_ZERO"),
            severeWorkDuration = durationDomainBelief(sevDurs, "UNCLASSIFIED_SEVERE_DURATION_NOT_ZERO"),
            highIntensityHeavyPlusSevereDuration = durationDomainBelief(hvyDurs + sevDurs, "UNCLASSIFIED_HIGH_INTENSITY_DURATION_NOT_ZERO"),
            neuromuscularWorkDuration = durationDomainBelief(nmDurs, "UNCLASSIFIED_NEUROMUSCULAR_DURATION_NOT_ZERO"),
            warmUpCoolDownRecoveryDuration = durationDomainBelief(wuCdDurs, "UNCLASSIFIED_WUCD_DURATION_NOT_ZERO"),
            unclassifiedRunningDuration = unclDurBelief,
            moderateWorkDistance = distanceDomainBelief(modDists, "UNCLASSIFIED_MODERATE_DISTANCE"),
            heavyWorkDistance = distanceDomainBelief(hvyDists, "UNCLASSIFIED_HEAVY_DISTANCE"),
            severeWorkDistance = distanceDomainBelief(sevDists, "UNCLASSIFIED_SEVERE_DISTANCE"),
            highIntensityHeavyPlusSevereDistance = distanceDomainBelief(hvyDists + sevDists, "UNCLASSIFIED_HIGH_INTENSITY_DISTANCE"),
            neuromuscularWorkDistance = distanceDomainBelief(nmDists, "UNCLASSIFIED_NEUROMUSCULAR_DISTANCE"),
            warmUpCoolDownRecoveryDistance = distanceDomainBelief(wuCdDists, "UNCLASSIFIED_WUCD_DISTANCE"),
            unclassifiedRunningDistance = unclDistBelief,
            fullyClassifiedDurationSessionCount = fullyClassifiedDur,
            partiallyClassifiedDurationSessionCount = partiallyClassifiedDur,
            fullyClassifiedDistanceSessionCount = fullyClassifiedDist,
            unclassifiedOrIncompleteSessionCount = unclassifiedOrIncomplete,
            conflictingDomainBreakdownCount = conflictingBreakdowns,
            authenticatedExecutionAuthority = false,
            promotesResearchOnlyPhysiology = false
        )
    }

    // =========================================================================
    // 3. CHRONOLOGICAL SOURCE-QUALIFIED DOWNHILL AGGREGATION (M2C-A04, M2C-B05)
    // =========================================================================

    fun aggregateRollingDownhill(
        context: M2BEvaluationContext,
        bounds: M2CWindowBounds,
        pinnedEntriesInWindow: List<M2CReconciledRunningEntry>,
        flags: MutableSet<String>
    ): M2CRollingDownhillExposure {
        val localFlags = linkedSetOf<String>()
        val retainedRejections = linkedSetOf<M2BGAPRejectionReason>()

        var contributingActivities = 0
        var completeRecordingActivities = 0
        var partialOrPausedActivities = 0
        var reconstructedOnlyActivities = 0
        var conflictingRecordings = 0
        var missingElevationActivities = 0
        var excludedOverlapCount = 0
        val rawIntervals = mutableListOf<M2CDownhillIntervalRecord>()

        for (entry in pinnedEntriesInWindow.sortedBy { it.record }) {
            val rec = entry.record
            if (rec.workloadEligibility != WorkloadAggregationEligibility.ELIGIBLE_VERIFIED_OBSERVED) {
                missingElevationActivities += 1
                localFlags += "AMBIGUOUS_OR_COLLIDED_DOWNHILL_SESSION_EXCLUDED"
                continue
            }
            if (entry.downhillStreamConflict) {
                conflictingRecordings += 1
                localFlags += "CONTRADICTORY_DOWNHILL_STREAM_EXCLUDED"
                continue
            }
            val stream = entry.runningStream
            if (stream == null) {
                missingElevationActivities += 1
                continue
            }
            val src = stream.sourceRecord()
            val sessionEnd = src?.let(::m2bSessionEnd)
            if (src == null || sessionEnd == null || src.startedAtUtc.isAfter(context.asOf) || sessionEnd.isAfter(context.asOf)) {
                conflictingRecordings += 1
                localFlags += "INVALID_OR_FUTURE_DOWNHILL_STREAM_EXCLUDED"
                continue
            }
            val quality = stream.recording?.sourceQuality ?: stream.activity.telemetryQuality
            if (quality.distanceQuality != TelemetryQualityStatus.VALID_OBSERVED ||
                quality.durationQuality != TelemetryQualityStatus.VALID_OBSERVED
            ) {
                missingElevationActivities += 1
                localFlags += "DEGRADED_TELEMETRY_DOWNHILL_STREAM_EXCLUDED"
                continue
            }

            val expectedOrigin = M2BStreamIdentity.of(stream.activity, src)
            val provenance = M2BSourceProvenance.of(Engine15M2CCanonical.aliasesFromCanonicalActivity(stream.activity))
            val recording = stream.recording

            if (recording != null) {
                if (recording.origin != expectedOrigin || stream.segments.isNotEmpty() || recording.samples.size < 2 ||
                    recording.samples.any { it.at < src.startedAtUtc || it.at > sessionEnd }
                ) {
                    conflictingRecordings += 1
                    localFlags += "INVALID_RECORDING_WINDOW_ORIGIN_OR_BOUNDS_EXCLUDED"
                    continue
                }
                var hadPauseOrGap = recording.samples.first().at > src.startedAtUtc || recording.samples.last().at < sessionEnd
                val stepHorizList = mutableListOf<Double>()
                val stepPathList = mutableListOf<Double>()
                val sessionIntervals = mutableListOf<M2CDownhillIntervalRecord>()

                for ((a, b) in recording.samples.zipWithNext()) {
                    if (b.at <= a.at || !a.elevationMeters.isFinite() || !b.elevationMeters.isFinite()) {
                        hadPauseOrGap = true; continue
                    }
                    if (b.continuity != M2BContinuityEvidence.ACTIVE_CONTIGUOUS) {
                        hadPauseOrGap = true; continue
                    }
                    val dx = b.cumulativeHorizontalDistance.value - a.cumulativeHorizontalDistance.value
                    val rise = b.elevationMeters - a.elevationMeters
                    if (!dx.isFinite() || dx <= 0.0 || !rise.isFinite()) {
                        hadPauseOrGap = true; continue
                    }
                    val durS = durationSeconds(a.at, b.at)
                    val pathM = hypot(dx, rise)
                    if (!durS.isFinite() || durS <= 0.0 || !pathM.isFinite() || pathM <= 0.0) {
                        hadPauseOrGap = true; continue
                    }
                    stepHorizList += dx
                    stepPathList += pathM

                    val intervalRejs = mutableListOf<M2BGAPRejection>()
                    if (stream.surface == M2BSurface.TECHNICAL_OR_UNKNOWN) {
                        intervalRejs += M2BGAPRejection(a.at, b.at, M2BGAPRejectionReason.UNSUPPORTED_SURFACE)
                    }
                    when (val adj = Engine15M2BIntensityModels.minetti(Meters.of(dx), rise, Seconds.of(durS))) {
                        is M2BGradeAdjustment.Computed -> Unit
                        is M2BGradeAdjustment.Abstained -> intervalRejs += M2BGAPRejection(
                            a.at, b.at,
                            when (adj) {
                                M2BGradeAdjustment.Abstained.OUT_OF_DOMAIN -> M2BGAPRejectionReason.GAP_OUT_OF_DOMAIN
                                M2BGradeAdjustment.Abstained.ECCENTRIC_DOMINATED -> M2BGAPRejectionReason.ECCENTRIC_DOMINATED_QUARANTINE
                                M2BGradeAdjustment.Abstained.INVALID_SEGMENT -> M2BGAPRejectionReason.INVALID_NUMERIC_INPUT
                            }
                        )
                    }
                    retainedRejections += intervalRejs.map { it.reason }

                    if (rise < 0.0) {
                        val lossM = -rise
                        if (!lossM.isFinite() || lossM <= 0.0) {
                            hadPauseOrGap = true; continue
                        }
                        sessionIntervals += M2CDownhillIntervalRecord(
                            canonicalActivityId = rec.canonicalActivityId,
                            sourceAlias = stream.source,
                            provenance = provenance,
                            start = a.at,
                            end = b.at,
                            attributedLocalDate = rec.attributedLocalDate,
                            elevationLoss = Meters.of(lossM),
                            horizontalDistance = Meters.of(dx),
                            slopePathDistance = Meters.of(pathM),
                            duration = Seconds.of(durS),
                            observationBasis = M2BObservationBasis.RECORDING_DERIVED_DESCRIPTION,
                            surface = stream.surface,
                            parentTraceHadPausesOrUnknownContinuity = hadPauseOrGap,
                            gapRejections = intervalRejs
                        )
                    }
                }

                val stepHorizSum = Engine15M2CCanonical.compensatedSum(stepHorizList)
                val stepPathSum = Engine15M2CCanonical.compensatedSum(stepPathList)
                if (!stepHorizSum.isFinite() || !stepPathSum.isFinite() ||
                    !recordingMatchesSummaryDistance(recording, src, stepHorizSum, stepPathSum, sessionEnd, hadPauseOrGap)
                ) {
                    conflictingRecordings += 1
                    localFlags += "CONTRADICTORY_DOWNHILL_RECORDING_DISTANCE_EXCLUDED"
                    continue
                }

                contributingActivities += 1
                if (hadPauseOrGap) {
                    partialOrPausedActivities += 1
                    localFlags += "PARTIAL_OR_PAUSED_DOWNHILL_RECORDING_BOUNDED"
                    rawIntervals += sessionIntervals.map {
                        M2CDownhillIntervalRecord(
                            it.canonicalActivityId, it.sourceAlias, it.provenance, it.start, it.end,
                            it.attributedLocalDate, it.elevationLoss, it.horizontalDistance, it.slopePathDistance,
                            it.duration, it.observationBasis, it.surface, true, it.gapRejections
                        )
                    }
                } else {
                    completeRecordingActivities += 1
                    rawIntervals += sessionIntervals
                }
            } else if (stream.segments.isNotEmpty()) {
                var validReconstructed = true
                val segIntervals = mutableListOf<M2CDownhillIntervalRecord>()
                for (seg in stream.segments) {
                    if (seg.origin != expectedOrigin || seg.start < src.startedAtUtc || seg.end > sessionEnd ||
                        seg.end <= seg.start || seg.horizontalDistance.value <= 0.0 || !seg.riseMeters.isFinite()
                    ) {
                        validReconstructed = false; continue
                    }
                    val durS = durationSeconds(seg.start, seg.end)
                    val pathM = hypot(seg.horizontalDistance.value, seg.riseMeters)
                    if (!durS.isFinite() || durS <= 0.0 || !pathM.isFinite() || pathM <= 0.0) {
                        validReconstructed = false; continue
                    }
                    val intervalRejs = mutableListOf<M2BGAPRejection>()
                    if (stream.surface == M2BSurface.TECHNICAL_OR_UNKNOWN) {
                        intervalRejs += M2BGAPRejection(seg.start, seg.end, M2BGAPRejectionReason.UNSUPPORTED_SURFACE)
                    }
                    when (val adj = Engine15M2BIntensityModels.minetti(seg.horizontalDistance, seg.riseMeters, Seconds.of(durS))) {
                        is M2BGradeAdjustment.Computed -> Unit
                        is M2BGradeAdjustment.Abstained -> intervalRejs += M2BGAPRejection(
                            seg.start, seg.end,
                            when (adj) {
                                M2BGradeAdjustment.Abstained.OUT_OF_DOMAIN -> M2BGAPRejectionReason.GAP_OUT_OF_DOMAIN
                                M2BGradeAdjustment.Abstained.ECCENTRIC_DOMINATED -> M2BGAPRejectionReason.ECCENTRIC_DOMINATED_QUARANTINE
                                M2BGradeAdjustment.Abstained.INVALID_SEGMENT -> M2BGAPRejectionReason.INVALID_NUMERIC_INPUT
                            }
                        )
                    }
                    retainedRejections += intervalRejs.map { it.reason }
                    if (seg.riseMeters < 0.0) {
                        val lossM = -seg.riseMeters
                        if (!lossM.isFinite() || lossM <= 0.0) {
                            validReconstructed = false; continue
                        }
                        segIntervals += M2CDownhillIntervalRecord(
                            canonicalActivityId = rec.canonicalActivityId,
                            sourceAlias = stream.source,
                            provenance = provenance,
                            start = seg.start,
                            end = seg.end,
                            attributedLocalDate = rec.attributedLocalDate,
                            elevationLoss = Meters.of(lossM),
                            horizontalDistance = seg.horizontalDistance,
                            slopePathDistance = Meters.of(pathM),
                            duration = Seconds.of(durS),
                            observationBasis = M2BObservationBasis.RECONSTRUCTED_DESCRIPTION,
                            surface = stream.surface,
                            parentTraceHadPausesOrUnknownContinuity = !validReconstructed,
                            gapRejections = intervalRejs
                        )
                    }
                }
                contributingActivities += 1
                reconstructedOnlyActivities += 1
                rawIntervals += segIntervals
                localFlags += "RECONSTRUCTED_DOWNHILL_UNVERIFIED_DESCRIPTIVE_ONLY"
            } else {
                missingElevationActivities += 1
            }
        }

        // Chronological overlap prevention (excluded overlaps downgrade completeness).
        val nonOverlapping = mutableListOf<M2CDownhillIntervalRecord>()
        var lastEnd: Instant? = null
        for (iv in rawIntervals.sorted()) {
            val prev = lastEnd
            if (prev != null && iv.start.isBefore(prev)) {
                excludedOverlapCount += 1
                localFlags += "OVERLAPPING_DOWNHILL_INTERVAL_EXCLUDED"
                continue
            }
            nonOverlapping += iv
            lastEnd = iv.end
        }

        val lossSum = Engine15M2CCanonical.compensatedSum(nonOverlapping.map { it.elevationLoss.value })
        val horizSum = Engine15M2CCanonical.compensatedSum(nonOverlapping.map { it.horizontalDistance.value })
        val slopeSum = Engine15M2CCanonical.compensatedSum(nonOverlapping.map { it.slopePathDistance.value })
        val durSum = Engine15M2CCanonical.compensatedSum(nonOverlapping.map { it.duration.value })

        val overflowed = !lossSum.isFinite() || !horizSum.isFinite() || !slopeSum.isFinite() || !durSum.isFinite()
        if (overflowed) localFlags += "DOWNHILL_NUMERICAL_OVERFLOW_EXCLUDED"

        val hasCoverageGapOrConflict = partialOrPausedActivities > 0 || missingElevationActivities > 0 ||
            conflictingRecordings > 0 || excludedOverlapCount > 0

        val (status, lossBelief) = when {
            overflowed -> M2CDownhillCoverageStatus.MISSING_OR_CONFLICTING_ELEVATION_EVIDENCE to
                BeliefState.Unknown("DOWNHILL_NUMERICAL_OVERFLOW_EXCLUDED")
            contributingActivities == 0 -> {
                localFlags += "NO_ELEVATION_RECORDING_EMPTY_NOT_ZERO"
                M2CDownhillCoverageStatus.MISSING_OR_CONFLICTING_ELEVATION_EVIDENCE to
                    BeliefState.Unknown(if (conflictingRecordings > 0) "CONFLICTING_ELEVATION_RECORDINGS_ONLY" else "NO_ELEVATION_RECORDING_NOT_ZERO_DESCENT")
            }
            reconstructedOnlyActivities > 0 && completeRecordingActivities == 0 && partialOrPausedActivities == 0 ->
                M2CDownhillCoverageStatus.UNVERIFIED_RECONSTRUCTED_ONLY to
                    BeliefState.Bounded(Meters.of(lossSum), null, EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, UncertaintyKind.COACHING_GUIDANCE_BAND)
            hasCoverageGapOrConflict || reconstructedOnlyActivities > 0 -> {
                localFlags += "DOWNHILL_PARTIAL_WINDOW_COVERAGE_BOUNDED"
                M2CDownhillCoverageStatus.PARTIAL_MEASURED_DESCENT_BOUNDED to
                    BeliefState.Bounded(Meters.of(lossSum), null, EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, UncertaintyKind.COACHING_GUIDANCE_BAND)
            }
            lossSum == 0.0 -> {
                localFlags += "DOWNHILL_CONFIRMED_ZERO_ON_COMPLETE_FLAT_RECORDINGS"
                M2CDownhillCoverageStatus.CONFIRMED_ZERO_RECORDING_DESCENT to
                    BeliefState.Estimated(Meters.ZERO, EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, UncertaintyKind.NONE)
            }
            else -> {
                localFlags += "DOWNHILL_COMPLETE_RECORDING_DESCRIPTIVE_PRE_M9"
                M2CDownhillCoverageStatus.COMPLETE_RECORDING_DERIVED_DESCRIPTIVE to
                    BeliefState.Estimated(Meters.of(lossSum), EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, UncertaintyKind.COACHING_GUIDANCE_BAND)
            }
        }

        fun metricDistBelief(v: Double, code: String): BeliefState<Meters> = when {
            overflowed -> BeliefState.Unknown("DOWNHILL_NUMERICAL_OVERFLOW_EXCLUDED")
            status == M2CDownhillCoverageStatus.MISSING_OR_CONFLICTING_ELEVATION_EVIDENCE -> BeliefState.Unknown(code)
            status == M2CDownhillCoverageStatus.CONFIRMED_ZERO_RECORDING_DESCENT ||
                status == M2CDownhillCoverageStatus.COMPLETE_RECORDING_DERIVED_DESCRIPTIVE ->
                BeliefState.Estimated(Meters.of(v), EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, UncertaintyKind.COACHING_GUIDANCE_BAND)
            else -> BeliefState.Bounded(Meters.of(v), null, EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, UncertaintyKind.COACHING_GUIDANCE_BAND)
        }

        fun metricDurBelief(v: Double, code: String): BeliefState<Seconds> = when {
            overflowed -> BeliefState.Unknown("DOWNHILL_NUMERICAL_OVERFLOW_EXCLUDED")
            status == M2CDownhillCoverageStatus.MISSING_OR_CONFLICTING_ELEVATION_EVIDENCE -> BeliefState.Unknown(code)
            status == M2CDownhillCoverageStatus.CONFIRMED_ZERO_RECORDING_DESCENT ||
                status == M2CDownhillCoverageStatus.COMPLETE_RECORDING_DERIVED_DESCRIPTIVE ->
                BeliefState.Estimated(Seconds.of(v), EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, UncertaintyKind.COACHING_GUIDANCE_BAND)
            else -> BeliefState.Bounded(Seconds.of(v), null, EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL, UncertaintyKind.COACHING_GUIDANCE_BAND)
        }

        flags += localFlags
        return M2CRollingDownhillExposure(
            bounds = bounds,
            coverageStatus = status,
            cumulativeElevationLossBelief = lossBelief,
            recordedDescentHorizontalDistance = metricDistBelief(horizSum, "NO_DESCENT_HORIZONTAL_DISTANCE"),
            recordedDescentSlopePathDistance = metricDistBelief(slopeSum, "NO_DESCENT_SLOPE_DISTANCE"),
            recordedDescentDuration = metricDurBelief(durSum, "NO_DESCENT_DURATION"),
            contributingActivityCount = contributingActivities,
            partialOrPausedActivityCount = partialOrPausedActivities,
            missingElevationActivityCount = missingElevationActivities,
            conflictingRecordingCount = conflictingRecordings,
            excludedDuplicateOrOverlapCount = excludedOverlapCount,
            retainedGapRejectionReasons = retainedRejections,
            intervals = nonOverlapping,
            diagnosticFlags = localFlags,
            verifiedWorkloadAuthority = false,
            isBiomechanicalTissueDamageEstimate = false
        )
    }

    // =========================================================================
    // 4. STRICT RPE VALIDATION (FINAL-03) & OVERFLOW-SAFE AGGREGATION HELPERS
    // =========================================================================

    private fun recordingMatchesSummaryDistance(
        rec: M2BRecordingWindow,
        src: RawSourceComponent,
        stepHorizSum: Double,
        stepPathSum: Double,
        sessionEnd: Instant,
        hadPauseOrGap: Boolean
    ): Boolean {
        val recordedDist = src.rawDistanceM?.takeIf { it.isFinite() && it > 0.0 } ?: return false
        val observedDist = when (rec.distanceSemantics) {
            M2BDistanceSemantics.HORIZONTAL -> stepHorizSum
            M2BDistanceSemantics.SLOPE_PATH -> stepPathSum
        }
        val tol = rec.distanceTolerance?.value ?: 0.0
        val diff = observedDist - recordedDist
        if (!diff.isFinite() || diff > tol) return false
        if (rec.samples.last().cumulativeHorizontalDistance.value - recordedDist > tol) return false
        val startsAtOrigin = rec.samples.first().at == src.startedAtUtc
        val endsAtFinish = rec.samples.last().at == sessionEnd
        if (startsAtOrigin && rec.samples.first().cumulativeHorizontalDistance.value > tol) return false
        if (!hadPauseOrGap && startsAtOrigin && endsAtFinish && diff < -tol) return false
        return true
    }

    /**
     * FINAL-03: Strict provider-qualified post-session RPE reconciliation.
     * Matching an external ID against an opaque upstream record ID NEVER establishes
     * provider-qualified provenance; sessions without validated sessionAliases reject RPE cleanly.
     */
    private fun validatePostSessionRpe(
        il: M2CSessionInternalLoadInput?,
        sessionAliases: Set<M2ASourceQualifiedAlias>,
        completedAtUtc: Instant,
        asOf: Instant,
        duration: Seconds,
        flags: MutableSet<String>,
        onInvalid: () -> Unit
    ): Triple<Double?, M2CSessionLoadAu?, M2ASourceQualifiedAlias?> {
        if (il == null) return Triple(null, null, null)
        if (il.recordedAtUtc.isAfter(asOf) || il.recordedAtUtc.isBefore(completedAtUtc)) {
            flags += "INVALID_POST_SESSION_RPE_CHRONOLOGY_EXCLUDED"
            onInvalid()
            return Triple(null, null, null)
        }
        if (sessionAliases.isEmpty()) {
            flags += "RPE_OPAQUE_UPSTREAM_SOURCE_UNVALIDATED_EXCLUDED"
            onInvalid()
            return Triple(null, null, null)
        }
        val normAlias = Engine15M2CCanonical.normalizeAlias(il.sourceAlias)
        if (normAlias !in sessionAliases) {
            flags += "RPE_SOURCE_ALIAS_UNRECONCILED_WITH_SESSION_EXCLUDED"
            onInvalid()
            return Triple(null, null, null)
        }
        val rpe = il.cr10Rpe ?: return Triple(null, null, normAlias)
        if (!rpe.isFinite() || rpe !in Engine15M2CPolicyRegistry.MOD_09_CR10_MIN_RPE..Engine15M2CPolicyRegistry.MOD_09_CR10_MAX_RPE) {
            flags += "INVALID_CR10_RPE_VALUE_EXCLUDED"
            onInvalid()
            return Triple(null, null, null)
        }
        val cleanRpe = if (rpe == 0.0) 0.0 else rpe
        val rawAu = cleanRpe * (duration.value / Engine15M2CPolicyRegistry.SECONDS_PER_MINUTE)
        if (!rawAu.isFinite() || rawAu < 0.0) {
            flags += "INTERNAL_LOAD_OVERFLOW_EXCLUDED"
            onInvalid()
            return Triple(null, null, null)
        }
        return Triple(cleanRpe, M2CSessionLoadAu.of(rawAu), normAlias)
    }

    private fun computeGroupedBounds(
        verifiedVals: List<Double>,
        ambiguousVals: List<Pair<String, Double>>
    ): Pair<Double, Double> {
        val verSum = Engine15M2CCanonical.compensatedSum(verifiedVals.sorted())
        if (!verSum.isFinite() || verSum < 0.0) return Double.NaN to Double.NaN
        val groupMins = mutableListOf<Double>()
        val groupMaxs = mutableListOf<Double>()
        for ((_, grp) in ambiguousVals.groupBy { it.first }.toSortedMap()) {
            val vals = grp.map { it.second }
            if (vals.any { !it.isFinite() || it < 0.0 }) return Double.NaN to Double.NaN
            groupMins += vals.min()
            val gSum = Engine15M2CCanonical.compensatedSum(vals.sorted())
            if (!gSum.isFinite() || gSum < 0.0) return Double.NaN to Double.NaN
            groupMaxs += gSum
        }
        val ambMin = Engine15M2CCanonical.compensatedSum(groupMins.sorted())
        val ambMax = Engine15M2CCanonical.compensatedSum(groupMaxs.sorted())
        val lo = if (ambMin.isFinite()) verSum + ambMin else Double.NaN
        val hi = if (ambMax.isFinite()) verSum + ambMax else Double.NaN
        return (if (lo.isFinite() && lo >= 0.0) lo else Double.NaN) to
            (if (hi.isFinite() && hi >= 0.0) hi else Double.NaN)
    }

    private fun assembleInternalLoadBelief(
        verifiedLoads: List<Double>,
        ambiguousLoads: List<Pair<String, Double>>,
        anyMissingOrBlocked: Boolean,
        emptyCode: String
    ): M2CInternalLoadBelief {
        if (verifiedLoads.isEmpty() && ambiguousLoads.isEmpty()) return M2CInternalLoadBelief.Unknown(emptyCode)
        val (lo, hiRaw) = computeGroupedBounds(verifiedLoads, ambiguousLoads)
        if (!lo.isFinite()) return M2CInternalLoadBelief.Unknown("INTERNAL_LOAD_OVERFLOW")
        return if (ambiguousLoads.isEmpty() && !anyMissingOrBlocked) {
            M2CInternalLoadBelief.Estimated(M2CSessionLoadAu.of(lo), EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL)
        } else {
            val hi = if (!anyMissingOrBlocked && hiRaw.isFinite()) M2CSessionLoadAu.of(hiRaw) else null
            M2CInternalLoadBelief.Bounded(M2CSessionLoadAu.of(lo), hi, EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL)
        }
    }

    private fun assemblePinnedAndUnpinnedInternalLoadBelief(
        pinnedVerified: List<Double>,
        pinnedAmbiguous: List<Pair<String, Double>>,
        pinnedMissingOrBlocked: Boolean,
        unpinnedVerified: List<Double>,
        unpinnedAmbiguous: List<Pair<String, Double>>,
        hasUnpinnedEntries: Boolean,
        emptyCode: String
    ): M2CInternalLoadBelief {
        if (!hasUnpinnedEntries) {
            return assembleInternalLoadBelief(pinnedVerified, pinnedAmbiguous, pinnedMissingOrBlocked, emptyCode)
        }
        val hasPinned = pinnedVerified.isNotEmpty() || pinnedAmbiguous.isNotEmpty()
        val hasUnpinned = unpinnedVerified.isNotEmpty() || unpinnedAmbiguous.isNotEmpty()
        return when {
            hasPinned -> {
                val (pinnedLo, _) = computeGroupedBounds(pinnedVerified, pinnedAmbiguous)
                if (!pinnedLo.isFinite()) M2CInternalLoadBelief.Unknown("INTERNAL_LOAD_OVERFLOW")
                else M2CInternalLoadBelief.Bounded(
                    lower = M2CSessionLoadAu.of(pinnedLo),
                    upper = null,
                    authority = EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL
                )
            }
            hasUnpinned -> {
                val (unpinnedLo, _) = computeGroupedBounds(unpinnedVerified, unpinnedAmbiguous)
                if (!unpinnedLo.isFinite()) M2CInternalLoadBelief.Unknown("INTERNAL_LOAD_OVERFLOW")
                else M2CInternalLoadBelief.Bounded(
                    lower = M2CSessionLoadAu.of(unpinnedLo),
                    upper = null,
                    authority = EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL
                )
            }
            else -> M2CInternalLoadBelief.Unknown(emptyCode)
        }
    }

    private fun assembleSecondsBelief(
        verifiedVals: List<Double>,
        ambiguousVals: List<Pair<String, Double>>,
        anyMissingOrBlocked: Boolean,
        emptyCode: String,
        authority: EvidenceAuthorityClass = EvidenceAuthorityClass.SENSOR_SUPPORTED
    ): BeliefState<Seconds> {
        if (verifiedVals.isEmpty() && ambiguousVals.isEmpty()) return BeliefState.Unknown(emptyCode)
        val (lo, hiRaw) = computeGroupedBounds(verifiedVals, ambiguousVals)
        if (!lo.isFinite()) return BeliefState.Unknown("NUMERICAL_OVERFLOW_EXCLUDED")
        return if (ambiguousVals.isEmpty() && !anyMissingOrBlocked) {
            BeliefState.Estimated(Seconds.of(lo), authority, UncertaintyKind.NONE)
        } else {
            val hi = if (!anyMissingOrBlocked && hiRaw.isFinite()) Seconds.of(hiRaw) else null
            BeliefState.Bounded(Seconds.of(lo), hi, authority, UncertaintyKind.COACHING_GUIDANCE_BAND)
        }
    }

    private fun assemblePinnedAndUnpinnedSecondsBelief(
        pinnedVerified: List<Double>,
        pinnedAmbiguous: List<Pair<String, Double>>,
        pinnedMissingOrBlocked: Boolean,
        unpinnedVerified: List<Double>,
        unpinnedAmbiguous: List<Pair<String, Double>>,
        hasUnpinnedEntries: Boolean,
        emptyCode: String
    ): BeliefState<Seconds> {
        if (!hasUnpinnedEntries) {
            return assembleSecondsBelief(
                pinnedVerified, pinnedAmbiguous, pinnedMissingOrBlocked, emptyCode, EvidenceAuthorityClass.SENSOR_SUPPORTED
            )
        }
        val hasPinned = pinnedVerified.isNotEmpty() || pinnedAmbiguous.isNotEmpty()
        val hasUnpinned = unpinnedVerified.isNotEmpty() || unpinnedAmbiguous.isNotEmpty()
        return when {
            hasPinned -> {
                val (pinnedLo, _) = computeGroupedBounds(pinnedVerified, pinnedAmbiguous)
                if (!pinnedLo.isFinite()) BeliefState.Unknown("NUMERICAL_OVERFLOW_EXCLUDED")
                else BeliefState.Bounded(
                    lower = Seconds.of(pinnedLo),
                    upper = null,
                    authority = EvidenceAuthorityClass.SENSOR_SUPPORTED,
                    uncertaintyKind = UncertaintyKind.COACHING_GUIDANCE_BAND
                )
            }
            hasUnpinned -> {
                val (unpinnedLo, _) = computeGroupedBounds(unpinnedVerified, unpinnedAmbiguous)
                if (!unpinnedLo.isFinite()) BeliefState.Unknown("NUMERICAL_OVERFLOW_EXCLUDED")
                else BeliefState.Bounded(
                    lower = Seconds.of(unpinnedLo),
                    upper = null,
                    authority = EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL,
                    uncertaintyKind = UncertaintyKind.COACHING_GUIDANCE_BAND
                )
            }
            else -> BeliefState.Unknown(emptyCode)
        }
    }

    private fun assembleMetersBelief(
        verifiedVals: List<Double>,
        ambiguousVals: List<Pair<String, Double>>,
        anyMissingOrBlocked: Boolean,
        emptyCode: String,
        authority: EvidenceAuthorityClass = EvidenceAuthorityClass.SENSOR_SUPPORTED
    ): BeliefState<Meters> {
        if (verifiedVals.isEmpty() && ambiguousVals.isEmpty()) return BeliefState.Unknown(emptyCode)
        val (lo, hiRaw) = computeGroupedBounds(verifiedVals, ambiguousVals)
        if (!lo.isFinite()) return BeliefState.Unknown("NUMERICAL_OVERFLOW_EXCLUDED")
        return if (ambiguousVals.isEmpty() && !anyMissingOrBlocked) {
            BeliefState.Estimated(Meters.of(lo), authority, UncertaintyKind.NONE)
        } else {
            val hi = if (!anyMissingOrBlocked && hiRaw.isFinite()) Meters.of(hiRaw) else null
            BeliefState.Bounded(Meters.of(lo), hi, authority, UncertaintyKind.COACHING_GUIDANCE_BAND)
        }
    }

    private fun assemblePinnedAndUnpinnedMetersBelief(
        pinnedVerified: List<Double>,
        pinnedAmbiguous: List<Pair<String, Double>>,
        pinnedMissingOrBlocked: Boolean,
        unpinnedVerified: List<Double>,
        unpinnedAmbiguous: List<Pair<String, Double>>,
        hasUnpinnedEntries: Boolean,
        emptyCode: String
    ): BeliefState<Meters> {
        if (!hasUnpinnedEntries) {
            return assembleMetersBelief(
                pinnedVerified, pinnedAmbiguous, pinnedMissingOrBlocked, emptyCode, EvidenceAuthorityClass.SENSOR_SUPPORTED
            )
        }
        val hasPinned = pinnedVerified.isNotEmpty() || pinnedAmbiguous.isNotEmpty()
        val hasUnpinned = unpinnedVerified.isNotEmpty() || unpinnedAmbiguous.isNotEmpty()
        return when {
            hasPinned -> {
                val (pinnedLo, _) = computeGroupedBounds(pinnedVerified, pinnedAmbiguous)
                if (!pinnedLo.isFinite()) BeliefState.Unknown("NUMERICAL_OVERFLOW_EXCLUDED")
                else BeliefState.Bounded(
                    lower = Meters.of(pinnedLo),
                    upper = null,
                    authority = EvidenceAuthorityClass.SENSOR_SUPPORTED,
                    uncertaintyKind = UncertaintyKind.COACHING_GUIDANCE_BAND
                )
            }
            hasUnpinned -> {
                val (unpinnedLo, _) = computeGroupedBounds(unpinnedVerified, unpinnedAmbiguous)
                if (!unpinnedLo.isFinite()) BeliefState.Unknown("NUMERICAL_OVERFLOW_EXCLUDED")
                else BeliefState.Bounded(
                    lower = Meters.of(unpinnedLo),
                    upper = null,
                    authority = EvidenceAuthorityClass.SELF_REPORTED_PROVISIONAL,
                    uncertaintyKind = UncertaintyKind.COACHING_GUIDANCE_BAND
                )
            }
            else -> BeliefState.Unknown(emptyCode)
        }
    }

    private fun durationSeconds(start: Instant, end: Instant): Double =
        Duration.between(start, end).let { it.seconds + it.nano / 1_000_000_000.0 }
}
