pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "FlameLauncher"
include(":app")

val withAmethyst = providers.gradleProperty("withAmethyst").orNull == "true"
if (withAmethyst) {
    val engineRoot = file("engine/amethyst")
    val requiredProjects = mapOf(
        ":engine:app_pojavlauncher" to "app_pojavlauncher",
        ":engine:jre_lwjgl3glfw" to "jre_lwjgl3glfw",
        ":engine:jre_lwjgl3glfw:lwjgl-3.3.3" to "jre_lwjgl3glfw/lwjgl-3.3.3",
        ":engine:jre_lwjgl3glfw:lwjgl-3.4.1" to "jre_lwjgl3glfw/lwjgl-3.4.1",
        ":engine:arc_dns_injector" to "arc_dns_injector",
        ":engine:MioLibPatcher" to "MioLibPatcher",
        ":engine:forge_installer" to "forge_installer",
        ":engine:androidnsbypass" to "androidnsbypass"
    )

    requiredProjects.forEach { (path, relativeDir) ->
        val projectDir = engineRoot.resolve(relativeDir)
        if (!projectDir.isDirectory) {
            throw GradleException(
                "Amethyst project $path is missing at ${projectDir.path}. " +
                    "Run git submodule update --init --recursive."
            )
        }
        include(path)
        project(path).projectDir = projectDir
    }
}
