package io.stridelab.privatealpha

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import kotlin.math.roundToInt
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import androidx.navigation.NavHostController
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class NativeMainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StrideTheme { NativeStrideApp() } }
    }
}

@Composable internal fun StrideTheme(content: @Composable () -> Unit) {
    // Match the approved Trackside 3.0 review. Use the system sans-serif (Roboto on
    // Android) until the final licensed type assets are bundled and checked.
    val displayFont = FontFamily.SansSerif
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = TracksidePalette.red, onPrimary = TracksidePalette.text,
            background = TracksidePalette.backgroundBottom,
            surface = TracksidePalette.surface,
            surfaceVariant = TracksidePalette.surfaceVariant,
            onSurface = TracksidePalette.text,
            onBackground = TracksidePalette.text,
            onSurfaceVariant = TracksidePalette.textMuted,
            secondary = TracksidePalette.strength,
            secondaryContainer = Color(0xFF20394C),
            onSecondaryContainer = TracksidePalette.text,
            tertiary = TracksidePalette.calisthenics,
            outline = TracksidePalette.outline,
            outlineVariant = Color(0xFF3A5363),
            surfaceContainer = TracksidePalette.surface,
            surfaceContainerHigh = TracksidePalette.surfaceRaised
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = displayFont, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
            headlineMedium = TextStyle(fontFamily = displayFont, fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp),
            headlineSmall = TextStyle(fontFamily = displayFont, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
            titleLarge = TextStyle(fontFamily = displayFont, fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
            labelMedium = TextStyle(fontFamily = displayFont, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp)
        ),
        shapes = Shapes(medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp)),
        content = content
    )
}

@Composable internal fun NativeStrideApp(model: TrainingViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle(context=kotlinx.coroutines.Dispatchers.Main.immediate)
    val online by model.online.collectAsStateWithLifecycle(context=kotlinx.coroutines.Dispatchers.Main.immediate)
    val actions = remember(model) { NativeActions(model::refresh, model::checkIntervals, model::syncIntervals, model::signOut, model::signIn, model::loadDetail, model::refreshWellness, model.planActions(), syncGarmin=model::syncGarmin, loadGarminGps=model::loadGarminGps) }
    NativeStrideContent(state, online, actions)
}

/** Presentation boundary allows isolated screenshots without a real session or fake production data. */
internal data class NativeActions(val refresh: () -> Unit, val checkIntervals: () -> Unit,
    val syncIntervals: () -> Unit, val signOut: () -> Unit, val signIn: (String, String) -> Unit, val loadDetail: (String) -> Unit = {}, val refreshWellness: () -> Unit = {}, val planning:PlanActions = PlanActions(),
    val syncGarmin: (suspend (GarminPersonalSource) -> GarminConnectionStatus)? = null,
    val loadGarminGps: ((String,GarminPersonalSource)->Unit)?=null)

/** Save each tab's root state, never its transient detail/auth destination. */
private fun NavHostController.openTabRoot(path: String) {
    while(currentDestination?.route in setOf("activity/{id}", "auth", "builder/{discipline}", "workout/{planId}/{sessionId}", "practice/{key}", "manage/{planId}/{date}")) {
        if(!popBackStack()) break
    }
    navigate(path) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    // Also normalize stacks restored from an older build that saved a child destination.
    if(currentDestination?.route != path) popBackStack(path, inclusive = false)
}

private fun NavHostController.openWorkout(planId:String,sessionId:String) {
    if(currentDestination?.route!="activities") openTabRoot("training")
    navigate("workout/${android.net.Uri.encode(planId)}/${android.net.Uri.encode(sessionId)}")
}

private fun NavHostController.openActivity(id: String) {
    openTabRoot("activities")
    navigate("activity/$id")
}

@Composable internal fun NativeStrideContent(state: TrainingState, online: Boolean, actions: NativeActions) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val groupedActivities = remember(state.activities) {
        ActivityDisplayConsolidation.groups(state.activities)
    }
    val visibleActivities = remember(groupedActivities) { groupedActivities.map { it.primary } }
    CompositionLocalProvider(LocalProfileAction provides { nav.openTabRoot("settings") }) {
    Scaffold(
        bottomBar = {
            // The approved 25D / 25E plan-decision pages do not use the five-tab dock.
            if(route != "missed/{planId}/{sessionId}" &&
                route != "review-move/{planId}/{sessionId}/{date}" &&
                route != "activity/{id}" && route != "builder/{discipline}") {
                Box(
                    Modifier.fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    TracksideFloatingNavigation(route) { path -> nav.openTabRoot(path) }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(TracksidePalette.backgroundTop, TracksidePalette.backgroundBottom))).padding(padding).imePadding()) {
            if(state.busy || state.initializing) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("loading"))
            if(!online) Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Text("Offline · Saved activities remain available", Modifier.fillMaxWidth().padding(horizontal = 20.dp,vertical = 10.dp).testTag("offline-status"),style = MaterialTheme.typography.bodyMedium)
            }
            state.message?.let { Surface(color = MaterialTheme.colorScheme.surfaceVariant) { Text(it, Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium) } }
            NavHost(navController = nav, startDestination = "home", modifier = Modifier.weight(1f)) {
                composable("home") {
                    // The visual Today shell is data-driven and retains the existing root navigation route.
                    val greeting = when(java.time.LocalTime.now().hour) {
                        in 5..11 -> "Good morning"
                        in 12..17 -> "Good afternoon"
                        else -> "Good evening"
                    }
                    Screen("Today", greeting, dense = true) {
                        if(state.initializing) item { Text("Opening your saved training…") }
                        else if(state.account == null) item {
                            EmptyState("Start with your history", "Sign in to synchronize your activities. Saved history is available offline.")
                            Button(onClick = { nav.navigate("auth") }, modifier = Modifier.fillMaxWidth()) { Text("Sign in") }
                        } else {
                            val plan = state.plans.firstOrNull { it.status == "approved" }
                            val todaySessions = plan?.sessions?.filter { it.date == java.time.LocalDate.now() && it.status != "unassigned" } ?: emptyList()
                            val activeMultiSession = todaySessions.size >= 2 &&
                                todaySessions.any { it.status == "completed" }
                            val next = plan?.sessions?.filter {
                                it.date >= java.time.LocalDate.now() && it.status in setOf("planned", "in_progress")
                            }?.sortedWith(compareBy<PlannedSession>({ it.date }, { it.id }))?.firstOrNull()
                            if(next != null && plan != null && !activeMultiSession) item(key = "today-featured") {
                                TracksideTodayHero(plan.cardPresentation(next)) { nav.openWorkout(plan.id, next.id) }
                            }
                            if(plan == null) item {
                                SignalCard {
                                    Text("No active plan", style = MaterialTheme.typography.titleLarge)
                                    Text("Create and approve a plan to see your upcoming workouts here.",
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Button(onClick = { nav.openTabRoot("training") }, modifier = Modifier.fillMaxWidth()) { Text("Create a training plan") }
                                }
                            }
                            if(plan != null) {
                                if (activeMultiSession) item(key = "today-active-status") {
                                    TracksideParityTodayActiveStatus(plan, todaySessions)
                                }
                                item {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (activeMultiSession) "Your workouts" else "Today's plan",
                                            style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                        TextButton(onClick = { nav.openTabRoot("training") }) { Text("View week ›") }
                                    }
                                }
                                val today = todaySessions.map(plan::cardPresentation)
                                if(today.isEmpty()) item {
                                    Text("No workouts scheduled for today", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                                today.forEach { session ->
                                    item(key = "today-" + session.id) {
                                        if (activeMultiSession)
                                            TracksideParityWorkoutCard(session, false) {
                                                nav.openWorkout(plan.id, session.id)
                                            }
                                        else TracksideTodaySession(session) {
                                            nav.openWorkout(plan.id, session.id)
                                        }
                                    }
                                }
                            }
                            item { Text("Recent activity", style = MaterialTheme.typography.titleLarge) }
                            groupedActivities.take(2).forEach { group ->
                                item(key = "recent-" + group.primary.id) {
                                    TracksideTodayActivity(group.primary,
                                        sourceLabel=group.sourceLabel) {
                                        nav.openActivity(group.primary.id)
                                    }
                                }
                            }
                            if(state.activities.isEmpty()) item {
                                Text("No saved activities yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            item { SyncStatus(state) }
                            item { WeeklyDashboard(state.activities, plans = state.plans) }
                            item { WellnessOverview(state.wellness, actions.refreshWellness, online && !state.busy && !state.needsSignIn) }
                            item { HistoryOverview(visibleActivities) }
                            if(visibleActivities.count { it.durationS != null } >= 2)
                                item { RecordedDurationChart(visibleActivities) }
                            item { FilledTonalButton(onClick = actions.refresh, enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Refresh history") } }
                            if(state.needsSignIn) item { Button(onClick = { nav.navigate("auth") }) { Text("Sign in again") } }
                        }
                    }
                }
                composable("activities") {
                    Screen("Activities", "YOUR HISTORY") {
                        item { SyncStatus(state) }
                        if(state.account != null && visibleActivities.isNotEmpty())
                            item { HistoryOverview(visibleActivities) }
                        if(state.account == null) item { EmptyState("Sign in to view your history", "Only records belonging to your signed-in account are displayed."); Button(onClick = { nav.navigate("auth") }) { Text("Sign in") } }
                        else {
                            item { OutlinedButton(onClick = actions.refresh, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Refresh history") } }
                            if(state.activities.isEmpty()) item { EmptyState("No activities cached", "Your saved history will appear here after a successful refresh.") }
                            val logged=state.plans.flatMap { p -> p.sessions.filter { it.status=="completed" && (it.linkedActivityId==null || state.activities.none { a->a.id==it.linkedActivityId }) }.map { p to it } }.sortedByDescending { it.second.log?.at }
                            if(logged.isNotEmpty()) item { Text("WORKOUT LOGS · ON THIS DEVICE",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary) }
                            logged.forEach { (p,s) -> item(key="logged-${s.id}") { PlannedWorkoutCard(s) { nav.openWorkout(p.id,s.id) } } }
                            items(groupedActivities, key = { it.primary.id }) { group ->
                                ActivityRow(group.primary, sourceLabel=group.sourceLabel) {
                                    nav.openActivity(group.primary.id)
                                }
                            }
                        }
                    }
                }
                composable("activity/{id}") { detail ->
                    val activity = state.activities.firstOrNull { it.id == detail.arguments?.getString("id") }
                    // Garmin stays primary in the activity view. Matching
                    // Intervals.icu streams remain explicitly supplemental.
                    val matchedGroup = groupedActivities.firstOrNull { it.primary.id == activity?.id }
                    val detailSource = matchedGroup?.detailedSource ?: activity
                    val context=LocalContext.current
                    val garminSource=remember(state.account,context) {
                        state.account?.let { account ->
                            GarminPersonalSource(account,GarminCredentialVault(context.applicationContext),
                                GarminPersonalClient())
                        }
                    }
                    val garminId=activity?.takeIf { it.source=="garmin" }?.id
                    val onLoadGarminGps:(()->Unit)?=actions.loadGarminGps?.let { load ->
                        if(garminId!=null && garminSource!=null) ({ load(garminId,garminSource) })
                        else null
                    }
                    var automaticDetailAttempted by rememberSaveable(detailSource?.id) { mutableStateOf(false) }
                    var activityTab by rememberSaveable(activity?.id) { mutableIntStateOf(0) }
                    var matteStyle by rememberSaveable(activity?.id) { mutableStateOf(false) }
                    LaunchedEffect(detailSource?.id, online, state.busy, state.needsSignIn) {
                        // Load real supplementary Intervals.icu streams when
                        // Garmin is the canonical summary. Never use the Garmin
                        // local summary ID as a remote stream endpoint.
                        if (!automaticDetailAttempted && detailSource != null &&
                            detailSource.supportsPace &&
                            state.account != null && online && !state.busy && !state.needsSignIn &&
                            !state.details.containsKey(detailSource.id)) {
                            if(detailSource.source!="garmin") {
                                automaticDetailAttempted=true
                                actions.loadDetail(detailSource.id)
                            } else if(onLoadGarminGps!=null) {
                                automaticDetailAttempted=true
                                onLoadGarminGps()
                            }
                        }
                    }
                    if (activity == null) {
                        Screen("Activity unavailable", "RECORDED ACTIVITY", compactTitle = true) {
                            item { TextButton(onClick = { nav.popBackStack() }) {
                                Text("Back to history")
                            } }
                            item { EmptyState("No activity available",
                                "This record is not in the current account's saved cache.") }
                        }
                    } else {
                        TracksideFigmaActivityDetailSurface(
                            activity = activity,
                            recorded = detailSource?.let { state.details[it.id] },
                            detailSource = detailSource ?: activity,
                            matchedSourceIds = matchedGroup?.let { group ->
                                (listOf(group.primary) + group.additionalSources).map { it.id }.toSet()
                            } ?: setOf(activity.id),
                            history = state.activities,
                            state = state,
                            actions = actions,
                            online = online,
                            selectedTab = activityTab,
                            onTab = { activityTab = it },
                            matte = matteStyle,
                            onToggleMatte = { matteStyle = !matteStyle },
                            onBack = { nav.popBackStack() },
                            onLoadGarminGps = onLoadGarminGps
                        )
                    }
                }
                composable("training") { TrainingScreen(state,actions.planning,{nav.navigate("builder/all")},{p,s->nav.openWorkout(p,s)},{nav.navigate("auth")},{p,d->nav.navigate("manage/${android.net.Uri.encode(p)}/$d")}) }
                composable("manage/{planId}/{date}") { back ->
                    val planId = back.arguments?.getString("planId") ?: ""
                    val plan = state.plans.firstOrNull { it.id == planId }
                    val selected = runCatching { java.time.LocalDate.parse(back.arguments?.getString("date")) }
                        .getOrDefault(java.time.LocalDate.now())
                    TracksideManagePlanScreen(
                        plan = plan, selected = selected, busy = state.busy,
                        actions = actions.planning,
                        onBack = {
                            // Return to Training explicitly. A saved tab destination may
                            // not be the immediate prior NavHost entry after tab restore.
                            if (!nav.popBackStack("training", inclusive = false)) {
                                nav.openTabRoot("training")
                            }
                        },
                        onBuildDraft = { nav.navigate("builder/all") },
                        onWorkout = { sessionId ->
                            nav.navigate("workout/${android.net.Uri.encode(planId)}/${android.net.Uri.encode(sessionId)}")
                        },
                        onReviewMove = { sessionId, date ->
                            nav.navigate("review-move/${android.net.Uri.encode(planId)}/${android.net.Uri.encode(sessionId)}/$date")
                        }
                    )
                }
                composable("builder/{discipline}") { builder -> PlanBuilder(state,actions.planning,builder.arguments?.getString("discipline") ?: "all",{nav.openTabRoot("training")},{nav.navigate("auth")},{p,s->nav.navigate("workout/${android.net.Uri.encode(p)}/${android.net.Uri.encode(s)}")}) }
                composable("library") { LibraryScreen(state,actions.planning,{p,s->nav.openWorkout(p,s)},{key->nav.navigate("practice/$key")}) { entry -> if(entry.discipline=="run") {nav.openTabRoot("training");nav.navigate("builder/run")}else nav.navigate("practice/entry-${entry.id}") } }
                composable("practice/{key}") { backStack -> PracticeBuilder(state,actions.planning,backStack.arguments?.getString("key") ?: "new",{nav.openTabRoot("library")},{p,s->nav.openWorkout(p,s)},{nav.openTabRoot("training")}) }
                composable("move/{planId}/{sessionId}") { back ->
                    val planId = back.arguments?.getString("planId") ?: ""
                    val sessionId = back.arguments?.getString("sessionId") ?: ""
                    val plan = state.plans.firstOrNull { it.id == planId }
                    val session = plan?.sessions?.firstOrNull { it.id == sessionId }
                    if (plan == null || session == null) {
                        Screen("Workout unavailable", "TRAINING", compactTitle = true) {
                            item { TextButton(onClick = { nav.popBackStack() }) { Text("Back") } }
                        }
                    } else {
                        TrainingScreen(
                            state, actions.planning,
                            onBuild = { nav.navigate("builder/all") },
                            onOpen = { id, sId -> nav.openWorkout(id, sId) },
                            onSignIn = { nav.navigate("auth") },
                            onManage = { id, date ->
                                nav.navigate("manage/${android.net.Uri.encode(id)}/$date")
                            }
                        )
                        TracksideParityMoveWorkoutSheet(
                            plan = plan, session = session,
                            actions = actions.planning,
                            onDismiss = { nav.popBackStack() },
                            onReviewDestination = { date ->
                                nav.navigate("review-move/${android.net.Uri.encode(planId)}/${android.net.Uri.encode(sessionId)}/$date")
                            }
                        )
                    }
                }
                composable("review-move/{planId}/{sessionId}/{date}") { back ->
                    val planId = back.arguments?.getString("planId") ?: ""
                    val sessionId = back.arguments?.getString("sessionId") ?: ""
                    val date = runCatching {
                        java.time.LocalDate.parse(back.arguments?.getString("date"))
                    }.getOrNull()
                    val plan = state.plans.firstOrNull { it.id == planId }
                    if (plan == null || date == null) {
                        Screen("Review unavailable", "TRAINING", compactTitle = true) {
                            item { TextButton(onClick = { nav.popBackStack() }) { Text("Back") } }
                        }
                    } else {
                        TracksideParityReviewChanges(
                            plan = plan,
                            proposal = TracksideChangeProposal(
                                TracksideChangeKind.MOVE,
                                sessionId = sessionId,
                                destination = date
                            ),
                            otherPlans = state.plans,
                            busy = state.busy,
                            onBack = { nav.popBackStack() },
                            onRegenerateDraft = { nav.navigate("builder/all") },
                            onApply = { checked ->
                                checked.destination?.let {
                                    actions.planning.move(plan.id, plan.revision, checked.sessionId, it)
                                    nav.openTabRoot("training")
                                }
                            }
                        )
                    }
                }
                composable("missed/{planId}/{sessionId}") { back ->
                    val planId = back.arguments?.getString("planId") ?: ""
                    val sessionId = back.arguments?.getString("sessionId") ?: ""
                    val plan = state.plans.firstOrNull { it.id == planId }
                    val session = plan?.sessions?.firstOrNull { it.id == sessionId }
                    TracksideParityMissedWorkoutFlow(
                        plan = plan,
                        session = session,
                        otherPlans = state.plans,
                        busy = state.busy,
                        actions = actions.planning,
                        onBack = { nav.popBackStack() },
                        onBuildDraft = { nav.navigate("builder/all") }
                    )
                }
                composable("workout/{planId}/{sessionId}") { workout ->
                    val planId = workout.arguments?.getString("planId") ?: ""
                    val sessionId = workout.arguments?.getString("sessionId") ?: ""
                    WorkoutScreen(
                        state, planId, sessionId, actions.planning,
                        onBack = { nav.popBackStack() },
                        onMissed = {
                            nav.navigate("missed/${android.net.Uri.encode(planId)}/${android.net.Uri.encode(sessionId)}")
                        },
                        onMove = {
                            nav.navigate("move/${android.net.Uri.encode(planId)}/${android.net.Uri.encode(sessionId)}")
                        }
                    )
                }
                composable("settings") {
                    val context = LocalContext.current
                    var confirmSignOut by remember { mutableStateOf(false) }
                    Screen("Settings", "ACCOUNT & DATA") {
                        item { SignalCard { Text("YOUR STRIDELAB", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary); Text(if(state.account == null) "Keep your training close." else "Connected to your history.", style = MaterialTheme.typography.titleLarge); Text("Private preview · ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                        item { SyncStatus(state) }
                        if(state.account != null) {
                            item { TracksideSectionHeader("Private account") }
                            item { SignalCard { Text("ACCOUNT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary); Text(state.account!!, style = MaterialTheme.typography.bodyMedium) } }
                            item { TracksideSectionHeader("Connections") }
                            item { GarminSettingsCard(state.account!!,actions.syncGarmin) }
                            item { SignalCard {
                                Text("ACTIVITY SOURCE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text("Intervals.icu",style = MaterialTheme.typography.titleLarge)
                                val connection = state.intervals
                                Text(when { connection == null -> "Connection not checked"; !connection.configured -> "Not connected"; connection.status == "error" -> "Connected source needs attention"; else -> "Saved connection · ${connection.status}" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                connection?.let {
                                    Text("Last checked ${timestamp(it.checkedAt)}",style = MaterialTheme.typography.bodySmall)
                                    it.lastProviderSync?.let { synced -> Text("Source last synchronized ${timestamp(synced.toEpochMilli())}",style = MaterialTheme.typography.bodySmall) }
                                }
                                OutlinedButton(onClick = actions.checkIntervals,enabled = !state.busy && !state.needsSignIn,modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Check connection") }
                                Button(onClick = actions.syncIntervals, enabled = !state.busy && !state.needsSignIn && state.intervals?.configured == true, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Synchronize Intervals.icu") }
                                Text("Uses your existing source connection. Manage credentials in your existing StrideLab integration settings, then check again here. Native setup and backfill are not yet available.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } }
                            item { OutlinedButton(onClick = { confirmSignOut = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Sign out on this device") } }
                        } else item { Button(onClick = { nav.navigate("auth") }, modifier = Modifier.fillMaxWidth()) { Text("Sign in") } }
                        item { TracksideSectionHeader("Device & data") }
                        item { SignalCard {
                            Text("LEGACY DATA & BACKUPS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text("Your earlier training is preserved", style = MaterialTheme.typography.titleLarge)
                            Text("Review your original records and use the existing backup and restore tools. Legacy records are not yet converted into native history.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(onClick = { context.startActivity(Intent(context, MainActivity::class.java)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Open preserved prototype") }
                        } }
                        item { Text("Private testing", style = MaterialTheme.typography.titleMedium); Text("Real-account and phone acceptance are in progress. Your saved data stays on this device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if(confirmSignOut) AlertDialog(onDismissRequest = { confirmSignOut = false }, title = { Text("Sign out?") }, text = { Text("Removes this device’s saved session. Cached records remain private in app storage and are hidden until that account signs in again.") }, confirmButton = { TextButton(onClick = { confirmSignOut = false; actions.signOut() }) { Text("Sign out") } }, dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } })
                }
                composable("auth") {
                    var email by rememberSaveable { mutableStateOf("") }
                    // Never persist passwords through saved state or process recreation.
                    var password by remember { mutableStateOf("") }
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if(event == Lifecycle.Event.ON_STOP) password = ""
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); password = "" }
                    }
                    var showPassword by remember { mutableStateOf(false) }
                    val initialGeneration = rememberSaveable { state.loginGeneration }
                    LaunchedEffect(state.loginGeneration, state.busy, state.needsSignIn) {
                        if(state.account != null && !state.busy && !state.needsSignIn && state.loginGeneration > initialGeneration) nav.popBackStack()
                    }
                    Screen("Welcome back", "SECURE SIGN-IN") {
                        item { SignalCard { Text("YOUR HISTORY. YOUR PACE.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary); Text("One place for the work you put in.", style = MaterialTheme.typography.headlineSmall); Text("Sign in with your existing StrideLab account. Saved activities stay on this device for offline viewing.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                        item { Column(verticalArrangement = Arrangement.spacedBy(18.dp)) { OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true, shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next), enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("email"))
                        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true, shape = RoundedCornerShape(16.dp), visualTransformation = if(showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = { TextButton(onClick = { showPassword = !showPassword }) { Text(if(showPassword) "Hide" else "Show") } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if(email.isNotBlank() && password.isNotEmpty() && !state.busy) { actions.signIn(email,password); password = "" } }), enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("password"))
                        Button(onClick = { actions.signIn(email, password); password = "" }, enabled = email.isNotBlank() && password.isNotEmpty() && !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("submit-signin")) { Text("Sign in securely") } } }
                        item { TextButton(onClick = { nav.popBackStack() }) { Text("Back") } }
                    }
                }
            }
        }
    }
}

}

internal val LocalProfileAction=staticCompositionLocalOf<()->Unit> { {} }

@Composable internal fun Screen(title: String, eyebrow: String, compactTitle: Boolean = false,
    dense: Boolean = false, edgeToEdge: Boolean = false, titleBadge: String? = null,
    bottomOverlayPadding: androidx.compose.ui.unit.Dp = 0.dp,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().padding(bottom = bottomOverlayPadding),
        contentAlignment = Alignment.TopCenter
    ) {
        val horizontal = if (edgeToEdge) 0.dp else if (dense) 18.dp else 22.dp
        val top = if (edgeToEdge) 0.dp else if (dense) 12.dp else 24.dp
        val gap = if (edgeToEdge) 0.dp else if (dense) 12.dp else 18.dp
        LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize().testTag("screen").semantics { paneTitle = title }, contentPadding = PaddingValues(start = horizontal, end = horizontal, top = top, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
            if (!edgeToEdge) item {
                TracksideScreenHeader(
                    title, eyebrow, compactTitle, LocalProfileAction.current,
                    dense = dense, titleBadge = titleBadge
                )
            }
            content()
        }
    }
}

/** Decorative brand strokes; never a route, chart or athlete measurement. */
@Composable internal fun BrandMark(modifier: Modifier = Modifier) {
    val signal = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawLine(signal, Offset(w*.12f,h*.72f), Offset(w*.48f,h*.18f), w*.14f, androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(signal, Offset(w*.38f,h*.78f), Offset(w*.86f,h*.25f), w*.14f, androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

@Composable internal fun SignalCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    TracksideGlassCard(modifier = modifier, content = content)
}
@Composable private fun EmptyState(title: String, description: String) {
    SignalCard {
        Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .10f), RoundedCornerShape(16.dp)).padding(12.dp)) { BrandMark(Modifier.fillMaxSize()) }
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable internal fun HistoryOverview(activities: List<ActivityRecord>) {
    SignalCard {
        Text("ON THIS DEVICE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(if(activities.size == 1) "1 saved activity" else "${activities.size} saved activities", style = MaterialTheme.typography.headlineMedium)
        Text("Activity summaries saved on this device. Open a record to inspect available source details.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
/** Only recorded elapsed durations. No target, trend, readiness or missing-value inference. */
@Composable internal fun RecordedDurationChart(activities: List<ActivityRecord>) {
    val records = remember(activities) { activities.filter { it.durationS != null }.take(5) }
    val maximum = records.maxOfOrNull { it.durationS!! } ?: 0.0
    SignalCard {
        Text("RECENT TRAINING TIME", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text("Elapsed time · recent saved activities", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        records.forEach { activity ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(activity.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("${activity.dateLabel} · ${activity.durationLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.fillMaxWidth().height(5.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
                    if(maximum > 0 && activity.durationS!! > 0) Box(Modifier.fillMaxWidth((activity.durationS / maximum).toFloat()).fillMaxHeight().background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(3.dp)))
                }
            }
        }
    }
}
@Composable internal fun ActivityRow(
    activity: ActivityRecord, sourceLabel:String=activity.sourceLabel, onClick: () -> Unit
) {
    val accent = when { activity.supportsPace -> TracksidePalette.running; activity.type.contains("strength", true) || activity.type == "training" -> TracksidePalette.strength; else -> TracksidePalette.calisthenics }
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("activity-${activity.id}"), shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))) {
        Column(Modifier.background(Brush.horizontalGradient(listOf(accent.copy(alpha = .07f), Color.Transparent))).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(8.dp).background(accent, RoundedCornerShape(4.dp)))
                Text(activity.type.uppercase(), style = MaterialTheme.typography.labelMedium, color = accent, modifier = Modifier.weight(1f))
                Text("↗", style = MaterialTheme.typography.titleLarge, color = accent)
            }
            Text(activity.title, style = MaterialTheme.typography.titleLarge)
            Text(activity.dateLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(if(activity.distanceM == null && !activity.supportsPace) activity.durationLabel else "${activity.distanceLabel} · ${activity.durationLabel}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(sourceLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable internal fun MetricPair(first: Pair<String,String>?, second: Pair<String,String>) {
    val enlarged = LocalDensity.current.fontScale > 1.15f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if(enlarged || maxWidth < 340.dp) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            first?.let { Metric(it.first,it.second, Modifier.fillMaxWidth()) }; Metric(second.first,second.second, Modifier.fillMaxWidth())
        } else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            first?.let { Metric(it.first,it.second,Modifier.weight(1f)) }
            Metric(second.first,second.second,Modifier.weight(1f))
        }
    }
}
@Composable internal fun Metric(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth()) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}
@Composable private fun SyncStatus(state: TrainingState) {
    val last = state.lastSync?.let(::timestamp)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(14.dp)) {
        Text(if(state.busy) state.task ?: "Working… Saved data remains on this device." else if(last != null) "Saved offline · refreshed $last" else "No completed history refresh recorded", Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
private fun timestamp(value: Long): String = DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value))



@Composable private fun LibraryGrid(modifier:Modifier) {
    val color=MaterialTheme.colorScheme.onSurface
    Canvas(modifier) { val gap=size.width*.16f;val cell=(size.width-gap)/2
        for(x in 0..1)for(y in 0..1) drawRoundRect(color,Offset(x*(cell+gap),y*(cell+gap)),androidx.compose.ui.geometry.Size(cell,cell),androidx.compose.ui.geometry.CornerRadius(size.width*.08f),style=androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
    }
}
