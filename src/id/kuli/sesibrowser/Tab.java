package id.kuli.sesibrowser;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.Window;
import android.webkit.WebView;

/** Satu tab di dalam sesi: WebView sendiri + judul/ikon/thumbnail untuk tab switcher. */
public class Tab {
    private static long NEXT = 1;

    public final long id = NEXT++;
    public WebView web;
    public String url = "", title = "";
    public Bitmap icon, thumb;
    public Tab parent;            // tab yang membuka tab ini (Back di tab ini → kembali ke parent)
    public String pendingUrl;     // tab hasil restore: dimuat malas saat pertama dibuka
    public boolean loaded = false;
    public int progress = 100;
    public boolean loading = false;

    public String displayTitle() {
        if (title != null && !title.trim().isEmpty()) return title.trim();
        String h = host();
        return h.isEmpty() ? "Tab baru" : h;
    }

    public String host() {
        String u = url == null || url.isEmpty() ? pendingUrl : url;
        if (u == null) return "";
        try {
            String h = android.net.Uri.parse(u).getHost();
            return h == null ? "" : h.replaceFirst("^www\\.", "");
        } catch (Exception e) { return ""; }
    }

    /** Snapshot kecil isi halaman untuk kartu di tab switcher (lebar ±360px, hemat memori). */
    public long thumbAt = 0;      // kapan thumbnail terakhir diambil

    /**
     * Snapshot hanya jika thumbnail lama sudah > 400 ms (hindari kerja ganda saat pindah cepat).
     * Pakai PixelCopy: baca langsung dari frame GPU secara asinkron — tidak ada rasterisasi ulang
     * halaman di UI thread (web.draw() sinkron 50–200 ms = hitch saat buka switcher / pindah tab).
     * done dipanggil di UI thread setelah selesai (sukses atau gagal); boleh null.
     */
    public void snapshot(Window win, Runnable done) {
        if (web == null || web.getWidth() <= 0 || web.getHeight() <= 0 || !web.isShown() || win == null) { if (done != null) done.run(); return; }
        long now = android.os.SystemClock.uptimeMillis();
        if (thumb != null && now - thumbAt < 400) { if (done != null) done.run(); return; }
        thumbAt = now;
        try {
            int[] loc = new int[2]; web.getLocationInWindow(loc);
            int srcH = Math.max(1, (int) Math.min(web.getHeight(), web.getWidth() * 1.25f));
            float scale = 360f / web.getWidth();
            int w = Math.max(1, (int) (web.getWidth() * scale));
            int h = Math.max(1, (int) (srcH * scale));
            Rect src = new Rect(loc[0], loc[1], loc[0] + web.getWidth(), loc[1] + srcH);
            final Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
            final WebView wv = web;
            PixelCopy.request(win, src, b, result -> {
                if (result == PixelCopy.SUCCESS && web == wv) {
                    Bitmap old = thumb; thumb = b;
                    if (old != null && old != b) old.recycle();
                } else {
                    b.recycle();
                    if (web == wv) snapshotSoftware();   // fallback: surface belum siap dsb.
                }
                if (done != null) done.run();
            }, new Handler(Looper.getMainLooper()));
        } catch (Throwable e) {
            snapshotSoftware();
            if (done != null) done.run();
        }
    }

    /** Jalur lama (sinkron, software) — hanya sebagai fallback bila PixelCopy gagal. */
    private void snapshotSoftware() {
        if (web == null || web.getWidth() <= 0 || web.getHeight() <= 0) return;
        try {
            float scale = 360f / web.getWidth();
            int w = Math.max(1, (int) (web.getWidth() * scale));
            int h = Math.max(1, (int) (Math.min(web.getHeight(), web.getWidth() * 1.25f) * scale));
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
            Canvas c = new Canvas(b);
            c.scale(scale, scale);
            c.translate(-web.getScrollX(), -web.getScrollY());
            web.draw(c);
            if (thumb != null) thumb.recycle();
            thumb = b;
        } catch (Throwable ignored) {}
    }

    public void destroy() {
        if (thumb != null) { thumb.recycle(); thumb = null; }
        icon = null;
        if (web != null) { try { web.stopLoading(); web.destroy(); } catch (Throwable ignored) {} web = null; }
    }
}
