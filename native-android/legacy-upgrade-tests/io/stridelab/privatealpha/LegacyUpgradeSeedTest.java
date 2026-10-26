package io.stridelab.privatealpha;

import android.content.Context;
import android.os.SystemClock;
import android.webkit.WebView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Runs only on an isolated emulator before replacing the legacy APK with native code. */
public class LegacyUpgradeSeedTest {
    @Test public void seedLegacyUpgradeFixture() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("io.stridelab.privatealpha",context.getPackageName());
        try(TrainingStore store=new TrainingStore(context)) {
            store.applyRemotePage("synthetic-upgrade", "activity", Collections.singletonList(new TrainingStore.RemoteRecord("upgrade-activity", "{\"synthetic\":true,\"distance_m\":5000,\"avg_hr\":null}", "revision-before-upgrade")), "fixture-cursor", 123);
            store.enqueue("synthetic-upgrade","workout","offline-workout","upgrade-operation","{\"synthetic\":true,\"sets\":[5,5,5]}",null,124);
        }
        new SessionVault(context).save(new NativeSession("synthetic-upgrade","synthetic-upgrade-access","synthetic-upgrade-refresh",2000000000L));
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            WebView[] view=new WebView[1];
            scenario.onActivity(a -> view[0]=find(a.findViewById(android.R.id.content)));
            assertNotNull(view[0]);
            boolean ready=false;
            for(int n=0;n<100;n++) {
                if(eval(scenario,view[0],"location.origin+'|'+(document.querySelector('#app h1')!==null)").contains("appassets.androidplatform.net|true")){ready=true;break;}
                SystemClock.sleep(300);
            }
            assertTrue("Legacy app origin did not load",ready);
            waitForInteractiveFrame(scenario,view[0]);
            String snapshot=eval(scenario,view[0],"(function(){localStorage.setItem('stride-native-upgrade-fixture','synthetic-value-π');S.nativeUpgradeFixture={synthetic:true};localStorage.setItem('afterglow2',JSON.stringify(S));return localStorage.getItem('afterglow2');})()");
            assertTrue(context.getSharedPreferences("synthetic-upgrade-probe",0).edit().putString("afterglowSnapshotEvaluation",snapshot).putBoolean("seeded",true).commit());
            finishAndAwaitDestroyed(scenario);
        }
    }
    private static void finishAndAwaitDestroyed(ActivityScenario<MainActivity> scenario) throws Exception {
        // Exercise the app's real finish path, then verify actual destruction before auto-close.
        // ActivityScenario's implicit EmptyActivity transition races WebView focus on cold devices.
        scenario.onActivity(android.app.Activity::finish);
        for(int n=0;n<100;n++) {
            if(scenario.getState()==androidx.lifecycle.Lifecycle.State.DESTROYED)return;
            SystemClock.sleep(100);
        }
        assertEquals("Legacy activity must finish without an input-dispatch timeout",androidx.lifecycle.Lifecycle.State.DESTROYED,scenario.getState());
    }
    private static void waitForInteractiveFrame(ActivityScenario<MainActivity> scenario,WebView view) throws Exception {
        java.util.concurrent.atomic.AtomicBoolean committed=new java.util.concurrent.atomic.AtomicBoolean(android.os.Build.VERSION.SDK_INT<29);
        scenario.onActivity(activity -> {
            if(android.os.Build.VERSION.SDK_INT>=29)view.getViewTreeObserver().registerFrameCommitCallback(() -> committed.set(true));
            view.postInvalidateOnAnimation();
        });
        boolean[] focused={false};
        for(int n=0;n<100;n++) {
            scenario.onActivity(activity -> focused[0]=activity.hasWindowFocus()&&view.isLaidOut());
            if(committed.get()&&focused[0])return;
            SystemClock.sleep(100);
        }
        fail("Legacy document loaded but its interactive frame did not become ready");
    }
    private static WebView find(android.view.View view) {
        if(view instanceof WebView)return (WebView)view;
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int n=0;n<group.getChildCount();n++){WebView result=find(group.getChildAt(n));if(result!=null)return result;}}
        return null;
    }
    private static String eval(ActivityScenario<MainActivity> scenario, WebView view, String js) throws Exception {
        ArrayBlockingQueue<String> result=new ArrayBlockingQueue<>(1);
        scenario.onActivity(a -> view.evaluateJavascript(js,result::offer));
        String value=result.poll(12,TimeUnit.SECONDS);assertNotNull(value);return value;
    }
}
