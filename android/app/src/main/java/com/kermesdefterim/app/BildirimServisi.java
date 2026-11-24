package com.kermesdefterim.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Uygulama arka plandayken sunucuyu dinler ve garson çağrısı, sipariş hazır,
 * yeni sipariş olaylarında telefona bildirim gösterir. Uygulama ekrandayken
 * bildirim göstermez (MainActivity.ekranda).
 */
public class BildirimServisi extends Service {
    static final String PREFS = "bildirim";
    static final String KANAL_DURUM = "durum";
    static final String KANAL_OLAY = "olaylar";
    static final String KAPAT = "com.kermesdefterim.app.KAPAT";
    private static final int ID_DURUM = 1;

    private volatile boolean calisiyor;
    private Thread is;
    private PowerManager.WakeLock kilit;

    static void baslat(Context c) {
        Intent i = new Intent(c, BildirimServisi.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    static void durdur(Context c) {
        c.stopService(new Intent(c, BildirimServisi.class));
    }

    static void kanallar(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel durum = new NotificationChannel(KANAL_DURUM, "Bildirim servisi", NotificationManager.IMPORTANCE_MIN);
        durum.setDescription("Bildirimler açıkken görünen sessiz bilgi");
        durum.setShowBadge(false);
        nm.createNotificationChannel(durum);
        NotificationChannel olay = new NotificationChannel(KANAL_OLAY, "Çağrı ve siparişler", NotificationManager.IMPORTANCE_HIGH);
        olay.setDescription("Garson çağrısı, sipariş hazır ve yeni sipariş");
        olay.enableVibration(true);
        olay.setVibrationPattern(new long[]{0, 250, 120, 250, 120, 400});
        nm.createNotificationChannel(olay);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && KAPAT.equals(intent.getAction())) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("acik", false).apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!p.getBoolean("acik", false) || p.getString("token", "").isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        kanallar(this);
        Notification n = durumBildirimi(p.getString("kullanici", ""));
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID_DURUM, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(ID_DURUM, n);
        if (is == null || !is.isAlive()) {
            calisiyor = true;
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            kilit = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KermesDefterim:bildirim");
            kilit.acquire(14L * 60 * 60 * 1000); // en fazla 14 saat (bir kermes günü)
            is = new Thread(this::dinle, "bildirim-dinle");
            is.start();
        }
        return START_STICKY;
    }

    private Notification durumBildirimi(String kullanici) {
        PendingIntent ac = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent kapat = PendingIntent.getService(this, 1, new Intent(this, BildirimServisi.class).setAction(KAPAT), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, KANAL_DURUM)
            .setSmallIcon(R.drawable.ic_bildirim)
            .setContentTitle("Kermes Defterim bildirimleri açık")
            .setContentText(kullanici.isEmpty() ? "Çağrı ve siparişler telefona gelecek." : kullanici + " için çağrı ve siparişler telefona gelecek.")
            .setContentIntent(ac)
            .setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "Kapat", kapat).build())
            .build();
    }

    private void dinle() {
        long seq = -1;
        int bekleme = 3000;
        while (calisiyor) {
            SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            String origin = p.getString("origin", "https://app.kermesdefterim.com");
            String token = p.getString("token", ""), kod = p.getString("kod", "");
            HttpURLConnection c = null;
            try {
                String adres = origin + "/api/bildirimler" + (seq < 0 ? "" : "?since=" + seq + "&bekle=25");
                c = (HttpURLConnection) new URL(adres).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(40000);
                c.setRequestProperty("Authorization", "Bearer " + token);
                c.setRequestProperty("x-kermes", kod);
                int durum = c.getResponseCode();
                if (durum == 401 || durum == 402 || durum == 403) {
                    goster("Bildirimler durdu", "Oturumun süresi doldu. Uygulamayı açıp yeniden giriş yapın.", "oturum");
                    p.edit().putBoolean("acik", false).apply();
                    break;
                }
                if (durum != 200) throw new Exception("HTTP " + durum);
                JSONObject j = new JSONObject(oku(c.getInputStream()));
                JSONArray olaylar = j.optJSONArray("events");
                if (seq >= 0 && olaylar != null && !MainActivity.ekranda) {
                    for (int i = 0; i < olaylar.length(); i++) {
                        JSONObject o = olaylar.getJSONObject(i);
                        goster(o.optString("title", "Kermes Defterim"), o.optString("body", ""), o.optString("tag", "olay"));
                    }
                }
                seq = j.optLong("seq", 0);
                bekleme = 3000;
            } catch (Exception e) {
                try { Thread.sleep(bekleme); } catch (InterruptedException ignored) { return; }
                bekleme = Math.min(bekleme * 2, 30000);
            } finally {
                if (c != null) c.disconnect();
            }
        }
        stopSelf();
    }

    private void goster(String baslik, String metin, String etiket) {
        PendingIntent ac = PendingIntent.getActivity(this, 2, new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, KANAL_OLAY)
            .setSmallIcon(R.drawable.ic_bildirim)
            .setContentTitle(baslik)
            .setContentText(metin)
            .setStyle(new Notification.BigTextStyle().bigText(metin))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentIntent(ac)
            .setAutoCancel(true)
            .build();
        getSystemService(NotificationManager.class).notify(etiket, 2, n);
    }

    private static String oku(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] tampon = new byte[8192];
        int n;
        while ((n = in.read(tampon)) > 0) b.write(tampon, 0, n);
        in.close();
        return b.toString("UTF-8");
    }

    @Override
    public void onDestroy() {
        calisiyor = false;
        if (is != null) is.interrupt();
        if (kilit != null && kilit.isHeld()) kilit.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
