# Runtime integration plan

This document defines the checks that must pass before MyLauncher can truthfully offer a working **Start Game** action. It is a plan, not an implemented runtime or launch pipeline.

## Current state

- The Android app can fetch official version metadata and download/check selected Minecraft files.
- Asset indexes and objects, ordinary libraries, and experimental Linux ARM64 native archives have download/extraction support.
- Native extraction is experimental. Linux ARM64 metadata is not proof that a native library can run on Android.
- The app does **not** currently start Minecraft. Do not remove the launch blocker or show a success state until end-to-end tests pass.

## Required implementation stages

### 1. Runtime provider

- Choose a maintained Java runtime distribution and document its license, supported Android ABIs, supported Java versions, and redistribution terms before bundling or downloading it.
- Use a trusted HTTPS source with published checksums or signatures; verify downloaded runtime artifacts before installation.
- Store runtime files in app-private storage. Validate archive paths before extraction and never overwrite unrelated app files.
- Detect device ABI at runtime. Treat arm64-v8a, x86_64, and other ABIs separately; never assume an x86 desktop runtime or Linux native library is Android-compatible.
- Report missing, unsupported, or corrupt runtimes as actionable blockers rather than silently falling back.

### 2. Version-specific launch plan

For each selected Minecraft version, derive a launch plan from its official version JSON and applicable inherited metadata. Do not hard-code one command for every version.

The plan must validate:
- the selected version metadata and client JAR;
- required library artifacts and version-specific native classifiers;
- the asset index and the expected asset directory layout;
- Java-version requirements from version metadata;
- ordered classpath entries, platform rules, native library directory, working directory, and JVM/game arguments;
- placeholders such as player name, version ID, game directory, assets directory, and asset index ID.

Keep argument parsing structured. Respect modern argument objects and legacy string arguments; do not split quoted legacy arguments naively on spaces. Apply Mojang rules for the actual device OS/architecture, and test edge cases against official metadata.

### 3. Process lifecycle and diagnostics

- Launch through the selected compatible runtime using argument arrays, not shell-concatenated commands.
- Capture stdout/stderr to a bounded log file in app-private storage; redact tokens and other secrets.
- Show process start, exit code, crash logs, and common missing-file/runtime errors in the UI.
- Provide cancellation and clean up child processes when the user stops a launch or the app is closed.
- Do not claim the game launched just because a process was created; surface early exit and initialization failures.

### 4. Authentication and offline profile

- A local offline profile is only a local display name. It is not a valid Microsoft account and does not grant access to online-mode servers.
- Keep offline mode explicitly labelled and never fake Microsoft/Xbox/Minecraft authentication.
- Online authentication must use the genuine approved OAuth/Xbox/Minecraft flow, secure token storage, correct token refresh, and secret redaction. Do not collect account passwords directly.

## Acceptance gates

Do not enable a real Start Game action until these are verified:

1. A clean install can install one supported release completely from official metadata.
2. Every required artifact is checksum-verified where a trusted hash is available, and missing/corrupt files fail clearly.
3. The runtime is confirmed compatible with the actual Android ABI and tested device.
4. Generated arguments, classpath, native path, asset paths, and working directory are validated for the chosen version.
5. A controlled launch test records a real process result and logs, including failure paths.
6. At least one supported device completes a real game launch test; unsupported devices remain blocked with a clear reason.

## Development rule

Keep launch readiness conservative. A file count is not proof of integrity, a successful download is not proof of runtime compatibility, and a successful Gradle build is not proof that Minecraft runs. Update the app's readiness report only when each corresponding requirement is actually implemented and tested.
