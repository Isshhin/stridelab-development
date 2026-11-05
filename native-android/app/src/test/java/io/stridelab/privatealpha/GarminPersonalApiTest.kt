package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

class GarminPersonalApiTest {
    private val now=Instant.parse("2026-11-05T00:00:00Z")
    private fun jwt(exp:Long):String {
        fun part(value:String)=Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
        return part("{\"alg\":\"none\"}")+"."+part("{\"exp\":$exp}")+".signature"
    }
    private class FakeTransport(var response:GarminHttpResponse):GarminHttpTransport {
        lateinit var request:GarminHttpRequest
        override fun execute(request:GarminHttpRequest):GarminHttpResponse {
            this.request=request
            return response
        }
    }

    @Test fun tokenExchangeUsesOnlyTheOfficialPersonalApiContract() {
        val token=jwt(now.epochSecond+86_400)
        val http=FakeTransport(GarminHttpResponse(200,token))
        val result=GarminPersonalClient(http){now}.exchange("personal-key")
        assertEquals(token,result.value)
        assertEquals(Instant.ofEpochSecond(now.epochSecond+86_400),result.expiresAt)
        assertEquals("POST",http.request.method)
        assertEquals("https://personal.connectapi.garmin.com/personalapitoken-service/personal-api/personal-api-key/token",http.request.url)
        assertEquals("ApiKey personal-key",http.request.headers["Authorization"])
        assertFalse(http.request.toString().contains("personal-key"))
    }

    @Test fun missingInvalidExpiredMalformedAndRateLimitedTokensFailClosed() {
        val client=GarminPersonalClient(FakeTransport(GarminHttpResponse(200,"unused"))){now}
        assertFails<IllegalArgumentException> { client.exchange(" ") }
        assertFails<GarminAuthenticationException> {
            GarminPersonalClient(FakeTransport(GarminHttpResponse(401,"invalid"))){now}.exchange("bad")
        }
        assertFails<GarminRateLimitException> {
            GarminPersonalClient(FakeTransport(GarminHttpResponse(429,"slow"))){now}.exchange("key")
        }
        assertFails<GarminTokenException> {
            GarminPersonalClient(FakeTransport(GarminHttpResponse(200,"not-a-jwt"))){now}.exchange("key")
        }
        assertFails<GarminAuthenticationException> {
            GarminPersonalClient(FakeTransport(GarminHttpResponse(200,jwt(now.epochSecond-1)))){now}.exchange("key")
        }
    }

    @Test fun officialActivitySearchNormalizesProvenanceAndKeepsUpstreamId() {
        val body="""[{"activityId":123,"activityUUID":{"uuid":"upstream-uuid"},"activityName":"Morning Run","startTimeGMT":"2026-11-04T22:30:00.0","activityType":{"typeKey":"running"},"distance":5012.5,"duration":1800.0,"movingDuration":1750.0,"averageHR":147.0,"averageSpeed":2.8}]"""
        val http=FakeTransport(GarminHttpResponse(200,body))
        val client=GarminPersonalClient(http){now}
        val activities=client.activities(GarminBearerToken("token",now.plusSeconds(100)),0,20)
        assertEquals(1,activities.size)
        val normalized=GarminActivityNormalizer.normalize("athlete-a",activities.single())
        assertEquals("garmin",normalized.provider)
        assertEquals("upstream-uuid",normalized.externalId)
        assertEquals("running",normalized.activityType)
        assertEquals(Instant.parse("2026-11-04T22:30:00Z"),normalized.startedAt)
        assertEquals(5012.5,normalized.distanceMeters!!,0.0)
        assertEquals(2.8,normalized.averageSpeedMetersPerSecond!!,0.0)
        assertEquals(123L,normalized.numericActivityId)
        assertEquals("Bearer token",http.request.headers["Authorization"])
        assertTrue(http.request.url.contains("start=0&limit=20"))
    }

    @Test fun acceptsSpaceSeparatedGmtWithFractionAndExplicitOffsetWithoutLocalTimeShift() {
        val token=GarminBearerToken("test-token",now.plusSeconds(600))
        val examples=listOf(
            "2026-11-04 22:30:00" to "2026-11-04T22:30:00Z",
            "2026-11-04 22:30:00.000" to "2026-11-04T22:30:00Z",
            "2026-11-04T22:30:00.0" to "2026-11-04T22:30:00Z",
            "2026-11-05T06:30:00+08:00" to "2026-11-04T22:30:00Z"
        )
        examples.forEach { (raw,expected) ->
            val body=org.json.JSONArray().put(org.json.JSONObject()
                .put("activityId","source-123")
                .put("activityName","Realistic timestamp fixture")
                .put("activityType","running")
                .put("startTimeGMT",raw)).toString()
            val actual=GarminPersonalClient(FakeTransport(GarminHttpResponse(200,body))){now}
                .activities(token,0,20).single()
            assertEquals("Wrong conversion for $raw",Instant.parse(expected),actual.startedAt)
        }
    }

    @Test fun localOnlyTimeRequiresAnExplicitOffsetToAvoidWrongDay() {
        val token=GarminBearerToken("test-token",now.plusSeconds(600))
        fun body(timestamp:String)=org.json.JSONArray().put(org.json.JSONObject()
            .put("activityId","source-123").put("startTimeLocal",timestamp)).toString()
        val valid=GarminPersonalClient(
            FakeTransport(GarminHttpResponse(200,body("2026-11-05 06:30:00+08:00")))){now}
            .activities(token,0,20).single()
        assertEquals(Instant.parse("2026-11-04T22:30:00Z"),valid.startedAt)
        val invalid=assertFails<GarminProtocolException> {
            GarminPersonalClient(
                FakeTransport(GarminHttpResponse(200,body("2026-11-05 06:30:00")))){now}
                .activities(token,0,20)
        }
        assertTrue(invalid.message.orEmpty().contains("UTC timestamp"))
    }

    @Test fun trulyInvalidOrMissingActivityTimestampStillFailsClosed() {
        val token=GarminBearerToken("test-token",now.plusSeconds(600))
        listOf("", "not-a-date", "2026-99-99 99:99:99").forEach { raw ->
            val body=org.json.JSONArray().put(org.json.JSONObject()
                .put("activityId","source-123").put("startTimeGMT",raw)).toString()
            val error=assertFails<GarminProtocolException> {
                GarminPersonalClient(FakeTransport(GarminHttpResponse(200,body))){now}
                    .activities(token,0,20)
            }
            assertTrue(error.message.orEmpty().contains("timestamp"))
        }
    }

    @Test fun ownerFitDownloadUsesNumericIdAndDoesNotExposeBearerInRequestLogs() {
        val expected=byteArrayOf('P'.code.toByte(),'K'.code.toByte(),3,4,1,2)
        var seen:GarminHttpRequest?=null
        val client=GarminPersonalClient(object:GarminHttpTransport {
            override fun execute(request:GarminHttpRequest):GarminHttpResponse =
                throw AssertionError("Only the binary endpoint is expected")
            override fun executeBinary(request:GarminHttpRequest):GarminBinaryResponse {
                seen=request
                return GarminBinaryResponse(200,expected)
            }
        }) { now }
        assertArrayEquals(expected,client.downloadActivity(
            GarminBearerToken("test-secret",now.plusSeconds(600)),123456789L))
        assertEquals("GET",seen!!.method)
        assertEquals("https://personal.connectapi.garmin.com/download-service/files/activity/123456789",
            seen!!.url)
        assertEquals("Bearer test-secret",seen!!.headers["Authorization"])
        assertFalse(seen.toString().contains("test-secret"))
        assertFails<IllegalArgumentException> {
            client.downloadActivity(GarminBearerToken("token",now.plusSeconds(600)),0L)
        }
    }

    @Test fun emptyListDuplicateAndCrossAccountClassificationStayExplicit() {
        val empty=GarminPersonalClient(FakeTransport(GarminHttpResponse(200,"[]"))){now}
            .activities(GarminBearerToken("token",now.plusSeconds(100)),0,20)
        assertTrue(empty.isEmpty())
        val activity=GarminActivity("42","Run","running",now,1200.0,5000.0,null,null)
        val a=GarminActivityNormalizer.candidate("owner-a",activity)
        assertEquals(ImportedActivityDisposition.ALREADY_SEEN_SOURCE,
            ActivityImportMatcher.inspect(a,listOf(a)))
        val other=GarminActivityNormalizer.candidate("owner-b",activity)
        assertEquals(ImportedActivityDisposition.NEW_SOURCE,
            ActivityImportMatcher.inspect(other,listOf(a)))
    }

    @Test fun networkFailureDoesNotExposeAuthorizationHeader() {
        val client=GarminPersonalClient(object:GarminHttpTransport {
            override fun execute(request:GarminHttpRequest):GarminHttpResponse {
                throw java.io.IOException("offline")
            }
        }) { now }
        val error=assertFails<GarminNetworkException> { client.exchange("do-not-print") }
        assertFalse(error.message.orEmpty().contains("do-not-print"))
    }

    private inline fun <reified T:Throwable> assertFails(block:()->Unit):T {
        try { block() } catch(error:Throwable) {
            if(error is T)return error
            throw AssertionError("Expected ${T::class.java.simpleName}, got ${error::class.java.simpleName}",error)
        }
        throw AssertionError("Expected ${T::class.java.simpleName}")
    }
}
