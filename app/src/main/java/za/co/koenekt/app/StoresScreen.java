package za.co.koenekt.app;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import za.co.koenekt.app.StoreMath.Figures;
import za.co.koenekt.app.StoreMath.Metric;

/**
 * My stores: live till figures for every store a store login may see, through the Koenekt Link relay.
 * The dashboard is customisable on the phone (tiles, stores shown, store types, grouping, sort order).
 * Holds the shared answers for the store and compare screens, and refreshes every 60 s while one of
 * them is open; never in the background.
 */
final class StoresScreen {
    static final int REFRESH_MS = 60_000;
    private static final int MAX_TRIES = 8;
    final MainActivity a;
    private final Handler timer = new Handler(Looper.getMainLooper());
    /** From GET /api/stores, in the relay's order (by name). */
    final List<JSONObject> stores = new ArrayList<>();
    /** Store id -> its last live answer (kept when the PC goes offline: the relay sends its last one). */
    final Map<String, JSONObject> answers = new HashMap<>();
    /** Store id -> "asking", "ok", "offline", "error: …" or "gone". */
    final Map<String, String> state = new HashMap<>();
    final Dash dash;
    private int run;
    private boolean listLoaded;
    private String listError = "";
    private Runnable view = this::render;     // what is on screen: the dashboard, one store or compare
    private StoreScreen storeScreen;
    private CompareScreen compareScreen;

    StoresScreen(MainActivity activity) { a = activity; dash = new Dash(activity); }
    int dp(float n) { return a.dp(n); }
    boolean owner() { return "owner".equals(a.linkRole()); }

    // ------------------------------------------------------------------ data
    /** Opens the dashboard and starts the 60-second refresh. */
    void show() {
        view = this::render; storeScreen = null; compareScreen = null;
        start();
    }
    private void start() {
        int me = ++run; timer.removeCallbacksAndMessages(null);
        view.run(); refresh(me);
    }
    void refreshNow() { start(); }
    private boolean live(int me) { return me == run && a.showingStores(); }

    private void refresh(int me) {
        a.linkRequest("GET", "/api/stores", null, data -> {
            if (!live(me)) return;
            listLoaded = true; listError = ""; stores.clear();
            JSONArray rows = data.optJSONArray("stores");
            if (rows != null) for (int i = 0; i < rows.length(); i++) if (rows.optJSONObject(i) != null) stores.add(rows.optJSONObject(i));
            for (JSONObject s : stores) if (dash.shows(s.optString("store"))) ask(me, s.optString("store"), 0);
            a.redraw(view);
        }, message -> { if (live(me)) { listError = message; a.redraw(view); } });
        timer.postDelayed(() -> { if (live(me)) refresh(me); }, REFRESH_MS);
    }

    /** One store's live figures; the relay answers "pending" while the shop PC reads its tills. */
    private void ask(int me, String id, int tries) {
        if (!answers.containsKey(id) || tries == 0 && !"ok".equals(state.get(id))) state.put(id, "asking");
        a.linkRequest("POST", "/api/stores/" + id + "/live", new JSONObject(), data -> {
            if (!live(me)) return;
            String status = data.optString("status");
            JSONObject result = data.optJSONObject("result");
            if (status.equals("pending") && tries < MAX_TRIES) {
                timer.postDelayed(() -> { if (live(me)) ask(me, id, tries + 1); }, Math.max(2, data.optInt("retry_after", 3)) * 1000L);
                return;
            }
            if (result != null) answers.put(id, result);
            if (status.equals("ok")) state.put(id, "ok");
            else if (status.equals("offline")) state.put(id, "offline");
            else if (status.equals("unknown")) state.put(id, "gone");
            else if (status.equals("pending")) state.put(id, "error: The shop PC did not answer in time. It tries again in a minute.");
            else state.put(id, "error: " + data.optString("error", "The shop PC could not read its tills."));
            a.redraw(view);
        }, message -> { if (live(me)) { state.put(id, "error: " + message); a.redraw(view); } });
    }

    JSONObject store(String id) {
        for (JSONObject s : stores) if (s.optString("store").equals(id)) return s;
        return null;
    }

    /** The figures of one store, from its last answer (an offline store keeps its last one, marked by state). */
    Figures figures(JSONObject s) {
        String id = s.optString("store");
        Figures f = new Figures(); f.id = id; f.name = s.optString("name", "Store"); f.type = dash.type(id);
        f.online = s.optBoolean("online");
        JSONObject r = answers.get(id);
        if (r == null) return f;
        f.answered = true; f.asOf = r.optString("as_of");
        f.sales = num(r, "sales"); f.receipts = num(r, "receipts");
        f.salesExcl = num(r, "sales_excl"); f.gpValue = num(r, "gp_value");
        f.tillsRead = r.optInt("tills_read"); f.tillsTotal = r.optInt("tills_total");
        JSONObject money = r.optJSONObject("money");
        if (money != null) { f.refunds = Math.abs(num(money, "refunds")); f.paidOut = num(money, "paid_out"); }
        JSONObject ev = r.optJSONObject("events");
        if (ev != null) { f.voids = ev.optInt("void_line") + ev.optInt("void_sale"); f.noSales = ev.optInt("no_sale"); f.overrides = ev.optInt("price_override"); }
        JSONArray hours = r.optJSONArray("hours");
        if (hours != null) for (int i = 0; i < hours.length(); i++) {
            JSONObject h = hours.optJSONObject(i);
            if (h != null && h.optInt("hour", -1) >= 0 && h.optInt("hour") < 24) f.hours[h.optInt("hour")] = h.optDouble("sales", 0);
        }
        return f;
    }
    static double num(JSONObject o, String key) { return o.isNull(key) || !o.has(key) ? Double.NaN : o.optDouble(key, Double.NaN); }

    /** Stores the dashboard shows: not hidden, and inside the chosen scope. */
    List<Figures> shown() {
        List<Figures> out = new ArrayList<>();
        for (JSONObject s : stores) {
            String id = s.optString("store");
            if (!dash.shows(id)) continue;
            Figures f = figures(s);
            if (dash.scope.startsWith("type:") && !f.type.equals(dash.scope.substring(5))) continue;
            if (dash.scope.startsWith("store:") && !id.equals(dash.scope.substring(6))) continue;
            out.add(f);
        }
        return out;
    }
    List<Figures> all() {
        List<Figures> out = new ArrayList<>();
        for (JSONObject s : stores) if (dash.shows(s.optString("store"))) out.add(figures(s));
        return out;
    }
    List<Metric> metrics() {
        List<Metric> out = new ArrayList<>();
        for (Metric m : Metric.values()) if (owner() || !m.needsGp()) out.add(m);
        return out;
    }

    /** What a store row says under its name. */
    String line(Figures f) {
        String st = state.getOrDefault(f.id, "asking");
        JSONObject s = store(f.id);
        if (st.equals("gone")) return "No longer served by its PC";
        if (st.startsWith("error: ")) return st.substring(7);
        if (st.equals("offline") || (s != null && !s.optBoolean("online") && !f.answered)) {
            String since = s == null ? "" : since(s.optDouble("last_seen", 0));
            return "PC offline" + (since.isEmpty() ? "" : " since " + since) + (f.answered ? " · last figures " + StoreMath.clock(f.asOf) : "");
        }
        if (!f.answered) return "Asking the shop PC…";
        StringBuilder b = new StringBuilder();
        b.append(f.tillsRead).append(" of ").append(f.tillsTotal).append(f.tillsTotal == 1 ? " till" : " tills");
        if (f.tillsRead < f.tillsTotal) b.append(" (").append(f.tillsTotal - f.tillsRead).append(" not read)");
        b.append(" · ").append(StoreMath.count(f.receipts)).append(" sales · basket ").append(StoreMath.money(StoreMath.basket(f)));
        if (owner() && !Double.isNaN(StoreMath.gpPct(f))) b.append(" · GP ").append(StoreMath.format(f, Metric.GP_PCT));
        return b.toString();
    }
    static String since(double epochSeconds) {
        if (epochSeconds <= 0) return "";
        return new java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT).format(new java.util.Date((long) (epochSeconds * 1000)));
    }

    // ------------------------------------------------------------------ dashboard
    private void render() {
        view = this::render;
        a.setBar("My stores", false,
                Ui.iconButton(a, R.drawable.ic_compare, "Compare stores", () -> openCompare(Metric.SALES)),
                Ui.iconButton(a, R.drawable.ic_tune, "Customise dashboard", this::customise),
                Ui.iconButton(a, R.drawable.ic_refresh, "Refresh", this::refreshNow));
        LinearLayout page = a.page(); page.setPadding(dp(16), dp(4), dp(16), dp(28));
        if (!listLoaded) {
            if (listError.isEmpty()) page.addView(Ui.loading(a)); else page.addView(Ui.notice(a, listError, true));
            return;
        }
        if (stores.isEmpty()) {
            page.addView(message("No stores yet. A store appears here once its shop PC is approved for your login. Ask Koenekt if one is missing."));
            return;
        }
        HorizontalScrollView scroll = new HorizontalScrollView(a); scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = Ui.row(a); scroll.addView(chips); page.addView(scroll); Ui.gap(page, 14);
        addChip(chips, scopeLabel(), this::pickScope);
        addChip(chips, "Sort: " + dash.sort.label, () -> {
            List<Metric> ms = metrics(); String[] names = new String[ms.size()];
            for (int i = 0; i < names.length; i++) names[i] = ms.get(i).label;
            Ui.choose(a, "Sort stores by", names, Math.max(0, ms.indexOf(dash.sort)), w -> { dash.sort = ms.get(w); dash.save(); render(); });
        });
        if (!listError.isEmpty()) { page.addView(Ui.notice(a, listError, true)); Ui.gap(page, 12); }

        List<Figures> shown = shown();
        Figures total = StoreMath.total("All", shown);
        LinearLayout grid = Ui.column(a); page.addView(grid);
        LinearLayout row = null; int i = 0;
        for (Metric m : dash.tiles) {
            if (m.needsGp() && !owner()) continue;
            if (i % 2 == 0) { row = Ui.row(a); row.setGravity(Gravity.TOP); if (i > 0) Ui.gap(grid, 10); grid.addView(row, Ui.fill(a, -1)); }
            tile(row, m.label, StoreMath.format(total, m), i % 2 == 1, () -> openCompare(m));
            i++;
        }
        if (row != null && i % 2 == 1) row.addView(new android.view.View(a), new LinearLayout.LayoutParams(0, 1, 1));
        Ui.gap(page, 12);
        int online = 0, answered = 0;
        for (Figures f : shown) { if ("ok".equals(state.get(f.id))) answered++; JSONObject s = store(f.id); if (s != null && s.optBoolean("online")) online++; }
        String at = StoreMath.clock(total.asOf);
        page.addView(Ui.text(a, answered + " of " + shown.size() + (shown.size() == 1 ? " store" : " stores") + " live"
                + (online < shown.size() ? " · " + (shown.size() - online) + " PC offline" : "")
                + (at.isEmpty() ? "" : " · till figures from " + at) + " · refreshes every minute", 13, Ui.MUTED));
        Ui.gap(page, 16);

        Map<String, List<Figures>> groups = StoreMath.byType(shown);
        if (dash.group && !(groups.size() == 1 && groups.containsKey(StoreMath.NO_TYPE))) {
            double all = Double.isNaN(total.sales) ? 0 : total.sales;
            for (Map.Entry<String, List<Figures>> g : groups.entrySet()) {
                Figures t = StoreMath.total(g.getKey(), g.getValue());
                String share = all > 0 && !Double.isNaN(t.sales) ? " · " + Math.round(100 * t.sales / all) + "% of sales" : "";
                TextView head = Ui.title(a, g.getKey(), 17); page.addView(head); Ui.gap(page, 2);
                page.addView(Ui.text(a, g.getValue().size() + (g.getValue().size() == 1 ? " store · " : " stores · ")
                        + StoreMath.money(t.sales) + share
                        + (owner() && !Double.isNaN(StoreMath.gpPct(t)) ? " · GP " + StoreMath.format(t, Metric.GP_PCT) : ""), 13.5f, Ui.MUTED));
                Ui.gap(page, 8);
                page.addView(storeCard(StoreMath.rank(g.getValue(), dash.sort))); Ui.gap(page, 18);
            }
        } else page.addView(storeCard(StoreMath.rank(shown, dash.sort)));
        if (dash.types().isEmpty() && shown.size() > 1) {
            Ui.gap(page, 4);
            page.addView(Ui.text(a, "Tip: give each store a type (Customise → Store types) to group them and compare type against type.", 13, Ui.MUTED));
        }
    }

    private LinearLayout storeCard(List<Figures> list) {
        LinearLayout card = Ui.column(a); card.setBackground(Ui.outline(Ui.SURFACE, Ui.LINE, 1, 20, a)); card.setClipToOutline(true);
        for (int i = 0; i < list.size(); i++) {
            Figures f = list.get(i);
            if (i > 0) card.addView(Ui.divider(a, 16));
            boolean ok = "ok".equals(state.get(f.id));
            String value = dash.sort == Metric.SALES || !f.answered ? StoreMath.money(f.sales) : StoreMath.format(f, dash.sort);
            card.addView(Ui.listRow(a, Ui.dot(a, ok ? Ui.LIME : Ui.OUT), f.name, line(f), value, () -> openStore(f.id)));
        }
        return card;
    }

    void tile(LinearLayout row, String label, String value, boolean right, Runnable action) {
        LinearLayout t = Ui.column(a); t.setPadding(dp(14), dp(12), dp(14), dp(12));
        t.setBackground(Ui.pressable(row.getChildCount() == 0 && !right && label.equals(Metric.SALES.label)
                ? Ui.shape(Ui.TINT, 18, a) : Ui.outline(Ui.SURFACE, Ui.LINE, 1, 18, a), 18, a));
        TextView v = Ui.title(a, value, 22); v.setSingleLine(true); t.addView(v);
        t.addView(Ui.text(a, label, 13, Ui.MUTED));
        t.setContentDescription(value + " " + label);
        if (action != null) t.setOnClickListener(x -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        if (row.getChildCount() > 0) p.leftMargin = dp(10);
        row.addView(t, p);
    }

    void addChip(LinearLayout chips, String label, Runnable action) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        if (chips.getChildCount() > 0) p.leftMargin = dp(8);
        chips.addView(Ui.chip(a, label, action), p);
    }

    TextView message(String text) {
        TextView t = Ui.text(a, text, 15, Ui.MUTED); t.setGravity(Gravity.CENTER); t.setPadding(dp(16), dp(48), dp(16), dp(48)); return t;
    }

    private String scopeLabel() {
        if (dash.scope.startsWith("type:")) return dash.scope.substring(5);
        if (dash.scope.startsWith("store:")) { JSONObject s = store(dash.scope.substring(6)); if (s != null) return s.optString("name"); }
        return "All stores";
    }
    private void pickScope() {
        List<String> keys = new ArrayList<>(), names = new ArrayList<>();
        keys.add(""); names.add("All stores");
        for (String t : dash.types()) { keys.add("type:" + t); names.add(t + " (type)"); }
        for (JSONObject s : stores) if (dash.shows(s.optString("store"))) { keys.add("store:" + s.optString("store")); names.add(s.optString("name")); }
        Ui.choose(a, "Show", names.toArray(new String[0]), Math.max(0, keys.indexOf(dash.scope)), w -> { dash.scope = keys.get(w); dash.save(); render(); });
    }

    // ------------------------------------------------------------------ customise
    private void customise() {
        String[] items = {"Tiles on the dashboard", "Stores to show", "Store types", (dash.group ? "Turn off" : "Turn on") + " grouping by store type"};
        new AlertDialog.Builder(a).setTitle("Customise My stores").setItems(items, (d, w) -> {
            if (w == 0) pickTiles(); else if (w == 1) pickStores(); else if (w == 2) pickTypeStore();
            else { dash.group = !dash.group; dash.save(); render(); }
        }).setNegativeButton("Close", null).show();
    }
    private void pickTiles() {
        List<Metric> ms = metrics(); String[] names = new String[ms.size()]; boolean[] on = new boolean[ms.size()];
        for (int i = 0; i < names.length; i++) { names[i] = ms.get(i).label; on[i] = dash.tiles.contains(ms.get(i)); }
        new AlertDialog.Builder(a).setTitle("Tiles on the dashboard").setMultiChoiceItems(names, on, (d, w, checked) -> on[w] = checked)
                .setPositiveButton("Save", (d, w) -> {
                    dash.tiles.clear();
                    for (int i = 0; i < on.length; i++) if (on[i]) dash.tiles.add(ms.get(i));
                    if (dash.tiles.isEmpty()) dash.tiles.add(Metric.SALES);
                    dash.save(); render();
                }).setNegativeButton("Cancel", null).show();
    }
    private void pickStores() {
        String[] names = new String[stores.size()]; boolean[] on = new boolean[stores.size()];
        for (int i = 0; i < names.length; i++) { names[i] = stores.get(i).optString("name"); on[i] = dash.shows(stores.get(i).optString("store")); }
        new AlertDialog.Builder(a).setTitle("Stores to show").setMultiChoiceItems(names, on, (d, w, checked) -> on[w] = checked)
                .setPositiveButton("Save", (d, w) -> {
                    for (int i = 0; i < on.length; i++) dash.setShown(stores.get(i).optString("store"), on[i]);
                    if (dash.scope.startsWith("store:") && !dash.shows(dash.scope.substring(6))) dash.scope = "";
                    dash.save(); refreshNow();
                }).setNegativeButton("Cancel", null).show();
    }
    private void pickTypeStore() {
        String[] names = new String[stores.size()];
        for (int i = 0; i < names.length; i++) {
            String id = stores.get(i).optString("store");
            names[i] = stores.get(i).optString("name") + "  ·  " + dash.type(id);
        }
        new AlertDialog.Builder(a).setTitle("Store types").setItems(names, (d, w) -> pickType(stores.get(w)))
                .setNegativeButton("Done", null).show();
    }
    private void pickType(JSONObject s) {
        String id = s.optString("store");
        List<String> choices = new ArrayList<>(dash.types());
        for (String t : new String[]{"Liquor store", "Bottle store", "Tavern", "Wholesale", "Supermarket", "Butchery"}) if (!choices.contains(t)) choices.add(t);
        choices.add("Other type…"); choices.add(StoreMath.NO_TYPE);
        new AlertDialog.Builder(a).setTitle(s.optString("name")).setItems(choices.toArray(new String[0]), (d, w) -> {
            String pick = choices.get(w);
            if (pick.equals("Other type…")) {
                EditText input = new EditText(a); input.setSingleLine(true); input.setHint("e.g. Express");
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
                FrameLayout box = new FrameLayout(a); box.setPadding(dp(24), dp(8), dp(24), 0); box.addView(input);
                new AlertDialog.Builder(a).setTitle("Store type").setView(box).setPositiveButton("Save", (d2, w2) -> {
                    dash.setType(id, input.getText().toString()); dash.save(); pickTypeStore(); render();
                }).setNegativeButton("Cancel", null).show();
                return;
            }
            dash.setType(id, pick.equals(StoreMath.NO_TYPE) ? "" : pick); dash.save(); render(); pickTypeStore();
        }).setNegativeButton("Cancel", null).show();
    }

    // ------------------------------------------------------------------ navigation
    void openStore(String id) {
        storeScreen = new StoreScreen(this, id); compareScreen = null; view = storeScreen::render; view.run();
    }
    void openCompare(Metric metric) {
        compareScreen = new CompareScreen(this, metric); storeScreen = null; view = compareScreen::render; view.run();
    }
    void back() { storeScreen = null; compareScreen = null; render(); }

    // ------------------------------------------------------------------ settings on this phone
    /** The dashboard's layout, per store login, kept on this phone only (no figures). */
    static final class Dash {
        private final MainActivity a;
        final List<Metric> tiles = new ArrayList<>();
        private final Set<String> hidden = new TreeSet<>();
        private final Map<String, String> types = new HashMap<>();
        boolean group = true;
        Metric sort = Metric.SALES;
        String scope = "";

        Dash(MainActivity activity) {
            a = activity;
            try {
                JSONObject o = new JSONObject(prefs().getString(key(), "{}"));
                JSONArray t = o.optJSONArray("tiles");
                if (t != null) for (int i = 0; i < t.length(); i++) try { tiles.add(Metric.valueOf(t.optString(i))); } catch (IllegalArgumentException ignored) { }
                JSONArray h = o.optJSONArray("hidden");
                if (h != null) for (int i = 0; i < h.length(); i++) hidden.add(h.optString(i));
                JSONObject ty = o.optJSONObject("types");
                if (ty != null) for (java.util.Iterator<String> k = ty.keys(); k.hasNext(); ) { String id = k.next(); types.put(id, ty.optString(id)); }
                group = o.optBoolean("group", true); scope = o.optString("scope", "");
                try { sort = Metric.valueOf(o.optString("sort", "SALES")); } catch (IllegalArgumentException ignored) { }
            } catch (Exception ignored) { /* a damaged layout falls back to the default */ }
            if (tiles.isEmpty()) { tiles.add(Metric.SALES); tiles.add(Metric.GP_PCT); tiles.add(Metric.RECEIPTS); tiles.add(Metric.BASKET); }
        }
        private android.content.SharedPreferences prefs() { return a.getPreferences(Context.MODE_PRIVATE); }
        private String key() { return "stores_dash:" + a.linkUser(); }

        boolean shows(String id) { return !hidden.contains(id); }
        void setShown(String id, boolean on) { if (on) hidden.remove(id); else hidden.add(id); }
        String type(String id) { String t = types.get(id); return t == null || t.isBlank() ? StoreMath.NO_TYPE : t; }
        void setType(String id, String type) {
            String t = type == null ? "" : type.trim();
            if (t.length() > 40) t = t.substring(0, 40);
            if (t.isEmpty()) types.remove(id); else types.put(id, t);
            if (scope.startsWith("type:") && !types().contains(scope.substring(5))) scope = "";
        }
        Set<String> types() { Set<String> out = new TreeSet<>(String.CASE_INSENSITIVE_ORDER); out.addAll(types.values()); return out; }

        void save() {
            try {
                JSONObject o = new JSONObject();
                JSONArray t = new JSONArray(); for (Metric m : tiles) t.put(m.name());
                o.put("tiles", t).put("hidden", new JSONArray(hidden)).put("types", new JSONObject(types))
                        .put("group", group).put("sort", sort.name()).put("scope", scope);
                prefs().edit().putString(key(), o.toString()).apply();
            } catch (Exception ignored) { /* layout only; nothing to lose */ }
        }
    }
}
