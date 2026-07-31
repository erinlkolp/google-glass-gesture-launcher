package dev.erinlkolp.glasslauncher;

/** One launchable activity. */
public final class AppEntry {

    public final String label;
    public final String packageName;
    public final String activityName;

    public AppEntry(String label, String packageName, String activityName) {
        this.label = label;
        this.packageName = packageName;
        this.activityName = activityName;
    }
}
