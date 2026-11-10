package za.co.koenekt.app;

import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import za.co.koenekt.app.HistoryMath.Agg;
import za.co.koenekt.app.HistoryMath.Metric;

/**
 * My stores → Compare: a past period, store against store, shop type against shop type, or group against group,
 * ranked on any figure, against last year or the period before; the top six by day; every figure per row.
 * Today's race between stores stays on Live (CompareScreen).
 */
final class PeriodScreen {
    private final StoresScreen s;
    private final MainActivity a;
    private String periodKey = "mtd", compare = "prev_year", by = "store";
    private Metric metric = Metric.TURNOVER;
    private final Set<String> out = new HashSet<>();

    PeriodScreen(StoresScreen stores) { s = stores; a = stores.a; }
    private int dp(float n) { return a.dp(n); }

    void render() {
        a.setBar("Compare", false, Ui.iconButton(a, R.drawable.ic_refresh, "Refresh", () -> { s.past.forget(); s.refreshNow(); }));
        LinearLayout page = a.page(); page.setPadding(dp(16), dp(4), dp(16), dp(28));
        if (!s.ready(page)) return;
        if (!DashboardScreen.metricOk(metric, s.owner())) metric = Metric.TURNOVER;
        LocalDate today = PastData.today();
        String[] p = HistoryMath.period(periodKey, today), c = HistoryMath.against(p, compare);
        String lo = c != null && c[0].compareTo(p[0]) < 0 ? c[0] : p[0];
        if (s.past.groups == null) { page.addView(Ui.loading(a)); s.past.needGroups(s::redraw); return; }
        if (!s.past.has(lo, p[1]) && s.past.historyError.isEmpty()) {
            s.past.needHistory(lo, p[1], s::redraw);
            if (s.past.history == null || !s.past.history.covers(lo, p[1])) { page.addView(Ui.loading(a)); return; }
        }
        DashboardScreen ds = new DashboardScreen(s);
        DashboardScreen.HorizontalChips chips = ds.new HorizontalChips(page);
        chips.add(HistoryMath.label(HistoryMath.PERIODS, periodKey), () -> pick("Period", HistoryMath.PERIODS, periodKey, v -> { periodKey = v; render(); }));
        chips.add(HistoryMath.label(HistoryMath.COMPARES, compare), () -> pick("Against", HistoryMath.COMPARES, compare, v -> { compare = v; render(); }));
        chips.add("Compare: " + HistoryMath.label(DashboardScreen.BYS, by), () -> pick("Compare", DashboardScreen.BYS, by, v -> { by = v; out.clear(); render(); }));
        List<Metric> ms = DashboardScreen.metrics(s.owner()); String[][] mo = new String[ms.size()][];
        for (int i = 0; i < mo.length; i++) mo[i] = new String[]{ms.get(i).key, ms.get(i).label};
        chips.add("Rank by: " + metric.label, () -> pick("Rank by", mo, metric.key, v -> { metric = Metric.of(v); render(); }));
        List<PastData.Entity> all = s.past.entities(by), list = new ArrayList<>();
        for (PastData.Entity e : all) if (!out.contains(e.key)) list.add(e);
        if (!all.isEmpty()) chips.add(list.size() + " of " + all.size(), () -> pickWho(all));
        if (!s.past.historyError.isEmpty()) { page.addView(Ui.notice(a, s.past.historyError, true)); Ui.gap(page, 12); }
        page.addView(Ui.text(a, HistoryMath.range(p, today) + (c == null ? "" : " against " + HistoryMath.range(c, today)) + ". Past days only: today is on Live.", 13, Ui.MUTED));
        Ui.gap(page, 12);
        if (all.isEmpty()) {
            page.addView(s.message("group".equals(by) ? (s.owner() ? "No groups yet. Make groups of your stores on the Groups tab." : "Your store owner has not made any groups.") : "Nothing to compare."));
            return;
        }
        LinearLayout rank = Ui.card(a);
        rank.addView(Ui.label(a, metric.label + ", " + (metric.lower ? "lowest" : "highest") + " first")); Ui.gap(rank, 8);
        ds.ranking(rank, list, metric, p, c, 50);
        page.addView(rank, Ui.fill(a, -1)); Ui.gap(page, 12);

        long span = ChronoUnit.DAYS.between(LocalDate.parse(p[0]), LocalDate.parse(p[1])) + 1;
        Metric line = metric == Metric.STOCK_DAYS ? Metric.TURNOVER : metric;
        List<PastData.Entity> top = new ArrayList<>();
        for (PastData.Entity e : list) if (HistoryMath.agg(s.past.history(), e.ids, p[0], p[1], s.owner()) != null) top.add(e);
        top.sort((x, y) -> Double.compare(value(y, p), value(x, p)));
        if (top.size() > 6) top = top.subList(0, 6);
        if (top.size() > 1 && span > 1) {
            LinearLayout box = Ui.card(a);
            box.addView(Ui.label(a, line.label + " by day · top " + top.size())); Ui.gap(box, 8);
            double[][] series = new double[top.size()][];
            for (int i = 0; i < series.length; i++) series[i] = HistoryMath.series(s.past.history(), top.get(i).ids, p[0], p[1], line, s.owner());
            Chart chart = new Chart(a);
            chart.days(series, DashboardScreen.dayLabels(p[0], (int) span, today), v -> HistoryMath.format(v, line));
            box.addView(chart, new LinearLayout.LayoutParams(-1, dp(190)));
            int[] colours = Chart.series();
            for (int i = 0; i < top.size(); i++) {
                LinearLayout key = Ui.row(a); key.setPadding(0, dp(4), 0, 0);
                android.view.View sw = new android.view.View(a); sw.setBackground(Ui.shape(colours[i % colours.length], 1, a));
                key.addView(sw, new LinearLayout.LayoutParams(dp(14), dp(3)));
                TextView name = Ui.text(a, "  " + top.get(i).name, 13, Ui.INK); key.addView(name);
                box.addView(key);
            }
            page.addView(box, Ui.fill(a, -1)); Ui.gap(page, 12);
        }

        LinearLayout table = Ui.card(a); table.addView(Ui.label(a, "All figures"));
        List<PastData.Entity> rows = new ArrayList<>(list);
        rows.sort((x, y) -> (metric.lower ? 1 : -1) * Double.compare(value(x, p), value(y, p)));
        Set<String> together = new HashSet<>();
        for (PastData.Entity e : rows) { together.addAll(e.ids); table.addView(Ui.divider(a, 0)); table.addView(figures(e.name, e.ids, p, c, ms)); }
        table.addView(Ui.divider(a, 0)); table.addView(figures("Together", new ArrayList<>(together), p, c, ms));
        page.addView(table, Ui.fill(a, -1));
    }

    private double value(PastData.Entity e, String[] p) {
        Agg x = HistoryMath.agg(s.past.history(), e.ids, p[0], p[1], s.owner());
        double v = x == null ? Double.NaN : x.value(metric);
        return Double.isNaN(v) ? (metric.lower ? Double.MAX_VALUE : -Double.MAX_VALUE) : v;
    }
    private LinearLayout figures(String name, List<String> ids, String[] p, String[] c, List<Metric> ms) {
        Agg now = HistoryMath.agg(s.past.history(), ids, p[0], p[1], s.owner()), before = c == null ? null : HistoryMath.agg(s.past.history(), ids, c[0], c[1], s.owner());
        LinearLayout r = Ui.column(a); r.setPadding(0, dp(10), 0, dp(10));
        r.addView(Ui.title(a, name + (ids.size() > 1 ? "  ·  " + ids.size() + " stores" : ""), 15));
        if (now == null) { r.addView(Ui.text(a, "No days in this period yet.", 13, Ui.MUTED)); return r; }
        StringBuilder b = new StringBuilder();
        for (Metric m : ms) {
            double v = now.value(m); if (Double.isNaN(v)) continue;
            if (b.length() > 0) b.append("\n");
            b.append(m.label).append(": ").append(HistoryMath.format(v, m));
            String ct = HistoryMath.changeText(HistoryMath.change(now, before, m), m);
            if (!ct.isEmpty()) b.append("  (").append(ct).append(")");
        }
        TextView t = Ui.text(a, b.toString(), 13, Ui.MUTED); r.addView(t);
        return r;
    }
    private void pickWho(List<PastData.Entity> all) {
        String[] names = new String[all.size()]; boolean[] on = new boolean[all.size()];
        for (int i = 0; i < names.length; i++) { names[i] = all.get(i).name; on[i] = !out.contains(all.get(i).key); }
        Ui.dialog(a).setTitle("Who is compared").setMultiChoiceItems(names, on, (d, w, checked) -> on[w] = checked)
                .setPositiveButton("Show", (d, w) -> { out.clear(); for (int i = 0; i < on.length; i++) if (!on[i]) out.add(all.get(i).key); render(); })
                .setNegativeButton("Cancel", null).show();
    }
    private void pick(String title, String[][] opts, String current, java.util.function.Consumer<String> picked) {
        String[] names = new String[opts.length]; int sel = 0;
        for (int i = 0; i < opts.length; i++) { names[i] = opts[i][1]; if (opts[i][0].equals(current)) sel = i; }
        Ui.choose(a, title, names, sel, w -> picked.accept(opts[w][0]));
    }
}
