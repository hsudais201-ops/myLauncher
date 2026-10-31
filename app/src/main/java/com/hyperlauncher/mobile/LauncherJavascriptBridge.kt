package com.hyperlauncher.mobile

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.StatFs
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
    private val baseStorageRoot: File
) {
    private val instanceStore = LauncherInstanceStore(baseStorageRoot)
    private val instanceStateLock = Any()
    @Volatile private var storageRoot: File = baseStorageRoot
    @Volatile private var selectedInstanceId: String = LauncherInstanceStore.DEFAULT_ID
    private val executor = Executors.newSingleThreadExecutor()
    private val installCancelled = AtomicBoolean(false)
    private val installationActive = AtomicBoolean(false)
    private val modSearchExecutor = Executors.newFixedThreadPool(2)
    private val modInstallExecutor = Executors.newSingleThreadExecutor()
    private val modInstallActive = AtomicBoolean(false)
    private val modInstallCancelled = AtomicBoolean(false)
    @Volatile private var curseForgeApiKey: String? = null



    init {
        try {
            activateInstanceState(instanceStore.list())
        } catch (_: Exception) {
            // Preserve the legacy default storage root if the instance registry is unavailable.
            storageRoot = baseStorageRoot
            selectedInstanceId = LauncherInstanceStore.DEFAULT_ID
        }
    }


    @JavascriptInterface
    fun refreshInstances() {
        executor.execute {
            try {
                val state = synchronized(instanceStateLock) {
                    instanceStore.list().also { activateInstanceState(it) }
                }
                postToUi("onNativeInstances", state.toString())
            } catch (error: Exception) {
                postInstanceError(error)
            }
        }
    }

    @JavascriptInterface
    fun createInstance(name: String, version: String) {
        updateInstanceProfile {
            instanceStore.create(name, version)
        }
    }

    @JavascriptInterface
    fun selectInstance(id: String) {
        updateInstanceProfile {
            instanceStore.select(id)
        }
    }

    @JavascriptInterface
    fun renameInstance(id: String, name: String) {
        updateInstanceProfile {
            instanceStore.rename(id, name)
        }
    }

    @JavascriptInterface
    fun deleteInstance(id: String) {
        updateInstanceProfile {
            require(id != selectedInstanceId) { "Select a different instance before deleting this one" }
            instanceStore.delete(id)
        }
    }

    @JavascriptInterface
    fun updateSelectedInstanceVersion(version: String) {
        updateInstanceProfile {
            instanceStore.updateSelectedVersion(version)
        }
    }

    private fun updateInstanceProfile(operation: () -> JSONObject) {
        executor.execute {
            try {
                val state = synchronized(instanceStateLock) {
                    ensureInstanceChangesAllowed()
                    operation().also { activateInstanceState(it) }
                }
                postToUi("onNativeInstances", state.toString())
            } catch (error: Exception) {
                postInstanceError(error)
            }
        }
    }

    private fun ensureInstanceChangesAllowed() {
        check(!installationActive.get() && !modInstallActive.get()) {
            "Wait for current downloads to finish before switching or changing instances"
        }
    }

    private fun activateInstanceState(state: JSONObject) {
        val selectedId = state.optString("selectedId", LauncherInstanceStore.DEFAULT_ID)
        storageRoot = instanceStore.storageRoot(selectedId)
        selectedInstanceId = selectedId
    }

    private fun postInstanceError(error: Exception) {
        postToUi(
            "onNativeInstances",
            JSONObject()
                .put("ok", false)
                .put("error", error.message ?: "Could not update launcher instances")
                .toString()
        )
    }

    @JavascriptInterface
    fun refreshDeviceInfo() {
        try {
            val activityManager = webView.context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            val range = DeviceMemoryPolicy.calculate(memoryInfo.totalMem)
            if (!storageRoot.exists() && !storageRoot.mkdirs()) {
                throw IllegalStateException("Could not inspect launcher storage")
            }
            val storageInfo = StatFs(storageRoot.absolutePath)
            val abis = Build.SUPPORTED_ABIS.toList()
            // Only a launcher-marked Android runtime bundle can count as installed.
            // Desktop JREs with the same CPU architecture are deliberately rejected.
            val runtimeInventory = AndroidRuntimeInventory.inspect(
                File(storageRoot, "runtimes"),
                abis.firstOrNull { it == "arm64-v8a" || it == "x86_64" }
            )
            val runtimeAssessment = AndroidRuntimeCompatibility.assess(
                androidApiLevel = Build.VERSION.SDK_INT,
                supportedAbis = abis,
                runtimeInstalled = runtimeInventory.compatibleRuntime != null
            )
            val primaryAbi = abis.firstOrNull().orEmpty()
            val architecture = when (primaryAbi) {
                "arm64-v8a" -> "ARM64"
                "armeabi-v7a" -> "ARM32"
                "x86_64" -> "x86_64"
                "x86" -> "x86"
                else -> primaryAbi.ifBlank { "Unknown" }
            }
            val model = (Build.MANUFACTURER + " " + Build.MODEL).trim()
            postToUi(
                "onNativeDeviceInfo",
                JSONObject()
                    .put("ok", true)
                    .put("deviceModel", model)
                    .put("architecture", architecture)
                    .put("supportedAbis", JSONArray(abis))
                    .put("androidSdk", Build.VERSION.SDK_INT)
                    .put("runtimeSupportedAbi", runtimeAssessment.selectedAbi ?: JSONObject.NULL)
                    .put("runtimeReady", runtimeAssessment.runtimeUsable)
                    .put("runtimeWarning", runtimeAssessment.warning ?: JSONObject.NULL)
                    .put("runtimeJavaMajor", runtimeInventory.compatibleRuntime?.javaMajor ?: JSONObject.NULL)
                    .put("runtimeName", runtimeInventory.compatibleRuntime?.name ?: JSONObject.NULL)
                    .put("totalMemoryBytes", memoryInfo.totalMem)
                    .put("availableMemoryBytes", memoryInfo.availMem)
                    .put("availableStorageBytes", storageInfo.availableBytes)
                    .put("minimumMemoryGb", range.minimumGb)
                    .put("maximumMemoryGb", range.maximumGb)
                    .put("recommendedMemoryGb", range.recommendedGb)
                    .put("selectedInstanceId", selectedInstanceId)
                    .toString()
            )
        } catch (error: Exception) {
            postToUi(
                "onNativeDeviceInfo",
                JSONObject().put("ok", false)
                    .put("error", error.message ?: "Could not inspect this device")
                    .toString()
            )
        }
    }

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
        if (versionId != "release" && versionId != "snapshot" && !Regex("^[0-9][A-Za-z0-9._-]{0,63}$").matches(versionId)) {
            postToUi(
                "onNativeMinecraftInstall",
                JSONObject().put("state", "error").put("error", "Invalid Minecraft version ID").toString()
            )
            return
        }
        val targetStorageRoot = synchronized(instanceStateLock) {
            if (modInstallActive.get() || !installationActive.compareAndSet(false, true)) null else storageRoot
        }
        if (targetStorageRoot == null) {
            postInstallEvent(
                JSONObject().put("state", "error").put("error", "Finish the current download before starting another installation")
            )
            return
        }
        installCancelled.set(false)
        executor.execute {
            var resolvedVersionId = versionId
            try {
                val version = MinecraftVersionManifest.findVersion(MinecraftVersionManifest.fetch(), versionId)
                    ?: throw IllegalArgumentException("Selected version was not found in Mojang's official manifest")
                resolvedVersionId = version.id
                postInstallEvent(
                    JSONObject().put("state", "planning").put("version", resolvedVersionId)
                )
                val plan = MinecraftInstallPlanner.create(version)
                if (installCancelled.get()) throw java.util.concurrent.CancellationException()
                val installed = MinecraftInstallationService.install(
                    plan = plan,
                    storageRoot = targetStorageRoot,
                    shouldCancel = { installCancelled.get() },
                    onProgress = { progress ->
                        postInstallEvent(
                            JSONObject()
                                .put("state", "downloading")
                                .put("version", version.id)
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
                        .put("version", version.id)
                        .put("installedFiles", installed.size)
                        .put("storagePath", targetStorageRoot.absolutePath)
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
    fun installRuntimeForVersion(versionId: String) {
        if (versionId != "release" && versionId != "snapshot" &&
            !Regex("^[0-9][A-Za-z0-9._-]{0,63}$").matches(versionId)
        ) {
            postRuntimeInstallEvent(
                JSONObject().put("state", "error").put("error", "Invalid Minecraft version ID")
            )
            return
        }

        val targetStorageRoot = synchronized(instanceStateLock) {
            if (modInstallActive.get() || !installationActive.compareAndSet(false, true)) null
            else storageRoot
        }
        if (targetStorageRoot == null) {
            postRuntimeInstallEvent(
                JSONObject().put("state", "error")
                    .put("error", "Finish the current download before installing a Java runtime")
            )
            return
        }

        installCancelled.set(false)
        executor.execute {
            try {
                val version = MinecraftVersionManifest.findVersion(
                    MinecraftVersionManifest.fetch(),
                    versionId
                ) ?: throw IllegalArgumentException(
                    "Selected version was not found in Mojang's official manifest"
                )
                postRuntimeInstallEvent(
                    JSONObject().put("state", "planning").put("version", version.id)
                )

                val details = MinecraftVersionDetailsResolver.fetch(version)
                val requiredJavaMajor = details.javaMajorRequirement ?: 8
                val abi = Build.SUPPORTED_ABIS.firstOrNull {
                    it == "arm64-v8a" || it == "x86_64"
                } ?: throw IllegalStateException(
                    "This device does not have a supported 64-bit Android runtime ABI"
                )
                val runtimePackage = AndroidRuntimePackageCatalog.compatible(
                    requiredJavaMajor,
                    abi
                ) ?: throw IllegalStateException(
                    "No verified Android Java runtime package is available for Java " +
                        "$requiredJavaMajor on $abi"
                )

                postRuntimeInstallEvent(
                    JSONObject()
                        .put("state", "downloading")
                        .put("version", version.id)
                        .put("requiredJavaMajor", requiredJavaMajor)
                        .put("selectedJavaMajor", runtimePackage.javaMajor)
                        .put("abi", abi)
                        .put("totalBytes", runtimePackage.sizeBytes)
                        .put("downloadedBytes", 0L)
                )

                val installed = AndroidRuntimeInstaller.install(
                    runtimePackage = runtimePackage,
                    cacheDirectory = File(webView.context.cacheDir, "runtime-downloads"),
                    runtimeRoot = File(targetStorageRoot, "runtimes"),
                    onDownloadProgress = { downloaded, total ->
                        postRuntimeInstallEvent(
                            JSONObject()
                                .put("state", "downloading")
                                .put("version", version.id)
                                .put("requiredJavaMajor", requiredJavaMajor)
                                .put("selectedJavaMajor", runtimePackage.javaMajor)
                                .put("abi", abi)
                                .put("downloadedBytes", downloaded)
                                .put("totalBytes", total)
                        )
                    },
                    onStage = { stage ->
                        postRuntimeInstallEvent(
                            JSONObject()
                                .put("state", stage)
                                .put("version", version.id)
                                .put("selectedJavaMajor", runtimePackage.javaMajor)
                                .put("abi", abi)
                        )
                    },
                    shouldCancel = { installCancelled.get() }
                )

                postRuntimeInstallEvent(
                    JSONObject()
                        .put("state", "complete")
                        .put("version", version.id)
                        .put("requiredJavaMajor", requiredJavaMajor)
                        .put("javaMajor", installed.javaMajor)
                        .put("abi", abi)
                        .put("runtimeName", installed.name)
                        .put("message", "Android Java runtime installed. Graphics and game launch are still separate.")
                )
                refreshDeviceInfo()
            } catch (error: java.util.concurrent.CancellationException) {
                postRuntimeInstallEvent(JSONObject().put("state", "cancelled").put("version", versionId))
            } catch (error: Exception) {
                postRuntimeInstallEvent(
                    JSONObject()
                        .put("state", "error")
                        .put("version", versionId)
                        .put("error", error.message ?: "Android Java runtime installation failed")
                )
            } finally {
                installationActive.set(false)
            }
        }
    }

    private fun postRuntimeInstallEvent(event: JSONObject) {
        postToUi("onNativeRuntimeInstall", event.toString())
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
        offset: Int,
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
                    curseForgeKey = curseForgeApiKey,
                    offset = offset
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
    fun listModVersions(
        source: String,
        projectId: String,
        projectType: String,
        gameVersion: String,
        loader: String,
        requestId: String
    ) {
        val safeRequestId = requestId.take(40)
        modSearchExecutor.execute {
            val payload = try {
                ModPlatformBrowser.listProjectVersions(
                    source, projectId, projectType, gameVersion, loader, curseForgeApiKey
                ).put("requestId", safeRequestId).put("projectId", projectId)
            } catch (error: Exception) {
                JSONObject()
                    .put("ok", false)
                    .put("source", source)
                    .put("requestId", safeRequestId)
                    .put("error", error.message ?: "Could not load compatible files")
            }
            postToUi("onNativeModVersions", payload.toString())
        }
    }

    @JavascriptInterface
    fun installModFile(
        source: String,
        projectId: String,
        fileId: String,
        fileName: String,
        projectType: String,
        gameVersion: String,
        loader: String,
        requestId: String
    ) {
        val targetStorageRoot = synchronized(instanceStateLock) {
            if (installationActive.get() || !modInstallActive.compareAndSet(false, true)) null else storageRoot
        }
        if (targetStorageRoot == null) {
            postToUi(
                "onNativeModInstall",
                JSONObject().put("state", "error").put("error", "Finish the current download before starting another file").toString()
            )
            return
        }
        modInstallCancelled.set(false)
        val safeRequestId = requestId.take(40)
        modInstallExecutor.execute {
            try {
                postModInstallEvent(
                    JSONObject().put("state", "resolving").put("requestId", safeRequestId).put("fileName", fileName)
                )
                val file = ModPlatformBrowser.resolveFileDownload(
                    source, projectId, fileId, fileName, projectType, gameVersion, loader, curseForgeApiKey
                )
                MinecraftStoragePreflight.check(targetStorageRoot, file.descriptor.size)
                if (modInstallCancelled.get()) throw java.util.concurrent.CancellationException()
                postModInstallEvent(
                    JSONObject().put("state", "downloading").put("requestId", safeRequestId)
                        .put("fileName", file.fileName).put("downloadedBytes", 0).put("totalBytes", file.descriptor.size)
                )
                val installed = VerifiedMinecraftFileDownloader.download(
                    descriptor = file.descriptor,
                    relativePath = file.relativePath,
                    storageRoot = targetStorageRoot,
                    onProgress = { downloaded, total ->
                        postModInstallEvent(
                            JSONObject().put("state", "downloading").put("requestId", safeRequestId)
                                .put("fileName", file.fileName).put("downloadedBytes", downloaded).put("totalBytes", total)
                        )
                    },
                    shouldCancel = { modInstallCancelled.get() },
                    additionalTrustedHosts = ModPlatformBrowser.modDownloadHosts
                )
                postModInstallEvent(
                    JSONObject().put("state", "complete").put("requestId", safeRequestId)
                        .put("fileName", file.fileName).put("path", installed.relativeTo(targetStorageRoot).invariantSeparatorsPath)
                )
            } catch (error: java.util.concurrent.CancellationException) {
                postModInstallEvent(JSONObject().put("state", "cancelled").put("requestId", safeRequestId))
            } catch (error: Exception) {
                postModInstallEvent(
                    JSONObject().put("state", "error").put("requestId", safeRequestId)
                        .put("error", error.message ?: "Mod download failed")
                )
            } finally {
                modInstallActive.set(false)
            }
        }
    }

    @JavascriptInterface
    fun cancelModInstall() {
        modInstallCancelled.set(true)
    }

    private fun postModInstallEvent(event: JSONObject) {
        postToUi("onNativeModInstall", event.toString())
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
        modInstallCancelled.set(true)
        modInstallExecutor.shutdownNow()
        executor.shutdownNow()
    }
}
