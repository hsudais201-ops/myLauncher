package com.mflegend.flamelauncher.catalog

import java.net.URL

/** Allows only direct HTTPS artifact URLs from explicitly approved Maven repositories. */
internal object MojangLibraryEndpointPolicy {
    private val allowedHosts = setOf(
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
