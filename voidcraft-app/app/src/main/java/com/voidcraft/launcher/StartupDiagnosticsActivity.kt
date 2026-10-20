package com.voidcraft.launcher

import android.app.Activity
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/**
 * Read-only diagnostics. This page does not claim Minecraft can launch and
 * does not alter memory, graphics settings, FPS, game files, or user data.
 */
class StartupDiagnosticsActivity : Activity() {
    private val background = Color.rgb(10, 14, 24)
    private val surface = Color.rgb(18, 25, 40)
    private val panel = Color.rgb(23, 33, 52)
    private val accent = Color.rgb(139, 124, 255)
    private val mint = Color.rgb(51, 216, 190)
    private val primaryText = Color.rgb(243, 246, 255)
    private val secondaryText = Color.rgb(157, 174, 199)
    private val border = Color.rgb(42, 58, 85)
    private lateinit var rows: LinearLayout
    private var lastReport = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = surface
        LauncherPreferences.applyWindowFlags(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(this@StartupDiagnosticsActivity.background)
        }
        val scroll = ScrollView(this).apply { setBackgroundColor(this@StartupDiagnosticsActivity.background) }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(26))
        }
        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        page.addView(actionText("‹  FEATURE STORE", 13, accent) { finish() })
        page.addView(space(18))
        page.addView(text("Startup Diagnostics", 26, primaryText, true))
        page.addView(text(
            "Read-only checks to help understand this device before attempting a game launch.",
            14, secondaryText, false
        ).apply { setPadding(0, dp(6), 0, dp(16)) })

        val warning = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(13), dp(13), dp(13), dp(13))
            background = rounded(Color.rgb(48, 39, 25), 14, Color.rgb(112, 87, 45))
        }
        warning.addView(text("!", 18, Color.rgb(255, 190, 104), true).apply {
            setPadding(0, 0, dp(10), 0)
        })
        warning.addView(text(
            "Diagnostics are informational. They do not prove Minecraft will launch. No FPS cap is applied here.",
            12, primaryText, false
        ))
        page.addView(warning)
        page.addView(space(14))

        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(rows)
        page.addView(space(14))
        page.addView(actionButton("REFRESH CHECKS") { renderReport() })
        page.addView(space(8))
        page.addView(actionButton("COPY REPORT") { copyReport() })
        page.addView(space(12))
        page.addView(text(
            "Privacy: this report stays on the device unless you explicitly copy and share it.",
            12, secondaryText, false
        ))
        root.addView(LauncherNavigationBar.create(this, "Store"))
        setContentView(root)
        renderReport()
    }

    override fun onResume() {
        super.onResume()
        LauncherPreferences.applyWindowFlags(this)
    }

    private fun renderReport() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memory)
        val stat = StatFs(filesDir.absolutePath)
        val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
        val totalBytes = stat.blockCountLong * stat.blockSizeLong
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
        val abis = Build.SUPPORTED_ABIS.joinToString().ifBlank { "unknown" }
        val report = listOf(
            "App version" to version,
            "Android version" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "Device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Supported ABIs" to abis,
            "RAM total" to formatBytes(memory.totalMem),
            "RAM currently available" to formatBytes(memory.availMem),
            "Low-memory state" to if (memory.lowMemory) "Yes" else "No",
            "App storage volume total" to formatBytes(totalBytes),
            "App storage volume available" to formatBytes(freeBytes),
            "App files directory" to filesDir.absolutePath,
            "Minecraft runtime" to "Not connected in this independent shell",
            "FPS cap" to "No launcher-imposed cap configured",
            "World/game data changed" to "No"
        )
        lastReport = report.joinToString("\n") { "${it.first}: ${it.second}" }
        rows.removeAllViews()
        report.forEachIndexed { index, item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = rounded(surface, 14, border)
            }
            card.addView(text(item.first.uppercase(Locale.ROOT), 10, secondaryText, true))
            card.addView(text(item.second, 14, primaryText, false).apply {
                setPadding(0, dp(5), 0, 0)
            })
            rows.addView(card)
            if (index != report.lastIndex) rows.addView(space(7))
        }
    }

    private fun copyReport() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("VoidCraft diagnostics", lastReport))
        Toast.makeText(this, "Report copied. Review it before sharing.", Toast.LENGTH_LONG).show()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "${bytes} B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = -1
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024.0
            unit++
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit])
    }

    private fun actionButton(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 12f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        gravity = Gravity.CENTER
        setTextColor(primaryText)
        background = rounded(panel, 13, border)
        setPadding(dp(14), dp(13), dp(14), dp(13))
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
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
}
