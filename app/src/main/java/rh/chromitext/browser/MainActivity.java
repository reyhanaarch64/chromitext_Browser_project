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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class MainActivity extends Activity implements ExtensionHost {
    private static final int REQUEST_INSTALL_ZIP = 7101;
    private static final int REQUEST_WEB_FILE = 7102;
    private static final String DEBUG_NETWORK_PREFIX = "__CHROMITEXT_NET__";
    private static final int REQUEST_STORAGE_PERMISSION = 7103;
    private int COLOR_PANEL = Color.rgb(214, 211, 204);
    private int COLOR_BUTTON = Color.rgb(235, 232, 224);
    private int COLOR_BORDER = Color.rgb(139, 136, 128);
    private int COLOR_TEXT = Color.rgb(30, 30, 30);
    private int COLOR_ACCENT = Color.rgb(36, 92, 132);
    private boolean darkTheme;
    private int configuredSearchHeight = 42;
    private int configuredToolbarHeight = 42;
    private int configuredIconSize = 15;
    private int configuredToolbarGap = 1;
    private boolean searchAtBottom;

    private LinearLayout rootLayout;
    private FrameLayout appFrame;
    private FrameLayout browserLayer;
    private View activePageView;
    private boolean replaceNextPage;
    private final ArrayList<View> pageStack = new ArrayList<View>();
    private BrowserDebuggerOverlay debuggerOverlay;
    private BrowserPageLayout browserPageLayout;
    private boolean debuggerEnabled;
    private boolean customViewActive;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private PermissionRequest pendingWebPermission;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;
    private static final int REQUEST_WEB_PERMISSION = 7120;
    private static final int REQUEST_GEO_PERMISSION = 7121;
    private EditText addressBar;
    private TextView clearAddressButton;
    private LinearLayout addressRowView;
    private PopupWindow addressSuggestionPopup;
    private LinearLayout addressSuggestionList;
    private final Handler addressSuggestionHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingAddressSuggestionTask;
    private int addressSuggestionRequestId;
    private FrameLayout webFrame;
    private TextView statusView;
    private TextView tabSummary;
    private final List<BrowserTab> tabs = new ArrayList<BrowserTab>();
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
    private BrowserTab activeTab;
    private ValueCallback<Uri[]> fileChooserCallback;
    private ExtensionManager.PreparedInstall pendingInstall;
    private BrowserDatabase database;
    private volatile boolean extensionRuntimeLoaded;
    private volatile boolean activityDestroyed;
    private volatile int extensionRuntimeGeneration;
    private final Object extensionRuntimeProcessingLock = new Object();
    private int activeBackgroundWorkCount;
    private boolean databaseClosed;
    private String globalUserAgentMode = "default";
    private String customUserAgent = "";
    private boolean javascriptEnabled = true;

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

    private static class AddressSuggestion {
        String title;
        String url;
        boolean history;
        AddressSuggestion(String title, String url, boolean history) {
            this.title = title == null ? "" : title;
            this.url = url == null ? "" : url;
            this.history = history;
        }
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        database = new BrowserDatabase(this);
        database.migrateLegacyPreferences(this);
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
        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setTextSize(Math.max(12, configuredIconSize - 1));
        addressBar.setTextColor(COLOR_TEXT);
        addressBar.setHintTextColor(darkTheme ? Color.rgb(160, 166, 174) : Color.rgb(100, 100, 100));
        addressBar.setHint("Cari atau masukkan URL");
        addressBar.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setPadding(dp(9), 0, dp(7), 0);
        addressBar.setSelectAllOnFocus(true);
        addressBar.setBackground(boxBackground(darkTheme ? Color.rgb(29, 33, 39) : Color.rgb(255, 255, 255), COLOR_BORDER));
        addressRow.addView(addressBar, new LinearLayout.LayoutParams(0, dp(configuredSearchHeight), 1));
        clearAddressButton = new TextView(this);
        clearAddressButton.setText("×");
        clearAddressButton.setTextSize(24);
        clearAddressButton.setTextColor(COLOR_TEXT);
        clearAddressButton.setGravity(Gravity.CENTER);
        clearAddressButton.setSingleLine(true);
        clearAddressButton.setContentDescription("Kosongkan address bar");
        clearAddressButton.setBackground(boxBackground(COLOR_BUTTON, COLOR_BORDER));
        clearAddressButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(dp(Math.max(30, configuredSearchHeight - 4)), dp(configuredSearchHeight));
        clearParams.leftMargin = dp(3);
        addressRow.addView(clearAddressButton, clearParams);
        TextView goButton = iconButton("solid", 0xf061, "go", "go");
        goButton.setContentDescription("Buka Alamat");
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
        statusView.setText("Siap");

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
                    scheduleAddressSuggestions(addressBar.getText().toString(), 0);
                } else {
                    hideAddressSuggestions();
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
                    scheduleAddressSuggestions(text, 300);
                } else {
                    hideAddressSuggestions();
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
                scheduleAddressSuggestions("", 0);
            }
        });
    }

    private void scheduleAddressSuggestions(final String rawQuery, int delayMs) {
        if (pendingAddressSuggestionTask != null) addressSuggestionHandler.removeCallbacks(pendingAddressSuggestionTask);
        final int requestId = ++addressSuggestionRequestId;
        pendingAddressSuggestionTask = new Runnable() {
            public void run() {
                if (activityDestroyed || addressBar == null || !addressBar.hasFocus() || browserLayer.getVisibility() != View.VISIBLE) return;
                String query = rawQuery == null ? "" : rawQuery.trim();
                final ArrayList<AddressSuggestion> local = getHistorySuggestions(query, query.length() == 0 ? 6 : 4);
                renderAddressSuggestions(local, query);
                if (query.length() == 0) return;
                fetchGoogleSuggestions(query, requestId);
            }
        };
        addressSuggestionHandler.postDelayed(pendingAddressSuggestionTask, Math.max(0, delayMs));
    }

    private ArrayList<AddressSuggestion> getHistorySuggestions(String query, int limit) {
        ArrayList<AddressSuggestion> result = new ArrayList<AddressSuggestion>();
        ArrayList<String> lines = readLines("history");
        Collections.reverse(lines);
        HashSet<String> seen = new HashSet<String>();
        String lowerQuery = query == null ? "" : query.toLowerCase(Locale.US);
        for (int i = 0; i < lines.size() && result.size() < limit; i++) {
            String[] pair = lines.get(i).split("\t", 2);
            if (pair.length != 2 || pair[1].length() == 0) continue;
            String title = pair[0];
            String url = pair[1];
            if (seen.contains(url)) continue;
            if (lowerQuery.length() > 0 && title.toLowerCase(Locale.US).indexOf(lowerQuery) < 0 && url.toLowerCase(Locale.US).indexOf(lowerQuery) < 0) continue;
            seen.add(url);
            result.add(new AddressSuggestion(title.length() == 0 ? Uri.parse(url).getHost() : title, url, true));
        }
        return result;
    }

    private void fetchGoogleSuggestions(final String query, final int requestId) {
        Thread worker = new Thread(new Runnable() {
            public void run() {
                HttpURLConnection connection = null;
                BufferedReader reader = null;
                final ArrayList<String> values = new ArrayList<String>();
                try {
                    String encoded = URLEncoder.encode(query, "UTF-8");
                    URL endpoint = new URL("https://suggestqueries.google.com/complete/search?client=firefox&q=" + encoded);
                    connection = (HttpURLConnection) endpoint.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(2500);
                    connection.setReadTimeout(2500);
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36");
                    int status = connection.getResponseCode();
                    if (status >= 200 && status < 300) {
                        reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                        StringBuilder payload = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null && payload.length() < 128000) payload.append(line);
                        JSONArray root = new JSONArray(payload.toString());
                        if (root.length() > 1 && root.opt(1) instanceof JSONArray) {
                            JSONArray suggestions = root.getJSONArray(1);
                            for (int i = 0; i < suggestions.length() && values.size() < 5; i++) {
                                String value = suggestions.optString(i, "");
                                if (value.length() > 0 && !values.contains(value)) values.add(value);
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // Search suggestions are optional; local history remains available offline.
                } finally {
                    if (reader != null) try { reader.close(); } catch (Exception ignored) { }
                    if (connection != null) connection.disconnect();
                }
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (activityDestroyed || requestId != addressSuggestionRequestId || addressBar == null || !addressBar.hasFocus()) return;
                        String current = addressBar.getText().toString().trim();
                        if (!current.equals(query)) return;
                        ArrayList<AddressSuggestion> merged = getHistorySuggestions(query, 4);
                        HashSet<String> seen = new HashSet<String>();
                        for (int i = 0; i < merged.size(); i++) {
                            seen.add(merged.get(i).url.toLowerCase(Locale.US));
                            seen.add(merged.get(i).title.toLowerCase(Locale.US));
                        }
                        for (int i = 0; i < values.size(); i++) {
                            String value = values.get(i);
                            String key = value.toLowerCase(Locale.US);
                            if (!seen.contains(key)) {
                                merged.add(new AddressSuggestion(value, value, false));
                                seen.add(key);
                            }
                        }
                        renderAddressSuggestions(merged, query);
                    }
                });
            }
        }, "Chromitext-SearchSuggestions");
        worker.setDaemon(true);
        worker.start();
    }

    private void renderAddressSuggestions(ArrayList<AddressSuggestion> suggestions, String query) {
        if (activityDestroyed || addressRowView == null || addressBar == null || !addressBar.hasFocus()) return;
        if (browserLayer.getVisibility() != View.VISIBLE) { hideAddressSuggestions(); return; }
        if (suggestions == null || suggestions.size() == 0) {
            if (addressSuggestionPopup != null) {
                try { addressSuggestionPopup.dismiss(); } catch (Exception ignored) { }
                addressSuggestionPopup = null;
            }
            addressSuggestionList = null;
            return;
        }
        if (addressSuggestionPopup != null) {
            try { addressSuggestionPopup.dismiss(); } catch (Exception ignored) { }
            addressSuggestionPopup = null;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(0, dp(2), 0, dp(2));
        panel.setBackground(boxBackground(darkTheme ? Color.rgb(29, 33, 39) : Color.WHITE, COLOR_BORDER));
        addressSuggestionList = panel;
        boolean hasHistory = false;
        for (int i = 0; i < suggestions.size(); i++) if (suggestions.get(i).history) hasHistory = true;
        boolean dividerAdded = false;
        for (int i = 0; i < suggestions.size(); i++) {
            final AddressSuggestion suggestion = suggestions.get(i);
            if (hasHistory && !suggestion.history && !dividerAdded) {
                View divider = new View(this);
                divider.setBackgroundColor(COLOR_BORDER);
                panel.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
                TextView label = new TextView(this);
                label.setText("SARAN GOOGLE");
                label.setTextColor(darkTheme ? Color.rgb(160, 166, 174) : Color.rgb(100, 100, 100));
                label.setTextSize(10);
                label.setPadding(dp(12), dp(5), dp(8), dp(2));
                panel.addView(label, new LinearLayout.LayoutParams(-1, -2));
                dividerAdded = true;
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(11), dp(6), dp(10), dp(6));
            row.setBackgroundColor(darkTheme ? Color.rgb(29, 33, 39) : Color.WHITE);
            TextView icon = new TextView(this);
            FontAwesome.apply(this, icon, "solid", suggestion.history ? 0xf1da : 0xf002, suggestion.history ? "◷" : "⌕");
            icon.setTextSize(18);
            icon.setGravity(Gravity.CENTER);
            icon.setTextColor(suggestion.history ? Color.rgb(22, 133, 232) : (darkTheme ? Color.rgb(170, 177, 187) : Color.rgb(105, 105, 105)));
            row.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(38)));
            LinearLayout textColumn = new LinearLayout(this);
            textColumn.setOrientation(LinearLayout.VERTICAL);
            TextView heading = new TextView(this);
            heading.setText(suggestion.title);
            heading.setTextSize(14);
            heading.setSingleLine(true);
            heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
            heading.setTextColor(suggestion.history ? Color.rgb(22, 133, 232) : COLOR_TEXT);
            textColumn.addView(heading, new LinearLayout.LayoutParams(-1, -2));
            TextView detail = new TextView(this);
            detail.setText(suggestion.history ? suggestion.url : "Cari di Google");
            detail.setTextSize(11);
            detail.setSingleLine(true);
            detail.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            detail.setTextColor(suggestion.history ? Color.rgb(22, 133, 232) : (darkTheme ? Color.rgb(160, 166, 174) : Color.rgb(100, 100, 100)));
            textColumn.addView(detail, new LinearLayout.LayoutParams(-1, -2));
            row.addView(textColumn, new LinearLayout.LayoutParams(0, -2, 1));
            TextView action = new TextView(this);
            FontAwesome.apply(this, action, "solid", 0xf0da, "↖");
            action.setTextSize(16);
            action.setTextColor(darkTheme ? Color.rgb(160, 166, 174) : Color.rgb(100, 100, 100));
            action.setGravity(Gravity.CENTER);
            row.addView(action, new LinearLayout.LayoutParams(dp(28), dp(38)));
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    hideAddressSuggestions();
                    if (suggestion.history) {
                        hideKeyboard();
                        navigate(suggestion.url);
                    } else {
                        String searchUrl = "https://www.google.com/search?q=" + Uri.encode(suggestion.title);
                        hideKeyboard();
                        navigate(searchUrl);
                    }
                }
            });
            panel.addView(row, new LinearLayout.LayoutParams(-1, -2));
            if (i < suggestions.size() - 1) {
                View separator = new View(this);
                separator.setBackgroundColor(darkTheme ? Color.rgb(55, 60, 68) : Color.rgb(230, 230, 230));
                panel.addView(separator, new LinearLayout.LayoutParams(-1, dp(1)));
            }
        }
        scroll.addView(panel, new ScrollView.LayoutParams(-1, -2));
        scroll.setBackgroundColor(darkTheme ? Color.rgb(29, 33, 39) : Color.WHITE);
        scroll.setLayoutParams(new ViewGroup.LayoutParams(Math.max(dp(220), addressRowView.getWidth()), -2));
        scroll.measure(View.MeasureSpec.makeMeasureSpec(Math.max(dp(220), addressRowView.getWidth()), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(dp(500), View.MeasureSpec.AT_MOST));
        int popupHeight = Math.min(scroll.getMeasuredHeight(), dp(360));
        if (popupHeight <= 0) popupHeight = dp(120);
        addressSuggestionPopup = new PopupWindow(scroll, Math.max(dp(220), addressRowView.getWidth()), popupHeight, false);
        addressSuggestionPopup.setBackgroundDrawable(boxBackground(darkTheme ? Color.rgb(29, 33, 39) : Color.WHITE, COLOR_BORDER));
        if (Build.VERSION.SDK_INT >= 21) addressSuggestionPopup.setElevation(dp(4));
        addressSuggestionPopup.setOutsideTouchable(true);
        addressSuggestionPopup.setTouchable(true);
        addressSuggestionPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        addressSuggestionPopup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        addressSuggestionPopup.showAsDropDown(addressRowView, 0, dp(1));
    }

    private void hideAddressSuggestions() {
        if (pendingAddressSuggestionTask != null) {
            addressSuggestionHandler.removeCallbacks(pendingAddressSuggestionTask);
            pendingAddressSuggestionTask = null;
        }
        addressSuggestionRequestId++;
        if (addressSuggestionPopup != null) {
            try { addressSuggestionPopup.dismiss(); } catch (Exception ignored) { }
            addressSuggestionPopup = null;
        }
        addressSuggestionList = null;
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

    private GradientDrawable boxBackground(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(0);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

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
                tab.loading = true;
                if (activeTab == tab) {
                    addressBar.setText(tab.url);
                    statusView.setText("memuat: " + tab.url);
                }
                updateTabSummary();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                tab.url = url == null ? tab.url : url;
                tab.loading = false;
                if (activeTab == tab) {
                    addressBar.setText(tab.url);
                    statusView.setText("selesai dimuat");
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
                if (activeTab == tab) statusView.setText("error: " + description);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (Build.VERSION.SDK_INT >= 23 && error != null) {
                    addDebuggerConsole(tab, "[web error] " + error.getErrorCode() + " " + error.getDescription());
                    if (request != null && request.isForMainFrame() && activeTab == tab) {
                        statusView.setText("error: " + error.getDescription());
                    }
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                addDebuggerConsole(tab, "[ssl error] koneksi dibatalkan: " + String.valueOf(error));
                handler.cancel();
                if (activeTab == tab) statusView.setText("sertifikat ssl tidak valid");
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                tab.title = title == null || title.length() == 0 ? "tanpa judul" : title;
                updateTabSummary();
            }

            @Override
            public void onProgressChanged(WebView view, int progress) {
                if (activeTab == tab && tab.loading) statusView.setText("memuat " + progress + "%");
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
                if (activeTab == tab) statusView.setText("console: " + rawMessage);
                return true;
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(final String origin, final GeolocationPermissions.Callback callback) {
                new AlertDialog.Builder(MainActivity.this).setTitle("Izin Lokasi")
                    .setMessage(origin + " meminta akses lokasi perangkat. izinkan?")
                    .setPositiveButton("Izinkan", new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int which) {
                        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            pendingGeoCallback = callback;
                            pendingGeoOrigin = origin;
                            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_GEO_PERMISSION);
                        } else callback.invoke(origin, true, false);
                    }})
                    .setNegativeButton("Tolak", new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int which) { callback.invoke(origin, false, false); }})
                    .setOnCancelListener(new DialogInterface.OnCancelListener() { public void onCancel(DialogInterface dialog) { callback.invoke(origin, false, false); }})
                    .show();
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                if (Build.VERSION.SDK_INT < 21) { request.deny(); return; }
                new AlertDialog.Builder(MainActivity.this).setTitle("Izin Website")
                    .setMessage(String.valueOf(request.getOrigin()) + " meminta akses perangkat: " + java.util.Arrays.toString(request.getResources()) + ". izinkan?")
                    .setPositiveButton("Izinkan", new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int which) {
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
                    .setNegativeButton("Tolak", new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int which) { request.deny(); }})
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
                new AlertDialog.Builder(MainActivity.this).setTitle("Pesan dari Halaman")
                    .setMessage(message).setPositiveButton("OK", new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int which) { result.confirm(); }})
                    .setOnCancelListener(new DialogInterface.OnCancelListener() { public void onCancel(DialogInterface dialog) { result.cancel(); }})
                    .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this).setTitle("Konfirmasi Halaman")
                    .setMessage(message).setPositiveButton("OK", new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int which) { result.confirm(); }})
                    .setNegativeButton("Batal", new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int which) { result.cancel(); }})
                    .setOnCancelListener(new DialogInterface.OnCancelListener() { public void onCancel(DialogInterface dialog) { result.cancel(); }})
                    .show();
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, final JsPromptResult result) {
                final EditText input = new EditText(MainActivity.this);
                input.setText(defaultValue == null ? "" : defaultValue);
                new AlertDialog.Builder(MainActivity.this).setTitle("Input dari Halaman")
                    .setMessage(message).setView(input)
                    .setPositiveButton("Kirim", new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int which) { result.confirm(input.getText().toString()); }})
                    .setNegativeButton("Batal", new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int which) { result.cancel(); }})
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
            catch (Exception error) { showToast("Tidak ada aplikasi yang tersedia untuk membuka alamat ini."); }
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
        statusView.setText("meminta halaman...");
        try { activeTab.webView.loadUrl(url); }
        catch (Exception error) { statusView.setText("gagal membuka url: " + error.getMessage()); }
    }

    private void showBlockedPage(BrowserTab tab, String url) {
        tab.url = url;
        tab.bridgeUrl = "about:blank";
        tab.title = "akses diblokir";
        String safe = TextUtils.htmlEncode(url);
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{font-family:sans-serif;margin:24px;color:#222}h2{font-size:21px}code{overflow-wrap:anywhere;color:#8b2727}hr{border:0;border-top:1px solid #ccc}</style></head><body><h2>akses diblokir</h2><p>alamat ini diblokir oleh extension browser.</p><hr><code>" + safe + "</code><p>ubah aturan extension untuk membuka alamat ini.</p></body></html>";
        tab.webView.loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null);
        if (activeTab == tab) {
            addressBar.setText(url);
            statusView.setText("navigasi diblokir oleh extension");
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
            if (isCrossDomainNavigation(sourceUrl, targetUrl)) return true;
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

    private boolean isCrossDomainNavigation(String sourceUrl, String targetUrl) {
        Uri source = Uri.parse(sourceUrl == null ? "" : sourceUrl);
        Uri target = Uri.parse(targetUrl == null ? "" : targetUrl);
        String sourceScheme = source.getScheme();
        String targetScheme = target.getScheme();
        if (sourceScheme == null || targetScheme == null) return false;
        if (!("http".equalsIgnoreCase(sourceScheme) || "https".equalsIgnoreCase(sourceScheme))) return false;
        if (!("http".equalsIgnoreCase(targetScheme) || "https".equalsIgnoreCase(targetScheme))) return false;
        String sourceHost = normalizeNavigationHost(source.getHost());
        String targetHost = normalizeNavigationHost(target.getHost());
        if (sourceHost.length() == 0 || targetHost.length() == 0) return false;
        if (sourceHost.equals(targetHost)) return false;
        // Permit current hostname and all of its subdomains, e.g. myweb.id -> api.myweb.id.
        if (targetHost.endsWith("." + sourceHost)) return false;
        return true;
    }

    private String normalizeNavigationHost(String host) {
        if (host == null) return "";
        String value = host.trim().toLowerCase(java.util.Locale.US);
        while (value.endsWith(".") && value.length() > 1) value = value.substring(0, value.length() - 1);
        return value;
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
        if ("desktop".equals(globalUserAgentMode)) {
            String current = WebSettings.getDefaultUserAgent(this);
            String version = "120.0.0.0";
            int chrome = current.indexOf("Chrome/");
            if (chrome >= 0) {
                int begin = chrome + "Chrome/".length();
                int end = current.indexOf(' ', begin);
                if (end < 0) end = current.length();
                String found = current.substring(begin, end);
                if (found.matches("[0-9.]+")) version = found;
            }
            return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + version + " Safari/537.36";
        }
        if ("safari".equals(globalUserAgentMode)) {
            return "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1";
        }
        if ("custom".equals(globalUserAgentMode) && customUserAgent.length() > 0) return customUserAgent;
        // The WebView default UA is the closest built-in Android Chrome-like preset.
        return WebSettings.getDefaultUserAgent(this);
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

    private void syncNetworkDebuggerHooks() {
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
        StringBuilder js = new StringBuilder();
        js.append("(function(){var w=window;");
        js.append("if(w.__chromitextNetInstalled){w.__chromitextNetActive=true;return;}");
        js.append("w.__chromitextNetInstalled=true;w.__chromitextNetActive=true;");
        js.append("function emit(o){if(!w.__chromitextNetActive)return;try{console.info('__CHROMITEXT_NET__'+JSON.stringify(o));}catch(e){}}");
        js.append("function clip(v,n){try{v=String(v==null?'':v);return v.length>n?v.slice(0,n)+' [truncated]':v;}catch(e){return '[unavailable]';}}");
        js.append("function headersOf(h){var o={};try{if(!h)return o;if(typeof Headers!=='undefined'&&h instanceof Headers){h.forEach(function(v,k){o[k]=v;});}else if(Array.isArray(h)){h.forEach(function(p){if(p&&p.length>1)o[p[0]]=p[1];});}else{Object.keys(h).forEach(function(k){o[k]=h[k];});}}catch(e){}return o;}");
        js.append("var oldFetch=w.fetch;if(typeof oldFetch==='function'){w.fetch=function(input,init){");
        js.append("var u=typeof input==='string'?input:((input&&input.url)||String(input));var m=(init&&init.method)||((input&&input.method)||'GET');");
        js.append("var rh=headersOf((init&&init.headers)||((input&&input.headers)||null));var rb=(init&&typeof init.body!=='undefined')?clip(init.body,4000):'';var start=Date.now();");
        js.append("return oldFetch.apply(this,arguments).then(function(r){var h={};try{r.headers.forEach(function(v,k){h[k]=v;});}catch(e){}");
        js.append("var emitResponse=function(body){emit({type:'fetch',method:m,url:r.url||u,status:r.status,statusText:r.statusText,requestHeaders:rh,requestBody:rb,headers:h,responseBody:clip(body,5000),ms:Date.now()-start});};");
        js.append("try{r.clone().text().then(function(body){emitResponse(body);},function(){emitResponse('[response body unavailable]');});}catch(e){emitResponse('[response body unavailable]');}return r;},");
        js.append("function(e){emit({type:'fetch',method:m,url:u,status:0,error:String(e),requestHeaders:rh,requestBody:rb,ms:Date.now()-start});throw e;});};}");
        js.append("var p=w.XMLHttpRequest&&w.XMLHttpRequest.prototype;if(p&&p.open&&p.send){var oldOpen=p.open;var oldSend=p.send;var oldSet=p.setRequestHeader;");
        js.append("p.open=function(m,u){this.__chromitextMethod=m;this.__chromitextUrl=u;this.__chromitextHeaders={};return oldOpen.apply(this,arguments);};");
        js.append("if(oldSet)p.setRequestHeader=function(k,v){try{this.__chromitextHeaders=this.__chromitextHeaders||{};this.__chromitextHeaders[k]=v;}catch(e){}return oldSet.apply(this,arguments);};");
        js.append("p.send=function(body){var x=this;x.__chromitextStart=Date.now();x.__chromitextBody=clip(body,4000);");
        js.append("if(!x.__chromitextBound){x.__chromitextBound=true;x.addEventListener('loadend',function(){var h={};try{var raw=x.getAllResponseHeaders().trim();");
        js.append("if(raw){raw.split(/\\r?\\n/).forEach(function(line){var n=line.indexOf(':');if(n>0)h[line.slice(0,n).trim()]=line.slice(n+1).trim();});}}catch(e){}");
        js.append("var bodyText='';try{bodyText=clip(x.responseText,5000);}catch(e){bodyText='[response body unavailable]';}");
        js.append("emit({type:'xhr',method:x.__chromitextMethod||'GET',url:x.responseURL||x.__chromitextUrl||'',status:x.status,statusText:x.statusText,requestHeaders:x.__chromitextHeaders||{},requestBody:x.__chromitextBody||'',headers:h,responseBody:bodyText,ms:Date.now()-(x.__chromitextStart||Date.now())});});}");
        js.append("return oldSend.apply(this,arguments);};}");
        js.append("})();");
        try { tab.webView.evaluateJavascript(js.toString(), null); }
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
            statusView.setText(tab.loading ? "memuat..." : "Siap");
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
        tabSummary.setText("tabs: " + tabs.size() + "  |  aktif: " + (tabs.indexOf(activeTab) + 1) + "  |  " + name);
    }

    private LinearLayout openPage(final String title) {
        hideAddressSuggestions();
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

    private void backFromPage() {
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

    private void replaceCurrentPage() { replaceNextPage = true; }

    private void addPageAction(LinearLayout body, int iconCode, String title, String description, View.OnClickListener listener) {
        browserPageLayout.addAction(body, iconCode, title, description, listener);
    }

    private void addPageNote(LinearLayout body, String value) {
        browserPageLayout.addNote(body, value);
    }

    private void showMainMenu() {
        LinearLayout body = openPage("Menu");
        addPageAction(body, 0xf02e, "Bookmark", "Halaman yang disimpan", new View.OnClickListener() { public void onClick(View v) { showBookmarks(); } });
        addPageAction(body, 0xf1da, "Riwayat Penjelajahan", "Daftar halaman yang pernah dibuka", new View.OnClickListener() { public void onClick(View v) { showHistory(); } });
        addPageAction(body, 0xf019, "Unduhan", "File unduhan dikelola Android", new View.OnClickListener() { public void onClick(View v) { showDownloadsInfo(); } });
        addPageAction(body, 0xf12e, "Extension", "Aktifkan atau nonaktifkan extension dengan cepat", new View.OnClickListener() { public void onClick(View v) { backFromPage(); showQuickExtensionPopup(); } });
        addPageAction(body, 0xf013, "Pengaturan", "Preferensi, privasi, keamanan, dan debugging", new View.OnClickListener() { public void onClick(View v) { showSettings(); } });
        addPageAction(body, 0xf188, "Developer Tools", "Console dan Network untuk tab aktif", new View.OnClickListener() { public void onClick(View v) { showDebuggerSettings(); } });
        addPageAction(body, 0xf2f1, "JavaScript", "Status: " + (javascriptEnabled ? "Aktif" : "Nonaktif"), new View.OnClickListener() { public void onClick(View v) { toggleJavascript(); replaceCurrentPage(); showMainMenu(); } });
        addPageAction(body, 0xf108, "Mode Desktop" + ("desktop".equals(globalUserAgentMode) ? " [aktif]" : " [nonaktif]"), "Ganti User-Agent ke Chrome desktop dan sesuaikan viewport halaman", new View.OnClickListener() { public void onClick(View v) { toggleDesktopMode(); } });
        addPageAction(body, 0xf12a, "Hapus Data Situs", "Hapus cache, cookie, dan web storage", new View.OnClickListener() { public void onClick(View v) { clearBrowserData(); } });
        addPageAction(body, 0xf05a, "Tentang Chromitext", "Informasi aplikasi", new View.OnClickListener() { public void onClick(View v) { showAbout(); } });
    }

    private void showSettings() {
        LinearLayout body = openPage("Pengaturan");
        addPageAction(body, 0xf0ad, "Preferensi", "Tampilan, user-agent global, JavaScript, dan halaman awal", new View.OnClickListener() { public void onClick(View v) { showPreferenceSettings(); } });
        addPageAction(body, 0xf21b, "Privasi", "Cookie per domain dan data situs", new View.OnClickListener() { public void onClick(View v) { showPrivacySettings(); } });
        addPageAction(body, 0xf132, "Keamanan", "Validasi sertifikat SSL dan penghapusan data", new View.OnClickListener() { public void onClick(View v) { showSecuritySettings(); } });
        addPageAction(body, 0xf188, "Debugging", "Developer Tools dan konfigurasi modul", new View.OnClickListener() { public void onClick(View v) { showDebuggerSettings(); } });
        addPageAction(body, 0xf12e, "Ekstensi", "Kelola extension dan izin akses", new View.OnClickListener() { public void onClick(View v) { showExtensionManager(); } });
        addPageAction(body, 0xf05a, "Tentang", "Informasi aplikasi dan komponen", new View.OnClickListener() { public void onClick(View v) { showAbout(); } });
    }

    private void showPreferenceSettings() {
        LinearLayout body = openPage("Preferensi");
        addPageAction(body, 0xf1ab, "User-Agent Global", "Saat ini: " + userAgentModeLabel(), new View.OnClickListener() { public void onClick(View v) { showUserAgentSettings(); } });
        addPageAction(body, 0xf11c, "JavaScript", javascriptEnabled ? "Aktif" : "Nonaktif", new View.OnClickListener() { public void onClick(View v) { toggleJavascript(); replaceCurrentPage(); showPreferenceSettings(); } });
        addPageAction(body, 0xf015, "Halaman Awal", "Atur URL yang dibuka melalui tombol Beranda", new View.OnClickListener() { public void onClick(View v) { editHomepage(); } });
        addPageAction(body, 0xf042, "Tampilan Aplikasi", "Tema, posisi address bar, dan ukuran toolbar", new View.OnClickListener() { public void onClick(View v) { showAppearanceSettings(); } });
        addPageNote(body, "User-Agent berlaku secara global untuk semua situs. Penggantian per domain hanya tersedia melalui extension.");
    }

    private void showAppearanceSettings() {
        LinearLayout body = openPage("Tampilan Aplikasi");
        addPageAction(body, 0xf042, "Tema Aplikasi", getThemeLabel(), new View.OnClickListener() { public void onClick(View v) {
            showSingleChoiceSetting("Tema Aplikasi", "ui_theme", new String[]{"system", "light", "dark"}, new String[]{"Ikuti Sistem", "Terang", "Gelap"}, true);
        }});
        addPageAction(body, 0xf0c9, "Posisi Address Bar", searchAtBottom ? "Bawah" : "Atas", new View.OnClickListener() { public void onClick(View v) {
            showSingleChoiceSetting("Posisi Address Bar", "ui_search_position", new String[]{"top", "bottom"}, new String[]{"Atas", "Bawah"}, false);
        }});
        addPageAction(body, 0xf03b, "Tinggi Address Bar", configuredSearchHeight + " dp", new View.OnClickListener() { public void onClick(View v) {
            showSingleChoiceSetting("Tinggi Address Bar", "ui_search_height", new String[]{"34", "38", "42", "46", "50", "54"}, new String[]{"34 dp — sangat ringkas", "38 dp — ringkas", "42 dp — normal", "46 dp", "50 dp", "54 dp — besar"}, false);
        }});
        addPageAction(body, 0xf150, "Tinggi Toolbar", configuredToolbarHeight + " dp", new View.OnClickListener() { public void onClick(View v) {
            showSingleChoiceSetting("Tinggi Toolbar", "ui_toolbar_height", new String[]{"34", "38", "42", "46", "50", "54"}, new String[]{"34 dp — sangat ringkas", "38 dp — ringkas", "42 dp — normal", "46 dp", "50 dp", "54 dp — besar"}, false);
        }});
        addPageAction(body, 0xf00a, "Ukuran Ikon Toolbar", configuredIconSize + " sp", new View.OnClickListener() { public void onClick(View v) {
            showSingleChoiceSetting("Ukuran Ikon Toolbar", "ui_icon_size", new String[]{"12", "14", "15", "17", "19", "22"}, new String[]{"12 sp", "14 sp", "15 sp", "17 sp", "19 sp", "22 sp"}, false);
        }});
        addPageAction(body, 0xf0c9, "Jarak Antar Tombol", configuredToolbarGap + " dp", new View.OnClickListener() { public void onClick(View v) {
            showSingleChoiceSetting("Jarak Antar Tombol", "ui_toolbar_gap", new String[]{"0", "1", "2", "3", "4", "5"}, new String[]{"0 dp — rapat", "1 dp", "2 dp", "3 dp", "4 dp", "5 dp — renggang"}, false);
        }});
        addPageNote(body, "Ukuran visual ikon dapat lebih kecil daripada area sentuh tombol untuk menjaga kemudahan penggunaan.");
    }

    private String getThemeLabel() {
        String mode = database.getSetting("ui_theme", "system");
        if ("dark".equals(mode)) return "Gelap";
        if ("light".equals(mode)) return "Terang";
        return "Ikuti Sistem";
    }

    private void showSingleChoiceSetting(final String title, final String key, final String[] values, final String[] labels, final boolean isTheme) {
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
        }).setNegativeButton("Batal", null).show();
    }

    private void rebuildBrowserUiForAppearance() {
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
        showAppearanceSettings();
    }

    private void toggleDesktopMode() {
        String nextMode = "desktop".equals(globalUserAgentMode) ? "default" : "desktop";
        globalUserAgentMode = nextMode;
        database.putSetting("user_agent_mode", globalUserAgentMode);
        applyGlobalUserAgentToTabs();
        showToast("Mode Desktop " + ("desktop".equals(globalUserAgentMode) ? "diaktifkan." : "dinonaktifkan."));
        replaceCurrentPage();
        showMainMenu();
    }

    private String userAgentModeLabel() {
        if ("desktop".equals(globalUserAgentMode)) return "Chrome desktop";
        if ("safari".equals(globalUserAgentMode)) return "Safari iOS";
        if ("custom".equals(globalUserAgentMode)) return "Custom";
        return "Default Android WebView";
    }

    private void showUserAgentSettings() {
        LinearLayout body = openPage("User-Agent Global");
        addPageAction(body, 0xf192, "Default Android / Chrome Mobile", "Gunakan User-Agent bawaan WebView", new View.OnClickListener() { public void onClick(View v) { saveUserAgent("default", ""); } });
        addPageAction(body, 0xf108, "Chrome desktop", "Gunakan identitas Chrome desktop di Linux", new View.OnClickListener() { public void onClick(View v) { saveUserAgent("desktop", ""); } });
        addPageAction(body, 0xf179, "Safari iOS", "Gunakan template User-Agent Safari mobile", new View.OnClickListener() { public void onClick(View v) { saveUserAgent("safari", ""); } });
        addPageAction(body, 0xf044, "Custom User-Agent", "Masukkan string User-Agent kustom", new View.OnClickListener() { public void onClick(View v) { promptCustomUserAgent(); } });
        addPageNote(body, "Mengubah User-Agent tidak meniru seluruh fitur, rendering, atau API browser lain.");
    }

    private void saveUserAgent(String mode, String custom) {
        globalUserAgentMode = mode;
        if (custom != null) customUserAgent = custom;
        database.putSetting("user_agent_mode", globalUserAgentMode);
        database.putSetting("user_agent_custom", customUserAgent);
        applyGlobalUserAgentToTabs();
        showToast("User-Agent global disimpan.");
        replaceCurrentPage();
        showUserAgentSettings();
    }

    private void promptCustomUserAgent() {
        final EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setText(customUserAgent);
        input.setHint("Mozilla/5.0 ...");
        new AlertDialog.Builder(this).setTitle("Custom User-Agent").setView(input)
            .setPositiveButton("Simpan", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                String value = input.getText().toString().trim();
                if (value.length() == 0) { showToast("User-Agent tidak boleh kosong."); return; }
                saveUserAgent("custom", value);
            }}).setNegativeButton("Batal", null).show();
    }

    private void applyGlobalUserAgentToTabs() {
        for (int i = 0; i < tabs.size(); i++) {
            BrowserTab tab = tabs.get(i);
            applyUserAgent(tab, tab.url);
            if (tab.url != null && !tab.url.startsWith("about:blank")) tab.webView.reload();
        }
    }

    private void editHomepage() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(database.getSetting("homepage", "https://www.google.com/"));
        input.setHint("https://example.com/");
        new AlertDialog.Builder(this).setTitle("Halaman Awal").setView(input)
            .setPositiveButton("Simpan", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                String value = input.getText().toString().trim();
                if (value.length() == 0) value = "https://www.google.com/";
                if (!value.contains("://")) value = "https://" + value;
                database.putSetting("homepage", value);
                showToast("Halaman awal disimpan.");
            }}).setNegativeButton("Batal", null).show();
    }

    private void showPrivacySettings() {
        LinearLayout body = openPage("Privasi & Cookie");
        addPageNote(body, "Daftar origin berasal dari situs yang pernah dibuka. Cookie yang ditampilkan mengikuti data yang disimpan WebView.");
        List<String> origins = database.getKnownOrigins();
        if (origins.size() == 0) addPageNote(body, "Belum ada origin yang tercatat.");
        for (int i = 0; i < origins.size(); i++) {
            final String origin = origins.get(i);
            addPageAction(body, 0xf1c0, origin, "Lihat atau hapus cookie dan Web Storage", new View.OnClickListener() { public void onClick(View v) { showOriginStorage(origin); } });
        }
        addPageAction(body, 0xf12d, "Hapus Semua Cookie", "Hapus cookie dari seluruh situs", new View.OnClickListener() { public void onClick(View v) { confirmClearCookies(); } });
        addPageAction(body, 0xf2ed, "Hapus Seluruh Web Storage", "Hapus data Web Storage dari seluruh origin", new View.OnClickListener() { public void onClick(View v) { confirmClearWebStorage(); } });
    }

    private void showOriginStorage(final String origin) {
        LinearLayout body = openPage("Web Storage Domain");
        String cookies = CookieManager.getInstance().getCookie(origin);
        addPageNote(body, "origin: " + origin + "\n\ncookie saat ini:\n" + (cookies == null ? "(kosong)" : cookies));
        addPageAction(body, 0xf2ed, "Hapus Cookie Domain Ini", "Penghapusan bersifat best-effort; cookie dengan Path atau Domain khusus mungkin memerlukan penanganan tambahan.", new View.OnClickListener() { public void onClick(View v) { confirmDeleteOriginCookies(origin); } });
        addPageAction(body, 0xf2ed, "Hapus Seluruh Storage Domain Ini", "Hapus data Web Storage untuk origin ini", new View.OnClickListener() { public void onClick(View v) {
            new AlertDialog.Builder(MainActivity.this).setTitle("Hapus Storage Domain?").setMessage(origin + "\n\nlocalStorage dan data WebStorage origin ini akan dihapus.")
                .setPositiveButton("Hapus", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                    android.webkit.WebStorage.getInstance().deleteOrigin(origin);
                    if (activeTab != null && origin.equals(getOrigin(activeTab.url))) activeTab.webView.evaluateJavascript("try{localStorage.clear();sessionStorage.clear();}catch(e){}", null);
                    showToast("Storage origin dihapus."); replaceCurrentPage(); showOriginStorage(origin);
                }}).setNegativeButton("Batal", null).show();
        }});
    }

    private void confirmDeleteOriginCookies(final String origin) {
        new AlertDialog.Builder(this).setTitle("Hapus Cookie Domain?").setMessage("Cookie untuk " + origin + " akan dihapus dari CookieManager.")
            .setPositiveButton("Hapus", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                final String raw = CookieManager.getInstance().getCookie(origin);
                if (raw != null) {
                    String host = Uri.parse(origin).getHost();
                    String[] pairs = raw.split(";");
                    CookieManager cm = CookieManager.getInstance();
                    for (int i = 0; i < pairs.length; i++) {
                        String pair = pairs[i].trim();
                        int eq = pair.indexOf('=');
                        if (eq > 0) {
                            String name = pair.substring(0, eq).trim();
                            cm.setCookie(origin, name + "=; Max-Age=0; Path=/");
                            if (host != null) cm.setCookie(origin, name + "=; Max-Age=0; Path=/; Domain=" + host);
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 21) cm.flush();
                }
                showToast("Permintaan penghapusan cookie dikirim."); replaceCurrentPage(); showOriginStorage(origin);
            }}).setNegativeButton("Batal", null).show();
    }

    private void confirmClearCookies() {
        new AlertDialog.Builder(this).setTitle("Hapus Semua Cookie?").setMessage("Cookie seluruh domain akan dihapus.")
            .setPositiveButton("Hapus", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                CookieManager cm = CookieManager.getInstance();
                if (Build.VERSION.SDK_INT >= 21) { cm.removeAllCookies(null); cm.flush(); }
                else { CookieSyncManager.createInstance(MainActivity.this); cm.removeAllCookie(); }
                showToast("Semua cookie dihapus."); replaceCurrentPage(); showPrivacySettings();
            }}).setNegativeButton("Batal", null).show();
    }

    private void confirmClearWebStorage() {
        new AlertDialog.Builder(this).setTitle("Hapus Seluruh Web Storage?").setMessage("Data penyimpanan situs akan dihapus.")
            .setPositiveButton("Hapus", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                android.webkit.WebStorage.getInstance().deleteAllData();
                for (int i = 0; i < tabs.size(); i++) tabs.get(i).webView.evaluateJavascript("try{localStorage.clear();sessionStorage.clear();}catch(e){}", null);
                showToast("Web Storage dihapus.");
            }}).setNegativeButton("Batal", null).show();
    }

    private void showSecuritySettings() {
        LinearLayout body = openPage("Keamanan");
        addPageAction(body, 0xf12a, "Hapus Data Browser", "Cache, cookie, dan data situs", new View.OnClickListener() { public void onClick(View v) { clearBrowserData(); } });
        addPageNote(body, "Koneksi dengan sertifikat SSL yang tidak valid selalu dibatalkan. Chromitext tidak menyediakan opsi untuk melewati validasi sertifikat.");
        addPageNote(body, "Extension Java/BeanShell dapat mengakses kelas yang tersedia di proses aplikasi. Pasang extension hanya dari sumber tepercaya.");
    }

    private void showDebuggerSettings() {
        LinearLayout body = openPage("Debugging");
        addPageAction(body, 0xf188, "Developer Tools", debuggerEnabled ? "Aktif — ketuk tombol Developer Tools yang mengambang untuk membuka panel." : "Nonaktif — Developer Tools tidak berjalan secara default.", new View.OnClickListener() { public void onClick(View v) {
            debuggerEnabled = !debuggerEnabled;
            database.putBoolean("debugger_enabled", debuggerEnabled);
            if (!debuggerEnabled) hideDebuggerPanel();
            refreshDebuggerOverlayVisibility();
            syncNetworkDebuggerHooks();
            replaceCurrentPage();
            showDebuggerSettings();
        }});
        addPageNote(body, "Modul yang aktif menentukan tab yang tersedia pada overlay. Modul nonaktif tidak ditampilkan dan tidak mengumpulkan data.");
        final String[] modules = new String[]{"Console", "Network", "Storage", "Source", "Elements", "Info"};
        final int[] icons = new int[]{0xf120, 0xf0ac, 0xf1c0, 0xf121, 0xf1fc, 0xf05a};
        for (int i = 0; i < modules.length; i++) {
            final String module = modules[i];
            addPageAction(body, icons[i], module, database.isDebuggerModuleEnabled(module) ? "Aktif" : "Nonaktif", new View.OnClickListener() { public void onClick(View v) {
                database.setDebuggerModuleEnabled(module, !database.isDebuggerModuleEnabled(module));
                if ("Network".equals(module)) syncNetworkDebuggerHooks();
                rebuildDebuggerPanel(); replaceCurrentPage(); showDebuggerSettings();
            }});
        }
        addPageAction(body, 0xf12d, "Hapus Log Debugger", "Hapus log Console, Network, dan extension", new View.OnClickListener() { public void onClick(View v) {
            for (int j = 0; j < tabs.size(); j++) { synchronized (tabs.get(j)) { tabs.get(j).consoleLogs.clear(); tabs.get(j).networkLogs.clear(); } }
            database.clearExtensionLogs();
            showToast("Log debugger dihapus.");
        }});
        addPageAction(body, 0xf188, "Debugger Extension", "Lihat log error dan runtime extension", new View.OnClickListener() { public void onClick(View v) { showExtensionDebugger(); } });
    }

    private void showExtensionDebugger() { showExtensionDebugger(null); }

    private void showExtensionDebugger(final String extensionId) {
        String pageTitle = extensionId == null ? "Debugger Extension" : "Log: " + extensionId;
        LinearLayout body = openPage(pageTitle);
        final TextView logs = new TextView(this);
        logs.setTextColor(COLOR_TEXT);
        logs.setTextSize(12);
        logs.setTypeface(Typeface.MONOSPACE);
        String currentLogs = database.getExtensionLogs(extensionId);
        logs.setText(currentLogs.length() == 0 ? "Belum ada log" + (extensionId == null ? " extension." : " untuk " + extensionId + ".") : currentLogs);
        logs.setPadding(dp(8), dp(8), dp(8), dp(8));
        logs.setBackground(boxBackground(darkTheme ? Color.rgb(29, 33, 39) : Color.rgb(247, 245, 239), COLOR_BORDER));
        body.addView(logs, new LinearLayout.LayoutParams(-1, -2));
        addPageAction(body, 0xf021, "Muat Ulang Log", "Muat ulang log" + (extensionId == null ? " seluruh extension" : " milik extension ini"), new View.OnClickListener() { public void onClick(View v) {
            String value = database.getExtensionLogs(extensionId);
            logs.setText(value.length() == 0 ? "Belum ada log" + (extensionId == null ? " extension." : " untuk " + extensionId + ".") : value);
        }});
        addPageAction(body, 0xf12d, "Hapus Log", extensionId == null ? "Hapus log seluruh extension" : "Hanya hapus log " + extensionId, new View.OnClickListener() { public void onClick(View v) {
            database.clearExtensionLogs(extensionId);
            logs.setText("Belum ada log" + (extensionId == null ? " extension." : " untuk " + extensionId + "."));
        }});
        addPageNote(body, "Log difilter berdasarkan ID extension saat layar dibuka dari detail extension. Error BeanShell ditampilkan sesuai catatan runtime yang tersedia.");
    }

    private void showAbout() {
        LinearLayout body = openPage("Tentang Chromitext");
        addPageNote(body, "Chromitext\nBrowser Android berbasis WebView.\nPackage: rh.chromitext.browser\nDatabase pengaturan: chromite.db\n\nFitur: multi-tab, bookmark, history, download Android, global user-agent, cookie/storage manager, debugger overlay opsional, debugger extension, dan extension ZIP.\n\nExtension Java menggunakan BeanShell opsional. Tambahkan bsh-2.0b4.jar ke folder libs/ di project AIDE lalu rebuild. Permission internet dan Android API dipakai sebagai pemeriksaan source-level umum, bukan sandbox yang tahan bypass; instal hanya extension tepercaya.");
    }

    private void rememberCurrentOrigin(String url) {
        String origin = getOrigin(url);
        if (origin != null && database != null) database.rememberOrigin(origin);
    }

    private String getOrigin(String url) {
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

    private void refreshDebuggerOverlayVisibility() {
        if (debuggerOverlay != null) debuggerOverlay.setEnabled(debuggerEnabled);
    }

    private void hideDebuggerPanel() {
        if (debuggerOverlay != null) debuggerOverlay.hidePanel();
    }

    private void refreshDebuggerPanel() {
        if (debuggerOverlay != null) debuggerOverlay.refreshPanel();
    }

    private void rebuildDebuggerPanel() {
        if (debuggerOverlay != null) debuggerOverlay.rebuildPanelTabs();
    }

    private void showTabsDialog() {
        LinearLayout body = openPage("Tab Terbuka (" + tabs.size() + ")");
        addPageAction(body, 0xf067, "Tab Baru", "Buka tab baru", new View.OnClickListener() { public void onClick(View v) { addTab(false); backFromPage(); } });
        for (int i = 0; i < tabs.size(); i++) {
            final BrowserTab tab = tabs.get(i);
            final int number = i + 1;
            addPageAction(body, 0xf2d0, (tab == activeTab ? "[aktif] " : "") + number + ". " + tab.title, tab.url, new View.OnClickListener() { public void onClick(View v) { setActiveTab(tab); backFromPage(); } });
            addPageAction(body, 0xf00d, "Tutup Tab " + number, tab.title, new View.OnClickListener() { public void onClick(View v) {
                if (tab == activeTab) closeActiveTab();
                else { tabs.remove(tab); webFrame.removeView(tab.webView); try { tab.webView.stopLoading(); tab.webView.destroy(); } catch (Exception ignored) { } updateTabSummary(); }
                backFromPage();
            }});
        }
    }

    private void showQuickExtensionPopup() {
        extensionManager.refresh();
        final List<ExtensionInfo> installed = extensionManager.getExtensions();
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(5), dp(12), dp(5));
        content.setBackgroundColor(COLOR_PANEL);
        final AlertDialog[] dialogHolder = new AlertDialog[1];
        if (installed.size() == 0) {
            TextView empty = new TextView(this);
            empty.setText("Belum ada extension yang terpasang. Pasang ZIP melalui Pengelola Extension di Pengaturan.");
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
                String desc = info.description == null || info.description.length() == 0 ? info.type + " extension" : info.description;
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
                toggle.setContentDescription("Aktifkan " + info.getDisplayName());
                row.addView(toggle, new LinearLayout.LayoutParams(-2, -2));
                toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton button, boolean checked) {
                        boolean previous = extensionManager.isEnabled(info);
                        if (previous == checked) return;
                        extensionManager.setEnabled(info, checked);
                        reloadExtensionRuntime();
                        showToast(info.getDisplayName() + (checked ? " diaktifkan" : " dinonaktifkan"));
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
            .setTitle("Extension (" + installed.size() + ")")
            .setView(scroll)
            .setPositiveButton("Pengelola", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) { showExtensionManager(); }
            })
            .setNegativeButton("Tutup", null)
            .create();
        dialogHolder[0] = dialog;
        dialog.show();
    }

    private void showExtensionManager() {
        extensionManager.refresh();
        final List<ExtensionInfo> installed = extensionManager.getExtensions();
        LinearLayout body = openPage("Pengelola Ekstensi");
        addPageAction(body, 0xf093, "Pasang Extension ZIP", "Pilih arsip ZIP melalui pemilih file Android", new View.OnClickListener() { public void onClick(View v) { chooseExtensionZip(); } });
        addPageAction(body, 0xf188, "Debugger Extension", "Log startup, error BeanShell, dan runtime extension", new View.OnClickListener() { public void onClick(View v) { showExtensionDebugger(); } });
        if (installed.size() == 0) addPageNote(body, "Belum ada extension yang terpasang. Pilih Pasang Extension ZIP untuk memulai.");
        for (int i = 0; i < installed.size(); i++) {
            final ExtensionInfo info = installed.get(i);
            addPageAction(body, 0xf12e, (extensionManager.isEnabled(info) ? "[aktif] " : "[nonaktif] ") + info.getDisplayName(), "v" + info.version + " · " + info.type + " · " + info.id, new View.OnClickListener() { public void onClick(View v) { showExtensionDetails(info); } });
        }
    }

    private void showExtensionDetails(final ExtensionInfo info) {
        if (info == null) return;
        LinearLayout body = openPage("Detail Extension");
        StringBuilder details = new StringBuilder();
        details.append(info.getDisplayName()).append("\nid: ").append(info.id).append("\nversi: ").append(info.version).append("\ntipe: ").append(info.type).append("\n");
        if (info.description.length() > 0) details.append("\n").append(info.description).append("\n");
        details.append("\ndomain lock: ").append(info.domainLockEnabled ? "Aktif" : "none / semua domain").append("\n");
        for (int i = 0; i < info.domains.size(); i++) details.append("  - ").append(info.domains.get(i)).append("\n");
        details.append("\npermission:");
        if (info.permissions.size() == 0) details.append("\n  tidak meminta permission");
        for (int i = 0; i < info.permissions.size(); i++) {
            String p = info.permissions.get(i);
            details.append("\n  [").append(extensionManager.hasPermission(info, p) ? "diizinkan" : "ditolak").append("] ").append(p);
        }
        if ("java".equals(info.type)) details.append("\n\ncatatan keamanan: pemeriksaan permission internet/API Android memindai referensi source yang umum dan bukan sandbox sempurna. BeanShell berjalan dalam proses aplikasi; reflection atau pemanggilan tidak langsung dapat melewati pemeriksaan. Instal hanya extension tepercaya.");
        addPageNote(body, details.toString());
        addPageAction(body, extensionManager.isEnabled(info) ? 0xf05e : 0xf058, extensionManager.isEnabled(info) ? "Nonaktifkan Extension" : "Aktifkan Extension", "Ubah status extension", new View.OnClickListener() { public void onClick(View v) {
            extensionManager.setEnabled(info, !extensionManager.isEnabled(info)); reloadExtensionRuntime(); replaceCurrentPage(); showExtensionDetails(info);
        }});
        addPageAction(body, 0xf084, "Kelola Permission", "Berikan atau cabut permission yang diminta", new View.OnClickListener() { public void onClick(View v) { showPermissionEditor(info); }});
        addPageAction(body, 0xf2ed, "Hapus Extension", "Hapus file extension dari penyimpanan internal", new View.OnClickListener() { public void onClick(View v) { confirmDeleteExtension(info); }});
        addPageAction(body, 0xf188, "Lihat Log Extension", "Tampilkan log milik " + info.id, new View.OnClickListener() { public void onClick(View v) { showExtensionDebugger(info.id); }});
    }

    private void showPermissionEditor(final ExtensionInfo info) {
        if (info.permissions.size() == 0) {
            showToast("Extension ini tidak meminta permission.");
            return;
        }
        final String[] permissions = info.permissions.toArray(new String[info.permissions.size()]);
        final String[] labels = new String[permissions.length];
        final boolean[] checked = new boolean[permissions.length];
        for (int i = 0; i < permissions.length; i++) {
            labels[i] = permissions[i] + " — " + ExtensionManager.permissionDescription(permissions[i]);
            checked[i] = extensionManager.hasPermission(info, permissions[i]);
        }
        new AlertDialog.Builder(this).setTitle("permission: " + info.getDisplayName())
            .setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                public void onClick(DialogInterface dialog, int which, boolean isChecked) { checked[which] = isChecked; }
            })
            .setPositiveButton("Simpan", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    Set<String> granted = new HashSet<String>();
                    for (int i = 0; i < permissions.length; i++) if (checked[i]) granted.add(permissions[i]);
                    extensionManager.setPermissions(info, granted);
                    reloadExtensionRuntime();
                    replaceCurrentPage();
                    showExtensionDetails(extensionManager.getExtension(info.id));
                }
            }).setNegativeButton("Batal", null).show();
    }

    private void confirmDeleteExtension(final ExtensionInfo info) {
        new AlertDialog.Builder(this).setTitle("Hapus Extension?")
            .setMessage("File " + info.getDisplayName() + " akan dihapus dari penyimpanan internal browser.")
            .setPositiveButton("Hapus", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    showToast("Menghapus extension...");
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
                                            showError("Hapus Extension", failure);
                                            return;
                                        }
                                        extensionManager.refresh();
                                        reloadExtensionRuntime();
                                        replaceCurrentPage();
                                        showExtensionManager();
                                        showToast("Extension dan seluruh datanya sudah dihapus.");
                                    }
                                });
                            } finally { endBackgroundWork(); }
                        }
                    }, "Chromitext-ExtensionDeleter");
                    deleteWorker.setDaemon(true);
                    beginBackgroundWork();
                    deleteWorker.start();
                }
            }).setNegativeButton("Batal", null).show();
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
                startActivityForResult(Intent.createChooser(intent, "pilih zip extension"), REQUEST_INSTALL_ZIP);
            } catch (Exception second) { showError("pemilih file", second); }
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
        message.append("nama: ").append(info.getDisplayName()).append("\nversi: ").append(info.version)
            .append("\ntipe: ").append(info.type).append("\nid: ").append(info.id).append("\n\n");
        message.append(info.description.length() == 0 ? "tanpa deskripsi" : info.description);
        message.append("\n\ndomain lock: ").append(info.domainLockEnabled ? info.domains.toString() : "none (semua domain)");
        if ("java".equals(info.type)) {
            message.append("\n\nperingatan: permission internet dan Android API diperiksa lewat pemindaian source untuk pola yang umum. Ini bukan sandbox sempurna; reflection atau pemanggilan tidak langsung dapat melewatinya. Pasang hanya extension dari sumber tepercaya.");
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle("Pasang Extension").setMessage(message.toString());
        if (permissions.length > 0) {
            builder.setMultiChoiceItems(labels, checked, new DialogInterface.OnMultiChoiceClickListener() {
                public void onClick(DialogInterface dialog, int which, boolean isChecked) { checked[which] = isChecked; }
            });
        }
        builder.setPositiveButton("pasang", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                final Set<String> granted = new HashSet<String>();
                for (int i = 0; i < permissions.length; i++) if (checked[i]) granted.add(permissions[i]);
                showToast("Memasang extension...");
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
                                        showError("instalasi extension", failure);
                                        return;
                                    }
                                    reloadExtensionRuntime();
                                    showToast("Extension terpasang: " + info.getDisplayName());
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
        }).setNegativeButton("Batal", new DialogInterface.OnClickListener() {
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

    private boolean containsAny(String text, String[] tokens) {
        if (text == null || tokens == null) return false;
        for (int i = 0; i < tokens.length; i++) if (text.indexOf(tokens[i]) >= 0) return true;
        return false;
    }

    private boolean isNetworkClassName(String name) {
        return name.startsWith("java.net.") || name.startsWith("javax.net.") ||
            name.startsWith("java.nio.channels.Socket") || name.startsWith("okhttp3.") || name.startsWith("retrofit2.");
    }

    private boolean isFileClassName(String name) {
        return name.equals("java.io.File") || name.startsWith("java.io.FileInputStream") ||
            name.startsWith("java.io.FileOutputStream") || name.startsWith("java.io.FileReader") ||
            name.startsWith("java.io.FileWriter") || name.startsWith("java.io.RandomAccessFile") ||
            name.startsWith("java.nio.file.");
    }

    private boolean isReflectionClassName(String name) {
        return name.startsWith("java.lang.reflect.") || name.equals("java.lang.ClassLoader");
    }

    private boolean isProcessExecutionClassName(String name) {
        return name.equals("java.lang.Runtime") || name.equals("java.lang.ProcessBuilder");
    }

    private void runBeanShellExtension(final ExtensionInfo info) {
        try {
            final String source = extensionManager.readScript(info, info.javaEntry);
            String lowerSource = source.toLowerCase(Locale.US);
            boolean needsInternet = containsAny(lowerSource, new String[]{
                "import java.net.", "java.net.", "javax.net.", "httpurlconnection", "httpsurlconnection", "socketchannel", "okhttp", "retrofit", "webview.loadurl(", "downloadmanager.enqueue", "android.net.http"
            });
            boolean needsAndroidApi = lowerSource.indexOf("android.") >= 0;
            boolean needsFileAccess = containsAny(lowerSource, new String[]{
                "java.io.file", "fileinputstream", "fileoutputstream", "filereader", "filewriter", "randomaccessfile", "java.nio.file."
            });
            boolean needsReflection = containsAny(lowerSource, new String[]{
                "java.lang.reflect.", "forname(", "forname (", "getclass(", "getclass (", "getmethod(", "getmethod (", "getdeclaredmethod", "getdeclaredfield", "classloader", "loadclass("
            });
            boolean needsProcessExecution = containsAny(lowerSource, new String[]{
                "runtime.getruntime", "processbuilder", "java.lang.runtime"
            });
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
                    if (MainActivity.this.isNetworkClassName(name) && !extensionManager.hasPermission(info, "internet")) {
                        throw new NoClassDefFoundError("permission extension dibutuhkan: internet (" + name + ")");
                    }
                    if (name.startsWith("android.") && !extensionManager.hasPermission(info, "androidApi") && !extensionManager.hasPermission(info, "androidClasses")) {
                        throw new NoClassDefFoundError("permission extension dibutuhkan: androidApi (" + name + ")");
                    }
                    if (MainActivity.this.isFileClassName(name) && !extensionManager.hasPermission(info, "fileAccess")) {
                        throw new NoClassDefFoundError("permission extension dibutuhkan: fileAccess (" + name + ")");
                    }
                    if (MainActivity.this.isReflectionClassName(name) && !extensionManager.hasPermission(info, "reflection")) {
                        throw new NoClassDefFoundError("permission extension dibutuhkan: reflection (" + name + ")");
                    }
                    if (MainActivity.this.isProcessExecutionClassName(name) && !extensionManager.hasPermission(info, "processExecution")) {
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
            showToast("Memeriksa arsip extension...");
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
                                if (failure != null) showError("membaca zip extension", failure);
                                else if (result != null) reviewInstall(result);
                                else showToast("ZIP extension tidak menghasilkan data instalasi.");
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
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) showToast("Izin penyimpanan untuk unduhan diberikan.");
        }
    }

    private void startDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
            request.setTitle(filename);
            request.setDescription("download dari Chromitext");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            if (mimeType != null) request.setMimeType(mimeType);
            if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                filename);
            manager.enqueue(request);
            showToast("Unduhan ditambahkan ke Download Manager Android.");
        } catch (Exception error) {
            showError("download", error);
        }
    }

    private void toggleJavascript() {
        javascriptEnabled = !javascriptEnabled;
        database.putBoolean("javascript_enabled", javascriptEnabled);
        for (int i = 0; i < tabs.size(); i++) tabs.get(i).webView.getSettings().setJavaScriptEnabled(javascriptEnabled);
        showToast("JavaScript " + (javascriptEnabled ? "Aktif" : "Nonaktif"));
        if (activeTab != null && !activeTab.url.startsWith("about:blank")) activeTab.webView.reload();
    }

    private void clearBrowserData() {
        new AlertDialog.Builder(this).setTitle("Hapus Data Browser")
            .setMessage("Hapus cache WebView, cookie, dan data situs? Bookmark dan extension tidak akan dihapus.")
            .setPositiveButton("Hapus", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    for (int i = 0; i < tabs.size(); i++) tabs.get(i).webView.clearCache(true);
                    CookieManager cookies = CookieManager.getInstance();
                    if (Build.VERSION.SDK_INT >= 21) cookies.removeAllCookies(null);
                    else {
                        CookieSyncManager.createInstance(MainActivity.this);
                        cookies.removeAllCookie();
                    }
                    WebViewDatabaseHelper.clear(MainActivity.this);
                    showToast("Data situs dibersihkan.");
                }
            }).setNegativeButton("Batal", null).show();
    }

    private void showBookmarks() {
        final ArrayList<String> entries = readLines("bookmarks");
        LinearLayout body = openPage("Bookmark");
        addPageAction(body, 0xf067, "Simpan Halaman Saat Ini", activeTab == null ? "Tidak ada tab aktif." : activeTab.url, new View.OnClickListener() { public void onClick(View v) { addCurrentBookmark(); replaceCurrentPage(); showBookmarks(); } });
        if (entries.size() == 0) addPageNote(body, "Belum ada bookmark.");
        for (int i = entries.size() - 1; i >= 0; i--) {
            final String[] pair = entries.get(i).split("\t", 2);
            if (pair.length != 2) continue;
            addPageAction(body, 0xf02e, pair[0], pair[1], new View.OnClickListener() { public void onClick(View v) { navigate(pair[1]); backFromPage(); }});
            addPageAction(body, 0xf2ed, "Hapus Bookmark", pair[0], new View.OnClickListener() { public void onClick(View v) { database.deleteBookmark(pair[1]); replaceCurrentPage(); showBookmarks(); }});
        }
    }

    private void addCurrentBookmark() {
        if (activeTab == null || !UrlResolver.isWebUrl(activeTab.url)) { showToast("Halaman ini tidak dapat disimpan sebagai bookmark."); return; }
        database.addBookmark(activeTab.title, activeTab.url);
        showToast("Bookmark disimpan.");
    }

    private void showHistory() {
        final ArrayList<String> entries = readLines("history");
        Collections.reverse(entries);
        LinearLayout body = openPage("Riwayat Penjelajahan");
        addPageAction(body, 0xf2ed, "Hapus Seluruh Riwayat", "Hapus riwayat penjelajahan", new View.OnClickListener() { public void onClick(View v) {
            new AlertDialog.Builder(MainActivity.this).setTitle("Hapus Riwayat Penjelajahan?").setMessage("Seluruh riwayat penjelajahan akan dihapus.")
                .setPositiveButton("Hapus", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { database.clearHistory(); replaceCurrentPage(); showHistory(); }}).setNegativeButton("Batal", null).show();
        }});
        if (entries.size() == 0) addPageNote(body, "Riwayat penjelajahan masih kosong.");
        for (int i = 0; i < entries.size(); i++) {
            final String[] pair = entries.get(i).split("\t", 2);
            if (pair.length != 2) continue;
            addPageAction(body, 0xf1da, pair[0], pair[1], new View.OnClickListener() { public void onClick(View v) { navigate(pair[1]); backFromPage(); }});
            addPageAction(body, 0xf2ed, "Hapus Entri", pair[1], new View.OnClickListener() { public void onClick(View v) { database.deleteHistoryUrl(pair[1]); replaceCurrentPage(); showHistory(); }});
        }
    }

    private void showDownloadsInfo() {
        LinearLayout body = openPage("Unduhan");
        addPageNote(body, "Unduhan dikelola oleh Download Manager Android. Buka aplikasi Downloads/Files untuk melihat file yang diunduh.");
        addPageAction(body, 0xf019, "Buka Unduhan", "Buka aplikasi pengelola unduhan Android", new View.OnClickListener() { public void onClick(View v) {
            try { startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)); }
            catch (Exception error) { showToast("Aplikasi pengelola unduhan tidak tersedia."); }
        }});
    }

    private void saveHistory(String title, String url) {
        if (!UrlResolver.isWebUrl(url)) return;
        database.addHistory(title, url, 500);
    }

    private ArrayList<String> readLines(String key) {
        return new ArrayList<String>(database.getLines(key));
    }

    private void hideKeyboard() {
        hideAddressSuggestions();
        try {
            InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            manager.hideSoftInputFromWindow(addressBar.getWindowToken(), 0);
            addressBar.clearFocus();
        } catch (Exception ignored) { }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showError(String title, Exception error) {
        String message = error == null ? "error tidak diketahui" : error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show();
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
        if (addressSuggestionPopup != null && addressSuggestionPopup.isShowing()) { hideAddressSuggestions(); return; }
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
        hideAddressSuggestions();
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
