package za.co.koenekt.app;

import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import za.co.koenekt.app.HistoryMath.Agg;
import za.co.koenekt.app.HistoryMath.Metric;
import za.co.koenekt.app.StoreMath.Figures;

/**
 * My stores → Dashboard: the owner's own tiles over past days (and Live today), in the layout shared with the
 * website (Reporter repo docs/LINK-REPORTS-API.md; GET/POST /api/me/dashboard). Customise changes this phone's copy;
 * Save layout keeps it on the relay for the website too; Reset layout goes back to the standard one.
 */
final class DashboardScreen {
    private static final String[][] KINDS = {{"kpi", "Figure"}, {"trend", "Trend by day"}, {"ranking", "Ranking"}, {"table", "Table"}, {"live", "Live today"}};
    static final String[][] BYS = {{"store", "Store"}, {"group", "Your groups"}, {"type", "Shop type"}, {"shoptype", "Shop type (detailed)"}};
    private static final String[][] SIZES = {{"s", "Small (two to a row)"}, {"m", "Full width"}};
    private final StoresScreen s;
    private final MainActivity a;
    private boolean edit;
    private String note = "";

    DashboardScreen(StoresScreen stores) { s = stores; a = stores.a; }
    private int dp(float n) { return a.dp(n); }
    private JSONObject layout() { return s.past.layout != null ? s.past.layout : PastData.defaults(); }

    boolean wantsLive() {
        JSONArray t = layout().optJSONArray("tiles");
        if (t != null) for (int i = 0; i < t.length(); i++) if ("live".equals(t.optJSONObject(i) == null ? "" : t.optJSONObject(i).optString("kind"))) return true;
        return false;
    }
    static boolean metricOk(Metric m, boolean owner) { return m != null && (owner || !m.gp); }
    static List<Metric> metrics(boolean owner) { List<Metric> out = new ArrayList<>(); for (Metric m : Metric.values()) if (metricOk(m, owner)) out.add(m); return out; }

    private String[] period(JSONObject tile) {
        String key = tile.optString("period", layout().optString("period", "mtd"));
        return HistoryMath.period(key, PastData.today());
    }
    private String[] against(JSONObject tile, String[] p) {
        return HistoryMath.against(p, tile.optString("compare", layout().optString("compare", "prev_year")));
    }
    private boolean usable(JSONObject t) {
        String k = t.optString("kind");
        if (k.equals("live")) return true;
        if (k.equals("kpi") || k.equals("trend") || k.equals("ranking")) return metricOk(Metric.of(t.optString("metric")), s.owner());
        if (k.equals("table")) { for (String m : PastData.strings(t.optJSONArray("metrics"))) if (metricOk(Metric.of(m), s.owner())) return true; }
        return false;
    }
    /** The days every tile needs, so one call fetches them all. */
    private String[] needed() {
        String lo = null, hi = null; JSONArray tiles = layout().optJSONArray("tiles");
        LocalDate y = PastData.today().minusDays(1);
        if (tiles != null) for (int i = 0; i < tiles.length(); i++) {
            JSONObject t = tiles.optJSONObject(i); if (t == null || !usable(t) || t.optString("kind").equals("live")) continue;
            List<String[]> spans = new ArrayList<>();
            if (t.optString("kind").equals("trend")) spans.add(new String[]{y.minusDays(Math.max(1, t.optInt("days", 30)) - 1).toString(), y.toString()});
            else { String[] p = period(t); spans.add(p); String[] c = against(t, p); if (c != null) spans.add(c); }
            for (String[] sp : spans) { if (lo == null || sp[0].compareTo(lo) < 0) lo = sp[0]; if (hi == null || sp[1].compareTo(hi) > 0) hi = sp[1]; }
        }
        return lo == null ? null : new String[]{lo, hi};
    }

    void render() {
        a.setBar("Dashboard", false,
                Ui.iconButton(a, R.drawable.ic_tune, edit ? "Done customising" : "Customise dashboard", () -> { edit = !edit; note = ""; render(); }),
                Ui.iconButton(a, R.drawable.ic_refresh, "Refresh", () -> { s.past.forget(); s.refreshNow(); }));
        LinearLayout page = a.page(); page.setPadding(dp(16), dp(4), dp(16), dp(28));
        if (!s.ready(page)) return;
        if (!s.past.layoutLoaded || s.past.groups == null) {
            page.addView(Ui.loading(a));
            s.past.needLayout(() -> s.past.needGroups(s::redraw));
            return;
        }
        String[] need = needed();
        if (need != null && !s.past.has(need[0], need[1]) && s.past.historyError.isEmpty()) {
            if (s.past.history == null) { page.addView(Ui.loading(a)); s.past.needHistory(need[0], need[1], s::redraw); return; }
            s.past.needHistory(need[0], need[1], s::redraw);
        }
        JSONObject L = layout();
        HorizontalChips chips = new HorizontalChips(page);
        chips.add(HistoryMath.label(HistoryMath.PERIODS, L.optString("period", "mtd")), () -> pick("Period", HistoryMath.PERIODS, L.optString("period", "mtd"), v -> set(L, "period", v)));
        chips.add(HistoryMath.label(HistoryMath.COMPARES, L.optString("compare", "prev_year")), () -> pick("Compare with", HistoryMath.COMPARES, L.optString("compare", "prev_year"), v -> set(L, "compare", v)));
        if (!s.past.historyError.isEmpty()) { page.addView(Ui.notice(a, s.past.historyError, true)); Ui.gap(page, 12); }
        if (!note.isEmpty()) { page.addView(Ui.text(a, note, 13.5f, Ui.MUTED)); Ui.gap(page, 8); }
        if (edit || s.past.dirty) layoutButtons(page);
        int missing = 0;
        for (JSONObject st : s.stores) if (s.past.history == null || !s.past.history.latest.containsKey(st.optString("store"))) missing++;
        if (missing > 0 && s.past.history != null) {
            page.addView(Ui.notice(a, (missing == s.stores.size() ? "No past days have arrived yet." : missing + " of " + s.stores.size() + " stores have sent no past days yet.")
                    + " A store sends its daily totals once Koenekt Reporter on its PC is 1.9.106 or later; the first time it sends up to two years.", false));
            Ui.gap(page, 12);
        }
        JSONArray tiles = L.optJSONArray("tiles");
        LinearLayout pair = null;
        int shown = 0;
        if (tiles != null) for (int i = 0; i < tiles.length(); i++) {
            JSONObject t = tiles.optJSONObject(i); if (t == null || !usable(t)) continue;
            boolean small = t.optString("size", t.optString("kind").equals("kpi") ? "s" : "m").equals("s") && !edit;
            LinearLayout box = tile(t, i);
            if (small) {
                if (pair == null) { pair = Ui.row(a); pair.setGravity(Gravity.TOP); page.addView(pair, Ui.fill(a, -1)); Ui.gap(page, 10); }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1); if (pair.getChildCount() > 0) lp.leftMargin = dp(10);
                pair.addView(box, lp);
                if (pair.getChildCount() == 2) pair = null;
            } else {
                if (pair != null) { pair.addView(new android.view.View(a), new LinearLayout.LayoutParams(0, 1, 1)); pair = null; }
                page.addView(box, Ui.fill(a, -1)); Ui.gap(page, 10);
            }
            shown++;
        }
        if (pair != null) pair.addView(new android.view.View(a), new LinearLayout.LayoutParams(0, 1, 1));
        if (shown == 0) page.addView(s.message("This dashboard has no tiles. Customise it to add one, or reset the layout."));
        if (edit) { Ui.gap(page, 6); page.addView(Ui.secondary(a, "Add a tile", this::addTile), Ui.fill(a, -1)); }
    }

    private void layoutButtons(LinearLayout page) {
        LinearLayout row = Ui.row(a);
        TextView save = Ui.primary(a, "Save layout", () -> s.past.saveLayout(() -> { note = "Layout saved. The website shows it too."; edit = false; s.redraw(); }, m -> a.toast(m)));
        Ui.enable(save, s.past.dirty);
        row.addView(save, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.leftMargin = dp(10);
        row.addView(Ui.secondary(a, "Reset layout", () -> Ui.dialog(a).setTitle("Reset the dashboard?")
                .setMessage("The standard dashboard comes back, here and on the website. Your saved layout is removed.")
                .setPositiveButton("Reset", (d, w) -> s.past.resetLayout(() -> { note = "Back to the standard layout."; edit = false; s.redraw(); }, m -> a.toast(m)))
                .setNegativeButton("Cancel", null).show()), lp);
        page.addView(row, Ui.fill(a, -1));
        if (s.past.dirty) { Ui.gap(page, 6); page.addView(Ui.text(a, "You have changed this layout. Save it to keep it here and on the website.", 13, Ui.MUTED)); }
        Ui.gap(page, 14);
    }

    private LinearLayout tile(JSONObject t, int index) {
        LinearLayout box = Ui.card(a);
        String kind = t.optString("kind"); Metric m = Metric.of(t.optString("metric"));
        LocalDate today = PastData.today();
        String[] p = period(t), c = against(t, p);
        List<String> ids = s.past.scopeIds(t.optJSONObject("scope"));
        if (kind.equals("kpi")) {
            Agg now = HistoryMath.agg(s.past.history(), ids, p[0], p[1], s.owner()), before = c == null ? null : HistoryMath.agg(s.past.history(), ids, c[0], c[1], s.owner());
            box.addView(Ui.label(a, m.label));
            TextView v = Ui.title(a, now == null ? "—" : HistoryMath.format(now.value(m), m), 22); v.setSingleLine(true); Ui.gap(box, 6); box.addView(v); Ui.gap(box, 6);
            double ch = HistoryMath.change(now, before, m);
            box.addView(Ui.text(a, s.past.scopeName(t.optJSONObject("scope")) + " · " + HistoryMath.range(p, today), 12.5f, Ui.MUTED));
            String vs = layout().optString("compare", "prev_year");
            if (!Double.isNaN(ch)) box.addView(changeView(ch, m, " vs " + ("prev_period".equals(t.optString("compare", vs)) ? "the period before" : "last year")));
        } else if (kind.equals("trend")) {
            int days = Math.max(1, t.optInt("days", 30)); LocalDate y = today.minusDays(1);
            String from = y.minusDays(days - 1).toString(), to = y.toString();
            box.addView(Ui.label(a, m.label + " by day · last " + days + " days"));
            box.addView(Ui.text(a, s.past.scopeName(t.optJSONObject("scope")), 12.5f, Ui.MUTED)); Ui.gap(box, 8);
            Chart chart = new Chart(a);
            chart.days(new double[][]{HistoryMath.series(s.past.history(), ids, from, to, m, s.owner())}, dayLabels(from, days, today), v -> HistoryMath.format(v, m));
            box.addView(chart, new LinearLayout.LayoutParams(-1, dp(170)));
        } else if (kind.equals("ranking")) {
            String by = t.optString("by", "store");
            box.addView(Ui.label(a, m.label + " by " + HistoryMath.label(BYS, by).toLowerCase(java.util.Locale.ROOT)));
            box.addView(Ui.text(a, HistoryMath.range(p, today), 12.5f, Ui.MUTED)); Ui.gap(box, 8);
            ranking(box, s.past.entities(by), m, p, c, 10);
        } else if (kind.equals("table")) {
            String by = t.optString("by", "store");
            box.addView(Ui.label(a, "By " + HistoryMath.label(BYS, by).toLowerCase(java.util.Locale.ROOT)));
            box.addView(Ui.text(a, HistoryMath.range(p, today), 12.5f, Ui.MUTED)); Ui.gap(box, 4);
            List<Metric> cols = new ArrayList<>();
            for (String k : PastData.strings(t.optJSONArray("metrics"))) if (metricOk(Metric.of(k), s.owner())) cols.add(Metric.of(k));
            for (PastData.Entity e : s.past.entities(by)) {
                Agg now = HistoryMath.agg(s.past.history(), e.ids, p[0], p[1], s.owner()), before = c == null ? null : HistoryMath.agg(s.past.history(), e.ids, c[0], c[1], s.owner());
                box.addView(Ui.divider(a, 0));
                LinearLayout r = Ui.column(a); r.setPadding(0, dp(8), 0, dp(8));
                r.addView(Ui.title(a, e.name, 15));
                StringBuilder b = new StringBuilder();
                for (Metric k : cols) {
                    if (b.length() > 0) b.append('\n');
                    b.append(k.label).append(" ").append(now == null ? "—" : HistoryMath.format(now.value(k), k));
                    String ct = HistoryMath.changeText(HistoryMath.change(now, before, k), k);
                    if (!ct.isEmpty()) b.append(" (").append(ct).append(")");
                }
                r.addView(Ui.text(a, b.toString(), 13, Ui.MUTED));
                box.addView(r);
            }
        } else {   // live
            List<Figures> live = s.all(); Figures tot = StoreMath.total("All", live);
            int answered = 0; for (Figures f : live) if (f.answered) answered++;
            box.addView(Ui.label(a, "Live today · " + answered + " of " + live.size() + " stores answered")); Ui.gap(box, 8);
            LinearLayout row = Ui.row(a); row.setGravity(Gravity.TOP); box.addView(row, Ui.fill(a, -1));
            mini(row, "Sales today", StoreMath.money(tot.sales)); mini(row, "Receipts", StoreMath.count(tot.receipts));
            if (s.owner()) mini(row, "GP % (est.)", StoreMath.format(tot, StoreMath.Metric.GP_PCT));
            Ui.gap(box, 6);
            box.addView(Ui.quiet(a, "Open Live", () -> s.openSection("live")));
        }
        if (edit) editTools(box, t, index);
        return box;
    }
    private void mini(LinearLayout row, String label, String value) {
        LinearLayout c = Ui.column(a); TextView v = Ui.title(a, value, 18); v.setSingleLine(true); c.addView(v); c.addView(Ui.text(a, label, 12, Ui.MUTED));
        row.addView(c, new LinearLayout.LayoutParams(0, -2, 1));
    }
    static String[] dayLabels(String from, int days, LocalDate today) {
        String[] out = new String[days]; LocalDate d = LocalDate.parse(from);
        for (int i = 0; i < days; i++) out[i] = HistoryMath.nice(d.plusDays(i).toString(), today).replaceAll(" \\d{4}$", "");
        return out;
    }
    TextView changeView(double ch, Metric m, String tail) {
        int v = HistoryMath.verdict(ch, m);
        return Ui.text(a, HistoryMath.changeText(ch, m) + tail, 12.5f, v > 0 ? Ui.BRAND : v < 0 ? Ui.DANGER : Ui.MUTED);
    }
    /** Bars, best first (lowest first where lower is better), with the change against the comparison. */
    void ranking(LinearLayout box, List<PastData.Entity> list, Metric m, String[] p, String[] c, int max) {
        List<Object[]> rows = new ArrayList<>();
        for (PastData.Entity e : list) {
            Agg now = HistoryMath.agg(s.past.history(), e.ids, p[0], p[1], s.owner());
            if (now == null || Double.isNaN(now.value(m))) continue;
            Agg before = c == null ? null : HistoryMath.agg(s.past.history(), e.ids, c[0], c[1], s.owner());
            rows.add(new Object[]{e.name, now.value(m), HistoryMath.change(now, before, m)});
        }
        rows.sort((x, y) -> (m.lower ? 1 : -1) * Double.compare((double) x[1], (double) y[1]));
        if (rows.isEmpty()) { box.addView(Ui.text(a, "No figures for this period yet.", 13.5f, Ui.MUTED)); return; }
        double top = 0; for (Object[] r : rows) top = Math.max(top, Math.abs((double) r[1]));
        for (int i = 0; i < rows.size() && i < max; i++) {
            Object[] r = rows.get(i); double v = (double) r[1];
            LinearLayout line = Ui.row(a); line.setPadding(0, dp(5), 0, dp(5));
            TextView name = Ui.text(a, (i + 1) + "  " + r[0], 14, Ui.INK); name.setSingleLine(true); name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            line.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            TextView val = Ui.text(a, HistoryMath.format(v, m), 14, Ui.INK); val.setTypeface(Ui.MONO); line.addView(val);
            box.addView(line);
            LinearLayout track = Ui.row(a); track.setBackground(Ui.shape(Ui.SURFACE2, 1, a));
            android.view.View fill = new android.view.View(a); fill.setBackground(Ui.shape(v < 0 ? Ui.DANGER : Ui.LIME, 1, a));
            float share = top > 0 ? (float) (Math.abs(v) / top) : 0;
            track.addView(fill, new LinearLayout.LayoutParams(0, dp(6), Math.max(0.001f, share)));
            track.addView(new android.view.View(a), new LinearLayout.LayoutParams(0, dp(6), Math.max(0.001f, 1 - share)));
            box.addView(track, Ui.fill(a, -1));
            double ch = (double) r[2];
            if (!Double.isNaN(ch)) box.addView(changeView(ch, m, ""));
        }
    }

    // ---------------------------------------------------------------- customise
    private void editTools(LinearLayout box, JSONObject t, int index) {
        Ui.gap(box, 10); box.addView(Ui.divider(a, 0)); Ui.gap(box, 6);
        HorizontalChips chips = new HorizontalChips(box);
        String kind = t.optString("kind");
        chips.add("Shows: " + HistoryMath.label(KINDS, kind), () -> pick("Tile shows", KINDS, kind, v -> {
            put(t, "kind", v);
            if (!v.equals("live") && !metricOk(Metric.of(t.optString("metric")), s.owner())) put(t, "metric", "turnover");
            if (v.equals("table") && t.optJSONArray("metrics") == null) put(t, "metrics", new JSONArray().put("turnover").put("per_day"));
            if ((v.equals("ranking") || v.equals("table")) && !t.has("by")) put(t, "by", "store");
            changed();
        }));
        if (!kind.equals("live") && !kind.equals("table")) {
            List<Metric> ms = metrics(s.owner()); String[][] opts = new String[ms.size()][];
            for (int i = 0; i < opts.length; i++) opts[i] = new String[]{ms.get(i).key, ms.get(i).label};
            chips.add(Metric.of(t.optString("metric")).label, () -> pick("Figure", opts, t.optString("metric"), v -> { put(t, "metric", v); changed(); }));
        }
        if (kind.equals("table")) chips.add("Columns", () -> pickColumns(t));
        if (kind.equals("kpi") || kind.equals("trend")) {
            List<String[]> sc = s.past.scopes();
            chips.add("For: " + s.past.scopeName(t.optJSONObject("scope")), () -> pick("For", sc.toArray(new String[0][]), PastData.scopeValue(t.optJSONObject("scope")),
                    v -> { put(t, "scope", PastData.scope(v)); changed(); }));
        }
        if (kind.equals("ranking") || kind.equals("table")) chips.add("By: " + HistoryMath.label(BYS, t.optString("by", "store")), () -> pick("By", BYS, t.optString("by", "store"), v -> { put(t, "by", v); changed(); }));
        if (kind.equals("trend")) {
            String[][] ds = {{"7", "7 days"}, {"14", "14 days"}, {"30", "30 days"}, {"60", "60 days"}, {"90", "90 days"}, {"180", "180 days"}, {"365", "365 days"}};
            chips.add(t.optInt("days", 30) + " days", () -> pick("Days", ds, String.valueOf(t.optInt("days", 30)), v -> { put(t, "days", Integer.parseInt(v)); changed(); }));
        }
        if (kind.equals("kpi") || kind.equals("ranking") || kind.equals("table")) {
            String[][] ps = new String[HistoryMath.PERIODS.length + 1][]; ps[0] = new String[]{"", "Dashboard period"};
            System.arraycopy(HistoryMath.PERIODS, 0, ps, 1, HistoryMath.PERIODS.length);
            chips.add(t.optString("period").isEmpty() ? "Dashboard period" : HistoryMath.label(HistoryMath.PERIODS, t.optString("period")),
                    () -> pick("Period", ps, t.optString("period"), v -> { if (v.isEmpty()) t.remove("period"); else put(t, "period", v); changed(); }));
        }
        chips.add("Size: " + (t.optString("size", kind.equals("kpi") ? "s" : "m").equals("s") ? "small" : "wide"),
                () -> pick("Size", SIZES, t.optString("size", kind.equals("kpi") ? "s" : "m"), v -> { put(t, "size", v); changed(); }));
        LinearLayout row = Ui.row(a);
        JSONArray tiles = layout().optJSONArray("tiles");
        row.addView(Ui.quiet(a, "Up", () -> move(index, -1)));
        row.addView(Ui.quiet(a, "Down", () -> move(index, 1)));
        row.addView(Ui.quiet(a, "Remove", () -> { tiles.remove(index); changed(); }));
        box.addView(row);
    }
    private void pickColumns(JSONObject t) {
        List<Metric> ms = metrics(s.owner()); String[] names = new String[ms.size()]; boolean[] on = new boolean[ms.size()];
        List<String> cur = PastData.strings(t.optJSONArray("metrics"));
        for (int i = 0; i < names.length; i++) { names[i] = ms.get(i).label; on[i] = cur.contains(ms.get(i).key); }
        Ui.dialog(a).setTitle("Columns").setMultiChoiceItems(names, on, (d, w, checked) -> on[w] = checked)
                .setPositiveButton("Save", (d, w) -> {
                    JSONArray out = new JSONArray(); for (int i = 0; i < on.length; i++) if (on[i]) out.put(ms.get(i).key);
                    if (out.length() == 0) out.put("turnover");
                    put(t, "metrics", out); changed();
                }).setNegativeButton("Cancel", null).show();
    }
    private void move(int index, int by) {
        JSONArray tiles = layout().optJSONArray("tiles"); int j = index + by;
        if (tiles == null || j < 0 || j >= tiles.length()) return;
        try { Object x = tiles.get(index); tiles.put(index, tiles.get(j)); tiles.put(j, x); } catch (Exception ignored) { }
        changed();
    }
    private void addTile() {
        try {
            JSONArray tiles = layout().optJSONArray("tiles");
            if (tiles == null) { tiles = new JSONArray(); layout().put("tiles", tiles); }
            tiles.put(new JSONObject().put("id", "t" + Long.toString(System.currentTimeMillis(), 36)).put("kind", "kpi")
                    .put("metric", "turnover").put("scope", new JSONObject().put("type", "all")));
        } catch (Exception ignored) { }
        changed();
    }
    private void set(JSONObject l, String key, String v) { put(l, key, v); changed(); }
    private void changed() { s.past.dirty = true; note = ""; render(); }
    private static void put(JSONObject o, String k, Object v) { try { o.put(k, v); } catch (Exception ignored) { } }
    private void pick(String title, String[][] opts, String current, java.util.function.Consumer<String> picked) {
        String[] names = new String[opts.length]; int sel = 0;
        for (int i = 0; i < opts.length; i++) { names[i] = opts[i][1]; if (opts[i][0].equals(current)) sel = i; }
        Ui.choose(a, title, names, sel, w -> picked.accept(opts[w][0]));
    }

    /** A row of filter chips that scrolls sideways. */
    final class HorizontalChips {
        private final LinearLayout row;
        HorizontalChips(LinearLayout parent) {
            android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(a); scroll.setHorizontalScrollBarEnabled(false);
            row = Ui.row(a); scroll.addView(row); parent.addView(scroll); Ui.gap(parent, 12);
        }
        void add(String label, Runnable action) { s.addChip(row, label, action); }
    }
}
