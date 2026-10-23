package rh.chromitext.browser;

import android.content.Context;
import org.json.JSONArray;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** Loads language registry and UTF-8 properties dictionaries from assets/lang. */
public final class LanguageManager {
    private static final String DIR = "lang/";
    private static final Map<String, String> LANGUAGES = new LinkedHashMap<String, String>();
    private static Properties active = new Properties();
    private static Properties fallback = new Properties();
    private static String activeCode = "id_ID";
    private LanguageManager() {}

    public static synchronized void load(Context context, String code) {
        loadRegistry(context);
        String selected = code;
        if (!LANGUAGES.containsKey(selected)) selected = LANGUAGES.containsKey("id_ID") ? "id_ID" : (LANGUAGES.size() == 0 ? "" : LANGUAGES.keySet().iterator().next());
        active = readProperties(context, selected);
        fallback = readProperties(context, "id_ID");
        activeCode = selected;
    }

    public static synchronized void loadRegistry(Context context) {
        LANGUAGES.clear();
        InputStream in = null;
        try {
            in = context.getAssets().open(DIR + "languages.json");
            StringBuilder json = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) json.append(line);
            JSONArray arr = new JSONArray(json.toString());
            for (int i = 0; i < arr.length(); i++) {
                JSONArray item = arr.optJSONArray(i);
                if (item == null || item.length() < 2) continue;
                String code = item.optString(0, "").trim();
                String name = item.optString(1, "").trim();
                if (code.matches("[A-Za-z]{2,3}_[A-Za-z]{2,4}") && name.length() > 0) LANGUAGES.put(code, name);
            }
        } catch (Exception ignored) {
        } finally { if (in != null) try { in.close(); } catch (IOException ignored) {} }
    }

    private static Properties readProperties(Context context, String code) {
        Properties p = new Properties();
        if (code == null || code.length() == 0) return p;
        InputStream in = null;
        try {
            in = context.getAssets().open(DIR + code + ".lang");
            p.load(new InputStreamReader(in, "UTF-8"));
        } catch (Exception ignored) {
        } finally { if (in != null) try { in.close(); } catch (IOException ignored) {} }
        return p;
    }

    public static synchronized String get(String key, String defaultValue) {
        String value = active.getProperty(key);
        if (value == null) value = fallback.getProperty(key);
        return value == null ? defaultValue : value;
    }
    public static synchronized String format(String key, String defaultValue, Object... args) {
        String pattern = get(key, defaultValue);
        try { return String.format(java.util.Locale.US, pattern, args); }
        catch (Exception ignored) { return pattern; }
    }

    public static synchronized String getCode() { return activeCode; }
    public static synchronized Map<String, String> getLanguages(Context context) {
        loadRegistry(context);
        return new LinkedHashMap<String, String>(LANGUAGES);
    }
}
