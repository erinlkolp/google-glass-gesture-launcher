# Wi-Fi Toggle Tile — Design

**Date:** 2026-08-08
**Target:** Google Glass Explorer Edition hardware running community AOSP 5.1.1
**Status:** Approved, ready for implementation planning
**Builds on:** [`2026-07-30-glass-gesture-launcher-design.md`](2026-07-30-glass-gesture-launcher-design.md)

---

## 1. Context

The launcher currently presents a flat, alphabetically sorted list of launchable
activities. Every card is an `AppEntry` (label, package, activity), and `TAP` always
means the same thing: fire a component `Intent`.

This change adds a card that toggles the Wi-Fi radio. It is the first entry in the
list that is **not** an app, so it breaks both of the assumptions above.

### 1.1 Verified device facts

Re-confirmed on the physical device on 2026-08-08, not assumed:

| Property | Value |
|---|---|
| `adb devices` | `0123456789ABCDEF`, `model:Glass_1` |
| Android | 5.1.1, API 22, build `LMY49J` |
| Wi-Fi at time of probe | disabled (`settings get global wifi_on` → `0`) |
| Wi-Fi service | present; `dumpsys wifi` reports `Wi-Fi is disabled`, `WifiController` state machine live |

The API level is the load-bearing fact. `WifiManager.setWifiEnabled()` was not
restricted until **API 29**; at API 22 an ordinary app holding `CHANGE_WIFI_STATE`
can still flip the radio directly. A real in-app toggle is therefore possible, and
we do not need to punt the user out to `com.android.settings`.

`targetSdk` is 22, so both required permissions are granted at install time and no
runtime permission flow is needed.

---

## 2. Goals and non-goals

### Goals

- A card, pinned as the first entry, that reports current Wi-Fi state and toggles it on `TAP`.
- The label tracks state live, including the transitional `ENABLING` / `DISABLING`
  states, and reflects changes made by other apps while the launcher is visible.
- A tile abstraction that makes future non-app tiles additive rather than another
  branch in a growing switch.
- Pure, device-free unit tests for all the logic that does not touch `WifiManager`.

### Non-goals

- Network selection, SSID display, signal strength, or any connection management.
  This toggles the radio; that is all.
- Bluetooth, brightness, or other toggles. The abstraction must not *obstruct* them,
  but none are built here.
- Any change to how apps are discovered or sorted. `AppEntry` and `AppEntrySorter`
  are untouched.
- Tethering / AP mode.

---

## 3. Placement

The Wi-Fi tile is **pinned first**, ahead of the alphabetical app list.

The deciding factor is the existing `KEYCODE_CAMERA` binding: `AppCardView.recenter()`
already jumps the selection to index 0. Pinning the toggle there makes it reachable
from anywhere in the list as *camera button, then tap* — two inputs, no swiping,
without adding a new gesture.

The cost, accepted deliberately: `recenter()` no longer lands on an app. Sorting the
tile alphabetically under "W" was rejected because it would sit near the end of the
list behind many swipes.

---

## 4. Architecture

Six new files in the `app` module. Three are pure and carry the tests; three are thin
Android glue. This follows the precedent already set by `AppEntrySorter` — a pure
class living inside `app` with a plain JUnit test — rather than introducing a module.

| File | Kind | Purpose |
|---|---|---|
| `Tile.java` | interface | `label()`, `detailLines()`, `key()`, `activate(Context)` |
| `AppTile.java` | glue | Wraps an `AppEntry`; `activate()` is today's launch `Intent` |
| `WifiState.java` | **pure** | `fromCode(int)` → enum + display label |
| `WifiTile.java` | glue | Reads and writes `WifiManager` |
| `TileListBuilder.java` | **pure** | Pins tiles ahead of the app list |
| `TileSelection.java` | **pure** | Owns the entry list + selected index, and the diff |

### 4.0 Why `TileSelection` exists

`app/src/test` is a plain JVM source set — there is no Robolectric and no
`androidTest` directory. `AppCardView extends View`, so it cannot be instantiated in a
unit test at all: the stub `android.jar` throws on the `View` constructor.

Today that means the selection logic inside `AppCardView` — the list diff, the index
clamping in `move()`, and `recenter()` — is **entirely untested**. Since this change
has to rewrite that diff anyway (from `activityName` to `key()`), the state moves out
into a pure `TileSelection` and `AppCardView` is left doing nothing but drawing.

This is a targeted extraction of code this change already has to touch, not a general
refactor of the view layer. It is what makes risk §10.2 testable.

```java
final class TileSelection {          // pure: no android imports
    void setTiles(List<Tile> tiles);      // preserves index iff keys match
    Tile selected();                      // null when empty
    void move(int delta);                 // clamped to bounds
    void recenter();                      // index 0
    int selectedIndex();
    int size();
    boolean isEmpty();
}
```

Note that a pure test *may* still reference Android types in a signature — a fake
`Tile` implementing `activate(Context)` compiles and runs fine against the stub jar,
because the parameter type is never resolved at runtime. What it may not do is *call*
stubbed framework behaviour. `TileSelection` never does.

### 4.1 `Tile`

```java
interface Tile {
    String label();              // drawn large and centred
    List<String> detailLines();  // shown on LONG_PRESS
    String key();                // stable identity for the list diff
    void activate(Context c);    // what TAP does
}
```

`key()` exists because `AppCardView.setEntries()` currently diffs incoming lists by
`activityName` to decide whether to preserve the selection index. A Wi-Fi tile has no
activity name, so the diff moves to `key()`: `"app:<package>/<activity>"` for `AppTile`,
`"action:wifi"` for `WifiTile`.

`detailLines()` generalises the two hardcoded package/activity lines in `onDraw` into
a loop. `AppTile` returns its package and activity, preserving today's behaviour
exactly; `WifiTile` returns a short description and its raw state.

### 4.2 `WifiState`

A pure enum — `DISABLING`, `OFF`, `ENABLING`, `ON`, `UNKNOWN`, `UNAVAILABLE` — with a
`fromCode(int)` mapping over `WifiManager`'s integer constants and a display label
for each. The naming tracks `WifiManager`'s own vocabulary rather than inventing
`TURNING_ON`/`TURNING_OFF` synonyms. `UNAVAILABLE` has no corresponding `WifiManager`
code; it is constructed directly by `WifiTile` when `getSystemService(WIFI_SERVICE)`
returns null (§8). It takes a raw `int` rather than a `WifiManager`, which is what
keeps it free of Android imports and unit-testable on the JVM. Out-of-range codes map
to `UNKNOWN` rather than throwing.

### 4.3 `WifiTile`

Holds a `WifiManager`. `label()` renders `"Wi-Fi: " + WifiState.fromCode(mgr.getWifiState()).label()`.

`activate()` reads current state and:

- `OFF` → `setWifiEnabled(true)`
- `ON` → `setWifiEnabled(false)`
- **anything else** → no-op

The guard is written fail-closed — it proceeds only for `ON` and `OFF` — rather than
testing for the transitional states. That covers `ENABLING`/`DISABLING`, so an impatient
double-tap cannot queue an enable immediately followed by a disable, and it also covers
`UNKNOWN` and `UNAVAILABLE`. The `UNAVAILABLE` case matters: that is the null
`WifiManager` state, and a guard that let it through would dereference null on the very
next line.

### 4.4 `TileListBuilder`

`build(List<Tile> pinned, List<AppEntry> apps)` → `List<Tile>`: the pinned tiles in
order, then each app wrapped in an `AppTile`, order preserved. Pure, and the reason
placement is testable without a device.

---

## 5. Data flow

```
AppRepository.load() ──> List<AppEntry> ──┐
                                          ├─> TileListBuilder.build(pinned, apps)
List<Tile> pinned = [wifiTile] ───────────┘         │
                                                    v
                                         AppCardView.setEntries(List<Tile>)
                                                    │  (delegates to TileSelection)
   TAP ──> cardView.selected().activate(this) ──────┘
                                                    ^
   WIFI_STATE_CHANGED broadcast ──> cardView.invalidate()
```

`WifiTile` is constructed once in `onCreate` and reused, so its identity is stable
across the `onResume` reload; `label()` is evaluated at draw time, which is what makes
a bare `invalidate()` sufficient to reflect a state change.

### 5.1 Changes to existing files

- **`AppCardView`** — delegates list and selection state to `TileSelection`; `onDraw`
  calls `label()` and loops `detailLines()`. Keeps its existing public surface
  (`setEntries`, `selected`, `move`, `recenter`, `isShowingDetail`,
  `setShowingDetail`) so `LauncherActivity`'s gesture handling is unchanged apart
  from `TAP`. No behaviour changes beyond the new key-based diff.
- **`LauncherActivity`** — `launchSelected()` dissolves into `AppTile.activate()`;
  `case TAP` becomes `cardView.selected().activate(this)` with a null guard. Gains the
  Wi-Fi broadcast receiver.
- **`AndroidManifest.xml`** — adds `ACCESS_WIFI_STATE` and `CHANGE_WIFI_STATE`.
- **`AppEntry`, `AppEntrySorter`, `gesture-core`, `daemon`** — unchanged.

---

## 6. Lifecycle

The `WIFI_STATE_CHANGED_ACTION` receiver registers in `onResume` and unregisters in
`onPause`, invalidating the card view on each change.

This is deliberately a tighter lifecycle than `AppRepository`'s package watcher, which
spans onCreate/onDestroy. Wi-Fi state only matters while the cards are on screen, and
because the launcher is HOME with `launchMode="singleTask"`, launching any app pauses
the activity and drops the receiver at no extra cost. Registration must be idempotent
in the same style as `AppRepository.start()`/`stop()` so a double call cannot throw.

---

## 7. Display constraint

Per the measured hardware facts in the parent spec, the see-through optical display
washes out mid-tones and gradients; only pure black and pure white read reliably.

**Consequence: Wi-Fi state is carried in text only.** No green/red state colouring, no
dimmed styling for the off state, no indicator glyph. The existing white-on-black
paints are reused unchanged, and the label itself says `Wi-Fi: On` or `Wi-Fi: Off`.

At the current 34px label size, `"Wi-Fi: Turning on…"` is roughly 380px of the 640px
display. That fits, but it is arithmetic rather than a measurement, and is confirmed
by screencap during on-device verification (§9).

---

## 8. Error handling

| Condition | Behaviour |
|---|---|
| `setWifiEnabled()` returns `false` | Toast `"Could not toggle Wi-Fi"`, matching the existing launch-failure pattern |
| `getSystemService(WIFI_SERVICE)` returns null | Label reads `"Wi-Fi: Unavailable"`; `activate()` is a no-op |
| Exception from `activate()` | Caught and toasted, as `launchSelected()` already does |
| Tap during a transition | Ignored (§4.3) |
| Unrecognised state code | `WifiState.UNKNOWN`, label `"Wi-Fi: Unknown"` |

---

## 9. Build and test strategy

### Pure JVM tests (plain JUnit, `app/src/test`, no device)

- **`WifiStateTest`** — every `WifiManager` code, plus out-of-range and negative
  values, maps to the right state and label.
- **`TileListBuilderTest`** — the pinned tile is always index 0; app order is
  preserved; an empty app list still yields the Wi-Fi tile; multiple pinned tiles
  keep their relative order.
- **`AppTileTest`** — label, key, and detail lines delegate correctly to `AppEntry`.
- **`TileSelectionTest`** — the `key()`-based diff preserves the selection index
  across a package change and resets it when the list genuinely differs; `move()`
  clamps at both ends; `recenter()` returns to index 0; `selected()` is null on an
  empty list; an index past the end of a shorter list is clamped rather than throwing.

The 42 existing tests must remain green; this change must not alter any of them.

### On-device verification

`setWifiEnabled()` cannot be unit tested, so it is verified on the physical Glass:

1. `./gradlew :app:assembleDebug` and `adb install -r`.
2. Restart the launcher activity (it is HOME, so it is already resident).
3. Confirm the Wi-Fi tile is index 0 and reads `Wi-Fi: Off`.
4. Tap; screencap during bring-up to confirm the transitional label renders and fits.
5. Confirm `adb shell settings get global wifi_on` flips `0` → `1` and `dumpsys wifi`
   no longer reports `Wi-Fi is disabled`.
6. Tap again; confirm it returns to `0`.
7. Confirm app tiles still launch, and that `AppRepository`'s package watching is
   unaffected.

---

## 10. Risks

### 10.1 Radio bring-up time

On the OMAP4430 a cold Wi-Fi enable takes several seconds. This is precisely why the
transitional states are shown rather than collapsed into on/off — a frozen label would
read as a dropped tap. No mitigation needed beyond §4.3.

### 10.2 Selection index after a package change

Pinning a tile at index 0 shifts every app index by one, and the diff that decides
whether to preserve the selection is being rewritten. This is the most likely place
for a regression, and it is currently untested code — which is the reason for the
`TileSelection` extraction in §4.0 and its test in §9.

### 10.3 Receiver leak

An unbalanced register/unregister across onResume/onPause throws on Android. Mitigated
by the idempotent `watching`-flag pattern already proven in `AppRepository`.
