# Copyright, provenance, and license audit

**Audit status: INCOMPLETE — do not describe the whole repository or current APK as copyright-free or fully clean-room.**

This is an engineering inventory, not legal advice or a legal clearance opinion. It records what was verified from the tracked repository and identifies what still needs source-level, dependency-level, and legal review.

## Executive summary

The repository currently contains two distinct paths:

1. **Legacy APK path:** `.github/workflows/build.yml` fetches the exact Amethyst-Android commit `330c6eae3164df64bdc4828e946a9e62cc5169e4`, initializes its Git submodules, then runs the VoidCraft customizer and UI/logo patches before building `:app_pojavlauncher:assembleDebug`. This path is derived from an external launcher codebase. It is not a clean-room build.
2. **Independent foundation:** `voidcraft-android/` is a separate Android Gradle project using package `com.voidcraft.launcher` and tracked first-party Java/UI code. It has local settings, saved-server CRUD, and an optional installable Server Status feature with its own screen. That feature implements bounded, read-only Minecraft: Java Edition status queries. The app still does not run Minecraft, download game files, authenticate accounts, install loaders, or provide a remote/community feature catalog. CI passes for the independent subtree; real-server/device testing remains outstanding.

At the pinned upstream revision, the top-level Amethyst `LICENSE` is GNU LGPLv3 (Git blob ID `0a041280bd00a9d068f503b8ee7ce35214bd24a1`); the upstream `README.md` (blob `446ec1c75a70f646e6832917754d54c2cc7ce4ac`) declares LGPLv3 and lists third-party credits. Its `.gitmodules` (blob `83f9bdb3277019fa32fd2c3d1e5d8a2cbe4441a0`) lists five direct submodules. These are traceable facts, not a complete license clearance; recursive submodule commits and actual APK contents/notices still need to be archived and reviewed.

No root-level license file was present in the audited repository tree. Absence of a license does not put code in the public domain or give others blanket permission to reuse it. See [GitHub's licensing documentation](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository).

## Evidence and known risks

| Area | Evidence observed | Consequence / required action |
|---|---|---|
| Legacy build source | `.github/workflows/build.yml` pins and clones Amethyst-Android, initializes submodules, then patches and builds it. | Must remain clearly labeled as legacy while present. It must not be represented as independent first-party implementation. |
| Upstream license | At pinned commit `330c6eae3164df64bdc4828e946a9e62cc5169e4`, `LICENSE` is GNU LGPLv3 (Git blob `0a041280bd00a9d068f503b8ee7ce35214bd24a1`); the pinned README also says LGPLv3. | Follow the license at the exact pinned revision. Preserve required notices/source obligations and review the modified/combined work and all bundled dependencies with qualified counsel before redistribution. |
| Upstream ancestry/dependencies | The pinned README identifies Amethyst as based on Boardwalk/PojavLauncher, credits multiple components, and reports Boardwalk terms as uncertain, LWJGLX as unknown, and PFFFT as ARR. The pinned `.gitmodules` file lists MobileGlues, SDL, sdl2-compat, MioLibPatcher, and androidnsbypass. The tracked `ANGLE_LICENSE` (blob `0f65fd60fd68d465a2283378849f16532f5ee4f1`) contains source/binary redistribution conditions even though the README shorthand labels ANGLE “All Rights Reserved,” so the actual license text and inventory must be reconciled. | Inventory every recursive submodule and identify which components are actually present in the APK. Archive the exact license/notice text and SHA-256 for each shipped component. Treat any unresolved or contradictory license metadata as pending; do not assume the top-level LGPL covers everything. |
| Patch/customizer scripts | `.github/scripts/customize_amethyst.py` is a large script that modifies upstream files; other scripts patch the upstream UI, renderer workaround, and branding. | Review the scripts' commit history and contents for copied source blocks, snippets, constants, assets, or derived implementations. A text scan alone cannot determine provenance. |
| Independent Android app | `voidcraft-android/` has its own Gradle project, app ID, Java package, manifest, dashboard, settings, saved-server store, and optional Server Status screen/client. `SOURCE_PROVENANCE.md` records the intended origin of each Java source file. | Keep the code isolated from the legacy source. CI checks source-file provenance entries, disallowed launcher references, upstream checkout absence, dependency declarations, and vendored-runtime binary extensions. These checks are guardrails, not proof of independent authorship or legal clearance. Real server/device tests remain outstanding. |
| Build tooling | The independent project uses Android Gradle Plugin 8.7.3, Android SDK Platform 35, Gradle 8.9, and Java/Android platform APIs. | Keep build-tool and SDK license records. These tools and APIs have their own terms; this does not make every component copyright-free. |
| Minecraft content | The clean-room subtree currently does not package game JARs, game assets, Java runtimes, renderer/native libraries, or loader binaries. Mojang's official [EULA](https://www.minecraft.net/en-us/eula) restricts distribution of its software/content and warns third-party tools not to imply official approval. | Keep Minecraft content out of this repository and APK unless redistribution permission is verified. When game installation is implemented, fetch only from authorized sources, respect entitlement/applicable terms, and do not imply Mojang/Microsoft endorsement. |
| Branding | The repository has a VoidCraft logo; the app is a third-party launcher. | Do not use Mojang/Microsoft/Minecraft logos or visual identity in a way that implies official status or endorsement. Keep a clear third-party disclaimer. Review any logo/font/icon source and its license. |
| Public license | The audited root tree has no root `LICENSE` file. | Do not add MIT/Apache/CC0 to the entire repository until the rights and provenance of all included and generated material are established. Choose a license only for material whose copyright holders can grant it. |

## Meaning of “copyright-free”

“Copyright-free” can mean different things:

- **No unauthorized copying / copyright-compliant:** the actual target for this migration. Write independent code from documented functional requirements, keep provenance, and use only original or lawfully licensed components.
- **Open source:** the author still owns copyright but grants reuse under a chosen license.
- **Public domain / CC0:** a different, intentional rights dedication. It cannot be applied on behalf of third-party authors, and contributor ownership/consent must be resolved first.

This project should target a **clean-room, rights-cleared launcher**, not promise that the whole product has no copyright at all. Android, runtimes, libraries, Minecraft files, and third-party assets each have their own rights and terms.

The independent app's current build/runtime dependency list is maintained in [voidcraft-android/DEPENDENCY_INVENTORY.md](voidcraft-android/DEPENDENCY_INVENTORY.md), and its source-by-source provenance intentions are recorded in [voidcraft-android/SOURCE_PROVENANCE.md](voidcraft-android/SOURCE_PROVENANCE.md). Neither document clears the legacy APK's fetched source/submodules or constitutes an independent legal opinion.

## Required migration plan

### Gate 1 — Build a traceable source inventory

The legacy build workflow now generates `VOIDCRAFT_THIRD_PARTY_AUDIT.md` and a machine-readable JSON companion after the APK is built, and uploads both as the `voidcraft-third-party-evidence` artifact. The script is `.github/scripts/audit_third_party.py`; its unit tests cover hashing, submodule states, APK entry inventory, and explicit incomplete status. Review the actual artifact before treating any inventory item as verified. The artifact is not an SPDX SBOM and is not legal clearance.
- [x] Record the upstream URL, exact pinned commit, and Git blob IDs of the top-level `LICENSE`, `README.md`, and `.gitmodules` files.
- [ ] Review the generated legacy third-party evidence artifact from CI: it records recursive submodule commit IDs, SHA-256 hashes for license/notice candidates, Gradle dependency declaration candidates, and selected APK entry hashes. This is evidence collection only; it does not identify every binary or decide license compatibility.
- [ ] Archive the actual recursive submodule commit IDs used by the legacy workflow, calculate SHA-256 for every bundled component license/notice, and reconcile incomplete or conflicting credits (including Boardwalk, LWJGLX, ANGLE, and PFFFT).
- [ ] Inventory every submodule, Maven/Gradle dependency, native library, JRE, renderer, loader, font, image, and generated/bundled asset actually included in an APK.
- [ ] Record component name, version, source URL, SPDX license expression where verifiable, redistribution obligations, notices, and whether it is bundled.
- [ ] Resolve any “unknown,” “All Rights Reserved,” or unlicensed component before redistribution.
- [ ] Review the patch/customizer scripts against their history and upstream sources; preserve the audit result.

### Gate 2 — Grow the independent implementation
- [ ] Build a full first-party launcher UI and data model in `voidcraft-android/`; do not copy, mechanically translate, rename, or extract upstream implementation code.
- [ ] Keep all extension-store code and downloaded feature packages separate. Establish source provenance, license metadata, publisher identity, integrity checks, uninstall, and permissions before executing or loading third-party features.
- [ ] Implement version metadata parsing/downloads using authorized metadata and download URLs; do not commit or redistribute Minecraft client files/assets.
- [ ] Choose a Java runtime and renderer strategy whose components have verified redistribution rights and compatible licenses. Open-source alternatives may be used only with their license terms observed; “open source” does not mean “copyright-free.”
- [ ] Implement loader integration from public specifications or properly licensed installer interfaces; document each dependency and test each supported loader/version.
- [ ] Implement Microsoft/Xbox sign-in through documented supported flows; never collect user passwords directly or label local profiles as authenticated accounts.
- [x] Implement local saved-server management and bounded, read-only Minecraft: Java Edition status querying without treating failed queries as proof that a server process is stopped.
- [ ] Test status queries against a real server across Android devices/network conditions; Bedrock status and DNS SRV discovery remain out of scope for this milestone.
- [ ] Preserve user data with an explicit migration format and backup/restore tests. Do not delete the existing engine path until the replacement passes acceptance tests.

### Gate 3 — Compliance and release controls
- [ ] Produce a machine-readable SBOM plus human-readable third-party notices for each release artifact.
- [ ] Make CI fail when an unapproved dependency or vendored binary is added.
- [ ] Generate and archive build provenance, source revision, dependency versions, license scan, test results, and APK hashes.
- [x] Add automated tests that assert the standalone workflow does not fetch the Amethyst/PojavLauncher repositories. Keep this separate from the legacy build while migration remains incomplete.
- [ ] Add repository-wide historical/source/assets scanning and review its findings; the clean-room subtree checks do not clear legacy history or every asset.
- [ ] Audit repository history, assets, generated sources, tests, screenshots, and documentation—not just the current Java files.
- [ ] Run emulator/device tests, installation/launch tests, and data migration tests; review distribution terms with a qualified lawyer before claiming legal clearance.
- [ ] Decide whether to license only independently authored code permissively, dedicate eligible code to CC0, or keep some portions unlicensed. Do not relicense contributors' or upstream authors' code without authority.

## Acceptance criteria before removing the legacy build

The root/default release path can switch away from Amethyst only after all of the following are true:

1. The independent app builds reproducibly with no upstream launcher checkout or patching step.
2. The launcher can safely manage installations, versions, downloads, accounts, runtime, renderer, loaders, and user data using original or properly licensed components.
3. Each shipped dependency and asset has a recorded, compatible license and required notices.
4. No Minecraft game files or other restricted content are bundled unless redistribution is explicitly authorized.
5. CI, emulator/device tests, upgrade/migration tests, and release documentation pass.
6. Source provenance has been reviewed. Automated keyword scans alone are not a certificate of originality.

**Current decision:** keep the current engine-based build labelled as legacy, keep the clean-room app isolated, and do not claim that the whole repository or its existing default APK is copyright-free or fully cleared. The independent subtree is progressing toward a rights-cleared implementation; upstream/component license review, source-history/assets review, a licensed Java runtime/renderer/download pipeline, Android device testing, and legal review remain open.
