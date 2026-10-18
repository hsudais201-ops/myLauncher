# VoidCraft Feature Store

The Feature Store is a new, separate extension system. It does **not** replace or remove Amethyst-Android, native libraries, Java runtimes, rendering, game/version download, Forge/NeoForge/Fabric-like/OptiFine loaders, or built-in Modrinth/CurseForge integration.

## What a feature package is

A `.vcfeature` file is a UTF-8 JSON manifest (maximum 1 MiB) with `schemaVersion: 1`, a stable `id`, `name`, `version`, `author`, `owner`, `description`, and a `screen` definition. There is no category/taxonomy field. The current renderer supports `theme`, `server_status`, `page`, and `social` screen types. Page widgets are declarative text, toggle, and HTTPS-link controls.

Feature packages are data, not executable Java, DEX, shell, or scripts. This keeps a community upload from silently gaining the launcher's privileges or replacing core files. Unsupported widgets/actions are ignored rather than executed.

## Add a feature to the public catalog

1. Create and test a feature in the launcher's **Feature Store → Create** screen.
2. Export/share the resulting `.vcfeature` package.
3. Add the package under `feature-store/packages/` in a pull request.
4. Add a catalog entry to `feature-store/catalog.json` with its id, title, version, author/owner, description, package asset path, HTTPS download URL, and SHA-256 of the exact package bytes.
5. Keep the package id identical in the catalog and package manifest. Do not publish secrets, tokens, personal data, or executable payloads.

The launcher includes a bundled catalog for offline browsing and checks the public catalog URL for newer entries. Downloaded packages must use HTTPS, match their SHA-256, pass schema validation, and remain within the size limit. Owners can publish through normal repository review; the app does not contain a personal access token or silently write to GitHub. Direct one-tap upload to a shared public service would require a separately hosted authenticated publishing endpoint.

## Current sample features

- **Theme Studio** — changes supported launcher UI colors with named palettes.
- **Server Status** — queries the public mcsrvstat.us service for an entered Java server.
- **Community Resources** — a separate feature screen linking to Modrinth, CurseForge, and the project.
- **Social Hub (Preview)** — profile username, provider-choice UI, local friend-request workflow, contact removal, and local chat previews. This currently stores data on this device only; it does not deliver real friend requests/messages or authenticate with Microsoft/Xbox, Google, or other providers.

The catalog is an additive option; existing launcher pages and integrations stay available.

## Social Hub: what is and is not connected

The Social Hub is an installable UI prototype. Its profile username is separate from the selected Minecraft launcher profile. The Profile, Friends, Requests, and Chats views can be switched with the **Swipe → Next** button. Local preview contacts can be added/removed, incoming requests can be simulated and accepted/declined, outgoing requests can be queued/cancelled, and preview messages can be saved on the device. Those actions are not visible to any other player. The Microsoft/Xbox, Google, and other-provider buttons explain that authentication is not configured; they do not ask for passwords and do not pretend to sign in.

Real cross-device chat requires a hosted service for account identity, server-side authorization, friend/request state, message storage/delivery, abuse controls, and push/online status. Linking real Microsoft/Xbox identities additionally requires the appropriate Xbox authentication/service access and permissions; the existing Microsoft sign-in used to launch Minecraft is not by itself a chat/social API. Google or other account support needs configured OAuth clients plus server-side token verification. No backend, OAuth secrets, or messaging credentials are bundled in this preview.
