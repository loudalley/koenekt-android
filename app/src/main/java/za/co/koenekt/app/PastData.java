package za.co.koenekt.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;

import za.co.koenekt.app.HistoryMath.Day;
import za.co.koenekt.app.HistoryMath.History;

/**
 * What the Dashboard, Compare, Reports and Groups sections of My stores load from the Link relay, kept until the
 * login changes: each store's daily totals (GET /api/history), the account's shop groups (GET /api/groups) and the
 * owner's dashboard layout (GET /api/me/dashboard, shared with the website). Figures stay in memory only.
 */
final class PastData {
    private static final long FRESH_MS = 10 * 60_000L;
    private static final int MAX_DAYS = 800;
    private final StoresScreen s;
    History history;
    private long historyAt;
    private String loading = "";
    String historyError = "";
    JSONArray groups;
    boolean canEditGroups;
    String groupsError = "";
    JSONObject layout, saved;
    boolean layoutLoaded, dirty;

    PastData(StoresScreen stores) { s = stores; }

    /** A store: who, plus who to compare it with. */
    static final class Entity {
        final String key, name; final List<String> ids;
        Entity(String key, String name, List<String> ids) { this.key = key; this.name = name; this.ids = ids; }
    }

    static LocalDate today() { return LocalDate.now(); }

    // ---------------------------------------------------------------- daily totals
    boolean has(String from, String to) {
        return history != null && history.covers(from, to) && System.currentTimeMillis() - historyAt < FRESH_MS;
    }
    /** Loads (or widens) the daily totals to cover from..to, then runs done. Several asks for one range share one call. */
    void needHistory(String from, String to, Runnable done) {
        if (has(from, to)) { done.run(); return; }
        if (history != null && System.currentTimeMillis() - historyAt < FRESH_MS) {   // widen what we have, within the limit
            if (history.from.compareTo(from) < 0 && days(history.from, to) <= MAX_DAYS) from = history.from;
            if (history.to.compareTo(to) > 0 && days(from, history.to) <= MAX_DAYS) to = history.to;
        }
        if (days(from, to) > MAX_DAYS) from = LocalDate.parse(to).minusDays(MAX_DAYS - 1).toString();
        String key = from + "|" + to;
        if (key.equals(loading)) return;
        loading = key;
        s.a.linkRequest("GET", "/api/history?from=" + from + "&to=" + to, null, data -> {
            loading = ""; historyError = ""; history = parse(data); historyAt = System.currentTimeMillis(); done.run();
        }, message -> { loading = ""; historyError = message; done.run(); });
    }
    void forget() { history = null; historyAt = 0; groups = null; }
    /** What is loaded so far (empty before the first answer). */
    History history() { return history != null ? history : new History(); }
    /** The history plus today's live figures from each store's Live answer (the dashboard's "Include today"). */
    History withToday() {
        java.util.Map<String, Day> live = new java.util.HashMap<>();
        String now = today().toString();
        for (org.json.JSONObject st : s.stores) {
            org.json.JSONObject r = s.answers.get(st.optString("store"));
            if (r == null || !r.optString("day").startsWith(now)) continue;
            Day d = new Day(); d.turnover = StoresScreen.num(r, "sales"); if (Double.isNaN(d.turnover)) d.turnover = 0;
            d.salesExcl = StoresScreen.num(r, "sales_excl"); d.gpValue = StoresScreen.num(r, "gp_value");
            d.cost = Double.isNaN(d.salesExcl) || Double.isNaN(d.gpValue) ? Double.NaN : d.salesExcl - d.gpValue;
            org.json.JSONArray items = r.optJSONArray("items"); d.itemsSold = items == null ? 0 : items.length();
            live.put(st.optString("store"), d);
        }
        return HistoryMath.withToday(history(), live, today());
    }
    private static long days(String a, String b) { return ChronoUnit.DAYS.between(LocalDate.parse(a), LocalDate.parse(b)) + 1; }

    static History parse(JSONObject data) {
        History h = new History(); h.from = data.optString("from"); h.to = data.optString("to");
        JSONObject stores = data.optJSONObject("stores");
        if (stores == null) return h;
        for (Iterator<String> it = stores.keys(); it.hasNext(); ) {
            String id = it.next(); JSONObject st = stores.optJSONObject(id); if (st == null) continue;
            TreeMap<String, Day> days = new TreeMap<>();
            JSONObject ds = st.optJSONObject("days");
            if (ds != null) for (Iterator<String> k = ds.keys(); k.hasNext(); ) {
                String d = k.next(); JSONObject r = ds.optJSONObject(d); if (r == null) continue;
                Day day = new Day();
                day.turnover = StoresScreen.num(r, "turnover"); day.salesExcl = StoresScreen.num(r, "sales_excl");
                day.cost = StoresScreen.num(r, "cost"); day.gpValue = StoresScreen.num(r, "gp_value");
                day.stock = StoresScreen.num(r, "stock_excl"); day.itemsSold = StoresScreen.num(r, "items_sold");
                day.lowGp = StoresScreen.num(r, "low_gp_count");
                days.put(d, day);
            }
            h.stores.put(id, days);
            if (!st.isNull("latest") && !st.optString("latest").isEmpty()) h.latest.put(id, st.optString("latest"));
        }
        return h;
    }

    // ---------------------------------------------------------------- groups
    void needGroups(Runnable done) {
        if (groups != null) { done.run(); return; }
        s.a.linkRequest("GET", "/api/groups", null, data -> {
            groups = data.optJSONArray("groups"); if (groups == null) groups = new JSONArray();
            canEditGroups = data.optBoolean("can_edit"); groupsError = ""; done.run();
        }, message -> { groups = new JSONArray(); groupsError = message; done.run(); });
    }
    void setGroups(JSONObject reply) { JSONArray g = reply.optJSONArray("groups"); if (g != null) groups = g; }

    /** Who is compared: each store, each of the owner's groups, or each shop type (set by Koenekt on the relay). */
    List<Entity> entities(String by) {
        List<Entity> out = new ArrayList<>();
        if ("group".equals(by)) {
            if (groups != null) for (int i = 0; i < groups.length(); i++) {
                JSONObject g = groups.optJSONObject(i); if (g == null) continue;
                List<String> ids = strings(g.optJSONArray("stores"));
                if (!ids.isEmpty()) out.add(new Entity(g.optString("id"), g.optString("name"), ids));
            }
            return out;
        }
        if ("type".equals(by) || "shoptype".equals(by)) {
            TreeMap<String, List<String>> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER); List<String> none = new ArrayList<>();
            for (JSONObject st : s.stores) {
                List<String> names = strings(st.optJSONArray("type".equals(by) ? "type_families" : "type_labels"));
                if (names.isEmpty()) none.add(st.optString("store"));
                for (String n : names) m.computeIfAbsent(n, k -> new ArrayList<>()).add(st.optString("store"));
            }
            for (String n : m.keySet()) out.add(new Entity(n, n, m.get(n)));
            if (!none.isEmpty()) out.add(new Entity("", "Type not set", none));
            return out;
        }
        for (JSONObject st : s.stores) out.add(new Entity(st.optString("store"), st.optString("name"), List.of(st.optString("store"))));
        return out;
    }
    static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a != null) for (int i = 0; i < a.length(); i++) if (!a.optString(i).isEmpty()) out.add(a.optString(i));
        return out;
    }

    /** A tile's stores: all, one store, one group or one shop type. */
    List<String> scopeIds(JSONObject scope) {
        String type = scope == null ? "all" : scope.optString("type", "all");
        List<String> out = new ArrayList<>();
        for (JSONObject st : s.stores) {
            String id = st.optString("store");
            if (type.equals("store") && !id.equals(scope.optString("id"))) continue;
            if (type.equals("shop_type") && !strings(st.optJSONArray("types")).contains(scope.optString("code"))
                    && !strings(st.optJSONArray("type_families")).contains(scope.optString("code"))) continue;
            if (type.equals("group") && !groupStores(scope.optString("id")).contains(id)) continue;
            out.add(id);
        }
        return out;
    }
    private List<String> groupStores(String gid) {
        if (groups != null) for (int i = 0; i < groups.length(); i++) {
            JSONObject g = groups.optJSONObject(i); if (g != null && g.optString("id").equals(gid)) return strings(g.optJSONArray("stores"));
        }
        return new ArrayList<>();
    }
    String scopeName(JSONObject scope) {
        String type = scope == null ? "all" : scope.optString("type", "all");
        if (type.equals("store")) { JSONObject st = s.store(scope.optString("id")); return st == null ? "A store" : st.optString("name"); }
        if (type.equals("group")) {
            if (groups != null) for (int i = 0; i < groups.length(); i++) { JSONObject g = groups.optJSONObject(i); if (g != null && g.optString("id").equals(scope.optString("id"))) return g.optString("name"); }
            return "A group";
        }
        if (type.equals("shop_type")) return scope.optString("code");
        return s.stores.size() == 1 ? s.stores.get(0).optString("name") : "All stores";
    }
    /** {value, label} for picking a tile's stores. */
    List<String[]> scopes() {
        List<String[]> out = new ArrayList<>(); out.add(new String[]{"all", "All stores"});
        for (JSONObject st : s.stores) out.add(new String[]{"store:" + st.optString("store"), st.optString("name")});
        if (groups != null) for (int i = 0; i < groups.length(); i++) { JSONObject g = groups.optJSONObject(i); if (g != null) out.add(new String[]{"group:" + g.optString("id"), "Group: " + g.optString("name")}); }
        TreeSet<String> fam = new TreeSet<>();
        for (JSONObject st : s.stores) fam.addAll(strings(st.optJSONArray("type_families")));
        for (String f : fam) out.add(new String[]{"shop_type:" + f, "Type: " + f});
        return out;
    }
    static JSONObject scope(String value) {
        try {
            if (value.equals("all")) return new JSONObject().put("type", "all");
            int i = value.indexOf(':'); String t = value.substring(0, i), x = value.substring(i + 1);
            return new JSONObject().put("type", t).put(t.equals("shop_type") ? "code" : "id", x);
        } catch (Exception e) { return new JSONObject(); }
    }
    static String scopeValue(JSONObject scope) {
        if (scope == null || "all".equals(scope.optString("type", "all"))) return "all";
        String t = scope.optString("type"); return t + ":" + scope.optString(t.equals("shop_type") ? "code" : "id");
    }

    // ---------------------------------------------------------------- dashboard layout
    void needLayout(Runnable done) {
        if (layoutLoaded) { done.run(); return; }
        s.a.linkRequest("GET", "/api/me/dashboard", null, data -> {
            JSONObject l = data.optJSONObject("layout");
            saved = l != null && l.optJSONArray("tiles") != null ? l : null;
            layout = copy(saved != null ? saved : defaults()); layoutLoaded = true; dirty = false; done.run();
        }, message -> { layout = defaults(); layoutLoaded = true; done.run(); });
    }
    static JSONObject defaults() { try { return new JSONObject(HistoryMath.DEFAULT_LAYOUT); } catch (Exception e) { return new JSONObject(); } }
    static JSONObject copy(JSONObject o) { try { return new JSONObject(o.toString()); } catch (Exception e) { return defaults(); } }
    void saveLayout(Runnable done, Consumer<String> failed) {
        try {
            s.a.linkRequest("POST", "/api/me/dashboard", new JSONObject().put("layout", layout), data -> { saved = copy(layout); dirty = false; done.run(); }, failed);
        } catch (Exception e) { failed.accept(e.getMessage()); }
    }
    void resetLayout(Runnable done, Consumer<String> failed) {
        try {
            s.a.linkRequest("POST", "/api/me/dashboard", new JSONObject().put("reset", true), data -> { saved = null; layout = defaults(); dirty = false; done.run(); }, failed);
        } catch (Exception e) { failed.accept(e.getMessage()); }
    }
}
