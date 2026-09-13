package id.kuli.sesibrowser;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.os.Build;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

/** Helper kecil untuk gaya visual & animasi yang konsisten. */
final class Ui {
    static final PathInterpolator EASE = new PathInterpolator(0.2f, 0f, 0f, 1f);
    /** Emphasized-decelerate (Material 3) — untuk elemen yang "mendarat". */
    static final PathInterpolator LAND = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    /** Cepat di awal, mendarat halus — untuk transisi konten (tab, kartu). */
    static final PathInterpolator SNAP = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    /** Sedikit "memantul" untuk elemen kecil (badge, tombol). */
    static final android.view.animation.OvershootInterpolator POP = new android.view.animation.OvershootInterpolator(2.2f);
    private static final int TAG_FADE = 0x7f0f0001;
    private Ui() {}

    static int dp(Context c, float v) { return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f); }

    /** Judul: tinta hitam pekat (flat, tanpa gradien). */
    static void gradientText(final TextView tv) { tv.getPaint().setShader(null); tv.setTextColor(0xFFF2F2F0); }

    /** Dialog bottom-sheet dengan sudut membulat dan animasi slide. */
    static Dialog sheet(Activity a, View content) {
        Dialog d = new Dialog(a, R.style.SheetTheme);
        d.setContentView(content);
        Window w = d.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setNavigationBarColor(0xFF1E1F24);
            if (Build.VERSION.SDK_INT >= 28) w.getAttributes().layoutInDisplayCutoutMode
                    = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        return d;
    }

    /** Efek tekan: mengecil sedikit lalu kembali (feel "smooth"). */
    static void pressable(final View v) {
        v.setOnTouchListener((view, ev) -> {
            switch (ev.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.9f).scaleY(0.9f).alpha(0.85f).setDuration(110).setInterpolator(EASE).start(); break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(260).setInterpolator(POP).start(); break;
            }
            return false;
        });
    }

    static void haptic(View v) { v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); }

    /** Animasi tinggi view (collapse / expand) tanpa jank. */
    static void animateHeight(final View v, int to, int duration) {
        int from = v.getHeight();
        if (from == to) return;
        ValueAnimator va = ValueAnimator.ofInt(from, to);
        va.setDuration(duration);
        va.setInterpolator(EASE);
        va.addUpdateListener(an -> {
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            lp.height = (Integer) an.getAnimatedValue();
            v.setLayoutParams(lp);
        });
        va.start();
    }

    /** Token per-view: fadeIn yang datang saat fadeOut masih berjalan membatalkan aksi GONE-nya. */
    private static int nextFadeToken(View v) {
        Object o = v.getTag(TAG_FADE);
        int t = (o instanceof Integer ? (Integer) o : 0) + 1;
        v.setTag(TAG_FADE, t);
        return t;
    }

    static void fadeIn(View v, int dur) {
        nextFadeToken(v);
        if (v.getVisibility() == View.VISIBLE && v.getAlpha() == 1f) return;
        v.animate().cancel();
        v.setVisibility(View.VISIBLE);
        v.animate().alpha(1f).setDuration(dur).setInterpolator(EASE).withEndAction(null).start();
    }

    static void fadeOut(final View v, int dur, final boolean gone) {
        final int token = nextFadeToken(v);
        v.animate().cancel();
        v.animate().alpha(0f).setDuration(dur).setInterpolator(EASE)
                .withEndAction(() -> {
                    Object o = v.getTag(TAG_FADE);
                    if (gone && o instanceof Integer && (Integer) o == token) v.setVisibility(View.GONE);
                }).start();
    }

    static void showKeyboard(final View v) {
        v.post(() -> {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                    v.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(v, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        });
    }
}
