package za.co.koenekt.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A till slip drawn as the till printed it: fixed-width columns (40 on Vector's 80 mm paper), bold, double-height
 * and white-on-black text kept, on white paper in both light and dark mode. The same drawing makes the picture and
 * the PDF that are sent on, so what is sent is what is on screen. The lines come from the shop PC (Reporter's
 * linkslips.py; styles as in kslipper's slipcore.py) and already end with the "*** REPRINT ... ***" line.
 */
@android.annotation.SuppressLint("ViewConstructor")   // made in code only, never from a layout file
final class SlipView extends View {
    static final int BOLD = 1, DOUBLE = 2, REVERSE = 4;
    private static final int PAPER = Color.WHITE, INK = 0xFF1A1A1A;
    /** Zoom after a tap; the slip always starts fitted to the screen's width. */
    private static final float ZOOMED = 1.75f;

    /** One printed line: its segments in order, each with a style. */
    static final class Line {
        boolean centred;
        final List<String> text = new ArrayList<>();
        final List<Integer> style = new ArrayList<>();
        int length() { int n = 0; for (String t : text) n += t.length(); return n; }
        boolean tall() { for (int s : style) if ((s & DOUBLE) != 0) return true; return false; }
    }

    static List<Line> parse(JSONArray lines) {
        List<Line> out = new ArrayList<>();
        if (lines != null) for (int i = 0; i < lines.length(); i++) {
            JSONObject l = lines.optJSONObject(i); if (l == null) continue;
            Line line = new Line(); line.centred = l.optBoolean("c");
            JSONArray segs = l.optJSONArray("s");
            if (segs != null) for (int k = 0; k < segs.length(); k++) {
                JSONArray seg = segs.optJSONArray(k); if (seg == null) continue;
                line.text.add(seg.optString(0)); line.style.add(seg.optInt(1));
            }
            out.add(line);
        }
        return out;
    }
    /** The slip as plain text, one printed line per line (for a screen reader). */
    static String plain(List<Line> lines) {
        StringBuilder b = new StringBuilder();
        for (Line l : lines) { for (String t : l.text) b.append(t); b.append('\n'); }
        return b.toString();
    }

    private final List<Line> lines;
    private final int cols, baseWidth;
    private boolean zoomed;
    private final Paint edge = new Paint();

    /** baseWidth: the paper's width in pixels when it fits the screen. */
    SlipView(Context c, List<Line> lines, int cols, int baseWidth) {
        super(c); this.lines = lines; this.cols = Math.max(20, cols); this.baseWidth = baseWidth;
        setContentDescription("Slip as printed. " + plain(lines));
        edge.setStyle(Paint.Style.STROKE); edge.setStrokeWidth(Math.max(1f, c.getResources().getDisplayMetrics().density));
        setOnClickListener(v -> { zoomed = !zoomed; requestLayout(); invalidate(); });
    }
    private float width() { return baseWidth * (zoomed ? ZOOMED : 1f); }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(Math.round(width()), (int) Math.ceil(draw(null, lines, cols, width())));
    }
    @Override protected void onDraw(Canvas canvas) {
        draw(canvas, lines, cols, width());
        edge.setColor(Ui.LINE);   // the paper's edge, on screen only (not in the picture or the PDF)
        canvas.drawRect(0.5f, 0.5f, getWidth() - 0.5f, getHeight() - 0.5f, edge);
    }

    /** Draws the slip `width` wide from the top left; with no canvas it only measures. Returns the height. */
    static float draw(Canvas canvas, List<Line> lines, int cols, float width) {
        float pad = width * 0.05f, cell = (width - 2 * pad) / cols;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(Ui.MONO); p.setTextSize(100);
        float size = 100f * cell / Math.max(1f, p.measureText("0"));
        p.setTextSize(size);
        float row = size * 1.30f, base = size * 1.0f, y = pad;
        Paint fill = new Paint(); fill.setStyle(Paint.Style.FILL);
        if (canvas != null) {
            float total = pad;
            for (Line l : lines) total += l.tall() ? row * 2 : row;
            fill.setColor(PAPER); canvas.drawRect(0, 0, width, total + pad, fill);
        }
        for (Line l : lines) {
            boolean tall = l.tall();
            float h = tall ? row * 2 : row;
            if (canvas != null) {
                int n = l.length();
                int col = l.centred && n < cols ? (cols - n) / 2 : 0;
                for (int s = 0; s < l.text.size(); s++) {
                    String t = l.text.get(s); int st = l.style.get(s);
                    boolean bold = (st & BOLD) != 0, dbl = (st & DOUBLE) != 0, rev = (st & REVERSE) != 0;
                    float x = pad + col * cell;
                    if (rev) { fill.setColor(INK); canvas.drawRect(x, y, Math.min(width - pad, x + t.length() * cell), y + h, fill); }
                    p.setColor(rev ? PAPER : INK);
                    p.setTypeface(bold || rev ? Ui.MONO_BOLD : Ui.MONO); p.setFakeBoldText(bold && Ui.MONO_BOLD == Ui.MONO);
                    // one character per column, so a glyph the font lacks can never push the columns out of line
                    for (int i = 0; i < t.length() && col + i < cols; i++) {
                        char ch = t.charAt(i); if (ch == ' ') continue;
                        float cx = x + i * cell;
                        if (dbl) {
                            canvas.save(); canvas.translate(cx, y + base * 2); canvas.scale(1f, 2f);
                            canvas.drawText(t, i, i + 1, 0, 0, p); canvas.restore();
                        } else canvas.drawText(t, i, i + 1, cx, y + (tall ? row : 0) + base, p);
                    }
                    col += t.length();
                }
            }
            y += h;
        }
        return y + pad;
    }

    /** The slip as a PNG `width` pixels wide (a long slip is made narrower so it stays a sensible size). */
    static byte[] png(List<Line> lines, int cols, int width) throws java.io.IOException {
        float h = draw(null, lines, cols, width);
        if (h > 9000) { width = Math.max(480, Math.round(width * 9000f / h)); h = draw(null, lines, cols, width); }
        Bitmap b = Bitmap.createBitmap(width, (int) Math.ceil(h), Bitmap.Config.ARGB_8888);
        try {
            draw(new Canvas(b), lines, cols, width);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!b.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new java.io.IOException("could not make the picture");
            return out.toByteArray();
        } finally { b.recycle(); }
    }

    /** The slip as a one-page PDF at receipt width (80 mm), as long as the slip. */
    static byte[] pdf(List<Line> lines, int cols) throws java.io.IOException {
        final int w = 227;   // 80 mm in points
        PdfDocument doc = new PdfDocument();
        try {
            int h = (int) Math.ceil(draw(null, lines, cols, w));
            PdfDocument.Page page = doc.startPage(new PdfDocument.PageInfo.Builder(w, Math.max(60, h), 1).create());
            draw(page.getCanvas(), lines, cols, w);
            doc.finishPage(page);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.writeTo(out);
            return out.toByteArray();
        } finally { doc.close(); }
    }
}
