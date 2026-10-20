package com.voidcraft.launcher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Shared bottom navigation for the independent VoidCraft UI.
 */
object LauncherNavigationBar {
    private val surface = Color.rgb(18, 25, 40)
    private val accent = Color.rgb(139, 124, 255)
    private val secondaryText = Color.rgb(157, 174, 199)
    private val border = Color.rgb(42, 58, 85)

    fun create(
        activity: Activity,
        selected: String,
        onHomeClick: (() -> Unit)? = null
    ): View {
        val wrapper = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surface)
        }
        wrapper.addView(View(activity).apply { setBackgroundColor(border) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 1)))
        val nav = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(activity, 5), dp(activity, 7), dp(activity, 5), dp(activity, 6))
        }
        val items = listOf(
            Triple("⌂", "Home", MainActivity::class.java),
            Triple("▦", "Instances", InstanceListActivity::class.java),
            Triple("⇩", "Versions", VersionBrowserActivity::class.java),
            Triple("◎", "Account", AccountActivity::class.java),
            Triple("◇", "Store", FeatureStoreActivity::class.java),
            Triple("⚙", "Settings", SettingsActivity::class.java)
        )
        items.forEach { (glyph, label, target) ->
            val isSelected = label == selected
            val item = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(activity, 2), dp(activity, 2), dp(activity, 2), dp(activity, 2))
                isClickable = true
                isFocusable = true
                contentDescription = label
                setOnClickListener {
                    if (isSelected) {
                        if (label == "Home") onHomeClick?.invoke()
                    } else if (label == "Home" && onHomeClick != null && activity is MainActivity) {
                        onHomeClick.invoke()
                    } else {
                        activity.startActivity(
                            Intent(activity, target).addFlags(
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            )
                        )
                    }
                }
            }
            item.addView(TextView(activity).apply {
                text = glyph
                textSize = 21f
                gravity = Gravity.CENTER
                setTextColor(if (isSelected) accent else secondaryText)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            })
            item.addView(TextView(activity).apply {
                text = label
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(if (isSelected) accent else secondaryText)
                typeface = Typeface.create("sans-serif", if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                setPadding(0, dp(activity, 2), 0, 0)
            })
            nav.addView(item, LinearLayout.LayoutParams(0, dp(activity, 49), 1f))
        }
        wrapper.addView(nav)
        return wrapper
    }

    private fun dp(activity: Activity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
