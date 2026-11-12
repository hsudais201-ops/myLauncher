# Embedded JVM Android architecture decision

Status: **Open architecture gate — MojoLauncher’s in-process OpenJDK integration is the primary candidate; production VM invocation remains disabled.**

## Current application constraints

The Android application currently declares `minSdk = 26` and `targetSdk = 36` in `app/build.gradle.kts`. Do not solve execution by lowering the target SDK below Android 10.

The initial hardware target is a Dell Chromebook 11 3180 with an Intel Celeron N3060 (x86_64-capable) and Android 9. This identifies the CPU family, not the ABI list exposed by Android. Capture `Build.SUPPORTED_ABIS`, `SUPPORTED_64_BIT_ABIS`, `SUPPORTED_32_BIT_ABIS`, `Process.is64Bit()`, and the native library directory on the actual device before selecting runtime artifacts.

## Evidence and corrected assessment

1. The JNI Invocation API says creating multiple Java VMs in one process is not supported as a general JNI contract. Android application processes already run ART, so a second VM in the same process is a compatibility risk that must be deliberately addressed. See the [JNI Invocation API](https://docs.oracle.com/javase/10/docs/specs/jni/invocation.html) and [AOSP ART JNI implementation](https://android.googlesource.com/platform/art/+/master/runtime/jni/java_vm_ext.cc).
2. A concrete Android launcher precedent exists: [MojoLauncher, branch `v3_openjdk`, commit `1b8d90424c1cb791321d45f60ac62c54f4b013fa`](https://github.com/MojoLauncher/MojoLauncher/tree/v3_openjdk). Its `app_mojo/src/main/jni/jre_launcher/jre_launcher.c` loads the selected OpenJDK `libjvm` with `dlopen`, resolves and calls `JNI_CreateJavaVM`, invokes a Java main class, and destroys the VM. It also installs class-loader/native-library hooks and process-exit/abort hooks. The Java side is in `app_mojo/src/main/java/git/artdeell/mojo/utils/jre/JavaRunner.java`; the native hook stack is under `app_mojo/src/main/jni/jre_launcher/` and `jvm_hooks/`. This is a materially better starting point than inventing a generic bridge or assuming PRoot is mandatory.
3. MojoLauncher’s code demonstrates an implemented in-process integration pattern, but does not by itself prove that an arbitrary OpenJDK build, arbitrary ART version, all Android ABIs, or this app’s target device are compatible. The JNI general restriction still matters. Flame must pin and audit the exact OpenJDK build and the complete native hook set, then prove VM creation, Java execution, teardown, and ART stability on the Chromebook before authorizing invocation.
4. MojoLauncher identifies itself as LGPL-3.0 and lists OpenJDK as GPLv2 among its third-party components. Before adapting source, inspect the exact file headers, upstream licenses, notices, build scripts, and distribution obligations. Do not copy source without preserving required notices and satisfying the applicable licenses. See the [MojoLauncher repository and license credits](https://github.com/MojoLauncher/MojoLauncher).
5. Android security guidance states that apps targeting API 29 or later cannot execute files from their writable app home directory. This blocks the naive design of downloading a JRE into `filesDir` and starting its `bin/java` with `ProcessBuilder`. It does not, by itself, rule out an in-process VM loaded as shared libraries with `dlopen`; that exact approach must be validated against Android linker namespaces, file permissions, and the pinned runtime. See [Android app security best practices](https://developer.android.com/privacy-and-security/security-best-practices) and [Android 10 behavior changes](https://developer.android.com/about/versions/10/behavior-changes-all#execute-permission).
6. MojoLauncher’s native runner has Java-version-specific native-library loading hooks, Android linker path hints, LWJGL preload hooks, and process execution interception. A bare `JNI_CreateJavaVM` call is therefore not a complete integration. Audit these hooks and their version-specific assumptions as a single unit.
7. [DroidNative’s architecture notes](https://github.com/DroidNative/DroidNativeApp/blob/main/docs/architecture.md) describe an APK-packaged PRoot/user-mode ELF loader pattern for guest Linux programs. This remains a fallback research option, not the preferred first implementation now that a direct Android OpenJDK integration precedent has been identified. Its x86_64 host support and compatibility with this Chromebook remain unproven.
8. MojoLauncher’s repository advertises a working Android Minecraft Java launcher and documents its renderer/native ecosystem, but this is not proof that Flame’s selected versions, runtime builds, renderer configuration, or Chromebook hardware are supported. See the [MojoLauncher project overview](https://github.com/MojoLauncher/MojoLauncher).

## Current decision

- **Primary candidate:** audit and adapt the pinned MojoLauncher in-process OpenJDK integration, including the exact runtime build, VM lifecycle, Java-version hooks, native library path hints, process hooks, and renderer dependencies.
- Do not call a generic downloaded HotSpot/OpenJDK `JNI_CreateJavaVM` from the Android app process without that complete, version-pinned integration and explicit device validation.
- Do not treat `dlopen`, `dlsym`, JNI symbol presence, or a linker preflight as proof of safe VM creation.
- Do not use a second Android app process or ordinary Android Service as a supposed JVM-isolation boundary; those processes also run ART.
- Do not use `ProcessBuilder` to execute a downloaded binary from writable app storage while targeting API 36.
- Keep `JvmInvocationReadiness` fail-closed and Play disabled until the real VM smoke test passes on the target device.
- Keep PRoot/user-mode ELF loading as a fallback only; do not wire it into the launch path or advertise it as supported without a separate device proof.

## Primary integration audit

Before porting code, record:

1. Exact MojoLauncher source commit, OpenJDK build repository/commit, patch set, build flags, runtime archive hashes, supported Java majors, and supported ABIs.
2. JNI VM lifecycle: creation, JNI version, VM options, main-thread ownership, thread attach/detach, exception handling, Java `System.exit` and abort interception, destroy behavior, and repeated launch semantics.
3. Android ART coexistence assumptions, signal-handler changes, process-global state, native symbol interposition, and effects on Android framework JNI calls.
4. Java 8/17/21/25 native-library loader differences and the exact `libjava.so` hook targets/signatures.
5. Android linker namespace behavior, dependency loading order, library path hints, writable runtime file behavior, and whether all required runtime libraries can be loaded on API 28.
6. `mojoexec` process execution behavior, subprocess ABI and packaging requirements, cancellation, signal forwarding, and whether the Chromebook’s reported ABI is supported.
7. LWJGL/GLFW/SDL/OpenAL/renderer native dependencies, display surface ownership, input/audio lifecycle, and renderer selection.
8. LGPL/GPL and third-party license obligations, file-level notices, source-offer/redistribution requirements, and whether Flame can legally distribute modified binaries and runtime artifacts.

Do not adapt isolated fragments before the audit identifies the minimum coherent native and Java source set.

## Required experiment sequence

1. Capture actual Android ABI lists, process bitness, API level, device model, and native library directory from the Chromebook.
2. Pin the upstream OpenJDK build and verify a matching runtime exists for the actual process ABI and Java major.
3. Build a debug-only native smoke-test path using the audited upstream runner and hooks. Keep it separate from the Minecraft Play flow.
4. Load the pinned `libjvm`, create a VM, invoke a tiny HelloWorld main class, capture stdout/stderr and exit status, and verify the Android activity remains functional.
5. Test Java native-library loading, classpath behavior, subprocess execution, signal handling, cancellation, clean VM teardown, repeated sessions, and low-memory conditions.
6. Run Android/ART stability tests and compare logs/crashes before and after VM creation. A successful HelloWorld alone is not sufficient.
7. Only after the Java smoke gate passes, integrate a minimal renderer and validate EGL/OpenGL, resize, input, audio, pause/resume, and teardown.
8. Only after the renderer gate passes, connect verified Minecraft artifacts, account state, selected instance/profile, and Play/session lifecycle.

Each candidate must prove on the actual Android 9 Chromebook: VM creation, Java main execution, classpath/native loading, output capture, safe teardown, repeatability, memory behavior, and no ART/app instability. Expand to other API levels and ABIs only with equivalent device evidence.

## Fallback architectures

1. **Dedicated helper APK:** evaluate only if the in-process upstream integration cannot be made reliable. This introduces installation consent, updates, package visibility, a separate UID, and authenticated IPC.
2. **User-mode ELF loader / PRoot:** evaluate only if the native runtime cannot be loaded and executed under the current Android restrictions. Verify host and guest ABIs independently, ptrace/seccomp compatibility, process counts, cancellation, and memory overhead on the Chromebook.
3. **APK-packaged executable/runtime:** assess packaging size, update model, ABI coverage, and license obligations if a helper executable is required. Do not assume this solves JVM lifecycle or ART coexistence.

## Release gate

No candidate is approved until a reproducible physical-device test demonstrates a real Java smoke-test program running through the exact selected integration without destabilizing ART or the launcher. Unit tests, ELF inspection, linker preflight, and symbol presence are not sufficient. Minecraft/LWJGL rendering remains a separate subsequent gate.
