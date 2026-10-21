#!/usr/bin/env bash
set -euo pipefail
APK_PATH="$1"
PACKAGE_ID="com.mf.mylauncher.debug"
mkdir -p upstream
LOG="upstream/voidcraft-mojo-emulator-logcat.txt"
ACTIVITIES="upstream/voidcraft-mojo-activities.txt"
SCREENSHOT="upstream/voidcraft-mojo-emulator-screenshot.png"
test -n "$APK_PATH" && test -s "$APK_PATH"
adb install -r "$APK_PATH"
adb shell am force-stop "$PACKAGE_ID" || true
adb shell monkey -p "$PACKAGE_ID" 1
sleep 5
seen=""
for _ in $(seq 1 30); do
  adb shell dumpsys activity activities > "$ACTIVITIES" 2>/dev/null || true
  if grep -Fq "$PACKAGE_ID" "$ACTIVITIES"; then seen=1; break; fi
  sleep 2
done
adb logcat -d -t 8000 > "$LOG" 2>/dev/null || true
adb exec-out screencap -p > "$SCREENSHOT" 2>/dev/null || true
if [[ -z "$seen" ]]; then
  echo "Mojo-based VoidCraft app did not appear in activity diagnostics." >&2
  grep -E 'FATAL EXCEPTION|AndroidRuntime|Process:|Exception|Unable to start' "$LOG" | tail -150 || true
  exit 1
fi
test -s "$SCREENSHOT"
echo "MOJO VOIDCRAFT APK SMOKE TEST PASSED: install and app activity launch."
echo "This test does not establish that Minecraft entered a world."
