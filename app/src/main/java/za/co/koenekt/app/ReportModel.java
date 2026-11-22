package za.co.koenekt.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static za.co.koenekt.app.ReportFiles.c;

/**
 * A report answer from the shop PC (the same JSON the Reports screen shows) as a
 * {@link ReportFiles.Model} for the PDF, Excel and CSV downloads. Field for field the same as the
 * website's KP.reportModel (koenekt-website assets/js/report-export.js), so a download from the
 * app and one from the website hold the same tables.
 */
final class ReportModel {
    private ReportModel() { }

    private static final String[] TENDERS = {"cash", "card", "eft", "cheque", "voucher", "other"};

    private static Object v(JSONObject o, String k) {
        if (o == null || !o.has(k) || o.isNull(k)) return null;
        return o.opt(k);
    }
    private static String s(JSONObject o, String k) { Object x = v(o, k); return x == null ? "" : x.toString(); }
    private static String day(Object x) { String t = x == null ? "" : x.toString(); return t.length() >= 10 ? t.substring(0, 10) : t; }
    private static double n(Object x) { return ReportFiles.num(x); }
    private static List<JSONObject> list(JSONObject r, String k) {
        List<JSONObject> out = new ArrayList<>();
        JSONArray a = r.optJSONArray(k);
        if (a != null) for (int i = 0; i < a.length(); i++) { JSONObject o = a.optJSONObject(i); if (o != null) out.add(o); }
        return out;
    }
    private static String supName(JSONObject i) { String v = s(i, "supplier"); return v.isEmpty() ? "(no supplier)" : v; }
    private static Object[] tile(String label, Object value, String type) { return new Object[]{label, value, type}; }
    private static ReportFiles.Table deptTable(String name, String first, List<JSONObject> rows) {
        ReportFiles.Table t = new ReportFiles.Table(name, c(first, "t"), c("Lines", "i"), c("Qty", "q"), c("Sales incl", "m"), c("Sales excl", "m"),
                c("Cost", "m"), c("GP", "m"), c("GP %", "p"), c("Share %", "p"));
        for (JSONObject d : rows) t.rows.add(new Object[]{s(d, "name"), v(d, "items"), v(d, "qty"), v(d, "incl"), v(d, "excl"), v(d, "cost"), v(d, "gp_value"), v(d, "gp_pct"), v(d, "share_pct")});
        return t;
    }

    /** {@code supplier}: the order report's one supplier picked on screen, or "" for all. */
    static ReportFiles.Model build(String kind, JSONObject r, String supplier, String account) {
        ReportFiles.Model m = new ReportFiles.Model();
        m.kind = kind;
        m.store = s(r, "name").isEmpty() ? "Store" : s(r, "name");
        m.account = account == null ? "" : account;
        m.from = day(v(r, "date_from"));
        m.to = day(v(r, "date_to"));
        String made = s(r, "made_at").replace('T', ' ');
        m.made = made.length() >= 16 ? made.substring(0, 16) : made;
        JSONArray inc = r.optJSONArray("include_suppliers");
        if (inc != null) for (int i = 0; i < inc.length(); i++) m.suppliers.add(inc.optString(i));
        switch (kind) {
            case "range": {
                m.title = "Range report";
                m.tiles.add(tile("Turnover", v(r, "turnover_incl"), "m"));
                m.tiles.add(tile("Sales excl. VAT", v(r, "sales_excl"), "m"));
                m.tiles.add(tile("GP", v(r, "gp_value"), "m"));
                m.tiles.add(tile("GP %", v(r, "gp_pct"), "p"));
                m.tiles.add(tile("Days traded", v(r, "days_with_sales"), "i"));
                m.tiles.add(tile("Lines sold", v(r, "items_sold"), "i"));
                double ref = n(v(r, "refunds_value"));
                m.tiles.add(tile("Refunds", Double.isNaN(ref) ? 0.0 : Math.abs(ref), "m"));
                ReportFiles.Table days = new ReportFiles.Table("Days", c("Date", "d"), c("Day", "t"), c("Turnover", "m"), c("Sales excl", "m"), c("Cost", "m"), c("GP", "m"), c("GP %", "p"));
                for (JSONObject d : list(r, "days"))
                    days.rows.add(new Object[]{day(v(d, "date")), s(d, "weekday") + (s(d, "holiday").isEmpty() ? "" : " · " + s(d, "holiday")),
                            v(d, "turnover"), v(d, "sales_excl"), v(d, "cost"), v(d, "gp_value"), v(d, "gp_pct")});
                m.tables.add(days);
                m.tables.add(deptTable("Departments", "Department", list(r, "departments")));
                if (!list(r, "groups").isEmpty()) m.tables.add(deptTable("Groups", "Group", list(r, "groups")));
                if (!list(r, "suppliers").isEmpty()) m.tables.add(deptTable("Suppliers", "Supplier", list(r, "suppliers")));
                List<JSONObject> low = list(r, "low_gp");
                if (!low.isEmpty()) {
                    ReportFiles.Table t = new ReportFiles.Table("GP below " + s(r, "low_gp_threshold") + "%", c("Item no", "t"), c("Item", "t"), c("Department", "t"),
                            c("Qty", "q"), c("Sales incl", "m"), c("GP", "m"), c("GP %", "p"));
                    for (JSONObject i : low) t.rows.add(new Object[]{s(i, "itemno"), s(i, "name"), s(i, "dept"), v(i, "qty"), v(i, "incl"), v(i, "gp_value"), v(i, "gp_pct")});
                    m.tables.add(t);
                }
                List<JSONObject> items = list(r, "items");
                ReportFiles.Table t = new ReportFiles.Table("Items", c("Item no", "t"), c("Item", "t"), c("Department", "t"), c("Group", "t"), c("Supplier", "t"),
                        c("Qty", "q"), c("Sales incl", "m"), c("Sales excl", "m"), c("Cost", "m"), c("GP", "m"), c("GP %", "p"));
                for (JSONObject i : items)
                    t.rows.add(new Object[]{s(i, "itemno"), s(i, "name"), s(i, "dept"), s(i, "group"), s(i, "supplier"), v(i, "qty"), v(i, "incl"), v(i, "excl"), v(i, "cost"), v(i, "gp_value"), v(i, "gp_pct")});
                if (r.optInt("items_total", 0) > items.size())
                    t.note = "The " + items.size() + " best sellers of " + r.optInt("items_total") + " lines; the full list is in the Range report on the shop PC.";
                m.tables.add(t);
                m.main = "Items";
                break;
            }
            case "order": {
                String sp = supplier == null ? "" : supplier;
                m.title = "Order report" + (sp.isEmpty() ? "" : " · " + sp);
                List<JSONObject> rows = new ArrayList<>();
                for (JSONObject i : list(r, "rows")) if (sp.isEmpty() || supName(i).equals(sp)) rows.add(i);
                Collections.sort(rows, (a, b) -> { int x = s(a, "supplier").compareTo(s(b, "supplier")); return x != 0 ? x : s(a, "name").compareTo(s(b, "name")); });
                double value = 0; int out = 0;
                for (JSONObject i : rows) { double ov = n(v(i, "order_value")); if (!Double.isNaN(ov)) value += ov; if (i.optBoolean("stockout")) out++; }
                m.tiles.add(tile("Lines to order", sp.isEmpty() ? v(r, "lines") : (Object) (long) rows.size(), "i"));
                m.tiles.add(tile("Order value", sp.isEmpty() ? v(r, "order_value") : (Object) value, "m"));
                m.tiles.add(tile("Sold out while selling", sp.isEmpty() ? v(r, "stockouts") : (Object) (long) out, "i"));
                m.tiles.add(tile("Trading days", v(r, "trading_days"), "i"));
                m.tiles.add(tile("Cover (days)", v(r, "cover_days"), "i"));
                if (r.optBoolean("ignore_soh")) m.notes.add("Stock on hand is ignored: every line is ordered for full cover.");
                List<JSONObject> bys = list(r, "by_supplier");
                if (sp.isEmpty() && bys.size() > 1) {
                    ReportFiles.Table t = new ReportFiles.Table("By supplier", c("Supplier", "t"), c("Lines", "i"), c("Sold out", "i"), c("Order value", "m"));
                    for (JSONObject b : bys) t.rows.add(new Object[]{s(b, "name"), v(b, "lines"), v(b, "stockouts"), v(b, "order_value")});
                    m.tables.add(t);
                }
                ReportFiles.Table t = new ReportFiles.Table("Suggested order", c("Supplier", "t"), c("Item no", "t"), c("Item", "t"), c("Department", "t"), c("Group", "t"),
                        c("Sold", "q"), c("Per day", "n2"), c("On hand", "q"), c("Cover days", "i"), c("Order qty", "q"), c("Unit cost", "m"), c("Order value", "m"),
                        c("Sold out", "t"), c("Last bought", "d"));
                for (JSONObject i : rows)
                    t.rows.add(new Object[]{s(i, "supplier"), s(i, "itemno"), s(i, "name"), s(i, "dept"), s(i, "group"), v(i, "units"), v(i, "rate"), v(i, "soh"),
                            v(i, "cover"), v(i, "suggest"), v(i, "unit_cost"), v(i, "order_value"), i.optBoolean("stockout") ? "yes" : "", day(v(i, "last_bought"))});
                if (r.optInt("rows_total", 0) > list(r, "rows").size()) t.note = "The first " + list(r, "rows").size() + " of " + r.optInt("rows_total") + " lines.";
                m.tables.add(t);
                List<JSONObject> neg = list(r, "negative_rows");
                if (sp.isEmpty() && !neg.isEmpty()) {
                    ReportFiles.Table nt = new ReportFiles.Table("Negative stock (not ordered)", c("Item no", "t"), c("Item", "t"), c("Department", "t"), c("Sold", "q"), c("On hand", "q"), c("Would order", "q"));
                    for (JSONObject i : neg) nt.rows.add(new Object[]{s(i, "itemno"), s(i, "name"), s(i, "dept"), v(i, "units"), v(i, "raw_soh") != null ? v(i, "raw_soh") : v(i, "soh"), v(i, "suggest")});
                    m.tables.add(nt);
                }
                m.main = "Suggested order";
                break;
            }
            case "rosi": {
                JSONObject t0 = r.optJSONObject("total");
                String by = s(r, "by"), byName = by.equals("dept") ? "Department" : by.equals("supplier") ? "Supplier" : "Group";
                m.title = "ROSI report";
                m.tiles.add(tile("ROSI", v(t0, "rosi"), "i"));
                m.tiles.add(tile("GP %", v(t0, "gp_pct"), "p"));
                m.tiles.add(tile("Stock turns (a year)", v(t0, "turns"), "n1"));
                m.tiles.add(tile("Stock days", v(t0, "stock_days"), "i"));
                m.tiles.add(tile("Stock value", v(t0, "stock"), "m"));
                if (r.optBoolean("stock_is_stale")) m.notes.add("The stock is today's, not the stock at the end of the period, so turns and ROSI for a past period drift.");
                m.notes.add("ROSI = stock turns × GP %.");
                ReportFiles.Table t = new ReportFiles.Table("By " + byName.toLowerCase(java.util.Locale.ROOT), c(byName, "t"), c("Stock", "m"), c("Sales", "m"), c("Cost", "m"), c("GP", "m"),
                        c("GP %", "p"), c("Turns", "n1"), c("Stock days", "i"), c("ROSI", "i"));
                for (JSONObject x : list(r, "rows"))
                    t.rows.add(new Object[]{s(x, "name"), v(x, "stock"), v(x, "sales"), v(x, "cost"), v(x, "gp_value"), v(x, "gp_pct"), v(x, "turns"), v(x, "stock_days"), v(x, "rosi")});
                if (t0 != null) {
                    t.rows.add(new Object[]{"Total", v(t0, "stock"), v(t0, "sales"), v(t0, "cost"), v(t0, "gp_value"), v(t0, "gp_pct"), v(t0, "turns"), v(t0, "stock_days"), v(t0, "rosi")});
                    t.total = true;
                }
                m.tables.add(t);
                m.main = t.name;
                break;
            }
            default: {
                m.kind = "turnover";
                JSONObject tt = r.optJSONObject("total");
                List<JSONObject> tills = list(r, "tills");
                List<String> tn = new ArrayList<>();
                for (String k : TENDERS) for (JSONObject x : tills) { double a = n(v(x, k)); if (!Double.isNaN(a) && Math.abs(a) > 0.004) { tn.add(k); break; } }
                m.title = "Turnover & tenders";
                double po = n(v(tt, "paid_out"));
                m.tiles.add(tile("Sales", v(tt, "sales"), "m"));
                m.tiles.add(tile("Money in", v(tt, "money_in"), "m"));
                m.tiles.add(tile("Paid out", Double.isNaN(po) ? 0.0 : Math.abs(po), "m"));
                m.tiles.add(tile("Bankable", v(tt, "bankable"), "m"));
                m.tiles.add(tile("Accounts moved", v(tt, "account_movement"), "m"));
                m.tiles.add(tile("Receipts", v(r, "receipts"), "i"));
                List<ReportFiles.Col> cols = new ArrayList<>();
                cols.add(c("Till", "t")); cols.add(c("Sales", "m")); cols.add(c("Settled", "m")); cols.add(c("On account", "m")); cols.add(c("Money in", "m"));
                for (String k : tn) cols.add(c(Character.toUpperCase(k.charAt(0)) + k.substring(1), "m"));
                cols.add(c("Paid out", "m")); cols.add(c("Bankable", "m"));
                ReportFiles.Table t = new ReportFiles.Table("Per till", cols.toArray(new ReportFiles.Col[0]));
                List<JSONObject> withTotal = new ArrayList<>(tills);
                if (tt != null) withTotal.add(tt);
                for (int i = 0; i < withTotal.size(); i++) {
                    JSONObject x = withTotal.get(i);
                    boolean isTotal = tt != null && i == withTotal.size() - 1;
                    List<Object> row = new ArrayList<>();
                    row.add(isTotal ? "All tills" : "Till " + s(x, "till"));
                    row.add(v(x, "sales")); row.add(v(x, "sales_settled")); row.add(v(x, "sales_on_account")); row.add(v(x, "money_in"));
                    for (String k : tn) row.add(v(x, k));
                    row.add(v(x, "paid_out")); row.add(v(x, "bankable"));
                    t.rows.add(row.toArray());
                }
                t.total = tt != null;
                m.tables.add(t);
                List<JSONObject> days = list(r, "days");
                if (days.size() > 1) {
                    ReportFiles.Table d = new ReportFiles.Table("Per day", c("Date", "d"), c("Sales", "m"), c("Money in", "m"), c("Paid out", "m"), c("Bankable", "m"));
                    for (JSONObject x : days) d.rows.add(new Object[]{day(v(x, "date")), v(x, "sales"), v(x, "money_in"), v(x, "paid_out"), v(x, "bankable")});
                    m.tables.add(d);
                }
                List<JSONObject> pos = list(r, "paid_outs");
                if (!pos.isEmpty()) {
                    ReportFiles.Table p = new ReportFiles.Table("Paid outs", c("Date", "d"), c("Till", "t"), c("To", "t"), c("Why", "t"), c("Amount", "m"));
                    for (JSONObject x : pos) { double a = n(v(x, "total")); p.rows.add(new Object[]{day(v(x, "date")), s(x, "till"), s(x, "receiver"), s(x, "reason"), Double.isNaN(a) ? null : Math.abs(a)}); }
                    m.tables.add(p);
                }
                m.notes.add("Sales is what went out of the door, paid for or not. Money in is what came in, whatever it was for.");
                m.main = "Per till";
            }
        }
        return m;
    }
}
