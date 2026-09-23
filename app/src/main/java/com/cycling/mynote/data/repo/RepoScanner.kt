package com.cycling.mynote.data.repo

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.cycling.mynote.core.model.FolderNode
import com.cycling.mynote.core.model.RepoStats
import com.cycling.mynote.data.saf.DocumentEntry
import com.cycling.mynote.data.saf.DocumentTreeStore
import javax.inject.Inject
import javax.inject.Singleton

/** A note found by a scan, identified by its repository-relative path. */
@Immutable
data class ScannedNote(
    val relativePath: String,
    val documentId: String,
    val parentPath: String,
    val fileName: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
)

/** A folder found by a scan. [noteCount] counts every Markdown file beneath it, recursively. */
@Immutable
data class ScannedFolder(
    val relativePath: String,
    val name: String,
    val documentId: String,
    val parentPath: String,
    val noteCount: Int,
)

@Immutable
data class ScanResult(
    val notes: List<ScannedNote>,
    val folders: List<ScannedFolder>,
    val stats: RepoStats,
)

/**
 * Walks the granted folder and reports every Markdown file in it.
 *
 * Breadth-first with a visited-document set and a depth ceiling: a provider that reports a folder
 * as its own ancestor would otherwise recurse until the stack is gone, and the visited set costs
 * one `HashSet` insert per document for a guaranteed termination.
 */
@Singleton
class RepoScanner @Inject constructor(
    private val store: DocumentTreeStore,
) {

    fun scan(treeUri: Uri): ScanResult {
        val rootId = store.rootDocumentId(treeUri)
        val notes = mutableListOf<ScannedNote>()
        val folders = mutableListOf<ScannedFolder>()
        val visited = mutableSetOf(rootId)

        var frontier = listOf(ScanNode(documentId = rootId, path = "", depth = 0))
        var totalBytes = 0L

        while (frontier.isNotEmpty()) {
            val next = mutableListOf<ScanNode>()
            for (node in frontier) {
                if (node.depth >= MAX_DEPTH) continue
                val children = store.listChildren(treeUri, node.documentId)
                for (child in children) {
                    if (child.displayName.startsWith('.')) continue
                    val path = if (node.path.isEmpty()) {
                        child.displayName
                    } else {
                        "${node.path}/${child.displayName}"
                    }

                    when {
                        child.isDirectory -> {
                            if (!visited.add(child.documentId)) continue
                            folders += ScannedFolder(
                                relativePath = path,
                                name = child.displayName,
                                documentId = child.documentId,
                                parentPath = node.path,
                                noteCount = 0,
                            )
                            next += ScanNode(child.documentId, path, node.depth + 1)
                        }

                        child.isMarkdown -> {
                            notes += ScannedNote(
                                relativePath = path,
                                documentId = child.documentId,
                                parentPath = node.path,
                                fileName = child.displayName,
                                sizeBytes = child.sizeBytes,
                                modifiedAt = child.lastModified,
                            )
                            totalBytes += child.sizeBytes
                        }
                    }
                }
            }
            frontier = next
        }

        val counted = folders.map { it.copy(noteCount = countNotesUnder(it.relativePath, notes)) }

        return ScanResult(
            notes = notes.sortedBy { it.relativePath },
            folders = counted.sortedBy { it.relativePath },
            stats = RepoStats(
                noteCount = notes.size,
                folderCount = counted.size,
                totalBytes = totalBytes,
            ),
        )
    }

    /**
     * Builds the nested folder tree the drawer renders, rooted at the repository root.
     *
     * @param rootNoteCount the total number of notes, which is what the root node reports because
     *   notes sitting directly in the repository root belong to no folder row.
     */
    fun buildTree(folders: List<ScannedFolder>, rootNoteCount: Int): FolderNode {
        val childrenByParent = folders.groupBy { it.parentPath }

        fun build(folder: ScannedFolder): FolderNode = FolderNode(
            name = folder.name,
            path = folder.relativePath,
            noteCount = folder.noteCount,
            children = childrenByParent[folder.relativePath].orEmpty()
                .sortedBy { it.name }
                .map(::build),
        )

        return FolderNode(
            name = "",
            path = "",
            noteCount = rootNoteCount,
            children = childrenByParent[""].orEmpty().sortedBy { it.name }.map(::build),
        )
    }

    private fun countNotesUnder(path: String, notes: List<ScannedNote>): Int {
        val prefix = "$path/"
        return notes.count { it.relativePath.startsWith(prefix) }
    }

    private data class ScanNode(val documentId: String, val path: String, val depth: Int)

    private companion object {
        /** Deep enough for any realistic note archive, shallow enough to bound a pathological tree. */
        const val MAX_DEPTH = 24
    }
}

/** Filesystem-free helpers shared by the repositories that need to name documents. */
object NoteNaming {

    /** Characters SAF providers reject in display names. */
    private val ILLEGAL = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000')

    /** Makes [title] safe to use as a file name, keeping it non-empty. */
    fun toFileName(title: String, extension: String = "md"): String {
        val cleaned = buildString {
            title.forEach { char -> append(if (char in ILLEGAL) '-' else char) }
        }.trim().trim('.').take(MAX_NAME_LENGTH)
        val safe = cleaned.ifBlank { "未命名" }
        return if (safe.endsWith(".$extension", ignoreCase = true)) safe else "$safe.$extension"
    }

    /** The display title of a file: its name with the Markdown extension removed. */
    fun titleFromFileName(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0) fileName.substring(0, dot) else fileName
    }

    /** `2025-03-12.md` for a diary entry on the given date. */
    fun diaryFileName(date: java.time.LocalDate): String = "$date.md"

    /**
     * [desired] itself when it is free, otherwise the same name with ` 2`, ` 3`, … before the
     * extension. Two files with the same name cannot live in one folder, and silently overwriting
     * one note — or one picture — with another is the worst way to resolve that.
     */
    fun uniqueName(taken: Set<String>, desired: String, maxAttempts: Int = MAX_NAME_ATTEMPTS): String {
        if (desired !in taken) return desired

        val base = desired.substringBeforeLast('.')
        val extension = desired.substringAfterLast('.', "md")
        for (suffix in 2..maxAttempts) {
            val candidate = "$base $suffix.$extension"
            if (candidate !in taken) return candidate
        }
        return "$base ${System.currentTimeMillis()}.$extension"
    }

    private const val MAX_NAME_ATTEMPTS = 50

    private const val MAX_NAME_LENGTH = 120
}
