plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.flame.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.flame.launcher"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val withAmethyst = providers.gradleProperty("withAmethyst").orNull == "true"
        buildConfigField("boolean", "AMETHYST_ENABLED", withAmethyst.toString())
        manifestPlaceholders["flameApplicationClass"] =
            if (withAmethyst) "net.kdt.pojavlaunch.PojavApplication" else "com.flame.launcher.FlameApplication"
        manifestPlaceholders["flameApplicationTheme"] =
            if (withAmethyst) "@style/AppTheme" else "@style/Theme.FlameLauncher"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    ndkVersion = "27.3.13750724"

    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += setOf("**/libbytehook.so", "**/libandroidnsbypass.so")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation(libs.junit)

    if (providers.gradleProperty("withAmethyst").orNull == "true") {
        implementation(project(":engine:app_pojavlauncher"))
        // Local engine AARs must be direct app dependencies; AGP forbids nesting them in an AAR.
        implementation(fileTree(mapOf("dir" to rootProject.file("engine/amethyst/app_pojavlauncher/libs"), "include" to listOf("*.aar"))))
    }
}
