package io.stridelab.privatealpha

/** Persistent optional weekday appointments, using ISO weekdays 1=Mon..7=Sun.
 * Zero is an incomplete UI assignment and MUST fail validation before generation.
 *
 * Session order matches existing engine template ordinals: run0 = long,
 * run1..qualityCount = quality, remaining = easy; other disciplines use their
 * existing generated session order. An empty map preserves old auto scheduling.
 */
internal object WeeklyPlacement {
    val disciplines = listOf("run", "str", "cal", "cond")

    fun count(p: PlanRequest, discipline: String): Int = when(discipline) {
        "run" -> p.runDays
        "str" -> p.strengthDays
        "cal" -> p.calDays
        "cond" -> p.conditioningDays
        else -> 0
    }

    fun days(p: PlanRequest, discipline: String): List<Int> {
        val explicit = p.weeklySlots[discipline]
        if (explicit != null) return explicit
        val count = count(p, discipline)
        if (count == 0) return emptyList()
        if (discipline != "run") return List(count) { 0 }
        val quality = p.qualityDays.sorted()
            .filter { it in p.days && it != p.longDay }
            .take(p.qualityCount)
        val chosen = mutableListOf(p.longDay)
        chosen.addAll(quality)
        while (chosen.size < 1 + p.qualityCount) chosen.add(0)
        chosen.addAll(p.days.sorted().filter { it !in chosen }
            .take((count - chosen.size).coerceAtLeast(0)))
        return (chosen + List(count) { 0 }).take(count)
    }

    /** Assign or swap a session, never silently discard another appointment. */
    fun put(p: PlanRequest, discipline: String, index: Int, day: Int): PlanRequest {
        require(discipline in disciplines && index in 0 until count(p, discipline))
        require(day in 1..7)
        val slots = days(p, discipline).toMutableList()
        val oldDay = slots[index]
        val other = slots.indexOf(day)
        if (other >= 0 && other != index) slots[other] = oldDay
        slots[index] = day
        return withSlots(p, discipline, slots)
    }

    fun clear(p: PlanRequest, discipline: String, index: Int): PlanRequest {
        require(discipline in disciplines && index in 0 until count(p, discipline))
        val slots = days(p, discipline).toMutableList()
        slots[index] = 0
        return withSlots(p, discipline, slots)
    }

    fun resize(p: PlanRequest, discipline: String, newCount: Int): PlanRequest {
        require(discipline in disciplines && newCount >= 0)
        val original = days(p, discipline)
        // Reduce session roles, not arbitrary calendar days. Slot 0 is always
        // the long run. If a lower run count reduces permitted quality work,
        // discard the extra quality slot before discarding an existing easy run.
        // Otherwise a pinned easy day can silently become a different workout.
        val retained = if (discipline == "run" && newCount < original.size) {
            val qualityToKeep = p.qualityCount.coerceAtMost((newCount - 2).coerceAtLeast(0))
            val easyToKeep = (newCount - (if (newCount > 0) 1 else 0) -
                qualityToKeep).coerceAtLeast(0)
            original.take(if (newCount > 0) 1 else 0) +
                original.drop(1).take(qualityToKeep) +
                original.drop(1 + p.qualityCount).take(easyToKeep)
        } else original.take(newCount)
        val slots = (retained + List(newCount) { 0 }).take(newCount)
        val updated = when(discipline) {
            "run" -> p.copy(runDays=newCount,
                qualityCount=p.qualityCount.coerceAtMost((newCount-2).coerceAtLeast(0)))
            "str" -> p.copy(strengthDays=newCount)
            "cal" -> p.copy(calDays=newCount)
            else -> p.copy(conditioningDays=newCount)
        }
        return withSlots(updated, discipline, slots)
    }

    private fun withSlots(
        p: PlanRequest, discipline: String, slots: List<Int>
    ): PlanRequest {
        val saved = if (slots.isEmpty()) p.weeklySlots - discipline
            else p.weeklySlots + (discipline to slots)
        if (discipline != "run") return p.copy(
            weeklySlots = saved,
            days = p.days + slots.filter { it in 1..7 }
        )
        val newLong = slots.firstOrNull()?.takeIf { it in 1..7 } ?: p.longDay
        val quality = slots.drop(1).take(p.qualityCount)
            .filter { it in 1..7 }.toSet()
        return p.copy(
            weeklySlots=saved,
            days=p.days + slots.filter { it in 1..7 },
            longDay=newLong, qualityDays=quality
        )
    }

    /** Explicitly free a weekday only after the athlete confirms the change.
     * Keep session counts intact; every affected manual appointment becomes
     * unassigned, so validation blocks generation until it is moved or set auto.
     */
    fun makeUnavailable(p: PlanRequest, day: Int): PlanRequest {
        require(day in 1..7)
        var changed=p
        p.weeklySlots.forEach { (discipline, slots) ->
            slots.forEachIndexed { index, assignedDay ->
                if(assignedDay==day) changed=clear(changed,discipline,index)
            }
        }
        val remaining=changed.days-day
        return changed.copy(
            days=remaining,
            longDay=changed.longDay.takeIf { it in remaining }
                ?: (remaining.maxOrNull() ?: changed.longDay),
            qualityDays=changed.qualityDays.intersect(remaining)
        )
    }

    fun full(p: PlanRequest, discipline: String): Boolean {
        val chosen = p.weeklySlots[discipline] ?: return true
        return chosen.size == count(p, discipline) &&
            chosen.all { it in p.days && it in 1..7 } &&
            chosen.toSet().size == chosen.size
    }

    fun validationError(p: PlanRequest): String? {
        for ((discipline, chosen) in p.weeklySlots) {
            if (discipline !in disciplines) return "Unknown training discipline in weekly assignments."
            if (chosen.size != count(p,discipline)) {
                return "Finish assigning every " + disciplineName(discipline) +
                    " workout, or choose automatic scheduling."
            }
            if (chosen.any { it !in 1..7 || it !in p.days }) {
                return "Every assigned workout needs an available weekday."
            }
            if (chosen.distinct().size != chosen.size) {
                return "Only one " + disciplineName(discipline) +
                    " session may occupy a weekday."
            }
            if (discipline == "run" && chosen.isNotEmpty()) {
                if (chosen.first() != p.longDay) {
                    return "Your long run must match the selected long-run weekday."
                }
                if (p.qualityCount > 0 &&
                    chosen.drop(1).take(p.qualityCount).toSet() != p.qualityDays) {
                    return "Quality-run day preferences must match the selected quality sessions."
                }
            }
        }
        return null
    }

    fun fixedDay(p: PlanRequest, session: PlannedSession): Int? {
        val index = session.id.substringAfterLast("-" + session.discipline, "")
            .toIntOrNull() ?: return null
        return p.weeklySlots[session.discipline]?.getOrNull(index)
    }

    fun label(p:PlanRequest, discipline:String, index:Int):String = when(discipline) {
        "run" -> when {
            index == 0 -> "Long run"
            index <= p.qualityCount -> "Quality " + index
            else -> "Easy " + (index - p.qualityCount)
        }
        "str" -> if(index == 0) "Strength A" else "Strength " + (index+1)
        "cal" -> "Calisthenics " + (index+1)
        "cond" -> "Conditioning " + (index+1)
        else -> "Session " + (index+1)
    }

    fun disciplineName(d:String) = when(d) {
        "run" -> "running"
        "str" -> "strength"
        "cal" -> "calisthenics"
        "cond" -> "conditioning"
        else -> "training"
    }
}
