#!/usr/bin/env bash
set -Eeuo pipefail

APK="${1:?Usage: emulator_smoke_test.sh <integrated-apk> [output-dir]}"
OUT_DIR="${2:-voidcraft-emulator-smoke}"
PACKAGE="com.voidcraft.launcher"
ENGINE_ACTIVITY="net.kdt.pojavlaunch.LauncherActivity"
mkdir -p "$OUT_DIR"

collect_diagnostics() {
  local status=$?
  trap - EXIT
  set +e
  if command -v adb >/dev/null 2>&1 && adb get-state >/dev/null 2>&1; then
    adb logcat -d -v threadtime > "$OUT_DIR/logcat.txt" 2>&1
    adb shell dumpsys activity activities > "$OUT_DIR/activity-final.txt" 2>&1
    adb shell screencap -p /sdcard/voidcraft-final.png >/dev/null 2>&1
    adb pull /sdcard/voidcraft-final.png "$OUT_DIR/final-screen.png" >/dev/null 2>&1
  fi
  printf 'exit_code=%s\n' "$status" >> "$OUT_DIR/result.txt"
  exit "$status"
}
trap collect_diagnostics EXIT

if [[ ! -s "$APK" ]]; then
  echo "Integrated APK not found or empty: $APK" >&2
  exit 2
fi

adb wait-for-device
adb shell getprop sys.boot_completed | tr -d '\r' | grep -q '^1$' || {
  echo "Android emulator has not completed boot." >&2
  exit 3
}

printf 'APK: %s\n' "$APK" | tee "$OUT_DIR/test-info.txt"
sha256sum "$APK" | tee -a "$OUT_DIR/test-info.txt"
adb install -r "$APK" | tee "$OUT_DIR/install.txt"
adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
adb shell monkey -p "$PACKAGE" 1 | tee "$OUT_DIR/launch-main.txt"

# The integrated upstream engine declares the Android process as
# com.voidcraft.launcher:launcher, so pidof "$PACKAGE" alone misses it.
# Check the resumed app activity instead; this also verifies that Android
# actually brought the launcher UI to the foreground.
launcher_seen=false
for attempt in $(seq 1 45); do
  adb shell dumpsys activity activities > "$OUT_DIR/activity-home.txt" 2>&1 || true
  if grep -Fq "$PACKAGE/.MainActivity" "$OUT_DIR/activity-home.txt"; then
    launcher_seen=true
    break
  fi
  sleep 1
done
if [[ "$launcher_seen" != true ]]; then
  echo "Voidcraft MainActivity did not become active." >&2
  exit 4
fi

find_play_target() {
  python3 - "$OUT_DIR/window.xml" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path = sys.argv[1]
try:
    root = ET.parse(path).getroot()
except (OSError, ET.ParseError):
    raise SystemExit(1)
for node in root.iter("node"):
    text = " ".join(node.attrib.get("text", "").split()).upper()
    if text != "PLAY":
        continue
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not match:
        continue
    x1, y1, x2, y2 = map(int, match.groups())
    print((x1 + x2) // 2, (y1 + y2) // 2)
    raise SystemExit(0)
raise SystemExit(1)
PY
}

found_play=""
for attempt in $(seq 1 20); do
  adb shell uiautomator dump /sdcard/voidcraft-window.xml >/dev/null 2>&1 || true
  adb shell cat /sdcard/voidcraft-window.xml > "$OUT_DIR/window.xml" 2>/dev/null || true
  if found_play="$(find_play_target 2>/dev/null)" && [[ -n "$found_play" ]]; then
    break
  fi
  sleep 2
done
if [[ -z "$found_play" ]]; then
  echo "Could not find an accessible PLAY button in Voidcraft's home screen." >&2
  exit 5
fi
read -r tap_x tap_y <<< "$found_play"
printf 'PLAY button center: %s,%s\n' "$tap_x" "$tap_y" | tee -a "$OUT_DIR/test-info.txt"
adb shell input tap "$tap_x" "$tap_y"

engine_seen=false
for attempt in $(seq 1 60); do
  adb shell dumpsys activity activities > "$OUT_DIR/activity-launch.txt" 2>&1 || true
  if grep -Fq "$ENGINE_ACTIVITY" "$OUT_DIR/activity-launch.txt"; then
    engine_seen=true
    break
  fi
  sleep 1
done
if [[ "$engine_seen" != true ]]; then
  echo "PLAY did not open $ENGINE_ACTIVITY within 60 seconds." >&2
  exit 6
fi

# Ensure the engine activity remains alive after its initial startup rather than immediately crashing.
sleep 8
adb shell dumpsys activity activities > "$OUT_DIR/activity-stability.txt" 2>&1
if ! grep -Fq "$ENGINE_ACTIVITY" "$OUT_DIR/activity-stability.txt"; then
  echo "Engine activity did not remain active after initial startup." >&2
  exit 7
fi

printf 'PASS: Voidcraft installed, home UI exposed PLAY, and PLAY opened %s in the emulator.\n' "$ENGINE_ACTIVITY" | tee "$OUT_DIR/result.txt"
