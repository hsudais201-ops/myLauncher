#!/usr/bin/env python3
"""Apply deterministic myLauncher branding/package patches to Amethyst-Android."""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def replace_idempotently(text: str, old: str, new: str, label: str) -> str:
    """Replace a token, allow repeat runs, and fail on unexpected upstream changes."""
    if old in text:
        return text.replace(old, new)
    if new in text:
        return text
    raise ValueError(f"Could not find expected token in {label}: {old!r} or {new!r}")


def validate_xml_files(paths: tuple[Path, ...]) -> None:
    """Fail early when a generated Android resource is not well-formed XML."""
    for path in paths:
        try:
            ET.parse(path)
        except ET.ParseError as exc:
            raise ValueError(f"Generated Android XML is invalid at {path}: {exc}") from exc


def patch_upstream(upstream: Path) -> None:
    gradle = upstream / "app_pojavlauncher" / "build.gradle"
    strings = upstream / "app_pojavlauncher" / "src" / "main" / "res" / "values" / "strings.xml"
    colors = upstream / "app_pojavlauncher" / "src" / "main" / "res" / "values" / "colors.xml"
    settings = upstream / "settings.gradle"
    for path in (gradle, strings, colors, settings):
        if not path.is_file():
            raise FileNotFoundError(f"Expected upstream file is missing: {path}")

    build_text = gradle.read_text(encoding="utf-8")
    build_text = replace_idempotently(
        build_text, "org.angelauramc.amethyst", "com.mf.mylauncher", str(gradle)
    )
    build_text = replace_idempotently(build_text, "Amethyst", "myLauncher", str(gradle))
    min_sdk = re.search(r"\bminSdkVersion\s+(\d+)\b", build_text)
    if not min_sdk:
        raise ValueError("Could not verify minSdkVersion; refusing an unverified Android 9 build")
    if int(min_sdk.group(1)) > 28:
        raise ValueError(f"Upstream minSdkVersion {min_sdk.group(1)} is above Android 9/API 28")
    if 'applicationId "com.mf.mylauncher"' not in build_text:
        raise ValueError("The patched Gradle file has no expected myLauncher applicationId")
    if "org.angelauramc.amethyst" in build_text:
        raise ValueError("An upstream application ID/authority remains in build.gradle")
    gradle.write_text(build_text, encoding="utf-8")

    strings_text = strings.read_text(encoding="utf-8")
    strings_text = strings_text.replace("org.angelauramc.amethyst", "com.mf.mylauncher")
    strings_text = replace_idempotently(strings_text, "Amethyst", "myLauncher", str(strings))
    if not re.search(
        r'<string\s+name="app_name"[^>]*>\s*myLauncher\s*</string>',
        strings_text,
        flags=re.DOTALL,
    ):
        raise ValueError("The patched strings.xml is missing app_name=myLauncher")
    # Upstream defines app_short_name through Gradle resValue, not strings.xml.
    required_gradle_values = (
        '"app_name", "myLauncher (Debug)"',
        '"app_name", "myLauncher"',
        '"app_short_name", "myLauncher (Debug)"',
        '"app_short_name", "myLauncher"',
        "'application_package', 'com.mf.mylauncher.debug'",
        "'application_package', 'com.mf.mylauncher'",
        "'storageProviderAuthorities', 'com.mf.mylauncher.scoped.gamefolder.debug'",
        "'storageProviderAuthorities', 'com.mf.mylauncher.scoped.gamefolder'",
        "'shareProviderAuthority', 'com.mf.mylauncher.scoped.controlfolder.debug'",
    )
    missing_values = [value for value in required_gradle_values if value not in build_text]
    if missing_values:
        raise ValueError(f"Gradle app labels or authorities are incomplete: {missing_values}")
    if "org.angelauramc.amethyst" in strings_text:
        raise ValueError("An upstream application authority remains in strings.xml")
    if not re.search(r'<string\s+name="main_add_account"', strings_text):
        raise ValueError("Required main_add_account string is missing from strings.xml")
    graphics_label = '<string name="mylauncher_graphics_renderer">Graphics &amp; Renderer</string>'
    if not re.search(r'<string\s+name="mylauncher_graphics_renderer"', strings_text):
        account_label = re.search(
            r'<string\s+name="main_add_account"[^>]*>.*?</string>',
            strings_text,
            flags=re.DOTALL,
        )
        if not account_label:
            raise ValueError("Could not find main_add_account to insert the graphics shortcut label")
        strings_text = strings_text[:account_label.end()] + "\n    " + graphics_label + strings_text[account_label.end():]
    if (
        len(re.findall(r'<string\s+name="mylauncher_graphics_renderer"', strings_text)) != 1
        or graphics_label not in strings_text
    ):
        raise ValueError("The Graphics & Renderer launcher label is missing or duplicated")
    control_labels = (
        '<string name="mylauncher_accounts">Accounts: Microsoft / Offline</string>',
        '<string name="mylauncher_instances">Create Instance / Profile</string>',
        '<string name="mylauncher_java_runtime">Java &amp; Runtime</string>',
        '<string name="mylauncher_launcher_settings">Launcher Settings</string>',
        '<string name="mylauncher_customize_controls">Customize Controls</string>',
        '<string name="mylauncher_control_settings">Controls &amp; Touch Settings</string>',
        '<string name="mylauncher_performance_overlay_category">Performance Overlay</string>',
        '<string name="mylauncher_performance_overlay_enabled">Show in-game performance overlay</string>',
        '<string name="mylauncher_performance_overlay_enabled_summary">Display live FPS and process memory. Drag the overlay to move it; toggle it from the in-game menu.</string>',
        '<string name="mylauncher_performance_overlay_fps">Show FPS</string>',
        '<string name="mylauncher_performance_overlay_fps_summary">Count real renderer frame swaps per second.</string>',
        '<string name="mylauncher_performance_overlay_memory">Show memory usage</string>',
        '<string name="mylauncher_performance_overlay_memory_summary">Show the launcher process memory resident in RAM.</string>',
        '<string name="mylauncher_toggle_performance_overlay">Toggle Performance Overlay</string>',
        '<string name="mylauncher_performance_overlay_enabled_toast">Performance overlay enabled</string>',
        '<string name="mylauncher_performance_overlay_disabled_toast">Performance overlay disabled</string>',
        '<string name="mylauncher_performance_overlay_fps_prefix">FPS</string>',
        '<string name="mylauncher_performance_overlay_memory_prefix">RAM</string>',
    )
    for control_label in control_labels:
        control_key = re.search(r'<string\s+name="([^"]+)"', control_label).group(1)
        if not re.search(r'<string\s+name="' + re.escape(control_key) + r'"', strings_text):
            graphics_match = re.search(
                r'<string\s+name="mylauncher_graphics_renderer"[^>]*>.*?</string>',
                strings_text,
                flags=re.DOTALL,
            )
            if not graphics_match:
                raise ValueError("Could not find Graphics & Renderer label insertion point for control settings")
            strings_text = strings_text[:graphics_match.end()] + "\n    " + control_label + strings_text[graphics_match.end():]
        if (
            len(re.findall(r'<string\s+name="' + re.escape(control_key) + r'"', strings_text)) != 1
            or control_label not in strings_text
        ):
            raise ValueError(f"Voidcraft control label {control_key} is missing or duplicated")
    strings.write_text(strings_text, encoding="utf-8")

    colors_text = colors.read_text(encoding="utf-8")
    for color_name in ("primary_text", "secondary_text"):
        if not re.search(r'<color\s+name="' + color_name + r'"', colors_text):
            raise ValueError(f"Required header color {color_name!r} is missing from colors.xml")

    settings_text = settings.read_text(encoding="utf-8")
    settings_text = replace_idempotently(settings_text, "Amethyst", "myLauncher", str(settings))
    if not re.search(r"rootProject\.name\s*=\s*['\"]myLauncher['\"]", settings_text):
        raise ValueError("Gradle rootProject.name was not customized")
    settings.write_text(settings_text, encoding="utf-8")

    tools_java = upstream / "app_pojavlauncher" / "src" / "main" / "java" / "net" / "kdt" / "pojavlaunch" / "Tools.java"
    if not tools_java.is_file():
        raise FileNotFoundError(f"Expected upstream tools class is missing: {tools_java}")
    tools_text = tools_java.read_text(encoding="utf-8")
    tools_text = replace_idempotently(
        tools_text,
        'public static String APP_NAME = "Amethyst";',
        'public static String APP_NAME = "myLauncher";',
        str(tools_java),
    )
    if 'public static String APP_NAME = "Amethyst";' in tools_text:
        raise ValueError("The shared launcher app-name constant still uses upstream branding")
    tools_java.write_text(tools_text, encoding="utf-8")

    # The upstream local/offline account flow exists, but this guard blocks it when
    # there is no online account. Keep offline account creation available.
    local_login = upstream / "app_pojavlauncher" / "src" / "main" / "java" / "net" / "kdt" / "pojavlaunch" / "fragments" / "LocalLoginFragment.java"
    if not local_login.is_file():
        raise FileNotFoundError(f"Expected offline account screen is missing: {local_login}")
    login_text = local_login.read_text(encoding="utf-8")
    login_text = re.sub(
        r"^import static net\.kdt\.pojavlaunch\.Tools\.hasOnlineProfile;\n",
        "",
        login_text,
        flags=re.MULTILINE,
    )
    login_guard = """        // This is overkill but meh
        if (!hasOnlineProfile()){
            Tools.swapFragment(requireActivity(), MainMenuFragment.class, MainMenuFragment.TAG, null);
        }
"""
    if login_guard in login_text:
        login_text = login_text.replace(login_guard, "        // Offline accounts are supported; do not require an online profile to add one.\n")
    elif "if (!hasOnlineProfile())" in login_text:
        raise ValueError("Unrecognized offline-login guard; refusing an unsafe source edit")
    if "if (!hasOnlineProfile())" in login_text or "import static net.kdt.pojavlaunch.Tools.hasOnlineProfile;" in login_text:
        raise ValueError("Offline account screen still contains its online-profile gate/import")
    if (
        "ExtraCore.setValue(ExtraConstants.MOJANG_LOGIN_TODO, new String[]{" not in login_text
        or "if(!checkEditText())" not in login_text
    ):
        raise ValueError("The upstream offline-login submission/username validation flow is incomplete")

    # Fail closed if upstream changes the local username rules. Local profile names
    # must remain valid and unique rather than silently admitting unusable profiles.
    required_validation_imports = (
        "import java.io.File;",
        "import java.util.regex.Matcher;",
        "import java.util.regex.Pattern;",
    )
    missing_imports = [value for value in required_validation_imports if value not in login_text]
    if missing_imports:
        raise ValueError(f"Offline username validation imports are missing: {missing_imports}")
    if 'mUsernameValidationPattern = Pattern.compile("^[a-zA-Z0-9_]*$");' not in login_text:
        raise ValueError("Offline username validation pattern must allow only letters, digits, and underscores")

    validation_method = re.search(
        r"private\s+boolean\s+checkEditText\s*\(\s*\)\s*\{(?P<body>.*?)^\s*\}",
        login_text,
        flags=re.DOTALL | re.MULTILINE,
    )
    if not validation_method:
        raise ValueError("Offline account checkEditText() username validator is missing")
    username_validation_anchors = (
        "String text = mUsernameEditText.getText().toString();",
        "Matcher matcher = mUsernameValidationPattern.matcher(text);",
        "return !(text.isEmpty() ||",
        "text.length() < 3 ||",
        "text.length() > 16 ||",
        "!matcher.find() ||",
        'new File(Tools.DIR_ACCOUNT_NEW + "/" + text + ".json").exists()',
    )
    # Normalize whitespace so validation checks the operators and their ordering,
    # not harmless formatting differences in the pinned upstream method.
    validation_body = " ".join(validation_method.group("body").split())
    missing_validation = [
        anchor for anchor in username_validation_anchors if anchor not in validation_body
    ]
    if missing_validation:
        raise ValueError(
            "Offline username validation is incomplete; expected username syntax, "
            f"length, and duplicate-profile guards are missing: {missing_validation}"
        )
    # Validate the submitted value separately after syntax/length checks so fixture tests
    # can detect each broken guard; CI also runs launcher/test.sh when the isolated core exists.
    normalized_login_text = " ".join(login_text.split())
    expected_username_submission = (
        'ExtraCore.setValue(ExtraConstants.MOJANG_LOGIN_TODO, '
        'new String[]{ mUsernameEditText.getText().toString(), "" });'
    )
    if expected_username_submission not in normalized_login_text:
        raise ValueError(
            "The upstream offline-login submission is incomplete or does not submit the validated username"
        )
    local_login.write_text(login_text, encoding="utf-8")

    login_layout = upstream / "app_pojavlauncher" / "src" / "main" / "res" / "layout" / "fragment_local_login.xml"
    if not login_layout.is_file():
        raise FileNotFoundError(f"Expected offline login layout is missing: {login_layout}")
    login_layout_text = login_layout.read_text(encoding="utf-8")
    login_layout_text = login_layout_text.replace('android:inputType="textEmailAddress"', 'android:inputType="textPersonName"')
    login_layout_text = replace_idempotently(
        login_layout_text,
        'android:text="@string/login_online_login_label"',
        'android:text="@string/mylauncher_accounts"',
        str(login_layout),
    )
    if (
        login_layout_text.count('android:inputType="textPersonName"') != 1
        or 'android:inputType="textEmailAddress"' in login_layout_text
        or 'android:text="@string/mylauncher_accounts"' not in login_layout_text
        or 'android:text="@string/login_online_login_label"' in login_layout_text
    ):
        raise ValueError("Offline account form must use a username field and an Add account action")
    login_layout.write_text(login_layout_text, encoding="utf-8")

    # Add a responsive branded header without introducing heavyweight UI dependencies.
    header = """            <TextView
                android:id="@+id/mylauncher_hero_title"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="20dp"
                android:layout_marginTop="18dp"
                android:layout_marginEnd="20dp"
                android:text="myLauncher"
                android:textColor="@color/primary_text"
                android:textSize="26sp"
                android:textStyle="bold"
                app:layout_constraintTop_toTopOf="parent"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintEnd_toEndOf="parent" />

            <TextView
                android:id="@+id/mylauncher_hero_subtitle"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="20dp"
                android:layout_marginTop="2dp"
                android:layout_marginEnd="20dp"
                android:layout_marginBottom="12dp"
                android:text="Minecraft Java Edition · built for lightweight devices"
                android:textColor="@color/secondary_text"
                android:textSize="12sp"
                app:layout_constraintTop_toBottomOf="@id/mylauncher_hero_title"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintEnd_toEndOf="parent" />

"""
    for relative_layout in (
        "app_pojavlauncher/src/main/res/layout/fragment_launcher.xml",
        "app_pojavlauncher/src/main/res/layout-land/fragment_launcher.xml",
    ):
        layout_path = upstream / relative_layout
        if not layout_path.is_file():
            raise FileNotFoundError(f"Expected launcher layout is missing: {layout_path}")
        layout_text = layout_path.read_text(encoding="utf-8")
        if "mylauncher_hero_title" not in layout_text:
            anchor_match = re.search(r"^[ \t]*<androidx\.constraintlayout\.widget\.Guideline", layout_text, flags=re.MULTILINE)
            if not anchor_match:
                raise ValueError(f"Could not find launcher layout insertion point in {layout_path}")
            layout_text = layout_text[:anchor_match.start()] + header + layout_text[anchor_match.start():]
        for button_id in ("news_button", "discord_button"):
            pattern = re.compile(r'(<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/' + button_id + r'".*?/>\s*)', re.DOTALL)
            match = pattern.search(layout_text)
            if not match:
                raise ValueError(f"Could not find {button_id} in {layout_path}")
            block = match.group(1).replace(
                'app:layout_constraintTop_toTopOf="parent"',
                'app:layout_constraintTop_toBottomOf="@id/mylauncher_hero_subtitle"'
            )
            layout_text = layout_text[:match.start(1)] + block + layout_text[match.end(1):]
            patched_button = re.search(
                r'<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/'
                + re.escape(button_id)
                + r'".*?/>',
                layout_text,
                flags=re.DOTALL,
            )
            if not patched_button or 'app:layout_constraintTop_toBottomOf="@id/mylauncher_hero_subtitle"' not in patched_button.group(0):
                raise ValueError(f"Could not position {button_id} beneath the myLauncher header in {layout_path}")

        layout_text = re.sub(
            r'(android:id="@\+id/social_divider".*?app:layout_constraintTop_toTopOf=)"parent"',
            r'\1"@id/news_button"',
            layout_text,
            count=1,
            flags=re.DOTALL
        )
        divider_match = re.search(
            r'<View\s+android:id="@\+id/social_divider".*?/>',
            layout_text,
            flags=re.DOTALL,
        )
        if not divider_match or 'app:layout_constraintTop_toTopOf="@id/news_button"' not in divider_match.group(0):
            raise ValueError(f"Could not align social divider with news_button in {layout_path}")

        custom_control_match = re.search(
            r'<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/custom_control_button".*?/>',
            layout_text,
            flags=re.DOTALL,
        )
        if not custom_control_match:
            raise ValueError(f"Could not find custom_control_button in {layout_path}")
        custom_control_block = custom_control_match.group(0)
        if 'android:text="@string/mylauncher_customize_controls"' not in custom_control_block:
            if 'android:text="@string/mcl_option_customcontrol"' not in custom_control_block:
                raise ValueError(f"Unexpected Custom Controls label in {layout_path}")
            custom_control_block = custom_control_block.replace(
                'android:text="@string/mcl_option_customcontrol"',
                'android:text="@string/mylauncher_customize_controls"',
                1,
            )
            layout_text = (
                layout_text[:custom_control_match.start()]
                + custom_control_block
                + layout_text[custom_control_match.end():]
            )

        if 'android:id="@+id/mylauncher_control_settings_button"' not in layout_text:
            control_settings_button = """            <com.kdt.mcgui.LauncherMenuButton
                android:id="@+id/mylauncher_control_settings_button"
                style="@style/LauncherMenuButton.Universal"
                android:layout_width="match_parent"
                android:drawableStart="@drawable/ic_setting_control_scale"
                android:text="@string/mylauncher_control_settings"
                app:layout_constraintTop_toBottomOf="@id/custom_control_button" />
"""
            custom_control_match = re.search(
                r'<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/custom_control_button".*?/>',
                layout_text,
                flags=re.DOTALL,
            )
            if not custom_control_match:
                raise ValueError(f"Could not find insertion point for Controls & Touch Settings in {layout_path}")
            layout_text = (
                layout_text[:custom_control_match.end()]
                + "\n\\n"
                + control_settings_button
                + layout_text[custom_control_match.end():]
            )

        install_jar_match = re.search(
            r'<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/install_jar_button".*?/>',
            layout_text,
            flags=re.DOTALL,
        )
        if not install_jar_match:
            raise ValueError(f"Could not find install_jar_button after adding control settings in {layout_path}")
        install_jar_block = install_jar_match.group(0)
        old_install_constraint = 'app:layout_constraintTop_toBottomOf="@id/custom_control_button"'
        new_install_constraint = 'app:layout_constraintTop_toBottomOf="@id/mylauncher_control_settings_button"'
        if old_install_constraint in install_jar_block:
            install_jar_block = install_jar_block.replace(old_install_constraint, new_install_constraint, 1)
            layout_text = (
                layout_text[:install_jar_match.start()]
                + install_jar_block
                + layout_text[install_jar_match.end():]
            )
        elif new_install_constraint not in install_jar_block:
            raise ValueError(f"Install JAR button would overlap the control settings shortcut in {layout_path}")

        if "mylauncher_account_button" not in layout_text:
            account_button = """            <com.kdt.mcgui.LauncherMenuButton
                android:id="@+id/mylauncher_account_button"
                style="@style/LauncherMenuButton.Universal"
                android:layout_width="match_parent"
                android:drawableStart="@android:drawable/ic_menu_myplaces"
                android:text="@string/mylauncher_accounts"
                app:layout_constraintTop_toBottomOf="@id/open_files_button" />

"""
            open_files_pattern = re.compile(r'(<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/open_files_button".*?/>\s*)', re.DOTALL)
            open_files_match = open_files_pattern.search(layout_text)
            if not open_files_match:
                raise ValueError(f"Could not find open_files_button in {layout_path}")
            block = open_files_match.group(1) + "\n" + account_button
            layout_text = layout_text[:open_files_match.start(1)] + block + layout_text[open_files_match.end(1):]

        account_match = re.search(
            r'<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/mylauncher_account_button".*?/>',
            layout_text,
            flags=re.DOTALL,
        )
        open_files_index = layout_text.find('android:id="@+id/open_files_button"')
        if (
            not account_match
            or account_match.group(0).count('android:id="@+id/mylauncher_account_button"') != 1
            or 'style="@style/LauncherMenuButton.Universal"' not in account_match.group(0)
            or 'android:layout_width="match_parent"' not in account_match.group(0)
            or 'android:drawableStart="@android:drawable/ic_menu_myplaces"' not in account_match.group(0)
            or 'android:text="@string/mylauncher_accounts"' not in account_match.group(0)
            or 'app:layout_constraintTop_toBottomOf="@id/open_files_button"' not in account_match.group(0)
            or open_files_index < 0
            or open_files_index > account_match.start()
        ):
            raise ValueError(f"myLauncher account shortcut is incomplete in {layout_path}")

        if 'android:id="@+id/mylauncher_graphics_button"' not in layout_text:
            graphics_button = """            <com.kdt.mcgui.LauncherMenuButton
                android:id="@+id/mylauncher_graphics_button"
                style="@style/LauncherMenuButton.Universal"
                android:layout_width="match_parent"
                android:drawableStart="@drawable/ic_setting_video"
                android:text="@string/mylauncher_graphics_renderer"
                app:layout_constraintTop_toBottomOf="@id/mylauncher_account_button" />
"""
            layout_text = layout_text[:account_match.end()] + "\n\n" + graphics_button + layout_text[account_match.end():]

        account_match = re.search(
            r'<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/mylauncher_account_button".*?/>',
            layout_text,
            flags=re.DOTALL,
        )
        graphics_match = re.search(
            r'<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/mylauncher_graphics_button".*?/>',
            layout_text,
            flags=re.DOTALL,
        )
        if (
            layout_text.count('android:id="@+id/mylauncher_graphics_button"') != 1
            or not graphics_match
            or 'style="@style/LauncherMenuButton.Universal"' not in graphics_match.group(0)
            or 'android:layout_width="match_parent"' not in graphics_match.group(0)
            or 'android:drawableStart="@drawable/ic_setting_video"' not in graphics_match.group(0)
            or 'android:text="@string/mylauncher_graphics_renderer"' not in graphics_match.group(0)
            or 'app:layout_constraintTop_toBottomOf="@id/mylauncher_account_button"' not in graphics_match.group(0)
            or not account_match
            or account_match.start() > graphics_match.start()
        ):
            raise ValueError(f"myLauncher Graphics & Renderer shortcut is incomplete in {layout_path}")

        integrated_menu_buttons = (
            (
                "mylauncher_instances_button",
                "@android:drawable/ic_menu_add",
                "@string/mylauncher_instances",
                "mylauncher_graphics_button",
            ),
            (
                "mylauncher_java_runtime_button",
                "@android:drawable/ic_menu_manage",
                "@string/mylauncher_java_runtime",
                "mylauncher_instances_button",
            ),
            (
                "mylauncher_launcher_settings_button",
                "@android:drawable/ic_menu_preferences",
                "@string/mylauncher_launcher_settings",
                "mylauncher_java_runtime_button",
            ),
        )
        for button_id, button_drawable, button_label, anchor_id in integrated_menu_buttons:
            button_pattern = re.compile(
                r'<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/'
                + re.escape(button_id)
                + r'".*?/>',
                flags=re.DOTALL,
            )
            if not button_pattern.search(layout_text):
                anchor_pattern = re.compile(
                    r'<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/'
                    + re.escape(anchor_id)
                    + r'".*?/>',
                    flags=re.DOTALL,
                )
                anchor_match = anchor_pattern.search(layout_text)
                if not anchor_match:
                    raise ValueError(f"Could not find {anchor_id} to add {button_id} in {layout_path}")
                button_block = (
                    '            <com.kdt.mcgui.LauncherMenuButton\n'
                    + f'                android:id="@+id/{button_id}"\n'
                    + '                style="@style/LauncherMenuButton.Universal"\n'
                    + '                android:layout_width="match_parent"\n'
                    + f'                android:drawableStart="{button_drawable}"\n'
                    + f'                android:text="{button_label}"\n'
                    + f'                app:layout_constraintTop_toBottomOf="@id/{anchor_id}" />\n'
                )
                layout_text = layout_text[:anchor_match.end()] + "\n\n" + button_block + layout_text[anchor_match.end():]
            current_button = button_pattern.search(layout_text)
            if (
                not current_button
                or layout_text.count(f'android:id="@+id/{button_id}"') != 1
                or 'style="@style/LauncherMenuButton.Universal"' not in current_button.group(0)
                or 'android:layout_width="match_parent"' not in current_button.group(0)
                or f'android:drawableStart="{button_drawable}"' not in current_button.group(0)
                or f'android:text="{button_label}"' not in current_button.group(0)
                or f'app:layout_constraintTop_toBottomOf="@id/{anchor_id}"' not in current_button.group(0)
            ):
                raise ValueError(f"Voidcraft integrated launcher shortcut {button_id} is incomplete in {layout_path}")

        title_match = re.search(
            r'<TextView\s+android:id="@\+id/mylauncher_hero_title".*?/>',
            layout_text,
            flags=re.DOTALL,
        )
        subtitle_match = re.search(
            r'<TextView\s+android:id="@\+id/mylauncher_hero_subtitle".*?/>',
            layout_text,
            flags=re.DOTALL,
        )
        custom_control_match = re.search(
            r'<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/custom_control_button".*?/>',
            layout_text,
            flags=re.DOTALL,
        )
        control_settings_match = re.search(
            r'<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/mylauncher_control_settings_button".*?/>',
            layout_text,
            flags=re.DOTALL,
        )
        install_jar_match = re.search(
            r'<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/install_jar_button".*?/>',
            layout_text,
            flags=re.DOTALL,
        )
        if (
            not custom_control_match
            or 'android:text="@string/mylauncher_customize_controls"' not in custom_control_match.group(0)
            or not control_settings_match
            or 'style="@style/LauncherMenuButton.Universal"' not in control_settings_match.group(0)
            or 'android:layout_width="match_parent"' not in control_settings_match.group(0)
            or 'android:drawableStart="@drawable/ic_setting_control_scale"' not in control_settings_match.group(0)
            or 'android:text="@string/mylauncher_control_settings"' not in control_settings_match.group(0)
            or 'app:layout_constraintTop_toBottomOf="@id/custom_control_button"' not in control_settings_match.group(0)
            or not install_jar_match
            or 'app:layout_constraintTop_toBottomOf="@id/mylauncher_control_settings_button"' not in install_jar_match.group(0)
        ):
            raise ValueError(f"Voidcraft Customize Controls or Controls & Touch Settings is incomplete in {layout_path}")

        if (
            layout_text.count('android:id="@+id/mylauncher_hero_title"') != 1
            or layout_text.count('android:id="@+id/mylauncher_hero_subtitle"') != 1
            or layout_text.count('android:id="@+id/custom_control_button"') != 1
            or layout_text.count('android:id="@+id/mylauncher_control_settings_button"') != 1
            or layout_text.count('android:id="@+id/mylauncher_account_button"') != 1
            or layout_text.count('android:id="@+id/mylauncher_graphics_button"') != 1
            or layout_text.count('android:id="@+id/news_button"') != 1
            or layout_text.count('android:id="@+id/discord_button"') != 1
            or layout_text.count('android:id="@+id/social_divider"') != 1
            or not title_match
            or not subtitle_match
            or 'android:layout_width="0dp"' not in title_match.group(0)
            or 'android:text="myLauncher"' not in title_match.group(0)
            or 'android:layout_width="0dp"' not in subtitle_match.group(0)
            or 'android:text="Minecraft Java Edition · built for lightweight devices"' not in subtitle_match.group(0)
            or 'app:layout_constraintTop_toBottomOf="@id/mylauncher_hero_title"' not in subtitle_match.group(0)
        ):
            raise ValueError(f"myLauncher header or account button is missing, duplicated, or incomplete in {layout_path}")
        # Keep generated XML clean so CI's git diff --check is a reliable guard.
        layout_text = re.sub(r"(?m)[ \t]+$", "", layout_text)
        layout_path.write_text(layout_text, encoding="utf-8")

    # Ensure the account shortcut still has a consumer in the upstream activity.
    launcher_activity = upstream / "app_pojavlauncher" / "src" / "main" / "java" / "net" / "kdt" / "pojavlaunch" / "LauncherActivity.java"
    if not launcher_activity.is_file():
        raise FileNotFoundError(f"Expected launcher activity is missing: {launcher_activity}")
    activity_text = launcher_activity.read_text(encoding="utf-8")
    if (
        "ExtraCore.addExtraListener(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);" not in activity_text
        or "Tools.swapFragment(this, SelectAuthFragment.class, SelectAuthFragment.TAG, null);" not in activity_text
    ):
        raise ValueError("The upstream SELECT_AUTH_METHOD event is not wired to the account selector")

    auth_listener = re.search(
        r"private\s+final\s+ExtraListener<Boolean>\s+mSelectAuthMethod\s*=\s*\(key,\s*value\)\s*->\s*\{(?P<body>.*?)^\s*\};",
        activity_text,
        flags=re.DOTALL | re.MULTILINE,
    )
    if not auth_listener:
        raise ValueError("The SELECT_AUTH_METHOD listener body is missing")
    auth_listener_body = " ".join(auth_listener.group("body").split())
    required_listener_anchors = (
        "Fragment fragment = getSupportFragmentManager().findFragmentById(mFragmentView.getId());",
        "if(!(fragment instanceof MainMenuFragment)) return false;",
        "Tools.swapFragment(this, SelectAuthFragment.class, SelectAuthFragment.TAG, null);",
        "return false;",
    )
    missing_listener_anchors = [
        anchor for anchor in required_listener_anchors if anchor not in auth_listener_body
    ]
    if missing_listener_anchors:
        raise ValueError(
            "The SELECT_AUTH_METHOD listener is incomplete or unsafe: "
            f"{missing_listener_anchors}"
        )
    if auth_listener_body.index("if(!(fragment instanceof MainMenuFragment)) return false;") > auth_listener_body.index(
        "Tools.swapFragment(this, SelectAuthFragment.class, SelectAuthFragment.TAG, null);"
    ):
        raise ValueError("The SELECT_AUTH_METHOD listener must check the active fragment before navigation")

    main_menu = upstream / "app_pojavlauncher" / "src" / "main" / "java" / "net" / "kdt" / "pojavlaunch" / "fragments" / "MainMenuFragment.java"
    if not main_menu.is_file():
        raise FileNotFoundError(f"Expected main menu fragment is missing: {main_menu}")
    main_menu_text = main_menu.read_text(encoding="utf-8")
    graphics_imports = (
        "import net.kdt.pojavlaunch.LauncherActivity;",
        "import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceVideoFragment;",
        "import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceControlFragment;",
        "import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceJavaFragment;",
        "import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;",
    )
    import_anchor = "import net.kdt.pojavlaunch.extra.ExtraCore;"
    if import_anchor not in main_menu_text:
        raise ValueError("Could not find imports needed for Graphics & Renderer navigation")
    for graphics_import in graphics_imports:
        if graphics_import not in main_menu_text:
            main_menu_text = main_menu_text.replace(import_anchor, import_anchor + "\n" + graphics_import, 1)
            import_anchor = graphics_import
    # The game-directory browser is local file access and should also work for local profiles.
    # Preserve the demo-profile warning, but remove the unrelated online-account gate.
    directory_gate = """            } else if (!hasOnlineProfile()) { // Otherwise display the generic pop-up to log in
                hasNoOnlineProfileDialog(requireActivity());
            } else openPath(v.getContext(), getCurrentProfileDirectory(), false);"""
    directory_allowed = """            } else openPath(v.getContext(), getCurrentProfileDirectory(), false);"""
    if directory_gate in main_menu_text:
        main_menu_text = main_menu_text.replace(directory_gate, directory_allowed, 1)
    elif directory_allowed not in main_menu_text:
        raise ValueError("Could not make game-directory access available to local profiles safely")
    directory_handler = re.search(
        r"mOpenDirectoryButton\.setOnClickListener.*?mNewsButton\.setOnLongClickListener",
        main_menu_text,
        flags=re.DOTALL,
    )
    if (
        not directory_handler
        or "Tools.isDemoProfile(v.getContext())" not in directory_handler.group(0)
        or "else openPath(v.getContext(), getCurrentProfileDirectory(), false);" not in directory_handler.group(0)
        or "else if (!hasOnlineProfile())" in directory_handler.group(0)
    ):
        raise ValueError("Game-directory access or demo-profile protection is incomplete")

    account_binding = "Button mMyLauncherAccountButton = view.findViewById(R.id.mylauncher_account_button);"
    if account_binding not in main_menu_text:
        binding_anchor = "Button mOpenDirectoryButton = view.findViewById(R.id.open_files_button);"
        if binding_anchor not in main_menu_text:
            raise ValueError("Could not find the main menu button binding insertion point")
        main_menu_text = main_menu_text.replace(binding_anchor, binding_anchor + "\n        " + account_binding, 1)
    account_listener = "mMyLauncherAccountButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true));"
    if account_listener not in main_menu_text:
        listener_anchor = "mVersionSpinner = view.findViewById(R.id.mc_version_spinner);"
        if listener_anchor not in main_menu_text:
            raise ValueError("Could not find the main menu listener insertion point")
        main_menu_text = main_menu_text.replace(listener_anchor, listener_anchor + "\n        " + account_listener, 1)
    control_settings_binding = "Button mMyLauncherControlSettingsButton = view.findViewById(R.id.mylauncher_control_settings_button);"
    if control_settings_binding not in main_menu_text:
        if account_binding not in main_menu_text:
            raise ValueError("Could not find account binding insertion point for Controls & Touch Settings")
        main_menu_text = main_menu_text.replace(
            account_binding, account_binding + "\n        " + control_settings_binding, 1
        )

    graphics_binding = "Button mMyLauncherGraphicsButton = view.findViewById(R.id.mylauncher_graphics_button);"
    if graphics_binding not in main_menu_text:
        if account_binding not in main_menu_text:
            raise ValueError("Could not find the account binding insertion point for graphics settings")
        main_menu_text = main_menu_text.replace(
            account_binding, account_binding + "\n        " + graphics_binding, 1
        )

    instances_binding = "Button mMyLauncherInstancesButton = view.findViewById(R.id.mylauncher_instances_button);"
    runtime_binding = "Button mMyLauncherRuntimeButton = view.findViewById(R.id.mylauncher_java_runtime_button);"
    settings_binding = "Button mMyLauncherSettingsButton = view.findViewById(R.id.mylauncher_launcher_settings_button);"
    for new_binding in (instances_binding, runtime_binding, settings_binding):
        if new_binding not in main_menu_text:
            main_menu_text = main_menu_text.replace(graphics_binding, graphics_binding + "\n        " + new_binding, 1)
        if main_menu_text.count(new_binding) != 1:
            raise ValueError(f"Integrated launcher menu binding {new_binding} is missing or duplicated")
    graphics_listener = (
        "mMyLauncherGraphicsButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), "
        "LauncherPreferenceVideoFragment.class, LauncherActivity.SETTING_FRAGMENT_TAG, null));"
    )
    if graphics_listener not in main_menu_text:
        if account_listener not in main_menu_text:
            raise ValueError("Could not find the account listener insertion point for graphics settings")
        main_menu_text = main_menu_text.replace(
            account_listener, account_listener + "\n        " + graphics_listener, 1
        )
    control_settings_listener = (
        "mMyLauncherControlSettingsButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), "
        "LauncherPreferenceControlFragment.class, LauncherActivity.SETTING_FRAGMENT_TAG, null));"
    )
    if control_settings_listener not in main_menu_text:
        if graphics_listener not in main_menu_text:
            raise ValueError("Could not find Graphics & Renderer listener insertion point for control settings")
        main_menu_text = main_menu_text.replace(
            graphics_listener, graphics_listener + "\n        " + control_settings_listener, 1
        )
    instances_listener = (
        "mMyLauncherInstancesButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), "
        "ProfileTypeSelectFragment.class, ProfileTypeSelectFragment.TAG, null));"
    )
    runtime_listener = (
        "mMyLauncherRuntimeButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), "
        "LauncherPreferenceJavaFragment.class, LauncherActivity.SETTING_FRAGMENT_TAG, null));"
    )
    settings_listener = (
        "mMyLauncherSettingsButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), "
        "LauncherPreferenceFragment.class, LauncherActivity.SETTING_FRAGMENT_TAG, null));"
    )
    for new_listener, insertion_anchor in (
        (instances_listener, control_settings_listener),
        (runtime_listener, instances_listener),
        (settings_listener, runtime_listener),
    ):
        if new_listener not in main_menu_text:
            if insertion_anchor not in main_menu_text:
                raise ValueError("Could not find the integrated launcher navigation insertion point")
            main_menu_text = main_menu_text.replace(insertion_anchor, insertion_anchor + "\n        " + new_listener, 1)

    if (
        main_menu_text.count(instances_binding) != 1
        or main_menu_text.count(runtime_binding) != 1
        or main_menu_text.count(settings_binding) != 1
        or main_menu_text.count(instances_listener) != 1
        or main_menu_text.count(runtime_listener) != 1
        or main_menu_text.count(settings_listener) != 1
        or "LauncherPreferenceJavaFragment.class" not in main_menu_text
        or "LauncherPreferenceFragment.class" not in main_menu_text
        or "ProfileTypeSelectFragment.class" not in main_menu_text
    ):
        raise ValueError("Integrated launcher instance, Java/runtime, or settings navigation is incomplete")

    if (
        main_menu_text.count(account_binding) != 1
        or main_menu_text.count(account_listener) != 1
        or main_menu_text.count(control_settings_binding) != 1
        or main_menu_text.count(control_settings_listener) != 1
        or main_menu_text.count(graphics_binding) != 1
        or main_menu_text.count(graphics_listener) != 1
        or "import android.widget.Button;" not in main_menu_text
        or "import net.kdt.pojavlaunch.extra.ExtraConstants;" not in main_menu_text
        or "import net.kdt.pojavlaunch.extra.ExtraCore;" not in main_menu_text
        or "import net.kdt.pojavlaunch.LauncherActivity;" not in main_menu_text
        or "import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceVideoFragment;" not in main_menu_text
        or "import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceControlFragment;" not in main_menu_text
    ):
        raise ValueError("The account, graphics, or controls shortcut binding, handler, or imports are incomplete")
    main_menu.write_text(main_menu_text, encoding="utf-8")

    # Replace the upstream launcher artwork with a small vector mark that scales cleanly.
    manifest = upstream / "app_pojavlauncher" / "src" / "main" / "AndroidManifest.xml"
    if not manifest.is_file():
        raise FileNotFoundError(f"Expected Android manifest is missing: {manifest}")
    manifest_text = manifest.read_text(encoding="utf-8")
    manifest_text = manifest_text.replace('android:icon="@mipmap/ic_launcher"', 'android:icon="@drawable/mylauncher_icon"')
    manifest_text = manifest_text.replace('android:roundIcon="@mipmap/ic_launcher_round"', 'android:roundIcon="@drawable/mylauncher_icon"')
    if (
        manifest_text.count('android:icon="@drawable/mylauncher_icon"') != 1
        or manifest_text.count('android:roundIcon="@drawable/mylauncher_icon"') != 1
        or 'android:label="@string/app_name"' not in manifest_text
        or "org.angelauramc.amethyst" in manifest_text
        or '@mipmap/ic_launcher' in manifest_text
        or '@mipmap/ic_launcher_round' in manifest_text
    ):
        raise ValueError("Manifest branding, package references, or app icon are incomplete")
    manifest.write_text(manifest_text, encoding="utf-8")
    icon = upstream / "app_pojavlauncher" / "src" / "main" / "res" / "drawable" / "mylauncher_icon.xml"
    icon.parent.mkdir(parents=True, exist_ok=True)
    icon.write_text("""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#171B26" android:pathData="M0,0h108v108h-108z" />
    <path android:fillColor="#7C5CFF" android:pathData="M18,28H32L54,54L76,28H90V80H76V50L54,76L32,50V80H18Z" />
</vector>
""", encoding="utf-8")

    # The upstream auth selector also blocks local profiles until an online account exists.
    # Replace that gate so users can actually choose the offline account path.
    auth_selector = upstream / "app_pojavlauncher" / "src" / "main" / "java" / "net" / "kdt" / "pojavlaunch" / "fragments" / "SelectAuthFragment.java"
    if not auth_selector.is_file():
        raise FileNotFoundError(f"Expected authentication selector is missing: {auth_selector}")
    auth_text = auth_selector.read_text(encoding="utf-8")
    old_local_auth = "mLocalButton.setOnClickListener(v -> hasNoOnlineProfileDialog(requireActivity(), () -> Tools.swapFragment(requireActivity(), LocalLoginFragment.class, LocalLoginFragment.TAG, null)));"
    new_local_auth = "mLocalButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), LocalLoginFragment.class, LocalLoginFragment.TAG, null));"
    if old_local_auth in auth_text:
        auth_text = auth_text.replace(old_local_auth, new_local_auth)
    elif new_local_auth not in auth_text:
        raise ValueError("Could not find the local-account selection handler")
    auth_text = re.sub(
        r"^import static net\.kdt\.pojavlaunch\.Tools\.hasNoOnlineProfileDialog;\n",
        "",
        auth_text,
        flags=re.MULTILINE,
    )
    microsoft_auth_handler = "mMicrosoftButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), MicrosoftLoginFragment.class, MicrosoftLoginFragment.TAG, null));"
    if (
        "hasNoOnlineProfileDialog(requireActivity()" in auth_text
        or "import static net.kdt.pojavlaunch.Tools.hasNoOnlineProfileDialog;" in auth_text
        or auth_text.count(new_local_auth) != 1
        or auth_text.count(microsoft_auth_handler) != 1
    ):
        raise ValueError("Offline account selection is gated or an account sign-in handler is incomplete")
    auth_selector.write_text(auth_text, encoding="utf-8")

    launcher_preferences = upstream / "app_pojavlauncher" / "src" / "main" / "java" / "net" / "kdt" / "pojavlaunch" / "prefs" / "LauncherPreferences.java"
    if not launcher_preferences.is_file():
        raise FileNotFoundError(f"Expected upstream file is missing: {launcher_preferences}")
    preferences_text = launcher_preferences.read_text(encoding="utf-8")
    preferences_text = replace_idempotently(
        preferences_text,
        "int targetSide = isDevicePowerful ? 1080 : 720;",
        "int targetSide = Tools.getTotalDeviceMemory(context) <= 2048 ? 480 : (isDevicePowerful ? 1080 : 720);",
        str(launcher_preferences),
    )
    preferences_text = replace_idempotently(
        preferences_text,
        "if (deviceRam < 2048) return 656;",
        "if (deviceRam <= 2048) return 512;",
        str(launcher_preferences),
    )
    if "int targetSide = Tools.getTotalDeviceMemory(context) <= 2048 ? 480 : (isDevicePowerful ? 1080 : 720);" not in preferences_text:
        raise ValueError("Low-memory resolution target must override the powerful-device heuristic")
    if "int targetSide = isDevicePowerful ? 1080 :" in preferences_text:
        raise ValueError("Powerful-device heuristic incorrectly overrides the 2 GB RAM limit")
    if "if (deviceRam < 2048) return 656;" in preferences_text or "if (deviceRam <= 2048) return 656;" in preferences_text:
        raise ValueError("The low-memory RAM allocation was not reduced at the 2 GB boundary")
    if "if (deviceRam <= 2048) return 512;" not in preferences_text:
        raise ValueError("The 1.5–2 GB RAM tier must default to a 512 MB heap")
    launcher_preferences.write_text(preferences_text, encoding="utf-8")


    # Add the performance overlay settings to the existing control settings screen.
    pref_control = upstream / "app_pojavlauncher" / "src" / "main" / "res" / "xml" / "pref_control.xml"
    if not pref_control.is_file():
        raise FileNotFoundError(f"Expected control preferences are missing: {pref_control}")
    pref_control_text = pref_control.read_text(encoding="utf-8")
    overlay_preferences = """    <PreferenceCategory
        android:title="@string/mylauncher_performance_overlay_category">
        <androidx.preference.SwitchPreferenceCompat
            android:key="mylauncher_performance_overlay_enabled"
            android:defaultValue="false"
            android:title="@string/mylauncher_performance_overlay_enabled"
            android:summary="@string/mylauncher_performance_overlay_enabled_summary" />
        <androidx.preference.SwitchPreferenceCompat
            android:key="mylauncher_performance_overlay_fps"
            android:defaultValue="true"
            android:title="@string/mylauncher_performance_overlay_fps"
            android:summary="@string/mylauncher_performance_overlay_fps_summary" />
        <androidx.preference.SwitchPreferenceCompat
            android:key="mylauncher_performance_overlay_memory"
            android:defaultValue="true"
            android:title="@string/mylauncher_performance_overlay_memory"
            android:summary="@string/mylauncher_performance_overlay_memory_summary" />
    </PreferenceCategory>
"""
    if "mylauncher_performance_overlay_enabled" not in pref_control_text:
        if "</PreferenceScreen>" not in pref_control_text:
            raise ValueError("Could not append performance overlay switches to pref_control.xml")
        pref_control_text = pref_control_text.replace(
            "</PreferenceScreen>", overlay_preferences + "</PreferenceScreen>", 1
        )
    if (
        pref_control_text.count('android:key="mylauncher_performance_overlay_enabled"') != 1
        or pref_control_text.count('android:key="mylauncher_performance_overlay_fps"') != 1
        or pref_control_text.count('android:key="mylauncher_performance_overlay_memory"') != 1
    ):
        raise ValueError("Performance overlay preferences are missing or duplicated")
    pref_control.write_text(pref_control_text, encoding="utf-8")

    # Add a quick in-game master toggle without changing the existing menu actions.
    menu_arrays = upstream / "app_pojavlauncher" / "src" / "main" / "res" / "values" / "headings_array.xml"
    if not menu_arrays.is_file():
        raise FileNotFoundError(f"Expected menu array resources are missing: {menu_arrays}")
    arrays_text = menu_arrays.read_text(encoding="utf-8")
    menu_match = re.search(
        r'(<string-array\s+name="menu_ingame"[^>]*>)(?P<body>.*?)</string-array>',
        arrays_text,
        flags=re.DOTALL,
    )
    if not menu_match:
        raise ValueError("Could not find the in-game menu array for the performance toggle")
    menu_body = menu_match.group("body")
    toggle_item = "<item>@string/mylauncher_toggle_performance_overlay</item>"
    if toggle_item not in menu_body:
        menu_body = menu_body.rstrip() + "\n        " + toggle_item + "\n    "
        arrays_text = (
            arrays_text[:menu_match.start("body")]
            + menu_body
            + arrays_text[menu_match.end("body"):]
        )
    if arrays_text.count(toggle_item) != 1:
        raise ValueError("The in-game Performance Overlay toggle is missing or duplicated")
    menu_arrays.write_text(arrays_text, encoding="utf-8")

    # Count actual native renderer buffer swaps; do not substitute display refresh rate.
    egl_bridge = upstream / "app_pojavlauncher" / "src" / "main" / "jni" / "egl_bridge.c"
    if not egl_bridge.is_file():
        raise FileNotFoundError(f"Expected native EGL bridge is missing: {egl_bridge}")
    egl_text = egl_bridge.read_text(encoding="utf-8")
    counter_declaration = "static volatile uint32_t mylauncher_render_frame_count = 0;"
    if counter_declaration not in egl_text:
        abi_anchor = "#define ABI_COMPAT __attribute__((unused))"
        if abi_anchor not in egl_text:
            raise ValueError("Could not locate the EGL bridge insertion point for the native frame counter")
        egl_text = egl_text.replace(
            abi_anchor, abi_anchor + "\n\n" + counter_declaration, 1
        )
    counter_getter = """JNIEXPORT jlong JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_nativeGetRenderFrameCount(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    return (jlong) __atomic_load_n(&mylauncher_render_frame_count, __ATOMIC_RELAXED);
}
"""
    if "Java_net_kdt_pojavlaunch_utils_JREUtils_nativeGetRenderFrameCount" not in egl_text:
        swap_anchor = "EXTERNAL_API void pojavSwapBuffers() {"
        if swap_anchor not in egl_text:
            raise ValueError("Could not locate pojavSwapBuffers for the native FPS hook")
        egl_text = egl_text.replace(swap_anchor, counter_getter + "\n" + swap_anchor, 1)
    old_swap = """EXTERNAL_API void pojavSwapBuffers() {
    br_swap_buffers();
}"""
    new_swap = """EXTERNAL_API void pojavSwapBuffers() {
    br_swap_buffers();
    __atomic_add_fetch(&mylauncher_render_frame_count, 1, __ATOMIC_RELAXED);
}"""
    if old_swap in egl_text:
        egl_text = egl_text.replace(old_swap, new_swap, 1)
    elif new_swap not in egl_text:
        raise ValueError("Native render-frame counting was not installed safely")
    if (
        egl_text.count(counter_declaration) != 1
        or egl_text.count("Java_net_kdt_pojavlaunch_utils_JREUtils_nativeGetRenderFrameCount") != 1
        or new_swap not in egl_text
    ):
        raise ValueError("Native FPS counter definition or hook is missing or duplicated")
    egl_bridge.write_text(egl_text, encoding="utf-8")

    # Expose the native counter to Java through the already-loaded pojavexec library.
    jre_utils = upstream / "app_pojavlauncher" / "src" / "main" / "java" / "net" / "kdt" / "pojavlaunch" / "utils" / "JREUtils.java"
    if not jre_utils.is_file():
        raise FileNotFoundError(f"Expected JRE utility class is missing: {jre_utils}")
    jre_text = jre_utils.read_text(encoding="utf-8")
    getter_anchor = "public static native int[] renderAWTScreenFrame(/* Object canvas, int width, int height */);"
    getter_declaration = "@androidx.annotation.Keep\n    public static native long nativeGetRenderFrameCount();"
    if getter_declaration not in jre_text:
        if getter_anchor not in jre_text:
            raise ValueError("Could not locate JREUtils native declarations for the frame counter")
        jre_text = jre_text.replace(
            getter_anchor, getter_anchor + "\n    " + getter_declaration, 1
        )
    if jre_text.count("public static native long nativeGetRenderFrameCount();") != 1:
        raise ValueError("Java native FPS counter declaration is missing or duplicated")
    jre_utils.write_text(jre_text, encoding="utf-8")

    performance_overlay_source = r"""package net.kdt.pojavlaunch;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.JREUtils;

import java.util.Locale;

/** A low-overhead, draggable in-game FPS and process-memory display. */
public final class PerformanceOverlayView extends TextView {
    public static final String PREF_ENABLED = "mylauncher_performance_overlay_enabled";
    public static final String PREF_FPS = "mylauncher_performance_overlay_fps";
    public static final String PREF_MEMORY = "mylauncher_performance_overlay_memory";
    private static final String PREF_X_DP = "mylauncher_performance_overlay_x_dp";
    private static final String PREF_Y_DP = "mylauncher_performance_overlay_y_dp";
    private static final int DEFAULT_MARGIN_DP = 8;
    private static final long UPDATE_INTERVAL_MS = 1000L;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mUpdatesStarted;
    private long mPreviousFrames = -1L;
    private long mPreviousSampleMs;
    private int mCurrentFps = -1;
    private float mDownRawX;
    private float mDownRawY;
    private int mDownLeft;
    private int mDownTop;
    private boolean mNativeCounterUnavailable;

    private final Runnable mUpdateTask = new Runnable() {
        @Override
        public void run() {
            updateMetrics();
            if (mUpdatesStarted) {
                mHandler.postDelayed(this, UPDATE_INTERVAL_MS);
            }
        }
    };

    public PerformanceOverlayView(Context context) {
        super(context);
        setTextColor(Color.WHITE);
        setTextSize(12f);
        setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        setPadding(dp(9), dp(6), dp(9), dp(6));
        setIncludeFontPadding(true);
        setClickable(true);
        setFocusable(false);
        setLongClickable(false);
        setElevation(dp(6));

        GradientDrawable background = new GradientDrawable();
        background.setColor(0xD91A1D26);
        background.setCornerRadius(dp(8));
        background.setStroke(dp(1), 0x88FFFFFF);
        setBackground(background);
        setContentDescription("Draggable Minecraft performance overlay");
        setOnTouchListener(this::handleDrag);
        setVisibility(GONE);
        updateMetrics();
    }

    public void attachTo(ControlLayout parent) {
        if (getParent() != null) {
            return;
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        params.leftMargin = dp(DEFAULT_MARGIN_DP);
        params.topMargin = dp(DEFAULT_MARGIN_DP);
        parent.addView(this, params);
        post(this::restoreSavedPosition);
    }

    public void restoreSavedPosition() {
        if (!(getParent() instanceof ViewGroup)) {
            return;
        }
        post(() -> {
            ViewGroup parent = (ViewGroup) getParent();
            if (parent == null || !(getLayoutParams() instanceof FrameLayout.LayoutParams)) {
                return;
            }
            SharedPreferences prefs = preferences();
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
            params.gravity = Gravity.TOP | Gravity.START;
            params.leftMargin = dp(prefs.getInt(PREF_X_DP, DEFAULT_MARGIN_DP));
            params.topMargin = dp(prefs.getInt(PREF_Y_DP, DEFAULT_MARGIN_DP));
            clampToParent(parent, params);
            setLayoutParams(params);
        });
    }

    public void startUpdates() {
        if (mUpdatesStarted) {
            return;
        }
        mUpdatesStarted = true;
        mHandler.removeCallbacks(mUpdateTask);
        mUpdateTask.run();
    }

    public void stopUpdates() {
        mUpdatesStarted = false;
        mHandler.removeCallbacks(mUpdateTask);
    }

    public boolean toggleEnabled() {
        SharedPreferences prefs = preferences();
        boolean enabled = !prefs.getBoolean(PREF_ENABLED, false);
        prefs.edit().putBoolean(PREF_ENABLED, enabled).apply();
        updateMetrics();
        return enabled;
    }

    public void refreshNow() {
        updateMetrics();
    }

    private SharedPreferences preferences() {
        if (LauncherPreferences.DEFAULT_PREF != null) {
            return LauncherPreferences.DEFAULT_PREF;
        }
        return PreferenceManager.getDefaultSharedPreferences(getContext());
    }

    private void updateMetrics() {
        SharedPreferences prefs = preferences();
        boolean overlayEnabled = prefs.getBoolean(PREF_ENABLED, false);
        boolean showFps = prefs.getBoolean(PREF_FPS, true);
        boolean showMemory = prefs.getBoolean(PREF_MEMORY, true);
        boolean visible = overlayEnabled && (showFps || showMemory);
        setVisibility(visible ? VISIBLE : GONE);
        if (!visible) {
            return;
        }

        StringBuilder text = new StringBuilder();
        if (showFps) {
            long now = SystemClock.elapsedRealtime();
            try {
                long frames = JREUtils.nativeGetRenderFrameCount() & 0xffffffffL;
                if (mPreviousSampleMs > 0L && now > mPreviousSampleMs && mPreviousFrames >= 0L) {
                    long frameDelta = (frames - mPreviousFrames) & 0xffffffffL;
                    mCurrentFps = (int) Math.max(0L, Math.round(frameDelta * 1000.0 / (now - mPreviousSampleMs)));
                } else {
                    mCurrentFps = -1;
                }
                mPreviousFrames = frames;
                mPreviousSampleMs = now;
                text.append("FPS: ").append(mCurrentFps < 0 ? "--" : String.format(Locale.US, "%d", mCurrentFps));
            } catch (UnsatisfiedLinkError | RuntimeException error) {
                mNativeCounterUnavailable = true;
                text.append("FPS: N/A");
            }
            if (mNativeCounterUnavailable) {
                text.setLength(0);
                text.append("FPS: N/A");
            }
        }

        if (showMemory) {
            Debug.MemoryInfo memoryInfo = new Debug.MemoryInfo();
            Debug.getMemoryInfo(memoryInfo);
            long memoryMb = Math.max(0, memoryInfo.getTotalPss()) / 1024L;
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append("RAM: ").append(memoryMb).append(" MB");
        }

        setText(text.toString());
    }

    private boolean handleDrag(View view, MotionEvent event) {
        if (!(getParent() instanceof ViewGroup)
                || !(getLayoutParams() instanceof FrameLayout.LayoutParams)) {
            return false;
        }
        ViewGroup parent = (ViewGroup) getParent();
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownRawX = event.getRawX();
                mDownRawY = event.getRawY();
                mDownLeft = getLeft();
                mDownTop = getTop();
                return true;
            case MotionEvent.ACTION_MOVE:
                params.gravity = Gravity.TOP | Gravity.START;
                params.leftMargin = mDownLeft + Math.round(event.getRawX() - mDownRawX);
                params.topMargin = mDownTop + Math.round(event.getRawY() - mDownRawY);
                clampToParent(parent, params);
                setLayoutParams(params);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                savePosition();
                performClick();
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void savePosition() {
        float density = getResources().getDisplayMetrics().density;
        SharedPreferences prefs = preferences();
        prefs.edit()
                .putInt(PREF_X_DP, Math.max(0, Math.round(getLeft() / density)))
                .putInt(PREF_Y_DP, Math.max(0, Math.round(getTop() / density)))
                .apply();
    }

    private void clampToParent(ViewGroup parent, FrameLayout.LayoutParams params) {
        int maxLeft = Math.max(0, parent.getWidth() - getMeasuredWidth());
        int maxTop = Math.max(0, parent.getHeight() - getMeasuredHeight());
        params.leftMargin = Math.max(0, Math.min(params.leftMargin, maxLeft));
        params.topMargin = Math.max(0, Math.min(params.topMargin, maxTop));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
"""
    overlay_class = upstream / "app_pojavlauncher" / "src" / "main" / "java" / "net" / "kdt" / "pojavlaunch" / "PerformanceOverlayView.java"
    overlay_class.parent.mkdir(parents=True, exist_ok=True)
    overlay_class.write_text(performance_overlay_source, encoding="utf-8")

    # Place the overlay over the control layer, persist its drag position, and add quick on/off.
    main_activity = upstream / "app_pojavlauncher" / "src" / "main" / "java" / "net" / "kdt" / "pojavlaunch" / "MainActivity.java"
    if not main_activity.is_file():
        raise FileNotFoundError(f"Expected game activity is missing: {main_activity}")
    main_activity_text = main_activity.read_text(encoding="utf-8")
    field_anchor = "private FrameLayout contentFrame;"
    field_new = field_anchor + "\n    private PerformanceOverlayView mPerformanceOverlayView;"
    if "private PerformanceOverlayView mPerformanceOverlayView;" not in main_activity_text:
        if field_anchor not in main_activity_text:
            raise ValueError("Could not find the MainActivity field insertion point for the performance overlay")
        main_activity_text = main_activity_text.replace(field_anchor, field_new, 1)

    old_menu_case = "case 4: openCustomControls(); break;"
    new_menu_case = """case 4: openCustomControls(); break;
                    case 5: togglePerformanceOverlay(); break;"""
    if new_menu_case not in main_activity_text:
        if old_menu_case not in main_activity_text:
            raise ValueError("Could not add the in-game Performance Overlay on/off menu action")
        main_activity_text = main_activity_text.replace(old_menu_case, new_menu_case, 1)

    attach_anchor = "            loadControls();"
    attach_replacement = "            loadControls();\n            ensurePerformanceOverlay();"
    if attach_replacement not in main_activity_text:
        if attach_anchor not in main_activity_text:
            raise ValueError("Could not find the control-layout startup hook for the performance overlay")
        main_activity_text = main_activity_text.replace(attach_anchor, attach_replacement, 1)

    load_controls_anchor = "        mControlLayout.toggleControlVisible();"
    load_controls_replacement = load_controls_anchor + "\n        if (mPerformanceOverlayView != null) mPerformanceOverlayView.bringToFront();"
    if load_controls_replacement not in main_activity_text:
        if load_controls_anchor not in main_activity_text:
            raise ValueError("Could not keep the performance overlay in front of reloaded touch controls")
        main_activity_text = main_activity_text.replace(load_controls_anchor, load_controls_replacement, 1)

    on_resume_anchor = "        super.onResume();"
    on_resume_replacement = on_resume_anchor + "\n        if (mPerformanceOverlayView != null) mPerformanceOverlayView.startUpdates();"
    if on_resume_replacement not in main_activity_text:
        resume_method = re.search(r"public void onResume\(\)\s*\{(?P<body>.*?)\n    \}", main_activity_text, flags=re.DOTALL)
        if not resume_method or on_resume_anchor not in resume_method.group(0):
            raise ValueError("Could not connect performance overlay lifecycle to MainActivity.onResume")
        main_activity_text = main_activity_text[:resume_method.start()] + resume_method.group(0).replace(on_resume_anchor, on_resume_replacement, 1) + main_activity_text[resume_method.end():]

    pause_anchor = "        mGyroControl.disable();"
    pause_replacement = "        if (mPerformanceOverlayView != null) mPerformanceOverlayView.stopUpdates();\n" + pause_anchor
    if pause_replacement not in main_activity_text:
        if pause_anchor not in main_activity_text:
            raise ValueError("Could not connect performance overlay lifecycle to MainActivity.onPause")
        main_activity_text = main_activity_text.replace(pause_anchor, pause_replacement, 1)

    ensure_method = """    private void ensurePerformanceOverlay() {
        if (mPerformanceOverlayView == null) {
            mPerformanceOverlayView = new PerformanceOverlayView(this);
            mPerformanceOverlayView.attachTo(mControlLayout);
        }
        mPerformanceOverlayView.bringToFront();
        mPerformanceOverlayView.startUpdates();
    }

    private void togglePerformanceOverlay() {
        if (mPerformanceOverlayView == null) ensurePerformanceOverlay();
        boolean enabled = mPerformanceOverlayView.toggleEnabled();
        Toast.makeText(this, enabled
                ? R.string.mylauncher_performance_overlay_enabled_toast
                : R.string.mylauncher_performance_overlay_disabled_toast,
                Toast.LENGTH_SHORT).show();
    }

"""
    if "private void ensurePerformanceOverlay()" not in main_activity_text:
        menu_method_anchor = "    private void openCustomControls() {"
        if menu_method_anchor not in main_activity_text:
            raise ValueError("Could not find MainActivity insertion point for performance overlay actions")
        main_activity_text = main_activity_text.replace(menu_method_anchor, ensure_method + menu_method_anchor, 1)
    if (
        main_activity_text.count("private void ensurePerformanceOverlay()") != 1
        or main_activity_text.count("private void togglePerformanceOverlay()") != 1
        or "case 5: togglePerformanceOverlay(); break;" not in main_activity_text
        or "mPerformanceOverlayView.attachTo(mControlLayout);" not in main_activity_text
        or "mPerformanceOverlayView.startUpdates();" not in main_activity_text
        or "mPerformanceOverlayView.stopUpdates();" not in main_activity_text
    ):
        raise ValueError("MainActivity Performance Overlay controls or lifecycle wiring is incomplete")
    main_activity.write_text(main_activity_text, encoding="utf-8")

    validate_xml_files((
        strings,
        colors,
        login_layout,
        upstream / "app_pojavlauncher" / "src" / "main" / "res" / "layout" / "fragment_launcher.xml",
        upstream / "app_pojavlauncher" / "src" / "main" / "res" / "layout-land" / "fragment_launcher.xml",
        pref_control,
        menu_arrays,
        manifest,
        icon,
    ))

    print("myLauncher patch OK")
    print("  app label: myLauncher")
    print("  application ID: com.mf.mylauncher (debug adds .debug)")
    print("  storage/share authorities: customized")
    print("  low-memory defaults: 296/448/512 MB RAM tiers and ~480 px short-edge target for <=2 GB")


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(f"Usage: {Path(argv[0]).name} PATH_TO_AMETHYST_SOURCE", file=sys.stderr)
        return 2
    try:
        patch_upstream(Path(argv[1]).resolve())
    except (OSError, ValueError) as exc:
        print(f"myLauncher patch FAILED: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))