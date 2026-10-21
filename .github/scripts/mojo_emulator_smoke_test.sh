#!/usr/bin/env bash
set -euo pipefail

APK_PATH="$1"
PACKAGE_ID="com.mf.mylauncher.debug"
UI_XML="upstream/voidcraft-mojo-ui.xml"
GAME_LOG="upstream/voidcraft-mojo-emulator-logcat.txt"
ACTIVITY_LOG="upstream/voidcraft-mojo-activities.txt"
SCREENSHOT="upstream/voidcraft-mojo-emulator-screenshot.png"
mkdir -p upstream

if [[ -z "$APK_PATH" || ! -s "$APK_PATH" ]]; then
  echo "APK is missing or empty: $APK_PATH" >&2
  exit 1
fi

adb install -r "$APK_PATH"

# API 28 has the legacy storage permission flow used by Mojo's TestStorageActivity.
# Grant it before launch so UI assertions exercise the home screen, not a permission dialog.
adb shell pm grant "$PACKAGE_ID" android.permission.READ_EXTERNAL_STORAGE
adb shell pm grant "$PACKAGE_ID" android.permission.WRITE_EXTERNAL_STORAGE
adb shell am force-stop "$PACKAGE_ID" || true
adb shell monkey -p "$PACKAGE_ID" 1

dump_ui() {
  timeout -k 3s 15s adb shell uiautomator dump /sdcard/voidcraft-mojo-ui.xml >/dev/null 2>&1 &&
    timeout -k 3s 10s adb exec-out cat /sdcard/voidcraft-mojo-ui.xml > "$UI_XML" &&
    test -s "$UI_XML"
}

ui_has() {
  python3 - "$UI_XML" "$1" "$2" <<'PY'
import sys
import xml.etree.ElementTree as ET

path, mode, needle = sys.argv[1:]
try:
    root = ET.parse(path).getroot()
except (OSError, ET.ParseError):
    raise SystemExit(1)

for node in root.iter("node"):
    attrs = node.attrib
    if attrs.get("visible-to-user", "true") == "false":
        continue
    if mode == "id":
        match = attrs.get("resource-id", "").rsplit("/", 1)[-1] == needle
    elif mode == "text":
        match = attrs.get("text", "").strip().casefold() == needle.casefold()
    else:
        raise SystemExit(2)
    if match:
        raise SystemExit(0)
raise SystemExit(1)
PY
}

home_ready=""
for _ in $(seq 1 40); do
  if dump_ui && ui_has text "VOIDCRAFT" && ui_has id "play_button"; then
    home_ready=1
    break
  fi
  sleep 2
done

adb shell dumpsys activity activities > "$ACTIVITY_LOG" 2>/dev/null || true
adb logcat -d -t 10000 > "$GAME_LOG" 2>/dev/null || true
adb exec-out screencap -p > "$SCREENSHOT" 2>/dev/null || true

if [[ -z "$home_ready" ]]; then
  echo "VoidCraft home screen did not expose the VOIDCRAFT hero title and Play control." >&2
  grep -E 'FATAL EXCEPTION|AndroidRuntime|Process:|Exception|Unable to start|Permission Denial' "$GAME_LOG" | tail -200 || true
  exit 1
fi

test -s "$SCREENSHOT"
echo "MOJO VOIDCRAFT HOME SMOKE TEST PASSED: APK installed, storage permissions granted, VOIDCRAFT hero title and Play control visible."
echo "This smoke test does not establish that Minecraft started or entered a world."
