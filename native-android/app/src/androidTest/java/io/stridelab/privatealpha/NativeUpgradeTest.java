package io.stridelab.privatealpha;

import android.content.Context;
import android.os.SystemClock;
import android.webkit.WebView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Assume;
import org.junit.Test;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class NativeUpgradeTest {
    @Test public void updateRetainsLegacyOriginDatabaseOutboxAndKeystore() throws Exception {
        Assume.assumeTrue("Run the legacy seed/install sequence to enable this update test", "true".equals(InstrumentationRegistry.getArguments().getString("expectLegacyUpgrade")));
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("Legacy APK fixture must exist: install -r must not clear app data",context.getSharedPreferences("synthetic-upgrade-probe",0).getBoolean("seeded",false));
        try(TrainingStore store=new TrainingStore(context)) {
            assertEquals("revision-before-upgrade",store.get("synthetic-upgrade","activity","upgrade-activity").serverVersion);
            assertEquals("fixture-cursor",store.cursor("synthetic-upgrade","activity"));
            assertEquals("upgrade-operation",store.ready("synthetic-upgrade",100).get(0).operationId);
        }
        assertEquals("synthetic-upgrade-access",new SessionVault(context).read("synthetic-upgrade").accessToken);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            WebView[] view=new WebView[1];scenario.onActivity(a -> view[0]=find(a.findViewById(android.R.id.content)));
            boolean ready=false;
            for(int n=0;n<100;n++){if(eval(scenario,view[0],"location.origin+'|'+(document.querySelector('#app h1')!==null)").contains("appassets.androidplatform.net|true")){ready=true;break;}SystemClock.sleep(300);}
            assertTrue("Preserved legacy origin did not load",ready);
            waitForInteractiveFrame(scenario,view[0]);
            assertEquals("synthetic-value-π",new org.json.JSONTokener(eval(scenario,view[0],"localStorage.getItem('stride-native-upgrade-fixture')")).nextValue());
            assertEquals(context.getSharedPreferences("synthetic-upgrade-probe",0).getString("afterglowSnapshotEvaluation",null),eval(scenario,view[0],"localStorage.getItem('afterglow2')"));
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
    private static String eval(ActivityScenario<MainActivity> scenario,WebView view,String js) throws Exception {
        ArrayBlockingQueue<String> result=new ArrayBlockingQueue<>(1);scenario.onActivity(a -> view.evaluateJavascript(js,result::offer));
        String value=result.poll(12,TimeUnit.SECONDS);assertNotNull(value);return value;
    }
}
