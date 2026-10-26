package io.stridelab.privatealpha;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Offline Android shell. The accepted HTML and coaching engine remain untouched. */
public class MainActivity extends Activity {
    private static final String TAG = "StrideLabPreAlpha";
    private static final String APP_URL = "https://appassets.androidplatform.net/assets/www/index.html";
    private static final String APP_PREFIX = "https://appassets.androidplatform.net/assets/www/";
    private static final int REQUEST_BACKUP = 42;
    private static final int REQUEST_RESTORE = 43;
    private static final String FETCH_STATE_JS = "(function(){try{return localStorage.getItem('afterglow2')||'';}catch(e){return '';}})()";
    private WebView webView;
    private WebViewAssetLoader assetLoader;
    private boolean pageReady = false;
    private String pendingBackup;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(16,19,27));
        getWindow().setNavigationBarColor(Color.rgb(16,19,27));
        getWindow().getDecorView().setSystemUiVisibility(0);
        assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(Color.rgb(16,19,27));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), 0, dp(8), 0);
        header.setBackgroundColor(Color.rgb(24,27,37));
        TextView name = new TextView(this);
        name.setText("StrideLab  ·  PRIVATE PRE-ALPHA");
        name.setTextColor(Color.WHITE);
        name.setTextSize(13);
        name.setContentDescription("StrideLab private pre-alpha");
        header.addView(name,new LinearLayout.LayoutParams(0, dp(48),1));
        Button menu = new Button(this);
        menu.setText("⋮");
        menu.setTextColor(Color.WHITE);
        menu.setAllCaps(false);
        menu.setContentDescription("App options, backup and restore");
        menu.setOnClickListener(v -> showOptions());
        header.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(48)));
        outer.addView(header);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(16,19,27));
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isLocal(uri) && uri.getPath() != null && uri.getPath().startsWith("/assets/www/")) {
                    WebResourceResponse response = assetLoader.shouldInterceptRequest(uri);
                    return response != null ? response : forbidden();
                }
                return forbidden();  // No network loads, not even remotely hosted fonts or scripts.
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isLocal(uri) && uri.getPath() != null && uri.getPath().startsWith("/assets/www/")) return false;
                // Exercise-credit and license links open outside the isolated app, only on user clicks.
                if (request.isForMainFrame() && request.hasGesture() && "https".equals(uri.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)); }
                    catch (Exception e) { toast("No browser available"); }
                }
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (APP_URL.equals(url)) pageReady = true;
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest req, android.webkit.WebResourceError error) {
                if (req.isForMainFrame()) { pageReady = false; toast("App content could not load"); }
            }
        });
        WebSettings settings=webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        outer.addView(webView,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        setContentView(outer);
        webView.loadUrl(APP_URL);
    }

    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private static boolean isLocal(Uri uri){return "https".equals(uri.getScheme()) && "appassets.androidplatform.net".equals(uri.getHost()) && uri.getPort()==-1;}
    private static WebResourceResponse forbidden(){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void showOptions(){new AlertDialog.Builder(this).setTitle("StrideLab data and updates")
            .setItems(new String[]{"Back up my data", "Restore from backup", "About this build"}, (dialog,which)->{
                if(which==0)backUp(); else if(which==1)pickRestore(); else showAbout();
            }).show();}
    private void showAbout(){new AlertDialog.Builder(this).setTitle("StrideLab private pre-alpha")
            .setMessage("Offline local prototype. Your saved workouts, plans and imported activities stay in Android WebView storage. Back up before replacing a phone, clearing app data or reinstalling. Updates require the same app ID and signing key. No automatic cloud backup or Garmin sync.")
            .setPositiveButton("OK",null).show();}
    private interface StateReceiver{void onValue(String state);}
    private void withCurrentState(StateReceiver receiver){
        if(!pageReady){toast("App is still loading");return;}
        webView.evaluateJavascript(FETCH_STATE_JS,raw->{
            try {receiver.onValue(new JSONArray("["+raw+"]").getString(0));}
            catch(Exception ex){toast("Could not read saved data");}
        });
    }
    private void backUp(){withCurrentState(value->{
        try {pendingBackup=BackupCodec.encode(value);}
        catch(Exception e){toast("Backup unavailable: "+e.getMessage());return;}
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("application/json");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_TITLE,"stridelab-backup.json");
        startActivityForResult(i,REQUEST_BACKUP);
    });}
    private void pickRestore(){
        if(!pageReady){toast("App is still loading");return;}
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("application/json");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,REQUEST_RESTORE);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        Uri u=data.getData();
        if(requestCode==REQUEST_BACKUP){
            if(pendingBackup==null){toast("Backup expired, try again");return;}
            try(OutputStream out=getContentResolver().openOutputStream(u,"wt")){
                if(out==null)throw new IllegalStateException("File is not writable");
                out.write(pendingBackup.getBytes(StandardCharsets.UTF_8));out.flush();
                toast("Backup saved. Keep it somewhere private.");
            }catch(Exception e){Log.e(TAG,"Backup export failed",e);toast("Backup save failed; try another location");}
            finally{pendingBackup=null;}
        } else if(requestCode==REQUEST_RESTORE){
            try(InputStream in=getContentResolver().openInputStream(u)){
                if(in==null)throw new IllegalStateException("File unavailable");
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int n;
                while((n=in.read(chunk))!=-1){if(bytes.size()+n>BackupCodec.MAX_BYTES)throw new IllegalStateException("Backup exceeds 2 MiB");bytes.write(chunk,0,n);}
                String state=BackupCodec.decode(bytes.toString("UTF-8"));
                new AlertDialog.Builder(this).setTitle("Replace local StrideLab data?")
                    .setMessage("This restores workouts, imported activities and plans from the selected backup. Current local data will be REPLACED. Back up the current data first if you need it.")
                    .setNegativeButton("Cancel",null)
                    .setNeutralButton("Back up first",(d,w)->backUp())
                    .setPositiveButton("Replace",(d,w)->applyRestore(state)).show();
            }catch(Exception e){toast("Restore rejected: "+e.getMessage());}
        }
    }
    private void applyRestore(String replacement){
        if(!pageReady){toast("App is not ready");return;}
        // Value is JSON-string escaped, never inserted as executable JavaScript.
        String quoted=JSONObject.quote(replacement);
        String js="(function(){var k='"+BackupCodec.STORAGE_KEY+"',old=localStorage.getItem(k);"+
            "try{var next="+quoted+";localStorage.setItem(k,next);"+
            "if(localStorage.getItem(k)!==next)throw Error('Verification failed');return 'OK';}"+
            "catch(e){try{if(old===null)localStorage.removeItem(k);else localStorage.setItem(k,old);}catch(x){}return 'FAILED';}})()";
        webView.evaluateJavascript(js,result->{
            if("\"OK\"".equals(result)){toast("Data restored. Reloading StrideLab.");webView.reload();}
            else toast("Restore could not save; existing data was preserved where possible");
        });
    }
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);}
    @Override public void onBackPressed(){
        // Close prototype sheets using the same Escape behavior tested in Playwright.
        if(!pageReady){super.onBackPressed();return;}
        webView.evaluateJavascript("(function(){var s=document.querySelector('#layer .sh, #layer [role=dialog]');if(!s)return false;document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));return true;})()",answer->{
            if("true".equals(answer))return;
            new AlertDialog.Builder(this).setTitle("Exit StrideLab?")
                .setMessage("Saved data will remain on this device. Unfinished workout edits may be lost.")
                .setNegativeButton("Stay",null).setPositiveButton("Exit",(d,w)->finish()).show();
        });
    }
    @Override public void onPause(){
        super.onPause();
        Log.d(TAG,"Legacy lifecycle: pausing WebView");
        if(webView!=null)webView.onPause();
        Log.d(TAG,"Legacy lifecycle: paused");
    }
    @Override public void onResume(){super.onResume();if(webView!=null)webView.onResume();}
    @Override protected void onDestroy(){
        pageReady=false;
        if(webView!=null){
            Log.d(TAG,"Legacy lifecycle: detaching WebView");
            android.view.ViewParent parent=webView.getParent();
            if(parent instanceof android.view.ViewGroup)((android.view.ViewGroup)parent).removeView(webView);
            // Android requires removal from the view hierarchy before destroy().
            // This releases rendering/focus resources without clearing any web storage.
            webView.destroy();webView=null;
            Log.d(TAG,"Legacy lifecycle: destroyed");
        }
        super.onDestroy();
    }
}
