package com.voidcraft.launcher

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Independently authored VoidCraft app shell.
 *
 * This prototype intentionally does not start Minecraft: runtime, account,
 * download, and persistent instance services have not been connected yet.
 */
class MainActivity : Activity() {
    private val background = Color.rgb(13, 17, 28)
    private val panel = Color.rgb(25, 32, 48)
    private val accent = Color.rgb(120, 105, 255)
    private val primaryText = Color.rgb(244, 246, 255)
    private val secondaryText = Color.rgb(166, 176, 199)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = background
        window.decorView.systemUiVisibility = 0
        setContentView(buildDashboard())
    }

    private fun buildDashboard(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(this@MainActivity.background)
            isFillViewport = true
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(26), dp(20), dp(28))
        }
        scroll.addView(page)

        page.addView(label("VOIDCRAFT", 13, accent, true))
        page.addView(label("Your Minecraft, your way.", 27, primaryText, true).apply {
            setPadding(0, dp(7), 0, dp(5))
        })
        page.addView(label("Original launcher shell • developer preview", 13, secondaryText, false))
        page.addView(space(22))

        val statusCard = card()
        statusCard.addView(label("LAUNCH STATUS", 12, secondaryText, true))
        statusCard.addView(label("Runtime not connected yet", 19, primaryText, true).apply {
            setPadding(0, dp(8), 0, dp(5))
        })
        statusCard.addView(label(
            "This is the new independently authored app shell. It does not replace or delete your existing launcher or game data.",
            14, secondaryText, false
        ))
        statusCard.addView(space(12))
        statusCard.addView(actionButton("Runtime integration status") {
            notice("Minecraft runtime is not connected in this prototype.")
        })
        page.addView(statusCard)
        page.addView(space(14))

        page.addView(sectionCard(
            "Instances",
            "A dedicated place for game profiles, per-instance settings, and world-safe management.",
            "Open instances"
        ) { notice("Instance storage and migration are the next integration stage.") })
        page.addView(space(12))
        page.addView(sectionCard(
            "Performance",
            "Device diagnostics and reversible graphics presets, with FPS settings left user-configurable.",
            "Open performance"
        ) { notice("Performance controls will be connected after settings and runtime adapters exist.") })
        page.addView(space(12))
        page.addView(sectionCard(
            "Feature store",
            "Optional add-ons with compatibility checks, permissions, and safe install/uninstall flows.",
            "Open feature store"
        ) { notice("The feature-store service is not connected in this prototype.") })
        page.addView(space(22))

        page.addView(label("MIGRATION SAFETY", 12, accent, true))
        page.addView(label(
            "Existing launcher remains separate. No worlds, profiles, mods, or settings are migrated or modified by this shell.",
            14, secondaryText, false
        ).apply { setPadding(0, dp(7), 0, dp(12)) })
        page.addView(label("VoidCraft Launcher • 0.1.0-dev", 12, secondaryText, false))
        return scroll
    }

    private fun sectionCard(title: String, description: String, button: String, onClick: () -> Unit): View {
        val container = card()
        container.addView(label(title, 19, primaryText, true))
        container.addView(label(description, 14, secondaryText, false).apply {
            setPadding(0, dp(7), 0, dp(13))
        })
        container.addView(actionButton(button, onClick))
        return container
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(panel, 18)
        elevation = dp(2).toFloat()
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
            gravity = Gravity.START
            includeFontPadding = true
        }

    private fun actionButton(value: String, onClick: () -> Unit): Button = Button(this).apply {
        text = value
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = rounded(accent, 12)
        setPadding(dp(12), dp(5), dp(12), dp(5))
        setOnClickListener { onClick() }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun notice(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
