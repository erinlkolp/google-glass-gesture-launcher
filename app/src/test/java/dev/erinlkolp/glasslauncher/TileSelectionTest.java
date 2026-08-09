package dev.erinlkolp.glasslauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class TileSelectionTest {

    private static List<Tile> tiles(String... keys) {
        List<Tile> result = new ArrayList<Tile>();
        for (String key : keys) {
            result.add(new FakeTile(key));
        }
        return result;
    }

    @Test
    public void preservesSelectionWhenTheListIsUnchanged() {
        TileSelection selection = new TileSelection();
        selection.setTiles(tiles("a", "b", "c"));
        selection.move(2);
        assertEquals(2, selection.selectedIndex());

        selection.setTiles(tiles("a", "b", "c"));
        assertEquals(2, selection.selectedIndex());
    }

    @Test
    public void resetsSelectionWhenTheListChanges() {
        TileSelection selection = new TileSelection();
        selection.setTiles(tiles("a", "b", "c"));
        selection.move(2);

        selection.setTiles(tiles("a", "b"));
        assertEquals(0, selection.selectedIndex());
    }

    @Test
    public void resetsSelectionWhenOnlyTheOrderChanges() {
        TileSelection selection = new TileSelection();
        selection.setTiles(tiles("a", "b", "c"));
        selection.move(1);

        selection.setTiles(tiles("c", "b", "a"));
        assertEquals(0, selection.selectedIndex());
    }

    @Test
    public void moveClampsAtBothEnds() {
        TileSelection selection = new TileSelection();
        selection.setTiles(tiles("a", "b", "c"));

        selection.move(99);
        assertEquals(2, selection.selectedIndex());
        selection.move(-99);
        assertEquals(0, selection.selectedIndex());
    }

    @Test
    public void recenterReturnsToTheFirstTile() {
        TileSelection selection = new TileSelection();
        selection.setTiles(tiles("a", "b", "c"));
        selection.move(2);

        selection.recenter();
        assertEquals(0, selection.selectedIndex());
        assertEquals("a", selection.selected().key());
    }

    @Test
    public void selectedIsNullWhenEmptyAndMovingDoesNotThrow() {
        TileSelection selection = new TileSelection();
        assertTrue(selection.isEmpty());
        assertNull(selection.selected());

        selection.move(1);
        selection.recenter();
        assertEquals(0, selection.size());
    }

    @Test
    public void selectedFollowsTheIndex() {
        TileSelection selection = new TileSelection();
        selection.setTiles(Arrays.<Tile>asList(new FakeTile("a"), new FakeTile("b")));
        selection.move(1);
        assertEquals("b", selection.selected().key());
    }
}
