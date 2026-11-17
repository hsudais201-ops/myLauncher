package com.mflegend.flamelauncher.catalog

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MojangLibraryRulePolicyTest {
    private val android = MojangLibraryRuleContext(
        platformName = "android",
        osVersion = "14.2",
        osArchitecture = "aarch64"
    )

    @Test
    fun allowsLibrariesWithoutRules() {
        assertTrue(MojangLibraryRulePolicy.isAllowed(JSONObject(), android))
    }

    @Test
    fun allowsExplicitGlobalRuleOnAndroid() {
        val library = library("""[{"action":"allow"}]""")
        assertTrue(MojangLibraryRulePolicy.isAllowed(library, android))
    }

    @Test
    fun doesNotTreatAndroidAsDesktopLinuxOrWindows() {
        val linux = library("""[{"action":"allow","os":{"name":"linux"}}]""")
        val windows = library("""[{"action":"allow","os":{"name":"windows"}}]""")
        assertFalse(MojangLibraryRulePolicy.isAllowed(linux, android))
        assertFalse(MojangLibraryRulePolicy.isAllowed(windows, android))
    }

    @Test
    fun matchesOnlyExplicitAndroidOsRule() {
        val androidRule = library("""[{"action":"allow","os":{"name":"android"}}]""")
        assertTrue(MojangLibraryRulePolicy.isAllowed(androidRule, android))
    }

    @Test
    fun evaluatesArchitectureAndVersionExpressions() {
        val matching = library(
            """[{"action":"allow","os":{"name":"android","arch":"aarch64|arm64","version":"1[34]\\..*"}}]"""
        )
        val notMatching = library(
            """[{"action":"allow","os":{"name":"android","arch":"x86_64"}}]"""
        )
        assertTrue(MojangLibraryRulePolicy.isAllowed(matching, android))
        assertFalse(MojangLibraryRulePolicy.isAllowed(notMatching, android))
    }

    @Test
    fun evaluatesRulesInOrderWithLastMatchingRuleWinning() {
        val allowedThenDenied = library(
            """[{"action":"allow"},{"action":"disallow","features":{"is_demo_user":true}}]"""
        )
        val deniedThenAllowed = library(
            """[{"action":"disallow","os":{"name":"windows"}},{"action":"allow","os":{"name":"android"}}]"""
        )
        val contextWithFeature = android.copy(features = mapOf("is_demo_user" to true))
        assertFalse(MojangLibraryRulePolicy.isAllowed(allowedThenDenied, contextWithFeature))
        assertTrue(MojangLibraryRulePolicy.isAllowed(deniedThenAllowed, android))
    }

    @Test
    fun unknownFeatureDoesNotGetGuessed() {
        val featureRule = library(
            """[{"action":"allow","features":{"is_demo_user":false}}]"""
        )
        assertFalse(MojangLibraryRulePolicy.isAllowed(featureRule, android))
    }

    @Test
    fun rejectsMalformedRulesActionsAndRegexes() {
        val malformed = JSONObject().put("rules", "not-an-array")
        val unknownAction = library("""[{"action":"maybe"}]""")
        val invalidRegex = library("""[{"action":"allow","os":{"arch":"["}}]""")
        val oversizedRegex = library(
            """[{"action":"allow","os":{"arch":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}}]"""
        )
        assertFalse(runCatching { MojangLibraryRulePolicy.isAllowed(malformed, android) }.isSuccess)
        assertFalse(runCatching { MojangLibraryRulePolicy.isAllowed(unknownAction, android) }.isSuccess)
        assertFalse(runCatching { MojangLibraryRulePolicy.isAllowed(invalidRegex, android) }.isSuccess)
        assertFalse(runCatching { MojangLibraryRulePolicy.isAllowed(oversizedRegex, android) }.isSuccess)
    }

    private fun library(rules: String): JSONObject =
        JSONObject().put("rules", org.json.JSONArray(rules))
}
