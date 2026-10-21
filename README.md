# VoidCraft Launcher

**Owner/developer:** MF

VoidCraft Launcher is an Android launcher for Minecraft: Java Edition, built on the public [MojoLauncher](https://github.com/MojoLauncher/MojoLauncher) codebase. This migration branch pins MojoLauncher source commit `8ffba8f4ea0b9466854aa9f7a375c77b4fb96bdd` on `v3_openjdk` and applies a small, reviewable VoidCraft identity layer.

## Current migration status

This is a source-base migration, not a claim that every previous VoidCraft customization has already been ported. The initial layer changes the application ID/provider authorities, app and crash labels, version prefix, night-violet palette, original VC monogram app icon, and home-screen hero panel. MojoLauncher’s launcher, Java/runtime integration, native libraries, renderers, version downloads, and account flow are retained from the pinned upstream source.

**Not yet ported/verified in this migration:** prior Amethyst-specific VoidCraft UI patches and extras such as enhanced folder toggles, world-backup screens, redacted log sharing, settings export/import, the Performance Advisor, the optional Feature Store, and other custom screens. These must be adapted to Mojo’s source and regression-tested before they are described as available here.

The first acceptance gate passed on CI run [#39622376823](https://github.com/hsudais201-ops/myLauncher/actions/runs/39622376823): the full debug APK built, package/signature/minimum-SDK checks passed, and the APK installed and launched in an Android 9/API 28 x86_64 emulator. The recorded build was produced from commit `c0fd0dc5ffe2f544f558183c46ed30150f166908`; the current branch additionally hardens one layout-anchor matcher. This smoke test does not prove that Minecraft enters a single-player world or runs on physical Chromebooks/phones.

## Build

The GitHub Actions workflow pins the upstream commit, checks out its submodules, applies the branding script, builds the full debug variant, verifies its package and signature, then tests an install/launch in an API 28 x86_64 emulator. Successful runs publish a debug APK artifact and diagnostic logs.

To build from the upstream checkout locally:

```bash
./gradlew --no-daemon :app_mojo:assembleFullDebug
```

The CI pipeline requires Java 21 and the Android SDK/NDK versions pinned in `.github/workflows/build.yml`.

## Application identity

- Display name: VoidCraft Launcher
- Android application ID (debug): `com.mf.mylauncher.debug`
- Android app module: `app_mojo`
- Upstream Java namespace retained for compatibility: `git.artdeell.mojo`
- UI palette: night violet

Do not assume old data migration or APK-over-install compatibility from package ID alone. Test installation with a clean profile first, and back up worlds and configuration before trying a migration build on a device containing existing launcher data.

## Source and licensing

MojoLauncher identifies its code as GNU LGPL-3.0 licensed and includes third-party components with their own licenses. See [MOJO_UPSTREAM_NOTICES.md](MOJO_UPSTREAM_NOTICES.md). Keep upstream notices and corresponding source/change information available when redistributing; audit each submodule and dependency before any public release. This project does not include Minecraft game assets or grant a Minecraft license.
