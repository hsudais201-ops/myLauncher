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



def set_style_item(text: str, style_name: str, item_name: str, value: str) -> str:
    """Set a theme item without creating duplicates on repeat patcher runs."""
    style = re.search(
        r'(<style\s+name="' + re.escape(style_name) + r'"[^>]*>)(.*?)(</style>)',
        text,
        re.DOTALL,
    )
    if not style:
        raise ValueError(f"Could not find {style_name!r} in styles.xml")
    body = style.group(2)
    item = re.compile(
        r'(<item\s+name="' + re.escape(item_name) + r'"\s*>)(.*?)(</item>)',
        re.DOTALL,
    )
    body, count = item.subn(lambda m: m.group(1) + value + m.group(3), body, count=1)
    if not count:
        if re.search(r'<item\s+name="' + re.escape(item_name) + r'"', body):
            raise ValueError(f"Unsupported style item {item_name!r} in {style_name!r}")
        body = body.rstrip() + f'\n        <item name="{item_name}">{value}</item>\n    '
    return text[:style.start()] + style.group(1) + body + style.group(3) + text[style.end():]


def upsert_string(text: str, name: str, value: str) -> str:
    """Set or append a string resource idempotently."""
    pattern = re.compile(
        r'(<string\s+name="' + re.escape(name) + r'"[^>]*>)(.*?)(</string>)',
        re.DOTALL,
    )
    text, count = pattern.subn(lambda m: m.group(1) + value + m.group(3), text, count=1)
    if count:
        return text
    if re.search(r'<string\s+name="' + re.escape(name) + r'"', text):
        raise ValueError(f"Unsupported string declaration for {name!r}")
    closing = re.search(r'</resources\s*>', text)
    if not closing:
        raise ValueError("Could not find </resources> in strings.xml")
    return text[:closing.start()] + f'    <string name="{name}">{value}</string>\n' + text[closing.start():]



def set_view_attribute(layout_text: str, tag: str, view_id: str, attribute: str, value: str, label: str) -> str:
    """Set a named attribute on one XML view in an idempotent way."""
    pattern = re.compile(
        r'<' + re.escape(tag) + r'\b(?=[^>]*android:id="@\+id/' + re.escape(view_id) + r'")[^>]*?/>',
        re.DOTALL,
    )
    match = pattern.search(layout_text)
    if not match:
        raise ValueError(f"Could not find {view_id!r} view in {label}")
    block = match.group(0)
    attr = re.compile(r'(?<![\w:.-])' + re.escape(attribute) + r'="[^"]*"')
    if attr.search(block):
        block = attr.sub(f'{attribute}="{value}"', block, count=1)
    else:
        block = block[:-2].rstrip() + f' {attribute}="{value}" />'
    return layout_text[:match.start()] + block + layout_text[match.end():]


def write_resource(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content.strip() + "\n", encoding="utf-8")


def patch_upstream(upstream: Path) -> None:
    app = upstream / "app_pojavlauncher"
    res = app / "src/main/res"
    colors = res / "values/colors.xml"
    styles = res / "values/styles.xml"
    strings = res / "values/strings.xml"
    main_activity = app / "src/main/java/net/kdt/pojavlaunch/MainActivity.java"
    manifest = app / "src/main/AndroidManifest.xml"
    card = res / "drawable/background_card.xml"
    launcher_preferences = app / "src/main/java/net/kdt/pojavlaunch/prefs/LauncherPreferences.java"
    mine_button = app / "src/main/java/com/kdt/mcgui/MineButton.java"
    mine_edit_text = app / "src/main/java/com/kdt/mcgui/MineEditText.java"

    for path in (colors, styles, strings, main_activity, launcher_preferences, mine_button, mine_edit_text, manifest, card):
        if not path.is_file():
            raise FileNotFoundError(f"Required UI/compatibility source is missing: {path}")

    # One shared palette makes the home screen, account flows, settings, and dialogs
    # feel like the same product instead of unrelated upstream screens.
    color_text = colors.read_text(encoding="utf-8")
    palette = {
        "minebutton_color": "#8D6BFF",
        "background_app": "#0D0A14",
        "background_overlay": "#362747",
        "background_status_bar": "#100C19",
        "background_bottom_bar": "#15101F",
        "primary_text": "#F7F2FF",
        "secondary_text": "#B7AACD",
    }
    for name, value in palette.items():
        color_text = set_color(color_text, name, value)
    for name, value in {
        "mylauncher_accent": "#BBA2FF",
        "mylauncher_accent_pressed": "#7650D8",
        "mylauncher_surface": "#15111F",
        "mylauncher_surface_pressed": "#2D2240",
        "mylauncher_border": "#433452",
        "mylauncher_field": "#100C19",
        "mylauncher_primary_text": "#FFFFFF",
        "mylauncher_ripple": "#66BBA2FF",
        "mylauncher_hero_top": "#2C2043",
        "mylauncher_hero_bottom": "#171120",
    }.items():
        color_text = ensure_color(color_text, name, value)
    colors.write_text(color_text, encoding="utf-8")

    styles_text = styles.read_text(encoding="utf-8")
    for item, value in {
        "colorAccent": "@color/mylauncher_accent",
        "android:colorAccent": "@color/mylauncher_accent",
        "colorControlActivated": "@color/mylauncher_accent",
        "android:colorControlActivated": "@color/mylauncher_accent",
        "colorControlHighlight": "@color/mylauncher_ripple",
        "android:colorControlHighlight": "@color/mylauncher_ripple",
        "colorButtonNormal": "@color/mylauncher_surface",
        "android:windowBackground": "@color/background_app",
        "android:textColorPrimary": "@color/primary_text",
        "android:textColorSecondary": "@color/secondary_text",
        "android:statusBarColor": "@color/background_status_bar",
        "android:navigationBarColor": "@color/background_app",
        "android:windowLightStatusBar": "false",
        "android:windowLightNavigationBar": "false",
    }.items():
        styles_text = set_style_item(styles_text, "AppTheme", item, value)
    for item, value in {
        "android:background": "@drawable/mylauncher_button_background",
        "android:textColor": "@color/primary_text",
        "android:textAllCaps": "false",
        "android:fontFamily": "sans-serif-medium",
        "android:minHeight": "48dp",
        "android:letterSpacing": "0.01",
    }.items():
        styles_text = set_style_item(styles_text, "LauncherMenuButton", item, value)
    styles_text = set_style_item(styles_text, "LauncherMenuButton.Universal", "android:layout_height", "58dp")
    if not re.search(r'<style\s+name="MyLauncherDialogTheme"', styles_text):
        end = re.search(r'</resources\s*>', styles_text)
        if not end:
            raise ValueError(f"Could not find </resources> in {styles}")
        styles_text = styles_text[:end.start()] + """
    <style name="MyLauncherDialogTheme" parent="Theme.AppCompat.DayNight.Dialog">
        <item name="colorAccent">@color/mylauncher_accent</item>
        <item name="android:colorAccent">@color/mylauncher_accent</item>
        <item name="android:windowBackground">@color/mylauncher_surface</item>
        <item name="android:textColorPrimary">@color/primary_text</item>
        <item name="android:textColorSecondary">@color/secondary_text</item>
        <item name="android:windowMinWidthMajor">87%</item>
        <item name="android:windowMinWidthMinor">87%</item>
    </style>
""" + styles_text[end.start():]
    styles.write_text(styles_text, encoding="utf-8")

    card = res / "drawable/background_card.xml"
    card_text = card.read_text(encoding="utf-8")
    card_text = re.sub(r'<corners\s+android:radius="[^"]+"\s*/>', '<corners android:radius="18dp" />', card_text, count=1)
    card_text = re.sub(r'<solid\s+android:color="[^"]+"\s*/>', '<solid android:color="@color/mylauncher_surface" />', card_text, count=1)
    if "<stroke " not in card_text:
        solid = re.search(r'^[ \t]*<solid[^>]*/>', card_text, re.MULTILINE)
        if not solid:
            raise ValueError(f"Could not find the card fill in {card}")
        card_text = card_text[:solid.end()] + '\n    <stroke android:width="1dp" android:color="@color/mylauncher_border" />' + card_text[solid.end():]
    card.write_text(card_text, encoding="utf-8")

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
    <gradient android:angle="270" android:startColor="@color/mylauncher_hero_top" android:endColor="@color/mylauncher_hero_bottom" />
    <stroke android:width="1dp" android:color="@color/mylauncher_border" />
    <corners android:radius="22dp" />
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

        if "mylauncher_hero_kicker" not in text:
            title = re.search(r'^[ \t]*<TextView\s+android:id="@\+id/mylauncher_hero_title"', text, re.MULTILINE)
            if not title:
                raise ValueError(f"Could not find myLauncher hero title in {layout}")
            kicker = """            <TextView
                android:id="@+id/mylauncher_hero_kicker"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="18dp"
                android:layout_marginTop="15dp"
                android:layout_marginEnd="18dp"
                android:fontFamily="sans-serif-medium"
                android:letterSpacing="0.12"
                android:text="MYLAUNCHER  /  JAVA EDITION"
                android:textColor="@color/mylauncher_accent"
                android:textSize="10sp"
                app:layout_constraintTop_toTopOf="@id/mylauncher_hero_surface"
                app:layout_constraintStart_toStartOf="@id/mylauncher_hero_surface"
                app:layout_constraintEnd_toEndOf="@id/mylauncher_hero_surface" />

"""
            text = text[:title.start()] + kicker + text[title.start():]

        for attribute, value in (
            ("android:layout_width", "0dp"),
            ("android:layout_height", "wrap_content"),
            ("android:layout_marginStart", "18dp"),
            ("android:layout_marginTop", "33dp"),
            ("android:layout_marginEnd", "18dp"),
            ("android:text", "myLauncher"),
            ("android:textColor", "@color/primary_text"),
            ("android:textSize", "27sp"),
            ("android:textStyle", "bold"),
            ("android:fontFamily", "sans-serif"),
            ("app:layout_constraintTop_toTopOf", "@id/mylauncher_hero_surface"),
            ("app:layout_constraintStart_toStartOf", "@id/mylauncher_hero_surface"),
            ("app:layout_constraintEnd_toEndOf", "@id/mylauncher_hero_surface"),
        ):
            text = set_view_attribute(text, "TextView", "mylauncher_hero_title", attribute, value, str(layout))
        for attribute, value in (
            ("android:layout_width", "0dp"),
            ("android:layout_height", "wrap_content"),
            ("android:layout_marginStart", "19dp"),
            ("android:layout_marginTop", "1dp"),
            ("android:layout_marginEnd", "18dp"),
            ("android:text", "Your Java worlds, reimagined for mobile."),
            ("android:textColor", "@color/secondary_text"),
            ("android:textSize", "12sp"),
            ("app:layout_constraintTop_toBottomOf", "@id/mylauncher_hero_title"),
            ("app:layout_constraintStart_toStartOf", "@id/mylauncher_hero_surface"),
            ("app:layout_constraintEnd_toEndOf", "@id/mylauncher_hero_surface"),
        ):
            text = set_view_attribute(text, "TextView", "mylauncher_hero_subtitle", attribute, value, str(layout))
        for attribute, value in (
            ("android:layout_width", "0dp"),
            ("android:layout_height", "wrap_content"),
            ("android:layout_marginStart", "18dp"),
            ("android:layout_marginTop", "15dp"),
            ("android:layout_marginEnd", "18dp"),
            ("android:fontFamily", "sans-serif-medium"),
            ("android:letterSpacing", "0.12"),
            ("android:text", "MYLAUNCHER  /  JAVA EDITION"),
            ("android:textColor", "@color/mylauncher_accent"),
            ("android:textSize", "10sp"),
            ("app:layout_constraintTop_toTopOf", "@id/mylauncher_hero_surface"),
            ("app:layout_constraintStart_toStartOf", "@id/mylauncher_hero_surface"),
            ("app:layout_constraintEnd_toEndOf", "@id/mylauncher_hero_surface"),
        ):
            text = set_view_attribute(text, "TextView", "mylauncher_hero_kicker", attribute, value, str(layout))

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
    text_color_line = "setTextColor(ResourcesCompat.getColor(getResources(), R.color.mylauncher_primary_text, null));"
    if text_color_line not in button_text:
        play_bg = re.search(
            r'setBackground\(ResourcesCompat\.getDrawable\(getResources\(\), R\.drawable\.mylauncher_play_button_background, null\)\);',
            button_text,
        )
        if not play_bg:
            raise ValueError(f"Could not locate the primary play-button background setter in {mine_button}")
        button_text = button_text[:play_bg.end()] + "\n\t\t" + text_color_line + button_text[play_bg.end():]
    if "import net.kdt.pojavlaunch.R;" not in button_text:
        package_line = re.search(r'(?m)^package [^;]+;\s*$', button_text)
        if package_line:
            button_text = button_text[:package_line.end()] + "\n\nimport net.kdt.pojavlaunch.R;" + button_text[package_line.end():]
    mine_button.write_text(button_text, encoding="utf-8")

    input_text = mine_edit_text.read_text(encoding="utf-8")
    old_input_background = 'setBackgroundColor(Color.parseColor("#131313"));'
    input_calls = """setBackgroundResource(R.drawable.mylauncher_input_background);
        setTextColor(ResourcesCompat.getColor(getResources(), R.color.primary_text, null));
        setHintTextColor(ResourcesCompat.getColor(getResources(), R.color.secondary_text, null));"""
    if old_input_background in input_text:
        input_text = input_text.replace(old_input_background, input_calls, 1)
    elif "setBackgroundResource(R.drawable.mylauncher_input_background);" in input_text:
        if "setTextColor(ResourcesCompat.getResources" in input_text:
            raise ValueError(f"Unexpected input color setter in {mine_edit_text}")
        if "R.color.primary_text" not in input_text:
            input_text = input_text.replace(
                "setBackgroundResource(R.drawable.mylauncher_input_background);",
                "setBackgroundResource(R.drawable.mylauncher_input_background);\n        setTextColor(ResourcesCompat.getColor(getResources(), R.color.primary_text, null));",
                1,
            )
        if "setHintTextColor(ResourcesCompat.getResources" in input_text:
            raise ValueError(f"Unexpected hint color setter in {mine_edit_text}")
        if "setHintTextColor(" not in input_text:
            input_text = input_text.replace(
                "setBackgroundResource(R.drawable.mylauncher_input_background);",
                "setBackgroundResource(R.drawable.mylauncher_input_background);\n        setHintTextColor(ResourcesCompat.getColor(getResources(), R.color.secondary_text, null));",
                1,
            )
    else:
        raise ValueError(f"Could not find the expected MineEditText background in {mine_edit_text}")
    if "import androidx.core.content.res.ResourcesCompat;" not in input_text:
        input_text = input_text.replace(
            "import android.widget.EditText;",
            "import android.widget.EditText;\nimport androidx.core.content.res.ResourcesCompat;",
        )
    if "import net.kdt.pojavlaunch.R;" not in input_text:
        if "import android.widget.EditText;" in input_text:
            input_text = input_text.replace(
                "import android.widget.EditText;",
                "import android.widget.EditText;\nimport net.kdt.pojavlaunch.R;",
                1,
            )
        elif "package com.kdt.mcgui;" in input_text:
            input_text = input_text.replace(
                "package com.kdt.mcgui;",
                "package com.kdt.mcgui;\n\nimport net.kdt.pojavlaunch.R;",
                1,
            )
    mine_edit_text.write_text(input_text, encoding="utf-8")

    # Patch the final renderer configuration write, which happens in MainActivity
    # after renderer selection and immediately before Java starts.
    main_activity_text = main_activity.read_text(encoding="utf-8")
    main_activity_text = replace_idempotently(
        main_activity_text,
        "LauncherPreferences.writeMGRendererSettings();",
        "LauncherPreferences.writeMGRendererSettings(versionId);",
        str(main_activity),
    )
    main_activity.write_text(main_activity_text, encoding="utf-8")

    prefs_text = launcher_preferences.read_text(encoding="utf-8")
    old_signature = "    public static void writeMGRendererSettings() throws IOException {"
    new_signature = r"""    private static boolean isRenderPearlWorkaroundTarget(String versionId) {
        if (versionId == null) return false;
        String id = versionId.trim();
        if (!id.matches("(?i)^26\\.(?:3|[4-9]|[1-9][0-9]+)(?:[.-].*|$)")) return false;
        return !id.matches("(?i)^26\\.3-snapshot-1(?:$|[-+].*)");
    }

    public static void writeMGRendererSettings(String versionId) throws IOException {"""
    prefs_text = replace_idempotently(
        prefs_text, old_signature, new_signature, str(launcher_preferences)
    )
    old_setting = '        MGConfigJson.put("enableNoError", Integer.parseInt(DEFAULT_PREF.getString("mg_renderer_setting_errorSetting", "0")));'
    new_setting = """        String errorSetting = DEFAULT_PREF.getString("mg_renderer_setting_errorSetting", "0");
        if (isRenderPearlWorkaroundTarget(versionId)
                && "opengles_mobileglues".equals(Tools.LOCAL_RENDERER)
                && "0".equals(errorSetting)) {
            // Value 2 means ignore shader/program errors (partial filtering).
            errorSetting = "2";
            Log.i("myLauncher", "Applying MobileGlues compatibility filter for Minecraft " + versionId);
        }
        MGConfigJson.put("enableNoError", Integer.parseInt(errorSetting));"""
    prefs_text = replace_idempotently(prefs_text, old_setting, new_setting, str(launcher_preferences))
    if "isRenderPearlWorkaroundTarget(versionId)" not in prefs_text or 'errorSetting = "2";' not in prefs_text:
        raise ValueError("Version-gated MobileGlues shader/program error-filter workaround was not installed")
    launcher_preferences.write_text(prefs_text, encoding="utf-8")

    strings_text = strings.read_text(encoding="utf-8")
    for resource_name, label_text in {
        "mcl_setting_renderer_mobileglues": "MobileGlues (OpenGL ES) - 26.3 compatibility varies by device",
        "mcl_setting_renderer_vulkan_kopper_zink": "Kopper Zink (Vulkan) - requires a compatible Vulkan driver",
        "mcl_setting_renderer_vulkan_zink": "Zink (Vulkan) - compatibility and speed vary by device",
    }.items():
        strings_text = upsert_string(strings_text, resource_name, label_text)
    strings.write_text(strings_text, encoding="utf-8")

    icon = res / "drawable" / "mylauncher_icon.xml"
    if icon.is_file():
        icon_text = icon.read_text(encoding="utf-8")
        icon_text = icon_text.replace('android:fillColor="#171B26"', 'android:fillColor="#0D0A14"')
        icon_text = icon_text.replace('android:fillColor="#7C5CFF"', 'android:fillColor="#BBA2FF"')
        icon.write_text(icon_text, encoding="utf-8")

    manifest_text = manifest.read_text(encoding="utf-8")
    manifest_text = replace_idempotently(
        manifest_text,
        'android:theme="@style/Theme.AppCompat.DayNight.Dialog"',
        'android:theme="@style/MyLauncherDialogTheme"',
        str(manifest),
    )
    manifest.write_text(manifest_text, encoding="utf-8")


    xml_files = [
        colors, styles, strings, manifest, card,
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
    print("  original night-violet theme applied to shared launcher components and dialogs")
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
