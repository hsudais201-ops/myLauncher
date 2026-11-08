package com.flame.launcher.runtime

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
        memory: MinecraftLaunchMemory = MinecraftLaunchMemory()
    ): MinecraftLaunchCommand {
        require(plan.versionId.matches(VERSION_ID)) { "Minecraft version ID is invalid." }
        require(plan.mainClass.matches(MAIN_CLASS)) { "Minecraft main class is invalid." }
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
        require(memory.minimumMb in MIN_HEAP_MB..MAX_HEAP_MB &&
            memory.maximumMb in memory.minimumMb..MAX_HEAP_MB
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
            "-Xms${memory.minimumMb}M",
            "-Xmx${memory.maximumMb}M",
            "-Djava.library.path=${nativeRoot.absolutePath}",
            "-Dorg.lwjgl.librarypath=${nativeRoot.absolutePath}",
            "-Dminecraft.launcher.brand=FlameLauncher",
            "-Dminecraft.launcher.version=0.1.0",
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
        )
        require(account.userType in setOf("msa", "legacy", "mojang")) {
            "Minecraft account type is invalid."
        }

        return MinecraftLaunchCommand(
            executable = runtime.javaExecutable ?: throw IllegalArgumentException(
                "Selected runtime has no executable Java process. An embedded JVM bridge is required."
            ),
            workingDirectory = gameRoot,
            arguments = jvmArguments + gameArguments,
            classPath = classPath
        )
    }

    private fun isSafeRelativePath(value: String): Boolean =
        value.isNotBlank() && !value.startsWith("/") && '\\\\' !in value &&
            ':' !in value && value.split('/').all {
                it.isNotBlank() && it != "." && it != ".."
            }

    private const val MAX_TOKEN_LENGTH = 16 * 1024
    private const val MIN_HEAP_MB = 256
    private const val MAX_HEAP_MB = 16 * 1024
    private val VERSION_ID = Regex("[A-Za-z0-9._-]{1,64}")
    private val MAIN_CLASS = Regex("[A-Za-z_$][A-Za-z0-9_$.]{1,255}")
    private val UUID_PATTERN = Regex("[0-9a-fA-F]{32}|[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
    private val ASSET_INDEX_ID = Regex("[A-Za-z0-9._-]{1,128}")
    private val SUPPORTED_ARCHITECTURES = setOf("arm64", "arm", "x86_64", "x86")
}

data class MinecraftLaunchAccount(
    val username: String,
    val uuid: String,
    val accessToken: String,
    val userType: String = "msa"
)

data class MinecraftLaunchMemory(
    val minimumMb: Int = 512,
    val maximumMb: Int = 1536
)

data class MinecraftLaunchCommand(
    val executable: File,
    val workingDirectory: File,
    val arguments: List<String>,
    val classPath: List<String>
) {
    /** Creates a ProcessBuilder without invoking a shell or interpolating a command string. */
    fun toProcessBuilder(): ProcessBuilder =
        ProcessBuilder(listOf(executable.absolutePath) + arguments)
            .directory(workingDirectory)
            .redirectErrorStream(true)
}
