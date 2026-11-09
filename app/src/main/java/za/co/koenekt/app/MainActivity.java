package za.co.koenekt.app;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.BiometricManager;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.print.PrintManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Native navigation, sign-in and everyday screens; the remaining product workflows run in a restricted WebView. */
public final class MainActivity extends Activity {
    static final int TAB_HOME = 0, TAB_PEOPLE = 1, TAB_LIVE = 2, TAB_ACCOUNT = 3, TAB_UPDATES = 4;   // Updates is opened from Home, not the bottom bar
    static final int TAB_STORES = 5;
    private static final int PICK_FILE = 20, SAVE_FILE = 21, DEVICE_UNLOCK = 22, CONFIRM_DEVICE = 23, MAX_EXPORT = 20 * 1024 * 1024;
    /** Preference (this phone only; backups are off): Account → Allow screenshots on this phone. */
    private static final String ALLOW_SCREENSHOTS = "allow_screenshots", APPEARANCE = "appearance";
    /** System (follow the phone), light or dark; this phone only. */
    private String appearance = Ui.SYSTEM, lockMessage = "";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    /** My stores asks several shop PCs at once, and each answer may take up to 20 s. */
    private final ExecutorService linkWorker = Executors.newFixedThreadPool(4);
    /** My stores (Koenekt Link relay) session: a bearer token, never a password. */
    private String linkToken = "", linkUser = "", linkRole = "";
    private StoresScreen storesScreen;
    private final Set<String> caps = new HashSet<>();
    private String workspace = AppPolicy.DEFAULT_WORKSPACE, displayName = "", role = "", roleLabel = "";
    private String notice = "", currentTitle = "Koenekt", exportNonce = "";
    private boolean operator = false, peopleSession = false, inWeb = false, busy = false;
    private boolean mustChangePassword;   // after an administrator's password reset
    private int epoch = 0, tab = TAB_HOME;
    private LinearLayout root, appBar, barActions, nav;
    private View navLine;
    private ImageView backButton;
    private FrameLayout content;
    private TextView barTitle;
    private ProgressBar progress;
    private WebView web;
    private ValueCallback<Uri[]> uploadCallback;
    private File pendingExport;
    private String mobileScript;
    private SessionVault vault;
    private FrameLayout shell;
    private View shield;
    private boolean locked, authInProgress, foreground, setupInProgress, pinPromptActive, externalTask;
    private CancellationSignal authentication;
    private Runnable afterUnlock, backAction;
    private int authAttempt, pinAttempt, pendingAuthAttempt;
    /** Phone-lock check for a setting (Allow screenshots); while it is open, leaving the app does not lock it at once. */
    private Runnable afterConfirm;
    private boolean confirmPinActive, leftDuringConfirm;
    private CancellationSignal confirmation;
    private Intent pendingFileResult;
    private int pendingFileRequest, pendingFileStatus;
    private List<Company> companies;
    private PeopleScreen peopleScreen;
    private LiveScreen liveScreen;
    private UpdatesScreen updatesScreen;
    private Flow flow;

    /** A full-screen task (add or edit a person) that keeps its answers across locking and unlocking. */
    interface Flow { void show(); }

    /** A People company/site the signed-in account may use. */
    static final class Company {
        final String name, url;
        Company(String name, String url) { this.name = name; this.url = url; }
    }

    @Override public void onCreate(Bundle state) {
        appearance = getPreferences(MODE_PRIVATE).getString(APPEARANCE, Ui.SYSTEM);
        boolean dark = Ui.wantsDark(this, appearance);
        setTheme(dark ? R.style.AppTheme_Dark : R.style.AppTheme);
        super.onCreate(state);
        Ui.apply(this, dark);
        vault = new SessionVault(this);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
        File[] stale = getCacheDir().listFiles((dir, name) -> name.startsWith("export-"));
        if (stale != null) for (File file : stale) file.delete();
        applyScreenProtection();
        workspace = getPreferences(MODE_PRIVATE).getString("workspace", AppPolicy.DEFAULT_WORKSPACE);
        // Debug builds given -PdevOrigin talk to a local People console; release builds never do.
        if (BuildConfig.DEBUG && !BuildConfig.DEV_ORIGIN.isEmpty()) { AppPolicy.allowDevOrigin(BuildConfig.DEV_ORIGIN); workspace = BuildConfig.DEV_ORIGIN; }
        try { workspace = AppPolicy.workspace(workspace); } catch (Exception e) { workspace = AppPolicy.DEFAULT_WORKSPACE; }
        if (BuildConfig.DEBUG && !BuildConfig.DEV_LINK.isEmpty()) AppPolicy.allowDevLink(BuildConfig.DEV_LINK);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().removeAllCookies(ignored -> buildShell());
    }

    int dp(float n) { return Ui.dp(this, n); }

    private void buildShell() {
        root = Ui.column(this); root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            root.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        appBar = Ui.row(this); appBar.setPadding(dp(8), dp(4), dp(8), dp(4)); appBar.setMinimumHeight(dp(64));
        backButton = Ui.iconButton(this, R.drawable.ic_back, "Back", this::handleBack); appBar.addView(backButton);
        barTitle = Ui.title(this, "", 24); barTitle.setSingleLine(true); barTitle.setEllipsize(TextUtils.TruncateAt.END);
        appBar.addView(barTitle, new LinearLayout.LayoutParams(0, -2, 1));
        barActions = Ui.row(this); appBar.addView(barActions);
        root.addView(appBar); progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100); progress.setVisibility(View.GONE); progress.setProgressTintList(ColorStateList.valueOf(Ui.LIME));
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        content = new FrameLayout(this); content.setBackgroundColor(Ui.BG); root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        navLine = new View(this); navLine.setBackgroundColor(Ui.LINE);
        root.addView(navLine, new LinearLayout.LayoutParams(-1, Math.max(1, dp(0.5f))));
        nav = Ui.row(this); nav.setBackgroundColor(Ui.BG); nav.setPadding(0, 0, 0, dp(8));
        root.addView(nav, new LinearLayout.LayoutParams(-1, -2));
        shell = new FrameLayout(this); shell.addView(root); setContentView(shell);
        createWebView(); loginScreen(""); root.requestApplyInsets();
        if (vault.exists()) showLock("Unlock your saved sign-in.");
        if (getIntent().getData() != null) handleSetupLink(getIntent().getData());
        else if (locked) root.post(this::requestUnlock);
    }

    /** App bar: optional back arrow, large title and trailing actions. A null title hides the bar. */
    void setBar(String title, boolean back, View... actions) {
        appBar.setVisibility(title == null ? View.GONE : View.VISIBLE);
        backButton.setVisibility(back ? View.VISIBLE : View.GONE);
        barTitle.setPadding(back ? dp(4) : dp(12), 0, dp(4), 0); barTitle.setText(title == null ? "" : title);
        barActions.removeAllViews(); for (View action : actions) barActions.addView(action);
        backAction = null;
    }
    /** Overrides the next Back press for the current screen (cleared by setBar). */
    void onBack(Runnable action) { backAction = action; }

    private boolean hasTab(int which) {
        if (which == TAB_PEOPLE) return peopleSession && caps.contains("people_read");
        if (which == TAB_LIVE) return peopleSession && caps.contains("dashboard");
        if (which == TAB_UPDATES) return operator;
        if (which == TAB_STORES) return !linkToken.isEmpty();
        return true;
    }
    /** Any of the three services: People, management (licence site) or My stores. */
    private boolean signedIn() { return peopleSession || operator || !linkToken.isEmpty(); }
    boolean showingStores() { return tab == TAB_STORES && !locked && !inWeb && flow == null && storesScreen != null; }
    String linkRole() { return linkRole; }
    String linkUser() { return linkUser; }
    private void showNav(boolean visible) {
        nav.setVisibility(visible ? View.VISIBLE : View.GONE); navLine.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) return;
        nav.removeAllViews();
        int[] tabs = {TAB_HOME, TAB_STORES, TAB_PEOPLE, TAB_LIVE, TAB_ACCOUNT};
        int[] icons = {R.drawable.ic_home, R.drawable.ic_store, R.drawable.ic_people, R.drawable.ic_live, R.drawable.ic_account};
        String[] names = {"Home", "Stores", "People", "Who's in", "Account"};
        for (int i = 0; i < tabs.length; i++) {
            if (!hasTab(tabs[i])) continue;
            boolean selected = tabs[i] == tab;
            // The site's look: a 2dp accent bar over the selected tab, mono capitals underneath.
            LinearLayout cell = Ui.column(this); cell.setGravity(Gravity.CENTER_HORIZONTAL);
            View bar = new View(this); bar.setBackgroundColor(selected ? Ui.LIME : Color.TRANSPARENT);
            cell.addView(bar, new LinearLayout.LayoutParams(-1, dp(2)));
            ImageView icon = Ui.icon(this, icons[i], selected ? Ui.BRAND : Ui.MUTED, 24);
            ((LinearLayout.LayoutParams) icon.getLayoutParams()).topMargin = dp(8);
            cell.addView(icon);
            TextView label = Ui.text(this, names[i], 10, selected ? Ui.BRAND : Ui.MUTED); Ui.mono(label, selected);
            label.setLetterSpacing(0.04f); label.setMaxLines(1); label.setPadding(0, dp(5), 0, 0); label.setGravity(Gravity.CENTER);
            cell.addView(label);
            cell.setContentDescription(names[i]); cell.setClickable(true); cell.setFocusable(true);
            cell.setBackground(Ui.pressable(Ui.shape(Color.TRANSPARENT, 0, this), 0, this));
            final int target = tabs[i]; cell.setOnClickListener(v -> showTab(target));
            nav.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
        }
    }

    void showTab(int which) {
        if (busy || locked) return;
        if (!signedIn()) { loginScreen(notice); return; }
        if (peopleSession && mustChangePassword) { startPasswordChange(true); return; }
        if (!hasTab(which)) which = TAB_HOME;
        leaveWeb(); flow = null; tab = which;
        if (which == TAB_STORES) { if (storesScreen == null) storesScreen = new StoresScreen(this); showNav(true); storesScreen.show(); }
        else if (which == TAB_PEOPLE) { if (peopleScreen == null) peopleScreen = new PeopleScreen(this); showNav(true); peopleScreen.show(); }
        else if (which == TAB_LIVE) { if (liveScreen == null) liveScreen = new LiveScreen(this); showNav(true); liveScreen.show(); }
        else if (which == TAB_UPDATES) { if (updatesScreen == null) updatesScreen = new UpdatesScreen(this); showNav(false); updatesScreen.show(); }
        else if (which == TAB_ACCOUNT) accountTab();
        else home();
    }
    private void leaveWeb() { if (inWeb) { web.stopLoading(); web.onPause(); inWeb = false; } }

    void startAddEmployee() {
        if (busy || locked || !peopleSession || !caps.contains("people_hire")) return;
        leaveWeb(); showNav(false); flow = new AddEmployeeFlow(this); flow.show();
    }
    void editPerson(JSONObject person, Company company, JSONArray shifts) {
        if (busy || locked || !peopleSession || !caps.contains("people_edit")) return;
        leaveWeb(); showNav(false); flow = new EditPersonFlow(this, person, company, shifts); flow.show();
    }
    void finishFlow(boolean changed) {
        flow = null; if (changed && peopleScreen != null) peopleScreen.invalidate(); showTab(TAB_PEOPLE);
    }
    void leaveFlow(int which) { flow = null; showTab(which); }
    void startPasswordChange(boolean forced) {
        if (busy || locked || !peopleSession) return;
        leaveWeb(); showNav(false); flow = new PasswordFlow(this, forced); flow.show();
    }
    /** The server issued a new session cookie; save it again so the next unlock doesn't use the old one. */
    void passwordChanged() {
        mustChangePassword = false; flow = null; toast("Your password is changed.");
        if (vault.exists()) rememberSignIn(); else showTab(tab);
    }
    void accountDeleted() { signOut(); toast("Your account is deleted. The activity log keeps what it did."); }

    void mount(View view) {
        // A keyboard left open by the previous screen would cover the new one.
        View focused = getCurrentFocus();
        if (focused != null) ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focused.getWindowToken(), 0);
        content.removeAllViews();
        if (view.getParent() instanceof ViewGroup parent) parent.removeView(view);
        content.addView(view, new FrameLayout.LayoutParams(-1, -1));
    }
    /** Scrolling page with comfortable side margins. */
    LinearLayout page() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout l = Ui.column(this); l.setPadding(dp(20), dp(8), dp(20), dp(28)); scroll.addView(l); mount(scroll); return l;
    }

    /** Redraws the current page (new figures arrived) without jumping back to the top. */
    void redraw(Runnable render) {
        int y = content.getChildCount() > 0 && content.getChildAt(0) instanceof ScrollView s ? s.getScrollY() : 0;
        render.run();
        if (y > 0 && content.getChildCount() > 0 && content.getChildAt(0) instanceof ScrollView s) s.post(() -> s.scrollTo(0, y));
    }

    boolean can(String cap) { return caps.contains(cap); }
    String displayName() { return displayName; }

    /** Companies/sites the account can use, from the same endpoints as the People website. Cached until sign-out. */
    void loadCompanies(Consumer<List<Company>> done, Consumer<String> failed) {
        if (companies != null) { done.accept(companies); return; }
        request("/api/companies", null, data -> {
            List<Company> list = new ArrayList<>(); JSONArray rows = data.optJSONArray("companies");
            if (rows != null) for (int i = 0; i < rows.length(); i++) {
                JSONObject c = rows.optJSONObject(i); if (c == null) continue;
                String url = c.optString("frappe_url").replaceAll("/+$", "");
                if (!url.isEmpty()) list.add(new Company(c.optString("name", c.optString("frappe_company", url)), url));
            }
            if (!list.isEmpty()) { companies = list; done.accept(list); return; }
            request("/api/sites", null, sites -> {
                JSONArray s = sites.optJSONArray("sites");
                if (s != null) for (int i = 0; i < s.length(); i++) {
                    JSONObject c = s.optJSONObject(i); if (c == null) continue;
                    String url = c.optString("frappe_url", c.optString("url")).replaceAll("/+$", "");
                    if (!url.isEmpty()) list.add(new Company(c.optString("company", c.optString("site", url)), url));
                }
                companies = list; done.accept(list);
            }, failed);
        }, failed);
    }
    int companyIndex(List<Company> list) {
        String saved = getPreferences(MODE_PRIVATE).getString("company_url", "");
        for (int i = 0; i < list.size(); i++) if (list.get(i).url.equals(saved)) return i;
        return 0;
    }
    void rememberCompany(Company company) { getPreferences(MODE_PRIVATE).edit().putString("company_url", company.url).apply(); }

    interface Done { void accept(JSONObject data); }
    /** People API call on the worker; results are dropped if the session changed or the app locked meanwhile. */
    void request(String path, JSONObject body, Done ok, Consumer<String> failed) { request("POST", path, body, ok, failed); }
    /** As above with an explicit method for a body (the website's PUT endpoints). */
    void request(String method, String path, JSONObject body, Done ok, Consumer<String> failed) { call(false, method, path, body, ok, failed); }
    /** Licence-site (management) API call, e.g. the Reporter updates routes; a 401 ends only the management session. */
    void adminRequest(String path, JSONObject body, Done ok, Consumer<String> failed) { call(true, "POST", path, body, ok, failed); }
    private void call(boolean management, String method, String path, JSONObject body, Done ok, Consumer<String> failed) {
        int attempt = epoch; String url = (management ? AppPolicy.ADMIN : workspace) + path;
        worker.execute(() -> {
            try {
                JSONObject data = api(url, method, body);
                runOnUiThread(() -> { if (attempt == epoch && !isDestroyed()) ok.accept(data); });
            } catch (Exception e) {
                boolean expired = e instanceof SessionRejected && ((SessionRejected) e).status == 401;
                String message = friendly(e);
                runOnUiThread(() -> {
                    if (attempt != epoch || isDestroyed()) return;
                    if (expired && management) { operator = false; updatesScreen = null; notice = "Your management session ended. Sign in again to continue."; showTab(TAB_HOME); }
                    else if (expired) { peopleSession = false; caps.clear(); notice = "Your People session ended. Sign in again to continue."; showTab(TAB_HOME); }
                    else failed.accept(message);
                });
            }
        });
    }
    static String query(String value) {
        try { return URLEncoder.encode(value, "UTF-8"); }   // Charset overload needs API 33
        catch (java.io.UnsupportedEncodingException e) { throw new IllegalStateException(e); }
    }

    // All product workflows require JS. Navigation is origin-restricted; there is no native JS bridge.
    @SuppressLint("SetJavaScriptEnabled")
    private void createWebView() {
        web = new WebView(this); web.setBackgroundColor(Ui.BG); web.setSaveEnabled(false);
        WebSettings s = web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(false); s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE); s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false);
        s.setSupportMultipleWindows(true); s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setUserAgentString(s.getUserAgentString() + " KoenektAndroid/" + BuildConfig.VERSION_NAME);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return route(request.getUrl().toString(), request.isForMainFrame());
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                progress.setVisibility(View.VISIBLE);
            }
            @Override public void onPageCommitVisible(WebView view, String url) {
                if (!locked && trusted(url)) syncWebAppearance();
            }
            @Override public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                if (locked || !trusted(url)) return;
                if (AppPolicy.sameOrigin(workspace, url) && Uri.parse(url).getPath().equals("/login") && peopleSession) {
                    peopleSession = false; caps.clear(); notice = "Your People session ended. Sign in again to continue.";
                }
                syncWebAppearance();
                installMobileSupport();
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel(); showError("The secure connection could not be verified. Check your connection and device clock.");
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showError("Koenekt could not be reached. Check your connection, then retry.");
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                // Keep subframes/resources from reaching file, content or unencrypted URLs.
                String scheme = request.getUrl().getScheme();
                boolean devHttp = AppPolicy.isDevOrigin(workspace) && AppPolicy.sameOrigin(workspace, request.getUrl().toString());
                if (("http".equals(scheme) && !devHttp) || "file".equals(scheme) || "content".equals(scheme)) {
                    return new WebResourceResponse("text/plain", "UTF-8", new java.io.ByteArrayInputStream(new byte[0]));
                }
                return null;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) { progress.setProgress(value); }
            @Override public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                if (locked || !trusted(url)) { result.cancel(); return true; }
                Ui.dialog(MainActivity.this).setMessage(message).setPositiveButton("OK", (d,w) -> result.confirm())
                        .setOnCancelListener(d -> result.cancel()).show(); return true;
            }
            @Override public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                if (locked || !trusted(url)) { result.cancel(); return true; }
                Ui.dialog(MainActivity.this).setMessage(message).setPositiveButton("Continue", (d,w) -> result.confirm())
                        .setNegativeButton("Cancel", (d,w) -> result.cancel()).setOnCancelListener(d -> result.cancel()).show(); return true;
            }
            // e.g. the Users page's "Reset password" asks for the temporary password
            @Override public boolean onJsPrompt(WebView view, String url, String message, String value, JsPromptResult result) {
                if (locked || !trusted(url)) { result.cancel(); return true; }
                LinearLayout box = Ui.column(MainActivity.this); box.setPadding(dp(24), dp(16), dp(24), 0);
                box.addView(Ui.text(MainActivity.this, message, 15, Ui.INK)); Ui.gap(box, 12);
                EditText answer = Ui.field(box, null, null, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                if (value != null) answer.setText(value);
                Ui.dialog(MainActivity.this).setView(box)
                        .setPositiveButton("OK", (d,w) -> result.confirm(answer.getText().toString()))
                        .setNegativeButton("Cancel", (d,w) -> result.cancel()).setOnCancelListener(d -> result.cancel()).show(); return true;
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (locked || !trusted(view.getUrl())) return false;
                if (uploadCallback != null) uploadCallback.onReceiveValue(null);
                uploadCallback = callback;
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                String[] accepts = params.getAcceptTypes();
                if (accepts.length > 0 && !accepts[0].isEmpty() && !accepts[0].startsWith(".")) i.putExtra(Intent.EXTRA_MIME_TYPES, accepts);
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                try { externalTask = true; startActivityForResult(i, PICK_FILE); }
                catch (Exception e) { externalTask = false; callback.onReceiveValue(null); uploadCallback = null; }
                return true;
            }
            @Override public boolean onCreateWindow(WebView view, boolean dialog, boolean gesture, android.os.Message message) {
                if (locked || !gesture || !trusted(view.getUrl())) return false;
                WebView popup = new WebView(MainActivity.this);
                popup.setWebViewClient(new WebViewClient() {
                    @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                        String url = request.getUrl().toString();
                        if (!route(url, true) && trusted(url)) web.loadUrl(url);
                        v.post(v::destroy); return true;
                    }
                });
                ((WebView.WebViewTransport) message.obj).setWebView(popup); message.sendToTarget(); return true;
            }
        });
        web.setDownloadListener((url, ua, disposition, mime, length) -> {
            if (locked) return;
            if (url.startsWith("blob:") && trusted(web.getUrl())) {
                web.evaluateJavascript("window.__koenektExportBlob && window.__koenektExportBlob(" + JSONObject.quote(url) + ", 'report')", null);
            } else if (trusted(url)) download(url, URLUtil.guessFileName(url, disposition, mime), mime);
            else toast("This download is outside your workspace.");
        });
    }

    private boolean trusted(String url) { return url != null && AppPolicy.allowed(workspace, operator, url); }
    /** An open People page needs a live People session; licence pages already need the operator session. */
    private boolean resumable(String url) {
        return inWeb && trusted(url) && (peopleSession || !AppPolicy.sameOrigin(workspace, url));
    }

    private boolean route(String url, boolean mainFrame) {
        if (locked) return true;
        if (url.startsWith("koenekt-signout://")) {
            if (mainFrame && url.equals("koenekt-signout://" + exportNonce) && trusted(web.getUrl())) confirmSignOut();
            return true;
        }
        if (url.startsWith("koenekt-export://")) {
            if (mainFrame && url.equals("koenekt-export://" + exportNonce) && trusted(web.getUrl())) receiveBlob();
            return true;
        }
        if (!mainFrame) return false;
        if (trusted(url)) {
            String path = Uri.parse(url).getPath().toLowerCase(Locale.ROOT);
            if (path.endsWith(".pdf") || path.endsWith(".csv") || path.endsWith(".xlsx") || path.endsWith("/licence-file")) {
                download(url, URLUtil.guessFileName(url, null, null), null); return true;
            }
            return false;
        }
        Uri u = Uri.parse(url);
        if ("https".equals(u.getScheme()) || "mailto".equals(u.getScheme()) || "tel".equals(u.getScheme())) {
            Ui.dialog(this).setTitle("Open outside Koenekt?").setMessage(u.getHost() == null ? url : u.getHost())
                    .setPositiveButton("Open", (d,w) -> { try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { toast("No app can open this link."); } })
                    .setNegativeButton("Cancel", null).show();
        } else toast("This type of link is not supported.");
        return true;
    }

    private void loginScreen(String message) {
        leaveWeb(); flow = null; currentTitle = "Koenekt"; setBar(null, false); showNav(false);
        LinearLayout page = page(); page.setPadding(dp(24), dp(44), dp(24), dp(28));
        ((View) page.getParent()).setBackgroundColor(Ui.BG);
        ImageView logo = new ImageView(this); logo.setImageResource(Ui.logo()); logo.setAdjustViewBounds(true);
        logo.setContentDescription("Koenekt"); page.addView(logo, new LinearLayout.LayoutParams(dp(150), -2));
        Ui.gap(page, 44); page.addView(Ui.title(this, "Sign in", 32)); Ui.gap(page, 8);
        page.addView(Ui.text(this, "Use your Koenekt account. You'll see the companies and tools your account allows.", 16, Ui.MUTED));
        Ui.gap(page, 28);
        EditText user = Ui.field(page, "Username", "Your username", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText pw = Ui.field(page, "Password", "Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        // Only the master login needs an authenticator code, so the field appears just for it.
        LinearLayout codeBox = Ui.column(this);
        EditText code = Ui.field(codeBox, "Authenticator code", "6-digit code", InputType.TYPE_CLASS_NUMBER);
        codeBox.setVisibility(View.GONE); page.addView(codeBox);
        user.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                codeBox.setVisibility(s.toString().trim().equalsIgnoreCase("master") ? View.VISIBLE : View.GONE);
            }
        });
        TextView error = Ui.text(this, message, 14, Ui.DANGER); page.addView(error); Ui.gap(page, 12);
        TextView signIn = Ui.primary(this, getString(R.string.sign_in), () -> {});
        page.addView(signIn, Ui.fill(this, 54));
        signIn.setOnClickListener(v -> {
            if (busy) return;
            String origin = workspace;
            String username = user.getText().toString().trim(), password = pw.getText().toString(), otp = code.getText().toString().trim();
            if (username.isEmpty() || password.isEmpty()) { error.setText(R.string.credentials_required); return; }
            busy = true; Ui.enable(signIn, false); signIn.setText(R.string.signing_in); error.setText("");
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(pw.getWindowToken(), 0);
            int attempt = ++epoch; workspace = origin;
            boolean remember = vault.deviceSecured();
            worker.execute(() -> {
                boolean adminOk = false, peopleOk = false;
                JSONObject person = null; String failure = "";
                if (username.equalsIgnoreCase("master")) {
                    try { api(AppPolicy.ADMIN + "/licences/api/login", new JSONObject().put("password", password).put("code", otp)); adminOk = true; }
                    catch (Exception e) { failure = "Management: " + friendly(e); }
                }
                try {
                    api(origin + "/api/login", new JSONObject().put("username", username).put("password", password).put("code", otp).put("remember_device", remember));
                    person = api(origin + "/api/me", null); peopleOk = true;
                } catch (Exception e) { failure += (failure.isEmpty() ? "" : "\n") + "People: " + friendly(e); }
                // A store login (My stores) is made by Koenekt; most People accounts have none, so its refusal is not shown.
                String[] store = linkLogin(username, password);
                final boolean gotAdmin = adminOk, gotPeople = peopleOk;
                final JSONObject account = person; final String msg = failure;
                runOnUiThread(() -> {
                    if (attempt != epoch || isFinishing() || isDestroyed()) return;
                    busy = false; pw.setText(""); code.setText("");
                    if (!gotPeople && !gotAdmin && store == null) {
                        Ui.enable(signIn, true); signIn.setText(R.string.sign_in); error.setText(msg); return;
                    }
                    setLink(store, username);
                    operator = gotAdmin; peopleSession = gotPeople; applyPerson(account); companies = null;
                    String shown = !gotPeople && !gotAdmin ? "" : msg;   // a store-only login has no People account to complain about
                    notice = shown.isEmpty() ? "" : shown + "\nAvailable sections are shown below. Sign in again to retry the other service.";
                    getPreferences(MODE_PRIVATE).edit().putString("workspace", workspace).apply();
                    rememberSignIn();
                });
            });
        });
        Ui.gap(page, 8);
        page.addView(Ui.secondary(this, "Join My stores with a code", () -> startJoin(null)), Ui.fill(this, 52));
        Ui.gap(page, 24);
        page.addView(Ui.text(this, "After signing in, open the app with your fingerprint or phone PIN. Your password is never stored on this phone.", 13.5f, Ui.MUTED));
        Ui.gap(page, 4);
        String where = "Workspace: " + Uri.parse(workspace).getHost() + " · Change";
        TextView place = Ui.quiet(this, where, this::chooseWorkspace); place.setPadding(0, 0, 0, 0); place.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        page.addView(place, new LinearLayout.LayoutParams(-2, dp(48)));
        TextView about = Ui.quiet(this, "About & privacy", this::about); about.setPadding(0, 0, 0, 0); about.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        page.addView(about, new LinearLayout.LayoutParams(-2, dp(48)));
    }

    /** Sign-in screen: type another organisation's workspace (its name, e.g. "demo", or its HTTPS People address). */
    private void chooseWorkspace() {
        String host = Uri.parse(workspace).getHost();
        EditText input = new EditText(this); input.setSingleLine(true); input.setHint("e.g. people");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(host != null && host.endsWith(".people.koenekt.co.za") ? host.substring(0, host.indexOf('.')) : "people");
        input.setSelectAllOnFocus(true); input.requestFocus();
        FrameLayout box = new FrameLayout(this); box.setPadding(dp(24), dp(8), dp(24), 0); box.addView(input);
        Ui.dialog(this).setTitle("Workspace")
                .setMessage("Your organisation's workspace name, from your administrator.")
                .setView(box)
                .setPositiveButton("Use", (d, w) -> {
                    try { workspace = AppPolicy.workspace(input.getText().toString()); }
                    catch (IllegalArgumentException e) { toast(e.getMessage()); return; }
                    getPreferences(MODE_PRIVATE).edit().putString("workspace", workspace).apply();
                    loginScreen("");
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void applyPerson(JSONObject person) {
        caps.clear(); role = person == null ? "" : person.optString("role");
        mustChangePassword = person != null && person.optBoolean("must_change_password");
        boolean storesOnly = person == null && !operator && !linkToken.isEmpty();
        displayName = person != null ? person.optString("display_name", person.optString("username")) : storesOnly ? linkUser : "Koenekt management";
        roleLabel = person != null ? person.optString("role_label", role) : storesOnly
                ? ("owner".equals(linkRole) ? "My stores · owner" : "My stores · staff") : "Licences, customers and Reporter updates";
        if (person != null) {
            JSONArray list = person.optJSONArray("caps");
            if (list != null) for (int i=0; i<list.length(); i++) caps.add(list.optString(i));
            JSONArray sites = person.optJSONArray("allowed_sites");
            boolean all = false;
            if (sites != null) for (int i=0; i<sites.length(); i++) if (sites.optString(i).equals("*")) all = true;
            if (role.equals("master") || (caps.contains("manage_users") && all)) caps.add("activity_view");
        }
    }

    private void home() {
        if (busy || locked) return;
        if (!signedIn()) { loginScreen(notice); return; }
        if (peopleSession && mustChangePassword) { startPasswordChange(true); return; }
        leaveWeb(); flow = null; tab = TAB_HOME; currentTitle = "Koenekt"; setBar(null, false); showNav(true);
        LinearLayout page = page(); page.setPadding(dp(20), dp(24), dp(20), dp(28));
        ImageView logo = new ImageView(this); logo.setImageResource(Ui.logo()); logo.setAdjustViewBounds(true);
        logo.setContentDescription("Koenekt"); page.addView(logo, new LinearLayout.LayoutParams(dp(104), -2));
        Ui.gap(page, 28);
        String first = displayName.trim().isEmpty() ? "" : displayName.trim().split("\\s+")[0];
        page.addView(Ui.title(this, greeting() + (first.isEmpty() ? "" : ",\n" + first), 30)); Ui.gap(page, 4);
        page.addView(Ui.text(this, roleLabel, 15, Ui.MUTED)); Ui.gap(page, 24);
        if (!notice.isEmpty()) { page.addView(Ui.notice(this, notice, false)); Ui.gap(page, 20); }
        LinearLayout quick = Ui.row(this); quick.setGravity(Gravity.TOP);
        if (hasTab(TAB_STORES)) addQuick(quick, R.drawable.ic_store, "My stores", "Live sales from the tills", () -> showTab(TAB_STORES));
        if (peopleSession && can("people_hire") && quick.getChildCount() < 2) addQuick(quick, R.drawable.ic_add, "Add employee", "Name, company and clock PIN", this::startAddEmployee);
        if (hasTab(TAB_LIVE) && quick.getChildCount() < 2) addQuick(quick, R.drawable.ic_live, "Who's in", "Live attendance right now", () -> showTab(TAB_LIVE));
        if (hasTab(TAB_UPDATES) && quick.getChildCount() < 2) addQuick(quick, R.drawable.ic_update, "Reporter updates", "Shop PCs, update and pause", () -> showTab(TAB_UPDATES));
        if (quick.getChildCount() > 0) { page.addView(quick); Ui.gap(page, 28); }
        List<AppPolicy.Feature> tools = new ArrayList<>();
        for (AppPolicy.Feature f : AppPolicy.features(caps, operator))
            if (!(f.path().equals("/dashboard") && hasTab(TAB_LIVE))) tools.add(f);   // native Who's in replaces the web board
        if (tools.isEmpty() && quick.getChildCount() == 0) {
            page.addView(Ui.text(this, "Your account has no enabled sections yet. Ask your workspace administrator to grant company access and permissions.", 16, Ui.INK));
            return;
        }
        if (tools.isEmpty()) return;
        page.addView(Ui.label(this, "Tools")); Ui.gap(page, 10);
        LinearLayout card = Ui.column(this); card.setBackground(Ui.outline(Ui.SURFACE, Ui.LINE, 1, 20, this)); card.setClipToOutline(true);
        for (int i = 0; i < tools.size(); i++) {
            AppPolicy.Feature f = tools.get(i);
            if (i > 0) card.addView(Ui.divider(this, 16));
            card.addView(Ui.listRow(this, null, f.name(), f.description(), null,
                    f.path().endsWith("#updates") ? () -> showTab(TAB_UPDATES) : () -> open(f)));   // native Reporter updates screen
        }
        page.addView(card);
    }
    private void addQuick(LinearLayout row, int icon, String title, String subtitle, Runnable action) {
        LinearLayout tile = Ui.card(this); tile.setMinimumHeight(dp(132));
        ImageView i = Ui.icon(this, icon, Ui.BRAND, 44); i.setPadding(dp(10), dp(10), dp(10), dp(10));
        i.setBackground(Ui.outline(Ui.TINT, Ui.TINT_LINE, 1, 2, this));
        tile.addView(i); Ui.gap(tile, 14); tile.addView(Ui.title(this, title, 17)); Ui.gap(tile, 2);
        tile.addView(Ui.text(this, subtitle, 13, Ui.MUTED));
        tile.setBackground(Ui.pressable(Ui.outline(Ui.SURFACE, Ui.LINE, 1, 20, this), 20, this));
        tile.setClickable(true); tile.setFocusable(true); tile.setContentDescription(title + ". " + subtitle); tile.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
        if (row.getChildCount() > 0) p.leftMargin = dp(12);
        row.addView(tile, p);
    }
    private static String greeting() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
    }

    private void accountTab() {
        tab = TAB_ACCOUNT; currentTitle = "Account"; setBar("Account", false); showNav(true);
        LinearLayout page = page();
        LinearLayout who = Ui.row(this); who.addView(Ui.avatar(this, displayName, 56));
        LinearLayout words = Ui.column(this); words.setPadding(dp(16), 0, 0, 0);
        words.addView(Ui.title(this, displayName, 20)); words.addView(Ui.text(this, roleLabel, 14, Ui.MUTED));
        String host = Uri.parse(peopleSession ? workspace : operator ? AppPolicy.ADMIN : AppPolicy.link()).getHost();
        if (host != null) words.addView(Ui.text(this, host, 13, Ui.MUTED));
        who.addView(words, new LinearLayout.LayoutParams(0, -2, 1)); page.addView(who); Ui.gap(page, 24);
        page.addView(Ui.label(this, "Appearance")); Ui.gap(page, 8);
        page.addView(appearanceSwitch()); Ui.gap(page, 8);
        String how = Ui.LIGHT.equals(appearance) ? "Always light." : Ui.DARK_MODE.equals(appearance) ? "Always dark."
                : "Follows your phone: " + (Ui.dark ? "dark" : "light") + " now.";
        page.addView(Ui.text(this, how, 13, Ui.MUTED)); Ui.gap(page, 24);
        LinearLayout card = Ui.column(this); card.setBackground(Ui.outline(Ui.SURFACE, Ui.LINE, 1, 20, this)); card.setClipToOutline(true);
        card.addView(Ui.listRow(this, null, "Refresh access", "Reload your companies and permissions", null, this::refreshAccess));
        card.addView(Ui.divider(this, 16));
        if (linkToken.isEmpty()) {
            card.addView(Ui.listRow(this, null, "Join My stores with a code", "From an invite: choose your own login", null, () -> startJoin(null)));
            card.addView(Ui.divider(this, 16));
            card.addView(Ui.listRow(this, null, "Connect My stores", "Sign in with a store login you already have", null, this::connectStores));
        } else {
            if ("owner".equals(linkRole)) {
                card.addView(Ui.listRow(this, null, "Team", "Invite staff, reset codes, remove logins", null, this::startTeam));
                card.addView(Ui.divider(this, 16));
            }
            card.addView(Ui.listRow(this, null, "Change store password", "For " + linkUser + "; other phones are signed out", null, this::changeStorePassword));
            card.addView(Ui.divider(this, 16));
            card.addView(Ui.listRow(this, null, "My stores: " + linkUser, "Sign out of My stores on this phone", null, this::disconnectStores));
        }
        card.addView(Ui.divider(this, 16));
        boolean ownAccount = peopleSession && !role.equals("master");   // master's login is managed by Koenekt
        if (ownAccount) {
            card.addView(Ui.listRow(this, null, "Change password", "Other phones and browsers will need the new one", null, () -> startPasswordChange(false)));
            card.addView(Ui.divider(this, 16));
        }
        if (secureBuild()) { card.addView(screenshotsRow()); card.addView(Ui.divider(this, 16)); }
        card.addView(Ui.listRow(this, null, "About & privacy", "How this app protects your sign-in", null, this::about));
        card.addView(Ui.divider(this, 16));
        card.addView(Ui.listRow(this, null, "Privacy policy", "Opens in your browser", null, () -> openExternal(AppPolicy.PRIVACY_URL)));
        page.addView(card); Ui.gap(page, 20);
        TextView out = Ui.secondary(this, "Sign out", this::confirmSignOut); out.setTextColor(Ui.DANGER); page.addView(out, Ui.fill(this, 52));
        if (ownAccount) {
            Ui.gap(page, 4);
            TextView delete = Ui.quiet(this, "Delete my account", () -> PasswordFlow.confirmDelete(this));
            delete.setTextColor(Ui.DANGER); page.addView(delete, Ui.fill(this, 48));
        }
        if (!linkToken.isEmpty()) {
            Ui.gap(page, 4);
            TextView deleteStore = Ui.quiet(this, "Delete my store login", this::deleteStoreLogin);
            deleteStore.setTextColor(Ui.DANGER); page.addView(deleteStore, Ui.fill(this, 48));
        }
        Ui.gap(page, 20);
        TextView version = Ui.text(this, "Koenekt " + BuildConfig.VERSION_NAME, 13, Ui.MUTED); version.setGravity(Gravity.CENTER); page.addView(version);
    }

    /** System / Light / Dark, as the People site's appearance switch. */
    private View appearanceSwitch() {
        LinearLayout box = Ui.row(this); int pad = dp(3); box.setPadding(pad, pad, pad, pad);
        box.setBackground(Ui.outline(Ui.SURFACE, Ui.LINE, 1, 2, this));
        String[] values = {Ui.SYSTEM, Ui.LIGHT, Ui.DARK_MODE}, names = {"System", "Light", "Dark"};
        int[] icons = {R.drawable.ic_phone, R.drawable.ic_sun, R.drawable.ic_moon};
        for (int i = 0; i < values.length; i++) {
            boolean on = values[i].equals(appearance); final String value = values[i];
            LinearLayout seg = Ui.column(this); seg.setGravity(Gravity.CENTER); seg.setMinimumHeight(dp(60));
            seg.addView(Ui.icon(this, icons[i], on ? Ui.BG : Ui.INK, 22));
            TextView t = Ui.text(this, names[i], 12, on ? Ui.BG : Ui.INK); Ui.mono(t, true); t.setLetterSpacing(0.04f); t.setPadding(0, dp(4), 0, 0); t.setGravity(Gravity.CENTER);
            seg.addView(t);
            seg.setBackground(Ui.pressable(Ui.shape(on ? Ui.INK : Color.TRANSPARENT, 1, this), 1, this));
            seg.setContentDescription("Appearance: " + names[i] + (on ? ", selected" : ""));
            seg.setClickable(true); seg.setFocusable(true); seg.setOnClickListener(v -> setAppearance(value));
            box.addView(seg, new LinearLayout.LayoutParams(0, -2, 1));
        }
        return box;
    }
    private void setAppearance(String value) {
        if (value.equals(appearance)) return;
        appearance = value; getPreferences(MODE_PRIVATE).edit().putString(APPEARANCE, value).apply();
        applyAppearance(true);
    }
    /**
     * Repaints in the chosen colours without restarting the activity (the unlocked session lives here).
     * Open web pages and half-filled forms keep their state; the next screen they open is in the new colours.
     */
    private void applyAppearance(boolean force) {
        boolean dark = Ui.wantsDark(this, appearance);
        if (!force && dark == Ui.dark) return;
        setTheme(dark ? R.style.AppTheme_Dark : R.style.AppTheme);
        Ui.apply(this, dark);
        if (root == null) return;
        root.setBackgroundColor(Ui.BG); content.setBackgroundColor(Ui.BG); nav.setBackgroundColor(Ui.BG); navLine.setBackgroundColor(Ui.LINE);
        barTitle.setTextColor(Ui.INK); backButton.setColorFilter(Ui.INK);
        for (int i = 0; i < barActions.getChildCount(); i++) {   // a web page keeps its bar, so recolour its icons
            View action = barActions.getChildAt(i);
            if (action instanceof android.widget.ImageView) ((android.widget.ImageView) action).setColorFilter(Ui.INK);
        }
        progress.setProgressTintList(ColorStateList.valueOf(Ui.LIME));
        if (web != null) { web.setBackgroundColor(Ui.BG); if (!locked && trusted(web.getUrl())) syncWebAppearance(); }
        if (locked) { showLock(lockMessage); return; }
        if (inWeb || flow != null) { if (nav.getVisibility() == View.VISIBLE) showNav(true); return; }
        if (!signedIn()) loginScreen(notice); else showTab(tab);
    }
    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        if (Ui.SYSTEM.equals(appearance)) applyAppearance(false);   // the phone switched between light and dark
    }

    /** Release builds (or debug with -PsecureScreens); other debug builds always allow screenshots. */
    private boolean secureBuild() {
        boolean debuggable = (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        return !debuggable || BuildConfig.SECURE_SCREENS;
    }
    private boolean screenshotsAllowed() { return getPreferences(MODE_PRIVATE).getBoolean(ALLOW_SCREENSHOTS, false); }
    /** People data (ID numbers, pay, payroll) stays out of screenshots, recordings and recent-app previews unless allowed. */
    private void applyScreenProtection() {
        if (AppPolicy.protectScreen(secureBuild(), screenshotsAllowed())) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
    private View screenshotsRow() {
        boolean on = screenshotsAllowed();
        LinearLayout row = Ui.listRow(this, null, "Allow screenshots on this phone",
                on ? "On: also screen recording and recent apps" : "Off: Koenekt is hidden from screenshots", null, null);
        Switch toggle = new Switch(this); toggle.setChecked(on); toggle.setContentDescription("Allow screenshots on this phone");
        toggle.setThumbTintList(ColorStateList.valueOf(on ? Ui.LIME : Ui.BORDER_STRONG));
        toggle.setTrackTintList(ColorStateList.valueOf(on ? Ui.TINT_LINE : Ui.LINE));
        // The switch only moves once the setting has changed (turning it on asks for the phone's lock).
        toggle.setOnClickListener(v -> { toggle.setChecked(screenshotsAllowed()); toggleScreenshots(); });
        row.addView(toggle);
        row.setOnClickListener(v -> toggleScreenshots()); row.setClickable(true);
        return row;
    }
    private void setScreenshotsAllowed(boolean allowed) {
        getPreferences(MODE_PRIVATE).edit().putBoolean(ALLOW_SCREENSHOTS, allowed).apply();
        applyScreenProtection();
        if (tab == TAB_ACCOUNT && !locked && !inWeb && flow == null) accountTab();
    }
    private void toggleScreenshots() {
        if (locked || afterConfirm != null) return;
        if (screenshotsAllowed()) { setScreenshotsAllowed(false); toast("Screenshots of Koenekt are blocked again."); return; }
        if (!vault.deviceSecured()) { toast("Set a screen lock on this phone first."); return; }
        Ui.dialog(this).setTitle("Allow screenshots?")
                .setMessage("Screenshots and screen recordings of Koenekt will work on this phone, and its screens will show in recent apps.\n\n"
                        + "They can include ID numbers, pay and payroll. Screenshots go to the phone's gallery, where other apps and backups can reach them. Only share them with people allowed to see that information.\n\n"
                        + "This applies to this phone only. Confirm with your fingerprint or phone PIN.")
                .setPositiveButton("Continue", (d, w) -> confirmDevice("Allow screenshots", () -> {
                    setScreenshotsAllowed(true); toast("Screenshots are allowed on this phone.");
                }))
                .setNegativeButton("Cancel", null).show();
    }

    /** Asks for the phone's own lock (strong biometric or PIN, pattern, password) before a setting changes. */
    @SuppressWarnings("deprecation")
    private void confirmDevice(String title, Runnable onConfirmed) {
        if (locked || authInProgress || afterConfirm != null || isDestroyed()) return;
        afterConfirm = onConfirmed; leftDuringConfirm = false; confirmPinActive = false;
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            BiometricPrompt.Builder prompt = new BiometricPrompt.Builder(this)
                    .setTitle(title).setSubtitle("Use your fingerprint or device PIN");
            if (android.os.Build.VERSION.SDK_INT >= 30)
                prompt.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            else if (android.os.Build.VERSION.SDK_INT >= 29) prompt.setDeviceCredentialAllowed(true);
            else prompt.setNegativeButton("Use device PIN", getMainExecutor(), (dialog, which) -> confirmWithPin(title));
            confirmation = new CancellationSignal();
            prompt.build().authenticate(confirmation, getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) { confirmed(true); }
                @Override public void onAuthenticationError(int code, CharSequence message) {
                    if (afterConfirm == null || confirmPinActive) return;
                    if (code == BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT || code == BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS
                            || code == BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE || code == BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT
                            || code == BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT) confirmWithPin(title);
                    else confirmed(false);
                }
            });
        } else confirmWithPin(title);
    }
    @SuppressWarnings("deprecation")
    private void confirmWithPin(String title) {
        if (afterConfirm == null || confirmPinActive) return;
        Intent prompt = getSystemService(KeyguardManager.class).createConfirmDeviceCredentialIntent(title, "Confirm your phone PIN, pattern or password");
        if (prompt == null) { confirmed(false); toast("Set a screen lock on this phone first."); return; }
        try { confirmPinActive = true; startActivityForResult(prompt, CONFIRM_DEVICE); }
        catch (Exception e) { confirmPinActive = false; confirmed(false); toast("Device authentication is unavailable."); }
    }
    private void confirmed(boolean ok) {
        Runnable action = afterConfirm; boolean left = leftDuringConfirm;
        afterConfirm = null; leftDuringConfirm = false; confirmPinActive = false; confirmation = null;
        if (action == null || isDestroyed()) return;
        if (ok) action.run();
        // Cancelled after the app went to the background: lock now, as leaving would have.
        else if (left) lockInBackground();
    }

    private void open(AppPolicy.Feature feature) {
        if (locked) return;
        String url = feature.path().startsWith("https:") ? feature.path() : workspace + feature.path();
        if (!trusted(url)) return;
        inWeb = true; currentTitle = feature.name(); showNav(false);
        setBar(currentTitle, true, Ui.iconButton(this, R.drawable.ic_refresh, "Refresh page", () -> web.reload()),
                Ui.iconButton(this, R.drawable.ic_more, "More options", this::webMenu));
        mount(web); web.onResume(); web.loadUrl(url);
    }
    /** Opens a People website page (e.g. full records) inside the app. */
    void openWeb(String title, String path) { open(new AppPolicy.Feature(title, "", path)); }

    private void refreshAccess() {
        if (busy || locked) return;
        busy = true; int attempt = epoch; boolean checkPeople = peopleSession, checkAdmin = operator; String token = linkToken;
        worker.execute(() -> {
            JSONObject person = null; boolean adminOk = checkAdmin, linkOk = !token.isEmpty(); String msg = "", role = linkRole;
            try { if (checkPeople) person = api(workspace + "/api/me", null); }
            catch (Exception e) { msg = "People: " + friendly(e); }
            try { if (checkAdmin) api(AppPolicy.ADMIN + "/licences/api/overview", null); }
            catch (Exception e) { adminOk = false; msg += "\nManagement: " + friendly(e); }
            try { if (linkOk) role = linkApi(AppPolicy.link() + "/api/stores", "GET", null, token).optString("role", role); }
            catch (SessionRejected e) { linkOk = false; msg += "\nMy stores: session ended. Sign in again."; }
            catch (Exception e) { msg += "\nMy stores: " + friendly(e); }
            final JSONObject p = person; final boolean a = adminOk, l = linkOk; final String m = msg, r = role;
            runOnUiThread(() -> {
                if (epoch != attempt || isDestroyed()) return;
                busy = false; peopleSession = p != null; operator = a; linkRole = r;
                if (!l) linkToken = "";
                applyPerson(p); notice = m.trim();
                companies = null; peopleScreen = null; liveScreen = null; updatesScreen = null; storesScreen = null; home();
            });
        });
    }

    /** Relay sign-in on the worker: {token, role}, or null when it refused or could not be reached. */
    private String[] linkLogin(String username, String password) {
        try {
            JSONObject r = linkApi(AppPolicy.link() + "/api/login", "POST",
                    new JSONObject().put("username", username).put("password", password), "");
            String token = r.optString("token");
            return token.isEmpty() ? null : new String[]{token, r.optString("role", "staff")};
        } catch (Exception e) { return null; }
    }
    private void setLink(String[] store, String username) {
        linkToken = store == null ? "" : store[0]; linkRole = store == null ? "" : store[1];
        linkUser = store == null ? "" : username; storesScreen = null;
    }

    /** Account: add a store login to a People or management sign-in. */
    private void connectStores() {
        if (busy || locked) return;
        LinearLayout box = Ui.column(this); box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(Ui.text(this, "Your store login from Koenekt. It shows live sales for the stores it may see.", 14, Ui.MUTED)); Ui.gap(box, 12);
        EditText user = Ui.field(box, "Store login", "e.g. owner.shop", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText pw = Ui.field(box, "Password", "Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Ui.dialog(this).setTitle("Connect My stores").setView(box).setNegativeButton("Cancel", null)
                .setPositiveButton("Connect", (d, w) -> {
                    String u = user.getText().toString().trim(), p = pw.getText().toString();
                    if (u.isEmpty() || p.isEmpty()) { toast(getString(R.string.credentials_required)); return; }
                    busy = true; int attempt = epoch;
                    worker.execute(() -> {
                        String[] store = linkLogin(u, p);
                        runOnUiThread(() -> {
                            if (attempt != epoch || isDestroyed()) return;
                            busy = false;
                            if (store == null) { toast("My stores sign-in failed. Check the store login and password, or your connection."); return; }
                            setLink(store, u); refreshAfterStores();
                        });
                    });
                }).show();
    }
    private void disconnectStores() {
        Ui.dialog(this).setTitle("Sign out of My stores?").setMessage("People and management stay signed in.")
                .setPositiveButton("Sign out", (d, w) -> {
                    linkLogout(); setLink(null, "");
                    if (!peopleSession && !operator) signOut(); else refreshAfterStores();
                }).setNegativeButton("Cancel", null).show();
    }
    /** Join My stores with an invite code (typed, or from its QR link). */
    void startJoin(String code) {
        if (busy || locked) return;
        leaveWeb(); showNav(false); flow = new JoinFlow(this, code); flow.show();
    }
    /** Back out of joining: to Account when signed in, else to Sign in. */
    void leaveJoin() {
        flow = null;
        if (signedIn()) showTab(TAB_ACCOUNT); else loginScreen("");
    }
    /** The relay made the login (or set the new password) and signed this phone in to My stores. */
    void joined(String token, String username, String role, boolean reset) {
        if (token == null || token.isEmpty()) { toast("The login was made, but signing in failed. Sign in with it."); leaveJoin(); return; }
        if (!linkToken.isEmpty() && !linkToken.equals(token)) linkLogout();
        setLink(new String[]{token, role}, username);
        flow = null; tab = TAB_STORES;
        if (!peopleSession && !operator) applyPerson(null);
        notice = reset ? "Your new password is saved." : "Welcome to My stores, " + username + ".";
        refreshAfterStores();
    }
    void startTeam() {
        if (busy || locked || linkToken.isEmpty() || !"owner".equals(linkRole)) return;
        leaveWeb(); showNav(false); flow = new TeamFlow(this); flow.show();
    }
    /** The Android share sheet (e.g. WhatsApp) with an invite's link and code. */
    void shareText(String subject, String text) {
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, text);
        try { externalTask = true; startActivity(Intent.createChooser(send, subject)); }
        catch (Exception e) { externalTask = false; toast("No app can share this."); }
    }
    /** Relay calls before there is a store session (check a code, join). */
    void linkPublic(String method, String path, JSONObject body, Done ok, Consumer<String> failed) {
        int attempt = epoch; String url = AppPolicy.link() + path;
        linkWorker.execute(() -> {
            try {
                JSONObject data = linkApi(url, method, body, "");
                runOnUiThread(() -> { if (attempt == epoch && !isDestroyed()) ok.accept(data); });
            } catch (Exception e) {
                String message = friendly(e);
                runOnUiThread(() -> { if (attempt == epoch && !isDestroyed()) failed.accept(message); });
            }
        });
    }
    /** Account: change the My stores password (POST /api/me/password); this phone stays signed in. */
    private void changeStorePassword() {
        if (busy || locked || linkToken.isEmpty()) return;
        LinearLayout box = Ui.column(this); box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(Ui.text(this, "For " + linkUser + ". This phone stays signed in; other phones need the new password.", 14, Ui.MUTED)); Ui.gap(box, 12);
        int secret = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
        EditText old = Ui.field(box, "Current password", null, secret);
        EditText fresh = Ui.field(box, "New password", "At least 10 characters", secret);
        EditText again = Ui.field(box, "New password again", null, secret);
        android.app.AlertDialog dialog = Ui.dialog(this).setTitle("Change store password").setView(box)
                .setNegativeButton("Cancel", null).setPositiveButton("Save", null).show();
        TextView save = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
        save.setOnClickListener(v -> {
            String o = old.getText().toString(), n = fresh.getText().toString();
            if (o.isEmpty()) { old.setError("Enter your current password"); return; }
            if (n.length() < 10) { fresh.setError("At least 10 characters"); return; }
            if (!n.equals(again.getText().toString())) { again.setError("The two passwords are different"); return; }
            try {
                save.setEnabled(false);
                linkRequest("POST", "/api/me/password", new JSONObject().put("old", o).put("new", n), data -> {
                    dialog.dismiss(); toast("Your store password is changed.");
                }, message -> { save.setEnabled(true); fresh.setText(""); again.setText(""); fresh.setError(message); });
            } catch (Exception e) { save.setEnabled(true); }
        });
    }
    /** Account: delete this My stores login (POST /api/me/delete). People and management stay as they are. */
    private void deleteStoreLogin() {
        if (busy || locked || linkToken.isEmpty()) return;
        LinearLayout view = Ui.column(this); view.setPadding(dp(24), dp(20), dp(24), dp(4));
        view.addView(Ui.title(this, "Delete your store login?", 20)); Ui.gap(view, 12);
        view.addView(Ui.text(this, "Your My stores login " + linkUser + " and its password are deleted from Koenekt and every phone using it is signed out. "
                + "The shop's figures are not affected. The log keeps that this login existed.", 15, Ui.INK));
        Ui.gap(view, 16);
        EditText password = Ui.field(view, "Your store password", null, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        android.app.AlertDialog dialog = Ui.dialog(this).setView(view).setNegativeButton("Cancel", null)
                .setPositiveButton("Delete login", null).show();
        TextView delete = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
        delete.setTextColor(Ui.DANGER);
        delete.setOnClickListener(v -> {
            String typed = password.getText().toString();
            if (typed.isEmpty()) { password.setError("Enter your password"); return; }
            try {
                delete.setEnabled(false);
                linkRequest("POST", "/api/me/delete", new JSONObject().put("password", typed), data -> {
                    dialog.dismiss(); setLink(null, "");
                    toast("Your store login is deleted.");
                    if (!peopleSession && !operator) signOut(); else refreshAfterStores();
                }, message -> { delete.setEnabled(true); password.setText(""); password.setError(message); });
            } catch (Exception e) { delete.setEnabled(true); }
        });
    }

    /** Saves the changed set of sessions (asks for the fingerprint or PIN again), then shows Home. */
    private void refreshAfterStores() {
        storesScreen = null;
        rememberSignIn();   // re-saves and re-checks every session, or shows Home when the phone has no screen lock
    }
    /** Ends this phone's relay session there too (it ends only this token, not the login's other phones). */
    private void linkLogout() {
        String token = linkToken;
        if (token.isEmpty()) return;
        linkWorker.execute(() -> { try { linkApi(AppPolicy.link() + "/api/logout", "POST", new JSONObject(), token); } catch (Exception ignored) { } });
    }

    /** My stores: the relay's phone API with the store token. A 401 ends only the My stores session. */
    void linkRequest(String method, String path, JSONObject body, Done ok, Consumer<String> failed) {
        int attempt = epoch; String token = linkToken, url = AppPolicy.link() + path;
        if (token.isEmpty()) return;
        linkWorker.execute(() -> {
            try {
                JSONObject data = linkApi(url, method, body, token);
                runOnUiThread(() -> { if (attempt == epoch && token.equals(linkToken) && !isDestroyed()) ok.accept(data); });
            } catch (Exception e) {
                boolean expired = e instanceof SessionRejected && ((SessionRejected) e).status == 401;
                String message = friendly(e);
                runOnUiThread(() -> {
                    if (attempt != epoch || !token.equals(linkToken) || isDestroyed()) return;
                    if (expired) { setLink(null, ""); notice = "Your My stores session ended. Sign in again to continue."; showTab(TAB_HOME); }
                    else failed.accept(message);
                });
            }
        });
    }

    private JSONObject linkApi(String url, String method, JSONObject payload, String token) throws Exception {
        if (!AppPolicy.linkAllowed(url)) throw new Exception("Untrusted service address.");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        // A live answer waits up to 20 s on the relay while the shop PC reads its tills.
        c.setConnectTimeout(15000); c.setReadTimeout(35000); c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept", "application/json"); c.setRequestProperty("User-Agent", "KoenektAndroid/" + BuildConfig.VERSION_NAME);
        if (!token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
        try {
            c.setRequestMethod(method);
            if (payload != null) {
                c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = c.getOutputStream()) { out.write(payload.toString().getBytes(StandardCharsets.UTF_8)); }
            }
            int status = c.getResponseCode();
            if (status < 200 || status >= 300) {
                String detail = "";
                try (InputStream in = c.getErrorStream()) {
                    if (in != null) detail = new JSONObject(new String(readLimited(in, 16 * 1024), StandardCharsets.UTF_8)).optString("error").trim();
                } catch (Exception ignored) { /* no readable reason */ }
                if (status == 401) throw new SessionRejected(401, detail.isEmpty() ? "Sign-in failed or expired." : detail);
                throw new Exception(detail.isEmpty() ? "My stores is unavailable (" + status + "). Try again later." : detail);
            }
            try (InputStream in = c.getInputStream()) { return new JSONObject(new String(readLimited(in, 8 * 1024 * 1024), StandardCharsets.UTF_8)); }
        } finally { c.disconnect(); }
    }

    private void webMenu() {
        if (locked || !inWeb) return;
        PopupMenu p = new PopupMenu(this, barActions.getChildAt(barActions.getChildCount() - 1));
        p.getMenu().add("Print / save page as PDF").setOnMenuItemClickListener(i -> {
            if (trusted(web.getUrl())) {
                externalTask = true;
                ((PrintManager) getSystemService(PRINT_SERVICE)).print(currentTitle, web.createPrintDocumentAdapter(currentTitle), null);
            }
            return true;
        });
        p.getMenu().add("Back to Home").setOnMenuItemClickListener(i -> { showTab(TAB_HOME); return true; });
        p.show();
    }

    private void about() {
        Ui.dialog(this).setTitle("Koenekt " + BuildConfig.VERSION_NAME)
                .setMessage("People, licences, Reporter updates and My stores.\n\nThis app connects to your existing Koenekt services over HTTPS. My stores shows live till figures that your shop PCs send through Koenekt while you have it open; the app keeps no copy of them after you leave it. Server permissions determine what you can view and change.\n\nPasswords and authenticator codes are not saved. Remembered session tokens are encrypted using Android Keystore and require device authentication to restore. Unlock with a strong biometric such as fingerprint, or the phone's PIN, pattern or password. Android 8 uses the device credential. Sign out forgets the saved login. Expired or revoked server sessions require a new Koenekt sign-in.\n\nStaff, attendance, payroll and licence information stays with your Koenekt service. Exported reports are saved only to a location you select. The app has no advertising or analytics SDKs. Screenshots, screen recordings and recent-app previews of Koenekt are blocked unless you turn on Account → Allow screenshots on this phone.\n\nFor account access or correction, contact your organisation's administrator. To delete your login, use Account → Delete my account (a My stores login: Account → Delete my store login); if you can no longer sign in, use Delete account below. A My stores login you make with an invite code keeps only a hash of your password on Koenekt's server; the app never stores it. The app does not use the camera: your phone's own camera reads an invite's QR code. Deleting a login does not erase employment records that the organisation must retain.\n\nAn internet connection is required. Phone biometrics unlock this app; staff fingerprint/face enrollment still uses the connected clocks.\n\nFonts: IBM Plex Sans and IBM Plex Mono (© IBM Corp.) and Big Shoulders Display (© The Big Shoulders Project Authors), under the SIL Open Font License 1.1; the licences are included in the app.")
                .setNeutralButton("Privacy policy", (d,w) -> openExternal(AppPolicy.PRIVACY_URL))
                .setNegativeButton("Delete account", (d,w) -> openExternal(AppPolicy.DELETE_ACCOUNT_URL))
                .setPositiveButton("Close", null).show();
    }
    private void openExternal(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { toast("No browser is available to open " + url); }
    }

    void confirmSignOut() {
        if (busy) return;
        Ui.dialog(this).setTitle("Sign out of this app?").setMessage("Clear the People and management sessions on this device.")
                .setPositiveButton("Sign out", (d,w) -> signOut()).setNegativeButton("Cancel", null).show();
    }

    private void signOut() {
        authAttempt++; authInProgress = false; pinPromptActive = false; pendingAuthAttempt = 0; afterUnlock = null; busy = false;
        if (authentication != null) authentication.cancel();
        vault.clear(); locked = false; hideShield(); pendingFileRequest = 0; pendingFileResult = null;
        linkLogout(); setLink(null, "");
        epoch++; operator = false; peopleSession = false; caps.clear(); notice = "";
        companies = null; peopleScreen = null; liveScreen = null; updatesScreen = null; flow = null; tab = TAB_HOME;
        web.stopLoading(); web.loadUrl("about:blank"); web.clearHistory(); web.clearCache(true); web.clearFormData();
        WebStorage.getInstance().deleteAllData(); cancelExport();
        if (uploadCallback != null) { uploadCallback.onReceiveValue(null); uploadCallback = null; }
        // The licence site's logout endpoint revokes all operator sessions; local sign-out must not do that.
        CookieManager.getInstance().removeAllCookies(ignored -> loginScreen(""));
    }

    private static final class SessionRejected extends Exception {
        final int status;
        SessionRejected(int status, String message) { super(message); this.status = status; }
    }

    private void rememberSignIn() {
        if (!vault.deviceSecured()) {
            toast("Set a phone PIN or screen lock to enable remembered sign-in."); home(); return;
        }
        try {
            vault.prepare();
            JSONObject saved = new JSONObject().put("workspace", workspace)
                    .put("people", peopleSession ? AppPolicy.sessionCookie(CookieManager.getInstance().getCookie(workspace + "/"), "adms_session") : "")
                    .put("management", operator ? AppPolicy.sessionCookie(CookieManager.getInstance().getCookie(AppPolicy.ADMIN + "/licences/"), "kls_session") : "")
                    .put("link", linkToken).put("link_user", linkUser).put("link_role", linkRole);
            if (saved.optString("people").isEmpty() && saved.optString("management").isEmpty() && linkToken.isEmpty())
                throw new Exception("No session was returned.");
            afterUnlock = () -> {
                try { vault.save(saved); restoreSavedSession(); }
                catch (Exception e) { signOut(); toast("The saved sign-in could not be secured. Sign in again."); }
            };
            showLock("Confirm your fingerprint or device PIN to protect your saved sign-in."); requestUnlock();
        } catch (Exception e) { toast("Sign-in could not be remembered. " + friendly(e)); home(); }
    }

    private void showLock(String message) {
        if (shell == null) return;
        locked = true; lockMessage = message; root.setVisibility(View.INVISIBLE);
        if (shield != null) shell.removeView(shield);
        LinearLayout panel = Ui.column(this); panel.setPadding(dp(28), dp(70), dp(28), dp(36));
        panel.setGravity(Gravity.CENTER); panel.setBackgroundColor(Ui.BG);
        ImageView mark = new ImageView(this); mark.setImageResource(Ui.brandMark()); mark.setContentDescription("Koenekt");
        panel.addView(mark, new LinearLayout.LayoutParams(dp(54), dp(74)));
        Ui.gap(panel, 28); TextView heading = Ui.title(this, "Koenekt is locked", 26); heading.setGravity(Gravity.CENTER); panel.addView(heading);
        Ui.gap(panel, 10); TextView body = Ui.text(this, message, 16, Ui.MUTED); body.setGravity(Gravity.CENTER); panel.addView(body);
        Ui.gap(panel, 36); panel.addView(Ui.primary(this, "Unlock", this::requestUnlock), Ui.fill(this, 54));
        Ui.gap(panel, 8);
        panel.addView(Ui.quiet(this, "Sign out", () -> {
            Ui.dialog(this).setTitle("Forget this saved login?")
                    .setPositiveButton("Sign out", (d,w) -> signOut()).setNegativeButton("Cancel", null).show();
        }), Ui.fill(this, 48));
        shield = panel; shell.addView(shield, new FrameLayout.LayoutParams(-1, -1));
    }
    private void hideShield() {
        if (shield != null) { shell.removeView(shield); shield = null; }
        if (root != null) root.setVisibility(View.VISIBLE);
    }
    private void releaseLock() {
        if (!foreground) { showLock("Unlock to continue."); return; }
        locked = false; hideShield();
        web.resumeTimers(); if (inWeb) web.onResume();
        if (pendingFileRequest != 0) {
            int request = pendingFileRequest, status = pendingFileStatus; Intent result = pendingFileResult;
            pendingFileRequest = 0; pendingFileResult = null; handleFileResult(request, status, result);
        }
    }

    @SuppressWarnings("deprecation")
    private void requestUnlock() {
        if (!locked || authInProgress || busy || !foreground || setupInProgress || isDestroyed()) return;
        if (!vault.deviceSecured()) { signOut(); toast("Set a device screen lock, then sign in again."); return; }
        final int attempt = ++authAttempt;
        authInProgress = true; authentication = new CancellationSignal();
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            BiometricPrompt.Builder prompt = new BiometricPrompt.Builder(this)
                    .setTitle("Unlock Koenekt").setSubtitle("Use your fingerprint or device PIN");
            if (android.os.Build.VERSION.SDK_INT >= 30)
                prompt.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            else if (android.os.Build.VERSION.SDK_INT >= 29) prompt.setDeviceCredentialAllowed(true);
            else prompt.setNegativeButton("Use device PIN", getMainExecutor(), (dialog, which) -> launchDevicePin(attempt));
            prompt.build().authenticate(authentication, getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) { authenticated(attempt); }
                @Override public void onAuthenticationError(int code, CharSequence message) {
                    if (attempt != authAttempt || !authInProgress || pinPromptActive) return;
                    if (code == BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT || code == BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS
                            || code == BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE || code == BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT
                            || code == BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT) launchDevicePin(attempt);
                    else { authInProgress = false; showLock("Unlock cancelled. Your saved login is still protected."); }
                }
            });
        } else launchDevicePin(attempt);
    }

    @SuppressWarnings("deprecation")
    private void launchDevicePin(int attempt) {
        if (attempt != authAttempt || !authInProgress) return;
        Intent prompt = getSystemService(KeyguardManager.class).createConfirmDeviceCredentialIntent("Unlock Koenekt", "Confirm your phone PIN, pattern or password");
        if (prompt == null) { authInProgress = false; showLock("Set a phone screen lock to continue."); return; }
        try { pinPromptActive = true; pinAttempt = attempt; startActivityForResult(prompt, DEVICE_UNLOCK); }
        catch (Exception e) { pinPromptActive = false; authInProgress = false; showLock("Device authentication is unavailable. Try unlocking again."); }
    }

    private void authenticated(int attempt) {
        if (attempt != authAttempt || !authInProgress || isDestroyed()) return;
        if (!foreground) { pendingAuthAttempt = attempt; return; }
        authInProgress = false;
        Runnable action = afterUnlock; afterUnlock = null;
        if (action != null) action.run(); else restoreSavedSession();
    }

    private void restoreSavedSession() {
        final JSONObject saved;
        try { saved = vault.read(); workspace = AppPolicy.workspace(saved.getString("workspace")); }
        catch (Exception e) { signOut(); toast("Saved login is unavailable or device security changed. Sign in again."); return; }
        String p = AppPolicy.sessionCookie(saved.optString("people"), "adms_session");
        String a = AppPolicy.sessionCookie(saved.optString("management"), "kls_session");
        String l = saved.optString("link"), lUser = saved.optString("link_user"), lRole = saved.optString("link_role");
        if (p.isEmpty() && a.isEmpty() && l.isEmpty()) { signOut(); return; }
        busy = true; showLock("Checking your saved sign-in…"); int attempt = epoch;
        Runnable verify = () -> worker.execute(() -> {
            JSONObject person = null; boolean adminOk = false, retry = false; String error = "";
            // My stores: kept when the relay can't be reached (it is checked again on the next call), dropped when it refuses.
            boolean storesKept = false, storesChecked = false; String storesRole = lRole;
            if (!l.isEmpty()) {
                try { storesRole = linkApi(AppPolicy.link() + "/api/stores", "GET", null, l).optString("role", lRole); storesKept = storesChecked = true; }
                catch (SessionRejected e) { error += "\nMy stores session expired. Sign in again to renew it."; }
                catch (Exception e) { storesKept = true; retry = true; error += "\nMy stores: " + friendly(e); }
            }
            final boolean keepStores = storesKept, storesOk = storesChecked; final String role = storesRole;
            if (!p.isEmpty()) {
                try { person = api(workspace + "/api/me", null); }
                catch (SessionRejected e) { error += "\nPeople session expired. Sign in again to renew it."; }
                catch (Exception e) { retry = true; error += "\n" + friendly(e); }
            }
            if (!a.isEmpty()) {
                try { api(AppPolicy.ADMIN + "/licences/api/overview", null); adminOk = true; }
                catch (SessionRejected e) { error += "\nManagement session expired. Sign in again to renew it."; }
                catch (Exception e) { retry = true; error += "\n" + friendly(e); }
            }
            final JSONObject account = person; final boolean admin = adminOk, networkRetry = retry; final String message = error.trim();
            runOnUiThread(() -> {
                if (attempt != epoch || isDestroyed()) return;
                busy = false;
                if (account == null && !admin && !storesOk) {
                    CookieManager.getInstance().removeAllCookies(null);
                    if (networkRetry) { showLock("Could not check your saved login. Check your connection and unlock to retry."); return; }
                    signOut(); toast("Your Koenekt session expired or was revoked. Sign in again."); return;
                }
                linkToken = keepStores ? l : ""; linkUser = keepStores ? lUser : ""; linkRole = keepStores ? role : ""; storesScreen = null;
                peopleSession = account != null; operator = admin; applyPerson(account); notice = message;
                // Return to the open page when its service session is still valid; otherwise re-list sections.
                boolean resumeFile = pendingFileRequest != 0, stay = resumeFile || resumable(web.getUrl());
                releaseLock();
                if (!stay) resumeScreen();
                else if (!resumeFile && !locked && web.getProgress() < 100) web.reload();
            });
        });
        // Wait for both asynchronous CookieManager writes before making native requests.
        int[] remaining = { (p.isEmpty() ? 0 : 1) + (a.isEmpty() ? 0 : 1) };
        if (remaining[0] == 0) { verify.run(); return; }   // My stores only: no cookies to install
        ValueCallback<Boolean> installed = ok -> {
            if (attempt != epoch || isDestroyed()) return;
            if (!ok) { busy = false; signOut(); toast("Saved session could not be restored. Sign in again."); return; }
            if (--remaining[0] == 0) verify.run();
        };
        String secure = AppPolicy.isDevOrigin(workspace) ? "" : " Secure;";   // local HTTP console in debug builds only
        if (!p.isEmpty()) CookieManager.getInstance().setCookie(workspace + "/", p + "; Path=/;" + secure + " HttpOnly; SameSite=Lax", installed);
        if (!a.isEmpty()) CookieManager.getInstance().setCookie(AppPolicy.ADMIN + "/licences/", a + "; Path=/licences; Secure; HttpOnly; SameSite=Strict", installed);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if (intent.getData() != null) handleSetupLink(intent.getData());
    }
    private void handleSetupLink(Uri link) {
        if (AppPolicy.isJoinLink(link.toString())) { handleJoinLink(link); return; }
        final String target;
        try { target = AppPolicy.setupWorkspace(link.toString()); }
        catch (Exception e) { toast("This Koenekt setup link is invalid."); if (locked) requestUnlock(); return; }
        if (target.equals(workspace)) { if (locked) requestUnlock(); return; }
        setupInProgress = true;
        Ui.dialog(this).setTitle("Connect your organisation?").setMessage(Uri.parse(target).getHost() + "\n\nThis will forget any saved login on this phone.")
                .setPositiveButton("Connect", (d,w) -> {
                    setupInProgress = false; workspace = target;
                    getPreferences(MODE_PRIVATE).edit().putString("workspace", workspace).apply(); signOut();
                }).setNegativeButton("Cancel", (d,w) -> { setupInProgress = false; if (locked) requestUnlock(); })
                .setOnCancelListener(d -> { setupInProgress = false; if (locked) requestUnlock(); }).show();
    }

    /** An invite's QR link or the web page's button: open the join screen with the code filled in. */
    private void handleJoinLink(Uri link) {
        final String code;
        try { code = AppPolicy.joinCode(link.toString()); }
        catch (Exception e) { toast("This invite link is not valid. Type the code instead."); if (locked) requestUnlock(); return; }
        if (locked) { leaveWeb(); flow = new JoinFlow(this, code); requestUnlock(); return; }   // shown after unlocking
        startJoin(code);
    }

    private JSONObject api(String url, JSONObject payload) throws Exception { return api(url, "POST", payload); }
    private JSONObject api(String url, String method, JSONObject payload) throws Exception {
        if (!(AppPolicy.sameOrigin(workspace, url) || AppPolicy.sameOrigin(AppPolicy.ADMIN, url))) throw new Exception("Untrusted service address.");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        // The licence site asks the update service once per ticked PC, so those calls may take longer.
        c.setConnectTimeout(15000); c.setReadTimeout(url.contains("/licences/api/updates/") ? 90000 : 20000); c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept", "application/json"); c.setRequestProperty("User-Agent", "KoenektAndroid/" + BuildConfig.VERSION_NAME);
        String cookies = CookieManager.getInstance().getCookie(url);
        if (cookies != null) c.setRequestProperty("Cookie", cookies);
        try {
            if (payload != null) {
                c.setRequestMethod(method); c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = c.getOutputStream()) { out.write(payload.toString().getBytes(StandardCharsets.UTF_8)); }
            }
            int status = c.getResponseCode();
            for (var entry : c.getHeaderFields().entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase("Set-Cookie"))
                    for (String cookie : entry.getValue()) CookieManager.getInstance().setCookie(url, cookie, null);
            }
            if (status < 200 || status >= 300) {
                if (status == 401) throw new SessionRejected(401, "Sign-in failed or expired. Check your password and authenticator code.");
                if (status == 403) throw new SessionRejected(403, "This account does not have access.");
                if (status == 429) throw new Exception("Too many attempts. Wait 15 minutes before retrying.");
                // Validation errors (e.g. "PIN already in use") carry the server's explanation; show it. The licence
                // site also explains its 502s (e.g. "The update service is not reachable").
                String detail = status >= 400 && (status < 500 || AppPolicy.sameOrigin(AppPolicy.ADMIN, url)) ? errorDetail(c) : "";
                throw new Exception(detail.isEmpty() ? "Service unavailable (" + status + "). Try again later." : detail);
            }
            try (InputStream in = c.getInputStream()) { return new JSONObject(new String(readLimited(in, 4 * 1024 * 1024), StandardCharsets.UTF_8)); }
        } finally { c.disconnect(); }
    }

    /** FastAPI's {"detail": "..."} message from an error response, or "". */
    private String errorDetail(HttpURLConnection c) {
        try (InputStream in = c.getErrorStream()) {
            if (in == null) return "";
            Object detail = new JSONObject(new String(readLimited(in, 16 * 1024), StandardCharsets.UTF_8)).opt("detail");
            return detail instanceof String text ? text.trim() : "";
        } catch (Exception e) { return ""; }
    }

    private String friendly(Exception e) {
        if (e instanceof java.net.SocketTimeoutException || e instanceof java.net.UnknownHostException || e instanceof javax.net.ssl.SSLException)
            return "Could not securely reach Koenekt. Check your connection or contact your administrator.";
        return e.getMessage() == null ? "Could not complete the request." : e.getMessage();
    }
    private byte[] readLimited(InputStream in, int maximum) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[16384]; int n;
        while ((n = in.read(buffer)) != -1) {
            if (out.size() + n > maximum) throw new Exception("The export is too large. Select a smaller report range.");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    /**
     * People and the licence site keep their own System / Light / Dark choice (localStorage
     * "koenekt-theme", read before first paint). Inside the app the app's Appearance decides, so
     * each page is given the app's colours, and remembers them for the next page it opens, and the
     * page's own switch is hidden. Style is set through the DOM, which those sites' CSP allows.
     */
    private void syncWebAppearance() {
        String mode = Ui.dark ? "dark" : "light";
        web.evaluateJavascript("(function(m){try{localStorage.setItem('koenekt-theme',m)}catch(e){}"
                + "var r=document.documentElement;r.setAttribute('data-theme',m);r.setAttribute('data-theme-choice',m);"
                + "try{if(window.KoenektTheme)window.KoenektTheme.choose(m)}catch(e){}"
                + "var hide=function(){var l=document.querySelectorAll('select[data-kp-theme],#themeSwitch');"
                + "for(var i=0;i<l.length;i++){l[i].style.setProperty('display','none','important');l[i].setAttribute('aria-hidden','true')}};"
                + "hide();setTimeout(hide,800)})(" + JSONObject.quote(mode) + ")", null);
    }

    private void installMobileSupport() {
        try {
            exportNonce = UUID.randomUUID().toString();
            if (mobileScript == null) try (InputStream in = getAssets().open("mobile.js")) { mobileScript = new String(readLimited(in, 128*1024), StandardCharsets.UTF_8); }
            web.evaluateJavascript("window.__koenektNonce=" + JSONObject.quote(exportNonce) + ";\n" + mobileScript, null);
        } catch (Exception e) { toast("Mobile export support could not be loaded."); }
    }

    private void receiveBlob() {
        if (pendingExport != null || busy) { toast("Finish the current download first."); return; }
        int attempt = epoch;
        web.evaluateJavascript("JSON.stringify(window.__koenektDownload || null)", result -> {
            if (attempt != epoch) return;
            try {
                Object decoded = new org.json.JSONTokener(result).nextValue();
                JSONObject blob = new JSONObject(String.valueOf(decoded));
                web.evaluateJavascript("window.__koenektDownload=null", null);
                if (blob.has("error")) { toast(blob.optString("error")); return; }
                String encoded = blob.getString("data");
                if (encoded.length() > (MAX_EXPORT * 4L / 3L + 8)) throw new Exception("Select a smaller report range.");
                byte[] bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT);
                stage(bytes, blob.optString("name", "report"), blob.optString("mime", "application/octet-stream"));
            } catch (Exception e) { toast("Could not save export. " + friendly(e)); }
        });
    }

    private void download(String url, String filename, String mime) {
        if (!trusted(url) || pendingExport != null || busy) { toast("Finish the current download first."); return; }
        busy = true; progress.setVisibility(View.VISIBLE); int attempt = epoch;
        worker.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                byte[] bytes; String name = filename, type = mime;
                try {
                    c.setConnectTimeout(15000); c.setReadTimeout(30000); c.setInstanceFollowRedirects(false);
                    String cookie = CookieManager.getInstance().getCookie(url); if (cookie != null) c.setRequestProperty("Cookie", cookie);
                    int status = c.getResponseCode();
                    if (status != 200) throw new Exception("Download failed (" + status + "). Refresh the page and try again.");
                    type = c.getContentType();
                    if (type != null && (type.contains("text/html") || type.contains("application/json"))) throw new Exception("Your session may have ended. Refresh access and sign in again.");
                    name = URLUtil.guessFileName(url, c.getHeaderField("Content-Disposition"), type);
                    try (InputStream in = c.getInputStream()) { bytes = readLimited(in, MAX_EXPORT); }
                } finally { c.disconnect(); }
                final String exportName = name, exportType = type;
                runOnUiThread(() -> { if (attempt != epoch || isDestroyed()) return; busy = false; progress.setVisibility(View.GONE); stage(bytes, exportName, exportType); });
            } catch (Exception e) {
                runOnUiThread(() -> { if (attempt != epoch || isDestroyed()) return; busy = false; progress.setVisibility(View.GONE); toast(friendly(e)); });
            }
        });
    }

    private void stage(byte[] data, String name, String mime) {
        try {
            if (pendingExport != null) { toast("Finish the current download first."); return; }
            if (data.length > MAX_EXPORT) throw new Exception("Select a smaller report range.");
            pendingExport = File.createTempFile("export-", ".tmp", getCacheDir());
            try (FileOutputStream out = new FileOutputStream(pendingExport)) { out.write(data); }
            name = name.replaceAll("[^A-Za-z0-9._ ()-]", "_");
            if (name.isEmpty() || name.equals(".") || name.equals("..")) name = "koenekt-report";
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(mime == null || mime.isEmpty() ? "application/octet-stream" : mime.split(";")[0])
                    .putExtra(Intent.EXTRA_TITLE, name);
            externalTask = true; startActivityForResult(i, SAVE_FILE);
        } catch (Exception e) { externalTask = false; cancelExport(); toast("Could not save file. " + friendly(e)); }
    }

    private void cancelExport() { if (pendingExport != null) { pendingExport.delete(); pendingExport = null; } }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == CONFIRM_DEVICE) {
            if (confirmPinActive) confirmed(result == RESULT_OK);
            return;
        }
        if (request == DEVICE_UNLOCK) {
            if (!pinPromptActive) return;
            pinPromptActive = false;
            if (result == RESULT_OK) authenticated(pinAttempt);
            else { authInProgress = false; showLock("Unlock cancelled. Your saved login is still protected."); }
            return;
        }
        if (locked && (request == PICK_FILE || request == SAVE_FILE)) {
            pendingFileRequest = request; pendingFileStatus = result; pendingFileResult = data; return;
        }
        handleFileResult(request, result, data);
    }
    private void handleFileResult(int request, int result, Intent data) {
        if (request == PICK_FILE && uploadCallback != null) {
            Uri[] uris = null;
            if (result == RESULT_OK && data != null) {
                ClipData clips = data.getClipData();
                if (clips != null) { uris = new Uri[clips.getItemCount()]; for (int i=0; i<uris.length; i++) uris[i] = clips.getItemAt(i).getUri(); }
                else if (data.getData() != null) uris = new Uri[]{data.getData()};
                if (uris != null) for (Uri uri : uris) if (!"content".equals(uri.getScheme())) { uris = null; break; }
            }
            uploadCallback.onReceiveValue(uris); uploadCallback = null;
        }
        if (request == SAVE_FILE && pendingExport != null) {
            if (result != RESULT_OK || data == null || data.getData() == null) { cancelExport(); return; }
            File file = pendingExport; pendingExport = null; Uri target = data.getData();
            worker.execute(() -> {
                try (InputStream in = new java.io.FileInputStream(file); OutputStream out = getContentResolver().openOutputStream(target, "wt")) {
                    if (out == null) throw new Exception("Destination unavailable.");
                    byte[] buffer = new byte[16384]; int length;
                    while ((length = in.read(buffer)) != -1) out.write(buffer, 0, length);
                    runOnUiThread(() -> toast("File saved."));
                } catch (Exception e) { runOnUiThread(() -> toast("Could not save file. " + friendly(e))); }
                finally { file.delete(); }
            });
        }
    }

    void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private void showError(String message) {
        if (locked) return;
        progress.setVisibility(View.GONE);
        Ui.dialog(this).setTitle("Connection unavailable").setMessage(message)
                .setPositiveButton("Retry", (d,w) -> web.reload()).setNegativeButton("Home", (d,w) -> home()).show();
    }
    private void handleBack() {
        if (locked) { finish(); return; }
        if (backAction != null) { backAction.run(); return; }
        if (inWeb && web.canGoBack()) web.goBack();
        else if (inWeb) showTab(tab);
        else if (flow != null) finishFlow(false);
        else if (tab != TAB_HOME && signedIn()) showTab(TAB_HOME);
        else finish();
    }
    /** After unlocking: an add or edit flow keeps its answers; tabs reload their data. */
    private void resumeScreen() {
        if (flow != null && (peopleSession || flow instanceof JoinFlow || (flow instanceof TeamFlow && !linkToken.isEmpty()))) {
            showNav(false); flow.show();
        }
        else showTab(tab);
    }
    // API 33+ uses the registered OnBackInvokedCallback; older Android uses this callback.
    @SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { handleBack(); }
    @Override protected void onPause() { super.onPause(); if (web != null) web.onPause(); }
    @Override protected void onResume() {
        super.onResume(); foreground = true; externalTask = false;
        if (pendingAuthAttempt != 0) { int attempt = pendingAuthAttempt; pendingAuthAttempt = 0; authenticated(attempt); }
        else if (locked && root != null) root.post(this::requestUnlock);
        else if (web != null && inWeb) web.onResume();
    }
    @Override protected void onStop() {
        super.onStop(); foreground = false;
        if (authInProgress || root == null) return;
        // The phone's PIN screen for a setting covers the app; the result decides (see confirmed).
        if (afterConfirm != null) { leftDuringConfirm = true; return; }
        lockInBackground();
    }
    private void lockInBackground() {
        if (vault.exists() || signedIn()) {
            // Without a screen lock the return cannot be protected, so sign out, except while
            // the user is in a file picker, Save dialog or print screen this app opened.
            if (!vault.deviceSecured()) { if (!externalTask) signOut(); return; }
            epoch++; busy = false;
            showLock("Unlock to continue.");
            web.stopLoading(); web.onPause(); web.pauseTimers();
            CookieManager.getInstance().removeAllCookies(null);
            linkToken = "";   // read again from the vault after unlocking, like the cookies
        }
    }
    @Override protected void onDestroy() {
        authAttempt++;
        if (authentication != null) authentication.cancel();
        if (confirmation != null) confirmation.cancel();
        afterConfirm = null;
        epoch++; worker.shutdownNow(); linkWorker.shutdownNow(); cancelExport();
        if (uploadCallback != null) uploadCallback.onReceiveValue(null);
        if (web != null) { if (web.getParent() instanceof ViewGroup parent) parent.removeView(web); web.destroy(); }
        super.onDestroy();
    }
}
