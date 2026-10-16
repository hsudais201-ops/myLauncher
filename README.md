# myLauncher

myLauncher is a Minecraft: Java Edition launcher foundation being rebuilt for Android. Product-owned code lives in launcher/, separate from build automation and generated upstream runtime files.

## Current status

The code now loads official Minecraft version metadata, validates per-version client/library/asset metadata, builds a deterministic install plan, downloads files only from approved HTTPS hosts, verifies SHA-1 and exact sizes, supports progress/cancellation, and exposes a native Android screen for selecting versions and preparing base files. Modern and legacy launch-argument metadata is now parsed and can be converted into a platform-filtered Java argv list.

This is not yet a playable launcher. The compatible Java runtime/native extraction and process runner, authenticated Microsoft account flow, complete game launch integration, and real-device testing remain unfinished. The UI's game-launch button remains disabled until those pieces exist.

The old Amethyst-based customization/build work is preserved on backup/pre-clear-2026-10-16 and remains untouched.

## Test the core

Requires a JDK:

    ./launcher/test.sh

## Build the Android shell

With JDK 17+, Gradle 8.9, and Android SDK platform/build-tools 35 installed:

    cd launcher/android-app
    gradle :app:assembleDebug

GitHub Actions runs both the warning-free core regression suite and Android debug APK assembly/package validation on changes. Passing tests or producing a debug APK does not prove Minecraft starts successfully.
