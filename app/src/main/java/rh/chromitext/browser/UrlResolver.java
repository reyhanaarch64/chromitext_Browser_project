package rh.chromitext.browser;

import android.net.Uri;
import java.util.Locale;
import java.util.regex.Pattern;

public final class UrlResolver {
    private static final Pattern WEB_SCHEME = Pattern.compile("(?i)^https?://.*");
    private static final Pattern DOMAIN_LIKE = Pattern.compile("(?i)^[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?(?::[0-9]{1,5})?(?:/[^\\s]*)?$");

    private UrlResolver() { }

    public static String resolve(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.length() == 0) return "";
        if (containsInternalWhitespace(value)) return googleSearch(value);

        if (WEB_SCHEME.matcher(value).matches()) {
            Uri uri = Uri.parse(value);
            if (uri.getHost() != null && uri.getHost().length() > 0) return value;
            return googleSearch(value);
        }

        if (value.startsWith("//")) {
            String candidate = "https:" + value;
            Uri uri = Uri.parse(candidate);
            if (uri.getHost() != null) return candidate;
            return googleSearch(value);
        }

        if (value.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) {
            return value;
        }

        if (DOMAIN_LIKE.matcher(value).matches() &&
            (value.indexOf('.') >= 0 || value.toLowerCase(Locale.US).startsWith("localhost"))) {
            String candidate = "https://" + value;
            Uri uri = Uri.parse(candidate);
            if (uri.getHost() != null && uri.getHost().length() > 0) return candidate;
        }

        return googleSearch(value);
    }

    public static boolean isWebUrl(String value) {
        if (value == null) return false;
        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme();
        return uri.getHost() != null && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
    }

    private static boolean containsInternalWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) return true;
        }
        return false;
    }

    private static String googleSearch(String query) {
        return "https://www.google.com/search?q=" + Uri.encode(query);
    }
}
