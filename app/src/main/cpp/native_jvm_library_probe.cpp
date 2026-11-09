#include <jni.h>
#include <dlfcn.h>
#include <elf.h>
#include <cstdint>
#include <fstream>
#include <string>

namespace {

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

    const std::string elf_error = ValidateElf(path);
    if (!elf_error.empty()) {
        return ToJavaString(env, elf_error);
    }

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

    return ToJavaString(env, "OK: ELF ABI and program-header bounds match, Android linker loaded libjvm, and JNI invocation symbols are exported.");
}
