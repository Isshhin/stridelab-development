package io.stridelab.privatealpha

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Synthetic owner-only data. No live Garmin account or internet required. */
@RunWith(AndroidJUnit4::class)
class GarminZonePreferenceStoreTest {
    @Test fun importedProfilesAndSourceSelectionPersistOfflineWithoutChangingManualSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manual = AthleteHeartRatePreferences(context)
        val store = GarminZonePreferenceStore(context)
        val owner = "garmin-zones-qa-" + UUID.randomUUID()
        val other = "garmin-zones-other-" + UUID.randomUUID()
        manual.save(owner, AthleteHeartRateZoneSettings(
            maximumHr = 197, method = HeartRateZoneMethod.MAX_BPM))
        try {
            assertEquals(DisplayHeartRateZoneSource.MANUAL, store.source(owner))
            assertNull(store.load(owner))
            val profile = GarminHeartRateZoneProfile("RUNNING",
                listOf(105, 132, 145, 168, 182), "LTHR", 197)
            assertTrue(store.save(owner, profile, 1_800_000_000_000L))
            assertEquals(profile, GarminZonePreferenceStore(context).load(owner)?.profile)
            assertEquals(1_800_000_000_000L, store.load(owner)?.syncedAtMillis)
            assertNull(store.load(other))

            assertTrue(store.select(owner, DisplayHeartRateZoneSource.GARMIN))
            assertEquals(DisplayHeartRateZoneSource.GARMIN,
                GarminZonePreferenceStore(context).source(owner))
            assertEquals(DisplayHeartRateZoneSource.MANUAL, store.source(other))
            assertEquals(197, manual.settings(owner).maximumHr)

            assertTrue(store.clearCache(owner))
            assertNull(store.load(owner))
            // Losing the Garmin cache does not modify the manual maximum.
            assertEquals(197, manual.settings(owner).maximumHr)
            assertTrue(store.select(owner, DisplayHeartRateZoneSource.MANUAL))
        } finally {
            manual.clear(owner)
            store.select(owner, DisplayHeartRateZoneSource.MANUAL)
            store.clearCache(owner)
        }
    }
}
