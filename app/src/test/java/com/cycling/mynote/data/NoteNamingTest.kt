package com.cycling.mynote.data

import com.cycling.mynote.data.repo.NoteNaming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the naming contract the repositories rely on.
 *
 * The interesting case is the boring one: when nothing holds the requested name, `uniqueName` must
 * hand that name straight back. It reads like a no-op, which is exactly how it came to be treated as
 * a failure — a caller that rejected "the name I asked for" rejected every note whose title was not
 * already taken, so creating the first note in a repository always failed.
 */
class NoteNamingTest {

    @Test
    fun `keeps the desired name when it is free`() {
        assertEquals("未命名笔记.md", NoteNaming.uniqueName(emptySet(), "未命名笔记.md"))
        assertEquals("会议记录.md", NoteNaming.uniqueName(setOf("其他.md"), "会议记录.md"))
    }

    @Test
    fun `suffixes with 2 and up when the name is taken`() {
        val taken = setOf("未命名笔记.md", "未命名笔记 2.md")
        assertEquals("未命名笔记 3.md", NoteNaming.uniqueName(taken, "未命名笔记.md"))
        assertEquals("图片 2.png", NoteNaming.uniqueName(setOf("图片.png"), "图片.png"))
    }

    @Test
    fun `never returns a name that is already taken`() {
        val taken = (2..6).map { "笔记 $it.md" }.toSet() + "笔记.md"
        val name = NoteNaming.uniqueName(taken, "笔记.md")
        assertTrue("$name should be free", name !in taken)
    }

    @Test
    fun `makes a title safe to use as a file name`() {
        assertEquals("a-b.md", NoteNaming.toFileName("a/b"))
        assertEquals("未命名.md", NoteNaming.toFileName("   "))
        assertEquals("note.md", NoteNaming.toFileName("note.md"))
    }
}
