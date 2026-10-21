package com.voidcraft.launcher

import android.app.Activity
import android.content.Context
import android.view.WindowManager

/**
 * Minimal app-wide preferences shared by the independent VoidCraft screens.
 * This currently owns one real launcher-shell setting only.
 */
object LauncherPreferences {
    const val PREFS_NAME = "voidcraft_launcher_preferences"
    private const val KEY_KEEP_SCREEN_AWAKE = "keep_screen_awake"
    private const val KEY_ACCENT_THEME = "accent_theme"

    private val accentColors = mapOf(
        "violet" to android.graphics.Color.rgb(139, 124, 255),
        "cyan" to android.graphics.Color.rgb(55, 205, 231),
        "emerald" to android.graphics.Color.rgb(55, 210, 145),
        "amber" to android.graphics.Color.rgb(255, 174, 66),
        "rose" to android.graphics.Color.rgb(255, 105, 157)
    )

    fun setKeepScreenAwake(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_KEEP_SCREEN_AWAKE, enabled)
            .apply()
    }

    fun keepScreenAwake(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_KEEP_SCREEN_AWAKE, false)

    fun accentTheme(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACCENT_THEME, "violet")?.takeIf { it in accentColors } ?: "violet"

    fun accentColor(context: Context): Int = accentColors[accentTheme(context)]
        ?: android.graphics.Color.rgb(139, 124, 255)

    fun setAccentTheme(context: Context, theme: String): Boolean {
        if (theme !in accentColors) return false
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ACCENT_THEME, theme).commit()
    }

    fun applyWindowFlags(activity: Activity) {
        if (keepScreenAwake(activity)) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
