# Voidcraft Android engine integration and release gates

## Integration design

The working branch overlays the Voidcraft Jetpack Compose frontend onto a pinned checkout of
[AngelAuraMC/Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android) at commit
`330c6eae3164df64bdc4828e946a9e62cc5169e4`. The reproducible workflow is
`.github/workflows/voidcraft-engine-build.yml`; the build-time overlay is
`tools/integrate_amethyst.py`.

The intended combined-app flow is:

1. The upstream storage-bootstrap Activity requests/validates storage access, initializes launcher preferences, and unpacks required native/components.
2. After bootstrap, Android opens the Voidcraft Compose home screen.
3. **PLAY** opens the integrated upstream `net.kdt.pojavlaunch.LauncherActivity`.
4. The upstream launcher handles its supported account sign-in, runtime selection, native renderers,
   version installation, mod-loader integration, touch controls, and actual game launch.
5. The Voidcraft version preparation tools and mod/file manager use upstream game directories when
   those public engine directory fields are available; the standalone prototype falls back to app-private storage.

This is an integration path, not a claim that the current artifact has been tested on a physical device.
Opening the backend launcher does not auto-start Minecraft: the user must complete the upstream
launcher’s supported sign-in/profile/version setup and press its launch control. No authentication
bypass is implemented.

## Current release status

- [ ] Combined Voidcraft APK build succeeds after overlaying Compose and engine sources.
- [ ] APK installs and starts on supported physical Android devices.
- [ ] Upstream launcher is reachable from Voidcraft's PLAY action.
- [ ] Official account sign-in, runtime install, vanilla version install, and world launch are device-tested.
- [ ] Touch input, keyboard/gamepad input, audio, and renderer selection are device-tested.
- [ ] Version/library/asset downloads are checked against the engine's selected game directory.
- [ ] All required source, notices, bundled native/component licenses, and redistribution terms are reviewed.
- [ ] Release APK signing, versioning, update path, privacy/security checks, and website distribution are prepared.

Until these gates pass, artifacts from the integration workflow are **debug/test builds only**, not
a verified public release.

## License and permission review

The pinned upstream README identifies Amethyst as LGPLv3 and lists additional components with
separate terms. The upstream repository's own credits currently identify concerns including:

- ANGLE: listed as **All Rights Reserved** in the upstream README.
- LWJGLX: listed as having an **unknown license**.
- pffft: listed as **ARR** in the upstream README.
- OpenJDK and OpenAL-Soft: listed as GPLv2.
- Other components use separate licenses such as LGPL-2.1, MIT, BSD-3-Clause, Apache-2.0, or other terms.

These names are a review checklist based on the pinned upstream README, not a complete legal audit.
The workflow records a revision/submodule manifest and inventories license-like files, but a file
inventory alone does not establish that every binary/native component can be redistributed.

**Do not publish the combined APK publicly until the component licenses and permissions are checked.**
In particular, obtain clarification/permission or select a legally distributable alternative for
components marked ARR or unknown; preserve required copyright/license notices; make required source,
corresponding changes, and relink/rebuild instructions available for LGPL/GPL components; and review
the obligations for the exact binaries included in the final APK. This checklist is engineering
documentation, not legal advice.

Reference materials:
- [Pinned upstream source](https://github.com/AngelAuraMC/Amethyst-Android/tree/330c6eae3164df64bdc4828e946a9e62cc5169e4)
- [Upstream README and credits](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/README.md)
- [Upstream LGPL-3 license](https://github.com/AngelAuraMC/Amethyst-Android/blob/330c6eae3164df64bdc4828e946a9e62cc5169e4/LICENSE)
