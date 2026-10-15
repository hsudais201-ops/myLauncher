#!/usr/bin/env python3
"""Apply deterministic myLauncher branding/package patches to Amethyst-Android."""
from __future__ import annotations

import re
import sys
from pathlib import Path


def replace_idempotently(text: str, old: str, new: str, label: str) -> str:
    """Replace a token, allow repeat runs, and fail on unexpected upstream changes."""
    if old in text:
        return text.replace(old, new)
    if new in text:
        return text
    raise ValueError(f"Could not find expected token in {label}: {old!r} or {new!r}")


def patch_upstream(upstream: Path) -> None:
    gradle = upstream / "app_pojavlauncher" / "build.gradle"
    strings = upstream / "app_pojavlauncher" / "src" / "main" / "res" / "values" / "strings.xml"
    settings = upstream / "settings.gradle"
    for path in (gradle, strings, settings):
        if not path.is_file():
            raise FileNotFoundError(f"Expected upstream file is missing: {path}")

    build_text = gradle.read_text(encoding="utf-8")
    build_text = replace_idempotently(
        build_text, "org.angelauramc.amethyst", "com.mf.mylauncher", str(gradle)
    )
    build_text = replace_idempotently(build_text, "Amethyst", "myLauncher", str(gradle))
    min_sdk = re.search(r"\bminSdkVersion\s+(\d+)\b", build_text)
    if not min_sdk:
        raise ValueError("Could not verify minSdkVersion; refusing an unverified Android 9 build")
    if int(min_sdk.group(1)) > 28:
        raise ValueError(f"Upstream minSdkVersion {min_sdk.group(1)} is above Android 9/API 28")
    if 'applicationId "com.mf.mylauncher"' not in build_text:
        raise ValueError("The patched Gradle file has no expected myLauncher applicationId")
    if "org.angelauramc.amethyst" in build_text:
        raise ValueError("An upstream application ID/authority remains in build.gradle")
    gradle.write_text(build_text, encoding="utf-8")

    strings_text = strings.read_text(encoding="utf-8")
    strings_text = strings_text.replace("org.angelauramc.amethyst", "com.mf.mylauncher")
    strings_text = replace_idempotently(strings_text, "Amethyst", "myLauncher", str(strings))
    for key in ("app_name", "app_short_name"):
        if not re.search(
            rf'<string\s+name="{re.escape(key)}"[^>]*>\s*myLauncher\s*</string>',
            strings_text,
            flags=re.DOTALL,
        ):
            raise ValueError(f"The patched strings.xml is missing {key}=myLauncher")
    if "org.angelauramc.amethyst" in strings_text:
        raise ValueError("An upstream application authority remains in strings.xml")
    strings.write_text(strings_text, encoding="utf-8")

    settings_text = settings.read_text(encoding="utf-8")
    settings_text = replace_idempotently(settings_text, "Amethyst", "myLauncher", str(settings))
    if not re.search(r"rootProject\.name\s*=\s*['\"]myLauncher['\"]", settings_text):
        raise ValueError("Gradle rootProject.name was not customized")
    settings.write_text(settings_text, encoding="utf-8")

    launcher_preferences = upstream / "app_pojavlauncher" / "src" / "main" / "java" / "net" / "kdt" / "pojavlaunch" / "prefs" / "LauncherPreferences.java"
    if not launcher_preferences.is_file():
        raise FileNotFoundError(f"Expected upstream file is missing: {launcher_preferences}")
    preferences_text = launcher_preferences.read_text(encoding="utf-8")
    preferences_text = replace_idempotently(
        preferences_text,
        "int targetSide = isDevicePowerful ? 1080 : 720;",
        "int targetSide = isDevicePowerful ? 1080 : (Tools.getTotalDeviceMemory(context) <= 2048 ? 480 : 720);",
        str(launcher_preferences),
    )
    preferences_text = replace_idempotently(
        preferences_text,
        "if (deviceRam < 2048) return 656;",
        "if (deviceRam <= 2048) return 656;",
        str(launcher_preferences),
    )
    if "int targetSide = isDevicePowerful ? 1080 : 720;" in preferences_text:
        raise ValueError("Low-memory resolution target was not applied")
    if "if (deviceRam < 2048) return 656;" in preferences_text:
        raise ValueError("The 2 GB RAM allocation boundary was not corrected")
    launcher_preferences.write_text(preferences_text, encoding="utf-8")

    print("myLauncher patch OK")
    print("  app label: myLauncher")
    print("  application ID: com.mf.mylauncher (debug adds .debug)")
    print("  storage/share authorities: customized")
    print("  low-memory defaults: 656 MB at <=2 GB; ~480 px short-edge render target")


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(f"Usage: {Path(argv[0]).name} PATH_TO_AMETHYST_SOURCE", file=sys.stderr)
        return 2
    try:
        patch_upstream(Path(argv[1]).resolve())
    except (OSError, ValueError) as exc:
        print(f"myLauncher patch FAILED: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))