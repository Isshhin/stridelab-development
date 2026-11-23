package io.stridelab.privatealpha

import java.io.ByteArrayOutputStream
import java.text.Normalizer
import java.time.Instant
import kotlin.math.roundToInt

/** FIT 2.0 workout files. All values passed to the writer are in FIT wire units. */
internal object GarminFitWorkoutEncoder {
    private const val OPEN = 2
    private const val OPEN_DURATION = 5
    private const val REPEAT = 6
    private const val INVALID_UINT32 = 0xffffffffL
    private const val NAME_BYTES = 48
    private val fitEpoch = Instant.parse("1989-12-31T00:00:00Z").epochSecond
    private val crcTable = intArrayOf(
        0x0000, 0xcc01, 0xd801, 0x1400, 0xf001, 0x3c00, 0x2800, 0xe401,
        0xa001, 0x6c00, 0x7800, 0xb401, 0x5000, 0x9c01, 0x8801, 0x4400
    )

    fun fileName(title: String): String {
        val stem = asciiName(title).replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')
            .ifEmpty { "Running_workout" }
        val reserved = Regex("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]", RegexOption.IGNORE_CASE)
        return (if (reserved.matches(stem)) "${stem}_workout" else stem) + ".fit"
    }

    fun encode(
        title: String,
        prescription: RunningPrescription?,
        createdAt: Instant,
        verifiedHrZones: ExecutionHrZoneProfile? = null,
        fallbackMeters: Int? = null,
        fallbackSeconds: Int? = null,
        serialNumber: Long = 1L
    ): ByteArray {
        require(serialNumber in 1..INVALID_UINT32)
        require(fallbackMeters == null || fallbackMeters in 1..120_000)
        require(fallbackSeconds == null || fallbackSeconds in 1..14_400)
        val timestamp = createdAt.epochSecond - fitEpoch
        require(timestamp in 0 until INVALID_UINT32)
        val steps = compactRepeats(prescription?.exportSteps(verifiedHrZones)
            ?: listOf(step("Run", 0, fallbackMeters, if (fallbackMeters == null) fallbackSeconds else null)))
        val body = ByteArrayOutputStream().apply {
            // file_id (0): type, manufacturer, product, time_created, serial_number.
            definition(0, 0, Field(0, 1, 0), Field(1, 2, 0x84), Field(2, 2, 0x84),
                Field(4, 4, 0x86), Field(3, 4, 0x8c))
            write(0)
            write(5)
            little(255, 2) // Development manufacturer.
            little(0, 2)
            little(timestamp, 4)
            little(serialNumber, 4)

            // workout (26): sport, num_valid_steps, wkt_name.
            definition(1, 26, Field(4, 1, 0), Field(6, 2, 0x84), Field(8, NAME_BYTES, 7))
            write(1)
            write(1)
            little(steps.size.toLong(), 2)
            fitString(title)

            // workout_step (27): message_index, name, duration, target, custom bounds, intensity.
            definition(2, 27, Field(254, 2, 0x84), Field(0, NAME_BYTES, 7),
                Field(1, 1, 0), Field(2, 4, 0x86), Field(3, 1, 0), Field(4, 4, 0x86),
                Field(5, 4, 0x86), Field(6, 4, 0x86), Field(7, 1, 0))
            steps.forEachIndexed { index, step ->
                write(2)
                little(index.toLong(), 2)
                fitString(step.name)
                write(step.durationType)
                little(step.durationValue, 4)
                write(step.targetType)
                little(step.targetValue, 4)
                little(step.low, 4)
                little(step.high, 4)
                write(step.intensity)
            }
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            write(14) // Includes the two-byte header CRC.
            write(0x20)
            little(2_100, 2) // FIT Profile 21.00.
            little(body.size.toLong(), 4)
            write(".FIT".toByteArray(Charsets.US_ASCII))
            little(crc(toByteArray()).toLong(), 2)
            write(body)
            little(crc(toByteArray()).toLong(), 2)
        }.toByteArray()
    }

    private data class Step(
        val name: String, val intensity: Int, val durationType: Int, val durationValue: Long,
        val targetType: Int = OPEN, val targetValue: Long = INVALID_UINT32,
        val low: Long = INVALID_UINT32, val high: Long = INVALID_UINT32,
        val role: String = ""
    )

    private fun step(name: String, intensity: Int, meters: Int?, seconds: Int?) = when {
        seconds != null -> Step(name, intensity, 0, seconds * 1_000L)
        meters != null -> Step(name, intensity, 1, meters * 100L)
        else -> Step(name, intensity, OPEN_DURATION, INVALID_UINT32)
    }

    private fun RunningPrescription.exportSteps(verifiedHrZones: ExecutionHrZoneProfile?): List<Step> {
        val structured = mixedSegments ?: segments.map {
            RunningStepV2(it.purpose, it.effort, meters = it.meters, stepIntent = it.stepIntent,
                targetPaceMinSecPerKm = it.targetPaceMinSecPerKm, targetPaceMaxSecPerKm = it.targetPaceMaxSecPerKm,
                targetHrMinBpm = it.targetHrMinBpm, targetHrMaxBpm = it.targetHrMaxBpm)
        }
        return structured.map { source ->
            val (label, intensity) = when (source.purpose) {
                "WARM_UP" -> "Warmup" to 2
                "COOL_DOWN" -> "Cooldown" to 3
                "RECOVERY" -> "Recovery" to 4
                "TRANSITION" -> "Rest" to 1
                else -> when (source.stepIntent) {
                    RunningStepIntent.STRIDE -> "Stride"
                    RunningStepIntent.HILL -> "Hill"
                    RunningStepIntent.TEMPO -> "Tempo"
                    RunningStepIntent.THRESHOLD -> "Threshold"
                    RunningStepIntent.RACE_PACE -> "Race pace"
                    else -> if (source.purpose == "WORK") "Interval" else "Steady"
                } to 0
            }
            val step = step(label, intensity, source.meters, source.seconds).copy(role = source.purpose)
            val hrLow = source.targetHrMinBpm
            val hrHigh = source.targetHrMaxBpm
            val fast = source.targetPaceMinSecPerKm
            val slow = source.targetPaceMaxSecPerKm
            when {
                verifiedHrZones != null && hrLow != null && hrHigh != null ->
                    step.copy(targetType = 1, targetValue = 0, low = hrLow + 100L, high = hrHigh + 100L)
                fast != null && slow != null ->
                    step.copy(targetType = 0, targetValue = 0,
                        low = (1_000_000.0 / slow).roundToInt().toLong(),
                        high = (1_000_000.0 / fast).roundToInt().toLong())
                else -> step
            }
        }
    }

    private fun compactRepeats(steps: List<Step>): List<Step> = buildList {
        var index = 0
        while (index < steps.size) {
            val work = steps[index]
            val recovery = steps.getOrNull(index + 1)
            if (work.role != "WORK" || recovery?.role != "RECOVERY") {
                add(work)
                index++
                continue
            }
            val start = size
            add(work)
            add(recovery)
            index += 2
            var count = 1L
            while (steps.getOrNull(index) == work && steps.getOrNull(index + 1) == recovery) {
                index += 2
                count++
            }
            if (count > 1) add(Step("Repeat", 0, REPEAT, start.toLong(), targetValue = count))
        }
    }

    private data class Field(val number: Int, val size: Int, val baseType: Int)

    private fun ByteArrayOutputStream.definition(local: Int, global: Long, vararg fields: Field) {
        write(0x40 or local)
        write(0) // Reserved.
        write(0) // Little endian.
        little(global, 2)
        write(fields.size)
        for (field in fields) {
            write(field.number)
            write(field.size)
            write(field.baseType)
        }
    }

    private fun ByteArrayOutputStream.little(value: Long, bytes: Int) {
        repeat(bytes) { write((value ushr (8 * it)).toInt() and 0xff) }
    }

    private fun asciiName(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^ -~]+"), " ")
        .replace(Regex(" +"), " ")
        .trim().take(NAME_BYTES - 1).trimEnd().ifEmpty { "Running workout" }

    private fun ByteArrayOutputStream.fitString(value: String) {
        val ascii = asciiName(value).toByteArray(Charsets.US_ASCII)
        write(ascii)
        write(ByteArray(NAME_BYTES - ascii.size))
    }

    private fun crc(bytes: ByteArray): Int {
        var crc = 0
        for (byte in bytes) {
            val value = byte.toInt() and 0xff
            crc = (crc ushr 4) xor crcTable[(crc xor value) and 0xf]
            crc = (crc ushr 4) xor crcTable[(crc xor (value ushr 4)) and 0xf]
        }
        return crc
    }
}
