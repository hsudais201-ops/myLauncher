package com.voidcraft.cosmetics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CosmeticIdTest {
    @Test void supportedCosmeticsAreDistinctAndSafeIds() {
        String[] ids = {"void-aura", "neon-mint", "arctic-ice", "solar-ember"};
        assertEquals(4, java.util.Arrays.stream(ids).distinct().count());
        for (String id : ids) {
            assertTrue(id.matches("^[a-z-]{1,32}$"));
            assertTrue(CosmeticId.isSupported(id));
            assertEquals(id, CosmeticId.normalize(id));
        }
    }

    @Test void noneIsTheDisabledState() {
        assertEquals("none", CosmeticId.normalize("none"));
        assertFalse(CosmeticId.isSupported("none"));
    }

    @Test void malformedOrUnknownIdsFailClosed() {
        for (String id : new String[] {null, "", "../void-aura", "VOID-AURA", "unknown", "solar-ember.json"}) {
            assertFalse(CosmeticId.isSupported(id));
            assertEquals("none", CosmeticId.normalize(id));
        }
    }
}
