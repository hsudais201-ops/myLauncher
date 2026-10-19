#!/usr/bin/env python3
"""Inject the additive, data-only Feature Store into the pinned Amethyst source tree."""
from __future__ import annotations

import hashlib
import json
import re
import shutil
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
APP_ROOT = Path("app_pojavlauncher/src/main")
PACKAGE_ROOT = Path("net/kdt/pojavlaunch")
SOURCE_ROOT = REPO_ROOT / "feature-store"
MAX_PACKAGE_BYTES = 1024 * 1024


def _replace_once(text: str, old: str, new: str, label: str) -> str:
    if old in text:
        return text.replace(old, new, 1)
    if new in text:
        return text
    raise ValueError(f"Expected insertion point missing in {label}: {old!r}")


def _add_string_resources(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    values = {
        "mylauncher_feature_store": "Feature Store",
        "mylauncher_feature_store_hint": "Browse, install, create, export, and manage optional launcher features.",
    }
    for name, value in values.items():
        match = re.search(r'<string\s+name="' + re.escape(name) + r'"(?:\s[^>]*)?>', text)
        if match:
            continue
        closing = text.rfind("</resources>")
        if closing < 0:
            raise ValueError(f"Missing resources closing tag in {path}")
        text = text[:closing] + f'    <string name="{name}">{value}</string>\n' + text[closing:]
    ET.fromstring(text)
    path.write_text(text, encoding="utf-8")


def _patch_layout(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    button_id = 'android:id="@+id/mylauncher_feature_store_button"'
    if button_id not in text:
        pattern = re.compile(
            r'(<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/mylauncher_account_button".*?/>[ \t]*\n)',
            re.DOTALL,
        )
        match = pattern.search(text)
        if not match:
            raise ValueError(f"Could not find the existing account shortcut in {path}")
        feature_button = """            <com.kdt.mcgui.LauncherMenuButton
                android:id="@+id/mylauncher_feature_store_button"
                style="@style/LauncherMenuButton.Universal"
                android:layout_width="match_parent"
                android:drawableStart="@android:drawable/ic_menu_add"
                android:text="@string/mylauncher_feature_store"
                android:contentDescription="@string/mylauncher_feature_store_hint"
                app:layout_constraintTop_toBottomOf="@id/mylauncher_account_button" />

"""
        text = text[:match.end(1)] + feature_button + text[match.end(1):]
    if text.count(button_id) != 1:
        raise ValueError(f"Feature Store button is missing or duplicated in {path}")
    button = re.search(
        r'<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/mylauncher_feature_store_button".*?/>',
        text,
        re.DOTALL,
    )
    if not button or 'app:layout_constraintTop_toBottomOf="@id/mylauncher_account_button"' not in button.group(0):
        raise ValueError(f"Feature Store button is not connected to the existing home-screen shortcut list in {path}")
    text = "\n".join(line.rstrip() for line in text.splitlines()) + "\n"
    path.write_text(text, encoding="utf-8")


def _patch_manifest(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    activity = 'android:name=".FeatureStoreActivity"'
    if activity not in text:
        anchor = '        <activity\n            android:name=".LauncherActivity"'
        addition = (
            '        <activity\n'
            '            android:name=".FeatureStoreActivity"\n'
            '            android:exported="false"\n'
            '            android:label="@string/mylauncher_feature_store" />\n'
        )
        text = _replace_once(text, anchor, addition + anchor, str(path))
    if text.count(activity) != 1:
        raise ValueError("FeatureStoreActivity manifest entry is missing or duplicated")
    ET.fromstring(text)
    path.write_text(text, encoding="utf-8")


def _patch_menu(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    import_anchor = "import net.kdt.pojavlaunch.CustomControlsActivity;\n"
    import_line = "import net.kdt.pojavlaunch.FeatureStoreActivity;\n"
    if import_line not in text:
        text = _replace_once(text, import_anchor, import_anchor + import_line, str(path))

    account_binding = "Button mMyLauncherAccountButton = view.findViewById(R.id.mylauncher_account_button);"
    feature_binding = "Button mMyLauncherFeatureStoreButton = view.findViewById(R.id.mylauncher_feature_store_button);"
    if feature_binding not in text:
        text = _replace_once(text, account_binding, account_binding + "\n        " + feature_binding, str(path))

    listener = (
        "mMyLauncherFeatureStoreButton.setOnClickListener(v -> "
        "startActivity(new Intent(requireContext(), FeatureStoreActivity.class)));"
    )
    if listener not in text:
        anchor = "mVersionSpinner = view.findViewById(R.id.mc_version_spinner);"
        text = _replace_once(text, anchor, listener + "\n        " + anchor, str(path))

    required = (import_line, feature_binding, listener, "import android.content.Intent;",
                "import android.widget.Button;")
    for item in required:
        if text.count(item) != 1:
            raise ValueError(f"Feature Store menu integration is missing or duplicated: {item}")
    path.write_text(text, encoding="utf-8")


def _patch_base_activity(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    anchor = "Tools.ignoreNotch(shouldIgnoreNotch(),this);"
    call = "FeatureTheme.apply(this);"
    if call not in text:
        text = _replace_once(text, anchor, anchor + "\n        " + call, str(path))
    if text.count(call) != 1:
        raise ValueError("FeatureTheme must be applied exactly once in BaseActivity")
    path.write_text(text, encoding="utf-8")


def _validate_store_catalog() -> None:
    """Validate repository-hosted packages for the public catalog; never copy them into the APK."""
    source_assets = SOURCE_ROOT
    catalog_path = source_assets / "catalog.json"
    packages_path = source_assets / "packages"
    if not catalog_path.is_file() or not packages_path.is_dir():
        raise FileNotFoundError("The public feature catalog or package folder is missing")
    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    if catalog.get("schemaVersion") != 1 or not isinstance(catalog.get("features"), list):
        raise ValueError("The public Feature Store catalog schema is invalid")

    seen: set[str] = set()
    for entry in catalog["features"]:
        if not isinstance(entry, dict):
            raise ValueError("Catalog feature entry is not an object")
        if "category" in entry:
            raise ValueError("Feature categories are not part of the catalog schema")
        feature_id = entry.get("id", "")
        expected = entry.get("sha256", "")
        package_url = entry.get("packageUrl", "")
        if not isinstance(package_url, str) or not package_url.startswith("https://"):
            raise ValueError(f"Feature package URL must use HTTPS: {feature_id!r}")
        if not re.fullmatch(r"[a-z0-9][a-z0-9._-]{1,79}", feature_id) or ".." in feature_id:
            raise ValueError(f"Unsafe catalog feature id: {feature_id!r}")
        if feature_id in seen:
            raise ValueError(f"Duplicate catalog feature id: {feature_id}")
        seen.add(feature_id)
        package_name = package_url.split("?", 1)[0].rsplit("/", 1)[-1]
        if not re.fullmatch(r"[A-Za-z0-9._-]{1,100}\.vcfeature", package_name):
            raise ValueError(f"Unsafe package filename in URL for {feature_id}")
        if not re.fullmatch(r"[a-fA-F0-9]{64}", expected):
            raise ValueError(f"Invalid SHA-256 in catalog for {feature_id}")
        package_path = packages_path / package_name
        if not package_path.is_file() or package_path.stat().st_size > MAX_PACKAGE_BYTES:
            raise ValueError(f"Feature package is missing or too large: {package_path}")
        raw = package_path.read_bytes()
        if hashlib.sha256(raw).hexdigest() != expected.lower():
            raise ValueError(f"Feature package SHA-256 mismatch: {package_path}")
        manifest = json.loads(raw.decode("utf-8"))
        if "category" in manifest:
            raise ValueError(f"Feature categories are not supported in {feature_id}")
        if manifest.get("schemaVersion") != 1 or manifest.get("id") != feature_id:
            raise ValueError(f"Catalog id/schema does not match package: {feature_id}")
        if not all(isinstance(manifest.get(key), str) and manifest[key].strip()
                   for key in ("name", "version", "author", "owner")):
            raise ValueError(f"Feature metadata is incomplete: {feature_id}")


def patch_upstream(upstream: Path, repo_root: Path = REPO_ROOT) -> None:
    global REPO_ROOT, SOURCE_ROOT
    previous_repo_root, previous_source_root = REPO_ROOT, SOURCE_ROOT
    REPO_ROOT = repo_root.resolve()
    SOURCE_ROOT = REPO_ROOT / "feature-store"
    try:
        app = upstream / APP_ROOT
        manifest = app / "AndroidManifest.xml"
        strings = app / "res" / "values" / "strings.xml"
        base_activity = app / "java" / PACKAGE_ROOT / "BaseActivity.java"
        main_menu = app / "java" / PACKAGE_ROOT / "fragments" / "MainMenuFragment.java"
        layouts = (
            app / "res" / "layout" / "fragment_launcher.xml",
            app / "res" / "layout-land" / "fragment_launcher.xml",
        )
        activity_source = SOURCE_ROOT / "src" / "main" / "java" / PACKAGE_ROOT / "FeatureStoreActivity.java"
        theme_source = SOURCE_ROOT / "src" / "main" / "java" / PACKAGE_ROOT / "FeatureTheme.java"
        required = (manifest, strings, base_activity, main_menu, *layouts, activity_source, theme_source)
        for path in required:
            if not path.is_file():
                raise FileNotFoundError(f"Required Feature Store source file is missing: {path}")

        _validate_store_catalog()
        for layout in layouts:
            _patch_layout(layout)
        _add_string_resources(strings)
        _patch_manifest(manifest)
        _patch_menu(main_menu)
        _patch_base_activity(base_activity)

        java_destination = app / "java" / PACKAGE_ROOT
        java_destination.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(activity_source, java_destination / "FeatureStoreActivity.java")
        shutil.copyfile(theme_source, java_destination / "FeatureTheme.java")

        assets_destination = app / "assets" / "feature-store"
        if assets_destination.exists():
            raise ValueError(
                "Feature Store catalog/packages must not be bundled into APK assets"
            )

        for path in (manifest, strings, *layouts):
            ET.parse(path)
        menu_text = main_menu.read_text(encoding="utf-8")
        if "FeatureStoreActivity.class" not in menu_text or "FeatureTheme.apply(this);" not in base_activity.read_text(encoding="utf-8"):
            raise ValueError("Feature Store navigation or global theme application is incomplete")
    finally:
        REPO_ROOT, SOURCE_ROOT = previous_repo_root, previous_source_root


def main(argv: list[str]) -> int:
    if len(argv) not in (2, 3):
        print(f"Usage: {Path(argv[0]).name} PATH_TO_AMETHYST_SOURCE [REPO_ROOT]", file=sys.stderr)
        return 2
    try:
        repo_root = Path(argv[2]).resolve() if len(argv) == 3 else REPO_ROOT
        patch_upstream(Path(argv[1]).resolve(), repo_root)
    except (OSError, ValueError, json.JSONDecodeError, ET.ParseError) as exc:
        print(f"VoidCraft Feature Store patch FAILED: {exc}", file=sys.stderr)
        return 1
    print("VoidCraft Feature Store patch OK")
    print("  preserves engine, runtime, native libraries, rendering, version downloads, loaders, Modrinth and CurseForge")
    print("  adds a built-in store manager; online catalog metadata is fetched/cached and feature packages download only after Install")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
