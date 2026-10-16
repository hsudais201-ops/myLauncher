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
    if build_text.count("'shareProviderAuthority', 'com.mf.mylauncher.scoped.controlfolder'") != 1:
        raise ValueError("The release share-provider authority is missing or duplicated")
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
        "'shareProviderAuthority', 'com.mf.mylauncher.scoped.controlfolder'",
    )
    missing_values = [value for value in required_gradle_values if value not in build_text]
    if missing_values:
        raise ValueError(f"Gradle app labels or authorities are incomplete: {missing_values}")
    if "org.angelauramc.amethyst" in strings_text:
        raise ValueError("An upstream application authority remains in strings.xml")
    if not re.search(r'<string\s+name="main_add_account"', strings_text):
        raise ValueError("Required main_add_account string is missing from strings.xml")
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
    local_login.write_text(login_text, encoding="utf-8")

    login_layout = upstream / "app_pojavlauncher" / "src" / "main" / "res" / "layout" / "fragment_local_login.xml"
    if not login_layout.is_file():
        raise FileNotFoundError(f"Expected offline login layout is missing: {login_layout}")
    login_layout_text = login_layout.read_text(encoding="utf-8")
    login_layout_text = login_layout_text.replace('android:inputType="textEmailAddress"', 'android:inputType="textPersonName"')
    if (
        login_layout_text.count('android:inputType="textPersonName"') != 1
        or 'android:inputType="textEmailAddress"' in login_layout_text
    ):
        raise ValueError("Offline account username field was not configured exclusively as a username")
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

        if "mylauncher_account_button" not in layout_text:
            account_button = """            <com.kdt.mcgui.LauncherMenuButton
                android:id="@+id/mylauncher_account_button"
                style="@style/LauncherMenuButton.Universal"
                android:layout_width="match_parent"
                android:drawableStart="@android:drawable/ic_menu_myplaces"
                android:text="@string/main_add_account"
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
            or 'android:text="@string/main_add_account"' not in account_match.group(0)
            or 'app:layout_constraintTop_toBottomOf="@id/open_files_button"' not in account_match.group(0)
            or open_files_index < 0
            or open_files_index > account_match.start()
        ):
            raise ValueError(f"myLauncher account shortcut is incomplete in {layout_path}")
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
        if (
            layout_text.count('android:id="@+id/mylauncher_hero_title"') != 1
            or layout_text.count('android:id="@+id/mylauncher_hero_subtitle"') != 1
            or layout_text.count('android:id="@+id/mylauncher_account_button"') != 1
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

    main_menu = upstream / "app_pojavlauncher" / "src" / "main" / "java" / "net" / "kdt" / "pojavlaunch" / "fragments" / "MainMenuFragment.java"
    if not main_menu.is_file():
        raise FileNotFoundError(f"Expected main menu fragment is missing: {main_menu}")
    main_menu_text = main_menu.read_text(encoding="utf-8")
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
    if (
        main_menu_text.count(account_binding) != 1
        or main_menu_text.count(account_listener) != 1
        or "import android.widget.Button;" not in main_menu_text
        or "import net.kdt.pojavlaunch.extra.ExtraConstants;" not in main_menu_text
        or "import net.kdt.pojavlaunch.extra.ExtraCore;" not in main_menu_text
    ):
        raise ValueError("The account shortcut binding, handler, or required Java imports are incomplete")
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
        or '@mipmap/ic_launcher' in manifest_text
        or '@mipmap/ic_launcher_round' in manifest_text
    ):
        raise ValueError("Could not completely apply the myLauncher app icon to the Android manifest")
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
    if (
        "hasNoOnlineProfileDialog(requireActivity()" in auth_text
        or "import static net.kdt.pojavlaunch.Tools.hasNoOnlineProfileDialog;" in auth_text
        or auth_text.count(new_local_auth) != 1
    ):
        raise ValueError("Offline account selection is still gated or its handler is incomplete")
    auth_selector.write_text(auth_text, encoding="utf-8")

    launcher_preferences = upstream / "app_pojavlauncher" / "src" / "main" / "java" / "net" / "kdt" / "pojavlaunch" / "prefs" / "LauncherPreferences.java"
    if not launcher_preferences.is_file():
        raise FileNotFoundError(f"Expected upstream file is missing: {launcher_preferences}")
    preferences_text = launcher_preferences.read_text(encoding="utf-8")
    preferences_text = replace_idempotently(
        preferences_text,
        "int targetSide = isDevicePowerful ? 1080 : 720;",
        "int targetSide = isDevicePowerful ? 1080 : (Tools.getTotalDeviceMemory(context) <= 2048 ? 480 : 720);",
        str(launcher_preferences),
    )
    preferences_text = replace_idempotently(
        preferences_text,
        "if (deviceRam < 2048) return 656;",
        "if (deviceRam <= 2048) return 656;",
        str(launcher_preferences),
    )
    if "int targetSide = isDevicePowerful ? 1080 : 720;" in preferences_text:
        raise ValueError("Low-memory resolution target was not applied")
    if "if (deviceRam < 2048) return 656;" in preferences_text:
        raise ValueError("The 2 GB RAM allocation boundary was not corrected")
    launcher_preferences.write_text(preferences_text, encoding="utf-8")

    validate_xml_files((
        strings,
        colors,
        login_layout,
        upstream / "app_pojavlauncher" / "src" / "main" / "res" / "layout" / "fragment_launcher.xml",
        upstream / "app_pojavlauncher" / "src" / "main" / "res" / "layout-land" / "fragment_launcher.xml",
        manifest,
        icon,
    ))

    print("myLauncher patch OK")
    print("  app label: myLauncher")
    print("  application ID: com.mf.mylauncher (debug adds .debug)")
    print("  storage/share authorities: customized")
    print("  low-memory defaults: conservative upstream RAM tiers and ~480 px short-edge target for <=2 GB")


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