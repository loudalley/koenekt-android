package za.co.koenekt.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Report downloads (William, 22 Nov 2026: "all reports drawn from web must have csv ... pdf
 * button for mobile and the site as well as excel nicely branded").
 *
 * One description of a report ({@link Model}: title, headline figures, tables of typed columns)
 * feeds the CSV and Excel (.xlsx) writers here and the branded PDF in {@link ReportPdf}. The
 * website builds the same files (koenekt-website assets/js/report-export.js). Plain Java with no
 * Android classes, so tools/ExportChecks.java checks it on the JVM.
 *
 * Column types: t text, d date (YYYY-MM-DD), m money, p percent (23.4 means 23.4 %), q quantity,
 * i whole number, n1 / n2 decimals. Rows hold raw values (Double, Long, String or null).
 */
final class ReportFiles {
    private ReportFiles() { }

    static final class Col {
        final String label, type;
        Col(String label, String type) { this.label = label; this.type = type; }
        boolean text() { return type.equals("t") || type.equals("d"); }
    }

    static final class Table {
        final String name;
        final Col[] cols;
        final List<Object[]> rows = new ArrayList<>();
        boolean total;
        String note = "";
        Table(String name, Col... cols) { this.name = name; this.cols = cols; }
    }

    static final class Model {
        String kind = "", title = "", store = "", account = "", from = "", to = "", made = "", main = "";
        final List<String> suppliers = new ArrayList<>();
        final List<Object[]> tiles = new ArrayList<>();          // {label, value, type}
        final List<Table> tables = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
        Table main() {
            for (Table t : tables) if (t.name.equals(main)) return t;
            return tables.isEmpty() ? null : tables.get(0);
        }
        String period() { return from.equals(to) ? from : from + " to " + to; }
    }

    static Col c(String label, String type) { return new Col(label, type); }

    static double num(Object v) {
        if (v == null) return Double.NaN;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { String s = v.toString().trim(); return s.isEmpty() ? Double.NaN : Double.parseDouble(s); }
        catch (NumberFormatException e) { return Double.NaN; }
    }

    private static String group(double n, int dp) {
        String s = String.format(Locale.ROOT, "%." + dp + "f", Math.abs(n));
        String whole = dp > 0 ? s.substring(0, s.indexOf('.')) : s, rest = dp > 0 ? s.substring(s.indexOf('.')) : "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < whole.length(); i++) {
            if (i > 0 && (whole.length() - i) % 3 == 0) b.append(' ');
            b.append(whole.charAt(i));
        }
        return (n < 0 ? "-" : "") + b + rest;
    }

    /** What a value looks like in the PDF and on screen. */
    static String fmt(Object v, String type) {
        if (type.equals("t") || type.equals("d")) return v == null ? "" : v.toString();
        double n = num(v);
        if (Double.isNaN(n)) return "—";
        switch (type) {
            case "m": return (n < 0 ? "-R " : "R ") + group(Math.abs(n), 2);
            case "p": return String.format(Locale.ROOT, "%.1f%%", n);
            case "i": return group(Math.round(n), 0);
            case "n1": return String.format(Locale.ROOT, "%.1f", n);
            case "n2": return String.format(Locale.ROOT, "%.2f", n);
            case "q":
                if (Math.abs(n - Math.round(n)) < 0.0005) return group(Math.round(n), 0);
                String q = group(n, 3);
                while (q.endsWith("0")) q = q.substring(0, q.length() - 1);
                return q;
            default: return v.toString();
        }
    }

    static String fileBase(Model m) {
        String s = ("Koenekt " + m.store + " " + m.title + " " + m.period()).replaceAll("[^\\w &().,-]+", " ");
        return s.replaceAll("\\s+", " ").trim();
    }

    // ---------------------------------------------------------------------- CSV
    private static String csvCell(String v) {
        if (v == null) return "";
        boolean quote = v.indexOf('"') >= 0 || v.indexOf(',') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0 || v.indexOf(';') >= 0;
        return quote ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }
    private static String plainNumber(double n) {
        double r = Math.round(n * 1000) / 1000.0;
        if (r == Math.rint(r) && Math.abs(r) < 1e15) return Long.toString((long) r);
        String s = String.format(Locale.ROOT, "%.3f", r);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** The report's main table as CSV (UTF-8 with a BOM so Excel reads it as UTF-8). */
    static byte[] csv(Model m) {
        Table t = m.main();
        StringBuilder b = new StringBuilder().append((char) 0xFEFF);
        if (t != null) {
            for (int i = 0; i < t.cols.length; i++) b.append(i > 0 ? "," : "").append(csvCell(t.cols[i].label));
            b.append("\r\n");
            for (Object[] row : t.rows) {
                for (int i = 0; i < t.cols.length; i++) {
                    Object v = i < row.length ? row[i] : null;
                    String cell;
                    if (t.cols[i].text()) cell = v == null ? "" : v.toString();
                    else { double n = num(v); cell = Double.isNaN(n) ? "" : plainNumber(n); }
                    b.append(i > 0 ? "," : "").append(csvCell(cell));
                }
                b.append("\r\n");
            }
        }
        return b.toString().getBytes(StandardCharsets.UTF_8);
    }
    static String csvName(Model m) {
        Table t = m.main();
        String n = (fileBase(m) + (t == null ? "" : " " + t.name)).replaceAll("[^\\w &().,-]+", " ").replaceAll("\\s+", " ").trim();
        return n + ".csv";
    }

    // --------------------------------------------------------------------- XLSX
    private static final String[] XF_TYPES = {"t", "d", "m", "p", "q", "i", "n1", "n2"};
    private static final int[] NUMFMT = {0, 164, 4, 165, 166, 3, 167, 2};
    private static int xf(String type, boolean bold) {
        int i = 0;
        while (i < XF_TYPES.length && !XF_TYPES[i].equals(type)) i++;
        return 4 + i * 2 + (bold ? 1 : 0);
    }
    static String xml(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch < 0x20 && ch != '\t' && ch != '\n' && ch != '\r') continue;
            switch (ch) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '"': b.append("&quot;"); break;
                default: b.append(ch);
            }
        }
        return b.toString();
    }
    static String colName(int i) {
        StringBuilder s = new StringBuilder();
        i += 1;
        while (i > 0) { int r = (i - 1) % 26; s.insert(0, (char) ('A' + r)); i = (i - 1) / 26; }
        return s.toString();
    }
    private static String cell(String ref, Object v, String type, int style) {
        if (type.equals("d") && v != null && v.toString().matches("\\d{4}-\\d{2}-\\d{2}")) {
            long serial = java.time.LocalDate.parse(v.toString()).toEpochDay() + 25569;     // 1970-01-01 is 25569
            return "<c r=\"" + ref + "\" s=\"" + style + "\"><v>" + serial + "</v></c>";
        }
        if (!type.equals("t") && !type.equals("d")) {
            double n = num(v);
            if (Double.isNaN(n)) return "<c r=\"" + ref + "\" s=\"" + style + "\"/>";
            return "<c r=\"" + ref + "\" s=\"" + style + "\"><v>" + plainNumber(n) + "</v></c>";
        }
        if (v == null || v.toString().isEmpty()) return "";
        return "<c r=\"" + ref + "\" t=\"inlineStr\" s=\"" + style + "\"><is><t xml:space=\"preserve\">" + xml(v.toString()) + "</t></is></c>";
    }
    private static String styles() {
        StringBuilder xfs = new StringBuilder("<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"><alignment wrapText=\"1\" vertical=\"center\"/></xf>"
                + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
                + "<xf numFmtId=\"0\" fontId=\"3\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>");
        for (int i = 0; i < XF_TYPES.length; i++)
            for (int b = 0; b < 2; b++)
                xfs.append("<xf numFmtId=\"").append(NUMFMT[i]).append("\" fontId=\"").append(b).append("\" fillId=\"0\" borderId=\"").append(b == 1 ? 2 : 0)
                        .append("\" xfId=\"0\" applyNumberFormat=\"1\"").append(b == 1 ? " applyFont=\"1\" applyBorder=\"1\"" : "").append("/>");
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<numFmts count=\"4\"><numFmt numFmtId=\"164\" formatCode=\"yyyy-mm-dd\"/><numFmt numFmtId=\"165\" formatCode=\"0.0&quot;%&quot;\"/><numFmt numFmtId=\"166\" formatCode=\"#,##0.###;-#,##0.###;0\"/><numFmt numFmtId=\"167\" formatCode=\"0.0\"/></numFmts>"
                + "<fonts count=\"4\"><font><sz val=\"10\"/><name val=\"Calibri\"/><family val=\"2\"/></font><font><b/><sz val=\"10\"/><name val=\"Calibri\"/><family val=\"2\"/></font><font><b/><sz val=\"14\"/><name val=\"Calibri\"/><family val=\"2\"/></font><font><sz val=\"10\"/><color rgb=\"FF6B6B68\"/><name val=\"Calibri\"/><family val=\"2\"/></font></fonts>"
                + "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFEFEFEA\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
                + "<borders count=\"3\"><border><left/><right/><top/><bottom/><diagonal/></border><border><left/><right/><top/><bottom style=\"thin\"><color rgb=\"FF9A9A94\"/></bottom><diagonal/></border><border><left/><right/><top style=\"thin\"><color rgb=\"FF9A9A94\"/></top><bottom/><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"" + (4 + XF_TYPES.length * 2) + "\">" + xfs + "</cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>";
    }
    private static String sheet(List<String> rows, double[] widths, int freeze, String filter, boolean landscape) {
        StringBuilder b = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        b.append(freeze > 0 ? "<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"" + freeze + "\" topLeftCell=\"A" + (freeze + 1) + "\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>"
                : "<sheetViews><sheetView workbookViewId=\"0\"/></sheetViews>");
        b.append("<sheetFormatPr defaultRowHeight=\"13.8\"/>");
        if (widths.length > 0) {
            b.append("<cols>");
            for (int i = 0; i < widths.length; i++) b.append(String.format(Locale.ROOT, "<col min=\"%d\" max=\"%d\" width=\"%.1f\" customWidth=\"1\"/>", i + 1, i + 1, widths[i]));
            b.append("</cols>");
        }
        b.append("<sheetData>");
        for (String r : rows) b.append(r);
        b.append("</sheetData>");
        if (filter != null && !filter.isEmpty()) b.append("<autoFilter ref=\"").append(filter).append("\"/>");
        b.append("<pageMargins left=\"0.5\" right=\"0.5\" top=\"0.6\" bottom=\"0.6\" header=\"0.3\" footer=\"0.3\"/><pageSetup orientation=\"")
                .append(landscape ? "landscape" : "portrait").append("\"/></worksheet>");
        return b.toString();
    }
    private static String sheetName(String name, Map<String, Boolean> used) {
        String base = name.replaceAll("[\\[\\]:*?/\\\\]", " ").trim();
        if (base.length() > 31) base = base.substring(0, 31).trim();
        if (base.isEmpty()) base = "Sheet";
        String n = base;
        for (int i = 2; used.containsKey(n.toLowerCase(Locale.ROOT)); i++) n = (base.length() > 28 ? base.substring(0, 28) : base) + " " + i;
        used.put(n.toLowerCase(Locale.ROOT), true);
        return n;
    }

    /** A real .xlsx: a Summary sheet, then one sheet per table with a frozen bold header and a filter. */
    static byte[] xlsx(Model m) throws IOException {
        Map<String, Boolean> used = new LinkedHashMap<>();
        List<String[]> sheets = new ArrayList<>();         // {name, xml}
        List<String> sr = new ArrayList<>();
        int[] r = {1};
        java.util.function.Consumer<String> row = cells -> { sr.add("<row r=\"" + r[0] + "\">" + cells + "</row>"); r[0]++; };
        row.accept(cell("A1", "Koenekt · " + m.title, "t", 2));
        row.accept(cell("A" + r[0], "Store", "t", 3) + cell("B" + r[0], m.store + (m.account.isEmpty() ? "" : " (" + m.account + ")"), "t", 0));
        row.accept(cell("A" + r[0], "Period", "t", 3) + cell("B" + r[0], m.period(), "t", 0));
        if (!m.suppliers.isEmpty()) row.accept(cell("A" + r[0], "Suppliers", "t", 3) + cell("B" + r[0], String.join(", ", m.suppliers), "t", 0));
        if (!m.made.isEmpty()) row.accept(cell("A" + r[0], "Run on the shop PC", "t", 3) + cell("B" + r[0], m.made, "t", 0));
        r[0]++;
        for (Object[] t : m.tiles) row.accept(cell("A" + r[0], (String) t[0], "t", 0) + cell("B" + r[0], t[1], (String) t[2], xf((String) t[2], false)));
        if (!m.notes.isEmpty()) { r[0]++; for (String n : m.notes) row.accept(cell("A" + r[0], n, "t", 3)); }
        sheets.add(new String[]{sheetName("Summary", used), sheet(sr, new double[]{24, 44}, 0, "", false)});
        List<String> filters = new ArrayList<>();
        filters.add("");
        for (Table t : m.tables) {
            List<String> rows = new ArrayList<>();
            double[] widths = new double[t.cols.length];
            StringBuilder head = new StringBuilder("<row r=\"1\" ht=\"18\" customHeight=\"1\">");
            for (int i = 0; i < t.cols.length; i++) {
                widths[i] = Math.max(8, t.cols[i].label.length() + 2);
                head.append(cell(colName(i) + "1", t.cols[i].label, "t", 1));
            }
            rows.add(head.append("</row>").toString());
            for (int ri = 0; ri < t.rows.size(); ri++) {
                Object[] vals = t.rows.get(ri);
                boolean bold = t.total && ri == t.rows.size() - 1;
                StringBuilder b = new StringBuilder("<row r=\"" + (ri + 2) + "\">");
                for (int i = 0; i < t.cols.length; i++) {
                    Object v = i < vals.length ? vals[i] : null;
                    String ty = t.cols[i].type;
                    int len = t.cols[i].text() ? (v == null ? 0 : v.toString().length()) : fmt(v, ty).length();
                    if (len + 2 > widths[i]) widths[i] = Math.min(ty.equals("t") ? 48 : 18, len + 2);
                    b.append(cell(colName(i) + (ri + 2), v, ty, ty.equals("t") && !bold ? 0 : xf(ty, bold)));
                }
                rows.add(b.append("</row>").toString());
            }
            if (!t.note.isEmpty()) rows.add("<row r=\"" + (t.rows.size() + 3) + "\">" + cell("A" + (t.rows.size() + 3), t.note, "t", 3) + "</row>");
            String filter = t.rows.isEmpty() ? "" : "A1:" + colName(t.cols.length - 1) + (t.rows.size() + 1);
            filters.add(filter);
            sheets.add(new String[]{sheetName(t.name, used), sheet(rows, widths, 1, filter, t.cols.length > 7)});
        }
        StringBuilder types = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        StringBuilder book = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
        StringBuilder rels = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < sheets.size(); i++) {
            types.append("<Override PartName=\"/xl/worksheets/sheet").append(i + 1).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
            book.append("<sheet name=\"").append(xml(sheets.get(i)[0])).append("\" sheetId=\"").append(i + 1).append("\" r:id=\"rId").append(i + 1).append("\"/>");
            rels.append("<Relationship Id=\"rId").append(i + 1).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet").append(i + 1).append(".xml\"/>");
            String f = filters.get(i);
            if (!f.isEmpty()) {
                String[] ab = f.split(":");
                names.append("<definedName name=\"_xlnm._FilterDatabase\" localSheetId=\"").append(i).append("\" hidden=\"1\">'")
                        .append(xml(sheets.get(i)[0].replace("'", "''"))).append("'!$A$1:$").append(ab[1].replaceAll("\\d", "")).append("$").append(ab[1].replaceAll("\\D", "")).append("</definedName>");
            }
        }
        types.append("</Types>");
        book.append("</sheets>").append(names.length() > 0 ? "<definedNames>" + names + "</definedNames>" : "").append("</workbook>");
        rels.append("<Relationship Id=\"rId").append(sheets.size() + 1).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            put(z, "[Content_Types].xml", types.toString());
            put(z, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            put(z, "xl/workbook.xml", book.toString());
            put(z, "xl/_rels/workbook.xml.rels", rels.toString());
            put(z, "xl/styles.xml", styles());
            for (int i = 0; i < sheets.size(); i++) put(z, "xl/worksheets/sheet" + (i + 1) + ".xml", sheets.get(i)[1]);
        }
        return out.toByteArray();
    }
    static String xlsxName(Model m) { return fileBase(m) + ".xlsx"; }
    static String pdfName(Model m) { return fileBase(m) + ".pdf"; }

    private static void put(ZipOutputStream z, String name, String data) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(data.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }
}
