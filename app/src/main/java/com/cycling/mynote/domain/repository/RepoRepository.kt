package com.cycling.mynote.domain.repository

import com.cycling.mynote.core.model.FolderNode
import com.cycling.mynote.core.model.IndexStatus
import com.cycling.mynote.core.model.LastRepo
import com.cycling.mynote.core.model.RepoInfo
import com.cycling.mynote.core.model.RepoStats
import com.cycling.mynote.data.index.IndexOutcome
import kotlinx.coroutines.flow.Flow

/**
 * The granted Markdown folder: its identity, its index, and the folder tree inside it.
 *
 * The index lives here rather than in a repository of its own because it has no meaning apart from
 * a repository — it is a cache of one folder's contents and is thrown away with the grant.
 */
interface RepoRepository {

    fun observeRepo(): Flow<RepoInfo?>

    fun observeStats(): Flow<RepoStats>

    fun observeIndexStatus(): Flow<IndexStatus>

    fun observeFolderTree(): Flow<FolderNode>

    /** The last released repository, offered as `继续上次的仓库 · <name>` on the onboarding screen. */
    fun observeLastRepo(): Flow<LastRepo?>

    suspend fun currentRepo(): RepoInfo?

    /**
     * Persists the grant on [treeUri] and makes it the current repository.
     *
     * @return the outcome of the first index build. Throws [com.cycling.mynote.core.error.DataError]
     *   when the folder itself cannot be read, which is a different failure from an index that
     *   could not be built over a folder that is fine.
     */
    suspend fun grantAccess(treeUri: String): IndexOutcome

    /** Drops the grant and clears the index. */
    suspend fun release()

    suspend fun markOpened()

    /**
     * Brings the index in line with the folder.
     *
     * @param force rebuilds from scratch instead of comparing fingerprints.
     */
    suspend fun refreshIndex(
        force: Boolean = false,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): IndexOutcome

    suspend fun createFolder(parentPath: String, name: String): String

    /**
     * Walks the folder again and republishes [observeFolderTree] and [observeStats] from what it
     * found, without touching the index.
     *
     * The tree can only come from the filesystem — the index knows about notes, not about empty
     * folders — so anything that can add a folder has to ask for this: filing an attachment creates
     * `attachments/` on first use, and until the tree is walked again the drawer has no row for a
     * folder the note's own picture is already stored in.
     */
    suspend fun refreshFolderTree()

    suspend fun folderExists(path: String): Boolean

    /** Creates `Inbox.md` and `日记/` if the repository has neither. */
    suspend fun ensureStarterContent()
}
