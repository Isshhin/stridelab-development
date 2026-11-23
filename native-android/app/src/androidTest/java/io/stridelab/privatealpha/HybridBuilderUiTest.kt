package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HybridBuilderUiTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun allSixFocusCardsStaySelectedWithSeparateResistanceTracks() {
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    PlanBuilder(TrainingState(initializing = false, account = "hybrid-ui"),
                        PlanActions(), onDone = {}, onSignIn = {}, onPreview = { _, _ -> })
                }
            }
        }
        val tracks = listOf("run", "plyo", "iso", "str", "cal", "cond")
        for (track in tracks) {
            val focus = compose.onNodeWithTag("discipline-$track")
            focus.performScrollTo()
            if (!focus.fetchSemanticsNode().config[SemanticsProperties.Selected]) focus.performClick()
            focus.assertIsSelected()
        }
        tracks.forEach { compose.onNodeWithTag("discipline-$it").assertIsSelected() }
        compose.onNodeWithTag("discipline-plyo").performScrollTo().performClick()
        compose.onNodeWithTag("discipline-plyo").assertIsNotSelected()
        compose.onNodeWithTag("discipline-iso").assertIsSelected()
        compose.onNodeWithTag("discipline-str").assertIsSelected()
    }


    @Test fun trainYourWayShowsBaselineBeforeTargetsAndKeepsEdits() {
        showBuilder()
        next()
        compose.onNodeWithTag("builder-option-custom").performScrollTo().performClick()
        next()

        compose.onNodeWithText("2 / 6").assertExists()
        compose.onNodeWithText("BASELINE").assertExists()
        compose.onNodeWithTag("builder-advanced-mileage").assertDoesNotExist()
        compose.onNodeWithTag("builder-experience-2").performClick()
        setSlider("builder-ability-weekly-slider", 30f)
        setSlider("builder-ability-longest-slider", 12f)
        next()

        compose.onNodeWithText("3 / 6").assertExists()
        compose.onNodeWithText("Training targets").assertExists()
        compose.onNodeWithTag("builder-advanced-quality-count-2").assertIsNotEnabled()
        compose.onNodeWithTag("builder-quality-frequency-reason").performScrollTo().assertIsDisplayed()
        setSlider("builder-target-mileage-slider", 40f)

        back()
        assertSlider("builder-ability-weekly-slider", 30f)
        assertSlider("builder-ability-longest-slider", 12f)
        next()
        assertSlider("builder-target-mileage-slider", 40f)
        next()
        compose.onNodeWithTag("builder-run-plus").performScrollTo().performClick()
        back()

        compose.onNodeWithText("3 / 6").assertExists()
        compose.onNodeWithTag("builder-quality-frequency-reason").assertDoesNotExist()
        compose.onNodeWithTag("builder-advanced-quality-count-2").performScrollTo().assertIsEnabled().performClick()
        next()
        next()
        compose.onNodeWithText("5 / 6").assertExists()
    }

    @Test fun scheduleCountsAllSixTracksAndPassesAnIndependentDailyLimit() {
        var generated: List<HybridTrackRequest>? = null
        showBuilder(PlanActions(generateHybrid = { generated = it }))
        val tracks = setOf("run", "str", "plyo", "iso", "cal", "cond")
        for (track in tracks) {
            val choice = compose.onNodeWithTag("discipline-$track").performScrollTo()
            if (!choice.fetchSemanticsNode().config[SemanticsProperties.Selected]) choice.performClick()
        }
        next() // Running goal
        compose.onNodeWithTag("builder-option-custom").performScrollTo().performClick()
        next() // Baseline
        next() // Targets
        next() // Running week
        next() // Strength goal
        next() // Strength setup
        next() // Cross-training goals
        next() // Calisthenics setup
        next() // Schedule

        compose.onNodeWithText("5 / 6").assertExists()
        compose.onNodeWithTag("builder-training-tracks").assertExists()
        compose.onNodeWithText("6 training tracks").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("builder-hybrid-pairs").assertDoesNotExist()
        compose.onNodeWithText("Allow compatible same-day workouts").assertDoesNotExist()
        compose.onNodeWithTag("builder-hybrid-daily-limit").performScrollTo().performTextInput("95")
        next()
        compose.runOnIdle {
            val requests = checkNotNull(generated)
            assertEquals(tracks, requests.map { it.track }.toSet())
            assertTrue(requests.all { it.request.timeBudgetEnabled && it.request.dailyMinutes == 95 })
        }
    }

    private fun showBuilder(actions: PlanActions = PlanActions()) {
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    PlanBuilder(TrainingState(initializing = false, account = "hybrid-ui"),
                        actions, discipline = "run", onDone = {}, onSignIn = {}, onPreview = { _, _ -> })
                }
            }
        }
    }

    private fun next() = compose.onNodeWithTag("builder-figma-continue").performClick()
    private fun back() = compose.onNodeWithTag("builder-figma-back").performClick()

    private fun setSlider(tag: String, value: Float) {
        compose.onNodeWithTag(tag).performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
    }

    private fun assertSlider(tag: String, value: Float) {
        assertEquals(value, compose.onNodeWithTag(tag).fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current)
    }
}
