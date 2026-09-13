package id.kuli.sesibrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Process;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.app.Dialog;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Switch;
import android.widget.Toast;

import androidx.webkit.UserAgentMetadata;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_DEVICE = 11, REQ_FILE = 12, REQ_PERM_DL = 13;
    private ValueCallback<Uri[]> fileCallback;
    private Uri captureUri;
    private Runnable pendingDownload;                        // ditunda sampai izin storage (Android 9) diberikan
    private RegionDetector regionDetector;
    private Downloads downloads;                             // manajer unduhan (daftar, progres, buka/bagikan/hapus)
    private android.content.BroadcastReceiver dlReceiver;
    /** Nama objek JS jembatan blob: (acak per proses agar tidak jadi penanda fingerprint; dl.js memindahkannya ke Symbol). */
    private static final String DL_BRIDGE = "_" + Long.toHexString(Double.doubleToLongBits(Math.random())).substring(0, 8);

    private WebView web;                         // alias: WebView tab aktif
    private final List<Tab> tabs = new ArrayList<>();
    private Tab cur;
    private TextView tabCount;
    private Dialog tabsDialog;
    private static final int MAX_TABS = 24;
    private EditText urlBar;
    private ProgressBar progress;
    private ImageView urlIcon, btnClear, btnBack, btnForward, btnReload;
    private View header, topChrome, btnShrink, navWrap, switchOverlay, webCard, content, quickUrl, nav;
    private EditText quickUrlBar;
    private View navDock;
    private AvatarView dockAvatar;
    private boolean navDocked = false;
    private String spoofScriptCache;
    private android.graphics.drawable.GradientDrawable navBg;
    private android.graphics.drawable.GradientDrawable webBg;
    private boolean chromeAnimating = false;
    private LinearLayout sessionRow;
    private AvatarView navAvatar;
    private ProfileStore store;
    private Profile profile;
    private boolean nativeUAD = false;
    private boolean headerCollapsed = false, loading = false, urlFocused = false;
    private int headerHeight = 0, lastScrollY = 0;
    private String currentUrl = "";

    // ---- Introvert Dreams MAX MODE (Dola/Seaart companion) ----
    private ExtRuntime rt;                       // emulasi runtime ekstensi Chrome (storage, messaging, downloads)
    private WebView bgWeb;                       // WebView tersembunyi: background.js (service worker ekstensi)
    private WebView popupWeb;                    // UI popup ekstensi (popup.html)
    private Dialog popupDialog;
    private String maxModeScriptCache;           // bundle content-script MAX MODE (disuntik di document_start)
    private ImageView maxFab;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestKeepAlivePerms();
        regionDetector = new RegionDetector(this, this::onRegionDetected);
        regionDetector.start();
        regionDetector.schedule(800);
        setContentView(R.layout.activity_main);

        store = new ProfileStore(this);
        profile = store.current();
        downloads = Downloads.get(this);
        registerDownloadReceiver();
        setupMaxMode();
        cleanCaptures();
        // Pastikan versi Chrome di identitas = versi WebView terpasang (Sec-CH-UA harus konsisten)
        String real = store.realChromeVersion();
        if (real != null && !real.equals(profile.chromeFull)) { profile.chromeFull = real; store.update(profile); }

        urlBar = findViewById(R.id.urlBar);
        tabCount = findViewById(R.id.tabCount);
        urlIcon = findViewById(R.id.urlIcon);
        btnClear = findViewById(R.id.btnClear);
        progress = findViewById(R.id.progress);
        header = findViewById(R.id.header);
        topChrome = findViewById(R.id.topChrome);
        btnShrink = findViewById(R.id.btnShrink);
        webCard = findViewById(R.id.webCard);
        content = findViewById(R.id.content);
        quickUrl = findViewById(R.id.quickUrl);
        quickUrlBar = findViewById(R.id.quickUrlBar);
        nav = findViewById(R.id.nav);
        navDock = findViewById(R.id.navDock);
        dockAvatar = findViewById(R.id.dockAvatar);
        setupNavDock();
        navBg = new android.graphics.drawable.GradientDrawable();
        navBg.setColor(0xFF0F0F12);
        navBg.setCornerRadius(Ui.dp(this, 23));
        nav.setBackground(navBg);
        webBg = new android.graphics.drawable.GradientDrawable();
        webBg.setColor(webColor());
        webBg.setCornerRadius(Ui.dp(this, 28));
        webCard.setBackground(webBg);
        Ui.gradientText(findViewById(R.id.appTitle));
        navWrap = findViewById(R.id.navWrap);
        switchOverlay = findViewById(R.id.switchOverlay);
        sessionRow = findViewById(R.id.sessionRow);
        navAvatar = findViewById(R.id.navAvatar);
        btnBack = findViewById(R.id.btnBack);
        btnForward = findViewById(R.id.btnForward);
        btnReload = findViewById(R.id.btnReload);
        View btnSession = findViewById(R.id.btnSession);
        View btnSettings = findViewById(R.id.btnSettings);

        renderSessions();

        View btnTabs = findViewById(R.id.btnTabs);
        for (View v : new View[]{btnBack, btnForward, btnReload, btnSession, btnSettings, btnTabs}) Ui.pressable(v);
        btnTabs.setOnClickListener(v -> { Ui.haptic(v); showTabSwitcher(); });
        btnTabs.setOnLongClickListener(v -> { Ui.haptic(v); newTab(homeUrl(), true, null); Toast.makeText(this, "Tab baru", Toast.LENGTH_SHORT).show(); return true; });
        setupUrlPillSwipe();

        btnBack.setOnClickListener(v -> { if (web.canGoBack()) { Ui.haptic(v); web.goBack(); } });
        btnForward.setOnClickListener(v -> { if (web.canGoForward()) { Ui.haptic(v); web.goForward(); } });
        btnReload.setOnClickListener(v -> { Ui.haptic(v); if (loading) web.stopLoading(); else web.reload(); });
        btnReload.setOnLongClickListener(v -> { Ui.haptic(v); showDownloads(); return true; });
        btnSession.setOnClickListener(v -> { Ui.haptic(v); showSessionSheet(); });
        btnSession.setOnLongClickListener(v -> { openDevice(profile.id); return true; });
        btnSettings.setOnClickListener(v -> { Ui.haptic(v); showSettingsSheet(); });
        btnClear.setOnClickListener(v -> { urlBar.setText(""); });

        // Tombol alamat di nav: mode penuh → address bar cepat di atas nav; mode normal → fokus URL bar atas
        View btnUrl = findViewById(R.id.btnUrl);
        Ui.pressable(btnUrl);
        btnUrl.setOnClickListener(v -> {
            Ui.haptic(v);
            if (headerCollapsed) showQuickUrl(true);
            else { urlBar.requestFocus(); Ui.showKeyboard(urlBar); }
        });
        // Tahan tombol alamat = ke beranda (pengganti tombol home)
        btnUrl.setOnLongClickListener(v -> {
            Ui.haptic(v);
            web.loadUrl(homeUrl());
            Toast.makeText(this, "Beranda", Toast.LENGTH_SHORT).show();
            return true;
        });
        // Tombol layar penuh manual di URL pill (untuk situs yang layout-nya bermasalah di mode kartu)
        final View btnExpand = findViewById(R.id.btnExpand);
        Ui.pressable(btnExpand);
        btnExpand.setOnClickListener(v -> { Ui.haptic(v); collapseHeader(true); });
        findViewById(R.id.quickClose).setOnClickListener(v -> showQuickUrl(false));
        quickUrlBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN)) {
                String q = quickUrlBar.getText().toString();
                showQuickUrl(false);
                go(q);
                return true;
            }
            return false;
        });

        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_DOWN)) {
                go(urlBar.getText().toString());
                return true;
            }
            return false;
        });
        urlBar.setOnFocusChangeListener((v, has) -> {
            urlFocused = has;
            ((SwipeRow) findViewById(R.id.urlPill)).setSwipeEnabled(!has);
            btnClear.setVisibility(has ? View.VISIBLE : View.GONE);
            findViewById(R.id.btnExpand).setVisibility(has ? View.GONE : View.VISIBLE);
            urlIcon.setImageResource(has ? R.drawable.ic_search : iconFor(currentUrl));
            if (has) { urlBar.setText(currentUrl); urlBar.selectAll(); collapseHeader(false); }
            else showUrl(currentUrl);
            // sembunyikan nav saat keyboard terbuka supaya tidak ikut naik
            if (has) hideNavUi(150); else showNavUi(200);
        });

        // Mode penuh: header + URL menyusut saat halaman di-scroll, lalu TERKUNCI.
        // Scroll balik tidak memunculkan UI lagi — hanya tombol di sisi kanan (btnShrink) yang membukanya.
        topChrome.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (!headerCollapsed && !chromeAnimating && b - t > 0) headerHeight = b - t;
        });
        Ui.pressable(btnShrink);
        btnShrink.setOnClickListener(v -> { Ui.haptic(v); collapseHeader(false); });
        updateNavState();

        restoreTabs();
        Intent it = getIntent();
        if (it != null && Intent.ACTION_VIEW.equals(it.getAction()) && it.getData() != null) {
            // link dari app lain → selalu buka di tab baru
            newTab(it.getData().toString(), true, null);
        }
        if (tabs.isEmpty()) newTab(homeUrl(), true, null);
        bgWeb.loadUrl("file:///android_asset/background.html");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) newTab(intent.getData().toString(), true, null);
    }

    private String homeUrl() { return ProfileStore.ENGINES[store.engine()][1]; }

    // ------------------------------------------------------------------ Tab
    /** Buat tab baru. url=null → tab kosong yang dimuat malas (restore). */
    private Tab newTab(String url, boolean select, Tab parent) {
        if (tabs.size() >= MAX_TABS) { Toast.makeText(this, "Maksimal " + MAX_TABS + " tab", Toast.LENGTH_SHORT).show(); return cur; }
        Tab t = new Tab();
        t.parent = parent;
        t.web = new WebView(this);
        t.web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        t.web.setVerticalScrollBarEnabled(false);      // tanpa bar gulir tepi (jadul); scroll tetap normal
        t.web.setHorizontalScrollBarEnabled(false);
        t.web.setScrollbarFadingEnabled(true);
        t.web.setVisibility(View.GONE);
        ((FrameLayout) webCard).addView(t.web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setupWebView(t);
        int at = cur == null ? tabs.size() : tabs.indexOf(cur) + 1;   // tab baru muncul di sebelah tab aktif
        tabs.add(Math.min(at, tabs.size()), t);
        if (url != null) { t.pendingUrl = url; t.url = url; }
        if (select) selectTab(t); else { t.web.onPause(); updateTabCount(); }
        saveTabs();
        return t;
    }

    private void selectTab(Tab t) { selectTab(t, 0); }

    /**
     * Pindah tab. dir = 0 → crossfade + zoom halus; dir = ±1 → tab lama geser keluar, tab baru masuk dari arah dir.
     * WebView dinaikkan ke hardware layer selama animasi agar 60 fps tanpa re-raster.
     */
    private void selectTab(final Tab t, int dir) {
        if (t == null || t.web == null) return;
        if (cur == t) return;
        final Tab old = cur;
        final WebView oldWeb = old != null ? old.web : null;
        if (old != null && oldWeb != null) old.snapshot(getWindow(), null);   // async (PixelCopy), tidak memblokir UI thread
        cur = t;
        web = t.web;
        web.animate().cancel();
        web.setTranslationX(0f); web.setScaleX(1f); web.setScaleY(1f);
        web.setVisibility(View.VISIBLE);
        web.onResume();                              // tab aktif: timer JS/media/animasi jalan lagi
        web.bringToFront();
        progress.bringToFront(); btnShrink.bringToFront();
        if (!t.loaded && t.pendingUrl != null) { t.loaded = true; String u = t.pendingUrl; t.pendingUrl = null; web.loadUrl(u); }
        else if (!t.loaded) { t.loaded = true; }

        if (oldWeb != null) {
            final int w = Math.max(1, webCard.getWidth());
            oldWeb.animate().cancel();
            oldWeb.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            if (dir == 0) {
                web.setAlpha(0f); web.setScaleX(1.04f); web.setScaleY(1.04f);
                oldWeb.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setDuration(180).setInterpolator(Ui.SNAP)
                    .withEndAction(() -> resetWeb(oldWeb, old)).start();
                web.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(Ui.SNAP)
                    .withEndAction(() -> web.setLayerType(View.LAYER_TYPE_NONE, null)).start();
            } else {
                web.setAlpha(1f);
                web.setTranslationX(dir * w);
                oldWeb.animate().translationX(-dir * w).setDuration(260).setInterpolator(Ui.SNAP)
                    .withEndAction(() -> resetWeb(oldWeb, old)).start();
                web.animate().translationX(0f).setDuration(260).setInterpolator(Ui.SNAP)
                    .withEndAction(() -> web.setLayerType(View.LAYER_TYPE_NONE, null)).start();
            }
        } else { web.setAlpha(1f); }

        currentUrl = t.url;
        loading = t.loading;
        rt.page = web; rt.currentUrl = t.url == null ? "" : t.url; rt.currentTitle = t.title; rt.notifyTabUpdated(); updateMaxFab();
        if (!urlFocused) showUrl(currentUrl);
        btnReload.setImageResource(loading ? R.drawable.ic_close : R.drawable.ic_reload);
        if (loading) { progress.setProgress(t.progress); progress.setAlpha(1f); } else progress.setAlpha(0f);
        lastScrollY = web.getScrollY();
        updateNavState();
        updateTabCount();
        if (!urlFocused) web.requestFocus();
    }

    /** Kembalikan WebView lama ke keadaan netral & sembunyikan (kalau masih ada di daftar tab). */
    private void resetWeb(WebView wv, Tab t) {
        if (wv == null) return;
        wv.setLayerType(View.LAYER_TYPE_NONE, null);
        wv.setTranslationX(0f); wv.setAlpha(1f); wv.setScaleX(1f); wv.setScaleY(1f);
        if (t != cur) { wv.setVisibility(View.GONE); wv.onPause(); }   // tab tersembunyi: hentikan timer JS/media (bukan pauseTimers() yang global)
    }

    private void closeTab(Tab t) {
        if (t == null) return;
        int idx = tabs.indexOf(t);
        if (idx < 0) return;
        tabs.remove(idx);
        for (Tab o : tabs) if (o.parent == t) o.parent = t.parent;
        boolean wasCur = (t == cur);
        if (wasCur) {
            // Tentukan & tampilkan pengganti sebelum WebView lama dilepas, supaya fokus tidak lompat ke URL bar
            if (tabs.isEmpty()) newTab(homeUrl(), true, null);
            else selectTab((t.parent != null && tabs.contains(t.parent)) ? t.parent : tabs.get(Math.min(idx, tabs.size() - 1)));
        }
        detach(t);
        updateTabCount();
        saveTabs();
    }

    /** Lepas WebView dari kartu & hancurkan (setelah animasi keluar kalau sedang berjalan). */
    private void detach(final Tab t) {
        final WebView wv = t.web;
        if (wv == null) return;
        t.web = null;
        wv.animate().cancel();
        wv.setVisibility(View.GONE);
        ((FrameLayout) webCard).removeView(wv);
        try { wv.stopLoading(); wv.destroy(); } catch (Throwable ignored) {}
        t.destroy();
    }

    private void closeAllTabs() {
        List<Tab> old = new ArrayList<>(tabs);
        tabs.clear(); cur = null;
        Tab fresh = newTab(homeUrl(), true, null);        // tab baru terpasang dulu → nav & fokus tetap stabil
        for (Tab t : old) if (t != fresh) detach(t);
        web = fresh.web;
        showNavUi(0);
    }

    private void switchTabBy(int delta) {
        if (tabs.size() < 2 || cur == null) return;
        int i = tabs.indexOf(cur) + delta;
        if (i < 0 || i >= tabs.size()) { nudgePill(delta); return; }
        Ui.haptic(urlBar);
        selectTab(tabs.get(i), delta);
        // pill masuk dari arah tab baru
        View pill = findViewById(R.id.urlPill);
        pill.setTranslationX(delta * Ui.dp(this, 48)); pill.setAlpha(0.3f);
        pill.animate().translationX(0f).alpha(1f).setDuration(240).setInterpolator(Ui.SNAP).start();
        showTabHint((i + 1) + " / " + tabs.size());
    }

    private void nudgePill(int dir) {
        View pill = findViewById(R.id.urlPill);
        pill.animate().translationX(dir * Ui.dp(this, 10)).setDuration(80)
            .withEndAction(() -> pill.animate().translationX(0f).setDuration(160).setInterpolator(Ui.EASE).start()).start();
    }

    /** Geser URL pill kiri/kanan → pindah ke tab sebelah (seperti Chrome). */
    private void setupUrlPillSwipe() {
        final SwipeRow pill = findViewById(R.id.urlPill);
        final float threshold = Ui.dp(this, 64);
        pill.setListener(new SwipeRow.Listener() {
            boolean layered = false;
            @Override public void onSwipeMove(float dx) {
                pill.setTranslationX(dx * 0.45f);
                pill.setAlpha(Math.max(0.5f, 1f - Math.abs(dx) / (threshold * 4f)));
                if (web == null || tabs.size() < 2) return;
                int i = tabs.indexOf(cur);
                boolean canGo = dx < 0 ? i < tabs.size() - 1 : i > 0;
                if (!layered) { web.setLayerType(View.LAYER_TYPE_HARDWARE, null); layered = true; }
                // konten ikut jari (parallax); kalau sudah di ujung, hanya bergeser sedikit (efek "karet")
                web.setTranslationX(canGo ? dx * 0.35f : dx * 0.08f);
            }
            @Override public void onSwipeEnd(float dx, float vx) {
                pill.animate().translationX(0f).alpha(1f).setDuration(220).setInterpolator(Ui.SNAP).start();
                layered = false;
                boolean go = Math.abs(dx) > threshold || Math.abs(vx) > 1100f;
                if (go && tabs.size() > 1) {
                    int i = tabs.indexOf(cur) + (dx < 0 ? 1 : -1);
                    if (i >= 0 && i < tabs.size()) { switchTabBy(dx < 0 ? 1 : -1); return; }
                }
                if (web != null) web.animate().translationX(0f).setDuration(220).setInterpolator(Ui.SNAP)
                    .withEndAction(() -> { if (web != null) web.setLayerType(View.LAYER_TYPE_NONE, null); }).start();
                if (go) nudgePill(dx < 0 ? 1 : -1);
            }
        });
    }

    private void updateTabCount() {
        if (tabCount == null) return;
        int n = tabs.size();
        String txt = n > 99 ? ":D" : String.valueOf(n);
        if (txt.contentEquals(tabCount.getText())) return;      // tidak berubah → tidak perlu animasi
        tabCount.setText(txt);
        tabCount.setTextSize(n > 9 ? 10 : 11);
        tabCount.animate().cancel();
        tabCount.setScaleX(0.7f); tabCount.setScaleY(0.7f);
        tabCount.animate().scaleX(1f).scaleY(1f).setDuration(320).setInterpolator(Ui.POP).start();
    }

    private TextView tabHint;
    private void showTabHint(String txt) { showTabHint(txt, null); }

    /** Label kecil di atas nav ("2 / 5", "⬇ berkas · Lihat"). onTap != null → bisa diketuk & tampil lebih lama. */
    private void showTabHint(String txt, final Runnable onTap) {
        if (tabHint == null) {
            tabHint = new TextView(this);
            tabHint.setTextColor(0xFFF4F4F0); tabHint.setTextSize(12); tabHint.setTypeface(null, android.graphics.Typeface.BOLD);
            tabHint.setPadding(Ui.dp(this, 14), Ui.dp(this, 7), Ui.dp(this, 14), Ui.dp(this, 7));
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xFF0F0F12); bg.setCornerRadius(Ui.dp(this, 16));
            tabHint.setBackground(bg); tabHint.setElevation(Ui.dp(this, 6));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
            lp.bottomMargin = Ui.dp(this, 72);
            ((FrameLayout) findViewById(R.id.root)).addView(tabHint, lp);
            tabHint.setAlpha(0f);
        }
        tabHint.setText(txt);
        tabHint.setOnClickListener(onTap == null ? null : x -> { Ui.haptic(x); tabHint.animate().cancel(); tabHint.setAlpha(0f); onTap.run(); });
        tabHint.setClickable(onTap != null);
        tabHint.animate().cancel();
        tabHint.setTranslationY(Ui.dp(this, 8));
        final long hold = onTap == null ? 600 : 2800;
        tabHint.animate().alpha(1f).translationY(0f).setDuration(160).setInterpolator(Ui.SNAP)
            .withEndAction(() -> tabHint.animate().alpha(0f).setStartDelay(hold).setDuration(220).withEndAction(null).start()).start();
    }

    private void saveTabs() {
        if (profile == null) return;
        List<String[]> l = new ArrayList<>();
        for (Tab t : tabs) {
            String u = t.url != null && !t.url.isEmpty() ? t.url : t.pendingUrl;
            if (u == null || u.isEmpty() || u.startsWith("about:")) continue;
            l.add(new String[]{u, t.title});
        }
        store.saveTabs(profile.id, l, cur == null ? 0 : Math.max(0, tabs.indexOf(cur)));
    }

    private void restoreTabs() {
        List<String[]> l = store.loadTabs(profile.id);
        if (l.isEmpty()) return;
        int curIdx = Math.max(0, Math.min(l.size() - 1, store.loadTabsCurrent(profile.id)));
        for (int i = 0; i < l.size(); i++) {
            Tab t = newTab(l.get(i)[0], false, null);
            if (t == null || tabs.size() >= MAX_TABS) break;
            t.title = l.get(i)[1];
        }
        if (curIdx < tabs.size()) selectTab(tabs.get(curIdx));
    }

    // ------------------------------------------------------------------ Tab switcher (grid)
    private boolean switcherOpening = false;

    /** Ambil thumbnail tab aktif dulu (async, dari GPU), baru buka grid — supaya kartu tab aktif langsung segar. */
    private void showTabSwitcher() {
        if (tabsDialog != null || switcherOpening) return;
        if (cur == null || cur.web == null) { showTabSwitcherNow(); return; }
        switcherOpening = true;
        final boolean[] done = {false};
        final Runnable open = () -> { if (done[0] || isFinishing()) return; done[0] = true; switcherOpening = false; showTabSwitcherNow(); };
        cur.snapshot(getWindow(), open);
        webCard.postDelayed(open, 80);   // pengaman: buka paling lambat 80 ms walau copy belum selesai
    }

    private void showTabSwitcherNow() {
        if (tabsDialog != null) return;
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_tabs, null);
        final Dialog d = new Dialog(this, R.style.TabsTheme);
        d.setContentView(v);
        if (d.getWindow() != null) d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        tabsDialog = d;
        final LinearLayout grid = v.findViewById(R.id.tabsGrid);
        final TextView title = v.findViewById(R.id.tabsTitle);
        final java.util.Map<Tab, View> cards = new java.util.HashMap<>();
        final boolean[] first = {true};

        final Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            title.setText(tabs.size() + " tab · " + profile.name);
            // 1. posisi lama kartu yang masih ada (untuk animasi geser)
            final java.util.Map<Tab, int[]> oldPos = new java.util.HashMap<>();
            for (java.util.Map.Entry<Tab, View> e : cards.entrySet()) {
                View c = e.getValue();
                if (c.getParent() != null) oldPos.put(e.getKey(), new int[]{((View) c.getParent()).getLeft() + c.getLeft(), ((View) c.getParent()).getTop() + c.getTop()});
            }
            grid.removeAllViews();
            LinearLayout row = null;
            for (int i = 0; i < tabs.size(); i++) {
                if (i % 2 == 0) {
                    row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setClipChildren(false); row.setClipToPadding(false);
                    grid.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                }
                Tab t = tabs.get(i);
                View c = cards.get(t);
                if (c == null) { c = tabCard(t, d, rebuild[0]); cards.put(t, c); }
                else { c.setSelected(t == cur); c.setTranslationX(0f); c.setAlpha(1f); c.setScaleX(1f); c.setScaleY(1f); }
                if (c.getParent() != null) ((ViewGroup) c.getParent()).removeView(c);
                row.addView(c);
            }
            if (tabs.size() % 2 == 1 && row != null) {   // pengisi agar kartu terakhir tidak melebar
                View filler = new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 1); lp.weight = 1;
                lp.setMargins(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
                row.addView(filler, lp);
            }
            for (Tab t : new ArrayList<>(cards.keySet())) if (!tabs.contains(t)) cards.remove(t);
            grid.setClipChildren(false);
            // 2. setelah layout: kartu bergeser dari posisi lama ke baru (move), kartu baru muncul
            final boolean stagger = first[0]; first[0] = false;
            grid.post(() -> {
                int idx = 0, curIdx = Math.max(0, tabs.indexOf(cur));
                for (Tab t : tabs) {
                    View c = cards.get(t);
                    if (c == null || c.getParent() == null) continue;
                    View r = (View) c.getParent();
                    int nx = r.getLeft() + c.getLeft(), ny = r.getTop() + c.getTop();
                    int[] op = oldPos.get(t);
                    if (stagger) {
                        c.setAlpha(0f); c.setTranslationY(Ui.dp(this, 28)); c.setScaleX(0.94f); c.setScaleY(0.94f);
                        int delay = Math.min(220, 24 * Math.abs(idx - curIdx));   // kartu aktif dulu, lalu menyebar
                        c.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setStartDelay(delay).setDuration(280).setInterpolator(Ui.SNAP).start();
                    } else if (op != null && (op[0] != nx || op[1] != ny)) {
                        c.setTranslationX(op[0] - nx); c.setTranslationY(op[1] - ny);
                        c.animate().translationX(0f).translationY(0f).setDuration(280).setInterpolator(Ui.SNAP).start();
                    } else if (op == null) {
                        c.setAlpha(0f); c.setScaleX(0.9f); c.setScaleY(0.9f);
                        c.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(Ui.SNAP).start();
                    }
                    idx++;
                }
            });
        };
        rebuild[0].run();

        View btnNew = v.findViewById(R.id.btnNewTab);
        Ui.pressable(btnNew);
        btnNew.setScaleX(0.6f); btnNew.setScaleY(0.6f); btnNew.setAlpha(0f);
        btnNew.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(120).setDuration(340).setInterpolator(Ui.POP).start();
        btnNew.setOnClickListener(x -> { Ui.haptic(x); d.dismiss(); newTab(homeUrl(), true, null);
            urlBar.postDelayed(() -> { if (!headerCollapsed) { urlBar.requestFocus(); Ui.showKeyboard(urlBar); } else showQuickUrl(true); }, 260); });
        v.findViewById(R.id.btnTabsClose).setOnClickListener(x -> d.dismiss());
        Ui.pressable(v.findViewById(R.id.btnTabsClose));
        v.findViewById(R.id.btnCloseAll).setOnClickListener(x -> {
            new AlertDialog.Builder(this).setTitle("Tutup semua tab?")
                .setMessage(tabs.size() + " tab akan ditutup. Cookie & login sesi tetap tersimpan.")
                .setPositiveButton("Tutup semua", (dd, w) -> {
                    // kartu berhamburan keluar, lalu dialog ditutup
                    int i = 0;
                    for (View c : cards.values()) c.animate().translationY(Ui.dp(this, 60)).alpha(0f).scaleX(0.9f).scaleY(0.9f)
                        .setStartDelay(Math.min(160, 18 * i++)).setDuration(220).setInterpolator(Ui.EASE).start();
                    grid.postDelayed(() -> { closeAllTabs(); d.dismiss(); }, 260);
                })
                .setNegativeButton("Batal", null).show();
        });
        // web di belakang sedikit mengecil saat switcher terbuka (kesan "zoom out"), kembali saat ditutup
        webCard.animate().scaleX(0.94f).scaleY(0.94f).setDuration(260).setInterpolator(Ui.SNAP).start();
        d.setOnDismissListener(x -> {
            tabsDialog = null; showNavUi(0);
            webCard.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(Ui.SNAP).start();
        });
        d.show();
        // scroll ke kartu aktif
        final View scroll = v.findViewById(R.id.tabsScroll);
        scroll.post(() -> { int idx = tabs.indexOf(cur); if (idx >= 2) ((android.widget.ScrollView) scroll).smoothScrollTo(0, (idx / 2) * Ui.dp(this, 245)); });
    }

    private View tabCard(final Tab t, final Dialog d, final Runnable rebuild) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_tab, null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT); lp.weight = 1;
        int m = Ui.dp(this, 6); lp.setMargins(m, m, m, m);
        card.setLayoutParams(lp);
        card.setSelected(t == cur);
        ((TextView) card.findViewById(R.id.tabTitle)).setText(t.displayTitle());
        TextView host = card.findViewById(R.id.tabHost);
        String h = t.host();
        host.setText(h); host.setVisibility(h.isEmpty() ? View.GONE : View.VISIBLE);
        ImageView ic = card.findViewById(R.id.tabIcon);
        if (t.icon != null) { ic.setImageBitmap(t.icon); ic.setImageTintList(null); }
        ImageView th = card.findViewById(R.id.tabThumb);
        if (t.thumb != null && !t.thumb.isRecycled()) {
            th.setImageBitmap(t.thumb);
            th.setScaleType(ImageView.ScaleType.MATRIX);
            th.addOnLayoutChangeListener((vv, l, tt, r, b, ol, ot, or, ob) -> {
                if (r - l <= 0 || t.thumb == null || t.thumb.isRecycled()) return;
                android.graphics.Matrix mx = new android.graphics.Matrix();
                float sc = (float) (r - l) / t.thumb.getWidth();
                mx.setScale(sc, sc);
                th.setImageMatrix(mx);
            });
        } else {
            th.setScaleType(ImageView.ScaleType.CENTER);
            th.setImageResource(R.drawable.ic_globe);
            th.setImageTintList(android.content.res.ColorStateList.valueOf(0xFF6F717A));
        }
        Ui.pressable(card);
        card.setOnClickListener(x -> {
            Ui.haptic(x);
            // kartu yang dipilih "membesar" sebentar → dialog tutup → tab tampil
            x.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).setInterpolator(Ui.SNAP)
                .withEndAction(() -> { d.dismiss(); selectTab(t); }).start();
        });
        card.setOnLongClickListener(x -> { Ui.haptic(x); closeTabAnimated(card, t, rebuild); return true; });
        card.findViewById(R.id.tabClose).setOnClickListener(x -> { Ui.haptic(x); closeTabAnimated(card, t, rebuild); });
        return card;
    }

    private void closeTabAnimated(View card, Tab t, Runnable rebuild) {
        if (!tabs.contains(t)) return;
        card.setEnabled(false);
        card.animate().translationX(card.getWidth() * 1.1f).alpha(0f).rotation(4f).setDuration(200).setInterpolator(Ui.EASE)
            .withEndAction(() -> { closeTab(t); rebuild.run(); }).start();
    }

    /** Long-press link/gambar di halaman → menu konteks. */
    private boolean onWebLongPress(final Tab t) {
        WebView.HitTestResult r = t.web.getHitTestResult();
        int type = r.getType();
        final String extra = r.getExtra();
        if (type == WebView.HitTestResult.SRC_ANCHOR_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper()) {
                @Override public void handleMessage(android.os.Message msg) {
                    String href = msg.getData().getString("url");
                    if (href == null || href.isEmpty()) href = extra;
                    showLinkMenu(t, href, type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE ? extra : null);
                }
            };
            t.web.requestFocusNodeHref(h.obtainMessage());
            return true;
        }
        if (type == WebView.HitTestResult.IMAGE_TYPE && extra != null) { showLinkMenu(t, null, extra); return true; }
        return false;
    }

    private void showLinkMenu(final Tab t, final String href, final String img) {
        final List<String> items = new ArrayList<>();
        if (href != null) { items.add("Buka di tab baru"); items.add("Buka di tab baru (latar)"); items.add("Salin tautan"); items.add("Bagikan tautan"); }
        if (img != null) { items.add("Buka gambar di tab baru"); items.add("Unduh gambar"); items.add("Salin alamat gambar"); }
        if (items.isEmpty()) return;
        String shown = href != null ? href : img;
        new AlertDialog.Builder(this)
            .setTitle(shown.length() > 90 ? shown.substring(0, 90) + "…" : shown)
            .setItems(items.toArray(new String[0]), (d, w) -> {
                String c = items.get(w);
                switch (c) {
                    case "Buka di tab baru": { Tab nt = newTab(href, false, t); if (nt != null) selectTab(nt, 1); break; }
                    case "Buka di tab baru (latar)": { Tab nt = newTab(href, false, t); if (nt != null && nt != cur) { nt.loaded = true; nt.pendingUrl = null; nt.web.loadUrl(href); } Toast.makeText(this, "Dibuka di latar", Toast.LENGTH_SHORT).show(); break; }
                    case "Salin tautan": copy(href); break;
                    case "Bagikan tautan": share(href); break;
                    case "Buka gambar di tab baru": { Tab nt = newTab(img, false, t); if (nt != null) selectTab(nt, 1); break; }
                    case "Unduh gambar": download(img, t.web.getSettings().getUserAgentString(), null, null); break;
                    case "Salin alamat gambar": copy(img); break;
                }
            }).show();
    }

    private void copy(String s) {
        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("url", s));
        Toast.makeText(this, "Disalin", Toast.LENGTH_SHORT).show();
    }

    private void share(String s) {
        Intent i = new Intent(Intent.ACTION_SEND); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TEXT, s);
        try { startActivity(Intent.createChooser(i, "Bagikan")); } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------------ Unduhan
    private void startDownload(final Tab t, final String url, final String ua, final String cd, final String mime, final long len) {
        if (url == null) return;
        if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingDownload = () -> startDownload(t, url, ua, cd, mime, len);
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERM_DL);
            return;
        }
        if (url.startsWith("blob:")) {
            if (t == null || t.web == null) return;
            String name = Downloads.fileName(url, cd, mime);
            if (name.startsWith("downloadfile")) name = "";
            // Picu dl.js lewat CustomEvent (tanpa properti global). Listener memanggil preventDefault → dispatchEvent = false → tertangani.
            String js = "(function(u,m,n){try{return !document.dispatchEvent(new CustomEvent(" + JSONObject.quote(DL_BRIDGE)
                + ",{cancelable:true,detail:{u:u,m:m,n:n}}))}catch(e){return false}})("
                + JSONObject.quote(url) + "," + JSONObject.quote(mime == null ? "" : mime) + "," + JSONObject.quote(name) + ");";
            t.web.evaluateJavascript(js, r -> { if (!"true".equals(r)) Toast.makeText(this, "Penangkap unduhan belum siap — muat ulang halaman", Toast.LENGTH_SHORT).show(); });
            return;
        }
        if (url.startsWith("data:")) {
            try {
                int comma = url.indexOf(',');
                String meta = url.substring(5, comma), body = url.substring(comma + 1);
                String m = meta.contains(";") ? meta.substring(0, meta.indexOf(';')) : meta;
                byte[] bytes = meta.contains(";base64") ? android.util.Base64.decode(body, android.util.Base64.DEFAULT) : Uri.decode(body).getBytes("UTF-8");
                String name = Downloads.fileName("", cd, m.isEmpty() ? mime : m);
                if (name.startsWith("downloadfile")) name = "unduhan-" + System.currentTimeMillis() + name.substring(name.lastIndexOf('.') < 0 ? name.length() : name.lastIndexOf('.'));
                downloads.saveBytes(name, m.isEmpty() ? mime : m, bytes);
                onDownloadStarted(name);
            } catch (Exception e) { Toast.makeText(this, "Data URL tidak valid", Toast.LENGTH_SHORT).show(); }
            return;
        }
        if (!url.startsWith("http")) { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {} return; }
        Downloads.Item it = downloads.start(url, ua, cd, mime, currentUrl);
        onDownloadStarted(it.name + (len > 0 ? " · " + Downloads.size(len) : ""));
    }

    private void download(String url, String ua, String cd, String mime) { startDownload(cur, url, ua, cd, mime, -1); }

    private void onDownloadStarted(String label) {
        Ui.haptic(btnReload);
        showTabHint("⬇ " + label + "  ·  Lihat", this::showDownloads);
    }

    private void showDownloads() { downloads.showSheet(this, web != null ? web.getSettings().getUserAgentString() : null); }

    /** Jembatan JS → Java untuk blob: — data dikirim per potongan ke berkas sementara, lalu dipindah ke Download. */
    private final class DlBridge {
        final Tab tab;
        final java.util.Map<String, Object[]> open = new java.util.concurrent.ConcurrentHashMap<>();   // id → {File, OutputStream, name, mime, total}
        DlBridge(Tab t) { tab = t; }

        @JavascriptInterface public void begin(String id, String name, String mime, long total) {
            try {
                java.io.File dir = new java.io.File(getCacheDir(), "blobdl"); dir.mkdirs();
                java.io.File f = new java.io.File(dir, "dl-" + System.nanoTime() + ".part");
                if (!f.createNewFile()) throw new java.io.IOException("createNewFile");
                open.put(id, new Object[]{f, new java.io.BufferedOutputStream(new java.io.FileOutputStream(f), 256 * 1024), name, mime, Long.valueOf(total)});
                runOnUiThread(() -> showTabHint("⬇ Menyiapkan " + (name == null || name.isEmpty() ? "berkas" : name) + "…", null));
            } catch (Throwable e) { fail("Tidak bisa menulis cache (" + e.getClass().getSimpleName() + ")"); }
        }
        @JavascriptInterface public void chunk(String id, String b64) {
            Object[] o = open.get(id);
            if (o == null) return;
            try {
                byte[] d = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                ((java.io.OutputStream) o[1]).write(d);
                long total = (Long) o[4];
                if (total > 4L * 1024 * 1024) {
                    long got = ((java.io.File) o[0]).length() + d.length;
                    final int pct = (int) (got * 100 / Math.max(1, total));
                    if (pct / 10 != (int) (((got - d.length) * 100 / Math.max(1, total)) / 10))
                        runOnUiThread(() -> showTabHint("⬇ Menyiapkan… " + pct + "%", null));
                }
            } catch (Throwable e) { abort(id); fail("Gagal menulis potongan"); }
        }
        @JavascriptInterface public void end(String id) {
            Object[] o = open.remove(id);
            if (o == null) return;
            try {
                ((java.io.OutputStream) o[1]).close();
                java.io.File f = (java.io.File) o[0];
                String m = o[3] == null || ((String) o[3]).isEmpty() ? "application/octet-stream" : (String) o[3];
                String n = (String) o[2];
                if (n == null || n.isEmpty()) n = "unduhan-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(new java.util.Date());
                if (!n.contains(".")) { String ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(m); if (ext != null) n += "." + ext; }
                final String fn = n, fm = m; final long len = f.length();
                runOnUiThread(() -> { downloads.saveFile(fn, fm, f); onDownloadStarted(fn + " · " + Downloads.size(len)); });
            } catch (Throwable e) { fail("Gagal menyimpan"); }
        }
        @JavascriptInterface public void dataUrl(String url, String name) {
            runOnUiThread(() -> startDownload(tab, url, null, name == null || name.isEmpty() ? null : "attachment; filename=\"" + name + "\"", null, -1));
        }
        @JavascriptInterface public void fail(String reason) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Unduhan gagal: " + reason, Toast.LENGTH_SHORT).show());
        }
        private void abort(String id) {
            Object[] o = open.remove(id);
            if (o == null) return;
            try { ((java.io.OutputStream) o[1]).close(); } catch (Throwable ignored) {}
            ((java.io.File) o[0]).delete();
        }
    }

    @Override public void onRequestPermissionsResult(int rc, String[] p, int[] g) {
        super.onRequestPermissionsResult(rc, p, g);
        if (rc == REQ_PERM_DL) {
            Runnable r = pendingDownload; pendingDownload = null;
            if (g.length > 0 && g[0] == android.content.pm.PackageManager.PERMISSION_GRANTED && r != null) r.run();
            else Toast.makeText(this, "Izin penyimpanan ditolak — unduhan dibatalkan", Toast.LENGTH_SHORT).show();
        }
    }

    /** Selesai/gagal dari DownloadManager sistem → segarkan daftar + hint yang bisa diketuk. */
    private void registerDownloadReceiver() {
        dlReceiver = new android.content.BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                long id = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                downloads.refresh();
                for (Downloads.Item it : downloads.all()) if (it.dmId == id) {
                    if (it.status == Downloads.DONE) showTabHint("✓ " + it.name + "  ·  Buka", () -> downloads.open(MainActivity.this, it));
                    else if (it.status == Downloads.FAILED) showTabHint("✕ " + it.name + " gagal  ·  Lihat", MainActivity.this::showDownloads);
                }
            }
        };
        android.content.IntentFilter f = new android.content.IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(dlReceiver, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(dlReceiver, f);
    }

    // ------------------------------------------------------------------ Upload (<input type=file>)
    private boolean openFileChooser(ValueCallback<Uri[]> cb, WebChromeClient.FileChooserParams params) {
        if (fileCallback != null) { try { fileCallback.onReceiveValue(null); } catch (Exception ignored) {} }
        fileCallback = cb; captureUri = null;
        String[] accept = params.getAcceptTypes();
        boolean wantsImage = false, onlyNonImage = accept != null && accept.length > 0;
        if (accept != null) for (String a : accept) {
            if (a == null || a.trim().isEmpty() || a.equals("*/*")) { wantsImage = true; onlyNonImage = false; }
            else if (a.startsWith("image/") || a.equals(".jpg") || a.equals(".jpeg") || a.equals(".png")) { wantsImage = true; onlyNonImage = false; }
        }
        if (accept == null || accept.length == 0) wantsImage = true;

        Intent camera = null;
        if (wantsImage) {
            try {
                java.io.File f = new java.io.File(CaptureProvider.dir(this), "IMG_" + System.currentTimeMillis() + ".jpg");
                f.createNewFile();
                captureUri = CaptureProvider.uriFor(this, f);
                camera = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                camera.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, captureUri);
                camera.setClipData(android.content.ClipData.newRawUri("", captureUri));
                camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception e) { camera = null; captureUri = null; }
        }
        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("*/*");
        if (accept != null && accept.length > 0) {
            List<String> mimes = new ArrayList<>();
            for (String a : accept) {
                if (a == null) continue; a = a.trim();
                if (a.isEmpty()) continue;
                if (a.startsWith(".")) { String m = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(a.substring(1).toLowerCase()); if (m != null) mimes.add(m); }
                else if (a.contains("/")) mimes.add(a);
            }
            if (!mimes.isEmpty()) {
                pick.putExtra(Intent.EXTRA_MIME_TYPES, mimes.toArray(new String[0]));
                if (mimes.size() == 1) pick.setType(mimes.get(0));
            }
        }
        if (params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

        Intent launch;
        if (params.isCaptureEnabled() && camera != null) launch = camera;
        else {
            launch = Intent.createChooser(pick, onlyNonImage ? "Pilih berkas" : "Pilih berkas atau foto");
            if (camera != null) launch.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
        }
        try { startActivityForResult(launch, REQ_FILE); return true; }
        catch (Exception e) {
            fileCallback = null; cb.onReceiveValue(null);
            Toast.makeText(this, "Tidak ada aplikasi pemilih berkas", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private void onFileChosen(int res, Intent data) {
        ValueCallback<Uri[]> cb = fileCallback; fileCallback = null;
        if (cb == null) return;
        Uri[] out = null;
        if (res == RESULT_OK) {
            if (data != null && data.getClipData() != null && data.getClipData().getItemCount() > 0) {
                android.content.ClipData cd = data.getClipData();
                out = new Uri[cd.getItemCount()];
                for (int i = 0; i < out.length; i++) out[i] = cd.getItemAt(i).getUri();
            } else if (data != null && data.getData() != null) out = new Uri[]{data.getData()};
            else if (captureUri != null) {
                java.io.File f = new java.io.File(CaptureProvider.dir(this), captureUri.getLastPathSegment());
                if (f.length() > 0) out = new Uri[]{captureUri};
            }
        }
        if (out == null && captureUri != null) new java.io.File(CaptureProvider.dir(this), captureUri.getLastPathSegment()).delete();
        captureUri = null;
        cb.onReceiveValue(out);
    }

    /** Buang foto kamera lama (> 1 hari) agar cache tidak menumpuk. */
    private void cleanCaptures() {
        try {
            java.io.File[] fs = CaptureProvider.dir(this).listFiles();
            long cut = System.currentTimeMillis() - 86400000L;
            if (fs != null) for (java.io.File f : fs) if (f.lastModified() < cut) f.delete();
        } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------------ WebView
    private void setupWebView(final Tab t) {
        final WebView wv = t.web;
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        // Mode performa (selalu aktif): render prioritas tinggi, cache & pre-raster agresif — tukar RAM dengan kecepatan.
        try { s.setRenderPriority(WebSettings.RenderPriority.HIGH); } catch (Throwable ignored) {}
        s.setLoadsImagesAutomatically(true);
        s.setBlockNetworkImage(false);
        s.setEnableSmoothTransition(true);
        // Tidak ada hardware layer permanen: WebView fullscreen di layer offscreen = re-upload texture tiap frame scroll.
        // Layer hanya dipasang sementara selama animasi pindah tab (lihat selectTab/resetWeb).
        s.setSaveFormData(false);
        s.setGeolocationEnabled(false);          // lokasi asli perangkat tidak pernah bocor (VPN tetap konsisten)
        s.setSavePassword(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);   // popup tanpa ketukan pengguna tetap diblokir
        s.setSupportMultipleWindows(true);                   // target=_blank / window.open → tab baru
        s.setUserAgentString(profile.userAgent(store.desktop()));
        s.setOffscreenPreRaster(true);           // pre-raster tile di luar layar => scroll lebih mulus
        wv.setBackgroundColor(webColor());
        applyWebDark(s);

        applyAntiDetection(wv, s);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(wv, true);

        wv.setOnScrollChangeListener((v, x, y, ox, oy) -> {
            if (t != cur) return;
            int dy = y - lastScrollY;
            lastScrollY = y;
            if (!urlFocused && dy > 12 && y > Ui.dp(this, 48)) collapseHeader(true);
        });
        wv.setOnLongClickListener(v -> onWebLongPress(t));

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                String sch = u.getScheme();
                if ("http".equals(sch) || "https".equals(sch)) return false;
                try {
                    Intent i = "intent".equals(sch) ? Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME)
                                                    : new Intent(Intent.ACTION_VIEW, u);
                    startActivity(i);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Tidak ada aplikasi untuk link ini", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            @Override public void onPageStarted(WebView v, String url, android.graphics.Bitmap f) {
                t.url = url; t.loading = true; t.progress = 0; t.icon = null; t.title = "";
                if (!maxModeDocStart) { v.evaluateJavascript(maxModeGuardScript(), null); if (isMaxModeHost(url)) v.evaluateJavascript(maxModeScript(), null); }   // WebView lama: tanpa document_start
                if (t != cur) return;
                currentUrl = url; loading = true;
                rt.currentUrl = url; rt.currentTitle = ""; updateMaxFab();
                if (!urlFocused) showUrl(url);
                progress.setProgress(0);
                Ui.fadeIn(progress, 120);
                btnReload.setImageResource(R.drawable.ic_close);
                updateNavState();
            }
            @Override public void onPageFinished(WebView v, String url) {
                t.url = url; t.loading = false; t.progress = 100;
                CookieManager.getInstance().flush();
                saveTabs();
                if (t != cur) return;
                currentUrl = url; loading = false;
                rt.currentUrl = url; rt.currentTitle = t.title; rt.notifyTabUpdated(); rt.reArmFillIfPending(v, url); updateMaxFab();
                if (!urlFocused) showUrl(url);
                Ui.fadeOut(progress, 250, false);
                btnReload.setImageResource(R.drawable.ic_reload);
                updateNavState();
            }
            @Override public void doUpdateVisitedHistory(WebView v, String url, boolean reload) { t.url = url; if (t == cur) { currentUrl = url; rt.currentUrl = url; if (!urlFocused) showUrl(url); updateNavState(); updateMaxFab(); } }
        });
        wv.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) {
                t.progress = p;
                if (t != cur) return;
                android.animation.ObjectAnimator.ofInt(progress, "progress", p).setDuration(160).start();
                if (p >= 100) Ui.fadeOut(progress, 250, false);
            }
            @Override public void onReceivedTitle(WebView v, String title) { t.title = title == null ? "" : title; if (t == cur) rt.currentTitle = t.title; }
            @Override public boolean onConsoleMessage(android.webkit.ConsoleMessage cm) {
                if (!isMaxModeHost(t.url)) return false;
                rt.logConsole("page", cm.messageLevel().name(), cm.message(), cm.sourceId(), cm.lineNumber());
                return true;
            }
            @Override public void onReceivedIcon(WebView v, android.graphics.Bitmap icon) { t.icon = icon; }
            @Override public boolean onCreateWindow(WebView v, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                if (!isUserGesture) return false;                       // popup otomatis → tolak
                Tab nt = newTab(null, true, t);
                if (nt == null || nt == t) return false;
                nt.loaded = true;
                ((WebView.WebViewTransport) resultMsg.obj).setWebView(nt.web);
                resultMsg.sendToTarget();
                return true;
            }
            @Override public void onCloseWindow(WebView v) { if (tabs.size() > 1) closeTab(t); }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, android.webkit.GeolocationPermissions.Callback cb) { cb.invoke(origin, false, false); }
            @Override public void onPermissionRequest(final android.webkit.PermissionRequest req) {
                // Kamera/mikrofon: tanya pengguna per situs, tidak pernah otomatis
                StringBuilder what = new StringBuilder();
                for (String r : req.getResources()) {
                    if (r.equals(android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE)) what.append(what.length() > 0 ? " & " : "").append("kamera");
                    else if (r.equals(android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE)) what.append(what.length() > 0 ? " & " : "").append("mikrofon");
                }
                if (what.length() == 0) { req.deny(); return; }
                new android.app.AlertDialog.Builder(MainActivity.this)
                    .setTitle(req.getOrigin().getHost())
                    .setMessage("Situs ini meminta akses " + what + ".")
                    .setPositiveButton("Izinkan", (d, w) -> { try { req.grant(req.getResources()); } catch (Throwable e) { req.deny(); } })
                    .setNegativeButton("Tolak", (d, w) -> req.deny())
                    .setOnCancelListener(d -> req.deny()).show();
            }
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                return openFileChooser(cb, params);
            }
        });
        wv.addJavascriptInterface(new DlBridge(t), DL_BRIDGE);
        wv.addJavascriptInterface(new Bridge(rt, "page", wv), MAX_BRIDGE);
        installMaxModeScript(wv);
        wv.setDownloadListener((url, ua, cd, mime, len) -> {
            boolean emptyTab = tabs.size() > 1 && !url.startsWith("blob:") && (t.url == null || t.url.isEmpty() || t.url.equals(url))
                    && t.web.copyBackForwardList().getSize() == 0;
            startDownload(t, url, ua, cd, mime, len);
            // Navigasi unduhan tidak mengganti halaman → kembalikan URL bar & progress ke halaman yang masih tampil
            String real = t.web.getUrl();
            t.loading = false; t.progress = 100;
            if (real != null && !real.equals(url)) t.url = real;
            if (t == cur) {
                loading = false; currentUrl = t.url;
                if (!urlFocused) showUrl(t.url);
                Ui.fadeOut(progress, 200, false);
                btnReload.setImageResource(R.drawable.ic_reload);
                updateNavState();
            }
            // Tab yang dibuka hanya untuk unduhan (target=_blank tanpa halaman) → tutup otomatis
            if (emptyTab) t.web.post(() -> { if (tabs.contains(t)) closeTab(t); });
        });
    }

    /** Semua trik agar WebView tampil sebagai Chrome Android biasa dengan identitas sesi ini. */
    private void applyAntiDetection(WebView wv, WebSettings s) {
        // 1. Hapus header X-Requested-With (bocorin nama paket app => tanda WebView)
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST))
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(s, Collections.<String>emptySet());
        } catch (Throwable ignored) {}

        // 2. Client Hints (Sec-CH-UA-*, navigator.userAgentData) sesuai identitas sesi
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
                List<UserAgentMetadata.BrandVersion> brands = new ArrayList<>();
                brands.add(new UserAgentMetadata.BrandVersion.Builder().setBrand("Chromium")
                        .setMajorVersion(profile.chromeMajor()).setFullVersion(profile.chromeFull).build());
                brands.add(new UserAgentMetadata.BrandVersion.Builder().setBrand("Google Chrome")
                        .setMajorVersion(profile.chromeMajor()).setFullVersion(profile.chromeFull).build());
                brands.add(new UserAgentMetadata.BrandVersion.Builder().setBrand("Not/A)Brand")
                        .setMajorVersion("24").setFullVersion("24.0.0.0").build());
                UserAgentMetadata md = new UserAgentMetadata.Builder()
                        .setBrandVersionList(brands)
                        .setFullVersion(profile.chromeFull)
                        .setPlatform(store.desktop() ? "Linux" : "Android")
                        .setPlatformVersion(store.desktop() ? "6.5.0" : profile.androidVer + ".0.0")
                        .setArchitecture(store.desktop() ? "x86" : "")
                        .setModel(store.desktop() ? "" : profile.model)
                        .setMobile(!store.desktop())
                        .setBitness(store.desktop() ? 64 : 0)
                        .setWow64(false)
                        .build();
                WebSettingsCompat.setUserAgentMetadata(s, md);
                nativeUAD = true;
            }
        } catch (Throwable ignored) { nativeUAD = false; }

        // 3. Script fingerprint dijalankan sebelum JS halaman (semua origin)
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                androidx.webkit.ScriptHandler old = (androidx.webkit.ScriptHandler) wv.getTag(R.id.tabCount);
                if (old != null) { try { old.remove(); } catch (Throwable ignored) {} }
                if (spoofScriptCache == null) spoofScriptCache = buildSpoofScript() + "\n" + dlJs();
                wv.setTag(R.id.tabCount, WebViewCompat.addDocumentStartJavaScript(wv, spoofScriptCache, Collections.singleton("*")));
            }
        } catch (Throwable ignored) {}
    }

    private String dlJsCache;
    /** Skrip penangkap unduhan blob: (assets/dl.js), disuntik ke semua frame setelah spoof script. */
    private String dlJs() {
        if (dlJsCache == null) {
            try {
                InputStream in = getAssets().open("dl.js");
                ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) > 0) bo.write(b, 0, n); in.close();
                dlJsCache = bo.toString("UTF-8").replace("__DL_BRIDGE__", DL_BRIDGE);
            } catch (Exception e) { dlJsCache = ""; }
        }
        return dlJsCache;
    }

    private String buildSpoofScript() {
        try {
            InputStream in = getAssets().open("spoof.js");
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            JSONObject o = new JSONObject();
            o.put("model", profile.model);
            o.put("androidVer", profile.androidVer);
            o.put("full", profile.chromeFull);
            o.put("major", profile.chromeMajor());
            o.put("cores", profile.cores);
            o.put("desktop", store.desktop());
            o.put("tz", Regions.tz(profile.region));
            o.put("lang", Regions.locale(profile.region));
            o.put("langs", Regions.acceptLanguage(profile.region));
            o.put("memGb", profile.memGb);
            o.put("seed", profile.seed);
            o.put("jsUAD", !nativeUAD);
            String[] gpu = gpuFor(profile);
            o.put("gpuVendor", gpu[0]);
            o.put("gpuRenderer", gpu[1]);
            String src = bo.toString("UTF-8").replace("__PROFILE__", o.toString());
            // Sisipkan salinan kode ini sendiri (sebagai string literal JS) agar bisa disuntik ke Worker
            String inner = src.replace("'__SELF_SOURCE__'", "''");
            return src.replace("'__SELF_SOURCE__'", new org.json.JSONStringer().array().value(inner).endArray().toString().replaceAll("^\\[|\\]$", ""));
        } catch (Exception e) { return ""; }
    }

    private static String[] gpuFor(Profile p) {
        if ("Google".equals(p.brand)) return new String[]{"ARM", p.model.contains("8") ? "Mali-G715" : "Mali-G710"};
        if (p.model.startsWith("SM-A0") || p.model.startsWith("SM-A1")) return new String[]{"ARM", "Mali-G57 MC2"};
        if (p.model.startsWith("SM-A3") || p.model.startsWith("SM-M3")) return new String[]{"ARM", "Mali-G68 MC4"};
        String[] adreno = {"610", "619", "642L", "650", "660", "695", "710", "730", "740", "750"};
        return new String[]{"Qualcomm", "Adreno (TM) " + adreno[(int) (p.seed % adreno.length)]};
    }

    // ------------------------------------------------------------------ Navigasi
    private void go(String input) {
        String q = input.trim();
        if (q.isEmpty()) return;
        String url;
        if (q.startsWith("http://") || q.startsWith("https://")) url = q;
        else if (q.contains(".") && !q.contains(" ")) url = "https://" + q;
        else url = ProfileStore.ENGINES[store.engine()][2] + Uri.encode(q);
        web.loadUrl(url);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(urlBar.getWindowToken(), 0);
        urlBar.clearFocus();
        web.requestFocus();
    }

    /** Tampilkan host saja saat tidak diedit (bersih seperti Chrome/Safari). */
    private void showUrl(String url) {
        if (url == null) url = "";
        String shown = url;
        try {
            Uri u = Uri.parse(url);
            if (u.getHost() != null) shown = u.getHost().replaceFirst("^www\\.", "");
        } catch (Exception ignored) {}
        urlBar.setText(shown);
        urlIcon.setImageResource(iconFor(url));
    }

    private static int iconFor(String url) {
        if (url == null || url.isEmpty()) return R.drawable.ic_search;
        return url.startsWith("https://") ? R.drawable.ic_lock : R.drawable.ic_globe;
    }

    private void updateNavState() {
        if (web == null) return;
        btnBack.animate().alpha(web.canGoBack() ? 1f : 0.35f).setDuration(150).start();
        btnForward.animate().alpha(web.canGoForward() ? 1f : 0.35f).setDuration(150).start();
    }

    /** Ganti UA/Client-Hints/spoof sesuai mode desktop, lalu muat ulang halaman. */
    /** Warna latar area web: gelap saat "Web gelap" aktif, putih jika tidak (hindari kilatan putih saat memuat). */
    private int webColor() { return store.darkWeb() ? 0xFF0E0E12 : 0xFFFFFFFF; }

    /** Gelapkan konten halaman lewat androidx.webkit (algorithmic darkening di Android 13+, force dark di bawahnya). */
    private void applyWebDark(WebSettings s) {
        boolean on = store.darkWeb();
        try {
            if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, on);
            } else if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.FORCE_DARK)) {
                WebSettingsCompat.setForceDark(s, on ? WebSettingsCompat.FORCE_DARK_ON : WebSettingsCompat.FORCE_DARK_OFF);
                if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.FORCE_DARK_STRATEGY))
                    WebSettingsCompat.setForceDarkStrategy(s, WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING);
            }
        } catch (Throwable ignored) {}
    }

    private void applyDarkWebMode() {
        int c = webColor();
        if (!headerCollapsed) webBg.setColor(c);
        for (Tab t : tabs) {
            if (t.web == null) continue;
            t.web.setBackgroundColor(c);
            applyWebDark(t.web.getSettings());
        }
        Toast.makeText(this, store.darkWeb() ? "Web gelap aktif" : "Web gelap nonaktif", Toast.LENGTH_SHORT).show();
    }

    private void applyDesktopMode() {
        spoofScriptCache = null;
        rt.userAgent = profile.userAgent(store.desktop());
        for (Tab t : tabs) {
            if (t.web == null) continue;
            WebSettings s = t.web.getSettings();
            s.setUserAgentString(profile.userAgent(store.desktop()));
            applyAntiDetection(t.web, s);
            installMaxModeScript(t.web);
            if (t.loaded && t == cur) t.web.reload();
            else if (t.loaded) { t.pendingUrl = t.url; t.loaded = false; }   // tab lain dimuat ulang saat dibuka
        }
        Toast.makeText(this, store.desktop() ? "Mode desktop aktif" : "Mode ponsel", Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ Nav pill: susut ke samping
    /** Geser nav pill ke kanan/kiri → menyusut jadi bulatan kecil di tepi; ketuk bulatan → kembali. */
    private void setupNavDock() {
        final float threshold = Ui.dp(this, 56);
        ((SwipeRow) nav).setListener(new SwipeRow.Listener() {
            @Override public void onSwipeMove(float dx) {
                nav.setTranslationX(dx);
                nav.setAlpha(Math.max(0.35f, 1f - Math.abs(dx) / (threshold * 3f)));
            }
            @Override public void onSwipeEnd(float dx, float vx) {
                if (Math.abs(dx) > threshold || Math.abs(vx) > 900f) dockNav(dx >= 0);
                else nav.animate().translationX(0f).alpha(1f).setDuration(220).setInterpolator(Ui.EASE).start();
            }
        });
        Ui.pressable(navDock);
        navDock.setOnClickListener(v -> { Ui.haptic(v); undockNav(); });
        // bulatan bisa digeser naik/turun sepanjang tepi supaya tidak menutupi tombol situs
        navDock.setOnTouchListener(new View.OnTouchListener() {
            float y0, ty0; boolean moved;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: y0 = e.getRawY(); ty0 = v.getTranslationY(); moved = false; return false;
                    case MotionEvent.ACTION_MOVE: {
                        float dy = e.getRawY() - y0;
                        if (!moved && Math.abs(dy) < Ui.dp(MainActivity.this, 6)) return false;
                        moved = true;
                        float min = -(findViewById(R.id.root).getHeight() - v.getHeight() - Ui.dp(MainActivity.this, 20));
                        v.setTranslationY(Math.max(min, Math.min(0f, ty0 + dy)));
                        return true;
                    }
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                        if (moved) { v.setPressed(false); return true; }
                        return false;
                }
                return false;
            }
        });
    }

    private void dockNav(boolean toRight) {
        if (navDocked) return;
        navDocked = true;
        float off = (toRight ? 1 : -1) * (findViewById(R.id.root).getWidth() / 2f + nav.getWidth());
        nav.animate().translationX(off).alpha(0f).setDuration(220).setInterpolator(Ui.EASE)
            .withEndAction(() -> { navWrap.setVisibility(View.GONE); nav.setTranslationX(0f); nav.setAlpha(1f); }).start();
        dockAvatar.bind(profile, false);
        // bulatan muncul di sisi yang sama dengan arah geser (kiri atau kanan)
        android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) navDock.getLayoutParams();
        lp.gravity = android.view.Gravity.BOTTOM | (toRight ? android.view.Gravity.END : android.view.Gravity.START);
        navDock.setLayoutParams(lp);
        navDock.setScaleX(0.6f); navDock.setScaleY(0.6f); navDock.setAlpha(0f);
        navDock.setVisibility(View.VISIBLE);
        navDock.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(120).setDuration(220).setInterpolator(Ui.EASE).start();
    }

    private void undockNav() {
        if (!navDocked) return;
        navDocked = false;
        navDock.animate().setStartDelay(0).scaleX(0.6f).scaleY(0.6f).alpha(0f).setDuration(140)
            .withEndAction(() -> navDock.setVisibility(View.GONE)).start();
        navWrap.setAlpha(1f); navWrap.setVisibility(View.VISIBLE);
        nav.setTranslationY(Ui.dp(this, 30)); nav.setAlpha(0f);
        nav.animate().translationY(0f).alpha(1f).setDuration(240).setInterpolator(Ui.EASE).start();
    }

    /** Sembunyikan UI nav (pill ATAU bulatan dock) — dipakai saat keyboard terbuka. */
    private void hideNavUi(int dur) { Ui.fadeOut(navDocked ? navDock : navWrap, dur, true); }
    private void showNavUi(int dur) { Ui.fadeIn(navDocked ? navDock : navWrap, dur); }

    /** Address bar cepat di atas nav (mode penuh). */
    private void showQuickUrl(boolean show) {
        if (show) {
            quickUrlBar.setText(currentUrl);
            quickUrl.setTranslationY(Ui.dp(this, 24)); quickUrl.setAlpha(0f);
            quickUrl.setVisibility(View.VISIBLE);
            quickUrl.animate().translationY(0f).alpha(1f).setDuration(220).setInterpolator(Ui.EASE).start();
            hideNavUi(120);
            quickUrlBar.requestFocus(); quickUrlBar.selectAll();
            Ui.showKeyboard(quickUrlBar);
        } else {
            if (quickUrl.getVisibility() != View.VISIBLE) return;
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(quickUrlBar.getWindowToken(), 0);
            quickUrlBar.clearFocus();
            quickUrl.animate().translationY(Ui.dp(this, 24)).alpha(0f).setDuration(160)
                .withEndAction(() -> quickUrl.setVisibility(View.GONE)).start();
            showNavUi(180);
            web.requestFocus();
        }
    }

    /** Mode penuh: header+URL menyusut, kartu web melebar rata tepi tanpa border, lalu terkunci. */
    private void collapseHeader(final boolean collapse) {
        if (headerCollapsed == collapse || chromeAnimating) return;
        if (headerHeight <= 0) {
            topChrome.measure(View.MeasureSpec.makeMeasureSpec(topChrome.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            headerHeight = topChrome.getMeasuredHeight();
            if (headerHeight <= 0) return;
        }
        headerCollapsed = collapse;
        chromeAnimating = true;
        webCard.setClipToOutline(true);
        final int hFrom = topChrome.getHeight(), hTo = collapse ? 0 : headerHeight;
        final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) webCard.getLayoutParams();
        final int mFrom = lp.leftMargin, mTo = collapse ? 0 : Ui.dp(this, 12);
        final int tFrom = lp.topMargin, tTo = collapse ? 0 : Ui.dp(this, 10);
        final float rFrom = webBg.getCornerRadius(), rTo = collapse ? 0f : Ui.dp(this, 28);
        final android.animation.ArgbEvaluator argb = new android.animation.ArgbEvaluator();
        final int cFrom = collapse ? webColor() : 0xFF1E1F24, cTo = collapse ? 0xFF1E1F24 : webColor();
        final int pbFrom = content.getPaddingBottom(), pbTo = collapse ? 0 : Ui.dp(this, 62);
        final int nFrom = navBg.getColor() != null ? navBg.getColor().getDefaultColor() : 0xFF0F0F12;
        final int nTo = collapse ? 0xF20F0F12 : 0xFF0F0F12;   // sedikit tembus saat melayang di atas web

        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(collapse ? 300 : 340);
        va.setInterpolator(Ui.EASE);
        va.addUpdateListener(an -> {
            float f = (float) an.getAnimatedValue();
            ViewGroup.LayoutParams hl = topChrome.getLayoutParams();
            hl.height = (int) (hFrom + (hTo - hFrom) * f);
            topChrome.setLayoutParams(hl);
            topChrome.setAlpha(collapse ? 1f - f : f);
            int m = (int) (mFrom + (mTo - mFrom) * f);
            lp.leftMargin = m; lp.rightMargin = m;
            lp.topMargin = (int) (tFrom + (tTo - tFrom) * f);
            webCard.setLayoutParams(lp);
            webBg.setCornerRadius(rFrom + (rTo - rFrom) * f);
            webBg.setColor((Integer) argb.evaluate(f, cFrom, cTo));
            webCard.invalidateOutline();
            content.setPadding(0, 0, 0, (int) (pbFrom + (pbTo - pbFrom) * f));
            navBg.setColor((Integer) argb.evaluate(f, nFrom, nTo));
        });
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                chromeAnimating = false;
                webCard.setClipToOutline(!collapse);
                if (!collapse) {
                    ViewGroup.LayoutParams hl = topChrome.getLayoutParams();
                    hl.height = ViewGroup.LayoutParams.WRAP_CONTENT;   // kembali fleksibel
                    topChrome.setLayoutParams(hl);
                }
            }
        });
        va.start();

        if (collapse) { btnShrink.setAlpha(0f); btnShrink.setVisibility(View.VISIBLE);
            btnShrink.animate().alpha(0.7f).setStartDelay(200).setDuration(220).start(); }
        else { btnShrink.animate().setStartDelay(0).alpha(0f).setDuration(140)
            .withEndAction(() -> btnShrink.setVisibility(View.GONE)).start(); }
    }

    // ------------------------------------------------------------------ Baris sesi (chips)
    private void renderSessions() {
        List<Profile> list = store.all();
        sessionRow.removeAllViews();
        sessionRow.addView(chip(null, false));
        for (Profile p : list) sessionRow.addView(chip(p, p.id.equals(profile.id)));
        navAvatar.bind(profile, true);
        navAvatar.setGapColor(0xFF0F0F12);
        dockAvatar.bind(profile, false);
    }

    private View chip(final Profile p, boolean active) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        int w = Ui.dp(this, 74);
        col.setLayoutParams(new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        col.setClipChildren(false); col.setClipToPadding(false);
        col.setBackgroundResource(R.drawable.bg_row_ripple);

        AvatarView av = new AvatarView(this);
        int s = Ui.dp(this, 60);
        av.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        if (p == null) av.bindPlus(); else av.bind(p, active);
        col.addView(av);

        TextView name = new TextView(this);
        name.setText(p == null ? getString(R.string.new_session) : p.name);
        name.setTextSize(11);
        name.setTextColor(active ? 0xFFF2F2F0 : 0xFFA3A5AC);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 6);
        name.setLayoutParams(lp);
        col.addView(name);

        Ui.pressable(col);
        col.setOnClickListener(v -> {
            Ui.haptic(v);
            if (p == null) promptNewSession();
            else if (p.id.equals(profile.id)) openDevice(p.id);
            else switchTo(p);
        });
        if (p != null) col.setOnLongClickListener(v -> { openDevice(p.id); return true; });
        return col;
    }

    // ------------------------------------------------------------------ Sesi (bottom sheet)
    private void showSessionSheet() {
        final List<Profile> list = store.all();
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_sessions, null);
        final Dialog d = Ui.sheet(this, v);
        ((TextView) v.findViewById(R.id.sheetCount)).setText(list.size() + " sesi · cookie & identitas terpisah");
        LinearLayout box = v.findViewById(R.id.sheetList);
        for (final Profile p : list) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_session, box, false);
            boolean active = p.id.equals(profile.id);
            ((AvatarView) row.findViewById(R.id.avatar)).bind(p, active);
            ((AvatarView) row.findViewById(R.id.avatar)).setGapColor(0xFF2A2B32);
            ((TextView) row.findViewById(R.id.name)).setText(p.name);
            ((TextView) row.findViewById(R.id.device)).setText(p.brand + " " + p.model + " · Android " + p.androidVer);
            row.findViewById(R.id.check).setVisibility(active ? View.VISIBLE : View.GONE);
            row.setOnClickListener(x -> { d.dismiss(); if (active) openDevice(p.id); else switchTo(p); });
            row.findViewById(R.id.btnEdit).setOnClickListener(x -> { d.dismiss(); openDevice(p.id); });
            box.addView(row);
        }
        View btnNew = v.findViewById(R.id.btnNewSession);
        Ui.pressable(btnNew);
        btnNew.setOnClickListener(x -> { d.dismiss(); promptNewSession(); });
        d.show();
    }

    private void promptNewSession() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setText("Sesi " + (store.all().size() + 1));
        et.setSelectAllOnFocus(true);
        FrameLayout wrap = new FrameLayout(this);
        int m = Ui.dp(this, 20);
        wrap.setPadding(m, 0, m, 0);
        wrap.addView(et);
        new AlertDialog.Builder(this)
                .setTitle("Sesi baru")
                .setMessage("Cookie, cache, UA, Client-Hints, IMEI, Android ID, MAC, serial, GSF ID, GAID, dan fingerprint canvas/audio semuanya diacak baru.")
                .setView(wrap)
                .setPositiveButton("Buat & pindah", (d, w) -> {
                    String name = et.getText().toString().trim();
                    switchTo(store.create(name.isEmpty() ? "Sesi" : name));
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void showSettingsSheet() {
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_settings, null);
        final Dialog d = Ui.sheet(this, v);
        final LinearLayout row = v.findViewById(R.id.engineRow);
        for (int i = 0; i < ProfileStore.ENGINES.length; i++) {
            final int idx = i;
            TextView chip = new TextView(this);
            chip.setText(ProfileStore.ENGINES[i][0]);
            chip.setTextSize(14);
            chip.setTypeface(null, android.graphics.Typeface.BOLD);
            chip.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 10));
            chip.setBackgroundResource(R.drawable.bg_chip);
            boolean sel = idx == store.engine();
            chip.setSelected(sel);
            chip.setTextColor(sel ? 0xFF111114 : 0xFFA3A5AC);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(this, 8);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(x -> {
                store.setEngine(idx);
                for (int k = 0; k < row.getChildCount(); k++) {
                    TextView c = (TextView) row.getChildAt(k);
                    c.setSelected(k == idx);
                    c.setTextColor(k == idx ? 0xFF111114 : 0xFFA3A5AC);
                }
                Ui.haptic(x);
            });
            row.addView(chip);
        }
        final Switch sw = v.findViewById(R.id.swDesktop);
        sw.setChecked(store.desktop());
        v.findViewById(R.id.rowDesktop).setOnClickListener(x -> {
            boolean on = !store.desktop();
            store.setDesktop(on);
            sw.setChecked(on);
            Ui.haptic(x);
            applyDesktopMode();
        });
        ((AvatarView) v.findViewById(R.id.settingsAvatar)).bind(profile, true);
        ((AvatarView) v.findViewById(R.id.settingsAvatar)).setGapColor(0xFF2A2B32);
        ((TextView) v.findViewById(R.id.settingsName)).setText(profile.name);
        ((TextView) v.findViewById(R.id.settingsDevice)).setText(profile.brand + " " + profile.model + " · Android " + profile.androidVer + " · Chrome " + profile.chromeMajor());
        v.findViewById(R.id.btnIdentity).setOnClickListener(x -> { d.dismiss(); openDevice(profile.id); });
        Ui.pressable(v.findViewById(R.id.rowDownloads));
        int act = downloads.activeCount(), tot = downloads.all().size();
        ((TextView) v.findViewById(R.id.downloadsSub)).setText(act > 0 ? act + " sedang berjalan" : tot > 0 ? tot + " berkas · folder Download" : "Berkas tersimpan di folder Download");
        v.findViewById(R.id.rowDownloads).setOnClickListener(x -> { Ui.haptic(x); d.dismiss(); showDownloads(); });
        Ui.pressable(v.findViewById(R.id.rowMaxMode));
        v.findViewById(R.id.rowMaxMode).setOnClickListener(x -> { Ui.haptic(x); d.dismiss(); openMaxPopup(); });
        v.findViewById(R.id.rowMaxMode).setOnLongClickListener(x -> { d.dismiss(); newTab(ExtRuntime.HOME, true, null); return true; });
        v.findViewById(R.id.credit).setOnClickListener(x -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/628889841098"))); }
            catch (Exception e) { Toast.makeText(this, "WA 628889841098", Toast.LENGTH_SHORT).show(); }
        });
        d.show();
    }

    private void openDevice(String id) {
        Intent i = new Intent(this, DeviceActivity.class);
        i.putExtra(DeviceActivity.EXTRA_ID, id);
        startActivityForResult(i, REQ_DEVICE);
        overridePendingTransition(R.anim.slide_up, R.anim.fade_out);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FILE) { onFileChosen(res, data); return; }
        if (req != REQ_DEVICE || data == null) return;
        String action = data.getStringExtra(DeviceActivity.RESULT_ACTION);
        String id = data.getStringExtra(DeviceActivity.EXTRA_ID);
        if ("deleted".equals(action)) {
            if (id != null && id.equals(profile.id)) { store.setCurrentId(store.all().get(0).id); restartApp(); }
            else { Toast.makeText(this, "Sesi dihapus", Toast.LENGTH_SHORT).show(); renderSessions(); }
        } else if ("saved".equals(action) && id != null && id.equals(profile.id)) {
            // identitas sesi aktif berubah -> restart supaya Client Hints & script ikut berubah
            restartApp();
        } else if ("switch".equals(action) && id != null && !id.equals(profile.id)) {
            switchTo(store.get(id));
        } else {
            renderSessions(); // nama/identitas sesi lain berubah
        }
    }

    private void switchTo(final Profile p) {
        saveTabs();
        CookieManager.getInstance().flush();
        store.setCurrentId(p.id);
        ((AvatarView) findViewById(R.id.overlayAvatar)).bind(p, true);
        ((TextView) findViewById(R.id.overlayText)).setText("Pindah ke " + p.name);
        switchOverlay.setVisibility(View.VISIBLE);
        switchOverlay.animate().alpha(1f).setDuration(220).setInterpolator(Ui.EASE)
                .withEndAction(() -> switchOverlay.postDelayed(this::restartApp, 250)).start();
    }

    private void restartApp() {
        saveTabs();
        CookieManager.getInstance().flush();
        Intent i = new Intent(this, RestartActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        i.putExtra(RestartActivity.EXTRA_PID, Process.myPid());
        startActivity(i);
        finishAffinity();
        Runtime.getRuntime().exit(0);
    }

    // ------------------------------------------------------------------ lifecycle
    @Override public void onBackPressed() {
        if (popupDialog != null) { closeMaxPopup(); return; }
        if (quickUrl.getVisibility() == View.VISIBLE) { showQuickUrl(false); return; }
        if (urlFocused) { urlBar.clearFocus(); if (web != null) web.requestFocus(); return; }
        if (web != null && web.canGoBack()) web.goBack();
        else if (cur != null && cur.parent != null && tabs.contains(cur.parent)) {   // tab anak: geser balik ke induk lalu tutup
            final Tab child = cur;
            selectTab(child.parent, -1);
            tabs.remove(child);
            for (Tab o : tabs) if (o.parent == child) o.parent = child.parent;
            web.postDelayed(() -> { detach(child); updateTabCount(); saveTabs(); }, 280);
            updateTabCount();
        }
        else if (headerCollapsed) collapseHeader(false);
        else super.onBackPressed();   // tab tetap tersimpan, dipulihkan saat app dibuka lagi
    }
    /** Hasil geo-IP: jika sesi memakai wilayah otomatis dan negara/zona berubah (mis. VPN), simpan & muat ulang
     *  proses supaya zona waktu, bahasa, Accept-Language dan script fingerprint semuanya konsisten. */
    private void onRegionDetected(String cc, String tz, String ip) {
        if (profile == null || !profile.autoRegion) return;
        String[] r = Regions.fromGeo(cc, tz);
        if (r[0].isEmpty() || r[0].equals(profile.region)) return;
        profile.region = r[0];
        boolean listed = Regions.isListed(r[0]);
        profile.rName = listed ? "" : r[1]; profile.rTz = listed ? "" : r[2]; profile.rLoc = listed ? "" : r[3]; profile.rAl = listed ? "" : r[4];
        store.update(profile);
        Toast.makeText(this, "Lokasi terdeteksi: " + r[1] + " — sesi menyesuaikan…", Toast.LENGTH_LONG).show();
        web.postDelayed(this::restartApp, 1200);
    }

    /** Sekali saja: izin notifikasi (Android 13+) untuk foreground service, lalu minta pengecualian
     *  penghematan baterai supaya sistem tidak mematikan proses saat lama di latar belakang. */
    private void requestKeepAlivePerms() {
        try {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 41);
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            android.content.SharedPreferences sp = getSharedPreferences("keepalive", MODE_PRIVATE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName()) && !sp.getBoolean("asked", false)) {
                sp.edit().putBoolean("asked", true).apply();
                Intent i = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()));
                startActivity(i);
            }
        } catch (Throwable ignored) {}
    }

    @Override protected void onPause() { super.onPause(); saveTabs(); CookieManager.getInstance().flush(); KeepAliveService.start(this); }
    @Override protected void onResume() { super.onResume(); KeepAliveService.stop(this); if (regionDetector != null) regionDetector.checkIfStale(); }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); saveTabs(); }
    @Override protected void onDestroy() { if (dlReceiver != null) { try { unregisterReceiver(dlReceiver); } catch (Exception ignored) {} dlReceiver = null; }
        if (fileCallback != null) { try { fileCallback.onReceiveValue(null); } catch (Exception ignored) {} fileCallback = null; }
        saveTabs(); CookieManager.getInstance().flush(); KeepAliveService.stop(this); if (regionDetector != null) regionDetector.stop(); for (Tab t : tabs) t.destroy(); tabs.clear();
        if (rt != null) rt.destroy(); closeMaxPopup(); if (bgWeb != null) { try { bgWeb.destroy(); } catch (Throwable ignored) {} bgWeb = null; }
        super.onDestroy(); }

    // ================================================================== Introvert Dreams MAX MODE
    /** Nama objek jembatan ekstensi di halaman (acak per proses; disembunyikan lagi di luar Dola/Seaart, lihat maxModeGuardScript). */
    private static final String MAX_BRIDGE = "_w" + Long.toHexString(Double.doubleToLongBits(Math.random())).substring(1, 8);
    private static final java.util.regex.Pattern MAX_HOST = java.util.regex.Pattern.compile("(^|\\.)(dola\\.com|seaart\\.ai)$", java.util.regex.Pattern.CASE_INSENSITIVE);
    private boolean maxModeDocStart = false;
    private String maxModeGuardCache;

    static boolean isMaxModeHost(String url) {
        if (url == null) return false;
        try { String h = Uri.parse(url).getHost(); return h != null && MAX_HOST.matcher(h).find(); } catch (Exception e) { return false; }
    }

    /** Runtime ekstensi + WebView latar (background.js) + tombol melayang. Dipanggil sebelum tab pertama dibuat. */
    private void setupMaxMode() {
        rt = new ExtRuntime(this);
        rt.userAgent = profile.userAgent(store.desktop());
        rt.hooks = new ExtRuntime.UiHooks() {
            public void openUrl(String url) {
                closeMaxPopup();
                if (web != null && (isMaxModeHost(currentUrl) || currentUrl == null || currentUrl.isEmpty())) web.loadUrl(url);
                else newTab(url, true, null);
            }
            public void openPopup() { openMaxPopup(); }
            public void closePopup() { closeMaxPopup(); }
            public void reloadTab() { if (web != null) web.reload(); }
        };

        bgWeb = new WebView(this);
        setupExtWebView(bgWeb);
        rt.bg = bgWeb;
        bgWeb.addJavascriptInterface(new Bridge(rt, "background"), "__WhempyBridge");
        bgWeb.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(android.webkit.ConsoleMessage cm) { rt.logConsole("background", cm.messageLevel().name(), cm.message(), cm.sourceId(), cm.lineNumber()); return true; }
        });
        ((FrameLayout) findViewById(R.id.root)).addView(bgWeb, 0, new FrameLayout.LayoutParams(1, 1));

        maxFab = new ImageView(this);
        maxFab.setImageResource(R.drawable.ic_maxmode);
        maxFab.setScaleType(ImageView.ScaleType.FIT_CENTER);
        maxFab.setElevation(Ui.dp(this, 6));
        maxFab.setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        android.graphics.drawable.GradientDrawable fabBg = new android.graphics.drawable.GradientDrawable();
        fabBg.setColor(0xFF17181C); fabBg.setCornerRadius(Ui.dp(this, 16));
        maxFab.setBackground(fabBg);
        maxFab.setClipToOutline(true);
        maxFab.setContentDescription("MAX MODE");
        int size = Ui.dp(this, 48), m = Ui.dp(this, 12);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size, android.view.Gravity.BOTTOM | android.view.Gravity.END);
        lp.setMargins(0, 0, m, Ui.dp(this, 16));
        maxFab.setLayoutParams(lp);
        maxFab.setVisibility(View.GONE);
        Ui.pressable(maxFab);
        maxFab.setOnClickListener(v -> { Ui.haptic(v); openMaxPopup(); });
        maxFab.setOnLongClickListener(v -> { Ui.haptic(v); if (web != null) web.reload(); Toast.makeText(this, "Memuat ulang…", Toast.LENGTH_SHORT).show(); return true; });
        ((FrameLayout) findViewById(R.id.webCard)).addView(maxFab);

        maxModeDocStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);
        rt.docStartSupported = maxModeDocStart;
        rt.logNative("main", "docStartInjection=" + maxModeDocStart + " webview=" + store.realChromeVersion());
    }

    /** Tombol MAX MODE hanya tampil saat tab aktif berada di Dola/Seaart. */
    private void updateMaxFab() {
        if (maxFab == null) return;
        boolean show = isMaxModeHost(currentUrl);
        if (show && maxFab.getVisibility() != View.VISIBLE) { maxFab.setAlpha(0f); maxFab.setScaleX(0.6f); maxFab.setScaleY(0.6f); maxFab.setVisibility(View.VISIBLE); maxFab.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(Ui.POP).start(); }
        else if (!show && maxFab.getVisibility() == View.VISIBLE) { maxFab.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(150).withEndAction(() -> maxFab.setVisibility(View.GONE)).start(); }
        if (show) maxFab.bringToFront();
    }

    /** Pengaturan WebView lokal (background & popup ekstensi): file:// boleh akses universal. */
    private void setupExtWebView(WebView w) {
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        w.setBackgroundColor(0xFF1E1F24);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(w, true);
    }

    private String readAsset(String path) {
        try {
            InputStream in = getAssets().open(path);
            ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] b = new byte[16384]; int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n); in.close();
            return bo.toString("UTF-8");
        } catch (Exception e) { android.util.Log.e("MaxMode", "asset missing: " + path, e); return ""; }
    }

    /** Skrip ringan untuk SEMUA origin: sembunyikan jembatan di luar Dola/Seaart + autofill login Google. */
    private String maxModeGuardScript() {
        if (maxModeGuardCache != null) return maxModeGuardCache;
        StringBuilder sb = new StringBuilder();
        sb.append("(function(){try{var N=").append(JSONObject.quote(MAX_BRIDGE)).append(";")
          .append("if(!/(^|\\.)(dola\\.com|seaart\\.ai)$/i.test(location.hostname)){try{delete window[N];}catch(e){}")
          .append("if(N in window){try{Object.defineProperty(window,N,{value:undefined,enumerable:false,configurable:true});}catch(e){}}}}catch(e){}})();\n");
        sb.append("if (window === window.top && /(^|\\.)(google\\.com|dola\\.com|seaart\\.ai)$/i.test(location.hostname)) { try {\n").append(readAsset("google-autofill.js")).append("\n} catch (e) { console.error('[Whempy] autofill failed', e); } }\n");
        maxModeGuardCache = sb.toString();
        return maxModeGuardCache;
    }

    /** Bundle penuh MAX MODE (chrome-shim + content script MAIN/ISOLATED + CSS) — hanya untuk Dola/Seaart, top frame. */
    private String maxModeScript() {
        if (maxModeScriptCache != null) return maxModeScriptCache;
        StringBuilder sb = new StringBuilder();
        sb.append("if (window === window.top && /(^|\\.)(dola\\.com|seaart\\.ai)$/i.test(location.hostname) && !window.__whempyInjected) { window.__whempyInjected = true;\n");
        String bridgeRef = "window[" + JSONObject.quote(MAX_BRIDGE) + "]";
        sb.append(readAsset("chrome-shim.js").replace("__CTX__", "page").replace("window.__WhempyBridge", bridgeRef)).append("\n;\n");
        sb.append("try { delete ").append(bridgeRef).append("; } catch (e) {}\n");
        for (String f : readAsset("page-scripts.txt").split("\n")) {
            f = f.trim(); if (f.isEmpty()) continue;
            sb.append("try {\n").append(readAsset(f)).append("\n} catch (e) { console.error('[Whempy] ").append(f).append(" failed', e); }\n;\n");
        }
        String css = readAsset("ext/dock-polish.css");
        sb.append("(function(){ function add(){ if (document.getElementById('__whempy_css')) return; var s=document.createElement('style'); s.id='__whempy_css'; s.textContent=")
          .append(JSONObject.quote(css))
          .append("; (document.head||document.documentElement).appendChild(s);} if (document.head) add(); else document.addEventListener('DOMContentLoaded', add, {once:true}); })();\n");
        sb.append("}\n");
        maxModeScriptCache = sb.toString();
        return maxModeScriptCache;
    }

    /** Daftarkan skrip MAX MODE di document_start (setelah spoof.js yang sudah terpasang di applyAntiDetection). */
    private void installMaxModeScript(WebView wv) {
        if (!maxModeDocStart) return;
        try {
            Object old = wv.getTag(R.id.tag_maxmode);
            if (old instanceof androidx.webkit.ScriptHandler[]) for (androidx.webkit.ScriptHandler h : (androidx.webkit.ScriptHandler[]) old) { try { h.remove(); } catch (Throwable ignored) {} }
            java.util.Set<String> origins = new java.util.HashSet<>(java.util.Arrays.asList(
                "https://dola.com", "https://*.dola.com", "https://seaart.ai", "https://*.seaart.ai",
                "http://dola.com", "http://*.dola.com", "http://seaart.ai", "http://*.seaart.ai"));
            wv.setTag(R.id.tag_maxmode, new androidx.webkit.ScriptHandler[]{
                WebViewCompat.addDocumentStartJavaScript(wv, maxModeGuardScript(), Collections.singleton("*")),
                WebViewCompat.addDocumentStartJavaScript(wv, maxModeScript(), origins)});
        } catch (Throwable e) {
            android.util.Log.w("MaxMode", "addDocumentStartJavaScript failed, falling back", e);
            rt.logNative("main", "addDocumentStartJavaScript failed: " + e);
            maxModeDocStart = false; rt.docStartSupported = false;
        }
    }

    // ------------------------------------------------------------------ popup ekstensi (popup.html)
    private void openMaxPopup() {
        if (popupDialog != null) { popupDialog.show(); return; }
        popupWeb = new WebView(this);
        setupExtWebView(popupWeb);
        popupWeb.addJavascriptInterface(new Bridge(rt, "popup"), "__WhempyBridge");
        popupWeb.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(android.webkit.ConsoleMessage cm) { rt.logConsole("popup", cm.messageLevel().name(), cm.message(), cm.sourceId(), cm.lineNumber()); return true; }
        });
        popupWeb.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("file://")) return false;
                closeMaxPopup();
                if (url.startsWith("http://") || url.startsWith("https://")) { if (web != null) web.loadUrl(url); }
                else try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {}
                return true;
            }
        });
        rt.popup = popupWeb;
        FrameLayout wrap = new FrameLayout(this);
        wrap.setBackgroundColor(0xFF1E1F24);
        wrap.addView(popupWeb, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View close = LayoutInflater.from(this).inflate(R.layout.view_popup_close, wrap, false);
        Ui.pressable(close);
        close.setOnClickListener(v -> { Ui.haptic(v); closeMaxPopup(); });
        wrap.addView(close);
        popupDialog = new Dialog(this, R.style.TabsTheme);
        popupDialog.setContentView(wrap, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        android.view.Window win = popupDialog.getWindow();
        if (win != null) win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        popupDialog.setOnDismissListener(d -> {
            rt.popup = null;
            if (popupWeb != null) { try { popupWeb.destroy(); } catch (Throwable ignored) {} popupWeb = null; }
            popupDialog = null;
        });
        popupDialog.show();
        popupWeb.loadUrl("file:///android_asset/ext/popup.html?mode=popup");
    }
    private void closeMaxPopup() { if (popupDialog != null) popupDialog.dismiss(); }
}
