package io.stridelab.privatealpha

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate

/**
 * Batch B device checks. Test data is synthetic, never a user's account.
 * The workflow runs this suite at 360x800dp/1.3x and 412x950dp/1.5x.
 */
class TracksideResponsiveQaTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    private val start = LocalDate.now().plusDays(1)

    private fun snapshot(): PlanSnapshot = PlanSnapshot(
        id = "responsive-plan", owner = "synthetic-athlete",
        engineVersion = ENGINE1_VERSION, policyId = ENGINE1_POLICY,
        createdAt = Instant.EPOCH, request = PlanRequest(start = start, weeks = 2),
        sessions = listOf(
            PlannedSession("responsive-run", start, "run", "Long easy aerobic run", 70, 10.0, "easy"),
            PlannedSession("responsive-strength", start, "str", "Upper-body strength", 40, null, "strength"),
            PlannedSession("responsive-next", start.plusDays(2), "cal", "Push/pull", 35, null, "calisthenics")
        ),
        explanations = emptyList(), status = "approved"
    )

    private fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    Box(Modifier.fillMaxSize().padding(12.dp)) {
                        content()
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val config = compose.activity.resources.configuration
        val scale = (config.fontScale * 100f).toInt()
        val file = File(
            File(compose.activity.getExternalFilesDir(null), "trackside-qa").apply { mkdirs() },
            "${name}-${config.screenWidthDp}dp-font${scale}.png"
        )
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(file).use { out ->
            check(image.compress(Bitmap.CompressFormat.PNG, 100, out)) { "Could not save $file" }
        }
        assertTrue("QA screenshot missing: $file", file.length() > 0)
    }

    @Test fun todayHeroAtLargeFontPreservesTargetsAndVisibleAction() {
        val run = snapshot().sessions.first()
        var opened = false
        show {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                TracksideTodayHero(run) { opened = true }
            }
        }
        compose.onNodeWithTag("today-hero").assertExists()
        compose.onNodeWithText("Route not recorded", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("View workout details  ›", useUnmergedTree = true).assertExists()
        screenshot("today")
        compose.onNodeWithTag("today-hero").performClick()
        compose.runOnIdle { assertTrue("Hero action not usable", opened) }
    }

    @Test fun trainingWeekRetainsReadableTouchTargetsAtScaledFont() {
        val plan = snapshot()
        val selected = mutableStateOf(start)
        show {
            TracksideTrainingWeekChart(plan, selected.value) { selected.value = it }
        }
        compose.onNodeWithTag("training-week-chart").assertExists()
        val day = compose.onNodeWithTag("calendar-day-$start")
        val bounds = day.getUnclippedBoundsInRoot()
        assertTrue("Calendar touch width ${bounds.width} smaller than 48dp", bounds.width.value >= 48f)
        assertTrue("Calendar touch height ${bounds.height} smaller than 48dp", bounds.height.value >= 48f)
        screenshot("training")
        day.performClick()
        compose.runOnIdle { assertTrue("Selected day changed", selected.value == start) }
    }

    @Test fun manageActionsCanBeReachedWithLargeText() {
        val plan = snapshot()
        show {
            TracksideManagePlanScreen(plan, start, false, PlanActions(),
                onBack = {}, onBuildDraft = {}, onWorkout = {})
        }
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("manage-adjust-weeks"))
        val action = compose.onNodeWithTag("manage-adjust-weeks")
        action.assertIsDisplayed()
        val bounds = action.getUnclippedBoundsInRoot()
        assertTrue("Manage action smaller than 48dp", bounds.height.value >= 48f)
        screenshot("manage-actions")
        action.performClick()
        compose.onNodeWithText("Create a reviewed draft?").assertExists()
    }
}
