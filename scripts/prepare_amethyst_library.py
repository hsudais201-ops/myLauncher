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
    "id 'com.android.library' version '9.3.1'",
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
if "com.android.application" in source or "applicationId " in source or "signingConfigs" in source:
    fail("application-only Android DSL remains after conversion")
if source == original:
    fail("no expected upstream Gradle changes were applied")

app_gradle.write_text(source, encoding="utf-8")

# Patch project paths in the other upstream Gradle files without changing Java/native sources.
for gradle_file in ENGINE.rglob("build.gradle"):
    if gradle_file == app_gradle:
        continue
    text = gradle_file.read_text(encoding="utf-8")
    changed = text
    for old, new in (
        (":app_pojavlauncher", ":engine:app_pojavlauncher"),
        (":jre_lwjgl3glfw", ":engine:jre_lwjgl3glfw"),
        (":forge_installer", ":engine:forge_installer"),
        (":arc_dns_injector", ":engine:arc_dns_injector"),
        (":MioLibPatcher", ":engine:MioLibPatcher"),
        (":androidnsbypass", ":engine:androidnsbypass"),
    ):
        changed = changed.replace(old, new)
    if changed != text:
        gradle_file.write_text(changed, encoding="utf-8")

print(f"Prepared Amethyst {actual} as an Android library module for Flame.")
