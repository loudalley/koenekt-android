package za.co.koenekt.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Small dependency-free charts for My stores: hourly bars, running-total lines for comparing stores, and a line
 *  per day for past periods (Dashboard, Compare, Range report; a day with no figure is a gap). */
final class Chart extends View {
    /** One colour per store or type when comparing; each pair differs in lightness as well as hue. */
    static int[] series() {
        return Ui.dark ? new int[]{Ui.LIME, Color.rgb(134, 169, 245), Color.rgb(240, 163, 94), Color.rgb(185, 138, 240), Color.rgb(240, 138, 158), Ui.MUTED}
                : new int[]{Ui.BRAND, Color.rgb(46, 112, 196), Color.rgb(196, 106, 18), Color.rgb(128, 72, 186), Color.rgb(184, 52, 86), Ui.MUTED};
    }
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG), label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF();
    private final Path path = new Path();
    private double[][] series = new double[0][];
    private boolean bars;
    private int first = 0, last = 23;
    /** Days mode: labels under the x axis (one per point) and the text for the top value. */
    private String[] dayLabels;
    private java.util.function.DoubleFunction<String> valueText;

    Chart(Context c) {
        super(c);
        label.setColor(Ui.MUTED); label.setTextSize(Ui.dp(c, 11)); label.setTypeface(Ui.MONO);
        line.setStyle(Paint.Style.STROKE); line.setStrokeWidth(Ui.dp(c, 2.5f)); line.setStrokeJoin(Paint.Join.MITER);
        setMinimumHeight(Ui.dp(c, 170));
    }

    /** One series of hourly figures as bars. */
    void bars(double[] hours) { bars = true; dayLabels = null; series = new double[][]{hours}; span(); invalidate(); }
    /** Several series as lines (one colour each, in series() order). */
    void lines(double[][] values) { bars = false; dayLabels = null; series = values; span(); invalidate(); }
    /** One value per day per series (NaN = no figure that day); labels as long as each series. */
    void days(double[][] values, String[] labels, java.util.function.DoubleFunction<String> text) {
        bars = false; series = values; dayLabels = labels; valueText = text; invalidate();
    }

    /** Show only the trading hours: the first to the last hour with any figure (at least 6 hours). */
    private void span() {
        first = 23; last = 0;
        for (double[] s : series) for (int h = 0; h < s.length; h++) {
            // Bars: any hour with sales. Lines are running totals, so they end where the total stops growing.
            boolean moves = bars ? s[h] != 0 : s[h] != (h == 0 ? 0 : s[h - 1]);
            if (s[h] != 0 && first > h) first = h;
            if (moves) last = Math.max(last, h);
        }
        if (first > last) { first = 8; last = 17; }
        while (last - first < 5) { if (first > 0) first--; if (last - first < 5 && last < 23) last++; if (first == 0 && last == 23) break; }
    }

    @Override protected void onDraw(Canvas canvas) {
        if (dayLabels != null) { drawDays(canvas); return; }
        float w = getWidth(), h = getHeight(), bottom = h - Ui.dp(getContext(), 18), top = Ui.dp(getContext(), 6);
        double max = 0;
        for (double[] s : series) for (int i = first; i <= last && i < s.length; i++) max = Math.max(max, s[i]);
        int n = last - first + 1;
        float step = w / n;
        fill.setColor(Ui.LINE); canvas.drawRect(0, bottom, w, bottom + Math.max(1, Ui.dp(getContext(), 1)), fill);
        for (int i = 0; i < n; i++) {
            int hour = first + i;
            if (n <= 12 || hour % 2 == 0) {
                String t = String.valueOf(hour); float tw = label.measureText(t);
                canvas.drawText(t, i * step + (step - tw) / 2, h - Ui.dp(getContext(), 4), label);
            }
        }
        if (max <= 0) return;
        if (bars) {
            double[] s = series[0]; float gap = Math.max(2, step * 0.18f);
            fill.setColor(Ui.LIME);
            for (int i = 0; i < n && first + i < s.length; i++) {
                float y = (float) (bottom - (bottom - top) * s[first + i] / max);
                bar.set(i * step + gap / 2, y, (i + 1) * step - gap / 2, bottom);
                canvas.drawRect(bar, fill);
            }
            return;
        }
        for (int k = 0; k < series.length; k++) {
            double[] s = series[k]; Path p = path; p.reset();
            boolean empty = true; for (double v : s) if (v != 0) { empty = false; break; }
            if (empty) continue;   // a store with no figures (PC offline) gets no flat line
            for (int i = 0; i < n && first + i < s.length; i++) {
                float x = i * step + step / 2, y = (float) (bottom - (bottom - top) * s[first + i] / max);
                if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
            }
            int[] colours = series(); line.setColor(colours[k % colours.length]); canvas.drawPath(p, line);
        }
    }

    private void drawDays(Canvas canvas) {
        float w = getWidth(), h = getHeight(), bottom = h - Ui.dp(getContext(), 18), top = Ui.dp(getContext(), 18);
        double max = 0, min = 0; int n = 0;
        for (double[] s : series) { n = Math.max(n, s.length); for (double v : s) if (!Double.isNaN(v)) { max = Math.max(max, v); min = Math.min(min, v); } }
        fill.setColor(Ui.LINE); canvas.drawRect(0, bottom, w, bottom + Math.max(1, Ui.dp(getContext(), 1)), fill);
        if (n == 0 || max <= min) return;
        float step = n > 1 ? w / (n - 1) : w;
        if (valueText != null) canvas.drawText(valueText.apply(max), 0, top - Ui.dp(getContext(), 6), label);
        int every = Math.max(1, (int) Math.ceil(n / 5.0));
        for (int i = 0; i < n && i < dayLabels.length; i++) {
            boolean lastOne = i == n - 1;
            if (!(lastOne || (i % every == 0 && n - 1 - i >= every / 2.0))) continue;
            String t = dayLabels[i]; float tw = label.measureText(t), x = n > 1 ? i * step : w / 2;
            float at = i == 0 ? 0 : lastOne ? w - tw : x - tw / 2;
            canvas.drawText(t, Math.max(0, Math.min(w - tw, at)), h - Ui.dp(getContext(), 4), label);
        }
        int[] colours = series();
        for (int k = 0; k < series.length; k++) {
            double[] s = series[k]; Path p = path; p.reset(); boolean on = false;
            for (int i = 0; i < s.length; i++) {
                if (Double.isNaN(s[i])) { on = false; continue; }
                float x = n > 1 ? i * step : w / 2, y = (float) (bottom - (bottom - top) * (s[i] - min) / (max - min));
                boolean alone = (i == 0 || Double.isNaN(s[i - 1])) && (i == s.length - 1 || Double.isNaN(s[i + 1]));
                if (alone) { fill.setColor(series.length == 1 ? Ui.LIME : colours[k % colours.length]); canvas.drawCircle(x, y, Ui.dp(getContext(), 3.5f), fill); }
                if (on) p.lineTo(x, y); else p.moveTo(x, y);
                on = true;
            }
            line.setColor(series.length == 1 ? Ui.LIME : colours[k % colours.length]); canvas.drawPath(p, line);
        }
    }
}
