package com.cycling.mynote.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownCommandsTest {

    private fun apply(id: String, text: String, start: Int, end: Int): MarkdownEdit {
        val command = MarkdownCommands.ALL.single { it.id == id }
        return MarkdownCommands.apply(command, text, start, end)
    }

    @Test
    fun `the toolbar holds the design's nine actions in order`() {
        assertEquals(
            listOf("heading", "bold", "italic", "list", "task", "quote", "code", "link", "image"),
            MarkdownCommands.ALL.map { it.id },
        )
    }

    @Test
    fun `bold wraps the selection and leaves the caret after it`() {
        val edit = apply("bold", "hello world", 0, 5)

        assertEquals("**hello** world", edit.text)
        assertEquals(9, edit.selectionStart)
    }

    @Test
    fun `bold with nothing selected puts the caret between the markers`() {
        val edit = apply("bold", "ab", 1, 1)

        assertEquals("a****b", edit.text)
        assertEquals(3, edit.selectionStart)
    }

    @Test
    fun `a heading marker toggles off when the line already has one`() {
        val added = apply("heading", "标题", 0, 1)
        assertEquals("# 标题", added.text)

        val removed = apply("heading", added.text, 0, 1)
        assertEquals("标题", removed.text)
    }

    @Test
    fun `a line marker applies to every selected line`() {
        val edit = apply("list", "一\n二", 0, 3)

        assertEquals("- 一\n- 二", edit.text)
    }

    @Test
    fun `a line marker leaves blank lines alone`() {
        val edit = apply("quote", "一\n\n二", 0, 5)

        assertEquals("> 一\n\n> 二", edit.text)
    }

    @Test
    fun `the task action turns plain lines into finished tasks`() {
        val edit = apply("task", "买牛奶", 0, 3)

        assertEquals("- [x] 买牛奶", edit.text)
    }

    @Test
    fun `the task action turns an existing bullet into a task`() {
        val edit = apply("task", "- 买牛奶", 0, 3)

        assertEquals("- [x] 买牛奶", edit.text)
    }

    @Test
    fun `the task action prefixes a line that is not a bullet`() {
        // An ordered item keeps its marker as the task's text: a checkbox inside `1. ` is not
        // something the parser reads back as a task, so prefixing is what survives a round trip.
        val edit = apply("task", "1. 买牛奶", 0, 3)

        assertEquals("- [x] 1. 买牛奶", edit.text)
    }

    @Test
    fun `the task action clears finished tasks`() {
        val edit = apply("task", "- [x] 买牛奶", 2, 7)

        assertEquals("- [ ] 买牛奶", edit.text)
    }
}
