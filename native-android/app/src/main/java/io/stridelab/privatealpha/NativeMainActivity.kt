package io.stridelab.privatealpha

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
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
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFFC3E889), onPrimary = Color(0xFF20310D),
        background = Color(0xFF101411), surface = Color(0xFF101411),
        surfaceVariant = Color(0xFF273027), onSurface = Color(0xFFE3E9DF),
        secondary = Color(0xFFAECDB8), secondaryContainer = Color(0xFF2D3A26), onSecondaryContainer = Color(0xFFC3E889),
        surfaceContainer = Color(0xFF171D18), surfaceContainerHigh = Color(0xFF253026)
    ), content = content)
}

@Composable internal fun NativeStrideApp(model: TrainingViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val online by model.online.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("home" to "Home", "activities" to "Activities", "settings" to "Settings").forEach { (path, label) ->
                    NavigationBarItem(selected = route == path || (path == "activities" && route == "activity/{id}"), onClick = {
                        nav.navigate(path) { popUpTo(nav.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true }
                    }, icon = { Icon(when(path) { "home" -> Icons.Default.Home; "activities" -> Icons.Default.DateRange; else -> Icons.Default.Settings }, contentDescription = null) },
                        label = { Text(label) }, modifier = Modifier.testTag("nav-$path"))
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if(state.busy || state.initializing) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("loading"))
            if(!online) Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Text("Offline · Saved activities remain available", Modifier.fillMaxWidth().padding(horizontal = 20.dp,vertical = 10.dp).testTag("offline-status"),style = MaterialTheme.typography.bodyMedium)
            }
            state.message?.let { Surface(color = MaterialTheme.colorScheme.surfaceVariant) { Text(it, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium) } }
            NavHost(navController = nav, startDestination = "home", modifier = Modifier.weight(1f)) {
                composable("home") {
                    Screen("Your training", "STRIDELAB") {
                        if(state.initializing) item { Text("Opening your saved training…") }
                        else if(state.account == null) item {
                            EmptyState("Start with your history", "Sign in to synchronize your activities. Saved history is available offline.")
                            Button(onClick = { nav.navigate("auth") }, modifier = Modifier.fillMaxWidth()) { Text("Sign in") }
                        } else {
                            if(state.account != null) item { SyncStatus(state) }
                            item { Text(if(state.activities.size == 1) "1 saved activity" else "${state.activities.size} saved activities", style = MaterialTheme.typography.headlineMedium) }
                            item { FilledTonalButton(onClick = model::refresh, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Refresh history") } }
                            if(state.needsSignIn) item { Button(onClick = { nav.navigate("auth") }) { Text("Sign in again") } }
                            state.activities.firstOrNull()?.let { activity ->
                                item { Text("LATEST ACTIVITY", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                                item { ActivityRow(activity) { nav.navigate("activity/${activity.id}") } }
                            }
                            if(state.activities.isEmpty()) item { EmptyState("No saved activities yet", "Refresh your history or synchronize your connected Intervals.icu source in Settings. Existing local prototype data remains in the legacy app.") }
                        }
                    }
                }
                composable("activities") {
                    Screen("Activities", "YOUR HISTORY") {
                        if(state.account != null) item { SyncStatus(state) }
                        if(state.account == null) item { EmptyState("Sign in to view your history", "Only records belonging to your signed-in account are displayed."); Button(onClick = { nav.navigate("auth") }) { Text("Sign in") } }
                        else {
                            item { OutlinedButton(onClick = model::refresh, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Refresh history") } }
                            if(state.activities.isEmpty()) item { EmptyState("No activities cached", "Your saved history will appear here after a successful refresh.") }
                            items(state.activities, key = { it.id }) { activity -> ActivityRow(activity) { nav.navigate("activity/${activity.id}") }; HorizontalDivider() }
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
                            item { Text("Values come from the activity source and use metric units. Missing metrics are shown as not recorded. Route and charts are not available in this preview.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
                composable("settings") {
                    val context = LocalContext.current
                    var confirmSignOut by remember { mutableStateOf(false) }
                    Screen("Settings", "ACCOUNT & DATA") {
                        item { Text("Native preview · ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium) }
                        if(state.account != null) item { SyncStatus(state) }
                        if(state.account != null) {
                            item { Text("Account identifier", style = MaterialTheme.typography.labelLarge); Text(state.account!!, style = MaterialTheme.typography.bodySmall) }
                            item {
                                Text("Intervals.icu",style = MaterialTheme.typography.titleLarge)
                                val connection = state.intervals
                                Text(when { connection == null -> "Connection not checked"; !connection.configured -> "Not connected"; connection.status == "error" -> "Connected source needs attention"; else -> "Saved connection · ${connection.status}" })
                                connection?.let {
                                    Text("Last checked ${timestamp(it.checkedAt)}",style = MaterialTheme.typography.bodySmall)
                                    it.lastProviderSync?.let { synced -> Text("Source last synchronized ${timestamp(synced.toEpochMilli())}",style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                            item { OutlinedButton(onClick = model::checkIntervals,enabled = !state.busy && !state.needsSignIn,modifier = Modifier.fillMaxWidth()) { Text("Check connection") } }
                            item { Button(onClick = model::syncIntervals, enabled = !state.busy && !state.needsSignIn && state.intervals?.configured == true, modifier = Modifier.fillMaxWidth()) { Text("Synchronize Intervals.icu") } }
                            item { Text("Uses your existing source connection. If disconnected, connect Intervals.icu in your existing StrideLab integration settings, then check again here. Source credential setup and provider backfill are not yet available in this native screen.", style = MaterialTheme.typography.bodyMedium) }
                            item { OutlinedButton(onClick = { confirmSignOut = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Sign out on this device") } }
                        } else item { Button(onClick = { nav.navigate("auth") }, modifier = Modifier.fillMaxWidth()) { Text("Sign in") } }
                        item { HorizontalDivider() }
                        item { Text("Legacy data & backups", style = MaterialTheme.typography.titleLarge) }
                        item { Text("Your existing prototype storage is preserved. Open its original interface to review records or use its backup and restore tools. Automatic conversion into native training records is not yet implemented.") }
                        item { OutlinedButton(onClick = { context.startActivity(Intent(context, MainActivity::class.java)) }, modifier = Modifier.fillMaxWidth()) { Text("Open preserved prototype") } }
                        item { Text("Validation in progress", style = MaterialTheme.typography.titleMedium); Text("This migration build is not yet validated for daily use. Installation updates require the original signing certificate.", style = MaterialTheme.typography.bodyMedium) }
                    }
                    if(confirmSignOut) AlertDialog(onDismissRequest = { confirmSignOut = false }, title = { Text("Sign out?") }, text = { Text("Removes this device’s saved session. Cached records remain private in app storage and are hidden until that account signs in again.") }, confirmButton = { TextButton(onClick = { confirmSignOut = false; model.signOut() }) { Text("Sign out") } }, dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } })
                }
                composable("auth") {
                    var email by rememberSaveable { mutableStateOf("") }
                    // Never persist passwords through saved state or process recreation.
                    var password by remember { mutableStateOf("") }
                    var showPassword by remember { mutableStateOf(false) }
                    val initialGeneration = rememberSaveable { state.loginGeneration }
                    LaunchedEffect(state.loginGeneration, state.busy, state.needsSignIn) {
                        if(state.account != null && !state.busy && !state.needsSignIn && state.loginGeneration > initialGeneration) nav.popBackStack()
                    }
                    Screen("Welcome back", "SECURE SIGN-IN") {
                        item { Text("Sign in with your existing StrideLab account. Saved activities stay on this device for offline viewing.") }
                        item { OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next), enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("email")) }
                        item { OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = if(showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = { TextButton(onClick = { showPassword = !showPassword }) { Text(if(showPassword) "Hide" else "Show") } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if(email.isNotBlank() && password.isNotEmpty() && !state.busy) { model.signIn(email,password); password = "" } }), enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("password")) }
                        item { Button(onClick = { model.signIn(email, password); password = "" }, enabled = email.isNotBlank() && password.isNotEmpty() && !state.busy, modifier = Modifier.fillMaxWidth().testTag("submit-signin")) { Text("Sign in securely") } }
                        item { TextButton(onClick = { nav.popBackStack() }) { Text("Back") } }
                    }
                }
            }
        }
    }
}

@Composable private fun Screen(title: String, eyebrow: String, compactTitle: Boolean = false, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("screen"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(8.dp)); Text(title, style = if(compactTitle) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("screen-title")) }
        content()
    }
}
@Composable private fun EmptyState(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 12.dp)) { Text(title, style = MaterialTheme.typography.titleLarge); Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
@Composable private fun ActivityRow(activity: ActivityRecord, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp).heightIn(min = 48.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(activity.dateLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        Text(activity.title, style = MaterialTheme.typography.titleLarge)
        Text(if(activity.distanceM == null && !activity.supportsPace) activity.durationLabel else "${activity.distanceLabel} · ${activity.durationLabel}", style = MaterialTheme.typography.bodyLarge)
        Text("${activity.type} · ${activity.sourceLabel}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun MetricPair(first: Pair<String,String>?, second: Pair<String,String>) {
    val enlarged = LocalDensity.current.fontScale > 1.15f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if(enlarged || maxWidth < 300.dp) Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            first?.let { Metric(it.first,it.second) }; Metric(second.first,second.second)
        } else Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            first?.let { Metric(it.first,it.second,Modifier.weight(1f)) }
            Metric(second.first,second.second,Modifier.weight(1f))
        }
    }
}
@Composable private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier,verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary); Text(value, style = MaterialTheme.typography.titleLarge); HorizontalDivider(Modifier.padding(top = 12.dp)) }
}
@Composable private fun SyncStatus(state: TrainingState) {
    val last = state.lastSync?.let(::timestamp)
    Text(if(state.busy) state.task ?: "Working… Saved data remains on this device." else if(last != null) "Cached on this device · refreshed $last" else "No completed history refresh recorded", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun timestamp(value: Long): String = DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value))
