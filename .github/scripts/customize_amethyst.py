#!/usr/bin/env python3
"""Apply deterministic myLauncher branding/package patches to Amethyst-Android."""
from __future__ import annotations

import re
import sys
from pathlib import Path


def replace_idempotently(text: str, old: str, new: str, label: str) -> str:
    """Replace a token, allow repeat runs, and fail on unexpected upstream changes."""
    if old in text:
        return text.replace(old, new)
    if new in text:
        return text
    raise ValueError(f"Could not find expected token in {label}: {old!r} or {new!r}")


def patch_upstream(upstream: Path) -> None:
    gradle = upstream / "app_pojavlauncher" / "build.gradle"
    strings = upstream / "app_pojavlauncher" / "src" / "main" / "res" / "values" / "strings.xml"
    settings = upstream / "settings.gradle"
    for path in (gradle, strings, settings):
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
    if '"app_short_name", "myLauncher (Debug)"' not in build_text or '"app_short_name", "myLauncher"' not in build_text:
        raise ValueError("Gradle app_short_name branding was not applied")
    if "org.angelauramc.amethyst" in strings_text:
        raise ValueError("An upstream application authority remains in strings.xml")
    strings.write_text(strings_text, encoding="utf-8")

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
    login_text = login_text.replace("import static net.kdt.pojavlaunch.Tools.hasOnlineProfile;\n\n", "")
    login_guard = """        // This is overkill but meh
        if (!hasOnlineProfile()){
            Tools.swapFragment(requireActivity(), MainMenuFragment.class, MainMenuFragment.TAG, null);
        }
"""
    if login_guard in login_text:
        login_text = login_text.replace(login_guard, "        // Offline accounts are supported; do not require an online profile to add one.\n")
    elif "if (!hasOnlineProfile())" in login_text:
        raise ValueError("Unrecognized offline-login guard; refusing an unsafe source edit")
    if "if (!hasOnlineProfile())" in login_text:
        raise ValueError("Offline account screen still blocks users without an online profile")
    local_login.write_text(login_text, encoding="utf-8")

    login_layout = upstream / "app_pojavlauncher" / "src" / "main" / "res" / "layout" / "fragment_local_login.xml"
    if not login_layout.is_file():
        raise FileNotFoundError(f"Expected offline login layout is missing: {login_layout}")
    login_layout_text = login_layout.read_text(encoding="utf-8")
    login_layout_text = login_layout_text.replace('android:inputType="textEmailAddress"', 'android:inputType="textPersonName"')
    if 'android:inputType="textPersonName"' not in login_layout_text:
        raise ValueError("Offline account username field was not configured for usernames")
    login_layout.write_text(login_layout_text, encoding="utf-8")

    # Add a responsive branded header without introducing heavyweight UI dependencies.
    header = """            <TextView
                android:id="@+id/mylauncher_hero_title"
                android:layout_width="match_parent"
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
                android:layout_width="match_parent"
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
            pattern = re.compile(r'(<com\.kdt\.mcgui\.LauncherMenuButton\s+android:id="@\+id/' + button_id + r'".*?/\s*)', re.DOTALL)
            match = pattern.search(layout_text)
            if not match:
                raise ValueError(f"Could not find {button_id} in {layout_path}")
            block = match.group(1).replace(
                'app:layout_constraintTop_toTopOf="parent"',
                'app:layout_constraintTop_toBottomOf="@id/mylauncher_hero_subtitle"'
            )
            layout_text = layout_text[:match.start(1)] + block + layout_text[match.end(1):]
        layout_text = re.sub(
            r'(android:id="@\+id/social_divider".*?app:layout_constraintTop_toTopOf=)"parent"',
            r'\1"@id/news_button"',
            layout_text,
            count=1,
            flags=re.DOTALL
        )
        layout_path.write_text(layout_text, encoding="utf-8")

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

    print("myLauncher patch OK")
    print("  app label: myLauncher")
    print("  application ID: com.mf.mylauncher (debug adds .debug)")
    print("  storage/share authorities: customized")
    print("  low-memory defaults: 656 MB at <=2 GB; ~480 px short-edge render target")


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