package com.mflegend.flamelauncher.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeImportBudgetTest {
    @Test
    fun countsFilesAndDirectoriesSeparately() {
        val budget = RuntimeImportBudget(maxEntries = 4, maxTotalBytes = 100)
        budget.registerEntry(isDirectory = true)
        budget.registerEntry(isDirectory = false)
        budget.registerEntry(isDirectory = true)
        budget.registerEntry(isDirectory = false)
        budget.addBytes(25)

        assertEquals(4, budget.entries)
        assertEquals(2, budget.files)
        assertEquals(25L, budget.bytes)
    }

    @Test
    fun rejectsExcessiveEntryCount() {
        val budget = RuntimeImportBudget(maxEntries = 1, maxTotalBytes = 100)
        budget.registerEntry(isDirectory = true)

        val error = runCatching { budget.registerEntry(isDirectory = false) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals(1, budget.entries)
    }

    @Test
    fun rejectsNegativeOrExcessiveByteTotalsWithoutOverflow() {
        val budget = RuntimeImportBudget(maxEntries = 10, maxTotalBytes = 100)
        assertTrue(runCatching { budget.addBytes(-1) }.exceptionOrNull() is IllegalArgumentException)
        budget.addBytes(75)
        assertTrue(runCatching { budget.addBytes(26) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { budget.addBytes(Long.MAX_VALUE) }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(75L, budget.bytes)
    }
}
