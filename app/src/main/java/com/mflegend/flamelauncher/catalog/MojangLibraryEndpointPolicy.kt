package com.mflegend.flamelauncher.catalog

import java.net.URL

/** Allows direct HTTPS artifact URLs from the approved Maven repository hosts. */
internal object MojangLibraryEndpointPolicy {
    fun isApproved(url: URL): Boolean =
        url.host.lowercase() in MAVEN_HOSTS && MojangHttpsEndpointPolicy.isApproved(url)

    private val MAVEN_HOSTS = setOf(
        "libraries.minecraft.net",
        "repo.maven.apache.org",
        "maven.fabricmc.net",
        "maven.minecraftforge.net",
        "maven.neoforged.net",
        "maven.quiltmc.org"
    )
}
