package za.co.koenekt.app;

import android.content.Intent;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import android.content.res.ColorStateList;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * A store owner's team on My stores (Link relay /api/team and /api/invites): who has a login,
 * invite staff with a QR code and a code to type (shown once, shareable), give a staff member
 * a reset code for a new password, remove a staff login, revoke an open invite. Owners invite
 * staff only; a second owner comes from Koenekt.
 */
final class TeamFlow implements MainActivity.Flow {
    private final MainActivity a;
    private JSONArray team, invites;
    private String you = "", error = "";
    private boolean loading;
    /** The code just made (invite or reset), shown once until Done. */
    private JSONObject shown;
    private String shownTitle = "", shownFor = "";

    TeamFlow(MainActivity activity) { a = activity; }
    private int dp(float n) { return a.dp(n); }

    @Override public void show() {
        if (shown != null) { codeScreen(); return; }
        if (team == null && !loading) load();
        listScreen();
    }

    private void load() {
        loading = true; error = "";
        a.linkRequest("GET", "/api/team", null, data -> {
            team = data.optJSONArray("team"); you = data.optString("you");
            a.linkRequest("GET", "/api/invites", null, more -> {
                invites = more.optJSONArray("invites"); loading = false; show();
            }, this::failed);
        }, this::failed);
    }
    private void failed(String message) { loading = false; error = message; show(); }

    private void listScreen() {
        a.setBar("Team", true, Ui.iconButton(a, R.drawable.ic_refresh, "Refresh", () -> { team = null; show(); }));
        a.onBack(() -> a.leaveFlow(MainActivity.TAB_ACCOUNT));
        LinearLayout page = a.page();
        page.addView(Ui.text(a, "People with a My stores login for your stores. Staff see sales, not gross profit.", 15, Ui.MUTED));
        Ui.gap(page, 16);
        page.addView(Ui.primary(a, "Invite staff", this::invite), Ui.fill(a, 54));
        Ui.gap(page, 24);
        if (!error.isEmpty()) { page.addView(Ui.notice(a, error, true)); Ui.gap(page, 16); }
        if (loading || team == null) { if (error.isEmpty()) page.addView(Ui.loading(a)); return; }

        page.addView(Ui.label(a, "Logins")); Ui.gap(page, 8);
        LinearLayout card = list();
        for (int i = 0; i < team.length(); i++) {
            JSONObject t = team.optJSONObject(i); if (t == null) continue;
            String name = t.optString("username");
            boolean staff = "staff".equals(t.optString("role")), me = name.equals(you);
            String sub = (staff ? "Staff" : "Owner") + (me ? " · you" : "") + " · "
                    + (t.isNull("last_login") ? "never signed in" : "signed in " + ago(t.optDouble("last_login")));
            if (card.getChildCount() > 0) card.addView(Ui.divider(a, 16));
            card.addView(Ui.listRow(a, Ui.avatar(a, name.replace('.', ' '), 40), name, sub, null,
                    staff && !me ? () -> staffMenu(name) : null));
        }
        page.addView(card);

        int open = 0;
        LinearLayout codes = list();
        if (invites != null) for (int i = 0; i < invites.length(); i++) {
            JSONObject inv = invites.optJSONObject(i);
            if (inv == null || !"open".equals(inv.optString("status"))) continue;
            open++;
            String label = inv.optString("label").isEmpty() ? "Staff invite" : inv.optString("label");
            String sub = "Works until " + when(inv.optDouble("expires")) + " · "
                    + (inv.optInt("uses") - inv.optInt("uses_left")) + " of " + inv.optInt("uses") + " used";
            String id = inv.optString("id");
            if (codes.getChildCount() > 0) codes.addView(Ui.divider(a, 16));
            codes.addView(Ui.listRow(a, null, label, sub, null, () -> revoke(id, label)));
        }
        if (open > 0) {
            Ui.gap(page, 24); page.addView(Ui.label(a, "Open invites")); Ui.gap(page, 8); page.addView(codes);
            Ui.gap(page, 8); page.addView(Ui.text(a, "Tap an invite to revoke it. The code itself is only shown when it is made.", 13, Ui.MUTED));
        }
    }

    private LinearLayout list() {
        LinearLayout l = Ui.column(a); l.setBackground(Ui.outline(Ui.SURFACE, Ui.LINE, 1, 20, a)); l.setClipToOutline(true); return l;
    }

    private void invite() {
        LinearLayout box = Ui.column(a); box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(Ui.text(a, "Makes a QR code and a code to type. The new staff member chooses their own username and password.", 14, Ui.MUTED));
        Ui.gap(box, 14);
        EditText note = Ui.field(box, "Note for yourself", "e.g. Thandi, tills", Ui.textInput());
        box.addView(Ui.label(a, "Code works for")); Ui.gap(box, 4);
        RadioGroup group = new RadioGroup(a); group.setOrientation(RadioGroup.HORIZONTAL);
        String[] names = {"1 day", "2 days"}; int[] ids = new int[names.length];
        for (int i = 0; i < names.length; i++) {
            RadioButton r = new RadioButton(a); r.setText(names[i]); r.setTextColor(Ui.INK); ids[i] = android.view.View.generateViewId(); r.setId(ids[i]);
            r.setButtonTintList(ColorStateList.valueOf(Ui.LIME)); r.setMinHeight(dp(48)); r.setPadding(0, 0, dp(16), 0);
            group.addView(r);
        }
        group.check(ids[1]);
        box.addView(group);
        Ui.dialog(a).setTitle("Invite staff").setView(box).setNegativeButton("Cancel", null)
                .setPositiveButton("Make code", (d, w) -> {
                    try {
                        JSONObject body = new JSONObject().put("label", note.getText().toString().trim())
                                .put("hours", group.getCheckedRadioButtonId() == ids[0] ? 24 : 48);
                        a.linkRequest("POST", "/api/invites", body, data -> {
                            shown = data; shownTitle = "Invite staff";
                            shownFor = note.getText().toString().trim();
                            team = null; show();
                        }, message -> a.toast(message));
                    } catch (Exception e) { a.toast("Could not make the invite."); }
                }).show();
    }

    private void staffMenu(String name) {
        Ui.choose(a, name, new String[]{"Give a reset code (new password)", "Remove from My stores"}, -1, which -> {
            if (which == 0) resetCode(name); else remove(name);
        });
    }

    private void resetCode(String name) {
        Ui.dialog(a).setTitle("Reset code for " + name + "?")
                .setMessage("They type it in the app (Join with a code) and choose a new password. Their phones are then signed out. Their current password works until then.")
                .setPositiveButton("Make code", (d, w) -> {
                    try {
                        a.linkRequest("POST", "/api/team/reset", new JSONObject().put("username", name), data -> {
                            shown = data; shownTitle = "Reset code"; shownFor = "New password for " + name; show();
                        }, message -> a.toast(message));
                    } catch (Exception e) { a.toast("Could not make the code."); }
                }).setNegativeButton("Cancel", null).show();
    }

    private void remove(String name) {
        Ui.dialog(a).setTitle("Remove " + name + "?")
                .setMessage("Their My stores login is deleted and their phones are signed out. You can invite them again later.")
                .setPositiveButton("Remove", (d, w) -> {
                    try {
                        a.linkRequest("POST", "/api/team/remove", new JSONObject().put("username", name), data -> {
                            a.toast(name + " is removed."); team = null; show();
                        }, message -> a.toast(message));
                    } catch (Exception e) { a.toast("Could not remove the login."); }
                }).setNegativeButton("Cancel", null).show();
    }

    private void revoke(String id, String label) {
        Ui.dialog(a).setTitle("Revoke this invite?").setMessage(label + "\n\nNobody can use its code after this.")
                .setPositiveButton("Revoke", (d, w) -> {
                    try {
                        a.linkRequest("POST", "/api/invites/revoke", new JSONObject().put("id", id), data -> {
                            team = null; show();
                        }, message -> a.toast(message));
                    } catch (Exception e) { a.toast("Could not revoke the invite."); }
                }).setNegativeButton("Cancel", null).show();
    }

    /** The code just made: QR (always black on white), the code to type, Share and Done. */
    private void codeScreen() {
        a.setBar(shownTitle, true);
        Runnable done = () -> { shown = null; team = null; show(); };
        a.onBack(done);
        LinearLayout page = a.page();
        boolean reset = shownTitle.startsWith("Reset");
        page.addView(Ui.title(a, reset ? "Give them this code" : "Show this to the new staff member", 24)); Ui.gap(page, 6);
        if (!shownFor.isEmpty()) { page.addView(Ui.text(a, shownFor, 15, Ui.INK)); Ui.gap(page, 4); }
        page.addView(Ui.text(a, reset
                ? "They open Koenekt, tap Join with a code, type it and choose a new password."
                : "They scan the QR code with their phone's camera, or open Koenekt, tap Join with a code and type it. Then they choose their own username and password.", 15, Ui.MUTED));
        Ui.gap(page, 18);
        QrView qr = new QrView(a, shown.optJSONArray("qr"));
        if (!qr.empty()) {
            FrameLayout holder = new FrameLayout(a);
            holder.addView(qr, new FrameLayout.LayoutParams(dp(260), dp(260), Gravity.CENTER));
            page.addView(holder, Ui.fill(a, 260)); Ui.gap(page, 16);
        }
        TextView code = Ui.text(a, shown.optString("code"), 34, Ui.INK); Ui.mono(code, true); code.setLetterSpacing(0.14f);
        code.setGravity(Gravity.CENTER); code.setTextIsSelectable(true); page.addView(code, Ui.fill(a, -1));
        Ui.gap(page, 6);
        TextView until = Ui.text(a, "Works once, until " + when(shown.optDouble("expires")) + ".", 14, Ui.MUTED);
        until.setGravity(Gravity.CENTER); page.addView(until, Ui.fill(a, -1));
        Ui.gap(page, 22);
        String text = (reset ? "Your Koenekt reset code: " + shown.optString("code")
                + "\nOpen Koenekt, tap Join with a code, type it and choose a new password."
                : "Join My stores in the Koenekt app: " + shown.optString("url")
                + "\nOr open Koenekt, tap Join with a code and type " + shown.optString("code") + ".");
        page.addView(Ui.primary(a, "Share", () -> a.shareText(shownTitle, text)), Ui.fill(a, 54));
        Ui.gap(page, 8);
        page.addView(Ui.secondary(a, "Done", done), Ui.fill(a, 52));
        Ui.gap(page, 12);
        page.addView(Ui.text(a, "The code is shown only now. Anyone with it can use it once, so share it only with this person.", 13, Ui.MUTED));
    }

    private static String when(double epochSeconds) {
        if (Double.isNaN(epochSeconds) || epochSeconds <= 0) return "?";
        return new SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(new Date((long) (epochSeconds * 1000)));
    }
    private static String ago(double epochSeconds) {
        long s = Math.max(0, System.currentTimeMillis() / 1000 - (long) epochSeconds);
        if (s < 120) return "just now";
        if (s < 7200) return (s / 60) + " min ago";
        if (s < 172800) return (s / 3600) + " h ago";
        return (s / 86400) + " days ago";
    }
}
