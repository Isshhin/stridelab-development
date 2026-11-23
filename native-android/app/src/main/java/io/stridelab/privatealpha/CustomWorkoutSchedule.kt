package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

internal val customDateRange = LocalDate.of(2000, 1, 1)..LocalDate.of(2100, 12, 31)

/** The anchor is the occurrence's identity even after moving it. */
internal data class CustomOccurrenceKey(val seriesId: String, val anchor: LocalDate) {
    val workoutId get() = UUID.nameUUIDFromBytes("custom-workout:$seriesId:$anchor".toByteArray(Charsets.UTF_8)).toString()
}

internal data class CustomWorkoutBinding(val key: CustomOccurrenceKey, val date: LocalDate)

internal data class ScheduleRule(val start: LocalDate, val weekly: Boolean = false, val end: LocalDate? = null) {
    init {
        require(start in customDateRange) { "Choose a date between 2000 and 2100." }
        require(end == null || weekly && end >= start && end in customDateRange) {
            "The end date must be on or after the first workout."
        }
    }

    fun occursOn(date: LocalDate): Boolean = if (weekly) {
        date >= start && (end == null || date <= end) && date.dayOfWeek == start.dayOfWeek
    } else date == start
}

/** A null rule ends the series from this cutoff while retaining its earlier identity. */
internal data class CustomScheduleWindow(val from: LocalDate, val rule: ScheduleRule?)

internal data class CustomOccurrence(
    val key: CustomOccurrenceKey,
    val date: LocalDate,
    val template: ManualWorkoutTemplate,
    val weekly: Boolean
) {
    fun start(performedOn: LocalDate, now: Long): FreeformWorkout =
        template.startWorkout(performedOn, now).copy(id = key.workoutId, custom = CustomWorkoutBinding(key, date))
}

internal data class CustomSchedule(
    val id: String,
    val template: ManualWorkoutTemplate,
    val modifiedAt: Long,
    val windows: List<CustomScheduleWindow>,
    val overrides: Map<LocalDate, LocalDate?> = emptyMap()
) {
    val owner get() = template.owner

    fun resolve(key: CustomOccurrenceKey): CustomOccurrence? {
        if (key.seriesId != id) return null
        val rule = ruleAt(key.anchor)
        val date = if (key.anchor in overrides) overrides[key.anchor] else key.anchor.takeIf { rule?.occursOn(it) == true }
        return date?.let { CustomOccurrence(key, it, template, rule?.weekly ?: windows.any { w -> w.rule?.weekly == true }) }
    }

    fun ruleAt(date: LocalDate): ScheduleRule? = windows.lastOrNull { it.from <= date }?.rule

    fun between(from: LocalDate, through: LocalDate): List<CustomOccurrence> {
        val interval = ChronoUnit.DAYS.between(from, through)
        require(interval in 0..92) { "A calendar query can cover up to 93 days." }
        val regular = (0L..interval).mapNotNull { offset ->
            val anchor = from.plusDays(offset)
            if (anchor in overrides) null else resolve(CustomOccurrenceKey(id, anchor))
        }
        val moved = overrides.mapNotNull { (anchor, replacement) ->
            if (replacement != null && replacement in from..through) resolve(CustomOccurrenceKey(id, anchor)) else null
        }
        return (regular + moved).sortedWith(compareBy<CustomOccurrence> { it.date }.thenBy { it.key.anchor })
    }

    fun move(key: CustomOccurrenceKey, date: LocalDate?, now: Long): CustomSchedule {
        val current = requireNotNull(resolve(key)) { "This workout is no longer scheduled." }
        require(date == null || date in customDateRange) { "Choose a date between 2000 and 2100." }
        if (date == current.date) return this
        return copy(modifiedAt = maxOf(now, modifiedAt + 1), overrides = overrides + (key.anchor to date))
    }

    fun replaceFuture(anchor: LocalDate, rule: ScheduleRule?, now: Long): CustomSchedule {
        requireNotNull(resolve(CustomOccurrenceKey(id, anchor))) { "This workout is no longer scheduled." }
        require(rule == null || rule.start >= anchor) { "Choose a first date on or after this occurrence’s original date." }
        return copy(
            modifiedAt = maxOf(now, modifiedAt + 1),
            windows = windows.filter { it.from < anchor } + CustomScheduleWindow(anchor, rule),
            overrides = if (rule == null) overrides.filterKeys { it < anchor } else overrides
        )
    }

    companion object {
        fun create(template: ManualWorkoutTemplate, rule: ScheduleRule, now: Long) =
            CustomSchedule(UUID.randomUUID().toString(), template, now, listOf(CustomScheduleWindow(rule.start, rule)))
    }
}

internal object CustomScheduleCodec {
    private const val MAX_ROW = 1_250_000

    fun encode(schedule: CustomSchedule): String {
        RecordPolicy.identifier(schedule.id)
        require(schedule.modifiedAt in 1 until Long.MAX_VALUE - 1)
        require(schedule.windows.isNotEmpty() && schedule.windows.size <= 1000 && schedule.overrides.size <= 5000) {
            "This schedule has too many changes to store."
        }
        require(schedule.windows.zipWithNext().all { (a, b) -> a.from < b.from })
        require(schedule.windows.all { window ->
            window.from in customDateRange && (window.rule == null || window.rule.start >= window.from)
        })
        require(schedule.overrides.all { (anchor, date) ->
            anchor in customDateRange && (date == null || date in customDateRange)
        })
        return JSONObject()
            .put("schema", 1).put("id", schedule.id).put("owner", schedule.owner).put("modifiedAt", schedule.modifiedAt)
            .put("template", JSONObject(ManualWorkoutTemplateCodec.encode(schedule.template)))
            .put("windows", JSONArray(schedule.windows.map { window ->
                JSONObject().put("from", window.from.toString()).apply {
                    window.rule?.let { rule ->
                        put("start", rule.start.toString())
                        put("weekly", rule.weekly)
                        put("end", rule.end?.toString())
                    }
                }
            }))
            .put("overrides", JSONArray(schedule.overrides.toSortedMap().map { (anchor, date) ->
                JSONObject().put("anchor", anchor.toString()).put("date", date?.toString())
            })).toString().also { require(it.length <= MAX_ROW) { "This schedule is too large." } }
    }

    fun decode(raw: String, owner: String): CustomSchedule {
        require(raw.length <= MAX_ROW)
        val json = JSONObject(raw)
        require(json.getInt("schema") == 1 && json.getString("owner") == owner)
        val windows = json.getJSONArray("windows")
        val overrides = json.getJSONArray("overrides")
        require(windows.length() in 1..1000 && overrides.length() <= 5000)
        val edits = List(overrides.length()) { index ->
            val row = overrides.getJSONObject(index)
            row.date("anchor") to row.optionalDate("date")
        }
        require(edits.distinctBy { it.first }.size == edits.size)
        return CustomSchedule(
            id = json.getString("id"), template = ManualWorkoutTemplateCodec.decode(json.getJSONObject("template").toString(), owner),
            modifiedAt = json.getLong("modifiedAt"),
            windows = List(windows.length()) { index ->
                val row = windows.getJSONObject(index)
                val rule = row.optionalDate("start")?.let { ScheduleRule(it, row.getBoolean("weekly"), row.optionalDate("end")) }
                CustomScheduleWindow(row.date("from"), rule)
            },
            overrides = edits.toMap()
        ).also { encode(it) }
    }

    private fun JSONObject.date(key: String): LocalDate = LocalDate.parse(getString(key))
    private fun JSONObject.optionalDate(key: String): LocalDate? = if (isNull(key)) null else date(key)
}
