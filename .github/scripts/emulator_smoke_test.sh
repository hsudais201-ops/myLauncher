#!/bin/sh
set -eu

APK_PATH="$1"
if [ -z "$APK_PATH" ]; then
    echo "Usage: emulator_smoke_test.sh <apk-path>" >&2
    exit 2
fi

# The workflow validates and signs this APK before starting the emulator.
timeout 180s adb install -r "$APK_PATH"

# TestStorageActivity requests legacy storage permissions on Android 9 before opening LauncherActivity.
timeout 15s adb shell pm grant com.mf.mylauncher.debug android.permission.READ_EXTERNAL_STORAGE
timeout 15s adb shell pm grant com.mf.mylauncher.debug android.permission.WRITE_EXTERNAL_STORAGE
timeout 15s adb shell am force-stop com.mf.mylauncher.debug || true
# TestStorageActivity is the manifest's exported launcher entry point. It
# handles storage access and then opens LauncherActivity.
timeout 20s adb shell am start -n com.mf.mylauncher.debug/net.kdt.pojavlaunch.TestStorageActivity

# Confirm that LauncherActivity is the currently resumed foreground activity,
# not merely a stale activity record left in the task history.
ACTIVITY_PATTERN='(mResumedActivity|topResumedActivity).*com\.mf\.mylauncher\.debug/net\.kdt\.pojavlaunch\.LauncherActivity'

capture_diagnostic_screenshot() {
    timeout 10s adb shell screencap -p /sdcard/voidcraft-launcher-smoke.png >/dev/null 2>&1 || return 0
    timeout 10s adb pull /sdcard/voidcraft-launcher-smoke.png upstream/mylauncher-emulator-screenshot.png >/dev/null 2>&1 || true
    timeout 10s adb shell rm -f /sdcard/voidcraft-launcher-smoke.png >/dev/null 2>&1 || true
}

for i in $(seq 1 45); do
    ACTIVITY_DUMP=$(timeout 10s adb shell dumpsys activity activities 2>/dev/null | tr -d '\r' || true)
    if printf '%s\n' "$ACTIVITY_DUMP" | grep -Eq "$ACTIVITY_PATTERN"; then
        break
    fi
    if [ "$i" -eq 45 ]; then
        timeout 20s adb logcat -d -t 5000 > upstream/mylauncher-emulator-logcat.txt || true
        timeout 20s adb shell dumpsys activity activities > upstream/mylauncher-activities.txt || true
        capture_diagnostic_screenshot
        cat upstream/mylauncher-activities.txt || true
        grep -E 'FATAL EXCEPTION|AndroidRuntime|com\.mf\.mylauncher\.debug' upstream/mylauncher-emulator-logcat.txt | tail -250 || true
        echo "LauncherActivity was not resumed at the end of the emulator smoke test" >&2
        exit 1
    fi
    sleep 2
done

timeout 20s adb shell dumpsys activity activities > upstream/mylauncher-activities.txt || true
timeout 20s adb logcat -d -t 5000 > upstream/mylauncher-emulator-logcat.txt || true
if ! grep -Eq "$ACTIVITY_PATTERN" upstream/mylauncher-activities.txt; then
    capture_diagnostic_screenshot
    cat upstream/mylauncher-activities.txt || true
    grep -E 'FATAL EXCEPTION|AndroidRuntime|com\.mf\.mylauncher\.debug' upstream/mylauncher-emulator-logcat.txt | tail -250 || true
    echo "LauncherActivity was not resumed at the end of the emulator smoke test" >&2
    exit 1
fi

# Capture the Android emulator screen after LauncherActivity becomes available.
sleep 2
timeout 20s adb shell screencap -p /sdcard/voidcraft-launcher-smoke.png
timeout 20s adb pull /sdcard/voidcraft-launcher-smoke.png upstream/mylauncher-emulator-screenshot.png
test -s upstream/mylauncher-emulator-screenshot.png
timeout 10s adb shell rm -f /sdcard/voidcraft-launcher-smoke.png || true
echo "Emulator screenshot saved: upstream/mylauncher-emulator-screenshot.png"
echo "Emulator smoke test passed: APK installed and LauncherActivity resumed."
