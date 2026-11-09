# Embedded OpenJDK integration design

Status: engineering design; implementation and device validation are still pending.

This document records the implementation boundary for running a bundled OpenJDK runtime from the Android launcher process. It is deliberately separate from the existing JVM ELF validation and library-retention code: loading `libjvm.so` is not equivalent to creating a VM or running Minecraft.

## Current verified foundation

- Runtime packages are downloaded, verified, extracted, and installed transactionally.
- The selected runtime's ELF architecture and dynamic dependencies are inspected before loading.
- Runtime-owned dependencies are resolved and preloaded in dependency-first order.
- The native loader retains the selected runtime handles for process lifetime.
- The Kotlin launch host validates the game directory, class path, VM options, runtime home, and native-library directory.
- CI runs `testDebugUnitTest assembleDebug`; commit `dcd0538054f9c46971822b1e91f47a8ac0b0f1df` passed.

None of those steps calls `JNI_CreateJavaVM`, invokes a Java main class, or provides an Android LWJGL implementation.

## Runtime architecture

The application has Android ART already running. The bundled OpenJDK VM is a second runtime with different native libraries, class loading, signal behavior, and shutdown semantics. The JNI Invocation API does not provide a portable multi-VM-per-process design. Android-specific integration must therefore be treated as a platform port, not as a generic JNI wrapper.

The upstream MojoLauncher implementation is useful as a technical reference, not as code to copy blindly. Relevant reference files on its `v3_openjdk` branch:

- [jre_launcher.c](https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/app_mojo/src/main/jni/jre_launcher/jre_launcher.c)
- [JavaRunner.java](https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/app_mojo/src/main/java/git/artdeell/mojo/utils/jre/JavaRunner.java)
- [native_library_hook.c](https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/app_mojo/src/main/jni/jre_launcher/native_library_hook.c)
- [elf_hinter.c](https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/app_mojo/src/main/jni/jre_launcher/elf_hinter.c)
- [lwjgl_dlopen_hook.c](https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/app_mojo/src/main/jni/jvm_hooks/lwjgl_dlopen_hook.c)
- [load_stages.h](https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/app_mojo/src/main/jni/jre_launcher/load_stages.h)

Review upstream licensing and preserve required notices before adapting any implementation. This design document contains no copied upstream source.

## Required startup stages

1. **Preflight** — confirm runtime ABI, Java version, ELF dependency graph, storage paths, native extraction, and supported Android API level. Fail before loading if any dependency is unresolved.
2. **Load runtime** — load the selected OpenJDK libraries in the validated dependency order. Keep the runtime handles alive for the lifetime of the VM; do not offer an unload operation.
3. **Prepare Android linker compatibility** — investigate Android linker namespaces and API-level-specific library lookup. Any library hinting must be bounded, reversible where safe, and limited to libraries belonging to the selected runtime. Never override Android system libraries.
4. **Install VM lifecycle hooks** — provide controlled handling for OpenJDK's `exit` and `abort` callbacks. A Java `System.exit` must end the game session, not terminate the Android launcher process. Fatal VM aborts need a defined crash-reporting policy; they must not be disguised as normal session completion.
5. **Create VM** — validate every `JavaVMOption`, use a version-compatible `JavaVMInitArgs`, preserve unrecognized-option policy, and create the VM exactly once per Android process unless a proven runtime implementation supports more. Creation failure must release only temporary resources; do not unload a partially initialized runtime if VM-owned threads may exist.
6. **Install Java native-library hooks** — support the exact OpenJDK versions shipped by the runtime catalog. Hook signatures differ between Java 8, 17, and 21+. Detect method signatures at runtime, validate every JNI lookup, and fail with a stage-specific diagnostic rather than registering a mismatched function pointer.
7. **Configure class loading and launch context** — set the game class path and runtime properties without allowing user options to override launcher-managed paths. Use a dedicated class loader where required and preserve Minecraft's argument boundaries.
8. **Invoke the game entry point** — resolve the requested main class and method, check and report all pending JNI exceptions, and run the game on a controlled attached native thread.
9. **LWJGL integration** — intercept or adapt native loading only through a tested Android-compatible backend. The backend must provide EGL/OpenGL ES context and surface lifecycle, input translation, display sizing, audio, and library lookup. The current GLSurfaceView diagnostic renderer is not an LWJGL backend.
10. **Session lifecycle** — capture stdout/stderr, propagate Java exceptions, handle Android pause/resume and surface recreation, and implement a cooperative stop policy. Do not call `DestroyJavaVM` while game/native threads remain active; do not unload `libjvm` after VM startup.

## JNI and thread-safety rules

- Never share a `JNIEnv*` across threads. Store the `JavaVM*` and call `AttachCurrentThread` for each native thread that needs JNI, then detach only threads attached by that component.
- Use local references within bounded JNI frames; promote cross-thread references to global references and release them deterministically.
- Check `ExceptionCheck` after class lookup, method lookup, object creation, method invocation, and native registration. Preserve the original exception when reporting it.
- Do not invoke callbacks into destroyed Android activities or views. UI callbacks must be marshalled through a lifecycle-aware Android boundary.
- Avoid arbitrary shell command construction. Preserve arguments as arrays and keep game paths canonical.
- Treat VM shutdown as process-lifetime-sensitive. A safe first release may support one game session per app process and require a launcher process restart before another session.

## Compatibility matrix to prove

For each supported runtime package and Android ABI, record:

- Android API level and device ABI;
- OpenJDK major/minor/build and package source;
- `libjvm.so` and dependency load result;
- VM creation result and negotiated JNI version;
- Java main invocation result;
- class-loader hook variant selected;
- LWJGL version and native backend;
- EGL context creation, resize, pause/resume, and teardown;
- input, audio, and filesystem behavior;
- stop, crash, and app-restart behavior.

Do not mark a combination supported based only on a successful APK build or successful `dlopen`.

## Delivery gates

- **Gate A — build:** unit tests and debug APK build pass on CI.
- **Gate B — runtime load:** supported device can load the runtime and resolve all required libraries.
- **Gate C — VM:** VM starts and executes a small Java smoke-test main class without destabilizing ART.
- **Gate D — graphics:** Android LWJGL backend creates a context and renders a test frame.
- **Gate E — game:** legitimate Minecraft installation completes launch, reaches the title screen, accepts input, and survives pause/resume.
- **Gate F — stability:** stop/relaunch, low-memory behavior, crash diagnostics, and multiple supported ABIs are tested on physical devices.

Current status: Gate A passed on commit `dcd0538`. Gates B–F remain unverified.
