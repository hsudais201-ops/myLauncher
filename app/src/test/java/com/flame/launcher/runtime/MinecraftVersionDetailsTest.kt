package com.flame.launcher.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MinecraftVersionDetailsTest {
    private val clientSha = "0123456789abcdef0123456789abcdef01234567"
    private val assetSha = "abcdef0123456789abcdef0123456789abcdef01"
    private val librarySha = "fedcba9876543210fedcba9876543210fedcba98"

    private fun details(
        id: String = "1.21",
        clientUrl: String = "https://piston-data.mojang.com/v1/objects/$clientSha/client.jar",
        clientDigest: String = clientSha
    ) = """
        {
          "id": "$id",
          "mainClass": "net.minecraft.client.main.Main",
          "assets": "17",
          "assetIndex": {
            "id": "17",
            "url": "https://piston-meta.mojang.com/v1/packages/$assetSha/17.json",
            "sha1": "$assetSha",
            "size": 1234
          },
          "downloads": {
            "client": {
              "url": "$clientUrl",
              "sha1": "$clientDigest",
              "size": 987654
            }
          },
          "javaVersion": {"component": "java-runtime-dummy", "majorVersion": 21},
          "libraries": [
            {
              "name": "org.example:sample:1.0",
              "downloads": {
                "artifact": {
                  "path": "org/example/sample/1.0/sample-1.0.jar",
                  "url": "https://libraries.minecraft.net/org/example/sample/1.0/sample-1.0.jar",
                  "sha1": "$librarySha",
                  "size": 456
                },
                "classifiers": {
                  "natives-linux-64": {
                    "path": "org/example/sample/1.0/sample-1.0-natives-linux.jar",
                    "url": "https://libraries.minecraft.net/org/example/sample/1.0/sample-1.0-natives-linux.jar",
                    "sha1": "$librarySha",
                    "size": 789
                  }
                }
              },
              "rules": [
                {"action": "disallow", "os": {"name": "windows"}},
                {"action": "allow", "os": {"name": "linux", "arch": "aarch64"}, "features": {"is_demo_user": false}}
              ],
              "natives": {"linux": "natives-linux-64"},
              "extract": {"exclude": ["META-INF/", "META-INF/versions/"]}
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parsesClientAssetLibraryAndNativeDescriptors() {
        val parsed = MinecraftVersionDetails.parse(details(), "1.21")
        assertEquals("1.21", parsed.id)
        assertEquals("net.minecraft.client.main.Main", parsed.mainClass)
        assertEquals("17", parsed.assets)
        assertEquals(21, parsed.javaMajorVersion)
        assertEquals(clientSha, parsed.client.sha1)
        assertEquals(assetSha, parsed.assetIndex.sha1)
        assertEquals(1, parsed.libraries.size)
        assertEquals("org.example:sample:1.0", parsed.libraries.single().coordinate)
        assertEquals(librarySha, parsed.libraries.single().artifact?.sha1)
        assertEquals(librarySha, parsed.libraries.single().classifiers["natives-linux-64"]?.sha1)
        assertEquals(2, parsed.libraries.single().rules.size)
        assertEquals("linux", parsed.libraries.single().rules.last().osName)
        assertEquals(false, parsed.libraries.single().rules.last().features["is_demo_user"])
        assertEquals("natives-linux-64", parsed.libraries.single().natives["linux"])
        assertEquals(listOf("META-INF", "META-INF/versions"), parsed.libraries.single().extractExcludes)
    }

    @Test
    fun rejectsVersionDetailsThatExceedParserSizeLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftVersionDetails.parse(" ".repeat(8 * 1024 * 1024 + 1), "1.21")
        }
    }

    @Test
    fun acceptsMissingJavaRequirement() {
        val json = details().replace(
            """"javaVersion": {"component": "java-runtime-dummy", "majorVersion": 21},""",
            ""
        )
        val parsed = MinecraftVersionDetails.parse(json, "1.21")
        assertNull(parsed.javaMajorVersion)
        assertEquals(1, parsed.libraries.size)
    }

    @Test
    fun rejectsUnsafeLibraryPaths() {
        val unsafe = details().replace(
            "org/example/sample/1.0/sample-1.0.jar",
            "../outside.jar"
        )
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftVersionDetails.parse(unsafe, "1.21")
        }
    }

    @Test
    fun acceptsSingleCharacterMainClassAndRejectsMalformedSegments() {
        val singleCharacter = details().replace(
            "net.minecraft.client.main.Main",
            "A"
        )
        assertEquals("A", MinecraftVersionDetails.parse(singleCharacter, "1.21").mainClass)

        listOf("net.minecraft..Main", "net.minecraft.1Main", "net.minecraft.Main.").forEach { invalid ->
            val malformed = details().replace(
                "net.minecraft.client.main.Main",
                invalid
            )
            assertThrows(
                "Expected rejection for $invalid",
                IllegalArgumentException::class.java
            ) {
                MinecraftVersionDetails.parse(malformed, "1.21")
            }
        }
    }

    @Test
    fun rejectsWrongVersionIdAndInvalidMainClass() {
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftVersionDetails.parse(details(), "1.20.1")
        }
        val invalid = details().replace(
            "net.minecraft.client.main.Main",
            "bad main class"
        )
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftVersionDetails.parse(invalid, "1.21")
        }
    }

    @Test
    fun rejectsUntrustedClientHostAndInvalidHashesOrSizes() {
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftVersionDetails.parse(
                details(clientUrl = "https://attacker.example/client.jar"),
                "1.21"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftVersionDetails.parse(
                details(clientDigest = "invalid"),
                "1.21"
            )
        }
        val invalidSize = details().replace("987654", "0")
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftVersionDetails.parse(invalidSize, "1.21")
        }
    }
}
