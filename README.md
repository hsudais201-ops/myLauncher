# myLauncher

myLauncher is a Minecraft: Java Edition launcher foundation being rebuilt for Android. Product-owned code lives in launcher/, separate from build automation and generated upstream runtime files.

## Current status

The tested core loads official version metadata, plans and checksum-verifies client/library/asset downloads, supports progress/cancellation, and builds modern or legacy Java argument lists. Native ZIP extraction has traversal protection, size/entry caps and staged publication. A process supervisor can invoke an executable without a shell, drain stdout/stderr, and enforce cancellation/timeouts; a runtime probe checks the installed Java major version against the selected Minecraft version. The Android screen can select versions and prepare supported base files.

This is not yet a playable launcher. Android-compatible Java runtime installation, platform-specific native selection/validation, wiring runtime probing and process supervision to a real game launch, authenticated Microsoft account flow, and real-device testing remain unfinished. The game-launch button remains disabled until those pieces exist.

The old Amethyst-based customization/build work is preserved on backup/pre-clear-2026-10-16 and remains untouched.

## Test the core

Requires a JDK:

    ./launcher/test.sh

## Build the Android shell

With JDK 17+, Gradle 8.9, and Android SDK platform/build-tools 35 installed:

    cd launcher/android-app
    gradle :app:assembleDebug

GitHub Actions runs the warning-free core regression suite and Android debug APK package validation. Passing tests or producing an APK does not prove Minecraft starts successfully.
