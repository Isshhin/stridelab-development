package io.stridelab.privatealpha

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** No Garmin login or network; verifies opt-in and durable throttle contracts. */
@RunWith(AndroidJUnit4::class)
class GarminAutomaticSyncPreferencesTest {
    @Test fun automaticSyncAndCloudConsentAreAccountScoped() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = GarminSyncPreferences(context)
        val ownerA = "test-" + UUID.randomUUID()
        val ownerB = "test-" + UUID.randomUUID()
        assertTrue(prefs.automatic(ownerA))
        assertFalse(prefs.cloudBackup(ownerA))
        prefs.setAutomatic(ownerA, false)
        prefs.setCloudBackup(ownerA, true)
        assertFalse(prefs.automatic(ownerA))
        assertTrue(prefs.cloudBackup(ownerA))
        assertTrue(prefs.automatic(ownerB))
        assertFalse(prefs.cloudBackup(ownerB))
        prefs.setCloudBackup(ownerA, false)
    }

    @Test fun dueClaimsAndUploadReceiptsAreIdempotent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = GarminSyncPreferences(context)
        val owner = "test-" + UUID.randomUUID()
        assertTrue(prefs.claim(owner, "garmin", 1800000L, 10000000L))
        assertFalse(prefs.claim(owner, "garmin", 1800000L, 10000001L))
        assertTrue(prefs.claim(owner, "garmin", 1800000L, 11800001L))
        assertTrue(prefs.claim(owner, "cloud", 1800000L, 10000001L))
        val id = "garmin-" + "a".repeat(64)
        val payload = "{\"id\":\"record-1\"}"
        assertTrue(prefs.needsCloudUpload(owner, "activity", id, payload))
        prefs.acknowledgeCloudUpload(owner, "activity", id, payload)
        assertFalse(prefs.needsCloudUpload(owner, "activity", id, payload))
        assertTrue(prefs.needsCloudUpload(owner, "activity", id, payload + " "))
    }
}
