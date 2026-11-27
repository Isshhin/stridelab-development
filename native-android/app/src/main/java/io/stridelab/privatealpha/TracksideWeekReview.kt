package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A saved planned-distance total; any unknown running distance keeps it unknown. */
internal fun weekReviewDistance(rows: List<PlannedSession>): Double? {
    val distances = rows.filter { it.discipline == "run" }.map { Engine15SessionPresentation.currentDistance(it) }
    if (distances.any { it == null || !it.isFinite() || it < 0.0 }) return null
    return distances.filterNotNull().sum().takeIf { it.isFinite() }
}

private fun sessionAccentColor(session: PlannedSession): Color = when {
    session.discipline == "run" && session.kind == "long" -> StrideLabVisualPalette.lime
    session.discipline == "run" && session.kind == "quality" -> {
        val fam = session.runningPrescription?.family?.lowercase(Locale.ROOT).orEmpty()
        if (fam == "intervals" || fam == "hills") StrideLabVisualPalette.highCoral
        else StrideLabVisualPalette.moderateAmber
    }
    session.discipline == "run" -> StrideLabVisualPalette.easyTeal
    else -> StrideLabVisualPalette.disciplineColor(session.discipline)
}

/**
 * Art-directed Plan Blueprint & Weekly Review screen.
 * Pure AMOLED obsidian cards, Hero Plan Blueprint header, interactive periodization &
 * intensity charts, inline mini workout block thumbnails, zero developer/research clutter,
 * and unrestricted 1-tap finalization.
 */
@Composable
internal fun TracksideWeekReview(
    request: PlanRequest,
    plan: PlanSnapshot?,
    busy: Boolean,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onOpenSession: (String) -> Unit = {},
    weekIndex: Int = 0,
    onWeekChange: (Int) -> Unit = {},
    error: String? = null,
    experimentalSelected: Boolean = false,
    onExperimentalSelected: (Boolean) -> Unit = {},
    onSaveResearchReview: (PlanRequest, IndependentAndroidPreviewConstraints) -> Unit = { _, _ -> },
    researchDraftCount: Int = 0,
    savedResearchReviews: List<IndependentResearchDraft> = emptyList(),
    onChooseGoalOption: (PlanRequest) -> Unit = {},
    activities: List<ActivityRecord> = emptyList(),
    recoveryNote: String? = null,
    supportReview: RunnerSupportSchedule.Review? = null,
    activationLabel: String = "Activate Plan",
    trackControls: (@Composable () -> Unit)? = null
) {
    val firstDate = plan?.request?.start ?: request.start
    val totalWeeks = (plan?.request?.weeks ?: request.weeks).coerceAtLeast(1)
    val safeWeekIdx = weekIndex.coerceIn(0, totalWeeks - 1)
    val weekStart = firstDate.plusWeeks(safeWeekIdx.toLong())
    val weekEnd = weekStart.plusDays(6)
    val blockEnd = firstDate.plusWeeks(totalWeeks.toLong()).minusDays(1)
    val rows = plan?.sessions?.filter { it.date in weekStart..weekEnd } ?: emptyList()
    val total = if (plan == null)
        request.runDays + request.strengthDays + request.calDays + request.conditioningDays
    else rows.size
    val days = if (plan == null) request.days.size else rows.map { it.date }.distinct().size
    val exactAssignments = request.weeklySlots.flatMap { (discipline, weekdays) ->
        weekdays.mapIndexedNotNull { index, weekday ->
            if (weekday !in 1..7) null
            else Triple(weekday, discipline, WeeklyPlacement.label(request, discipline, index))
        }
    }
    val counts = if (plan == null) listOf(
        "run" to request.runDays, "str" to request.strengthDays,
        "cal" to request.calDays, "cond" to request.conditioningDays
    ) else listOf("run", "str", "cal", "cond").map { d ->
        d to plan.sessions.count { it.discipline == d }
    }

    val targetReview = plan?.let(Engine15MileageTargetIntegrity::review)
    val dateFmt = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

    TracksideBuilderFrame(
        progressStep = 6,
        toolbarTitle = if (plan == null) "Blueprint" else "Plan blueprint",
        eyebrow = "PLAN / BLUEPRINT",
        headline = if (plan == null) "Your training\nblueprint" else "Your training\nblueprint",
        description = if (plan == null) "Review your weekly structure before generating your preview."
        else "Tap any week bar or workout card to inspect details before activating.",
        onBack = onBack,
        ctaLabel = if (busy) "Finalizing…" else if (plan == null) "Generate My Plan" else activationLabel,
        onContinue = onContinue,
        continueEnabled = !busy && supportReview?.blocked != true,
        descriptionBottomGap = 16,
        contentTopPadding = 12,
        headlineLineHeight = 34
    ) {
        if (plan != null) TextButton(onClick = onBack, enabled = !busy,
            modifier = Modifier.testTag("builder-discard-preview")) { Text("Discard preview") }
        trackControls?.invoke()
        supportReview?.let { RunnerSupportPreviewCard(it, weekStart, weekEnd) }
        // 1. Flat Hero Plan Blueprint Summary (when plan exists)
        if (plan != null) {
            val peakWeeklyKm = remember(plan.id, plan.revision) {
                if (request.runDays == 0 && plan.sessions.none { it.discipline == "run" }) null
                else (0 until totalWeeks).mapNotNull { w ->
                    val ws = firstDate.plusWeeks(w.toLong())
                    val we = ws.plusDays(6)
                    weekReviewDistance(plan.sessions.filter { it.date in ws..we })
                }.maxOrNull()
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("builder-hero-blueprint")
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${firstDate.format(dateFmt)} - ${blockEnd.format(dateFmt)}".uppercase(),
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold,
                        color = PlanBuilderPalette.accent
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        counts.filter { it.second > 0 }.forEach { (disc, _) ->
                            val dColor = StrideLabVisualPalette.disciplineColor(disc)
                            val label = when (disc) {
                                "run" -> "Run"
                                "str" -> "Strength"
                                "cal" -> "Cali"
                                else -> "Cond"
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(dColor.copy(alpha = 0.14f))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    label,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = dColor
                                )
                            }
                        }
                    }
                }

                Text(
                    plan.request.goal,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = PlanBuilderPalette.text,
                    letterSpacing = (-0.4).sp
                )

                StrideLabHairline()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeroKpiBlock(
                        value = peakWeeklyKm?.let { String.format(Locale.US, "%.0f km", it) } ?: "-",
                        label = "Peak week",
                        accent = PlanBuilderPalette.accent,
                        modifier = Modifier.weight(1f)
                    )
                    HeroKpiBlock(
                        value = "$totalWeeks wks",
                        label = "Block length",
                        accent = PlanBuilderPalette.text,
                        modifier = Modifier.weight(1f)
                    )
                    HeroKpiBlock(
                        value = plan.sessions.size.toString(),
                        label = "Workouts",
                        accent = PlanBuilderPalette.text,
                        modifier = Modifier.weight(1f)
                    )
                }
                StrideLabHairline()
            }
            Spacer(Modifier.height(10.dp))
        }

        // 2. Interactive Visual Analytics Stack (Running plans)
        if (plan != null && request.runDays > 0) {
            val tidEval = remember(plan.id, plan.revision) {
                Engine15MarathonTidEvaluator.evaluate(plan)
            }
            val paceZones = remember(plan.request, activities) {
                Engine15PaceZoneEstimator.estimate(plan.request, activities = activities)
            }
            PlanPeriodizationChart(
                plan = plan,
                selectedWeekIndex = safeWeekIdx,
                onSelectWeek = onWeekChange
            )
            Spacer(Modifier.height(10.dp))
            TidIntensityBar(evaluation = tidEval)
            Spacer(Modifier.height(10.dp))
            ProvisionalPaceZoneStrip(zones = paceZones)
            Spacer(Modifier.height(12.dp))
        }

        recoveryNote?.let {
            Text(it, color = StrideLabVisualPalette.moderateAmber, fontSize = 12.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(12.dp))
        }

        // 3. Flat Advisory Row (non-blocking — user can always finalize immediately)
        if (plan != null && targetReview != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("builder-mileage-target-review"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(StrideLabVisualPalette.moderateAmber)
                    )
                    Column(
                        modifier = Modifier.weight(1f).padding(vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            if (plan.request.foundationAcknowledged) "FOUNDATION PROGRESSION"
                            else "COACH PROGRESSION NOTE",
                            color = StrideLabVisualPalette.moderateAmber,
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            targetReview.explanation(),
                            color = PlanBuilderPalette.secondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        if (targetReview.requiresFoundationDecision && !plan.request.foundationAcknowledged) {
                            TextButton(
                                onClick = { onChooseGoalOption(plan.request.copy(foundationAcknowledged = true)) },
                                enabled = !busy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 40.dp)
                                    .testTag("builder-choose-foundation")
                            ) {
                                Text(
                                    "Switch to gradual foundation build",
                                    color = PlanBuilderPalette.accent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                StrideLabHairline()
            }
            Spacer(Modifier.height(10.dp))
        }

        if (plan != null) MileageGoalChoices(plan, busy, onChooseGoalOption)

        // 4. Flat Weekly KPI Strip
        val trainingDays = days.coerceIn(0, 7)
        val noRunning = request.runDays == 0 && rows.none { it.discipline == "run" }
        val distance = when {
            noRunning -> null
            plan != null -> weekReviewDistance(rows)
            else -> request.targetWeeklyKm ?: request.weeklyKm
        }
        val distanceLabel = when {
            noRunning -> "No running"
            plan != null && distance == null -> "Est. volume"
            plan != null -> "Week km"
            request.targetWeeklyKm != null -> "Target km"
            else -> "Weekly km"
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    Triple(distance?.let { String.format(Locale.US, "%.1f", it) } ?: "-", distanceLabel, PlanBuilderPalette.accent),
                    Triple(total.toString(), "Sessions", PlanBuilderPalette.text),
                    Triple(trainingDays.toString(), "Active", StrideLabVisualPalette.easyTeal),
                    Triple((7 - trainingDays).toString(), "Rest", PlanBuilderPalette.secondary)
                ).forEach { (value, caption, color) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .semantics(mergeDescendants = true) {},
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            value,
                            color = color,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            style = TabularTextStyle,
                            maxLines = 1
                        )
                        Text(
                            caption,
                            color = PlanBuilderPalette.muted,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            }
            StrideLabHairline()
        }

        // 5. Flat Week Switcher
        if (plan != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = { onWeekChange(safeWeekIdx - 1) },
                        enabled = safeWeekIdx > 0,
                        modifier = Modifier.testTag("builder-previous-week")
                    ) {
                        Text(
                            "‹ Prev",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (safeWeekIdx > 0) PlanBuilderPalette.accent else PlanBuilderPalette.disabled
                        )
                    }
                    Text(
                        "Week ${safeWeekIdx + 1} of $totalWeeks",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = PlanBuilderPalette.text,
                        style = TabularTextStyle
                    )
                    TextButton(
                        onClick = { onWeekChange(safeWeekIdx + 1) },
                        enabled = safeWeekIdx < totalWeeks - 1,
                        modifier = Modifier.testTag("builder-next-week")
                    ) {
                        Text(
                            "Next ›",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (safeWeekIdx < totalWeeks - 1) PlanBuilderPalette.accent else PlanBuilderPalette.disabled
                        )
                    }
                }
                StrideLabHairline()
            }
        }

        Spacer(Modifier.height(10.dp))

        // 6. Flat Weekly Schedule with semantic left stripes + inline mini workout bar charts
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    (weekStart.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())) + " - " +
                        weekEnd.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))).uppercase(),
                    fontSize = 10.sp,
                    letterSpacing = 1.1.sp,
                    color = PlanBuilderPalette.muted,
                    fontWeight = FontWeight.Bold
                )
                val doubleDays = plan?.let { PlanScheduleValidation.isMultiPolicy(it.policyId) }
                    ?: (request.policyChoice == "multi-session" || request.allowDoubleDays)
                Text(
                    if (doubleDays) "ADAPTIVE MULTI-SESSION" else "SINGLE SESSION / DAY",
                    fontSize = 9.5.sp,
                    letterSpacing = 0.8.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (doubleDays) StrideLabVisualPalette.moderateAmber else PlanBuilderPalette.secondary
                )
            }

            StrideLabHairline()

            (0..6).forEach { offset ->
                val date = weekStart.plusDays(offset.toLong())
                val day = date.dayOfWeek.value
                val assigned = rows.filter { it.date == date }
                val isActiveDay = assigned.isNotEmpty() || (plan == null && exactAssignments.any { it.first == day })

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(
                        modifier = Modifier.width(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            date.dayOfWeek.name.take(3),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActiveDay) PlanBuilderPalette.accent else PlanBuilderPalette.muted
                        )
                        Text(
                            date.dayOfMonth.toString(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isActiveDay) PlanBuilderPalette.text else PlanBuilderPalette.muted,
                            style = TabularTextStyle
                        )
                    }

                    if (plan == null) {
                        val choices = exactAssignments.filter { it.first == day }
                        Text(
                            when {
                                choices.isNotEmpty() -> choices.joinToString(" + ") { it.third }
                                day in request.days -> "Available · coach assigns"
                                else -> "Rest day"
                            },
                            fontSize = 12.sp,
                            color = if (choices.isNotEmpty()) PlanBuilderPalette.text else PlanBuilderPalette.muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("builder-requested-day-$day")
                        )
                    } else if (assigned.isEmpty()) {
                        Text(
                            "Rest & recovery",
                            fontSize = 12.sp,
                            color = PlanBuilderPalette.muted,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            assigned.take(2).forEach { session ->
                                val accent = sessionAccentColor(session)
                                val dist = Engine15SessionPresentation.currentDistance(session)
                                val subtitle = buildString {
                                    if (dist != null && dist > 0.0) {
                                        append(String.format(Locale.US, "%.1f km", dist))
                                        append(" · ")
                                    }
                                    append("${session.minutes} min")
                                }
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("builder-preview-session-${session.id}"),
                                    onClick = { onOpenSession(session.id) },
                                    color = Color.Transparent
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Semantic left color stripe
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(accent)
                                        )
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(start = 10.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(Modifier.weight(1f)) {
                                                    Text(
                                                        session.effectiveTitle,
                                                        fontSize = 13.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = PlanBuilderPalette.text,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        subtitle,
                                                        fontSize = 11.sp,
                                                        color = PlanBuilderPalette.secondary,
                                                        style = TabularTextStyle
                                                    )
                                                }
                                                Text(
                                                    "›",
                                                    color = PlanBuilderPalette.muted,
                                                    fontSize = 18.sp
                                                )
                                            }
                                            if (session.discipline == "run") {
                                                val blocks = remember(session.id, session.title, session.distanceKm) {
                                                    WorkoutVisualBlocks.fromSession(session)
                                                }
                                                if (blocks.isNotEmpty()) {
                                                    WorkoutStructureBarChart(
                                                        blocks = blocks,
                                                        height = 18.dp,
                                                        interactive = false,
                                                        tag = "mini-bars-${session.id}"
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                StrideLabHairline()
            }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                PlanBuilderErrorBanner(it)
            }
        }
    }
}

@Composable
private fun HeroKpiBlock(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            value,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = accent,
            style = TabularTextStyle,
            maxLines = 1
        )
        Text(
            label,
            fontSize = 10.sp,
            color = PlanBuilderPalette.muted,
            maxLines = 1
        )
    }
}
