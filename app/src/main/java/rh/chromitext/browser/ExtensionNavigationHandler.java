package rh.chromitext.browser;

public interface ExtensionNavigationHandler {
    void onNavigation(String fromUrl, String toUrl, boolean willBeBlocked);
}
