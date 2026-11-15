package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Default view is the compact seven-day summary. Tapping a day reveals an
 * inline editor; the original manual-day, session-count and quality callbacks
 * remain authoritative, never a decorative or fake pin.
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

    TracksideBuilderFrame(
        progressStep = 3,
        toolbarTitle="Running Setup",
        eyebrow="RUNNING / WEEKLY SCHEDULE",
        headline="Your running week",
        description="Suggested from your goals and recent training. Tap a day to change it.",
        descriptionBottomGap=49,
        descriptionFontSize=11f,
        contentTopPadding=54,
        onBack=onBack,
        onContinue=onContinue,
        continueEnabled=!custom || complete
    ) {
        Surface(
            modifier=Modifier.fillMaxWidth().heightIn(min=227.dp)
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
                Spacer(Modifier.height(32.dp))
                // At 360dp the cells scroll horizontally instead of shrinking
                // to unreachable < 44dp targets.
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
                            "L" -> Color(0xFFFFFFFF)
                            "Q" -> PlanBuilderPalette.accent
                            "E" -> Color(0xFF1D240B)
                            "−" -> Color(0xFF242424)
                            else -> Color(0xFF171717)
                        }
                        val cellBorder=when(code) {
                            "L" -> Color.White
                            "Q" -> PlanBuilderPalette.accent
                            "E" -> PlanBuilderPalette.accent
                            "−" -> Color(0xFF383838)
                            else -> Color(0xFF383838)
                        }
                        Column(Modifier.width(46.dp),
                            horizontalAlignment=Alignment.CenterHorizontally) {
                            Text(DayOfWeek.of(day).name.take(3),
                                fontSize=9.sp,fontWeight=FontWeight.Bold,
                                color=weekMuted)
                            Spacer(Modifier.height(19.dp))
                            Surface(
                                onClick={
                                    when {
                                        slot>=0 -> {
                                            editorOpen=true
                                            onSelectSlot(slot)
                                        }
                                        !editorOpen -> editorOpen=true
                                        custom -> onAssignDay(day)
                                    }
                                },
                                modifier=Modifier.size(46.dp)
                                    .testTag("builder-running-day-"+day),
                                color=cellBg,
                                shape=RoundedCornerShape(9.dp),
                                border=BorderStroke(1.dp,cellBorder)
                            ) {
                                Box(contentAlignment=Alignment.Center) {
                                    if(code.isNotBlank()) Text(code,
                                        color=if(code=="L") Color(0xFF242424)
                                            else if(code=="−") PlanBuilderPalette.accent
                                            else weekWhite,
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
                Spacer(Modifier.height(23.dp))
                Text(assigned.toString()+" of "+count+" placed · "+
                        request.qualityCount.toString()+" quality · 1 long run",
                    color=weekMuted,fontSize=11.5f.sp,
                    modifier=Modifier.testTag("builder-running-assignment-progress"))
            }
        }

        Spacer(Modifier.height(14.dp))
        Text("RUNNING SESSION TYPES",fontSize=11.sp,
            fontWeight=FontWeight.Bold,color=weekMuted)
        Spacer(Modifier.height(10.dp))
        Surface(
            modifier=Modifier.fillMaxWidth().heightIn(min=143.dp)
                .testTag("builder-running-legend"),
            color=weekPanel,shape=RoundedCornerShape(13.dp),
            border=BorderStroke(1.dp,weekOutline)
        ) {
            // Keep the approved 143dp card: 3 x 40dp rows plus a reserved
            // 18dp bottom inset prevents the final subtitle touching its border.
            Column(Modifier.padding(start=10.dp,end=10.dp,top=2.dp,bottom=18.dp)) {
                RunningWeekLegendRow("E","Easy run","Aerobic base and recovery",
                    Color(0xFF1D240B),weekWhite)
                RunningWeekLegendRow("Q","Quality",
                    "Threshold, tempo or selected categories",
                    PlanBuilderPalette.accent,weekWhite)
                RunningWeekLegendRow("L","Long run",
                    "Endurance session on one preferred day",
                    Color(0xFFFFFFFF),Color(0xFF242424))
            }
        }

        Spacer(Modifier.height(22.dp))
        Surface(
            modifier=Modifier.fillMaxWidth().heightIn(min=89.dp)
                .testTag("builder-running-pinned-note"),
            color=weekPanel,shape=RoundedCornerShape(13.dp),
            border=BorderStroke(1.dp,weekOutline)
        ) {
            Column(Modifier.padding(13.dp)) {
                Text(if(pinned!=null) "Pinned by you ◆" else "Suggested · Pin your days ◆",
                    color=weekWhite,fontSize=12.sp,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text("Tap any day to edit or remove its session. Pinned choices are saved and cannot be moved silently.",
                    color=weekMuted,fontSize=10.5f.sp,lineHeight=14.sp)
            }
        }

        // Editing tools are intentionally inline and below the approved
        // compact default. There are no disruptive popups or fake controls.
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
}

@Composable
private fun RunningWeekLegendRow(
    code: String, title: String, subtitle: String,
    tileColor: Color, codeColor: Color
) {
    val fontScale=LocalDensity.current.fontScale
    Row(if(fontScale<=1.01f) Modifier.fillMaxWidth().height(40.dp)
        else Modifier.fillMaxWidth().heightIn(min=43.dp),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Surface(modifier=Modifier.size(32.dp),color=tileColor,
            shape=RoundedCornerShape(8.dp)) {
            Box(contentAlignment=Alignment.Center) {
                Text(code,fontSize=14.sp,color=codeColor,
                    fontWeight=FontWeight.Bold)
            }
        }
        Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(title,fontSize=12.5f.sp,color=weekWhite,
                fontWeight=FontWeight.Bold)
            Text(subtitle,fontSize=10.5f.sp,color=weekMuted)
        }
    }
}
