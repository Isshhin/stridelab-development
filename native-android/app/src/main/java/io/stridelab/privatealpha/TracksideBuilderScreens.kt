package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Native data-bound equivalents of APPROVED MASTER GALLERY Figma frames:
 * 551:580 (disciplines), 551:603 (running goals), 551:796 (race intent),
 * 551:865 (ability). These are not the previous generic 4-step chip screens.
 *
 * No Figma demo mileage, fitness results, heart-rate values or PRs are inserted
 * into actual athlete plans. Screens modify the existing PlanRequest only.
 */
private val black = PlanBuilderPalette.background
private val outline = PlanBuilderPalette.border
private val slate = PlanBuilderPalette.surface
private val darkWine = PlanBuilderPalette.selected
private val fRed = PlanBuilderPalette.accent
private val white = PlanBuilderPalette.text
private val muted = PlanBuilderPalette.secondary

@Composable
internal fun TracksideBuilderFrame(
    eyebrow: String,
    toolbarTitle: String = "Generate Training Plan",
    ctaLabel: String = "Continue",
    headline: String,
    description: String,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    continueEnabled: Boolean = true,
    footer: (@Composable () -> Unit)? = null,
    descriptionBottomGap: Int = 18,
    descriptionFontSize: Float = 11.5f,
    contentTopPadding: Int = 14,
    headlineLineHeight: Int = 30,
    progressStep: Int = 2,
    body: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxSize().background(black).testTag("figma-builder-frame")) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            TextButton(
                modifier = Modifier.size(28.dp).testTag("builder-figma-back"),
                onClick = onBack,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("‹", color = white, fontSize = 29.sp, lineHeight = 30.sp)
            }
            Box(Modifier.weight(1f)) {
                PlanBuilderStageProgress(progressStep)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp,
                    top = contentTopPadding.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Text(headline, fontSize = 23.sp, lineHeight = headlineLineHeight.sp,
                fontWeight = FontWeight.Bold, color = white,
                modifier = Modifier.fillMaxWidth())
            if(description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(description, fontSize = descriptionFontSize.sp,
                    lineHeight = 17.sp, color = muted)
                Spacer(Modifier.height(descriptionBottomGap.dp))
            } else Spacer(Modifier.height(10.dp))
            body()
        }
        footer?.invoke()
        Button(
            onClick = onContinue,
            enabled = continueEnabled,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .height(55.dp).testTag("builder-figma-continue"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = fRed,
                contentColor = PlanBuilderPalette.accentOn,
                disabledContainerColor = PlanBuilderPalette.border,
                disabledContentColor = muted
            )
        ) {
            Text(ctaLabel, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(15.dp))
    }
}

@Composable
internal fun TracksideDisciplineScreen(
    request: PlanRequest,
    onToggle: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val entries = listOf(
        Triple("run", "Running", "Endurance, speed & racing"),
        Triple("str", "Strength", "Muscle, power and resilience"),
        Triple("cal", "Calisthenics", "Bodyweight strength and skills"),
        Triple("cond", "Conditioning", "Fitness and work capacity")
    )
    TracksideBuilderFrame(
        eyebrow = "BUILD YOUR PLAN",
        headline = "Choose your disciplines",
        description = "Pick one or more. We'll build the plan around them.",
        onBack = onBack,
        onContinue = onContinue,
        continueEnabled = request.runDays + request.strengthDays +
            request.calDays + request.conditioningDays > 0,
        progressStep = 1
    ) {
        // Figma 551:580 has a larger description-to-card gap than other
        // builder stages. Keep this adjustment local to discipline selection.
        entries.forEachIndexed { i, (discipline, name, desc) ->
            val selected = when(discipline) {
                "run" -> request.runDays > 0
                "str" -> request.strengthDays > 0
                "cal" -> request.calDays > 0
                else -> request.conditioningDays > 0
            }
            val accent = PlanBuilderPalette.accent
            val selectedBg = PlanBuilderPalette.selected
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp)
                    .testTag("discipline-$discipline"),
                onClick = { onToggle(discipline) },
                shape = RoundedCornerShape(13.dp),
                color = if(selected) selectedBg else slate,
                border = BorderStroke(1.dp, if(selected) accent else outline)
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(30.dp)
                        .background(PlanBuilderPalette.selected, RoundedCornerShape(7.dp)),
                        contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(when(discipline) {
                                "run" -> R.drawable.trackside_discipline_running
                                "str" -> R.drawable.trackside_discipline_strength
                                "cal" -> R.drawable.trackside_discipline_calisthenics
                                else -> R.drawable.trackside_discipline_conditioning
                            }),
                            tint = PlanBuilderPalette.accent, contentDescription = null,
                            modifier = Modifier.size(19.dp)
                                .testTag("discipline-icon-"+discipline)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(name, color = white, fontSize = 14.sp,
                            fontWeight = FontWeight.Bold)
                        Text(desc, color = muted, fontSize = 10.5f.sp)
                    }
                }
            }
            if (i < entries.lastIndex) Spacer(Modifier.height(8.dp))
        }
        val chosen = listOf(request.runDays, request.strengthDays,
            request.calDays, request.conditioningDays).count { it > 0 }
        Spacer(Modifier.height(13.dp))
        Text("$chosen selected", Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center, color = muted, fontSize = 11.sp)
    }
}

@Composable
private fun TracksideOption(
    label: String, description: String, selected: Boolean,
    icon: Int, onClick: () -> Unit, height: Int = 88,
    tag: String = label, runningGoal: Boolean = false
) {
    // Race-goal options retain their existing appearance. The approved
    // Running Goal 551:603 uses separate icon assets and no checkmark.
    val cardSize = if (runningGoal) Modifier.heightIn(min = height.dp)
        else Modifier.height(height.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().then(cardSize)
            .testTag("builder-option-$tag"),
        onClick = onClick,
        color = if (selected && runningGoal) Color(0xFF1D240B)
            else if (selected) darkWine else slate,
        shape = RoundedCornerShape(if (runningGoal) 14.dp else 13.dp),
        border = BorderStroke(1.dp,
            if (selected && runningGoal) PlanBuilderPalette.accent
            else if (selected) PlanBuilderPalette.accent else outline)
    ) {
        Row(
            Modifier.padding(
                horizontal = if (runningGoal) 14.dp else 12.dp,
                vertical = if (runningGoal) 12.dp else 0.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (runningGoal) 13.dp else 14.dp)
        ) {
            Box(
                Modifier.size(if (runningGoal) 41.dp else 43.dp)
                    .background(
                        if (selected && runningGoal) Color(0xFF1D240B)
                        else if (selected) Color(0xFF1D240B) else Color(0xFF1D240B),
                        RoundedCornerShape(11.dp)
                    ), contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(icon), contentDescription = null,
                    tint = PlanBuilderPalette.accent,
                    modifier = Modifier.size(if (runningGoal) 25.dp else 24.dp)
                        .testTag("builder-goal-icon-$tag")
                )
            }
            Column(Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(label,
                    fontSize = if (runningGoal) 16.sp else 15.sp,
                    fontWeight = if (runningGoal) FontWeight.Bold
                        else FontWeight.SemiBold,
                    color = white,
                    maxLines = if (runningGoal) 2 else 1,
                    overflow = TextOverflow.Ellipsis)
                Text(description, fontSize = 11.5f.sp, lineHeight = 14.sp,
                    color = muted, maxLines = if (runningGoal) 3 else 2)
            }
            if (selected && !runningGoal)
                Text("✓", fontSize = 16.sp, color = white)
        }
    }
}

@Composable
internal fun TracksideRunningGoalScreen(
    selected: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val entries = listOf(
        Triple("race", "Train for a race", "5K, 10K, half marathon or marathon"),
        Triple("faster", "Run faster", "Improve pace and running performance"),
        Triple("endurance", "Build endurance", "Run longer and build your aerobic base"),
        Triple("fitness", "General fitness", "Stay active and maintain consistency"),
        Triple("custom", "Train your way", "Set your mileage and workout preferences")
    )
    TracksideBuilderFrame(
        eyebrow = "RUNNING SETUP", headline = "Your running goal",
        description = "What do you want to achieve?",
        onBack = onBack, onContinue = onContinue
    ) {
        // The 8dp stage-specific gap matches the approved choices' starting
        // position without affecting Strength, Calisthenics, or other screens.
        Spacer(Modifier.height(8.dp))
        entries.forEachIndexed { i, (route, label, desc) ->
            TracksideOption(label, desc, selected == route,
                icon = when (route) {
                    "race" -> R.drawable.trackside_running_goal_race
                    "faster" -> R.drawable.trackside_running_goal_faster
                    "endurance" -> R.drawable.trackside_running_goal_endurance
                    "fitness" -> R.drawable.trackside_running_goal_fitness
                    else -> R.drawable.trackside_running_goal_custom
                },
                onClick = { onSelect(route) }, tag = route, runningGoal = true
            )
            if (i < entries.lastIndex) Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun TracksideRaceOption(
    title: String, description: String, selected: Boolean, icon: Int,
    tag: String, onClick: () -> Unit
) {
    // Figma 551:796: native 25dp vectors and 68dp baseline cards.
    // Allow cards to expand rather than clip at accessible font scales.
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp)
            .testTag("builder-option-$tag"),
        onClick = onClick,
        color = if (selected) Color(0xFF1D240B) else Color(0xFF151515),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp,
            if (selected) PlanBuilderPalette.accent else Color(0xFF2B2B2B))
    ) {
        Row(
            Modifier.padding(horizontal = 11.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Box(
                Modifier.width(41.dp).height(43.dp)
                    .background(
                        if (selected) Color(0xFF1D240B) else Color(0xFF1D240B),
                        RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(icon), contentDescription = null,
                    tint = PlanBuilderPalette.accent,
                    modifier = Modifier.size(25.dp)
                        .testTag("builder-race-icon-$tag")
                )
            }
            Column(Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, fontSize = 14.3f.sp, color = white,
                    fontWeight = FontWeight.Bold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Text(description, fontSize = 11.3f.sp, lineHeight = 15.sp,
                    color = muted, maxLines = 3)
            }
            if (selected) Icon(
                painterResource(R.drawable.trackside_race_goal_check),
                contentDescription = null, tint = PlanBuilderPalette.accent,
                modifier = Modifier.size(18.dp)
                    .testTag("builder-race-selected-check")
            )
        }
    }
}

@Composable
internal fun TracksideRaceGoalScreen(
    intent: String, distance: String,
    onIntent: (String) -> Unit, onDistance: (String) -> Unit,
    onBack: () -> Unit, onContinue: () -> Unit
) {
    var unsupportedAction by remember { mutableStateOf<String?>(null) }
    val intents = listOf(
        Triple("pr", "Beat my personal best", "Train to improve your existing PR"),
        Triple("time", "Reach a target time", "Work toward a specific finish time"),
        Triple("finish", "Finish confidently", "Prepare to complete the distance"),
        Triple("benchmark", "Set my first benchmark", "Establish a PR for a new distance")
    )
    TracksideBuilderFrame(
        eyebrow = "RUNNING / RACE & PERFORMANCE",
        headline = "What are you aiming\nfor?",
        description = "Choose what success looks like for you.",
        onBack = onBack,
        onContinue = onContinue,
        // Race reference is more compact than other builder stages.
        descriptionBottomGap = 10,
        // Never silently accept a target-time instruction that the current
        // PlanRequest contract cannot represent or apply.
        continueEnabled = intent != "time"
    ) {
        intents.forEachIndexed { i,(id,title,desc) ->
            TracksideRaceOption(title,desc,intent==id,
                icon=when(id) {
                    "pr" -> R.drawable.trackside_race_goal_pr
                    "time" -> R.drawable.trackside_race_goal_time
                    "finish" -> R.drawable.trackside_race_goal_finish
                    else -> R.drawable.trackside_race_goal_benchmark
                },
                onClick={onIntent(id)}, tag=id
            )
            if(i < intents.lastIndex) Spacer(Modifier.height(9.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("DISTANCE", modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center, fontSize=11.sp,
            color=muted, fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(15.dp,Alignment.CenterHorizontally)) {
            listOf("5K","10K","21K","42K").forEach { dist ->
                Surface(
                    modifier = Modifier.width(63.dp).height(38.dp)
                        .testTag("builder-distance-"+dist),
                    onClick={onDistance(dist)},
                    color=if(distance==dist) Color(0xFF1D240B) else Color.Transparent,
                    shape=RoundedCornerShape(22.dp)
                ) {
                    Box(contentAlignment=Alignment.Center) {
                        Text(dist,fontSize=13.sp,
                            color=if(distance==dist)white else muted,
                            fontWeight=if(distance==dist) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        when(intent) {
            "pr" -> Surface(
                modifier=Modifier.fillMaxWidth().heightIn(min=116.dp)
                    .testTag("builder-race-pr-panel"),
                color=Color(0xFF171717), shape=RoundedCornerShape(13.dp),
                border=BorderStroke(1.dp,Color(0xFF2B2B2B))
            ) {
                Column(Modifier.padding(11.dp),
                    verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("Your current $distance PR",fontSize=15.sp,
                            color=white,fontWeight=FontWeight.Bold)
                        Text("OPTIONAL",fontSize=10.sp,color=muted)
                    }
                    Row(Modifier.fillMaxWidth(),
                        verticalAlignment=Alignment.CenterVertically) {
                        Text("Not entered",fontSize=13.sp,color=muted,
                            modifier=Modifier.weight(1f))
                        Surface(
                            onClick={unsupportedAction="PR entry"},
                            shape=RoundedCornerShape(9.dp),
                            color=Color(0xFF171717),
                            border=BorderStroke(1.dp,Color(0xFF50606D)),
                            modifier=Modifier.heightIn(min=44.dp)
                                .testTag("builder-race-add-pr")
                        ) {
                            Box(contentAlignment=Alignment.Center,
                                modifier=Modifier.padding(horizontal=16.dp)) {
                                Text("Add PR",fontSize=12.sp,color=white)
                            }
                        }
                    }
                    Text("Use a current PR as your baseline; no target time required.",
                        fontSize=10.sp,lineHeight=14.sp,color=muted)
                }
            }
            "time" -> Surface(
                modifier=Modifier.fillMaxWidth().heightIn(min=143.dp),
                color=Color(0xFF171717), shape=RoundedCornerShape(13.dp),
                border=BorderStroke(1.dp,outline)
            ) {
                Column(Modifier.padding(13.dp),
                    verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text("Target finish time",fontSize=13.sp,
                        fontWeight=FontWeight.Bold,color=white)
                    Text("Your goal time for the selected distance.",
                        fontSize=11.sp,color=muted)
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf("HOURS","MINUTES","SECONDS").forEach { label ->
                            Surface(
                                Modifier.weight(1f).height(55.dp),
                                color=Color(0xFF171717),
                                shape=RoundedCornerShape(9.dp),
                                border=BorderStroke(1.dp,outline)
                            ) {
                                Column(horizontalAlignment=Alignment.CenterHorizontally,
                                    verticalArrangement=Arrangement.Center) {
                                    Text(label,fontSize=9.sp,color=muted)
                                    Text("—",fontSize=16.sp,color=white)
                                }
                            }
                        }
                    }
                }
            }
            else -> Surface(
                Modifier.fillMaxWidth().heightIn(min=67.dp),
                color=Color(0xFF171717),shape=RoundedCornerShape(13.dp),
                border=BorderStroke(1.dp,outline)
            ) {
                Box(Modifier.padding(13.dp),contentAlignment=Alignment.CenterStart) {
                    Text("Your goal is to complete the chosen distance.",
                        fontSize=12.sp,color=muted)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("TARGET DAY",fontSize=11.sp,color=muted,
            fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Surface(
            onClick={unsupportedAction="Target date"},
            modifier=Modifier.fillMaxWidth().heightIn(min=44.dp)
                .testTag("builder-race-target-day"),
            color=Color(0xFF0B0B0B),
            shape=RoundedCornerShape(13.dp),
            border=BorderStroke(1.dp,Color(0xFF2B2B2B))
        ) {
            Row(Modifier.padding(horizontal=12.dp),
                verticalAlignment=Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.trackside_race_goal_calendar),
                    contentDescription=null,tint=Color.Unspecified,
                    modifier=Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Choose a date (optional)",fontSize=12.5f.sp,
                    color=muted,modifier=Modifier.weight(1f))
                Icon(painterResource(R.drawable.trackside_race_goal_chevron),
                    contentDescription=null,tint=Color.Unspecified,
                    modifier=Modifier.size(16.dp))
            }
        }
        if(intent=="time") {
            Spacer(Modifier.height(11.dp))
            Text("Target-time prescriptions are not supported by the active " +
                "engine. Choose another goal to continue.",
                fontSize=11.sp,color=Color(0xFFFFA7B5),
                modifier=Modifier.testTag("builder-target-time-unavailable"))
        }
    }
    if(unsupportedAction!=null) AlertDialog(
        onDismissRequest={unsupportedAction=null},
        title={Text(unsupportedAction!!,color=white)},
        text={Text(
            "The current coaching engine does not store this value in " +
                "a saved PlanRequest. No PR or target date has been recorded.",
            color=muted
        )},
        confirmButton={
            TextButton(onClick={unsupportedAction=null}) {
                Text("Close",color=white)
            }
        }
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TracksideAbilityRange(
    header: String, title: String, note: String,
    value: Double, maxValue: Float, unit: String, tag: String,
    onValue: (Double) -> Unit
) {
    Text(header, fontSize=11.sp, color=muted, fontWeight=FontWeight.Bold)
    Spacer(Modifier.height(11.dp))
    Surface(Modifier.fillMaxWidth().heightIn(min=127.dp)
        .testTag("builder-ability-$tag-card"),
        color=Color(0xFF171717),shape=RoundedCornerShape(13.dp),
        border=BorderStroke(1.dp,outline)) {
        // Keep the default accessible Material slider hit area while using
        // the compact interior inset from the approved 127dp Figma metric.
        Column(Modifier.padding(start=14.dp,end=14.dp,top=13.dp,bottom=0.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                Text(title,color=white,fontSize=12.sp,fontWeight=FontWeight.Bold)
                Text(note,color=muted,fontSize=10.sp)
            }
            Spacer(Modifier.height(1.dp))
            Row(verticalAlignment=Alignment.Bottom) {
                Text(String.format(Locale.getDefault(),"%.0f",value),
                    fontSize=31.sp,fontWeight=FontWeight.Bold,color=white)
                Spacer(Modifier.width(13.dp))
                Text(unit,color=muted,fontSize=11.sp,
                    modifier=Modifier.padding(bottom=6.dp))
            }
            Slider(
                value=value.toFloat().coerceIn(0f,maxValue),
                onValueChange={ onValue(it.toDouble()) },
                valueRange=0f..maxValue,
                modifier=Modifier.fillMaxWidth().height(29.dp)
                    .testTag("builder-ability-$tag-slider"),
                // Material3 supplies the drag, keyboard and accessibility actions.
                // Draw only the 5dp track and 15dp circular thumb from Figma 551:865.
                thumb={ _ ->
                    Box(Modifier.size(15.dp).background(
                        PlanBuilderPalette.accent,RoundedCornerShape(50)))
                },
                track={ _ ->
                    Box(Modifier.fillMaxWidth().height(5.dp)
                        .background(Color(0xFF40515D),RoundedCornerShape(3.dp))) {
                        Box(Modifier.fillMaxWidth(
                            (value.toFloat()/maxValue).coerceIn(0f,1f))
                            .height(5.dp).background(
                                PlanBuilderPalette.accent,RoundedCornerShape(3.dp)))
                    }
                }
            )
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("0 km",fontSize=9.sp,color=muted)
                Text(String.format(Locale.getDefault(),"%.0f km",maxValue/2),
                    fontSize=9.sp,color=muted)
                Text(String.format(Locale.getDefault(),"%.0f km",maxValue),
                    fontSize=9.sp,color=muted)
            }
        }
    }
}

@Composable
internal fun TracksideRunningAbilityScreen(
    experience: Int, weeklyKm: Double, longestKm: Double,
    onExperience: (Int) -> Unit,
    onWeeklyKm: (Double) -> Unit,
    onLongestKm: (Double) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onAdvanced: () -> Unit
) {
    var resultInfoOpen by remember { mutableStateOf(false) }
    TracksideBuilderFrame(
        eyebrow="RUNNING / CURRENT ABILITY",
        headline="Where are you\nstarting?",
        description="Use your recent training, not the mileage you hope to reach.",
        onBack=onBack,onContinue=onContinue,
        descriptionBottomGap=11,
        contentTopPadding=61,
        headlineLineHeight=30
    ) {
        Text("RUNNING EXPERIENCE", fontSize=11.sp, color=muted,
            fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(11.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("Beginner","Intermediate","Advanced").forEachIndexed { i,label ->
                Surface(
                    modifier=Modifier.weight(1f).height(45.dp)
                        .testTag("builder-experience-$i"),
                    onClick={onExperience(i)},
                    color=if(experience==i) darkWine else Color(0xFF171717),
                    border=BorderStroke(1.dp,
                        if(experience==i) PlanBuilderPalette.accent else outline),
                    shape=RoundedCornerShape(9.dp)
                ) {
                    Box(contentAlignment=Alignment.Center) {
                        Text(label,fontSize=11.sp,color=white,
                            fontWeight=if(experience==i) FontWeight.SemiBold
                                else FontWeight.Normal)
                    }
                }
            }
        }
        Spacer(Modifier.height(13.dp))
        TracksideAbilityRange(
            header="CURRENT WEEKLY DISTANCE",title="Typical weekly volume",
            note="Last 4–6 weeks",value=weeklyKm,maxValue=120f,unit="km / week",
            tag="weekly",
            onValue=onWeeklyKm
        )
        Spacer(Modifier.height(15.dp))
        TracksideAbilityRange(
            header="LONGEST RECENT RUN",title="Longest run completed",
            note="Recent training",value=longestKm,maxValue=42f,unit="km",
            tag="longest",
            onValue=onLongestKm
        )
        Spacer(Modifier.height(11.dp))
        Text("RECENT PERFORMANCE  (OPTIONAL)",fontSize=11.sp,
            color=muted,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(9.dp))
        Surface(
            modifier=Modifier.fillMaxWidth().heightIn(min=72.dp)
                .testTag("builder-ability-optional-card"),
            shape=RoundedCornerShape(12.dp),color=Color(0xFF171717),
            border=BorderStroke(1.dp,outline)
        ) {
            Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f),
                    verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Race or time trial result",fontSize=12.sp,
                        fontWeight=FontWeight.Bold,color=white)
                    Text("Helps estimate appropriate training intensities.",
                        fontSize=10.sp,color=muted)
                }
                TextButton(onClick={resultInfoOpen=true},
                    modifier=Modifier.testTag("builder-ability-add-result")) {
                    Text("Add result",fontSize=11.sp,color=white)
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        TextButton(onClick=onAdvanced,modifier=Modifier.testTag("builder-advanced-settings")) {
            Text("Advanced training settings ›",fontSize=11.sp,color=muted)
        }
    }
    if (resultInfoOpen) AlertDialog(
        onDismissRequest={resultInfoOpen=false},
        title={Text("Recent performance",color=white)},
        text={Text("This coaching engine does not yet store race or time-trial results in the plan request. Your weekly volume, longest run and editable planning pace remain available.",color=muted)},
        confirmButton={
            TextButton(onClick={resultInfoOpen=false;onAdvanced()}) {
                Text("Edit advanced settings",color=white)
            }
        },
        dismissButton={
            TextButton(onClick={resultInfoOpen=false}) { Text("Close",color=muted) }
        }
    )
}
