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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Choose a workout then tap its day. The saved weekdays are honored by the
 * selected Engine 1 schedule, or the actual recovery constraints reject them.
 * Never display merely suggested E/Q/L tiles as user-controlled appointments.
 */
@Composable
internal fun TracksideFigmaRunningSetup(
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
    val complete = assigned == count && slots.distinct().size == count
    val muted = Color(0xFFB7C4CC)
    val bg = Color(0xFF24252F)
    val border = Color(0xFF5A6879)
    TracksideFigmaBuilderFrame(
        toolbarTitle = "Running Setup",
        eyebrow = "RUNNING / YOUR WEEK",
        headline = "Choose your run days",
        description = "Select a workout, then choose the weekday for it.",
        onBack = onBack,
        onContinue = onContinue,
        continueEnabled = !custom || complete
    ) {
        Row(Modifier.fillMaxWidth().height(46.dp)
            .background(Color(0xFF172B36),RoundedCornerShape(11.dp))
            .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            listOf(true to "Choose my days",false to "Coach decides").forEach { (mode,label) ->
                Surface(
                    onClick = { onCustom(mode) },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .testTag(if(mode) "builder-running-custom" else "builder-running-decide"),
                    color = if(custom==mode)Color(0xFF642F41) else Color(0xFF213642),
                    shape = RoundedCornerShape(9.dp),
                    border = BorderStroke(1.dp,
                        if(custom==mode) Color(0xFFD28293) else Color(0xFF506E80))
                ) {
                    Box(contentAlignment=Alignment.Center) {
                        Text(label,color=Color.White,fontSize=12.sp,
                            fontWeight=FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(13.dp))
        Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(13.dp),
            color=bg,border=BorderStroke(1.dp,border)) {
            Column(Modifier.padding(14.dp)) {
                Text("RUNS PER WEEK",fontSize=10.sp,fontWeight=FontWeight.Bold,
                    color=muted)
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(count.toString(),fontSize=36.sp,color=Color.White,
                        fontWeight=FontWeight.Bold)
                    Spacer(Modifier.width(11.dp))
                    Text("workouts",fontSize=11.sp,color=muted)
                    Spacer(Modifier.weight(1f))
                    listOf(-1,1).forEach { delta ->
                        Surface(
                            onClick={onRunningCount((count+delta).coerceIn(1,6))},
                            enabled=count+delta in 1..6,
                            modifier=Modifier.padding(start=7.dp).size(42.dp)
                                .testTag(if(delta==1)"builder-run-plus" else "builder-run-minus"),
                            color=Color(0xFF2C4C60),
                            shape=RoundedCornerShape(10.dp),
                            border=BorderStroke(1.dp,Color(0xFF62879B))
                        ) {
                            Box(contentAlignment=Alignment.Center) {
                                Text(if(delta==1)"+" else "−",fontSize=23.sp,
                                    color=Color.White,fontWeight=FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("QUALITY RUNS",fontSize=10.sp,color=muted)
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        (0..2).forEach { q ->
                            val eligible=q<=(count-2).coerceAtLeast(0) &&
                                (q<=1 || (request.experience==2 &&
                                    request.weeklyKm>=30.0 && count>=4))
                            Surface(
                                onClick={onQualityCount(q)},
                                enabled=eligible,
                                modifier=Modifier.size(36.dp,33.dp)
                                    .testTag("builder-run-quality-count-"+q),
                                color=if(q==request.qualityCount)Color(0xFFB33B52)
                                    else Color(0xFF263F4C),
                                shape=RoundedCornerShape(8.dp)
                            ) {
                                Box(contentAlignment=Alignment.Center) {
                                    Text(q.toString(),fontSize=12.sp,color=Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        if(custom) {
            Text("1. SELECT A WORKOUT",fontSize=11.sp,fontWeight=FontWeight.Bold,
                color=muted)
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                (0 until count).forEach { i ->
                    val active=selected==i
                    val weekday=slots.getOrNull(i) ?: 0
                    Surface(
                        onClick={onSelectSlot(i)},
                        modifier=Modifier.widthIn(min=100.dp).height(56.dp)
                            .testTag("builder-run-session-"+i),
                        color=if(active)Color(0xFF653344) else Color(0xFF203544),
                        shape=RoundedCornerShape(10.dp),
                        border=BorderStroke(if(active)2.dp else 1.dp,
                            if(active)Color(0xFFFFA7B8) else border)
                    ) {
                        Column(Modifier.padding(horizontal=11.dp,vertical=6.dp),
                            verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            Text(WeeklyPlacement.label(request,"run",i),
                                fontSize=11.sp,color=Color.White,
                                fontWeight=FontWeight.Bold)
                            Text(if(weekday==0)"Unassigned"
                                 else java.time.DayOfWeek.of(weekday).name.take(3),
                                fontSize=10.sp,color=muted)
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("2. TAP THE DAY FOR THIS WORKOUT",
                fontSize=11.sp,fontWeight=FontWeight.Bold,color=muted)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                (1..7).forEach { day ->
                    val slot=slots.indexOf(day)
                    val active=slot==selected
                    val letter=when {
                        slot<0 -> "+"
                        slot==0 -> "L"
                        slot<=request.qualityCount -> "Q"
                        else -> "E"
                    }
                    Column(Modifier.weight(1f),
                        horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(java.time.DayOfWeek.of(day).name.take(3),
                            fontSize=9.sp,color=muted)
                        Spacer(Modifier.height(9.dp))
                        Surface(
                            onClick={onAssignDay(day)},
                            modifier=Modifier.fillMaxWidth().height(57.dp)
                                .testTag("builder-running-day-"+day),
                            color=when {
                                active -> Color(0xFFC3425C)
                                slot==0 -> Color(0xFFF1F2F4)
                                slot>0 && letter=="Q" -> Color(0xFF91354B)
                                slot>0 -> Color(0xFF53303E)
                                else -> Color(0xFF1C3443)
                            },
                            shape=RoundedCornerShape(9.dp),
                            border=BorderStroke(if(active)2.dp else 1.dp,
                                if(active)Color(0xFFFFB9C4) else border)
                        ) {
                            Box(contentAlignment=Alignment.Center) {
                                Text(letter,fontSize=15.sp,fontWeight=FontWeight.Bold,
                                    color=if(slot==0)Color.Black else Color.White)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(13.dp))
            Text(assigned.toString()+" of "+count+" workouts assigned",
                fontSize=12.sp,fontWeight=FontWeight.Bold,
                color=if(complete)Color(0xFF88D6B6) else Color(0xFFFFBA9A),
                modifier=Modifier.testTag("builder-running-assignment-progress"))
            Spacer(Modifier.height(7.dp))
            Text("Tap an occupied weekday to swap workouts. Saturdays and rest days can be selected.",
                fontSize=11.sp,lineHeight=15.sp,color=muted)
            TextButton(onClick=onClearSlot,
                modifier=Modifier.testTag("builder-run-clear-selected")) {
                Text("Clear selected workout's day",
                    fontSize=12.sp,color=Color(0xFFFFA6B2))
            }
            Surface(Modifier.fillMaxWidth(),color=Color(0xFF1A303C),
                shape=RoundedCornerShape(10.dp),
                border=BorderStroke(1.dp,Color(0xFF55798A))) {
                Text("Moving: "+WeeklyPlacement.label(request,"run",selected)+
                    ". The engine will keep this weekday or explain a recovery conflict.",
                    Modifier.padding(12.dp),fontSize=11.sp,lineHeight=16.sp,
                    color=Color.White)
            }
        } else {
            Surface(Modifier.fillMaxWidth(),color=Color(0xFF1A303C),
                shape=RoundedCornerShape(10.dp),
                border=BorderStroke(1.dp,Color(0xFF55798A))) {
                Text("StrideLab will place your runs on available days. To control exact days, choose 'Choose my days' above.",
                    Modifier.padding(14.dp),color=Color.White,
                    fontSize=12.sp,lineHeight=17.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("PREFERRED QUALITY STYLE",fontSize=11.sp,
            color=muted,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            listOf("Threshold","Tempo","Intervals","Hills").forEach { choice ->
                Surface(
                    onClick={onQualityPreference(choice)},
                    modifier=Modifier.weight(1f).height(37.dp)
                        .testTag("builder-quality-"+choice),
                    color=if(choice==qualityPreference)Color(0xFF673246)
                        else Color(0xFF223846),
                    shape=RoundedCornerShape(8.dp),
                    border=BorderStroke(1.dp,
                        if(choice==qualityPreference)Color(0xFFB56C80) else border)
                ) {
                    Box(contentAlignment=Alignment.Center) {
                        Text(choice,fontSize=10.sp,color=Color.White)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Style is advisory until the active coaching engine supports it; weekday assignments are enforced.",
            fontSize=10.sp,lineHeight=14.sp,color=muted)
    }
}
