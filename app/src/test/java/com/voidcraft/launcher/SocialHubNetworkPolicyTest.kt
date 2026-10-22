package com.voidcraft.launcher

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
}
