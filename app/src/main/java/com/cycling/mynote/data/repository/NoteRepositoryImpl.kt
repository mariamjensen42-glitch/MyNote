package com.cycling.mynote.data.repository

import android.net.Uri
import com.cycling.mynote.core.error.DataError
import com.cycling.mynote.core.model.FrontMatter
import com.cycling.mynote.core.model.Note
import com.cycling.mynote.core.model.NoteDocument
import com.cycling.mynote.core.model.NoteFilter
import com.cycling.mynote.core.model.NoteSort
import com.cycling.mynote.data.index.NoteIndexDao
import com.cycling.mynote.data.index.NoteIndexEntity
import com.cycling.mynote.data.markdown.FrontMatterParser
import com.cycling.mynote.data.markdown.NoteTextExtractor
import com.cycling.mynote.data.markdown.stripInlineMarkdown
import com.cycling.mynote.data.repo.NoteNaming
import com.cycling.mynote.data.repo.RepoPathResolver
import com.cycling.mynote.data.repo.RepoSession
import com.cycling.mynote.data.saf.DocumentTreeStore
import com.cycling.mynote.di.IoDispatcher
import com.cycling.mynote.domain.repository.NoteQuery
import com.cycling.mynote.domain.repository.NoteRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Reads and writes the Markdown files and keeps the index in step with them.
 *
 * The ordering rule is: write the file first, update the index second. An interruption can
 * therefore leave an index row that is stale — which the next refresh repairs from the file — but
 * can never leave a file the app claims to have written and did not.
 *
 * All mutation goes through [mutate], which serialises read-modify-write cycles. Two concurrent
 * `setTags` calls would otherwise both read the same original text and the second would silently
 * undo the first.
 */
@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val session: RepoSession,
    private val store: DocumentTreeStore,
    private val paths: RepoPathResolver,
    private val dao: NoteIndexDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) : NoteRepository {

    private val mutationMutex = Mutex()

    override fun observeNotes(query: NoteQuery): Flow<List<Note>> =
        dao.observeAll().map { entities ->
            entities.asSequence()
                .map(NoteIndexEntity::toNote)
                .filter { matchesFilter(it, query) }
                .sortedWith(comparator(query.sort))
                .toList()
        }

    override fun observeNote(noteId: String): Flow<Note?> =
        dao.observeByNoteId(noteId).map { it?.toNote() }

    override fun observeAllTags(): Flow<List<String>> = dao.observeAll().map { entities ->
        entities.flatMap { it.tags }.distinct().sorted()
    }

    override suspend fun openNote(noteId: String): NoteDocument = withContext(io) {
        val uri = session.requireTreeUri()
        val entity = dao.findByNoteId(noteId)
        val documentId = entity?.documentId ?: paths.resolveDocumentId(uri, noteId)
        val raw = store.readText(uri, documentId)
        val parsed = FrontMatterParser.parse(raw)
        val text = NoteTextExtractor.extract(raw, noteId.substringAfterLast('/'), parsed)

        NoteDocument(
            note = entity?.toNote() ?: Note(
                id = noteId,
                documentId = documentId,
                fileName = noteId.substringAfterLast('/'),
                title = text.title,
                folder = noteId.substringBeforeLast('/', ""),
                snippet = text.snippet,
                tags = parsed.frontMatter.tags,
                aliases = parsed.frontMatter.aliases,
                isPinned = parsed.frontMatter.isPinned,
                isFavorite = parsed.frontMatter.isFavorite,
                modifiedAt = 0L,
                sizeBytes = raw.toByteArray(Charsets.UTF_8).size.toLong(),
                hasFrontMatter = parsed.bodyStartOffset > 0,
            ),
            raw = raw,
            frontMatter = parsed.frontMatter,
            stats = text.stats,
        )
    }

    override suspend fun saveNote(noteId: String, raw: String): Note = mutate(noteId) { uri, documentId ->
        store.writeText(uri, documentId, raw)
        reindex(uri, noteId, documentId)
    }

    override suspend fun createNote(title: String, folderPath: String): Note = withContext(io) {
        val uri = session.requireTreeUri()
        val parentId = if (folderPath.isEmpty()) {
            store.rootDocumentId(uri)
        } else {
            paths.resolveDocumentId(uri, folderPath)
        }

        val fileName = uniqueFileName(uri, parentId, NoteNaming.toFileName(title))
        val documentId = store.createFile(uri, parentId, fileName)
        val noteId = if (folderPath.isEmpty()) fileName else "$folderPath/$fileName"

        store.writeText(uri, documentId, newNoteTemplate(NoteNaming.titleFromFileName(fileName)))
        paths.invalidate()
        reindex(uri, noteId, documentId)
    }

    override suspend fun openOrCreateDiaryEntry(date: LocalDate): Note = withContext(io) {
        val uri = session.requireTreeUri()
        val fileName = NoteNaming.diaryFileName(date)
        val noteId = "$DIARY_FOLDER/$fileName"

        dao.findByNoteId(noteId)?.let { return@withContext it.toNote() }

        val rootId = store.rootDocumentId(uri)
        val diaryId = store.listChildren(uri, rootId)
            .firstOrNull { it.displayName == DIARY_FOLDER && it.isDirectory }
            ?.documentId
            ?: store.createDirectory(uri, rootId, DIARY_FOLDER)

        val existing = store.listChildren(uri, diaryId).firstOrNull { it.displayName == fileName }
        val documentId = existing?.documentId
            ?: store.createFile(uri, diaryId, fileName).also { created ->
                store.writeText(uri, created, diaryTemplate(date))
            }

        paths.invalidate()
        reindex(uri, noteId, documentId)
    }

    override suspend fun renameNote(noteId: String, newTitle: String): Note = mutate(noteId) { uri, documentId ->
        val folder = noteId.substringBeforeLast('/', "")
        val parentId = paths.resolveParentDocumentId(uri, noteId)
        val fileName = uniqueFileName(uri, parentId, NoteNaming.toFileName(newTitle))
        val newNoteId = if (folder.isEmpty()) fileName else "$folder/$fileName"

        val renamedInPlace = store.rename(uri, documentId, fileName)
        if (!renamedInPlace) {
            // Providers that do not implement renameDocument report the original name back; a copy
            // plus delete is the portable equivalent and keeps the note's content either way.
            val copiedId = store.copyFile(uri, documentId, parentId, fileName)
            store.delete(uri, documentId)
            paths.invalidate()
            dao.deleteByNoteIds(listOf(noteId))
            return@mutate reindex(uri, newNoteId, copiedId)
        }

        paths.invalidate()
        dao.deleteByNoteIds(listOf(noteId))
        reindex(uri, newNoteId, documentId)
    }

    override suspend fun deleteNote(noteId: String) = withContext(io) {
        val uri = session.requireTreeUri()
        val documentId = pathFor(uri, noteId)
        store.delete(uri, documentId)
        paths.invalidate()
        dao.deleteByNoteIds(listOf(noteId))
        Unit
    }

    override suspend fun moveNote(noteId: String, targetFolderPath: String): Note = mutate(noteId) { uri, documentId ->
        val fileName = noteId.substringAfterLast('/')
        val targetParentId = if (targetFolderPath.isEmpty()) {
            store.rootDocumentId(uri)
        } else {
            paths.resolveDocumentId(uri, targetFolderPath)
        }

        val newNoteId = if (targetFolderPath.isEmpty()) fileName else "$targetFolderPath/$fileName"
        if (newNoteId == noteId) return@mutate reindex(uri, noteId, documentId)

        val conflict = store.listChildren(uri, targetParentId).any { it.displayName == fileName }
        if (conflict) throw DataError.NameConflict(fileName)

        val copiedId = store.copyFile(uri, documentId, targetParentId, fileName)
        store.delete(uri, documentId)
        paths.invalidate()
        dao.deleteByNoteIds(listOf(noteId))
        reindex(uri, newNoteId, copiedId)
    }

    override suspend fun setPinned(noteId: String, pinned: Boolean) {
        editFrontMatter(noteId) { it.copy(isPinned = pinned) }
    }

    override suspend fun setFavorite(noteId: String, favorite: Boolean) {
        editFrontMatter(noteId) { it.copy(isFavorite = favorite) }
    }

    override suspend fun setTags(noteId: String, tags: List<String>) {
        editFrontMatter(noteId) { it.copy(tags = tags.distinct()) }
    }

    override suspend fun setAliases(noteId: String, aliases: List<String>) {
        editFrontMatter(noteId) { it.copy(aliases = aliases.distinct()) }
    }

    override suspend fun appendToInbox(text: String, sourceLabel: String?): Note = withContext(io) {        val uri = session.requireTreeUri()
        val existing = dao.findByNoteId(INBOX_FILE)
        val documentId = existing?.documentId ?: run {
            val rootId = store.rootDocumentId(uri)
            val created = store.listChildren(uri, rootId)
                .firstOrNull { it.displayName == INBOX_FILE && !it.isDirectory }
                ?.documentId
                ?: store.createFile(uri, rootId, INBOX_FILE)
            paths.invalidate()
            created
        }

        val current = runCatching { store.readText(uri, documentId) }.getOrDefault("")
        val stamp = CAPTURE_STAMP.format(LocalDateTime.now())
        val attribution = sourceLabel?.let { "（来自 $it）" }.orEmpty()
        val updated = buildString {
            append(current.trimEnd('\n', '\r', ' ', '\t'))
            append("\n\n- ")
            append(stamp).append(' ')
            append(text.trim())
            append(attribution)
            append('\n')
        }

        store.writeText(uri, documentId, updated)
        reindex(uri, INBOX_FILE, documentId)
    }

    /** Applies [transform] to the note's front matter and rewrites the file. */
    private suspend fun editFrontMatter(
        noteId: String,
        transform: (FrontMatter) -> FrontMatter,
    ): Note = mutate(noteId) { uri, documentId ->
        val raw = store.readText(uri, documentId)
        val parsed = FrontMatterParser.parse(raw)
        val body = raw.substring(parsed.bodyStartOffset.coerceIn(0, raw.length))
        val updated = FrontMatterParser.render(transform(parsed.frontMatter)) + body
        store.writeText(uri, documentId, updated)
        reindex(uri, noteId, documentId)
    }

    /**
     * Serialises a read-modify-write cycle on one note.
     *
     * @param noteId the path used to resolve the document when the index has no row for it.
     */
    private suspend fun mutate(
        noteId: String,
        block: suspend (uri: Uri, documentId: String) -> Note,
    ): Note = mutationMutex.withLock {
        withContext(io) {
            val uri = session.requireTreeUri()
            block(uri, pathFor(uri, noteId))
        }
    }

    private suspend fun pathFor(uri: Uri, noteId: String): String =
        dao.findByNoteId(noteId)?.documentId ?: paths.resolveDocumentId(uri, noteId)

    /** Re-reads the file and replaces its index row, returning the note as it now reads. */
    private suspend fun reindex(uri: Uri, noteId: String, documentId: String): Note {
        val raw = store.readText(uri, documentId)
        val parsed = FrontMatterParser.parse(raw)
        val fileName = noteId.substringAfterLast('/')
        val text = NoteTextExtractor.extract(raw, fileName, parsed)
        val plainBody = plainBodyOf(raw.substring(parsed.bodyStartOffset.coerceIn(0, raw.length)))
        val lastModified = System.currentTimeMillis()

        val entity = NoteIndexEntity(
            noteId = noteId,
            documentId = documentId,
            fileName = fileName,
            title = text.title,
            folder = noteId.substringBeforeLast('/', ""),
            snippet = text.snippet,
            tags = parsed.frontMatter.tags,
            aliases = parsed.frontMatter.aliases,
            isPinned = parsed.frontMatter.isPinned,
            isFavorite = parsed.frontMatter.isFavorite,
            modifiedAt = lastModified,
            sizeBytes = raw.toByteArray(Charsets.UTF_8).size.toLong(),
            hasFrontMatter = parsed.bodyStartOffset > 0,
            plainBody = plainBody,
            searchText = buildString {
                append(text.title.lowercase())
                parsed.frontMatter.tags.forEach { append('\n').append(it.lowercase()) }
                parsed.frontMatter.aliases.forEach { append('\n').append(it.lowercase()) }
                append('\n').append(plainBody.lowercase())
            },
            indexedAt = lastModified,
        )

        dao.upsertAll(listOf(entity))
        return entity.toNote()
    }

    /** Finds a file name that does not collide inside [parentId], appending ` 2`, ` 3`, … */
    private suspend fun uniqueFileName(uri: Uri, parentId: String, desired: String): String {
        val taken = store.listChildren(uri, parentId).mapTo(mutableSetOf()) { it.displayName }
        if (desired !in taken) return desired

        val base = desired.substringBeforeLast('.')
        val extension = desired.substringAfterLast('.', "md")
        var suffix = 2
        while (true) {
            val candidate = "$base $suffix.$extension"
            if (candidate !in taken) return candidate
            suffix++
            if (suffix > MAX_NAME_ATTEMPTS) throw DataError.NameConflict(desired)
        }
    }

    private fun plainBodyOf(body: String): String = body
        .lineSequence()
        .map { stripInlineMarkdown(it.removePrefix("#").trimEnd()) }
        .filter { it.isNotBlank() }
        .joinToString("\n")

    private fun matchesFilter(note: Note, query: NoteQuery): Boolean {
        query.folderPath?.let { folder ->
            val inFolder = if (folder.isEmpty()) {
                note.folder.isEmpty() || note.folder == ""
            } else {
                note.folder == folder || note.folder.startsWith("$folder/")
            }
            if (!inFolder) return false
        }

        query.tag?.let { tag -> if (tag !in note.tags) return false }

        return when (query.filter) {
            NoteFilter.ALL, NoteFilter.FOLDER -> true
            // "最近" is a rolling window rather than a count, so the chip means the same thing
            // whether the repository holds ten notes or ten thousand.
            NoteFilter.RECENT -> note.modifiedAt >= System.currentTimeMillis() - RECENT_WINDOW_MS
            NoteFilter.TAGGED -> note.tags.isNotEmpty()
            NoteFilter.FAVORITE -> note.isFavorite
        }
    }

    /**
     * Pinned notes always float to the top; the chosen sort only orders within each group, which is
     * what makes the pin useful rather than decorative.
     */
    private fun comparator(sort: NoteSort): Comparator<Note> {
        val bySort = when (sort) {
            NoteSort.UPDATED_DESC -> compareByDescending<Note> { it.modifiedAt }
            NoteSort.UPDATED_ASC -> compareBy<Note> { it.modifiedAt }
            NoteSort.TITLE_ASC -> compareBy { it.title.lowercase() }
            NoteSort.CREATED_DESC -> compareByDescending { it.modifiedAt }
        }
        return compareByDescending<Note> { it.isPinned }
            .then(bySort)
            .thenBy { it.title.lowercase() }
    }

    private fun newNoteTemplate(title: String): String = buildString {
        append(FrontMatterParser.render(FrontMatter(created = today())))
        append("# ").append(title).append("\n\n")
    }

    private fun diaryTemplate(date: LocalDate): String = buildString {
        append(FrontMatterParser.render(FrontMatter(created = date.toString())))
        append("# ").append(date.toString()).append(" 日记\n\n")
        append("## 今天\n\n")
        append("## 想法\n\n")
        append("## 待办\n\n- [ ] \n")
    }

    private fun today(): String = LocalDate.now().toString()

    private companion object {
        const val DIARY_FOLDER = "日记"
        const val INBOX_FILE = "Inbox.md"
        const val MAX_NAME_ATTEMPTS = 200

        /** The "最近" chip's window. */
        const val RECENT_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

        val CAPTURE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
