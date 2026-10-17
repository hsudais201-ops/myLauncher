#!/bin/sh
set -eu

APK_PATH="${1:-}"
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
timeout 30s adb shell monkey -p com.mf.mylauncher.debug 1

for i in $(seq 1 45); do
    if timeout 10s adb shell dumpsys activity activities | tr -d '\r' | grep -Fq 'com.mf.mylauncher.debug/net.kdt.pojavlaunch.LauncherActivity'; then
        break
    fi
    if [ "$i" -eq 45 ]; then
        timeout 20s adb logcat -d -t 5000 > upstream/mylauncher-emulator-logcat.txt || true
        timeout 20s adb shell dumpsys activity activities > upstream/mylauncher-activities.txt || true
        cat upstream/mylauncher-activities.txt || true
        grep -E 'FATAL EXCEPTION|AndroidRuntime|com\.mf\.mylauncher\.debug' upstream/mylauncher-emulator-logcat.txt | tail -250 || true
        echo "APK did not reach LauncherActivity in the emulator" >&2
        exit 1
    fi
    sleep 2
done

timeout 20s adb shell dumpsys activity activities > upstream/mylauncher-activities.txt || true
timeout 20s adb logcat -d -t 5000 > upstream/mylauncher-emulator-logcat.txt || true
grep -Fq 'com.mf.mylauncher.debug/net.kdt.pojavlaunch.LauncherActivity' upstream/mylauncher-activities.txt
echo "Emulator smoke test passed: APK installed and LauncherActivity resumed."
