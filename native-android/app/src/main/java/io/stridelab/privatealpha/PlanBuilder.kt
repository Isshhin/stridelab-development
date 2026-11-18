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
    // Signed-out plans are persisted under an isolated on-device owner.
    // Cloud sign-in remains optional for backup; generating does not need it.
    val observedBaseline=remember(state.activities,state.plans) {
        automaticRunningBaseline(state.activities,plans=state.plans)
    }
    val baseline=remember {
        val existing=state.plans.firstOrNull {
            Engine1Engines.canManage(it) && it.policyId != SessionTemplate.POLICY_ID
        }?.let(TracksideCoaching::setupFor)
        if(existing!=null) {
            // Keep a previously chosen nonzero mileage baseline. Sparse
            // imported history must not silently raise an athlete's load.
            // When no baseline was supplied, use the eligible recorded estimate.
            if(existing.weeklyKm>0.0 || existing.recentMileageBasis in setOf(
                    "UNKNOWN","CONFIRMED_ZERO","PEAK_WEEK_ONLY"
                )) existing
            else observedBaseline?.let {
                existing.copy(weeklyKm=it.weeklyKm,recentMileageBasis="RECORDED_PARTIAL")
            } ?: existing
        } else {
            PlanRequest(policyChoice="coordinated",
                weeklyKm=observedBaseline?.weeklyKm ?: 0.0,
                recentMileageBasis=if(observedBaseline==null) "LEGACY_UNSPECIFIED" else "RECORDED_PARTIAL",
                longestKm=state.activities.filter { DashboardSummary.isRun(it) }
                    .mapNotNull { it.distanceM }.maxOrNull()?.div(1000) ?: 0.0)
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
    var tempoEquivalents by rememberSaveable { mutableStateOf("") }
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
            reportedPeakWeeklyKm=peak,timeBudgetEnabled=false,
            qualityDistanceClaims = if (BuildConfig.DEBUG && BuildConfig.ENGINE15_INDEPENDENT_RUNNING_ACTIVE &&
                input.qualityCount == 1 && input.weeks == 8 && !input.userDirectedPlanning &&
                input.userDefinedQualityWorkouts.isEmpty()) tempoEquivalents.lineSequence()
                .filter { it.isNotBlank() }.map { row ->
                    val numbers = row.trim().split(Regex("\\s+")).map { it.toIntOrNull() }
                    if (numbers.size != 5 || numbers.any { it == null || it <= 0 })
                        throw PlanConstraint("Enter five positive whole numbers per Tempo row: week, slot, warm-up metres, work metres, cooldown metres.")
                    val values = numbers.filterNotNull()
                    Engine15M5SegmentDistanceClaim(values[0], values[1], values.drop(2).map(Int::toDouble), null,
                        Engine15M5DistanceClaimProvenance.USER_ASSERTED_EQUIVALENT)
                }.toList() else emptyList()
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
    if (step == 4 && preview != null) {
        TracksideWeekReview(
            request = preview.request,
            plan = preview,
            busy = state.busy || awaiting || pendingApprovalId != null,
            onBack = {
                // A large target mismatch needs actual baseline input, not
                // another pass through the date/availability editor.
                if(Engine15MileageTargetIntegrity.review(preview)?.requiresFoundationDecision==true &&
                    !preview.request.foundationAcknowledged) {
                    step=1
                    runningSubPage=2
                    advancedBackPage=0
                } else {
                    step=2
                    scheduleSubPage=0
                }
            },
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
            error = error,
            onChooseGoalOption = { chosen ->
                error=null;beforeId=state.plans.firstOrNull()?.id;awaiting=true
                weeks=chosen.weeks.toString();targetKm=chosen.targetWeeklyKm.toString();targetSupplied=true;edit(chosen)
                actions.generate(chosen)
            }
        )
        return
    }
    if (step == 0) {
        TracksideDisciplineScreen(
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
                }
            )
            2 -> TracksideRunningAbilityScreen(
                experience = input.experience,
                weeklyKm = km.toDoubleOrNull()?.coerceIn(0.0,120.0) ?: 0.0,
                longestKm = longest.toDoubleOrNull()?.coerceIn(0.0,42.0) ?: 0.0,
                historyContent = {
                    RunningHistoryConfirmation(state) { value ->
                        km="%.1f".format(java.util.Locale.US,value)
                        edit(input.copy(weeklyKm=value,recentMileageBasis="RECORDED_PARTIAL",
                            foundationAcknowledged=false))
                    }
                    CoachFeedbackChoices(
                        "Your recent weekly mileage",
                        listOf("Usual average","Peak week only","No recent running","Unknown") +
                            if(observedBaseline!=null) listOf("Recorded estimate") else emptyList(),
                        when(input.recentMileageBasis) {
                            "SELF_REPORTED_AVERAGE" -> "Usual average"
                            "PEAK_WEEK_ONLY" -> "Peak week only"
                            "CONFIRMED_ZERO" -> "No recent running"
                            "UNKNOWN" -> "Unknown"
                            "RECORDED_PARTIAL" -> if(observedBaseline!=null) "Recorded estimate" else null
                            else -> null
                        }
                    ) { choice ->
                        when(choice) {
                            "Usual average" -> edit(input.copy(
                                recentMileageBasis="SELF_REPORTED_AVERAGE",foundationAcknowledged=false))
                            "Peak week only" -> {
                                km="0"
                                edit(input.copy(weeklyKm=0.0,recentMileageBasis="PEAK_WEEK_ONLY",
                                    foundationAcknowledged=false))
                            }
                            "No recent running" -> {
                                km="0"
                                edit(input.copy(weeklyKm=0.0,recentMileageBasis="CONFIRMED_ZERO",
                                    foundationAcknowledged=false))
                            }
                            "Recorded estimate" -> observedBaseline?.let { observed ->
                                km="%.1f".format(java.util.Locale.US,observed.weeklyKm)
                                edit(input.copy(weeklyKm=observed.weeklyKm,
                                    recentMileageBasis="RECORDED_PARTIAL",foundationAcknowledged=false))
                            }
                            else -> {
                                km="0"
                                edit(input.copy(weeklyKm=0.0,recentMileageBasis="UNKNOWN",
                                    foundationAcknowledged=false))
                            }
                        }
                    }
                    FormText("Highest recent week (optional, km)",peakWeekKm,{
                        peakWeekKm=it
                    },numeric=true)
                    Text(
                        "Use your usual weekly average, not your biggest week, as the " +
                            "starting mileage. Self-reported data is provisional. " +
                            "Recorded estimates may have incomplete coverage; Garmin import is optional.",
                        style=MaterialTheme.typography.bodySmall,
                        modifier=Modifier.testTag("builder-mileage-provenance-note")
                    )
                    if(input.trainYourWay &&
                        (input.recentMileageBasis in setOf("UNKNOWN","PEAK_WEEK_ONLY","LEGACY_UNSPECIFIED") &&
                            (km.toDoubleOrNull() ?: 0.0)==0.0 ||
                            input.recentMileageBasis=="CONFIRMED_ZERO") &&
                        (targetKm.toDoubleOrNull() ?: 0.0)>0.0) {
                        Text(
                            "Recent weekly average is missing or zero. The " +
                            "${targetKm} km/week value is a future target, not a training " +
                            "baseline. Enter your actual recent mileage here. Leaving it " +
                            "at zero requires explicitly choosing a foundation plan " +
                            "before approval.",
                            color=TracksidePalette.lime,
                            style=MaterialTheme.typography.bodySmall,
                            modifier=Modifier.testTag("builder-running-baseline-warning")
                        )
                    }
                    if(input.policyChoice=="engine15") {
                        if(BuildConfig.DEBUG) CoachFeedbackChoices(
                            "Weekly volume policy",
                            listOf("Coach recommended","User directed"),
                            if(input.userDirectedPlanning) "User directed" else "Coach recommended"
                        ) { selected -> edit(input.copy(userDirectedPlanning=selected=="User directed")) }
                        CoachFeedbackChoices("Usual quality sessions per week",listOf("0","1","2"),input.recentQualitySessionsPerWeek?.toString()) { edit(input.copy(recentQualitySessionsPerWeek=it?.toInt())) }
                    }
                },
                onExperience = { edit(input.copy(experience=it)) },
                onWeeklyKm = { value ->
                    km=value.toInt().toString()
                    edit(input.copy(weeklyKm=value,
                        recentMileageBasis="SELF_REPORTED_AVERAGE",
                        foundationAcknowledged=false))
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
            3 -> TracksideAdvancedRunning(
                guided = advancedBackPage != 0,
                weeks = input.weeks,
                userDirectedPlanning = input.userDirectedPlanning,
                userDefinedQualityWorkouts = input.userDefinedQualityWorkouts,
                weeklyKm = targetKm.toDoubleOrNull()?.coerceIn(0.0, 120.0) ?: 0.0,
                qualityCount = input.qualityCount.coerceIn(0, 2),
                maxQualityCount = minOf(
                    (input.runDays-2).coerceAtLeast(0),
                    if(input.experience==2 && input.runDays>=4 &&
                        (targetKm.toDoubleOrNull() ?: km.toDoubleOrNull() ?: 0.0)>=40.0) 2 else 1
                ),
                selectedQualityTypes = advancedQualityTypes,
                onWeeklyKm = { value ->
                    targetKm=value.toInt().toString()
                    targetSupplied=true
                    edit(input.copy(targetWeeklyKm=value,foundationAcknowledged=false))
                },
                onQualityCount = { n ->
                    val allowed = minOf(
                        (input.runDays-2).coerceAtLeast(0),
                        if(input.experience==2 && input.runDays>=4 &&
                            (targetKm.toDoubleOrNull() ?: km.toDoubleOrNull() ?: 0.0)>=40.0) 2 else 1
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
                    val allowed = minOf((input.runDays-2).coerceAtLeast(0),
                        if(input.experience==2 && input.runDays>=4 &&
                            (targetKm.toDoubleOrNull() ?: input.weeklyKm)>=40.0) 2 else 1)
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
                            (targetKm.toDoubleOrNull() ?: changed.weeklyKm)>=40.0) 2 else 1)
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
        return
    }
    if(step==1 && (input.runDays==0 || crossTrainingGoals)) {
        if(input.strengthDays>0 && !strengthGoalSaved) {
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
        return
    }
    if (step == 2) {
        // One Plan Dates screen: all availability, capacity and remaining
        // discipline placement controls are inline instead of a second page.
        // The composer still generates a draft; only explicit approval
        // activates a plan.
        TracksidePlanDetailsScreen(
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
                    },
                    researchContent={
                        if (BuildConfig.DEBUG && BuildConfig.ENGINE15_INDEPENDENT_RUNNING_ACTIVE &&
                            weeks == "8" && input.qualityCount == 1 && !input.userDirectedPlanning &&
                            input.userDefinedQualityWorkouts.isEmpty() &&
                            input.strengthDays + input.calDays + input.conditioningDays == 0) {
                            OutlinedTextField(
                                value = tempoEquivalents,
                                onValueChange = { tempoEquivalents = it.take(1024) },
                                label = { Text("Private Tempo · asserted distances") },
                                supportingText = { Text("One row per loading week: week, slot, warm-up metres, work metres, cooldown metres, separated by spaces. Request a draft with this field empty to see the available slots.") },
                                modifier = Modifier.fillMaxWidth().testTag("private-tempo-distances")
                            )
                        }
                        IndependentAndroidResearchPreviewPanel(
                            request=runCatching { request() }.getOrDefault(input),
                            onSaveResearchReview=actions.saveResearchReview,
                            savedResearchCount=state.researchDrafts.size,
                            savedResearchReviews=state.researchDrafts
                        )
                    }
                )
                error?.let { message ->
                    Text(message,color=MaterialTheme.colorScheme.error,
                        modifier=Modifier.testTag("plan-form-error"))
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
        return
    }
    if(step==3) {
        // Older builder state may resume at an obsolete page.
        // Return to the combined plan dates and availability screen.
        LaunchedEffect(Unit) {
            scheduleSubPage=0
            step=2
        }
        return
    }
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
