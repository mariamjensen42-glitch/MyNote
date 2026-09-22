package com.cycling.mynote.data.saf

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.cycling.mynote.core.error.DataError
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * All Storage Access Framework traffic for the repository folder.
 *
 * Uses [DocumentsContract] directly rather than `DocumentFile`: the document-file wrapper answers
 * almost every question with a separate provider round trip, so listing a folder of *n* files costs
 * *n* IPC calls, whereas one children query costs one. The whole library is re-scanned on every
 * refresh, which makes that difference the deciding factor.
 *
 * Every method takes the tree URI explicitly. There is no mutable "current repository" in here —
 * that belongs to the repository layer — which keeps this class free of lifecycle and trivially
 * testable with a fake [ContentResolver].
 */
@Singleton
class DocumentTreeStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val resolver: ContentResolver get() = context.contentResolver

    /** The document id of the granted folder itself, i.e. the root of the repository. */
    fun rootDocumentId(treeUri: Uri): String = DocumentsContract.getTreeDocumentId(treeUri)

    /** A child document URI that is usable with [ContentResolver] for reading and writing. */
    fun documentUri(treeUri: Uri, documentId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)

    /**
     * Lists the children of [parentDocumentId] in a single provider query.
     *
     * Documents whose name the provider will not report are skipped rather than surfaced with a
     * placeholder name, since a nameless row would be un-openable in the UI anyway.
     */
    fun listChildren(treeUri: Uri, parentDocumentId: String): List<DocumentEntry> {
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

        return query(childrenUri, projection) { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(projection[0])
            val nameIndex = cursor.getColumnIndexOrThrow(projection[1])
            val mimeIndex = cursor.getColumnIndexOrThrow(projection[2])
            val sizeIndex = cursor.getColumnIndexOrThrow(projection[3])
            val modifiedIndex = cursor.getColumnIndexOrThrow(projection[4])

            buildList {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex) ?: continue
                    add(
                        DocumentEntry(
                            documentId = cursor.getString(idIndex) ?: continue,
                            displayName = name,
                            mimeType = cursor.getString(mimeIndex).orEmpty(),
                            sizeBytes = if (cursor.isNull(sizeIndex)) 0L else cursor.getLong(sizeIndex),
                            lastModified = if (cursor.isNull(modifiedIndex)) 0L else cursor.getLong(modifiedIndex),
                        ),
                    )
                }
            }
        }
    }

    /** Reads a document as UTF-8 text. */
    fun readText(treeUri: Uri, documentId: String): String = try {
        resolver.openInputStream(documentUri(treeUri, documentId))?.use { stream ->
            stream.bufferedReader(Charsets.UTF_8).readText()
        } ?: throw DataError.Io("无法打开文件流：$documentId")
    } catch (e: FileNotFoundException) {
        throw DataError.NoteNotFound(documentId)
    } catch (e: IOException) {
        throw DataError.Io("读取失败：$documentId", e)
    }

    /**
     * Overwrites a document with [text].
     *
     * `"wt"` asks the provider to truncate; providers that reject the mode are retried with `"w"`,
     * which is what the framework falls back to anyway and is truncating in practice for the
     * providers Android ships.
     */
    fun writeText(treeUri: Uri, documentId: String, text: String) {
        val uri = documentUri(treeUri, documentId)
        val written = runCatching {
            resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }.getOrNull()

        if (written != null) return

        try {
            resolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?: throw DataError.Io("无法打开写入流：$documentId")
        } catch (e: IOException) {
            throw DataError.Io("写入失败：$documentId", e)
        }
    }

    /** Creates an empty file and returns its new document id. */
    fun createFile(
        treeUri: Uri,
        parentDocumentId: String,
        displayName: String,
        mimeType: String = DocumentEntry.MIME_MARKDOWN,
    ): String = try {
        DocumentsContract.createDocument(
            resolver,
            DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocumentId),
            mimeType,
            displayName,
        )?.let(DocumentsContract::getDocumentId)
            ?: throw DataError.Io("创建文件失败：$displayName")
    } catch (e: FileNotFoundException) {
        throw DataError.RepoUnavailable(e)
    }

    /** Creates an empty folder and returns its new document id. */
    fun createDirectory(
        treeUri: Uri,
        parentDocumentId: String,
        displayName: String,
    ): String = try {
        DocumentsContract.createDocument(
            resolver,
            DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocumentId),
            DocumentEntry.MIME_DIRECTORY,
            displayName,
        )?.let(DocumentsContract::getDocumentId)
            ?: throw DataError.Io("创建文件夹失败：$displayName")
    } catch (e: FileNotFoundException) {
        throw DataError.RepoUnavailable(e)
    }

    /**
     * Renames a document.
     *
     * The provider's own rename is used when available because it preserves the file in place;
     * providers that do not implement it report the original name back, which is treated as
     * failure so the caller can fall back to a copy.
     */
    fun rename(treeUri: Uri, documentId: String, newDisplayName: String): Boolean = runCatching {
        val renamed = DocumentsContract.renameDocument(
            resolver,
            documentUri(treeUri, documentId),
            newDisplayName,
        ) ?: return@runCatching false
        DocumentsContract.getDocumentId(renamed) == documentId
    }.getOrDefault(false)

    fun delete(treeUri: Uri, documentId: String) {
        val deleted = runCatching {
            DocumentsContract.deleteDocument(resolver, documentUri(treeUri, documentId))
        }.getOrDefault(false)
        if (!deleted) throw DataError.Io("删除失败：$documentId")
    }

    /** Copies the bytes of [sourceDocumentId] into [targetParentDocumentId] under [displayName]. */
    fun copyFile(
        treeUri: Uri,
        sourceDocumentId: String,
        targetParentDocumentId: String,
        displayName: String,
    ): String {
        val newDocumentId = createFile(treeUri, targetParentDocumentId, displayName)
        val bytes = try {
            resolver.openInputStream(documentUri(treeUri, sourceDocumentId))
                ?.use { it.readBytes() }
                ?: throw DataError.Io("无法读取源文件：$sourceDocumentId")
        } catch (e: IOException) {
            throw DataError.Io("移动时读取失败：$sourceDocumentId", e)
        }
        writeBytes(treeUri, newDocumentId, bytes)
        return newDocumentId
    }

    private fun writeBytes(treeUri: Uri, documentId: String, bytes: ByteArray) {
        try {
            resolver.openOutputStream(documentUri(treeUri, documentId))?.use { it.write(bytes) }
                ?: throw DataError.Io("无法写入目标文件：$documentId")
        } catch (e: IOException) {
            throw DataError.Io("移动时写入失败：$documentId", e)
        }
    }

    /** True when the document still exists and is readable. */
    fun exists(treeUri: Uri, documentId: String): Boolean = runCatching {
        query(
            documentUri(treeUri, documentId),
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
        ) { it.moveToFirst() }
    }.getOrDefault(false)

    /**
     * Verifies the persisted grant still resolves.
     *
     * A tree permission can be revoked between sessions, and every later call fails with a
     * provider-specific exception; checking once up front turns that into the single actionable
     * [DataError.RepoUnavailable] the onboarding screen knows how to react to.
     */
    fun isAccessible(treeUri: Uri): Boolean = runCatching {
        listChildren(treeUri, rootDocumentId(treeUri))
        true
    }.getOrElse { error ->
        Log.w(TAG, "repository is not accessible: $treeUri", error)
        false
    }

    private fun <T> query(uri: Uri, projection: Array<String>, block: (Cursor) -> T): T {
        val cursor = try {
            resolver.query(uri, projection, null, null, null)
        } catch (e: SecurityException) {
            throw DataError.RepoUnavailable(e)
        } catch (e: IllegalArgumentException) {
            throw DataError.RepoUnavailable(e)
        } ?: throw DataError.RepoUnavailable()

        return cursor.use(block)
    }

    private companion object {
        const val TAG = "DocumentTreeStore"
    }
}

/** Builds the human-readable path label the design shows under a repository name. */
object DocumentIdLabel {

    /**
     * Turns `primary:Documents/Notes` into `内部存储 / Documents / Notes`.
     *
     * SAF exposes no absolute path, so this describes the provider's own document id. Anything that
     * is not the primary volume is shown verbatim rather than guessed at.
     */
    fun describe(documentId: String): String {
        val volume = documentId.substringBefore(':', "")
        val path = documentId.substringAfter(':', "")
        val volumeLabel = when (volume) {
            "primary" -> "内部存储"
            "home" -> "文档"
            "" -> return documentId
            else -> volume
        }
        return if (path.isBlank()) volumeLabel else "$volumeLabel / $path"
    }

    /** The last path segment, used as the repository's display name. */
    fun displayName(documentId: String): String =
        documentId.substringAfterLast('/').substringAfterLast(':').ifBlank { documentId }
}
