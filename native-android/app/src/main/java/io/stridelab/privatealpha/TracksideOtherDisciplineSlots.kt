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

/** Any supported discipline may opt into exact weekday placement independently.
 * The builder does not claim that the current engine has distinct strength
 * upper/lower or calisthenics push/pull templates until it actually does.
 */
@Composable
internal fun TracksideOtherDisciplineSlots(
    request: PlanRequest,
    selectedDiscipline: String,
    selectedSlot: Int,
    onSelectDiscipline: (String) -> Unit,
    onSelectSlot: (Int) -> Unit,
    onAssignDay: (String,Int,Int) -> Unit,
    onClearSlot: (String,Int) -> Unit,
    onAuto: (String) -> Unit,
    onCount: (String,Int) -> Unit
) {
    val active=listOf("str","cal","cond").filter {
        WeeklyPlacement.count(request,it)>0
    }
    if(active.isEmpty()) return
    val discipline=selectedDiscipline.takeIf { it in active } ?: active.first()
    val n=WeeklyPlacement.count(request,discipline)
    val slots=WeeklyPlacement.days(request,discipline)
    val manual=discipline in request.weeklySlots
    val current=selectedSlot.coerceIn(0,n-1)
    val textMuted=Color(0xFFC5C5C5)
    val border=PlanBuilderPalette.accent
    Surface(
        modifier=Modifier.fillMaxWidth().testTag("builder-other-discipline-editor"),
        color=Color(0xFF303030),
        shape=RoundedCornerShape(14.dp),
        border=BorderStroke(1.dp,border)
    ) {
        Column(Modifier.padding(13.dp),
            verticalArrangement=Arrangement.spacedBy(11.dp)) {
            Text("PLACE OTHER DISCIPLINES",
                fontSize=11.sp,color=textMuted,fontWeight=FontWeight.Bold)
            Text("Choose a discipline, a session, then a weekday.",
                fontSize=12.sp,color=Color.White)
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                active.forEach { name ->
                    Surface(
                        onClick={
                            onSelectDiscipline(name)
                            onSelectSlot(0)
                        },
                        modifier=Modifier.weight(1f).height(41.dp)
                            .testTag("builder-discipline-day-tab-"+name),
                        color=if(name==discipline)PlanBuilderPalette.selected
                            else Color(0xFF303030),
                        shape=RoundedCornerShape(9.dp),
                        border=BorderStroke(1.dp,
                            if(name==discipline)PlanBuilderPalette.accent else border)
                    ) {
                        Box(contentAlignment=Alignment.Center) {
                            Text(when(name) {
                                "str" -> "Strength"
                                "cal" -> "Cali"
                                else -> "Conditioning"
                            },fontSize=11.sp,color=Color.White)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),
                verticalAlignment=Alignment.CenterVertically) {
                Text("SESSIONS PER WEEK",fontSize=10.sp,
                    fontWeight=FontWeight.Bold,color=textMuted,
                    modifier=Modifier.weight(1f))
                Text(n.toString(),color=Color.White,fontSize=19.sp,
                    fontWeight=FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                val max=if(discipline=="cond")3 else 4
                listOf(-1,1).forEach { delta ->
                    Surface(
                        onClick={onCount(discipline,n+delta)},
                        enabled=n+delta in 1..max,
                        modifier=Modifier.padding(start=5.dp).size(34.dp)
                            .testTag("builder-other-count-"+(if(delta<0)"minus" else "plus")),
                        color=Color(0xFF383838),
                        shape=RoundedCornerShape(8.dp),
                        border=BorderStroke(1.dp,border)
                    ) {
                        Box(contentAlignment=Alignment.Center) {
                            Text(if(delta<0)"−" else "+",
                                fontSize=20.sp,color=Color.White)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                (0 until n).forEach { i ->
                    val activeSlot=i==current
                    Surface(
                        onClick={onSelectSlot(i)},
                        modifier=Modifier.widthIn(min=99.dp).heightIn(min=49.dp)
                            .testTag("builder-other-session-"+i),
                        shape=RoundedCornerShape(9.dp),
                        color=if(activeSlot)PlanBuilderPalette.selected
                            else Color(0xFF303030),
                        border=BorderStroke(if(activeSlot)2.dp else 1.dp,
                            if(activeSlot)PlanBuilderPalette.accent else border)
                    ) {
                        Column(Modifier.padding(horizontal=8.dp,vertical=6.dp),
                            verticalArrangement=Arrangement.spacedBy(3.dp)) {
                            Text(WeeklyPlacement.label(request,discipline,i),
                                fontSize=11.sp,color=Color.White,
                                fontWeight=FontWeight.Bold)
                            val day=slots.getOrNull(i) ?: 0
                            Text(if(!manual)"Coach places"
                                 else if(day==0)"Unassigned"
                                 else java.time.DayOfWeek.of(day).name.take(3),
                                fontSize=10.sp,color=textMuted,
                                modifier=Modifier.testTag(
                                    "builder-other-session-weekday-"+i))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                (1..7).forEach { day ->
                    val occupied=if(manual)slots.indexOf(day) else -1
                    val picked=occupied==current && manual
                    Column(Modifier.weight(1f),
                        horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(java.time.DayOfWeek.of(day).name.take(3),
                            fontSize=9.sp,color=textMuted)
                        Spacer(Modifier.height(7.dp))
                        Surface(
                            onClick={onAssignDay(discipline,current,day)},
                            modifier=Modifier.fillMaxWidth().height(44.dp)
                                .testTag("builder-other-day-"+day),
                            color=if(picked)PlanBuilderPalette.selected
                                else if(occupied>=0)PlanBuilderPalette.surfaceRaised
                                else Color(0xFF303030),
                            shape=RoundedCornerShape(8.dp),
                            border=BorderStroke(if(picked)2.dp else 1.dp,
                                if(picked)Color.White else border)
                        ) {
                            Box(contentAlignment=Alignment.Center) {
                                Text(if(occupied>=0)(occupied+1).toString() else "+",
                                    fontSize=13.sp,color=Color.White)
                            }
                        }
                    }
                }
            }
            Text(
                if(!manual) "Automatic placement is selected. Tap a weekday to start your custom schedule."
                else slots.count { it in 1..7 }.toString()+" of "+n+
                    " sessions assigned. Tapping an occupied day swaps two sessions.",
                color=if(manual && slots.all { it in 1..7 })
                    PlanBuilderPalette.accent else textMuted,
                fontSize=11.sp,lineHeight=15.sp,
                modifier=Modifier.testTag("builder-other-assignment-progress")
            )
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                TextButton(onClick={onClearSlot(discipline,current)},
                    enabled=manual) {
                    Text("Clear day",fontSize=11.sp,color=Color(0xFFFF9DA7))
                }
                TextButton(onClick={onAuto(discipline)},
                    enabled=manual) {
                    Text("Coach places these sessions",
                        fontSize=11.sp,color=PlanBuilderPalette.accent)
                }
            }
            Text("Same-day combinations and recovery spacing must pass the selected coaching policy. Unsupported exercise splits are not promised.",
                fontSize=10.sp,lineHeight=14.sp,color=textMuted)
        }
    }
}
