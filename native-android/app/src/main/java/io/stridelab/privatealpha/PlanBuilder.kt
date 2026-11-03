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
    val initial=remember {
        when(discipline) {
            "run" -> baseline.copy(
                runDays=3,strengthDays=0,calDays=0,conditioningDays=0,
                qualityCount=0,qualityDays=emptySet(),
                weeklySlots=emptyMap(),disciplineGoals=emptyMap()
            )
            "str" -> baseline.copy(
                goal="General strength",runDays=0,strengthDays=2,
                calDays=0,conditioningDays=0,qualityCount=0,
                qualityDays=emptySet(),weeklySlots=emptyMap(),
                disciplineGoals=emptyMap()
            )
            "cal" -> baseline.copy(
                goal="Calisthenics foundation",runDays=0,strengthDays=0,
                calDays=2,conditioningDays=0,qualityCount=0,
                qualityDays=emptySet(),weeklySlots=emptyMap(),
                disciplineGoals=emptyMap()
            )
            else -> baseline
        }.let { request ->
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
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var awaiting by rememberSaveable { mutableStateOf(false) }
    var beforeId by rememberSaveable { mutableStateOf<String?>(null) }
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingApprovalId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.busy,state.plans,state.message) {
        if(awaiting && !state.busy) {
            val newest=state.plans.firstOrNull()
            if(newest!=null && newest.id!=beforeId && newest.status=="draft") {
                previewId=newest.id
                step=4
                error=null
                awaiting=false
            } else if(state.message!=null) {
                // The scheduler may reject conflicts after request validation.
                // Keep the athlete in review with the actual recovery reason.
                error=state.message
                awaiting=false
            }
        }
        val target=pendingApprovalId
        if(target!=null && !state.busy) {
            if(state.plans.any { it.id==target && it.status=="approved" }) {
                pendingApprovalId=null
                error=null
                onDone()
            } else if(state.message!=null) {
                error=state.message
                pendingApprovalId=null
            }
        }
    }
    fun request():PlanRequest=try { input.copy(start=LocalDate.parse(start),weeks=weeks.toInt(),minutes=minutes.toInt(),dailyMinutes=if(input.policyChoice=="multi-session")dailyMinutes.toInt() else input.dailyMinutes,weeklyKm=km.toDouble(),longestKm=longest.toDouble(),planningPace=pace.toDouble()*60) } catch(e:Exception) { throw PlanConstraint("Enter a valid YYYY-MM-DD date and numeric mileage, pace, weeks, minutes, and daily budget if enabled.") }
    var previewWeek by rememberSaveable { mutableIntStateOf(0) }
    var runningSubPage by rememberSaveable { mutableIntStateOf(0) }
    var scheduleSubPage by rememberSaveable { mutableIntStateOf(0) }
    var runningSelectedSlot by rememberSaveable { mutableIntStateOf(0) }
    var otherSelectedDiscipline by rememberSaveable { mutableStateOf("str") }
    var otherSelectedSlot by rememberSaveable { mutableIntStateOf(0) }
    var runningCustom by rememberSaveable { mutableStateOf(true) }
    var qualityPreference by rememberSaveable {
        mutableStateOf(initial.preferredQuality)
    }
    var advancedQualityCsv by rememberSaveable {
        mutableStateOf(
            (initial.allowedQualityTypes.ifEmpty {
                setOf("Threshold","Tempo")
            }).sorted().joinToString("|")
        )
    }
    val advancedQualityTypes = advancedQualityCsv.split('|').filter { it.isNotBlank() }.toSet()
    var advancedBackPage by rememberSaveable { mutableIntStateOf(0) }
    var guidedFocus by rememberSaveable { mutableStateOf("10K") }
    var editingAbilityFromGuided by rememberSaveable { mutableStateOf(false) }
    var crossTrainingGoals by rememberSaveable { mutableStateOf(false) }
    var strengthGoalSaved by rememberSaveable { mutableStateOf(false) }
    var strengthSetupSaved by rememberSaveable { mutableStateOf(false) }
    var strengthSelectedSlot by rememberSaveable { mutableIntStateOf(0) }
    var crossGoalsSaved by rememberSaveable { mutableStateOf(false) }
    var caliSelectedSlot by rememberSaveable { mutableIntStateOf(0) }
    var strengthGoalLabel by rememberSaveable {
        mutableStateOf(when(Engine1Engines.goal(initial,"str")) {
            "Muscle development" -> "Build Muscle"
            "Strength endurance" -> "Maintain Fitness"
            else -> "Build Strength"
        })
    }
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
            busy = state.busy || pendingApprovalId != null,
            onBack = { step = 3 },
            onContinue = {
                if(!state.busy && pendingApprovalId==null) {
                    error=null
                    pendingApprovalId=preview.id
                    actions.approve(preview.id, preview.revision)
                }
            },
            onOpenSession = { sessionId -> onPreview(preview.id, sessionId) },
            weekIndex = previewWeek,
            onWeekChange = { previewWeek = it },
            error = error
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
                val toggled=when(d) {
                    "run" -> input.copy(
                        runDays=if(active)0 else 3,
                        qualityCount=0,
                        qualityDays=emptySet()
                    )
                    "str" -> input.copy(strengthDays=if(active)0 else 2)
                    "cal" -> input.copy(calDays=if(active)0 else 2)
                    else -> input.copy(conditioningDays=if(active)0 else 1)
                }
                // A deselected discipline must not leave phantom fixed sessions
                // or stale discipline goals in a future generated plan.
                val cleaned=toggled.copy(
                    weeklySlots=if(active) toggled.weeklySlots-d else toggled.weeklySlots,
                    disciplineGoals=if(active) toggled.disciplineGoals-d
                        else toggled.disciplineGoals
                )
                val primaryValid=when(cleaned.goal) {
                    "Aerobic foundation","Improve 5K","Improve 10K",
                    "Half marathon","Marathon" -> cleaned.runDays>0
                    "General strength","Muscle development",
                    "Strength endurance" -> cleaned.strengthDays>0
                    "Calisthenics foundation","Pull-up progression",
                    "Push-up progression","Core strength and control",
                    "Handstand foundations" -> cleaned.calDays>0
                    "Conditioning" -> cleaned.conditioningDays>0
                    else -> true
                }
                val fallback=when {
                    cleaned.runDays>0 -> "General fitness"
                    cleaned.strengthDays>0 -> "General strength"
                    cleaned.calDays>0 -> "Calisthenics foundation"
                    cleaned.conditioningDays>0 -> "Conditioning"
                    else -> "General fitness"
                }
                edit(cleaned.copy(goal=if(primaryValid) cleaned.goal else fallback))
            },
            onBack = onDone,
            onContinue = {
                step=1
                runningSubPage=0
                crossTrainingGoals=false
                strengthGoalSaved=false
                strengthSetupSaved=false
                crossGoalsSaved=false
            }
        )
        return
    }
    if (step == 1 && input.runDays > 0 && !crossTrainingGoals) {
        when (runningSubPage) {
            0 -> TracksideFigmaRunningGoalScreen(
                selected = runningRoute,
                onSelect = { route ->
                    editingAbilityFromGuided = false
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
                onWeeklyKm = { value ->
                    km=value.toInt().toString()
                    edit(input.copy(weeklyKm=value))
                },
                onLongestKm = { value ->
                    longest=value.toInt().toString()
                    edit(input.copy(longestKm=value))
                },
                onBack = {
                    runningSubPage = when(runningRoute) {
                        "race" -> 1
                        "custom" -> 3
                        else -> 5
                    }
                },
                onContinue = {
                    runningSubPage = if(editingAbilityFromGuided) 5 else 4
                    editingAbilityFromGuided = false
                },
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
                onWeeklyKm = { value ->
                    km=value.toInt().toString()
                    edit(input.copy(weeklyKm=value))
                },
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
                    edit(input.copy(allowedQualityTypes=updated))
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
                onEditAbility = {
                    editingAbilityFromGuided = true
                    runningSubPage = 2
                },
                onAdvanced = {
                    advancedBackPage = 5
                    runningSubPage = 3
                },
                onBack = {
                    editingAbilityFromGuided = false
                    runningSubPage = 0
                },
                onContinue = {
                    editingAbilityFromGuided = false
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
                selectedSlot = runningSelectedSlot,
                onSelectSlot = { runningSelectedSlot = it },
                custom = runningCustom,
                onCustom = { enabled ->
                    runningCustom = enabled
                    edit(input.copy(
                        weeklySlots = if (enabled) input.weeklySlots +
                            ("run" to WeeklyPlacement.days(input,"run"))
                        else input.weeklySlots - "run"
                    ))
                },
                qualityPreference = qualityPreference,
                onQualityPreference = { selected ->
                    qualityPreference=selected
                    edit(input.copy(preferredQuality=selected))
                },
                onQualityCount = { n ->
                    val allowed = minOf((input.runDays-2).coerceAtLeast(0),
                        if(input.experience==2 && input.runDays>=4 &&
                            input.weeklyKm>=30.0) 2 else 1)
                    if(n<=allowed) {
                        val slots=WeeklyPlacement.days(input,"run")
                        edit(input.copy(
                            qualityCount=n,
                            qualityDays=slots.drop(1).take(n).filter {
                                it in 1..7
                            }.toSet()
                        ))
                    }
                },
                onRunningCount = { n ->
                    val changed = if(runningCustom)
                        WeeklyPlacement.resize(input,"run",n)
                    else input.copy(
                        runDays=n,
                        weeklySlots=input.weeklySlots-"run"
                    )
                    val allowed=minOf((n-2).coerceAtLeast(0),
                        if(changed.experience==2 && n>=4 &&
                            changed.weeklyKm>=30.0) 2 else 1)
                    val q=changed.qualityCount.coerceAtMost(allowed)
                    val slots=WeeklyPlacement.days(changed,"run")
                    edit(changed.copy(
                        qualityCount=q,
                        qualityDays=slots.drop(1).take(q).filter {
                            it in 1..7
                        }.toSet()
                    ))
                    runningSelectedSlot=runningSelectedSlot.coerceIn(0,n-1)
                },
                onAssignDay = { day ->
                    edit(WeeklyPlacement.put(input,"run",
                        runningSelectedSlot.coerceIn(0,input.runDays-1),day))
                },
                onClearSlot = {
                    edit(WeeklyPlacement.clear(input,"run",
                        runningSelectedSlot.coerceIn(0,input.runDays-1)))
                },
                onBack = { runningSubPage=2 },
                onContinue = {
                    // Save even an unchanged default once the user chose
                    // manual placement, so the engine must honor those days.
                    if(runningCustom && "run" !in input.weeklySlots) {
                        edit(input.copy(
                            weeklySlots=input.weeklySlots +
                                ("run" to WeeklyPlacement.days(input,"run"))
                        ))
                    }
                    if(input.strengthDays+input.calDays+input.conditioningDays>0) {
                        crossTrainingGoals=true
                        strengthGoalSaved=false
                    } else {
                        step=2
                        scheduleSubPage=0
                    }
                }
            )
        }
        return
    }
    if(step==1 && (input.runDays==0 || crossTrainingGoals)) {
        if(input.strengthDays>0 && !strengthGoalSaved) {
            TracksideFigmaStrengthGoalScreen(
                selected=strengthGoalLabel,
                onSelect={ strengthGoalLabel=it },
                onBack={
                    if(input.runDays>0) {
                        crossTrainingGoals=false
                        runningSubPage=4
                    } else step=0
                },
                onContinue={
                    val goal=strengthChoicePolicy(strengthGoalLabel)
                    edit(input.copy(
                        goal=if(input.runDays==0)goal else input.goal,
                        disciplineGoals=input.disciplineGoals+("str" to goal)
                    ))
                    strengthGoalSaved=true
                    strengthSetupSaved=false
                }
            )
        } else if(input.strengthDays>0 && !strengthSetupSaved) {
            TracksideFigmaStrengthSetup(
                request=input,
                selectedSlot=strengthSelectedSlot,
                onSelectedSlot={ strengthSelectedSlot=it },
                onChangeMode={ custom ->
                    edit(input.copy(weeklySlots=
                        if(custom) input.weeklySlots+
                            ("str" to WeeklyPlacement.days(input,"str"))
                        else input.weeklySlots-"str"
                    ))
                },
                onCount={ count ->
                    edit(if("str" in input.weeklySlots)
                        WeeklyPlacement.resize(input,"str",count)
                    else input.copy(strengthDays=count))
                    strengthSelectedSlot=strengthSelectedSlot.coerceIn(0,count-1)
                },
                onAssign={ index,day ->
                    edit(WeeklyPlacement.put(input,"str",index,day))
                },
                onClear={ index ->
                    edit(WeeklyPlacement.clear(input,"str",index))
                },
                onEquipment={ equipmentId,enabled ->
                    edit(input.copy(equipment=input.equipment+
                        (equipmentId to if(enabled)1 else 0)))
                },
                onBack={ strengthGoalSaved=false },
                onEditGoal={ strengthGoalSaved=false },
                onContinue={ strengthSetupSaved=true }
            )
        } else if(input.calDays>0 && crossGoalsSaved) {
            TracksideFigmaCalisthenicsSetup(
                request=input,
                selectedSlot=caliSelectedSlot,
                onSelectedSlot={ caliSelectedSlot=it },
                onMode={ custom ->
                    edit(input.copy(weeklySlots=
                        if(custom) input.weeklySlots+
                            ("cal" to WeeklyPlacement.days(input,"cal"))
                        else input.weeklySlots-"cal"))
                },
                onCount={ count ->
                    edit(if("cal" in input.weeklySlots)
                        WeeklyPlacement.resize(input,"cal",count)
                    else input.copy(calDays=count))
                    caliSelectedSlot=caliSelectedSlot.coerceIn(0,count-1)
                },
                onAssign={ index,day ->
                    edit(WeeklyPlacement.put(input,"cal",index,day))
                },
                onClear={ index ->
                    edit(WeeklyPlacement.clear(input,"cal",index))
                },
                onAbility={ part,level ->
                    edit(when(part) {
                        "Push" -> input.copy(calLevel=level)
                        "Pull" -> input.copy(pullLevel=level)
                        else -> input.copy(coreLevel=level)
                    })
                },
                onBack={ crossGoalsSaved=false },
                onContinue={
                    crossGoalsSaved=false
                    step=2
                    scheduleSubPage=0
                }
            )
        } else {
            TracksideFigmaCrossTrainingGoalsScreen(
                request=input,
                onBack={
                    if(input.strengthDays>0) strengthSetupSaved=false
                    else if(input.runDays>0) {
                        crossTrainingGoals=false
                        runningSubPage=4
                    } else step=0
                },
                onContinue={
                    if(input.calDays>0) crossGoalsSaved=true
                    else { step=2; scheduleSubPage=0 }
                },
                onEditStrengthGoal={
                    strengthGoalSaved=false
                    strengthSetupSaved=false
                },
                onGoal={ d,goal ->
                    val primary=when {
                        input.runDays>0 -> "run"
                        input.strengthDays>0 -> "str"
                        input.calDays>0 -> "cal"
                        else -> "cond"
                    }
                    edit(input.copy(
                        goal=if(d==primary)goal else input.goal,
                        disciplineGoals=input.disciplineGoals+(d to goal)
                    ))
                },
                onCount={ d,count ->
                    edit(if(d in input.weeklySlots)
                        WeeklyPlacement.resize(input,d,count)
                    else when(d) {
                        "str" -> input.copy(strengthDays=count)
                        "cal" -> input.copy(calDays=count)
                        else -> input.copy(conditioningDays=count)
                    })
                },
                onExperience={ d,level ->
                    edit(when(d) {
                        "str" -> input.copy(strengthExperience=level)
                        "push" -> input.copy(calLevel=level)
                        "pull" -> input.copy(pullLevel=level)
                        else -> input.copy(coreLevel=level)
                    })
                }
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
                dailyBudget = dailyMinutes,
                multiSession = input.policyChoice=="multi-session",
                onDailyBudget = { dailyMinutes=it },
                onBack = {
                    step = 1
                    if(input.runDays > 0) {
                        if(input.strengthDays+input.calDays+input.conditioningDays>0) {
                            crossTrainingGoals=true
                            strengthGoalSaved=true
                            strengthSetupSaved=true
                        } else runningSubPage = 4
                    }
                    crossGoalsSaved=input.calDays>0
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
                dailyBudget = dailyMinutes,
                onDailyBudget = { dailyMinutes=it },
                onDay = { day ->
                    val pinned = input.weeklySlots.values.flatten().contains(day)
                    if(day !in input.days || !pinned) {
                        val days = if(day in input.days) input.days-day
                            else input.days+day
                        edit(input.copy(
                            days=days,
                            longDay=if(input.longDay in days)input.longDay
                                else (days.maxOrNull() ?: input.longDay),
                            qualityDays=input.qualityDays.intersect(days)
                        ))
                    }
                },
                onRemovePinnedDay = { day ->
                    edit(WeeklyPlacement.makeUnavailable(input,day))
                },
                selectedOtherDiscipline = otherSelectedDiscipline,
                selectedOtherSlot = otherSelectedSlot,
                onSelectOtherDiscipline = { otherSelectedDiscipline=it },
                onSelectOtherSlot = { otherSelectedSlot=it },
                onAssignOtherDay = { discipline,index,day ->
                    edit(WeeklyPlacement.put(input,discipline,index,day))
                },
                onClearOtherSlot = { discipline,index ->
                    edit(WeeklyPlacement.clear(input,discipline,index))
                },
                onAutoOther = { discipline ->
                    edit(input.copy(weeklySlots=input.weeklySlots-discipline))
                },
                onCountOther = { discipline,count ->
                    if(discipline in input.weeklySlots) {
                        edit(WeeklyPlacement.resize(input,discipline,count))
                    } else {
                        edit(when(discipline) {
                            "str" -> input.copy(strengthDays=count)
                            "cal" -> input.copy(calDays=count)
                            else -> input.copy(conditioningDays=count)
                        })
                    }
                    otherSelectedSlot=otherSelectedSlot.coerceIn(0,count-1)
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
    // An orphaned draft after process restore must never reveal the removed
    // legacy chip-based builder. Preserve all edited request inputs.
    val missingDraft = step == 4
    TracksideFigmaBuilderFrame(
        eyebrow = "PLAN / RECOVERY",
        toolbarTitle = if(missingDraft) "Restore draft" else "Plan setup",
        headline = if(missingDraft) "Draft unavailable" else "Resume plan setup",
        description = if(missingDraft)
            "The draft is no longer available. Your choices are still here."
            else "This step could not be restored. Your plan inputs are preserved.",
        ctaLabel = if(missingDraft) "Review your choices" else "Return to setup",
        onBack = {
            if(missingDraft) step = 3 else step = 0
        },
        onContinue = {
            error = null
            if(missingDraft) {
                previewId = null
                pendingApprovalId = null
                step = 3
            } else {
                step = 0
                runningSubPage = 0
                scheduleSubPage = 0
                crossTrainingGoals = false
                strengthGoalSaved = false
            }
        }
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("builder-draft-recovery"),
            color = MaterialTheme.colorScheme.surface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
        ) {
            Column(Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if(missingDraft) "No saved draft found" else "Setup could not resume",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    if(missingDraft)
                        "Review your selected disciplines, weekdays, session counts and plan dates before generating a new draft. No existing approved plan is changed."
                    else "Return to the first step and continue editing your existing plan choices.",
                    style = MaterialTheme.typography.bodyMedium
                )
                error?.let {
                    Text(it,color=MaterialTheme.colorScheme.error)
                }
            }
        }
    }
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
