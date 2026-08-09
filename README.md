# Glass Gesture Launcher

A touchpad-driven application launcher for a Google Glass Explorer Edition unit
reflashed with a community AOSP 5.1.1 build, plus a small root daemon that supplies a
global "go home" gesture usable from inside any foreground app.

This does **not** use the Glass Development Kit (GDK). The device runs stock AOSP —
there is no Glass system layer, timeline, card metaphor, or Glass system service —
so there is nothing for the GDK to talk to. The touchpad is recognized by the framework
as an ordinary `SOURCE_TOUCHSCREEN`, so the launcher is a plain `Activity` reading
`MotionEvent`s, plus a separate root process reading raw evdev for the parts of the
gesture surface an app can't observe when it isn't foregrounded.

## Hardware it targets

| Property | Value |
|---|---|
| `ro.product.device` | `glass-1` |
| SoC | OMAP4430 |
| Android | 5.1.1, API 22 |
| Build type | `eng` |
| Display | 640 × 360 px, density 240 |
| Touchpad | `sensor00fn11`, reports `SOURCE_TOUCHSCREEN`, native geometry 1366 × 187 |

These are measured facts about the specific unit this project was built against, not
assumptions about Glass hardware in general.

## The anisotropy problem

The touchpad's native surface is 1366 × 187 units, but the framework linearly rescales
that onto the 640 × 360 display. The two aspect ratios don't match, so screen-space
coordinates amplify vertical motion roughly 4.11× relative to horizontal. Because of
this, every gesture measurement in this project is done in touchpad-native units, never
in screen pixels. See [the design spec, §3](docs/superpowers/specs/2026-07-30-glass-gesture-launcher-design.md#3-the-anisotropy-problem)
for the full derivation.

## Build

Prerequisites:
- JDK 21
- An Android SDK with platform `android-34` and
  build-tools `34.0.0`. Point `ANDROID_HOME` at it, or set `sdk.dir` in `local.properties`.
  `tools/` is gitignored and not committed — see
  [the plan's Task 1](docs/superpowers/plans/2026-07-30-glass-gesture-launcher.md) for
  how it was bootstrapped.

```bash
./gradlew test
./gradlew :app:installDebug
```

`./gradlew test` runs all unit tests across the three modules (62 total: 21 in
`gesture-core`, 25 in `app`, 16 in `daemon`). `:app:installDebug` builds and installs the
launcher APK onto a connected device via the Android Gradle Plugin (no `adb` on `PATH`
required for this one — AGP drives the install itself).

## Tiles

The card list is not only apps. The first card is always the Wi-Fi toggle; the installed
apps follow it in alphabetical order. Tapping the Wi-Fi card flips the radio and the
label tracks the change, including the `Turning on…` / `Turning off…` states, and it also
reflects changes made by other apps while the launcher is on screen.

State is shown as text rather than colour on purpose: the see-through display washes out
anything that is not pure black or pure white.

This works because the device is API 22 — `WifiManager.setWifiEnabled()` was not
restricted until API 29, so no trip out to Settings is needed.

## Gesture reference

In-app gestures, handled by `LauncherActivity`:

| Gesture | Action |
|---|---|
| Swipe forward (one finger) | Select next app |
| Swipe backward (one finger) | Select previous app |
| Two-finger swipe forward | Jump ahead 10 entries |
| Two-finger swipe backward | Jump back 10 entries |
| Tap | Activate the selected tile — launch an app, or toggle Wi-Fi |
| Long press | Toggle detail view (package + activity name) |
| Swipe down | Close detail view if open, otherwise no-op |
| Camera button | Recenter selection to the first tile (the Wi-Fi toggle) |

Handled globally by the root daemon, independent of which app is foregrounded:

| Gesture | Action |
|---|---|
| Two-finger swipe down | Go to the home screen (`com.android.launcher2.Launcher`) |

## Running the daemon

The daemon is not installed as an app; it's pushed to `/data/local/tmp` and run under
`app_process` as root. Build, push, and start it with:

```bash
./run-daemon.sh
```

On success it prints:

```
gestured: watching /dev/input/event3
```

and stays running, printing `gestured: two-finger down -> home` each time it recognizes
the gesture. Run this way, it **does not survive a reboot** — it must be started again by
re-running `./run-daemon.sh` after every power cycle. This is the quickest way to try the
daemon out or iterate on it during development.

`adb` is expected to be on `PATH`; `run-daemon.sh` invokes it directly.

### Boot persistence

For the daemon to start automatically at every boot (no manual `./run-daemon.sh` needed),
install it as a boot hook:

```bash
./scripts/install-boot-hook.sh
```

This copies `daemon/build/libs/gestured.jar` to `/system/bin/gestured.jar` and writes
`/system/bin/install-recovery.sh`, a script named for and executed by the pre-existing
(and otherwise unused) `flash_recovery` service in `/init.rc`. That service is `class
main`/`oneshot`, so the script backgrounds itself immediately and polls
`sys.boot_completed` before launching `app_process`, so init is never held up and the
daemon isn't started before the framework is ready. It requires an `eng`/`userdebug`
build where `adb shell` is already root, same as manual daemon operation.

The boot-started daemon's stdout/stderr (the same `gestured: watching ...` and
`gestured: two-finger down -> home` lines `./run-daemon.sh` prints) are appended to
`/data/local/tmp/gestured.log` rather than shown anywhere, since there's no console
attached at boot; check that file to confirm the daemon started, or to see why it
didn't (e.g. a missing/corrupt `/system/bin/gestured.jar` is logged there instead of
failing silently).

To remove the boot hook (the daemon can still be run manually with `./run-daemon.sh`
afterward):

```bash
adb shell 'mount -o rw,remount /system && rm /system/bin/install-recovery.sh /system/bin/gestured.jar && mount -o ro,remount /system && rm -f /data/local/tmp/gestured.log'
```

## Known limitations

- The daemon does not survive reboot unless installed via `./scripts/install-boot-hook.sh`
  (see "Boot persistence" above); a daemon started with `./run-daemon.sh` alone must be
  restarted manually after every power cycle.
- The gesture is observed but not consumed: the daemon deliberately never issues
  `EVIOCGRAB` (an exclusive grab would block input system-wide), so the foreground app
  also sees the same swipe that triggered the go-home action.
- The launcher does not declare `android.intent.category.HOME`, so
  `com.android.launcher2.Launcher` remains the system home screen; this app is only
  reachable by launching it explicitly.
- Long app labels clip at the screen edge rather than truncating with an ellipsis.
- The camera-button recenter resets the selected app but does not close an open detail
  view.
- The daemon requires an `eng`/`userdebug` build where `adb shell` is already root; it
  will not work on a locked/user build.

## Layout

- `gesture-core/` — the gesture recognition algorithm (`GlassGestureDetector`,
  `TouchpadGeometry`, `GestureOrientation`, etc.). Deliberately has zero Android
  imports, which keeps it unit-testable on a plain JVM and lets both `app` and `daemon`
  share the exact same detector.
- `app/` — the Android launcher UI: `LauncherActivity`, the app card view, and the
  installed-apps repository.
- `daemon/` — the root process that resolves the touchpad's evdev node by name via
  `EvdevDeviceLocator` (tolerating renumbering across boots) and triggers the global
  go-home gesture; dexed into a standalone jar runnable by `app_process`.

## Further reading

- [Design spec](docs/superpowers/specs/2026-07-30-glass-gesture-launcher-design.md)
- [Implementation plan](docs/superpowers/plans/2026-07-30-glass-gesture-launcher.md)
