# VoidCraft Original Codebase Migration Plan

**Goal:** replace the Amethyst-derived launcher application code with an independently designed and implemented VoidCraft Launcher codebase, while preserving user data and maintaining a working Android launcher throughout migration.

## Current architecture (verified from the repository)

The current repository is primarily a build/customization layer, not a complete standalone Android launcher source tree. The GitHub Actions workflow fetches a pinned Amethyst-Android revision and applies VoidCraft patches to it before building the APK. The README explicitly identifies Amethyst as the runtime engine. Therefore, changing only the branding/customizer scripts cannot make the whole launcher code original.

## Rules for this migration

- Do not copy/paste Amethyst implementation files into a new VoidCraft package and merely rename symbols. Design and implement VoidCraft modules independently.
- Maintain a written dependency and license inventory for every retained library, runtime, renderer, native component, and build tool. Original VoidCraft app code does not automatically make third-party dependencies license-free.
- Never bundle proprietary Minecraft game files. Download required game files through legitimate supported flows and respect Minecraft/Microsoft terms.
- Do not remove the existing launcher or its essential runtime path until the replacement can build, install, open, authenticate where supported, install/verify a version, and launch a test game on target devices.
- Preserve existing user profiles, worlds, options, instances, mods, resource packs, screenshots, logs, and other user data. Add migration/backup/rollback code before changing storage schemas.
- Keep the current working app available on a separate branch/release during migration. Develop the rewrite on this branch and use reviewable pull requests.
- Never describe a feature as working based on UI placeholders or a successful compile alone. Record test evidence and unresolved compatibility limits.

## Proposed original architecture

Create a new Android application module with a VoidCraft-owned application ID/package and independently authored code. Keep engine integration behind interfaces so the launcher UI and business logic do not depend directly on upstream activity classes.

Suggested modules/interfaces:

- `app`: application entry point, lifecycle, navigation, themes, accessibility and error reporting.
- `core/model`: version, instance, account reference, feature manifest, download and launch result models.
- `core/storage`: repository interfaces, atomic writes, schema migrations, backup/restore, scoped-storage handling.
- `core/download`: cancellable/resumable downloads, checksums where available, retries, progress and safe temporary-file promotion.
- `core/versions`: version metadata resolution, inheritance validation, libraries/assets/client-file verification and repair.
- `core/accounts`: account-provider interfaces and secure token storage; implement Microsoft sign-in only through supported OAuth flows.
- `core/runtime`: a narrow launch/runtime interface; keep Java/runtime/native integration as a separately audited dependency until a viable replacement exists.
- `core/rendering`: renderer selection/capability reporting and diagnostics; do not claim unsupported graphics APIs.
- `features/store`: signed/validated feature manifests, permission declarations, compatibility checks, installation rollback, uninstall cleanup and dedicated feature pages. Do not allow arbitrary uploaded executable code to run with unrestricted app privileges.
- `features/performance`: device diagnostics, user-selectable graphics presets, uncapped FPS option where the game supports it, settings backup/restore, and evidence-based performance reports.
- `features/instances`: instance creation/clone/import/export, per-instance settings and safe data migration.
- `features/mods`: mod/resource-pack/shader management with compatibility warnings and non-destructive file operations.
- `test`: unit tests, integration tests, Android emulator smoke tests, and documented physical-device test matrix.

These are target boundaries, not a claim that the modules already exist.

## Migration stages and acceptance gates

### Stage 0 — Inventory and license audit
- Record all source directories currently fetched from Amethyst, patch scripts, native libraries, Java runtimes, renderer components, transitive dependencies, licenses, and upstream-specific APIs.
- Classify each item as: original VoidCraft code, third-party library, licensed upstream component, or unresolved.
- Capture current build output and behavior as a baseline.
- **Gate:** no component is removed before its role, license, replacement, and data impact are documented.

### Stage 1 — Independent app shell
- Create the new app module, VoidCraft navigation, theme, dashboard, settings, diagnostics, and test harness without copying upstream UI source.
- Add CI that builds the new module independently.
- **Gate:** install, cold start, rotation, process recreation, accessibility basics, and error handling pass on emulator.

### Stage 2 — Data and instance layer
- Define explicit schemas and adapters for existing profiles, options, worlds, mods, resource packs, screenshots, logs, and version metadata.
- Build read-only discovery first, then migration with backups, idempotency, and rollback.
- **Gate:** migration tests show no lost or overwritten files; old and new versions can read the preserved data where formats permit.

### Stage 3 — Download and version services
- Implement independent metadata parsing, download queue, progress/cancel/retry, integrity checks where authoritative hashes exist, asset/library resolution, and repair reports.
- Use official endpoints and supported distribution sources; do not mirror or embed proprietary game binaries in the repository.
- **Gate:** tests cover interrupted downloads, corrupt/missing files, inherited versions, disk-full errors, and offline behavior.

### Stage 4 — Runtime adapter and first launch
- Specify the interface that turns a validated instance into a runtime launch request.
- Evaluate the legal and technical options for Java VM, native libraries, input, windowing, and renderer integration. Keep retained components isolated and properly attributed until replacements are proven.
- **Gate:** a legally obtained test version reaches the title screen on the target device matrix, with logs captured and no regressions in existing user data.

### Stage 5 — Feature parity, store, and performance
- Port required launcher features one by one into original modules, including modpack import/export and the touch-control designer in the core launcher as specified by the project owner.
- Keep optional feature-store items separately installable, with signed manifests, permissions, compatibility checks, rollback and dedicated pages.
- Keep FPS user-configurable/uncapped where supported; do not impose arbitrary 30/60 FPS limits. Clearly report that hardware, refresh rate, game version, and renderer determine real FPS.
- **Gate:** each feature has automated tests plus relevant emulator/device acceptance evidence.

### Stage 6 — Remove upstream build dependency
- Remove the automatic upstream source fetch and patch pipeline only after every required replacement passes its acceptance gate.
- Retain a dependency notice, license texts, notices, SBOM/dependency inventory, architecture docs, testing docs and a release rollback plan.
- **Gate:** clean-room CI builds the original VoidCraft application from this repository, with no hidden fetch of Amethyst application source and no unreviewed upstream patches.

## Required documents to maintain

- `ARCHITECTURE.md`: module boundaries and launch/data flow.
- `LICENSES.md` or `THIRD_PARTY_NOTICES.md`: component-level license and attribution inventory.
- `TESTING.md`: reproducible unit, integration, emulator and device tests.
- `DATA_MIGRATION.md`: schemas, backup/restore, migration and rollback behavior.
- `CHANGELOG.md`: user-visible changes and compatibility notes.

## Current status

**Independent shell, metadata-only profile screen, version metadata readers, and a secure file downloader are implemented as separate core code; migration remains incomplete.** The independent app includes a validated `InstanceProfile`, versioned app-private metadata catalog, profile screen, Mojang version-manifest parser/client, client/asset-index metadata parser, and HTTPS downloader. The downloader uses size bounds, checks any supplied expected SHA-1/SHA-256, supports cancellation, retries transient failures, and stages output before replacing a destination. It restarts failed downloads from the beginning and is not resumable yet.

Earlier profile catalog tests plus debug APK build passed in [run #39359802263](https://github.com/hsudais201-ops/myLauncher/actions/runs/39359802263), before the new metadata/downloader files existed. The current branch's tests now include metadata validation and downloader retry/cancellation/integrity cases; wait for the current Actions run to confirm them before marking the new code as passing.

These services are not wired into an installation UI and do not constitute a Minecraft installer. Library/assets dependency resolution, inherited-version merge, persistent download queues/resume, supported account auth, Java/native runtime, rendering/input, feature-store, touch-control designer, modpack import/export, and existing-launcher data discovery/migration remain incomplete. No physical-device test or actual Minecraft launch is claimed. The current Amethyst-based build is unchanged.

A preliminary source/license audit is recorded in `VOIDCRAFT_SOURCE_AND_LICENSE_AUDIT.md`. The independent module now records Gson 2.11.0's Apache-2.0 license in `voidcraft-app/THIRD_PARTY_NOTICES.md`. This is still not a complete SBOM or legal clearance. No claim is made that the whole launcher is already copyright-free. Keep the existing working build intact until the replacement proves feature, runtime, data-preservation, license, and device parity.
