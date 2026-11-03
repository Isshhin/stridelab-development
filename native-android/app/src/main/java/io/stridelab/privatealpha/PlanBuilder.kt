@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.stridelab.privatealpha

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONObject

@Composable internal fun PlanBuilder(state:TrainingState,actions:PlanActions,discipline:String="all",onDone:()->Unit,onSignIn:()->Unit,onPreview:(String,String)->Unit) {
    if(state.account==null) { Screen("Create a plan","TRAINING") { item { SignalCard { Text("A watch is optional.",style=MaterialTheme.typography.headlineSmall);Text("Sign in once to keep plans and logs private to your account. You can then build, approve and manage plans offline.");SignalButton("Sign in",onSignIn) } } };return }
    val baseline=remember { state.plans.firstOrNull { it.policyId in setOf(ENGINE1_POLICY,CoordinatedEngine1.policyId,MultiSessionEngine1.policyId) }?.request ?: PlanRequest(policyChoice="coordinated",weeklyKm=historyWeeklyKm(state.activities,plans=state.plans) ?: 0.0,longestKm=state.activities.filter { DashboardSummary.isRun(it) }.mapNotNull { it.distanceM }.maxOrNull()?.div(1000) ?: 0.0) }
    val initial=remember { when(discipline) { "run"->baseline.copy(runDays=3,strengthDays=0,calDays=0,conditioningDays=0);"str"->baseline.copy(goal="General strength",runDays=0,strengthDays=2,calDays=0,conditioningDays=0,qualityCount=0);"cal"->baseline.copy(goal="Calisthenics foundation",runDays=0,strengthDays=0,calDays=2,conditioningDays=0,qualityCount=0);else->baseline }.let { request ->
        if(state.plans.isEmpty()) request.copy(
            days=setOf(1,2,3,4,5,7), longDay=7,
            qualityDays=request.qualityDays.intersect(setOf(1,2,3,4,5,7))
        ) else request
    } }
    var encoded by rememberSaveable { mutableStateOf(initial.encode().toString()) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    val input=PlanRequest.decode(JSONObject(encoded))
    fun edit(p:PlanRequest) { encoded=p.encode().toString() }
    var km by rememberSaveable { mutableStateOf(initial.weeklyKm.toString()) };var longest by rememberSaveable { mutableStateOf(initial.longestKm.toString()) }
    var pace by rememberSaveable { mutableStateOf((initial.planningPace/60).toString()) };var start by rememberSaveable { mutableStateOf(maxOf(initial.start,LocalDate.now()).toString()) }
    var weeks by rememberSaveable { mutableStateOf(initial.weeks.toString()) };var minutes by rememberSaveable { mutableStateOf(initial.minutes.toString()) };var dailyMinutes by rememberSaveable { mutableStateOf(initial.dailyMinutes.toString()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) };var awaiting by rememberSaveable { mutableStateOf(false) };var beforeId by rememberSaveable { mutableStateOf<String?>(null) };var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.busy,state.plans,state.message) { if(awaiting&&!state.busy) {
        val newest=state.plans.firstOrNull();if(newest!=null&&newest.id!=beforeId) { previewId=newest.id;step=4;awaiting=false } else if(state.message!=null) awaiting=false
    } }
    fun request():PlanRequest=try { input.copy(start=LocalDate.parse(start),weeks=weeks.toInt(),minutes=minutes.toInt(),dailyMinutes=if(input.policyChoice=="multi-session")dailyMinutes.toInt() else input.dailyMinutes,weeklyKm=km.toDouble(),longestKm=longest.toDouble(),planningPace=pace.toDouble()*60) } catch(e:Exception) { throw PlanConstraint("Enter a valid YYYY-MM-DD date and numeric mileage, pace, weeks, minutes, and daily budget if enabled.") }
    var previewWeek by rememberSaveable { mutableIntStateOf(0) }
    var runningSubPage by rememberSaveable { mutableIntStateOf(0) }
    var scheduleSubPage by rememberSaveable { mutableIntStateOf(0) }
    var runningSelectedDay by rememberSaveable { mutableIntStateOf(initial.longDay.coerceIn(1,7)) }
    var runningCustom by rememberSaveable { mutableStateOf(true) }
    var qualityPreference by rememberSaveable { mutableStateOf("Threshold") }
    var advancedQualityCsv by rememberSaveable { mutableStateOf("Threshold|Tempo") }
    val advancedQualityTypes = advancedQualityCsv.split('|').filter { it.isNotBlank() }.toSet()
    var advancedBackPage by rememberSaveable { mutableIntStateOf(0) }
    var guidedFocus by rememberSaveable { mutableStateOf("10K") }
    var runningRoute by rememberSaveable { mutableStateOf("race") }
    var raceIntent by rememberSaveable { mutableStateOf("pr") }
    var raceDistance by rememberSaveable { mutableStateOf(when(input.goal) {
        "Improve 10K" -> "10K"
        "Half marathon" -> "21K"
        "Marathon" -> "42K"
        else -> "5K"
    }) }
    val preview=state.plans.find { it.id==previewId }
    if (step == 4 && preview != null) {
        TracksideFigmaWeekReview(
            request = preview.request,
            plan = preview,
            busy = state.busy,
            onBack = { step = 3 },
            onContinue = {
                actions.approve(preview.id, preview.revision)
                onDone()
            },
            onOpenSession = { sessionId -> onPreview(preview.id, sessionId) },
            weekIndex = previewWeek,
            onWeekChange = { previewWeek = it }
        )
        return
    }
    if (step == 0) {
        TracksideFigmaDisciplineScreen(
            request = input,
            onToggle = { d ->
                val active = when(d) {
                    "run" -> input.runDays > 0
                    "str" -> input.strengthDays > 0
                    "cal" -> input.calDays > 0
                    else -> input.conditioningDays > 0
                }
                edit(when(d) {
                    "run" -> input.copy(runDays=if(active)0 else 3,qualityCount=0)
                    "str" -> input.copy(strengthDays=if(active)0 else 2)
                    "cal" -> input.copy(calDays=if(active)0 else 2)
                    else -> input.copy(conditioningDays=if(active)0 else 1)
                })
            },
            onBack = onDone,
            onContinue = { step = 1; runningSubPage = 0 }
        )
        return
    }
    if (step == 1 && input.runDays > 0) {
        when (runningSubPage) {
            0 -> TracksideFigmaRunningGoalScreen(
                selected = runningRoute,
                onSelect = { route ->
                    runningRoute = route
                    guidedFocus = when(route) {
                        "faster" -> "10K"
                        "endurance" -> "Run longer"
                        "fitness" -> "Consistency"
                        else -> guidedFocus
                    }
                },
                onBack = { step = 0 },
                onContinue = {
                    when (runningRoute) {
                        "race" -> runningSubPage = 1
                        "custom" -> {
                            advancedBackPage = 0
                            runningSubPage = 3
                        }
                        else -> runningSubPage = 5
                    }
                }
            )
            1 -> TracksideFigmaRaceGoalScreen(
                intent = raceIntent, distance = raceDistance,
                onIntent = { raceIntent = it }, onDistance = { raceDistance = it },
                onBack = { runningSubPage = 0 },
                onContinue = {
                    val goal = when(raceDistance) {
                        "10K" -> "Improve 10K"
                        "21K" -> "Half marathon"
                        "42K" -> "Marathon"
                        else -> "Improve 5K"
                    }
                    edit(input.copy(goal=goal,
                        disciplineGoals=input.disciplineGoals+("run" to goal)))
                    runningSubPage = 2
                }
            )
            2 -> TracksideFigmaRunningAbilityScreen(
                experience = input.experience,
                weeklyKm = km.toDoubleOrNull()?.coerceIn(0.0,120.0) ?: 0.0,
                longestKm = longest.toDoubleOrNull()?.coerceIn(0.0,42.0) ?: 0.0,
                onExperience = { edit(input.copy(experience=it)) },
                onWeeklyKm = { km = it.toInt().toString() },
                onLongestKm = { longest = it.toInt().toString() },
                onBack = {
                    runningSubPage = when(runningRoute) {
                        "race" -> 1
                        "custom" -> 3
                        else -> 5
                    }
                },
                onContinue = { runningSubPage = 4 },
                onAdvanced = {
                    advancedBackPage = 2
                    runningSubPage = 3
                }
            )
            3 -> TracksideFigmaAdvancedRunning(
                guided = advancedBackPage != 0,
                weeklyKm = km.toDoubleOrNull()?.coerceIn(0.0, 120.0) ?: 0.0,
                qualityCount = input.qualityCount.coerceIn(0, 2),
                maxQualityCount = minOf(
                    (input.runDays-2).coerceAtLeast(0),
                    if(input.experience==2 && input.runDays>=4 &&
                        (km.toDoubleOrNull() ?: 0.0)>=30.0) 2 else 1
                ),
                selectedQualityTypes = advancedQualityTypes,
                onWeeklyKm = { km = it.toInt().toString() },
                onQualityCount = { n ->
                    val allowed = minOf(
                        (input.runDays-2).coerceAtLeast(0),
                        if(input.experience==2 && input.runDays>=4 &&
                            (km.toDoubleOrNull() ?: 0.0)>=30.0) 2 else 1
                    )
                    edit(input.copy(qualityCount=n.coerceAtMost(allowed)))
                },
                onToggleQuality = { quality ->
                    val updated =
                        if (quality in advancedQualityTypes) advancedQualityTypes-quality
                        else advancedQualityTypes+quality
                    advancedQualityCsv = updated.sorted().joinToString("|")
                },
                onBack = { runningSubPage = advancedBackPage },
                onContinue = {
                    runningSubPage = if (advancedBackPage == 5) 5 else 2
                }
            )
            5 -> TracksideFigmaGuidedRunning(
                route = runningRoute,
                selectedFocus = guidedFocus,
                onFocus = { guidedFocus = it },
                weeklyKm = km.toDoubleOrNull()?.coerceIn(0.0,120.0) ?: 0.0,
                experience = input.experience,
                onEditAbility = { runningSubPage = 2 },
                onAdvanced = {
                    advancedBackPage = 5
                    runningSubPage = 3
                },
                onBack = { runningSubPage = 0 },
                onContinue = {
                    val goal = when(runningRoute) {
                        "faster" -> when(guidedFocus) {
                            "10K" -> "Improve 10K"
                            "21K" -> "Half marathon"
                            else -> "Improve 5K"
                        }
                        "endurance" -> "Aerobic foundation"
                        else -> "General fitness"
                    }
                    edit(input.copy(
                        goal=goal,
                        disciplineGoals=input.disciplineGoals+("run" to goal)
                    ))
                    runningSubPage = 2
                }
            )
            4 -> TracksideFigmaRunningSetup(
                request = input,
                selectedDay = runningSelectedDay,
                onSelectedDay = { runningSelectedDay = it },
                custom = runningCustom,
                onCustom = { runningCustom = it },
                qualityPreference = qualityPreference,
                onQualityPreference = { qualityPreference = it },
                onRunningCount = { n ->
                    edit(input.copy(runDays=n,
                        qualityCount=input.qualityCount.coerceAtMost((n-1).coerceAtLeast(0))))
                },
                onChooseType = { day, type ->
                    when(type) {
                        "Long run" -> {
                            val remaining=input.qualityDays-day
                            edit(input.copy(
                                longDay=day,
                                qualityDays=remaining,
                                qualityCount=input.qualityCount.coerceAtMost(
                                    remaining.size)))
                        }
                        "Quality" -> {
                            val futureLong=if(input.longDay==day)
                                ((input.days-day).maxOrNull() ?: input.longDay)
                                else input.longDay
                            val allowed = minOf(
                                (input.runDays-2).coerceAtLeast(0),
                                if(input.experience==2 && input.runDays>=4 &&
                                    (km.toDoubleOrNull() ?: 0.0)>=30.0) 2 else 1
                            )
                            val desired=((input.qualityDays+day)-futureLong)
                                .sorted().take(allowed).toSet()
                            edit(input.copy(
                                longDay=futureLong,
                                qualityDays=desired,
                                qualityCount=desired.size))
                        }
                        else -> edit(input.copy(
                            qualityDays=input.qualityDays-day,
                            qualityCount=if((input.qualityDays-day).isEmpty())
                                0 else input.qualityCount))
                    }
                },
                onBack = { runningSubPage = 2 },
                onContinue = { step = 2; scheduleSubPage = 0 }
            )
        }
        return
    }
    if (step == 2 && scheduleSubPage < 2) {
        if(scheduleSubPage == 0) {
            TracksideFigmaPlanDetailsScreen(
                start = start, weeks = weeks, minutes = minutes,
                onStart = { start = it },
                onWeeks = { weeks = it },
                onMinutes = { minutes = it },
                onBack = {
                    step = 1
                    if(input.runDays > 0) runningSubPage = 4
                },
                onContinue = { scheduleSubPage = 1 }
            )
        } else {
            TracksideFigmaAvailabilityScreen(
                request = input,
                onMultiSessionToggle = { enabled ->
                    edit(input.copy(
                        policyChoice = if(enabled) "multi-session" else "coordinated"
                    ))
                },
                onDay = { day ->
                    val days = if(day in input.days) input.days - day
                        else input.days + day
                    edit(input.copy(
                        days = days,
                        longDay = if(input.longDay in days) input.longDay
                            else (days.maxOrNull() ?: input.longDay),
                        qualityDays = input.qualityDays.intersect(days)
                    ))
                },
                onBack = { scheduleSubPage = 0 },
                onContinue = { step = 3 }
            )
        }
        return
    }
    if (step == 3) {
        val previewInputs = runCatching { request() }.getOrDefault(input)
        TracksideFigmaWeekReview(
            request = previewInputs,
            plan = null,
            busy = state.busy || awaiting,
            onBack = { step = 2; scheduleSubPage = 1 },
            onContinue = {
                error = null
                try {
                    val p = request()
                    if (p.start < LocalDate.now())
                        throw PlanConstraint("Choose today or a future start date.")
                    Engine1Engines.validate(p)
                    beforeId = state.plans.firstOrNull()?.id
                    awaiting = true
                    actions.generate(p)
                } catch(e: PlanConstraint) { error = e.message }
            },
            error = error
        )
        return
    }
    key(step) { Screen(when(step) {0->"Choose disciplines";1->"Goals & ability";2->"Your schedule";else->"Review your choices"},"CREATE TRAINING PLAN · ${step+1} / 4") {
        item { LinearProgressIndicator(progress={(step+1)/4f},modifier=Modifier.fillMaxWidth());Text("One coordinated plan · Engine 1.0 · no watch required",style=MaterialTheme.typography.bodySmall) }
        if(step==0) {
            item { Text("Pick the disciplines you want to train. All selected disciplines share one calendar.",style=MaterialTheme.typography.bodyLarge) }
            listOf("run","str","cal","cond").forEach { d -> item {
                val frequency=when(d) {"run"->input.runDays;"str"->input.strengthDays;"cal"->input.calDays;else->input.conditioningDays}
                val toggle={ val n=if(frequency>0)0 else if(d=="run")3 else if(d=="cond")1 else 2;edit(when(d){"run"->input.copy(runDays=n,qualityCount=0);"str"->input.copy(strengthDays=n);"cal"->input.copy(calDays=n);else->input.copy(conditioningDays=n)}) }
                Surface(onClick=toggle,shape=androidx.compose.foundation.shape.RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface,border=androidx.compose.foundation.BorderStroke(if(frequency>0)2.dp else 1.dp,if(frequency>0)disciplineColor(d)else MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.testTag("discipline-$d")) {
                    Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {Text(disciplineLabel(d),style=MaterialTheme.typography.titleLarge,color=disciplineColor(d));Text(if(d=="run") "Build an easy running habit, distance or controlled quality." else if(d=="str") "Equipment-aware resistance prescriptions." else if(d=="cal") "Current ability, supported variations and skill practice." else "Comfortable low-impact conditioning.",style=MaterialTheme.typography.bodyMedium)}
                        Checkbox(frequency>0,{toggle()})
                    }
                }
            } }
        }
        if(step==1) {
            listOf("run" to input.runDays,"str" to input.strengthDays,"cal" to input.calDays,"cond" to input.conditioningDays).filter { it.second>0 }.forEach { (d,f) -> item { Text(disciplineLabel(d)+" sessions per week",style=MaterialTheme.typography.titleLarge);FrequencyChips(f,if(d=="run")6 else if(d=="cond")3 else 4,"frequency-$d") { n -> edit(when(d){"run"->input.copy(runDays=n,qualityCount=0);"str"->input.copy(strengthDays=n);"cal"->input.copy(calDays=n);else->input.copy(conditioningDays=n)}) } } }
            item { Text("Engine 1.0 planning policy",style=MaterialTheme.typography.titleLarge);ChoiceChips(listOf("Coordinated discipline goals","Verified baseline fallback","Multi-session scheduling"),when(input.policyChoice){"coordinated"->0;"multi-session"->2;else->1}) { edit(input.copy(policyChoice=when(it){0->"coordinated";2->"multi-session";else->"baseline"})) };Text("The verified fallback and coordinated policy remain unchanged. Multi-session scheduling uses a separate versioned policy, allows only compatible same-day sessions, and keeps saved plans and logs intact.",style=MaterialTheme.typography.bodySmall) }
            item { Text("Primary goal",style=MaterialTheme.typography.titleLarge);if(input.goal !in builderGoals(input))Text("Your previous goal needs a discipline you removed. Choose a goal for the selected disciplines.",color=MaterialTheme.colorScheme.error);FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { builderGoals(input).forEach { goal -> SignalChip(input.goal==goal,{val d=Engine1Engines.discipline(goal);edit(input.copy(goal=goal,disciplineGoals=if(d==null)input.disciplineGoals else input.disciplineGoals+(d to goal)))},label={Text(goal)}) } } }
            if(input.policyChoice=="coordinated")listOf("run" to input.runDays,"str" to input.strengthDays,"cal" to input.calDays,"cond" to input.conditioningDays).filter { it.second>0 }.forEach { (d,_) -> item {
                Text(disciplineLabel(d)+" goal",style=MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { Engine1Engines.goals.getValue(d).forEach { goal -> SignalChip(Engine1Engines.goal(input,d)==goal,{edit(input.copy(goal=if(Engine1Engines.discipline(input.goal)==d)goal else input.goal,disciplineGoals=input.disciplineGoals+(d to goal)))},label={Text(goal)},modifier=Modifier.testTag("goal-$d-$goal")) } }
            } }
            item { FormText("Secondary goal notes",input.secondaryGoal,{edit(input.copy(secondaryGoal=it.take(200)))}) }
            item { Text(if(input.runDays>0) "Running experience" else "General training experience",style=MaterialTheme.typography.titleLarge);ChoiceChips(listOf("Starting","Some experience","Experienced"),input.experience) { edit(input.copy(experience=it)) } }
            if(input.runDays>0) {
                item { FormText("Recent weekly running distance (km)",km,{km=it},true);Text(historyWeeklyKm(state.activities,plans=state.plans)?.let { "Recorded source / dated-native average over the last 28 days: %.1f km/week. History may be incomplete; edit this value.".format(it) } ?: "No history estimate available. Enter your own distance, or use 0 for a time-based beginner block.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                item { FormText("Longest recent run (km)",longest,{longest=it},true);FormText("Planning pace estimate (min/km)",pace,{pace=it},true);Text("An editable planning assumption, not a recorded pace. Use effort and your available time; a watch is optional.",style=MaterialTheme.typography.bodySmall) }
                item { Text("Quality sessions per week");FrequencyChips(input.qualityCount,2) { edit(input.copy(qualityCount=it)) };Text("Beginners and return-to-running weeks remain easy. Hard sessions require adequate running history and recovery spacing.",style=MaterialTheme.typography.bodySmall) }
            }
            if(input.strengthDays>0) item { Text("Strength experience");ChoiceChips(listOf("Starting","Some experience","Experienced"),input.strengthExperience) { edit(input.copy(strengthExperience=it)) } }
            if(input.calDays>0) {
                item { Text("Current pushing ability");ChoiceChips(listOf("Starting / assisted","Controlled push-up","Comfortable push-up"),input.calLevel) { edit(input.copy(calLevel=it)) } }
                item { Text("Current pulling ability");ChoiceChips(listOf("Starting / assisted","Controlled supported pull","Controlled pull-up"),input.pullLevel) { edit(input.copy(pullLevel=it)) } }
                item { Text("Current core ability");ChoiceChips(listOf("Dead bug","Hollow tuck","Comfortable hollow hold"),input.coreLevel) { edit(input.copy(coreLevel=it)) };Text("Report each movement separately. No skill stage advances automatically by elapsed weeks.",style=MaterialTheme.typography.bodySmall) }
            }
        }
        if(step==2) {
            item { NativeDateField("Start date",start,{start=it});Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick={start=LocalDate.now().toString()}) { Text("Today") };OutlinedButton(onClick={start=LocalDate.now().with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY)).toString()}) { Text("Next Monday") } } }
            item { FormText("Plan weeks (1–16)",weeks,{weeks=it},true);FormText("Available minutes per session (15–120)",minutes,{minutes=it},true) }
            item { Text("Available training days · ${input.days.size}",style=MaterialTheme.typography.titleLarge);DayChoices(input.days,"available-day") { d -> edit(input.copy(days=if(d in input.days)input.days-d else input.days+d)) };Text("Weekly session total: ${input.runDays+input.strengthDays+input.calDays+input.conditioningDays}. Session counts and scheduled training-day counts are separate; conflicts are explained and sessions are never silently dropped.",style=MaterialTheme.typography.bodySmall) }
            if(input.policyChoice=="multi-session") item { FormText("Maximum total training minutes per day (15–240)",dailyMinutes,{dailyMinutes=it},true);Text("This is a combined daily cap across compatible sessions. Each individual session also stays within its own time limit.",style=MaterialTheme.typography.bodySmall) }
            if(input.runDays>0) {
                item { Text("One preferred long-run day",style=MaterialTheme.typography.titleLarge);DayChoices(setOf(input.longDay),"long-day") { d -> edit(input.copy(longDay=d,days=input.days+d,qualityDays=input.qualityDays-d)) } }
                item { Text("Preferred quality days",style=MaterialTheme.typography.titleLarge);DayChoices(input.qualityDays,"quality-day") { d -> edit(input.copy(qualityDays=if(d in input.qualityDays)input.qualityDays-d else input.qualityDays+d)) } }
            }
            item { Text("Equipment quantities",style=MaterialTheme.typography.titleLarge);Text("Bodyweight/floor options remain available. Choose only verified, suitable equipment and stable supports.",style=MaterialTheme.typography.bodySmall) }
            listOf("db" to "Dumbbells","bands" to "Bands","anchor" to "Secure band anchors","bar" to "Secure overhead bars","support" to "Stable raised supports","bb" to "Barbells","rack" to "Racks with safeties","bench" to "Benches").forEach { (key,label) -> item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(label,Modifier.weight(1f));OutlinedButton(onClick={edit(input.copy(equipment=input.equipment+(key to maxOf(0,(input.equipment[key]?:0)-1))))}) { Text("−") };Text("${input.equipment[key]?:0}",Modifier.padding(12.dp));OutlinedButton(onClick={edit(input.copy(equipment=input.equipment+(key to minOf(20,(input.equipment[key]?:0)+1))))}) { Text("+") } } } }
            item { Row { Checkbox(input.returning,{edit(input.copy(returning=it))});Text("Returning from a break",Modifier.padding(top=12.dp)) };FormText("Limitations / preferences for review",input.limitations,{edit(input.copy(limitations=it.take(1000)))});FormText("Optional recovery notes",input.recoveryNotes,{edit(input.copy(recoveryNotes=it.take(1000)))}) }
            item { Text("Exclude specific exercises",style=MaterialTheme.typography.titleMedium);Text("Notes are retained for review; they do not automatically interpret an injury. Use exclusions to remove a movement.",style=MaterialTheme.typography.bodySmall);FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("sq" to "Bodyweight squat","gs" to "Goblet squat","hg" to "Hip hinge","ip" to "Incline push-up","dr" to "DB row","plank" to "Plank").forEach { (id,label) -> SignalChip(id in input.excluded,{edit(input.copy(excluded=if(id in input.excluded)input.excluded-id else input.excluded+id))},label={Text(label)}) } } }
        }
        if(step==3) item { SignalCard {
            Text(input.goal,style=MaterialTheme.typography.headlineSmall);Text("Running ${input.runDays} · Strength ${input.strengthDays} · Calisthenics ${input.calDays} · Conditioning ${input.conditioningDays}")
            Text("$start · $weeks weeks · up to $minutes min/session");Text("Available: "+input.days.sorted().joinToString { dayName(it) })
            if(input.runDays>0) {Text("Recent: $km km/week · longest $longest km · planning pace $pace min/km");Text("Long run: ${dayName(input.longDay)} · quality: ${input.qualityCount}, ${input.qualityDays.sorted().joinToString { dayName(it) }}")}
            Text("Nothing is approved yet. Generate a saved draft, inspect the prescriptions and calendar, then explicitly approve it.")
        } }
        error?.let { item { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("plan-form-error")) } }
        item { SignalButton(if(step<3) "Continue" else "Generate plan",{
            error=null
            if(step<3) step++ else try { val p=request();if(p.start<LocalDate.now())throw PlanConstraint("Choose today or a future start date.");Engine1Engines.validate(p);beforeId=state.plans.firstOrNull()?.id;awaiting=true;actions.generate(p) } catch(e:PlanConstraint) { error=e.message }
        },!state.busy&&!awaiting)
            if(step>0) TextButton(onClick={step--;error=null},modifier=Modifier.fillMaxWidth()) { Text("Back") }
            TextButton(onClick=onDone,modifier=Modifier.fillMaxWidth()) { Text("Close builder") }
        }
    } }
}
@Composable internal fun FormText(label:String,value:String,onChange:(String)->Unit,numeric:Boolean=false) { OutlinedTextField(value,onChange,label={Text(label)},modifier=Modifier.fillMaxWidth().padding(vertical=4.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp),keyboardOptions=KeyboardOptions(keyboardType=if(numeric)KeyboardType.Decimal else KeyboardType.Text)) }
@Composable internal fun FrequencyChips(value:Int,max:Int,tagPrefix:String?=null,onSelect:(Int)->Unit) { FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { (0..max).forEach { n -> SignalChip(value==n,{onSelect(n)},label={Text(if(n==0) "Off" else "$n / week")},modifier=if(tagPrefix==null)Modifier else Modifier.testTag("$tagPrefix-$n")) } } }
@Composable internal fun ChoiceChips(values:List<String>,selected:Int,onSelect:(Int)->Unit) { FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { values.forEachIndexed { i,label -> SignalChip(i==selected,{onSelect(i)},label={Text(label)}) } } }
@Composable internal fun DayChoices(selected:Set<Int>,tagPrefix:String?=null,onSelect:(Int)->Unit) { FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { (1..7).forEach { d -> SignalChip(d in selected,{onSelect(d)},label={Text(dayName(d))},modifier=if(tagPrefix==null)Modifier else Modifier.testTag("$tagPrefix-$d")) } } }
internal fun dayName(day:Int)=java.time.DayOfWeek.of(day).getDisplayName(java.time.format.TextStyle.SHORT,java.util.Locale.getDefault())
internal fun historyWeeklyKm(history:List<ActivityRecord>,now:Instant=Instant.now(),plans:List<PlanSnapshot> = emptyList(),zone:ZoneId=ZoneId.systemDefault()):Double? {
    val rows=history.distinctBy { it.id }.filter { DashboardSummary.isRun(it)&&it.startedAt<=now&&it.startedAt>=now.minusSeconds(28*86400L)&&it.distanceM!=null }
    val end=now.atZone(zone).toLocalDate();val native=DashboardSummary.confirmedWorkouts(plans,history,now).filter { it.discipline=="run" && it.distanceM!=null && it.date in end.minusDays(27)..end }
    return if(rows.size+native.size>=3)(rows.sumOf { it.distanceM!! }+native.sumOf { it.distanceM!! })/4000.0 else null
}

@Composable internal fun NativeDateField(label:String,value:String,onChange:(String)->Unit,enabled:Boolean=true) {
    val context=androidx.compose.ui.platform.LocalContext.current
    Column {
        OutlinedTextField(value,onChange,label={Text("$label (YYYY-MM-DD)")},enabled=enabled,modifier=Modifier.fillMaxWidth(),singleLine=true)
        if(enabled) TextButton(onClick={
            val date=runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
            android.app.DatePickerDialog(context,{_,y,m,d->onChange(LocalDate.of(y,m+1,d).toString())},date.year,date.monthValue-1,date.dayOfMonth).show()
        }) { Text("Choose date") }
    }
}

private fun builderGoals(p:PlanRequest)=ENGINE1_GOALS.filter { goal -> when(goal) {
    "General fitness"->true
    "Aerobic foundation","Improve 5K","Improve 10K","Half marathon","Marathon"->p.runDays>0
    "General strength","Muscle development","Strength endurance"->p.strengthDays>0
    "Calisthenics foundation","Pull-up progression","Push-up progression","Core strength and control","Handstand foundations"->p.calDays>0
    "Conditioning"->p.conditioningDays>0
    else->false
} }
