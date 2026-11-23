package io.stridelab.privatealpha

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.geometry.LatLngBounds

/** Synthetic geographic coordinates only; no network, location permission or tile download. */
@RunWith(AndroidJUnit4::class)
class StrideOfflineCoverageAndroidTest {
    private val bounds = LatLngBounds.from(14.7, 121.2, 14.4, 120.8)
    private val saved = StrideSavedMap(1, "Test", bounds, true, 100, 10240)
    private val route = RecordedGpsRoute(listOf(listOf(
        RecordedGpsPoint(14.55,121.02,0.0),
        RecordedGpsPoint(14.56,121.03,30.0)
    )))

    @Test fun completeSavedAreaContainsTrimmedRecordedRoute() {
        assertTrue(StrideOfflineCoverage.covers(listOf(saved), route))
    }

    @Test fun partialDownloadAndEmptyAreasNeverPretendToContainBasemap() {
        assertFalse(StrideOfflineCoverage.covers(
            listOf(saved.copy(completed=false, progress=95)), route))
        assertFalse(StrideOfflineCoverage.covers(emptyList(), route))
        assertFalse(StrideOfflineCoverage.covers(listOf(saved), RecordedGpsRoute(emptyList())))
    }

    @Test fun entireRouteMustFitOneArea() {
        val extended = RecordedGpsRoute(listOf(listOf(
            RecordedGpsPoint(14.55,121.02,0.0),
            RecordedGpsPoint(14.56,121.03,30.0),
            RecordedGpsPoint(14.90,121.04,60.0)
        )))
        assertFalse(StrideOfflineCoverage.covers(listOf(saved), extended))
    }

    @Test fun missingGeographicContextCannotBeMadeUpFromTwoPartialRegions() {
        val left = saved.copy(bounds=LatLngBounds.from(14.7,121.025,14.4,120.8))
        val right = saved.copy(id=2,bounds=LatLngBounds.from(14.7,121.2,14.4,121.026))
        assertFalse(StrideOfflineCoverage.covers(listOf(left,right), route))
    }
}
