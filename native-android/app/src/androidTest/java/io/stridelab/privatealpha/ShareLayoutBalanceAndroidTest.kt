package io.stridelab.privatealpha

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.ceil

/** Geometry-only tests; no external photos, cached maps, or user-recorded GPS. */
@RunWith(AndroidJUnit4::class)
class ShareLayoutBalanceAndroidTest {
    private val stats = setOf(ShareMetric.TITLE,ShareMetric.DATE,
        ShareMetric.DISTANCE,ShareMetric.DURATION,ShareMetric.PACE,
        ShareMetric.HR,ShareMetric.ELEVATION)

    @Test fun allRatiosRetainReadableLabelsAndMapHeightAtSliderExtremes() {
        for (ratio in ShareRatio.entries) {
            for (template in listOf(ShareTemplate.ROUTE,ShareTemplate.MINIMAL)) {
                for (layout in ShareLayout.entries) {
                    val base=ShareStudioState(
                        ratio=ratio,template=template,
                        variant=template.variants.first(),
                        metrics=stats,routeMode=ShareRouteMode.ROUTE,layout=layout
                    )
                    val top=ShareCardRenderer.geometry(base.copy(routeBalance=0))
                    val bottom=ShareCardRenderer.geometry(base.copy(routeBalance=100))
                    assertTrue(bottom.statsTop >= top.statsTop)
                    assertTrue(bottom.route.bottom >= top.route.bottom)
                    assertTrue(top.route.height() >= 151.5f)
                    assertTrue(bottom.route.height() >= 151.5f)
                    val rows=ceil(5/2.0).toFloat()
                    for (geometry in listOf(top,bottom)) {
                        assertTrue(geometry.route.bottom < geometry.statsTop)
                        val lastLabel=geometry.statsTop+(rows-1f)*geometry.rowHeight+110f
                        assertTrue("Stats must stay above the StrideLab footer",lastLabel <= ratio.height-89f)
                    }
                }
            }
        }
    }

    @Test fun centeredSliderPreservesExistingDefaultComposition() {
        for (ratio in ShareRatio.entries) {
            val fields=setOf(ShareMetric.TITLE,ShareMetric.DATE,
                ShareMetric.DISTANCE,ShareMetric.DURATION)
            val rows=1f
            for (template in listOf(ShareTemplate.ROUTE,ShareTemplate.MINIMAL)) {
                val original=ShareStudioState(
                    template=template,variant=template.variants.first(),
                    ratio=ratio,metrics=fields,routeMode=ShareRouteMode.ROUTE
                )
                val geometry=ShareCardRenderer.geometry(original)
                val height=ratio.height.toFloat()
                val row=158f
                val statsHeight=rows*row
                val expected=if(template==ShareTemplate.ROUTE)
                    height-statsHeight-140f
                else height-statsHeight-170f
                assertEquals(expected,geometry.route.bottom,.01f)
                assertEquals(geometry,ShareCardRenderer.geometry(original.copy(routeBalance=50)))
            }
        }
    }

    @Test fun statsFirstAndWorkoutTemplatesIgnoreRouteBalance() {
        for (template in listOf(ShareTemplate.STATS,ShareTemplate.WORKOUT)) {
            val state=ShareStudioState(template=template,variant=template.variants.first())
            assertEquals(
                ShareCardRenderer.geometry(state.copy(routeBalance=0)),
                ShareCardRenderer.geometry(state.copy(routeBalance=100))
            )
        }
    }
}
