package za.co.koenekt.app;

import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * My stores → Reports (owners): Range, Ordering, ROSI and Turnover & tenders, run on the shop PC by Koenekt
 * Reporter 1.9.106+ through the relay (POST /api/stores/{id}/{report}); the same reports as the PC and the website.
 * The relay answers "pending" while the PC works (a big shop can take minutes); the phone asks again every few
 * seconds while this section is open. Answers stay in memory only.
 */
final class ReportsScreen {
    private static final String[][] KINDS = {{"range", "Range report"}, {"order", "Ordering"}, {"rosi", "ROSI"}, {"turnover", "Turnover & tenders"}};
    private static final String[][] QUICK = {{"mtd", "Month to date"}, {"last_month", "Last month"}, {"last_30", "Last 30 days"}, {"yesterday", "Yesterday"}};
    private static final int SHOW = 40;
    private final StoresScreen s;
    private final MainActivity a;
    private final Handler timer = new Handler(Looper.getMainLooper());
    private String store = "", kind = "range", from = "", to = "", by = "group", cover = "";
    private boolean month, all;
    /** Report key -> {"state": asking|ok|error, "result"|"error", "started"}. */
    private final Map<String, JSONObject> results = new HashMap<>();

    ReportsScreen(StoresScreen stores) { s = stores; a = stores.a; }
    private int dp(float n) { return a.dp(n); }
    private String key() { return String.join("|", store, kind, from, to, kind.equals("order") ? cover : "", kind.equals("rosi") ? by + month : ""); }

    void render() {
        a.setBar("Reports", false);
        LinearLayout page = a.page(); page.setPadding(dp(16), dp(4), dp(16), dp(28));
        if (!s.ready(page)) return;
        if (!s.owner()) { page.addView(s.message("Reports are for store owners.")); return; }
        if (s.store(store) == null) store = s.stores.get(0).optString("store");
        if (from.isEmpty()) { String[] p = HistoryMath.period("mtd", PastData.today()); from = p[0]; to = PastData.today().toString(); }
        DashboardScreen.HorizontalChips chips = new DashboardScreen(s).new HorizontalChips(page);
        chips.add(HistoryMath.label(KINDS, kind), () -> pick("Report", KINDS, kind, v -> {
            kind = v; all = false;
            if (kind.equals("order") && to.compareTo(PastData.today().toString()) >= 0) { LocalDate y = PastData.today().minusDays(1); to = y.toString(); from = y.minusDays(27).toString(); }
            render(); }));
        String[][] st = new String[s.stores.size()][];
        for (int i = 0; i < st.length; i++) st[i] = new String[]{s.stores.get(i).optString("store"), s.stores.get(i).optString("name")};
        chips.add(s.store(store).optString("name"), () -> pick("Store", st, store, v -> { store = v; render(); }));
        LocalDate today = PastData.today();
        chips.add(HistoryMath.range(new String[]{from, to}, today), this::pickDates);
        if (kind.equals("order")) chips.add(cover.isEmpty() ? "Cover: the shop's" : "Cover: " + cover + " days", this::pickCover);
        if (kind.equals("rosi")) {
            chips.add(by.equals("dept") ? "By department" : "By group", () -> pick("By", new String[][]{{"group", "Group"}, {"dept", "Department"}}, by, v -> { by = v; render(); }));
            chips.add(month ? "Month sheet (× 12)" : "Annualised by days", () -> { month = !month; render(); });
        }
        page.addView(Ui.primary(a, "Run " + (kind.equals("rosi") ? "ROSI" : HistoryMath.label(KINDS, kind).toLowerCase(java.util.Locale.ROOT)), this::run), Ui.fill(a, -1));
        Ui.gap(page, 14);
        JSONObject box = results.get(key());
        if (box == null) {
            page.addView(Ui.text(a, "Choose the report, store and dates, then run it. It runs on the shop PC, the same as the report in Koenekt Reporter; a big shop can take a few minutes.", 14, Ui.MUTED));
            return;
        }
        String state = box.optString("state");
        if (state.equals("asking")) {
            long secs = (System.currentTimeMillis() - box.optLong("started")) / 1000;
            page.addView(Ui.notice(a, "The shop PC is working on it · " + secs + " s", false));
            return;
        }
        if (state.equals("error")) { page.addView(Ui.notice(a, box.optString("error"), true)); return; }
        JSONObject r = box.optJSONObject("result");
        if (r != null) show(page, r);
    }

    private void run() {
        String k = key();
        JSONObject box = new JSONObject();
        try { box.put("state", "asking").put("started", System.currentTimeMillis()); } catch (Exception ignored) { }
        results.put(k, box);
        ask(k, box, 0);
        render();
    }
    private JSONObject params() {
        JSONObject p = new JSONObject();
        try {
            p.put("from", from).put("to", to);
            if (kind.equals("order") && !cover.isEmpty()) p.put("cover_days", Integer.parseInt(cover));
            if (kind.equals("rosi")) { p.put("by", by); if (month) p.put("month_mode", true); }
        } catch (Exception ignored) { }
        return p;
    }
    private void ask(String k, JSONObject box, int tries) {
        String id = store, what = kind;
        JSONObject body = new JSONObject();
        try { body.put("params", params()); } catch (Exception ignored) { }
        a.linkRequest("POST", "/api/stores/" + id + "/" + what, body, data -> {
            if (results.get(k) != box) return;
            String status = data.optString("status");
            try {
                if (status.equals("pending") && tries < 200) {
                    timer.postDelayed(() -> { if (results.get(k) == box) ask(k, box, tries + 1); }, Math.max(3, data.optInt("retry_after", 5)) * 1000L);
                    if (showing()) render();
                    return;
                }
                if (status.equals("ok")) box.put("state", "ok").put("result", data.optJSONObject("result"));
                else if (status.equals("offline")) box.put("state", "error").put("error", "The shop PC is offline. Reports run on the shop PC, so it must be on with Koenekt Reporter running.");
                else {
                    String err = data.optString("error", "The shop PC could not run this report.");
                    if (status.equals("pending")) err = "The shop PC has not finished. Try again in a few minutes.";
                    else if (err.contains("cannot answer")) err = "This store's Koenekt Reporter is older than 1.9.106, which brings reports to the app. It updates itself overnight.";
                    box.put("state", "error").put("error", err);
                }
            } catch (Exception ignored) { }
            if (showing()) render();
        }, message -> {
            if (results.get(k) != box) return;
            try { box.put("state", "error").put("error", message); } catch (Exception ignored) { }
            if (showing()) render();
        });
    }
    private boolean showing() { return a.showingStores() && "reports".equals(s.section); }

    // ---------------------------------------------------------------- results
    private void show(LinearLayout page, JSONObject r) {
        LocalDate today = PastData.today();
        page.addView(Ui.text(a, r.optString("name") + " · " + HistoryMath.range(new String[]{r.optString("date_from").substring(0, 10), r.optString("date_to").substring(0, 10)}, today)
                + (r.optString("made_at").length() >= 16 ? " · run on the shop PC at " + r.optString("made_at").substring(11, 16) : ""), 13, Ui.MUTED));
        Ui.gap(page, 10);
        switch (r.optString("report", kind)) {
            case "range": range(page, r); break;
            case "order": order(page, r); break;
            case "rosi": rosi(page, r); break;
            default: turnover(page, r);
        }
    }
    private static double n(JSONObject o, String k) { return StoresScreen.num(o, k); }
    private static String money(JSONObject o, String k) { return StoreMath.money(n(o, k)); }
    private static String pct(double v) { return Double.isNaN(v) ? "—" : String.format(java.util.Locale.ROOT, "%.1f%%", v); }

    private void tiles(LinearLayout page, String[][] list) {
        LinearLayout row = null;
        for (int i = 0; i < list.length; i++) {
            if (i % 2 == 0) { row = Ui.row(a); row.setGravity(Gravity.TOP); if (i > 0) Ui.gap(page, 10); page.addView(row, Ui.fill(a, -1)); }
            s.tile(row, list[i][0], list[i][1], i % 2 == 1, null);
        }
        if (row != null && list.length % 2 == 1) row.addView(new android.view.View(a), new LinearLayout.LayoutParams(0, 1, 1));
        Ui.gap(page, 14);
    }
    private LinearLayout section(LinearLayout page, String title) {
        LinearLayout card = Ui.card(a); card.addView(Ui.label(a, title)); Ui.gap(card, 6);
        page.addView(card, Ui.fill(a, -1)); Ui.gap(page, 12); return card;
    }
    private void line(LinearLayout card, String title, String sub, String value) {
        card.addView(Ui.divider(a, 0));
        LinearLayout r = Ui.row(a); r.setPadding(0, dp(8), 0, dp(8)); r.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words = Ui.column(a);
        TextView t = Ui.text(a, title, 14.5f, Ui.INK); words.addView(t);
        if (sub != null && !sub.isEmpty()) words.addView(Ui.text(a, sub, 12.5f, Ui.MUTED));
        r.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        TextView v = Ui.text(a, value, 14, Ui.INK); v.setTypeface(Ui.MONO); v.setPadding(dp(8), 0, 0, 0); r.addView(v);
        card.addView(r);
    }
    private void more(LinearLayout card, int total) {
        if (total > SHOW) card.addView(Ui.quiet(a, all ? "Show the first " + SHOW : "Show all " + total, () -> { all = !all; render(); }));
    }

    private void range(LinearLayout page, JSONObject r) {
        tiles(page, new String[][]{{"Turnover", money(r, "turnover_incl")}, {"Sales excl. VAT", money(r, "sales_excl")}, {"GP", money(r, "gp_value")},
                {"GP %", pct(n(r, "gp_pct"))}, {"Days traded", StoreMath.count(n(r, "days_with_sales"))}, {"Lines sold", StoreMath.count(n(r, "items_sold"))}});
        JSONArray days = r.optJSONArray("days");
        if (days != null && days.length() > 1) {
            LinearLayout c = section(page, "Turnover by day");
            double[] v = new double[days.length()]; String[] labels = new String[days.length()];
            for (int i = 0; i < v.length; i++) { JSONObject d = days.optJSONObject(i); v[i] = n(d, "turnover"); labels[i] = HistoryMath.nice(d.optString("date").substring(0, 10), PastData.today()).replaceAll(" \\d{4}$", ""); }
            Chart chart = new Chart(a); chart.days(new double[][]{v}, labels, StoreMath::money);
            c.addView(chart, new LinearLayout.LayoutParams(-1, dp(170)));
        }
        if (days != null) {
            LinearLayout c = section(page, "Days");
            for (int i = 0; i < days.length(); i++) { JSONObject d = days.optJSONObject(i);
                line(c, HistoryMath.nice(d.optString("date").substring(0, 10), PastData.today()) + " " + d.optString("weekday"),
                        "GP " + money(d, "gp_value") + " · " + pct(n(d, "gp_pct")) + (d.optString("holiday").isEmpty() ? "" : " · " + d.optString("holiday")), money(d, "turnover")); }
        }
        for (String[] part : new String[][]{{"departments", "Departments"}, {"groups", "Groups"}}) {
            JSONArray list = r.optJSONArray(part[0]); if (list == null || list.length() == 0) continue;
            LinearLayout c = section(page, part[1]);
            for (int i = 0; i < list.length(); i++) { JSONObject d = list.optJSONObject(i);
                line(c, d.optString("name"), "GP " + money(d, "gp_value") + " · " + pct(n(d, "gp_pct")) + " · " + pct(n(d, "share_pct")) + " of sales", money(d, "incl")); }
        }
        JSONArray low = r.optJSONArray("low_gp");
        if (low != null && low.length() > 0) {
            LinearLayout c = section(page, "GP below " + Math.round(r.optDouble("low_gp_threshold", 20)) + "%");
            for (int i = 0; i < low.length() && i < 30; i++) { JSONObject d = low.optJSONObject(i);
                line(c, d.optString("name"), d.optString("itemno") + " · " + StoreMath.qty(n(d, "qty")) + " sold · GP " + money(d, "gp_value"), pct(n(d, "gp_pct"))); }
        }
        JSONArray items = r.optJSONArray("items");
        if (items != null) {
            LinearLayout c = section(page, "Items, best sellers first");
            int show = all ? items.length() : Math.min(SHOW, items.length());
            for (int i = 0; i < show; i++) { JSONObject d = items.optJSONObject(i);
                line(c, d.optString("name"), d.optString("itemno") + " · " + StoreMath.qty(n(d, "qty")) + " sold · GP " + money(d, "gp_value") + " · " + pct(n(d, "gp_pct")), money(d, "incl")); }
            more(c, items.length());
            if (r.optInt("items_total") > items.length())
                c.addView(Ui.text(a, "The " + items.length() + " best sellers of " + r.optInt("items_total") + " lines are here; the full list is in the Range report on the shop PC.", 12.5f, Ui.MUTED));
        }
    }

    private void order(LinearLayout page, JSONObject r) {
        tiles(page, new String[][]{{"Lines to order", StoreMath.count(n(r, "lines"))}, {"Order value", money(r, "order_value")},
                {"Sold out while selling", StoreMath.count(n(r, "stockouts"))}, {"Cover", r.optInt("cover_days") + " days"}});
        if (r.optBoolean("ignore_soh")) { page.addView(Ui.notice(a, "Stock on hand is ignored: every line is ordered for full cover.", false)); Ui.gap(page, 12); }
        JSONArray rows = r.optJSONArray("rows");
        if (rows != null) {
            LinearLayout c = section(page, "Suggested order, most urgent first");
            int show = all ? rows.length() : Math.min(SHOW, rows.length());
            for (int i = 0; i < show; i++) { JSONObject d = rows.optJSONObject(i);
                double cov = n(d, "cover");
                line(c, d.optString("name") + (d.optBoolean("stockout") ? " · sold out" : ""),
                        d.optString("dept") + " · on hand " + StoreMath.qty(n(d, "soh")) + " · " + (Double.isNaN(n(d, "rate")) ? "—" : String.format(java.util.Locale.ROOT, "%.2f", n(d, "rate"))) + " a day"
                                + (Double.isNaN(cov) ? "" : " · " + Math.round(cov) + " days left") + " · " + money(d, "order_value"),
                        StoreMath.qty(n(d, "suggest")));
            }
            more(c, rows.length());
        }
        JSONArray neg = r.optJSONArray("negative_rows");
        if (neg != null && neg.length() > 0) {
            LinearLayout c = section(page, "Negative stock (not ordered)");
            c.addView(Ui.text(a, "The till believes these are below zero, usually goods sold before they were received. Check them before ordering.", 13, Ui.MUTED));
            for (int i = 0; i < neg.length() && i < 40; i++) { JSONObject d = neg.optJSONObject(i);
                line(c, d.optString("name"), "on hand " + StoreMath.qty(d.has("raw_soh") ? n(d, "raw_soh") : n(d, "soh")), StoreMath.qty(n(d, "suggest"))); }
        }
    }

    private void rosi(LinearLayout page, JSONObject r) {
        JSONObject t = r.optJSONObject("total"); if (t == null) t = new JSONObject();
        tiles(page, new String[][]{{"ROSI", Double.isNaN(n(t, "rosi")) ? "—" : String.valueOf(Math.round(n(t, "rosi")))}, {"GP %", pct(n(t, "gp_pct"))},
                {"Stock turns (a year)", Double.isNaN(n(t, "turns")) ? "—" : String.format(java.util.Locale.ROOT, "%.1f", n(t, "turns"))},
                {"Stock days", Double.isNaN(n(t, "stock_days")) ? "—" : String.valueOf(Math.round(n(t, "stock_days")))}});
        if (r.optBoolean("stock_is_stale")) { page.addView(Ui.notice(a, "The stock is today's, not the stock at the end of the period, so turns and ROSI for a past period drift.", false)); Ui.gap(page, 12); }
        page.addView(Ui.text(a, "ROSI = stock turns × GP %. It rewards stock that earns a margin and also moves.", 13, Ui.MUTED)); Ui.gap(page, 12);
        JSONArray rows = r.optJSONArray("rows");
        if (rows == null) return;
        LinearLayout c = section(page, ("dept".equals(r.optString("by")) ? "By department" : "By group") + ", best first");
        for (int i = 0; i < rows.length(); i++) { JSONObject d = rows.optJSONObject(i);
            line(c, d.optString("name"), "Stock " + money(d, "stock") + " · GP " + pct(n(d, "gp_pct")) + " · "
                    + (Double.isNaN(n(d, "turns")) ? "—" : String.format(java.util.Locale.ROOT, "%.1f", n(d, "turns"))) + " turns",
                    Double.isNaN(n(d, "rosi")) ? "—" : String.valueOf(Math.round(n(d, "rosi")))); }
    }

    private void turnover(LinearLayout page, JSONObject r) {
        JSONObject t = r.optJSONObject("total"); if (t == null) t = new JSONObject();
        tiles(page, new String[][]{{"Sales", money(t, "sales")}, {"Money in", money(t, "money_in")}, {"Paid out", StoreMath.money(Math.abs(StoresScreen.num(t, "paid_out")))},
                {"Bankable", money(t, "bankable")}, {"Accounts moved", money(t, "account_movement")}, {"Receipts", StoreMath.count(n(r, "receipts"))}});
        page.addView(Ui.text(a, "Sales is what went out of the door, paid for or not. Money in is what came in, whatever it was for.", 13, Ui.MUTED)); Ui.gap(page, 12);
        JSONArray tills = r.optJSONArray("tills");
        if (tills != null) {
            LinearLayout c = section(page, "Per till");
            for (int i = 0; i < tills.length(); i++) { JSONObject d = tills.optJSONObject(i);
                StringBuilder b = new StringBuilder();
                for (String k : new String[]{"cash", "card", "eft", "cheque", "voucher", "other"}) {
                    double v = n(d, k); if (Double.isNaN(v) || Math.abs(v) < 0.005) continue;
                    if (b.length() > 0) b.append(" · ");
                    b.append(Character.toUpperCase(k.charAt(0))).append(k.substring(1)).append(" ").append(StoreMath.money(v));
                }
                line(c, "Till " + d.optString("till"), "Sales " + money(d, "sales") + (b.length() > 0 ? " · " + b : ""), money(d, "bankable"));
            }
        }
        JSONArray po = r.optJSONArray("paid_outs");
        if (po != null && po.length() > 0) {
            LinearLayout c = section(page, "Paid outs");
            for (int i = 0; i < po.length() && i < 60; i++) { JSONObject d = po.optJSONObject(i);
                line(c, (d.optString("receiver") + " " + d.optString("reason")).trim(), d.optString("date").substring(0, Math.min(10, d.optString("date").length())) + " · Till " + d.optString("till"),
                        StoreMath.money(Math.abs(StoresScreen.num(d, "total")))); }
        }
    }

    // ---------------------------------------------------------------- choices
    private void pickDates() {
        String[][] opts = new String[QUICK.length + 1][];
        System.arraycopy(QUICK, 0, opts, 0, QUICK.length); opts[QUICK.length] = new String[]{"custom", "Choose dates…"};
        pick("Dates", opts, "", v -> {
            if (!v.equals("custom")) { String[] p = HistoryMath.period(v, PastData.today()); from = p[0]; to = p[1]; render(); return; }
            date("From", from, f -> date("To", to, t -> {
                if (f.compareTo(t) > 0) { a.toast("The start date is after the end date."); return; }
                from = f; to = t; render();
            }));
        });
    }
    private void date(String title, String current, java.util.function.Consumer<String> picked) {
        LocalDate d = LocalDate.parse(current);
        android.app.DatePickerDialog dlg = new android.app.DatePickerDialog(a, (v, y, m, day) -> picked.accept(LocalDate.of(y, m + 1, day).toString()),
                d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth());
        dlg.setTitle(title);
        dlg.getDatePicker().setMaxDate(System.currentTimeMillis());
        dlg.show();
    }
    private void pickCover() {
        EditText input = new EditText(a); input.setSingleLine(true); input.setInputType(InputType.TYPE_CLASS_NUMBER); input.setText(cover);
        input.setHint("e.g. 14 (empty: the shop's setting)");
        FrameLayout wrap = new FrameLayout(a); wrap.setPadding(dp(24), dp(8), dp(24), 0); wrap.addView(input);
        Ui.dialog(a).setTitle("Cover days").setMessage("How many days the order should last, 1 to 120.").setView(wrap)
                .setPositiveButton("Use", (d, w) -> {
                    String v = input.getText().toString().trim();
                    if (!v.isEmpty()) { int n; try { n = Integer.parseInt(v); } catch (NumberFormatException e) { n = 0; }
                        if (n < 1 || n > 120) { a.toast("Cover days must be between 1 and 120."); return; } v = String.valueOf(n); }
                    cover = v; render();
                }).setNegativeButton("Cancel", null).show();
    }
    private void pick(String title, String[][] opts, String current, java.util.function.Consumer<String> picked) {
        String[] names = new String[opts.length]; int sel = -1;
        for (int i = 0; i < opts.length; i++) { names[i] = opts[i][1]; if (opts[i][0].equals(current)) sel = i; }
        Ui.choose(a, title, names, sel, w -> picked.accept(opts[w][0]));
    }
}
