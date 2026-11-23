package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

private val hubLime = StrideLabSurfaceTokens.lime
private val hubInk = StrideLabSurfaceTokens.ink
private val hubMuted = StrideLabSurfaceTokens.inkSecondary
private val hubCard = StrideLabSurfaceTokens.surfaceElevated
private val hubBorder = StrideLabSurfaceTokens.hairline

/**
 * Art-directed Training Hub Header.
 * Calendar-first navigation directly under Training header:
 * Day | Week toggle (`training-view-day` / `training-view-agenda`),
 * interactive 7-day calendar with dual Logged + Planned markers, and 1-tap Plan Switcher / Manage.
 */
@Composable
internal fun TracksideTrainingHubHeader(
    selectedPlan: PlanSnapshot?,
    approvedPlans: List<PlanSnapshot>,
    scopedPlans: List<PlanSnapshot>,
    tracks: List<String>,
    selectedTrack: String?,
    selectedDate: LocalDate,
    agenda: Boolean,
    recordedDates: Set<LocalDate> = emptySet(),
    customPlanned: Map<LocalDate, Int> = emptyMap(),
    customCalendar: Boolean = false,
    onAgenda: (Boolean) -> Unit,
    onSelectedDate: (LocalDate) -> Unit,
    onPlanSelected: (String?) -> Unit,
    onTrackSelected: (String) -> Unit,
    onManage: (String) -> Unit
) {
    val scoped = scopedPlans
    var selectorOpen by rememberSaveable(approvedPlans.first().owner) { mutableStateOf(false) }
    var manageOpen by rememberSaveable(approvedPlans.first().owner) { mutableStateOf(false) }

    // Interactive 7-Day Calendar placed first directly below Training header
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = hubCard,
        border = BorderStroke(1.dp, hubBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(164.dp)) {
                    StrideLabLiquidSegmentedControl(
                        labels = listOf("day" to "Day", "agenda" to "Week"),
                        selectedKey = if (agenda) "agenda" else "day",
                        accent = hubLime,
                        testTagPrefix = "training-view",
                        onSelect = { key -> onAgenda(key == "agenda") }
                    )
                }
                Box {
                    TextButton(
                        onClick = {
                            if (scoped.size == 1) onManage(scoped.single().id) else manageOpen = true
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("training-manage-plan")
                    ) {
                        Text("Manage ›", color = hubLime, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    DropdownMenu(expanded = manageOpen, onDismissRequest = { manageOpen = false }) {
                        scoped.forEach { plan ->
                            DropdownMenuItem(
                                text = { Text(plan.name ?: plan.request.goal) },
                                onClick = { manageOpen = false; onManage(plan.id) },
                                modifier = Modifier.testTag("training-manage-${plan.id}")
                            )
                        }
                    }
                }
            }

            TracksideTrainingWeekChart(
                plans = scoped,
                selected = selectedDate,
                showNavigation = true,
                recordedDates = recordedDates,
                weekScopeSelected = agenda,
                onToggleWeekScope = { onAgenda(!agenda) },
                customPlanned = customPlanned,
                customCalendar = customCalendar,
                onSelect = onSelectedDate
            )
        }
    }

    // Flat Active Plan Identity Strip (hairline-bounded, no heavy nested gradient box)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("training-active-plan-card"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (scoped.size > 1) "ACTIVE TRAINING PLANS" else "ACTIVE TRAINING PLAN",
                    fontSize = 10.sp,
                    letterSpacing = 1.1.sp,
                    color = StrideLabSurfaceTokens.inkMuted,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (selectedTrack != null) trainingTrackLabel(selectedTrack)
                    else if (scoped.size > 1) "All active plans"
                    else scoped.single().let { it.name ?: it.request.goal },
                    fontSize = 16.sp,
                    color = hubInk,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (scoped.size > 1) "${scoped.size} plans · ${scoped.sumOf { it.sessions.size }} workouts"
                    else scoped.single().let { "${it.request.weeks}-week block · ${it.sessions.size} workouts" },
                    color = hubMuted,
                    fontSize = 12.sp,
                    style = TabularTextStyle
                )
            }
            if (approvedPlans.size > 1 || tracks.size > 1) {
                TextButton(
                    onClick = { selectorOpen = !selectorOpen },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("training-switch-plan")
                ) {
                    Text(
                        if (selectorOpen) "Hide" else "Switch ▾",
                        color = hubLime,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        scoped.forEach { TracksideTrainingTargetWarning(it) }
        if (selectorOpen) {
            Column(modifier = Modifier.fillMaxWidth()) {
                StrideLabHairline()
                (listOf(null) + approvedPlans).forEach { candidate ->
                    val isCurrent = candidate?.id == selectedPlan?.id && selectedTrack == null
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isCurrent) StrideLabSurfaceTokens.surfaceElevated
                                else StrideLabSurfaceTokens.canvas
                            )
                            .clickable {
                                onPlanSelected(candidate?.id)
                                selectorOpen = false
                            }
                            .padding(horizontal = 10.dp, vertical = 12.dp)
                            .testTag("training-filter-${candidate?.id ?: "all"}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            candidate?.let { it.name ?: it.request.goal } ?: "All active plans",
                            color = if (isCurrent) hubLime else hubInk,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            fontSize = 13.sp
                        )
                        if (isCurrent) {
                            Text("Active", color = hubLime, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    StrideLabHairline()
                }
                if (tracks.size > 1) {
                    tracks.forEach { track ->
                        TextButton(
                            onClick = { onTrackSelected(track); selectorOpen = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("training-discipline-$track")
                        ) {
                            Text(
                                trainingTrackLabel(track),
                                color = if (track == selectedTrack) hubLime else hubInk
                            )
                        }
                    }
                }
            }
        }
        StrideLabHairline()
    }
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
