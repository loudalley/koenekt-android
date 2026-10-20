package za.co.koenekt.app;

import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * Change your own People password with POST /api/me/password. After an administrator's reset
 * ("forced") nothing else opens until it is done: the server refuses every other call too.
 * Typed passwords live only in the fields, never in fields of this object, so a redraw or a
 * lock clears them.
 */
final class PasswordFlow implements MainActivity.Flow {
    private static final String SAVING = "Saving…";
    private final MainActivity a;
    private final boolean forced;
    private String error = "";
    private boolean sending;

    PasswordFlow(MainActivity activity, boolean forced) { a = activity; this.forced = forced; }
    private int dp(float n) { return a.dp(n); }

    @Override public void show() {
        a.setBar(forced ? "New password" : "Change password", !forced);
        a.onBack(() -> {
            if (sending) return;
            if (forced) a.confirmSignOut(); else a.leaveFlow(MainActivity.TAB_ACCOUNT);
        });
        LinearLayout screen = Ui.column(a);
        ScrollView scroll = new ScrollView(a); scroll.setFillViewport(true);
        LinearLayout body = Ui.column(a); body.setPadding(dp(20), dp(8), dp(20), dp(20)); scroll.addView(body);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        body.addView(Ui.title(a, forced ? "Choose your own password" : "Change your password", 24)); Ui.gap(body, 6);
        body.addView(Ui.text(a, forced
                ? "Your administrator gave you a temporary password. Choose your own to continue."
                : "You'll stay signed in here. Other phones and browsers will need the new password.", 15, Ui.MUTED));
        Ui.gap(body, 22);
        int secret = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
        EditText current = Ui.field(body, forced ? "Temporary password" : "Current password", null, secret);
        EditText fresh = Ui.field(body, "New password", "At least 6 characters", secret);
        EditText repeat = Ui.field(body, "New password again", null, secret);
        if (!error.isEmpty()) body.addView(Ui.notice(a, error, true));

        LinearLayout footer = Ui.column(a); footer.setPadding(dp(20), dp(10), dp(20), dp(14)); footer.setBackgroundColor(Ui.BG);
        TextView save = Ui.primary(a, "Save new password", () -> submit(current, fresh, repeat));
        footer.addView(save, Ui.fill(a, 54));
        if (forced) footer.addView(Ui.quiet(a, "Sign out", a::confirmSignOut), Ui.fill(a, 48));
        screen.addView(footer);
        a.mount(screen);
        if (sending) { Ui.enable(save, false); save.setText(SAVING); }
        current.requestFocus();
    }

    private void submit(EditText current, EditText fresh, EditText repeat) {
        if (sending) return;
        String now = current.getText().toString(), next = fresh.getText().toString();
        if (now.isEmpty()) { fail(forced ? "Enter the temporary password." : "Enter your current password."); return; }
        if (next.length() < 6) { fail("The new password needs at least 6 characters."); return; }
        if (!next.equals(repeat.getText().toString())) { fail("The two new passwords are different."); return; }
        try {
            JSONObject body = new JSONObject().put("current_password", now).put("new_password", next);
            sending = true; error = ""; show();
            a.request("/api/me/password", body, data -> { sending = false; a.passwordChanged(); },
                    message -> { sending = false; fail(message); });
        } catch (Exception e) { sending = false; fail("Could not change the password."); }
    }
    private void fail(String message) { error = message; show(); }

    /** Account → Delete my account: confirm with the password, then POST /api/me/delete. */
    static void confirmDelete(MainActivity a) {
        LinearLayout view = Ui.column(a); view.setPadding(a.dp(24), a.dp(20), a.dp(24), a.dp(4));
        view.addView(Ui.title(a, "Delete your account?", 20)); Ui.gap(view, 12);
        view.addView(Ui.text(a, "You won't be able to sign in again, and your name, access and password are erased. "
                + "The activity log keeps a record of what this account did, and staff and attendance records stay with "
                + "your organisation.", 15, Ui.INK));
        Ui.gap(view, 16);
        EditText password = Ui.field(view, "Your password", null, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        AlertDialog dialog = new AlertDialog.Builder(a).setView(view).setNegativeButton("Cancel", null)
                .setPositiveButton("Delete account", null).show();
        TextView delete = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        delete.setTextColor(Ui.DANGER);
        delete.setOnClickListener(v -> {
            String typed = password.getText().toString();
            if (typed.isEmpty()) { password.setError("Enter your password"); return; }
            try {
                delete.setEnabled(false);
                a.request("/api/me/delete", new JSONObject().put("password", typed), data -> {
                    dialog.dismiss(); a.accountDeleted();
                }, message -> { delete.setEnabled(true); password.setText(""); password.setError(message); });
            } catch (Exception e) { delete.setEnabled(true); }
        });
    }
}
