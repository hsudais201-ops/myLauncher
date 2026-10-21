package com.voidcraft.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Private, persisted text chat between accepted VoidCraft friends. */
class SocialChatActivity : Activity() {
    private val background = Color.rgb(10, 14, 24)
    private val surface = Color.rgb(18, 25, 40)
    private val panel = Color.rgb(23, 33, 52)
    private val accent = Color.rgb(139, 124, 255)
    private val mint = Color.rgb(51, 216, 190)
    private val primaryText = Color.rgb(243, 246, 255)
    private val secondaryText = Color.rgb(157, 174, 199)
    private val border = Color.rgb(42, 58, 85)
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val loading = AtomicBoolean(false)
    private lateinit var messagesColumn: LinearLayout
    private lateinit var messageScroll: ScrollView
    private lateinit var statusLabel: TextView
    private lateinit var input: EditText
    private lateinit var sendButton: TextView
    private var active = false
    private var myUserId: Long? = null
    private var renderedSignature = ""

    private val poller = object : Runnable {
        override fun run() {
            if (!active) return
            refreshMessages()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private val peerId: Long by lazy {
        intent.getLongExtra(EXTRA_USER_ID, -1L)
    }
    private val peerUsername: String by lazy {
        intent.getStringExtra(EXTRA_USERNAME).orEmpty()
    }
    private val peerDisplayName: String by lazy {
        intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty().ifBlank { peerUsername }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = surface
        LauncherPreferences.applyWindowFlags(this)
        if (peerId < 1L || peerUsername.isBlank()) {
            finish()
            return
        }
        setContentView(buildScreen())
    }

    override fun onResume() {
        super.onResume()
        active = true
        refreshMessages()
        handler.removeCallbacks(poller)
        handler.postDelayed(poller, POLL_INTERVAL_MS)
    }

    override fun onPause() {
        active = false
        handler.removeCallbacks(poller)
        super.onPause()
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(background)
        }
        val pageTop = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(12), dp(15), dp(10))
        }
        pageTop.addView(topBar())
        statusLabel = label("Connecting to secure private chat…", 11, secondaryText, false)
        pageTop.addView(space(7))
        pageTop.addView(statusLabel)
        pageTop.addView(space(10))
        val safetyActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        safetyActions.addView(actionButton("Block player", panel) { confirmBlock() },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })
        safetyActions.addView(actionButton("Report", panel) { showReportDialog() },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(5) })
        pageTop.addView(safetyActions)
        root.addView(pageTop, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        messageScroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = false
            setPadding(dp(12), dp(5), dp(12), dp(10))
        }
        messagesColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        messageScroll.addView(messagesColumn)
        root.addView(messageScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            setBackgroundColor(surface)
        }
        input = EditText(this).apply {
            hint = "Message @" + peerUsername
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 1
            maxLines = 4
            setTextColor(primaryText)
            setHintTextColor(secondaryText)
            textSize = 14f
            background = rounded(panel, 16, border)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            contentDescription = "Private message"
        }
        composer.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        sendButton = actionButton("Send", accent) { sendMessage() }
        composer.addView(sendButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(8) })
        root.addView(composer)
        root.addView(LauncherNavigationBar.create(this, "Social"))
        return root
    }

    private fun refreshMessages() {
        if (!active || loading.get()) return
        val api = SocialApiClient(applicationContext)
        if (!api.isSignedIn) {
            statusLabel.text = "Sign in with your VoidCraft account to continue this chat."
            statusLabel.setTextColor(Color.rgb(255, 190, 104))
            messagesColumn.removeAllViews()
            messagesColumn.addView(emptyState("Sign-in required", "Your conversations are tied to your VoidCraft account."))
            messagesColumn.addView(space(10))
            messagesColumn.addView(actionButton("Sign in to VoidCraft", accent) {
                startActivityForResult(Intent(this, VoidCraftAccountActivity::class.java), REQUEST_ACCOUNT)
            })
            return
        }
        if (!loading.compareAndSet(false, true)) return
        executor.execute {
            val result = runCatching {
                val currentId = myUserId ?: api.currentUser().id.also { myUserId = it }
                api.messages(peerId, limit = 40).let { currentId to it }
            }
            runOnUiThread {
                loading.set(false)
                if (isFinishing || isDestroyed || !active) return@runOnUiThread
                result.onSuccess { (currentId, messages) ->
                    val signature = messages.joinToString(",") { it.id.toString() }
                    statusLabel.text = "Private chat • messages refresh automatically"
                    statusLabel.setTextColor(mint)
                    if (signature != renderedSignature) {
                        renderedSignature = signature
                        renderMessages(messages, currentId)
                    }
                }.onFailure { error ->
                    statusLabel.text = "Private chat could not connect."
                    statusLabel.setTextColor(Color.rgb(255, 190, 104))
                    if (renderedSignature.isEmpty()) {
                        messagesColumn.removeAllViews()
                        messagesColumn.addView(emptyState(
                            "Messages unavailable",
                            error.message ?: "Sign in again or check the website social service."
                        ))
                    }
                }
            }
        }
    }

    private fun renderMessages(messages: List<VoidCraftSocialMessage>, currentUserId: Long) {
        messagesColumn.removeAllViews()
        if (messages.isEmpty()) {
            messagesColumn.addView(emptyState(
                "Start the conversation",
                "This is a private VoidCraft message thread. Only you and @" + peerUsername + " can read it."
            ))
            return
        }
        messages.forEach { message ->
            val isMine = message.senderId == currentUserId
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = if (isMine) Gravity.END else Gravity.START
                setPadding(dp(2), dp(3), dp(2), dp(3))
            }
            val bubble = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(9), dp(12), dp(9))
                background = rounded(
                    if (isMine) Color.rgb(58, 57, 112) else panel,
                    15,
                    if (isMine) null else border
                )
                maximumWidth = dp(280)
            }
            if (!isMine) {
                bubble.addView(label("@" + (message.senderUsername.ifBlank { peerUsername }), 10, mint, true))
                bubble.addView(space(3))
            }
            bubble.addView(label(message.body, 14, primaryText, false))
            bubble.addView(space(4))
            bubble.addView(label(formatTime(message.createdAt), 9, secondaryText, false).apply {
                gravity = if (isMine) Gravity.END else Gravity.START
            })
            row.addView(bubble, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                maxWidth = dp(300)
                if (isMine) marginStart = dp(30) else marginEnd = dp(30)
            })
            messagesColumn.addView(row)
        }
        messageScroll.post { messageScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun sendMessage() {
        if (loading.get()) return
        val message = input.text.toString().trim()
        if (message.isEmpty()) return
        if (message.length > 2000) {
            notice("Messages must be 2,000 characters or fewer.")
            return
        }
        val originalText = message
        input.text?.clear()
        sendButton.isEnabled = false
        sendButton.alpha = 0.65f
        executor.execute {
            val result = runCatching {
                SocialApiClient(applicationContext).sendMessage(peerId, originalText)
            }
            runOnUiThread {
                sendButton.isEnabled = true
                sendButton.alpha = 1f
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess {
                    refreshMessages()
                }.onFailure { error ->
                    input.setText(originalText)
                    notice(error.message ?: "Could not send message.")
                }
            }
        }
    }

    private fun confirmBlock() {
        AlertDialog.Builder(this)
            .setTitle("Block @" + peerUsername + "?")
            .setMessage("This stops friend requests and private messages in either direction, and removes the friendship.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Block") { _, _ ->
                executor.execute {
                    val result = runCatching { SocialApiClient(applicationContext).blockUser(peerId) }
                    runOnUiThread {
                        result.onSuccess {
                            notice("Player blocked.")
                            finish()
                        }.onFailure { error -> notice(error.message ?: "Could not block player.") }
                    }
                }
            }
            .show()
    }

    private fun showReportDialog() {
        val categories = arrayOf("Harassment", "Spam", "Impersonation", "Inappropriate content", "Other")
        val keys = arrayOf("harassment", "spam", "impersonation", "inappropriate-content", "other")
        AlertDialog.Builder(this)
            .setTitle("Report @" + peerUsername)
            .setItems(categories) { _, which -> askReportDetails(keys[which], categories[which]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun askReportDetails(category: String, title: String) {
        val details = EditText(this).apply {
            hint = "Optional details for moderators"
            minLines = 2
            maxLines = 5
            setTextColor(primaryText)
            setHintTextColor(secondaryText)
        }
        AlertDialog.Builder(this)
            .setTitle("Report: " + title)
            .setView(details)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Submit report") { _, _ ->
                val explanation = details.text.toString().trim()
                executor.execute {
                    val result = runCatching {
                        SocialApiClient(applicationContext).reportUser(peerId, category, explanation)
                    }
                    runOnUiThread {
                        result.onSuccess { notice("Report submitted for moderator review.") }
                            .onFailure { error -> notice(error.message ?: "Could not submit report.") }
                    }
                }
            }
            .show()
    }

    private fun topBar(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(actionButton("‹ Back", panel) { finish() })
        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        titleColumn.addView(label(peerDisplayName, 15, primaryText, true))
        titleColumn.addView(label("@" + peerUsername, 11, secondaryText, false).apply {
            setPadding(0, dp(3), 0, 0)
        })
        row.addView(titleColumn, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(label("VC", 13, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = gradient(accent, Color.rgb(79, 101, 245), 12)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        })
        return row
    }

    private fun formatTime(value: String): String = try {
        val instant = java.time.Instant.parse(value)
        java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            .withZone(java.time.ZoneId.systemDefault()).format(instant)
    } catch (_: Exception) {
        ""
    }

    private fun emptyState(title: String, description: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(15), dp(15), dp(15))
        background = rounded(panel, 16, border)
        addView(label(title, 14, primaryText, true))
        addView(space(6))
        addView(label(description, 12, secondaryText, false))
    }

    private fun actionButton(textValue: String, color: Int, action: () -> Unit) =
        TextView(this).apply {
            text = textValue
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = rounded(color, 12, if (color == panel) border else null)
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
        active = false
        handler.removeCallbacks(poller)
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ACCOUNT && resultCode == RESULT_OK) refreshMessages()
    }

    companion object {
        const val EXTRA_USER_ID = "com.voidcraft.launcher.SOCIAL_USER_ID"
        const val EXTRA_USERNAME = "com.voidcraft.launcher.SOCIAL_USERNAME"
        const val EXTRA_DISPLAY_NAME = "com.voidcraft.launcher.SOCIAL_DISPLAY_NAME"
        private const val REQUEST_ACCOUNT = 218
        private const val POLL_INTERVAL_MS = 4_000L
    }
}
