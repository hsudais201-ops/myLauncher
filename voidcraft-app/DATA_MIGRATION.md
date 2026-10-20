# VoidCraft data preservation and migration

## Current behavior

The independent shell does not access the existing launcher's storage. The new `FileInstanceCatalogRepository` stores only VoidCraft instance metadata in the app-private directory passed to it by its caller. On Android, production wiring should use the new app's `Context.filesDir`.

The catalog's schema version is currently `1`. Each record contains:
- Validated instance ID.
- Display name.
- Minecraft version string.
- Loader ID.
- Creation timestamp.

The logical game-directory path is derived as `instances/<validated-id>/game`. The catalog code does not create that directory and does not use this relative path to access the filesystem.

## Safety guarantees currently implemented

- Catalog writes use a same-directory temporary file and an atomic move when supported.
- A malformed catalog or unknown schema throws an I/O error instead of being silently replaced with an empty catalog.
- IDs are constrained to letters, digits, underscores, and hyphens; path traversal input is rejected.
- Updating an ID replaces its metadata record rather than creating a duplicate.
- Removing an ID deletes only the record from the catalog. It does not delete saves, mods, or files under the instance directory.

## Not yet implemented

- Read-only discovery of existing launcher profiles and data locations.
- Conversion of existing profile/options formats.
- Copying or moving worlds, mods, resource packs, screenshots, logs, or account settings.
- Backup creation and restore tooling.
- Migration preview, rollback, and duplicate/conflict resolution.
- Cross-version validation of Minecraft metadata, mod-loader data, or game directories.

No migration should run automatically until those pieces exist and have tests showing that a failed migration leaves source data intact.

## Required migration protocol

1. Discover candidate data locations read-only; never assume a path based only on device type.
2. Show exactly which files and settings are proposed for migration.
3. Validate paths and available storage, and report unsupported entries before copying.
4. Create and verify a backup before writes.
5. Copy to a staging location; verify file counts and hashes where useful and practical.
6. Commit by an explicit, recoverable switch only after validation.
7. On any error, roll back the new destination and leave the original files in place.
8. Test interrupted migration, insufficient storage, corrupt metadata, duplicate IDs, and restore.
