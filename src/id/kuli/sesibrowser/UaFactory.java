package id.kuli.sesibrowser;

import java.security.SecureRandom;

/** Membuat identitas perangkat acak (UA + Android ID) untuk tiap sesi. */
public class UaFactory {
    private static final SecureRandom RND = new SecureRandom();

    // model, versi Android minimum yang wajar
    private static final String[][] DEVICES = {
        {"SM-S918B", "13"}, {"SM-S911B", "14"}, {"SM-A546E", "13"}, {"SM-A346E", "14"},
        {"SM-A155F", "14"}, {"SM-M346B", "13"}, {"Pixel 7", "14"}, {"Pixel 8", "14"},
        {"Pixel 6a", "13"}, {"2201117TY", "13"}, {"23049PCD8G", "14"}, {"22101316G", "13"},
        {"M2101K6G", "12"}, {"CPH2451", "14"}, {"CPH2483", "13"}, {"CPH2591", "14"},
        {"V2312", "14"}, {"V2301", "13"}, {"RMX3771", "14"}, {"RMX3630", "13"},
        {"Infinix X6837", "13"}, {"TECNO CK7n", "13"}, {"moto g84 5G", "13"}, {"ASUS_AI2302", "14"}
    };
    private static final String[] CHROME = {
        "124.0.6367.82", "125.0.6422.113", "126.0.6478.122", "127.0.6533.103",
        "128.0.6613.127", "129.0.6668.81", "130.0.6723.102", "131.0.6778.135",
        "132.0.6834.79", "133.0.6943.49", "134.0.6998.39", "135.0.7049.38"
    };

    public static String[] randomDevice() {
        String[] d = DEVICES[RND.nextInt(DEVICES.length)];
        int base = Integer.parseInt(d[1]);
        int ver = Math.min(15, base + RND.nextInt(2)); // versi Android base atau +1
        return new String[]{d[0], String.valueOf(ver)};
    }

    public static String buildUa(String model, String androidVer) {
        String chrome = CHROME[RND.nextInt(CHROME.length)];
        return "Mozilla/5.0 (Linux; Android " + androidVer + "; " + model
                + ") AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + chrome
                + " Mobile Safari/537.36";
    }

    public static String randomHex(int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(Integer.toHexString(RND.nextInt(16)));
        return sb.toString();
    }
}
