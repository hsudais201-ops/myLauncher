package com.voidcraft.cosmetics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CosmeticIdTest {
    @Test void supportedCosmeticsAreDistinctAndSafeIds() {
        String[] ids = {"void-aura", "neon-mint", "arctic-ice", "solar-ember"};
        assertEquals(4, java.util.Arrays.stream(ids).distinct().count());
        for (String id : ids) assertTrue(id.matches("^[a-z-]{1,32}$"));
    }

    @Test void noneIsTheDisabledState() {
        assertFalse(java.util.Set.of("void-aura", "neon-mint", "arctic-ice", "solar-ember").contains("none"));
    }
}
