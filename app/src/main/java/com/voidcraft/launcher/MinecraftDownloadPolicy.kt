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
    url.protocol.equals("https", ignoreCase = true) &&
        url.host.equals("piston-meta.mojang.com", ignoreCase = true) &&
        hasNoEmbeddedCredentials(url) &&
        usesStandardHttpsPort(url)

/** Version JSON may come from either official Mojang metadata host. */
internal fun isApprovedMinecraftVersionMetadataUrl(url: URL): Boolean =
    url.protocol.equals("https", ignoreCase = true) &&
        url.host.lowercase() in approvedVersionMetadataHosts &&
        hasNoEmbeddedCredentials(url) &&
        usesStandardHttpsPort(url)

/** For pinned downloads, also require exact HTTPS host, no credentials, and the standard TLS port. */
internal fun isApprovedHttpsDownloadUrl(url: URL, allowedHosts: Set<String>): Boolean =
    url.protocol.equals("https", ignoreCase = true) &&
        url.host.lowercase() in allowedHosts &&
        hasNoEmbeddedCredentials(url) &&
        usesStandardHttpsPort(url)

/**
 * Resolves a Location header and validates it before the caller opens the destination connection.
 */
internal fun resolveTrustedDownloadRedirect(
    currentUrl: URL,
    location: String,
    allowedHosts: Set<String>
): URL {
    require(location.isNotBlank()) { "Download redirect has no destination" }
    val next = runCatching { URL(currentUrl, location) }
        .getOrElse { error("Download server returned an invalid redirect URL") }
    require(isApprovedHttpsDownloadUrl(next, allowedHosts)) {
        "Blocked a redirect outside the trusted HTTPS host allowlist"
    }
    return next
}

private fun usesStandardHttpsPort(url: URL): Boolean =
    url.port == -1 || url.port == 443

private fun hasNoEmbeddedCredentials(url: URL): Boolean =
    runCatching { url.toURI().rawUserInfo == null }.getOrDefault(false)
