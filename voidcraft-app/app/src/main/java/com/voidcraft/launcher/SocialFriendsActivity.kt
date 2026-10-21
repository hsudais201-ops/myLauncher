package com.voidcraft.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

/** Real server-backed VoidCraft friends and request list. No sample users are inserted. */
class SocialFriendsActivity : Activity() {
    private val background = Color.rgb(10, 14, 24)
    private val surface = Color.rgb(18, 25, 40)
    private val panel = Color.rgb(23, 33, 52)
    private val accent = Color.rgb(139, 124, 255)
    private val mint = Color.rgb(51, 216, 190)
    private val primaryText = Color.rgb(243, 246, 255)
    private val secondaryText = Color.rgb(157, 174, 199)
    private val border = Color.rgb(42, 58, 85)
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var statusLabel: TextView
    private lateinit var searchField: EditText
    private lateinit var searchResults: LinearLayout
    private lateinit var socialContent: LinearLayout
    private var friends: List<VoidCraftFriend> = emptyList()
    private var requests: List<VoidCraftFriendRequest> = emptyList()
    private var conversations: List<VoidCraftConversation> = emptyList()
    private var foundPlayers: List<VoidCraftFriend> = emptyList()
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = surface
        LauncherPreferences.applyWindowFlags(this)
        setContentView(buildScreen())
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(background)
        }
        val scroll = ScrollView(this).apply { clipToPadding = false }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(28))
        }
        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        page.addView(topBar())
        page.addView(space(22))
        page.addView(label("VOIDCRAFT SOCIAL", 11, accent, true))
        page.addView(space(6))
        page.addView(label("Friends & messages", 28, primaryText, true))
        page.addView(space(7))
        page.addView(label(
            "Find players, manage requests, and open private conversations with accepted friends.",
            13, secondaryText, false
        ))
        page.addView(space(13))
        statusLabel = label("Connecting to VoidCraft account service…", 12, secondaryText, false)
        page.addView(statusLabel)
        page.addView(space(14))

        val searchCard = card()
        searchCard.addView(label("FIND A PLAYER", 10, accent, true))
        searchCard.addView(space(8))
        searchField = field("Enter at least 3 characters of a username")
        searchCard.addView(searchField)
        searchCard.addView(space(9))
        searchCard.addView(actionButton("Search VoidCraft players", accent) { searchPlayers() })
        searchCard.addView(space(8))
        searchResults = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        searchCard.addView(searchResults)
        page.addView(searchCard)
        page.addView(space(20))

        page.addView(sectionTitle("FRIEND REQUESTS"))
        page.addView(space(9))
        socialContent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(socialContent)

        root.addView(LauncherNavigationBar.create(this, "Social"))
        renderSearchResults()
        return root
    }

    private fun refreshData() {
        if (!::socialContent.isInitialized || loading) return
        val api = SocialApiClient(applicationContext)
        if (!api.isSignedIn) {
            statusLabel.text = "Sign in with your VoidCraft website account to load real friends and requests."
            socialContent.removeAllViews()
            socialContent.addView(emptyState(
                "Sign-in required",
                "Your friend list and messages belong to your VoidCraft account, not Discord or the local launcher profile."
            ))
            socialContent.addView(space(10))
            socialContent.addView(actionButton("Sign in / create VoidCraft account", accent) {
                startActivityForResult(Intent(this, VoidCraftAccountActivity::class.java), REQUEST_ACCOUNT)
            })
            searchResults.removeAllViews()
            return
        }

        loading = true
        statusLabel.text = "Loading friends and requests…"
        executor.execute {
            val result = runCatching {
                Triple(api.friends(), api.friendRequests(), api.conversations())
            }
            runOnUiThread {
                loading = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { (loadedFriends, loadedRequests, loadedConversations) ->
                    friends = loadedFriends
                    requests = loadedRequests
                    conversations = loadedConversations
                    statusLabel.text = "Signed-in account • " + friends.size + " friend(s) • " +
                        requests.count { it.direction == "incoming" } + " incoming request(s) • " +
                        conversations.size + " recent chat(s)"
                    statusLabel.setTextColor(mint)
                    renderSocialContent()
                    renderSearchResults()
                }.onFailure { error ->
                    statusLabel.text = "Could not load social data."
                    statusLabel.setTextColor(Color.rgb(255, 190, 104))
                    socialContent.removeAllViews()
                    socialContent.addView(emptyState("Could not connect", error.message ?: "Try again."))
                    socialContent.addView(space(10))
                    socialContent.addView(actionButton("Retry", accent) { refreshData() })
                }
            }
        }
    }

    private fun searchPlayers() {
        if (loading) return
        val query = searchField.text.toString().trim()
        if (query.length < 3) {
            notice("Enter at least 3 characters.")
            return
        }
        if (!SocialApiClient(applicationContext).isSignedIn) {
            notice("Sign in to your VoidCraft account first.")
            return
        }
        searchResults.removeAllViews()
        searchResults.addView(label("Searching…", 12, secondaryText, false))
        executor.execute {
            val result = runCatching { SocialApiClient(applicationContext).searchPlayers(query) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { foundPlayers = it }
                    .onFailure {
                        foundPlayers = emptyList()
                        notice(it.message ?: "Player search failed.")
                    }
                renderSearchResults()
            }
        }
    }

    private fun renderSocialContent() {
        socialContent.removeAllViews()
        val incoming = requests.filter { it.direction == "incoming" }
        val outgoing = requests.filter { it.direction == "outgoing" }

        if (incoming.isEmpty() && outgoing.isEmpty()) {
            socialContent.addView(emptyState(
                "No pending requests",
                "When another VoidCraft player sends you a friend request, it will appear here."
            ))
        }
        incoming.forEach { request ->
            socialContent.addView(requestCard(request))
            socialContent.addView(space(8))
        }
        outgoing.forEach { request ->
            socialContent.addView(infoCard(
                request.displayName + "  @" + request.username,
                "Friend request sent • waiting for a response",
                mint
            ))
            socialContent.addView(space(8))
        }

        socialContent.addView(space(12))
        socialContent.addView(sectionTitle("RECENT MESSAGES"))
        socialContent.addView(space(9))
        if (conversations.isEmpty()) {
            socialContent.addView(emptyState(
                "No conversations yet",
                "Once you and an accepted VoidCraft friend exchange a message, the latest conversation will appear here."
            ))
        } else {
            conversations.forEach { conversation ->
                socialContent.addView(conversationCard(conversation))
                socialContent.addView(space(8))
            }
        }

        socialContent.addView(space(12))
        socialContent.addView(sectionTitle("YOUR FRIENDS"))
        socialContent.addView(space(9))
        if (friends.isEmpty()) {
            socialContent.addView(emptyState(
                "Your list is empty",
                "Search for a username above and send a friend request to start building your VoidCraft list."
            ))
        } else {
            friends.forEach { friend ->
                socialContent.addView(friendCard(friend))
                socialContent.addView(space(9))
            }
        }
    }

    private fun renderSearchResults() {
        if (!::searchResults.isInitialized) return
        searchResults.removeAllViews()
        if (foundPlayers.isEmpty()) return
        val existingIds = friends.map { it.id }.toSet()
        foundPlayers.forEach { player ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(11), dp(11), dp(11), dp(11))
                background = rounded(surface, 12, border)
            }
            row.addView(label(player.displayName + "  @" + player.username, 14, primaryText, true))
            if (player.bio.isNotBlank()) {
                row.addView(space(3))
                row.addView(label(player.bio, 11, secondaryText, false))
            }
            row.addView(space(8))
            row.addView(actionButton(
                if (existingIds.contains(player.id)) "Open conversation" else "Send friend request",
                if (existingIds.contains(player.id)) panel else accent
            ) {
                if (existingIds.contains(player.id)) openChat(player)
                else sendRequest(player.username)
            })
            searchResults.addView(row)
            searchResults.addView(space(7))
        }
    }

    private fun requestCard(request: VoidCraftFriendRequest): View {
        val box = card()
        box.addView(label(request.displayName + "  @" + request.username, 14, primaryText, true))
        box.addView(space(5))
        box.addView(label("Wants to connect with you on VoidCraft.", 12, secondaryText, false))
        box.addView(space(10))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(actionButton("Accept", accent) { respondToRequest(request.id, accept = true) },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })
        row.addView(actionButton("Decline", panel) { respondToRequest(request.id, accept = false) },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(5) })
        box.addView(row)
        return box
    }

    private fun conversationCard(conversation: VoidCraftConversation): View {
        val box = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(label(conversation.displayName, 14, primaryText, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(label("@" + conversation.username, 10, mint, true))
        box.addView(row)
        box.addView(space(6))
        box.addView(label(conversation.lastMessage.take(160), 12, secondaryText, false))
        box.addView(space(9))
        box.addView(actionButton("Open private chat", accent) {
            openChat(VoidCraftFriend(
                conversation.userId, conversation.username, conversation.displayName,
                "", "member", conversation.lastMessageAt
            ))
        })
        return box
    }

    private fun friendCard(friend: VoidCraftFriend): View {
        val box = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val mark = label(friend.username.take(1).uppercase(), 18, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = gradient(accent, Color.rgb(33, 173, 166), 13)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        }
        row.addView(mark)
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), 0, 0, 0)
        }
        details.addView(label(friend.displayName, 15, primaryText, true))
        details.addView(label("@" + friend.username, 12, secondaryText, false).apply {
            setPadding(0, dp(3), 0, 0)
        })
        row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(row)
        box.addView(space(11))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(actionButton("Message", accent) { openChat(friend) },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(5) })
        actions.addView(actionButton("Remove", panel) { confirmRemoveFriend(friend) },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(5) })
        box.addView(actions)
        return box
    }

    private fun sendRequest(username: String) {
        runApiAction("Sending friend request…", {
            SocialApiClient(applicationContext).sendFriendRequest(username)
        }) {
            foundPlayers = emptyList()
            renderSearchResults()
            refreshData()
        }
    }

    private fun respondToRequest(requestId: Long, accept: Boolean) {
        runApiAction(if (accept) "Accepting request…" else "Declining request…", {
            val api = SocialApiClient(applicationContext)
            if (accept) api.acceptFriendRequest(requestId) else api.declineFriendRequest(requestId)
        }) { refreshData() }
    }

    private fun confirmRemoveFriend(friend: VoidCraftFriend) {
        AlertDialog.Builder(this)
            .setTitle("Remove @" + friend.username + "?")
            .setMessage("This removes the friendship. It does not delete your VoidCraft account.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->
                runApiAction("Removing friend…", {
                    SocialApiClient(applicationContext).removeFriend(friend.id)
                }) { refreshData() }
            }
            .show()
    }

    private fun runApiAction(
        progress: String,
        action: () -> Unit,
        done: () -> Unit
    ) {
        if (loading) return
        loading = true
        statusLabel.text = progress
        executor.execute {
            val result = runCatching { action() }
            runOnUiThread {
                loading = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess {
                    notice("VoidCraft social data updated.")
                    done()
                }.onFailure { error ->
                    statusLabel.text = "Action failed."
                    notice(error.message ?: "Could not complete that action.")
                }
            }
        }
    }

    private fun openChat(friend: VoidCraftFriend) {
        startActivity(Intent(this, SocialChatActivity::class.java).apply {
            putExtra(SocialChatActivity.EXTRA_USER_ID, friend.id)
            putExtra(SocialChatActivity.EXTRA_USERNAME, friend.username)
            putExtra(SocialChatActivity.EXTRA_DISPLAY_NAME, friend.displayName)
        })
    }

    private fun topBar(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(label("VC", 17, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = gradient(accent, Color.rgb(79, 101, 245), 13)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        })
        row.addView(label("VOIDCRAFT SOCIAL", 14, primaryText, true).apply {
            setPadding(dp(10), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(actionButton("Account", panel) {
            startActivityForResult(Intent(this, VoidCraftAccountActivity::class.java), REQUEST_ACCOUNT)
        })
        return row
    }

    private fun sectionTitle(value: String) = label(value, 11, secondaryText, true)

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = rounded(panel, 16, border)
    }

    private fun infoCard(title: String, description: String, statusColor: Int) = card().apply {
        addView(label(title, 14, primaryText, true))
        addView(space(5))
        addView(label(description, 12, statusColor, false))
    }

    private fun emptyState(title: String, description: String) = card().apply {
        addView(label(title, 14, primaryText, true))
        addView(space(6))
        addView(label(description, 12, secondaryText, false))
    }

    private fun field(hintText: String) = EditText(this).apply {
        hint = hintText
        textSize = 14f
        isSingleLine = true
        setTextColor(primaryText)
        setHintTextColor(secondaryText)
        background = rounded(surface, 12, border)
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun actionButton(textValue: String, color: Int, action: () -> Unit) =
        TextView(this).apply {
            text = textValue
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(12), dp(10), dp(12))
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
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ACCOUNT && resultCode == RESULT_OK) refreshData()
    }

    companion object {
        private const val REQUEST_ACCOUNT = 217
    }
}
