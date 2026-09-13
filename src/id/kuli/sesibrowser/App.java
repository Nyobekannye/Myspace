package id.kuli.sesibrowser;

import android.app.Application;
import android.os.Build;
import android.webkit.WebView;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        String proc = Build.VERSION.SDK_INT >= 28 ? getProcessName() : getPackageName();
        if (proc != null && proc.endsWith(":restart")) return;
        Profile p = new ProfileStore(this).current();
        Regions.register(p);
        if (Build.VERSION.SDK_INT >= 28) {
            try { WebView.setDataDirectorySuffix(p.dirSuffix()); } catch (IllegalStateException ignored) {}
        }
        // Bahasa sesi: WebView membentuk Accept-Language & navigator.language(s) dari LocaleList default
        // proses ini — harus di-set SEBELUM WebView pertama dibuat.
        java.util.Locale[] locs = Regions.locales(p.region);
        if (locs != null && Build.VERSION.SDK_INT >= 24) {
            try { android.os.LocaleList.setDefault(new android.os.LocaleList(locs)); } catch (Throwable ignored) {}
        }
        String tz = Regions.tz(p.region);
        if (!tz.isEmpty()) { try { java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(tz)); } catch (Throwable ignored) {} }
    }
}
