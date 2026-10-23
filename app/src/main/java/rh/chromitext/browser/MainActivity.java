package rh.chromitext.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.HorizontalScrollView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ConsoleMessage;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.JsResult;
import android.webkit.JsPromptResult;
import android.webkit.PermissionRequest;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.URLUtil;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.PopupWindow;
import android.widget.Switch;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class MainActivity extends Activity implements ExtensionHost, AddressSuggestionController.Host, BrowserLibraryPagesController.Host {
    private static final int REQUEST_INSTALL_ZIP = 7101;
    private static final int REQUEST_WEB_FILE = 7102;
    private static final String DEBUG_NETWORK_PREFIX = "__CHROMITEXT_NET__";
    private static final int REQUEST_STORAGE_PERMISSION = 7103;
    int COLOR_PANEL = Color.rgb(214, 211, 204);
    int COLOR_BUTTON = Color.rgb(235, 232, 224);
    int COLOR_BORDER = Color.rgb(139, 136, 128);
    int COLOR_TEXT = Color.rgb(30, 30, 30);
    int COLOR_ACCENT = Color.rgb(36, 92, 132);
    boolean darkTheme;
    int configuredSearchHeight = 42;
    int configuredToolbarHeight = 42;
    int configuredIconSize = 15;
    int configuredToolbarGap = 1;
    boolean searchAtBottom;

    private LinearLayout rootLayout;
    private FrameLayout appFrame;
    private FrameLayout browserLayer;
    private View activePageView;
    private boolean replaceNextPage;
    private final ArrayList<View> pageStack = new ArrayList<View>();
    private BrowserDebuggerOverlay debuggerOverlay;
    private BrowserPageLayout browserPageLayout;
    boolean debuggerEnabled;
    private boolean customViewActive;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private PermissionRequest pendingWebPermission;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;
    private static final int REQUEST_WEB_PERMISSION = 7120;
    private static final int REQUEST_GEO_PERMISSION = 7121;
    private EditText addressBar;
    private TextView securityLockButton;
    private TextView clearAddressButton;
    private LinearLayout addressRowView;
    private AddressSuggestionController addressSuggestionController;
    private BrowserLibraryPagesController libraryPagesController;
    private BrowserSettingsUiController settingsUiController;
    private FrameLayout webFrame;
    private TextView statusView;
    private TextView tabSummary;
    final List<BrowserTab> tabs = new ArrayList<BrowserTab>();
    private final List<RequestBlockRule> requestBlockRules = new CopyOnWriteArrayList<RequestBlockRule>();
    private final List<RequestObserverRule> requestObservers = new CopyOnWriteArrayList<RequestObserverRule>();
    private final List<NavigationObserverRule> navigationObservers = new CopyOnWriteArrayList<NavigationObserverRule>();
    private final List<NavigationBlockRule> navigationBlockRules = new CopyOnWriteArrayList<NavigationBlockRule>();
    private final List<CrossDomainNavigationBlockRule> crossDomainNavigationBlockRules = new CopyOnWriteArrayList<CrossDomainNavigationBlockRule>();
    private final List<UserAgentRule> userAgentRules = new CopyOnWriteArrayList<UserAgentRule>();
    private final List<ScriptRule> scriptRules = new CopyOnWriteArrayList<ScriptRule>();
    private final List<BridgeRegistration> bridgeRegistrations = new CopyOnWriteArrayList<BridgeRegistration>();
    private final Map<Integer, LocalHttpServer> localServers = new HashMap<Integer, LocalHttpServer>();
    private final Map<Integer, String> localServerOwners = new HashMap<Integer, String>();
    private final List<Object> beanShellInterpreters = new ArrayList<Object>();
    private ExtensionManager extensionManager;
    BrowserTab activeTab;
    private ValueCallback<Uri[]> fileChooserCallback;
    private ExtensionManager.PreparedInstall pendingInstall;
    BrowserDatabase database;
    private volatile boolean extensionRuntimeLoaded;
    private volatile boolean activityDestroyed;
    private volatile int extensionRuntimeGeneration;
    private final Object extensionRuntimeProcessingLock = new Object();
    private int activeBackgroundWorkCount;
    private boolean databaseClosed;
    String globalUserAgentMode = "default";
    String customUserAgent = "";
    boolean javascriptEnabled = true;

    private static class RequestBlockRule {
        String extensionId;
        String domain;
    }

    private static class RequestObserverRule {
        String extensionId;
        String domain;
        ExtensionRequestHandler handler;
    }

    private static class NavigationObserverRule {
        String extensionId;
        String domain;
        ExtensionNavigationHandler handler;
    }

    private static class NavigationBlockRule {
        String extensionId;
        String domain;
    }

    private static class CrossDomainNavigationBlockRule {
        String extensionId;
    }

    private static class UserAgentRule {
        String extensionId;
        String domain;
        String userAgent;
    }

    private static class ScriptRule {
        String extensionId;
        String domain;
        String path;
    }

    private static class BridgeRegistration {
        String extensionId;
        String method;
        String origin;
        ExtensionBridgeHandler handler;
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        database = new BrowserDatabase(this);
        database.migrateLegacyPreferences(this);
        LanguageManager.load(this, database.getSetting("language", "id_ID"));
        String themeMode = database.getSetting("ui_theme", "system");
        boolean systemDark = false;
        if (Build.VERSION.SDK_INT >= 29) {
            systemDark = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }
        darkTheme = "dark".equals(themeMode) || ("system".equals(themeMode) && systemDark);
        setTheme(darkTheme ? R.style.AppThemeDark : R.style.AppTheme);
        applyPalette();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(darkTheme ? Color.rgb(18, 20, 24) : Color.rgb(36, 78, 107));
            getWindow().setNavigationBarColor(COLOR_PANEL);
            getWindow().getDecorView().setSystemUiVisibility(!darkTheme && Build.VERSION.SDK_INT >= 26 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
        }
        globalUserAgentMode = database.getSetting("user_agent_mode", "default");
        customUserAgent = database.getSetting("user_agent_custom", "");
        debuggerEnabled = database.getBoolean("debugger_enabled", false);
        javascriptEnabled = database.getBoolean("javascript_enabled", true);
        searchAtBottom = "bottom".equals(database.getSetting("ui_search_position", "top"));
        configuredSearchHeight = readIntSetting("ui_search_height", 42, 34, 54);
        configuredToolbarHeight = readIntSetting("ui_toolbar_height", 42, 34, 54);
        configuredIconSize = readIntSetting("ui_icon_size", 15, 12, 22);
        configuredToolbarGap = readIntSetting("ui_toolbar_gap", 1, 0, 5);
        extensionManager = new ExtensionManager(this, database);
        addressSuggestionController = new AddressSuggestionController(this);
        libraryPagesController = new BrowserLibraryPagesController(this);
        settingsUiController = new BrowserSettingsUiController(this);
        buildUi();
        restoreSessionOrStart();
        // Extension scripts are initialized off the UI thread to avoid ANR/crash on slow OEM devices.
        loadExtensions();
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
        }
    }

    private void applyPalette() {
        if (darkTheme) {
            COLOR_PANEL = Color.rgb(22, 25, 30);
            COLOR_BUTTON = Color.rgb(37, 42, 50);
            COLOR_BORDER = Color.rgb(63, 70, 81);
            COLOR_TEXT = Color.rgb(232, 236, 242);
            COLOR_ACCENT = Color.rgb(49, 119, 170);
        } else {
            COLOR_PANEL = Color.rgb(244, 244, 244);
            COLOR_BUTTON = Color.rgb(232, 232, 232);
            COLOR_BORDER = Color.rgb(190, 190, 190);
            COLOR_TEXT = Color.rgb(32, 33, 36);
            COLOR_ACCENT = Color.rgb(36, 92, 132);
        }
    }

    private int readIntSetting(String key, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(database.getSetting(key, String.valueOf(fallback)));
            return Math.max(min, Math.min(max, value));
        } catch (Exception ignored) { return fallback; }
    }

    private void restoreSessionOrStart() {
        List<String> saved = database.getSessionUrls();
        if (saved == null || saved.size() == 0) {
            addTab(false);
            return;
        }
        int activeIndex = database.getSessionActiveIndex();
        for (int i = 0; i < saved.size(); i++) {
            String url = saved.get(i);
            addTab(true);
            if (url != null && url.length() > 0 && !"about:blank".equals(url)) navigate(url);
        }
        if (tabs.size() > 0) {
            int safeIndex = Math.max(0, Math.min(tabs.size() - 1, activeIndex));
            setActiveTab(tabs.get(safeIndex));
        } else addTab(false);
    }

    private void saveSession() {
        if (database == null) return;
        ArrayList<String> urls = new ArrayList<String>();
        int activeIndex = 0;
        for (int i = 0; i < tabs.size(); i++) {
            BrowserTab tab = tabs.get(i);
            String url = tab == null ? null : tab.url;
            if (url == null || url.length() == 0) url = "about:blank";
            urls.add(url);
            if (tab == activeTab) activeIndex = i;
        }
        database.saveSessionUrls(urls, activeIndex);
    }

    private int dpForUi(float value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }

    private void buildUi() {
        browserPageLayout = new BrowserPageLayout(this, COLOR_PANEL, COLOR_BUTTON, COLOR_BORDER, COLOR_TEXT);
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(COLOR_PANEL);
        rootLayout.setPadding(dp(4), dp(4), dp(4), dp(3));

        addressRowView = new LinearLayout(this);
        final LinearLayout addressRow = addressRowView;
        addressRow.setOrientation(LinearLayout.HORIZONTAL);
        securityLockButton = new TextView(this);
        securityLockButton.setTextSize(17);
        securityLockButton.setGravity(Gravity.CENTER);
        securityLockButton.setTextColor(COLOR_TEXT);
        securityLockButton.setSingleLine(true);
        securityLockButton.setPadding(dp(5), 0, dp(5), 0);
        securityLockButton.setBackground(boxBackground(COLOR_BUTTON, COLOR_BORDER));
        securityLockButton.setContentDescription(LanguageManager.get("accessibility.connection_security", "Informasi keamanan koneksi"));
        securityLockButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showConnectionSecurityInfo(); }
        });
        addressRow.addView(securityLockButton, new LinearLayout.LayoutParams(dp(32), dp(configuredSearchHeight)));
        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setTextSize(Math.max(12, configuredIconSize - 1));
        addressBar.setTextColor(COLOR_TEXT);
        addressBar.setHintTextColor(darkTheme ? Color.rgb(160, 166, 174) : Color.rgb(100, 100, 100));
        addressBar.setHint(LanguageManager.get("placeholder.search.bar", "Cari atau masukkan URL"));
        addressBar.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setPadding(dp(9), 0, dp(7), 0);
        addressBar.setSelectAllOnFocus(true);
        updateSecurityIndicator("about:blank");
        addressBar.setBackground(boxBackground(darkTheme ? Color.rgb(29, 33, 39) : Color.rgb(255, 255, 255), COLOR_BORDER));
        addressRow.addView(addressBar, new LinearLayout.LayoutParams(0, dp(configuredSearchHeight), 1));
        clearAddressButton = new TextView(this);
        clearAddressButton.setText("×");
        clearAddressButton.setTextSize(24);
        clearAddressButton.setTextColor(COLOR_TEXT);
        clearAddressButton.setGravity(Gravity.CENTER);
        clearAddressButton.setSingleLine(true);
        clearAddressButton.setContentDescription(LanguageManager.get("accessibility.clear_address", "Kosongkan address bar"));
        clearAddressButton.setBackground(boxBackground(COLOR_BUTTON, COLOR_BORDER));
        clearAddressButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(dp(Math.max(30, configuredSearchHeight - 4)), dp(configuredSearchHeight));
        clearParams.leftMargin = dp(3);
        addressRow.addView(clearAddressButton, clearParams);
        TextView goButton = iconButton("solid", 0xf061, "go", "go");
        goButton.setContentDescription(LanguageManager.get("accessibility.open_address", "Buka Alamat"));
        LinearLayout.LayoutParams goParams = new LinearLayout.LayoutParams(dp(configuredSearchHeight), dp(configuredSearchHeight));
        goParams.leftMargin = dp(3);
        addressRow.addView(goButton, goParams);
        

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setPadding(0, dp(2), 0, dp(2));
        toolbar.addView(iconButton("solid", 0xf060, "back", "back"), weightedButtonParams());
        toolbar.addView(iconButton("solid", 0xf061, "forward", "forward"), weightedButtonParams());
        toolbar.addView(iconButton("solid", 0xf2f1, "reload", "reload"), weightedButtonParams());
        toolbar.addView(iconButton("solid", 0xf015, "home", "home"), weightedButtonParams());
        toolbar.addView(iconButton("solid", 0xf02e, "★", "bookmark"), weightedButtonParams());
        toolbar.addView(iconButton("solid", 0xf067, "tab", "tab"), weightedButtonParams());
        toolbar.addView(iconButton("solid", 0xf0c9, "menu", "menu"), weightedButtonParams());
        

        webFrame = new FrameLayout(this);
        webFrame.setBackgroundColor(darkTheme ? Color.rgb(16, 17, 19) : Color.WHITE);
        if (searchAtBottom) {
            rootLayout.addView(webFrame, new LinearLayout.LayoutParams(-1, 0, 1));
            rootLayout.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(configuredToolbarHeight + 4)));
            rootLayout.addView(addressRow, new LinearLayout.LayoutParams(-1, dp(configuredSearchHeight)));
        } else {
            rootLayout.addView(addressRow, new LinearLayout.LayoutParams(-1, dp(configuredSearchHeight)));
            rootLayout.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(configuredToolbarHeight + 4)));
            rootLayout.addView(webFrame, new LinearLayout.LayoutParams(-1, 0, 1));
        }

        // Status is retained as an internal status sink, but no status/tab strip
        // is rendered so the WebView receives the full remaining screen.
        tabSummary = new TextView(this);
        statusView = new TextView(this);
        statusView.setText(LanguageManager.get("status.siap", "Siap"));

        appFrame = new FrameLayout(this);
        browserLayer = new FrameLayout(this);
        browserLayer.addView(rootLayout, new FrameLayout.LayoutParams(-1, -1));
        appFrame.addView(browserLayer, new FrameLayout.LayoutParams(-1, -1));
        setContentView(appFrame);
        createDebuggerOverlayButton();
        refreshDebuggerOverlayVisibility();
        goButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) { navigateFromAddressBar(); }
        });
        addressBar.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    navigateFromAddressBar();
                    return true;
                }
                return false;
            }
        });
        addressBar.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View view, boolean hasFocus) {
                if (hasFocus && browserLayer.getVisibility() == View.VISIBLE) {
                    addressSuggestionController.schedule(addressBar.getText().toString(), 0);
                } else {
                    addressSuggestionController.hide();
                }
            }
        });
        addressBar.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { }
            public void afterTextChanged(Editable value) {
                String text = value == null ? "" : value.toString();
                if (clearAddressButton != null) clearAddressButton.setVisibility(text.length() > 0 ? View.VISIBLE : View.GONE);
                if (addressBar.hasFocus() && browserLayer.getVisibility() == View.VISIBLE) {
                    addressSuggestionController.schedule(text, 300);
                } else {
                    addressSuggestionController.hide();
                }
            }
        });
        clearAddressButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                addressBar.setText("");
                addressBar.setSelection(0);
                addressBar.requestFocus();
                InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (manager != null) manager.showSoftInput(addressBar, InputMethodManager.SHOW_IMPLICIT);
                addressSuggestionController.schedule("", 0);
            }
        });
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(Math.max(28, configuredToolbarHeight)), 1);
        params.leftMargin = dp(configuredToolbarGap);
        params.rightMargin = dp(configuredToolbarGap);
        return params;
    }

    private TextView iconButton(String family, int codePoint, String fallback, final String action) {
        final TextView button = new TextView(this);
        FontAwesome.apply(this, button, family, codePoint, fallback);
        button.setTextColor(COLOR_TEXT);
        button.setTextSize(configuredIconSize);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setBackground(boxBackground(COLOR_BUTTON, COLOR_BORDER));
        button.setClickable(true);
        button.setFocusable(true);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                if ("back".equals(action)) {
                    if (activeTab != null && activeTab.webView.canGoBack()) activeTab.webView.goBack();
                } else if ("forward".equals(action)) {
                    if (activeTab != null && activeTab.webView.canGoForward()) activeTab.webView.goForward();
                } else if ("reload".equals(action)) {
                    if (activeTab != null) activeTab.webView.reload();
                } else if ("home".equals(action)) {
                    navigate(database.getSetting("homepage", "https://www.google.com/"));
                } else if ("bookmark".equals(action)) {
                    addCurrentBookmark();
                } else if ("tab".equals(action)) {
                    showTabsDialog();
                } else if ("menu".equals(action)) {
                    showMainMenu();
                } else if ("go".equals(action)) {
                    navigateFromAddressBar();
                }
            }
        });
        return button;
    }

    GradientDrawable boxBackground(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(0);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    public Activity libraryActivity() { return this; }
    public BrowserDatabase libraryDatabase() { return database; }
    public BrowserTab libraryActiveTab() { return activeTab; }
    public LinearLayout openLibraryPage(String title) { return openPage(title); }
    public void addLibraryAction(LinearLayout body, int iconCode, String title, String description, View.OnClickListener listener) { addPageAction(body, iconCode, title, description, listener); }
    public void addLibraryNote(LinearLayout body, String value) { addPageNote(body, value); }
    public void navigateFromLibrary(String url) { navigate(url); }
    public void backFromLibraryPage() { backFromPage(); }
    public void replaceLibraryPage() { replaceCurrentPage(); }
    public void showBookmarksPage() { showBookmarks(); }
    public void showHistoryPage() { showHistory(); }
    public void showLibraryToast(String message) { showToast(message); }

    public Activity suggestionActivity() { return this; }
    public BrowserDatabase suggestionDatabase() { return database; }
    public EditText suggestionAddressBar() { return addressBar; }
    public View suggestionBrowserLayer() { return browserLayer; }
    public View suggestionAnchor() { return addressRowView; }
    public boolean suggestionIsDestroyed() { return activityDestroyed; }
    public boolean suggestionIsDarkTheme() { return darkTheme; }
    public int suggestionPanelColor() { return COLOR_PANEL; }
    public int suggestionBorderColor() { return COLOR_BORDER; }
    public int suggestionTextColor() { return COLOR_TEXT; }
    public int suggestionDp(float value) { return dp(value); }
    public GradientDrawable suggestionBackground(int fill, int stroke) { return boxBackground(fill, stroke); }
    public void navigateFromSuggestion(String url) { navigate(url); }
    public void hideKeyboardForSuggestion() { hideKeyboard(); }

    private BrowserTab addTab(boolean blank) {
        final BrowserTab tab = new BrowserTab();
        final WebView web = new WebView(this);
        tab.webView = web;
        configureWebView(tab);
        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(-1, -1);
        webFrame.addView(web, frameParams);
        tabs.add(tab);
        setActiveTab(tab);
        updateTabSummary();
        if (!blank) navigate(database.getSetting("homepage", "https://www.google.com/"));
        return tab;
    }

    private void configureWebView(final BrowserTab tab) {
        final WebView web = tab.webView;
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(javascriptEnabled);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setUseWideViewPort("desktop".equals(globalUserAgentMode));
        settings.setLoadWithOverviewMode("desktop".equals(globalUserAgentMode));
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        if (Build.VERSION.SDK_INT >= 21) settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        web.setBackgroundColor(Color.WHITE); // Native UI theme never forces a dark theme on website content.
        web.addJavascriptInterface(new NativeBridge(MainActivity.this, tab), "ChromitextBridge");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return processNavigation(tab, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (Build.VERSION.SDK_INT >= 21) return processNavigation(tab, request.getUrl().toString());
                return processNavigation(tab, request.getUrl().toString());
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                tab.url = url == null ? "about:blank" : url;
                tab.bridgeUrl = tab.url;
                tab.sslCertificateError = false;
                tab.loading = true;
                if (activeTab == tab) {
                    addressBar.setText(tab.url);
                    updateSecurityIndicator(tab.url);
                    statusView.setText(LanguageManager.get("status.loading_prefix", "Memuat: ") + tab.url);
                }
                updateTabSummary();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                tab.url = url == null ? tab.url : url;
                tab.loading = false;
                if (activeTab == tab) {
                    addressBar.setText(tab.url);
                    updateSecurityIndicator(tab.url);
                    statusView.setText(LanguageManager.get("status.selesai.dimuat", "selesai dimuat"));
                }
                saveHistory(tab.title, tab.url);
                rememberCurrentOrigin(tab.url);
                syncNetworkDebuggerHooks();
                injectScripts(tab);
                updateTabSummary();
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return interceptRequest(tab, url, "GET", null);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String method = request == null ? "GET" : request.getMethod();
                Map<String, String> headers = request == null ? null : request.getRequestHeaders();
                String url = request == null ? "" : request.getUrl().toString();
                return interceptRequest(tab, url, method, headers);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                addDebuggerConsole(tab, "[web error] " + errorCode + " " + description + " | " + failingUrl);
                if (activeTab == tab) statusView.setText(LanguageManager.get("status.error_prefix", "Error: ") + description);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (Build.VERSION.SDK_INT >= 23 && error != null) {
                    addDebuggerConsole(tab, "[web error] " + error.getErrorCode() + " " + error.getDescription());
                    if (request != null && request.isForMainFrame() && activeTab == tab) {
                        statusView.setText(LanguageManager.get("status.error_prefix", "Error: ") + error.getDescription());
                    }
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                addDebuggerConsole(tab, "[ssl error] koneksi dibatalkan: " + String.valueOf(error));
                handler.cancel();
                boolean mainHostCertificateError = false;
                try {
                    String pageHost = Uri.parse(tab.url == null ? "" : tab.url).getHost();
                    String errorHost = error == null ? null : Uri.parse(error.getUrl()).getHost();
                    mainHostCertificateError = pageHost != null && errorHost != null && pageHost.equalsIgnoreCase(errorHost);
                } catch (Exception ignored) { }
                if (mainHostCertificateError) tab.sslCertificateError = true;
                if (activeTab == tab) {
                    updateSecurityIndicator(tab.url);
                    statusView.setText(LanguageManager.get("status.sertifikat.ssl.tidak.valid", "sertifikat ssl tidak valid"));
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                tab.title = title == null || title.length() == 0 ? LanguageManager.get("web.untitled", "tanpa judul") : title;
                updateTabSummary();
            }

            @Override
            public void onProgressChanged(WebView view, int progress) {
                if (activeTab == tab && tab.loading) statusView.setText(LanguageManager.get("status.loading", "Memuat...") + " " + progress + "%");
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                String rawMessage = message.message() == null ? "" : message.message();
                if (rawMessage.startsWith(DEBUG_NETWORK_PREFIX)) {
                    if (debuggerEnabled && database != null && database.isDebuggerModuleEnabled("Network")) {
                        String payload = rawMessage.substring(DEBUG_NETWORK_PREFIX.length());
                        if (payload.length() > 24000) payload = payload.substring(0, 24000) + "…";
                        tab.addNetwork("[fetch/xhr] " + payload);
                        if (activeTab == tab) runOnUiThread(new Runnable() { public void run() { if (activeTab == tab) refreshDebuggerPanel(); } });
                    }
                    return true;
                }
                String sourceId = message.sourceId() == null ? "" : message.sourceId();
                String line = "[" + message.messageLevel().name() + "] " + rawMessage +
                    " (" + sourceId + ":" + message.lineNumber() + ")";
                addDebuggerConsole(tab, line);
                String extensionId = findExtensionIdForSource(sourceId);
                if (extensionId != null) extensionLog(extensionId, "[javascript] " + line);
                if (activeTab == tab) statusView.setText(LanguageManager.get("status.console_prefix", "Console: ") + rawMessage);
                return true;
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(final String origin, final GeolocationPermissions.Callback callback) {
                new AlertDialog.Builder(MainActivity.this).setTitle(LanguageManager.get("dialog.title.izin.lokasi", "Izin Lokasi"))
                    .setMessage(LanguageManager.format("permission.location.message", "%1$s meminta akses lokasi perangkat. Izinkan?", origin))
                    .setPositiveButton(LanguageManager.get("action.allow", "Izinkan"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int which) {
                        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            pendingGeoCallback = callback;
                            pendingGeoOrigin = origin;
                            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_GEO_PERMISSION);
                        } else callback.invoke(origin, true, false);
                    }})
                    .setNegativeButton(LanguageManager.get("action.deny", "Tolak"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int which) { callback.invoke(origin, false, false); }})
                    .setOnCancelListener(new DialogInterface.OnCancelListener() { public void onCancel(DialogInterface dialog) { callback.invoke(origin, false, false); }})
                    .show();
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                if (Build.VERSION.SDK_INT < 21) { request.deny(); return; }
                new AlertDialog.Builder(MainActivity.this).setTitle(LanguageManager.get("dialog.title.izin.website", "Izin Website"))
                    .setMessage(LanguageManager.format("permission.web.message", "%1$s meminta akses perangkat: %2$s. Izinkan?", String.valueOf(request.getOrigin()), java.util.Arrays.toString(request.getResources())))
                    .setPositiveButton(LanguageManager.get("action.allow", "Izinkan"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int which) {
                        ArrayList<String> needed = new ArrayList<String>();
                        String[] resources = request.getResources();
                        for (int i = 0; i < resources.length; i++) {
                            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resources[i]) && Build.VERSION.SDK_INT >= 23 && checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) needed.add(android.Manifest.permission.CAMERA);
                            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resources[i]) && Build.VERSION.SDK_INT >= 23 && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) needed.add(android.Manifest.permission.RECORD_AUDIO);
                        }
                        if (needed.size() > 0) {
                            pendingWebPermission = request;
                            requestPermissions(needed.toArray(new String[needed.size()]), REQUEST_WEB_PERMISSION);
                        } else request.grant(resources);
                    }})
                    .setNegativeButton(LanguageManager.get("action.deny", "Tolak"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int which) { request.deny(); }})
                    .setOnCancelListener(new DialogInterface.OnCancelListener() { public void onCancel(DialogInterface dialog) { request.deny(); }})
                    .show();
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingWebPermission == request) pendingWebPermission = null;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customViewActive) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                customViewActive = true;
                hideDebuggerPanel();
                appFrame.removeAllViews();
                appFrame.addView(view, new FrameLayout.LayoutParams(-1, -1));
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }

            @Override
            public void onHideCustomView() {
                exitCustomView(true);
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this).setTitle(LanguageManager.get("dialog.title.pesan.dari.halaman", "Pesan dari Halaman"))
                    .setMessage(message).setPositiveButton(LanguageManager.get("action.ok", "OK"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int which) { result.confirm(); }})
                    .setOnCancelListener(new DialogInterface.OnCancelListener() { public void onCancel(DialogInterface dialog) { result.cancel(); }})
                    .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this).setTitle(LanguageManager.get("dialog.title.konfirmasi.halaman", "Konfirmasi Halaman"))
                    .setMessage(message).setPositiveButton(LanguageManager.get("action.ok", "OK"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int which) { result.confirm(); }})
                    .setNegativeButton(LanguageManager.get("action.cancel", "Batal"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int which) { result.cancel(); }})
                    .setOnCancelListener(new DialogInterface.OnCancelListener() { public void onCancel(DialogInterface dialog) { result.cancel(); }})
                    .show();
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, final JsPromptResult result) {
                final EditText input = new EditText(MainActivity.this);
                input.setText(defaultValue == null ? "" : defaultValue);
                new AlertDialog.Builder(MainActivity.this).setTitle(LanguageManager.get("dialog.title.input.dari.halaman", "Input dari Halaman"))
                    .setMessage(message).setView(input)
                    .setPositiveButton(LanguageManager.get("action.submit", "Kirim"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int which) { result.confirm(input.getText().toString()); }})
                    .setNegativeButton(LanguageManager.get("action.cancel", "Batal"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int which) { result.cancel(); }})
                    .setOnCancelListener(new DialogInterface.OnCancelListener() { public void onCancel(DialogInterface dialog) { result.cancel(); }})
                    .show();
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = filePathCallback;
                try {
                    Intent intent = fileChooserParams != null ? fileChooserParams.createIntent() : new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    if (intent.getType() == null) intent.setType("*/*");
                    startActivityForResult(intent, REQUEST_WEB_FILE);
                    return true;
                } catch (ActivityNotFoundException error) {
                    if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                    fileChooserCallback = null;
                    return true;
                } catch (Exception error) {
                    if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                    fileChooserCallback = null;
                    addDebuggerConsole(tab, "[file chooser] " + error.toString());
                    return true;
                }
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                if (!isUserGesture) return false;
                BrowserTab newTab = addTab(true);
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(newTab.webView);
                resultMsg.sendToTarget();
                return true;
            }
        });
        web.setDownloadListener(new DownloadListener() {
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                startDownload(url, userAgent, contentDisposition, mimeType);
            }
        });
    }

    private void exitCustomView(boolean notifyCallback) {
        if (!customViewActive) return;
        WebChromeClient.CustomViewCallback callback = customViewCallback;
        customViewActive = false;
        customView = null;
        customViewCallback = null;
        appFrame.removeAllViews();
        browserLayer.setVisibility(activePageView == null ? View.VISIBLE : View.GONE);
        appFrame.addView(browserLayer, new FrameLayout.LayoutParams(-1, -1));
        if (activePageView != null) appFrame.addView(activePageView, new FrameLayout.LayoutParams(-1, -1));
        getWindow().getDecorView().setSystemUiVisibility(0);
        refreshDebuggerOverlayVisibility();
        if (notifyCallback && callback != null) {
            try { callback.onCustomViewHidden(); } catch (Exception ignored) { }
        }
    }

    private boolean processNavigation(BrowserTab tab, String rawUrl) {
        if (rawUrl == null || rawUrl.length() == 0) return false;
        Uri uri = Uri.parse(rawUrl);
        String scheme = uri.getScheme();
        if (scheme != null && !"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme) &&
            !"about".equalsIgnoreCase(scheme) && !"data".equalsIgnoreCase(scheme) && !"javascript".equalsIgnoreCase(scheme)) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
            catch (Exception error) { showToast(LanguageManager.get("toast.tidak.ada.aplikasi.yang.tersedia.untuk.mem", "Tidak ada aplikasi yang tersedia untuk membuka alamat ini.")); }
            return true;
        }
        boolean blocked = isNavigationBlocked(rawUrl, tab.url);
        notifyNavigationObservers(tab.url, rawUrl, blocked);
        if (blocked) {
            showBlockedPage(tab, rawUrl);
            return true;
        }
        applyUserAgent(tab, rawUrl);
        tab.bridgeUrl = "about:blank";
        return false;
    }

    private void navigateFromAddressBar() {
        if (addressBar == null) return;
        String value = addressBar.getText().toString();
        String url = UrlResolver.resolve(value);
        if (url.length() == 0) return;
        hideKeyboard();
        navigate(url);
    }

    private void navigate(String url) {
        if (activeTab == null) addTab(true);
        if (activeTab != null) activeTab.sslCertificateError = false;
        updateSecurityIndicator(url);
        // Address-bar navigation is an explicit user action; cross-domain guard only blocks page-initiated navigation.
        boolean blocked = isNavigationBlocked(url, activeTab.url, false);
        notifyNavigationObservers(activeTab.url, url, blocked);
        if (blocked) {
            showBlockedPage(activeTab, url);
            return;
        }
        applyUserAgent(activeTab, url);
        activeTab.bridgeUrl = "about:blank";
        addressBar.setText(url);
        statusView.setText(LanguageManager.get("status.meminta.halaman", "meminta halaman..."));
        try { activeTab.webView.loadUrl(url); }
        catch (Exception error) { statusView.setText(LanguageManager.get("status.open_url_error_prefix", "Gagal membuka URL: ") + error.getMessage()); }
    }

    private void showBlockedPage(BrowserTab tab, String url) {
        tab.url = url;
        tab.bridgeUrl = "about:blank";
        tab.title = LanguageManager.get("web.blocked_title", "akses diblokir");
        String safe = TextUtils.htmlEncode(url);
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{font-family:sans-serif;margin:24px;color:#222}h2{font-size:21px}code{overflow-wrap:anywhere;color:#8b2727}hr{border:0;border-top:1px solid #ccc}</style></head><body><h2>" + TextUtils.htmlEncode(LanguageManager.get("web.blocked_title", "akses diblokir")) + "</h2><p>" + TextUtils.htmlEncode(LanguageManager.get("web.blocked_description", "alamat ini diblokir oleh extension browser.")) + "</p><hr><code>" + safe + "</code><p>" + TextUtils.htmlEncode(LanguageManager.get("web.blocked_action", "ubah aturan extension untuk membuka alamat ini.")) + "</p></body></html>";
        tab.webView.loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null);
        if (activeTab == tab) {
            addressBar.setText(url);
            statusView.setText(LanguageManager.get("status.navigasi.diblokir.oleh.extension", "navigasi diblokir oleh extension"));
        }
        addDebuggerConsole(tab, "[Chromitext] navigation blocked: " + url);
    }

    private WebResourceResponse interceptRequest(final BrowserTab tab, String url, String method, Map<String, String> headers) {
        if (url == null || url.length() == 0) return null;
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) return null;
        boolean blocked = shouldBlockRequest(tab.url, url);
        String headersText = formatRequestHeaders(headers);
        if (debuggerEnabled && database != null && database.isDebuggerModuleEnabled("Network")) {
            StringBuilder line = new StringBuilder();
            line.append(method == null ? "GET" : method).append(" ").append(url);
            if (blocked) line.append("  -> BLOCKED");
            else line.append("  -> delegated to WebView (response status tidak tersedia di hook ini)");
            if (headersText.length() > 0) line.append("\nheaders: ").append(headersText);
            final String networkLine = line.toString();
            tab.addNetwork(networkLine);
            if (activeTab == tab) runOnUiThread(new Runnable() { public void run() { if (activeTab == tab) refreshDebuggerPanel(); } });
        }
        notifyRequestObservers(tab.url, url, method == null ? "GET" : method, headersText, blocked);
        if (blocked) {
            byte[] empty = new byte[0];
            ByteArrayInputStream input = new ByteArrayInputStream(empty);
            if (Build.VERSION.SDK_INT >= 21) {
                Map<String, String> responseHeaders = new HashMap<String, String>();
                responseHeaders.put("Cache-Control", "no-store");
                return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked by Chromitext extension", responseHeaders, input);
            }
            return new WebResourceResponse("text/plain", "UTF-8", input);
        }
        return null;
    }

    private String formatRequestHeaders(Map<String, String> headers) {
        if (headers == null || headers.size() == 0) return "";
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, String> item : headers.entrySet()) {
            if (count++ >= 20) { out.append("..."); break; }
            out.append(item.getKey()).append(": ").append(item.getValue()).append("; ");
        }
        return out.toString();
    }

    private void notifyRequestObservers(String pageUrl, String requestUrl, String method, String headers, boolean blocked) {
        for (int i = 0; i < requestObservers.size(); i++) {
            RequestObserverRule rule = requestObservers.get(i);
            ExtensionInfo info = extensionManager.getExtension(rule.extensionId);
            if (info == null || !extensionManager.isEnabled(info) || !extensionManager.hasPermission(info, "webRequest")) continue;
            if (!extensionManager.matchesDomainLock(info, pageUrl)) continue;
            if (!"*".equals(rule.domain) && !ExtensionManager.matchesHost(requestUrl, rule.domain, true)) continue;
            try { rule.handler.onRequest(pageUrl, requestUrl, method, headers, blocked); }
            catch (Throwable error) { extensionLog(info.id, "request observer error: " + error.toString()); }
        }
    }

    private void notifyNavigationObservers(String fromUrl, String toUrl, boolean blocked) {
        for (int i = 0; i < navigationObservers.size(); i++) {
            NavigationObserverRule rule = navigationObservers.get(i);
            ExtensionInfo info = extensionManager.getExtension(rule.extensionId);
            if (info == null || !extensionManager.isEnabled(info) || !extensionManager.hasPermission(info, "webNavigation")) continue;
            if (!extensionManager.matchesDomainLock(info, fromUrl) && !extensionManager.matchesDomainLock(info, toUrl)) continue;
            if (!"*".equals(rule.domain) && !ExtensionManager.matchesHost(toUrl, rule.domain, true)) continue;
            try { rule.handler.onNavigation(fromUrl, toUrl, blocked); }
            catch (Throwable error) { extensionLog(info.id, "navigation observer error: " + error.toString()); }
        }
    }

    private boolean shouldBlockRequest(String pageUrl, String requestUrl) {
        for (int i = 0; i < requestBlockRules.size(); i++) {
            RequestBlockRule rule = requestBlockRules.get(i);
            ExtensionInfo info = extensionManager.getExtension(rule.extensionId);
            if (info == null || !extensionManager.isEnabled(info) || !extensionManager.hasPermission(info, "webRequestBlocking")) continue;
            if (!extensionManager.matchesDomainLock(info, pageUrl)) continue;
            if (ExtensionManager.matchesHost(requestUrl, rule.domain, true)) return true;
        }
        return false;
    }

    private boolean isNavigationBlocked(String targetUrl, String sourceUrl) {
        return isNavigationBlocked(targetUrl, sourceUrl, true);
    }

    private boolean isNavigationBlocked(String targetUrl, String sourceUrl, boolean enforceCrossDomainRules) {
        if (enforceCrossDomainRules) for (int i = 0; i < crossDomainNavigationBlockRules.size(); i++) {
            CrossDomainNavigationBlockRule rule = crossDomainNavigationBlockRules.get(i);
            ExtensionInfo info = extensionManager.getExtension(rule.extensionId);
            if (info == null || !extensionManager.isEnabled(info) || !extensionManager.hasPermission(info, "navigationBlocking")) continue;
            if (!extensionManager.matchesDomainLock(info, sourceUrl)) continue;
            if (BrowserNavigationPolicy.isCrossDomainNavigation(sourceUrl, targetUrl)) return true;
        }
        for (int i = 0; i < navigationBlockRules.size(); i++) {
            NavigationBlockRule rule = navigationBlockRules.get(i);
            ExtensionInfo info = extensionManager.getExtension(rule.extensionId);
            if (info == null || !extensionManager.isEnabled(info) || !extensionManager.hasPermission(info, "navigationBlocking")) continue;
            if (!extensionManager.matchesDomainLock(info, sourceUrl) && !extensionManager.matchesDomainLock(info, targetUrl)) continue;
            if (ExtensionManager.matchesHost(targetUrl, rule.domain, true)) return true;
        }
        return false;
    }

    private void applyUserAgent(BrowserTab tab, String targetUrl) {
        if (tab == null || tab.webView == null) return;
        String selected = null;
        // Extension overrides are intentionally the only per-domain user-agent mechanism.
        for (int i = userAgentRules.size() - 1; i >= 0; i--) {
            UserAgentRule rule = userAgentRules.get(i);
            ExtensionInfo info = extensionManager.getExtension(rule.extensionId);
            if (info == null || !extensionManager.isEnabled(info) || !extensionManager.hasPermission(info, "userAgent")) continue;
            if (!extensionManager.matchesDomainLock(info, targetUrl)) continue;
            if (ExtensionManager.matchesHost(targetUrl, rule.domain, true)) { selected = rule.userAgent; break; }
        }
        if (selected == null) selected = getGlobalUserAgent();
        try {
            WebSettings settings = tab.webView.getSettings();
            settings.setUserAgentString(selected);
            boolean desktopViewport = "desktop".equals(globalUserAgentMode);
            settings.setUseWideViewPort(desktopViewport);
            settings.setLoadWithOverviewMode(desktopViewport);
        } catch (Exception error) { addDebuggerConsole(tab, "[user-agent] " + error.getMessage()); }
    }

    private String getGlobalUserAgent() {
        return UserAgentProfileManager.getGlobalUserAgent(this, globalUserAgentMode, customUserAgent);
    }

    private void injectScripts(final BrowserTab tab) {
        if (tab == null || tab.webView == null || !javascriptEnabled) return;
        final List<Injection> injections = new ArrayList<Injection>();
        List<ExtensionInfo> installed = extensionManager.getExtensions();
        for (int i = 0; i < installed.size(); i++) {
            ExtensionInfo info = installed.get(i);
            if (!extensionManager.isEnabled(info) || !extensionManager.hasPermission(info, "scripting")) continue;
            if (!extensionManager.matchesDomainLock(info, tab.url)) continue;
            for (int j = 0; j < info.javascriptEntries.size(); j++) {
                Injection injection = new Injection();
                injection.info = info;
                injection.path = info.javascriptEntries.get(j);
                injection.domain = "*";
                injections.add(injection);
            }
        }
        for (int i = 0; i < scriptRules.size(); i++) {
            ScriptRule rule = scriptRules.get(i);
            ExtensionInfo info = extensionManager.getExtension(rule.extensionId);
            if (info == null || !extensionManager.isEnabled(info) || !extensionManager.hasPermission(info, "scripting")) continue;
            if (!extensionManager.matchesDomainLock(info, tab.url)) continue;
            if ("*".equals(rule.domain) || ExtensionManager.matchesHost(tab.url, rule.domain, true)) {
                Injection injection = new Injection();
                injection.info = info;
                injection.path = rule.path;
                injection.domain = rule.domain;
                injections.add(injection);
            }
        }
        for (int i = 0; i < injections.size(); i++) {
            Injection injection = injections.get(i);
            try {
                String script = extensionManager.readScript(injection.info, injection.path);
                String sourceName = "chromitext-extension-" + encodeExtensionSourceId(injection.info.id) + "." + injection.path.replaceAll("[^A-Za-z0-9._-]", "_") + ".js";
                String wrapped = "(function(){\n" + script + "\n})();\n//# sourceURL=" + sourceName;
                String encoded = android.util.Base64.encodeToString(wrapped.getBytes("UTF-8"), android.util.Base64.NO_WRAP);
                tab.webView.evaluateJavascript("(function(){var s=atob('" + encoded + "');(0,eval)(s);})();", null);
            } catch (Exception error) {
                addDebuggerConsole(tab, "[extension " + injection.info.id + "] gagal injeksi " + injection.path + ": " + error.getMessage());
            }
        }
    }

    void syncNetworkDebuggerHooks() {
        if (database == null) return;
        boolean shouldEnable = debuggerEnabled && database.isDebuggerModuleEnabled("Network");
        for (int i = 0; i < tabs.size(); i++) {
            final BrowserTab tab = tabs.get(i);
            if (shouldEnable) injectDebuggerNetworkHooks(tab);
            else if (tab.webView != null) tab.webView.evaluateJavascript("if(window.__chromitextNetInstalled){window.__chromitextNetActive=false;}", null);
        }
    }

    private void injectDebuggerNetworkHooks(final BrowserTab tab) {
        if (tab == null || tab.webView == null || !debuggerEnabled || database == null || !database.isDebuggerModuleEnabled("Network")) return;
        String js = BrowserNetworkInstrumentation.buildJavaScript();
        try { tab.webView.evaluateJavascript(js, null); }
        catch (Exception error) { addDebuggerConsole(tab, "[ERROR] [network debugger] failed to install hook: " + error.getMessage()); }
    }

    private String encodeExtensionSourceId(String extensionId) {
        StringBuilder out = new StringBuilder();
        try {
            byte[] bytes = extensionId.getBytes("UTF-8");
            final char[] hex = "0123456789abcdef".toCharArray();
            for (int i = 0; i < bytes.length; i++) {
                int value = bytes[i] & 255;
                out.append(hex[(value >> 4) & 15]).append(hex[value & 15]);
            }
        } catch (Exception ignored) {
            return extensionId.replaceAll("[^A-Za-z0-9]", "_");
        }
        return out.toString();
    }

    private String findExtensionIdForSource(String sourceId) {
        if (sourceId == null || sourceId.length() == 0 || extensionManager == null) return null;
        List<ExtensionInfo> installed = extensionManager.getExtensions();
        for (int i = 0; i < installed.size(); i++) {
            ExtensionInfo info = installed.get(i);
            String prefix = "chromitext-extension-" + encodeExtensionSourceId(info.id) + ".";
            if (sourceId.startsWith(prefix)) return info.id;
        }
        return null;
    }

    private static class Injection {
        ExtensionInfo info;
        String path;
        String domain;
    }

    private void setActiveTab(BrowserTab tab) {
        activeTab = tab;
        for (int i = 0; i < tabs.size(); i++) {
            BrowserTab item = tabs.get(i);
            if (item.webView != null) item.webView.setVisibility(item == tab ? View.VISIBLE : View.GONE);
        }
        if (tab != null) {
            addressBar.setText(tab.url.startsWith("about:blank") ? "" : tab.url);
            statusView.setText(tab.loading ? LanguageManager.get("status.loading", "Memuat...") : LanguageManager.get("status.ready", "Siap"));
            if (tab.extensionReloadPending && tab.webView != null && !tab.url.startsWith("about:blank")) {
                tab.extensionReloadPending = false;
                tab.webView.reload();
            }
        }
        updateTabSummary();
    }

    private void closeActiveTab() {
        if (activeTab == null) return;
        BrowserTab removing = activeTab;
        int index = tabs.indexOf(removing);
        tabs.remove(removing);
        if (removing.webView != null) {
            webFrame.removeView(removing.webView);
            try { removing.webView.stopLoading(); removing.webView.removeJavascriptInterface("ChromitextBridge"); removing.webView.destroy(); }
            catch (Exception ignored) { }
        }
        if (tabs.size() == 0) {
            activeTab = null;
            addTab(false);
        } else {
            if (index >= tabs.size()) index = tabs.size() - 1;
            setActiveTab(tabs.get(Math.max(0, index)));
        }
        updateTabSummary();
    }

    private void addDebuggerConsole(final BrowserTab tab, final String message) {
        if (tab != null && debuggerEnabled && database != null && database.isDebuggerModuleEnabled("Console")) {
            tab.addConsole(message);
            if (activeTab == tab) runOnUiThread(new Runnable() { public void run() { if (activeTab == tab) refreshDebuggerPanel(); } });
        }
    }

    private void updateTabSummary() {
        if (tabSummary == null) return;
        String name = activeTab == null ? "" : activeTab.title;
        tabSummary.setText(LanguageManager.get("status.tabs_prefix", "Tab: ") + tabs.size() + "  |  " + LanguageManager.get("status.active_tab_prefix", "Aktif: ") + (tabs.indexOf(activeTab) + 1) + "  |  " + name);
    }

    LinearLayout openPage(final String title) {
        addressSuggestionController.hide();
        hideDebuggerPanel();
        if (replaceNextPage) {
            replaceNextPage = false;
            if (activePageView != null) appFrame.removeView(activePageView);
            activePageView = null;
        } else {
            pageStack.add(activePageView);
        }
        BrowserPageLayout.Page page = browserPageLayout.createPage(title, new View.OnClickListener() {
            public void onClick(View view) { backFromPage(); }
        });
        browserLayer.setVisibility(View.GONE);
        if (activePageView != null) appFrame.removeView(activePageView);
        activePageView = page.root;
        appFrame.addView(activePageView, new FrameLayout.LayoutParams(-1, -1));
        refreshDebuggerOverlayVisibility();
        return page.body;
    }

    void backFromPage() {
        if (activePageView != null) appFrame.removeView(activePageView);
        activePageView = null;
        if (pageStack.size() > 0) activePageView = pageStack.remove(pageStack.size() - 1);
        if (activePageView == null) {
            browserLayer.setVisibility(View.VISIBLE);
            appFrame.bringChildToFront(browserLayer);
            refreshDebuggerOverlayVisibility();
        } else {
            browserLayer.setVisibility(View.GONE);
            appFrame.addView(activePageView, new FrameLayout.LayoutParams(-1, -1));
        }
    }

    void replaceCurrentPage() { replaceNextPage = true; }

    void addPageAction(LinearLayout body, int iconCode, String title, String description, View.OnClickListener listener) {
        browserPageLayout.addAction(body, iconCode, title, description, listener);
    }

    void addPageNote(LinearLayout body, String value) {
        browserPageLayout.addNote(body, value);
    }

    private void showMainMenu() { settingsUiController.showMainMenu(); }

    private void showSettings() { settingsUiController.showSettings(); }



    void showLanguageSettings() {
        LanguageSettingsController.show(this, database, new LanguageSettingsController.Listener() {
            public void onLanguageChanged(String languageCode) {
                rebuildBrowserUiForLanguage();
            }
        });
    }

    private void updateSecurityIndicator(String url) {
        boolean invalidCertificate = activeTab != null && url != null && url.equals(activeTab.url) && activeTab.sslCertificateError;
        ConnectionSecurityUi.update(this, securityLockButton, url, COLOR_TEXT, invalidCertificate);
    }

    private void showConnectionSecurityInfo() {
        String url = activeTab == null ? (addressBar == null ? "" : addressBar.getText().toString()) : activeTab.url;
        ConnectionSecurityUi.showInfo(this, url, activeTab != null && activeTab.sslCertificateError);
    }

    private void showPreferenceSettings() { settingsUiController.showPreferenceSettings(); }

    private void showAppearanceSettings() { settingsUiController.showAppearanceSettings(); }



    void showSingleChoiceSetting(final String title, final String key, final String[] values, final String[] labels, final boolean isTheme) {
        String fallback = "";
        if ("ui_theme".equals(key)) fallback = "system";
        else if ("ui_search_position".equals(key)) fallback = "top";
        else if ("ui_search_height".equals(key) || "ui_toolbar_height".equals(key)) fallback = "42";
        else if ("ui_icon_size".equals(key)) fallback = "15";
        else if ("ui_toolbar_gap".equals(key)) fallback = "1";
        String current = database.getSetting(key, fallback);
        int selected = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) selected = i;
        new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels, selected, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                database.putSetting(key, values[which]);
                dialog.dismiss();
                if (isTheme) {
                    String mode = database.getSetting("ui_theme", "system");
                    boolean systemDark = Build.VERSION.SDK_INT >= 29 &&
                        (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                    darkTheme = "dark".equals(mode) || ("system".equals(mode) && systemDark);
                    setTheme(darkTheme ? R.style.AppThemeDark : R.style.AppTheme);
                    if (Build.VERSION.SDK_INT >= 21) {
                        getWindow().setStatusBarColor(darkTheme ? Color.rgb(18, 20, 24) : Color.rgb(36, 78, 107));
                        getWindow().setNavigationBarColor(darkTheme ? Color.rgb(22, 25, 30) : Color.rgb(244, 244, 244));
                        getWindow().getDecorView().setSystemUiVisibility(!darkTheme && Build.VERSION.SDK_INT >= 26 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
                    }
                }
                searchAtBottom = "bottom".equals(database.getSetting("ui_search_position", "top"));
                configuredSearchHeight = readIntSetting("ui_search_height", 42, 34, 54);
                configuredToolbarHeight = readIntSetting("ui_toolbar_height", 42, 34, 54);
                configuredIconSize = readIntSetting("ui_icon_size", 15, 12, 22);
                configuredToolbarGap = readIntSetting("ui_toolbar_gap", 1, 0, 5);
                applyPalette();
                rebuildBrowserUiForAppearance();
            }
        }).setNegativeButton(LanguageManager.get("action.cancel", "Batal"), null).show();
    }

    void rebuildBrowserUiForAppearance() {
        rebuildBrowserUiChrome();
        showAppearanceSettings();
    }

    private void rebuildBrowserUiForLanguage() {
        for (int i = 0; i < tabs.size(); i++) {
            BrowserTab tab = tabs.get(i);
            if (tab.url == null || tab.url.startsWith("about:blank")) tab.title = LanguageManager.get("tabs.new_tab", "Tab Baru");
        }
        rebuildBrowserUiChrome();
        replaceCurrentPage();
        showSettings();
    }

    private void rebuildBrowserUiChrome() {
        if (webFrame == null || rootLayout == null || browserLayer == null) return;
        activePageView = null;
        pageStack.clear();
        buildUi();
        for (int i = 0; i < tabs.size(); i++) {
            BrowserTab tab = tabs.get(i);
            if (tab.webView != null) {
                if (tab.webView.getParent() instanceof ViewGroup) ((ViewGroup)tab.webView.getParent()).removeView(tab.webView);
                webFrame.addView(tab.webView, new FrameLayout.LayoutParams(-1, -1));
            }
        }
        if (activeTab != null) setActiveTab(activeTab);
    }

    void toggleDesktopMode() {
        String nextMode = "desktop".equals(globalUserAgentMode) ? "default" : "desktop";
        globalUserAgentMode = nextMode;
        database.putSetting("user_agent_mode", globalUserAgentMode);
        applyGlobalUserAgentToTabs();
        showToast(LanguageManager.format("toast.desktop_mode_status", "Mode Desktop %1$s", "desktop".equals(globalUserAgentMode) ? LanguageManager.get("state.turned_on", "diaktifkan") : LanguageManager.get("state.turned_off", "dinonaktifkan")));
        replaceCurrentPage();
        showMainMenu();
    }



    private void showUserAgentSettings() { settingsUiController.showUserAgentSettings(); }

    void saveUserAgent(String mode, String custom) {
        globalUserAgentMode = mode;
        if (custom != null) customUserAgent = custom;
        database.putSetting("user_agent_mode", globalUserAgentMode);
        database.putSetting("user_agent_custom", customUserAgent);
        applyGlobalUserAgentToTabs();
        showToast(LanguageManager.get("toast.user.agent.global.disimpan", "User-Agent global disimpan."));
        replaceCurrentPage();
        showUserAgentSettings();
    }

    void promptCustomUserAgent() {
        final EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setText(customUserAgent);
        input.setHint(LanguageManager.get("placeholder.user_agent", "Mozilla/5.0 ..."));
        new AlertDialog.Builder(this).setTitle(LanguageManager.get("dialog.title.custom.user.agent", "Custom User-Agent")).setView(input)
            .setPositiveButton(LanguageManager.get("action.save", "Simpan"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                String value = input.getText().toString().trim();
                if (value.length() == 0) { showToast(LanguageManager.get("toast.user.agent.tidak.boleh.kosong", "User-Agent tidak boleh kosong.")); return; }
                saveUserAgent("custom", value);
            }}).setNegativeButton(LanguageManager.get("action.cancel", "Batal"), null).show();
    }

    private void applyGlobalUserAgentToTabs() {
        for (int i = 0; i < tabs.size(); i++) {
            BrowserTab tab = tabs.get(i);
            applyUserAgent(tab, tab.url);
            if (tab.url != null && !tab.url.startsWith("about:blank")) tab.webView.reload();
        }
    }

    void editHomepage() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(database.getSetting("homepage", "https://www.google.com/"));
        input.setHint(LanguageManager.get("placeholder.homepage", "https://example.com/"));
        new AlertDialog.Builder(this).setTitle(LanguageManager.get("dialog.title.halaman.awal", "Halaman Awal")).setView(input)
            .setPositiveButton(LanguageManager.get("action.save", "Simpan"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                String value = input.getText().toString().trim();
                if (value.length() == 0) value = "https://www.google.com/";
                if (!value.contains("://")) value = "https://" + value;
                database.putSetting("homepage", value);
                showToast(LanguageManager.get("toast.halaman.awal.disimpan", "Halaman awal disimpan."));
            }}).setNegativeButton(LanguageManager.get("action.cancel", "Batal"), null).show();
    }

    private void showPrivacySettings() { settingsUiController.showPrivacySettings(); }

    private void showOriginStorage(final String origin) { settingsUiController.showOriginStorage(origin); }

    private void confirmDeleteOriginCookies(final String origin) { settingsUiController.confirmDeleteOriginCookies(origin); }

    private void confirmClearCookies() { settingsUiController.confirmClearCookies(); }

    private void confirmClearWebStorage() { settingsUiController.confirmClearWebStorage(); }

    private void showSecuritySettings() { settingsUiController.showSecuritySettings(); }



    private void showDebuggerSettings() { settingsUiController.showDebuggerSettings(); }

    private void showExtensionDebugger() { settingsUiController.showExtensionDebugger(); }



    private void showExtensionDebugger(final String extensionId) { settingsUiController.showExtensionDebugger(extensionId); }

    private void showAbout() { settingsUiController.showAbout(); }

    private void rememberCurrentOrigin(String url) {
        String origin = getOrigin(url);
        if (origin != null && database != null) database.rememberOrigin(origin);
    }

    String getOrigin(String url) {
        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) return null;
            int port = uri.getPort();
            if (port >= 0 && !("http".equalsIgnoreCase(scheme) && port == 80) && !("https".equalsIgnoreCase(scheme) && port == 443)) return scheme + "://" + host + ":" + port;
            return scheme + "://" + host;
        } catch (Exception error) { return null; }
    }

    private void createDebuggerOverlayButton() {
        debuggerOverlay = new BrowserDebuggerOverlay(this, browserLayer, database, new BrowserDebuggerOverlay.Host() {
            public BrowserTab getActiveTab() { return activeTab; }
            public int getTabCount() { return tabs.size(); }
            public int getActiveTabNumber() { return tabs.indexOf(activeTab) + 1; }
            public boolean isPageActive() { return activePageView != null; }
            public boolean isFullscreen() { return customViewActive; }
        });
        debuggerOverlay.setEnabled(debuggerEnabled);
    }

    void refreshDebuggerOverlayVisibility() {
        if (debuggerOverlay != null) debuggerOverlay.setEnabled(debuggerEnabled);
    }

    void hideDebuggerPanel() {
        if (debuggerOverlay != null) debuggerOverlay.hidePanel();
    }

    private void refreshDebuggerPanel() {
        if (debuggerOverlay != null) debuggerOverlay.refreshPanel();
    }

    void rebuildDebuggerPanel() {
        if (debuggerOverlay != null) debuggerOverlay.rebuildPanelTabs();
    }

    void showTabsDialog() {
        LinearLayout body = openPage(LanguageManager.format("tabs.open_count_title", "Tab Terbuka (%1$d)", tabs.size()));
        addPageAction(body, 0xf067,LanguageManager.get("ui.tab.baru", "Tab Baru"),LanguageManager.get("description.buka.tab.baru", "Buka tab baru"), new View.OnClickListener() { public void onClick(View v) { addTab(false); backFromPage(); } });
        for (int i = 0; i < tabs.size(); i++) {
            final BrowserTab tab = tabs.get(i);
            final int number = i + 1;
            addPageAction(body, 0xf2d0, (tab == activeTab ? LanguageManager.get("state.active_prefix", "[aktif] ") : "") + number + ". " + tab.title, tab.url, new View.OnClickListener() { public void onClick(View v) { setActiveTab(tab); backFromPage(); } });
            addPageAction(body, 0xf00d, LanguageManager.format("tabs.close_title", "Tutup Tab %1$d", number), tab.title, new View.OnClickListener() { public void onClick(View v) {
                if (tab == activeTab) closeActiveTab();
                else { tabs.remove(tab); webFrame.removeView(tab.webView); try { tab.webView.stopLoading(); tab.webView.destroy(); } catch (Exception ignored) { } updateTabSummary(); }
                backFromPage();
            }});
        }
    }

    void showQuickExtensionPopup() {
        extensionManager.refresh();
        final List<ExtensionInfo> installed = extensionManager.getExtensions();
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(5), dp(12), dp(5));
        content.setBackgroundColor(COLOR_PANEL);
        final AlertDialog[] dialogHolder = new AlertDialog[1];
        if (installed.size() == 0) {
            TextView empty = new TextView(this);
            empty.setText(LanguageManager.get("status.belum.ada.extension.yang.terpasang.pasang", "Belum ada extension yang terpasang. Pasang ZIP melalui Pengelola Extension di Pengaturan."));
            empty.setTextColor(COLOR_TEXT);
            empty.setTextSize(14);
            empty.setPadding(dp(6), dp(15), dp(6), dp(15));
            content.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        } else {
            for (int i = 0; i < installed.size(); i++) {
                final ExtensionInfo info = installed.get(i);
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(7), dp(9), dp(4), dp(9));
                row.setBackground(boxBackground(darkTheme ? Color.rgb(29, 33, 39) : Color.WHITE, COLOR_BORDER));
                LinearLayout labels = new LinearLayout(this);
                labels.setOrientation(LinearLayout.VERTICAL);
                TextView name = new TextView(this);
                name.setText(info.getDisplayName());
                name.setTextColor(COLOR_TEXT);
                name.setTextSize(14);
                name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                TextView description = new TextView(this);
                String desc = info.description == null || info.description.length() == 0 ? LanguageManager.format("extension.type_description", "%1$s extension", info.type) : info.description;
                description.setText(desc);
                description.setTextColor(darkTheme ? Color.rgb(165, 172, 182) : Color.rgb(94, 94, 94));
                description.setTextSize(11);
                description.setMaxLines(2);
                description.setEllipsize(android.text.TextUtils.TruncateAt.END);
                labels.addView(name, new LinearLayout.LayoutParams(-1, -2));
                labels.addView(description, new LinearLayout.LayoutParams(-1, -2));
                row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
                final Switch toggle = new Switch(this);
                toggle.setChecked(extensionManager.isEnabled(info));
                toggle.setContentDescription(LanguageManager.format("accessibility.enable_extension", "Aktifkan %1$s", info.getDisplayName()));
                row.addView(toggle, new LinearLayout.LayoutParams(-2, -2));
                toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton button, boolean checked) {
                        boolean previous = extensionManager.isEnabled(info);
                        if (previous == checked) return;
                        extensionManager.setEnabled(info, checked);
                        reloadExtensionRuntime();
                        showToast(LanguageManager.format(checked ? "toast.extension_enabled" : "toast.extension_disabled", checked ? "%1$s diaktifkan" : "%1$s dinonaktifkan", info.getDisplayName()));
                    }
                });
                labels.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View view) {
                        if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                        showExtensionManager();
                        showExtensionDetails(info);
                    }
                });
                content.addView(row, new LinearLayout.LayoutParams(-1, -2));
                if (i < installed.size() - 1) {
                    View gap = new View(this);
                    content.addView(gap, new LinearLayout.LayoutParams(-1, dp(5)));
                }
            }
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        scroll.setPadding(dp(6), dp(2), dp(6), dp(2));
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(LanguageManager.format("extension.installed_count_title", "Extension (%1$d)", installed.size()))
            .setView(scroll)
            .setPositiveButton(LanguageManager.get("action.manage", "Pengelola"), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) { showExtensionManager(); }
            })
            .setNegativeButton(LanguageManager.get("action.close", "Tutup"), null)
            .create();
        dialogHolder[0] = dialog;
        dialog.show();
    }

    void showExtensionManager() {
        extensionManager.refresh();
        final List<ExtensionInfo> installed = extensionManager.getExtensions();
        LinearLayout body = openPage(LanguageManager.get("page.extension_manager", "Pengelola Ekstensi"));
        addPageAction(body, 0xf093,LanguageManager.get("action.pasang.extension.zip", "Pasang Extension ZIP"),LanguageManager.get("description.pilih.arsip.zip.melalui.pemilih.file.andro", "Pilih arsip ZIP melalui pemilih file Android"), new View.OnClickListener() { public void onClick(View v) { chooseExtensionZip(); } });
        addPageAction(body, 0xf188,LanguageManager.get("ui.debugger.extension", "Debugger Extension"),LanguageManager.get("description.log.startup.error.beanshell.dan.runtime.ex", "Log startup, error BeanShell, dan runtime extension"), new View.OnClickListener() { public void onClick(View v) { showExtensionDebugger(); } });
        if (installed.size() == 0) addPageNote(body,LanguageManager.get("message.belum.ada.extension.yang.terpasang.pilih.p", "Belum ada extension yang terpasang. Pilih Pasang Extension ZIP untuk memulai."));
        for (int i = 0; i < installed.size(); i++) {
            final ExtensionInfo info = installed.get(i);
            addPageAction(body, 0xf12e, (extensionManager.isEnabled(info) ? LanguageManager.get("state.active_prefix", "[aktif] ") : LanguageManager.get("state.inactive_prefix", "[nonaktif] ")) + info.getDisplayName(), LanguageManager.format("extension.version_type_id", "v%1$s · %2$s · %3$s", info.version, info.type, info.id), new View.OnClickListener() { public void onClick(View v) { showExtensionDetails(info); } });
        }
    }

    private void showExtensionDetails(final ExtensionInfo info) {
        if (info == null) return;
        LinearLayout body = openPage(LanguageManager.get("page.extension_details", "Detail Extension"));
        StringBuilder details = new StringBuilder();
        details.append(info.getDisplayName()).append("\n").append(LanguageManager.get("extension.id_label", "ID")).append(": ").append(info.id)
            .append("\n").append(LanguageManager.get("extension.version_label", "Versi")).append(": ").append(info.version)
            .append("\n").append(LanguageManager.get("extension.type_label", "Tipe")).append(": ").append(info.type).append("\n");
        if (info.description.length() > 0) details.append("\n").append(info.description).append("\n");
        details.append("\n").append(LanguageManager.get("extension.domain_lock", "Domain lock")).append(": ").append(info.domainLockEnabled ? LanguageManager.get("state.enabled", "Aktif") : LanguageManager.get("extension.all_domains", "none / semua domain")).append("\n");
        for (int i = 0; i < info.domains.size(); i++) details.append("  - ").append(info.domains.get(i)).append("\n");
        details.append("\n").append(LanguageManager.get("extension.permissions_label", "Permission" )).append(":");
        if (info.permissions.size() == 0) details.append("\n  ").append(LanguageManager.get("extension.no_permissions", "Tidak meminta permission"));
        for (int i = 0; i < info.permissions.size(); i++) {
            String p = info.permissions.get(i);
            details.append("\n  [").append(extensionManager.hasPermission(info, p) ? LanguageManager.get("permission.granted", "diizinkan") : LanguageManager.get("permission.denied", "ditolak")).append("] ").append(p);
        }
        if ("java".equals(info.type)) details.append("\n\n").append(LanguageManager.get("extension.java_security_note", "Catatan keamanan: pemeriksaan permission internet/API Android memindai referensi source yang umum dan bukan sandbox sempurna. BeanShell berjalan dalam proses aplikasi; reflection atau pemanggilan tidak langsung dapat melewati pemeriksaan. Instal hanya extension tepercaya."));
        addPageNote(body, details.toString());
        addPageAction(body, extensionManager.isEnabled(info) ? 0xf05e : 0xf058, extensionManager.isEnabled(info) ? LanguageManager.get("extension.disable", "Nonaktifkan Extension") : LanguageManager.get("extension.enable", "Aktifkan Extension"),LanguageManager.get("description.ubah.status.extension", "Ubah status extension"), new View.OnClickListener() { public void onClick(View v) {
            extensionManager.setEnabled(info, !extensionManager.isEnabled(info)); reloadExtensionRuntime(); replaceCurrentPage(); showExtensionDetails(info);
        }});
        addPageAction(body, 0xf084,LanguageManager.get("action.kelola.permission", "Kelola Permission"),LanguageManager.get("extension.permissions_description", "Berikan atau cabut permission yang diminta"), new View.OnClickListener() { public void onClick(View v) { showPermissionEditor(info); }});
        addPageAction(body, 0xf2ed,LanguageManager.get("action.hapus.extension", "Hapus Extension"),LanguageManager.get("description.hapus.file.extension.dari.penyimpanan.inte", "Hapus file extension dari penyimpanan internal"), new View.OnClickListener() { public void onClick(View v) { confirmDeleteExtension(info); }});
        addPageAction(body, 0xf188,LanguageManager.get("action.lihat.log.extension", "Lihat Log Extension"), LanguageManager.format("extension.show_logs_description", "Tampilkan log milik %1$s", info.id), new View.OnClickListener() { public void onClick(View v) { showExtensionDebugger(info.id); }});
    }

    private void showPermissionEditor(final ExtensionInfo info) {
        if (info.permissions.size() == 0) {
            showToast(LanguageManager.get("toast.extension.ini.tidak.meminta.permission", "Extension ini tidak meminta permission."));
            return;
        }
        final String[] permissions = info.permissions.toArray(new String[info.permissions.size()]);
        final String[] labels = new String[permissions.length];
        final boolean[] checked = new boolean[permissions.length];
        for (int i = 0; i < permissions.length; i++) {
            labels[i] = permissions[i] + " — " + ExtensionManager.permissionDescription(permissions[i]);
            checked[i] = extensionManager.hasPermission(info, permissions[i]);
        }
        new AlertDialog.Builder(this).setTitle(LanguageManager.format("extension.permission_title", "Permission: %1$s", info.getDisplayName()))
            .setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                public void onClick(DialogInterface dialog, int which, boolean isChecked) { checked[which] = isChecked; }
            })
            .setPositiveButton(LanguageManager.get("action.save", "Simpan"), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    Set<String> granted = new HashSet<String>();
                    for (int i = 0; i < permissions.length; i++) if (checked[i]) granted.add(permissions[i]);
                    extensionManager.setPermissions(info, granted);
                    reloadExtensionRuntime();
                    replaceCurrentPage();
                    showExtensionDetails(extensionManager.getExtension(info.id));
                }
            }).setNegativeButton(LanguageManager.get("action.cancel", "Batal"), null).show();
    }

    private void confirmDeleteExtension(final ExtensionInfo info) {
        new AlertDialog.Builder(this).setTitle(LanguageManager.get("dialog.title.hapus.extension", "Hapus Extension?"))
            .setMessage(LanguageManager.format("extension.uninstall_message", "File %1$s akan dihapus dari penyimpanan internal browser.", info.getDisplayName()))
            .setPositiveButton(LanguageManager.get("action.delete", "Hapus"), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    showToast(LanguageManager.get("toast.menghapus.extension", "Menghapus extension..."));
                    Thread deleteWorker = new Thread(new Runnable() {
                        public void run() {
                            try {
                                Exception problem = null;
                                try {
                                    // Cancel any queued runtime rebuild and remove live hooks owned by
                                    // this extension before deleting its files and persisted records.
                                    extensionRuntimeGeneration++;
                                    synchronized (extensionRuntimeProcessingLock) {
                                        extensionRuntimeGeneration++;
                                        removeExtensionRuntimeState(info.id);
                                        extensionManager.deleteExtension(info);
                                    }
                                } catch (Exception error) { problem = error; }
                                final Exception failure = problem;
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        if (activityDestroyed) return;
                                        if (failure != null) {
                                            // If filesystem deletion failed, re-enable hooks for any
                                            // extension that still exists instead of leaving it inert.
                                            extensionManager.refresh();
                                            reloadExtensionRuntime();
                                            showError(LanguageManager.get("error.hapus.extension", "Hapus Extension"), failure);
                                            return;
                                        }
                                        extensionManager.refresh();
                                        reloadExtensionRuntime();
                                        replaceCurrentPage();
                                        showExtensionManager();
                                        showToast(LanguageManager.get("toast.extension.dan.seluruh.datanya.sudah.dihapu", "Extension dan seluruh datanya sudah dihapus."));
                                    }
                                });
                            } finally { endBackgroundWork(); }
                        }
                    }, "Chromitext-ExtensionDeleter");
                    deleteWorker.setDaemon(true);
                    beginBackgroundWork();
                    deleteWorker.start();
                }
            }).setNegativeButton(LanguageManager.get("action.cancel", "Batal"), null).show();
    }

    /** Removes hooks and local resources owned by an extension being uninstalled. */
    private void removeExtensionRuntimeState(String extensionId) {
        if (extensionId == null) return;
        for (int i = requestBlockRules.size() - 1; i >= 0; i--) {
            RequestBlockRule rule = requestBlockRules.get(i);
            if (extensionId.equals(rule.extensionId)) requestBlockRules.remove(rule);
        }
        for (int i = requestObservers.size() - 1; i >= 0; i--) {
            RequestObserverRule rule = requestObservers.get(i);
            if (extensionId.equals(rule.extensionId)) requestObservers.remove(rule);
        }
        for (int i = navigationObservers.size() - 1; i >= 0; i--) {
            NavigationObserverRule rule = navigationObservers.get(i);
            if (extensionId.equals(rule.extensionId)) navigationObservers.remove(rule);
        }
        for (int i = navigationBlockRules.size() - 1; i >= 0; i--) {
            NavigationBlockRule rule = navigationBlockRules.get(i);
            if (extensionId.equals(rule.extensionId)) navigationBlockRules.remove(rule);
        }
        for (int i = crossDomainNavigationBlockRules.size() - 1; i >= 0; i--) {
            CrossDomainNavigationBlockRule rule = crossDomainNavigationBlockRules.get(i);
            if (extensionId.equals(rule.extensionId)) crossDomainNavigationBlockRules.remove(rule);
        }
        for (int i = userAgentRules.size() - 1; i >= 0; i--) {
            UserAgentRule rule = userAgentRules.get(i);
            if (extensionId.equals(rule.extensionId)) userAgentRules.remove(rule);
        }
        for (int i = scriptRules.size() - 1; i >= 0; i--) {
            ScriptRule rule = scriptRules.get(i);
            if (extensionId.equals(rule.extensionId)) scriptRules.remove(rule);
        }
        for (int i = bridgeRegistrations.size() - 1; i >= 0; i--) {
            BridgeRegistration rule = bridgeRegistrations.get(i);
            if (extensionId.equals(rule.extensionId)) bridgeRegistrations.remove(rule);
        }
        synchronized (localServers) {
            ArrayList<Integer> removePorts = new ArrayList<Integer>();
            for (Map.Entry<Integer, String> entry : localServerOwners.entrySet()) {
                if (extensionId.equals(entry.getValue())) removePorts.add(entry.getKey());
            }
            for (int i = 0; i < removePorts.size(); i++) {
                Integer port = removePorts.get(i);
                LocalHttpServer server = localServers.get(port);
                if (server != null) {
                    try { server.stop(); } catch (Exception ignored) { }
                }
                localServers.remove(port);
                localServerOwners.remove(port);
            }
        }
    }

    private void chooseExtensionZip() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");
            startActivityForResult(intent, REQUEST_INSTALL_ZIP);
        } catch (Exception error) {
            try {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(Intent.createChooser(intent, LanguageManager.get("dialog.choose_extension_zip", "Pilih ZIP extension")), REQUEST_INSTALL_ZIP);
            } catch (Exception second) { showError(LanguageManager.get("error.pemilih.file", "pemilih file"), second); }
        }
    }

    private void reviewInstall(final ExtensionManager.PreparedInstall prepared) {
        pendingInstall = prepared;
        final ExtensionInfo info = prepared.info;
        final String[] permissions = info.permissions.toArray(new String[info.permissions.size()]);
        final String[] labels = new String[permissions.length];
        final boolean[] checked = new boolean[permissions.length];
        for (int i = 0; i < permissions.length; i++) {
            labels[i] = permissions[i] + " — " + ExtensionManager.permissionDescription(permissions[i]);
            checked[i] = false;
        }
        StringBuilder message = new StringBuilder();
        message.append(LanguageManager.format("extension.install_name", "Nama: %1$s", info.getDisplayName()))
            .append("\n").append(LanguageManager.format("extension.install_version", "Versi: %1$s", info.version))
            .append("\n").append(LanguageManager.format("extension.install_type", "Tipe: %1$s", info.type))
            .append("\n").append(LanguageManager.format("extension.install_id", "ID: %1$s", info.id)).append("\n\n");
        message.append(info.description.length() == 0 ? LanguageManager.get("extension.no_description", "Tanpa deskripsi") : info.description);
        message.append("\n\n").append(LanguageManager.get("extension.domain_lock_label", "Kunci domain: "))
            .append(info.domainLockEnabled ? info.domains.toString() : LanguageManager.get("extension.all_domains", "Semua domain"));
        if ("java".equals(info.type)) {
            message.append("\n\n").append(LanguageManager.get("extension.java_permission_warning", "Peringatan: izin internet dan Android API diperiksa melalui pemindaian pola source yang umum. Ini bukan sandbox sempurna; reflection atau pemanggilan tidak langsung dapat melewati pemeriksaan. Pasang hanya extension dari sumber tepercaya."));
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(LanguageManager.get("dialog.title.pasang.extension", "Pasang Extension")).setMessage(message.toString());
        if (permissions.length > 0) {
            builder.setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                public void onClick(DialogInterface dialog, int which, boolean isChecked) { checked[which] = isChecked; }
            });
        }
        builder.setPositiveButton(LanguageManager.get("action.install", "pasang"), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                final Set<String> granted = new HashSet<String>();
                for (int i = 0; i < permissions.length; i++) if (checked[i]) granted.add(permissions[i]);
                showToast(LanguageManager.get("toast.memasang.extension", "Memasang extension..."));
                Thread commitWorker = new Thread(new Runnable() {
                    public void run() {
                        try {
                            Exception problem = null;
                            try { extensionManager.commitInstall(prepared, granted); }
                            catch (Exception error) { problem = error; }
                            final Exception failure = problem;
                            runOnUiThread(new Runnable() {
                                public void run() {
                                    pendingInstall = null;
                                    if (activityDestroyed) return;
                                    if (failure != null) {
                                        extensionManager.cancelInstall(prepared);
                                        showError(LanguageManager.get("error.instalasi.extension", "instalasi extension"), failure);
                                        return;
                                    }
                                    reloadExtensionRuntime();
                                    showToast(LanguageManager.format("toast.extension_installed", "Extension terpasang: %1$s", info.getDisplayName()));
                                    showExtensionManager();
                                }
                            });
                        } finally { endBackgroundWork(); }
                    }
                }, "Chromitext-ExtensionInstaller");
                commitWorker.setDaemon(true);
                beginBackgroundWork();
                commitWorker.start();
            }
        }).setNegativeButton(LanguageManager.get("action.cancel", "Batal"), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                extensionManager.cancelInstall(prepared);
                pendingInstall = null;
            }
        });
        AlertDialog dialog = builder.create();
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface ignored) {
                extensionManager.cancelInstall(prepared);
                pendingInstall = null;
            }
        });
        dialog.show();
    }

    private synchronized void beginBackgroundWork() {
        activeBackgroundWorkCount++;
    }

    private void endBackgroundWork() {
        boolean closeDatabase = false;
        synchronized (this) {
            if (activeBackgroundWorkCount > 0) activeBackgroundWorkCount--;
            if (activityDestroyed && activeBackgroundWorkCount == 0 && !databaseClosed) {
                databaseClosed = true;
                closeDatabase = true;
            }
        }
        if (closeDatabase && database != null) {
            try { database.close(); } catch (Exception ignored) { }
        }
    }

    private void loadExtensions() {
        extensionRuntimeLoaded = false;
        reloadExtensionRuntime();
    }

    /** Rebuilds extension registrations away from the UI thread. A newer request supersedes stale work. */
    private void reloadExtensionRuntime() {
        final int request = ++extensionRuntimeGeneration;
        extensionRuntimeLoaded = false;
        Thread worker = new Thread(new Runnable() {
            public void run() {
                try {
                synchronized (extensionRuntimeProcessingLock) {
                    if (request != extensionRuntimeGeneration || activityDestroyed) return;
                    try {
                        requestBlockRules.clear();
                        requestObservers.clear();
                        navigationObservers.clear();
                        navigationBlockRules.clear();
                        crossDomainNavigationBlockRules.clear();
                        userAgentRules.clear();
                        scriptRules.clear();
                        bridgeRegistrations.clear();
                        beanShellInterpreters.clear();
                        extensionManager.refresh();
                        List<ExtensionInfo> installed = extensionManager.getExtensions();
                        for (int i = 0; i < installed.size(); i++) {
                            if (request != extensionRuntimeGeneration || activityDestroyed) return;
                            ExtensionInfo info = installed.get(i);
                            if (!extensionManager.isEnabled(info) || !"java".equals(info.type)) continue;
                            runBeanShellExtension(info);
                        }
                        if (request != extensionRuntimeGeneration || activityDestroyed) return;
                        stopInvalidLocalServers();
                    } catch (Throwable error) {
                        extensionLog("chromitext.runtime", "gagal memuat runtime extension: " + throwableToString(error));
                    }
                }
                if (request != extensionRuntimeGeneration || activityDestroyed) return;
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (activityDestroyed || request != extensionRuntimeGeneration) return;
                        extensionRuntimeLoaded = true;
                        // Only reload the foreground tab immediately. Other tabs reload when selected,
                        // preventing a permission toggle from kicking every WebView at once.
                        for (int i = 0; i < tabs.size(); i++) {
                            BrowserTab tab = tabs.get(i);
                            if (tab == null || tab.webView == null || tab.url.startsWith("about:blank")) continue;
                            if (tab == activeTab) {
                                tab.extensionReloadPending = false;
                                tab.webView.reload();
                            } else {
                                tab.extensionReloadPending = true;
                            }
                        }
                    }
                });
                } finally {
                    endBackgroundWork();
                }
            }
        }, "Chromitext-ExtensionRuntime-" + request);
        worker.setDaemon(true);
        beginBackgroundWork();
        worker.start();
    }

    private void runBeanShellExtension(final ExtensionInfo info) {
        try {
            final String source = extensionManager.readScript(info, info.javaEntry);
            boolean needsInternet = ExtensionPermissionScanner.needsInternet(source);
            boolean needsAndroidApi = ExtensionPermissionScanner.needsAndroidApi(source);
            boolean needsFileAccess = ExtensionPermissionScanner.needsFileAccess(source);
            boolean needsReflection = ExtensionPermissionScanner.needsReflection(source);
            boolean needsProcessExecution = ExtensionPermissionScanner.needsProcessExecution(source);
            if (needsInternet && !extensionManager.hasPermission(info, "internet")) {
                extensionLog(info.id, "runtime ditolak: script terindikasi memakai akses jaringan, tetapi permission 'internet' belum diaktifkan dan dideklarasikan di manifest.json.");
                return;
            }
            if (needsAndroidApi && !extensionManager.hasPermission(info, "androidApi") && !extensionManager.hasPermission(info, "androidClasses")) {
                extensionLog(info.id, "runtime ditolak: script merujuk API Android, tetapi permission 'androidApi' belum diaktifkan dan dideklarasikan di manifest.json.");
                return;
            }
            if (needsFileAccess && !extensionManager.hasPermission(info, "fileAccess")) {
                extensionLog(info.id, "runtime ditolak: script merujuk akses file Java langsung, tetapi permission 'fileAccess' belum diaktifkan dan dideklarasikan di manifest.json.");
                return;
            }
            if (needsReflection && !extensionManager.hasPermission(info, "reflection")) {
                extensionLog(info.id, "runtime ditolak: script merujuk reflection/pemuatan kelas dinamis, tetapi permission 'reflection' belum diaktifkan dan dideklarasikan di manifest.json.");
                return;
            }
            if (needsProcessExecution && !extensionManager.hasPermission(info, "processExecution")) {
                extensionLog(info.id, "runtime ditolak: script merujuk eksekusi proses sistem, tetapi permission 'processExecution' belum diaktifkan dan dideklarasikan di manifest.json.");
                return;
            }

            Class<?> interpreterClass = Class.forName("bsh.Interpreter");
            Constructor<?> constructor = interpreterClass.getConstructor(new Class[0]);
            final Object interpreter = constructor.newInstance(new Object[0]);
            ClassLoader permissionLoader = new ClassLoader(MainActivity.class.getClassLoader()) {
                @Override
                protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    if (ExtensionPermissionScanner.isNetworkClassName(name) && !extensionManager.hasPermission(info, "internet")) {
                        throw new NoClassDefFoundError("permission extension dibutuhkan: internet (" + name + ")");
                    }
                    if (name.startsWith("android.") && !extensionManager.hasPermission(info, "androidApi") && !extensionManager.hasPermission(info, "androidClasses")) {
                        throw new NoClassDefFoundError("permission extension dibutuhkan: androidApi (" + name + ")");
                    }
                    if (ExtensionPermissionScanner.isFileClassName(name) && !extensionManager.hasPermission(info, "fileAccess")) {
                        throw new NoClassDefFoundError("permission extension dibutuhkan: fileAccess (" + name + ")");
                    }
                    if (ExtensionPermissionScanner.isReflectionClassName(name) && !extensionManager.hasPermission(info, "reflection")) {
                        throw new NoClassDefFoundError("permission extension dibutuhkan: reflection (" + name + ")");
                    }
                    if (ExtensionPermissionScanner.isProcessExecutionClassName(name) && !extensionManager.hasPermission(info, "processExecution")) {
                        throw new NoClassDefFoundError("permission extension dibutuhkan: processExecution (" + name + ")");
                    }
                    return super.loadClass(name, resolve);
                }
            };
            Method setLoaderMethod = interpreterClass.getMethod("setClassLoader", new Class[]{ClassLoader.class});
            setLoaderMethod.invoke(interpreter, new Object[]{permissionLoader});
            Method setMethod = interpreterClass.getMethod("set", new Class[]{String.class, Object.class});
            BrowserExtensionApi api = new BrowserExtensionApi(info, extensionManager, this);
            setMethod.invoke(interpreter, new Object[]{"browser", api});
            setMethod.invoke(interpreter, new Object[]{"extensionDir", extensionManager.getExtensionDirectory(info.id)});
            Method evalMethod = interpreterClass.getMethod("eval", new Class[]{String.class});
            evalMethod.invoke(interpreter, new Object[]{source});
            beanShellInterpreters.add(interpreter);
            extensionLog(info.id, "runtime BeanShell berhasil dimuat");
        } catch (ClassNotFoundException error) {
            extensionLog(info.id, "bsh-2.0b4.jar belum ditemukan. Taruh JAR di folder libs/ lalu rebuild project.");
        } catch (Throwable error) {
            Throwable cause = error;
            if (error instanceof java.lang.reflect.InvocationTargetException && ((java.lang.reflect.InvocationTargetException) error).getTargetException() != null) {
                cause = ((java.lang.reflect.InvocationTargetException) error).getTargetException();
            }
            extensionLog(info.id, "gagal menjalankan script java: " + throwableToString(cause));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_WEB_FILE) {
            final ValueCallback<Uri[]> callback = fileChooserCallback;
            fileChooserCallback = null;
            if (callback != null) {
                Uri[] results = null;
                try { results = WebChromeClient.FileChooserParams.parseResult(resultCode, data); }
                catch (Exception ignored) {
                    if (resultCode == RESULT_OK && data != null && data.getData() != null) results = new Uri[]{data.getData()};
                }
                try { callback.onReceiveValue(results); } catch (Exception ignored) { }
            }
            return;
        }
        if (requestCode == REQUEST_INSTALL_ZIP && resultCode == RESULT_OK && data != null && data.getData() != null) {
            final Uri zipUri = data.getData();
            showToast(LanguageManager.get("toast.memeriksa.arsip.extension", "Memeriksa arsip extension..."));
            Thread installer = new Thread(new Runnable() {
                public void run() {
                    try {
                        InputStream input = null;
                        ExtensionManager.PreparedInstall prepared = null;
                        Exception problem = null;
                        try {
                            input = getContentResolver().openInputStream(zipUri);
                            prepared = extensionManager.prepareInstall(input);
                        } catch (Exception error) {
                            problem = error;
                        } finally {
                            if (input != null) try { input.close(); } catch (Exception ignored) { }
                        }
                        final ExtensionManager.PreparedInstall result = prepared;
                        final Exception failure = problem;
                        runOnUiThread(new Runnable() {
                            public void run() {
                                if (activityDestroyed) {
                                    if (result != null) extensionManager.cancelInstall(result);
                                    return;
                                }
                                if (failure != null) showError(LanguageManager.get("error.membaca.zip.extension", "membaca zip extension"), failure);
                                else if (result != null) reviewInstall(result);
                                else showToast(LanguageManager.get("toast.zip.extension.tidak.menghasilkan.data.inst", "ZIP extension tidak menghasilkan data instalasi."));
                            }
                        });
                    } finally { endBackgroundWork(); }
                }
            }, "Chromitext-ExtensionZipReader");
            installer.setDaemon(true);
            beginBackgroundWork();
            installer.start();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WEB_PERMISSION) {
            if (pendingWebPermission != null) {
                boolean granted = grantResults.length > 0;
                for (int i = 0; i < grantResults.length; i++) if (grantResults[i] != PackageManager.PERMISSION_GRANTED) granted = false;
                try { if (granted) pendingWebPermission.grant(pendingWebPermission.getResources()); else pendingWebPermission.deny(); }
                catch (Exception ignored) { }
                pendingWebPermission = null;
            }
            return;
        }
        if (requestCode == REQUEST_GEO_PERMISSION) {
            if (pendingGeoCallback != null) {
                boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
                pendingGeoCallback.invoke(pendingGeoOrigin, granted, false);
                pendingGeoCallback = null;
                pendingGeoOrigin = null;
            }
            return;
        }
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) showToast(LanguageManager.get("toast.izin.penyimpanan.untuk.unduhan.diberikan", "Izin penyimpanan untuk unduhan diberikan."));
        }
    }

    private void startDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
            request.setTitle(filename);
            request.setDescription(LanguageManager.get("downloads.notification_description", "Unduhan dari Chromitext"));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            if (mimeType != null) request.setMimeType(mimeType);
            if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                filename);
            manager.enqueue(request);
            showToast(LanguageManager.get("toast.unduhan.ditambahkan.ke.download.manager.an", "Unduhan ditambahkan ke Download Manager Android."));
        } catch (Exception error) {
            showError(LanguageManager.get("error.download", "download"), error);
        }
    }

    void toggleJavascript() {
        javascriptEnabled = !javascriptEnabled;
        database.putBoolean("javascript_enabled", javascriptEnabled);
        for (int i = 0; i < tabs.size(); i++) tabs.get(i).webView.getSettings().setJavaScriptEnabled(javascriptEnabled);
        showToast(LanguageManager.format("toast.javascript_status", "JavaScript %1$s", javascriptEnabled ? LanguageManager.get("state.enabled", "Aktif") : LanguageManager.get("state.disabled", "Nonaktif")));
        if (activeTab != null && !activeTab.url.startsWith("about:blank")) activeTab.webView.reload();
    }

    void clearBrowserData() {
        new AlertDialog.Builder(this).setTitle(LanguageManager.get("dialog.title.hapus.data.browser", "Hapus Data Browser"))
            .setMessage(LanguageManager.get("dialog.message.hapus.cache.webview.cookie.dan.data.situs", "Hapus cache WebView, cookie, dan data situs? Bookmark dan extension tidak akan dihapus."))
            .setPositiveButton(LanguageManager.get("action.delete", "Hapus"), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    for (int i = 0; i < tabs.size(); i++) tabs.get(i).webView.clearCache(true);
                    CookieManager cookies = CookieManager.getInstance();
                    if (Build.VERSION.SDK_INT >= 21) cookies.removeAllCookies(null);
                    else {
                        CookieSyncManager.createInstance(MainActivity.this);
                        cookies.removeAllCookie();
                    }
                    WebViewDatabaseHelper.clear(MainActivity.this);
                    showToast(LanguageManager.get("toast.data.situs.dibersihkan", "Data situs dibersihkan."));
                }
            }).setNegativeButton(LanguageManager.get("action.cancel", "Batal"), null).show();
    }

    void showBookmarks() { libraryPagesController.showBookmarks(); }

    private void addCurrentBookmark() { libraryPagesController.addCurrentBookmark(); }

    void showHistory() { libraryPagesController.showHistory(); }

    void showDownloadsInfo() { libraryPagesController.showDownloadsInfo(); }

    private void saveHistory(String title, String url) {
        if (!UrlResolver.isWebUrl(url)) return;
        database.addHistory(title, url, 500);
    }

    private ArrayList<String> readLines(String key) {
        return new ArrayList<String>(database.getLines(key));
    }

    private void hideKeyboard() {
        addressSuggestionController.hide();
        try {
            InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            manager.hideSoftInputFromWindow(addressBar.getWindowToken(), 0);
            addressBar.clearFocus();
        } catch (Exception ignored) { }
    }

    void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showError(String title, Exception error) {
        String message = error == null ? LanguageManager.get("error.unknown", "Error tidak diketahui") : error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton(LanguageManager.get("action.ok", "OK"), null).show();
    }

    private String throwableToString(Throwable error) {
        if (error == null) return "error tidak diketahui";
        java.io.StringWriter writer = new java.io.StringWriter();
        java.io.PrintWriter printer = new java.io.PrintWriter(writer);
        error.printStackTrace(printer);
        printer.flush();
        return writer.toString();
    }

    @Override
    public void extensionLog(String extensionId, String message) {
        // Late callbacks from a removed extension must not recreate its deleted log records.
        if (extensionId != null && extensionManager != null &&
                !"chromitext.runtime".equals(extensionId) && extensionManager.getExtension(extensionId) == null) {
            return;
        }
        final String line = "[" + extensionId + "] " + message;
        if (database != null) {
            try { database.addExtensionLog(extensionId, message); }
            catch (Exception error) { android.util.Log.w("ChromitextExtension", "Gagal menyimpan log extension", error); }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (statusView != null && activeTab == null) statusView.setText(line);
        } else if (!activityDestroyed) {
            runOnUiThread(new Runnable() {
                public void run() {
                    if (!activityDestroyed && statusView != null && activeTab == null) statusView.setText(line);
                }
            });
        }
    }

    @Override
    public void addRequestBlockRule(ExtensionInfo info, String domain) {
        RequestBlockRule rule = new RequestBlockRule();
        rule.extensionId = info.id;
        rule.domain = domain;
        requestBlockRules.add(rule);
    }

    @Override
    public void addRequestObserver(ExtensionInfo info, String domain, ExtensionRequestHandler handler) {
        RequestObserverRule rule = new RequestObserverRule();
        rule.extensionId = info.id;
        rule.domain = domain;
        rule.handler = handler;
        requestObservers.add(rule);
    }

    @Override
    public void addNavigationObserver(ExtensionInfo info, String domain, ExtensionNavigationHandler handler) {
        NavigationObserverRule rule = new NavigationObserverRule();
        rule.extensionId = info.id;
        rule.domain = domain;
        rule.handler = handler;
        navigationObservers.add(rule);
    }

    @Override
    public void addNavigationBlockRule(ExtensionInfo info, String domain) {
        NavigationBlockRule rule = new NavigationBlockRule();
        rule.extensionId = info.id;
        rule.domain = domain;
        navigationBlockRules.add(rule);
    }

    @Override
    public void addCrossDomainNavigationBlockRule(ExtensionInfo info) {
        if (info == null) return;
        for (int i = 0; i < crossDomainNavigationBlockRules.size(); i++) {
            if (info.id.equals(crossDomainNavigationBlockRules.get(i).extensionId)) return;
        }
        CrossDomainNavigationBlockRule rule = new CrossDomainNavigationBlockRule();
        rule.extensionId = info.id;
        crossDomainNavigationBlockRules.add(rule);
    }

    @Override
    public void addUserAgentRule(ExtensionInfo info, String domain, String userAgent) {
        UserAgentRule rule = new UserAgentRule();
        rule.extensionId = info.id;
        rule.domain = domain;
        rule.userAgent = userAgent;
        userAgentRules.add(rule);
    }

    @Override
    public void addScriptRule(ExtensionInfo info, String domain, String relativeScriptPath) {
        ScriptRule rule = new ScriptRule();
        rule.extensionId = info.id;
        rule.domain = domain;
        rule.path = relativeScriptPath;
        scriptRules.add(rule);
    }

    @Override
    public void registerBridgeAction(ExtensionInfo info, String method, String allowedOrigin, ExtensionBridgeHandler handler) {
        BridgeRegistration registration = new BridgeRegistration();
        registration.extensionId = info.id;
        registration.method = method;
        registration.origin = allowedOrigin;
        registration.handler = handler;
        bridgeRegistrations.add(registration);
    }

    public String dispatchBridge(String pageUrl, String method, String jsonArguments) {
        JSONObject response = new JSONObject();
        if (method == null || method.length() == 0) {
            try { response.put("OK", false); response.put("error", "method kosong"); } catch (Exception ignored) { }
            return response.toString();
        }
        for (int i = 0; i < bridgeRegistrations.size(); i++) {
            BridgeRegistration registration = bridgeRegistrations.get(i);
            if (!registration.method.equals(method)) continue;
            ExtensionInfo info = extensionManager.getExtension(registration.extensionId);
            if (info == null || !extensionManager.isEnabled(info) || !extensionManager.hasPermission(info, "nativeBridge")) continue;
            if (!extensionManager.matchesDomainLock(info, pageUrl)) continue;
            if (!ExtensionManager.matchesDomain(pageUrl, registration.origin)) continue;
            try {
                String result = registration.handler.invoke(jsonArguments == null ? "{}" : jsonArguments);
                response.put("OK", true);
                response.put("result", result == null ? JSONObject.NULL : result);
                return response.toString();
            } catch (Throwable error) {
                try { response.put("OK", false); response.put("error", error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage())); }
                catch (Exception ignored) { }
                extensionLog(registration.extensionId, "bridge " + method + " gagal: " + error.toString());
                return response.toString();
            }
        }
        try { response.put("OK", false); response.put("error", "bridge tidak terdaftar atau origin tidak diizinkan"); }
        catch (Exception ignored) { }
        return response.toString();
    }

    @Override
    public void startLocalServer(ExtensionInfo info, int port, String directory) throws Exception {
        if (!extensionManager.hasPermission(info, "localServer")) throw new SecurityException("permission localServer belum diberikan");
        synchronized (localServers) {
            LocalHttpServer old = localServers.get(Integer.valueOf(port));
            if (old != null && old.isRunning()) throw new IllegalStateException("port sudah dipakai oleh server Chromitext");
            LocalHttpServer server = new LocalHttpServer(port, directory);
            localServers.put(Integer.valueOf(port), server);
            localServerOwners.put(Integer.valueOf(port), info.id);
        }
    }

    @Override
    public void stopLocalServer(ExtensionInfo info, int port) {
        synchronized (localServers) {
            LocalHttpServer server = localServers.get(Integer.valueOf(port));
            String owner = localServerOwners.get(Integer.valueOf(port));
            if (server != null && info != null && info.id.equals(owner)) {
                server.stop();
                localServers.remove(Integer.valueOf(port));
                localServerOwners.remove(Integer.valueOf(port));
            }
        }
    }

    private void stopInvalidLocalServers() {
        synchronized (localServers) {
            ArrayList<Integer> remove = new ArrayList<Integer>();
            for (Map.Entry<Integer, LocalHttpServer> entry : localServers.entrySet()) {
                String owner = localServerOwners.get(entry.getKey());
                ExtensionInfo info = owner == null ? null : extensionManager.getExtension(owner);
                if (info == null || !extensionManager.isEnabled(info) || !extensionManager.hasPermission(info, "localServer")) {
                    entry.getValue().stop();
                    remove.add(entry.getKey());
                }
            }
            for (int i = 0; i < remove.size(); i++) {
                localServers.remove(remove.get(i));
                localServerOwners.remove(remove.get(i));
            }
        }
    }

    @Override
    protected void onPause() {
        saveSession();
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        saveSession();
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (addressSuggestionController != null && addressSuggestionController.isShowing()) { addressSuggestionController.hide(); return; }
        if (customViewActive) { exitCustomView(true); return; }
        if (activePageView != null) { backFromPage(); return; }
        if (debuggerOverlay != null && debuggerOverlay.isPanelVisible()) { hideDebuggerPanel(); return; }
        if (activeTab != null && activeTab.webView.canGoBack()) activeTab.webView.goBack();
        else if (tabs.size() > 1) closeActiveTab();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        addressSuggestionController.hide();
        extensionRuntimeGeneration++;
        if (customViewActive) exitCustomView(false);
        for (int i = 0; i < tabs.size(); i++) {
            try {
                WebView web = tabs.get(i).webView;
                if (web != null) {
                    web.stopLoading();
                    web.removeJavascriptInterface("ChromitextBridge");
                    if (web.getParent() instanceof ViewGroup) ((ViewGroup) web.getParent()).removeView(web);
                    web.destroy();
                }
            } catch (Exception ignored) { }
        }
        synchronized (localServers) {
            for (LocalHttpServer server : localServers.values()) {
                try { server.stop(); } catch (Exception ignored) { }
            }
            localServers.clear();
            localServerOwners.clear();
        }
        super.onDestroy();
        boolean closeDatabase = false;
        synchronized (this) {
            if (activeBackgroundWorkCount == 0 && !databaseClosed) {
                databaseClosed = true;
                closeDatabase = true;
            }
        }
        if (closeDatabase && database != null) {
            try { database.close(); } catch (Exception ignored) { }
        }
        // Active extension/file workers close SQLite after their final task exits.
    }

    private static class WebViewDatabaseHelper {
        static void clear(Context context) {
            try {
                android.webkit.WebStorage.getInstance().deleteAllData();
                android.webkit.WebViewDatabase.getInstance(context).clearFormData();
                android.webkit.WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword();
                android.webkit.WebViewDatabase.getInstance(context).clearUsernamePassword();
            } catch (Exception ignored) { }
        }
    }
}
