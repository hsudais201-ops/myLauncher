# Source provenance — independent VoidCraft Android app

**Scope:** `voidcraft-android/` only. This file is a maintainer provenance record, not a legal opinion or a certificate that the entire repository is infringement-free.

## Authoring rule

The independent app's Java/UI implementations are authored specifically for VoidCraft from documented requirements and platform/protocol behavior. They must not be copied, renamed, mechanically translated, extracted, or patched from Amethyst-Android, PojavLauncher, or another launcher. The clean-room workflow intentionally does not check out the legacy launcher source.

Public documentation can describe behavior and protocol formats; do not copy expressive source text or implementation code from an existing launcher. For any new code or asset, record the source, authoring method, commit, and license here before merging.

## Current first-party source files

| Path | Purpose | Authoring/provenance | External runtime dependency |
|---|---|---|---|
| `app/src/main/java/com/voidcraft/launcher/MainActivity.java` | Dashboard/navigation, preferences, saved-server manager, optional Server Status feature UI, official version metadata browser, and in-app About & Legal notices | Written for VoidCraft from requirements using Android platform APIs; not intentionally derived from another launcher source | Android framework APIs only; status calls the local client below |
| `app/src/main/java/com/voidcraft/launcher/SavedServerStore.java` | Local saved-server add/edit/remove/list persistence | Written for VoidCraft using Android SharedPreferences and platform JSON APIs | Android framework APIs and `org.json` from Android |
| `app/src/main/java/com/voidcraft/launcher/MinecraftServerStatusClient.java` | Read-only Minecraft: Java Edition status handshake/request and bounded response parsing | Written for VoidCraft from the public status protocol behavior; not copied from another launcher implementation | Java networking/IO classes and Android's `org.json`; no third-party runtime library declared |
| `app/src/main/java/com/voidcraft/launcher/MinecraftVersionManifestClient.java` | Fetches and validates Mojang's public Java Edition version manifest; bounds response size and restricts version-manifest URLs to the official HTTPS metadata host | Written for VoidCraft using public manifest fields and Android/Java APIs; no launcher implementation copied | Java networking/IO and Android's `org.json`; does not download game JARs or assets |
| `app/src/main/AndroidManifest.xml` | App entry point, app label/theme, and Internet permission for server status queries | Created for this independent app using Android manifest conventions | Android manifest/platform APIs only |
| `app/src/main/res/values/strings.xml` | Independent app label | Created for this independent app | Android resource format only |
| `app/src/main/res/values/styles.xml` | Independent basic dark-violet theme | Created for this independent app from Android theme attributes; no external theme package | Android framework theme only |
| `app/build.gradle` | App ID, SDK levels, Java language level, and app build configuration | Created for the standalone project | Android Gradle Plugin is build-only; see dependency inventory |
| `build.gradle` | Pins Android Gradle Plugin version for the standalone project | Created for the standalone project | Build-only plugin from Google Maven |
| `settings.gradle` | Standalone repository/plugin repositories and `:app` project wiring | Created for the standalone project | Gradle repository metadata only |
| `gradle.properties` | Standalone Gradle/Android build properties | Created for the standalone project | Build configuration only |
| `tests/test_clean_room.py` | Regression/source-boundary checks | Written for this repository to prevent accidental boundary regressions; it is not shipped in the APK | Python standard library only |

## Non-source assets and generated content

- No Minecraft client JARs, game assets, Java runtime binaries, renderer/native libraries, loader binaries, or upstream launcher source files are intended to be bundled in this independent app milestone.
- Server favicons are received from the server at query time and held in memory for display; they are not checked into the repository or packaged as built-in launcher assets.
- The independent app uses its own app ID and package, and the CI workflow builds only `voidcraft-android/`.

## Automated guardrails

`tests/test_clean_room.py` checks the Java package/source boundary, declared external runtime dependencies, the absence of legacy checkout steps in the independent workflow, required source files, server-status behavior, and build/network permission assumptions. These checks reduce accidental regressions but **cannot prove authorship, detect every copied fragment, clear assets or commit history, or replace a full legal audit**.

## Before adding anything new

1. Identify the exact source URL, version/commit, author, and license for any non-original source, asset, font, icon, SDK, action, native library, or binary.
2. Confirm its license actually permits the intended use and redistribution; preserve required notices and source offers.
3. If provenance/license is unknown or marked “All Rights Reserved,” do not ship it until permission or a documented, rights-cleared replacement is obtained.
4. Update this file and `DEPENDENCY_INVENTORY.md`; add a test when a machine-checkable boundary can prevent accidental bundling.
5. Do not remove copyright or license notices from third-party code. Compliance is achieved by following them or replacing the component lawfully, not by deleting notices.

## Explicit exclusions

This file does not cover the repository's legacy root build, which fetches a pinned Amethyst-Android checkout and applies VoidCraft patches. That path remains **not cleared by this record**. See [the repository audit](../COPYRIGHT_AND_LICENSE_AUDIT.md).
