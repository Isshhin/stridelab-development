package io.stridelab.privatealpha

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/** Same emulator viewport as native captures. Preserved WebView is a test reference, never a primary native screen. */
class PrototypeReferenceTest {
    @Test fun captureAcceptedMobileOverviewTrainingAndExerciseModalWithoutChangingSavedState() {
        check(android.os.Build.FINGERPRINT.contains("generic")||android.os.Build.HARDWARE=="ranchu"||android.os.Build.HARDWARE=="goldfish")
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var web:WebView
            fun find(view:View):WebView? { if(view is WebView)return view;if(view is ViewGroup) for(i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it };return null }
            scenario.onActivity { web=checkNotNull(find(it.findViewById(android.R.id.content))) }
            fun eval(js:String):String { val queue=ArrayBlockingQueue<String>(1);scenario.onActivity { web.evaluateJavascript(js) { queue.offer(it) } };return checkNotNull(queue.poll(12,TimeUnit.SECONDS)) }
            fun ready(selector:String) { var found=false;for(i in 0..99) { if(eval("document.querySelector(${JSONObject.quote(selector)})!==null")=="true") { found=true;break };android.os.SystemClock.sleep(200) };assertTrue("Accepted prototype control did not load: $selector",found) }
            fun frame() { val queue=ArrayBlockingQueue<Boolean>(1);scenario.onActivity { web.postVisualStateCallback(System.nanoTime(),object:WebView.VisualStateCallback(){override fun onComplete(id:Long){queue.offer(true)}});web.postInvalidateOnAnimation() };assertEquals(true,queue.poll(12,TimeUnit.SECONDS));instrumentation.waitForIdleSync();android.os.SystemClock.sleep(350) }
            fun capture(name:String) { frame();val folder=File(context.getExternalFilesDir(null),"native-ui-evidence");folder.mkdirs();File(folder,"$name.png").outputStream().use { assertTrue(checkNotNull(instrumentation.uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG,100,it)) } }
            ready("[data-a='tab'][data-v='today']")
            val original=JSONTokener(eval("localStorage.getItem('afterglow2')")).nextValue() as? String
            try {
                eval("document.querySelector('[data-a=tab][data-v=today]').click();true");capture("phase1-reference-overview-demo")
                eval("document.querySelector('[data-a=tab][data-v=training]').click();true");ready("[data-a='plan']");capture("phase1-reference-training")
                eval("document.querySelector('[data-a=tab][data-v=lib]').click();true");ready("button[aria-label^='Incline push-up,']")
                eval("document.querySelector(\"button[aria-label^='Incline push-up,']\").click();true");ready("#layer .ov:not(.out) .sh")
                frame();frame()
                assertEquals("true",eval("(()=>{const h=document.querySelector('#layer .sh h1'),d=document.querySelector('#layer .ov:not(.out)');if(!h||!d)return false;const r=h.getBoundingClientRect();return h.textContent.includes('Incline push-up')&&r.height>0&&r.top>=0&&r.top<innerHeight&&getComputedStyle(d).opacity==='1'})()"))
                capture("phase1-reference-library-modal")
                assertEquals("true",eval("document.querySelector('#layer .ov:not(.out) .sh')!==null"))
            } finally {
                if(original!=null)eval("Object.keys(S).forEach(k=>delete S[k]);Object.assign(S,JSON.parse(${JSONObject.quote(original)}));localStorage.setItem('afterglow2',${JSONObject.quote(original)});true")
                else eval("localStorage.removeItem('afterglow2');true")
                scenario.onActivity { it.finish() }
                var destroyed=false;for(i in 0..99) { if(scenario.state==androidx.lifecycle.Lifecycle.State.DESTROYED) { destroyed=true;break };android.os.SystemClock.sleep(100) };assertTrue("Reference activity must finish cleanly",destroyed)
            }
        }
    }
}
