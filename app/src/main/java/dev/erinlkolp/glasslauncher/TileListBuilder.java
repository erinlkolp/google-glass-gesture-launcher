package dev.erinlkolp.glasslauncher;

import java.util.ArrayList;
import java.util.List;

/**
 * Composes the card list: pinned tiles first, then one {@link AppTile} per app.
 *
 * <p>App ordering is left entirely to {@link AppEntrySorter}; this only prepends.
 */
public final class TileListBuilder {

    private TileListBuilder() {
    }

    public static List<Tile> build(List<Tile> pinned, List<AppEntry> apps) {
        List<Tile> result = new ArrayList<Tile>(pinned.size() + apps.size());
        result.addAll(pinned);
        for (AppEntry app : apps) {
            result.add(new AppTile(app));
        }
        return result;
    }
}
