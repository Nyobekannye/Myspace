package id.kuli.sesibrowser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/**
 * Avatar bulat ala "story ring": lingkaran gradien (warna deterministik dari seed sesi),
 * inisial di tengah, dan ring gradien ungu→pink→oranye jika sesi aktif.
 */
public class AvatarView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gap = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private String initials = "";
    private long seed = 1;
    private boolean active = false;
    private boolean plus = false;
    private int c1 = 0xFFCFC6E6, c2 = 0xFFCFC6E6;
    private BrandMark brand = null;
    private final Paint logo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float angle = -90f;
    private SweepGradient sweep;
    private android.animation.ValueAnimator spin;

    private void syncAnim() {
        boolean want = active && isAttachedToWindow() && getVisibility() == VISIBLE;
        if (want && spin == null) {
            spin = android.animation.ValueAnimator.ofFloat(0f, 360f);
            spin.setDuration(3200); spin.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            spin.setInterpolator(new android.view.animation.LinearInterpolator());
            spin.addUpdateListener(a -> { angle = (float) a.getAnimatedValue(); postInvalidateOnAnimation(); });
            spin.start();
        } else if (!want && spin != null) { spin.cancel(); spin = null; }
    }
    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { super.onSizeChanged(w, h, ow, oh); sweep = null; }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); syncAnim(); }
    @Override protected void onDetachedFromWindow() { syncAnim(); if (spin != null) { spin.cancel(); spin = null; } super.onDetachedFromWindow(); }
    @Override protected void onVisibilityChanged(View v, int vis) { super.onVisibilityChanged(v, vis); syncAnim(); }

    public AvatarView(Context c) { super(c); init(); }
    public AvatarView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        ring.setStyle(Paint.Style.STROKE);
        gap.setColor(0xFF1E1F24);
        text.setColor(0xFF161616);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
    }

    public void bind(Profile p, boolean isActive) {
        plus = false;
        active = isActive;
        seed = p.seed;
        initials = initialsOf(p.name);
        int[] pal = palette(seed);
        c1 = pal[0]; c2 = pal[1];
        brand = BrandMark.of(p.brand);
        syncAnim();
        invalidate();
    }

    /** Mode "+ Baru": lingkaran gelap dengan tanda plus. */
    public void bindPlus() {
        plus = true; active = false; initials = "+"; brand = null;
        c1 = 0xFF34353D; c2 = 0xFF34353D;
        syncAnim();
        invalidate();
    }

    public void setGapColor(int color) { gap.setColor(color); invalidate(); }

    static String initialsOf(String name) {
        if (name == null) return "";
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String s : parts) { if (!s.isEmpty()) sb.append(Character.toUpperCase(s.charAt(0))); if (sb.length() == 2) break; }
        return sb.toString();
    }

    static int[] palette(long seed) {
        int[][] pals = {   // pastel flat, inisial gelap
            {0xFFCFC6E6, 0xFFCFC6E6}, {0xFFE7B4CB, 0xFFE7B4CB}, {0xFFF0D3A8, 0xFFF0D3A8},
            {0xFFB9D3C2, 0xFFB9D3C2}, {0xFFB7C9E2, 0xFFB7C9E2}, {0xFFE8C1B0, 0xFFE8C1B0},
            {0xFFD6D0A8, 0xFFD6D0A8}, {0xFFC9CDD6, 0xFFC9CDD6}
        };
        return pals[(int) Math.floorMod(seed, (long) pals.length)];
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) / 2f;
        float ringW = Math.max(2f, r * 0.09f);
        float gapW = Math.max(2f, r * 0.08f);

        if (active) {
            // Ring sesi aktif: lingkaran redup + "komet" (busur dengan ekor memudar) yang berputar mulus.
            // Digambar dengan SweepGradient penuh 360° lalu canvas.rotate — tidak ada ujung terpotong/patah.
            ring.setStrokeWidth(ringW);
            ring.setStrokeCap(Paint.Cap.BUTT);
            float inset = ringW / 2f + 1f;
            rect.set(inset, inset, w - inset, h - inset);
            ring.setShader(null);
            ring.setColor(0x33F2F2F0);
            c.drawOval(rect, ring);
            if (sweep == null) {
                // 0° = ekor transparan → 300° = kepala penuh → kembali transparan (celah kecil)
                sweep = new SweepGradient(cx, cy,
                        new int[]{0x00F2F2F0, 0x00F2F2F0, 0x40F2F2F0, 0xFFF2F2F0, 0xFFF2F2F0, 0x00F2F2F0},
                        new float[]{0f, 0.35f, 0.55f, 0.90f, 0.97f, 1f});
            }
            ring.setShader(sweep);
            c.save();
            c.rotate(angle, cx, cy);
            c.drawOval(rect, ring);
            c.restore();
            ring.setShader(null);
            c.drawCircle(cx, cy, r - ringW - 1.5f, gap);
            r = r - ringW - gapW;
        }

        if (brand != null && !plus) {
            // Avatar brand: lingkaran warna brand + logo/wordmark minimalis
            fill.setShader(null);
            fill.setColor(brand.bg);
            c.drawCircle(cx, cy, r, fill);
            // outline halus supaya cakram tidak menyatu dengan latar arang
            logo.reset(); logo.setAntiAlias(true); logo.setStyle(Paint.Style.STROKE);
            logo.setStrokeWidth(1.5f); logo.setColor(0x2EFFFFFF);
            c.drawCircle(cx, cy, r - 0.75f, logo);
            brand.draw(c, getContext(), cx, cy, r, logo);
            return;
        }

        fill.setShader(null);
        fill.setColor(c1);
        c.drawCircle(cx, cy, r, fill);

        text.setTextSize(plus ? r * 1.1f : (initials.length() > 1 ? r * 0.72f : r * 0.9f));
        text.setColor(plus ? 0xFFA3A5AC : 0xFF161616);
        Paint.FontMetrics fm = text.getFontMetrics();
        float ty = cy - (fm.ascent + fm.descent) / 2f;
        c.drawText(initials, cx, ty, text);
    }
}
