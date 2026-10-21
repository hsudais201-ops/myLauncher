# Feature Store catalog

The Android launcher reads the public catalog from:

`https://raw.githubusercontent.com/hsudais201-ops/myLauncher/main/store/catalog.json`

## Publishing a listing

Add an object to the `items` array. Required fields: `id`, `name`, `kind`, `category`, and `availability`. Recommended fields include `tagline`, `description`, `version`, `compatibility`, `license`, and `packageUrl`.

- Use a stable, unique `id` containing only letters, numbers, dots, underscores, or hyphens.
- `kind` should be `launcher` or `content`.
- `availability` should be `available`, `preview`, or `coming_soon`.
- Use `builtIn: true` only for a feature actually implemented in the launcher.
- A downloadable package must use an HTTPS URL and must be licensed for redistribution. Do not publish access tokens or private URLs.
- A listing with no package URL remains a preview and cannot be installed.
- Catalog updates appear after the catalog is merged to `main` and the user refreshes the Store or opens it while online.

## Current limitations

The Store interface and catalog refresh are implemented in the bootstrap. Built-in theme options can be enabled, opened, and uninstalled. External packages are currently downloaded to Voidcraft's private app storage; they are **not** installed into a Minecraft profile yet. Dynamic native Kotlin/Android code loading is not supported by this catalog design. A real game-content installer must validate package hashes/signatures, compatibility, safe archive paths, and destination folders before enabling Mods, Resource Packs, Shaders, or Worlds as installable.
