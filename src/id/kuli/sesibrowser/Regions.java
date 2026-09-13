package id.kuli.sesibrowser;

/** Daftar wilayah: zona waktu + bahasa (Accept-Language / navigator.language) yang harus cocok dengan negara VPN. */
public final class Regions {
    /** {kode, nama tampil, timezone IANA, locale utama, Accept-Language lengkap} */
    public static final String[][] LIST = {
        {"",   "Ikuti perangkat",   "",                    "",      ""},
        {"ID", "Indonesia",         "Asia/Jakarta",        "id-ID", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7"},
        {"SG", "Singapura",         "Asia/Singapore",      "en-SG", "en-SG,en;q=0.9"},
        {"MY", "Malaysia",          "Asia/Kuala_Lumpur",   "en-MY", "en-MY,en;q=0.9,ms;q=0.8"},
        {"TH", "Thailand",          "Asia/Bangkok",        "th-TH", "th-TH,th;q=0.9,en;q=0.8"},
        {"VN", "Vietnam",           "Asia/Ho_Chi_Minh",    "vi-VN", "vi-VN,vi;q=0.9,en;q=0.8"},
        {"PH", "Filipina",          "Asia/Manila",         "en-PH", "en-PH,en;q=0.9,fil;q=0.8"},
        {"JP", "Jepang",            "Asia/Tokyo",          "ja-JP", "ja-JP,ja;q=0.9,en;q=0.8"},
        {"KR", "Korea Selatan",     "Asia/Seoul",          "ko-KR", "ko-KR,ko;q=0.9,en;q=0.8"},
        {"HK", "Hong Kong",         "Asia/Hong_Kong",      "zh-HK", "zh-HK,zh;q=0.9,en;q=0.8"},
        {"TW", "Taiwan",            "Asia/Taipei",         "zh-TW", "zh-TW,zh;q=0.9,en;q=0.8"},
        {"IN", "India",             "Asia/Kolkata",        "en-IN", "en-IN,en;q=0.9,hi;q=0.8"},
        {"AE", "Uni Emirat Arab",   "Asia/Dubai",          "en-AE", "en-AE,en;q=0.9,ar;q=0.8"},
        {"TR", "Turki",             "Europe/Istanbul",     "tr-TR", "tr-TR,tr;q=0.9,en;q=0.8"},
        {"AU", "Australia",         "Australia/Sydney",    "en-AU", "en-AU,en;q=0.9"},
        {"GB", "Inggris",           "Europe/London",       "en-GB", "en-GB,en;q=0.9"},
        {"DE", "Jerman",            "Europe/Berlin",       "de-DE", "de-DE,de;q=0.9,en;q=0.8"},
        {"FR", "Prancis",           "Europe/Paris",        "fr-FR", "fr-FR,fr;q=0.9,en;q=0.8"},
        {"NL", "Belanda",           "Europe/Amsterdam",    "nl-NL", "nl-NL,nl;q=0.9,en;q=0.8"},
        {"ES", "Spanyol",           "Europe/Madrid",       "es-ES", "es-ES,es;q=0.9,en;q=0.8"},
        {"IT", "Italia",            "Europe/Rome",         "it-IT", "it-IT,it;q=0.9,en;q=0.8"},
        {"PL", "Polandia",          "Europe/Warsaw",       "pl-PL", "pl-PL,pl;q=0.9,en;q=0.8"},
        {"SE", "Swedia",            "Europe/Stockholm",    "sv-SE", "sv-SE,sv;q=0.9,en;q=0.8"},
        {"CH", "Swiss",             "Europe/Zurich",       "de-CH", "de-CH,de;q=0.9,en;q=0.8"},
        {"RU", "Rusia",             "Europe/Moscow",       "ru-RU", "ru-RU,ru;q=0.9,en;q=0.8"},
        {"US_E","AS (Timur/New York)","America/New_York",  "en-US", "en-US,en;q=0.9"},
        {"US_C","AS (Tengah/Chicago)","America/Chicago",   "en-US", "en-US,en;q=0.9"},
        {"US_W","AS (Barat/Los Angeles)","America/Los_Angeles","en-US","en-US,en;q=0.9"},
        {"CA", "Kanada (Toronto)",  "America/Toronto",     "en-CA", "en-CA,en;q=0.9,fr;q=0.8"},
        {"BR", "Brasil",            "America/Sao_Paulo",   "pt-BR", "pt-BR,pt;q=0.9,en;q=0.8"},
        {"MX", "Meksiko",           "America/Mexico_City", "es-MX", "es-MX,es;q=0.9,en;q=0.8"},
        {"AR", "Argentina",         "America/Argentina/Buenos_Aires","es-AR","es-AR,es;q=0.9,en;q=0.8"},
        {"ZA", "Afrika Selatan",    "Africa/Johannesburg", "en-ZA", "en-ZA,en;q=0.9"},
        {"NG", "Nigeria",           "Africa/Lagos",        "en-NG", "en-NG,en;q=0.9"},
        {"EG", "Mesir",             "Africa/Cairo",        "ar-EG", "ar-EG,ar;q=0.9,en;q=0.8"},
    };

    /** Wilayah kustom (negara di luar LIST) yang didaftarkan dari profil aktif. */
    private static final java.util.Map<String, String[]> CUSTOM = new java.util.HashMap<>();
    public static void register(Profile p) {
        if (p != null && p.rTz != null && !p.rTz.isEmpty() && p.region != null && !p.region.isEmpty())
            CUSTOM.put(p.region, new String[]{p.region, p.rName, p.rTz, p.rLoc, p.rAl});
    }
    public static boolean isListed(String code) { for (String[] r : LIST) if (r[0].equals(code)) return true; return false; }

    public static String[] get(String code) {
        if (code == null) return LIST[0];
        for (String[] r : LIST) if (r[0].equals(code)) return r;
        String[] c = CUSTOM.get(code);
        return c != null ? c : LIST[0];
    }

    /** Bahasa utama per negara (untuk negara di luar LIST). */
    private static final String[][] LANG = {
        {"SA","ar"},{"QA","ar"},{"KW","ar"},{"BH","ar"},{"OM","ar"},{"JO","ar"},{"IQ","ar"},{"MA","ar"},{"DZ","ar"},{"TN","ar"},
        {"CN","zh"},{"MO","zh"},{"KH","km"},{"LA","lo"},{"MM","my"},{"BD","bn"},{"PK","ur"},{"LK","si"},{"NP","ne"},{"IR","fa"},
        {"IL","he"},{"UA","uk"},{"BY","ru"},{"KZ","ru"},{"CZ","cs"},{"SK","sk"},{"HU","hu"},{"RO","ro"},{"BG","bg"},{"GR","el"},
        {"PT","pt"},{"AT","de"},{"BE","nl"},{"DK","da"},{"NO","nb"},{"FI","fi"},{"IE","en"},{"NZ","en"},{"CL","es"},{"CO","es"},
        {"PE","es"},{"VE","es"},{"EC","es"},{"UY","es"},{"KE","en"},{"GH","en"},{"ET","am"},{"HR","hr"},{"RS","sr"},{"LT","lt"},{"LV","lv"},{"EE","et"},
    };

    /** Terjemahkan hasil geo-IP (kode negara ISO-2 + zona waktu IANA) ke entri wilayah. Selalu non-null. */
    public static String[] fromGeo(String cc, String tz) {
        if (cc == null || cc.length() != 2) return LIST[0];
        cc = cc.toUpperCase(java.util.Locale.ROOT);
        if (cc.equals("US")) {
            if (tz != null && (tz.contains("Los_Angeles") || tz.contains("Phoenix") || tz.contains("Denver") || tz.contains("Boise"))) return get("US_W");
            if (tz != null && (tz.contains("Chicago") || tz.contains("Denver"))) return get("US_C");
            return get("US_E");
        }
        for (String[] r : LIST) if (r[0].equals(cc)) return r;
        if (tz == null || tz.isEmpty()) return LIST[0];
        String lang = "en";
        for (String[] l : LANG) if (l[0].equals(cc)) { lang = l[1]; break; }
        String loc = lang + "-" + cc;
        String al = lang.equals("en") ? loc + ",en;q=0.9" : loc + "," + lang + ";q=0.9,en;q=0.8";
        String name = new java.util.Locale("", cc).getDisplayCountry(new java.util.Locale("id"));
        if (name == null || name.isEmpty()) name = cc;
        String[] e = {cc, name, tz, loc, al};
        CUSTOM.put(cc, e);
        return e;
    }
    public static int indexOf(String code) {
        for (int i = 0; i < LIST.length; i++) if (LIST[i][0].equals(code)) return i;
        return 0;
    }
    public static String label(String code) { return get(code)[1]; }
    public static String tz(String code) { return get(code)[2]; }
    public static String locale(String code) { return get(code)[3]; }
    public static String acceptLanguage(String code) { return get(code)[4]; }

    /** Daftar java.util.Locale dari string Accept-Language, untuk LocaleList.setDefault. */
    public static java.util.Locale[] locales(String code) {
        String al = acceptLanguage(code);
        if (al.isEmpty()) return null;
        java.util.List<java.util.Locale> out = new java.util.ArrayList<>();
        for (String part : al.split(",")) {
            String tag = part.split(";")[0].trim();
            if (!tag.isEmpty()) out.add(java.util.Locale.forLanguageTag(tag));
        }
        return out.toArray(new java.util.Locale[0]);
    }
    private Regions() {}
}
