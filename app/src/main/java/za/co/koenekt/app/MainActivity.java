package za.co.koenekt.app;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.hardware.biometrics.BiometricPrompt;
import android.hardware.biometrics.BiometricManager;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.print.PrintManager;
import android.text.InputType;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native navigation and login, using the existing authenticated product workflows. */
public final class MainActivity extends Activity {
    private static final int GREEN = Color.rgb(18, 52, 41), LIME = Color.rgb(122, 193, 66);
    private static final int BG = Color.rgb(243, 246, 241), INK = Color.rgb(26, 31, 28);
    private static final int PICK_FILE = 20, SAVE_FILE = 21, DEVICE_UNLOCK = 22, MAX_EXPORT = 20 * 1024 * 1024;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Set<String> caps = new HashSet<>();
    private String workspace = AppPolicy.DEFAULT_WORKSPACE, displayName = "", role = "";
    private String notice = "", currentTitle = "Koenekt", exportNonce = "";
    private boolean operator = false, peopleSession = false, inWeb = false, busy = false;
    private int epoch = 0;
    private LinearLayout root, toolbar;
    private FrameLayout content;
    private TextView title, subtitle;
    private ProgressBar progress;
    private WebView web;
    private ValueCallback<Uri[]> uploadCallback;
    private File pendingExport;
    private String mobileScript;
    private SessionVault vault;
    private FrameLayout shell;
    private View shield;
    private boolean locked, authInProgress, foreground, setupInProgress, pinPromptActive;
    private CancellationSignal authentication;
    private Runnable afterUnlock;
    private int authAttempt, pinAttempt, pendingAuthAttempt;
    private Intent pendingFileResult;
    private int pendingFileRequest, pendingFileStatus;

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
        try { workspace = AppPolicy.workspace(workspace); } catch (Exception e) { workspace = AppPolicy.DEFAULT_WORKSPACE; }
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().removeAllCookies(ignored -> buildShell());
    }

    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private GradientDrawable rounded(int color) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(14)); return shape;
    }
    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t;
    }
    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    private void gap(LinearLayout l, int height) { View v = new View(this); l.addView(v, new LinearLayout.LayoutParams(1, dp(height))); }
    private Button button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(GREEN);
        b.setOnClickListener(v -> action.run()); b.setMinHeight(dp(48)); return b;
    }

    private void buildShell() {
        root = column(); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            root.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        toolbar = new LinearLayout(this); toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(10), dp(7), dp(10), dp(7)); toolbar.setBackgroundColor(GREEN);
        Button home = button("⌂", this::home); home.setContentDescription("Home"); home.setTextColor(Color.WHITE);
        home.setBackgroundColor(Color.TRANSPARENT); toolbar.addView(home, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout words = column(); title = text("koenekt", 22, Color.WHITE); title.setTypeface(null, Typeface.BOLD);
        subtitle = text("People · Licences · Reporter", 11, LIME); words.addView(title); words.addView(subtitle);
        toolbar.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        Button menu = button("⋮", () -> menu()); menu.setContentDescription("More options"); menu.setTextSize(25);
        menu.setTextColor(Color.WHITE); menu.setBackgroundColor(Color.TRANSPARENT);
        toolbar.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(52)));
        root.addView(toolbar); progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100); progress.setVisibility(View.GONE); root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        content = new FrameLayout(this); root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        shell = new FrameLayout(this); shell.addView(root); setContentView(shell);
        createWebView(); loginScreen(""); root.requestApplyInsets();
        if (vault.exists()) showLock("Unlock your saved sign-in.");
        if (getIntent().getData() != null) handleSetupLink(getIntent().getData());
        else if (locked) root.post(this::requestUnlock);
    }

    // All product workflows require JS. Navigation is origin-restricted; there is no native JS bridge.
    @SuppressLint("SetJavaScriptEnabled")
    private void createWebView() {
        web = new WebView(this); web.setBackgroundColor(BG); web.setSaveEnabled(false);
        WebSettings s = web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(false); s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE); s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false);
        s.setSupportMultipleWindows(true); s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setUserAgentString(s.getUserAgentString() + " KoenektAndroid/1.0.1");
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
                if ("http".equals(scheme) || "file".equals(scheme) || "content".equals(scheme)) {
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
                try { startActivityForResult(i, PICK_FILE); } catch (Exception e) { callback.onReceiveValue(null); uploadCallback = null; }
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

    private void mount(View view) {
        content.removeAllViews();
        if (view.getParent() instanceof ViewGroup parent) parent.removeView(view);
        content.addView(view, new FrameLayout.LayoutParams(-1, -1));
    }
    private LinearLayout page() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout l = column(); l.setPadding(dp(22), dp(24), dp(22), dp(28)); scroll.addView(l); mount(scroll); return l;
    }

    private EditText field(LinearLayout page, String label, String hint, int type) {
        TextView caption = text(label, 13, GREEN); caption.setTypeface(null, Typeface.BOLD); page.addView(caption);
        EditText f = new EditText(this); f.setHint(hint); f.setTextSize(16); f.setSingleLine(true); f.setInputType(type);
        f.setMinHeight(dp(54)); f.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO); page.addView(f);
        gap(page, 14); return f;
    }

    private void loginScreen(String message) {
        inWeb = false; currentTitle = "Koenekt"; title.setText(R.string.brand); subtitle.setText(R.string.welcome_subtitle);
        LinearLayout page = page(); TextView heading = text("Welcome back", 30, GREEN); heading.setTypeface(null, Typeface.BOLD); page.addView(heading);
        gap(page, 8); page.addView(text("Sign in with your Koenekt account. Your access follows your login.", 16, INK)); gap(page, 26);
        EditText user = field(page, "Username", "Your username", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText pw = field(page, "Password", "Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText code = field(page, "Authenticator code (master login)", "6-digit code, if required", InputType.TYPE_CLASS_NUMBER);
        TextView error = text(message, 14, Color.rgb(146, 48, 34)); page.addView(error); gap(page, 10);
        Button signIn = button("Sign in", () -> {}); signIn.setBackground(rounded(LIME)); signIn.setTextColor(GREEN);
        page.addView(signIn, new LinearLayout.LayoutParams(-1, dp(54)));
        signIn.setOnClickListener(v -> {
            if (busy) return;
            String origin = workspace;
            String username = user.getText().toString().trim(), password = pw.getText().toString(), otp = code.getText().toString().trim();
            if (username.isEmpty() || password.isEmpty()) { error.setText(R.string.credentials_required); return; }
            busy = true; signIn.setEnabled(false); signIn.setText(R.string.signing_in); error.setText("");
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
                        signIn.setEnabled(true); signIn.setText(R.string.sign_in); error.setText(msg); return;
                    }
                    operator = gotAdmin; peopleSession = gotPeople; applyPerson(account);
                    notice = msg.isEmpty() ? "" : msg + "\nAvailable sections are shown below. Sign in again to retry the other service.";
                    getPreferences(MODE_PRIVATE).edit().putString("workspace", workspace).apply();
                    rememberSignIn();
                });
            });
        });
        gap(page, 22); page.addView(text("Sign in once, then reopen with your fingerprint or device PIN. Master access also opens licences, customers and Reporter updates.", 13, Color.DKGRAY));
        page.addView(button("About & privacy", this::about));
    }

    private void applyPerson(JSONObject person) {
        caps.clear(); role = person == null ? "" : person.optString("role");
        displayName = person == null ? "Koenekt management" : person.optString("display_name", person.optString("username"));
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
        web.stopLoading(); web.onPause(); inWeb = false; currentTitle = "Koenekt";
        title.setText(R.string.brand); subtitle.setText(operator ? "Management" : "Your account");
        LinearLayout page = page(); TextView welcome = text(displayName, 27, GREEN); welcome.setTypeface(null, Typeface.BOLD); page.addView(welcome);
        gap(page, 18);
        if (!notice.isEmpty()) { page.addView(text(notice, 14, Color.rgb(129, 82, 14))); gap(page, 18); }
        List<AppPolicy.Feature> features = AppPolicy.features(caps, operator);
        if (features.isEmpty()) page.addView(text("Your account has no enabled sections yet. Ask your workspace administrator to grant company access and permissions.", 16, INK));
        for (AppPolicy.Feature feature : features) {
            LinearLayout card = column(); card.setPadding(dp(18), dp(17), dp(18), dp(17)); card.setBackground(rounded(Color.WHITE));
            TextView name = text(feature.name() + "  ›", 19, GREEN); name.setTypeface(null, Typeface.BOLD); card.addView(name);
            gap(card, 5); card.addView(text(feature.description(), 14, Color.DKGRAY));
            card.setFocusable(true); card.setClickable(true); card.setContentDescription(feature.name() + ". " + feature.description());
            card.setOnClickListener(v -> open(feature)); page.addView(card, new LinearLayout.LayoutParams(-1, -2)); gap(page, 12);
        }
        gap(page, 8); page.addView(button("Refresh access", this::refreshAccess));
        page.addView(button("Sign out", this::confirmSignOut));
    }

    private void open(AppPolicy.Feature feature) {
        if (locked) return;
        String url = feature.path().startsWith("https:") ? feature.path() : workspace + feature.path();
        if (!trusted(url)) return;
        inWeb = true; currentTitle = feature.name(); title.setText(currentTitle); subtitle.setText(getString(R.string.workspace_user, displayName));
        mount(web); web.onResume(); web.loadUrl(url);
    }

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
                busy = false; peopleSession = p != null; operator = a; applyPerson(p); notice = m.trim(); home();
            });
        });
    }

    private void menu() {
        if (locked) return;
        PopupMenu p = new PopupMenu(this, toolbar.getChildAt(toolbar.getChildCount()-1));
        p.getMenu().add("Home").setOnMenuItemClickListener(i -> { home(); return true; });
        if (inWeb) {
            p.getMenu().add("Refresh page").setOnMenuItemClickListener(i -> { web.reload(); return true; });
            p.getMenu().add("Print / save page as PDF").setOnMenuItemClickListener(i -> {
                if (trusted(web.getUrl())) ((PrintManager) getSystemService(PRINT_SERVICE)).print(currentTitle, web.createPrintDocumentAdapter(currentTitle), null);
                return true;
            });
        }
        p.getMenu().add("About & privacy").setOnMenuItemClickListener(i -> { about(); return true; });
        if (peopleSession || operator) p.getMenu().add("Sign out").setOnMenuItemClickListener(i -> { confirmSignOut(); return true; });
        p.show();
    }

    private void about() {
        new AlertDialog.Builder(this).setTitle("Koenekt 1.0.1")
                .setMessage("People, licences and Reporter updates.\n\nThis app connects to your existing Koenekt services over HTTPS. Server permissions determine what you can view and change.\n\nPasswords and authenticator codes are not saved. Remembered session tokens are encrypted using Android Keystore and require device authentication to restore. Unlock with a strong biometric such as fingerprint, or the phone's PIN, pattern or password. Android 8 uses the device credential. Sign out forgets the saved login. Expired or revoked server sessions require a new Koenekt sign-in.\n\nStaff, attendance, payroll and licence information stays with your Koenekt service. Exported reports are saved only to a location you select. The app has no advertising or analytics SDKs.\n\nFor account access, correction or removal, contact your organisation's administrator. Removing a login does not erase employment records that the organisation must retain.\n\nAn internet connection is required. Phone biometrics unlock this app; staff fingerprint/face enrollment still uses the connected clocks.")
                .setPositiveButton("Close", null).show();
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
        web.stopLoading(); web.loadUrl("about:blank"); web.clearHistory(); web.clearCache(true); web.clearFormData();
        WebStorage.getInstance().deleteAllData(); cancelExport();
        if (uploadCallback != null) { uploadCallback.onReceiveValue(null); uploadCallback = null; }
        // The licence site's logout endpoint revokes all operator sessions; local sign-out must not do that.
        CookieManager.getInstance().removeAllCookies(ignored -> loginScreen(""));
    }

    private static final class SessionRejected extends Exception {
        SessionRejected(String message) { super(message); }
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
        LinearLayout panel = column(); panel.setPadding(dp(28), dp(70), dp(28), dp(36));
        panel.setGravity(Gravity.CENTER_VERTICAL); panel.setBackgroundColor(BG);
        TextView brand = text("koenekt", 34, GREEN); brand.setTypeface(null, Typeface.BOLD); panel.addView(brand);
        gap(panel, 16); panel.addView(text(message, 17, INK)); gap(panel, 26);
        panel.addView(button("Unlock with fingerprint or PIN", this::requestUnlock));
        panel.addView(button("Sign out", () -> {
            new AlertDialog.Builder(this).setTitle("Forget this saved login?")
                    .setPositiveButton("Sign out", (d,w) -> signOut()).setNegativeButton("Cancel", null).show();
        }));
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
                boolean resumeFile = pendingFileRequest != 0;
                releaseLock();
                if (!resumeFile) home();
            });
        });
        // Wait for both asynchronous CookieManager writes before making native requests.
        int[] remaining = { (p.isEmpty() ? 0 : 1) + (a.isEmpty() ? 0 : 1) };
        ValueCallback<Boolean> installed = ok -> {
            if (attempt != epoch || isDestroyed()) return;
            if (!ok) { busy = false; signOut(); toast("Saved session could not be restored. Sign in again."); return; }
            if (--remaining[0] == 0) verify.run();
        };
        if (!p.isEmpty()) CookieManager.getInstance().setCookie(workspace + "/", p + "; Path=/; Secure; HttpOnly; SameSite=Lax", installed);
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
        c.setRequestProperty("Accept", "application/json"); c.setRequestProperty("User-Agent", "KoenektAndroid/1.0.1");
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
                if (status == 401) throw new SessionRejected("Sign-in failed or expired. Check your password and authenticator code.");
                if (status == 403) throw new SessionRejected("This account does not have access.");
                if (status == 429) throw new Exception("Too many attempts. Wait 15 minutes before retrying.");
                throw new Exception("Service unavailable (" + status + "). Try again later.");
            }
            try (InputStream in = c.getInputStream()) { return new JSONObject(new String(readLimited(in, 4 * 1024 * 1024), StandardCharsets.UTF_8)); }
        } finally { c.disconnect(); }
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
            startActivityForResult(i, SAVE_FILE);
        } catch (Exception e) { cancelExport(); toast("Could not save file. " + friendly(e)); }
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

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    private void showError(String message) {
        if (locked) return;
        progress.setVisibility(View.GONE);
        new AlertDialog.Builder(this).setTitle("Connection unavailable").setMessage(message)
                .setPositiveButton("Retry", (d,w) -> web.reload()).setNegativeButton("Home", (d,w) -> home()).show();
    }
    private void handleBack() {
        if (locked) { finish(); return; }
        if (inWeb && web.canGoBack()) web.goBack(); else if (inWeb) home(); else finish();
    }
    // API 33+ uses the registered OnBackInvokedCallback; older Android uses this callback.
    @SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { handleBack(); }
    @Override protected void onPause() { super.onPause(); if (web != null) web.onPause(); }
    @Override protected void onResume() {
        super.onResume(); foreground = true;
        if (pendingAuthAttempt != 0) { int attempt = pendingAuthAttempt; pendingAuthAttempt = 0; authenticated(attempt); }
        else if (locked && root != null) root.post(this::requestUnlock);
        else if (web != null && inWeb) web.onResume();
    }
    @Override protected void onStop() {
        super.onStop(); foreground = false;
        if (authInProgress || root == null) return;
        if (vault.exists() || peopleSession || operator) {
            epoch++; busy = false;
            if (!vault.deviceSecured()) { signOut(); return; }
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
