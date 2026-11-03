package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Approved Figma 614:195 running weekly setup.
 * E/Q/L indicates a suggested preference before engine generation,
 * NOT a saved workout. Pinned sessions require generated plans.
 */
@Composable
internal fun TracksideFigmaRunningSetup(
    request: PlanRequest,
    selectedDay: Int,
    onSelectedDay: (Int) -> Unit,
    custom: Boolean,
    onCustom: (Boolean) -> Unit,
    qualityPreference: String,
    onQualityPreference: (String) -> Unit,
    onRunningCount: (Int) -> Unit,
    onChooseType: (Int, String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val runDays=request.runDays.coerceIn(1,6)
    val available=request.days.sorted()
    val ordered=buildList {
        if(request.longDay in available)add(request.longDay)
        request.qualityDays.sorted().filter { it in available && it !in this }.forEach(::add)
        available.filter { it !in this }.forEach(::add)
    }.take(runDays)
    val selection=when {
        selectedDay==request.longDay -> "Long run"
        selectedDay in request.qualityDays -> "Quality"
        else -> "Easy"
    }
    TracksideFigmaBuilderFrame(
        toolbarTitle="Running Setup",
        eyebrow="RUNNING / WEEKLY SCHEDULE",
        headline="Your running week",
        description="Choose your weekly sessions and preferred days.",
        onBack=onBack, onContinue=onContinue
    ) {
        Row(Modifier.fillMaxWidth().height(45.dp)
                .background(Color(0xFF172936), RoundedCornerShape(11.dp))
                .padding(3.dp),
            horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            listOf(true to "Customize",false to "Let StrideLab decide").forEach { (value,title) ->
                Surface(
                    onClick={onCustom(value)},
                    modifier=Modifier.weight(1f).fillMaxHeight()
                        .testTag(if(value)"builder-running-custom" else "builder-running-decide"),
                    color=if(custom==value)Color(0xFF522633) else Color(0xFF1A2C38),
                    shape=RoundedCornerShape(9.dp),
                    border=BorderStroke(1.dp,
                        if(custom==value)Color(0xFFBA748A) else Color(0xFF526D7E))
                ) { Box(contentAlignment=Alignment.Center) {
                    Text(title,fontSize=12.sp,fontWeight=FontWeight.Bold,color=Color.White)
                }}
            }
        }
        Spacer(Modifier.height(11.dp))
        Surface(Modifier.fillMaxWidth(),
            color=Color(0xFF2A2530),shape=RoundedCornerShape(14.dp),
            border=BorderStroke(1.dp,Color(0xFF715263))) {
            Column(Modifier.padding(14.dp)) {
                Text("WEEKLY RUNS",color=Color(0xFFB6C0CA),
                    fontSize=10.sp,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(13.dp))
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(runDays.toString(),fontSize=38.sp,
                        color=Color.White,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.width(14.dp))
                    Text("sessions / week",fontSize=11.sp,color=Color(0xFFB6C0CA))
                    Spacer(Modifier.weight(1f))
                    listOf(-1,1).forEach { delta ->
                        Surface(
                            onClick={onRunningCount((runDays+delta).coerceIn(1,6))},
                            enabled=runDays+delta in 1..6,
                            modifier=Modifier.padding(start=6.dp).size(44.dp)
                                .testTag(if(delta==1)"builder-run-plus" else "builder-run-minus"),
                            color=Color(0xFF2D4658),shape=RoundedCornerShape(11.dp),
                            border=BorderStroke(1.dp,Color(0xFF597485))
                        ) { Box(contentAlignment=Alignment.Center) {
                            Text(if(delta==1)"+" else "−",fontSize=23.sp,
                                fontWeight=FontWeight.Bold,color=Color.White)
                        }}
                    }
                }
                Spacer(Modifier.height(17.dp))
                Box(Modifier.fillMaxWidth().height(1.dp)
                    .background(Color(0xFF625464)))
                Spacer(Modifier.height(17.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    (1..7).forEach { day ->
                        Column(Modifier.weight(1f),
                            horizontalAlignment=Alignment.CenterHorizontally) {
                            Text(java.time.DayOfWeek.of(day).name.take(3),
                                fontSize=9.sp,color=Color(0xFFB6C0CA))
                            Spacer(Modifier.height(11.dp))
                            val present=day in ordered
                            val type=when {
                                !present -> "—"
                                day==request.longDay -> "L"
                                day in request.qualityDays -> "Q"
                                else -> "E"
                            }
                            Surface(
                                onClick={onSelectedDay(day)},
                                enabled=day in request.days,
                                modifier=Modifier.fillMaxWidth().height(54.dp)
                                    .testTag("builder-running-day-"+day),
                                color=when(type){
                                    "L"->Color(0xFFF1F3F5)
                                    "Q"->Color(0xFFB63047)
                                    "E"->Color(0xFF5B2B3A)
                                    else->Color(0xFF15202A)
                                },
                                shape=RoundedCornerShape(9.dp),
                                border=BorderStroke(
                                    if(selectedDay==day)1.6f.dp else 1.dp,
                                    if(selectedDay==day)Color.White else Color(0xFF576877))
                            ) { Box(contentAlignment=Alignment.Center) {
                                Text(type,fontSize=15.sp,
                                    color=if(type=="L")Color.Black else Color.White,
                                    fontWeight=FontWeight.Bold)
                            }}
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(ordered.size.toString() + " of " + runDays + " placed",
                        fontSize=10.sp,color=Color.White)
                    Text("Suggested days · editable",fontSize=10.sp,
                        color=Color(0xFFB6C0CA))
                }
                Spacer(Modifier.height(17.dp))
                Box(Modifier.fillMaxWidth().height(1.dp)
                    .background(Color(0xFF625464)))
                if(custom) {
                    Spacer(Modifier.height(17.dp))
                    Text(java.time.DayOfWeek.of(selectedDay).name + " / EDIT SESSION",
                        fontSize=10.sp,fontWeight=FontWeight.Bold,color=Color(0xFFFF6C88))
                    Spacer(Modifier.height(9.dp))
                    Text(selection + " workout",fontSize=15.sp,
                        color=Color.White,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        listOf("Easy","Quality","Long run").forEach { type ->
                            Surface(
                                onClick={onChooseType(selectedDay,type)},
                                enabled=selectedDay in request.days,
                                modifier=Modifier.weight(1f).height(52.dp)
                                    .testTag("builder-running-type-"+type.replace(' ','-')),
                                color=if(selection==type)Color(0xFFB73A4F)
                                    else Color(0xFF203849),
                                shape=RoundedCornerShape(10.dp),
                                border=BorderStroke(1.dp,Color(0xFF536F80))
                            ) { Box(contentAlignment=Alignment.Center) {
                                Text(type,fontSize=11.sp,fontWeight=FontWeight.Bold,
                                    color=Color.White)
                            }}
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Pin placement after generating a plan. Current-engine suggestions remain editable before approval.",
                        fontSize=10.sp,lineHeight=14.sp,color=Color(0xFFB6C0CA))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Surface(Modifier.fillMaxWidth(),color=Color(0xFF21202C),
            shape=RoundedCornerShape(12.dp),
            border=BorderStroke(1.dp,Color(0xFF55465B))) {
            Column(Modifier.padding(13.dp)) {
                Text("QUALITY WORKOUTS",fontSize=10.sp,color=Color(0xFFB6C0CA),
                    fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(11.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf("Threshold","Tempo","Intervals","Hills").forEach { label ->
                        Surface(
                            onClick={onQualityPreference(label)},
                            modifier=Modifier.weight(1f).height(35.dp)
                                .testTag("builder-quality-"+label),
                            color=if(qualityPreference==label)Color(0xFF5A2E3B)
                                else Color(0xFF1D303A),
                            shape=RoundedCornerShape(7.dp),
                            border=BorderStroke(1.dp,
                                if(qualityPreference==label)Color(0xFF9F6576)
                                else Color(0xFF47677B))
                        ) { Box(contentAlignment=Alignment.Center) {
                            Text(label,fontSize=10.sp,color=Color.White)
                        }}
                    }
                }
                Spacer(Modifier.height(9.dp))
                Text("Quality type is a planning preference; the coaching engine validates actual workouts.",
                    fontSize=10.sp,color=Color(0xFFB6C0CA))
            }
        }
    }
}
