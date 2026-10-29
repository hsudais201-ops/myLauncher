# MyLauncher — Android Edition

An Android-first launcher project for Minecraft: Java Edition.

## Status
Foundation setup. This folder is the clean starting area for the new implementation while previous repository files await cleanup.

## Planned milestones
1. Mobile launcher UI and navigation.
2. Local offline profile creation.
3. Minecraft version metadata and installation pipeline.
4. Java runtime integration and launch argument assembly.
5. Microsoft account sign-in using the real OAuth flow.
6. Optional feature store with explicit install/uninstall and integrity checks.

## Account modes
- Offline profile: local username for local/testing use; it does not authenticate the player for online-mode servers.
- Microsoft account: must use a real, approved Microsoft/Xbox/Minecraft authentication flow; never simulate successful authentication.

## Security
- Verify downloads against trusted hashes when available.
- Never execute arbitrary downloaded feature code.
- Keep tokens out of logs and source control.
