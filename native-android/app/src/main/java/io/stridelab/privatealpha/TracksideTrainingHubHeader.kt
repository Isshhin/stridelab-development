package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

private val hubLime=Color(0xFFD8FB70)
private val hubInk=Color(0xFFF1F5EF)
private val hubMuted=Color(0xFFAAB5AA)
private val hubCard=Color(0xFF1B211D)
private val hubBorder=Color(0xFF353F35)

/** Navigation/presentation only. Approval and recommendation actions remain in their owners. */
@Composable
internal fun TracksideTrainingHubHeader(
    active:PlanSnapshot,
    approvedPlans:List<PlanSnapshot>,
    pendingDrafts:Int,
    selectedDate:LocalDate,
    agenda:Boolean,
    busy:Boolean,
    actions:PlanActions,
    onAgenda:(Boolean)->Unit,
    onSelectedDate:(LocalDate)->Unit,
    onPlanSelected:(String)->Unit,
    onManage:()->Unit,
    onPlanTools:()->Unit
) {
    var selectorOpen by rememberSaveable(active.owner) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
        verticalAlignment=Alignment.CenterVertically) {
        Row(
            Modifier.height(42.dp),
            horizontalArrangement=Arrangement.spacedBy(3.dp)
        ) {
            listOf(false to "Day",true to "Agenda").forEach { (mode,label) ->
                Surface(
                    onClick={onAgenda(mode)},
                    modifier=Modifier.width(78.dp).testTag(
                        if(mode)"training-view-agenda" else "training-view-day"),
                    shape=RoundedCornerShape(10.dp),
                    color=if(agenda==mode)hubLime else hubCard,
                    border=BorderStroke(1.dp,if(agenda==mode)hubLime else hubBorder)
                ) {
                    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                        Text(label,color=if(agenda==mode)Color(0xFF101610) else hubMuted,
                            fontSize=12.sp,fontWeight=FontWeight.SemiBold)
                    }
                }
            }
        }
        TextButton(onClick=onManage,
            modifier=Modifier.testTag("training-manage-plan")) {
            Text("Manage ›",color=hubLime,fontWeight=FontWeight.SemiBold)
        }
    }

    Surface(shape=RoundedCornerShape(14.dp),color=hubCard,
        border=BorderStroke(1.dp,hubBorder),
        modifier=Modifier.fillMaxWidth().testTag("training-active-plan-card")) {
        Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("ACTIVE TRAINING PLAN",fontSize=10.sp,color=hubMuted,
                    modifier=Modifier.weight(1f),fontWeight=FontWeight.Bold)
                Surface(color=Color(0xFF303F28),shape=RoundedCornerShape(12.dp)) {
                    Text("Approved",color=hubLime,fontSize=10.sp,
                        fontWeight=FontWeight.SemiBold,
                        modifier=Modifier.padding(horizontal=9.dp,vertical=5.dp))
                }
            }
            Text(active.name ?: active.request.goal,fontSize=18.sp,
                color=hubInk,fontWeight=FontWeight.Bold,maxLines=2,
                overflow=TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("${active.request.weeks}-week training plan",
                    color=hubMuted,fontSize=11.sp,modifier=Modifier.weight(1f))
                if(pendingDrafts>0) TextButton(onClick=onPlanTools) {
                    Text("$pendingDrafts draft${if(pendingDrafts==1)"" else "s"} to review",
                        color=hubLime,fontSize=11.sp)
                }
                if(approvedPlans.size>1) TextButton(onClick={selectorOpen=!selectorOpen}) {
                    Text(if(selectorOpen)"Hide plans" else "Switch plan",
                        color=hubLime,fontSize=11.sp)
                }
            }
            if(selectorOpen) approvedPlans.forEach { candidate ->
                Surface(
                    onClick={onPlanSelected(candidate.id);selectorOpen=false},
                    modifier=Modifier.fillMaxWidth().heightIn(min=43.dp),
                    color=if(candidate.id==active.id)Color(0xFF2D3828) else Color(0xFF222923),
                    shape=RoundedCornerShape(9.dp),
                    border=BorderStroke(1.dp,if(candidate.id==active.id)hubLime else hubBorder)
                ) {
                    Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(candidate.name ?: candidate.request.goal,
                            color=hubInk,modifier=Modifier.weight(1f),fontSize=12.sp)
                        if(candidate.id==active.id) Text("Active",color=hubLime,fontSize=10.sp)
                    }
                }
            }
        }
    }
    Surface(shape=RoundedCornerShape(14.dp),color=hubCard,
        border=BorderStroke(1.dp,hubBorder),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            TracksideTrainingWeekChart(
                plan=active,selected=selectedDate,
                showNavigation=true,onSelect=onSelectedDate)
        }
    }
    Engine15CoachReviewCard(active,actions,busy)
}
