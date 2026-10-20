# VoidCraft source and license audit (preliminary)

**Status: partial inventory, not legal clearance.** This document records the evidence checked during the independent-codebase migration. A full dependency and distribution audit is still required before release.

## Findings from the VoidCraft repository

- The tracked tree on `main` currently contains the build workflow, customization/test scripts, roadmap, README, ignore file, and a branding SVG. It does not contain the full Android application source tree and has no root `LICENSE` file in the inspected tree.
- `.github/workflows/build.yml` fetches the Amethyst-Android repository at the pinned commit `330c6eae3164df64bdc4828e946a9e62cc5169e4`, initializes submodules, applies VoidCraft customizations, then builds the upstream `app_pojavlauncher` and GLFW module.
- The existing launcher is therefore not yet an independently implemented application. Branding or package-name changes do not make the upstream implementation original.
- The separate `voidcraft-app/` module contains new VoidCraft-written dashboard/profile-management code. Its profile catalog stores metadata only and does not bundle Minecraft game assets or game binaries.

## License evidence at the pinned upstream commit

The `LICENSE` file at the inspected Amethyst-Android commit is GNU Lesser General Public License version 3 (LGPL-3.0). The upstream README also identifies Amethyst as LGPLv3. If the upstream application/runtime remains in a distributed build, its applicable license obligations remain relevant; the independent shell does not remove them.

The upstream README separately lists components with individual license/notice terms, including:
- Boardwalk: upstream README says license status is unknown / Apache-2.0 or GPLv2.
- PojavLauncher: upstream README identifies its stated GLGPL license.
- Android Support libraries: Apache-2.0.
- GL4ES and Krypton Wrapper: MIT.
- MobileGlues: LGPL-2.1.
- ANGLE: an included “All Rights Reserved” license notice.
- OpenJDK: GPLv2.
- LWJGL3: BSD-3-Clause; LWJGLX is marked unknown.
- Mesa, bhook, libepoxy, and virglrenderer: MIT.
- OpenAL Soft: GPLv2; Oboe: Apache-2.0; pffft: ARR.
- SDL3 and sdl2-compat: zlib.

These are upstream README declarations, not an independently verified component-by-component bill of materials for the exact APK. Each actual downloaded artifact, submodule, native binary, prebuilt JRE, transitive Gradle dependency, and license notice must be checked at the exact version used.

## Submodule and dependency verification still needed

The upstream `.gitmodules` file lists MobileGlues, SDL, sdl2-compat, MioLibPatcher, and androidnsbypass. At the inspected pinned Git tree, gitlink entries were visible for MioLibPatcher (`9a1f807f2b8e7abfd921a3ed5a69e23ed6540b0c`) and androidnsbypass (`9f57982abf007aacc45becfc9328ae06fa6bbe3b`); some other listed paths did not appear as gitlinks in that tree response. Reconcile this against the exact checkout and `git submodule status --recursive` captured by the build before finalizing the SBOM.

The upstream tree also includes embedded `arc_dns_injector` and `forge_installer` source directories. Their source, notices, and transitive dependencies need individual review.

## What is and is not claimed

- New code in `voidcraft-app/` is independently authored for this migration and does not copy Amethyst Activity/UI implementation files.
- The existing Amethyst-based build still fetches and builds upstream application/runtime code.
- No conclusion is made that the current whole launcher APK is “copyright-free.” Copyright still exists in original and third-party code; licenses define permitted use and obligations.
- No conclusion is made that the existing full build is cleared for all distribution models, especially commercial distribution, until the exact dependency inventory and notices are verified.
- Do not bundle proprietary Minecraft files. Existing supported-download/runtime flows must use legitimate distribution mechanisms and respect applicable Minecraft/Microsoft terms.
- The project owner has not selected a separate license for new VoidCraft-authored code in the inspected repository; the absence of a repository license must not be treated as public-domain permission.

## Required next audit actions

1. Generate a reproducible SBOM for the exact upstream commit and built APK, including Gradle dependencies, submodules, native libraries, and prebuilt JREs.
2. Capture each component's source URL, exact revision/version, license file/hash, modifications, and required notices.
3. Resolve all “unknown,” ambiguous, or restrictive terms before redistributing those components.
4. Decide and add a license for new VoidCraft-owned source code; preserve third-party notices separately.
5. Repeat the audit whenever the pinned upstream revision or dependency set changes.
