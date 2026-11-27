package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test

class FreeformWeightUnitTest {

    @Test
    fun kgToLbAndDisplayFormattingAreAccurate() {
        assertEquals("132.3", FreeformWeightConverter.kgStringToDisplay("60", FreeformWeightUnit.LB))
        assertEquals("220.5", FreeformWeightConverter.kgStringToDisplay("100", FreeformWeightUnit.LB))
        assertEquals("60", FreeformWeightConverter.kgStringToDisplay("60", FreeformWeightUnit.KG))
        assertEquals("", FreeformWeightConverter.kgStringToDisplay("", FreeformWeightUnit.LB))
        assertEquals("", FreeformWeightConverter.kgStringToDisplay("   ", FreeformWeightUnit.LB))
    }

    @Test
    fun lbInputConvertsToCanonicalKgAndRoundTripsCleanly() {
        val canonicalKg = FreeformWeightConverter.displayStringToCanonicalKg("135", FreeformWeightUnit.LB)
        assertEquals(61.23, canonicalKg.toDouble(), 0.01)

        val roundTrippedLb = FreeformWeightConverter.kgStringToDisplay(canonicalKg, FreeformWeightUnit.LB)
        assertEquals(135.0, roundTrippedLb.toDouble(), 0.1)

        assertEquals("42.5", FreeformWeightConverter.displayStringToCanonicalKg("42.5", FreeformWeightUnit.KG))
        assertEquals("", FreeformWeightConverter.displayStringToCanonicalKg("", FreeformWeightUnit.LB))
        assertEquals("abc", FreeformWeightConverter.displayStringToCanonicalKg("abc", FreeformWeightUnit.LB))
    }

    @Test
    fun measureLabelsAndVolumesReflectSelectedUnit() {
        val measure = FreeformMeasure(quantity = "8", kg = "60")
        assertEquals("8 reps · 60 kg", FreeformWeightConverter.formatMeasureLabel(measure, ManualDiscipline.STRENGTH, FreeformWeightUnit.KG))
        assertEquals("8 reps · 132.3 lb", FreeformWeightConverter.formatMeasureLabel(measure, ManualDiscipline.STRENGTH, FreeformWeightUnit.LB))

        assertEquals("480 kg", FreeformWeightConverter.formatVolume(480.0, FreeformWeightUnit.KG))
        assertEquals("1058.2 lb", FreeformWeightConverter.formatVolume(480.0, FreeformWeightUnit.LB))
    }
}
