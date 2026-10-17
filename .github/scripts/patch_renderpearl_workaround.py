#!/usr/bin/env python3
"""Apply a guarded MobileGlues workaround for Minecraft 26.3 RenderPearl startup."""
from __future__ import annotations

import sys
from pathlib import Path


MAIN_ACTIVITY = Path(
    "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/MainActivity.java"
)
LAUNCHER_PREFERENCES = Path(
    "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/prefs/LauncherPreferences.java"
)

OLD_CALL = "LauncherPreferences.writeMGRendererSettings();"
NEW_CALL = "LauncherPreferences.writeMGRendererSettings(versionId);"

OLD_SIGNATURE = "    public static void writeMGRendererSettings() throws IOException {"
NEW_SIGNATURE = r'''    /**
     * Minecraft 26.3 after snapshot 1 introduced RenderPearl startup behavior
     * that can fail MobileGlues' OpenGL backend when GL error filtering is
     * disabled. Use partial filtering only for affected versions and only when
     * the user left the preference at its default (0). Explicit settings remain
     * untouched. This is a compatibility workaround, not a renderer guarantee.
     */
    private static boolean isRenderPearlWorkaroundTarget(String versionId) {
        if (versionId == null) return false;
        String id = versionId.trim();
        if (!id.matches("(?i)^26\\.3(?:$|[-+].*)")) return false;
        // Amethyst documents 26.3-snapshot-1 as its last supported 26.3 build.
        return !id.matches("(?i)^26\\.3-snapshot-1(?:$|[-+].*)");
    }

    public static void writeMGRendererSettings(String versionId) throws IOException {'''

OLD_CONFIG_LINE = 'MGConfigJson.put("enableNoError", Integer.parseInt(DEFAULT_PREF.getString("mg_renderer_setting_errorSetting", "0")));'
NEW_CONFIG_BLOCK = '''int errorSetting = Integer.parseInt(DEFAULT_PREF.getString("mg_renderer_setting_errorSetting", "0"));
        if (isRenderPearlWorkaroundTarget(versionId) && errorSetting == 0) {
            // MobileGlues NoErrorConfig.Level1 = 2 (partial GL error filtering).
            // This mitigates the known default-filtering crash; it cannot supply
            // missing Vulkan capabilities or fix an upstream EGL/RenderPearl bug.
            errorSetting = 2;
            Log.i("myLauncher", "Applying MobileGlues partial error filtering workaround for Minecraft " + versionId);
        }
        MGConfigJson.put("enableNoError", errorSetting);'''


def replace_once_or_already_patched(text: str, old: str, new: str, label: str) -> str:
    """Replace one anchor, accept a complete prior patch, and reject ambiguous upstream changes."""
    count = text.count(old)
    if count == 1:
        return text.replace(old, new, 1)
    if count == 0 and new in text:
        return text
    raise ValueError(f"Expected exactly one patch anchor in {label}; found {count}: {old!r}")


def patch_upstream(upstream: Path) -> None:
    activity = upstream / MAIN_ACTIVITY
    preferences = upstream / LAUNCHER_PREFERENCES
    for path in (activity, preferences):
        if not path.is_file():
            raise FileNotFoundError(f"Expected upstream file is missing: {path}")

    # Validate and construct both replacements in memory before writing either file.
    # This avoids leaving a half-applied patch if a pinned upstream anchor changes.
    activity_text = activity.read_text(encoding="utf-8")
    preferences_text = preferences.read_text(encoding="utf-8")
    patched_activity = replace_once_or_already_patched(
        activity_text, OLD_CALL, NEW_CALL, str(activity)
    )
    patched_preferences = replace_once_or_already_patched(
        preferences_text, OLD_SIGNATURE, NEW_SIGNATURE, str(preferences)
    )
    patched_preferences = replace_once_or_already_patched(
        patched_preferences, OLD_CONFIG_LINE, NEW_CONFIG_BLOCK, str(preferences)
    )

    required_anchors = (
        "isRenderPearlWorkaroundTarget(versionId) && errorSetting == 0",
        'MGConfigJson.put("enableNoError", errorSetting);',
        'errorSetting = 2;',
        'Log.i("myLauncher", "Applying MobileGlues partial error filtering workaround for Minecraft " + versionId);',
        r'if (!id.matches("(?i)^26\\.3(?:$|[-+].*)")) return false;',
        r'return !id.matches("(?i)^26\\.3-snapshot-1(?:$|[-+].*)");',
    )
    missing = [anchor for anchor in required_anchors if anchor not in patched_preferences]
    if missing:
        raise ValueError(f"RenderPearl workaround is incomplete: {missing}")
    if NEW_CALL not in patched_activity:
        raise ValueError("Selected Minecraft version is not passed to MobileGlues configuration")
    
    activity.write_text(patched_activity, encoding="utf-8")
    preferences.write_text(patched_preferences, encoding="utf-8")

    print("myLauncher RenderPearl workaround patch OK")
    print("  Minecraft 26.3-snapshot-1: unchanged")
    print("  Minecraft 26.3 stable and later snapshots: default error filtering becomes partial")
    print("  Explicit MobileGlues error-filter preferences: preserved")
    print("  Other Minecraft versions: unchanged")


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(f"Usage: {Path(argv[0]).name} PATH_TO_AMETHYST_SOURCE", file=sys.stderr)
        return 2
    try:
        patch_upstream(Path(argv[1]).resolve())
    except (OSError, ValueError) as exc:
        print(f"myLauncher RenderPearl patch FAILED: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
