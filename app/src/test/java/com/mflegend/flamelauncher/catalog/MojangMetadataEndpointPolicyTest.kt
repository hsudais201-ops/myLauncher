package com.mflegend.flamelauncher.catalog

import java.net.URL
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MojangMetadataEndpointPolicyTest {
    @Test fun acceptsOfficialHttpsOrigin() {
        assertTrue(MojangMetadataEndpointPolicy.isApproved(URL("https://piston-meta.mojang.com/v1/packages/test.json")))
        assertTrue(MojangMetadataEndpointPolicy.isApproved(URL("https://PISTON-META.MOJANG.COM:443/v1/packages/test.json")))
    }

    @Test fun rejectsNonHttpsAndLookalikeHosts() {
        assertFalse(MojangMetadataEndpointPolicy.isApproved(URL("http://piston-meta.mojang.com/file.json")))
        assertFalse(MojangMetadataEndpointPolicy.isApproved(URL("https://piston-meta.mojang.com.evil.example/file.json")))
        assertFalse(MojangMetadataEndpointPolicy.isApproved(URL("https://evil.example/piston-meta.mojang.com/file.json")))
    }

    @Test fun rejectsCredentialsUnexpectedPortsQueriesAndFragments() {
        assertFalse(MojangMetadataEndpointPolicy.isApproved(URL("https://user@piston-meta.mojang.com/file.json")))
        assertFalse(MojangMetadataEndpointPolicy.isApproved(URL("https://piston-meta.mojang.com:8443/file.json")))
        assertFalse(MojangMetadataEndpointPolicy.isApproved(URL("https://piston-meta.mojang.com/file.json?redirect=evil")))
        assertFalse(MojangMetadataEndpointPolicy.isApproved(URL("https://piston-meta.mojang.com/file.json#fragment")))
    }
}
