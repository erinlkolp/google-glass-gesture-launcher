package dev.erinlkolp.glasslauncher;

import static org.junit.Assert.assertEquals;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class AppEntrySorterTest {

    private static AppEntry entry(String label, String pkg, String activity) {
        return new AppEntry(label, pkg, activity);
    }

    @Test
    public void sortsByLabelCaseInsensitively() {
        List<AppEntry> input = Arrays.asList(
                entry("zebra", "c", "C"),
                entry("Apple", "a", "A"),
                entry("mango", "b", "B"));
        List<AppEntry> sorted = AppEntrySorter.sort(input);
        assertEquals("Apple", sorted.get(0).label);
        assertEquals("mango", sorted.get(1).label);
        assertEquals("zebra", sorted.get(2).label);
    }

    @Test
    public void removesDuplicateActivities() {
        List<AppEntry> input = Arrays.asList(
                entry("Camera", "com.android.camera2", "CameraActivity"),
                entry("Camera", "com.android.camera2", "CameraActivity"));
        assertEquals(1, AppEntrySorter.sort(input).size());
    }

    @Test
    public void keepsDistinctActivitiesFromTheSamePackage() {
        List<AppEntry> input = Arrays.asList(
                entry("Clock", "com.android.deskclock", "DeskClock"),
                entry("Settings", "com.android.deskclock", "SettingsActivity"));
        assertEquals(2, AppEntrySorter.sort(input).size());
    }

    @Test
    public void emptyInputProducesEmptyOutput() {
        assertEquals(0, AppEntrySorter.sort(new ArrayList<AppEntry>()).size());
    }

    @Test
    public void doesNotMutateItsInput() {
        List<AppEntry> input = new ArrayList<AppEntry>();
        input.add(entry("zebra", "c", "C"));
        input.add(entry("Apple", "a", "A"));
        AppEntrySorter.sort(input);
        assertEquals("zebra", input.get(0).label);
    }
}
