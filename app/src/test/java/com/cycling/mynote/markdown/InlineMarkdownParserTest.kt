package com.cycling.mynote.markdown

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
            if (span.imageUrl != null) append("img:${span.imageUrl}")
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
    fun `images carry their alt text and source url`() {
        assertEquals(listOf("img:p.png" to "图"), runs("![图](p.png)"))
    }

    @Test
    fun `a bare url becomes a link`() {
        assertEquals(listOf("link:https://e.com" to "https://e.com"), runs("https://e.com"))
    }

    @Test
    fun `a bare url stops before trailing punctuation`() {
        assertEquals(
            listOf("" to "见 ", "link:https://e.com" to "https://e.com", "" to "。"),
            runs("见 https://e.com。"),
        )
    }

    @Test
    fun `a www address is linked with a scheme`() {
        assertEquals(listOf("link:https://www.e.com" to "www.e.com"), runs("www.e.com"))
    }

    @Test
    fun `a url inside a link label is not linked twice`() {
        assertEquals(listOf("link:https://e.com" to "https://e.com"), runs("[https://e.com](https://e.com)"))
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
    fun `html tags stay in the text rather than being dropped`() {
        // The preview cannot render HTML, so it carries the tag through as text — which is what
        // CommonMark's own output does with it. Dropping the tag silently hid part of the note.
        assertEquals(listOf("" to "a<br/>b"), runs("a<br/>b"))
    }

    @Test
    fun `an emphasis run that cannot open stays literal`() {
        // CommonMark's flanking rules: a run followed by whitespace cannot open emphasis, so these
        // are arithmetic and prose, not malformed emphasis.
        assertEquals(listOf("" to "2 * 3 * 4 = 6"), runs("2 * 3 * 4 = 6"))
        assertEquals(listOf("" to "a * b * c"), runs("a * b * c"))
        assertEquals(listOf("" to "a * foo bar*"), runs("a * foo bar*"))
        assertEquals(listOf("" to "*foo bar *"), runs("*foo bar *"))
        assertEquals(listOf("" to "** 空格在外 **"), runs("** 空格在外 **"))
    }

    @Test
    fun `an underscore inside a word never emphasises`() {
        assertEquals(listOf("" to "foo_bar_baz"), runs("foo_bar_baz"))
        assertEquals(listOf("" to "foo__bar__baz"), runs("foo__bar__baz"))
        assertEquals(listOf("" to "x__y__z"), runs("x__y__z"))
    }

    @Test
    fun `an underscore at a word boundary still emphasises`() {
        // `__init__.py` is strong emphasis around `init` in CommonMark too: the run starts a word
        // there, which is exactly the case the in-word restriction does not cover.
        assertEquals(listOf("b" to "init", "" to ".py"), runs("__init__.py"))
        assertEquals(listOf("i" to "foo_bar_baz"), runs("_foo_bar_baz_"))
    }

    @Test
    fun `asterisks inside a word still emphasise`() {
        assertEquals(listOf("" to "3", "i" to "4", "" to "5"), runs("3*4*5"))
        assertEquals(listOf("" to "a ", "i" to "b", "" to " c"), runs("a *b* c"))
    }

    @Test
    fun `entities are decoded`() {
        assertEquals(listOf("" to "a & b"), runs("a &amp; b"))
        assertEquals(listOf("" to "3 < 4 > 2"), runs("3 &lt; 4 &gt; 2"))
        assertEquals(listOf("" to "© 2026"), runs("&copy; 2026"))
        assertEquals(listOf("" to "A"), runs("&#65;"))
        assertEquals(listOf("" to "A"), runs("&#x41;"))
    }

    @Test
    fun `an unknown entity and a bare ampersand stay literal`() {
        assertEquals(listOf("" to "&notanentity;"), runs("&notanentity;"))
        assertEquals(listOf("" to "a & b"), runs("a & b"))
    }

    @Test
    fun `an entity inside emphasis is decoded inside it`() {
        assertEquals(listOf("b" to "a & b"), runs("**a &amp; b**"))
    }

    @Test
    fun `spans report the source range of the construct they came from`() {
        val spans = InlineMarkdownParser.parse("a **bold** c")

        val bold = spans.single { it.bold }
        assertEquals("bold", bold.text)
        // The markers belong to the construct, so the range covers `**bold**`.
        assertEquals(2 to 10, bold.sourceStart to bold.sourceEnd)
    }

    @Test
    fun `source ranges shift with the offset the line is parsed at`() {
        val spans = InlineMarkdownParser.parse("**x**", baseOffset = 40)

        assertEquals(40 to 45, spans.single().sourceStart to spans.single().sourceEnd)
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
