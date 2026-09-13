package id.kuli.sesibrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manajer unduhan Sesi Browser.
 *  - http/https  → DownloadManager sistem (jalan di latar, ada notifikasi). Kalau DownloadManager
 *                  dinonaktifkan/gagal (umum di ROM tertentu) → pengunduh internal (HttpURLConnection).
 *  - blob:/data: → ditulis langsung ke folder Download (MediaStore di Android 10+, File di Android 9).
 *  Semua muncul di satu daftar dengan progres, buka, batal, hapus.
 */
public final class Downloads {
    static final int PENDING = 0, RUNNING = 1, DONE = 2, FAILED = 3, PAUSED = 4;

    public static final class Item {
        long id, dmId = -1, total = -1, done = 0, ts;
        String name = "", mime = "", url = "", uri = "", reason = "";
        int status = PENDING;
        volatile boolean cancel = false;

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("dmId", dmId); o.put("total", total); o.put("done", done); o.put("ts", ts);
            o.put("name", name); o.put("mime", mime); o.put("url", url); o.put("uri", uri); o.put("reason", reason); o.put("status", status);
            return o;
        }
        static Item from(JSONObject o) {
            Item i = new Item();
            i.id = o.optLong("id"); i.dmId = o.optLong("dmId", -1); i.total = o.optLong("total", -1); i.done = o.optLong("done"); i.ts = o.optLong("ts");
            i.name = o.optString("name"); i.mime = o.optString("mime"); i.url = o.optString("url"); i.uri = o.optString("uri"); i.reason = o.optString("reason");
            i.status = o.optInt("status");
            if (i.dmId < 0 && (i.status == RUNNING || i.status == PENDING)) { i.status = FAILED; i.reason = "Terputus (aplikasi ditutup)"; }
            return i;
        }
    }

    private static Downloads INSTANCE;
    public static synchronized Downloads get(Context c) {
        if (INSTANCE == null) INSTANCE = new Downloads(c.getApplicationContext());
        return INSTANCE;
    }

    private final Context ctx;
    private final SharedPreferences sp;
    private final DownloadManager dm;
    private final List<Item> items = new ArrayList<>();
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private Runnable uiListener;

    private Downloads(Context c) {
        ctx = c;
        sp = c.getSharedPreferences("downloads", Context.MODE_PRIVATE);
        dm = (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
        try {
            JSONArray a = new JSONArray(sp.getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) items.add(Item.from(a.getJSONObject(i)));
        } catch (Exception ignored) {}
    }

    private synchronized void save() {
        JSONArray a = new JSONArray();
        try { for (Item i : items) a.put(i.toJson()); } catch (Exception ignored) {}
        sp.edit().putString("list", a.toString()).apply();
    }

    private void notifyUi() { h.post(() -> { if (uiListener != null) uiListener.run(); }); }

    public synchronized List<Item> all() { return new ArrayList<>(items); }
    public synchronized int activeCount() { int n = 0; for (Item i : items) if (i.status == RUNNING || i.status == PENDING || i.status == PAUSED) n++; return n; }

    // ------------------------------------------------------------------ mulai unduhan
    /** Nama berkas dari URL / Content-Disposition / mime, dijamin unik di folder Download (sebisa mungkin). */
    static String fileName(String url, String cd, String mime) {
        String fn = null;
        try { fn = URLUtil.guessFileName(url, cd, mime); } catch (Throwable ignored) {}
        if (fn == null || fn.isEmpty()) fn = "unduhan";
        // guessFileName memberi .bin untuk mime tak dikenal — perbaiki dari mime yang lebih spesifik
        if (fn.endsWith(".bin") && mime != null) {
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            if (ext != null) fn = fn.substring(0, fn.length() - 4) + "." + ext;
        }
        fn = fn.replaceAll("[\\\\/:*?\"<>|]", "_");
        try {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (new File(dir, fn).exists()) {
                int dot = fn.lastIndexOf('.');
                String base = dot > 0 ? fn.substring(0, dot) : fn, ext = dot > 0 ? fn.substring(dot) : "";
                for (int k = 1; k < 500; k++) { String c = base + " (" + k + ")" + ext; if (!new File(dir, c).exists()) { fn = c; break; } }
            }
        } catch (Throwable ignored) {}
        return fn;
    }

    static String betterMime(String mime, String name) {
        if (mime == null || mime.isEmpty() || "application/octet-stream".equals(mime) || "application/force-download".equals(mime)) {
            int dot = name.lastIndexOf('.');
            if (dot >= 0) {
                String g = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase());
                if (g != null) return g;
            }
            return "application/octet-stream";
        }
        int semi = mime.indexOf(';');
        return semi > 0 ? mime.substring(0, semi).trim() : mime;
    }

    private boolean dmEnabled() {
        if (dm == null) return false;
        try {
            int st = ctx.getPackageManager().getApplicationEnabledSetting("com.android.providers.downloads");
            return st != PackageManager.COMPONENT_ENABLED_STATE_DISABLED && st != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                    && st != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
        } catch (Throwable e) { return true; }
    }

    /** Unduh http/https. Mengembalikan item yang dibuat. */
    public Item start(String url, String ua, String cd, String mime, String referer) {
        Item it = new Item();
        it.id = System.currentTimeMillis();
        it.name = fileName(url, cd, mime);
        it.mime = betterMime(mime, it.name);
        it.url = url; it.ts = it.id;
        String cookie = null;
        try { cookie = CookieManager.getInstance().getCookie(url); } catch (Throwable ignored) {}

        if (dmEnabled()) {
            try {
                DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                if (cookie != null) r.addRequestHeader("Cookie", cookie);
                if (ua != null) r.addRequestHeader("User-Agent", ua);
                if (referer != null && !referer.isEmpty()) r.addRequestHeader("Referer", referer);
                r.addRequestHeader("Accept", "*/*");
                r.setMimeType(it.mime);
                r.setTitle(it.name);
                r.setDescription("Sesi Browser");
                r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, it.name);
                r.setAllowedOverMetered(true);
                r.setAllowedOverRoaming(true);
                it.dmId = dm.enqueue(r);
                it.status = PENDING;
                synchronized (this) { items.add(0, it); }
                save(); notifyUi();
                return it;
            } catch (Throwable e) { it.dmId = -1; }
        }
        synchronized (this) { items.add(0, it); }
        save();
        inApp(it, ua, cookie, referer);
        return it;
    }

    /** Catat unduhan DownloadManager yang dimulai pihak lain (mis. MAX MODE) agar tampil di daftar ini. */
    public Item track(long dmId, String url, String name, String mime) {
        Item it = new Item();
        it.id = System.currentTimeMillis();
        it.dmId = dmId; it.url = url == null ? "" : url;
        it.name = name == null || name.isEmpty() ? "unduhan" : name.substring(name.lastIndexOf('/') + 1);
        it.mime = betterMime(mime, it.name);
        it.ts = it.id; it.status = PENDING;
        synchronized (this) { items.add(0, it); }
        save(); notifyUi();
        return it;
    }

    /** Simpan byte yang sudah ada di memori (blob:/data: URL). */
    public Item saveBytes(final String name, final String mime, final byte[] data) {
        final Item it = new Item();
        it.id = System.currentTimeMillis();
        it.name = uniqueName(name);
        it.mime = betterMime(mime, it.name);
        it.total = data.length; it.ts = it.id; it.status = RUNNING;
        synchronized (this) { items.add(0, it); }
        notifyUi();
        pool.execute(() -> {
            try {
                Uri u = openOutput(it);
                OutputStream out = ctx.getContentResolver().openOutputStream(u);
                if (out == null) throw new IOException("Tidak bisa menulis berkas");
                out.write(data); out.close();
                finishOutput(it, u);
                it.done = data.length; it.status = DONE; it.uri = u.toString();
            } catch (Throwable e) { it.status = FAILED; it.reason = msg(e); }
            save(); notifyUi();
        });
        return it;
    }

    /** Pindahkan berkas sementara (hasil blob) ke folder Download lewat stream — hemat memori untuk berkas besar. */
    public Item saveFile(final String name, final String mime, final java.io.File src) {
        final Item it = new Item();
        it.id = System.currentTimeMillis();
        it.name = uniqueName(name);
        it.mime = betterMime(mime, it.name);
        it.total = src.length(); it.ts = it.id; it.status = RUNNING;
        synchronized (this) { items.add(0, it); }
        notifyUi();
        pool.execute(() -> {
            try {
                Uri u = openOutput(it);
                OutputStream out = ctx.getContentResolver().openOutputStream(u);
                if (out == null) throw new IOException("Tidak bisa menulis berkas");
                java.io.InputStream in = new java.io.FileInputStream(src);
                byte[] b = new byte[256 * 1024]; int n; long done = 0;
                while ((n = in.read(b)) > 0) { out.write(b, 0, n); done += n; it.done = done; }
                in.close(); out.close();
                finishOutput(it, u);
                it.status = DONE; it.uri = u.toString();
            } catch (Throwable e) { it.status = FAILED; it.reason = msg(e); }
            finally { src.delete(); }
            save(); notifyUi();
        });
        return it;
    }

    private static String uniqueName(String n) { return fileName("https://x/" + Uri.encode(n), null, null); }

    // ------------------------------------------------------------------ pengunduh internal
    private void inApp(final Item it, final String ua, final String cookie, final String referer) {
        it.status = RUNNING; it.reason = ""; notifyUi();
        pool.execute(() -> {
            HttpURLConnection c = null; Uri out = null;
            try {
                c = (HttpURLConnection) new URL(it.url).openConnection();
                c.setInstanceFollowRedirects(true);
                c.setConnectTimeout(15000); c.setReadTimeout(30000);
                if (ua != null) c.setRequestProperty("User-Agent", ua);
                if (cookie != null) c.setRequestProperty("Cookie", cookie);
                if (referer != null && !referer.isEmpty()) c.setRequestProperty("Referer", referer);
                c.setRequestProperty("Accept", "*/*");
                int code = c.getResponseCode();
                if (code >= 400) throw new IOException("HTTP " + code);
                String cd = c.getHeaderField("Content-Disposition");
                if (cd != null && cd.toLowerCase().contains("filename")) { it.name = fileName(it.url, cd, c.getContentType()); }
                it.mime = betterMime(c.getContentType(), it.name);
                it.total = c.getContentLengthLong();
                out = openOutput(it);
                OutputStream os = ctx.getContentResolver().openOutputStream(out);
                if (os == null) throw new IOException("Tidak bisa menulis berkas");
                InputStream in = c.getInputStream();
                byte[] buf = new byte[64 * 1024]; int n; long done = 0, last = 0;
                while ((n = in.read(buf)) > 0) {
                    if (it.cancel) throw new IOException("Dibatalkan");
                    os.write(buf, 0, n); done += n; it.done = done;
                    long now = System.currentTimeMillis();
                    if (now - last > 250) { last = now; notifyUi(); }
                }
                os.flush(); os.close(); in.close();
                finishOutput(it, out);
                it.status = DONE; it.uri = out.toString();
            } catch (Throwable e) {
                it.status = FAILED; it.reason = msg(e);
                if (out != null) { try { ctx.getContentResolver().delete(out, null, null); } catch (Throwable ignored) {} }
            } finally { if (c != null) c.disconnect(); }
            save(); notifyUi();
        });
    }

    private static String msg(Throwable e) {
        String m = e.getMessage();
        if (m == null || m.isEmpty()) m = e.getClass().getSimpleName();
        return m.length() > 80 ? m.substring(0, 80) : m;
    }

    /** Buat tujuan tulis di folder Download publik → content:// URI. */
    private Uri openOutput(Item it) throws IOException {
        ContentResolver cr = ctx.getContentResolver();
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, it.name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, it.mime);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri u = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (u == null) throw new IOException("MediaStore menolak");
            return u;
        }
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Folder Download tidak bisa dibuat");
        File f = new File(dir, it.name);
        new FileOutputStream(f).close();
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DATA, f.getAbsolutePath());
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, it.name);
        v.put(MediaStore.MediaColumns.MIME_TYPE, it.mime);
        Uri u = cr.insert(MediaStore.Files.getContentUri("external"), v);
        if (u == null) throw new IOException("MediaStore menolak");
        return u;
    }

    private void finishOutput(Item it, Uri u) {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.IS_PENDING, 0);
            try { ctx.getContentResolver().update(u, v, null, null); } catch (Throwable ignored) {}
        }
    }

    // ------------------------------------------------------------------ status dari DownloadManager
    public void refresh() {
        List<Item> dmItems = new ArrayList<>();
        synchronized (this) { for (Item i : items) if (i.dmId >= 0 && i.status != DONE && i.status != FAILED) dmItems.add(i); }
        if (dmItems.isEmpty() || dm == null) return;
        long[] ids = new long[dmItems.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = dmItems.get(i).dmId;
        List<Long> seen = new ArrayList<>();
        Cursor c = null;
        try {
            c = dm.query(new DownloadManager.Query().setFilterById(ids));
            if (c != null) while (c.moveToNext()) {
                long id = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID));
                seen.add(id);
                Item it = null; for (Item x : dmItems) if (x.dmId == id) it = x;
                if (it == null) continue;
                int st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                it.done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                it.total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                String mt = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE));
                if (mt != null && !mt.isEmpty()) it.mime = mt;
                switch (st) {
                    case DownloadManager.STATUS_SUCCESSFUL: it.status = DONE; try { Uri u = dm.getUriForDownloadedFile(id); if (u != null) it.uri = u.toString(); } catch (Throwable ignored) {} break;
                    case DownloadManager.STATUS_FAILED: it.status = FAILED; it.reason = reason(c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))); break;
                    case DownloadManager.STATUS_PAUSED: it.status = PAUSED; it.reason = reason(c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))); break;
                    case DownloadManager.STATUS_RUNNING: it.status = RUNNING; break;
                    default: it.status = PENDING;
                }
            }
        } catch (Throwable ignored) {
        } finally { if (c != null) c.close(); }
        for (Item it : dmItems) if (!seen.contains(it.dmId) && it.status != DONE) { it.status = FAILED; it.reason = "Dihapus dari sistem"; }
        save();
    }

    private static String reason(int r) {
        switch (r) {
            case DownloadManager.ERROR_INSUFFICIENT_SPACE: return "Ruang penyimpanan penuh";
            case DownloadManager.ERROR_HTTP_DATA_ERROR: case DownloadManager.ERROR_UNHANDLED_HTTP_CODE: return "Server menolak";
            case DownloadManager.ERROR_FILE_ALREADY_EXISTS: return "Berkas sudah ada";
            case DownloadManager.ERROR_TOO_MANY_REDIRECTS: return "Terlalu banyak redirect";
            case DownloadManager.ERROR_CANNOT_RESUME: return "Tidak bisa dilanjutkan";
            case DownloadManager.ERROR_FILE_ERROR: return "Gagal menulis berkas";
            case DownloadManager.PAUSED_WAITING_FOR_NETWORK: return "Menunggu jaringan";
            case DownloadManager.PAUSED_WAITING_TO_RETRY: return "Mencoba lagi…";
            case DownloadManager.PAUSED_QUEUED_FOR_WIFI: return "Menunggu Wi-Fi";
            case DownloadManager.ERROR_DEVICE_NOT_FOUND: return "Penyimpanan tidak ditemukan";
            default: return "Gagal (" + r + ")";
        }
    }

    // ------------------------------------------------------------------ aksi
    public void open(Activity a, Item it) {
        Uri u = null;
        if (it.dmId >= 0 && dm != null) { try { u = dm.getUriForDownloadedFile(it.dmId); } catch (Throwable ignored) {} }
        if (u == null && it.uri != null && !it.uri.isEmpty()) u = Uri.parse(it.uri);
        if (u == null) { Toast.makeText(a, "Berkas tidak ditemukan", Toast.LENGTH_SHORT).show(); return; }
        String mime = it.mime;
        if (it.dmId >= 0 && dm != null) { try { String m = dm.getMimeTypeForDownloadedFile(it.dmId); if (m != null) mime = m; } catch (Throwable ignored) {} }
        if (mime == null || mime.isEmpty()) mime = "*/*";
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(u, mime);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try { a.startActivity(i); }
        catch (Throwable e) {
            i.setDataAndType(u, "*/*");
            try { a.startActivity(Intent.createChooser(i, "Buka dengan")); }
            catch (Throwable e2) { Toast.makeText(a, "Tidak ada aplikasi untuk membuka berkas ini", Toast.LENGTH_SHORT).show(); }
        }
    }

    public void share(Activity a, Item it) {
        Uri u = null;
        if (it.dmId >= 0 && dm != null) { try { u = dm.getUriForDownloadedFile(it.dmId); } catch (Throwable ignored) {} }
        if (u == null && !it.uri.isEmpty()) u = Uri.parse(it.uri);
        if (u == null) return;
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(it.mime.isEmpty() ? "*/*" : it.mime);
        i.putExtra(Intent.EXTRA_STREAM, u);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { a.startActivity(Intent.createChooser(i, "Bagikan")); } catch (Throwable ignored) {}
    }

    /** Batalkan (jika berjalan) atau hapus dari daftar; deleteFile = ikut hapus berkasnya. */
    public void remove(Item it, boolean deleteFile) {
        boolean running = it.status == RUNNING || it.status == PENDING || it.status == PAUSED;
        if (it.dmId >= 0 && dm != null && (running || deleteFile)) { try { dm.remove(it.dmId); } catch (Throwable ignored) {} }
        else if (deleteFile && !it.uri.isEmpty()) { try { ctx.getContentResolver().delete(Uri.parse(it.uri), null, null); } catch (Throwable ignored) {} }
        it.cancel = true;
        synchronized (this) { items.remove(it); }
        save(); notifyUi();
    }

    public void retry(Item it, String ua) {
        remove(it, false);
        if (it.url != null && it.url.startsWith("http")) start(it.url, ua, null, it.mime, null);
    }

    public void clearFinished() {
        synchronized (this) { for (Item i : new ArrayList<>(items)) if (i.status == DONE || i.status == FAILED) items.remove(i); }
        save(); notifyUi();
    }

    // ------------------------------------------------------------------ UI: bottom sheet
    static String size(long b) {
        if (b < 0) return "";
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format(java.util.Locale.US, "%.0f KB", b / 1024f);
        if (b < 1024L * 1024 * 1024) return String.format(java.util.Locale.US, "%.1f MB", b / 1048576f);
        return String.format(java.util.Locale.US, "%.2f GB", b / 1073741824f);
    }

    static int iconFor(String mime) {
        if (mime == null) return R.drawable.ic_file;
        if (mime.startsWith("image/")) return R.drawable.ic_file_image;
        if (mime.startsWith("video/")) return R.drawable.ic_file_video;
        if (mime.startsWith("audio/")) return R.drawable.ic_file_audio;
        if (mime.contains("zip") || mime.contains("rar") || mime.contains("7z") || mime.contains("tar")) return R.drawable.ic_file_zip;
        if (mime.contains("android.package")) return R.drawable.ic_file_apk;
        if (mime.contains("pdf") || mime.startsWith("text/") || mime.contains("document") || mime.contains("word") || mime.contains("sheet")) return R.drawable.ic_file_doc;
        return R.drawable.ic_file;
    }

    public void showSheet(final Activity a, final String ua) {
        final View v = LayoutInflater.from(a).inflate(R.layout.sheet_downloads, null);
        final Dialog d = Ui.sheet(a, v);
        final LinearLayout list = v.findViewById(R.id.dlList);
        final TextView count = v.findViewById(R.id.dlCount);
        final View empty = v.findViewById(R.id.dlEmpty);
        final View clear = v.findViewById(R.id.dlClear);
        Ui.pressable(clear);
        clear.setOnClickListener(x -> { Ui.haptic(x); clearFinished(); });
        v.findViewById(R.id.dlOpenFolder).setOnClickListener(x -> {
            Intent i = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
            try { a.startActivity(i); } catch (Throwable e) { Toast.makeText(a, "Buka aplikasi Files → Download", Toast.LENGTH_SHORT).show(); }
        });

        final java.util.Map<Long, View> rows = new java.util.HashMap<>();
        final Runnable render = () -> {
            List<Item> all = all();
            int act = activeCount();
            count.setText(all.isEmpty() ? "" : all.size() + " berkas" + (act > 0 ? " · " + act + " berjalan" : ""));
            empty.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
            clear.setVisibility(all.size() > act ? View.VISIBLE : View.GONE);
            // sinkronkan baris tanpa rebuild total (progres update tiap 0,6 s)
            List<Long> ids = new ArrayList<>();
            for (int idx = 0; idx < all.size(); idx++) {
                Item it = all.get(idx);
                ids.add(it.id);
                View row = rows.get(it.id);
                if (row == null) {
                    row = LayoutInflater.from(a).inflate(R.layout.item_download, list, false);
                    rows.put(it.id, row);
                    row.setAlpha(0f); row.setTranslationY(Ui.dp(a, 12));
                    row.animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(Ui.SNAP).start();
                }
                if (row.getParent() == null) list.addView(row, Math.min(idx, list.getChildCount()));
                else if (list.indexOfChild(row) != idx) { list.removeView(row); list.addView(row, Math.min(idx, list.getChildCount())); }
                bindRow(a, row, it, d, ua);
            }
            for (Long id : new ArrayList<>(rows.keySet())) if (!ids.contains(id)) {
                final View row = rows.remove(id);
                row.animate().alpha(0f).translationX(row.getWidth() * 0.5f).setDuration(180).withEndAction(() -> list.removeView(row)).start();
            }
        };
        final Runnable[] tick = new Runnable[1];
        tick[0] = () -> { refresh(); render.run(); if (activeCount() > 0) h.postDelayed(tick[0], 600); };
        uiListener = () -> { render.run(); h.removeCallbacks(tick[0]); if (activeCount() > 0) h.postDelayed(tick[0], 600); };
        d.setOnDismissListener(x -> { uiListener = null; h.removeCallbacks(tick[0]); });
        tick[0].run();
        d.show();
    }

    private void bindRow(final Activity a, View row, final Item it, final Dialog d, final String ua) {
        ((ImageView) row.findViewById(R.id.dlIcon)).setImageResource(iconFor(it.mime));
        ((TextView) row.findViewById(R.id.dlName)).setText(it.name);
        TextView sub = row.findViewById(R.id.dlSub);
        ProgressBar pb = row.findViewById(R.id.dlProgress);
        ImageView action = row.findViewById(R.id.dlAction);
        boolean running = it.status == RUNNING || it.status == PENDING || it.status == PAUSED;
        String s;
        switch (it.status) {
            case DONE: s = size(it.total > 0 ? it.total : it.done) + " · Selesai"; sub.setTextColor(0xFF6FB37F); break;
            case FAILED: s = it.reason.isEmpty() ? "Gagal" : it.reason; sub.setTextColor(0xFFE0655C); break;
            case PAUSED: s = it.reason.isEmpty() ? "Dijeda" : it.reason; sub.setTextColor(0xFFA3A5AC); break;
            case RUNNING:
                s = it.total > 0 ? size(it.done) + " / " + size(it.total) + " · " + (int) (it.done * 100 / it.total) + "%" : size(it.done) + " · Mengunduh…";
                sub.setTextColor(0xFFA3A5AC); break;
            default: s = "Menunggu…"; sub.setTextColor(0xFFA3A5AC);
        }
        sub.setText(s);
        pb.setVisibility(running ? View.VISIBLE : View.GONE);
        if (running) {
            if (it.total > 0) { pb.setIndeterminate(false); int p = (int) (it.done * 100 / it.total); if (Math.abs(pb.getProgress() - p) > 0) android.animation.ObjectAnimator.ofInt(pb, "progress", p).setDuration(400).start(); }
            else pb.setIndeterminate(true);
        }
        action.setImageResource(running ? R.drawable.ic_close : (it.status == FAILED ? R.drawable.ic_reload : R.drawable.ic_more));
        action.setOnClickListener(x -> {
            Ui.haptic(x);
            if (running) remove(it, true);
            else if (it.status == FAILED) retry(it, ua);
            else moreMenu(a, it, d, ua);
        });
        row.setOnClickListener(x -> { if (it.status == DONE) open(a, it); else if (it.status == FAILED) retry(it, ua); });
        row.setOnLongClickListener(x -> { moreMenu(a, it, d, ua); return true; });
    }

    private void moreMenu(final Activity a, final Item it, final Dialog d, final String ua) {
        final List<String> m = new ArrayList<>();
        if (it.status == DONE) { m.add("Buka"); m.add("Bagikan"); }
        if (!it.url.isEmpty() && it.url.startsWith("http")) { m.add("Salin tautan"); m.add("Unduh ulang"); }
        m.add("Hapus dari daftar");
        if (it.status == DONE) m.add("Hapus berkas");
        new AlertDialog.Builder(a).setTitle(it.name).setItems(m.toArray(new String[0]), (dd, w) -> {
            switch (m.get(w)) {
                case "Buka": open(a, it); break;
                case "Bagikan": share(a, it); break;
                case "Salin tautan": {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("url", it.url));
                    Toast.makeText(a, "Disalin", Toast.LENGTH_SHORT).show(); break;
                }
                case "Unduh ulang": retry(it, ua); break;
                case "Hapus dari daftar": remove(it, false); break;
                case "Hapus berkas": remove(it, true); Toast.makeText(a, "Berkas dihapus", Toast.LENGTH_SHORT).show(); break;
            }
        }).show();
    }
}
