package com.mflegend.flamelauncher.catalog

import java.net.URL

/** Validates direct HTTPS downloads against the launcher's explicit Mojang/Maven host allowlist. */
internal object MojangHttpsEndpointPolicy {
    private val allowedHosts = setOf(
        "piston-meta.mojang.com",
        "launcher.mojang.com",
        "piston-data.mojang.com",
        "resources.download.minecraft.net",
        "libraries.minecraft.net",
        "repo.maven.apache.org",
        "maven.fabricmc.net",
        "maven.minecraftforge.net",
        "maven.neoforged.net",
        "maven.quiltmc.org"
    )

    fun isApproved(url: URL): Boolean =
        url.protocol.equals("https", ignoreCase = true) &&
            url.host.lowercase() in allowedHosts &&
            (url.port == -1 || url.port == 443) &&
            url.userInfo == null &&
            url.query == null &&
            url.ref == null &&
            url.path.startsWith("/") &&
            url.path.isNotBlank()
}
