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
private val black = Color(0xFF000000)
private val outline = Color(0xFF344956)
private val slate = Color(0xFF101922)
private val darkWine = Color(0xFF54232F)
private val fRed = Color(0xFFC6313C)
private val white = Color(0xFFF7FAFF)
private val muted = Color(0xFFB6C0CA)

@Composable
internal fun TracksideFigmaBuilderFrame(
    eyebrow: String,
    toolbarTitle: String = "Generate Training Plan",
    ctaLabel: String = "Continue",
    headline: String,
    description: String,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    continueEnabled: Boolean = true,
    footer: (@Composable () -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxSize().background(black).testTag("figma-builder-frame")) {
        Row(
            Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(
                modifier = Modifier.size(28.dp).testTag("builder-figma-back"),
                onClick = onBack,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("‹", color = white, fontSize = 30.sp, lineHeight = 32.sp)
            }
            Text(toolbarTitle, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold, color = white)
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.dp)
            .background(Color(0xFF20303A)))
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 47.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Text(eyebrow, fontSize = 11.sp, lineHeight = 14.sp,
                fontWeight = FontWeight.Bold, color = Color(0xFFFF8E89))
            Spacer(Modifier.height(19.dp))
            Text(headline, fontSize = 30.sp, lineHeight = 35.sp,
                fontWeight = FontWeight.Bold, color = white,
                modifier = Modifier.fillMaxWidth())
            if(description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(description, fontSize = 12.5f.sp,
                    lineHeight = 17.sp, color = muted)
                Spacer(Modifier.height(40.dp))
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
            colors = ButtonDefaults.buttonColors(containerColor = fRed)
        ) {
            Box(Modifier.fillMaxSize()) {
                Text(ctaLabel, fontSize = 15.sp, color = white,
                    modifier = Modifier.align(Alignment.Center))
                Text("→", fontSize = 25.sp, color = white,
                    modifier = Modifier.align(Alignment.CenterEnd))
            }
        }
        Spacer(Modifier.height(15.dp))
    }
}

@Composable
internal fun TracksideFigmaDisciplineScreen(
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
    TracksideFigmaBuilderFrame(
        eyebrow = "BUILD YOUR PLAN",
        headline = "Choose your\ndisciplines",
        description = "Choose the disciplines you want to train.",
        onBack = onBack,
        onContinue = onContinue,
        continueEnabled = request.runDays + request.strengthDays +
            request.calDays + request.conditioningDays > 0,
        footer = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 17.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) { index ->
                    Box(Modifier.padding(end = 6.dp).size(37.dp, 5.dp)
                        .background(
                            when(index) {
                                0 -> Color(0xFFC6314A)
                                1 -> Color(0xFF488BD0)
                                else -> Color(0xFF334550)
                            },
                            RoundedCornerShape(5.dp)
                        ))
                }
                Spacer(Modifier.weight(1f))
                val chosen = listOf(request.runDays, request.strengthDays,
                    request.calDays, request.conditioningDays).count { it > 0 }
                Text("$chosen of 4", fontSize = 11.sp, color = muted)
            }
        }
    ) {
        // Figma 551:580 has a larger description-to-card gap than other
        // builder stages. Keep this adjustment local to discipline selection.
        Spacer(Modifier.height(23.dp))
        entries.forEachIndexed { i, (discipline, name, desc) ->
            val selected = when(discipline) {
                "run" -> request.runDays > 0
                "str" -> request.strengthDays > 0
                "cal" -> request.calDays > 0
                else -> request.conditioningDays > 0
            }
            val accent = when(discipline) {
                "run" -> Color(0xFFF18B7E)
                "str" -> Color(0xFF78AFFF)
                "cal" -> Color(0xFFB394F0)
                else -> Color(0xFFE5C478)
            }
            val selectedBg = if (discipline == "run") darkWine
                else if (discipline == "str") Color(0xFF203E60)
                else Color(0xFF332742)
            Surface(
                modifier = Modifier.fillMaxWidth().height(87.dp)
                    .testTag("discipline-$discipline"),
                onClick = { onToggle(discipline) },
                shape = RoundedCornerShape(13.dp),
                color = if(selected) selectedBg else slate,
                border = BorderStroke(1.dp, if(selected) accent else outline)
            ) {
                Row(Modifier.padding(horizontal = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(when(discipline) {
                                "run" -> R.drawable.trackside_discipline_running
                                "str" -> R.drawable.trackside_discipline_strength
                                "cal" -> R.drawable.trackside_discipline_calisthenics
                                else -> R.drawable.trackside_discipline_conditioning
                            }),
                            tint = Color.Unspecified, contentDescription = null,
                            modifier = Modifier.size(24.dp)
                                .testTag("discipline-icon-"+discipline)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(name, color = white, fontSize = 17.sp,
                            fontWeight = FontWeight.Bold)
                        Text(desc, color = muted, fontSize = 12.sp)
                    }
                }
            }
            if (i < entries.lastIndex) Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TracksideFigmaOption(
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
        color = if (selected && runningGoal) Color(0xFF51202E)
            else if (selected) darkWine else slate,
        shape = RoundedCornerShape(if (runningGoal) 14.dp else 13.dp),
        border = BorderStroke(1.dp,
            if (selected && runningGoal) Color(0xFFF18B7E)
            else if (selected) Color(0xFFE36676) else outline)
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
                        if (selected && runningGoal) Color(0xFF6B313F)
                        else if (selected) Color(0xFF6B3645) else Color(0xFF243643),
                        RoundedCornerShape(11.dp)
                    ), contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(icon), contentDescription = null,
                    tint = if (runningGoal) Color.Unspecified
                        else if (selected) Color(0xFFFFA7AD)
                        else Color(0xFFA3BECE),
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
internal fun TracksideFigmaRunningGoalScreen(
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
    TracksideFigmaBuilderFrame(
        eyebrow = "RUNNING SETUP", headline = "What's your\nrunning goal?",
        description = "Your training plan starts with what you want to achieve.",
        onBack = onBack, onContinue = onContinue
    ) {
        // The 8dp stage-specific gap matches the approved choices' starting
        // position without affecting Strength, Calisthenics, or other screens.
        Spacer(Modifier.height(8.dp))
        entries.forEachIndexed { i, (route, label, desc) ->
            TracksideFigmaOption(label, desc, selected == route,
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
private fun TracksideFigmaRaceOption(
    title: String, description: String, selected: Boolean, icon: Int,
    tag: String, onClick: () -> Unit
) {
    // Figma 551:796: native 25dp vectors and 68dp baseline cards.
    // Allow cards to expand rather than clip at accessible font scales.
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp)
            .testTag("builder-option-$tag"),
        onClick = onClick,
        color = if (selected) Color(0xFF532631) else Color(0xFF15212B),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp,
            if (selected) Color(0xFFB86674) else Color(0xFF354652))
    ) {
        Row(
            Modifier.padding(horizontal = 11.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Box(
                Modifier.width(41.dp).height(43.dp)
                    .background(
                        if (selected) Color(0xFF753E4C) else Color(0xFF243540),
                        RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(icon), contentDescription = null,
                    tint = Color.Unspecified,
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
                contentDescription = null, tint = Color.Unspecified,
                modifier = Modifier.size(18.dp)
                    .testTag("builder-race-selected-check")
            )
        }
    }
}

@Composable
internal fun TracksideFigmaRaceGoalScreen(
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
    TracksideFigmaBuilderFrame(
        eyebrow = "RUNNING / RACE & PERFORMANCE",
        headline = "What are you aiming\nfor?",
        description = "Choose what success looks like for you.",
        onBack = onBack,
        onContinue = onContinue,
        // Never silently accept a target-time instruction that the current
        // PlanRequest contract cannot represent or apply.
        continueEnabled = intent != "time"
    ) {
        intents.forEachIndexed { i,(id,title,desc) ->
            TracksideFigmaRaceOption(title,desc,intent==id,
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
        Spacer(Modifier.height(25.dp))
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
                    color=if(distance==dist) Color(0xFF652735) else Color.Transparent,
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
                color=Color(0xFF15232C), shape=RoundedCornerShape(13.dp),
                border=BorderStroke(1.dp,Color(0xFF354A55))
            ) {
                Column(Modifier.padding(13.dp),
                    verticalArrangement=Arrangement.spacedBy(12.dp)) {
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
                            color=Color(0xFF293945),
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
                color=Color(0xFF162A36), shape=RoundedCornerShape(13.dp),
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
                                color=Color(0xFF203742),
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
                color=Color(0xFF162A36),shape=RoundedCornerShape(13.dp),
                border=BorderStroke(1.dp,outline)
            ) {
                Box(Modifier.padding(13.dp),contentAlignment=Alignment.CenterStart) {
                    Text("Your goal is to complete the chosen distance.",
                        fontSize=12.sp,color=muted)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("TARGET DAY",fontSize=11.sp,color=muted,
            fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Surface(
            onClick={unsupportedAction="Target date"},
            modifier=Modifier.fillMaxWidth().heightIn(min=44.dp)
                .testTag("builder-race-target-day"),
            color=Color(0xFF080D12),
            shape=RoundedCornerShape(13.dp),
            border=BorderStroke(1.dp,Color(0xFF35444F))
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

@Composable
private fun TracksideFigmaAbilityRange(
    header: String, title: String, note: String,
    value: Double, maxValue: Float, unit: String, onValue: (Double) -> Unit
) {
    Text(header, fontSize=11.sp, color=muted, fontWeight=FontWeight.Bold)
    Spacer(Modifier.height(11.dp))
    Surface(Modifier.fillMaxWidth().height(127.dp),
        color=Color(0xFF1A2D38),shape=RoundedCornerShape(13.dp),
        border=BorderStroke(1.dp,outline)) {
        Column(Modifier.padding(horizontal=14.dp,vertical=13.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                Text(title,color=white,fontSize=12.sp,fontWeight=FontWeight.Bold)
                Text(note,color=muted,fontSize=10.sp)
            }
            Spacer(Modifier.height(9.dp))
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
                colors=SliderDefaults.colors(
                    thumbColor=Color(0xFFEE6E88), activeTrackColor=Color(0xFFCF3755),
                    inactiveTrackColor=Color(0xFF4C6674)
                ),
                modifier=Modifier.fillMaxWidth().height(29.dp)
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
internal fun TracksideFigmaRunningAbilityScreen(
    experience: Int, weeklyKm: Double, longestKm: Double,
    onExperience: (Int) -> Unit,
    onWeeklyKm: (Double) -> Unit,
    onLongestKm: (Double) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onAdvanced: () -> Unit
) {
    var resultInfoOpen by remember { mutableStateOf(false) }
    TracksideFigmaBuilderFrame(
        eyebrow="RUNNING / CURRENT ABILITY",
        headline="Where are you\nstarting?",
        description="Use your recent training, not the mileage you hope to reach.",
        onBack=onBack,onContinue=onContinue
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
                    color=if(experience==i) darkWine else Color(0xFF1A2B36),
                    border=BorderStroke(1.dp,
                        if(experience==i) Color(0xFFB47686) else outline),
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
        Spacer(Modifier.height(27.dp))
        TracksideFigmaAbilityRange(
            header="CURRENT WEEKLY DISTANCE",title="Typical weekly volume",
            note="Last 4–6 weeks",value=weeklyKm,maxValue=120f,unit="km / week",
            onValue=onWeeklyKm
        )
        Spacer(Modifier.height(27.dp))
        TracksideFigmaAbilityRange(
            header="LONGEST RECENT RUN",title="Longest run completed",
            note="Recent training",value=longestKm,maxValue=42f,unit="km",
            onValue=onLongestKm
        )
        Spacer(Modifier.height(24.dp))
        Text("RECENT PERFORMANCE  (OPTIONAL)",fontSize=11.sp,
            color=muted,fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(11.dp))
        Surface(
            modifier=Modifier.fillMaxWidth().height(75.dp),
            shape=RoundedCornerShape(12.dp),color=Color(0xFF172A36),
            border=BorderStroke(1.dp,outline)
        ) {
            Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f),
                    verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Race or time trial result",fontSize=12.sp,
                        fontWeight=FontWeight.Bold,color=white)
                    Text("Helps estimate appropriate training intensities.",
                        fontSize=10.sp,color=muted)
                }
                TextButton(onClick={resultInfoOpen=true}) {
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
