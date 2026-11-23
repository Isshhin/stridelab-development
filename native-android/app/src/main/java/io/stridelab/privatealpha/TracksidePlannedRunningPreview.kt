package io.stridelab.privatealpha

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
internal fun WorkoutDetailTopBar(
    weekNumber: Long,
    statusLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(WorkoutDetailPalette.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onBack)
                .heightIn(min = 48.dp)
                .padding(end = 10.dp)
                .testTag("workout-planned-back"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "‹",
                color = WorkoutDetailPalette.textSecondary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Week $weekNumber · Training",
                color = WorkoutDetailPalette.textSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = statusLabel.uppercase(Locale.getDefault()),
            color = WorkoutDetailPalette.lime,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
internal fun WorkoutDetailBottomBar(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        WorkoutDetailPalette.background.copy(alpha = 0.0f),
                        WorkoutDetailPalette.background.copy(alpha = 0.94f),
                        WorkoutDetailPalette.background
                    )
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .testTag("workout-log-toggle"),
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = WorkoutDetailPalette.lime,
                contentColor = WorkoutDetailPalette.background,
                disabledContainerColor = WorkoutDetailPalette.cardRaised,
                disabledContentColor = WorkoutDetailPalette.textMuted
            )
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                letterSpacing = 0.6.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Presentation of a saved running prescription, not a completed activity.
 * Never reconstruct distance from the original after an accepted time-only review.
 */
@Composable
internal fun TracksidePlannedRunningPreview(
    session: PlannedSession,
    target: Engine15SessionPresentation.Target,
    displayTitle: String,
    onBack: () -> Unit,
    planStart: LocalDate = session.date,
    onMove: (() -> Unit)? = null,
    onMissed: (() -> Unit)? = null,
    onAdjustDifficulty: ((Int) -> Unit)? = null,
    busy: Boolean = false,
    selectedStepIndex: Int? = null,
    onSelectStep: (Int?) -> Unit = {},
    planningPaceSecPerKm: Double = 360.0,
    showInlineTopBar: Boolean = true,
    onExportToWatch: (() -> Unit)? = null
) {
    val label = when {
        target.state == Engine15SessionPresentation.State.ACCEPTED_REST -> "Rest"
        target.state == Engine15SessionPresentation.State.ACCEPTED_TIME_ONLY -> "Time-only alternative"
        session.runningPrescription?.role == "QUALITY" ->
            session.runningPrescription.family ?: "Quality"
        session.quality || session.kind == "quality" ->
            session.runningPrescription?.family ?: "Quality session"
        session.runningPrescription?.role == "LONG" || session.kind == "long" -> "Long run · Easy effort"
        session.runningPrescription?.role == "RECOVERY" -> "Recovery"
        else -> "Easy effort"
    }
    val showOriginalSteps = target.state == Engine15SessionPresentation.State.ORIGINAL ||
        target.state == Engine15SessionPresentation.State.PENDING_REVIEW ||
        target.state == Engine15SessionPresentation.State.ACCEPTED_PROGRESSION
    val segments = session.runningPrescription?.segments?.takeIf { showOriginalSteps }
    val mixedSegments = session.runningPrescription?.mixedSegments?.takeIf { showOriginalSteps }
    val estimatedDistance = session.runningPrescription?.estimatedDistanceKm?.takeIf { showOriginalSteps }
    val weekNumber = (ChronoUnit.DAYS.between(planStart, session.date) / 7L + 1L)
        .coerceAtLeast(1L)

    val stepItems = remember(
        session.id,
        session.difficultyStep,
        segments,
        mixedSegments,
        target.currentSteps,
        planningPaceSecPerKm
    ) {
        buildWorkoutStepItems(
            session = session,
            segments = segments,
            mixedSegments = mixedSegments,
            textFallback = target.currentSteps,
            planningPaceSecPerKm = planningPaceSecPerKm
        )
    }
    val profileBars = remember(stepItems) { flattenProfileBars(stepItems) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("workout-planned-preview"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (showInlineTopBar) {
            WorkoutDetailTopBar(
                weekNumber = weekNumber,
                statusLabel = session.status,
                onBack = onBack
            )
        }

        // A. Header & Symmetric Hero Metrics
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("workout-planned-summary")
        ) {
            Canvas(Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                for (i in 0..6) {
                    val shift = i * 7.dp.toPx()
                    val path = Path().apply {
                        moveTo(w * 0.44f, h * 0.68f + shift * 0.45f)
                        cubicTo(
                            w * 0.68f,
                            h * 0.64f - shift * 0.30f,
                            w * 0.80f,
                            h * 0.24f + shift * 0.65f,
                            w * 1.12f,
                            h * 0.14f + shift * 0.55f
                        )
                    }
                    drawPath(
                        path = path,
                        color = WorkoutDetailPalette.teal.copy(alpha = 0.09f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = session.date.format(
                        DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault())
                    ).uppercase(Locale.getDefault()),
                    color = WorkoutDetailPalette.textMuted,
                    fontSize = 11.sp,
                    letterSpacing = 0.7.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = displayTitle,
                    color = WorkoutDetailPalette.text,
                    fontSize = 27.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("workout-planned-title")
                )
                Text(
                    text = when {
                        target.currentDistanceTargetKm != null ->
                            "$label · " + String.format(
                                Locale.getDefault(),
                                "%.1f km",
                                target.currentDistanceTargetKm
                            )
                        target.state == Engine15SessionPresentation.State.ACCEPTED_REST -> "Reviewed rest"
                        estimatedDistance != null ->
                            "$label · " + String.format(
                                Locale.getDefault(),
                                "~%.1f km",
                                estimatedDistance
                            )
                        else -> label
                    },
                    color = WorkoutDetailPalette.textSecondary,
                    fontSize = 14.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("workout-planned-subtitle")
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    HeroMetricColumn(
                        value = when {
                            target.state == Engine15SessionPresentation.State.ACCEPTED_REST -> "Rest"
                            target.currentDistanceTargetKm != null ->
                                String.format(Locale.getDefault(), "%.1f km", target.currentDistanceTargetKm)
                            estimatedDistance != null ->
                                String.format(Locale.getDefault(), "~%.1f km", estimatedDistance)
                            else -> "Time-based"
                        },
                        caption = when {
                            target.currentDistanceTargetKm != null -> "Planned distance"
                            estimatedDistance != null -> "Est. distance"
                            else -> "Current target"
                        },
                        valueTag = "workout-planned-primary-target",
                        modifier = Modifier.weight(1f)
                    )
                    HeroMetricColumn(
                        value = if (target.state == Engine15SessionPresentation.State.ACCEPTED_REST) {
                            "0 min"
                        } else {
                            "${target.currentMinutes} min"
                        },
                        caption = "Estimated duration",
                        valueTag = "workout-planned-duration",
                        modifier = Modifier.weight(1f)
                    )
                    HeroMetricColumn(
                        value = if (target.state == Engine15SessionPresentation.State.ACCEPTED_REST) {
                            "—"
                        } else {
                            formatWorkoutAvgPace(
                                target.currentDistanceTargetKm ?: estimatedDistance,
                                target.currentMinutes
                            )
                        },
                        caption = "Avg Pace",
                        valueTag = "workout-planned-avg-pace",
                        modifier = Modifier.weight(1f)
                    )
                }

                if (estimatedDistance != null) {
                    Text(
                        text = "Estimated distance uses planning pace. Follow the timed and measured steps below.",
                        color = WorkoutDetailPalette.textMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
                if (target.state == Engine15SessionPresentation.State.ACCEPTED_TIME_ONLY) {
                    Text(
                        text = "Reviewed time allowance is your current target; original distance and intervals are not required.",
                        color = WorkoutDetailPalette.textMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
                if (target.state == Engine15SessionPresentation.State.ACCEPTED_PROGRESSION && target.disclosure != null) {
                    Text(
                        text = "Progressed target · ${target.disclosure}",
                        color = WorkoutDetailPalette.lime,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        if (onExportToWatch != null) RunningWorkoutFitExportButton(onExportToWatch)

        // B. Combined Workout Profile & Difficulty Card
        if (profileBars.isNotEmpty() || (onAdjustDifficulty != null && target.state != Engine15SessionPresentation.State.ACCEPTED_REST)) {
            WorkoutProfileAndDifficultyCard(
                session = session,
                bars = profileBars,
                stepItems = stepItems,
                selectedStepIndex = selectedStepIndex,
                onSelectStep = onSelectStep,
                onAdjustDifficulty = if (target.state != Engine15SessionPresentation.State.ACCEPTED_REST) {
                    onAdjustDifficulty
                } else {
                    null
                },
                busy = busy
            )
        }

        // C. Workout Steps Timeline
        TracksideRunningStepCards(
            items = stepItems,
            selectedStepIndex = selectedStepIndex,
            onSelectStep = { index ->
                onSelectStep(if (selectedStepIndex == index) null else index)
            }
        )

        if (onMove != null || onMissed != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                onMove?.let { action ->
                    OutlinedButton(
                        onClick = action,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("workout-planned-move"),
                        border = BorderStroke(1.dp, WorkoutDetailPalette.border),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Move workout",
                            color = WorkoutDetailPalette.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                onMissed?.let { action ->
                    OutlinedButton(
                        onClick = action,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("workout-planned-missed"),
                        border = BorderStroke(1.dp, WorkoutDetailPalette.border),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Missed / skipped",
                            color = WorkoutDetailPalette.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroMetricColumn(
    value: String,
    caption: String,
    valueTag: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = value,
            color = WorkoutDetailPalette.text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(valueTag)
        )
        Text(
            text = caption,
            color = WorkoutDetailPalette.textMuted,
            fontSize = 11.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun WorkoutProfileAndDifficultyCard(
    session: PlannedSession,
    bars: List<WorkoutProfileBar>,
    stepItems: List<WorkoutStepItem>,
    selectedStepIndex: Int?,
    onSelectStep: (Int?) -> Unit,
    onAdjustDifficulty: ((Int) -> Unit)?,
    busy: Boolean,
    modifier: Modifier = Modifier
) {
    val modified = session.difficultyStep != 0
    val label = Engine15WorkoutDifficultyAdjuster.labelFor(session.difficultyStep)
    val badgeText = if (modified) {
        val sign = if (session.difficultyStep > 0) "+" else ""
        "$label ($sign${session.difficultyStep})"
    } else {
        label
    }
    val summary = Engine15WorkoutDifficultyAdjuster.changeSummary(session)

    val selectedHeaderLabel = selectedStepIndex?.let { idx ->
        when (val item = stepItems.getOrNull(idx)) {
            is WorkoutStepItem.SingleStep -> item.step.title
            is WorkoutStepItem.RepeatGroup -> item.title
            null -> null
        }
    } ?: "Tap any block"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("workout-profile-card"),
        color = WorkoutDetailPalette.card,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (modified) WorkoutDetailPalette.lime.copy(alpha = 0.45f) else WorkoutDetailPalette.border
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "WORKOUT PROFILE",
                    color = WorkoutDetailPalette.textMuted,
                    fontSize = 11.sp,
                    letterSpacing = 1.1.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = selectedHeaderLabel,
                    color = WorkoutDetailPalette.lime,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            if (bars.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(74.dp)
                        .testTag("workout-structure-bars")
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(WorkoutDetailPalette.borderMuted)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        bars.forEachIndexed { barIdx, bar ->
                            val isSelected = selectedStepIndex == null || selectedStepIndex == bar.stepIndex
                            val animatedAlpha by animateFloatAsState(
                                targetValue = if (isSelected) 1f else 0.35f,
                                animationSpec = tween(160),
                                label = "profile-bar-alpha-$barIdx"
                            )
                            val weight = bar.durationMinutes.coerceAtLeast(1f)
                            Box(
                                modifier = Modifier
                                    .weight(weight)
                                    .fillMaxHeight(bar.intensityFraction.coerceIn(0.32f, 1f))
                                    .alpha(animatedAlpha)
                                    .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                bar.accentColor.copy(alpha = 0.96f),
                                                bar.accentColor.copy(alpha = 0.45f),
                                                bar.accentColor.copy(alpha = 0.22f)
                                            )
                                        )
                                    )
                                    .clickable {
                                        onSelectStep(if (selectedStepIndex == bar.stepIndex) null else bar.stepIndex)
                                    }
                                    .testTag("workout-profile-bar-$barIdx")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .align(Alignment.TopCenter)
                                        .background(bar.accentColor)
                                )
                            }
                        }
                    }
                }
            }

            if (onAdjustDifficulty != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(WorkoutDetailPalette.borderMuted)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("workout-difficulty-card"),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = badgeText,
                            color = if (modified) WorkoutDetailPalette.lime else WorkoutDetailPalette.text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("workout-difficulty-badge")
                        )
                        DifficultyControlPill(
                            text = "− Easier",
                            enabled = !busy && session.difficultyStep > -2,
                            onClick = { onAdjustDifficulty(-1) },
                            tag = "workout-difficulty-easier"
                        )
                        DifficultyControlPill(
                            text = "+ Harder",
                            enabled = !busy && session.difficultyStep < 2,
                            onClick = { onAdjustDifficulty(1) },
                            tag = "workout-difficulty-harder"
                        )
                        if (modified) {
                            Text(
                                text = "Reset",
                                color = WorkoutDetailPalette.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !busy) { onAdjustDifficulty(0) }
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                                    .testTag("workout-difficulty-reset")
                            )
                        }
                    }
                    summary?.let {
                        Text(
                            text = it,
                            color = WorkoutDetailPalette.textSecondary,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("workout-difficulty-summary")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DifficultyControlPill(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Box(
        modifier = Modifier
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) WorkoutDetailPalette.cardRaised else WorkoutDetailPalette.cardNested)
            .border(1.dp, WorkoutDetailPalette.border, RoundedCornerShape(999.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) WorkoutDetailPalette.text else WorkoutDetailPalette.textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** Retained for non-running disciplines (Strength, Calisthenics, Conditioning). */
@Composable
internal fun TracksideWorkoutDifficultyCard(
    session: PlannedSession,
    busy: Boolean,
    onAdjust: (Int) -> Unit
) {
    val modified = session.difficultyStep != 0
    val label = Engine15WorkoutDifficultyAdjuster.labelFor(session.difficultyStep)
    val badgeText = if (modified) {
        val sign = if (session.difficultyStep > 0) "+" else ""
        "$label ($sign${session.difficultyStep})"
    } else {
        label
    }
    val summary = Engine15WorkoutDifficultyAdjuster.changeSummary(session)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("workout-difficulty-card"),
        color = TracksidePalette.surfaceRaised,
        contentColor = TracksidePalette.text,
        border = BorderStroke(
            1.dp,
            if (modified) TracksidePalette.lime.copy(alpha = 0.55f) else TracksidePalette.outline
        ),
        shape = RoundedCornerShape(15.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "WORKOUT DIFFICULTY",
                    color = TracksidePalette.textMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        badgeText,
                        color = if (modified) TracksidePalette.lime else TracksidePalette.text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("workout-difficulty-badge")
                    )
                    if (modified) {
                        TextButton(
                            onClick = { onAdjust(0) },
                            enabled = !busy,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .heightIn(min = 32.dp)
                                .testTag("workout-difficulty-reset")
                        ) {
                            Text(
                                "Reset",
                                color = TracksidePalette.lime,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onAdjust(-1) },
                    enabled = !busy && session.difficultyStep > -2,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("workout-difficulty-easier"),
                    border = BorderStroke(1.dp, TracksidePalette.outline),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "− Easier",
                        color = TracksidePalette.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                OutlinedButton(
                    onClick = { onAdjust(1) },
                    enabled = !busy && session.difficultyStep < 2,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("workout-difficulty-harder"),
                    border = BorderStroke(1.dp, TracksidePalette.outline),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "+ Harder",
                        color = TracksidePalette.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            summary?.let {
                Text(
                    it,
                    color = TracksidePalette.textMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.testTag("workout-difficulty-summary")
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, backgroundColor = 0xFF0A0C0E)
@Composable
internal fun WorkoutDetailIntervalPreview() {
    val start = LocalDate.of(2026, 11, 23)
    val base = PlannedSession(
        id = "preview-threshold",
        date = LocalDate.of(2026, 11, 26),
        discipline = "run",
        title = "Threshold Workout",
        minutes = 80,
        distanceKm = 11.36,
        kind = "quality",
        quality = true,
        runningPrescription = RunningPrescription(
            role = "QUALITY",
            family = "Threshold",
            segments = emptyList(),
            mixedSegments = listOf(
                RunningStepV2("WARM_UP", "Easy conversational effort", seconds = 1500, distanceEquivalentMeters = 3500),
                RunningStepV2("WORK", "Threshold effort", seconds = 300, distanceEquivalentMeters = 880),
                RunningStepV2("RECOVERY", "Easy jog", seconds = 120, distanceEquivalentMeters = 280),
                RunningStepV2("WORK", "Threshold effort", seconds = 300, distanceEquivalentMeters = 880),
                RunningStepV2("RECOVERY", "Easy jog", seconds = 120, distanceEquivalentMeters = 280),
                RunningStepV2("WORK", "Threshold effort", seconds = 300, distanceEquivalentMeters = 880),
                RunningStepV2("RECOVERY", "Easy jog", seconds = 120, distanceEquivalentMeters = 280),
                RunningStepV2("WORK", "Threshold effort", seconds = 300, distanceEquivalentMeters = 880),
                RunningStepV2("COOL_DOWN", "Easy warm-down", seconds = 1500, distanceEquivalentMeters = 3500)
            ),
            reconciledDistanceMeters = 11360
        )
    )
    val req = PlanRequest(goal = "Half marathon", start = start, planningPace = 420.0, timeBudgetEnabled = false)
    val adjusted = Engine15WorkoutDifficultyAdjuster.adjust(base, req, +1)
    val plan = PlanSnapshot(
        id = "preview-plan",
        owner = "preview",
        engineVersion = ENGINE15_VERSION,
        policyId = ENGINE15_INDEPENDENT_RUNNING_POLICY,
        createdAt = java.time.Instant.EPOCH,
        request = req,
        sessions = listOf(adjusted),
        explanations = emptyList(),
        status = "approved"
    )
    var selected by remember { mutableStateOf<Int?>(null) }
    StrideTheme {
        Scaffold(
            containerColor = WorkoutDetailPalette.background,
            topBar = { WorkoutDetailTopBar(weekNumber = 1, statusLabel = adjusted.status, onBack = {}) },
            bottomBar = { WorkoutDetailBottomBar(label = "START WORKOUT", enabled = true, onClick = {}) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                TracksidePlannedRunningPreview(
                    session = adjusted,
                    target = Engine15SessionPresentation.forSession(plan, adjusted.id),
                    displayTitle = adjusted.title,
                    onBack = {},
                    planStart = start,
                    onAdjustDifficulty = {},
                    selectedStepIndex = selected,
                    onSelectStep = { selected = it },
                    planningPaceSecPerKm = req.planningPace,
                    showInlineTopBar = false
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800, backgroundColor = 0xFF0A0C0E)
@Composable
internal fun WorkoutDetailSteadyStatePreview() {
    val start = LocalDate.of(2026, 11, 23)
    val steady = PlannedSession(
        id = "preview-easy",
        date = LocalDate.of(2026, 11, 25),
        discipline = "run",
        title = "Aerobic Base Run",
        minutes = 52,
        distanceKm = 8.4,
        kind = "easy",
        runningPrescription = RunningPrescription(
            role = "EASY",
            family = null,
            segments = listOf(
                RunningSegment("EASY", 8400, "Comfortable conversational effort")
            )
        )
    )
    val req = PlanRequest(goal = "Aerobic foundation", start = start, planningPace = 370.0)
    val plan = PlanSnapshot(
        id = "preview-steady",
        owner = "preview",
        engineVersion = ENGINE15_VERSION,
        policyId = ENGINE15_INDEPENDENT_RUNNING_POLICY,
        createdAt = java.time.Instant.EPOCH,
        request = req,
        sessions = listOf(steady),
        explanations = emptyList(),
        status = "approved"
    )
    var selected by remember { mutableStateOf<Int?>(null) }
    StrideTheme {
        Scaffold(
            containerColor = WorkoutDetailPalette.background,
            topBar = { WorkoutDetailTopBar(weekNumber = 1, statusLabel = steady.status, onBack = {}) },
            bottomBar = { WorkoutDetailBottomBar(label = "START WORKOUT", enabled = true, onClick = {}) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                TracksidePlannedRunningPreview(
                    session = steady,
                    target = Engine15SessionPresentation.forSession(plan, steady.id),
                    displayTitle = steady.title,
                    onBack = {},
                    planStart = start,
                    onAdjustDifficulty = {},
                    selectedStepIndex = selected,
                    onSelectStep = { selected = it },
                    planningPaceSecPerKm = req.planningPace,
                    showInlineTopBar = false
                )
            }
        }
    }
}
