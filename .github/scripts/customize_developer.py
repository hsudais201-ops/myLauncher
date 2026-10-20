#!/usr/bin/env python3
"""Add VoidCraft's informational Developer & Testing policy screen to the pinned engine."""
from __future__ import annotations

import html
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


STRINGS = {
    "voidcraft_dev_testing_title": "Developer & Testing",
    "voidcraft_dev_testing_summary": "Security rules, restricted capabilities, and the staged testing process.",
    "voidcraft_dev_category_stages": "Development and test stages",
    "voidcraft_dev_stage_local_title": "Stage 1 — Computer tests (required)",
    "voidcraft_dev_stage_local_summary": (
        "Run source and regression/security checks on a computer, build the APK, verify its "
        "signature and SHA-256, then install and launch it in the Android 9 emulator. "
        "Emulator launch alone does not prove full gameplay compatibility."
    ),
    "voidcraft_dev_stage_private_title": "Stage 2 — Private online beta (not active)",
    "voidcraft_dev_stage_private_summary": (
        "Not configured yet. Before enabling online tests, use a genuinely access-controlled "
        "private host, approve named owner/support testers, limit roles, use revocable access, "
        "and distribute only reviewed builds with verified signatures and checksums. "
        "Artifacts in this public repository are not private."
    ),
    "voidcraft_dev_stage_web_title": "Stage 3 — Web testing portal (future)",
    "voidcraft_dev_stage_web_summary": (
        "Not implemented. Do not announce a public portal until authentication, tester "
        "authorization, privacy controls, rate limits, audit logs, security review, and rollback "
        "are implemented and tested."
    ),
    "voidcraft_dev_category_access": "Access and build security",
    "voidcraft_dev_access_title": "Owner and support-team permissions",
    "voidcraft_dev_access_summary": (
        "The owner approves builds and tester access. Approved support testers may install "
        "approved test builds and submit reports only. Do not give testers signing keys, "
        "production secrets, or access to other users' private data. Revoke access promptly "
        "when it is no longer needed."
    ),
    "voidcraft_dev_verify_title": "Build verification",
    "voidcraft_dev_verify_summary": (
        "Test only reviewed commits. Run regression and security checks before building, verify "
        "the APK signature and SHA-256, pin and review dependencies, and keep secrets out of "
        "source code and logs. A debug APK is for testing, not a production release."
    ),
    "voidcraft_dev_category_restrictions": "Restricted and prohibited capabilities",
    "voidcraft_dev_restrict_code_title": "No untrusted executable code",
    "voidcraft_dev_restrict_code_summary": (
        "Do not execute uploaded scripts, shell commands, arbitrary JAR/DEX/native libraries, "
        "self-modifying modules, or remote code. Future extensions must use a reviewed package "
        "format, signed manifests, and a narrow allowlist of launcher APIs."
    ),
    "voidcraft_dev_restrict_data_title": "No credential theft or silent data upload",
    "voidcraft_dev_restrict_data_summary": (
        "Never collect or upload passwords, Microsoft/Xbox/session tokens, cookies, private "
        "account data, worlds, screenshots, or full logs without a clear purpose and explicit "
        "user consent. Diagnostics must be opt-in, size-limited, previewable, and redacted."
    ),
    "voidcraft_dev_restrict_system_title": "No security or licensing bypass",
    "voidcraft_dev_restrict_system_summary": (
        "No root/exploit attempts, permission bypass, account impersonation, Minecraft ownership "
        "or licensing bypass, or evasion of provider security controls, documented API terms, "
        "or rate limits."
    ),
    "voidcraft_dev_restrict_files_title": "No unsafe file or update actions",
    "voidcraft_dev_restrict_files_summary": (
        "No silent world/mod deletion, overwrites, unrestricted filesystem access, hidden "
        "telemetry, undisclosed tracking, unverified downloads, unsigned updates, or persistent "
        "background networking without a disclosed and approved reason."
    ),
    "voidcraft_dev_restrict_sources_title": "No unreviewed community submissions",
    "voidcraft_dev_restrict_sources_summary": (
        "Future community features must disclose publisher, version, compatibility, requested "
        "permissions, and source/provenance information. Review submissions, scan packages, "
        "record releases, and provide disable and rollback controls."
    ),
    "voidcraft_dev_privacy_title": "Bug report privacy",
    "voidcraft_dev_privacy_summary": (
        "Ask before sharing diagnostics. Redact tokens and personal paths, show a preview, let "
        "the user cancel, and never attach worlds or account secrets by default."
    ),
}

PREF_ENTRY = """    <PreferenceCategory android:title="@string/voidcraft_dev_testing_title">
        <Preference
            android:key="voidcraft_developer_testing"
            android:title="@string/voidcraft_dev_testing_title"
            android:summary="@string/voidcraft_dev_testing_summary"
            android:fragment="net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceDeveloperFragment"
            android:persistent="false" />
    </PreferenceCategory>
"""

PREFERENCE_SCREEN = """<?xml version="1.0" encoding="utf-8"?>
<PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
    <net.kdt.pojavlaunch.prefs.BackButtonPreference />

    <PreferenceCategory android:title="@string/voidcraft_dev_category_stages">
        <Preference
            android:key="voidcraft_dev_stage_local"
            android:title="@string/voidcraft_dev_stage_local_title"
            android:summary="@string/voidcraft_dev_stage_local_summary"
            android:persistent="false"
            android:selectable="false" />
        <Preference
            android:key="voidcraft_dev_stage_private"
            android:title="@string/voidcraft_dev_stage_private_title"
            android:summary="@string/voidcraft_dev_stage_private_summary"
            android:persistent="false"
            android:selectable="false" />
        <Preference
            android:key="voidcraft_dev_stage_web"
            android:title="@string/voidcraft_dev_stage_web_title"
            android:summary="@string/voidcraft_dev_stage_web_summary"
            android:persistent="false"
            android:selectable="false" />
    </PreferenceCategory>

    <PreferenceCategory android:title="@string/voidcraft_dev_category_access">
        <Preference
            android:key="voidcraft_dev_access"
            android:title="@string/voidcraft_dev_access_title"
            android:summary="@string/voidcraft_dev_access_summary"
            android:persistent="false"
            android:selectable="false" />
        <Preference
            android:key="voidcraft_dev_verify"
            android:title="@string/voidcraft_dev_verify_title"
            android:summary="@string/voidcraft_dev_verify_summary"
            android:persistent="false"
            android:selectable="false" />
    </PreferenceCategory>

    <PreferenceCategory android:title="@string/voidcraft_dev_category_restrictions">
        <Preference
            android:key="voidcraft_dev_restrict_code"
            android:title="@string/voidcraft_dev_restrict_code_title"
            android:summary="@string/voidcraft_dev_restrict_code_summary"
            android:persistent="false"
            android:selectable="false" />
        <Preference
            android:key="voidcraft_dev_restrict_data"
            android:title="@string/voidcraft_dev_restrict_data_title"
            android:summary="@string/voidcraft_dev_restrict_data_summary"
            android:persistent="false"
            android:selectable="false" />
        <Preference
            android:key="voidcraft_dev_restrict_system"
            android:title="@string/voidcraft_dev_restrict_system_title"
            android:summary="@string/voidcraft_dev_restrict_system_summary"
            android:persistent="false"
            android:selectable="false" />
        <Preference
            android:key="voidcraft_dev_restrict_files"
            android:title="@string/voidcraft_dev_restrict_files_title"
            android:summary="@string/voidcraft_dev_restrict_files_summary"
            android:persistent="false"
            android:selectable="false" />
        <Preference
            android:key="voidcraft_dev_restrict_sources"
            android:title="@string/voidcraft_dev_restrict_sources_title"
            android:summary="@string/voidcraft_dev_restrict_sources_summary"
            android:persistent="false"
            android:selectable="false" />
        <Preference
            android:key="voidcraft_dev_privacy"
            android:title="@string/voidcraft_dev_privacy_title"
            android:summary="@string/voidcraft_dev_privacy_summary"
            android:persistent="false"
            android:selectable="false" />
    </PreferenceCategory>
</PreferenceScreen>
"""

FRAGMENT = """package net.kdt.pojavlaunch.prefs.screens;

import android.os.Bundle;

import net.kdt.pojavlaunch.R;

/**
 * Read-only explanation of VoidCraft's staged test and extension security policy.
 * This screen intentionally does not grant permissions or claim to enable online access.
 */
public class LauncherPreferenceDeveloperFragment extends LauncherPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_developer);
    }
}
"""


def upsert_string(text: str, name: str, value: str, path: Path) -> str:
    """Insert or replace one Android string resource without creating duplicates."""
    safe_value = html.escape(value, quote=False)
    pattern = re.compile(
        r'(<string\s+name="' + re.escape(name) + r'"[^>]*>).*?(</string>)',
        re.DOTALL,
    )
    updated, count = pattern.subn(
        lambda match: match.group(1) + safe_value + match.group(2),
        text,
        count=1,
    )
    if count:
        return updated
    if re.search(r'<string\s+name="' + re.escape(name) + r'"', text):
        raise ValueError(f"Unsupported existing string declaration for {name!r} in {path}")
    closing = re.search(r"</resources\s*>", text)
    if not closing:
        raise ValueError(f"Could not find </resources> in {path}")
    line = f'    <string name="{name}">{safe_value}</string>\n'
    return text[:closing.start()] + line + text[closing.start():]


def write_managed_file(path: Path, content: str) -> None:
    """Create an extension file or accept an identical prior patch; do not overwrite unknown code."""
    path.parent.mkdir(parents=True, exist_ok=True)
    normalized = content.rstrip() + "\n"
    if path.exists():
        current = path.read_text(encoding="utf-8")
        if current != normalized:
            raise ValueError(
                f"Refusing to overwrite an unexpected existing file: {path}. "
                "Review the upstream change and update the patch deliberately."
            )
        return
    path.write_text(normalized, encoding="utf-8")


def patch_upstream(upstream: Path) -> None:
    app = upstream / "app_pojavlauncher"
    res = app / "src/main/res"
    main_preferences = res / "xml/pref_main.xml"
    strings_path = res / "values/strings.xml"
    developer_preferences = res / "xml/pref_developer.xml"
    fragment_path = (
        app / "src/main/java/net/kdt/pojavlaunch/prefs/screens/"
        "LauncherPreferenceDeveloperFragment.java"
    )

    for path in (main_preferences, strings_path):
        if not path.is_file():
            raise FileNotFoundError(f"Required developer-screen input is missing: {path}")

    main_text = main_preferences.read_text(encoding="utf-8")
    entry_exists = 'android:key="voidcraft_developer_testing"' in main_text
    fragment_name = "net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceDeveloperFragment"
    if entry_exists:
        if fragment_name not in main_text:
            raise ValueError("The existing Developer & Testing preference points to an unexpected fragment")
    else:
        closing = re.search(r"</PreferenceScreen\s*>", main_text)
        if not closing:
            raise ValueError(f"Could not find </PreferenceScreen> in {main_preferences}")
        main_text = main_text[:closing.start()] + PREF_ENTRY + main_text[closing.start():]
    main_preferences.write_text(main_text, encoding="utf-8")

    strings_text = strings_path.read_text(encoding="utf-8")
    for name, value in STRINGS.items():
        strings_text = upsert_string(strings_text, name, value, strings_path)
    strings_path.write_text(strings_text, encoding="utf-8")

    write_managed_file(developer_preferences, PREFERENCE_SCREEN)
    write_managed_file(fragment_path, FRAGMENT)

    for path in (main_preferences, strings_path, developer_preferences):
        try:
            ET.parse(path)
        except ET.ParseError as exc:
            raise ValueError(f"Generated Android XML is invalid at {path}: {exc}") from exc

    print("VoidCraft Developer & Testing screen patch OK")
    print("  added staged test guidance, role boundaries, and restricted-capability list")
    print("  no Android permissions, network endpoints, or online access gates were added")


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(f"Usage: {Path(argv[0]).name} PATH_TO_AMETHYST_SOURCE", file=sys.stderr)
        return 2
    try:
        patch_upstream(Path(argv[1]).resolve())
    except (OSError, ValueError) as exc:
        print(f"VoidCraft Developer & Testing patch FAILED: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
