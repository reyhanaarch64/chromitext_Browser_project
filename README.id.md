# Chromitext

[English](README.md) | **Bahasa Indonesia**

Browser Android berbasis `WebView` (package `rh.chromitext.browser`). Kodenya ditulis dengan gaya Java 7: listener memakai anonymous inner class, tanpa lambda, tanpa AndroidX, dan tanpa library UI tambahan. Extension Java berjalan di atas BeanShell (`app/libs/bsh-2.0b4.jar`).

## Fitur

- Multi-tab, bookmark, riwayat, dan toggle mode desktop
- Address bar dengan saran riwayat dan saran Google
- Developer Tools halaman bawaan (Console, Network, Storage, Source, Elements, Info)
- Sistem extension: content script JavaScript, extension Java (BeanShell), sistem permission, dan log per extension
- Tema UI ikuti sistem, terang, atau gelap, dengan toolbar dan address bar yang bisa diatur

## Buka di AIDE

1. Clone repository ini, atau unduh sebagai ZIP lalu ekstrak.
2. Buka/import folder project sebagai project Android di AIDE.
3. Source Java ada di `app/src/main/java/rh/chromitext/browser/`.
4. Project memakai `target=android-28` dan mendukung Android 5.0 (API 21) ke atas.
5. Build di AIDE, lalu uji di perangkat.

## File source utama

- `MainActivity.java`: navigasi, multi-tab, dialog JavaScript, file chooser, fullscreen, izin website, dan integrasi extension.
- `BrowserDatabase.java`: database SQLite internal `chromite.db`.
- `BrowserPageLayout.java`: layout halaman internal dan baris aksi.
- `BrowserDebuggerOverlay.java`: floating button, panel Developer Tools, viewer console/network, fitur salin, dan pengubah ukuran panel.
- `ExtensionManager.java`: instalasi ZIP, validasi arsip/manifest, serta pengelolaan extension dan permission.
- `BrowserTab.java`: state tiap tab dan buffer Console/Network.
- `BrowserExtensionApi.java`, `NativeBridge.java`, `LocalHttpServer.java`: API native extension dan server loopback.
- `ExtensionInfo.java`, `ExtensionHost.java`, dan interface `Extension*Handler`: model data dan callback extension.
- `UrlResolver.java`, `FontAwesome.java`: helper pengolah URL dan icon font (font ada di `assets/fonts/`).

## Icon aplikasi

Manifest memakai `@mipmap/ic_launcher` dan `@mipmap/ic_launcher_round`. PNG launcher icon tersedia untuk density mdpi sampai xxxhdpi, jadi launcher tidak bergantung pada satu drawable vector saja.

## UI browser

- Title bar aplikasi di layar browsing dihilangkan supaya WebView memakai seluruh ruang.
- Toolbar menyediakan navigasi, reload, Beranda, Bookmark, tab, dan Menu. Menu hamburger punya toggle **Mode Desktop** yang mengubah User-Agent ke Chrome desktop dan mengaktifkan viewport lebar/overview pada WebView.
- Address bar punya tombol **X** untuk mengosongkan input tanpa menutup keyboard, daftar saran native, dan saran Google yang diambil secara asynchronous dengan debounce. Riwayat yang cocok tampil di bagian atas berupa judul + URL berwarna biru; saat input kosong, riwayat terbaru bisa ditampilkan.
- Menu hamburger punya popup **Extension** berisi daftar extension terpasang beserta switch aktif/nonaktif. Perubahan disimpan ke `chromite.db`, runtime extension dibangun ulang, dan tab aktif dimuat ulang sehingga langsung berlaku tanpa perlu masuk ke Pengaturan.
- Halaman Menu, Bookmark, Riwayat Penjelajahan, daftar tab, Pengelola Ekstensi, Pengaturan, Privasi, Keamanan, Debugging, dan Tentang masing-masing memakai layar internal terpisah.
- Pemilih file untuk `<input type="file">` memakai `WebChromeClient.FileChooserParams.createIntent()` dan `parseResult()`, sehingga pemilihan file ditangani Android.
- Dialog JavaScript `alert`, `confirm`, dan `prompt`, popup/pembuatan window, fullscreen web, geolocation, permintaan kamera/mikrofon, dan unduhan ditangani oleh aplikasi.
- Tombol Back keluar dari fullscreen terlebih dahulu.

## Pengaturan dan database

Pengaturan disimpan di database SQLite internal `chromite.db`. SharedPreferences lama hanya dibaca satu kali untuk migrasi. Saran riwayat memakai tabel `history` yang sudah ada. Saran Google memakai endpoint autocomplete Google lewat HTTPS dan gagal secara senyap jika offline atau endpoint tidak tersedia. Database berisi tabel `settings`, `bookmarks`, `history`, `debugger_modules`, `extension_logs`, dan `site_origins`.

Pengaturan tampilan yang disimpan di database:

- Tema UI: Ikuti Sistem, Terang, atau Gelap. Tema ini hanya berlaku untuk UI native Chromitext dan tidak memaksa website menjadi gelap.
- Posisi address bar: atas atau bawah.
- Tinggi address bar dan toolbar, ukuran ikon, serta jarak antar tombol.
- User-Agent global untuk semua situs: default WebView/Android, Chrome desktop, Safari iOS, atau custom. Tidak ada User-Agent per situs di Pengaturan; pengecualian per domain tetap menjadi fitur extension.
- Status Developer Tools dan status tiap modul.
- Snapshot URL tab dan indeks tab aktif.

Pengaturan UI diterapkan dengan membangun ulang komponen native tanpa memaksa WebView reload. Jika proses aplikasi dihentikan, snapshot sesi hanya memulihkan URL dan tab aktif, bukan state JavaScript, isi form, posisi scroll, media, atau seluruh state halaman.

## Developer Tools halaman

Developer Tools nonaktif secara default. Aktifkan lewat Pengaturan → Debugging. Floating button bisa digeser, tinggi panel bisa diubah, dan setiap modul bisa dinonaktifkan sendiri-sendiri:

- **Console**: level log ditandai dengan warna; setiap entri bisa dipilih dan disalin.
- **Network**: menampilkan daftar request beserta method, domain/path, status, durasi, header request/response, dan body jika berhasil ditangkap oleh hook `fetch`/XHR. Detail request bisa dibuka dan disalin.
- **Storage**: cookie dari `CookieManager`, serta localStorage/sessionStorage halaman aktif.
- **Source**: `outerHTML` DOM saat diminta. Ini bukan debugger source lengkap dengan breakpoint.
- **Elements**: ringkasan DOM dasar.
- **Info**: title, URL, status loading, dan indeks tab.

Network bukan proxy tingkat paket. Request yang terjadi sebelum hook `fetch`/XHR terpasang bisa terlewat, dan kebijakan CORS serta WebView membatasi header yang bisa dibaca. Request resource yang diamati lewat `shouldInterceptRequest` tidak selalu memberikan status dan response body akhir. Ukuran body yang ditampilkan dibatasi supaya log tidak menghabiskan memori.

## Debugger extension

Halaman Debugger Extension global menampilkan log semua extension. Dari halaman detail sebuah extension, **Lihat Log Extension** menampilkan dan menghapus log khusus untuk ID extension itu saja. Log disimpan di `chromite.db` dengan batas total 500 entri, dan bisa dimuat ulang atau dihapus. Stack trace bergantung pada informasi yang disediakan BeanShell/runtime.

Ekstraksi ZIP, pemasangan/penghapusan extension, dan pemuatan ulang runtime extension dijalankan di worker thread, sehingga perubahan permission atau status extension tidak memblokir UI. Setelah runtime berubah, hanya tab aktif yang langsung dimuat ulang; tab lain ditandai dan dimuat ulang saat dipilih. Sesi menyimpan URL dan indeks tab; WebView dan JavaScript mengikuti lifecycle Activity.

## Extension

- Installer menerima file ZIP dan mengekstraknya ke direktori privat `files/extensions/<extension-id>/`.
- Ekstraksi memeriksa path arsip, jumlah entri, dan batas ukuran.
- `type: "javascript"` berjalan sebagai content script pada halaman yang cocok.
- `type: "java"` memakai BeanShell secara reflektif, sehingga source tidak perlu mengimpor BeanShell agar bisa dikompilasi.
- Status aktif dan permission extension disimpan di `chromite.db`; berkas extension berada di direktori privat aplikasi.
- Penghapusan extension menghapus folder yang benar-benar ditemukan lewat `manifest.json` (termasuk folder legacy yang namanya berbeda dari ID), membersihkan permission/status lama, dan menghapus seluruh log extension tersebut. Hook runtime serta server lokal milik extension ikut dilepas.
- Domain lock dan permission tetap berlaku. Extension Java bisa mendeklarasikan `internet`, `androidApi`, `fileAccess`, `reflection`, dan `processExecution` bila perlu. Tanpa grant, runtime memeriksa source untuk pola penggunaan terkait dan class loader menolak sejumlah jalur pemuatan kelas yang sensitif.
- Contoh di `manifest.json`: `"permissions": ["userAgent", "internet", "androidApi"]`. Semua permission nonaktif sampai pengguna menyetujuinya di dialog instalasi atau halaman Kelola Permission.
- **Penting:** BeanShell tetap berjalan di dalam proses aplikasi. Pemindaian source dan class-loader guard meningkatkan kontrol atas akses langsung yang umum, tetapi bukan sandbox keamanan yang tahan bypass. Permission `reflection` khususnya harus dianggap akses tingkat tinggi, karena reflection bisa dipakai untuk melewati pembatasan lain. Jangan memasang extension Java yang tidak dipercaya; isolasi per-extension yang benar membutuhkan proses/UID terpisah atau runtime sandbox yang dirancang khusus.

Saat membuat ZIP extension, pastikan `manifest.json` berada langsung di root arsip.

## Batasan keamanan

1. `ChromitextBridge` memeriksa origin, domain lock, status extension, dan permission `nativeBridge`.
2. Error SSL dibatalkan, bukan diabaikan. Akses file WebView dari konten website dinonaktifkan.
3. Penghapusan cookie per domain bersifat best-effort, karena cookie bisa memakai Path/Domain khusus.
4. Permission Android tetap menentukan akses perangkat, meskipun website sudah meminta izin lewat WebView.
