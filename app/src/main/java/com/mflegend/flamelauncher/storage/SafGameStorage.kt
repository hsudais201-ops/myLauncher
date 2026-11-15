package com.mflegend.flamelauncher.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.DocumentsContract.Document
import java.io.InputStream
import java.io.OutputStream

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

        val entries = mutableListOf<Entry>()
        resolver.query(childrenUri, projection, null, null, Document.COLUMN_DISPLAY_NAME)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)
            val modifiedColumn = cursor.getColumnIndexOrThrow(Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                val documentId = cursor.getString(idColumn)
                entries += Entry(
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                    documentId = documentId,
                    name = cursor.getString(nameColumn) ?: "",
                    mimeType = cursor.getString(mimeColumn) ?: "application/octet-stream",
                    sizeBytes = if (cursor.isNull(sizeColumn)) null else cursor.getLong(sizeColumn),
                    lastModifiedMillis = if (cursor.isNull(modifiedColumn)) null else cursor.getLong(modifiedColumn)
                )
            }
        }
        return entries
    }

    fun openInput(uri: Uri): InputStream {
        requireInTree(uri)
        return requireNotNull(resolver.openInputStream(uri)) { "The document provider could not open this file." }
    }

    /** Opens a document for writing. Use "wt" to replace contents or "wa" to append. */
    fun openOutput(uri: Uri, mode: String = "wt"): OutputStream {
        requireInTree(uri)
        require(mode == "wt" || mode == "wa" || mode == "rw" || mode == "rwt") {
            "Unsupported document write mode."
        }
        return requireNotNull(resolver.openOutputStream(uri, mode)) { "The document provider could not write this file." }
    }

    fun createDirectory(parentUri: Uri, displayName: String): Uri {
        requireInTree(parentUri)
        validateDisplayName(displayName)
        return requireNotNull(
            DocumentsContract.createDocument(resolver, parentUri, Document.MIME_TYPE_DIR, displayName)
        ) { "The document provider could not create the directory." }
    }

    fun createFile(parentUri: Uri, displayName: String, mimeType: String = "application/octet-stream"): Uri {
        requireInTree(parentUri)
        validateDisplayName(displayName)
        require(mimeType.isNotBlank()) { "A MIME type is required." }
        return requireNotNull(
            DocumentsContract.createDocument(resolver, parentUri, mimeType, displayName)
        ) { "The document provider could not create the file." }
    }

    fun delete(uri: Uri): Boolean {
        requireInTree(uri)
        return DocumentsContract.deleteDocument(resolver, uri)
    }

    private fun requireInTree(uri: Uri) {
        require(uri.scheme == "content" && uri.authority == treeUri.authority) {
            "The document does not belong to the selected storage provider."
        }
        require(
            uri == rootDocumentUri ||
                DocumentsContract.isChildDocument(resolver, rootDocumentUri, uri)
        ) { "The document is outside the selected game folder." }
    }

    private fun validateDisplayName(name: String) {
        require(name.isNotBlank() && name != "." && name != "..") { "A valid document name is required." }
        require('/' !in name && '\\\\' !in name) { "Document names cannot contain path separators." }
    }
}
