package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek

/** Approved Strength Setup 698:3 and 698:96.
 * Explicitly represented weekdays and Engine 1 equipment are persisted.
 * Split/muscle emphasis controls disclose that Engine 1 does not enforce them.
 */
private val strengthInk=Color(0xFFFFFFFF)
private val strengthSubtle=Color(0xFFC5C5C5)
private val strengthAccent=PlanBuilderPalette.accent
private val strengthEdge=PlanBuilderPalette.accent

@Composable
internal fun TracksideStrengthSetup(
    request:PlanRequest,
    selectedSlot:Int,
    onSelectedSlot:(Int)->Unit,
    onChangeMode:(Boolean)->Unit,
    onCount:(Int)->Unit,
    onAssign:(Int,Int)->Unit,
    onClear:(Int)->Unit,
    onEquipment:(String,Boolean)->Unit,
    onBack:()->Unit,
    onEditGoal:()->Unit,
    onContinue:()->Unit
) {
    val manual="str" in request.weeklySlots
    val count=request.strengthDays.coerceAtLeast(1)
    val explicit=WeeklyPlacement.days(request,"str")
    val placed=if(manual) explicit.count { it in 1..7 } else 0
    var editingDays by rememberSaveable { mutableStateOf(false) }
    var choosingEquipment by rememberSaveable { mutableStateOf(false) }
    var advisory by rememberSaveable { mutableStateOf("") }
    if(choosingEquipment) {
        TracksideStrengthEquipment(
            request=request,onChoice=onEquipment,
            onBack={ choosingEquipment=false }
        )
        return
    }
    TracksideBuilderFrame(
        progressStep = 3,
        toolbarTitle="Strength Setup",
        eyebrow="STRENGTH / SETUP",
        headline="Your strength plan",
        description="",
        onBack=onBack,
        onContinue=onContinue,
        continueEnabled=!manual || WeeklyPlacement.full(request,"str")
    ) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min=48.dp)
                .background(Color(0xFF242424),RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement=Arrangement.spacedBy(6.dp),
            verticalAlignment=Alignment.CenterVertically
        ) {
            listOf(true to "Customize", false to "Let StrideLab decide").forEach { (custom,label) ->
                val selected=manual==custom
                Surface(
                    onClick={onChangeMode(custom)},
                    modifier=Modifier.weight(1f).heightIn(min=40.dp)
                        .testTag(if(custom)"builder-strength-customize"
                            else "builder-strength-auto"),
                    color=if(selected)PlanBuilderPalette.selected else PlanBuilderPalette.surface,
                    shape=RoundedCornerShape(9.dp),
                    border=BorderStroke(1.dp,
                        if(selected)PlanBuilderPalette.accent else Color(0xFF383838))
                ) {
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal=5.dp,vertical=11.dp),
                        contentAlignment=Alignment.Center
                    ) {
                        Text(label,color=if(selected)strengthInk else strengthSubtle,
                            fontSize=12.sp,fontWeight=FontWeight.Bold,
                            textAlign=TextAlign.Center,maxLines=2)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        StrengthSectionLabel("TRAINING PREFERENCES")
        Spacer(Modifier.height(10.dp))
        StrengthPreferenceRow(
            "Strength goal",
            when(Engine1Engines.goal(request,"str")) {
                "General strength" -> "Build strength"
                "Muscle development" -> "Build muscle"
                else -> "Maintain fitness"
            },
            R.drawable.trackside_strength_goal_icon,onEditGoal,
            "builder-strength-setup-goal")
        Spacer(Modifier.height(7.dp))
        StrengthPreferenceRow(
            "Muscle emphasis","Not available in Engine 1",
            R.drawable.trackside_strength_muscle_icon,
            { advisory="Muscle emphasis is not saved or used by Engine 1.5 in this builder. Workout selection will follow the supported strength goal." },
            "builder-strength-setup-emphasis")
        Spacer(Modifier.height(7.dp))
        val equipmentSummary=listOf(
            "db" to "Dumbbells","bands" to "Bands",
            "bb" to "Barbell","bench" to "Bench",
            "bar" to "Pull-up bar","rack" to "Rack"
        ).filter { (id,_) -> (request.equipment[id]?:0)>0 }
            .joinToString(" · ") { it.second }.ifBlank { "Bodyweight only" }
        StrengthPreferenceRow(
            "Equipment",equipmentSummary,
            R.drawable.trackside_strength_equipment_icon,
            { choosingEquipment=true },"builder-strength-setup-equipment")
        Spacer(Modifier.height(4.dp))

        Surface(
            modifier=Modifier.fillMaxWidth().testTag("builder-strength-weekly"),
            color=Color(0xFF303030),
            shape=RoundedCornerShape(15.dp),
            border=BorderStroke(1.dp,strengthEdge)
        ) {
            Column(Modifier.padding(start=13.dp,end=13.dp,top=8.dp,bottom=0.dp)) {
                StrengthSectionLabel("WEEKLY SESSIONS")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(count.toString(),color=strengthInk,fontSize=42.sp,
                        fontWeight=FontWeight.Bold)
                    Spacer(Modifier.width(16.dp))
                    Text("sessions / week",color=strengthSubtle,fontSize=11.sp,
                        modifier=Modifier.weight(1f))
                    listOf(-1,1).forEach { delta ->
                        val enabled=count+delta in 1..4
                        Surface(
                            onClick={onCount(count+delta)},
                            enabled=enabled,
                            modifier=Modifier.padding(start=8.dp).size(44.dp)
                                .testTag(if(delta<0)"builder-strength-minus"
                                    else "builder-strength-plus"),
                            color=Color(0xFF303030),
                            shape=RoundedCornerShape(10.dp),
                            border=BorderStroke(1.dp,PlanBuilderPalette.accent)
                        ) {
                            Box(contentAlignment=Alignment.Center) {
                                Text(if(delta<0)"−" else "+",color=strengthInk,fontSize=22.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF383838)))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    (1..7).forEach { day ->
                        Text(DayOfWeek.of(day).name.take(3),
                            color=strengthSubtle,fontSize=9.5f.sp,
                            fontWeight=FontWeight.Bold,
                            modifier=Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(10.dp))
                val suggested=request.days.sorted().take(count)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    (1..7).forEach { day ->
                        val slot=if(manual) explicit.indexOf(day)
                            else suggested.indexOf(day)
                        val selected=manual && slot==selectedSlot && slot>=0
                        Surface(
                            onClick={
                                if(manual) onAssign(selectedSlot.coerceIn(0,count-1),day)
                                else {
                                    onChangeMode(true)
                                    onAssign(0,day)
                                }
                            },
                            modifier=Modifier.weight(1f).height(49.dp)
                                .testTag("builder-strength-weekday-"+day),
                            color=when {
                                day !in request.days && slot<0 -> Color(0xFF242424)
                                slot>=0 -> Color(0xFF383838)
                                else -> Color(0xFF171717)
                            },
                            shape=RoundedCornerShape(9.dp),
                            border=BorderStroke(if(selected)2.dp else 1.dp,
                                when {
                                    selected -> Color(0xFFFFFFFF)
                                    slot>=0 -> PlanBuilderPalette.accent
                                    else -> Color(0xFF383838)
                                })
                        ) {
                            Box(contentAlignment=Alignment.Center) {
                                val label=if(slot>=0) when(slot) {
                                    0 -> "UB"
                                    1 -> "LB"
                                    else -> (slot+1).toString()
                                } else if(day !in request.days)"−" else ""
                                Text(label,color=if(slot>=0)PlanBuilderPalette.accent
                                    else strengthSubtle,
                                    fontSize=11.sp,fontWeight=FontWeight.Bold)
                            }
                        }
                    }
                }
                // Keep the edit button's Material touch target intact; remove
                // only the excessive visual gap below the weekday cells.
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(
                        if(manual) placed.toString()+" of "+count+" placed"
                        else "Coach places "+count+" sessions",
                        modifier=Modifier.weight(1f),color=strengthInk,fontSize=11.sp
                    )
                    TextButton(
                        onClick={ editingDays=!editingDays },
                        modifier=Modifier.testTag("builder-strength-edit-schedule")
                    ) {
                        Text(if(editingDays)"Close editor" else "Edit schedule ›",
                            color=strengthSubtle,fontSize=10.3f.sp)
                    }
                }
            }
        }
        if(editingDays) {
            Spacer(Modifier.height(10.dp))
            Text("Choose which session to place, then select its weekday.",
                color=strengthSubtle,fontSize=11.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                repeat(count) { i ->
                    Surface(
                        onClick={onSelectedSlot(i)},
                        modifier=Modifier.weight(1f).height(44.dp)
                            .testTag("builder-strength-session-"+i),
                        color=if(i==selectedSlot)PlanBuilderPalette.selected else PlanBuilderPalette.surface,
                        shape=RoundedCornerShape(8.dp),
                        border=BorderStroke(1.dp,if(i==selectedSlot)
                            PlanBuilderPalette.accent else PlanBuilderPalette.accent)
                    ) {
                        Box(contentAlignment=Alignment.Center) {
                            Text((i+1).toString(),color=strengthInk,fontSize=13.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            TextButton(onClick={ onClear(selectedSlot.coerceIn(0,count-1)) },
                enabled=manual) {
                Text("Clear selected session day",color=Color(0xFFFF9DA7))
            }
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier=Modifier.fillMaxWidth().height(76.dp)
                .testTag("builder-strength-split"),
            color=Color(0xFF303030),
            shape=RoundedCornerShape(15.dp),
            border=BorderStroke(1.dp,PlanBuilderPalette.accent)
        ) {
            Column(Modifier.padding(horizontal=12.dp,vertical=9.dp)) {
                StrengthSectionLabel("TRAINING SPLIT")
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    listOf("Upper / Lower","Full Body","PPL").forEachIndexed { index,label ->
                        Surface(
                            onClick={ advisory="The current coaching engine does not accept or enforce a chosen strength split. The generated draft shows the actual prescribed exercises." },
                            modifier=Modifier.weight(if(index==0)1.3f else 1f)
                                .height(42.dp).testTag("builder-strength-split-"+index),
                            color=if(index==0)PlanBuilderPalette.accent
                                else Color(0xFF303030),
                            shape=RoundedCornerShape(8.dp),
                            border=BorderStroke(1.dp,
                                if(index==0)PlanBuilderPalette.accent
                                else PlanBuilderPalette.accent)
                        ) {
                            Box(contentAlignment=Alignment.Center) {
                                Text(label,color=if(index==0)strengthInk
                                    else strengthSubtle,
                                    fontSize=11.sp,fontWeight=FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
    if(advisory.isNotBlank()) AlertDialog(
        onDismissRequest={ advisory="" },title={ Text("Coaching availability") },
        text={ Text(advisory) },
        confirmButton={ TextButton(onClick={ advisory="" }) { Text("Close") } }
    )
}

@Composable
private fun StrengthSectionLabel(label:String) {
    Text(label,color=strengthSubtle,fontSize=10.sp,
        fontWeight=FontWeight.Bold)
}

@Composable
private fun StrengthPreferenceRow(
    title:String,subtitle:String,iconRes:Int,onClick:()->Unit,tag:String
) {
    Surface(
        onClick=onClick,
        modifier=Modifier.fillMaxWidth().heightIn(min=59.dp).testTag(tag),
        color=Color(0xFF303030),
        shape=RoundedCornerShape(13.dp),
        border=BorderStroke(1.dp,PlanBuilderPalette.accent)
    ) {
        // Intrinsic height grows when Android font scaling requires it.
        // 3dp vertical padding retains Figma's 59dp preference-row baseline.
        Row(Modifier.padding(horizontal=12.dp,vertical=3.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(37.dp).background(PlanBuilderPalette.accent,
                    RoundedCornerShape(10.dp)),
                contentAlignment=Alignment.Center
            ) {
                Icon(
                    painter=painterResource(iconRes),
                    contentDescription=null,
                    tint = PlanBuilderPalette.accent,
                    modifier=Modifier.size(23.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(title,color=strengthInk,fontSize=12.5f.sp,
                    fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle,color=strengthSubtle,fontSize=10.8f.sp,
                    maxLines=1)
            }
            Text("›",color=PlanBuilderPalette.accent,fontSize=23.sp)
        }
    }
}

@Composable
private fun TracksideStrengthEquipment(
    request:PlanRequest,
    onChoice:(String,Boolean)->Unit,
    onBack:()->Unit
) {
    val list=listOf(
        "db" to "Dumbbells","bb" to "Barbell",
        "bands" to "Resistance bands","bench" to "Bench",
        "bar" to "Pull-up bar","rack" to "Rack",
        "anchor" to "Band anchors","support" to "Stable supports"
    )
    TracksideBuilderFrame(
        progressStep = 3,
        toolbarTitle="Equipment",eyebrow="STRENGTH / EQUIPMENT",
        headline="What equipment\ndo you have?",
        description="Choose the equipment available for your workouts.",
        onBack=onBack,onContinue=onBack
    ) {
        val selected=list.count { (id,_) -> (request.equipment[id]?:0)>0 }
        Text(selected.toString()+" of "+list.size+" selected",
            color=strengthSubtle,fontSize=11.sp)
        Spacer(Modifier.height(16.dp))
        list.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                pair.forEach { (id,label) ->
                    val active=(request.equipment[id]?:0)>0
                    Surface(
                        onClick={onChoice(id,!active)},
                        modifier=Modifier.weight(1f).height(99.dp)
                            .testTag("builder-strength-equipment-"+id),
                        color=if(active)PlanBuilderPalette.selected else PlanBuilderPalette.surface,
                        shape=RoundedCornerShape(13.dp),
                        border=BorderStroke(1.dp,if(active)strengthAccent
                            else PlanBuilderPalette.accent)
                    ) {
                        Column(Modifier.padding(13.dp)) {
                            Text(if(active)"✓" else "◦",color=strengthAccent,
                                fontSize=23.sp)
                            Spacer(Modifier.weight(1f))
                            Text(label,color=strengthInk,fontSize=12.sp,
                                fontWeight=FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text("Only selected equipment affects generated strength prescriptions. " +
            "The existing Engine 1 equipment restrictions are preserved.",
            color=strengthSubtle,fontSize=11.sp,lineHeight=16.sp)
    }
}
