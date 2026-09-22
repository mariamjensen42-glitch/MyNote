package com.cycling.mynote.core.model

import androidx.compose.runtime.Immutable

/**
 * A note as it appears in a list.
 *
 * [id] is the repository-relative path (for example `日记/2025-03-12.md`) rather than the SAF
 * document id: paths survive the user re-granting access to the same folder through a different
 * tree URI, which is exactly what the index is keyed on. [documentId] is the value needed to open
 * the file right now and is refreshed on every scan.
 */
@Immutable
data class Note(
    val id: String,
    val documentId: String,
    val fileName: String,
    val title: String,
    val folder: String,
    val snippet: String,
    val tags: List<String>,
    val aliases: List<String>,
    val isPinned: Boolean,
    val isFavorite: Boolean,
    val modifiedAt: Long,
    val sizeBytes: Long,
    val hasFrontMatter: Boolean,
) {
    val extension: String get() = fileName.substringAfterLast('.', "")
}

/** A note together with its Markdown source. */
@Immutable
data class NoteDocument(
    val note: Note,
    val raw: String,
    val frontMatter: FrontMatter,
    val stats: NoteStats,
)

/** Counts shown in the editor header, e.g. `12 行 · 386 字`. */
@Immutable
data class NoteStats(val lineCount: Int, val characterCount: Int)

/**
 * The subset of YAML front matter the app understands.
 *
 * [unknownLines] holds the raw lines of every key that is not modelled here. They are re-emitted
 * verbatim when the note is saved, so opening a note in the app cannot silently drop metadata the
 * user or another tool put in the file.
 */
@Immutable
data class FrontMatter(
    val title: String? = null,
    val tags: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val created: String? = null,
    val updated: String? = null,
    val unknownLines: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = title == null && tags.isEmpty() && aliases.isEmpty() && !isPinned &&
            !isFavorite && created == null && updated == null && unknownLines.isEmpty()

    /** The front matter the app itself derives; a note with only these keys needs no block. */
    val hasContent: Boolean
        get() = tags.isNotEmpty() || aliases.isNotEmpty() || isPinned || isFavorite ||
            title != null || created != null || updated != null || unknownLines.isNotEmpty()

    companion object {
        const val DELIMITER = "---"
        const val KEY_TITLE = "title"
        const val KEY_TAGS = "tags"
        const val KEY_ALIASES = "aliases"
        const val KEY_PINNED = "pinned"
        const val KEY_FAVORITE = "favorite"
        const val KEY_CREATED = "created"
        const val KEY_UPDATED = "updated"

        val EMPTY = FrontMatter()
    }
}

/** Which slice of the library the list is showing. Mirrors the chips above the note list. */
enum class NoteFilter(val label: String) {
    ALL("全部"),
    RECENT("最近"),
    TAGGED("标签"),
    FAVORITE("收藏"),
    FOLDER("文件夹"),
}

enum class NoteSort(val label: String) {
    UPDATED_DESC("最近更新"),
    UPDATED_ASC("最早更新"),
    TITLE_ASC("标题"),
    CREATED_DESC("创建时间"),
}
