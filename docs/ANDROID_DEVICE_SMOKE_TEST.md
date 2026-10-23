# Voidcraft Android device smoke test

This is an acceptance checklist for **debug/test artifacts**, not a public release sign-off. A successful CI build proves the APK compiled and passed the workflow's checks; it does not prove that Minecraft launches on a particular device.

## 1. Get the combined test APK

1. Open the [Voidcraft integrated Android engine workflow runs](https://github.com/hsudais201-ops/myLauncher/actions/workflows/voidcraft-engine-build.yml).
2. Open the newest run with a green success result on `launcher/bootstrap`.
3. Download the `voidcraft-integrated-debug-apk` artifact, then extract its ZIP with the phone's Files app. Do not install the separate `voidcraft-debug-apk` when you intend to test the integrated engine; that artifact is the standalone frontend build.
4. Keep the included SHA-256 and build-provenance files with the test notes.

Only install an artifact produced by the expected repository workflow. The app's minimum Android version is API 26 (Android 8.0).

Because the standalone and integrated debug APKs may be signed by different temporary CI debug keys, Android may refuse to install the integrated APK over an already-installed standalone build. If Android reports a signature conflict, uninstall the existing Voidcraft test build first, then install the integrated APK. **Uninstalling erases that app's private settings and cached data**; these debug artifacts are not a supported upgrade path. A public release needs a stable, protected signing key shared by release builds.

## 2. First launch and engine access

- Install the extracted APK. If Android asks, grant the file manager permission to install apps, then revoke that permission after installation.
- Open Voidcraft and complete the engine's first-run storage setup.
- Tap **PLAY**. The integrated upstream launcher should open. If it does not, record the exact error and stop; do not assume the game engine is working.
- For the version-handoff test, open Voidcraft **Versions**, select a vanilla version from the official manifest, inspect it, and choose **PREPARE FULL VERSION FILES**. Confirm and wait for the client JAR, supported library JARs, asset index, and referenced assets to finish verification.
- Return to Voidcraft Home and tap **PLAY**. When the selected version's saved metadata and client JAR match their recorded ID, size, and SHA-1, the integrated engine should apply that version to the active profile. Record the exact toast/message and confirm the engine's profile menu shows the same version. If the files are missing or do not verify, the handoff should be refused; manually install/select the version in the engine before continuing.
- Use only the engine's supported official account sign-in for an account you control. Never share passwords, session tokens, or unredacted logs.
- In the engine, confirm a compatible Java runtime is available, complete sign-in, and start the chosen vanilla version from the engine's own launch control. Downloading metadata or files in the Voidcraft Versions screen alone is not a successful world-launch test.

## 3. Test game launch and inputs

Record pass/fail for each item:

- [ ] Voidcraft home screen opens without crashing.
- [ ] PLAY reaches the integrated engine launcher.
- [ ] Official sign-in/profile setup works.
- [ ] A compatible Java runtime is selectable and starts.
- [ ] A vanilla release installs, including its assets and libraries.
- [ ] The version prepared in Voidcraft is applied to the intended engine profile after size/hash checks.
- [ ] Missing/mismatched client files are not treated as a successful handoff.
- [ ] Minecraft reaches the title screen.
- [ ] A single-player world can be created/opened and played for at least five minutes.
- [ ] Touch controls respond; keyboard/gamepad input is checked if available.
- [ ] Audio and renderer behave without persistent errors.
- [ ] Returning from Minecraft to Voidcraft does not crash the launcher.
- [ ] The in-app OpenGL diagnostic screen reports the expected GPU/GL information.
- [ ] Settings → Device diagnostics completes; review storage, memory, engine, runtime, selected-version and game-log checks.
- [ ] If reporting a problem, export the diagnostics report and review it before sharing. It includes device model, Android version, CPU ABI and file-presence checks, but excludes account tokens and raw game-log contents.

For Mods and Files, first confirm that the tools target the same active game directory shown by the engine. Only test a mod known to match that Minecraft version and mod loader. Do not treat a downloaded Feature Store package as an installed Minecraft mod: arbitrary third-party packages are stored but are not injected into the live game.

## 4. Report a reproducible result

Include the app artifact/run link and SHA-256, device model, Android version, CPU ABI, Minecraft version, Java version, renderer, and the failed checklist step. Attach a screenshot of the failure or game title screen. Redact account names, personal paths, passwords, tokens, and other private data from logs.

## Release blockers

- A physical-device test must verify sign-in, runtime selection, version installation, and successful world launch before calling the launcher playable.
- Social Hub is a staging beta, not a production-ready community service.
- Do not publicly distribute a combined APK until the exact bundled engine/native-component redistribution terms, required LGPL/GPL source and notices, and any ARR/unknown-license concerns have been resolved.
- Public signing, safe update delivery, privacy/security review, and the website distribution path remain separate release gates.
