package com.mflegend.flamelauncher.catalog

/**
 * Validates the content-addressed object declarations in one asset index.
 *
 * Multiple logical asset names may share a SHA-1 object, but they must agree
 * on its declared size. The aggregate limit counts unique objects whether or
 * not their bytes are already present on disk.
 */
internal class AssetObjectTracker(
    private val maxObjectBytes: Long,
    private val maxTotalBytes: Long
) {
    private val declaredSizes = HashMap<String, Long>()

    var totalDeclaredBytes: Long = 0
        private set

    /** Returns true for a new object and false for an identical duplicate. */
    fun register(hash: String, size: Long): Boolean {
        require(hash.matches(Regex("[a-f0-9]{40}"))) {
            "Asset index contains an invalid object hash."
        }
        require(size in 0..maxObjectBytes) {
            "Asset object size is missing or exceeds the safety limit."
        }

        val previousSize = declaredSizes[hash]
        if (previousSize != null) {
            require(previousSize == size) {
                "Asset index declares conflicting sizes for the same object hash."
            }
            return false
        }

        require(totalDeclaredBytes <= maxTotalBytes - size) {
            "Asset set exceeds the total download safety limit."
        }
        declaredSizes[hash] = size
        totalDeclaredBytes += size
        return true
    }
}
