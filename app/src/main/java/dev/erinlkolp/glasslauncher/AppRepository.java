package dev.erinlkolp.glasslauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.List;

/**
 * Queries the platform for launchable activities, caching the sorted result.
 *
 * <p>The cache is invalidated only when a package is actually installed,
 * removed, or changed. Both {@link #load()} and the receiver run on the main
 * thread, so the cache field needs no synchronisation.
 */
public final class AppRepository {

    private final Context context;
    private final PackageManager packageManager;

    private List<AppEntry> cache;
    private boolean watching;

    private final BroadcastReceiver packageWatcher = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ignored, Intent intent) {
            cache = null;
        }
    };

    public AppRepository(Context context) {
        this.context = context;
        this.packageManager = context.getPackageManager();
    }

    /** Begins watching for package changes. Idempotent; pair with {@link #stop()}. */
    public void start() {
        if (watching) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        context.registerReceiver(packageWatcher, filter);
        watching = true;
    }

    /** Stops watching. Idempotent, so a double call cannot throw. */
    public void stop() {
        if (!watching) {
            return;
        }
        context.unregisterReceiver(packageWatcher);
        watching = false;
    }

    /** @return the cached sorted list, rebuilt only when the cache is invalid. */
    public List<AppEntry> load() {
        if (cache != null) {
            return cache;
        }
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
        cache = AppEntrySorter.sort(entries);
        return cache;
    }
}
