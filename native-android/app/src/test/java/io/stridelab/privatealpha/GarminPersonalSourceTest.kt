package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class GarminPersonalSourceTest {
    private val now=Instant.parse("2026-11-05T00:00:00Z")
    private class Store:GarminCredentialStore {
        val records=mutableMapOf<String,GarminCredentialRecord>()
        override fun save(record:GarminCredentialRecord) { records[record.accountId]=record }
        override fun read(accountId:String)=records[accountId]
        override fun clear(accountId:String) { records.remove(accountId) }
    }
    private class Api:GarminApi {
        var exchanges=0
        var failure:GarminException?=null
        var records=listOf<GarminActivity>()
        override fun exchange(apiKey:String):GarminBearerToken {
            failure?.let { throw it };exchanges++
            return GarminBearerToken("token-$exchanges",Instant.parse("2026-11-06T00:00:00Z"))
        }
        override fun activities(token:GarminBearerToken,start:Int,limit:Int)=records.drop(start).take(limit)
    }

    @Test fun connectSyncRefreshDisconnectAndAccountIsolationAreExplicit() {
        val store=Store();val api=Api()
        val source=GarminPersonalSource("owner-a",store,api){now}
        assertEquals("not_connected",source.status().state)
        assertEquals("connected",source.connect("key-a").state)
        assertFalse(store.records.getValue("owner-a").toString().contains("key-a"))
        api.records=listOf(GarminActivity("activity-1","Run","running",now,1800.0,5000.0,null,null))
        val page=source.activities("owner-a",null,20)
        assertEquals("garmin",page.records.single().provider)
        assertEquals("activity-1",page.records.single().externalId)
        assertEquals(1,api.exchanges)
        assertFails<IllegalArgumentException> { source.activities("owner-b",null,20) }
        source.disconnect()
        assertEquals("not_connected",source.status().state)
    }

    @Test fun expiredTokenReexchangesAndOfflineFailurePreservesStoredKeyWithoutFakeSuccess() {
        val store=Store();val api=Api()
        store.save(GarminCredentialRecord("owner","key", "expired",now.minusSeconds(1)))
        val source=GarminPersonalSource("owner",store,api){now}
        source.activities("owner",null,20)
        assertEquals(1,api.exchanges)
        api.failure=GarminNetworkException("Garmin could not be reached",java.io.IOException("offline"))
        store.save(GarminCredentialRecord("owner","key"))
        val status=source.connect("key")
        assertEquals("error",status.state)
        assertEquals("key",store.records.getValue("owner").apiKey)
        assertNull(store.records.getValue("owner").bearerToken)
    }

    @Test fun dataFailureIsNeverMisclassifiedAsAuthenticationExpiry() {
        val data=GarminConnectionStatus.fromError(
            GarminProtocolException("Garmin activity has an invalid timestamp"))
        assertEquals(GarminIssueKind.ACTIVITY_DATA,data.issueKind)
        assertEquals("Garmin activity data needs attention",data.heading())
        assertEquals("Garmin activity has an invalid timestamp",data.error)
        assertFalse(data.heading().contains("Authentication"))

        val auth=GarminConnectionStatus.fromError(
            GarminAuthenticationException("Garmin authentication was rejected"))
        assertEquals(GarminIssueKind.AUTHENTICATION,auth.issueKind)
        assertTrue(auth.heading().contains("authentication"))
        val network=GarminConnectionStatus.fromError(
            GarminNetworkException("offline",java.io.IOException("secret details")))
        assertEquals(GarminIssueKind.NETWORK,network.issueKind)
        assertFalse(network.error.orEmpty().contains("secret details"))
        val rate=GarminConnectionStatus.fromError(GarminRateLimitException())
        assertEquals(GarminIssueKind.RATE_LIMIT,rate.issueKind)
        val service=GarminConnectionStatus.fromError(GarminServiceException(503))
        assertEquals(GarminIssueKind.SERVICE,service.issueKind)
        assertFalse(service.heading().contains("authentication"))
    }

    private inline fun <reified T:Throwable> assertFails(block:()->Unit) {
        try { block() } catch(error:Throwable) { if(error is T)return;throw error }
        fail("Expected ${T::class.java.simpleName}")
    }
}
