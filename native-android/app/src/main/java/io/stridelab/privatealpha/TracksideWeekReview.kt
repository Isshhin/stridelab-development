package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** A saved planned-distance total; any unknown running distance keeps it unknown. */
internal fun weekReviewDistance(rows: List<PlannedSession>): Double? {
    val distances=rows.filter { it.discipline=="run" }.map { Engine15SessionPresentation.currentDistance(it) }
    if(distances.any { it==null || !it.isFinite() || it<0.0 })return null
    return distances.filterNotNull().sum().takeIf { it.isFinite() }
}

/**
 * Approved 614:412 Week Preview / 653:471 first plan approval.
 * Before generation, only availability and counts are known; the view MUST NOT
 * fabricate workout placement. After the engine saves its real draft, the same
 * Figma geometry displays actual dated prescriptions and requires approval.
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
    onSaveResearchReview: (PlanRequest,IndependentAndroidPreviewConstraints) -> Unit = { _,_ -> },
    researchDraftCount: Int = 0,
    savedResearchReviews: List<IndependentResearchDraft> = emptyList(),
    onChooseGoalOption:(PlanRequest)->Unit = {}
) {
    // A plan can start on any weekday. Its engine weeks are anchored at
    // request.start, NOT calendar Mondays; the review must display every
    // prescribed session, including the last partial calendar week.
    val firstDate = plan?.request?.start ?: request.start
    val weekStart = firstDate.plusWeeks(
        weekIndex.coerceIn(0,(request.weeks-1).coerceAtLeast(0)).toLong()
    )
    val weekEnd = weekStart.plusDays(6)
    val rows = plan?.sessions?.filter {
        it.date in weekStart..weekEnd
    } ?: emptyList()
    val total = if(plan == null)
        request.runDays + request.strengthDays + request.calDays + request.conditioningDays
    else rows.size
    val days = if(plan==null)request.days.size else rows.map { it.date }.distinct().size
    val exactAssignments = request.weeklySlots.flatMap { (discipline, weekdays) ->
        weekdays.mapIndexedNotNull { index, weekday ->
            if(weekday !in 1..7) null
            else Triple(weekday,discipline,WeeklyPlacement.label(request,discipline,index))
        }
    }
    val counts = if(plan==null) listOf(
        "run" to request.runDays, "str" to request.strengthDays,
        "cal" to request.calDays, "cond" to request.conditioningDays
    ) else listOf("run","str","cal","cond").map { d ->
        d to rows.count { it.discipline == d }
    }

    val targetReview=plan?.let(Engine15MileageTargetIntegrity::review)
    val needsFoundationChoice=targetReview?.requiresFoundationDecision==true &&
        plan?.request?.foundationAcknowledged!=true

    TracksideBuilderFrame(
        progressStep = 6,
        toolbarTitle = if (plan == null) "Training blueprint" else "Review plan",
        eyebrow = "PLAN / WEEK PREVIEW",
        headline = if (plan == null) "Your training blueprint" else "Review your training plan",
        description = if (plan == null) "Review your setup before StrideLab creates your plan."
            else "Your personalized plan. Review before finalizing.",
        onBack = onBack,
        ctaLabel = if(needsFoundationChoice) "Resolve mileage target"
            else if (plan == null && experimentalSelected)
                "Research mode — no saved plan"
            else if(plan == null) "Generate My Plan" else "Finalize Plan",
        onContinue = onContinue,
        continueEnabled = !busy && (plan != null || !experimentalSelected) &&
            !needsFoundationChoice,
        descriptionBottomGap = 16,
        footer = {
            Column(Modifier.padding(horizontal=20.dp,vertical=9.dp)) {
                Text(
                    if(plan == null && exactAssignments.isNotEmpty())
                        "Chosen weekdays are requests, not generated prescriptions. Conflicts will be reported."
                    else if(plan == null)
                        "Unassigned sessions will be placed by the coaching engine."
                    else "Confirm only after reviewing the saved prescriptions.",
                    color=Color(0xFFC5C5C5),fontSize=11.sp,
                    modifier=Modifier.fillMaxWidth()
                )
            }
        }
    ) {

    if(plan!=null && targetReview!=null) {
        Surface(
            modifier=Modifier.fillMaxWidth().testTag("builder-mileage-target-review"),
            color=PlanBuilderPalette.surface,
            shape=RoundedCornerShape(14.dp),
            border=BorderStroke(1.dp,
                if(needsFoundationChoice) PlanBuilderPalette.accent
                else PlanBuilderPalette.border)
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement=Arrangement.spacedBy(9.dp)) {
                Text(
                    when {
                        plan.request.foundationAcknowledged -> "FOUNDATION · FUTURE MILEAGE GOAL"
                        needsFoundationChoice -> "MILEAGE TARGET NEEDS REVIEW"
                        else -> "CURRENT TRAINING VS MILEAGE TARGET"
                    },
                    color=PlanBuilderPalette.accent,
                    fontSize=11.sp,fontWeight=FontWeight.Bold
                )
                Text(targetReview.explanation(),
                    color=PlanBuilderPalette.text,
                    fontSize=12.sp,lineHeight=18.sp)
                if(needsFoundationChoice) {
                    Text(
                        "This plan cannot be finalized as the requested mileage. " +
                        "Go back to Running ability and enter your actual recent weekly " +
                        "mileage and longest run, or explicitly choose a foundation " +
                        "plan with the requested mileage as a future goal.",
                        color=PlanBuilderPalette.secondary,
                        fontSize=12.sp,lineHeight=18.sp
                    )
                    TextButton(
                        onClick={onChooseGoalOption(plan.request.copy(foundationAcknowledged=true))},
                        enabled=!busy,
                        modifier=Modifier.fillMaxWidth().heightIn(min=44.dp)
                            .testTag("builder-choose-foundation")
                    ) {
                        Text("Use foundation plan instead",
                            color=PlanBuilderPalette.accent,
                            fontWeight=FontWeight.SemiBold)
                    }
                } else if(plan.request.foundationAcknowledged) {
                    Text(
                        "Foundation plan explicitly selected. This is not a promise " +
                        "to reach the future mileage target; follow the displayed workouts and reassess " +
                        "your recent training before increasing mileage.",
                        color=PlanBuilderPalette.secondary,
                        fontSize=12.sp,lineHeight=18.sp
                    )
                } else if(targetReview.estimatedPeakKm!=null &&
                    targetReview.estimatedPeakKm+0.01<targetReview.requestedKm) {
                    Text("The requested target is not fully reached by this conservative schedule.",
                        color=PlanBuilderPalette.secondary,fontSize=12.sp,lineHeight=18.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    if(plan!=null) MileageGoalChoices(plan,busy,onChooseGoalOption)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            val trainingDays = days.coerceIn(0,7)
            val noRunning = request.runDays == 0 && rows.none { it.discipline == "run" }
            val distance = when {
                noRunning -> null
                plan != null -> weekReviewDistance(rows)
                else -> request.targetWeeklyKm ?: request.weeklyKm
            }
            val distanceLabel = when {
                noRunning -> "no running"
                plan != null && distance == null -> "distance unknown"
                plan != null -> "planned km"
                request.targetWeeklyKm != null -> "target km / week"
                else -> "recent km / week"
            }
            listOf(
                (distance?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—") to distanceLabel,
                total.toString() to "sessions",
                trainingDays.toString() to if (plan == null) "AVAILABLE DAYS" else "ACTIVE DAYS",
                (7-trainingDays).toString() to "rest days"
            ).chunked(2).forEach { metrics ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    metrics.forEach { (value,caption) ->
                        Surface(Modifier.weight(1f).heightIn(min=62.dp),
                            color=PlanBuilderPalette.surface,
                            shape=RoundedCornerShape(12.dp),
                            border=BorderStroke(1.dp,PlanBuilderPalette.border)) {
                            Column(Modifier.semantics(mergeDescendants=true) {}
                                .padding(horizontal=10.dp,vertical=10.dp),
                                horizontalAlignment=Alignment.CenterHorizontally) {
                                Text(value,color=PlanBuilderPalette.text,
                                    fontSize=21.sp,fontWeight=FontWeight.Bold)
                                Text(caption,color=PlanBuilderPalette.secondary,fontSize=12.sp)
                            }
                        }
                    }
                }
            }
        }
        if(plan!=null && request.runDays>0 && request.targetWeeklyKm!=null) {
            Spacer(Modifier.height(8.dp))
            Text(String.format(Locale.getDefault(),
                    if(request.foundationAcknowledged) "Future goal (not prescribed): %.1f km / week"
                    else "Requested target: %.1f km / week",
                    request.targetWeeklyKm),
                color=PlanBuilderPalette.secondary,fontSize=12.sp)
        }
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            counts.filter { it.second > 0 }.forEach { (discipline, count) ->
                val name = when (discipline) {
                    "run" -> "Running"
                    "str" -> "Strength"
                    "cal" -> "Cali"
                    else -> "Conditioning"
                }
                Text("$count $name", color = PlanBuilderPalette.secondary,
                    fontSize = 10.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(12.dp))
        if(plan != null) {
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick={onWeekChange(weekIndex-1)},
                    enabled=weekIndex>0,modifier=Modifier.testTag("builder-previous-week")) {
                    Text("‹ Previous",fontSize=12.sp)
                }
                Text("Week ${weekIndex+1} of ${request.weeks}",fontSize=11.sp,
                    color=Color(0xFFC5C5C5))
                TextButton(onClick={onWeekChange(weekIndex+1)},
                    enabled=weekIndex<request.weeks-1,
                    modifier=Modifier.testTag("builder-next-week")) {
                    Text("Next ›",fontSize=12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(12.dp))
        Surface(Modifier.fillMaxWidth(),color=Color(0xFF151515),
            shape=RoundedCornerShape(14.dp),
            border=BorderStroke(1.dp,Color(0xFF2B2B2B))) {
            Column(Modifier.padding(10.dp),
                verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(
                        weekStart.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM",
                            java.util.Locale.getDefault())) + " – " +
                        weekEnd.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM",
                            java.util.Locale.getDefault())),
                        fontSize=10.sp,color=Color(0xFFC5C5C5),
                        fontWeight=FontWeight.Bold
                    )
                    val doubleDays = plan?.let { PlanScheduleValidation.isMultiPolicy(it.policyId) }
                        ?: (request.policyChoice == "multi-session" || request.allowDoubleDays)
                    Text(
                        if (doubleDays) "MAX 2 / DAY" else "CURRENT POLICY · 1 / DAY",
                        fontSize=10.sp,color=Color(0xFFC5C5C5))
                }
                Spacer(Modifier.height(7.dp))
                (0..6).forEach { offset ->
                    val date=weekStart.plusDays(offset.toLong())
                    val day=date.dayOfWeek.value
                    val assigned=rows.filter { it.date==date }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min=40.dp)
                            .background(Color(0xFF171717),RoundedCornerShape(9.dp))
                            .padding(horizontal=9.dp,vertical=5.dp),
                        verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(7.dp)
                    ) {
                        Text(date.dayOfWeek.name.take(3),fontSize=10.sp,
                            fontWeight=FontWeight.Bold,color=Color.White,
                            modifier=Modifier.width(50.dp))
                        if(plan==null) {
                            val choices=exactAssignments.filter { it.first==day }
                            Text(
                                when {
                                    choices.isNotEmpty() -> choices.joinToString(" + ") {
                                        it.third
                                    } + " · requested"
                                    day in request.days -> "Available · coach may place sessions"
                                    else -> "Unavailable"
                                },
                                fontSize=11.sp,
                                color=if(choices.isNotEmpty())Color.White
                                      else Color(0xFFC5C5C5),
                                maxLines=2,overflow=TextOverflow.Ellipsis,
                                modifier=Modifier.testTag("builder-requested-day-"+day)
                            )
                        } else if(assigned.isEmpty()) {
                            Text("Rest / no scheduled session",fontSize=11.sp,
                                color=Color(0xFFC5C5C5))
                        } else {
                            assigned.take(2).forEach { session ->
                                Surface(
                                    modifier=Modifier.weight(1f).heightIn(min=48.dp)
                                        .testTag("builder-preview-session-" + session.id),
                                    onClick={onOpenSession(session.id)},
                                    color=PlanBuilderPalette.selected,
                                    shape=RoundedCornerShape(9.dp),
                                    border=BorderStroke(1.dp,
                                        PlanBuilderPalette.accent)
                                ) {
                                    Box(contentAlignment=Alignment.Center) {
                                        Text(session.effectiveTitle,
                                            fontSize=12.sp,color=Color.White,
                                            modifier=Modifier.padding(horizontal=7.dp,vertical=8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(13.dp))
        Text(if(plan==null)
            "○ Coaching checks will run when you generate a saved draft."
            else "✓ Saved engine prescriptions · approval is a separate action",
            fontSize=11.sp,
            color=if(plan==null) Color(0xFFC5C5C5) else PlanBuilderPalette.accent)
        if (plan == null) {
            Spacer(Modifier.height(12.dp))
            Text("GENERATION ROUTE",fontSize=11.sp,
                color=PlanBuilderPalette.secondary)
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected=!experimentalSelected,
                    onClick={onExperimentalSelected(false)},
                    label={Text("Existing coach · Engine 1.5")},
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                        .semantics { selected = !experimentalSelected }
                        .testTag("builder-route-existing"),
                    shape=RoundedCornerShape(50),
                    border=BorderStroke(1.dp,if(!experimentalSelected)PlanBuilderPalette.accent else PlanBuilderPalette.border),
                    colors=FilterChipDefaults.filterChipColors(
                        containerColor=PlanBuilderPalette.surface,
                        labelColor=PlanBuilderPalette.text,
                        selectedContainerColor=PlanBuilderPalette.selected,
                        selectedLabelColor=PlanBuilderPalette.accent
                    )
                )
                FilterChip(
                    selected=experimentalSelected,
                    onClick={onExperimentalSelected(true)},
                    label={Text("Experimental research review")},
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                        .semantics { selected = experimentalSelected }
                        .testTag("builder-route-research"),
                    shape=RoundedCornerShape(50),
                    border=BorderStroke(1.dp,if(experimentalSelected)PlanBuilderPalette.accent else PlanBuilderPalette.border),
                    colors=FilterChipDefaults.filterChipColors(
                        containerColor=PlanBuilderPalette.surface,
                        labelColor=PlanBuilderPalette.text,
                        selectedContainerColor=PlanBuilderPalette.selected,
                        selectedLabelColor=PlanBuilderPalette.accent
                    )
                )
            }
            if (experimentalSelected) {
                if (request.runDays > 0) {
                    IndependentAndroidResearchPreviewPanel(
                        request=request,
                        onSaveResearchReview=onSaveResearchReview,
                        savedResearchCount=researchDraftCount,
                        savedResearchReviews=savedResearchReviews
                    )
                } else {
                    Text("The research mileage model requires running sessions. " +
                        "Use Existing coach for other disciplines.",
                        fontSize=11.sp,color=PlanBuilderPalette.secondary)
                }
            } else {
                Text("Existing Coach uses Engine 1.5 to create a normal saved draft. " +
                    "Research review is separate and can never be approved as a workout.",
                    fontSize=11.sp,color=PlanBuilderPalette.secondary)
            }
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it,color=Color(0xFFFF9DA7),fontSize=12.sp,
                modifier=Modifier.testTag("plan-form-error")
                    .semantics { liveRegion=LiveRegionMode.Polite })
        }
    }
}
