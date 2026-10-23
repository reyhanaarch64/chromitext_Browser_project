package rh.chromitext.browser;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Persistent browser settings, bookmarks, history, debugger options and extension logs. */
public class BrowserDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "chromite.db";
    private static final int DATABASE_VERSION = 1;

    public BrowserDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /** One-time import from the older app build; all future writes use chromite.db. */
    public synchronized void migrateLegacyPreferences(Context context) {
        if ("1".equals(getSetting("migration.browser_prefs_v1", "0"))) return;
        SharedPreferences old = context.getSharedPreferences("chromitext_browser", Context.MODE_PRIVATE);
        if (getSetting("user_agent_mode", null) == null) {
            putSetting("user_agent_mode", old.getBoolean("desktop_mode", false) ? "desktop" : "default");
        }
        if (getSetting("javascript_enabled", null) == null) {
            putBoolean("javascript_enabled", old.getBoolean("javascript_enabled", true));
        }
        migrateLinesFromPreferences(old, "bookmarks");
        migrateLinesFromPreferences(old, "history");
        old.edit().clear().commit();
        putSetting("migration.browser_prefs_v1", "1");
    }

    private void migrateLinesFromPreferences(SharedPreferences old, String key) {
        if (getLines(key).size() > 0) return;
        String raw = old.getString(key, "");
        if (raw == null || raw.length() == 0) return;
        ArrayList<String> lines = new ArrayList<String>();
        String[] items = raw.split("\n");
        for (int i = 0; i < items.length; i++) if (items[i].length() > 0) lines.add(items[i]);
        replaceLines(key, lines);
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        db.execSQL("CREATE TABLE bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, url TEXT NOT NULL UNIQUE, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE history (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, url TEXT NOT NULL, visited_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX history_url_idx ON history(url)");
        db.execSQL("CREATE TABLE debugger_modules (name TEXT PRIMARY KEY, enabled INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE extension_logs (id INTEGER PRIMARY KEY AUTOINCREMENT, extension_id TEXT NOT NULL, message TEXT NOT NULL, created_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE site_origins (origin TEXT PRIMARY KEY, last_seen INTEGER NOT NULL)");
        insertDefaultModule(db, "Console", 1);
        insertDefaultModule(db, "Network", 1);
        insertDefaultModule(db, "Storage", 1);
        insertDefaultModule(db, "Source", 1);
        insertDefaultModule(db, "Elements", 0);
        insertDefaultModule(db, "Info", 0);
    }

    private void insertDefaultModule(SQLiteDatabase db, String name, int enabled) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("enabled", enabled);
        db.insert("debugger_modules", null, values);
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Future schema upgrades go here. Existing user data must not be dropped.
    }

    public synchronized String getSetting(String key, String fallback) {
        Cursor c = getReadableDatabase().query("settings", new String[]{"value"}, "key=?", new String[]{key}, null, null, null);
        try { return c.moveToFirst() ? c.getString(0) : fallback; }
        finally { c.close(); }
    }

    public synchronized boolean getBoolean(String key, boolean fallback) {
        String value = getSetting(key, fallback ? "1" : "0");
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    public synchronized void putSetting(String key, String value) {
        ContentValues values = new ContentValues();
        values.put("key", key);
        values.put("value", value == null ? "" : value);
        getWritableDatabase().insertWithOnConflict("settings", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized void putBoolean(String key, boolean value) {
        putSetting(key, value ? "1" : "0");
    }

    /** Writes a group of Boolean settings in one transaction to avoid repeated UI-thread SQLite commits. */
    public synchronized void putBooleanSettings(Map<String, Boolean> values) {
        if (values == null || values.size() == 0) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Map.Entry<String, Boolean> entry : values.entrySet()) {
                if (entry.getKey() == null) continue;
                ContentValues row = new ContentValues();
                row.put("key", entry.getKey());
                row.put("value", Boolean.TRUE.equals(entry.getValue()) ? "1" : "0");
                db.insertWithOnConflict("settings", null, row, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public synchronized void removeSetting(String key) {
        getWritableDatabase().delete("settings", "key=?", new String[]{key});
    }

    /**
     * Permanently purge persistent state for an uninstalled extension.
     * This removes enable/permission/storage keys and every saved debugger log
     * for the exact extension id in one SQLite transaction.
     */
    public synchronized void removeExtensionRecords(String extensionId) {
        removeExtensionRecords(extensionId, null);
    }

    public synchronized void removeExtensionRecords(String extensionId, Set<String> retainedSettingKeys) {
        if (extensionId == null || extensionId.length() == 0) return;
        SQLiteDatabase db = getWritableDatabase();
        ArrayList<String> removeKeys = new ArrayList<String>();
        Cursor cursor = db.query("settings", new String[]{"key"}, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                String key = cursor.getString(0);
                if (isExtensionOwnedSettingKey(key, extensionId) &&
                        (retainedSettingKeys == null || !retainedSettingKeys.contains(key))) {
                    removeKeys.add(key);
                }
            }
        } finally { cursor.close(); }

        db.beginTransaction();
        try {
            for (int i = 0; i < removeKeys.size(); i++) {
                db.delete("settings", "key=?", new String[]{removeKeys.get(i)});
            }
            db.delete("extension_logs", "extension_id=?", new String[]{extensionId});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private boolean isExtensionOwnedSettingKey(String key, String id) {
        if (key == null) return false;
        if (key.equals("extension.enabled." + id)) return true;
        if (key.equals("extension.permission." + id)) return true;
        if (key.startsWith("extension.permission." + id + ".")) return true;
        if (key.startsWith("extension.storage." + id + ".") || key.equals("extension.storage." + id)) return true;
        if (key.startsWith("extension.data." + id + ".") || key.equals("extension.data." + id)) return true;
        if (key.startsWith("extension.runtime." + id + ".") || key.equals("extension.runtime." + id)) return true;
        if (key.startsWith("extension." + id + ".")) return true;
        return false;
    }

    public synchronized List<String> getLines(String table) {
        ArrayList<String> result = new ArrayList<String>();
        if ("bookmarks".equals(table)) {
            Cursor c = getReadableDatabase().query("bookmarks", new String[]{"title", "url"}, null, null, null, null, "created_at ASC");
            try { while (c.moveToNext()) result.add(clean(c.getString(0)) + "\t" + c.getString(1)); }
            finally { c.close(); }
        } else if ("history".equals(table)) {
            Cursor c = getReadableDatabase().query("history", new String[]{"title", "url"}, null, null, null, null, "visited_at ASC, id ASC");
            try { while (c.moveToNext()) result.add(clean(c.getString(0)) + "\t" + c.getString(1)); }
            finally { c.close(); }
        }
        return result;
    }

    public synchronized void replaceLines(String table, List<String> lines) {
        if (!"bookmarks".equals(table) && !"history".equals(table)) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(table, null, null);
            long now = System.currentTimeMillis();
            for (int i = 0; i < lines.size(); i++) {
                String[] pair = lines.get(i).split("\t", 2);
                if (pair.length != 2 || pair[1].length() == 0) continue;
                ContentValues values = new ContentValues();
                values.put("title", clean(pair[0]));
                values.put("url", pair[1]);
                if ("bookmarks".equals(table)) {
                    values.put("created_at", now + i);
                    db.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_IGNORE);
                } else {
                    values.put("visited_at", now + i);
                    db.insert(table, null, values);
                }
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public synchronized void addHistory(String title, String url, int maxItems) {
        if (url == null || url.length() == 0) return;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("title", clean(title));
        values.put("url", url);
        values.put("visited_at", System.currentTimeMillis());
        db.insert("history", null, values);
        db.execSQL("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY visited_at DESC, id DESC LIMIT ?)", new Object[]{Integer.valueOf(maxItems)});
    }

    public synchronized boolean isBookmarked(String url) {
        Cursor c = getReadableDatabase().query("bookmarks", new String[]{"url"}, "url=?", new String[]{url}, null, null, null);
        try { return c.moveToFirst(); }
        finally { c.close(); }
    }

    public synchronized void addBookmark(String title, String url) {
        if (url == null || url.length() == 0) return;
        ContentValues values = new ContentValues();
        values.put("title", clean(title));
        values.put("url", url);
        values.put("created_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("bookmarks", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized void deleteBookmark(String url) {
        getWritableDatabase().delete("bookmarks", "url=?", new String[]{url});
    }

    public synchronized void deleteHistoryUrl(String url) {
        getWritableDatabase().delete("history", "url=?", new String[]{url});
    }

    public synchronized void clearHistory() { getWritableDatabase().delete("history", null, null); }

    public synchronized boolean isDebuggerModuleEnabled(String name) {
        Cursor c = getReadableDatabase().query("debugger_modules", new String[]{"enabled"}, "name=?", new String[]{name}, null, null, null);
        try { return c.moveToFirst() && c.getInt(0) != 0; }
        finally { c.close(); }
    }

    public synchronized void setDebuggerModuleEnabled(String name, boolean enabled) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("enabled", enabled ? 1 : 0);
        getWritableDatabase().insertWithOnConflict("debugger_modules", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized void rememberOrigin(String origin) {
        if (origin == null || origin.length() == 0) return;
        ContentValues values = new ContentValues();
        values.put("origin", origin);
        values.put("last_seen", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("site_origins", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized List<String> getKnownOrigins() {
        ArrayList<String> result = new ArrayList<String>();
        Cursor c = getReadableDatabase().query("site_origins", new String[]{"origin"}, null, null, null, null, "last_seen DESC");
        try { while (c.moveToNext()) result.add(c.getString(0)); }
        finally { c.close(); }
        return result;
    }

    public synchronized void deleteOrigin(String origin) {
        getWritableDatabase().delete("site_origins", "origin=?", new String[]{origin});
    }

    public synchronized void addExtensionLog(String extensionId, String message) {
        ContentValues values = new ContentValues();
        values.put("extension_id", extensionId == null ? "?" : extensionId);
        values.put("message", message == null ? "" : message);
        values.put("created_at", System.currentTimeMillis());
        SQLiteDatabase db = getWritableDatabase();
        db.insert("extension_logs", null, values);
        db.execSQL("DELETE FROM extension_logs WHERE id NOT IN (SELECT id FROM extension_logs ORDER BY id DESC LIMIT 500)");
    }

    /** Returns all logs when extensionId is null, otherwise only the selected extension's logs. */
    public synchronized String getExtensionLogs(String extensionId) {
        StringBuilder out = new StringBuilder();
        Cursor c;
        if (extensionId == null) {
            c = getReadableDatabase().query("extension_logs", new String[]{"extension_id", "message", "created_at"}, null, null, null, null, "id ASC");
        } else {
            c = getReadableDatabase().query("extension_logs", new String[]{"extension_id", "message", "created_at"}, "extension_id=?", new String[]{extensionId}, null, null, "id ASC");
        }
        try {
            while (c.moveToNext()) {
                String time = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date(c.getLong(2)));
                out.append('[').append(time).append("] [").append(c.getString(0)).append("] ").append(c.getString(1)).append('\n');
            }
        } finally { c.close(); }
        return out.toString();
    }

    public synchronized String getExtensionLogs() { return getExtensionLogs(null); }

    public synchronized void clearExtensionLogs(String extensionId) {
        if (extensionId == null) getWritableDatabase().delete("extension_logs", null, null);
        else getWritableDatabase().delete("extension_logs", "extension_id=?", new String[]{extensionId});
    }

    public synchronized void clearExtensionLogs() { clearExtensionLogs(null); }


    public synchronized void saveSessionUrls(List<String> urls, int activeIndex) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Cursor cursor = db.query("settings", new String[]{"key"}, "key LIKE ?", new String[]{"session_url_%"}, null, null, null);
            ArrayList<String> oldKeys = new ArrayList<String>();
            try { while (cursor.moveToNext()) oldKeys.add(cursor.getString(0)); }
            finally { cursor.close(); }
            for (int i = 0; i < oldKeys.size(); i++) db.delete("settings", "key=?", new String[]{oldKeys.get(i)});
            int count = urls == null ? 0 : Math.min(40, urls.size());
            putSetting("session_count", String.valueOf(count));
            putSetting("session_active", String.valueOf(Math.max(0, Math.min(Math.max(0, count - 1), activeIndex))));
            for (int i = 0; i < count; i++) putSetting("session_url_" + i, urls.get(i) == null ? "about:blank" : urls.get(i));
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public synchronized List<String> getSessionUrls() {
        ArrayList<String> urls = new ArrayList<String>();
        int count = 0;
        try { count = Integer.parseInt(getSetting("session_count", "0")); } catch (Exception ignored) { }
        count = Math.max(0, Math.min(40, count));
        for (int i = 0; i < count; i++) {
            String url = getSetting("session_url_" + i, "about:blank");
            if (url == null || url.length() == 0) url = "about:blank";
            urls.add(url);
        }
        return urls;
    }

    public synchronized int getSessionActiveIndex() {
        try { return Integer.parseInt(getSetting("session_active", "0")); }
        catch (Exception ignored) { return 0; }
    }

    private String clean(String value) {
        if (value == null) return "";
        return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
