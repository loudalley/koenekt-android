package za.co.koenekt.app;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * JVM checks for the report downloads' CSV and Excel writers (ReportFiles). The PDF is drawn by
 * Android (ReportPdf) and is checked on a device. Writes sample files to the folder given as the
 * first argument (default app/build/export-checks) so they can be opened in Excel or openpyxl.
 *
 *   javac -encoding UTF-8 -d app/build/export-checks-classes app/src/main/java/za/co/koenekt/app/ReportFiles.java tools/ExportChecks.java
 *   java -cp app/build/export-checks-classes za.co.koenekt.app.ExportChecks
 */
public final class ExportChecks {
    private static int passed;

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        passed++;
    }

    private static ReportFiles.Model sample() {
        ReportFiles.Model m = new ReportFiles.Model();
        m.kind = "range"; m.title = "Range report"; m.store = "Demo Superette"; m.from = "2026-11-01"; m.to = "2026-11-21";
        m.made = "2026-11-22 08:15";
        m.suppliers.add("Sasko");
        m.tiles.add(new Object[]{"Turnover", 123456.78, "m"});
        m.tiles.add(new Object[]{"GP %", 26.08, "p"});
        m.notes.add("A note & <something> \"quoted\"");
        ReportFiles.Table days = new ReportFiles.Table("Days", ReportFiles.c("Date", "d"), ReportFiles.c("Turnover", "m"), ReportFiles.c("GP %", "p"));
        days.rows.add(new Object[]{"2026-11-01", 5000.0, 25.6});
        days.rows.add(new Object[]{"2026-11-02", null, Double.NaN});
        m.tables.add(days);
        ReportFiles.Table items = new ReportFiles.Table("Items: all/[best]", ReportFiles.c("Item no", "t"), ReportFiles.c("Item", "t"), ReportFiles.c("Qty", "q"),
                ReportFiles.c("Sales", "m"), ReportFiles.c("GP %", "p"));
        items.rows.add(new Object[]{"0012", "Château crème \"special\", long", 2.345, 1000.0, 31.03});
        items.rows.add(new Object[]{"0013", "Line\nbreak", 12L, -45.5, null});
        items.rows.add(new Object[]{"", "Total", 14.345, 954.5, 30.0});
        items.total = true;
        items.note = "The 2 best sellers of 812 lines.";
        m.tables.add(items);
        m.main = "Items: all/[best]";
        return m;
    }

    public static void main(String[] args) throws Exception {
        Path out = Paths.get(args.length > 0 ? args[0] : "app/build/export-checks");
        Files.createDirectories(out);

        // formatting
        check(ReportFiles.fmt(123456.78, "m").equals("R 123 456.78"), "money grouping");
        check(ReportFiles.fmt(-200.0, "m").equals("-R 200.00"), "negative money");
        check(ReportFiles.fmt(26.08, "p").equals("26.1%"), "percent");
        check(ReportFiles.fmt(2.345, "q").equals("2.345") && ReportFiles.fmt(12L, "q").equals("12") && ReportFiles.fmt(2.5, "q").equals("2.5"), "quantity");
        check(ReportFiles.fmt(null, "m").equals("—") && ReportFiles.fmt("x", "m").equals("—"), "missing number");
        check(ReportFiles.fmt(1234567.0, "i").equals("1 234 567"), "whole number");
        check(ReportFiles.colName(0).equals("A") && ReportFiles.colName(25).equals("Z") && ReportFiles.colName(26).equals("AA") && ReportFiles.colName(701).equals("ZZ"), "column names");
        check(ReportFiles.xml("a&b<c>\"d\u0001").equals("a&amp;b&lt;c&gt;&quot;d"), "xml escaping drops control characters");

        ReportFiles.Model m = sample();
        check(ReportFiles.fileBase(m).equals("Koenekt Demo Superette Range report 2026-11-01 to 2026-11-21"), "file name: " + ReportFiles.fileBase(m));

        // CSV: the main table, BOM, quoted cells, plain numbers
        String csv = new String(ReportFiles.csv(m), StandardCharsets.UTF_8);
        check(csv.startsWith("\uFEFFItem no,Item,Qty,Sales,GP %\r\n"), "csv header");
        check(csv.contains("0012,\"Château crème \"\"special\"\", long\",2.345,1000,31.03\r\n"), "csv quoting and numbers: " + csv);
        check(csv.contains("0013,\"Line\nbreak\",12,-45.5,\r\n"), "csv line break kept quoted, missing number empty");
        Files.write(out.resolve(ReportFiles.csvName(m)), ReportFiles.csv(m));

        // XLSX: the parts a spreadsheet needs, sheet names cleaned
        byte[] x = ReportFiles.xlsx(m);
        Files.write(out.resolve(ReportFiles.xlsxName(m)), x);
        java.util.Map<String, String> parts = new java.util.HashMap<>();
        try (ZipInputStream z = new ZipInputStream(new java.io.ByteArrayInputStream(x), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) parts.put(e.getName(), new String(z.readAllBytes(), StandardCharsets.UTF_8));
        }
        for (String p : new String[]{"[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/styles.xml",
                "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml", "xl/worksheets/sheet3.xml"})
            check(parts.containsKey(p), "xlsx part " + p);
        String book = parts.get("xl/workbook.xml");
        check(book.contains("name=\"Summary\"") && book.contains("name=\"Days\"") && book.contains("name=\"Items  all  best\""), "sheet names: " + book);
        String days = parts.get("xl/worksheets/sheet2.xml");
        check(days.contains("<v>46327</v>"), "2026-11-01 is Excel day 46327");
        check(days.contains("state=\"frozen\"") && days.contains("<autoFilter ref=\"A1:C3\"/>"), "frozen header and filter");
        String items = parts.get("xl/worksheets/sheet3.xml");
        check(items.contains("Château crème &quot;special&quot;, long"), "text escaped once");
        check(items.contains("<v>2.345</v>") && items.contains("<v>-45.5</v>"), "numbers stay numbers");

        System.out.println("ExportChecks: " + passed + " passed; samples in " + out.toAbsolutePath());
    }
}
