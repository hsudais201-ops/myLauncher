#!/usr/bin/env python3
"""Prepare the pinned Amethyst Android application module for in-process library hosting.

This modifies only the checked-out submodule worktree during CI; the upstream commit remains pinned.
"""
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
ENGINE = ROOT / "engine" / "amethyst"
PINNED = "28d6e12d68121b7dd75f2e841058a6b7cdfe4a29"


def fail(message: str) -> None:
    print(f"Amethyst host preparation failed: {message}", file=sys.stderr)
    raise SystemExit(1)


if not ENGINE.is_dir() or not (ENGINE / ".git").exists():
    fail("engine/amethyst is not checked out. Initialize the pinned submodule first.")

actual = subprocess.check_output(
    ["git", "-C", str(ENGINE), "rev-parse", "HEAD"], text=True
).strip()
if actual != PINNED:
    fail(f"expected upstream commit {PINNED}, got {actual}")

app_gradle = ENGINE / "app_pojavlauncher" / "build.gradle"
if not app_gradle.is_file():
    fail("upstream app_pojavlauncher/build.gradle is missing")

source = app_gradle.read_text(encoding="utf-8")
original = source

source = source.replace(
    "id 'com.android.application' version '9.3.1'",
    "id 'com.android.library'",
    1,
)

# A library must not define an install identity or signing keys.
signing_block = """    signingConfigs {
        customDebug {
            storeFile file("debug.keystore")
            storePassword "android"
            keyAlias "androiddebugkey"
            keyPassword "android"
        }
        customRelease {
            storeFile file("aamc_upload.jks")
            storePassword System.getenv("GPLAY_KEYSTORE_PASSWORD")
            keyAlias "android_github_release"
            keyPassword System.getenv("GPLAY_KEYSTORE_PASSWORD")
        }
        googlePlayBuild {
            storeFile file("upload.jks")
            storePassword System.getenv("GPLAY_KEYSTORE_PASSWORD")
            keyAlias "upload"
            keyPassword System.getenv("GPLAY_KEYSTORE_PASSWORD")
        }
    }

"""
if signing_block not in source:
    fail("upstream signingConfigs block changed; review the integration patch")
source = source.replace(signing_block, "", 1)

for line in (
    '        applicationId "org.angelauramc.amethyst"\n',
    "        versionCode 10000000\n",
    "        versionName getVersionName()\n",
    "        multiDexEnabled true //important\n",
    "            applicationIdSuffix '.debug'\n",
    "            signingConfig signingConfigs.customDebug\n",
    "            signingConfig signingConfigs.customRelease\n",
    "            signingConfig signingConfigs.googlePlayBuild\n",
):
    source = source.replace(line, "")

bundle_block = """    bundle {
        language {
            enableSplit = false
        }
    }

"""
if bundle_block not in source:
    fail("upstream bundle configuration changed; review the integration patch")
source = source.replace(bundle_block, "", 1)

# The Android library plugin has no application versionName; keep the value used by
# Amethyst's crash reporting in the generated library BuildConfig.
default_config_marker = "    defaultConfig {\\n"
if default_config_marker not in source:
    fail("upstream defaultConfig block changed; cannot add library VERSION_NAME")
source = source.replace(
    default_config_marker,
    default_config_marker + '        buildConfigField "String", "VERSION_NAME", "\\\"\${getVersionName()}\\\""\\n',
    1,
)

# Resource shrinking is an APK-only operation and AGP rejects it on library modules.
source = source.replace("            shrinkResources true\\n", "")
if "shrinkResources true" in source:
    fail("resource shrinking remains enabled in the converted Android library")

# Resource authorities must be unique to Flame, not to a separately installed Amethyst app.
source = source.replace("org.angelauramc.amethyst.debug", "com.flame.launcher")
source = source.replace("org.angelauramc.amethyst", "com.flame.launcher")
source = source.replace("Amethyst (Debug)", "Flame Launcher")
source = source.replace('"Amethyst"', '"Flame Launcher"')

# All engine Gradle projects live under the :engine namespace in Flame's composite project.
for old, new in (
    (":forge_installer", ":engine:forge_installer"),
    (":arc_dns_injector", ":engine:arc_dns_injector"),
    (":MioLibPatcher", ":engine:MioLibPatcher"),
    (":jre_lwjgl3glfw", ":engine:jre_lwjgl3glfw"),
    (":androidnsbypass", ":engine:androidnsbypass"),
    (":app_pojavlauncher", ":engine:app_pojavlauncher"),
):
    source = source.replace(old, new)

source = source.replace(
    "apply from: rootProject.file('gradle/prefab_bypass.gradle')",
    "apply from: file('../gradle/prefab_bypass.gradle')",
)
build_features_block = """    buildFeatures {
        prefab true
        buildConfig true
    }
"""
if build_features_block not in source:
    fail("upstream buildFeatures block changed; review the library conversion")
source = source.replace(
    build_features_block,
    build_features_block.replace("        buildConfig true\\n", "        buildConfig true\\n        resValues true\\n"),
    1,
)

if "com.android.application" in source or "applicationId " in source or "signingConfigs" in source:
    fail("application-only Android DSL remains after conversion")
if source == original:
    fail("no expected upstream Gradle changes were applied")

app_gradle.write_text(source, encoding="utf-8")

# Align the nested Android native library with the engine's pinned NDK.
dns_gradle = ENGINE / "androidnsbypass" / "build.gradle.kts"
if dns_gradle.is_file():
    dns_source = dns_gradle.read_text(encoding="utf-8")
    if 'ndkVersion = "27.3.13750724"' not in dns_source:
        dns_source = dns_source.replace(
            "android {\\n",
            'android {\\n    ndkVersion = "27.3.13750724"\\n',
            1,
        )
        dns_gradle.write_text(dns_source, encoding="utf-8")

# Patch project paths in every engine Gradle file, including the LWJGL aggregator.
# The engine is nested under :engine in Flame's settings.gradle.kts.
project_paths = (
    (":app_pojavlauncher", ":engine:app_pojavlauncher"),
    (":jre_lwjgl3glfw", ":engine:jre_lwjgl3glfw"),
    (":forge_installer", ":engine:forge_installer"),
    (":arc_dns_injector", ":engine:arc_dns_injector"),
    (":MioLibPatcher", ":engine:MioLibPatcher"),
    (":androidnsbypass", ":engine:androidnsbypass"),
)
for gradle_file in ENGINE.rglob("build.gradle"):
    text = gradle_file.read_text(encoding="utf-8")
    changed = text
    for old, new in project_paths:
        changed = changed.replace(old, new)
    if changed != text:
        gradle_file.write_text(changed, encoding="utf-8")

# Fail early if any of the upstream app-only DSL or un-namespaced project paths remain.
prepared = app_gradle.read_text(encoding="utf-8")
for forbidden in ("com.android.application", "applicationId ", "signingConfigs"):
    if forbidden in prepared:
        fail(f"unconverted upstream app Gradle token remains: {forbidden}")
for stale_path in (
    ":app_pojavlauncher",
    ":forge_installer",
    ":arc_dns_injector",
    ":MioLibPatcher",
    ":jre_lwjgl3glfw",
    ":androidnsbypass",
):
    if re.search(rf"(?<!engine){re.escape(stale_path)}", prepared):
        fail(f"un-namespaced upstream Gradle project path remains: {stale_path}")

lwjgl_gradle = ENGINE / "jre_lwjgl3glfw" / "build.gradle"
if lwjgl_gradle.exists() and ":engine:jre_lwjgl3glfw:" not in lwjgl_gradle.read_text(encoding="utf-8"):
    fail("LWJGL aggregator still references an un-namespaced Gradle project")

print(f"Prepared Amethyst {actual} as an Android library module for Flame.")
