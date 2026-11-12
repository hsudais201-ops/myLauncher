# Embedded JVM isolation decision for Android

Status: **Open architecture gate — no production VM invocation is authorized.**

## Current application constraints

The Android application currently declares `minSdk = 26` and `targetSdk = 36` in `app/build.gradle.kts`. The target SDK is important: a solution that only works by lowering the target below Android 10 is not an acceptable implementation for this project.

## Evidence

1. The Java Native Interface Invocation API states that creating multiple Java VMs in one process is not supported. See the [JNI Invocation API](https://docs.oracle.com/javase/10/docs/specs/jni/invocation.html).
2. Android app processes already execute on ART. AOSP's ART implementation exports `JNI_CreateJavaVM` and initializes its own `Runtime` through that entry point; seeing the symbol in a process therefore does not establish that a second, independent HotSpot VM can safely coexist. See [AOSP ART JNI implementation](https://android.googlesource.com/platform/art/+/master/runtime/jni/java_vm_ext.cc) and [Android's ART guidance](https://developer.android.com/guide/practices/verifying-apps-art).
3. Android's security guidance states that apps targeting API 29 or later cannot execute files from their writable app home directory; executable code must be embedded in the APK. This blocks the naive design of downloading a JRE into `filesDir` and invoking its `bin/java` with `ProcessBuilder` on the current target SDK. See [Android app security best practices](https://developer.android.com/privacy-and-security/security-best-practices) and [Android 10 behavior changes](https://developer.android.com/about/versions/10/behavior-changes-all#execute-permission).

## Decision

- Do not call a downloaded HotSpot/OpenJDK `JNI_CreateJavaVM` from the Android application process.
- Do not treat `dlopen`, `dlsym`, `JNI_CreateJavaVM` symbol presence, or `JNI_GetCreatedJavaVMs` as proof of safe VM creation.
- Do not use a second Android app process or ordinary Android Service as a supposed VM-isolation boundary; those are still Android app processes running ART.
- Do not switch to `ProcessBuilder` for a downloaded executable in writable app storage while targeting API 36.
- Keep the current fail-closed `JvmInvocationReadiness` gate and Play disabled.

## Architectures to evaluate before implementation

1. **APK-packaged helper executable/runtime:** determine whether a helper and the required runtime can be packaged as signed, read-only executable code in the installed APK/native-library area, and whether its size, updates, ABI coverage, and licensing are practical.
2. **Dedicated helper APK:** evaluate a separately installed helper package with its own process/UID and a narrow authenticated IPC protocol. This introduces installation consent, updates, package visibility, and lifecycle work; it is not yet selected.
3. **User-mode ELF loader:** evaluate a maintained loader that can run a downloaded Linux JVM without direct `execve` of a writable app-home executable. This is a substantial compatibility and security project, not a small JNI change.

Each candidate must prove on the actual Android 9 Chromebook: process startup, Java main-class execution, classpath/native loading, stdout/stderr capture, cancellation, clean stop, repeated launches, memory behavior, and no ART/app instability. It must also be checked on the other advertised API levels and ABIs before expanding support.

## Release gate

No candidate is approved until a reproducible device test demonstrates a real Java smoke-test program running in the intended isolated runtime process. A linker preflight or unit test is not sufficient. Minecraft/LWJGL rendering remains a separate subsequent gate.
