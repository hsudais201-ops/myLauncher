package com.mflegend.flamelauncher.catalog

import java.net.URL

/** Restricts version-manifest metadata requests to the official Mojang HTTPS origin. */
internal object MojangMetadataEndpointPolicy {
    fun isApproved(url: URL): Boolean =
        url.host.equals("piston-meta.mojang.com", ignoreCase = true) &&
            MojangHttpsEndpointPolicy.isApproved(url)
}
