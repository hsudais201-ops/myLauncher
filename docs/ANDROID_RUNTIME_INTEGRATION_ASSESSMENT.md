# Android JVM and renderer integration assessment

**Assessment date:** 2026-11-16  
**Decision:** Use Amethyst-Android as the primary technical reference for a future integration spike. Do not copy source or bundle its runtime artifacts until the dependency and redistribution audit is complete.

## Why this reference

The former PojavLauncher repository is archived and explicitly points to Amethyst-Android as its successor. The successor repository is active, uses the `v3_openjdk` branch, and its project README describes an Android Java Edition launcher with OpenJDK 8, 17, and 21 mobile ports for ARM32, ARM64, x86, and x86_64. It also documents OpenGL, OpenAL, native input, and the custom LWJGL stack.

- Amethyst-Android: https://github.com/AngelAuraMC/Amethyst-Android
- Build instructions and runtime artifact flow: https://github.com/AngelAuraMC/Amethyst-Android/blob/v3_openjdk/README.md
- Runtime build project: https://github.com/AngelAuraMC/openjdk-build-multiarch
- Custom LWJGL: https://github.com/AngelAuraMC/lwjgl3
- Former PojavLauncher archive: https://github.com/PojavLauncherTeam/PojavLauncher

The Amethyst README documents a quick build that uses prebuilt JREs supplied by its CI. Its detailed path downloads a `jre8-pojav` artifact from the OpenJDK multi-architecture build project and places it in the launcher's JRE assets. This is useful implementation evidence, but it is not yet an approved runtime distribution mechanism for Flame Launcher.

## Licensing and redistribution gate

The Amethyst-Android repository identifies its own license as GNU LGPLv3. Its README separately lists components with different terms, including OpenJDK under GPLv2, GL4ES under MIT, MobileGlues under LGPL-2.1, LWJGL3 under BSD-3, SDL3 under zlib, ANGLE as All Rights Reserved, and LWJGLX as unknown license.

Before reusing any code or binary, Flame Launcher must:
1. Identify the exact source repository, commit, submodule revision, and artifact version.
2. Read the license and notices for that exact component and confirm the license covers the intended use and distribution.
3. Resolve all unknown or restrictive entries (notably LWJGLX and ANGLE) before bundling or redistributing them.
4. Preserve required copyright notices, license texts, source-offer/source-distribution obligations, and modification notices where applicable.
5. Verify the OpenJDK build artifact's provenance, architecture, checksum, and redistribution terms; do not assume a CI artifact is automatically safe to redistribute.
6. Keep a third-party notices inventory in the repository and include the required notices in app packaging.

This is an engineering assessment, not legal advice or a completed license review.

## Pinned upstream review baseline

A reproducible review of the current `v3_openjdk` branch was performed at commit `166af615dbdca1a507b8c2d379822cca02795132` (2026-11-13). The pinned tree declares multiple submodules and the README identifies additional components with unknown or restrictive license statements. See [`AMETHYST_DEPENDENCY_AUDIT.md`](AMETHYST_DEPENDENCY_AUDIT.md) for the commit identity, observed gitlinks, dependency inventory, runtime artifact provenance requirements, and explicit stop conditions. This is a source-review baseline only; no integration or redistribution is approved.

## Proposed integration strategy

1. Keep Flame Launcher's Kotlin/Compose UI and existing SAF-aware installation pipeline.
2. Build a separate experimental branch or isolated Android module for the JVM/renderer proof of concept; do not replace the current app shell.
3. Pin Amethyst and all native dependencies to reviewed commits. Prefer documented APIs or a narrow adapter boundary over copying large source trees.
4. Start with one ARM64 Android device and one known Minecraft version. Confirm the JVM can load under Android's linker, the required native libraries resolve, and the renderer can create and retain an Android surface.
5. Only after that, connect Flame Launcher's version metadata and verified game files to the runtime adapter.
6. Add input, audio, pause/resume, process shutdown, and crash recovery tests before enabling a Play action.
7. Maintain separate runtime bundles per ABI and supported Java major version. Do not infer Minecraft compatibility from the runtime's `release` file alone.

## Current Flame Launcher status

Flame Launcher now lets a user import an already-extracted ARM64 runtime folder into its private `noBackupFilesDir/java-runtimes/<id>` directory. The importer validates relative document names, path depth, entry count, per-file and total byte limits, Java `release` metadata, matching ARM64 process ABI, and the AArch64 ELF64 header of `libjvm.so`; the runtime scanner repeats the metadata/path/ELF checks. This is user-supplied runtime staging only: Flame does not yet download, bundle, execute, or load a JRE. The importer reads the `release` metadata through a strict streaming byte limit (rather than trusting a file-length check before an unbounded read), and the scanner applies the same bounded-read principle. The importer and scanner do not prove bionic/linker compatibility, JNI startup, graphics/audio/input support, or successful Minecraft gameplay. No Amethyst source, JRE binaries, or renderer libraries have been copied into Flame Launcher.
