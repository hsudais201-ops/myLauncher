package com.flame.launcher.runtime

import com.flame.launcher.content.MinecraftInstanceLaunchSettings
import java.io.File

/**
 * Builds a shell-free JVM invocation for an installed vanilla Minecraft version.
 *
 * Authentication data is accepted only for the duration of the launch request;
 * callers must not persist this object or log its arguments.
 */
object MinecraftLaunchCommandBuilder {
    fun build(
        plan: MinecraftLaunchDependencyPlan,
        gameDirectory: File,
        nativeDirectory: File,
        runtime: JavaRuntimeInstallation,
        account: MinecraftLaunchAccount,
        memory: MinecraftLaunchMemory = MinecraftLaunchMemory(),
        profileSettings: MinecraftInstanceLaunchSettings? = null
    ): MinecraftLaunchCommand {
        val profileOptions = profileSettings?.let(MinecraftLaunchProfileOptionsBuilder::build)
        val effectiveMemory = profileOptions?.memory ?: memory
        require(plan.versionId.matches(VERSION_ID)) { "Minecraft version ID is invalid." }
        require(plan.mainClass.length <= 256 && plan.mainClass.matches(MAIN_CLASS)) {
            "Minecraft main class is invalid."
        }
        require(plan.requiredJavaMajorVersion == null ||
            runtime.majorVersion == plan.requiredJavaMajorVersion
        ) { "Selected Java runtime does not match this Minecraft version's Java requirement." }
        require(runtime.architecture in SUPPORTED_ARCHITECTURES) {
            "Selected Java runtime architecture is unsupported."
        }
        require(account.username.isNotBlank() && account.username.length <= 64 &&
            account.username.none { it.isISOControl() }
        ) { "Minecraft account username is invalid." }
        require(UUID_PATTERN.matches(account.uuid)) { "Minecraft account UUID is invalid." }
        require(account.accessToken.isNotBlank() && account.accessToken.length <= MAX_TOKEN_LENGTH) {
            "Minecraft access token is missing or invalid."
        }
        require(effectiveMemory.minimumMb in MIN_HEAP_MB..MAX_HEAP_MB &&
            effectiveMemory.maximumMb in effectiveMemory.minimumMb..MAX_HEAP_MB
        ) { "Minecraft memory settings are invalid." }

        val gameRoot = gameDirectory.canonicalFile
        require(gameRoot.isDirectory) { "Minecraft game directory does not exist." }
        val nativeRoot = nativeDirectory.canonicalFile
        require(nativeRoot.isDirectory && nativeRoot.path.startsWith(gameRoot.path + File.separator)) {
            "Minecraft native directory must be inside the game directory."
        }
        val classPath = plan.classPath.map { relative ->
            require(isSafeRelativePath(relative)) { "Minecraft classpath contains an unsafe path." }
            val file = File(gameRoot, relative).canonicalFile
            require(file.path.startsWith(gameRoot.path + File.separator) && file.isFile) {
                "Minecraft classpath artifact is missing or outside the game directory: $relative"
            }
            file.absolutePath
        }.distinct()
        require(classPath.isNotEmpty()) { "Minecraft classpath is empty." }

        val clientPath = "versions/${plan.versionId}/${plan.versionId}.jar"
        require(clientPath in plan.classPath) { "Minecraft client JAR is missing from the classpath." }

        val assetsDirectory = File(gameRoot, "assets").canonicalFile
        require(assetsDirectory.isDirectory) { "Minecraft assets directory is missing." }
        require(plan.assetIndexId.matches(ASSET_INDEX_ID)) { "Minecraft asset index ID is invalid." }

        val jvmArguments = listOf(
            "-Xms${effectiveMemory.minimumMb}M",
            "-Xmx${effectiveMemory.maximumMb}M",
            "-Djava.library.path=${nativeRoot.absolutePath}",
            "-Dorg.lwjgl.librarypath=${nativeRoot.absolutePath}",
            "-Dminecraft.launcher.brand=FlameLauncher",
            "-Dminecraft.launcher.version=0.1.0"
        ) + profileOptions?.jvmArguments.orEmpty() + listOf(
            "-cp",
            classPath.joinToString(File.pathSeparator)
        )
        val gameArguments = listOf(
            plan.mainClass,
            "--username", account.username,
            "--version", plan.versionId,
            "--gameDir", gameRoot.absolutePath,
            "--assetsDir", assetsDirectory.absolutePath,
            "--assetIndex", plan.assetIndexId,
            "--uuid", account.uuid.replace("-", ""),
            "--accessToken", account.accessToken,
            "--userType", account.userType,
            "--versionType", "release"
        ) + profileOptions?.let { options ->
            buildList {
                add("--width")
                add(options.width.toString())
                add("--height")
                add(options.height.toString())
                if (options.fullscreen) add("--fullscreen")
                addAll(options.gameArguments)
            }
        }.orEmpty()
        require(account.userType in setOf("msa", "legacy", "mojang")) {
            "Minecraft account type is invalid."
        }

        val jvmLibrary = runtime.jvmLibrary?.canonicalFile
        if (runtime.executionKind == JavaRuntimeExecutionKind.EMBEDDED_JVM) {
            require(jvmLibrary != null && jvmLibrary.isFile &&
                jvmLibrary.path.startsWith(runtime.homeDirectory.canonicalPath + File.separator)
            ) { "Selected embedded runtime has no valid JVM library." }
        } else {
            require(runtime.javaExecutable?.isFile == true) {
                "Selected process runtime has no executable Java binary."
            }
        }

        return MinecraftLaunchCommand(
            executable = runtime.javaExecutable,
            workingDirectory = gameRoot,
            arguments = jvmArguments + gameArguments,
            classPath = classPath,
            runtimeHome = runtime.homeDirectory.canonicalFile,
            jvmLibrary = jvmLibrary,
            executionKind = runtime.executionKind
        )
    }

    private fun isSafeRelativePath(value: String): Boolean =
        value.isNotBlank() && !value.startsWith("/") && value.indexOf(92.toChar()) < 0 &&
            ':' !in value && value.split('/').all {
                it.isNotBlank() && it != "." && it != ".."
            }

    private const val MAX_TOKEN_LENGTH = 16 * 1024
    private const val MIN_HEAP_MB = 256
    private const val MAX_HEAP_MB = 16 * 1024
    private val VERSION_ID = Regex("[A-Za-z0-9._-]{1,64}")
    private val MAIN_CLASS = Regex(
        "[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*"
    )
    private val UUID_PATTERN = Regex("[0-9a-fA-F]{32}|[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
    private val ASSET_INDEX_ID = Regex("[A-Za-z0-9._-]{1,128}")
    private val SUPPORTED_ARCHITECTURES = setOf("arm64", "arm", "x86_64", "x86")
}

data class MinecraftLaunchAccount(
    val username: String,
    val uuid: String,
    val accessToken: String,
    val userType: String = "msa"
) {
    override fun toString(): String =
        "MinecraftLaunchAccount(username=$username, uuid=$uuid, accessToken=<redacted>, userType=$userType)"
}

data class MinecraftLaunchMemory(
    val minimumMb: Int = 512,
    val maximumMb: Int = 1536
)

data class MinecraftLaunchCommand(
    val executable: File?,
    val workingDirectory: File,
    val arguments: List<String>,
    val classPath: List<String>,
    val runtimeHome: File? = null,
    val jvmLibrary: File? = null,
    val executionKind: JavaRuntimeExecutionKind = JavaRuntimeExecutionKind.PROCESS
) {
    /**
     * Creates a shell-free process invocation for runtimes with an executable
     * Java binary. Embedded Android JVMs must use MinecraftProcessHost instead.
     */
    fun toProcessBuilder(): ProcessBuilder {
        val java = executable
            ?: throw IllegalStateException("This launch command requires an embedded JVM host.")
        check(executionKind == JavaRuntimeExecutionKind.PROCESS) {
            "Embedded JVM commands cannot be started with ProcessBuilder."
        }
        return ProcessBuilder(listOf(java.absolutePath) + arguments)
            .directory(workingDirectory)
            .redirectErrorStream(true)
    }

    override fun toString(): String =
        "MinecraftLaunchCommand(executable=$executable, workingDirectory=$workingDirectory, " +
            "arguments=${redactLaunchArguments(arguments)}, classPath=$classPath, " +
            "runtimeHome=$runtimeHome, jvmLibrary=$jvmLibrary, executionKind=$executionKind)"
}

internal fun redactLaunchArguments(arguments: List<String>): List<String> {
    var redactNext = false
    return arguments.map { argument ->
        if (redactNext) {
            redactNext = false
            "<redacted>"
        } else {
            val tokenOption = TOKEN_OPTIONS.firstOrNull { option ->
                argument.startsWith("$option=")
            }
            if (tokenOption != null) {
                "$tokenOption=<redacted>"
            } else {
                redactNext = argument in TOKEN_OPTIONS
                argument
            }
        }
    }
}

private val TOKEN_OPTIONS = setOf("--accessToken", "--access-token")
