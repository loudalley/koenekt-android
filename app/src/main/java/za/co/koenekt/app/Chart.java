package za.co.koenekt.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Small dependency-free charts for My stores: hourly bars, and running-total lines for comparing stores. */
final class Chart extends View {
    static final int[] SERIES = {Ui.BRAND, Ui.LIME, Color.rgb(46, 112, 196), Color.rgb(214, 124, 32),
            Color.rgb(140, 82, 196), Color.rgb(196, 64, 96)};
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG), label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF();
    private final Path path = new Path();
    private double[][] series = new double[0][];
    private boolean bars;
    private int first = 0, last = 23;

    Chart(Context c) {
        super(c);
        label.setColor(Ui.MUTED); label.setTextSize(Ui.dp(c, 11));
        line.setStyle(Paint.Style.STROKE); line.setStrokeWidth(Ui.dp(c, 2.5f)); line.setStrokeJoin(Paint.Join.ROUND);
        setMinimumHeight(Ui.dp(c, 170));
    }

    /** One series of hourly figures as bars. */
    void bars(double[] hours) { bars = true; series = new double[][]{hours}; span(); invalidate(); }
    /** Several series as lines (one colour each, in SERIES order). */
    void lines(double[][] values) { bars = false; series = values; span(); invalidate(); }

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
            fill.setColor(Ui.BRAND);
            for (int i = 0; i < n && first + i < s.length; i++) {
                float y = (float) (bottom - (bottom - top) * s[first + i] / max);
                bar.set(i * step + gap / 2, y, (i + 1) * step - gap / 2, bottom);
                canvas.drawRoundRect(bar, 6, 6, fill);
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
            line.setColor(SERIES[k % SERIES.length]); canvas.drawPath(p, line);
        }
    }
}
