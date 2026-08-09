package dev.erinlkolp.glasslauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import java.util.List;
import org.junit.Test;

public class AppTileTest {

    private static AppTile tile(String label, String pkg, String activity) {
        return new AppTile(new AppEntry(label, pkg, activity));
    }

    @Test
    public void labelIsTheEntryLabel() {
        assertEquals("Camera", tile("Camera", "com.android.camera2", "CameraActivity").label());
    }

    @Test
    public void keyCombinesPackageAndActivity() {
        assertEquals("app:com.android.camera2/CameraActivity",
                tile("Camera", "com.android.camera2", "CameraActivity").key());
    }

    @Test
    public void distinctActivitiesInOnePackageGetDistinctKeys() {
        assertNotEquals(
                tile("Clock", "com.android.deskclock", "DeskClock").key(),
                tile("Timer", "com.android.deskclock", "SettingsActivity").key());
    }

    @Test
    public void detailLinesArePackageThenActivity() {
        List<String> lines = tile("Camera", "com.android.camera2", "CameraActivity").detailLines();
        assertEquals(2, lines.size());
        assertEquals("com.android.camera2", lines.get(0));
        assertEquals("CameraActivity", lines.get(1));
    }
}
