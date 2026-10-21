package com.voidcraft.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
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
import com.voidcraft.launcher.core.FileInstanceCatalogRepository
import com.voidcraft.launcher.core.InstanceProfile

/** Private notes keyed by profile ID. No game files are read or changed. */
class InstanceNotesActivity : Activity() {
    private val background = Color.rgb(10, 14, 24)
    private val surface = Color.rgb(18, 25, 40)
    private val panel = Color.rgb(23, 33, 52)
    private val accent = Color.rgb(139, 124, 255)
    private val mint = Color.rgb(51, 216, 190)
    private val primaryText = Color.rgb(243, 246, 255)
    private val secondaryText = Color.rgb(157, 174, 199)
    private val border = Color.rgb(42, 58, 85)
    private lateinit var list: LinearLayout
    private val repository by lazy { FileInstanceCatalogRepository(filesDir) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = surface
        LauncherPreferences.applyWindowFlags(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(this@InstanceNotesActivity.background)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(this@InstanceNotesActivity.background)
            isFillViewport = true
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
        page.addView(text("Instance Notes", 26, primaryText, true))
        page.addView(text(
            "Keep private reminders, mod lists, troubleshooting steps, or settings notes for each launcher profile.",
            14, secondaryText, false
        ).apply { setPadding(0, dp(6), 0, dp(16)) })

        val privacy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(Color.rgb(19, 44, 52), 15, Color.rgb(36, 101, 107))
        }
        privacy.addView(text("LOCAL AND PRIVATE", 11, mint, true))
        privacy.addView(text(
            "Notes stay in this app's private storage. They are not uploaded or written into Minecraft files. Uninstalling this optional feature keeps saved notes.",
            12, primaryText, false
        ).apply { setPadding(0, dp(5), 0, 0) })
        page.addView(privacy)
        page.addView(space(16))
        page.addView(actionButton("REFRESH INSTANCES") { renderInstances() })
        page.addView(space(12))
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        page.addView(list)
        root.addView(LauncherNavigationBar.create(this, "Store"))
        setContentView(root)
        renderInstances()
    }

    override fun onResume() {
        super.onResume()
        LauncherPreferences.applyWindowFlags(this)
        if (::list.isInitialized) renderInstances()
    }

    private fun renderInstances() {
        list.removeAllViews()
        val profiles = try {
            repository.list()
        } catch (error: Exception) {
            list.addView(text(
                "Could not read the instance catalog. Nothing was reset. " + (error.message ?: ""),
                13, Color.rgb(255, 175, 175), false
            ))
            return
        }
        if (profiles.isEmpty()) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
                background = rounded(surface, 15, border)
            }
            empty.addView(text("No instances yet", 17, primaryText, true))
            empty.addView(text(
                "Create a profile in Instances first. Notes will appear here when a profile exists.",
                13, secondaryText, false
            ).apply { setPadding(0, dp(6), 0, dp(12)) })
            empty.addView(actionButton("OPEN INSTANCES") {
                startActivity(Intent(this, InstanceListActivity::class.java))
            })
            list.addView(empty)
            return
        }
        profiles.forEach { profile ->
            val note = notePrefs().getString(noteKey(profile.id), "").orEmpty()
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(15), dp(14), dp(15), dp(14))
                background = rounded(surface, 16, border)
            }
            card.addView(text(profile.name, 17, primaryText, true))
            card.addView(text(
                "Minecraft " + profile.minecraftVersion + " • " + profile.loaderId,
                12, secondaryText, false
            ).apply { setPadding(0, dp(4), 0, dp(9)) })
            val preview = if (note.isBlank()) {
                "No notes saved yet."
            } else {
                val oneLine = note.lines().joinToString(" ")
                if (oneLine.length > 150) oneLine.take(150) + "…" else oneLine
            }
            card.addView(text(preview, 13, if (note.isBlank()) secondaryText else primaryText, false)
                .apply { setPadding(0, 0, 0, dp(12)) })
            card.addView(actionButton(if (note.isBlank()) "ADD NOTE" else "EDIT NOTE") {
                showEditor(profile, note)
            })
            list.addView(card)
            list.addView(space(10))
        }
    }

    private fun showEditor(profile: InstanceProfile, saved: String) {
        val editor = EditText(this).apply {
            setText(saved)
            hint = "Private note for " + profile.name
            setTextColor(primaryText)
            setHintTextColor(secondaryText)
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 5
            maxLines = 12
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(panel, 12, border)
        }
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), 0)
            addView(editor, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(text("Up to 5,000 characters. Notes remain on this device.",
                11, secondaryText, false).apply { setPadding(0, dp(7), 0, 0) })
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(profile.name)
            .setView(wrapper)
            .setNegativeButton("Cancel", null)
            .setNeutralButton(if (saved.isBlank()) "Clear" else "Delete note", null)
            .setPositiveButton("Save note", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val value = editor.text.toString().trimEnd()
                if (value.length > MAX_NOTE_LENGTH) {
                    notice("Notes are limited to " + MAX_NOTE_LENGTH + " characters.")
                    return@setOnClickListener
                }
                val editorPrefs = notePrefs().edit()
                if (value.isBlank()) editorPrefs.remove(noteKey(profile.id))
                else editorPrefs.putString(noteKey(profile.id), value)
                if (!editorPrefs.commit()) {
                    notice("Could not save the note. Your previous note was kept.")
                    return@setOnClickListener
                }
                dialog.dismiss()
                renderInstances()
                notice("Note saved locally.")
            }
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                if (saved.isBlank()) {
                    editor.setText("")
                    return@setOnClickListener
                }
                AlertDialog.Builder(this)
                    .setTitle("Delete note?")
                    .setMessage("Only this private note will be removed. The instance and game files remain unchanged.")
                    .setNegativeButton("Keep note", null)
                    .setPositiveButton("Delete note") { _, _ ->
                        val removed = notePrefs().edit().remove(noteKey(profile.id)).commit()
                        if (removed) {
                            dialog.dismiss()
                            renderInstances()
                            notice("Note deleted. Game data was not changed.")
                        } else {
                            notice("Could not delete the note.")
                        }
                    }
                    .show()
            }
        }
        dialog.show()
    }

    private fun notePrefs() = getSharedPreferences(NOTES_PREFS, MODE_PRIVATE)
    private fun noteKey(profileId: String) = "note_" + profileId

    private fun actionButton(value: String, onClick: () -> Unit) = TextView(this).apply {
        text = value
        textSize = 11f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setTextColor(primaryText)
        gravity = Gravity.CENTER
        background = rounded(panel, 11, border)
        setPadding(dp(12), dp(12), dp(12), dp(12))
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
    private fun notice(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    companion object {
        private const val NOTES_PREFS = "voidcraft_instance_notes"
        private const val MAX_NOTE_LENGTH = 5000
    }
}
