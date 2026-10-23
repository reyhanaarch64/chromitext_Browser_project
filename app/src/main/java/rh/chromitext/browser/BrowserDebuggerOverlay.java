package rh.chromitext.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.util.ArrayList;
import java.util.List;

/** Optional on-page Developer Tools. UI is native and module collection is controlled by settings. */
public class BrowserDebuggerOverlay {
    public interface Host {
        BrowserTab getActiveTab();
        int getTabCount();
        int getActiveTabNumber();
        boolean isPageActive();
        boolean isFullscreen();
    }

    private final Activity activity;
    private final FrameLayout browserLayer;
    private final BrowserDatabase database;
    private final Host host;
    private View floatingButton;
    private LinearLayout panel;
    private LinearLayout contentList;
    private boolean enabled;
    private boolean dark;
    private String currentModule = "Console";
    private int panelHeight;
    private int panelColor;
    private int cardColor;
    private int borderColor;
    private int textColor;
    private int mutedColor;
    private int accentColor;
    private String currentCopyText = "";

    public BrowserDebuggerOverlay(Activity activity, FrameLayout browserLayer, BrowserDatabase database, Host host) {
        this.activity = activity;
        this.browserLayer = browserLayer;
        this.database = database;
        this.host = host;
        this.panelHeight = readPanelHeight();
        setPalette();
        createFloatingButton();
    }

    private void setPalette() {
        String mode = database.getSetting("ui_theme", "system");
        dark = "dark".equals(mode) || ("system".equals(mode) && Build.VERSION.SDK_INT >= 29 &&
            (activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES);
        if (dark) {
            panelColor = Color.rgb(22, 25, 30);
            cardColor = Color.rgb(31, 36, 44);
            borderColor = Color.rgb(65, 73, 85);
            textColor = Color.rgb(232, 236, 242);
            mutedColor = Color.rgb(165, 174, 185);
            accentColor = Color.rgb(72, 147, 202);
        } else {
            panelColor = Color.rgb(250, 251, 253);
            cardColor = Color.rgb(255, 255, 255);
            borderColor = Color.rgb(213, 218, 225);
            textColor = Color.rgb(32, 38, 46);
            mutedColor = Color.rgb(94, 104, 117);
            accentColor = Color.rgb(36, 92, 132);
        }
    }

    public void setEnabled(boolean value) {
        enabled = value;
        refreshVisibility();
    }

    public void refreshVisibility() {
        if (floatingButton == null) return;
        floatingButton.setVisibility(enabled && !host.isPageActive() && !host.isFullscreen() ? View.VISIBLE : View.GONE);
        if (!enabled) hidePanel();
    }

    public boolean isPanelVisible() { return panel != null && panel.getVisibility() == View.VISIBLE; }
    public void hidePanel() { if (panel != null) panel.setVisibility(View.GONE); }
    public void refreshPanel() { refreshPanelContent(); }
    public void rebuildPanelTabs() { if (isPanelVisible()) showPanel(); }

    public void togglePanel() {
        if (!enabled || host.getActiveTab() == null) return;
        if (isPanelVisible()) hidePanel(); else showPanel();
    }

    private void createFloatingButton() {
        TextView button = new TextView(activity);
        floatingButton = button;
        FontAwesome.apply(activity, button, "solid", 0xf188, "DEV");
        button.setTextColor(Color.WHITE);
        button.setTextSize(18);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(LanguageManager.get("accessibility.open_developer_tools", "Buka Developer Tools"));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(accentColor);
        bg.setCornerRadius(dp(28));
        button.setBackground(bg);
        if (Build.VERSION.SDK_INT >= 21) button.setElevation(dp(7));
        final int[] down = new int[2];
        final float[] touch = new float[2];
        final boolean[] dragged = new boolean[1];
        button.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View view, MotionEvent event) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    touch[0] = event.getRawX(); touch[1] = event.getRawY();
                    down[0] = (int)view.getX(); down[1] = (int)view.getY(); dragged[0] = false;
                    return true;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    float dx = event.getRawX() - touch[0]; float dy = event.getRawY() - touch[1];
                    if (Math.abs(dx) > dp(3) || Math.abs(dy) > dp(3)) dragged[0] = true;
                    if (dragged[0]) {
                        int x = Math.max(0, Math.min(browserLayer.getWidth() - view.getWidth(), down[0] + (int)dx));
                        int y = Math.max(0, Math.min(browserLayer.getHeight() - view.getHeight(), down[1] + (int)dy));
                        lp.gravity = Gravity.TOP | Gravity.LEFT; lp.leftMargin = x; lp.topMargin = y;
                        view.setLayoutParams(lp);
                    }
                    return true;
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (!dragged[0]) togglePanel();
                    return true;
                }
                return true;
            }
        });
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        params.rightMargin = dp(9);
        browserLayer.addView(button, params);
    }

    private void showPanel() {
        setPalette();
        if (!database.isDebuggerModuleEnabled(currentModule)) currentModule = firstEnabledModule();
        if (panel != null && panel.getParent() instanceof ViewGroup) ((ViewGroup)panel.getParent()).removeView(panel);
        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(background(panelColor, borderColor, 14));
        if (Build.VERSION.SDK_INT >= 21) panel.setElevation(dp(10));

        // Drag handle resizes the panel; its height is persisted in chromite.db.
        final View grip = new View(activity);
        grip.setBackground(background(borderColor, borderColor, 3));
        LinearLayout.LayoutParams gripParams = new LinearLayout.LayoutParams(dp(46), dp(5));
        gripParams.gravity = Gravity.CENTER_HORIZONTAL;
        gripParams.topMargin = dp(6); gripParams.bottomMargin = dp(5);
        panel.addView(grip, gripParams);
        grip.setOnTouchListener(new View.OnTouchListener() {
            private float lastY;
            private int startHeight;
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) { lastY = e.getRawY(); startHeight = ((FrameLayout.LayoutParams)panel.getLayoutParams()).height; return true; }
                if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    int max = Math.max(dp(220), browserLayer.getHeight() - dp(55));
                    int h = Math.max(dp(170), Math.min(max, startHeight + (int)(lastY - e.getRawY())));
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)panel.getLayoutParams(); lp.height = h; panel.setLayoutParams(lp);
                    return true;
                }
                if (e.getAction() == MotionEvent.ACTION_UP) {
                    panelHeight = ((FrameLayout.LayoutParams)panel.getLayoutParams()).height;
                    database.putSetting("debugger_panel_height", String.valueOf(panelHeight));
                    return true;
                }
                return true;
            }
        });

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), 0, dp(5), 0);
        TextView title = new TextView(activity);
        title.setText(LanguageManager.get("status.developer.tools", "Developer Tools")); title.setTextColor(textColor); title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(38), 1));
        TextView copyAll = actionText("⧉", LanguageManager.get("action.copy_all_content", "Salin"));
        header.addView(copyAll, new LinearLayout.LayoutParams(dp(42), dp(36)));
        copyAll.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { copyText(currentCopyText, LanguageManager.format("debugger.copy_module_toast", "Konten %1$s disalin", displayModuleName(currentModule))); } });
        TextView close = actionText("×", LanguageManager.get("action.close", "Tutup")); close.setTextSize(22);
        header.addView(close, new LinearLayout.LayoutParams(dp(38), dp(36)));
        close.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { hidePanel(); } });
        panel.addView(header, new LinearLayout.LayoutParams(-1, dp(38)));

        HorizontalScrollView tabScroll = new HorizontalScrollView(activity);
        tabScroll.setHorizontalScrollBarEnabled(false); tabScroll.setFillViewport(false);
        LinearLayout moduleRow = new LinearLayout(activity); moduleRow.setOrientation(LinearLayout.HORIZONTAL);
        final String[] modules = new String[]{"Console", "Network", "Storage", "Source", "Elements", "Info"};
        final int[] icons = new int[]{0xf120, 0xf0ac, 0xf1c0, 0xf121, 0xf1fc, 0xf05a};
        for (int i = 0; i < modules.length; i++) {
            final String module = modules[i]; final int iconCode = icons[i];
            if (!database.isDebuggerModuleEnabled(module)) continue;
            LinearLayout tab = new LinearLayout(activity);
            tab.setOrientation(LinearLayout.HORIZONTAL); tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(9), 0, dp(9), 0);
            tab.setBackground(background(module.equals(currentModule) ? (dark ? Color.rgb(49, 61, 75) : Color.rgb(224, 237, 248)) : cardColor, borderColor, 5));
            TextView icon = new TextView(activity); FontAwesome.apply(activity, icon, "solid", iconCode, "•");
            icon.setTextColor(module.equals(currentModule) ? accentColor : mutedColor); icon.setTextSize(13); icon.setGravity(Gravity.CENTER);
            tab.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(34)));
            TextView label = new TextView(activity); label.setText(displayModuleName(module)); label.setTextColor(textColor); label.setTextSize(11);
            label.setGravity(Gravity.CENTER);
            tab.addView(label, new LinearLayout.LayoutParams(-2, dp(34)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(34)); lp.leftMargin = dp(3); lp.rightMargin = dp(3);
            moduleRow.addView(tab, lp);
            tab.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { currentModule = module; showPanel(); } });
        }
        tabScroll.addView(moduleRow, new FrameLayout.LayoutParams(-2, dp(38)));
        panel.addView(tabScroll, new LinearLayout.LayoutParams(-1, dp(40)));

        contentList = new LinearLayout(activity); contentList.setOrientation(LinearLayout.VERTICAL); contentList.setPadding(dp(7), dp(6), dp(7), dp(9));
        ScrollView contentScroll = new ScrollView(activity); contentScroll.setFillViewport(false); contentScroll.setBackgroundColor(panelColor);
        contentScroll.addView(contentList, new ScrollView.LayoutParams(-1, -2));
        panel.addView(contentScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, panelHeight, Gravity.BOTTOM);
        params.leftMargin = dp(4); params.rightMargin = dp(4); params.bottomMargin = dp(4);
        browserLayer.addView(panel, params);
        refreshPanelContent();
    }

    private String displayModuleName(String module) {
        if ("Console".equals(module)) return LanguageManager.get("debugger.module.console", "Console");
        if ("Network".equals(module)) return LanguageManager.get("debugger.module.network", "Network");
        if ("Storage".equals(module)) return LanguageManager.get("debugger.module.storage", "Storage");
        if ("Source".equals(module)) return LanguageManager.get("debugger.module.source", "Source");
        if ("Elements".equals(module)) return LanguageManager.get("debugger.module.elements", "Elements");
        if ("Info".equals(module)) return LanguageManager.get("debugger.module.info", "Info");
        return module;
    }

    private TextView actionText(String text, String description) {
        TextView v = new TextView(activity); v.setText(text); v.setTextColor(textColor); v.setTextSize(16);
        v.setGravity(Gravity.CENTER); v.setContentDescription(description); v.setBackground(background(cardColor, borderColor, 6));
        return v;
    }

    private void refreshPanelContent() {
        if (!isPanelVisible() || contentList == null) return;
        contentList.removeAllViews();
        BrowserTab tab = host.getActiveTab();
        if (tab == null) { addPlainCard(LanguageManager.get("message.tidak.ada.tab.aktif", "Tidak ada tab aktif."), mutedColor); return; }
        if (firstEnabledModule().length() == 0) { addPlainCard(LanguageManager.get("message.semua.modul.dinonaktifkan.aktifkan.modul.m", "Semua modul dinonaktifkan. Aktifkan modul melalui Pengaturan > Debugging."), mutedColor); return; }
        if (!database.isDebuggerModuleEnabled(currentModule)) currentModule = firstEnabledModule();
        if ("Console".equals(currentModule)) renderConsole(tab);
        else if ("Network".equals(currentModule)) renderNetwork(tab);
        else if ("Info".equals(currentModule)) {
            String value = "TITLE\n" + safe(tab.title) + "\n\nURL\n" + safe(tab.url) + "\n\nLOADING\n" + tab.loading + "\n\nTAB\n" + host.getActiveTabNumber() + "/" + host.getTabCount();
            currentCopyText = value; addPlainCard(value, textColor);
        } else if ("Storage".equals(currentModule)) renderStorage(tab);
        else if ("Source".equals(currentModule)) renderSource(tab);
        else if ("Elements".equals(currentModule)) renderElements(tab);
    }

    private void renderConsole(BrowserTab tab) {
        final List<String> logs = snapshot(tab, true);
        StringBuilder copy = new StringBuilder();
        if (logs.size() == 0) { currentCopyText = ""; addPlainCard(LanguageManager.get("message.belum.ada.output.console", "Belum ada output Console."), mutedColor); return; }
        for (int i = 0; i < logs.size(); i++) {
            final String line = logs.get(i); copy.append(line).append('\n');
            int color = consoleColor(line);
            LinearLayout row = new LinearLayout(activity); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.TOP);
            row.setPadding(dp(8), dp(7), dp(5), dp(7)); row.setBackground(background(cardColor, borderColor, 7));
            TextView mark = new TextView(activity); mark.setText(levelLabel(line)); mark.setTextColor(color); mark.setTextSize(10); mark.setTypeface(Typeface.DEFAULT, Typeface.BOLD); mark.setGravity(Gravity.CENTER);
            row.addView(mark, new LinearLayout.LayoutParams(dp(48), -2));
            TextView text = new TextView(activity); text.setText(line); text.setTextColor(textColor); text.setTextSize(11); text.setTypeface(Typeface.MONOSPACE); text.setTextIsSelectable(true);
            row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
            TextView cp = actionText("⧉", LanguageManager.get("debugger.copy_console_entry", "Salin entri Console")); row.addView(cp, new LinearLayout.LayoutParams(dp(30), dp(30)));
            cp.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { copyText(line, LanguageManager.get("debugger.console_entry_copied", "Entri Console disalin")); } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(5); contentList.addView(row, lp);
        }
        currentCopyText = copy.toString();
    }

    private int consoleColor(String value) {
        String upper = value == null ? "" : value.toUpperCase(java.util.Locale.US);
        if (upper.startsWith("[ERROR]") || upper.contains("[WEB ERROR]") || upper.contains("[SSL ERROR]")) return Color.rgb(220, 68, 68);
        if (upper.startsWith("[WARNING]") || upper.startsWith("[WARN]") || upper.contains("WARNING")) return Color.rgb(206, 151, 30);
        if (upper.startsWith("[TIP]")) return Color.rgb(77, 156, 110);
        if (upper.startsWith("[DEBUG]")) return Color.rgb(100, 147, 200);
        return mutedColor;
    }

    private String levelLabel(String value) {
        if (value == null) return LanguageManager.get("debugger.level.log", "LOG");
        String upper = value.toUpperCase(java.util.Locale.US);
        if (upper.startsWith("[ERROR]") || upper.contains("[WEB ERROR]")) return LanguageManager.get("debugger.level.error", "ERROR");
        if (upper.startsWith("[WARNING]") || upper.startsWith("[WARN]")) return LanguageManager.get("debugger.level.warn", "WARN");
        if (upper.startsWith("[TIP]")) return LanguageManager.get("debugger.level.tip", "TIP");
        if (upper.startsWith("[DEBUG]")) return LanguageManager.get("debugger.level.debug", "DEBUG");
        if (upper.startsWith("[INFO]")) return LanguageManager.get("debugger.level.info", "INFO");
        return LanguageManager.get("debugger.level.log", "LOG");
    }

    private void renderNetwork(BrowserTab tab) {
        List<String> logs = snapshot(tab, false);
        if (logs.size() == 0) { currentCopyText = ""; addPlainCard(LanguageManager.get("message.belum.ada.request.yang.teramati.request.aw", "Belum ada request yang teramati. Request awal halaman mungkin tidak tercatat jika debugger diaktifkan setelah halaman dimuat."), mutedColor); return; }
        StringBuilder all = new StringBuilder();
        for (int i = logs.size() - 1; i >= 0; i--) {
            final String raw = logs.get(i); all.append(raw).append("\n\n");
            JSONObject object = parseNetworkObject(raw);
            if (object == null) {
                TextView fallback = text(raw, textColor, 11, true); fallback.setTextIsSelectable(true); fallback.setBackground(background(cardColor, borderColor, 7)); fallback.setPadding(dp(9), dp(8), dp(9), dp(8));
                LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2); fp.bottomMargin = dp(5); contentList.addView(fallback, fp); continue;
            }
            final JSONObject request = object;
            String method = object.optString("method", "GET").toUpperCase(java.util.Locale.US);
            String url = object.optString("url", LanguageManager.get("debugger.url_unavailable", "(URL tidak tersedia)"));
            String domain = url;
            String path = url;
            try { Uri uri = Uri.parse(url); domain = uri.getHost() == null ? url : uri.getHost(); path = uri.getEncodedPath(); if (path == null || path.length() == 0) path = "/"; }
            catch (Exception ignored) { }
            int status = object.optInt("status", -1);
            int statusColor = status >= 400 || status == 0 ? Color.rgb(218, 68, 68) : (status >= 200 && status < 400 ? Color.rgb(51, 157, 102) : mutedColor);
            LinearLayout card = new LinearLayout(activity); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(10), dp(8), dp(10), dp(8)); card.setBackground(background(cardColor, borderColor, 8));
            LinearLayout top = new LinearLayout(activity); top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL);
            TextView methodView = text(method, methodColor(method), 11, true); methodView.setTypeface(Typeface.DEFAULT, Typeface.BOLD); methodView.setGravity(Gravity.CENTER); methodView.setPadding(dp(6), dp(4), dp(6), dp(4)); methodView.setBackground(background(dark ? Color.rgb(42, 48, 58) : Color.rgb(239, 243, 247), borderColor, 4));
            top.addView(methodView, new LinearLayout.LayoutParams(-2, -2));
            TextView hostView = text("  " + domain, textColor, 12, true); hostView.setTypeface(Typeface.DEFAULT, Typeface.BOLD); top.addView(hostView, new LinearLayout.LayoutParams(0, -2, 1));
            TextView statusView = text(status < 0 ? "—" : String.valueOf(status), statusColor, 11, true); statusView.setGravity(Gravity.CENTER); statusView.setTypeface(Typeface.DEFAULT, Typeface.BOLD); top.addView(statusView, new LinearLayout.LayoutParams(dp(44), -2));
            card.addView(top);
            TextView urlView = text(path, mutedColor, 10, true); urlView.setPadding(0, dp(5), 0, dp(2)); urlView.setTextIsSelectable(true); card.addView(urlView);
            String ms = object.has("ms") ? object.optString("ms") + " ms" : LanguageManager.get("debugger.duration_unavailable", "durasi tidak tersedia");
            TextView meta = text(ms + (object.optString("statusText").length() > 0 ? " · " + object.optString("statusText") : ""), mutedColor, 10, false); card.addView(meta);
            card.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showNetworkDetails(request); } });
            card.setFocusable(true); card.setClickable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(6); contentList.addView(card, lp);
        }
        currentCopyText = all.toString();
    }

    private JSONObject parseNetworkObject(String raw) {
        if (raw == null) return null;
        int begin = raw.indexOf('{'); if (begin < 0) return null;
        try { Object value = new JSONTokener(raw.substring(begin)).nextValue(); return value instanceof JSONObject ? (JSONObject)value : null; }
        catch (Exception ignored) { return null; }
    }

    private void showNetworkDetails(JSONObject request) {
        final String details;
        try { details = request.toString(2); } catch (Exception ignored) { details = request.toString(); }
        final TextView body = text(details, textColor, 11, true); body.setTypeface(Typeface.MONOSPACE); body.setTextIsSelectable(true); body.setPadding(dp(12), dp(10), dp(12), dp(10));
        ScrollView scroll = new ScrollView(activity); scroll.addView(body);
        new AlertDialog.Builder(activity).setTitle(LanguageManager.get("dialog.title.network.request.details", "Network Request Details"))
            .setView(scroll)
            .setPositiveButton(LanguageManager.get("action.salin.detail", "Salin Detail"), new android.content.DialogInterface.OnClickListener() { public void onClick(android.content.DialogInterface dialog, int which) { copyText(details, LanguageManager.get("debugger.request_details_copied", "Detail request disalin")); } })
            .setNegativeButton(LanguageManager.get("action.close", "Tutup"), null).show();
    }

    private int methodColor(String method) {
        if ("GET".equals(method)) return Color.rgb(47, 135, 200);
        if ("POST".equals(method)) return Color.rgb(52, 156, 105);
        if ("PUT".equals(method) || "PATCH".equals(method)) return Color.rgb(193, 139, 42);
        if ("DELETE".equals(method)) return Color.rgb(216, 67, 67);
        return accentColor;
    }

    private void renderStorage(final BrowserTab tab) {
        String cookies = CookieManager.getInstance().getCookie(tab.url);
        final String start = LanguageManager.format("debugger.storage_details", "URL\n%1$s\n\nCookie (CookieManager)\n%2$s\n\nMemuat localStorage dan sessionStorage...", safe(tab.url), cookies == null ? LanguageManager.get("debugger.empty_value", "(kosong)") : cookies);
        currentCopyText = start; addPlainCard(start, textColor);
        if (tab.webView != null) tab.webView.evaluateJavascript("(function(){var o={};try{o.localStorage=JSON.stringify(localStorage);o.sessionStorage=JSON.stringify(sessionStorage);o.documentCookie=document.cookie;}catch(e){o.error=String(e)}return JSON.stringify(o)})()", new ValueCallback<String>() {
            public void onReceiveValue(String value) {
                if (isPanelVisible() && "Storage".equals(currentModule)) {
                    String result = start + "\n\n" + LanguageManager.get("debugger.page_storage", "Page Storage") + "\n" + decodeEvaluateResult(value);
                    currentCopyText = result; contentList.removeAllViews(); addPlainCard(result, textColor);
                }
            }
        });
    }

    private void renderSource(final BrowserTab tab) {
        currentCopyText = LanguageManager.get("debugger.loading_source", "Memuat DOM source..."); addPlainCard(currentCopyText, mutedColor);
        if (tab.webView != null) tab.webView.evaluateJavascript("(function(){return document.documentElement?document.documentElement.outerHTML:'(documentElement tidak tersedia)'})()", new ValueCallback<String>() {
            public void onReceiveValue(String value) {
                if (isPanelVisible() && "Source".equals(currentModule)) {
                    String source = decodeEvaluateResult(value);
                    if (source.length() > 150000) source = source.substring(0, 150000) + "\n\n" + LanguageManager.get("debugger.source_truncated", "[source dipotong pada 150.000 karakter]");
                    currentCopyText = source; contentList.removeAllViews();
                    TextView view = text(source, textColor, 10, true); view.setTypeface(Typeface.MONOSPACE); view.setTextIsSelectable(true); view.setPadding(dp(9), dp(8), dp(9), dp(10));
                    view.setBackground(background(cardColor, borderColor, 6)); contentList.addView(view, new LinearLayout.LayoutParams(-1, -2));
                }
            }
        });
    }

    private void renderElements(final BrowserTab tab) {
        currentCopyText = LanguageManager.get("debugger.loading_elements", "Mengambil ringkasan DOM..."); addPlainCard(currentCopyText, mutedColor);
        if (tab.webView != null) tab.webView.evaluateJavascript("(function(){return 'documentElement: '+(document.documentElement?document.documentElement.tagName:'none')+'\\nbody children: '+(document.body?document.body.children.length:0)+'\\nurl: '+location.href})()", new ValueCallback<String>() {
            public void onReceiveValue(String value) {
                if (isPanelVisible() && "Elements".equals(currentModule)) { currentCopyText = decodeEvaluateResult(value); contentList.removeAllViews(); addPlainCard(currentCopyText, textColor); }
            }
        });
    }

    private List<String> snapshot(BrowserTab tab, boolean console) {
        ArrayList<String> result = new ArrayList<String>();
        synchronized (tab) { result.addAll(console ? tab.consoleLogs : tab.networkLogs); }
        return result;
    }

    private void addPlainCard(String value, int color) {
        TextView v = text(value, color, 11, true); v.setTypeface(Typeface.MONOSPACE); v.setTextIsSelectable(true); v.setPadding(dp(10), dp(9), dp(10), dp(9));
        v.setBackground(background(cardColor, borderColor, 7)); contentList.addView(v, new LinearLayout.LayoutParams(-1, -2));
        currentCopyText = value;
    }

    private TextView text(String value, int color, float size, boolean mono) {
        TextView v = new TextView(activity); v.setText(value); v.setTextColor(color); v.setTextSize(size);
        if (mono) v.setTypeface(Typeface.MONOSPACE);
        return v;
    }

    private String decodeEvaluateResult(String value) {
        if (value == null) return "null";
        try { Object decoded = new JSONTokener(value).nextValue(); return decoded == null ? "null" : String.valueOf(decoded); }
        catch (Exception ignored) { return value; }
    }

    private void copyText(String value, String notice) {
        if (value == null || value.length() == 0) { android.widget.Toast.makeText(activity, LanguageManager.get("debugger.nothing_to_copy", "Tidak ada teks untuk disalin."), android.widget.Toast.LENGTH_SHORT).show(); return; }
        try {
            ClipboardManager clipboard = (ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Chromitext Developer Tools", value));
            android.widget.Toast.makeText(activity, notice, android.widget.Toast.LENGTH_SHORT).show();
        } catch (Exception error) { android.widget.Toast.makeText(activity, LanguageManager.get("debugger.copy_failed", "Gagal menyalin teks."), android.widget.Toast.LENGTH_SHORT).show(); }
    }

    private String firstEnabledModule() {
        String[] modules = new String[]{"Console", "Network", "Storage", "Source", "Elements", "Info"};
        for (int i = 0; i < modules.length; i++) if (database.isDebuggerModuleEnabled(modules[i])) return modules[i];
        return "";
    }

    private int readPanelHeight() {
        try { return Math.max(dp(170), Integer.parseInt(database.getSetting("debugger_panel_height", "360"))); }
        catch (Exception ignored) { return dp(300); }
    }

    private int dp(float value) { return (int)(value * activity.getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable background(int fill, int stroke, float radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); d.setStroke(dp(1), stroke); return d;
    }

    private String safe(String value) { return value == null ? "" : value; }
}
