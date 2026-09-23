package com.cycling.mynote.data.attachment

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.cycling.mynote.core.error.DataError
import com.cycling.mynote.data.repo.NoteNaming
import com.cycling.mynote.data.repo.RepoPathResolver
import com.cycling.mynote.data.repo.RepoSession
import com.cycling.mynote.data.saf.DocumentTreeStore
import com.cycling.mynote.di.IoDispatcher
import com.cycling.mynote.domain.repository.RepoRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Files pictures that come from outside the repository into it.
 *
 * A note refers to its pictures by path, and the picture has to live somewhere the app can read
 * again — the granted folder — so picking one copies it into the attachment folder rather than
 * leaving a URI that stops working when the picker's grant expires. Nothing is moved or deleted at
 * the source: the user's gallery is theirs.
 */
@Singleton
class NoteAttachments @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: DocumentTreeStore,
    private val paths: RepoPathResolver,
    private val session: RepoSession,
    private val repoRepository: RepoRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /**
     * Copies the picture behind [sourceUri] into [folderPath] inside the repository.
     *
     * @return the new file's path relative to the repository root, or `null` when it could not be
     *   read or written — a cancelled picker grant, a folder that cannot be created, a file that is
     *   too large.
     */
    suspend fun saveImage(sourceUri: String, folderPath: String): String? = withContext(io) {
        val uri = runCatching { Uri.parse(sourceUri) }.getOrNull() ?: return@withContext null
        val bytes = readPicked(uri) ?: return@withContext null
        val fileName = displayNameOf(uri) ?: DEFAULT_FILE_NAME

        val treeUri = try {
            session.requireTreeUri()
        } catch (e: DataError) {
            return@withContext null
        }
        val folderId = ensureFolder(treeUri, folderPath) ?: return@withContext null
        val taken = store.listChildren(treeUri, folderId).mapTo(mutableSetOf()) { it.displayName }
        val unique = NoteNaming.uniqueName(taken, fileName)

        val documentId = try {
            store.createFile(treeUri, folderId, unique, mimeTypeOf(unique))
        } catch (e: DataError) {
            return@withContext null
        }
        store.writeBytes(treeUri, documentId, bytes)
        // The walk to the attachment folder is cached per path, and a new file can change what
        // resolves; dropping it keeps the next lookup honest.
        paths.invalidate()

        // The folder tree is a picture of the filesystem, and this just changed it — usually by
        // creating the attachment folder itself. Without this the drawer keeps showing the tree it
        // walked before the picture was filed, with no row for the folder the picture went into.
        // A failed walk is tolerated: the picture is filed and the note's reference to it is good,
        // so the only thing at stake is how soon the drawer catches up, which a later refresh does.
        runCatching { repoRepository.refreshFolderTree() }

        if (folderPath.isEmpty()) unique else "$folderPath/$unique"
    }

    /**
     * Resolves the attachment folder, creating it — and any missing parent — the first time a picture
     * is filed there, so the user never has to make the folder by hand.
     */
    private suspend fun ensureFolder(treeUri: Uri, folderPath: String): String? {
        if (folderPath.isEmpty()) return store.rootDocumentId(treeUri)

        val existing = try {
            paths.resolveDocumentId(treeUri, folderPath)
        } catch (e: DataError) {
            null
        }
        if (existing != null) return existing

        var parentId = store.rootDocumentId(treeUri)
        var walked = ""
        for (segment in folderPath.split('/').filter { it.isNotEmpty() }) {
            walked = if (walked.isEmpty()) segment else "$walked/$segment"
            val child = store.listChildren(treeUri, parentId)
                .firstOrNull { it.displayName == segment && it.isDirectory }
                ?.documentId
                ?: try {
                    store.createDirectory(treeUri, parentId, segment)
                } catch (e: DataError) {
                    return null
                }
            parentId = child
        }
        return parentId
    }

    private fun readPicked(uri: Uri): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { readAtMost(it, MAX_IMAGE_BYTES) }
    }.getOrNull()

    private fun displayNameOf(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()

    private fun readAtMost(stream: InputStream, maxBytes: Int): ByteArray? {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = stream.read(chunk)
            if (read < 0) break
            if (buffer.size() + read > maxBytes) return null
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    /** The provider needs a type to create the document with; the extension is what it can see. */
    private fun mimeTypeOf(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "heic" -> "image/heic"
        else -> "image/*"
    }

    private companion object {
        const val DEFAULT_FILE_NAME = "image.png"
        const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
    }
}
