# myLauncher

myLauncher is a Minecraft: Java Edition launcher foundation being rebuilt for Android. Product-owned code lives in launcher/, separate from build automation and generated upstream runtime files.

## Current status

The tested core loads official version metadata, plans and checksum-verifies client/library/asset downloads, supports progress/cancellation, and builds modern or legacy Java argument lists. Native archive extraction supports multiple archives in a single staged directory with per-archive exclusions, duplicate-file rejection, and preservation of the previous directory on failure. A verified classpath resolver checks client/library/logging files before building an ordered classpath.

The installation service can also download and extract native archives when given an explicit target plan. The Android screen deliberately does not request stock Linux native archives: a compatible Android native/runtime provider, full launch orchestration, authenticated Microsoft account flow and real-device testing are still required. The game-launch button remains disabled until those pieces exist.

The old Amethyst-based customization/build work is preserved on backup/pre-clear-2026-10-16 and remains untouched.

## Test the core

Requires a JDK:

    ./launcher/test.sh

## Build the Android shell

With JDK 17+, Gradle 8.9, and Android SDK platform/build-tools 35 installed:

    cd launcher/android-app
    gradle :app:assembleDebug

GitHub Actions runs both the warning-free core regression suite and Android debug APK package validation. Passing tests or producing an APK does not prove Minecraft starts successfully.
