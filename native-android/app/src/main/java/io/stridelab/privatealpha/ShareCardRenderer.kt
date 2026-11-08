package io.stridelab.privatealpha

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.*

internal object ShareCardPalette {
    const val LIME:Int = 0xFFD8FB70.toInt()
    const val WHITE:Int = 0xFFF0F7F1.toInt()
    const val MUTED:Int = 0xFFBFCFC4.toInt()
    const val BLACK:Int = 0xFF080D0C.toInt()
    fun colors(mode:ShareRouteMode):IntArray = when(mode) {
        ShareRouteMode.PACE -> intArrayOf(LIME,0xFFFFBE7A.toInt(),0xFFFF8C87.toInt())
        ShareRouteMode.ELEVATION -> intArrayOf(0xFF5EABD6.toInt(),0xFFC2A8FF.toInt(),0xFFFFBE7A.toInt())
        ShareRouteMode.HR -> intArrayOf(0xFF79C9B3.toInt(),LIME,0xFFFF8C87.toInt())
        else -> intArrayOf(LIME,LIME,LIME)
    }
}

internal data class ShareCardGeometry(val route:RectF, val statsTop:Float, val rowHeight:Float)

/** One fixed-resolution graphic for preview AND export. Editor font scaling stays independent. */
internal object ShareCardRenderer {
    private val numeric = ShareMetric.entries.filter { it !in setOf(ShareMetric.TITLE,ShareMetric.DATE,ShareMetric.LOCATION) }
    fun geometry(state:ShareStudioState):ShareCardGeometry {
        val h=state.ratio.height.toFloat()
        return when(state.template) {
            ShareTemplate.ROUTE -> ShareCardGeometry(RectF(72f,max(260f,h*.21f),1008f,h*.68f),h*.71f,(h*.91f-h*.71f)/3f)
            ShareTemplate.STATS -> ShareCardGeometry(
                RectF(700f,max(260f,h*.15f),1008f,h*.38f),h*.41f,(h*.90f-h*.41f)/4f)
            ShareTemplate.MINIMAL -> ShareCardGeometry(
                RectF(205f,h*.28f,875f,h*.61f),h*.82f,(h*.94f-h*.82f)/2f)
        }
    }

    fun render(activity:ShareRenderableActivity, state:ShareStudioState, map:Bitmap? = null, width:Int = 1080):Bitmap {
        require(width in 1..1080)
        val bitmap=Bitmap.createBitmap(width,(state.ratio.height.toFloat()*width/1080f).toInt(),Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        canvas.scale(width/1080f,width/1080f)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        if(!state.transparent) canvas.drawColor(ShareCardPalette.BLACK)
        if(!state.transparent && state.background==ShareBackground.GRADIENT) {
            paint.shader=LinearGradient(0f,0f,1080f,state.ratio.height.toFloat(),
                intArrayOf(0xFF1C2D28.toInt(),ShareCardPalette.BLACK,0xFF252032.toInt()),null,Shader.TileMode.CLAMP)
            canvas.drawPaint(paint); paint.shader=null
        }
        // A route-focused card must never be an empty giant route frame
        // when no privacy-safe GPS coordinates exist (or route is hidden).
        // Reflow the poster using genuine recorded stats instead.
        val renderTemplate=if(state.template==ShareTemplate.ROUTE &&
            (activity.route.pointCount<2 || state.routeMode==ShareRouteMode.HIDDEN))
            ShareTemplate.STATS else state.template
        val g=geometry(if(renderTemplate==state.template) state
            else state.copy(template=renderTemplate))
        fun text(value:String,x:Float,y:Float,size:Float,color:Int=ShareCardPalette.WHITE,bold:Boolean=false,maxWidth:Float=968f) {
            paint.color=color; paint.typeface=Typeface.create(if(state.typography==ShareTypography.CONDENSED) "sans-serif-condensed" else "sans-serif",if(bold) Typeface.BOLD else Typeface.NORMAL)
            paint.textSize=size
            if(paint.measureText(value)>maxWidth) paint.textSize=(size*maxWidth/paint.measureText(value)).coerceAtLeast(22f)
            canvas.drawText(value,x,y,paint)
        }
        if(!state.transparent) text(activity.activityType.uppercase(java.util.Locale.getDefault()),56f,66f,24f,ShareCardPalette.LIME,true)
        if(!state.transparent && ShareMetric.TITLE in state.metrics) {
            val title=activity.metrics[ShareMetric.TITLE].orEmpty()
            val tp=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color=ShareCardPalette.WHITE; textSize=58f; typeface=Typeface.create("sans-serif",Typeface.BOLD)
            }
            val titleLayout=StaticLayout.Builder.obtain(title,0,title.length,tp,968)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(2)
                .setEllipsize(android.text.TextUtils.TruncateAt.END).setIncludePad(false).build()
            canvas.save(); canvas.translate(56f,88f); titleLayout.draw(canvas); canvas.restore()
        }
        if(!state.transparent && ShareMetric.DATE in state.metrics) text(activity.metrics[ShareMetric.DATE].orEmpty(),56f,232f,28f,ShareCardPalette.MUTED)

        if(state.routeMode!=ShareRouteMode.HIDDEN && g.route.height()>0) {
            canvas.save()
            // Keep the snapshot's native logo and attribution fully visible at its edges.
            canvas.clipRect(g.route)
            if(map!=null && !state.transparent) canvas.drawBitmap(map,null,g.route,paint.apply { color=Color.WHITE; alpha=255 })
            else drawCoordinateRoute(canvas,g.route,activity,state)
            canvas.restore()
        }
        val selected=numeric.filter { it in state.metrics && it in activity.metrics }
        val fields=state.featuredMetric?.takeIf { it in selected }?.let { listOf(it)+(selected-it) } ?: selected
        fun stat(metric:ShareMetric,x:Float,y:Float,valueSize:Float,labelSize:Float,width:Float,accent:Boolean=false) {
            text(activity.metrics.getValue(metric),x,y,valueSize,if(accent) ShareCardPalette.LIME else ShareCardPalette.WHITE,true,width)
            text(metric.label.uppercase(java.util.Locale.getDefault()),x,y+labelSize*1.45f,labelSize,ShareCardPalette.MUTED,maxWidth=width)
        }
        when(renderTemplate) {
            ShareTemplate.ROUTE -> fields.forEachIndexed { i,metric ->
                val row=if(i==0) 0 else 1+(i-1)/2
                val x=if(i==0) 72f else 72f+((i-1)%2)*490f
                val y=g.statsTop+row*g.rowHeight+g.rowHeight*.54f
                stat(metric,x,y,g.rowHeight*(if(i==0) .83f else .48f),
                    g.rowHeight*.16f,if(i==0) 920f else 440f,i==0)
            }
            ShareTemplate.STATS -> {
                // New designs use up to four rows; legacy presets with five
                // recorded metrics must still render all saved selections.
                val rowHeight=(state.ratio.height*.90f-g.statsTop)/
                    max(4,fields.size).toFloat()
                fields.forEachIndexed { i,metric ->
                    val y=g.statsTop+i*rowHeight+rowHeight*.56f
                    stat(metric,72f,y,min(122f,rowHeight*.53f),
                        max(20f,rowHeight*.14f),910f,i==0)
                    if(i<fields.lastIndex && !state.transparent) {
                        paint.color=0xFF27322B.toInt(); paint.strokeWidth=2f
                        val bottom=g.statsTop+(i+1)*rowHeight-12f
                        canvas.drawLine(72f,bottom,1008f,bottom,paint)
                    }
                }
            }
            ShareTemplate.MINIMAL -> {
                fields.firstOrNull()?.let { metric ->
                    stat(metric,72f,g.statsTop,min(175f,state.ratio.height*.15f),
                        24f,936f,false)
                }
                fields.drop(1).take(3).forEachIndexed { i,metric ->
                    val x=72f+i*310f
                    val y=state.ratio.height*.895f
                    stat(metric,x,y,min(49f,state.ratio.height*.047f),
                        20f,290f)
                }
            }
        }
        // Privacy status belongs to the editor UI, not the shareable image.
        // No route-trim indicator, original endpoint or editor chrome is exported.
        if(!state.transparent && renderTemplate==ShareTemplate.STATS) {
            paint.shader=null; paint.color=ShareCardPalette.LIME; paint.style=Paint.Style.FILL
            canvas.drawRect(0f,state.ratio.height-90f,1080f,
                state.ratio.height.toFloat(),paint)
            text("STRIDELAB",56f,state.ratio.height-34f,28f,ShareCardPalette.BLACK,true)
            text("RUN LOG",856f,state.ratio.height-34f,22f,
                ShareCardPalette.BLACK,true,maxWidth=160f)
        } else {
            val brandSize=when(state.branding) {
                ShareBranding.MINIMAL->22f
                ShareBranding.SUBTLE->27f
                ShareBranding.STANDARD->34f
            }
            if(!state.transparent) {
                text("STRIDELAB",56f,state.ratio.height-40f,
                    brandSize,ShareCardPalette.MUTED,true)
                if(state.branding==ShareBranding.STANDARD) {
                    text("EVERY STRIDE COUNTS",540f,state.ratio.height-40f,
                        22f,ShareCardPalette.MUTED,maxWidth=484f)
                }
            }
        }
        return bitmap
    }

    /** Same Mercator projection as the map; a coordinate trace is never presented as streets. */
    private fun drawCoordinateRoute(canvas:Canvas, rect:RectF, activity:ShareRenderableActivity, state:ShareStudioState) {
        val points=activity.route.cameraPoints()
        if(points.isEmpty()) return
        fun mx(p:RecordedGpsPoint)=(p.longitude+180.0)/360.0
        fun my(p:RecordedGpsPoint)=(1.0-ln(tan(Math.PI/4+Math.toRadians(p.latitude)/2))/Math.PI)/2
        val minX=points.minOf(::mx); val maxX=points.maxOf(::mx)
        val minY=points.minOf(::my); val maxY=points.maxOf(::my)
        val scale=state.zoom.factor*min((rect.width()-100)/(maxX-minX).coerceAtLeast(.000001),
            (rect.height()-100)/(maxY-minY).coerceAtLeast(.000001))
        fun position(p:RecordedGpsPoint)=PointF((rect.centerX()+rect.width()*state.position.shift+(mx(p)-(minX+maxX)/2)*scale).toFloat(),
            (rect.centerY()+rect.height()*state.verticalPosition.shift+(my(p)-(minY+maxY)/2)*scale).toFloat())
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=state.routeColor.color; strokeWidth=state.stroke.width; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND; style=Paint.Style.STROKE
        }
        activity.route.segments.forEach { segment ->
            val path=Path()
            segment.forEachIndexed { i,p -> val xy=position(p); if(i==0) path.moveTo(xy.x,xy.y) else path.lineTo(xy.x,xy.y) }
            paint.alpha=255
            canvas.drawPath(path,paint)
            if(segment.size==1) { val xy=position(segment[0]); canvas.drawPoint(xy.x,xy.y,paint) }
        }
        val analysis=activity.analysis[state.routeMode] ?: return
        val low=analysis.minimum ?: return
        val high=(analysis.maximum ?: low).coerceAtLeast(low+.001)
        val colors=ShareCardPalette.colors(state.routeMode)
        paint.alpha=255
        analysis.edges.forEach { edge ->
            val t=((edge.value-low)/(high-low)).coerceIn(0.0,1.0)*2
            val index=if(t<1) 0 else 1
            paint.color=blend(colors[index],colors[index+1],(t-index).toFloat())
            val a=position(edge.start); val b=position(edge.end)
            canvas.drawLine(a.x,a.y,b.x,b.y,paint)
        }
    }

    private fun blend(a:Int,b:Int,t:Float)=Color.rgb(
        (Color.red(a)+(Color.red(b)-Color.red(a))*t).toInt(),
        (Color.green(a)+(Color.green(b)-Color.green(a))*t).toInt(),
        (Color.blue(a)+(Color.blue(b)-Color.blue(a))*t).toInt())
}
