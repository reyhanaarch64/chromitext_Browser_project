package rh.chromitext.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ExtensionManager {
    private static final long MAX_TOTAL_UNZIPPED = 25L * 1024L * 1024L;
    private static final long MAX_FILE_SIZE = 5L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 800;
    private final Context context;
    private final File root;
    private final BrowserDatabase database;
    private final boolean ownsDatabase;
    private final List<ExtensionInfo> extensions = new ArrayList<ExtensionInfo>();

    public static class PreparedInstall {
        public ExtensionInfo info;
        public File stagingDirectory;
        public boolean consumed;
    }

    public ExtensionManager(Context context) {
        this(context, new BrowserDatabase(context.getApplicationContext()), true);
    }

    public ExtensionManager(Context context, BrowserDatabase database) {
        this(context, database, false);
    }

    private ExtensionManager(Context context, BrowserDatabase database, boolean ownsDatabase) {
        this.context = context.getApplicationContext();
        this.root = new File(this.context.getFilesDir(), "extensions");
        if (!root.exists()) root.mkdirs();
        this.database = database;
        this.ownsDatabase = ownsDatabase;
        migrateLegacyExtensionPreferences();
        refresh();
    }

    public void close() {
        if (ownsDatabase && database != null) database.close();
    }

    private void migrateLegacyExtensionPreferences() {
        if ("1".equals(database.getSetting("migration.extension_prefs_v1", "0"))) return;
        SharedPreferences legacy = context.getSharedPreferences("chromitext_extensions", Context.MODE_PRIVATE);
        Map<String, ?> values = legacy.getAll();
        for (Map.Entry<String, ?> item : values.entrySet()) {
            String oldKey = item.getKey();
            Object oldValue = item.getValue();
            String newKey = null;
            if (oldKey.startsWith("enabled.")) newKey = "extension.enabled." + oldKey.substring("enabled.".length());
            else if (oldKey.startsWith("permission.")) newKey = "extension.permission." + oldKey.substring("permission.".length());
            if (newKey != null && oldValue instanceof Boolean && database.getSetting(newKey, null) == null) {
                database.putBoolean(newKey, ((Boolean) oldValue).booleanValue());
            }
        }
        legacy.edit().clear().commit();
        database.putSetting("migration.extension_prefs_v1", "1");
    }

    public synchronized void refresh() {
        extensions.clear();
        if (!root.exists()) root.mkdirs();
        File[] dirs = root.listFiles();
        if (dirs == null) return;
        for (int i = 0; i < dirs.length; i++) {
            File dir = dirs[i];
            if (!dir.isDirectory() || dir.getName().startsWith(".")) continue;
            try {
                ExtensionInfo info = parseManifest(dir);
                if (info != null) extensions.add(info);
            } catch (Exception ignored) { }
        }
    }

    public synchronized List<ExtensionInfo> getExtensions() {
        return new ArrayList<ExtensionInfo>(extensions);
    }

    public synchronized ExtensionInfo getExtension(String id) {
        for (int i = 0; i < extensions.size(); i++) {
            if (extensions.get(i).id.equals(id)) return extensions.get(i);
        }
        return null;
    }

    public File getRootDirectory() { return root; }

    public File getExtensionDirectory(String id) {
        if (id != null) {
            ExtensionInfo installed = getExtension(id);
            if (installed != null && installed.directory != null) return installed.directory;
        }
        return new File(root, id == null ? "" : id);
    }

    public boolean isEnabled(ExtensionInfo info) {
        return info != null && database.getBoolean("extension.enabled." + info.id, true);
    }

    public void setEnabled(ExtensionInfo info, boolean enabled) {
        if (info != null) database.putBoolean("extension.enabled." + info.id, enabled);
    }

    public boolean hasPermission(ExtensionInfo info, String permission) {
        if (info == null || permission == null || !info.permissions.contains(permission)) return false;
        return database.getBoolean("extension.permission." + info.id + "." + permission, false);
    }

    public boolean isPermissionDeclared(ExtensionInfo info, String permission) {
        return info != null && info.permissions.contains(permission);
    }

    public void setPermission(ExtensionInfo info, String permission, boolean allowed) {
        if (info != null && info.permissions.contains(permission)) {
            database.putBoolean("extension.permission." + info.id + "." + permission, allowed);
        }
    }

    public void setPermissions(ExtensionInfo info, Set<String> allowed) {
        if (info == null) return;
        Map<String, Boolean> updates = new java.util.HashMap<String, Boolean>();
        for (int i = 0; i < info.permissions.size(); i++) {
            String permission = info.permissions.get(i);
            updates.put("extension.permission." + info.id + "." + permission,
                Boolean.valueOf(allowed != null && allowed.contains(permission)));
        }
        database.putBooleanSettings(updates);
    }

    public boolean matchesDomainLock(ExtensionInfo info, String pageUrl) {
        if (info == null) return false;
        if (!info.domainLockEnabled) return true;
        for (int i = 0; i < info.domains.size(); i++) {
            if (matchesOriginPattern(pageUrl, info.domains.get(i), info.includeSubdomains)) return true;
        }
        return false;
    }

    public static boolean matchesDomain(String url, String pattern) {
        return matchesOriginPattern(url, pattern, false);
    }

    public static boolean matchesHost(String url, String pattern, boolean includeSubdomains) {
        Uri target = parseHttpUri(url);
        if (target == null || target.getHost() == null) return false;
        String value = pattern == null ? "" : pattern.trim();
        if (value.length() == 0) return false;
        if (value.startsWith("*.")) {
            includeSubdomains = true;
            value = value.substring(2);
        }
        Uri rule = parseHttpUri(value);
        if (rule != null && rule.getHost() != null) {
            String targetHost = target.getHost().toLowerCase(Locale.US);
            String ruleHost = rule.getHost().toLowerCase(Locale.US);
            boolean hostMatches = targetHost.equals(ruleHost) ||
                (includeSubdomains && targetHost.endsWith("." + ruleHost));
            if (!hostMatches) return false;
            if (hasExplicitPort(value, rule) && effectivePort(target) != effectivePort(rule)) return false;
            if (value.indexOf("://") >= 0 && !target.getScheme().equalsIgnoreCase(rule.getScheme())) return false;
            return true;
        }
        String host = value.toLowerCase(Locale.US);
        int slash = host.indexOf('/');
        if (slash >= 0) host = host.substring(0, slash);
        int colon = host.lastIndexOf(':');
        String requiredPort = null;
        if (colon > 0 && host.indexOf(']') < colon) {
            requiredPort = host.substring(colon + 1);
            host = host.substring(0, colon);
        }
        String targetHost = target.getHost().toLowerCase(Locale.US);
        boolean same = targetHost.equals(host) || (includeSubdomains && targetHost.endsWith("." + host));
        if (!same) return false;
        if (requiredPort != null) {
            try { return effectivePort(target) == Integer.parseInt(requiredPort); }
            catch (Exception ignored) { return false; }
        }
        return true;
    }

    private static boolean matchesOriginPattern(String url, String pattern, boolean includeSubdomains) {
        if (url == null || pattern == null) return false;
        Uri target = parseHttpUri(url);
        String value = pattern.trim();
        if (target == null || target.getHost() == null || value.length() == 0) return false;
        boolean wildcard = value.startsWith("*.");
        if (wildcard) value = value.substring(2);
        Uri rule = parseHttpUri(value);
        if (rule == null || rule.getHost() == null) return false;
        String targetHost = target.getHost().toLowerCase(Locale.US);
        String ruleHost = rule.getHost().toLowerCase(Locale.US);
        boolean hostMatches = targetHost.equals(ruleHost) ||
            ((includeSubdomains || wildcard) && targetHost.endsWith("." + ruleHost));
        if (!hostMatches) return false;
        if (value.indexOf("://") >= 0) {
            if (!target.getScheme().equalsIgnoreCase(rule.getScheme())) return false;
            if (effectivePort(target) != effectivePort(rule)) return false;
        } else if (hasExplicitPort(value, rule) && effectivePort(target) != effectivePort(rule)) {
            return false;
        }
        return true;
    }

    private static Uri parseHttpUri(String value) {
        if (value == null) return null;
        String s = value.trim();
        if (s.length() == 0) return null;
        if (s.startsWith("*.")) s = s.substring(2);
        if (s.indexOf("://") < 0) s = "https://" + s;
        try {
            Uri uri = Uri.parse(s);
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) return null;
            if (uri.getHost() == null || uri.getHost().length() == 0) return null;
            return uri;
        } catch (Exception ignored) { return null; }
    }

    private static boolean hasExplicitPort(String raw, Uri uri) {
        if (raw == null || uri == null) return false;
        String authority = uri.getEncodedAuthority();
        if (authority == null) return false;
        int colon = authority.lastIndexOf(':');
        return colon > 0 && authority.indexOf(']') < colon;
    }

    private static int effectivePort(Uri uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "http".equalsIgnoreCase(uri.getScheme()) ? 80 : 443;
    }

    public PreparedInstall prepareInstall(InputStream source) throws Exception {
        if (source == null) throw new IOException("file zip kosong");
        File staging = new File(context.getFilesDir(), ".install_" + System.currentTimeMillis());
        if (!staging.mkdirs()) throw new IOException("gagal membuat direktori staging");
        boolean success = false;
        try {
            ZipInputStream zip = new ZipInputStream(new BufferedInputStream(source));
            byte[] buffer = new byte[8192];
            long total = 0L;
            int entryCount = 0;
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ENTRIES) throw new IOException("arsip memiliki terlalu banyak entri");
                String name = normalizeZipPath(entry.getName());
                if (name.length() == 0) { zip.closeEntry(); continue; }
                File out = new File(staging, name);
                String basePath = staging.getCanonicalPath() + File.separator;
                String outPath = out.getCanonicalPath();
                if (!outPath.startsWith(basePath)) throw new IOException("path zip keluar dari direktori extension");
                if (entry.isDirectory()) {
                    if (!out.exists() && !out.mkdirs()) throw new IOException("gagal membuat folder extension");
                    zip.closeEntry();
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("gagal membuat folder file");
                BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(out));
                long fileSize = 0L;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    fileSize += read;
                    total += read;
                    if (fileSize > MAX_FILE_SIZE) throw new IOException("file dalam zip terlalu besar");
                    if (total > MAX_TOTAL_UNZIPPED) throw new IOException("ukuran hasil ekstraksi melewati batas 25 MB");
                    output.write(buffer, 0, read);
                }
                output.close();
                zip.closeEntry();
            }
            zip.close();
            ExtensionInfo info = parseManifest(staging);
            if (info == null) throw new IOException("manifest.json tidak ditemukan atau tidak valid");
            validateManifestFiles(staging, info);
            PreparedInstall prepared = new PreparedInstall();
            prepared.info = info;
            prepared.stagingDirectory = staging;
            success = true;
            return prepared;
        } finally {
            if (!success) deleteRecursive(staging);
        }
    }

    public synchronized void commitInstall(PreparedInstall prepared, Set<String> allowedPermissions) throws Exception {
        if (prepared == null || prepared.consumed || prepared.info == null || prepared.stagingDirectory == null) {
            throw new IOException("data instalasi tidak valid");
        }
        String id = prepared.info.id;
        if (!id.matches("[A-Za-z0-9._-]{1,128}")) throw new IOException("id extension tidak valid");
        File destination = new File(root, id);
        File backup = new File(context.getFilesDir(), ".backup_" + id + "_" + System.currentTimeMillis());
        boolean movedOld = false;
        if (destination.exists()) {
            if (!destination.renameTo(backup)) throw new IOException("gagal memindahkan extension versi lama");
            movedOld = true;
        }
        if (!prepared.stagingDirectory.renameTo(destination)) {
            if (movedOld) backup.renameTo(destination);
            throw new IOException("gagal memasang file extension");
        }
        if (movedOld) deleteRecursive(backup);
        prepared.consumed = true;
        setPermissions(prepared.info, allowedPermissions);
        database.putBoolean("extension.enabled." + id, true);
        refresh();
    }

    public void cancelInstall(PreparedInstall prepared) {
        if (prepared != null && !prepared.consumed) {
            deleteRecursive(prepared.stagingDirectory);
            prepared.consumed = true;
        }
    }

    /**
     * Completely removes an extension from disk and removes its persisted state.
     * Use the directory actually discovered from manifest.json, not only root/id:
     * older builds may have stored an extension in a folder whose name differs
     * from the id inside its manifest.
     */
    public synchronized void deleteExtension(ExtensionInfo info) throws IOException {
        if (info == null) throw new IOException("extension tidak ditemukan");
        String id = info.id == null ? "" : info.id.trim();
        if (!id.matches("[A-Za-z0-9._-]{1,128}")) throw new IOException("id extension tidak valid");

        ArrayList<File> targets = new ArrayList<File>();
        File[] dirs = root.listFiles();
        if (dirs == null && root.exists()) throw new IOException("gagal membaca direktori extension");
        if (dirs != null) {
            for (int i = 0; i < dirs.length; i++) {
                File candidate = dirs[i];
                if (!candidate.isDirectory() || candidate.getName().startsWith(".")) continue;
                boolean sameDiscoveredDirectory = sameCanonicalPath(candidate, info.directory);
                boolean sameManifestId = manifestHasId(candidate, id);
                if (sameDiscoveredDirectory || sameManifestId) addUniqueDirectory(targets, candidate);
            }
        }
        // Also consider the expected canonical folder if this is a standard install.
        File expected = new File(root, id);
        if (expected.isDirectory() && manifestHasId(expected, id)) addUniqueDirectory(targets, expected);

        for (int i = 0; i < targets.size(); i++) {
            File target = targets.get(i);
            if (!deleteRecursiveStrict(target)) {
                throw new IOException("sebagian file extension gagal dihapus: " + target.getName());
            }
            if (target.exists()) throw new IOException("folder extension masih tersisa: " + target.getName());
        }

        // Verify the files are no longer discoverable before erasing metadata.
        refresh();
        if (getExtension(id) != null) {
            throw new IOException("folder extension masih terdeteksi setelah penghapusan: " + id);
        }

        // Preserve records belonging to surviving extensions, including ids that
        // happen to share a textual prefix with the extension being removed.
        Set<String> retainedSettingKeys = new HashSet<String>();
        Set<String> retainedLegacyKeys = new HashSet<String>();
        for (int i = 0; i < extensions.size(); i++) {
            ExtensionInfo retained = extensions.get(i);
            if (retained.id == null || id.equals(retained.id)) continue;
            retainedSettingKeys.add("extension.enabled." + retained.id);
            retainedLegacyKeys.add("enabled." + retained.id);
            for (int j = 0; j < retained.permissions.size(); j++) {
                retainedSettingKeys.add("extension.permission." + retained.id + "." + retained.permissions.get(j));
                retainedLegacyKeys.add("permission." + retained.id + "." + retained.permissions.get(j));
            }
        }
        // Purge all persisted records, including historical debugger logs and
        // stale permission keys from older manifests that no longer declare them.
        // Preserve exact settings still owned by other installed extensions.
        if (database != null) database.removeExtensionRecords(id, retainedSettingKeys);
        removeLegacyExtensionPreferences(id, retainedLegacyKeys);
        refresh();
    }

    private boolean manifestHasId(File directory, String id) {
        try {
            ExtensionInfo found = parseManifest(directory);
            return found != null && id.equals(found.id);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean sameCanonicalPath(File first, File second) {
        if (first == null || second == null) return false;
        try { return first.getCanonicalPath().equals(second.getCanonicalPath()); }
        catch (IOException ignored) { return first.getAbsolutePath().equals(second.getAbsolutePath()); }
    }

    private void addUniqueDirectory(ArrayList<File> targets, File candidate) {
        for (int i = 0; i < targets.size(); i++) {
            if (sameCanonicalPath(targets.get(i), candidate)) return;
        }
        targets.add(candidate);
    }

    private void removeLegacyExtensionPreferences(String id, Set<String> retainedLegacyKeys) {
        try {
            SharedPreferences legacy = context.getSharedPreferences("chromitext_extensions", Context.MODE_PRIVATE);
            Map<String, ?> all = legacy.getAll();
            SharedPreferences.Editor editor = legacy.edit();
            boolean changed = false;
            String enabledKey = "enabled." + id;
            String permissionPrefix = "permission." + id + ".";
            String permissionExact = "permission." + id;
            for (String key : all.keySet()) {
                if ((enabledKey.equals(key) || permissionExact.equals(key) || key.startsWith(permissionPrefix)) &&
                        (retainedLegacyKeys == null || !retainedLegacyKeys.contains(key))) {
                    editor.remove(key);
                    changed = true;
                }
            }
            if (changed) editor.commit();
        } catch (Exception ignored) { }
    }

    public static String permissionDescription(String permission) {
        if ("webNavigation".equals(permission)) return "memantau navigasi dan halaman aktif";
        if ("navigationBlocking".equals(permission)) return "memblokir navigasi ke domain tertentu";
        if ("webRequest".equals(permission)) return "memantau metadata request jaringan yang tersedia";
        if ("webRequestBlocking".equals(permission)) return "memblokir request jaringan yang didukung";
        if ("scripting".equals(permission)) return "menyuntikkan javascript ke halaman web";
        if ("userAgent".equals(permission)) return "mengubah user-agent berdasarkan domain";
        if ("tabs".equals(permission)) return "membaca dan mengelola tab browser";
        if ("storage".equals(permission)) return "menyimpan data extension";
        if ("nativeBridge".equals(permission)) return "menyediakan fungsi native untuk origin yang diizinkan";
        if ("localServer".equals(permission)) return "menjalankan server lokal pada 127.0.0.1";
        if ("internet".equals(permission)) return "akses jaringan internet melalui kode Java extension";
        if ("androidApi".equals(permission)) return "menggunakan API Android seperti perangkat, service, dan sistem";
        if ("reflection".equals(permission)) return "memakai reflection atau pemuatan kelas secara dinamis";
        if ("processExecution".equals(permission)) return "menjalankan proses/perintah sistem melalui Java";
        if ("androidClasses".equals(permission)) return "akses kelas android melalui runtime (izin lama)";
        if ("appClasses".equals(permission)) return "akses kelas aplikasi yang tersedia";
        if ("fileAccess".equals(permission)) return "akses file langsung melalui kelas Java";
        return "kemampuan extension: " + permission;
    }

    private ExtensionInfo parseManifest(File directory) throws Exception {
        File manifest = new File(directory, "manifest.json");
        if (!manifest.isFile()) return null;
        JSONObject json = new JSONObject(readFile(manifest, 1024 * 1024));
        int manifestVersion = json.optInt("manifest_version", -1);
        if (manifestVersion != 1) throw new IOException("manifest_version tidak didukung");
        ExtensionInfo info = new ExtensionInfo();
        info.id = json.optString("id", "");
        info.name = json.optString("name", info.id);
        info.version = json.optString("version", "1.0.0");
        info.type = json.optString("type", "").toLowerCase(Locale.US);
        info.description = json.optString("description", "");
        info.author = json.optString("author", "");
        info.directory = directory;
        if (!info.id.matches("[A-Za-z0-9._-]{1,128}")) throw new IOException("id extension tidak valid");
        if (!"java".equals(info.type) && !"javascript".equals(info.type)) throw new IOException("type harus java atau javascript");

        JSONObject entryObject = json.optJSONObject("entry");
        if ("javascript".equals(info.type)) {
            String jsEntry = json.optString("entry", "");
            if (entryObject != null) jsEntry = entryObject.optString("javascript", "");
            if (jsEntry.length() == 0) throw new IOException("entry javascript belum ditentukan");
            jsEntry = validateRelativePath(jsEntry);
            if (!jsEntry.startsWith("script/javascript/") || !jsEntry.toLowerCase(Locale.US).endsWith(".js")) throw new IOException("entry javascript harus berada di script/javascript/ dan berakhiran .js");
            info.javascriptEntries.add(jsEntry);
        } else {
            if (entryObject == null) throw new IOException("extension java harus memakai entry berbentuk object");
            info.javaEntry = validateRelativePath(entryObject.optString("java", ""));
            if (info.javaEntry.length() == 0) throw new IOException("entry.java belum ditentukan");
            if (!info.javaEntry.startsWith("script/java/") || !info.javaEntry.toLowerCase(Locale.US).endsWith(".java")) throw new IOException("entry java harus berada di script/java/ dan berakhiran .java");
            JSONArray scripts = entryObject.optJSONArray("javascript");
            if (scripts != null) {
                for (int i = 0; i < scripts.length(); i++) {
                    String path = validateRelativePath(scripts.optString(i, ""));
                    if (path.length() > 0) {
                        if (!path.startsWith("script/javascript/") || !path.toLowerCase(Locale.US).endsWith(".js")) throw new IOException("entry javascript harus berada di script/javascript/ dan berakhiran .js");
                        info.javascriptEntries.add(path);
                    }
                }
            }
        }

        JSONArray permissions = json.optJSONArray("permissions");
        if (permissions != null) {
            for (int i = 0; i < permissions.length(); i++) {
                String p = permissions.optString(i, "").trim();
                if (p.length() > 0 && !info.permissions.contains(p)) info.permissions.add(p);
            }
        }

        JSONObject lock = json.optJSONObject("domain_lock");
        if (lock != null) {
            info.domainLockEnabled = lock.optBoolean("enabled", false);
            info.includeSubdomains = lock.optBoolean("include_subdomains", false);
            JSONArray domains = lock.optJSONArray("domains");
            if (domains != null) {
                for (int i = 0; i < domains.length(); i++) {
                    String domain = domains.optString(i, "").trim();
                    if (domain.length() > 0) info.domains.add(domain);
                }
            }
            if (info.domainLockEnabled && info.domains.size() == 0) {
                throw new IOException("domain_lock aktif tetapi daftar domains kosong");
            }
        }
        return info;
    }

    private void validateManifestFiles(File base, ExtensionInfo info) throws Exception {
        if ("java".equals(info.type)) {
            if (!resolveInside(base, info.javaEntry).isFile()) throw new IOException("entry java tidak ditemukan: " + info.javaEntry);
        }
        if ("javascript".equals(info.type) && info.javascriptEntries.size() == 0) {
            throw new IOException("entry javascript kosong");
        }
        for (int i = 0; i < info.javascriptEntries.size(); i++) {
            String path = info.javascriptEntries.get(i);
            if (!resolveInside(base, path).isFile()) throw new IOException("entry javascript tidak ditemukan: " + path);
        }
    }

    private static String validateRelativePath(String path) throws IOException {
        if (path == null) throw new IOException("path kosong");
        String value = path.trim().replace('\\', '/');
        if (value.length() == 0 || value.startsWith("/") || value.indexOf(':') >= 0) throw new IOException("path script tidak valid");
        String[] parts = value.split("/");
        for (int i = 0; i < parts.length; i++) {
            if ("..".equals(parts[i]) || ".".equals(parts[i]) || parts[i].length() == 0) throw new IOException("path script tidak valid");
        }
        return value;
    }

    private static String normalizeZipPath(String path) throws IOException {
        if (path == null) throw new IOException("entri zip tanpa nama");
        String value = path.replace('\\', '/');
        if (value.startsWith("/") || value.startsWith("~")) throw new IOException("path absolut tidak diizinkan");
        String[] parts = value.split("/");
        for (int i = 0; i < parts.length; i++) {
            if ("..".equals(parts[i]) || ".".equals(parts[i])) throw new IOException("path traversal ditolak");
        }
        while (value.startsWith("./")) value = value.substring(2);
        return value;
    }

    private static File resolveInside(File base, String relativePath) throws IOException {
        String relative = validateRelativePath(relativePath);
        File file = new File(base, relative);
        String basePath = base.getCanonicalPath() + File.separator;
        if (!file.getCanonicalPath().startsWith(basePath)) throw new IOException("path keluar dari direktori extension");
        return file;
    }

    public String readScript(ExtensionInfo info, String relativePath) throws IOException {
        File file = resolveInside(info.directory, relativePath);
        return readFile(file, 2 * 1024 * 1024);
    }

    private static String readFile(File file, int maxBytes) throws IOException {
        if (!file.isFile()) throw new IOException("file tidak ditemukan: " + file.getName());
        FileInputStream input = new FileInputStream(file);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        int total = 0;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                input.close();
                throw new IOException("file terlalu besar");
            }
            output.write(buffer, 0, read);
        }
        input.close();
        return new String(output.toByteArray(), "UTF-8");
    }

    private static boolean deleteRecursive(File file) {
        if (file == null || !file.exists()) return true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (int i = 0; i < children.length; i++) deleteRecursive(children[i]);
            }
        }
        return file.delete() || !file.exists();
    }

    /** Deletion variant for uninstall: every child and the final directory must disappear. */
    private static boolean deleteRecursiveStrict(File file) {
        if (file == null) return false;
        if (!file.exists()) return true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return false;
            for (int i = 0; i < children.length; i++) {
                if (!deleteRecursiveStrict(children[i])) return false;
            }
        }
        return file.delete() || !file.exists();
    }
}
