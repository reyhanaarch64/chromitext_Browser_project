package rh.chromitext.browser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class BrowserExtensionApi {
    private final ExtensionInfo info;
    private final ExtensionManager manager;
    private final ExtensionHost host;

    public BrowserExtensionApi(ExtensionInfo info, ExtensionManager manager, ExtensionHost host) {
        this.info = info;
        this.manager = manager;
        this.host = host;
    }

    public void log(String message) {
        host.extensionLog(info.id, String.valueOf(message));
    }

    public void print(String message) {
        log(message);
    }

    public boolean hasPermission(String permission) {
        return manager.hasPermission(info, permission);
    }

    public void requirePermission(String permission) {
        if (!manager.hasPermission(info, permission)) {
            throw new SecurityException("permission belum diberikan: " + permission);
        }
    }

    public String getExtensionId() { return info.id; }
    public String getName() { return info.name; }
    public String getVersion() { return info.version; }

    public File getExtensionDir() {
        return manager.getExtensionDirectory(info.id);
    }

    public String readAsset(String relativePath) {
        FileInputStream input = null;
        try {
            File assets = new File(getExtensionDir(), "assets").getCanonicalFile();
            File file = resolveInside(assets, relativePath);
            if (!file.isFile()) throw new IOException("asset tidak ditemukan");
            input = new FileInputStream(file);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            int total = 0;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > 5 * 1024 * 1024) {
                    throw new IOException("asset terlalu besar untuk dibaca sekaligus");
                }
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), "UTF-8");
        } catch (IOException e) {
            log("gagal membaca asset '" + relativePath + "': " + e.getMessage());
            return null;
        } finally {
            if (input != null) {
                try { input.close(); } catch (IOException ignored) { }
            }
        }
    }

    public void onRequest(String domain, ExtensionRequestHandler handler) {
        requirePermission("webRequest");
        checkDomainText(domain);
        if (handler == null) throw new IllegalArgumentException("request handler kosong");
        host.addRequestObserver(info, domain.trim(), handler);
        log("request observer terdaftar: " + domain);
    }

    public void onNavigation(String domain, ExtensionNavigationHandler handler) {
        requirePermission("webNavigation");
        checkDomainText(domain);
        if (handler == null) throw new IllegalArgumentException("navigation handler kosong");
        host.addNavigationObserver(info, domain.trim(), handler);
        log("navigation observer terdaftar: " + domain);
    }

    public void blockDomain(String domain) {
        requirePermission("webRequestBlocking");
        checkDomainText(domain);
        host.addRequestBlockRule(info, domain.trim());
        log("request block rule ditambahkan: " + domain);
    }

    public void blockNavigationDomain(String domain) {
        requirePermission("navigationBlocking");
        checkDomainText(domain);
        host.addNavigationBlockRule(info, domain.trim());
        log("navigation block rule ditambahkan: " + domain);
    }

    /** Blocks top-level HTTP(S) navigation away from the current hostname.
     * The current hostname and its subdomains remain allowed.
     */
    public void blockCrossDomainNavigation() {
        requirePermission("navigationBlocking");
        host.addCrossDomainNavigationBlockRule(info);
        log("proteksi lintas-domain aktif: hostname saat ini dan subdomainnya diizinkan");
    }

    public void setUserAgentForDomain(String domain, String userAgent) {
        requirePermission("userAgent");
        checkDomainText(domain);
        if (userAgent == null || userAgent.length() == 0 || userAgent.length() > 1024) {
            throw new IllegalArgumentException("user-agent kosong atau terlalu panjang");
        }
        host.addUserAgentRule(info, domain.trim(), userAgent);
        log("user-agent rule ditambahkan: " + domain);
    }

    public void injectScriptForDomain(String domain, String relativeScriptPath) throws IOException {
        requirePermission("scripting");
        checkDomainText(domain);
        String path = validateRelativePath(relativeScriptPath);
        if (!path.startsWith("script/javascript/") || !path.toLowerCase(java.util.Locale.US).endsWith(".js")) throw new IllegalArgumentException("script injeksi harus berada di script/javascript/ dan berakhiran .js");
        File script = resolveInside(getExtensionDir(), path);
        if (!script.isFile()) throw new IllegalArgumentException("script tidak ditemukan: " + path);
        host.addScriptRule(info, domain.trim(), path);
        log("script injection rule ditambahkan: " + domain);
    }

    public void registerBridgeAction(String method, String allowedOrigin, ExtensionBridgeHandler handler) {
        requirePermission("nativeBridge");
        if (method == null || !method.matches("[A-Za-z][A-Za-z0-9._-]{0,79}")) {
            throw new IllegalArgumentException("nama bridge tidak valid");
        }
        if (allowedOrigin == null || !isHttpOrigin(allowedOrigin)) {
            throw new IllegalArgumentException("origin bridge harus berupa origin http/https yang valid");
        }
        if (handler == null) throw new IllegalArgumentException("bridge handler kosong");
        if (info.domainLockEnabled) {
            boolean allowed = false;
            for (int i = 0; i < info.domains.size(); i++) {
                if (ExtensionManager.matchesDomain(allowedOrigin, info.domains.get(i))) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) throw new SecurityException("origin bridge berada di luar domain lock extension");
        }
        host.registerBridgeAction(info, method, allowedOrigin, handler);
        log("native bridge terdaftar: " + method + " untuk " + allowedOrigin);
    }

    public void startLocalServer(int port, String directory) throws Exception {
        requirePermission("localServer");
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("port harus di antara 1024 sampai 65535");
        if (directory == null) throw new IllegalArgumentException("direktori server kosong");
        File dir = new File(directory).getCanonicalFile();
        if (!dir.isDirectory() || !dir.canRead()) throw new IOException("direktori server tidak bisa dibaca");
        host.startLocalServer(info, port, dir.getAbsolutePath());
        log("server lokal aktif di http://127.0.0.1:" + port + "/");
    }

    public void stopLocalServer(int port) {
        requirePermission("localServer");
        host.stopLocalServer(info, port);
        log("permintaan stop server pada port " + port);
    }

    private static void checkDomainText(String domain) {
        if (domain == null || domain.trim().length() == 0 || domain.length() > 2048) {
            throw new IllegalArgumentException("domain kosong atau tidak valid");
        }
        if (domain.indexOf('\n') >= 0 || domain.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("domain tidak valid");
        }
    }

    private static boolean isHttpOrigin(String value) {
        android.net.Uri uri = android.net.Uri.parse(value);
        String scheme = uri.getScheme();
        if (uri.getHost() == null || scheme == null) return false;
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return false;
        return uri.getUserInfo() == null;
    }

    private static String validateRelativePath(String path) {
        if (path == null) throw new IllegalArgumentException("path kosong");
        String value = path.trim().replace('\\', '/');
        if (value.length() == 0 || value.startsWith("/") || value.indexOf(':') >= 0) throw new IllegalArgumentException("path tidak valid");
        String[] parts = value.split("/");
        for (int i = 0; i < parts.length; i++) {
            if ("..".equals(parts[i]) || ".".equals(parts[i]) || parts[i].length() == 0) throw new IllegalArgumentException("path traversal ditolak");
        }
        return value;
    }

    private static File resolveInside(File base, String relative) throws IOException {
        String path = validateRelativePath(relative);
        File file = new File(base, path).getCanonicalFile();
        String prefix = base.getCanonicalPath() + File.separator;
        if (!file.getPath().startsWith(prefix)) throw new IOException("path keluar dari direktori extension");
        return file;
    }
}
