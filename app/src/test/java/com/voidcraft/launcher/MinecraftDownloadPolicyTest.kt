package com.voidcraft.launcher

import java.net.URL
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftDownloadPolicyTest {
    @Test
    fun acceptsOnlyFortyHexadecimalSha1Characters() {
        assertTrue(isValidMinecraftSha1("0123456789abcdef0123456789abcdef01234567"))
        assertTrue(isValidMinecraftSha1("ABCDEF0123456789ABCDEF0123456789ABCDEF01"))
        assertFalse(isValidMinecraftSha1("0123456789abcdef"))
        assertFalse(isValidMinecraftSha1("0123456789abcdef0123456789abcdef0123456z"))
        assertFalse(isValidMinecraftSha1(" 0123456789abcdef0123456789abcdef01234567"))
    }

    @Test
    fun acceptsTheOfficialVersionManifestHostOnlyOverHttps() {
        assertTrue(
            isApprovedMinecraftVersionManifestUrl(
                URL("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
            )
        )
        assertFalse(
            isApprovedMinecraftVersionManifestUrl(
                URL("http://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
            )
        )
        assertFalse(
            isApprovedMinecraftVersionManifestUrl(
                URL("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json")
            )
        )
        assertFalse(
            isApprovedMinecraftVersionManifestUrl(
                URL("https://piston-meta.mojang.com.attacker.example/version_manifest_v2.json")
            )
        )
    }

    @Test
    fun metadataUrlsMustUseAnOfficialHostAndHttps() {
        assertTrue(
            isApprovedMinecraftVersionMetadataUrl(
                URL("https://piston-meta.mojang.com/v1/packages/example/version.json")
            )
        )
        assertTrue(
            isApprovedMinecraftVersionMetadataUrl(
                URL("https://launchermeta.mojang.com/v1/packages/example/version.json")
            )
        )
        assertFalse(
            isApprovedMinecraftVersionMetadataUrl(
                URL("http://piston-meta.mojang.com/v1/packages/example/version.json")
            )
        )
        assertFalse(
            isApprovedMinecraftVersionMetadataUrl(
                URL("https://piston-meta.mojang.com.attacker.example/version.json")
            )
        )
    }

    @Test
    fun rejectsEmbeddedCredentialsInOfficialLookingUrls() {
        assertFalse(
            isApprovedMinecraftVersionManifestUrl(
                URL("https://attacker@piston-meta.mojang.com/mc/game/version_manifest_v2.json")
            )
        )
        assertFalse(
            isApprovedMinecraftVersionMetadataUrl(
                URL("https://attacker@launchermeta.mojang.com/v1/version.json")
            )
        )
    }
}
