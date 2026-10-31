package com.hyperlauncher.mobile

import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bridge for the locally bundled UI only. Never expose it to remote web pages.
 */
class LauncherJavascriptBridge(
    private val webView: WebView,
    private val storageRoot: File
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val installCancelled = AtomicBoolean(false)
    private val installationActive = AtomicBoolean(false)
    private val modSearchExecutor = Executors.newFixedThreadPool(2)
    @Volatile private var curseForgeApiKey: String? = null

    @JavascriptInterface
    fun refreshVersions() {
        executor.execute {
            val payload = try {
                val versions = MinecraftVersionManifest.fetch()
                val array = JSONArray()
                versions.forEach { version ->
                    array.put(
                        JSONObject()
                            .put("id", version.id)
                            .put("type", version.type)
                            .put("releaseTime", version.releaseTime)
                    )
                }
                JSONObject().put("ok", true).put("versions", array).toString()
            } catch (error: Exception) {
                JSONObject()
                    .put("ok", false)
                    .put("error", error.message ?: "Could not load official versions")
                    .toString()
            }
            postToUi("onNativeMinecraftVersions", payload)
        }
    }

    @JavascriptInterface
    fun installVersion(versionId: String) {
        if (!Regex("^[0-9][A-Za-z0-9._-]{0,63}$").matches(versionId)) {
            postToUi(
                "onNativeMinecraftInstall",
                JSONObject().put("state", "error").put("error", "Invalid Minecraft version ID").toString()
            )
            return
        }
        if (!installationActive.compareAndSet(false, true)) {
            postInstallEvent(JSONObject().put("state", "error").put("error", "An installation is already running"))
            return
        }
        installCancelled.set(false)
        executor.execute {
            try {
                postInstallEvent(
                    JSONObject().put("state", "planning").put("version", versionId)
                )
                val version = MinecraftVersionManifest.fetch().firstOrNull { it.id == versionId }
                    ?: throw IllegalArgumentException("Selected version was not found in Mojang's official manifest")
                val plan = MinecraftInstallPlanner.create(version)
                if (installCancelled.get()) throw java.util.concurrent.CancellationException()
                val installed = MinecraftInstallationService.install(
                    plan = plan,
                    storageRoot = storageRoot,
                    shouldCancel = { installCancelled.get() },
                    onProgress = { progress ->
                        postInstallEvent(
                            JSONObject()
                                .put("state", "downloading")
                                .put("version", versionId)
                                .put("completedFiles", progress.completedFiles)
                                .put("totalFiles", progress.totalFiles)
                                .put("completedBytes", progress.completedBytes)
                                .put("totalBytes", progress.totalBytes)
                                .put("currentPath", progress.currentPath ?: JSONObject.NULL)
                        )
                    }
                )
                postInstallEvent(
                    JSONObject()
                        .put("state", "complete")
                        .put("version", versionId)
                        .put("installedFiles", installed.size)
                        .put("storagePath", storageRoot.absolutePath)
                )
            } catch (error: java.util.concurrent.CancellationException) {
                postInstallEvent(JSONObject().put("state", "cancelled").put("version", versionId))
            } catch (error: Exception) {
                postInstallEvent(
                    JSONObject()
                        .put("state", "error")
                        .put("version", versionId)
                        .put("error", error.message ?: "Installation failed")
                )
            } finally {
                installationActive.set(false)
            }
        }
    }


    @JavascriptInterface
    fun setCurseForgeApiKey(value: String) {
        val key = value.trim()
        if (key.length !in 8..512 || key.any { it.isWhitespace() || it.code < 33 }) {
            postToUi(
                "onNativeCurseForgeKey",
                JSONObject().put("ok", false).put("error", "Enter a valid CurseForge API key").toString()
            )
            return
        }
        // Keep credentials in memory only. They are not written to preferences or files.
        curseForgeApiKey = key
        postToUi("onNativeCurseForgeKey", JSONObject().put("ok", true).toString())
    }

    @JavascriptInterface
    fun clearCurseForgeApiKey() {
        curseForgeApiKey = null
        postToUi("onNativeCurseForgeKey", JSONObject().put("ok", true).put("cleared", true).toString())
    }

    @JavascriptInterface
    fun searchMods(
        source: String,
        query: String,
        projectType: String,
        gameVersion: String,
        loader: String,
        sort: String,
        requestId: String
    ) {
        val safeRequestId = requestId.take(40)
        modSearchExecutor.execute {
            val payload = try {
                ModPlatformBrowser.search(
                    source = source,
                    query = query,
                    projectType = projectType,
                    gameVersion = gameVersion,
                    loader = loader,
                    sort = sort,
                    curseForgeKey = curseForgeApiKey
                ).put("requestId", safeRequestId)
            } catch (error: Exception) {
                JSONObject()
                    .put("ok", false)
                    .put("source", source)
                    .put("requestId", safeRequestId)
                    .put("error", error.message ?: "Could not search the mod catalog")
            }
            postToUi("onNativeModSearch", payload.toString())
        }
    }

    @JavascriptInterface
    fun openProjectUrl(value: String) {
        try {
            val uri = Uri.parse(value)
            val host = uri.host?.lowercase().orEmpty()
            val allowed = host == "modrinth.com" || host == "www.modrinth.com" ||
                host == "curseforge.com" || host == "www.curseforge.com" ||
                host == "console.curseforge.com"
            require(uri.scheme == "https" && allowed && uri.userInfo == null &&
                (uri.port == -1 || uri.port == 443)) { "Blocked an untrusted project link" }
            webView.post {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    webView.context.startActivity(intent)
                } catch (_: Exception) {
                    postToUi(
                        "onNativeModSearch",
                        JSONObject().put("ok", false).put("error", "No browser app is available to open this link").toString()
                    )
                }
            }
        } catch (error: Exception) {
            postToUi(
                "onNativeModSearch",
                JSONObject().put("ok", false).put("error", error.message ?: "Invalid project link").toString()
            )
        }
    }

    @JavascriptInterface
    fun cancelInstallation() {
        installCancelled.set(true)
    }

    private fun postInstallEvent(event: JSONObject) {
        postToUi("onNativeMinecraftInstall", event.toString())
    }

    private fun postToUi(callback: String, payload: String) {
        webView.post {
            if (!webView.isAttachedToWindow) return@post
            val quoted = JSONObject.quote(payload)
            webView.evaluateJavascript(
                "window.$callback && window.$callback(JSON.parse($quoted))",
                null
            )
        }
    }

    fun close() {
        installCancelled.set(true)
        curseForgeApiKey = null
        modSearchExecutor.shutdownNow()
        executor.shutdownNow()
    }
}
