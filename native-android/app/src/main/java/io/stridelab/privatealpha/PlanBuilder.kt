@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.stridelab.privatealpha

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
    // Signed-out plans are persisted under an isolated on-device owner.
    // Cloud sign-in remains optional for backup; generating does not need it.
    val observedBaseline=remember(state.activities,state.plans) {
        automaticRunningBaseline(state.activities,plans=state.plans)
    }
    val detectedExperience=remember(state.activities,state.plans) {
        automaticRunningExperience(state.activities,plans=state.plans)
    }
    val historicalEasyPace=remember(state.activities) {
        Engine15PaceZoneEstimator.inferHistoricalEasyPaceSecondsPerKm(state.activities, 360.0)
    }
    val baseline=remember {
        val existing=state.plans.firstOrNull {
            Engine1Engines.canManage(it) && it.policyId != SessionTemplate.POLICY_ID
        }?.let(TracksideCoaching::setupFor)
        if(existing!=null) {
            val withDetectedExp = if (detectedExperience != null) existing.copy(
                experience = detectedExperience,
                planningPace = historicalEasyPace
            ) else existing
            if(withDetectedExp.weeklyKm>0.0) {
                withDetectedExp.copy(
                    recentMileageBasis = if (withDetectedExp.recentMileageBasis in setOf("LEGACY_UNSPECIFIED", "UNKNOWN"))
                        "SELF_REPORTED_AVERAGE" else withDetectedExp.recentMileageBasis
                )
            } else if (withDetectedExp.recentMileageBasis in setOf("UNKNOWN","CONFIRMED_ZERO","PEAK_WEEK_ONLY")) {
                withDetectedExp
            } else observedBaseline?.let {
                withDetectedExp.copy(weeklyKm=it.weeklyKm,recentMileageBasis="RECORDED_PARTIAL")
            } ?: withDetectedExp
        } else {
            PlanRequest(
                policyChoice="coordinated",
                experience=detectedExperience ?: 0,
                planningPace=historicalEasyPace,
                weeklyKm=observedBaseline?.weeklyKm ?: 0.0,
                recentMileageBasis=if(observedBaseline==null) "SELF_REPORTED_AVERAGE" else "RECORDED_PARTIAL",
                longestKm=state.activities.filter { DashboardSummary.isRun(it) }
                    .mapNotNull { it.distanceM }.maxOrNull()?.div(1000) ?: 0.0
            )
        }
    }
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
    }.copy(timeBudgetEnabled=false) }
    var encoded by rememberSaveable { mutableStateOf(initial.encode().toString()) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    val input=PlanRequest.decode(JSONObject(encoded))
    fun edit(p:PlanRequest) { encoded=p.encode().toString() }
    var km by rememberSaveable { mutableStateOf(initial.weeklyKm.toString()) };var targetKm by rememberSaveable { mutableStateOf((initial.targetWeeklyKm ?: initial.weeklyKm).toString()) };var targetSupplied by rememberSaveable { mutableStateOf(initial.targetWeeklyKm != null) };var longest by rememberSaveable { mutableStateOf(initial.longestKm.toString()) }
    var peakWeekKm by rememberSaveable {
        mutableStateOf(initial.reportedPeakWeeklyKm?.toString() ?: "")
    }
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
    fun request():PlanRequest=try {
        val peak=peakWeekKm.trim().takeIf { it.isNotEmpty() }?.toDouble()
        if(peak!=null && (!peak.isFinite() || peak !in 0.0..120.0))
            throw PlanConstraint("Enter a valid recent peak between 0 and 120 km/week.")
        if(input.recentMileageBasis=="PEAK_WEEK_ONLY" && (peak==null || peak<=0.0))
            throw PlanConstraint("Enter your peak weekly mileage, or select Unknown if you cannot estimate it.")
        input.copy(
            start=LocalDate.parse(start),weeks=weeks.toInt(),minutes=minutes.toInt(),
            dailyMinutes=if(input.policyChoice=="multi-session")dailyMinutes.toInt() else input.dailyMinutes,
            weeklyKm=km.toDouble(),targetWeeklyKm=if(targetSupplied) targetKm.toDouble() else null,
            longestKm=longest.toDouble(),planningPace=pace.toDouble()*60,
            reportedPeakWeeklyKm=peak,timeBudgetEnabled=false
        )
    } catch(e:PlanConstraint) { throw e }
      catch(e:Exception) {
        throw PlanConstraint("Enter valid dates and numeric recent average, peak, target mileage, pace and weeks.")
    }
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
    var runningRoute by rememberSaveable { mutableStateOf(if(initial.trainYourWay) "custom" else "race") }
    var raceIntent by rememberSaveable { mutableStateOf("pr") }
    var raceDistance by rememberSaveable { mutableStateOf(when(input.goal) {
        "Improve 10K" -> "10K"
        "Half marathon" -> "21K"
        "Marathon" -> "42K"
        else -> "5K"
    }) }
    val preview=state.plans.find { it.id==previewId }
    if (step == 3) {
        LaunchedEffect(Unit) {
            scheduleSubPage = 0
            step = 2
        }
        return
    }
    val stageKey = when {
        step == 4 && preview != null -> 40
        step == 0 -> 0
        step == 1 && input.runDays > 0 && !crossTrainingGoals -> when (runningSubPage) {
            0 -> 10
            1 -> 11
            5 -> 12
            3 -> 13
            2 -> 14
            else -> 15
        }
        step == 1 && (input.runDays == 0 || crossTrainingGoals) -> when {
            input.strengthDays > 0 && !strengthGoalSaved -> 20
            input.strengthDays > 0 && !strengthSetupSaved -> 21
            input.calDays > 0 && crossGoalsSaved -> 23
            else -> 22
        }
        step == 2 -> 30
        else -> 50
    }
    AnimatedContent(
        targetState = stageKey,
        transitionSpec = {
            val forward = targetState >= initialState
            val enterSlide = slideInHorizontally(animationSpec = tween(220)) { if (forward) it / 6 else -it / 6 }
            val exitSlide = slideOutHorizontally(animationSpec = tween(180)) { if (forward) -it / 6 else it / 6 }
            (enterSlide + fadeIn(tween(200))) togetherWith (exitSlide + fadeOut(tween(160)))
        },
        label = "plan-builder-stage"
    ) { activeStage ->
        when (activeStage) {
            40 -> {
                val activePreview = preview ?: return@AnimatedContent
                TracksideWeekReview(
                    request = activePreview.request,
                    plan = activePreview,
                    busy = state.busy || awaiting || pendingApprovalId != null,
                    onBack = {
                        step = 2
                        scheduleSubPage = 0
                    },
                    onContinue = {
                        if (!state.busy && pendingApprovalId == null) {
                            error = null
                            pendingApprovalId = activePreview.id
                            actions.approve(activePreview.id, activePreview.revision)
                        }
                    },
                    onOpenSession = { sessionId -> onPreview(activePreview.id, sessionId) },
                    weekIndex = previewWeek,
                    onWeekChange = { previewWeek = it },
                    error = error,
                    onChooseGoalOption = { chosen ->
                        error = null; beforeId = state.plans.firstOrNull()?.id; awaiting = true
                        weeks = chosen.weeks.toString(); targetKm = chosen.targetWeeklyKm.toString(); targetSupplied = true; edit(chosen)
                        actions.generate(chosen)
                    },
                    activities = state.activities
                )
            }
            0 -> TracksideDisciplineScreen(
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
        10, 11, 12, 13, 14, 15 -> when (runningSubPage) {
            0 -> TracksideRunningGoalScreen(
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
                            // Train Your Way is mileage-led, not an inherited 5K race plan.
                            edit(input.copy(
                                goal="Aerobic foundation",
                                disciplineGoals=input.disciplineGoals+("run" to "Aerobic foundation"),
                                trainYourWay=true,
                                foundationAcknowledged=false
                            ))
                            advancedBackPage = 0
                            runningSubPage = 3
                        }
                        else -> runningSubPage = 5
                    }
                }
            )
            1 -> TracksideRaceGoalScreen(
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
                        disciplineGoals=input.disciplineGoals+("run" to goal),
                        trainYourWay=false,foundationAcknowledged=false))
                    runningSubPage = 2
                },
                targetRaceDate = input.targetRaceDate,
                onTargetRaceDate = { chosenDate ->
                    if (chosenDate != null) {
                        val startDate = runCatching { LocalDate.parse(start) }.getOrDefault(LocalDate.now())
                        val computedWeeks = java.time.temporal.ChronoUnit.WEEKS.between(startDate, chosenDate).toInt().coerceIn(4, 16)
                        weeks = computedWeeks.toString()
                        edit(input.copy(targetRaceDate = chosenDate, weeks = computedWeeks))
                    } else {
                        edit(input.copy(targetRaceDate = null))
                    }
                },
                benchmarkSeconds = input.benchmarkDurationSeconds,
                onBenchmarkSeconds = { seconds ->
                    val distMeters = when (raceDistance) {
                        "10K" -> 10_000
                        "21K" -> 21_097
                        "42K" -> 42_195
                        else -> 5_000
                    }
                    edit(input.copy(
                        benchmarkDistanceMeters = if (seconds != null) distMeters else null,
                        benchmarkDurationSeconds = seconds
                    ))
                },
                paceZones = remember(input.benchmarkDistanceMeters, input.benchmarkDurationSeconds, input.planningPace, state.activities) {
                    Engine15PaceZoneEstimator.estimate(input, activities = state.activities)
                }
            )
            2 -> {
                val benchDistLabel = when (input.benchmarkDistanceMeters) {
                    42_195 -> "42K"
                    21_097 -> "21K"
                    10_000 -> "10K"
                    else -> "5K"
                }
                TracksideRunningAbilityScreen(
                    experience = input.experience,
                    weeklyKm = km.toDoubleOrNull()?.coerceIn(0.0, 120.0) ?: 0.0,
                    longestKm = longest.toDoubleOrNull()?.coerceIn(0.0, 42.0) ?: 0.0,
                    onExperience = { edit(input.copy(experience = it)) },
                    onWeeklyKm = { value ->
                        km = value.toInt().toString()
                        edit(input.copy(
                            weeklyKm = value,
                            recentMileageBasis = "SELF_REPORTED_AVERAGE",
                            foundationAcknowledged = false
                        ))
                    },
                    onLongestKm = { value ->
                        longest = value.toInt().toString()
                        edit(input.copy(longestKm = value))
                    },
                    onBack = {
                        runningSubPage = when (runningRoute) {
                            "race" -> 1
                            "custom" -> 3
                            else -> 5
                        }
                    },
                    onContinue = {
                        runningSubPage = if (editingAbilityFromGuided) 5 else 4
                        editingAbilityFromGuided = false
                    },
                    onAdvanced = {
                        advancedBackPage = 2
                        runningSubPage = 3
                    },
                    benchmarkDistance = benchDistLabel,
                    benchmarkSeconds = input.benchmarkDurationSeconds,
                    onSaveBenchmark = { dist, seconds ->
                        val distMeters = when (dist) {
                            "10K" -> 10_000
                            "21K" -> 21_097
                            "42K" -> 42_195
                            else -> 5_000
                        }
                        edit(input.copy(
                            benchmarkDistanceMeters = if (seconds != null) distMeters else null,
                            benchmarkDurationSeconds = seconds
                        ))
                    },
                    paceZones = remember(input.benchmarkDistanceMeters, input.benchmarkDurationSeconds, input.planningPace, state.activities) {
                        Engine15PaceZoneEstimator.estimate(input, activities = state.activities)
                    },
                    autoDetectedExperience = detectedExperience != null
                )
            }
            3 -> TracksideAdvancedRunning(
                guided = advancedBackPage != 0,
                weeks = input.weeks,
                userDirectedPlanning = input.userDirectedPlanning,
                userDefinedQualityWorkouts = input.userDefinedQualityWorkouts,
                weeklyKm = targetKm.toDoubleOrNull()?.coerceIn(0.0, 120.0) ?: 0.0,
                qualityCount = input.qualityCount.coerceIn(0, 2),
                maxQualityCount = (input.runDays - 1).coerceIn(0, 2),
                selectedQualityTypes = advancedQualityTypes,
                onWeeklyKm = { value ->
                    targetKm = value.toInt().toString()
                    targetSupplied = true
                    edit(input.copy(targetWeeklyKm = value, foundationAcknowledged = false))
                },
                onQualityCount = { n ->
                    val allowed = (input.runDays - 1).coerceIn(0, 2)
                    edit(input.copy(qualityCount = n.coerceAtMost(allowed)))
                },
                onToggleQuality = { quality ->
                    val updated =
                        if (quality in advancedQualityTypes) advancedQualityTypes-quality
                        else advancedQualityTypes+quality
                    advancedQualityCsv = updated.sorted().joinToString("|")
                    edit(input.copy(allowedQualityTypes=updated))
                },
                onSaveQualityWorkouts = { workouts ->
                    edit(input.copy(userDefinedQualityWorkouts=workouts))
                },
                onRemoveQualityWorkout = { key ->
                    edit(input.copy(userDefinedQualityWorkouts=input.userDefinedQualityWorkouts-key))
                },
                onBack = { runningSubPage = advancedBackPage },
                onContinue = {
                    runningSubPage = if (advancedBackPage == 5) 5 else 2
                }
            )
            5 -> TracksideGuidedRunning(
                route = runningRoute,
                selectedFocus = guidedFocus,
                onFocus = { guidedFocus = it },
                weeklyKm = targetKm.toDoubleOrNull()?.coerceIn(0.0,120.0) ?: 0.0,
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
                        disciplineGoals=input.disciplineGoals+("run" to goal),
                        trainYourWay=false,foundationAcknowledged=false
                    ))
                    runningSubPage = 2
                }
            )
            4 -> TracksideRunningSetup(
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
                    edit(input.copy(preferredQuality=selected, preferredQualityExplicit=true))
                },
                onQualityCount = { n ->
                    val allowed = (input.runDays - 1).coerceIn(0, 2)
                    if (n <= allowed) {
                        val slots = WeeklyPlacement.days(input, "run")
                        edit(input.copy(
                            qualityCount = n,
                            qualityDays = slots.drop(1).take(n).filter { it in 1..7 }.toSet()
                        ))
                    }
                },
                onRunningCount = { n ->
                    val changed = if (runningCustom)
                        WeeklyPlacement.resize(input, "run", n)
                    else input.copy(
                        runDays = n,
                        weeklySlots = input.weeklySlots - "run"
                    )
                    val allowed = (n - 1).coerceIn(0, 2)
                    val q = changed.qualityCount.coerceAtMost(allowed)
                    val slots = WeeklyPlacement.days(changed, "run")
                    edit(changed.copy(
                        qualityCount = q,
                        qualityDays = slots.drop(1).take(q).filter { it in 1..7 }.toSet()
                    ))
                    runningSelectedSlot = runningSelectedSlot.coerceIn(0, n - 1)
                },
                onAssignDay = { day ->
                    edit(WeeklyPlacement.put(input,"run",
                        runningSelectedSlot.coerceIn(0,input.runDays-1),day))
                },
                onAssignSlot = { index,day ->
                    // Tap day -> choose type is a single atomic placement.
                    // WeeklyPlacement handles occupied-day swaps and preserves
                    // long/quality ordinals and workout counts.
                    runningCustom=true
                    edit(WeeklyPlacement.put(input,"run",index,day))
                    runningSelectedSlot=index
                },
                onClearDay = { index ->
                    runningCustom=true
                    edit(WeeklyPlacement.clear(input,"run",index))
                    runningSelectedSlot=index
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
        20, 21, 22, 23 -> if(input.strengthDays>0 && !strengthGoalSaved) {
            TracksideStrengthGoalScreen(
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
            TracksideStrengthSetup(
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
            TracksideCalisthenicsSetup(
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
            TracksideCrossTrainingGoalsScreen(
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
        30 -> TracksidePlanDetailsScreen(
            start=start,weeks=weeks,minutes=minutes,
            onStart={ start=it },onWeeks={ weeks=it },onMinutes={ minutes=it },
            dailyBudget=dailyMinutes,
            multiSession=input.policyChoice=="multi-session",
            onDailyBudget={ dailyMinutes=it },
            generationBusy=state.busy || awaiting,
            scheduleValid=compactScheduleError(input)==null,
            scheduleContent={
                TracksideCompactScheduleEditor(
                    request=input,
                    onDay={ day ->
                        val pinned=input.weeklySlots.values.flatten().contains(day)
                        if(day !in input.days || !pinned) {
                            val days=if(day in input.days)input.days-day else input.days+day
                            edit(input.copy(
                                days=days,
                                longDay=if(input.longDay in days)input.longDay
                                    else (days.maxOrNull() ?: input.longDay),
                                qualityDays=input.qualityDays.intersect(days)
                            ))
                        }
                    },
                    onRemovePinnedDay={ day ->
                        edit(WeeklyPlacement.makeUnavailable(input,day))
                    },
                    onMultiSessionToggle={ enabled ->
                        edit(input.copy(policyChoice=
                            if(enabled)"multi-session" else "coordinated"))
                    },
                    selectedOtherDiscipline=otherSelectedDiscipline,
                    selectedOtherSlot=otherSelectedSlot,
                    onSelectOtherDiscipline={ otherSelectedDiscipline=it },
                    onSelectOtherSlot={ otherSelectedSlot=it },
                    onAssignOtherDay={ discipline,index,day ->
                        edit(WeeklyPlacement.put(input,discipline,index,day))
                    },
                    onClearOtherSlot={ discipline,index ->
                        edit(WeeklyPlacement.clear(input,discipline,index))
                    },
                    onAutoOther={ discipline ->
                        edit(input.copy(weeklySlots=input.weeklySlots-discipline))
                    },
                    onCountOther={ discipline,count ->
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
                    }
                )
                error?.let { message ->
                    Spacer(Modifier.height(12.dp))
                    PlanBuilderErrorBanner(message)
                }
            },
            onBack={
                step=1
                if(input.runDays>0) {
                    if(input.strengthDays+input.calDays+input.conditioningDays>0) {
                        crossTrainingGoals=true
                        strengthGoalSaved=true
                        strengthSetupSaved=true
                    } else runningSubPage=4
                }
                crossGoalsSaved=input.calDays>0
            },
            onContinue={
                if(!state.busy && !awaiting) {
                    error=null
                    try {
                        val p=request()
                        if(p.start<LocalDate.now())
                            throw PlanConstraint("Choose today or a future start date.")
                        val conflict=compactScheduleError(p)
                        if(conflict!=null)throw PlanConstraint(conflict)
                        IndependentEngineRouteGate.requireProduction(
                            IndependentEngineRoute.EXISTING_COACH
                        )
                        val selected=TracksideCoaching.request(p)
                        beforeId=state.plans.firstOrNull()?.id
                        awaiting=true
                        actions.generate(selected)
                    } catch(e:PlanConstraint) { error=e.message }
                }
            }
        )
        else -> {
            // An orphaned draft after process restore must never reveal the removed
            // legacy chip-based builder. Preserve all edited request inputs.
            val missingDraft = step == 4
            TracksideBuilderFrame(
                eyebrow = "PLAN / RECOVERY",
                toolbarTitle = if(missingDraft) "Restore draft" else "Plan setup",
                headline = if(missingDraft) "Draft unavailable" else "Resume plan setup",
                description = if(missingDraft)
                    "The draft is no longer available. Your choices are still here."
                    else "This step could not be restored. Your plan inputs are preserved.",
                ctaLabel = if(missingDraft) "Review plan dates" else "Return to setup",
                onBack = {
                    if(missingDraft) { step=2; scheduleSubPage=0 } else step = 0
                },
                onContinue = {
                    error = null
                    if(missingDraft) {
                        previewId = null
                        pendingApprovalId = null
                        step = 2
                        scheduleSubPage = 0
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
                    color = PlanBuilderPalette.surface,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            if(missingDraft) "No saved draft found" else "Setup could not resume",
                            style = MaterialTheme.typography.titleMedium,
                            color = PlanBuilderPalette.text
                        )
                        Text(
                            if(missingDraft)
                                "Review your selected disciplines, weekdays, session counts and plan dates before generating a new draft. No existing approved plan is changed."
                            else "Return to the first step and continue editing your existing plan choices.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PlanBuilderPalette.secondary
                        )
                        error?.let { PlanBuilderErrorBanner(it) }
                    }
                }
            }
        }
        }
    }
}
@Composable internal fun FormText(label:String,value:String,onChange:(String)->Unit,numeric:Boolean=false) { OutlinedTextField(value,onChange,label={Text(label)},modifier=Modifier.fillMaxWidth().padding(vertical=4.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp),keyboardOptions=KeyboardOptions(keyboardType=if(numeric)KeyboardType.Decimal else KeyboardType.Text)) }
@Composable internal fun ChoiceChips(values:List<String>,selected:Int,onSelect:(Int)->Unit) { FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { values.forEachIndexed { i,label -> SignalChip(i==selected,{onSelect(i)},label={Text(label)}) } } }
internal fun dayName(day:Int)=java.time.DayOfWeek.of(day).getDisplayName(java.time.format.TextStyle.SHORT,java.util.Locale.getDefault())

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
