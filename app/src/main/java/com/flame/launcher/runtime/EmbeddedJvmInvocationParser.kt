package com.flame.launcher.runtime

import java.io.File

/**
 * Converts the launcher's shell-free argument vector into the distinct inputs
 * required by JNI_CreateJavaVM and the Java main-method invocation.
 *
 * JNI invocation options do not accept the java executable's -cp syntax, so
 * the classpath is converted into -Djava.class.path and kept separately.
 */
object EmbeddedJvmInvocationParser {
    fun parse(
        arguments: List<String>,
        expectedClassPath: List<String>,
        runtimeHome: File,
        workingDirectory: File
    ): EmbeddedJvmInvocation {
        require(arguments.isNotEmpty()) { "Embedded JVM launch arguments are empty." }
        require(arguments.all { it.isNotEmpty() && '\u0000' !in it }) {
            "Embedded JVM launch arguments contain an empty or invalid value."
        }
        val classPathSwitches = setOf("-cp", "-classpath", "--class-path")
        val switchIndices = arguments.indices.filter { arguments[it] in classPathSwitches }
        require(switchIndices.size == 1) {
            "Embedded JVM launch requires exactly one classpath switch."
        }
        val switchIndex = switchIndices.single()
        require(switchIndex > 0 && switchIndex + 2 < arguments.size) {
            "Embedded JVM classpath or main class is missing."
        }

        val classPathValue = arguments[switchIndex + 1]
        val classPath = classPathValue.split(File.pathSeparator)
        require(classPath.isNotEmpty() && classPath.none(String::isBlank)) {
            "Embedded JVM classpath contains an empty entry."
        }
        val canonicalClassPath = classPath.map { entry ->
            val file = File(entry).canonicalFile
            require(file.isFile) { "Embedded JVM classpath entry is missing: $entry" }
            file
        }
        val expected = expectedClassPath.map { File(it).canonicalFile }
        require(expected.isNotEmpty() && canonicalClassPath == expected) {
            "Embedded JVM command classpath does not match the validated launch plan."
        }

        val mainClass = arguments[switchIndex + 2]
        require(isValidMainClass(mainClass)) {
            "Embedded JVM main class is invalid."
        }
        val vmOptions = arguments.take(switchIndex).toMutableList()
        require(vmOptions.all { it.startsWith("-") }) {
            "Embedded JVM options contain an unexpected positional argument."
        }
        val launcherManagedProperties = setOf(
            "java.class.path",
            "java.home",
            "user.dir"
        )
        require(vmOptions.none { option ->
            option.startsWith("-D") &&
                option.substringAfter("-D").substringBefore('=')
                    .substringBefore(':') in launcherManagedProperties
        }) {
            "Embedded JVM options must not override launcher-managed system properties."
        }
        val home = runtimeHome.canonicalFile
        val working = workingDirectory.canonicalFile
        require(home.isDirectory && working.isDirectory) {
            "Embedded JVM runtime home or working directory is unavailable."
        }
        vmOptions += "-Djava.class.path=" + canonicalClassPath.joinToString(File.pathSeparator) {
            it.absolutePath
        }
        vmOptions += "-Djava.home=" + home.absolutePath
        vmOptions += "-Duser.dir=" + working.absolutePath

        return EmbeddedJvmInvocation(
            vmOptions = vmOptions,
            classPath = canonicalClassPath,
            mainClass = mainClass,
            applicationArguments = arguments.drop(switchIndex + 3)
        )
    }

    private fun isValidMainClass(value: String): Boolean =
        value.length <= 256 && MAIN_CLASS.matches(value)

    private val MAIN_CLASS = Regex(
        "[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*"
    )
}

/**
 * Immutable, JNI-shaped input for a future VM creation call.
 *
 * JNI_CreateJavaVM accepts one JavaVMOption per option string. Keeping this
 * conversion explicit prevents accidental shell quoting or passing the
 * desktop launcher-only -cp switch into the native invocation API. This plan
 * is data only: constructing it never calls JNI_CreateJavaVM.
 */
data class EmbeddedJvmCreatePlan(
    val options: List<String>,
    val ignoreUnrecognized: Boolean = false
) {
    init {
        require(options.none { it.isEmpty() || '\u0000' in it }) {
            "JNI VM options contain an empty or invalid value."
        }
        require(options.size <= MAX_OPTION_COUNT) {
            "JNI VM option count exceeds Flame's safety limit."
        }
        require(options.all { it.toByteArray(Charsets.UTF_8).size <= MAX_OPTION_BYTES }) {
            "A JNI VM option exceeds Flame's per-option size limit."
        }
        val totalBytes = options.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() }
        require(totalBytes <= MAX_TOTAL_OPTION_BYTES) {
            "Combined JNI VM options exceed Flame's safety limit."
        }
        require(!ignoreUnrecognized) {
            "Flame requires strict JNI option validation."
        }
    }

    val optionCount: Int get() = options.size

    companion object {
        private const val MAX_OPTION_COUNT = 256
        private const val MAX_OPTION_BYTES = 64 * 1024
        private const val MAX_TOTAL_OPTION_BYTES = 256 * 1024
    }
}

fun EmbeddedJvmInvocation.toVmCreationPlan(): EmbeddedJvmCreatePlan {
    require(vmOptions.isNotEmpty()) { "Embedded JVM options are empty." }
    require(vmOptions.none { it == "-cp" || it == "-classpath" || it == "--class-path" }) {
        "Desktop Java classpath switches must not reach JNI_CreateJavaVM."
    }
    require(vmOptions.none { it.startsWith("-javaagent:") ||
        it.startsWith("-agentlib:") || it.startsWith("-agentpath:") ||
        it.startsWith("-Xrun") }) {
        "Java agents and native agent options are disabled in embedded launch mode."
    }
    require(vmOptions.all(::isSupportedEmbeddedJvmOption)) {
        "Embedded JVM option is not in Flame's supported option set."
    }
    return EmbeddedJvmCreatePlan(
        options = vmOptions.toList(),
        ignoreUnrecognized = false
    )
}

private fun isSupportedEmbeddedJvmOption(option: String): Boolean {
    if (HEAP_OPTION.matches(option)) return true
    if (!option.startsWith("-D")) return false
    val property = option.substringAfter("-D").substringBefore('=')
    return property in SUPPORTED_SYSTEM_PROPERTIES &&
        '=' in option &&
        option.substringAfter('=', "").isNotEmpty()
}

private val HEAP_OPTION = Regex("-X(?:ms|mx)[1-9][0-9]*[kKmMgGtT]")
private val SUPPORTED_SYSTEM_PROPERTIES = setOf(
    "java.class.path",
    "java.home",
    "user.dir",
    "java.library.path",
    "org.lwjgl.librarypath",
    "minecraft.launcher.brand",
    "minecraft.launcher.version"
)

data class EmbeddedJvmInvocation(
    val vmOptions: List<String>,
    val classPath: List<File>,
    val mainClass: String,
    val applicationArguments: List<String>
) {
    override fun toString(): String =
        "EmbeddedJvmInvocation(vmOptions=$vmOptions, classPath=$classPath, " +
            "mainClass=$mainClass, applicationArguments=${redactLaunchArguments(applicationArguments)})"
}
