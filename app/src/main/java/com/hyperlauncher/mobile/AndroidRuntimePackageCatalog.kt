package com.hyperlauncher.mobile

/**
 * Android-specific OpenJDK archives published by the Amethyst OpenJDK build.
 *
 * URLs are versioned GitHub release assets and each archive is pinned to the
 * SHA-256 digest and byte length reported by GitHub's release metadata. These
 * packages provide the Java runtime only; they do not include Hyper Launcher's
 * future LWJGL/graphics/input integration.
 */
data class AndroidRuntimePackage(
    val javaMajor: Int,
    val abi: String,
    val archiveFileName: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String
)

object AndroidRuntimePackageCatalog {
    private const val RELEASE_BASE =
        "https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/download"

    private val packages = listOf(
        packageEntry(8, "arm64-v8a", "arm64", 28_026_736L,
            "9a59124d9791957d55c68be664ab76831f336cf2e1e1cd4414220c6fdbf0e06d"),
        packageEntry(8, "x86_64", "x86_64", 29_132_884L,
            "b1fbcef4965c17925894febe8216d089c6dd47b37950b5f945a89616443c1d0e"),
        packageEntry(17, "arm64-v8a", "arm64", 26_900_804L,
            "e162c860fe05ee4a4e4af7606437419879f6c748386a7b09fa77d10db6a64091"),
        packageEntry(17, "x86_64", "x86_64", 27_780_012L,
            "893e27d2aed8b40407f29fe939e2a0f193e5d55f72892a303db634b0808a2b61"),
        packageEntry(21, "arm64-v8a", "arm64", 28_675_516L,
            "8d41ec401ee59f7722df60ed991f81ad146e130452804bfdd8a05d3436f7bbfe"),
        packageEntry(21, "x86_64", "x86_64", 29_662_988L,
            "cb88723961f5f9ad63afa1f212eb199816c27cabfd7dc66567bde1d8fb69713b"),
        packageEntry(25, "arm64-v8a", "arm64", 38_031_580L,
            "d3eb7afe2240c26728a1bb440502c5f18ac3883e932d202dd7f0c9bcbbce4c37"),
        packageEntry(25, "x86_64", "x86_64", 39_061_384L,
            "7fca862ee1b2d5fe23cd9c9c3d9b7ad3c241947ad1a6cc9464ef2e674867105d")
    )

    /** Exact runtime package for a Java major and Android ABI, or null if absent. */
    fun exact(javaMajor: Int, abi: String): AndroidRuntimePackage? =
        packages.firstOrNull { it.javaMajor == javaMajor && it.abi == abi }

    /**
     * Selects the lowest available Java major that satisfies the version
     * requirement. A newer Java runtime is not assumed to exist unless its
     * exact Android ABI package is present in this catalog.
     */
    fun compatible(requiredJavaMajor: Int, abi: String): AndroidRuntimePackage? {
        require(requiredJavaMajor > 0) { "Required Java major must be positive" }
        return packages.asSequence()
            .filter { it.abi == abi && it.javaMajor >= requiredJavaMajor }
            .minByOrNull { it.javaMajor }
    }

    fun availableForAbi(abi: String): List<AndroidRuntimePackage> =
        packages.filter { it.abi == abi }.sortedBy { it.javaMajor }

    private fun packageEntry(
        javaMajor: Int,
        abi: String,
        archiveArch: String,
        sizeBytes: Long,
        sha256: String
    ): AndroidRuntimePackage {
        val fileName = "jre$javaMajor-android-$archiveArch.tar.xz"
        val releaseTag = "download_jre$javaMajor"
        return AndroidRuntimePackage(
            javaMajor = javaMajor,
            abi = abi,
            archiveFileName = fileName,
            downloadUrl = "$RELEASE_BASE/$releaseTag/$fileName",
            sizeBytes = sizeBytes,
            sha256 = sha256
        )
    }
}
