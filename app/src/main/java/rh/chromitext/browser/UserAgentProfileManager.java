package rh.chromitext.browser;

import android.content.Context;
import android.webkit.WebSettings;

/** Builds the global User-Agent profiles offered by Chromitext. */
public final class UserAgentProfileManager {
    private UserAgentProfileManager() { }

    public static String getGlobalUserAgent(Context context, String mode, String customUserAgent) {
        if ("desktop".equals(mode)) {
            String current = WebSettings.getDefaultUserAgent(context);
            String version = "120.0.0.0";
            int chrome = current.indexOf("Chrome/");
            if (chrome >= 0) {
                int begin = chrome + "Chrome/".length();
                int end = current.indexOf(' ', begin);
                if (end < 0) end = current.length();
                String found = current.substring(begin, end);
                if (found.matches("[0-9.]+")) version = found;
            }
            return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + version + " Safari/537.36";
        }
        if ("safari".equals(mode)) {
            return "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1";
        }
        if ("custom".equals(mode) && customUserAgent != null && customUserAgent.length() > 0) return customUserAgent;
        return WebSettings.getDefaultUserAgent(context);
    }

    public static String modeLabel(String mode) {
        if ("desktop".equals(mode)) return LanguageManager.get("user_agent.mode_desktop", "Chrome desktop");
        if ("safari".equals(mode)) return LanguageManager.get("user_agent.mode_safari", "Safari iOS");
        if ("custom".equals(mode)) return LanguageManager.get("user_agent.mode_custom", "Kustom");
        return LanguageManager.get("user_agent.mode_default", "Default Android WebView");
    }
}
