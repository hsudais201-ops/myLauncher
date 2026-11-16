package com.mflegend.flamelauncher.catalog

import java.net.URL
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MojangHttpsEndpointPolicyTest {
    @Test fun acceptsApprovedOfficialAndMavenHosts() {
        listOf(
            "https://piston-meta.mojang.com/metadata.json",
            "https://launcher.mojang.com/client.jar",
            "https://piston-data.mojang.com/client.jar",
            "https://resources.download.minecraft.net/ab/hash",
            "https://libraries.minecraft.net/library.jar",
            "https://repo.maven.apache.org/maven2/library.jar",
            "https://maven.fabricmc.net/library.jar",
            "https://maven.minecraftforge.net/library.jar",
            "https://maven.neoforged.net/library.jar",
            "https://maven.quiltmc.org/library.jar",
            "https://PISTON-META.MOJANG.COM:443/metadata.json"
        ).forEach { assertTrue(it, MojangHttpsEndpointPolicy.isApproved(URL(it))) }
    }

    @Test fun rejectsUntrustedOriginsAndNonHttpsSchemes() {
        listOf(
            "http://piston-meta.mojang.com/file",
            "https://piston-meta.mojang.com.evil.example/file",
            "https://evil.example/piston-meta.mojang.com/file",
            "https://127.0.0.1/file",
            "ftp://libraries.minecraft.net/file"
        ).forEach { assertFalse(it, MojangHttpsEndpointPolicy.isApproved(URL(it))) }
    }

    @Test fun rejectsCredentialsUnexpectedPortsQueriesAndFragments() {
        listOf(
            "https://user@libraries.minecraft.net/file",
            "https://libraries.minecraft.net:8443/file",
            "https://libraries.minecraft.net/file?redirect=evil",
            "https://libraries.minecraft.net/file#fragment"
        ).forEach { assertFalse(it, MojangHttpsEndpointPolicy.isApproved(URL(it))) }
    }
}
