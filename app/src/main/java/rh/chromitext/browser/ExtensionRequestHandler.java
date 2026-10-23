package rh.chromitext.browser;

public interface ExtensionRequestHandler {
    void onRequest(String pageUrl, String requestUrl, String method, String headers, boolean willBeBlocked);
}
