# Pinned Minecraft Java runtime engine source

Flame's selected runtime candidate is **Amethyst-Android**, maintained by AngelAuraMC.

- Upstream: https://github.com/AngelAuraMC/Amethyst-Android
- Pinned upstream ref: `v3_openjdk`
- Pinned commit: `28d6e12d68121b7dd75f2e841058a6b7cdfe4a29`
- License at the inspected upstream revision: LGPL-3.0, with separately licensed third-party components.

The submodule is pinned to an immutable commit. It is intentionally not checked out by Flame's regular Android CI yet: GitHub reports the upstream repository at roughly 1.4 GB, and recursive submodules/native assets can be large. The Flame UI build and unit tests should remain fast and independent while the integration work identifies the minimum native components that need to be built.

## Fetch the pinned source

Use a shallow clone for the top-level source:

```bash
git submodule update --init --depth 1 --recommend-shallow engine/amethyst
```

Initialize nested upstream submodules only when working on the native engine build:

```bash
git -C engine/amethyst submodule update --init --recursive --depth 1
```

The upstream `.gitmodules` includes public dependencies such as SDL, sdl2-compat, MobileGlues, and MioLibPatcher. One upstream URL uses SSH syntax; when using a CI environment without an SSH key, configure a Git URL rewrite to HTTPS before initializing nested modules.

## Integration boundary

The pinned source is not yet compiled into Flame's APK and does not yet launch Minecraft from the Flame UI. It is a source reference and reproducible dependency pin for the next integration stage.

Before APK redistribution, audit the license of every included component and binary. Preserve LGPL notices and corresponding source obligations, and exclude or obtain explicit permission for components whose terms do not permit the intended redistribution. Do not bundle upstream prebuilt JREs, renderer libraries, or game assets as part of this source pin.
