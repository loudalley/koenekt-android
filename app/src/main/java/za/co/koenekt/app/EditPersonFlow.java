package za.co.koenekt.app;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Short native edit form: name, department, job title and start date. Saves with the same
 * /api/employees update as the People website (its "employee" key marks an update). The clock
 * PIN is the person's permanent identity on the clocks, so it is shown but never editable.
 * Answers are kept as they are typed, so they survive locking and unlocking the app.
 */
final class EditPersonFlow implements MainActivity.Flow {
    private static final String SAVING = "Saving…";
    private final MainActivity a;
    private final JSONObject person;
    private final MainActivity.Company place;
    private final boolean active;
    private final String original;
    private String first, last, department, jobTitle, hireDate, error = "";
    private List<String> departments;
    private boolean sending;

    EditPersonFlow(MainActivity activity, JSONObject person, MainActivity.Company place) {
        a = activity; this.person = person; this.place = place;
        active = "Active".equalsIgnoreCase(person.optString("status", "Active"));
        first = person.optString("first_name").trim(); last = person.optString("last_name").trim();
        if (first.isEmpty() && last.isEmpty()) {   // Same split as the website when only a display name exists.
            String[] parts = PeopleScreen.name(person).split("\\s+", 2);
            first = parts[0]; last = parts.length > 1 ? parts[1] : "";
        }
        department = person.optString("department").trim(); jobTitle = person.optString("job_title").trim();
        hireDate = person.optString("hire_date").trim();
        if (hireDate.length() > 10) hireDate = hireDate.substring(0, 10);
        original = answers();
    }
    private int dp(float n) { return a.dp(n); }

    private String answers() { return String.join("\n", first, last, department, jobTitle, hireDate); }

    private void back() {
        if (sending) return;
        if (answers().equals(original)) { a.finishFlow(false); return; }
        new AlertDialog.Builder(a).setTitle("Discard your changes?")
                .setPositiveButton("Discard", (d, w) -> a.finishFlow(false)).setNegativeButton("Keep editing", null).show();
    }

    @Override public void show() {
        a.setBar("Edit details", true); a.onBack(this::back);
        LinearLayout screen = Ui.column(a);
        ScrollView scroll = new ScrollView(a); scroll.setFillViewport(true);
        LinearLayout body = Ui.column(a); body.setPadding(dp(20), dp(4), dp(20), dp(20)); scroll.addView(body);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout who = Ui.row(a); who.addView(Ui.avatar(a, PeopleScreen.name(person), 48));
        LinearLayout words = Ui.column(a); words.setPadding(dp(14), 0, 0, 0);
        words.addView(Ui.title(a, PeopleScreen.name(person), 18));
        words.addView(Ui.text(a, PeopleScreen.join(" · ", "Clock PIN " + person.optString("pin"), place.name), 14, Ui.MUTED));
        who.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(who); Ui.gap(body, 8);
        body.addView(Ui.text(a, "The clock PIN can't be changed: each clock stores this person's fingerprint or face under it.", 13.5f, Ui.MUTED));
        Ui.gap(body, 22);

        EditText firstField = Ui.field(body, "First name", "e.g. Thandi", Ui.textInput()); firstField.setText(first);
        EditText lastField = Ui.field(body, "Last name", "e.g. Mokoena", Ui.textInput()); lastField.setText(last);
        body.addView(Ui.label(a, "Department")); Ui.gap(body, 6);
        TextView dept = Ui.chip(a, department.isEmpty() ? "No department" : department, this::chooseDepartment);
        dept.setMaxWidth(Integer.MAX_VALUE); dept.setMinHeight(dp(54)); dept.setTextSize(16);
        body.addView(dept, Ui.fill(a, -1)); Ui.gap(body, 16);
        EditText jobField = Ui.field(body, "Job title (optional)", "e.g. Baker", Ui.textInput()); jobField.setText(jobTitle);
        LinearLayout card = Ui.column(a); card.setBackground(Ui.outline(Ui.SURFACE, Ui.LINE, 1, 18, a)); card.setClipToOutline(true);
        card.addView(Ui.listRow(a, null, "Start date", hireDate.isEmpty() ? "Not set" : AddEmployeeFlow.pretty(hireDate), null, this::chooseDate));
        body.addView(card);
        if (!error.isEmpty()) { Ui.gap(body, 16); body.addView(Ui.notice(a, error, true)); }
        Ui.gap(body, 14);
        body.addView(Ui.text(a, active ? "Pay, tax and ID details stay in People records."
                : "They have left, so saving won't add them back to the clocks.", 13.5f, Ui.MUTED));

        LinearLayout footer = Ui.column(a); footer.setPadding(dp(20), dp(10), dp(20), dp(14)); footer.setBackgroundColor(Ui.BG);
        screen.addView(footer);
        // A leaver is not on the clocks any more; pushing their name would enrol them again.
        TextView save = Ui.primary(a, active ? "Save and update clocks" : "Save", () -> submit(active));
        footer.addView(save, Ui.fill(a, 54));
        TextView quiet = null;
        if (active) { quiet = Ui.quiet(a, "Save without updating clocks", () -> submit(false)); footer.addView(quiet, Ui.fill(a, 48)); }
        a.mount(screen);

        TextView[] buttons = {save, quiet};
        Runnable check = () -> { for (TextView b : buttons) if (b != null) Ui.enable(b, !sending && !first.isEmpty()); };
        watch(firstField, s -> { first = s; check.run(); });
        watch(lastField, s -> last = s);
        watch(jobField, s -> jobTitle = s);
        check.run();
        if (sending) save.setText(SAVING);
    }

    private interface Text { void set(String value); }
    private static void watch(EditText field, Text into) {
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { into.set(s.toString().trim()); }
        });
    }

    private void chooseDepartment() {
        if (sending) return;
        if (departments == null) {
            a.request("/api/frappe/departments?url=" + MainActivity.query(place.url) + "&company=" + MainActivity.query(place.name), null, data -> {
                List<String> names = new ArrayList<>();
                JSONArray rows = data.optJSONArray("departments");
                if (rows != null) for (int i = 0; i < rows.length(); i++) {
                    String n = rows.optJSONObject(i) == null ? "" : rows.optJSONObject(i).optString("name");
                    if (!n.isEmpty()) names.add(n);
                }
                // Keep the current department selectable even if the list no longer has it.
                if (!department.isEmpty() && !names.contains(department)) names.add(0, department);
                departments = names; chooseDepartment();
            }, message -> { error = message; show(); });
            return;
        }
        String[] options = new String[departments.size() + 1];
        options[0] = "No department";
        for (int i = 0; i < departments.size(); i++) options[i + 1] = departments.get(i);
        Ui.choose(a, "Department", options, department.isEmpty() ? 0 : departments.indexOf(department) + 1, which -> {
            department = which == 0 ? "" : departments.get(which - 1); show();
        });
    }

    private void chooseDate() {
        if (sending) return;
        LocalDate d;
        try { d = LocalDate.parse(hireDate); } catch (Exception e) { d = LocalDate.now(); }
        new DatePickerDialog(a, (picker, y, m, day) -> { hireDate = LocalDate.of(y, m + 1, day).toString(); show(); },
                d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()).show();
    }

    private void submit(boolean sendToClocks) {
        if (sending || first.isEmpty()) return;
        try {
            // Same shape as the website's update. ID number and birth date are left out so the server keeps
            // them; the clock privilege and card are sent back unchanged so a name update doesn't reset them.
            JSONObject body = new JSONObject().put("frappe_url", place.url).put("company", place.name)
                    .put("employee", person.optString("name")).put("pin", person.optString("pin"))
                    .put("first_name", first).put("last_name", last).put("department", department).put("job_title", jobTitle)
                    .put("hire_date", hireDate).put("status", person.optString("status", "Active"))
                    .put("privilege", person.optInt("privilege", 0)).put("card_no", person.optString("card_no"))
                    .put("push_device", sendToClocks).put("push_all_company", true);
            sending = true; error = ""; show();
            a.request("/api/employees", body, data -> {
                sending = false;
                JSONArray queued = data.optJSONArray("devices");
                int clocks = queued == null ? 0 : queued.length();
                a.toast((first + " " + last).trim() + " is saved." + (clocks > 0 ? " Sent to " + clocks + (clocks == 1 ? " clock." : " clocks.") : ""));
                a.finishFlow(true);
            }, message -> { sending = false; error = message; show(); });
        } catch (Exception e) { sending = false; error = e.getMessage() == null ? "Could not save these details." : e.getMessage(); show(); }
    }
}
