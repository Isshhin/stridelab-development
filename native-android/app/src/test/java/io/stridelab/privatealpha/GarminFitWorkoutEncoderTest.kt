package io.stridelab.privatealpha

import com.garmin.fit.Decode
import com.garmin.fit.FileIdMesg
import com.garmin.fit.Intensity
import com.garmin.fit.Mesg
import com.garmin.fit.MesgListener
import com.garmin.fit.MesgNum
import com.garmin.fit.Sport
import com.garmin.fit.WktStepDuration
import com.garmin.fit.WktStepTarget
import com.garmin.fit.WorkoutMesg
import com.garmin.fit.WorkoutStepMesg
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.LocalDate

class GarminFitWorkoutEncoderTest {
    private val date = Instant.parse("2026-11-23T00:00:00Z")
    private val warmup = RunningStepV2("WARM_UP", "easy", seconds = 600)
    private val interval = RunningStepV2("WORK", "fast", meters = 1_000,
        targetPaceMinSecPerKm = 250, targetPaceMaxSecPerKm = 300,
        targetHrMinBpm = 155, targetHrMaxBpm = 170)
    private val recovery = RunningStepV2("RECOVERY", "easy", meters = 200,
        targetHrMinBpm = 115, targetHrMaxBpm = 135)
    private val cooldown = RunningStepV2("COOL_DOWN", "easy", seconds = 480)
    private val prescription = RunningPrescription("QUALITY", "Intervals", emptyList(),
        mixedSegments = listOf(warmup) + List(4) { listOf(interval, recovery) }.flatten() + cooldown,
        estimatedDistanceKm = 8.0)

    @Test fun writesFitHeaderAndBothChecksums() {
        val bytes = GarminFitWorkoutEncoder.encode("Séance 🏃", prescription, date, serialNumber = 123)
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(14, header.get().toInt())
        assertEquals(0x20, header.get().toInt())
        assertEquals(2100, header.short.toInt())
        assertEquals(bytes.size - 16, header.int)
        assertEquals(".FIT", String(bytes, 8, 4, Charsets.US_ASCII))
        assertEquals(crc(bytes, 12), header.getShort(12).toInt() and 0xffff)
        assertEquals(crc(bytes, bytes.size - 2), header.getShort(bytes.size - 2).toInt() and 0xffff)
        assertTrue(Decode().checkFileIntegrity(ByteArrayInputStream(bytes)))
        val messages = decode(bytes)
        val id = FileIdMesg(messages.first())
        assertEquals(com.garmin.fit.File.WORKOUT, id.type)
        assertEquals(255, id.manufacturer.toInt())
        assertEquals(123L, id.serialNumber.toLong())
        assertEquals(date.epochSecond - Instant.parse("1989-12-31T00:00:00Z").epochSecond,
            id.timeCreated.timestamp.toLong())
        assertEquals("Seance", WorkoutMesg(messages[1]).wktName)
        assertEquals("Seance.fit", GarminFitWorkoutEncoder.fileName("Séance 🏃"))
    }

    @Test fun encodesFourIntervalsWithZeroBasedRepeatIndexAndGarminIntensity() {
        val messages = decode(GarminFitWorkoutEncoder.encode("4000m intervals", prescription, date))
        val workout = WorkoutMesg(messages.single { it.num == MesgNum.WORKOUT })
        val steps = messages.steps()
        assertEquals(Sport.RUNNING, workout.sport)
        assertEquals(5, workout.numValidSteps.toInt())
        assertEquals((0..4).toList(), steps.map { it.messageIndex })
        assertEquals(listOf(Intensity.WARMUP, Intensity.ACTIVE, Intensity.RECOVERY,
            Intensity.ACTIVE, Intensity.COOLDOWN), steps.map { it.intensity })
        assertEquals(600_000L, steps[0].durationValue.toLong())
        assertEquals(100_000L, steps[1].durationValue.toLong())
        assertEquals(20_000L, steps[2].durationValue.toLong())
        assertEquals(WktStepDuration.REPEAT_UNTIL_STEPS_CMPLT, steps[3].durationType)
        assertEquals(1L, steps[3].durationStep.toLong())
        assertEquals(4L, steps[3].targetValue.toLong())
        assertEquals(WktStepTarget.OPEN, steps[3].targetType)
        assertEquals(480_000L, steps[4].durationValue.toLong())
    }

    @Test fun unverifiedHrFallsBackToOrderedSpeedOrOpenTargets() {
        val steps = decode(GarminFitWorkoutEncoder.encode("Intervals", prescription, date)).steps()
        assertFalse(steps.any { it.targetType == WktStepTarget.HEART_RATE })
        assertEquals(WktStepTarget.SPEED, steps[1].targetType)
        assertEquals(3333L, steps[1].customTargetValueLow.toLong())
        assertEquals(4000L, steps[1].customTargetValueHigh.toLong())
        assertEquals(WktStepTarget.OPEN, steps[2].targetType)
        assertNull(steps[2].customTargetValueLow)
        assertNull(steps[2].customTargetValueHigh)
    }

    @Test fun verifiedHrUsesAbsoluteBpmOffsets() {
        val zones = ExecutionHrZoneProfile(95, 115, 135, 155, 175, 195)
        val steps = decode(GarminFitWorkoutEncoder.encode("Intervals", prescription, date, zones)).steps()
        assertEquals(WktStepTarget.HEART_RATE, steps[1].targetType)
        assertEquals(255L, steps[1].customTargetHeartRateLow.toLong())
        assertEquals(270L, steps[1].customTargetHeartRateHigh.toLong())
        assertEquals(WktStepTarget.OPEN, steps[0].targetType)
    }

    @Test fun reviewedTimeOnlyWorkoutDoesNotExportOriginalIntervalsOrDistanceEstimate() {
        val session = PlannedSession("run", LocalDate.parse("2026-11-23"), "run", "Intervals",
            45, null, "quality", quality = true, runningPrescription = prescription)
        val target = Engine15SessionPresentation.Target(
            Engine15SessionPresentation.State.ACCEPTED_TIME_ONLY, 25, null, emptyList(), 45, null, null
        )
        val steps = decode(encodeRunningWorkoutForWatch(session.title, session, target, date, null)).steps()
        assertEquals(WktStepDuration.TIME, steps.single().durationType)
        assertEquals(1_500_000L, steps.single().durationValue.toLong())
        assertEquals(WktStepTarget.OPEN, steps.single().targetType)
    }

    @Test fun openDurationUsesTheLapButton() {
        val steps = decode(GarminFitWorkoutEncoder.encode("Open run", null, date)).steps()
        assertEquals(WktStepDuration.OPEN, steps.single().durationType)
    }

    @Test fun differingRecoveriesAndAnUnpairedFinalIntervalKeepTheirOriginalOrder() {
        val shorterRecovery = recovery.copy(meters = 100)
        val uneven = prescription.copy(mixedSegments =
            listOf(warmup, interval, recovery, interval, shorterRecovery, interval, cooldown))
        val steps = decode(GarminFitWorkoutEncoder.encode("Uneven intervals", uneven, date)).steps()
        assertEquals(listOf(600_000L, 100_000L, 20_000L, 100_000L, 10_000L, 100_000L, 480_000L),
            steps.map { it.durationValue })
        assertFalse(steps.any { it.durationType == WktStepDuration.REPEAT_UNTIL_STEPS_CMPLT })
    }

    private fun decode(bytes: ByteArray): List<Mesg> {
        val messages = mutableListOf<Mesg>()
        val decoder = Decode()
        decoder.addListener(MesgListener { messages.add(it) })
        assertTrue(decoder.read(ByteArrayInputStream(bytes)))
        return messages
    }

    private fun List<Mesg>.steps() = filter { it.num == MesgNum.WORKOUT_STEP }.map(::WorkoutStepMesg)

    // Independent bitwise verification of the encoder's nibble-table CRC.
    private fun crc(bytes: ByteArray, count: Int): Int {
        var value = 0
        for (index in 0 until count) {
            value = value xor (bytes[index].toInt() and 0xff)
            repeat(8) { value = (value ushr 1) xor (if (value and 1 != 0) 0xa001 else 0) }
        }
        return value
    }
}
