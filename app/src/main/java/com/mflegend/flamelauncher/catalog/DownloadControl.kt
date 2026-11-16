package com.mflegend.flamelauncher.catalog

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** Cooperative cancellation and byte progress shared by the install pipeline. */
class DownloadControl(
    private val onProgress: (stage: String, completed: Long, total: Long?) -> Unit = { _, _, _ -> }
) {
    private val cancelled = AtomicBoolean(false)

    fun cancel() {
        cancelled.set(true)
    }

    fun checkpoint() {
        if (cancelled.get() || Thread.currentThread().isInterrupted) {
            throw CancellationException("Download cancelled.")
        }
    }

    fun progress(stage: String, completed: Long, total: Long?) {
        checkpoint()
        onProgress(stage, completed, total)
    }
}
