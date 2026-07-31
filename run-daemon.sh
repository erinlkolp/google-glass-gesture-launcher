#!/usr/bin/env bash
# Builds, deploys, and starts the gesture daemon on the attached Glass.
set -euo pipefail
cd "$(dirname "$0")"
./gradlew :daemon:dexJar
adb push daemon/build/libs/gestured.jar /data/local/tmp/
exec adb shell \
    "CLASSPATH=/data/local/tmp/gestured.jar \
     app_process /system/bin dev.erinlkolp.glasslauncher.daemon.Main"
