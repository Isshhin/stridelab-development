package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class GarminPersonalClientWellnessTest {

    @Test
    fun sleepSummaries_fallsBackToDailySleepWhenRangeEndpointReturns404() {
        val requests = mutableListOf<GarminHttpRequest>()
        val transport = GarminHttpTransport { req ->
            requests += req
            when {
                req.url.contains("/sleep-service/sleep/dailySleepSummariesByDate") ->
                    GarminHttpResponse(404, "")
                req.url.contains("/sleep-service/sleep/dailySleep?date=2026-11-27") ->
                    GarminHttpResponse(
                        200,
                        """{"dailySleepDTO":{"calendarDate":"2026-11-27","sleepTimeSeconds":28800}}"""
                    )
                else -> GarminHttpResponse(404, "")
            }
        }
        val client = GarminPersonalClient(transport = transport, clock = { Instant.parse("2026-11-27T10:00:00Z") })
        val token = GarminBearerToken("bearer-test", Instant.parse("2026-11-27T12:00:00Z"))
        val summaries = client.sleepSummaries(token, LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 27))

        assertEquals(1, summaries.size)
        assertEquals(8.0, summaries.single().sleepDurationHours!!, 0.01)
        assertTrue(requests.any { it.url.contains("dailySleepSummariesByDate") })
        assertTrue(requests.any { it.url.contains("dailySleep?date=2026-11-27") })
    }

    @Test
    fun fetchWellnessRecords_isNonFatalOn403And429() {
        val transport = GarminHttpTransport { req ->
            when {
                req.url.contains("/hrv-service/hrv/daily/") ->
                    GarminHttpResponse(
                        200,
                        """{"hrvSummaries":[{"calendarDate":"2026-11-27","lastNightAvg":65.0,"status":"BALANCED"}]}"""
                    )
                req.url.contains("/sleep-service/") -> GarminHttpResponse(403, "")
                req.url.contains("/wellness-service/wellness/dailyHeartRate") -> GarminHttpResponse(429, "")
                else -> GarminHttpResponse(404, "")
            }
        }
        val client = GarminPersonalClient(transport = transport, clock = { Instant.parse("2026-11-27T10:00:00Z") })
        val store = object : GarminCredentialStore {
            private var rec: GarminCredentialRecord? = GarminCredentialRecord(
                "local-garmin",
                "api-key",
                "valid-bearer",
                Instant.parse("2026-11-27T12:00:00Z")
            )
            override fun read(accountId: String) = rec?.takeIf { it.accountId == accountId }
            override fun save(record: GarminCredentialRecord) { rec = record }
            override fun clear(accountId: String) { rec = null }
        }
        val source = GarminPersonalSource(
            accountId = "local-garmin",
            credentials = store,
            api = client,
            clock = { Instant.parse("2026-11-27T10:00:00Z") }
        )

        val records = source.fetchWellnessRecords(
            accountId = "local-garmin",
            startDate = LocalDate.of(2026, 11, 25),
            endDate = LocalDate.of(2026, 11, 27),
            zone = ZoneOffset.UTC
        )
        assertEquals(1, records.size)
        assertEquals("hrv", records.single().type)
        assertEquals(65.0, records.single().value, 0.01)
    }
}
