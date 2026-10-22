package com.voidcraft.launcher

/** Redirects must not carry Social Hub passwords or bearer tokens to another URL. */
internal fun mustRejectSocialApiRedirect(statusCode: Int): Boolean = statusCode in 300..399
