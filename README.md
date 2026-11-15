# MFLegend

MFLegend is a native Minecraft: Java Edition launcher built with Tauri 2, Rust, React, TypeScript, and Vite. The current target environment is Linux, including Chromebook Linux containers.

## Current features

- Mojang version manifest browsing and version selection.
- Vanilla installation pipeline for client JARs, libraries, asset indexes, and assets, with SHA-1 verification.
- Fabric installer integration and an initial Forge installer workflow.
- Offline-mode Java launch with runtime selection, memory allocation, inherited version metadata, and launch argument handling.
- Separate installation profiles with independent game directories; create, select, rename, duplicate, and remove launcher entries without deleting their files.
- Installed version/profile scanning.
- Local mod and resource-pack file management.
- World import, export, backup, and removal.
- Local PNG skin library and preview.
- Theme, accent, Java runtime, and game-directory preferences.
- Microsoft Entra public application Client ID configuration in Settings, preparing for a future device-code sign-in flow.

## Important limitations

- Microsoft account sign-in, Xbox Live/XSTS exchange, Minecraft Services authentication, and secure token storage are **not implemented**. The Client ID field only stores public application configuration; it does not sign users in.
- Launch currently uses an offline identity. Official online-mode servers generally require Microsoft authentication.
- The skin library is local-only; it does not equip or upload skins to a Minecraft account.
- Forge installer compatibility and all game-version combinations require additional runtime testing.
- Installation and launch require the native desktop app; browser preview cannot perform those operations.

## Development

Prerequisites: Node.js 20+, Rust stable, and the Tauri Linux system dependencies for your distribution.

```bash
npm install
npm run tauri dev
```

For a frontend-only preview: `npm run dev`.

## Microsoft application configuration

To prepare for a future Microsoft device-code flow, register a **public/native desktop application** in Microsoft Entra, enable the device-code flow, and enter its Application (client) ID in MFLegend Settings. Do not create or embed a client secret. Supplying a Client ID does not currently enable sign-in.

## Security and distribution

- Never store Microsoft passwords or client secrets in launcher preferences.
- Implement token persistence only through an operating-system credential store (such as Secret Service/libsecret or the platform equivalent).
- Obtain Minecraft assets and libraries through official metadata and legitimate distribution endpoints.
- Review Mojang/Microsoft terms before distributing builds.
