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
internal fun TracksideFigmaWeekReview(
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

    TracksideFigmaBuilderFrame(
        toolbarTitle = "Week Preview",
        eyebrow = "PLAN / WEEK PREVIEW",
        headline = "Your training week",
        description = if (plan == null) "Review availability before generating."
            else "Review before activating",
        onBack = onBack,
        ctaLabel = if(plan == null)"Generate my plan" else "Approve plan",
        onContinue = onContinue,
        continueEnabled = !busy,
        footer = {
            Column(Modifier.padding(horizontal=20.dp,vertical=9.dp)) {
                Text(
                    if(plan == null && exactAssignments.isNotEmpty())
                        "Chosen weekdays are requests, not generated prescriptions. Conflicts will be reported."
                    else if(plan == null)
                        "Unassigned sessions will be placed by the coaching engine."
                    else "Confirm only after reviewing the saved prescriptions.",
                    color=Color(0xFFB6C0CA),fontSize=11.sp,
                    modifier=Modifier.fillMaxWidth()
                )
            }
        }
    ) {
        if(plan != null) {
            Row(Modifier.fillMaxWidth().height(36.dp),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick={onWeekChange(weekIndex-1)},
                    enabled=weekIndex>0,modifier=Modifier.testTag("builder-previous-week")) {
                    Text("‹ Previous",color=Color.White,fontSize=11.sp)
                }
                Text("Week ${weekIndex+1} of ${request.weeks}",fontSize=11.sp,
                    color=Color(0xFFB6C0CA))
                TextButton(onClick={onWeekChange(weekIndex+1)},
                    enabled=weekIndex<request.weeks-1,
                    modifier=Modifier.testTag("builder-next-week")) {
                    Text("Next ›",color=Color.White,fontSize=11.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),
            color=Color(0xFF192E39),
            border=BorderStroke(1.dp,Color(0xFF4F6E7E))) {
            Column(Modifier.padding(14.dp)) {
                Text("THIS WEEK",fontSize=10.sp,color=Color(0xFFB6C0CA),
                    fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(total.toString(),fontSize=39.sp,fontWeight=FontWeight.Bold,
                        color=Color.White)
                    Spacer(Modifier.width(16.dp))
                    Text("SESSIONS",fontSize=10.sp,color=Color(0xFFB6C0CA))
                    Spacer(Modifier.weight(1f))
                    Text(days.toString(),fontSize=39.sp,fontWeight=FontWeight.Bold,
                        color=Color.White)
                    Spacer(Modifier.width(12.dp))
                    Text(if(plan == null) "AVAILABLE DAYS" else "ACTIVE DAYS",
                        fontSize=10.sp,color=Color(0xFFB6C0CA))
                }
                Spacer(Modifier.height(15.dp))
                Row(Modifier.fillMaxWidth().height(9.dp),
                    horizontalArrangement=Arrangement.spacedBy(1.dp)) {
                    counts.filter { it.second > 0 }.forEach { (d,n) ->
                        Box(Modifier.weight(n.toFloat()).fillMaxHeight()
                            .background(tracksideDisciplineColor(d),
                                RoundedCornerShape(4.dp)))
                    }
                }
                Spacer(Modifier.height(13.dp))
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    counts.filter { it.second > 0 }.forEach { (d,n) ->
                        Surface(
                            Modifier.weight(1f).height(48.dp),
                            color=Color(0xFF1E3441),shape=RoundedCornerShape(9.dp),
                            border=BorderStroke(1.dp,Color(0xFF537080))
                        ) { Box(contentAlignment=Alignment.Center) {
                            Text("$n " + when(d) {
                                "run" -> "Running"
                                "str" -> "Strength"
                                "cal" -> "Cali"
                                else -> "Conditioning"
                            },fontSize=10.sp,color=Color.White,maxLines=1,
                                overflow=TextOverflow.Ellipsis)
                        }}
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Surface(Modifier.fillMaxWidth(),color=Color(0xFF152C38),
            shape=RoundedCornerShape(14.dp),
            border=BorderStroke(1.dp,Color(0xFF486D7F))) {
            Column(Modifier.padding(10.dp),
                verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(
                        weekStart.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM",
                            java.util.Locale.getDefault())) + " – " +
                        weekEnd.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM",
                            java.util.Locale.getDefault())),
                        fontSize=10.sp,color=Color(0xFFB6C0CA),
                        fontWeight=FontWeight.Bold
                    )
                    val doubleDays = plan?.let { PlanScheduleValidation.isMultiPolicy(it.policyId) }
                        ?: (request.policyChoice == "multi-session" || request.allowDoubleDays)
                    Text(
                        if (doubleDays) "MAX 2 / DAY" else "CURRENT POLICY · 1 / DAY",
                        fontSize=10.sp,color=Color(0xFFB6C0CA))
                }
                Spacer(Modifier.height(7.dp))
                (0..6).forEach { offset ->
                    val date=weekStart.plusDays(offset.toLong())
                    val day=date.dayOfWeek.value
                    val assigned=rows.filter { it.date==date }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min=40.dp)
                            .background(Color(0xFF1C3441),RoundedCornerShape(9.dp))
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
                                      else Color(0xFFB6C0CA),
                                maxLines=2,overflow=TextOverflow.Ellipsis,
                                modifier=Modifier.testTag("builder-requested-day-"+day)
                            )
                        } else if(assigned.isEmpty()) {
                            Text("Rest / no scheduled session",fontSize=11.sp,
                                color=Color(0xFFB6C0CA))
                        } else {
                            assigned.take(2).forEach { session ->
                                Surface(
                                    modifier=Modifier.weight(1f).heightIn(min=31.dp)
                                        .testTag("builder-preview-session-" + session.id),
                                    onClick={onOpenSession(session.id)},
                                    color=tracksideDisciplineColor(
                                        session.discipline).copy(alpha=0.53f),
                                    shape=RoundedCornerShape(9.dp),
                                    border=BorderStroke(1.dp,
                                        tracksideDisciplineColor(session.discipline))
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
            color=if(plan==null) Color(0xFFB6C0CA) else Color(0xFF69D3B0))
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it,color=Color(0xFFFFA8AE),fontSize=12.sp,
                modifier=Modifier.testTag("plan-form-error"))
        }
    }
}
