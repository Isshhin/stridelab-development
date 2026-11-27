package io.stridelab.privatealpha

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

private data class IndexedExecutionSet(val globalIndex: Int, val set: ExecutionSet)
private data class CoachedExerciseGroup(val prescription: ExercisePrescription, val sets: List<IndexedExecutionSet>)

/**
 * Hevy-style flattened AMOLED tabular set log for coached Strength & Calisthenics sessions,
 * backed directly by WorkoutPlayerEngine + player-exec-* checkpoints in TrainingStore.
 */
@Composable
internal fun CoachedSetTable(
    plan: PlanSnapshot,
    session: PlannedSession,
    execution: WorkoutExecution,
    allPlans: List<PlanSnapshot>,
    entries: String,
    onEntries: (String) -> Unit,
    busy: Boolean,
    error: String?,
    onBack: () -> Unit,
    onOpenFullPlayer: () -> Unit,
    onOpenScopedTimer: (setIndex: Int) -> Unit,
    onSwapHarder: (exerciseId: String) -> Unit,
    onSwapEasier: ((exerciseId: String) -> Unit)? = null,
    dispatch: (PlayerEvent) -> Unit
) {
    val catalog = rememberAugmentedExerciseCatalog()
    var dismissedUpgradeIds by rememberSaveable(plan.id, session.id) { mutableStateOf(emptyList<String>()) }
    var tableError by remember(plan.id, session.id) { mutableStateOf<String?>(null) }
    var inspectExercise by remember { mutableStateOf<ExercisePrescription?>(null) }
    var showSessionMuscleFocus by rememberSaveable(plan.id, session.id) { mutableStateOf(false) }

    val strengthSummary = remember(plan.owner, allPlans, catalog, session.date) {
        if (session.discipline == "str") {
            StrengthAnalyticsProjector.summarize(
                plans = allPlans,
                freeformWorkouts = emptyList(),
                catalog = catalog,
                ownerId = plan.owner,
                weekStart = session.date.minusDays((session.date.dayOfWeek.value - 1).toLong()),
                today = session.date
            )
        } else null
    }
    val groups = remember(execution.sets) {
        val grouped = LinkedHashMap<String, MutableList<IndexedExecutionSet>>()
        execution.sets.forEachIndexed { idx, row ->
            grouped.getOrPut(row.target.id) { mutableListOf() }.add(IndexedExecutionSet(idx, row))
        }
        grouped.values.map { list -> CoachedExerciseGroup(list.first().set.target, list) }
    }

    val resting = execution.phase == PlayerPhase.Resting
    val timer = execution.timer
    val restRemainingSec by produceState(0L, resting, timer?.deadlineMs, timer?.remainingMs) {
        if (!resting || timer == null) {
            value = 0L
        } else {
            fun sample(): Long = ((timer.remaining(SystemClock.elapsedRealtime()) + 999L) / 1000L).coerceAtLeast(0L)
            value = sample()
            while (resting && timer.deadlineMs != null && timer.remaining(SystemClock.elapsedRealtime()) > 0L) {
                delay(250L)
                value = sample()
            }
        }
    }

    fun draftValue(exId: String, setNumber: Int, field: String, fallback: String = ""): String {
        val raw = runCatching { JSONObject(entries).optString("$exId|$setNumber|$field", "") }.getOrDefault("")
        return raw.ifBlank { fallback }
    }

    fun writeDraft(exId: String, setNumber: Int, field: String, value: String) {
        val json = runCatching { JSONObject(entries) }.getOrDefault(JSONObject())
        json.put("$exId|$setNumber|$field", value.take(24))
        onEntries(json.toString())
        tableError = null
    }

    Surface(
        color = StrideLabSurfaceTokens.canvas,
        contentColor = StrideLabSurfaceTokens.ink,
        modifier = Modifier
            .fillMaxSize()
            .testTag("coached-set-table")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StrideLabSurfaceTokens.canvas)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    enabled = !busy,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("coached-back")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Training",
                        tint = StrideLabSurfaceTokens.ink
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.effectiveTitle,
                        color = StrideLabSurfaceTokens.ink,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${execution.outcome.confirmedSets}/${execution.sets.size} SETS CONFIRMED",
                        color = StrideLabSurfaceTokens.lime,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        style = TabularTextStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = onOpenFullPlayer,
                    enabled = !busy,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("coached-open-player")
                ) {
                    Text(
                        "Player",
                        color = StrideLabSurfaceTokens.inkSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                Button(
                    onClick = { dispatch(PlayerEvent.Review) },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StrideLabSurfaceTokens.lime,
                        contentColor = StrideLabSurfaceTokens.onLime
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("coached-finish")
                ) {
                    Text("Finish", fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            StrideLabHairline()

            (tableError ?: error)?.let { msg ->
                Text(
                    text = msg,
                    color = Color(0xFFFF8C87),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            val sessionFocus = remember(groups, catalog) {
                MuscleFocusProjector.forPlannedSession(groups.map { it.prescription }, catalog)
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("coached-set-list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                if (!sessionFocus.isEmpty) {
                    item(key = "coached-session-muscle-focus") {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showSessionMuscleFocus = !showSessionMuscleFocus }
                                    .padding(horizontal = 4.dp, vertical = 8.dp)
                                    .testTag("coached-muscle-focus-toggle"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "MUSCLE FOCUS",
                                        color = StrideLabSurfaceTokens.inkMuted,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    )
                                    Text(
                                        text = sessionFocus.primaryMuscles.take(3).joinToString(" · "),
                                        color = StrideLabSurfaceTokens.inkSecondary,
                                        fontSize = 12.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = if (showSessionMuscleFocus) "Hide" else "Map",
                                    color = StrideLabSurfaceTokens.lime,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (showSessionMuscleFocus) {
                                SessionMuscleFocusSection(
                                    focus = sessionFocus,
                                    provenance = "Planned session focus"
                                )
                            } else {
                                StrideLabHairline()
                            }
                        }
                    }
                }
                items(groups, key = { it.prescription.id }) { group ->
                    val ex = group.prescription
                    val metricType = ex.metricType(session.discipline)
                    val isTimed = metricType == ExerciseMetricType.HOLD_SECONDS || metricType == ExerciseMetricType.WEIGHT_AND_HOLD
                    val showWeight = metricType == ExerciseMetricType.WEIGHT_AND_REPS || metricType == ExerciseMetricType.WEIGHT_AND_HOLD

                    val libRecord = remember(catalog, ex.id, ex.name) {
                        MuscleFocusProjector.resolveRecord(ex.id, ex.name, catalog)
                    }
                    val muscleLabel = remember(libRecord) {
                        libRecord?.let { ExerciseMuscleCatalog.formatPrimaryTag(it.primaryMuscles) }
                    }
                    val evidence = remember(ex.id, plan.owner, allPlans, session.date) {
                        ExerciseProgressionHistoryReader.latestForExercise(
                            exerciseId = ex.id,
                            owner = plan.owner,
                            plans = allPlans,
                            beforeDate = session.date
                        )
                    }
                    val strengthProjection = remember(ex, session.discipline, evidence) {
                        if (metricType == ExerciseMetricType.WEIGHT_AND_REPS ||
                            (session.discipline == "str" && metricType == ExerciseMetricType.REPS)
                        ) {
                            StrengthProgressionProjector.project(ex, session.discipline, evidence)
                        } else null
                    }
                    val upgradeSuggestion = remember(ex, session.discipline, evidence) {
                        if (ex.id !in dismissedUpgradeIds) {
                            CalisthenicsProgressionProjector.suggestUpgrade(ex, session.discipline, evidence)
                        } else null
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("coached-exercise-${ex.id}"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Exercise Header + Info Trigger + Optional Timed-Hold Player Handoff Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { inspectExercise = ex }
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = ex.name,
                                    color = StrideLabSurfaceTokens.ink,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = listOfNotNull(
                                        muscleLabel,
                                        ex.equipment.takeIf { it.isNotBlank() && it != "None" }
                                    ).joinToString(" · ").ifBlank { session.title },
                                    color = StrideLabSurfaceTokens.inkMuted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { inspectExercise = ex },
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("exercise-info-trigger-${ex.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = "Exercise info for ${ex.name}",
                                    tint = StrideLabSurfaceTokens.inkSecondary
                                )
                            }
                            if (isTimed) {
                                val nextUnfinished = group.sets
                                    .firstOrNull { it.set.disposition == SetDisposition.PENDING }
                                    ?.globalIndex ?: group.sets.first().globalIndex
                                OutlinedButton(
                                    onClick = {
                                        dispatch(PlayerEvent.FocusSet(nextUnfinished))
                                        onOpenScopedTimer(nextUnfinished)
                                    },
                                    enabled = !busy,
                                    shape = RoundedCornerShape(100.dp),
                                    border = BorderStroke(1.dp, StrideLabSurfaceTokens.lime),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = StrideLabSurfaceTokens.surfaceSubtle,
                                        contentColor = StrideLabSurfaceTokens.lime
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("coached-timer-${ex.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Timer", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                            }
                        }

                        // Phase 1 Strength Guidance Line + PR Badge
                        val prRecord = strengthSummary?.prsByExerciseId?.get(ex.id.ifBlank { ex.name.lowercase() })
                        if (strengthProjection != null || prRecord != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(StrideLabSurfaceTokens.surfaceElevated)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (strengthProjection != null) {
                                    Text(
                                        text = listOfNotNull(
                                            strengthProjection.previousSummary,
                                            strengthProjection.summary
                                        ).joinToString(" · "),
                                        color = if (strengthProjection.isSessionOne) {
                                            StrideLabSurfaceTokens.inkSecondary
                                        } else {
                                            StrideLabSurfaceTokens.lime
                                        },
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        style = TabularTextStyle,
                                        modifier = Modifier.weight(1f).testTag("coached-projection-${ex.id}")
                                    )
                                }
                                if (prRecord != null) {
                                    StrideLabMetricBadge(
                                        label = "PR ${freeformNumber(prRecord.bestWeightKg)}kg×${prRecord.repsAtBestWeight}",
                                        accentColor = StrideLabVisualPalette.strengthCoral
                                    )
                                }
                            }
                        }

                        // Phase 1 Calisthenics / Timed-Hold Opt-In Level-Up Chip
                        if (upgradeSuggestion != null && execution.canChangePrescription) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0x1FD8FB70),
                                border = BorderStroke(1.dp, StrideLabSurfaceTokens.lime),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .clickable(enabled = !busy) { onSwapHarder(ex.id) }
                                            .testTag("coached-level-up-${ex.id}"),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = "Ready to level up → ${upgradeSuggestion.suggestedExercise.name}",
                                            color = StrideLabSurfaceTokens.lime,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { dismissedUpgradeIds = dismissedUpgradeIds + ex.id },
                                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Dismiss upgrade suggestion",
                                            tint = StrideLabSurfaceTokens.inkMuted
                                        )
                                    }
                                }
                            }
                        }

                        // Compact Tabular Grid Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "SET",
                                color = StrideLabSurfaceTokens.inkMuted,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(30.dp)
                            )
                            Text(
                                "PREV",
                                color = StrideLabSurfaceTokens.inkMuted,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(0.9f),
                                maxLines = 1
                            )
                            if (showWeight) {
                                Text(
                                    "KG",
                                    color = StrideLabSurfaceTokens.inkMuted,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(0.95f)
                                )
                            }
                            Text(
                                if (isTimed) "SEC" else "REPS",
                                color = StrideLabSurfaceTokens.inkMuted,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(0.95f)
                            )
                            Text(
                                "✓",
                                color = StrideLabSurfaceTokens.inkMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(48.dp)
                            )
                        }

                        // Set Rows
                        group.sets.forEach { (globalIdx, step) ->
                            val isActiveRow = globalIdx == execution.cursor && !step.isConfirmed
                            val rowSurface = if (isActiveRow) StrideLabSurfaceTokens.surfaceElevated else Color.Transparent
                            val prevEvidence = evidence?.sets?.getOrNull(step.number - 1)
                            val prevText = when {
                                prevEvidence == null -> "—"
                                prevEvidence.seconds != null && prevEvidence.kg != null ->
                                    "${prevEvidence.kg.toInt()}×${prevEvidence.seconds}s"
                                prevEvidence.seconds != null -> "${prevEvidence.seconds} s"
                                prevEvidence.kg != null && prevEvidence.reps != null ->
                                    "${prevEvidence.kg}×${prevEvidence.reps}"
                                prevEvidence.reps != null -> "${prevEvidence.reps}"
                                else -> "—"
                            }
                            val actualSecStr = step.actual?.seconds?.toString().orEmpty()
                            val actualRepStr = step.actual?.reps?.toString().orEmpty()
                            val actualKgStr = step.actual?.kg?.let {
                                if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
                            }.orEmpty()

                            val primaryField = if (isTimed) "seconds" else "reps"
                            val primaryValue = draftValue(
                                ex.id,
                                step.number,
                                primaryField,
                                if (isTimed) actualSecStr else actualRepStr
                            )
                            val kgValue = draftValue(ex.id, step.number, "kg", actualKgStr)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(rowSurface)
                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                                    .graphicsLayer { alpha = if (step.isConfirmed) 0.78f else 1f }
                                    .testTag("coached-set-row-$globalIdx"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = step.number.toString(),
                                    color = if (step.isConfirmed) StrideLabSurfaceTokens.lime else StrideLabSurfaceTokens.ink,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = TabularTextStyle,
                                    modifier = Modifier.width(30.dp)
                                )
                                Text(
                                    text = prevText,
                                    color = StrideLabSurfaceTokens.inkMuted,
                                    fontSize = 12.sp,
                                    style = TabularTextStyle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(0.9f)
                                )
                                if (showWeight) {
                                    CompactSetNumberCell(
                                        value = kgValue,
                                        placeholder = "kg",
                                        enabled = !busy && !step.isConfirmed,
                                        decimal = true,
                                        tag = "coached-kg-$globalIdx",
                                        onValueChange = { writeDraft(ex.id, step.number, "kg", it) },
                                        modifier = Modifier.weight(0.95f)
                                    )
                                }
                                CompactSetNumberCell(
                                    value = primaryValue,
                                    placeholder = (if (isTimed) ex.seconds else ex.reps)?.toString() ?: "-",
                                    enabled = !busy && !step.isConfirmed,
                                    decimal = false,
                                    tag = if (isTimed) "coached-sec-$globalIdx" else "coached-reps-$globalIdx",
                                    onValueChange = { writeDraft(ex.id, step.number, primaryField, it) },
                                    modifier = Modifier.weight(0.95f)
                                )
                                val checkLabel = if (step.isConfirmed) {
                                    "Unconfirm ${ex.name} set ${step.number}"
                                } else {
                                    "Confirm ${ex.name} set ${step.number}"
                                }
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(enabled = !busy) {
                                            if (step.isConfirmed) {
                                                dispatch(PlayerEvent.UnconfirmSetAt(globalIdx))
                                            } else {
                                                val parsedKg = kgValue.trim().toDoubleOrNull()
                                                    ?.takeIf { it.isFinite() && it >= 0.0 }
                                                val resolvedPrimary = primaryValue.trim().toIntOrNull()
                                                    ?: (if (isTimed) ex.seconds else ex.reps)
                                                if (resolvedPrimary == null || resolvedPrimary <= 0) {
                                                    tableError = "Enter a valid ${if (isTimed) "duration in seconds" else "rep count"} for ${ex.name} set ${step.number}."
                                                } else {
                                                    tableError = null
                                                    val actual = if (isTimed) {
                                                        SetActual(seconds = resolvedPrimary, kg = parsedKg)
                                                    } else {
                                                        SetActual(reps = resolvedPrimary, kg = parsedKg)
                                                    }
                                                    dispatch(PlayerEvent.CompleteSetAt(globalIdx, actual, startRest = true))
                                                }
                                            }
                                        }
                                        .semantics { contentDescription = checkLabel }
                                        .testTag("coached-confirm-$globalIdx"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (step.isConfirmed) StrideLabSurfaceTokens.lime
                                                else StrideLabSurfaceTokens.surfaceSubtle
                                            )
                                            .border(
                                                1.dp,
                                                if (step.isConfirmed) StrideLabSurfaceTokens.lime else StrideLabSurfaceTokens.hairline,
                                                RoundedCornerShape(8.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = if (step.isConfirmed) StrideLabSurfaceTokens.onLime else StrideLabSurfaceTokens.inkMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // + Add set (auto-prefills from last row)
                        TextButton(
                            onClick = {
                                val lastSet = group.sets.last().set
                                val nextNum = lastSet.number + 1
                                val prevSec = draftValue(ex.id, lastSet.number, "seconds", lastSet.actual?.seconds?.toString().orEmpty())
                                val prevRep = draftValue(ex.id, lastSet.number, "reps", lastSet.actual?.reps?.toString().orEmpty())
                                val prevKg = draftValue(ex.id, lastSet.number, "kg", lastSet.actual?.kg?.toString().orEmpty())
                                val json = runCatching { JSONObject(entries) }.getOrDefault(JSONObject())
                                if (prevSec.isNotBlank()) json.put("${ex.id}|$nextNum|seconds", prevSec)
                                if (prevRep.isNotBlank()) json.put("${ex.id}|$nextNum|reps", prevRep)
                                if (prevKg.isNotBlank()) json.put("${ex.id}|$nextNum|kg", prevKg)
                                onEntries(json.toString())
                                dispatch(PlayerEvent.AddExtraSet(ex.id))
                            },
                            enabled = !busy,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("coached-add-set-${ex.id}")
                        ) {
                            Text(
                                "+ Add set",
                                color = StrideLabSurfaceTokens.lime,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        StrideLabHairline()
                    }
                }
            }

            // Non-Blocking Rest Bar
            if (resting) {
                StrideLabHairline()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StrideLabSurfaceTokens.surfaceElevated)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .heightIn(min = 48.dp)
                        .testTag("coached-rest-bar"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "REST ${restRemainingSec / 60}:${(restRemainingSec % 60).toString().padStart(2, '0')}",
                        color = StrideLabSurfaceTokens.lime,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        style = TabularTextStyle,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { dispatch(PlayerEvent.ExtendRest) },
                        enabled = !busy,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("+15s", color = StrideLabSurfaceTokens.ink, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(
                        onClick = { dispatch(PlayerEvent.SkipRest) },
                        enabled = !busy,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Skip", color = StrideLabSurfaceTokens.lime, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    inspectExercise?.let { targetEx ->
        val canSwapThis = execution.canChangePrescription &&
            execution.sets.none { it.target.id == targetEx.id && it.isConfirmed }
        ExerciseInfoSheet(
            exerciseId = targetEx.id,
            exerciseName = targetEx.name,
            disciplineLabel = plannedDisciplineLabel(session),
            disciplineTint = disciplineColor(session.discipline),
            catalog = catalog,
            prescription = targetEx,
            disciplineCode = session.discipline,
            canSwap = canSwapThis,
            busy = busy,
            onSwapEasier = onSwapEasier?.let { cb ->
                {
                    inspectExercise = null
                    cb(targetEx.id)
                }
            },
            onSwapHarder = {
                inspectExercise = null
                onSwapHarder(targetEx.id)
            },
            onDismiss = { inspectExercise = null }
        )
    }
}

@Composable
private fun CompactSetNumberCell(
    value: String,
    placeholder: String,
    enabled: Boolean,
    decimal: Boolean,
    tag: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sanitized: (String) -> Unit = { next ->
        val filtered = buildString {
            var seenDot = false
            for (ch in next) {
                if (ch.isDigit()) append(ch)
                else if (decimal && ch == '.' && !seenDot) {
                    append(ch); seenDot = true
                }
            }
        }.take(6)
        onValueChange(filtered)
    }
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(StrideLabSurfaceTokens.surfaceSubtle)
            .border(1.dp, StrideLabSurfaceTokens.hairline, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                color = StrideLabSurfaceTokens.inkMuted,
                fontSize = 14.sp,
                style = TabularTextStyle,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
        BasicTextField(
            value = value,
            onValueChange = sanitized,
            enabled = enabled,
            singleLine = true,
            textStyle = TabularTextStyle.copy(
                color = StrideLabSurfaceTokens.ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            ),
            cursorBrush = SolidColor(StrideLabSurfaceTokens.lime),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tag)
        )
    }
}
