package com.hyperlauncher.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class MinecraftLibraryResolverTest {
    @Test
    fun parsesLinuxAllowedMavenArtifact() {
        val json = """
            {
              "libraries": [
                {
                  "name": "org.example:demo:1.0",
                  "rules": [
                    {"action":"allow","os":{"name":"linux"}},
                    {"action":"disallow","os":{"name":"windows"}}
                  ],
                  "downloads": {
                    "artifact": {
                      "path":"org/example/demo/1.0/demo-1.0.jar",
                      "url":"https://libraries.minecraft.net/org/example/demo/1.0/demo-1.0.jar",
                      "sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                      "size":123
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val libraries = MinecraftLibraryResolver.parse("1.21.4", json)

        assertEquals(1, libraries.size)
        assertEquals("org.example:demo:1.0", libraries.single().name)
        assertEquals("libraries/org/example/demo/1.0/demo-1.0.jar", libraries.single().artifact.relativePath)
        assertEquals(123L, libraries.single().artifact.descriptor.size)
    }

    @Test
    fun allowsRuleSetsContainingOnlyNonMatchingDisallowRules() {
        val json = """
            {"libraries":[
              {"name":"org.example:demo:1","rules":[{"action":"disallow","os":{"name":"windows"}}],
               "downloads":{"artifact":{"path":"org/example/demo/1/demo.jar","url":"https://libraries.minecraft.net/demo.jar","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":10}}}
            ]}
        """.trimIndent()

        assertEquals(1, MinecraftLibraryResolver.parse("1.20.1", json).size)
    }

    @Test
    fun rejectsUnsafePathsAndUntrustedHosts() {
        val unsafePath = """{"libraries":[{"name":"x:y:1","downloads":{"artifact":{"path":"../../escape.jar","url":"https://libraries.minecraft.net/a.jar","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":10}}}]}"""
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftLibraryResolver.parse("1.20.1", unsafePath)
        }

        val unsafeHost = """{"libraries":[{"name":"x:y:1","downloads":{"artifact":{"path":"x/y.jar","url":"https://evil.example/a.jar","sha1":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","size":10}}}]}"""
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftLibraryResolver.parse("1.20.1", unsafeHost)
        }
    }
}
