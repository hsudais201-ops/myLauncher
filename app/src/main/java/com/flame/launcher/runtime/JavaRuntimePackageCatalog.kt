package com.flame.launcher.runtime

import java.net.URI
import org.json.JSONException
import org.json.JSONObject

/**
 * Validated runtime package metadata supplied by an independently trusted catalog.
 *
 * The catalog transport must itself be pinned to an application-controlled HTTPS
 * host (or authenticated by a signature) before calling parse(). A SHA-256 stored
 * beside a download URL is an integrity check, not an independent authenticity
 * guarantee. The returned package host is separately checked against the caller's
 * allowlist before it can be passed to JavaRuntimePackageDownloader.
 */
data class JavaRuntimePackageCatalog(
    val schemaVersion: Int,
    val packages: List<JavaRuntimePackageDescriptor>
) {
    fun find(id: String): JavaRuntimePackageDescriptor? =
        packages.firstOrNull { it.id == id }

    fun compatiblePackages(
        requiredMajorVersion: Int,
        deviceArchitecture: String
    ): List<JavaRuntimePackageDescriptor> {
        require(requiredMajorVersion in 1..100) { "Required Java major version is invalid." }
        val architecture = JavaRuntimeManager.normalizeArchitecture(deviceArchitecture)
            ?: return emptyList()
        return packages.filter {
            it.javaMajorVersion == requiredMajorVersion && it.architecture == architecture
        }
    }

    companion object {
        private const val MAX_CATALOG_BYTES = 2 * 1024 * 1024
        private const val MAX_PACKAGES = 256
        private const val MAX_PACKAGE_BYTES = 1024L * 1024 * 1024
        private val ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
        private val VERSION_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._+~-]{0,63}")
        private val SHA256_PATTERN = Regex("[a-fA-F0-9]{64}")
        private val HOST_PATTERN = Regex("[a-z0-9.-]{1,253}")

        fun parse(json: String, trustedPackageHosts: Set<String>): JavaRuntimePackageCatalog {
            require(json.toByteArray(Charsets.UTF_8).size in 1..MAX_CATALOG_BYTES) {
                "Java runtime catalog is empty or exceeds the size limit."
            }
            val hosts = trustedPackageHosts.map { it.lowercase() }.toSet()
            require(hosts.isNotEmpty() && hosts.all { HOST_PATTERN.matches(it) }) {
                "Java runtime catalog requires a valid package-host allowlist."
            }
            val root = try {
                JSONObject(json)
            } catch (failure: JSONException) {
                throw IllegalArgumentException("Java runtime catalog is not valid JSON.", failure)
            }
            val schema = root.optInt("schemaVersion", -1)
            require(schema == SUPPORTED_SCHEMA_VERSION) {
                "Java runtime catalog schema version is not supported."
            }
            val rawPackages = root.optJSONArray("packages")
                ?: throw IllegalArgumentException("Java runtime catalog is missing its packages list.")
            require(rawPackages.length() in 1..MAX_PACKAGES) {
                "Java runtime catalog has an invalid number of packages."
            }

            val ids = HashSet<String>()
            val packages = ArrayList<JavaRuntimePackageDescriptor>(rawPackages.length())
            for (index in 0 until rawPackages.length()) {
                val item = rawPackages.optJSONObject(index)
                    ?: throw IllegalArgumentException("Java runtime package $index is not an object.")
                val id = item.optString("id")
                val version = item.optString("version")
                val major = item.optInt("javaMajorVersion", -1)
                val architectureRaw = item.optString("architecture")
                val architecture = JavaRuntimeManager.normalizeArchitecture(architectureRaw)
                val url = item.optString("url")
                val sha256 = item.optString("sha256")
                val sizeBytes = item.optLong("sizeBytes", -1L)
                val archiveType = item.optString("archiveType")

                require(ID_PATTERN.matches(id)) { "Java runtime package $index has an invalid ID." }
                require(ids.add(id)) { "Java runtime catalog contains duplicate package ID $id." }
                require(VERSION_PATTERN.matches(version)) { "Java runtime package $id has an invalid version." }
                require(major in 1..100) { "Java runtime package $id has an invalid Java major version." }
                require(architecture != null) { "Java runtime package $id has an unsupported architecture." }
                require(isTrustedPackageUrl(url, hosts)) {
                    "Java runtime package $id has an untrusted download URL."
                }
                require(SHA256_PATTERN.matches(sha256)) {
                    "Java runtime package $id has an invalid SHA-256 digest."
                }
                require(sizeBytes in 1..MAX_PACKAGE_BYTES) {
                    "Java runtime package $id has an invalid archive size."
                }
                require(archiveType == "zip") {
                    "Java runtime package $id uses an unsupported archive type."
                }
                packages += JavaRuntimePackageDescriptor(
                    id = id,
                    version = version,
                    javaMajorVersion = major,
                    architecture = architecture!!,
                    downloadUrl = url,
                    sha256 = sha256.lowercase(),
                    sizeBytes = sizeBytes,
                    archiveType = archiveType
                )
            }
            return JavaRuntimePackageCatalog(schema, packages)
        }

        private fun isTrustedPackageUrl(value: String, hosts: Set<String>): Boolean = try {
            val uri = URI(value)
            uri.scheme == "https" &&
                uri.host?.lowercase() in hosts &&
                uri.userInfo == null &&
                uri.port in setOf(-1, 443)
        } catch (_: Exception) {
            false
        }

        const val SUPPORTED_SCHEMA_VERSION = 1
    }
}

data class JavaRuntimePackageDescriptor(
    val id: String,
    val version: String,
    val javaMajorVersion: Int,
    val architecture: String,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val archiveType: String
)
