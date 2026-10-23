package com.voidcraft.launcher

import java.net.URL
import org.junit.Assert.assertEquals
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

    @Test
    fun rejectsNonstandardPortsAndCredentialsForTrustedFileDownloads() {
        val hosts = setOf("libraries.minecraft.net", "resources.download.minecraft.net")
        assertTrue(isApprovedHttpsDownloadUrl(URL("https://libraries.minecraft.net/com/example.jar"), hosts))
        assertFalse(isApprovedHttpsDownloadUrl(URL("http://libraries.minecraft.net/com/example.jar"), hosts))
        assertFalse(isApprovedHttpsDownloadUrl(URL("https://attacker.example/com/example.jar"), hosts))
        assertFalse(isApprovedHttpsDownloadUrl(URL("https://user@libraries.minecraft.net/com/example.jar"), hosts))
        assertFalse(isApprovedHttpsDownloadUrl(URL("https://libraries.minecraft.net:8443/com/example.jar"), hosts))
    }

    @Test
    fun rejectsNonstandardPortsForOfficialMetadata() {
        assertFalse(
            isApprovedMinecraftVersionMetadataUrl(
                URL("https://piston-meta.mojang.com:8443/v1/version.json")
            )
        )
        assertFalse(
            isApprovedMinecraftVersionManifestUrl(
                URL("https://piston-meta.mojang.com:8443/mc/game/version_manifest_v2.json")
            )
        )
    }

    @Test
    fun resolvesRelativeRedirectsOnlyWithinApprovedHttpsHosts() {
        val hosts = setOf("libraries.minecraft.net", "release-assets.githubusercontent.com")
        val original = URL("https://libraries.minecraft.net/com/example.jar")
        assertEquals(
            URL("https://libraries.minecraft.net/com/next.jar"),
            resolveTrustedDownloadRedirect(original, "next.jar", hosts)
        )
        assertEquals(
            URL("https://release-assets.githubusercontent.com/download/file?token=sample"),
            resolveTrustedDownloadRedirect(
                original,
                "https://release-assets.githubusercontent.com/download/file?token=sample",
                hosts
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRedirectToPlainHttp() {
        resolveTrustedDownloadRedirect(
            URL("https://libraries.minecraft.net/example.jar"),
            "http://libraries.minecraft.net/redirected.jar",
            setOf("libraries.minecraft.net")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRedirectToUntrustedHost() {
        resolveTrustedDownloadRedirect(
            URL("https://libraries.minecraft.net/example.jar"),
            "https://attacker.example/redirected.jar",
            setOf("libraries.minecraft.net")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRedirectWithEmbeddedCredentialsOrCustomPort() {
        resolveTrustedDownloadRedirect(
            URL("https://libraries.minecraft.net/example.jar"),
            "https://user@libraries.minecraft.net:8443/redirected.jar",
            setOf("libraries.minecraft.net")
        )
    }
}
