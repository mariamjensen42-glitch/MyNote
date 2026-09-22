package com.cycling.mynote.data.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Renders a parse result as `markers to text` pairs.
 *
 * Asserting on the run list rather than on a rendered string keeps a failure readable: the markers
 * are spelled out and there is no separator whose own escaping has to be reasoned about.
 */
private fun runs(text: String): List<Pair<String, String>> =
    InlineMarkdownParser.parse(text).map { span ->
        buildString {
            if (span.bold) append('b')
            if (span.italic) append('i')
            if (span.code) append('c')
            if (span.strike) append('s')
            if (span.linkUrl != null) append("link:${span.linkUrl}")
        } to span.text
    }

class InlineMarkdownParserTest {

    @Test
    fun `plain text is a single run`() {
        assertEquals(listOf("" to "hello"), runs("hello"))
    }

    @Test
    fun `bold wraps only the emphasised text`() {
        assertEquals(
            listOf("" to "a ", "b" to "b", "" to " c"),
            runs("a **b** c"),
        )
    }

    @Test
    fun `italic uses the single marker`() {
        assertEquals(listOf("i" to "x"), runs("*x*"))
    }

    @Test
    fun `triple marker is bold and italic`() {
        assertEquals(listOf("bi" to "x"), runs("***x***"))
    }

    @Test
    fun `nested emphasis is additive`() {
        assertEquals(
            listOf("b" to "outer ", "bi" to "inner"),
            runs("**outer *inner***"),
        )
    }

    @Test
    fun `italic nested inside bold keeps the surrounding text plain`() {
        assertEquals(
            listOf("b" to "a ", "bi" to "b", "b" to " c"),
            runs("**a *b* c**"),
        )
    }

    @Test
    fun `underscores inside a word stay literal`() {
        assertEquals(listOf("" to "snake_case_name"), runs("snake_case_name"))
    }

    @Test
    fun `underscore emphasis at a word boundary still works`() {
        assertEquals(listOf("i" to "x"), runs("_x_"))
    }

    @Test
    fun `code spans are not parsed for emphasis`() {
        assertEquals(listOf("c" to "**not bold**"), runs("`**not bold**`"))
    }

    @Test
    fun `links carry the url and keep inline emphasis`() {
        assertEquals(listOf("link:https://e.com" to "点击"), runs("[点击](https://e.com)"))
    }

    @Test
    fun `images render as bracketed alt text with the url`() {
        assertEquals(listOf("link:p.png" to "[图]"), runs("![图](p.png)"))
    }

    @Test
    fun `strikethrough is detected`() {
        assertEquals(listOf("s" to "gone"), runs("~~gone~~"))
    }

    @Test
    fun `an unmatched marker is emitted literally`() {
        assertEquals(listOf("" to "**half"), runs("**half"))
        assertEquals(listOf("" to "`code"), runs("`code"))
    }

    @Test
    fun `backslash escapes the next character`() {
        assertEquals(listOf("" to "*literal*"), runs("\\*literal\\*"))
    }

    @Test
    fun `html tags are dropped but their content is kept`() {
        assertEquals(listOf("" to "ab"), runs("a<br/>b"))
    }

    @Test
    fun `a bare whitespace run is preserved`() {
        assertEquals(listOf("" to " "), runs(" "))
    }

    @Test
    fun `two separate bold spans do not merge`() {
        assertEquals(
            listOf("b" to "a", "" to " and ", "b" to "b"),
            runs("**a** and **b**"),
        )
    }
}
