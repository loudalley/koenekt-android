package za.co.koenekt.app;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Native live attendance: who is at work now, with counts and last clock time. */
final class LiveScreen {
    private static final String[] PRESENCE_VALUES = {"all", "at_work", "not_at_work"};
    private static final String[] PRESENCE_NAMES = {"Everyone", "At work", "Not in"};
    private final MainActivity a;
    private final List<JSONObject> people = new ArrayList<>();
    private List<MainActivity.Company> companies;
    private int company, presence;
    private LinearLayout chips, tiles;
    private TextView summary;
    private FrameLayout body;

    LiveScreen(MainActivity activity) { a = activity; }
    private int dp(float n) { return a.dp(n); }

    void show() {
        a.setBar("Who's in", false, Ui.iconButton(a, R.drawable.ic_refresh, "Refresh", this::load));
        LinearLayout screen = Ui.column(a);
        LinearLayout header = Ui.column(a); header.setPadding(dp(16), 0, dp(16), dp(4));
        chips = Ui.row(a); header.addView(chips); Ui.gap(header, 14);
        tiles = Ui.row(a); header.addView(tiles); Ui.gap(header, 14);
        summary = Ui.label(a, ""); header.addView(summary);
        screen.addView(header);
        body = new FrameLayout(a); screen.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        a.mount(screen);
        body.addView(Ui.loading(a));
        a.loadCompanies(list -> {
            companies = list; company = Math.min(a.companyIndex(list), Math.max(0, list.size() - 1));
            buildChips();
            if (list.isEmpty()) message("No companies are available to your account yet.");
            else load();
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
        chips.addView(Ui.chip(a, PRESENCE_NAMES[presence], () -> Ui.choose(a, "Show", PRESENCE_NAMES, presence, which -> {
            presence = which; buildChips(); load();
        })));
    }

    private void load() {
        if (companies == null || companies.isEmpty()) return;
        body.removeAllViews(); body.addView(Ui.loading(a));
        String path = "/api/dashboard/live?url=" + MainActivity.query(companies.get(company).url) + "&presence=" + PRESENCE_VALUES[presence];
        a.request(path, null, data -> {
            JSONObject totals = data.optJSONObject("totals");
            tiles.removeAllViews();
            if (totals != null) {
                tile("At work", totals.optInt("at_work"), true);
                tile("Not in", totals.optInt("not_at_work"), false);
                tile("Clocked today", totals.optInt("seen_today"), false);
            }
            String asOf = data.optString("as_of");
            summary.setText(asOf.length() >= 16 ? "Updated " + asOf.substring(11, 16) : "");
            people.clear();
            JSONArray rows = data.optJSONArray("people");
            if (rows != null) for (int i = 0; i < rows.length(); i++) if (rows.optJSONObject(i) != null) people.add(rows.optJSONObject(i));
            people.sort((x, y) -> x.optBoolean("at_work") != y.optBoolean("at_work") ? (x.optBoolean("at_work") ? -1 : 1)
                    : PeopleScreen.name(x).compareToIgnoreCase(PeopleScreen.name(y)));
            showList();
        }, this::message);
    }

    private void tile(String label, int value, boolean highlight) {
        LinearLayout t = Ui.column(a); t.setPadding(dp(14), dp(12), dp(14), dp(12));
        t.setBackground(highlight ? Ui.shape(Ui.TINT, 18, a) : Ui.outline(Ui.SURFACE, Ui.LINE, 1, 18, a));
        t.addView(Ui.title(a, String.valueOf(value), 26)); t.addView(Ui.text(a, label, 13, Ui.MUTED));
        t.setContentDescription(value + " " + label);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        if (tiles.getChildCount() > 0) p.leftMargin = dp(10);
        tiles.addView(t, p);
    }

    private void showList() {
        body.removeAllViews();
        if (people.isEmpty()) { message(presence == 1 ? "No one is clocked in right now." : "No one to show for this company."); return; }
        ListView list = new ListView(a); list.setDivider(null); list.setSelector(android.R.color.transparent);
        list.setClipToPadding(false); list.setPadding(0, dp(4), 0, dp(16));
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return people.size(); }
            @Override public Object getItem(int i) { return people.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View reuse, ViewGroup parent) {
                JSONObject p = people.get(i); boolean in = p.optBoolean("at_work");
                String last = clock(p.optString("last_time", p.optString("last_punch")));
                // The dot shows in/out; the time is the last clock event today (which may be a break).
                return Ui.listRow(a, Ui.dot(a, in ? Ui.LIME : Ui.OUT), PeopleScreen.name(p), p.optString("department"), last, null);
            }
        });
        body.addView(list);
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
