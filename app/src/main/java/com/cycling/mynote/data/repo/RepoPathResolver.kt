package com.cycling.mynote.data.repo

import android.net.Uri
import com.cycling.mynote.core.error.DataError
import com.cycling.mynote.data.saf.DocumentTreeStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Translates between repository-relative paths and the SAF document ids needed to touch the files.
 *
 * Notes are identified by path everywhere above this class, because paths survive the user
 * re-granting access to the same folder. Paths have to be turned back into document ids to do any
 * I/O, and doing that with one provider query per path segment on every keystroke-triggered save
 * would be wasteful, so successful lookups are memoised. Any structural change (create, rename,
 * move, delete) invalidates the whole map: a stale entry would make the app write into a file the
 * user has since moved, which is far worse than re-resolving.
 */
@Singleton
class RepoPathResolver @Inject constructor(
    private val store: DocumentTreeStore,
) {

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, String>()

    /** The document id for [relativePath]. */
    suspend fun resolveDocumentId(treeUri: Uri, relativePath: String): String {
        val key = cacheKey(treeUri, relativePath)
        mutex.withLock { cache[key] }?.let { return it }

        val resolved = walk(treeUri, relativePath)
        mutex.withLock { cache[key] = resolved }
        return resolved
    }

    /**
     * The document id of the folder that contains [relativePath].
     *
     * `""` and any path naming the repository itself resolve to the root document id.
     */
    suspend fun resolveParentDocumentId(treeUri: Uri, relativePath: String): String {
        val parent = relativePath.substringBeforeLast('/', "")
        if (parent.isEmpty()) return store.rootDocumentId(treeUri)
        return resolveDocumentId(treeUri, parent)
    }

    suspend fun invalidate() {
        mutex.withLock { cache.clear() }
    }

    private suspend fun walk(treeUri: Uri, relativePath: String): String {
        if (relativePath.isEmpty()) return store.rootDocumentId(treeUri)

        var currentId = store.rootDocumentId(treeUri)
        var walked = ""
        for (segment in relativePath.split('/')) {
            val children = store.listChildren(treeUri, currentId)
            val match = children.firstOrNull { it.displayName == segment }
                ?: throw DataError.NoteNotFound(relativePath)
            currentId = match.documentId
            walked = if (walked.isEmpty()) segment else "$walked/$segment"
        }
        return currentId
    }

    private fun cacheKey(treeUri: Uri, relativePath: String): String = "$treeUri|$relativePath"
}
