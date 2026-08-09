package dev.erinlkolp.glasslauncher;

import java.util.ArrayList;
import java.util.List;

/**
 * The card list and which card is selected.
 *
 * <p>Deliberately free of Android imports. {@link AppCardView} extends {@code View}
 * and so cannot be instantiated in this project's plain-JVM test source set, which
 * left this logic untested while it lived there. The view now delegates here and does
 * nothing but draw.
 */
public final class TileSelection {

    private List<Tile> tiles = new ArrayList<Tile>();
    private int selectedIndex;

    /**
     * Replaces the list, keeping the selection only when the new list holds the same
     * tiles in the same order.
     */
    public void setTiles(List<Tile> tiles) {
        // sameOrder() implies equal sizes, so a preserved index is always still in
        // bounds and needs no clamping here.
        if (!sameOrder(this.tiles, tiles)) {
            selectedIndex = 0;
        }
        this.tiles = tiles;
    }

    private static boolean sameOrder(List<Tile> a, List<Tile> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).key().equals(b.get(i).key())) {
                return false;
            }
        }
        return true;
    }

    /** @return the selected tile, or null when the list is empty. */
    public Tile selected() {
        if (tiles.isEmpty()) {
            return null;
        }
        return tiles.get(selectedIndex);
    }

    /** Moves the selection by {@code delta}, clamped to the list bounds. */
    public void move(int delta) {
        if (tiles.isEmpty()) {
            return;
        }
        selectedIndex = Math.max(0, Math.min(tiles.size() - 1, selectedIndex + delta));
    }

    public void recenter() {
        selectedIndex = 0;
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public int size() {
        return tiles.size();
    }

    public boolean isEmpty() {
        return tiles.isEmpty();
    }
}
