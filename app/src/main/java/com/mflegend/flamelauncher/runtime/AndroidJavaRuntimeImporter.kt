package com.mflegend.flamelauncher.runtime

import android.content.Context
import android.net.Uri
import com.mflegend.flamelauncher.storage.SafGameStorage
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.FileVisitResult
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Copies a user-selected, already-extracted Java runtime into app-private storage.
 *
 * Import never executes or loads the copied binaries. Only ARM64 Linux/AArch64
 * runtime layouts are accepted for the current initial Android target.
 */
class AndroidJavaRuntimeImporter(context: Context) {
    private val resolver = context.contentResolver
    private val privateRoot = context.noBackupFilesDir

    data class ImportResult(
        val runtimeId: String,
        val javaVersion: String,
        val javaMajorVersion: Int,
        val architecture: String,
        val filesCopied: Int,
        val bytesCopied: Long,
        val runtimeDirectory: File
    )

    suspend fun importFromTree(treeUri: Uri, environment: AndroidRuntimeEnvironment): ImportResult {
        require(environment.supportsInitialArm64Runtime) {
            "Runtime import currently supports only a 64-bit ARM64 Android app process."
        }
        val storage = SafGameStorage(resolver, treeUri)
        val runtimeRoot = File(privateRoot, RUNTIME_DIRECTORY)
        ensureRuntimeRoot(runtimeRoot)
        cleanupStaleStaging(runtimeRoot)

        val staging = File(runtimeRoot, STAGING_PREFIX + UUID.randomUUID().toString())
        check(staging.mkdir()) { "Could not create a private temporary runtime folder." }
        var installedDestination: File? = null

        try {
            currentCoroutineContext().ensureActive()
            val budget = RuntimeImportBudget()
            copyDirectoryContents(storage, storage.root(), staging, emptyList(), budget)

            currentCoroutineContext().ensureActive()
            val releaseFile = File(staging, "release")
            require(
                RuntimeImportPolicy.isContained(staging, releaseFile) &&
                    !RuntimePathPolicy.isSymbolicLink(releaseFile) &&
                    releaseFile.isFile &&
                    releaseFile.length() in 1..MAX_RELEASE_FILE_BYTES
            ) {
                "Select the Java runtime root folder; its release file must be directly inside it."
            }
            val releaseProperties = JavaRuntimeMetadata.parseReleaseFile(
                RuntimeImportPolicy.readBoundedText(releaseFile, MAX_RELEASE_FILE_BYTES)
            )
            val javaVersion = releaseProperties["JAVA_VERSION"]
                ?: error("The Java runtime release file does not declare JAVA_VERSION.")
            val javaMajor = JavaRuntimeMetadata.parseJavaMajor(javaVersion)
                ?: error("The Java runtime declares an unsupported Java version.")
            require(javaMajor >= MINIMUM_JAVA_MAJOR) {
                "Java $javaMajor is too old to be imported as a Minecraft runtime."
            }
            val declaredArchitecture = releaseProperties["OS_ARCH"]
                ?: error("The Java runtime release file does not declare OS_ARCH.")
            val architecture = JavaRuntimeMetadata.normalizeArchitecture(declaredArchitecture)
            require(
                JavaRuntimeMetadata.matchesInitialArm64Process(
                    architecture,
                    environment.is64BitProcess,
                    environment.supportedAbis
                )
            ) {
                "This runtime is not an ARM64 build matching the current Android app process."
            }

            val jvmLibrary = findJvmLibrary(staging)
                ?: error("The runtime is missing an AArch64 ELF64 libjvm.so file.")
            require(RuntimeBinaryPolicy.isArm64Elf64(jvmLibrary)) {
                "The runtime's libjvm.so is not a valid AArch64 ELF64 binary."
            }

            currentCoroutineContext().ensureActive()
            val runtimeId = "java-$javaMajor-arm64-" + UUID.randomUUID().toString().take(8)
            val destination = File(runtimeRoot, runtimeId)
            check(!destination.exists()) { "A runtime with the generated identifier already exists." }
            moveDirectory(staging, destination)
            installedDestination = destination
            currentCoroutineContext().ensureActive()
            require(RuntimeImportPolicy.isContained(runtimeRoot, destination)) {
                "The imported runtime resolved outside Flame Launcher's private runtime directory."
            }

            return ImportResult(
                runtimeId = runtimeId,
                javaVersion = javaVersion,
                javaMajorVersion = javaMajor,
                architecture = architecture,
                filesCopied = budget.files,
                bytesCopied = budget.bytes,
                runtimeDirectory = destination
            )
        } catch (error: Exception) {
            runCatching {
                if (staging.exists() && !staging.deleteRecursively()) {
                    throw IOException("Could not remove the temporary Java runtime import folder.")
                }
            }.exceptionOrNull()?.let(error::addSuppressed)

            installedDestination?.let { installed ->
                runCatching {
                    when {
                        RuntimePathPolicy.isSymbolicLink(installed) ->
                            Files.deleteIfExists(installed.toPath())
                        RuntimeImportPolicy.isContained(runtimeRoot, installed) -> {
                            if (installed.exists() && !installed.deleteRecursively()) {
                                throw IOException("Could not roll back the installed Java runtime folder.")
                            }
                        }
                        else -> throw IOException(
                            "Refusing to remove an installed runtime outside private runtime storage."
                        )
                    }
                }.exceptionOrNull()?.let(error::addSuppressed)
            }
            throw error
        }
    }

    /**
     * Removes only folders created by interrupted imports. The file walker does
     * not follow symbolic links, so a stale link cannot redirect cleanup.
     */
    private fun cleanupStaleStaging(runtimeRoot: File) {
        val children = runtimeRoot.listFiles()
            ?: throw IOException("Could not inspect private Java runtime storage.")
        for (child in children) {
            if (!child.name.startsWith(STAGING_PREFIX)) continue
            if (RuntimePathPolicy.isSymbolicLink(child)) {
                Files.deleteIfExists(child.toPath())
            } else if (child.isDirectory && RuntimeImportPolicy.isContained(runtimeRoot, child)) {
                deleteTreeWithoutFollowingLinks(child)
            } else if (child.isFile && RuntimeImportPolicy.isContained(runtimeRoot, child)) {
                Files.deleteIfExists(child.toPath())
            }
        }
    }

    private fun deleteTreeWithoutFollowingLinks(root: File) {
        val canonicalRoot = root.canonicalFile.toPath()
        Files.walkFileTree(root.toPath(), object : SimpleFileVisitor<java.nio.file.Path>() {
            override fun visitFile(file: java.nio.file.Path, attrs: BasicFileAttributes): FileVisitResult {
                require(file.toAbsolutePath().normalize().startsWith(canonicalRoot)) {
                    "A stale runtime path resolved outside its staging folder."
                }
                Files.deleteIfExists(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(
                directory: java.nio.file.Path,
                error: IOException?
            ): FileVisitResult {
                if (error != null) throw error
                require(directory.toAbsolutePath().normalize().startsWith(canonicalRoot)) {
                    "A stale runtime folder resolved outside its staging root."
                }
                Files.deleteIfExists(directory)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun ensureRuntimeRoot(runtimeRoot: File) {
        if (runtimeRoot.exists()) {
            require(!RuntimePathPolicy.isSymbolicLink(runtimeRoot) && runtimeRoot.isDirectory) {
                "The private Java runtime storage location is not a safe directory."
            }
        } else {
            require(runtimeRoot.mkdir()) { "Could not create private Java runtime storage." }
        }
        require(RuntimeImportPolicy.isContained(privateRoot, runtimeRoot)) {
            "The private Java runtime directory resolves outside app-private storage."
        }
    }

    private suspend fun copyDirectoryContents(
        storage: SafGameStorage,
        sourceDirectory: Uri,
        destinationDirectory: File,
        parentSegments: List<String>,
        budget: RuntimeImportBudget
    ) {
        currentCoroutineContext().ensureActive()
        val entries = storage.listChildren(sourceDirectory)
        val seenNames = HashSet<String>()
        for (entry in entries) {
            currentCoroutineContext().ensureActive()
            RuntimeImportPolicy.validateEntryName(entry.name)
            require(seenNames.add(entry.name)) {
                "The selected runtime folder contains duplicate document names."
            }
            val segments = parentSegments + entry.name
            RuntimeImportPolicy.validateRelativePath(segments)
            budget.registerEntry(entry.isDirectory)

            val target = File(destinationDirectory, entry.name)
            require(RuntimeImportPolicy.isContained(destinationDirectory, target)) {
                "A Java runtime document resolved outside its destination directory."
            }
            if (entry.isDirectory) {
                require(!target.exists() && target.mkdir()) {
                    "Could not safely create a Java runtime folder."
                }
                copyDirectoryContents(storage, entry.uri, target, segments, budget)
            } else {
                val declaredSize = entry.sizeBytes
                if (declaredSize != null) {
                    require(declaredSize in 0..RuntimeImportPolicy.MAX_FILE_BYTES) {
                        "A Java runtime file exceeds the import size limit."
                    }
                }
                require(!target.exists()) { "The runtime contains a duplicate file path." }
                copyFile(storage, entry.uri, target, declaredSize, budget)
            }
        }
    }

    private suspend fun copyFile(
        storage: SafGameStorage,
        source: Uri,
        destination: File,
        declaredSize: Long?,
        budget: RuntimeImportBudget
    ) {
        var copied = 0L
        storage.openInput(source).use { input ->
            FileOutputStream(destination, false).use { output ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    copied += count
                    require(copied <= RuntimeImportPolicy.MAX_FILE_BYTES) {
                        "A Java runtime file exceeds the import size limit."
                    }
                    budget.addBytes(count.toLong())
                    output.write(buffer, 0, count)
                }
                output.flush()
                output.fd.sync()
            }
        }
        if (declaredSize != null) {
            require(copied == declaredSize) {
                "The selected storage provider returned a file with an unexpected size."
            }
        }
    }

    private fun findJvmLibrary(home: File): File? {
        val candidates = listOf(
            File(home, "lib/server/libjvm.so"),
            File(home, "lib/aarch64/server/libjvm.so"),
            File(home, "lib/arm64/server/libjvm.so")
        )
        return candidates.firstOrNull {
            RuntimeImportPolicy.isContained(home, it) &&
                !RuntimePathPolicy.isSymbolicLink(it) &&
                it.isFile &&
                it.length() >= MINIMUM_ELF_HEADER_BYTES &&
                RuntimeBinaryPolicy.isArm64Elf64(it)
        }
    }

    private fun moveDirectory(source: File, destination: File) {
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            if (!source.renameTo(destination)) {
                throw IOException("Could not finish installing the verified Java runtime.")
            }
        }
    }

    private companion object {
        const val RUNTIME_DIRECTORY = "java-runtimes"
        const val STAGING_PREFIX = "~importing-"
        const val MINIMUM_JAVA_MAJOR = 8
        const val MAX_RELEASE_FILE_BYTES = 64L * 1024L
        const val MINIMUM_ELF_HEADER_BYTES = 20L
        const val COPY_BUFFER_BYTES = 64 * 1024
    }
}
