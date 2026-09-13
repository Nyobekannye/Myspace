package id.kuli.sesibrowser;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

import androidx.webkit.WebViewCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ProfileStore {
    private static final String PREF = "profiles";
    private static final String K_LIST = "list", K_CUR = "current", K_ENGINE = "engine", K_DESKTOP = "desktop", K_DARKWEB = "darkweb";

    public static final String[][] ENGINES = {
        {"Google", "https://www.google.com", "https://www.google.com/search?q="},
        {"Bing", "https://www.bing.com", "https://www.bing.com/search?q="},
        {"DuckDuckGo", "https://duckduckgo.com", "https://duckduckgo.com/?q="},
        {"Brave", "https://search.brave.com", "https://search.brave.com/search?q="},
        {"Yahoo", "https://id.search.yahoo.com", "https://id.search.yahoo.com/search?p="}
    };

    private final Context ctx;
    private final SharedPreferences sp;

    public ProfileStore(Context c) {
        ctx = c.getApplicationContext();
        sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** Versi WebView/Chrome yang benar-benar terpasang, agar UA & Client Hints konsisten dengan engine. */
    public String realChromeVersion() {
        try {
            PackageInfo pi = WebViewCompat.getCurrentWebViewPackage(ctx);
            if (pi != null && pi.versionName != null && pi.versionName.matches("\\d+\\.\\d+\\.\\d+\\.\\d+"))
                return pi.versionName;
        } catch (Throwable ignored) {}
        return null;
    }

    private List<Profile> load() {
        List<Profile> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(sp.getString(K_LIST, "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(Profile.fromJson(arr.getJSONObject(i)));
        } catch (JSONException ignored) {}
        return out;
    }

    public List<Profile> all() {
        List<Profile> out = load();
        if (out.isEmpty()) {
            Profile p = create("Sesi 1");
            out.add(p);
            setCurrentId(p.id);
        }
        return out;
    }

    private void save(List<Profile> list) {
        JSONArray arr = new JSONArray();
        try { for (Profile p : list) arr.put(p.toJson()); } catch (JSONException ignored) {}
        sp.edit().putString(K_LIST, arr.toString()).commit();
    }

    public Profile create(String name) {
        Profile p = new Profile();
        p.id = IdentityFactory.hex(8);
        p.name = name;
        p.created = System.currentTimeMillis();
        IdentityFactory.randomizeAll(p, realChromeVersion());
        List<Profile> list = load();
        list.add(p);
        save(list);
        return p;
    }

    public void update(Profile np) {
        List<Profile> list = load();
        for (int i = 0; i < list.size(); i++) if (list.get(i).id.equals(np.id)) list.set(i, np);
        save(list);
    }

    public Profile get(String id) {
        for (Profile p : all()) if (p.id.equals(id)) return p;
        return null;
    }

    public void delete(String id) {
        List<Profile> list = load();
        Profile target = null;
        for (Profile p : list) if (p.id.equals(id)) target = p;
        if (target == null) return;
        list.remove(target);
        save(list);
        clearTabs(id);
        wipeDirs(target.dirSuffix());
    }

    private void wipeDirs(String suffix) {
        deleteRecursive(new File(ctx.getDataDir(), "app_webview_" + suffix));
        deleteRecursive(new File(ctx.getCacheDir(), "WebView_" + suffix));
        deleteRecursive(new File(ctx.getDataDir(), "app_textures_" + suffix));
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursive(k);
        f.delete();
    }

    public String currentId() { return sp.getString(K_CUR, null); }
    public void setCurrentId(String id) { sp.edit().putString(K_CUR, id).commit(); }

    public Profile current() {
        List<Profile> list = all();
        String cur = currentId();
        for (Profile p : list) if (p.id.equals(cur)) return p;
        setCurrentId(list.get(0).id);
        return list.get(0);
    }

    public int indexOf(String id) {
        List<Profile> list = all();
        for (int i = 0; i < list.size(); i++) if (list.get(i).id.equals(id)) return i;
        return 0;
    }

    // ------------------------------------------------------------ Tab per sesi (dipulihkan saat app dibuka lagi)
    /** Simpan daftar tab (url+judul) & indeks tab aktif untuk sesi tertentu. */
    public void saveTabs(String profileId, List<String[]> urlTitle, int current) {
        try {
            JSONArray arr = new JSONArray();
            for (String[] t : urlTitle) {
                JSONObject o = new JSONObject();
                o.put("u", t[0]); o.put("t", t[1] == null ? "" : t[1]);
                arr.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("cur", current); root.put("list", arr);
            sp.edit().putString("tabs_" + profileId, root.toString()).apply();
        } catch (JSONException ignored) {}
    }

    /** @return [ [url,title], ... ] atau kosong jika belum ada. */
    public List<String[]> loadTabs(String profileId) {
        List<String[]> out = new ArrayList<>();
        try {
            String raw = sp.getString("tabs_" + profileId, null);
            if (raw == null) return out;
            JSONArray arr = new JSONObject(raw).getJSONArray("list");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String u = o.optString("u", "");
                if (!u.isEmpty()) out.add(new String[]{u, o.optString("t", "")});
            }
        } catch (JSONException ignored) {}
        return out;
    }

    public int loadTabsCurrent(String profileId) {
        try {
            String raw = sp.getString("tabs_" + profileId, null);
            return raw == null ? 0 : new JSONObject(raw).optInt("cur", 0);
        } catch (JSONException e) { return 0; }
    }

    public void clearTabs(String profileId) { sp.edit().remove("tabs_" + profileId).apply(); }

    public int engine() { return sp.getInt(K_ENGINE, 0); }
    public void setEngine(int i) { sp.edit().putInt(K_ENGINE, i).apply(); }
    public boolean desktop() { return sp.getBoolean(K_DESKTOP, false); }
    public void setDesktop(boolean b) { sp.edit().putBoolean(K_DESKTOP, b).apply(); }
    /** Gelapkan halaman web (algorithmic darkening / force dark). Default aktif. */
    public boolean darkWeb() { return true; }   // mode gelap selalu aktif, tanpa toggle
    public void setDarkWeb(boolean b) { sp.edit().putBoolean(K_DARKWEB, b).apply(); }
}
