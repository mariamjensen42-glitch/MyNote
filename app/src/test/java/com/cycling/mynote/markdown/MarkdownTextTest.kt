package com.cycling.mynote.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextTest {

    @Test
    fun `title prefers front matter over the first heading`() {
        val source = "---\ntitle: 显式标题\n---\n# 正文标题\n"
        val text = MarkdownText.extract(source, "file.md")

        assertEquals("显式标题", text.title)
    }

    @Test
    fun `title falls back to the first heading`() {
        val text = MarkdownText.extract("## 二级标题\n\n正文\n", "file.md")
        assertEquals("二级标题", text.title)
    }

    @Test
    fun `title falls back to the Setext heading`() {
        val text = MarkdownText.extract("取消机制\n===\n\n正文\n", "file.md")
        assertEquals("取消机制", text.title)
    }

    @Test
    fun `title falls back to the file name without its extension`() {
        val text = MarkdownText.extract("没有任何标题\n", "2025-03-12.md")
        assertEquals("2025-03-12", text.title)
    }

    @Test
    fun `snippet skips the heading and strips inline markup`() {
        val source = "# 标题\n\n这是 **加粗** 和 `代码` 的正文。\n"
        val text = MarkdownText.extract(source, "file.md")

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

        assertEquals("真正的正文", MarkdownText.extract(source, "file.md").snippet)
    }

    @Test
    fun `snippet unwraps link and image syntax`() {
        val source = "# 标题\n\n[链接文字](https://example.com) 之后\n"
        assertEquals("链接文字 之后", MarkdownText.extract(source, "file.md").snippet)
    }

    @Test
    fun `snippet drops a task marker`() {
        val source = "# 标题\n\n- [ ] 要做的事\n"
        assertEquals("要做的事", MarkdownText.extract(source, "file.md").snippet)
    }

    @Test
    fun `snippet is empty for a note with no prose`() {
        assertEquals("", MarkdownText.extract("# 只有标题\n", "file.md").snippet)
        assertEquals("", MarkdownText.extract("", "file.md").snippet)
    }

    @Test
    fun `snippet is truncated to a readable length`() {
        val source = "# 标题\n\n" + "字".repeat(200) + "\n"
        val snippet = MarkdownText.extract(source, "file.md").snippet

        assertTrue(snippet.length <= 60)
    }

    @Test
    fun `stats count lines and non-whitespace characters`() {
        val source = "# 标题\n\n正文\n"
        val stats = MarkdownText.extract(source, "file.md").stats

        assertEquals(4, stats.lineCount)
        assertEquals(5, stats.characterCount)
    }

    @Test
    fun `stats of an empty document are zero`() {
        val stats = MarkdownText.extract("", "file.md").stats
        assertEquals(0, stats.lineCount)
        assertEquals(0, stats.characterCount)
    }

    @Test
    fun `the search body is the prose without its markers`() {
        val source = """
            |---
            |tags: [a]
            |---
            |
            |# 标题
            |
            |- [x] 做完了
            |- 未完
            |  - 嵌套
            |
            |**粗体** 与 [链接](https://e.com)
        """.trimMargin()

        assertEquals(
            listOf("标题", "做完了", "未完", "嵌套", "粗体 与 链接"),
            MarkdownText.searchBody(source).lines(),
        )
    }

    @Test
    fun `the search body keeps the text inside a fence but drops the fence`() {
        assertEquals("val x = 1", MarkdownText.searchBody("```kotlin\nval x = 1\n```\n"))
    }

    @Test
    fun `the search body drops rules but keeps an image's alternative text`() {
        // A rule is not text; the alt text of an image is the only words it has, so it is searched.
        assertEquals("图", MarkdownText.searchBody("---\n\n![图](p.png)\n"))
    }
}
