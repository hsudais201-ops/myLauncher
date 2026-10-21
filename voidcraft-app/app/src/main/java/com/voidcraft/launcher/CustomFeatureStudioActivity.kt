package com.voidcraft.launcher

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local no-code feature blueprint editor. Blueprints are specifications only:
 * they are not Kotlin, scripts, plugins, or executable packages.
 */
class CustomFeatureStudioActivity : Activity() {
    private val bg = Color.rgb(10, 14, 24)
    private val surface = Color.rgb(18, 25, 40)
    private val panel = Color.rgb(23, 33, 52)
    private val fg = Color.rgb(243, 246, 255)
    private val muted = Color.rgb(157, 174, 199)
    private val accent by lazy { LauncherPreferences.accentColor(this) }
    private val drafts = mutableListOf<JSONObject>()
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = surface
        LauncherPreferences.applyWindowFlags(this)
        loadDrafts()

        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }
        scroll.addView(page)
        page.addView(actionText("‹  Back", 15, accent, true) { finish() })
        page.addView(space(16))
        page.addView(text("CREATE YOUR OWN", 12, accent, true))
        page.addView(text("Feature Studio", 29, fg, true))
        page.addView(text(
            "Design your own add-on blueprint. Your drafts are saved on this device and can be edited later.",
            14, muted, false
        ))
        page.addView(space(16))

        val safety = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(Color.rgb(19, 44, 52), 14)
        }
        safety.addView(text("SAFE BY DEFAULT", 12, Color.rgb(51, 216, 190), true))
        safety.addView(text(
            "A blueprint is not executable code. It cannot access game files, use the network, or install itself. Running or publishing a real feature requires a separate reviewed implementation and security checks.",
            12, muted, false
        ))
        page.addView(safety)
        page.addView(space(18))
        page.addView(actionText("+  Create feature blueprint", 15, fg, true) { showEditor(null) }.apply {
            setPadding(dp(15), dp(16), dp(15), dp(16))
            background = rounded(panel, 14)
        })
        page.addView(space(20))
        page.addView(text("YOUR BLUEPRINTS", 17, fg, true))
        page.addView(space(8))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(list)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(LauncherNavigationBar.create(this, "Store"))
        setContentView(root)
        renderDrafts()
    }

    private fun showEditor(existing: JSONObject?) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), 0)
        }
        val name = field("Feature name", existing?.optString("name", "") ?: "")
        val description = field("What should it do?", existing?.optString("description", "") ?: "", true)
        val template = field("Template (e.g. dashboard, utility, visual tool)", existing?.optString("template", "utility") ?: "utility")
        val behavior = field("Steps / design notes (no code)", existing?.optString("notes", "") ?: "", true)
        form.addView(label("NAME")); form.addView(name); form.addView(space(8))
        form.addView(label("DESCRIPTION")); form.addView(description); form.addView(space(8))
        form.addView(label("STARTING TEMPLATE")); form.addView(template); form.addView(space(8))
        form.addView(label("HOW IT SHOULD WORK")); form.addView(behavior)
        val scroller = ScrollView(this).apply { addView(form) }
        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "New feature blueprint" else "Edit blueprint")
            .setView(scroller)
            .setNegativeButton("Cancel", null)
            .setNeutralButton(if (existing == null) "Save draft" else "Save changes") { _, _ ->
                val n = name.text.toString().trim()
                val d = description.text.toString().trim()
                val t = template.text.toString().trim().ifBlank { "utility" }
                val b = behavior.text.toString().trim()
                if (n.isBlank() || d.isBlank()) {
                    Toast.makeText(this, "Add a feature name and description first.", Toast.LENGTH_LONG).show()
                    return@setNeutralButton
                }
                if (n.length > 60 || d.length > 500 || b.length > 3000 || t.length > 40) {
                    Toast.makeText(this, "Please shorten one of the fields.", Toast.LENGTH_LONG).show()
                    return@setNeutralButton
                }
                val item = existing ?: JSONObject().put("id", java.util.UUID.randomUUID().toString())
                item.put("name", n).put("description", d).put("template", t).put("notes", b)
                item.put("status", "Draft • not executable")
                if (existing == null) drafts.add(item)
                saveDrafts()
                renderDrafts()
                Toast.makeText(this, "Blueprint saved on this device.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun renderDrafts() {
        list.removeAllViews()
        if (drafts.isEmpty()) {
            list.addView(text("No custom blueprints yet. Create one to get started.", 13, muted, false))
            return
        }
        drafts.toList().forEach { item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(14), dp(14), dp(14))
                background = rounded(surface, 14)
            }
            card.addView(text(item.optString("name"), 16, fg, true))
            card.addView(text(item.optString("description"), 12, muted, false))
            card.addView(space(6))
            card.addView(text("• " + item.optString("template") + "  •  Draft only", 11, accent, true))
            card.addView(space(8))
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(actionText("EDIT", 11, fg, true) { showEditor(item) }.apply {
                setPadding(dp(12), dp(10), dp(18), dp(10))
            })
            actions.addView(actionText("SHARE", 11, accent, true) {
                val export = JSONObject()
                    .put("format", "voidcraft-feature-blueprint")
                    .put("version", 1)
                    .put("blueprint", JSONObject(item.toString()))
                    .toString(2)
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "VoidCraft feature blueprint: " + item.optString("name"))
                    putExtra(android.content.Intent.EXTRA_TEXT, export)
                }
                startActivity(android.content.Intent.createChooser(intent, "Share blueprint"))
            }.apply { setPadding(dp(12), dp(10), dp(18), dp(10)) })
            actions.addView(actionText("DELETE", 11, Color.rgb(255, 170, 170), true) {
                AlertDialog.Builder(this).setTitle("Delete blueprint?")
                    .setMessage("This only deletes this saved design draft.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        drafts.removeAll { it.optString("id") == item.optString("id") }
                        saveDrafts()
                        renderDrafts()
                    }.show()
            }.apply { setPadding(dp(12), dp(10), dp(12), dp(10)) })
            card.addView(actions)
            list.addView(card)
            list.addView(space(10))
        }
    }

    private fun loadDrafts() {
        val raw = getSharedPreferences("voidcraft_custom_feature_drafts", MODE_PRIVATE)
            .getString("drafts", "[]") ?: "[]"
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) drafts.add(array.getJSONObject(i))
        } catch (_: Exception) {
            drafts.clear()
        }
    }

    private fun saveDrafts() {
        val array = JSONArray()
        drafts.forEach { array.put(it) }
        getSharedPreferences("voidcraft_custom_feature_drafts", MODE_PRIVATE)
            .edit().putString("drafts", array.toString()).apply()
    }

    private fun field(hintText: String, value: String, multiline: Boolean = false) =
        EditText(this).apply {
            hint = hintText
            setText(value)
            textSize = 14f
            setTextColor(fg)
            setHintTextColor(muted)
            inputType = if (multiline) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = if (multiline) 3 else 1
            background = rounded(panel, 10)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

    private fun label(value: String) = text(value, 10, accent, true)
    private fun actionText(value: String, size: Int, color: Int, bold: Boolean, action: () -> Unit) =
        text(value, size, color, bold).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
    private fun text(value: String, size: Int, color: Int, bold: Boolean) =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.BOLD)
                else Typeface.create("sans-serif", Typeface.NORMAL)
            setPadding(0, dp(3), 0, dp(3))
        }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }
    private fun space(height: Int) = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
