#!/usr/bin/env python3
"""Apply the original myLauncher icon to the Android app before building."""
from pathlib import Path
import re
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
    <path android:fillColor="#171D3F" android:strokeColor="#6EF3FF" android:strokeWidth="1.5" android:strokeLineJoin="round" android:pathData="M54,13 L79,27.5 L79,56.5 L54,71 L29,56.5 L29,27.5 Z" />
    <path android:fillColor="#6EF3FF" android:pathData="M36,53 V31 L46,43 L54,35 L62,43 L72,31 V53 H64 V44 L54,56 L44,44 V53 Z" />
    <path android:fillColor="#A8FFCB" android:pathData="M77,20a2.5,2.5 0,1 0,5 0a2.5,2.5 0,1 0,-5 0" />
</vector>
"""

def main(root: Path) -> None:
    app = root / "app_pojavlauncher"
    manifest = app / "src/main/AndroidManifest.xml"
    if not manifest.is_file():
        raise FileNotFoundError(f"Android manifest not found: {manifest}")
    tree = ET.parse(manifest)
    node = tree.getroot()
    app_node = node.find("application")
    if app_node is None:
        raise ValueError("Android manifest has no application element")
    app_node.set(ANDROID + "icon", "@drawable/mylauncher_logo")
    app_node.set(ANDROID + "roundIcon", "@drawable/mylauncher_logo")
    tree.write(manifest, encoding="utf-8", xml_declaration=True)

    drawable = app / "src/main/res/drawable"
    drawable.mkdir(parents=True, exist_ok=True)
    icon = drawable / "mylauncher_logo.xml"
    icon.write_text(VECTOR, encoding="utf-8")
    ET.parse(icon)
    ET.parse(manifest)
    print("Applied and validated myLauncher custom launcher icon.")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: apply_logo.py <upstream-checkout>")
    main(Path(sys.argv[1]))
