package id.kuli.sesibrowser;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Deteksi lokasi dari IP publik (berubah saat VPN nyala/mati/pindah server) → region sesi menyesuaikan.
 * Mendengarkan perubahan jaringan default (termasuk transport VPN) dan memeriksa ulang saat aplikasi kembali ke depan.
 * Permintaan geo-IP tanpa cookie, UA generik, tidak lewat WebView — tidak menyentuh data sesi.
 */
final class RegionDetector {
    interface Callback { void onRegion(String cc, String tz, String ip); }

    private static final String[] ENDPOINTS = {
        "https://ipwho.is/?fields=success,country_code,timezone.id,ip",
        "https://ipapi.co/json/",
        "http://ip-api.com/json/?fields=status,countryCode,timezone,query",
    };

    private final Context ctx;
    private final Callback cb;
    private final Handler h = new Handler(Looper.getMainLooper());
    private ConnectivityManager.NetworkCallback netCb;
    private boolean lastVpn; private boolean busy; private long lastRun;
    private final Runnable run = this::detectNow;

    RegionDetector(Context c, Callback cb) { this.ctx = c; this.cb = cb; }

    void start() {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;
        lastVpn = isVpn(cm);
        netCb = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network n) { schedule(2500); }
            @Override public void onLost(Network n) { schedule(3000); }
            @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) {
                boolean vpn = c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) || isVpn(cm);
                if (vpn != lastVpn) { lastVpn = vpn; schedule(2000); }
            }
        };
        try { cm.registerDefaultNetworkCallback(netCb); } catch (Throwable ignored) { netCb = null; }
    }
    void stop() {
        h.removeCallbacks(run);
        if (netCb == null) return;
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        try { if (cm != null) cm.unregisterNetworkCallback(netCb); } catch (Throwable ignored) {}
        netCb = null;
    }

    static boolean isVpn(ConnectivityManager cm) {
        try {
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities c = cm.getNetworkCapabilities(n);
                if (c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /** Debounce: jaringan sering berubah beberapa kali saat VPN tersambung. */
    void schedule(long delayMs) { h.removeCallbacks(run); h.postDelayed(run, delayMs); }

    /** Cek ulang jika sudah > 45 detik sejak cek terakhir (dipanggil saat onResume). */
    void checkIfStale() { if (System.currentTimeMillis() - lastRun > 45_000) schedule(600); }

    void detectNow() {
        if (busy) return;
        busy = true; lastRun = System.currentTimeMillis();
        new Thread(() -> {
            String cc = null, tz = null, ip = null;
            for (String ep : ENDPOINTS) {
                try {
                    JSONObject o = new JSONObject(fetch(ep));
                    if (o.has("success") && !o.optBoolean("success", true)) continue;
                    if (o.has("status") && !"success".equals(o.optString("status"))) continue;
                    cc = o.optString("country_code", o.optString("countryCode", null));
                    Object t = o.opt("timezone");
                    tz = t instanceof JSONObject ? ((JSONObject) t).optString("id", null) : (t == null ? null : String.valueOf(t));
                    ip = o.optString("ip", o.optString("query", null));
                    if (cc != null && cc.length() == 2) break;
                    cc = null;
                } catch (Throwable ignored) {}
            }
            final String fcc = cc, ftz = tz, fip = ip;
            h.post(() -> { busy = false; if (fcc != null) cb.onRegion(fcc, ftz, fip); });
        }, "geoip").start();
    }

    private static String fetch(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(6000); c.setReadTimeout(6000); c.setUseCaches(false);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36");
        c.setRequestProperty("Accept", "application/json");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] b = new byte[2048]; int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            return bo.toString("UTF-8");
        } finally { c.disconnect(); }
    }
}
