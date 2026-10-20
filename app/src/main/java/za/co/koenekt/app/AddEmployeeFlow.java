package za.co.koenekt.app;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Three short steps instead of the website's 25-field form: name, where they work, clock PIN.
 * Uses the same /api/employees call as the People website; pay, tax and clock-card details
 * stay in People records. Answers survive locking and unlocking the app.
 */
final class AddEmployeeFlow {
    private static final String ADDING = "Adding…";
    private final MainActivity a;
    private int step = 1;                       // 1 name, 2 workplace, 3 PIN and confirm, 4 done
    private String first = "", last = "", department = "", jobTitle = "", pin = "", pinFor = "", error = "";
    private String hireDate = LocalDate.now().toString();
    private List<MainActivity.Company> companies;
    private int company;
    private List<String> departments;
    private String departmentsFor = "";
    private boolean sending;
    private JSONObject result;

    AddEmployeeFlow(MainActivity activity) { a = activity; }
    private int dp(float n) { return a.dp(n); }
    private MainActivity.Company place() { return companies.get(company); }

    void show() {
        if (companies == null) {
            frame("Add employee", null); a.mount(Ui.loading(a));
            a.loadCompanies(list -> {
                if (list.isEmpty()) { a.finishAddEmployee(false); return; }
                companies = list; company = Math.min(a.companyIndex(list), list.size() - 1); show();
            }, message -> { error = message; a.finishAddEmployee(false); });
            return;
        }
        if (step == 1) name(); else if (step == 2) workplace(); else if (step == 3) confirm(); else done();
    }

    private void back() {
        if (sending) return;
        if (step == 4) { a.finishAddEmployee(true); return; }
        if (step > 1) { step--; error = ""; show(); return; }
        if (first.isEmpty() && last.isEmpty()) { a.finishAddEmployee(false); return; }
        new AlertDialog.Builder(a).setTitle("Discard this new employee?")
                .setPositiveButton("Discard", (d, w) -> a.finishAddEmployee(false)).setNegativeButton("Keep editing", null).show();
    }

    /** Screen skeleton: progress, heading, scrolling body and a footer that stays above the keyboard. */
    private LinearLayout[] frame(String heading, String helper) {
        a.setBar("Add employee", true); a.onBack(this::back);
        LinearLayout screen = Ui.column(a);
        ScrollView scroll = new ScrollView(a); scroll.setFillViewport(true);
        LinearLayout body = Ui.column(a); body.setPadding(dp(20), dp(4), dp(20), dp(20)); scroll.addView(body);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (step <= 3) {
            LinearLayout bars = Ui.row(a);
            for (int i = 1; i <= 3; i++) {
                View bar = new View(a); bar.setBackground(Ui.shape(i <= step ? Ui.BRAND : Ui.LINE, 3, a));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(5), 1); if (i > 1) p.leftMargin = dp(6);
                bars.addView(bar, p);
            }
            body.addView(bars); Ui.gap(body, 12); body.addView(Ui.label(a, "Step " + step + " of 3")); Ui.gap(body, 14);
        }
        body.addView(Ui.title(a, heading, 26));
        if (helper != null) { Ui.gap(body, 6); body.addView(Ui.text(a, helper, 15, Ui.MUTED)); }
        Ui.gap(body, 24);
        LinearLayout footer = Ui.column(a); footer.setPadding(dp(20), dp(10), dp(20), dp(14)); footer.setBackgroundColor(Ui.BG);
        screen.addView(footer);
        a.mount(screen);
        return new LinearLayout[]{body, footer};
    }

    private void name() {
        LinearLayout[] f = frame("Who are you adding?", "Their name as it should appear on reports and on the clock.");
        EditText firstField = Ui.field(f[0], "First name", "e.g. Thandi", Ui.textInput()); firstField.setText(first);
        EditText lastField = Ui.field(f[0], "Last name", "e.g. Mokoena", Ui.textInput()); lastField.setText(last);
        TextView next = Ui.primary(a, "Next", () -> {
            first = firstField.getText().toString().trim(); last = lastField.getText().toString().trim();
            if (!first.isEmpty()) { step = 2; show(); }
        });
        Ui.enable(next, !firstField.getText().toString().trim().isEmpty());
        firstField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { Ui.enable(next, !s.toString().trim().isEmpty()); }
        });
        f[1].addView(next, Ui.fill(a, 54));
        firstField.requestFocus();
        firstField.postDelayed(() -> ((InputMethodManager) a.getSystemService(MainActivity.INPUT_METHOD_SERVICE)).showSoftInput(firstField, 0), 150);
    }

    private void workplace() {
        LinearLayout[] f = frame("Where does " + first + " work?", null);
        LinearLayout body = f[0];
        if (companies.size() > 1) {
            body.addView(Ui.label(a, "Company")); Ui.gap(body, 8);
            LinearLayout list = Ui.column(a); list.setBackground(Ui.outline(Ui.SURFACE, Ui.LINE, 1, 18, a)); list.setClipToOutline(true);
            for (int i = 0; i < companies.size(); i++) {
                final int which = i;
                if (i > 0) list.addView(Ui.divider(a, 16));
                ImageView tick = Ui.icon(a, R.drawable.ic_check, Ui.BRAND, 22); tick.setVisibility(i == company ? View.VISIBLE : View.INVISIBLE);
                LinearLayout row = Ui.listRow(a, null, companies.get(i).name, null, null, null);
                row.addView(tick);
                row.setBackground(Ui.pressable(Ui.shape(i == company ? Ui.TINT : Ui.SURFACE, 0, a), 0, a));
                row.setClickable(true); row.setOnClickListener(v -> {
                    if (company != which) { company = which; department = ""; a.rememberCompany(place()); }
                    show();
                });
                list.addView(row);
            }
            body.addView(list); Ui.gap(body, 22);
        }
        body.addView(Ui.label(a, "Department")); Ui.gap(body, 8);
        TextView dept = Ui.chip(a, department.isEmpty() ? "Choose a department" : department, this::chooseDepartment);
        dept.setMaxWidth(Integer.MAX_VALUE); dept.setMinHeight(dp(54)); dept.setTextSize(16);
        body.addView(dept, Ui.fill(a, -1)); Ui.gap(body, 22);
        EditText job = Ui.field(body, "Job title (optional)", "e.g. Baker", Ui.textInput()); job.setText(jobTitle);
        f[1].addView(Ui.primary(a, "Next", () -> { jobTitle = job.getText().toString().trim(); step = 3; show(); }), Ui.fill(a, 54));
    }

    private void chooseDepartment() {
        String url = place().url;
        if (departments == null || !departmentsFor.equals(url)) {
            a.request("/api/frappe/departments?url=" + MainActivity.query(url) + "&company=" + MainActivity.query(place().name), null, data -> {
                List<String> names = new ArrayList<>();
                JSONArray rows = data.optJSONArray("departments");
                if (rows != null) for (int i = 0; i < rows.length(); i++) {
                    String n = rows.optJSONObject(i) == null ? "" : rows.optJSONObject(i).optString("name");
                    if (!n.isEmpty()) names.add(n);
                }
                departments = names; departmentsFor = url; chooseDepartment();
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

    private void confirm() {
        String url = place().url;
        if (!pinFor.equals(url)) {   // Suggest the next free PIN for this company, once.
            frame("Clock PIN", null); a.mount(Ui.loading(a));
            a.request("/api/people/next-pin?url=" + MainActivity.query(url) + "&company=" + MainActivity.query(place().name), null,
                    data -> { pin = data.optString("pin"); pinFor = url; show(); },
                    message -> { pinFor = url; show(); });
            return;
        }
        LinearLayout[] f = frame("Clock PIN", "The number " + first + " types on the clock. We've filled in the next free one; PINs can't be changed later.");
        LinearLayout body = f[0];
        EditText pinField = Ui.field(body, null, "PIN", InputType.TYPE_CLASS_NUMBER); pinField.setText(pin);
        pinField.setTextSize(26); pinField.setMinHeight(dp(64)); pinField.setLetterSpacing(0.1f);
        LinearLayout card = Ui.column(a); card.setBackground(Ui.outline(Ui.SURFACE, Ui.LINE, 1, 18, a)); card.setClipToOutline(true);
        card.addView(Ui.listRow(a, Ui.avatar(a, first + " " + last, 40), (first + " " + last).trim(),
                PeopleScreen.join(" · ", place().name, department, jobTitle), null, () -> { step = 2; show(); }));
        card.addView(Ui.divider(a, 16));
        card.addView(Ui.listRow(a, null, "Start date", pretty(hireDate), null, this::chooseDate));
        body.addView(card);
        if (!error.isEmpty()) { Ui.gap(body, 16); body.addView(Ui.notice(a, error, true)); }
        Ui.gap(body, 14);
        body.addView(Ui.text(a, "Pay, tax, ID number and clock card can be added later in People records.", 13.5f, Ui.MUTED));
        TextView add = Ui.primary(a, "Add and send to clocks", () -> submit(pinField, true));
        TextView save = Ui.quiet(a, "Save without sending to clocks", () -> submit(pinField, false));
        f[1].addView(add, Ui.fill(a, 54)); f[1].addView(save, Ui.fill(a, 48));
        if (sending) { Ui.enable(add, false); Ui.enable(save, false); add.setText(ADDING); }
    }

    private void chooseDate() {
        LocalDate d = LocalDate.parse(hireDate);
        new DatePickerDialog(a, (picker, y, m, day) -> { hireDate = LocalDate.of(y, m + 1, day).toString(); show(); },
                d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()).show();
    }

    private void submit(EditText pinField, boolean sendToClocks) {
        if (sending) return;
        pin = pinField.getText().toString().trim();
        if (pin.isEmpty()) { error = "Enter a clock PIN."; show(); return; }
        try {
            JSONObject body = new JSONObject().put("frappe_url", place().url).put("company", place().name).put("pin", pin)
                    .put("first_name", first).put("last_name", last).put("department", department).put("job_title", jobTitle)
                    .put("hire_date", hireDate).put("status", "Active").put("push_device", sendToClocks).put("push_all_company", true);
            sending = true; error = ""; show();
            a.request("/api/employees", body, data -> { sending = false; result = data; step = 4; show(); },
                    message -> { sending = false; error = message; show(); });
        } catch (Exception e) { error = e.getMessage() == null ? "Could not add this person." : e.getMessage(); show(); }
    }

    private void done() {
        String who = (first + " " + last).trim();
        LinearLayout[] f = frame(who + " is added", null);
        LinearLayout body = f[0];
        ImageView tick = Ui.icon(a, R.drawable.ic_check, Ui.BRAND, 64); tick.setPadding(dp(14), dp(14), dp(14), dp(14));
        android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
        circle.setShape(android.graphics.drawable.GradientDrawable.OVAL); circle.setColor(Ui.TINT); tick.setBackground(circle);
        body.addView(tick, 0);
        ((LinearLayout.LayoutParams) tick.getLayoutParams()).bottomMargin = dp(20);
        JSONArray queued = result == null ? null : result.optJSONArray("devices");
        int clocks = queued == null ? 0 : queued.length();
        String pinText = result == null || result.optString("pin").isEmpty() ? pin : result.optString("pin");
        body.addView(Ui.text(a, "Clock PIN " + pinText + " at " + place().name + ".", 16, Ui.INK)); Ui.gap(body, 6);
        body.addView(Ui.text(a, clocks > 0 ? "Sent to " + clocks + (clocks == 1 ? " clock." : " clocks.")
                : "Not sent to the clocks yet. Use People records to send when you're ready.", 15, Ui.MUTED));
        if (clocks > 0) { Ui.gap(body, 6); body.addView(Ui.text(a, "Enrol their fingerprint or face on the clock as usual.", 15, Ui.MUTED)); }
        f[1].addView(Ui.primary(a, "Done", () -> a.finishAddEmployee(true)), Ui.fill(a, 54));
        f[1].addView(Ui.quiet(a, "Add another person", () -> {
            first = ""; last = ""; jobTitle = ""; pin = ""; pinFor = ""; error = ""; result = null;
            hireDate = LocalDate.now().toString(); step = 1; show();
        }), Ui.fill(a, 48));
    }

    static String pretty(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) return "";
        try { return LocalDate.parse(isoDate).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())); }
        catch (Exception e) { return isoDate; }
    }
}
