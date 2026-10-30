package com.hyperlauncher.mobile

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
                JSONObject().put("ok", false).put("error", "Invalid Minecraft version ID").toString()
            )
            return
        }
        if (!installCancelled.compareAndSet(false, false)) return
        executor.execute {
            installCancelled.set(false)
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
            }
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
        executor.shutdownNow()
    }
}
