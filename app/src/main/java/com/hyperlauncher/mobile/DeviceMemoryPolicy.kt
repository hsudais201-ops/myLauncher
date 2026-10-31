package com.hyperlauncher.mobile

data class DeviceMemoryRange(
    val minimumGb: Int,
    val maximumGb: Int,
    val recommendedGb: Int
)

/**
 * Conservative memory-target guidance for mobile devices.
 * This is a UI policy only; it does not reserve RAM or configure a game JVM.
 */
object DeviceMemoryPolicy {
    private const val GIB = 1024L * 1024L * 1024L

    fun calculate(totalMemoryBytes: Long): DeviceMemoryRange {
        require(totalMemoryBytes > 0L) { "Total device memory must be positive" }
        val totalGb = totalMemoryBytes.toDouble() / GIB.toDouble()
        val minimum = if (totalGb < 5.0) 1 else 2
        val maximum = (totalGb * 0.55).toInt().coerceIn(minimum, 8)
        val recommended = 4.coerceAtMost(maximum).coerceAtLeast(minimum)
        return DeviceMemoryRange(minimum, maximum, recommended)
    }
}
