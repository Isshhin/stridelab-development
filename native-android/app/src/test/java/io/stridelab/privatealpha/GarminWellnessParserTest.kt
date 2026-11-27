package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class GarminWellnessParserTest {

    @Test
    fun parseHrvRange_parsesSummariesAndRejectsOutOfRange() {
        val json = """
            {
              "hrvSummaries": [
                {
                  "calendarDate": "2026-11-26",
                  "lastNightAvg": 64.0,
                  "weeklyAvg": 61.0,
                  "status": "BALANCED",
                  "baseline": { "balancedLow": 54.0, "balancedUpper": 72.0 }
                },
                {
                  "calendarDate": "2026-11-25",
                  "lastNightAvg": 2.0,
                  "status": "LOW"
                }
              ]
            }
        """.trimIndent()

        val parsed = GarminWellnessParser.parseHrvRange(json)
        assertEquals(1, parsed.size)
        val item = parsed.single()
        assertEquals(LocalDate.of(2026, 11, 26), item.date)
        assertEquals(64.0, item.lastNightAvgMs!!, 0.01)
        assertEquals(61.0, item.weeklyAvgMs!!, 0.01)
        assertEquals("BALANCED", item.status)
        assertEquals(54.0, item.baselineLowMs!!, 0.01)
        assertEquals(72.0, item.baselineHighMs!!, 0.01)
    }

    @Test
    fun parseSleepRangeAndSingleDay_extractsDurationStagesAndScore() {
        val singleDay = """
            {
              "dailySleepDTO": {
                "calendarDate": "2026-11-27",
                "sleepTimeSeconds": 27720,
                "deepSleepSeconds": 5400,
                "lightSleepSeconds": 15300,
                "remSleepSeconds": 7020,
                "awakeSleepSeconds": 900,
                "sleepScores": {
                  "overall": { "value": 84, "qualifierKey": "GOOD" }
                }
              }
            }
        """.trimIndent()

        val sleep = GarminWellnessParser.parseSleepSingleDay(singleDay)
        assertNotNull(sleep)
        assertEquals(LocalDate.of(2026, 11, 27), sleep!!.date)
        assertEquals(7.7, sleep.sleepDurationHours!!, 0.01)
        assertEquals(5400, sleep.deepSeconds)
        assertEquals(15300, sleep.lightSeconds)
        assertEquals(7020, sleep.remSeconds)
        assertEquals(900, sleep.awakeSeconds)
        assertEquals(84, sleep.sleepScore)
        assertEquals("GOOD", sleep.sleepQualifier)
    }

    @Test
    fun parseDailyHeartRate_andWellnessRecordRoundTrip_supportsGarminSource() {
        val hrJson = """
            {
              "calendarDate": "2026-11-27",
              "restingHeartRate": 48,
              "minHeartRate": 44,
              "maxHeartRate": 152
            }
        """.trimIndent()
        val hr = GarminWellnessParser.parseDailyHeartRate(hrJson)
        assertNotNull(hr)
        assertEquals(48.0, hr!!.restingHeartRateBpm!!, 0.01)

        val bundle = GarminWellnessBundle(
            dailyHr = listOf(hr),
            hrv = listOf(GarminHrvSummary(LocalDate.of(2026, 11, 27), 62.0, 60.0, "BALANCED", 52.0, 70.0)),
            sleep = listOf(GarminSleepSummary(LocalDate.of(2026, 11, 27), 7.5, 5000, 15000, 7000, 600, 82, "GOOD"))
        )
        val records = GarminWellnessParser.toWellnessRecords(bundle, ZoneOffset.UTC)
        assertEquals(3, records.size)
        records.forEach { rec ->
            val encoded = rec.encode("local-garmin-1")
            val decoded = WellnessRecord.parse(encoded, "local-garmin-1")
            assertNotNull(decoded)
            assertEquals("garmin", decoded!!.source)
            assertEquals(rec.value, decoded.value, 0.001)
            assertNotNull(decoded.metadata)
        }

        val wearableInputs = GarminWellnessParser.toWearableInputs("local-garmin-1", bundle, ZoneOffset.UTC)
        assertEquals(3, wearableInputs.size)
        wearableInputs.forEach { input ->
            assertNotNull(WearableWellnessNormalizer.normalize(input))
        }
    }
}
