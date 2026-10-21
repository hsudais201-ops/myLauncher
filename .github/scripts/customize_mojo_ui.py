#!/usr/bin/env python3
"""Apply the first native Mojo XML screen adaptation for VoidCraft branding."""
from __future__ import annotations
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

HERO = '''\t\t\t<LinearLayout
\t\t\t\tandroid:id="@+id/voidcraft_hero"
\t\t\t\tandroid:layout_width="0dp"
\t\t\t\tandroid:layout_height="wrap_content"
\t\t\t\tandroid:layout_marginStart="12dp"
\t\t\t\tandroid:layout_marginTop="12dp"
\t\t\t\tandroid:layout_marginEnd="12dp"
\t\t\t\tandroid:background="@drawable/voidcraft_hero_background"
\t\t\t\tandroid:orientation="vertical"
\t\t\t\tandroid:padding="18dp"
\t\t\t\tapp:layout_constraintEnd_toEndOf="parent"
\t\t\t\tapp:layout_constraintStart_toStartOf="parent"
\t\t\t\tapp:layout_constraintTop_toTopOf="parent">

\t\t\t\t<TextView
\t\t\t\t\tandroid:layout_width="match_parent"
\t\t\t\t\tandroid:layout_height="wrap_content"
\t\t\t\t\tandroid:fontFamily="sans-serif-black"
\t\t\t\t\tandroid:letterSpacing="0.035"
\t\t\t\t\tandroid:text="@string/voidcraft_hero_title"
\t\t\t\t\tandroid:textColor="@color/primary_text"
\t\t\t\t\tandroid:textSize="24sp" />

\t\t\t\t<TextView
\t\t\t\t\tandroid:layout_width="match_parent"
\t\t\t\t\tandroid:layout_height="wrap_content"
\t\t\t\t\tandroid:layout_marginTop="5dp"
\t\t\t\t\tandroid:text="@string/voidcraft_hero_subtitle"
\t\t\t\t\tandroid:textColor="@color/secondary_text"
\t\t\t\t\tandroid:textSize="13sp" />
\t\t\t</LinearLayout>
'''
DRAWABLE = '''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <corners android:radius="18dp" />
    <gradient android:angle="270" android:startColor="#2C2043" android:endColor="#171120" />
    <stroke android:width="1dp" android:color="#433452" />
</shape>
'''

def patch_button_top(layout: str, view_id: str) -> str:
    pattern = re.compile(r'<[A-Za-z0-9_.]+\b(?=[^>]*android:id="@\+id/' + re.escape(view_id) + r'")[^>]*/>', re.DOTALL)
    match = pattern.search(layout)
    if not match:
        raise ValueError(f"Could not locate launcher view {view_id}")
    node = match.group(0)
    anchor = 'app:layout_constraintTop_toTopOf="parent"'
    replacement = 'app:layout_constraintTop_toBottomOf="@id/voidcraft_hero"'
    if replacement not in node:
        if anchor not in node:
            raise ValueError(f"Unexpected top constraint on {view_id}")
        node = node.replace(anchor, replacement, 1)
        if 'android:layout_marginTop=' not in node:
            node = node[:-2] + ' android:layout_marginTop="14dp" />'
    return layout[:match.start()] + node + layout[match.end():]

def patch_divider(layout: str) -> str:
    pattern = re.compile(r'<View\b(?=[^>]*android:id="@\+id/social_divider")[^>]*/>', re.DOTALL)
    match = pattern.search(layout)
    if not match:
        raise ValueError("Could not locate social divider")
    node = match.group(0)
    node = node.replace('app:layout_constraintTop_toTopOf="parent"',
                        'app:layout_constraintTop_toTopOf="@id/news_button"', 1)
    return layout[:match.start()] + node + layout[match.end():]

def upsert_string(text: str, name: str, value: str) -> str:
    pattern = re.compile(r'(<string\s+name="' + re.escape(name) + r'"(?:\s+[^>]*)?>).*?(</string>)', re.DOTALL)
    updated, count = pattern.subn(lambda m: m.group(1) + value + m.group(2), text, count=1)
    if count:
        return updated
    if re.search(r'<string\s+name="' + re.escape(name) + r'"', text):
        raise ValueError(f"Unsupported string declaration for {name}")
    close = re.search(r'</resources\s*>', text)
    if not close:
        raise ValueError("Could not find closing resources element in strings.xml")
    return updated[:close.start()] + f'    <string name="{name}">{value}</string>\n' + updated[close.start():]

def patch_upstream(root: Path) -> None:
    app = root / "app_mojo"
    layout_path = app / "src/main/res/layout/fragment_launcher.xml"
    strings_path = app / "src/main/res/values/strings.xml"
    drawable_path = app / "src/main/res/drawable/voidcraft_hero_background.xml"
    for path in (layout_path, strings_path):
        if not path.is_file():
            raise FileNotFoundError(f"Expected Mojo launcher UI resource is missing: {path}")
    layout = layout_path.read_text(encoding="utf-8")
    news_pattern = re.compile(r'<[^>]*\bandroid:id="@\+id/news_button"[^>]*>', re.DOTALL)
    if '@+id/voidcraft_hero' not in layout:
        match = news_pattern.search(layout)
        if not match:
            raise ValueError("Mojo launcher layout changed; refusing to insert a detached hero panel")
        layout = layout[:match.start()] + HERO + '\\n' + layout[match.start():]
        layout = patch_button_top(layout, "news_button")
        layout = patch_button_top(layout, "social_media_button")
        layout = patch_divider(layout)
    elif 'app:layout_constraintTop_toBottomOf="@id/voidcraft_hero"' not in layout:
        raise ValueError("Existing VoidCraft hero is incomplete; refusing to guess at a partial patch")
    strings = strings_path.read_text(encoding="utf-8")
    strings = upsert_string(strings, "voidcraft_hero_title", "VOIDCRAFT")
    strings = upsert_string(strings, "voidcraft_hero_subtitle", "Minecraft Java Edition  •  Built for mobile")
    drawable_path.parent.mkdir(parents=True, exist_ok=True)
    if drawable_path.is_file() and drawable_path.read_text(encoding="utf-8") != DRAWABLE:
        raise ValueError("An unexpected voidcraft_hero_background.xml already exists")
    drawable_path.write_text(DRAWABLE, encoding="utf-8")
    ET.fromstring(layout)
    ET.fromstring(strings)
    ET.fromstring(DRAWABLE)
    layout_path.write_text(layout, encoding="utf-8")
    strings_path.write_text(strings, encoding="utf-8")
    if 'android:id="@+id/voidcraft_hero"' not in layout:
        raise ValueError("Hero panel verification failed")
    print("Applied VoidCraft hero panel to MojoLauncher home screen.")

if __name__ == "__main__":
    patch_upstream(Path(sys.argv[1] if len(sys.argv) > 1 else "upstream").resolve())
