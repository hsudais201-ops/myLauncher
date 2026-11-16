# Amethyst upstream dependency audit snapshot

**Review date:** 2026-11-16  
**Upstream repository:** https://github.com/AngelAuraMC/Amethyst-Android  
**Reviewed ref:** `v3_openjdk`  
**Pinned upstream commit:** `166af615dbdca1a507b8c2d379822cca02795132`  
**Commit date:** 2026-11-13  
**Purpose:** Reproducible source-review baseline only. This is not approval to copy, link, package, or redistribute any upstream code or binary.

## Source identity and reproducibility

The branch currently resolves to the commit above. Use that immutable commit URL when comparing source, rather than relying on a moving branch URL:

https://github.com/AngelAuraMC/Amethyst-Android/tree/166af615dbdca1a507b8c2d379822cca02795132

The full recursive Git tree for the pinned commit was inspected (not truncated). It contains exactly these two mode-`160000` gitlinks:

| Path | Upstream repository | Pinned gitlink |
|---|---|---|
| `MioLibPatcher` | https://github.com/AngelAuraMC/MioLibPatcher | `9a1f807f2b8e7abfd921a3ed5a69e23ed6540b0c` |
| `androidnsbypass` | https://github.com/alexytomi/androidnsbypass | `9f57982abf007aacc45becfc9328ae06fa6bbe3b` |

The recursive tree did not contain gitlinks at the previously suspected SDL, SDL2-compat, or MobileGlues paths. Those names may appear in upstream dependency descriptions or source/build references, but this pinned tree inspection does not establish them as Git submodules. Their exact source provenance and revisions must be traced from the actual build/source references before reuse.

Do not use a moving submodule branch or current upstream HEAD as a substitute for the exact gitlink recorded by the reviewed parent commit.

### License check of the two pinned gitlinks

The two gitlink commits were inspected independently:

| Submodule | Pinned commit | License evidence at that commit | Flame Launcher decision |
|---|---|---|---|
| MioLibPatcher | `9a1f807f2b8e7abfd921a3ed5a69e23ed6540b0c` | Recursive tree contains no file named LICENSE, COPYING, or NOTICE; build declares Javassist 3.29.2-GA | **Stop**: no license was established for the submodule source. Do not copy, link, or redistribute it without explicit permission or verified licensing evidence. Audit Javassist's own license separately if ever considered. |
| androidnsbypass | `9f57982abf007aacc45becfc9328ae06fa6bbe3b` | Root `LICENSE` grants MIT-style terms; `LICENSE-BSD-2-Clause` is also present for included material | Potentially reusable only after tracing which files carry each notice, preserving both applicable notices, and reviewing the exact native build integration. This is not blanket approval for the parent project. |

The license observations above are from the exact pinned trees, not from current default branches. A missing license file is not permission to use the code.

## License evidence visible at the pinned commit

The repository root `LICENSE` is GNU LGPL version 3. The README's dependency section separately identifies a mixed set of upstream components and license statements. The root LGPL license does not automatically relicense those dependencies or establish that all binaries can be redistributed together.

| Component named upstream | License statement in pinned README | Flame Launcher gate |
|---|---|---|
| Amethyst Android source | GNU LGPLv3 | Review linking/combination model, notices, source and relinking obligations before reuse |
| Boardwalk | Unknown / Apache-2.0 or GPLv2 | Unresolved; inspect exact included source and provenance |
| PojavLauncher | “GLGPL” label with linked license | Verify exact license text and inherited obligations |
| Android Support Libraries | Apache-2.0 | Pin exact artifacts and collect notices |
| GL4ES | MIT | Pin exact source/binary and preserve notice |
| MobileGlues | LGPL-2.1 | Trace the exact source/artifact revision and applicable LGPL obligations; it is not a gitlink in the pinned root tree |
| Krypton Wrapper | MIT | Verify exact source revision and notice |
| ANGLE | “All Rights Reserved” in README; local ANGLE license file exists | **Stop**: obtain and review the exact license text and redistribution permission |
| OpenJDK | GPLv2 statement | Identify exact JDK build, exceptions, source offer and redistribution terms |
| LWJGL3 | BSD-3-Clause | Pin exact fork/revision and preserve notices |
| LWJGLX | Unknown | **Stop**: no inclusion or redistribution until license is established |
| Mesa | MIT statement | Identify exact version and all included subcomponents/notices |
| bhook | MIT | Pin exact revision and preserve notice |
| libepoxy | MIT | Pin exact revision and preserve notice |
| virglrenderer | MIT statement | Pin exact revision and preserve notices |
| OpenAL-Soft | GPLv2 statement | Resolve exact build and obligations before packaging |
| Oboe | Apache-2.0 | Pin exact revision and notices |
| pffft | “ARR” in README | **Stop**: clarify rights and redistribution permission |
| SDL3 / SDL2 compatibility | zlib statements | Trace exact source/artifact revisions and preserve notices; the suspected paths are not gitlinks in the pinned root tree |

This table records what the upstream README says; it is not an independent legal determination. The README itself marks several items as unknown or restrictive, and it points to a separate local ANGLE license file. Those are blockers, not assumptions to waive.

## Runtime artifact provenance

The pinned README's quick-build instructions rely on prebuilt JREs supplied by upstream CI. Its detailed build instructions direct builders to download a `jre8-pojav` CI artifact from the OpenJDK multi-architecture project and place it in the Android app assets.

No artifact was downloaded or added to Flame Launcher during this review. Before considering any JRE:
- Identify the exact workflow run, artifact ID/name, source commit, build recipe, JDK update level, ABI contents, and SHA-256.
- Inspect the artifact's own license files and notices, including any bundled native libraries.
- Establish whether the artifact may be redistributed in Flame Launcher and under what source/notice obligations.
- Rebuild from reviewed source if artifact provenance or reproducibility is insufficient.
- Validate Android bionic compatibility and JNI startup on physical arm64 hardware; an ARM64 label alone is not proof.

## Decision

**Do not integrate or redistribute Amethyst code, its submodules, native libraries, or CI JRE artifacts yet.** The pinned source is now a reproducible reference for a later component-by-component review. Immediate blockers include unresolved Boardwalk/Pojav provenance, ANGLE rights, LWJGLX license, pffft rights, OpenJDK artifact provenance, and exact submodule/dependency revisions.

A safe next engineering step is a narrow interface/prototype plan that does not import upstream implementation or binaries. Revisit integration only after the blockers are resolved and the intended distribution model is approved.
