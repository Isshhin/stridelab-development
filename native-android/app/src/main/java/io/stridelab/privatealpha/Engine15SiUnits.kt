package io.stridelab.privatealpha

import java.math.BigDecimal

/**
 * Canonical SI base-unit quantity contracts for Engine 1.5 (L1 hard guards).
 * Rejects NaN, +Infinity, -Infinity, and out-of-domain quantities at ingress,
 * and normalizes IEEE-754 negative zero (-0.0) to +0.0 for deterministic hashing.
 */
internal sealed interface CanonicalSiQuantity {
    val canonicalTypeTag: String
    val siValue: Double

    fun canonicalDecimalString(): String = canonicalDecimalString(siValue)

    fun canonicalToken(): String = "$canonicalTypeTag:${canonicalDecimalString()}"

    companion object {
        internal fun requireFinite(value: Double, label: String): Double {
            require(value.isFinite()) {
                "STRUCTURAL_INPUT_INVALID: $label must be finite, was $value"
            }
            return if (value == 0.0) 0.0 else value
        }

        internal fun requireNonNegative(value: Double, label: String): Double {
            val clean = requireFinite(value, label)
            require(clean >= 0.0) {
                "STRUCTURAL_INPUT_INVALID: $label must be >= 0.0, was $clean"
            }
            return clean
        }

        internal fun requirePositive(value: Double, label: String): Double {
            val clean = requireFinite(value, label)
            require(clean > 0.0) {
                "STRUCTURAL_INPUT_INVALID: $label must be > 0.0, was $clean"
            }
            return clean
        }

        internal fun canonicalDecimalString(value: Double): String {
            val clean = requireFinite(value, "quantity")
            return BigDecimal.valueOf(clean).stripTrailingZeros().toPlainString()
        }
    }
}

internal class Meters private constructor(val value: Double) : CanonicalSiQuantity, Comparable<Meters> {
    override val canonicalTypeTag: String get() = "m"
    override val siValue: Double get() = value

    fun toKilometers(): Double = value / 1000.0

    override fun compareTo(other: Meters): Int = value.compareTo(other.value)
    override fun equals(other: Any?): Boolean = other is Meters && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "${canonicalDecimalString()} m"

    companion object {
        val ZERO: Meters = Meters(0.0)

        fun of(meters: Double): Meters =
            Meters(CanonicalSiQuantity.requireNonNegative(meters, "meters"))

        fun fromKilometers(km: Double): Meters {
            val cleanKm = CanonicalSiQuantity.requireNonNegative(km, "kilometers")
            return Meters(CanonicalSiQuantity.requireNonNegative(cleanKm * 1000.0, "meters"))
        }
    }
}

internal class Seconds private constructor(val value: Double) : CanonicalSiQuantity, Comparable<Seconds> {
    override val canonicalTypeTag: String get() = "s"
    override val siValue: Double get() = value

    override fun compareTo(other: Seconds): Int = value.compareTo(other.value)
    override fun equals(other: Any?): Boolean = other is Seconds && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "${canonicalDecimalString()} s"

    companion object {
        val ZERO: Seconds = Seconds(0.0)

        fun of(seconds: Double): Seconds =
            Seconds(CanonicalSiQuantity.requireNonNegative(seconds, "seconds"))

        fun fromMinutes(minutes: Double): Seconds {
            val cleanMin = CanonicalSiQuantity.requireNonNegative(minutes, "minutes")
            return Seconds(CanonicalSiQuantity.requireNonNegative(cleanMin * 60.0, "seconds"))
        }
    }
}

internal class Kilograms private constructor(val value: Double) : CanonicalSiQuantity, Comparable<Kilograms> {
    override val canonicalTypeTag: String get() = "kg"
    override val siValue: Double get() = value

    override fun compareTo(other: Kilograms): Int = value.compareTo(other.value)
    override fun equals(other: Any?): Boolean = other is Kilograms && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "${canonicalDecimalString()} kg"

    companion object {
        val ZERO: Kilograms = Kilograms(0.0)

        fun of(kg: Double): Kilograms =
            Kilograms(CanonicalSiQuantity.requireNonNegative(kg, "kilograms"))
    }
}

internal class MetersPerSecond private constructor(val value: Double) : CanonicalSiQuantity, Comparable<MetersPerSecond> {
    override val canonicalTypeTag: String get() = "m/s"
    override val siValue: Double get() = value

    fun toPaceSecondsPerKm(): Double = 1000.0 / value

    override fun compareTo(other: MetersPerSecond): Int = value.compareTo(other.value)
    override fun equals(other: Any?): Boolean = other is MetersPerSecond && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "${canonicalDecimalString()} m/s"

    companion object {
        fun of(mps: Double): MetersPerSecond =
            MetersPerSecond(CanonicalSiQuantity.requirePositive(mps, "metersPerSecond"))

        fun fromPaceSecondsPerKm(secPerKm: Double): MetersPerSecond {
            val cleanPace = CanonicalSiQuantity.requirePositive(secPerKm, "paceSecondsPerKm")
            return MetersPerSecond(CanonicalSiQuantity.requirePositive(1000.0 / cleanPace, "metersPerSecond"))
        }
    }
}

internal class BeatsPerSecond private constructor(val value: Double) : CanonicalSiQuantity, Comparable<BeatsPerSecond> {
    override val canonicalTypeTag: String get() = "beat/s"
    override val siValue: Double get() = value

    fun toBpm(): Double = value * 60.0

    override fun compareTo(other: BeatsPerSecond): Int = value.compareTo(other.value)
    override fun equals(other: Any?): Boolean = other is BeatsPerSecond && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "${canonicalDecimalString()} beat/s"

    companion object {
        fun of(bps: Double): BeatsPerSecond =
            BeatsPerSecond(CanonicalSiQuantity.requirePositive(bps, "beatsPerSecond"))

        fun fromBpm(bpm: Double): BeatsPerSecond {
            val cleanBpm = CanonicalSiQuantity.requirePositive(bpm, "bpm")
            require(cleanBpm in 20.0..260.0) {
                "STRUCTURAL_INPUT_INVALID: bpm must be in [20, 260], was $cleanBpm"
            }
            return BeatsPerSecond(cleanBpm / 60.0)
        }
    }
}

internal class DimensionlessRatio private constructor(val value: Double) : CanonicalSiQuantity, Comparable<DimensionlessRatio> {
    override val canonicalTypeTag: String get() = "1"
    override val siValue: Double get() = value

    override fun compareTo(other: DimensionlessRatio): Int = value.compareTo(other.value)
    override fun equals(other: Any?): Boolean = other is DimensionlessRatio && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = canonicalDecimalString()

    companion object {
        fun of(ratio: Double): DimensionlessRatio =
            DimensionlessRatio(CanonicalSiQuantity.requireFinite(ratio, "dimensionlessRatio"))

        fun nonNegative(ratio: Double): DimensionlessRatio =
            DimensionlessRatio(CanonicalSiQuantity.requireNonNegative(ratio, "dimensionlessRatio"))
    }
}
