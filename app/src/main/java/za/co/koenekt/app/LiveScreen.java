package za.co.koenekt.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Native live attendance: who is at work now, with counts and last clock time, per department (shop). */
final class LiveScreen {
    private static final int EVERYONE = 0, AT_WORK = 1, NOT_IN = 2, CLOCKED_OUT = 3, NOT_CLOCKED = 4;
    private static final String[] PRESENCE_NAMES = {"Everyone", "At work", "Not in", "Clocked out", "Not clocked today"};
    private static final String ALL = "";
    private final MainActivity a;
    private final List<JSONObject> everyone = new ArrayList<>(), people = new ArrayList<>();
    /** Department → {headcount, at work, clocked today}, from the last load. */
    private final Map<String, int[]> departments = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private List<MainActivity.Company> companies;
    private int company, presence;
    private String department = ALL;
    private LinearLayout chips, tiles;
    private TextView summary;
    private FrameLayout body;

    LiveScreen(MainActivity activity) { a = activity; }
    private int dp(float n) { return a.dp(n); }

    void show() {
        a.setBar("Who's in", false, Ui.iconButton(a, R.drawable.ic_refresh, "Refresh", this::load));
        LinearLayout screen = Ui.column(a);
        LinearLayout header = Ui.column(a); header.setPadding(dp(16), 0, dp(16), dp(4));
        HorizontalScrollView scroll = new HorizontalScrollView(a); scroll.setHorizontalScrollBarEnabled(false);
        chips = Ui.row(a); scroll.addView(chips); header.addView(scroll); Ui.gap(header, 14);
        tiles = Ui.row(a); header.addView(tiles); Ui.gap(header, 14);
        summary = Ui.label(a, ""); header.addView(summary);
        screen.addView(header);
        body = new FrameLayout(a); screen.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        a.mount(screen);
        body.addView(Ui.loading(a));
        a.loadCompanies(list -> {
            companies = list; company = Math.min(a.companyIndex(list), Math.max(0, list.size() - 1));
            if (!list.isEmpty()) department = prefs().getString(key(), ALL);
            buildChips();
            if (list.isEmpty()) message("No companies are available to your account yet.");
            else load();
        }, this::message);
    }

    private SharedPreferences prefs() { return a.getPreferences(Context.MODE_PRIVATE); }
    /** The chosen department is remembered per company. */
    private String key() { return "live_department:" + companies.get(company).url; }

    private void pickDepartment(String name) {
        department = name; prefs().edit().putString(key(), name).apply(); render();
    }

    private void buildChips() {
        chips.removeAllViews();
        if (companies.size() > 1) {
            addChip(Ui.chip(a, companies.get(company).name, () -> {
                String[] names = new String[companies.size()];
                for (int i = 0; i < names.length; i++) names[i] = companies.get(i).name;
                Ui.choose(a, "Company", names, company, which -> {
                    company = which; a.rememberCompany(companies.get(which));
                    department = prefs().getString(key(), ALL); departments.clear(); buildChips(); load();
                });
            }));
        }
        if (departments.size() > 1) {
            addChip(Ui.chip(a, department.equals(ALL) ? "All departments" : department, () -> {
                List<String> names = new ArrayList<>(departments.keySet());
                String[] items = new String[names.size() + 1]; items[0] = "All departments";
                for (int i = 0; i < names.size(); i++) items[i + 1] = names.get(i);
                Ui.choose(a, "Department", items, department.equals(ALL) ? 0 : names.indexOf(department) + 1,
                        which -> pickDepartment(which == 0 ? ALL : names.get(which - 1)));
            }));
        }
        addChip(Ui.chip(a, PRESENCE_NAMES[presence], () -> Ui.choose(a, "Show", PRESENCE_NAMES, presence, which -> {
            presence = which; render();
        })));
    }

    private void addChip(View chip) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        if (chips.getChildCount() > 0) p.leftMargin = dp(8);
        chips.addView(chip, p);
    }

    /** Loads everyone once; department and presence are then filtered here, so switching is instant. */
    private void load() {
        if (companies == null || companies.isEmpty()) return;
        body.removeAllViews(); body.addView(Ui.loading(a));
        String path = "/api/dashboard/live?url=" + MainActivity.query(companies.get(company).url) + "&presence=all";
        a.request(path, null, data -> {
            String asOf = data.optString("as_of");
            summary.setText(asOf.length() >= 16 ? "Updated " + asOf.substring(11, 16) : "");
            everyone.clear(); departments.clear();
            JSONArray rows = data.optJSONArray("people");
            if (rows != null) for (int i = 0; i < rows.length(); i++) {
                JSONObject p = rows.optJSONObject(i); if (p == null) continue;
                everyone.add(p);
                int[] count = departments.computeIfAbsent(dept(p), d -> new int[3]);
                count[0]++; if (p.optBoolean("at_work")) count[1]++; if (seen(p)) count[2]++;
            }
            everyone.sort((x, y) -> x.optBoolean("at_work") != y.optBoolean("at_work") ? (x.optBoolean("at_work") ? -1 : 1)
                    : PeopleScreen.name(x).compareToIgnoreCase(PeopleScreen.name(y)));
            if (!departments.containsKey(department)) department = ALL;   // renamed or emptied since last time
            render();
        }, this::message);
    }

    private static String dept(JSONObject p) {
        String d = p.optString("department").trim();
        return d.isEmpty() ? "(no department)" : d;
    }
    private static boolean seen(JSONObject p) {
        return p.optBoolean("seen_today", !p.optString("last_time", p.optString("last_punch")).isEmpty());
    }

    private boolean shows(JSONObject p) {
        boolean in = p.optBoolean("at_work"), seen = seen(p);
        switch (presence) {
            case AT_WORK: return in;
            case NOT_IN: return !in;
            case CLOCKED_OUT: return !in && seen;
            case NOT_CLOCKED: return !seen;
            default: return true;
        }
    }

    private void render() {
        buildChips();
        int in = 0, total = 0, seen = 0;
        people.clear();
        for (JSONObject p : everyone) {
            if (!department.equals(ALL) && !dept(p).equalsIgnoreCase(department)) continue;
            total++; if (p.optBoolean("at_work")) in++; if (seen(p)) seen++;
            if (shows(p)) people.add(p);
        }
        tiles.removeAllViews();
        tile("At work", in, true, AT_WORK);
        tile("Not in", total - in, false, NOT_IN);
        tile("Clocked today", seen, false, -1);
        showList();
    }

    /** Count tile; with a filter, tapping it shows just those people and tapping again shows everyone. */
    private void tile(String label, int value, boolean highlight, int filter) {
        LinearLayout t = Ui.column(a); t.setPadding(dp(14), dp(12), dp(14), dp(12));
        t.setBackground(Ui.pressable(highlight ? Ui.shape(Ui.TINT, 18, a) : Ui.outline(Ui.SURFACE, Ui.LINE, 1, 18, a), 18, a));
        t.addView(Ui.title(a, String.valueOf(value), 26)); t.addView(Ui.text(a, label, 13, Ui.MUTED));
        t.setContentDescription(value + " " + label);
        if (filter >= 0) t.setOnClickListener(v -> { presence = presence == filter ? EVERYONE : filter; render(); });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        if (tiles.getChildCount() > 0) p.leftMargin = dp(10);
        tiles.addView(t, p);
    }

    private void showList() {
        body.removeAllViews();
        // With every department showing, the list starts with one row per department: tap one to see just it.
        List<String> shops = department.equals(ALL) && departments.size() > 1 ? new ArrayList<>(departments.keySet()) : new ArrayList<>();
        if (people.isEmpty() && shops.isEmpty()) { message(empty()); return; }
        List<Object> items = new ArrayList<>();
        if (!shops.isEmpty()) {
            items.add("Departments"); items.addAll(shops);
            items.add(people.isEmpty() ? empty() : "People");
        }
        items.addAll(people);
        ListView list = new ListView(a); list.setDivider(null); list.setSelector(android.R.color.transparent);
        list.setClipToPadding(false); list.setPadding(0, dp(4), 0, dp(16));
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Object getItem(int i) { return items.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public int getViewTypeCount() { return 3; }
            /** 0 person, 1 department, 2 heading. */
            @Override public int getItemViewType(int i) {
                return items.get(i) instanceof JSONObject ? 0 : i >= 1 && i <= shops.size() ? 1 : 2;
            }
            @Override public View getView(int i, View reuse, ViewGroup parent) {
                Object item = items.get(i);
                if (item instanceof JSONObject) {
                    JSONObject p = (JSONObject) item; boolean in = p.optBoolean("at_work");
                    String last = clock(p.optString("last_time", p.optString("last_punch")));
                    // The dot shows in/out; the time is the last clock event today (which may be a break).
                    return Ui.listRow(a, Ui.dot(a, in ? Ui.LIME : Ui.OUT), PeopleScreen.name(p),
                            department.equals(ALL) ? p.optString("department") : null, last, null);
                }
                String name = (String) item;
                if (getItemViewType(i) == 1) {
                    int[] c = departments.get(name);
                    String sub = c[1] + " of " + c[0] + " at work · " + c[2] + " clocked today";
                    return Ui.listRow(a, Ui.dot(a, c[1] > 0 ? Ui.LIME : Ui.OUT), name, sub, null, () -> pickDepartment(name));
                }
                TextView heading = Ui.label(a, name); heading.setPadding(dp(16), dp(i == 0 ? 4 : 18), dp(16), dp(6));
                return heading;
            }
        });
        body.addView(list);
    }

    private String empty() {
        String where = department.equals(ALL) ? "this company" : department;
        switch (presence) {
            case AT_WORK: return "No one in " + where + " is clocked in right now.";
            case NOT_IN: return "Everyone in " + where + " is at work.";
            case CLOCKED_OUT: return "No one in " + where + " has clocked out today.";
            case NOT_CLOCKED: return "Everyone in " + where + " has clocked today.";
            default: return "No one to show for " + where + ".";
        }
    }

    /** "2026-10-20 08:02:11" or "08:02:11" → "08:02". */
    private static String clock(String stamp) {
        if (stamp == null || stamp.isEmpty()) return "";
        String time = stamp.contains(" ") ? stamp.substring(stamp.lastIndexOf(' ') + 1) : stamp.contains("T") ? stamp.substring(stamp.indexOf('T') + 1) : stamp;
        return time.length() >= 5 ? time.substring(0, 5) : time;
    }

    private void message(String text) {
        body.removeAllViews();
        TextView t = Ui.text(a, text, 15, Ui.MUTED); t.setGravity(Gravity.CENTER); t.setPadding(dp(32), dp(48), dp(32), dp(48));
        body.addView(t, new FrameLayout.LayoutParams(-1, -2));
    }
}
