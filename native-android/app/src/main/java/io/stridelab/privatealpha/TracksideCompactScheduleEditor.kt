package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek

/**
 * Exact pre-generation capacity checks from the retired standalone
 * availability screen. Engine validation still performs the final schedule
 * and recovery checks when a draft is generated.
 */
internal fun compactScheduleError(request: PlanRequest): String? {
    val chosen=request.days.size
    val total=request.runDays+request.strengthDays+request.calDays+request.conditioningDays
    val capacity=chosen * if(request.policyChoice=="multi-session") 2 else 1
    val requiredDoubleDays=(total-chosen).coerceAtLeast(0)
    val easyRunCandidates=(request.runDays-
        (if(request.runDays>0) 1 else 0)-request.qualityCount).coerceAtLeast(0)
    val upperStrengthSlots=if(BuildConfig.ENGINE15_ACTIVE) request.strengthDays/2 else 0
    val compatibleDoubleLimit=minOf(easyRunCandidates,request.calDays+upperStrengthSlots)
    val placements=request.weeklySlots.flatMap { (discipline,slots) ->
        slots.mapIndexedNotNull { index,day ->
            if(day in 1..7) Triple(day,discipline,index) else null
        }
    }
    val byDay=placements.groupBy { it.first }
    val overlap=byDay.values.any { it.size>1 }
    val invalidPair=byDay.values.any { day ->
        if(day.size<=1) false
        else if(day.size!=2) true
        else {
            val easy=day.any { it.second=="run" && it.third>request.qualityCount }
            val partner=day.any {
                it.second=="cal" ||
                    (upperStrengthSlots>0 && it.second=="str" && it.third%2==1)
            }
            !easy || !partner
        }
    }
    return when {
        WeeklyPlacement.validationError(request)!=null ->
            WeeklyPlacement.validationError(request)
        chosen==0 -> "Select at least one available day."
        overlap && request.policyChoice!="multi-session" ->
            "Two workouts share a weekday. Move one or enable compatible same-day training."
        invalidPair -> "Only compatible easy-run and calisthenics or upper-strength pairs can share a day."
        requiredDoubleDays>compatibleDoubleLimit ->
            "This schedule requires more compatible double days than the selected sessions allow."
        total>capacity -> "There are $total sessions but only $capacity available slots."
        else -> null
    }
}

/** Inline availability controls; no separate Weekly Availability step. */
@Composable
internal fun TracksideCompactScheduleEditor(
    request:PlanRequest,
    onDay:(Int)->Unit,
    onRemovePinnedDay:(Int)->Unit,
    onMultiSessionToggle:(Boolean)->Unit,
    selectedOtherDiscipline:String,
    selectedOtherSlot:Int,
    onSelectOtherDiscipline:(String)->Unit,
    onSelectOtherSlot:(Int)->Unit,
    onAssignOtherDay:(String,Int,Int)->Unit,
    onClearOtherSlot:(String,Int)->Unit,
    onAutoOther:(String)->Unit,
    onCountOther:(String,Int)->Unit,
    researchContent:(@Composable ()->Unit)?=null
) {
    var advanced by rememberSaveable { mutableStateOf(false) }
    var research by rememberSaveable { mutableStateOf(false) }
    var pendingRemoval by rememberSaveable { mutableIntStateOf(0) }
    val error=compactScheduleError(request)
    val total=request.runDays+request.strengthDays+request.calDays+request.conditioningDays
    val assignments=request.weeklySlots.values.flatten()
    val byDay=assignments.filter { it in 1..7 }.groupingBy { it }.eachCount()
    val runSlots=WeeklyPlacement.days(request,"run")
    val muted=Color(0xFFAEB9AA)
    val surface=Color(0xFF1B211D)
    val border=Color(0xFF343D34)
    val accent=PlanBuilderPalette.accent
    Spacer(Modifier.height(16.dp))
    Surface(Modifier.fillMaxWidth().testTag("builder-inline-schedule"),
        shape=RoundedCornerShape(15.dp),color=surface,
        border=BorderStroke(1.dp,border)) {
        Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(11.dp)) {
            Text("WEEKLY AVAILABILITY",color=muted,fontWeight=FontWeight.Bold,fontSize=10.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                (1..7).forEach { day ->
                    val available=day in request.days
                    val slot=runSlots.indexOf(day)
                    val code=when {
                        !available -> "–"
                        slot==0 && request.runDays>0 -> "L"
                        slot>0 && slot<=request.qualityCount -> "Q"
                        slot>0 -> "E"
                        else -> if((byDay[day] ?: 0)>0) (byDay[day] ?: 0).toString() else "·"
                    }
                    Column(Modifier.width(47.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(DayOfWeek.of(day).name.take(3),color=muted,fontSize=10.sp)
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            onClick={
                                if(available && day in assignments) pendingRemoval=day
                                else onDay(day)
                            },
                            modifier=Modifier.size(47.dp)
                                .testTag("available-day-$day")
                                .semantics {
                                    stateDescription=if(available) "Available, $code" else "Unavailable"
                                },
                            shape=RoundedCornerShape(9.dp),
                            color=if(available && code!="·") Color(0xFF2D3A27)
                                else if(available)Color(0xFF252D25) else Color(0xFF242624),
                            border=BorderStroke(1.dp,if(available && code!="·")accent else border)
                        ) {
                            Box(contentAlignment=Alignment.Center) {
                                Text(code,color=if(available)accent else muted,fontWeight=FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Text("E Easy  ·  Q Quality  ·  L Long  ·  – Unavailable",
                color=muted,fontSize=10.sp)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(total.toString(),color=Color.White,fontSize=23.sp,
                    fontWeight=FontWeight.Bold,
                    modifier=Modifier.padding(end=11.dp)
                        .testTag("builder-capacity-count"))
                Column(Modifier.weight(1f)) {
                    Text("Schedule check",color=Color.White,fontWeight=FontWeight.SemiBold)
                    Text("sessions across ${request.days.size} days",
                        color=muted,fontSize=11.sp,
                        modifier=Modifier.testTag("builder-capacity-days"))
                }
                Text(if(error==null)"Valid" else "Review",fontWeight=FontWeight.Bold,
                    color=if(error==null)accent else Color(0xFFFFA1A6),
                    modifier=Modifier.testTag("builder-capacity-status"))
            }
            if(error!=null) Text(error,color=Color(0xFFFFA1A6),fontSize=11.sp)
            Text("Final recovery and spacing checks run during generation.",
                color=muted,fontSize=10.sp)
            TextButton(onClick={advanced=!advanced},
                modifier=Modifier.fillMaxWidth().testTag("builder-advanced-scheduling")) {
                Text(if(advanced)"Hide advanced scheduling" else "Advanced scheduling",
                    color=accent,fontWeight=FontWeight.SemiBold)
            }
            if(advanced) {
                Surface(onClick={onMultiSessionToggle(request.policyChoice!="multi-session")},
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                        .testTag("builder-enable-multi-session")
                        .semantics {
                            stateDescription=if(request.policyChoice=="multi-session")
                                "Same-day sessions enabled"
                            else "Same-day sessions disabled"
                        },
                    color=Color(0xFF292F29),shape=RoundedCornerShape(10.dp),
                    border=BorderStroke(1.dp,border)) {
                    Row(Modifier.padding(12.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        Text("Compatible same-day sessions",color=Color.White,
                            modifier=Modifier.weight(1f),fontSize=12.sp)
                        Text(if(request.policyChoice=="multi-session")"On" else "Off",
                            color=accent,fontWeight=FontWeight.Bold)
                    }
                }
                Text("Only compatible sessions can share a day; final recovery checks apply.",
                    fontSize=10.sp,color=muted)
                TracksideOtherDisciplineSlots(request,selectedOtherDiscipline,
                    selectedOtherSlot,onSelectOtherDiscipline,onSelectOtherSlot,
                    onAssignOtherDay,onClearOtherSlot,onAutoOther,onCountOther)
                if(researchContent!=null) {
                    TextButton(onClick={research=!research},
                        modifier=Modifier.testTag("builder-availability-research-toggle")) {
                        Text(if(research)"Hide research preview" else "Research preview (read-only)",
                            color=accent)
                    }
                    if(research) researchContent()
                }
            }
        }
    }
    if(pendingRemoval in 1..7) {
        val day=pendingRemoval
        AlertDialog(
            onDismissRequest={pendingRemoval=0},
            title={Text("Change ${DayOfWeek.of(day).name.lowercase().replaceFirstChar { it.uppercase() }}?")},
            text={Text("Placed sessions on this day will become unassigned. Move or reassign them before generating.")},
            confirmButton={
                TextButton(onClick={onRemovePinnedDay(day);pendingRemoval=0},
                    modifier=Modifier.testTag("builder-confirm-remove-pinned-day")) {
                    Text("Make unavailable")
                }
            },
            dismissButton={TextButton(onClick={pendingRemoval=0}) { Text("Cancel") }}
        )
    }
}
