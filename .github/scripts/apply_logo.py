#!/usr/bin/env python3
"""Apply and validate the VoidCraft Launcher Android application icon."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
TOOLS = "{http://schemas.android.com/tools}"
ET.register_namespace("android", ANDROID[1:-1])
ET.register_namespace("tools", TOOLS[1:-1])

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

    # Copy the designed VC emblem from this repository into the generated app.
    asset = Path(__file__).resolve().parents[2] / "assets/branding/voidcraft-vc-logo.webp"
    if not asset.is_file():
        raise FileNotFoundError(f"VoidCraft VC logo asset not found: {asset}")
    image_bytes = asset.read_bytes()
    if len(image_bytes) < 12 or image_bytes[:4] != b"RIFF" or image_bytes[8:12] != b"WEBP":
        raise ValueError(f"Logo asset is not a valid WebP file: {asset}")

    drawable = app / "src/main/res/drawable-nodpi"
    drawable.mkdir(parents=True, exist_ok=True)
    icon = drawable / "voidcraft_launcher_logo.webp"
    icon.write_bytes(image_bytes)

    # Do not leave the old generated vector placeholder in the output.
    legacy_icon = app / "src/main/res/drawable/voidcraft_launcher_logo.xml"
    if legacy_icon.exists():
        legacy_icon.unlink()

    ET.parse(manifest)
    if not icon.is_file() or icon.stat().st_size != len(image_bytes):
        raise ValueError("Logo asset copy validation failed")
    print("Applied and validated the VoidCraft VC monogram launcher icon.")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: apply_logo.py <upstream-checkout>")
    main(Path(sys.argv[1]))
