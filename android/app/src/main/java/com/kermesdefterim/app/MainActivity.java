package com.kermesdefterim.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.webkit.JavascriptInterface;
import org.json.JSONObject;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

public class MainActivity extends Activity {
    private static final String HOME = "https://app.kermesdefterim.com/";
    private static final String HOST = "app.kermesdefterim.com";

    private WebView web;
    /** Uygulama ekrandayken bildirim gösterilmez. */
    static volatile boolean ekranda;
    private static final int IZIN_ISTEGI = 7;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#184d43"));
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        // Android 15 çizimi kenardan kenara yapar; durum/gezinti çubuğu payı bırak.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()).top;
            int bottom = Math.max(
                insets.getInsets(WindowInsets.Type.systemBars()).bottom,
                insets.getInsets(WindowInsets.Type.ime()).bottom);
            v.setPadding(0, top, 0, bottom);
            return WindowInsets.CONSUMED;
        });

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Kopru(), "KermesAndroid");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) { }
                return true;
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                if (!req.isForMainFrame()) return;
                String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                    + "<body style='font-family:sans-serif;background:#f6f1e7;color:#184d43;text-align:center;padding:48px 24px'>"
                    + "<h2>Bağlantı kurulamadı</h2><p>İnternet bağlantını kontrol et.</p>"
                    + "<p><a href='" + HOME + "' style='display:inline-block;padding:12px 24px;background:#184d43;color:#fff;"
                    + "border-radius:12px;text-decoration:none'>Tekrar dene</a></p></body></html>";
                v.loadDataWithBaseURL(HOME, html, "text/html", "utf-8", null);
            }
        });

        // Bildirimler açık bırakılmışsa servis kapanmış olabilir: yeniden başlat
        if (getSharedPreferences(BildirimServisi.PREFS, MODE_PRIVATE).getBoolean("acik", false) && izinVar()) BildirimServisi.baslat(this);

        if (state != null) {
            web.restoreState(state);
        } else {
            web.loadUrl(HOME);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override protected void onPause() { super.onPause(); ekranda = false; web.onPause(); }
    @Override protected void onResume() { super.onResume(); ekranda = true; web.onResume(); }

    private boolean izinVar() {
        return Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int kod, String[] izinler, int[] sonuclar) {
        super.onRequestPermissionsResult(kod, izinler, sonuclar);
        if (kod != IZIN_ISTEGI) return;
        if (izinVar()) {
            BildirimServisi.baslat(this);
        } else {
            getSharedPreferences(BildirimServisi.PREFS, MODE_PRIVATE).edit().putBoolean("acik", false).apply();
            web.evaluateJavascript("window.kermesBildirimIzinYok && window.kermesBildirimIzinYok()", null);
        }
    }

    /** Web uygulamasının bildirimleri açıp kapattığı köprü (yalnızca kendi alan adımız yüklenir). */
    private class Kopru {
        @JavascriptInterface
        public String bildirimAc(String json) {
            try {
                JSONObject j = new JSONObject(json);
                String origin = j.optString("origin", "");
                if (!origin.equals("https://" + HOST)) return "hata";
                SharedPreferences.Editor e = getSharedPreferences(BildirimServisi.PREFS, MODE_PRIVATE).edit();
                e.putBoolean("acik", true).putString("origin", origin).putString("kod", j.optString("code", ""))
                    .putString("token", j.optString("token", "")).putString("kullanici", j.optString("user", "")).apply();
                runOnUiThread(() -> {
                    if (izinVar()) {
                        BildirimServisi.durdur(MainActivity.this);
                        BildirimServisi.baslat(MainActivity.this);
                    } else if (Build.VERSION.SDK_INT >= 33) {
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, IZIN_ISTEGI);
                    }
                });
                return izinVar() ? "tamam" : "izin-isteniyor";
            } catch (Exception ex) {
                return "hata";
            }
        }

        @JavascriptInterface
        public void bildirimKapat() {
            getSharedPreferences(BildirimServisi.PREFS, MODE_PRIVATE).edit().putBoolean("acik", false).remove("token").apply();
            runOnUiThread(() -> BildirimServisi.durdur(MainActivity.this));
        }
    }
}
