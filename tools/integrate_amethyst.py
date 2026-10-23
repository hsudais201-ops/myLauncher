#!/usr/bin/env python3
"""Overlay Voidcraft's Compose frontend onto a pinned Amethyst Android engine checkout.

This is a build-time integration script. It never downloads code and refuses to run when
expected upstream Gradle/manifest anchors have changed, so upstream drift cannot silently
produce a different app layout.
"""
from __future__ import annotations

import argparse
import shutil
from pathlib import Path

UPSTREAM_PLUGIN_BLOCK = """plugins {
    id 'com.android.application' version '9.3.1'
}"""

VOIDCRAFT_PLUGIN_BLOCK = """plugins {
    id 'com.android.application' version '9.3.1'
    id 'org.jetbrains.kotlin.plugin.compose' version '2.4.20'
}"""

TEST_STORAGE_LAUNCHER_BLOCK = """        <activity
            android:name=".TestStorageActivity"
            android:exported="true"
            android:label="@string/app_short_name"
            android:launchMode="singleTop">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>"""

TEST_STORAGE_NO_LAUNCHER_BLOCK = """        <activity
            android:name=".TestStorageActivity"
            android:exported="false"
            android:label="@string/app_short_name"
            android:launchMode="singleTop" />"""

VOIDCRAFT_ACTIVITY = """        <activity
            android:name="com.voidcraft.launcher.MainActivity"
            android:exported="false"
            android:label="Voidcraft"
            android:theme="@style/AppTheme"
            android:windowSoftInputMode="adjustResize" />
"""

VERSION_HANDOFF_METHOD = """    private void applyVoidcraftSelectedVersion() {
        String requestedVersion = getIntent() == null ? null :
                getIntent().getStringExtra("voidcraft.selected_version");
        if (requestedVersion == null || requestedVersion.isEmpty() ||
                "latest".equalsIgnoreCase(requestedVersion)) return;
        if (!requestedVersion.matches("^[A-Za-z0-9._-]{1,100}$")) {
            Toast.makeText(this, "Voidcraft passed an invalid Minecraft version ID.", Toast.LENGTH_LONG).show();
            return;
        }
        if (Tools.DIR_HOME_VERSION == null || Tools.DIR_HOME_VERSION.isEmpty()) return;

        File versionDirectory = new File(Tools.DIR_HOME_VERSION, requestedVersion);
        File metadata = new File(versionDirectory, requestedVersion + ".json");
        File clientJar = new File(versionDirectory, requestedVersion + ".jar");
        if (!metadata.isFile() || metadata.length() == 0L ||
                !clientJar.isFile() || clientJar.length() == 0L) {
            Toast.makeText(this,
                    "Minecraft " + requestedVersion +
                            " is not prepared yet. Prepare it in Voidcraft or install it in the engine first.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        try {
            org.json.JSONObject versionJson = new org.json.JSONObject(Tools.read(metadata.getAbsolutePath()));
            if (!requestedVersion.equals(versionJson.optString("id", ""))) {
                Toast.makeText(this, "Prepared metadata does not match Minecraft " + requestedVersion + ".",
                        Toast.LENGTH_LONG).show();
                return;
            }

            org.json.JSONObject downloads = versionJson.optJSONObject("downloads");
            org.json.JSONObject client = downloads == null ? null : downloads.optJSONObject("client");
            String expectedSha1 = client == null ? "" : client.optString("sha1", "");
            long expectedSize = client == null ? -1L : client.optLong("size", -1L);
            if (!expectedSha1.matches("(?i)^[0-9a-f]{40}$") ||
                    expectedSize <= 0L || clientJar.length() != expectedSize) {
                Toast.makeText(this,
                        "Minecraft " + requestedVersion + " has incomplete or mismatched client metadata.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-1");
            try (java.io.InputStream input = new java.io.FileInputStream(clientJar)) {
                byte[] buffer = new byte[32768];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, count);
                }
            }
            StringBuilder calculatedSha1 = new StringBuilder(40);
            for (byte value : digest.digest()) {
                calculatedSha1.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            }
            if (!expectedSha1.equalsIgnoreCase(calculatedSha1.toString())) {
                Toast.makeText(this,
                        "Minecraft " + requestedVersion + " client JAR failed SHA-1 verification. Re-prepare the version.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            LauncherProfiles.load();
            java.util.Map<String, MinecraftProfile> profiles = LauncherProfiles.mainProfileJson.profiles;
            String profileId = LauncherPreferences.DEFAULT_PREF.getString(
                    LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
            if (profileId == null || !profiles.containsKey(profileId)) {
                if (profiles.isEmpty()) {
                    throw new IllegalStateException("No Minecraft profile is available.");
                }
                // The launcher activity can open before its profile spinner initializes.
                // Reuse an existing profile rather than failing the handoff on first launch.
                profileId = profiles.keySet().iterator().next();
                LauncherPreferences.DEFAULT_PREF.edit()
                        .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, profileId).apply();
            }
            MinecraftProfile profile = profiles.get(profileId);
            if (profile == null) {
                throw new IllegalStateException("The selected Minecraft profile could not be loaded.");
            }
            profile.lastVersionId = requestedVersion;
            profiles.put(profileId, profile);
            LauncherProfiles.write();
            Toast.makeText(this, "Selected verified Minecraft " + requestedVersion + " from Voidcraft.",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception exception) {
            Toast.makeText(this, "Could not verify or apply Voidcraft's selected version. Choose it in the engine profile menu.",
                    Toast.LENGTH_LONG).show();
        }
    }

"""

RENDERER_HANDOFF_METHOD = """    private void applyVoidcraftSelectedRenderer() {
        String requestedRenderer = getIntent() == null ? null :
                getIntent().getStringExtra("voidcraft.renderer");
        if (requestedRenderer == null || requestedRenderer.isEmpty()) return;

        if (!java.util.Arrays.asList(
                "opengles2",
                "opengles2_holy",
                "opengles_mobileglues",
                "vulkan_zink",
                "opengles3_ltw"
        ).contains(requestedRenderer)) {
            Toast.makeText(this, "Voidcraft requested an unsupported renderer.", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            LauncherProfiles.load();
            java.util.Map<String, MinecraftProfile> profiles = LauncherProfiles.mainProfileJson.profiles;
            String profileId = LauncherPreferences.DEFAULT_PREF.getString(
                    LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
            if (profileId == null || !profiles.containsKey(profileId)) {
                if (profiles.isEmpty()) {
                    throw new IllegalStateException("No Minecraft profile is available.");
                }
                profileId = profiles.keySet().iterator().next();
                LauncherPreferences.DEFAULT_PREF.edit()
                        .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, profileId).apply();
            }

            MinecraftProfile profile = profiles.get(profileId);
            if (profile == null) {
                throw new IllegalStateException("The selected Minecraft profile could not be loaded.");
            }
            profile.pojavRendererName = requestedRenderer;
            profiles.put(profileId, profile);
            LauncherProfiles.write();
            Toast.makeText(this,
                    "Renderer request saved. The engine will check device/library compatibility and may fall back.",
                    Toast.LENGTH_LONG).show();
        } catch (Exception exception) {
            Toast.makeText(this,
                    "Could not save the renderer request. Choose a compatible renderer in engine settings.",
                    Toast.LENGTH_LONG).show();
        }
    }

"""

def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"Expected exactly one upstream anchor for {label}; found {count}. The engine source likely changed.")
    return source.replace(old, new, 1)

def patch_launcher_activity(source: str) -> str:
    source = replace_once(
        source,
        "        bindViews();\n        checkNotificationPermission();",
        "        bindViews();\n        applyVoidcraftSelectedVersion();\n        applyVoidcraftSelectedRenderer();\n        checkNotificationPermission();",
        "Voidcraft selected-version handoff call"
    )
    return replace_once(
        source,
        "    @Override\n    protected void onResume() {",
        VERSION_HANDOFF_METHOD + RENDERER_HANDOFF_METHOD + "    @Override\n    protected void onResume() {",
        "Voidcraft selected-version handoff method"
    )

GL4ES_PINNED_SOURCE_COMMIT = "b12ad048157b3aa255d078c235dd4571e1900309"
GL4ES_LICENSE_COMMIT = "144d0dc79855b392e5256dd15fb2dbdedb17141a"
GL4ES_GIT_BLOB_SHAS = {
    "arm64-v8a": ("53e795282115fec813628d3d19cbe54b2a69cb02", 2649480),
    "armeabi-v7a": ("fc706fa556852e0a6b87e4b8001809e90f176b40", 1713044),
    "x86": ("12f910e571346e03ed2ac457a98202b7b9d82318", 2808944),
    "x86_64": ("b21f9355d121d636495cfe72049d46ebc3ac2227", 2760568),
}


def git_blob_sha1(path: Path) -> str:
    data = path.read_bytes()
    header = f"blob {len(data)}\\0".encode("ascii")
    import hashlib
    return hashlib.sha1(header + data).hexdigest()


def install_holy_gl4es(engine_root: Path, gl4es_root: Path, license_root: Path) -> None:
    source_root = gl4es_root / "app_pojavlauncher" / "src" / "main" / "jniLibs"
    destination_root = engine_root / "app_pojavlauncher" / "src" / "main" / "jniLibs"
    for abi, (expected_sha, expected_size) in GL4ES_GIT_BLOB_SHAS.items():
        source = source_root / abi / "libgl4es_114.so"
        if not source.is_file():
            raise SystemExit(f"Pinned Holy GL4ES library is missing: {source}")
        actual_size = source.stat().st_size
        actual_sha = git_blob_sha1(source)
        if actual_size != expected_size or actual_sha != expected_sha:
            raise SystemExit(
                f"Unexpected Holy GL4ES binary for {abi}: size={actual_size}, git-blob={actual_sha}"
            )
        destination = destination_root / abi / "libgl4es_114.so"
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
        if destination.stat().st_size != expected_size or git_blob_sha1(destination) != expected_sha:
            raise SystemExit(f"Copied Holy GL4ES library failed integrity check: {destination}")
        print(f"Verified and packaged Holy GL4ES {abi}: {actual_size} bytes")

    license_file = license_root / "LICENSE"
    if not license_file.is_file():
        raise SystemExit(f"GL4ES MIT license is missing: {license_file}")
    license_text = license_file.read_text(encoding="utf-8")
    if "Permission is hereby granted, free of charge" not in license_text or "THE SOFTWARE IS PROVIDED" not in license_text:
        raise SystemExit("The checked-out GL4ES license did not match the expected MIT license text.")
    bundled_license = (
        engine_root / "app_pojavlauncher" / "src" / "main" / "assets"
        / "licenses" / "GL4ES-MIT-LICENSE.txt"
    )
    bundled_license.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(license_file, bundled_license)
    print(f"Bundled GL4ES MIT license: {bundled_license}")


def patch_holy_gl4es_renderer(engine_root: Path) -> None:
    module = engine_root / "app_pojavlauncher"
    java_root = module / "src" / "main" / "java" / "net" / "kdt" / "pojavlaunch"
    jre_utils_file = java_root / "utils" / "JREUtils.java"
    tools_file = java_root / "Tools.java"
    renderer_arrays_file = module / "src" / "main" / "res" / "values" / "headings_array.xml"
    strings_file = module / "src" / "main" / "res" / "values" / "strings.xml"
    for path in (jre_utils_file, tools_file, renderer_arrays_file, strings_file):
        if not path.is_file():
            raise SystemExit(f"Required renderer integration source is missing: {path}")

    jre_utils = jre_utils_file.read_text(encoding="utf-8")
    jre_utils = replace_once(
        jre_utils,
        '            case "opengles2":\\n            case "opengles2_5":\\n            case "opengles3":\\n                renderLibrary = "libng_gl4es.so"; break;',
        '            case "opengles2_holy": renderLibrary = "libgl4es_114.so"; break;\\n'
        '            case "opengles2":\\n            case "opengles2_5":\\n            case "opengles3":\\n                renderLibrary = "libng_gl4es.so"; break;',
        "Holy GL4ES library loading case"
    )
    jre_utils = replace_once(
        jre_utils,
        '            if(LOCAL_RENDERER.equals("opengles2")){',
        '            if(LOCAL_RENDERER.equals("opengles2") || LOCAL_RENDERER.equals("opengles2_holy")){',
        "Holy GL4ES GLES2 environment"
    )
    jre_utils = replace_once(
        jre_utils,
        'envMap.put("LIBGL_ES", "2"); // Krypton Wrapper crashes with 1',
        'envMap.put("LIBGL_ES", "2"); // GL4ES-family GLES2 backends require GLES 2.',
        "GLES2 renderer comment"
    )
    jre_utils_file.write_text(jre_utils, encoding="utf-8")

    tools = tools_file.read_text(encoding="utf-8")
    tools = replace_once(
        tools,
        '        boolean appHasKw = new File(Tools.NATIVE_LIB_DIR, "libng_gl4es.so").exists();',
        '        boolean appHasKw = new File(Tools.NATIVE_LIB_DIR, "libng_gl4es.so").exists();\\n'
        '        boolean appHasHolyGl4es = new File(Tools.NATIVE_LIB_DIR, "libgl4es_114.so").isFile();',
        "Holy GL4ES availability check"
    )
    tools = replace_once(
        tools,
        '            if(rendererId.contains("opengles2") && (!deviceHasOpenGLES3 || !appHasKw)) continue;',
        '            if(rendererId.equals("opengles2") && (!deviceHasOpenGLES3 || !appHasKw)) continue;\\n'
        '            if(rendererId.equals("opengles2_holy") && !appHasHolyGl4es) continue;',
        "Holy GL4ES compatible renderer gate"
    )
    tools_file.write_text(tools, encoding="utf-8")

    headings = renderer_arrays_file.read_text(encoding="utf-8")
    old_names = '''    <string-array name="renderer">
        <item name="0">@string/mcl_setting_renderer_ng_gl4es</item>
        <item name="1">@string/mcl_setting_renderer_mobileglues</item>
        <item name="2">@string/mcl_setting_renderer_vulkan_kopper_zink</item>
        <item name="3">@string/mcl_setting_renderer_vulkan_zink</item>
        <item name="4">@string/mcl_setting_renderer_ltw</item>
    </string-array>'''
    new_names = '''    <string-array name="renderer">
        <item name="0">@string/mcl_setting_renderer_ng_gl4es</item>
        <item name="1">@string/mcl_setting_renderer_holy_gl4es</item>
        <item name="2">@string/mcl_setting_renderer_mobileglues</item>
        <item name="3">@string/mcl_setting_renderer_vulkan_kopper_zink</item>
        <item name="4">@string/mcl_setting_renderer_vulkan_zink</item>
        <item name="5">@string/mcl_setting_renderer_ltw</item>
    </string-array>'''
    headings = replace_once(headings, old_names, new_names, "Holy GL4ES renderer label array")
    old_values = '''    <string-array name="renderer_values">
        <item>opengles2</item> <!-- BZLZHH/NG-GL4ES (Krypton Wrapper) -->
        <item>opengles_mobileglues</item>
        <item>opengles3_desktopgl_zink_kopper</item> <!-- Swung's Kopper Zink backport to 23.0.4 -->
        <item>vulkan_zink</item> <!-- osmesa 23.0.4 -->
        <item>opengles3_ltw</item>
    </string-array>'''
    new_values = '''    <string-array name="renderer_values">
        <item>opengles2</item> <!-- BZLZHH/NG-GL4ES (Krypton Wrapper) -->
        <item>opengles2_holy</item> <!-- Original Holy GL4ES 1.1.4 -->
        <item>opengles_mobileglues</item>
        <item>opengles3_desktopgl_zink_kopper</item> <!-- Swung's Kopper Zink backport to 23.0.4 -->
        <item>vulkan_zink</item> <!-- osmesa 23.0.4 -->
        <item>opengles3_ltw</item>
    </string-array>'''
    headings = replace_once(headings, old_values, new_values, "Holy GL4ES renderer ID array")
    renderer_arrays_file.write_text(headings, encoding="utf-8")

    strings = strings_file.read_text(encoding="utf-8")
    strings = replace_once(
        strings,
        '    <string name="mcl_setting_renderer_ng_gl4es">Krypton Wrapper - (all versions, fast)</string>',
        '    <string name="mcl_setting_renderer_ng_gl4es">Krypton Wrapper - (all versions, fast)</string>\\n'
        '    <string name="mcl_setting_renderer_holy_gl4es">Holy GL4ES - (all versions, fast)</string>',
        "Holy GL4ES renderer display string"
    )
    strings_file.write_text(strings, encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("engine_root", type=Path, help="Path to a checked-out Amethyst-Android source tree")
    parser.add_argument("--gl4es-root", type=Path, required=True, help="Pinned PojavLauncher checkout with GL4ES native libraries")
    parser.add_argument("--gl4es-license-root", type=Path, required=True, help="Pinned GL4ES source checkout with the MIT LICENSE file")
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[1]
    engine_root = args.engine_root.resolve()
    gl4es_root = args.gl4es_root.resolve()
    gl4es_license_root = args.gl4es_license_root.resolve()
    module = engine_root / "app_pojavlauncher"
    java_root = module / "src" / "main" / "java"
    gradle_file = module / "build.gradle"
    manifest_file = module / "src" / "main" / "AndroidManifest.xml"

    if not (engine_root / "settings.gradle").is_file():
        raise SystemExit(f"Not an Amethyst-Android Gradle checkout: {engine_root}")
    for required in (gradle_file, manifest_file):
        if not required.is_file():
            raise SystemExit(f"Required upstream file is missing: {required}")

    voidcraft_sources = repo_root / "app" / "src" / "main" / "java" / "com" / "voidcraft" / "launcher"
    if not voidcraft_sources.is_dir():
        raise SystemExit(f"Voidcraft source directory is missing: {voidcraft_sources}")

    destination = java_root / "com" / "voidcraft" / "launcher"
    if destination.exists():
        shutil.rmtree(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copytree(voidcraft_sources, destination)
    print(f"Overlaid {len(list(destination.glob('*.kt')))} Voidcraft Kotlin source files into {destination}")

    install_holy_gl4es(engine_root, gl4es_root, gl4es_license_root)
    patch_holy_gl4es_renderer(engine_root)

    gradle = gradle_file.read_text(encoding="utf-8")
    gradle = replace_once(gradle, UPSTREAM_PLUGIN_BLOCK, VOIDCRAFT_PLUGIN_BLOCK, "application Kotlin/Compose plugins")
    gradle = replace_once(
        gradle,
        """    buildFeatures {
        prefab true
        buildConfig true
    }""",
        """    buildFeatures {
        prefab true
        buildConfig true
        compose true
    }""",
        "Compose build feature"
    )

    dependency_anchor = '    implementation project(":androidnsbypass")\n\n}'
    dependency_block = """    implementation project(":androidnsbypass")

    // Voidcraft frontend: versions are aligned with the current stable Compose BOM.
    implementation platform('androidx.compose:compose-bom:2026.09.00')
    implementation 'androidx.activity:activity-compose:1.13.0'
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.ui:ui-graphics'
    implementation 'androidx.compose.ui:ui-tooling-preview'
    implementation 'androidx.compose.material3:material3'
    implementation 'androidx.compose.material:material-icons-extended'
    implementation files(criticalNativeStubJar).builtBy("packageCriticalNativeStub")

}"""
    gradle = replace_once(gradle, dependency_anchor, dependency_block, "Compose/runtime dependencies")
    critical_native_tasks = """def criticalNativeStubSource = file("src/main/java/dalvik/annotation/optimization/CriticalNative.java")
def criticalNativeStubClasses = file("$buildDir/generated/criticalNativeStub/classes")
def criticalNativeStubJar = file("$buildDir/generated/criticalNativeStub/critical-native-stub.jar")

tasks.register("compileCriticalNativeStub", Exec) {
    inputs.file(criticalNativeStubSource)
    outputs.dir(criticalNativeStubClasses)
    doFirst {
        if (!criticalNativeStubSource.isFile()) {
            throw new GradleException("Pinned engine CriticalNative source is missing")
        }
        def java8Home = System.getenv("JAVA_HOME_8_X64")
        if (!java8Home) {
            throw new GradleException("Java 8 toolchain is required for CriticalNative")
        }
        delete criticalNativeStubClasses
        criticalNativeStubClasses.mkdirs()
        commandLine new File(java8Home, "bin/javac").absolutePath,
            "-source", "8", "-target", "8", "-d", criticalNativeStubClasses.absolutePath,
            criticalNativeStubSource.absolutePath
    }
}

tasks.register("packageCriticalNativeStub", Exec) {
    dependsOn("compileCriticalNativeStub")
    inputs.dir(criticalNativeStubClasses)
    outputs.file(criticalNativeStubJar)
    doFirst {
        criticalNativeStubJar.parentFile.mkdirs()
        criticalNativeStubJar.delete()
        def java8Home = System.getenv("JAVA_HOME_8_X64")
        if (!java8Home) {
            throw new GradleException("Java 8 toolchain is required for CriticalNative")
        }
        commandLine new File(java8Home, "bin/jar").absolutePath,
            "cf", criticalNativeStubJar.absolutePath,
            "-C", criticalNativeStubClasses.absolutePath, "."
    }
}
"""
    gradle = replace_once(
        gradle,
        "\n}\n\ndependencies {",
        "\n}\n\n" + critical_native_tasks + "\ndependencies {",
        "Java 8 CriticalNative stub task and dependency ordering"
    )
    gradle = replace_once(
        gradle,
        """    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }""",
        """    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }

    sourceSets {
        main {
            java {
                exclude "dalvik/annotation/optimization/CriticalNative.java"
            }
        }
    }

    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
        }
    }""",
        "Java/Kotlin 11 targets with Java 8 CriticalNative stub"
    )

    # Give the integrated build Voidcraft's existing application identity and branded label.
    replacements = [
        ("applicationId \"org.angelauramc.amethyst\"", 'applicationId "com.voidcraft.launcher"'),
        ("applicationIdSuffix '.debug'", ""),
        ("org.angelauramc.amethyst", "com.voidcraft.launcher"),
        ("com.voidcraft.launcher.debug'", "com.voidcraft.launcher'"),
        ("com.voidcraft.launcher.scoped.gamefolder.debug'", "com.voidcraft.launcher.scoped.gamefolder'"),
        ("com.voidcraft.launcher.scoped.controlfolder.debug'", "com.voidcraft.launcher.scoped.controlfolder'"),
        ('resValue "string", "app_name", "Amethyst (Debug)"', 'resValue "string", "app_name", "Voidcraft"'),
        ('resValue "string", "app_short_name", "Amethyst (Debug)"', 'resValue "string", "app_short_name", "Voidcraft"'),
        ('resValue "string", "app_name", "Amethyst"', 'resValue "string", "app_name", "Voidcraft"'),
        ('resValue "string", "app_short_name", "Amethyst"', 'resValue "string", "app_short_name", "Voidcraft"'),
        ("minSdkVersion 21", "minSdkVersion 26"),
        ("versionCode 10000000", "versionCode 1"),
        ("versionName getVersionName()", 'versionName "0.1.0"'),
    ]
    for old, new in replacements:
        if old in gradle:
            gradle = gradle.replace(old, new)
    if 'applicationId "com.voidcraft.launcher"' not in gradle:
        raise RuntimeError("Failed to set the integrated application ID to com.voidcraft.launcher")
    if "applicationIdSuffix '.debug'" in gradle:
        raise RuntimeError("Debug application suffix is still present")

    # Preserve the upstream first-run storage bootstrap, then show Voidcraft.
    test_storage_file = java_root / "net" / "kdt" / "pojavlaunch" / "TestStorageActivity.java"
    if not test_storage_file.is_file():
        raise SystemExit(f"Upstream storage bootstrap is missing: {test_storage_file}")
    test_storage = test_storage_file.read_text(encoding="utf-8")
    test_storage = replace_once(
        test_storage,
        "Intent intent =  new Intent(this, LauncherActivity.class);",
        'Intent intent = new Intent();\n        intent.setClassName(this, "com.voidcraft.launcher.MainActivity");',
        "post-storage-bootstrap destination"
    )
    test_storage_file.write_text(test_storage, encoding="utf-8")

    # Hand off a version chosen in Voidcraft to the engine's active profile when its
    # integrity-checked client files have already been prepared in engine storage.
    launcher_activity_file = java_root / "net" / "kdt" / "pojavlaunch" / "LauncherActivity.java"
    if not launcher_activity_file.is_file():
        raise SystemExit(f"Upstream launcher activity is missing: {launcher_activity_file}")
    launcher_activity = launcher_activity_file.read_text(encoding="utf-8")
    launcher_activity = patch_launcher_activity(launcher_activity)
    launcher_activity_file.write_text(launcher_activity, encoding="utf-8")

    # The upstream gradle.properties deliberately disables AGP 9 built-in Kotlin.
    # Enable it for Voidcraft's Kotlin sources while keeping the upstream Java/native modules.
    properties_file = engine_root / "gradle.properties"
    if not properties_file.is_file():
        raise SystemExit(f"Upstream Gradle properties are missing: {properties_file}")
    properties = properties_file.read_text(encoding="utf-8")
    properties = replace_once(
        properties,
        "android.builtInKotlin=false",
        "android.builtInKotlin=true",
        "built-in Kotlin compiler switch"
    )
    properties_file.write_text(properties, encoding="utf-8")

    manifest = manifest_file.read_text(encoding="utf-8")
    manifest = replace_once(manifest, '        android:allowBackup="true"', '        android:allowBackup="false"', "application privacy setting")
    manifest = replace_once(manifest, '        <activity\n            android:name=".LauncherActivity"', VOIDCRAFT_ACTIVITY + '        <activity\n            android:name=".LauncherActivity"', "Voidcraft Activity")
    manifest_file.write_text(manifest, encoding="utf-8")
    gradle_file.write_text(gradle, encoding="utf-8")

    # Ship the upstream project's top-level license inside the combined APK.
    # The CI inventory remains a separate artifact because the dependency tree carries additional licenses.
    upstream_license = engine_root / "LICENSE"
    app_license_dir = module / "src" / "main" / "assets" / "licenses"
    if not upstream_license.is_file():
        raise SystemExit(f"Upstream LGPL license is missing: {upstream_license}")
    app_license_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(upstream_license, app_license_dir / "AMETHYST-LGPL-3.0.txt")

    # A short manifest makes the artifact traceable even after the workflow job has expired.
    metadata = engine_root / "VOIDCRAFT_ENGINE_INTEGRATION.txt"
    metadata.write_text(
        "Voidcraft integrated Android build\\n"
        "Engine: AngelAuraMC/Amethyst-Android\\n"
        "Upstream ref: 330c6eae3164df64bdc4828e946a9e62cc5169e4\\n"
        "Holy GL4ES binaries: PojavLauncherTeam/PojavLauncher@" + GL4ES_PINNED_SOURCE_COMMIT + "\\n"
        "Holy GL4ES MIT license source: PojavLauncherTeam/gl4es-114-extra@" + GL4ES_LICENSE_COMMIT + "\\n"
        "Integration: upstream storage/native bootstrap runs first, then Voidcraft Compose opens; PLAY hands off a prepared Minecraft version and renderer to the engine profile and opens the upstream launcher/runtime/renderers.\\n"
        "Build target: :app_pojavlauncher:assembleDebug\\n"
        "This debug artifact is a test build, not a verified public release.\\n",
        encoding="utf-8"
    )
    print("Preserved upstream storage bootstrap; PLAY hands off a prepared version and opens net.kdt.pojavlaunch.LauncherActivity.")

if __name__ == "__main__":
    main()
