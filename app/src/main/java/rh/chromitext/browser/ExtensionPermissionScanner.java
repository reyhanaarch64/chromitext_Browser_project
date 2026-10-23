package rh.chromitext.browser;

import java.util.Locale;

/** Static source-pattern checks used before running a BeanShell extension. */
public final class ExtensionPermissionScanner {
    private ExtensionPermissionScanner() { }

    public static boolean needsInternet(String source) {
        String text = normalize(source);
        return containsAny(text, new String[]{
            "import java.net.", "java.net.", "javax.net.", "httpurlconnection",
            "httpsurlconnection", "socketchannel", "okhttp", "retrofit",
            "webview.loadurl(", "downloadmanager.enqueue", "android.net.http"
        });
    }

    public static boolean needsAndroidApi(String source) {
        return normalize(source).indexOf("android.") >= 0;
    }

    public static boolean needsFileAccess(String source) {
        return containsAny(normalize(source), new String[]{
            "java.io.file", "fileinputstream", "fileoutputstream", "filereader",
            "filewriter", "randomaccessfile", "java.nio.file."
        });
    }

    public static boolean needsReflection(String source) {
        return containsAny(normalize(source), new String[]{
            "java.lang.reflect.", "forname(", "forname (", "getclass(", "getclass (",
            "getmethod(", "getmethod (", "getdeclaredmethod", "getdeclaredfield",
            "classloader", "loadclass("
        });
    }

    public static boolean needsProcessExecution(String source) {
        return containsAny(normalize(source), new String[]{
            "runtime.getruntime", "processbuilder", "java.lang.runtime"
        });
    }

    public static boolean isNetworkClassName(String name) {
        return name != null && (name.startsWith("java.net.") || name.startsWith("javax.net.") ||
            name.startsWith("java.nio.channels.Socket") || name.startsWith("okhttp3.") ||
            name.startsWith("retrofit2."));
    }

    public static boolean isFileClassName(String name) {
        return name != null && (name.equals("java.io.File") || name.startsWith("java.io.FileInputStream") ||
            name.startsWith("java.io.FileOutputStream") || name.startsWith("java.io.FileReader") ||
            name.startsWith("java.io.FileWriter") || name.startsWith("java.io.RandomAccessFile") ||
            name.startsWith("java.nio.file."));
    }

    public static boolean isReflectionClassName(String name) {
        return name != null && (name.startsWith("java.lang.reflect.") || name.equals("java.lang.ClassLoader"));
    }

    public static boolean isProcessExecutionClassName(String name) {
        return name != null && (name.equals("java.lang.Runtime") || name.equals("java.lang.ProcessBuilder"));
    }

    private static String normalize(String source) {
        return source == null ? "" : source.toLowerCase(Locale.US);
    }

    private static boolean containsAny(String text, String[] tokens) {
        if (text == null || tokens == null) return false;
        for (int i = 0; i < tokens.length; i++) {
            if (text.indexOf(tokens[i]) >= 0) return true;
        }
        return false;
    }
}
