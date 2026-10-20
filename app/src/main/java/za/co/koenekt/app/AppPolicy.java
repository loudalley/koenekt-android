package za.co.koenekt.app;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Pure policy; the services still enforce every capability and company boundary. */
public final class AppPolicy {
    public static final String ADMIN = "https://admin.koenekt.co.za";
    public static final String DEFAULT_WORKSPACE = "https://people.koenekt.co.za";
    private AppPolicy() {}

    public static String setupWorkspace(String link) {
        try {
            URI uri = new URI(link);
            String path = uri.getPath();
            if (!"koenekt".equals(uri.getScheme()) || !"workspace".equals(uri.getHost())
                    || uri.getRawUserInfo() != null || uri.getPort() != -1
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || path == null || !path.matches("/[a-z0-9]+(?:-[a-z0-9]+)*")) throw new IllegalArgumentException();
            return workspace(path.substring(1));
        } catch (Exception e) { throw new IllegalArgumentException("This Koenekt setup link is invalid."); }
    }

    /** Accept exactly the intended session cookie, never an arbitrary cookie/header. */
    public static String sessionCookie(String cookies, String name) {
        if (!"adms_session".equals(name) && !"kls_session".equals(name)) throw new IllegalArgumentException("Unknown session cookie");
        if (cookies == null || cookies.indexOf('\r') >= 0 || cookies.indexOf('\n') >= 0) return "";
        for (String part : cookies.split(";")) {
            String value = part.trim();
            if (value.startsWith(name + "=") && value.length() > name.length() + 1) return value;
        }
        return "";
    }

    public static String workspace(String input) {
        String value = input.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.isEmpty()) return DEFAULT_WORKSPACE;
        if (value.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            value = value.equals("people") ? DEFAULT_WORKSPACE : "https://" + value + ".people.koenekt.co.za";
        } else if (!value.contains("://")) value = "https://" + value;
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            if (!"https".equals(uri.getScheme()) || uri.getRawUserInfo() != null || uri.getPort() != -1
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !(uri.getRawPath().isEmpty() || uri.getRawPath().equals("/"))
                    || host == null || !(host.equals("people.koenekt.co.za")
                    || host.matches("[a-z0-9]+(?:-[a-z0-9]+)*\\.people\\.koenekt\\.co\\.za"))) {
                throw new IllegalArgumentException();
            }
            return "https://" + host;
        } catch (Exception e) {
            throw new IllegalArgumentException("Enter your Koenekt workspace name or HTTPS People address.");
        }
    }

    public static boolean sameOrigin(String origin, String candidate) {
        try {
            URI a = new URI(origin), b = new URI(candidate);
            return "https".equals(b.getScheme()) && b.getRawUserInfo() == null
                    && a.getHost().equals(b.getHost()) && a.getPort() == b.getPort();
        } catch (Exception e) { return false; }
    }

    public static boolean allowed(String workspace, boolean operator, String candidate) {
        return sameOrigin(workspace, candidate) || (operator && sameOrigin(ADMIN, candidate)
                && (URI.create(candidate).getPath().equals("/licences")
                || URI.create(candidate).getPath().startsWith("/licences/")));
    }

    public record Feature(String name, String description, String path) {}

    public static List<Feature> features(Set<String> caps, boolean operator) {
        List<Feature> out = new ArrayList<>();
        if (caps.contains("people_read")) out.add(new Feature("People", "Staff, hiring, leavers, transfers, imports and clock enrollment", "/people"));
        if (caps.contains("reports")) out.add(new Feature("Reports", "Attendance, hours and PDF, Excel or CSV exports", "/reports"));
        if (caps.contains("dashboard")) out.add(new Feature("Live attendance", "Who is in, who is out and today's activity", "/dashboard"));
        if (caps.contains("payroll")) out.add(new Feature("Payroll", "Pay runs, adjustments, payslips and summaries", "/payroll"));
        if (caps.contains("clocks_read")) out.add(new Feature("Clocks", "Connections, users and delivery status", "/"));
        if (caps.contains("settings")) out.add(new Feature("Companies & settings", "Shops, company branding, shifts and attendance rules", "/#companies"));
        if (caps.contains("manage_users")) out.add(new Feature("User access", "Accounts, company access and permissions", "/admin/users"));
        if (caps.contains("activity_view")) out.add(new Feature("People activity", "Audit trail for this workspace", "/activity"));
        if (operator) {
            out.add(new Feature("Licences & machines", "Issue, renew, edit, suspend and manage activations", ADMIN + "/licences/#machines"));
            out.add(new Feature("Customers", "Customer details, hosted workspaces and setup links", ADMIN + "/licences/#customers"));
            out.add(new Feature("Reporter updates", "PC status, update one or all, cancel, pause and resume", ADMIN + "/licences/#updates"));
            out.add(new Feature("Licence activity", "Licensing and Reporter update audit trail", ADMIN + "/licences/#activity"));
        }
        return out;
    }
}
