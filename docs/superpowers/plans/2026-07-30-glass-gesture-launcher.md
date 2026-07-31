# Glass Gesture Launcher Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a touchpad-driven application launcher for Google Glass Explorer Edition hardware running community AOSP 5.1.1, plus a root daemon providing a global two-finger-swipe-down "go home" gesture.

**Architecture:** Three Gradle modules. `gesture-core` is a pure-JVM library holding the entire gesture recognition algorithm with zero Android imports, which makes it unit-testable without a device and shareable between two hosts. `app` is a normal Android application that feeds it `MotionEvent` data. `daemon` is a plain Java program, dex'd and run as root via `app_process`, that feeds it raw evdev data read from `/dev/input/event3` so gestures can be recognised while other applications are foregrounded.

**Tech Stack:** Java 8 (source/target), Gradle 8.9, Android Gradle Plugin 8.7, JDK 21, JUnit 4. No AndroidX. No third-party runtime dependencies.

**Reference spec:** `docs/superpowers/specs/2026-07-30-glass-gesture-launcher-design.md`

## Global Constraints

Every task's requirements implicitly include this section.

- **Application ID:** `dev.erinlkolp.glasslauncher`
- **`compileSdk 34`, `minSdk 22`, `targetSdk 22`** — `targetSdk` deliberately matches the device; do not raise it.
- **AGP 8.7 / Gradle 8.9 / JDK 21.** JDK 21 has been supported since AGP 8.5.
- **Java `options.release = 8`** on every module. d8 consumes this cleanly for `--min-api 22`.
- **No AndroidX, no support library, no third-party runtime dependencies.** Plain framework `android.app.Activity` and `android.view.View` subclasses only. JUnit 4 is permitted as `testImplementation` (test-only, never shipped).
- **Touchpad native geometry: 1366 × 187.** Display: 640 × 360. These four numbers are measured facts; do not alter them.
- **`gesture-core` must contain zero `android.*` imports.** This is what keeps it JVM-testable. Any task adding one is wrong.
- **Do NOT declare `android.intent.category.HOME`** in the manifest. Phase 3 only. `com.android.launcher2.Launcher` must remain the system home screen for the entirety of this plan.
- **The daemon must NOT issue `EVIOCGRAB`.** Exclusive grab would block input system-wide.
- **`adb` lives at `./tools/platform-tools/adb`** — it is gitignored and not on `PATH`.

---

## File Structure

```
settings.gradle.kts                  Module registry
build.gradle.kts                     Root build config, plugin versions
gradle.properties                    JVM args, AndroidX opt-out
local.properties                     SDK path (gitignored)

gesture-core/                        PURE JVM — zero Android imports
  build.gradle.kts
  src/main/java/dev/erinlkolp/glasslauncher/gesture/
    TouchPhase.java                  DOWN / MOVE / UP / CANCEL
    TouchSample.java                 Immutable (phase, x, y, timeMs, pointerCount)
    Gesture.java                     Recognised gesture enum
    TouchpadGeometry.java            Screen px -> native unit conversion (the anisotropy fix)
    GestureOrientation.java          invertX / invertY axis-direction config
    GlassGestureDetector.java        The recogniser
  src/test/java/dev/erinlkolp/glasslauncher/gesture/
    TouchpadGeometryTest.java
    SwipeTrace.java                  Test helper: builds synthetic sample sequences
    GlassGestureDetectorTest.java

app/                                 Android application
  build.gradle.kts
  src/main/AndroidManifest.xml
  src/main/java/dev/erinlkolp/glasslauncher/
    LauncherActivity.java            Fullscreen host, owns the detector
    MotionEventAdapter.java          MotionEvent -> TouchSample
    AppEntry.java                    label + package + activity class
    AppEntrySorter.java              Pure sorting/dedup — JVM-testable
    AppRepository.java               PackageManager queries + package broadcasts
    AppCardView.java                 Rendering
    GestureDebugOverlay.java         Live diagnostics
  src/main/res/values/strings.xml
  src/test/java/dev/erinlkolp/glasslauncher/
    AppEntrySorterTest.java

daemon/                              Plain Java, dex'd, runs as root
  build.gradle.kts                   Includes the custom d8 dexJar task
  src/main/java/dev/erinlkolp/glasslauncher/daemon/
    InputEvent.java                  16-byte struct input_event parser
    EvdevDeviceLocator.java          Resolve node by name via /proc/bus/input/devices
    EvdevReader.java                 Frame assembly -> TouchSample
    HomeLauncher.java                Fires the HOME intent
    Main.java                        Entry point
  src/test/java/dev/erinlkolp/glasslauncher/daemon/
    InputEventTest.java
    EvdevDeviceLocatorTest.java
    EvdevReaderTest.java
  src/test/resources/
    proc-bus-input-devices.txt       Captured from the device (Task 9)
    two-finger-down.getevent.txt     Captured from the device (Task 9)
```

---

### Task 1: Toolchain and Gradle skeleton

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `local.properties`
- Create: `gradle/wrapper/gradle-wrapper.properties`

**Interfaces:**
- Consumes: nothing.
- Produces: a working `./gradlew` and an Android SDK at `tools/android-sdk` with `platforms;android-34` and `build-tools;34.0.0` installed. Every later task depends on this.

- [ ] **Step 1: Install the Android SDK command-line tools**

The machine has JDK 21 and `tools/platform-tools/adb` but no SDK.

```bash
cd /home/ekolp/workspace/my-first-google-glass-project
mkdir -p tools/android-sdk/cmdline-tools
curl -fsSL -o /tmp/cmdline-tools.zip \
  https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-extract
mv /tmp/cmdline-extract/cmdline-tools tools/android-sdk/cmdline-tools/latest
rm -rf /tmp/cmdline-tools.zip /tmp/cmdline-extract
```

- [ ] **Step 2: Accept licences and install SDK packages**

```bash
cd /home/ekolp/workspace/my-first-google-glass-project
export ANDROID_HOME="$PWD/tools/android-sdk"
yes | tools/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null
tools/android-sdk/cmdline-tools/latest/bin/sdkmanager \
  "platforms;android-34" "build-tools;34.0.0"
```

Verify `tools/android-sdk/build-tools/34.0.0/d8` exists — Task 11 needs it.

- [ ] **Step 3: Write `local.properties`**

Already covered by `.gitignore`.

```properties
sdk.dir=/home/ekolp/workspace/my-first-google-glass-project/tools/android-sdk
```

- [ ] **Step 4: Write `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=false
org.gradle.parallel=true
```

`android.useAndroidX=false` is the explicit opt-out required by the no-AndroidX constraint.

- [ ] **Step 5: Write `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "glass-gesture-launcher"
include(":gesture-core")
include(":app")
include(":daemon")
```

- [ ] **Step 6: Write root `build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application") version "8.7.0" apply false
}
```

`java-library` is a Gradle built-in and needs no version declaration here; only the
Android plugin has to be resolved from the `google()` repository.

- [ ] **Step 7: Create module directories and placeholder build files**

Gradle fails on `include()` of a directory with no build file, so all three must exist before the wrapper runs.

```bash
mkdir -p gesture-core/src/main/java/dev/erinlkolp/glasslauncher/gesture
mkdir -p gesture-core/src/test/java/dev/erinlkolp/glasslauncher/gesture
mkdir -p app/src/main/java/dev/erinlkolp/glasslauncher
mkdir -p app/src/test/java/dev/erinlkolp/glasslauncher
mkdir -p app/src/main/res/values
mkdir -p daemon/src/main/java/dev/erinlkolp/glasslauncher/daemon
mkdir -p daemon/src/test/java/dev/erinlkolp/glasslauncher/daemon
mkdir -p daemon/src/test/resources
```

Write `gesture-core/build.gradle.kts`:

```kotlin
plugins { id("java-library") }

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile>().configureEach { options.release.set(8) }

dependencies { testImplementation("junit:junit:4.13.2") }
```

Write `daemon/build.gradle.kts` with the same content plus its dependency on the core module:

```kotlin
plugins { id("java-library") }

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile>().configureEach { options.release.set(8) }

dependencies {
    implementation(project(":gesture-core"))
    testImplementation("junit:junit:4.13.2")
}
```

Write `app/build.gradle.kts`:

```kotlin
plugins { id("com.android.application") }

android {
    namespace = "dev.erinlkolp.glasslauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.erinlkolp.glasslauncher"
        minSdk = 22
        targetSdk = 22
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
}

dependencies {
    implementation(project(":gesture-core"))
    testImplementation("junit:junit:4.13.2")
}
```

Write a minimal `app/src/main/AndroidManifest.xml` so the module configures. Note the deliberate absence of `CATEGORY_HOME`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="Glass Launcher"
        android:theme="@android:style/Theme.Holo.NoActionBar.Fullscreen">
        <activity
            android:name=".LauncherActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

Create `app/src/main/java/dev/erinlkolp/glasslauncher/LauncherActivity.java` as a stub so the manifest resolves:

```java
package dev.erinlkolp.glasslauncher;

import android.app.Activity;

public class LauncherActivity extends Activity {
}
```

- [ ] **Step 8: Generate the Gradle wrapper**

```bash
cd /home/ekolp/workspace/my-first-google-glass-project
gradle wrapper --gradle-version 8.9 2>/dev/null \
  || curl -fsSL -o /tmp/gradle.zip https://services.gradle.org/distributions/gradle-8.9-bin.zip \
     && unzip -q /tmp/gradle.zip -d /tmp && /tmp/gradle-8.9/bin/gradle wrapper --gradle-version 8.9
```

There is no system `gradle`, so the fallback branch downloads 8.9 once and uses it to write the wrapper.

- [ ] **Step 9: Verify the build configures**

Run: `./gradlew projects`
Expected: lists `:app`, `:daemon`, `:gesture-core` and prints `BUILD SUCCESSFUL`.

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "build: add Gradle skeleton and three-module structure"
```

---

### Task 2: TouchpadGeometry — the anisotropy correction

**Files:**
- Create: `gesture-core/src/main/java/dev/erinlkolp/glasslauncher/gesture/TouchpadGeometry.java`
- Test: `gesture-core/src/test/java/dev/erinlkolp/glasslauncher/gesture/TouchpadGeometryTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `TouchpadGeometry(float screenWidth, float screenHeight)` with instance methods `float toNativeX(float screenX)`, `float toNativeY(float screenY)`, and constant `TouchpadGeometry GLASS` (640 × 360). Task 3 consumes all of these.

This is the highest-value class in the codebase. Spec §3 explains why: the pad is 1366 × 187 mapped onto 640 × 360, so vertical motion arrives ~4.11× amplified. Every threshold downstream is meaningless unless this is right.

- [ ] **Step 1: Write the failing test**

```java
package dev.erinlkolp.glasslauncher.gesture;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class TouchpadGeometryTest {

    private static final float EPS = 0.01f;

    @Test
    public void fullScreenWidthMapsToFullNativeWidth() {
        TouchpadGeometry g = TouchpadGeometry.GLASS;
        assertEquals(1366.0f, g.toNativeX(640.0f), EPS);
    }

    @Test
    public void fullScreenHeightMapsToFullNativeHeight() {
        TouchpadGeometry g = TouchpadGeometry.GLASS;
        assertEquals(187.0f, g.toNativeY(360.0f), EPS);
    }

    @Test
    public void originIsPreserved() {
        TouchpadGeometry g = TouchpadGeometry.GLASS;
        assertEquals(0.0f, g.toNativeX(0.0f), EPS);
        assertEquals(0.0f, g.toNativeY(0.0f), EPS);
    }

    /**
     * The whole reason this class exists. One screen pixel of vertical travel
     * represents far less physical movement than one pixel of horizontal travel.
     */
    @Test
    public void verticalAxisIsCompressedRelativeToHorizontal() {
        TouchpadGeometry g = TouchpadGeometry.GLASS;
        float horizontalUnitsPerPixel = g.toNativeX(1.0f);
        float verticalUnitsPerPixel = g.toNativeY(1.0f);
        assertEquals(2.134375f, horizontalUnitsPerPixel, EPS);
        assertEquals(0.519444f, verticalUnitsPerPixel, EPS);
        assertEquals(4.109f, horizontalUnitsPerPixel / verticalUnitsPerPixel, 0.01f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :gesture-core:test`
Expected: FAIL — compilation error, `TouchpadGeometry` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package dev.erinlkolp.glasslauncher.gesture;

/**
 * Converts screen-space coordinates back into the touchpad's native units.
 *
 * <p>The Glass touchpad reports a native surface of 1366 x 187. Android's
 * InputReader linearly rescales that onto the 640 x 360 display, which is a
 * different aspect ratio, so the coordinates an application receives are
 * anisotropic: vertical motion is amplified roughly 4.11x relative to
 * horizontal. Doing distance or angle arithmetic in screen space therefore
 * produces physically wrong answers. Every measurement in this package is
 * performed in native units obtained through this class.
 */
public final class TouchpadGeometry {

    public static final int NATIVE_WIDTH = 1366;
    public static final int NATIVE_HEIGHT = 187;

    /** The measured configuration of the Glass unit this project targets. */
    public static final TouchpadGeometry GLASS = new TouchpadGeometry(640.0f, 360.0f);

    private final float xScale;
    private final float yScale;

    public TouchpadGeometry(float screenWidth, float screenHeight) {
        if (screenWidth <= 0.0f || screenHeight <= 0.0f) {
            throw new IllegalArgumentException(
                    "screen dimensions must be positive, got "
                            + screenWidth + "x" + screenHeight);
        }
        this.xScale = NATIVE_WIDTH / screenWidth;
        this.yScale = NATIVE_HEIGHT / screenHeight;
    }

    public float toNativeX(float screenX) {
        return screenX * xScale;
    }

    public float toNativeY(float screenY) {
        return screenY * yScale;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :gesture-core:test`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add gesture-core/
git commit -m "feat(gesture-core): add TouchpadGeometry anisotropy correction"
```

---

### Task 3: Single-finger gesture recognition

**Files:**
- Create: `gesture-core/src/main/java/dev/erinlkolp/glasslauncher/gesture/TouchPhase.java`
- Create: `gesture-core/src/main/java/dev/erinlkolp/glasslauncher/gesture/TouchSample.java`
- Create: `gesture-core/src/main/java/dev/erinlkolp/glasslauncher/gesture/Gesture.java`
- Create: `gesture-core/src/main/java/dev/erinlkolp/glasslauncher/gesture/GestureOrientation.java`
- Create: `gesture-core/src/main/java/dev/erinlkolp/glasslauncher/gesture/GlassGestureDetector.java`
- Create: `gesture-core/src/test/java/dev/erinlkolp/glasslauncher/gesture/SwipeTrace.java`
- Test: `gesture-core/src/test/java/dev/erinlkolp/glasslauncher/gesture/GlassGestureDetectorTest.java`

**Interfaces:**
- Consumes: `TouchpadGeometry` from Task 2.
- Produces:
  - `enum TouchPhase { DOWN, MOVE, UP, CANCEL }`
  - `TouchSample(TouchPhase phase, float x, float y, long timeMs, int pointerCount)` with public final fields `phase`, `x`, `y`, `timeMs`, `pointerCount`
  - `enum Gesture { NONE, TAP, LONG_PRESS, SWIPE_FORWARD, SWIPE_BACKWARD, SWIPE_DOWN, TWO_FINGER_SWIPE_FORWARD, TWO_FINGER_SWIPE_BACKWARD, TWO_FINGER_SWIPE_DOWN }`
  - `GestureOrientation(boolean invertX, boolean invertY)` with constant `GestureOrientation.DEFAULT`
  - `GlassGestureDetector(TouchpadGeometry geometry, GestureOrientation orientation)` with method `Gesture accept(TouchSample sample)` returning `Gesture.NONE` for every sample that does not complete a gesture.

Task 4 extends the detector for two-finger gestures. Tasks 6 and 11 both construct a detector.

- [ ] **Step 1: Write the value types**

`TouchPhase.java`:

```java
package dev.erinlkolp.glasslauncher.gesture;

public enum TouchPhase {
    DOWN, MOVE, UP, CANCEL
}
```

`TouchSample.java`:

```java
package dev.erinlkolp.glasslauncher.gesture;

/**
 * One input sample, in screen-space coordinates. Deliberately free of any
 * Android type so that both the in-app MotionEvent path and the root daemon's
 * evdev path can produce it.
 */
public final class TouchSample {

    public final TouchPhase phase;
    public final float x;
    public final float y;
    public final long timeMs;
    public final int pointerCount;

    public TouchSample(TouchPhase phase, float x, float y, long timeMs, int pointerCount) {
        if (phase == null) {
            throw new IllegalArgumentException("phase must not be null");
        }
        this.phase = phase;
        this.x = x;
        this.y = y;
        this.timeMs = timeMs;
        this.pointerCount = pointerCount;
    }
}
```

`Gesture.java`:

```java
package dev.erinlkolp.glasslauncher.gesture;

public enum Gesture {
    NONE,
    TAP,
    LONG_PRESS,
    SWIPE_FORWARD,
    SWIPE_BACKWARD,
    SWIPE_DOWN,
    TWO_FINGER_SWIPE_FORWARD,
    TWO_FINGER_SWIPE_BACKWARD,
    TWO_FINGER_SWIPE_DOWN
}
```

`GestureOrientation.java`:

```java
package dev.erinlkolp.glasslauncher.gesture;

/**
 * Which physical direction each touchpad axis increases in.
 *
 * <p>Spec section 7.2: it is not yet known which end of the temple corresponds
 * to X = 0, nor whether Y increases upward or downward. Task 6 determines both
 * empirically using the on-device debug overlay and updates {@link #DEFAULT}.
 */
public final class GestureOrientation {

    public static final GestureOrientation DEFAULT = new GestureOrientation(false, false);

    public final boolean invertX;
    public final boolean invertY;

    public GestureOrientation(boolean invertX, boolean invertY) {
        this.invertX = invertX;
        this.invertY = invertY;
    }
}
```

- [ ] **Step 2: Write the test helper**

`SwipeTrace.java` — builds realistic sample sequences so tests read as intent rather than as arithmetic.

```java
package dev.erinlkolp.glasslauncher.gesture;

import java.util.ArrayList;
import java.util.List;

/** Builds synthetic touch traces in screen-space pixels for detector tests. */
final class SwipeTrace {

    private final List<TouchSample> samples = new ArrayList<TouchSample>();

    static List<TouchSample> straight(float fromX, float fromY,
                                      float toX, float toY,
                                      long durationMs, int pointerCount) {
        SwipeTrace trace = new SwipeTrace();
        int steps = 10;
        trace.samples.add(new TouchSample(TouchPhase.DOWN, fromX, fromY, 0L, pointerCount));
        for (int i = 1; i < steps; i++) {
            float t = (float) i / steps;
            trace.samples.add(new TouchSample(
                    TouchPhase.MOVE,
                    fromX + (toX - fromX) * t,
                    fromY + (toY - fromY) * t,
                    (long) (durationMs * t),
                    pointerCount));
        }
        trace.samples.add(new TouchSample(TouchPhase.UP, toX, toY, durationMs, pointerCount));
        return trace.samples;
    }

    static Gesture play(GlassGestureDetector detector, List<TouchSample> samples) {
        Gesture last = Gesture.NONE;
        for (TouchSample sample : samples) {
            Gesture g = detector.accept(sample);
            if (g != Gesture.NONE) {
                last = g;
            }
        }
        return last;
    }
}
```

- [ ] **Step 3: Write the failing tests**

`GlassGestureDetectorTest.java`. The fourth test is the one that justifies the entire design — see spec §3.

```java
package dev.erinlkolp.glasslauncher.gesture;

import static org.junit.Assert.assertEquals;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class GlassGestureDetectorTest {

    private GlassGestureDetector detector;

    @Before
    public void setUp() {
        detector = new GlassGestureDetector(TouchpadGeometry.GLASS, GestureOrientation.DEFAULT);
    }

    @Test
    public void shortStationaryContactIsATap() {
        List<TouchSample> trace = SwipeTrace.straight(300f, 180f, 305f, 182f, 120L, 1);
        assertEquals(Gesture.TAP, SwipeTrace.play(detector, trace));
    }

    @Test
    public void longStationaryContactIsALongPress() {
        List<TouchSample> trace = SwipeTrace.straight(300f, 180f, 303f, 181f, 700L, 1);
        assertEquals(Gesture.LONG_PRESS, SwipeTrace.play(detector, trace));
    }

    @Test
    public void clearHorizontalTravelIsAForwardSwipe() {
        List<TouchSample> trace = SwipeTrace.straight(100f, 180f, 300f, 195f, 200L, 1);
        assertEquals(Gesture.SWIPE_FORWARD, SwipeTrace.play(detector, trace));
    }

    /**
     * THE critical test. This swipe travels 117 px horizontally and 135 px
     * vertically in SCREEN space, so a naive detector comparing raw pixel
     * deltas concludes "vertical" and reports SWIPE_DOWN.
     *
     * Converted to native units it is 249.7 x 70.1 — overwhelmingly horizontal.
     * The physical finger motion was a forward swipe with slight downward drift.
     * If this test fails, the anisotropy correction is not being applied.
     */
    @Test
    public void screenSpaceVerticalDominanceDoesNotOverrideNativeHorizontalDominance() {
        List<TouchSample> trace = SwipeTrace.straight(100f, 20f, 217f, 155f, 200L, 1);
        assertEquals(Gesture.SWIPE_FORWARD, SwipeTrace.play(detector, trace));
    }

    @Test
    public void negativeHorizontalTravelIsABackwardSwipe() {
        List<TouchSample> trace = SwipeTrace.straight(400f, 180f, 200f, 190f, 200L, 1);
        assertEquals(Gesture.SWIPE_BACKWARD, SwipeTrace.play(detector, trace));
    }

    @Test
    public void genuineVerticalTravelIsADownSwipe() {
        List<TouchSample> trace = SwipeTrace.straight(300f, 40f, 310f, 300f, 200L, 1);
        assertEquals(Gesture.SWIPE_DOWN, SwipeTrace.play(detector, trace));
    }

    @Test
    public void upwardTravelIsNotRecognised() {
        List<TouchSample> trace = SwipeTrace.straight(300f, 300f, 310f, 40f, 200L, 1);
        assertEquals(Gesture.NONE, SwipeTrace.play(detector, trace));
    }

    @Test
    public void invertedOrientationFlipsForwardAndBackward() {
        GlassGestureDetector inverted = new GlassGestureDetector(
                TouchpadGeometry.GLASS, new GestureOrientation(true, false));
        List<TouchSample> trace = SwipeTrace.straight(100f, 180f, 300f, 195f, 200L, 1);
        assertEquals(Gesture.SWIPE_BACKWARD, SwipeTrace.play(inverted, trace));
    }

    @Test
    public void cancelledContactProducesNothing() {
        detector.accept(new TouchSample(TouchPhase.DOWN, 100f, 180f, 0L, 1));
        detector.accept(new TouchSample(TouchPhase.MOVE, 300f, 180f, 100L, 1));
        assertEquals(Gesture.NONE,
                detector.accept(new TouchSample(TouchPhase.CANCEL, 300f, 180f, 150L, 1)));
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `./gradlew :gesture-core:test`
Expected: FAIL — `GlassGestureDetector` does not exist.

- [ ] **Step 5: Write the implementation**

```java
package dev.erinlkolp.glasslauncher.gesture;

/**
 * Recognises Glass touchpad gestures from a stream of {@link TouchSample}s.
 *
 * <p>All measurement happens in touchpad-native units, never in screen pixels;
 * see {@link TouchpadGeometry} for why that distinction is load-bearing.
 *
 * <p>Not thread-safe. Each host owns one instance and feeds it from a single
 * thread.
 */
public final class GlassGestureDetector {

    /** Native units of travel below which a contact is stationary. */
    private static final float TAP_SLOP = 40.0f;
    /** Native units of horizontal travel required to call a swipe horizontal. */
    private static final float HORIZONTAL_THRESHOLD = 150.0f;
    /** Native units of vertical travel required to call a swipe vertical. */
    private static final float VERTICAL_THRESHOLD = 60.0f;
    /** Contact duration at or above which a stationary contact is a long press. */
    private static final long LONG_PRESS_MS = 500L;

    private final TouchpadGeometry geometry;
    private final GestureOrientation orientation;

    private boolean tracking;
    private float startNativeX;
    private float startNativeY;
    private long startTimeMs;
    private int maxPointerCount;

    public GlassGestureDetector(TouchpadGeometry geometry, GestureOrientation orientation) {
        if (geometry == null || orientation == null) {
            throw new IllegalArgumentException("geometry and orientation must not be null");
        }
        this.geometry = geometry;
        this.orientation = orientation;
    }

    /**
     * @return the recognised gesture, or {@link Gesture#NONE} if this sample did
     *         not complete one.
     */
    public Gesture accept(TouchSample sample) {
        switch (sample.phase) {
            case DOWN:
                tracking = true;
                startNativeX = geometry.toNativeX(sample.x);
                startNativeY = geometry.toNativeY(sample.y);
                startTimeMs = sample.timeMs;
                maxPointerCount = sample.pointerCount;
                return Gesture.NONE;

            case MOVE:
                if (tracking && sample.pointerCount > maxPointerCount) {
                    maxPointerCount = sample.pointerCount;
                }
                return Gesture.NONE;

            case CANCEL:
                tracking = false;
                return Gesture.NONE;

            case UP:
                if (!tracking) {
                    return Gesture.NONE;
                }
                tracking = false;
                if (sample.pointerCount > maxPointerCount) {
                    maxPointerCount = sample.pointerCount;
                }
                return classify(sample);

            default:
                return Gesture.NONE;
        }
    }

    private Gesture classify(TouchSample up) {
        float dx = geometry.toNativeX(up.x) - startNativeX;
        float dy = geometry.toNativeY(up.y) - startNativeY;
        if (orientation.invertX) {
            dx = -dx;
        }
        if (orientation.invertY) {
            dy = -dy;
        }

        long durationMs = up.timeMs - startTimeMs;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);

        if (distance < TAP_SLOP) {
            return durationMs >= LONG_PRESS_MS ? Gesture.LONG_PRESS : Gesture.TAP;
        }

        // Both deltas are in the same physical units, so a bare magnitude
        // comparison is now meaningful. In screen space it would not be.
        boolean horizontal = Math.abs(dx) >= Math.abs(dy);

        if (horizontal) {
            if (Math.abs(dx) < HORIZONTAL_THRESHOLD) {
                return Gesture.NONE;
            }
            return dx > 0.0f ? Gesture.SWIPE_FORWARD : Gesture.SWIPE_BACKWARD;
        }

        if (Math.abs(dy) < VERTICAL_THRESHOLD) {
            return Gesture.NONE;
        }
        // Upward swipes are unassigned; only downward is meaningful.
        return dy > 0.0f ? Gesture.SWIPE_DOWN : Gesture.NONE;
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :gesture-core:test`
Expected: PASS, 13 tests — 4 in `TouchpadGeometryTest`, 9 in `GlassGestureDetectorTest`.

- [ ] **Step 7: Commit**

```bash
git add gesture-core/
git commit -m "feat(gesture-core): recognise single-finger taps and swipes"
```

---

### Task 4: Two-finger gesture recognition

**Files:**
- Modify: `gesture-core/src/main/java/dev/erinlkolp/glasslauncher/gesture/GlassGestureDetector.java`
- Modify: `gesture-core/src/test/java/dev/erinlkolp/glasslauncher/gesture/GlassGestureDetectorTest.java`

**Interfaces:**
- Consumes: everything from Task 3.
- Produces: no new types. `GlassGestureDetector.accept` gains the ability to return `TWO_FINGER_SWIPE_FORWARD`, `TWO_FINGER_SWIPE_BACKWARD`, and `TWO_FINGER_SWIPE_DOWN`. Task 8 and Task 11 depend on these.

- [ ] **Step 1: Write the failing tests**

Append to `GlassGestureDetectorTest.java`:

```java
    @Test
    public void twoFingerHorizontalTravelIsATwoFingerForwardSwipe() {
        List<TouchSample> trace = SwipeTrace.straight(100f, 180f, 300f, 195f, 200L, 2);
        assertEquals(Gesture.TWO_FINGER_SWIPE_FORWARD, SwipeTrace.play(detector, trace));
    }

    @Test
    public void twoFingerBackwardTravelIsATwoFingerBackwardSwipe() {
        List<TouchSample> trace = SwipeTrace.straight(400f, 180f, 200f, 190f, 200L, 2);
        assertEquals(Gesture.TWO_FINGER_SWIPE_BACKWARD, SwipeTrace.play(detector, trace));
    }

    @Test
    public void twoFingerDownwardTravelIsTheGlobalHomeGesture() {
        List<TouchSample> trace = SwipeTrace.straight(300f, 40f, 310f, 300f, 200L, 2);
        assertEquals(Gesture.TWO_FINGER_SWIPE_DOWN, SwipeTrace.play(detector, trace));
    }

    /**
     * A second finger landing mid-gesture must still count. The peak pointer
     * count across the whole contact decides, not the count at lift-off.
     */
    @Test
    public void secondFingerArrivingMidSwipeStillCountsAsTwoFinger() {
        detector.accept(new TouchSample(TouchPhase.DOWN, 300f, 40f, 0L, 1));
        detector.accept(new TouchSample(TouchPhase.MOVE, 305f, 150f, 100L, 2));
        assertEquals(Gesture.TWO_FINGER_SWIPE_DOWN,
                detector.accept(new TouchSample(TouchPhase.UP, 310f, 300f, 200L, 1)));
    }

    @Test
    public void twoFingerStationaryContactIsNotAGesture() {
        List<TouchSample> trace = SwipeTrace.straight(300f, 180f, 303f, 181f, 120L, 2);
        assertEquals(Gesture.NONE, SwipeTrace.play(detector, trace));
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :gesture-core:test`
Expected: FAIL — the two-finger tests report `SWIPE_FORWARD` / `SWIPE_DOWN` / `TAP` because pointer count is currently ignored during classification.

- [ ] **Step 3: Modify `classify` to branch on pointer count**

Replace the body of `classify` in `GlassGestureDetector.java` with:

```java
    private Gesture classify(TouchSample up) {
        float dx = geometry.toNativeX(up.x) - startNativeX;
        float dy = geometry.toNativeY(up.y) - startNativeY;
        if (orientation.invertX) {
            dx = -dx;
        }
        if (orientation.invertY) {
            dy = -dy;
        }

        long durationMs = up.timeMs - startTimeMs;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        boolean multiTouch = maxPointerCount >= 2;

        if (distance < TAP_SLOP) {
            if (multiTouch) {
                // Two-finger taps carry no meaning in this design.
                return Gesture.NONE;
            }
            return durationMs >= LONG_PRESS_MS ? Gesture.LONG_PRESS : Gesture.TAP;
        }

        // Both deltas are in the same physical units, so a bare magnitude
        // comparison is now meaningful. In screen space it would not be.
        boolean horizontal = Math.abs(dx) >= Math.abs(dy);

        if (horizontal) {
            if (Math.abs(dx) < HORIZONTAL_THRESHOLD) {
                return Gesture.NONE;
            }
            if (multiTouch) {
                return dx > 0.0f
                        ? Gesture.TWO_FINGER_SWIPE_FORWARD
                        : Gesture.TWO_FINGER_SWIPE_BACKWARD;
            }
            return dx > 0.0f ? Gesture.SWIPE_FORWARD : Gesture.SWIPE_BACKWARD;
        }

        if (Math.abs(dy) < VERTICAL_THRESHOLD) {
            return Gesture.NONE;
        }
        // Upward swipes are unassigned; only downward is meaningful.
        if (dy <= 0.0f) {
            return Gesture.NONE;
        }
        return multiTouch ? Gesture.TWO_FINGER_SWIPE_DOWN : Gesture.SWIPE_DOWN;
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :gesture-core:test`
Expected: PASS, 18 tests — 4 in `TouchpadGeometryTest`, 14 in `GlassGestureDetectorTest`.

- [ ] **Step 5: Commit**

```bash
git add gesture-core/
git commit -m "feat(gesture-core): recognise two-finger swipes"
```

---

### Task 5: App shell that installs and runs on the device

**Files:**
- Modify: `app/src/main/java/dev/erinlkolp/glasslauncher/LauncherActivity.java`
- Create: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: the Gradle setup from Task 1.
- Produces: an installable APK whose `LauncherActivity` fills the display with a black `AppCardView`-shaped placeholder. Task 6 attaches input handling to it.

- [ ] **Step 1: Write `strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Glass Launcher</string>
</resources>
```

- [ ] **Step 2: Point the manifest label at it**

In `app/src/main/AndroidManifest.xml`, change `android:label="Glass Launcher"` to `android:label="@string/app_name"`. Leave everything else as written in Task 1 — in particular, do **not** add `CATEGORY_HOME`.

- [ ] **Step 3: Implement the activity**

```java
package dev.erinlkolp.glasslauncher;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class LauncherActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        TextView placeholder = new TextView(this);
        placeholder.setBackgroundColor(Color.BLACK);
        placeholder.setTextColor(Color.WHITE);
        placeholder.setTextSize(24.0f);
        placeholder.setText("Glass Launcher\n"
                + metrics.widthPixels + " x " + metrics.heightPixels
                + " @ " + metrics.densityDpi + "dpi");
        placeholder.setPadding(16, 16, 16, 16);
        setContentView(placeholder);

        placeholder.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
    }
}
```

Printing the metrics confirms on real hardware that the display is 640 × 360 @ 240 dpi, which every rendering decision assumes.

- [ ] **Step 4: Build and install**

```bash
./gradlew :app:installDebug
```

Expected: `BUILD SUCCESSFUL` and `Installed on 1 device`.

If Gradle cannot find the device, point it at the local adb:

```bash
ANDROID_SDK_ROOT="$PWD/tools/android-sdk" ./gradlew :app:installDebug
```

- [ ] **Step 5: Launch and verify on the device**

```bash
./tools/platform-tools/adb shell am start \
    -n dev.erinlkolp.glasslauncher/.LauncherActivity
./tools/platform-tools/adb shell dumpsys activity activities | grep mResumedActivity
```

Expected: `mResumedActivity` names `dev.erinlkolp.glasslauncher/.LauncherActivity`, and the prism shows white text on black reading `640 x 360 @ 240dpi`.

- [ ] **Step 6: Commit**

```bash
git add app/
git commit -m "feat(app): add fullscreen launcher activity shell"
```

---

### Task 6: MotionEvent adapter and debug overlay

**Files:**
- Create: `app/src/main/java/dev/erinlkolp/glasslauncher/MotionEventAdapter.java`
- Create: `app/src/main/java/dev/erinlkolp/glasslauncher/GestureDebugOverlay.java`
- Modify: `app/src/main/java/dev/erinlkolp/glasslauncher/LauncherActivity.java`
- Modify: `gesture-core/src/main/java/dev/erinlkolp/glasslauncher/gesture/GestureOrientation.java` (only if Step 6 shows inversion is needed)

**Interfaces:**
- Consumes: `TouchSample`, `TouchPhase`, `Gesture`, `GlassGestureDetector`, `GestureOrientation`, `TouchpadGeometry` from Tasks 2–4.
- Produces: `MotionEventAdapter.toSample(MotionEvent event)` returning `TouchSample` or `null` for uninteresting actions; `GestureDebugOverlay extends View` with `void record(TouchSample sample, Gesture gesture)`. Task 8 consumes `MotionEventAdapter` permanently. `GestureDebugOverlay` stops being the content view in Task 8 but stays in the source tree as a diagnostic — swap it back into `setContentView` whenever touchpad behaviour needs inspecting again.

This task resolves spec §7.2 — the touchpad orientation unknown.

- [ ] **Step 1: Write the adapter**

```java
package dev.erinlkolp.glasslauncher;

import android.view.MotionEvent;
import dev.erinlkolp.glasslauncher.gesture.TouchPhase;
import dev.erinlkolp.glasslauncher.gesture.TouchSample;

/** Translates Android {@link MotionEvent}s into device-agnostic samples. */
public final class MotionEventAdapter {

    private MotionEventAdapter() {
    }

    /** @return a sample, or null if this event carries no useful phase. */
    public static TouchSample toSample(MotionEvent event) {
        TouchPhase phase;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                phase = TouchPhase.DOWN;
                break;
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                phase = TouchPhase.MOVE;
                break;
            case MotionEvent.ACTION_UP:
                phase = TouchPhase.UP;
                break;
            case MotionEvent.ACTION_CANCEL:
                phase = TouchPhase.CANCEL;
                break;
            default:
                return null;
        }
        return new TouchSample(
                phase,
                event.getX(),
                event.getY(),
                event.getEventTime(),
                event.getPointerCount());
    }
}
```

`ACTION_POINTER_DOWN` maps to `MOVE` deliberately: the detector only needs it to raise its peak pointer count, which Task 4's `secondFingerArrivingMidSwipeStillCountsAsTwoFinger` test covers.

- [ ] **Step 2: Write the overlay**

```java
package dev.erinlkolp.glasslauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import dev.erinlkolp.glasslauncher.gesture.Gesture;
import dev.erinlkolp.glasslauncher.gesture.TouchSample;
import dev.erinlkolp.glasslauncher.gesture.TouchpadGeometry;

/**
 * Live touchpad diagnostics. Exists to make the touchpad observable rather than
 * guessed at, and specifically to answer which physical end of the temple is
 * X = 0 (spec section 7.2).
 */
public final class GestureDebugOverlay extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TouchpadGeometry geometry = TouchpadGeometry.GLASS;

    private String phaseLine = "no input yet";
    private String coordLine = "";
    private String nativeLine = "";
    private String gestureLine = "last gesture: none";

    public GestureDebugOverlay(Context context) {
        super(context);
        setBackgroundColor(Color.BLACK);
        paint.setColor(Color.WHITE);
        paint.setTextSize(18.0f);
    }

    public void record(TouchSample sample, Gesture gesture) {
        if (sample != null) {
            phaseLine = "phase: " + sample.phase + "   pointers: " + sample.pointerCount;
            coordLine = String.format("screen: %.0f, %.0f", sample.x, sample.y);
            nativeLine = String.format("native: %.0f, %.0f",
                    geometry.toNativeX(sample.x), geometry.toNativeY(sample.y));
        }
        if (gesture != null && gesture != Gesture.NONE) {
            gestureLine = "last gesture: " + gesture;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float y = 30.0f;
        canvas.drawText(phaseLine, 12.0f, y, paint);
        canvas.drawText(coordLine, 12.0f, y + 26.0f, paint);
        canvas.drawText(nativeLine, 12.0f, y + 52.0f, paint);
        canvas.drawText(gestureLine, 12.0f, y + 78.0f, paint);
    }
}
```

- [ ] **Step 3: Wire the activity to both**

Replace `LauncherActivity.java` entirely:

```java
package dev.erinlkolp.glasslauncher;

import android.app.Activity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.WindowManager;
import dev.erinlkolp.glasslauncher.gesture.Gesture;
import dev.erinlkolp.glasslauncher.gesture.GestureOrientation;
import dev.erinlkolp.glasslauncher.gesture.GlassGestureDetector;
import dev.erinlkolp.glasslauncher.gesture.TouchSample;
import dev.erinlkolp.glasslauncher.gesture.TouchpadGeometry;

public class LauncherActivity extends Activity {

    private GlassGestureDetector detector;
    private GestureDebugOverlay overlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        detector = new GlassGestureDetector(TouchpadGeometry.GLASS, GestureOrientation.DEFAULT);
        overlay = new GestureDebugOverlay(this);
        setContentView(overlay);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        TouchSample sample = MotionEventAdapter.toSample(event);
        if (sample == null) {
            return super.onTouchEvent(event);
        }
        Gesture gesture = detector.accept(sample);
        overlay.record(sample, gesture);
        return true;
    }
}
```

- [ ] **Step 4: Build, install, verify it responds**

```bash
./gradlew :app:installDebug
./tools/platform-tools/adb shell am start \
    -n dev.erinlkolp.glasslauncher/.LauncherActivity
```

Expected: touching the temple updates the `phase`, `screen`, and `native` lines live.

- [ ] **Step 5: Determine touchpad orientation**

Swipe **forward** (toward the front of the head) and read the `native:` X value.

- If X **increases** on a forward swipe, `GestureOrientation.DEFAULT` is already correct. Do nothing.
- If X **decreases**, edit `GestureOrientation.java` and change `DEFAULT` to
  `new GestureOrientation(true, false)`.

Then swipe **downward** and read the `native:` Y value.

- If Y **increases** on a downward swipe, Y is already correct.
- If Y **decreases**, set the second constructor argument to `true` as well.

- [ ] **Step 6: Confirm the gesture line reports sensible results**

Perform each of: forward swipe, backward swipe, downward swipe, tap, two-finger downward swipe. Confirm `last gesture:` reports `SWIPE_FORWARD`, `SWIPE_BACKWARD`, `SWIPE_DOWN`, `TAP`, `TWO_FINGER_SWIPE_DOWN` respectively.

If forward and backward are reversed, Step 5's inversion was applied the wrong way — flip `invertX` and re-run.

- [ ] **Step 7: Re-run unit tests**

Changing `GestureOrientation.DEFAULT` does not affect the tests, which construct orientations explicitly.

Run: `./gradlew :gesture-core:test`
Expected: PASS, 18 tests.

- [ ] **Step 8: Commit**

```bash
git add app/ gesture-core/
git commit -m "feat(app): add MotionEvent adapter and gesture debug overlay"
```

---

### Task 7: App inventory

**Files:**
- Create: `app/src/main/java/dev/erinlkolp/glasslauncher/AppEntry.java`
- Create: `app/src/main/java/dev/erinlkolp/glasslauncher/AppEntrySorter.java`
- Create: `app/src/main/java/dev/erinlkolp/glasslauncher/AppRepository.java`
- Test: `app/src/test/java/dev/erinlkolp/glasslauncher/AppEntrySorterTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `AppEntry(String label, String packageName, String activityName)` with public final fields of those names
  - `AppEntrySorter.sort(List<AppEntry> entries)` returning a new sorted, de-duplicated `List<AppEntry>`
  - `AppRepository(Context context)` with `List<AppEntry> load()`, `void start()`, `void stop()`.
    `load()` returns a cached list; `start()`/`stop()` register and unregister the
    package-change receiver that invalidates it, and are idempotent.

Task 8 consumes `AppRepository.load()` and `AppEntry`'s fields.

- [ ] **Step 1: Write `AppEntry`**

```java
package dev.erinlkolp.glasslauncher;

/** One launchable activity. */
public final class AppEntry {

    public final String label;
    public final String packageName;
    public final String activityName;

    public AppEntry(String label, String packageName, String activityName) {
        this.label = label;
        this.packageName = packageName;
        this.activityName = activityName;
    }
}
```

- [ ] **Step 2: Write the failing sorter tests**

The sorter is pure, so it gets real tests. The eng build carries many development packages, so ordering and de-duplication matter more here than on a consumer device.

```java
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
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest`
Expected: FAIL — `AppEntrySorter` does not exist.

- [ ] **Step 4: Write the sorter**

```java
package dev.erinlkolp.glasslauncher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure ordering and de-duplication of launchable entries. */
public final class AppEntrySorter {

    private AppEntrySorter() {
    }

    public static List<AppEntry> sort(List<AppEntry> entries) {
        Set<String> seen = new HashSet<String>();
        List<AppEntry> result = new ArrayList<AppEntry>();
        for (AppEntry entry : entries) {
            if (seen.add(entry.packageName + "/" + entry.activityName)) {
                result.add(entry);
            }
        }
        Collections.sort(result, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return result;
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS, 5 tests.

- [ ] **Step 6: Write the repository**

Per spec §4.2, the repository caches its result and invalidates on package changes.
Enumeration is genuinely expensive here: the eng build carries development packages,
and every entry costs a `loadLabel()` call that touches that package's resources.
Rebuilding on every resume would stall visibly on an OMAP4430.

```java
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
```

- [ ] **Step 7: Commit**

```bash
git add app/
git commit -m "feat(app): add launchable app inventory with sorting"
```

---

### Task 8: Card rendering and gesture-driven navigation

**Files:**
- Create: `app/src/main/java/dev/erinlkolp/glasslauncher/AppCardView.java`
- Modify: `app/src/main/java/dev/erinlkolp/glasslauncher/LauncherActivity.java`

**Interfaces:**
- Consumes: `AppRepository`, `AppEntry` (Task 7); `GlassGestureDetector`, `Gesture` (Tasks 3–4); `MotionEventAdapter`, `GestureDebugOverlay` (Task 6).
- Produces: a functioning launcher. Nothing later depends on it.

- [ ] **Step 1: Write the card view**

Rendering targets a see-through optical display: pure black, pure white, large type, no mid-tones. Spec §4.2.

```java
package dev.erinlkolp.glasslauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** Draws the currently selected app entry, one at a time. */
public final class AppCardView extends View {

    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint detailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint counterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<AppEntry> entries = new ArrayList<AppEntry>();
    private int selectedIndex;
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

    public void setEntries(List<AppEntry> entries) {
        this.entries = entries;
        this.selectedIndex = 0;
        invalidate();
    }

    public AppEntry selected() {
        if (entries.isEmpty()) {
            return null;
        }
        return entries.get(selectedIndex);
    }

    /** Moves the selection by {@code delta}, clamped to the list bounds. */
    public void move(int delta) {
        if (entries.isEmpty()) {
            return;
        }
        selectedIndex = Math.max(0, Math.min(entries.size() - 1, selectedIndex + delta));
        invalidate();
    }

    public void recenter() {
        selectedIndex = 0;
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

        if (entries.isEmpty()) {
            canvas.drawText("No launchable apps", centerX, centerY, labelPaint);
            return;
        }

        AppEntry entry = entries.get(selectedIndex);
        canvas.drawText(entry.label, centerX, centerY, labelPaint);
        canvas.drawText((selectedIndex + 1) + " / " + entries.size(),
                centerX, getHeight() - 20.0f, counterPaint);

        if (showingDetail) {
            canvas.drawText(entry.packageName, centerX, centerY + 34.0f, detailPaint);
            canvas.drawText(entry.activityName, centerX, centerY + 56.0f, detailPaint);
        }
    }
}
```

- [ ] **Step 2: Rewrite the activity to drive it**

```java
package dev.erinlkolp.glasslauncher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Toast;
import dev.erinlkolp.glasslauncher.gesture.Gesture;
import dev.erinlkolp.glasslauncher.gesture.GestureOrientation;
import dev.erinlkolp.glasslauncher.gesture.GlassGestureDetector;
import dev.erinlkolp.glasslauncher.gesture.TouchSample;
import dev.erinlkolp.glasslauncher.gesture.TouchpadGeometry;

public class LauncherActivity extends Activity {

    /** Entries skipped by a two-finger horizontal swipe. */
    private static final int PAGE_JUMP = 10;

    private GlassGestureDetector detector;
    private AppCardView cardView;
    private AppRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        detector = new GlassGestureDetector(TouchpadGeometry.GLASS, GestureOrientation.DEFAULT);
        repository = new AppRepository(this);
        repository.start();
        cardView = new AppCardView(this);
        cardView.setEntries(repository.load());
        setContentView(cardView);
    }

    @Override
    protected void onDestroy() {
        repository.stop();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Cheap: load() returns the cache unless a package actually changed,
        // in which case the receiver has already invalidated it.
        cardView.setEntries(repository.load());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        TouchSample sample = MotionEventAdapter.toSample(event);
        if (sample == null) {
            return super.onTouchEvent(event);
        }
        handle(detector.accept(sample));
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_CAMERA) {
            cardView.recenter();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void handle(Gesture gesture) {
        switch (gesture) {
            case SWIPE_FORWARD:
                cardView.move(1);
                break;
            case SWIPE_BACKWARD:
                cardView.move(-1);
                break;
            case TWO_FINGER_SWIPE_FORWARD:
                cardView.move(PAGE_JUMP);
                break;
            case TWO_FINGER_SWIPE_BACKWARD:
                cardView.move(-PAGE_JUMP);
                break;
            case TAP:
                launchSelected();
                break;
            case LONG_PRESS:
                cardView.setShowingDetail(!cardView.isShowingDetail());
                break;
            case SWIPE_DOWN:
                if (cardView.isShowingDetail()) {
                    cardView.setShowingDetail(false);
                } else {
                    finish();
                }
                break;
            case TWO_FINGER_SWIPE_DOWN:
                // Handled globally by the root daemon; see spec section 5.
                break;
            default:
                break;
        }
    }

    private void launchSelected() {
        AppEntry entry = cardView.selected();
        if (entry == null) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(new ComponentName(entry.packageName, entry.activityName));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not launch " + entry.label, Toast.LENGTH_SHORT).show();
        }
    }
}
```

- [ ] **Step 3: Build, install, and verify each gesture on the device**

```bash
./gradlew :app:installDebug
./tools/platform-tools/adb shell am start \
    -n dev.erinlkolp.glasslauncher/.LauncherActivity
```

Confirm, in order:

1. A list of app names appears with a `n / total` counter.
2. Forward swipe advances the selection; backward swipe reverses it.
3. Two-finger horizontal swipe jumps 10.
4. Tap launches the selected app.
5. Pressing the physical camera button returns the selection to `1 / total`.
6. Long press toggles package and activity names.
7. Swipe down dismisses the detail if shown, otherwise exits the launcher.

- [ ] **Step 4: Commit**

```bash
git add app/
git commit -m "feat(app): add card rendering and gesture-driven navigation"
```

---

### Task 9: Capture real evdev fixtures from the device

**Files:**
- Create: `daemon/src/test/resources/proc-bus-input-devices.txt`
- Create: `daemon/src/test/resources/two-finger-down.getevent.txt`
- Create: `docs/superpowers/notes/2026-07-30-mt-protocol-finding.md`

**Interfaces:**
- Consumes: nothing.
- Produces: two test fixtures captured from real hardware, and a written determination of whether the touchpad uses multitouch protocol A or B. Tasks 10 and 11 depend on both.

This resolves spec §7.1. Writing the parser before capturing this would be guessing.

- [ ] **Step 1: Capture the input device list**

```bash
mkdir -p daemon/src/test/resources docs/superpowers/notes
./tools/platform-tools/adb shell cat /proc/bus/input/devices \
    | tr -d '\r' > daemon/src/test/resources/proc-bus-input-devices.txt
```

Confirm the file contains a stanza with `N: Name="sensor00fn11"` and `H: Handlers=event3`.

- [ ] **Step 2: Capture a real two-finger downward swipe**

Start the capture, then perform a two-finger downward swipe on the temple, then let it time out.

```bash
timeout 10 ./tools/platform-tools/adb shell getevent -lt /dev/input/event3 \
    | tr -d '\r' > daemon/src/test/resources/two-finger-down.getevent.txt
wc -l daemon/src/test/resources/two-finger-down.getevent.txt
```

Expected: several hundred lines. If the file is empty, the swipe missed the capture window — repeat.

- [ ] **Step 3: Determine the multitouch protocol**

```bash
grep -c ABS_MT_SLOT       daemon/src/test/resources/two-finger-down.getevent.txt
grep -c SYN_MT_REPORT     daemon/src/test/resources/two-finger-down.getevent.txt
grep -c ABS_MT_TRACKING_ID daemon/src/test/resources/two-finger-down.getevent.txt
```

- `ABS_MT_SLOT` count 0 **and** `SYN_MT_REPORT` count above 0 → **protocol A**. This is what the capability dump predicted.
- `ABS_MT_SLOT` count above 0 → **protocol B**. Task 11 Step 3 gives the alternative implementation.

- [ ] **Step 4: Record the finding**

Write `docs/superpowers/notes/2026-07-30-mt-protocol-finding.md`:

```markdown
# Multitouch protocol determination

Captured from `sensor00fn11` (`/dev/input/event3`) on 2026-07-30 during a
real two-finger downward swipe.

| Event code | Occurrences |
|---|---|
| `ABS_MT_SLOT` | <count from Step 3> |
| `SYN_MT_REPORT` | <count from Step 3> |
| `ABS_MT_TRACKING_ID` | <count from Step 3> |

**Determination: protocol <A or B>.**

Resolves spec section 7.1. `EvdevReader` (Task 11) is implemented against this.
```

Replace each `<count …>` and `<A or B>` with the real values before committing.

- [ ] **Step 5: Commit**

```bash
git add daemon/src/test/resources/ docs/superpowers/notes/
git commit -m "test(daemon): capture real evdev fixtures from device"
```

---

### Task 10: Evdev record parsing and device location

**Files:**
- Create: `daemon/src/main/java/dev/erinlkolp/glasslauncher/daemon/InputEvent.java`
- Create: `daemon/src/main/java/dev/erinlkolp/glasslauncher/daemon/EvdevDeviceLocator.java`
- Test: `daemon/src/test/java/dev/erinlkolp/glasslauncher/daemon/InputEventTest.java`
- Test: `daemon/src/test/java/dev/erinlkolp/glasslauncher/daemon/EvdevDeviceLocatorTest.java`

**Interfaces:**
- Consumes: `proc-bus-input-devices.txt` from Task 9.
- Produces:
  - `InputEvent` with `public static final int SIZE_BYTES = 16`, constants `EV_SYN = 0`, `EV_KEY = 1`, `EV_ABS = 3`, `SYN_REPORT = 0`, `SYN_MT_REPORT = 2`, `ABS_MT_SLOT = 0x2f`, `ABS_MT_POSITION_X = 0x35`, `ABS_MT_POSITION_Y = 0x36`, `ABS_MT_TRACKING_ID = 0x39`; static method `InputEvent parse(byte[] buffer, int offset)`; public final fields `type`, `code`, `value`, `timeMs`
  - `EvdevDeviceLocator.findByName(String contents, String deviceName)` returning `String` node path such as `/dev/input/event3`, or `null`

Task 11 consumes both.

- [ ] **Step 1: Write the failing `InputEvent` tests**

`struct input_event` on this 32-bit ARM device is 16 bytes, little-endian:
`tv_sec` (int32), `tv_usec` (int32), `type` (uint16), `code` (uint16), `value` (int32).

```java
package dev.erinlkolp.glasslauncher.daemon;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class InputEventTest {

    /** tv_sec=1, tv_usec=500000, type=EV_ABS, code=ABS_MT_POSITION_X, value=683 */
    private static byte[] record() {
        return new byte[] {
                0x01, 0x00, 0x00, 0x00,          // tv_sec  = 1
                0x20, (byte) 0xA1, 0x07, 0x00,   // tv_usec = 500000
                0x03, 0x00,                      // type    = EV_ABS
                0x35, 0x00,                      // code    = ABS_MT_POSITION_X
                (byte) 0xAB, 0x02, 0x00, 0x00    // value   = 683
        };
    }

    @Test
    public void recordIsSixteenBytes() {
        assertEquals(16, InputEvent.SIZE_BYTES);
    }

    @Test
    public void parsesTypeCodeAndValue() {
        InputEvent event = InputEvent.parse(record(), 0);
        assertEquals(InputEvent.EV_ABS, event.type);
        assertEquals(InputEvent.ABS_MT_POSITION_X, event.code);
        assertEquals(683, event.value);
    }

    @Test
    public void combinesSecondsAndMicrosecondsIntoMilliseconds() {
        InputEvent event = InputEvent.parse(record(), 0);
        assertEquals(1500L, event.timeMs);
    }

    @Test
    public void parsesAtAnOffsetWithinALargerBuffer() {
        byte[] buffer = new byte[32];
        System.arraycopy(record(), 0, buffer, 16, 16);
        InputEvent event = InputEvent.parse(buffer, 16);
        assertEquals(InputEvent.ABS_MT_POSITION_X, event.code);
        assertEquals(683, event.value);
    }

    @Test
    public void parsesNegativeValues() {
        byte[] buffer = record();
        buffer[11] = 0x00;
        buffer[10] = 0x39;                       // code = ABS_MT_TRACKING_ID
        buffer[12] = (byte) 0xFF;
        buffer[13] = (byte) 0xFF;
        buffer[14] = (byte) 0xFF;
        buffer[15] = (byte) 0xFF;                // value = -1 (contact lifted)
        assertEquals(-1, InputEvent.parse(buffer, 0).value);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :daemon:test`
Expected: FAIL — `InputEvent` does not exist.

- [ ] **Step 3: Write `InputEvent`**

```java
package dev.erinlkolp.glasslauncher.daemon;

/**
 * One {@code struct input_event} as delivered by the Linux evdev interface.
 *
 * <p>Layout on this device (32-bit ARM, little-endian), 16 bytes total:
 * {@code tv_sec} int32, {@code tv_usec} int32, {@code type} uint16,
 * {@code code} uint16, {@code value} int32.
 */
public final class InputEvent {

    public static final int SIZE_BYTES = 16;

    public static final int EV_SYN = 0x00;
    public static final int EV_KEY = 0x01;
    public static final int EV_ABS = 0x03;

    public static final int SYN_REPORT = 0x00;
    public static final int SYN_MT_REPORT = 0x02;

    public static final int ABS_MT_SLOT = 0x2f;
    public static final int ABS_MT_POSITION_X = 0x35;
    public static final int ABS_MT_POSITION_Y = 0x36;
    public static final int ABS_MT_TRACKING_ID = 0x39;

    public final int type;
    public final int code;
    public final int value;
    public final long timeMs;

    private InputEvent(int type, int code, int value, long timeMs) {
        this.type = type;
        this.code = code;
        this.value = value;
        this.timeMs = timeMs;
    }

    public static InputEvent parse(byte[] buffer, int offset) {
        long seconds = readInt32(buffer, offset);
        long micros = readInt32(buffer, offset + 4);
        int type = readUint16(buffer, offset + 8);
        int code = readUint16(buffer, offset + 10);
        int value = (int) readInt32(buffer, offset + 12);
        return new InputEvent(type, code, value, seconds * 1000L + micros / 1000L);
    }

    private static long readInt32(byte[] b, int o) {
        return (b[o] & 0xFFL)
                | ((b[o + 1] & 0xFFL) << 8)
                | ((b[o + 2] & 0xFFL) << 16)
                | ((long) b[o + 3] << 24);
    }

    private static int readUint16(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8);
    }
}
```

`readInt32` casts the top byte without masking so the sign extends — that is what makes the `-1` tracking-id test pass.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :daemon:test`
Expected: PASS, 5 tests.

- [ ] **Step 5: Write the failing locator tests**

Event node numbering is not guaranteed stable across boots, so the daemon resolves it by name.

```java
package dev.erinlkolp.glasslauncher.daemon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;

public class EvdevDeviceLocatorTest {

    private static String fixture() throws IOException {
        InputStream in = EvdevDeviceLocatorTest.class
                .getResourceAsStream("/proc-bus-input-devices.txt");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = in.read(chunk)) != -1) {
            out.write(chunk, 0, read);
        }
        in.close();
        return out.toString("UTF-8");
    }

    @Test
    public void findsTheTouchpadInRealDeviceOutput() throws IOException {
        assertEquals("/dev/input/event3",
                EvdevDeviceLocator.findByName(fixture(), "sensor00fn11"));
    }

    @Test
    public void returnsNullForAnUnknownDevice() throws IOException {
        assertNull(EvdevDeviceLocator.findByName(fixture(), "no-such-device"));
    }

    @Test
    public void findsADeviceOtherThanTheTouchpad() throws IOException {
        assertEquals("/dev/input/event5",
                EvdevDeviceLocator.findByName(fixture(), "gpio-keys"));
    }
}
```

- [ ] **Step 6: Run tests to verify they fail**

Run: `./gradlew :daemon:test`
Expected: FAIL — `EvdevDeviceLocator` does not exist.

- [ ] **Step 7: Write the locator**

```java
package dev.erinlkolp.glasslauncher.daemon;

/**
 * Resolves an input device node from the contents of
 * {@code /proc/bus/input/devices}, matching on device name rather than on a
 * hardcoded event number, which is not stable across boots.
 */
public final class EvdevDeviceLocator {

    private EvdevDeviceLocator() {
    }

    /** @return the node path such as {@code /dev/input/event3}, or null. */
    public static String findByName(String contents, String deviceName) {
        String needle = "Name=\"" + deviceName + "\"";
        boolean inStanza = false;
        for (String line : contents.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                inStanza = false;
                continue;
            }
            if (trimmed.startsWith("N:") && trimmed.contains(needle)) {
                inStanza = true;
                continue;
            }
            if (inStanza && trimmed.startsWith("H:")) {
                // Split on '=' as well as whitespace. The real line is
                // `H: Handlers=event3` with no space after the '=', so
                // splitting on whitespace alone yields the single token
                // "Handlers=event3", which matches no `event` prefix.
                for (String token : trimmed.substring(2).trim().split("[\\s=]+")) {
                    if (token.startsWith("event")) {
                        return "/dev/input/" + token;
                    }
                }
            }
        }
        return null;
    }
}
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `./gradlew :daemon:test`
Expected: PASS, 8 tests.

- [ ] **Step 9: Commit**

```bash
git add daemon/
git commit -m "feat(daemon): parse evdev records and locate input devices"
```

---

### Task 11: The daemon — global two-finger-down goes home

**Files:**
- Create: `daemon/src/main/java/dev/erinlkolp/glasslauncher/daemon/EvdevReader.java`
- Create: `daemon/src/main/java/dev/erinlkolp/glasslauncher/daemon/HomeLauncher.java`
- Create: `daemon/src/main/java/dev/erinlkolp/glasslauncher/daemon/Main.java`
- Test: `daemon/src/test/java/dev/erinlkolp/glasslauncher/daemon/EvdevReaderTest.java`
- Modify: `daemon/build.gradle.kts` (add the `dexJar` task)

**Interfaces:**
- Consumes: `InputEvent`, `EvdevDeviceLocator` (Task 10); `GlassGestureDetector`, `TouchSample`, `TouchPhase`, `Gesture`, `TouchpadGeometry`, `GestureOrientation` (Tasks 2–4).
- Produces: `daemon/build/libs/gestured.jar`, a dex'd jar runnable under `app_process`.

- [ ] **Step 1: Write the failing frame-assembly test**

`EvdevReader` converts evdev frames into `TouchSample`s. Its frame logic is pure and therefore testable: `feed(InputEvent)` returns a `TouchSample` when a frame completes, otherwise `null`.

The touchpad reports native units (X 0–1366, Y 0–187) but `GlassGestureDetector` expects screen-space pixels, so the reader scales native down to screen — the exact inverse of `TouchpadGeometry`.

```java
package dev.erinlkolp.glasslauncher.daemon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import dev.erinlkolp.glasslauncher.gesture.TouchPhase;
import dev.erinlkolp.glasslauncher.gesture.TouchSample;
import org.junit.Before;
import org.junit.Test;

public class EvdevReaderTest {

    private EvdevReader reader;

    @Before
    public void setUp() {
        reader = new EvdevReader();
    }

    private static InputEvent abs(int code, int value) {
        return synthetic(InputEvent.EV_ABS, code, value);
    }

    private static InputEvent syn(int code) {
        return synthetic(InputEvent.EV_SYN, code, 0);
    }

    /** Builds an InputEvent through the real parser so tests exercise it too. */
    private static InputEvent synthetic(int type, int code, int value) {
        byte[] b = new byte[InputEvent.SIZE_BYTES];
        b[8] = (byte) (type & 0xFF);
        b[9] = (byte) ((type >> 8) & 0xFF);
        b[10] = (byte) (code & 0xFF);
        b[11] = (byte) ((code >> 8) & 0xFF);
        b[12] = (byte) (value & 0xFF);
        b[13] = (byte) ((value >> 8) & 0xFF);
        b[14] = (byte) ((value >> 16) & 0xFF);
        b[15] = (byte) ((value >> 24) & 0xFF);
        return InputEvent.parse(b, 0);
    }

    @Test
    public void incompleteFrameProducesNothing() {
        assertNull(reader.feed(abs(InputEvent.ABS_MT_POSITION_X, 683)));
        assertNull(reader.feed(abs(InputEvent.ABS_MT_POSITION_Y, 93)));
    }

    @Test
    public void singleContactFrameProducesADownSample() {
        reader.feed(abs(InputEvent.ABS_MT_TRACKING_ID, 1));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_X, 683));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_Y, 93));
        reader.feed(syn(InputEvent.SYN_MT_REPORT));
        TouchSample sample = reader.feed(syn(InputEvent.SYN_REPORT));

        assertNotNull(sample);
        assertEquals(TouchPhase.DOWN, sample.phase);
        assertEquals(1, sample.pointerCount);
    }

    /** Native 683 of 1366 is mid-pad, which is screen x 320 of 640. */
    @Test
    public void nativeCoordinatesAreScaledIntoScreenSpace() {
        reader.feed(abs(InputEvent.ABS_MT_TRACKING_ID, 1));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_X, 683));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_Y, 93));
        reader.feed(syn(InputEvent.SYN_MT_REPORT));
        TouchSample sample = reader.feed(syn(InputEvent.SYN_REPORT));

        assertEquals(320.0f, sample.x, 1.0f);
        assertEquals(179.0f, sample.y, 2.0f);
    }

    @Test
    public void twoContactsInOneFrameReportPointerCountTwo() {
        reader.feed(abs(InputEvent.ABS_MT_TRACKING_ID, 1));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_X, 600));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_Y, 90));
        reader.feed(syn(InputEvent.SYN_MT_REPORT));
        reader.feed(abs(InputEvent.ABS_MT_TRACKING_ID, 2));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_X, 700));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_Y, 95));
        reader.feed(syn(InputEvent.SYN_MT_REPORT));
        TouchSample sample = reader.feed(syn(InputEvent.SYN_REPORT));

        assertEquals(2, sample.pointerCount);
    }

    @Test
    public void secondFrameWithContactsIsAMove() {
        reader.feed(abs(InputEvent.ABS_MT_TRACKING_ID, 1));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_X, 600));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_Y, 90));
        reader.feed(syn(InputEvent.SYN_MT_REPORT));
        reader.feed(syn(InputEvent.SYN_REPORT));

        reader.feed(abs(InputEvent.ABS_MT_TRACKING_ID, 1));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_X, 650));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_Y, 95));
        reader.feed(syn(InputEvent.SYN_MT_REPORT));
        TouchSample sample = reader.feed(syn(InputEvent.SYN_REPORT));

        assertEquals(TouchPhase.MOVE, sample.phase);
    }

    @Test
    public void emptyFrameAfterContactIsAnUp() {
        reader.feed(abs(InputEvent.ABS_MT_TRACKING_ID, 1));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_X, 600));
        reader.feed(abs(InputEvent.ABS_MT_POSITION_Y, 90));
        reader.feed(syn(InputEvent.SYN_MT_REPORT));
        reader.feed(syn(InputEvent.SYN_REPORT));

        reader.feed(syn(InputEvent.SYN_MT_REPORT));
        TouchSample sample = reader.feed(syn(InputEvent.SYN_REPORT));

        assertEquals(TouchPhase.UP, sample.phase);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :daemon:test`
Expected: FAIL — `EvdevReader` does not exist.

- [ ] **Step 3: Write `EvdevReader`**

This implementation is for **protocol A**, as Task 9 determined. If Task 9 found protocol B instead, replace the contact-counting logic: track the current slot from `ABS_MT_SLOT`, store per-slot tracking ids in a map, treat `ABS_MT_TRACKING_ID == -1` as that slot lifting, and take `pointerCount` as the number of slots with a non-negative tracking id at `SYN_REPORT`. Everything else — the scaling, the phase logic, the public method signature — stays identical.

```java
package dev.erinlkolp.glasslauncher.daemon;

import dev.erinlkolp.glasslauncher.gesture.TouchPhase;
import dev.erinlkolp.glasslauncher.gesture.TouchSample;
import dev.erinlkolp.glasslauncher.gesture.TouchpadGeometry;

/**
 * Assembles evdev multitouch protocol A frames into {@link TouchSample}s.
 *
 * <p>Contacts are delimited by {@code SYN_MT_REPORT}; a frame ends at
 * {@code SYN_REPORT}. Coordinates arrive in touchpad-native units and are
 * scaled into screen space so that samples are indistinguishable from those
 * the in-app MotionEvent path produces, letting both share one detector.
 */
public final class EvdevReader {

    private static final float SCREEN_WIDTH = 640.0f;
    private static final float SCREEN_HEIGHT = 360.0f;

    private int contactsInFrame;
    private boolean sawContactFields;
    private int firstX;
    private int firstY;
    private boolean contactActive;
    private long frameTimeMs;

    /** @return a sample when a frame completes, otherwise null. */
    public TouchSample feed(InputEvent event) {
        if (event.type == InputEvent.EV_ABS) {
            switch (event.code) {
                case InputEvent.ABS_MT_POSITION_X:
                    if (contactsInFrame == 0) {
                        firstX = event.value;
                    }
                    sawContactFields = true;
                    break;
                case InputEvent.ABS_MT_POSITION_Y:
                    if (contactsInFrame == 0) {
                        firstY = event.value;
                    }
                    sawContactFields = true;
                    break;
                case InputEvent.ABS_MT_TRACKING_ID:
                    sawContactFields = true;
                    break;
                default:
                    break;
            }
            return null;
        }

        if (event.type != InputEvent.EV_SYN) {
            return null;
        }

        if (event.code == InputEvent.SYN_MT_REPORT) {
            if (sawContactFields) {
                contactsInFrame++;
                sawContactFields = false;
            }
            return null;
        }

        if (event.code != InputEvent.SYN_REPORT) {
            return null;
        }

        frameTimeMs = event.timeMs;
        TouchSample sample = buildSample();
        contactsInFrame = 0;
        sawContactFields = false;
        return sample;
    }

    private TouchSample buildSample() {
        float screenX = firstX * (SCREEN_WIDTH / TouchpadGeometry.NATIVE_WIDTH);
        float screenY = firstY * (SCREEN_HEIGHT / TouchpadGeometry.NATIVE_HEIGHT);

        if (contactsInFrame == 0) {
            if (!contactActive) {
                return null;
            }
            contactActive = false;
            return new TouchSample(TouchPhase.UP, screenX, screenY, frameTimeMs, 0);
        }

        TouchPhase phase = contactActive ? TouchPhase.MOVE : TouchPhase.DOWN;
        contactActive = true;
        return new TouchSample(phase, screenX, screenY, frameTimeMs, contactsInFrame);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :daemon:test`
Expected: PASS, 14 tests.

- [ ] **Step 5: Write `HomeLauncher`**

```java
package dev.erinlkolp.glasslauncher.daemon;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/** Sends the device to its home screen. Verified working from the shell. */
public final class HomeLauncher {

    private static final String[] COMMAND = {
            "am", "start",
            "-a", "android.intent.action.MAIN",
            "-c", "android.intent.category.HOME"
    };

    public void goHome() {
        try {
            ProcessBuilder builder = new ProcessBuilder(COMMAND);
            builder.redirectErrorStream(true);
            Process process = builder.start();

            // Drain the child's output before waiting. Leaving it unread risks
            // filling the OS pipe buffer, at which point `am` blocks writing and
            // waitFor() never returns — permanently wedging this daemon's single
            // read loop and silently disabling the gesture until reboot.
            InputStream out = process.getInputStream();
            byte[] sink = new byte[256];
            while (out.read(sink) != -1) {
                // discard
            }

            if (!process.waitFor(5L, TimeUnit.SECONDS)) {
                process.destroy();
                System.err.println("gestured: am start timed out");
            }
        } catch (IOException e) {
            System.err.println("gestured: could not launch home: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

The foreground app is deliberately left running; this is Home-button behaviour, not termination. Spec §4.3.

- [ ] **Step 6: Write `Main`**

```java
package dev.erinlkolp.glasslauncher.daemon;

import dev.erinlkolp.glasslauncher.gesture.Gesture;
import dev.erinlkolp.glasslauncher.gesture.GestureOrientation;
import dev.erinlkolp.glasslauncher.gesture.GlassGestureDetector;
import dev.erinlkolp.glasslauncher.gesture.TouchSample;
import dev.erinlkolp.glasslauncher.gesture.TouchpadGeometry;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Watches the touchpad for a global two-finger downward swipe and sends the
 * device home when it sees one.
 *
 * <p>Runs as root via {@code app_process}, because an application uid can
 * neither execute {@code /system/xbin/su} (mode 4750, group shell) nor open
 * {@code /dev/input/event3} (mode 0660, group input). See spec section 4.3.
 *
 * <p>The device is opened for reading only and is never grabbed with
 * {@code EVIOCGRAB}; an exclusive grab would block input system-wide.
 */
public final class Main {

    private static final String DEVICE_NAME = "sensor00fn11";

    public static void main(String[] args) throws IOException {
        String node = locateTouchpad();
        if (node == null) {
            System.err.println("gestured: could not find input device " + DEVICE_NAME);
            System.exit(1);
        }
        System.out.println("gestured: watching " + node);

        EvdevReader reader = new EvdevReader();
        GlassGestureDetector detector =
                new GlassGestureDetector(TouchpadGeometry.GLASS, GestureOrientation.DEFAULT);
        HomeLauncher home = new HomeLauncher();

        InputStream in = new FileInputStream(node);
        byte[] buffer = new byte[InputEvent.SIZE_BYTES * 64];
        try {
            while (true) {
                int read = in.read(buffer);
                if (read < 0) {
                    break;
                }
                for (int offset = 0;
                     offset + InputEvent.SIZE_BYTES <= read;
                     offset += InputEvent.SIZE_BYTES) {
                    TouchSample sample = reader.feed(InputEvent.parse(buffer, offset));
                    if (sample == null) {
                        continue;
                    }
                    if (detector.accept(sample) == Gesture.TWO_FINGER_SWIPE_DOWN) {
                        System.out.println("gestured: two-finger down -> home");
                        home.goHome();
                    }
                }
            }
        } finally {
            in.close();
        }
    }

    private static String locateTouchpad() throws IOException {
        InputStream in = new FileInputStream("/proc/bus/input/devices");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        try {
            while ((read = in.read(chunk)) != -1) {
                out.write(chunk, 0, read);
            }
        } finally {
            in.close();
        }
        return EvdevDeviceLocator.findByName(out.toString("UTF-8"), DEVICE_NAME);
    }
}
```

- [ ] **Step 7: Add the `dexJar` task**

Append to `daemon/build.gradle.kts`:

```kotlin
val dexJar by tasks.registering(Exec::class) {
    description = "Dexes the daemon and gesture-core into a jar runnable by app_process."
    group = "build"
    dependsOn(tasks.named("jar"), project(":gesture-core").tasks.named("jar"))

    val sdkDir = File(rootProject.rootDir, "tools/android-sdk")
    val d8 = File(sdkDir, "build-tools/34.0.0/d8")
    val androidJar = File(sdkDir, "platforms/android-34/android.jar")
    val outputJar = layout.buildDirectory.file("libs/gestured.jar").get().asFile

    doFirst { outputJar.parentFile.mkdirs() }

    commandLine(
        d8.absolutePath,
        "--release",
        "--min-api", "22",
        "--lib", androidJar.absolutePath,
        "--output", outputJar.absolutePath,
        tasks.named<Jar>("jar").get().archiveFile.get().asFile.absolutePath,
        project(":gesture-core").tasks.named<Jar>("jar").get().archiveFile.get().asFile.absolutePath
    )
}
```

`d8` writes a zip containing `classes.dex` when `--output` ends in `.jar`, which is exactly the layout `app_process` expects.

- [ ] **Step 8: Build the dex jar**

Run: `./gradlew :daemon:dexJar`
Expected: `BUILD SUCCESSFUL`, and `daemon/build/libs/gestured.jar` exists.

Verify it contains a dex:

```bash
unzip -l daemon/build/libs/gestured.jar | grep classes.dex
```

Expected: one `classes.dex` entry.

- [ ] **Step 9: Deploy and run the daemon**

```bash
./tools/platform-tools/adb push daemon/build/libs/gestured.jar /data/local/tmp/
./tools/platform-tools/adb shell "CLASSPATH=/data/local/tmp/gestured.jar \
    app_process /system/bin dev.erinlkolp.glasslauncher.daemon.Main"
```

Expected: prints `gestured: watching /dev/input/event3` and stays running.

If it exits with a `ClassNotFoundException`, the jar lacks `classes.dex` — revisit Step 8.

- [ ] **Step 10: Verify the gesture end to end**

With the daemon running in that terminal, in another terminal launch an unrelated app:

```bash
./tools/platform-tools/adb shell am start -n com.android.settings/.Settings
```

Now perform a **two-finger downward swipe** on the temple.

Expected: the daemon prints `gestured: two-finger down -> home`, and the prism returns to the home screen. Confirm:

```bash
./tools/platform-tools/adb shell dumpsys activity activities | grep mResumedActivity
```

Expected: `com.android.launcher2.Launcher`.

Also confirm single-finger swipes inside Settings still scroll normally — the daemon must not be consuming events.

- [ ] **Step 11: Write the run script**

Create `run-daemon.sh` at the repository root:

```bash
#!/usr/bin/env bash
# Builds, deploys, and starts the gesture daemon on the attached Glass.
set -euo pipefail
cd "$(dirname "$0")"
./gradlew :daemon:dexJar
./tools/platform-tools/adb push daemon/build/libs/gestured.jar /data/local/tmp/
exec ./tools/platform-tools/adb shell \
    "CLASSPATH=/data/local/tmp/gestured.jar \
     app_process /system/bin dev.erinlkolp.glasslauncher.daemon.Main"
```

```bash
chmod +x run-daemon.sh
```

- [ ] **Step 12: Run the full test suite**

Run: `./gradlew test`
Expected: PASS. 18 tests in `:gesture-core`, 5 in `:app`, 14 in `:daemon` — 37 total.

- [ ] **Step 13: Commit**

```bash
git add daemon/ run-daemon.sh
git commit -m "feat(daemon): add global two-finger-down home gesture"
```

---

### Task 12: README

**Files:**
- Create: `README.md`

**Interfaces:**
- Consumes: the finished state of every prior task. Run this last, so the gesture
  table and the deploy commands describe what actually shipped rather than what was
  planned.
- Produces: nothing other tasks depend on.

- [ ] **Step 1: Confirm the facts you are about to document**

Do not copy these from the plan — read them out of the code that now exists, because
Task 6 may have changed the orientation default and Task 9 may have found protocol B.

```bash
grep -n "DEFAULT" gesture-core/src/main/java/dev/erinlkolp/glasslauncher/gesture/GestureOrientation.java
grep -n "protocol" docs/superpowers/notes/2026-07-30-mt-protocol-finding.md
./gradlew test 2>&1 | tail -5
```

- [ ] **Step 2: Write `README.md`**

Cover exactly these sections, in this order:

1. **What this is** — a touchpad-driven application launcher for Google Glass Explorer
   Edition hardware running community AOSP 5.1.1, plus a root daemon supplying a global
   go-home gesture. State plainly that it does NOT use the Glass GDK, and why: the
   device runs stock AOSP with no Glass system layer, so the GDK has nothing to talk to.

2. **Hardware it targets** — reproduce the verified-facts table from spec §1.1 and
   §1.2: `glass-1` / OMAP4430 / Android 5.1.1 API 22 / eng build / 640×360 at density
   240 / touchpad `sensor00fn11` reporting `SOURCE_TOUCHSCREEN` with native geometry
   1366 × 187.

3. **The anisotropy problem, in three sentences** — the pad is 1366 × 187 mapped onto
   640 × 360, so received coordinates amplify vertical motion about 4.11×, and every
   measurement is therefore done in native units. Link to spec §3 for the full
   explanation rather than restating it.

4. **Build** — prerequisites (JDK 21, the SDK bootstrap from Task 1), then
   `./gradlew test` and `./gradlew :app:installDebug`.

5. **Gesture reference** — a table of every gesture and its action, read out of
   `LauncherActivity.handle()` and the daemon, not out of the spec.

6. **Running the daemon** — `./run-daemon.sh`, what it prints on success, and the
   explicit note that it must be restarted after every reboot.

7. **Known limitations** — the daemon does not survive reboot; the gesture is observed
   but not consumed, so the foreground app also sees the swipe; the launcher does not
   claim `CATEGORY_HOME`, so `com.android.launcher2.Launcher` remains the system home
   screen.

8. **Layout** — one line per module saying what it holds and, for `gesture-core`, that
   it is deliberately Android-free so it can be unit-tested and shared with the daemon.

9. **Further reading** — relative links to the spec and this plan.

Keep it accurate over comprehensive. Every command in it must be one you have actually
run.

- [ ] **Step 3: Verify every command in the README**

Execute each command block you wrote. Any that fails must be corrected, not removed.

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "docs: add README"
```

---

### Task 13: Hardware-verification fixes

**Run immediately after Task 8** — before Task 9. Both defects were found by physical
on-device testing and both originate in this plan, not in implementer error.

**Files:**
- Modify: `gesture-core/src/main/java/dev/erinlkolp/glasslauncher/gesture/GlassGestureDetector.java`
- Modify: `gesture-core/src/test/java/dev/erinlkolp/glasslauncher/gesture/GlassGestureDetectorTest.java`
- Modify: `app/src/main/java/dev/erinlkolp/glasslauncher/LauncherActivity.java`

**Interfaces:**
- Consumes: everything from Tasks 2–8.
- Produces: no API changes. `Gesture`, `TouchSample`, and the detector's constructor are
  unchanged, so Task 11's daemon is unaffected.

#### Defect 1 — ambiguous gestures degrade into `TAP`, launching apps

Observed: a two-finger downward swipe launched the Camera app and fired the shutter.
The activity-manager event log showed the launch carried flags `270532608`
(`FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_RESET_TASK_IF_NEEDED`) — exactly what
`launchSelected()` sets — proving our own launcher started it after classifying the
swipe as `TAP`.

Root cause: `TAP_SLOP = 40` native units is 3% of the pad's 1366-unit width but **21% of
its 187-unit height**. A downward swipe that does not traverse a fifth of the short axis
falls under the slop and reads as stationary. `TAP` also had no upper duration bound, so
any sub-500 ms contact qualified.

The fallback direction is what makes this serious: an unrecognised gesture should be a
no-op, never "launch whatever is selected."

- [ ] **Step 1: Write the failing tests**

Append to `GlassGestureDetectorTest.java`:

```java
    /**
     * A contact too slow to be a tap but too quick to be a long press must be
     * NOTHING. Previously this returned TAP, which launched the selected app —
     * the most destructive possible outcome for a misread gesture.
     */
    @Test
    public void contactBetweenTapAndLongPressDurationsIsNotAGesture() {
        List<TouchSample> trace = SwipeTrace.straight(300f, 180f, 304f, 181f, 350L, 1);
        assertEquals(Gesture.NONE, SwipeTrace.play(detector, trace));
    }

    /**
     * A deliberate but insufficient downward drag must be NOTHING — not a tap,
     * and not a swipe. 60 screen px of vertical travel is ~31.2 native units:
     * past TAP_SLOP (25) so it is not stationary, but short of
     * VERTICAL_THRESHOLD (60) so it is not a swipe either.
     *
     * <p>This is the gap that previously launched apps: an aborted downward
     * swipe fell under the old TAP_SLOP of 40 and read as a tap.
     */
    @Test
    public void insufficientDownwardDragIsNeitherTapNorSwipe() {
        List<TouchSample> trace = SwipeTrace.straight(300f, 100f, 302f, 160f, 200L, 1);
        assertEquals(Gesture.NONE, SwipeTrace.play(detector, trace));
    }

    @Test
    public void quickStationaryContactIsStillATap() {
        List<TouchSample> trace = SwipeTrace.straight(300f, 180f, 302f, 181f, 90L, 1);
        assertEquals(Gesture.TAP, SwipeTrace.play(detector, trace));
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :gesture-core:test`
Expected: `contactBetweenTapAndLongPressDurationsIsNotAGesture` and
`shortDownwardDragIsNotATap` FAIL, both reporting `TAP`.
`quickStationaryContactIsStillATap` should already pass.

- [ ] **Step 3: Tighten the tap constants**

In `GlassGestureDetector.java`, replace the `TAP_SLOP` declaration and add a new bound:

```java
    /**
     * Native units of travel below which a contact is stationary.
     *
     * <p>Deliberately tight. The pad is 1366 units wide but only 187 tall, so a
     * generous slop that is negligible horizontally consumes a large fraction of
     * the vertical range and swallows genuine downward swipes.
     */
    private static final float TAP_SLOP = 25.0f;
    /** Contacts longer than this are too slow to be a tap. */
    private static final long TAP_MAX_MS = 250L;
```

- [ ] **Step 4: Make the stationary branch reject ambiguity**

In `classify()`, replace the whole `if (distance < TAP_SLOP) { ... }` block with:

```java
        if (distance < TAP_SLOP) {
            if (multiTouch) {
                // Two-finger taps carry no meaning in this design.
                return Gesture.NONE;
            }
            if (durationMs >= LONG_PRESS_MS) {
                return Gesture.LONG_PRESS;
            }
            if (durationMs <= TAP_MAX_MS) {
                return Gesture.TAP;
            }
            // Between the two: too slow to be a tap, too quick to be a long
            // press. Ambiguous input must be a no-op, never an app launch.
            return Gesture.NONE;
        }
```

Change nothing else in `classify()`.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :gesture-core:test`
Expected: PASS, 21 tests — 4 in `TouchpadGeometryTest`, 17 in `GlassGestureDetectorTest`.

#### Defect 2 — the system steals downward swipes

Observed: swiping down opened the notification shade instead of reaching the app.

Root cause: the `StatusBar` window holds `touchableRegion=[0,0][640,38]`. The pad's 187
vertical units map onto 360 screen px, so the top ~20 native units of the strip fall
inside that region and Android routes the swipe to the shade. `LauncherActivity` sets
`SYSTEM_UI_FLAG_LOW_PROFILE`, which only dims navigation icons and does not prevent this.
`SYSTEM_UI_FLAG_IMMERSIVE_STICKY` is the flag that keeps edge swipes with the app.

- [ ] **Step 6: Replace the inert flag with immersive mode**

In `LauncherActivity.java`, delete any existing `setSystemUiVisibility` call and add:

```java
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveMode();
        }
    }

    /**
     * Keeps edge swipes with this activity instead of the system.
     *
     * <p>The StatusBar window claims the top 38 px of the display as touchable.
     * The touchpad's 187 vertical units are mapped onto 360 px, so the top ~20
     * units of the strip land in that region and downward swipes were being
     * routed to the notification shade. IMMERSIVE_STICKY suppresses that.
     */
    private void applyImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
```

Call `applyImmersiveMode()` at the end of `onCreate` as well, and add
`import android.view.View;` if absent.

- [ ] **Step 7: Verify on the device**

```bash
./gradlew :app:installDebug
./tools/platform-tools/adb shell am start -n dev.erinlkolp.glasslauncher/.LauncherActivity
./tools/platform-tools/adb shell input swipe 320 20 320 300 250
./tools/platform-tools/adb shell dumpsys window windows | grep -E "StatusBar|mCurrentFocus"
```

Expected: focus remains on `LauncherActivity`; the shade does not open. A synthetic swipe
is a weaker check than a real finger here, since the interception happens in the window
manager — flag in the report that final confirmation needs the device operator.

- [ ] **Step 8: Commit**

```bash
git add gesture-core/ app/
git commit -m "fix: reject ambiguous gestures and claim edge swipes from the system"
```

---

## Phase 3

Both items below were deferred until the launcher and daemon were proven on hardware.
That condition was met on 2026-07-30: the operator confirmed all seven gestures, and a
physical two-finger downward swipe from inside another app produced
`gestured: two-finger down -> home` with `mResumedActivity` becoming
`com.android.launcher2.Launcher`.

### Task 14: Daemon survives reboot

**Files:**
- Create: `scripts/install-boot-hook.sh`
- Modify: `README.md` (add an uninstall note)

**Interfaces:**
- Consumes: `daemon/build/libs/gestured.jar` from Task 11.
- Produces: a daemon that starts automatically at every boot.

**The hook, verified present on this device.** `/init.rc` line 593 declares:

```
service flash_recovery /system/bin/install-recovery.sh
    class main
    seclabel u:r:install_recovery:s0
    oneshot
```

The script it names **does not exist**, so the service fails silently at every boot. Creating
it makes init execute it as root at boot — no `init.rc` edit, no boot-image repacking.
SELinux is Permissive, so the `install_recovery` seclabel imposes no restriction.

Two constraints follow from the service definition. `class main` starts early, possibly
before `system_server`, and `app_process` needs a live runtime — so the script must wait
for `sys.boot_completed`. And `oneshot` means init waits for the script to exit, so it must
background its work rather than blocking.

- [ ] **Step 1: Move the jar somewhere durable**

`/data/local/tmp` survives reboot but is a scratch location. Use `/system/bin` alongside
the script so the daemon and its launcher live together.

- [ ] **Step 2: Write `scripts/install-boot-hook.sh`**

```bash
#!/usr/bin/env bash
# Installs the gesture daemon as a boot service on Google Glass.
# Requires an eng/userdebug build where `adb shell` is already root.
set -euo pipefail
cd "$(dirname "$0")/.."
ADB=./tools/platform-tools/adb

$ADB shell 'mount -o rw,remount /system' 
$ADB push daemon/build/libs/gestured.jar /data/local/tmp/gestured.jar
$ADB shell 'cp /data/local/tmp/gestured.jar /system/bin/gestured.jar'

$ADB shell 'cat > /system/bin/install-recovery.sh <<EOF
#!/system/bin/sh
# Started by init (service flash_recovery, class main, oneshot).
# Backgrounds immediately so init is not held, and waits for the framework
# because app_process needs a live runtime.
(
  while [ "\$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
  done
  export CLASSPATH=/system/bin/gestured.jar
  exec app_process /system/bin dev.erinlkolp.glasslauncher.daemon.Main
) &
EOF'

$ADB shell 'chmod 755 /system/bin/install-recovery.sh'
$ADB shell 'chmod 644 /system/bin/gestured.jar'
$ADB shell 'mount -o ro,remount /system'
echo "Installed. Reboot with: $ADB reboot"
```

```bash
chmod +x scripts/install-boot-hook.sh
```

- [ ] **Step 3: Install and verify before rebooting**

```bash
./scripts/install-boot-hook.sh
./tools/platform-tools/adb shell 'ls -l /system/bin/install-recovery.sh /system/bin/gestured.jar'
./tools/platform-tools/adb shell 'cat /system/bin/install-recovery.sh'
```

Confirm the script is mode 755 and its contents are correct. A malformed script here runs
at every boot as root, so read it before rebooting.

- [ ] **Step 4: Reboot and confirm**

```bash
./tools/platform-tools/adb reboot
./tools/platform-tools/adb wait-for-device
sleep 45
./tools/platform-tools/adb shell 'ps | grep app_process'
```

Expected: an `app_process` running as root. Then have the operator perform a two-finger
downward swipe from inside any app and confirm it goes home, with no daemon started
manually.

- [ ] **Step 5: Document removal**

Add to `README.md`: removing the hook is
`adb shell 'mount -o rw,remount /system && rm /system/bin/install-recovery.sh /system/bin/gestured.jar && mount -o ro,remount /system'`.

- [ ] **Step 6: Commit**

```bash
git add scripts/install-boot-hook.sh README.md
git commit -m "feat: install the gesture daemon as a boot service"
```

---

### Task 15: Launcher becomes the home screen

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: the working launcher from Tasks 8 and 13.
- Produces: `LauncherActivity` as a HOME candidate.

Do this **after** Task 14, so the daemon's global go-home is already boot-persistent before
the launcher becomes the thing HOME resolves to.

- [ ] **Step 1: Add the HOME filter**

In `app/src/main/AndroidManifest.xml`, add a second intent-filter to `LauncherActivity`,
leaving the existing `MAIN`/`LAUNCHER` filter untouched:

```xml
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
```

- [ ] **Step 2: Install and choose**

```bash
./gradlew :app:installDebug
./tools/platform-tools/adb shell input keyevent KEYCODE_HOME
./tools/platform-tools/adb shell dumpsys activity activities | grep mResumedActivity
```

Android presents a chooser the first time. The operator selects the Glass launcher.
`com.android.launcher2.Launcher` stays installed as a fallback — do NOT uninstall it.

- [ ] **Step 3: Verify the escape route still works**

Critical check, because the launcher is now what HOME resolves to. Launch another app, then
have the operator perform the two-finger downward swipe. Confirm `mResumedActivity` becomes
`dev.erinlkolp.glasslauncher/.LauncherActivity`.

If the launcher ever misbehaves, recovery is
`adb shell cmd package set-home-activity com.android.launcher/com.android.launcher2.Launcher`,
or on API 22 clearing defaults via
`adb shell pm clear com.android.launcher` and re-choosing. Verify one of these works
*before* relying on the new home screen.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat(app): declare LauncherActivity as a HOME candidate"
```
