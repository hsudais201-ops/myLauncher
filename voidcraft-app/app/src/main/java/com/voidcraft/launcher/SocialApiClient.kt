package com.voidcraft.launcher

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

internal data class VoidCraftSocialUser(
    val id: Long,
    val username: String,
    val displayName: String,
    val bio: String,
    val role: String,
    val createdAt: String,
    val email: String? = null
)

internal data class VoidCraftFriend(
    val id: Long,
    val username: String,
    val displayName: String,
    val bio: String,
    val role: String,
    val createdAt: String
)

internal data class VoidCraftFriendRequest(
    val id: Long,
    val direction: String,
    val username: String,
    val displayName: String,
    val createdAt: String
)

internal data class VoidCraftSocialMessage(
    val id: Long,
    val senderId: Long,
    val recipientId: Long,
    val body: String,
    val senderUsername: String,
    val createdAt: String
)

internal class SocialApiException(
    val statusCode: Int,
    message: String
) : IOException(message)

/**
 * Native client for the same VoidCraft API used by the official website.
 * All methods perform blocking I/O: invoke from a background executor, never the UI thread.
 */
internal class SocialApiClient(context: android.content.Context) {
    private val tokenStore = SocialTokenStore(context.applicationContext)

    val isSignedIn: Boolean
        get() = tokenStore.read() != null

    fun isServiceHealthy(): Boolean = try {
        call("GET", "/health", includeToken = false)
            .get("service")?.asString == "voidcraft-social-api"
    } catch (_: Exception) {
        false
    }

    fun register(email: String, username: String, password: String): VoidCraftSocialUser {
        val body = JsonObject().apply {
            addProperty("email", email.trim())
            addProperty("username", username.trim())
            addProperty("password", password)
        }
        return authenticate("/launcher/auth/register", body)
    }

    fun login(identifier: String, password: String): VoidCraftSocialUser {
        val body = JsonObject().apply {
            addProperty("identifier", identifier.trim())
            addProperty("password", password)
        }
        return authenticate("/launcher/auth/login", body)
    }

    fun currentUser(): VoidCraftSocialUser = parseUser(
        call("GET", "/auth/me").getAsJsonObject("user")
    )

    fun signOut() {
        try {
            if (tokenStore.read() != null) call("POST", "/auth/logout", JsonObject())
        } finally {
            tokenStore.clear()
        }
    }

    fun searchPlayers(query: String): List<VoidCraftFriend> {
        val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        return call("GET", "/players?q=" + encoded).getAsJsonArray("players").mapObjects(::parseFriend)
    }

    fun friends(): List<VoidCraftFriend> =
        call("GET", "/friends").getAsJsonArray("friends").mapObjects(::parseFriend)

    fun friendRequests(): List<VoidCraftFriendRequest> =
        call("GET", "/friends/requests").getAsJsonArray("requests").mapObjects { item ->
            val row = item.asJsonObject
            VoidCraftFriendRequest(
                id = row.get("id").asLong,
                direction = row.get("direction").asString,
                username = row.get("username").asString,
                displayName = row.get("displayName").asString,
                createdAt = row.get("createdAt").asString
            )
        }

    fun sendFriendRequest(username: String) {
        call("POST", "/friends/requests", JsonObject().apply {
            addProperty("username", username.trim())
        })
    }

    fun acceptFriendRequest(requestId: Long) {
        call("POST", "/friends/requests/" + requestId + "/accept", JsonObject())
    }

    fun declineFriendRequest(requestId: Long) {
        call("DELETE", "/friends/requests/" + requestId)
    }

    fun removeFriend(userId: Long) {
        call("DELETE", "/friends/" + userId)
    }

    fun messages(userId: Long, beforeId: Long? = null, limit: Int = 40): List<VoidCraftSocialMessage> {
        val cursor = beforeId?.let { "&before=" + it } ?: ""
        return call("GET", "/conversations/" + userId + "/messages?limit=" + limit + cursor)
            .getAsJsonArray("messages").mapObjects { item ->
                val row = item.asJsonObject
                VoidCraftSocialMessage(
                    id = row.get("id").asLong,
                    senderId = row.get("senderId").asLong,
                    recipientId = row.get("recipientId").asLong,
                    body = row.get("body").asString,
                    senderUsername = row.get("senderUsername").asString,
                    createdAt = row.get("createdAt").asString
                )
            }
    }

    fun sendMessage(userId: Long, content: String): VoidCraftSocialMessage {
        val response = call("POST", "/conversations/" + userId + "/messages", JsonObject().apply {
            addProperty("content", content)
        })
        val row = response.getAsJsonObject("message")
        return VoidCraftSocialMessage(
            id = row.get("id").asLong,
            senderId = row.get("senderId").asLong,
            recipientId = row.get("recipientId").asLong,
            body = row.get("body").asString,
            senderUsername = row.get("senderUsername")?.asString ?: "",
            createdAt = row.get("createdAt").asString
        )
    }

    fun blockUser(userId: Long) {
        call("POST", "/blocks/" + userId, JsonObject())
    }

    fun unblockUser(userId: Long) {
        call("DELETE", "/blocks/" + userId)
    }

    fun reportUser(userId: Long, category: String, details: String) {
        call("POST", "/reports", JsonObject().apply {
            addProperty("targetUserId", userId)
            addProperty("category", category)
            addProperty("details", details)
        })
    }

    private fun authenticate(path: String, body: JsonObject): VoidCraftSocialUser {
        val response = call("POST", path, body, includeToken = false)
        val accessToken = response.get("accessToken")?.asString
            ?: throw SocialApiException(0, "The account service did not return a launcher session.")
        tokenStore.save(accessToken)
        return parseUser(response.getAsJsonObject("user"))
    }

    private fun parseFriend(item: com.google.gson.JsonElement): VoidCraftFriend {
        val row = item.asJsonObject
        return VoidCraftFriend(
            id = row.get("id").asLong,
            username = row.get("username").asString,
            displayName = row.get("displayName")?.asString ?: row.get("username").asString,
            bio = row.get("bio")?.asString ?: "",
            role = row.get("role")?.asString ?: "member",
            createdAt = row.get("createdAt")?.asString ?: ""
        )
    }

    private fun parseUser(row: JsonObject): VoidCraftSocialUser = VoidCraftSocialUser(
        id = row.get("id").asLong,
        username = row.get("username").asString,
        displayName = row.get("displayName")?.asString ?: row.get("username").asString,
        bio = row.get("bio")?.asString ?: "",
        role = row.get("role")?.asString ?: "member",
        createdAt = row.get("createdAt")?.asString ?: "",
        email = row.get("email")?.asString
    )

    private fun call(
        method: String,
        apiPath: String,
        body: JsonObject? = null,
        includeToken: Boolean = true
    ): JsonObject {
        val url = URL(API_BASE_URL + apiPath)
        if (url.protocol != "https" || url.host != API_HOST || !url.path.startsWith("/api/")) {
            throw SocialApiException(0, "Refusing to send VoidCraft credentials to an untrusted URL.")
        }
        val connection = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-VoidCraft-Client", "launcher")
            if (body != null) setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (includeToken) {
                val token = tokenStore.read()
                if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer " + token)
            }
            if (body != null) doOutput = true
        }

        try {
            if (body != null) {
                connection.outputStream.use { output ->
                    output.write(GSON.toJson(body).toByteArray(Charsets.UTF_8))
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_RESPONSE_BYTES) {
                        throw SocialApiException(status, "The account service returned an oversized response.")
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: ByteArray(0)

            val responseJson = try {
                if (bytes.isEmpty()) JsonObject()
                else JsonParser.parseString(String(bytes, Charsets.UTF_8)).asJsonObject
            } catch (_: Exception) {
                JsonObject().apply {
                    addProperty("error", "Unexpected response from VoidCraft account service.")
                }
            }

            if (status !in 200..299) {
                if (status == 401 && includeToken) tokenStore.clear()
                val message = responseJson.get("error")?.asString
                    ?: "VoidCraft account service returned HTTP " + status + "."
                throw SocialApiException(status, message)
            }
            return responseJson
        } catch (error: SocialApiException) {
            throw error
        } catch (error: Exception) {
            throw SocialApiException(0, error.message ?: "Could not reach VoidCraft account service.")
        } finally {
            connection.disconnect()
        }
    }

    private fun <T> JsonArray.mapObjects(mapper: (com.google.gson.JsonElement) -> T): List<T> =
        map { mapper(it) }

    private companion object {
        const val API_HOST = "voidcraft-website.pages.dev"
        const val API_BASE_URL = "https://voidcraft-website.pages.dev/api"
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 8_000
        const val MAX_RESPONSE_BYTES = 1024 * 1024
        val GSON = com.google.gson.Gson()
    }
}
