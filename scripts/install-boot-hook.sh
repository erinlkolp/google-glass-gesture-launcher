#!/usr/bin/env bash
# Installs the gesture daemon as a boot service on Google Glass.
# Requires an eng/userdebug build where `adb shell` is already root.
set -euo pipefail
cd "$(dirname "$0")/.."
ADB=adb

$ADB shell 'mount -o rw,remount /system'
trap '$ADB shell "mount -o ro,remount /system" 2>/dev/null || true' EXIT
$ADB push daemon/build/libs/gestured.jar /data/local/tmp/gestured.jar
$ADB shell 'cp /data/local/tmp/gestured.jar /system/bin/gestured.jar'

$ADB shell 'cat > /system/bin/install-recovery.sh <<EOF
#!/system/bin/sh
# Started by init (service flash_recovery, class main, oneshot).
# Backgrounds immediately so init is not held, and waits for the framework
# because app_process needs a live runtime.
LOG=/data/local/tmp/gestured.log
(
  trap "" HUP
  exec </dev/null >>\$LOG 2>&1
  while [ "\$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
  done
  if [ ! -f /system/bin/gestured.jar ]; then
    echo "\$(date): gestured.jar missing, not starting"
    exit 1
  fi
  echo "\$(date): starting gestured" >> \$LOG
  export CLASSPATH=/system/bin/gestured.jar
  while true; do
    app_process /system/bin dev.erinlkolp.glasslauncher.daemon.Main </dev/null >> \$LOG 2>&1
    status=\$?
    echo "\$(date): gestured exited with status \$status" >> \$LOG
    if [ \$status -eq 0 ]; then
      echo "\$(date): clean exit, not restarting" >> \$LOG
      break
    fi
    sleep 30
  done
) &
EOF'

$ADB shell 'chmod 755 /system/bin/install-recovery.sh'
$ADB shell 'chmod 644 /system/bin/gestured.jar'
$ADB shell 'mount -o ro,remount /system'
echo "Installed. Reboot with: $ADB reboot"
