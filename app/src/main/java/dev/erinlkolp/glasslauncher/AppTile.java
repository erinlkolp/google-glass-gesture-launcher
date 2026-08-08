package dev.erinlkolp.glasslauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import java.util.Arrays;
import java.util.List;

/** A tile that launches an installed activity. */
public final class AppTile implements Tile {

    private final AppEntry entry;

    public AppTile(AppEntry entry) {
        this.entry = entry;
    }

    @Override
    public String label() {
        return entry.label;
    }

    @Override
    public List<String> detailLines() {
        return Arrays.asList(entry.packageName, entry.activityName);
    }

    @Override
    public String key() {
        return "app:" + entry.packageName + "/" + entry.activityName;
    }

    @Override
    public void activate(Context context) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(new ComponentName(entry.packageName, entry.activityName));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Could not launch " + entry.label, Toast.LENGTH_SHORT).show();
        }
    }
}
