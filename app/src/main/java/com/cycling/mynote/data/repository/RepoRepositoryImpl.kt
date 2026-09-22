package com.cycling.mynote.data.repository

import android.net.Uri
import com.cycling.mynote.core.error.DataError
import com.cycling.mynote.core.model.FolderNode
import com.cycling.mynote.core.model.IndexState
import com.cycling.mynote.core.model.IndexStatus
import com.cycling.mynote.core.model.LastRepo
import com.cycling.mynote.core.model.RepoInfo
import com.cycling.mynote.core.model.RepoStats
import com.cycling.mynote.data.index.IndexOutcome
import com.cycling.mynote.data.index.NoteIndexDao
import com.cycling.mynote.data.index.NoteIndexer
import com.cycling.mynote.data.prefs.PreferencesStore
import com.cycling.mynote.data.repo.RepoPathResolver
import com.cycling.mynote.data.repo.RepoScanner
import com.cycling.mynote.data.repo.RepoSession
import com.cycling.mynote.data.repo.ScannedNote
import com.cycling.mynote.data.saf.DocumentTreeStore
import com.cycling.mynote.di.ApplicationScope
import com.cycling.mynote.di.IoDispatcher
import com.cycling.mynote.domain.repository.RepoRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns the granted folder: its identity, its stats, its folder tree and its index.
 *
 * One scan backs all three. A scan is the only operation here that costs a provider round trip per
 * folder, so re-running it to answer a second question would double the price of every refresh;
 * [scanAndPublish] is the single place that walks the tree and everything else reads its result.
 */
@Singleton
class RepoRepositoryImpl @Inject constructor(
    private val session: RepoSession,
    private val store: DocumentTreeStore,
    private val scanner: RepoScanner,
    private val indexer: NoteIndexer,
    private val pathResolver: RepoPathResolver,
    private val dao: NoteIndexDao,
    private val preferences: PreferencesStore,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationScope private val appScope: CoroutineScope,
) : RepoRepository {

    private val indexMutex = Mutex()
    private val warmUpMutex = Mutex()
    private var warmedUp = false

    private val stats = MutableStateFlow(RepoStats(0, 0, 0))
    private val folderTree = MutableStateFlow(ROOT)
    private val indexStatus = MutableStateFlow(IndexStatus())

    override fun observeRepo(): Flow<RepoInfo?> = combine(
        session.treeUri,
        session.displayName,
        session.pathLabel,
        stats,
        preferences.lastOpenedAt(),
    ) { treeUri, name, label, currentStats, lastOpenedAt ->
        if (treeUri == null || name == null) {
            null
        } else {
            RepoInfo(
                treeUri = treeUri.toString(),
                name = name,
                pathLabel = label ?: treeUri.toString(),
                noteCount = currentStats.noteCount,
                totalBytes = currentStats.totalBytes,
                lastOpenedAt = lastOpenedAt,
            )
        }
    }.withWarmUp()

    override fun observeStats(): Flow<RepoStats> = stats.onStart { warmUp() }

    override fun observeIndexStatus(): Flow<IndexStatus> = indexStatus.onStart { warmUp() }

    override fun observeFolderTree(): Flow<FolderNode> = folderTree.onStart { warmUp() }

    override fun observeLastRepo(): Flow<LastRepo?> = session.lastRepo

    override suspend fun currentRepo(): RepoInfo? = observeRepo().first()

    override suspend fun grantAccess(treeUri: String): IndexOutcome {
        val uri = Uri.parse(treeUri)
        session.grantAccess(uri)
        pathResolver.invalidate()
        if (!store.isAccessible(uri)) throw DataError.RepoUnavailable()
        warmedUp = true
        return refreshIndex(force = true)
    }

    override suspend fun release() {
        session.release()
        withContext(io) {
            dao.clear()
            pathResolver.invalidate()
        }
        stats.value = RepoStats(0, 0, 0)
        folderTree.value = ROOT
        indexStatus.value = IndexStatus()
        warmedUp = true
    }

    override suspend fun markOpened() = session.markOpened()

    override suspend fun refreshIndex(
        force: Boolean,
        onProgress: (done: Int, total: Int) -> Unit,
    ): IndexOutcome = indexMutex.withLock {
        val uri = session.treeUriOrNull()
            ?: return@withLock IndexOutcome.Failure(DataError.RepoNotConfigured())

        indexStatus.value = indexStatus.value.copy(state = IndexState.BUILDING, message = null)

        val scan = try {
            scanAndPublish(uri)
        } catch (e: DataError) {
            indexStatus.value = indexStatus.value.copy(state = IndexState.FAILED, message = e.message)
            return@withLock IndexOutcome.Failure(e)
        }

        val outcome = withContext(io) {
            if (force) indexer.rebuild(uri, scan, onProgress) else indexer.refresh(uri, scan, onProgress)
        }

        when (outcome) {
            is IndexOutcome.Success -> {
                val builtAt = System.currentTimeMillis()
                preferences.setIndexBuiltAt(builtAt)
                indexStatus.value = IndexStatus(
                    state = IndexState.IDLE,
                    builtAt = builtAt,
                    indexedNoteCount = scan.stats.noteCount,
                    message = null,
                )
            }

            is IndexOutcome.Failure -> indexStatus.value = indexStatus.value.copy(
                state = IndexState.FAILED,
                message = outcome.error.message,
            )
        }

        outcome
    }

    override suspend fun createFolder(parentPath: String, name: String): String {
        val uri = session.requireTreeUri()
        return withContext(io) {
            val parentId = pathResolver.resolveParentDocumentId(uri, join(parentPath, name))
            if (store.listChildren(uri, parentId).any { it.displayName == name }) {
                throw DataError.NameConflict(name)
            }
            store.createDirectory(uri, parentId, name)
            pathResolver.invalidate()
            scanAndPublish(uri)
            join(parentPath, name)
        }
    }

    override suspend fun folderExists(path: String): Boolean {
        val uri = session.treeUriOrNull() ?: return false
        if (path.isEmpty()) return true
        return withContext(io) { runCatching { pathResolver.resolveDocumentId(uri, path) }.isSuccess }
    }

    override suspend fun ensureStarterContent() {
        val uri = session.treeUriOrNull() ?: return
        withContext(io) {
            val scan = scanAndPublish(uri)
            if (scan.notes.isNotEmpty()) return@withContext

            val rootId = store.rootDocumentId(uri)
            if (scan.folders.none { it.relativePath == DIARY_FOLDER }) {
                store.createDirectory(uri, rootId, DIARY_FOLDER)
            }

            val created = mutableListOf<ScannedNote>()
            if (scan.notes.none { it.relativePath == INBOX_FILE }) {
                val inboxId = store.createFile(uri, rootId, INBOX_FILE)
                store.writeText(uri, inboxId, SAMPLE_INBOX)
                created += ScannedNote(
                    relativePath = INBOX_FILE,
                    documentId = inboxId,
                    parentPath = "",
                    fileName = INBOX_FILE,
                    sizeBytes = SAMPLE_INBOX.toByteArray(Charsets.UTF_8).size.toLong(),
                    modifiedAt = System.currentTimeMillis(),
                )
            }
            pathResolver.invalidate()

            // Index the files just written by the ids we already hold. A directory listing taken
            // immediately after a create can still miss them — the provider's view of the volume
            // lags the write — which would leave the brand-new repository looking empty.
            created.forEach { indexer.upsert(uri, it) }

            // Re-scan for the header counts, retrying for that same lag.
            repeat(SCAN_ATTEMPTS) { attempt ->
                val resolved = scanAndPublish(uri)
                if (resolved.notes.isNotEmpty() || attempt == SCAN_ATTEMPTS - 1) return@withContext
                delay(SCAN_RETRY_DELAY_MS)
            }
        }
    }

    /** The single place that walks the tree; publishes the stats and folder tree it produces. */
    private suspend fun scanAndPublish(uri: Uri) = withContext(io) {
        val scan = scanner.scan(uri)
        stats.value = scan.stats
        folderTree.value = scanner.buildTree(scan.folders, scan.stats.noteCount)
        scan
    }

    /**
     * Loads the persisted index size and timestamp so the chips and the settings row are correct on
     * a cold start, then refreshes in the background. Until the refresh lands the UI shows the last
     * known numbers rather than zeroes.
     */
    private suspend fun warmUp() {
        warmUpMutex.withLock {
            if (warmedUp) return
            warmedUp = true
        }
        val uri = session.treeUriOrNull() ?: return
        val indexedCount = withContext(io) { dao.count() }
        val builtAt = preferences.indexBuiltAt().first()
        indexStatus.value = IndexStatus(
            state = IndexState.IDLE,
            builtAt = builtAt,
            indexedNoteCount = indexedCount,
            message = null,
        )
        appScope.launch { runCatching { refreshIndex(force = false) } }
    }

    /** Runs [warmUp] before the first element of a repository stream reaches its collector. */
    private fun <T> Flow<T>.withWarmUp(): Flow<T> = onStart { warmUp() }

    private fun join(parent: String, name: String) =
        if (parent.isEmpty()) name else "$parent/$name"

    private companion object {
        val ROOT = FolderNode(name = "", path = "", noteCount = 0, children = emptyList())
        const val DIARY_FOLDER = "日记"
        const val INBOX_FILE = "Inbox.md"

        /** How many times to re-list the folder while the provider catches up with a new file. */
        const val SCAN_ATTEMPTS = 4
        const val SCAN_RETRY_DELAY_MS = 250L
        val SAMPLE_INBOX = """
            |---
            |tags: [收件箱]
            |---
            |
            |# Inbox
            |
            |今天要记下的三件事：
            |
            |- [ ] 把笔记仓库整理成三个主题
            |- [ ] 给重要的笔记加上标签
            |- [ ] 试试在搜索里输入 “>” 打开命令面板
            |
        """.trimMargin()
    }
}
