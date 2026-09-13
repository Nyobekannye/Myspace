package id.kuli.sesibrowser;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Emulates the Chrome extension runtime (Sesi MAX MODE) on top of Sesi Browser:
 * message routing, storage, tabs, downloads. `page` always points at the WebView of the active tab.
 */
public class ExtRuntime {
    private static final String TAG = "Whempy/rt";
    public static final String HOME = "https://www.dola.com/";

    final Activity act;
    final Handler ui = new Handler(Looper.getMainLooper());
    final SharedPreferences prefs;
    final DownloadManager dm;
    WebView page, bg, popup;
    volatile String currentUrl = HOME;
    volatile String currentTitle = "Dola";
    volatile String userAgent = "";

    private boolean bgReady = false;
    private final List<String> bgQueue = new ArrayList<>();
    private final Map<String, int[]> noHandler = new HashMap<>();
    private final LinkedHashSet<String> answered = new LinkedHashSet<>();
    private final Map<Long, JSONObject> dlMeta = new HashMap<>();
    /** cbId → WebView tab pengirim (multi-tab: balasan harus kembali ke tab asal, bukan ke tab aktif). */
    private final Map<String, WebView> pendingSource = new HashMap<>();

    interface UiHooks { void openUrl(String url); void openPopup(); void closePopup(); void reloadTab(); }
    UiHooks hooks;

    public ExtRuntime(Activity act) {
        this.act = act;
        prefs = act.getSharedPreferences("whempy_storage", Context.MODE_PRIVATE);
        dm = (DownloadManager) act.getSystemService(Context.DOWNLOAD_SERVICE);
        IntentFilter f = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) act.registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);
        else act.registerReceiver(receiver, f);
    }

    public void destroy() { try { act.unregisterReceiver(receiver); } catch (Exception ignored) {} }

    // ---------------------------------------------------------------- eval helpers
    private void eval(final WebView w, final String js) {
        if (w == null) return;
        ui.post(new Runnable() { public void run() { try { w.evaluateJavascript(js, null); } catch (Exception e) { Log.w(TAG, "eval failed", e); } } });
    }
    private void evalBg(String js) {
        synchronized (bgQueue) { if (!bgReady) { bgQueue.add(js); return; } }
        eval(bg, js);
    }
    void onBgReady() {
        List<String> q;
        synchronized (bgQueue) { bgReady = true; q = new ArrayList<>(bgQueue); bgQueue.clear(); }
        for (String js : q) eval(bg, js);
        Log.i(TAG, "background ready, flushed " + q.size());
    }
    private void broadcast(String js) { eval(page, js); evalBg(js); eval(popup, js); }
    private WebView ctxView(String ctx) {
        if ("page".equals(ctx)) return page;
        if ("popup".equals(ctx)) return popup;
        return bg;
    }
    private static String q(String s) { return JSONObject.quote(s == null ? "" : s); }

    // ---------------------------------------------------------------- messaging
    private String senderJson(String from) {
        try {
            JSONObject s = new JSONObject();
            s.put("id", "whempy-maxmode");
            if ("page".equals(from)) {
                JSONObject tab = tabObj();
                s.put("tab", tab); s.put("url", currentUrl); s.put("frameId", 0);
            } else {
                s.put("url", "file:///android_asset/ext/popup.html");
            }
            return s.toString();
        } catch (Exception e) { return "{}"; }
    }
    private void deliver(String targetCtx, String msgJson, String senderJson, String cbId, String origin) {
        String js = "__whempyShim.deliverMessage(" + q(msgJson) + "," + q(senderJson) + "," + q(cbId) + "," + q(origin) + ")";
        if ("background".equals(targetCtx)) evalBg(js); else eval(ctxView(targetCtx), js);
    }
    void sendMessage(String from, String msgJson, String cbId) { sendMessage(from, msgJson, cbId, null); }
    void sendMessage(String from, String msgJson, String cbId, WebView source) {
        if (source != null) synchronized (pendingSource) { pendingSource.put(cbId, source); if (pendingSource.size() > 2000) { Iterator<String> it = pendingSource.keySet().iterator(); it.next(); it.remove(); } }
        List<String> targets = new ArrayList<>();
        if ("page".equals(from)) { targets.add("background"); if (popup != null) targets.add("popup"); }
        else if ("popup".equals(from)) targets.add("background");
        else if (popup != null) targets.add("popup");
        if (targets.isEmpty()) { respond(cbId, from, null); return; }
        synchronized (noHandler) { noHandler.put(cbId, new int[]{targets.size()}); }
        String sender = senderJson(from);
        for (String t : targets) deliver(t, msgJson, sender, cbId, from);
    }
    void tabsSendMessage(String from, String msgJson, String cbId) {
        synchronized (noHandler) { noHandler.put(cbId, new int[]{1}); }
        deliver("page", msgJson, senderJson(from), cbId, from);
    }
    void respond(String cbId, String origin, String json) {
        synchronized (noHandler) {
            if (answered.contains(cbId)) return;
            if ("__NOHANDLER__".equals(json)) {
                int[] c = noHandler.get(cbId);
                if (c != null && --c[0] > 0) return;
                json = null;
            }
            answered.add(cbId); noHandler.remove(cbId);
            if (answered.size() > 3000) { Iterator<String> it = answered.iterator(); it.next(); it.remove(); }
        }
        WebView target = null;
        synchronized (pendingSource) { target = pendingSource.remove(cbId); }
        if (target == null) target = ctxView(origin);
        eval(target, "__whempyShim.deliverResponse(" + q(cbId) + "," + (json == null ? "null" : q(json)) + ")");
    }

    // ---------------------------------------------------------------- storage
    synchronized String storageGet() { return prefs.getString("data", "{}"); }
    synchronized void storageSet(String itemsJson) {
        try {
            JSONObject all = new JSONObject(storageGet());
            JSONObject items = new JSONObject(itemsJson);
            JSONObject changes = new JSONObject();
            Iterator<String> it = items.keys();
            while (it.hasNext()) {
                String k = it.next(); Object nv = items.get(k); Object ov = all.opt(k);
                JSONObject ch = new JSONObject();
                if (ov != null) ch.put("oldValue", ov);
                ch.put("newValue", nv); changes.put(k, ch); all.put(k, nv);
            }
            prefs.edit().putString("data", all.toString()).apply();
            if (changes.length() > 0) broadcast("__whempyShim.fireStorageChanged(" + q(changes.toString()) + ")");
        } catch (Exception e) { Log.w(TAG, "storageSet", e); }
    }
    synchronized void storageRemove(String keysJson) {
        try {
            JSONObject all = new JSONObject(storageGet());
            JSONArray keys = new JSONArray(keysJson);
            JSONObject changes = new JSONObject();
            for (int i = 0; i < keys.length(); i++) {
                String k = keys.getString(i);
                if (all.has(k)) { JSONObject ch = new JSONObject(); ch.put("oldValue", all.get(k)); changes.put(k, ch); all.remove(k); }
            }
            prefs.edit().putString("data", all.toString()).apply();
            if (changes.length() > 0) broadcast("__whempyShim.fireStorageChanged(" + q(changes.toString()) + ")");
        } catch (Exception e) { Log.w(TAG, "storageRemove", e); }
    }
    synchronized void storageClear() {
        try { JSONArray keys = new JSONArray(); Iterator<String> it = new JSONObject(storageGet()).keys(); while (it.hasNext()) keys.put(it.next()); storageRemove(keys.toString()); } catch (Exception ignored) {}
    }

    // ---------------------------------------------------------------- tabs / ui
    private JSONObject tabObj() throws Exception {
        JSONObject t = new JSONObject();
        t.put("id", 1); t.put("index", 0); t.put("windowId", 1); t.put("active", true); t.put("highlighted", true);
        t.put("url", currentUrl); t.put("pendingUrl", currentUrl); t.put("title", currentTitle == null || currentTitle.isEmpty() ? "Dola" : currentTitle); t.put("status", "complete");
        t.put("lastAccessed", System.currentTimeMillis()); t.put("incognito", false); t.put("pinned", false);
        return t;
    }
    String tabsQuery() { try { return new JSONArray().put(tabObj()).toString(); } catch (Exception e) { return "[]"; } }
    void openUrl(final String url) { ui.post(new Runnable() { public void run() { if (hooks != null) hooks.openUrl(url); } }); }
    void openPopup() { ui.post(new Runnable() { public void run() { if (hooks != null) hooks.openPopup(); } }); }
    void closePopup() { ui.post(new Runnable() { public void run() { if (hooks != null) hooks.closePopup(); } }); }
    void reloadTab() { ui.post(new Runnable() { public void run() { if (hooks != null) hooks.reloadTab(); } }); }
    void notifyTabUpdated() { String js = "__whempyShim.fireTabUpdated(" + q(tabsQuery().replaceFirst("^\\[", "").replaceFirst("\\]$", "")) + ")"; evalBg(js); eval(popup, js); }

    /** chrome.runtime.getURL: images are returned as data: URLs so https pages can display them. */
    String getURL(String path) {
        String p = path.startsWith("/") ? path.substring(1) : path;
        String lower = p.toLowerCase();
        String mime = lower.endsWith(".svg") ? "image/svg+xml" : lower.endsWith(".png") ? "image/png" : lower.endsWith(".css") ? "text/css" : null;
        if (mime != null) {
            try {
                InputStream in = act.getAssets().open("ext/" + p);
                ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n); in.close();
                return "data:" + mime + ";base64," + Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
            } catch (Exception e) { /* fall through */ }
        }
        return "file:///android_asset/ext/" + p;
    }

    // ---------------------------------------------------------------- downloads
    private static String sanitizeSegment(String s) {
        s = s.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F]", "_").trim();
        if (s.isEmpty() || s.equals(".") || s.equals("..")) s = "video";
        return s;
    }
    String download(String json) {
        try {
            JSONObject o = new JSONObject(json);
            String url = o.getString("url");
            if (!url.startsWith("http://") && !url.startsWith("https://")) return "ERR:Only http(s) URLs are supported on Android";
            if (Build.VERSION.SDK_INT < 29 && act.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ui.post(new Runnable() { public void run() { try { act.requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 44); } catch (Throwable ignored) {} } });
                return "ERR:Izin penyimpanan diperlukan — coba lagi setelah mengizinkan";
            }
            String filename = o.optString("filename", "");
            if (filename.isEmpty()) filename = URLUtil.guessFileName(url, null, "video/mp4");
            String[] parts = filename.replace('\\', '/').split("/");
            StringBuilder rel = new StringBuilder();
            for (int i = 0; i < parts.length; i++) { if (parts[i].isEmpty()) continue; if (rel.length() > 0) rel.append('/'); rel.append(sanitizeSegment(parts[i])); }
            String relPath = rel.toString();
            File base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            String conflict = o.optString("conflictAction", "uniquify");
            if (!"overwrite".equals(conflict)) {
                File f = new File(base, relPath);
                if (f.exists()) {
                    String dir = relPath.contains("/") ? relPath.substring(0, relPath.lastIndexOf('/') + 1) : "";
                    String name = relPath.substring(dir.length());
                    int dot = name.lastIndexOf('.');
                    String stem = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
                    int n = 1;
                    while (new File(base, dir + stem + " (" + n + ")" + ext).exists()) n++;
                    relPath = dir + stem + " (" + n + ")" + ext;
                }
            }
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, relPath);
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setTitle(relPath.substring(relPath.lastIndexOf('/') + 1));
            req.setDescription("Sesi Browser · MAX MODE");
            if (relPath.toLowerCase().endsWith(".mp4")) req.setMimeType("video/mp4");
            req.setAllowedOverMetered(true); req.setAllowedOverRoaming(true);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) req.addRequestHeader("Cookie", cookie);
            if (!userAgent.isEmpty()) req.addRequestHeader("User-Agent", userAgent);
            req.addRequestHeader("Referer", currentUrl);
            JSONArray headers = o.optJSONArray("headers");
            if (headers != null) for (int i = 0; i < headers.length(); i++) {
                JSONObject h = headers.getJSONObject(i);
                String hn = h.optString("name", "");
                if (!hn.isEmpty() && !hn.equalsIgnoreCase("cookie")) req.addRequestHeader(hn, h.optString("value", ""));
            }
            long id = dm.enqueue(req);
            // Tampil juga di daftar unduhan Sesi Browser (progres, buka, bagikan)
            try { Downloads.get(act).track(id, url, relPath, relPath.toLowerCase().endsWith(".mp4") ? "video/mp4" : null); } catch (Throwable ignored) {}
            JSONObject meta = new JSONObject(); meta.put("url", url); meta.put("filename", new File(base, relPath).getAbsolutePath());
            synchronized (dlMeta) { dlMeta.put(id, meta); }
            JSONObject created = new JSONObject(); created.put("id", id); created.put("url", url); created.put("filename", meta.getString("filename")); created.put("state", "in_progress");
            evalBg("__whempyShim.fireDownloadCreated(" + q(created.toString()) + ")");
            Log.i(TAG, "download #" + id + " -> " + relPath);
            return String.valueOf(id);
        } catch (Exception e) {
            Log.w(TAG, "download failed", e);
            return "ERR:" + e.getMessage();
        }
    }
    private JSONObject itemFromCursor(Cursor c) throws Exception {
        long id = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID));
        int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
        long recv = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
        long total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
        String localUri = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
        String uri = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_URI));
        int reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
        String state = status == DownloadManager.STATUS_SUCCESSFUL ? "complete" : status == DownloadManager.STATUS_FAILED ? "interrupted" : "in_progress";
        JSONObject meta; synchronized (dlMeta) { meta = dlMeta.get(id); }
        String filename = localUri != null && localUri.startsWith("file://") ? Uri.parse(localUri).getPath() : (meta != null ? meta.optString("filename") : "");
        JSONObject it = new JSONObject();
        it.put("id", id); it.put("url", uri); it.put("finalUrl", uri); it.put("filename", filename); it.put("state", state);
        it.put("bytesReceived", recv); it.put("totalBytes", total); it.put("fileSize", total); it.put("exists", status == DownloadManager.STATUS_SUCCESSFUL);
        it.put("paused", status == DownloadManager.STATUS_PAUSED); it.put("canResume", false); it.put("incognito", false); it.put("danger", "safe"); it.put("mime", "video/mp4");
        it.put("startTime", new java.util.Date().toString());
        if (status == DownloadManager.STATUS_FAILED) it.put("error", reason == DownloadManager.ERROR_INSUFFICIENT_SPACE ? "FILE_NO_SPACE" : reason >= 400 ? "SERVER_FAILED" : "NETWORK_FAILED");
        return it;
    }
    String downloadSearch(String json) {
        JSONArray out = new JSONArray();
        try {
            JSONObject qo = new JSONObject(json);
            DownloadManager.Query dq = new DownloadManager.Query();
            if (qo.has("id")) dq.setFilterById(qo.getLong("id"));
            if (qo.has("state")) {
                String st = qo.getString("state");
                if ("in_progress".equals(st)) dq.setFilterByStatus(DownloadManager.STATUS_PENDING | DownloadManager.STATUS_RUNNING | DownloadManager.STATUS_PAUSED);
                else if ("complete".equals(st)) dq.setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL);
                else if ("interrupted".equals(st)) dq.setFilterByStatus(DownloadManager.STATUS_FAILED);
            }
            Cursor c = dm.query(dq);
            if (c != null) { while (c.moveToNext()) out.put(itemFromCursor(c)); c.close(); }
        } catch (Exception e) { Log.w(TAG, "downloadSearch", e); }
        return out.toString();
    }
    void downloadRemove(String id) { try { dm.remove(Long.parseLong(id)); } catch (Exception ignored) {} }
    String downloadErase(String json) {
        JSONArray ids = new JSONArray();
        try {
            JSONObject qo = new JSONObject(json);
            if (qo.has("id")) { long id = qo.getLong("id"); dm.remove(id); ids.put(id); }
        } catch (Exception ignored) {}
        return ids.toString();
    }
    void downloadOpen() {
        try { Intent i = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS); i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); act.startActivity(i); } catch (Exception ignored) {}
    }
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id < 0) return;
            try {
                Cursor c = dm.query(new DownloadManager.Query().setFilterById(id));
                if (c != null && c.moveToFirst()) {
                    JSONObject it = itemFromCursor(c);
                    JSONObject delta = new JSONObject();
                    delta.put("id", id);
                    delta.put("state", new JSONObject().put("previous", "in_progress").put("current", it.getString("state")));
                    if (it.has("error")) delta.put("error", new JSONObject().put("current", it.getString("error")));
                    delta.put("filename", new JSONObject().put("current", it.optString("filename")));
                    String js = "__whempyShim.fireDownloadChanged(" + q(delta.toString()) + ")";
                    evalBg(js); eval(popup, js);
                }
                if (c != null) c.close();
            } catch (Exception e) { Log.w(TAG, "download complete", e); }
        }
    };

    // ---------------------------------------------------------------- cookies
    String cookiesGet(String url) { try { String c = CookieManager.getInstance().getCookie(url); return c == null ? "" : c; } catch (Exception e) { return ""; } }
    void cookiesSet(String url, String cookie) { try { CookieManager.getInstance().setCookie(url, cookie); CookieManager.getInstance().flush(); } catch (Exception ignored) {} }

    // ---------------------------------------------------------------- Google login autofill (native-armed, survives navigation)
    private volatile String pendingEmail, pendingPassword;
    private volatile long pendingExpiry = 0;
    private static boolean isGoogleHost(String url) {
        try { String h = Uri.parse(url).getHost(); return h != null && (h.equals("accounts.google.com") || h.endsWith(".accounts.google.com")); } catch (Exception e) { return false; }
    }
    /** Called from the popup ("Isi ke Google"): arms the fill for up to 5 minutes and fires immediately into the current page. */
    void armAndFireFill(String email, String password) {
        pendingEmail = email; pendingPassword = password; pendingExpiry = System.currentTimeMillis() + 5 * 60 * 1000;
        fireFill(page);
    }
    private void fireFill(WebView w) {
        if (w == null || pendingExpiry == 0) return;
        eval(w, "window.__whempyFillLogin && window.__whempyFillLogin(" + q(pendingEmail) + "," + q(pendingPassword) + ")");
    }
    /** Called on every onPageFinished of the main page WebView. Re-arms the fill after Google's multi-step navigations. */
    void reArmFillIfPending(WebView w, String url) {
        if (pendingExpiry == 0) return;
        if (System.currentTimeMillis() > pendingExpiry) { pendingEmail = null; pendingPassword = null; pendingExpiry = 0; return; }
        if (isGoogleHost(url)) fireFill(w);
    }

    // ---------------------------------------------------------------- saved accounts (local-only, AES/GCM via Android Keystore)
    private static final String KEYSTORE_ALIAS = "whempy_creds_key";
    private SharedPreferences credPrefs() { return act.getSharedPreferences("whempy_creds", Context.MODE_PRIVATE); }

    private java.security.Key credKey() throws Exception {
        java.security.KeyStore ks = java.security.KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(KEYSTORE_ALIAS)) {
            javax.crypto.KeyGenerator kg = javax.crypto.KeyGenerator.getInstance(
                    android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new android.security.keystore.KeyGenParameterSpec.Builder(KEYSTORE_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT | android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            kg.generateKey();
        }
        return ks.getKey(KEYSTORE_ALIAS, null);
    }
    private String encrypt(String plain) throws Exception {
        javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        c.init(javax.crypto.Cipher.ENCRYPT_MODE, credKey());
        byte[] iv = c.getIV();
        byte[] ct = c.doFinal(plain.getBytes("UTF-8"));
        byte[] out = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ct, 0, out, iv.length, ct.length);
        return Base64.encodeToString(out, Base64.NO_WRAP);
    }
    private String decrypt(String b64) throws Exception {
        byte[] all = Base64.decode(b64, Base64.NO_WRAP);
        byte[] iv = new byte[12], ct = new byte[all.length - 12];
        System.arraycopy(all, 0, iv, 0, 12);
        System.arraycopy(all, 12, ct, 0, ct.length);
        javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        c.init(javax.crypto.Cipher.DECRYPT_MODE, credKey(), new javax.crypto.spec.GCMParameterSpec(128, iv));
        return new String(c.doFinal(ct), "UTF-8");
    }
    private synchronized JSONArray credsArray() {
        try { return new JSONArray(credPrefs().getString("accounts", "[]")); } catch (Exception e) { return new JSONArray(); }
    }
    private synchronized void saveCredsArray(JSONArray arr) { credPrefs().edit().putString("accounts", arr.toString()).apply(); }

    /** Returns JSON array of {id,label,email} — passwords never leave native code once saved. */
    String credsList() {
        try {
            JSONArray arr = credsArray(), out = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject a = arr.getJSONObject(i);
                JSONObject o = new JSONObject(); o.put("id", a.getString("id")); o.put("label", a.optString("label", a.getString("email"))); o.put("email", a.getString("email"));
                out.put(o);
            }
            return out.toString();
        } catch (Exception e) { Log.w(TAG, "credsList", e); return "[]"; }
    }
    /** json: {email,password,label?}. Upserts by email. Returns the account id. */
    String credsSave(String json) {
        try {
            JSONObject in = new JSONObject(json);
            String email = in.getString("email").trim();
            String password = in.getString("password");
            String label = in.optString("label", "").trim();
            if (email.isEmpty() || password.isEmpty()) return "ERR:Email dan password wajib diisi";
            JSONArray arr = credsArray();
            String id = null;
            for (int i = 0; i < arr.length(); i++) if (arr.getJSONObject(i).getString("email").equalsIgnoreCase(email)) { id = arr.getJSONObject(i).getString("id"); break; }
            if (id == null) id = "acc_" + System.currentTimeMillis();
            JSONObject rec = new JSONObject();
            rec.put("id", id); rec.put("email", email); rec.put("label", label.isEmpty() ? email : label); rec.put("passwordEnc", encrypt(password));
            JSONArray out = new JSONArray();
            boolean replaced = false;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject a = arr.getJSONObject(i);
                if (a.getString("id").equals(id)) { out.put(rec); replaced = true; } else out.put(a);
            }
            if (!replaced) out.put(rec);
            saveCredsArray(out);
            return id;
        } catch (Exception e) { Log.w(TAG, "credsSave", e); return "ERR:" + e.getMessage(); }
    }
    void credsDelete(String id) {
        try {
            JSONArray arr = credsArray(), out = new JSONArray();
            for (int i = 0; i < arr.length(); i++) if (!arr.getJSONObject(i).getString("id").equals(id)) out.put(arr.getJSONObject(i));
            saveCredsArray(out);
        } catch (Exception e) { Log.w(TAG, "credsDelete", e); }
    }
    /** Decrypts the account, arms + fires the fill into the current page. Returns "OK" or "ERR:...". */
    String fillLoginById(String id) {
        try {
            JSONArray arr = credsArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject a = arr.getJSONObject(i);
                if (a.getString("id").equals(id)) {
                    String email = a.getString("email");
                    String password = decrypt(a.getString("passwordEnc"));
                    armAndFireFill(email, password);
                    return "OK";
                }
            }
            return "ERR:Akun tidak ditemukan";
        } catch (Exception e) { Log.w(TAG, "fillLoginById", e); return "ERR:" + e.getMessage(); }
    }

    // ---------------------------------------------------------------- debug log (in-app, no PC/adb needed)
    volatile boolean docStartSupported = false;
    private final java.util.List<String> debugLines = new ArrayList<>();
    synchronized void logConsole(String source, String level, String message, String sourceId, int line) {
        String ts = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date());
        String file = sourceId == null ? "" : sourceId.replaceAll("^.*/", "");
        debugLines.add("[" + ts + "][" + source + "][" + level + "] " + message + (file.isEmpty() ? "" : " (" + file + ":" + line + ")"));
        if (debugLines.size() > 800) debugLines.remove(0);
    }
    synchronized void logNative(String source, String message) {
        String ts = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date());
        debugLines.add("[" + ts + "][" + source + "][native] " + message);
        if (debugLines.size() > 800) debugLines.remove(0);
    }
    synchronized String debugLogText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Sesi Browser MAX MODE debug log\n");
        sb.append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append("), ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("docStartSupported=").append(docStartSupported).append('\n');
        sb.append("currentUrl=").append(currentUrl).append("\n\n");
        for (String l : debugLines) sb.append(l).append('\n');
        return sb.toString();
    }
    void shareDebugLog() {
        final String text = debugLogText();
        ui.post(new Runnable() { public void run() {
            try {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, "Sesi Browser MAX MODE - debug log");
                send.putExtra(Intent.EXTRA_TEXT, text.length() > 60000 ? text.substring(text.length() - 60000) : text);
                Intent chooser = Intent.createChooser(send, "Kirim log ke...");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                act.startActivity(chooser);
            } catch (Exception e) { Log.w(TAG, "shareDebugLog", e); }
        }});
    }
}
