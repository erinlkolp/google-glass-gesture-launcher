package dev.erinlkolp.glasslauncher;

import android.content.Context;
import java.util.List;

/**
 * One card in the launcher list.
 *
 * <p>A tile is not necessarily an app: {@link AppTile} launches an activity, while
 * {@link WifiTile} toggles a radio. {@link AppCardView} draws tiles without knowing
 * which kind it has.
 */
public interface Tile {

    /**
     * The text drawn large and centred. Evaluated at draw time, so a tile whose
     * state can change may return a different string on each call.
     */
    String label();

    /** Extra lines shown while the detail view is open. May be empty. */
    List<String> detailLines();

    /**
     * Stable identity for this tile, used to decide whether a reloaded list is the
     * same list. Must not vary with the tile's state.
     */
    String key();

    /** What a TAP does. */
    void activate(Context context);
}
