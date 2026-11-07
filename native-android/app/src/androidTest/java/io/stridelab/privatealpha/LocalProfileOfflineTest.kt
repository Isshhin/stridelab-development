package io.stridelab.privatealpha

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** All fixtures are device-local; no external network or athlete records. */
@RunWith(AndroidJUnit4::class)
class LocalProfileOfflineTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "local-profile-no-login-test.db"
    private val prefs = "local-profile-no-login-test"
    private val cloudOwnerA = "synthetic-cloud-owner-a"
    private val cloudOwnerB = "synthetic-cloud-owner-b"
    private val gateway = FakeCloudGateway()
    private lateinit var repository: TrainingRepository

    private class FakeCloudGateway : TrainingGateway {
        var owner = "synthetic-cloud-owner-a"
        var signInCalls = 0
        var historyCalls = 0
        override fun signIn(email: String, password: String): NativeSession {
            signInCalls++
            return NativeSession(owner, "fixture-token", "fixture-refresh", Long.MAX_VALUE)
        }
        override fun refresh(session: NativeSession): NativeSession = session
        override fun verify(session: NativeSession) { check(session.account == owner) }
        override fun activities(session: NativeSession, before: ActivityRecord?): JSONArray {
            historyCalls++
            return JSONArray()
        }
        override fun signOut(session: NativeSession) {}
    }

    private fun open() = runBlocking {
        repository = TrainingRepository(context, gateway, dbName, prefs)
        withTimeout(10_000L) { repository.state.first { !it.initializing } }
    }

    @Before fun setup() {
        context.deleteDatabase(dbName)
        context.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit().clear().commit()
        SessionVault(context).clear(cloudOwnerA)
        SessionVault(context).clear(cloudOwnerB)
        open()
    }

    @After fun cleanup() {
        repository.close()
        context.deleteDatabase(dbName)
        context.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit().clear().commit()
        SessionVault(context).clear(cloudOwnerA)
        SessionVault(context).clear(cloudOwnerB)
    }

    @Test fun firstLaunchHasLocalOwnerAndCanSaveOfflineWithoutCloud() = runBlocking {
        val local = repository.state.value.account
        assertNotNull(local)
        assertTrue(local!!.startsWith("local-"))
        assertNull(repository.state.value.cloudAccount)
        assertFalse(repository.state.value.needsSignIn)
        assertTrue(repository.changeShelf("running-basics"))
        assertTrue("running-basics" in repository.state.value.shelf.favorites)
        assertEquals(0, gateway.signInCalls)
        assertEquals(0, gateway.historyCalls)

        repository.close()
        open()
        withTimeout(10_000L) {
            repository.state.first { !it.initializing && "running-basics" in it.shelf.favorites }
        }
        assertEquals(local, repository.state.value.account)
        assertNull(repository.state.value.cloudAccount)
    }

    @Test fun cloudSignInAndSignOutKeepTheSameOfflineData() = runBlocking {
        val local = requireNotNull(repository.state.value.account)
        assertTrue(repository.changeShelf("saved-workout"))
        assertTrue(repository.signIn("fixture@example.invalid", "fixture"))
        assertEquals(local, repository.state.value.account)
        assertEquals(cloudOwnerA, repository.state.value.cloudAccount)
        assertEquals(0, gateway.historyCalls)
        assertTrue("saved-workout" in repository.state.value.shelf.favorites)

        assertTrue(repository.signOut())
        assertNull(repository.state.value.cloudAccount)
        assertEquals(local, repository.state.value.account)
        assertTrue("saved-workout" in repository.state.value.shelf.favorites)

        repository.close()
        open()
        withTimeout(10_000L) {
            repository.state.first { !it.initializing && "saved-workout" in it.shelf.favorites }
        }
        assertEquals(local, repository.state.value.account)
        assertNull(repository.state.value.cloudAccount)
    }

    @Test fun cloudOwnerMappingRequiresExactSourceOwner() {
        val local = requireNotNull(repository.state.value.account)
        val original = org.json.JSONObject()
            .put("user_id", local)
            .put("activity_id", "synthetic-run")
            .put("samples", org.json.JSONArray())
            .toString()
        val remote = remapCloudSnapshotOwner(original, local, cloudOwnerA)
        assertEquals(cloudOwnerA, org.json.JSONObject(remote).getString("user_id"))
        val roundTrip = remapCloudSnapshotOwner(remote, cloudOwnerA, local)
        assertEquals(local, org.json.JSONObject(roundTrip).getString("user_id"))
        assertEquals("synthetic-run", org.json.JSONObject(roundTrip).getString("activity_id"))
        try {
            remapCloudSnapshotOwner(remote, cloudOwnerB, local)
            fail("Mismatched cloud owners must not be imported")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun differentCloudAccountCannotInheritLocalTrainingWithoutConsent() = runBlocking {
        val local = requireNotNull(repository.state.value.account)
        assertTrue(repository.signIn("fixture@example.invalid", "fixture"))
        assertTrue(repository.signOut())
        gateway.owner = cloudOwnerB
        assertFalse(repository.signIn("other@example.invalid", "fixture"))
        assertEquals(local, repository.state.value.account)
        assertNull(repository.state.value.cloudAccount)
        assertEquals(0, gateway.historyCalls)
    }
}
