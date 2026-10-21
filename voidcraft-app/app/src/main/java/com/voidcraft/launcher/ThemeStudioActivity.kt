package com.voidcraft.launcher

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** First-party theme editor. Choices are persisted locally and can be changed at any time. */
class ThemeStudioActivity : Activity() {
    private val background = Color.rgb(10, 14, 24)
    private val surface = Color.rgb(18, 25, 40)
    private val panel = Color.rgb(23, 33, 52)
    private val primaryText = Color.rgb(243, 246, 255)
    private val secondaryText = Color.rgb(157, 174, 199)
    private val border = Color.rgb(42, 58, 85)
    private lateinit var previewAccent: View
    private lateinit var selectedLabel: TextView

    private data class Theme(val id: String, val name: String, val color: Int)
    private val themes = listOf(
        Theme("violet", "Void Violet", Color.rgb(139, 124, 255)),
        Theme("cyan", "Glacier Cyan", Color.rgb(55, 205, 231)),
        Theme("emerald", "Emerald", Color.rgb(55, 210, 145)),
        Theme("amber", "Solar Amber", Color.rgb(255, 174, 66)),
        Theme("rose", "Neon Rose", Color.rgb(255, 105, 157))
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = surface
        LauncherPreferences.applyWindowFlags(this)

        val scroll = ScrollView(this).apply { setBackgroundColor(this@ThemeStudioActivity.background) }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(28))
        }
        scroll.addView(page)
        page.addView(actionText("‹   BACK", 14, primaryText) { finish() })
        page.addView(space(20))
        page.addView(text("Theme Studio", 28, primaryText, true))
        page.addView(text(
            "Choose an accent color for VoidCraft. Your choice is saved on this device and can be changed or reset at any time.",
            14, secondaryText, false
        ).apply { setPadding(0, dp(7), 0, dp(18)) })

        val preview = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(surface, 18, border)
        }
        preview.addView(text("LIVE PREVIEW", 10, secondaryText, true))
        preview.addView(space(12))
        previewAccent = View(this).apply {
            background = rounded(LauncherPreferences.accentColor(this@ThemeStudioActivity), 12)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8))
        }
        preview.addView(previewAccent)
        preview.addView(space(12))
        preview.addView(text("VOIDCRAFT  •  Java Edition", 16, primaryText, true))
        selectedLabel = text("", 12, secondaryText, false).apply { setPadding(0, dp(5), 0, 0) }
        preview.addView(selectedLabel)
        page.addView(preview)
        page.addView(space(18))
        page.addView(text("ACCENT PRESETS", 11, secondaryText, true))
        page.addView(space(10))

        themes.forEach { theme ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(13), dp(12), dp(13), dp(12))
                background = rounded(surface, 14, border)
                isClickable = true
                isFocusable = true
                contentDescription = "Apply ${theme.name} theme"
                setOnClickListener { applyTheme(theme) }
            }
            row.addView(View(this).apply {
                background = rounded(theme.color, 30)
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
            })
            row.addView(text(theme.name, 15, primaryText, true).apply {
                setPadding(dp(12), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(text("APPLY  ›", 10, theme.color, true))
            page.addView(row)
            page.addView(space(8))
        }

        page.addView(space(8))
        page.addView(actionText("RESET TO VOID VIOLET", 12, primaryText) {
            applyTheme(themes.first())
        }.apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(panel, 12, border)
        })
        page.addView(space(12))
        page.addView(text(
            "Theme changes affect accent elements in the home screen, settings, and Feature Store when those screens are reopened. Minecraft graphics and FPS are not changed.",
            12, secondaryText, false
        ))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(this@ThemeStudioActivity.background)
        }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(LauncherNavigationBar.create(this, "Store"))
        setContentView(root)
        updatePreview()
    }

    private fun applyTheme(theme: Theme) {
        LauncherPreferences.setAccentTheme(this, theme.id)
        previewAccent.background = rounded(theme.color, 12)
        updatePreview()
        Toast.makeText(this, "${theme.name} applied. Reopen other screens to refresh their accents.", Toast.LENGTH_SHORT).show()
    }

    private fun updatePreview() {
        val id = LauncherPreferences.accentTheme(this)
        val theme = themes.firstOrNull { it.id == id } ?: themes.first()
        selectedLabel.text = "Selected: ${theme.name}"
        selectedLabel.setTextColor(theme.color)
        previewAccent.background = rounded(theme.color, 12)
    }

    private fun actionText(value: String, size: Int, color: Int, onClick: () -> Unit) =
        text(value, size, color, true).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun text(value: String, size: Int, color: Int, bold: Boolean) =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.BOLD)
                else Typeface.create("sans-serif", Typeface.NORMAL)
            gravity = Gravity.START
            includeFontPadding = true
        }

    private fun rounded(color: Int, radius: Int, stroke: Int? = null) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
