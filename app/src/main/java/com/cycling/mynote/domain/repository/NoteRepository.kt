package com.cycling.mynote.domain.repository

import com.cycling.mynote.core.model.Note
import com.cycling.mynote.core.model.NoteDocument
import com.cycling.mynote.core.model.NoteFilter
import com.cycling.mynote.core.model.NoteSort
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * How the library list is narrowed and ordered.
 *
 * @param folderPath `null` means every folder; `""` means the repository root.
 * @param tag `null` means every tag.
 */
data class NoteQuery(
    val filter: NoteFilter = NoteFilter.ALL,
    val sort: NoteSort = NoteSort.UPDATED_DESC,
    val folderPath: String? = null,
    val tag: String? = null,
) {
    companion object {
        val Default = NoteQuery()
    }
}

/**
 * Reads and writes the Markdown notes themselves.
 *
 * Every mutation writes the `.md` file first and only then updates the index, so an interrupted
 * write can lose index freshness but never a note.
 */
interface NoteRepository {

    fun observeNotes(query: NoteQuery = NoteQuery.Default): Flow<List<Note>>

    fun observeNote(noteId: String): Flow<Note?>

    fun observeAllTags(): Flow<List<String>>

    suspend fun openNote(noteId: String): NoteDocument

    /** Writes [raw] and returns the note as it now reads. */
    suspend fun saveNote(noteId: String, raw: String): Note

    suspend fun createNote(title: String, folderPath: String = ""): Note

    /** The diary entry for [date], creating `日记/yyyy-MM-dd.md` when it does not exist yet. */
    suspend fun openOrCreateDiaryEntry(date: LocalDate): Note

    suspend fun renameNote(noteId: String, newTitle: String): Note

    suspend fun deleteNote(noteId: String)

    suspend fun moveNote(noteId: String, targetFolderPath: String): Note

    suspend fun setPinned(noteId: String, pinned: Boolean)

    suspend fun setFavorite(noteId: String, favorite: Boolean)

    suspend fun setTags(noteId: String, tags: List<String>)

    suspend fun setAliases(noteId: String, aliases: List<String>)

    /** Creates the note if needed and appends [text] under a timestamped bullet list. */
    suspend fun appendToInbox(text: String, sourceLabel: String? = null): Note
}
