#!/usr/bin/env python3
"""Prepare the pinned Amethyst Android application module for in-process library hosting.

This modifies only the checked-out submodule worktree during CI; the upstream commit remains pinned.
"""
from pathlib import Path
import re
import subprocess
import sys
import zipfile

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
    "        targetSdkVersion 34\n",
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
source, replacements = re.subn(
    r"(?m)^    defaultConfig \{[ \t]*$",
    """    defaultConfig {
        buildConfigField "String", "VERSION_NAME", '"' + getVersionName() + '"'
""",
    source,
    count=1,
)
if replacements != 1:
    fail("upstream defaultConfig block changed; cannot add library VERSION_NAME")

# Resource shrinking is an APK-only operation and AGP rejects it on library modules.
source = re.sub(r"(?m)^\s*shrinkResources\s+true\s*$\n?", "", source)
# Code shrinking is performed by the final application, not by an Android library.
source = source.replace("            minifyEnabled true\n", "            minifyEnabled false\n", 1)
if "shrinkResources true" in source or "minifyEnabled true" in source:
    fail("APK-only shrinking remains enabled in the converted Android library")

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

# A library AAR cannot itself depend on local AAR files. Move those runtime
# dependencies to Flame's final application module, while exposing their classes
# to this library's compiler via extracted classes.jar files.
aar_dependency = "    implementation fileTree(dir: 'libs', include: ['*.jar', '*.aar'])"
if source.count(aar_dependency) != 1:
    fail("upstream local AAR dependency declaration changed; review packaging migration")
source = source.replace(
    aar_dependency,
    "    implementation fileTree(dir: 'libs', include: ['*.jar'])\\n"
    + '    compileOnly fileTree(dir: "$buildDir/flame-aar-compile-libs", include: [\'*.jar\'])',
    1,
)

native_packaging = "            pickFirsts += ['**/libbytehook.so']\\n"
if source.count(native_packaging) != 1:
    fail("upstream JNI packaging block changed; cannot assign androidnsbypass ownership")
source = source.replace(
    native_packaging,
    native_packaging + "            excludes += ['**/libandroidnsbypass.so']\\n",
    1,
)

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
    build_features_block.replace("        buildConfig true\n", "        buildConfig true\n        resValues true\n"),
    1,
)

# Local AARs cannot be nested inside an Android library AAR under AGP 9.3.1.
# Keep local JARs with the engine module, but resolve AARs directly in Flame's APK.
local_aar_filetree = "implementation fileTree(dir: 'libs', include: ['*.jar', '*.aar'])"
local_jar_filetree = "implementation fileTree(dir: 'libs', include: ['*.jar'])"
if source.count(local_aar_filetree) != 1:
    fail("upstream local JAR/AAR fileTree changed; review the AAR dependency relocation")
source = source.replace(local_aar_filetree, local_jar_filetree, 1)

if "com.android.application" in source:
    fail("application plugin remains after library conversion")
for pattern, label in (
    (r"(?m)^\s*applicationId\s+", "applicationId"),
    (r"(?m)^\s*applicationIdSuffix\s+", "applicationIdSuffix"),
    (r"(?m)^\s*targetSdkVersion\s+", "targetSdkVersion"),
    (r"(?m)^\s*versionCode\s+", "versionCode"),
    (r"(?m)^\s*versionName\s+", "versionName"),
    (r"(?m)^\s*multiDexEnabled\b", "multiDexEnabled"),
    (r"(?m)^\s*signingConfigs\s*\{", "signingConfigs"),
    (r"(?m)^\s*bundle\s*\{", "bundle"),
):
    if re.search(pattern, source):
        fail(f"application-only Android DSL remains after conversion: {label}")
if source == original:
    fail("no expected upstream Gradle changes were applied")

app_gradle.write_text(source, encoding="utf-8")

# Extract AAR compile classes for the library module. The original AARs are
# attached directly to Flame's application module, where AGP can merge their
# manifests, resources, assets, and native payloads correctly.
aar_lib_dir = app_gradle.parent / "libs"
compile_lib_dir = app_gradle.parent / "build" / "flame-aar-compile-libs"
compile_lib_dir.mkdir(parents=True, exist_ok=True)
aar_files = sorted(aar_lib_dir.glob("*.aar"))
if not aar_files:
    fail("expected upstream local AAR dependencies but found none")
extracted_count = 0
for aar_path in aar_files:
    try:
        with zipfile.ZipFile(aar_path) as archive:
            jar_members = [
                member for member in archive.namelist()
                if member == "classes.jar" or (
                    member.startswith("libs/") and member.endswith(".jar")
                )
            ]
            for member in jar_members:
                suffix = Path(member).name
                output = compile_lib_dir / f"{aar_path.stem}-{suffix}"
                output.write_bytes(archive.read(member))
                extracted_count += 1
    except (OSError, zipfile.BadZipFile) as error:
        fail(f"could not inspect local AAR {aar_path.name}: {error}")
if extracted_count == 0:
    fail("no compile-time JARs were found inside Amethyst's local AAR dependencies")

# Android library R fields are non-constant by design. Convert the six resource-ID
# switch labels in this upstream activity to equivalent comparisons so javac can
# compile the library without changing touch behavior.
touch_java = ENGINE / "app_pojavlauncher" / "src" / "main" / "java" / "net" / "kdt" / "pojavlaunch" / "JavaGUILauncherActivity.java"
if not touch_java.is_file():
    fail("upstream JavaGUILauncherActivity.java is missing")
touch_source = touch_java.read_text(encoding="utf-8")
mouse_switch = """        switch (v.getId()) {
            case R.id.installmod_mouse_pri:
                AWTInputBridge.sendMousePress(AWTInputEvent.BUTTON1_DOWN_MASK, isDown);
                break;
                
            case R.id.installmod_mouse_sec:
                AWTInputBridge.sendMousePress(AWTInputEvent.BUTTON3_DOWN_MASK, isDown);
                break;
        }
"""
mouse_if = """        if (v.getId() == R.id.installmod_mouse_pri) {
            AWTInputBridge.sendMousePress(AWTInputEvent.BUTTON1_DOWN_MASK, isDown);
        } else if (v.getId() == R.id.installmod_mouse_sec) {
            AWTInputBridge.sendMousePress(AWTInputEvent.BUTTON3_DOWN_MASK, isDown);
        }
"""
window_switch = """        if(isDown) switch(v.getId()) {
            case R.id.installmod_window_moveup:
                AWTInputBridge.nativeMoveWindow(0, -10);
                break;
            case R.id.installmod_window_movedown:
                AWTInputBridge.nativeMoveWindow(0, 10);
                break;
            case R.id.installmod_window_moveleft:
                AWTInputBridge.nativeMoveWindow(-10, 0);
                break;
            case R.id.installmod_window_moveright:
                AWTInputBridge.nativeMoveWindow(10, 0);
                break;
        }
"""
window_if = """        if (isDown) {
            if (v.getId() == R.id.installmod_window_moveup) {
                AWTInputBridge.nativeMoveWindow(0, -10);
            } else if (v.getId() == R.id.installmod_window_movedown) {
                AWTInputBridge.nativeMoveWindow(0, 10);
            } else if (v.getId() == R.id.installmod_window_moveleft) {
                AWTInputBridge.nativeMoveWindow(-10, 0);
            } else if (v.getId() == R.id.installmod_window_moveright) {
                AWTInputBridge.nativeMoveWindow(10, 0);
            }
        }
"""
if touch_source.count(mouse_switch) != 1:
    fail("upstream mouse-button switch changed; review the non-final resource ID patch")
if touch_source.count(window_switch) != 1:
    fail("upstream window-move switch changed; review the non-final resource ID patch")
touch_source = touch_source.replace(mouse_switch, mouse_if, 1)
touch_source = touch_source.replace(window_switch, window_if, 1)
touch_java.write_text(touch_source, encoding="utf-8")

# Align the nested Android native library with the engine's pinned NDK.
dns_gradle = ENGINE / "androidnsbypass" / "build.gradle.kts"
if dns_gradle.is_file():
    dns_source = dns_gradle.read_text(encoding="utf-8")
    if 'ndkVersion = "27.3.13750724"' not in dns_source:
        dns_source, replacements = re.subn(
            r"(?m)^android \{[ \t]*$",
            'android {\n    ndkVersion = "27.3.13750724"',
            dns_source,
            count=1,
        )
        if replacements != 1:
            fail("could not pin androidnsbypass to the engine NDK version")
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
    if gradle_file == app_gradle:
        continue
    text = gradle_file.read_text(encoding="utf-8")
    changed = text
    for old, new in project_paths:
        changed = changed.replace(old, new)
    # The upstream root build declares this as a static Groovy helper, which is
    # not inherited by nested project scripts. Call Flame's root extra-property
    # closure explicitly from each component task instead.
    changed = changed.replace(
        "writeVersionFile(jarFile, versionFile)",
        "rootProject.ext.writeVersionFile.invoke(jarFile, versionFile)",
    )
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
