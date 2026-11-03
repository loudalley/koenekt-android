package za.co.koenekt.app;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import za.co.koenekt.app.StoreMath.Figures;
import za.co.koenekt.app.StoreMath.Metric;

/** Stores (or store types) against each other: a ranking for one figure, head to head on all of them, and the hour-by-hour race. */
final class CompareScreen {
    private final StoresScreen s;
    private final MainActivity a;
    private Metric metric;
    private boolean byType;
    /** Store ids left out of the comparison (all shown stores are in by default). */
    private final Set<String> out = new HashSet<>();

    CompareScreen(StoresScreen stores, Metric start) { s = stores; a = stores.a; metric = start; }
    private int dp(float n) { return a.dp(n); }

    /** The contenders: chosen stores, or one total per store type. */
    private List<Figures> contenders() {
        List<Figures> stores = new ArrayList<>();
        for (Figures f : s.all()) if (!out.contains(f.id)) stores.add(f);
        if (!byType) return stores;
        List<Figures> types = new ArrayList<>();
        for (Map.Entry<String, List<Figures>> g : StoreMath.byType(stores).entrySet()) {
            Figures t = StoreMath.total(g.getKey(), g.getValue()); t.id = "type:" + g.getKey(); types.add(t);
        }
        return types;
    }

    void render() {
        a.setBar("Compare", true, Ui.iconButton(a, R.drawable.ic_refresh, "Refresh", s::refreshNow));
        a.onBack(s::back);
        LinearLayout page = a.page(); page.setPadding(dp(16), dp(4), dp(16), dp(28));
        if (metric.needsGp() && !s.owner()) metric = Metric.SALES;
        HorizontalScrollView scroll = new HorizontalScrollView(a); scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = Ui.row(a); scroll.addView(chips); page.addView(scroll); Ui.gap(page, 16);
        s.addChip(chips, byType ? "Type vs type" : "Store vs store", () -> Ui.choose(a, "Compare",
                new String[]{"Store vs store", "Type vs type"}, byType ? 1 : 0, w -> { byType = w == 1; render(); }));
        s.addChip(chips, metric.label, () -> {
            List<Metric> ms = s.metrics(); String[] names = new String[ms.size()];
            for (int i = 0; i < names.length; i++) names[i] = ms.get(i).label;
            Ui.choose(a, "Compare on", names, Math.max(0, ms.indexOf(metric)), w -> { metric = ms.get(w); render(); });
        });
        List<Figures> all = s.all();
        s.addChip(chips, (all.size() - countOut(all)) + " of " + all.size() + " stores", this::pickStores);

        List<Figures> list = StoreMath.rank(contenders(), metric);
        if (list.size() < 2) {
            page.addView(s.message(byType ? "Give your stores types (My stores → Customise → Store types) to compare type against type."
                    : "Choose at least two stores to compare."));
            return;
        }
        ranking(page, list);
        if (list.size() <= 4) headToHead(page, list);
        race(page, StoreMath.rank(contenders(), Metric.SALES));
        String at = StoreMath.clock(StoreMath.total("all", contenders()).asOf);
        page.addView(Ui.text(a, "Today so far, from the tills" + (at.isEmpty() ? "" : " (oldest read " + at + ")")
                + ". Stores close at different times, so compare evenings with care. Past days and weeks follow once the shop PCs send back-office history.", 13, Ui.MUTED));
    }

    private int countOut(List<Figures> all) { int n = 0; for (Figures f : all) if (out.contains(f.id)) n++; return n; }

    private void pickStores() {
        List<Figures> all = s.all(); String[] names = new String[all.size()]; boolean[] on = new boolean[all.size()];
        for (int i = 0; i < names.length; i++) { names[i] = all.get(i).name + "  ·  " + all.get(i).type; on[i] = !out.contains(all.get(i).id); }
        new AlertDialog.Builder(a).setTitle("Stores in the comparison").setMultiChoiceItems(names, on, (d, w, c) -> on[w] = c)
                .setPositiveButton("Compare", (d, w) -> { out.clear(); for (int i = 0; i < on.length; i++) if (!on[i]) out.add(all.get(i).id); render(); })
                .setNegativeButton("Cancel", null).show();
    }

    /** One bar per contender, best first; the bar shows its share of the biggest figure. */
    private void ranking(LinearLayout page, List<Figures> list) {
        page.addView(Ui.title(a, metric.label + (metric.lowerWins() ? " · fewer is better" : ""), 17)); Ui.gap(page, 8);
        LinearLayout c = Ui.card(a); page.addView(c); Ui.gap(page, 18);
        double max = 0;
        for (Figures f : list) { double v = StoreMath.value(f, metric); if (!Double.isNaN(v)) max = Math.max(max, v); }
        for (int i = 0; i < list.size(); i++) {
            Figures f = list.get(i); double v = StoreMath.value(f, metric);
            LinearLayout r = Ui.row(a);
            TextView name = Ui.text(a, (i + 1) + ".  " + f.name, 15, Ui.INK); name.setSingleLine(true);
            r.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            TextView val = Ui.title(a, StoreMath.format(f, metric), 15); r.addView(val);
            c.addView(r);
            FrameLayout track = new FrameLayout(a); track.setBackground(Ui.shape(Ui.BG, 6, a));
            View bar = new View(a); bar.setBackground(Ui.shape(i == 0 && !Double.isNaN(v) ? Ui.LIME : Ui.BRAND, 6, a));
            float share = max > 0 && !Double.isNaN(v) ? (float) (v / max) : 0f;
            track.addView(bar, new FrameLayout.LayoutParams(0, -1));
            track.post(() -> { bar.getLayoutParams().width = Math.round(track.getWidth() * share); bar.requestLayout(); });
            LinearLayout.LayoutParams p = Ui.fill(a, 10); p.topMargin = dp(6); p.bottomMargin = dp(i == list.size() - 1 ? 0 : 12);
            c.addView(track, p);
        }
    }

    /** Every figure side by side; the winner of each row is highlighted. */
    private void headToHead(LinearLayout page, List<Figures> list) {
        page.addView(Ui.title(a, "Head to head", 17)); Ui.gap(page, 8);
        LinearLayout c = Ui.column(a); c.setBackground(Ui.outline(Ui.SURFACE, Ui.LINE, 1, 20, a)); c.setClipToOutline(true);
        c.setPadding(0, dp(6), 0, dp(6));
        LinearLayout head = Ui.row(a); head.setPadding(dp(12), dp(6), dp(8), dp(6));
        head.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1.3f));
        for (int k = 0; k < list.size(); k++) {
            TextView t = Ui.label(a, list.get(k).name); t.setGravity(Gravity.END); t.setMaxLines(2);
            t.setTextColor(Chart.SERIES[k % Chart.SERIES.length]);
            head.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        }
        c.addView(head);
        for (Metric m : s.metrics()) {
            c.addView(Ui.divider(a, 0));
            LinearLayout r = Ui.row(a); r.setPadding(dp(12), dp(8), dp(8), dp(8));
            r.addView(Ui.text(a, m.label, 13.5f, Ui.MUTED), new LinearLayout.LayoutParams(0, -2, 1.3f));
            Figures best = StoreMath.rank(list, m).get(0);
            boolean any = m != Metric.TILLS && !Double.isNaN(StoreMath.value(best, m));   // more tills is not "better"
            for (Figures f : list) {
                TextView v = Ui.text(a, StoreMath.format(f, m), 14, Ui.INK); v.setGravity(Gravity.END);
                if (any && f == best && list.size() > 1) { v.setTextColor(Ui.BRAND); v.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD)); }
                r.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
            }
            c.addView(r);
        }
        page.addView(c); Ui.gap(page, 18);
    }

    /** Running total of sales through the day, up to six contenders, one line each. */
    private void race(LinearLayout page, List<Figures> bySales) {
        int n = Math.min(bySales.size(), Chart.SERIES.length);
        page.addView(Ui.title(a, "Hour by hour", 17)); Ui.gap(page, 2);
        page.addView(Ui.text(a, "Sales so far today, added up hour by hour", 13, Ui.MUTED)); Ui.gap(page, 8);
        LinearLayout c = Ui.card(a);
        double[][] lines = new double[n][];
        for (int i = 0; i < n; i++) lines[i] = StoreMath.cumulative(bySales.get(i).hours);
        Chart chart = new Chart(a); chart.lines(lines); c.addView(chart, Ui.fill(a, 190));
        Ui.gap(c, 8);
        for (int i = 0; i < n; i++) {
            LinearLayout r = Ui.row(a); r.setPadding(0, dp(3), 0, dp(3));
            r.addView(Ui.dot(a, Chart.SERIES[i]));
            TextView t = Ui.text(a, "  " + bySales.get(i).name, 14, Ui.INK);
            r.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
            r.addView(Ui.text(a, StoreMath.money(bySales.get(i).sales), 14, Ui.MUTED));
            c.addView(r);
        }
        page.addView(c); Ui.gap(page, 14);
    }
}
