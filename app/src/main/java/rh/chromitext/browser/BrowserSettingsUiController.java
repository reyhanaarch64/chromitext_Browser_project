package rh.chromitext.browser;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.net.Uri;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import java.util.List;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;

/** Builds native browser/settings/debugger pages; the Activity remains the event and lifecycle coordinator. */
final class BrowserSettingsUiController {
    private final MainActivity host;

    BrowserSettingsUiController(MainActivity activity) {
        this.host = activity;
    }

    void showMainMenu() {
        LinearLayout body = host.openPage(LanguageManager.get("menu.title", "Menu"));
        host.addPageAction(body, 0xf02e,LanguageManager.get("menu.bookmarks", "Bookmark"),LanguageManager.get("description.halaman.yang.disimpan", "Halaman yang disimpan"), new View.OnClickListener() { public void onClick(View v) { host.showBookmarks(); } });
        host.addPageAction(body, 0xf1da,LanguageManager.get("menu.history", "Riwayat Penjelajahan"),LanguageManager.get("description.daftar.halaman.yang.pernah.dibuka", "Daftar halaman yang pernah dibuka"), new View.OnClickListener() { public void onClick(View v) { host.showHistory(); } });
        host.addPageAction(body, 0xf019,LanguageManager.get("menu.downloads", "Unduhan"),LanguageManager.get("description.file.unduhan.dikelola.android", "File unduhan dikelola Android"), new View.OnClickListener() { public void onClick(View v) { host.showDownloadsInfo(); } });
        host.addPageAction(body, 0xf12e,LanguageManager.get("ui.extension", "Extension"),LanguageManager.get("description.aktifkan.atau.nonaktifkan.extension.dengan", "Aktifkan atau nonaktifkan extension dengan cepat"), new View.OnClickListener() { public void onClick(View v) { host.backFromPage(); host.showQuickExtensionPopup(); } });
        host.addPageAction(body, 0xf013,LanguageManager.get("menu.settings", "Pengaturan"),LanguageManager.get("description.preferensi.privasi.keamanan.dan.debugging", "Preferensi, privasi, keamanan, dan debugging"), new View.OnClickListener() { public void onClick(View v) { showSettings(); } });
        host.addPageAction(body, 0xf188,LanguageManager.get("menu.developer_tools", "Developer Tools"),LanguageManager.get("description.console.dan.network.untuk.tab.aktif", "Console dan Network untuk tab aktif"), new View.OnClickListener() { public void onClick(View v) { showDebuggerSettings(); } });
        host.addPageAction(body, 0xf2f1,LanguageManager.get("menu.javascript", "JavaScript"), LanguageManager.format("message.javascript_status", "Status: %1$s", host.javascriptEnabled ? LanguageManager.get("state.enabled", "Aktif") : LanguageManager.get("state.disabled", "Nonaktif")), new View.OnClickListener() { public void onClick(View v) { host.toggleJavascript(); host.replaceCurrentPage(); showMainMenu(); } });
        host.addPageAction(body, 0xf108, LanguageManager.get("menu.desktop_mode", "Mode Desktop") + ("desktop".equals(host.globalUserAgentMode) ? LanguageManager.get("state.active_suffix", " [aktif]") : LanguageManager.get("state.inactive_suffix", " [nonaktif]")),LanguageManager.get("description.ganti.user.agent.ke.chrome.desktop.dan.ses", "Ganti User-Agent ke Chrome desktop dan sesuaikan viewport halaman"), new View.OnClickListener() { public void onClick(View v) { host.toggleDesktopMode(); } });
        host.addPageAction(body, 0xf12a,LanguageManager.get("menu.clear_site_data", "Hapus Data Situs"),LanguageManager.get("description.hapus.cache.cookie.dan.web.storage", "Hapus cache, cookie, dan web storage"), new View.OnClickListener() { public void onClick(View v) { host.clearBrowserData(); } });
        host.addPageAction(body, 0xf05a,LanguageManager.get("menu.about", "Tentang Chromitext"),LanguageManager.get("description.informasi.aplikasi", "Informasi aplikasi"), new View.OnClickListener() { public void onClick(View v) { showAbout(); } });
    }

    void showSettings() {
        LinearLayout body = host.openPage(LanguageManager.get("settings.title", "Pengaturan"));
        host.addPageAction(body, 0xf0ad,LanguageManager.get("settings.preferences", "Preferensi"),LanguageManager.get("description.tampilan.user.agent.global.javascript.dan", "Tampilan, user-agent global, JavaScript, dan halaman awal"), new View.OnClickListener() { public void onClick(View v) { showPreferenceSettings(); } });
        host.addPageAction(body, 0xf1ab, LanguageManager.get("settings.language", "Bahasa Aplikasi"), LanguageManager.get("language.title", "Bahasa Aplikasi") + ": " + currentLanguageName(), new View.OnClickListener() { public void onClick(View v) { host.showLanguageSettings(); } });
        host.addPageAction(body, 0xf21b,LanguageManager.get("settings.privacy", "Privasi"),LanguageManager.get("description.cookie.per.domain.dan.data.situs", "Cookie per domain dan data situs"), new View.OnClickListener() { public void onClick(View v) { showPrivacySettings(); } });
        host.addPageAction(body, 0xf132,LanguageManager.get("settings.security", "Keamanan"),LanguageManager.get("description.validasi.sertifikat.ssl.dan.penghapusan.da", "Validasi sertifikat SSL dan penghapusan data"), new View.OnClickListener() { public void onClick(View v) { showSecuritySettings(); } });
        host.addPageAction(body, 0xf188,LanguageManager.get("settings.debugging", "Debugging"),LanguageManager.get("description.developer.tools.dan.konfigurasi.modul", "Developer Tools dan konfigurasi modul"), new View.OnClickListener() { public void onClick(View v) { showDebuggerSettings(); } });
        host.addPageAction(body, 0xf12e,LanguageManager.get("settings.extensions", "Ekstensi"),LanguageManager.get("description.kelola.extension.dan.izin.akses", "Kelola extension dan izin akses"), new View.OnClickListener() { public void onClick(View v) { host.showExtensionManager(); } });
        host.addPageAction(body, 0xf05a,LanguageManager.get("settings.about", "Tentang"),LanguageManager.get("description.informasi.aplikasi.dan.komponen", "Informasi aplikasi dan komponen"), new View.OnClickListener() { public void onClick(View v) { showAbout(); } });
    }

    String currentLanguageName() {
        java.util.Map<String, String> languages = LanguageManager.getLanguages(host);
        String name = languages.get(LanguageManager.getCode());
        return name == null ? LanguageManager.getCode() : name;
    }

    void showPreferenceSettings() {
        LinearLayout body = host.openPage(LanguageManager.get("page.preferences", "Preferensi"));
        host.addPageAction(body, 0xf1ab,LanguageManager.get("ui.user.agent.global", "User-Agent Global"), LanguageManager.format("settings.global_user_agent_current", "Saat ini: %1$s", userAgentModeLabel()), new View.OnClickListener() { public void onClick(View v) { showUserAgentSettings(); } });
        host.addPageAction(body, 0xf11c,LanguageManager.get("menu.javascript", "JavaScript"), host.javascriptEnabled ? LanguageManager.get("state.enabled", "Aktif") : LanguageManager.get("state.disabled", "Nonaktif"), new View.OnClickListener() { public void onClick(View v) { host.toggleJavascript(); host.replaceCurrentPage(); showPreferenceSettings(); } });
        host.addPageAction(body, 0xf015,LanguageManager.get("ui.halaman.awal", "Halaman Awal"),LanguageManager.get("description.atur.url.yang.dibuka.melalui.tombol.berand", "Atur URL yang dibuka melalui tombol Beranda"), new View.OnClickListener() { public void onClick(View v) { host.editHomepage(); } });
        host.addPageAction(body, 0xf042,LanguageManager.get("ui.tampilan.aplikasi", "Tampilan Aplikasi"),LanguageManager.get("description.tema.posisi.address.bar.dan.ukuran.toolbar", "Tema, posisi address bar, dan ukuran toolbar"), new View.OnClickListener() { public void onClick(View v) { showAppearanceSettings(); } });
        host.addPageNote(body,LanguageManager.get("message.user.agent.berlaku.secara.global.untuk.sem", "User-Agent berlaku secara global untuk semua situs. Penggantian per domain hanya tersedia melalui extension."));
    }

    void showAppearanceSettings() {
        LinearLayout body = host.openPage(LanguageManager.get("page.appearance", "Tampilan Aplikasi"));
        host.addPageAction(body, 0xf042,LanguageManager.get("ui.tema.aplikasi", "Tema Aplikasi"), getThemeLabel(), new View.OnClickListener() { public void onClick(View v) {
            host.showSingleChoiceSetting(LanguageManager.get("settings.theme", "Tema Aplikasi"), "ui_theme", new String[]{"system", "light", "dark"}, new String[]{LanguageManager.get("theme.system", "Ikuti Sistem"), LanguageManager.get("theme.light", "Terang"), LanguageManager.get("theme.dark", "Gelap")}, true);
        }});
        host.addPageAction(body, 0xf0c9,LanguageManager.get("ui.posisi.address.bar", "Posisi Address Bar"), host.searchAtBottom ? LanguageManager.get("position.bottom", "Bawah") : LanguageManager.get("position.top", "Atas"), new View.OnClickListener() { public void onClick(View v) {
            host.showSingleChoiceSetting(LanguageManager.get("settings.address_bar_position", "Posisi Address Bar"), "ui_search_position", new String[]{"top", "bottom"}, new String[]{LanguageManager.get("position.top", "Atas"), LanguageManager.get("position.bottom", "Bawah")}, false);
        }});
        host.addPageAction(body, 0xf03b,LanguageManager.get("ui.tinggi.address.bar", "Tinggi Address Bar"), host.configuredSearchHeight + " dp", new View.OnClickListener() { public void onClick(View v) {
            host.showSingleChoiceSetting(LanguageManager.get("settings.address_bar_height", "Tinggi Address Bar"), "ui_search_height", new String[]{"34", "38", "42", "46", "50", "54"}, new String[]{LanguageManager.get("settings.size_34_compact", "34 dp — sangat ringkas"), LanguageManager.get("settings.size_38_compact", "38 dp — ringkas"), LanguageManager.get("settings.size_42_normal", "42 dp — normal"), "46 dp", "50 dp", LanguageManager.get("settings.size_54_large", "54 dp — besar")}, false);
        }});
        host.addPageAction(body, 0xf150,LanguageManager.get("ui.tinggi.toolbar", "Tinggi Toolbar"), host.configuredToolbarHeight + " dp", new View.OnClickListener() { public void onClick(View v) {
            host.showSingleChoiceSetting(LanguageManager.get("settings.toolbar_height", "Tinggi Toolbar"), "ui_toolbar_height", new String[]{"34", "38", "42", "46", "50", "54"}, new String[]{LanguageManager.get("settings.size_34_compact", "34 dp — sangat ringkas"), LanguageManager.get("settings.size_38_compact", "38 dp — ringkas"), LanguageManager.get("settings.size_42_normal", "42 dp — normal"), "46 dp", "50 dp", LanguageManager.get("settings.size_54_large", "54 dp — besar")}, false);
        }});
        host.addPageAction(body, 0xf00a,LanguageManager.get("ui.ukuran.ikon.toolbar", "Ukuran Ikon Toolbar"), host.configuredIconSize + " sp", new View.OnClickListener() { public void onClick(View v) {
            host.showSingleChoiceSetting(LanguageManager.get("settings.toolbar_icon_size", "Ukuran Ikon Toolbar"), "ui_icon_size", new String[]{"12", "14", "15", "17", "19", "22"}, new String[]{"12 sp", "14 sp", "15 sp", "17 sp", "19 sp", "22 sp"}, false);
        }});
        host.addPageAction(body, 0xf0c9,LanguageManager.get("ui.jarak.antar.tombol", "Jarak Antar Tombol"), host.configuredToolbarGap + " dp", new View.OnClickListener() { public void onClick(View v) {
            host.showSingleChoiceSetting(LanguageManager.get("settings.toolbar_spacing", "Jarak Antar Tombol"), "ui_toolbar_gap", new String[]{"0", "1", "2", "3", "4", "5"}, new String[]{LanguageManager.get("settings.spacing_0_tight", "0 dp — rapat"), "1 dp", "2 dp", "3 dp", "4 dp", LanguageManager.get("settings.spacing_5_wide", "5 dp — renggang")}, false);
        }});
        host.addPageNote(body,LanguageManager.get("message.ukuran.visual.ikon.dapat.lebih.kecil.darip", "Ukuran visual ikon dapat lebih kecil daripada area sentuh tombol untuk menjaga kemudahan penggunaan."));
    }

    String getThemeLabel() {
        String mode = host.database.getSetting("ui_theme", "system");
        if ("dark".equals(mode)) return LanguageManager.get("theme.dark", "Gelap");
        if ("light".equals(mode)) return LanguageManager.get("theme.light", "Terang");
        return LanguageManager.get("theme.system", "Ikuti Sistem");
    }

    String userAgentModeLabel() {
        return UserAgentProfileManager.modeLabel(host.globalUserAgentMode);
    }

    void showUserAgentSettings() {
        LinearLayout body = host.openPage(LanguageManager.get("page.user_agent", "User-Agent Global"));
        host.addPageAction(body, 0xf192,LanguageManager.get("ui.default.android.chrome.mobile", "Default Android / Chrome Mobile"),LanguageManager.get("description.gunakan.user.agent.bawaan.webview", "Gunakan User-Agent bawaan WebView"), new View.OnClickListener() { public void onClick(View v) { host.saveUserAgent("default", ""); } });
        host.addPageAction(body, 0xf108,LanguageManager.get("ui.chrome.desktop", "Chrome desktop"),LanguageManager.get("description.gunakan.identitas.chrome.desktop.di.linux", "Gunakan identitas Chrome desktop di Linux"), new View.OnClickListener() { public void onClick(View v) { host.saveUserAgent("desktop", ""); } });
        host.addPageAction(body, 0xf179,LanguageManager.get("ui.safari.ios", "Safari iOS"),LanguageManager.get("description.gunakan.template.user.agent.safari.mobile", "Gunakan template User-Agent Safari mobile"), new View.OnClickListener() { public void onClick(View v) { host.saveUserAgent("safari", ""); } });
        host.addPageAction(body, 0xf044,LanguageManager.get("ui.custom.user.agent", "Custom User-Agent"),LanguageManager.get("description.masukkan.string.user.agent.kustom", "Masukkan string User-Agent kustom"), new View.OnClickListener() { public void onClick(View v) { host.promptCustomUserAgent(); } });
        host.addPageNote(body,LanguageManager.get("message.mengubah.user.agent.tidak.meniru.seluruh.f", "Mengubah User-Agent tidak meniru seluruh fitur, rendering, atau API browser lain."));
    }

    void showPrivacySettings() {
        LinearLayout body = host.openPage(LanguageManager.get("page.privacy_cookies", "Privasi & Cookie"));
        host.addPageNote(body,LanguageManager.get("message.daftar.origin.berasal.dari.situs.yang.pern", "Daftar origin berasal dari situs yang pernah dibuka. Cookie yang ditampilkan mengikuti data yang disimpan WebView."));
        List<String> origins = host.database.getKnownOrigins();
        if (origins.size() == 0) host.addPageNote(body,LanguageManager.get("message.belum.ada.origin.yang.tercatat", "Belum ada origin yang tercatat."));
        for (int i = 0; i < origins.size(); i++) {
            final String origin = origins.get(i);
            host.addPageAction(body, 0xf1c0, origin,LanguageManager.get("description.lihat.atau.hapus.cookie.dan.web.storage", "Lihat atau hapus cookie dan Web Storage"), new View.OnClickListener() { public void onClick(View v) { showOriginStorage(origin); } });
        }
        host.addPageAction(body, 0xf12d,LanguageManager.get("action.hapus.semua.cookie", "Hapus Semua Cookie"),LanguageManager.get("description.hapus.cookie.dari.seluruh.situs", "Hapus cookie dari seluruh situs"), new View.OnClickListener() { public void onClick(View v) { confirmClearCookies(); } });
        host.addPageAction(body, 0xf2ed,LanguageManager.get("action.hapus.seluruh.web.storage", "Hapus Seluruh Web Storage"),LanguageManager.get("description.hapus.data.web.storage.dari.seluruh.origin", "Hapus data Web Storage dari seluruh origin"), new View.OnClickListener() { public void onClick(View v) { confirmClearWebStorage(); } });
    }

    void showOriginStorage(final String origin) {
        LinearLayout body = host.openPage(LanguageManager.get("page.web.storage.domain", "Web Storage Domain"));
        String cookies = CookieManager.getInstance().getCookie(origin);
        host.addPageNote(body, LanguageManager.format("privacy.origin_cookie_details", "Origin: %1$s\n\nCookie saat ini:\n%2$s", origin, cookies == null ? "(kosong)" : cookies));
        host.addPageAction(body, 0xf2ed,LanguageManager.get("action.hapus.cookie.domain.ini", "Hapus Cookie Domain Ini"),LanguageManager.get("description.penghapusan.bersifat.best.effort.cookie.de", "Penghapusan bersifat best-effort; cookie dengan Path atau Domain khusus mungkin memerlukan penanganan tambahan."), new View.OnClickListener() { public void onClick(View v) { confirmDeleteOriginCookies(origin); } });
        host.addPageAction(body, 0xf2ed,LanguageManager.get("action.hapus.seluruh.storage.domain.ini", "Hapus Seluruh Storage Domain Ini"),LanguageManager.get("description.hapus.data.web.storage.untuk.origin.ini", "Hapus data Web Storage untuk origin ini"), new View.OnClickListener() { public void onClick(View v) {
            new AlertDialog.Builder(host).setTitle(LanguageManager.get("dialog.title.hapus.storage.domain", "Hapus Storage Domain?")).setMessage(LanguageManager.format("privacy.delete_origin_storage_message", "%1$s\n\nlocalStorage dan data WebStorage origin ini akan dihapus.", origin))
                .setPositiveButton(LanguageManager.get("action.delete", "Hapus"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                    android.webkit.WebStorage.getInstance().deleteOrigin(origin);
                    if (host.activeTab != null && origin.equals(host.getOrigin(host.activeTab.url))) host.activeTab.webView.evaluateJavascript("try{localStorage.clear();sessionStorage.clear();}catch(e){}", null);
                    host.showToast(LanguageManager.get("toast.storage.origin.dihapus", "Storage origin dihapus.")); host.replaceCurrentPage(); showOriginStorage(origin);
                }}).setNegativeButton(LanguageManager.get("action.cancel", "Batal"), null).show();
        }});
    }

    void confirmDeleteOriginCookies(final String origin) {
        new AlertDialog.Builder(host).setTitle(LanguageManager.get("dialog.title.hapus.cookie.domain", "Hapus Cookie Domain?")).setMessage(LanguageManager.format("privacy.delete_domain_cookies_message", "Cookie untuk %1$s akan dihapus dari CookieManager.", origin))
            .setPositiveButton(LanguageManager.get("action.delete", "Hapus"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
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
                host.showToast(LanguageManager.get("toast.permintaan.penghapusan.cookie.dikirim", "Permintaan penghapusan cookie dikirim.")); host.replaceCurrentPage(); showOriginStorage(origin);
            }}).setNegativeButton(LanguageManager.get("action.cancel", "Batal"), null).show();
    }

    void confirmClearCookies() {
        new AlertDialog.Builder(host).setTitle(LanguageManager.get("dialog.title.hapus.semua.cookie", "Hapus Semua Cookie?")).setMessage(LanguageManager.get("dialog.message.cookie.seluruh.domain.akan.dihapus", "Cookie seluruh domain akan dihapus."))
            .setPositiveButton(LanguageManager.get("action.delete", "Hapus"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                CookieManager cm = CookieManager.getInstance();
                if (Build.VERSION.SDK_INT >= 21) { cm.removeAllCookies(null); cm.flush(); }
                else { CookieSyncManager.createInstance(host); cm.removeAllCookie(); }
                host.showToast(LanguageManager.get("toast.semua.cookie.dihapus", "Semua cookie dihapus.")); host.replaceCurrentPage(); showPrivacySettings();
            }}).setNegativeButton(LanguageManager.get("action.cancel", "Batal"), null).show();
    }

    void confirmClearWebStorage() {
        new AlertDialog.Builder(host).setTitle(LanguageManager.get("dialog.title.hapus.seluruh.web.storage", "Hapus Seluruh Web Storage?")).setMessage(LanguageManager.get("dialog.message.data.penyimpanan.situs.akan.dihapus", "Data penyimpanan situs akan dihapus."))
            .setPositiveButton(LanguageManager.get("action.delete", "Hapus"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
                android.webkit.WebStorage.getInstance().deleteAllData();
                for (int i = 0; i < host.tabs.size(); i++) host.tabs.get(i).webView.evaluateJavascript("try{localStorage.clear();sessionStorage.clear();}catch(e){}", null);
                host.showToast(LanguageManager.get("toast.web.storage.dihapus", "Web Storage dihapus."));
            }}).setNegativeButton(LanguageManager.get("action.cancel", "Batal"), null).show();
    }

    void showSecuritySettings() {
        LinearLayout body = host.openPage(LanguageManager.get("page.security", "Keamanan"));
        host.addPageAction(body, 0xf12a,LanguageManager.get("action.hapus.data.browser", "Hapus Data Browser"),LanguageManager.get("description.cache.cookie.dan.data.situs", "Cache, cookie, dan data situs"), new View.OnClickListener() { public void onClick(View v) { host.clearBrowserData(); } });
        host.addPageNote(body,LanguageManager.get("message.koneksi.dengan.sertifikat.ssl.yang.tidak.v", "Koneksi dengan sertifikat SSL yang tidak valid selalu dibatalkan. Chromitext tidak menyediakan opsi untuk melewati validasi sertifikat."));
        host.addPageNote(body,LanguageManager.get("message.extension.java.beanshell.dapat.mengakses.k", "Extension Java/BeanShell dapat mengakses kelas yang tersedia di proses aplikasi. Pasang extension hanya dari sumber tepercaya."));
    }

    String debuggerModuleLabel(String module) {
        if ("Console".equals(module)) return LanguageManager.get("debugger.module.console", "Console");
        if ("Network".equals(module)) return LanguageManager.get("debugger.module.network", "Network");
        if ("Storage".equals(module)) return LanguageManager.get("debugger.module.storage", "Storage");
        if ("Source".equals(module)) return LanguageManager.get("debugger.module.source", "Source");
        if ("Elements".equals(module)) return LanguageManager.get("debugger.module.elements", "Elements");
        if ("Info".equals(module)) return LanguageManager.get("debugger.module.info", "Info");
        return module;
    }

    void showDebuggerSettings() {
        LinearLayout body = host.openPage(LanguageManager.get("page.debugging", "Debugging"));
        host.addPageAction(body, 0xf188,LanguageManager.get("menu.developer_tools", "Developer Tools"), host.debuggerEnabled ? LanguageManager.get("debugger.enabled_description", "Aktif — ketuk tombol Developer Tools yang mengambang untuk membuka panel.") : LanguageManager.get("debugger.disabled_description", "Nonaktif — Developer Tools tidak berjalan secara default."), new View.OnClickListener() { public void onClick(View v) {
            host.debuggerEnabled = !host.debuggerEnabled;
            host.database.putBoolean("debugger_enabled", host.debuggerEnabled);
            if (!host.debuggerEnabled) host.hideDebuggerPanel();
            host.refreshDebuggerOverlayVisibility();
            host.syncNetworkDebuggerHooks();
            host.replaceCurrentPage();
            showDebuggerSettings();
        }});
        host.addPageNote(body,LanguageManager.get("message.modul.yang.aktif.menentukan.tab.yang.terse", "Modul yang aktif menentukan tab yang tersedia pada overlay. Modul nonaktif tidak ditampilkan dan tidak mengumpulkan data."));
        final String[] modules = new String[]{"Console", "Network", "Storage", "Source", "Elements", "Info"};
        final int[] icons = new int[]{0xf120, 0xf0ac, 0xf1c0, 0xf121, 0xf1fc, 0xf05a};
        for (int i = 0; i < modules.length; i++) {
            final String module = modules[i];
            host.addPageAction(body, icons[i], debuggerModuleLabel(module), host.database.isDebuggerModuleEnabled(module) ? LanguageManager.get("state.enabled", "Aktif") : LanguageManager.get("state.disabled", "Nonaktif"), new View.OnClickListener() { public void onClick(View v) {
                host.database.setDebuggerModuleEnabled(module, !host.database.isDebuggerModuleEnabled(module));
                if ("Network".equals(module)) host.syncNetworkDebuggerHooks();
                host.rebuildDebuggerPanel(); host.replaceCurrentPage(); showDebuggerSettings();
            }});
        }
        host.addPageAction(body, 0xf12d,LanguageManager.get("action.hapus.log.debugger", "Hapus Log Debugger"),LanguageManager.get("description.hapus.log.console.network.dan.extension", "Hapus log Console, Network, dan extension"), new View.OnClickListener() { public void onClick(View v) {
            for (int j = 0; j < host.tabs.size(); j++) { synchronized (host.tabs.get(j)) { host.tabs.get(j).consoleLogs.clear(); host.tabs.get(j).networkLogs.clear(); } }
            host.database.clearExtensionLogs();
            host.showToast(LanguageManager.get("toast.log.debugger.dihapus", "Log debugger dihapus."));
        }});
        host.addPageAction(body, 0xf188,LanguageManager.get("ui.debugger.extension", "Debugger Extension"),LanguageManager.get("description.lihat.log.error.dan.runtime.extension", "Lihat log error dan runtime extension"), new View.OnClickListener() { public void onClick(View v) { showExtensionDebugger(); } });
    }

    void showExtensionDebugger() { showExtensionDebugger(null); }

    void showExtensionDebugger(final String extensionId) {
        String pageTitle = extensionId == null ? LanguageManager.get("debugger.extension.title", "Debugger Extension") : LanguageManager.format("debugger.extension.log_title", "Log: %1$s", extensionId);
        LinearLayout body = host.openPage(pageTitle);
        final TextView logs = new TextView(host);
        logs.setTextColor(host.COLOR_TEXT);
        logs.setTextSize(12);
        logs.setTypeface(Typeface.MONOSPACE);
        String currentLogs = host.database.getExtensionLogs(extensionId);
        logs.setText(currentLogs.length() == 0 ? extensionLogsEmptyMessage(extensionId) : currentLogs);
        logs.setPadding(host.dp(8), host.dp(8), host.dp(8), host.dp(8));
        logs.setBackground(host.boxBackground(host.darkTheme ? Color.rgb(29, 33, 39) : Color.rgb(247, 245, 239), host.COLOR_BORDER));
        body.addView(logs, new LinearLayout.LayoutParams(-1, -2));
        host.addPageAction(body, 0xf021,LanguageManager.get("action.muat.ulang.log", "Muat Ulang Log"), LanguageManager.get(extensionId == null ? "debugger.refresh_all_description" : "debugger.refresh_one_description", extensionId == null ? "Muat ulang log seluruh extension" : "Muat ulang log milik extension ini"), new View.OnClickListener() { public void onClick(View v) {
            String value = host.database.getExtensionLogs(extensionId);
            logs.setText(value.length() == 0 ? extensionLogsEmptyMessage(extensionId) : value);
        }});
        host.addPageAction(body, 0xf12d,LanguageManager.get("action.hapus.log", "Hapus Log"), extensionId == null ? LanguageManager.get("debugger.clear_all_description", "Hapus log seluruh extension") : LanguageManager.format("debugger.clear_one_description", "Hanya hapus log %1$s", extensionId), new View.OnClickListener() { public void onClick(View v) {
            host.database.clearExtensionLogs(extensionId);
            logs.setText(extensionLogsEmptyMessage(extensionId));
        }});
        host.addPageNote(body,LanguageManager.get("message.log.difilter.berdasarkan.id.extension.saat", "Log difilter berdasarkan ID extension saat layar dibuka dari detail extension. Error BeanShell ditampilkan sesuai catatan runtime yang tersedia."));
    }

    String extensionLogsEmptyMessage(String extensionId) {
        if (extensionId == null) return LanguageManager.get("debugger.logs_empty_all", "Belum ada log extension.");
        return LanguageManager.format("debugger.logs_empty_one", "Belum ada log untuk %1$s.", extensionId);
    }

    void showAbout() {
        LinearLayout body = host.openPage(LanguageManager.get("page.about", "Tentang Chromitext"));
        host.addPageNote(body,LanguageManager.get("message.chromitext.browser.android.berbasis.webvie", "Chromitext\nBrowser Android berbasis WebView.\nPackage: rh.chromitext.browser\nDatabase pengaturan: chromite.db\n\nFitur: multi-tab, bookmark, history, download Android, global user-agent, cookie/storage manager, debugger overlay opsional, debugger extension, dan extension ZIP.\n\nExtension Java menggunakan BeanShell opsional. Tambahkan bsh-2.0b4.jar ke folder libs/ di project AIDE lalu rebuild. Permission internet dan Android API dipakai sebagai pemeriksaan source-level umum, bukan sandbox yang tahan bypass; instal hanya extension tepercaya."));
    }
}
