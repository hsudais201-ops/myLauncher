import java.security.MessageDigest
import org.gradle.api.tasks.bundling.Jar

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

if (providers.gradleProperty("withAmethyst").orNull == "true") {
    // The upstream Groovy subproject build scripts call writeVersionFile(...)
    // as a root-project helper. Expose the equivalent closure to those scripts.
    extensions.extraProperties["writeVersionFile"] = { jarFile: File, versionFile: File ->
        val digest = MessageDigest.getInstance("SHA-1")
        jarFile.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        versionFile.parentFile.mkdirs()
        versionFile.writeText(
            digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        )
    }

    // Match upstream's root build behavior: package non-LWJGL component jars
    // into the app assets tree and emit the matching SHA-1 version marker.
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
                    val jarFile = archiveFile.get().asFile
                    val versionFile = rootProject.file(
                        "engine/amethyst/app_pojavlauncher/src/main/assets/components/$componentName/version"
                    )
                    @Suppress("UNCHECKED_CAST")
                    val writer = rootProject.extensions.extraProperties["writeVersionFile"] as (File, File) -> Unit
                    writer(jarFile, versionFile)
                }
            }
        }
    }
}
