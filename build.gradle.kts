import java.security.MessageDigest
import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

if (providers.gradleProperty("withAmethyst").orNull == "true") {
    // Reproduce the upstream Amethyst root build's component-JAR packaging when
    // the engine is hosted as nested Gradle projects inside Flame.
    subprojects {
        val isEngineComponent = path.startsWith(":engine:") &&
            path != ":engine:app_pojavlauncher" &&
            path != ":engine:jre_lwjgl3glfw" &&
            !path.startsWith(":engine:jre_lwjgl3glfw:")

        if (isEngineComponent) {
            tasks.withType<Jar>().configureEach {
                val componentName = project.name
                destinationDirectory.set(
                    rootProject.layout.projectDirectory.dir(
                        "engine/amethyst/app_pojavlauncher/src/main/assets/components/$componentName"
                    )
                )
                doLast {
                    val versionFile = rootProject.file(
                        "engine/amethyst/app_pojavlauncher/src/main/assets/components/$componentName/version"
                    )
                    versionFile.parentFile.mkdirs()
                    val digest = MessageDigest.getInstance("SHA-1")
                    archiveFile.get().asFile.inputStream().use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            digest.update(buffer, 0, count)
                        }
                    }
                    versionFile.writeText(
                        digest.digest().joinToString("") { byte -> "%02x".format(byte) }
                    )
                }
            }
        }
    }
}
