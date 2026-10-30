package io.stridelab.privatealpha

import com.garmin.fit.*
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.time.Instant

/** Read-only, bounded official-profile decoding. No plan completion or remote writes. */
internal object FitActivityDecoder {
    data class Point(val at: Instant, val distanceM: Double?, val speedMps: Double?,
        val heartRate: Double?, val altitudeM: Double?, val latitude: Double?,
        val longitude: Double?, val cadenceRpm: Double?, val powerW: Double?)
    data class Lap(val startedAt: Instant?, val elapsedS: Double?, val timerS: Double?,
        val distanceM: Double?, val averageHr: Double?)
    data class Activity(val identity: ImportedSourceIdentity, val sport: String,
        val startedAt: Instant, val elapsedS: Double?, val timerS: Double?,
        val distanceM: Double?, val averageHr: Double?, val maximumHr: Double?,
        val points: List<Point>, val laps: List<Lap>) {
        fun candidate() = ImportedActivityCandidate(identity,sport,startedAt,elapsedS,distanceM)
        /** Factual observations only; no invented recovery or coaching conclusion. */
        fun evidence(): List<String> = buildList {
            add("Source: user-authorized FIT; $sport")
            distanceM?.let { add("Recorded distance: $it m") }
            elapsedS?.let { add("Recorded elapsed time: $it s") }
            timerS?.let { add("Recorded timer time: $it s (not moving time)") }
            averageHr?.let { add("Recorded average heart rate: $it bpm") }
            add("${points.count { it.latitude != null && it.longitude != null }} recorded GPS samples")
        }
    }
    private fun metric(n: Number?, minimum: Double = 0.0): Double? = n?.toDouble()?.also {
        require(it.isFinite() && it >= minimum) { "Invalid FIT metric" }
    }
    private fun time(t: DateTime?): Instant? = t?.date?.toInstant()
    private fun coordinate(n: Int?, limit: Double): Double? = n?.let {
        (it.toDouble() * 180.0 / 2147483648.0).also { v -> require(v in -limit..limit) }
    }
    fun decode(bytes: ByteArray, account: String): Activity {
        require(account.isNotBlank() && account.length <= 256)
        require(bytes.size in 14..FitContainerReader.MAX_BYTES)
        val header = bytes[0].toInt() and 255
        require(header == 12 || header == 14)
        val length = (4..7).fold(0L) { value, i -> value or ((bytes[i].toLong() and 255) shl ((i-4)*8)) }
        require(header + length + 2 == bytes.size.toLong()) { "Chained or incomplete FIT file" }
        require(Decode().checkFileIntegrity(ByteArrayInputStream(bytes))) { "FIT checksum failure" }
        val sessions = mutableListOf<SessionMesg>()
        val records = mutableListOf<RecordMesg>()
        val laps = mutableListOf<LapMesg>()
        var activityFile = false
        var count = 0
        val decoder = Decode()
        decoder.addListener(MesgListener { message ->
            require(++count <= 100_000) { "Too many FIT messages" }
            when(message.num) {
                MesgNum.FILE_ID -> activityFile = FileIdMesg(message).type == com.garmin.fit.File.ACTIVITY
                MesgNum.SESSION -> sessions.add(SessionMesg(message))
                MesgNum.RECORD -> { require(records.size < 20_000); records.add(RecordMesg(message)) }
                MesgNum.LAP -> { require(laps.size < 1000); laps.add(LapMesg(message)) }
            }
        })
        require(decoder.read(ByteArrayInputStream(bytes)))
        require(activityFile && sessions.size == 1) { "Only single-session activity FIT files are supported; multisport needs explicit review" }
        val s = sessions.single()
        val start = time(s.startTime) ?: throw IllegalArgumentException("Missing FIT session start")
        val sport = s.sport?.name?.lowercase(java.util.Locale.ROOT)
            ?: throw IllegalArgumentException("Missing FIT sport")
        val points = records.map { r ->
            Point(time(r.timestamp) ?: throw IllegalArgumentException("Missing record timestamp"),
                metric(r.distance),metric(r.enhancedSpeed ?: r.speed),metric(r.heartRate,1.0),
                metric(r.enhancedAltitude ?: r.altitude,-12000.0),coordinate(r.positionLat,90.0),
                coordinate(r.positionLong,180.0),metric(r.cadence),metric(r.power))
        }
        require(points.all { it.at >= start } && points.zipWithNext().all { (a,b) -> b.at >= a.at })
        val elapsed = metric(s.totalElapsedTime)
        val timer = metric(s.totalTimerTime)
        require(elapsed == null || timer == null || timer <= elapsed + 0.001)
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return Activity(ImportedSourceIdentity(account,"fit-file",sha256=hash),sport,start,elapsed,timer,
            metric(s.totalDistance),metric(s.avgHeartRate,1.0),metric(s.maxHeartRate,1.0),points,
            laps.map { Lap(time(it.startTime),metric(it.totalElapsedTime),metric(it.totalTimerTime),
                metric(it.totalDistance),metric(it.avgHeartRate,1.0)) })
    }
}
