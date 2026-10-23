package rh.chromitext.browser;

import android.webkit.WebView;
import java.util.ArrayList;
import java.util.List;

public class BrowserTab {
    public WebView webView;
    public String url = "about:blank";
    public volatile String bridgeUrl = "about:blank";
    public String title = "tab baru";
    public boolean loading = false;
    public volatile boolean extensionReloadPending = false;
    public final List<String> consoleLogs = new ArrayList<String>();
    public final List<String> networkLogs = new ArrayList<String>();

    public synchronized void addConsole(String value) {
        consoleLogs.add(value);
        while (consoleLogs.size() > 300) consoleLogs.remove(0);
    }

    public synchronized void addNetwork(String value) {
        networkLogs.add(value);
        while (networkLogs.size() > 500) networkLogs.remove(0);
    }

    public synchronized String getConsoleText() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < consoleLogs.size(); i++) {
            out.append(consoleLogs.get(i)).append('\n');
        }
        return out.toString();
    }

    public synchronized String getNetworkText() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < networkLogs.size(); i++) {
            out.append(networkLogs.get(i)).append('\n');
        }
        return out.toString();
    }
}
