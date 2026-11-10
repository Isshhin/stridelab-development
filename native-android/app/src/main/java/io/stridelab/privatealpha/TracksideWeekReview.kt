package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale

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
    error: String? = null
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

    TracksideBuilderFrame(
        progressStep = if (plan == null) 6 else 7,
        toolbarTitle = if (plan == null) "Training blueprint" else "Review plan",
        eyebrow = "PLAN / WEEK PREVIEW",
        headline = if (plan == null) "Your training blueprint" else "Review your training plan",
        description = if (plan == null) "Review your setup before StrideLab creates your plan."
            else "Your personalized plan. Review before finalizing.",
        onBack = onBack,
        ctaLabel = if(plan == null)"Generate My Plan" else "Finalize Plan",
        onContinue = onContinue,
        continueEnabled = !busy, descriptionBottomGap = 16,
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

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            val trainingDays = days.coerceIn(0,7)
            val targetDistance = if (request.runDays > 0) {
                String.format(Locale.getDefault(), "%.0f", request.weeklyKm)
            } else "—"
            listOf(
                targetDistance to if (request.runDays > 0) "km target" else "no running",
                total.toString() to "sessions",
                trainingDays.toString() to if (plan == null) "AVAILABLE DAYS" else "ACTIVE DAYS",
                (7-trainingDays).toString() to "rest days"
            ).forEach { (value, caption) ->
                Surface(Modifier.weight(1f).heightIn(min = 62.dp),
                    color = PlanBuilderPalette.surface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PlanBuilderPalette.border)) {
                    Column(Modifier.padding(horizontal = 5.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(value, color = PlanBuilderPalette.text,
                            fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text(caption, color = PlanBuilderPalette.secondary,
                            fontSize = 9.sp, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
            }
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
            Row(Modifier.fillMaxWidth().height(36.dp),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick={onWeekChange(weekIndex-1)},
                    enabled=weekIndex>0,modifier=Modifier.testTag("builder-previous-week")) {
                    Text("‹ Previous",color=Color.White,fontSize=11.sp)
                }
                Text("Week ${weekIndex+1} of ${request.weeks}",fontSize=11.sp,
                    color=Color(0xFFC5C5C5))
                TextButton(onClick={onWeekChange(weekIndex+1)},
                    enabled=weekIndex<request.weeks-1,
                    modifier=Modifier.testTag("builder-next-week")) {
                    Text("Next ›",color=Color.White,fontSize=11.sp)
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
                                    modifier=Modifier.weight(1f).heightIn(min=31.dp)
                                        .testTag("builder-preview-session-" + session.id),
                                    onClick={onOpenSession(session.id)},
                                    color=PlanBuilderPalette.selected,
                                    shape=RoundedCornerShape(9.dp),
                                    border=BorderStroke(1.dp,
                                        PlanBuilderPalette.accent)
                                ) {
                                    Box(contentAlignment=Alignment.Center) {
                                        Text(session.effectiveTitle,
                                            fontSize=10.sp,color=Color.White,
                                            maxLines=1,overflow=TextOverflow.Ellipsis,
                                            modifier=Modifier.padding(horizontal=5.dp))
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
            color=if(plan==null) Color(0xFFC5C5C5) else Color(0xFFDBF351))
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it,color=Color(0xFFFF9DA7),fontSize=12.sp,
                modifier=Modifier.testTag("plan-form-error"))
        }
    }
}
