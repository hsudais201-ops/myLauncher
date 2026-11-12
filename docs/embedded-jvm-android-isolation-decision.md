# Embedded JVM isolation decision for Android

Status: **Open architecture gate — no production VM invocation is authorized.**

## Current application constraints

The Android application currently declares `minSdk = 26` and `targetSdk = 36` in `app/build.gradle.kts`. The target SDK is important: a solution that only works by lowering the target below Android 10 is not an acceptable implementation for this project.

The initial hardware target is a Dell Chromebook 11 3180 with an Intel Celeron N3060 (x86_64-capable) and Android 9. This identifies the CPU family, not the ABI list exposed by the Android runtime. The app must read and record `Build.SUPPORTED_ABIS`, `SUPPORTED_64_BIT_ABIS`, and `SUPPORTED_32_BIT_ABIS` on the actual device before selecting a native host or guest runtime.

## Evidence

1. The Java Native Interface Invocation API states that creating multiple Java VMs in one process is not supported. See the [JNI Invocation API](https://docs.oracle.com/javase/10/docs/specs/jni/invocation.html).
2. Android app processes already execute on ART. AOSP's ART implementation exports `JNI_CreateJavaVM` and initializes its own `Runtime` through that entry point; seeing the symbol in a process therefore does not establish that a second, independent HotSpot VM can safely coexist. See [AOSP ART JNI implementation](https://android.googlesource.com/platform/art/+/master/runtime/jni/java_vm_ext.cc) and [Android's ART guidance](https://developer.android.com/guide/practices/verifying-apps-art).
3. Android's security guidance states that apps targeting API 29 or later cannot execute files from their writable app home directory; executable code must be embedded in the APK. This blocks the naive design of downloading a JRE into `filesDir` and invoking its `bin/java` with `ProcessBuilder` on the current target SDK. See [Android app security best practices](https://developer.android.com/privacy-and-security/security-best-practices) and [Android 10 behavior changes](https://developer.android.com/about/versions/10/behavior-changes-all#execute-permission).
4. [DroidNative's architecture notes](https://github.com/DroidNative/DroidNativeApp/blob/main/docs/architecture.md) document one viable *class* of workaround for Linux guest programs: package the host PRoot executable and its ELF loader as APK native libraries, run those host components from Android's read-only native-library directory, and have the loader map guest ELF programs with `mmap` rather than asking the kernel to `execve` a writable app-data binary. This is evidence for the packaging/loader pattern, not proof that a Java VM works through it.
5. PRoot relies on `ptrace` syscall interception. DroidNative documents Android-specific complications, including seccomp behavior and process-lifecycle constraints. Its public overview mentions x86 support, but the reviewed architecture/build notes do not establish a ready-to-use x86_64 host build for this Chromebook. Do not infer x86_64 compatibility from the device's Intel CPU alone. See the [DroidNative architecture](https://github.com/DroidNative/DroidNativeApp/blob/main/docs/architecture.md) and [DroidNative project overview](https://github.com/DroidNative/DroidNativeApp).
6. Android 9's compatibility definition requires devices to accurately report supported ABIs through `Build.SUPPORTED_ABIS` and related fields. Read those values at runtime rather than hard-coding a presumed ABI. See the [Android 9 CDD ABI requirements](https://source.android.com/docs/compatibility/9/android-9-cdd).

## Decision

- Do not call a downloaded HotSpot/OpenJDK `JNI_CreateJavaVM` from the Android application process.
- Do not treat `dlopen`, `dlsym`, `JNI_CreateJavaVM` symbol presence, or `JNI_GetCreatedJavaVMs` as proof of safe VM creation.
- Do not use a second Android app process or ordinary Android Service as a supposed VM-isolation boundary; those are still Android app processes running ART.
- Do not switch to `ProcessBuilder` for a downloaded executable in writable app storage while targeting API 36.
- Keep the current fail-closed `JvmInvocationReadiness` gate and Play disabled.
- Treat PRoot plus a user-mode ELF loader as an investigation candidate only. Do not wire it into the launch path or advertise it as supported until a target-device Java smoke test passes.

## Architectures to evaluate before implementation

1. **APK-packaged helper executable/runtime:** determine whether a helper and the required runtime can be packaged as signed, read-only executable code in the installed APK/native-library area, and whether its size, updates, ABI coverage, and licensing are practical.
2. **Dedicated helper APK:** evaluate a separately installed helper package with its own process/UID and a narrow authenticated IPC protocol. This introduces installation consent, updates, package visibility, and lifecycle work; it is not yet selected.
3. **User-mode ELF loader / PRoot:** evaluate a maintained Android host build and loader that can run a downloaded Linux JVM without direct `execve` of a writable app-home executable. The host PRoot/loader must itself be packaged as APK native code for the current target SDK. Verify the host ABI independently from the guest JVM ABI; cross-architecture guest execution may require a separate emulator and is not assumed. Measure ptrace overhead, syscall compatibility, process counts, cancellation, and memory on the 4 GB Chromebook. This is a substantial compatibility and security project, not a small JNI change.

## Required experiment sequence

1. Capture actual Android ABI lists and API/device details from the Chromebook.
2. Build or obtain a minimal signed APK-packaged host executable for the exact reported ABI; verify that Android can load/start it from the installed native-library directory.
3. Run a trivial native host self-test, then a trivial guest ELF program through the loader. Record exit status, stdout/stderr, signals, cancellation, and repeatability.
4. Run the selected Mojo/OpenJDK runtime's `java -version`, then a HelloWorld main class with classpath and required native libraries.
5. Test thread creation, JNI/native loading, process stop, repeated VM sessions, low-memory behavior, and app/ART stability.
6. Only after those pass, investigate Minecraft's LWJGL rendering/input/audio integration as a separate gate.

Each candidate must prove on the actual Android 9 Chromebook: process startup, Java main-class execution, classpath/native loading, stdout/stderr capture, cancellation, clean stop, repeated launches, memory behavior, and no ART/app instability. It must also be checked on the other advertised API levels and ABIs before expanding support.

## Release gate

No candidate is approved until a reproducible device test demonstrates a real Java smoke-test program running in the intended isolated runtime process. A linker preflight or unit test is not sufficient. Minecraft/LWJGL rendering remains a separate subsequent gate.
