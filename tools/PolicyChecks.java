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
        System.out.println("Policy checks passed: " + checks);
    }
}
