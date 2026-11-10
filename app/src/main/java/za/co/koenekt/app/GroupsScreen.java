package za.co.koenekt.app;

import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * My stores → Groups (owners): the account's own groups of stores ("North", "Fresh stores") for Compare and the
 * Dashboard, kept on the relay and shared with the website (GET/POST /api/groups, /api/groups/delete). Shop types are
 * set by Koenekt. An owner limited to some stores sees only those and may change only groups made of them.
 */
final class GroupsScreen {
    private final StoresScreen s;
    private final MainActivity a;

    GroupsScreen(StoresScreen stores) { s = stores; a = stores.a; }
    private int dp(float n) { return a.dp(n); }

    void render() {
        a.setBar("Groups", false, Ui.iconButton(a, R.drawable.ic_add, "New group", () -> edit(null)));
        LinearLayout page = a.page(); page.setPadding(dp(16), dp(4), dp(16), dp(28));
        if (!s.ready(page)) return;
        if (s.past.groups == null) { page.addView(Ui.loading(a)); s.past.needGroups(s::redraw); return; }
        page.addView(Ui.text(a, "Put your stores together your way, for example \"North\" or \"Fresh stores\", to compare them on Compare and the Dashboard. "
                + "The groups are the same on the website.", 14, Ui.MUTED));
        Ui.gap(page, 14);
        if (!s.past.groupsError.isEmpty()) { page.addView(Ui.notice(a, s.past.groupsError, true)); Ui.gap(page, 12); }
        JSONArray g = s.past.groups;
        if (g.length() == 0) { page.addView(s.message("No groups yet. Tap + to make one.")); return; }
        LinearLayout card = Ui.column(a); card.setBackground(Ui.outline(Ui.SURFACE, Ui.LINE, 1, 20, a));
        for (int i = 0; i < g.length(); i++) {
            JSONObject group = g.optJSONObject(i); if (group == null) continue;
            if (card.getChildCount() > 0) card.addView(Ui.divider(a, 16));
            List<String> names = new ArrayList<>();
            for (String id : PastData.strings(group.optJSONArray("stores"))) { JSONObject st = s.store(id); names.add(st == null ? "A store" : st.optString("name")); }
            boolean editable = group.optBoolean("editable") && s.past.canEditGroups;
            String acc = s.accounts().size() > 1 && !group.optString("account").isEmpty() ? group.optString("account") + " · " : "";
            card.addView(Ui.listRow(a, null, group.optString("name"), acc + String.join(", ", names), editable ? null : "Read only",
                    editable ? () -> edit(group) : null));
        }
        page.addView(card, Ui.fill(a, -1));
    }

    private void edit(JSONObject group) {
        if (!s.past.canEditGroups || s.past.groups == null) { a.toast("Only a store owner can change groups."); return; }
        if (group == null) {          // a new group lives in one account: ask which, when the login owns several
            List<String> owned = new ArrayList<>();
            for (String acc : s.accounts()) for (JSONObject st : s.stores)
                if (acc.equals(st.optString("account")) && "owner".equals(st.optString("role"))) { owned.add(acc); break; }
            if (owned.size() > 1) {
                Ui.choose(a, "Group stores of", owned.toArray(new String[0]), -1, w -> named(null, owned.get(w)));
                return;
            }
            named(null, owned.isEmpty() ? "" : owned.get(0));
            return;
        }
        named(group, group.optString("account"));
    }
    private void named(JSONObject group, String account) {
        EditText name = new EditText(a); name.setSingleLine(true); name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setHint("e.g. North"); name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
        if (group != null) name.setText(group.optString("name"));
        FrameLayout wrap = new FrameLayout(a); wrap.setPadding(dp(24), dp(8), dp(24), 0); wrap.addView(name);
        android.app.AlertDialog.Builder b = Ui.dialog(a).setTitle(group == null ? "New group" : "Group name").setView(wrap)
                .setPositiveButton("Next", (d, w) -> pickStores(group, name.getText().toString().trim(), account))
                .setNegativeButton("Cancel", null);
        if (group != null) b.setNeutralButton("Delete", (d, w) -> Ui.dialog(a).setTitle("Delete " + group.optString("name") + "?")
                .setMessage("The stores stay as they are; only the group goes, here and on the website.")
                .setPositiveButton("Delete", (d2, w2) -> send("/api/groups/delete", body(group.optString("id"), null, null, account)))
                .setNegativeButton("Cancel", null).show());
        b.show();
    }
    private void pickStores(JSONObject group, String name, String account) {
        if (name.isEmpty()) { a.toast("Give the group a name."); return; }
        List<JSONObject> list = new ArrayList<>();            // only the stores of the group's account
        for (JSONObject st : s.stores) if (account.isEmpty() || st.optString("account").isEmpty() || account.equals(st.optString("account"))) list.add(st);
        String[] names = new String[list.size()]; boolean[] on = new boolean[list.size()];
        List<String> had = group == null ? new ArrayList<>() : PastData.strings(group.optJSONArray("stores"));
        for (int i = 0; i < names.length; i++) { names[i] = list.get(i).optString("name"); on[i] = had.contains(list.get(i).optString("store")); }
        Ui.dialog(a).setTitle("Stores in " + name).setMultiChoiceItems(names, on, (d, w, checked) -> on[w] = checked)
                .setPositiveButton("Save group", (d, w) -> {
                    JSONArray ids = new JSONArray();
                    for (int i = 0; i < on.length; i++) if (on[i]) ids.put(list.get(i).optString("store"));
                    send("/api/groups", body(group == null ? null : group.optString("id"), name, ids, account));
                }).setNegativeButton("Cancel", null).show();
    }
    private static JSONObject body(String id, String name, JSONArray stores, String account) {
        JSONObject o = new JSONObject();
        try {
            if (id != null) o.put("id", id); if (name != null) o.put("name", name); if (stores != null) o.put("stores", stores);
            if (account != null && !account.isEmpty()) o.put("account", account);
        } catch (Exception ignored) { }
        return o;
    }
    private void send(String path, JSONObject body) {
        a.linkRequest("POST", path, body, data -> { s.past.setGroups(data); s.redraw(); }, a::toast);
    }
}
