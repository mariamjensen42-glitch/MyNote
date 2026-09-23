package com.cycling.mynote.markdown

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
    fun `two pipes without a delimiter row are just a paragraph`() {
        val blocks = MarkdownParser.parse("| a | b |\n不是分隔行\n").blocks
        assertTrue(blocks.all { it is MarkdownBlock.Paragraph })
        assertTrue(blocks.isNotEmpty())
    }

    @Test
    fun `a table is parsed from its header and delimiter row`() {
        val source = """
            | 名称 | 数量 |
            | :--- | ---: |
            | 苹果 | 3 |
            | 梨 | 12 |
        """.trimMargin()

        val table = MarkdownParser.parse(source).blocks.single() as MarkdownBlock.Table

        assertEquals(listOf("名称", "数量"), table.header.map { cell -> cell.text() })
        assertEquals(
            listOf(MarkdownBlock.Table.Alignment.START, MarkdownBlock.Table.Alignment.END),
            table.alignments,
        )
        assertEquals(2, table.rows.size)
        assertEquals("梨", table.rows[1][0].text())
    }

    @Test
    fun `table rows are normalised to the header's width`() {
        val source = "| a | b |\n| - | - |\n| 只有一格 |\n| 一 | 二 | 三 |\n"

        val table = MarkdownParser.parse(source).blocks.single() as MarkdownBlock.Table

        // A short row is padded with an empty cell, a long one is cut down to the declared columns.
        assertEquals(listOf("只有一格", ""), table.rows[0].map { it.text() })
        assertEquals(listOf("一", "二"), table.rows[1].map { it.text() })
    }

    @Test
    fun `a table can be written without the outer pipes`() {
        val source = "a | b\n--- | ---\n1 | 2\n"

        val table = MarkdownParser.parse(source).blocks.single() as MarkdownBlock.Table
        assertEquals(2, table.header.size)
        assertEquals(2, table.rows.single().size)
    }

    @Test
    fun `inline emphasis inside a cell is parsed`() {
        val source = "| a |\n| - |\n| **粗** |\n"

        val table = MarkdownParser.parse(source).blocks.single() as MarkdownBlock.Table
        val cell = table.rows.single().single()

        assertEquals("粗", cell.single().text)
        assertTrue(cell.single().bold)
    }

    @Test
    fun `a table ends at the first line without a pipe`() {
        val source = "| a |\n| - |\n| 1 |\n\n之后的段落\n"

        val blocks = MarkdownParser.parse(source).blocks
        assertEquals(1, (blocks[0] as MarkdownBlock.Table).rows.size)
        assertTrue(blocks[1] is MarkdownBlock.Paragraph)
    }

    @Test
    fun `a paragraph of pictures is recognised as pictures`() {
        // Two images on consecutive lines are one paragraph, and the newline between them must not
        // make it look like prose.
        val blocks = MarkdownParser.parse("![a](a.png)\n![b](b.png)\n").blocks
        val paragraph = blocks.single() as MarkdownBlock.Paragraph

        assertTrue(paragraph.spans.isImageOnly())
        assertEquals(listOf("a.png" to "a", "b.png" to "b"), paragraph.spans.images())
    }

    @Test
    fun `a picture with text beside it is not a picture block`() {
        val blocks = MarkdownParser.parse("看这个 ![a](a.png)\n").blocks
        val paragraph = blocks.single() as MarkdownBlock.Paragraph

        assertTrue(!paragraph.spans.isImageOnly())
    }

    private fun List<InlineSpan>.text(): String = joinToString("") { it.text }
}
