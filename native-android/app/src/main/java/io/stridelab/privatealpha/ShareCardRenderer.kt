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
        val count=state.metrics.count { it in numeric }.coerceAtLeast(1)
        val row=if(state.layout==ShareLayout.COMPACT) 138f else 158f
        val statsHeight=ceil(count/2.0).toFloat()*row
        return when(state.template) {
            ShareTemplate.STATS -> ShareCardGeometry(RectF(56f,280f+statsHeight,1024f,h-104f),250f,row)
            ShareTemplate.ROUTE -> ShareCardGeometry(RectF(56f,246f,1024f,h-statsHeight-140f),h-statsHeight-102f,row)
            ShareTemplate.MINIMAL -> ShareCardGeometry(RectF(120f,270f,960f,h-statsHeight-170f),h-statsHeight-112f,row)
        }
    }

    fun render(activity:ShareRenderableActivity, state:ShareStudioState, map:Bitmap? = null):Bitmap {
        val bitmap=Bitmap.createBitmap(1080,state.ratio.height,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(ShareCardPalette.BLACK)
        if(state.background==ShareBackground.GRADIENT) {
            paint.shader=LinearGradient(0f,0f,1080f,bitmap.height.toFloat(),
                intArrayOf(0xFF1C2D28.toInt(),ShareCardPalette.BLACK,0xFF252032.toInt()),null,Shader.TileMode.CLAMP)
            canvas.drawPaint(paint); paint.shader=null
        }
        val g=geometry(state)
        fun text(value:String,x:Float,y:Float,size:Float,color:Int=ShareCardPalette.WHITE,bold:Boolean=false,maxWidth:Float=968f) {
            paint.color=color; paint.typeface=Typeface.create("sans-serif",if(bold) Typeface.BOLD else Typeface.NORMAL)
            paint.textSize=size
            if(paint.measureText(value)>maxWidth) paint.textSize=(size*maxWidth/paint.measureText(value)).coerceAtLeast(22f)
            canvas.drawText(value,x,y,paint)
        }
        text(activity.activityType.uppercase(java.util.Locale.getDefault()),56f,66f,24f,ShareCardPalette.LIME,true)
        if(ShareMetric.TITLE in state.metrics) {
            val title=activity.metrics[ShareMetric.TITLE].orEmpty()
            val tp=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color=ShareCardPalette.WHITE; textSize=58f; typeface=Typeface.create("sans-serif",Typeface.BOLD)
            }
            val titleLayout=StaticLayout.Builder.obtain(title,0,title.length,tp,968)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(2)
                .setEllipsize(android.text.TextUtils.TruncateAt.END).setIncludePad(false).build()
            canvas.save(); canvas.translate(56f,88f); titleLayout.draw(canvas); canvas.restore()
        }
        if(ShareMetric.DATE in state.metrics) text(activity.metrics[ShareMetric.DATE].orEmpty(),56f,232f,28f,ShareCardPalette.MUTED)

        if(state.routeMode!=ShareRouteMode.HIDDEN && g.route.height()>0) {
            canvas.save()
            // Keep the snapshot's native logo and attribution fully visible at its edges.
            canvas.clipRect(g.route)
            if(map!=null) canvas.drawBitmap(map,null,g.route,paint.apply { color=Color.WHITE; alpha=255 })
            else drawCoordinateRoute(canvas,g.route,activity,state)
            canvas.restore()
        }
        val fields=numeric.filter { it in state.metrics && it in activity.metrics }
        fields.forEachIndexed { i,metric ->
            val x=56f+(i%2)*500f
            val y=g.statsTop+(i/2)*g.rowHeight
            val value=activity.metrics.getValue(metric)
            val featured=state.template==ShareTemplate.STATS && i==0
            text(value,x,y+68f,if(featured) 88f else 62f,
                if(featured || (state.variant==ShareVariant.PERFORMANCE && i==0)) ShareCardPalette.LIME else ShareCardPalette.WHITE,true,456f)
            text(metric.label.uppercase(java.util.Locale.getDefault()),x,y+110f,23f,ShareCardPalette.MUTED,maxWidth=456f)
        }
        text(if(state.routeMode==ShareRouteMode.HIDDEN) "ROUTE HIDDEN · FULL ACTIVITY STATS" else "ROUTE TRIMMED · FULL ACTIVITY STATS",56f,bitmap.height-80f,20f,ShareCardPalette.MUTED)
        val brandSize=when(state.branding) { ShareBranding.MINIMAL->22f; ShareBranding.SUBTLE->27f; ShareBranding.STANDARD->34f }
        text("STRIDELAB",56f,bitmap.height-40f,brandSize,ShareCardPalette.MUTED,true)
        if(state.branding==ShareBranding.STANDARD) text("EVERY STRIDE COUNTS",540f,bitmap.height-40f,22f,ShareCardPalette.MUTED,maxWidth=484f)
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
        val scale=min((rect.width()-100)/(maxX-minX).coerceAtLeast(.000001),
            (rect.height()-100)/(maxY-minY).coerceAtLeast(.000001))
        fun position(p:RecordedGpsPoint)=PointF((rect.centerX()+(mx(p)-(minX+maxX)/2)*scale).toFloat(),
            (rect.centerY()+(my(p)-(minY+maxY)/2)*scale).toFloat())
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=ShareCardPalette.LIME; strokeWidth=9f; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND; style=Paint.Style.STROKE
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
