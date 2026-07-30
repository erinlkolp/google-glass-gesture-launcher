# Glass Gesture Launcher — Design

**Date:** 2026-07-30
**Target:** Google Glass Explorer Edition hardware running community AOSP 5.1.1
**Status:** Approved, ready for implementation planning

---

## 1. Context

The device is a Google Glass Explorer Edition unit reflashed with a community AOSP
5.1.1 build. This is materially different from stock Glass: there is no Glass system
layer, no timeline, no card metaphor, and no voice-trigger requirement. The Glass
Development Kit (GDK) is therefore not merely deprecated here — it is inapplicable,
because there is no Glass system service for it to talk to.

Everything below rests on measurements taken from the device on 2026-07-30, not on
assumptions about Glass hardware. The raw probe output is preserved in the session
scratchpad.

### 1.1 Verified device facts

| Property | Value |
|---|---|
| `ro.product.model` / `device` | `Glass 1` / `glass-1` |
| SoC | OMAP4430 (32-bit ARMv7) |
| Android | 5.1.1, API 22, build `LMY49J` |
| Build type | `eng`, `test-keys` — `adb shell` runs as uid 0 |
| SELinux | **Permissive** |
| Display | 640 × 360 px, density 240 → **320 × 180 dp** |
| Status bar | 38 px tall (reclaimed by going fullscreen) |
| Current HOME | `com.android.launcher2.Launcher` |

### 1.2 Verified touchpad facts

The touchpad is `sensor00fn11` on `/dev/input/event3`, sysfs `/devices/touchpad`.

- Kernel reports `INPUT_PROP_DIRECT` with `ABS_MT_*` axes and **no `.idc` file**
  (`/system/usr/idc/` contains only the two stock emulator `qwerty` files). AOSP's
  InputReader therefore falls back to defaults and classifies it as a **touchscreen**.
- `dumpsys input` confirms: `Sources: 0x80001002` (`SOURCE_TOUCHSCREEN | SOURCE_SWITCH`),
  `Touch Input Mapper → DeviceType: touchScreen`.
- **Consequence: events arrive through ordinary `View.onTouchEvent(MotionEvent)`.**
  No GDK, no `onGenericMotionEvent`, no raw evdev required for in-app gestures.
- Native axis ranges are `ABS_MT_POSITION_X: 0..1366`, `ABS_MT_POSITION_Y: 0..187`.
  The framework has already rescaled these onto the display, so `MotionEvent`
  reports `X: 0..639`, `Y: 0..359`.
- Multitouch: `ABS_MT_TRACKING_ID` min 1 max 10; `ABS_MT_PRESSURE` 0..255.
  **No `ABS_MT_SLOT`**, which indicates MT protocol A rather than B (see §7.1).

### 1.3 Other input hardware

| Device | Node | Capability | Use |
|---|---|---|---|
| `gpio-keys` | `event5` | `KEY_CAMERA` | Physical top button → `KEYCODE_CAMERA` |
| `twl6030_pwrbutton` | `event0` | `KEY_POWER` | Not used |
| `ltr506_ps` | `event2` | `ABS_DISTANCE` 0..4095 | Proximity / wink sensor — out of scope |
| `ltr506_als` | `event1` | `ABS_MISC` | Ambient light — out of scope |

---

## 2. Goals and non-goals

### Goals

1. A touchpad-driven application launcher usable on a 320 × 180 dp see-through display.
2. A gesture recognition layer correct for this device's unusual input geometry.
3. A global **two-finger swipe down → go to home screen** gesture that works from
   inside any application.
4. A diagnostic overlay that makes touchpad behaviour observable during development.

### Non-goals

- Head gestures, wink detection, and camera-based hand tracking. The proximity and
  motion sensors exist but are explicitly out of scope.
- Replacing `com.android.launcher2.Launcher` as the system HOME activity in the first
  build (see §6, phasing).
- Reboot persistence for the root daemon (deferred; see §7.4).
- Any use of the GDK.

---

## 3. The anisotropy problem

This is the single most important correctness detail in the design.

The touchpad's native surface is **1366 × 187** — an aspect ratio of roughly 7.3:1.
The framework maps this linearly onto a **640 × 360** display, an aspect ratio of 16:9.
The two do not match, so the mapping is anisotropic:

```
horizontal: 640 / 1366 = 0.4685 screen px per native unit
vertical:   360 /  187 = 1.9251 screen px per native unit
```

Assuming the pad's native units are physically square, **vertical motion is amplified
by 1.9251 / 0.4685 ≈ 4.11×** relative to horizontal in the coordinates an app receives.

A stock `android.view.GestureDetector` with symmetric thresholds would therefore
misclassify constantly: a forward swipe travelling 200 px with 15 px of vertical drift
is 4.3° off-axis in physical reality, but appears **16.4° off-axis** in screen
coordinates. Sloppy forward swipes would register as "down".

**Resolution:** `GlassGestureDetector` converts incoming screen coordinates back into
native units before performing any distance, angle, or velocity computation:

```java
nx = x * (1366.0 / 640.0);   // 2.134375
ny = y * ( 187.0 / 360.0);   // 0.519444
```

All thresholds are expressed in native units, which correspond to real distance on the
temple. These two constants are the only place the device geometry is encoded, and they
are derived from values read off the device rather than hardcoded guesses.

---

## 4. Architecture

Three Gradle modules. The split exists so that the gesture algorithm can be shared
between two very different hosts and tested without a device.

```
gesture-core/   java-library   pure JVM, zero Android imports
    ├── TouchSample            (x, y, timeMs, pointerCount) value type
    ├── Gesture                enum of recognised gestures
    └── GlassGestureDetector   the algorithm, incl. anisotropy correction

app/            android app
    ├── LauncherActivity       fullscreen host
    ├── MotionEventAdapter     MotionEvent → TouchSample
    ├── AppRepository          PackageManager queries + package-change broadcasts
    ├── AppCardView            rendering
    └── GestureDebugOverlay    live diagnostics

daemon/         java-library   depends on gesture-core; d8'd into a dex jar
    ├── EvdevReader            /dev/input/event3 → TouchSample
    └── Main                   entry point, runs as root via app_process
```

### 4.1 `gesture-core`

Deliberately has no Android dependency at all. It consumes `TouchSample` values and
emits `Gesture` values. It does not know whether those samples came from a
`MotionEvent` or from raw kernel events, which is precisely what allows the in-app
path and the root daemon to share one recogniser and one set of tuned thresholds.

### 4.2 `app`

A normal launchable application. Fullscreen (the 38 px status bar is too expensive on
a 180 dp-tall display). Rendering targets a see-through optical display, where
mid-tones wash out badly in ambient light:

- Pure black background, white text, no gradients or subtle fills.
- Large type; roughly one app entry visible at a time, which suits linear swiping.
- No AndroidX. Plain framework `Activity` and custom `View` subclasses.

`AppRepository` queries `PackageManager.queryIntentActivities` for
`ACTION_MAIN` + `CATEGORY_LAUNCHER`, sorts by label, caches the result, and refreshes on
`ACTION_PACKAGE_ADDED` / `_REMOVED`. Note that this eng build carries development
packages (`com.android.development`, etc.), so the list will be longer than on a
consumer device — 162 activities answer bare `ACTION_MAIN`, though the
`CATEGORY_LAUNCHER` subset is considerably smaller.

### 4.3 `daemon`

**Why it must exist.** An application receives touch events only while focused. Once
another app is foregrounded, the launcher sees nothing, so a global gesture cannot be
implemented in the app. The available workarounds on API 22 are all unacceptable:

- `AccessibilityService` can only detect touch gestures with
  `FLAG_REQUEST_TOUCH_EXPLORATION_MODE`, which converts the entire device to
  TalkBack-style interaction and breaks touch in every other application.
- A system overlay with `FLAG_WATCH_OUTSIDE_TOUCH` receives `ACTION_OUTSIDE` without
  coordinates, which cannot track a two-finger swipe.

**Why root is required, and why the app cannot supply it.** Measured on the device:

```
/system/xbin/su    -rwsr-x---  root shell     mode 4750 — group 'shell' only
/dev/input/event3  crw-rw----  root input     mode 0660 — group 'input' only
```

`su` exists but is the stock eng-build binary: executable by uid 0 or gid `shell`.
An application (uid ~10050) is in neither group, so it can neither elevate nor open the
touchpad node. SELinux is Permissive, so this is purely a Unix-permission boundary —
but it is a real one, and it is not crossable from application code.

**Launch mechanism.** The daemon runs via `app_process`, the same mechanism the
platform's own `am` and `pm` commands use. Verified against this ROM by reading
`/system/bin/am`:

```sh
export CLASSPATH=$base/framework/am.jar
exec app_process $base/bin com.android.commands.am.Am "$@"
```

The daemon is therefore invoked as:

```sh
CLASSPATH=/data/local/tmp/gestured.jar \
    app_process /system/bin com.example.glasslauncher.daemon.Main
```

`app_process` is a symlink to `app_process32`, consistent with the 32-bit SoC.

**Behaviour.** `EvdevReader` opens `/dev/input/event3` **non-exclusively** — it does
not issue `EVIOCGRAB`, which would block input system-wide. Consequence: the gesture is
observed but not consumed, so the foreground application also sees the swipe. This is
accepted; a two-finger downward swipe is rarely meaningful to other apps.

The node is resolved by name (`sensor00fn11`) via `/proc/bus/input/devices` rather than
hardcoding `event3`, since event numbering is not guaranteed stable across boots.

On recognising the gesture, the daemon executes:

```sh
am start -a android.intent.action.MAIN -c android.intent.category.HOME
```

Verified working from the shell. The foreground app is **left running in the
background** — this is Home-button behaviour, not termination. No `force-stop`.

`struct input_event` on 32-bit ARM is 16 bytes: `{ __kernel_time_t tv_sec (4),
suseconds_t tv_usec (4), __u16 type, __u16 code, __s32 value }`.

---

## 5. Gesture map

| Gesture | Scope | Action |
|---|---|---|
| One-finger swipe forward / backward | In app | Next / previous app entry |
| One-finger tap | In app | Launch selected app |
| One-finger swipe down | In app | Dismiss detail view if open, otherwise `finish()` the launcher |
| One-finger long press | In app | App detail (package, version) |
| Two-finger horizontal swipe | In app | Jump 10 entries |
| **Two-finger swipe down** | **Global (daemon)** | **Go to home screen** |
| `KEYCODE_CAMERA` (top button) | In app | Recenter to top of list |

Two-finger gestures are discriminated by direction: horizontal means paging, downward
means home. Both the in-app recogniser and the daemon observe two-pointer input
simultaneously when the launcher itself is foregrounded, so each must check direction
before acting. A two-finger downward swipe performed inside the launcher will therefore
be handled by the daemon (go home) and ignored by the app — which is the correct and
intended outcome, since the launcher is already the home-adjacent surface.

---

## 6. Phasing

**Phase 1 — in-app gestures.** Build `gesture-core` with its unit tests, then the app
as an *ordinary launchable application*, not as HOME. Use the debug overlay to resolve
touchpad orientation (§7.2) and tune thresholds.

The app deliberately does **not** declare `CATEGORY_HOME` in this phase.
`com.android.launcher2.Launcher` remains the system home screen throughout, so a
gesture bug can never leave the device unusable — which matters acutely on hardware
whose only input surface is the thing being debugged.

**Phase 2 — the daemon.** Build `daemon`, push the dex jar to `/data/local/tmp`, and
start it manually over `adb`. Confirm the global two-finger-down gesture works from
inside a third-party app.

**Phase 3 — promotion.** Only once both are proven: optionally add `CATEGORY_HOME` to
`LauncherActivity`, and optionally add reboot persistence for the daemon.

---

## 7. Open questions and risks

Each is cheap to settle empirically during Phase 1; none blocks the design.

### 7.1 MT protocol A vs B
The capability dump lists `ABS_MT_TRACKING_ID` but **no `ABS_MT_SLOT`**, indicating
protocol A (contacts delimited by `SYN_MT_REPORT`) rather than protocol B (slot-based).
These parse differently. Confirm by observing `getevent -l` during a real two-finger
touch before writing `EvdevReader`. Affects the daemon only — the in-app path receives
already-assembled `MotionEvent` pointers.

### 7.2 Touchpad orientation
It is not known which physical end of the temple corresponds to X = 0. The debug
overlay resolves this immediately with one finger swipe. Affects only the sign of the
forward/backward mapping.

### 7.3 Dex-jar packaging
Producing a dex'd jar from a `java-library` module requires a custom Gradle task
invoking `d8` from build-tools. Standard practice, but the one piece of build
configuration that is not boilerplate.

### 7.4 Reboot persistence (deferred)
API 22 predates `/system/etc/init/*.rc`. The conventional hook is appending to
`/system/etc/install-recovery.sh`, whose existence on this ROM is unverified.
`/system` is mounted `ro` but is remountable given root and Permissive SELinux.
Out of scope for the first build.

### 7.5 Toolchain
Neither the Android SDK nor Gradle is installed on the development machine; only
JDK 21 and the `platform-tools` downloaded during the probe. First implementation task
is installing `cmdline-tools`, `platforms;android-34`, and `build-tools;34.0.0`.

---

## 8. Build and test strategy

**Toolchain.** AGP 8.7 / Gradle 8.9 (JDK 21 has been supported since AGP 8.5).
`compileSdk 34`, `minSdk 22`, `targetSdk 22`. `targetSdk` deliberately matches the
device rather than being maximised — there is no Play Store requirement here, and
matching avoids compatibility shims. No AndroidX dependencies.

**Unit tests (JVM, no device).** `gesture-core` is fully testable with synthetic
`TouchSample` traces. This is where threshold tuning stops being guesswork. Tests must
include, at minimum:

- A 200 px horizontal swipe with 15 px vertical drift classifies as **horizontal**,
  not down. (Directly asserts the §3 correction.)
- A genuine downward swipe classifies as down.
- Two-pointer downward motion classifies as the global home gesture, not as paging.
- Taps are distinguished from short swipes by distance and duration.

`EvdevReader`'s record parser is likewise testable against synthetic byte arrays.

**Manual, on device.** Rendering legibility, app launching, and the daemon end-to-end.
The debug overlay exists to make this observable rather than guessed at.

**Deployment loop.**

```sh
./gradlew :app:installDebug
./gradlew :daemon:dexJar && adb push daemon/build/gestured.jar /data/local/tmp/
adb shell CLASSPATH=/data/local/tmp/gestured.jar \
    app_process /system/bin com.example.glasslauncher.daemon.Main
```
