# VoidCraft architecture (migration work in progress)

## Current implementation

The independent app is in `voidcraft-app/`, uses Kotlin and Android platform Views, and has its own Android application ID: `com.voidcraft.launcher`.

Implemented:
- `MainActivity`: independently authored dashboard and navigation; no upstream Activity or UI code is imported.
- `InstanceListActivity`: profile metadata list/create/remove UI, with explicit messaging that game files are not installed and removal is non-destructive.
- `core/InstanceProfile.kt`: validated metadata model for one game instance.
- `core/InstanceCatalogRepository.kt`: versioned, app-private metadata catalog with temporary-file writes, schema validation, and metadata-only removal.
- `core/InstanceCatalogRepositoryTest.kt`: JVM tests for round-trip persistence, ID validation, schema safety, and preservation of game files on catalog removal.

Not yet implemented or connected:
- Version metadata and game-file downloads.
- Microsoft/Xbox/Minecraft authentication and secure token storage.
- Java runtime and native runtime launch bridge.
- Renderer/input integration.
- Import/export, modpack workflows, touch-control designer, and feature store.
- Read-only discovery and migration of data from the existing launcher.

The Instances dashboard button opens the metadata-only profile screen. Performance, runtime launch, authentication, downloads, and feature-store buttons remain placeholders; they must not be represented as connected services.

## Intended dependency direction

The app UI should call VoidCraft-owned service interfaces. Domain models should not depend on Android Activities or upstream UI classes. Platform-specific file, network, authentication, and runtime adapters should sit behind narrow interfaces so they can be tested and audited independently.

Planned boundaries:
- `core`: stable models and validation.
- `storage`: app-private metadata, safe file operations, backups, and explicit migration adapters.
- `versions`: metadata and installed-file verification.
- `downloads`: bounded, cancellable queue with retry and integrity checking.
- `accounts`: supported provider sign-in and secure credential storage.
- `runtime`: launch request/result contract; retained Java/native components remain audited dependencies until a replacement is proven.
- `features/store`: validated feature manifests, explicit permissions, compatibility checks, rollback, and dedicated feature entry points.

## Data safety invariants

1. Listing an instance never creates, moves, renames, or deletes a game directory.
2. Removing a catalog record never deletes instance files or worlds.
3. Unknown schema versions and malformed catalogs fail visibly rather than being overwritten as an empty catalog.
4. Any future import, migration, or destructive cleanup must include backup, preview/confirmation, rollback, and tests.
5. The existing launcher and its current build path remain available until parity gates are passed.
