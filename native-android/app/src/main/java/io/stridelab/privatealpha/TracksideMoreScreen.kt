package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val moreBlack = Color(0xFF080B0A)
private val moreSurface = Color(0xFF151A18)
private val moreBorder = Color(0xFF364039)
private val moreDivider = Color(0xFF303833)
private val moreWhite = Color(0xFFF1F4F1)
private val moreMuted = Color(0xFFAAB4AD)
private val moreSecondary = Color(0xFF82918A)
private val moreLime = Color(0xFFD8FB70)

internal enum class MorePage { Garmin, Data, Preferences, About }

/** Compact Trackside control centre. No placeholder toggles or fabricated account data. */
@Composable
internal fun TracksideMoreScreen(
    state: TrainingState,
    onSignIn: () -> Unit,
    onLibrary: () -> Unit,
    onGarmin: () -> Unit,
    onData: () -> Unit,
    onPreferences: () -> Unit,
    onAbout: () -> Unit,
    onSignOut: () -> Unit
) {
    val signedIn = state.account != null
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(moreBlack)
            .testTag("screen").semantics { paneTitle = "More" },
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 116.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "more-header") {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "More", color = moreWhite, fontSize = 31.sp, lineHeight = 37.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("screen-title").semantics { heading() }
                )
                IconButton(
                    onClick = onPreferences,
                    modifier = Modifier.size(48.dp).testTag("more-settings-shortcut")
                ) {
                    MoreIcon(MoreSymbol.Sliders, color = moreMuted, modifier = Modifier.size(23.dp))
                }
            }
        }
        item(key = "more-account") {
            Surface(
                color = moreSurface, shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, moreBorder),
                modifier = Modifier.fillMaxWidth().testTag("more-profile-card")
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape, color = Color(0xFF303D2B),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            MoreIcon(MoreSymbol.Person, moreLime, Modifier.size(24.dp))
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Your StrideLab", color = moreWhite, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(
                            if (signedIn) "Signed in" else "Not signed in",
                            color = moreMuted, fontSize = 12.sp
                        )
                    }
                    if (!signedIn) {
                        Surface(
                            onClick = onSignIn, shape = RoundedCornerShape(24.dp),
                            color = moreLime, modifier = Modifier.testTag("more-sign-in")
                        ) {
                            Text(
                                "Sign in", color = moreBlack, fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
                            )
                        }
                    } else {
                        Text("Connected", color = moreLime, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        item(key = "more-training-title") { MoreSection("TRAINING") }
        item(key = "more-training") {
            MoreGroup {
                MoreActionRow("Workout library", MoreSymbol.Dumbbell, "library", onLibrary)
            }
        }
        item(key = "more-connection-title") { MoreSection("CONNECTIONS") }
        item(key = "more-connections") {
            MoreGroup {
                MoreActionRow(
                    "Garmin Connect", MoreSymbol.Watch, "garmin",
                    onClick = if (signedIn) onGarmin else onSignIn
                )
            }
        }
        item(key = "more-data-title") { MoreSection("PREFERENCES & DATA") }
        item(key = "more-preferences") {
            MoreGroup {
                MoreActionRow("Data & backups", MoreSymbol.Data, "data", onData)
                MoreRule()
                MoreActionRow("App preferences", MoreSymbol.Sliders, "preferences", onPreferences)
                MoreRule()
                MoreActionRow("About StrideLab", MoreSymbol.Info, "about", onAbout)
            }
        }
        if (signedIn) {
            item(key = "more-signout") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(
                        onClick = onSignOut, modifier = Modifier.testTag("more-sign-out"),
                        colors = ButtonDefaults.textButtonColors(contentColor = moreMuted)
                    ) { Text("Sign out on this device", fontSize = 13.sp) }
                }
            }
        }
        item(key = "more-footer") {
            Text(
                "StrideLab · Private Preview",
                color = moreSecondary, fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/** Detail pages lead only to implemented destinations and system settings. */
@Composable
internal fun TracksideMoreDetailScreen(
    page: MorePage,
    state: TrainingState,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onLegacyBackups: () -> Unit,
    onSystemAppSettings: () -> Unit,
    syncGarmin: (suspend (GarminPersonalSource) -> GarminConnectionStatus)?
) {
    val title = when (page) {
        MorePage.Garmin -> "Garmin Connect"
        MorePage.Data -> "Data & backups"
        MorePage.Preferences -> "App preferences"
        MorePage.About -> "About StrideLab"
    }
    LazyColumn(
        Modifier.fillMaxSize().background(moreBlack)
            .testTag("more-detail").semantics { paneTitle = title },
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "detail-heading") {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onBack, modifier = Modifier.testTag("more-back")) {
                    Text("←", fontSize = 25.sp, color = moreWhite)
                }
                Text(
                    title, color = moreWhite, fontSize = 27.sp, lineHeight = 33.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("more-detail-title").semantics { heading() }
                )
            }
        }
        when (page) {
            MorePage.Garmin -> item(key = "detail-garmin") {
                val account = state.account
                if (account == null) {
                    MoreGroup { MoreActionRow("Sign in to connect Garmin", MoreSymbol.Person, "connect-sign-in", onSignIn) }
                } else {
                    // Preserve existing secure credential, sync and disconnect behaviour.
                    GarminSettingsCard(account, syncGarmin)
                }
            }
            MorePage.Data -> {
                item(key = "detail-data-caption") {
                    MoreInfoCard(
                        "Saved data",
                        "Your existing offline records remain on this device."
                    )
                }
                item(key = "detail-backups") {
                    MoreGroup {
                        MoreActionRow(
                            "Legacy backup & restore", MoreSymbol.Data, "legacy-backups",
                            onLegacyBackups
                        )
                    }
                }
            }
            MorePage.Preferences -> {
                item(key = "detail-athlete-hr") {
                    AthleteHeartRatePreferenceCard(state.account)
                }
                item(key = "detail-preferences") {
                    MoreGroup {
                        MoreActionRow(
                            "Android app settings", MoreSymbol.Sliders,
                            "system-settings", onSystemAppSettings
                        )
                    }
                }
                item(key = "detail-pref-note") {
                    MoreInfoCard(
                        "Appearance",
                        "StrideLab currently uses its dark theme. System permissions and notifications are managed by Android."
                    )
                }
            }
            MorePage.About -> {
                item(key = "detail-about") {
                    MoreInfoCard("StrideLab", "Private Preview · " + BuildConfig.VERSION_NAME)
                }
            }
        }
    }
}

@Composable
private fun AthleteHeartRatePreferenceCard(account: String?) {
    val context = LocalContext.current
    val preferences = remember(context) { AthleteHeartRatePreferences(context) }
    var savedMaximum by remember(account) { mutableStateOf(preferences.maximumHr(account)) }
    var text by remember(account, savedMaximum) {
        mutableStateOf(savedMaximum?.toString().orEmpty())
    }
    val parsed = text.toIntOrNull()
    val valid = text.isBlank() || parsed in 100..240

    Surface(
        shape = RoundedCornerShape(21.dp), color = moreSurface,
        border = BorderStroke(1.dp, moreBorder),
        modifier = Modifier.fillMaxWidth().testTag("athlete-hr-preference")
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Heart rate zones", color = moreWhite, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Set your maximum heart rate once. Activity Detail uses it for " +
                    "five estimated %HRmax zones; this does not change Garmin zones or coaching.",
                color = moreMuted, fontSize = 12.sp, lineHeight = 18.sp
            )
            OutlinedTextField(
                value = text,
                onValueChange = { value ->
                    if (value.length <= 3 && value.all { it.isDigit() }) text = value
                },
                modifier = Modifier.fillMaxWidth().testTag("athlete-max-hr-input"),
                label = { Text("Maximum heart rate") },
                placeholder = { Text("100–240 bpm") },
                suffix = { Text("bpm") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = text.isNotBlank() && !valid,
                supportingText = {
                    Text(
                        when {
                            text.isNotBlank() && !valid -> "Enter a value from 100 to 240 bpm."
                            account == null -> "Saved locally on this device."
                            else -> "Saved for the signed-in athlete on this device."
                        }
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = moreLime,
                    focusedLabelColor = moreLime,
                    unfocusedBorderColor = moreBorder,
                    focusedTextColor = moreWhite,
                    unfocusedTextColor = moreWhite
                )
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (savedMaximum != null) {
                    TextButton(
                        onClick = {
                            preferences.setMaximumHr(account, null)
                            savedMaximum = null
                            text = ""
                        },
                        modifier = Modifier.testTag("athlete-max-hr-clear")
                    ) {
                        Text("Clear", color = moreMuted)
                    }
                }
                Button(
                    onClick = {
                        preferences.setMaximumHr(account, parsed)
                        savedMaximum = parsed
                    },
                    enabled = text.isNotBlank() && valid && parsed != savedMaximum,
                    modifier = Modifier.testTag("athlete-max-hr-save"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = moreLime,
                        contentColor = moreBlack
                    )
                ) {
                    Text("Save", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable private fun MoreInfoCard(title: String, description: String) {
    Surface(
        shape = RoundedCornerShape(21.dp), color = moreSurface,
        border = BorderStroke(1.dp, moreBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, color = moreWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = moreMuted, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable private fun MoreSection(title: String) {
    Text(
        title, color = moreSecondary, fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 2.dp, top = 6.dp)
    )
}

@Composable private fun MoreGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = moreSurface,
        border = BorderStroke(1.dp, moreBorder),
        shape = RoundedCornerShape(19.dp)
    ) {
        Column(content = content)
    }
}

@Composable private fun MoreRule() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 1.dp,
        color = moreDivider
    )
}

@Composable private fun MoreActionRow(
    title: String,
    symbol: MoreSymbol,
    tag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp)
            .testTag("more-row-" + tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        MoreIcon(symbol, moreLime)
        Text(
            title, color = moreWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        MoreIcon(MoreSymbol.Chevron, moreSecondary, Modifier.size(18.dp))
    }
}

private enum class MoreSymbol { Person, Dumbbell, Watch, Data, Sliders, Info, Chevron }

/** Simple static vector glyphs; no dynamic resources or animated draw operations. */
@Composable private fun MoreIcon(
    symbol: MoreSymbol,
    color: Color,
    modifier: Modifier = Modifier.size(21.dp)
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.7.dp.toPx()
        when (symbol) {
            MoreSymbol.Person -> {
                drawCircle(color, w * .16f, Offset(w * .50f, h * .29f), style = Stroke(stroke))
                drawArc(
                    color, 195f, 150f, false, Offset(w * .16f, h * .41f),
                    Size(w * .68f, h * .52f), style = Stroke(stroke)
                )
            }
            MoreSymbol.Dumbbell -> {
                drawLine(color, Offset(w * .14f, h * .5f), Offset(w * .86f, h * .5f), stroke)
                for (x in listOf(.22f, .32f, .68f, .78f)) {
                    drawLine(color, Offset(w * x, h * .25f), Offset(w * x, h * .75f), stroke * 1.8f)
                }
            }
            MoreSymbol.Watch -> {
                drawRoundRect(
                    color, Offset(w * .26f, h * .23f), Size(w * .48f, h * .54f),
                    CornerRadius(w * .15f), style = Stroke(stroke)
                )
                drawLine(color, Offset(w * .42f, 0f), Offset(w * .42f, h * .20f), stroke)
                drawLine(color, Offset(w * .58f, 0f), Offset(w * .58f, h * .20f), stroke)
                drawLine(color, Offset(w * .42f, h * .80f), Offset(w * .42f, h), stroke)
                drawLine(color, Offset(w * .58f, h * .80f), Offset(w * .58f, h), stroke)
            }
            MoreSymbol.Data -> {
                drawOval(color, Offset(w * .12f, h * .12f), Size(w * .76f, h * .28f), style = Stroke(stroke))
                drawArc(color, 0f, 180f, false, Offset(w * .12f, h * .39f),
                    Size(w * .76f, h * .29f), style = Stroke(stroke))
                drawArc(color, 0f, 180f, false, Offset(w * .12f, h * .60f),
                    Size(w * .76f, h * .29f), style = Stroke(stroke))
                drawLine(color, Offset(w * .12f, h * .26f), Offset(w * .12f, h * .75f), stroke)
                drawLine(color, Offset(w * .88f, h * .26f), Offset(w * .88f, h * .75f), stroke)
            }
            MoreSymbol.Sliders -> {
                for ((y, knob) in listOf(.25f to .32f, .50f to .67f, .75f to .42f)) {
                    drawLine(color, Offset(w * .12f, h * y), Offset(w * .88f, h * y), stroke)
                    drawCircle(moreSurface, w * .105f, Offset(w * knob, h * y))
                    drawCircle(color, w * .095f, Offset(w * knob, h * y), style = Stroke(stroke))
                }
            }
            MoreSymbol.Info -> {
                drawCircle(color, w * .40f, Offset(w * .5f, h * .5f), style = Stroke(stroke))
                drawCircle(color, w * .035f, Offset(w * .5f, h * .29f))
                drawLine(color, Offset(w * .5f, h * .46f), Offset(w * .5f, h * .71f), stroke)
            }
            MoreSymbol.Chevron -> {
                drawLine(color, Offset(w * .38f, h * .24f), Offset(w * .62f, h * .50f), stroke)
                drawLine(color, Offset(w * .62f, h * .50f), Offset(w * .38f, h * .76f), stroke)
            }
        }
    }
}
