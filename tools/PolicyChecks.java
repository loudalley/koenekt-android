import java.util.Set;
import za.co.koenekt.app.AppPolicy;

public class PolicyChecks {
    private static int checks;
    private static void check(boolean value, String name) { checks++; if (!value) throw new AssertionError(name); }
    private static void reject(String value) {
        boolean rejected = false;
        try { AppPolicy.workspace(value); } catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, "Rejected workspace: " + value);
    }
    public static void main(String[] args) {
        check(AppPolicy.workspace("").equals(AppPolicy.DEFAULT_WORKSPACE), "default workspace");
        check(AppPolicy.workspace("latinos").equals("https://latinos.people.koenekt.co.za"), "customer slug");
        check(AppPolicy.workspace("HTTPS://PEOPLE.KOENEKT.CO.ZA/").equals(AppPolicy.DEFAULT_WORKSPACE), "normalisation");
        for (String value : new String[]{"http://people.koenekt.co.za", "https://people.koenekt.co.za.evil.com", "https://evil.com", "https://admin.koenekt.co.za", "https://user@people.koenekt.co.za", "https://people.koenekt.co.za:8443", "https://people.koenekt.co.za/people", "https://people.koenekt.co.za?url=evil", "https://people.koenekt.co.za#evil", "https://a.b.people.koenekt.co.za", "file:///tmp/login", "https://people.koenekt.co.za\\@evil.com"}) reject(value);
        String workspace = AppPolicy.workspace("one");
        check(AppPolicy.allowed(workspace, false, workspace + "/people"), "client workspace allowed");
        check(!AppPolicy.allowed(workspace, false, AppPolicy.ADMIN + "/licences/"), "client blocked from licence service");
        check(!AppPolicy.allowed(workspace, false, AppPolicy.workspace("two") + "/people"), "other tenant blocked");
        check(AppPolicy.allowed(workspace, true, AppPolicy.ADMIN + "/licences/#updates"), "authenticated operator allowed");
        check(!AppPolicy.allowed(workspace, true, AppPolicy.ADMIN + "/api/v1/checkin"), "no operator navigation outside admin screen");
        check(!AppPolicy.allowed(workspace, true, "http://admin.koenekt.co.za/licences/"), "no unencrypted operator page");
        check(!AppPolicy.allowed(workspace, true, "https://admin.koenekt.co.za.evil.com/licences/"), "operator lookalike blocked");
        var client = AppPolicy.features(Set.of("people_read", "people_hire", "people_leave", "reports"), false);
        check(client.size() == 2, "client sees enabled sections only");
        check(client.stream().noneMatch(f -> f.path().startsWith("https:")), "client has no management modules");
        check(AppPolicy.features(Set.of(), false).isEmpty(), "unprivileged user has no sections");
        check(AppPolicy.features(Set.of(), true).size() == 4, "operator may use management without a People session");
        check(AppPolicy.features(Set.of("manage_users"), false).stream().noneMatch(f -> f.path().equals("/activity")), "branch user manager has no company-wide audit shortcut");
        check(AppPolicy.setupWorkspace("koenekt://workspace/example").equals("https://example.people.koenekt.co.za"), "organisation setup link");
        check(AppPolicy.setupWorkspace("koenekt://workspace/people").equals(AppPolicy.DEFAULT_WORKSPACE), "default setup link");
        for (String link : new String[]{"koenekt://workspace/evil.com", "https://workspace/example", "koenekt://workspace/example?password=x", "koenekt://user@workspace/example", "koenekt://workspace/a/b", "koenekt://workspace/example#secret"}) {
            boolean rejected = false; try { AppPolicy.setupWorkspace(link); } catch (IllegalArgumentException e) { rejected = true; }
            check(rejected, "Reject unsafe setup link " + link);
        }
        check(AppPolicy.sessionCookie("other=one; adms_session=sample-token; third=two", "adms_session").equals("adms_session=sample-token"), "save only selected session cookie");
        check(AppPolicy.sessionCookie("kls_session=sample", "adms_session").isEmpty(), "do not interchange operator/client cookies");
        check(AppPolicy.sessionCookie("adms_session=x\r\nHost: evil", "adms_session").isEmpty(), "reject cookie header injection");
        check(AppPolicy.sessionCookie("adms_session=", "adms_session").isEmpty(), "reject empty token");
        for (String page : new String[]{AppPolicy.PRIVACY_URL, AppPolicy.DELETE_ACCOUNT_URL}) {
            check(page.startsWith("https://loudalley.github.io/koenekt-android/"), "policy page is the published HTTPS site: " + page);
            check(!AppPolicy.allowed(AppPolicy.DEFAULT_WORKSPACE, true, page), "policy page opens outside the trusted WebView: " + page);
        }
        // Development origin: never active unless a debug build sets it, and only the emulator host.
        check(!AppPolicy.sameOrigin("http://10.0.2.2:8765", "http://10.0.2.2:8765/people"), "plain HTTP rejected by default");
        boolean rejected = false;
        try { AppPolicy.allowDevOrigin("http://evil.example:80"); } catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, "development origin limited to the emulator host");
        AppPolicy.allowDevOrigin("http://10.0.2.2:8765");
        check(AppPolicy.sameOrigin("http://10.0.2.2:8765", "http://10.0.2.2:8765/people"), "development origin allowed once set");
        check(!AppPolicy.sameOrigin("http://10.0.2.2:8765", "http://10.0.2.2:9999/"), "other local ports still rejected");
        check(!AppPolicy.sameOrigin(AppPolicy.DEFAULT_WORKSPACE, "http://people.koenekt.co.za/"), "production stays HTTPS-only");
        // My stores: only the relay's phone API, only over HTTPS, the debug relay only when set.
        check(AppPolicy.linkAllowed(AppPolicy.LINK + "/api/login"), "relay sign-in");
        check(AppPolicy.linkAllowed(AppPolicy.LINK + "/api/stores"), "relay store list");
        check(AppPolicy.linkAllowed(AppPolicy.LINK + "/api/stores/a1b2c30000/live"), "relay live figures");
        for (String path : new String[]{"join", "join/check", "me", "me/password", "me/delete", "invites", "invites/revoke",
                "team", "team/remove", "team/reset"})
            check(AppPolicy.linkAllowed(AppPolicy.LINK + "/api/" + path), "relay join/account call " + path);
        // Dashboard, Compare, Reports and Groups (Reporter 1.9.106 relay)
        for (String path : new String[]{"stores/a1b2c30000/range", "stores/a1b2c30000/order", "stores/a1b2c30000/rosi",
                "stores/a1b2c30000/turnover", "groups", "groups/delete", "me/dashboard", "history",
                "history?from=2026-11-01&to=2026-11-09"})
            check(AppPolicy.linkAllowed(AppPolicy.LINK + "/api/" + path), "relay reports call " + path);
        for (String url : new String[]{"https://link.koenekt.co.za/api/stores?from=2026-11-01&to=2026-11-09",
                "https://link.koenekt.co.za/api/history?from=2026-11-01&to=2026-11-09&x=1",
                "https://link.koenekt.co.za/api/history?stores=../op", "https://link.koenekt.co.za/api/stores/a1b2c30000/delete",
                "https://link.koenekt.co.za/api/history/../op/agents?from=2026-11-01&to=2026-11-09"})
            check(!AppPolicy.linkAllowed(url), "relay call refused: " + url);
        for (String url : new String[]{"https://link.koenekt.co.za/op/invites", "https://link.koenekt.co.za/op/users/reset",
                "https://link.koenekt.co.za/api/join/../../op/invites", "https://link.koenekt.co.za/api/team/remove/x",
                "https://link.koenekt.co.za/api/join#K7QMXRT4", "https://link.koenekt.co.za/j"})
            check(!AppPolicy.linkAllowed(url), "relay call refused: " + url);
        // Invite codes and links (the QR, the web page's button, or typed).
        check("K7QMXRT4".equals(AppPolicy.inviteCode(" k7qm-xrt4 ")), "typed code normalised");
        for (String bad : new String[]{"K7QM-XRT", "K7QM-XRT45", "K7QM-XRT0", "K7QM-XRTO", "K7QM-XRT1", "K7QM XRTL", "", null})
            check(AppPolicy.inviteCode(bad) == null, "code refused: " + bad);
        check("K7QMXRT4".equals(AppPolicy.joinCode("https://link.koenekt.co.za/j#K7QMXRT4")), "QR link");
        check("K7QMXRT4".equals(AppPolicy.joinCode("https://link.koenekt.co.za/j#k7qm-xrt4")), "QR link, dashed");
        check("K7QMXRT4".equals(AppPolicy.joinCode("koenekt://join/K7QMXRT4")), "app link from the web page");
        for (String link : new String[]{"http://link.koenekt.co.za/j#K7QMXRT4", "https://link.koenekt.co.za.evil.com/j#K7QMXRT4",
                "https://evil.com/j#K7QMXRT4", "https://link.koenekt.co.za/j?code=K7QMXRT4", "https://link.koenekt.co.za/other#K7QMXRT4",
                "https://user@link.koenekt.co.za/j#K7QMXRT4", "https://link.koenekt.co.za:444/j#K7QMXRT4", "https://link.koenekt.co.za/j",
                "koenekt://join/K7QMXRT", "koenekt://join/K7QMXRT4/x", "koenekt://join/K7QMXRT4?x=1", "koenekt://workspace/K7QMXRT4"}) {
            boolean refused = false; try { AppPolicy.joinCode(link); } catch (IllegalArgumentException e) { refused = true; }
            check(refused, "invite link refused: " + link);
        }
        check(AppPolicy.isJoinLink("https://link.koenekt.co.za/j#X") && AppPolicy.isJoinLink("koenekt://join/X")
                && !AppPolicy.isJoinLink("koenekt://workspace/demo"), "join links told apart from setup links");
        for (String url : new String[]{"http://link.koenekt.co.za/api/stores", "https://link.koenekt.co.za.evil.com/api/stores",
                "https://link.koenekt.co.za/op/agents", "https://link.koenekt.co.za/agent/poll", "https://link.koenekt.co.za/api/stores/../op/live",
                "https://user@link.koenekt.co.za/api/stores", "https://link.koenekt.co.za:8443/api/stores",
                "https://link.koenekt.co.za/api/stores?x=1", AppPolicy.ADMIN + "/api/stores", "http://10.0.2.2:18198/api/stores"})
            check(!AppPolicy.linkAllowed(url), "relay call refused: " + url);
        rejected = false;
        try { AppPolicy.allowDevLink("https://evil.example:443"); } catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, "development relay limited to the emulator host");
        AppPolicy.allowDevLink("http://10.0.2.2:18198");
        check(AppPolicy.linkAllowed("http://10.0.2.2:18198/api/stores"), "development relay allowed once set");
        check(!AppPolicy.linkAllowed(AppPolicy.LINK + "/api/stores"), "one relay at a time");
        // Screenshots: blocked in release builds unless this phone's owner allowed them.
        check(AppPolicy.protectScreen(true, false), "release build blocks screenshots by default");
        check(!AppPolicy.protectScreen(true, true), "release build allows screenshots once turned on for this phone");
        check(!AppPolicy.protectScreen(false, false), "debug build allows screenshots");
        check(!AppPolicy.protectScreen(false, true), "debug build allows screenshots with the switch on");
        System.out.println("Policy checks passed: " + checks);
    }
}
