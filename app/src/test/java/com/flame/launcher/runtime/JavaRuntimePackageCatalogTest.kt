package com.flame.launcher.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JavaRuntimePackageCatalogTest {
    private val digest = "0123456789abcdef".repeat(4)

    private fun catalog(
        id: String = "temurin-17-arm64",
        url: String = "https://runtime.example/java-17.zip",
        sha256: String = digest,
        major: Int = 17,
        architecture: String = "aarch64",
        sizeBytes: Long = 1024,
        archiveType: String = "zip"
    ) = """
        {
          "schemaVersion": 1,
          "packages": [{
            "id": "$id",
            "version": "17.0.12+7",
            "javaMajorVersion": $major,
            "architecture": "$architecture",
            "url": "$url",
            "sha256": "$sha256",
            "sizeBytes": $sizeBytes,
            "archiveType": "$archiveType"
          }]
        }
    """.trimIndent()

    @Test
    fun parsesPackageAndSelectsOnlyMatchingJavaMajorAndArchitecture() {
        val parsed = JavaRuntimePackageCatalog.parse(catalog(), setOf("runtime.example"))
        val item = parsed.find("temurin-17-arm64")
        assertEquals(1, parsed.schemaVersion)
        assertEquals("17.0.12+7", item?.version)
        assertEquals("arm64", item?.architecture)
        assertEquals(digest, item?.sha256)
        assertEquals(1, parsed.compatiblePackages(17, "arm64-v8a").size)
        assertTrue(parsed.compatiblePackages(21, "arm64").isEmpty())
        assertTrue(parsed.compatiblePackages(17, "x86_64").isEmpty())
        assertNull(parsed.find("missing"))
    }

    @Test
    fun rejectsMalformedJsonAndUnsupportedSchema() {
        assertThrows(IllegalArgumentException::class.java) {
            JavaRuntimePackageCatalog.parse("{bad", setOf("runtime.example"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            JavaRuntimePackageCatalog.parse(
                """{"schemaVersion":2,"packages":[]}""",
                setOf("runtime.example")
            )
        }
    }

    @Test
    fun rejectsUntrustedHostsHttpAndCredentials() {
        listOf(
            "https://attacker.example/java.zip",
            "http://runtime.example/java.zip",
            "https://user@runtime.example/java.zip"
        ).forEach { url ->
            assertThrows(IllegalArgumentException::class.java) {
                JavaRuntimePackageCatalog.parse(catalog(url = url), setOf("runtime.example"))
            }
        }
    }

    @Test
    fun rejectsInvalidDigestSizeArchitectureAndArchiveType() {
        val invalidCatalogs = listOf(
            catalog(sha256 = "xyz"),
            catalog(sizeBytes = 0),
            catalog(sizeBytes = 512L * 1024 * 1024 + 1),
            catalog(architecture = "mips"),
            catalog(archiveType = "tar.gz")
        )
        invalidCatalogs.forEach { json ->
            assertThrows(IllegalArgumentException::class.java) {
                JavaRuntimePackageCatalog.parse(json, setOf("runtime.example"))
            }
        }
    }

    @Test
    fun rejectsDuplicateIdsAndMissingHostAllowlist() {
        val duplicate = """
            {
              "schemaVersion": 1,
              "packages": [
                {
                  "id":"same","version":"17.0.12","javaMajorVersion":17,
                  "architecture":"aarch64","url":"https://runtime.example/a.zip",
                  "sha256":"$digest","sizeBytes":100,"archiveType":"zip"
                },
                {
                  "id":"same","version":"17.0.13","javaMajorVersion":17,
                  "architecture":"aarch64","url":"https://runtime.example/b.zip",
                  "sha256":"$digest","sizeBytes":100,"archiveType":"zip"
                }
              ]
            }
        """.trimIndent()
        assertThrows(IllegalArgumentException::class.java) {
            JavaRuntimePackageCatalog.parse(duplicate, setOf("runtime.example"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            JavaRuntimePackageCatalog.parse(catalog(), emptySet())
        }
    }
}
