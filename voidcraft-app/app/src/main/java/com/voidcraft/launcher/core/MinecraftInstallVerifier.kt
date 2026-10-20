package com.voidcraft.launcher.core

import java.io.File
import java.security.MessageDigest

enum class ArtifactVerificationState {
    VERIFIED,
    MISSING,
    NOT_A_FILE,
    SIZE_MISMATCH,
    CHECKSUM_MISMATCH,
    UNVERIFIABLE,
    INVALID_PLAN
}

data class ArtifactVerification(
    val artifact: MinecraftPlannedDownload,
    val state: ArtifactVerificationState,
    val actualBytes: Long? = null,
    val actualSha1: String? = null,
    val message: String,
    val cachedReceipt: FileDownloadReceipt? = null
)

data class MinecraftInstallVerificationReport(
    val versionId: String,
    val artifacts: List<ArtifactVerification>,
    val planWarnings: List<String>
) {
    val verifiedCount: Int
        get() = artifacts.count { it.state == ArtifactVerificationState.VERIFIED }

    val needsDownload: List<ArtifactVerification>
        get() = artifacts.filter { it.state != ArtifactVerificationState.VERIFIED }

    val isFullyVerified: Boolean
        get() = planWarnings.isEmpty() && artifacts.isNotEmpty() && needsDownload.isEmpty()
}

/**
 * Read-only scan of planned Minecraft files. It never creates, moves, or deletes
 * files. Only files matching a manifest-provided SHA-1 (and declared size, when
 * present) count as VERIFIED and may be reused by the install executor.
 */
class MinecraftInstallVerifier {
    fun inspect(plan: MinecraftInstallPlan, installRoot: File): MinecraftInstallVerificationReport {
        val paths = try {
            MinecraftInstallPathPolicy.validatePlan(plan)
        } catch (error: Exception) {
            val invalid = plan.downloads.map { artifact ->
                ArtifactVerification(
                    artifact = artifact,
                    state = ArtifactVerificationState.INVALID_PLAN,
                    message = error.message ?: "Install plan is invalid"
                )
            }
            return MinecraftInstallVerificationReport(plan.versionId, invalid, plan.warnings)
        }

        if (installRoot.exists() && !installRoot.isDirectory) {
            val invalid = plan.downloads.map { artifact ->
                ArtifactVerification(
                    artifact = artifact,
                    state = ArtifactVerificationState.INVALID_PLAN,
                    message = "Install root is not a directory"
                )
            }
            return MinecraftInstallVerificationReport(plan.versionId, invalid, plan.warnings)
        }

        val root = installRoot.canonicalFile
        val entries = plan.downloads.mapIndexed { index, artifact ->
            val file = try {
                MinecraftInstallPathPolicy.resolveInside(root, paths[index])
            } catch (error: Exception) {
                return@mapIndexed ArtifactVerification(
                    artifact = artifact,
                    state = ArtifactVerificationState.INVALID_PLAN,
                    message = error.message ?: "Destination path is unsafe"
                )
            }

            if (!file.exists()) {
                return@mapIndexed ArtifactVerification(
                    artifact = artifact,
                    state = ArtifactVerificationState.MISSING,
                    message = "File is missing"
                )
            }
            if (!file.isFile) {
                return@mapIndexed ArtifactVerification(
                    artifact = artifact,
                    state = ArtifactVerificationState.NOT_A_FILE,
                    message = "Destination is not a regular file"
                )
            }

            val size = file.length()
            if (artifact.expectedBytes != null && size != artifact.expectedBytes) {
                return@mapIndexed ArtifactVerification(
                    artifact = artifact,
                    state = ArtifactVerificationState.SIZE_MISMATCH,
                    actualBytes = size,
                    message = "File size differs from version metadata"
                )
            }

            val expectedSha1 = artifact.expectedSha1
            if (expectedSha1 == null) {
                return@mapIndexed ArtifactVerification(
                    artifact = artifact,
                    state = ArtifactVerificationState.UNVERIFIABLE,
                    actualBytes = size,
                    message = "No authoritative SHA-1 is available; do not trust the existing file as verified"
                )
            }

            val digests = try {
                hashFile(file)
            } catch (error: Exception) {
                return@mapIndexed ArtifactVerification(
                    artifact = artifact,
                    state = ArtifactVerificationState.INVALID_PLAN,
                    actualBytes = size,
                    message = "Could not verify file: " + (error.message ?: "I/O error")
                )
            }

            if (!digests.first.equals(expectedSha1, ignoreCase = true)) {
                ArtifactVerification(
                    artifact = artifact,
                    state = ArtifactVerificationState.CHECKSUM_MISMATCH,
                    actualBytes = size,
                    actualSha1 = digests.first,
                    message = "File SHA-1 differs from version metadata"
                )
            } else {
                ArtifactVerification(
                    artifact = artifact,
                    state = ArtifactVerificationState.VERIFIED,
                    actualBytes = size,
                    actualSha1 = digests.first,
                    message = "Checksum and declared size verified",
                    cachedReceipt = FileDownloadReceipt(
                        file = file,
                        bytesDownloaded = size,
                        sha1 = digests.first,
                        sha256 = digests.second,
                        attempts = 0,
                        wasCached = true
                    )
                )
            }
        }
        return MinecraftInstallVerificationReport(plan.versionId, entries, plan.warnings)
    }

    private fun hashFile(file: File): Pair<String, String> {
        val sha1 = MessageDigest.getInstance("SHA-1")
        val sha256 = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                sha1.update(buffer, 0, count)
                sha256.update(buffer, 0, count)
            }
        }
        return sha1.digest().toHex() to sha256.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") {
        "%02x".format(it.toInt() and 0xff)
    }
}
