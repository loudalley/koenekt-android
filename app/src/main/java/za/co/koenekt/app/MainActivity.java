package za.co.koenekt.app;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.BiometricManager;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
    static final int TAB_HOME = 0, TAB_PEOPLE = 1, TAB_LIVE = 2, TAB_ACCOUNT = 3;
    private static final int PICK_FILE = 20, SAVE_FILE = 21, DEVICE_UNLOCK = 22, MAX_EXPORT = 20 * 1024 * 1024;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Set<String> caps = new HashSet<>();
    private String workspace = AppPolicy.DEFAULT_WORKSPACE, displayName = "", role = "", roleLabel = "";
    private String notice = "", currentTitle = "Koenekt", exportNonce = "";
    private boolean operator = false, peopleSession = false, inWeb = false, busy = false;
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
    private Intent pendingFileResult;
    private int pendingFileRequest, pendingFileStatus;
    private List<Company> companies;
    private PeopleScreen peopleScreen;
    private LiveScreen liveScreen;
    private Flow flow;

    /** A full-screen task (add or edit a person) that keeps its answers across locking and unlocking. */
    interface Flow { void show(); }

    /** A People company/site the signed-in account may use. */
    static final class Company {
        final String name, url;
        Company(String name, String url) { this.name = name; this.url = url; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        vault = new SessionVault(this);
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
        File[] stale = getCacheDir().listFiles((dir, name) -> name.startsWith("export-"));
        if (stale != null) for (File file : stale) file.delete();
        // Personnel/payroll content must not appear in recent-app previews.
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        workspace = getPreferences(MODE_PRIVATE).getString("workspace", AppPolicy.DEFAULT_WORKSPACE);
        // Debug builds given -PdevOrigin talk to a local People console; release builds never do.
        if (BuildConfig.DEBUG && !BuildConfig.DEV_ORIGIN.isEmpty()) { AppPolicy.allowDevOrigin(BuildConfig.DEV_ORIGIN); workspace = BuildConfig.DEV_ORIGIN; }
        try { workspace = AppPolicy.workspace(workspace); } catch (Exception e) { workspace = AppPolicy.DEFAULT_WORKSPACE; }
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().removeAllCookies(ignored -> buildShell());
    }

    int dp(float n) { return Ui.dp(this, n); }

    private void buildShell() {
        root = Ui.column(this); root.setBackgroundColor(Ui.SURFACE);
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
        nav = Ui.row(this); nav.setBackgroundColor(Ui.SURFACE); nav.setPadding(0, dp(8), 0, dp(6));
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
        return true;
    }
    private void showNav(boolean visible) {
        nav.setVisibility(visible ? View.VISIBLE : View.GONE); navLine.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) return;
        nav.removeAllViews();
        int[] tabs = {TAB_HOME, TAB_PEOPLE, TAB_LIVE, TAB_ACCOUNT};
        int[] icons = {R.drawable.ic_home, R.drawable.ic_people, R.drawable.ic_live, R.drawable.ic_account};
        String[] names = {"Home", "People", "Who's in", "Account"};
        for (int i = 0; i < tabs.length; i++) {
            if (!hasTab(tabs[i])) continue;
            boolean selected = tabs[i] == tab;
            LinearLayout cell = Ui.column(this); cell.setGravity(Gravity.CENTER_HORIZONTAL);
            ImageView icon = Ui.icon(this, icons[i], selected ? Ui.BRAND : Ui.MUTED, 24);
            icon.setPadding(dp(18), dp(4), dp(18), dp(4)); icon.setLayoutParams(new LinearLayout.LayoutParams(dp(60), dp(32)));
            if (selected) icon.setBackground(Ui.shape(Ui.TINT, 16, this));
            cell.addView(icon);
            TextView label = Ui.text(this, names[i], 12, selected ? Ui.BRAND : Ui.MUTED); label.setPadding(0, dp(4), 0, 0); label.setGravity(Gravity.CENTER);
            if (selected) label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            cell.addView(label);
            cell.setContentDescription(names[i]); cell.setClickable(true); cell.setFocusable(true);
            cell.setBackground(Ui.pressable(Ui.shape(Color.TRANSPARENT, 16, this), 16, this));
            final int target = tabs[i]; cell.setOnClickListener(v -> showTab(target));
            nav.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
        }
    }

    void showTab(int which) {
        if (busy || locked) return;
        if (!peopleSession && !operator) { loginScreen(notice); return; }
        if (!hasTab(which)) which = TAB_HOME;
        leaveWeb(); flow = null; tab = which;
        if (which == TAB_PEOPLE) { if (peopleScreen == null) peopleScreen = new PeopleScreen(this); showNav(true); peopleScreen.show(); }
        else if (which == TAB_LIVE) { if (liveScreen == null) liveScreen = new LiveScreen(this); showNav(true); liveScreen.show(); }
        else if (which == TAB_ACCOUNT) accountTab();
        else home();
    }
    private void leaveWeb() { if (inWeb) { web.stopLoading(); web.onPause(); inWeb = false; } }

    void startAddEmployee() {
        if (busy || locked || !peopleSession || !caps.contains("people_hire")) return;
        leaveWeb(); showNav(false); flow = new AddEmployeeFlow(this); flow.show();
    }
    void editPerson(JSONObject person, Company company) {
        if (busy || locked || !peopleSession || !caps.contains("people_edit")) return;
        leaveWeb(); showNav(false); flow = new EditPersonFlow(this, person, company); flow.show();
    }
    void finishFlow(boolean changed) {
        flow = null; if (changed && peopleScreen != null) peopleScreen.invalidate(); showTab(TAB_PEOPLE);
    }

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
    void request(String path, JSONObject body, Done ok, Consumer<String> failed) {
        int attempt = epoch; String url = workspace + path;
        worker.execute(() -> {
            try {
                JSONObject data = api(url, body);
                runOnUiThread(() -> { if (attempt == epoch && !isDestroyed()) ok.accept(data); });
            } catch (Exception e) {
                boolean expired = e instanceof SessionRejected && ((SessionRejected) e).status == 401;
                String message = friendly(e);
                runOnUiThread(() -> {
                    if (attempt != epoch || isDestroyed()) return;
                    if (expired) { peopleSession = false; caps.clear(); notice = "Your People session ended. Sign in again to continue."; showTab(TAB_HOME); }
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
            @Override public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                if (locked || !trusted(url)) return;
                if (AppPolicy.sameOrigin(workspace, url) && Uri.parse(url).getPath().equals("/login") && peopleSession) {
                    peopleSession = false; caps.clear(); notice = "Your People session ended. Sign in again to continue.";
                }
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
                new AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("OK", (d,w) -> result.confirm())
                        .setOnCancelListener(d -> result.cancel()).show(); return true;
            }
            @Override public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                if (locked || !trusted(url)) { result.cancel(); return true; }
                new AlertDialog.Builder(MainActivity.this).setMessage(message).setPositiveButton("Continue", (d,w) -> result.confirm())
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
            new AlertDialog.Builder(this).setTitle("Open outside Koenekt?").setMessage(u.getHost() == null ? url : u.getHost())
                    .setPositiveButton("Open", (d,w) -> { try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { toast("No app can open this link."); } })
                    .setNegativeButton("Cancel", null).show();
        } else toast("This type of link is not supported.");
        return true;
    }

    private void loginScreen(String message) {
        leaveWeb(); flow = null; currentTitle = "Koenekt"; setBar(null, false); showNav(false);
        LinearLayout page = page(); page.setPadding(dp(24), dp(44), dp(24), dp(28));
        ((View) page.getParent()).setBackgroundColor(Ui.SURFACE);
        ImageView logo = new ImageView(this); logo.setImageResource(R.drawable.ic_logo); logo.setAdjustViewBounds(true);
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
                final boolean gotAdmin = adminOk, gotPeople = peopleOk;
                final JSONObject account = person; final String msg = failure;
                runOnUiThread(() -> {
                    if (attempt != epoch || isFinishing() || isDestroyed()) return;
                    busy = false; pw.setText(""); code.setText("");
                    if (!gotPeople && !gotAdmin) {
                        Ui.enable(signIn, true); signIn.setText(R.string.sign_in); error.setText(msg); return;
                    }
                    operator = gotAdmin; peopleSession = gotPeople; applyPerson(account); companies = null;
                    notice = msg.isEmpty() ? "" : msg + "\nAvailable sections are shown below. Sign in again to retry the other service.";
                    getPreferences(MODE_PRIVATE).edit().putString("workspace", workspace).apply();
                    rememberSignIn();
                });
            });
        });
        Ui.gap(page, 24);
        page.addView(Ui.text(this, "After signing in, open the app with your fingerprint or phone PIN. Your password is never stored on this phone.", 13.5f, Ui.MUTED));
        Ui.gap(page, 4);
        TextView about = Ui.quiet(this, "About & privacy", this::about); about.setPadding(0, 0, 0, 0); about.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        page.addView(about, new LinearLayout.LayoutParams(-2, dp(48)));
    }

    private void applyPerson(JSONObject person) {
        caps.clear(); role = person == null ? "" : person.optString("role");
        displayName = person == null ? "Koenekt management" : person.optString("display_name", person.optString("username"));
        roleLabel = person == null ? "Licences, customers and Reporter updates" : person.optString("role_label", role);
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
        if (!peopleSession && !operator) { loginScreen(notice); return; }
        leaveWeb(); flow = null; tab = TAB_HOME; currentTitle = "Koenekt"; setBar(null, false); showNav(true);
        LinearLayout page = page(); page.setPadding(dp(20), dp(24), dp(20), dp(28));
        ImageView logo = new ImageView(this); logo.setImageResource(R.drawable.ic_logo); logo.setAdjustViewBounds(true);
        logo.setContentDescription("Koenekt"); page.addView(logo, new LinearLayout.LayoutParams(dp(104), -2));
        Ui.gap(page, 28);
        String first = displayName.trim().isEmpty() ? "" : displayName.trim().split("\\s+")[0];
        page.addView(Ui.title(this, greeting() + (first.isEmpty() ? "" : ",\n" + first), 30)); Ui.gap(page, 4);
        page.addView(Ui.text(this, roleLabel, 15, Ui.MUTED)); Ui.gap(page, 24);
        if (!notice.isEmpty()) { page.addView(Ui.notice(this, notice, false)); Ui.gap(page, 20); }
        LinearLayout quick = Ui.row(this); quick.setGravity(Gravity.TOP);
        if (peopleSession && can("people_hire")) addQuick(quick, R.drawable.ic_add, "Add employee", "Name, company and clock PIN", this::startAddEmployee);
        if (hasTab(TAB_LIVE)) addQuick(quick, R.drawable.ic_live, "Who's in", "Live attendance right now", () -> showTab(TAB_LIVE));
        if (quick.getChildCount() > 0) { page.addView(quick); Ui.gap(page, 28); }
        List<AppPolicy.Feature> tools = new ArrayList<>();
        for (AppPolicy.Feature f : AppPolicy.features(caps, operator))
            if (!(f.path().equals("/dashboard") && hasTab(TAB_LIVE))) tools.add(f);   // native Who's in replaces the web board
        if (tools.isEmpty() && quick.getChildCount() == 0) {
            page.addView(Ui.text(this, "Your account has no enabled sections yet. Ask your workspace administrator to grant company access and permissions.", 16, Ui.INK));
            return;
        }
        if (tools.isEmpty()) return;
        page.addView(Ui.title(this, "Tools", 18)); Ui.gap(page, 10);
        LinearLayout card = Ui.column(this); card.setBackground(Ui.outline(Ui.SURFACE, Ui.LINE, 1, 20, this)); card.setClipToOutline(true);
        for (int i = 0; i < tools.size(); i++) {
            AppPolicy.Feature f = tools.get(i);
            if (i > 0) card.addView(Ui.divider(this, 16));
            card.addView(Ui.listRow(this, null, f.name(), f.description(), null, () -> open(f)));
        }
        page.addView(card);
    }
    private void addQuick(LinearLayout row, int icon, String title, String subtitle, Runnable action) {
        LinearLayout tile = Ui.card(this); tile.setMinimumHeight(dp(132));
        ImageView i = Ui.icon(this, icon, Ui.BRAND, 44); i.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable circle = new GradientDrawable(); circle.setShape(GradientDrawable.OVAL); circle.setColor(Ui.TINT); i.setBackground(circle);
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
        String host = Uri.parse(peopleSession ? workspace : AppPolicy.ADMIN).getHost();
        if (host != null) words.addView(Ui.text(this, host, 13, Ui.MUTED));
        who.addView(words, new LinearLayout.LayoutParams(0, -2, 1)); page.addView(who); Ui.gap(page, 24);
        LinearLayout card = Ui.column(this); card.setBackground(Ui.outline(Ui.SURFACE, Ui.LINE, 1, 20, this)); card.setClipToOutline(true);
        card.addView(Ui.listRow(this, null, "Refresh access", "Reload your companies and permissions", null, this::refreshAccess));
        card.addView(Ui.divider(this, 16));
        card.addView(Ui.listRow(this, null, "About & privacy", "How this app protects your sign-in", null, this::about));
        card.addView(Ui.divider(this, 16));
        card.addView(Ui.listRow(this, null, "Privacy policy", "Opens in your browser", null, () -> openExternal(AppPolicy.PRIVACY_URL)));
        page.addView(card); Ui.gap(page, 20);
        TextView out = Ui.secondary(this, "Sign out", this::confirmSignOut); out.setTextColor(Ui.DANGER); page.addView(out, Ui.fill(this, 52));
        Ui.gap(page, 20);
        TextView version = Ui.text(this, "Koenekt " + BuildConfig.VERSION_NAME, 13, Ui.MUTED); version.setGravity(Gravity.CENTER); page.addView(version);
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
        busy = true; int attempt = epoch; boolean checkPeople = peopleSession, checkAdmin = operator;
        worker.execute(() -> {
            JSONObject person = null; boolean adminOk = checkAdmin; String msg = "";
            try { if (checkPeople) person = api(workspace + "/api/me", null); }
            catch (Exception e) { msg = "People: " + friendly(e); }
            try { if (checkAdmin) api(AppPolicy.ADMIN + "/licences/api/overview", null); }
            catch (Exception e) { adminOk = false; msg += "\nManagement: " + friendly(e); }
            final JSONObject p = person; final boolean a = adminOk; final String m = msg;
            runOnUiThread(() -> {
                if (epoch != attempt || isDestroyed()) return;
                busy = false; peopleSession = p != null; operator = a; applyPerson(p); notice = m.trim();
                companies = null; peopleScreen = null; liveScreen = null; home();
            });
        });
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
        new AlertDialog.Builder(this).setTitle("Koenekt " + BuildConfig.VERSION_NAME)
                .setMessage("People, licences and Reporter updates.\n\nThis app connects to your existing Koenekt services over HTTPS. Server permissions determine what you can view and change.\n\nPasswords and authenticator codes are not saved. Remembered session tokens are encrypted using Android Keystore and require device authentication to restore. Unlock with a strong biometric such as fingerprint, or the phone's PIN, pattern or password. Android 8 uses the device credential. Sign out forgets the saved login. Expired or revoked server sessions require a new Koenekt sign-in.\n\nStaff, attendance, payroll and licence information stays with your Koenekt service. Exported reports are saved only to a location you select. The app has no advertising or analytics SDKs.\n\nFor account access or correction, contact your organisation's administrator. To ask for your login to be deleted, use Delete account below. Removing a login does not erase employment records that the organisation must retain.\n\nAn internet connection is required. Phone biometrics unlock this app; staff fingerprint/face enrollment still uses the connected clocks.")
                .setNeutralButton("Privacy policy", (d,w) -> openExternal(AppPolicy.PRIVACY_URL))
                .setNegativeButton("Delete account", (d,w) -> openExternal(AppPolicy.DELETE_ACCOUNT_URL))
                .setPositiveButton("Close", null).show();
    }
    private void openExternal(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { toast("No browser is available to open " + url); }
    }

    private void confirmSignOut() {
        if (busy) return;
        new AlertDialog.Builder(this).setTitle("Sign out of this app?").setMessage("Clear the People and management sessions on this device.")
                .setPositiveButton("Sign out", (d,w) -> signOut()).setNegativeButton("Cancel", null).show();
    }

    private void signOut() {
        authAttempt++; authInProgress = false; pinPromptActive = false; pendingAuthAttempt = 0; afterUnlock = null; busy = false;
        if (authentication != null) authentication.cancel();
        vault.clear(); locked = false; hideShield(); pendingFileRequest = 0; pendingFileResult = null;
        epoch++; operator = false; peopleSession = false; caps.clear(); notice = "";
        companies = null; peopleScreen = null; liveScreen = null; flow = null; tab = TAB_HOME;
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
                    .put("management", operator ? AppPolicy.sessionCookie(CookieManager.getInstance().getCookie(AppPolicy.ADMIN + "/licences/"), "kls_session") : "");
            if (saved.optString("people").isEmpty() && saved.optString("management").isEmpty()) throw new Exception("No session was returned.");
            afterUnlock = () -> {
                try { vault.save(saved); restoreSavedSession(); }
                catch (Exception e) { signOut(); toast("The saved sign-in could not be secured. Sign in again."); }
            };
            showLock("Confirm your fingerprint or device PIN to protect your saved sign-in."); requestUnlock();
        } catch (Exception e) { toast("Sign-in could not be remembered. " + friendly(e)); home(); }
    }

    private void showLock(String message) {
        if (shell == null) return;
        locked = true; root.setVisibility(View.INVISIBLE);
        if (shield != null) shell.removeView(shield);
        LinearLayout panel = Ui.column(this); panel.setPadding(dp(28), dp(70), dp(28), dp(36));
        panel.setGravity(Gravity.CENTER); panel.setBackgroundColor(Ui.BG);
        ImageView mark = new ImageView(this); mark.setImageResource(R.drawable.ic_brand_mark); mark.setContentDescription("Koenekt");
        panel.addView(mark, new LinearLayout.LayoutParams(dp(54), dp(74)));
        Ui.gap(panel, 28); TextView heading = Ui.title(this, "Koenekt is locked", 26); heading.setGravity(Gravity.CENTER); panel.addView(heading);
        Ui.gap(panel, 10); TextView body = Ui.text(this, message, 16, Ui.MUTED); body.setGravity(Gravity.CENTER); panel.addView(body);
        Ui.gap(panel, 36); panel.addView(Ui.primary(this, "Unlock", this::requestUnlock), Ui.fill(this, 54));
        Ui.gap(panel, 8);
        panel.addView(Ui.quiet(this, "Sign out", () -> {
            new AlertDialog.Builder(this).setTitle("Forget this saved login?")
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
        if (p.isEmpty() && a.isEmpty()) { signOut(); return; }
        busy = true; showLock("Checking your saved sign-in…"); int attempt = epoch;
        Runnable verify = () -> worker.execute(() -> {
            JSONObject person = null; boolean adminOk = false, retry = false; String error = "";
            if (!p.isEmpty()) {
                try { person = api(workspace + "/api/me", null); }
                catch (SessionRejected e) { error = "People session expired. Sign in again to renew it."; }
                catch (Exception e) { retry = true; error = friendly(e); }
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
                if (account == null && !admin) {
                    CookieManager.getInstance().removeAllCookies(null);
                    if (networkRetry) { showLock("Could not check your saved login. Check your connection and unlock to retry."); return; }
                    signOut(); toast("Your Koenekt session expired or was revoked. Sign in again."); return;
                }
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
        final String target;
        try { target = AppPolicy.setupWorkspace(link.toString()); }
        catch (Exception e) { toast("This Koenekt setup link is invalid."); if (locked) requestUnlock(); return; }
        if (target.equals(workspace)) { if (locked) requestUnlock(); return; }
        setupInProgress = true;
        new AlertDialog.Builder(this).setTitle("Connect your organisation?").setMessage(Uri.parse(target).getHost() + "\n\nThis will forget any saved login on this phone.")
                .setPositiveButton("Connect", (d,w) -> {
                    setupInProgress = false; workspace = target;
                    getPreferences(MODE_PRIVATE).edit().putString("workspace", workspace).apply(); signOut();
                }).setNegativeButton("Cancel", (d,w) -> { setupInProgress = false; if (locked) requestUnlock(); })
                .setOnCancelListener(d -> { setupInProgress = false; if (locked) requestUnlock(); }).show();
    }

    private JSONObject api(String url, JSONObject payload) throws Exception {
        if (!(AppPolicy.sameOrigin(workspace, url) || AppPolicy.sameOrigin(AppPolicy.ADMIN, url))) throw new Exception("Untrusted service address.");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(20000); c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept", "application/json"); c.setRequestProperty("User-Agent", "KoenektAndroid/" + BuildConfig.VERSION_NAME);
        String cookies = CookieManager.getInstance().getCookie(url);
        if (cookies != null) c.setRequestProperty("Cookie", cookies);
        try {
            if (payload != null) {
                c.setRequestMethod("POST"); c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json");
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
                // Validation errors (e.g. "PIN already in use") carry the server's explanation; show it.
                String detail = status >= 400 && status < 500 ? errorDetail(c) : "";
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
        new AlertDialog.Builder(this).setTitle("Connection unavailable").setMessage(message)
                .setPositiveButton("Retry", (d,w) -> web.reload()).setNegativeButton("Home", (d,w) -> home()).show();
    }
    private void handleBack() {
        if (locked) { finish(); return; }
        if (backAction != null) { backAction.run(); return; }
        if (inWeb && web.canGoBack()) web.goBack();
        else if (inWeb) showTab(tab);
        else if (flow != null) finishFlow(false);
        else if (tab != TAB_HOME && (peopleSession || operator)) showTab(TAB_HOME);
        else finish();
    }
    /** After unlocking: an add or edit flow keeps its answers; tabs reload their data. */
    private void resumeScreen() {
        if (flow != null && peopleSession) { showNav(false); flow.show(); }
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
        if (vault.exists() || peopleSession || operator) {
            // Without a screen lock the return cannot be protected, so sign out, except while
            // the user is in a file picker, Save dialog or print screen this app opened.
            if (!vault.deviceSecured()) { if (!externalTask) signOut(); return; }
            epoch++; busy = false;
            showLock("Unlock to continue.");
            web.stopLoading(); web.onPause(); web.pauseTimers();
            CookieManager.getInstance().removeAllCookies(null);
        }
    }
    @Override protected void onDestroy() {
        authAttempt++;
        if (authentication != null) authentication.cancel();
        epoch++; worker.shutdownNow(); cancelExport();
        if (uploadCallback != null) uploadCallback.onReceiveValue(null);
        if (web != null) { if (web.getParent() instanceof ViewGroup parent) parent.removeView(web); web.destroy(); }
        super.onDestroy();
    }
}
