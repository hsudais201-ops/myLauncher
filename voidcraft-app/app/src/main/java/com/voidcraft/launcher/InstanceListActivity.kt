package com.voidcraft.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.voidcraft.launcher.core.FileInstanceCatalogRepository
import com.voidcraft.launcher.core.InstanceProfile

/**
 * Functional, metadata-only profile screen for the independent VoidCraft shell.
 * Profile creation does not install game files; removal never deletes files.
 */
class InstanceListActivity : Activity() {
    private val background = Color.rgb(10, 14, 24)
    private val panel = Color.rgb(23, 33, 52)
    private val accent = Color.rgb(139, 124, 255)
    private val primaryText = Color.rgb(244, 246, 255)
    private val secondaryText = Color.rgb(166, 176, 199)

    private lateinit var repository: FileInstanceCatalogRepository
    private lateinit var profileList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = FileInstanceCatalogRepository(filesDir)
        window.statusBarColor = background
        window.navigationBarColor = background
        window.decorView.systemUiVisibility = 0
        LauncherPreferences.applyWindowFlags(this)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(this@InstanceListActivity.background)
            isFillViewport = true
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
        }
        scroll.addView(page)

        page.addView(label("INSTANCES", 25, primaryText, true))
        page.addView(label(
            "Create and manage profile metadata. These profiles do not install Minecraft or launch the game yet.",
            14, secondaryText, false
        ).apply { setPadding(0, dp(8), 0, dp(16)) })

        page.addView(actionButton("Create profile metadata") { showCreateDialog() })
        page.addView(space(8))
        page.addView(actionButton("Refresh list") { refreshProfiles() })
        page.addView(space(8))
        page.addView(actionButton("Back to dashboard") { finish() })
        page.addView(space(18))

        profileList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        page.addView(profileList)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(this@InstanceListActivity.background)
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(LauncherNavigationBar.create(this, "Instances"))
        setContentView(root)
        refreshProfiles()
    }

    override fun onResume() {
        super.onResume()
        LauncherPreferences.applyWindowFlags(this)
        if (::profileList.isInitialized && ::repository.isInitialized) refreshProfiles()
    }

    private fun refreshProfiles() {
        profileList.removeAllViews()
        val profiles = try {
            repository.list()
        } catch (error: Exception) {
            profileList.addView(label(
                "Could not read the instance catalog. It was not reset. Details: ${error.message ?: "unknown error"}",
                14, Color.rgb(255, 175, 175), false
            ))
            return
        }

        if (profiles.isEmpty()) {
            profileList.addView(card().apply {
                addView(label("No profiles yet", 18, primaryText, true))
                addView(label(
                    "Create a profile to save its name, Minecraft version, and loader choice. Game files will not be downloaded.",
                    14, secondaryText, false
                ).apply { setPadding(0, dp(8), 0, 0) })
            })
            return
        }

        profiles.forEach { profile ->
            val item = card()
            item.addView(label(profile.name, 18, primaryText, true))
            item.addView(label(
                "Minecraft ${profile.minecraftVersion} • Loader: ${profile.loaderId}",
                14, secondaryText, false
            ).apply { setPadding(0, dp(6), 0, 0) })
            item.addView(label(
                "Metadata only — runtime and downloads are not connected.",
                12, secondaryText, false
            ).apply { setPadding(0, dp(5), 0, dp(12)) })
            item.addView(actionButton("Remove from list") { confirmRemove(profile) })
            profileList.addView(item)
            profileList.addView(space(10))
        }
    }

    private fun showCreateDialog() {
        val nameField = editField("Profile name")
        val versionField = editField("Minecraft version (for example, 1.21.4)")
        val loaderField = editField("Loader ID: vanilla, fabric, forge…").apply {
            setText("vanilla")
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), 0)
            addView(nameField, fieldParams())
            addView(versionField, fieldParams())
            addView(loaderField, fieldParams())
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Create profile metadata")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save profile", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val profile = InstanceProfile.new(
                        name = nameField.text.toString(),
                        minecraftVersion = versionField.text.toString(),
                        loaderId = loaderField.text.toString()
                    )
                    repository.upsert(profile)
                    dialog.dismiss()
                    refreshProfiles()
                    notice("Profile metadata saved. Minecraft files were not installed.")
                } catch (error: Exception) {
                    notice(error.message ?: "Profile could not be saved.")
                }
            }
        }
        dialog.show()
    }

    private fun confirmRemove(profile: InstanceProfile) {
        AlertDialog.Builder(this)
            .setTitle("Remove profile from list?")
            .setMessage(
                "This removes only \"${profile.name}\" from the VoidCraft metadata catalog. " +
                    "It does not delete any game files, saves, mods, or worlds."
            )
            .setNegativeButton("Keep profile", null)
            .setPositiveButton("Remove from list") { _, _ ->
                try {
                    repository.remove(profile.id)
                    refreshProfiles()
                    notice("Profile removed from the list. Game files were left untouched.")
                } catch (error: Exception) {
                    notice(error.message ?: "Profile could not be removed.")
                }
            }
            .show()
    }

    private fun editField(hintText: String) = EditText(this).apply {
        hint = hintText
        textSize = 15f
        setTextColor(primaryText)
        setHintTextColor(secondaryText)
        inputType = InputType.TYPE_CLASS_TEXT
        isSingleLine = true
    }

    private fun fieldParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(8) }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(panel, 16)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun label(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD)
        else Typeface.create("sans-serif", Typeface.NORMAL)
        gravity = Gravity.START
        includeFontPadding = true
    }

    private fun actionButton(value: String, onClick: () -> Unit) = Button(this).apply {
        text = value
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = rounded(accent, 12)
        setPadding(dp(12), dp(5), dp(12), dp(5))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setOnClickListener { onClick() }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun notice(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
