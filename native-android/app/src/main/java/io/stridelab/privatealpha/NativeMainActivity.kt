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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
        secondary = Color(0xFFAECDB8)
    ), content = content)
}

@Composable internal fun NativeStrideApp(model: TrainingViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("home" to "Home", "activities" to "Activities", "settings" to "Settings").forEach { (path, label) ->
                    NavigationBarItem(selected = route == path, onClick = {
                        nav.navigate(path) { popUpTo(nav.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true }
                    }, icon = { Text(when(path) { "home" -> "⌂"; "activities" -> "≋"; else -> "⚙" }, style = MaterialTheme.typography.titleLarge) },
                        label = { Text(label) }, modifier = Modifier.testTag("nav-$path"))
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if(state.busy || state.initializing) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("loading"))
            state.message?.let { Surface(color = MaterialTheme.colorScheme.surfaceVariant) { Text(it, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium) } }
            NavHost(navController = nav, startDestination = "home", modifier = Modifier.weight(1f)) {
                composable("home") {
                    Screen("Your training", "STRIDELAB") {
                        if(state.initializing) item { Text("Opening your saved training…") }
                        else if(state.account == null) item {
                            EmptyState("Your training, on your device", "Sign in to load your genuine workout history. No sample activities are shown.")
                            Button(onClick = { nav.navigate("auth") }, modifier = Modifier.fillMaxWidth()) { Text("Sign in") }
                        } else {
                            item { SyncStatus(state) }
                            item { Text("${state.activities.size} saved activities", style = MaterialTheme.typography.headlineMedium) }
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
                        item { SyncStatus(state) }
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
                    Screen(activity?.title ?: "Activity unavailable", "ACTIVITY DETAIL") {
                        item { TextButton(onClick = { nav.popBackStack() }) { Text("Back to history") } }
                        if(activity == null) item { EmptyState("No activity available", "This activity is not in the current account’s local cache.") }
                        else {
                            item { Text(activity.dateLabel, style = MaterialTheme.typography.bodyLarge); Text("${activity.type} · ${activity.source}", color = MaterialTheme.colorScheme.secondary) }
                            item { Metric("Distance", activity.distanceLabel) }
                            item { Metric("Elapsed duration", activity.durationLabel) }
                            item { Metric("Moving duration", activity.movingTimeS?.let { ActivityRecord.duration(it) } ?: "Not recorded") }
                            item { Metric("Recorded average pace", activity.paceLabel) }
                            item { Metric("Average heart rate", activity.averageHr?.let { "${it.toInt()} bpm" } ?: "Not recorded") }
                            item { Metric("Elevation gain", activity.elevationM?.let { "${it.toInt()} m" } ?: "Not recorded") }
                            item { Text("Values retain the source’s units and provenance. This native slice uses metric display. GPS samples, charts and contextual analysis have not been connected yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
                composable("settings") {
                    val context = LocalContext.current
                    var confirmSignOut by remember { mutableStateOf(false) }
                    Screen("Settings", "ACCOUNT & DATA") {
                        item { Text("Native migration · 0.3.0", style = MaterialTheme.typography.titleMedium) }
                        item { SyncStatus(state) }
                        if(state.account != null) {
                            item { Text("Supabase account", style = MaterialTheme.typography.labelLarge); Text(state.account!!, style = MaterialTheme.typography.bodySmall) }
                            item { Button(onClick = model::syncIntervals, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Sync connected Intervals.icu") } }
                            item { Text("Uses your existing backend connection. Credentials are not shared with the legacy WebView. Source setup and historical provider backfill are not yet available in this native screen.", style = MaterialTheme.typography.bodyMedium) }
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
                    val initialAccount = remember { state.account }
                    LaunchedEffect(state.account, state.busy, state.needsSignIn) {
                        if(state.account != null && !state.busy && !state.needsSignIn && (initialAccount == null || state.message?.startsWith("History refreshed") == true)) nav.popBackStack()
                    }
                    Screen("Welcome back", "SECURE SIGN-IN") {
                        item { Text("Use your existing StrideLab email and password. Your session is encrypted with Android Keystore.") }
                        item { OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("email")) }
                        item { OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("password")) }
                        item { Button(onClick = { model.signIn(email, password); password = "" }, enabled = email.isNotBlank() && password.isNotEmpty() && !state.busy, modifier = Modifier.fillMaxWidth().testTag("submit-signin")) { Text("Sign in securely") } }
                        item { TextButton(onClick = { nav.popBackStack() }) { Text("Back") } }
                    }
                }
            }
        }
    }
}

@Composable private fun Screen(title: String, eyebrow: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("screen"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(8.dp)); Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("screen-title")) }
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
        Text("${activity.distanceLabel} · ${activity.durationLabel}", style = MaterialTheme.typography.bodyLarge)
        Text("${activity.type} · ${activity.source}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun Metric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary); Text(value, style = MaterialTheme.typography.headlineMedium); HorizontalDivider(Modifier.padding(top = 12.dp)) }
}
@Composable private fun SyncStatus(state: TrainingState) {
    val last = state.lastSync?.let { DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it)) }
    Text(if(state.busy) "Synchronizing… Saved data remains on this device." else if(last != null) "Cached on this device · refreshed $last" else "No completed history refresh recorded", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
