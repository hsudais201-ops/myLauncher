# Mojo Android JVM and renderer port audit

Status: source audit complete; no upstream implementation has been copied into Flame Launcher. This document defines the next native integration work and the evidence required before it can be enabled.

## Why the current native library is not a game runtime

Flame's current CMake target builds `flame_jvm_bridge` from `native_jvm_library_probe.cpp`. It validates ELF headers, asks Android's linker to inspect runtime dependencies, and checks whether `JNI_CreateJavaVM` and `JNI_GetCreatedJavaVMs` are exported. It does not create a VM, invoke Java code, install OpenJDK hooks, translate LWJGL calls, or create a Minecraft rendering context.

A `libjvm.so` that loads successfully is therefore only a runtime-inspection result, not a launch-ready Java VM.

## Audited upstream reference

The current runtime installer consumes MojoLauncher split runtime packages. The matching upstream technical reference is [MojoLauncher](https://github.com/MojoLauncher/MojoLauncher/tree/v3_openjdk), branch `v3_openjdk`.

Important source boundaries:

| Upstream file or target | Observed responsibility | Flame work still required |
|---|---|---|
| [JavaRunner.java](https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/app_mojo/src/main/java/git/artdeell/mojo/utils/jre/JavaRunner.java) | Builds JRE-specific options, locates the VM library, prepares runtime environment and submits the VM/game arguments to native code. | Port the option policy into Flame's typed launch plan; do not accept arbitrary shell text or allow user overrides of launcher-owned properties. |
| [jre_launcher.c](https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/app_mojo/src/main/jni/jre_launcher/jre_launcher.c) | Loads `libjvm.so`, resolves `JNI_CreateJavaVM`, adds VM exit/abort hooks, creates the VM, installs class-loader hooks and invokes a Java main class. | Build a lifecycle-safe Flame bridge with explicit stages, exception handling, process/session ownership and device tests. |
| [load_stages.h](https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/app_mojo/src/main/jni/jre_launcher/load_stages.h) | Defines stage identifiers for runtime load, VM creation, native hook lookup/registration, classpath loading and main invocation. | Expose equivalent typed diagnostics to the existing Kotlin process controller. |
| [native_library_hook.c](https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/app_mojo/src/main/jni/jre_launcher/native_library_hook.c) | Handles Java 8, Java 17 and Java 21-style native library loader method signatures and prepares library-loading compatibility. | Audit exact signatures and behavior for each runtime actually shipped by Flame, including Java 25; fail closed when the expected method cannot be verified. |
| [elf_hinter.c](https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/app_mojo/src/main/jni/jre_launcher/elf_hinter.c) | Works around Android linker namespace/library lookup constraints for runtime-owned native libraries. | Port only after a bounded allowlist, API-level behavior, reversibility and collision policy are reviewed. Never hint Android system libraries. |
| [lwjgl_dlopen_hook.c](https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/app_mojo/src/main/jni/jvm_hooks/lwjgl_dlopen_hook.c) | Intercepts LWJGL's dynamic loader and redirects selected renderer/driver library requests. | Implement the matching Android graphics backend and native library packaging; do not point LWJGL at Flame's diagnostic GLSurfaceView. |
| [CMakeLists.txt](https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/app_mojo/src/main/jni/CMakeLists.txt) | Builds native VM integration alongside MojoExec, Android GLFW and SDL targets. | Define a reproducible NDK/CMake target and exact library set for each ABI, without pulling desktop-only dependencies. |

The upstream JNI bridge dynamically loads the runtime and creates the VM; it also performs Java-version-specific native-library hook installation and controls exit/abort behavior. That implementation is a coordinated native/JVM port, not a standalone JNI function wrapper.

## Renderer is a separate P0 dependency

Minecraft Java clients use LWJGL and desktop-style graphics APIs. Android's native graphics stack exposes EGL/OpenGL ES, so a compatible translation/renderer layer and Android-specific LWJGL loader are required. Upstream Mojo's native build includes Android GLFW and SDL and has its own dynamic-library redirection path. Flame currently has only an OpenGL ES diagnostic surface.

Renderer acceptance must cover:
- EGL/context creation against the Android surface and actual selected renderer;
- GL symbol translation and native library resolution for the targeted LWJGL version;
- frame swap, viewport resize, fullscreen/window dimensions and surface recreation;
- touch, keyboard, mouse and gamepad input forwarding;
- audio device initialization and shutdown;
- pause/resume, Activity recreation, graceful game stop and renderer teardown.

Do not claim vanilla Minecraft compatibility based on rendering a test triangle in Flame's existing surface.

## Runtime and device targeting

Flame's Gradle configuration currently declares Android API 26 as minimum and packages native code for `arm64-v8a`, `armeabi-v7a`, `x86` and `x86_64`. This is a build declaration, not a tested compatibility matrix.

The immediate validation target should be one exact combination: the user's Android 9 Chromebook, its runtime-reported ABI, a specific Mojo Java runtime build, and one renderer backend. The device is believed to be x86_64, but the app must read and record the actual Android ABI instead of assuming it from the Chromebook model.

Flame's current Mojo runtime adapter offers Java 17, 21 and 25 and selects an architecture-specific signed component. Every combination must be verified against the live signed manifest; the presence of a code path for an ABI does not prove the upstream package publishes it.

## License and source hygiene

The inspected MojoLauncher repository declares LGPL-3.0. Its `app_mojo/src/main/assets/licenses/LICENSE-mojoexec` separately contains an MIT notice. Before adapting any upstream file:
1. Inspect the specific file's copyright and license headers, plus the repository's applicable license.
2. Preserve copyright, attribution and required notices.
3. Keep modifications identifiable and retain the required source-availability/compliance process for any LGPL-covered distribution.
4. Do not assume the MIT `mojoexec` license covers the JNI launcher, class-loader hooks, GLFW, SDL or renderer sources.
5. Record the exact upstream commit SHA used as the port baseline and re-audit before upgrading it.

This audit intentionally does not reproduce upstream source code.

## Safe implementation sequence

1. Freeze the exact Mojo runtime build, Java major, ABI and Android API test target. Record the upstream commit SHA and per-file license metadata.
2. Add a dedicated native smoke-test library and Kotlin diagnostics for stage-specific failures. The first native test must load one VM and execute a tiny Java main class only on the selected test device.
3. Port the VM lifecycle, exit/abort policy, Java-version-specific native-library hooks and linker compatibility as one audited unit. Keep VM handles alive for the process lifetime; support one VM/game session per app process until restart/relaunch semantics are proven.
4. Verify Java stdout/stderr, exception reporting, cancellation, Android Activity lifecycle and safe stop without terminating ART or the launcher.
5. Add one matching Android GLFW/renderer backend and prove the renderer test suite before starting Minecraft.
6. Integrate the validated runtime bridge, version artifacts/assets, account model, profile settings and existing process controller behind a fail-closed preflight gate.
7. Promote the launch UI only after the exact device reaches the Minecraft title screen and loads a world with usable input.

## Exit criteria

The native milestone is not complete until a physical-device record includes runtime build/hash, Java version, Android API, process ABI, JNI version, VM creation result, Java smoke-test output, hook variant, graphics backend, input/audio, pause/resume, stop and crash behavior. A green Gradle build, successful ELF check, successful `dlopen`, or uploaded debug APK is insufficient.
