package com.hyperlauncher.mobile

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import java.io.File

/**
 * Native Android host for the bundled Hyper Launcher UI.
 *
 * This shell is not yet a Minecraft runtime and does not launch game processes.
 */
class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var launcherBridge: LauncherJavascriptBridge

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(16, 17, 15)
        window.navigationBarColor = Color.rgb(16, 17, 15)

        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(16, 17, 15))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            settings.allowFileAccessFromFileURLs = false
            settings.allowUniversalAccessFromFileURLs = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val uri = request.url
                    val isBundledLauncherAsset = uri.scheme == "file" &&
                        uri.path?.startsWith("/android_asset/") == true
                    return !isBundledLauncherAsset
                }
            }
            webChromeClient = WebChromeClient()
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
        }
        launcherBridge = LauncherJavascriptBridge(webView, File(filesDir, "minecraft"))
        webView.addJavascriptInterface(launcherBridge, "HyperLauncherNative")
        webView.loadUrl("file:///android_asset/index.html")

        setContentView(
            FrameLayout(this).apply {
                addView(
                    webView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
            }
        )
    }

    @Deprecated("Deprecated in Android API; retained for compatibility with older Android devices.")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::launcherBridge.isInitialized) launcherBridge.close()
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }
}
