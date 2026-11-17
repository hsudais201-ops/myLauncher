package com.mflegend.flamelauncher.runtime

/** Counts copied runtime entries and bytes without trusting provider metadata. */
internal class RuntimeImportBudget(
    private val maxEntries: Int = RuntimeImportPolicy.MAX_ENTRIES,
    private val maxTotalBytes: Long = RuntimeImportPolicy.MAX_TOTAL_BYTES
) {
    var entries: Int = 0
        private set
    var files: Int = 0
        private set
    var bytes: Long = 0
        private set

    fun registerEntry(isDirectory: Boolean) {
        require(entries < maxEntries) {
            "The selected runtime contains too many files and folders."
        }
        entries++
        if (!isDirectory) files++
    }

    fun addBytes(count: Long) {
        require(count >= 0 && bytes <= maxTotalBytes - count) {
            "The selected runtime exceeds the total import size limit."
        }
        bytes += count
    }
}
