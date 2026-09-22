package com.cycling.mynote.data.index

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import com.cycling.mynote.core.model.Note
import kotlinx.coroutines.flow.Flow

/**
 * One indexed note.
 *
 * The primary key is the repository-relative path, not the SAF document id: re-granting access to
 * the same folder hands out new document ids but the same paths, so keying on the path keeps the
 * index valid across that.
 *
 * [searchText] is the lower-cased concatenation of everything searchable. Queries run as
 * `LIKE '%term%'` against this single column instead of through SQLite's FTS tokenizers, because
 * the notes are mostly Chinese and FTS4's `simple`/`unicode61` tokenizers do not segment CJK — a
 * search for 索引 would tokenize to one token and only match a whole-run occurrence. Substring
 * matching gets Chinese right, and for a personal archive of this size a scan over one indexed
 * column is not the bottleneck. The trade-off is deliberate and reversible: this table is
 * disposable by design, so swapping in an FTS table later costs a rebuild, not a migration.
 *
 * [plainBody] is the note's prose with Markdown syntax removed. Keeping it here rather than
 * re-reading the file per keystroke is what makes search instant, and it does not contradict the
 * "no content database" promise the app makes: this table is a cache that is deleted and rebuilt
 * from the folder at any time, and the `.md` files remain the only source of truth.
 */
@Entity(
    tableName = "note_index",
    indices = [Index("modifiedAt"), Index("folder"), Index("isPinned")],
)
data class NoteIndexEntity(
    @PrimaryKey val noteId: String,
    val documentId: String,
    val fileName: String,
    val title: String,
    val folder: String,
    val snippet: String,
    @ColumnInfo(name = "tags") val tags: List<String>,
    @ColumnInfo(name = "aliases") val aliases: List<String>,
    val isPinned: Boolean,
    val isFavorite: Boolean,
    val modifiedAt: Long,
    val sizeBytes: Long,
    val hasFrontMatter: Boolean,
    val plainBody: String,
    val searchText: String,
    val indexedAt: Long,
) {
    fun toNote(): Note = Note(
        id = noteId,
        documentId = documentId,
        fileName = fileName,
        title = title,
        folder = folder,
        snippet = snippet,
        tags = tags,
        aliases = aliases,
        isPinned = isPinned,
        isFavorite = isFavorite,
        modifiedAt = modifiedAt,
        sizeBytes = sizeBytes,
        hasFrontMatter = hasFrontMatter,
    )
}

/** The (path, modified, size) fingerprint used to spot files changed outside the app. */
data class IndexedFingerprint(
    val noteId: String,
    val documentId: String,
    val modifiedAt: Long,
    val sizeBytes: Long,
)

@Dao
interface NoteIndexDao {

    @Query("SELECT * FROM note_index ORDER BY isPinned DESC, modifiedAt DESC")
    fun observeAll(): Flow<List<NoteIndexEntity>>

    @Query("SELECT * FROM note_index WHERE noteId = :noteId LIMIT 1")
    suspend fun findByNoteId(noteId: String): NoteIndexEntity?

    @Query("SELECT * FROM note_index WHERE noteId = :noteId LIMIT 1")
    fun observeByNoteId(noteId: String): Flow<NoteIndexEntity?>

    @Query("SELECT noteId, documentId, modifiedAt, sizeBytes FROM note_index")
    suspend fun fingerprints(): List<IndexedFingerprint>

    @Query("SELECT COUNT(*) FROM note_index")
    suspend fun count(): Int

    /**
     * Substring search over the pre-lower-cased [NoteIndexEntity.searchText].
     *
     * The caller passes an already normalised term. Ranking happens in Kotlin because the position
     * of the match inside title/tags/body decides the order, and expressing that in SQL would mean
     * three more `INSTR` calls per row for no benefit at this scale.
     */
    @Query("SELECT * FROM note_index WHERE searchText LIKE '%' || :term || '%' LIMIT :limit")
    suspend fun search(term: String, limit: Int): List<NoteIndexEntity>

    @Upsert
    suspend fun upsertAll(notes: List<NoteIndexEntity>)

    @Query("DELETE FROM note_index WHERE noteId IN (:noteIds)")
    suspend fun deleteByNoteIds(noteIds: List<String>)

    @Query("DELETE FROM note_index")
    suspend fun clear()
}

/**
 * Stores the tag and alias lists as one delimited column.
 *
 * A join table would be the normalised answer, but these lists are read whole with every list row
 * and never queried by element — the search index matches tags through [NoteIndexEntity.searchText]
 * — so a join would add a query to every library render and buy nothing.
 */
class StringListConverter {

    @TypeConverter
    fun fromList(value: List<String>): String = value.joinToString(SEPARATOR)

    @TypeConverter
    fun toList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(SEPARATOR)

    private companion object {
        /** Unit separator: cannot appear in a tag typed by a user, unlike a comma or a newline. */
        const val SEPARATOR = "\u001F"
    }
}

@Database(entities = [NoteIndexEntity::class], version = 1, exportSchema = false)
@TypeConverters(StringListConverter::class)
abstract class NoteIndexDatabase : RoomDatabase() {
    abstract fun noteIndexDao(): NoteIndexDao
}
