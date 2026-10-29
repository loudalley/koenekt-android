package za.co.koenekt.app;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Native Reporter updates (the licence site's Updates tab): shop PCs with their Windows name, address and install
 * date; tick PCs and update or cancel just those, every PC, or one; pause and resume. Every call goes to the licence
 * site, which holds the update-service token, checks the operator session and confirms fleet or multi-PC requests.
 */
final class UpdatesScreen {
    private static final String API = "/licences/api/updates/";
    private static final String WEB = AppPolicy.ADMIN + "/licences/#updates";
    private static final DateTimeFormatter SA_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)
            .withZone(ZoneId.of("Africa/Johannesburg"));
    private static final List<String> BUSY_PHASES = Arrays.asList("download", "verify", "install", "health", "check");
    // The updater's own reasons for waiting on purpose (Reporter app/update/client.py), in words, as on the website.
    private static final String[][] WAITING = {
        {"maintenance window", "Waiting for its 03:00–04:00 install window. \"Update this PC\" installs it sooner."},
        {"scheduled report send", "Waiting until the report due about now has been sent."},
        {"window is open", "Waiting until Reporter is closed on that PC."},
        {"work is in progress", "Waiting until the PC is idle (a report or another install is running)."},
        {"install lock", "Waiting until the PC is idle (a report or another install is running)."},
        {"windows installer", "Waiting until the PC is idle (a report or another install is running)."},
    };
    private final MainActivity a;
    private final List<JSONObject> devices = new ArrayList<>();
    private final Set<String> ticked = new LinkedHashSet<>();   // kept across refreshes, like the website
    private String release = "";   // "" asks for whatever each PC would be offered
    private LinearLayout tiles, chips, selection;
    private View selectionLine;
    private TextView summary, selectionCount;
    private FrameLayout body;
    private ImageView more;
    private BaseAdapter adapter;

    UpdatesScreen(MainActivity activity) { a = activity; }
    private int dp(float n) { return a.dp(n); }

    void show() {
        more = Ui.iconButton(a, R.drawable.ic_more, "More options", this::menu);
        a.setBar("Reporter updates", true, Ui.iconButton(a, R.drawable.ic_refresh, "Refresh", this::load), more);
        LinearLayout screen = Ui.column(a);
        LinearLayout header = Ui.column(a); header.setPadding(dp(16), 0, dp(16), dp(4));
        tiles = Ui.row(a); header.addView(tiles); Ui.gap(header, 14);
        chips = Ui.row(a); header.addView(chips);
        summary = Ui.label(a, ""); summary.setPadding(0, dp(10), 0, 0); header.addView(summary);
        screen.addView(header);
        body = new FrameLayout(a); screen.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        selection = Ui.row(a); selection.setBackgroundColor(Ui.SURFACE); selection.setPadding(dp(16), dp(10), dp(12), dp(10));
        selectionCount = Ui.title(a, "", 15); selection.addView(selectionCount, new LinearLayout.LayoutParams(0, -2, 1));
        selection.addView(Ui.quiet(a, "Clear", () -> { ticked.clear(); refreshList(); }), new LinearLayout.LayoutParams(-2, dp(44)));
        TextView cancel = Ui.secondary(a, "Cancel…", () -> preview(true, false, new ArrayList<>(ticked)));
        selection.addView(cancel, new LinearLayout.LayoutParams(-2, dp(44)));
        TextView update = Ui.primary(a, "Update…", () -> preview(false, false, new ArrayList<>(ticked)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(44)); p.leftMargin = dp(8);
        selection.addView(update, p);
        selectionLine = new View(a); selectionLine.setBackgroundColor(Ui.LINE);
        screen.addView(selectionLine, new LinearLayout.LayoutParams(-1, Math.max(1, dp(0.5f))));
        screen.addView(selection, new LinearLayout.LayoutParams(-1, -2));
        a.mount(screen);
        if (devices.isEmpty()) { body.addView(Ui.loading(a)); refreshSelection(); }
        else showList();
        load();
    }

    private void menu() {
        PopupMenu m = new PopupMenu(a, more);
        m.getMenu().add("Tick every PC").setOnMenuItemClickListener(i -> {
            for (JSONObject d : devices) ticked.add(d.optString("device_id")); refreshList(); return true;
        });
        m.getMenu().add("Update every PC…").setOnMenuItemClickListener(i -> { preview(false, true, Collections.emptyList()); return true; });
        m.getMenu().add("Cancel every request…").setOnMenuItemClickListener(i -> { preview(true, true, Collections.emptyList()); return true; });
        m.getMenu().add("Releases and full page (web)").setOnMenuItemClickListener(i -> { a.openWeb("Reporter updates", WEB); return true; });
        m.show();
    }

    private void load() {
        a.adminRequest(API + "devices", null, data -> {
            devices.clear();
            JSONArray rows = data.optJSONArray("devices");
            if (rows != null) for (int i = 0; i < rows.length(); i++) if (rows.optJSONObject(i) != null) devices.add(rows.optJSONObject(i));
            devices.sort((x, y) -> {
                int shop = shop(x).compareToIgnoreCase(shop(y));
                return shop != 0 ? shop : x.optString("device_id").compareTo(y.optString("device_id"));
            });
            // A PC that has left the service cannot stay ticked.
            Set<String> present = new TreeSet<>(); for (JSONObject d : devices) present.add(d.optString("device_id"));
            ticked.retainAll(present);
            if (body == null || !body.isAttachedToWindow()) return;
            showList();
            String refreshed = "Refreshed " + Instant.now().atZone(ZoneId.of("Africa/Johannesburg")).toLocalTime().toString().substring(0, 5)
                    + (devices.isEmpty() ? "" : " · Tick PCs to update or cancel just those. Tap a PC for details.");
            summary.setText(refreshed);
        }, message -> { if (body != null && body.isAttachedToWindow()) message(message); });
    }

    private void showList() {
        tiles.removeAllViews();
        int waiting = 0, attention = 0;
        for (JSONObject d : devices) {
            JSONObject f = request(d); if (f != null && !expired(f)) waiting++;
            if (failed(d) || stale(d.optString("last_contact"))) attention++;
        }
        tile(String.valueOf(devices.size()), "Shop PCs", true);
        tile(String.valueOf(waiting), "Asked to update", false);
        tile(String.valueOf(attention), "Need a look", false);
        buildChips();
        body.removeAllViews();
        if (devices.isEmpty()) { message("No Reporter PCs have enrolled with the update service yet."); refreshSelection(); return; }
        ListView list = new ListView(a); list.setDivider(null); list.setSelector(android.R.color.transparent);
        list.setClipToPadding(false); list.setPadding(0, dp(4), 0, dp(16));
        adapter = new BaseAdapter() {
            @Override public int getCount() { return devices.size(); }
            @Override public Object getItem(int i) { return devices.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View reuse, ViewGroup parent) { return row(devices.get(i)); }
        };
        list.setAdapter(adapter); body.addView(list);
        refreshSelection();
    }
    private void refreshList() { if (adapter != null) adapter.notifyDataSetChanged(); refreshSelection(); }
    private void refreshSelection() {
        int n = ticked.size();
        selection.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        selectionLine.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        String count = n + " ticked";
        selectionCount.setText(count);
    }

    private void buildChips() {
        chips.removeAllViews();
        Set<String> offered = new TreeSet<>();
        for (JSONObject d : devices) if (!d.optString("offered_release").isEmpty()) offered.add(d.optString("offered_release"));
        if (!release.isEmpty()) offered.add(release);
        chips.setVisibility(offered.isEmpty() ? View.GONE : View.VISIBLE);
        if (offered.isEmpty()) return;
        List<String> names = new ArrayList<>(); names.add("Latest for each PC"); names.addAll(offered);
        chips.addView(Ui.chip(a, release.isEmpty() ? "Latest release" : release, () ->
                Ui.choose(a, "Update to", names.toArray(new String[0]), Math.max(0, names.indexOf(release)), which -> {
                    release = which == 0 ? "" : names.get(which); buildChips();
                })));
        chips.setPadding(0, 0, 0, dp(2));
    }

    private void tile(String value, String label, boolean highlight) {
        LinearLayout t = Ui.column(a); t.setPadding(dp(14), dp(12), dp(14), dp(12));
        t.setBackground(highlight ? Ui.shape(Ui.TINT, 18, a) : Ui.outline(Ui.SURFACE, Ui.LINE, 1, 18, a));
        t.addView(Ui.title(a, value, 26)); t.addView(Ui.text(a, label, 13, Ui.MUTED));
        t.setContentDescription(value + " " + label);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        if (tiles.getChildCount() > 0) p.leftMargin = dp(10);
        tiles.addView(t, p);
    }

    private View row(JSONObject d) {
        String id = d.optString("device_id");
        CheckBox box = new CheckBox(a); box.setChecked(ticked.contains(id));
        box.setButtonTintList(ColorStateList.valueOf(Ui.BRAND));
        box.setContentDescription("Tick " + shop(d) + " " + pcName(d));
        box.setOnCheckedChangeListener((v, on) -> { if (on) ticked.add(id); else ticked.remove(id); refreshSelection(); });
        String sub = PeopleScreen.join(" · ", pcName(d), installed(d), d.optString("last_contact").isEmpty() ? "never heard from" : ago(d.optString("last_contact")));
        return Ui.listRow(a, box, shop(d), sub, state(d), () -> details(d));
    }

    /** One or two words for the list; the details dialog explains. */
    private static String state(JSONObject d) {
        String phase = d.optString("phase", "idle");
        JSONObject f = request(d);
        if (d.optBoolean("paused")) return "Paused";
        if (phase.equals("failed")) return "Failed";
        if (phase.equals("deferred")) return "Waiting";
        if (BUSY_PHASES.contains(phase)) return capital(phase) + "…";
        if (f != null && !expired(f)) return "Requested";
        if (!d.optString("error").isEmpty()) return "Error";
        if (!d.optString("offered_version").isEmpty()) return "Offered " + d.optString("offered_version");
        return "Up to date";
    }

    private void details(JSONObject d) {
        String id = d.optString("device_id");
        LinearLayout view = Ui.column(a); view.setPadding(dp(24), dp(24), dp(24), dp(8));
        view.addView(Ui.title(a, shop(d), 20));
        view.addView(Ui.text(a, PeopleScreen.join(" · ", d.optString("hostname"), shortId(id)), 14, Ui.MUTED)); Ui.gap(view, 14);
        String phase = d.optString("phase", "idle"), error = d.optString("error");
        boolean deferred = phase.equals("deferred");
        String status = d.optBoolean("paused") ? "Paused — offered nothing until resumed" : deferred ? "Waiting" : capital(phase);
        if (d.optBoolean("reboot_pending")) status += ", reboot pending";
        fact(view, "Status", status);
        if (!error.isEmpty()) {
            TextView why = Ui.text(a, deferred ? waitingText(error) : error, 14, deferred ? Ui.MUTED : Ui.DANGER);
            why.setPadding(0, dp(4), 0, dp(10)); view.addView(why); view.addView(Ui.divider(a, 0));
        }
        String working = d.optString("target_version");
        fact(view, "Reporter", installed(d).isEmpty() ? "unknown" : installed(d) + (!working.isEmpty() && !working.equals(installed(d)) ? " (working on " + working + ")" : ""));
        fact(view, "Would be offered", d.optBoolean("paused") ? "nothing (paused)" : d.optString("offered_version").isEmpty() ? "nothing newer"
                : d.optString("offered_version") + "\n" + d.optString("offered_release"));
        JSONObject f = request(d);
        if (f != null) fact(view, "Update request", expired(f) ? "Expired (" + f.optString("release_id") + ")"
                : "Requested — not installed yet\n" + f.optString("release_id") + "\nasked " + ago(f.optString("requested_at")) + ", lapses " + saTime(f.optString("expires_at")));
        fact(view, "Last contact", d.optString("last_contact").isEmpty() ? "never" : ago(d.optString("last_contact")) + "\n" + saTime(d.optString("last_contact")));
        fact(view, "PC name", d.optString("hostname"));
        fact(view, "IP address", d.optString("ip"));
        fact(view, "Installed", saTime(d.optString("enrolled_at")));
        String updater = d.optString("updater_version");
        fact(view, "Updater", updater.isEmpty() ? "" : updater + (isOld(updater) ? " — needs Reporter 1.9.85 first" : ""));
        fact(view, "Architecture", d.optString("arch"));
        fact(view, "PC id", id);
        AlertDialog dialog = new AlertDialog.Builder(a).setView(scroll(view)).setPositiveButton("Close", null).create();
        Ui.gap(view, 18);
        view.addView(Ui.primary(a, "Update this PC", () -> { dialog.dismiss(); preview(false, false, Collections.singletonList(id)); }), Ui.fill(a, 52));
        if (f != null && !expired(f)) {
            Ui.gap(view, 8);
            TextView cancel = Ui.secondary(a, "Cancel request", () -> { dialog.dismiss(); preview(true, false, Collections.singletonList(id)); });
            cancel.setTextColor(Ui.DANGER); view.addView(cancel, Ui.fill(a, 52));
        }
        Ui.gap(view, 8);
        boolean paused = d.optBoolean("paused");
        view.addView(Ui.quiet(a, paused ? "Resume updates" : "Pause updates", () -> { dialog.dismiss(); pause(d, !paused); }), Ui.fill(a, 52));
        dialog.show();
    }

    /** Ask the service what WOULD happen, show it, and only then offer the button (same as the website). */
    private void preview(boolean cancel, boolean all, List<String> ids) {
        if (!all && ids.isEmpty()) return;
        final JSONObject req;
        try {
            req = new JSONObject().put("action", cancel ? "cancel" : "force");
            if (all) req.put("all", true);
            else if (ids.size() == 1) req.put("device_id", ids.get(0));
            else req.put("device_ids", new JSONArray(ids));
            if (!cancel && !release.isEmpty()) req.put("release_id", release);
        } catch (Exception e) { a.toast("Could not prepare the request."); return; }
        LinearLayout view = Ui.column(a); view.setPadding(dp(24), dp(20), dp(24), dp(4));
        view.addView(Ui.title(a, cancel ? "Cancel update request" : "Request an update now", 20)); Ui.gap(view, 8);
        view.addView(Ui.text(a, scope(all, ids) + (cancel || release.isEmpty() ? "" : "\nOnly release " + release), 14, Ui.MUTED)); Ui.gap(view, 12);
        LinearLayout results = Ui.column(a); results.addView(Ui.loading(a)); view.addView(results);
        AlertDialog dialog = new AlertDialog.Builder(a).setView(scroll(view)).setNegativeButton("Close", null)
                .setPositiveButton("Checking…", null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        a.adminRequest(API + "preview", req, data -> {
            JSONArray rows = data.optJSONArray("devices") == null ? new JSONArray() : data.optJSONArray("devices");
            int n = count(rows, cancel ? "would_cancel" : "would_queue");
            results.removeAllViews(); addResults(results, rows);
            Ui.gap(results, 12);
            results.addView(Ui.text(a, cancel
                    ? "A PC that has already started installing will finish. Cancelling only stops PCs that have not picked the request up yet (they see it at their next check, within about 10 minutes)."
                    : "This only lifts the overnight-window wait. Each PC installs at its next check (within about 10 minutes), only when Reporter is closed and idle, and never while paused. A request lapses after 24 hours. It is not installed until the PC reports the new version.",
                    13, Ui.MUTED));
            TextView go = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            go.setText(n == 0 ? (cancel ? "Nothing to cancel" : "Nothing would be asked")
                    : cancel ? "Cancel " + n + (n == 1 ? " request" : " requests") : "Ask " + n + (n == 1 ? " PC" : " PCs") + " to update");
            go.setEnabled(n > 0);
            if (cancel) go.setTextColor(Ui.DANGER);
            go.setOnClickListener(v -> { dialog.dismiss(); send(cancel, all, ids, req); });
        }, message -> { results.removeAllViews(); results.addView(Ui.notice(a, message, true)); });
    }

    private void send(boolean cancel, boolean all, List<String> ids, JSONObject req) {
        try {
            req.remove("action");
            // The licence site refuses a fleet or multi-PC request without the confirmation the preview stands for.
            if (all) req.put("confirm", "all"); else if (ids.size() > 1) req.put("confirm", "selected");
        } catch (Exception e) { a.toast("Could not prepare the request."); return; }
        a.adminRequest(API + (cancel ? "cancel" : "force"), req, data -> {
            JSONArray rows = data.optJSONArray("devices") == null ? new JSONArray() : data.optJSONArray("devices");
            int done = count(rows, cancel ? "cancelled" : "queued"), skipped = count(rows, "skipped");
            LinearLayout view = Ui.column(a); view.setPadding(dp(24), dp(20), dp(24), dp(4));
            view.addView(Ui.title(a, cancel ? "Cancel sent" : "Update requested", 20)); Ui.gap(view, 8);
            view.addView(Ui.text(a, cancel ? done + (done == 1 ? " request" : " requests") + " cancelled."
                    : done + (done == 1 ? " PC" : " PCs") + " asked to update" + (skipped > 0 ? ", " + skipped + " skipped" : "")
                    + ". They are not updated until they report the new version.", 15, Ui.INK));
            Ui.gap(view, 12); addResults(view, rows);
            new AlertDialog.Builder(a).setView(scroll(view)).setPositiveButton("OK", null).show();
            if (!all) ids.forEach(ticked::remove);
            if (body != null && body.isAttachedToWindow()) load();
        }, message -> new AlertDialog.Builder(a).setTitle(cancel ? "Could not cancel" : "Could not request the update")
                .setMessage(message).setPositiveButton("OK", null).show());
    }

    private void pause(JSONObject d, boolean pause) {
        String id = d.optString("device_id"), who = shop(d) + " (" + shortId(id) + ")";
        Runnable go = () -> {
            try {
                a.adminRequest(API + "devices/" + MainActivity.query(id) + "/pause", new JSONObject().put("paused", pause), data -> {
                    a.toast(shop(d) + ": updates " + (pause ? "paused." : "resumed."));
                    if (body != null && body.isAttachedToWindow()) load();
                }, message -> new AlertDialog.Builder(a).setTitle("Could not " + (pause ? "pause" : "resume") + " updates")
                        .setMessage(message).setPositiveButton("OK", null).show());
            } catch (Exception e) { a.toast("Could not prepare the request."); }
        };
        if (!pause) { go.run(); return; }
        new AlertDialog.Builder(a).setTitle("Pause updates for " + who + "?")
                .setMessage("It is offered nothing until resumed, and any update it already started finishes.")
                .setPositiveButton("Pause", (dlg, w) -> go.run()).setNegativeButton("Cancel", null).show();
    }

    private String scope(boolean all, List<String> ids) {
        if (all) {
            Set<String> shops = new TreeSet<>(); for (JSONObject d : devices) shops.add(shop(d));
            return "Every enrolled PC (" + devices.size() + " PCs in " + shops.size() + " shops). Each PC is checked on its own; the list says which would be asked and which skipped.";
        }
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            JSONObject d = find(id);
            names.add(d == null ? shortId(id) : shop(d) + " (" + pcName(d) + ")");
        }
        return (ids.size() == 1 ? "One PC: " : ids.size() + " ticked PCs: ") + String.join(", ", names);
    }

    private void addResults(LinearLayout parent, JSONArray rows) {
        if (rows.length() == 0) { parent.addView(Ui.text(a, "No PCs.", 14, Ui.MUTED)); return; }
        LinearLayout card = Ui.column(a); card.setBackground(Ui.outline(Ui.SURFACE, Ui.LINE, 1, 16, a)); card.setClipToOutline(true);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject r = rows.optJSONObject(i); if (r == null) continue;
            if (card.getChildCount() > 0) card.addView(Ui.divider(a, 14));
            String result = r.optString("result"), label = resultLabel(result);
            JSONObject d = find(r.optString("device_id"));
            LinearLayout line = Ui.column(a); line.setPadding(dp(14), dp(10), dp(14), dp(10));
            LinearLayout top = Ui.row(a);
            String shop = r.optString("shop").isEmpty() ? d == null ? "(no shop name)" : shop(d) : r.optString("shop");
            TextView name = Ui.title(a, shop, 15); top.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            boolean bad = result.equals("skipped");
            top.addView(Ui.text(a, label, 13, bad ? Ui.DANGER : result.startsWith("would") ? Ui.WARN : Ui.BRAND));
            line.addView(top);
            String why = r.optString("reason");
            if (why.isEmpty() && !r.optString("version").isEmpty()) why = "Reporter " + r.optString("version") + " (" + r.optString("release_id") + ")";
            line.addView(Ui.text(a, PeopleScreen.join(" · ", d == null ? shortId(r.optString("device_id")) : pcName(d), why), 13, Ui.MUTED));
            card.addView(line);
        }
        parent.addView(card);
    }
    private static String resultLabel(String result) {
        switch (result) {
            case "would_queue": return "Will be asked";
            case "queued": return "Requested";
            case "skipped": return "Skipped";
            case "would_cancel": return "Will be cancelled";
            case "cancelled": return "Cancelled";
            case "nothing_pending": return "Nothing pending";
            default: return result;
        }
    }
    private static int count(JSONArray rows, String result) {
        int n = 0; for (int i = 0; i < rows.length(); i++) if (rows.optJSONObject(i) != null && result.equals(rows.optJSONObject(i).optString("result"))) n++;
        return n;
    }

    private JSONObject find(String id) { for (JSONObject d : devices) if (d.optString("device_id").equals(id)) return d; return null; }
    private static String shop(JSONObject d) { return d.optString("shop").isEmpty() ? "(no shop name)" : d.optString("shop"); }
    private static String shortId(String id) { return id.length() > 8 ? id.substring(0, 8) : id; }
    /** The Windows name Reporter 1.9.94 sends, else the start of the PC id (as the website shows it). */
    private static String pcName(JSONObject d) { return d.optString("hostname").isEmpty() ? shortId(d.optString("device_id")) : d.optString("hostname"); }
    private static String installed(JSONObject d) { return d.optString("current_version", d.optString("reporter_version")); }
    private static JSONObject request(JSONObject d) {
        JSONObject f = d.optJSONObject("force_update");
        return f == null || f.optString("release_id").isEmpty() ? null : f;
    }
    private static boolean expired(JSONObject f) { Instant at = instant(f.optString("expires_at")); return at == null || !at.isAfter(Instant.now()); }
    private static boolean failed(JSONObject d) {
        return d.optString("phase").equals("failed") || (!d.optString("error").isEmpty() && !d.optString("phase").equals("deferred"));
    }
    private static boolean stale(String iso) { Instant at = instant(iso); return at == null || at.isBefore(Instant.now().minusSeconds(3 * 3600)); }
    /** Updaters before 1.1.0 (Reporter 1.9.85) cannot take a force request. */
    static boolean isOld(String version) {
        String[] parts = (version == null || version.isEmpty() ? "0" : version).split("\\.");
        int[] floor = {1, 1, 0};
        for (int i = 0; i < 3; i++) {
            int v; try { v = i < parts.length ? Integer.parseInt(parts[i].replaceAll("[^0-9].*", "")) : 0; } catch (NumberFormatException e) { v = 0; }
            if (v != floor[i]) return v < floor[i];
        }
        return false;
    }
    static String waitingText(String reason) {
        String lower = reason.toLowerCase(Locale.ROOT);
        for (String[] w : WAITING) if (lower.contains(w[0])) return w[1];
        return reason;
    }
    private static String capital(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    static Instant instant(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        try { return OffsetDateTime.parse(iso).toInstant(); } catch (Exception ignored) { }
        try { return LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC); } catch (Exception e) { return null; }
    }
    static String saTime(String iso) { Instant at = instant(iso); return at == null ? "" : SA_TIME.format(at); }
    static String ago(String iso) {
        Instant at = instant(iso); if (at == null) return "";
        long s = Math.max(0, Instant.now().getEpochSecond() - at.getEpochSecond());
        if (s < 90) return "just now";
        if (s < 3600) return (s / 60) + " min ago";
        if (s < 48 * 3600) return (s / 3600) + " h ago";
        return (s / 86400) + " days ago";
    }

    private ScrollView scroll(View view) { ScrollView s = new ScrollView(a); s.addView(view); return s; }
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
}
