package io.stridelab.privatealpha

import android.content.Context
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

internal enum class PersonalBestSource(val badgeLabel: String) {
    VERIFIED_RACE_PR("OFFICIAL PR"),
    RECORDED_ACTIVITY("RECORDED"),
    SPLIT_BEST_EFFORT("BEST EFFORT")
}

internal enum class PersonalBestDistance(
    val key: String,
    val label: String,
    val meters: Double
) {
    ONE_K("1k", "1 km", 1000.0),
    FIVE_K("5k", "5 km", 5000.0),
    TEN_K("10k", "10 km", 10000.0),
    HALF_MARATHON("hm", "Half marathon", 21097.5),
    MARATHON("marathon", "Marathon", 42195.0)
}

internal data class OfficialRacePr(
    val distanceKey: String,
    val durationSeconds: Int,
    val eventName: String? = null,
    val achievedOn: LocalDate? = null
) {
    init {
        require(durationSeconds in 90..86400)
    }
}

internal data class RecordedBestEffort(
    val distance: PersonalBestDistance,
    val durationSeconds: Double,
    val paceSecPerKm: Double,
    val source: PersonalBestSource,
    val activityId: String,
    val activityTitle: String,
    val achievedAt: Instant
)

internal data class PersonalBestEntry(
    val distance: PersonalBestDistance,
    val officialPr: OfficialRacePr?,
    val recordedBest: RecordedBestEffort?
)

internal class UserPersonalBestStore(context: Context) {
    private val prefs = context.getSharedPreferences("stridelab_official_prs_v1", Context.MODE_PRIVATE)

    private fun ownerKey(owner: String?): String =
        owner?.trim()?.takeIf { it.isNotEmpty() } ?: "local"

    fun loadAll(owner: String?): Map<String, OfficialRacePr> {
        val raw = prefs.getString("prs_${ownerKey(owner)}", null) ?: return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            buildMap {
                PersonalBestDistance.entries.forEach { d ->
                    val obj = root.optJSONObject(d.key) ?: return@forEach
                    val sec = obj.optInt("durationSeconds", 0)
                    if (sec in 90..86400) {
                        val event = obj.optString("eventName").takeIf { it.isNotBlank() }
                        val date = obj.optString("achievedOn").takeIf { it.isNotBlank() }
                            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                        put(d.key, OfficialRacePr(d.key, sec, event, date))
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun save(owner: String?, pr: OfficialRacePr): Boolean {
        val existing = loadAll(owner).toMutableMap()
        existing[pr.distanceKey] = pr
        return writeMap(owner, existing)
    }

    fun clear(owner: String?, distanceKey: String): Boolean {
        val existing = loadAll(owner).toMutableMap()
        existing.remove(distanceKey)
        return writeMap(owner, existing)
    }

    private fun writeMap(owner: String?, map: Map<String, OfficialRacePr>): Boolean {
        val root = JSONObject()
        map.forEach { (k, pr) ->
            root.put(
                k,
                JSONObject()
                    .put("durationSeconds", pr.durationSeconds)
                    .apply {
                        pr.eventName?.let { put("eventName", it) }
                        pr.achievedOn?.let { put("achievedOn", it.toString()) }
                    }
            )
        }
        return prefs.edit().putString("prs_${ownerKey(owner)}", root.toString()).commit()
    }
}

internal object RunningPersonalBests {
    private const val MIN_VALID_PACE_SEC_PER_KM = 150.0 // 2:30/km rejects GPS spikes

    fun compute(
        activities: List<ActivityRecord>,
        details: Map<String, ActivityDetail>,
        officialPrs: Map<String, OfficialRacePr> = emptyMap(),
        asOf: Instant = Instant.now()
    ): List<PersonalBestEntry> {
        val visibleRuns = ActivityDisplayConsolidation.visible(activities)
            .filter { DashboardSummary.isRun(it) && !it.startedAt.isAfter(asOf) }

        return PersonalBestDistance.entries.map { dist ->
            val wholeBest = RecordedDistanceBests.find(visibleRuns, dist.meters, asOf)?.let { matched ->
                val pace = matched.activity.recordedPace ?: return@let null
                if (!pace.isFinite() || pace < MIN_VALID_PACE_SEC_PER_KM) return@let null
                val durS = matched.activity.movingTimeS ?: matched.activity.durationS
                    ?: (pace * (matched.activity.distanceM ?: dist.meters) / 1000.0)
                val normalizedSec = if ((matched.activity.distanceM ?: 0.0) >= dist.meters) {
                    durS * (dist.meters / (matched.activity.distanceM ?: dist.meters))
                } else {
                    pace * (dist.meters / 1000.0)
                }
                RecordedBestEffort(
                    distance = dist,
                    durationSeconds = normalizedSec,
                    paceSecPerKm = pace,
                    source = PersonalBestSource.RECORDED_ACTIVITY,
                    activityId = matched.activity.id,
                    activityTitle = matched.activity.title,
                    achievedAt = matched.activity.startedAt
                )
            }

            val splitBest = visibleRuns
                .asSequence()
                .filter { (it.distanceM ?: 0.0) >= dist.meters * 0.995 }
                .mapNotNull { run ->
                    val detail = details[run.id] ?: return@mapNotNull null
                    findBestLapWindow(run, detail, dist)
                }
                .minWithOrNull(compareBy<RecordedBestEffort> { it.durationSeconds }.thenBy { it.achievedAt })

            val bestRecorded = listOfNotNull(wholeBest, splitBest)
                .minWithOrNull(compareBy<RecordedBestEffort> { it.durationSeconds }.thenBy { it.achievedAt })

            PersonalBestEntry(
                distance = dist,
                officialPr = officialPrs[dist.key],
                recordedBest = bestRecorded
            )
        }
    }

    private fun findBestLapWindow(
        activity: ActivityRecord,
        detail: ActivityDetail,
        target: PersonalBestDistance
    ): RecordedBestEffort? {
        val laps = detail.laps
        if (laps.isEmpty()) return null

        var best: RecordedBestEffort? = null
        val n = laps.size
        for (start in 0 until n) {
            var sumMeters = 0.0
            var sumSeconds = 0.0
            var valid = true
            for (end in start until n) {
                val lap = laps[end]
                val d = lap.distance?.takeIf { it.isFinite() && it > 0.0 }
                val s = lap.seconds?.takeIf { it.isFinite() && it > 0.0 }
                if (d == null || s == null) {
                    valid = false
                    break
                }
                val lapPace = s * 1000.0 / d
                if (lapPace < MIN_VALID_PACE_SEC_PER_KM) {
                    valid = false
                    break
                }
                sumMeters += d
                sumSeconds += s
                if (sumMeters >= target.meters) {
                    if (sumMeters <= target.meters * 1.03) {
                        val normalizedSeconds = sumSeconds * (target.meters / sumMeters)
                        val pace = normalizedSeconds / (target.meters / 1000.0)
                        if (pace >= MIN_VALID_PACE_SEC_PER_KM &&
                            (best == null || normalizedSeconds < best.durationSeconds)
                        ) {
                            best = RecordedBestEffort(
                                distance = target,
                                durationSeconds = normalizedSeconds,
                                paceSecPerKm = pace,
                                source = PersonalBestSource.SPLIT_BEST_EFFORT,
                                activityId = activity.id,
                                activityTitle = activity.title,
                                achievedAt = activity.startedAt
                            )
                        }
                    }
                    break
                }
            }
            if (!valid && start == n - 1) break
        }
        return best
    }

    fun formatDuration(seconds: Double): String {
        val total = kotlin.math.round(seconds).toInt().coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun formatPace(secPerKm: Double): String {
        val total = kotlin.math.round(secPerKm).toInt().coerceAtLeast(1)
        return String.format(Locale.US, "%d:%02d /km", total / 60, total % 60)
    }
}
