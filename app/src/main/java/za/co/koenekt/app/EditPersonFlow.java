package za.co.koenekt.app;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Short native edit form: name, department, job title, start date, shift, ID / passport and birth
 * date. Saves with the same calls as the People website: the /api/employees update (its "employee"
 * key marks an update; it also stores ID number and birth date), then PUT /api/shifts/assign when
 * the shift changed. The clock PIN is the person's permanent identity on the clocks, so it is shown
 * but never editable. Answers are kept as they are typed, so they survive locking and unlocking.
 */
final class EditPersonFlow implements MainActivity.Flow {
    private static final String SAVING = "Saving…";
    private final MainActivity a;
    private final JSONObject person;
    private final MainActivity.Company place;
    private final JSONArray shifts;
    private final boolean active;
    private final String original;
    private String first, last, department, jobTitle, hireDate, idNumber, birthDate, shiftId, error = "";
    private List<String> departments;
    private boolean sending;
    private int scrollY;   // Kept so a picker or error redraw doesn't jump back to the top

    EditPersonFlow(MainActivity activity, JSONObject person, MainActivity.Company place, JSONArray shifts) {
        a = activity; this.person = person; this.place = place; this.shifts = shifts;
        active = "Active".equalsIgnoreCase(person.optString("status", "Active"));
        first = person.optString("first_name").trim(); last = person.optString("last_name").trim();
        if (first.isEmpty() && last.isEmpty()) {   // Same split as the website when only a display name exists.
            String[] parts = PeopleScreen.name(person).split("\\s+", 2);
            first = parts[0]; last = parts.length > 1 ? parts[1] : "";
        }
        department = person.optString("department").trim(); jobTitle = person.optString("job_title").trim();
        hireDate = day(person.optString("hire_date")); birthDate = day(person.optString("birth_date"));
        idNumber = person.optString("id_number").trim(); shiftId = person.optString("shift_id").trim();
        original = answers();
    }
    private static String day(String value) { value = value.trim(); return value.length() > 10 ? value.substring(0, 10) : value; }
    private int dp(float n) { return a.dp(n); }

    private String answers() { return String.join("\n", first, last, department, jobTitle, hireDate, idNumber, birthDate, shiftId); }

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
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> scrollY = y);
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
        if (shifts.length() > 0) {
            card.addView(Ui.divider(a, 16));
            card.addView(Ui.listRow(a, null, "Shift", shiftLabel(), null, this::chooseShift));
        }
        if (active && person.has("has_clock_password")) {   // only servers that keep clock passwords send this
            card.addView(Ui.divider(a, 16));
            card.addView(Ui.listRow(a, null, "Clock password",
                    person.optBoolean("has_clock_password") ? "Set · tap to change or remove" : "Not set", null, this::clockPassword));
        }
        body.addView(card); Ui.gap(body, 22);
        EditText idField = Ui.field(body, "ID or passport number (optional)", "SA ID or passport",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        idField.setText(idNumber);
        LinearLayout born = Ui.column(a); born.setBackground(Ui.outline(Ui.SURFACE, Ui.LINE, 1, 18, a)); born.setClipToOutline(true);
        born.addView(Ui.listRow(a, null, "Birth date", birthDate.isEmpty() ? "Not set" : AddEmployeeFlow.pretty(birthDate), null, this::chooseBirthDate));
        body.addView(born);
        if (!error.isEmpty()) { Ui.gap(body, 16); body.addView(Ui.notice(a, error, true)); }
        Ui.gap(body, 14);
        body.addView(Ui.text(a, active ? "Pay and tax details stay in People records."
                : "They have left, so saving won't add them back to the clocks.", 13.5f, Ui.MUTED));

        LinearLayout footer = Ui.column(a); footer.setPadding(dp(20), dp(10), dp(20), dp(14)); footer.setBackgroundColor(Ui.BG);
        screen.addView(footer);
        // A leaver is not on the clocks any more; pushing their name would enrol them again.
        TextView save = Ui.primary(a, active ? "Save and update clocks" : "Save", () -> submit(active));
        footer.addView(save, Ui.fill(a, 54));
        TextView quiet = null;
        if (active) { quiet = Ui.quiet(a, "Save without updating clocks", () -> submit(false)); footer.addView(quiet, Ui.fill(a, 48)); }
        a.mount(screen); scroll.post(() -> scroll.scrollTo(0, error.isEmpty() ? scrollY : body.getBottom()));   // Show any error

        TextView[] buttons = {save, quiet};
        Runnable check = () -> { for (TextView b : buttons) if (b != null) Ui.enable(b, !sending && !first.isEmpty()); };
        watch(firstField, s -> { first = s; check.run(); });
        watch(lastField, s -> last = s);
        watch(jobField, s -> jobTitle = s);
        watch(idField, s -> idNumber = s);
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

    /** The person's own shift, or "Automatic" with the shift the rules pick for today. */
    private String shiftLabel() {
        if (!shiftId.isEmpty()) return shiftName(shiftId);
        String today = person.optString("shift_name").trim();
        return "Automatic" + (today.isEmpty() || !person.optString("shift_id").trim().isEmpty() ? "" : " · today " + today);
    }
    private String shiftName(String id) {
        for (int i = 0; i < shifts.length(); i++) {
            JSONObject t = shifts.optJSONObject(i);
            if (t != null && id.equals(t.optString("id"))) return t.optString("name").isEmpty() ? id : t.optString("name");
        }
        return id;
    }
    private void chooseShift() {
        if (sending) return;
        List<String> ids = new ArrayList<>(), names = new ArrayList<>();
        ids.add(""); names.add("Automatic (from the shift rules)");
        for (int i = 0; i < shifts.length(); i++) {
            JSONObject t = shifts.optJSONObject(i);
            if (t == null || t.optString("id").isEmpty()) continue;
            ids.add(t.optString("id")); names.add(shiftName(t.optString("id")));
        }
        Ui.choose(a, "Shift", names.toArray(new String[0]), Math.max(0, ids.indexOf(shiftId)), which -> { shiftId = ids.get(which); show(); });
    }

    /** Sets the password typed on the clock right away (not with Save), and sends it to the company's clocks. */
    private void clockPassword() {
        if (sending) return;
        boolean set = person.optBoolean("has_clock_password");
        LinearLayout view = Ui.column(a); view.setPadding(dp(24), dp(20), dp(24), dp(4));
        view.addView(Ui.title(a, "Clock password", 20)); Ui.gap(view, 8);
        view.addView(Ui.text(a, "Up to 8 digits that " + first + " can type on the clock instead of a fingerprint or face. "
                + "It is sent to the clocks at " + place.name + " straight away.", 15, Ui.MUTED));
        Ui.gap(view, 16);
        EditText field = Ui.field(view, set ? "New clock password" : "Clock password", "Digits only",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(8)});
        AlertDialog.Builder builder = new AlertDialog.Builder(a).setView(view).setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null);
        if (set) builder.setNeutralButton("Remove", (d, w) -> sendClockPassword(""));
        AlertDialog dialog = builder.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String digits = field.getText().toString().trim();
            if (digits.isEmpty()) { field.setError("Enter up to 8 digits"); return; }
            dialog.dismiss(); sendClockPassword(digits);
        });
    }
    private void sendClockPassword(String digits) {
        try {
            JSONObject body = new JSONObject().put("frappe_url", place.url).put("company", place.name)
                    .put("employee", person.optString("name")).put("pin", person.optString("pin")).put("password", digits);
            sending = true; error = ""; show();
            a.request("/api/employees/clock-password", body, data -> {
                sending = false;
                JSONArray queued = data.optJSONArray("devices");
                int clocks = queued == null ? 0 : queued.length();
                try { person.put("has_clock_password", !digits.isEmpty()); } catch (Exception ignored) { }
                a.toast("Clock password " + (digits.isEmpty() ? "removed" : "saved") + ". Sent to " + clocks + (clocks == 1 ? " clock." : " clocks."));
                show();
            }, message -> { sending = false; error = message; show(); });
        } catch (Exception e) { sending = false; error = "Could not change the clock password."; show(); }
    }

    private void chooseBirthDate() {
        if (sending) return;
        LocalDate d;
        try { d = LocalDate.parse(birthDate); } catch (Exception e) { d = LocalDate.now().minusYears(30); }
        DatePickerDialog picker = new DatePickerDialog(a, (view, y, m, dayOfMonth) -> { birthDate = LocalDate.of(y, m + 1, dayOfMonth).toString(); show(); },
                d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth());
        // The server accepts 1920 up to today.
        picker.getDatePicker().setMinDate(LocalDate.of(1920, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
        picker.getDatePicker().setMaxDate(System.currentTimeMillis());
        if (!birthDate.isEmpty()) picker.setButton(DatePickerDialog.BUTTON_NEUTRAL, "Clear", (dialog, w) -> { birthDate = ""; show(); });
        picker.show();
    }

    private void submit(boolean sendToClocks) {
        if (sending || first.isEmpty()) return;
        try {
            // Same shape as the website's update. The clock privilege and card are sent back unchanged so a name
            // update doesn't reset them. ID number and birth date are sent only when the server listed them.
            JSONObject body = new JSONObject().put("frappe_url", place.url).put("company", place.name)
                    .put("employee", person.optString("name")).put("pin", person.optString("pin"))
                    .put("first_name", first).put("last_name", last).put("department", department).put("job_title", jobTitle)
                    .put("hire_date", hireDate).put("status", person.optString("status", "Active"))
                    .put("privilege", person.optInt("privilege", 0)).put("card_no", person.optString("card_no"))
                    .put("push_device", sendToClocks).put("push_all_company", true);
            if (person.has("id_number") || !idNumber.isEmpty()) body.put("id_number", idNumber);
            if (person.has("birth_date") || !birthDate.isEmpty()) body.put("birth_date", birthDate);
            sending = true; error = ""; show();
            a.request("/api/employees", body, data -> {
                JSONArray queued = data.optJSONArray("devices");
                int clocks = queued == null ? 0 : queued.length();
                String key = data.optString("employee", person.optString("name"));
                Runnable saved = () -> {
                    sending = false;
                    a.toast((first + " " + last).trim() + " is saved." + (clocks > 0 ? " Sent to " + clocks + (clocks == 1 ? " clock." : " clocks.") : ""));
                    a.finishFlow(true);
                };
                Runnable shift = () -> {
                    if (shiftId.equals(person.optString("shift_id").trim())) { saved.run(); return; }
                    then("PUT", "/api/shifts/assign", key, "shift_id", shiftId, "the shift didn't change", saved);
                };
                // /api/employees ignores an empty birth date, so clearing one takes the website's extras call.
                if (birthDate.isEmpty() && !day(person.optString("birth_date")).isEmpty())
                    then("PUT", "/api/people/extras", key, "birth_date", "", "the birth date wasn't cleared", shift);
                else shift.run();
            }, message -> { sending = false; error = message; show(); });
        } catch (Exception e) { sending = false; error = e.getMessage() == null ? "Could not save these details." : e.getMessage(); show(); }
    }
    /** A follow-up call after the main save; on failure the form stays open and says what is missing. */
    private void then(String method, String path, String employee, String field, String value, String failure, Runnable next) {
        try {
            a.request(method, path, new JSONObject().put("employee", employee).put(field, value), done -> next.run(),
                    message -> { sending = false; error = "Details are saved, but " + failure + ": " + message; show(); });
        } catch (Exception e) { sending = false; error = "Details are saved, but " + failure + "."; show(); }
    }
}
