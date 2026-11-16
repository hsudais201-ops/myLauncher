package com.mflegend.flamelauncher.catalog

import java.net.URL

/** Restricts version-manifest metadata requests to the official Mojang HTTPS origin. */
internal object MojangMetadataEndpointPolicy {
    fun isApproved(url: URL): Boolean =
        url.protocol.equals("https", ignoreCase = true) &&
            url.host.equals("piston-meta.mojang.com", ignoreCase = true) &&
            (url.port == -1 || url.port == 443) &&
            url.userInfo == null &&
            url.query == null &&
            url.ref == null
}
