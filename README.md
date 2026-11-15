# MFLegend

A native Minecraft: Java Edition launcher for Linux and Chromebook Linux environments.

## Current implementation
- Tauri 2 + React + TypeScript desktop application.
- Live Mojang version manifest with release/snapshot filtering.
- Native Java runtime discovery (Linux PATH and common install locations).
- Persistent launcher preferences and selected version.
- Responsive dark launcher interface with navigation and clear feature status.

## Development
Prerequisites: Node.js 20+, Rust stable, and the Tauri Linux system dependencies for your distribution.

```bash
npm install
npm run tauri dev
```

For a frontend-only preview: `npm run dev`.

## Scope and safety
This is an actively developed launcher foundation, not yet a complete Minecraft distribution/launch implementation. The UI does not pretend that installation, authentication, mod loading, worlds, or skin management are complete. Microsoft authentication must use the official device-code/OAuth flow and secure OS credential storage; never put client secrets or passwords in the app.

Minecraft assets and libraries must be obtained from official metadata and legitimate distribution endpoints. Review Mojang/Microsoft terms before distributing builds.
