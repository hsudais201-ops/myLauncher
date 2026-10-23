package com.voidcraft.launcher

/** Redirects must not carry Social Hub passwords or bearer tokens to another URL. */
internal fun mustRejectSocialApiRedirect(statusCode: Int): Boolean = statusCode in 300..399

private const val MAX_SOCIAL_API_RESPONSE_BYTES = 1024 * 1024
private const val MAX_SOCIAL_API_REQUEST_BYTES = 64 * 1024

/** The UI sends small JSON requests; bound them before opening the request body stream. */
internal fun isSocialApiRequestSizeAllowed(sizeBytes: Int): Boolean =
    sizeBytes in 0..MAX_SOCIAL_API_REQUEST_BYTES

/**
 * Reads a network response without trusting Content-Length. Supports unknown lengths but enforces
 * the limit on the streamed bytes as well, including for error responses.
 */
internal fun readBoundedSocialApiResponse(
    input: java.io.InputStream,
    declaredLength: Long,
    maxBytes: Int = MAX_SOCIAL_API_RESPONSE_BYTES
): String {
    require(maxBytes > 0) { "Response safety limit must be positive." }
    require(declaredLength >= -1L) { "Invalid Social Hub response length." }
    require(declaredLength == -1L || declaredLength <= maxBytes.toLong()) {
        "Social Hub response exceeds the 1 MiB safety limit."
    }

    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count == 0) continue
        total += count
        require(total <= maxBytes) { "Social Hub response exceeded the 1 MiB safety limit." }
        output.write(buffer, 0, count)
    }
    require(declaredLength < 0L || total.toLong() == declaredLength) {
        "Social Hub response length did not match its headers."
    }
    return output.toString(Charsets.UTF_8.name())
}
