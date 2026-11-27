package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseInfoSheet(
    exerciseId: String?,
    exerciseName: String,
    disciplineLabel: String,
    disciplineTint: Color,
    catalog: List<ExerciseLibraryRecordV2>,
    prescription: ExercisePrescription? = null,
    disciplineCode: String? = null,
    canSwap: Boolean = false,
    busy: Boolean = false,
    onSwapEasier: (() -> Unit)? = null,
    onSwapHarder: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val record = remember(exerciseId, exerciseName, catalog) {
        MuscleFocusProjector.resolveRecord(exerciseId, exerciseName, catalog)
    }
    val focus = remember(exerciseId, exerciseName, catalog) {
        MuscleFocusProjector.forExercise(exerciseId, exerciseName, catalog)
    }
    val metricLabel = remember(prescription, disciplineCode, record) {
        val metric = when {
            prescription != null && disciplineCode != null -> prescription.metricType(disciplineCode)
            record != null -> record.metricType()
            else -> null
        }
        when (metric) {
            ExerciseMetricType.REPS -> "Reps"
            ExerciseMetricType.WEIGHT_AND_REPS -> "Load × Reps"
            ExerciseMetricType.HOLD_SECONDS -> "Timed Hold"
            ExerciseMetricType.WEIGHT_AND_HOLD -> "Weighted Hold"
            null -> null
        }
    }
    val coachingCues = remember(record, prescription) {
        val list = mutableListOf<String>()
        prescription?.cue?.trim()?.takeIf { it.isNotEmpty() }?.let { list.add(it) }
        record?.technique.orEmpty().forEach { cue ->
            val clean = cue.trim()
            if (clean.isNotEmpty() && list.none { it.equals(clean, ignoreCase = true) }) {
                list.add(clean)
            }
        }
        list
    }
    val easierAlt = remember(prescription) { prescription?.let { ExerciseAlternatives.easier(it) } }
    val harderAlt = remember(prescription) { prescription?.let { ExerciseAlternatives.harder(it) } }
    val easierText = easierAlt?.name ?: record?.easier?.takeIf { it.isNotBlank() }
    val harderText = harderAlt?.name ?: record?.harder?.takeIf { it.isNotBlank() }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StrideLabSurfaceTokens.surfaceElevated,
        contentColor = StrideLabSurfaceTokens.ink,
        modifier = Modifier.testTag("exercise-info-sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = exerciseName,
                        color = StrideLabSurfaceTokens.ink,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(disciplineTint.copy(alpha = 0.16f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = disciplineLabel.uppercase(),
                                color = disciplineTint,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.7.sp
                            )
                        }
                        if (metricLabel != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(StrideLabSurfaceTokens.surfaceSubtle)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = metricLabel,
                                    color = StrideLabSurfaceTokens.inkSecondary,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("exercise-info-close")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close exercise info",
                        tint = StrideLabSurfaceTokens.inkSecondary
                    )
                }
            }

            StrideLabHairline()

            // Muscles Section
            if (focus.isEmpty) {
                Text(
                    text = "Custom exercise. No mapped muscle regions.",
                    color = StrideLabSurfaceTokens.inkMuted,
                    fontSize = 13.sp
                )
            } else {
                MuscleFocusCardContent(focus = focus)
            }

            // Coaching Cues
            if (coachingCues.isNotEmpty()) {
                StrideLabHairline()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "TECHNIQUE & CUES",
                        color = StrideLabSurfaceTokens.inkMuted,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    coachingCues.take(4).forEach { cue ->
                        Text(
                            text = "• $cue",
                            color = StrideLabSurfaceTokens.inkSecondary,
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp
                        )
                    }
                }
            }

            // Easier / Harder Alternatives
            if (easierText != null || harderText != null) {
                StrideLabHairline()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "ALTERNATIVES",
                        color = StrideLabSurfaceTokens.inkMuted,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    if (easierText != null) {
                        AlternativeChipRow(
                            badge = "Easier",
                            title = easierText,
                            clickable = canSwap && onSwapEasier != null && !busy,
                            tag = "exercise-info-swap-easier",
                            onClick = { onSwapEasier?.invoke() }
                        )
                    }
                    if (harderText != null) {
                        AlternativeChipRow(
                            badge = "Harder",
                            title = harderText,
                            clickable = canSwap && onSwapHarder != null && !busy,
                            tag = "exercise-info-swap-harder",
                            onClick = { onSwapHarder?.invoke() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlternativeChipRow(
    badge: String,
    title: String,
    clickable: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = StrideLabSurfaceTokens.surfaceSubtle,
        border = BorderStroke(
            1.dp,
            if (clickable) StrideLabSurfaceTokens.lime.copy(alpha = 0.45f) else StrideLabSurfaceTokens.hairline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = badge.uppercase(),
                color = if (clickable) StrideLabSurfaceTokens.lime else StrideLabSurfaceTokens.inkMuted,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp
            )
            Text(
                text = title,
                color = StrideLabSurfaceTokens.ink,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (clickable) {
                Text(
                    text = "Swap",
                    color = StrideLabSurfaceTokens.lime,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
