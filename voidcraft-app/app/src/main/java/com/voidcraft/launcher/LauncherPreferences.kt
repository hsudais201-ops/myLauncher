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

    fun setKeepScreenAwake(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_KEEP_SCREEN_AWAKE, enabled)
            .apply()
    }

    fun keepScreenAwake(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_KEEP_SCREEN_AWAKE, false)

    fun applyWindowFlags(activity: Activity) {
        if (keepScreenAwake(activity)) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
