#!/bin/sh
set -eu

APK_PATH="$1"
if [ -z "$APK_PATH" ]; then
    echo "Usage: emulator_smoke_test.sh <apk-path>" >&2
    exit 2
fi

# The workflow validates and signs this APK before starting the emulator.
timeout -k 3s 180s adb install -r "$APK_PATH"

# TestStorageActivity requests legacy storage permissions on Android 9 before opening LauncherActivity.
timeout -k 3s 15s adb shell pm grant com.mf.mylauncher.debug android.permission.READ_EXTERNAL_STORAGE
timeout -k 3s 15s adb shell pm grant com.mf.mylauncher.debug android.permission.WRITE_EXTERNAL_STORAGE
timeout -k 3s 15s adb shell am force-stop com.mf.mylauncher.debug || true
# Capture a screenshot on launch failure as well as success, when ADB still works.
capture_diagnostic_screenshot() {
    # exec-out streams raw PNG bytes directly, avoiding remote-file/pull races.
    timeout -k 3s 10s adb exec-out screencap -p > upstream/mylauncher-emulator-screenshot.png 2>/dev/null || true
}

# TestStorageActivity is the manifest's exported launcher entry point. It
# handles storage access and then opens LauncherActivity.
# Hosted emulator images occasionally print "Starting: Intent" but leave
# am start attached until timeout. Ignore only timeout status 124 here; the
# resumed-activity check below still decides whether the smoke test passes.
if timeout -k 3s 20s adb shell am start -n com.mf.mylauncher.debug/net.kdt.pojavlaunch.TestStorageActivity; then
    :
else
    start_status=$?
    if [ "$start_status" -eq 124 ]; then
        echo "Android activity-start command timed out after dispatch; verifying LauncherActivity."
    else
        timeout -k 3s 20s adb logcat -d -t 5000 > upstream/mylauncher-emulator-logcat.txt || true
        timeout -k 3s 20s adb shell dumpsys activity activities > upstream/mylauncher-activities.txt || true
        capture_diagnostic_screenshot
        cat upstream/mylauncher-activities.txt || true
        grep -E 'FATAL EXCEPTION|AndroidRuntime|com\.mf\.mylauncher\.debug' upstream/mylauncher-emulator-logcat.txt | tail -250 || true
        exit "$start_status"
    fi
fi

# Require the launcher to be foreground/resumed, not just present in task history.
ACTIVITY_PATTERN='(mResumedActivity|topResumedActivity).*com\.mf\.mylauncher\.debug/net\.kdt\.pojavlaunch\.LauncherActivity'
ACTIVITY_QUERY_ERRORS=0
for i in $(seq 1 45); do
    ACTIVITY_DUMP=""
    if ACTIVITY_DUMP=$(timeout -k 3s 5s adb shell dumpsys activity activities 2>/dev/null) && [ -n "$ACTIVITY_DUMP" ]; then
        ACTIVITY_QUERY_ERRORS=0
        ACTIVITY_DUMP=$(printf '%s\n' "$ACTIVITY_DUMP" | tr -d '\r')
    else
        ACTIVITY_QUERY_ERRORS=$((ACTIVITY_QUERY_ERRORS + 1))
        if [ "$ACTIVITY_QUERY_ERRORS" -ge 5 ]; then
            timeout -k 3s 20s adb logcat -d -t 5000 > upstream/mylauncher-emulator-logcat.txt || true
            timeout -k 3s 20s adb shell dumpsys activity activities > upstream/mylauncher-activities.txt || true
            capture_diagnostic_screenshot
            cat upstream/mylauncher-activities.txt || true
            grep -E 'FATAL EXCEPTION|AndroidRuntime|com\.mf\.mylauncher\.debug' upstream/mylauncher-emulator-logcat.txt | tail -250 || true
            echo "ADB activity queries failed repeatedly; LauncherActivity could not be verified." >&2
            exit 1
        fi
    fi
    if printf '%s\n' "$ACTIVITY_DUMP" | grep -Eq "$ACTIVITY_PATTERN"; then
        break
    fi
    if [ "$i" -eq 45 ]; then
        timeout -k 3s 20s adb logcat -d -t 5000 > upstream/mylauncher-emulator-logcat.txt || true
        timeout -k 3s 20s adb shell dumpsys activity activities > upstream/mylauncher-activities.txt || true
        capture_diagnostic_screenshot
        cat upstream/mylauncher-activities.txt || true
        grep -E 'FATAL EXCEPTION|AndroidRuntime|com\.mf\.mylauncher\.debug' upstream/mylauncher-emulator-logcat.txt | tail -250 || true
        echo "LauncherActivity was not resumed at the end of the emulator smoke test" >&2
        exit 1
    fi
    sleep 2
done

timeout -k 3s 20s adb shell dumpsys activity activities > upstream/mylauncher-activities.txt || true
timeout -k 3s 20s adb logcat -d -t 5000 > upstream/mylauncher-emulator-logcat.txt || true
if ! grep -Eq "$ACTIVITY_PATTERN" upstream/mylauncher-activities.txt; then
    capture_diagnostic_screenshot
    cat upstream/mylauncher-activities.txt || true
    grep -E 'FATAL EXCEPTION|AndroidRuntime|com\.mf\.mylauncher\.debug' upstream/mylauncher-emulator-logcat.txt | tail -250 || true
    echo "LauncherActivity was not resumed at the end of the emulator smoke test" >&2
    exit 1
fi

# Capture the Android emulator screen after LauncherActivity becomes available.
# Stream raw PNG bytes from ADB and retry transient emulator/shell failures.
sleep 2
SCREENSHOT_PATH=upstream/mylauncher-emulator-screenshot.png
SCREENSHOT_CAPTURED=false
attempt=1
while [ "$attempt" -le 3 ]; do
    rm -f "$SCREENSHOT_PATH"
    if timeout -k 3s 20s adb exec-out screencap -p > "$SCREENSHOT_PATH" 2> /tmp/voidcraft-screencap-error.txt && test -s "$SCREENSHOT_PATH"; then
        SCREENSHOT_CAPTURED=true
        break
    fi
    echo "Screenshot capture attempt $attempt of 3 failed." >&2
    if [ -s /tmp/voidcraft-screencap-error.txt ]; then
        cat /tmp/voidcraft-screencap-error.txt >&2
    fi
    attempt=$((attempt + 1))
    sleep 2
done
if [ "$SCREENSHOT_CAPTURED" != true ]; then
    timeout -k 3s 20s adb shell dumpsys activity activities > upstream/mylauncher-activities.txt || true
    timeout -k 3s 20s adb logcat -d -t 5000 > upstream/mylauncher-emulator-logcat.txt || true
    capture_diagnostic_screenshot
    cat upstream/mylauncher-activities.txt || true
    grep -E 'FATAL EXCEPTION|AndroidRuntime|com\.mf\.mylauncher\.debug' upstream/mylauncher-emulator-logcat.txt | tail -250 || true
    echo "Unable to capture a non-empty emulator screenshot after 3 attempts." >&2
    exit 1
fi
test -s "$SCREENSHOT_PATH"
echo "Emulator screenshot saved: $SCREENSHOT_PATH"
echo "Launcher-only smoke test passed: LauncherActivity resumed and screenshot captured."

# Exercise the real vanilla-game path on a clean emulator, not only the launcher shell.
# The default upstream profile is Minecraft 1.7.10, a lightweight Java 8 version.
UI_XML=/tmp/voidcraft-launcher-ui.xml
GAME_LOG=upstream/mylauncher-game-launch-logcat.txt
MINECRAFT_LOG=upstream/mylauncher-minecraft-latestlog.txt
GAME_LOG_PATH=""
GAME_ACTIVITY_PATTERN='(mResumedActivity|topResumedActivity).*com\.mf\.mylauncher\.debug/net\.kdt\.pojavlaunch\.MainActivity'

dump_ui() {
    timeout -k 3s 15s adb shell uiautomator dump /sdcard/voidcraft-ui.xml >/dev/null 2>&1 &&
        timeout -k 3s 10s adb exec-out cat /sdcard/voidcraft-ui.xml > "$UI_XML" &&
        test -s "$UI_XML"
}

find_view_center() {
    python3 - "$UI_XML" "$1" "$2" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path, mode, needle = sys.argv[1:]
try:
    root = ET.parse(path).getroot()
except (OSError, ET.ParseError):
    sys.exit(1)

for node in root.iter("node"):
    attrs = node.attrib
    if attrs.get("visible-to-user", "true") == "false":
        continue
    if attrs.get("enabled", "true") == "false":
        continue
    resource_id = attrs.get("resource-id", "").rsplit("/", 1)[-1]
    label = attrs.get("text", "").strip()
    description = attrs.get("content-desc", "").strip()
    if mode == "id":
        matched = resource_id == needle
    elif mode == "text":
        matched = label == needle or description == needle
    elif mode == "text-contains":
        matched = needle.casefold() in label.casefold() or needle.casefold() in description.casefold()
    else:
        sys.exit(2)
    if not matched:
        continue
    bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", attrs.get("bounds", ""))
    if not bounds:
        continue
    x1, y1, x2, y2 = map(int, bounds.groups())
    if x2 <= x1 or y2 <= y1:
        continue
    print((x1 + x2) // 2, (y1 + y2) // 2)
    sys.exit(0)
sys.exit(1)
PY
}

wait_for_view() {
    mode=$1
    needle=$2
    wait_seconds=$3
    deadline=$(( $(date +%s) + wait_seconds ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        if dump_ui; then
            center="$(find_view_center "$mode" "$needle" 2>/dev/null || true)"
            if [ -n "$center" ]; then
                printf '%s\n' "$center"
                return 0
            fi
        fi
        sleep 1
    done
    echo "Timed out waiting for UI element ($mode): $needle" >&2
    return 1
}

tap_view() {
    mode=$1
    needle=$2
    wait_seconds=$3
    center="$(wait_for_view "$mode" "$needle" "$wait_seconds")" || return 1
    set -- $center
    timeout -k 3s 10s adb shell input tap "$1" "$2"
    sleep 2
}

capture_game_diagnostics() {
    timeout -k 3s 20s adb logcat -d -t 12000 > "$GAME_LOG" 2>/dev/null || true
    timeout -k 3s 20s adb shell dumpsys activity activities > upstream/mylauncher-activities.txt 2>/dev/null || true
    if [ -n "$GAME_LOG_PATH" ]; then
        timeout -k 3s 15s adb shell "cat '$GAME_LOG_PATH'" > "$MINECRAFT_LOG" 2>/dev/null || true
    fi
    capture_diagnostic_screenshot
}

fail_real_game_smoke() {
    echo "REAL MINECRAFT LAUNCH SMOKE TEST FAILED: $*" >&2
    capture_game_diagnostics
    grep -E 'FATAL EXCEPTION|AndroidRuntime|Exception in thread|NoClassDefFoundError|UnsatisfiedLinkError|MinecraftDownloader|MainActivity|JREUtils|LauncherActivity|VoidCraftCI' "$GAME_LOG" | tail -250 || true
    if [ -s "$MINECRAFT_LOG" ]; then
        tail -250 "$MINECRAFT_LOG" || true
    fi
    exit 1
}

# The CI emulator is fresh, so no account or private user files should exist.
# The local profile is only for single-player smoke testing; no online server is used.
if ! tap_view id account_spinner 30; then
    fail_real_game_smoke "could not open the account selector from the launcher home screen"
fi
if ! tap_view text "Local account" 12; then
    # Some upstream account-spinner implementations expose an intermediate Add account row.
    if tap_view text "Add account" 5; then
        if ! tap_view text "Local account" 20; then
            fail_real_game_smoke "could not choose Local account"
        fi
    else
        fail_real_game_smoke "Local account option did not appear"
    fi
fi
if ! tap_view id login_edit_email 20; then
    fail_real_game_smoke "local-account username field did not appear"
fi
timeout -k 3s 10s adb shell input text VoidCraftCI
# Hide the keyboard so the submit control remains tappable on small virtual screens.
timeout -k 3s 10s adb shell input keyevent 4 || true
sleep 1
if ! tap_view id login_button 20; then
    fail_real_game_smoke "could not submit the offline test account"
fi
if ! wait_for_view id play_button 40 >/dev/null; then
    fail_real_game_smoke "launcher did not return to the Play screen after creating the offline account"
fi
# Allow the official version manifest lookup to finish before selecting the default profile.
sleep 15
if ! dump_ui; then
    fail_real_game_smoke "could not inspect the launcher before starting Minecraft"
fi
if ! find_view_center text-contains "1.7.10" >/dev/null 2>&1; then
    fail_real_game_smoke "fresh default profile is not showing the expected Minecraft 1.7.10 version"
fi

echo "Starting real vanilla Minecraft 1.7.10 through the user-facing Play button."
if ! tap_view id play_button 20; then
    fail_real_game_smoke "the Play button was not available"
fi

# Downloads can be large on a fresh emulator. Wait for the actual game Activity,
# not just LauncherActivity, and collect useful logs if it fails to arrive.
GAME_DEADLINE=$(( $(date +%s) + 900 ))
GAME_ACTIVITY_SEEN=false
while [ "$(date +%s)" -lt "$GAME_DEADLINE" ]; do
    ACTIVITY_DUMP="$(timeout -k 3s 10s adb shell dumpsys activity activities 2>/dev/null || true)"
    if printf '%s\n' "$ACTIVITY_DUMP" | tr -d '\r' | grep -Eq "$GAME_ACTIVITY_PATTERN"; then
        GAME_ACTIVITY_SEEN=true
        break
    fi
    sleep 4
done
if [ "$GAME_ACTIVITY_SEEN" != true ]; then
    fail_real_game_smoke "MainActivity did not become foreground within 15 minutes; version download or native game startup likely failed"
fi

# MainActivity creates this log in the configured game directory. Requiring the
# real runCraft startup marker avoids treating an Activity launch by itself as success.
for i in $(seq 1 60); do
    GAME_LOG_PATH="$(timeout -k 3s 15s adb shell 'find /sdcard -maxdepth 8 -type f -name latestlog.txt 2>/dev/null | head -n 1' 2>/dev/null | tr -d '\r')"
    if [ -n "$GAME_LOG_PATH" ]; then
        break
    fi
    sleep 2
done
if [ -z "$GAME_LOG_PATH" ]; then
    fail_real_game_smoke "Minecraft latestlog.txt was not visible after MainActivity opened"
fi

STARTUP_MARKER_SEEN=false
for i in $(seq 1 90); do
    timeout -k 3s 15s adb shell "cat '$GAME_LOG_PATH'" > "$MINECRAFT_LOG" 2>/dev/null || true
    if grep -Fq -- "Starting game with Launcher Debug!" "$MINECRAFT_LOG"; then
        STARTUP_MARKER_SEEN=true
        break
    fi
    ACTIVITY_DUMP="$(timeout -k 3s 10s adb shell dumpsys activity activities 2>/dev/null || true)"
    if ! printf '%s\n' "$ACTIVITY_DUMP" | tr -d '\r' | grep -Eq "$GAME_ACTIVITY_PATTERN"; then
        fail_real_game_smoke "game Activity exited before the Minecraft JVM startup marker appeared"
    fi
    sleep 2
done
if [ "$STARTUP_MARKER_SEEN" != true ]; then
    fail_real_game_smoke "Minecraft's runCraft/JVM startup marker did not appear in latestlog.txt"
fi

# Keep the Activity alive briefly and reject obvious JVM/client initialization failures.
sleep 20
capture_game_diagnostics
ACTIVITY_DUMP="$(cat upstream/mylauncher-activities.txt 2>/dev/null || true)"
if ! printf '%s\n' "$ACTIVITY_DUMP" | tr -d '\r' | grep -Eq "$GAME_ACTIVITY_PATTERN"; then
    fail_real_game_smoke "Minecraft MainActivity did not remain in the foreground after JVM startup"
fi
if grep -Eq 'Exception in thread "main"|NoClassDefFoundError|UnsatisfiedLinkError' "$MINECRAFT_LOG"; then
    fail_real_game_smoke "Minecraft latestlog.txt contains a Java client startup failure"
fi
if grep -Eq 'FATAL EXCEPTION|AndroidRuntime: FATAL EXCEPTION' "$GAME_LOG"; then
    fail_real_game_smoke "Android reported a fatal exception during Minecraft startup"
fi

echo "REAL MINECRAFT LAUNCH SMOKE TEST PASSED: local account → vanilla 1.7.10 download → MainActivity → Minecraft JVM startup marker."
echo "Game activity and startup logs: $GAME_LOG and $MINECRAFT_LOG"
echo "The automated smoke test checks JVM startup; a physical-device single-player/world acceptance test is still required."

