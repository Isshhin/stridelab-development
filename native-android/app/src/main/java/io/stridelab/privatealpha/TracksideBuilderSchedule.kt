package io.stridelab.privatealpha

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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

/** Decorative 38dp Plan Dates icon tile. The parent card retains its full click target and label. */
@Composable
private fun PlanDatesIconTile(icon: Int, tag: String) {
    Box(
        modifier = Modifier.size(38.dp)
            .background(Color(0xFF383838), RoundedCornerShape(10.dp))
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = PlanBuilderPalette.accent
        )
    }
}

/** Approved 720:546 Plan details, kept separate from the availability screen. */
@Composable
internal fun TracksidePlanDetailsScreen(
    start: String, weeks: String, minutes: String,
    onStart: (String) -> Unit, onWeeks: (String) -> Unit,
    onMinutes: (String) -> Unit,
    onBack: () -> Unit, onContinue: () -> Unit,
    dailyBudget: String = "90",
    multiSession: Boolean = false,
    onDailyBudget: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val panelBrush = Brush.horizontalGradient(listOf(PlanBuilderPalette.surface, PlanBuilderPalette.surfaceRaised))
    var customWeeks by rememberSaveable { mutableStateOf(false) }
    var goalDateInfo by rememberSaveable { mutableStateOf(false) }
    val date = runCatching { LocalDate.parse(start) }.getOrDefault(LocalDate.now())
    TracksideBuilderFrame(
        progressStep = 4,
        eyebrow = "PLAN / DETAILS", toolbarTitle = "Plan details",
        headline = "Set your plan dates",
        description = "Choose when to start and how long to train.",
        onBack = onBack,
        onContinue = onContinue,
        continueEnabled = weeks.toIntOrNull() in 1..16 &&
            !date.isBefore(LocalDate.now())
    ) {
        Surface(
            Modifier.fillMaxWidth(), shape=RoundedCornerShape(14.dp),
            color=Color.Transparent, border=BorderStroke(1.dp,PlanBuilderPalette.accent)
        ) {
            Column(Modifier.fillMaxWidth().background(panelBrush).padding(13.dp)) {
                TracksideFormLabel("START DATE")
                Spacer(Modifier.height(18.dp))
                Text("When should the plan begin?",fontSize=13.sp,
                    fontWeight=FontWeight.SemiBold,color=Color.White)
                Spacer(Modifier.height(18.dp))
                Surface(
                    modifier=Modifier.fillMaxWidth().heightIn(min=68.dp)
                        .testTag("builder-start-date"),
                    color=Color(0xFF383838),shape=RoundedCornerShape(11.dp),
                    border=BorderStroke(1.dp,PlanBuilderPalette.accent),
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, y, m, d -> onStart(LocalDate.of(y,m+1,d).toString()) },
                            date.year, date.monthValue-1, date.dayOfMonth
                        ).show()
                    }
                ) {
                    Row(Modifier.padding(horizontal=11.dp, vertical=10.dp),
                        verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(11.dp)) {
                        PlanDatesIconTile(R.drawable.trackside_plan_dates_calendar, "builder-date-calendar-vector")
                        Column(Modifier.weight(1f)) {
                            Text(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM",
                                Locale.getDefault())),fontSize=13.sp,
                                color=Color.White,fontWeight=FontWeight.SemiBold)
                            Spacer(Modifier.height(5.dp))
                            Text("Tap to choose a date",fontSize=10.sp,
                                color=Color(0xFFC5C5C5))
                        }
                        Text("›",color=Color.White,fontSize=20.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(17.dp))
        Surface(
            Modifier.fillMaxWidth(),color=Color.Transparent,
            shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,PlanBuilderPalette.accent)
        ) {
            Column(Modifier.fillMaxWidth().background(panelBrush).padding(13.dp)) {
                TracksideFormLabel("PLAN LENGTH")
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
                            color=if(active)PlanBuilderPalette.accent else Color(0xFF303030),
                            shape=RoundedCornerShape(10.dp),
                            border=BorderStroke(1.dp,
                                if(active)PlanBuilderPalette.accent else PlanBuilderPalette.accent)
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
                    .background(PlanBuilderPalette.accent))
                TextButton(onClick={goalDateInfo=true}) {
                    Text("Have a goal date? Use that instead  ›",
                        fontSize=11.sp,color=PlanBuilderPalette.accent)
                }
            }
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
internal fun TracksideAvailabilityScreen(
    request: PlanRequest,
    onDay: (Int) -> Unit,
    onRemovePinnedDay: (Int) -> Unit = {},
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
    val daysBrush=Brush.horizontalGradient(listOf(PlanBuilderPalette.surface, PlanBuilderPalette.surfaceRaised))
    val capacityBrush=Brush.horizontalGradient(listOf(PlanBuilderPalette.surface, PlanBuilderPalette.surfaceRaised))
    val coachingBrush=Brush.horizontalGradient(listOf(PlanBuilderPalette.surface, PlanBuilderPalette.surfaceRaised))
    val chosen=request.days.size
    val total=request.runDays+request.strengthDays+request.calDays+request.conditioningDays
    val maxPerDay=if(request.policyChoice=="multi-session") 2 else 1
    val capacity=chosen*maxPerDay
    // Check the available prescription types; the engine verifies actual spacing and time.
    val requiredDoubleDays=(total-chosen).coerceAtLeast(0)
    val easyRunCandidates=(request.runDays -
        (if(request.runDays>0) 1 else 0) - request.qualityCount).coerceAtLeast(0)
    val upperStrengthSlots = if (BuildConfig.ENGINE15_ACTIVE) request.strengthDays / 2 else 0
    val compatibleDoubleLimit=minOf(easyRunCandidates,request.calDays + upperStrengthSlots)
    val insufficientPairCapacity=requiredDoubleDays>compatibleDoubleLimit
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
        else {
            val easy = placed.any { it.second=="run" && it.third>request.qualityCount }
            val partner = placed.any { it.second=="cal" ||
                (upperStrengthSlots>0 && it.second=="str" && it.third%2==1) }
            !easy || !partner
        }
    }
    val placementError=WeeklyPlacement.validationError(request)
    val capacityFits=chosen>0 && total<=capacity &&
        placementError==null && !unsupportedOverlap &&
        !invalidPair && !insufficientPairCapacity
    TracksideBuilderFrame(
        progressStep = 5,
        eyebrow = "PLAN / AVAILABILITY",
        toolbarTitle = "Availability",
        headline="Your weekly availability",
        description="Tell us which days you can train.",
        onBack=onBack,onContinue=onContinue,
        continueEnabled=capacityFits
    ) {

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")
                .forEachIndexed { index, label ->
                    val day = index + 1
                    val available = day in request.days
                    val preferred = available && request.runDays > 0 && day == request.longDay
                    val fixedSessions = byDay[day]?.size ?: 0
                    Surface(
                        onClick = {
                            if (available && day in pinned) pendingRemovalDay = day
                            else onDay(day)
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
                            .testTag("available-day-$day")
                            .semantics {
                                stateDescription = when {
                                    available && fixedSessions > 0 ->
                                        "Available, $fixedSessions placed workouts. Tap to change."
                                    available -> "Available. Tap to make unavailable."
                                    else -> "Unavailable. Tap to make available."
                                }
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = if (preferred) PlanBuilderPalette.selected
                            else PlanBuilderPalette.surface,
                        border = BorderStroke(1.dp,
                            if (preferred) PlanBuilderPalette.accent
                            else PlanBuilderPalette.border)
                    ) {
                        Row(Modifier.fillMaxWidth()
                            .padding(horizontal = 11.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text(label, color = PlanBuilderPalette.text,
                                fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                                modifier = Modifier.width(35.dp))
                            Text("●", fontSize = 11.sp,
                                color = when {
                                    preferred -> PlanBuilderPalette.text
                                    available -> PlanBuilderPalette.accent
                                    else -> PlanBuilderPalette.disabled
                                })
                            Text(
                                when {
                                    preferred -> "Preferred · long run"
                                    available && fixedSessions > 0 ->
                                        "Available · $fixedSessions fixed"
                                    available -> "Available"
                                    else -> "Rest / unavailable"
                                },
                                fontSize = 11.sp,
                                color = PlanBuilderPalette.text,
                                modifier = Modifier.weight(1f)
                            )
                            Text("Change", fontSize = 10.sp,
                                color = PlanBuilderPalette.secondary)
                        }
                    }
                }
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                Text("● Available", fontSize = 9.sp,
                    color = PlanBuilderPalette.accent)
                Spacer(Modifier.width(12.dp))
                Text("● Preferred", fontSize = 9.sp,
                    color = PlanBuilderPalette.text)
                Spacer(Modifier.width(12.dp))
                Text("● Rest", fontSize = 9.sp,
                    color = PlanBuilderPalette.disabled)
            }
        }
        Spacer(Modifier.height(23.dp))
        Surface(Modifier.fillMaxWidth().heightIn(min=125.dp),
            shape=RoundedCornerShape(14.dp),color=Color.Transparent,
            border=BorderStroke(1.dp,PlanBuilderPalette.border)) {
            Column(Modifier.fillMaxWidth().background(capacityBrush).padding(13.dp)) {
                TracksideFormLabel("AUTOMATIC CAPACITY CHECK")
                Spacer(Modifier.height(17.dp))
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(total.toString(),fontSize=27.sp,color=Color.White,
                        fontWeight=FontWeight.Bold,
                        modifier=Modifier.testTag("builder-capacity-count"))
                    Spacer(Modifier.width(14.dp))
                    Text("sessions across $chosen days",
                        fontSize=11.sp,color=Color.White,
                        modifier=Modifier.testTag("builder-capacity-days"))
                }
                Spacer(Modifier.height(14.dp))
                Text(when {
                    placementError!=null -> placementError
                    unsupportedOverlap -> "Two workouts share a weekday. Enable compatible same-day sessions or move one."
                    invalidPair -> if(upperStrengthSlots>0)
                        "This engine allows only easy running with calisthenics or upper-body strength on the same day."
                        else "This engine allows only easy running with calisthenics on the same day."
                    insufficientPairCapacity -> "This request needs $requiredDoubleDays compatible double days, but these sessions allow at most $compatibleDoubleLimit easy-run pairs."
                    capacityFits && requiredDoubleDays>0 -> "Compatible double-day slots may fit; final recovery checks run on generation."
                    capacityFits -> "✓ Requested weekdays are recorded; recovery checks run on generation"
                    chosen == 0 -> "Select at least one available training day."
                    total > capacity -> "$total weekly sessions exceed $capacity available slots across $chosen days (" +
                        (if (request.policyChoice == "multi-session") "same-day sessions enabled" else "one session per day") + ")."
                    else -> "Capacity check is unresolved; review your weekly session assignments."
                },
                    fontSize=11.sp,color=if(capacityFits)
                        PlanBuilderPalette.accent else Color(0xFFFF9DA7),
                    modifier=Modifier.testTag("builder-capacity-status"))
                if(total>chosen || overlap || request.policyChoice=="multi-session") {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        onClick={
                            onMultiSessionToggle(request.policyChoice!="multi-session")
                        },
                        modifier=Modifier.fillMaxWidth().height(49.dp)
                            .testTag("builder-enable-multi-session")
                            .semantics {
                                stateDescription = if(request.policyChoice=="multi-session")
                                    "Same-day sessions enabled" else "Same-day sessions disabled"
                            },
                        color=if(request.policyChoice=="multi-session")
                            Color(0xFF383838) else Color(0xFF303030),
                        shape=RoundedCornerShape(8.dp),
                        border=BorderStroke(1.dp,
                            if(request.policyChoice=="multi-session")PlanBuilderPalette.accent
                            else PlanBuilderPalette.accent)
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
                    Text("Only compatible sessions can share a day; a successful slot count does not guarantee placement.",
                        fontSize=10.sp,lineHeight=13.sp,
                        color=Color(0xFFC5C5C5))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        TracksideOtherDisciplineSlots(
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
            shape=RoundedCornerShape(14.dp),color=Color.Transparent,
            border=BorderStroke(1.dp,PlanBuilderPalette.border)) {
            Column(Modifier.fillMaxWidth().background(coachingBrush).padding(13.dp)) {
                TracksideFormLabel("NEXT: COACHING CHECKS")
                Spacer(Modifier.height(23.dp))
                Text("◷  Recovery balance",fontSize=11.sp,color=Color.White)
                Spacer(Modifier.height(19.dp))
                Text("◷  Training load and progression",fontSize=11.sp,color=Color.White)
                Spacer(Modifier.height(10.dp))
                Text("Evaluated after plan generation",fontSize=10.sp,
                    color=Color(0xFFC5C5C5))
            }
        }
        // Local scroll clearance keeps the final coaching label above the
        // fixed Continue dock even with extra same-day and placement controls.
        Spacer(Modifier.fillMaxWidth().height(24.dp)
            .testTag("builder-availability-coaching-clearance"))
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
private fun TracksideFormLabel(s: String) {
    Text(s,fontSize=10.sp,fontWeight=FontWeight.Bold,
        color=Color(0xFFC5C5C5),letterSpacing=0.2f.sp)
}
