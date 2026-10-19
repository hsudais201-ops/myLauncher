# VoidCraft clean-room implementation policy

## Goal

Build a first-party VoidCraft Launcher codebase from functional requirements, without copying or translating source files from Amethyst-Android, PojavLauncher, or another launcher.

## Rules for new first-party code

- Write new source from documented behavior and public platform/API documentation, not by renaming or mechanically rewriting upstream files.
- Keep the independent implementation under `voidcraft-android/` until it can be built and validated on its own.
- Do not import upstream launcher packages, copy upstream screens, or reuse upstream patch/customizer scripts in the clean-room app.
- Record every third-party dependency and its license before adding it. Prefer Android platform APIs or well-documented permissively licensed libraries when they meet the requirement.
- Maintain `voidcraft-android/SOURCE_PROVENANCE.md` for each first-party source file and all later additions. Do not remove copyright/license notices from third-party material; replace a component or comply with its terms instead.
- Do not describe the entire repository or APK as “copyright-free” until source provenance and every shipped component have been audited; automated keyword scans are not legal clearance.
- Treat Minecraft game files, Microsoft/Xbox authentication, Java runtimes, native libraries, renderers, and loader formats as separate technical/legal integration work. Do not claim these are implemented just because a UI exists.
- Keep the existing engine-based build and the new clean-room build separate while the new path is incomplete. Do not remove the existing workflow or user-data paths as part of the initial scaffold.

## Current status

The existing root workflow still fetches a pinned Amethyst-Android source checkout and applies VoidCraft modifications. It is **not** a clean-room build. The repository does not currently have a verified, repository-wide copyright clearance or a top-level license covering all files.

The `voidcraft-android/` subtree is the new independent application foundation. Its dedicated CI job builds this subtree without fetching the upstream launcher repository. It provides a first-party navigation/dashboard shell, local settings, on-device saved-server CRUD, and an optional installable Server Status feature with its own screen. The feature performs bounded, read-only Minecraft: Java Edition status queries. The app does not launch Minecraft; live server status is not gameplay validation. Remote community catalog, upload, publisher verification, and package signing remain unimplemented.

See [COPYRIGHT_AND_LICENSE_AUDIT.md](COPYRIGHT_AND_LICENSE_AUDIT.md) for the repository-wide risk inventory, [voidcraft-android/SOURCE_PROVENANCE.md](voidcraft-android/SOURCE_PROVENANCE.md) for clean-room source origins, and [voidcraft-android/DEPENDENCY_INVENTORY.md](voidcraft-android/DEPENDENCY_INVENTORY.md) for declared build/runtime dependencies. These are engineering guardrails, not legal certification.

## Completion criteria

The independent app must build from its own tracked source, have tests for core behavior and package boundaries, and pass Android emulator/device checks before it can replace the current app. A full clean-room migration additionally requires an original or properly licensed runtime/integration strategy, license review for all included components, and data migration tests for existing installations, worlds, and settings.
