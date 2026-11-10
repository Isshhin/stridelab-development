package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek

/** Approved master gallery 614:335. Manual calisthenics appointments use
 * WeeklyPlacement and persist in PlanRequest. Focus types are NOT currently
 * enforced by Engine 1; they remain visually distinguishable advisory choices.
 */
private val caliViolet=PlanBuilderPalette.accent
private val caliInk=Color(0xFFFFFFFF)
private val caliSubtle=Color(0xFFC5C5C5)

@Composable
internal fun TracksideCalisthenicsSetup(
    request:PlanRequest,
    selectedSlot:Int,
    onSelectedSlot:(Int)->Unit,
    onMode:(Boolean)->Unit,
    onCount:(Int)->Unit,
    onAssign:(Int,Int)->Unit,
    onClear:(Int)->Unit,
    onAbility:(String,Int)->Unit,
    onBack:()->Unit,
    onContinue:()->Unit
) {
    val count=request.calDays.coerceAtLeast(1)
    val custom="cal" in request.weeklySlots
    val days=WeeklyPlacement.days(request,"cal")
    val assigned=if(custom)days.count { it in 1..7 } else 0
    var focusedDay by rememberSaveable { mutableIntStateOf(3) }
    var advisory by rememberSaveable { mutableStateOf("") }
    TracksideBuilderFrame(
        progressStep = 3,
        toolbarTitle="Calisthenics Setup",
        eyebrow="CALISTHENICS / WEEKLY SCHEDULE",
        headline="Your calisthenics week",
        description="",
        onBack=onBack,onContinue=onContinue,
        continueEnabled=!custom || WeeklyPlacement.full(request,"cal")
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min=48.dp)
                .background(Color(0xFF242424),RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement=Arrangement.spacedBy(6.dp),
            verticalAlignment=Alignment.CenterVertically
        ) {
            listOf(true to "Customize",false to "Let StrideLab decide").forEach { (selectedMode,label) ->
                val isSelected=selectedMode==custom
                Surface(
                    onClick={onMode(selectedMode)},
                    modifier=Modifier.weight(1f).heightIn(min=40.dp)
                        .testTag(if(selectedMode)"builder-cali-customize" else "builder-cali-auto"),
                    color=if(isSelected)Color(0xFF383838) else Color(0xFF242424),
                    shape=RoundedCornerShape(9.dp),
                    border=BorderStroke(1.dp,
                        if(isSelected)PlanBuilderPalette.accent else Color(0xFF383838))
                ) {
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal=5.dp,vertical=11.dp),
                        contentAlignment=Alignment.Center
                    ) {
                        Text(label,color=if(isSelected)caliInk else caliSubtle,
                            fontSize=12.sp,fontWeight=FontWeight.Bold,
                            textAlign=TextAlign.Center,maxLines=2)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Surface(
            modifier=Modifier.fillMaxWidth().testTag("builder-cali-weekly"),
            color=Color(0xFF303030),
            shape=RoundedCornerShape(15.dp),
            border=BorderStroke(1.dp,PlanBuilderPalette.accent)
        ) {
            Column(Modifier.padding(13.dp)) {
                Text("WEEKLY SESSIONS",color=caliSubtle,fontSize=10.5f.sp,
                    fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(count.toString(),color=caliInk,fontSize=42.sp,
                        fontWeight=FontWeight.Bold)
                    Spacer(Modifier.width(17.dp))
                    Text("sessions / week",color=caliSubtle,fontSize=11.5f.sp,
                        modifier=Modifier.weight(1f))
                    listOf(-1,1).forEach { d ->
                        Surface(
                            onClick={onCount(count+d)},
                            enabled=count+d in 1..4,
                            modifier=Modifier.padding(start=8.dp).size(44.dp)
                                .testTag(if(d<0)"builder-cali-minus" else "builder-cali-plus"),
                            color=Color(0xFF303030),
                            shape=RoundedCornerShape(10.dp),
                            border=BorderStroke(1.dp,PlanBuilderPalette.accent)
                        ) {
                            Box(contentAlignment=Alignment.Center) {
                                Text(if(d<0)"−" else "+",color=caliInk,fontSize=22.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(13.dp))
                Box(Modifier.fillMaxWidth().height(1.dp)
                    .background(Color(0xFF383838)))
                Spacer(Modifier.height(13.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    (1..7).forEach { day ->
                        Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {
                            Text(DayOfWeek.of(day).name.take(3),
                                color=caliSubtle,fontSize=9.5f.sp,
                                fontWeight=FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                val suggested=request.days.sorted().take(count)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    (1..7).forEach { day ->
                        val index=if(custom)days.indexOf(day) else suggested.indexOf(day)
                        val selected=focusedDay==day
                        Surface(
                            onClick={
                                focusedDay=day
                                if(custom) onAssign(selectedSlot.coerceIn(0,count-1),day)
                                else {
                                    onMode(true)
                                    onAssign(0,day)
                                }
                            },
                            modifier=Modifier.weight(1f).height(49.dp)
                                .testTag("builder-cali-day-"+day),
                            color=when {
                                index>=0 -> Color(0xFF383838)
                                day !in request.days -> Color(0xFF242424)
                                else -> Color(0xFF171717)
                            },
                            shape=RoundedCornerShape(9.dp),
                            border=BorderStroke(if(selected)2.dp else 1.dp,
                                when {
                                    selected -> Color(0xFFFFFFFF)
                                    index>=0 -> PlanBuilderPalette.accent
                                    else -> Color(0xFF383838)
                                })
                        ) {
                            Box(contentAlignment=Alignment.Center) {
                                val marker=if(index>=0) when(index) {
                                    0 -> "PU"
                                    1 -> "PL"
                                    2 -> "C"
                                    else -> "SK"
                                } else if(day !in request.days)"−" else ""
                                Text(marker,color=if(index>=0)PlanBuilderPalette.accent
                                    else caliSubtle,
                                    fontSize=11.sp,fontWeight=FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(if(custom)assigned.toString()+" of "+count+" placed"
                        else "Coach decides on placement",
                        color=caliInk,fontSize=11.sp,
                        modifier=Modifier.weight(1f))
                    Text(if(custom)"Suggested days  ·  editable" else "Suggested, not pinned",
                        color=caliSubtle,fontSize=10.sp)
                }
                Spacer(Modifier.height(13.dp))
                Box(Modifier.fillMaxWidth().height(1.dp)
                    .background(Color(0xFF383838)))
                Spacer(Modifier.height(12.dp))
                Text(DayOfWeek.of(focusedDay).name+" / EDIT SESSION",
                    color=caliViolet,fontSize=10.5f.sp,
                    fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(
                    when(selectedSlot) {
                        0 -> "Push"
                        1 -> "Pull"
                        2 -> "Core"
                        else -> "Skills"
                    },color=caliInk,fontSize=18.sp,
                    fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    listOf("Push","Pull","Core","Skills").forEachIndexed { i,label ->
                        val chosen=selectedSlot==i
                        Surface(
                            onClick={
                                advisory="Focus labels are not enforced by the current coaching engine. Use the reviewed generated draft to check the actual movements."
                                onSelectedSlot(i.coerceAtMost(count-1))
                            },
                            modifier=Modifier.weight(1f).height(52.dp)
                                .testTag("builder-cali-session-type-"+label),
                            color=if(chosen)PlanBuilderPalette.accent
                                else Color(0xFF303030),
                            shape=RoundedCornerShape(10.dp),
                            border=BorderStroke(1.dp,
                                if(chosen)PlanBuilderPalette.accent
                                else Color(0xFF383838))
                        ) {
                            Box(contentAlignment=Alignment.Center) {
                                Text(label,color=if(chosen)caliInk else caliSubtle,
                                    fontSize=11.sp,fontWeight=FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick={
                            if(!custom) onMode(true)
                            onAssign(selectedSlot.coerceIn(0,count-1),focusedDay)
                        },
                        modifier=Modifier.weight(2f).height(48.dp)
                            .testTag("builder-cali-pin"),
                        color=Color(0xFF303030),
                        shape=RoundedCornerShape(10.dp),
                        border=BorderStroke(1.dp,PlanBuilderPalette.accent)
                    ) {
                        Box(contentAlignment=Alignment.Center) {
                            Text("⌖  Pin this day",color=caliInk,fontSize=12.7f.sp,
                                fontWeight=FontWeight.Bold)
                        }
                    }
                    Surface(
                        onClick={onClear(selectedSlot.coerceIn(0,count-1))},
                        enabled=custom,
                        modifier=Modifier.weight(1f).height(48.dp)
                            .testTag("builder-cali-unassign"),
                        color=Color(0xFF303030),
                        shape=RoundedCornerShape(10.dp),
                        border=BorderStroke(1.dp,Color(0xFF1D240B))
                    ) {
                        Box(contentAlignment=Alignment.Center) {
                            Text("Remove",color=PlanBuilderPalette.accent,
                                fontSize=11.5f.sp,fontWeight=FontWeight.Bold)
                        }
                    }
                }
                // The advisory is shown when focus choices are tapped; the approved
                // weekly card does not contain an extra paragraph below the actions.
                Spacer(Modifier.height(10.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Surface(
            modifier=Modifier.fillMaxWidth().heightIn(min=86.dp)
                .testTag("builder-cali-starting-levels"),
            color=Color(0xFF242424),
            shape=RoundedCornerShape(15.dp),
            border=BorderStroke(1.dp,PlanBuilderPalette.accent)
        ) {
            Column(Modifier.padding(horizontal=11.dp,vertical=6.dp)) {
                Text("STARTING LEVELS",color=caliSubtle,fontSize=10.sp,
                    fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                listOf(listOf("Push" to request.calLevel,
                    "Pull" to request.pullLevel),
                    listOf("Core" to request.coreLevel,
                    "Skills" to 0)).forEachIndexed { rowIndex,pair ->
                    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        pair.forEach { (key,value) ->
                            Surface(
                                onClick={
                                    if(key=="Skills") advisory="Skill-level profiling is not yet supported by Engine 1."
                                    else onAbility(key,value.let { (it+1)%3 })
                                },
                                modifier=Modifier.weight(1f).height(23.dp)
                                    .testTag("builder-cali-level-"+key),
                                color=Color(0xFF303030),
                                shape=RoundedCornerShape(6.dp),
                                border=BorderStroke(1.dp,Color(0xFF383838))
                            ) {
                                Row(Modifier.padding(horizontal=5.dp),
                                    verticalAlignment=Alignment.CenterVertically) {
                                    Text(key,color=caliInk,fontSize=9.5f.sp,
                                        modifier=Modifier.weight(1f))
                                    Text(when(value) {0->"Beginner";1->"Intermediate";else->"Advanced"},
                                        color=PlanBuilderPalette.accent,fontSize=9.sp)
                                }
                            }
                        }
                    }
                    if(rowIndex==0) Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
    if(advisory.isNotBlank()) AlertDialog(
        onDismissRequest={advisory=""},
        title={Text("Coaching support")},
        text={Text(advisory)},
        confirmButton={TextButton(onClick={advisory=""}){Text("Close")}}
    )
}
