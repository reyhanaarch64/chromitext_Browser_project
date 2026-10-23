package rh.chromitext.browser;

import android.net.Uri;
import java.util.Locale;

/** Stateless URL policy helpers shared by browser navigation handlers. */
public final class BrowserNavigationPolicy {
    private BrowserNavigationPolicy() { }

    public static boolean isCrossDomainNavigation(String sourceUrl, String targetUrl) {
        Uri source = Uri.parse(sourceUrl == null ? "" : sourceUrl);
        Uri target = Uri.parse(targetUrl == null ? "" : targetUrl);
        String sourceScheme = source.getScheme();
        String targetScheme = target.getScheme();
        if (sourceScheme == null || targetScheme == null) return false;
        if (!("http".equalsIgnoreCase(sourceScheme) || "https".equalsIgnoreCase(sourceScheme))) return false;
        if (!("http".equalsIgnoreCase(targetScheme) || "https".equalsIgnoreCase(targetScheme))) return false;
        String sourceHost = normalizeHost(source.getHost());
        String targetHost = normalizeHost(target.getHost());
        if (sourceHost.length() == 0 || targetHost.length() == 0) return false;
        if (sourceHost.equals(targetHost)) return false;
        // Allow the current hostname and its subdomains, e.g. example.com -> api.example.com.
        return !targetHost.endsWith("." + sourceHost);
    }

    public static String normalizeHost(String host) {
        if (host == null) return "";
        String value = host.trim().toLowerCase(Locale.US);
        while (value.endsWith(".") && value.length() > 1) value = value.substring(0, value.length() - 1);
        return value;
    }
}
