#!/usr/bin/env python3
"""Audit the pinned Amethyst Android build inputs.

This reports source declarations. It does not prove every runtime, ABI, renderer,
Minecraft version, or physical device works.
"""
from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
VALID_ABIS = {"armeabi-v7a", "arm64-v8a", "x86", "x86_64", "riscv64"}
REQUIRED_PATHS = (
    Path("app_pojavlauncher/build.gradle"),
    Path("app_pojavlauncher/src/main/AndroidManifest.xml"),
    Path("app_pojavlauncher/src/main/jni/Application.mk"),
)


class AuditError(ValueError):
    """Raised when required build facts are missing or internally inconsistent."""


def _require_file(path: Path) -> str:
    if not path.is_file():
        raise AuditError(f"Required upstream file is missing: {path}")
    return path.read_text(encoding="utf-8")


def _setting(pattern: str, text: str, label: str) -> int:
    match = re.search(pattern, text, flags=re.MULTILINE)
    if not match:
        raise AuditError(f"Could not read {label} from app_pojavlauncher/build.gradle")
    return int(match.group(1))


def _bool_attr(element: ET.Element, name: str) -> str:
    value = element.get(ANDROID_NS + name)
    return value if value is not None else "not declared"


def audit_upstream(upstream: Path) -> tuple[str, str]:
    """Return (Markdown report, concise summary) or raise AuditError."""
    gradle_path = upstream / REQUIRED_PATHS[0]
    manifest_path = upstream / REQUIRED_PATHS[1]
    application_mk_path = upstream / REQUIRED_PATHS[2]

    gradle = _require_file(gradle_path)
    manifest_text = _require_file(manifest_path)
    application_mk = _require_file(application_mk_path)

    compile_sdk = _setting(r"\bcompileSdk\s*=\s*(\d+)\b", gradle, "compileSdk")
    min_sdk = _setting(r"\bminSdkVersion\s+(\d+)\b", gradle, "minSdkVersion")
    target_sdk = _setting(r"\btargetSdkVersion\s+(\d+)\b", gradle, "targetSdkVersion")

    if min_sdk < 1:
        raise AuditError(f"minSdkVersion must be positive, got {min_sdk}")
    if target_sdk < min_sdk:
        raise AuditError(f"targetSdkVersion {target_sdk} is below minSdkVersion {min_sdk}")
    if compile_sdk < target_sdk:
        raise AuditError(f"compileSdk {compile_sdk} is below targetSdkVersion {target_sdk}")

    platform_match = re.search(r"(?m)^\s*APP_PLATFORM\s*:=\s*android-(\d+)\s*$", application_mk)
    if not platform_match:
        raise AuditError("Could not parse APP_PLATFORM from app_pojavlauncher/src/main/jni/Application.mk")
    native_platform = int(platform_match.group(1))
    if native_platform > min_sdk:
        raise AuditError(
            f"Native APP_PLATFORM android-{native_platform} exceeds app minSdkVersion {min_sdk}"
        )

    abi_match = re.search(r"(?m)^\s*APP_ABI\s*:=\s*(.*?)\s*$", application_mk)
    if not abi_match:
        raise AuditError("Could not parse APP_ABI from app_pojavlauncher/src/main/jni/Application.mk")
    abis = abi_match.group(1).split()
    if not abis:
        raise AuditError("APP_ABI is empty; no native ABI build targets are declared")
    if len(set(abis)) != len(abis):
        raise AuditError(f"APP_ABI contains duplicate values: {abis}")
    unknown_abis = sorted(set(abis) - VALID_ABIS)
    if unknown_abis:
        raise AuditError(f"APP_ABI contains unrecognized Android ABI values: {unknown_abis}")

    try:
        manifest_root = ET.fromstring(manifest_text)
    except ET.ParseError as exc:
        raise AuditError(f"AndroidManifest.xml is not well-formed XML: {exc}") from exc

    permissions: list[tuple[str, str]] = []
    permission_tags = {"uses-permission", "uses-permission-sdk-23", "uses-permission-sdk-m"}
    for child in manifest_root:
        if child.tag in permission_tags:
            name = child.get(ANDROID_NS + "name")
            if name:
                permissions.append((name, child.get(ANDROID_NS + "maxSdkVersion", "—")))

    permission_names = {name for name, _ in permissions}
    if "android.permission.INTERNET" not in permission_names:
        raise AuditError("AndroidManifest.xml does not declare INTERNET permission")

    application = manifest_root.find("application")
    if application is None:
        raise AuditError("AndroidManifest.xml has no application element")

    allow_backup = _bool_attr(application, "allowBackup")
    full_backup_content = _bool_attr(application, "fullBackupContent")
    data_extraction_rules = _bool_attr(application, "dataExtractionRules")
    cleartext_traffic = _bool_attr(application, "usesCleartextTraffic")
    resizeable_activity = _bool_attr(application, "resizeableActivity")

    features = []
    for feature in manifest_root.findall("uses-feature"):
        name = feature.get(ANDROID_NS + "name", "(unnamed)")
        required = feature.get(ANDROID_NS + "required", "true")
        gl_es = feature.get(ANDROID_NS + "glEsVersion")
        detail = f"required={required}"
        if gl_es:
            detail += f", GLES={gl_es}"
        features.append((name, detail))

    components = []
    for kind in ("activity", "activity-alias", "service", "receiver", "provider"):
        for element in application.findall(kind):
            name = element.get(ANDROID_NS + "name", "(unnamed)")
            exported = element.get(ANDROID_NS + "exported", "not declared")
            has_filter = "yes" if element.find("intent-filter") is not None else "no"
            foreground_type = element.get(ANDROID_NS + "foregroundServiceType")
            additional = f"foregroundServiceType={foreground_type}" if foreground_type else "—"
            components.append((kind, name, exported, has_filter, additional))

    review_notes = [
        "Native ABI declarations indicate build targets only. Runtime payloads, graphics translation, drivers, Java availability, Minecraft compatibility and gameplay need separate checks on each claimed device/ABI.",
        "The manifest declares foreground-service types. Review actual work type and lifecycle behavior against Android version-specific restrictions before expanding background activity.",
    ]
    if allow_backup.lower() == "true" and full_backup_content == "not declared" and data_extraction_rules == "not declared":
        review_notes.append(
            "android:allowBackup is true and this manifest does not explicitly reference fullBackupContent or dataExtractionRules. Review whether account/session material and user files are appropriately excluded from device/cloud backup."
        )
    if "android.permission.RECORD_AUDIO" in permission_names:
        review_notes.append(
            "RECORD_AUDIO is declared. Confirm which feature requires microphone access, request runtime permission only when needed, and document the user-facing purpose."
        )
    if "android.permission.POST_NOTIFICATIONS" in permission_names:
        review_notes.append(
            "POST_NOTIFICATIONS is declared. Verify permission-denied and notifications-disabled paths; lack of notification permission should not fail the underlying task."
        )

    lines = [
        "# Pinned Amethyst Android source audit",
        "",
        "> Generated from the checked-out source. This is a declaration audit, not device-compatibility certification.",
        "",
        "## Build configuration",
        "",
        "| Setting | Declared value |",
        "|---|---:|",
        f"| Android compile SDK | {compile_sdk} |",
        f"| Android minimum SDK (minSdkVersion) | {min_sdk} |",
        f"| Android target SDK (targetSdkVersion) | {target_sdk} |",
        f"| Native NDK platform | android-{native_platform} |",
        f"| Native ABI build targets | {', '.join(abis)} |",
        "",
        "The repository documents Android 9/API 28 and newer as its intended product-support baseline. A lower APK minimum SDK does not promise support below that documented baseline.",
        "",
        "## Manifest permissions",
        "",
        "| Permission | maxSdkVersion |",
        "|---|---:|",
    ]
    lines.extend(f"| {name} | {max_sdk} |" for name, max_sdk in sorted(set(permissions)))
    lines.extend([
        "",
        "## Application declarations",
        "",
        "| Attribute | Declared value |",
        "|---|---|",
        f"| allowBackup | {allow_backup} |",
        f"| fullBackupContent | {full_backup_content} |",
        f"| dataExtractionRules | {data_extraction_rules} |",
        f"| usesCleartextTraffic | {cleartext_traffic} |",
        f"| resizeableActivity | {resizeable_activity} |",
        "",
        "## Declared device features",
        "",
        "| Feature | Settings |",
        "|---|---|",
    ])
    lines.extend(f"| {name} | {detail} |" for name, detail in features)
    if not features:
        lines.append("| (none declared) | — |")

    lines.extend([
        "",
        "## Declared app components",
        "",
        "| Component type | Name | Exported | Has intent filter | Additional |",
        "|---|---|---|---|---|",
    ])
    lines.extend(
        f"| {kind} | {name} | {exported} | {has_filter} | {additional} |"
        for kind, name, exported, has_filter, additional in components
    )
    lines.extend(["", "## Follow-up review notes", ""])
    lines.extend(f"- {note}" for note in review_notes)
    lines.extend([
        "",
        "## Limitations",
        "",
        "- Source declarations do not prove that native libraries or Java runtimes are present and usable for every declared ABI.",
        "- The current automated emulator smoke test targets API 28/x86_64 and validates launcher startup, not Minecraft world launch or physical-device gameplay.",
        "- Review this report alongside FEATURE_AUDIT.md, the actual APK contents, and device acceptance results.",
        "",
    ])
    summary = (
        f"Upstream source audit: compile SDK {compile_sdk}; min SDK {min_sdk}; "
        f"target SDK {target_sdk}; native platform android-{native_platform}; ABIs {', '.join(abis)}; "
        f"{len(set(permissions))} distinct permissions."
    )
    return "\n".join(lines), summary


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("upstream", type=Path, help="Path to checked-out Amethyst-Android repository")
    parser.add_argument("--output", type=Path, help="Markdown report path (default: stdout)")
    args = parser.parse_args(argv)

    try:
        report, summary = audit_upstream(args.upstream)
    except (AuditError, OSError) as exc:
        print(f"Upstream audit failed: {exc}", file=sys.stderr)
        return 1

    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report, encoding="utf-8")
    else:
        print(report)
    print(summary, file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
