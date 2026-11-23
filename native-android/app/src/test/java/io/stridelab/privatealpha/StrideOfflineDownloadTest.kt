package io.stridelab.privatealpha

import org.junit.Assert.*
import org.junit.Test

class StrideOfflineDownloadTest {
    @Test fun progressStartsUnknownAndReaches100OnlyWhenComplete() {
        val start = OfflineMapProgress(0, 0)
        val halfway = OfflineMapProgress(5, 128 * 1024)
        assertNull(start.percent(1, precise = false, complete = false))
        assertTrue(halfway.advancedFrom(start))
        assertFalse(halfway.advancedFrom(halfway))
        assertEquals(50, halfway.percent(10, precise = true, complete = false))
        assertEquals(99, halfway.percent(5, precise = true, complete = false))
        assertEquals(100, halfway.percent(5, precise = true, complete = true))
    }

    @Test fun progressResetsStallDeadlineAndErrorsShowActionableCopy() {
        assertFalse(offlineMapStalled(59_999, 0))
        assertTrue(offlineMapStalled(60_000, 0))
        assertFalse(offlineMapStalled(60_000, 59_000))
        assertTrue(offlineMapError("REASON_CONNECTION", null).contains("Wi-Fi and retry"))
        assertTrue(offlineMapError("Other", "HTTP 429").contains("limiting requests"))
        assertTrue(offlineMapError("REASON_NOT_FOUND", null).contains("missing"))
        assertTrue(offlineMapError("REASON_SERVER", null).contains("later"))
    }
}
