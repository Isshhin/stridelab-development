package io.stridelab.privatealpha

import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TodayThumbnailRequestsTest {
    @get:Rule val compose = createAndroidComposeRule<NativeMainActivity>()

    @Test fun requestsOnlyDisplayedActivitiesNewestFirst() {
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val elapsed = java.time.Duration.between(start, Instant.now()).seconds.coerceAtLeast(2)
        fun activity(id: String, time: Instant) = ActivityRecord(
            id = id, title = id, type = "run", source = "garmin", startedAt = time,
            distanceM = 5000.0, durationS = 1800.0, movingTimeS = 1790.0,
            recordedPace = null, averageHr = null, elevationM = null
        )
        val older = activity("older", start)
        val newer = activity("newer", start.plusSeconds(elapsed / 2))
        val yesterday = activity("yesterday", start.minusSeconds(3600))
        val records = listOf(yesterday, older, newer)
        val requests = java.util.Collections.synchronizedList(mutableListOf<String>())
        compose.runOnUiThread {
            compose.activity.setContent {
                StrideTheme {
                    StrideLabTodayScreen(
                        state = TrainingState(initializing = false, account = "test", activities = records,
                            cachedDetailIds = records.map { it.id }.toSet()),
                        visibleActivities = records, online = false, onOpenTraining = {}, onOpenActivity = {},
                        onOpenWorkout = { _, _ -> }, onOpenActivityDetail = {}, onOpenMore = {}, onSignIn = {},
                        onEnsureCachedDetail = { requests.add(it) }
                    )
                }
            }
        }
        compose.waitUntil(8_000) { requests.size >= 2 }
        compose.runOnIdle { assertEquals(listOf("newer", "older"), requests.toList()) }
    }
}
