package com.voidcraft.launcher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Social Hub shell for VoidCraft community discovery.
 * It deliberately does not simulate real friends, online presence, or messages:
 * those require an authenticated backend and abuse-reporting/privacy controls.
 */
class SocialHubActivity : Activity() {
    private val bg = Color.rgb(10, 14, 24)
    private val surface = Color.rgb(18, 25, 40)
    private val card = Color.rgb(23, 33, 52)
    private val accent by lazy { LauncherPreferences.accentColor(this) }
    private val textMain = Color.rgb(243, 246, 255)
    private val textMuted = Color.rgb(157, 174, 199)
    private val border = Color.rgb(42, 58, 85)
    private val mint = Color.rgb(51, 216, 190)
    private val discordInvite = "https://discord.gg/FM6myW8ZFb"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = surface
        LauncherPreferences.applyWindowFlags(this)
        setContentView(buildScreen())
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this).apply { clipToPadding = false }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(24))
        }
        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        page.addView(topBar())
        page.addView(space(22))
        page.addView(label("VOIDCRAFT COMMUNITY", 11, accent, true))
        page.addView(space(7))
        page.addView(label("Social Hub", 30, textMain, true))
        page.addView(space(7))
        page.addView(label(
            "Meet players, follow updates, and find help for your launcher.",
            14, textMuted, false
        ))
        page.addView(space(18))
        page.addView(communityHero())
        page.addView(space(22))
        page.addView(sectionTitle("YOUR SOCIAL SPACE", "Account-aware"))
        page.addView(space(10))
        page.addView(infoCard(
            "♙", "Friends & requests", "Find players, accept or decline requests, and manage your list.",
            "BACKEND NOT CONNECTED", accent
        ) {
            notice("Friends are not connected yet. No fake users or friend requests have been created.")
        })
        page.addView(space(10))
        page.addView(infoCard(
            "✉", "Private messages", "A private inbox with block, report, and message controls is planned.",
            "NOT AVAILABLE YET", textMuted
        ) {
            notice("Chat is not live yet. Sign-in alone is not enough; a secure messaging service is required.")
        })
        page.addView(space(10))
        page.addView(infoCard(
            "◈", "Player profile", "Your public display name and privacy settings will live here when account services are ready.",
            "LOCAL PREVIEW", mint
        ) {
            startActivity(Intent(this, AccountActivity::class.java))
        })
        page.addView(space(22))
        page.addView(sectionTitle("COMMUNITY LINKS", "Open"))
        page.addView(space(10))
        page.addView(linkCard(
            "Discord server", "Announcements • support • feature ideas",
            "Open community ↗"
        ) { openDiscord() })
        page.addView(space(10))
        page.addView(linkCard(
            "Help & feedback", "Share bugs and suggest what VoidCraft should build next",
            "Join discussion ↗"
        ) { openDiscord() })
        page.addView(space(18))
        page.addView(privacyNote())
        root.addView(LauncherNavigationBar.create(this, "Social"))
        return root
    }

    private fun topBar(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val mark = TextView(this).apply {
            text = "VC"
            textSize = 17f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = gradient(accent, Color.rgb(79, 101, 245), 14)
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
            contentDescription = "VoidCraft logo"
        }
        row.addView(mark)
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), 0, 0, 0)
        }
        brand.addView(label("VOIDCRAFT", 15, textMain, true))
        brand.addView(label("PLAYER COMMUNITY", 10, textMuted, true).apply {
            letterSpacing = 0.08f
            setPadding(0, dp(3), 0, 0)
        })
        row.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(pill("SOCIAL", accent))
        return row
    }

    private fun communityHero(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(19), dp(18), dp(18))
            background = gradient(Color.rgb(36, 37, 78), Color.rgb(20, 49, 68), 22, border)
        }
        panel.addView(pill("OFFICIAL COMMUNITY", mint))
        panel.addView(space(13))
        panel.addView(label("Your next world starts with a community.", 22, textMain, true))
        panel.addView(space(8))
        panel.addView(label(
            "Get support, share ideas, report bugs, and follow VoidCraft updates on Discord.",
            13, textMuted, false
        ))
        panel.addView(space(16))
        val button = TextView(this).apply {
            text = "  Join VoidCraft Discord  ↗  "
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(13), dp(12), dp(13))
            background = rounded(accent, 14)
            isClickable = true
            isFocusable = true
            setOnClickListener { openDiscord() }
        }
        panel.addView(button)
        panel.addView(space(8))
        panel.addView(label("Invite: discord.gg/FM6myW8ZFb", 11, textMuted, false))
        return panel
    }

    private fun infoCard(
        glyph: String, title: String, description: String,
        status: String, statusColor: Int, action: () -> Unit
    ): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(card, 17, border)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val icon = TextView(this).apply {
            text = glyph
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(accent)
            background = rounded(Color.rgb(42, 42, 78), 12)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        }
        row.addView(icon)
        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), 0, 0, 0)
        }
        titleColumn.addView(label(title, 15, textMain, true))
        titleColumn.addView(label(description, 12, textMuted, false).apply {
            setPadding(0, dp(4), 0, 0)
        })
        row.addView(titleColumn, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(label("›", 25, textMuted, false))
        box.addView(row)
        box.addView(space(12))
        box.addView(label("●  $status", 10, statusColor, true))
        return box
    }

    private fun linkCard(title: String, subtitle: String, actionLabel: String, action: () -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(15), dp(15), dp(15))
            background = rounded(surface, 16, border)
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        details.addView(label(title, 15, textMain, true))
        details.addView(label(subtitle, 12, textMuted, false).apply { setPadding(0, dp(4), 0, 0) })
        row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(label("↗", 20, accent, true))
        box.addView(row)
        box.addView(space(10))
        box.addView(label(actionLabel, 12, mint, true))
        box.isClickable = true
        box.isFocusable = true
        box.setOnClickListener { action() }
        return box
    }

    private fun privacyNote(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(Color.rgb(25, 32, 48), 14, border)
        }
        box.addView(label("PRIVACY FIRST", 10, mint, true))
        box.addView(space(6))
        box.addView(label(
            "Friends and direct messages are not active yet. VoidCraft will need secure sign-in, server-side access checks, block/report tools, and privacy controls before real social data is enabled.",
            12, textMuted, false
        ))
        return box
    }

    private fun sectionTitle(title: String, action: String): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(label(title, 11, textMuted, true), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(label(action, 11, accent, true))
        return row
    }

    private fun openDiscord() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(discordInvite)))
        } catch (_: Exception) {
            notice("Could not open Discord. Try $discordInvite")
        }
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        typeface = Typeface.create(
            if (bold) "sans-serif-medium" else "sans-serif",
            if (bold) Typeface.BOLD else Typeface.NORMAL
        )
        includeFontPadding = true
    }

    private fun pill(value: String, color: Int) = TextView(this).apply {
        text = value
        textSize = 9f
        letterSpacing = 0.04f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setTextColor(color)
        gravity = Gravity.CENTER
        setPadding(dp(9), dp(7), dp(9), dp(7))
        background = rounded(Color.rgb(28, 39, 60), 30, border)
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun notice(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
