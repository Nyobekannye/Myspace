package id.kuli.sesibrowser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.Locale;

/**
 * Logo brand untuk avatar sesi — satu bahasa desain untuk semua brand:
 * cakram arang lembut, glif monokrom putih susu, garis tunggal berujung bulat (smooth),
 * ditambah wordmark kecil seragam di bawahnya. Tidak ada warna brand yang mencolok.
 * Brand tak dikenal → null → AvatarView memakai pastel + inisial.
 */
final class BrandMark {
    static final int DISC = 0xFF34353D;   // latar cakram (surface2)
    static final int INK  = 0xFFF4F1EA;   // putih susu

    final int bg = DISC;
    final String word;   // wordmark seragam
    final int glyph;     // bentuk monogram

    private static final Typeface FACE = Typeface.create("sans-serif-medium", Typeface.NORMAL);


    // glif — bentuk khas tiap brand, disederhanakan ke garis tunggal monokrom
    private static final int G_GOOGLE = 1, G_MI = 2, G_SQ_LETTER = 3, G_OVAL = 4, G_O = 5, G_V = 6, G_X = 7,
            G_TRI_T = 8, G_M_RING = 9, G_A = 10, G_ONEPLUS = 11, G_FLOWER = 12, G_LG = 13, G_LETTER = 14;

    final String letter;   // huruf pendamping glif (opsional)
    private BrandMark(String word, int glyph, String letter) { this.word = word; this.glyph = glyph; this.letter = letter; }

    static BrandMark of(String brand) {
        if (brand == null) return null;
        switch (brand.trim().toLowerCase(Locale.ROOT)) {
            case "google":   return new BrandMark("Google",  G_GOOGLE,    null);   // huruf G Google
            case "xiaomi":   return new BrandMark("Xiaomi",  G_MI,        "mi");   // kotak membulat + "mi"
            case "redmi":    return new BrandMark("Redmi",   G_SQ_LETTER, "R");
            case "poco":     return new BrandMark("POCO",    G_SQ_LETTER, "P");    // kotak + P
            case "samsung":  return new BrandMark("Samsung", G_OVAL,      null);   // elips miring
            case "oppo":     return new BrandMark("OPPO",    G_O,         null);   // huruf O bundar
            case "vivo":     return new BrandMark("vivo",    G_V,         null);   // huruf v membulat
            case "realme":   return new BrandMark("realme",  G_SQ_LETTER, "r");
            case "infinix":  return new BrandMark("Infinix", G_X,         null);   // simbol X
            case "tecno":    return new BrandMark("TECNO",   G_TRI_T,     null);   // segitiga T
            case "motorola": return new BrandMark("moto",    G_M_RING,    null);   // M dalam lingkaran (batwing)
            case "asus":     return new BrandMark("ASUS",    G_A,         null);   // A tanpa palang
            case "oneplus":  return new BrandMark("OnePlus", G_ONEPLUS,   null);   // kotak + "1+"
            case "huawei":   return new BrandMark("Huawei",  G_FLOWER,    null);   // kelopak bunga
            case "honor":    return new BrandMark("HONOR",   G_LETTER,    "H");
            case "sony":     return new BrandMark("SONY",    G_LETTER,    "S");
            case "nokia":    return new BrandMark("NOKIA",   G_LETTER,    "N");
            case "lg":       return new BrandMark("LG",      G_LG,        null);   // wajah LG
            default: return null;
        }
    }

    /** Huruf di dalam bentuk: fill, tinggi ≤ maxH, lebar ≤ maxW. */
    private static void glyphText(Canvas c, String s, float cx, float cy, float maxW, float maxH, Paint p, Typeface tf) {
        Paint.Style st = p.getStyle();
        p.setStyle(Paint.Style.FILL); p.setTypeface(tf); p.setTextAlign(Paint.Align.CENTER); p.setLetterSpacing(-0.02f);
        p.setTextSize(100f);
        float w = p.measureText(s);
        p.setTextSize(Math.min(maxH, 100f * maxW / Math.max(1f, w)));
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, p);
        p.setStyle(st); p.setLetterSpacing(0f);
    }

    /** Gambar ke dalam lingkaran (cx,cy,r). Cakram latar sudah digambar caller. */
    void draw(Canvas c, Context ctx, float cx, float cy, float r, Paint p) {
        p.reset(); p.setAntiAlias(true); p.setColor(INK);
        p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
        float sw = Math.max(1.6f, r * 0.11f);
        p.setStrokeWidth(sw);
        float gy = cy - r * 0.16f, gr = r * 0.30f;
        p.setStyle(Paint.Style.STROKE);
        RectF b = new RectF(cx - gr, gy - gr, cx + gr, gy + gr);
        Path path = new Path();
        switch (glyph) {
            case G_GOOGLE:
                c.drawArc(b, -40, 300, false, p);
                c.drawLine(cx + gr * 0.15f, gy, cx + gr, gy, p); break;
            case G_MI:
                c.drawRoundRect(b, gr * 0.45f, gr * 0.45f, p);
                glyphText(c, "mi", cx, gy, gr * 1.15f, gr * 1.15f, p, Typeface.create("sans-serif", Typeface.BOLD)); break;
            case G_SQ_LETTER:
                c.drawRoundRect(b, gr * 0.45f, gr * 0.45f, p);
                glyphText(c, letter, cx, gy, gr * 1.15f, gr * 1.15f, p, Typeface.create("sans-serif", Typeface.BOLD)); break;
            case G_OVAL: {
                c.save(); c.rotate(-14f, cx, gy);
                RectF ov = new RectF(cx - gr * 1.25f, gy - gr * 0.7f, cx + gr * 1.25f, gy + gr * 0.7f);
                c.drawOval(ov, p); c.restore(); break;
            }
            case G_O:
                c.drawCircle(cx, gy, gr, p); break;
            case G_V:
                path.moveTo(cx - gr, gy - gr * 0.7f); path.lineTo(cx, gy + gr * 0.85f); path.lineTo(cx + gr, gy - gr * 0.7f);
                c.drawPath(path, p); break;
            case G_X:
                c.drawLine(cx - gr * 0.85f, gy - gr * 0.85f, cx + gr * 0.85f, gy + gr * 0.85f, p);
                c.drawLine(cx + gr * 0.85f, gy - gr * 0.85f, cx - gr * 0.85f, gy + gr * 0.85f, p); break;
            case G_TRI_T:
                path.moveTo(cx, gy + gr); path.lineTo(cx - gr, gy - gr * 0.75f); path.lineTo(cx + gr, gy - gr * 0.75f); path.close();
                c.drawPath(path, p); break;
            case G_M_RING:
                c.drawCircle(cx, gy, gr, p);
                path.moveTo(cx - gr * 0.5f, gy + gr * 0.45f); path.lineTo(cx - gr * 0.5f, gy - gr * 0.4f); path.lineTo(cx, gy + gr * 0.15f);
                path.lineTo(cx + gr * 0.5f, gy - gr * 0.4f); path.lineTo(cx + gr * 0.5f, gy + gr * 0.45f);
                p.setStrokeWidth(sw * 0.8f); c.drawPath(path, p); p.setStrokeWidth(sw); break;
            case G_A:
                path.moveTo(cx - gr, gy + gr * 0.85f); path.lineTo(cx, gy - gr * 0.85f); path.lineTo(cx + gr, gy + gr * 0.85f);
                c.drawPath(path, p); break;
            case G_ONEPLUS:
                c.drawRoundRect(b, gr * 0.3f, gr * 0.3f, p);
                glyphText(c, "1+", cx, gy, gr * 1.2f, gr * 1.15f, p, Typeface.create("sans-serif", Typeface.BOLD)); break;
            case G_FLOWER:
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(-90 + 45 * i);
                    c.drawLine(cx + (float) Math.cos(a) * gr * 0.35f, gy + (float) Math.sin(a) * gr * 0.35f,
                               cx + (float) Math.cos(a) * gr, gy + (float) Math.sin(a) * gr, p);
                }
                break;
            case G_LG:
                c.drawArc(b, -30, 330, false, p);                                      // lingkaran terbuka (G)
                c.drawLine(cx, gy - gr * 0.35f, cx, gy + gr * 0.4f, p);                // batang L
                c.drawLine(cx, gy + gr * 0.4f, cx + gr * 0.55f, gy + gr * 0.4f, p);    // kaki L
                p.setStyle(Paint.Style.FILL); c.drawCircle(cx - gr * 0.42f, gy - gr * 0.42f, sw * 0.75f, p); // mata
                break;
            case G_LETTER:
                c.drawCircle(cx, gy, gr, p);
                glyphText(c, letter, cx, gy, gr * 1.1f, gr * 1.15f, p, Typeface.create("sans-serif", Typeface.BOLD)); break;
        }
        // wordmark seragam
        p.setStyle(Paint.Style.FILL); p.setTypeface(FACE); p.setTextAlign(Paint.Align.CENTER);
        p.setLetterSpacing(0.02f);
        p.setTextSize(100f);
        float w = p.measureText(word);
        float size = Math.min(r * 0.40f, 100f * (r * 1.3f) / Math.max(1f, w));
        p.setTextSize(size);
        Paint.FontMetrics fm = p.getFontMetrics();
        float ty = cy + r * 0.50f - (fm.ascent + fm.descent) / 2f;
        c.drawText(word, cx, ty, p);
    }
}
