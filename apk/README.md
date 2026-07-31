# Prebuilt artifacts

Built from this repository. Both are debug builds signed with the standard Android
debug keystore — fine for sideloading onto a development device, not for distribution.

| File | What it is |
|---|---|
| `glass-launcher-v0.1-1-debug.apk` | The launcher app. `applicationId dev.erinlkolp.glasslauncher`, `minSdk 22`, `targetSdk 22`. |
| `gestured.jar` | The root daemon, already dex'd. Run via `app_process`, not installable as an app. |

## Installing

```bash
./tools/platform-tools/adb install -r apk/glass-launcher-v0.1-1-debug.apk
```

The APK declares both `LAUNCHER` and `HOME` intent filters, so after installing, Android
will offer it as a home-screen choice. Selecting it is optional — it works as an ordinary
app too.

## Running the daemon

The daemon is what provides the global two-finger-swipe-down "go home" gesture from inside
any app. It needs root, so it requires an `eng`/`userdebug` build where `adb shell` is
already uid 0.

```bash
./tools/platform-tools/adb push apk/gestured.jar /data/local/tmp/
./tools/platform-tools/adb shell "CLASSPATH=/data/local/tmp/gestured.jar \
    app_process /system/bin dev.erinlkolp.glasslauncher.daemon.Main"
```

It prints `gestured: watching /dev/input/event3` on success. To make it start at boot
instead, see `scripts/install-boot-hook.sh`.

## Rebuilding

```bash
./gradlew :app:assembleDebug   # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :daemon:dexJar       # -> daemon/build/libs/gestured.jar
```

These files are copies, not build outputs — regenerate them by hand when you cut a new
version.
