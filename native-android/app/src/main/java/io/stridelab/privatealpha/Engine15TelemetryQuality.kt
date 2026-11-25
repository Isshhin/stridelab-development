package io.stridelab.privatealpha

import java.util.Collections
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class TelemetryQualityStatus {
    VALID_OBSERVED,
    MISSING_TELEMETRY,
    DEGRADED,
    QUARANTINED_CADENCE_LOCK,
    QUARANTINED_UNRELIABLE,
    UNSUPPORTED_OR_UNAVAILABLE_ANALYSIS
}

internal enum class HeartRateSensorModality {
    UNKNOWN,
    OPTICAL_WRIST_PPG,
    CHEST_STRAP_ECG
}

internal class TelemetryQualityAssessment(
    val hrQuality: TelemetryQualityStatus,
    val distanceQuality: TelemetryQualityStatus,
    val durationQuality: TelemetryQualityStatus,
    flags: Set<String> = emptySet()
) {
    val flags: Set<String>

    init {
        val cleaned = flags.map { flag ->
            RecordPolicy.identifier(flag)
            flag
        }.sorted()
        this.flags = Collections.unmodifiableSet(LinkedHashSet(cleaned))
    }

    fun canonicalRepresentation(): String {
        val flagList = "${flags.size}:[${flags.joinToString(",") { AthleteStateSnapshot.canonicalSegment(it) }}]"
        return "hr=${hrQuality.name}|dist=${distanceQuality.name}|dur=${durationQuality.name}|flags=$flagList"
    }

    override fun equals(other: Any?): Boolean =
        other is TelemetryQualityAssessment &&
            hrQuality == other.hrQuality &&
            distanceQuality == other.distanceQuality &&
            durationQuality == other.durationQuality &&
            flags == other.flags

    override fun hashCode(): Int {
        var result = hrQuality.hashCode()
        result = 31 * result + distanceQuality.hashCode()
        result = 31 * result + durationQuality.hashCode()
        result = 31 * result + flags.hashCode()
        return result
    }
}

/**
 * Deterministic Layer-1 telemetry quality classifier applying POL-003..POL-008 strictly
 * within their documented preconditions without mutating or deleting raw observations.
 */
internal object Engine15TelemetryQualityClassifier {

    fun classify(
        record: ActivityRecord,
        detail: ActivityDetail?,
        discipline: AthleteDiscipline,
        hrSensorModality: HeartRateSensorModality = HeartRateSensorModality.UNKNOWN,
        isOutdoorGnss: Boolean? = null
    ): TelemetryQualityAssessment {
        val flags = linkedSetOf<String>()

        val hrQuality = classifyHeartRate(
            record = record,
            detail = detail,
            discipline = discipline,
            hrSensorModality = hrSensorModality,
            flags = flags
        )
        val distanceQuality = classifyDistance(
            record = record,
            detail = detail,
            isOutdoorGnss = isOutdoorGnss,
            flags = flags
        )
        val durationQuality = when {
            record.durationS == null -> TelemetryQualityStatus.MISSING_TELEMETRY
            record.durationS.isFinite() && record.durationS >= 0.0 -> TelemetryQualityStatus.VALID_OBSERVED
            else -> TelemetryQualityStatus.QUARANTINED_UNRELIABLE
        }

        if ((hrQuality == TelemetryQualityStatus.QUARANTINED_CADENCE_LOCK ||
                hrQuality == TelemetryQualityStatus.QUARANTINED_UNRELIABLE) &&
            distanceQuality == TelemetryQualityStatus.VALID_OBSERVED &&
            durationQuality == TelemetryQualityStatus.VALID_OBSERVED
        ) {
            flags += "HR_QUARANTINED_DISTANCE_DURATION_PRESERVED"
        }

        return TelemetryQualityAssessment(
            hrQuality = hrQuality,
            distanceQuality = distanceQuality,
            durationQuality = durationQuality,
            flags = flags
        )
    }

    private fun classifyHeartRate(
        record: ActivityRecord,
        detail: ActivityDetail?,
        discipline: AthleteDiscipline,
        hrSensorModality: HeartRateSensorModality,
        flags: MutableSet<String>
    ): TelemetryQualityStatus {
        val samples = detail?.samples.orEmpty()
        if (samples.isEmpty()) {
            val avg = record.averageHr
            val max = record.maximumHr
            if (avg == null && max == null) {
                return TelemetryQualityStatus.MISSING_TELEMETRY
            }
            if ((avg != null && !isPlausibleHr(avg)) || (max != null && !isPlausibleHr(max))) {
                flags += "HR_SUMMARY_OUT_OF_RANGE"
                return TelemetryQualityStatus.QUARANTINED_UNRELIABLE
            }
            flags += "HR_SUMMARY_ONLY_STREAM_UNAVAILABLE"
            return TelemetryQualityStatus.UNSUPPORTED_OR_UNAVAILABLE_ANALYSIS
        }

        val totalSamples = samples.size
        val hrValues = samples.map { it.hr?.takeIf { hr -> hr.isFinite() && hr > 0.0 } }
        val nonNullHrCount = hrValues.count { it != null }
        if (nonNullHrCount == 0) {
            val avg = record.averageHr
            val max = record.maximumHr
            if (avg == null && max == null) {
                return TelemetryQualityStatus.MISSING_TELEMETRY
            }
            if ((avg != null && !isPlausibleHr(avg)) || (max != null && !isPlausibleHr(max))) {
                flags += "HR_SUMMARY_OUT_OF_RANGE"
                return TelemetryQualityStatus.QUARANTINED_UNRELIABLE
            }
            flags += "HR_SUMMARY_ONLY_STREAM_UNAVAILABLE"
            return TelemetryQualityStatus.UNSUPPORTED_OR_UNAVAILABLE_ANALYSIS
        }

        val outOfRangeCount = hrValues.count { it != null && !isPlausibleHr(it) }
        val outOfRangeFraction = outOfRangeCount.toDouble() / nonNullHrCount.toDouble()
        if (outOfRangeFraction > Engine15M1PolicyRegistry.POL_005_MAX_BAD_HR_SAMPLE_FRACTION) {
            flags += "HR_OUT_OF_RANGE_SAMPLES"
            return TelemetryQualityStatus.QUARANTINED_UNRELIABLE
        }

        val missingSamples = totalSamples - nonNullHrCount
        val maxFlatlineRun = longestFlatlineRun(hrValues)
        val flatlineCount = if (totalSamples >= 4 && maxFlatlineRun >= ceil(0.25 * totalSamples).toInt() && maxFlatlineRun >= 4) {
            maxFlatlineRun
        } else {
            0
        }
        val dropoutOrFlatlineFraction = (missingSamples + flatlineCount).coerceAtMost(totalSamples).toDouble() / totalSamples.toDouble()
        if (dropoutOrFlatlineFraction >= Engine15M1PolicyRegistry.POL_006_MIN_HR_DROPOUT_FRACTION) {
            flags += "HR_DROPOUT_OR_FLATLINE"
            return TelemetryQualityStatus.QUARANTINED_UNRELIABLE
        }

        if (discipline == AthleteDiscipline.RUN) {
            val cadenceLockFraction = computeCadenceLockFraction(samples)
            if (cadenceLockFraction != null && cadenceLockFraction >= Engine15M1PolicyRegistry.POL_004_CADENCE_LOCK_MIN_ACTIVE_FRACTION) {
                return when (hrSensorModality) {
                    HeartRateSensorModality.OPTICAL_WRIST_PPG -> {
                        flags += "HR_CADENCE_LOCK_QUARANTINED"
                        TelemetryQualityStatus.QUARANTINED_CADENCE_LOCK
                    }
                    HeartRateSensorModality.CHEST_STRAP_ECG -> {
                        flags += "HR_CADENCE_PROXIMITY_NON_CONCLUSIVE"
                        TelemetryQualityStatus.VALID_OBSERVED
                    }
                    HeartRateSensorModality.UNKNOWN -> {
                        flags += "HR_CADENCE_PROXIMITY_NON_CONCLUSIVE"
                        flags += "HR_SENSOR_MODALITY_UNKNOWN"
                        TelemetryQualityStatus.UNSUPPORTED_OR_UNAVAILABLE_ANALYSIS
                    }
                }
            }
        }

        return TelemetryQualityStatus.VALID_OBSERVED
    }

    private fun computeCadenceLockFraction(samples: List<ActivitySample>): Double? {
        var pairedDuration = 0.0
        var lockedDuration = 0.0
        var pairedSamples = 0
        var lockedSamples = 0

        for (i in samples.indices) {
            val s = samples[i]
            val hr = s.hr?.takeIf { it.isFinite() && isPlausibleHr(it) } ?: continue
            val cad = (s.cadenceSpm ?: s.runningDynamics?.cadenceSpm)?.takeIf { it.isFinite() && it > 0.0 } ?: continue
            val locked = abs(hr - cad) <= Engine15M1PolicyRegistry.POL_003_CADENCE_LOCK_TOLERANCE_BPM_SPM
            pairedSamples += 1
            if (locked) lockedSamples += 1

            if (i + 1 < samples.size) {
                val dt = samples[i + 1].seconds - s.seconds
                if (dt.isFinite() && dt > 0.0) {
                    pairedDuration += dt
                    if (locked) lockedDuration += dt
                }
            }
        }

        if (pairedSamples == 0) return null
        return if (pairedDuration > 0.0) lockedDuration / pairedDuration else lockedSamples.toDouble() / pairedSamples.toDouble()
    }

    private fun longestFlatlineRun(hrValues: List<Double?>): Int {
        var best = 0
        var current = 0
        var prev: Double? = null
        for (hr in hrValues) {
            if (hr != null && prev != null && hr == prev) {
                current += 1
            } else if (hr != null) {
                current = 1
            } else {
                current = 0
            }
            if (current > best) best = current
            prev = hr
        }
        return best
    }

    private fun classifyDistance(
        record: ActivityRecord,
        detail: ActivityDetail?,
        isOutdoorGnss: Boolean?,
        flags: MutableSet<String>
    ): TelemetryQualityStatus {
        val distanceM = record.distanceM ?: return TelemetryQualityStatus.MISSING_TELEMETRY
        if (!distanceM.isFinite() || distanceM < 0.0) {
            return TelemetryQualityStatus.QUARANTINED_UNRELIABLE
        }

        val samples = detail?.samples.orEmpty()
        val outdoor = isOutdoorGnss ?: samples.any { it.latitude != null && it.longitude != null }
        if (!outdoor || samples.size < 2) {
            return TelemetryQualityStatus.VALID_OBSERVED
        }

        val missingGnss = samples.count { it.latitude == null || it.longitude == null }
        val gnssDropoutFraction = missingGnss.toDouble() / samples.size.toDouble()
        if (gnssDropoutFraction >= Engine15M1PolicyRegistry.POL_008_MIN_GNSS_DROPOUT_FRACTION) {
            flags += "GNSS_DROPOUT"
            return TelemetryQualityStatus.DEGRADED
        }

        var spikeRunSeconds = 0.0
        for (i in 0 until samples.size - 1) {
            val a = samples[i]
            val b = samples[i + 1]
            val dt = b.seconds - a.seconds
            if (!dt.isFinite() || dt <= 0.0) continue

            val speedMps: Double? = if (a.latitude != null && a.longitude != null &&
                b.latitude != null && b.longitude != null
            ) {
                haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude) / dt
            } else {
                null
            }

            if (speedMps != null && speedMps.isFinite() &&
                speedMps > Engine15M1PolicyRegistry.POL_007_MAX_PLAUSIBLE_GNSS_SPEED_MPS
            ) {
                spikeRunSeconds += dt
                if (spikeRunSeconds > Engine15M1PolicyRegistry.POL_007_MIN_SPIKE_DURATION_SECONDS) {
                    flags += "GNSS_SUSTAINED_SPEED_SPIKE"
                    return TelemetryQualityStatus.DEGRADED
                }
            } else {
                spikeRunSeconds = 0.0
            }
        }

        return TelemetryQualityStatus.VALID_OBSERVED
    }

    private fun isPlausibleHr(bpm: Double): Boolean =
        bpm.isFinite() &&
            bpm >= Engine15M1PolicyRegistry.POL_005_MIN_PLAUSIBLE_HR_BPM &&
            bpm <= Engine15M1PolicyRegistry.POL_005_MAX_PLAUSIBLE_HR_BPM

    internal fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusM = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
            cos(rLat1) * cos(rLat2) * sin(dLon / 2.0) * sin(dLon / 2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt((1.0 - a).coerceAtLeast(0.0)))
        return earthRadiusM * c
    }
}
