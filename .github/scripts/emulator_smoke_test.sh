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
echo "Emulator smoke test passed: APK installed and LauncherActivity resumed."
echo "Non-empty emulator screenshot captured: $SCREENSHOT_PATH"
