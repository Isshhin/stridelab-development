package io.stridelab.privatealpha

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserActivityWorkoutLabelStoreTest {
    @Test fun labelsPersistOfflineAndRemainOwnerScoped() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = UserActivityWorkoutLabelStore(context)
        val owner = "synthetic-workout-label-owner"
        val activityId = "synthetic-workout-label-activity"
        val label = UserActivityWorkoutLabel(
            ActivityWorkoutKind.QUALITY_RUN, ActivityQualitySubtype.INTERVALS
        )
        store.clear(owner, activityId)

        assertTrue(store.save(owner, activityId, label))
        assertEquals(label, UserActivityWorkoutLabelStore(context).load(owner, activityId))
        assertNull(store.load("different-owner", activityId))
        assertTrue(store.clear(owner, activityId))
        assertNull(store.load(owner, activityId))
    }
}
