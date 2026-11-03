package za.co.koenekt.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** My stores figures: totals, store types, rankings and money text. No Android, so the JVM checks can run it. */
public final class StoreMath {
    public static final String NO_TYPE = "No type";
    private StoreMath() {}

    /** One store's live answer, or a total of several. NaN = not known (offline, not read, or a staff login's GP). */
    public static final class Figures {
        public String id = "", name = "", type = NO_TYPE, asOf = "";
        public boolean online, answered;
        public int stores = 1, tillsRead, tillsTotal, voids, noSales, overrides;
        public double sales = Double.NaN, receipts = Double.NaN, salesExcl = Double.NaN, gpValue = Double.NaN;
        public double refunds = Double.NaN, paidOut = Double.NaN;
        public final double[] hours = new double[24];
    }

    /** What a tile or a comparison can show. GP needs an owner login. */
    public enum Metric {
        SALES("Sales", true), RECEIPTS("Receipts", false), BASKET("Average basket", true), GP_PCT("GP % (est.)", false),
        PER_TILL("Sales per till", true), TILLS("Tills read", false), REFUNDS("Refunds", true), VOIDS("Voids", false),
        NO_SALES("No-sale drawer opens", false), OVERRIDES("Price overrides", false);
        public final String label; final boolean money;
        Metric(String label, boolean money) { this.label = label; this.money = money; }
        /** Lower is better for these: a comparison crowns the smallest. */
        public boolean lowerWins() { return this == REFUNDS || this == VOIDS || this == NO_SALES || this == OVERRIDES; }
        public boolean needsGp() { return this == GP_PCT; }
    }

    public static double value(Figures f, Metric m) {
        switch (m) {
            case SALES: return f.sales;
            case RECEIPTS: return f.receipts;
            case BASKET: return basket(f);
            case GP_PCT: return gpPct(f);
            case PER_TILL: return f.tillsRead > 0 && !Double.isNaN(f.sales) ? f.sales / f.tillsRead : Double.NaN;
            case TILLS: return f.answered ? f.tillsRead : Double.NaN;
            case REFUNDS: return f.refunds;
            case VOIDS: return f.answered ? f.voids : Double.NaN;
            case NO_SALES: return f.answered ? f.noSales : Double.NaN;
            case OVERRIDES: return f.answered ? f.overrides : Double.NaN;
            default: return Double.NaN;
        }
    }

    public static double basket(Figures f) {
        return Double.isNaN(f.sales) || Double.isNaN(f.receipts) || f.receipts <= 0 ? Double.NaN : f.sales / f.receipts;
    }
    /** GP as a share of sales excluding VAT, as the daily report shows it. */
    public static double gpPct(Figures f) {
        return Double.isNaN(f.gpValue) || Double.isNaN(f.salesExcl) || f.salesExcl <= 0 ? Double.NaN : 100.0 * f.gpValue / f.salesExcl;
    }

    private static double add(double a, double b) { return Double.isNaN(b) ? a : (Double.isNaN(a) ? b : a + b); }

    /** Sum of the stores that answered. GP adds up only when every one of them has it, so the % never mixes. */
    public static Figures total(String name, List<Figures> stores) {
        Figures t = new Figures(); t.name = name; t.stores = stores.size();
        boolean allGp = true; int answered = 0;
        for (Figures f : stores) {
            t.online |= f.online;
            if (!f.answered) continue;
            answered++; t.answered = true;
            t.sales = add(t.sales, f.sales); t.receipts = add(t.receipts, f.receipts);
            t.refunds = add(t.refunds, f.refunds); t.paidOut = add(t.paidOut, f.paidOut);
            t.tillsRead += f.tillsRead; t.tillsTotal += f.tillsTotal;
            t.voids += f.voids; t.noSales += f.noSales; t.overrides += f.overrides;
            for (int h = 0; h < 24; h++) t.hours[h] += f.hours[h];
            if (Double.isNaN(f.gpValue) || Double.isNaN(f.salesExcl)) allGp = false;
            else { t.gpValue = add(t.gpValue, f.gpValue); t.salesExcl = add(t.salesExcl, f.salesExcl); }
            if (t.asOf.isEmpty() || (!f.asOf.isEmpty() && f.asOf.compareTo(t.asOf) < 0)) t.asOf = f.asOf;
        }
        if (!allGp || answered == 0) { t.gpValue = Double.NaN; t.salesExcl = Double.NaN; }
        return t;
    }

    /** Store type (set on the phone) -> its stores; the biggest type first, stores without a type last. */
    public static LinkedHashMap<String, List<Figures>> byType(List<Figures> stores) {
        Map<String, List<Figures>> groups = new LinkedHashMap<>();
        for (Figures f : stores) groups.computeIfAbsent(f.type == null || f.type.isBlank() ? NO_TYPE : f.type, k -> new ArrayList<>()).add(f);
        List<String> names = new ArrayList<>(groups.keySet());
        names.sort(Comparator.comparing((String n) -> n.equals(NO_TYPE))
                .thenComparing(n -> -orZero(total(n, groups.get(n)).sales))
                .thenComparing(String.CASE_INSENSITIVE_ORDER));
        LinkedHashMap<String, List<Figures>> out = new LinkedHashMap<>();
        for (String n : names) out.put(n, groups.get(n));
        return out;
    }

    private static double orZero(double v) { return Double.isNaN(v) ? 0 : v; }

    /** Best first for the metric; stores with no figure go last, then by name. */
    public static List<Figures> rank(List<Figures> stores, Metric m) {
        List<Figures> out = new ArrayList<>(stores);
        out.sort((a, b) -> {
            double x = value(a, m), y = value(b, m);
            if (Double.isNaN(x) != Double.isNaN(y)) return Double.isNaN(x) ? 1 : -1;
            if (!Double.isNaN(x) && x != y) return m.lowerWins() ? Double.compare(x, y) : Double.compare(y, x);
            return a.name.compareToIgnoreCase(b.name);
        });
        return out;
    }

    /** Running total of sales by hour, for the hour-by-hour race. */
    public static double[] cumulative(double[] hours) {
        double[] out = new double[hours.length]; double sum = 0;
        for (int i = 0; i < hours.length; i++) { sum += hours[i]; out[i] = sum; }
        return out;
    }

    /** "R 48 913" (whole rand) or "R 98.40" (small amounts such as a basket). */
    public static String money(double v) {
        if (Double.isNaN(v)) return "—";
        boolean cents = Math.abs(v) < 1000;
        String s = String.format(Locale.ROOT, cents ? "%,.2f" : "%,.0f", v).replace(',', ' ');
        return (v < 0 ? "−R " + s.substring(1) : "R " + s);
    }
    public static String count(double v) {
        return Double.isNaN(v) ? "—" : String.format(Locale.ROOT, "%,.0f", v).replace(',', ' ');
    }
    public static String format(Figures f, Metric m) {
        double v = value(f, m);
        if (Double.isNaN(v)) return "—";
        if (m == Metric.GP_PCT) return String.format(Locale.ROOT, "%.1f%%", v);
        if (m == Metric.TILLS) return f.tillsRead + " of " + f.tillsTotal;
        return m.money ? money(v) : count(v);
    }

    /** "14:32" from "2026-11-03T14:32:10" or "2026-11-03 14:32:10"; "" when unknown. */
    public static String clock(String stamp) {
        if (stamp == null || stamp.length() < 16) return "";
        return stamp.substring(11, 16);
    }
}
