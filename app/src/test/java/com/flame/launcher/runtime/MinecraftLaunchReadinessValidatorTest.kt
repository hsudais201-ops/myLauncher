package com.flame.launcher.runtime

import com.flame.launcher.content.MinecraftInstanceLaunchSettings
import com.flame.launcher.content.MinecraftInstanceProfile
import com.flame.launcher.content.MinecraftInstanceProfileResult
import com.flame.launcher.content.MinecraftInstanceProfileStore
import com.flame.launcher.content.MinecraftLoader
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftLaunchReadinessValidatorTest {
    @Test
    fun blocksWhenProfileAndDependencyPlanVersionsDiffer() = withFixture { root, profile, manager ->
        val result = validator(manager).validate(profile, settings(), plan(version = "1.20.1"),)
        assertTrue(result is MinecraftLaunchReadiness.Blocked.ProfileVersionMismatch)
    }

    @Test
    fun blocksWhenLaunchSettingsAreInvalid() = withFixture { root, profile, manager ->
        val result = validator(manager).validate(profile, settings(maximumMemoryMb = 256), plan())
        assertTrue(result is MinecraftLaunchReadiness.Blocked.InvalidSettings)
    }

    @Test
    fun refusesToGuessWhenJavaRequirementMetadataIsMissing() = withFixture { root, profile, manager ->
        val result = validator(manager).validate(profile, settings(), plan(requiredJava = null))
        assertEquals(
            MinecraftLaunchReadiness.Blocked.VersionRequirementUnavailable,
            result
        )
    }

    @Test
    fun blocksWhenNoRuntimeOfRequiredMajorIsInstalled() = withFixture { root, profile, manager ->
        val result = validator(manager).validate(profile, settings(), plan())
        assertTrue(result is MinecraftLaunchReadiness.Blocked.RuntimeUnavailable)
    }

    @Test
    fun blocksWhenOnlyRuntimeForAnotherArchitectureIsInstalled() = withFixture { root, profile, _ ->
        createRuntime(File(root, "runtimes"), "java-21-x64", "21.0.4", "amd64")
        val manager = JavaRuntimeManager(File(root, "runtimes"), "arm64-v8a")
        val result = validator(manager).validate(profile, settings(), plan())
        assertTrue(result is MinecraftLaunchReadiness.Blocked.RuntimeUnavailable)
        assertEquals(
            emptyList<Int>(),
            (result as MinecraftLaunchReadiness.Blocked.RuntimeUnavailable).availableMajors
        )
    }

    @Test
    fun blocksAnExplicitRuntimeSelectionWithWrongJavaMajor() = withFixture { root, profile, manager ->
        createRuntime(File(root, "runtimes"), "java-17", "17.0.12", "aarch64")
        createRuntime(File(root, "runtimes"), "java-21", "21.0.4", "aarch64")
        val result = validator(manager).validate(
            profile,
            settings(javaRuntimeId = "java-17"),
            plan()
        )
        assertTrue(result is MinecraftLaunchReadiness.Blocked.PreferredRuntimeUnavailable)
    }

    @Test
    fun acceptsAnExplicitCompatibleRuntime() = withFixture { root, profile, manager ->
        createRuntime(File(root, "runtimes"), "java-21", "21.0.4", "aarch64")
        val result = validator(manager).validate(
            profile,
            settings(javaRuntimeId = "java-21"),
            plan()
        )
        assertTrue(result is MinecraftLaunchReadiness.Ready)
        assertEquals("java-21", (result as MinecraftLaunchReadiness.Ready).runtime.id)
    }

    @Test
    fun automaticallySelectsCompatibleRuntimeWhenNoPreferenceIsSaved() = withFixture { root, profile, manager ->
        createRuntime(File(root, "runtimes"), "java-21-old", "21.0.2", "aarch64")
        createRuntime(File(root, "runtimes"), "java-21-new", "21.0.4", "aarch64")
        val result = validator(manager).validate(profile, settings(), plan())
        assertTrue(result is MinecraftLaunchReadiness.Ready)
        assertEquals("java-21-new", (result as MinecraftLaunchReadiness.Ready).runtime.id)
    }

    private fun validator(manager: JavaRuntimeManager) = MinecraftLaunchReadinessValidator(manager)

    private fun settings(
        maximumMemoryMb: Int = 1536,
        javaRuntimeId: String? = null
    ) = MinecraftInstanceLaunchSettings(
        maximumMemoryMb = maximumMemoryMb,
        javaRuntimeId = javaRuntimeId
    )

    private fun plan(
        version: String = "1.21",
        requiredJava: Int? = 21
    ) = MinecraftLaunchDependencyPlan(
        versionId = version,
        mainClass = "net.minecraft.client.main.Main",
        requiredJavaMajorVersion = requiredJava,
        assetIndexId = "17",
        downloads = emptyList(),
        classPath = emptyList(),
        nativeLibraries = emptyList()
    )

    private fun withFixture(
        block: (File, MinecraftInstanceProfile, JavaRuntimeManager) -> Unit
    ) {
        val root = Files.createTempDirectory("flame-readiness-").toFile()
        try {
            val profilesRoot = File(root, "profiles")
            val profile = (MinecraftInstanceProfileStore(profilesRoot).createProfile(
                "Readiness test",
                "1.21",
                MinecraftLoader.FABRIC
            ) as MinecraftInstanceProfileResult.Created).profile
            val runtimes = File(root, "runtimes")
            runtimes.mkdirs()
            block(root, profile, JavaRuntimeManager(runtimes, "arm64-v8a"))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun createRuntime(root: File, id: String, version: String, architecture: String) {
        val home = File(root, id).apply { mkdirs() }
        File(home, "release").writeText(
            "JAVA_VERSION=\"$version\"\nOS_ARCH=\"$architecture\"\n"
        )
        File(home, "lib/server/libjvm.so").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
    }
}
