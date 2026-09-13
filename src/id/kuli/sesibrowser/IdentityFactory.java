package id.kuli.sesibrowser;

import java.security.SecureRandom;
import java.util.UUID;

/** Generator identitas perangkat acak namun plausible. */
public class IdentityFactory {
    private static final SecureRandom R = new SecureRandom();

    // brand, model, min Android
    private static final String[][] DEVICES = {
        {"Samsung","SM-S918B","13"}, {"Samsung","SM-S911B","14"}, {"Samsung","SM-S928B","14"},
        {"Samsung","SM-A546E","13"}, {"Samsung","SM-A346E","14"}, {"Samsung","SM-A155F","14"},
        {"Samsung","SM-A356E","14"}, {"Samsung","SM-M346B","13"}, {"Samsung","SM-A055F","13"},
        {"Google","Pixel 7","14"}, {"Google","Pixel 8","14"}, {"Google","Pixel 7a","14"}, {"Google","Pixel 6a","13"},
        {"Xiaomi","2201117TY","13"}, {"Xiaomi","23049PCD8G","14"}, {"Xiaomi","22101316G","13"},
        {"Xiaomi","2312DRA50G","14"}, {"Xiaomi","23129RAA4G","14"}, {"Xiaomi","M2101K6G","12"},
        {"OPPO","CPH2451","14"}, {"OPPO","CPH2483","13"}, {"OPPO","CPH2591","14"}, {"OPPO","CPH2565","14"},
        {"vivo","V2312","14"}, {"vivo","V2301","13"}, {"vivo","V2247","13"},
        {"realme","RMX3771","14"}, {"realme","RMX3630","13"}, {"realme","RMX3890","14"},
        {"Infinix","Infinix X6837","13"}, {"Infinix","Infinix X6716","14"},
        {"TECNO","TECNO CK7n","13"}, {"TECNO","TECNO KJ5","14"},
        {"motorola","moto g84 5G","13"}, {"ASUS","ASUS_AI2302","14"}
    };
    // TAC (8 digit awal IMEI) dari beberapa vendor umum
    private static final String[] TACS = {
        "35326211","35439111","35847010","86723705","86160504","35201810","86445306","35696711",
        "35284511","86018106","35989910","86325805","35407011","35752610"
    };
    // OUI prefix vendor (3 byte pertama MAC)
    private static final String[] OUIS = {
        "8C:F5:A3","F0:6E:0B","A0:B4:A5","C8:3D:DC","64:CC:2E","5C:F7:C3","D4:6A:6A","40:B0:76",
        "E4:7D:BD","78:F8:82","24:18:1D","A4:50:46","F8:8B:37","58:CB:52","3C:BD:3E","70:FD:45"
    };
    private static final String[] CHROME_FALLBACK = {
        "128.0.6613.127","129.0.6668.81","130.0.6723.102","131.0.6778.135","132.0.6834.79","133.0.6943.49"
    };

    public static void randomizeAll(Profile p, String realChrome) {
        String[] d = DEVICES[R.nextInt(DEVICES.length)];
        p.brand = d[0]; p.model = d[1];
        int base = Integer.parseInt(d[2]);
        p.androidVer = String.valueOf(Math.min(15, base + R.nextInt(2)));
        p.chromeFull = realChrome != null ? realChrome : CHROME_FALLBACK[R.nextInt(CHROME_FALLBACK.length)];
        p.deviceId = imei();
        p.androidId = hex(16);
        p.btMac = mac();
        p.wifiMac = mac();
        p.serial = serial();
        p.gsfId = hex(16);
        p.advertisingId = UUID.randomUUID().toString();
        int[] cores = {4, 6, 8, 8, 8};
        p.cores = cores[R.nextInt(cores.length)];
        int[] mem = {4, 4, 6, 8, 8, 12};
        p.memGb = mem[R.nextInt(mem.length)];
        p.seed = Math.abs(R.nextLong() % 2147483647L) + 1;
    }

    public static String hex(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(Integer.toHexString(R.nextInt(16)));
        return sb.toString();
    }

    public static String imei() {
        StringBuilder sb = new StringBuilder(TACS[R.nextInt(TACS.length)]);
        for (int i = 0; i < 6; i++) sb.append(R.nextInt(10));
        // Luhn check digit
        int sum = 0;
        for (int i = 0; i < 14; i++) {
            int d = sb.charAt(i) - '0';
            if (i % 2 == 1) { d *= 2; if (d > 9) d -= 9; }
            sum += d;
        }
        sb.append((10 - (sum % 10)) % 10);
        return sb.toString();
    }

    public static String mac() {
        StringBuilder sb = new StringBuilder(OUIS[R.nextInt(OUIS.length)].toLowerCase());
        for (int i = 0; i < 3; i++) sb.append(String.format(":%02x", R.nextInt(256)));
        return sb.toString();
    }

    public static String serial() {
        String cs = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder("R");
        for (int i = 0; i < 10; i++) sb.append(cs.charAt(R.nextInt(cs.length())));
        return sb.toString();
    }
}
