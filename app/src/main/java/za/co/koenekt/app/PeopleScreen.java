package za.co.koenekt.app;

import android.app.AlertDialog;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Native People list: company and status filters, instant search, compact rows and a person summary. */
final class PeopleScreen {
    private static final String[] STATUS_VALUES = {"Active", "Inactive", "all"};
    private static final String[] STATUS_NAMES = {"Active", "Left", "Everyone"};
    private final MainActivity a;
    private final List<JSONObject> all = new ArrayList<>(), shown = new ArrayList<>();
    private List<MainActivity.Company> companies;
    private int company, status;
    private String query = "";
    private boolean loaded;
    private LinearLayout chips;
    private TextView summary;
    private FrameLayout body;
    private BaseAdapter adapter;

    PeopleScreen(MainActivity activity) { a = activity; }
    void invalidate() { loaded = false; }
    private int dp(float n) { return a.dp(n); }

    void show() {
        if (a.can("people_hire")) a.setBar("People", false, Ui.iconButton(a, R.drawable.ic_add, "Add employee", a::startAddEmployee));
        else a.setBar("People", false);
        LinearLayout screen = Ui.column(a);
        LinearLayout header = Ui.column(a); header.setPadding(dp(16), 0, dp(16), dp(4));
        LinearLayout search = Ui.row(a); search.setPadding(dp(14), 0, dp(6), 0);
        search.setBackground(Ui.shape(Color.rgb(235, 239, 233), 16, a));
        search.addView(Ui.icon(a, R.drawable.ic_search, Ui.MUTED, 20));
        EditText q = new EditText(a); q.setHint("Search name, PIN or department"); q.setBackground(null); q.setSingleLine(true);
        q.setTextSize(16); q.setTextColor(Ui.INK); q.setHintTextColor(Ui.MUTED); q.setPadding(dp(10), 0, 0, 0);
        q.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        q.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO); q.setText(query);
        q.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { query = s.toString(); filter(); }
        });
        search.addView(q, new LinearLayout.LayoutParams(0, dp(50), 1));
        header.addView(search); Ui.gap(header, 12);
        chips = Ui.row(a); header.addView(chips); Ui.gap(header, 12);
        summary = Ui.label(a, ""); header.addView(summary);
        screen.addView(header);
        body = new FrameLayout(a); screen.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        a.mount(screen);
        body.addView(Ui.loading(a));
        a.loadCompanies(list -> {
            companies = list; company = Math.min(a.companyIndex(list), Math.max(0, list.size() - 1));
            buildChips();
            if (list.isEmpty()) message("No companies are available to your account yet. Ask your administrator for company access.");
            else if (loaded) showList(); else load();
        }, this::message);
    }

    private void buildChips() {
        chips.removeAllViews();
        if (companies.size() > 1) {
            chips.addView(Ui.chip(a, companies.get(company).name, () -> {
                String[] names = new String[companies.size()];
                for (int i = 0; i < names.length; i++) names[i] = companies.get(i).name;
                Ui.choose(a, "Company", names, company, which -> {
                    company = which; a.rememberCompany(companies.get(which)); buildChips(); load();
                });
            }));
            View space = new View(a); chips.addView(space, new LinearLayout.LayoutParams(dp(8), 1));
        }
        chips.addView(Ui.chip(a, STATUS_NAMES[status], () -> Ui.choose(a, "Show", STATUS_NAMES, status, which -> {
            status = which; buildChips(); load();
        })));
    }

    private void load() {
        body.removeAllViews(); body.addView(Ui.loading(a)); summary.setText("");
        String path = "/api/people/directory?url=" + MainActivity.query(companies.get(company).url) + "&status=" + STATUS_VALUES[status];
        a.request(path, null, data -> {
            all.clear();
            JSONArray rows = data.optJSONArray("employees");
            if (rows != null) for (int i = 0; i < rows.length(); i++) if (rows.optJSONObject(i) != null) all.add(rows.optJSONObject(i));
            all.sort((x, y) -> name(x).compareToIgnoreCase(name(y)));
            loaded = true; showList();
        }, this::message);
    }

    private void filter() {
        shown.clear();
        String needle = query.trim().toLowerCase(Locale.ROOT);
        for (JSONObject p : all) {
            String hay = (name(p) + " " + p.optString("pin") + " " + p.optString("department") + " "
                    + p.optString("job_title") + " " + p.optString("id_number")).toLowerCase(Locale.ROOT);
            if (needle.isEmpty() || hay.contains(needle)) shown.add(p);
        }
        String what = STATUS_NAMES[status].equals("Everyone") ? "people" : STATUS_NAMES[status].toLowerCase(Locale.ROOT) + " people";
        if (summary != null) summary.setText(needle.isEmpty() ? all.size() + " " + what : shown.size() + " of " + all.size() + " match");
        if (adapter != null) adapter.notifyDataSetChanged();
        if (body != null && loaded && shown.isEmpty() && !all.isEmpty()) message("No one matches “" + query.trim() + "”.");
        else if (body != null && loaded && !shown.isEmpty() && body.getChildAt(0) instanceof TextView) showList();
    }

    private void showList() {
        body.removeAllViews();
        if (all.isEmpty()) {
            message("No " + STATUS_NAMES[status].toLowerCase(Locale.ROOT) + " people at " + companies.get(company).name + " yet."
                    + (a.can("people_hire") ? " Tap + to add someone." : ""));
            summary.setText(""); return;
        }
        ListView list = new ListView(a); list.setDivider(null); list.setClipToPadding(false); list.setPadding(0, dp(4), 0, dp(16));
        list.setSelector(android.R.color.transparent);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int i) { return shown.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View reuse, ViewGroup parent) {
                JSONObject p = shown.get(i);
                String sub = join(" · ", p.optString("department"), p.optString("pin").isEmpty() ? "" : "PIN " + p.optString("pin"));
                String state = "Active".equalsIgnoreCase(p.optString("status")) ? "" : "Left";
                return Ui.listRow(a, Ui.avatar(a, name(p), 40), name(p), sub, state, () -> details(p));
            }
        };
        list.setAdapter(adapter); body.addView(list); filter();
    }

    private void details(JSONObject p) {
        LinearLayout view = Ui.column(a); view.setPadding(dp(24), dp(24), dp(24), dp(8));
        LinearLayout top = Ui.row(a); top.addView(Ui.avatar(a, name(p), 56));
        LinearLayout words = Ui.column(a); words.setPadding(dp(16), 0, 0, 0);
        words.addView(Ui.title(a, name(p), 20));
        words.addView(Ui.text(a, join(" · ", p.optString("job_title"), p.optString("company")), 14, Ui.MUTED));
        top.addView(words, new LinearLayout.LayoutParams(0, -2, 1)); view.addView(top); Ui.gap(view, 18);
        fact(view, "Clock PIN", p.optString("pin"));
        fact(view, "Department", p.optString("department"));
        fact(view, "Started", AddEmployeeFlow.pretty(p.optString("hire_date")));
        fact(view, "Status", "Active".equalsIgnoreCase(p.optString("status")) ? "Active" : "Left" + (p.optString("left_on").isEmpty() ? "" : " on " + p.optString("left_on")));
        fact(view, "ID / passport", p.optString("id_number"));
        AlertDialog.Builder dialog = new AlertDialog.Builder(a).setView(view).setPositiveButton("Close", null);
        if (a.can("people_edit") || a.can("people_leave"))
            dialog.setNeutralButton("Edit in People records", (d, w) -> a.openWeb("People records", "/people"));
        dialog.show();
    }
    private void fact(LinearLayout parent, String label, String value) {
        if (value == null || value.isEmpty()) return;
        LinearLayout r = Ui.row(a); r.setPadding(0, dp(10), 0, dp(10));
        TextView l = Ui.text(a, label, 14, Ui.MUTED); r.addView(l, new LinearLayout.LayoutParams(0, -2, 1));
        TextView v = Ui.text(a, value, 15, Ui.INK); v.setGravity(Gravity.END); r.addView(v, new LinearLayout.LayoutParams(0, -2, 1.4f));
        parent.addView(r); parent.addView(Ui.divider(a, 0));
    }

    private void message(String text) {
        body.removeAllViews(); adapter = null;
        TextView t = Ui.text(a, text, 15, Ui.MUTED); t.setGravity(Gravity.CENTER); t.setPadding(dp(32), dp(48), dp(32), dp(48));
        body.addView(t, new FrameLayout.LayoutParams(-1, -2));
    }

    static String name(JSONObject p) {
        String n = p.optString("employee_name").trim();
        if (n.isEmpty()) n = (p.optString("first_name") + " " + p.optString("last_name")).trim();
        return n.isEmpty() ? p.optString("name", "Unnamed") : n;
    }
    static String join(String sep, String... parts) {
        List<String> kept = new ArrayList<>();
        for (String part : parts) if (part != null && !part.trim().isEmpty()) kept.add(part.trim());
        return TextUtils.join(sep, kept);
    }
}
