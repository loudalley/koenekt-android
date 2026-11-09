package za.co.koenekt.app;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Join My stores with an invite code: type it (or arrive from its QR link), see what it is
 * for, then choose your own username and password (POST /api/join on the Link relay). A reset
 * code from an owner or Koenekt only asks for a new password. Passwords live only in the
 * fields, never in this object, so a redraw or a lock clears them.
 */
final class JoinFlow implements MainActivity.Flow {
    private static final String USERNAME = "[A-Za-z0-9._@-]{3,64}";
    private final MainActivity a;
    private String code, typed, username = "", error = "";
    private JSONObject info;          // what the code is for, from /api/join/check
    private boolean sending, autoChecked;

    JoinFlow(MainActivity activity, String code) {
        a = activity; this.code = code == null ? "" : code;
        typed = this.code.isEmpty() ? "" : AppPolicy.prettyCode(this.code);
    }
    private int dp(float n) { return a.dp(n); }
    private boolean reset() { return info != null && !info.isNull("reset") && !info.optString("reset").isEmpty(); }

    @Override public void show() {
        if (info == null && !code.isEmpty() && !autoChecked && !sending) { autoChecked = true; check(); }
        if (info == null) codeStep(); else detailsStep();
    }

    private LinearLayout[] frame(String bar) {
        a.setBar(bar, true);
        a.onBack(() -> {
            if (sending) return;
            if (info != null) { info = null; error = ""; show(); } else a.leaveJoin();
        });
        LinearLayout screen = Ui.column(a);
        ScrollView scroll = new ScrollView(a); scroll.setFillViewport(true);
        LinearLayout body = Ui.column(a); body.setPadding(dp(20), dp(8), dp(20), dp(20)); scroll.addView(body);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout footer = Ui.column(a); footer.setPadding(dp(20), dp(10), dp(20), dp(14)); footer.setBackgroundColor(Ui.BG);
        screen.addView(footer);
        return new LinearLayout[]{screen, body, footer};
    }

    private void codeStep() {
        LinearLayout[] f = frame("Join My stores");
        LinearLayout body = f[1];
        body.addView(Ui.title(a, "Type your invite code", 24)); Ui.gap(body, 6);
        body.addView(Ui.text(a, "The code you were given looks like K7QM-4XRT. Or scan its QR code with your phone's camera.", 15, Ui.MUTED));
        Ui.gap(body, 22);
        EditText field = Ui.field(body, "Invite code", "XXXX-XXXX",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setTextSize(24); Ui.mono(field, true); field.setLetterSpacing(0.12f);
        field.setText(typed); field.setSelection(field.getText().length());
        field.addTextChangedListener(new TextWatcher() {
            boolean busy;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (busy) return;
                // Only the code's own letters, upper case, with the dash after four.
                StringBuilder clean = new StringBuilder();
                for (char ch : s.toString().toUpperCase(Locale.ROOT).toCharArray())
                    if (AppPolicy.CODE_ALPHABET.indexOf(ch) >= 0 && clean.length() < 8) clean.append(ch);
                String shown = clean.length() > 4 ? clean.substring(0, 4) + "-" + clean.substring(4) : clean.toString();
                typed = shown;
                if (!shown.equals(s.toString())) { busy = true; s.replace(0, s.length(), shown); busy = false; }
            }
        });
        if (!error.isEmpty()) body.addView(Ui.notice(a, error, true));
        TextView go = Ui.primary(a, sending ? "Checking…" : "Check code", () -> {
            String c = AppPolicy.inviteCode(field.getText().toString());
            if (c == null) { fail("Type the 8 letters and numbers of your code."); return; }
            code = c; check();
        });
        f[2].addView(go, Ui.fill(a, 54));
        if (sending) Ui.enable(go, false);
        a.mount(f[0]);
        if (!sending) field.requestFocus();
    }

    private void check() {
        if (sending) return;
        sending = true; error = "";
        try {
            a.linkPublic("POST", "/api/join/check", new JSONObject().put("code", code),
                    data -> { sending = false; info = data; error = ""; show(); },
                    message -> { sending = false; fail(message); });
        } catch (Exception e) { sending = false; fail("Could not check the code."); }
        show();
    }

    private void detailsStep() {
        boolean reset = reset();
        LinearLayout[] f = frame(reset ? "New password" : "Join My stores");
        LinearLayout body = f[1];
        body.addView(Ui.title(a, reset ? "Choose a new password" : "Create your login", 24)); Ui.gap(body, 14);

        LinearLayout card = Ui.card(a);
        if (reset) {
            card.addView(Ui.label(a, "Login")); Ui.gap(card, 4);
            card.addView(Ui.title(a, info.optString("reset"), 17));
        } else {
            boolean owner = "owner".equals(info.optString("role"));
            card.addView(Ui.label(a, owner ? "Join as owner" : "Join as staff")); Ui.gap(card, 4);
            card.addView(Ui.text(a, owner ? "Live sales and gross profit" : "Live sales (no gross profit)", 14, Ui.MUTED));
            Ui.gap(card, 10);
            JSONArray stores = info.optJSONArray("stores");
            List<String> names = new ArrayList<>();
            if (stores != null) for (int i = 0; i < stores.length(); i++) names.add(stores.optString(i));
            card.addView(Ui.title(a, names.isEmpty() ? "No stores connected yet" : String.join(", ", names), 17));
            if (names.size() > 1) card.addView(Ui.text(a, names.size() + " stores", 13.5f, Ui.MUTED));
        }
        Ui.gap(card, 10);
        card.addView(Ui.text(a, "Code " + AppPolicy.prettyCode(code) + " · works until " + when(info.optDouble("expires")), 13.5f, Ui.MUTED));
        body.addView(card); Ui.gap(body, 20);
        if (!error.isEmpty()) { body.addView(Ui.notice(a, error, true)); Ui.gap(body, 16); }

        if (!reset && !a.linkUser().isEmpty()) {
            body.addView(Ui.notice(a, "This phone's My stores login (" + a.linkUser() + ") will be replaced by the new one.", false));
            Ui.gap(body, 16);
        }
        int secret = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
        EditText name = null;
        if (!reset) {
            name = Ui.field(body, "Username", "e.g. thandi.n",
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            name.setText(username);
            EditText n = name;
            name.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) { username = n.getText().toString().trim(); }
            });
        }
        EditText pw = Ui.field(body, reset ? "New password" : "Password", "At least 10 characters", secret);
        EditText again = Ui.field(body, "Password again", null, secret);
        body.addView(Ui.text(a, (reset ? "" : "Your username: 3 to 64 letters, numbers or . _ @ - (no spaces).\n")
                + "Your password: at least 10 characters, not your username. It is stored only as a hash on Koenekt's server, never on this phone.", 13.5f, Ui.MUTED));

        EditText nameField = name;
        TextView go = Ui.primary(a, sending ? "Saving…" : reset ? "Save new password" : "Create login",
                () -> submit(nameField, pw, again));
        f[2].addView(go, Ui.fill(a, 54));
        f[2].addView(Ui.quiet(a, "Use a different code", () -> { if (!sending) { info = null; code = ""; typed = ""; error = ""; show(); } }), Ui.fill(a, 48));
        if (sending) Ui.enable(go, false);
        a.mount(f[0]);
        if (!sending) (name != null && username.isEmpty() ? name : pw).requestFocus();
    }

    private void submit(EditText name, EditText pw, EditText again) {
        if (sending) return;
        String user = name == null ? info.optString("reset") : name.getText().toString().trim();
        String p = pw.getText().toString();
        if (name != null && !user.matches(USERNAME)) { fail("Choose a username of 3 to 64 letters, numbers or . _ @ - (no spaces)."); return; }
        if (p.length() < 10) { fail("Your password needs at least 10 characters."); return; }
        if (!p.equals(again.getText().toString())) { fail("The two passwords are different."); return; }
        if (p.toLowerCase(Locale.ROOT).startsWith(user.toLowerCase(Locale.ROOT))) { fail("Do not use your username in your password."); return; }
        try {
            JSONObject body = new JSONObject().put("code", code).put("username", user).put("password", p);
            sending = true; error = ""; username = user; show();
            a.linkPublic("POST", "/api/join", body,
                    data -> { sending = false; a.joined(data.optString("token"), data.optString("username", user), data.optString("role", "staff"), reset()); },
                    message -> {
                        sending = false;
                        if (message.startsWith("This code is not valid")) { info = null; code = ""; }
                        fail(message);
                    });
        } catch (Exception e) { sending = false; fail("Could not create the login."); }
    }

    private void fail(String message) { error = message; show(); }

    private static String when(double epochSeconds) {
        if (Double.isNaN(epochSeconds) || epochSeconds <= 0) return "?";
        return new SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(new Date((long) (epochSeconds * 1000)));
    }
}
