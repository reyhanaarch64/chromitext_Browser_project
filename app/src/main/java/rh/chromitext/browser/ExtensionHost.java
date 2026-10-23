package rh.chromitext.browser;

public interface ExtensionHost {
    void extensionLog(String extensionId, String message);
    void addRequestBlockRule(ExtensionInfo info, String domain);
    void addRequestObserver(ExtensionInfo info, String domain, ExtensionRequestHandler handler);
    void addNavigationObserver(ExtensionInfo info, String domain, ExtensionNavigationHandler handler);
    void addNavigationBlockRule(ExtensionInfo info, String domain);
    void addCrossDomainNavigationBlockRule(ExtensionInfo info);
    void addUserAgentRule(ExtensionInfo info, String domain, String userAgent);
    void addScriptRule(ExtensionInfo info, String domain, String relativeScriptPath);
    void registerBridgeAction(ExtensionInfo info, String method, String allowedOrigin, ExtensionBridgeHandler handler);
    void startLocalServer(ExtensionInfo info, int port, String directory) throws Exception;
    void stopLocalServer(ExtensionInfo info, int port);
}
