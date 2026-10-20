package com.voidcraft.launcher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * VoidCraft settings screen. Device/runtime options that need a connected
 * Minecraft runtime are presented as unavailable instead of pretending to work.
 */
class SettingsActivity : Activity() {
    private val background = Color.rgb(10, 14, 24)
    private val surface = Color.rgb(18, 25, 40)
    private val panel = Color.rgb(23, 33, 52)
    private val accent = Color.rgb(139, 124, 255)
    private val mint = Color.rgb(51, 216, 190)
    private val primaryText = Color.rgb(243, 246, 255)
    private val secondaryText = Color.rgb(157, 174, 199)
    private val border = Color.rgb(42, 58, 85)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = surface
        LauncherPreferences.applyWindowFlags(this)

        val scroll = ScrollView(this).apply { setBackgroundColor(background) }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(28))
        }
        scroll.addView(page)

        page.addView(header())
        page.addView(space(22))
        page.addView(text("Make VoidCraft yours", 27, primaryText, true))
        page.addView(text(
            "Configure the launcher shell. Runtime-specific controls become available when the game runtime is integrated.",
            14, secondaryText, false
        ).apply { setPadding(0, dp(6), 0, 0) })
        page.addView(space(20))

        page.addView(sectionTitle("APP EXPERIENCE"))
        page.addView(space(9))
        page.addView(toggleCard(
            "Keep screen awake",
            "Prevent the display from sleeping while you use VoidCraft.",
            "keep_screen_awake",
            false
        ) { enabled ->
            LauncherPreferences.setKeepScreenAwake(this, enabled)
            LauncherPreferences.applyWindowFlags(this)
        })
        page.addView(space(10))
        page.addView(infoCard(
            "Appearance",
            "Night-sky surfaces, violet accents, cyan highlights",
            "VoidCraft theme"
        ) { notice("Theme customization is planned for a future settings update.") })
        page.addView(space(20))

        page.addView(sectionTitle("MINECRAFT")
        )
        page.addView(space(9))
        page.addView(infoCard(
            "Frame rate",
            "VoidCraft applies no FPS cap. Actual FPS depends on Minecraft, the renderer, and your device.",
            "Uncapped by launcher"
        ) { notice("No launcher-imposed FPS cap is configured in this prototype.") })
        page.addView(space(10))
        page.addView(infoCard(
            "Memory allocation",
            "Per-instance RAM settings need to be connected to the Java launch service.",
            "Not connected"
        ) { notice("RAM controls are not connected to a working runtime yet.") })
        page.addView(space(10))
        page.addView(infoCard(
            "Renderer and graphics",
            "Renderer selection, resolution, and graphics presets will be applied after runtime integration.",
            "Not connected"
        ) { notice("Renderer controls are not connected to a working runtime yet.") })
        page.addView(space(20))

        page.addView(sectionTitle("SECURITY & DATA"))
        page.addView(space(9))
        page.addView(infoCard(
            "Feature Store safety",
            "Add-ons should declare permissions, pass compatibility checks, and use a reversible install/uninstall flow.",
            "Policy preview"
        ) { startActivity(Intent(this, FeatureStoreActivity::class.java)) })
        page.addView(space(10))
        page.addView(infoCard(
            "User data protection",
            "This independent app keeps its catalog separate; it does not migrate or delete existing worlds or game files.",
            "Protected"
        ) { notice("Profile removal affects only VoidCraft profile metadata.") })
        page.addView(space(20))

        page.addView(sectionTitle("ABOUT"))
        page.addView(space(9))
        page.addView(infoCard(
            "VoidCraft Launcher",
            "Independent interface prototype • 0.1.0-dev",
            "Build information"
        ) { notice("This is a development build. Minecraft launch and device compatibility have not yet been validated.") })
        page.addView(space(14))
        page.addView(text(
            "Settings marked “Not connected” are informational previews, not active Minecraft controls.",
            12, secondaryText, false
        ))

        setContentView(scroll)
    }

    private fun header(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(actionText("‹", 32, primaryText) { finish() }.apply {
            gravity = Gravity.CENTER
            background = rounded(panel, 14, border)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        })
        val stack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        stack.addView(text("SETTINGS", 15, primaryText, true).apply {
            letterSpacing = 0.08f
        })
        stack.addView(text("Launcher preferences", 11, secondaryText, false).apply {
            setPadding(0, dp(3), 0, 0)
        })
        row.addView(stack)
        return row
    }

    private fun sectionTitle(title: String) = text(title, 11, secondaryText, true).apply {
        letterSpacing = 0.08f
    }

    private fun toggleCard(
        title: String,
        description: String,
        key: String,
        defaultValue: Boolean,
        onChanged: (Boolean) -> Unit
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(15), dp(14), dp(12), dp(14))
            background = rounded(surface, 17, border)
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(text(title, 15, primaryText, true))
        texts.addView(text(description, 12, secondaryText, false).apply {
            setPadding(0, dp(5), dp(8), 0)
        })
        card.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val pref = getSharedPreferences(LauncherPreferences.PREFS_NAME, MODE_PRIVATE)
        val toggle = Switch(this).apply {
            isChecked = pref.getBoolean(key, defaultValue)
            contentDescription = title
            setOnCheckedChangeListener { _, checked ->
                onChanged(checked)
            }
        }
        card.addView(toggle)
        return card
    }

    private fun infoCard(title: String, description: String, status: String, onClick: () -> Unit): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = rounded(surface, 17, border)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(text(title, 15, primaryText, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(text(status, 10, accent, true).apply {
            gravity = Gravity.CENTER
            background = rounded(panel, 30)
            setPadding(dp(8), dp(6), dp(8), dp(6))
        })
        card.addView(top)
        card.addView(text(description, 12, secondaryText, false).apply {
            setPadding(0, dp(7), 0, 0)
        })
        return card
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

    private fun notice(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
