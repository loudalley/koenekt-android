package za.co.koenekt.app;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Pure policy; the services still enforce every capability and company boundary. */
public final class AppPolicy {
    public static final String ADMIN = "https://admin.koenekt.co.za";
    public static final String DEFAULT_WORKSPACE = "https://people.koenekt.co.za";
    /** Koenekt Link relay: My stores asks shop PCs for live till figures through it. */
    public static final String LINK = "https://link.koenekt.co.za";
    /** Public policy pages (GitHub Pages from docs/); opened in the browser, never inside the WebView. */
    public static final String PRIVACY_URL = "https://loudalley.github.io/koenekt-android/privacy.html";
    public static final String DELETE_ACCOUNT_URL = "https://loudalley.github.io/koenekt-android/delete-account.html";
    private AppPolicy() {}

    /**
     * FLAG_SECURE (no screenshots, screen recording or recent-app previews): on in release builds
     * unless this phone's owner turned on Account → Allow screenshots on this phone (off by default,
     * turning it on asks for the phone's lock). Debug builds allow screenshots unless -PsecureScreens.
     */
    public static boolean protectScreen(boolean secureBuild, boolean screenshotsAllowedOnThisPhone) {
        return secureBuild && !screenshotsAllowedOnThisPhone;
    }

    /** Debug builds only (-PdevOrigin): a local People console on this PC, seen from the emulator. */
    private static String devOrigin = "";
    public static void allowDevOrigin(String origin) {
        if (!origin.matches("http://(10\\.0\\.2\\.2|127\\.0\\.0\\.1):[0-9]{2,5}"))
            throw new IllegalArgumentException("A development origin must be the emulator host.");
        devOrigin = origin;
    }
    public static boolean isDevOrigin(String origin) { return !devOrigin.isEmpty() && devOrigin.equals(origin); }

    /** Debug builds only (-PdevLink): a local Link relay on this PC, seen from the emulator. */
    private static String devLink = "";
    public static void allowDevLink(String origin) {
        if (!origin.matches("http://(10\\.0\\.2\\.2|127\\.0\\.0\\.1):[0-9]{2,5}"))
            throw new IllegalArgumentException("A development relay must be the emulator host.");
        devLink = origin;
    }
    public static String link() { return devLink.isEmpty() ? LINK : devLink; }

    /** Only the relay's phone API, over HTTPS (or the debug relay). */
    public static boolean linkAllowed(String url) {
        try {
            URI u = new URI(url), base = new URI(link());
            String path = u.getRawPath();
            return u.getScheme() != null && u.getScheme().equals(base.getScheme()) && u.getRawUserInfo() == null
                    && base.getHost().equals(u.getHost()) && base.getPort() == u.getPort()
                    && u.getRawQuery() == null && u.getRawFragment() == null
                    && path != null && path.matches("/api/(login|logout|stores|stores/[A-Za-z0-9_-]{4,64}/live"
                    + "|join|join/check|me|me/password|me/delete|invites|invites/revoke|team|team/remove|team/reset)");
        } catch (Exception e) { return false; }
    }

    /** Invite codes: 8 characters from this alphabet (no 0/O, 1/I/L), shown as XXXX-XXXX. */
    public static final String CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    /** "k7qm-xrt4 " -> "K7QMXRT4"; null unless it could be an invite code. */
    public static String inviteCode(String typed) {
        if (typed == null) return null;
        String code = typed.replaceAll("[\\s-]", "").toUpperCase(java.util.Locale.ROOT);
        if (code.length() != 8) return null;
        for (int i = 0; i < code.length(); i++) if (CODE_ALPHABET.indexOf(code.charAt(i)) < 0) return null;
        return code;
    }
    public static String prettyCode(String code) { return code.length() == 8 ? code.substring(0, 4) + "-" + code.substring(4) : code; }

    /**
     * The code in an invite link: https://link.koenekt.co.za/j#CODE (the QR; the code is in the
     * fragment so it never reaches a server log) or koenekt://join/CODE (the web page's button).
     * Anything else is refused; the code is still checked by the relay before anything is made.
     */
    public static String joinCode(String link) {
        try {
            URI u = new URI(link);
            if (u.getRawUserInfo() != null || u.getRawQuery() != null) throw new IllegalArgumentException();
            String code = null;
            if ("https".equals(u.getScheme()) && "link.koenekt.co.za".equals(u.getHost()) && u.getPort() == -1
                    && ("/j".equals(u.getRawPath()) || "/j/".equals(u.getRawPath())))
                code = inviteCode(u.getRawFragment());
            else if ("koenekt".equals(u.getScheme()) && "join".equals(u.getHost()) && u.getPort() == -1
                    && u.getRawFragment() == null && u.getRawPath() != null && u.getRawPath().matches("/[A-Za-z0-9-]{8,9}"))
                code = inviteCode(u.getRawPath().substring(1));
            if (code == null) throw new IllegalArgumentException();
            return code;
        } catch (Exception e) { throw new IllegalArgumentException("This invite link is not valid."); }
    }
    public static boolean isJoinLink(String link) {
        try {
            URI u = new URI(link);
            return ("https".equals(u.getScheme()) && "link.koenekt.co.za".equals(u.getHost()))
                    || ("koenekt".equals(u.getScheme()) && "join".equals(u.getHost()));
        } catch (Exception e) { return false; }
    }

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
        if (isDevOrigin(value)) return devOrigin;
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
            boolean secure = "https".equals(b.getScheme()) || (isDevOrigin(origin) && "http".equals(b.getScheme()));
            return secure && b.getRawUserInfo() == null
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
        if (caps.contains("people_read")) out.add(new Feature("People records", "Edit records, leavers, transfers, imports and exports", "/people"));
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
            out.add(new Feature("Reporter updates", "Shop PCs by name, tick and update or cancel, pause and resume", ADMIN + "/licences/#updates"));
            out.add(new Feature("Licence activity", "Licensing and Reporter update audit trail", ADMIN + "/licences/#activity"));
        }
        return out;
    }
}
