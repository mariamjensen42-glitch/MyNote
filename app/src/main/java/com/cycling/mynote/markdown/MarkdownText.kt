package com.cycling.mynote.markdown

import com.cycling.mynote.core.model.NoteStats

/** Plain-text projections of a Markdown document. */
data class NoteText(
    val title: String,
    val snippet: String,
    val stats: NoteStats,
)

/**
 * The plain-text side of a note: its title, the list snippet, the counts, and the prose that the
 * search index stores.
 *
 * Everything here is derived from [MarkdownParser] and [InlineMarkdownParser] rather than from
 * regexes of its own. That is deliberate: the app used to strip inline markup with a second,
 * pattern-based inline parser that disagreed with the real one about code spans and escapes, so a
 * note could be indexed under text it never rendered. One parser, one projection.
 */
object MarkdownText {

    /** How much of the first prose line the list snippet keeps. */
    private const val SNIPPET_MAX_LENGTH = 60

    /**
     * The title falls back the way a Markdown reader would expect: explicit front-matter `title`,
     * then the first heading, then the file name. The snippet is the first line of prose, which is
     * what the design shows under each note title.
     */
    fun extract(
        markdown: String,
        fileName: String,
        frontMatter: FrontMatterParser.ParseResult = FrontMatterParser.parse(markdown),
    ): NoteText {
        val document = MarkdownParser.parse(markdown)
        val title = frontMatter.frontMatter.title
            ?: document.blocks.filterIsInstance<MarkdownBlock.Heading>().firstOrNull()?.let(::textOf)
            ?: fileName.removeSuffix(".md").removeSuffix(".markdown").ifBlank { fileName }

        return NoteText(
            title = title,
            snippet = snippetOf(document),
            stats = NoteStats(
                lineCount = markdown.count { it == '\n' } + if (markdown.isEmpty()) 0 else 1,
                characterCount = markdown.count { !it.isWhitespace() },
            ),
        )
    }

    /**
     * The note's prose, one line per line, with no Markdown left in it.
     *
     * This is what the search index stores and matches against, so it holds what the note *reads* as
     * — markers, fences and image syntax are gone, and the text inside a code block is kept because
     * the author wrote it as content.
     */
    fun searchBody(markdown: String): String = MarkdownParser.parse(markdown).blocks
        .flatMap { block ->
            when (block) {
                is MarkdownBlock.Code -> block.lines
                MarkdownBlock.ThematicBreak -> emptyList()
                else -> listOf(textOf(block))
            }
        }
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString("\n")

    private fun snippetOf(document: MarkdownDocument): String {
        for (block in document.blocks) {
            // Headings are the note's title, code is not prose, and a rule is not text at all.
            if (block is MarkdownBlock.Heading ||
                block is MarkdownBlock.Code ||
                block === MarkdownBlock.ThematicBreak
            ) {
                continue
            }
            // A line that is only a picture renders as a picture, not as prose to quote.
            if (block is MarkdownBlock.Paragraph && block.spans.isImageOnly()) continue

            val line = textOf(block).substringBefore('\n').trim()
            if (line.isNotEmpty()) return line.take(SNIPPET_MAX_LENGTH)
        }
        return ""
    }

    private fun textOf(block: MarkdownBlock): String = when (block) {
        is MarkdownBlock.Heading -> textOf(block.spans)
        is MarkdownBlock.Paragraph -> textOf(block.spans)
        is MarkdownBlock.Bullet -> textOf(block.spans)
        is MarkdownBlock.Ordered -> textOf(block.spans)
        is MarkdownBlock.Task -> textOf(block.spans)
        is MarkdownBlock.Quote -> textOf(block.spans)
        is MarkdownBlock.Code -> block.lines.joinToString("\n")
        is MarkdownBlock.Table -> (listOf(block.header) + block.rows).joinToString("\n") { row ->
            row.joinToString(" ") { cell -> textOf(cell) }
        }
        MarkdownBlock.ThematicBreak -> ""
    }

    private fun textOf(spans: List<InlineSpan>): String = spans.joinToString("") { it.text }
}
