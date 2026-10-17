#!/usr/bin/env python3
"""Apply myLauncher's original UI theme and Minecraft 26.3+ graphics workaround."""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def replace_idempotently(text: str, old: str, new: str, label: str) -> str:
    """Replace one expected source anchor, tolerating an already-applied patch."""
    if old in text:
        return text.replace(old, new, 1)
    if new in text:
        return text
    raise ValueError(f"Could not find expected source anchor in {label}: {old!r}")


def set_color(text: str, name: str, value: str) -> str:
    pattern = re.compile(
        r'(<color\s+name="' + re.escape(name) + r'"[^>]*>).*?(</color>)',
        re.DOTALL,
    )
    updated, count = pattern.subn(lambda m: m.group(1) + value + m.group(2), text, count=1)
    if count != 1:
        raise ValueError(f"Required color {name!r} is missing")
    return updated


def ensure_color(text: str, name: str, value: str) -> str:
    pattern = re.compile(r'<color\s+name="' + re.escape(name) + r'"[^>]*>.*?</color>', re.DOTALL)
    if pattern.search(text):
        return set_color(text, name, value)
    return text.replace(
        "</resources>",
        f'    <color name="{name}">{value}</color>\n</resources>',
        1,
    )


def write_resource(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content.strip() + "\n", encoding="utf-8")


def patch_upstream(upstream: Path) -> None:
    app = upstream / "app_pojavlauncher"
    res = app / "src/main/res"
    colors = res / "values/colors.xml"
    styles = res / "values/styles.xml"
    strings = res / "values/strings.xml"
    launcher_activity = app / "src/main/java/net/kdt/pojavlaunch/LauncherActivity.java"
    launcher_preferences = app / "src/main/java/net/kdt/pojavlaunch/prefs/LauncherPreferences.java"
    mine_button = app / "src/main/java/com/kdt/mcgui/MineButton.java"
    mine_edit_text = app / "src/main/java/com/kdt/mcgui/MineEditText.java"

    for path in (colors, styles, strings, launcher_activity, launcher_preferences, mine_button, mine_edit_text):
        if not path.is_file():
            raise FileNotFoundError(f"Required UI/compatibility source is missing: {path}")

    # One shared palette makes the home screen, account flows, settings, and dialogs
    # feel like the same product instead of unrelated upstream screens.
    color_text = colors.read_text(encoding="utf-8")
    palette = {
        "minebutton_color": "#8B72FF",
        "background_app": "#0C1020",
        "background_overlay": "#29334D",
        "background_status_bar": "#151B2D",
        "background_bottom_bar": "#0A0F1D",
        "primary_text": "#F5F6FF",
        "secondary_text": "#B8C2DC",
    }
    for name, value in palette.items():
        color_text = set_color(color_text, name, value)
    for name, value in {
        "mylauncher_accent": "#A78BFA",
        "mylauncher_accent_pressed": "#7C3AED",
        "mylauncher_surface": "#182238",
        "mylauncher_surface_pressed": "#292747",
        "mylauncher_border": "#394766",
        "mylauncher_field": "#10182A",
    }.items():
        color_text = ensure_color(color_text, name, value)
    colors.write_text(color_text, encoding="utf-8")

    styles_text = styles.read_text(encoding="utf-8")
    app_theme = re.search(r'(<style\s+name="AppTheme"[^>]*>)(.*?)(</style>)', styles_text, re.DOTALL)
    if not app_theme:
        raise ValueError("Could not find AppTheme in styles.xml")
    theme = app_theme.group(0)
    for item in (
        '<item name="colorAccent">@color/mylauncher_accent</item>',
        '<item name="android:colorAccent">@color/mylauncher_accent</item>',
        '<item name="colorControlActivated">@color/mylauncher_accent</item>',
        '<item name="android:windowBackground">@color/background_app</item>',
    ):
        if item not in theme:
            theme = theme.replace("</style>", f"        {item}\n    </style>")
    styles_text = styles_text[:app_theme.start()] + theme + styles_text[app_theme.end():]

    button_style = re.search(r'(<style\s+name="LauncherMenuButton"[^>]*>)(.*?)(</style>)', styles_text, re.DOTALL)
    if not button_style:
        raise ValueError("Could not find LauncherMenuButton style")
    button_block = button_style.group(0)
    button_block = button_block.replace(
        '<item name="android:background">?android:attr/selectableItemBackground</item>',
        '<item name="android:background">@drawable/mylauncher_button_background</item>',
    )
    if '<item name="android:background">@drawable/mylauncher_button_background</item>' not in button_block:
        raise ValueError("LauncherMenuButton could not be given the myLauncher background")
    if '<item name="android:textColor">@color/primary_text</item>' not in button_block:
        button_block = button_block.replace(
            "</style>",
            '        <item name="android:textColor">@color/primary_text</item>\n    </style>',
        )
    styles_text = styles_text[:button_style.start()] + button_block + styles_text[button_style.end():]
    styles.write_text(styles_text, encoding="utf-8")

    drawables = {
        "mylauncher_button_background.xml": """<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_pressed="true">
        <shape android:shape="rectangle">
            <solid android:color="@color/mylauncher_surface_pressed" />
            <stroke android:width="1dp" android:color="@color/mylauncher_accent" />
            <corners android:radius="14dp" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@color/mylauncher_surface" />
            <stroke android:width="1dp" android:color="@color/mylauncher_border" />
            <corners android:radius="14dp" />
        </shape>
    </item>
</selector>""",
        "mylauncher_play_button_background.xml": """<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_pressed="true">
        <shape android:shape="rectangle">
            <solid android:color="@color/mylauncher_accent_pressed" />
            <corners android:radius="18dp" />
        </shape>
    </item>
    <item>
        <shape android:shape="rectangle">
            <gradient android:angle="0" android:startColor="@color/mylauncher_accent" android:endColor="#7658EF" />
            <corners android:radius="18dp" />
        </shape>
    </item>
</selector>""",
        "mylauncher_input_background.xml": """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/mylauncher_field" />
    <stroke android:width="1dp" android:color="@color/mylauncher_border" />
    <corners android:radius="12dp" />
    <padding android:left="10dp" android:top="6dp" android:right="10dp" android:bottom="6dp" />
</shape>""",
        "mylauncher_hero_background.xml": """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient android:angle="0" android:startColor="#242044" android:endColor="#17263C" />
    <stroke android:width="1dp" android:color="#51477F" />
    <corners android:radius="20dp" />
</shape>""",
    }
    for filename, content in drawables.items():
        write_resource(res / "drawable" / filename, content)

    # Turn the existing title/subtitle into a visually distinct hero panel in both
    # orientations. Keep the existing launcher controls and their event handlers.
    hero_surface = """            <View
                android:id="@+id/mylauncher_hero_surface"
                android:layout_width="0dp"
                android:layout_height="112dp"
                android:layout_marginStart="12dp"
                android:layout_marginTop="8dp"
                android:layout_marginEnd="12dp"
                android:background="@drawable/mylauncher_hero_background"
                app:layout_constraintTop_toTopOf="parent"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintEnd_toEndOf="parent" />

"""
    for relative in ("layout/fragment_launcher.xml", "layout-land/fragment_launcher.xml"):
        layout = res / relative
        if not layout.is_file():
            raise FileNotFoundError(f"Expected launcher layout is missing: {layout}")
        text = layout.read_text(encoding="utf-8")
        if "mylauncher_hero_surface" not in text:
            title = re.search(r'^[ \t]*<TextView\s+android:id="@\+id/mylauncher_hero_title"', text, re.MULTILINE)
            if not title:
                raise ValueError(f"Could not find myLauncher hero title in {layout}")
            text = text[:title.start()] + hero_surface + text[title.start():]

        for button_id in ("news_button", "discord_button"):
            pattern = re.compile(
                r'<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/' +
                re.escape(button_id) + r'".*?/>',
                re.DOTALL,
            )
            match = pattern.search(text)
            if not match:
                raise ValueError(f"Could not find {button_id} in {layout}")
            block = match.group(0)
            old_constraint = 'app:layout_constraintTop_toBottomOf="@id/mylauncher_hero_subtitle"'
            new_constraint = 'app:layout_constraintTop_toBottomOf="@id/mylauncher_hero_surface"'
            if old_constraint in block:
                block = block.replace(old_constraint, new_constraint, 1)
            if new_constraint not in block:
                raise ValueError(f"{button_id} is not positioned below the hero panel in {layout}")
            text = text[:match.start()] + block + text[match.end():]
        if text.count('android:id="@+id/mylauncher_hero_surface"') != 1:
            raise ValueError(f"Hero panel is missing or duplicated in {layout}")
        text = "\n".join(line.rstrip() for line in text.splitlines()) + "\n"
        layout.write_text(text, encoding="utf-8")

    # Retheme the primary Play button and text fields so core controls match the
    # shared component styles on the rest of the application.
    button_text = mine_button.read_text(encoding="utf-8")
    for old_background in ("R.drawable.mine_button_background", "R.drawable.mylauncher_primary_button_background"):
        if old_background in button_text:
            button_text = button_text.replace(old_background, "R.drawable.mylauncher_play_button_background", 1)
            break
    if "R.drawable.mylauncher_play_button_background" not in button_text:
        raise ValueError(f"Could not find the expected MineButton background in {mine_button}")
    mine_button.write_text(button_text, encoding="utf-8")

    input_text = mine_edit_text.read_text(encoding="utf-8")
    old_input_background = 'setBackgroundColor(Color.parseColor("#131313"));'
    if old_input_background in input_text:
        input_text = input_text.replace(old_input_background, "setBackgroundResource(R.drawable.mylauncher_input_background);", 1)
    elif "R.drawable.mylauncher_input_background" not in input_text:
        raise ValueError(f"Could not find the expected MineEditText background in {mine_edit_text}")
    mine_edit_text.write_text(input_text, encoding="utf-8")

    # The MobileGlues project documents a 26.3-snapshot-3+ compatibility setting:
    # error filtering must ignore shader/program errors. Apply it at launch only
    # for affected versions and this renderer, leaving saved user preferences alone.
    prefs_text = launcher_preferences.read_text(encoding="utf-8")
    if "public static boolean MYLAUNCHER_RENDERPEARL_COMPAT = false;" not in prefs_text:
        prefs_text = replace_idempotently(
            prefs_text,
            "    public static SharedPreferences DEFAULT_PREF;",
            "    public static boolean MYLAUNCHER_RENDERPEARL_COMPAT = false;\n\n    public static SharedPreferences DEFAULT_PREF;",
            str(launcher_preferences),
        )
    elif "public static SharedPreferences DEFAULT_PREF;" not in prefs_text:
        raise ValueError(f"SharedPreferences anchor is missing in {launcher_preferences}")
    old_setting = '        MGConfigJson.put("enableNoError", Integer.parseInt(DEFAULT_PREF.getString("mg_renderer_setting_errorSetting", "0")));'
    new_setting = """        String errorSetting = DEFAULT_PREF.getString("mg_renderer_setting_errorSetting", "0");
        if (MYLAUNCHER_RENDERPEARL_COMPAT) {
            errorSetting = "1";
        }
        MGConfigJson.put("enableNoError", Integer.parseInt(errorSetting));"""
    prefs_text = replace_idempotently(prefs_text, old_setting, new_setting, str(launcher_preferences))
    launcher_preferences.write_text(prefs_text, encoding="utf-8")

    activity_text = launcher_activity.read_text(encoding="utf-8")
    launch_anchor = "        new MinecraftDownloader().start("
    compat_block = r"""        // Minecraft 26.3+ RenderPearl compatibility: prepare MobileGlues error filtering
        // before the downloader launches the Java runtime. The setting is launch-scoped;
        // the user's saved preference is restored for other versions.
        boolean useRenderPearlCompatibility = normalizedVersionId.matches("^26\\.(?:3|[4-9])(?:[.-].*|$)");
        LauncherPreferences.MYLAUNCHER_RENDERPEARL_COMPAT = useRenderPearlCompatibility;
        try {
            LauncherPreferences.writeMGRendererSettings();
        } catch (IOException e) {
            Log.w("myLauncher", "Unable to write graphics compatibility settings", e);
        } finally {
            LauncherPreferences.MYLAUNCHER_RENDERPEARL_COMPAT = false;
        }
        if (useRenderPearlCompatibility) {
            Toast.makeText(this, R.string.mylauncher_26x_renderer_compat, Toast.LENGTH_LONG).show();
        }

"""
    if "boolean useRenderPearlCompatibility = normalizedVersionId.matches(" not in activity_text:
        activity_text = replace_idempotently(
            activity_text,
            launch_anchor,
            compat_block + launch_anchor,
            str(launcher_activity),
        )
    elif "LauncherPreferences.writeMGRendererSettings();" not in activity_text or launch_anchor not in activity_text:
        raise ValueError(f"Incomplete RenderPearl compatibility patch in {launcher_activity}")
    launcher_activity.write_text(activity_text, encoding="utf-8")

    strings_text = strings.read_text(encoding="utf-8")
    if 'name="mylauncher_26x_renderer_compat"' not in strings_text:
        anchor = re.search(r'<string\s+name="main_add_account"[^>]*>.*?</string>', strings_text, re.DOTALL)
        if not anchor:
            raise ValueError("Could not find main_add_account to add the compatibility notice")
        addition = '\n    <string name="mylauncher_26x_renderer_compat">Graphics compatibility settings prepared for Minecraft 26.3+.</string>'
        strings_text = strings_text[:anchor.end()] + addition + strings_text[anchor.end():]
    strings.write_text(strings_text, encoding="utf-8")

    icon = res / "drawable" / "mylauncher_icon.xml"
    if icon.is_file():
        icon_text = icon.read_text(encoding="utf-8")
        icon_text = icon_text.replace('android:fillColor="#171B26"', 'android:fillColor="#0C1020"')
        icon_text = icon_text.replace('android:fillColor="#7C5CFF"', 'android:fillColor="#A78BFA"')
        icon.write_text(icon_text, encoding="utf-8")

    xml_files = [
        colors, styles, strings,
        res / "layout/fragment_launcher.xml",
        res / "layout-land/fragment_launcher.xml",
        res / "drawable/mylauncher_button_background.xml",
        res / "drawable/mylauncher_play_button_background.xml",
        res / "drawable/mylauncher_input_background.xml",
        res / "drawable/mylauncher_hero_background.xml",
    ]
    if icon.is_file():
        xml_files.append(icon)
    for path in xml_files:
        try:
            ET.parse(path)
        except ET.ParseError as exc:
            raise ValueError(f"Generated Android XML is invalid at {path}: {exc}") from exc

    print("myLauncher UI/compatibility patch OK")
    print("  original midnight-indigo theme applied to shared launcher components")
    print("  portrait/landscape home screens use the myLauncher hero panel")
    print("  Minecraft 26.3+ MobileGlues receives launch-only error-filter compatibility")


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(f"Usage: {Path(argv[0]).name} PATH_TO_AMETHYST_SOURCE", file=sys.stderr)
        return 2
    try:
        patch_upstream(Path(argv[1]).resolve())
    except (OSError, ValueError) as exc:
        print(f"myLauncher UI patch FAILED: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
