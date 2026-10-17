#!/usr/bin/env python3
"""Apply and validate the VoidCraft Launcher Android application icon."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"

VECTOR = """<?xml version="1.0" encoding="utf-8"?>
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
"""

def main(root: Path) -> None:
    app = root / "app_pojavlauncher"
    manifest = app / "src/main/AndroidManifest.xml"
    if not manifest.is_file():
        raise FileNotFoundError(f"Android manifest not found: {manifest}")
    tree = ET.parse(manifest)
    manifest_root = tree.getroot()
    app_node = manifest_root.find("application")
    if app_node is None:
        raise ValueError("Android manifest has no application element")
    app_node.set(ANDROID + "icon", "@drawable/voidcraft_launcher_logo")
    app_node.set(ANDROID + "roundIcon", "@drawable/voidcraft_launcher_logo")
    tree.write(manifest, encoding="utf-8", xml_declaration=True)

    drawable = app / "src/main/res/drawable"
    drawable.mkdir(parents=True, exist_ok=True)
    icon = drawable / "voidcraft_launcher_logo.xml"
    icon.write_text(VECTOR, encoding="utf-8")
    ET.parse(icon)
    ET.parse(manifest)
    print("Applied and validated VoidCraft Launcher vector icon.")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: apply_logo.py <upstream-checkout>")
    main(Path(sys.argv[1]))
