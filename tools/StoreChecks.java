import java.util.List;
import za.co.koenekt.app.StoreMath;
import za.co.koenekt.app.StoreMath.Figures;
import za.co.koenekt.app.StoreMath.Metric;

/** My stores maths: totals, store types and rankings (made-up stores only). */
public class StoreChecks {
    private static int checks;
    private static void check(boolean value, String name) { checks++; if (!value) throw new AssertionError(name); }

    private static Figures store(String name, String type, double sales, double receipts, double gp, double excl, int tills, int read) {
        Figures f = new Figures(); f.name = name; f.type = type; f.answered = true; f.online = true;
        f.sales = sales; f.receipts = receipts; f.gpValue = gp; f.salesExcl = excl; f.tillsTotal = tills; f.tillsRead = read;
        f.refunds = 0; f.asOf = "2026-11-03T14:3" + (int) (receipts % 10) + ":00";
        return f;
    }

    public static void main(String[] args) {
        Figures a = store("North", "Liquor", 10000, 100, 2000, 8695.65, 2, 2);
        Figures b = store("South", "Liquor", 30000, 150, 3000, 26086.96, 3, 3);
        Figures c = store("Depot", "Wholesale", 50000, 50, 2500, 43478.26, 1, 1);
        Figures off = new Figures(); off.name = "East"; off.type = "Liquor";   // offline, never answered

        check(StoreMath.basket(a) == 100, "basket");
        check(Math.abs(StoreMath.gpPct(a) - 23.0) < 0.01, "GP % on sales excl. VAT");
        check(StoreMath.value(b, Metric.PER_TILL) == 10000, "sales per till read");

        Figures liquor = StoreMath.total("Liquor", List.of(a, b, off));
        check(liquor.sales == 40000 && liquor.receipts == 250 && liquor.stores == 3, "type total skips a store that did not answer");
        check(liquor.tillsRead == 5 && liquor.tillsTotal == 5, "tills of answered stores");
        check(Math.abs(StoreMath.gpPct(liquor) - 100.0 * 5000 / 34782.61) < 0.01, "GP % from summed GP and sales excl.");
        Figures staffView = store("Staff", "Liquor", 5000, 10, Double.NaN, Double.NaN, 1, 1);
        check(Double.isNaN(StoreMath.gpPct(StoreMath.total("x", List.of(a, staffView)))), "no GP % when a store has none");
        check(StoreMath.total("x", List.of(a, b)).asOf.equals(a.asOf.compareTo(b.asOf) < 0 ? a.asOf : b.asOf), "oldest read time");
        check(!StoreMath.total("none", List.of(off)).answered && Double.isNaN(StoreMath.total("none", List.of(off)).sales), "nothing answered");

        var groups = StoreMath.byType(List.of(a, c, b, store("Kiosk", "", 100, 1, 0, 0, 1, 1)));
        check(String.join(",", groups.keySet()).equals("Wholesale,Liquor," + StoreMath.NO_TYPE), "biggest type first, untyped last");
        check(groups.get("Liquor").size() == 2, "both liquor stores grouped");

        check(StoreMath.rank(List.of(a, b, c, off), Metric.SALES).get(0) == c, "most sales first");
        check(StoreMath.rank(List.of(a, b, c, off), Metric.SALES).get(3) == off, "no figure last");
        check(StoreMath.rank(List.of(a, b, c), Metric.BASKET).get(0) == c, "biggest basket first");
        a.voids = 9; b.voids = 2; c.voids = 5;
        check(StoreMath.rank(List.of(a, b, c), Metric.VOIDS).get(0) == b, "fewest voids wins");

        a.hours[8] = 100; a.hours[9] = 50;
        double[] run = StoreMath.cumulative(a.hours);
        check(run[8] == 100 && run[9] == 150 && run[23] == 150, "running total by hour");

        check(StoreMath.money(48912.31).equals("R 48 912"), "whole rand money: " + StoreMath.money(48912.31));
        check(StoreMath.money(98.4).equals("R 98.40"), "small money with cents");
        check(StoreMath.money(Double.NaN).equals("—"), "unknown money");
        check(StoreMath.format(a, Metric.TILLS).equals("2 of 2"), "tills text");
        check(StoreMath.format(a, Metric.GP_PCT).equals("23.0%"), "GP text");
        check(StoreMath.format(off, Metric.VOIDS).equals("—"), "no figure text");
        check(StoreMath.clock("2026-11-03T14:32:10").equals("14:32") && StoreMath.clock("").isEmpty(), "clock");
        check(StoreMath.qty(12).equals("12") && StoreMath.qty(1.235).equals("1.235") && StoreMath.qty(2.5).equals("2.5"),
                "weighed quantities keep decimals: " + StoreMath.qty(1.235));
        check(StoreMath.qty(1200.0004).equals("1 200"), "whole quantities stay counts");
        history();
        System.out.println("Store checks passed: " + checks);
    }

    private static za.co.koenekt.app.HistoryMath.Day day(double turnover, double excl, double cost, double stock) {
        za.co.koenekt.app.HistoryMath.Day d = new za.co.koenekt.app.HistoryMath.Day();
        d.turnover = turnover; d.salesExcl = excl; d.cost = cost; d.gpValue = Double.isNaN(excl) ? Double.NaN : excl - cost; d.stock = stock;
        return d;
    }

    /** Past days (Dashboard, Compare): the same sums as the website (made-up stores). */
    private static void history() {
        java.time.LocalDate today = java.time.LocalDate.of(2026, 11, 10);
        za.co.koenekt.app.HistoryMath.History h = new za.co.koenekt.app.HistoryMath.History();
        java.util.TreeMap<String, za.co.koenekt.app.HistoryMath.Day> s1 = new java.util.TreeMap<>(), s2 = new java.util.TreeMap<>();
        s1.put("2026-11-01", day(1150, 1000, 750, 50000)); s1.put("2026-11-02", day(2300, 2000, 1400, 52000));
        s1.put("2026-11-03", day(0, 0, 0, Double.NaN));                       // closed: not a trading day
        s2.put("2026-11-01", day(575, 500, 400, Double.NaN)); s2.put("2025-11-01", day(500, 434.78, 330, Double.NaN));
        h.stores.put("s1", s1); h.stores.put("s2", s2);
        za.co.koenekt.app.HistoryMath.Agg a = za.co.koenekt.app.HistoryMath.agg(h, List.of("s1", "s2"), "2026-11-01", "2026-11-09", true);
        check(a.turnover == 4025 && a.days == 2 && a.stores == 2, "period sums over trading days");
        check(Math.abs(a.value(za.co.koenekt.app.HistoryMath.Metric.GP_PCT) - 100.0 * 950 / 3500) < 1e-9, "GP % from the sums, not an average");
        check(a.stock == 52000, "stock from each store's last day that has it");
        check(a.value(za.co.koenekt.app.HistoryMath.Metric.PER_DAY) == 2012.5, "turnover per trading day");
        check(Math.abs(a.value(za.co.koenekt.app.HistoryMath.Metric.STOCK_DAYS) - 52000 / (2550.0 / 2)) < 1e-9, "stock days");
        za.co.koenekt.app.HistoryMath.Agg staff = za.co.koenekt.app.HistoryMath.agg(h, List.of("s1"), "2026-11-01", "2026-11-09", false);
        check(Double.isNaN(staff.value(za.co.koenekt.app.HistoryMath.Metric.GP_VALUE)) && staff.turnover == 3450, "staff: no GP");
        check(za.co.koenekt.app.HistoryMath.agg(h, List.of("s1"), "2025-01-01", "2025-12-31", true) == null, "no days, no figures");
        za.co.koenekt.app.HistoryMath.Agg last = za.co.koenekt.app.HistoryMath.agg(h, List.of("s2"), "2025-11-01", "2025-11-09", true);
        za.co.koenekt.app.HistoryMath.Agg now = za.co.koenekt.app.HistoryMath.agg(h, List.of("s2"), "2026-11-01", "2026-11-09", true);
        check(Math.abs(za.co.koenekt.app.HistoryMath.change(now, last, za.co.koenekt.app.HistoryMath.Metric.TURNOVER) - 15) < 1e-9, "change in %");
        double pts = za.co.koenekt.app.HistoryMath.change(now, last, za.co.koenekt.app.HistoryMath.Metric.GP_PCT);
        check(Math.abs(pts - (20 - 100 * 104.78 / 434.78)) < 1e-6, "GP change in points");
        check(za.co.koenekt.app.HistoryMath.verdict(-5, za.co.koenekt.app.HistoryMath.Metric.COST) == 1
                && za.co.koenekt.app.HistoryMath.verdict(5, za.co.koenekt.app.HistoryMath.Metric.STOCK) == 0, "lower cost is better; stock is neutral");
        check(za.co.koenekt.app.HistoryMath.changeText(-0.34, za.co.koenekt.app.HistoryMath.Metric.GP_PCT).equals("▼ −0.3 pts"), "change text");
        double[] line = za.co.koenekt.app.HistoryMath.series(h, List.of("s1"), "2026-10-31", "2026-11-02", za.co.koenekt.app.HistoryMath.Metric.TURNOVER, true);
        check(line.length == 3 && Double.isNaN(line[0]) && line[2] == 2300, "a day per point, gaps stay gaps");

        String[] mtd = za.co.koenekt.app.HistoryMath.period("mtd", today);
        check(mtd[0].equals("2026-11-01") && mtd[1].equals("2026-11-09"), "month to date ends yesterday");
        String[] first = za.co.koenekt.app.HistoryMath.period("mtd", java.time.LocalDate.of(2026, 12, 1));
        check(first[0].equals("2026-11-01") && first[1].equals("2026-11-30"), "on the 1st, month to date is last month");
        String[] wtd = za.co.koenekt.app.HistoryMath.period("wtd", java.time.LocalDate.of(2026, 11, 9));    // a Monday
        check(wtd[0].equals("2026-11-02") && wtd[1].equals("2026-11-08"), "on a Monday, week to date is last week");
        String[] lm = za.co.koenekt.app.HistoryMath.period("last_month", today);
        check(lm[0].equals("2026-10-01") && lm[1].equals("2026-10-31"), "last month");
        String[] py = za.co.koenekt.app.HistoryMath.against(new String[]{"2028-02-01", "2028-02-29"}, "prev_year");
        check(py[0].equals("2027-02-01") && py[1].equals("2027-02-28"), "a leap day a year back");
        String[] pp = za.co.koenekt.app.HistoryMath.against(mtd, "prev_period");
        check(pp[0].equals("2026-10-23") && pp[1].equals("2026-10-31"), "the period before has the same length");
        check(za.co.koenekt.app.HistoryMath.against(mtd, "none") == null, "no comparison");
        String[] live = za.co.koenekt.app.HistoryMath.period("mtd", today, true);
        check(live[0].equals("2026-11-01") && live[1].equals("2026-11-10"), "month to date with today runs to today");
        check(za.co.koenekt.app.HistoryMath.period("mtd", java.time.LocalDate.of(2026, 12, 1), true)[0].equals("2026-12-01"), "on the 1st, today alone");
        check(za.co.koenekt.app.HistoryMath.period("last_month", today, true)[1].equals("2026-10-31"), "a closed period ignores today");
        // 11 Nov 2026: a period that runs to today is compared on its completed days, so a morning never looks behind
        String[][] fairYear = za.co.koenekt.app.HistoryMath.fair(live, za.co.koenekt.app.HistoryMath.against(live, "prev_year"), today);
        check(fairYear[0][0].equals("2026-11-01") && fairYear[0][1].equals("2026-11-09"), "to today: this side stops at yesterday");
        check(fairYear[1][0].equals("2025-11-01") && fairYear[1][1].equals("2025-11-09"), "to today: last year stops at the same day");
        String[][] fairBefore = za.co.koenekt.app.HistoryMath.fair(live, za.co.koenekt.app.HistoryMath.against(live, "prev_period"), today);
        check(fairBefore[1][0].equals("2026-10-23") && fairBefore[1][1].equals("2026-10-31"), "to today: the period before is the nine days before");
        String[] firstDay = za.co.koenekt.app.HistoryMath.period("mtd", java.time.LocalDate.of(2026, 12, 1), true);
        check(za.co.koenekt.app.HistoryMath.fair(firstDay, za.co.koenekt.app.HistoryMath.against(firstDay, "prev_year"), java.time.LocalDate.of(2026, 12, 1)) == null, "nothing is compared on a period's firstDay day");
        String[][] closed = za.co.koenekt.app.HistoryMath.fair(lm, za.co.koenekt.app.HistoryMath.against(lm, "prev_year"), today);
        check(closed[0][1].equals("2026-10-31") && closed[1][1].equals("2025-10-31"), "a closed period is compared whole");
        check(za.co.koenekt.app.HistoryMath.fair(live, null, today) == null, "no comparison asked, none made");
        check(za.co.koenekt.app.HistoryMath.DEFAULT_LAYOUT.indexOf("\"kind\":\"live\"") < za.co.koenekt.app.HistoryMath.DEFAULT_LAYOUT.indexOf("\"kind\":\"kpi\""), "the standard dashboard opens with today");
        check(za.co.koenekt.app.HistoryMath.range(live, today).equals("1 Nov – today"), "today named");
        java.util.Map<String, za.co.koenekt.app.HistoryMath.Day> liveDays = new java.util.HashMap<>();
        liveDays.put("s2", day(1150, 1000, 700, Double.NaN)); liveDays.put("s9", day(230, 200, 150, Double.NaN));
        za.co.koenekt.app.HistoryMath.History plus = za.co.koenekt.app.HistoryMath.withToday(h, liveDays, today);
        za.co.koenekt.app.HistoryMath.Agg mtdLive = za.co.koenekt.app.HistoryMath.agg(plus, List.of("s1", "s2", "s9"), live[0], live[1], true);
        check(mtdLive.turnover == 4025 + 1150 + 230 && mtdLive.stores == 3, "today's live figures add to month to date, even for a store with no past days");
        check(!h.stores.get("s2").containsKey("2026-11-10"), "the loaded history is not changed");
        check(za.co.koenekt.app.HistoryMath.range(mtd, today).equals("1 Nov – 9 Nov")
                && za.co.koenekt.app.HistoryMath.nice("2025-11-01", today).equals("1 Nov 2025"), "dates as text");
    }
}
