package com.voidcraft.launcher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Account UI preview. Local display-name storage is real; online authentication
 * is not simulated and will remain unavailable until OAuth/entitlement checks exist.
 */
class AccountActivity : Activity() {
    private val background = Color.rgb(10, 14, 24)
    private val surface = Color.rgb(18, 25, 40)
    private val panel = Color.rgb(23, 33, 52)
    private val accent = Color.rgb(139, 124, 255)
    private val mint = Color.rgb(51, 216, 190)
    private val primaryText = Color.rgb(243, 246, 255)
    private val secondaryText = Color.rgb(157, 174, 199)
    private val border = Color.rgb(42, 58, 85)

    private lateinit var displayNameField: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = surface
        LauncherPreferences.applyWindowFlags(this)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(this@AccountActivity.background)
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(26))
        }
        scroll.addView(page)

        page.addView(header())
        page.addView(space(22))
        page.addView(text("Your account", 27, primaryText, true))
        page.addView(text(
            "Manage the local launcher profile and check online sign-in readiness.",
            14, secondaryText, false
        ).apply { setPadding(0, dp(6), 0, 0) })
        page.addView(space(16))
        page.addView(statusCard())
        page.addView(space(18))

        page.addView(sectionTitle("LOCAL DISPLAY PROFILE"))
        page.addView(space(9))
        val localCard = card()
        localCard.addView(text("Display name", 15, primaryText, true))
        localCard.addView(text(
            "This name is stored on this device for the launcher UI only. It does not authenticate with Microsoft or create a Minecraft account.",
            12, secondaryText, false
        ).apply { setPadding(0, dp(5), 0, dp(11)) })
        val prefs = getSharedPreferences(LauncherPreferences.PREFS_NAME, MODE_PRIVATE)
        displayNameField = EditText(this).apply {
            hint = "Enter a display name"
            setText(prefs.getString(KEY_DISPLAY_NAME, "Player"))
            textSize = 14f
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(primaryText)
            setHintTextColor(secondaryText)
            background = rounded(panel, 12, border)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            contentDescription = "Local launcher display name"
        }
        localCard.addView(displayNameField)
        localCard.addView(space(10))
        localCard.addView(actionButton("Save display name", accent) { saveDisplayName() })
        page.addView(localCard)
        page.addView(space(18))

        page.addView(sectionTitle("MICROSOFT ACCOUNT"))
        page.addView(space(9))
        val microsoftCard = card()
        val msHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        msHeader.addView(text("Microsoft sign-in", 16, primaryText, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        msHeader.addView(statusPill("NOT CONNECTED", Color.rgb(255, 190, 104)))
        microsoftCard.addView(msHeader)
        microsoftCard.addView(text(
            "The independent app needs the full Microsoft OAuth, Xbox profile, Minecraft entitlement, and token-storage flow before online sign-in can be enabled.",
            12, secondaryText, false
        ).apply { setPadding(0, dp(8), 0, dp(12)) })
        microsoftCard.addView(actionButton("Connect Microsoft", panel) {
            notice("Microsoft authentication is not connected yet. No credentials or tokens were collected.")
        })
        page.addView(microsoftCard)
        page.addView(space(18))

        page.addView(sectionTitle("ACCOUNT SAFETY"))
        page.addView(space(9))
        val safety = card()
        safety.addView(text("No password fields in this preview", 14, primaryText, true))
        safety.addView(text(
            "When sign-in is added, authentication should use the official OAuth flow, protect refresh tokens, and verify Xbox/Minecraft ownership. Do not enter Microsoft credentials into this preview.",
            12, secondaryText, false
        ).apply { setPadding(0, dp(5), 0, 0) })
        page.addView(safety)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(this@AccountActivity.background)
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(LauncherNavigationBar.create(this, "Account"))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        LauncherPreferences.applyWindowFlags(this)
    }

    private fun saveDisplayName() {
        val name = displayNameField.text.toString().trim()
        if (name.isEmpty() || name.length > 24 || name.any { it.isISOControl() }) {
            notice("Use a display name of 1–24 visible characters.")
            return
        }
        getSharedPreferences(LauncherPreferences.PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_DISPLAY_NAME, name)
            .apply()
        notice("Display name saved locally. Minecraft sign-in is still disconnected.")
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
            contentDescription = "Back"
            setOnClickListener { finish() }
        }
        row.addView(back)
        val title = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        title.addView(text("VOIDCRAFT", 15, primaryText, true).apply {
            letterSpacing = 0.07f
        })
        title.addView(text("ACCOUNT CENTER", 10, secondaryText, true).apply {
            letterSpacing = 0.06f
            setPadding(0, dp(3), 0, 0)
        })
        row.addView(title)
        return row
    }

    private fun statusCard(): View {
        val box = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val avatar = TextView(this).apply {
            text = "VC"
            textSize = 18f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(accent, 15)
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(54))
        }
        row.addView(avatar)
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        info.addView(text("Local launcher profile", 15, primaryText, true))
        info.addView(text("Online account not connected", 12, secondaryText, false)
            .apply { setPadding(0, dp(4), 0, 0) })
        row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(row)
        box.addView(space(12))
        box.addView(statusPill("OFFLINE MODE", mint))
        return box
    }

    private fun sectionTitle(value: String) = text(value, 11, secondaryText, true).apply {
        letterSpacing = 0.08f
    }

    private fun statusPill(value: String, color: Int) = text(value, 9, color, true).apply {
        gravity = Gravity.CENTER
        background = rounded(panel, 30, border)
        setPadding(dp(9), dp(7), dp(9), dp(7))
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(15), dp(15), dp(15))
        background = rounded(surface, 18, border)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun actionButton(value: String, fill: Int, onClick: () -> Unit) =
        TextView(this).apply {
            text = value
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(primaryText)
            gravity = Gravity.CENTER
            background = rounded(fill, 12, if (fill == panel) border else null)
            setPadding(dp(14), dp(12), dp(14), dp(12))
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

    companion object {
        private const val KEY_DISPLAY_NAME = "launcher_display_name"
    }
}
