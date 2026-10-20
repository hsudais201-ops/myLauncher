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
import android.widget.TextView
import android.widget.Toast

/**
 * Original VoidCraft dashboard, using familiar Android Material-style patterns
 * without copying another launcher's assets, branding, or source layout.
 *
 * This is still a UI shell: the real Minecraft runtime and launch service
 * are not connected yet, so the Play action explains that state honestly.
 */
class MainActivity : Activity() {
    private var homeScroll: ScrollView? = null
    private val background = Color.rgb(10, 14, 24)
    private val surface = Color.rgb(18, 25, 40)
    private val cardSurface = Color.rgb(23, 33, 52)
    private val cardRaised = Color.rgb(29, 42, 65)
    private val accent = Color.rgb(139, 124, 255)
    private val mint = Color.rgb(51, 216, 190)
    private val primaryText = Color.rgb(243, 246, 255)
    private val secondaryText = Color.rgb(157, 174, 199)
    private val subtleBorder = Color.rgb(42, 58, 85)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = surface
        window.decorView.systemUiVisibility = 0
        LauncherPreferences.applyWindowFlags(this)
        setContentView(buildScreen())
    }

    override fun onResume() {
        super.onResume()
        LauncherPreferences.applyWindowFlags(this)
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(this@MainActivity.background)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            clipToPadding = false
            setBackgroundColor(background)
        }
        homeScroll = scroll
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(24))
        }
        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        page.addView(buildTopBar())
        page.addView(space(22))
        page.addView(text("Ready to play?", 27, primaryText, true))
        page.addView(text(
            "Your Minecraft worlds, instances, and settings in one place.",
            14, secondaryText, false
        ).apply { setPadding(0, dp(5), 0, 0) })
        page.addView(space(18))

        page.addView(buildInstanceCard())
        page.addView(space(12))
        page.addView(buildPlayButton())
        page.addView(space(15))
        page.addView(buildPerformanceCard())
        page.addView(space(22))

        page.addView(sectionHeading("QUICK ACCESS", "Open"))
        page.addView(space(10))
        val firstRow = horizontalRow()
        firstRow.addView(shortcutCard("▦", "Instances", "Profiles & loaders") {
            startActivity(Intent(this, InstanceListActivity::class.java))
        }, weightedParams(1f, dp(6)))
        firstRow.addView(shortcutCard("⇩", "Versions", "Official version list") {
            startActivity(Intent(this, VersionBrowserActivity::class.java))
        }, weightedParams(1f, dp(6)))
        page.addView(firstRow)
        page.addView(space(10))

        val secondRow = horizontalRow()
        secondRow.addView(shortcutCard("◇", "Feature Store", "Optional add-ons") {
            startActivity(Intent(this, FeatureStoreActivity::class.java))
        }, weightedParams(1f, dp(6)))
        secondRow.addView(shortcutCard("⚙", "Settings", "Launcher preferences") {
            startActivity(Intent(this, SettingsActivity::class.java))
        }, weightedParams(1f, dp(6)))
        page.addView(secondRow)
        page.addView(space(22))

        page.addView(sectionHeading("SETUP STATUS", "Prototype"))
        page.addView(space(10))
        page.addView(buildSetupStatus())
        page.addView(space(18))
        page.addView(text(
            "VoidCraft Launcher • 0.1.0-dev\nThis independent app is separate from your existing launcher. Your worlds and game files are not changed by this screen.",
            12, secondaryText, false
        ).apply { setPadding(dp(2), 0, dp(2), 0) })

        root.addView(buildBottomNav())
        return root
    }

    private fun buildTopBar(): View {
        val row = horizontalRow().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, dp(5))
        }
        val mark = TextView(this).apply {
            text = "VC"
            textSize = 17f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = gradient(accent, Color.rgb(79, 101, 245), 14)
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
        }
        row.addView(mark)
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), 0, 0, 0)
        }
        brand.addView(text("VOIDCRAFT", 15, primaryText, true).apply {
            letterSpacing = 0.09f
        })
        brand.addView(text("JAVA EDITION LAUNCHER", 10, secondaryText, true).apply {
            letterSpacing = 0.08f
            setPadding(0, dp(3), 0, 0)
        })
        row.addView(brand, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val profile = TextView(this).apply {
            text = "●  OFFLINE"
            textSize = 10f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(secondaryText)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = rounded(cardSurface, 30, subtleBorder)
            contentDescription = "Account status: no connected account"
        }
        row.addView(profile)
        return row
    }

    private fun buildInstanceCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(17), dp(17), dp(17), dp(17))
            background = gradient(Color.rgb(31, 43, 70), Color.rgb(25, 29, 61), 22, subtleBorder)
            elevation = dp(2).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(this@MainActivity, InstanceListActivity::class.java))
            }
        }
        val header = horizontalRow().apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("ACTIVE INSTANCE", 11, secondaryText, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(pill("SELECT PROFILE", accent))
        card.addView(header)
        card.addView(space(15))

        val body = horizontalRow().apply { gravity = Gravity.CENTER_VERTICAL }
        val icon = TextView(this).apply {
            text = "▦"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(primaryText)
            background = rounded(Color.rgb(43, 57, 89), 16, Color.rgb(66, 84, 127))
            layoutParams = LinearLayout.LayoutParams(dp(60), dp(60))
        }
        body.addView(icon)
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        info.addView(text("Choose an instance", 18, primaryText, true))
        info.addView(text(
            "Vanilla, Fabric, Forge and more",
            12, secondaryText, false
        ).apply { setPadding(0, dp(5), 0, 0) })
        body.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        body.addView(text("›", 30, secondaryText, false))
        card.addView(body)
        card.addView(space(15))

        val divider = View(this).apply { setBackgroundColor(subtleBorder) }
        card.addView(divider, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
        ))
        card.addView(space(12))
        val versionLine = horizontalRow().apply { gravity = Gravity.CENTER_VERTICAL }
        versionLine.addView(text("Minecraft version", 12, secondaryText, false),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        versionLine.addView(text("Choose version  ↗", 12, mint, true).apply {
            setOnClickListener {
                startActivity(Intent(this@MainActivity, VersionBrowserActivity::class.java))
            }
        })
        card.addView(versionLine)
        return card
    }

    private fun buildPlayButton(): View {
        val button = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = gradient(accent, Color.rgb(62, 190, 215), 17)
            elevation = dp(3).toFloat()
            isClickable = true
            isFocusable = true
            contentDescription = "Play Minecraft. Runtime is not connected in this prototype."
            setOnClickListener {
                notice("Minecraft runtime is not connected yet. This button will launch the selected instance after runtime integration.")
            }
        }
        button.addView(text("▶", 20, Color.WHITE, true).apply {
            setPadding(0, 0, dp(10), 0)
        })
        button.addView(text("PLAY", 17, Color.WHITE, true).apply {
            letterSpacing = 0.12f
        })
        return button
    }

    private fun buildPerformanceCard(): View {
        val row = horizontalRow().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(surface, 18, subtleBorder)
        }
        val icon = TextView(this).apply {
            text = "↗"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(mint)
            background = rounded(Color.rgb(18, 62, 68), 13)
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
        }
        row.addView(icon)
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }
        details.addView(text("Performance Advisor", 15, primaryText, true))
        details.addView(text(
            "Device tuning and diagnostics",
            12, secondaryText, false
        ).apply { setPadding(0, dp(4), 0, 0) })
        row.addView(details, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(pill("COMING NEXT", mint))
        row.isClickable = true
        row.isFocusable = true
        row.setOnClickListener {
            notice("Performance Advisor is planned; no FPS cap is applied by this prototype.")
        }
        return row
    }

    private fun shortcutCard(
        glyph: String,
        title: String,
        subtitle: String,
        onClick: () -> Unit
    ): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(12), dp(14))
            background = rounded(cardSurface, 17, subtleBorder)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            minimumHeight = dp(126)
        }
        val glyphView = TextView(this).apply {
            text = glyph
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(accent)
            background = rounded(Color.rgb(42, 42, 78), 12)
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
        }
        card.addView(glyphView)
        card.addView(space(12))
        card.addView(text(title, 15, primaryText, true))
        card.addView(text(subtitle, 11, secondaryText, false).apply {
            setPadding(0, dp(4), 0, 0)
        })
        return card
    }

    private fun buildSetupStatus(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(5), dp(15), dp(5))
            background = rounded(surface, 17, subtleBorder)
        }
        addStatusRow(card, "Dashboard UI", "Ready", mint)
        addStatusRow(card, "Version browser", "Metadata only", accent)
        addStatusRow(card, "Minecraft runtime", "Not connected", Color.rgb(255, 190, 104))
        return card
    }

    private fun addStatusRow(parent: LinearLayout, title: String, status: String, statusColor: Int) {
        val row = horizontalRow().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        row.addView(text(title, 13, primaryText, false),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(text("●  $status", 11, statusColor, true))
        parent.addView(row)
        if (title != "Minecraft runtime") {
            parent.addView(View(this).apply { setBackgroundColor(subtleBorder) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        }
    }

    private fun sectionHeading(title: String, action: String): View {
        val row = horizontalRow().apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(text(title, 11, secondaryText, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(text(action, 12, accent, true))
        return row
    }

    private fun buildBottomNav(): View =
        LauncherNavigationBar.create(this, "Home") { scrollToTop() }

    private fun scrollToTop() {
        homeScroll?.smoothScrollTo(0, 0)
    }

    private fun horizontalRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun weightedParams(weight: Float, margin: Int) =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight).apply {
            marginEnd = margin
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

    private fun pill(value: String, color: Int) = TextView(this).apply {
        text = value
        textSize = 9f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setTextColor(color)
        gravity = Gravity.CENTER
        letterSpacing = 0.04f
        setPadding(dp(9), dp(7), dp(9), dp(7))
        background = rounded(Color.rgb(28, 39, 60), 30, subtleBorder)
    }

    private fun rounded(color: Int, radius: Int, stroke: Int? = null) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun gradient(start: Int, end: Int, radius: Int, stroke: Int? = null) =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(start, end)).apply {
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun notice(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
