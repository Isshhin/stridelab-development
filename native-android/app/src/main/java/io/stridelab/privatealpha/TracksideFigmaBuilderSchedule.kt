package io.stridelab.privatealpha

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Approved 720:546 Plan details, kept separate from the availability screen. */
@Composable
internal fun TracksideFigmaPlanDetailsScreen(
    start: String, weeks: String, minutes: String,
    onStart: (String) -> Unit, onWeeks: (String) -> Unit,
    onMinutes: (String) -> Unit,
    onBack: () -> Unit, onContinue: () -> Unit,
    dailyBudget: String = "90",
    multiSession: Boolean = false,
    onDailyBudget: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var customWeeks by rememberSaveable { mutableStateOf(false) }
    var timeExpanded by rememberSaveable { mutableStateOf(false) }
    var goalDateInfo by rememberSaveable { mutableStateOf(false) }
    val date = runCatching { LocalDate.parse(start) }.getOrDefault(LocalDate.now())
    TracksideFigmaBuilderFrame(
        eyebrow = "PLAN / DETAILS", toolbarTitle = "Plan details",
        headline = "Set your plan dates",
        description = "Choose when to start and how long to train.",
        onBack = onBack,
        onContinue = onContinue,
        continueEnabled = weeks.toIntOrNull() in 1..16 &&
            minutes.toIntOrNull() in 15..120 &&
            (!multiSession || dailyBudget.toIntOrNull() in 15..240) &&
            !date.isBefore(LocalDate.now())
    ) {
        Surface(
            Modifier.fillMaxWidth(), shape=RoundedCornerShape(14.dp),
            color=Color(0xFF1A2F3A), border=BorderStroke(1.dp,Color(0xFF496878))
        ) {
            Column(Modifier.padding(13.dp)) {
                TracksideFigmaFormLabel("START DATE")
                Spacer(Modifier.height(18.dp))
                Text("When should the plan begin?",fontSize=13.sp,
                    fontWeight=FontWeight.SemiBold,color=Color.White)
                Spacer(Modifier.height(18.dp))
                Surface(
                    modifier=Modifier.fillMaxWidth().height(68.dp)
                        .testTag("builder-start-date"),
                    color=Color(0xFF25485E),shape=RoundedCornerShape(11.dp),
                    border=BorderStroke(1.dp,Color(0xFF698EAB)),
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, y, m, d -> onStart(LocalDate.of(y,m+1,d).toString()) },
                            date.year, date.monthValue-1, date.dayOfMonth
                        ).show()
                    }
                ) {
                    Row(Modifier.padding(horizontal=14.dp),
                        verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                        Text("▣",color=Color(0xFFB6D9E8),fontSize=22.sp)
                        Column(Modifier.weight(1f)) {
                            Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM",
                                Locale.getDefault())),fontSize=13.sp,
                                color=Color.White,fontWeight=FontWeight.SemiBold)
                            Spacer(Modifier.height(5.dp))
                            Text("Tap to choose a date",fontSize=10.sp,
                                color=Color(0xFFB6C0CA))
                        }
                        Text("›",color=Color.White,fontSize=20.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(17.dp))
        Surface(
            Modifier.fillMaxWidth(),color=Color(0xFF1A2F3A),
            shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,Color(0xFF496878))
        ) {
            Column(Modifier.padding(13.dp)) {
                TracksideFigmaFormLabel("PLAN LENGTH")
                Spacer(Modifier.height(18.dp))
                Text("How many weeks?",color=Color.White,
                    fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf("4","8","12","Custom").forEach { option ->
                        val active = if(option=="Custom") customWeeks
                            else weeks==option && !customWeeks
                        Surface(
                            onClick={
                                if(option=="Custom") customWeeks=true
                                else { customWeeks=false; onWeeks(option) }
                            },
                            modifier=Modifier.weight(1f).height(47.dp)
                                .testTag("builder-weeks-$option"),
                            color=if(active)Color(0xFF29526B)else Color(0xFF1C3646),
                            shape=RoundedCornerShape(10.dp),
                            border=BorderStroke(1.dp,
                                if(active)Color(0xFF85B7D0)else Color(0xFF527387))
                        ) { Box(contentAlignment=Alignment.Center) {
                            Text(if(option=="Custom")option else "$option weeks",
                                fontSize=10.sp,color=Color.White)
                        }}
                    }
                }
                if(customWeeks) {
                    Spacer(Modifier.height(9.dp))
                    OutlinedTextField(
                        value=weeks,onValueChange={onWeeks(it.filter(Char::isDigit).take(2))},
                        label={Text("Custom weeks (1–16)")},
                        modifier=Modifier.fillMaxWidth().testTag("builder-custom-weeks"),
                        singleLine=true
                    )
                }
                Spacer(Modifier.height(17.dp))
                Box(Modifier.fillMaxWidth().height(1.dp)
                    .background(Color(0xFF4B697A)))
                TextButton(onClick={goalDateInfo=true}) {
                    Text("Have a goal date? Use that instead  ›",
                        fontSize=11.sp,color=Color(0xFFBDDDED))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Surface(
            modifier=Modifier.fillMaxWidth()
                .testTag("builder-time-limit"),
            onClick={timeExpanded=!timeExpanded},
            color=Color(0xFF1A2F3A),shape=RoundedCornerShape(14.dp),
            border=BorderStroke(1.dp,Color(0xFF496878))
        ) {
            Column(Modifier.padding(15.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("◷",fontSize=24.sp,color=Color(0xFF9ACADB))
                    Spacer(Modifier.width(15.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Time limits",color=Color.White,fontSize=13.sp,
                            fontWeight=FontWeight.SemiBold)
                        Spacer(Modifier.height(5.dp))
                        Text("Optional · Only if your time is limited",
                            fontSize=10.sp,color=Color(0xFFB6C0CA))
                    }
                    Text("›",fontSize=21.sp,color=Color.White)
                }
            }
        }
        if(timeExpanded || multiSession) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value=minutes,onValueChange={onMinutes(it.filter(Char::isDigit).take(3))},
                label={Text("Minutes available per session (15–120)")},
                modifier=Modifier.fillMaxWidth(),singleLine=true
            )
        }
        if(multiSession) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value=dailyBudget,
                onValueChange={onDailyBudget(it.filter(Char::isDigit).take(3))},
                label={Text("Combined minutes per day (15–240)")},
                modifier=Modifier.fillMaxWidth()
                    .testTag("builder-combined-daily-minutes"),singleLine=true
            )
            Spacer(Modifier.height(6.dp))
            Text("Compatible two-a-day sessions share this daily time budget.",
                fontSize=10.sp,color=Color(0xFFB6C0CA))
        }
    }
    if(goalDateInfo) AlertDialog(
        onDismissRequest={goalDateInfo=false},
        title={Text("Goal date")},
        text={Text("A race-target date is not a field in the current coaching engine. You can set a start date and plan length above. No target date is assumed or saved.")},
        confirmButton={
            TextButton(onClick={goalDateInfo=false}) { Text("Close") }
        }
    )
}

/** Approved 614:88: availability cards and truthful pre-generation constraints. */
@Composable
internal fun TracksideFigmaAvailabilityScreen(
    request: PlanRequest,
    onDay: (Int) -> Unit,
    onRemovePinnedDay: (Int) -> Unit,
    onMultiSessionToggle: (Boolean) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    selectedOtherDiscipline: String = "str",
    selectedOtherSlot: Int = 0,
    onSelectOtherDiscipline: (String) -> Unit = {},
    onSelectOtherSlot: (Int) -> Unit = {},
    onAssignOtherDay: (String,Int,Int) -> Unit = { _,_,_ -> },
    onClearOtherSlot: (String,Int) -> Unit = { _,_ -> },
    onAutoOther: (String) -> Unit = {},
    onCountOther: (String,Int) -> Unit = { _,_ -> },
    dailyBudget: String = "90",
    onDailyBudget: (String) -> Unit = {}
) {
    var pendingRemovalDay by rememberSaveable { mutableIntStateOf(0) }
    val chosen=request.days.size
    val total=request.runDays+request.strengthDays+request.calDays+request.conditioningDays
    val maxPerDay=if(request.policyChoice=="multi-session") 2 else 1
    val capacity=chosen*maxPerDay
    val assignments=request.weeklySlots.flatMap { (discipline,slots) ->
        slots.mapIndexedNotNull { index,day ->
            if(day in 1..7) Triple(day,discipline,index) else null
        }
    }
    val pinned=assignments.map { it.first }
    val byDay=assignments.groupBy { it.first }
    val overlap=byDay.values.any { it.size>1 }
    val unsupportedOverlap=overlap && request.policyChoice!="multi-session"
    val invalidPair=byDay.values.any { placed ->
        if(placed.size<=1) false
        else if(placed.size!=2) true
        else placed.map { it.second }.toSet()!=setOf("run","cal") ||
            placed.any { it.second=="run" &&
                it.third<=request.qualityCount }
    }
    val placementError=WeeklyPlacement.validationError(request)
    val invalidBudget=request.policyChoice=="multi-session" &&
        dailyBudget.toIntOrNull() !in 15..240
    val capacityFits=chosen>0 && total<=capacity &&
        placementError==null && !unsupportedOverlap &&
        !invalidPair && !invalidBudget
    TracksideFigmaBuilderFrame(
        eyebrow = "PLAN / AVAILABILITY",
        toolbarTitle = "Availability",
        headline="Make room to train",
        description="Your available days apply across all selected sports.",
        onBack=onBack,onContinue=onContinue,
        continueEnabled=capacityFits
    ) {
        Surface(Modifier.fillMaxWidth().height(224.dp),
            shape=RoundedCornerShape(14.dp),color=Color(0xFF1B303B),
            border=BorderStroke(1.dp,Color(0xFF4F7487))) {
            Column(Modifier.padding(13.dp)) {
                TracksideFigmaFormLabel("YOUR TRAINING DAYS")
                Spacer(Modifier.height(23.dp))
                Row(verticalAlignment=Alignment.Bottom) {
                    Text(chosen.toString(),fontSize=39.sp,color=Color.White,
                        fontWeight=FontWeight.Bold)
                    Spacer(Modifier.width(18.dp))
                    Text("/ 7 DAYS",fontSize=11.sp,color=Color(0xFFB6C0CA),
                        modifier=Modifier.padding(bottom=9.dp))
                }
                Spacer(Modifier.height(25.dp))
                Row(
                    Modifier.fillMaxWidth().height(51.dp),
                    horizontalArrangement=Arrangement.spacedBy(4.dp)
                ) {
                    listOf("MON","TUE","WED","THU","FRI","SAT","SUN").forEachIndexed {
                        index,label ->
                        val day=index+1
                        val selected=day in request.days
                        Surface(
                            onClick={
                                if(selected && day in pinned) pendingRemovalDay=day
                                else onDay(day)
                            },
                            modifier=Modifier.weight(1f)
                                .fillMaxHeight().testTag("available-day-$day")
                                .semantics {
                                    stateDescription = when {
                                        selected && day in pinned ->
                                            "Available, ${byDay[day]?.size ?: 1} placed workouts. Tap to change."
                                        selected -> "Available. Tap to make unavailable."
                                        else -> "Unavailable. Tap to make available."
                                    }
                                },
                            color=if(selected)Color(0xFF202B34) else Color(0xFF18252F),
                            shape=RoundedCornerShape(9.dp),
                            border=BorderStroke(1.dp,
                                if(selected)Color(0xFFCCD7DD) else Color(0xFF435966))
                        ) {
                            Column(horizontalAlignment=Alignment.CenterHorizontally,
                                verticalArrangement=Arrangement.Center) {
                                Text(label,fontSize=9.sp,color=if(selected) Color.White
                                    else Color(0xFF8495A1),maxLines=1)
                                if(!selected) Text("–",fontSize=12.sp,color=Color(0xFF8495A1))
                                else if(day in pinned) Text("•",fontSize=12.sp,color=Color(0xFF91C8EA))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(23.dp))
        Surface(Modifier.fillMaxWidth().heightIn(min=125.dp),
            shape=RoundedCornerShape(14.dp),color=Color(0xFF112A33),
            border=BorderStroke(1.dp,Color(0xFF3A6A76))) {
            Column(Modifier.padding(13.dp)) {
                TracksideFigmaFormLabel("AUTOMATIC CAPACITY CHECK")
                Spacer(Modifier.height(17.dp))
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(total.toString(),fontSize=27.sp,color=Color.White,
                        fontWeight=FontWeight.Bold)
                    Spacer(Modifier.width(14.dp))
                    Text("sessions across $chosen days",
                        fontSize=11.sp,color=Color.White)
                }
                Spacer(Modifier.height(14.dp))
                Text(when {
                    placementError!=null -> placementError
                    unsupportedOverlap -> "Two workouts share a weekday. Enable compatible same-day sessions or move one."
                    invalidPair -> "This engine allows only easy running with calisthenics on the same day."
                    invalidBudget -> "Enter a combined daily time budget between 15 and 240 minutes."
                    capacityFits -> "✓ Requested weekdays are recorded; recovery checks run on generation"
                    else -> "Additional available day capacity is needed before continuing"
                },
                    fontSize=11.sp,color=if(capacityFits)
                        Color(0xFF69D5B0) else Color(0xFFFF8D8D))
                if(total>chosen || overlap || request.policyChoice=="multi-session") {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        onClick={
                            onMultiSessionToggle(request.policyChoice!="multi-session")
                        },
                        modifier=Modifier.fillMaxWidth().height(49.dp)
                            .testTag("builder-enable-multi-session"),
                        color=if(request.policyChoice=="multi-session")
                            Color(0xFF21485C) else Color(0xFF1A313D),
                        shape=RoundedCornerShape(8.dp),
                        border=BorderStroke(1.dp,
                            if(request.policyChoice=="multi-session")Color(0xFF84AFC1)
                            else Color(0xFF456C7E))
                    ) {
                        Row(Modifier.padding(horizontal=10.dp),
                            verticalAlignment=Alignment.CenterVertically) {
                            Text(if(request.policyChoice=="multi-session")"✓" else "+",
                                color=Color.White,fontSize=15.sp)
                            Spacer(Modifier.width(9.dp))
                            Text("Allow compatible same-day sessions",
                                color=Color.White,fontSize=11.sp)
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text("Engine 1 only permits selected pairings; a successful slot count does not guarantee placement.",
                        fontSize=10.sp,lineHeight=13.sp,
                        color=Color(0xFFB6C0CA))
                    if(request.policyChoice=="multi-session") {
                        Spacer(Modifier.height(11.dp))
                        OutlinedTextField(
                            value=dailyBudget,
                            onValueChange={onDailyBudget(it.filter(Char::isDigit).take(3))},
                            label={Text("Combined minutes per training day (15–240)")},
                            modifier=Modifier.fillMaxWidth()
                                .testTag("builder-availability-daily-budget"),
                            singleLine=true
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        TracksideFigmaOtherDisciplineSlots(
            request=request,
            selectedDiscipline=selectedOtherDiscipline,
            selectedSlot=selectedOtherSlot,
            onSelectDiscipline=onSelectOtherDiscipline,
            onSelectSlot=onSelectOtherSlot,
            onAssignDay=onAssignOtherDay,
            onClearSlot=onClearOtherSlot,
            onAuto=onAutoOther,
            onCount=onCountOther
        )
        Spacer(Modifier.height(18.dp))
        Surface(Modifier.fillMaxWidth().heightIn(min=131.dp),
            shape=RoundedCornerShape(14.dp),color=Color(0xFF132934),
            border=BorderStroke(1.dp,Color(0xFF3E6474))) {
            Column(Modifier.padding(13.dp)) {
                TracksideFigmaFormLabel("NEXT: COACHING CHECKS")
                Spacer(Modifier.height(23.dp))
                Text("◷  Recovery balance",fontSize=11.sp,color=Color.White)
                Spacer(Modifier.height(19.dp))
                Text("◷  Training load and progression",fontSize=11.sp,color=Color.White)
                Spacer(Modifier.height(10.dp))
                Text("Evaluated after plan generation",fontSize=10.sp,
                    color=Color(0xFFB6C0CA))
            }
        }
    }
    if(pendingRemovalDay in 1..7) {
        val day=pendingRemovalDay
        val count=byDay[day]?.size ?: 0
        val label=listOf("Monday","Tuesday","Wednesday","Thursday",
            "Friday","Saturday","Sunday")[day-1]
        AlertDialog(
            onDismissRequest={pendingRemovalDay=0},
            title={Text("Change $label availability?")},
            text={Text("$count manually placed workout(s) use $label. Making this day unavailable will leave those sessions unassigned. You can move them to another available day before generating your plan.")},
            confirmButton={
                TextButton(
                    onClick={
                        onRemovePinnedDay(day)
                        pendingRemovalDay=0
                    },
                    modifier=Modifier.testTag("builder-confirm-remove-pinned-day")
                ) { Text("Make unavailable") }
            },
            dismissButton={
                TextButton(onClick={pendingRemovalDay=0}) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun TracksideFigmaFormLabel(s: String) {
    Text(s,fontSize=10.sp,fontWeight=FontWeight.Bold,
        color=Color(0xFFB6C0CA),letterSpacing=0.2f.sp)
}
