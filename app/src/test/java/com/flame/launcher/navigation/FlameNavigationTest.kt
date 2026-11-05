package com.flame.launcher.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlameNavigationTest {
    @Test
    fun mainRoutesAreUnique() {
        val routes = FlameNavigation.mainSections.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun everyMainSectionCanBeResolvedByRoute() {
        FlameNavigation.mainSections.forEach { section ->
            assertEquals(section, FlameNavigation.find(section.route))
        }
    }

    @Test
    fun unknownRouteReturnsNull() {
        assertNull(FlameNavigation.find("not-a-flame-route"))
    }

    @Test
    fun minecraftContentAndFeatureStoreAreSeparateMainDestinations() {
        val routes = FlameNavigation.mainSections.map { it.route }
        assertTrue(routes.contains(LauncherSection.MINECRAFT_CONTENT.route))
        assertTrue(routes.contains(LauncherSection.FEATURE_STORE.route))
        assertTrue(
            FlameNavigation.mainSections.indexOf(LauncherSection.MINECRAFT_CONTENT) <
                FlameNavigation.mainSections.indexOf(LauncherSection.FEATURE_STORE)
        )
    }

    @Test
    fun allPlannedMainDestinationsArePresent() {
        val sections = FlameNavigation.mainSections.toSet()
        assertTrue(sections.containsAll(LauncherSection.entries.toSet()))
        assertEquals(8, sections.size)
    }
}
