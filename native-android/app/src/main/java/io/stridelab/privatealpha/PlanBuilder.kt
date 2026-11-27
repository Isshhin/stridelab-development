@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.stridelab.privatealpha

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
internal fun PlanBuilder(
    state: TrainingState,
    actions: PlanActions,
    discipline: String = "all",
    onDone: () -> Unit,
    onSignIn: () -> Unit,
    historySuggestion: HistoryBaselineSuggestion? = null,
    onPreview: (String, String) -> Unit
) {
    // Signed-out plans are persisted under an isolated on-device owner.
    // Cloud sign-in remains optional for backup; generating does not need it.
    val athletePlans = remember(state.plans, state.account, state.localPlanOwner) {
        state.plans.filter { it.owner == (state.account ?: state.localPlanOwner) }
    }
    val observedBaseline=remember(state.activities,athletePlans) {
        runningPlanHistory(state.activities, athletePlans)
    }
    val detectedExperience=remember(state.activities,state.plans) {
        automaticRunningExperience(state.activities,plans=athletePlans)
    }
    val historicalEasyPace=remember(state.activities) {
        Engine15PaceZoneEstimator.inferHistoricalEasyPaceSecondsPerKm(state.activities, 360.0)
    }
    val initial=remember {
        val baseline=PlanRequest(
            experience=detectedExperience ?: 0, planningPace=historicalEasyPace,
            recentMileageBasis="UNKNOWN", qualityCount=1, qualityDays=setOf(2),
            days=setOf(1,2,3,4,5,7), longDay=7
        )
        when(discipline) {
            "run" -> sanitizeRunningScheduleDays(baseline.copy(
                runDays=3,strengthDays=0,calDays=0,conditioningDays=0,
                qualityCount=if(baseline.qualityCount>0) baseline.qualityCount else 1,
                weeklySlots=emptyMap(),disciplineGoals=emptyMap()
            ))
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
            "plyo", "iso" -> chooseStrengthTrack(
                baseline.copy(runDays=0, qualityCount=0, qualityDays=emptySet()), discipline, deselect=false
            )
            "cond" -> baseline.copy(
                goal="Conditioning", runDays=0, conditioningDays=1, qualityCount=0, qualityDays=emptySet()
            )
            else -> if (state.plans.any { it.status == "approved" }) baseline.copy(
                runDays = 0, strengthDays = 0, calDays = 0, conditioningDays = 0,
                qualityCount = 0, qualityDays = emptySet(), weeklySlots = emptyMap(),
                disciplineGoals = emptyMap(), lockedSessionDays = emptyMap()
            ) else baseline
        }.let(::sanitizeRunningScheduleDays).copy(timeBudgetEnabled=false)
    }
    var encoded by rememberSaveable { mutableStateOf(initial.encode().toString()) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    val configured=remember(encoded) { PlanRequest.decode(JSONObject(encoded)) }
    var gearOverride by rememberSaveable { mutableStateOf<String?>(null) }
    var extraResistance by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val gear = remember(gearOverride, state.savedEquipment, athletePlans, configured) {
        gearOverride?.let { PlanRequest.decode(JSONObject(it)).equipment }
            ?: builderEquipmentFor(state, configured.copy(equipment = initial.equipment))
    }
    var longestOverride by rememberSaveable { mutableStateOf<Double?>(null) }
    var experienceOverride by rememberSaveable { mutableStateOf<Int?>(null) }
    val input=observedBaseline.applyTo(configured).let { request ->
        request.copy(
            longestKm = longestOverride ?: request.longestKm,
            experience = experienceOverride ?: detectedExperience ?: request.experience,
            planningPace = historicalEasyPace,
            equipment = gear
        )
    }
    val trackCount = HybridPlanBuilder.split(input, extraResistance.toSet()).size
    val isHybrid = trackCount > 1
    fun edit(p:PlanRequest) {
        // Keep derived fields live as imports arrive while the builder is open.
        val saved = if (p.recentMileageBasis == "RECORDED_PARTIAL")
            p.copy(weeklyKm=0.0, recentMileageBasis="UNKNOWN") else p
        encoded=saved.copy(longestKm=0.0).encode().toString()
    }
    var targetKm by rememberSaveable { mutableStateOf((initial.targetWeeklyKm ?: initial.weeklyKm).toString()) };var targetSupplied by rememberSaveable { mutableStateOf(initial.targetWeeklyKm != null) }
    var peakWeekKm by rememberSaveable {
        mutableStateOf(initial.reportedPeakWeeklyKm?.toString() ?: "")
    }
    var start by rememberSaveable { mutableStateOf(maxOf(initial.start,LocalDate.now()).toString()) }
    var weeks by rememberSaveable { mutableStateOf(initial.weeks.toString()) };var minutes by rememberSaveable { mutableStateOf(initial.minutes.toString()) };var dailyMinutes by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var awaiting by rememberSaveable { mutableStateOf(false) }
    var beforeId by rememberSaveable { mutableStateOf<String?>(null) }
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingApprovalId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingTracks by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var previewTrack by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.busy,state.plans,state.planPreview,state.hybridPlanPreview,state.message) {
        if(awaiting && !state.busy) {
            val newest=state.planPreview
            val batch = state.hybridPlanPreview
            if(batch != null && batch.id != beforeId) {
                previewId = batch.id
                previewTrack = batch.drafts.first().first
                step = 4
                error = null
                awaiting = false
            } else if(newest!=null && newest.id!=beforeId && newest.status=="draft") {
                previewId=newest.id
                step=4
                error=null
                awaiting=false
            } else if(state.message!=null) {
                // The scheduler may reject conflicts after request validation.
                // Keep the athlete in review with the actual recovery reason.
                error=coachBuilderError(state.message)
                awaiting=false
            }
        }
        val target=pendingApprovalId
        if(target!=null && !state.busy) {
            val ids = pendingTracks.ifEmpty { listOf(target) }
            if(ids.all { id -> state.plans.any { it.id == id && it.status == "approved" } }) {
                pendingApprovalId=null
                pendingTracks = arrayListOf()
                error=null
                onDone()
            } else if(state.message!=null) {
                error=coachBuilderError(state.message)
                pendingApprovalId=null
                pendingTracks = arrayListOf()
            }
        }
    }
    fun request():PlanRequest=try {
        val peak=peakWeekKm.trim().takeIf { it.isNotEmpty() }?.toDouble()
        if(peak!=null && (!peak.isFinite() || peak !in 0.0..120.0))
            throw PlanConstraint("Enter a valid recent peak between 0 and 120 km/week.")
        if(input.recentMileageBasis=="PEAK_WEEK_ONLY" && (peak==null || peak<=0.0))
            throw PlanConstraint("Enter your peak weekly mileage, or select Unknown if you cannot estimate it.")
        val dailyLimit = if (isHybrid) hybridDailyLimit(dailyMinutes) else null
        input.copy(
            start=LocalDate.parse(start),weeks=weeks.toInt(),minutes=minutes.toInt(),
            dailyMinutes=dailyLimit ?: input.dailyMinutes,
            targetWeeklyKm=if(targetSupplied) targetKm.toDouble() else null,
            reportedPeakWeeklyKm=peak,timeBudgetEnabled=dailyLimit != null
        )
    } catch(e:PlanConstraint) { throw e }
      catch(e:Exception) {
        throw PlanConstraint("Enter valid dates, plan length and mileage targets.")
    }
    var previewWeek by rememberSaveable { mutableIntStateOf(0) }
    var runningSubPage by rememberSaveable { mutableIntStateOf(0) }
    var scheduleSubPage by rememberSaveable { mutableIntStateOf(0) }
    var runningSelectedSlot by rememberSaveable { mutableIntStateOf(0) }
    var otherSelectedDiscipline by rememberSaveable { mutableStateOf("str") }
    var otherSelectedSlot by rememberSaveable { mutableIntStateOf(0) }
    var runningCustom by rememberSaveable { mutableStateOf(false) }
    var desiredQualityCount by rememberSaveable { mutableIntStateOf(initial.qualityCount) }
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
            in Engine15DisciplineProgression.goals.getValue("str") -> Engine1Engines.goal(initial, "str")
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
    val batch = state.hybridPlanPreview?.takeIf { it.id == previewId }
    val preview = batch?.drafts?.firstOrNull { it.first == previewTrack }?.second
        ?: batch?.drafts?.firstOrNull()?.second ?: state.displayedPlanPreview?.takeIf { it.id == previewId }
    androidx.activity.compose.BackHandler(enabled = step == 4 && !state.busy) {
        actions.discardPlanPreview()
        step = 2
        scheduleSubPage = 0
    }
    if (step == 3 || (step == 4 && preview == null && !awaiting && pendingApprovalId == null)) {
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
            2 -> 13
            4 -> if (runningRoute == "custom") 14 else 15
            3 -> if (runningRoute == "custom") 15 else 14
            else -> 16
        }
        step == 1 && (input.runDays == 0 || crossTrainingGoals) -> when {
            input.strengthDays > 0 && !strengthGoalSaved && strengthTrack(input) == "str" -> 20
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
                val controls: (@Composable () -> Unit)? = if (batch != null) {
                    { HybridBlueprintControls(batch, previewTrack, previewWeek) { previewTrack = it } }
                } else null
                TracksideWeekReview(
                    request = activePreview.request,
                    plan = activePreview,
                    busy = state.busy || awaiting || pendingApprovalId != null,
                    onBack = {
                        actions.discardPlanPreview()
                        step = 2
                        scheduleSubPage = 0
                    },
                    onContinue = {
                        if (!state.busy && pendingApprovalId == null) {
                            error = null
                            pendingApprovalId = batch?.id ?: activePreview.id
                            if (batch != null) {
                                pendingTracks = ArrayList(batch.drafts.map { it.second.id })
                                actions.approveHybrid(batch.id)
                            } else actions.approve(activePreview.id, activePreview.revision)
                        }
                    },
                    onOpenSession = { sessionId -> onPreview(activePreview.id, sessionId) },
                    weekIndex = previewWeek,
                    onWeekChange = { previewWeek = it },
                    error = error,
                    supportReview = state.planPreviewSupportReview?.takeIf { it.candidate.id == activePreview.id },
                    recoveryNote = if (state.planPreviewSupportReview == null)
                        PlanScheduleValidation.crossPlanRecoveryNote(activePreview, state.plans) else null,
                    onChooseGoalOption = { chosen ->
                        error = null; beforeId = batch?.id ?: state.planPreview?.id; awaiting = true
                        val revised = if (batch != null) chosen.copy(
                            strengthDays = input.strengthDays, calDays = input.calDays,
                            conditioningDays = input.conditioningDays,
                            disciplineGoals = input.disciplineGoals + ("run" to chosen.goal),
                            weeklySlots = input.weeklySlots.filterKeys { it != "run" } + chosen.weeklySlots
                        ) else chosen
                        weeks = revised.weeks.toString(); targetKm = revised.targetWeeklyKm.toString(); targetSupplied = true; edit(revised)
                        if (batch != null) actions.generateHybrid(HybridPlanBuilder.split(revised, extraResistance.toSet())) else actions.generate(revised)
                    },
                    activities = state.activities,
                    activationLabel = batch?.let { "Activate ${it.drafts.size} tracks" } ?: "Activate Plan",
                    trackControls = controls
                )
            }
            0 -> TracksideDisciplineScreen(
            request = input,
            onToggle = { d ->
                val active = when(d) {
                    "run" -> input.runDays > 0
                    "str", "plyo", "iso" -> d in extraResistance ||
                        input.strengthDays > 0 && strengthTrack(input) == d
                    "cal" -> input.calDays > 0
                    else -> input.conditioningDays > 0
                }
                val toggled=when(d) {
                    "run" -> {
                        if (active) {
                            desiredQualityCount = 0
                            input.copy(runDays=0, qualityCount=0, qualityDays=emptySet())
                        } else {
                            desiredQualityCount = 1
                            sanitizeRunningScheduleDays(input.copy(runDays=3, qualityCount=1))
                        }
                    }
                    "str", "plyo", "iso" -> {
                        val primary = strengthTrack(input)
                        when {
                            d in extraResistance -> {
                                extraResistance = ArrayList(extraResistance - d)
                                input
                            }
                            active && extraResistance.isNotEmpty() -> {
                                val next = extraResistance.first()
                                extraResistance = ArrayList(extraResistance - next)
                                chooseStrengthTrack(input, next, deselect = false)
                            }
                            !active && input.strengthDays > 0 && d == "str" -> {
                                extraResistance = ArrayList(extraResistance + primary)
                                chooseStrengthTrack(input, d, deselect = false)
                            }
                            !active && input.strengthDays > 0 -> {
                                extraResistance = ArrayList(extraResistance + d)
                                input
                            }
                            else -> chooseStrengthTrack(input, d, active)
                        }
                    }
                    "cal" -> input.copy(calDays=if(active)0 else 2)
                    else -> input.copy(conditioningDays=if(active)0 else 1)
                }
                // A deselected discipline must not leave phantom fixed sessions
                // or stale discipline goals in a future generated plan.
                val cleaned=toggled.copy(
                    weeklySlots=if(active && d !in setOf("str", "plyo", "iso")) toggled.weeklySlots-d else toggled.weeklySlots,
                    disciplineGoals=if(active && d !in setOf("str", "plyo", "iso")) toggled.disciplineGoals-d
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
                    else -> Engine1Engines.discipline(cleaned.goal)?.let {
                        cleaned.disciplineSessionCount(it) > 0
                    } ?: true
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
            extraResistance = extraResistance.toSet(),
            onContinue = {
                step=1
                runningSubPage=0
                crossTrainingGoals=false
                strengthGoalSaved=strengthTrack(input) != "str"
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
                            runningSubPage = 2
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
                val setWeeklyKm: (Double) -> Unit = { value ->
                    edit(input.copy(
                        weeklyKm = kotlin.math.round(value * 10) / 10,
                        recentMileageBasis = if (value >= 0.05) "SELF_REPORTED_AVERAGE" else "UNKNOWN",
                        foundationAcknowledged = false
                    ))
                }
                val setLongestKm: (Double) -> Unit = { value ->
                    longestOverride = kotlin.math.round(value * 10) / 10
                }
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val ownerScope = state.account ?: state.localGarminOwner
                val hrPrefs = remember(ctx) { AthleteHeartRatePreferences(ctx) }
                val garminZoneStore = remember(ctx) { GarminZonePreferenceStore(ctx) }
                var hrSettings by remember(ownerScope) { mutableStateOf(hrPrefs.settings(ownerScope)) }
                val garminHrProfile = remember(ownerScope, state.account, state.localGarminOwner) {
                    listOfNotNull(state.account, state.localGarminOwner).distinct().firstNotNullOfOrNull { acc ->
                        garminZoneStore.load(acc)?.profile?.let { GarminHeartRateZones.forRunning(listOf(it)) }
                    }?.let { ExecutionHrZoneProfile.fromGarmin(it) }
                }
                val activityHrProfile = remember(state.details) {
                    state.details.values.firstNotNullOfOrNull {
                        ExecutionHrZoneProfile.fromActivitySourceZones(it.sourceHeartRateZones)
                    }
                }
                val manualHrProfile = remember(hrSettings) {
                    HeartRateZoneAnalysis.toExecutionZoneProfile(hrSettings)
                }
                val activeHrProfile = manualHrProfile ?: garminHrProfile ?: activityHrProfile
                val hrSourceLabel = when {
                    manualHrProfile != null -> HeartRateZoneAnalysis.methodSummary(hrSettings)
                    garminHrProfile != null -> "Synced Garmin Running HR Zones"
                    activityHrProfile != null -> "Detected from Recent Activity Zones"
                    else -> null
                }
                TracksideRunningAbilityScreen(
                    experience = input.experience,
                    weeklyKm = input.weeklyKm.takeIf { input.recentMileageBasis != "UNKNOWN" },
                    longestKm = input.longestKm.takeIf { it > 0.0 || longestOverride != null },
                    onExperience = { experienceOverride = it },
                    onWeeklyKm = setWeeklyKm,
                    onLongestKm = setLongestKm,
                    historyContent = {
                        historySuggestion?.let {
                            Spacer(Modifier.height(12.dp))
                            HistoryBaselineCard(it, setWeeklyKm, setLongestKm)
                        }
                    },
                    weeklySource = when (input.recentMileageBasis) {
                        "RECORDED_PARTIAL" -> "From recent runs"
                        "SELF_REPORTED_AVERAGE" -> "Entered by you"
                        "CONFIRMED_ZERO" -> "No recent running"
                        else -> "No estimate yet"
                    },
                    longestSource = when {
                        longestOverride != null -> "Entered by you"
                        input.longestKm > 0 -> "Last 30 days"
                        else -> "No recent distance"
                    },
                    onConfirmNoRecentRunning = if (input.weeklyKm == 0.0 && input.longestKm == 0.0 &&
                        input.recentMileageBasis != "CONFIRMED_ZERO") {
                        {
                            experienceOverride = 0
                            edit(input.copy(recentMileageBasis = "CONFIRMED_ZERO"))
                        }
                    } else null,
                    onBack = {
                        runningSubPage = when (runningRoute) {
                            "race" -> 1
                            "custom" -> 0
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
                    progressStep = if (runningRoute == "custom") 2 else 3,
                    autoDetectedExperience = detectedExperience != null && experienceOverride == null,
                    runningTargetMode = input.runningTargetMode,
                    onRunningTargetMode = { mode ->
                        if (mode == "HR") {
                            val floors = activeHrProfile?.let {
                                listOf(it.z1FloorBpm, it.z2FloorBpm, it.z3FloorBpm, it.z4FloorBpm, it.z5FloorBpm)
                            } ?: emptyList()
                            edit(input.copy(runningTargetMode = "HR", hrZoneFloors = floors, hrMaxBpm = activeHrProfile?.maxHrBpm))
                        } else {
                            edit(input.copy(runningTargetMode = "PACE", hrZoneFloors = emptyList(), hrMaxBpm = null))
                        }
                    },
                    hrZoneProfile = activeHrProfile,
                    hrZoneSourceLabel = hrSourceLabel,
                    hrSettings = hrSettings,
                    onSaveHrSettings = { newSettings ->
                        hrPrefs.save(ownerScope, newSettings)
                        hrSettings = newSettings
                        val p = HeartRateZoneAnalysis.toExecutionZoneProfile(newSettings)
                        if (p != null) {
                            edit(input.copy(
                                runningTargetMode = "HR",
                                hrZoneFloors = listOf(p.z1FloorBpm, p.z2FloorBpm, p.z3FloorBpm, p.z4FloorBpm, p.z5FloorBpm),
                                hrMaxBpm = p.maxHrBpm
                            ))
                        }
                    }
                )
            }
            3 -> TracksideAdvancedRunning(
                guided = advancedBackPage != 0,
                weeks = input.weeks,
                userDirectedPlanning = input.userDirectedPlanning,
                userDefinedQualityWorkouts = input.userDefinedQualityWorkouts,
                weeklyKm = targetKm.toDoubleOrNull()?.coerceIn(0.0, 120.0) ?: 0.0,
                qualityCount = input.qualityCount.coerceIn(0, 2),
                maxQualityCount = (input.runDays - 2).coerceIn(0, 2),
                selectedQualityTypes = advancedQualityTypes,
                onWeeklyKm = { value ->
                    targetKm = value.toInt().toString()
                    targetSupplied = true
                    edit(input.copy(targetWeeklyKm = value, foundationAcknowledged = false))
                },
                onQualityCount = { n ->
                    desiredQualityCount = n
                    val allowed = (input.runDays - 2).coerceIn(0, 2)
                    val q = n.coerceAtMost(allowed)
                    val updated = sanitizeRunningScheduleDays(input.copy(qualityCount = q))
                    edit(
                        if ("run" in updated.weeklySlots) {
                            val slots = WeeklyPlacement.days(updated, "run")
                            updated.copy(
                                qualityDays = slots.drop(1).take(q).filter { it in 1..7 }.toSet()
                            )
                        } else updated
                    )
                },
                onToggleQuality = { quality ->
                    val updated =
                        if (quality in advancedQualityTypes) advancedQualityTypes-quality
                        else advancedQualityTypes+quality
                    advancedQualityCsv = updated.sorted().joinToString("|")
                    edit(input.copy(allowedQualityTypes=updated, preferredQualityExplicit=false))
                },
                onSaveQualityWorkouts = { workouts ->
                    edit(input.copy(userDefinedQualityWorkouts=workouts))
                },
                onRemoveQualityWorkout = { key ->
                    edit(input.copy(userDefinedQualityWorkouts=input.userDefinedQualityWorkouts-key))
                },
                onBack = { runningSubPage = if (runningRoute == "custom") 4 else advancedBackPage },
                onContinue = {
                    when {
                        runningRoute == "custom" -> {
                            if (input.strengthDays + input.calDays + input.conditioningDays > 0) {
                                crossTrainingGoals = true
                                strengthGoalSaved = false
                            } else {
                                step = 2
                                scheduleSubPage = 0
                            }
                        }
                        advancedBackPage == 5 -> runningSubPage = 5
                        else -> runningSubPage = 2
                    }
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
                    val updated = input.copy(
                        weeklySlots = if (enabled) input.weeklySlots +
                            ("run" to WeeklyPlacement.days(input, "run"))
                        else input.weeklySlots - "run"
                    )
                    edit(sanitizeRunningScheduleDays(updated))
                },
                qualityPreference = qualityPreference,
                onQualityPreference = { selected ->
                    qualityPreference=selected
                    edit(input.copy(preferredQuality=selected, preferredQualityExplicit=false))
                },
                onQualityCount = { n ->
                    desiredQualityCount = n
                    val allowed = (input.runDays - 2).coerceIn(0, 2)
                    val q = n.coerceAtMost(allowed)
                    val updated = sanitizeRunningScheduleDays(input.copy(qualityCount = q))
                    edit(
                        if ("run" in updated.weeklySlots) {
                            val slots = WeeklyPlacement.days(updated, "run")
                            updated.copy(
                                qualityDays = slots.drop(1).take(q).filter { it in 1..7 }.toSet()
                            )
                        } else updated
                    )
                },
                onRunningCount = { n ->
                    if (input.qualityCount > desiredQualityCount) {
                        desiredQualityCount = input.qualityCount
                    }
                    val changed = if (runningCustom)
                        WeeklyPlacement.resize(input, "run", n)
                    else input.copy(
                        runDays = n,
                        weeklySlots = input.weeklySlots - "run"
                    )
                    val allowed = (n - 2).coerceIn(0, 2)
                    val q = desiredQualityCount.coerceAtMost(allowed)
                    val updated = sanitizeRunningScheduleDays(changed.copy(qualityCount = q))
                    edit(
                        if ("run" in updated.weeklySlots) {
                            val slots = WeeklyPlacement.days(updated, "run")
                            updated.copy(
                                qualityDays = slots.drop(1).take(q).filter { it in 1..7 }.toSet()
                            )
                        } else updated
                    )
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
                onBack = { runningSubPage = 2 },
                progressStep = if (runningRoute == "custom") 3 else 4,
                onContinue = {
                    // Save even an unchanged default once the user chose
                    // manual placement, so the engine must honor those days.
                    if(runningCustom && "run" !in input.weeklySlots) {
                        edit(input.copy(
                            weeklySlots=input.weeklySlots +
                                ("run" to WeeklyPlacement.days(input,"run"))
                        ))
                    }
                    if (runningRoute == "custom") {
                        runningSubPage = 3
                    } else if (input.strengthDays+input.calDays+input.conditioningDays>0) {
                        crossTrainingGoals=true
                        strengthGoalSaved=false
                    } else {
                        step=2
                        scheduleSubPage=0
                    }
                }
            )
        }
        20, 21, 22, 23 -> if(input.strengthDays>0 && !strengthGoalSaved && strengthTrack(input) == "str") {
            TracksideStrengthGoalScreen(
                selected=strengthGoalLabel,
                onSelect={ strengthGoalLabel=it },
                onBack={
                    if(input.runDays>0) {
                        crossTrainingGoals=false
                        runningSubPage = if (runningRoute == "custom") 3 else 4
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
                onSaveEquipment={ equipment ->
                    gearOverride = input.copy(equipment = equipment).encode().toString()
                    (state.account ?: state.localPlanOwner)?.let { owner ->
                        actions.saveBuilderEquipment(owner, equipment)
                    }
                },
                onBack={ if (strengthTrack(input) == "str") strengthGoalSaved=false else step=0 },
                onEditGoal={ if (strengthTrack(input) == "str") strengthGoalSaved=false else step=0 },
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
                        runningSubPage = if (runningRoute == "custom") 3 else 4
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
                            edit(sanitizeRunningScheduleDays(input.copy(
                                days=days,
                                longDay=if(input.longDay in days)input.longDay
                                    else (days.maxOrNull() ?: input.longDay),
                                qualityDays=input.qualityDays.intersect(days)
                            )))
                        }
                    },
                    onRemovePinnedDay={ day ->
                        edit(sanitizeRunningScheduleDays(WeeklyPlacement.makeUnavailable(input,day)))
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
                    if (message.contains("smallest safe dose") && input.qualityCount > 0 &&
                        input.userDefinedQualityWorkouts.isEmpty() && input.lockedSessionDays.isEmpty() &&
                        "run" !in input.weeklySlots
                    ) {
                        TextButton(onClick = {
                            val count = input.qualityCount - 1
                            desiredQualityCount = count
                            edit(sanitizeRunningScheduleDays(input.copy(qualityCount = count,
                                qualityDays = input.qualityDays.sorted().take(count).toSet())))
                            error = null
                        }) { Text("Review with fewer quality days", color = PlanBuilderPalette.accent) }
                    }
                }
                if (isHybrid) {
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().testTag("builder-training-tracks"),
                        color = PlanBuilderPalette.surface,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, PlanBuilderPalette.border)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("$trackCount training tracks", color = PlanBuilderPalette.text)
                            if (extraResistance.isNotEmpty()) Text(
                                extraResistance.joinToString { "${trainingTrackLabel(it)} · 1 session/week" },
                                color = PlanBuilderPalette.secondary
                            )
                            OutlinedTextField(
                                value = dailyMinutes,
                                onValueChange = { dailyMinutes = it },
                                label = { Text("Daily training limit (min, optional)") },
                                modifier = Modifier.fillMaxWidth().testTag("builder-hybrid-daily-limit"),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                    }
                }
            },
            onBack={
                step=1
                if(input.runDays>0) {
                    if(input.strengthDays+input.calDays+input.conditioningDays>0) {
                        crossTrainingGoals=true
                        strengthGoalSaved=true
                        strengthSetupSaved=true
                    } else runningSubPage = if (runningRoute == "custom") 3 else 4
                }
                crossGoalsSaved=input.calDays>0
            },
            onContinue={
                if(!state.busy && !awaiting) {
                    error=null
                    try {
                        val rawP = request()
                        val p = if ("run" !in rawP.weeklySlots) sanitizeRunningScheduleDays(rawP) else rawP
                        if(p.start<LocalDate.now())
                            throw PlanConstraint("Choose today or a future start date.")
                        val conflict=compactScheduleError(p)
                        if(conflict!=null)throw PlanConstraint(conflict)
                        IndependentEngineRouteGate.requireProduction(
                            IndependentEngineRoute.EXISTING_COACH
                        )
                        val tracks = HybridPlanBuilder.split(p, extraResistance.toSet())
                        val selected = if (tracks.size == 1) TracksideCoaching.request(p) else null
                        beforeId=state.hybridPlanPreview?.id ?: state.planPreview?.id
                        awaiting=true
                        if (selected != null) actions.generate(selected) else actions.generateHybrid(tracks)
                    } catch(e:PlanConstraint) { error=coachBuilderError(e.message.orEmpty()) }
                }
            }
        )
        else -> {
            LaunchedEffect(Unit) { step = 0 }
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

private fun builderGoals(p:PlanRequest)=ALL_SUPPORTED_GOALS.filter { goal -> when(goal) {
    "General fitness"->true
    "Aerobic foundation","Improve 5K","Improve 10K","Half marathon","Marathon"->p.runDays>0
    "General strength","Muscle development","Strength endurance"->p.strengthDays>0
    "Calisthenics foundation","Pull-up progression","Push-up progression","Core strength and control","Handstand foundations"->p.calDays>0
    "Conditioning"->p.conditioningDays>0
    else->Engine1Engines.discipline(goal)?.let { p.disciplineSessionCount(it) > 0 } == true
} }

/** Repairs `longDay` and `qualityDays` against available `days`, preserving user pins when manual. */
internal fun sanitizeRunningScheduleDays(p: PlanRequest): PlanRequest {
    if (p.runDays <= 0) return p
    val safeLongDay = if (p.longDay in p.days) p.longDay else (p.days.maxOrNull() ?: p.longDay)
    if (p.qualityCount <= 0) return p.copy(longDay = safeLongDay, qualityDays = emptySet())
    val manualSlots = p.weeklySlots["run"]
    if (manualSlots != null) {
        val pinnedQuality = manualSlots.drop(1).take(p.qualityCount)
            .filter { it in p.days && it != safeLongDay }
            .toSet()
        return p.copy(longDay = safeLongDay, qualityDays = pinnedQuality)
    }
    val valid = p.qualityDays.filter { it in p.days && it != safeLongDay }.toMutableList()
    if (valid.size < p.qualityCount) {
        fun circularDist(a: Int, b: Int) = minOf(kotlin.math.abs(a - b), 7 - kotlin.math.abs(a - b))
        val remaining = p.days.filter { it != safeLongDay && it !in valid }
            .sortedWith(
                compareByDescending<Int> { circularDist(it, safeLongDay) }.thenBy { it }
            )
        for (c in remaining) {
            if (valid.size >= p.qualityCount) break
            if (circularDist(c, safeLongDay) >= 2 && valid.all { circularDist(c, it) >= 2 }) {
                valid.add(c)
            }
        }
        for (c in remaining) {
            if (valid.size >= p.qualityCount) break
            if (c !in valid) valid.add(c)
        }
    }
    return p.copy(longDay = safeLongDay, qualityDays = valid.take(p.qualityCount).toSet())
}
