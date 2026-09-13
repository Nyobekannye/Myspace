package id.kuli.sesibrowser;

import org.json.JSONException;
import org.json.JSONObject;

/** Satu sesi: cookie store terpisah + identitas perangkat virtual lengkap. */
public class Profile {
    public String id, name;
    public String brand, model, androidVer;   // dikirim via Client Hints (Sec-CH-UA-Model / Platform-Version)
    public String chromeFull;                 // versi Chrome/WebView sebenarnya, mis. 131.0.6778.135
    public String deviceId;                   // IMEI 15 digit (Luhn valid)
    public String androidId;                  // 16 hex
    public String btMac, wifiMac, serial;
    public String gsfId;                      // Google Services Framework ID, 16 hex
    public String advertisingId;              // GAID, UUID
    public int cores, memGb;                  // navigator.hardwareConcurrency / deviceMemory
    public long seed;                         // noise canvas/audio deterministik per sesi
    public String region = "";                // kode wilayah (lihat Regions), "" = ikuti perangkat
    public boolean autoRegion = true;         // deteksi otomatis dari IP publik (VPN) → region ikut berubah
    public String rName = "", rTz = "", rLoc = "", rAl = ""; // wilayah kustom hasil deteksi (negara di luar daftar)
    public long created;

    public String dirSuffix() { return "p_" + id; }

    public String chromeMajor() {
        int i = chromeFull.indexOf('.');
        return i > 0 ? chromeFull.substring(0, i) : chromeFull;
    }

    /** UA "reduced" persis seperti Chrome Android modern (model & versi disembunyikan, ada di Client Hints). */
    public String userAgent() { return userAgent(false); }

    /** UA Chrome Android (mobile) atau Chrome Linux (mode desktop), versi Chrome tetap sama. */
    public String userAgent(boolean desktop) {
        if (desktop) return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/"
                + chromeMajor() + ".0.0.0 Safari/537.36";
        return "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/"
                + chromeMajor() + ".0.0.0 Mobile Safari/537.36";
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id); o.put("name", name);
        o.put("brand", brand); o.put("model", model); o.put("androidVer", androidVer);
        o.put("chromeFull", chromeFull);
        o.put("deviceId", deviceId); o.put("androidId", androidId);
        o.put("btMac", btMac); o.put("wifiMac", wifiMac); o.put("serial", serial);
        o.put("gsfId", gsfId); o.put("advertisingId", advertisingId);
        o.put("cores", cores); o.put("memGb", memGb); o.put("seed", seed); o.put("region", region);
        o.put("autoRegion", autoRegion); o.put("rName", rName); o.put("rTz", rTz); o.put("rLoc", rLoc); o.put("rAl", rAl);
        o.put("created", created);
        return o;
    }

    public static Profile fromJson(JSONObject o) {
        Profile p = new Profile();
        p.id = o.optString("id"); p.name = o.optString("name", "Sesi");
        p.brand = o.optString("brand", "Samsung"); p.model = o.optString("model", "SM-A546E");
        p.androidVer = o.optString("androidVer", "13");
        p.chromeFull = o.optString("chromeFull", "131.0.6778.135");
        p.deviceId = o.optString("deviceId"); p.androidId = o.optString("androidId");
        p.btMac = o.optString("btMac"); p.wifiMac = o.optString("wifiMac"); p.serial = o.optString("serial");
        p.gsfId = o.optString("gsfId"); p.advertisingId = o.optString("advertisingId");
        p.cores = o.optInt("cores", 8); p.memGb = o.optInt("memGb", 8);
        p.seed = o.optLong("seed", 1); p.created = o.optLong("created"); p.region = o.optString("region", "");
        p.autoRegion = o.optBoolean("autoRegion", true);
        p.rName = o.optString("rName", ""); p.rTz = o.optString("rTz", ""); p.rLoc = o.optString("rLoc", ""); p.rAl = o.optString("rAl", "");
        return p;
    }

    public Profile copy() {
        try { return fromJson(toJson()); } catch (JSONException e) { return this; }
    }
}
