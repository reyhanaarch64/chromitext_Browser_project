package rh.chromitext.browser;

import android.webkit.JavascriptInterface;

public class NativeBridge {
    private final MainActivity activity;
    private final BrowserTab tab;

    public NativeBridge(MainActivity activity, BrowserTab tab) {
        this.activity = activity;
        this.tab = tab;
    }

    @JavascriptInterface
    public String invoke(String method, String jsonArguments) {
        String url = tab.bridgeUrl;
        return activity.dispatchBridge(url, method, jsonArguments);
    }
}
