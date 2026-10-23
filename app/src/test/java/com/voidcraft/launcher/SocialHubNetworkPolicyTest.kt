package com.voidcraft.launcher

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialHubNetworkPolicyTest {
    @Test
    fun rejectsAllRedirectResponsesBeforeReadingOrFollowingThem() {
        listOf(300, 301, 302, 303, 307, 308, 399).forEach {
            assertTrue("Expected redirect status $it to be rejected", mustRejectSocialApiRedirect(it))
        }
    }

    @Test
    fun permitsSuccessAndOrdinaryErrorStatusesToUseNormalResponseHandling() {
        listOf(200, 201, 204, 400, 401, 403, 404, 429, 500, 503).forEach {
            assertFalse("Expected status $it not to be treated as a redirect", mustRejectSocialApiRedirect(it))
        }
    }

    @Test
    fun boundsSocialHubRequestBodies() {
        assertTrue(isSocialApiRequestSizeAllowed(0))
        assertTrue(isSocialApiRequestSizeAllowed(64 * 1024))
        assertFalse(isSocialApiRequestSizeAllowed(-1))
        assertFalse(isSocialApiRequestSizeAllowed(64 * 1024 + 1))
    }

    @Test
    fun readsKnownAndUnknownLengthSocialResponses() {
        val body = """{"status":"ok"}""".toByteArray(StandardCharsets.UTF_8)
        assertEquals(
            """{"status":"ok"}""",
            readBoundedSocialApiResponse(ByteArrayInputStream(body), body.size.toLong())
        )
        assertEquals(
            """{"status":"ok"}""",
            readBoundedSocialApiResponse(ByteArrayInputStream(body), -1L)
        )
        assertEquals("", readBoundedSocialApiResponse(ByteArrayInputStream(byteArrayOf()), 0L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDeclaredOversizedSocialResponseBeforeReading() {
        readBoundedSocialApiResponse(ByteArrayInputStream(byteArrayOf()), 1024L * 1024L + 1L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsChunkedSocialResponseThatExceedsTheLimit() {
        readBoundedSocialApiResponse(ByteArrayInputStream(ByteArray(1024 * 1024 + 1)), -1L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSocialResponseLengthMismatch() {
        val body = "short".toByteArray(StandardCharsets.UTF_8)
        readBoundedSocialApiResponse(ByteArrayInputStream(body), body.size.toLong() + 1L)
    }
}
