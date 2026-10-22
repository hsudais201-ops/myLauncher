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

def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"Expected exactly one upstream anchor for {label}; found {count}. The engine source likely changed.")
    return source.replace(old, new, 1)

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("engine_root", type=Path, help="Path to a checked-out Amethyst-Android source tree")
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[1]
    engine_root = args.engine_root.resolve()
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

}"""
    gradle = replace_once(gradle, dependency_anchor, dependency_block, "Compose/runtime dependencies")
    gradle = replace_once(
        gradle,
        """    compileOptions {
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }""",
        """    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }""",
        "Java 11 bytecode target for Compose"
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
        "Integration: upstream storage/native bootstrap runs first, then Voidcraft Compose opens; PLAY opens the upstream launcher/runtime/renderers.\\n"
        "Build target: :app_pojavlauncher:assembleDebug\\n"
        "This debug artifact is a test build, not a verified public release.\\n",
        encoding="utf-8"
    )
    print("Preserved upstream storage bootstrap, then opened Voidcraft; PLAY opens net.kdt.pojavlaunch.LauncherActivity.")

if __name__ == "__main__":
    main()
