package com.cycling.mynote.markdown

import androidx.compose.runtime.Immutable

/**
 * A stretch of inline text with the emphasis that applies to it.
 *
 * Emphasis is flattened into flags instead of a tree: a renderer only ever needs bold, italic,
 * code, strike and a link applied to a run of characters, and a flat list keeps rendering to a
 * single `AnnotatedString` pass with no recursive span bookkeeping.
 *
 * [sourceStart] and [sourceEnd] are the run's offsets *in the line it came from*, which is what lets
 * the syntax highlighter colour the source from the very same parse the preview renders. They are
 * line-relative on purpose: the scanner works line by line, and the highlighter is the only consumer
 * that needs to turn them into file offsets. A run's text is the *rendered* text, so for an escaped
 * run (`\*`) the range is longer than the text — consumers must use the range only to locate the
 * source, never to re-slice it.
 */
@Immutable
data class InlineSpan(
    val text: String,
    val sourceStart: Int = 0,
    val sourceEnd: Int = 0,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strike: Boolean = false,
    val linkUrl: String? = null,
    val imageUrl: String? = null,
)

/** One rendered block of a Markdown document. */
@Immutable
sealed interface MarkdownBlock {
    @Immutable
    data class Heading(val level: Int, val spans: List<InlineSpan>) : MarkdownBlock

    @Immutable
    data class Paragraph(val spans: List<InlineSpan>) : MarkdownBlock

    @Immutable
    data class Bullet(
        val depth: Int,
        val marker: String,
        val spans: List<InlineSpan>,
    ) : MarkdownBlock

    @Immutable
    data class Ordered(val depth: Int, val number: Int, val spans: List<InlineSpan>) : MarkdownBlock

    /**
     * @param line the 0-based line of the task in the note's raw Markdown, so the preview can write a
     *   toggled checkbox back to the exact line it came from.
     */
    @Immutable
    data class Task(
        val depth: Int,
        val checked: Boolean,
        val spans: List<InlineSpan>,
        val line: Int,
    ) : MarkdownBlock

    @Immutable
    data class Quote(val depth: Int, val spans: List<InlineSpan>) : MarkdownBlock

    @Immutable
    data class Code(val language: String?, val lines: List<String>) : MarkdownBlock

    /**
     * A GFM table.
     *
     * The header is always present — a table is only a table because of the delimiter row under it —
     * and [alignments] has one entry per header cell. Rows are padded or truncated to the header's
     * width when they are parsed, so every row a renderer sees has the same number of cells.
     */
    @Immutable
    data class Table(
        val header: List<List<InlineSpan>>,
        val alignments: List<Alignment>,
        val rows: List<List<List<InlineSpan>>>,
    ) : MarkdownBlock {
        enum class Alignment { START, CENTER, END, NONE }
    }

    @Immutable
    data object ThematicBreak : MarkdownBlock
}

/**
 * True when every run that carries text is a picture, i.e. the block is a picture rather than prose.
 *
 * Whitespace runs are ignored on purpose: two images on consecutive lines are one paragraph whose
 * runs are separated by a newline, and that separator must not make the paragraph look like prose.
 */
fun List<InlineSpan>.isImageOnly(): Boolean =
    isNotEmpty() && none { it.imageUrl == null && it.text.isNotBlank() }

/** The pictures a run list refers to, as (reference, alt text) pairs. */
fun List<InlineSpan>.images(): List<Pair<String, String>> =
    mapNotNull { span -> span.imageUrl?.let { it to span.text } }

/** A parsed Markdown document: front matter stripped, only the body represented. */
@Immutable
data class MarkdownDocument(val blocks: List<MarkdownBlock>)
