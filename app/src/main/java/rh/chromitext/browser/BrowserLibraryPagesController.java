package rh.chromitext.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Owns bookmark, history, and download-information pages. */
public final class BrowserLibraryPagesController {
    public interface Host {
        Activity libraryActivity();
        BrowserDatabase libraryDatabase();
        BrowserTab libraryActiveTab();
        LinearLayout openLibraryPage(String title);
        void addLibraryAction(LinearLayout body, int iconCode, String title, String description, View.OnClickListener listener);
        void addLibraryNote(LinearLayout body, String value);
        void navigateFromLibrary(String url);
        void backFromLibraryPage();
        void replaceLibraryPage();
        void showBookmarksPage();
        void showHistoryPage();
        void showLibraryToast(String message);
    }

    private final Host host;

    public BrowserLibraryPagesController(Host host) { this.host = host; }

    public void showBookmarks() {
        final BrowserDatabase database = host.libraryDatabase();
        final List<String> entries = new ArrayList<String>(database.getLines("bookmarks"));
        LinearLayout body = host.openLibraryPage(LanguageManager.get("page.bookmarks", "Bookmark"));
        BrowserTab active = host.libraryActiveTab();
        host.addLibraryAction(body, 0xf067, LanguageManager.get("action.simpan.halaman.saat.ini", "Simpan Halaman Saat Ini"),
            active == null ? LanguageManager.get("tabs.no_active_tab", "Tidak ada tab aktif.") : active.url,
            new View.OnClickListener() { public void onClick(View view) {
                addCurrentBookmark();
                host.replaceLibraryPage();
                host.showBookmarksPage();
            }});
        if (entries.size() == 0) host.addLibraryNote(body, LanguageManager.get("message.belum.ada.bookmark", "Belum ada bookmark."));
        for (int i = entries.size() - 1; i >= 0; i--) {
            final String[] pair = entries.get(i).split("\t", 2);
            if (pair.length != 2) continue;
            host.addLibraryAction(body, 0xf02e, pair[0], pair[1], new View.OnClickListener() { public void onClick(View view) {
                host.navigateFromLibrary(pair[1]);
                host.backFromLibraryPage();
            }});
            host.addLibraryAction(body, 0xf2ed, LanguageManager.get("action.hapus.bookmark", "Hapus Bookmark"), pair[0],
                new View.OnClickListener() { public void onClick(View view) {
                    database.deleteBookmark(pair[1]);
                    host.replaceLibraryPage();
                    host.showBookmarksPage();
                }});
        }
    }

    public void addCurrentBookmark() {
        BrowserTab active = host.libraryActiveTab();
        if (active == null || !UrlResolver.isWebUrl(active.url)) {
            host.showLibraryToast(LanguageManager.get("toast.halaman.ini.tidak.dapat.disimpan.sebagai.b", "Halaman ini tidak dapat disimpan sebagai bookmark."));
            return;
        }
        host.libraryDatabase().addBookmark(active.title, active.url);
        host.showLibraryToast(LanguageManager.get("toast.bookmark.disimpan", "Bookmark disimpan."));
    }

    public void showHistory() {
        final BrowserDatabase database = host.libraryDatabase();
        final ArrayList<String> entries = new ArrayList<String>(database.getLines("history"));
        Collections.reverse(entries);
        LinearLayout body = host.openLibraryPage(LanguageManager.get("page.history", "Riwayat Penjelajahan"));
        host.addLibraryAction(body, 0xf2ed, LanguageManager.get("action.hapus.seluruh.riwayat", "Hapus Seluruh Riwayat"),
            LanguageManager.get("description.hapus.riwayat.penjelajahan", "Hapus riwayat penjelajahan"), new View.OnClickListener() { public void onClick(View view) {
                new AlertDialog.Builder(host.libraryActivity())
                    .setTitle(LanguageManager.get("dialog.title.hapus.riwayat.penjelajahan", "Hapus Riwayat Penjelajahan?"))
                    .setMessage(LanguageManager.get("dialog.message.seluruh.riwayat.penjelajahan.akan.dihapus", "Seluruh riwayat penjelajahan akan dihapus."))
                    .setPositiveButton(LanguageManager.get("action.delete", "Hapus"), new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int which) {
                        database.clearHistory();
                        host.replaceLibraryPage();
                        host.showHistoryPage();
                    }}).setNegativeButton(LanguageManager.get("action.cancel", "Batal"), null).show();
            }});
        if (entries.size() == 0) host.addLibraryNote(body, LanguageManager.get("message.riwayat.penjelajahan.masih.kosong", "Riwayat penjelajahan masih kosong."));
        for (int i = 0; i < entries.size(); i++) {
            final String[] pair = entries.get(i).split("\t", 2);
            if (pair.length != 2) continue;
            host.addLibraryAction(body, 0xf1da, pair[0], pair[1], new View.OnClickListener() { public void onClick(View view) {
                host.navigateFromLibrary(pair[1]);
                host.backFromLibraryPage();
            }});
            host.addLibraryAction(body, 0xf2ed, LanguageManager.get("action.hapus.entri", "Hapus Entri"), pair[1], new View.OnClickListener() { public void onClick(View view) {
                database.deleteHistoryUrl(pair[1]);
                host.replaceLibraryPage();
                host.showHistoryPage();
            }});
        }
    }

    public void showDownloadsInfo() {
        LinearLayout body = host.openLibraryPage(LanguageManager.get("page.downloads", "Unduhan"));
        host.addLibraryNote(body, LanguageManager.get("message.unduhan.dikelola.oleh.download.manager.and", "Unduhan dikelola oleh Download Manager Android. Buka aplikasi Downloads/Files untuk melihat file yang diunduh."));
        host.addLibraryAction(body, 0xf019, LanguageManager.get("action.buka.unduhan", "Buka Unduhan"),
            LanguageManager.get("description.buka.aplikasi.pengelola.unduhan.android", "Buka aplikasi pengelola unduhan Android"), new View.OnClickListener() { public void onClick(View view) {
                try { host.libraryActivity().startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)); }
                catch (Exception error) { host.showLibraryToast(LanguageManager.get("toast.aplikasi.pengelola.unduhan.tidak.tersedia", "Aplikasi pengelola unduhan tidak tersedia.")); }
            }});
    }
}
