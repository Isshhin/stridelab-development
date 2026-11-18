package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek

private val weekPanel = Color(0xFF242424)
private val weekOutline = Color(0xFF383838)
private val weekMuted = Color(0xFFC5C5C5)
private val weekWhite = Color(0xFFFFFFFF)

/**
 * Running-week baseline: approved Figma 548:95 (412dp) / 553:107 (360dp).
 *
 * The compact seven-day summary opens a focused assignment sheet on day taps.
 * The existing manual weekday, session-count and quality rules remain authoritative;
 * advanced controls are available separately without changing the generation contract.
 */
@Composable
internal fun TracksideRunningSetup(
    request: PlanRequest,
    selectedSlot: Int,
    onSelectSlot: (Int) -> Unit,
    custom: Boolean,
    onCustom: (Boolean) -> Unit,
    qualityPreference: String,
    onQualityPreference: (String) -> Unit,
    onQualityCount: (Int) -> Unit,
    onRunningCount: (Int) -> Unit,
    onAssignDay: (Int) -> Unit,
    onAssignSlot: (Int, Int) -> Unit,
    onClearDay: (Int) -> Unit,
    onClearSlot: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val count = request.runDays.coerceIn(1,6)
    val slots = WeeklyPlacement.days(request,"run")
    val selected = selectedSlot.coerceIn(0,count-1)
    val assigned = slots.count { it in 1..7 }
    val complete = assigned == count && slots.distinct().size == count &&
        slots.all { it in request.days }
    val pinned = request.weeklySlots["run"]
    var editorOpen by remember { mutableStateOf(false) }
    var editingDay by remember { mutableStateOf<Int?>(null) }

    TracksideBuilderFrame(
        progressStep = 3,
        toolbarTitle="Running Setup",
        eyebrow="RUNNING / WEEKLY SCHEDULE",
        headline="Your running week",
        description="Suggested from your goals and recent training. Tap a day to assign its session.",
        descriptionBottomGap=15,
        descriptionFontSize=11f,
        contentTopPadding=16,
        onBack=onBack,
        onContinue=onContinue,
        continueEnabled=!custom || complete
    ) {
        Surface(
            modifier=Modifier.fillMaxWidth().heightIn(min=170.dp)
                .testTag("builder-running-week-card"),
            color=weekPanel,
            shape=RoundedCornerShape(13.dp),
            border=BorderStroke(1.dp,weekOutline)
        ) {
            Column(Modifier.padding(start=11.dp,end=11.dp,top=7.dp,bottom=10.dp)) {
                Row(
                    Modifier.fillMaxWidth().height(44.dp),
                    verticalAlignment=Alignment.CenterVertically
                ) {
                    Text("Weekly runs",modifier=Modifier.weight(1f),
                        color=weekWhite,fontSize=14.sp,fontWeight=FontWeight.Bold)
                    Surface(
                        onClick={onRunningCount((count-1).coerceAtLeast(1))},
                        enabled=count>1,
                        modifier=Modifier.size(44.dp).testTag("builder-run-minus"),
                        color=Color(0xFF303030),shape=RoundedCornerShape(10.dp),
                        border=BorderStroke(1.dp,weekOutline)
                    ) { Box(contentAlignment=Alignment.Center) {
                        Text("−",color=weekWhite,fontSize=23.sp,fontWeight=FontWeight.Bold)
                    }}
                    Box(Modifier.width(55.dp),contentAlignment=Alignment.Center) {
                        Text(count.toString(),color=weekWhite,fontSize=21.sp,
                            fontWeight=FontWeight.Bold)
                    }
                    Surface(
                        onClick={onRunningCount((count+1).coerceAtMost(6))},
                        enabled=count<6,
                        modifier=Modifier.size(44.dp).testTag("builder-run-plus"),
                        color=Color(0xFF303030),shape=RoundedCornerShape(10.dp),
                        border=BorderStroke(1.dp,weekOutline)
                    ) { Box(contentAlignment=Alignment.Center) {
                        Text("+",color=weekWhite,fontSize=23.sp,fontWeight=FontWeight.Bold)
                    }}
                }
                Spacer(Modifier.height(12.dp))
                // At 360dp the cells scroll horizontally instead of shrinking
                // to unreachable < 48dp targets.
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .testTag("builder-running-day-row"),
                    horizontalArrangement=Arrangement.spacedBy(4.dp)
                ) {
                    (1..7).forEach { day ->
                        val slot=slots.indexOf(day)
                        val isPinned=pinned?.contains(day)==true
                        val available=day in request.days
                        val code=when {
                            slot==0 -> "L"
                            slot>0 && slot<=request.qualityCount -> "Q"
                            slot>0 -> "E"
                            !available -> "−"
                            else -> ""
                        }
                        val cellBg=when(code) {
                            "L" -> Color(0xFF2D3A27)
                            "Q" -> Color(0xFF2D3A27)
                            "E" -> Color(0xFF222922)
                            "−" -> Color(0xFF242424)
                            else -> Color(0xFF171717)
                        }
                        val cellBorder=when(code) {
                            "L" -> PlanBuilderPalette.accent
                            "Q" -> PlanBuilderPalette.accent
                            "E" -> PlanBuilderPalette.accent
                            "−" -> Color(0xFF383838)
                            else -> Color(0xFF383838)
                        }
                        val dayLabel=DayOfWeek.of(day).name.lowercase()
                            .replaceFirstChar { it.uppercase() }
                        val sessionLabel=when(code) {
                            "L" -> "Long run"
                            "Q" -> "Quality run"
                            "E" -> "Easy run"
                            "−" -> "Unavailable"
                            else -> "Unassigned"
                        }
                        Column(Modifier.width(48.dp),
                            horizontalAlignment=Alignment.CenterHorizontally) {
                            Text(DayOfWeek.of(day).name.take(3),
                                fontSize=9.sp,fontWeight=FontWeight.Bold,
                                color=weekMuted)
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                onClick={ editingDay=day },
                                modifier=Modifier.size(48.dp)
                                    .testTag("builder-running-day-"+day)
                                    .semantics {
                                        contentDescription="$dayLabel: $sessionLabel. Tap to edit"
                                    },
                                color=cellBg,
                                shape=RoundedCornerShape(9.dp),
                                border=BorderStroke(1.dp,cellBorder)
                            ) {
                                Box(contentAlignment=Alignment.Center) {
                                    if(code.isNotBlank()) Text(code,
                                        color=if(code=="−") weekMuted
                                            else PlanBuilderPalette.accent,
                                        fontSize=15.sp,fontWeight=FontWeight.Bold)
                                    if(isPinned) Text("◆",
                                        modifier=Modifier.align(Alignment.TopEnd)
                                            .padding(end=3.dp,top=1.dp),
                                        color=PlanBuilderPalette.accent,fontSize=8.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(assigned.toString()+" of "+count+" placed · "+
                        request.qualityCount.toString()+" quality · 1 long run",
                    color=weekMuted,fontSize=11.5f.sp,
                    modifier=Modifier.testTag("builder-running-assignment-progress"))
            }
        }

        Row(Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.SpaceBetween) {
            Text("E Easy  ·  Q Quality  ·  L Long",
                color=weekMuted,fontSize=10.sp,
                modifier=Modifier.testTag("builder-running-legend"))
        }
        Text(if(pinned!=null) "Your selected training days are pinned."
            else "Tap a day to choose its session.",
            color=weekMuted,fontSize=11.sp,
            modifier=Modifier.testTag("builder-running-pinned-note"))
        TextButton(
            onClick={ editorOpen=!editorOpen },
            modifier=Modifier.fillMaxWidth()
                .testTag("builder-running-advanced-toggle")
        ) {
            Text(if(editorOpen) "Hide advanced setup" else "Advanced weekly setup",
                color=PlanBuilderPalette.accent,fontSize=12.sp,fontWeight=FontWeight.Bold)
        }
        Spacer(Modifier.height(3.dp))

        // Advanced settings remain available for changing the quality count,
        // preferred style, automatic scheduling, and exact session ordinals.
        if(editorOpen) {
            Spacer(Modifier.height(16.dp))
            Text("EDIT YOUR RUNNING WEEK",fontSize=11.sp,
                color=weekMuted,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(true to "Choose my days", false to "Coach decides").forEach { (mode,label) ->
                    Surface(
                        onClick={
                            onCustom(mode)
                            if(!mode) editorOpen=false
                        },
                        modifier=Modifier.weight(1f).heightIn(min=48.dp)
                            .testTag(if(mode)"builder-running-custom"
                                else "builder-running-decide"),
                        color=if(custom==mode)Color(0xFF1D240B)
                            else Color(0xFF303030),
                        shape=RoundedCornerShape(10.dp),
                        border=BorderStroke(1.dp,
                            if(custom==mode)PlanBuilderPalette.accent
                            else PlanBuilderPalette.accent)
                    ) { Box(contentAlignment=Alignment.Center) {
                        Text(label,fontSize=12.sp,color=weekWhite,
                            fontWeight=FontWeight.Bold)
                    }}
                }
            }
            Spacer(Modifier.height(13.dp))
            if(custom) {
                Text("SELECT A WORKOUT, THEN TAP ITS DAY",
                    fontSize=11.sp,color=weekMuted,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(9.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    (0 until count).forEach { i ->
                        val active=i==selected
                        val weekday=slots.getOrNull(i) ?: 0
                        Surface(
                            onClick={onSelectSlot(i)},
                            modifier=Modifier.widthIn(min=100.dp).heightIn(min=56.dp)
                                .testTag("builder-run-session-"+i),
                            color=if(active)Color(0xFF1D240B)
                                else Color(0xFF303030),
                            shape=RoundedCornerShape(10.dp),
                            border=BorderStroke(if(active)2.dp else 1.dp,
                                if(active)PlanBuilderPalette.accent else weekOutline)
                        ) {
                            Column(Modifier.padding(horizontal=11.dp,vertical=7.dp),
                                verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                Text(WeeklyPlacement.label(request,"run",i),
                                    fontSize=11.sp,color=weekWhite,
                                    fontWeight=FontWeight.Bold)
                                Text(if(weekday==0)"Unassigned"
                                    else DayOfWeek.of(weekday).name.take(3),
                                    fontSize=10.sp,color=weekMuted)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(9.dp))
                Text("Selected: "+WeeklyPlacement.label(request,"run",selected)+
                    ". Tap a weekday below to assign or swap it.",
                    fontSize=11.sp,lineHeight=15.sp,color=weekMuted)
                Spacer(Modifier.height(10.dp))
                Text("ASSIGN TO WEEKDAY",color=weekMuted,fontSize=11.sp,
                    fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Row(Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .testTag("builder-run-editor-weekday-row"),
                    horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    (1..7).forEach { day ->
                        val active=slots.getOrNull(selected)==day
                        Surface(
                            onClick={onAssignDay(day)},
                            modifier=Modifier.size(46.dp)
                                .testTag("builder-run-editor-day-"+day),
                            color=if(active) PlanBuilderPalette.selected else PlanBuilderPalette.surface,
                            shape=RoundedCornerShape(9.dp),
                            border=BorderStroke(1.dp,
                                if(active)PlanBuilderPalette.accent else weekOutline)
                        ) { Box(contentAlignment=Alignment.Center) {
                            Text(DayOfWeek.of(day).name.take(3),fontSize=10.sp,
                                color=weekWhite,fontWeight=FontWeight.Bold)
                        }}
                    }
                }
                TextButton(onClick=onClearSlot,
                    modifier=Modifier.testTag("builder-run-clear-selected")) {
                    Text("Remove selected session's day",
                        fontSize=12.sp,color=PlanBuilderPalette.accent)
                }
                Text("Quality sessions",fontSize=11.sp,color=weekMuted)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    (0..2).forEach { q ->
                        val eligible=q<=(count-2).coerceAtLeast(0) &&
                            (q<=1 || (request.experience==2 &&
                                request.weeklyKm>=40.0 && count>=4))
                        Surface(
                            onClick={onQualityCount(q)},enabled=eligible,
                            modifier=Modifier.size(48.dp)
                                .testTag("builder-run-quality-count-"+q),
                            color=if(q==request.qualityCount)PlanBuilderPalette.accent
                                else Color(0xFF383838),
                            shape=RoundedCornerShape(9.dp)
                        ) { Box(contentAlignment=Alignment.Center) {
                            Text(q.toString(),fontSize=13.sp,
                                color=if(q==request.qualityCount)PlanBuilderPalette.accentOn else weekWhite)
                        }}
                    }
                }
                Spacer(Modifier.height(13.dp))
                Text("PREFERRED QUALITY STYLE",fontSize=11.sp,
                    color=weekMuted,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf("Threshold","Tempo","Intervals","Hills").forEach { choice ->
                        Surface(
                            onClick={onQualityPreference(choice)},
                            modifier=Modifier.widthIn(min=78.dp).heightIn(min=44.dp)
                                .testTag("builder-quality-"+choice),
                            color=if(choice==qualityPreference)Color(0xFF1D240B)
                                else Color(0xFF303030),
                            shape=RoundedCornerShape(8.dp),
                            border=BorderStroke(1.dp,
                                if(choice==qualityPreference)PlanBuilderPalette.accent
                                else weekOutline)
                        ) { Box(contentAlignment=Alignment.Center) {
                            Text(choice,fontSize=11.sp,color=weekWhite)
                        }}
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Your selected quality style is used when eligible. Exact pinned weekdays are enforced.",
                    fontSize=10.sp,lineHeight=14.sp,color=weekMuted)
            } else {
                Text("The coach will schedule sessions on your available days. Choose my days to pin an exact day.",
                    fontSize=11.sp,lineHeight=16.sp,color=weekMuted)
            }
        }
    }
    val chosenDay=editingDay
    if(chosenDay!=null) {
        RunningDayAssignmentSheet(
            day=chosenDay,
            request=request,
            slots=slots,
            onAssign=onAssignSlot,
            onClear=onClearDay,
            onDismiss={ editingDay=null }
        )
    }
}

private data class RunningDayChoice(
    val code: String,
    val title: String,
    val subtitle: String,
    val indices: List<Int>,
    val tag: String
)

/** A weekday is chosen first; selecting its session calls the same slot-safe
 * placement policy as the advanced editor. It does not change the quality
 * count or silently drop another session when two occupied days are swapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunningDayAssignmentSheet(
    day: Int,
    request: PlanRequest,
    slots: List<Int>,
    onAssign: (Int, Int) -> Unit,
    onClear: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val assignedIndex=slots.indexOf(day)
    val options=listOf(
        RunningDayChoice("E","Easy run","Aerobic base and recovery",
            ((request.qualityCount+1) until request.runDays).toList(),"easy"),
        RunningDayChoice("Q","Quality","Threshold, tempo or selected quality style",
            (1..request.qualityCount).toList(),"quality"),
        RunningDayChoice("L","Long run","Endurance session on one preferred day",
            listOf(0),"long")
    )
    val sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)

    ModalBottomSheet(
        onDismissRequest=onDismiss,
        sheetState=sheetState,
        modifier=Modifier.testTag("builder-run-day-sheet"),
        containerColor=Color(0xFF171717),
        contentColor=weekWhite,
        tonalElevation=0.dp,
        shape=RoundedCornerShape(topStart=24.dp,topEnd=24.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start=20.dp,end=20.dp,bottom=16.dp)
        ) {
            Text("ASSIGN RUNNING SESSION",color=PlanBuilderPalette.accent,
                fontSize=10.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(DayOfWeek.of(day).name.lowercase()
                    .replaceFirstChar { it.uppercase() },
                color=weekWhite,fontSize=23.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(
                if(assignedIndex>=0)
                    "Currently: "+WeeklyPlacement.label(request,"run",assignedIndex)+
                        ". Choose a different type to move or swap it."
                else "Choose which workout belongs on this day.",
                color=weekMuted,fontSize=12.sp,lineHeight=17.sp
            )
            if(day !in request.days) {
                Spacer(Modifier.height(9.dp))
                Text("This day is unavailable. Assigning a run will make it available.",
                    color=weekMuted,fontSize=11.sp,lineHeight=16.sp)
            }
            Spacer(Modifier.height(19.dp))
            options.forEach { choice ->
                val targetIndex=choice.indices.firstOrNull { slots.getOrNull(it)==day }
                    ?: choice.indices.firstOrNull { slots.getOrNull(it)==0 }
                    ?: choice.indices.firstOrNull()
                val selected=assignedIndex in choice.indices && assignedIndex>=0
                val fromDay=targetIndex?.let { slots.getOrNull(it) } ?: 0
                val status=when {
                    targetIndex==null -> if(choice.code=="Q")
                        "Change quality count in Advanced weekly setup."
                        else "No "+choice.title.lowercase()+" slot available."
                    selected -> "Currently assigned"
                    fromDay==0 -> "Assign an unplaced session"
                    assignedIndex>=0 -> "Swap with "+DayOfWeek.of(fromDay).name.take(3)
                    else -> "Move from "+DayOfWeek.of(fromDay).name.take(3)
                }
                val activeColor=when(choice.code) {
                    "L" -> Color.White
                    "Q" -> PlanBuilderPalette.accent
                    else -> Color(0xFF1D240B)
                }
                Surface(
                    onClick={
                        if(targetIndex!=null) {
                            onAssign(targetIndex,day)
                            onDismiss()
                        }
                    },
                    enabled=targetIndex!=null,
                    modifier=Modifier.fillMaxWidth().padding(bottom=9.dp)
                        .heightIn(min=68.dp)
                        .testTag("builder-run-sheet-option-"+choice.tag),
                    color=if(selected) Color(0xFF222C14) else Color(0xFF242424),
                    shape=RoundedCornerShape(12.dp),
                    border=BorderStroke(if(selected)2.dp else 1.dp,
                        if(selected)PlanBuilderPalette.accent else weekOutline)
                ) {
                    Row(Modifier.padding(horizontal=12.dp,vertical=10.dp),
                        verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(36.dp).background(activeColor,
                            RoundedCornerShape(8.dp)),contentAlignment=Alignment.Center) {
                            Text(choice.code,
                                color=if(choice.code=="L" || choice.code=="Q")
                                    Color(0xFF171717) else weekWhite,
                                fontSize=15.sp,fontWeight=FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(choice.title,color=if(targetIndex==null)weekMuted else weekWhite,
                                fontSize=14.sp,fontWeight=FontWeight.Bold)
                            Text(choice.subtitle,color=weekMuted,fontSize=11.sp,lineHeight=15.sp)
                            Text(status,color=if(selected)PlanBuilderPalette.accent
                                else weekMuted,fontSize=10.5f.sp,lineHeight=15.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(1.dp))
            TextButton(
                onClick={
                    if(assignedIndex>=0) onClear(assignedIndex)
                    onDismiss()
                },
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                    .testTag("builder-run-sheet-clear")
            ) {
                Text(if(assignedIndex>=0) "Remove run from this day"
                     else "Leave this day unassigned",
                    color=weekWhite,fontSize=13.sp)
            }
            if(assignedIndex>=0) {
                Text("Removing a day keeps the weekly run count unchanged. Assign the unplaced workout before continuing.",
                    color=weekMuted,fontSize=10.5f.sp,lineHeight=15.sp)
            }
            TextButton(onClick=onDismiss,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                    .testTag("builder-run-sheet-cancel")) {
                Text("Cancel",color=PlanBuilderPalette.accent,fontSize=13.sp)
            }
        }
    }
}
