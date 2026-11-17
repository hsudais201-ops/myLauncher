package com.mflegend.flamelauncher.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.io.FilterInputStream
import java.io.FilterOutputStream

/**
 * SAF-backed access to a user-selected game directory.
 *
 * Document URIs are provider handles, not filesystem paths. Keep them as Uri values
 * and use ContentResolver for all document operations.
 */
class SafGameStorage(
    private val resolver: ContentResolver,
    private val treeUri: Uri
) {
    private val rootDocumentUri: Uri

    init {
        require(treeUri.scheme == "content") { "A content:// tree URI is required." }
        require(DocumentsContract.isTreeUri(treeUri)) { "A SAF tree URI is required." }
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        rootDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
    }

    data class Entry(
        val uri: Uri,
        val documentId: String,
        val name: String,
        val mimeType: String,
        val sizeBytes: Long?,
        val lastModifiedMillis: Long?
    ) {
        val isDirectory: Boolean get() = mimeType == Document.MIME_TYPE_DIR
    }

    fun root(): Uri = rootDocumentUri

    /** Lists direct children of a directory selected from this tree. */
    fun listChildren(directoryUri: Uri = rootDocumentUri): List<Entry> {
        requireInTree(directoryUri)
        val parentId = DocumentsContract.getDocumentId(directoryUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED
        )

        return providerCall("reading the selected folder") {
            val entries = mutableListOf<Entry>()
            val cursor = requireNotNull(resolver.query(childrenUri, projection, null, null, Document.COLUMN_DISPLAY_NAME)) {
                "The document provider returned no folder listing."
            }
            cursor.use {
                val idColumn = it.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
                val nameColumn = it.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = it.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)
                val sizeColumn = it.getColumnIndexOrThrow(Document.COLUMN_SIZE)
                val modifiedColumn = it.getColumnIndexOrThrow(Document.COLUMN_LAST_MODIFIED)

                while (it.moveToNext()) {
                    val documentId = it.getString(idColumn)
                    entries += Entry(
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                        documentId = documentId,
                        name = it.getString(nameColumn) ?: "",
                        mimeType = it.getString(mimeColumn) ?: "application/octet-stream",
                        sizeBytes = if (it.isNull(sizeColumn)) null else it.getLong(sizeColumn),
                        lastModifiedMillis = if (it.isNull(modifiedColumn)) null else it.getLong(modifiedColumn)
                    )
                }
            }
            entries
        }
    }

    fun openInput(uri: Uri): InputStream {
        requireInTree(uri)
        val stream = providerCall("opening a file for reading") {
            requireNotNull(resolver.openInputStream(uri)) { "The document provider returned no input stream." }
        }
        return object : FilterInputStream(stream) {
            override fun read(): Int = providerCall("reading a file") { stream.read() }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                providerCall("reading a file") { stream.read(buffer, offset, length) }

            override fun skip(count: Long): Long =
                providerCall("skipping within a file") { stream.skip(count) }

            override fun close() {
                providerCall("closing a file after reading") { stream.close() }
            }
        }
    }

    /** Opens a document for writing. Use "wt" to replace contents or "wa" to append. */
    fun openOutput(uri: Uri, mode: String = "wt"): OutputStream {
        requireInTree(uri)
        require(mode == "wt" || mode == "wa" || mode == "rw" || mode == "rwt") {
            "Unsupported document write mode."
        }
        val stream = providerCall("opening a file for writing") {
            requireNotNull(resolver.openOutputStream(uri, mode)) { "The document provider returned no output stream." }
        }
        return object : FilterOutputStream(stream) {
            override fun write(value: Int) {
                providerCall("writing a file") { out.write(value) }
            }

            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                providerCall("writing a file") { out.write(buffer, offset, length) }
            }

            override fun flush() {
                providerCall("flushing a file") { out.flush() }
            }

            override fun close() {
                providerCall("closing a file after writing") { stream.close() }
            }
        }
    }

    fun createDirectory(parentUri: Uri, displayName: String): Uri {
        requireInTree(parentUri)
        validateDisplayName(displayName)
        require(listChildren(parentUri).none { it.name == displayName }) {
            "A document already occupies the requested folder name '$displayName'."
        }
        val created = providerCall("creating a folder") {
            requireNotNull(
                DocumentsContract.createDocument(resolver, parentUri, Document.MIME_TYPE_DIR, displayName)
            ) { "The document provider returned no folder." }
        }
        // Providers can return an unexpected document or expose duplicate names.
        // Confirm the created URI is a unique, visible directory before using it.
        requireInTree(created)
        val createdId = DocumentsContract.getDocumentId(created)
        val visibleEntries = listChildren(parentUri).filter { it.name == displayName }
        require(
            visibleEntries.size == 1 &&
                visibleEntries.single().documentId == createdId &&
                visibleEntries.single().isDirectory
        ) {
            "The storage provider did not create one distinct folder with the requested name."
        }
        return created
    }

    fun createFile(parentUri: Uri, displayName: String, mimeType: String = "application/octet-stream"): Uri {
        requireInTree(parentUri)
        validateDisplayName(displayName)
        require(mimeType.isNotBlank()) { "A MIME type is required." }
        require(listChildren(parentUri).none { it.name == displayName }) {
            "A document already occupies the requested file name '$displayName'."
        }
        val created = providerCall("creating a file") {
            requireNotNull(
                DocumentsContract.createDocument(resolver, parentUri, mimeType, displayName)
            ) { "The document provider returned no file." }
        }
        // Confirm that the provider created one distinct, visible file rather than
        // returning a reused URI, a directory, or an ambiguous duplicate.
        requireInTree(created)
        val createdId = DocumentsContract.getDocumentId(created)
        val visibleEntries = listChildren(parentUri).filter { it.name == displayName }
        require(
            visibleEntries.size == 1 &&
                visibleEntries.single().documentId == createdId &&
                !visibleEntries.single().isDirectory
        ) {
            "The storage provider did not create one distinct file with the requested name."
        }
        return created
    }

    /**
     * Creates a uniquely fresh temporary document. Stale entries with the reserved
     * temporary name must be removed and confirmed absent before a new file is made.
     */
    fun createTemporaryFile(parentUri: Uri, displayName: String, mimeType: String): Uri {
        requireInTree(parentUri)
        validateDisplayName(displayName)
        require(mimeType.isNotBlank()) { "A MIME type is required." }
        val staleEntries = listChildren(parentUri).filter { it.name == displayName }
        require(staleEntries.none { it.isDirectory }) {
            "A folder is occupying the reserved temporary download name."
        }
        staleEntries.forEach { entry ->
            require(delete(entry.uri)) {
                "The storage provider could not remove an interrupted temporary download."
            }
        }
        require(listChildren(parentUri).none { it.name == displayName }) {
            "An interrupted temporary download could not be cleared. Choose another storage provider or folder."
        }
        val created = createFile(parentUri, displayName, mimeType)
        try {
            requireInTree(created)
            val createdId = DocumentsContract.getDocumentId(created)
            val visibleEntries = listChildren(parentUri).filter { it.name == displayName }
            require(visibleEntries.size == 1 && visibleEntries.single().documentId == createdId) {
                "The storage provider did not create one distinct temporary download file."
            }
            return created
        } catch (error: Exception) {
            runCatching {
                requireInTree(created)
                if (!delete(created)) {
                    throw IOException("The unverified temporary download file could not be removed.")
                }
            }.onFailure(error::addSuppressed)
            throw error
        }
    }

    /** Finds one named directory and rejects file or duplicate-name collisions. */
    fun findDirectory(parentUri: Uri, displayName: String): Entry? {
        requireInTree(parentUri)
        validateDisplayName(displayName)
        val matches = listChildren(parentUri).filter { it.name == displayName }
        require(matches.none { !it.isDirectory }) {
            "A file already occupies the folder name '$displayName'."
        }
        require(matches.size <= 1) {
            "The storage provider returned multiple folders named '$displayName'. Resolve the duplicate before continuing."
        }
        return matches.singleOrNull()
    }

    /**
     * Finds a named file in a directory and fails closed on directory or duplicate-name
     * collisions instead of silently treating them as a missing destination.
     */
    fun findFile(parentUri: Uri, displayName: String): Entry? {
        requireInTree(parentUri)
        validateDisplayName(displayName)
        val matches = listChildren(parentUri).filter { it.name == displayName }
        require(matches.none { it.isDirectory }) {
            "A folder already occupies the file name '$displayName'."
        }
        require(matches.size <= 1) {
            "The storage provider returned multiple files named '$displayName'. Resolve the duplicate before continuing."
        }
        return matches.singleOrNull()
    }

    /** Renames a document and verifies the provider's returned identity in its original parent. */
    fun rename(uri: Uri, displayName: String): Uri {
        requireInTree(uri)
        validateDisplayName(displayName)
        val originalId = DocumentsContract.getDocumentId(uri)
        val originalPath = providerCall("locating a file before renaming") {
            requireNotNull(DocumentsContract.findDocumentPath(resolver, treeUri, uri)) {
                "The storage provider could not locate the file before renaming."
            }
        }
        val pathIds = originalPath.path
        require(pathIds.size >= 2 && pathIds.last() == originalId) {
            "The storage provider returned an invalid parent path for the file being renamed."
        }
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, pathIds[pathIds.lastIndex - 1])
        requireInTree(parentUri)
        require(listChildren(parentUri).none { it.name == displayName && it.documentId != originalId }) {
            "Another document already occupies the requested name '$displayName'."
        }

        val renamed = providerCall("renaming a file") {
            requireNotNull(DocumentsContract.renameDocument(resolver, uri, displayName)) {
                "The document provider returned no renamed file."
            }
        }
        requireInTree(renamed)
        val renamedId = DocumentsContract.getDocumentId(renamed)
        val visibleEntries = listChildren(parentUri).filter { it.name == displayName }
        require(
            visibleEntries.size == 1 &&
                visibleEntries.single().documentId == renamedId &&
                !visibleEntries.single().isDirectory
        ) {
            "The storage provider did not expose one distinct renamed file with the requested name."
        }
        return renamed
    }

    /** Replaces a destination only after its temporary replacement has been fully verified. */
    fun replaceWithVerifiedFile(temporaryUri: Uri, existingUri: Uri?, finalName: String): Uri {
        requireInTree(temporaryUri)
        existingUri?.let { requireInTree(it) }
        validateDisplayName(finalName)
        require(
            existingUri == null ||
                DocumentsContract.getDocumentId(existingUri) != DocumentsContract.getDocumentId(temporaryUri)
        ) {
            "The temporary document and existing destination must be different files."
        }
        val backupUri = existingUri?.let { rename(it, ".$finalName.backup") }
        val finalUri = try {
            rename(temporaryUri, finalName)
        } catch (error: Exception) {
            if (backupUri != null) {
                runCatching { rename(backupUri, finalName) }
                    .onFailure(error::addSuppressed)
            }
            throw error
        }
        // A cleanup failure must not turn a successful promotion into a reported
        // install failure. The verified final file is already in place; a stale
        // backup can be cleaned up separately.
        backupUri?.let { runCatching { delete(it) } }
        return finalUri
    }

    /**
     * Best-effort cleanup for an incomplete temporary file. A cleanup failure is
     * attached to the original transfer error so it never hides the real failure.
     */
    fun deleteTemporaryAfterFailure(uri: Uri, failure: Exception) {
        try {
            if (!delete(uri)) {
                failure.addSuppressed(IOException("The storage provider did not delete an incomplete temporary file."))
            }
        } catch (cleanupError: Exception) {
            if (cleanupError !== failure) failure.addSuppressed(cleanupError)
        }
    }

    fun delete(uri: Uri): Boolean {
        requireInTree(uri)
        return providerCall("deleting a file") { DocumentsContract.deleteDocument(resolver, uri) }
    }

    private fun requireInTree(uri: Uri) {
        require(uri.scheme == "content" && uri.authority == treeUri.authority) {
            "The document does not belong to the selected storage provider."
        }
        require(
            uri == rootDocumentUri ||
                providerCall("checking folder access") {
                    DocumentsContract.isChildDocument(resolver, rootDocumentUri, uri)
                }
        ) { "The document is outside the selected game folder." }
    }

    private inline fun <T> providerCall(operation: String, block: () -> T): T = try {
        block()
    } catch (error: StorageAccessException) {
        throw error
    } catch (error: SecurityException) {
        throw StorageAccessException(
            "Android no longer grants access while $operation. Re-select the game folder in Settings.",
            error
        )
    } catch (error: IOException) {
        throw StorageAccessException(
            "The selected storage provider failed while $operation. Check that the folder is available, then try again.",
            error
        )
    } catch (error: IllegalStateException) {
        throw StorageAccessException(
            "The selected storage provider became unavailable while $operation. Re-select the game folder or use another provider.",
            error
        )
    } catch (error: UnsupportedOperationException) {
        throw StorageAccessException(
            "This storage provider does not support the operation: $operation. Choose a different game folder location.",
            error
        )
    }

    private fun validateDisplayName(name: String) {
        require(name.isNotBlank() && name != "." && name != "..") { "A valid document name is required." }
        require('/' !in name && '\\' !in name) { "Document names cannot contain path separators." }
    }
}
