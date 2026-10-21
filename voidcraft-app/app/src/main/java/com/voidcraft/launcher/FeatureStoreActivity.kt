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
 * Category-free Feature Store catalog. Only reviewed built-in features that
 * have real implementations may be installed; remote/community code is never
 * executed by this UI or by the local registry.
 */
class FeatureStoreActivity : Activity() {
    private val background = Color.rgb(10, 14, 24)
    private val surface = Color.rgb(18, 25, 40)
    private val panel = Color.rgb(23, 33, 52)
    private val accent by lazy { LauncherPreferences.accentColor(this) }
    private val mint = Color.rgb(51, 216, 190)
    private val primaryText = Color.rgb(243, 246, 255)
    private val secondaryText = Color.rgb(157, 174, 199)
    private val border = Color.rgb(42, 58, 85)

    private data class Feature(
        val id: String,
        val name: String,
        val description: String,
        val state: String,
        val icon: String
    )

    // The UI and install policy share one catalog; no category taxonomy is used.
    private val features = FeatureRegistry.catalog().map { item ->
        Feature(
            item.id,
            item.name,
            item.description,
            when (item.status) {
                FeatureRegistry.Status.AVAILABLE -> "Available"
                FeatureRegistry.Status.PLANNED -> "Planned"
                FeatureRegistry.Status.REVIEW_REQUIRED -> "Review required"
            },
            when (item.id) {
                "smart-performance-advisor" -> "↗"
                "server-status" -> "◉"
                "friends-chat" -> "✉"
                "theme-studio" -> "✦"
                "crash-report-center" -> "⚑"
                "screenshot-gallery" -> "▧"
                "world-backup-scheduler" -> "◫"
                "mod-compatibility-checker" -> "◇"
                "shader-preset-manager" -> "☼"
                "startup-diagnostics" -> "✓"
                "resource-pack-manager" -> "▤"
                "resource-pack-compatibility" -> "◇"
                "world-details-viewer" -> "▣"
                "local-playtime-tracker" -> "◷"
                "accessibility-presets" -> "◉"
                "profile-icon-studio" -> "✎"
                "configuration-backup" -> "⤓"
                "network-diagnostics" -> "⌁"
                "release-watch" -> "◴"
                "controller-presets" -> "⌘"
                "resource-usage-history" -> "▥"
                "instance-notes" -> "≡"
                else -> "⇧"
            }
        )
    }

    private lateinit var searchField: EditText
    private lateinit var featureList: LinearLayout
    private lateinit var countLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = surface
        LauncherPreferences.applyWindowFlags(this)

        val scroll = ScrollView(this).apply { setBackgroundColor(this@FeatureStoreActivity.background) }
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
        page.addView(space(12))
        val createOwn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = rounded(panel, 16, accent)
            isClickable = true
            isFocusable = true
            contentDescription = "Create your own custom feature blueprint"
            setOnClickListener {
                startActivity(android.content.Intent(
                    this@FeatureStoreActivity, CustomFeatureStudioActivity::class.java
                ))
            }
        }
        createOwn.addView(text("＋  CREATE YOUR OWN FEATURE", 14, primaryText, true))
        createOwn.addView(text(
            "Build and save a custom feature blueprint on this device. No coding required; drafts cannot execute code.",
            12, secondaryText, false
        ).apply { setPadding(0, dp(5), 0, 0) })
        page.addView(createOwn)
        page.addView(space(15))

        searchField = EditText(this).apply {
            hint = "Search features"
            textSize = 14f
            isSingleLine = true
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
            "Only completed built-in features can be installed. Remote packages stay blocked until signed manifests, integrity checks, compatibility rules, permission enforcement, and rollback tests exist.",
            12, secondaryText, false
        ))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(this@FeatureStoreActivity.background)
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(LauncherNavigationBar.create(this, "Store"))
        setContentView(root)

        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = renderFeatures()
        })
        renderFeatures()
    }

    override fun onResume() {
        super.onResume()
        LauncherPreferences.applyWindowFlags(this)
    }

    private fun renderFeatures() {
        featureList.removeAllViews()
        val query = searchField.text.toString().trim().lowercase()
        val visible = features.filter {
            it.name.lowercase().contains(query) || it.description.lowercase().contains(query)
        }
        countLabel.text = "${visible.size} features • ${FeatureRegistry.installedIds(this).size} installed • no categories"
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
        val statusColor = when (feature.state) {
            "Available" -> mint
            "Review required" -> Color.rgb(255, 190, 104)
            else -> secondaryText
        }
        val displayState = if (FeatureRegistry.isInstalled(this@FeatureStoreActivity, feature.id)) {
            "INSTALLED"
        } else {
            feature.state.uppercase()
        }
        titleStack.addView(text(displayState, 9, statusColor, true).apply {
            letterSpacing = 0.05f
            setPadding(0, dp(4), 0, 0)
        })
        top.addView(titleStack, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(top)
        card.addView(text(feature.description, 12, secondaryText, false).apply {
            setPadding(0, dp(12), 0, dp(12))
        })
        val button = TextView(this).apply {
            text = when {
                FeatureRegistry.isInstalled(this@FeatureStoreActivity, feature.id) -> "OPEN FEATURE  ›"
                FeatureRegistry.canInstall(feature.id) -> "INSTALL FEATURE  ↓"
                else -> "VIEW DETAILS  ›"
            }
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(primaryText)
            gravity = Gravity.CENTER
            background = rounded(panel, 11, border)
            setPadding(dp(12), dp(11), dp(12), dp(11))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val installed = FeatureRegistry.isInstalled(this@FeatureStoreActivity, feature.id)
                val installable = FeatureRegistry.canInstall(feature.id)
                if (feature.id == "theme-studio" && installed) {
                    startActivity(android.content.Intent(
                        this@FeatureStoreActivity, ThemeStudioActivity::class.java
                    ))
                    return@setOnClickListener
                }
                if (feature.id == "startup-diagnostics" && installed) {
                    startActivity(android.content.Intent(
                        this@FeatureStoreActivity, StartupDiagnosticsActivity::class.java
                    ))
                    return@setOnClickListener
                }
                if (feature.id == "instance-notes" && installed) {
                    startActivity(android.content.Intent(
                        this@FeatureStoreActivity, InstanceNotesActivity::class.java
                    ))
                    return@setOnClickListener
                }
                if (feature.id == "network-diagnostics" && installed) {
                    startActivity(android.content.Intent(
                        this@FeatureStoreActivity, NetworkDiagnosticsActivity::class.java
                    ))
                    return@setOnClickListener
                }
                if (installable && !installed) {
                    android.app.AlertDialog.Builder(this@FeatureStoreActivity)
                        .setTitle("Install ${feature.name}?")
                        .setMessage(
                            "This built-in feature runs locally and does not download or execute add-on code. " +
                                "Declared capabilities: " +
                                (FeatureRegistry.find(feature.id)?.permissions?.joinToString() ?: "None")
                        )
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Install") { _, _ ->
                            val result = FeatureRegistry.install(this@FeatureStoreActivity, feature.id)
                            if (result.isSuccess) {
                                Toast.makeText(this@FeatureStoreActivity, "Feature installed", Toast.LENGTH_SHORT).show()
                                renderFeatures()
                            } else {
                                Toast.makeText(
                                    this@FeatureStoreActivity,
                                    result.exceptionOrNull()?.message ?: "Install failed",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        .show()
                    return@setOnClickListener
                }
                val definition = FeatureRegistry.find(feature.id)
                val capabilities = definition?.permissions?.takeIf { it.isNotEmpty() }
                    ?.joinToString(", ") ?: "None declared"
                val stateText = if (installed) "Installed locally."
                    else "Not installable yet. Implementation and security checks are not ready."
                android.app.AlertDialog.Builder(this@FeatureStoreActivity)
                    .setTitle(feature.name)
                    .setMessage(
                        "${feature.description}\n\nStatus: ${feature.state}\n" +
                            "Declared capabilities: $capabilities\n\n$stateText"
                    )
                    .setPositiveButton("Close", null)
                    .show()
            }
        }
        card.addView(button)
        if (FeatureRegistry.isInstalled(this, feature.id)) {
            card.addView(space(7))
            val uninstall = TextView(this).apply {
                text = "UNINSTALL FEATURE"
                textSize = 10f
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setTextColor(Color.rgb(255, 170, 170))
                gravity = Gravity.CENTER
                background = rounded(panel, 11, border)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    android.app.AlertDialog.Builder(this@FeatureStoreActivity)
                        .setTitle("Uninstall ${feature.name}?")
                        .setMessage("This removes the feature's installed-state entry. It does not delete worlds, game files, or user data.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Uninstall") { _, _ ->
                            val result = FeatureRegistry.uninstall(this@FeatureStoreActivity, feature.id)
                            if (result.isSuccess) {
                                Toast.makeText(this@FeatureStoreActivity, "Feature uninstalled", Toast.LENGTH_SHORT).show()
                                renderFeatures()
                            } else {
                                Toast.makeText(this@FeatureStoreActivity, "Uninstall failed", Toast.LENGTH_LONG).show()
                            }
                        }
                        .show()
                }
            }
            card.addView(uninstall)
        }
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
