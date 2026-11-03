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
        System.out.println("Store checks passed: " + checks);
    }
}
