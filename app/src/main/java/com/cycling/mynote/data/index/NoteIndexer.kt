package com.cycling.mynote.data.index

import android.net.Uri
import com.cycling.mynote.core.error.DataError
import com.cycling.mynote.data.markdown.FrontMatterParser
import com.cycling.mynote.data.markdown.NoteTextExtractor
import com.cycling.mynote.data.markdown.stripInlineMarkdown
import com.cycling.mynote.data.repo.ScanResult
import com.cycling.mynote.data.repo.ScannedNote
import com.cycling.mynote.data.saf.DocumentTreeStore
import javax.inject.Inject
import javax.inject.Singleton

/** What a refresh did, so the UI can report it and the drawer can flag external edits. */
sealed interface IndexOutcome {
    data class Success(
        val indexed: Int,
        val removed: Int,
        val externallyModified: Int,
        val durationMs: Long,
    ) : IndexOutcome

    data class Failure(val error: DataError) : IndexOutcome
}

/**
 * Builds and maintains the disposable search index from the Markdown folder.
 *
 * Scanning is [RepoScanner]'s job and is passed in, so the repository can update its stats and
 * folder tree from the same walk instead of paying for a second traversal of the provider.
 *
 * Reads a file only when the scan reports it as new or changed, comparing `(lastModified, size)`
 * against the indexed fingerprint. Both entry points return an [IndexOutcome] instead of throwing,
 * because a broken index must degrade search and nothing else — the notes are still readable
 * without it.
 */
@Singleton
class NoteIndexer @Inject constructor(
    private val store: DocumentTreeStore,
    private val dao: NoteIndexDao,
) {

    /** Discards the index and reads every note again. */
    suspend fun rebuild(
        treeUri: Uri,
        scan: ScanResult,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): IndexOutcome {
        val startedAt = System.currentTimeMillis()
        return try {
            val total = scan.notes.size
            val entities = mutableListOf<NoteIndexEntity>()

            scan.notes.forEachIndexed { position, note ->
                entities += index(treeUri, note)
                onProgress(position + 1, total)
            }

            dao.clear()
            entities.chunked(BATCH_SIZE).forEach { dao.upsertAll(it) }

            IndexOutcome.Success(
                indexed = entities.size,
                removed = 0,
                externallyModified = 0,
                durationMs = System.currentTimeMillis() - startedAt,
            )
        } catch (e: DataError) {
            IndexOutcome.Failure(e)
        }
    }

    /**
     * Brings the index in line with [scan], touching only what changed.
     *
     * A note whose `(lastModified, size)` no longer matches what was indexed was written by
     * something other than this app — a desktop editor, a sync client — and is counted so the
     * drawer can say so.
     */
    suspend fun refresh(
        treeUri: Uri,
        scan: ScanResult,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): IndexOutcome {
        val startedAt = System.currentTimeMillis()
        return try {
            val indexed = dao.fingerprints().associateBy { it.noteId }
            val scannedPaths = scan.notes.mapTo(mutableSetOf()) { it.relativePath }

            val stale = scan.notes.filter { note ->
                val previous = indexed[note.relativePath]
                previous == null ||
                    previous.modifiedAt != note.modifiedAt ||
                    previous.sizeBytes != note.sizeBytes
            }

            val externalCount = stale.count { note ->
                indexed[note.relativePath]?.let { previous ->
                    previous.modifiedAt != note.modifiedAt && previous.modifiedAt != 0L
                } == true
            }

            val entities = mutableListOf<NoteIndexEntity>()
            stale.forEachIndexed { position, note ->
                entities += index(treeUri, note)
                onProgress(position + 1, stale.size)
            }
            entities.chunked(BATCH_SIZE).forEach { dao.upsertAll(it) }

            val removed = indexed.keys.filterNot { it in scannedPaths }
            removed.chunked(BATCH_SIZE).forEach { dao.deleteByNoteIds(it) }

            IndexOutcome.Success(
                indexed = entities.size,
                removed = removed.size,
                externallyModified = externalCount,
                durationMs = System.currentTimeMillis() - startedAt,
            )
        } catch (e: DataError) {
            IndexOutcome.Failure(e)
        }
    }

    /** Replaces a single note's index row without a full scan; used right after saving. */
    suspend fun upsert(treeUri: Uri, note: ScannedNote): IndexOutcome = try {
        dao.upsertAll(listOf(index(treeUri, note)))
        IndexOutcome.Success(indexed = 1, removed = 0, externallyModified = 0, durationMs = 0)
    } catch (e: DataError) {
        IndexOutcome.Failure(e)
    }

    private fun index(treeUri: Uri, note: ScannedNote): NoteIndexEntity {
        val raw = store.readText(treeUri, note.documentId)
        val parsed = FrontMatterParser.parse(raw)
        val text = NoteTextExtractor.extract(raw, note.fileName, parsed)
        val plainBody = toPlainBody(raw.substring(parsed.bodyStartOffset.coerceIn(0, raw.length)))

        return NoteIndexEntity(
            noteId = note.relativePath,
            documentId = note.documentId,
            fileName = note.fileName,
            title = text.title,
            folder = note.parentPath,
            snippet = text.snippet,
            tags = parsed.frontMatter.tags,
            aliases = parsed.frontMatter.aliases,
            isPinned = parsed.frontMatter.isPinned,
            isFavorite = parsed.frontMatter.isFavorite,
            modifiedAt = note.modifiedAt,
            sizeBytes = note.sizeBytes,
            hasFrontMatter = parsed.bodyStartOffset > 0,
            plainBody = plainBody,
            searchText = buildSearchText(text.title, parsed, plainBody),
            indexedAt = System.currentTimeMillis(),
        )
    }

    private fun toPlainBody(body: String): String = body
        .lineSequence()
        .map { stripInlineMarkdown(it.removePrefix("#").trimEnd()) }
        .filter { it.isNotBlank() }
        .joinToString("\n")

    private fun buildSearchText(
        title: String,
        parsed: FrontMatterParser.ParseResult,
        plainBody: String,
    ): String = buildString {
        append(title.lowercase())
        parsed.frontMatter.tags.forEach { append('\n').append(it.lowercase()) }
        parsed.frontMatter.aliases.forEach { append('\n').append(it.lowercase()) }
        append('\n').append(plainBody.lowercase())
    }

    private companion object {
        /** Room's SQLite bindings cap out well before this; it only bounds one transaction. */
        const val BATCH_SIZE = 64
    }
}
