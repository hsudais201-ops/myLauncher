# VoidCraft Feature Store

The Feature Store is a new, separate extension system. It does **not** replace or remove Amethyst-Android, native libraries, Java runtimes, rendering, game/version download, Forge/NeoForge/Fabric-like/OptiFine loaders, or built-in Modrinth/CurseForge integration.

## What a feature package is

A `.vcfeature` file is a UTF-8 JSON manifest (maximum 1 MiB) with `schemaVersion: 1`, a stable `id`, `name`, `version`, `author`, `owner`, `description`, and a `screen` definition. There is no category/taxonomy field. The current renderer supports `theme`, `server_status`, `page`, and `social` screen types. Page widgets are declarative text, toggle, and HTTPS-link controls.

Feature packages are data, not executable Java, DEX, shell, or scripts. This keeps a community upload from silently gaining the launcher's privileges or replacing core files. Unsupported widgets/actions are ignored rather than executed.

## Add a feature to the public catalog

1. Create and test a feature in the launcher's **Feature Store → Create** screen.
2. Export/share the resulting `.vcfeature` package.
3. Add the package under `feature-store/packages/` in a pull request.
4. Add a catalog entry to `feature-store/catalog.json` with its id, title, version, author/owner, description, HTTPS `packageUrl`, and SHA-256 of the exact package bytes. The catalog carries metadata and a download URL only; it does not embed the package.
5. Keep the package id identical in the catalog and package manifest. Do not publish secrets, tokens, personal data, or executable payloads.

The APK contains the Feature Store manager and its safe declarative renderer only. It does **not** bundle the public catalog or any optional `.vcfeature` package, and no sample feature is preinstalled. When the user opens the store, it fetches catalog metadata over HTTPS and saves only that metadata as a private cache for later offline browsing. Each package is downloaded from its HTTPS `packageUrl` only after the user taps **Install** or **Update**; the SHA-256 is checked before schema validation and storage under the app-private `files/feature-store/installed/` directory. Opening an installed feature reads that separately installed package.

A cached catalog is only a list of available items, not installed functionality. On a first launch without internet and without a saved catalog cache, the store can still show its manager and allow local package import, but it cannot fetch the online list or install online features. Uninstall removes the selected installed package; it does not remove launcher/runtime files, worlds, accounts, or other features. Owners can publish new entries through a reviewed repository change; the app does not contain a personal access token or silently write to GitHub. Direct one-tap publishing to a shared public store requires a separately hosted authenticated publishing endpoint.

## Available sample packages in the remote catalog (not preinstalled)

These entries are offered by the remote store and appear in **Installed features** only after a user explicitly installs them.

- **Theme Studio** — changes supported launcher UI colors with named palettes.
- **Server Status** — save multiple Java Edition servers, add each with a display name and host/IP, refresh an individual server, edit its name/address, or remove it from the saved list. Status cards show ONLINE, OFFLINE, or UNKNOWN, current/max player counts, MOTD, version, and server icon/logo when supplied. Uses the public mcsrvstat.us service over HTTPS.
- **Community Resources** — a separate feature screen linking to mod discovery and launcher project resources.
- **Social Hub (Preview)** — profile username, provider-choice UI, local friend-request workflow, contact removal, and local chat previews. This currently stores data on this device only; it does not deliver real friend requests/messages or authenticate with Microsoft/Xbox, Google, or other providers.

## Social Hub: what is and is not connected

The Social Hub is an installable UI prototype. Its profile username is separate from the selected Minecraft launcher profile. The Profile, Friends, Requests, and Chats views can be switched with the **Swipe → Next** button. Local preview contacts can be added/removed, incoming requests can be simulated and accepted/declined, outgoing requests can be queued/cancelled, and preview messages can be saved on the device. Those actions are not visible to any other player. The Microsoft/Xbox, Google, and other-provider buttons explain that authentication is not configured; they do not ask for passwords and do not pretend to sign in.

Real cross-device chat requires a hosted service for account identity, server-side authorization, friend/request state, message storage/delivery, abuse controls, and push/online status. Linking real Microsoft/Xbox identities additionally requires the appropriate Xbox authentication/service access and permissions; the existing Microsoft sign-in used to launch Minecraft is not by itself a chat/social API. Google or other account support needs configured OAuth clients plus server-side token verification. No backend, OAuth secrets, or messaging credentials are bundled in this preview.
