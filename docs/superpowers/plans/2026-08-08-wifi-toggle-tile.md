# Wi-Fi Toggle Tile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a card to the launcher, pinned as the first entry, that reports the
current Wi-Fi state and toggles the radio on tap.

**Architecture:** Introduce a `Tile` interface so the card list is no longer assumed to
be app-only, with `AppTile` wrapping today's `AppEntry` and `WifiTile` owning the
toggle. The selection and list-diff logic moves out of `AppCardView` (a `View`, and
therefore not unit-testable in this project) into a pure `TileSelection`. All logic
that does not touch `WifiManager` gets plain JVM tests.

**Tech Stack:** Java 8, Android SDK 22 target / 34 compile, Gradle (Kotlin DSL), JUnit
4.13.2. **No AndroidX** (`android.useAndroidX=false`), no Robolectric, no Glass GDK.

**Spec:** [`docs/superpowers/specs/2026-08-08-wifi-toggle-tile-design.md`](../specs/2026-08-08-wifi-toggle-tile-design.md)

## Global Constraints

- **No AndroidX.** `gradle.properties` sets `android.useAndroidX=false`. Use framework
  classes only (`android.app.Activity`, `android.content.BroadcastReceiver`, …).
- **No Glass GDK.** The device runs stock AOSP; there is no Glass system service.
- **Java 8 source/target**, but match the surrounding code style: explicit type
  arguments (`new ArrayList<Tile>()`, not `<>`), anonymous inner classes rather than
  lambdas, no streams. Read a neighbouring file before writing a new one.
- **`app/src/test` is a plain JVM source set.** No Robolectric, no `androidTest`
  directory. A test may *reference* an Android type in a signature (it compiles against
  the stub `android.jar` and the type is never resolved at runtime) but must never
  *call* framework behaviour or construct a framework object.
- **Display:** see-through optics wash out mid-tones. Pure white on pure black only.
  State is conveyed as **text**, never colour.
- `minSdk = 22`, `targetSdk = 22`, `applicationId dev.erinlkolp.glasslauncher`.
- Existing tests must stay green: 21 in `gesture-core`, 5 in `app`, 16 in `daemon` —
  42 unique `@Test` methods.
- **`app` runs its unit tests twice**, once per build variant, so `./gradlew test`
  reports the `app` figure doubled (5 unique → 10 executions) while `gesture-core` and
  `daemon` are counted once. Per-task verification therefore uses
  `:app:testDebugUnitTest`, which reports unique counts. Expected counts below are
  unique `@Test` methods unless stated otherwise.
- Run all tests with `./gradlew test`. Build and install with `./gradlew :app:installDebug`.
- `adb` is on `PATH` (`~/Android/Sdk/platform-tools/adb`). The README's claim that it lives at a repo-relative path is stale — there is no `./adb`.

---

### Task 1: `Tile` interface and `AppTile`

Establishes the abstraction. `AppTile.activate()` is a straight move of the existing
`LauncherActivity.launchSelected()` body, so behaviour is unchanged.

**Files:**
- Create: `app/src/main/java/dev/erinlkolp/glasslauncher/Tile.java`
- Create: `app/src/main/java/dev/erinlkolp/glasslauncher/AppTile.java`
- Test: `app/src/test/java/dev/erinlkolp/glasslauncher/AppTileTest.java`

**Interfaces:**
- Consumes: `AppEntry` (existing — public final fields `label`, `packageName`, `activityName`)
- Produces: `Tile` with `String label()`, `List<String> detailLines()`, `String key()`,
  `void activate(Context)`. `AppTile(AppEntry)` constructor. `AppTile.key()` returns
  `"app:" + packageName + "/" + activityName`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/dev/erinlkolp/glasslauncher/AppTileTest.java`:

```java
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*AppTileTest*'`
Expected: FAIL — compilation error, `cannot find symbol: class AppTile`.

- [ ] **Step 3: Write `Tile.java`**

```java
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
```

- [ ] **Step 4: Write `AppTile.java`**

The `activate` body is moved verbatim from `LauncherActivity.launchSelected()`
(`LauncherActivity.java:132-146`), including the broad `catch (Exception)` and Toast.

**This deliberately duplicates `launchSelected()` for the duration of Tasks 1-3.**
Deleting the original here would mean retyping `AppCardView`'s generics in the same
commit, which is Task 4's job. The duplicate original is removed in Task 4 step 2. Keep
the two bodies identical until then — do not "improve" this copy.

```java
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
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*AppTileTest*'`
Expected: PASS, 4 tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dev/erinlkolp/glasslauncher/Tile.java \
        app/src/main/java/dev/erinlkolp/glasslauncher/AppTile.java \
        app/src/test/java/dev/erinlkolp/glasslauncher/AppTileTest.java
git commit -m "feat: add Tile abstraction with AppTile"
```

---

### Task 2: `TileSelection`

Extracts the list and selection state out of `AppCardView` so it can be tested. Nothing
consumes it yet — Task 4 wires it in.

**Files:**
- Create: `app/src/main/java/dev/erinlkolp/glasslauncher/TileSelection.java`
- Create: `app/src/test/java/dev/erinlkolp/glasslauncher/FakeTile.java` (shared test double)
- Test: `app/src/test/java/dev/erinlkolp/glasslauncher/TileSelectionTest.java`

**Interfaces:**
- Consumes: `Tile` (Task 1)
- Produces: `TileSelection` with `void setTiles(List<Tile>)`, `Tile selected()` (null when
  empty), `void move(int delta)`, `void recenter()`, `int selectedIndex()`, `int size()`,
  `boolean isEmpty()`.

**Behaviour being preserved** from `AppCardView.java:39-77`: the selection index survives
a reload only when the new list holds the same entries in the same order; otherwise it
resets to 0. `move()` clamps to both ends. `move()` and `recenter()` on an empty list must
not throw.

- [ ] **Step 1: Write the shared test double**

`FakeTile` is used by this task and by Task 3, so it is a top-level test class rather
than a private inner class duplicated in each suite. It implements `activate(Context)`
only because the interface requires it — the parameter type is never resolved at runtime
and the method is never called.

Create `app/src/test/java/dev/erinlkolp/glasslauncher/FakeTile.java`:

```java
package dev.erinlkolp.glasslauncher;

import android.content.Context;
import java.util.Collections;
import java.util.List;

/**
 * A tile that exists only to carry a key.
 *
 * <p>Test-source only. {@link #activate(Context)} is deliberately empty: these tests
 * cover list and selection behaviour, never activation.
 */
final class FakeTile implements Tile {

    private final String key;

    FakeTile(String key) {
        this.key = key;
    }

    @Override
    public String label() {
        return key;
    }

    @Override
    public List<String> detailLines() {
        return Collections.emptyList();
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public void activate(Context context) {
    }
}
```

- [ ] **Step 2: Write the failing test**

Create `app/src/test/java/dev/erinlkolp/glasslauncher/TileSelectionTest.java`.

```java
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
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*TileSelectionTest*'`
Expected: FAIL — compilation error, `cannot find symbol: class TileSelection`.

- [ ] **Step 4: Write `TileSelection.java`**

```java
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
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*TileSelectionTest*'`
Expected: PASS, 7 tests.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/dev/erinlkolp/glasslauncher/TileSelection.java \
        app/src/test/java/dev/erinlkolp/glasslauncher/FakeTile.java \
        app/src/test/java/dev/erinlkolp/glasslauncher/TileSelectionTest.java
git commit -m "feat: extract selection and list diff into a pure TileSelection"
```

---

### Task 3: `TileListBuilder`

Composes the final card list. This is where "pinned first" is decided, and it is pure so
the placement rule is testable.

**Files:**
- Create: `app/src/main/java/dev/erinlkolp/glasslauncher/TileListBuilder.java`
- Test: `app/src/test/java/dev/erinlkolp/glasslauncher/TileListBuilderTest.java`

**Interfaces:**
- Consumes: `Tile`, `AppTile` (Task 1), `AppEntry` (existing), `FakeTile` (Task 2, test source)
- Produces: `static List<Tile> TileListBuilder.build(List<Tile> pinned, List<AppEntry> apps)`

`FakeTile` already exists in the test source set from Task 2 — use it, do not redefine it.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/dev/erinlkolp/glasslauncher/TileListBuilderTest.java`:

```java
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*TileListBuilderTest*'`
Expected: FAIL — compilation error, `cannot find symbol: class TileListBuilder`.

- [ ] **Step 3: Write `TileListBuilder.java`**

```java
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
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*TileListBuilderTest*'`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dev/erinlkolp/glasslauncher/TileListBuilder.java \
        app/src/test/java/dev/erinlkolp/glasslauncher/TileListBuilderTest.java
git commit -m "feat: add TileListBuilder to pin tiles ahead of the app list"
```

---

### Task 4: Migrate the view and activity to tiles

Pure refactor. **User-visible behaviour must be identical after this task** — the pinned
list is empty, so the launcher still shows exactly the apps it does today. Wi-Fi arrives
in Task 6. This is a deliberate checkpoint: if anything regresses on device, it is this
task and not the Wi-Fi work.

**Files:**
- Modify: `app/src/main/java/dev/erinlkolp/glasslauncher/AppCardView.java` (whole file)
- Modify: `app/src/main/java/dev/erinlkolp/glasslauncher/LauncherActivity.java:34,74,110-112,132-146`

**Interfaces:**
- Consumes: `Tile`, `AppTile` (Task 1); `TileSelection` (Task 2); `TileListBuilder` (Task 3)
- Produces: `AppCardView.setEntries(List<Tile>)`, `AppCardView.selected()` returning `Tile`.
  `LauncherActivity.refreshTiles()` and `LauncherActivity.pinnedTiles()` — Task 6 changes
  only `pinnedTiles()`.

- [ ] **Step 1: Rewrite `AppCardView.java`**

The detail-line spacing keeps today's geometry: first line at `centerY + 34`, each
subsequent line 22px lower, matching the old `+34` / `+56` pair.

```java
package dev.erinlkolp.glasslauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import java.util.List;

/** Draws the currently selected tile, one at a time. */
public final class AppCardView extends View {

    /** Vertical gap between successive detail lines. */
    private static final float DETAIL_LINE_HEIGHT = 22.0f;

    /** Offset of the first detail line below the label baseline. */
    private static final float DETAIL_TOP_OFFSET = 34.0f;

    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint detailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint counterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final TileSelection selection = new TileSelection();
    private boolean showingDetail;

    public AppCardView(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);

        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(34.0f);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        detailPaint.setColor(Color.WHITE);
        detailPaint.setTextSize(16.0f);
        detailPaint.setTextAlign(Paint.Align.CENTER);

        counterPaint.setColor(Color.WHITE);
        counterPaint.setTextSize(16.0f);
        counterPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setEntries(List<Tile> tiles) {
        selection.setTiles(tiles);
        invalidate();
    }

    /** @return the selected tile, or null when there is nothing to show. */
    public Tile selected() {
        return selection.selected();
    }

    /** Moves the selection by {@code delta}, clamped to the list bounds. */
    public void move(int delta) {
        selection.move(delta);
        invalidate();
    }

    public void recenter() {
        selection.recenter();
        invalidate();
    }

    public boolean isShowingDetail() {
        return showingDetail;
    }

    public void setShowingDetail(boolean showingDetail) {
        this.showingDetail = showingDetail;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float centerX = getWidth() / 2.0f;
        float centerY = getHeight() / 2.0f;

        if (selection.isEmpty()) {
            canvas.drawText("No launchable apps", centerX, centerY, labelPaint);
            return;
        }

        Tile tile = selection.selected();
        // Labels are read at draw time so a stateful tile repaints correctly on
        // nothing more than invalidate().
        canvas.drawText(tile.label(), centerX, centerY, labelPaint);
        canvas.drawText((selection.selectedIndex() + 1) + " / " + selection.size(),
                centerX, getHeight() - 20.0f, counterPaint);

        if (showingDetail) {
            List<String> lines = tile.detailLines();
            for (int i = 0; i < lines.size(); i++) {
                canvas.drawText(lines.get(i), centerX,
                        centerY + DETAIL_TOP_OFFSET + i * DETAIL_LINE_HEIGHT, detailPaint);
            }
        }
    }
}
```

- [ ] **Step 2: Update `LauncherActivity.java`**

Four edits. First, replace the imports block at the top (`LauncherActivity.java:1-15`) —
`ComponentName` and `Intent` move to `AppTile`, so they go away here:

```java
package dev.erinlkolp.glasslauncher;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import dev.erinlkolp.glasslauncher.gesture.Gesture;
import dev.erinlkolp.glasslauncher.gesture.GestureOrientation;
import dev.erinlkolp.glasslauncher.gesture.GlassGestureDetector;
import dev.erinlkolp.glasslauncher.gesture.TouchSample;
import dev.erinlkolp.glasslauncher.gesture.TouchpadGeometry;
import java.util.Collections;
import java.util.List;
```

Second, in `onCreate`, replace `cardView.setEntries(repository.load());` (line 34) with:

```java
        refreshTiles();
```

Third, in `onResume`, replace `cardView.setEntries(repository.load());` (line 74) with
the same call:

```java
        refreshTiles();
```

Fourth, replace the `case TAP:` body and delete `launchSelected()` entirely
(lines 110-112 and 132-146):

```java
            case TAP:
                activateSelected();
                break;
```

Then add these three methods in place of the old `launchSelected()`:

```java
    /** The tiles pinned ahead of the app list. */
    private List<Tile> pinnedTiles() {
        return Collections.emptyList();
    }

    /**
     * Rebuilds the card list. Cheap: {@link AppRepository#load()} returns its cache
     * unless a package actually changed, and {@link TileSelection} keeps the selection
     * when the rebuilt list is identical.
     */
    private void refreshTiles() {
        cardView.setEntries(TileListBuilder.build(pinnedTiles(), repository.load()));
    }

    private void activateSelected() {
        Tile tile = cardView.selected();
        if (tile == null) {
            return;
        }
        tile.activate(this);
    }
```

Also delete the now-stale comment above the old `onResume` body
(`// Cheap: load() returns the cache unless a package actually changed, / // in which case the receiver has already invalidated it.`)
— that rationale now lives on `refreshTiles()`.

- [ ] **Step 3: Run the full suite**

Run: `./gradlew test`
Expected: PASS, 58 unique tests — 21 `gesture-core` + 21 `app` (5 existing + 16 new)
+ 16 `daemon`. Gradle's own output will show 79 executions, because the 21 `app` tests
run under both variants; that is expected, not a duplicate-test bug.

- [ ] **Step 4: Install and verify no regression on device**

```bash
./gradlew :app:installDebug
adb shell am force-stop dev.erinlkolp.glasslauncher
adb shell am start -n dev.erinlkolp.glasslauncher/.LauncherActivity
adb shell screencap -p /sdcard/tile-refactor.png && adb pull /sdcard/tile-refactor.png /tmp/
```

Confirm by eye and by touchpad: the first card is still an app (**not** Wi-Fi — that is
Task 6), the counter reads `1 / N` with the same N as before, swiping moves the
selection, tapping launches, long press shows package and activity on two lines, and the
camera button recenters.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dev/erinlkolp/glasslauncher/AppCardView.java \
        app/src/main/java/dev/erinlkolp/glasslauncher/LauncherActivity.java
git commit -m "refactor: draw tiles instead of app entries

No behaviour change: the pinned list is empty, so the card list is still
exactly the installed apps. AppCardView now delegates all list and selection
state to TileSelection and does nothing but draw."
```

---

### Task 5: `WifiState`

Pure state mapping and label text. No Android behaviour is invoked, so this is fully
unit-tested.

**Files:**
- Create: `app/src/main/java/dev/erinlkolp/glasslauncher/WifiState.java`
- Test: `app/src/test/java/dev/erinlkolp/glasslauncher/WifiStateTest.java`

**Interfaces:**
- Produces: `enum WifiState { DISABLING, OFF, ENABLING, ON, UNKNOWN, UNAVAILABLE }`
  with `String label()`, `boolean isTransitional()`, and
  `static WifiState fromCode(int)`.

**Why this is JVM-testable despite naming `WifiManager`:** the `WIFI_STATE_*` constants
are `static final int` compile-time constants, so javac inlines their values at the call
site. Nothing resolves `WifiManager` at runtime. Do **not** replace them with magic
numbers, and do **not** call any `WifiManager` method here.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/dev/erinlkolp/glasslauncher/WifiStateTest.java`:

```java
package dev.erinlkolp.glasslauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import android.net.wifi.WifiManager;
import org.junit.Test;

public class WifiStateTest {

    @Test
    public void mapsEveryDocumentedCode() {
        assertEquals(WifiState.DISABLING, WifiState.fromCode(WifiManager.WIFI_STATE_DISABLING));
        assertEquals(WifiState.OFF, WifiState.fromCode(WifiManager.WIFI_STATE_DISABLED));
        assertEquals(WifiState.ENABLING, WifiState.fromCode(WifiManager.WIFI_STATE_ENABLING));
        assertEquals(WifiState.ON, WifiState.fromCode(WifiManager.WIFI_STATE_ENABLED));
        assertEquals(WifiState.UNKNOWN, WifiState.fromCode(WifiManager.WIFI_STATE_UNKNOWN));
    }

    @Test
    public void mapsUnrecognisedCodesToUnknown() {
        assertEquals(WifiState.UNKNOWN, WifiState.fromCode(99));
        assertEquals(WifiState.UNKNOWN, WifiState.fromCode(-1));
        assertEquals(WifiState.UNKNOWN, WifiState.fromCode(Integer.MIN_VALUE));
    }

    @Test
    public void onlyTheInBetweenStatesAreTransitional() {
        assertTrue(WifiState.ENABLING.isTransitional());
        assertTrue(WifiState.DISABLING.isTransitional());
        assertFalse(WifiState.ON.isTransitional());
        assertFalse(WifiState.OFF.isTransitional());
        assertFalse(WifiState.UNKNOWN.isTransitional());
        assertFalse(WifiState.UNAVAILABLE.isTransitional());
    }

    @Test
    public void labelsAreShortEnoughForTheDisplay() {
        assertEquals("On", WifiState.ON.label());
        assertEquals("Off", WifiState.OFF.label());
        assertEquals("Turning on\u2026", WifiState.ENABLING.label());
        assertEquals("Turning off\u2026", WifiState.DISABLING.label());
        assertEquals("Unknown", WifiState.UNKNOWN.label());
        assertEquals("Unavailable", WifiState.UNAVAILABLE.label());
    }

    @Test
    public void everyStateHasANonEmptyLabel() {
        for (WifiState state : WifiState.values()) {
            assertTrue(state.name(), state.label().length() > 0);
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*WifiStateTest*'`
Expected: FAIL — compilation error, `cannot find symbol: class WifiState`.

- [ ] **Step 3: Write `WifiState.java`**

```java
package dev.erinlkolp.glasslauncher;

import android.net.wifi.WifiManager;

/**
 * Wi-Fi radio state and the text shown for it.
 *
 * <p>Takes a raw {@code int} rather than a {@link WifiManager} so the mapping and the
 * labels stay unit-testable on the JVM. The {@code WIFI_STATE_*} constants referenced
 * below are compile-time constants and are inlined by javac, so naming them here costs
 * no runtime dependency on the framework.
 */
public enum WifiState {

    // The ellipsis is a … escape rather than a literal character: the build sets
    // file.encoding through jvmargs but never passes -encoding to javac explicitly, so
    // a literal would be at the mercy of the platform default.
    DISABLING("Turning off\u2026"),
    OFF("Off"),
    ENABLING("Turning on\u2026"),
    ON("On"),
    UNKNOWN("Unknown"),
    /** No {@code WifiManager} at all — the device has no Wi-Fi service. */
    UNAVAILABLE("Unavailable");

    private final String label;

    WifiState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** @return true while the radio is mid-transition, when a tap should be ignored. */
    public boolean isTransitional() {
        return this == ENABLING || this == DISABLING;
    }

    public static WifiState fromCode(int code) {
        switch (code) {
            case WifiManager.WIFI_STATE_DISABLING:
                return DISABLING;
            case WifiManager.WIFI_STATE_DISABLED:
                return OFF;
            case WifiManager.WIFI_STATE_ENABLING:
                return ENABLING;
            case WifiManager.WIFI_STATE_ENABLED:
                return ON;
            default:
                return UNKNOWN;
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests '*WifiStateTest*'`
Expected: PASS, 5 tests.

If instead it fails at runtime with `java.lang.RuntimeException: Method ... not mocked`,
something is calling a real framework method — re-check that this file only reads
constants.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dev/erinlkolp/glasslauncher/WifiState.java \
        app/src/test/java/dev/erinlkolp/glasslauncher/WifiStateTest.java
git commit -m "feat: add pure WifiState mapping and labels"
```

---

### Task 6: `WifiTile`, permissions, and wiring

Lands the feature. `WifiTile` is the only untested class in the change, which is the
point of the split — everything it decides lives in `WifiState`.

**Files:**
- Create: `app/src/main/java/dev/erinlkolp/glasslauncher/WifiTile.java`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/dev/erinlkolp/glasslauncher/LauncherActivity.java`

**Interfaces:**
- Consumes: `Tile` (Task 1), `WifiState` (Task 5), `LauncherActivity.pinnedTiles()` (Task 4)
- Produces: `WifiTile(Context)`, `WifiTile.KEY` = `"action:wifi"`

- [ ] **Step 1: Add the permissions**

In `app/src/main/AndroidManifest.xml`, insert these two lines between the `<manifest>`
open tag and `<application>`. Both are normal-level and, at `targetSdk 22`, are granted
at install with no runtime prompt.

```xml
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
```

- [ ] **Step 2: Write `WifiTile.java`**

Note `getApplicationContext()`: the tile outlives individual draws and there is no reason
for it to hold the Activity.

```java
package dev.erinlkolp.glasslauncher;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.widget.Toast;
import java.util.Arrays;
import java.util.List;

/**
 * A tile that reports and toggles the Wi-Fi radio.
 *
 * <p>{@code setWifiEnabled} is usable directly because this device is API 22; the
 * restriction that forces apps out to Settings landed in API 29.
 *
 * <p>State is read fresh on every {@link #label()} call rather than cached, so the
 * activity only has to call {@code invalidate()} when the state-changed broadcast
 * arrives.
 */
public final class WifiTile implements Tile {

    public static final String KEY = "action:wifi";

    private final WifiManager wifiManager;

    public WifiTile(Context context) {
        this.wifiManager = (WifiManager)
                context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    private WifiState state() {
        if (wifiManager == null) {
            return WifiState.UNAVAILABLE;
        }
        try {
            return WifiState.fromCode(wifiManager.getWifiState());
        } catch (Exception e) {
            return WifiState.UNKNOWN;
        }
    }

    @Override
    public String label() {
        return "Wi-Fi: " + state().label();
    }

    @Override
    public List<String> detailLines() {
        return Arrays.asList("Toggles the Wi-Fi radio", "State: " + state().label());
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public void activate(Context context) {
        WifiState current = state();
        // Ignore taps mid-transition so an impatient double tap cannot queue an
        // enable immediately followed by a disable.
        if (current != WifiState.ON && current != WifiState.OFF) {
            return;
        }
        boolean enable = current == WifiState.OFF;
        try {
            if (!wifiManager.setWifiEnabled(enable)) {
                toastFailure(context);
            }
        } catch (Exception e) {
            toastFailure(context);
        }
    }

    private static void toastFailure(Context context) {
        Toast.makeText(context, "Could not toggle Wi-Fi", Toast.LENGTH_SHORT).show();
    }
}
```

- [ ] **Step 3: Pin the tile and watch for state changes in `LauncherActivity.java`**

Add to the imports:

```java
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
```

Add two fields alongside the existing ones:

```java
    private WifiTile wifiTile;
    private boolean watchingWifi;
```

Add the receiver as a field, mirroring `AppRepository`'s style:

```java
    private final BroadcastReceiver wifiWatcher = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ignored, Intent intent) {
            // WifiTile reads its state at draw time, so a repaint is all that is needed.
            cardView.invalidate();
        }
    };
```

In `onCreate`, construct the tile **before** the first `refreshTiles()` call — insert it
immediately after `repository.start();`:

```java
        wifiTile = new WifiTile(this);
```

Replace the `pinnedTiles()` stub from Task 4 with:

```java
    /** The tiles pinned ahead of the app list. */
    private List<Tile> pinnedTiles() {
        return Collections.<Tile>singletonList(wifiTile);
    }
```

Add the register/unregister pair, using the same idempotent flag pattern as
`AppRepository.start()`/`stop()` so an unbalanced call cannot throw:

```java
    /**
     * Registered only while the launcher is resumed: Wi-Fi state does not matter when
     * the cards are not on screen, and launching an app pauses this activity.
     */
    private void startWatchingWifi() {
        if (watchingWifi) {
            return;
        }
        registerReceiver(wifiWatcher, new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));
        watchingWifi = true;
    }

    private void stopWatchingWifi() {
        if (!watchingWifi) {
            return;
        }
        unregisterReceiver(wifiWatcher);
        watchingWifi = false;
    }
```

Call `startWatchingWifi();` at the end of `onResume()`, and add an `onPause()` override
directly after `onResume()`:

```java
    @Override
    protected void onPause() {
        stopWatchingWifi();
        super.onPause();
    }
```

- [ ] **Step 4: Run the full suite**

Run: `./gradlew test`
Expected: PASS, 63 unique tests (21 `gesture-core` + 26 `app` + 16 `daemon`); Gradle
reports 89 executions for the reason given in the Global Constraints. No existing test
should have changed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/dev/erinlkolp/glasslauncher/WifiTile.java \
        app/src/main/java/dev/erinlkolp/glasslauncher/LauncherActivity.java \
        app/src/main/AndroidManifest.xml
git commit -m "feat: add a pinned Wi-Fi toggle tile"
```

---

### Task 7: On-device verification

`setWifiEnabled()` cannot be unit tested, so this is where the feature is actually
proven. **Do not skip or summarise this task** — record the real command output.

**Files:** none modified.

- [ ] **Step 1: Install and restart the launcher**

```bash
./gradlew :app:installDebug
adb shell am force-stop dev.erinlkolp.glasslauncher
adb shell am start -n dev.erinlkolp.glasslauncher/.LauncherActivity
```

- [ ] **Step 2: Confirm the starting state is off**

```bash
adb shell settings get global wifi_on
```
Expected: `0`. If it prints `1`, turn it off first (`adb shell svc wifi disable`) so
that step 4 exercises the slower off→on direction.

- [ ] **Step 3: Confirm the tile is first and reads "Wi-Fi: Off"**

```bash
adb shell screencap -p /sdcard/wifi-off.png && adb pull /sdcard/wifi-off.png /tmp/
```

Check the pulled image: the label reads `Wi-Fi: Off`, the counter reads `1 / N` where N
is one more than the previous app count, and the text is not clipped at either edge.

- [ ] **Step 4: Tap, then immediately capture the transition**

The tap can be injected rather than performed by hand — the touchpad is classified as an
ordinary `SOURCE_TOUCHSCREEN`, so a synthetic down/up reaches `onTouchEvent` and
`GlassGestureDetector` classifies it as `TAP` exactly like a real one:

```bash
adb shell input tap 320 180 && adb shell screencap -p /sdcard/wifi-turning-on.png \
    && adb pull /sdcard/wifi-turning-on.png /tmp/
```

If `input tap` does not register (the detector applies its own movement and duration
thresholds), fall back to tapping the physical touchpad by hand and capturing within
about two seconds. Either way, **step 8 must still be done by hand** — injected events
cannot confirm the real gesture path works.

Expected: `Wi-Fi: Turning on…`, **fully visible and not clipped**. This is the widest
label the tile can render and the one width check that matters. If it is clipped, shorten
`WifiState.ENABLING`'s label to `"Turning on"` and re-run.

- [ ] **Step 5: Confirm the radio actually came on**

```bash
adb shell settings get global wifi_on
adb shell dumpsys wifi | head -1
```
Expected: `1`, and the `dumpsys` line no longer reads `Wi-Fi is disabled`.

- [ ] **Step 6: Confirm the tile settled on "Wi-Fi: On"**

```bash
adb shell screencap -p /sdcard/wifi-on.png && adb pull /sdcard/wifi-on.png /tmp/
```
Expected: `Wi-Fi: On` — proving the broadcast receiver repainted the card without any
interaction.

- [ ] **Step 7: Toggle back off**

```bash
adb shell input tap 320 180
sleep 6
adb shell settings get global wifi_on
```
Expected: `0`. The `sleep` matters — the radio takes several seconds to come down, and
reading too early returns the pre-transition value.

- [ ] **Step 8: Confirm nothing else regressed**

By hand on the device: swipe forward off the Wi-Fi tile onto the first app and launch it;
two-finger swipe down to come home; press the camera button and confirm the selection
returns to the Wi-Fi tile at index 0; long press the Wi-Fi tile and confirm two detail
lines render; long press an app and confirm package and activity still render.

- [ ] **Step 9: Record the results**

Paste the actual output of steps 2, 5 and 7 into the task report. If any step failed,
stop and report rather than proceeding to Task 8.

---

### Task 8: Refresh docs and prebuilt artifacts

Repo convention is that `apk/` holds committed prebuilts and the README states exact test
counts, so both go stale without this.

**Files:**
- Modify: `app/build.gradle.kts:10-11`
- Modify: `README.md`
- Modify: `apk/README.md`
- Delete: `apk/glass-launcher-v0.1-1-debug.apk`
- Create: `apk/glass-launcher-v0.2-2-debug.apk`

- [ ] **Step 1: Bump the version**

In `app/build.gradle.kts`, change `versionCode = 1` to `versionCode = 2` and
`versionName = "0.1"` to `versionName = "0.2"`.

- [ ] **Step 2: Update the README gesture table**

In `README.md`, change the `Tap` and `Camera button` rows to:

```markdown
| Tap | Activate the selected tile — launch an app, or toggle Wi-Fi |
| Camera button | Recenter selection to the first tile (the Wi-Fi toggle) |
```

- [ ] **Step 3: Document the tile in the README**

Add this section directly before `## Gesture reference`:

```markdown
## Tiles

The card list is not only apps. The first card is always the Wi-Fi toggle; the installed
apps follow it in alphabetical order. Tapping the Wi-Fi card flips the radio and the
label tracks the change, including the `Turning on…` / `Turning off…` states, and it also
reflects changes made by other apps while the launcher is on screen.

State is shown as text rather than colour on purpose: the see-through display washes out
anything that is not pure black or pure white.

This works because the device is API 22 — `WifiManager.setWifiEnabled()` was not
restricted until API 29, so no trip out to Settings is needed.
```

- [ ] **Step 4: Correct the test counts**

Get the real numbers rather than trusting the plan:

```bash
for m in gesture-core app daemon; do \
  printf "%-14s %s\n" "$m" "$(grep -rc '@Test' $m/src/test --include='*.java' | awk -F: '{s+=$2} END {print s}')"; \
done
```

Update the README sentence that currently reads `(40 total: 21 in gesture-core, 5 in app,
14 in daemon)` with the measured figures. Note the `daemon` figure in that sentence is
already stale (it says 14, the tree has 16) — fix it while you are in the line.

- [ ] **Step 5: Rebuild and refresh the prebuilts**

```bash
./gradlew :app:assembleDebug
git rm apk/glass-launcher-v0.1-1-debug.apk
cp app/build/outputs/apk/debug/app-debug.apk apk/glass-launcher-v0.2-2-debug.apk
```

The daemon is untouched by this change, so **do not** rebuild `apk/gestured.jar`.

- [ ] **Step 6: Update `apk/README.md`**

Change the filename in the table row and in the `adb install -r` command from
`glass-launcher-v0.1-1-debug.apk` to `glass-launcher-v0.2-2-debug.apk`.

- [ ] **Step 7: Verify the refreshed APK installs and runs**

```bash
adb install -r apk/glass-launcher-v0.2-2-debug.apk
adb shell dumpsys package dev.erinlkolp.glasslauncher | grep -E 'versionCode|versionName'
```
Expected: `versionCode=2`, `versionName=0.2`.

- [ ] **Step 8: Run the full suite one last time**

Run: `./gradlew test`
Expected: PASS, all green.

- [ ] **Step 9: Commit**

```bash
git add app/build.gradle.kts README.md apk/
git commit -m "docs: document the Wi-Fi tile and refresh prebuilts for v0.2"
```

---

## Verification summary

| Spec section | Covered by |
|---|---|
| §3 Placement (pinned first) | Task 3 (`TileListBuilderTest`), Task 7 step 3 |
| §4.1 `Tile` / `AppTile` | Task 1 |
| §4.0 `TileSelection` | Task 2 |
| §4.2 `WifiState` | Task 5 |
| §4.3 `WifiTile` toggle + transition guard | Task 6, Task 7 steps 4-7 |
| §4.4 `TileListBuilder` | Task 3 |
| §5.1 `AppCardView` / `LauncherActivity` / manifest | Tasks 4 and 6 |
| §6 Receiver lifecycle | Task 6 step 3, Task 7 step 6 |
| §7 Text-only state, label width | Task 5 (labels), Task 7 step 4 (width) |
| §8 Error handling | Task 6 step 2 |
| §9 Pure JVM tests | Tasks 1, 2, 3, 5 |
| §9 On-device verification | Task 7 |
| §10.2 Selection index regression | Task 2 (`TileSelectionTest`), Task 7 step 8 |
| §10.3 Receiver leak | Task 6 step 3 (idempotent flag) |
