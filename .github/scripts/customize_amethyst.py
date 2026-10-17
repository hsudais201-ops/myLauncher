#!/usr/bin/env python3
"""Apply deterministic VoidCraft Launcher branding/package patches to Amethyst-Android."""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def replace_idempotently(text: str, old: str, new: str, label: str) -> str:
    """Replace a token, allow repeat runs, and fail on unexpected upstream changes."""
    if old in text:
        return text.replace(old, new)
    if new in text:
        return text
    raise ValueError(f"Could not find expected token in {label}: {old!r} or {new!r}")


def validate_xml_files(paths: tuple[Path, ...]) -> None:
    """Fail early when a generated Android resource is not well-formed XML."""
    for path in paths:
        try:
            ET.parse(path)
        except ET.ParseError as exc:
            raise ValueError(f"Generated Android XML is invalid at {path}: {exc}") from exc


def patch_upstream(upstream: Path) -> None:
    gradle = upstream / "app_pojavlauncher" / "build.gradle"
    strings = upstream / "app_pojavlauncher" / "src" / "main" / "res" / "values" / "strings.xml"
    colors = upstream / "app_pojavlauncher" / "src" / "main" / "res" / "values" / "colors.xml"
    settings = upstream / "settings.gradle"
    for path in (gradle, strings, colors, settings):
        if not path.is_file():
            raise FileNotFoundError(f"Expected upstream file is missing: {path}")

    build_text = gradle.read_text(encoding="utf-8")
    build_text = replace_idempotently(
        build_text, "org.angelauramc.amethyst", "com.mf.mylauncher", str(gradle)
    )
    build_text = replace_idempotently(build_text, "Amethyst", "VoidCraft Launcher", str(gradle))
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
    strings_text = replace_idempotently(strings_text, "Amethyst", "VoidCraft Launcher", str(strings))
    if not re.search(
        r'<string\s+name="app_name"[^>]*>\s*VoidCraft Launcher\s*</string>',
        strings_text,
        flags=re.DOTALL,
    ):
        raise ValueError("The patched strings.xml is missing app_name=VoidCraft Launcher")
    # Upstream defines app_short_name through Gradle resValue, not strings.xml.
    required_gradle_values = (
        '"app_name", "VoidCraft Launcher (Debug)"',
        '"app_name", "VoidCraft Launcher"',
        '"app_short_name", "VoidCraft Launcher (Debug)"',
        '"app_short_name", "VoidCraft Launcher"',
        "'application_package', 'com.mf.mylauncher.debug'",
        "'application_package', 'com.mf.mylauncher'",
        "'storageProviderAuthorities', 'com.mf.mylauncher.scoped.gamefolder.debug'",
        "'storageProviderAuthorities', 'com.mf.mylauncher.scoped.gamefolder'",
        "'shareProviderAuthority', 'com.mf.mylauncher.scoped.controlfolder.debug'",
    )
    missing_values = [value for value in required_gradle_values if value not in build_text]
    if missing_values:
        raise ValueError(f"Gradle app labels or authorities are incomplete: {missing_values}")
    if "org.angelauramc.amethyst" in strings_text:
        raise ValueError("An upstream application authority remains in strings.xml")
    if not re.search(r'<string\s+name="main_add_account"', strings_text):
        raise ValueError("Required main_add_account string is missing from strings.xml")
    if not re.search(r'<string\s+name="main_mods_folder"', strings_text):
        account_string = re.search(
            r'(?m)^([ \t]*<string\s+name="main_add_account"[^>]*>.*?</string>)[ \t]*$',
            strings_text,
        )
        if not account_string:
            raise ValueError("Could not find main_add_account to add the mods-folder label")
        indent = re.match(r"[ \t]*", account_string.group(1)).group(0)
        mods_string = indent + '<string name="main_mods_folder">Mods folder</string>'
        strings_text = strings_text[:account_string.end()] + "\n" + mods_string + strings_text[account_string.end():]
    if not re.search(r'<string\s+name="main_mods_folder"[^>]*>\s*Mods folder\s*</string>', strings_text):
        raise ValueError("Required main_mods_folder string is missing or invalid")
    if not re.search(r'<string\s+name="main_mods_folder_hint"', strings_text):
        mods_label_match = re.search(
            r'(?m)^([ \t]*<string\s+name="main_mods_folder"[^>]*>.*?</string>)[ \t]*$',
            strings_text,
        )
        if not mods_label_match:
            raise ValueError("Could not find main_mods_folder to add the mod-manager accessibility hint")
        indent = re.match(r"[ \t]*", mods_label_match.group(1)).group(0)
        mods_hint = indent + '<string name="main_mods_folder_hint">Mods folder. Long-press to enable or disable JAR mods.</string>'
        strings_text = strings_text[:mods_label_match.end()] + "\n" + mods_hint + strings_text[mods_label_match.end():]
    if not re.search(r'<string\s+name="main_mods_folder_hint"[^>]*>\s*Mods folder\. Long-press to enable or disable JAR mods\.\s*</string>', strings_text):
        raise ValueError("Required main_mods_folder_hint accessibility text is missing or invalid")
    if not re.search(r'<string\s+name="main_resourcepacks_folder"', strings_text):
        mods_string_match = re.search(
            r'(?m)^([ \t]*<string\s+name="main_mods_folder"[^>]*>.*?</string>)[ \t]*$',
            strings_text,
        )
        if not mods_string_match:
            raise ValueError("Could not find main_mods_folder to add the resource-packs label")
        indent = re.match(r"[ \t]*", mods_string_match.group(1)).group(0)
        resourcepacks_string = indent + '<string name="main_resourcepacks_folder">Resource packs</string>'
        strings_text = strings_text[:mods_string_match.end()] + "\n" + resourcepacks_string + strings_text[mods_string_match.end():]
    if not re.search(r'<string\s+name="main_resourcepacks_folder"[^>]*>\s*Resource packs\s*</string>', strings_text):
        raise ValueError("Required main_resourcepacks_folder string is missing or invalid")
    if not re.search(r'<string\s+name="main_resourcepacks_folder_hint"', strings_text):
        resourcepacks_label_match = re.search(
            r'(?m)^([ \t]*<string\s+name="main_resourcepacks_folder"[^>]*>.*?</string>)[ \t]*$',
            strings_text,
        )
        if not resourcepacks_label_match:
            raise ValueError("Could not find main_resourcepacks_folder to add the resource-pack toggle hint")
        indent = re.match(r"[ \t]*", resourcepacks_label_match.group(1)).group(0)
        resourcepacks_hint = indent + '<string name="main_resourcepacks_folder_hint">Resource packs. Long-press to enable or disable ZIP packs.</string>'
        strings_text = strings_text[:resourcepacks_label_match.end()] + "\n" + resourcepacks_hint + strings_text[resourcepacks_label_match.end():]
    if not re.search(r'<string\s+name="main_resourcepacks_folder_hint"[^>]*>\s*Resource packs\. Long-press to enable or disable ZIP packs\.\s*</string>', strings_text):
        raise ValueError("Required main_resourcepacks_folder_hint text is missing or invalid")

    if not re.search(r'<string\s+name="main_world_saves_folder"', strings_text):
        resourcepacks_string_match = re.search(
            r'(?m)^([ \t]*<string\s+name="main_resourcepacks_folder"[^>]*>.*?</string>)[ \t]*$',
            strings_text,
        )
        if not resourcepacks_string_match:
            raise ValueError("Could not find main_resourcepacks_folder to add the world-saves label")
        indent = re.match(r"[ \t]*", resourcepacks_string_match.group(1)).group(0)
        saves_string = indent + '<string name="main_world_saves_folder">World saves</string>'
        strings_text = strings_text[:resourcepacks_string_match.end()] + "\n" + saves_string + strings_text[resourcepacks_string_match.end():]
    if not re.search(r'<string\s+name="main_world_saves_folder"[^>]*>\s*World saves\s*</string>', strings_text):
        raise ValueError("Required main_world_saves_folder string is missing or invalid")
    if not re.search(r'<string\s+name="main_world_saves_backup_hint"', strings_text):
        world_saves_label_match = re.search(
            r'(?m)^([ \t]*<string\s+name="main_world_saves_folder"[^>]*>.*?</string>)[ \t]*$',
            strings_text,
        )
        if not world_saves_label_match:
            raise ValueError("Could not find the World saves label to add backup help")
        indent = re.match(r"[ \t]*", world_saves_label_match.group(1)).group(0)
        backup_hint = indent + '<string name="main_world_saves_backup_hint">World saves. Long-press to create a ZIP backup.</string>'
        strings_text = strings_text[:world_saves_label_match.end()] + "\n" + backup_hint + strings_text[world_saves_label_match.end():]
    if not re.search(r'<string\s+name="main_world_saves_backup_hint"[^>]*>\s*World saves\. Long-press to create a ZIP backup\.\s*</string>', strings_text):
        raise ValueError("World saves backup accessibility hint is missing or invalid")
    restore_strings = {
        "main_world_saves_open_folder": "Open saves folder",
        "main_world_backup_restore": "Restore a ZIP backup",
        "main_world_backup_picker_title": "Choose a world backup",
        "main_world_backup_confirm_title": "Restore world backup?",
        "main_world_backup_confirm_message": "Existing worlds will not be overwritten. Name conflicts will be restored with a suffix.",
    }
    for string_name, string_value in restore_strings.items():
        if not re.search(r'<string\s+name="' + re.escape(string_name) + r'"', strings_text):
            hint_match = re.search(
                r'(?m)^([ \t]*<string\s+name="main_world_saves_backup_hint"[^>]*>.*?</string>)[ \t]*$',
                strings_text,
            )
            if not hint_match:
                raise ValueError("Could not find the world-backup accessibility hint to add restore strings")
            indent = re.match(r"[ \t]*", hint_match.group(1)).group(0)
            new_string = indent + '<string name="' + string_name + '">' + string_value + '</string>'
            strings_text = strings_text[:hint_match.end()] + "\n" + new_string + strings_text[hint_match.end():]
        if not re.search(
            r'<string\s+name="' + re.escape(string_name) + r'"[^>]*>\s*' + re.escape(string_value) + r'\s*</string>',
            strings_text,
        ):
            raise ValueError(f"World backup restore string is missing or invalid: {string_name}")
    if not re.search(r'<string\s+name="main_logs_folder"', strings_text):
        world_saves_string_match = re.search(
            r'(?m)^([ \t]*<string\s+name="main_world_saves_folder"[^>]*>.*?</string>)[ \t]*$',
            strings_text,
        )
        if not world_saves_string_match:
            raise ValueError("Could not find main_world_saves_folder to add the game-logs label")
        indent = re.match(r"[ \t]*", world_saves_string_match.group(1)).group(0)
        logs_string = indent + '<string name="main_logs_folder">Game logs</string>'
        strings_text = strings_text[:world_saves_string_match.end()] + "\n" + logs_string + strings_text[world_saves_string_match.end():]
    if not re.search(r'<string\s+name="main_logs_folder"[^>]*>\s*Game logs\s*</string>', strings_text):
        raise ValueError("Required main_logs_folder string is missing or invalid")
    if not re.search(r'<string\s+name="main_screenshots_folder"', strings_text):
        logs_string_match = re.search(
            r'(?m)^([ \t]*<string\s+name="main_logs_folder"[^>]*>.*?</string>)[ \t]*$',
            strings_text,
        )
        if not logs_string_match:
            raise ValueError("Could not find main_logs_folder to add the screenshots label")
        indent = re.match(r"[ \t]*", logs_string_match.group(1)).group(0)
        screenshots_string = indent + '<string name="main_screenshots_folder">Screenshots</string>'
        strings_text = strings_text[:logs_string_match.end()] + "\n" + screenshots_string + strings_text[logs_string_match.end():]
    if not re.search(r'<string\s+name="main_screenshots_folder"[^>]*>\s*Screenshots\s*</string>', strings_text):
        raise ValueError("Required main_screenshots_folder string is missing or invalid")
    if not re.search(r'<string\s+name="main_crash_reports_folder"', strings_text):
        screenshots_string_match = re.search(
            r'(?m)^([ \t]*<string\s+name="main_screenshots_folder"[^>]*>.*?</string>)[ \t]*$',
            strings_text,
        )
        if not screenshots_string_match:
            raise ValueError("Could not find main_screenshots_folder to add the crash-reports label")
        indent = re.match(r"[ \t]*", screenshots_string_match.group(1)).group(0)
        crash_reports_string = indent + '<string name="main_crash_reports_folder">Crash reports</string>'
        strings_text = strings_text[:screenshots_string_match.end()] + "\n" + crash_reports_string + strings_text[screenshots_string_match.end():]
    if not re.search(r'<string\s+name="main_crash_reports_folder"[^>]*>\s*Crash reports\s*</string>', strings_text):
        raise ValueError("Required main_crash_reports_folder string is missing or invalid")
    if not re.search(r'<string\s+name="main_config_folder"', strings_text):
        crash_reports_string_match = re.search(
            r'(?m)^([ \t]*<string\s+name="main_crash_reports_folder"[^>]*>.*?</string>)[ \t]*$',
            strings_text,
        )
        if not crash_reports_string_match:
            raise ValueError("Could not find main_crash_reports_folder to add the Config-folder label")
        indent = re.match(r"[ \t]*", crash_reports_string_match.group(1)).group(0)
        config_string = indent + '<string name="main_config_folder">Config folder</string>'
        strings_text = strings_text[:crash_reports_string_match.end()] + "\n" + config_string + strings_text[crash_reports_string_match.end():]
    if not re.search(r'<string\s+name="main_config_folder"[^>]*>\s*Config folder\s*</string>', strings_text):
        raise ValueError("Required main_config_folder string is missing or invalid")

    if not re.search(r'<string\s+name="main_shaderpacks_folder"', strings_text):
        screenshots_string_match = re.search(
            r'(?m)^([ \t]*<string\s+name="main_screenshots_folder"[^>]*>.*?</string>)[ \t]*$',
            strings_text,
        )
        if not screenshots_string_match:
            raise ValueError("Could not find main_screenshots_folder to add the shader-packs label")
        indent = re.match(r"[ \t]*", screenshots_string_match.group(1)).group(0)
        shaderpacks_string = indent + '<string name="main_shaderpacks_folder">Shader packs</string>'
        strings_text = strings_text[:screenshots_string_match.end()] + "\n" + shaderpacks_string + strings_text[screenshots_string_match.end():]
    if not re.search(r'<string\s+name="main_shaderpacks_folder"[^>]*>\s*Shader packs\s*</string>', strings_text):
        raise ValueError("Required main_shaderpacks_folder string is missing or invalid")
    if not re.search(r'<string\s+name="main_shaderpacks_folder_hint"', strings_text):
        shaderpacks_label_match = re.search(
            r'(?m)^([ \t]*<string\s+name="main_shaderpacks_folder"[^>]*>.*?</string>)[ \t]*$',
            strings_text,
        )
        if not shaderpacks_label_match:
            raise ValueError("Could not find main_shaderpacks_folder to add the shader-pack toggle hint")
        indent = re.match(r"[ \t]*", shaderpacks_label_match.group(1)).group(0)
        shaderpacks_hint = indent + '<string name="main_shaderpacks_folder_hint">Shader packs. Long-press to enable or disable ZIP packs.</string>'
        strings_text = strings_text[:shaderpacks_label_match.end()] + "\n" + shaderpacks_hint + strings_text[shaderpacks_label_match.end():]
    if not re.search(r'<string\s+name="main_shaderpacks_folder_hint"[^>]*>\s*Shader packs\. Long-press to enable or disable ZIP packs\.\s*</string>', strings_text):
        raise ValueError("Required main_shaderpacks_folder_hint text is missing or invalid")
    live_log_strings = {
        "main_live_log_filter_hint": "Filter live logs (case-insensitive)",
        "main_live_log_filter_clear": "Clear log filter",
        "main_live_log_count_all": "%1$d lines retained",
        "main_live_log_count_matches": "%1$d matching lines",
        "main_live_log_count_none": "No matching lines",
        "main_live_log_truncated": "[Older log lines omitted to bound memory use]",
    }
    for string_name, string_value in live_log_strings.items():
        if not re.search(r'<string\s+name="' + re.escape(string_name) + r'"', strings_text):
            shader_hint_match = re.search(
                r'(?m)^([ \t]*<string\s+name="main_shaderpacks_folder_hint"[^>]*>.*?</string>)[ \t]*$',
                strings_text,
            )
            if not shader_hint_match:
                raise ValueError("Could not find the shader-pack hint to add live-log filter resources")
            indent = re.match(r"[ \t]*", shader_hint_match.group(1)).group(0)
            new_string = indent + '<string name="' + string_name + '">' + string_value + '</string>'
            strings_text = strings_text[:shader_hint_match.end()] + "\n" + new_string + strings_text[shader_hint_match.end():]
        expected_value = re.escape(string_value)
        if not re.search(
            r'<string\s+name="' + re.escape(string_name) + r'"[^>]*>\s*' + expected_value + r'\s*</string>',
            strings_text,
        ):
            raise ValueError(f"Live log filter resource is missing or invalid: {string_name}")
    repair_version_strings = {
        "main_repair_version": "Verify / repair version",
        "main_repair_version_title": "Verify / repair selected version?",
        "main_repair_version_message": "Checks and downloads missing or corrupt files for the selected official Minecraft version. An internet connection is required. Minecraft will not launch afterward.",
        "main_repair_version_list_loading": "The Minecraft version list is still loading. Wait a moment and try again.",
        "main_repair_version_unlisted": "Automatic repair only supports official versions in the current version list. Use the matching installer for modded or custom versions.",
        "main_repair_version_requires_internet": "An internet connection is required to verify or repair version files.",
        "main_repair_version_started": "Verifying / repairing version files…",
        "main_repair_version_success": "Version verification/download completed.",
        "main_repair_version_failure": "Version repair failed. Check your connection and Game logs.",
    }
    for string_name, string_value in repair_version_strings.items():
        if not re.search(r'<string\s+name="' + re.escape(string_name) + r'"', strings_text):
            live_hint_match = re.search(
                r'(?m)^([ \t]*<string\s+name="main_live_log_truncated"[^>]*>.*?</string>)[ \t]*$',
                strings_text,
            )
            if not live_hint_match:
                raise ValueError("Could not find the live-log truncation label to add version-repair resources")
            indent = re.match(r"[ \t]*", live_hint_match.group(1)).group(0)
            new_string = indent + '<string name="' + string_name + '">' + string_value + '</string>'
            strings_text = strings_text[:live_hint_match.end()] + "\n" + new_string + strings_text[live_hint_match.end():]
        if not re.search(
            r'<string\s+name="' + re.escape(string_name) + r'"[^>]*>\s*' + re.escape(string_value) + r'\s*</string>',
            strings_text,
        ):
            raise ValueError(f"Version-repair resource is missing or invalid: {string_name}")
    strings.write_text(strings_text, encoding="utf-8")

    # App names can be localized in values-*/strings.xml. Force the product
    # identity in every translated app label/error string, not only the default locale.
    resources_dir = upstream / "app_pojavlauncher" / "src" / "main" / "res"
    localized_string_files = tuple(sorted(resources_dir.glob("values*/strings.xml")))
    for localized_strings in localized_string_files:
        localized_text = localized_strings.read_text(encoding="utf-8")
        localized_text = localized_text.replace("Amethyst", "VoidCraft Launcher")
        localized_text = re.sub(
            r'(<string\s+name="app_name"[^>]*>).*?(</string>)',
            r'\1VoidCraft Launcher\2',
            localized_text,
            count=1,
            flags=re.DOTALL,
        )
        localized_text = re.sub(
            r'(<string\s+name="app_short_name"[^>]*>).*?(</string>)',
            r'\1VoidCraft Launcher\2',
            localized_text,
            count=1,
            flags=re.DOTALL,
        )
        if "Amethyst" in localized_text:
            raise ValueError(f"An old app-brand string remains in {localized_strings}")
        localized_strings.write_text(localized_text, encoding="utf-8")

    # The launcher About dialog is backed by this asset. Keep upstream credits and
    # third-party license notices while identifying this custom build and its owner.
    about_file = upstream / "app_pojavlauncher" / "src" / "main" / "assets" / "about_en.txt"
    if not about_file.is_file():
        raise FileNotFoundError(f"Expected launcher About text is missing: {about_file}")
    about_text = about_file.read_text(encoding="utf-8")
    old_about_intro = '%s (Minecraft Java Launcher for Android), version %s by Tran Khanh Duy (based on "Boardwalk" app)<br>'
    new_about_intro = (
        '%s (Minecraft Java Launcher for Android), version %s<br>\n\n'
        '* Custom build:<br>\n'
        ' • VoidCraft Launcher is customized and maintained by MF. Original upstream credits: '
        'Tran Khanh Duy and contributors; based on the "Boardwalk" app.<br>'
    )
    if old_about_intro in about_text:
        about_text = about_text.replace(old_about_intro, new_about_intro, 1)
    elif "VoidCraft Launcher is customized and maintained by MF." not in about_text:
        raise ValueError("Could not identify the launcher About header to add VoidCraft Launcher/MF attribution safely")
    if (
        "VoidCraft Launcher is customized and maintained by MF." not in about_text
        or "Tran Khanh Duy and contributors" not in about_text
        or "* License:" not in about_text
        or "Third party components and their licenses" not in about_text
    ):
        raise ValueError("The About text must identify MF and preserve upstream/third-party license credits")
    about_file.write_text(about_text, encoding="utf-8")

    colors_text = colors.read_text(encoding="utf-8")
    for color_name in ("primary_text", "secondary_text"):
        if not re.search(r'<color\s+name="' + color_name + r'"', colors_text):
            raise ValueError(f"Required header color {color_name!r} is missing from colors.xml")

    settings_text = settings.read_text(encoding="utf-8")
    # AGP resolves some of its transitive buildscript dependencies from Maven
    # Central. pluginManagement repositories are separate from ordinary project
    # dependency repositories, so adding it only below dependencyResolutionManagement
    # is not sufficient for those classpath artifacts.
    settings_text = replace_idempotently(
        settings_text,
        """pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}""",
        """pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}""",
        str(settings),
    )
    settings_text = replace_idempotently(settings_text, "Amethyst", "VoidCraft Launcher", str(settings))
    if not re.search(r"rootProject\.name\s*=\s*['\"]VoidCraft Launcher['\"]", settings_text):
        raise ValueError("Gradle rootProject.name was not customized")
    settings.write_text(settings_text, encoding="utf-8")

    tools_java = upstream / "app_pojavlauncher" / "src" / "main" / "java" / "net" / "kdt" / "pojavlaunch" / "Tools.java"
    if not tools_java.is_file():
        raise FileNotFoundError(f"Expected upstream tools class is missing: {tools_java}")
    tools_text = tools_java.read_text(encoding="utf-8")
    tools_text = replace_idempotently(
        tools_text,
        'public static String APP_NAME = "Amethyst";',
        'public static String APP_NAME = "VoidCraft Launcher";',
        str(tools_java),
    )
    if 'public static String APP_NAME = "Amethyst";' in tools_text:
        raise ValueError("The shared launcher app-name constant still uses upstream branding")
    tools_java.write_text(tools_text, encoding="utf-8")

    # A local/offline profile does not need Microsoft authentication to download
    # public Minecraft version metadata and game files. Only a missing network
    # connection should force the already-installed-version check below.
    downloader_java = upstream / "app_pojavlauncher" / "src" / "main" / "java" / "net" / "kdt" / "pojavlaunch" / "tasks" / "MinecraftDownloader.java"
    if not downloader_java.is_file():
        raise FileNotFoundError(f"Expected Minecraft downloader is missing: {downloader_java}")
    downloader_text = downloader_java.read_text(encoding="utf-8")
    downloader_text = replace_idempotently(
        downloader_text,
        "if(isLocalProfile || !isOnline) {",
        "if(!isOnline) {",
        str(downloader_java),
    )
    if "if(isLocalProfile || !isOnline) {" in downloader_text:
        raise ValueError("Local profiles are still blocked from downloading Minecraft versions")
    if "if(!isOnline) {" not in downloader_text or "downloadGame(activity, version, realVersion);" not in downloader_text:
        raise ValueError("Minecraft downloader must check local files only without network and download when connected")

    # Detect missing, unreadable, and zero-byte manifests before Tools.read() opens
    # a missing path or the Gson parser receives an empty file.
    json_guard_marker = "if (!providedJsonFile.isFile() || !providedJsonFile.canRead() || providedJsonFile.length() == 0)"
    if json_guard_marker not in downloader_text:
        json_line = re.compile(
            r'(?m)^([ \t]*)File providedJsonFile = new File\(Tools\.DIR_HOME_VERSION \+ "/" \+ realVersion \+ "/" \+ realVersion \+ "\.json"\);[ \t]*$'
        )
        def add_metadata_guard(match: re.Match[str]) -> str:
            indent = match.group(1)
            guard_lines = (
                "if (!providedJsonFile.isFile() || !providedJsonFile.canRead() || providedJsonFile.length() == 0) {",
                "    throw new FileNotFoundException(",
                '        "Minecraft version " + realVersion + " is missing or its metadata is unreadable/empty. " +',
                '        "Connect to the internet and select Play again to download it."',
                "    );",
                "}",
            )
            return match.group(0) + "\n" + "\n".join(indent + line for line in guard_lines)
        downloader_text, guard_count = json_line.subn(add_metadata_guard, downloader_text, count=1)
        if guard_count != 1 or json_guard_marker not in downloader_text:
            raise ValueError("Could not add missing/unreadable/empty Minecraft version JSON guard")

    downloader_text = replace_idempotently(
        downloader_text,
        'String tryagain = !isOnline ? "Please ensure you have an internet connection" : "Please try again on your Microsoft Account";',
        'String tryagain = "Connect to the internet and try again.";',
        str(downloader_java),
    )
    downloader_text = replace_idempotently(
        downloader_text,
        'Tools.showErrorRemote(versionMessage + " is not currently installed. "+ tryagain, e);',
        'Tools.showErrorRemote(versionMessage + " is not installed or its version metadata is missing/corrupt. " + tryagain, e);',
        str(downloader_java),
    )
    if "Please try again on your Microsoft Account" in downloader_text:
        raise ValueError("Offline version errors must not suggest a Microsoft sign-in retry")
    downloader_java.write_text(downloader_text, encoding="utf-8")

    # The upstream local/offline account flow exists, but this guard blocks it when
    # there is no online account. Keep offline account creation available.
    local_login = upstream / "app_pojavlauncher" / "src" / "main" / "java" / "net" / "kdt" / "pojavlaunch" / "fragments" / "LocalLoginFragment.java"
    if not local_login.is_file():
        raise FileNotFoundError(f"Expected offline account screen is missing: {local_login}")
    login_text = local_login.read_text(encoding="utf-8")
    login_text = re.sub(
        r"^import static net\.kdt\.pojavlaunch\.Tools\.hasOnlineProfile;\n",
        "",
        login_text,
        flags=re.MULTILINE,
    )
    login_guard = """        // This is overkill but meh
        if (!hasOnlineProfile()){
            Tools.swapFragment(requireActivity(), MainMenuFragment.class, MainMenuFragment.TAG, null);
        }
"""
    if login_guard in login_text:
        login_text = login_text.replace(login_guard, "        // Offline accounts are supported; do not require an online profile to add one.\n")
    elif "if (!hasOnlineProfile())" in login_text:
        raise ValueError("Unrecognized offline-login guard; refusing an unsafe source edit")
    if "if (!hasOnlineProfile())" in login_text or "import static net.kdt.pojavlaunch.Tools.hasOnlineProfile;" in login_text:
        raise ValueError("Offline account screen still contains its online-profile gate/import")
    if (
        "ExtraCore.setValue(ExtraConstants.MOJANG_LOGIN_TODO, new String[]{" not in login_text
        or "if(!checkEditText())" not in login_text
    ):
        raise ValueError("The upstream offline-login submission/username validation flow is incomplete")

    # Fail closed if upstream changes the local username rules. Local profile names
    # must remain valid and unique rather than silently admitting unusable profiles.
    required_validation_imports = (
        "import java.io.File;",
        "import java.util.regex.Matcher;",
        "import java.util.regex.Pattern;",
    )
    missing_imports = [value for value in required_validation_imports if value not in login_text]
    if missing_imports:
        raise ValueError(f"Offline username validation imports are missing: {missing_imports}")
    if 'mUsernameValidationPattern = Pattern.compile("^[a-zA-Z0-9_]*$");' not in login_text:
        raise ValueError("Offline username validation pattern must allow only letters, digits, and underscores")

    validation_method = re.search(
        r"private\s+boolean\s+checkEditText\s*\(\s*\)\s*\{(?P<body>.*?)^\s*\}",
        login_text,
        flags=re.DOTALL | re.MULTILINE,
    )
    if not validation_method:
        raise ValueError("Offline account checkEditText() username validator is missing")
    username_validation_anchors = (
        "String text = mUsernameEditText.getText().toString();",
        "Matcher matcher = mUsernameValidationPattern.matcher(text);",
        "return !(text.isEmpty() ||",
        "text.length() < 3 ||",
        "text.length() > 16 ||",
        "!matcher.find() ||",
        'new File(Tools.DIR_ACCOUNT_NEW + "/" + text + ".json").exists()',
    )
    # Normalize whitespace so validation checks the operators and their ordering,
    # not harmless formatting differences in the pinned upstream method.
    validation_body = " ".join(validation_method.group("body").split())
    missing_validation = [
        anchor for anchor in username_validation_anchors if anchor not in validation_body
    ]
    if missing_validation:
        raise ValueError(
            "Offline username validation is incomplete; expected username syntax, "
            f"length, and duplicate-profile guards are missing: {missing_validation}"
        )
    local_login.write_text(login_text, encoding="utf-8")

    login_layout = upstream / "app_pojavlauncher" / "src" / "main" / "res" / "layout" / "fragment_local_login.xml"
    if not login_layout.is_file():
        raise FileNotFoundError(f"Expected offline login layout is missing: {login_layout}")
    login_layout_text = login_layout.read_text(encoding="utf-8")
    login_layout_text = login_layout_text.replace('android:inputType="textEmailAddress"', 'android:inputType="textPersonName"')
    login_layout_text = replace_idempotently(
        login_layout_text,
        'android:text="@string/login_online_login_label"',
        'android:text="@string/main_add_account"',
        str(login_layout),
    )
    if (
        login_layout_text.count('android:inputType="textPersonName"') != 1
        or 'android:inputType="textEmailAddress"' in login_layout_text
        or 'android:text="@string/main_add_account"' not in login_layout_text
        or 'android:text="@string/login_online_login_label"' in login_layout_text
    ):
        raise ValueError("Offline account form must use a username field and an Add account action")
    login_layout.write_text(login_layout_text, encoding="utf-8")

    # Add a responsive branded header without introducing heavyweight UI dependencies.
    header = """            <TextView
                android:id="@+id/mylauncher_hero_title"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="20dp"
                android:layout_marginTop="18dp"
                android:layout_marginEnd="20dp"
                android:text="VoidCraft Launcher"
                android:textColor="@color/primary_text"
                android:textSize="26sp"
                android:textStyle="bold"
                app:layout_constraintTop_toTopOf="parent"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintEnd_toEndOf="parent" />

            <TextView
                android:id="@+id/mylauncher_hero_subtitle"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="20dp"
                android:layout_marginTop="2dp"
                android:layout_marginEnd="20dp"
                android:layout_marginBottom="12dp"
                android:text="Minecraft Java Edition · by MF"
                android:textColor="@color/secondary_text"
                android:textSize="12sp"
                app:layout_constraintTop_toBottomOf="@id/mylauncher_hero_title"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintEnd_toEndOf="parent" />

"""
    for relative_layout in (
        "app_pojavlauncher/src/main/res/layout/fragment_launcher.xml",
        "app_pojavlauncher/src/main/res/layout-land/fragment_launcher.xml",
    ):
        layout_path = upstream / relative_layout
        if not layout_path.is_file():
            raise FileNotFoundError(f"Expected launcher layout is missing: {layout_path}")
        layout_text = layout_path.read_text(encoding="utf-8")
        if "mylauncher_hero_title" not in layout_text:
            anchor_match = re.search(r"^[ \t]*<androidx\.constraintlayout\.widget\.Guideline", layout_text, flags=re.MULTILINE)
            if not anchor_match:
                raise ValueError(f"Could not find launcher layout insertion point in {layout_path}")
            layout_text = layout_text[:anchor_match.start()] + header + layout_text[anchor_match.start():]
        for button_id in ("news_button", "discord_button"):
            pattern = re.compile(r'(<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/' + button_id + r'".*?/>\s*)', re.DOTALL)
            match = pattern.search(layout_text)
            if not match:
                raise ValueError(f"Could not find {button_id} in {layout_path}")
            block = match.group(1).replace(
                'app:layout_constraintTop_toTopOf="parent"',
                'app:layout_constraintTop_toBottomOf="@id/mylauncher_hero_subtitle"'
            )
            layout_text = layout_text[:match.start(1)] + block + layout_text[match.end(1):]
            patched_button = re.search(
                r'<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/'
                + re.escape(button_id)
                + r'".*?/>',
                layout_text,
                flags=re.DOTALL,
            )
            if not patched_button or 'app:layout_constraintTop_toBottomOf="@id/mylauncher_hero_subtitle"' not in patched_button.group(0):
                raise ValueError(f"Could not position {button_id} beneath the myLauncher header in {layout_path}")

        layout_text = re.sub(
            r'(android:id="@\+id/social_divider".*?app:layout_constraintTop_toTopOf=)"parent"',
            r'\1"@id/news_button"',
            layout_text,
            count=1,
            flags=re.DOTALL
        )
        divider_match = re.search(
            r'<View\s+android:id="@\+id/social_divider".*?/>',
            layout_text,
            flags=re.DOTALL,
        )
        if not divider_match or 'app:layout_constraintTop_toTopOf="@id/news_button"' not in divider_match.group(0):
            raise ValueError(f"Could not align social divider with news_button in {layout_path}")

        if "mylauncher_mods_button" not in layout_text:
            mods_button = """            <com.kdt.mcgui.LauncherMenuButton
                android:id="@+id/mylauncher_mods_button"
                style="@style/LauncherMenuButton.Universal"
                android:layout_width="match_parent"
                android:drawableStart="@android:drawable/ic_menu_manage"
                android:text="@string/main_mods_folder"
                app:layout_constraintTop_toBottomOf="@id/open_files_button" />

"""
            open_files_pattern = re.compile(r'(<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/open_files_button".*?/>\s*)', re.DOTALL)
            open_files_match = open_files_pattern.search(layout_text)
            if not open_files_match:
                raise ValueError(f"Could not find open_files_button in {layout_path}")
            block = open_files_match.group(1) + "\n" + mods_button
            layout_text = layout_text[:open_files_match.start(1)] + block + layout_text[open_files_match.end(1):]

        if "mylauncher_resourcepacks_button" not in layout_text:
            resourcepacks_button = """            <com.kdt.mcgui.LauncherMenuButton
                android:id="@+id/mylauncher_resourcepacks_button"
                style="@style/LauncherMenuButton.Universal"
                android:layout_width="match_parent"
                android:drawableStart="@android:drawable/ic_menu_gallery"
                android:text="@string/main_resourcepacks_folder"
                app:layout_constraintTop_toBottomOf="@id/mylauncher_mods_button" />

"""
            mods_pattern = re.compile(r'(<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/mylauncher_mods_button".*?/>\s*)', re.DOTALL)
            mods_match = mods_pattern.search(layout_text)
            if not mods_match:
                raise ValueError(f"Could not find mylauncher_mods_button in {layout_path}")
            block = mods_match.group(1) + "\n" + resourcepacks_button
            layout_text = layout_text[:mods_match.start(1)] + block + layout_text[mods_match.end(1):]

        if "mylauncher_world_saves_button" not in layout_text:
            world_saves_button = """            <com.kdt.mcgui.LauncherMenuButton
                android:id="@+id/mylauncher_world_saves_button"
                style="@style/LauncherMenuButton.Universal"
                android:layout_width="match_parent"
                android:drawableStart="@android:drawable/ic_menu_save"
                android:text="@string/main_world_saves_folder"
                app:layout_constraintTop_toBottomOf="@id/mylauncher_resourcepacks_button" />

"""
            resourcepacks_pattern = re.compile(r'(<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/mylauncher_resourcepacks_button".*?/>\s*)', re.DOTALL)
            resourcepacks_match = resourcepacks_pattern.search(layout_text)
            if not resourcepacks_match:
                raise ValueError(f"Could not find mylauncher_resourcepacks_button in {layout_path}")
            block = resourcepacks_match.group(1) + "\n" + world_saves_button
            layout_text = layout_text[:resourcepacks_match.start(1)] + block + layout_text[resourcepacks_match.end(1):]

        if "mylauncher_logs_button" not in layout_text:
            logs_button = """            <com.kdt.mcgui.LauncherMenuButton
                android:id="@+id/mylauncher_logs_button"
                style="@style/LauncherMenuButton.Universal"
                android:layout_width="match_parent"
                android:drawableStart="@android:drawable/ic_menu_view"
                android:text="@string/main_logs_folder"
                app:layout_constraintTop_toBottomOf="@id/mylauncher_world_saves_button" />

"""
            world_saves_pattern = re.compile(r'(<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/mylauncher_world_saves_button".*?/>\s*)', re.DOTALL)
            world_saves_match = world_saves_pattern.search(layout_text)
            if not world_saves_match:
                raise ValueError(f"Could not find mylauncher_world_saves_button to add the game-logs shortcut in {layout_path}")
            block = world_saves_match.group(1) + "\n" + logs_button
            layout_text = layout_text[:world_saves_match.start(1)] + block + layout_text[world_saves_match.end(1):]

        if "mylauncher_screenshots_button" not in layout_text:
            screenshots_button = """            <com.kdt.mcgui.LauncherMenuButton
                android:id="@+id/mylauncher_screenshots_button"
                style="@style/LauncherMenuButton.Universal"
                android:layout_width="match_parent"
                android:drawableStart="@android:drawable/ic_menu_gallery"
                android:text="@string/main_screenshots_folder"
                app:layout_constraintTop_toBottomOf="@id/mylauncher_logs_button" />

"""
            logs_pattern = re.compile(r'(<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/mylauncher_logs_button".*?/>\s*)', re.DOTALL)
            logs_match = logs_pattern.search(layout_text)
            if not logs_match:
                raise ValueError(f"Could not find mylauncher_logs_button to add screenshots shortcut in {layout_path}")
            block = logs_match.group(1) + "\n" + screenshots_button
            layout_text = layout_text[:logs_match.start(1)] + block + layout_text[logs_match.end(1):]

        if "mylauncher_crash_reports_button" not in layout_text:
            crash_reports_button = """            <com.kdt.mcgui.LauncherMenuButton
                android:id="@+id/mylauncher_crash_reports_button"
                style="@style/LauncherMenuButton.Universal"
                android:layout_width="match_parent"
                android:drawableStart="@android:drawable/ic_menu_info_details"
                android:text="@string/main_crash_reports_folder"
                app:layout_constraintTop_toBottomOf="@id/mylauncher_screenshots_button" />

"""
            screenshots_pattern = re.compile(r'(<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/mylauncher_screenshots_button".*?\/>\s*)', re.DOTALL)
            screenshots_match = screenshots_pattern.search(layout_text)
            if not screenshots_match:
                raise ValueError(f"Could not find mylauncher_screenshots_button to add crash-reports shortcut in {layout_path}")
            block = screenshots_match.group(1) + "\n" + crash_reports_button
            layout_text = layout_text[:screenshots_match.start(1)] + block + layout_text[screenshots_match.end(1):]

        if "mylauncher_config_button" not in layout_text:
            config_button = """            <com.kdt.mcgui.LauncherMenuButton
                android:id="@+id/mylauncher_config_button"
                style="@style/LauncherMenuButton.Universal"
                android:layout_width="match_parent"
                android:drawableStart="@android:drawable/ic_menu_preferences"
                android:text="@string/main_config_folder"
                app:layout_constraintTop_toBottomOf="@id/mylauncher_crash_reports_button" />

"""
            crash_reports_pattern = re.compile(r'(<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/mylauncher_crash_reports_button".*?/>\s*)', re.DOTALL)
            crash_reports_match = crash_reports_pattern.search(layout_text)
            if not crash_reports_match:
                raise ValueError(f"Could not find mylauncher_crash_reports_button to add Config folder in {layout_path}")
            block = crash_reports_match.group(1) + "\n" + config_button
            layout_text = layout_text[:crash_reports_match.start(1)] + block + layout_text[crash_reports_match.end(1):]

        if "mylauncher_shaderpacks_button" not in layout_text:
            shaderpacks_button = """            <com.kdt.mcgui.LauncherMenuButton
                android:id="@+id/mylauncher_shaderpacks_button"
                style="@style/LauncherMenuButton.Universal"
                android:layout_width="match_parent"
                android:drawableStart="@android:drawable/ic_menu_gallery"
                android:text="@string/main_shaderpacks_folder"
                app:layout_constraintTop_toBottomOf="@id/mylauncher_config_button" />

"""
            config_pattern = re.compile(r'(<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/mylauncher_config_button".*?/>\s*)', re.DOTALL)
            config_match = config_pattern.search(layout_text)
            if not config_match:
                raise ValueError(f"Could not find mylauncher_config_button to add shader-packs shortcut in {layout_path}")
            block = config_match.group(1) + "\n" + shaderpacks_button
            layout_text = layout_text[:config_match.start(1)] + block + layout_text[config_match.end(1):]

        if "mylauncher_repair_version_button" not in layout_text:
            repair_version_button = """            <com.kdt.mcgui.LauncherMenuButton
                android:id="@+id/mylauncher_repair_version_button"
                style="@style/LauncherMenuButton.Universal"
                android:layout_width="match_parent"
                android:drawableStart="@android:drawable/ic_popup_sync"
                android:text="@string/main_repair_version"
                app:layout_constraintTop_toBottomOf="@id/mylauncher_shaderpacks_button" />

"""
            shaderpacks_pattern = re.compile(r'(<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/mylauncher_shaderpacks_button".*?/>\s*)', re.DOTALL)
            shaderpacks_match = shaderpacks_pattern.search(layout_text)
            if not shaderpacks_match:
                raise ValueError(f"Could not find mylauncher_shaderpacks_button to add version repair in {layout_path}")
            block = shaderpacks_match.group(1) + "\n" + repair_version_button
            layout_text = layout_text[:shaderpacks_match.start(1)] + block + layout_text[shaderpacks_match.end(1):]

        if "mylauncher_account_button" not in layout_text:
            account_button = """            <com.kdt.mcgui.LauncherMenuButton
                android:id="@+id/mylauncher_account_button"
                style="@style/LauncherMenuButton.Universal"
                android:layout_width="match_parent"
                android:drawableStart="@android:drawable/ic_menu_myplaces"
                android:text="@string/main_add_account"
                app:layout_constraintTop_toBottomOf="@id/mylauncher_repair_version_button" />

"""
            repair_version_pattern = re.compile(r'(<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/mylauncher_repair_version_button".*?/>\s*)', re.DOTALL)
            repair_version_match = repair_version_pattern.search(layout_text)
            if not repair_version_match:
                raise ValueError(f"Could not find mylauncher_repair_version_button in {layout_path}")
            block = repair_version_match.group(1) + "\n" + account_button
            layout_text = layout_text[:repair_version_match.start(1)] + block + layout_text[repair_version_match.end(1):]

        account_pattern = re.compile(r'<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/mylauncher_account_button".*?/>', re.DOTALL)
        account_match = account_pattern.search(layout_text)
        if account_match:
            for prior_anchor in ("open_files_button", "mylauncher_mods_button", "mylauncher_resourcepacks_button", "mylauncher_world_saves_button", "mylauncher_logs_button", "mylauncher_screenshots_button", "mylauncher_crash_reports_button", "mylauncher_config_button", "mylauncher_shaderpacks_button"):
                prior_constraint = 'app:layout_constraintTop_toBottomOf="@id/' + prior_anchor + '"'
                if prior_constraint in account_match.group(0):
                    updated_account = account_match.group(0).replace(
                        prior_constraint,
                        'app:layout_constraintTop_toBottomOf="@id/mylauncher_repair_version_button"',
                        1,
                    )
                    layout_text = layout_text[:account_match.start()] + updated_account + layout_text[account_match.end():]
                    account_match = account_pattern.search(layout_text)
                    break

        mods_match = re.search(
            r'<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/mylauncher_mods_button".*?/>',
            layout_text,
            flags=re.DOTALL,
        )
        resourcepacks_match = re.search(
            r'<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/mylauncher_resourcepacks_button".*?/>',
            layout_text,
            flags=re.DOTALL,
        )
        world_saves_match = re.search(
            r'<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/mylauncher_world_saves_button".*?/>',
            layout_text,
            flags=re.DOTALL,
        )
        logs_match = re.search(
            r'<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/mylauncher_logs_button".*?/>',
            layout_text,
            flags=re.DOTALL,
        )
        screenshots_match = re.search(
            r'<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/mylauncher_screenshots_button".*?/>',
            layout_text,
            flags=re.DOTALL,
        )
        crash_reports_match = re.search(
            r'<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/mylauncher_crash_reports_button".*?/>',
            layout_text,
            flags=re.DOTALL,
        )
        config_match = re.search(
            r'<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/mylauncher_config_button".*?/>',
            layout_text,
            flags=re.DOTALL,
        )
        shaderpacks_match = re.search(
            r'<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/mylauncher_shaderpacks_button".*?/>',
            layout_text,
            flags=re.DOTALL,
        )
        repair_version_match = re.search(
            r'<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/mylauncher_repair_version_button".*?/>',
            layout_text,
            flags=re.DOTALL,
        )
        open_files_index = layout_text.find('android:id="@+id/open_files_button"')
        if (
            not mods_match
            or mods_match.group(0).count('android:id="@+id/mylauncher_mods_button"') != 1
            or 'style="@style/LauncherMenuButton.Universal"' not in mods_match.group(0)
            or 'android:layout_width="match_parent"' not in mods_match.group(0)
            or 'android:drawableStart="@android:drawable/ic_menu_manage"' not in mods_match.group(0)
            or 'android:text="@string/main_mods_folder"' not in mods_match.group(0)
            or 'app:layout_constraintTop_toBottomOf="@id/open_files_button"' not in mods_match.group(0)
            or not resourcepacks_match
            or resourcepacks_match.group(0).count('android:id="@+id/mylauncher_resourcepacks_button"') != 1
            or 'style="@style/LauncherMenuButton.Universal"' not in resourcepacks_match.group(0)
            or 'android:layout_width="match_parent"' not in resourcepacks_match.group(0)
            or 'android:drawableStart="@android:drawable/ic_menu_gallery"' not in resourcepacks_match.group(0)
            or 'android:text="@string/main_resourcepacks_folder"' not in resourcepacks_match.group(0)
            or 'app:layout_constraintTop_toBottomOf="@id/mylauncher_mods_button"' not in resourcepacks_match.group(0)
            or not world_saves_match
            or world_saves_match.group(0).count('android:id="@+id/mylauncher_world_saves_button"') != 1
            or 'style="@style/LauncherMenuButton.Universal"' not in world_saves_match.group(0)
            or 'android:layout_width="match_parent"' not in world_saves_match.group(0)
            or 'android:drawableStart="@android:drawable/ic_menu_save"' not in world_saves_match.group(0)
            or 'android:text="@string/main_world_saves_folder"' not in world_saves_match.group(0)
            or 'app:layout_constraintTop_toBottomOf="@id/mylauncher_resourcepacks_button"' not in world_saves_match.group(0)
            or not logs_match
            or logs_match.group(0).count('android:id="@+id/mylauncher_logs_button"') != 1
            or 'style="@style/LauncherMenuButton.Universal"' not in logs_match.group(0)
            or 'android:layout_width="match_parent"' not in logs_match.group(0)
            or 'android:drawableStart="@android:drawable/ic_menu_view"' not in logs_match.group(0)
            or 'android:text="@string/main_logs_folder"' not in logs_match.group(0)
            or 'app:layout_constraintTop_toBottomOf="@id/mylauncher_world_saves_button"' not in logs_match.group(0)
            or not screenshots_match
            or screenshots_match.group(0).count('android:id="@+id/mylauncher_screenshots_button"') != 1
            or 'style="@style/LauncherMenuButton.Universal"' not in screenshots_match.group(0)
            or 'android:layout_width="match_parent"' not in screenshots_match.group(0)
            or 'android:drawableStart="@android:drawable/ic_menu_gallery"' not in screenshots_match.group(0)
            or 'android:text="@string/main_screenshots_folder"' not in screenshots_match.group(0)
            or 'app:layout_constraintTop_toBottomOf="@id/mylauncher_logs_button"' not in screenshots_match.group(0)
            or not crash_reports_match
            or crash_reports_match.group(0).count('android:id="@+id/mylauncher_crash_reports_button"') != 1
            or 'style="@style/LauncherMenuButton.Universal"' not in crash_reports_match.group(0)
            or 'android:layout_width="match_parent"' not in crash_reports_match.group(0)
            or 'android:drawableStart="@android:drawable/ic_menu_info_details"' not in crash_reports_match.group(0)
            or 'android:text="@string/main_crash_reports_folder"' not in crash_reports_match.group(0)
            or 'app:layout_constraintTop_toBottomOf="@id/mylauncher_screenshots_button"' not in crash_reports_match.group(0)
            or not config_match
            or config_match.group(0).count('android:id="@+id/mylauncher_config_button"') != 1
            or 'style="@style/LauncherMenuButton.Universal"' not in config_match.group(0)
            or 'android:layout_width="match_parent"' not in config_match.group(0)
            or 'android:drawableStart="@android:drawable/ic_menu_preferences"' not in config_match.group(0)
            or 'android:text="@string/main_config_folder"' not in config_match.group(0)
            or 'app:layout_constraintTop_toBottomOf="@id/mylauncher_crash_reports_button"' not in config_match.group(0)
            or not shaderpacks_match
            or shaderpacks_match.group(0).count('android:id="@+id/mylauncher_shaderpacks_button"') != 1
            or 'style="@style/LauncherMenuButton.Universal"' not in shaderpacks_match.group(0)
            or 'android:layout_width="match_parent"' not in shaderpacks_match.group(0)
            or 'android:drawableStart="@android:drawable/ic_menu_gallery"' not in shaderpacks_match.group(0)
            or 'android:text="@string/main_shaderpacks_folder"' not in shaderpacks_match.group(0)
            or 'app:layout_constraintTop_toBottomOf="@id/mylauncher_config_button"' not in shaderpacks_match.group(0)
            or not repair_version_match
            or repair_version_match.group(0).count('android:id="@+id/mylauncher_repair_version_button"') != 1
            or 'style="@style/LauncherMenuButton.Universal"' not in repair_version_match.group(0)
            or 'android:layout_width="match_parent"' not in repair_version_match.group(0)
            or 'android:drawableStart="@android:drawable/ic_popup_sync"' not in repair_version_match.group(0)
            or 'android:text="@string/main_repair_version"' not in repair_version_match.group(0)
            or 'app:layout_constraintTop_toBottomOf="@id/mylauncher_shaderpacks_button"' not in repair_version_match.group(0)
            or not account_match
            or account_match.group(0).count('android:id="@+id/mylauncher_account_button"') != 1
            or 'style="@style/LauncherMenuButton.Universal"' not in account_match.group(0)
            or 'android:layout_width="match_parent"' not in account_match.group(0)
            or 'android:drawableStart="@android:drawable/ic_menu_myplaces"' not in account_match.group(0)
            or 'android:text="@string/main_add_account"' not in account_match.group(0)
            or 'app:layout_constraintTop_toBottomOf="@id/mylauncher_repair_version_button"' not in account_match.group(0)
            or open_files_index < 0
            or open_files_index > mods_match.start()
            or mods_match.start() > resourcepacks_match.start()
            or resourcepacks_match.start() > world_saves_match.start()
            or world_saves_match.start() > logs_match.start()
            or logs_match.start() > screenshots_match.start()
            or screenshots_match.start() > crash_reports_match.start()
            or crash_reports_match.start() > config_match.start()
            or config_match.start() > shaderpacks_match.start()
            or shaderpacks_match.start() > repair_version_match.start()
            or repair_version_match.start() > account_match.start()
        ):
            raise ValueError(f"myLauncher mods/resource-packs/world-saves/logs/screenshots/crash-reports/config/shader-packs/repair-version/account shortcuts are incomplete in {layout_path}")

        title_match = re.search(
            r'<TextView\s+android:id="@\+id/mylauncher_hero_title".*?/>',
            layout_text,
            flags=re.DOTALL,
        )
        subtitle_match = re.search(
            r'<TextView\s+android:id="@\+id/mylauncher_hero_subtitle".*?/>',
            layout_text,
            flags=re.DOTALL,
        )
        if (
            layout_text.count('android:id="@+id/mylauncher_hero_title"') != 1
            or layout_text.count('android:id="@+id/mylauncher_hero_subtitle"') != 1
            or layout_text.count('android:id="@+id/mylauncher_account_button"') != 1
            or layout_text.count('android:id="@+id/mylauncher_world_saves_button"') != 1
            or layout_text.count('android:id="@+id/mylauncher_logs_button"') != 1
            or layout_text.count('android:id="@+id/mylauncher_screenshots_button"') != 1
            or layout_text.count('android:id="@+id/mylauncher_crash_reports_button"') != 1
            or layout_text.count('android:id="@+id/mylauncher_config_button"') != 1
            or layout_text.count('android:id="@+id/mylauncher_shaderpacks_button"') != 1
            or layout_text.count('android:id="@+id/mylauncher_repair_version_button"') != 1
            or layout_text.count('android:id="@+id/news_button"') != 1
            or layout_text.count('android:id="@+id/discord_button"') != 1
            or layout_text.count('android:id="@+id/social_divider"') != 1
            or not title_match
            or not subtitle_match
            or 'android:layout_width="0dp"' not in title_match.group(0)
            or 'android:text="VoidCraft Launcher"' not in title_match.group(0)
            or 'android:layout_width="0dp"' not in subtitle_match.group(0)
            or 'android:text="Minecraft Java Edition · by MF"' not in subtitle_match.group(0)
            or 'app:layout_constraintTop_toBottomOf="@id/mylauncher_hero_title"' not in subtitle_match.group(0)
            or 'android:text="Minecraft Java Edition · by MF"' not in subtitle_match.group(0)
        ):
            raise ValueError(f"myLauncher header or folder/account shortcut is missing, duplicated, or incomplete in {layout_path}")
        # Generated XML must pass git diff --check; captured upstream whitespace
        # can otherwise be reinserted when moving launcher buttons.
        layout_text = "\n".join(line.rstrip() for line in layout_text.splitlines()) + "\n"
        layout_path.write_text(layout_text, encoding="utf-8")

    # Ensure the account shortcut still has a consumer in the upstream activity.
    launcher_activity = upstream / "app_pojavlauncher" / "src" / "main" / "java" / "net" / "kdt" / "pojavlaunch" / "LauncherActivity.java"
    if not launcher_activity.is_file():
        raise FileNotFoundError(f"Expected launcher activity is missing: {launcher_activity}")
    activity_text = launcher_activity.read_text(encoding="utf-8")
    if (
        "ExtraCore.addExtraListener(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);" not in activity_text
        or "Tools.swapFragment(this, SelectAuthFragment.class, SelectAuthFragment.TAG, null);" not in activity_text
    ):
        raise ValueError("The upstream SELECT_AUTH_METHOD event is not wired to the account selector")

    auth_listener = re.search(
        r"private\s+final\s+ExtraListener<Boolean>\s+mSelectAuthMethod\s*=\s*\(key,\s*value\)\s*->\s*\{(?P<body>.*?)^\s*\};",
        activity_text,
        flags=re.DOTALL | re.MULTILINE,
    )
    if not auth_listener:
        raise ValueError("The SELECT_AUTH_METHOD listener body is missing")
    auth_listener_body = " ".join(auth_listener.group("body").split())
    required_listener_anchors = (
        "Fragment fragment = getSupportFragmentManager().findFragmentById(mFragmentView.getId());",
        "if(!(fragment instanceof MainMenuFragment)) return false;",
        "Tools.swapFragment(this, SelectAuthFragment.class, SelectAuthFragment.TAG, null);",
        "return false;",
    )
    missing_listener_anchors = [
        anchor for anchor in required_listener_anchors if anchor not in auth_listener_body
    ]
    if missing_listener_anchors:
        raise ValueError(
            "The SELECT_AUTH_METHOD listener is incomplete or unsafe: "
            f"{missing_listener_anchors}"
        )
    if auth_listener_body.index("if(!(fragment instanceof MainMenuFragment)) return false;") > auth_listener_body.index(
        "Tools.swapFragment(this, SelectAuthFragment.class, SelectAuthFragment.TAG, null);"
    ):
        raise ValueError("The SELECT_AUTH_METHOD listener must check the active fragment before navigation")

    main_menu = upstream / "app_pojavlauncher" / "src" / "main" / "java" / "net" / "kdt" / "pojavlaunch" / "fragments" / "MainMenuFragment.java"
    if not main_menu.is_file():
        raise FileNotFoundError(f"Expected main menu fragment is missing: {main_menu}")
    main_menu_text = main_menu.read_text(encoding="utf-8")
    # The game-directory browser is local file access and should also work for local profiles.
    # Preserve the demo-profile warning, but remove the unrelated online-account gate.
    directory_gate = """            } else if (!hasOnlineProfile()) { // Otherwise display the generic pop-up to log in
                hasNoOnlineProfileDialog(requireActivity());
            } else openPath(v.getContext(), getCurrentProfileDirectory(), false);"""
    directory_allowed = """            } else openPath(v.getContext(), getCurrentProfileDirectory(), false);"""
    if directory_gate in main_menu_text:
        main_menu_text = main_menu_text.replace(directory_gate, directory_allowed, 1)
    elif directory_allowed not in main_menu_text:
        raise ValueError("Could not make game-directory access available to local profiles safely")
    directory_handler = re.search(
        r"mOpenDirectoryButton\.setOnClickListener.*?mNewsButton\.setOnLongClickListener",
        main_menu_text,
        flags=re.DOTALL,
    )
    if (
        not directory_handler
        or "Tools.isDemoProfile(v.getContext())" not in directory_handler.group(0)
        or "else openPath(v.getContext(), getCurrentProfileDirectory(), false);" not in directory_handler.group(0)
        or "else if (!hasOnlineProfile())" in directory_handler.group(0)
    ):
        raise ValueError("Game-directory access or demo-profile protection is incomplete")

    account_binding = "Button mMyLauncherAccountButton = view.findViewById(R.id.mylauncher_account_button);"
    if account_binding not in main_menu_text:
        binding_anchor = "Button mOpenDirectoryButton = view.findViewById(R.id.open_files_button);"
        if binding_anchor not in main_menu_text:
            raise ValueError("Could not find the main menu button binding insertion point")
        main_menu_text = main_menu_text.replace(binding_anchor, binding_anchor + "\n        " + account_binding, 1)
    account_listener = "mMyLauncherAccountButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true));"
    if account_listener not in main_menu_text:
        listener_anchor = "mVersionSpinner = view.findViewById(R.id.mc_version_spinner);"
        if listener_anchor not in main_menu_text:
            raise ValueError("Could not find the main menu listener insertion point")
        main_menu_text = main_menu_text.replace(listener_anchor, listener_anchor + "\n        " + account_listener, 1)
    if (
        main_menu_text.count(account_binding) != 1
        or main_menu_text.count(account_listener) != 1
        or "import android.widget.Button;" not in main_menu_text
        or "import net.kdt.pojavlaunch.extra.ExtraConstants;" not in main_menu_text
        or "import net.kdt.pojavlaunch.extra.ExtraCore;" not in main_menu_text
    ):
        raise ValueError("The account shortcut binding, handler, or required Java imports are incomplete")
    mods_binding = "Button mMyLauncherModsButton = view.findViewById(R.id.mylauncher_mods_button);"
    if mods_binding not in main_menu_text:
        if account_binding not in main_menu_text:
            raise ValueError("Could not bind the mods-folder shortcut without the account shortcut")
        main_menu_text = main_menu_text.replace(account_binding, account_binding + "\n        " + mods_binding, 1)
    mods_hint_binding = "mMyLauncherModsButton.setContentDescription(getString(R.string.main_mods_folder_hint));"
    if mods_hint_binding not in main_menu_text:
        main_menu_text = main_menu_text.replace(mods_binding, mods_binding + "\n        " + mods_hint_binding, 1)
    mods_listener = """mMyLauncherModsButton.setOnClickListener(v -> {
            if (Tools.isDemoProfile(v.getContext())) {
                hasNoOnlineProfileDialog(getActivity(), getString(R.string.demo_unsupported), getString(R.string.change_account));
                return;
            }
            File modsDirectory = new File(getCurrentProfileDirectory(), "mods");
            if (!modsDirectory.isDirectory() && !modsDirectory.mkdirs()) {
                Toast.makeText(requireContext(), "Unable to create the mods folder", Toast.LENGTH_LONG).show();
                return;
            }
            openPath(v.getContext(), modsDirectory, false);
        });"""
    if mods_listener not in main_menu_text:
        listener_anchor = "mVersionSpinner = view.findViewById(R.id.mc_version_spinner);"
        if listener_anchor not in main_menu_text:
            raise ValueError("Could not find the listener insertion point for the mods-folder shortcut")
        main_menu_text = main_menu_text.replace(listener_anchor, "        " + mods_listener + "\n        " + listener_anchor, 1)
    if (
        main_menu_text.count(mods_binding) != 1
        or main_menu_text.count(mods_hint_binding) != 1
        or main_menu_text.count(mods_listener) != 1
        or 'new File(getCurrentProfileDirectory(), "mods")' not in main_menu_text
        or "!modsDirectory.isDirectory() && !modsDirectory.mkdirs()" not in main_menu_text
        or "openPath(v.getContext(), modsDirectory, false);" not in main_menu_text
        or "import java.io.File;" not in main_menu_text
        or "import android.widget.Toast;" not in main_menu_text
        or "import static net.kdt.pojavlaunch.Tools.openPath;" not in main_menu_text
    ):
        raise ValueError("The mods-folder shortcut binding, directory creation, or handler is incomplete")

    mods_manager_listener = """mMyLauncherModsButton.setOnLongClickListener(v -> {
            if (Tools.isDemoProfile(v.getContext())) {
                hasNoOnlineProfileDialog(getActivity(), getString(R.string.demo_unsupported), getString(R.string.change_account));
                return true;
            }
            File modsDirectory = new File(getCurrentProfileDirectory(), "mods");
            if (!modsDirectory.isDirectory() && !modsDirectory.mkdirs()) {
                Toast.makeText(requireContext(), "Unable to create the mods folder", Toast.LENGTH_LONG).show();
                return true;
            }
            File[] modFiles = modsDirectory.listFiles((directory, name) -> {
                if (name == null) return false;
                String normalizedName = name.toLowerCase(java.util.Locale.ROOT);
                return new File(directory, name).isFile()
                        && (normalizedName.endsWith(".jar") || normalizedName.endsWith(".jar.disabled"));
            });
            if (modFiles == null || modFiles.length == 0) {
                Toast.makeText(requireContext(), "No JAR mods found to enable or disable", Toast.LENGTH_LONG).show();
                return true;
            }
            java.util.Arrays.sort(modFiles, (left, right) ->
                    left.getName().compareToIgnoreCase(right.getName()));
            String[] modChoices = new String[modFiles.length];
            for (int i = 0; i < modFiles.length; i++) {
                String normalizedName = modFiles[i].getName().toLowerCase(java.util.Locale.ROOT);
                boolean disabled = normalizedName.endsWith(".jar.disabled");
                modChoices[i] = (disabled ? "Disabled · " : "Enabled · ") + modFiles[i].getName();
            }
            new AlertDialog.Builder(requireContext())
                    .setTitle("Manage mods — enable or disable")
                    .setItems(modChoices, (dialog, which) -> {
                        if (which < 0 || which >= modFiles.length) return;
                        File selectedMod = modFiles[which];
                        String selectedName = selectedMod.getName();
                        String normalizedName = selectedName.toLowerCase(java.util.Locale.ROOT);
                        boolean wasDisabled = normalizedName.endsWith(".jar.disabled");
                        String destinationName = wasDisabled
                                ? selectedName.substring(0, selectedName.length() - ".disabled".length())
                                : selectedName + ".disabled";
                        File destination = new File(modsDirectory, destinationName);
                        new AlertDialog.Builder(requireContext())
                                .setTitle(wasDisabled ? "Enable mod?" : "Disable mod?")
                                .setMessage((wasDisabled ? "Enable " : "Disable ") + selectedName + "?")
                                .setNegativeButton(android.R.string.cancel, null)
                                .setPositiveButton(wasDisabled ? "Enable" : "Disable", (confirmDialog, result) -> {
                                    try {
                                        String modsRoot = modsDirectory.getCanonicalPath();
                                        String selectedPath = selectedMod.getCanonicalPath();
                                        String destinationPath = destination.getCanonicalPath();
                                        if (!modsDirectory.isDirectory()
                                                || !selectedMod.isFile()
                                                || !selectedPath.startsWith(modsRoot + File.separator)
                                                || !destinationPath.startsWith(modsRoot + File.separator)) {
                                            Toast.makeText(requireContext(), "The selected mod is no longer available", Toast.LENGTH_LONG).show();
                                            return;
                                        }
                                        if (destination.exists()) {
                                            Toast.makeText(requireContext(), "A file already exists with that name", Toast.LENGTH_LONG).show();
                                            return;
                                        }
                                        if (!selectedMod.renameTo(destination)) {
                                            Toast.makeText(requireContext(), "Unable to rename the mod file", Toast.LENGTH_LONG).show();
                                            return;
                                        }
                                        Toast.makeText(requireContext(),
                                                (wasDisabled ? "Enabled: " : "Disabled: ") + destination.getName(),
                                                Toast.LENGTH_LONG).show();
                                    } catch (IOException exception) {
                                        Toast.makeText(requireContext(), "Unable to validate the mod path: " + exception.getMessage(), Toast.LENGTH_LONG).show();
                                    }
                                })
                                .show();
                    })
                    .show();
            return true;
        });"""
    if mods_manager_listener not in main_menu_text:
        main_menu_text = main_menu_text.replace(mods_listener, mods_listener + "\n\n        " + mods_manager_listener, 1)
    if (
        main_menu_text.count(mods_manager_listener) != 1
        or main_menu_text.count("mMyLauncherModsButton.setOnLongClickListener") != 1
        or "name.toLowerCase(java.util.Locale.ROOT)" not in mods_manager_listener
        or 'normalizedName.endsWith(".jar")' not in mods_manager_listener
        or 'normalizedName.endsWith(".jar.disabled")' not in mods_manager_listener
        or 'selectedName + ".disabled"' not in mods_manager_listener
        or 'selectedName.substring(0, selectedName.length() - ".disabled".length())' not in mods_manager_listener
        or "setPositiveButton(wasDisabled ? \"Enable\" : \"Disable\"" not in mods_manager_listener
        or "destination.exists()" not in mods_manager_listener
        or "!selectedMod.renameTo(destination)" not in mods_manager_listener
        or "!selectedPath.startsWith(modsRoot + File.separator)" not in mods_manager_listener
        or "Tools.isDemoProfile(v.getContext())" not in mods_manager_listener
    ):
        raise ValueError("The mod enable/disable manager is incomplete or missing safety checks")
    if mods_hint_binding not in main_menu_text or main_menu_text.count(mods_hint_binding) != 1:
        raise ValueError("The Mods long-press accessibility hint is not wired exactly once")

    resourcepacks_binding = "Button mMyLauncherResourcePacksButton = view.findViewById(R.id.mylauncher_resourcepacks_button);"
    if resourcepacks_binding not in main_menu_text:
        if mods_binding not in main_menu_text:
            raise ValueError("Could not bind the resource-packs shortcut without the mods shortcut")
        main_menu_text = main_menu_text.replace(mods_binding, mods_binding + "\n        " + resourcepacks_binding, 1)
    resourcepacks_hint_binding = "mMyLauncherResourcePacksButton.setContentDescription(getString(R.string.main_resourcepacks_folder_hint));"
    if resourcepacks_hint_binding not in main_menu_text:
        main_menu_text = main_menu_text.replace(resourcepacks_binding, resourcepacks_binding + "\n        " + resourcepacks_hint_binding, 1)
    resourcepacks_listener = """mMyLauncherResourcePacksButton.setOnClickListener(v -> {
            if (Tools.isDemoProfile(v.getContext())) {
                hasNoOnlineProfileDialog(getActivity(), getString(R.string.demo_unsupported), getString(R.string.change_account));
                return;
            }
            File resourcePackDirectory = new File(getCurrentProfileDirectory(), "resourcepacks");
            if (!resourcePackDirectory.isDirectory() && !resourcePackDirectory.mkdirs()) {
                Toast.makeText(requireContext(), "Unable to create the resource packs folder", Toast.LENGTH_LONG).show();
                return;
            }
            openPath(v.getContext(), resourcePackDirectory, false);
        });"""
    if resourcepacks_listener not in main_menu_text:
        listener_anchor = "mVersionSpinner = view.findViewById(R.id.mc_version_spinner);"
        if listener_anchor not in main_menu_text:
            raise ValueError("Could not find the listener insertion point for the resource-packs shortcut")
        main_menu_text = main_menu_text.replace(listener_anchor, "        " + resourcepacks_listener + "\n        " + listener_anchor, 1)
    if (
        main_menu_text.count(resourcepacks_binding) != 1
        or main_menu_text.count(resourcepacks_hint_binding) != 1
        or main_menu_text.count(resourcepacks_listener) != 1
        or 'new File(getCurrentProfileDirectory(), "resourcepacks")' not in main_menu_text
        or "!resourcePackDirectory.isDirectory() && !resourcePackDirectory.mkdirs()" not in main_menu_text
        or "openPath(v.getContext(), resourcePackDirectory, false);" not in main_menu_text
    ):
        raise ValueError("The resource-packs shortcut binding, hint, directory creation, or handler is incomplete")

    resourcepacks_manager_listener = """mMyLauncherResourcePacksButton.setOnLongClickListener(v -> {
            if (Tools.isDemoProfile(v.getContext())) {
                hasNoOnlineProfileDialog(getActivity(), getString(R.string.demo_unsupported), getString(R.string.change_account));
                return true;
            }
            File resourcePackDirectory = new File(getCurrentProfileDirectory(), "resourcepacks");
            if (!resourcePackDirectory.isDirectory() && !resourcePackDirectory.mkdirs()) {
                Toast.makeText(requireContext(), "Unable to create the resource packs folder", Toast.LENGTH_LONG).show();
                return true;
            }
            File[] packFiles = resourcePackDirectory.listFiles((directory, name) -> {
                if (name == null) return false;
                String normalizedName = name.toLowerCase(java.util.Locale.ROOT);
                return new File(directory, name).isFile()
                        && (normalizedName.endsWith(".zip") || normalizedName.endsWith(".zip.disabled"));
            });
            if (packFiles == null || packFiles.length == 0) {
                Toast.makeText(requireContext(), "No ZIP resource packs found to enable or disable", Toast.LENGTH_LONG).show();
                return true;
            }
            java.util.Arrays.sort(packFiles, (left, right) ->
                    left.getName().compareToIgnoreCase(right.getName()));
            String[] packChoices = new String[packFiles.length];
            for (int i = 0; i < packFiles.length; i++) {
                String normalizedName = packFiles[i].getName().toLowerCase(java.util.Locale.ROOT);
                boolean disabled = normalizedName.endsWith(".zip.disabled");
                packChoices[i] = (disabled ? "Disabled · " : "Enabled · ") + packFiles[i].getName();
            }
            new AlertDialog.Builder(requireContext())
                    .setTitle("Manage resource packs — enable or disable")
                    .setItems(packChoices, (dialog, which) -> {
                        if (which < 0 || which >= packFiles.length) return;
                        File selectedPack = packFiles[which];
                        String selectedName = selectedPack.getName();
                        String normalizedName = selectedName.toLowerCase(java.util.Locale.ROOT);
                        boolean wasDisabled = normalizedName.endsWith(".zip.disabled");
                        String destinationName = wasDisabled
                                ? selectedName.substring(0, selectedName.length() - ".disabled".length())
                                : selectedName + ".disabled";
                        File destination = new File(resourcePackDirectory, destinationName);
                        new AlertDialog.Builder(requireContext())
                                .setTitle(wasDisabled ? "Enable resource pack?" : "Disable resource pack?")
                                .setMessage((wasDisabled ? "Enable " : "Disable ") + selectedName + "?")
                                .setNegativeButton(android.R.string.cancel, null)
                                .setPositiveButton(wasDisabled ? "Enable" : "Disable", (confirmDialog, result) -> {
                                    try {
                                        String packsRoot = resourcePackDirectory.getCanonicalPath();
                                        String selectedPath = selectedPack.getCanonicalPath();
                                        String destinationPath = destination.getCanonicalPath();
                                        if (!resourcePackDirectory.isDirectory()
                                                || !selectedPack.isFile()
                                                || !selectedPath.startsWith(packsRoot + File.separator)
                                                || !destinationPath.startsWith(packsRoot + File.separator)) {
                                            Toast.makeText(requireContext(), "The selected resource pack is no longer available", Toast.LENGTH_LONG).show();
                                            return;
                                        }
                                        if (destination.exists()) {
                                            Toast.makeText(requireContext(), "A file already exists with that name", Toast.LENGTH_LONG).show();
                                            return;
                                        }
                                        if (!selectedPack.renameTo(destination)) {
                                            Toast.makeText(requireContext(), "Unable to rename the resource pack file", Toast.LENGTH_LONG).show();
                                            return;
                                        }
                                        Toast.makeText(requireContext(),
                                                (wasDisabled ? "Enabled: " : "Disabled: ") + destination.getName(),
                                                Toast.LENGTH_LONG).show();
                                    } catch (IOException exception) {
                                        Toast.makeText(requireContext(), "Unable to validate the resource-pack path: " + exception.getMessage(), Toast.LENGTH_LONG).show();
                                    }
                                })
                                .show();
                    })
                    .show();
            return true;
        });"""
    if resourcepacks_manager_listener not in main_menu_text:
        main_menu_text = main_menu_text.replace(resourcepacks_listener, resourcepacks_listener + "\n\n        " + resourcepacks_manager_listener, 1)
    if (
        main_menu_text.count(resourcepacks_manager_listener) != 1
        or main_menu_text.count("mMyLauncherResourcePacksButton.setOnLongClickListener") != 1
        or "name.toLowerCase(java.util.Locale.ROOT)" not in resourcepacks_manager_listener
        or 'normalizedName.endsWith(".zip")' not in resourcepacks_manager_listener
        or 'normalizedName.endsWith(".zip.disabled")' not in resourcepacks_manager_listener
        or 'selectedName + ".disabled"' not in resourcepacks_manager_listener
        or 'selectedName.substring(0, selectedName.length() - ".disabled".length())' not in resourcepacks_manager_listener
        or "destination.exists()" not in resourcepacks_manager_listener
        or "!selectedPack.renameTo(destination)" not in resourcepacks_manager_listener
        or "!selectedPath.startsWith(packsRoot + File.separator)" not in resourcepacks_manager_listener
        or "Tools.isDemoProfile(v.getContext())" not in resourcepacks_manager_listener
    ):
        raise ValueError("The resource-pack enable/disable manager is incomplete or missing safety checks")
    if resourcepacks_hint_binding not in main_menu_text or main_menu_text.count(resourcepacks_hint_binding) != 1:
        raise ValueError("The Resource packs long-press accessibility hint is not wired exactly once")

    world_saves_binding = "Button mMyLauncherWorldSavesButton = view.findViewById(R.id.mylauncher_world_saves_button);"
    if world_saves_binding not in main_menu_text:
        if resourcepacks_binding not in main_menu_text:
            raise ValueError("Could not bind the world-saves shortcut without the resource-packs shortcut")
        main_menu_text = main_menu_text.replace(resourcepacks_binding, resourcepacks_binding + "\n        " + world_saves_binding, 1)
    backup_hint_binding = "mMyLauncherWorldSavesButton.setContentDescription(getString(R.string.main_world_saves_backup_hint));"
    if backup_hint_binding not in main_menu_text:
        main_menu_text = main_menu_text.replace(world_saves_binding, world_saves_binding + "\n        " + backup_hint_binding, 1)
    world_saves_listener = """mMyLauncherWorldSavesButton.setOnClickListener(v -> {
            if (Tools.isDemoProfile(v.getContext())) {
                hasNoOnlineProfileDialog(getActivity(), getString(R.string.demo_unsupported), getString(R.string.change_account));
                return;
            }
            File profileDirectory = getCurrentProfileDirectory();
            File worldSavesDirectory = new File(profileDirectory, "saves");
            if (!worldSavesDirectory.isDirectory() && !worldSavesDirectory.mkdirs()) {
                Toast.makeText(requireContext(), "Unable to create the world saves folder", Toast.LENGTH_LONG).show();
                return;
            }
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.main_world_saves_folder)
                    .setItems(new String[] {
                            getString(R.string.main_world_saves_open_folder),
                            getString(R.string.main_world_backup_restore)
                    }, (dialog, which) -> {
                        if (which == 0) {
                            openPath(v.getContext(), worldSavesDirectory, false);
                        } else if (which == 1) {
                            showWorldBackupPicker(requireActivity(), profileDirectory);
                        }
                    })
                    .show();
        });"""
    if world_saves_listener not in main_menu_text:
        listener_anchor = "mVersionSpinner = view.findViewById(R.id.mc_version_spinner);"
        if listener_anchor not in main_menu_text:
            raise ValueError("Could not find the listener insertion point for the world-saves shortcut")
        main_menu_text = main_menu_text.replace(listener_anchor, "        " + world_saves_listener + "\n        " + listener_anchor, 1)
    if (
        main_menu_text.count(world_saves_binding) != 1
        or main_menu_text.count(world_saves_listener) != 1
        or "File profileDirectory = getCurrentProfileDirectory();" not in world_saves_listener
        or 'new File(profileDirectory, "saves")' not in world_saves_listener
        or "!worldSavesDirectory.isDirectory() && !worldSavesDirectory.mkdirs()" not in world_saves_listener
        or "openPath(v.getContext(), worldSavesDirectory, false);" not in main_menu_text
    ):
        raise ValueError("The world-saves shortcut binding, directory creation, or handler is incomplete")

    world_saves_backup_listener = """mMyLauncherWorldSavesButton.setOnLongClickListener(v -> {
            if (Tools.isDemoProfile(v.getContext())) {
                hasNoOnlineProfileDialog(getActivity(), getString(R.string.demo_unsupported), getString(R.string.change_account));
                return true;
            }
            File savesDirectory = new File(getCurrentProfileDirectory(), "saves");
            File[] worldEntries = savesDirectory.listFiles();
            if (!savesDirectory.isDirectory() || worldEntries == null || worldEntries.length == 0) {
                Toast.makeText(requireContext(), "No worlds are available to back up", Toast.LENGTH_LONG).show();
                return true;
            }
            File backupsDirectory = new File(getCurrentProfileDirectory(), "backups");
            if (!backupsDirectory.isDirectory() && !backupsDirectory.mkdirs()) {
                Toast.makeText(requireContext(), "Unable to create the backups folder", Toast.LENGTH_LONG).show();
                return true;
            }
            String backupName = "worlds-" + System.currentTimeMillis() + ".zip";
            File backupFile = new File(backupsDirectory, backupName);
            File tempBackupFile = new File(backupsDirectory, backupName + ".tmp");
            if (backupFile.exists() || tempBackupFile.exists()) {
                Toast.makeText(requireContext(), "A backup with that name already exists; try again", Toast.LENGTH_LONG).show();
                return true;
            }
            final android.app.Activity backupActivity = getActivity();
            if (backupActivity == null) return true;
            Toast.makeText(requireContext(), "Creating world backup…", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                try (FileOutputStream fileOut = new FileOutputStream(tempBackupFile);
                     ZipOutputStream zipOut = new ZipOutputStream(fileOut)) {
                    zipWorldSaves(savesDirectory, savesDirectory.getCanonicalPath(), zipOut, new HashSet<String>());
                    zipOut.finish();
                } catch (IOException exception) {
                    if (tempBackupFile.exists()) tempBackupFile.delete();
                    backupActivity.runOnUiThread(() -> Toast.makeText(backupActivity,
                            "World backup failed: " + exception.getMessage(), Toast.LENGTH_LONG).show());
                    return;
                }
                if (!tempBackupFile.renameTo(backupFile)) {
                    tempBackupFile.delete();
                    backupActivity.runOnUiThread(() -> Toast.makeText(backupActivity,
                            "World backup failed: unable to finalize the ZIP file", Toast.LENGTH_LONG).show());
                    return;
                }
                backupActivity.runOnUiThread(() -> Toast.makeText(backupActivity,
                        "World backup saved: " + backupFile.getName(), Toast.LENGTH_LONG).show());
            }, "VoidCraftWorldBackup").start();
            return true;
        });"""
    if world_saves_backup_listener not in main_menu_text:
        main_menu_text = main_menu_text.replace(world_saves_listener, world_saves_listener + "\n\n        " + world_saves_backup_listener, 1)
    if (
        main_menu_text.count(world_saves_backup_listener) != 1
        or "mMyLauncherWorldSavesButton.setContentDescription(getString(R.string.main_world_saves_backup_hint));" not in main_menu_text
        or 'new File(getCurrentProfileDirectory(), "backups")' not in main_menu_text
        or 'new File(backupsDirectory, backupName + ".tmp")' not in main_menu_text
        or 'zipWorldSaves(savesDirectory, savesDirectory.getCanonicalPath(), zipOut, new HashSet<String>())' not in main_menu_text
        or '!tempBackupFile.renameTo(backupFile)' not in main_menu_text
        or "new Thread(() ->" not in main_menu_text
        or "Tools.isDemoProfile(v.getContext())" not in world_saves_backup_listener
    ):
        raise ValueError("World backup action is incomplete or lacks safety and error handling")

    logs_binding = "Button mMyLauncherLogsButton = view.findViewById(R.id.mylauncher_logs_button);"
    if logs_binding not in main_menu_text:
        if world_saves_binding not in main_menu_text:
            raise ValueError("Could not bind the game-logs shortcut without the world-saves shortcut")
        main_menu_text = main_menu_text.replace(world_saves_binding, world_saves_binding + "\n        " + logs_binding, 1)
    logs_listener = """mMyLauncherLogsButton.setOnClickListener(v -> {
            if (Tools.isDemoProfile(v.getContext())) {
                hasNoOnlineProfileDialog(getActivity(), getString(R.string.demo_unsupported), getString(R.string.change_account));
                return;
            }
            File logsDirectory = new File(getCurrentProfileDirectory(), "logs");
            if (!logsDirectory.isDirectory() && !logsDirectory.mkdirs()) {
                Toast.makeText(requireContext(), "Unable to create the game logs folder", Toast.LENGTH_LONG).show();
                return;
            }
            openPath(v.getContext(), logsDirectory, false);
        });"""
    if logs_listener not in main_menu_text:
        listener_anchor = "mVersionSpinner = view.findViewById(R.id.mc_version_spinner);"
        if listener_anchor not in main_menu_text:
            raise ValueError("Could not find the listener insertion point for the game-logs shortcut")
        main_menu_text = main_menu_text.replace(listener_anchor, "        " + logs_listener + "\n        " + listener_anchor, 1)
    if (
        main_menu_text.count(logs_binding) != 1
        or main_menu_text.count(logs_listener) != 1
        or 'new File(getCurrentProfileDirectory(), "logs")' not in main_menu_text
        or "!logsDirectory.isDirectory() && !logsDirectory.mkdirs()" not in main_menu_text
        or "openPath(v.getContext(), logsDirectory, false);" not in main_menu_text
    ):
        raise ValueError("The game-logs shortcut binding, directory creation, or handler is incomplete")

    logs_long_listener = """mMyLauncherLogsButton.setOnLongClickListener(v -> {
            if (Tools.isDemoProfile(v.getContext())) {
                hasNoOnlineProfileDialog(getActivity(), getString(R.string.demo_unsupported), getString(R.string.change_account));
                return true;
            }
            File latestLog = new File(new File(getCurrentProfileDirectory(), "logs"), "latest.log");
            if (!latestLog.isFile() || !latestLog.canRead()) {
                Toast.makeText(requireContext(), "Latest game log is not available yet", Toast.LENGTH_LONG).show();
                return true;
            }
            final long maxCopyBytes = 1024L * 1024L;
            try (java.io.RandomAccessFile logReader = new java.io.RandomAccessFile(latestLog, "r")) {
                long fileLength = logReader.length();
                long bytesToRead = Math.min(maxCopyBytes, fileLength);
                long offset = fileLength - bytesToRead;
                logReader.seek(offset);
                byte[] logBytes = new byte[(int) bytesToRead];
                logReader.readFully(logBytes);
                String copiedText = new String(logBytes, java.nio.charset.StandardCharsets.UTF_8);
                if (offset > 0) {
                    copiedText = "[Earlier log content omitted; copied final 1 MiB only.]\\n" + copiedText;
                }
                copiedText = copiedText.replaceAll(
                        "(?i)((?:access[_-]?token|refresh[_-]?token|client[_-]?secret|authorization|xsts[_-]?token|minecraft[_-]?token)[^A-Za-z0-9]{0,5})[A-Za-z0-9._~+/-=]+",
                        "$1[REDACTED]");
                copiedText = copiedText.replaceAll("(?i)Bearer[ ]+[A-Za-z0-9._~+/-=]+", "Bearer [REDACTED]");
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                        requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                if (clipboard == null) {
                    Toast.makeText(requireContext(), "Clipboard is not available", Toast.LENGTH_LONG).show();
                } else {
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("VoidCraft Launcher latest.log", copiedText));
                    Toast.makeText(requireContext(), "Latest game log copied with sensitive tokens redacted", Toast.LENGTH_LONG).show();
                }
            } catch (java.io.IOException error) {
                Toast.makeText(requireContext(), "Unable to copy latest game log: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
            return true;
        });"""
    if logs_long_listener not in main_menu_text:
        listener_anchor = "mVersionSpinner = view.findViewById(R.id.mc_version_spinner);"
        if listener_anchor not in main_menu_text:
            raise ValueError("Could not find the listener insertion point for the latest-log copy action")
        main_menu_text = main_menu_text.replace(listener_anchor, "        " + logs_long_listener + "\n        " + listener_anchor, 1)
    if (
        main_menu_text.count(logs_long_listener) != 1
        or 'new File(new File(getCurrentProfileDirectory(), "logs"), "latest.log")' not in main_menu_text
        or "final long maxCopyBytes = 1024L * 1024L;" not in main_menu_text
        or "logReader.seek(offset);" not in main_menu_text
        or "access[_-]?token|refresh[_-]?token|client[_-]?secret|authorization" not in main_menu_text
        or 'Bearer [REDACTED]' not in main_menu_text
        or "ClipboardManager" not in main_menu_text
        or "Latest game log copied with sensitive tokens redacted" not in main_menu_text
    ):
        raise ValueError("Latest-log copy, size limit, clipboard, or token redaction is incomplete")

    screenshots_binding = "Button mMyLauncherScreenshotsButton = view.findViewById(R.id.mylauncher_screenshots_button);"
    if screenshots_binding not in main_menu_text:
        if logs_binding not in main_menu_text:
            raise ValueError("Could not bind the screenshots shortcut without the game-logs shortcut")
        main_menu_text = main_menu_text.replace(logs_binding, logs_binding + "\n        " + screenshots_binding, 1)
    screenshots_listener = """mMyLauncherScreenshotsButton.setOnClickListener(v -> {
            if (Tools.isDemoProfile(v.getContext())) {
                hasNoOnlineProfileDialog(getActivity(), getString(R.string.demo_unsupported), getString(R.string.change_account));
                return;
            }
            File screenshotsDirectory = new File(getCurrentProfileDirectory(), "screenshots");
            if (!screenshotsDirectory.isDirectory() && !screenshotsDirectory.mkdirs()) {
                Toast.makeText(requireContext(), "Unable to create the screenshots folder", Toast.LENGTH_LONG).show();
                return;
            }
            openPath(v.getContext(), screenshotsDirectory, false);
        });"""
    if screenshots_listener not in main_menu_text:
        listener_anchor = "mVersionSpinner = view.findViewById(R.id.mc_version_spinner);"
        if listener_anchor not in main_menu_text:
            raise ValueError("Could not find the listener insertion point for the screenshots shortcut")
        main_menu_text = main_menu_text.replace(listener_anchor, "        " + screenshots_listener + "\n        " + listener_anchor, 1)
    if (
        main_menu_text.count(screenshots_binding) != 1
        or main_menu_text.count(screenshots_listener) != 1
        or 'new File(getCurrentProfileDirectory(), "screenshots")' not in main_menu_text
        or "!screenshotsDirectory.isDirectory() && !screenshotsDirectory.mkdirs()" not in main_menu_text
        or "openPath(v.getContext(), screenshotsDirectory, false);" not in main_menu_text
    ):
        raise ValueError("The screenshots shortcut binding, directory creation, or handler is incomplete")

    crash_reports_binding = "Button mMyLauncherCrashReportsButton = view.findViewById(R.id.mylauncher_crash_reports_button);"
    if crash_reports_binding not in main_menu_text:
        if screenshots_binding not in main_menu_text:
            raise ValueError("Could not bind the crash-reports shortcut without the screenshots shortcut")
        main_menu_text = main_menu_text.replace(screenshots_binding, screenshots_binding + "\n        " + crash_reports_binding, 1)
    crash_reports_listener = """mMyLauncherCrashReportsButton.setOnClickListener(v -> {
            if (Tools.isDemoProfile(v.getContext())) {
                hasNoOnlineProfileDialog(getActivity(), getString(R.string.demo_unsupported), getString(R.string.change_account));
                return;
            }
            File crashReportsDirectory = new File(getCurrentProfileDirectory(), "crash-reports");
            if (!crashReportsDirectory.isDirectory() && !crashReportsDirectory.mkdirs()) {
                Toast.makeText(requireContext(), "Unable to create the crash reports folder", Toast.LENGTH_LONG).show();
                return;
            }
            openPath(v.getContext(), crashReportsDirectory, false);
        });"""
    if crash_reports_listener not in main_menu_text:
        listener_anchor = "mVersionSpinner = view.findViewById(R.id.mc_version_spinner);"
        if listener_anchor not in main_menu_text:
            raise ValueError("Could not find the listener insertion point for the crash-reports shortcut")
        main_menu_text = main_menu_text.replace(listener_anchor, "        " + crash_reports_listener + "\n        " + listener_anchor, 1)
    if (
        main_menu_text.count(crash_reports_binding) != 1
        or main_menu_text.count(crash_reports_listener) != 1
        or 'new File(getCurrentProfileDirectory(), "crash-reports")' not in main_menu_text
        or "!crashReportsDirectory.isDirectory() && !crashReportsDirectory.mkdirs()" not in main_menu_text
        or "openPath(v.getContext(), crashReportsDirectory, false);" not in main_menu_text
    ):
        raise ValueError("The crash-reports shortcut binding, directory creation, or handler is incomplete")

    crash_reports_share_listener = """mMyLauncherCrashReportsButton.setOnLongClickListener(v -> {
            if (Tools.isDemoProfile(v.getContext())) {
                hasNoOnlineProfileDialog(getActivity(), getString(R.string.demo_unsupported), getString(R.string.change_account));
                return true;
            }
            File crashReportsDirectory = new File(getCurrentProfileDirectory(), "crash-reports");
            File[] reportFiles = crashReportsDirectory.listFiles((directory, name) -> {
                if (name == null) return false;
                String normalizedName = name.toLowerCase(java.util.Locale.ROOT);
                return new File(directory, name).isFile()
                        && (normalizedName.endsWith(".txt") || normalizedName.endsWith(".log"));
            });
            if (reportFiles == null || reportFiles.length == 0) {
                Toast.makeText(requireContext(), "No text crash reports are available to share", Toast.LENGTH_LONG).show();
                return true;
            }
            java.util.Arrays.sort(reportFiles, (left, right) ->
                    Long.compare(right.lastModified(), left.lastModified()));
            File latestReport = reportFiles[0];
            final long maxShareBytes = 2L * 1024L * 1024L;
            try (java.io.RandomAccessFile reportReader = new java.io.RandomAccessFile(latestReport, "r")) {
                long fileLength = reportReader.length();
                long bytesToRead = Math.min(maxShareBytes, fileLength);
                long offset = fileLength - bytesToRead;
                reportReader.seek(offset);
                byte[] reportBytes = new byte[(int) bytesToRead];
                reportReader.readFully(reportBytes);
                String reportText = new String(reportBytes, java.nio.charset.StandardCharsets.UTF_8);
                if (offset > 0) {
                    reportText = "[Earlier crash report content omitted; sharing final 2 MiB only.]\\n" + reportText;
                }
                reportText = reportText.replaceAll(
                        "(?i)((?:access[_-]?token|refresh[_-]?token|client[_-]?secret|authorization|xsts[_-]?token|minecraft[_-]?token)[^A-Za-z0-9]{0,5})[A-Za-z0-9._~+/-=]+",
                        "$1[REDACTED]");
                reportText = reportText.replaceAll("(?i)Bearer[ ]+[A-Za-z0-9._~+/-=]+", "Bearer [REDACTED]");
                android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT,
                        "VoidCraft Launcher crash report: " + latestReport.getName());
                shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, reportText);
                startActivity(android.content.Intent.createChooser(shareIntent,
                        "Share crash report — review before sending"));
            } catch (java.io.IOException error) {
                Toast.makeText(requireContext(), "Unable to prepare crash report: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
            return true;
        });"""
    if crash_reports_share_listener not in main_menu_text:
        main_menu_text = main_menu_text.replace(crash_reports_listener,
                crash_reports_listener + "\n\n        " + crash_reports_share_listener, 1)
    if (
        main_menu_text.count(crash_reports_share_listener) != 1
        or main_menu_text.count("mMyLauncherCrashReportsButton.setOnLongClickListener") != 1
        or "final long maxShareBytes = 2L * 1024L * 1024L;" not in main_menu_text
        or "reportReader.seek(offset);" not in main_menu_text
        or "Long.compare(right.lastModified(), left.lastModified())" not in main_menu_text
        or "access[_-]?token|refresh[_-]?token|client[_-]?secret|authorization" not in main_menu_text
        or 'Bearer [REDACTED]' not in main_menu_text
        or "Intent.ACTION_SEND" not in main_menu_text
        or "Intent.EXTRA_TEXT" not in main_menu_text
        or "review before sending" not in main_menu_text
        or "Tools.isDemoProfile(v.getContext())" not in crash_reports_share_listener
    ):
        raise ValueError("Crash report selection, bounded read, redaction, share chooser, or demo-profile guard is incomplete")

    config_binding = "Button mMyLauncherConfigButton = view.findViewById(R.id.mylauncher_config_button);"
    if config_binding not in main_menu_text:
        if crash_reports_binding not in main_menu_text:
            raise ValueError("Could not bind the Config folder shortcut without the Crash reports shortcut")
        main_menu_text = main_menu_text.replace(crash_reports_binding, crash_reports_binding + "\n        " + config_binding, 1)
    config_listener = """mMyLauncherConfigButton.setOnClickListener(v -> {
            if (Tools.isDemoProfile(v.getContext())) {
                hasNoOnlineProfileDialog(getActivity(), getString(R.string.demo_unsupported), getString(R.string.change_account));
                return;
            }
            File configDirectory = new File(getCurrentProfileDirectory(), "config");
            if (!configDirectory.isDirectory() && !configDirectory.mkdirs()) {
                Toast.makeText(requireContext(), "Unable to create the Config folder", Toast.LENGTH_LONG).show();
                return;
            }
            openPath(v.getContext(), configDirectory, false);
        });"""
    if config_listener not in main_menu_text:
        main_menu_text = main_menu_text.replace(crash_reports_listener, crash_reports_listener + "\n\n        " + config_listener, 1)
    if (
        main_menu_text.count(config_binding) != 1
        or main_menu_text.count(config_listener) != 1
        or 'new File(getCurrentProfileDirectory(), "config")' not in main_menu_text
        or "!configDirectory.isDirectory() && !configDirectory.mkdirs()" not in main_menu_text
        or "openPath(v.getContext(), configDirectory, false);" not in main_menu_text
        or "Tools.isDemoProfile(v.getContext())" not in config_listener
    ):
        raise ValueError("The Config-folder shortcut binding, directory creation, or handler is incomplete")

    shaderpacks_binding = "Button mMyLauncherShaderPacksButton = view.findViewById(R.id.mylauncher_shaderpacks_button);"
    if shaderpacks_binding not in main_menu_text:
        if screenshots_binding not in main_menu_text:
            raise ValueError("Could not bind the shader-packs shortcut without the Screenshots shortcut")
        main_menu_text = main_menu_text.replace(screenshots_binding, screenshots_binding + "\n        " + shaderpacks_binding, 1)
    shaderpacks_hint_binding = "mMyLauncherShaderPacksButton.setContentDescription(getString(R.string.main_shaderpacks_folder_hint));"
    if shaderpacks_hint_binding not in main_menu_text:
        main_menu_text = main_menu_text.replace(shaderpacks_binding, shaderpacks_binding + "\n        " + shaderpacks_hint_binding, 1)
    shaderpacks_listener = """mMyLauncherShaderPacksButton.setOnClickListener(v -> {
            if (Tools.isDemoProfile(v.getContext())) {
                hasNoOnlineProfileDialog(getActivity(), getString(R.string.demo_unsupported), getString(R.string.change_account));
                return;
            }
            File shaderPacksDirectory = new File(getCurrentProfileDirectory(), "shaderpacks");
            if (!shaderPacksDirectory.isDirectory() && !shaderPacksDirectory.mkdirs()) {
                Toast.makeText(requireContext(), "Unable to create the shader packs folder", Toast.LENGTH_LONG).show();
                return;
            }
            openPath(v.getContext(), shaderPacksDirectory, false);
        });"""
    if shaderpacks_listener not in main_menu_text:
        listener_anchor = "mVersionSpinner = view.findViewById(R.id.mc_version_spinner);"
        if listener_anchor not in main_menu_text:
            raise ValueError("Could not find the listener insertion point for the shader-packs shortcut")
        main_menu_text = main_menu_text.replace(listener_anchor, "        " + shaderpacks_listener + "\n        " + listener_anchor, 1)
    if (
        main_menu_text.count(shaderpacks_binding) != 1
        or main_menu_text.count(shaderpacks_hint_binding) != 1
        or main_menu_text.count(shaderpacks_listener) != 1
        or 'new File(getCurrentProfileDirectory(), "shaderpacks")' not in main_menu_text
        or "!shaderPacksDirectory.isDirectory() && !shaderPacksDirectory.mkdirs()" not in main_menu_text
        or "openPath(v.getContext(), shaderPacksDirectory, false);" not in main_menu_text
        or "Tools.isDemoProfile(v.getContext())" not in shaderpacks_listener
    ):
        raise ValueError("The shader-packs shortcut binding, hint, directory creation, or handler is incomplete")

    shaderpacks_manager_listener = """mMyLauncherShaderPacksButton.setOnLongClickListener(v -> {
            if (Tools.isDemoProfile(v.getContext())) {
                hasNoOnlineProfileDialog(getActivity(), getString(R.string.demo_unsupported), getString(R.string.change_account));
                return true;
            }
            File shaderPackDirectory = new File(getCurrentProfileDirectory(), "shaderpacks");
            if (!shaderPackDirectory.isDirectory() && !shaderPackDirectory.mkdirs()) {
                Toast.makeText(requireContext(), "Unable to create the shader packs folder", Toast.LENGTH_LONG).show();
                return true;
            }
            File[] shaderPackFiles = shaderPackDirectory.listFiles((directory, name) -> {
                if (name == null) return false;
                String normalizedName = name.toLowerCase(java.util.Locale.ROOT);
                return new File(directory, name).isFile()
                        && (normalizedName.endsWith(".zip") || normalizedName.endsWith(".zip.disabled"));
            });
            if (shaderPackFiles == null || shaderPackFiles.length == 0) {
                Toast.makeText(requireContext(), "No ZIP shader packs found to enable or disable", Toast.LENGTH_LONG).show();
                return true;
            }
            java.util.Arrays.sort(shaderPackFiles, (left, right) ->
                    left.getName().compareToIgnoreCase(right.getName()));
            String[] packChoices = new String[shaderPackFiles.length];
            for (int i = 0; i < shaderPackFiles.length; i++) {
                String normalizedName = shaderPackFiles[i].getName().toLowerCase(java.util.Locale.ROOT);
                boolean disabled = normalizedName.endsWith(".zip.disabled");
                packChoices[i] = (disabled ? "Disabled · " : "Enabled · ") + shaderPackFiles[i].getName();
            }
            new AlertDialog.Builder(requireContext())
                    .setTitle("Manage shader packs — enable or disable")
                    .setItems(packChoices, (dialog, which) -> {
                        if (which < 0 || which >= shaderPackFiles.length) return;
                        File selectedPack = shaderPackFiles[which];
                        String selectedName = selectedPack.getName();
                        String normalizedName = selectedName.toLowerCase(java.util.Locale.ROOT);
                        boolean wasDisabled = normalizedName.endsWith(".zip.disabled");
                        String destinationName = wasDisabled
                                ? selectedName.substring(0, selectedName.length() - ".disabled".length())
                                : selectedName + ".disabled";
                        File destination = new File(shaderPackDirectory, destinationName);
                        new AlertDialog.Builder(requireContext())
                                .setTitle(wasDisabled ? "Enable shader pack?" : "Disable shader pack?")
                                .setMessage((wasDisabled ? "Enable " : "Disable ") + selectedName + "?")
                                .setNegativeButton(android.R.string.cancel, null)
                                .setPositiveButton(wasDisabled ? "Enable" : "Disable", (confirmDialog, result) -> {
                                    try {
                                        String packsRoot = shaderPackDirectory.getCanonicalPath();
                                        String selectedPath = selectedPack.getCanonicalPath();
                                        String destinationPath = destination.getCanonicalPath();
                                        if (!shaderPackDirectory.isDirectory()
                                                || !selectedPack.isFile()
                                                || !selectedPath.startsWith(packsRoot + File.separator)
                                                || !destinationPath.startsWith(packsRoot + File.separator)) {
                                            Toast.makeText(requireContext(), "The selected shader pack is no longer available", Toast.LENGTH_LONG).show();
                                            return;
                                        }
                                        if (destination.exists()) {
                                            Toast.makeText(requireContext(), "A file already exists with that name", Toast.LENGTH_LONG).show();
                                            return;
                                        }
                                        if (!selectedPack.renameTo(destination)) {
                                            Toast.makeText(requireContext(), "Unable to rename the shader pack file", Toast.LENGTH_LONG).show();
                                            return;
                                        }
                                        Toast.makeText(requireContext(),
                                                (wasDisabled ? "Enabled: " : "Disabled: ") + destination.getName(),
                                                Toast.LENGTH_LONG).show();
                                    } catch (IOException exception) {
                                        Toast.makeText(requireContext(), "Unable to validate the shader-pack path: " + exception.getMessage(), Toast.LENGTH_LONG).show();
                                    }
                                })
                                .show();
                    })
                    .show();
            return true;
        });"""
    if shaderpacks_manager_listener not in main_menu_text:
        main_menu_text = main_menu_text.replace(shaderpacks_listener, shaderpacks_listener + "\n\n        " + shaderpacks_manager_listener, 1)
    if (
        main_menu_text.count(shaderpacks_manager_listener) != 1
        or main_menu_text.count("mMyLauncherShaderPacksButton.setOnLongClickListener") != 1
        or "name.toLowerCase(java.util.Locale.ROOT)" not in shaderpacks_manager_listener
        or 'normalizedName.endsWith(".zip")' not in shaderpacks_manager_listener
        or 'normalizedName.endsWith(".zip.disabled")' not in shaderpacks_manager_listener
        or 'selectedName + ".disabled"' not in shaderpacks_manager_listener
        or 'selectedName.substring(0, selectedName.length() - ".disabled".length())' not in shaderpacks_manager_listener
        or "destination.exists()" not in shaderpacks_manager_listener
        or "!selectedPack.renameTo(destination)" not in shaderpacks_manager_listener
        or "!selectedPath.startsWith(packsRoot + File.separator)" not in shaderpacks_manager_listener
        or "Tools.isDemoProfile(v.getContext())" not in shaderpacks_manager_listener
    ):
        raise ValueError("The shader-pack enable/disable manager is incomplete or missing safety checks")
    if shaderpacks_hint_binding not in main_menu_text or main_menu_text.count(shaderpacks_hint_binding) != 1:
        raise ValueError("The Shader packs long-press accessibility hint is not wired exactly once")

    required_backup_imports = (
        "import java.io.FileInputStream;",
        "import java.io.FileOutputStream;",
        "import java.io.IOException;",
        "import java.util.HashSet;",
        "import java.util.Set;",
        "import java.util.zip.ZipEntry;",
        "import java.util.zip.ZipOutputStream;",
    )
    if "import java.io.File;" not in main_menu_text:
        raise ValueError("World backup needs the existing java.io.File import")
    if "import java.io.FileInputStream;" not in main_menu_text:
        main_menu_text = main_menu_text.replace(
            "import java.io.File;\n",
            "import java.io.File;\nimport java.io.FileInputStream;\nimport java.io.FileOutputStream;\nimport java.io.IOException;\n",
            1,
        )
    if "import java.util.HashSet;" not in main_menu_text:
        main_menu_text = main_menu_text.replace("import java.io.FileInputStream;", "import java.util.HashSet;\nimport java.util.Set;\nimport java.io.FileInputStream;", 1)
    if "import java.util.zip.ZipEntry;" not in main_menu_text:
        main_menu_text = main_menu_text.replace("import java.util.Set;", "import java.util.zip.ZipEntry;\nimport java.util.zip.ZipOutputStream;\nimport java.util.Set;", 1)
    if "import java.util.zip.ZipInputStream;" not in main_menu_text:
        main_menu_text = main_menu_text.replace("import java.util.zip.ZipEntry;", "import java.util.zip.ZipEntry;\nimport java.util.zip.ZipInputStream;", 1)
    missing_backup_imports = [value for value in required_backup_imports if value not in main_menu_text]
    if missing_backup_imports:
        raise ValueError(f"World backup imports are incomplete: {missing_backup_imports}")

    backup_helper = """    private void showWorldBackupPicker(final android.app.Activity activity, File profileDirectory) {
        File backupsDirectory = new File(profileDirectory, "backups");
        File[] backupFiles = backupsDirectory.listFiles((directory, name) ->
                name != null && name.endsWith(".zip") && new File(directory, name).isFile());
        if (backupFiles == null || backupFiles.length == 0) {
            Toast.makeText(activity, "No ZIP backups were found", Toast.LENGTH_LONG).show();
            return;
        }
        java.util.Arrays.sort(backupFiles, (left, right) ->
                Long.compare(right.lastModified(), left.lastModified()));
        String[] backupNames = new String[backupFiles.length];
        for (int i = 0; i < backupFiles.length; i++) {
            backupNames[i] = backupFiles[i].getName();
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.main_world_backup_picker_title)
                .setItems(backupNames, (dialog, which) -> {
                    if (which < 0 || which >= backupFiles.length) return;
                    File selectedBackup = backupFiles[which];
                    new AlertDialog.Builder(activity)
                            .setTitle(R.string.main_world_backup_confirm_title)
                            .setMessage(R.string.main_world_backup_confirm_message)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.main_world_backup_restore, (confirmDialog, result) ->
                                    restoreWorldBackup(activity, profileDirectory, selectedBackup))
                            .show();
                })
                .show();
    }

    private static void restoreWorldBackup(final android.app.Activity activity, File profileDirectory, File backupArchive) {
        File backupsDirectory = new File(profileDirectory, "backups");
        File savesDirectory = new File(profileDirectory, "saves");
        final File stagingDirectory = new File(profileDirectory, ".voidcraft-restore-" + System.nanoTime());
        String resultMessage = "World backup restored";
        try {
            String backupRoot = backupsDirectory.getCanonicalPath();
            String archivePath = backupArchive.getCanonicalPath();
            if (!backupArchive.isFile()
                    || !archivePath.startsWith(backupRoot + File.separator)) {
                throw new IOException("The selected ZIP is not inside this profile's backups folder");
            }
            if (!savesDirectory.isDirectory() && !savesDirectory.mkdirs()) {
                throw new IOException("Unable to create the saves folder");
            }
            if (stagingDirectory.exists() || !stagingDirectory.mkdirs()) {
                throw new IOException("Unable to create a temporary restore folder");
            }
        } catch (IOException exception) {
            Toast.makeText(activity, "World backup restore failed: " + exception.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(activity, "Restoring world backup…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String result;
            int restoredCount = 0;
            try {
                extractWorldBackup(backupArchive, stagingDirectory);
                File[] stagedEntries = stagingDirectory.listFiles();
                if (stagedEntries == null || stagedEntries.length == 0) {
                    throw new IOException("The ZIP contains no world files");
                }
                for (File stagedEntry : stagedEntries) {
                    File destination = uniqueRestoredWorldDestination(savesDirectory, stagedEntry.getName());
                    if (!stagedEntry.renameTo(destination)) {
                        throw new IOException("Unable to move " + stagedEntry.getName()
                                + "; " + restoredCount + " item(s) may already have been restored");
                    }
                    restoredCount++;
                }
                result = "World backup restored: " + restoredCount + " item(s)";
            } catch (IOException exception) {
                result = "World backup restore failed after " + restoredCount + " item(s): " + exception.getMessage();
            } finally {
                deleteRestoreTree(stagingDirectory);
            }
            final String message = result;
            activity.runOnUiThread(() ->
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
        }, "VoidCraftWorldRestore").start();
    }

    private static void extractWorldBackup(File backupArchive, File stagingDirectory) throws IOException {
        final String stagingRoot = stagingDirectory.getCanonicalPath();
        final int maxEntries = 250000;
        final long availableBytes = new android.os.StatFs(stagingDirectory.getAbsolutePath()).getAvailableBytes();
        final long reserveBytes = Math.min(256L * 1024L * 1024L, Math.max(8L * 1024L * 1024L, availableBytes / 10L));
        final long storageBudgetBytes = availableBytes > reserveBytes ? availableBytes - reserveBytes : 0L;
        final long maxUncompressedBytes = Math.min(20L * 1024L * 1024L * 1024L, storageBudgetBytes);
        if (maxUncompressedBytes <= 0L) {
            throw new IOException("Not enough free storage to restore a world backup safely");
        }
        int entryCount = 0;
        long uncompressedBytes = 0L;
        Set<String> seenEntries = new HashSet<>();
        try (ZipInputStream zipInput = new ZipInputStream(new FileInputStream(backupArchive))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                if (++entryCount > maxEntries) {
                    throw new IOException("The ZIP contains too many entries");
                }
                String entryName = entry.getName();
                if (entryName == null || entryName.isEmpty()
                        || entryName.startsWith("/")
                        || entryName.indexOf((char) 92) >= 0
                        || entryName.indexOf((char) 0) >= 0
                        || entryName.indexOf(':') >= 0) {
                    throw new IOException("The ZIP contains an unsafe path");
                }
                boolean directoryEntry = entry.isDirectory();
                String normalizedName = directoryEntry && entryName.endsWith("/")
                        ? entryName.substring(0, entryName.length() - 1)
                        : entryName;
                if (normalizedName.isEmpty()) throw new IOException("The ZIP contains an empty path");
                String[] segments = normalizedName.split("/", -1);
                for (String segment : segments) {
                    if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                        throw new IOException("The ZIP contains an unsafe path segment");
                    }
                }
                if (!seenEntries.add(normalizedName)) {
                    throw new IOException("The ZIP contains duplicate entries");
                }

                File outputFile = new File(stagingDirectory, normalizedName);
                String outputPath = outputFile.getCanonicalPath();
                if (!outputPath.startsWith(stagingRoot + File.separator)) {
                    throw new IOException("A ZIP path escapes the temporary restore folder");
                }
                if (directoryEntry) {
                    if (outputFile.exists()) {
                        if (!outputFile.isDirectory()) throw new IOException("A ZIP path has conflicting entry types");
                    } else if (!outputFile.mkdirs()) {
                        throw new IOException("Unable to create a restored directory");
                    }
                } else {
                    File parent = outputFile.getParentFile();
                    if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
                        throw new IOException("Unable to create a restored file's parent directory");
                    }
                    if (outputFile.exists()) throw new IOException("The ZIP contains a duplicate file");
                    try (FileOutputStream fileOutput = new FileOutputStream(outputFile)) {
                        byte[] buffer = new byte[16384];
                        int count;
                        while ((count = zipInput.read(buffer)) != -1) {
                            if (count > maxUncompressedBytes - uncompressedBytes) {
                                throw new IOException("The ZIP exceeds the available storage budget or the 20 GB safety limit");
                            }
                            uncompressedBytes += count;
                            fileOutput.write(buffer, 0, count);
                        }
                    }
                }
                zipInput.closeEntry();
            }
        }
    }

    private static File uniqueRestoredWorldDestination(File savesDirectory, String originalName) {
        File destination = new File(savesDirectory, originalName);
        if (!destination.exists()) return destination;
        int suffix = 1;
        do {
            String restoredName = originalName + " (restored" + (suffix == 1 ? "" : " " + suffix) + ")";
            destination = new File(savesDirectory, restoredName);
            suffix++;
        } while (destination.exists());
        return destination;
    }

    private static void deleteRestoreTree(File node) {
        if (node == null || !node.exists()) return;
        if (node.isDirectory()) {
            File[] children = node.listFiles();
            if (children != null) {
                for (File child : children) deleteRestoreTree(child);
            }
        }
        node.delete();
    }

    /**
     * Adds world saves to a ZIP without following paths outside the selected saves directory.
     * Repeated canonical paths are skipped to prevent symbolic-link cycles and duplicate entries.
     */
    private static void zipWorldSaves(File current, String rootCanonicalPath, ZipOutputStream output,
                                      Set<String> visitedPaths) throws IOException {
        String canonicalPath = current.getCanonicalPath();
        if (!canonicalPath.equals(rootCanonicalPath)
                && !canonicalPath.startsWith(rootCanonicalPath + File.separator)) {
            throw new IOException("A world-save path escapes the saves directory");
        }
        if (!visitedPaths.add(canonicalPath)) return;

        String relativePath = canonicalPath.equals(rootCanonicalPath)
                ? ""
                : canonicalPath.substring(rootCanonicalPath.length() + 1).replace(File.separatorChar, '/');
        if (current.isDirectory()) {
            if (!relativePath.isEmpty()) {
                output.putNextEntry(new ZipEntry(relativePath + "/"));
                output.closeEntry();
            }
            File[] children = current.listFiles();
            if (children == null) {
                throw new IOException("Unable to read world-save directory: " + current.getName());
            }
            for (File child : children) {
                zipWorldSaves(child, rootCanonicalPath, output, visitedPaths);
            }
            return;
        }
        if (!current.isFile() || relativePath.isEmpty()) {
            throw new IOException("Unsupported world-save entry: " + current.getName());
        }

        output.putNextEntry(new ZipEntry(relativePath));
        try (FileInputStream input = new FileInputStream(current)) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
        }
        output.closeEntry();
    }
"""
    if "private static void zipWorldSaves(" not in main_menu_text:
        class_end = main_menu_text.rfind("\n}")
        if class_end < 0:
            raise ValueError("Could not find the end of MainMenuFragment to add the world backup helper")
        main_menu_text = main_menu_text[:class_end] + "\n\n" + backup_helper + main_menu_text[class_end:]
    elif (
        "A world-save path escapes the saves directory" not in main_menu_text
        or "output.putNextEntry(new ZipEntry(relativePath))" not in main_menu_text
        or "new byte[16384]" not in main_menu_text
        or "private void showWorldBackupPicker(" not in main_menu_text
        or "private static void restoreWorldBackup(" not in main_menu_text
        or "private static void extractWorldBackup(" not in main_menu_text
        or "outputPath.startsWith(stagingRoot + File.separator)" not in main_menu_text
        or "new android.os.StatFs(stagingDirectory.getAbsolutePath()).getAvailableBytes()" not in main_menu_text
        or "storageBudgetBytes" not in main_menu_text
        or "The ZIP exceeds the available storage budget or the 20 GB safety limit" not in main_menu_text
        or "private static File uniqueRestoredWorldDestination(" not in main_menu_text
        or "The ZIP contains an unsafe path segment" not in main_menu_text
    ):
        raise ValueError("Existing world backup/restore helpers are incomplete")

    main_menu.write_text(main_menu_text, encoding="utf-8")

    # Add a live, case-insensitive filter to the existing upstream LoggerView.
    logger_view = upstream / "app_pojavlauncher" / "src" / "main" / "java" / "com" / "kdt" / "LoggerView.java"
    logger_layout = upstream / "app_pojavlauncher" / "src" / "main" / "res" / "layout" / "view_logger.xml"
    for logger_path in (logger_view, logger_layout):
        if not logger_path.is_file():
            raise FileNotFoundError(f"Expected upstream logger file is missing: {logger_path}")

    logger_view_text = logger_view.read_text(encoding="utf-8")
    if "import android.widget.EditText;" not in logger_view_text:
        logger_view_text = logger_view_text.replace(
            "import android.content.Context;\n",
            "import android.content.Context;\nimport android.text.Editable;\nimport android.text.TextWatcher;\n",
            1,
        )
        logger_view_text = logger_view_text.replace(
            "import android.widget.ImageButton;\n",
            "import android.widget.ImageButton;\nimport android.widget.EditText;\nimport java.util.Locale;\n",
            1,
        )
    if "private EditText mLogFilterInput;" not in logger_view_text:
        logger_view_text = logger_view_text.replace(
            "    private TextView mLogTextView;\n",
            "    private TextView mLogTextView;\n"
            "    private EditText mLogFilterInput;\n"
            "    private TextView mLogFilterCount;\n"
            "    private static final int MAX_LOG_BUFFER_CHARS = 300000;\n"
            "    private final StringBuilder mLogBuffer = new StringBuilder();\n"
            "    private boolean mLogBufferTruncated;\n",
            1,
        )

    filter_setup = """        mLogFilterInput = findViewById(R.id.content_log_filter_input);
        mLogFilterCount = findViewById(R.id.content_log_filter_count);
        ImageButton clearFilterButton = findViewById(R.id.content_log_filter_clear);
        clearFilterButton.setContentDescription(getContext().getString(R.string.main_live_log_filter_clear));
        clearFilterButton.setOnClickListener(view -> mLogFilterInput.setText(""));
        mLogFilterInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                renderFilteredLog();
            }
        });
        renderFilteredLog();

"""
    if "mLogFilterInput = findViewById(R.id.content_log_filter_input);" not in logger_view_text:
        logger_view_text = logger_view_text.replace(
            "        mLogTextView.setVisibility(GONE);\n",
            "        mLogTextView.setVisibility(GONE);\n\n" + filter_setup,
            1,
        )

    old_toggle_off = """                        mLogTextView.setText("");
                        Logger.removeLogListener(mLogListener);"""
    new_toggle_off = """                        mLogTextView.setText("");
                        mLogBuffer.setLength(0);
                        mLogBufferTruncated = false;
                        mLogFilterInput.setText("");
                        mLogFilterCount.setText(getContext().getString(R.string.main_live_log_count_all, 0));
                        Logger.removeLogListener(mLogListener);"""
    if old_toggle_off in logger_view_text:
        logger_view_text = logger_view_text.replace(old_toggle_off, new_toggle_off, 1)
    elif new_toggle_off not in logger_view_text:
        raise ValueError("Could not safely patch LoggerView to clear the bounded filter buffer when output stops")

    original_log_listener = r"""        mLogListener = text -> {
            if(mLogTextView.getVisibility() != VISIBLE) return;
            post(() -> {
                mLogTextView.append(text + '\n');
                if(mScrollView.isKeepFocusing()) mScrollView.fullScroll(View.FOCUS_DOWN);
            });

        };"""
    new_log_listener = r"""        mLogListener = text -> {
            if(mLogTextView.getVisibility() != VISIBLE) return;
            post(() -> {
                if (!mLogToggle.isChecked() || mLogTextView.getVisibility() != VISIBLE) return;
                appendLogText(text);
                renderFilteredLog();
                if(mScrollView.isKeepFocusing()) mScrollView.fullScroll(View.FOCUS_DOWN);
            });
        };"""
    if original_log_listener in logger_view_text:
        logger_view_text = logger_view_text.replace(original_log_listener, new_log_listener, 1)
    elif new_log_listener not in logger_view_text:
        raise ValueError("Could not safely patch the upstream live-log callback")

    live_log_helpers = r"""
    private void appendLogText(String text) {
        mLogBuffer.append(text == null ? "" : text).append('\n');
        if (mLogBuffer.length() <= MAX_LOG_BUFFER_CHARS) return;
        int overflow = mLogBuffer.length() - MAX_LOG_BUFFER_CHARS;
        int nextLine = mLogBuffer.indexOf("\n", overflow);
        int removeChars = nextLine >= 0 ? nextLine + 1 : overflow;
        mLogBuffer.delete(0, removeChars);
        mLogBufferTruncated = true;
    }

    private void renderFilteredLog() {
        if (mLogTextView == null || mLogFilterInput == null || mLogFilterCount == null) return;
        String filter = mLogFilterInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        StringBuilder filteredText = new StringBuilder();
        if (mLogBufferTruncated) {
            filteredText.append(getContext().getString(R.string.main_live_log_truncated)).append('\n');
        }
        String[] lines = mLogBuffer.toString().split("\\n", -1);
        int lineCount = 0;
        int matchCount = 0;
        for (String line : lines) {
            if (line.isEmpty()) continue;
            lineCount++;
            if (filter.isEmpty() || line.toLowerCase(Locale.ROOT).contains(filter)) {
                filteredText.append(line).append('\n');
                matchCount++;
            }
        }
        mLogTextView.setText(filteredText.toString());
        if (filter.isEmpty()) {
            mLogFilterCount.setText(getContext().getString(R.string.main_live_log_count_all, lineCount));
        } else if (matchCount == 0) {
            mLogFilterCount.setText(R.string.main_live_log_count_none);
        } else {
            mLogFilterCount.setText(getContext().getString(R.string.main_live_log_count_matches, matchCount));
        }
    }
"""
    if "private void renderFilteredLog()" not in logger_view_text:
        class_end = logger_view_text.rfind("\n}")
        if class_end < 0:
            raise ValueError("Could not find the LoggerView class closing brace")
        logger_view_text = logger_view_text[:class_end] + "\n" + live_log_helpers + logger_view_text[class_end:]

    required_logger_tokens = (
        "mLogFilterInput.addTextChangedListener",
        "mLogBuffer.delete(0, removeChars)",
        "line.toLowerCase(Locale.ROOT).contains(filter)",
        "MAX_LOG_BUFFER_CHARS = 300000",
        "main_live_log_count_matches",
        "mLogToggle.isChecked()",
    )
    if any(token not in logger_view_text for token in required_logger_tokens):
        raise ValueError("Live log filtering, bounded retention or listener shutdown guard is incomplete")
    logger_view.write_text(logger_view_text, encoding="utf-8")

    logger_layout_text = logger_layout.read_text(encoding="utf-8")
    filter_row = """    <LinearLayout
        android:id="@+id/log_filter_row"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:gravity="center_vertical"
        android:orientation="horizontal"
        android:paddingStart="8dp"
        android:paddingEnd="8dp"
        app:layout_constraintTop_toBottomOf="@+id/top_log_view"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <EditText
            android:id="@+id/content_log_filter_input"
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:hint="@string/main_live_log_filter_hint"
            android:imeOptions="actionDone"
            android:inputType="text"
            android:maxLines="1"
            android:singleLine="true" />

        <ImageButton
            android:id="@+id/content_log_filter_clear"
            android:layout_width="40dp"
            android:layout_height="40dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="@string/main_live_log_filter_clear"
            android:scaleType="centerInside"
            android:src="@android:drawable/ic_menu_close_clear_cancel" />

        <TextView
            android:id="@+id/content_log_filter_count"
            android:layout_width="wrap_content"
            android:layout_height="match_parent"
            android:gravity="center_vertical"
            android:paddingHorizontal="8dp"
            android:text="@string/main_live_log_count_all"
            android:textSize="12sp" />
    </LinearLayout>

"""
    if 'android:id="@+id/log_filter_row"' not in logger_layout_text:
        scroll_anchor = '    <com.kdt.DefocusableScrollView\n        android:id="@+id/content_log_scroll"'
        if scroll_anchor not in logger_layout_text:
            raise ValueError("Could not find the existing live log scroll view to add its filter row")
        logger_layout_text = logger_layout_text.replace(scroll_anchor, filter_row + scroll_anchor, 1)
    old_scroll_constraint = 'app:layout_constraintTop_toBottomOf="@+id/top_log_view"'
    new_scroll_constraint = 'app:layout_constraintTop_toBottomOf="@+id/log_filter_row"'
    scroll_start = logger_layout_text.find('<com.kdt.DefocusableScrollView\n        android:id="@+id/content_log_scroll"')
    if scroll_start < 0:
        raise ValueError("Could not find the scroll view after adding the log filter row")
    scroll_end = logger_layout_text.find('>', scroll_start)
    scroll_opening = logger_layout_text[scroll_start:scroll_end + 1]
    if old_scroll_constraint in scroll_opening:
        scroll_opening = scroll_opening.replace(old_scroll_constraint, new_scroll_constraint, 1)
        logger_layout_text = logger_layout_text[:scroll_start] + scroll_opening + logger_layout_text[scroll_end + 1:]
    if (
        logger_layout_text.count('android:id="@+id/log_filter_row"') != 1
        or logger_layout_text.count('android:id="@+id/content_log_filter_input"') != 1
        or logger_layout_text.count('android:id="@+id/content_log_filter_clear"') != 1
        or logger_layout_text.count('android:id="@+id/content_log_filter_count"') != 1
        or new_scroll_constraint not in logger_layout_text[scroll_start:scroll_start + 500]
    ):
        raise ValueError("Live log filter row or scroll layout constraints are incomplete")
    logger_layout.write_text(logger_layout_text, encoding="utf-8")

    # Replace the upstream launcher artwork with a small vector mark that scales cleanly. with a small vector mark that scales cleanly.
    manifest = upstream / "app_pojavlauncher" / "src" / "main" / "AndroidManifest.xml"
    if not manifest.is_file():
        raise FileNotFoundError(f"Expected Android manifest is missing: {manifest}")
    manifest_text = manifest.read_text(encoding="utf-8")
    manifest_text = manifest_text.replace('android:icon="@mipmap/ic_launcher"', 'android:icon="@drawable/mylauncher_icon"')
    manifest_text = manifest_text.replace('android:roundIcon="@mipmap/ic_launcher_round"', 'android:roundIcon="@drawable/mylauncher_icon"')
    if (
        manifest_text.count('android:icon="@drawable/mylauncher_icon"') != 1
        or manifest_text.count('android:roundIcon="@drawable/mylauncher_icon"') != 1
        or 'android:label="@string/app_name"' not in manifest_text
        or "org.angelauramc.amethyst" in manifest_text
        or '@mipmap/ic_launcher' in manifest_text
        or '@mipmap/ic_launcher_round' in manifest_text
    ):
        raise ValueError("Manifest branding, package references, or app icon are incomplete")
    manifest.write_text(manifest_text, encoding="utf-8")
    icon = upstream / "app_pojavlauncher" / "src" / "main" / "res" / "drawable" / "mylauncher_icon.xml"
    icon.parent.mkdir(parents=True, exist_ok=True)
    icon.write_text("""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#10152F" android:pathData="M0,0h108v108h-108z" />
    <path android:fillColor="#171D3F" android:strokeColor="#6EF3FF" android:strokeWidth="3" android:pathData="M54,17 C33.6,17 17,33.6 17,54 C17,74.4 33.6,91 54,91 C74.4,91 91,74.4 91,54 C91,33.6 74.4,17 54,17 Z" />
    <path android:fillColor="#00000000" android:strokeColor="#7B61FF" android:strokeWidth="4" android:pathData="M20,37 C40,16 71,27 89,51" />
    <path android:fillColor="#6EF3FF" android:pathData="M54,43 C60.1,43 65,47.9 65,54 C65,60.1 60.1,65 54,65 C47.9,65 43,60.1 43,54 C43,47.9 47.9,43 54,43 Z" />
    <path android:fillColor="#A8FFCB" android:pathData="M83,25 C83,22 85,20 88,20 C91,20 93,22 93,25 C93,28 91,30 88,30 C85,30 83,28 83,25 Z" />
</vector>
""", encoding="utf-8")

    # The upstream auth selector also blocks local profiles until an online account exists.
    # Replace that gate so users can actually choose the offline account path.
    auth_selector = upstream / "app_pojavlauncher" / "src" / "main" / "java" / "net" / "kdt" / "pojavlaunch" / "fragments" / "SelectAuthFragment.java"
    if not auth_selector.is_file():
        raise FileNotFoundError(f"Expected authentication selector is missing: {auth_selector}")
    auth_text = auth_selector.read_text(encoding="utf-8")
    old_local_auth = "mLocalButton.setOnClickListener(v -> hasNoOnlineProfileDialog(requireActivity(), () -> Tools.swapFragment(requireActivity(), LocalLoginFragment.class, LocalLoginFragment.TAG, null)));"
    new_local_auth = "mLocalButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), LocalLoginFragment.class, LocalLoginFragment.TAG, null));"
    if old_local_auth in auth_text:
        auth_text = auth_text.replace(old_local_auth, new_local_auth)
    elif new_local_auth not in auth_text:
        raise ValueError("Could not find the local-account selection handler")
    auth_text = re.sub(
        r"^import static net\.kdt\.pojavlaunch\.Tools\.hasNoOnlineProfileDialog;\n",
        "",
        auth_text,
        flags=re.MULTILINE,
    )
    microsoft_auth_handler = "mMicrosoftButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), MicrosoftLoginFragment.class, MicrosoftLoginFragment.TAG, null));"
    if (
        "hasNoOnlineProfileDialog(requireActivity()" in auth_text
        or "import static net.kdt.pojavlaunch.Tools.hasNoOnlineProfileDialog;" in auth_text
        or auth_text.count(new_local_auth) != 1
        or auth_text.count(microsoft_auth_handler) != 1
    ):
        raise ValueError("Offline account selection is gated or an account sign-in handler is incomplete")
    auth_selector.write_text(auth_text, encoding="utf-8")

    # Keep modded-profile ownership checks, but make the blocked flow actionable:
    # users with a Minecraft Java license can go straight to Microsoft sign-in.
    profile_type = upstream / "app_pojavlauncher" / "src" / "main" / "java" / "net" / "kdt" / "pojavlaunch" / "fragments" / "ProfileTypeSelectFragment.java"
    if not profile_type.is_file():
        raise FileNotFoundError(f"Expected profile-type selector is missing: {profile_type}")
    profile_text = profile_type.read_text(encoding="utf-8")
    profile_text = replace_idempotently(
        profile_text,
        "import static net.kdt.pojavlaunch.Tools.hasNoOnlineProfileDialog;",
        "import androidx.appcompat.app.AlertDialog;",
        str(profile_type),
    )
    old_profile_gate = """    private void tryInstall(Class<? extends Fragment> fragmentClass, String tag){
        if(!hasOnlineProfile()){
            hasNoOnlineProfileDialog(requireActivity());
        } else {
            Tools.swapFragment(requireActivity(), fragmentClass, tag, null);
        }
    }"""
    new_profile_gate = """    private void tryInstall(Class<? extends Fragment> fragmentClass, String tag) {
        if (!hasOnlineProfile()) {
            new AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.no_minecraft_account_found)
                    .setMessage(R.string.feature_requires_java_account)
                    .setPositiveButton(R.string.login_online_login_label, (dialog, which) ->
                            Tools.swapFragment(requireActivity(), MicrosoftLoginFragment.class, MicrosoftLoginFragment.TAG, null))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        Tools.swapFragment(requireActivity(), fragmentClass, tag, null);
    }"""
    profile_text = replace_idempotently(
        profile_text, old_profile_gate, new_profile_gate, str(profile_type)
    )
    if (
        "hasNoOnlineProfileDialog" in profile_text
        or "if (!hasOnlineProfile())" not in profile_text
        or ".setPositiveButton(R.string.login_online_login_label" not in profile_text
        or "MicrosoftLoginFragment.class" not in profile_text
        or "Tools.swapFragment(requireActivity(), fragmentClass, tag, null);" not in profile_text
    ):
        raise ValueError("Profile installer must retain the ownership gate and offer Microsoft sign-in")
    profile_type.write_text(profile_text, encoding="utf-8")

    launcher_preferences = upstream / "app_pojavlauncher" / "src" / "main" / "java" / "net" / "kdt" / "pojavlaunch" / "prefs" / "LauncherPreferences.java"
    if not launcher_preferences.is_file():
        raise FileNotFoundError(f"Expected upstream file is missing: {launcher_preferences}")
    preferences_text = launcher_preferences.read_text(encoding="utf-8")
    preferences_text = replace_idempotently(
        preferences_text,
        "int targetSide = isDevicePowerful ? 1080 : 720;",
        "int targetSide = Tools.getTotalDeviceMemory(context) <= 2048 ? 480 : (isDevicePowerful ? 1080 : 720);",
        str(launcher_preferences),
    )
    preferences_text = replace_idempotently(
        preferences_text,
        "if (deviceRam < 2048) return 656;",
        "if (deviceRam <= 2048) return 512;",
        str(launcher_preferences),
    )
    if "int targetSide = Tools.getTotalDeviceMemory(context) <= 2048 ? 480 : (isDevicePowerful ? 1080 : 720);" not in preferences_text:
        raise ValueError("Low-memory resolution target must override the powerful-device heuristic")
    if "int targetSide = isDevicePowerful ? 1080 :" in preferences_text:
        raise ValueError("Powerful-device heuristic incorrectly overrides the 2 GB RAM limit")
    if "if (deviceRam < 2048) return 656;" in preferences_text or "if (deviceRam <= 2048) return 656;" in preferences_text:
        raise ValueError("The low-memory RAM allocation was not reduced at the 2 GB boundary")
    if "if (deviceRam <= 2048) return 512;" not in preferences_text:
        raise ValueError("The 1.5–2 GB RAM tier must default to a 512 MB heap")
    launcher_preferences.write_text(preferences_text, encoding="utf-8")

    validate_xml_files((
        strings,
        colors,
        login_layout,
        upstream / "app_pojavlauncher" / "src" / "main" / "res" / "layout" / "fragment_launcher.xml",
        upstream / "app_pojavlauncher" / "src" / "main" / "res" / "layout-land" / "fragment_launcher.xml",
        manifest,
        icon,
        *localized_string_files,
    ))

    print("VoidCraft Launcher patch OK")
    print("  app label: VoidCraft Launcher")
    print("  application ID: com.mf.mylauncher (debug adds .debug)")
    print("  storage/share authorities: customized")
    print("  low-memory defaults: 296/448/512 MB RAM tiers and ~480 px short-edge target for <=2 GB")


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(f"Usage: {Path(argv[0]).name} PATH_TO_AMETHYST_SOURCE", file=sys.stderr)
        return 2
    try:
        patch_upstream(Path(argv[1]).resolve())
    except (OSError, ValueError) as exc:
        print(f"VoidCraft Launcher patch FAILED: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))