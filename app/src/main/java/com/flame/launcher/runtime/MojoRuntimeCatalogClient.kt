package com.flame.launcher.runtime

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.PublicKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64

/**
 * Reader for the signed split-runtime manifest published by MojoLauncher.
 *
 * The manifest is not trusted as an authority for executable bytes: it only
 * supplies signatures. Every downloaded component must pass RSA/SHA-256
 * verification against the public certificate bundled with Flame Launcher.
 */
data class MojoRuntimePackageDescriptor(
    val javaMajorVersion: Int,
    val architecture: String,
    val component: String,
    val universalDownloadUrl: String,
    val universalSignature: ByteArray,
    val downloadUrl: String,
    val signature: ByteArray
)

class MojoRuntimeSignatureBundle private constructor(
    private val signatures: Map<String, ByteArray>
) {
    fun signatureFor(component: String): ByteArray? =
        signatures[component]?.clone()

    fun verify(component: String, input: InputStream, publicKey: PublicKey): Boolean {
        val expected = signatureFor(component) ?: return false
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(publicKey)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            verifier.update(buffer, 0, count)
        }
        return verifier.verify(expected)
    }

    companion object {
        private const val MAX_MANIFEST_BYTES = 32 * 1024
        private const val RSA_4096_SIGNATURE_BYTES = 512
        private val COMPONENT_PATTERN = Regex("(universal|bin-(arm|arm64|x86|x86_64))\\.tar\\.xz")

        fun parse(text: String): MojoRuntimeSignatureBundle {
            require(text.toByteArray(Charsets.UTF_8).size in 1..MAX_MANIFEST_BYTES) {
                "Runtime signature manifest is empty or too large."
            }
            val parsed = LinkedHashMap<String, ByteArray>()
            text.lineSequence().forEachIndexed { index, rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) return@forEachIndexed
                val separator = line.indexOf(':')
                require(separator > 0 && separator == line.lastIndexOf(':')) {
                    "Runtime signature manifest line ${index + 1} is malformed."
                }
                val component = line.substring(0, separator)
                require(COMPONENT_PATTERN.matches(component)) {
                    "Runtime signature manifest contains an unsupported component."
                }
                require(!parsed.containsKey(component)) {
                    "Runtime signature manifest contains a duplicate component."
                }
                val signature = try {
                    Base64.getDecoder().decode(line.substring(separator + 1))
                } catch (failure: IllegalArgumentException) {
                    throw IllegalArgumentException(
                        "Runtime signature manifest contains invalid Base64.",
                        failure
                    )
                }
                require(signature.size == RSA_4096_SIGNATURE_BYTES) {
                    "Runtime component signature is not an RSA-4096 signature."
                }
                parsed[component] = signature
            }
            require("universal.tar.xz" in parsed) {
                "Runtime signature manifest is missing the universal component."
            }
            require(parsed.size >= 2) {
                "Runtime signature manifest is missing architecture-specific components."
            }
            return MojoRuntimeSignatureBundle(parsed)
        }
    }
}

/**
 * Fixed-source adapter. The source is deliberately not configurable by remote
 * catalog data: the host and component paths are compiled into the application.
 *
 * Java 17 is published as jre-new, Java 21 as jre-21, and Java 25 as jre-25.
 */
class MojoRuntimeCatalogClient(
    private val connectionFactory: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    },
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 20_000
) {
    init {
        require(connectTimeoutMs in 1..60_000)
        require(readTimeoutMs in 1..60_000)
    }

    fun load(javaMajorVersion: Int, deviceArchitecture: String): MojoRuntimePackageDescriptor {
        val architecture = JavaRuntimeManager.normalizeArchitecture(deviceArchitecture)
            ?: throw IllegalArgumentException("Device architecture is unsupported.")
        val runtimePath = when (javaMajorVersion) {
            17 -> "jre-new"
            21 -> "jre-21"
            25 -> "jre-25"
            else -> throw IllegalArgumentException("This runtime source does not publish that Java major.")
        }
        val componentArchitecture = when (architecture) {
            "arm" -> "arm"
            "arm64" -> "arm64"
            "x86" -> "x86"
            "x86_64" -> "x86_64"
            else -> throw IllegalArgumentException("Device architecture is unsupported.")
        }
        val manifestUrl = URL("$BASE_URL/components/$runtimePath/version")
        val manifest = downloadManifest(manifestUrl)
        val bundle = MojoRuntimeSignatureBundle.parse(manifest)
        val component = "bin-$componentArchitecture.tar.xz"
        val universalSignature = bundle.signatureFor("universal.tar.xz")
            ?: throw IllegalArgumentException("The upstream runtime is missing its universal component signature.")
        val signature = bundle.signatureFor(component)
            ?: throw IllegalArgumentException(
                "The upstream runtime does not publish Java $javaMajorVersion for $architecture."
            )
        return MojoRuntimePackageDescriptor(
            javaMajorVersion = javaMajorVersion,
            architecture = architecture,
            component = component,
            universalDownloadUrl = "$BASE_URL/components/$runtimePath/universal.tar.xz",
            universalSignature = universalSignature,
            downloadUrl = "$BASE_URL/components/$runtimePath/$component",
            signature = signature
        )
    }

    private fun downloadManifest(address: URL): String {
        require(address.protocol == "https" && address.host == TRUSTED_HOST) {
            "Runtime manifest URL is not trusted."
        }
        val connection = connectionFactory(address)
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "text/plain")
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Runtime provider returned HTTP ${connection.responseCode}."
            }
            val declaredLength = connection.contentLengthLong
            require(declaredLength == -1L || declaredLength in 1..MAX_MANIFEST_BYTES.toLong()) {
                "Runtime signature manifest exceeds the size limit."
            }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(count > 0 && output.size() <= MAX_MANIFEST_BYTES - count) {
                        "Runtime signature manifest exceeds the size limit."
                    }
                    output.write(buffer, 0, count)
                }
            }
            return output.toString(Charsets.UTF_8.name())
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TRUSTED_HOST = "mojolauncher.github.io"
        private const val BASE_URL = "https://mojolauncher.github.io/jre-download"
        private const val MAX_MANIFEST_BYTES = 32 * 1024
    }
}

/** Loads the pinned upstream X.509 certificate and verifies signed components. */
object MojoRuntimeCertificate {
    fun readPublicKey(pemInput: InputStream): PublicKey {
        val certificate = pemInput.use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }
        certificate.checkValidity()
        return certificate.publicKey
    }
}
