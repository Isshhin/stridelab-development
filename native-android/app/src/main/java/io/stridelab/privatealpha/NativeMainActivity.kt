package io.stridelab.privatealpha

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
@Composable internal fun StrideTheme(content: @Composable () -> Unit) {
    val displayFont=remember { FontFamily(Font(R.font.bricolage_variable,weight=FontWeight.Bold,variationSettings=FontVariation.Settings(FontVariation.weight(800)))) }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFFD6FF4F), onPrimary = Color(0xFF0A0D02),
        background = Color(0xFF050506), surface = Color(0xFF101013),
        surfaceVariant = Color(0xFF25252B), onSurface = Color(0xFFF4F1EA),
        onBackground = Color(0xFFF4F1EA), onSurfaceVariant = Color(0xFFB0B0BA),
        secondary = Color(0xFF6FB4FF), secondaryContainer = Color(0xFF232F3D), onSecondaryContainer = Color(0xFFCCE4FF),
        outline = Color(0xFF777780), outlineVariant = Color(0xFF303038),
        surfaceContainer = Color(0xFF18181C), surfaceContainerHigh = Color(0xFF25252B)
    ), typography = Typography(
        headlineLarge = TextStyle(fontFamily=displayFont,fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        headlineMedium = TextStyle(fontFamily=displayFont,fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp),
        headlineSmall = TextStyle(fontFamily=displayFont,fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
        titleLarge = TextStyle(fontFamily=displayFont,fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    ), shapes = Shapes(medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)), content = content)
}

@Composable internal fun NativeStrideApp(model: TrainingViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle(context=kotlinx.coroutines.Dispatchers.Main.immediate)
    val online by model.online.collectAsStateWithLifecycle(context=kotlinx.coroutines.Dispatchers.Main.immediate)
    val actions = remember(model) { NativeActions(model::refresh, model::checkIntervals, model::syncIntervals, model::signOut, model::signIn, model::loadDetail, model::refreshWellness, model.planActions()) }
    NativeStrideContent(state, online, actions)
}

/** Presentation boundary allows isolated screenshots without a real session or fake production data. */
internal data class NativeActions(val refresh: () -> Unit, val checkIntervals: () -> Unit,
    val syncIntervals: () -> Unit, val signOut: () -> Unit, val signIn: (String, String) -> Unit, val loadDetail: (String) -> Unit = {}, val refreshWellness: () -> Unit = {}, val planning:PlanActions = PlanActions())

/** Save each tab's root state, never its transient detail/auth destination. */
private fun NavHostController.openTabRoot(path: String) {
    while(currentDestination?.route in setOf("activity/{id}", "auth", "builder/{discipline}", "workout/{planId}/{sessionId}", "practice/{key}")) {
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
    CompositionLocalProvider(LocalProfileAction provides { nav.openTabRoot("settings") }) {
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp,
                modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min=80.dp).clip(RoundedCornerShape(28.dp)),
                windowInsets = WindowInsets(0, 0, 0, 0)) {
                listOf("home" to "Overview", "training" to "Training", "activities" to "Activities", "library" to "Library").forEach { (path, label) ->
                    NavigationBarItem(selected = route == path || (path == "activities" && route == "activity/{id}") || (path=="training" && route in setOf("builder/{discipline}","workout/{planId}/{sessionId}")) || (path=="library" && route=="practice/{key}"), onClick = {
                        nav.openTabRoot(path)
                    }, icon = { if(path=="library") LibraryGrid(Modifier.size(24.dp)) else Icon(when(path) { "home" -> Icons.Default.Home; "training" -> Icons.Default.DateRange; "activities" -> Icons.AutoMirrored.Filled.List; else -> Icons.Default.Settings }, contentDescription = null) },
                        label = if(androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f) null else ({ Text(label, fontWeight = FontWeight.SemiBold) }),
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface, indicatorColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.testTag("nav-$path").semantics { contentDescription=label })
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(padding).imePadding()) {
            if(state.busy || state.initializing) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("loading"))
            if(!online) Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Text("Offline · Saved activities remain available", Modifier.fillMaxWidth().padding(horizontal = 20.dp,vertical = 10.dp).testTag("offline-status"),style = MaterialTheme.typography.bodyMedium)
            }
            state.message?.let { Surface(color = MaterialTheme.colorScheme.surfaceVariant) { Text(it, Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium) } }
            NavHost(navController = nav, startDestination = "home", modifier = Modifier.weight(1f)) {
                composable("home") {
                    Screen("Your training", "STRIDELAB / TRAINING JOURNAL") {
                        if(state.initializing) item { Text("Opening your saved training…") }
                        else if(state.account == null) item {
                            EmptyState("Start with your history", "Sign in to synchronize your activities. Saved history is available offline.")
                            Button(onClick = { nav.navigate("auth") }, modifier = Modifier.fillMaxWidth()) { Text("Sign in") }
                        } else {
                            item { SyncStatus(state) }
                            state.plans.firstOrNull { it.status=="approved" }?.let { plan -> item { HomePlanHero(plan) { sid -> nav.openWorkout(plan.id,sid) } } }
                            if(state.plans.none { it.status=="approved" }) item { OutlinedButton(onClick={nav.openTabRoot("training")},modifier=Modifier.fillMaxWidth()) { Text("Create your training plan") } }
                            state.activities.firstOrNull()?.let { activity ->
                                item { Text("LATEST ACTIVITY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                                item { ActivityRow(activity) { nav.openActivity(activity.id) } }
                            }
                            item { WeeklyDashboard(state.activities,plans=state.plans) }
                            item { WellnessOverview(state.wellness, actions.refreshWellness, online && !state.busy && !state.needsSignIn) }
                            item { HistoryOverview(state.activities) }
                            if(state.activities.count { it.durationS != null } >= 2) item { RecordedDurationChart(state.activities) }
                            item { FilledTonalButton(onClick = actions.refresh, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Refresh history") } }
                            if(state.needsSignIn) item { Button(onClick = { nav.navigate("auth") }) { Text("Sign in again") } }
                            if(state.activities.isEmpty()) item { EmptyState("No saved activities yet", "Refresh your history or synchronize your connected Intervals.icu source in Settings. Existing local prototype data remains in the legacy app.") }
                        }
                    }
                }
                composable("activities") {
                    Screen("Activities", "YOUR HISTORY") {
                        item { SyncStatus(state) }
                        if(state.account == null) item { EmptyState("Sign in to view your history", "Only records belonging to your signed-in account are displayed."); Button(onClick = { nav.navigate("auth") }) { Text("Sign in") } }
                        else {
                            item { OutlinedButton(onClick = actions.refresh, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Refresh history") } }
                            if(state.activities.isEmpty()) item { EmptyState("No activities cached", "Your saved history will appear here after a successful refresh.") }
                            val logged=state.plans.flatMap { p -> p.sessions.filter { it.status=="completed" && (it.linkedActivityId==null || state.activities.none { a->a.id==it.linkedActivityId }) }.map { p to it } }.sortedByDescending { it.second.log?.at }
                            if(logged.isNotEmpty()) item { Text("WORKOUT LOGS · ON THIS DEVICE",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary) }
                            logged.forEach { (p,s) -> item(key="logged-${s.id}") { PlannedWorkoutCard(s) { nav.openWorkout(p.id,s.id) } } }
                            items(state.activities, key = { it.id }) { activity -> ActivityRow(activity) { nav.openActivity(activity.id) } }
                        }
                    }
                }
                composable("activity/{id}") { detail ->
                    val activity = state.activities.firstOrNull { it.id == detail.arguments?.getString("id") }
                    Screen(activity?.title ?: "Activity unavailable", "ACTIVITY DETAIL", compactTitle = true) {
                        item { TextButton(onClick = { nav.popBackStack() }) { Text("Back to history") } }
                        if(activity == null) item { EmptyState("No activity available", "This activity is not in the current account’s local cache.") }
                        else {
                            item { Text(activity.dateLabel, style = MaterialTheme.typography.bodyLarge); Text("${activity.type} · ${activity.sourceLabel}", color = MaterialTheme.colorScheme.secondary) }
                            item { MetricPair(
                                if(activity.distanceM != null || activity.supportsPace) "Distance" to activity.distanceLabel else null,
                                "Elapsed time" to activity.durationLabel) }
                            if(activity.supportsPace) item { MetricPair(
                                "Moving time" to (activity.movingTimeS?.let { ActivityRecord.duration(it) } ?: "Not recorded"),
                                "Average pace" to activity.paceLabel) }
                            item { MetricPair(
                                "Average heart rate" to (activity.averageHr?.let { "${it.roundToInt()} bpm" } ?: "Not recorded"),
                                "Maximum heart rate" to (activity.maximumHr?.let { "${it.roundToInt()} bpm" } ?: "Not recorded")) }
                            if(activity.elevationM != null || activity.supportsPace) item { Metric("Elevation gain",activity.elevationM?.let { "${it.roundToInt()} m" } ?: "Not recorded") }
                            if(DashboardSummary.isRun(activity)) item { ActivityPlanLink(state,activity,actions.planning) }
                            item { OutlinedButton(onClick = { actions.loadDetail(activity.id) }, enabled = online && !state.busy && !state.needsSignIn, modifier = Modifier.fillMaxWidth()) { Text(if(state.details.containsKey(activity.id)) "Refresh recorded streams" else "Load recorded streams & laps") } }
                            state.details[activity.id]?.let { recorded -> item { RecordedActivityDetail(recorded,activity.supportsPace) } }
                            item { SignalCard {
                                Text(if(activity.supportsPace) "RUNNING INTELLIGENCE" else "SESSION CONTEXT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(ActivityIntelligence.narrative(activity, state.activities), style = MaterialTheme.typography.bodyLarge)
                                val comparison = ActivityIntelligence.comparison(activity, state.activities)
                                if(comparison != null) Text("Based on cached source summaries · ${comparison.count} earlier runs · preceding 90 days · distance ±10%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } }
                            item { Text("Values come from the activity source and use metric units. Missing metrics are shown as not recorded. Load recorded streams and laps when online. Availability depends on your source; summary metrics cannot reconstruct missing routes or charts.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
                composable("training") { TrainingScreen(state,actions.planning,{nav.navigate("builder/all")},{p,s->nav.openWorkout(p,s)},{nav.navigate("auth")}) }
                composable("builder/{discipline}") { builder -> PlanBuilder(state,actions.planning,builder.arguments?.getString("discipline") ?: "all",{nav.openTabRoot("training")},{nav.navigate("auth")},{p,s->nav.navigate("workout/${android.net.Uri.encode(p)}/${android.net.Uri.encode(s)}")}) }
                composable("library") { LibraryScreen(state,actions.planning,{p,s->nav.openWorkout(p,s)},{key->nav.navigate("practice/$key")}) { entry -> if(entry.discipline=="run") {nav.openTabRoot("training");nav.navigate("builder/run")}else nav.navigate("practice/entry-${entry.id}") } }
                composable("practice/{key}") { backStack -> PracticeBuilder(state,actions.planning,backStack.arguments?.getString("key") ?: "new",{nav.openTabRoot("library")},{p,s->nav.openWorkout(p,s)},{nav.openTabRoot("training")}) }
                composable("workout/{planId}/{sessionId}") { workout -> WorkoutScreen(state,workout.arguments?.getString("planId") ?: "",workout.arguments?.getString("sessionId") ?: "",actions.planning,{nav.popBackStack()}) }
                composable("settings") {
                    val context = LocalContext.current
                    var confirmSignOut by remember { mutableStateOf(false) }
                    Screen("Settings", "ACCOUNT & DATA") {
                        item { SignalCard { Text("YOUR STRIDELAB", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary); Text(if(state.account == null) "Keep your training close." else "Connected to your history.", style = MaterialTheme.typography.titleLarge); Text("Private preview · ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                        item { SyncStatus(state) }
                        if(state.account != null) {
                            item { SignalCard { Text("ACCOUNT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary); Text(state.account!!, style = MaterialTheme.typography.bodyMedium) } }
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

@Composable internal fun Screen(title: String, eyebrow: String, compactTitle: Boolean = false, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 760.dp).fillMaxSize().testTag("screen").semantics { paneTitle = title }, contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BrandMark(Modifier.size(26.dp))
                    Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,modifier=Modifier.weight(1f))
                    val profile=LocalProfileAction.current
                    FilledTonalIconButton(onClick=profile,modifier=Modifier.testTag("nav-settings")) { Icon(Icons.Default.Settings,contentDescription="Settings and profile") }
                }
                Spacer(Modifier.height(16.dp))
                Text(title, style = if(compactTitle) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.testTag("screen-title").semantics { heading() })
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
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
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
        Text("Your synchronized training journal. Open an activity to explore its recorded details.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
@Composable internal fun ActivityRow(activity: ActivityRecord, onClick: () -> Unit) {
    val accent = when { activity.supportsPace -> Color(0xFF6FB4FF); activity.type.contains("strength", true) || activity.type == "training" -> Color(0xFFFF9A62); else -> Color(0xFFC79BFF) }
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
            Text(activity.sourceLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
