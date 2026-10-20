package com.voidcraft.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.voidcraft.launcher.core.FileInstanceCatalogRepository
import com.voidcraft.launcher.core.InstanceProfile
import com.voidcraft.launcher.core.MinecraftVersionCatalog
import com.voidcraft.launcher.core.MinecraftVersionCatalogClient
import com.voidcraft.launcher.core.MinecraftVersionDetails
import com.voidcraft.launcher.core.MinecraftVersionEntry
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Browses official Mojang version metadata. It does not install game files.
 * Selecting "Create profile metadata" only records a desired version/profile.
 */
class VersionBrowserActivity : Activity() {
    private val background = Color.rgb(10, 14, 24)
    private val panel = Color.rgb(23, 33, 52)
    private val accent = Color.rgb(139, 124, 255)
    private val primaryText = Color.rgb(244, 246, 255)
    private val secondaryText = Color.rgb(166, 176, 199)

    private val handler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val client = MinecraftVersionCatalogClient()
    private lateinit var repository: FileInstanceCatalogRepository
    private lateinit var statusLabel: TextView
    private lateinit var latestLabel: TextView
    private lateinit var searchField: EditText
    private lateinit var versionList: ListView
    private lateinit var versionAdapter: VersionAdapter
    private val filterButtons = mutableListOf<Pair<Button, String?>>()

    private var catalog: MinecraftVersionCatalog? = null
    private var visibleEntries: List<MinecraftVersionEntry> = emptyList()
    private var activeTypeFilter: String? = null
    private var loadGeneration = 0
    private var detailsGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = FileInstanceCatalogRepository(filesDir)
        window.statusBarColor = background
        window.navigationBarColor = background
        window.decorView.systemUiVisibility = 0
        LauncherPreferences.applyWindowFlags(this)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(18))
            setBackgroundColor(this@VersionBrowserActivity.background)
        }
        page.addView(label("MINECRAFT VERSIONS", 23, primaryText, true))
        page.addView(label(
            "Official Mojang Java Edition metadata. Browsing does not install or launch a version.",
            13, secondaryText, false
        ).apply { setPadding(0, dp(6), 0, dp(8)) })

        latestLabel = label("Latest release and snapshot: loading…", 13, accent, true)
        page.addView(latestLabel)
        searchField = EditText(this).apply {
            hint = "Search version IDs"
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            setTextColor(primaryText)
            setHintTextColor(secondaryText)
            background = rounded(this@VersionBrowserActivity.panel, 12)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10); bottomMargin = dp(8) }
        }
        page.addView(searchField)

        val filterScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addFilterButton(filterRow, "All", null)
        addFilterButton(filterRow, "Releases", "release")
        addFilterButton(filterRow, "Snapshots", "snapshot")
        addFilterButton(filterRow, "Old betas", "old_beta")
        addFilterButton(filterRow, "Old alphas", "old_alpha")
        filterScroll.addView(filterRow)
        page.addView(filterScroll)

        statusLabel = label("Loading the official version list…", 13, secondaryText, false)
            .apply { setPadding(0, dp(8), 0, dp(8)) }
        page.addView(statusLabel)

        versionAdapter = VersionAdapter()
        versionList = ListView(this).apply {
            adapter = versionAdapter
            setBackgroundColor(this@VersionBrowserActivity.background)
            divider = rounded(Color.TRANSPARENT, 1)
            dividerHeight = dp(8)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        page.addView(versionList)

        page.addView(actionButton("Refresh version list") { loadCatalog() }.apply {
            layoutParams = buttonParams().apply { topMargin = dp(8) }
        })
        page.addView(actionButton("Back to dashboard") { finish() }.apply {
            layoutParams = buttonParams().apply { topMargin = dp(5) }
        })
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(this@VersionBrowserActivity.background)
        }
        root.addView(page, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(LauncherNavigationBar.create(this, "Versions"))
        setContentView(root)

        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = updateVisibleEntries()
        })
        versionList.setOnItemClickListener { _, _, position, _ ->
            if (position in visibleEntries.indices) showVersionDetails(visibleEntries[position])
        }
        updateFilterStyles()
        loadCatalog()
    }

    override fun onResume() {
        super.onResume()
        LauncherPreferences.applyWindowFlags(this)
    }

    override fun onDestroy() {
        loadGeneration += 1
        detailsGeneration += 1
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun loadCatalog() {
        val generation = ++loadGeneration
        statusLabel.text = "Loading official Mojang version metadata…"
        executor.execute {
            try {
                val loaded = client.fetchCatalog()
                handler.post {
                    if (isFinishing || isDestroyed || generation != loadGeneration) return@post
                    catalog = loaded
                    latestLabel.text = "Latest release: " + loaded.latest.release +
                        "   •   Latest snapshot: " + loaded.latest.snapshot
                    updateVisibleEntries()
                }
            } catch (error: Exception) {
                handler.post {
                    if (isFinishing || isDestroyed || generation != loadGeneration) return@post
                    statusLabel.text = if (catalog == null) {
                        "Could not load the version list. Check your connection and refresh. " +
                            (error.message ?: "")
                    } else {
                        "Refresh failed; showing the last loaded list. " + (error.message ?: "")
                    }
                    updateVisibleEntries()
                }
            }
        }
    }

    private fun addFilterButton(row: LinearLayout, title: String, type: String?) {
        val button = actionButton(title) {
            activeTypeFilter = type
            updateFilterStyles()
            updateVisibleEntries()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6) }
        }
        filterButtons.add(button to type)
        row.addView(button)
    }

    private fun updateFilterStyles() {
        filterButtons.forEach { (button, type) ->
            button.background = rounded(
                if (type == activeTypeFilter) accent else panel,
                10
            )
        }
    }

    private fun updateVisibleEntries() {
        val loaded = catalog
        if (loaded == null) {
            versionAdapter.replace(emptyList())
            if (!statusLabel.text.toString().startsWith("Could not load") &&
                !statusLabel.text.toString().startsWith("Refresh failed")) {
                statusLabel.text = "Loading official Mojang version metadata…"
            }
            return
        }

        val query = searchField.text.toString().trim().lowercase()
        visibleEntries = loaded.versions.filter { entry ->
            (activeTypeFilter == null || entry.type == activeTypeFilter) &&
                (query.isEmpty() || entry.id.lowercase().contains(query) ||
                    entry.type.lowercase().contains(query))
        }
        versionAdapter.replace(visibleEntries)
        statusLabel.text = visibleEntries.size.toString() + " versions shown • tap a version for metadata"
    }

    private fun showVersionDetails(entry: MinecraftVersionEntry) {
        val generation = ++detailsGeneration
        statusLabel.text = "Loading metadata for " + entry.id + "…"
        executor.execute {
            try {
                val details = client.fetchDetails(entry)
                handler.post {
                    if (isFinishing || isDestroyed || generation != detailsGeneration) return@post
                    statusLabel.text = visibleEntries.size.toString() +
                        " versions shown • tap a version for metadata"
                    showDetailsDialog(entry, details)
                }
            } catch (error: Exception) {
                handler.post {
                    if (isFinishing || isDestroyed || generation != detailsGeneration) return@post
                    statusLabel.text = "Could not load details for " + entry.id
                    notice("Version metadata failed: " + (error.message ?: "unknown error"))
                }
            }
        }
    }

    private fun showDetailsDialog(entry: MinecraftVersionEntry, details: MinecraftVersionDetails) {
        val message = listOf(
            "Type: " + details.type,
            "Release time: " + entry.releaseTime,
            "Main class: " + (details.mainClass ?: "not provided"),
            "Inherits from: " + (details.inheritsFrom ?: "none"),
            "Java major version: " + (details.javaMajorVersion?.toString() ?: "not specified"),
            "Client download: " + (details.clientDownload?.url ?: "not provided"),
            "Client size: " + (details.clientDownload?.sizeBytes?.toString() ?: "not specified") + " bytes",
            "Asset index: " + (details.assetIndex?.id ?: "not provided"),
            "",
            "This screen only reads metadata. A profile created here will not download game files or launch Minecraft."
        ).joinToString("\n")

        val dialog = AlertDialog.Builder(this)
            .setTitle(entry.id)
            .setMessage(message)
            .setNegativeButton("Close", null)
            .setNeutralButton("Create profile metadata", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                dialog.dismiss()
                showCreateProfileDialog(entry)
            }
        }
        dialog.show()
    }

    private fun showCreateProfileDialog(entry: MinecraftVersionEntry) {
        val nameInput = EditText(this).apply {
            hint = "Profile name"
            setText("Minecraft " + entry.id)
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            setTextColor(primaryText)
            setHintTextColor(secondaryText)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Create profile metadata")
            .setMessage(
                "This saves a profile record for " + entry.id +
                    ". Minecraft files will not be downloaded."
            )
            .setView(nameInput)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save profile metadata", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val profile = InstanceProfile.new(
                        name = nameInput.text.toString(),
                        minecraftVersion = entry.id,
                        loaderId = "vanilla"
                    )
                    repository.upsert(profile)
                    dialog.dismiss()
                    notice("Profile metadata saved. Version files are not installed.")
                } catch (error: Exception) {
                    notice(error.message ?: "Profile metadata could not be saved.")
                }
            }
        }
        dialog.show()
    }

    private inner class VersionAdapter : BaseAdapter() {
        private var entries: List<MinecraftVersionEntry> = emptyList()

        fun replace(updated: List<MinecraftVersionEntry>) {
            entries = updated
            notifyDataSetChanged()
        }

        override fun getCount(): Int = entries.size
        override fun getItem(position: Int): MinecraftVersionEntry = entries[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView as? LinearLayout ?: LinearLayout(this@VersionBrowserActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label("", 16, primaryText, true))
                addView(label("", 12, secondaryText, false).apply { setPadding(0, dp(4), 0, 0) })
            }
            row.setPadding(dp(14), dp(12), dp(14), dp(12))
            row.background = rounded(panel, 14)
            val entry = entries[position]
            (row.getChildAt(0) as TextView).text = entry.id
            (row.getChildAt(1) as TextView).text =
                entry.type.replace('_', ' ').uppercase() + "  •  " + entry.releaseTime
            return row
        }
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
        setPadding(dp(10), dp(4), dp(10), dp(4))
        setOnClickListener { onClick() }
    }

    private fun buttonParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun notice(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
