package za.co.koenekt.app;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Stores → Slips: find a till slip and send it on (William, 25 Nov 2026).
 *
 * The list is one store's slips for one day, newest first, 100 at a time, optionally one till and a search (receipt
 * number, a name, an amount, an item: the shop PC searches the printed words). A slip opens drawn as the till
 * printed it (SlipView) with a send bar that stays put: WhatsApp or WhatsApp Business (whichever the phone has; with
 * both it asks once and remembers, changeable under More), Telegram, email, or any other app as a picture or a PDF.
 *
 * Every question goes to the shop PC through the relay (POST /api/stores/<id>/slips, Reporter 1.9.113+; see
 * docs/LINK-REPORTS-API.md in the Reporter repo), so each state has its own words: asking, nothing found, the PC is
 * offline, the PC's Reporter is too old, an error, each with "Try again". Nothing is kept on the phone except the
 * file being sent, which the export cache deletes within the hour.
 */
final class SlipsScreen {
    static final String WHATSAPP = "com.whatsapp", WHATSAPP_BUSINESS = "com.whatsapp.w4b", TELEGRAM = "org.telegram.messenger";
    private static final String PREF_WHATSAPP = "slip_whatsapp";
    private static final int MAX_TRIES = 40;
    private final StoresScreen s;
    private final MainActivity a;
    private final Handler timer = new Handler(Looper.getMainLooper());

    private String store = "", query = "", typed = "";
    /** null = today, whatever the date is when asked. */
    private LocalDate day;
    private int till;   // 0 = every till
    private final List<JSONObject> rows = new ArrayList<>();
    private JSONArray tills = new JSONArray();
    private int total = -1, run;
    /** "" (not asked yet), asking, ok, offline or error. */
    private String state = "", error = "";

    private JSONObject open, slip;   // the row that was tapped, and its answer
    private String slipState = "", slipError = "";
    private int slipRun, listScroll;

    /** What is on screen now; a redraw with nothing new leaves it alone (the search field keeps its keyboard). */
    private View built;
    private int version, builtVersion = -1;

    SlipsScreen(StoresScreen stores) { s = stores; a = stores.a; }
    private int dp(float n) { return a.dp(n); }

    // ------------------------------------------------------------------ which apps can take a slip
    static boolean installed(Context c, String pkg) {
        try { c.getPackageManager().getPackageInfo(pkg, 0); return true; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }
    /** WhatsApp and WhatsApp Business are two apps; a phone may have either, both or neither. */
    static List<String> whatsApps(Context c) {
        List<String> out = new ArrayList<>();
        if (installed(c, WHATSAPP)) out.add(WHATSAPP);
        if (installed(c, WHATSAPP_BUSINESS)) out.add(WHATSAPP_BUSINESS);
        return out;
    }
    static String appName(String pkg) {
        return WHATSAPP_BUSINESS.equals(pkg) ? "WhatsApp Business" : WHATSAPP.equals(pkg) ? "WhatsApp" : TELEGRAM.equals(pkg) ? "Telegram" : "";
    }
    /** The WhatsApp to send with: the only one installed, or the one chosen; "" when it must be asked or there is none. */
    static String chosenWhatsApp(MainActivity a) {
        List<String> have = whatsApps(a);
        if (have.size() == 1) return have.get(0);
        String saved = a.getPreferences(Context.MODE_PRIVATE).getString(PREF_WHATSAPP, "");
        return have.contains(saved) ? saved : "";
    }
    /** More → This phone → Send slips with (only shown when the phone has both). */
    static void chooseWhatsApp(MainActivity a, Runnable done) {
        List<String> have = whatsApps(a);
        if (have.size() < 2) { if (done != null) done.run(); return; }
        String[] names = {appName(have.get(0)), appName(have.get(1))};
        Ui.choose(a, "Send slips with", names, have.indexOf(chosenWhatsApp(a)), w -> {
            a.getPreferences(Context.MODE_PRIVATE).edit().putString(PREF_WHATSAPP, have.get(w)).apply();
            if (done != null) done.run();
        });
    }

    // ------------------------------------------------------------------ the list
    private void changed() { version++; if (s.showingSection("slips")) a.redraw(this::render); }

    void render() {
        if (open != null) { renderSlip(); return; }
        if (built != null && builtVersion == version && built.isAttachedToWindow()) return;
        a.setBar("Stores", false, Ui.iconButton(a, R.drawable.ic_refresh, "Refresh", () -> ask(0)));
        LinearLayout page = a.page(); page.setPadding(dp(16), dp(4), dp(16), dp(28));
        built = (View) page.getParent(); builtVersion = version;
        if (!s.ready(page)) { builtVersion = -1; return; }   // the store list is still coming: draw again when it is in
        if (s.store(store) == null) store = firstStore();

        HorizontalScrollView scroll = new HorizontalScrollView(a); scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = Ui.row(a); scroll.addView(chips); page.addView(scroll); Ui.gap(page, 12);
        JSONObject st = s.store(store);
        s.addChip(chips, st == null ? "Store" : st.optString("name", "Store"), this::pickStore);
        s.addChip(chips, dayLabel(), this::pickDay);
        s.addChip(chips, tillLabel(), this::pickTill);

        page.addView(Ui.label(a, "Find a slip")); Ui.gap(page, 8);
        LinearLayout search = Ui.row(a); search.setPadding(dp(14), 0, dp(6), 0);
        search.setBackground(Ui.outline(Ui.FIELD, Ui.BORDER_STRONG, 1, 2, a));
        search.addView(Ui.icon(a, R.drawable.ic_search, Ui.MUTED, 20));
        EditText q = new EditText(a); q.setHint("Receipt no, name, amount or item"); q.setBackground(null); q.setSingleLine(true);
        q.setTextSize(16); q.setTypeface(Ui.SANS); q.setTextColor(Ui.INK); q.setHintTextColor(Ui.MUTED); q.setPadding(dp(10), 0, 0, 0);
        q.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH); q.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        q.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        q.setText(typed); q.setSelection(q.getText().length());
        q.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence t, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence t, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable t) { typed = t.toString(); }
        });
        // Each search is a trip to the shop PC, so it runs on the keyboard's Search key, not on every letter.
        q.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH && action != EditorInfo.IME_ACTION_DONE && event == null) return false;
            // the keyboard goes away, or it would cover the slips that were found
            ((android.view.inputmethod.InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(), 0);
            v.clearFocus();
            query = typed.trim(); ask(0); return true;
        });
        search.addView(q, new LinearLayout.LayoutParams(0, dp(50), 1));
        page.addView(search); Ui.gap(page, 14);

        if (st == null) return;
        if (state.isEmpty()) { ask(0); return; }
        if (!query.isEmpty()) {
            LinearLayout found = Ui.row(a);
            found.addView(Ui.text(a, "Searching for “" + query + "”", 14, Ui.INK), new LinearLayout.LayoutParams(0, -2, 1));
            found.addView(Ui.quiet(a, "Clear", () -> { query = ""; typed = ""; ask(0); }), new LinearLayout.LayoutParams(-2, dp(48)));
            page.addView(found); Ui.gap(page, 4);
        }
        for (int i = 0; i < tills.length(); i++) {   // a till that could not be read is said, the others still show
            JSONObject t = tills.optJSONObject(i);
            if (t != null && !t.isNull("ok") && !t.optBoolean("ok") && !t.optString("error").isEmpty()) {
                page.addView(Ui.notice(a, t.optString("name", "Till " + t.optInt("no")) + ": " + t.optString("error"), false)); Ui.gap(page, 10);
            }
        }
        if (state.equals("asking") && rows.isEmpty()) {
            page.addView(Ui.loading(a));
            page.addView(centred("Asking the shop PC. A busy till can take a little while."));
            return;
        }
        if (state.equals("offline")) { problem(page, st.optString("name", "This store") + "'s shop PC is offline",
                "Slips are read from the tills through the shop PC, and it is not answering. The slips are safe on the tills. They can be fetched again as soon as the PC is back on."); return; }
        if (state.equals("error")) {
            boolean old = error.contains("cannot answer");
            problem(page, old ? "This shop's PC needs a newer Koenekt Reporter" : "The slips could not be fetched",
                    old ? "Slips need Koenekt Reporter 1.9.113 or later on the shop PC. Ask Koenekt to update it." : error);
            return;
        }
        if (rows.isEmpty()) {
            page.addView(centred(query.isEmpty() ? "No slips on " + dayLabel().toLowerCase(Locale.ROOT) + (till > 0 ? " for " + tillLabel() : "") + ". Try another day" + (till > 0 ? " or all tills." : ".")
                    : "No slip matches “" + query + "”. Try another day, all tills, or fewer words."));
            return;
        }
        page.addView(Ui.label(a, total + (total == 1 ? " slip" : " slips") + " · newest first")); Ui.gap(page, 8);
        LinearLayout card = Ui.column(a); card.setBackground(Ui.outline(Ui.SURFACE, Ui.LINE, 1, 20, a)); card.setClipToOutline(true);
        for (int i = 0; i < rows.size(); i++) {
            JSONObject r = rows.get(i);
            if (i > 0) card.addView(Ui.divider(a, 16));
            TextView time = Ui.text(a, StoreMath.clock(r.optString("when")), 13, Ui.MUTED); time.setTypeface(Ui.MONO);
            LinearLayout row = Ui.listRow(a, time, "Receipt " + r.optString("rcpt"), sub(r), amount(r), () -> openSlip(r));
            card.addView(row);
        }
        page.addView(card);
        if (rows.size() < total) {
            Ui.gap(page, 12);
            if (state.equals("asking")) page.addView(Ui.loading(a));
            else page.addView(Ui.secondary(a, "Show the next " + Math.min(100, total - rows.size()), () -> ask(rows.size())), Ui.fill(a, 52));
        }
        if (listScroll > 0) { final int y = listScroll; listScroll = 0; built.post(() -> built.scrollTo(0, y)); }
    }

    private TextView centred(String text) {
        TextView t = Ui.text(a, text, 15, Ui.MUTED); t.setGravity(Gravity.CENTER); t.setPadding(dp(16), dp(20), dp(16), dp(20)); return t;
    }
    private void problem(LinearLayout page, String title, String text) {
        LinearLayout card = Ui.card(a);
        card.addView(Ui.title(a, title, 16)); Ui.gap(card, 8);
        card.addView(Ui.text(a, text, 14, Ui.MUTED)); Ui.gap(card, 14);
        card.addView(Ui.secondary(a, "Try again", () -> ask(0)), new LinearLayout.LayoutParams(-2, dp(48)));
        page.addView(card);
    }

    private String firstStore() {
        if (s.dash.scope.startsWith("store:") && s.store(s.dash.scope.substring(6)) != null) return s.dash.scope.substring(6);
        for (JSONObject st : s.stores) if (s.dash.shows(st.optString("store"))) return st.optString("store");
        return s.stores.isEmpty() ? "" : s.stores.get(0).optString("store");
    }
    private LocalDate theDay() { return day == null ? PastData.today() : day; }
    private String dayLabel() {
        LocalDate d = theDay(), today = PastData.today();
        return d.equals(today) ? "Today" : d.equals(today.minusDays(1)) ? "Yesterday" : HistoryMath.nice(d.toString(), today);
    }
    private String tillLabel() {
        if (till == 0) return "All tills";
        for (int i = 0; i < tills.length(); i++) { JSONObject t = tills.optJSONObject(i); if (t != null && t.optInt("no") == till) return t.optString("name", "Till " + till); }
        return "Till " + till;
    }
    private void pickStore() {
        List<String> ids = new ArrayList<>(), names = new ArrayList<>();
        for (JSONObject st : s.stores) { ids.add(st.optString("store")); names.add(st.optString("name")); }
        Ui.choose(a, "Store", names.toArray(new String[0]), ids.indexOf(store), w -> { store = ids.get(w); till = 0; tills = new JSONArray(); ask(0); });
    }
    private void pickDay() {
        Ui.choose(a, "Day", new String[]{"Today", "Yesterday", "Another day…"}, day == null ? 0 : day.equals(PastData.today().minusDays(1)) ? 1 : 2, w -> {
            if (w == 0) { day = null; ask(0); }
            else if (w == 1) { day = PastData.today().minusDays(1); ask(0); }
            else {
                LocalDate d = theDay();
                android.app.DatePickerDialog dlg = new android.app.DatePickerDialog(a, Ui.dark ? R.style.KoenektDialog_Dark : R.style.KoenektDialog,
                        (view, y, m, dd) -> { LocalDate got = LocalDate.of(y, m + 1, dd); day = got.equals(PastData.today()) ? null : got; ask(0); },
                        d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth());
                dlg.getDatePicker().setMaxDate(System.currentTimeMillis());
                dlg.show();
            }
        });
    }
    private void pickTill() {
        List<Integer> nos = new ArrayList<>(); List<String> names = new ArrayList<>();
        nos.add(0); names.add("All tills");
        for (int i = 0; i < tills.length(); i++) { JSONObject t = tills.optJSONObject(i); if (t != null) { nos.add(t.optInt("no")); names.add(t.optString("name", "Till " + t.optInt("no"))); } }
        Ui.choose(a, "Till", names.toArray(new String[0]), Math.max(0, nos.indexOf(till)), w -> { till = nos.get(w); ask(0); });
    }

    /** "Void · Till 1 · EXAMPLE TRADING", or the printed line a search found. */
    private String sub(JSONObject r) {
        if (!r.optString("found").isEmpty()) return r.optString("found");
        String kind = r.optString("kind"), k = kind.equals("SALE") ? "" : kind.equals("ACCOUNT") ? "On account" : kind.isEmpty() ? "" : kind.charAt(0) + kind.substring(1).toLowerCase(Locale.ROOT);
        StringBuilder b = new StringBuilder(k);
        for (String part : new String[]{r.optString("till_name"), r.optString("detail")})
            if (!part.isEmpty()) { if (b.length() > 0) b.append(" · "); b.append(part); }
        return b.toString();
    }
    static String amount(JSONObject r) {
        if (r.isNull("value")) return "";
        double v = r.optDouble("value", Double.NaN);
        if (Double.isNaN(v)) return "";
        String n = String.format(Locale.ROOT, "%,.2f", Math.abs(v)).replace(',', ' ');
        return (v < 0 ? "−R " : "R ") + n;
    }

    private JSONObject params(int offset) throws org.json.JSONException {
        String d = theDay().toString();
        JSONObject p = new JSONObject().put("from", d).put("to", d);
        if (till > 0) p.put("till", till);
        if (!query.isEmpty()) p.put("q", query);
        if (offset > 0) p.put("offset", offset);
        return p;
    }
    /** Asks for the list from `offset` (0 starts again). */
    private void ask(int offset) {
        if (store.isEmpty()) return;
        int me = ++run; state = "asking"; error = "";
        if (offset == 0) { rows.clear(); total = -1; }
        changed();
        poll(me, offset, 0);
    }
    private void poll(int me, int offset, int tries) {
        JSONObject body;
        try { body = new JSONObject().put("params", params(offset)); } catch (org.json.JSONException e) { return; }
        a.linkRequest("POST", "/api/stores/" + store + "/slips", body, data -> {
            if (me != run) return;
            String status = data.optString("status");
            if (status.equals("pending")) {
                if (tries >= MAX_TRIES) { fail("The shop PC did not answer in time."); return; }
                timer.postDelayed(() -> { if (me == run) poll(me, offset, tries + 1); }, Math.max(2, data.optInt("retry_after", 3)) * 1000L);
                return;
            }
            if (status.equals("offline")) { state = "offline"; changed(); return; }
            JSONObject r = data.optJSONObject("result");
            if (!status.equals("ok") || r == null) { fail(data.optString("error", "The shop PC could not read its slips.")); return; }
            JSONArray list = r.optJSONArray("slips");
            if (list != null) for (int i = 0; i < list.length(); i++) if (list.optJSONObject(i) != null) rows.add(list.optJSONObject(i));
            total = Math.max(rows.size(), r.optInt("total", rows.size()));
            if (r.optJSONArray("tills") != null) tills = r.optJSONArray("tills");
            state = "ok"; changed();
        }, message -> { if (me == run) fail(message); });
    }
    private void fail(String message) { state = "error"; error = message; changed(); }

    // ------------------------------------------------------------------ one slip
    private void openSlip(JSONObject row) {
        if (built instanceof ScrollView) listScroll = built.getScrollY();
        open = row; slip = null; slipError = ""; askSlip();
    }
    private void closeSlip() { open = null; slip = null; slipRun++; version++; a.redraw(this::render); }
    private void askSlip() {
        int me = ++slipRun; slipState = "asking"; slipError = ""; changed();
        pollSlip(me, 0);
    }
    private void pollSlip(int me, int tries) {
        JSONObject body;
        String d = open.optString("when").length() >= 10 ? open.optString("when").substring(0, 10) : theDay().toString();
        try { body = new JSONObject().put("params", new JSONObject().put("from", d).put("to", d).put("slip", open.optString("id"))); }
        catch (org.json.JSONException e) { return; }
        a.linkRequest("POST", "/api/stores/" + store + "/slips", body, data -> {
            if (me != slipRun || open == null) return;
            String status = data.optString("status");
            if (status.equals("pending")) {
                if (tries >= MAX_TRIES) { slipState = "error"; slipError = "The shop PC did not answer in time."; changed(); return; }
                timer.postDelayed(() -> { if (me == slipRun) pollSlip(me, tries + 1); }, Math.max(2, data.optInt("retry_after", 3)) * 1000L);
                return;
            }
            JSONObject r = data.optJSONObject("result"), got = r == null ? null : r.optJSONObject("slip");
            if (status.equals("offline")) { slipState = "error"; slipError = "The shop PC is offline, so the slip cannot be fetched now."; }
            else if (!status.equals("ok") || got == null) { slipState = "error"; slipError = data.optString("error", "The shop PC could not read that slip."); }
            else { slip = got; slipState = "ok"; }
            changed();
        }, message -> { if (me == slipRun && open != null) { slipState = "error"; slipError = message; changed(); } });
    }

    private String storeName() { JSONObject st = s.store(store); return st == null ? "" : st.optString("name"); }
    /** "25 Nov, 11:48" */
    private String when(JSONObject r) {
        String w = r.optString("when");
        if (w.length() < 16) return "";
        return HistoryMath.nice(w.substring(0, 10), PastData.today()) + ", " + w.substring(11, 16);
    }

    private void renderSlip() {
        if (built != null && builtVersion == version && built.isAttachedToWindow()) return;
        a.setBar("Receipt " + open.optString("rcpt"), true);
        a.onBack(this::closeSlip);
        LinearLayout screen = Ui.column(a);
        built = screen; builtVersion = version;
        StringBuilder where = new StringBuilder(storeName());
        for (String part : new String[]{open.optString("till_name"), when(open)}) if (!part.isEmpty()) where.append(" · ").append(part);
        TextView head = Ui.text(a, where, 13, Ui.MUTED); head.setPadding(dp(20), 0, dp(20), dp(10)); screen.addView(head);
        if (!slipState.equals("ok") || slip == null) {
            LinearLayout box = Ui.column(a); box.setPadding(dp(16), dp(8), dp(16), dp(16));
            if (slipState.equals("asking")) { box.addView(Ui.loading(a)); box.addView(centred("Asking the shop PC for the slip.")); }
            else {
                box.addView(Ui.notice(a, slipError.contains("cannot answer") ? "This shop's PC needs Koenekt Reporter 1.9.113 or later before it can show slips." : slipError, true));
                Ui.gap(box, 12); box.addView(Ui.secondary(a, "Try again", this::askSlip), new LinearLayout.LayoutParams(-2, dp(48)));
            }
            screen.addView(box, new LinearLayout.LayoutParams(-1, 0, 1));
            a.mount(screen);
            return;
        }
        List<SlipView.Line> lines = SlipView.parse(slip.optJSONArray("lines"));
        int cols = slip.optInt("cols", 40);
        int width = Math.min(a.getResources().getDisplayMetrics().widthPixels - dp(32), dp(420));
        ScrollView down = new ScrollView(a);
        HorizontalScrollView across = new HorizontalScrollView(a); across.setHorizontalScrollBarEnabled(false);
        LinearLayout holder = Ui.column(a); holder.setPadding(dp(16), dp(2), dp(16), dp(16));
        SlipView paper = new SlipView(a, lines, cols, width);
        holder.addView(paper, new LinearLayout.LayoutParams(-2, -2));
        TextView hint = Ui.text(a, "Tap the slip to make it bigger or smaller.", 12, Ui.MUTED); hint.setPadding(0, dp(8), 0, 0); holder.addView(hint);
        across.addView(holder); down.addView(across);
        screen.addView(down, new LinearLayout.LayoutParams(-1, 0, 1));
        screen.addView(Ui.divider(a, 0));
        screen.addView(sendBar(lines, cols));
        a.mount(screen);
    }

    /** One wide button for WhatsApp (or "Share slip" when the phone has none), then Telegram, Email and Other. */
    private View sendBar(List<SlipView.Line> lines, int cols) {
        LinearLayout bar = Ui.column(a); bar.setPadding(dp(16), dp(10), dp(16), dp(10));
        List<String> wa = whatsApps(a);
        if (wa.isEmpty()) bar.addView(Ui.primary(a, "Share slip", () -> other(lines, cols)), Ui.fill(a, 52));
        else {
            String pick = chosenWhatsApp(a);
            bar.addView(appButton("Send with " + (pick.isEmpty() ? "WhatsApp" : appName(pick)), pick.isEmpty() ? wa.get(0) : pick, true, () -> {
                String now = chosenWhatsApp(a);
                if (now.isEmpty()) chooseWhatsApp(a, () -> { version++; a.redraw(this::render); send(lines, cols, "png", chosenWhatsApp(a)); });
                else send(lines, cols, "png", now);
            }), Ui.fill(a, 52));
        }
        Ui.gap(bar, 8);
        LinearLayout row = Ui.row(a);
        if (installed(a, TELEGRAM)) addTo(row, appButton("Telegram", TELEGRAM, false, () -> send(lines, cols, "png", TELEGRAM)));
        addTo(row, Ui.secondary(a, "Email", () -> send(lines, cols, "pdf", mailApp())));
        if (!wa.isEmpty()) addTo(row, Ui.secondary(a, "Other", () -> other(lines, cols)));
        bar.addView(row);
        return bar;
    }
    private void addTo(LinearLayout row, View button) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1);
        if (row.getChildCount() > 0) p.leftMargin = dp(8);
        if (button instanceof TextView) { ((TextView) button).setMinHeight(dp(48)); button.setPadding(dp(6), 0, dp(6), 0); }
        row.addView(button, p);
    }
    /** A button with the installed app's own icon beside its name (so WhatsApp Business shows as itself). */
    private View appButton(String label, String pkg, boolean primary, Runnable action) {
        LinearLayout b = Ui.row(a); b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.pressable(primary ? Ui.shape(Ui.LIME, 2, a) : Ui.outline(android.graphics.Color.TRANSPARENT, Ui.LINE, 1, 2, a), 2, a));
        try {
            Drawable icon = a.getPackageManager().getApplicationIcon(pkg);
            ImageView i = new ImageView(a); i.setImageDrawable(icon); i.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(24), dp(24)); ip.rightMargin = dp(8); b.addView(i, ip);
        } catch (PackageManager.NameNotFoundException ignored) { /* the name alone */ }
        TextView t = Ui.text(a, label, 14, primary ? Ui.ON_ACCENT : Ui.INK); Ui.mono(t, true); t.setLetterSpacing(0.05f); t.setSingleLine(true);
        b.addView(t);
        b.setContentDescription(label); b.setClickable(true); b.setFocusable(true); b.setOnClickListener(v -> action.run());
        return b;
    }
    private void other(List<SlipView.Line> lines, int cols) {
        Ui.choose(a, "Share the slip as", new String[]{"A picture", "A PDF"}, -1, w -> send(lines, cols, w == 0 ? "png" : "pdf", ""));
    }
    /** The phone's own mail app, or "" (then the share sheet opens). */
    private String mailApp() {
        ResolveInfo r = a.getPackageManager().resolveActivity(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")), PackageManager.MATCH_DEFAULT_ONLY);
        String pkg = r == null || r.activityInfo == null ? "" : r.activityInfo.packageName;
        return pkg == null || pkg.equals("android") || pkg.contains("resolver") ? "" : pkg;
    }

    /** Makes the picture or PDF off the main thread and hands it to `pkg`, or to the share sheet when pkg is "". */
    private void send(List<SlipView.Line> lines, int cols, String fmt, String pkg) {
        final String title = "Receipt " + open.optString("rcpt") + (storeName().isEmpty() ? "" : ", " + storeName()) + (when(open).isEmpty() ? "" : ", " + when(open));
        final String name = "Slip-" + open.optString("rcpt").replaceAll("[^A-Za-z0-9]", "") + "-" + storeName().replaceAll("[^A-Za-z0-9]", "")
                + "-" + (open.optString("when").length() >= 10 ? open.optString("when").substring(0, 10) : "") + "." + fmt;
        android.widget.Toast.makeText(a, fmt.equals("pdf") ? "Making the PDF…" : "Making the picture…", android.widget.Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                byte[] bytes = fmt.equals("pdf") ? SlipView.pdf(lines, cols) : SlipView.png(lines, cols, 864);
                File d = ExportProvider.dir(a);
                File[] old = d.listFiles();
                if (old != null) for (File o : old) if (o.lastModified() < System.currentTimeMillis() - 3_600_000L) //noinspection ResultOfMethodCallIgnored
                    o.delete();
                File out = new File(d, name);
                try (FileOutputStream w = new FileOutputStream(out)) { w.write(bytes); }
                Uri uri = ExportProvider.uriFor(a, out);
                timer.post(() -> {
                    Intent send = new Intent(Intent.ACTION_SEND).setType(ExportProvider.mime(name))
                            .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, title).putExtra(Intent.EXTRA_TEXT, title + " (reprint)")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    send.setClipData(ClipData.newRawUri(name, uri));
                    try {
                        if (pkg == null || pkg.isEmpty()) a.startActivity(Intent.createChooser(send, "Send the slip"));
                        else try { a.startActivity(new Intent(send).setPackage(pkg)); }
                        catch (ActivityNotFoundException gone) { a.startActivity(Intent.createChooser(send, "Send the slip")); }
                    } catch (ActivityNotFoundException e) { a.toast("No app on this phone can take the slip."); }
                });
            } catch (Exception e) {
                timer.post(() -> a.toast("Could not make the file: " + e.getMessage()));
            }
        }).start();
    }
}
