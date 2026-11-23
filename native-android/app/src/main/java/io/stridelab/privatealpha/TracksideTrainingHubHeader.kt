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
 * Calendar-first navigation: Day | Week toggle (`training-view-day` / `training-view-agenda`),
 * interactive 7-day calendar with dual Logged + Planned markers, and 1-tap Plan Switcher / Manage.
 */
@Composable
internal fun TracksideTrainingHubHeader(
    active: PlanSnapshot,
    approvedPlans: List<PlanSnapshot>,
    selectedDate: LocalDate,
    agenda: Boolean,
    busy: Boolean,
    actions: PlanActions,
    recordedDates: Set<LocalDate> = emptySet(),
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
        Box(Modifier.width(172.dp)) {
            StrideLabLiquidSegmentedControl(
                labels = listOf("day" to "Day", "agenda" to "Week"),
                selectedKey = if (agenda) "agenda" else "day",
                accent = hubLime,
                testTagPrefix = "training-view",
                onSelect = { key -> onAgenda(key == "agenda") }
            )
        }
        TextButton(
            onClick = onManage,
            modifier = Modifier.testTag("training-manage-plan")
        ) {
            Text("Manage ›", color = hubLime, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }

    // Compact Active Training Plan Card
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
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                        .padding(horizontal = 9.dp, vertical = 3.dp)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        active.name ?: active.request.goal,
                        fontSize = 18.sp,
                        color = hubInk,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.3).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${active.request.weeks}-week block · ${active.sessions.size} workouts",
                        color = hubMuted,
                        fontSize = 11.5.sp
                    )
                }
                if (approvedPlans.size > 1) {
                    TextButton(onClick = { selectorOpen = !selectorOpen }) {
                        Text(
                            if (selectorOpen) "Hide" else "Switch ▾",
                            color = hubLime,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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

    // Interactive 7-Day Calendar Card (tapping a day focuses Day; tapping Week pill focuses Week)
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
                recordedDates = recordedDates,
                weekScopeSelected = agenda,
                onToggleWeekScope = { onAgenda(!agenda) },
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
