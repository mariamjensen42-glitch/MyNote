package com.cycling.mynote.core.model

import androidx.compose.runtime.Immutable

/**
 * A folder in the repository tree.
 *
 * [path] is repository-relative and `""` denotes the repository root, which the drawer never shows
 * as a row — its children are the top level.
 */
@Immutable
data class FolderNode(
    val name: String,
    val path: String,
    val noteCount: Int,
    val children: List<FolderNode>,
) {
    val isRoot: Boolean get() = path.isEmpty()
}

/**
 * One row of the folder-tree drawer, which interleaves folders and the note files inside them.
 *
 * Produced by flattening [FolderNode] with the set of expanded folders applied, so the drawer can
 * render a lazy list while the expansion state stays in the view model.
 */
@Immutable
sealed interface TreeEntry {
    val path: String
    val depth: Int

    @Immutable
    data class Folder(
        override val path: String,
        override val depth: Int,
        val name: String,
        val noteCount: Int,
        val isExpanded: Boolean,
        val hasChildren: Boolean,
    ) : TreeEntry

    @Immutable
    data class NoteFile(
        override val path: String,
        override val depth: Int,
        val noteId: String,
        val fileName: String,
        val modifiedAt: Long,
    ) : TreeEntry
}

/** A row of the long-press action strip under a note in the folder-tree design. */
enum class NoteAction(val label: String) {
    PIN("置顶"),
    RENAME("重命名"),
    MOVE("移动"),
    DELETE("删除"),
}

/**
 * Flattens the folder tree into drawer rows.
 *
 * A pure function rather than a repository concern: the drawer needs folders and note files
 * interleaved, which is a presentation order, and keeping it here means the expansion state can
 * live in the view model's UI state and be applied on every recomposition for free.
 *
 * Folders come before the notes they contain, and notes appear under a folder only once that
 * folder is in [expanded]. Notes sitting in the repository root are listed last, at depth 0, so a
 * stray file in the root is always reachable without expanding anything.
 */
object TreeEntryFactory {

    fun flatten(
        root: FolderNode,
        notes: List<Note>,
        expanded: Set<String>,
    ): List<TreeEntry> {
        val notesByFolder = notes.groupBy { it.folder }
        val rows = mutableListOf<TreeEntry>()

        fun noteRows(folderPath: String, depth: Int) {
            notesByFolder[folderPath].orEmpty()
                .sortedByDescending { it.modifiedAt }
                .forEach { note ->
                    rows += TreeEntry.NoteFile(
                        path = note.id,
                        depth = depth,
                        noteId = note.id,
                        fileName = note.fileName,
                        modifiedAt = note.modifiedAt,
                    )
                }
        }

        fun visit(folder: FolderNode, depth: Int) {
            val isExpanded = folder.path in expanded
            rows += TreeEntry.Folder(
                path = folder.path,
                depth = depth,
                name = folder.name,
                noteCount = folder.noteCount,
                isExpanded = isExpanded,
                hasChildren = folder.children.isNotEmpty() || notesByFolder.containsKey(folder.path),
            )
            if (!isExpanded) return
            folder.children.sortedBy { it.name }.forEach { visit(it, depth + 1) }
            noteRows(folder.path, depth + 1)
        }

        root.children.sortedBy { it.name }.forEach { visit(it, 0) }
        noteRows("", 0)
        return rows
    }
}
