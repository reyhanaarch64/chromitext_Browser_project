# Chromitext

**English** | [Bahasa Indonesia](README.id.md)

An Android browser built on `WebView` (package `rh.chromitext.browser`). The code follows a Java 7 style: listeners are anonymous inner classes, with no lambdas, no AndroidX and no extra UI libraries. Java extensions run on BeanShell (`app/libs/bsh-2.0b4.jar`).

## Features

- Multi-tab browsing, bookmarks, history and a desktop mode toggle
- Address bar with history and Google suggestions
- Built-in page Developer Tools (Console, Network, Storage, Source, Elements, Info)
- Extension system with JavaScript content scripts, Java (BeanShell) extensions, a permission system and per-extension logs
- System, light or dark UI theme, with a configurable toolbar and address bar

## Open in AIDE

1. Clone this repository, or download it as a ZIP and extract it.
2. Open/import the project folder as an Android project in AIDE.
3. Java sources are in `app/src/main/java/rh/chromitext/browser/`.
4. The project uses `target=android-28` and supports Android 5.0 (API 21) and above.
5. Build in AIDE, then test on a device.

## Main source files

- `MainActivity.java`: navigation, multi-tab, JavaScript dialogs, file chooser, fullscreen, website permissions and extension integration.
- `BrowserDatabase.java`: internal SQLite database `chromite.db`.
- `BrowserPageLayout.java`: layout for internal pages and action rows.
- `BrowserDebuggerOverlay.java`: floating button, Developer Tools panel, console/network viewer, copy and panel resizing.
- `ExtensionManager.java`: ZIP installation, archive/manifest validation, extension and permission management.
- `BrowserTab.java`: per-tab state and the Console/Network buffers.
- `BrowserExtensionApi.java`, `NativeBridge.java`, `LocalHttpServer.java`: native extension API and loopback server.
- `ExtensionInfo.java`, `ExtensionHost.java` and the `Extension*Handler` interfaces: extension data model and callbacks.
- `UrlResolver.java`, `FontAwesome.java`: URL resolution and icon font helpers (fonts are in `assets/fonts/`).

## App icon

The manifest uses `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`. PNG launcher icons are provided for mdpi through xxxhdpi, so the launcher does not depend on a single vector drawable.

## Browser UI

- The app title bar is removed on the browsing screen so the WebView gets the full space.
- The toolbar provides navigation, reload, Home, Bookmarks, tabs and Menu. The hamburger menu has a **Desktop Mode** toggle that switches the User-Agent to desktop Chrome and enables the wide viewport/overview mode of the WebView.
- The address bar has an **X** button that clears the input without closing the keyboard, a native suggestion list, and Google suggestions fetched asynchronously with debounce. Matching history entries appear at the top as a title plus a blue URL; when the input is empty, recent history can be shown.
- The hamburger menu has an **Extension** popup listing installed extensions with an on/off switch. A change is saved to `chromite.db`, the extension runtime is rebuilt and the active tab is reloaded, so it takes effect without opening Settings.
- Menu, Bookmarks, Browsing History, tab list, Extension Manager, Settings, Privacy, Security, Debugging and About each use a separate internal screen.
- The file picker for `<input type="file">` uses `WebChromeClient.FileChooserParams.createIntent()` and `parseResult()`, so Android handles file selection.
- JavaScript `alert`, `confirm` and `prompt` dialogs, popups/window creation, web fullscreen, geolocation, camera/microphone requests and downloads are handled by the app.
- The Back button exits fullscreen first.

## Settings and database

Settings are stored in the internal SQLite database `chromite.db`. The old SharedPreferences are only read once for migration. History suggestions use the existing `history` table. Google suggestions use the Google autocomplete endpoint over HTTPS and fail silently when offline or when the endpoint is unavailable. The database contains the tables `settings`, `bookmarks`, `history`, `debugger_modules`, `extension_logs` and `site_origins`.

Appearance settings stored in the database:

- UI theme: System, Light or Dark. It only applies to the native Chromitext UI and does not force websites into dark mode.
- Address bar position: top or bottom.
- Address bar and toolbar height, icon size and button spacing.
- Global User-Agent for all sites: default WebView/Android, desktop Chrome, iOS Safari or custom. There is no per-site User-Agent in Settings; per-domain exceptions remain an extension feature.
- Developer Tools status and each module's status.
- Tab URL snapshot and the active tab index.

UI settings are applied by rebuilding the native components without forcing a WebView reload. If the app process is killed, the session snapshot only restores the URLs and the active tab, not JavaScript state, form contents, scroll position, media or the full page state.

## Page Developer Tools

Developer Tools are off by default. Enable them in Settings → Debugging. The floating button can be dragged, the panel height can be changed, and each module can be disabled separately:

- **Console**: log levels are color-coded; each entry can be selected and copied.
- **Network**: lists requests with method, domain/path, status, duration, request/response headers and the body when the `fetch`/XHR hook can capture it. Request details can be opened and copied.
- **Storage**: cookies from `CookieManager`, plus localStorage/sessionStorage of the active page.
- **Source**: the DOM `outerHTML` on demand. It is not a full source debugger with breakpoints.
- **Elements**: a basic DOM summary.
- **Info**: title, URL, loading status and tab index.

Network is not a packet-level proxy. Requests made before the `fetch`/XHR hook is installed may be missed, and CORS and WebView policies limit which headers can be read. Resource requests observed through `shouldInterceptRequest` do not always give the final status and response body. Displayed bodies are size-limited to avoid exhausting memory.

## Extension debugger

The global Extension Debugger page shows the logs of all extensions. From an extension's detail page, **View Extension Log** shows and clears the logs of that extension ID only. Logs are stored in `chromite.db` with a limit of 500 entries in total, and can be reloaded or cleared. Stack traces depend on the information provided by BeanShell/the runtime.

ZIP extraction, extension install/removal and extension runtime reloads run on a worker thread, so permission or status changes do not block the UI. After the runtime changes, only the active tab is reloaded immediately; other tabs are marked and reloaded when selected. The session stores the URLs and the tab index; the WebView and JavaScript follow the Activity lifecycle.

## Extensions

- The installer accepts ZIP files and extracts them to the private directory `files/extensions/<extension-id>/`.
- Extraction checks archive paths, the number of entries and size limits.
- `type: "javascript"` runs as a content script on matching pages.
- `type: "java"` uses BeanShell through reflection, so the source does not need to import BeanShell to compile.
- Extension status and permissions are stored in `chromite.db`; extension files live in the app's private directory.
- Removing an extension deletes the folder actually found through `manifest.json` (including legacy folders whose name differs from the ID), clears its old permissions/status and deletes all of its logs. Runtime hooks and local servers owned by the extension are released too.
- Domain lock and permissions still apply. A Java extension can declare `internet`, `androidApi`, `fileAccess`, `reflection` and `processExecution` when needed. Without a grant, the runtime scans the source for related usage patterns and the class loader rejects several sensitive class-loading paths.
- Example in `manifest.json`: `"permissions": ["userAgent", "internet", "androidApi"]`. All permissions stay inactive until the user approves them in the install dialog or the Manage Permissions page.
- **Important:** BeanShell still runs inside the app process. Source scanning and the class-loader guard improve control over common direct access, but they are not a bypass-proof security sandbox. The `reflection` permission in particular must be treated as high-level access, because reflection can be used to get around other restrictions. Do not install Java extensions you do not trust; real per-extension isolation needs a separate process/UID or a purpose-built sandbox runtime.

When building an extension ZIP, make sure `manifest.json` is directly at the root of the archive.

## Security limits

1. `ChromitextBridge` checks the origin, domain lock, extension status and the `nativeBridge` permission.
2. SSL errors are cancelled, not ignored. WebView file access from website content is disabled.
3. Deleting cookies per domain is best-effort, because cookies can use a specific Path/Domain.
4. Android permissions still decide device access, even after a website has asked for permission through the WebView.
