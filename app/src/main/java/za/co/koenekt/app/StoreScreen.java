package za.co.koenekt.app;

import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import za.co.koenekt.app.StoreMath.Figures;
import za.co.koenekt.app.StoreMath.Metric;

/** One store, live from its tills: Today (by hour, payments, events), Items and Tills (cashier on each till now). */
final class StoreScreen {
    private static final String[] SECTIONS = {"Today", "Items", "Tills"};
    private static final String[][] EVENTS = {{"void_line", "Voided lines"}, {"void_sale", "Voided sales"},
            {"price_override", "Price overrides"}, {"discount", "Discounts"}, {"no_sale", "No-sale drawer opens"},
            {"on_hold", "Sales put on hold"}, {"manager_auth", "Manager authorisations"}};
    private final StoresScreen s;
    private final MainActivity a;
    private final String id;
    private int section, itemSort;   // itemSort: 0 sales, 1 quantity, 2 GP

    StoreScreen(StoresScreen stores, String storeId) { s = stores; a = stores.a; id = storeId; }
    private int dp(float n) { return a.dp(n); }

    void render() {
        JSONObject store = s.store(id);
        if (store == null) { s.back(); return; }
        Figures f = s.figures(store);
        a.setBar(f.name, true, Ui.iconButton(a, R.drawable.ic_refresh, "Refresh", s::refreshNow));
        a.onBack(s::back);
        LinearLayout page = a.page(); page.setPadding(dp(16), dp(4), dp(16), dp(28));
        String st = s.state.getOrDefault(id, "asking");
        if (!st.equals("ok")) { page.addView(Ui.notice(a, s.line(f), st.startsWith("error") || st.equals("gone"))); Ui.gap(page, 12); }
        JSONObject r = s.answers.get(id);
        if (r == null) { if (st.equals("asking")) page.addView(Ui.loading(a)); return; }

        LinearLayout row = Ui.row(a); row.setGravity(Gravity.TOP); page.addView(row, Ui.fill(a, -1));
        s.tile(row, "Sales", StoreMath.money(f.sales), false, null);
        s.tile(row, "Receipts", StoreMath.count(f.receipts), true, null);
        Ui.gap(page, 10);
        row = Ui.row(a); row.setGravity(Gravity.TOP); page.addView(row, Ui.fill(a, -1));
        s.tile(row, "Average basket", StoreMath.money(StoreMath.basket(f)), false, null);
        if (s.owner()) s.tile(row, "GP % (est.)", StoreMath.format(f, Metric.GP_PCT), true, null);
        else s.tile(row, "Tills read", f.tillsRead + " of " + f.tillsTotal, true, null);
        Ui.gap(page, 10);
        page.addView(Ui.text(a, "Till figures from " + StoreMath.clock(f.asOf) + " · " + f.tillsRead + " of " + f.tillsTotal + " tills read"
                + (s.owner() && r.optBoolean("gp_estimated") ? " · GP estimated from average cost" : ""), 13, Ui.MUTED));
        Ui.gap(page, 14);

        LinearLayout tabs = Ui.row(a); page.addView(tabs); Ui.gap(page, 14);
        for (int i = 0; i < SECTIONS.length; i++) {
            final int which = i;
            TextView t = Ui.text(a, SECTIONS[i], 14, i == section ? Ui.BRAND : Ui.INK);
            t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); t.setGravity(Gravity.CENTER);
            t.setPadding(dp(16), dp(8), dp(16), dp(8)); t.setMinHeight(dp(40));
            t.setBackground(Ui.pressable(i == section ? Ui.shape(Ui.TINT, 20, a) : Ui.outline(Ui.SURFACE, Ui.LINE, 1, 20, a), 20, a));
            t.setOnClickListener(v -> { section = which; render(); });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2); if (i > 0) p.leftMargin = dp(8);
            tabs.addView(t, p);
        }
        if (section == 0) today(page, f, r); else if (section == 1) items(page, r); else tills(page, r);
    }

    private LinearLayout card(LinearLayout page, String title) {
        page.addView(Ui.title(a, title, 17)); Ui.gap(page, 8);
        LinearLayout c = Ui.card(a); page.addView(c); Ui.gap(page, 18); return c;
    }
    private void pair(LinearLayout c, String label, String value) {
        LinearLayout r = Ui.row(a); r.setPadding(0, dp(5), 0, dp(5));
        r.addView(Ui.text(a, label, 15, Ui.INK), new LinearLayout.LayoutParams(0, -2, 1));
        TextView v = Ui.title(a, value, 15); r.addView(v); c.addView(r);
    }

    private void today(LinearLayout page, Figures f, JSONObject r) {
        LinearLayout c = card(page, "Sales by hour");
        Chart chart = new Chart(a); chart.bars(f.hours); c.addView(chart, Ui.fill(a, 170));
        JSONObject pay = r.optJSONObject("payments");
        if (pay != null) {
            c = card(page, "Payments");
            for (String[] k : new String[][]{{"cash", "Cash"}, {"card", "Card"}, {"account", "On account"}, {"other", "Other"}}) {
                double v = StoresScreen.num(pay, k[0]);
                if (Double.isNaN(v) || (v == 0 && k[0].equals("other"))) continue;
                String share = f.sales > 0 ? "  " + Math.round(100 * v / f.sales) + "%" : "";
                pair(c, k[1], StoreMath.money(v) + share);
            }
        }
        JSONObject money = r.optJSONObject("money");
        if (money != null) {
            c = card(page, "Refunds and money out");
            pair(c, "Refunds (" + money.optInt("refund_count") + ")", StoreMath.money(Math.abs(StoresScreen.num(money, "refunds"))));
            pair(c, "Paid out", StoreMath.money(StoresScreen.num(money, "paid_out")));
            pair(c, "Account payments received", StoreMath.money(StoresScreen.num(money, "account_paid")));
        }
        JSONObject ev = r.optJSONObject("events");
        if (ev != null) {
            c = card(page, "On the tills today");
            for (String[] e : EVENTS) pair(c, e[1], String.valueOf(ev.optInt(e[0])));
        }
    }

    private void items(LinearLayout page, JSONObject r) {
        JSONArray rows = r.optJSONArray("items");
        List<JSONObject> list = new ArrayList<>();
        if (rows != null) for (int i = 0; i < rows.length(); i++) if (rows.optJSONObject(i) != null) list.add(rows.optJSONObject(i));
        String[] sorts = s.owner() ? new String[]{"Sales", "Quantity", "GP"} : new String[]{"Sales", "Quantity"};
        if (itemSort >= sorts.length) itemSort = 0;
        LinearLayout chips = Ui.row(a); page.addView(chips); Ui.gap(page, 12);
        s.addChip(chips, "Sort: " + sorts[itemSort], () -> Ui.choose(a, "Sort items by", sorts, itemSort, w -> { itemSort = w; render(); }));
        String key = itemSort == 0 ? "sales" : itemSort == 1 ? "qty" : "gp_value";
        list.sort((x, y) -> Double.compare(y.optDouble(key, -1e18), x.optDouble(key, -1e18)));
        if (list.isEmpty()) { page.addView(s.message("No items sold yet today.")); return; }
        LinearLayout card = Ui.column(a); card.setBackground(Ui.outline(Ui.SURFACE, Ui.LINE, 1, 20, a)); card.setClipToOutline(true);
        int n = Math.min(list.size(), 60);
        for (int i = 0; i < n; i++) {
            JSONObject it = list.get(i);
            if (i > 0) card.addView(Ui.divider(a, 16));
            String qty = StoreMath.count(it.optDouble("qty"));
            String sub = it.optString("dept") + (it.optString("dept").isEmpty() ? "" : " · ") + qty + " sold"
                    + (s.owner() && !it.isNull("gp_value") ? " · GP " + StoreMath.money(it.optDouble("gp_value")) : "");
            card.addView(Ui.listRow(a, null, it.optString("name", "Item"), sub, StoreMath.money(it.optDouble("sales")), null));
        }
        page.addView(card);
        if (list.size() > n) { Ui.gap(page, 8); page.addView(Ui.text(a, "Top " + n + " of " + list.size() + " items.", 13, Ui.MUTED)); }
        JSONArray depts = r.optJSONArray("departments");
        if (depts != null && depts.length() > 0) {
            Ui.gap(page, 18);
            LinearLayout c = card(page, "Departments");
            for (int i = 0; i < depts.length(); i++) {
                JSONObject d = depts.optJSONObject(i); if (d == null) continue;
                pair(c, d.optString("name", "(none)"), StoreMath.money(d.optDouble("sales"))
                        + (s.owner() && !d.isNull("gp_value") ? "  GP " + StoreMath.money(d.optDouble("gp_value")) : ""));
            }
        }
    }

    private void tills(LinearLayout page, JSONObject r) {
        JSONArray rows = r.optJSONArray("tills");
        if (rows == null || rows.length() == 0) { page.addView(s.message("This store's PC lists no tills.")); return; }
        for (int i = 0; i < rows.length(); i++) {
            JSONObject t = rows.optJSONObject(i); if (t == null) continue;
            String name = t.optString("name"), title = "Till " + t.optString("no");
            if (!name.isEmpty() && !name.equalsIgnoreCase(title)) title += " · " + name;
            LinearLayout c = card(page, title);
            if (!t.optBoolean("read")) {
                c.addView(Ui.text(a, "Not read: " + t.optString("error", "the till did not answer"), 15, Ui.DANGER));
                continue;
            }
            if (t.optBoolean("stale")) { c.addView(Ui.notice(a, "These are the till's last figures: this read failed.", false)); Ui.gap(c, 8); }
            c.addView(Ui.title(a, StoreMath.money(t.optDouble("sales")), 24));
            String last = StoreMath.clock(t.optString("last_sale"));
            c.addView(Ui.text(a, t.optInt("receipts") + " sales · basket " + StoreMath.money(t.optDouble("basket"))
                    + (last.isEmpty() ? "" : " · last sale " + last), 14, Ui.MUTED));
            Ui.gap(c, 10);
            String cashier = t.optString("cashier");
            if (!t.isNull("cashier") && !cashier.isEmpty()) {
                String since = StoreMath.clock(t.optString("cashier_since"));
                pair(c, "On the till now", cashier + (since.isEmpty() ? "" : " since " + since));
            } else if (!t.isNull("logged_out_at")) pair(c, "On the till now", "Logged out at " + StoreMath.clock(t.optString("logged_out_at")));
            else pair(c, "On the till now", "No one logged in");
            JSONArray today = t.optJSONArray("cashiers_today");
            if (today != null && today.length() > 0) {
                List<String> names = new ArrayList<>(); for (int k = 0; k < today.length(); k++) names.add(today.optString(k));
                pair(c, "Worked it today", String.join(", ", names));
            }
            JSONObject ev = t.optJSONObject("events");
            if (ev != null) {
                List<String> bits = new ArrayList<>();
                for (String[] e : EVENTS) if (ev.optInt(e[0]) > 0) bits.add(ev.optInt(e[0]) + " " + e[1].toLowerCase(java.util.Locale.ROOT));
                if (!bits.isEmpty()) { Ui.gap(c, 4); c.addView(Ui.text(a, String.join(" · ", bits), 13.5f, Ui.WARN)); }
            }
        }
    }
}
