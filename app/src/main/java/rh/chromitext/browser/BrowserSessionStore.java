package rh.chromitext.browser;

import java.util.ArrayList;
import java.util.List;

/** Process-local retention for live WebViews while the opt-in foreground service is enabled. */
public final class BrowserSessionStore {
    private static final ArrayList<BrowserTab> RETAINED_TABS = new ArrayList<BrowserTab>();
    private static int activeIndex;
    private static MainActivity retainedRuntime;

    private BrowserSessionStore() { }

    public static synchronized void retain(List<BrowserTab> tabs, int selectedIndex) {
        RETAINED_TABS.clear();
        if (tabs != null) RETAINED_TABS.addAll(tabs);
        activeIndex = Math.max(0, Math.min(Math.max(0, RETAINED_TABS.size() - 1), selectedIndex));
    }

    public static synchronized boolean hasRetainedTabs() { return RETAINED_TABS.size() > 0; }

    public static synchronized ArrayList<BrowserTab> takeTabs() {
        ArrayList<BrowserTab> result = new ArrayList<BrowserTab>(RETAINED_TABS);
        RETAINED_TABS.clear();
        return result;
    }

    public static synchronized int getActiveIndex() { return activeIndex; }

    public static synchronized void retainRuntime(MainActivity activity) {
        if (retainedRuntime == null) retainedRuntime = activity;
    }

    public static synchronized MainActivity getRetainedRuntime() { return retainedRuntime; }

    public static synchronized void clear() {
        RETAINED_TABS.clear(); activeIndex = 0;
        retainedRuntime = null;
    }
}
