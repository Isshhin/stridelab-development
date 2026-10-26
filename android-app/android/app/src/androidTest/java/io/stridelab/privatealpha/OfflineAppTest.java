package io.stridelab.privatealpha;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.webkit.WebView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class OfflineAppTest {
    @Test public void offlineAssetsLoadAndStateSurvivesRecreation() throws Exception {
        final ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class);
        try {
            final WebView[] view=new WebView[1];
            // No view ID dependency: resolve WebView as a child of the vertical native shell.
            scenario.onActivity(a -> {android.view.ViewGroup outer=(android.view.ViewGroup) a.findViewById(android.R.id.content); view[0]=findWebView(outer);});
            assertNotNull("Expected offline WebView",view[0]);
            waitUntilReady(scenario,view[0]);
            String old = eval(scenario,view[0],"localStorage.getItem('afterglow2')");
            assertTrue("Expected a valid app origin",eval(scenario,view[0],"location.origin").contains("appassets.androidplatform.net"));
            assertEquals("true",eval(scenario,view[0],"(function(){localStorage.setItem('stride4eprobe','works');return localStorage.getItem('stride4eprobe')==='works';})()"));
            scenario.recreate();
            scenario.onActivity(a -> {android.view.ViewGroup outer=(android.view.ViewGroup) a.findViewById(android.R.id.content);view[0]=findWebView(outer);});
            waitUntilReady(scenario,view[0]);
            assertEquals("\"works\"",eval(scenario,view[0],"localStorage.getItem('stride4eprobe')"));
            eval(scenario,view[0],"localStorage.removeItem('stride4eprobe')");
        } finally {scenario.close();}
    }
    @Test public void noInternetPermission() {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals(PackageManager.PERMISSION_DENIED,c.checkSelfPermission("android.permission.INTERNET"));
    }
    private static WebView findWebView(android.view.View v){
        if(v instanceof WebView)return(WebView)v;
        if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++){WebView w=findWebView(g.getChildAt(i));if(w!=null)return w;}}
        return null;
    }
    private static void waitUntilReady(ActivityScenario<MainActivity> s,WebView v) throws Exception {
        for(int i=0;i<35;i++){String result=eval(s,v,"document.readyState+'|'+(document.querySelector('#app')!==null)");
            if(result.contains("complete") || result.contains("interactive"))return;
            SystemClock.sleep(300);
        }
        fail("Offline document did not become ready");
    }
    private static String eval(ActivityScenario<MainActivity> s,WebView v,String js) throws Exception {
        ArrayBlockingQueue<String> q=new ArrayBlockingQueue<>(1);
        s.onActivity(a -> v.evaluateJavascript(js,q::offer));
        String out=q.poll(12,TimeUnit.SECONDS);
        assertNotNull("JS evaluation timed out: "+js,out);return out;
    }
}
