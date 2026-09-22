package com.cycling.mynote.data.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTextExtractorTest {

    @Test
    fun `title prefers front matter over the first heading`() {
        val source = "---\ntitle: 显式标题\n---\n# 正文标题\n"
        val text = NoteTextExtractor.extract(source, "file.md")

        assertEquals("显式标题", text.title)
    }

    @Test
    fun `title falls back to the first heading`() {
        val text = NoteTextExtractor.extract("## 二级标题\n\n正文\n", "file.md")
        assertEquals("二级标题", text.title)
    }

    @Test
    fun `title falls back to the Setext heading`() {
        val text = NoteTextExtractor.extract("取消机制\n===\n\n正文\n", "file.md")
        assertEquals("取消机制", text.title)
    }

    @Test
    fun `title falls back to the file name without its extension`() {
        val text = NoteTextExtractor.extract("没有任何标题\n", "2025-03-12.md")
        assertEquals("2025-03-12", text.title)
    }

    @Test
    fun `snippet skips the heading and strips inline markup`() {
        val source = "# 标题\n\n这是 **加粗** 和 `代码` 的正文。\n"
        val text = NoteTextExtractor.extract(source, "file.md")

        assertEquals("这是 加粗 和 代码 的正文。", text.snippet)
    }

    @Test
    fun `snippet skips a fenced code block`() {
        val source = """
            |# 标题
            |
            |```
            |code();
            |```
            |
            |真正的正文
        """.trimMargin()

        assertEquals("真正的正文", NoteTextExtractor.extract(source, "file.md").snippet)
    }

    @Test
    fun `snippet unwraps link and image syntax`() {
        val source = "# 标题\n\n[链接文字](https://example.com) 之后\n"
        assertEquals("链接文字 之后", NoteTextExtractor.extract(source, "file.md").snippet)
    }

    @Test
    fun `snippet drops a task marker`() {
        val source = "# 标题\n\n- [ ] 要做的事\n"
        assertEquals("要做的事", NoteTextExtractor.extract(source, "file.md").snippet)
    }

    @Test
    fun `snippet is empty for a note with no prose`() {
        assertEquals("", NoteTextExtractor.extract("# 只有标题\n", "file.md").snippet)
        assertEquals("", NoteTextExtractor.extract("", "file.md").snippet)
    }

    @Test
    fun `snippet is truncated to a readable length`() {
        val source = "# 标题\n\n" + "字".repeat(200) + "\n"
        val snippet = NoteTextExtractor.extract(source, "file.md").snippet

        assertTrue(snippet.length <= 60)
    }

    @Test
    fun `stats count lines and non-whitespace characters`() {
        val source = "# 标题\n\n正文\n"
        val stats = NoteTextExtractor.extract(source, "file.md").stats

        assertEquals(4, stats.lineCount)
        assertEquals(5, stats.characterCount)
    }

    @Test
    fun `stats of an empty document are zero`() {
        val stats = NoteTextExtractor.extract("", "file.md").stats
        assertEquals(0, stats.lineCount)
        assertEquals(0, stats.characterCount)
    }
}
