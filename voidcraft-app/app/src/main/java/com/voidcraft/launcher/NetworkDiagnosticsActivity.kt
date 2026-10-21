package com.voidcraft.launcher

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.InetSocketAddress
import java.net.Socket

/**
 * User-triggered TCP connectivity check. It sends no game-protocol payload,
 * does not change network settings, and does not claim a Minecraft handshake.
 */
class NetworkDiagnosticsActivity : Activity() {
    private val background = Color.rgb(10, 14, 24)
    private val surface = Color.rgb(18, 25, 40)
    private val panel = Color.rgb(23, 33, 52)
    private val accent = Color.rgb(139, 124, 255)
    private val mint = Color.rgb(51, 216, 190)
    private val primaryText = Color.rgb(243, 246, 255)
    private val secondaryText = Color.rgb(157, 174, 199)
    private val border = Color.rgb(42, 58, 85)

    private lateinit var hostField: EditText
    private lateinit var portField: EditText
    private lateinit var testButton: TextView
    private lateinit var resultPanel: LinearLayout
    private lateinit var resultTitle: TextView
    private lateinit var resultBody: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = surface
        LauncherPreferences.applyWindowFlags(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(this@NetworkDiagnosticsActivity.background)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(this@NetworkDiagnosticsActivity.background)
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(28))
        }
        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        page.addView(actionText("‹  FEATURE STORE", 13, accent) { finish() })
        page.addView(space(18))
        page.addView(text("Network Diagnostics", 26, primaryText, true))
        page.addView(text(
            "Check whether a hostname or IP address accepts a TCP connection on a port you choose.",
            14, secondaryText, false
        ).apply { setPadding(0, dp(6), 0, dp(16)) })

        val notice = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(Color.rgb(48, 39, 25), 14, Color.rgb(112, 87, 45))
        }
        notice.addView(text("WHAT THIS TEST MEANS", 11, Color.rgb(255, 190, 104), true))
        notice.addView(text(
            "A successful result only means the TCP port accepted a connection at test time. It does not confirm a Minecraft handshake, server version, online player count, or gameplay access. No network settings are changed.",
            12, primaryText, false
        ).apply { setPadding(0, dp(5), 0, 0) })
        page.addView(notice)
        page.addView(space(18))

        page.addView(text("Hostname or IP address", 13, primaryText, true))
        hostField = editField("play.example.net").apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine(true)
        }
        page.addView(hostField)
        page.addView(space(12))
        page.addView(text("TCP port (1–65535)", 13, primaryText, true))
        portField = editField("25565").apply {
            setText("25565")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
        }
        page.addView(portField)
        page.addView(space(14))

        testButton = actionButton("TEST CONNECTION") { testConnection() }
        page.addView(testButton)
        page.addView(space(12))

        resultPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(surface, 15, border)
        }
        resultTitle = text("Ready", 15, primaryText, true)
        resultBody = text(
            "Enter a host and port, then run the test. Nothing is sent until you tap the button.",
            13, secondaryText, false
        ).apply { setPadding(0, dp(5), 0, 0) }
        resultPanel.addView(resultTitle)
        resultPanel.addView(resultBody)
        page.addView(resultPanel)

        root.addView(LauncherNavigationBar.create(this, "Store"))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        LauncherPreferences.applyWindowFlags(this)
    }

    private fun testConnection() {
        val host = hostField.text.toString().trim()
        val port = portField.text.toString().trim().toIntOrNull()
        if (host.isBlank() || host.length > 253 ||
            host.any { it.isWhitespace() } || host.contains("/") || host.contains("://")
        ) {
            showResult("Invalid hostname", "Enter a hostname or IP address only, without a URL scheme or path.", false)
            return
        }
        if (port == null || port !in 1..65535) {
            showResult("Invalid port", "Port must be a number from 1 through 65535.", false)
            return
        }

        testButton.isEnabled = false
        testButton.alpha = 0.65f
        showResult("Testing connection…", "Trying " + host + ":" + port + " with a 3-second timeout.", true)

        Thread({
            val started = SystemClock.elapsedRealtime()
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                }
                val elapsed = SystemClock.elapsedRealtime() - started
                runOnUiThread {
                    showResult(
                        "TCP connection succeeded",
                        host + ":" + port + " accepted a TCP connection in " + elapsed +
                            " ms. This is not a Minecraft server-status or gameplay test.",
                        true
                    )
                    testButton.isEnabled = true
                    testButton.alpha = 1f
                }
            } catch (error: Exception) {
                runOnUiThread {
                    showResult(
                        "Connection failed",
                        "Could not connect to " + host + ":" + port +
                            ". Check the hostname, port, internet connection, and server firewall. (" +
                            (error.javaClass.simpleName ?: "network error") + ")",
                        false
                    )
                    testButton.isEnabled = true
                    testButton.alpha = 1f
                }
            }
        }, "VoidCraftNetworkCheck").apply {
            isDaemon = true
            start()
        }
    }

    private fun showResult(title: String, detail: String, success: Boolean) {
        resultTitle.text = title
        resultTitle.setTextColor(if (success) mint else Color.rgb(255, 175, 175))
        resultBody.text = detail
    }

    private fun editField(hintText: String) = EditText(this).apply {
        hint = hintText
        textSize = 15f
        setTextColor(primaryText)
        setHintTextColor(secondaryText)
        background = rounded(panel, 12, border)
        setPadding(dp(13), dp(11), dp(13), dp(11))
    }

    private fun actionButton(value: String, onClick: () -> Unit) = TextView(this).apply {
        text = value
        textSize = 12f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setTextColor(primaryText)
        gravity = Gravity.CENTER
        background = rounded(panel, 12, border)
        setPadding(dp(14), dp(14), dp(14), dp(14))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
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

    companion object {
        private const val CONNECT_TIMEOUT_MS = 3000
    }
}
