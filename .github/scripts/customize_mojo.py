#!/usr/bin/env python3
"""Apply VoidCraft branding to a pinned MojoLauncher checkout without replacing its runtime."""
from __future__ import annotations
import re
import shutil
import sys
from pathlib import Path

def replace_or_keep(text: str, old: str, new: str, label: str) -> str:
    if old in text:
        return text.replace(old, new)
    if new in text:
        return text
    raise ValueError(f"Expected token missing from {label}: {old!r}")

def set_color(text: str, name: str, value: str, label: str) -> str:
    pattern = re.compile(r'(<color\s+name="' + re.escape(name) + r'"[^>]*>).*?(</color>)', re.DOTALL)
    result, count = pattern.subn(lambda m: m.group(1) + value + m.group(2), text, count=1)
    if count != 1:
        raise ValueError(f"Required color resource {name!r} not found in {label}")
    return result

def set_string(text: str, name: str, value: str, label: str, required: bool) -> str:
    pattern = re.compile(r'(<string\s+name="' + re.escape(name) + r'"(?:\s+[^>]*)?>).*?(</string>)', re.DOTALL)
    result, count = pattern.subn(lambda m: m.group(1) + value + m.group(2), text, count=1)
    if required and count != 1:
        raise ValueError(f"Required string resource {name!r} not found in {label}")
    return result

def patch_upstream(root: Path) -> None:
    app = root / "app_mojo"
    gradle = app / "build.gradle"
    res = app / "src/main/res"
    colors = res / "values/colors.xml"
    for path in (gradle, colors, res / "values/strings.xml", root / "settings.gradle", root / "LICENSE"):
        if not path.is_file():
            raise FileNotFoundError(f"Required pinned MojoLauncher file missing: {path}")

    build = gradle.read_text(encoding="utf-8")
    build = replace_or_keep(build, 'applicationId "git.artdeell.mjlaunch"', 'applicationId "com.mf.mylauncher"', str(gradle))
    build = build.replace("git.artdeell.mjlaunch", "com.mf.mylauncher")
    build = replace_or_keep(build, 'return "justicia-', 'return "voidcraft-', str(gradle))
    sdk = re.search(r"\bminSdkVersion\s+(\d+)\b", build)
    if not sdk or int(sdk.group(1)) > 28:
        raise ValueError("Cannot verify MojoLauncher supports Android 9/API 28")
    if 'applicationId "com.mf.mylauncher"' not in build or "git.artdeell.mjlaunch" in build:
        raise ValueError("VoidCraft application ID/provider authorities were not fully applied")
    if not re.search(r"namespace\s+['\"]git\.artdeell\.mojo['\"]", build):
        raise ValueError("Unexpected Java namespace; do not rename classes blindly")
    gradle.write_text(build, encoding="utf-8")

    label_found = False
    for strings in sorted(res.glob("values*/strings.xml")):
        content = strings.read_text(encoding="utf-8")
        label_found = label_found or bool(re.search(r'<string\s+name="app_short_name"', content))
        content = set_string(content, "app_short_name", "VoidCraft Launcher", str(strings), strings == res / "values/strings.xml")
        content = set_string(content, "error_fatal", "VoidCraft Launcher has unexpectedly crashed", str(strings), False)
        strings.write_text(content, encoding="utf-8")
    if not label_found:
        raise ValueError("No app_short_name string exists in source locales")

    content = colors.read_text(encoding="utf-8")
    for name, value in {
        "minebutton_color": "#8D6BFF",
        "background_app": "#0D0A14",
        "background_overlay": "#362747",
        "background_status_bar": "#100C19",
        "background_bottom_bar": "#15101F",
        "primary_text": "#F7F2FF",
        "secondary_text": "#B7AACD",
    }.items():
        content = set_color(content, name, value, str(colors))
    colors.write_text(content, encoding="utf-8")

    shutil.copyfile(root / "LICENSE", root / "VOIDCRAFT_MOJO_LICENSE.txt")
    (root / "VOIDCRAFT_LAUNCHER_BUILD_INFO.txt").write_text(
        "Product: VoidCraft Launcher\n"
        "Upstream: https://github.com/MojoLauncher/MojoLauncher\n"
        "Branch: v3_openjdk\n"
        "Pinned commit: 8ffba8f4ea0b9466854aa9f7a375c77b4fb96bdd\n"
        "Upstream license: GNU LGPL-3.0 (see VOIDCRAFT_MOJO_LICENSE.txt)\n"
        "Branding-only changes: app ID/provider authorities, labels, version prefix, and palette.\n"
        "Mojo launcher/runtime/native/rendering/download/account code is retained.\n",
        encoding="utf-8",
    )

if __name__ == "__main__":
    root = Path(sys.argv[1] if len(sys.argv) > 1 else "upstream").resolve()
    patch_upstream(root)
    print(f"Applied VoidCraft branding to pinned MojoLauncher source at {root}")
