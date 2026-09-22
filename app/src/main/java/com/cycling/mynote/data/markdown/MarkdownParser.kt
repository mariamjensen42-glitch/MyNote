package com.cycling.mynote.data.markdown

import androidx.compose.runtime.Immutable

/**
 * A stretch of inline text with the emphasis that applies to it.
 *
 * Emphasis is flattened into flags instead of a tree: the preview only ever needs bold, italic,
 * code, strike and a link applied to a run of characters, and a flat list keeps rendering to a
 * single `AnnotatedString` pass with no recursive span bookkeeping.
 */
@Immutable
data class InlineSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strike: Boolean = false,
    val linkUrl: String? = null,
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

    @Immutable
    data class Task(val depth: Int, val checked: Boolean, val spans: List<InlineSpan>) : MarkdownBlock

    @Immutable
    data class Quote(val depth: Int, val spans: List<InlineSpan>) : MarkdownBlock

    @Immutable
    data class Code(val language: String?, val lines: List<String>) : MarkdownBlock

    @Immutable
    data object ThematicBreak : MarkdownBlock
}

/** A parsed Markdown document: front matter stripped, only the body represented. */
@Immutable
data class MarkdownDocument(val blocks: List<MarkdownBlock>)

/**
 * A small Markdown block parser covering what the editor previews: headings, paragraphs, nested
 * bullets, ordered and task lists, quotes, fenced code and thematic breaks.
 *
 * Intentionally not a CommonMark implementation. Anything unrecognised degrades to a paragraph,
 * which renders the author's text rather than dropping it — the same choice the front-matter parser
 * makes. Block nesting is tracked by indent depth only, which is enough for the preview's left
 * padding and avoids maintaining a block stack.
 */
object MarkdownParser {

    private const val INDENT_UNIT = 2
    private const val FENCE_MARKER = "```"

    private val ATX_HEADING = Regex("""^(#{1,6})\s+(.*?)\s*#*\s*$""")
    private val SETEXT_UNDERLINE = Regex("""^(=+|-{2,})\s*$""")
    private val THEMATIC_BREAK = Regex("""^\s{0,3}([-*_])(\s*\1){2,}\s*$""")
    private val BULLET = Regex("""^(\s*)[-*+]\s+(.*)$""")
    private val ORDERED = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""")
    private val TASK = Regex("""^\[([ xX])]\s*(.*)$""")
    private val QUOTE = Regex("""^(\s*)>\s?(.*)$""")

    fun parse(markdown: String): MarkdownDocument {
        val frontMatter = FrontMatterParser.parse(markdown)
        val body = markdown.substring(frontMatter.bodyStartOffset.coerceIn(0, markdown.length))
        return MarkdownDocument(parseBlocks(body))
    }

    /** Renders a document back to text; used by tests and by the "copy source" action. */
    fun toPlainText(document: MarkdownDocument): String = document.blocks.joinToString("\n") { block ->
        when (block) {
            is MarkdownBlock.Heading -> block.spans.joinToString("") { it.text }
            is MarkdownBlock.Paragraph -> block.spans.joinToString("") { it.text }
            is MarkdownBlock.Bullet -> block.spans.joinToString("") { it.text }
            is MarkdownBlock.Ordered -> block.spans.joinToString("") { it.text }
            is MarkdownBlock.Task -> block.spans.joinToString("") { it.text }
            is MarkdownBlock.Quote -> block.spans.joinToString("") { it.text }
            is MarkdownBlock.Code -> block.lines.joinToString("\n")
            MarkdownBlock.ThematicBreak -> "---"
        }
    }

    private fun parseBlocks(body: String): List<MarkdownBlock> {
        val lines = body.split('\n')
        val blocks = mutableListOf<MarkdownBlock>()
        val paragraph = mutableListOf<String>()
        var index = 0

        fun flushParagraph() {
            if (paragraph.isEmpty()) return
            val text = paragraph.joinToString("\n").trim()
            if (text.isNotEmpty()) {
                blocks += MarkdownBlock.Paragraph(InlineMarkdownParser.parse(text))
            }
            paragraph.clear()
        }

        while (index < lines.size) {
            val line = lines[index]
            val leading = line.trimStart()

            if (leading.startsWith(FENCE_MARKER)) {
                flushParagraph()
                val language = leading.removePrefix(FENCE_MARKER).trim().ifBlank { null }
                val code = mutableListOf<String>()
                index++
                while (index < lines.size && !lines[index].trimStart().startsWith(FENCE_MARKER)) {
                    code += lines[index]
                    index++
                }
                val closed = index < lines.size
                if (closed) index++
                // An unterminated fence runs to the end of the document, where splitting on '\n'
                // leaves an empty final element that is an artefact of the last newline rather than
                // a blank line the author wrote.
                if (!closed && code.lastOrNull()?.isEmpty() == true) {
                    code.removeAt(code.size - 1)
                }
                blocks += MarkdownBlock.Code(language, code)
                continue
            }

            if (line.isBlank()) {
                flushParagraph()
                index++
                continue
            }

            if (THEMATIC_BREAK.matches(line)) {
                flushParagraph()
                blocks += MarkdownBlock.ThematicBreak
                index++
                continue
            }

            val atx = ATX_HEADING.matchEntire(line)
            if (atx != null) {
                flushParagraph()
                blocks += MarkdownBlock.Heading(
                    level = atx.groupValues[1].length,
                    spans = InlineMarkdownParser.parse(atx.groupValues[2]),
                )
                index++
                continue
            }

            val underline = lines.getOrNull(index + 1)
            if (underline != null && line.isNotBlank() && SETEXT_UNDERLINE.matches(underline)) {
                flushParagraph()
                blocks += MarkdownBlock.Heading(
                    level = if (underline.trim().startsWith("=")) 1 else 2,
                    spans = InlineMarkdownParser.parse(line.trim()),
                )
                index += 2
                continue
            }

            val quote = QUOTE.matchEntire(line)
            if (quote != null) {
                flushParagraph()
                blocks += MarkdownBlock.Quote(
                    depth = quote.groupValues[1].length / INDENT_UNIT,
                    spans = InlineMarkdownParser.parse(quote.groupValues[2]),
                )
                index++
                continue
            }

            val bullet = BULLET.matchEntire(line)
            if (bullet != null) {
                flushParagraph()
                val depth = bullet.groupValues[1].length / INDENT_UNIT
                val content = bullet.groupValues[2]
                val task = TASK.matchEntire(content)
                blocks += if (task != null) {
                    MarkdownBlock.Task(
                        depth = depth,
                        checked = task.groupValues[1] != " ",
                        spans = InlineMarkdownParser.parse(task.groupValues[2]),
                    )
                } else {
                    MarkdownBlock.Bullet(
                        depth = depth,
                        marker = "•",
                        spans = InlineMarkdownParser.parse(content),
                    )
                }
                index++
                continue
            }

            val ordered = ORDERED.matchEntire(line)
            if (ordered != null) {
                flushParagraph()
                blocks += MarkdownBlock.Ordered(
                    depth = ordered.groupValues[1].length / INDENT_UNIT,
                    number = ordered.groupValues[2].toIntOrNull() ?: 1,
                    spans = InlineMarkdownParser.parse(ordered.groupValues[3]),
                )
                index++
                continue
            }

            paragraph += line
            index++
        }

        flushParagraph()
        return blocks
    }
}
