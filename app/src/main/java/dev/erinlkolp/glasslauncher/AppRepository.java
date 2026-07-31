package dev.erinlkolp.glasslauncher;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.List;

/** Queries the platform for launchable activities. */
public final class AppRepository {

    private final PackageManager packageManager;

    public AppRepository(PackageManager packageManager) {
        this.packageManager = packageManager;
    }

    public List<AppEntry> load() {
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolved = packageManager.queryIntentActivities(intent, 0);
        List<AppEntry> entries = new ArrayList<AppEntry>(resolved.size());
        for (ResolveInfo info : resolved) {
            CharSequence label = info.loadLabel(packageManager);
            entries.add(new AppEntry(
                    label == null ? info.activityInfo.name : label.toString(),
                    info.activityInfo.packageName,
                    info.activityInfo.name));
        }
        return AppEntrySorter.sort(entries);
    }
}
