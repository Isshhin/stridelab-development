package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Figma 687:3 strength-goal cards, plus native Engine 1-supported
 * cross-discipline preferences. Decorative icons are provisional, as
 * they are in the approved Figma source. No unsupported split is promised.
 */
private val crossWhite = Color(0xFFFFFFFF)
private val crossMuted = Color(0xFFC5C5C5)
private val crossBlue = PlanBuilderPalette.accent

private data class StrengthGoalChoice(val title: String, val detail: String,
    val policyGoal: String, val icon: Int)

private val strengthGoalChoices = listOf(
    StrengthGoalChoice("Build Strength","Get stronger and lift heavier.",
        "General strength",R.drawable.trackside_figma_activity),
    StrengthGoalChoice("Build Muscle","Develop muscle size and definition.",
        "Muscle development",R.drawable.trackside_figma_loop),
    StrengthGoalChoice("Physique Development",
        "Emphasize the areas you want to improve.",
        "Muscle development",R.drawable.trackside_figma_activity),
    StrengthGoalChoice("Maintain Fitness","Keep your strength and consistency.",
        "Strength endurance",R.drawable.trackside_figma_loop)
)

internal fun strengthChoicePolicy(title:String) =
    strengthGoalChoices.firstOrNull { it.title==title }?.policyGoal
        ?: "General strength"

@Composable
internal fun TracksideStrengthGoalScreen(
    selected:String,
    onSelect:(String)->Unit,
    onBack:()->Unit,
    onContinue:()->Unit
) {
    var muscleInfo by remember { mutableStateOf(false) }
    val active=strengthGoalChoices.firstOrNull { it.title==selected }
        ?: strengthGoalChoices.first()
    TracksideBuilderFrame(
        progressStep = 2,
        toolbarTitle="Strength goal",
        eyebrow="STRENGTH / YOUR GOAL",
        headline="What are you\nworking toward?",
        description="Choose your main goal. StrideLab shapes your workouts around it.",
        ctaLabel="Save goal",
        onBack=onBack,onContinue=onContinue
    ) {
        strengthGoalChoices.forEachIndexed { index,choice ->
            val chosen=active.title==choice.title
            Surface(
                onClick={onSelect(choice.title)},
                modifier=Modifier.fillMaxWidth().height(105.dp)
                    .testTag("builder-strength-goal-" + index),
                color=Color.Transparent,
                shape=RoundedCornerShape(16.dp),
                border=BorderStroke(1.dp,
                    if(chosen)PlanBuilderPalette.accent else PlanBuilderPalette.accent)
            ) {
                Row(
                    Modifier.fillMaxSize().background(Brush.horizontalGradient(
                        if(chosen) listOf(PlanBuilderPalette.accent,Color(0xFF303030))
                        else listOf(Color(0xFF303030),Color(0xFF242424))
                    )).padding(11.dp),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(13.dp)
                ) {
                    Surface(
                        modifier=Modifier.size(84.dp,79.dp),
                        shape=RoundedCornerShape(12.dp),
                        color=if(chosen)PlanBuilderPalette.accent else Color(0xFF242424),
                        border=BorderStroke(1.dp,
                            if(chosen)PlanBuilderPalette.accent else PlanBuilderPalette.accent)
                    ) {
                        Box(contentAlignment=Alignment.Center) {
                            Icon(
                                painter=painterResource(choice.icon),
                                contentDescription=null,
                                tint=PlanBuilderPalette.accent,
                                modifier=Modifier.size(35.dp)
                            )
                        }
                    }
                    Column(Modifier.weight(1f),
                        verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        Text(choice.title,color=crossWhite,fontSize=15.7f.sp,
                            fontWeight=FontWeight.Bold,maxLines=2,
                            overflow=TextOverflow.Ellipsis)
                        Text(choice.detail,color=crossMuted,fontSize=11.4f.sp,
                            lineHeight=15.sp)
                    }
                }
            }
            Spacer(Modifier.height(if(index==strengthGoalChoices.lastIndex)
                11.dp else 9.dp))
        }
        Surface(
            onClick={muscleInfo=true},
            modifier=Modifier.fillMaxWidth().height(64.dp)
                .testTag("builder-strength-muscle-emphasis"),
            color=Color(0xFF242424),
            shape=RoundedCornerShape(16.dp),
            border=BorderStroke(1.dp,PlanBuilderPalette.accent)
        ) {
            Row(Modifier.padding(horizontal=12.dp),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(40.dp).background(PlanBuilderPalette.accent,
                        RoundedCornerShape(12.dp)),
                    contentAlignment=Alignment.Center
                ) { Text("◎",color=crossBlue,fontSize=25.sp) }
                Column(Modifier.weight(1f)) {
                    Text("Muscle emphasis",color=crossWhite,fontSize=13.sp,
                        fontWeight=FontWeight.Bold)
                    Text("Optional · Not yet supported by Engine 1",
                        color=crossMuted,fontSize=10.7f.sp)
                }
                Text("›",color=crossBlue,fontSize=23.sp)
            }
        }
        if(active.title=="Physique Development" ||
            active.title=="Maintain Fitness") {
            Spacer(Modifier.height(10.dp))
            Text(
                "Engine 1 uses its "+active.policyGoal+
                    " prescription for this choice. Specific physique or "+
                    "maintenance rules are not yet available.",
                fontSize=11.sp,lineHeight=15.sp,color=crossMuted,
                modifier=Modifier.testTag("builder-strength-goal-limitation"))
        }
    }
    if(muscleInfo) AlertDialog(
        onDismissRequest={muscleInfo=false},
        title={Text("Muscle emphasis")},
        text={Text("Muscle-specific emphasis is not yet saved or enforced by the current engine. Your selected strength goal and schedule will be saved.")},
        confirmButton={TextButton(onClick={muscleInfo=false}) { Text("Close") }}
    )
}

/** Full-screen Engine 1 goal/ability selection for all non-running disciplines.
 * Running athletes enter it after their running setup when they selected other
 * disciplines; single-discipline athletes do not see the legacy builder.
 */
@Composable
internal fun TracksideCrossTrainingGoalsScreen(
    request:PlanRequest,
    onBack:()->Unit,
    onContinue:()->Unit,
    onEditStrengthGoal:()->Unit,
    onGoal:(String,String)->Unit,
    onCount:(String,Int)->Unit,
    onExperience:(String,Int)->Unit
) {
    val active=listOf("str","cal","cond").filter {
        WeeklyPlacement.count(request,it)>0
    }
    TracksideBuilderFrame(
        progressStep = 3,
        toolbarTitle="Goals & ability",
        eyebrow="PLAN / GOALS & ABILITY",
        headline="Make it your plan",
        description="Set real coaching goals, weekly sessions and current ability for each discipline.",
        onBack=onBack,onContinue=onContinue,
        continueEnabled=active.isNotEmpty()
    ) {
        active.forEach { discipline ->
            val accent=tracksideDisciplineColor(discipline)
            Text(
                when(discipline) {
                    "str"->"STRENGTH"
                    "cal"->"CALISTHENICS"
                    else->"CONDITIONING"
                },color=accent,fontSize=11.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if(discipline=="str") {
                Surface(
                    onClick=onEditStrengthGoal,
                    modifier=Modifier.fillMaxWidth().heightIn(min=58.dp)
                        .testTag("builder-strength-edit-goal"),
                    color=Color(0xFF303030),
                    shape=RoundedCornerShape(13.dp),
                    border=BorderStroke(1.dp,PlanBuilderPalette.accent)
                ) {
                    Row(Modifier.padding(14.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Strength goal",fontSize=13.sp,color=crossWhite,
                                fontWeight=FontWeight.Bold)
                            Text(Engine1Engines.goal(request,"str"),
                                fontSize=11.sp,color=crossMuted)
                        }
                        Text("Edit ›",fontSize=12.sp,color=crossBlue)
                    }
                }
            } else {
                Engine1Engines.goals.getValue(discipline).forEach { goal ->
                    val chosen=Engine1Engines.goal(request,discipline)==goal
                    Surface(
                        onClick={onGoal(discipline,goal)},
                        modifier=Modifier.fillMaxWidth().padding(bottom=7.dp)
                            .heightIn(min=53.dp)
                            .testTag("builder-cross-goal-" + discipline + "-" + goal),
                        color=if(chosen)Color(0xFF383838) else Color(0xFF242424),
                        shape=RoundedCornerShape(11.dp),
                        border=BorderStroke(1.dp,
                            if(chosen)accent else PlanBuilderPalette.accent)
                    ) {
                        Row(Modifier.padding(horizontal=14.dp,vertical=13.dp),
                            verticalAlignment=Alignment.CenterVertically) {
                            Text(goal,color=crossWhite,fontSize=12.5f.sp,
                                modifier=Modifier.weight(1f))
                            if(chosen) Text("✓",color=accent)
                        }
                    }
                }
            }
            Spacer(Modifier.height(13.dp))
            val count=WeeklyPlacement.count(request,discipline)
            Surface(
                modifier=Modifier.fillMaxWidth(),
                color=Color(0xFF303030),
                shape=RoundedCornerShape(13.dp),
                border=BorderStroke(1.dp,PlanBuilderPalette.accent)
            ) {
                Row(Modifier.padding(horizontal=13.dp,vertical=10.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("SESSIONS PER WEEK",fontSize=10.sp,
                            color=crossMuted,fontWeight=FontWeight.Bold)
                        Text(count.toString(),fontSize=28.sp,
                            color=crossWhite,fontWeight=FontWeight.Bold)
                    }
                    val limit=if(discipline=="cond")3 else 4
                    listOf(-1,1).forEach { delta ->
                        Surface(
                            onClick={onCount(discipline,count+delta)},
                            enabled=count+delta in 1..limit,
                            modifier=Modifier.padding(start=8.dp).size(44.dp)
                                .testTag("builder-cross-count-" + discipline + "-" + delta),
                            color=Color(0xFF383838),
                            shape=RoundedCornerShape(10.dp),
                            border=BorderStroke(1.dp,PlanBuilderPalette.accent)
                        ) {
                            Box(contentAlignment=Alignment.Center) {
                                Text(if(delta<0)"−" else "+",
                                    fontSize=23.sp,color=crossWhite)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(13.dp))
            if(discipline=="str") {
                Text("STRENGTH EXPERIENCE",fontSize=10.sp,
                    color=crossMuted,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                TracksideCrossAbilityRow(
                    listOf("Starting","Some experience","Experienced"),
                    request.strengthExperience,accent,"strength",
                    { onExperience("str",it) })
            }
            if(discipline=="cal") {
                listOf(
                    Triple("PUSHING ABILITY",request.calLevel,"push"),
                    Triple("PULLING ABILITY",request.pullLevel,"pull"),
                    Triple("CORE ABILITY",request.coreLevel,"core")
                ).forEach { (label,selected,key) ->
                    Text(label,fontSize=10.sp,
                        color=crossMuted,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    TracksideCrossAbilityRow(
                        listOf("Starting","Progressing","Confident"),
                        selected,accent,key,{ onExperience(key,it) })
                    Spacer(Modifier.height(12.dp))
                }
            }
            Spacer(Modifier.height(26.dp))
        }
        Text("Training days are selected in the next steps. Recovery checks "+
            "apply to the generated draft.",color=crossMuted,
            fontSize=11.sp,lineHeight=15.sp)
    }
}

@Composable
private fun TracksideCrossAbilityRow(
    options:List<String>,
    chosen:Int,
    accent:Color,
    tag:String,
    onSelect:(Int)->Unit
) {
    Row(Modifier.fillMaxWidth(),
        horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        options.forEachIndexed { index,label ->
            val selected=chosen==index
            Surface(
                onClick={onSelect(index)},
                modifier=Modifier.weight(1f).heightIn(min=48.dp)
                    .testTag("builder-cross-ability-" + tag + "-" + index),
                color=if (selected) PlanBuilderPalette.selected else Color(0xFF242424),
                shape=RoundedCornerShape(9.dp),
                border=BorderStroke(1.dp,
                    if(selected)accent else PlanBuilderPalette.accent)
            ) {
                Box(Modifier.padding(horizontal=3.dp,vertical=8.dp),
                    contentAlignment=Alignment.Center) {
                    Text(label,fontSize=10.3f.sp,
                        color=if(selected)crossWhite else crossMuted,
                        maxLines=2,overflow=TextOverflow.Ellipsis)
                }
            }
        }
    }
}
