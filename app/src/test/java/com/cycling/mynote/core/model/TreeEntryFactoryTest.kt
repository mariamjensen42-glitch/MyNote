package com.cycling.mynote.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeEntryFactoryTest {

    private val tree = FolderNode(
        name = "",
        path = "",
        noteCount = 4,
        children = listOf(
            FolderNode(
                name = "日记",
                path = "日记",
                noteCount = 2,
                children = listOf(
                    FolderNode(name = "2025-03", path = "日记/2025-03", noteCount = 1, children = emptyList()),
                ),
            ),
            FolderNode(name = "阅读", path = "阅读", noteCount = 1, children = emptyList()),
        ),
    )

    private fun note(path: String, folder: String, modifiedAt: Long = 0L) = Note(
        id = path,
        documentId = "doc-$path",
        fileName = path.substringAfterLast('/'),
        title = path,
        folder = folder,
        snippet = "",
        tags = emptyList(),
        aliases = emptyList(),
        isPinned = false,
        isFavorite = false,
        modifiedAt = modifiedAt,
        sizeBytes = 0L,
        hasFrontMatter = false,
    )

    private val notes = listOf(
        note("日记/2025-03/2025-03-12.md", "日记/2025-03", modifiedAt = 3),
        note("日记/2025-02-01.md", "日记", modifiedAt = 2),
        note("阅读/深度工作.md", "阅读", modifiedAt = 1),
        note("Inbox.md", "", modifiedAt = 4),
    )

    @Test
    fun `collapsed folders hide their notes and children`() {
        val rows = TreeEntryFactory.flatten(tree, notes, expanded = emptySet())

        assertEquals(
            listOf("日记", "阅读", "Inbox.md"),
            rows.map { if (it is TreeEntry.Folder) it.name else (it as TreeEntry.NoteFile).fileName },
        )
    }

    @Test
    fun `expanding a folder reveals its notes after its children`() {
        val rows = TreeEntryFactory.flatten(tree, notes, expanded = setOf("日记"))

        assertEquals(
            listOf("日记", "2025-03", "2025-02-01.md"),
            rows.take(3).map { if (it is TreeEntry.Folder) it.name else (it as TreeEntry.NoteFile).fileName },
        )
    }

    @Test
    fun `nested folders report their depth`() {
        val rows = TreeEntryFactory.flatten(tree, notes, expanded = setOf("日记", "日记/2025-03"))
        val nested = rows.single { it.path == "日记/2025-03" }

        assertEquals(1, nested.depth)
    }

    @Test
    fun `notes in the repository root are always visible at depth zero`() {
        val rows = TreeEntryFactory.flatten(tree, notes, expanded = emptySet())
        val inbox = rows.single { it.path == "Inbox.md" }

        assertEquals(0, inbox.depth)
        assertTrue(inbox is TreeEntry.NoteFile)
    }

    @Test
    fun `a folder is expandable when it holds only notes`() {
        val rows = TreeEntryFactory.flatten(tree, notes, expanded = emptySet())
        val reading = rows.single { it.path == "阅读" } as TreeEntry.Folder

        assertTrue(reading.hasChildren)
    }

    @Test
    fun `notes within a folder are ordered newest first`() {
        val moreNotes = notes + note("日记/2025-01-01.md", "日记", modifiedAt = 99)
        val rows = TreeEntryFactory.flatten(tree, moreNotes, expanded = setOf("日记"))

        val diaryNotes = rows.filterIsInstance<TreeEntry.NoteFile>()
            .filter { it.noteId.startsWith("日记/") }
            .map { it.noteId }

        assertEquals(listOf("日记/2025-01-01.md", "日记/2025-02-01.md"), diaryNotes)
    }

    @Test
    fun `an empty repository produces no rows`() {
        val empty = FolderNode(name = "", path = "", noteCount = 0, children = emptyList())
        assertTrue(TreeEntryFactory.flatten(empty, emptyList(), emptySet()).isEmpty())
    }
}
