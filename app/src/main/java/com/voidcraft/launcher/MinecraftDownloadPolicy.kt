package com.voidcraft.launcher

import java.net.URL

private val approvedVersionMetadataHosts = setOf(
    "piston-meta.mojang.com",
    "launchermeta.mojang.com"
)

/** SHA-1 is used here only to verify files against Mojang's published metadata. */
internal fun isValidMinecraftSha1(value: String): Boolean =
    value.matches(Regex("^[0-9a-fA-F]{40}$"))

/** The version manifest has a single expected HTTPS host. */
internal fun isApprovedMinecraftVersionManifestUrl(url: URL): Boolean =
    url.protocol == "https" &&
        url.host.equals("piston-meta.mojang.com", ignoreCase = true) &&
        hasNoEmbeddedCredentials(url)

/** Version JSON may come from either official Mojang metadata host. */
internal fun isApprovedMinecraftVersionMetadataUrl(url: URL): Boolean =
    url.protocol == "https" &&
        url.host.lowercase() in approvedVersionMetadataHosts &&
        hasNoEmbeddedCredentials(url)

private fun hasNoEmbeddedCredentials(url: URL): Boolean =
    runCatching { url.toURI().rawUserInfo == null }.getOrDefault(false)
