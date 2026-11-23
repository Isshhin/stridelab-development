package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test

class OfflineSelectionRectTest {
    @Test fun fullSelectionMatchesPreviouslyApprovedCoordinates() {
        val rect = offlineSelectionRect(1f)
        assertEquals(.11f,rect.left,.0001f)
        assertEquals(.20f,rect.top,.0001f)
        assertEquals(.89f,rect.right,.0001f)
        assertEquals(.80f,rect.bottom,.0001f)
    }

    @Test fun smallerBoxKeepsCenteredSymmetryAndOrderedEdges() {
        val rect = offlineSelectionRect(.3f)
        assertTrue(rect.left < rect.right && rect.top < rect.bottom)
        assertEquals(1f,rect.left + rect.right,.0001f)
        assertEquals(1f,rect.top + rect.bottom,.0001f)
        assertTrue(rect.left > .11f && rect.top > .20f)
        assertTrue(rect.right < .89f && rect.bottom < .80f)
    }

    @Test fun outOfRangeInputIsClampedToSafeSelection() {
        assertEquals(offlineSelectionRect(.3f),offlineSelectionRect(-2f))
        assertEquals(offlineSelectionRect(1f),offlineSelectionRect(2f))
    }
}
