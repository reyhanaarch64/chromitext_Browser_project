package rh.chromitext.browser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ExtensionInfo {
    public String id = "";
    public String name = "";
    public String version = "";
    public String type = "";
    public String description = "";
    public String author = "";
    public String javaEntry = "";
    public final List<String> javascriptEntries = new ArrayList<String>();
    public final List<String> permissions = new ArrayList<String>();
    public final List<String> domains = new ArrayList<String>();
    public boolean domainLockEnabled = false;
    public boolean includeSubdomains = false;
    public File directory;

    public String getDisplayName() {
        return name == null || name.length() == 0 ? id : name;
    }
}
