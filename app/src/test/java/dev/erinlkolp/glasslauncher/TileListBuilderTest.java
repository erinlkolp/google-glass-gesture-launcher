package dev.erinlkolp.glasslauncher;

import static org.junit.Assert.assertEquals;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class TileListBuilderTest {

    private static AppEntry entry(String label) {
        return new AppEntry(label, "com.example." + label, label + "Activity");
    }

    @Test
    public void pinnedTileComesFirst() {
        List<Tile> result = TileListBuilder.build(
                Collections.<Tile>singletonList(new FakeTile("action:wifi")),
                Arrays.asList(entry("Browser"), entry("Camera")));

        assertEquals(3, result.size());
        assertEquals("action:wifi", result.get(0).key());
    }

    @Test
    public void appOrderIsPreserved() {
        List<Tile> result = TileListBuilder.build(
                Collections.<Tile>singletonList(new FakeTile("action:wifi")),
                Arrays.asList(entry("Browser"), entry("Camera"), entry("Settings")));

        assertEquals("Browser", result.get(1).label());
        assertEquals("Camera", result.get(2).label());
        assertEquals("Settings", result.get(3).label());
    }

    @Test
    public void emptyAppListStillYieldsThePinnedTile() {
        List<Tile> result = TileListBuilder.build(
                Collections.<Tile>singletonList(new FakeTile("action:wifi")),
                Collections.<AppEntry>emptyList());

        assertEquals(1, result.size());
        assertEquals("action:wifi", result.get(0).key());
    }

    @Test
    public void multiplePinnedTilesKeepTheirRelativeOrder() {
        List<Tile> result = TileListBuilder.build(
                Arrays.<Tile>asList(new FakeTile("action:wifi"), new FakeTile("action:bt")),
                Collections.<AppEntry>emptyList());

        assertEquals("action:wifi", result.get(0).key());
        assertEquals("action:bt", result.get(1).key());
    }

    @Test
    public void noPinnedTilesYieldsAppsOnly() {
        List<Tile> result = TileListBuilder.build(
                Collections.<Tile>emptyList(),
                Arrays.asList(entry("Browser")));

        assertEquals(1, result.size());
        assertEquals("app:com.example.Browser/BrowserActivity", result.get(0).key());
    }
}
