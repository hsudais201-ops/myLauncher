package com.voidcraft.launcher

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Feature Store UI preview. No category taxonomy is used. Install/uninstall
 * is intentionally not simulated until signed manifests, permission handling,
 * compatibility validation, and rollback are connected.
 */
class FeatureStoreActivity : Activity() {
    private val background = Color.rgb(10, 14, 24)
    private val surface = Color.rgb(18, 25, 40)
    private val panel = Color.rgb(23, 33, 52)
    private val accent = Color.rgb(139, 124, 255)
    private val mint = Color.rgb(51, 216, 190)
    private val primaryText = Color.rgb(243, 246, 255)
    private val secondaryText = Color.rgb(157, 174, 199)
    private val border = Color.rgb(42, 58, 85)

    private data class Feature(
        val name: String,
        val description: String,
        val state: String,
        val icon: String
    )

    private val features = listOf(
        Feature("Smart Performance Advisor",
            "Read device diagnostics and suggest reversible, device-aware settings without imposing an FPS cap.",
            "Planned", "↗"),
        Feature("Minecraft Server Status",
            "Track saved servers, online state, MOTD, player count, and server icon.",
            "Planned", "◉"),
        Feature("Friends & Chat",
            "A separate social page for profiles, friend requests, and private messages.",
            "Planned", "✉"),
        Feature("Theme Studio",
            "Customize VoidCraft colors, backgrounds, and interface accents.",
            "Planned", "✦"),
        Feature("Crash Report Center",
            "Read crash logs, summarize likely causes, and prepare a shareable bug report.",
            "Planned", "⚑"),
        Feature("Screenshot Gallery",
            "Browse and organize Minecraft screenshots without leaving the launcher.",
            "Planned", "▧"),
        Feature("World Backup Scheduler",
            "Create scheduled backups with integrity checks and restore previews.",
            "Planned", "◫"),
        Feature("Mod Compatibility Checker",
            "Inspect mod metadata for Minecraft version and loader compatibility conflicts.",
            "Planned", "◇"),
        Feature("Shader Preset Manager",
            "Save renderer-aware shader presets and switch between named configurations.",
            "Planned", "☼"),
        Feature("Startup Diagnostics",
            "Review runtime, storage, memory, and native-library readiness before launch.",
            "Planned", "✓"),
        Feature("Community Feature Upload",
            "Prepare user-submitted add-ons for permission review and compatibility validation.",
            "Review required", "⇧")
    )

    private lateinit var searchField: EditText
    private lateinit var featureList: LinearLayout
    private lateinit var countLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = surface
        LauncherPreferences.applyWindowFlags(this)

        val scroll = ScrollView(this).apply { setBackgroundColor(background) }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(26))
        }
        scroll.addView(page)

        page.addView(header())
        page.addView(space(22))
        page.addView(text("Feature Store", 27, primaryText, true))
        page.addView(text(
            "Optional add-ons for VoidCraft. Core engine, Java runtime, renderer, and essential launcher components are not store add-ons.",
            14, secondaryText, false
        ).apply { setPadding(0, dp(6), 0, 0) })
        page.addView(space(15))
        page.addView(safetyBanner())
        page.addView(space(15))

        searchField = EditText(this).apply {
            hint = "Search features"
            textSize = 14f
            singleLine = true
            setTextColor(primaryText)
            setHintTextColor(secondaryText)
            background = rounded(panel, 13, border)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            contentDescription = "Search optional features"
        }
        page.addView(searchField)
        page.addView(space(10))
        countLabel = text("", 12, secondaryText, false)
        page.addView(countLabel)
        page.addView(space(8))
        featureList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        page.addView(featureList)
        page.addView(space(18))
        page.addView(text(
            "Install and uninstall controls will be enabled only after signed feature manifests, permission prompts, compatibility checks, and rollback tests are implemented.",
            12, secondaryText, false
        ))
        setContentView(scroll)

        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = renderFeatures()
        })
        renderFeatures()
    }

    private fun renderFeatures() {
        featureList.removeAllViews()
        val query = searchField.text.toString().trim().lowercase()
        val visible = features.filter {
            it.name.lowercase().contains(query) || it.description.lowercase().contains(query)
        }
        countLabel.text = "${visible.size} features • browse without categories"
        if (visible.isEmpty()) {
            featureList.addView(text("No features match that search.", 14, secondaryText, false)
                .apply { setPadding(dp(10), dp(18), dp(10), dp(18)) })
            return
        }
        visible.forEach { feature ->
            featureList.addView(featureCard(feature))
            featureList.addView(space(10))
        }
    }

    private fun featureCard(feature: Feature): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(15), dp(15), dp(15))
            background = rounded(surface, 18, border)
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon = TextView(this).apply {
            text = feature.icon
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(accent)
            background = rounded(panel, 14)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }
        top.addView(icon)
        val titleStack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        titleStack.addView(text(feature.name, 15, primaryText, true))
        titleStack.addView(text(feature.state.uppercase(), 9,
            if (feature.state == "Review required") Color.rgb(255, 190, 104) else mint, true
        ).apply {
            letterSpacing = 0.05f
            setPadding(0, dp(4), 0, 0)
        })
        top.addView(titleStack, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(top)
        card.addView(text(feature.description, 12, secondaryText, false).apply {
            setPadding(0, dp(12), 0, dp(12))
        })
        val button = TextView(this).apply {
            text = "VIEW DETAILS  ›"
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(primaryText)
            gravity = Gravity.CENTER
            background = rounded(panel, 11, border)
            setPadding(dp(12), dp(11), dp(12), dp(11))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                Toast.makeText(
                    this@FeatureStoreActivity,
                    "${feature.name}: UI preview only. Secure installation is not connected yet.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        card.addView(button)
        return card
    }

    private fun header(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = text("‹", 32, primaryText, true).apply {
            gravity = Gravity.CENTER
            background = rounded(panel, 14, border)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
            contentDescription = "Back"
        }
        row.addView(back)
        val title = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        title.addView(text("VOIDCRAFT", 15, primaryText, true).apply {
            letterSpacing = 0.07f
        })
        title.addView(text("OPTIONAL ADD-ONS", 10, secondaryText, true).apply {
            letterSpacing = 0.06f
            setPadding(0, dp(3), 0, 0)
        })
        row.addView(title)
        return row
    }

    private fun safetyBanner(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(dp(13), dp(13), dp(13), dp(13))
            background = rounded(Color.rgb(19, 44, 52), 15, Color.rgb(36, 101, 107))
        }
        box.addView(text("✓", 20, mint, true).apply {
            setPadding(0, 0, dp(10), 0)
        })
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(text("Safety comes first", 14, primaryText, true))
        texts.addView(text(
            "Features must declare permissions and meet compatibility checks before installation. Community uploads are untrusted until reviewed.",
            12, secondaryText, false
        ).apply { setPadding(0, dp(4), 0, 0) })
        box.addView(texts)
        return box
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
