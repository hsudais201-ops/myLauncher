#include <jni.h>
#include <dlfcn.h>
#include <elf.h>
#include <cstdint>
#include <fstream>
#include <mutex>
#include <string>
#include <type_traits>
#include <vector>
#include <utility>

namespace {

std::mutex g_jvm_loader_mutex;
void* g_retained_jvm_handle = nullptr;
using GetCreatedJavaVmsFn = jint (JNICALL *)(JavaVM**, jsize, jsize*);
GetCreatedJavaVmsFn g_get_created_java_vms = nullptr;
std::string g_retained_jvm_path;
std::vector<void*> g_retained_dependency_handles;

jstring ToJavaString(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

#if defined(__aarch64__)
constexpr Elf64_Half kExpectedMachine = EM_AARCH64;
#elif defined(__arm__)
constexpr Elf32_Half kExpectedMachine = EM_ARM;
#elif defined(__x86_64__)
constexpr Elf64_Half kExpectedMachine = EM_X86_64;
#elif defined(__i386__)
constexpr Elf32_Half kExpectedMachine = EM_386;
#else
#error "Unsupported Android ABI for embedded JVM inspection"
#endif

template <typename Header>
std::string ValidateElfHeader(std::ifstream& input, std::uint64_t file_size) {
    Header header = {};
    input.seekg(0);
    input.read(reinterpret_cast<char*>(&header), sizeof(header));
    if (!input) {
        return "ERROR: Selected libjvm has a truncated ELF header.";
    }
    if (header.e_type != ET_DYN) {
        return "ERROR: Selected libjvm is not a shared ELF object.";
    }
    if (header.e_version != EV_CURRENT) {
        return "ERROR: Selected libjvm uses an unsupported ELF version.";
    }
    if (header.e_machine != kExpectedMachine) {
        return "ERROR: Selected libjvm architecture does not match this Android process ABI.";
    }
    if (header.e_ehsize != sizeof(Header)) {
        return "ERROR: Selected libjvm has an invalid ELF header size.";
    }

    // A loadable shared object must provide a complete program-header table.
    // Validate the table bounds before the Android linker parses the file.
    if (header.e_phnum == 0 || header.e_phentsize == 0 ||
        header.e_phentsize < sizeof(typename std::conditional<
            sizeof(Header) == sizeof(Elf64_Ehdr),
            Elf64_Phdr, Elf32_Phdr>::type)) {
        return "ERROR: Selected libjvm has an invalid ELF program-header table.";
    }
    const std::uint64_t table_offset = static_cast<std::uint64_t>(header.e_phoff);
    const std::uint64_t table_size =
        static_cast<std::uint64_t>(header.e_phnum) * header.e_phentsize;
    if (table_offset > file_size || table_size > file_size - table_offset) {
        return "ERROR: Selected libjvm has a truncated ELF program-header table.";
    }

    using ProgramHeader = typename std::conditional<
        sizeof(Header) == sizeof(Elf64_Ehdr),
        Elf64_Phdr, Elf32_Phdr>::type;
    bool has_loadable_segment = false;
    input.seekg(static_cast<std::streamoff>(table_offset));
    for (std::uint16_t index = 0; index < header.e_phnum; ++index) {
        ProgramHeader segment = {};
        input.read(reinterpret_cast<char*>(&segment), sizeof(segment));
        if (!input) {
            return "ERROR: Selected libjvm has a truncated ELF segment entry.";
        }
        if (segment.p_type != PT_LOAD) {
            const std::streamoff next =
                static_cast<std::streamoff>(table_offset) +
                static_cast<std::streamoff>(index + 1) * header.e_phentsize;
            input.seekg(next);
            continue;
        }
        has_loadable_segment = true;
        const std::uint64_t offset = static_cast<std::uint64_t>(segment.p_offset);
        const std::uint64_t bytes = static_cast<std::uint64_t>(segment.p_filesz);
        const std::uint64_t memory = static_cast<std::uint64_t>(segment.p_memsz);
        if (bytes > memory || offset > file_size || bytes > file_size - offset) {
            return "ERROR: Selected libjvm contains an invalid loadable ELF segment.";
        }
        if (segment.p_align > 1 &&
            (segment.p_align & (segment.p_align - 1)) != 0) {
            return "ERROR: Selected libjvm contains an invalid ELF segment alignment.";
        }
        const std::uint64_t alignment = static_cast<std::uint64_t>(segment.p_align);
        if (alignment > 1 &&
            (static_cast<std::uint64_t>(segment.p_vaddr) % alignment) !=
                (offset % alignment)) {
            return "ERROR: Selected libjvm contains a misaligned ELF load segment.";
        }
        const std::streamoff next =
            static_cast<std::streamoff>(table_offset) +
            static_cast<std::streamoff>(index + 1) * header.e_phentsize;
        input.seekg(next);
    }
    if (!has_loadable_segment) {
        return "ERROR: Selected libjvm has no loadable ELF segments.";
    }
    return "";
}

std::string ValidateElf(const std::string& path) {
    std::ifstream input(path, std::ios::binary);
    if (!input) {
        return "ERROR: Unable to open libjvm for ELF validation.";
    }

    input.seekg(0, std::ios::end);
    const std::streamoff end = input.tellg();
    if (end < static_cast<std::streamoff>(EI_NIDENT)) {
        return "ERROR: Selected libjvm is too small to contain an ELF header.";
    }
    const std::uint64_t file_size = static_cast<std::uint64_t>(end);

    unsigned char identity[EI_NIDENT] = {};
    input.seekg(0);
    input.read(reinterpret_cast<char*>(identity), sizeof(identity));
    if (!input || identity[EI_MAG0] != ELFMAG0 ||
        identity[EI_MAG1] != ELFMAG1 ||
        identity[EI_MAG2] != ELFMAG2 ||
        identity[EI_MAG3] != ELFMAG3) {
        return "ERROR: Selected libjvm is not a valid ELF library.";
    }
    if (identity[EI_DATA] != ELFDATA2LSB) {
        return "ERROR: Selected libjvm has an unsupported ELF byte order.";
    }

#if defined(__LP64__)
    if (identity[EI_CLASS] != ELFCLASS64) {
        return "ERROR: Selected libjvm is not a 64-bit library for this Android process.";
    }
    return ValidateElfHeader<Elf64_Ehdr>(input, file_size);
#else
    if (identity[EI_CLASS] != ELFCLASS32) {
        return "ERROR: Selected libjvm is not a 32-bit library for this Android process.";
    }
    return ValidateElfHeader<Elf32_Ehdr>(input, file_size);
#endif
}

}  // namespace

namespace {

jstring InspectJvmRuntime(JNIEnv* env, jstring java_path, jobjectArray dependency_paths) {
    if (java_path == nullptr) {
        return ToJavaString(env, "ERROR: JVM library path is missing.");
    }
    const char* path_chars = env->GetStringUTFChars(java_path, nullptr);
    if (path_chars == nullptr) {
        return ToJavaString(env, "ERROR: Unable to read JVM library path.");
    }
    const std::string path(path_chars);
    env->ReleaseStringUTFChars(java_path, path_chars);

    std::vector<void*> temporary_dependencies;
    const jsize dependency_count = dependency_paths == nullptr
        ? 0
        : env->GetArrayLength(dependency_paths);
    for (jsize index = 0; index < dependency_count; ++index) {
        auto dependency = static_cast<jstring>(
            env->GetObjectArrayElement(dependency_paths, index)
        );
        if (dependency == nullptr) {
            for (auto it = temporary_dependencies.rbegin(); it != temporary_dependencies.rend(); ++it) dlclose(*it);
            return ToJavaString(env, "ERROR: JVM dependency path is null.");
        }
        const char* dependency_chars = env->GetStringUTFChars(dependency, nullptr);
        if (dependency_chars == nullptr) {
            env->DeleteLocalRef(dependency);
            for (auto it = temporary_dependencies.rbegin(); it != temporary_dependencies.rend(); ++it) dlclose(*it);
            return ToJavaString(env, "ERROR: Unable to read JVM dependency path.");
        }
        const std::string dependency_path(dependency_chars);
        env->ReleaseStringUTFChars(dependency, dependency_chars);
        env->DeleteLocalRef(dependency);

        const std::string dependency_elf_error = ValidateElf(dependency_path);
        if (!dependency_elf_error.empty()) {
            for (auto it = temporary_dependencies.rbegin(); it != temporary_dependencies.rend(); ++it) dlclose(*it);
            return ToJavaString(
                env,
                "ERROR: JVM dependency failed ELF validation: " + dependency_elf_error
            );
        }
        dlerror();
        void* dependency_handle = dlopen(dependency_path.c_str(), RTLD_NOW | RTLD_GLOBAL);
        if (dependency_handle == nullptr) {
            const char* error = dlerror();
            for (auto it = temporary_dependencies.rbegin(); it != temporary_dependencies.rend(); ++it) dlclose(*it);
            return ToJavaString(
                env,
                std::string("ERROR: Android linker could not preflight JVM dependency: ") +
                    (error == nullptr ? "unknown linker error" : error)
            );
        }
        temporary_dependencies.push_back(dependency_handle);
    }

    const std::string elf_error = ValidateElf(path);
    if (!elf_error.empty()) {
        for (auto it = temporary_dependencies.rbegin(); it != temporary_dependencies.rend(); ++it) dlclose(*it);
        return ToJavaString(env, elf_error);
    }

    dlerror();
    void* handle = dlopen(path.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (handle == nullptr) {
        const char* error = dlerror();
        for (auto it = temporary_dependencies.rbegin(); it != temporary_dependencies.rend(); ++it) dlclose(*it);
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
    for (auto it = temporary_dependencies.rbegin(); it != temporary_dependencies.rend(); ++it) dlclose(*it);

    if (!has_create_vm) {
        return ToJavaString(env, "ERROR: libjvm loaded but does not export JNI_CreateJavaVM.");
    }
    if (!has_get_created_vms) {
        return ToJavaString(env, "ERROR: libjvm loaded but does not export JNI_GetCreatedJavaVMs.");
    }

    return ToJavaString(
        env,
        "OK: JVM dependency closure loaded in order, Android linker accepted libjvm, and JNI invocation symbols are exported."
    );
}

}  // namespace

extern "C"
JNIEXPORT jstring JNICALL
Java_com_flame_launcher_runtime_NativeJvmLibraryProbe_nativeInspectJvmLibrary(
    JNIEnv* env,
    jobject /* receiver */,
    jstring java_path
) {
    return InspectJvmRuntime(env, java_path, nullptr);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_flame_launcher_runtime_NativeJvmLibraryProbe_nativeInspectJvmRuntime(
    JNIEnv* env,
    jobject /* receiver */,
    jstring java_path,
    jobjectArray dependency_paths
) {
    return InspectJvmRuntime(env, java_path, dependency_paths);
}


/*
 * Retain exactly one embedded JVM for the lifetime of this Android process.
 * JNI invocation permits one VM per process in the supported Android setup;
 * unloading libjvm while Java/native threads may still exist is unsafe.
 */
namespace {

jstring RetainJvmRuntime(JNIEnv* env, jstring java_path, jobjectArray dependency_paths) {
    if (java_path == nullptr) {
        return ToJavaString(env, "ERROR: JVM library path is missing.");
    }
    const char* path_chars = env->GetStringUTFChars(java_path, nullptr);
    if (path_chars == nullptr) {
        return ToJavaString(env, "ERROR: Unable to read JVM library path.");
    }
    const std::string path(path_chars);
    env->ReleaseStringUTFChars(java_path, path_chars);

    std::lock_guard<std::mutex> lock(g_jvm_loader_mutex);
    if (g_retained_jvm_handle != nullptr) {
        if (g_retained_jvm_path == path) {
            return ToJavaString(env, "OK: libjvm and its runtime dependencies are already retained for this process.");
        }
        return ToJavaString(
            env,
            "ERROR: This Android process already retains a different JVM; "
            "a second embedded runtime cannot be loaded."
        );
    }

    std::vector<void*> newly_loaded;
    const jsize dependency_count = dependency_paths == nullptr
        ? 0
        : env->GetArrayLength(dependency_paths);
    for (jsize index = 0; index < dependency_count; ++index) {
        auto dependency = static_cast<jstring>(
            env->GetObjectArrayElement(dependency_paths, index)
        );
        if (dependency == nullptr) {
            for (auto it = newly_loaded.rbegin(); it != newly_loaded.rend(); ++it) dlclose(*it);
            return ToJavaString(env, "ERROR: JVM dependency path is null.");
        }
        const char* dependency_chars = env->GetStringUTFChars(dependency, nullptr);
        if (dependency_chars == nullptr) {
            env->DeleteLocalRef(dependency);
            for (auto it = newly_loaded.rbegin(); it != newly_loaded.rend(); ++it) dlclose(*it);
            return ToJavaString(env, "ERROR: Unable to read JVM dependency path.");
        }
        const std::string dependency_path(dependency_chars);
        env->ReleaseStringUTFChars(dependency, dependency_chars);
        env->DeleteLocalRef(dependency);

        const std::string dependency_elf_error = ValidateElf(dependency_path);
        if (!dependency_elf_error.empty()) {
            for (auto it = newly_loaded.rbegin(); it != newly_loaded.rend(); ++it) dlclose(*it);
            return ToJavaString(
                env,
                "ERROR: JVM runtime dependency failed ELF validation: " + dependency_elf_error
            );
        }

        dlerror();
        void* dependency_handle = dlopen(dependency_path.c_str(), RTLD_NOW | RTLD_GLOBAL);
        if (dependency_handle == nullptr) {
            const char* error = dlerror();
            for (auto it = newly_loaded.rbegin(); it != newly_loaded.rend(); ++it) dlclose(*it);
            return ToJavaString(
                env,
                std::string("ERROR: Android linker could not preload JVM dependency: ") +
                    (error == nullptr ? "unknown linker error" : error)
            );
        }
        newly_loaded.push_back(dependency_handle);
    }

    const std::string elf_error = ValidateElf(path);
    if (!elf_error.empty()) {
        for (auto it = newly_loaded.rbegin(); it != newly_loaded.rend(); ++it) dlclose(*it);
        return ToJavaString(env, elf_error);
    }

    dlerror();
    void* handle = dlopen(path.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (handle == nullptr) {
        const char* error = dlerror();
        for (auto it = newly_loaded.rbegin(); it != newly_loaded.rend(); ++it) dlclose(*it);
        return ToJavaString(
            env,
            std::string("ERROR: Android linker could not retain libjvm: ") +
                (error == nullptr ? "unknown linker error" : error)
        );
    }

    dlerror();
    void* create_vm = dlsym(handle, "JNI_CreateJavaVM");
    const char* create_error = dlerror();
    dlerror();
    void* get_created_vms = dlsym(handle, "JNI_GetCreatedJavaVMs");
    const char* list_error = dlerror();
    if (create_vm == nullptr || create_error != nullptr ||
        get_created_vms == nullptr || list_error != nullptr) {
        dlclose(handle);
        for (auto it = newly_loaded.rbegin(); it != newly_loaded.rend(); ++it) dlclose(*it);
        return ToJavaString(
            env,
            "ERROR: Retained library does not export the required JNI invocation symbols."
        );
    }

    // The dependency-first handles and libjvm are process-lifetime references.
    // Releasing them while VM-owned code or threads are active is unsafe.
    g_retained_dependency_handles = std::move(newly_loaded);
    g_retained_jvm_path = path;
    g_get_created_java_vms = reinterpret_cast<GetCreatedJavaVmsFn>(get_created_vms);
    g_retained_jvm_handle = handle;
    return ToJavaString(
        env,
        "OK: JVM runtime dependencies were preloaded in order and libjvm is retained for process lifetime."
    );
}

}  // namespace

extern "C"
JNIEXPORT jstring JNICALL
Java_com_flame_launcher_runtime_NativeJvmLibraryLoader_nativeRetainJvmLibrary(
    JNIEnv* env,
    jobject /* receiver */,
    jstring java_path
) {
    return RetainJvmRuntime(env, java_path, nullptr);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_flame_launcher_runtime_NativeJvmLibraryLoader_nativeRetainJvmRuntime(
    JNIEnv* env,
    jobject /* receiver */,
    jstring java_path,
    jobjectArray dependency_paths
) {
    return RetainJvmRuntime(env, java_path, dependency_paths);
}

/*
 * Query only the selected embedded OpenJDK library. This does not enumerate
 * Android ART and does not create, attach, or destroy any virtual machine.
 */
extern "C"
JNIEXPORT jstring JNICALL
Java_com_flame_launcher_runtime_NativeJvmLibraryLoader_nativeInspectVmState(
    JNIEnv* env,
    jobject /* receiver */
) {
    std::lock_guard<std::mutex> lock(g_jvm_loader_mutex);
    if (g_retained_jvm_handle == nullptr || g_get_created_java_vms == nullptr) {
        return ToJavaString(env, "ERROR: No embedded JVM library is retained by this process.");
    }

    JavaVM* existing_vms[1] = {};
    jsize count = 0;
    const jint status = g_get_created_java_vms(existing_vms, 1, &count);
    if (status != JNI_OK) {
        return ToJavaString(
            env,
            "ERROR: JNI_GetCreatedJavaVMs failed with status " + std::to_string(status) + "."
        );
    }
    return ToJavaString(
        env,
        "OK: retained OpenJDK reports " + std::to_string(count) +
            " created VM(s); no VM was created or modified by this check."
    );
}


/*
 * Compare the selected OpenJDK invocation entry point with the symbol that
 * an ordinary process-wide lookup would resolve. Android apps already run on
 * ART; this diagnostic makes symbol interposition visible without calling
 * JNI_CreateJavaVM or JNI_GetCreatedJavaVMs through the global namespace.
 */
namespace {
std::string DescribeSymbolProvider(void* symbol) {
    if (symbol == nullptr) return "unresolved";
    Dl_info info = {};
    if (dladdr(symbol, &info) == 0 || info.dli_fname == nullptr) {
        return "unknown provider";
    }
    return std::string(info.dli_fname);
}
}  // namespace

extern "C"
JNIEXPORT jstring JNICALL
Java_com_flame_launcher_runtime_NativeJvmLibraryLoader_nativeInspectInvocationApi(
    JNIEnv* env,
    jobject /* receiver */
) {
    std::lock_guard<std::mutex> lock(g_jvm_loader_mutex);
    if (g_retained_jvm_handle == nullptr) {
        return ToJavaString(env, "ERROR: Retain the selected OpenJDK runtime before inspecting JNI invocation providers.");
    }

    dlerror();
    void* selected_create = dlsym(g_retained_jvm_handle, "JNI_CreateJavaVM");
    const char* selected_error = dlerror();
    if (selected_create == nullptr || selected_error != nullptr) {
        return ToJavaString(env, "ERROR: Selected OpenJDK does not expose JNI_CreateJavaVM.");
    }

    dlerror();
    void* process_create = dlsym(RTLD_DEFAULT, "JNI_CreateJavaVM");
    const char* process_error = dlerror();
    if (process_create == nullptr || process_error != nullptr) {
        return ToJavaString(
            env,
            "WARN: No process-global JNI_CreateJavaVM provider was found; "
            "the selected OpenJDK provider is " + DescribeSymbolProvider(selected_create) + "."
        );
    }

    const std::string selected_provider = DescribeSymbolProvider(selected_create);
    const std::string process_provider = DescribeSymbolProvider(process_create);
    if (process_provider.find("libart.so") != std::string::npos) {
        return ToJavaString(
            env,
            "BLOCKED: process-global JNI_CreateJavaVM resolves to Android ART (" +
                process_provider + "), while the selected OpenJDK provider is " +
                selected_provider +
                ". Do not invoke either provider for a second VM until a supported "
                "runtime-isolation strategy is verified."
        );
    }

    if (process_create != selected_create) {
        return ToJavaString(
            env,
            "WARN: process-global JNI_CreateJavaVM resolves to " + process_provider +
                ", not the selected OpenJDK provider " + selected_provider +
                ". Invocation is not authorized by this diagnostic."
        );
    }

    return ToJavaString(
        env,
        "WARN: process-global and selected JNI_CreateJavaVM resolve to " +
            selected_provider +
            ". Symbol identity alone does not prove Android ART coexistence is safe."
    );
}
