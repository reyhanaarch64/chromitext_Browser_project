package rh.chromitext.browser;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Owns local-history and Google address suggestions, including popup lifecycle and async requests. */
public final class AddressSuggestionController {
    public interface Host {
        Activity suggestionActivity();
        BrowserDatabase suggestionDatabase();
        EditText suggestionAddressBar();
        View suggestionBrowserLayer();
        View suggestionAnchor();
        boolean suggestionIsDestroyed();
        boolean suggestionIsDarkTheme();
        int suggestionPanelColor();
        int suggestionBorderColor();
        int suggestionTextColor();
        int suggestionDp(float value);
        GradientDrawable suggestionBackground(int fill, int stroke);
        void navigateFromSuggestion(String url);
        void hideKeyboardForSuggestion();
    }

    private static final class Item {
        final String title;
        final String url;
        final boolean history;
        Item(String title, String url, boolean history) {
            this.title = title == null ? "" : title;
            this.url = url == null ? "" : url;
            this.history = history;
        }
    }

    private final Host host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PopupWindow popup;
    private Runnable pendingTask;
    private int requestId;

    public AddressSuggestionController(Host host) { this.host = host; }

    public void schedule(final String rawQuery, int delayMs) {
        if (pendingTask != null) handler.removeCallbacks(pendingTask);
        final int id = ++requestId;
        pendingTask = new Runnable() {
            public void run() {
                EditText address = host.suggestionAddressBar();
                View layer = host.suggestionBrowserLayer();
                if (host.suggestionIsDestroyed() || address == null || !address.hasFocus() || layer == null || layer.getVisibility() != View.VISIBLE) return;
                String query = rawQuery == null ? "" : rawQuery.trim();
                ArrayList<Item> local = historySuggestions(query, query.length() == 0 ? 6 : 4);
                render(local, query);
                if (query.length() > 0) fetchGoogleSuggestions(query, id);
            }
        };
        handler.postDelayed(pendingTask, Math.max(0, delayMs));
    }

    public void hide() {
        if (pendingTask != null) { handler.removeCallbacks(pendingTask); pendingTask = null; }
        requestId++;
        if (popup != null) {
            try { popup.dismiss(); } catch (Exception ignored) { }
            popup = null;
        }
    }

    public boolean isShowing() { return popup != null && popup.isShowing(); }

    private ArrayList<Item> historySuggestions(String query, int limit) {
        ArrayList<Item> result = new ArrayList<Item>();
        List<String> lines = host.suggestionDatabase().getLines("history");
        for (int i = lines.size() - 1; i >= 0 && result.size() < limit; i--) {
            String[] pair = lines.get(i).split("\t", 2);
            if (pair.length != 2 || pair[1].length() == 0) continue;
            String title = pair[0];
            String url = pair[1];
            boolean found = false;
            for (int j = 0; j < result.size(); j++) if (url.equalsIgnoreCase(result.get(j).url)) { found = true; break; }
            if (found) continue;
            String lowerQuery = query == null ? "" : query.toLowerCase(Locale.US);
            if (lowerQuery.length() > 0 && title.toLowerCase(Locale.US).indexOf(lowerQuery) < 0 && url.toLowerCase(Locale.US).indexOf(lowerQuery) < 0) continue;
            if (title.length() == 0) {
                try { title = Uri.parse(url).getHost(); } catch (Exception ignored) { }
            }
            result.add(new Item(title, url, true));
        }
        return result;
    }

    private void fetchGoogleSuggestions(final String query, final int id) {
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
                    // Suggestions are optional; local history remains available offline.
                } finally {
                    if (reader != null) try { reader.close(); } catch (Exception ignored) { }
                    if (connection != null) connection.disconnect();
                }
                final Activity activity = host.suggestionActivity();
                if (activity == null) return;
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        EditText address = host.suggestionAddressBar();
                        if (host.suggestionIsDestroyed() || id != requestId || address == null || !address.hasFocus()) return;
                        String current = address.getText().toString().trim();
                        if (!current.equals(query)) return;
                        ArrayList<Item> merged = historySuggestions(query, 4);
                        HashSet<String> seen = new HashSet<String>();
                        for (int i = 0; i < merged.size(); i++) {
                            seen.add(merged.get(i).url.toLowerCase(Locale.US));
                            seen.add(merged.get(i).title.toLowerCase(Locale.US));
                        }
                        for (int i = 0; i < values.size(); i++) {
                            String value = values.get(i);
                            String key = value.toLowerCase(Locale.US);
                            if (!seen.contains(key)) { merged.add(new Item(value, value, false)); seen.add(key); }
                        }
                        render(merged, query);
                    }
                });
            }
        }, "Chromitext-SearchSuggestions");
        worker.setDaemon(true);
        worker.start();
    }

    private void render(ArrayList<Item> items, String query) {
        Activity activity = host.suggestionActivity();
        EditText address = host.suggestionAddressBar();
        View anchor = host.suggestionAnchor();
        View layer = host.suggestionBrowserLayer();
        if (activity == null || host.suggestionIsDestroyed() || anchor == null || address == null || !address.hasFocus()) return;
        if (layer == null || layer.getVisibility() != View.VISIBLE) { hide(); return; }
        if (items == null || items.size() == 0) { hide(); return; }
        hidePopupOnly();
        final boolean dark = host.suggestionIsDarkTheme();
        final int panelColor = host.suggestionPanelColor();
        final int border = host.suggestionBorderColor();
        final int textColor = host.suggestionTextColor();
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(0, host.suggestionDp(2), 0, host.suggestionDp(2));
        panel.setBackground(host.suggestionBackground(dark ? Color.rgb(29, 33, 39) : Color.WHITE, border));
        boolean hasHistory = false;
        for (int i = 0; i < items.size(); i++) if (items.get(i).history) hasHistory = true;
        boolean dividerAdded = false;
        for (int i = 0; i < items.size(); i++) {
            final Item item = items.get(i);
            if (hasHistory && !item.history && !dividerAdded) {
                View divider = new View(activity); divider.setBackgroundColor(border);
                panel.addView(divider, new LinearLayout.LayoutParams(-1, host.suggestionDp(1)));
                TextView label = new TextView(activity);
                label.setText(LanguageManager.get("suggestion.google_header", "SARAN GOOGLE"));
                label.setTextColor(dark ? Color.rgb(160, 166, 174) : Color.rgb(100, 100, 100));
                label.setTextSize(10); label.setPadding(host.suggestionDp(12), host.suggestionDp(5), host.suggestionDp(8), host.suggestionDp(2));
                panel.addView(label, new LinearLayout.LayoutParams(-1, -2));
                dividerAdded = true;
            }
            LinearLayout row = new LinearLayout(activity); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(host.suggestionDp(11), host.suggestionDp(6), host.suggestionDp(10), host.suggestionDp(6));
            row.setBackgroundColor(dark ? Color.rgb(29, 33, 39) : Color.WHITE);
            TextView icon = new TextView(activity);
            FontAwesome.apply(activity, icon, "solid", item.history ? 0xf1da : 0xf002, item.history ? "◷" : "⌕");
            icon.setTextSize(18); icon.setGravity(Gravity.CENTER);
            icon.setTextColor(item.history ? Color.rgb(22, 133, 232) : (dark ? Color.rgb(170, 177, 187) : Color.rgb(105, 105, 105)));
            row.addView(icon, new LinearLayout.LayoutParams(host.suggestionDp(28), host.suggestionDp(38)));
            LinearLayout textColumn = new LinearLayout(activity); textColumn.setOrientation(LinearLayout.VERTICAL);
            TextView heading = new TextView(activity); heading.setText(item.title); heading.setTextSize(14); heading.setSingleLine(true); heading.setEllipsize(TextUtils.TruncateAt.END);
            heading.setTextColor(item.history ? Color.rgb(22, 133, 232) : textColor); textColumn.addView(heading, new LinearLayout.LayoutParams(-1, -2));
            TextView detail = new TextView(activity);
            detail.setText(item.history ? item.url : LanguageManager.get("suggestion.search_google", "Cari di Google"));
            detail.setTextSize(11); detail.setSingleLine(true); detail.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            detail.setTextColor(item.history ? Color.rgb(22, 133, 232) : (dark ? Color.rgb(160, 166, 174) : Color.rgb(100, 100, 100)));
            textColumn.addView(detail, new LinearLayout.LayoutParams(-1, -2));
            row.addView(textColumn, new LinearLayout.LayoutParams(0, -2, 1));
            TextView action = new TextView(activity); FontAwesome.apply(activity, action, "solid", 0xf0da, "↖");
            action.setTextSize(16); action.setTextColor(dark ? Color.rgb(160, 166, 174) : Color.rgb(100, 100, 100)); action.setGravity(Gravity.CENTER);
            row.addView(action, new LinearLayout.LayoutParams(host.suggestionDp(28), host.suggestionDp(38)));
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    hide();
                    host.hideKeyboardForSuggestion();
                    String target = item.history ? item.url : "https://www.google.com/search?q=" + Uri.encode(item.title);
                    host.navigateFromSuggestion(target);
                }
            });
            panel.addView(row, new LinearLayout.LayoutParams(-1, -2));
            if (i < items.size() - 1) {
                View separator = new View(activity); separator.setBackgroundColor(dark ? Color.rgb(55, 60, 68) : Color.rgb(230, 230, 230));
                panel.addView(separator, new LinearLayout.LayoutParams(-1, host.suggestionDp(1)));
            }
        }
        scroll.addView(panel, new ScrollView.LayoutParams(-1, -2));
        scroll.setBackgroundColor(dark ? Color.rgb(29, 33, 39) : Color.WHITE);
        int width = Math.max(host.suggestionDp(220), anchor.getWidth());
        scroll.setLayoutParams(new ViewGroup.LayoutParams(width, -2));
        scroll.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(host.suggestionDp(500), View.MeasureSpec.AT_MOST));
        int height = Math.min(scroll.getMeasuredHeight(), host.suggestionDp(360));
        if (height <= 0) height = host.suggestionDp(120);
        popup = new PopupWindow(scroll, width, height, false);
        popup.setBackgroundDrawable(host.suggestionBackground(dark ? Color.rgb(29, 33, 39) : Color.WHITE, border));
        if (Build.VERSION.SDK_INT >= 21) popup.setElevation(host.suggestionDp(4));
        popup.setOutsideTouchable(true); popup.setTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        popup.showAsDropDown(anchor, 0, host.suggestionDp(1));
    }

    private void hidePopupOnly() {
        if (popup != null) { try { popup.dismiss(); } catch (Exception ignored) { } popup = null; }
    }
}
