package dev.erinlkolp.glasslauncher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure ordering and de-duplication of launchable entries. */
public final class AppEntrySorter {

    private AppEntrySorter() {
    }

    public static List<AppEntry> sort(List<AppEntry> entries) {
        Set<String> seen = new HashSet<String>();
        List<AppEntry> result = new ArrayList<AppEntry>();
        for (AppEntry entry : entries) {
            if (seen.add(entry.packageName + "/" + entry.activityName)) {
                result.add(entry);
            }
        }
        Collections.sort(result, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return result;
    }
}
