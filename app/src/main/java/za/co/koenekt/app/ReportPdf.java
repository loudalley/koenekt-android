package za.co.koenekt.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.pdf.PdfDocument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The branded PDF for a report download: Koenekt logo, Big Shoulders title, IBM Plex text, the
 * site's green, headline figures, tables with repeating headers and page numbers. Same layout as
 * the website's PDF (report-export.js); drawn with Android's own PdfDocument, so no library.
 * Always the light palette: a printed page is white.
 */
final class ReportPdf {
    private ReportPdf() { }

    private static final int TEXT = 0xFF262626, DIM = 0xFF6B6B68, ACCENT = 0xFF5FA300, ACCENT_DARK = 0xFF4B8300,
            HEAD = 0xFFEAF3DC, ZEBRA = 0xFFF7F7F4, LINE = 0xFFDCDCD5;
    private static final float MX = 36, TOP = 36, BOTTOM = 40, FS = 7.5f, RH = 13, PAD = 4;

    private static final class Fonts {
        final Typeface sans, bold, display, mono;
        Fonts(Context c) {
            Typeface s = Typeface.SANS_SERIF, b = Typeface.DEFAULT_BOLD, d = Typeface.DEFAULT_BOLD, m = Typeface.MONOSPACE;
            try {
                s = c.getResources().getFont(R.font.ibm_plex_sans_regular);
                b = c.getResources().getFont(R.font.ibm_plex_sans_semibold);
                d = c.getResources().getFont(R.font.big_shoulders_display_extrabold);
                m = c.getResources().getFont(R.font.ibm_plex_mono_medium);
            } catch (RuntimeException ignored) { }
            sans = s; bold = b; display = d; mono = m;
        }
    }

    private final static class Doc {
        final PdfDocument pdf;                 // null on the dry run that only counts pages
        final float pw, ph, cw;
        final Context c; final Fonts f; final ReportFiles.Model m;
        final int total; final String stamp;
        PdfDocument.Page page; Canvas cv; float y; int count;
        Doc(Context c, ReportFiles.Model m, boolean wide, Fonts f, boolean dry, int total, String stamp) {
            this.c = c; this.m = m; this.f = f; this.total = total; this.stamp = stamp;
            pdf = dry ? null : new PdfDocument();
            pw = wide ? 842 : 595; ph = wide ? 595 : 842; cw = pw - 2 * MX;
        }
        Paint paint(Typeface t, float size, int color) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
            p.setTypeface(t); p.setTextSize(size); p.setColor(color);
            return p;
        }
        void rect(float x, float yy, float w, float h, int color) {
            if (cv == null) return;
            Paint p = new Paint(); p.setColor(color); p.setStyle(Paint.Style.FILL);
            cv.drawRect(x, yy, x + w, yy + h, p);
        }
        void line(float x1, float y1, float x2, float y2, int color, float width) {
            if (cv == null) return;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(color); p.setStrokeWidth(width);
            cv.drawLine(x1, y1, x2, y2, p);
        }
        /** Text at baseline yy, cut with an ellipsis to fit width; align l, r or c. */
        void text(String s, float x, float yy, Paint p, char align, float width) {
            if (cv == null) return;
            String t = fit(s, p, width);
            float tw = p.measureText(t);
            float tx = align == 'r' ? x + width - tw : align == 'c' ? x + (width - tw) / 2 : x;
            cv.drawText(t, tx, yy, p);
        }
        void header() {
            count++;
            y = TOP + 54;
            if (pdf == null) return;
            if (page != null) pdf.finishPage(page);
            page = pdf.startPage(new PdfDocument.PageInfo.Builder((int) pw, (int) ph, count).create());
            cv = page.getCanvas();
            Paint foot = paint(f.sans, 7, DIM);
            line(MX, ph - 28, pw - MX, ph - 28, LINE, 0.5f);
            text("Koenekt · My stores · koenekt.co.za", MX, ph - 17, foot, 'l', cw);
            text("Downloaded " + stamp, MX, ph - 17, foot, 'c', cw);
            text("Page " + count + " of " + total, MX, ph - 17, foot, 'r', cw);
            float lh = 22;
            Drawable logo = c.getDrawable(R.drawable.ic_logo);
            if (logo != null) {
                float ratio = logo.getIntrinsicHeight() > 0 ? (float) logo.getIntrinsicWidth() / logo.getIntrinsicHeight() : 4.27f;
                logo.setBounds((int) MX, (int) TOP, (int) (MX + lh * ratio), (int) (TOP + lh));
                logo.draw(cv);
            }
            text(m.title.toUpperCase(Locale.ROOT), MX, TOP + 18, paint(f.display, 19, TEXT), 'r', cw);
            text(m.store + (m.account.isEmpty() ? "" : " · " + m.account) + " · " + m.period(), MX, TOP + 31, paint(f.sans, 9, DIM), 'r', cw);
            rect(MX, TOP + 38, cw, 1.6f, ACCENT);
        }
        void room(float h) { if (y + h > ph - BOTTOM) header(); }
    }

    static String fit(String s, Paint p, float width) {
        if (s == null) return "";
        s = s.replaceAll("[\\r\\n\\t]+", " ");
        if (p.measureText(s) <= width + 0.05f) return s;
        String t = s;
        while (!t.isEmpty() && p.measureText(t + "…") > width) t = t.substring(0, t.length() - 1);
        return t + "…";
    }

    private static String cellText(Object v, ReportFiles.Col col) {
        return col.text() ? (v == null ? "" : v.toString()) : ReportFiles.fmt(v, col.type);
    }

    static byte[] build(Context c, ReportFiles.Model m) throws IOException {
        boolean wide = false;
        for (ReportFiles.Table t : m.tables) if (t.cols.length > 8) wide = true;
        Fonts f = new Fonts(c);
        String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(new java.util.Date());
        Doc dry = new Doc(c, m, wide, f, true, 0, stamp);       // count the pages for "Page n of N"
        layout(dry);
        Doc d = new Doc(c, m, wide, f, false, dry.count, stamp);
        layout(d);
        d.pdf.finishPage(d.page);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        d.pdf.writeTo(out);
        d.pdf.close();
        return out.toByteArray();
    }

    private static void layout(Doc d) {
        ReportFiles.Model m = d.m;
        boolean wide = d.pw > d.ph;
        d.header();
        List<String> meta = new ArrayList<>();
        if (!m.suppliers.isEmpty()) meta.add("Suppliers: " + String.join(", ", m.suppliers));
        if (!m.made.isEmpty()) meta.add("Run on the shop PC at " + m.made);
        if (!meta.isEmpty()) { d.text(String.join("   ·   ", meta), MX, d.y, d.paint(d.f.sans, 8, DIM), 'l', d.cw); d.y += 14; }
        // headline figures
        if (!m.tiles.isEmpty()) {
            int per = Math.min(m.tiles.size(), wide ? 7 : 4);
            float tw = (d.cw - (per - 1) * 8) / per, th = 38;
            Paint lab = d.paint(d.f.mono, 6.5f, DIM), val = d.paint(d.f.display, 15, TEXT);
            for (int i = 0; i < m.tiles.size(); i++) {
                int col = i % per;
                if (col == 0 && i > 0) d.y += th + 8;
                if (col == 0) d.room(th + 8);
                float x = MX + col * (tw + 8);
                Object[] t = m.tiles.get(i);
                d.rect(x, d.y, tw, th, ZEBRA);
                d.rect(x, d.y, 2, th, ACCENT);
                d.text(((String) t[0]).toUpperCase(Locale.ROOT), x + 9, d.y + 13, lab, 'l', tw - 14);
                d.text(ReportFiles.fmt(t[1], (String) t[2]), x + 9, d.y + 31, val, 'l', tw - 14);
            }
            d.y += th + 16;
        }
        Paint note = d.paint(d.f.sans, 8, DIM);
        for (String n : m.notes) { d.room(12); d.text(n, MX, d.y, note, 'l', d.cw); d.y += 12; }
        if (!m.notes.isEmpty()) d.y += 4;
        // tables
        Paint head = d.paint(d.f.bold, FS, TEXT), body = d.paint(d.f.sans, FS, TEXT), bodyBold = d.paint(d.f.bold, FS, TEXT);
        Paint section = d.paint(d.f.bold, 11, TEXT), cont = d.paint(d.f.bold, 9, DIM), small = d.paint(d.f.sans, 7.5f, DIM);
        for (ReportFiles.Table t : m.tables) {
            if (t.rows.isEmpty()) continue;
            float[] w = new float[t.cols.length];
            for (int i = 0; i < w.length; i++) w[i] = head.measureText(t.cols[i].label) + 2 * PAD + 1;
            int sample = Math.min(t.rows.size(), 400);
            for (int ri = 0; ri < t.rows.size(); ri++) {
                if (ri >= sample && !(t.total && ri == t.rows.size() - 1)) continue;
                Object[] row = t.rows.get(ri);
                Paint p = t.total && ri == t.rows.size() - 1 ? bodyBold : body;
                for (int i = 0; i < w.length; i++) {
                    String s = cellText(i < row.length ? row[i] : null, t.cols[i]);
                    w[i] = Math.max(w[i], Math.min(p.measureText(s) + 2 * PAD + 1, t.cols[i].type.equals("t") ? 220 : 90));
                }
            }
            float total = 0; for (float x : w) total += x;
            if (total > d.cw) {
                float fixed = 0, flex = 0;
                for (int i = 0; i < w.length; i++) if (t.cols[i].type.equals("t")) flex += w[i]; else fixed += w[i];
                float scale = flex > 0 ? Math.max(0.25f, (d.cw - fixed) / flex) : d.cw / total;
                for (int i = 0; i < w.length; i++) if (t.cols[i].type.equals("t") || flex == 0) w[i] *= scale;
                total = 0; for (float x : w) total += x;
                if (total > d.cw) for (int i = 0; i < w.length; i++) w[i] *= d.cw / total;
            } else if (total < d.cw) {
                int ti = -1; float best = -1;
                for (int i = 0; i < w.length; i++) if (t.cols[i].type.equals("t") && w[i] > best) { best = w[i]; ti = i; }
                if (ti >= 0) w[ti] += d.cw - total;
            }
            d.room(22 + RH * 3);
            d.rect(MX, d.y - 1, 3, 13, ACCENT);
            d.text(t.name, MX + 8, d.y + 10, section, 'l', d.cw - 8);
            d.y += 18;
            headRow(d, t, w, head);
            for (int ri = 0; ri < t.rows.size(); ri++) {
                if (d.y + RH > d.ph - BOTTOM) {
                    d.header();
                    d.text(t.name + " (continued)", MX, d.y + 8, cont, 'l', d.cw);
                    d.y += 14;
                    headRow(d, t, w, head);
                }
                boolean isTotal = t.total && ri == t.rows.size() - 1;
                if (isTotal) d.line(MX, d.y, MX + d.cw, d.y, TEXT, 0.8f);
                else if (ri % 2 == 1) d.rect(MX, d.y, d.cw, RH, ZEBRA);
                Object[] row = t.rows.get(ri);
                float x = MX;
                for (int i = 0; i < w.length; i++) {
                    boolean right = !t.cols[i].text();
                    d.text(cellText(i < row.length ? row[i] : null, t.cols[i]), x + PAD, d.y + RH - 3.5f, isTotal ? bodyBold : body, right ? 'r' : 'l', w[i] - 2 * PAD);
                    x += w[i];
                }
                d.y += RH;
            }
            d.line(MX, d.y, MX + d.cw, d.y, LINE, 0.5f);
            d.y += 8;
            if (!t.note.isEmpty()) { d.room(12); d.text(t.note, MX, d.y + 4, small, 'l', d.cw); d.y += 12; }
            d.y += 10;
        }
    }

    private static void headRow(Doc d, ReportFiles.Table t, float[] w, Paint head) {
        d.rect(MX, d.y, d.cw, RH + 2, HEAD);
        float x = MX;
        for (int i = 0; i < w.length; i++) {
            d.text(t.cols[i].label, x + PAD, d.y + RH - 2.5f, head, t.cols[i].text() ? 'l' : 'r', w[i] - 2 * PAD);
            x += w[i];
        }
        d.line(MX, d.y + RH + 2, MX + d.cw, d.y + RH + 2, ACCENT_DARK, 0.8f);
        d.y += RH + 2;
    }
}
