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

    @Test fun shortLivedButUsableTokenIsReusedAcrossHistoryPages() {
        val store=Store()
        val expiry=now.plusSeconds(15*60)
        store.save(GarminCredentialRecord("owner","key","stored",expiry))
        var exchanges=0
        var activityCalls=0
        val api=object:GarminApi {
            override fun exchange(apiKey:String):GarminBearerToken {
                exchanges++
                return GarminBearerToken("fresh",now.plusSeconds(3600))
            }
            override fun activities(token:GarminBearerToken,start:Int,limit:Int):List<GarminActivity> {
                assertEquals("stored",token.value)
                activityCalls++
                return emptyList()
            }
        }
        val source=GarminPersonalSource("owner",store,api){now}
        source.activities("owner",null,100)
        source.activities("owner","100",100)
        assertEquals(0,exchanges)
        assertEquals(2,activityCalls)
        assertEquals("stored",store.records.getValue("owner").bearerToken)
    }

    @Test fun revokedStoredTokenGetsOneFreshExchangeAndRetry() {
        val store=Store()
        store.save(GarminCredentialRecord("owner","key","revoked",now.plusSeconds(7200)))
        var exchanges=0
        var activityCalls=0
        val api=object:GarminApi {
            override fun exchange(apiKey:String):GarminBearerToken {
                exchanges++
                return GarminBearerToken("fresh",now.plusSeconds(7200))
            }
            override fun activities(token:GarminBearerToken,start:Int,limit:Int):List<GarminActivity> {
                activityCalls++
                if(token.value=="revoked")throw GarminAuthenticationException("Rejected old bearer")
                assertEquals("fresh",token.value)
                return listOf(GarminActivity("source-1","Run","running",now,1200.0,3000.0,null,null))
            }
        }
        val source=GarminPersonalSource("owner",store,api){now}
        assertEquals(1,source.activities("owner",null,100).records.size)
        assertEquals(1,exchanges)
        assertEquals(2,activityCalls)
        assertEquals("fresh",store.records.getValue("owner").bearerToken)
    }

    @Test fun newlyExchangedTokenRejectionDoesNotCauseRetryLoop() {
        val store=Store()
        store.save(GarminCredentialRecord("owner","key"))
        var exchanges=0
        var calls=0
        val api=object:GarminApi {
            override fun exchange(apiKey:String):GarminBearerToken {
                exchanges++
                return GarminBearerToken("fresh",now.plusSeconds(7200))
            }
            override fun activities(token:GarminBearerToken,start:Int,limit:Int):List<GarminActivity> {
                calls++
                throw GarminAuthenticationException("Rejected new bearer")
            }
        }
        val source=GarminPersonalSource("owner",store,api){now}
        assertFails<GarminAuthenticationException> { source.activities("owner",null,100) }
        assertEquals(1,exchanges)
        assertEquals(1,calls)
    }

    @Test fun legacyUuidLooksUpItsNumericIdBeforeOwnerFitDownload() {
        val store=Store()
        store.save(GarminCredentialRecord("owner","key","token",now.plusSeconds(7200)))
        var requests=0
        var downloadedId=0L
        val api=object:GarminApi {
            override fun exchange(apiKey:String)=
                GarminBearerToken("replacement",now.plusSeconds(7200))
            override fun activities(token:GarminBearerToken,start:Int,limit:Int):List<GarminActivity> {
                requests++
                assertEquals(0,start)
                return listOf(GarminActivity("uuid-source","Batis Running","running",
                    now,1800.0,5000.0,null,null,numericActivityId=123456789L))
            }
            override fun downloadActivity(token:GarminBearerToken,numericId:Long):ByteArray {
                downloadedId=numericId
                return byteArrayOf(1,2,3)
            }
        }
        val source=GarminPersonalSource("owner",store,api){now}
        assertArrayEquals(byteArrayOf(1,2,3),
            source.downloadFitForSource("owner","uuid-source"))
        assertEquals(1,requests)
        assertEquals(123456789L,downloadedId)
        assertFails<IllegalArgumentException> {
            source.downloadFitForSource("not-owner","uuid-source")
        }
    }

    @Test fun sourceWithPersistedNumericIdSkipsListSearch() {
        val store=Store()
        store.save(GarminCredentialRecord("owner","key","token",now.plusSeconds(7200)))
        val api=object:GarminApi {
            override fun exchange(apiKey:String)=GarminBearerToken("fresh",now.plusSeconds(7200))
            override fun activities(token:GarminBearerToken,start:Int,limit:Int):List<GarminActivity> =
                throw AssertionError("Numeric ID already resolved")
            override fun downloadActivity(token:GarminBearerToken,numericId:Long):ByteArray {
                assertEquals(789L,numericId)
                return byteArrayOf(4)
            }
        }
        assertArrayEquals(byteArrayOf(4),
            GarminPersonalSource("owner",store,api){now}
                .downloadFitForSource("owner","uuid-source",789L))
    }

    @Test fun legacySummaryWithoutExternalIdRecoversOnlyExactOwnerGarminMatch() {
        val store=Store()
        store.save(GarminCredentialRecord("owner","key","token",now.plusSeconds(7200)))
        val api=Api()
        api.records=listOf(
            GarminActivity("source-111","Wrong Run","running",now.minusSeconds(3600),
                1800.0,5000.0,null,null,numericActivityId=111L),
            GarminActivity("source-222","Batis Running","running",now,
                1800.0,5000.0,null,null,numericActivityId=222L)
        )
        val legacy=ActivityRecord("old-garmin-row","Batis Running","running","garmin",
            now,5000.0,1800.0,null,null,null,null)
        val source=GarminPersonalSource("owner",store,api){now}
        val identified=source.resolveLegacySource("owner",legacy)
        assertEquals("source-222",identified.externalId)
        assertEquals(222L,identified.numericActivityId)
        assertFails<IllegalArgumentException> {
            source.resolveLegacySource("another-owner",legacy)
        }
    }

    @Test fun legacyRecoveryRefusesAmbiguousAndUnverifiableGarminRecords() {
        val store=Store()
        store.save(GarminCredentialRecord("owner","key","token",now.plusSeconds(7200)))
        val api=Api()
        val legacy=ActivityRecord("old-garmin-row","Batis Running","running","garmin",
            now,5000.0,1800.0,null,null,null,null)
        api.records=listOf(
            GarminActivity("source-a","Batis Running","running",now,
                1800.0,5000.0,null,null,numericActivityId=111L),
            GarminActivity("source-b","Batis Running","running",now,
                1800.0,5000.0,null,null,numericActivityId=222L)
        )
        val source=GarminPersonalSource("owner",store,api){now}
        assertFails<GarminProtocolException> {
            source.resolveLegacySource("owner",legacy)
        }
        assertFails<GarminProtocolException> {
            source.resolveLegacySource("owner",legacy.copy(distanceM=null))
        }
        api.records=listOf(
            GarminActivity("wrong-time","Batis Running","running",now.minusSeconds(1),
                1800.0,5000.0,null,null,numericActivityId=333L)
        )
        assertFails<GarminProtocolException> {
            source.resolveLegacySource("owner",legacy)
        }
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
        val malformedToken=GarminConnectionStatus.fromError(GarminTokenException())
        assertEquals(GarminIssueKind.SERVICE,malformedToken.issueKind)
        assertFalse(malformedToken.heading().contains("activity",ignoreCase=true))
    }

    @Test fun heartRateZonesAreReadWithOwnerScopedBearerAndNoCrossOwnerAccess() {
        val store=Store()
        store.save(GarminCredentialRecord("owner-a","key-a","bearer",now.plusSeconds(7200)))
        val api=object:GarminApi {
            override fun exchange(apiKey:String):GarminBearerToken =
                throw AssertionError("Fresh bearer should not be required")
            override fun activities(token:GarminBearerToken,start:Int,limit:Int) =
                emptyList<GarminActivity>()
            override fun heartRateZones(token:GarminBearerToken):List<GarminHeartRateZoneProfile> {
                assertEquals("bearer",token.value)
                return listOf(GarminHeartRateZoneProfile("RUNNING",
                    listOf(110,130,145,165,180),"LTHR"))
            }
        }
        val source=GarminPersonalSource("owner-a",store,api){now}
        assertEquals(130,source.heartRateZones("owner-a").single().floorsBpm[1])
        assertFails<IllegalArgumentException> { source.heartRateZones("owner-b") }
    }

    private inline fun <reified T:Throwable> assertFails(block:()->Unit) {
        try { block() } catch(error:Throwable) { if(error is T)return;throw error }
        fail("Expected ${T::class.java.simpleName}")
    }
}
