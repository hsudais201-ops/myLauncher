package com.mflegend.flamelauncher.storage

import java.io.IOException

/** A user-actionable failure returned by an Android Storage Access Framework provider. */
class StorageAccessException(message: String, cause: Throwable) : IOException(message, cause)
