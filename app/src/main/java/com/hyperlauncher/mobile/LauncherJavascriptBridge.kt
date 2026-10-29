package com.hyperlauncher.mobile

import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Small bridge for the locally bundled UI only. Never expose this bridge to
 * remote web pages or load untrusted content in the WebView.
 */
class LauncherJavascriptBridge(
    private val webView: WebView
) {
    private val executor = Executors.newSingleThreadExecutor()

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

            webView.post {
                if (!webView.isAttachedToWindow) return@post
                val safelyQuotedPayload = JSONObject.quote(payload)
                webView.evaluateJavascript(
                    "window.onNativeMinecraftVersions && " +
                        "window.onNativeMinecraftVersions(JSON.parse($safelyQuotedPayload))",
                    null
                )
            }
        }
    }

    fun close() {
        executor.shutdownNow()
    }
}
