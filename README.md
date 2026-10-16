# myLauncher

myLauncher is a Minecraft: Java Edition launcher foundation being rebuilt for Android. Product-owned code lives in launcher/, separate from build automation and generated upstream runtime files.

## Current status

The tested core loads official version metadata, plans and checksum-verifies client/library/asset downloads, supports progress/cancellation, and builds modern or legacy Java argument lists. Native ZIP extraction now has traversal protection, size/entry caps, excludes and staged publication. A process supervisor can execute argv directly, drain stdout/stderr, and enforce cancellation/timeouts. The Android screen can select versions and prepare supported base files.

This is not yet a playable launcher. Android-compatible Java runtime integration, platform-specific native selection/validation, a process supervisor wired to the actual runtime, authenticated Microsoft account flow, complete launch integration and real-device tests remain unfinished. The game-launch button remains disabled until those pieces exist.

The old Amethyst-based customization/build work is preserved on backup/pre-clear-2026-10-16 and remains untouched.

## Test the core

Requires a JDK:

    ./launcher/test.sh

## Build the Android shell

With JDK 17+, Gradle 8.9, and Android SDK platform/build-tools 35 installed:

    cd launcher/android-app
    gradle :app:assembleDebug

GitHub Actions runs both the warning-free core regression suite and Android debug APK package validation. Passing tests or producing an APK does not prove Minecraft starts successfully.
