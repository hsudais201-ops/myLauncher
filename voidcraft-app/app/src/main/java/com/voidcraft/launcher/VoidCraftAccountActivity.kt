package com.voidcraft.launcher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
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
import java.util.concurrent.Executors

/** VoidCraft community account. This is separate from Minecraft/Microsoft sign-in. */
class VoidCraftAccountActivity : Activity() {
    private val background = Color.rgb(10, 14, 24)
    private val surface = Color.rgb(18, 25, 40)
    private val panel = Color.rgb(23, 33, 52)
    private val accent = Color.rgb(139, 124, 255)
    private val mint = Color.rgb(51, 216, 190)
    private val primaryText = Color.rgb(243, 246, 255)
    private val secondaryText = Color.rgb(157, 174, 199)
    private val border = Color.rgb(42, 58, 85)
    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var identifierField: EditText
    private lateinit var emailField: EditText
    private lateinit var usernameField: EditText
    private lateinit var passwordField: EditText
    private lateinit var statusLabel: TextView
    private lateinit var submitButton: TextView
    private lateinit var registerModeButton: TextView
    private lateinit var loginModeButton: TextView
    private lateinit var signOutButton: TextView
    private lateinit var authForm: LinearLayout
    private lateinit var authModeRow: LinearLayout
    private lateinit var profileCard: LinearLayout
    private lateinit var profileDisplayNameField: EditText
    private lateinit var profileBioField: EditText
    private lateinit var saveProfileButton: TextView
    private var createMode = false
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = surface
        LauncherPreferences.applyWindowFlags(this)
        setContentView(buildScreen())
        setMode(intent.getBooleanExtra(EXTRA_CREATE_ACCOUNT, false))
        updateSignOutVisibility()
        checkConnection()
        if (SocialApiClient(applicationContext).isSignedIn) loadOnlineProfile()
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(background)
        }
        val scroll = ScrollView(this).apply { clipToPadding = false }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }
        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        page.addView(topBar())
        page.addView(space(24))
        page.addView(label("VOIDCRAFT ACCOUNT", 11, accent, true))
        page.addView(space(6))
        page.addView(label("One account. Both places.", 28, primaryText, true))
        page.addView(space(8))
        page.addView(label(
            "Use your VoidCraft website account for friends and private chat in the launcher. This is not your Microsoft or Minecraft password.",
            14, secondaryText, false
        ))
        page.addView(space(16))

        val connectionCard = card()
        connectionCard.addView(label("ACCOUNT SERVICE", 10, accent, true))
        statusLabel = label("Checking website connection…", 13, secondaryText, false)
        connectionCard.addView(space(6))
        connectionCard.addView(statusLabel)
        connectionCard.addView(space(10))
        connectionCard.addView(actionButton("Check connection", panel) { checkConnection() })
        page.addView(connectionCard)
        page.addView(space(18))

        authModeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        loginModeButton = actionButton("Sign in", accent) { setMode(false) }
        registerModeButton = actionButton("Create account", panel) { setMode(true) }
        authModeRow.addView(loginModeButton, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginEnd = dp(6)
        })
        authModeRow.addView(registerModeButton, LinearLayout.LayoutParams(0, -2, 1f).apply {
            marginStart = dp(6)
        })
        page.addView(authModeRow)
        page.addView(space(12))

        authForm = card()
        val form = authForm
        form.addView(label("USERNAME OR EMAIL", 10, secondaryText, true))
        identifierField = field("Your VoidCraft username or email", InputType.TYPE_CLASS_TEXT)
        form.addView(identifierField)
        form.addView(space(11))
        form.addView(label("EMAIL ADDRESS", 10, secondaryText, true))
        emailField = field("name@example.com", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        form.addView(emailField)
        form.addView(space(11))
        form.addView(label("USERNAME", 10, secondaryText, true))
        usernameField = field("3–20 letters, numbers, or underscores", InputType.TYPE_CLASS_TEXT)
        form.addView(usernameField)
        form.addView(space(11))
        form.addView(label("PASSWORD", 10, secondaryText, true))
        passwordField = field("At least 12 characters", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        form.addView(passwordField)
        form.addView(space(14))
        submitButton = actionButton("Sign in to VoidCraft", accent) { submit() }
        form.addView(submitButton)
        form.addView(space(10))
        form.addView(label(
            "Never reuse your Microsoft password. VoidCraft account verification and password-recovery email must be enabled before public release.",
            11, secondaryText, false
        ))
        page.addView(form)
        page.addView(space(12))

        profileCard = card().apply { visibility = View.GONE }
        profileCard.addView(label("ONLINE PROFILE", 10, accent, true))
        profileCard.addView(space(5))
        profileCard.addView(label(
            "This profile is shared by the website and launcher. It is not the Microsoft/Minecraft profile.",
            12, secondaryText, false
        ))
        profileCard.addView(space(10))
        profileCard.addView(label("DISPLAY NAME", 10, secondaryText, true))
        profileDisplayNameField = field("Up to 32 characters", InputType.TYPE_CLASS_TEXT).apply {
            filters = arrayOf(android.text.InputFilter.LengthFilter(32))
        }
        profileCard.addView(profileDisplayNameField)
        profileCard.addView(space(10))
        profileCard.addView(label("BIO", 10, secondaryText, true))
        profileBioField = EditText(this).apply {
            hint = "A short profile bio (280 characters maximum)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            maxLines = 4
            setTextColor(primaryText)
            setHintTextColor(secondaryText)
            textSize = 14f
            background = rounded(surface, 12, border)
            setPadding(dp(12), dp(11), dp(12), dp(11))
            filters = arrayOf(android.text.InputFilter.LengthFilter(280))
        }
        profileCard.addView(profileBioField)
        profileCard.addView(space(12))
        saveProfileButton = actionButton("Save shared profile", accent) { saveOnlineProfile() }
        profileCard.addView(saveProfileButton)
        page.addView(profileCard)
        page.addView(space(12))

        val existingSession = SocialApiClient(applicationContext).isSignedIn
        if (existingSession) {
            authForm.visibility = View.GONE
            authModeRow.visibility = View.GONE
            profileCard.visibility = View.VISIBLE
        }

        signOutButton = actionButton("Sign out of this device", Color.rgb(102, 45, 61)) {
            signOut()
        }
        page.addView(signOutButton)
        page.addView(space(10))
        page.addView(actionButton("Open official VoidCraft website ↗", panel) {
            openWebsite()
        })
        page.addView(space(10))
        page.addView(actionButton("Back to Social Hub", panel) {
            finish()
        })
        root.addView(LauncherNavigationBar.create(this, "Social"))
        return root
    }

    private fun setMode(register: Boolean) {
        createMode = register
        if (!::identifierField.isInitialized) return
        identifierField.visibility = if (register) View.GONE else View.VISIBLE
        emailField.visibility = if (register) View.VISIBLE else View.GONE
        usernameField.visibility = if (register) View.VISIBLE else View.GONE
        loginModeButton.background = rounded(if (register) panel else accent, 13)
        registerModeButton.background = rounded(if (register) accent else panel, 13)
        loginModeButton.setTextColor(if (register) secondaryText else Color.WHITE)
        registerModeButton.setTextColor(if (register) Color.WHITE else secondaryText)
        submitButton.text = if (register) "Create VoidCraft account" else "Sign in to VoidCraft"
    }

    private fun submit() {
        if (busy) return
        val identifier = identifierField.text.toString().trim()
        val email = emailField.text.toString().trim()
        val username = usernameField.text.toString().trim()
        val password = passwordField.text.toString()
        if (createMode) {
            if (!email.contains("@") || username.length !in 3..20 || password.length < 12) {
                notice("Enter a valid email, a 3–20 character username, and a password of at least 12 characters.")
                return
            }
        } else if (identifier.isBlank() || password.isBlank()) {
            notice("Enter your VoidCraft username/email and password.")
            return
        }

        busy = true
        submitButton.isEnabled = false
        submitButton.alpha = 0.65f
        statusLabel.text = "Connecting securely…"
        val registering = createMode
        executor.execute {
            val result = runCatching {
                val client = SocialApiClient(applicationContext)
                if (!client.isServiceHealthy()) {
                    throw SocialApiException(
                        503,
                        "The VoidCraft account API is not connected to the website yet, or the network is unavailable."
                    )
                }
                if (registering) client.register(email, username, password)
                else client.login(identifier, password)
            }
            runOnUiThread {
                busy = false
                submitButton.isEnabled = true
                submitButton.alpha = 1f
                passwordField.text?.clear()
                result.onSuccess { user ->
                    statusLabel.text = "Connected as @" + user.username
                    statusLabel.setTextColor(mint)
                    notice(if (registering) "VoidCraft account created." else "Signed in as @" + user.username)
                    setResult(RESULT_OK)
                    finish()
                }.onFailure { error ->
                    statusLabel.text = "Account service unavailable"
                    statusLabel.setTextColor(Color.rgb(255, 190, 104))
                    notice(error.message ?: "Could not sign in to VoidCraft.")
                }
            }
        }
    }

    private fun checkConnection() {
        if (!::statusLabel.isInitialized || busy) return
        statusLabel.text = "Checking website connection…"
        executor.execute {
            val online = SocialApiClient(applicationContext).isServiceHealthy()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                statusLabel.text = if (online) {
                    "Connected. Use your VoidCraft account."
                } else {
                    "API not connected yet. Account sign-in will work after website integration."
                }
                statusLabel.setTextColor(if (online) mint else Color.rgb(255, 190, 104))
            }
        }
    }

    private fun loadOnlineProfile() {
        if (!::profileCard.isInitialized || !SocialApiClient(applicationContext).isSignedIn) return
        executor.execute {
            val result = runCatching { SocialApiClient(applicationContext).currentUser() }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { user ->
                    profileDisplayNameField.setText(user.displayName)
                    profileBioField.setText(user.bio)
                    profileCard.visibility = View.VISIBLE
                    authForm.visibility = View.GONE
                    authModeRow.visibility = View.GONE
                    statusLabel.text = "Signed into VoidCraft as @" + user.username
                    statusLabel.setTextColor(mint)
                    updateSignOutVisibility()
                }.onFailure { error ->
                    profileCard.visibility = View.GONE
                    authForm.visibility = View.VISIBLE
                    authModeRow.visibility = View.VISIBLE
                    updateSignOutVisibility()
                    statusLabel.text = "Please sign in again to load your profile."
                    statusLabel.setTextColor(Color.rgb(255, 190, 104))
                    notice(error.message ?: "Could not load your VoidCraft profile.")
                }
            }
        }
    }

    private fun saveOnlineProfile() {
        if (busy) return
        val displayName = profileDisplayNameField.text.toString().trim()
        val bio = profileBioField.text.toString().trim()
        if (displayName.isEmpty() || displayName.length > 32 || bio.length > 280) {
            notice("Display name must contain 1–32 characters and bio at most 280 characters.")
            return
        }
        busy = true
        saveProfileButton.isEnabled = false
        saveProfileButton.alpha = 0.65f
        statusLabel.text = "Saving your shared profile…"
        executor.execute {
            val result = runCatching {
                SocialApiClient(applicationContext).updateProfile(displayName, bio)
            }
            runOnUiThread {
                busy = false
                saveProfileButton.isEnabled = true
                saveProfileButton.alpha = 1f
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { user ->
                    profileDisplayNameField.setText(user.displayName)
                    profileBioField.setText(user.bio)
                    statusLabel.text = "Profile saved for @" + user.username
                    statusLabel.setTextColor(mint)
                    notice("Your VoidCraft profile was updated.")
                }.onFailure { error ->
                    statusLabel.text = "Could not save the profile."
                    statusLabel.setTextColor(Color.rgb(255, 190, 104))
                    notice(error.message ?: "Could not save your VoidCraft profile.")
                }
            }
        }
    }

    private fun updateSignOutVisibility() {
        if (!::signOutButton.isInitialized) return
        signOutButton.visibility = if (SocialApiClient(applicationContext).isSignedIn) View.VISIBLE else View.GONE
    }

    private fun signOut() {
        if (busy) return
        busy = true
        signOutButton.isEnabled = false
        executor.execute {
            runCatching { SocialApiClient(applicationContext).signOut() }
            runOnUiThread {
                busy = false
                signOutButton.isEnabled = true
                updateSignOutVisibility()
                statusLabel.text = "Signed out on this device."
                statusLabel.setTextColor(secondaryText)
                setResult(RESULT_OK)
                profileCard.visibility = View.GONE
                authForm.visibility = View.VISIBLE
                authModeRow.visibility = View.VISIBLE
                setMode(false)
                updateSignOutVisibility()
                notice("VoidCraft session cleared from this device.")
            }
        }
    }

    private fun openWebsite() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WEBSITE_URL)))
        } catch (_: Exception) {
            notice("Could not open the official VoidCraft website.")
        }
    }

    private fun topBar(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(label("VC", 18, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = gradient(accent, Color.rgb(79, 101, 245), 14)
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46))
        })
        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), 0, 0, 0)
        }
        titleColumn.addView(label("VOIDCRAFT", 15, primaryText, true))
        titleColumn.addView(label("ACCOUNT & SOCIAL IDENTITY", 10, secondaryText, true).apply {
            setPadding(0, dp(3), 0, 0)
        })
        row.addView(titleColumn, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(label("SECURE SESSION", 9, mint, true))
        return row
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(15), dp(15), dp(15))
        background = rounded(panel, 17, border)
    }

    private fun field(hintText: String, type: Int) = EditText(this).apply {
        hint = hintText
        inputType = type
        textSize = 14f
        isSingleLine = true
        setTextColor(primaryText)
        setHintTextColor(secondaryText)
        background = rounded(surface, 12, border)
        setPadding(dp(12), dp(11), dp(12), dp(11))
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
    }

    private fun actionButton(textValue: String, color: Int, action: () -> Unit) =
        TextView(this).apply {
            text = textValue
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(13), dp(12), dp(13))
            background = rounded(color, 13, if (color == panel) border else null)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun label(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        typeface = Typeface.create(
            if (bold) "sans-serif-medium" else "sans-serif",
            if (bold) Typeface.BOLD else Typeface.NORMAL
        )
    }

    private fun rounded(color: Int, radius: Int, stroke: Int? = null) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun gradient(start: Int, end: Int, radius: Int) =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(start, end)).apply {
            cornerRadius = dp(radius).toFloat()
        }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun notice(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CREATE_ACCOUNT = "com.voidcraft.launcher.CREATE_SOCIAL_ACCOUNT"
        const val WEBSITE_URL = "https://voidcraft-website.pages.dev"
    }
}
