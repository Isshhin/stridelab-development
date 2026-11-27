package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

internal data class GarminHrvSummary(
    val date: LocalDate,
    val lastNightAvgMs: Double?,
    val weeklyAvgMs: Double?,
    val status: String?,
    val baselineLowMs: Double?,
    val baselineHighMs: Double?
)

internal data class GarminSleepSummary(
    val date: LocalDate,
    val sleepDurationHours: Double?,
    val deepSeconds: Int?,
    val lightSeconds: Int?,
    val remSeconds: Int?,
    val awakeSeconds: Int?,
    val sleepScore: Int?,
    val sleepQualifier: String?
)

internal data class GarminDailyHrSummary(
    val date: LocalDate,
    val restingHeartRateBpm: Double?,
    val minHeartRateBpm: Double?,
    val maxHeartRateBpm: Double?
)

internal data class GarminWellnessBundle(
    val hrv: List<GarminHrvSummary> = emptyList(),
    val sleep: List<GarminSleepSummary> = emptyList(),
    val dailyHr: List<GarminDailyHrSummary> = emptyList()
)

/**
 * Defensive, pure JSON parsers and normalizers for Garmin Connect Personal API
 * wellness endpoints (HRV, Sleep, and Daily Resting Heart Rate).
 */
internal object GarminWellnessParser {

    fun parseHrvRange(body: String): List<GarminHrvSummary> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()
        val array = when {
            trimmed.startsWith("[") -> runCatching { JSONArray(trimmed) }.getOrNull()
            trimmed.startsWith("{") -> {
                val root = runCatching { JSONObject(trimmed) }.getOrNull() ?: return emptyList()
                root.optJSONArray("hrvSummaries")
                    ?: root.optJSONArray("summaries")
                    ?: root.optJSONObject("hrvSummary")?.let { JSONArray().put(it) }
                    ?: if (root.has("calendarDate")) JSONArray().put(root) else null
            }
            else -> null
        } ?: return emptyList()

        return (0 until array.length()).mapNotNull { idx ->
            val obj = array.optJSONObject(idx) ?: return@mapNotNull null
            val date = parseLocalDate(obj.optString("calendarDate")) ?: return@mapNotNull null
            val lastNight = optFiniteDouble(obj, "lastNightAvg")?.takeIf { it in 5.0..300.0 }
            val weekly = optFiniteDouble(obj, "weeklyAvg")?.takeIf { it in 5.0..300.0 }
            val status = obj.optString("status").trim().takeIf { it.isNotEmpty() && !it.equals("null", true) }
            val baselineObj = obj.optJSONObject("baseline")
            val baselineLow = baselineObj?.let { optFiniteDouble(it, "balancedLow") }?.takeIf { it in 5.0..300.0 }
            val baselineHigh = baselineObj?.let { optFiniteDouble(it, "balancedUpper") }?.takeIf { it in 5.0..300.0 }
            if (lastNight == null && weekly == null) return@mapNotNull null
            GarminHrvSummary(
                date = date,
                lastNightAvgMs = lastNight,
                weeklyAvgMs = weekly,
                status = status,
                baselineLowMs = baselineLow,
                baselineHighMs = baselineHigh
            )
        }
    }

    fun parseSleepRange(body: String): List<GarminSleepSummary> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.startsWith("[")) {
            val array = runCatching { JSONArray(trimmed) }.getOrNull() ?: return emptyList()
            return (0 until array.length()).mapNotNull { idx ->
                array.optJSONObject(idx)?.let(::parseSleepObject)
            }
        }
        val root = runCatching { JSONObject(trimmed) }.getOrNull() ?: return emptyList()
        val array = root.optJSONArray("dailySleepDTOs")
            ?: root.optJSONArray("sleepSummaries")
            ?: root.optJSONArray("summaries")
        if (array != null) {
            return (0 until array.length()).mapNotNull { idx ->
                array.optJSONObject(idx)?.let(::parseSleepObject)
            }
        }
        return listOfNotNull(parseSleepObject(root))
    }

    fun parseSleepSingleDay(body: String): GarminSleepSummary? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null
        val root = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
        return parseSleepObject(root)
    }

    private fun parseSleepObject(raw: JSONObject): GarminSleepSummary? {
        val obj = raw.optJSONObject("dailySleepDTO") ?: raw
        val date = parseLocalDate(obj.optString("calendarDate")) ?: return null
        val totalSec = optFiniteDouble(obj, "sleepTimeSeconds")
            ?.takeIf { it > 0.0 }
        val durationHours = totalSec?.div(3600.0)?.takeIf { it in 0.1..24.0 } ?: return null
        val deepSec = optNonNegativeInt(obj, "deepSleepSeconds")
        val lightSec = optNonNegativeInt(obj, "lightSleepSeconds")
        val remSec = optNonNegativeInt(obj, "remSleepSeconds")
        val awakeSec = optNonNegativeInt(obj, "awakeSleepSeconds")
        val overallScore = obj.optJSONObject("sleepScores")?.optJSONObject("overall")
            ?: raw.optJSONObject("sleepScores")?.optJSONObject("overall")
        val score = overallScore?.let { optNonNegativeInt(it, "value") }?.takeIf { it in 1..100 }
        val qualifier = overallScore?.optString("qualifierKey")?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("null", true) }
        return GarminSleepSummary(
            date = date,
            sleepDurationHours = durationHours,
            deepSeconds = deepSec,
            lightSeconds = lightSec,
            remSeconds = remSec,
            awakeSeconds = awakeSec,
            sleepScore = score,
            sleepQualifier = qualifier
        )
    }

    fun parseDailyHeartRate(body: String): GarminDailyHrSummary? {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return null
        val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
        val date = parseLocalDate(obj.optString("calendarDate")) ?: return null
        val rhr = optFiniteDouble(obj, "restingHeartRate")
            ?.takeIf { it in 25.0..150.0 }
            ?: optFiniteDouble(obj, "lastSevenDaysAvgRestingHeartRate")
                ?.takeIf { it in 25.0..150.0 }
            ?: return null
        val minHr = optFiniteDouble(obj, "minHeartRate")?.takeIf { it in 25.0..230.0 }
        val maxHr = optFiniteDouble(obj, "maxHeartRate")?.takeIf { it in 30.0..240.0 }
        return GarminDailyHrSummary(
            date = date,
            restingHeartRateBpm = rhr,
            minHeartRateBpm = minHr,
            maxHeartRateBpm = maxHr
        )
    }

    fun toWearableInputs(
        accountId: String,
        bundle: GarminWellnessBundle,
        zone: ZoneId = ZoneOffset.UTC
    ): List<WearableWellnessInput> {
        val out = mutableListOf<WearableWellnessInput>()
        bundle.dailyHr.forEach { hr ->
            val rhr = hr.restingHeartRateBpm ?: return@forEach
            out += WearableWellnessInput(
                accountId = accountId,
                provider = "garmin",
                externalId = WellnessRecord.canonicalId("garmin", "resting_hr", hr.date),
                kind = "resting_hr",
                value = rhr,
                unit = "bpm",
                at = recordedInstant(hr.date, 7, zone)
            )
        }
        bundle.hrv.forEach { hrv ->
            val value = hrv.lastNightAvgMs ?: return@forEach
            out += WearableWellnessInput(
                accountId = accountId,
                provider = "garmin",
                externalId = WellnessRecord.canonicalId("garmin", "hrv", hrv.date),
                kind = "hrv_rmssd",
                value = value,
                unit = "ms",
                at = recordedInstant(hrv.date, 6, zone)
            )
        }
        bundle.sleep.forEach { sleep ->
            val hours = sleep.sleepDurationHours ?: return@forEach
            out += WearableWellnessInput(
                accountId = accountId,
                provider = "garmin",
                externalId = WellnessRecord.canonicalId("garmin", "sleep_duration", sleep.date),
                kind = "sleep_duration",
                value = hours,
                unit = "hours",
                at = recordedInstant(sleep.date, 6, zone)
            )
        }
        return out
    }

    fun toWellnessRecords(
        bundle: GarminWellnessBundle,
        zone: ZoneId = ZoneOffset.UTC
    ): List<WellnessRecord> {
        val records = mutableListOf<WellnessRecord>()
        bundle.dailyHr.forEach { hr ->
            val rhr = hr.restingHeartRateBpm ?: return@forEach
            val meta = JSONObject().apply {
                hr.minHeartRateBpm?.let { put("minHrBpm", it) }
                hr.maxHeartRateBpm?.let { put("maxHrBpm", it) }
            }.takeIf { it.length() > 0 }
            records += WellnessRecord(
                id = WellnessRecord.canonicalId("garmin", "resting_hr", hr.date),
                type = "resting_hr",
                value = rhr,
                unit = "bpm",
                recordedAt = recordedInstant(hr.date, 7, zone),
                source = "garmin",
                metadata = meta
            )
        }
        bundle.hrv.forEach { hrv ->
            val value = hrv.lastNightAvgMs ?: return@forEach
            val meta = JSONObject().apply {
                hrv.weeklyAvgMs?.let { put("weeklyAvgMs", it) }
                hrv.status?.let { put("hrvStatus", it) }
                hrv.baselineLowMs?.let { put("baselineLowMs", it) }
                hrv.baselineHighMs?.let { put("baselineHighMs", it) }
            }.takeIf { it.length() > 0 }
            records += WellnessRecord(
                id = WellnessRecord.canonicalId("garmin", "hrv", hrv.date),
                type = "hrv",
                value = value,
                unit = "ms",
                recordedAt = recordedInstant(hrv.date, 6, zone),
                source = "garmin",
                metadata = meta
            )
        }
        bundle.sleep.forEach { sleep ->
            val hours = sleep.sleepDurationHours ?: return@forEach
            val meta = JSONObject().apply {
                sleep.sleepScore?.let { put("sleepScore", it) }
                sleep.sleepQualifier?.let { put("sleepQualifier", it) }
                sleep.deepSeconds?.let { put("deepSeconds", it) }
                sleep.lightSeconds?.let { put("lightSeconds", it) }
                sleep.remSeconds?.let { put("remSeconds", it) }
                sleep.awakeSeconds?.let { put("awakeSeconds", it) }
            }.takeIf { it.length() > 0 }
            records += WellnessRecord(
                id = WellnessRecord.canonicalId("garmin", "sleep_duration", sleep.date),
                type = "sleep_duration",
                value = hours,
                unit = "hours",
                recordedAt = recordedInstant(sleep.date, 6, zone),
                source = "garmin",
                metadata = meta
            )
        }
        return records
    }

    private fun recordedInstant(date: LocalDate, hour: Int, zone: ZoneId): Instant =
        date.atTime(hour, 0).atZone(zone).toInstant()

    private fun parseLocalDate(raw: String): LocalDate? {
        val text = raw.trim()
        if (text.isEmpty() || text.equals("null", true)) return null
        return runCatching { LocalDate.parse(text.take(10)) }.getOrNull()
    }

    private fun optFiniteDouble(obj: JSONObject, key: String): Double? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val v = obj.optDouble(key, Double.NaN)
        return v.takeIf { it.isFinite() }
    }

    private fun optNonNegativeInt(obj: JSONObject, key: String): Int? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val v = obj.optDouble(key, Double.NaN)
        if (!v.isFinite() || v < 0.0) return null
        return v.toInt()
    }
}
