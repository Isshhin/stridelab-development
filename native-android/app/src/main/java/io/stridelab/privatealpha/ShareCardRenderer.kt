package io.stridelab.privatealpha

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.*

internal object ShareCardPalette {
    const val LIME: Int = 0xFFD8FB70.toInt()
    const val WHITE: Int = 0xFFF7F7F5.toInt()
    const val MUTED: Int = 0xFF9FA4AA.toInt()
    const val BLACK: Int = 0xFF080B0A.toInt()
    const val HAIRLINE: Int = 0xFF23282E.toInt()
    const val SURFACE: Int = 0xFF121212.toInt()
    const val SURFACE_PHOTO: Int = 0xCC121212.toInt()

    fun colors(mode: ShareRouteMode): IntArray = when (mode) {
        ShareRouteMode.PACE -> intArrayOf(LIME, 0xFFFFBE7A.toInt(), 0xFFFF8C87.toInt())
        ShareRouteMode.ELEVATION -> intArrayOf(0xFF5EABD6.toInt(), 0xFFC2A8FF.toInt(), 0xFFFFBE7A.toInt())
        ShareRouteMode.HR -> intArrayOf(0xFF79C9B3.toInt(), LIME, 0xFFFF8C87.toInt())
        else -> intArrayOf(LIME, LIME, LIME)
    }
}

internal data class ShareCardGeometry(val route: RectF, val statsTop: Float, val rowHeight: Float)

/** One fixed-resolution graphic for preview AND export. Editor font scaling stays independent. */
internal object ShareCardRenderer {
    private val numeric = ShareMetric.entries.filter { it !in setOf(ShareMetric.TITLE, ShareMetric.DATE, ShareMetric.LOCATION) }

    fun geometry(state: ShareStudioState): ShareCardGeometry {
        val h = state.ratio.height.toFloat()
        val count = state.metrics.count { it in numeric }.coerceAtLeast(1)
        val row = if (state.layout == ShareLayout.COMPACT) 138f else 158f
        val statsHeight = ceil(count / 2.0).toFloat() * row
        val base = when (state.template) {
            ShareTemplate.STATS -> ShareCardGeometry(RectF(56f, 280f + statsHeight, 1024f, h - 104f), 250f, row)
            ShareTemplate.ROUTE -> ShareCardGeometry(RectF(56f, 246f, 1024f, h - statsHeight - 140f), h - statsHeight - 102f, row)
            ShareTemplate.MINIMAL -> ShareCardGeometry(RectF(120f, 270f, 960f, h - statsHeight - 170f), h - statsHeight - 112f, row)
            ShareTemplate.WORKOUT -> ShareCardGeometry(RectF(56f, 270f, 1024f, h - statsHeight - 138f), h - statsHeight - 112f, row)
        }
        // Bounded route-vs-stat split. Keep at least 152 px of map height,
        // the template's original separation, and labels above the brand footer.
        // Stats-first and workout cards keep their established safe geometry.
        if (state.template !in setOf(ShareTemplate.ROUTE, ShareTemplate.MINIMAL) ||
            state.routeMode == ShareRouteMode.HIDDEN
        ) return base
        val rows = ceil(count / 2.0).toFloat()
        val gap = if (state.template == ShareTemplate.MINIMAL) 58f else 38f
        val minStatsTop = base.route.top + 152f + gap
        val maxStatsTop = h - 200f - (rows - 1f) * row
        if (minStatsTop > maxStatsTop) return base
        val desired = base.statsTop + (state.routeBalance - 50) * 1.5f
        val statsTop = desired.coerceIn(minStatsTop, maxStatsTop)
        return base.copy(
            route = RectF(base.route.left, base.route.top, base.route.right, statsTop - gap),
            statsTop = statsTop
        )
    }

    fun render(activity: ShareRenderableActivity, state: ShareStudioState, map: Bitmap? = null, photo: Bitmap? = null): Bitmap {
        val bitmap = Bitmap.createBitmap(1080, state.ratio.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val g = geometry(state)
        val usePhotoOrTransparentShadow =
            state.background == ShareBackground.PHOTO || state.background == ShareBackground.TRANSPARENT

        when (state.background) {
            ShareBackground.TRANSPARENT -> Unit
            ShareBackground.DARK, ShareBackground.MAP -> canvas.drawColor(ShareCardPalette.BLACK)
            ShareBackground.GRADIENT -> {
                canvas.drawColor(ShareCardPalette.BLACK)
                paint.shader = LinearGradient(
                    0f, 0f, 1080f, bitmap.height.toFloat(),
                    intArrayOf(0xFF13221C.toInt(), ShareCardPalette.BLACK, 0xFF181426.toInt()),
                    floatArrayOf(0f, 0.54f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawPaint(paint)
                paint.shader = RadialGradient(
                    220f, 180f, 760f,
                    intArrayOf(0x22D8FB70, 0x0013221C),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawPaint(paint)
                paint.shader = null
            }
            ShareBackground.PHOTO -> {
                canvas.drawColor(ShareCardPalette.BLACK)
                if (photo != null && photo.width > 0 && photo.height > 0) {
                    // Center-crop to the chosen story ratio. Never stretch a personal photo.
                    val targetRatio = bitmap.width.toFloat() / bitmap.height
                    val sourceRatio = photo.width.toFloat() / photo.height
                    val crop = if (sourceRatio > targetRatio) {
                        val w = (photo.height * targetRatio).toInt().coerceIn(1, photo.width)
                        Rect((photo.width - w) / 2, 0, (photo.width + w) / 2, photo.height)
                    } else {
                        val h = (photo.width / targetRatio).toInt().coerceIn(1, photo.height)
                        Rect(0, (photo.height - h) / 2, photo.width, (photo.height + h) / 2)
                    }
                    paint.alpha = 255
                    paint.isFilterBitmap = true
                    canvas.drawBitmap(photo, crop, Rect(0, 0, bitmap.width, bitmap.height), paint)
                    paint.isFilterBitmap = false
                    // Subtle veil preserves photo vividness while boosting mid-frame readability.
                    paint.color = 0x16000000
                    canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
                    // Reset paint alpha before applying LinearGradient shaders; setting paint.color above lowers alpha.
                    paint.alpha = 255
                    // Directional scrims anchor editorial header and footer metrics without boxy containers.
                    paint.shader = LinearGradient(
                        0f, 0f, 0f, 300f,
                        intArrayOf(0xC8000000.toInt(), 0x66000000, 0x00000000),
                        floatArrayOf(0f, 0.58f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(0f, 0f, bitmap.width.toFloat(), 300f, paint)
                    val bottomScrimTop = (g.statsTop - 90f).coerceAtLeast(bitmap.height * 0.46f)
                    paint.shader = LinearGradient(
                        0f, bottomScrimTop, 0f, bitmap.height.toFloat(),
                        intArrayOf(0x00000000, 0x99000000.toInt(), 0xE0000000.toInt()),
                        floatArrayOf(0f, 0.45f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(0f, bottomScrimTop, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
                    paint.shader = null
                }
            }
        }

        fun text(
            value: String,
            x: Float,
            y: Float,
            size: Float,
            color: Int = ShareCardPalette.WHITE,
            bold: Boolean = false,
            maxWidth: Float = 968f,
            letterSpacing: Float = 0f,
            shadow: Boolean = false,
            alignRight: Boolean = false
        ) {
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = color
            paint.alpha = Color.alpha(color)
            paint.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            paint.fontFeatureSettings = "tnum"
            paint.letterSpacing = letterSpacing
            paint.textSize = size
            paint.textAlign = if (alignRight) Paint.Align.RIGHT else Paint.Align.LEFT
            if (shadow) paint.setShadowLayer(6f, 0f, 2f, 0x99000000.toInt()) else paint.clearShadowLayer()
            val measured = paint.measureText(value)
            if (measured > maxWidth && measured > 0f) {
                paint.textSize = (size * maxWidth / measured).coerceAtLeast(22f)
            }
            canvas.drawText(value, x, y, paint)
            paint.textAlign = Paint.Align.LEFT
            paint.letterSpacing = 0f
            paint.clearShadowLayer()
        }

        val isMinimal = state.template == ShareTemplate.MINIMAL
        val headerX = if (isMinimal) 120f else 56f
        val headerMaxWidth = if (isMinimal) 840f else 968f
        val eyebrow = activity.activityType.uppercase(java.util.Locale.getDefault())
        if (!isMinimal) {
            paint.style = Paint.Style.FILL
            paint.color = ShareCardPalette.LIME
            canvas.drawRoundRect(headerX, 54f, headerX + 18f, 57f, 2f, 2f, paint)
            text(
                value = eyebrow,
                x = headerX + 28f,
                y = 64f,
                size = 22f,
                color = ShareCardPalette.LIME,
                bold = true,
                maxWidth = headerMaxWidth - 28f,
                letterSpacing = 0.14f,
                shadow = usePhotoOrTransparentShadow
            )
        } else {
            text(
                value = eyebrow,
                x = headerX,
                y = 66f,
                size = 20f,
                color = ShareCardPalette.LIME,
                bold = true,
                maxWidth = headerMaxWidth,
                letterSpacing = 0.16f,
                shadow = usePhotoOrTransparentShadow
            )
        }

        if (ShareMetric.TITLE in state.metrics) {
            val title = activity.metrics[ShareMetric.TITLE].orEmpty()
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ShareCardPalette.WHITE
                textSize = 56f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                fontFeatureSettings = "tnum"
                if (usePhotoOrTransparentShadow) setShadowLayer(6f, 0f, 2f, 0x99000000.toInt())
            }
            val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, tp, headerMaxWidth.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 0.96f)
                .setMaxLines(2)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .build()
            canvas.save()
            canvas.translate(headerX, 86f)
            titleLayout.draw(canvas)
            canvas.restore()
        }

        val subtitle = listOfNotNull(
            activity.metrics[ShareMetric.DATE]?.takeIf { ShareMetric.DATE in state.metrics && it.isNotBlank() },
            activity.metrics[ShareMetric.LOCATION]?.takeIf { ShareMetric.LOCATION in state.metrics && it.isNotBlank() }
        ).joinToString(" · ")
        if (subtitle.isNotBlank()) {
            text(
                value = subtitle,
                x = headerX,
                y = 228f,
                size = 26f,
                color = ShareCardPalette.MUTED,
                maxWidth = headerMaxWidth,
                shadow = usePhotoOrTransparentShadow
            )
        }

        if (state.template == ShareTemplate.WORKOUT && g.route.height() > 140f) {
            val r = g.route
            val recap = activity.workout
            paint.style = Paint.Style.FILL
            paint.color = if (state.background == ShareBackground.PHOTO) ShareCardPalette.SURFACE_PHOTO else ShareCardPalette.SURFACE
            canvas.drawRoundRect(r, 28f, 28f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            paint.color = ShareCardPalette.HAIRLINE
            canvas.drawRoundRect(r, 28f, 28f, paint)
            paint.style = Paint.Style.FILL
            paint.color = ShareCardPalette.LIME
            canvas.drawRoundRect(r.left + 28f, r.top + 18f, r.left + 92f, r.top + 22f, 2f, 2f, paint)

            val x = r.left + 28f
            text("WORKOUT RECAP", x, r.top + 48f, 22f, ShareCardPalette.LIME, bold = true, maxWidth = r.width() - 56f, letterSpacing = 0.14f)
            text(recap?.label ?: "Recorded laps", x, r.top + 104f, 46f, ShareCardPalette.WHITE, bold = true, maxWidth = r.width() - 56f)
            val provenance = recap?.provenance?.label ?: "Unclassified recorded laps"
            val origin = recap?.recordingSource ?: "Activity"
            text("$provenance / $origin", x, r.top + 140f, 22f, ShareCardPalette.MUTED, maxWidth = r.width() - 56f)
            text(
                "Laps below are not verified WORK/RECOVERY intervals",
                x,
                r.top + 170f,
                20f,
                ShareCardPalette.MUTED,
                maxWidth = r.width() - 56f
            )
            val visibleRows = ((r.height() - 210f) / 70f).toInt().coerceIn(0, 4)
            recap?.laps?.take(visibleRows)?.forEachIndexed { index, lap ->
                val y = r.top + 216f + index * 70f
                if (index > 0) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 1f
                    paint.color = ShareCardPalette.HAIRLINE
                    canvas.drawLine(x, y - 42f, r.right - 28f, y - 42f, paint)
                    paint.style = Paint.Style.FILL
                }
                text("LAP ${lap.index}", x, y, 22f, ShareCardPalette.LIME, bold = true, maxWidth = 135f, letterSpacing = 0.08f)
                val summary = listOfNotNull(
                    lap.distance,
                    lap.duration,
                    lap.pace,
                    lap.heartRate.takeIf { ShareMetric.HR in state.metrics }
                ).joinToString(" / ").ifBlank { "Not recorded" }
                text(summary, x + 158f, y, 23f, ShareCardPalette.WHITE, maxWidth = r.width() - 220f)
            }
            if (recap != null && recap.totalLaps > visibleRows) {
                text(
                    "Showing ${minOf(visibleRows, recap.laps.size)} of ${recap.totalLaps} recorded laps",
                    x,
                    r.bottom - 14f,
                    19f,
                    ShareCardPalette.MUTED,
                    maxWidth = r.width() - 56f
                )
            }
        } else if (state.routeMode != ShareRouteMode.HIDDEN && g.route.height() > 0f) {
            canvas.save()
            // Keep the snapshot's native logo and attribution fully visible at its edges.
            canvas.clipRect(g.route)
            if (map != null && state.background == ShareBackground.MAP) {
                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
                paint.alpha = 255
                canvas.drawBitmap(map, null, g.route, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.5f
                paint.color = ShareCardPalette.HAIRLINE
                canvas.drawRect(g.route, paint)
                paint.style = Paint.Style.FILL
            } else {
                drawCoordinateRoute(canvas, g.route, activity, state)
            }
            canvas.restore()
        }

        val fields = state.visibleNumericMetrics(activity.metrics.keys)
        val statsTop = if (state.routeMode == ShareRouteMode.HIDDEN &&
            state.template in setOf(ShareTemplate.ROUTE, ShareTemplate.MINIMAL)
        ) {
            val gridHeight = ceil(fields.size / 2.0).toFloat() * g.rowHeight
            270f + ((bitmap.height - 370f - gridHeight) / 2f).coerceAtLeast(0f)
        } else {
            g.statsTop
        }

        val metricColLeft = if (isMinimal) 120f else 56f
        val metricColStep = if (isMinimal) 440f else 500f
        val metricMaxWidth = if (isMinimal) 400f else 456f

        if (state.template == ShareTemplate.STATS &&
            fields.size > 1 &&
            state.background in setOf(ShareBackground.DARK, ShareBackground.GRADIENT)
        ) {
            val dividerBottom = (statsTop + ceil(fields.size / 2.0).toFloat() * g.rowHeight - 20f)
                .coerceAtMost(bitmap.height - 96f)
            if (dividerBottom > statsTop + 20f) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                paint.color = ShareCardPalette.HAIRLINE
                canvas.drawLine(526f, statsTop + 14f, 526f, dividerBottom, paint)
                paint.style = Paint.Style.FILL
            }
        }

        fields.forEachIndexed { i, metric ->
            val col = i % 2
            val x = metricColLeft + col * metricColStep
            val y = statsTop + (i / 2) * g.rowHeight
            val value = activity.metrics.getValue(metric)
            val isHeroStats = state.template == ShareTemplate.STATS && i == 0
            val isPerformanceHero = state.template == ShareTemplate.ROUTE &&
                state.variant == ShareVariant.PERFORMANCE && i == 0

            if (isPerformanceHero) {
                paint.style = Paint.Style.FILL
                paint.color = ShareCardPalette.LIME
                canvas.drawRoundRect(x, y + 18f, x + 4f, y + 72f, 2f, 2f, paint)
            }

            val valueSize = when {
                isHeroStats -> 94f
                isPerformanceHero -> 68f
                state.template == ShareTemplate.STATS -> 58f
                else -> 62f
            }
            val valueColor = if (isHeroStats || isPerformanceHero ||
                (state.variant == ShareVariant.PERFORMANCE && i == 0)
            ) ShareCardPalette.LIME else ShareCardPalette.WHITE

            text(
                value = value,
                x = if (isPerformanceHero) x + 16f else x,
                y = y + 70f,
                size = valueSize,
                color = valueColor,
                bold = true,
                maxWidth = if (isPerformanceHero) metricMaxWidth - 16f else metricMaxWidth,
                letterSpacing = if (isHeroStats) -0.02f else 0f,
                shadow = usePhotoOrTransparentShadow
            )
            text(
                value = metric.label.uppercase(java.util.Locale.getDefault()),
                x = if (isPerformanceHero) x + 16f else x,
                y = y + 110f,
                size = if (isMinimal) 20f else 22f,
                color = ShareCardPalette.MUTED,
                bold = false,
                maxWidth = if (isPerformanceHero) metricMaxWidth - 16f else metricMaxWidth,
                letterSpacing = if (isMinimal) 0.16f else 0.10f,
                shadow = usePhotoOrTransparentShadow
            )
        }

        if (state.background in setOf(ShareBackground.DARK, ShareBackground.GRADIENT)) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = ShareCardPalette.HAIRLINE
            canvas.drawLine(headerX, bitmap.height - 80f, 1080f - headerX, bitmap.height - 80f, paint)
            paint.style = Paint.Style.FILL
        }

        val brandSize = when (state.branding) {
            ShareBranding.MINIMAL -> 22f
            ShareBranding.SUBTLE -> 26f
            ShareBranding.STANDARD -> 32f
        }
        val brandX = if (state.branding == ShareBranding.STANDARD) {
            paint.style = Paint.Style.FILL
            paint.color = ShareCardPalette.LIME
            canvas.drawRect(headerX, bitmap.height - 54f, headerX + 6f, bitmap.height - 48f, paint)
            headerX + 16f
        } else {
            headerX
        }
        text(
            value = "STRIDELAB",
            x = brandX,
            y = bitmap.height - 38f,
            size = brandSize,
            color = ShareCardPalette.MUTED,
            bold = true,
            maxWidth = 440f,
            letterSpacing = 0.18f,
            shadow = usePhotoOrTransparentShadow
        )
        if (state.branding == ShareBranding.STANDARD) {
            text(
                value = "EVERY STRIDE COUNTS",
                x = 1080f - headerX,
                y = bitmap.height - 38f,
                size = 20f,
                color = ShareCardPalette.MUTED,
                maxWidth = 460f,
                letterSpacing = 0.14f,
                shadow = usePhotoOrTransparentShadow,
                alignRight = true
            )
        }
        return bitmap
    }

    /** Same Mercator projection as the map; a coordinate trace is never presented as streets. */
    private fun drawCoordinateRoute(canvas: Canvas, rect: RectF, activity: ShareRenderableActivity, state: ShareStudioState) {
        val points = activity.route.cameraPoints()
        if (points.isEmpty()) return
        fun mx(p: RecordedGpsPoint) = (p.longitude + 180.0) / 360.0
        fun my(p: RecordedGpsPoint) = (1.0 - ln(tan(Math.PI / 4 + Math.toRadians(p.latitude) / 2)) / Math.PI) / 2
        val minX = points.minOf(::mx)
        val maxX = points.maxOf(::mx)
        val minY = points.minOf(::my)
        val maxY = points.maxOf(::my)
        val scale = min(
            (rect.width() - 100) / (maxX - minX).coerceAtLeast(.000001),
            (rect.height() - 100) / (maxY - minY).coerceAtLeast(.000001)
        )
        fun position(p: RecordedGpsPoint) = PointF(
            (rect.centerX() + (mx(p) - (minX + maxX) / 2) * scale).toFloat(),
            (rect.centerY() + (my(p) - (minY + maxY) / 2) * scale).toFloat()
        )

        val coreStroke = if (state.template == ShareTemplate.MINIMAL) 7f else 8.5f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
        }

        val paths = activity.route.segments.mapNotNull { segment ->
            if (segment.isEmpty()) null else Path().apply {
                segment.forEachIndexed { i, p ->
                    val xy = position(p)
                    if (i == 0) moveTo(xy.x, xy.y) else lineTo(xy.x, xy.y)
                }
            } to segment
        }

        // 1. Subtle outer glow around the coordinate trace
        paint.color = ShareCardPalette.LIME
        paint.alpha = 30
        paint.strokeWidth = coreStroke + 11.5f
        paths.forEach { (path, _) -> canvas.drawPath(path, paint) }

        // 2. Dark contrast halo on non-transparent backgrounds so route edges pop cleanly
        if (state.background != ShareBackground.TRANSPARENT) {
            paint.color = ShareCardPalette.BLACK
            paint.alpha = 180
            paint.strokeWidth = coreStroke + 4f
            paths.forEach { (path, _) -> canvas.drawPath(path, paint) }
        }

        // 3. Base coordinate path
        paint.color = ShareCardPalette.LIME
        paint.strokeWidth = coreStroke
        paint.alpha = if (state.routeMode == ShareRouteMode.ROUTE) 255 else 54
        paths.forEach { (path, segment) ->
            canvas.drawPath(path, paint)
            if (segment.size == 1) {
                val xy = position(segment[0])
                canvas.drawPoint(xy.x, xy.y, paint)
            }
        }

        // 4. Metric-colored segments when active
        val analysis = activity.analysis[state.routeMode]
        val low = analysis?.minimum
        if (analysis != null && low != null) {
            val high = (analysis.maximum ?: low).coerceAtLeast(low + .001)
            val colors = ShareCardPalette.colors(state.routeMode)
            paint.alpha = 255
            paint.strokeWidth = coreStroke
            analysis.edges.forEach { edge ->
                val t = ((edge.value - low) / (high - low)).coerceIn(0.0, 1.0) * 2
                val index = if (t < 1) 0 else 1
                paint.color = blend(colors[index], colors[index + 1], (t - index).toFloat())
                val a = position(edge.start)
                val b = position(edge.end)
                canvas.drawLine(a.x, a.y, b.x, b.y, paint)
            }
        }

        // 5. Endpoint markers derived exclusively from the trimmed display segments
        val startPoint = activity.route.segments.firstOrNull()?.firstOrNull()
        val endPoint = activity.route.segments.lastOrNull()?.lastOrNull()
        if (startPoint != null) {
            val xy = position(startPoint)
            paint.style = Paint.Style.FILL
            paint.color = ShareCardPalette.BLACK
            paint.alpha = 230
            canvas.drawCircle(xy.x, xy.y, 7f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = ShareCardPalette.WHITE
            paint.alpha = 255
            canvas.drawCircle(xy.x, xy.y, 7f, paint)
        }
        if (endPoint != null && endPoint != startPoint) {
            val xy = position(endPoint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = ShareCardPalette.BLACK
            paint.alpha = 230
            canvas.drawCircle(xy.x, xy.y, 8f, paint)
            paint.style = Paint.Style.FILL
            paint.color = ShareCardPalette.LIME
            paint.alpha = 255
            canvas.drawCircle(xy.x, xy.y, 6.5f, paint)
        }
    }

    private fun blend(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
    )
}
