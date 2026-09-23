package com.cycling.mynote.markdown

import com.cycling.mynote.core.model.FrontMatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrontMatterParserTest {

    @Test
    fun `parses a flow-style tag list`() {
        // trimMargin drops the leading and trailing blank lines, so the newline is added back
        // explicitly: these assertions are about where the body starts, which needs a real body.
        val source = """
            |---
            |tags: [技术, Kotlin]
            |pinned: true
            |---
            |# 标题
        """.trimMargin() + "\n"

        val result = FrontMatterParser.parse(source)

        assertEquals(listOf("技术", "Kotlin"), result.frontMatter.tags)
        assertTrue(result.frontMatter.isPinned)
        assertEquals("# 标题\n", result.bodyOf(source))
    }

    @Test
    fun `parses a block-style tag list`() {
        val source = """
            |---
            |tags:
            |  - 读书
            |  - 摘录
            |aliases: [coroutine-cancellation]
            |---
            |body
        """.trimMargin() + "\n"

        val result = FrontMatterParser.parse(source)

        assertEquals(listOf("读书", "摘录"), result.frontMatter.tags)
        assertEquals(listOf("coroutine-cancellation"), result.frontMatter.aliases)
        assertEquals("body\n", result.bodyOf(source))
    }

    @Test
    fun `keeps unknown keys so a save cannot drop a user's metadata`() {
        val source = """
            |---
            |tags: [a]
            |rating: 4
            |source: "https://example.com/x"
            |---
            |body
        """.trimMargin()

        val parsed = FrontMatterParser.parse(source)
        assertEquals(
            listOf("rating: 4", "source: \"https://example.com/x\""),
            parsed.frontMatter.unknownLines,
        )

        // Exactly what the editor writes back when it toggles the pin.
        val rewritten = FrontMatterParser.render(parsed.frontMatter) + parsed.bodyOf(source)
        assertTrue(rewritten.contains("rating: 4"))
        assertTrue(rewritten.contains("source: \"https://example.com/x\""))
        assertTrue(rewritten.contains("tags: [a]"))
    }

    @Test
    fun `document without front matter is body-only`() {
        val source = "# 标题\n\n正文\n"
        val result = FrontMatterParser.parse(source)

        assertEquals(FrontMatter.EMPTY, result.frontMatter)
        assertEquals(0, result.bodyStartOffset)
        assertEquals(source, result.bodyOf(source))
    }

    @Test
    fun `an unterminated block is not treated as front matter`() {
        val source = "---\ntags: [a]\n\nbody\n"
        val result = FrontMatterParser.parse(source)

        assertEquals(FrontMatter.EMPTY, result.frontMatter)
        assertEquals(0, result.bodyStartOffset)
    }

    @Test
    fun `render omits the block when there is nothing to write`() {
        assertEquals("", FrontMatterParser.render(FrontMatter()))
        assertFalse(FrontMatterParser.render(FrontMatter(tags = listOf("a"))).isEmpty())
    }

    @Test
    fun `render quotes values that would otherwise change meaning`() {
        assertTrue(FrontMatterParser.render(FrontMatter(title = "true")).contains("title: \"true\""))
        assertTrue(FrontMatterParser.render(FrontMatter(title = "普通标题")).contains("title: 普通标题"))
        // A colon inside a title would otherwise read as a nested key.
        assertTrue(FrontMatterParser.render(FrontMatter(title = "a: b")).contains("title: \"a: b\""))
    }

    @Test
    fun `render round-trips through the parser`() {
        val original = FrontMatter(
            title = "取消机制",
            tags = listOf("技术", "Kotlin"),
            aliases = listOf("cancellation"),
            isPinned = true,
            isFavorite = false,
            created = "2025-03-12",
            unknownLines = listOf("rating: 4"),
        )

        val reparsed = FrontMatterParser
            .parse(FrontMatterParser.render(original) + "body\n")
            .frontMatter

        assertEquals(original, reparsed)
    }

    @Test
    fun `a closing dots delimiter is accepted`() {
        val result = FrontMatterParser.parse("---\ntags: [a]\n...\nbody\n")
        assertEquals(listOf("a"), result.frontMatter.tags)
    }

    @Test
    fun `accepts singular and alternative key spellings`() {
        val result = FrontMatterParser.parse(
            """
            |---
            |keywords: [k1, k2]
            |starred: yes
            |pin: true
            |---
            |body
            """.trimMargin(),
        )

        assertEquals(listOf("k1", "k2"), result.frontMatter.tags)
        assertTrue(result.frontMatter.isFavorite)
        assertTrue(result.frontMatter.isPinned)
    }

    @Test
    fun `de-duplicates tags while preserving order`() {
        val result = FrontMatterParser.parse("---\ntags: [b, a, b]\n---\nbody\n")
        assertEquals(listOf("b", "a"), result.frontMatter.tags)
    }

    @Test
    fun `handles crlf line endings`() {
        val source = "---\r\ntags: [a]\r\n---\r\nbody\r\n"
        val result = FrontMatterParser.parse(source)

        assertEquals(listOf("a"), result.frontMatter.tags)
        assertEquals("body\r\n", result.bodyOf(source))
    }

    /** The slice of [source] the parser reports as the body. */
    private fun FrontMatterParser.ParseResult.bodyOf(source: String): String =
        source.substring(bodyStartOffset)
}
