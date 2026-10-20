package com.voidcraft.launcher.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftLaunchArgumentInterpolatorTest {
    @Test
    fun expandsPlaceholdersWithoutSplittingValuesContainingSpaces() {
        val args = listOf(
            "--username",
            "${auth_player_name}",
            "-Djava.library.path=${natives_directory}",
            "${game_directory}",
            "--version",
            "${version_name}"
        )
        val nativePath = "/data/user/0/com.voidcraft.launcher/files/native libraries"
        val gamePath = "/storage/emulated/0/My Minecraft/instance"
        val expanded = MinecraftLaunchArgumentInterpolator.expand(
            args,
            mapOf(
                "auth_player_name" to "Void Player",
                "natives_directory" to nativePath,
                "game_directory" to gamePath,
                "version_name" to "1.21.4"
            )
        )

        assertEquals(6, expanded.size)
        assertEquals("Void Player", expanded[1])
        assertEquals("-Djava.library.path=" + nativePath, expanded[2])
        assertEquals(gamePath, expanded[3])
        assertEquals("1.21.4", expanded[5])
    }

    @Test
    fun unresolvedPlaceholderFailsClosedWithoutEchoingArgumentSecrets() {
        val arguments = listOf("--accessToken=${missing_token}")
        val error = assertThrows(UnresolvedLaunchPlaceholderException::class.java) {
            MinecraftLaunchArgumentInterpolator.expand(
                arguments,
                mapOf("auth_access_token" to "example-secret-token")
            )
        }

        assertEquals("missing_token", error.placeholder)
        assertFalse(error.message.orEmpty().contains("example-secret-token"))
    }

    @Test
    fun invalidPlaceholderNameAndNulValuesAreRejected() {
        assertThrows(InvalidLaunchPlaceholderException::class.java) {
            MinecraftLaunchArgumentInterpolator.expand(listOf("--name=${bad name}"), emptyMap())
        }
        assertThrows(IllegalArgumentException::class.java) {
            MinecraftLaunchArgumentInterpolator.expand(listOf("${token}"), mapOf("token" to "bad\u0000token"))
        }
    }

    @Test
    fun resolverFiltersByPlatformBeforeExpandingAndPreservesOrder() {
        val values = listOf(
            MinecraftArgumentValue(listOf("--username", "${username}")),
            MinecraftArgumentValue(
                listOf("-Dplatform=${platform}"),
                listOf(MinecraftLibraryRule("allow", "linux", null, null, emptyMap()))
            ),
            MinecraftArgumentValue(
                listOf("--mac-only"),
                listOf(MinecraftLibraryRule("allow", "osx", null, null, emptyMap()))
            ),
            MinecraftArgumentValue(listOf("--last"))
        )

        val resolved = MinecraftLaunchArgumentResolver().resolve(
            values = values,
            platform = MinecraftPlatformContext("linux", "x86_64"),
            placeholders = mapOf("username" to "Player", "platform" to "linux")
        )

        assertEquals(listOf("--username", "Player", "-Dplatform=linux", "--last"), resolved)
        assertTrue(resolved.none { it == "--mac-only" })
    }

    @Test
    fun placeholderValuesAreNotRecursivelyExpanded() {
        val result = MinecraftLaunchArgumentInterpolator.expand(
            listOf("--value=${outer}"),
            mapOf("outer" to "${inner}", "inner" to "expanded")
        )

        assertEquals(listOf("--value=${inner}"), result)
    }
}
