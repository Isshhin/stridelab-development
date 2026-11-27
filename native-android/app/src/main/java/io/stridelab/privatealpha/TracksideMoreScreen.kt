package io.stridelab.privatealpha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val moreBlack = Color(0xFF000000)
private val moreSurface = Color(0xFF121212)
private val moreBorder = Color(0xFF1F242B)
private val moreDivider = Color(0xFF1F242B)
private val moreWhite = Color(0xFFF5F7FA)
private val moreMuted = Color(0xFFA0A8B3)
private val moreSecondary = Color(0xFF6B737E)
private val moreLime = Color(0xFFD8FB70)

internal enum class MorePage { Preferences, About }

/** Compact Trackside control centre. No placeholder toggles or fabricated account data. */
@Composable
internal fun TracksideMoreScreen(
    state: TrainingState,
    onSignIn: () -> Unit,
    onGarmin: () -> Unit,
    onOfflineMaps: () -> Unit,
    onPreferences: () -> Unit,
    onAbout: () -> Unit,
    onSignOut: () -> Unit,
    garminConnected: Boolean = false,
    onActivityBackup: (() -> Unit)? = null
) {
    val signedIn = state.account != null && !state.needsSignIn
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
                    val (profileTitle, profileSubtitle) = if (signedIn) {
                        AccountProfilePresentation.resolve(
                            state.account,
                            state.accountDisplayName,
                            state.accountEmail
                        )
                    } else {
                        "Local Mode" to "Not signed in · Saved on this device"
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            profileTitle,
                            color = moreWhite,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            profileSubtitle,
                            color = moreMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!signedIn) {
                        TextButton(onClick = onSignIn, modifier = Modifier.testTag("more-sign-in")) {
                            Text("Sign in", color = moreMuted)
                            MoreIcon(MoreSymbol.Chevron, moreSecondary, Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        item(key = "more-connection-title") { MoreSection("CONNECTIONS") }
        item(key = "more-connections") {
            MoreGroup {
                MoreActionRow(
                    "Garmin Connect", MoreSymbol.Watch, "garmin",
                    onClick = onGarmin,
                    status = if (garminConnected) "Connected" else "Not connected",
                    statusColor = if (garminConnected) moreLime else moreMuted
                )
            }
        }
        item(key = "more-data-title") { MoreSection("DATA & MAPS") }
        item(key = "more-data") {
            MoreGroup {
                if (onActivityBackup != null) {
                    MoreActionRow("Activity backup and recovery", MoreSymbol.Info,
                        "activity-archive", onActivityBackup)
                    MoreRule()
                }
                MoreActionRow("Offline maps", MoreSymbol.Map, "offline-maps", onOfflineMaps)
            }
        }
        item(key = "more-app-title") { MoreSection("APP") }
        item(key = "more-app") {
            MoreGroup {
                MoreActionRow("App preferences", MoreSymbol.Sliders, "preferences", onPreferences)
                MoreRule()
                MoreActionRow("About StrideLab", MoreSymbol.Info, "about", onAbout)
            }
        }
        if (state.account != null) {
            item(key = "more-signout") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(
                        onClick = onSignOut, modifier = Modifier.testTag("more-sign-out"),
                        colors = ButtonDefaults.textButtonColors(contentColor = moreMuted)
                    ) { Text("Sign out on this device", fontSize = 13.sp) }
                }
            }
        }
    }
}

/** Detail pages lead only to implemented destinations and system settings. */
@Composable
internal fun TracksideMoreDetailScreen(
    page: MorePage,
    state: TrainingState,
    onBack: () -> Unit,
    onSystemAppSettings: () -> Unit
) {
    val title = when (page) {
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
            MorePage.Preferences -> {
                item(key = "detail-athlete-hr") {
                    AthleteHeartRatePreferenceCard(state.account, state.localGarminOwner ?: state.account)
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
                item(key = "detail-build-identification") {
                    MoreInfoCard(
                        "Installed build",
                        (if(BuildConfig.APPLICATION_ID.endsWith(".qa"))
                            "Isolated QA application. Storage is separate from normal StrideLab."
                        else "Standard StrideLab installation.") +
                            "\nPackage: " + BuildConfig.APPLICATION_ID +
                            "\nVersion code: " + BuildConfig.VERSION_CODE +
                            "\nBuild type: " + BuildConfig.BUILD_TYPE
                    )
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
    onClick: () -> Unit,
    status: String? = null,
    statusColor: Color = moreMuted
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
            modifier = Modifier.weight(1f)
        )
        if (status != null) Text(status, color = statusColor, fontSize = 12.sp,
            modifier = Modifier.testTag("more-garmin-status"))
        MoreIcon(MoreSymbol.Chevron, moreSecondary, Modifier.size(18.dp))
    }
}

private enum class MoreSymbol { Person, Watch, Map, Sliders, Info, Chevron }

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
            MoreSymbol.Map -> {
                val top = h * .20f
                val bottom = h * .80f
                val a = w * .12f
                val b = w * .40f
                val c = w * .68f
                val d = w * .88f
                drawLine(color, Offset(a, top), Offset(a, bottom), stroke)
                drawLine(color, Offset(b, h * .12f), Offset(b, h * .70f), stroke)
                drawLine(color, Offset(c, h * .30f), Offset(c, h * .88f), stroke)
                drawLine(color, Offset(d, top), Offset(d, bottom), stroke)
                drawLine(color, Offset(a, top), Offset(b, h * .12f), stroke)
                drawLine(color, Offset(b, h * .12f), Offset(c, h * .30f), stroke)
                drawLine(color, Offset(c, h * .30f), Offset(d, top), stroke)
                drawLine(color, Offset(a, bottom), Offset(b, h * .70f), stroke)
                drawLine(color, Offset(b, h * .70f), Offset(c, h * .88f), stroke)
                drawLine(color, Offset(c, h * .88f), Offset(d, bottom), stroke)
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
