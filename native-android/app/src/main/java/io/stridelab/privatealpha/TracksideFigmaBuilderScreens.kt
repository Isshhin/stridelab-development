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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
            Text("Generate Training Plan", fontSize = 16.sp,
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
            Spacer(Modifier.height(4.dp))
            Text(description, fontSize = 12.5f.sp,
                lineHeight = 17.sp, color = muted)
            Spacer(Modifier.height(40.dp))
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
                Text("Continue", fontSize = 15.sp, color = white,
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
                Text("2 of 4", fontSize = 11.sp, color = muted)
            }
        }
    ) {
        entries.forEachIndexed { i, (discipline, name, desc) ->
            val selected = when(discipline) {
                "run" -> request.runDays > 0
                "str" -> request.strengthDays > 0
                "cal" -> request.calDays > 0
                else -> request.conditioningDays > 0
            }
            val accent = tracksideDisciplineColor(discipline)
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
                Row(Modifier.padding(horizontal = 21.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(when(discipline) {
                                "run" -> R.drawable.trackside_figma_run
                                "str" -> R.drawable.trackside_figma_activity
                                "cal" -> R.drawable.trackside_figma_loop
                                else -> R.drawable.trackside_figma_activity
                            }),
                            tint = accent, contentDescription = null,
                            modifier = Modifier.size(27.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(name, color = white, fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold)
                        Text(desc, color = muted, fontSize = 12.sp)
                    }
                }
            }
            if (i < entries.lastIndex) Spacer(Modifier.height(11.dp))
        }
    }
}

@Composable
private fun TracksideFigmaOption(
    label: String, description: String, selected: Boolean,
    icon: Int, onClick: () -> Unit, height: Int = 88,
    tag: String = label
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(height.dp)
            .testTag("builder-option-$tag"),
        onClick = onClick,
        color = if(selected) darkWine else slate,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, if(selected) Color(0xFFE36676) else outline)
    ) {
        Row(Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(43.dp).background(
                if (selected) Color(0xFF6B3645) else Color(0xFF243643),
                RoundedCornerShape(11.dp)
            ), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = null,
                    tint = if(selected) Color(0xFFFFA7AD) else Color(0xFFA3BECE),
                    modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = white, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(description, fontSize = 11.5f.sp, lineHeight = 14.sp,
                    color = muted, maxLines = 2)
            }
            if (selected) Text("✓", fontSize = 16.sp, color = white)
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
        entries.forEachIndexed { i, (route, label, desc) ->
            TracksideFigmaOption(label, desc, selected == route,
                icon = when(route) {
                    "race" -> R.drawable.trackside_figma_calendar
                    "faster" -> R.drawable.trackside_figma_run
                    "endurance" -> R.drawable.trackside_figma_activity
                    "fitness" -> R.drawable.trackside_figma_activity
                    else -> R.drawable.trackside_figma_edit
                },
                onClick = { onSelect(route) }, tag = route
            )
            if (i < entries.lastIndex) Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun TracksideFigmaRaceGoalScreen(
    intent: String, distance: String,
    onIntent: (String) -> Unit, onDistance: (String) -> Unit,
    onBack: () -> Unit, onContinue: () -> Unit
) {
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
        onBack = onBack, onContinue = onContinue
    ) {
        intents.forEachIndexed { i,(id,title,desc) ->
            TracksideFigmaOption(title,desc,intent==id,
                icon=when(id) {
                    "time" -> R.drawable.trackside_figma_activity
                    "finish" -> R.drawable.trackside_figma_calendar
                    else -> R.drawable.trackside_figma_run
                },
                onClick={onIntent(id)}, height=68, tag=id
            )
            if(i < intents.lastIndex) Spacer(Modifier.height(9.dp))
        }
        Spacer(Modifier.height(27.dp))
        Text("DISTANCE", modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center, fontSize=11.sp,
            color=muted, fontWeight=FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            listOf("5K","10K","21K","42K").forEach { dist ->
                Surface(
                    modifier = Modifier.width(72.dp).height(38.dp)
                        .testTag("builder-distance-$dist"),
                    onClick={onDistance(dist)},
                    color=if(distance==dist) darkWine else Color.Transparent,
                    shape=RoundedCornerShape(22.dp)
                ) {
                    Box(contentAlignment=Alignment.Center) {
                        Text(dist,fontSize=13.sp,color=white,
                            fontWeight=if(distance==dist) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
        Spacer(Modifier.height(11.dp))
        Surface(
            modifier=Modifier.fillMaxWidth().heightIn(min=102.dp),
            color=Color(0xFF162A36), shape=RoundedCornerShape(13.dp),
            border=BorderStroke(1.dp,outline)
        ) {
            Column(Modifier.padding(13.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("CURRENT $distance PERFORMANCE",fontSize=11.sp,
                        color=white,fontWeight=FontWeight.Bold)
                    Text("OPTIONAL",fontSize=10.sp,color=muted)
                }
                Text("No verified personal best entered",fontSize=12.sp,color=muted)
                Text("The current coaching engine does not accept a target finish time. No target is added to the plan.",
                    fontSize=10.sp,lineHeight=14.sp,color=muted)
            }
        }
    }
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
                TextButton(onClick=onAdvanced) { Text("Add result",fontSize=11.sp,color=white) }
            }
        }
    }
}
