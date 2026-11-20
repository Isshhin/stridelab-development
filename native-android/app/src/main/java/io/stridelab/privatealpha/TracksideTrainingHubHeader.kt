package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

private val hubLime = StrideLabVisualPalette.lime
private val hubInk = StrideLabVisualPalette.ink
private val hubMuted = StrideLabVisualPalette.secondary
private val hubCard = StrideLabVisualPalette.surface
private val hubBorder = StrideLabVisualPalette.border

/**
 * Art-directed Training Hub Header.
 * Pure AMOLED obsidian surfaces, crisp high-contrast Day/Agenda toggle,
 * subtle Lime top wash on the active training plan card.
 */
@Composable
internal fun TracksideTrainingHubHeader(
    active: PlanSnapshot,
    approvedPlans: List<PlanSnapshot>,
    pendingDrafts: Int,
    selectedDate: LocalDate,
    agenda: Boolean,
    busy: Boolean,
    actions: PlanActions,
    onAgenda: (Boolean) -> Unit,
    onSelectedDate: (LocalDate) -> Unit,
    onPlanSelected: (String) -> Unit,
    onManage: () -> Unit,
    onPlanTools: () -> Unit
) {
    var selectorOpen by rememberSaveable(active.owner) { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = hubCard,
            shape = RoundedCornerShape(13.dp),
            border = BorderStroke(1.dp, hubBorder)
        ) {
            Row(
                modifier = Modifier.padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                listOf(false to "Day", true to "Agenda").forEach { (mode, label) ->
                    val selected = agenda == mode
                    Surface(
                        onClick = { onAgenda(mode) },
                        modifier = Modifier
                            .width(78.dp)
                            .height(36.dp)
                            .testTag(if (mode) "training-view-agenda" else "training-view-day"),
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) hubLime else Color.Transparent
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                label,
                                color = if (selected) PlanBuilderPalette.accentOn else hubMuted,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
        TextButton(
            onClick = onManage,
            modifier = Modifier.testTag("training-manage-plan")
        ) {
            Text("Manage ›", color = hubLime, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = hubCard,
        border = BorderStroke(1.dp, StrideLabVisualPalette.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("training-active-plan-card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(hubLime.copy(alpha = 0.07f), Color.Transparent)
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ACTIVE TRAINING PLAN",
                    fontSize = 10.sp,
                    letterSpacing = 1.3.sp,
                    color = StrideLabVisualPalette.muted,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(StrideLabVisualPalette.limeTint)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        "ACTIVE",
                        color = hubLime,
                        fontSize = 9.5.sp,
                        letterSpacing = 0.8.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            Text(
                active.name ?: active.request.goal,
                fontSize = 20.sp,
                color = hubInk,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${active.request.weeks}-week block · ${active.sessions.size} workouts",
                    color = hubMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                if (pendingDrafts > 0) TextButton(onClick = onPlanTools) {
                    Text(
                        "$pendingDrafts draft${if (pendingDrafts == 1) "" else "s"}",
                        color = hubLime,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (approvedPlans.size > 1) TextButton(onClick = { selectorOpen = !selectorOpen }) {
                    Text(
                        if (selectorOpen) "Hide" else "Switch",
                        color = hubLime,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            TracksideTrainingTargetWarning(active)
            if (selectorOpen) approvedPlans.forEach { candidate ->
                val isCurrent = candidate.id == active.id
                Surface(
                    onClick = { onPlanSelected(candidate.id); selectorOpen = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp),
                    color = if (isCurrent) StrideLabVisualPalette.limeTint else StrideLabVisualPalette.surfaceRaised,
                    shape = RoundedCornerShape(11.dp),
                    border = BorderStroke(1.dp, if (isCurrent) hubLime else hubBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            candidate.name ?: candidate.request.goal,
                            color = hubInk,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            fontSize = 12.5.sp
                        )
                        if (isCurrent) Text("Active", color = hubLime, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = hubCard,
        border = BorderStroke(1.dp, hubBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            TracksideTrainingWeekChart(
                plan = active,
                selected = selectedDate,
                showNavigation = true,
                onSelect = onSelectedDate
            )
        }
    }
    Engine15CoachReviewCard(active, actions, busy)
}

/** Advisory for pre-fix approved plans; never mutates or silently relabels history. */
@Composable
internal fun TracksideTrainingTargetWarning(plan: PlanSnapshot) {
    val review = Engine15MileageTargetIntegrity.review(plan) ?: return
    if (!review.requiresFoundationDecision || plan.request.foundationAcknowledged) return
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("training-target-integrity-warning"),
        color = StrideLabVisualPalette.surfaceRaised,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, StrideLabVisualPalette.moderateAmber.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "PROGRESSION ADVISORY",
                color = StrideLabVisualPalette.moderateAmber,
                fontSize = 10.sp,
                letterSpacing = 1.1.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                review.explanation(),
                color = hubMuted,
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )
        }
    }
}
