package za.co.koenekt.app;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Past days for My stores (Dashboard and Compare): periods, sums and changes over the daily totals the Link relay
 * keeps (GET /api/history, Reporter 1.9.106+). The same rules as the website's portal-more.js and the Reporter
 * repo's docs/LINK-REPORTS-API.md: totals add up, GP % comes from the sums, stock is the last day that has it.
 * No Android, so the JVM checks (tools/StoreChecks.java) run it.
 */
public final class HistoryMath {
    private HistoryMath() {}

    /** One store's day. NaN = not sent (staff logins get no GP, cost or sales excl.; stock only on report days). */
    public static final class Day {
        public double turnover = Double.NaN, salesExcl = Double.NaN, cost = Double.NaN, gpValue = Double.NaN,
                stock = Double.NaN, itemsSold = Double.NaN, lowGp = Double.NaN;
    }

    /** Store id -> date (yyyy-mm-dd) -> day. */
    public static final class History {
        public final Map<String, TreeMap<String, Day>> stores = new TreeMap<>();
        public final Map<String, String> latest = new TreeMap<>();
        public String from = "", to = "";
        public boolean covers(String a, String b) { return !from.isEmpty() && from.compareTo(a) <= 0 && to.compareTo(b) >= 0; }
    }

    /** What a tile, ranking or table can show; keys match the shared layout format. */
    public enum Metric {
        TURNOVER("turnover", "Turnover", 'R', false, false), SALES_EXCL("sales_excl", "Sales excl. VAT", 'R', true, false),
        COST("cost", "Cost of sales", 'R', true, true), GP_VALUE("gp_value", "GP", 'R', true, false),
        GP_PCT("gp_pct", "GP %", '%', true, false), STOCK("stock_excl", "Stock value", 'R', false, false),
        STOCK_DAYS("stock_days", "Stock days", 'd', true, true), PER_DAY("per_day", "Turnover per trading day", 'R', false, false),
        ITEMS_SOLD("items_sold", "Lines sold", 'n', false, false), LOW_GP("low_gp_count", "Low-GP lines", 'n', true, true);
        public final String key, label; final char kind; public final boolean gp, lower;
        Metric(String key, String label, char kind, boolean gp, boolean lower) { this.key = key; this.label = label; this.kind = kind; this.gp = gp; this.lower = lower; }
        public static Metric of(String key) { for (Metric m : values()) if (m.key.equals(key)) return m; return null; }
        /** Stock is a level, not a result: its change is shown without good or bad. */
        public boolean neutral() { return this == STOCK; }
    }

    /** A period's sums for some stores. */
    public static final class Agg {
        public double turnover, salesExcl, cost, gpValue, itemsSold, lowGp, stock = Double.NaN;
        public int days, stores;
        public double value(Metric m) {
            switch (m) {
                case TURNOVER: return turnover;
                case SALES_EXCL: return salesExcl;
                case COST: return cost;
                case GP_VALUE: return gpValue;
                case GP_PCT: return salesExcl > 0 ? 100 * gpValue / salesExcl : Double.NaN;
                case STOCK: return stock;
                case STOCK_DAYS: return !Double.isNaN(stock) && cost > 0 && days > 0 ? stock / (cost / days) : Double.NaN;
                case PER_DAY: return days > 0 ? turnover / days : Double.NaN;
                case ITEMS_SOLD: return itemsSold;
                case LOW_GP: return lowGp;
                default: return Double.NaN;
            }
        }
    }

    /** null when none of the stores has a day in the period. */
    public static Agg agg(History h, Collection<String> ids, String from, String to, boolean owner) {
        Agg out = new Agg(); Set<String> dates = new HashSet<>(); boolean any = false, gpAll = true;
        for (String id : ids) {
            TreeMap<String, Day> days = h.stores.get(id); if (days == null) continue;
            String last = null; boolean mine = false;
            for (Map.Entry<String, Day> e : days.subMap(from, true, to, true).entrySet()) {
                Day d = e.getValue(); mine = true;
                out.turnover += z(d.turnover); out.itemsSold += z(d.itemsSold); out.lowGp += z(d.lowGp);
                if (Double.isNaN(d.salesExcl) || Double.isNaN(d.cost)) gpAll = false;
                else { out.salesExcl += d.salesExcl; out.cost += d.cost; out.gpValue += Double.isNaN(d.gpValue) ? d.salesExcl - d.cost : d.gpValue; }
                if (z(d.turnover) > 0) dates.add(e.getKey());
                if (!Double.isNaN(d.stock)) last = e.getKey();
            }
            if (mine) { any = true; out.stores++; }
            if (last != null) out.stock = (Double.isNaN(out.stock) ? 0 : out.stock) + days.get(last).stock;
        }
        if (!any) return null;
        out.days = dates.size();
        if (!gpAll || !owner) { out.salesExcl = Double.NaN; out.cost = Double.NaN; out.gpValue = Double.NaN; out.lowGp = owner ? out.lowGp : Double.NaN; }
        return out;
    }
    private static double z(double v) { return Double.isNaN(v) ? 0 : v; }

    /** One value per day (NaN where no store has the day), oldest first. */
    public static double[] series(History h, Collection<String> ids, String from, String to, Metric m, boolean owner) {
        LocalDate a = LocalDate.parse(from), b = LocalDate.parse(to);
        int n = (int) ChronoUnit.DAYS.between(a, b) + 1; double[] out = new double[Math.max(0, n)];
        for (int i = 0; i < n; i++) { String d = a.plusDays(i).toString(); Agg x = agg(h, ids, d, d, owner); out[i] = x == null ? Double.NaN : x.value(m); }
        return out;
    }

    /** Percent change, or points for GP %; NaN when either side is missing or the old value is 0. */
    public static double change(Agg now, Agg before, Metric m) {
        if (now == null || before == null) return Double.NaN;
        double x = now.value(m), y = before.value(m);
        if (Double.isNaN(x) || Double.isNaN(y)) return Double.NaN;
        if (m == Metric.GP_PCT) return x - y;
        return y == 0 ? Double.NaN : 100 * (x - y) / Math.abs(y);
    }
    /** +1 better, -1 worse, 0 neither (or a level such as stock). */
    public static int verdict(double change, Metric m) {
        if (Double.isNaN(change) || Math.abs(change) < 0.05 || m.neutral()) return 0;
        return (m.lower ? -change : change) > 0 ? 1 : -1;
    }

    // ---------------------------------------------------------------- periods (the shop's days)
    public static final String[][] PERIODS = {{"yesterday", "Yesterday"}, {"wtd", "Week to date"}, {"mtd", "Month to date"},
            {"last_month", "Last month"}, {"last_30", "Last 30 days"}, {"ytd", "Year to date"}, {"last_year", "Last year"}};
    public static final String[][] COMPARES = {{"prev_year", "Same days last year"}, {"prev_period", "The period before"}, {"none", "No comparison"}};

    /** {from, to}: past days only; today is on Live. */
    public static String[] period(String key, LocalDate today) {
        LocalDate y = today.minusDays(1), a, b = y;
        switch (key == null ? "" : key) {
            case "yesterday": a = y; break;
            case "wtd": a = today.with(DayOfWeek.MONDAY); if (!a.isBefore(today)) { a = a.minusDays(7); b = a.plusDays(6); } break;
            case "last_month": a = today.withDayOfMonth(1).minusMonths(1); b = today.withDayOfMonth(1).minusDays(1); break;
            case "last_30": a = y.minusDays(29); break;
            case "ytd": a = today.withDayOfYear(1); if (!a.isBefore(today)) a = a.minusYears(1); break;
            case "last_year": a = today.withDayOfYear(1).minusYears(1); b = today.withDayOfYear(1).minusDays(1); break;
            default: a = today.withDayOfMonth(1); if (!a.isBefore(today)) a = a.minusMonths(1); break;   // mtd
        }
        return new String[]{a.toString(), b.toString()};
    }
    /** The period to compare with, or null. 29 Feb a year back is 28 Feb. */
    public static String[] against(String[] p, String mode) {
        LocalDate a = LocalDate.parse(p[0]), b = LocalDate.parse(p[1]);
        if ("prev_year".equals(mode)) return new String[]{a.minusYears(1).toString(), b.minusYears(1).toString()};
        if ("prev_period".equals(mode)) { long n = ChronoUnit.DAYS.between(a, b) + 1; return new String[]{a.minusDays(n).toString(), a.minusDays(1).toString()}; }
        return null;
    }
    public static String label(String[][] list, String key) { for (String[] o : list) if (o[0].equals(key)) return o[1]; return key; }

    private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    /** "1 Nov", or "1 Nov 2025" outside this year. */
    public static String nice(String date, LocalDate today) {
        LocalDate d = LocalDate.parse(date);
        return d.getDayOfMonth() + " " + MONTHS[d.getMonthValue() - 1] + (d.getYear() != today.getYear() ? " " + d.getYear() : "");
    }
    public static String range(String[] p, LocalDate today) { return p[0].equals(p[1]) ? nice(p[0], today) : nice(p[0], today) + " – " + nice(p[1], today); }

    // ---------------------------------------------------------------- text
    public static String format(double v, Metric m) {
        if (Double.isNaN(v)) return "—";
        switch (m.kind) {
            case 'R': return StoreMath.money(v);
            case '%': return String.format(Locale.ROOT, "%.1f%%", v);
            case 'd': return Math.round(v) + " days";
            default: return StoreMath.count(v);
        }
    }
    /** "▲ +10.2%", "▼ −0.3 pts"; "" when there is nothing to say. */
    public static String changeText(double c, Metric m) {
        if (Double.isNaN(c)) return "";
        String arrow = c > 0 ? "▲ +" : c < 0 ? "▼ −" : "";
        return arrow + String.format(Locale.ROOT, "%.1f", Math.abs(c)) + (m == Metric.GP_PCT ? " pts" : "%");
    }

    /** The dashboard every owner starts with (shared with the website; null from the relay means this one). */
    public static final String DEFAULT_LAYOUT = "{\"version\":1,\"period\":\"mtd\",\"compare\":\"prev_year\",\"tiles\":["
            + "{\"id\":\"k1\",\"kind\":\"kpi\",\"metric\":\"turnover\",\"scope\":{\"type\":\"all\"}},"
            + "{\"id\":\"k2\",\"kind\":\"kpi\",\"metric\":\"gp_pct\",\"scope\":{\"type\":\"all\"}},"
            + "{\"id\":\"k3\",\"kind\":\"kpi\",\"metric\":\"gp_value\",\"scope\":{\"type\":\"all\"}},"
            + "{\"id\":\"k4\",\"kind\":\"kpi\",\"metric\":\"stock_excl\",\"scope\":{\"type\":\"all\"}},"
            + "{\"id\":\"l1\",\"kind\":\"live\",\"size\":\"l\"},"
            + "{\"id\":\"t1\",\"kind\":\"trend\",\"metric\":\"turnover\",\"days\":30,\"scope\":{\"type\":\"all\"},\"size\":\"l\"},"
            + "{\"id\":\"r1\",\"kind\":\"ranking\",\"metric\":\"turnover\",\"by\":\"store\",\"size\":\"m\"},"
            + "{\"id\":\"r2\",\"kind\":\"ranking\",\"metric\":\"gp_pct\",\"by\":\"store\",\"size\":\"m\"},"
            + "{\"id\":\"b1\",\"kind\":\"table\",\"by\":\"type\",\"metrics\":[\"turnover\",\"gp_pct\",\"per_day\"],\"size\":\"l\"}]}";
}
