package io.stridelab.privatealpha

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.ui.layout.layout
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
        // Remove the platform navigation-bar tint beneath the floating dock.
        // The gesture handle remains system-controlled and accessible.
        // Android 14 and below still use this setter for the dock background;
        // Android 15+ manages edge-to-edge system bar surfaces instead.
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.BLACK
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent { StrideTheme { NativeStrideApp() } }
    }
}

@Composable internal fun StrideTheme(content: @Composable () -> Unit) {
    // Match the current StrideLab Android app. Use the system sans-serif (Roboto on
    // Android) until the final licensed type assets are bundled and checked.
    val displayFont = FontFamily.SansSerif
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = TracksidePalette.lime, onPrimary = Color.Black,
            background = TracksidePalette.backgroundBottom,
            surface = TracksidePalette.surface,
            surfaceVariant = TracksidePalette.surfaceVariant,
            onSurface = TracksidePalette.text,
            onBackground = TracksidePalette.text,
            onSurfaceVariant = TracksidePalette.textMuted,
            secondary = TracksidePalette.strength,
            secondaryContainer = Color(0xFF202324),
            onSecondaryContainer = TracksidePalette.text,
            tertiary = TracksidePalette.calisthenics,
            outline = TracksidePalette.outline,
            outlineVariant = Color(0xFF303438),
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
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(model, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) model.onAppResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val actions = remember(model) { NativeActions(model::refresh, {}, {}, model::signOut, model::signIn, model::loadDetail, model::refreshWellness, model.planActions(), syncGarmin=model::syncGarmin, loadGarminGps=model::loadGarminGps,
        backupGarminCloud=model::backupGarminCloud, restoreGarminCloud=model::restoreGarminCloud,
        signUp=model::signUp, requestPasswordReset=model::requestPasswordReset,
        loadCachedDetail=model::loadCachedDetail,
        renameActivity=model::renameActivity) }
    NativeStrideContent(state, online, actions)
}

/** Presentation boundary allows isolated screenshots without a real session or fake production data. */
// Two retired-source callback slots are inert, kept temporarily so older
// Android UI fixtures compile. No production provider or control uses them.
internal data class NativeActions(val refresh: () -> Unit, val checkIntervals: () -> Unit,
    val syncIntervals: () -> Unit, val signOut: () -> Unit, val signIn: (String, String) -> Unit, val loadDetail: (String) -> Unit = {}, val refreshWellness: () -> Unit = {}, val planning:PlanActions = PlanActions(),
    val syncGarmin: (suspend (GarminPersonalSource) -> GarminConnectionStatus)? = null,
    val loadGarminGps: ((String,GarminPersonalSource)->Unit)?=null,
    val signUp: (String, String, String) -> Unit = { _, _, _ -> },
    val requestPasswordReset: (String) -> Unit = {},
    val backupGarminCloud: (suspend () -> Int)? = null,
    val restoreGarminCloud: (suspend () -> Int)? = null,
    val loadCachedDetail: (suspend (String) -> Boolean)? = null,
    val renameActivity: (suspend (String, String) -> Boolean)? = null)

/** Primary destinations switch immediately; the legacy Activity history route remains available.
 * retain the existing Navigation Compose transitions.
 */
private val mainTabRoutes = setOf("home", "training", "activities", "settings")

// Temporarily set to true in Android Studio to isolate tab content from navigation.
private const val TAB_SWITCH_DIAGNOSTIC = false

/** Save each tab's root state, never its transient detail/auth destination. */
private fun NavHostController.openTabRoot(path: String) {
    // Do not dispatch another navigation transaction when the selected
    // tab is already visible. Repeated taps must not rebuild its destination
    // and interrupt the floating navigation pill's animation.
    if (currentDestination?.route == path) return
    while(currentDestination?.route in setOf("activity/{id}", "auth", "auth/signup", "auth/reset", "more/garmin", "more/data", "more/offline-maps", "more/preferences", "more/about", "builder/{discipline}", "workout/{planId}/{sessionId}", "practice/{key}", "manage/{planId}/{date}")) {
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
    // A detail belongs to the destination that opened it. Do not first
    // navigate through Activity: Back from Today's session sheet must return
    // to Today with its selected date and scroll state intact.
    navigate("activity/${android.net.Uri.encode(id)}") {
        launchSingleTop = true
    }
}

@Composable internal fun NativeStrideContent(state: TrainingState, online: Boolean, actions: NativeActions, diagnosticTabs: Boolean = TAB_SWITCH_DIAGNOSTIC) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    // Immediate visual acknowledgement of a bottom-tab press. NavController
    // still owns the back stack, restoration and child destination lifecycle;
    // the dock's animation never gates which retained root is laid out.
    var requestedTab by remember { mutableStateOf<String?>(null) }
    var garminSheetOpen by remember { mutableStateOf(false) }
    var garminBusy by remember { mutableStateOf(false) }
    LaunchedEffect(route, state.account) {
        if (requestedTab == route || route !in mainTabRoutes) requestedTab = null
    }
    val visibleRoot = if (route == null || route in mainTabRoutes) {
        requestedTab ?: route
    } else route
    val onDockTab: (String) -> Unit = { target ->
        if (nav.currentDestination?.route == target) {
            // Repeated taps do not start another destination transaction.
            requestedTab = null
        } else {
            requestedTab = target
            try {
                nav.openTabRoot(target)
            } catch (error: Exception) {
                requestedTab = null
                throw error
            }
        }
    }
    val groupedActivities = remember(state.activities) {
        ActivityDisplayConsolidation.groups(ActiveActivitySources.filter(state.activities))
    }
    val visibleActivities = remember(groupedActivities) { groupedActivities.map { it.primary } }
    // A genuinely floating dock must not participate in Scaffold's bottomBar:
    // the overlay is positioned above the system gesture area independently
    // of the scrolling screen. Detail/builder flows continue to hide it.
    val showFloatingDock = route != "missed/{planId}/{sessionId}" &&
        route != "review-move/{planId}/{sessionId}/{date}" &&
        route != "activity/{id}" && route != "builder/{discipline}" &&
        route?.startsWith("auth") != true && route?.startsWith("more/") != true
    CompositionLocalProvider(
        LocalProfileAction provides { nav.openTabRoot("settings") },
        LocalFloatingDockVisible provides showFloatingDock
    ) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { padding ->
        // Today and Training share a true AMOLED-black canvas, including
        // the space behind and below the floating navigation dock.
        val pageBackground = if (route == "home" || route == "training" || route == "settings" || route?.startsWith("more/") == true) {
            Brush.verticalGradient(listOf(Color.Black, Color.Black))
        } else {
            Brush.verticalGradient(
                listOf(TracksidePalette.backgroundTop, TracksidePalette.backgroundBottom)
            )
        }
        Box(Modifier.fillMaxSize().background(pageBackground).padding(padding).imePadding()) {
            Column(Modifier.fillMaxSize()) {
            // Keep the signed-out primary screens composed independently of
            // NavHost so their remembered state survives tab switches.
            Box(Modifier.weight(1f)) {
            NavHost(
                navController = nav,
                startDestination = "home",
                // Content is laid out behind the dock, not pushed upward into
                // a blank bottom stripe. Scrollable roots reserve their own
                // ending padding so their final actions remain reachable.
                modifier = Modifier.fillMaxSize(),
                // Navigation Compose's default 700 ms fade made the bottom tabs
                // feel like a flash. The lime dock pill is the only tab motion.
                enterTransition = {
                    if (targetState.destination.route in mainTabRoutes) EnterTransition.None
                    else fadeIn(animationSpec = tween(700))
                },
                exitTransition = {
                    if (targetState.destination.route in mainTabRoutes) ExitTransition.None
                    else fadeOut(animationSpec = tween(700))
                },
                popEnterTransition = {
                    if (targetState.destination.route in mainTabRoutes) EnterTransition.None
                    else fadeIn(animationSpec = tween(700))
                },
                popExitTransition = {
                    if (targetState.destination.route in mainTabRoutes) ExitTransition.None
                    else fadeOut(animationSpec = tween(700))
                }
            ) {
                composable("home") { /* Root content is retained below NavHost. */ }
                composable("activities") {
                    Screen("Activities", "YOUR HISTORY") {
                        item { SyncStatus(state) }
                        if(visibleActivities.isNotEmpty())
                            item { HistoryOverview(visibleActivities) }
                        run {
                            if(state.account != null)
                                item { OutlinedButton(onClick = actions.refresh, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Refresh cloud history") } }
                            if(visibleActivities.isEmpty()) item { EmptyState("No activities saved yet", "Connect Garmin in More to import activities without signing in.") }
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
                    val activity = state.activities.firstOrNull {
                        it.id == detail.arguments?.getString("id") &&
                            ActiveActivitySources.accepts(it.source)
                    }
                    // Only enabled sources can open an active detail route.
                    val matchedGroup = groupedActivities.firstOrNull { it.primary.id == activity?.id }
                    val detailSource = matchedGroup?.detailedSource ?: activity
                    val context=LocalContext.current
                    val garminOwner = if(activity?.id?.let { it in state.localGarminIds } == true)
                        state.localGarminOwner else state.account
                    val garminSource=remember(garminOwner,context) {
                        garminOwner?.let { owner ->
                            GarminPersonalSource(owner,GarminCredentialVault(context.applicationContext),
                                GarminPersonalClient())
                        }
                    }
                    val garminId=activity?.takeIf { it.source=="garmin" }?.id
                    val onLoadGarminGps:(()->Unit)?=actions.loadGarminGps?.let { load ->
                        if(garminId!=null && garminSource!=null) ({ load(garminId,garminSource) })
                        else null
                    }
                    var automaticDetailAttempted by rememberSaveable(detailSource?.id) { mutableStateOf(false) }
                    var localRecordingChecked by remember(detailSource?.id) { mutableStateOf(false) }
                    LaunchedEffect(detailSource?.id, state.account) {
                        if(detailSource != null &&
                            (state.account != null || detailSource.id in state.localGarminIds) &&
                            detailSource.id !in state.details) {
                            actions.loadCachedDetail?.invoke(detailSource.id)
                        }
                        localRecordingChecked=true
                    }
                    var activityTab by rememberSaveable(activity?.id) { mutableIntStateOf(0) }
                    var matteStyle by rememberSaveable(activity?.id) { mutableStateOf(false) }
                    LaunchedEffect(detailSource?.id, online, state.busy, state.needsSignIn, localRecordingChecked, state.details) {
                        // Load real supplementary Intervals.icu streams when
                        // Garmin is the canonical summary. Never use the Garmin
                        // local summary ID as a remote stream endpoint.
                        if (localRecordingChecked && !automaticDetailAttempted && detailSource != null &&
                            detailSource.supportsPace &&
                            online && !state.busy &&
                            !state.details.containsKey(detailSource.id)) {
                            if(detailSource.source!="garmin") {
                                if(state.account != null && !state.needsSignIn) {
                                    automaticDetailAttempted=true
                                    actions.loadDetail(detailSource.id)
                                }
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
                        TracksideActivityDetailSurface(
                            activity = activity,
                            recorded = detailSource?.let { state.details[it.id] },
                            detailSource = detailSource ?: activity,
                            matchedSourceIds = matchedGroup?.let { group ->
                                (listOf(group.primary) + group.additionalSources).map { it.id }.toSet()
                            } ?: setOf(activity.id),
                            history = visibleActivities,
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
                composable("training") { /* Retained tab surface. */ }
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
                composable("settings") { /* Retained tab surface. */ }
                // Old restored route: return to More rather than opening a page.
                composable("more/garmin") {
                    LaunchedEffect(Unit) { nav.openTabRoot("settings") }
                }
                // Older installations can still restore the retired route safely.
                composable("more/data") {
                    LaunchedEffect(Unit) { nav.openTabRoot("settings") }
                }
                composable("more/offline-maps") {
                    TracksideOfflineMapsScreen(onBack = { nav.popBackStack() })
                }
                composable("more/preferences") {
                    TracksideMoreDetailDestination(MorePage.Preferences, nav, state, actions)
                }
                composable("more/about") {
                    TracksideMoreDetailDestination(MorePage.About, nav, state, actions)
                }
                composable("auth") {
                    val initialGeneration = rememberSaveable { state.loginGeneration }
                    LaunchedEffect(state.loginGeneration, state.busy, state.needsSignIn) {
                        if (state.account != null && !state.busy &&
                            !state.needsSignIn && state.loginGeneration > initialGeneration
                        ) nav.popBackStack()
                    }
                    TracksideAuthScreen(
                        page = TracksideAuthPage.SignIn,
                        busy = state.busy,
                        feedback = state.message,
                        onBack = { nav.popBackStack() },
                        onSignIn = actions.signIn,
                        onSignUp = actions.signUp,
                        onReset = actions.requestPasswordReset,
                        onSignInPage = {},
                        onSignUpPage = { nav.navigate("auth/signup") },
                        onResetPage = { nav.navigate("auth/reset") }
                    )
                }
                composable("auth/signup") {
                    TracksideAuthScreen(
                        page = TracksideAuthPage.SignUp,
                        busy = state.busy,
                        feedback = state.message,
                        onBack = { nav.popBackStack() },
                        onSignIn = actions.signIn,
                        onSignUp = actions.signUp,
                        onReset = actions.requestPasswordReset,
                        onSignInPage = { nav.popBackStack() },
                        onSignUpPage = {},
                        onResetPage = { nav.navigate("auth/reset") }
                    )
                }
                composable("auth/reset") {
                    TracksideAuthScreen(
                        page = TracksideAuthPage.Reset,
                        busy = state.busy,
                        feedback = state.message,
                        onBack = { nav.popBackStack() },
                        onSignIn = actions.signIn,
                        onSignUp = actions.signUp,
                        onReset = actions.requestPasswordReset,
                        onSignInPage = { nav.popBackStack() },
                        onSignUpPage = { nav.navigate("auth/signup") },
                        onResetPage = {}
                    )
                }
            }
            if (state.account != null) {
                // Keep signed-in roots composed but measure/place only the
                // selected one. Switching tabs no longer reconstructs Today,
                // Training and More after every NavHost destination change.
                // The keyed group is disposed if the account changes.
                key(state.account) {
                    PersistentTabSurface(active = visibleRoot == null || visibleRoot == "home") {
                        if (diagnosticTabs) TabDiagnosticPlaceholder("Today") else StrideLabTodayScreen(
                            state = state,
                            visibleActivities = visibleActivities,
                            online = online,
                            onOpenTraining = { nav.openTabRoot("training") },
                            onOpenActivity = { nav.openTabRoot("training") },
                            onOpenWorkout = { planId, sessionId -> nav.openWorkout(planId, sessionId) },
                            onOpenActivityDetail = { id -> nav.openActivity(id) },
                            onOpenMore = { nav.openTabRoot("settings") },
                            onSignIn = { nav.navigate("auth") }
                        )
                    }
                    PersistentTabSurface(active = visibleRoot == "training") {
                        if (diagnosticTabs) TabDiagnosticPlaceholder("Training")
                        else StrideLabUnifiedTrainingTimeline(
                            state, groupedActivities, actions.planning,
                            { nav.navigate("builder/all") },
                            { p, session -> nav.openWorkout(p, session) },
                            { id -> nav.openActivity(id) },
                            { nav.navigate("auth") },
                            { p, d -> nav.navigate("manage/${android.net.Uri.encode(p)}/$d") },
                            onLoadCachedDetail = actions.loadCachedDetail
                        )
                    }
                    PersistentTabSurface(active = visibleRoot == "settings") {
                        if (diagnosticTabs) TabDiagnosticPlaceholder("More") else {
                            var confirmSignOut by remember { mutableStateOf(false) }
                            TracksideMoreScreen(
                                state = state,
                                onSignIn = { nav.navigate("auth") },
                                        onGarmin = { garminSheetOpen = true },
                                onOfflineMaps = { nav.navigate("more/offline-maps") },
                                onPreferences = { nav.navigate("more/preferences") },
                                onAbout = { nav.navigate("more/about") },
                                onSignOut = { confirmSignOut = true }
                            )
                            if (confirmSignOut) {
                                AlertDialog(
                                    onDismissRequest = { confirmSignOut = false },
                                    title = { Text("Sign out?") },
                                    text = { Text("Your saved offline records will remain on this device.") },
                                    confirmButton = {
                                        TextButton(
                                            onClick = { confirmSignOut = false; actions.signOut() },
                                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD8FB70))
                                        ) { Text("Sign out") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { confirmSignOut = false }) {
                                            Text("Cancel")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                // Guest roots stay retained and account content is removed.
                PersistentTabSurface(active = visibleRoot == null || visibleRoot == "home") {
                    if (diagnosticTabs) TabDiagnosticPlaceholder("Today") else StrideLabTodayScreen(
                        state = state, visibleActivities = visibleActivities,
                        online = online,
                        onOpenTraining = { nav.openTabRoot("training") },
                        onOpenActivity = { nav.openTabRoot("training") },
                        onOpenWorkout = { planId, sessionId -> nav.openWorkout(planId, sessionId) },
                        onOpenActivityDetail = { id -> nav.openActivity(id) },
                        onOpenMore = { nav.openTabRoot("settings") },
                        onSignIn = { nav.navigate("auth") }
                    )
                }
                PersistentTabSurface(active = visibleRoot == "training") {
                    if (diagnosticTabs) TabDiagnosticPlaceholder("Training") else StrideLabUnifiedTrainingTimeline(
                        state, groupedActivities, actions.planning,
                        { nav.navigate("builder/all") },
                        { p, session -> nav.openWorkout(p, session) },
                        { id -> nav.openActivity(id) },
                        { nav.navigate("auth") },
                        { p, d -> nav.navigate("manage/${android.net.Uri.encode(p)}/$d") },
                        onLoadCachedDetail = actions.loadCachedDetail
                    )
                }
                PersistentTabSurface(active = visibleRoot == "settings") {
                    if (diagnosticTabs) TabDiagnosticPlaceholder("More") else TracksideMoreScreen(
                        state = state,
                        onSignIn = { nav.navigate("auth") },
                        onGarmin = { garminSheetOpen = true },
                        onOfflineMaps = { nav.navigate("more/offline-maps") },
                        onPreferences = { nav.navigate("more/preferences") },
                        onAbout = { nav.navigate("more/about") },
                        onSignOut = {}
                    )
                }
            }
            } // Persistent tab container
            }
            // Keep network/refresh animation outside the navigation Column.
            // Showing or hiding progress must not remeasure the map or tabs.
            if (state.busy || state.initializing) {
                LinearProgressIndicator(
                    modifier = Modifier.align(Alignment.TopCenter)
                        .fillMaxWidth().height(3.dp).testTag("loading"),
                    color = Color(0xFFD8FB70),
                    trackColor = Color(0xFF1A2A20)
                )
            }
        }
    }
    if (showFloatingDock) {
        Box(
            modifier = Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 10.dp)
                .fillMaxWidth(.94f)
                .widthIn(max = 410.dp)
                .testTag("floating-navigation-overlay")
        ) {
            TracksideFloatingNavigation(visibleRoot, onDockTab)
        }
    }
    if (garminSheetOpen) {
        val localOwner = state.localGarminOwner ?: state.account
        if (localOwner != null) {
            TracksideGarminConnectionSheet(
                accountId = localOwner,
                syncGarmin = actions.syncGarmin,
                backupGarminCloud = actions.backupGarminCloud,
                restoreGarminCloud = actions.restoreGarminCloud,
                allowCloudBackup = state.account != null && !state.needsSignIn,
                cloudPreferenceAccount = state.account,
                busy = garminBusy,
                onBusyChange = { garminBusy = it },
                onDismiss = { if (!garminBusy) garminSheetOpen = false }
            )
        }
    }
    }
}

}

@Composable
private fun TabDiagnosticPlaceholder(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Tab diagnostic: $label",
            color = Color(0xFFE7ECED),
            modifier = Modifier.testTag("tab-diagnostic-$label")
        )
    }
}


/**
 * Keeps tab state alive while inactive content is not measured or placed.
 */
@Composable
private fun PersistentTabSurface(
    active: Boolean,
    content: @Composable () -> Unit
) {
    Box(
        Modifier.fillMaxSize()
            .layout { measurable, constraints ->
                if (active) {
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                } else {
                    layout(constraints.minWidth, constraints.minHeight) {}
                }
            }
            .then(if (active) Modifier else Modifier.clearAndSetSemantics {})
    ) {
        content()
    }
}

/** Single routing boundary for focused More subpages. */
@Composable
private fun TracksideMoreDetailDestination(
    page: MorePage,
    nav: NavHostController,
    state: TrainingState,
    actions: NativeActions
) {
    val context = LocalContext.current
    TracksideMoreDetailScreen(
        page = page,
        state = state,
        onBack = { nav.popBackStack() },
        onSignIn = { nav.navigate("auth") },
        onSystemAppSettings = {
            context.startActivity(
                Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + context.packageName)
                )
            )
        },
        syncGarmin = actions.syncGarmin
    )
}

internal val LocalProfileAction=staticCompositionLocalOf<()->Unit> { {} }
internal val LocalFloatingDockVisible=staticCompositionLocalOf { false }

@Composable internal fun Screen(title: String, eyebrow: String, compactTitle: Boolean = false,
    dense: Boolean = false, edgeToEdge: Boolean = false, titleBadge: String? = null,
    bottomOverlayPadding: androidx.compose.ui.unit.Dp = 0.dp,
    hideHeader: Boolean = false,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().padding(bottom = bottomOverlayPadding),
        contentAlignment = Alignment.TopCenter
    ) {
        val horizontal = if (edgeToEdge) 0.dp else if (dense) 18.dp else 22.dp
        val top = if (edgeToEdge) 0.dp else if (dense) 12.dp else 24.dp
        val gap = if (edgeToEdge) 0.dp else if (dense) 12.dp else 18.dp
        LazyColumn(
            Modifier.widthIn(max = 760.dp).fillMaxSize().testTag("screen")
                .semantics { paneTitle = title },
            contentPadding = PaddingValues(
                start = horizontal, end = horizontal, top = top,
                bottom = if (LocalFloatingDockVisible.current) 110.dp else 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            if (!edgeToEdge && !hideHeader) item {
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

