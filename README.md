# myLauncher

myLauncher is a Minecraft: Java Edition launcher foundation being rebuilt for Android. Product-owned code lives in `launcher/`, separate from build automation and generated upstream runtime files.

## Current status

The tested core loads official version metadata, plans and checksum-verifies client/library/asset downloads, supports progress/cancellation, and builds modern or legacy Java argument lists. Native archive extraction supports multiple archives in a single staged directory with per-archive exclusions, duplicate-file rejection, and preservation of the previous directory on failure. A verified classpath resolver checks client/library/logging files before building an ordered classpath.

The installation service can download and extract native archives when given an explicit target plan. Launch preflight now verifies the installed classpath and checks that a selected Java executable reports the Java major version required by the version metadata. The Android screen checks for an installed runtime at the app-private path `runtimes/default/bin/java`.

This is not yet a playable launcher. No Android-compatible runtime provider or Android-native library selection is connected, and authenticated Microsoft sign-in, full launch orchestration, and real-device testing remain unfinished. The game-launch button stays disabled until the missing pieces are integrated and tested. A passing core suite or building an APK does not prove Minecraft starts successfully.

The old Amethyst-based customization/build work is preserved on `backup/pre-clear-2026-10-16` and remains untouched.

## Test the core

Requires a JDK:

    ./launcher/test.sh

## Build the Android shell

With JDK 17+, Gradle 8.9, and Android SDK platform/build-tools 35 installed:

    cd launcher/android-app
    gradle :app:assembleDebug

GitHub Actions runs the warning-free core regression suite and Android debug APK package validation.
