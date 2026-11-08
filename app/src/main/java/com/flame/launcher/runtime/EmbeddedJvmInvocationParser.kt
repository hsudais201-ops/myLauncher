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
        require(MAIN_CLASS.matches(mainClass)) {
            "Embedded JVM main class is invalid."
        }
        val vmOptions = arguments.take(switchIndex).toMutableList()
        require(vmOptions.all { it.startsWith("-") }) {
            "Embedded JVM options contain an unexpected positional argument."
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

    private val MAIN_CLASS = Regex("[A-Za-z_$][A-Za-z0-9_$.]{1,255}")
}

data class EmbeddedJvmInvocation(
    val vmOptions: List<String>,
    val classPath: List<File>,
    val mainClass: String,
    val applicationArguments: List<String>
)
