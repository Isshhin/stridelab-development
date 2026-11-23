package io.stridelab.privatealpha

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

internal object WorkoutExecutionCheckpoint {
    fun key(planId: String, sessionId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$planId\u0000$sessionId".toByteArray(Charsets.UTF_8))
        return "player-exec-" + digest.joinToString("") { "%02x".format(it) }
    }

    fun encode(owner: String, execution: WorkoutExecution, nowMs: Long, wallMs: Long, bootCount: Int? = null): String {
        val timer = execution.timer?.let {
JSONObject().put("budget", it.budgetMs).put("remaining", it.rawRemaining(nowMs))
                .put("running", it.deadlineMs != null)
        }
        val outcome = execution.outcome
        return JSONObject().put("schemaVersion", 1).put("type", "player-execution").put("user_id", owner)
            .put("executionId", execution.executionId).put("planId", execution.planId)
            .put("sessionId", execution.sessionId).put("finalizationId", execution.finalizationId)
            .put("revision", execution.revision).put("cursor", execution.cursor).put("openedAtMs", execution.openedAtMs)
            .put("phase", execution.phase.name)
            .put("status", if (execution.lifecycle == ExecutionLifecycle.FINISHED_NORMALLY) "COMPLETED" else execution.lifecycle.name)
            .put("lifecycle", outcome.lifecycle.name).put("prescriptionOutcome", outcome.prescription.name)
            .put("confirmedSets", outcome.confirmedSets).put("skippedSets", outcome.skippedSets)
            .put("timer", timer).put("wallAnchorMs", wallMs).put("monotonicAnchorMs", nowMs).put("bootCount", bootCount)
            .put("finalLog", execution.finalLog?.encode()).put("receipt", execution.receipt)
            .put("sets", JSONArray(execution.sets.map { step ->
                JSONObject().put("target", step.target.encode()).put("number", step.number)
                    .put("disposition", step.disposition.name).put("workMs", step.measuredWorkMs)
                    .put("restMs", step.actualRestMs).put("actual", step.actual?.let {
                        JSONObject().put("reps", it.reps).put("kg", it.kg).put("seconds", it.seconds).put("rir", it.rir)
                    })
            })).toString()
    }

    fun decode(raw: String, owner: String, planId: String, sessionId: String, nowMs: Long, wallMs: Long, bootCount: Int? = null): WorkoutExecution {
        val json = JSONObject(raw)
        require(json.getInt("schemaVersion") == 1 && json.getString("type") == "player-execution")
        require(json.getString("user_id") == owner && json.getString("planId") == planId && json.getString("sessionId") == sessionId)
        val timer = json.optJSONObject("timer")?.let {
            val budget = it.getLong("budget")
            val saved = it.getLong("remaining")
            require(budget >= 0 && saved <= budget && (it.getBoolean("running") || saved >= 0))
            val wall = (wallMs - json.getLong("wallAnchorMs")).coerceAtLeast(0)
            val monotonic = nowMs - json.getLong("monotonicAnchorMs")
            val savedBoot = json.nullInt("bootCount")
            val sameBoot = if (savedBoot != null && bootCount != null) savedBoot == bootCount
                else kotlin.math.abs(monotonic - wall) <= 2000
            val elapsed = if (sameBoot && monotonic >= 0) monotonic else wall
            if (it.getBoolean("running")) PlayerTimer(budget, deadlineMs = nowMs + saved - elapsed)
            else PlayerTimer(budget, saved)
        }
        val sets = json.getJSONArray("sets").objects().map { step ->
            ExecutionSet(ExercisePrescription.decode(step.getJSONObject("target")), step.getInt("number"),
                SetDisposition.valueOf(step.getString("disposition")), step.optJSONObject("actual")?.let {
                    SetActual(it.nullInt("reps"), it.nullDouble("kg"), it.nullInt("seconds"), it.nullInt("rir"))
                }, step.optionalLong("workMs"), step.optionalLong("restMs"))
        }
        val cursor = json.getInt("cursor")
        require(cursor in sets.indices)
        val lifecycle = when (val value = json.optString("lifecycle", json.optString("status"))) {
            "COMPLETED" -> ExecutionLifecycle.FINISHED_NORMALLY
            else -> ExecutionLifecycle.valueOf(value)
        }
        val execution = WorkoutExecution(json.getString("executionId"), planId, sessionId, json.getString("finalizationId"),
            sets, cursor, PlayerPhase.valueOf(json.getString("phase")), lifecycle,
            timer, json.optJSONObject("finalLog")?.let(WorkoutLog::decode), json.getBoolean("receipt"),
            json.getLong("revision"), json.optionalLong("openedAtMs"))
        if (json.has("prescriptionOutcome")) {
            val outcome = execution.outcome
            require(json.getString("prescriptionOutcome") == outcome.prescription.name &&
                json.getInt("confirmedSets") == outcome.confirmedSets && json.getInt("skippedSets") == outcome.skippedSets)
        }
        return execution
    }

    private fun JSONObject.optionalLong(key: String) = if (isNull(key)) null else getLong(key)
}
