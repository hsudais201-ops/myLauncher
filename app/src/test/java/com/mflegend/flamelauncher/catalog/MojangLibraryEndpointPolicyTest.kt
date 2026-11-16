package com.mflegend.flamelauncher.catalog

import java.net.URL
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MojangLibraryEndpointPolicyTest {
    @Test fun acceptsApprovedMavenHttpsHosts() {
        assertTrue(MojangLibraryEndpointPolicy.isApproved(URL("https://libraries.minecraft.net/com/example/library/1.0/library-1.0.jar")))
        assertTrue(MojangLibraryEndpointPolicy.isApproved(URL("https://repo.maven.apache.org/maven2/com/example/library/1.0/library-1.0.jar")))
        assertTrue(MojangLibraryEndpointPolicy.isApproved(URL("https://MAVEN.FABRICMC.NET:443/com/example/library/1.0/library-1.0.jar")))
    }

    @Test fun rejectsUnapprovedHostsAndNonHttpsSchemes() {
        assertFalse(MojangLibraryEndpointPolicy.isApproved(URL("http://libraries.minecraft.net/library.jar")))
        assertFalse(MojangLibraryEndpointPolicy.isApproved(URL("https://libraries.minecraft.net.evil.example/library.jar")))
        assertFalse(MojangLibraryEndpointPolicy.isApproved(URL("https://evil.example/libraries.minecraft.net/library.jar")))
    }

    @Test fun rejectsCredentialsPortsQueriesAndFragments() {
        assertFalse(MojangLibraryEndpointPolicy.isApproved(URL("https://user@libraries.minecraft.net/library.jar")))
        assertFalse(MojangLibraryEndpointPolicy.isApproved(URL("https://libraries.minecraft.net:8443/library.jar")))
        assertFalse(MojangLibraryEndpointPolicy.isApproved(URL("https://libraries.minecraft.net/library.jar?redirect=evil")))
        assertFalse(MojangLibraryEndpointPolicy.isApproved(URL("https://libraries.minecraft.net/library.jar#fragment")))
    }
}
