package com.cycling.mynote.data.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `headings carry their level`() {
        val blocks = MarkdownParser.parse("# 一\n## 二\n### 三\n").blocks

        assertEquals(
            listOf(
                MarkdownBlock.Heading::class,
                MarkdownBlock.Heading::class,
                MarkdownBlock.Heading::class,
            ),
            blocks.map { it::class },
        )
        assertEquals(listOf(1, 2, 3), blocks.map { (it as MarkdownBlock.Heading).level })
    }

    @Test
    fun `setext heading is level one for equals and two for dashes`() {
        val blocks = MarkdownParser.parse("标题\n===\n\n副标题\n---\n").blocks
        assertEquals(
            listOf(1, 2),
            blocks.filterIsInstance<MarkdownBlock.Heading>().map { it.level },
        )
    }

    @Test
    fun `consecutive lines join into one paragraph`() {
        val blocks = MarkdownParser.parse("第一行\n第二行\n\n新段落\n").blocks
        val paragraphs = blocks.filterIsInstance<MarkdownBlock.Paragraph>()

        assertEquals(2, paragraphs.size)
        assertEquals("第一行\n第二行", paragraphs[0].spans.joinToString("") { it.text })
    }

    @Test
    fun `task list items report their checked state`() {
        val blocks = MarkdownParser.parse("- [x] 完成\n- [ ] 未完成\n").blocks
        val tasks = blocks.filterIsInstance<MarkdownBlock.Task>()

        assertEquals(listOf(true, false), tasks.map { it.checked })
        assertEquals("完成", tasks[0].spans.joinToString("") { it.text })
    }

    @Test
    fun `bullet nesting follows the indent depth`() {
        val blocks = MarkdownParser.parse("- 外层\n  - 内层\n").blocks
        val bullets = blocks.filterIsInstance<MarkdownBlock.Bullet>()

        assertEquals(listOf(0, 1), bullets.map { it.depth })
    }

    @Test
    fun `ordered items keep their number`() {
        val blocks = MarkdownParser.parse("1. 一\n2. 二\n").blocks
        assertEquals(listOf(1, 2), blocks.filterIsInstance<MarkdownBlock.Ordered>().map { it.number })
    }

    @Test
    fun `a fenced block captures its language and body verbatim`() {
        val blocks = MarkdownParser.parse("```kotlin\nval x = 1\n# not a heading\n```\n").blocks
        val code = blocks.single() as MarkdownBlock.Code

        assertEquals("kotlin", code.language)
        assertEquals(listOf("val x = 1", "# not a heading"), code.lines)
    }

    @Test
    fun `an unclosed fence runs to the end of the document`() {
        val blocks = MarkdownParser.parse("```\nline\n").blocks
        val code = blocks.single() as MarkdownBlock.Code

        assertEquals(listOf("line"), code.lines)
    }

    @Test
    fun `a heading inside a fence is not a heading`() {
        val blocks = MarkdownParser.parse("```\n# inside\n```\n").blocks
        assertTrue(blocks.single() is MarkdownBlock.Code)
    }

    @Test
    fun `quotes are separate blocks`() {
        val blocks = MarkdownParser.parse("> 引用\n").blocks
        val quote = blocks.single() as MarkdownBlock.Quote
        assertEquals("引用", quote.spans.joinToString("") { it.text })
    }

    @Test
    fun `thematic breaks are recognised`() {
        val blocks = MarkdownParser.parse("---\n***\n___\n").blocks
        assertEquals(3, blocks.count { it is MarkdownBlock.ThematicBreak })
    }

    @Test
    fun `front matter is stripped before parsing`() {
        val blocks = MarkdownParser.parse("---\ntitle: x\n---\n# 标题\n").blocks
        assertEquals(1, blocks.size)
        assertTrue(blocks.single() is MarkdownBlock.Heading)
    }

    @Test
    fun `an empty document has no blocks`() {
        assertTrue(MarkdownParser.parse("").blocks.isEmpty())
        assertTrue(MarkdownParser.parse("\n\n\n").blocks.isEmpty())
    }

    @Test
    fun `plain text drops the markup but keeps the words`() {
        val source = "# 标题\n\n**粗体** 与 [链接](https://e.com)\n"
        assertEquals("标题\n粗体 与 链接", MarkdownParser.toPlainText(MarkdownParser.parse(source)))
    }

    @Test
    fun `an unrecognised construct still renders its text`() {
        // A table is not supported; it must degrade to a paragraph rather than vanish.
        val blocks = MarkdownParser.parse("| a | b |\n| - | - |\n").blocks
        assertTrue(blocks.all { it is MarkdownBlock.Paragraph })
        assertTrue(blocks.isNotEmpty())
    }
}
