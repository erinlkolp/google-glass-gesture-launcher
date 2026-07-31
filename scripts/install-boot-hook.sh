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
  trap "" HUP
  while [ "\$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
  done
  export CLASSPATH=/system/bin/gestured.jar
  exec app_process /system/bin dev.erinlkolp.glasslauncher.daemon.Main </dev/null >/dev/null 2>&1
) &
EOF'

$ADB shell 'chmod 755 /system/bin/install-recovery.sh'
$ADB shell 'chmod 644 /system/bin/gestured.jar'
$ADB shell 'mount -o ro,remount /system'
echo "Installed. Reboot with: $ADB reboot"
