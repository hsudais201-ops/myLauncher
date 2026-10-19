# Independent app dependency inventory

**Scope:** only `voidcraft-android/` at this revision. This file is not an inventory of the legacy APK path and is not a legal certification.

| Component | Version / source in this project | Role | Included in app APK? | License / clearance status |
|---|---|---|---|---|
| Android platform APIs | compile SDK 35; minimum API 28; target API 35 | UI, preferences, dialogs, JSON parsing | Android framework is provided by the OS; this project does not vendor framework source | Android platform terms apply; no Android framework sources are copied into this project |
| Android Gradle Plugin | `com.android.application` 8.7.3 from Google Maven | Build-time plugin | No | Build-only dependency. Verify publisher license metadata when maintaining the SBOM; not a runtime library |
| Gradle | 8.9 in the documented CI build | Build orchestration | No | Build-only tool. Its own distribution terms apply |
| Android SDK Platform / Build Tools | Platform 35, Build Tools 35.0.0 | Compile/package | No | SDK package terms accepted by CI; not packaged as launcher runtime code |
| Eclipse Temurin JDK | Java 17 in the independent CI workflow | Compiles Java source | No | Build-only tool; not bundled as the game's Java runtime |
| GitHub Actions | `actions/checkout@v4`, `actions/setup-python@v5`, `actions/setup-java@v4`, `android-actions/setup-android@v4`, `gradle/actions/setup-gradle@v4` | CI automation | No | Workflow-only dependencies; not part of the APK. Review the license/terms of each referenced action separately for repository distribution. |
| Application Java source | `com.voidcraft.launcher` | Original UI shell, local settings, saved-server persistence, optional Server Status feature UI | Yes | Authored for this independent subtree; see [SOURCE_PROVENANCE.md](SOURCE_PROVENANCE.md). Maintainer provenance notes and keyword checks are not legal clearance |
| Minecraft Java status client | `MinecraftServerStatusClient.java`; implements the documented read-only Java Edition status handshake/request | Online/reachable result, MOTD, player counts, version, optional server favicon | Yes | Authored for this app from protocol behavior; uses Java sockets/IO and Android `org.json`. No third-party runtime protocol library is declared. Real-server testing is still pending |
| Minecraft runtime / game files | Not integrated | Java runtime, game JAR/assets, native renderer, loader installers | No | Not present in this app milestone; requires separate technical and license review before integration/distribution |
| Third-party runtime libraries | No explicit `dependencies { ... }` block in `app/build.gradle` at this revision | None | No | No third-party runtime library currently declared by the app module |

## Release-time rules

Every new item—including CI actions and build plugins—must be added to this inventory before merging and must include:

- Exact name, upstream source URL, pinned release/version and checksum.
- SPDX license identifier only where verified against the exact source/release; otherwise write `UNKNOWN`.
- Whether it is build-only, downloaded at runtime, or bundled in the final APK/AAB.
- Required attribution/notice/source-offer obligations and a reference to the actual license text.
- Whether commercial distribution and modification are permitted under the identified terms.
- A test or CI check that proves how the item is packaged.

Do not bundle a component marked `UNKNOWN`, “All Rights Reserved,” or not licensed for the planned use until permission or a legally compatible replacement is documented. Do not assume the top-level Amethyst license covers each of its submodules or native dependencies.

## Out of scope

The root workflow `.github/workflows/build.yml` fetches Amethyst-Android at commit `330c6eae3164df64bdc4828e946a9e62cc5169e4`, initializes submodules and applies customizations. That output has a separate dependency/provenance surface and is **not cleared by this table**. See [the repository copyright audit](../COPYRIGHT_AND_LICENSE_AUDIT.md).

This inventory does not imply that the current app is playable: there is no game downloader, Java runtime, native renderer, loader installation pipeline, Microsoft/Xbox authentication, or full installation/launch pipeline in this subtree. A read-only Minecraft: Java Edition status query exists as an optional feature, but it is not a game runtime and has not yet been verified against a real server on a device.
