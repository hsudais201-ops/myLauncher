#include <jni.h>
#include <dlfcn.h>
#include <string>

namespace {

jstring ToJavaString(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

}  // namespace

extern "C"
JNIEXPORT jstring JNICALL
Java_com_flame_launcher_runtime_NativeJvmLibraryProbe_nativeInspectJvmLibrary(
    JNIEnv* env,
    jobject /* receiver */,
    jstring java_path
) {
    if (java_path == nullptr) {
        return ToJavaString(env, "ERROR: JVM library path is missing.");
    }

    const char* path_chars = env->GetStringUTFChars(java_path, nullptr);
    if (path_chars == nullptr) {
        return ToJavaString(env, "ERROR: Unable to read JVM library path.");
    }

    const std::string path(path_chars);
    env->ReleaseStringUTFChars(java_path, path_chars);

    dlerror();
    void* handle = dlopen(path.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (handle == nullptr) {
        const char* error = dlerror();
        return ToJavaString(
            env,
            std::string("ERROR: Android linker could not load libjvm: ") +
                (error == nullptr ? "unknown linker error" : error)
        );
    }

    dlerror();
    void* create_vm = dlsym(handle, "JNI_CreateJavaVM");
    const char* symbol_error = dlerror();
    const bool has_create_vm = create_vm != nullptr && symbol_error == nullptr;

    dlerror();
    void* get_created_vms = dlsym(handle, "JNI_GetCreatedJavaVMs");
    const char* list_error = dlerror();
    const bool has_get_created_vms =
        get_created_vms != nullptr && list_error == nullptr;

    dlclose(handle);

    if (!has_create_vm) {
        return ToJavaString(
            env,
            "ERROR: libjvm loaded but does not export JNI_CreateJavaVM."
        );
    }
    if (!has_get_created_vms) {
        return ToJavaString(
            env,
            "ERROR: libjvm loaded but does not export JNI_GetCreatedJavaVMs."
        );
    }

    return ToJavaString(env, "OK: Android linker loaded libjvm and found JNI invocation symbols.");
}
