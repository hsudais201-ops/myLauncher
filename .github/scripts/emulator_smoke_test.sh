#!/usr/bin/env bash
# Headless smoke test for the actual Voidcraft APK. Every adb operation is bounded:
# a wedged software emulator must not consume the full CI job timeout.
set -euo pipefail

APK_PATH="${1:?usage: emulator_smoke_test.sh path/to/app-debug.apk}"
mkdir -p upstream

adb_limited() {
    local seconds="$1"
    shift
    timeout --foreground "${seconds}s" adb "$@"
}

adb_limited 180 wait-for-device
adb_limited 90 install -r "$APK_PATH"
adb_limited 15 shell pm list packages | grep -Fx 'package:com.mf.mylauncher.debug'
adb_limited 15 shell pm path com.mf.mylauncher.debug | grep -F 'package:'

# First-run notification popups obscure the UI on API 33+.
adb_limited 15 shell pm grant com.mf.mylauncher.debug android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
adb_limited 15 shell am force-stop com.mf.mylauncher.debug
adb_limited 30 shell monkey -p com.mf.mylauncher.debug -c android.intent.category.LAUNCHER 1

# CI's software-rendered emulator can take longer on a cold start. Wait for
# LauncherActivity to resume AND for Android's temporary splash window to go away.
launcher_ready=0
for attempt in $(seq 1 12); do
    sleep 5
    if ! adb_limited 12 shell ps -A > upstream/mylauncher-processes.txt; then
        echo "Timed out reading emulator process list (attempt $attempt)." >&2
        break
    fi
    if ! adb_limited 12 shell dumpsys activity activities > upstream/mylauncher-activities.txt; then
        echo "Timed out reading emulator activity state (attempt $attempt)." >&2
        break
    fi
    if ! adb_limited 12 shell dumpsys window windows > upstream/mylauncher-windows.txt; then
        echo "Timed out reading emulator window state (attempt $attempt)." >&2
        break
    fi

    if grep -Eq 'ResumedActivity:.*com[.]mf[.]mylauncher[.]debug/net[.]kdt[.]pojavlaunch[.]LauncherActivity|mFocusedApp=.*com[.]mf[.]mylauncher[.]debug/net[.]kdt[.]pojavlaunch[.]LauncherActivity' upstream/mylauncher-activities.txt \
       && ! grep -Fq 'Splash Screen com.mf.mylauncher.debug' upstream/mylauncher-windows.txt; then
        launcher_ready=1
        break
    fi
done

adb_limited 15 logcat -d > upstream/mylauncher-emulator-logcat.txt || true
if [ -f upstream/mylauncher-processes.txt ]; then
    grep -E 'com[.]mf[.]mylauncher[.]debug(:launcher)?([[:space:]]|$)' upstream/mylauncher-processes.txt || true
fi

if [ "$launcher_ready" -ne 1 ]; then
    # SIGQUIT asks the debuggable Android process to print thread stacks to logcat.
    pid="$(awk '$NF ~ /^com[.]mf[.]mylauncher[.]debug(:launcher)?$/ {print $2; exit}' upstream/mylauncher-processes.txt 2>/dev/null || true)"
    if [ -n "$pid" ]; then
        adb_limited 10 shell run-as com.mf.mylauncher.debug kill -3 "$pid" >/dev/null 2>&1 || true
        sleep 2
        adb_limited 15 logcat -d > upstream/mylauncher-emulator-logcat.txt || true
    fi
    echo "LauncherActivity did not draw its first frame before the smoke-test timeout." >&2
    echo "Inspect mylauncher-activities.txt, mylauncher-windows.txt, and mylauncher-emulator-logcat.txt." >&2
    exit 1
fi

grep -E 'ResumedActivity:.*com[.]mf[.]mylauncher[.]debug/net[.]kdt[.]pojavlaunch[.]LauncherActivity|mFocusedApp=.*com[.]mf[.]mylauncher[.]debug/net[.]kdt[.]pojavlaunch[.]LauncherActivity' upstream/mylauncher-activities.txt
if grep -F 'Process: com.mf.mylauncher.debug' upstream/mylauncher-emulator-logcat.txt; then
    echo "Detected an Android runtime crash during launcher smoke test." >&2
    exit 1
fi
