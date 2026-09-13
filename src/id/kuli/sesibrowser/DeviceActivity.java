package id.kuli.sesibrowser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedHashMap;
import java.util.Map;

/** Halaman identitas perangkat per sesi (gaya "Device-01": Restore / Reset). */
public class DeviceActivity extends Activity {
    public static final String EXTRA_ID = "id";
    public static final String RESULT_ACTION = "action";

    private ProfileStore store;
    private Profile saved;      // yang tersimpan (untuk Restore)
    private Profile draft;      // yang sedang diedit
    private final Map<String, EditText> inputs = new LinkedHashMap<>();
    private TextView title, subtitle;
    private AvatarView heroAvatar;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_device);
        store = new ProfileStore(this);
        saved = store.get(getIntent().getStringExtra(EXTRA_ID));
        if (saved == null) { finish(); return; }
        draft = saved.copy();

        title = findViewById(R.id.title);
        subtitle = findViewById(R.id.subtitle);
        heroAvatar = findViewById(R.id.heroAvatar);
        Ui.gradientText(title);
        View close = findViewById(R.id.btnClose), restore = findViewById(R.id.btnRestore), reset = findViewById(R.id.btnReset);
        View save = findViewById(R.id.btnSave), del = findViewById(R.id.btnDelete);
        for (View v : new View[]{close, restore, reset, save, del, heroAvatar}) Ui.pressable(v);

        close.setOnClickListener(v -> finish());
        restore.setOnClickListener(v -> { Ui.haptic(v); draft = saved.copy(); render(); toast("Dikembalikan ke nilai tersimpan"); });
        reset.setOnClickListener(v -> {
            Ui.haptic(v);
            if (!collect()) return;
            String name = draft.name;
            IdentityFactory.randomizeAll(draft, store.realChromeVersion());
            draft.name = name;
            v.animate().rotationBy(360f).setDuration(500).setInterpolator(Ui.EASE).start();
            render(); toast("Identitas baru dibuat — tekan Simpan untuk menerapkan");
        });
        save.setOnClickListener(v -> { Ui.haptic(v); save(); });
        del.setOnClickListener(v -> confirmDelete());
        title.setOnClickListener(v -> rename());
        heroAvatar.setOnClickListener(v -> rename());
        render();
    }

    private void render() {
        title.setText(draft.name);
        subtitle.setText(draft.brand + " " + draft.model + " · Android " + draft.androidVer);
        heroAvatar.bind(draft, true);
        LinearLayout box = findViewById(R.id.fields);
        box.removeAllViews();
        inputs.clear();

        LinearLayout c;
        c = card(box, "Perangkat");
        field(c, "name", "Nama sesi", draft.name, false);
        field(c, "brand", "Brand", draft.brand, false);
        // avatar hero ikut berubah saat brand diketik (Samsung → logo Samsung, dst)
        inputs.get("brand").addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence cs, int a, int b, int d) {}
            public void onTextChanged(CharSequence cs, int a, int b, int d) {}
            public void afterTextChanged(android.text.Editable e) {
                Profile tmp = draft.copy(); tmp.brand = e.toString().trim(); heroAvatar.bind(tmp, true);
            }
        });
        field(c, "model", "Model (Sec-CH-UA-Model)", draft.model, false);
        field(c, "androidVer", "Android Version (Sec-CH-UA-Platform-Version)", draft.androidVer, false);

        c = card(box, "Identitas");
        field(c, "deviceId", "DeviceId (IMEI)", draft.deviceId, false);
        field(c, "androidId", "Android Id", draft.androidId, false);
        field(c, "serial", "Serial", draft.serial, false);
        field(c, "wifiMac", "Wifi Mac", draft.wifiMac, false);
        field(c, "btMac", "Bluetooth Mac", draft.btMac, false);
        field(c, "gsfId", "GSF Id", draft.gsfId, false);
        field(c, "advertisingId", "Advertising Id (GAID)", draft.advertisingId, false);

        c = card(box, "Wilayah (mengikuti negara VPN)");
        field(c, "autoRegion", "Deteksi otomatis dari IP / VPN", draft.autoRegion ? "Aktif — sesi menyesuaikan saat VPN berubah" : "Nonaktif — pilih manual di bawah", false);
        EditText ar = inputs.get("autoRegion");
        ar.setFocusable(false); ar.setClickable(true); ar.setCursorVisible(false);
        ar.setOnClickListener(v -> { draft.autoRegion = !draft.autoRegion; Ui.haptic(v); render(); });
        field(c, "region", "Zona waktu & bahasa", regionText(draft.region), false);
        EditText rg = inputs.get("region");
        rg.setFocusable(false); rg.setClickable(true); rg.setCursorVisible(false);
        rg.setAlpha(draft.autoRegion ? 0.6f : 1f);
        rg.setOnClickListener(v -> { if (draft.autoRegion) toast("Matikan deteksi otomatis untuk memilih manual"); else pickRegion(); });
        rg.setOnLongClickListener(v -> { if (!draft.autoRegion) pickRegion(); return true; });

        c = card(box, "Fingerprint");
        field(c, "cores", "CPU Cores (hardwareConcurrency)", String.valueOf(draft.cores), true);
        field(c, "memGb", "RAM GB (deviceMemory)", String.valueOf(draft.memGb), true);
        field(c, "seed", "Fingerprint Seed (canvas/audio)", String.valueOf(draft.seed), true);

        c = card(box, "Browser (otomatis, mengikuti engine)");
        field(c, "chromeFull", "Chrome / WebView", draft.chromeFull, false);
        field(c, "ua", "User-Agent", draft.userAgent(), false);
        inputs.get("chromeFull").setEnabled(false);
        inputs.get("ua").setEnabled(false);
        inputs.get("chromeFull").setAlpha(0.6f);
        inputs.get("ua").setAlpha(0.6f);
    }

    /** Kartu gelap membulat berisi beberapa field, dengan judul kecil di atasnya. */
    private LinearLayout card(LinearLayout parent, String heading) {
        TextView h = new TextView(this);
        h.setText(heading);
        h.setTextSize(12);
        h.setAllCaps(true);
        h.setLetterSpacing(0.08f);
        h.setTextColor(0xFFA3A5AC);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(18); hlp.leftMargin = dp(8); hlp.bottomMargin = dp(8);
        h.setLayoutParams(hlp);
        parent.addView(h);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(16), dp(6), dp(16), dp(6));
        card.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        parent.addView(card);
        return card;
    }

    private void field(LinearLayout parent, String key, String label, String value, boolean numeric) {
        if (parent.getChildCount() > 0) {
            View div = new View(this);
            div.setBackgroundColor(0xFF3A3B44);
            parent.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, dp(10), 0, dp(10));

        TextView tv = new TextView(this);
        tv.setText(label); tv.setTextColor(0xFF6F717A); tv.setTextSize(12);
        wrap.addView(tv);

        EditText et = new EditText(this);
        et.setText(value);
        et.setTextColor(0xFFF2F2F0);
        et.setTextSize(16);
        et.setSingleLine(true);
        et.setBackground(null);
        et.setPadding(0, dp(4), 0, dp(2));
        et.setInputType(numeric ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        et.setOnLongClickListener(v -> {
            ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText(key, et.getText().toString()));
            toast(label + " disalin"); return true;
        });
        wrap.addView(et);
        inputs.put(key, et);
        parent.addView(wrap);
    }

    private String regionText(String code) {
        if (code == null || code.isEmpty()) return "Ikuti perangkat (" + java.util.TimeZone.getDefault().getID() + ")";
        return Regions.label(code) + " · " + Regions.tz(code) + " · " + Regions.locale(code);
    }

    private void pickRegion() {
        String[] names = new String[Regions.LIST.length];
        for (int i = 0; i < names.length; i++) names[i] = Regions.LIST[i][1] + (i == 0 ? "" : "  (" + Regions.LIST[i][2] + ")");
        new AlertDialog.Builder(this).setTitle("Wilayah sesi")
            .setSingleChoiceItems(names, Regions.indexOf(draft.region), (d, w) -> {
                draft.region = Regions.LIST[w][0];
                inputs.get("region").setText(regionText(draft.region));
                d.dismiss();
            }).show();
    }

    private String val(String k) { return inputs.get(k).getText().toString().trim(); }

    private boolean collect() {
        try {
            draft.name = val("name").isEmpty() ? draft.name : val("name");
            draft.deviceId = val("deviceId"); draft.androidId = val("androidId");
            draft.btMac = val("btMac"); draft.wifiMac = val("wifiMac"); draft.serial = val("serial");
            draft.gsfId = val("gsfId"); draft.advertisingId = val("advertisingId");
            draft.brand = val("brand"); draft.model = val("model"); draft.androidVer = val("androidVer");
            draft.cores = Integer.parseInt(val("cores")); draft.memGb = Integer.parseInt(val("memGb"));
            draft.seed = Long.parseLong(val("seed"));
            return true;
        } catch (Exception e) { toast("Nilai angka tidak valid"); return false; }
    }

    private void save() {
        if (!collect()) return;
        store.update(draft);
        saved = draft.copy();
        boolean isCurrent = draft.id.equals(store.currentId());
        Intent r = new Intent().putExtra(EXTRA_ID, draft.id).putExtra(RESULT_ACTION, "saved");
        setResult(RESULT_OK, r);
        toast(isCurrent ? "Tersimpan — browser dimuat ulang dengan identitas baru" : "Tersimpan");
        finish();
    }

    private void rename() {
        final EditText et = new EditText(this);
        et.setText(draft.name);
        et.setSelectAllOnFocus(true);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(20), 0, dp(20), 0);
        wrap.addView(et);
        new AlertDialog.Builder(this).setTitle("Nama sesi").setView(wrap)
                .setPositiveButton("OK", (d, w) -> { draft.name = et.getText().toString().trim(); render(); })
                .setNegativeButton("Batal", null).show();
    }

    private void confirmDelete() {
        if (store.all().size() <= 1) { toast("Tidak bisa menghapus sesi terakhir"); return; }
        new AlertDialog.Builder(this)
                .setTitle("Hapus \"" + saved.name + "\"?")
                .setMessage("Semua cookie, login, cache, dan identitas sesi ini dihapus permanen.")
                .setPositiveButton("Hapus", (d, w) -> {
                    store.delete(saved.id);
                    setResult(RESULT_OK, new Intent().putExtra(EXTRA_ID, saved.id).putExtra(RESULT_ACTION, "deleted"));
                    finish();
                })
                .setNegativeButton("Batal", null).show();
    }

    @Override public void finish() { super.finish(); overridePendingTransition(R.anim.fade_in, R.anim.slide_down); }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
