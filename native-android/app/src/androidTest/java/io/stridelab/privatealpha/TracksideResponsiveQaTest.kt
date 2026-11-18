package io.stridelab.privatealpha

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
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

    private fun matchedNineSessionDraft(id: String, engine15: Boolean = false): PlanSnapshot {
        val request = PlanRequest(
            start = LocalDate.of(2026, 11, 9), weeks = 1,
            goal = "Aerobic foundation",
            days = setOf(1, 2, 3, 4, 5, 7), longDay = 7,
            runDays = 5, strengthDays = 0, calDays = 4, conditioningDays = 0,
            qualityCount = 1, qualityDays = setOf(2), experience = 1,
            weeklyKm = 35.0, longestKm = 12.0, minutes = 60,
            dailyMinutes = 120, policyChoice = "multi-session"
        )
        val selected = TracksideCoaching.request(request, engine15Active = engine15)
        val plan = Engine1Engines.resolve(selected).generate(
            selected, "synthetic-responsive", id, Instant.EPOCH
        )
        assertTrue("Matched draft must stay a draft before explicit approval",
            plan.status == "draft")
        assertTrue("Matched draft must contain nine real prescriptions",
            plan.sessions.size == 9)
        assertTrue("Matched draft must occupy six active days",
            plan.sessions.map { it.date }.distinct().size == 6)
        assertTrue("Matched draft must contain three legal double days",
            plan.sessions.groupBy { it.date }.values.count { it.size == 2 } == 3)
        return plan
    }

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
        val context = compose.activity
        val config = context.resources.configuration
        val scale = (config.fontScale * 100f).toInt()
        val fileName = "${name}-${config.screenWidthDp}dp-font${scale}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            // MediaStore survives target-app data resets between instrumentation tests.
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/StrideLabBatchB")
        }
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val encoded = ByteArrayOutputStream().use { buffer ->
            check(image.compress(Bitmap.CompressFormat.PNG, 100, buffer)) {
                "Unable to encode screenshot: $fileName"
            }
            buffer.toByteArray()
        }
        assertTrue("QA screenshot encode empty: $fileName", encoded.isNotEmpty())
        // MediaStore.SIZE can remain zero until the media provider scans the new file.
        // Verify the encoded bytes and output stream directly; CI subsequently verifies
        // all five persisted PNG files are present and nonempty on the device.
        values.put(MediaStore.Images.Media.IS_PENDING, 1)
        val uri = checkNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)) {
            "Unable to create MediaStore screenshot: $fileName"
        }
        val resolver = context.contentResolver
        try {
            checkNotNull(resolver.openOutputStream(uri)).use { out ->
                out.write(encoded)
                out.flush()
            }
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }, null, null)
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    @Test fun disciplineSelectionUsesResponsiveCurrentLayout() {
        // Use the modern Android screen as the layout authority; archived Figma
        // coordinates do not define this screen's current vertical spacing.
        val request=PlanRequest(
            runDays=3,strengthDays=2,calDays=0,conditioningDays=0,
            days=setOf(1,2,3,4,5,7)
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideDisciplineScreen(
                        request=request,onToggle={},onBack={},onContinue={}
                    )
                }
            }
        }
        listOf("run","str","cal","cond").forEach { discipline ->
            val card=compose.onNodeWithTag("discipline-"+discipline)
            card.assertExists()
            val icon=compose.onNodeWithTag("discipline-icon-"+discipline,
                useUnmergedTree=true)
            icon.assertExists()
            val bounds=card.getUnclippedBoundsInRoot()
            // Cards remain large enough to tap and may grow with accessibility text.
            val scale = compose.activity.resources.configuration.fontScale
            val maxHeight = if (scale <= 1.01f) 90f else 90f * scale
            assertTrue("Discipline card too tall: "+discipline,
                (bounds.bottom-bounds.top).value <= maxHeight)
            assertTrue("Discipline card is below the minimum touch size: "+discipline,
                (bounds.bottom-bounds.top).value >= 48f)
        }
        compose.onNodeWithText("2 selected").assertExists()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
        screenshot("builder-disciplines-figma-reference")
        // At 360dp and enlarged fonts the fourth card is initially below the
        // fold. Verify that users can scroll it fully into the body viewport.
        compose.onNodeWithTag("discipline-cond").performScrollTo()
        compose.onNodeWithTag("discipline-cond").assertIsDisplayed()
        screenshot("builder-disciplines-last-card-reachable")
        listOf("run","str","cal","cond").forEach { discipline ->
            compose.onNodeWithTag("discipline-"+discipline).performScrollTo()
                .assertIsDisplayed()
        }
        compose.onNodeWithTag("builder-figma-continue").assertIsDisplayed()
    }

    @Test fun runningGoalSelectionUsesResponsiveLayoutAndRemainsInteractive() {
        val selected=mutableStateOf("race")
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideRunningGoalScreen(
                        selected=selected.value,
                        onSelect={ selected.value=it },onBack={},onContinue={}
                    )
                }
            }
        }
        listOf("race","faster","endurance","fitness","custom").forEach { goal ->
            compose.onNodeWithTag("builder-option-"+goal).assertExists()
            compose.onNodeWithTag("builder-goal-icon-"+goal,
                useUnmergedTree=true).assertExists()
        }
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
        screenshot("builder-running-goal-figma-reference")
        val orderedGoals = listOf("race","faster","endurance","fitness","custom")
        val goalBounds = orderedGoals.map { goal ->
            val node=compose.onNodeWithTag("builder-option-"+goal)
            node.getUnclippedBoundsInRoot()
        }
        assertTrue("Goal choices must retain a usable touch area",
            goalBounds.all { (it.bottom-it.top).value >= 48f })
        assertTrue("Goal choices must remain in their intended order",
            goalBounds.zipWithNext().all { (above,below) -> above.top < below.top })
        orderedGoals.forEach { goal ->
            compose.onNodeWithTag("builder-option-"+goal).performScrollTo()
                .assertIsDisplayed()
        }
        compose.onNodeWithTag("builder-figma-continue").assertIsDisplayed()
        screenshot("builder-running-goal-last-card-reachable")
        compose.onNodeWithTag("builder-option-faster").performScrollTo()
        compose.onNodeWithTag("builder-option-faster").performClick()
        compose.runOnIdle {
            assertTrue("Running goal selection did not update",selected.value=="faster")
        }
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
    }

    @Test fun raceGoalSelectionFigmaReferenceAndUnsupportedInputsRemainSafe() {
        // Figma 551:796: Beat PR selected, 10K, optional current PR and target day.
        val intent=mutableStateOf("pr")
        val distance=mutableStateOf("10K")
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideRaceGoalScreen(
                        intent=intent.value,distance=distance.value,
                        onIntent={ intent.value=it },onDistance={ distance.value=it },
                        onBack={},onContinue={}
                    )
                }
            }
        }
        listOf("pr","time","finish","benchmark").forEach { goal ->
            compose.onNodeWithTag("builder-option-"+goal).assertExists()
            compose.onNodeWithTag("builder-race-icon-"+goal,
                useUnmergedTree=true).assertExists()
        }
        compose.onNodeWithTag("builder-race-selected-check",
            useUnmergedTree=true).assertExists()
        compose.onNodeWithText("Your current 10K PR").assertExists()
        compose.onNodeWithTag("builder-race-pr-panel").assertExists()
        compose.onNodeWithTag("builder-distance-10K").assertExists()
        compose.onNodeWithTag("builder-race-target-day").assertExists()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
        screenshot("builder-race-goal-figma-reference")

        val config=compose.activity.resources.configuration
        if(config.screenWidthDp>=400 && config.fontScale<=1.01f) {
            val card=compose.onNodeWithTag("builder-option-pr")
                .getUnclippedBoundsInRoot()
            assertTrue("Race Goal card taller than Figma baseline",
                (card.bottom-card.top).value <= 72f)
            val panel=compose.onNodeWithTag("builder-race-pr-panel")
                .getUnclippedBoundsInRoot()
            assertTrue("Race PR panel taller than Figma reference tolerance",
                (panel.bottom-panel.top).value <= 124f)
            val day=compose.onNodeWithTag("builder-race-target-day")
                .getUnclippedBoundsInRoot()
            val cta=compose.onNodeWithTag("builder-figma-continue")
                .getUnclippedBoundsInRoot()
            assertTrue("Race target day obscured by fixed Continue",
                day.bottom <= cta.top - 2.dp)
        }
        // Small screens require a scrollable body; fixed Continue never
        // disables the lower date field or blocks its touch surface.
        compose.onNodeWithTag("builder-race-target-day").performScrollTo()
        compose.onNodeWithTag("builder-race-target-day").assertIsDisplayed()
        screenshot("builder-race-goal-target-day-reachable")
        compose.onNodeWithTag("builder-distance-21K").performScrollTo()
        compose.onNodeWithTag("builder-distance-21K").performClick()
        compose.onNodeWithText("Your current 21K PR").assertExists()
        compose.onNodeWithTag("builder-option-time").performScrollTo()
        compose.onNodeWithTag("builder-option-time").performClick()
        compose.onNodeWithTag("builder-target-time-unavailable").assertExists()
        compose.onNodeWithTag("builder-figma-continue").assertIsNotEnabled()
        // Reset to a currently supported intent. No unsupported target time
        // may enter a saved PlanRequest under the current coaching engine.
        compose.onNodeWithTag("builder-option-pr").performScrollTo()
        compose.onNodeWithTag("builder-option-pr").performClick()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
    }

    @Test fun runningAbilityLayoutFitsCurrentViewportAndRemainsInteractive() {
        // Figma 551:865: Intermediate, 30 km/week, longest recent run 14 km.
        val experience=mutableStateOf(1)
        val weekly=mutableStateOf(30.0)
        val longest=mutableStateOf(14.0)
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideRunningAbilityScreen(
                        experience=experience.value,
                        weeklyKm=weekly.value,longestKm=longest.value,
                        onExperience={ experience.value=it },
                        onWeeklyKm={ weekly.value=it },
                        onLongestKm={ longest.value=it },
                        onBack={},onContinue={},onAdvanced={}
                    )
                }
            }
        }
        compose.onNodeWithTag("builder-experience-1").assertExists()
        compose.onNodeWithTag("builder-ability-weekly-card").assertExists()
        compose.onNodeWithTag("builder-ability-longest-card").assertExists()
        compose.onNodeWithTag("builder-ability-optional-card").assertExists()
        compose.onNodeWithTag("builder-ability-weekly-slider").assertExists()
        compose.onNodeWithTag("builder-ability-longest-slider").assertExists()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
        screenshot("builder-running-ability-figma-reference")

        val config=compose.activity.resources.configuration
        if(config.screenWidthDp>=400 && config.fontScale<=1.01f) {
            val optional=compose.onNodeWithTag("builder-ability-optional-card")
                .getUnclippedBoundsInRoot()
            val cta=compose.onNodeWithTag("builder-figma-continue")
                .getUnclippedBoundsInRoot()
            assertTrue("Optional performance obscured by fixed Continue",
                optional.bottom <= cta.top - 2.dp)
            val weeklyCard=compose.onNodeWithTag("builder-ability-weekly-card")
                .getUnclippedBoundsInRoot()
            assertTrue("Weekly ability card exceeds Figma tolerance",
                (weeklyCard.bottom-weeklyCard.top).value<=137f)
            val longestCard=compose.onNodeWithTag("builder-ability-longest-card")
                .getUnclippedBoundsInRoot()
            assertTrue("Longest-run card exceeds Figma tolerance",
                (longestCard.bottom-longestCard.top).value<=137f)
            assertTrue("Optional result panel exceeds Figma tolerance",
                (optional.bottom-optional.top).value<=85f)
            assertTrue("Ability cards must remain in weekly/longest/optional order",
                weeklyCard.bottom < longestCard.top && longestCard.bottom < optional.top)
            assertTrue("Optional result card must not overlap the fixed action",
                optional.bottom <= cta.top - 2.dp)
        }
        compose.onNodeWithTag("builder-ability-optional-card").performScrollTo()
        compose.onNodeWithTag("builder-ability-optional-card").assertIsDisplayed()
        screenshot("builder-running-ability-optional-reachable")

        compose.onNodeWithTag("builder-experience-2").performScrollTo()
        compose.onNodeWithTag("builder-experience-2").performClick()
        compose.runOnIdle { assertTrue("Experience selection not updated",
            experience.value==2) }
        compose.onNodeWithTag("builder-ability-weekly-slider").performScrollTo()
        compose.onNodeWithTag("builder-ability-weekly-slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(40f) }
        compose.runOnIdle { assertTrue("Weekly mileage slider ignored input",
            weekly.value in 39.0..41.0) }
        compose.onNodeWithTag("builder-ability-longest-slider").performScrollTo()
        compose.onNodeWithTag("builder-ability-longest-slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(16f) }
        compose.runOnIdle { assertTrue("Longest run slider ignored input",
            longest.value in 15.0..17.0) }
        compose.onNodeWithTag("builder-ability-add-result").performScrollTo()
        compose.onNodeWithTag("builder-ability-add-result").performClick()
        compose.onNodeWithText("This coaching engine does not yet store",
            substring=true).assertExists()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
    }

    @Test fun runningWeekLayoutKeepsWeekdayEditingUsable() {
        // Five sessions Tue–Fri plus Sunday long; Saturday closed.
        val saved=mutableStateOf(PlanRequest(
            runDays=5, experience=1, weeklyKm=30.0,
            days=setOf(2,3,4,5,7), longDay=7,
            qualityCount=1, qualityDays=setOf(2)
        ))
        val custom=mutableStateOf(false)
        val selected=mutableStateOf(0)
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideRunningSetup(
                        request=saved.value, selectedSlot=selected.value,
                        onSelectSlot={selected.value=it},
                        custom=custom.value,
                        onCustom={ enabled ->
                            custom.value=enabled
                            saved.value=saved.value.copy(
                                weeklySlots=if(enabled)
                                    saved.value.weeklySlots +
                                        ("run" to WeeklyPlacement.days(saved.value,"run"))
                                else saved.value.weeklySlots-"run")
                        },
                        qualityPreference="Threshold",
                        onQualityPreference={},
                        onQualityCount={ n ->
                            saved.value=saved.value.copy(qualityCount=n)
                        },
                        onRunningCount={ n ->
                            saved.value=WeeklyPlacement.resize(saved.value,"run",n)
                        },
                        onAssignDay={ day ->
                            saved.value=WeeklyPlacement.put(saved.value,"run",
                                selected.value,day)
                        },
                        onAssignSlot={ index,day ->
                            custom.value=true
                            saved.value=WeeklyPlacement.put(saved.value,"run",index,day)
                            selected.value=index
                        },
                        onClearDay={ index ->
                            custom.value=true
                            saved.value=WeeklyPlacement.clear(saved.value,"run",index)
                            selected.value=index
                        },
                        onClearSlot={
                            saved.value=WeeklyPlacement.clear(saved.value,"run",
                                selected.value)
                        },
                        onBack={},onContinue={}
                    )
                }
            }
        }
        compose.onNodeWithTag("builder-running-week-card").assertExists()
        compose.onNodeWithTag("builder-running-legend").assertExists()
        compose.onNodeWithTag("builder-running-pinned-note").assertExists()
        compose.onNodeWithTag("builder-run-plus").assertExists()
        compose.onNodeWithTag("builder-run-minus").assertExists()
        compose.onNodeWithText("5 of 5 placed",substring=true).assertExists()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
        listOf(1,2,3,4,5,6,7).forEach { day ->
            compose.onNodeWithTag("builder-running-day-"+day).assertExists()
        }
        screenshot("builder-running-week-figma-reference")

        val cfg=compose.activity.resources.configuration
        if(cfg.screenWidthDp>=400 && cfg.fontScale<=1.01f) {
            val week=compose.onNodeWithTag("builder-running-week-card")
                .getUnclippedBoundsInRoot()
            val legend=compose.onNodeWithTag("builder-running-legend")
                .getUnclippedBoundsInRoot()
            assertTrue("Running weekly card exceeds a usable compact layout",
                (week.bottom-week.top).value <= 241f)
            assertTrue("Weekly summary and legend must not overlap",
                week.bottom < legend.top)
            val lastDescription=compose.onNodeWithText(
                "Endurance session on one preferred day"
            ).getUnclippedBoundsInRoot()
            assertTrue("Running legend last description too close to bottom stroke",
                legend.bottom - lastDescription.bottom >= 14.dp)
        }
        // The final weekday must be reachable even on 360dp screens without
        // shrinking below the existing 46dp day-cell hit area.
        compose.onNodeWithTag("builder-running-day-row")
            .performTouchInput { swipeLeft() }
        compose.onNodeWithTag("builder-running-day-7").assertIsDisplayed()
        screenshot("builder-running-week-last-day-reachable")

        // The primary flow opens a picker immediately without searching for
        // the old below-the-fold editor. A type selection assigns that day.
        compose.onNodeWithTag("builder-running-day-6").assertIsDisplayed()
        compose.onNodeWithTag("builder-running-day-6").performClick()
        compose.onNodeWithTag("builder-run-day-sheet").assertExists()
        compose.onNodeWithTag("builder-run-sheet-option-easy").assertIsEnabled()
        compose.onNodeWithTag("builder-run-sheet-option-easy").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("Day-first assignment must activate manual placement",custom.value)
            assertTrue("Easy session was not assigned to Saturday",
                saved.value.weeklySlots["run"]?.get(2)==6)
            assertTrue("Saturday was not made available",6 in saved.value.days)
            assertTrue("Session count changed unexpectedly",saved.value.runDays==5)
        }
        // The same day can be reassigned to quality by swapping the occupied
        // weekday, rather than silently dropping an existing run.
        compose.onNodeWithTag("builder-running-day-6").performClick()
        compose.onNodeWithTag("builder-run-sheet-option-quality").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("Quality session was not swapped to Saturday",
                saved.value.weeklySlots["run"]?.get(1)==6)
            assertTrue("Displaced easy session should move to Tuesday",
                saved.value.weeklySlots["run"]?.get(2)==2)
            assertTrue("Long-run weekday changed unexpectedly",
                saved.value.weeklySlots["run"]?.get(0)==7)
        }
        compose.onNodeWithTag("builder-running-day-6").performClick()
        compose.onNodeWithTag("builder-run-sheet-clear").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("Clearing should unplace quality, not remove it",
                saved.value.weeklySlots["run"]?.get(1)==0)
            assertTrue("Weekly run count must remain five",saved.value.runDays==5)
        }
        compose.onNodeWithTag("builder-figma-continue").assertIsNotEnabled()
        compose.onNodeWithTag("builder-running-day-6").performClick()
        compose.onNodeWithTag("builder-run-sheet-option-quality").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()

        // Advanced session-ordinal and quality controls remain usable.
        compose.onNodeWithTag("builder-running-advanced-toggle").performScrollTo()
        compose.onNodeWithTag("builder-running-advanced-toggle").performClick()
        compose.onNodeWithTag("builder-running-custom").performScrollTo()
        compose.onNodeWithTag("builder-running-custom").performClick()
        // This guidance text is immediately below the horizontally scrollable
        // session-selector row. Scroll it in the outer vertical page first:
        // horizontal performScrollTo alone does not expose an offscreen row.
        compose.onNodeWithText("Selected:", substring=true).performScrollTo()
        compose.onNodeWithTag("builder-run-session-2").performScrollTo()
        compose.onNodeWithTag("builder-run-session-2").assertIsDisplayed()
        screenshot("builder-running-week-session-selector-visible")
        compose.onNodeWithTag("builder-run-session-2").performClick()
        compose.runOnIdle {
            assertTrue("Explicit Easy 1 session selection did not register",
                selected.value==2)
        }
        screenshot("builder-running-week-session-selected")
        // The picker sits below the fold inside the frame's vertical scroll,
        // with Saturday additionally inside a horizontal scroll row. Match the
        // working integration test: reveal the lower editor first, then the row.
        screenshot("builder-running-week-editor-before-scroll")
        val beforeRow=compose.onNodeWithTag("builder-run-editor-weekday-row")
            .getUnclippedBoundsInRoot()
        val beforeFooter=compose.onNodeWithTag("builder-figma-continue")
            .getUnclippedBoundsInRoot()
        android.util.Log.i("RunningWeekQA",
            "before scroll weekday=$beforeRow fixedContinue=$beforeFooter")
        compose.onNodeWithTag("builder-run-clear-selected").performScrollTo()
        compose.onNodeWithTag("builder-run-editor-weekday-row").performScrollTo()
        val afterRow=compose.onNodeWithTag("builder-run-editor-weekday-row")
            .getUnclippedBoundsInRoot()
        android.util.Log.i("RunningWeekQA",
            "after scroll weekday=$afterRow fixedContinue=$beforeFooter")
        compose.onNodeWithTag("builder-run-editor-weekday-row").assertIsDisplayed()
        screenshot("builder-running-week-editor-weekday-row-visible")
        compose.onNodeWithTag("builder-run-editor-day-6").performScrollTo()
        compose.onNodeWithTag("builder-run-editor-day-6").assertIsDisplayed()
        compose.onNodeWithTag("builder-run-editor-day-6").performClick()
        screenshot("builder-running-week-after-editor-assignment")
        compose.runOnIdle {
            assertTrue("Chosen day not persisted in weekly slots",
                saved.value.weeklySlots["run"]?.get(2)==6)
            assertTrue("Newly chosen weekday not made available",6 in saved.value.days)
        }
        compose.onNodeWithTag("builder-running-assignment-progress").assertExists()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
        screenshot("builder-running-week-manual-placement")
    }

    @Test fun approvedBuilderStrengthGoalAndSetupRenderAtCurrentViewport() {
        val state=TrainingState(initializing=false,account="synthetic-responsive")
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    PlanBuilder(state=state,actions=PlanActions(),
                        discipline="str",onDone={},onSignIn={},
                        onPreview={_,_->})
                }
            }
        }
        compose.onNodeWithTag("figma-builder-frame").assertExists()
        screenshot("builder-disciplines")
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-strength-goal-1").assertExists()
        screenshot("builder-strength-goal")
        compose.onNodeWithTag("builder-strength-goal-1").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-strength-weekly").assertExists()
        compose.onNodeWithTag("builder-strength-split").assertExists()
        screenshot("builder-strength-setup")
        compose.onNodeWithTag("builder-strength-customize").performClick()
        compose.onNodeWithTag("builder-figma-continue").assertIsNotEnabled()
        screenshot("builder-strength-unassigned")
        // The weekday picker is below the fold at 360dp/130%. A real user
        // must scroll it into view before tapping; do not dispatch an offscreen tap.
        compose.onNodeWithTag("builder-strength-weekday-1").performScrollTo()
        compose.onNodeWithTag("builder-strength-weekday-1").assertIsDisplayed()
        compose.onNodeWithTag("builder-strength-weekday-1").performClick()
        // Capture before asserting, so an unexpected placement-state failure is diagnosable.
        screenshot("builder-strength-after-first-tap")
        compose.onNodeWithText("1 of 2 placed").assertExists()
        compose.onNodeWithTag("builder-strength-edit-schedule").performScrollTo()
        compose.onNodeWithTag("builder-strength-edit-schedule").performClick()
        compose.onNodeWithTag("builder-strength-session-1").performScrollTo()
        compose.onNodeWithTag("builder-strength-session-1").performClick()
        compose.onNodeWithTag("builder-strength-weekday-4").performScrollTo()
        compose.onNodeWithTag("builder-strength-weekday-4").assertIsDisplayed()
        compose.onNodeWithTag("builder-strength-weekday-4").performClick()
        // Always capture placement diagnostics, even if the final state is wrong.
        screenshot("builder-strength-two-taps")
        compose.onNodeWithText("2 of 2 placed").assertExists()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
        screenshot("builder-strength-placed")
        compose.onNodeWithTag("builder-strength-auto").performClick()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
        screenshot("builder-strength-auto")
    }

    @Test fun strengthFigmaReferenceAndLargeFontPreferenceContentStayVisible() {
        // Figma 698:3: Monday Upper and Thursday Lower, Saturday unavailable.
        // Capture a matching manual scheduling state without an open editor.
        val request=PlanRequest(
            runDays=0,strengthDays=2,
            days=setOf(1,2,3,4,5,7),
            weeklySlots=mapOf("str" to listOf(1,4)),
            disciplineGoals=mapOf("str" to "Muscle development"),
            equipment=mapOf("db" to 1,"bands" to 1,"bb" to 1)
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideStrengthSetup(
                        request=request,
                        selectedSlot=0,onSelectedSlot={},
                        onChangeMode={},onCount={},onAssign={_,_->},
                        onClear={},onEquipment={_,_->},
                        onBack={},onEditGoal={},onContinue={}
                    )
                }
            }
        }
        compose.onNodeWithTag("builder-strength-customize").assertIsDisplayed()
        compose.onNodeWithTag("builder-strength-weekly").assertExists()
        compose.onNodeWithTag("builder-strength-split").assertExists()
        val config = compose.activity.resources.configuration
        if(config.fontScale <= 1.01f) {
            val weekly=compose.onNodeWithTag("builder-strength-weekly")
                .getUnclippedBoundsInRoot()
            assertTrue("Weekly card taller than Figma baseline tolerance: " +
                (weekly.bottom-weekly.top), (weekly.bottom-weekly.top).value <= 250f)
            if(config.screenHeightDp >= 900) {
                val split=compose.onNodeWithTag("builder-strength-split")
                    .getUnclippedBoundsInRoot()
                val continueCta=compose.onNodeWithTag("builder-figma-continue")
                    .getUnclippedBoundsInRoot()
                assertTrue("Training split overlaps Continue CTA",
                    split.bottom <= continueCta.top - 2.dp)
            }
        }
        // These bounds checks are intentionally run in every viewport/font matrix.
        listOf(
            "builder-strength-setup-goal" to "Build muscle",
            "builder-strength-setup-emphasis" to "Not available in Engine 1"
        ).forEach { (tag,label) ->
            val card=compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            val text=compose.onNodeWithText(label,useUnmergedTree=true)
                .getUnclippedBoundsInRoot()
            assertTrue("$label extends below its preference card",
                text.bottom <= card.bottom - 1.dp)
        }
        val mode=compose.onNodeWithTag("builder-strength-auto")
            .getUnclippedBoundsInRoot()
        val modeLabel=compose.onNodeWithText("Let StrideLab decide",
            useUnmergedTree=true).getUnclippedBoundsInRoot()
        assertTrue("Mode label extends past selector",
            modeLabel.right <= mode.right + 1.dp)
        screenshot("builder-strength-figma-reference")
    }

    @Test fun approvedCalisthenicsWeekAndUnassignedStatesRender() {
        val state=TrainingState(initializing=false,account="synthetic-responsive")
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    PlanBuilder(state=state,actions=PlanActions(),
                        discipline="cal",onDone={},onSignIn={},
                        onPreview={_,_->})
                }
            }
        }
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-cross-goal-cal-Pull-up progression").assertExists()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-cali-weekly").assertExists()
        screenshot("builder-calisthenics-setup")
        compose.onNodeWithTag("builder-cali-customize").performClick()
        compose.onNodeWithTag("builder-figma-continue").assertIsNotEnabled()
        screenshot("builder-calisthenics-unassigned")
        compose.onNodeWithTag("builder-cali-day-3").performClick()
        compose.onNodeWithTag("builder-cali-session-type-Pull").performClick()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("builder-cali-day-5").performClick()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
        screenshot("builder-calisthenics-assigned")
    }

    @Test fun calisthenicsFigmaReferenceAndLargeFontSectionsRemainReachable() {
        // Figma 614:335: Wednesday Push, Friday Pull, Saturday unavailable.
        // Match the approved manual state with the editor focused on Wednesday.
        val request=PlanRequest(
            runDays=0,strengthDays=0,calDays=2,
            days=setOf(1,2,3,4,5,7),
            weeklySlots=mapOf("cal" to listOf(3,5)),
            calLevel=1,pullLevel=0,coreLevel=1
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideCalisthenicsSetup(
                        request=request,
                        selectedSlot=0,onSelectedSlot={},
                        onMode={},onCount={},onAssign={_,_->},
                        onClear={},onAbility={_,_->},
                        onBack={},onContinue={}
                    )
                }
            }
        }
        compose.onNodeWithTag("builder-cali-customize").assertIsDisplayed()
        val mode=compose.onNodeWithTag("builder-cali-auto")
            .getUnclippedBoundsInRoot()
        val modeLabel=compose.onNodeWithText("Let StrideLab decide",
            useUnmergedTree=true).getUnclippedBoundsInRoot()
        assertTrue("Calisthenics mode label clipped horizontally",
            modeLabel.left >= mode.left - 1.dp &&
            modeLabel.right <= mode.right + 1.dp)
        assertTrue("Calisthenics mode label clipped vertically",
            modeLabel.bottom <= mode.bottom + 1.dp)
        compose.onNodeWithTag("builder-cali-weekly").assertExists()
        compose.onNodeWithTag("builder-cali-starting-levels").assertExists()
        compose.onNodeWithText("2 of 2 placed").assertExists()
        compose.onNodeWithText("Suggested days  ·  editable").assertExists()
        compose.onNodeWithText("Remove").assertExists()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
        screenshot("builder-calisthenics-figma-reference")

        val config=compose.activity.resources.configuration
        if(config.fontScale <= 1.01f) {
            val weekly=compose.onNodeWithTag("builder-cali-weekly")
                .getUnclippedBoundsInRoot()
            assertTrue("Calisthenics weekly preview exceeds reference tolerance: " +
                (weekly.bottom-weekly.top), (weekly.bottom-weekly.top).value <= 480f)
            if(config.screenHeightDp >= 900) {
                val levels=compose.onNodeWithTag("builder-cali-starting-levels")
                    .getUnclippedBoundsInRoot()
                assertTrue("Calisthenics starting levels taller than Figma tolerance: " +
                    (levels.bottom-levels.top), (levels.bottom-levels.top).value <= 94f)
                val cta=compose.onNodeWithTag("builder-figma-continue")
                    .getUnclippedBoundsInRoot()
                assertTrue("Calisthenics starting levels overlap fixed Continue",
                    levels.bottom <= cta.top - 2.dp)
            }
        }
        // With larger fonts these sections can be below the fold but must scroll
        // into view. Preserve the fixed CTA without shrinking readable content.
        compose.onNodeWithTag("builder-cali-starting-levels").performScrollTo()
        compose.onNodeWithTag("builder-cali-starting-levels").assertIsDisplayed()
    }

    @Test fun activityFixedMapAndIndependentSheetRenderAtCurrentViewport() {
        val recorded=ActivityRecord(
            "synthetic-responsive-activity","Recorded Evening Run","run","manual",
            Instant.parse("2026-11-01T09:00:00Z"),
            7400.0,2882.0,2792.0,379.0,147.0,64.0
        )
        val state=TrainingState(initializing=false,
            account="synthetic-responsive",activities=listOf(recorded))
        val matte=mutableStateOf(false)
        val tab=mutableStateOf(0)
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideActivityDetailSurface(
                        activity=recorded,recorded=null,detailSource=recorded,
                        matchedSourceIds=setOf(recorded.id),history=listOf(recorded),
                        state=state,
                        actions=NativeActions({}, {}, {}, {}, { _, _ -> }),
                        online=false,selectedTab=tab.value,onTab={ tab.value=it },
                        matte=matte.value,onToggleMatte={matte.value=!matte.value},
                        onBack={}
                    )
                }
            }
        }
        compose.onNodeWithTag("activity-map-hero").assertIsDisplayed()
        compose.onNodeWithTag("activity-sheet-scroll").assertExists()
        screenshot("activity-atmospheric")
        compose.onNodeWithTag("activity-change-glass-variant").assertDoesNotExist()
        compose.onNodeWithTag("activity-map-fullscreen").assertDoesNotExist()
        compose.onNodeWithTag("activity-sheet-scroll").performTouchInput { swipeUp() }
        compose.onNodeWithTag("activity-map-hero").assertIsDisplayed()
        screenshot("activity-map-persistent")
    }

    @Test fun planDatesFigmaBaselineAndControlsAtCurrentViewport() {
        // Approved Figma 720:546. On 2026-11-04, plus five days matches the
        // illustrated Monday 9 November; keep the date valid on future reruns.
        val date=mutableStateOf(LocalDate.now().plusDays(5).toString())
        val weeks=mutableStateOf("8")
        val minutes=mutableStateOf("60")
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksidePlanDetailsScreen(
                        start=date.value,weeks=weeks.value,minutes=minutes.value,
                        onStart={ date.value=it },onWeeks={ weeks.value=it },
                        onMinutes={ minutes.value=it },
                        onBack={},onContinue={}
                    )
                }
            }
        }
        compose.onNodeWithText("Set your plan dates").assertExists()
        compose.onNodeWithTag("builder-start-date").assertExists()
        compose.onNodeWithTag("builder-date-calendar-vector", useUnmergedTree=true).assertExists()
        val dateCard = compose.onNodeWithTag("builder-start-date").getUnclippedBoundsInRoot()
        val dateHelper = compose.onNodeWithText("Tap to choose a date", useUnmergedTree=true)
            .getUnclippedBoundsInRoot()
        assertTrue("Plan Dates helper clips the date row at this font scale",
            dateHelper.bottom <= dateCard.bottom - 4.dp)
        compose.onNodeWithTag("builder-weeks-8").assertExists()
        // The approved setup no longer imposes a per-session minutes limit.
        compose.onNodeWithTag("builder-time-limit").assertDoesNotExist()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
        screenshot("builder-plan-dates-figma-reference")

        // Validate the selectable plan length and ensure the removed
        // session-minute restriction does not reappear.
        compose.onNodeWithTag("builder-weeks-12").performScrollTo()
        compose.onNodeWithTag("builder-weeks-12").performClick()
        compose.runOnIdle {
            assertTrue("Plan length choice not reflected in builder state",
                weeks.value=="12")
        }
        compose.onNodeWithTag("builder-time-limit").assertDoesNotExist()
        compose.onNodeWithText("Minutes available per session (15–120)")
            .assertDoesNotExist()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
    }


    @Test fun availabilityNineSessionMatchedStateAndCoachingChecksAtCurrentViewport() {
        // This is a real Engine 1-compatible request, not the illustrative
        // five-run/two-strength/two-calisthenics mix that lacks a third pairing.
        val matched = PlanRequest(
            start=LocalDate.of(2026,11,9), weeks=1, goal="Aerobic foundation",
            days=setOf(1,2,3,4,5,7), longDay=7,
            runDays=5, strengthDays=0, calDays=4, conditioningDays=0,
            qualityCount=1, qualityDays=setOf(2), experience=1,
            weeklyKm=35.0, longestKm=12.0, minutes=60,
            dailyMinutes=120, policyChoice="multi-session"
        )
        val generated=MultiSessionEngine1.generate(
            matched,"synthetic-responsive","availability-matched",Instant.EPOCH)
        val firstWeek=generated.sessions
        assertTrue("Engine 1 must actually generate all nine sessions",
            firstWeek.size==9)
        assertTrue("Nine sessions should occupy six real weekdays",
            firstWeek.map { it.date.dayOfWeek.value }.toSet().size==6)
        assertTrue("Three double days must be genuinely compatible",
            firstWeek.groupBy { it.date }.values.count { it.size==2 }==3)
        val state=mutableStateOf(matched)
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideAvailabilityScreen(
                        request=state.value,
                        onDay={ day ->
                            state.value=state.value.copy(
                                days=if(day in state.value.days)
                                    state.value.days-day else state.value.days+day)
                        },
                        onRemovePinnedDay={ day ->
                            state.value=WeeklyPlacement.makeUnavailable(state.value,day)
                        },
                        onMultiSessionToggle={ enabled ->
                            state.value=state.value.copy(
                                policyChoice=if(enabled)"multi-session" else "coordinated")
                        },
                        dailyBudget="120", onBack={},onContinue={}
                    )
                }
            }
        }
        compose.onNodeWithText("9").assertExists()
        compose.onNodeWithText("sessions across 6 days").assertExists()
        // The new capacity summary gives actual selected days; retain the
        // seven selectable weekday controls instead of a decorative ratio.
        compose.onNodeWithTag("builder-capacity-days")
            .assertTextEquals("sessions across 6 days")
        (1..7).forEach { day ->
            compose.onNodeWithTag("available-day-$day").assertExists()
        }
        compose.onNodeWithTag("available-day-6").assertExists()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
        screenshot("builder-availability-nine-session-matched")

        // Reach the complete coaching panel through ordinary vertical scrolling.
        compose.onNodeWithText("Evaluated after plan generation").performScrollTo()
        compose.onNodeWithText("NEXT: COACHING CHECKS").assertIsDisplayed()
        compose.onNodeWithText("Recovery balance",substring=true).assertIsDisplayed()
        compose.onNodeWithText("Training load and progression",substring=true)
            .assertIsDisplayed()
        // Scroll to the local bottom clearance, not merely to the last
        // text node: performScrollTo() on text can stop at partial visibility.
        compose.onNodeWithTag("builder-availability-coaching-clearance")
            .performScrollTo()
        compose.onNodeWithText("Evaluated after plan generation").assertIsDisplayed()
        val coachingBottom=compose.onNodeWithText("Evaluated after plan generation")
            .getUnclippedBoundsInRoot().bottom
        val continueTop=compose.onNodeWithTag("builder-figma-continue")
            .getUnclippedBoundsInRoot().top
        assertTrue("Coaching checks must clear the fixed Continue dock",
            coachingBottom <= continueTop-2.dp)
        screenshot("builder-availability-coaching-checks-reachable")

        // Use real day-chip taps, not direct state mutations, to change and
        // restore availability. Five days cannot offer the required four pairs.
        compose.onNodeWithTag("available-day-5").performScrollTo()
        compose.onNodeWithTag("available-day-5").performClick()
        compose.onNodeWithText("sessions across 5 days").assertExists()
        compose.onNodeWithTag("builder-figma-continue").assertIsNotEnabled()
        compose.runOnIdle { assertTrue(state.value.days.size==5) }
        compose.onNodeWithTag("available-day-5").performClick()
        compose.onNodeWithText("sessions across 6 days").assertExists()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
        compose.runOnIdle {
            assertTrue(state.value.days.size==6 && 6 !in state.value.days)
        }
    }

    @Test fun availabilityPinnedRemovalConfirmationAtCurrentViewport() {
        val state=mutableStateOf(
            PlanRequest(
                days=setOf(1,2,3,4,5,7),runDays=3,longDay=7,
                weeklySlots=mapOf("run" to listOf(7,3,1)))
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideAvailabilityScreen(
                        request=state.value,
                        onDay={ day ->
                            state.value=state.value.copy(
                                days=if(day in state.value.days)
                                    state.value.days-day else state.value.days+day)
                        },
                        onRemovePinnedDay={ day ->
                            state.value=WeeklyPlacement.makeUnavailable(state.value,day)
                        },
                        onMultiSessionToggle={},onBack={},onContinue={}
                    )
                }
            }
        }
        compose.onNodeWithTag("available-day-7").performClick()
        compose.onNodeWithText("Change Sunday availability?").assertExists()
        // The native AlertDialog occupies a separate root window. Capturing
        // the activity root during the dialog fails on the emulator; assert
        // dialog behavior here and capture after it has closed.
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertTrue(state.value.weeklySlots["run"]==listOf(7,3,1))
            assertTrue(7 in state.value.days)
        }
        compose.onNodeWithTag("available-day-7").performClick()
        compose.onNodeWithTag("builder-confirm-remove-pinned-day").performClick()
        compose.runOnIdle {
            assertTrue(state.value.runDays==3)
            assertTrue(state.value.weeklySlots["run"]==listOf(0,3,1))
            assertTrue(7 !in state.value.days)
        }
        compose.onNodeWithTag("builder-figma-continue").assertIsNotEnabled()
        screenshot("builder-availability-pinned-unassigned")
    }


    @Test fun availabilityManualDoubleDayAssignmentsRespectActualPairing() {
        val initial=PlanRequest(
            start=LocalDate.of(2026,11,9),weeks=1,goal="Aerobic foundation",
            days=setOf(1,2,3,4,5,7),longDay=7,
            runDays=3,calDays=2,strengthDays=0,conditioningDays=0,
            qualityCount=1,qualityDays=setOf(2),experience=1,
            weeklyKm=35.0,longestKm=12.0,minutes=60,dailyMinutes=120,
            policyChoice="multi-session",
            weeklySlots=mapOf("run" to listOf(7,2,1))
        )
        val state=mutableStateOf(initial)
        val selected=mutableStateOf(0)
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideAvailabilityScreen(
                        request=state.value,
                        onDay={ day ->
                            state.value=state.value.copy(
                                days=if(day in state.value.days)
                                    state.value.days-day else state.value.days+day)
                        },
                        onRemovePinnedDay={ day ->
                            state.value=WeeklyPlacement.makeUnavailable(state.value,day)
                        },
                        onMultiSessionToggle={ enabled ->
                            state.value=state.value.copy(
                                policyChoice=if(enabled)"multi-session" else "coordinated")
                        },
                        selectedOtherDiscipline="cal",
                        selectedOtherSlot=selected.value,
                        onSelectOtherDiscipline={},
                        onSelectOtherSlot={ selected.value=it },
                        onAssignOtherDay={ discipline,index,day ->
                            state.value=WeeklyPlacement.put(
                                state.value,discipline,index,day)
                        },
                        onClearOtherSlot={ discipline,index ->
                            state.value=WeeklyPlacement.clear(
                                state.value,discipline,index)
                        },
                        onAutoOther={ discipline ->
                            state.value=state.value.copy(
                                weeklySlots=state.value.weeklySlots-discipline)
                        },
                        dailyBudget="120",onBack={},onContinue={}
                    )
                }
            }
        }
        compose.onNodeWithTag("builder-other-discipline-editor").performScrollTo()
        compose.onNodeWithTag("builder-other-day-1").performClick()
        compose.runOnIdle {
            assertTrue(state.value.weeklySlots["cal"]==listOf(1,0))
        }
        compose.onNodeWithTag("builder-figma-continue").assertIsNotEnabled()
        compose.onNodeWithTag("builder-other-session-1").performClick()
        compose.onNodeWithTag("builder-other-day-5").performClick()
        compose.runOnIdle {
            assertTrue(state.value.weeklySlots["cal"]==listOf(1,5))
            val generated=MultiSessionEngine1.generate(
                state.value,"synthetic-responsive","manual-double",Instant.EPOCH)
            assertTrue(generated.sessions.groupBy { it.date }.values
                .any { sessions ->
                    sessions.size==2 &&
                        sessions.map { it.discipline }.toSet()==setOf("run","cal")
                })
        }
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
        // The assigned weekday must remain wholly inside its session chip,
        // including at 150% font scale. Do not hide the second label.
        for(index in 0..1) {
            val card=compose.onNodeWithTag("builder-other-session-$index")
                .getUnclippedBoundsInRoot()
            val weekday=compose.onNodeWithTag(
                "builder-other-session-weekday-$index",
                useUnmergedTree=true).getUnclippedBoundsInRoot()
            assertTrue("Mixed-sport session weekday clips its chip at index $index",
                weekday.bottom <= card.bottom - 2.dp)
        }
        screenshot("builder-availability-compatible-double-day")
        compose.onNodeWithTag("builder-other-day-2").performClick()
        compose.onNodeWithText("This engine allows only easy running with calisthenics",
            substring=true).assertExists()
        compose.onNodeWithTag("builder-figma-continue").assertIsNotEnabled()
        compose.runOnIdle {
            assertTrue(state.value.weeklySlots["cal"]==listOf(1,2))
        }
        compose.onNodeWithTag("builder-other-day-5").performClick()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
        compose.runOnIdle {
            assertTrue(state.value.weeklySlots["cal"]==listOf(1,5))
        }
    }

    @Test fun weekPreviewSeparatesRequestedStateFromGeneratedNineSessionDraft() = verifyWeekPreview(engine15 = false)

    @Test fun engine15WeekPreviewSeparatesRequestedStateFromGeneratedNineSessionDraft() = verifyWeekPreview(engine15 = true)

    @Test fun weekPreviewShowsSelectedWeekDistanceAndKeepsNavigation() {
        val base=matchedNineSessionDraft("week-preview-distance")
        val plan=base.copy(
            request=base.request.copy(weeks=2,weeklyKm=40.0,targetWeeklyKm=80.0),
            sessions=base.sessions+base.sessions.first { it.discipline=="run" }.copy(
                id="week-two-unknown",date=base.request.start.plusWeeks(1),distanceKm=null
            )
        )
        val selected=mutableStateOf(0)
        val firstWeek=weekReviewDistance(plan.sessions.filter { it.date in plan.request.start..plan.request.start.plusDays(6) })
        show { TracksideWeekReview(plan.request,plan,false,{}, {},weekIndex=selected.value,onWeekChange={selected.value=it}) }
        compose.onNodeWithText("planned km").assertExists()
        compose.onNodeWithText(String.format(java.util.Locale.getDefault(),"%.1f",requireNotNull(firstWeek))).assertExists()
        compose.onNodeWithText("Requested target:",substring=true).assertExists()
        compose.onNodeWithTag("builder-previous-week").assertIsNotEnabled()
        compose.onNodeWithTag("builder-next-week").performClick()
        compose.onNodeWithText("distance unknown").assertExists()
        compose.onNodeWithTag("builder-next-week").assertIsNotEnabled()
        compose.onNodeWithTag("builder-previous-week").performClick()
        compose.onNodeWithText("planned km").assertExists()
    }

    @Test fun weekPreviewKeepsNoRunningStateInsteadOfShowingZeroKilometers() {
        val base=matchedNineSessionDraft("week-preview-no-running")
        val plan=base.copy(request=base.request.copy(runDays=0,targetWeeklyKm=80.0),sessions=base.sessions.filter { it.discipline!="run" })
        val generated=mutableStateOf(false)
        show { TracksideWeekReview(plan.request,if(generated.value)plan else null,false,{}, {}) }
        compose.onNodeWithText("no running").assertExists()
        compose.runOnIdle { generated.value=true }
        compose.onNodeWithText("no running").assertExists()
        compose.onNodeWithText("planned km").assertDoesNotExist()
        compose.onNodeWithText("Requested target:",substring=true).assertDoesNotExist()
    }

    @Test fun researchRouteIsExplicitSelectedAndCannotSubmitAsPlan() {
        val request=PlanRequest(runDays=3,days=setOf(1,3,5),qualityCount=0)
        val selected=mutableStateOf(false)
        show {
            TracksideWeekReview(request,null,false,{}, {},experimentalSelected=selected.value,
                onExperimentalSelected={selected.value=it})
        }
        compose.onNodeWithTag("builder-route-existing").assertIsSelected()
        compose.onNodeWithTag("builder-route-research").assertIsNotSelected()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
        compose.onNodeWithTag("builder-route-research").performScrollTo().performClick()
        compose.onNodeWithTag("builder-route-research").assertIsSelected()
        compose.onNodeWithTag("builder-figma-continue").assertIsNotEnabled()
        compose.onNodeWithText("Experimental mileage calendar").assertExists()
        compose.onNodeWithTag("builder-route-existing").performScrollTo().performClick()
        compose.onNodeWithTag("builder-route-existing").assertIsSelected()
        compose.onNodeWithTag("builder-figma-continue").assertIsEnabled()
    }

    @Test fun savedResearchReviewCanBeReopenedWithoutPlanActions() {
        val request=PlanRequest(
            goal="General fitness",experience=2,start=LocalDate.of(2026,11,16),weeks=5,
            days=setOf(1,2,3,5,6,7),runDays=6,longDay=6,qualityCount=1,
            qualityDays=setOf(2),weeklyKm=60.0,targetWeeklyKm=80.0
        )
        val limits=IndependentAndroidPreviewConstraints(
            selectedRunDays=request.days,
            explicitlyEnteredStartingWeeklyKm=60.0,
            explicitlyEnteredWeeklyCeilingKm=80.0,
            explicitlyEnteredMaxKmByDay=request.days.associateWith { if(it==1)5.0 else 25.0 },
            explicitlyEnteredPreviousLongRunKm=17.0,
            explicitlyEnteredLongRunCeilingKm=25.0,
            explicitlyEnteredMaxLongRunIncreaseKm=2.0
        )
        val preview=IndependentAndroidResearchBridge.preview(request,limits)
            as IndependentAndroidPreviewDecision.ReadOnly
        val saved=IndependentResearchDraft.fromPreview(
            "synthetic-responsive",request,preview,limits,
            id="0fa768cd-1f1a-47f8-85fb-4c4d6514dce7",createdAt=Instant.EPOCH
        )
        show {
            IndependentAndroidResearchPreviewPanel(
                request=request,savedResearchReviews=listOf(saved)
            )
        }
        compose.onNodeWithTag("builder-research-open-${saved.id}").performClick()
        compose.onNodeWithTag("builder-research-reopened").assertExists()
        compose.onNodeWithText("Research comparison · not executable").assertExists()
        compose.onNodeWithText("Week 1:",substring=true).assertExists()
        compose.onNodeWithText("Finalize Plan").assertDoesNotExist()
        compose.onNodeWithText("Approve plan").assertDoesNotExist()
    }

    private fun verifyWeekPreview(engine15: Boolean) {
        val plan = matchedNineSessionDraft("week-preview-matched", engine15)
        val prefix = if (engine15) "engine15-" else ""
        val displayed = mutableStateOf<PlanSnapshot?>(null)
        var opened: String? = null
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    TracksideWeekReview(
                        request = plan.request,
                        plan = displayed.value,
                        busy = false,
                        onBack = {},
                        onContinue = {},
                        onOpenSession = { opened = it }
                    )
                }
            }
        }

        // Before generation, show truthful demand/availability only. Do not
        // manufacture the engine's eventual weekday prescriptions.
        compose.onNodeWithText("9").assertExists()
        compose.onNodeWithText("AVAILABLE DAYS").assertExists()
        compose.onNodeWithText("Generate My Plan").assertExists()
        compose.onNodeWithText("MAX 2 / DAY").assertExists()
        compose.onNodeWithText(
            "Chosen weekdays are requests", substring = true
        ).assertDoesNotExist()
        compose.onNodeWithText(
            "Unassigned sessions will be placed", substring = true
        ).assertExists()
        screenshot(prefix + "builder-week-preview-requested-nine")

        compose.runOnUiThread { displayed.value = plan }
        compose.waitForIdle()

        // After the actual Engine 1 draft exists, every displayed prescription
        // comes from the saved draft and approval remains a separate action.
        compose.onNodeWithText("ACTIVE DAYS").assertExists()
        compose.onNodeWithText("Finalize Plan").assertExists()
        compose.onNodeWithText("MAX 2 / DAY").assertExists()
        compose.onNodeWithText("5 Running").assertExists()
        compose.onNodeWithText("4 Cali").assertExists()
        val first = plan.sessions.sortedWith(
            compareBy<PlannedSession>({ it.date }, { it.id })
        ).first()
        compose.onNodeWithTag("builder-preview-session-" + first.id)
            .assertExists()
        screenshot(prefix + "builder-week-preview-generated-nine")

        compose.onNodeWithText(
            "Saved engine prescriptions", substring = true
        ).performScrollTo()
        compose.onNodeWithText(
            "Saved engine prescriptions", substring = true
        ).assertIsDisplayed()
        val statusBottom = compose.onNodeWithText(
            "Saved engine prescriptions", substring = true
        ).getUnclippedBoundsInRoot().bottom
        val ctaTop = compose.onNodeWithTag("builder-figma-continue")
            .getUnclippedBoundsInRoot().top
        assertTrue("Generated Week Preview status must clear the fixed CTA",
            statusBottom <= ctaTop - 2.dp)
        screenshot(prefix + "builder-week-preview-generated-reachable")

        compose.onNodeWithTag("builder-preview-session-" + first.id)
            .performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue("Generated preview session did not remain interactive",
                opened == first.id)
        }
    }

    @Test fun draftApprovalAgendaUsesCompactNineSessionRowsAndClearsNavigation() = verifyDraftApproval(engine15 = false)

    @Test fun engine15DraftApprovalAgendaUsesCompactNineSessionRowsAndClearsNavigation() = verifyDraftApproval(engine15 = true)

    private fun verifyDraftApproval(engine15: Boolean) {
        val plan = matchedNineSessionDraft("draft-approval-matched", engine15)
        val prefix = if (engine15) "engine15-" else ""
        val state = TrainingState(
            initializing = false,
            account = "synthetic-responsive",
            plans = listOf(plan)
        )
        var approvals = 0
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    NativeStrideContent(
                        state,
                        true,
                        NativeActions(
                            {}, {}, {}, {}, { _, _ -> },
                            planning = PlanActions(
                                approve = { _, _ -> approvals++ }
                            )
                        )
                    )
                }
            }
        }

        compose.onNodeWithTag("nav-training").assertIsDisplayed().performClick()
        // The unified timeline is the current Training landing screen;
        // draft approval and legacy Agenda remain accessible via Plan tools.
        compose.onNodeWithTag("training-unified-timeline").assertExists()
        compose.onNodeWithTag("training-plan-tools").assertIsDisplayed().performClick()
        compose.onNodeWithTag("training-view-agenda")
            .assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("DRAFT").assertExists()
        val titleBounds = compose.onNodeWithTag("screen-title")
            .getUnclippedBoundsInRoot()
        val badgeBounds = compose.onNodeWithTag("screen-title-badge")
            .getUnclippedBoundsInRoot()
        val titleCenter = (titleBounds.top.value + titleBounds.bottom.value) / 2f
        val badgeCenter = (badgeBounds.top.value + badgeBounds.bottom.value) / 2f
        assertTrue("Draft badge must share the Training title row",
            kotlin.math.abs(titleCenter - badgeCenter) <= 10f)

        val agendaBounds = compose.onNodeWithTag("training-view-agenda")
            .getUnclippedBoundsInRoot()
        val weekBounds = compose.onNodeWithTag("training-week-span")
            .getUnclippedBoundsInRoot()
        val agendaCenter = (agendaBounds.top.value + agendaBounds.bottom.value) / 2f
        val weekCenter = (weekBounds.top.value + weekBounds.bottom.value) / 2f
        assertTrue("Draft week navigation must share the Day/Agenda row",
            kotlin.math.abs(agendaCenter - weekCenter) <= 12f)

        compose.onNodeWithTag("training-draft-approval-dock")
            .assertIsDisplayed()
        compose.onNodeWithText("Approve plan").assertIsDisplayed()

        val ordered = plan.sessions.sortedWith(
            compareBy<PlannedSession>({ it.date }, { it.id })
        )
        val first = ordered.first()
        val last = ordered.last()
        compose.onNodeWithTag("workout-" + first.id).assertExists()
        val config = compose.activity.resources.configuration
        if (config.screenWidthDp >= 400 && config.fontScale <= 1.01f) {
            val row = compose.onNodeWithTag("workout-" + first.id)
                .getUnclippedBoundsInRoot()
            assertTrue("Draft Agenda row is too tall for the approved compact state",
                (row.bottom - row.top).value <= 58f)
        }
        screenshot(prefix + "training-draft-approval")

        // The final prescription must be normally scrollable above the approval
        // dock, and the approval dock itself must stay above bottom navigation.
        compose.onNodeWithTag("screen")
            .performScrollToNode(hasTestTag("workout-" + last.id))
        compose.onNodeWithTag("workout-" + last.id).assertIsDisplayed()
        val lastBottom = compose.onNodeWithTag("workout-" + last.id)
            .getUnclippedBoundsInRoot().bottom
        val dock = compose.onNodeWithTag("training-draft-approval-dock")
            .getUnclippedBoundsInRoot()
        assertTrue("Last draft Agenda row is obscured by approval dock",
            lastBottom <= dock.top - 2.dp)
        val navTop = compose.onNodeWithTag("nav-training")
            .getUnclippedBoundsInRoot().top
        assertTrue("Draft approval dock overlaps bottom navigation",
            dock.bottom <= navTop - 2.dp)
        screenshot(prefix + "training-draft-approval-last-session-reachable")

        compose.onNodeWithText("Approve plan").performClick()
        compose.runOnIdle {
            assertTrue("Approve plan did not call the guarded approval action",
                approvals == 1)
        }
    }

    @Test fun builderGeneratesFromAvailabilityWithoutRedundantBlueprintPage() {
        val state=TrainingState(initializing=false,account="synthetic-responsive")
        var generatedRequests=0
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    PlanBuilder(state=state,actions=PlanActions(
                        generate={ generatedRequests++ }
                    ),onDone={},onSignIn={},onPreview={_,_->})
                }
            }
        }
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        // The independent coach deliberately defers an unverified zero-mileage
        // baseline. Enter plausible synthetic training inputs before asserting
        // the Generate callback; this is a navigation test, not a no-history test.
        compose.onNodeWithTag("builder-ability-weekly-slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(30f) }
        compose.onNodeWithTag("builder-ability-longest-slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(10f) }
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.onNodeWithTag("builder-start-date").assertExists()
        compose.onNodeWithTag("available-day-6").assertExists()
        compose.onNodeWithText("Generate My Plan").assertExists()
        compose.onNodeWithText("Your training blueprint").assertDoesNotExist()
        compose.onNodeWithTag("builder-generate-draft-note").assertExists()
        screenshot("builder-availability-final-generate")
        compose.onNodeWithTag("builder-figma-continue").performClick()
        compose.runOnIdle { assertTrue("Generate should be called once",generatedRequests==1) }
        compose.onNodeWithText("Your training blueprint").assertDoesNotExist()
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
        compose.onNodeWithTag("today-map-illustration", useUnmergedTree=true).assertExists()
        compose.onNodeWithContentDescription("Open planned workout", useUnmergedTree = true).assertExists()
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
        assertTrue("Calendar touch width ${bounds.right - bounds.left} smaller than 48dp", (bounds.right - bounds.left).value >= 48f)
        assertTrue("Calendar touch height ${bounds.bottom - bounds.top} smaller than 48dp", (bounds.bottom - bounds.top).value >= 48f)
        screenshot("training")
        day.performClick()
        compose.runOnIdle { assertTrue("Selected day changed", selected.value == start) }
    }


    @Test fun selectedSundayIsFullyVisibleAtLargeFontAndCompactWidth() {
        val plan = snapshot()
        val sunday = start.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY))
        show { TracksideTrainingWeekChart(plan, sunday) {} }
        compose.waitForIdle()
        val selectedDay = compose.onNodeWithTag("calendar-day-$sunday")
        selectedDay.assertIsDisplayed()
        val dayBounds = selectedDay.getUnclippedBoundsInRoot()
        val chartBounds = compose.onNodeWithTag("training-week-chart").getUnclippedBoundsInRoot()
        assertTrue("Selected Sunday extends beyond the visible calendar viewport",
            dayBounds.right <= chartBounds.right + 1.dp)
    }

    @Test fun completeAppShellKeepsTrainingNavigationReachableWithLargeText() {
        // Component screenshots above cannot detect bottom-navigation and system-inset
        // collisions. Exercise the actual NavHost using only synthetic plan records.
        val plan = snapshot()
        val state = TrainingState(
            initializing = false, account = "synthetic-athlete", plans = listOf(plan)
        )
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    NativeStrideContent(
                        state, true, NativeActions({}, {}, {}, {}, { _, _ -> })
                    )
                }
            }
        }
        compose.onNodeWithTag("nav-training").assertIsDisplayed().performClick()
        compose.onNodeWithTag("training-unified-timeline").assertExists()
        compose.onNodeWithTag("training-plan-tools").assertIsDisplayed().performClick()
        compose.onNodeWithTag("screen-title").assertTextEquals("Training")
        compose.onNodeWithTag("screen").performScrollToNode(hasTestTag("training-manage-plan"))
        compose.onNodeWithTag("nav-settings").assertIsDisplayed()
        screenshot("training-full-shell")

        compose.onNodeWithTag("training-manage-plan").performClick()
        compose.onNodeWithTag("manage-back").assertExists()
        compose.onNodeWithTag("nav-training").assertIsSelected()
        screenshot("manage-full-shell")
        compose.onNodeWithTag("manage-back").performClick()
        compose.waitUntil(timeoutMillis = 10_000L) {
            runCatching {
                compose.onNodeWithTag("screen-title").assertTextEquals("Training")
                true
            }.getOrDefault(false)
        }
        compose.onNodeWithTag("nav-training").assertIsSelected()
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
        assertTrue("Manage action smaller than 48dp", (bounds.bottom - bounds.top).value >= 48f)
        screenshot("manage-actions")
        action.performClick()
        compose.onNodeWithText("Create a reviewed draft?").assertExists()
    }
}
