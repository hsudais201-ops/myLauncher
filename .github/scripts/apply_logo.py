#!/usr/bin/env python3
"""Apply and validate the VoidCraft Launcher dimensional-cube Android icon."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
TOOLS = "{http://schemas.android.com/tools}"
ET.register_namespace("android", ANDROID[1:-1])
ET.register_namespace("tools", TOOLS[1:-1])

# Original VoidCraft identity: a luminous cyan/violet cube portal on a deep
# midnight background. Android VectorDrawable keeps the icon crisp at any size.
VECTOR = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#080B1B" android:pathData="M0,0h108v108h-108z" />
    <path android:fillColor="#11162F" android:pathData="M54,8 L94,31 L94,77 L54,100 L14,77 L14,31 Z" />
    <path android:fillColor="#536CFF" android:strokeColor="#A9F8FF" android:strokeWidth="1.5" android:pathData="M54,19 L82,35 L54,51 L26,35 Z" />
    <path android:fillColor="#293A91" android:strokeColor="#75DFFF" android:strokeWidth="1" android:pathData="M26,35 L54,51 L54,83 L26,67 Z" />
    <path android:fillColor="#6537A8" android:strokeColor="#D0A0FF" android:strokeWidth="1" android:pathData="M82,35 L54,51 L54,83 L82,67 Z" />
    <path android:fillColor="#0B1027" android:strokeColor="#A7F8FF" android:strokeWidth="1.5" android:pathData="M54,29 L70,38 L54,47 L38,38 Z" />
    <path android:fillColor="#5CE8FF" android:pathData="M38,38 L54,47 L54,67 L38,58 Z" />
    <path android:fillColor="#B77AFF" android:pathData="M70,38 L54,47 L54,67 L70,58 Z" />
    <path android:fillColor="#E4FFFF" android:pathData="M50,45 L54,47 L54,61 L50,59 Z" />
    <path android:fillColor="#FFFFFF" android:pathData="M85,16 L87,21 L92,23 L87,25 L85,30 L83,25 L78,23 L83,21 Z" />
    <path android:fillColor="#6EF3FF" android:pathData="M20,72 L22,76 L26,78 L22,80 L20,84 L18,80 L14,78 L18,76 Z" />
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
    print("Applied and validated VoidCraft Launcher dimensional-cube vector icon.")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: apply_logo.py <upstream-checkout>")
    main(Path(sys.argv[1]))
