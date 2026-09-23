package com.cycling.mynote.markdown

/**
 * A small Markdown block parser covering what the editor previews: headings, paragraphs, nested
 * bullets, ordered and task lists, quotes, fenced code and thematic breaks.
 *
 * Intentionally not a CommonMark implementation. Anything unrecognised degrades to a paragraph,
 * which renders the author's text rather than dropping it — the same choice the front-matter parser
 * makes. Block nesting is tracked by indent depth only, which is enough for the preview's left
 * padding and avoids maintaining a block stack.
 *
 * Every line pattern comes from [MarkdownSyntax], so the syntax this understands is declared in one
 * place and shared with the highlighter and the format toolbar.
 */
object MarkdownParser {

    fun parse(markdown: String): MarkdownDocument {
        val frontMatter = FrontMatterParser.parse(markdown)
        val bodyStart = frontMatter.bodyStartOffset.coerceIn(0, markdown.length)
        val body = markdown.substring(bodyStart)
        // Blocks are numbered from the top of the body, but a task's line has to address the note's
        // raw Markdown, which is what the editor rewrites.
        val lineOffset = markdown.take(bodyStart).count { it == '\n' }
        return MarkdownDocument(parseBlocks(body, bodyStart, lineOffset))
    }

    /**
     * @param bodyStart the offset of [body] in the whole note, so a span's source offsets address the
     *   note rather than the body.
     */
    private fun parseBlocks(body: String, bodyStart: Int, lineOffset: Int): List<MarkdownBlock> {
        val lines = splitWithOffsets(body, bodyStart)
        val blocks = mutableListOf<MarkdownBlock>()
        val paragraph = mutableListOf<Line>()
        var index = 0

        fun flushParagraph() {
            if (paragraph.isEmpty()) return
            // The paragraph's lines are contiguous in the source, so their join maps onto it one to
            // one and a single trim at each end is all the offset correction needed.
            val joined = paragraph.joinToString("\n") { it.text }
            val leading = joined.length - joined.trimStart().length
            val text = joined.trim()
            if (text.isNotEmpty()) {
                blocks += MarkdownBlock.Paragraph(
                    InlineMarkdownParser.parse(text, baseOffset = paragraph.first().start + leading),
                )
            }
            paragraph.clear()
        }

        while (index < lines.size) {
            val line = lines[index].text
            val leading = line.trimStart()

            if (isFence(line)) {
                flushParagraph()
                val fence = MarkdownSyntax.FENCE.find(line)!!
                val language = line.substring(fence.groups[1]!!.range.last + 1).trim().ifBlank { null }
                val code = mutableListOf<String>()
                index++
                while (index < lines.size && !isFence(lines[index].text)) {
                    code += lines[index].text
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

            if (MarkdownSyntax.THEMATIC_BREAK.matches(line)) {
                flushParagraph()
                blocks += MarkdownBlock.ThematicBreak
                index++
                continue
            }

            val atx = MarkdownSyntax.ATX_HEADING.matchEntire(line)
            if (atx != null) {
                flushParagraph()
                blocks += MarkdownBlock.Heading(
                    level = atx.groupValues[1].length,
                    spans = InlineMarkdownParser.parse(
                        text = atx.groupValues[2],
                        baseOffset = lines[index].start + atx.groups[2]!!.range.first,
                    ),
                )
                index++
                continue
            }

            val underline = lines.getOrNull(index + 1)?.text
            if (underline != null && line.isNotBlank() && MarkdownSyntax.SETEXT_UNDERLINE.matches(underline)) {
                flushParagraph()
                val contentStart = line.indexOfFirst { !it.isWhitespace() }
                blocks += MarkdownBlock.Heading(
                    level = if (underline.trim().startsWith("=")) 1 else 2,
                    spans = InlineMarkdownParser.parse(
                        text = line.trim(),
                        baseOffset = lines[index].start + contentStart,
                    ),
                )
                index += 2
                continue
            }

            // A row of cells is a table only when a delimiter row follows it: without one the pipes
            // are ordinary text and the line stays a paragraph.
            if (underline != null &&
                underlinesTable(line, underline)
            ) {
                flushParagraph()
                val columns = MarkdownSyntax.tableCells(underline)
                blocks += MarkdownBlock.Table(
                    header = tableRow(line, lines[index].start, columns.size),
                    alignments = columns.map(MarkdownSyntax::tableAlignment),
                    rows = tableRows(lines, index + 2, columns.size),
                )
                index += 2
                while (index < lines.size && isTableRow(lines[index].text)) index++
                continue
            }

            val quote = MarkdownSyntax.QUOTE.matchEntire(line)
            if (quote != null) {
                flushParagraph()
                blocks += MarkdownBlock.Quote(
                    depth = MarkdownSyntax.indentDepth(line),
                    spans = InlineMarkdownParser.parse(
                        text = quote.groupValues[2],
                        baseOffset = lines[index].start + quote.groups[2]!!.range.first,
                    ),
                )
                index++
                continue
            }

            val bullet = MarkdownSyntax.BULLET.matchEntire(line)
            if (bullet != null) {
                flushParagraph()
                val contentOffset = lines[index].start + bullet.groups[2]!!.range.first
                val content = bullet.groupValues[2]
                val task = MarkdownSyntax.TASK_MARKER.matchEntire(content)
                blocks += if (task != null) {
                    MarkdownBlock.Task(
                        depth = MarkdownSyntax.indentDepth(line),
                        checked = task.groupValues[1] != " ",
                        spans = InlineMarkdownParser.parse(
                            text = task.groupValues[2],
                            baseOffset = contentOffset + task.groups[2]!!.range.first,
                        ),
                        line = lineOffset + index,
                    )
                } else {
                    MarkdownBlock.Bullet(
                        depth = MarkdownSyntax.indentDepth(line),
                        marker = "•",
                        spans = InlineMarkdownParser.parse(content, baseOffset = contentOffset),
                    )
                }
                index++
                continue
            }

            val ordered = MarkdownSyntax.ORDERED.matchEntire(line)
            if (ordered != null) {
                flushParagraph()
                blocks += MarkdownBlock.Ordered(
                    depth = MarkdownSyntax.indentDepth(line),
                    number = ordered.groupValues[2].toIntOrNull() ?: 1,
                    spans = InlineMarkdownParser.parse(
                        text = ordered.groupValues[3],
                        baseOffset = lines[index].start + ordered.groups[3]!!.range.first,
                    ),
                )
                index++
                continue
            }

            paragraph += lines[index]
            index++
        }

        flushParagraph()
        return blocks
    }

    private fun isFence(line: String): Boolean = MarkdownSyntax.FENCE.containsMatchIn(line)

    /** A table needs a header row of cells and the delimiter row that declares the columns. */
    private fun underlinesTable(header: String, delimiter: String): Boolean =
        header.contains(MarkdownSyntax.TABLE_SEPARATOR) &&
            MarkdownSyntax.TABLE_DELIMITER_ROW.matches(delimiter)

    private fun isTableRow(line: String): Boolean =
        line.isNotBlank() && line.contains(MarkdownSyntax.TABLE_SEPARATOR)

    /**
     * The body rows of a table, each padded with empty cells or cut down to [columns] of them.
     *
     * Normalising here means a renderer never has to reason about a ragged table: rows are stored the
     * width the header declared, which is also what GFM renders.
     */
    private fun tableRows(lines: List<Line>, from: Int, columns: Int): List<List<List<InlineSpan>>> {
        val rows = mutableListOf<List<List<InlineSpan>>>()
        var index = from
        while (index < lines.size && isTableRow(lines[index].text)) {
            rows += tableRow(lines[index].text, lines[index].start, columns)
            index++
        }
        return rows
    }

    private fun tableRow(line: String, lineStart: Int, columns: Int): List<List<InlineSpan>> {
        val cells = MarkdownSyntax.tableCells(line)
        // Each cell is parsed from its own offset, so inline spans point at the right place in the
        // line even though the cells were split on a separator the parser has already skipped.
        var searchFrom = 0
        return (0 until columns).map { column ->
            val text = cells.getOrNull(column).orEmpty()
            val at = line.indexOf(text, searchFrom).takeIf { it >= 0 && text.isNotEmpty() }
            if (at != null) searchFrom = at + text.length
            InlineMarkdownParser.parse(text, baseOffset = lineStart + (at ?: 0))
        }
    }

    /** The body's lines with the offset each one starts at, which the inline spans are relative to. */
    private fun splitWithOffsets(body: String, bodyStart: Int): List<Line> {
        val lines = mutableListOf<Line>()
        var start = 0
        var index = 0
        while (index <= body.length) {
            if (index == body.length || body[index] == '\n') {
                lines += Line(body.substring(start, index), bodyStart + start)
                start = index + 1
            }
            index++
        }
        return lines
    }

    private data class Line(val text: String, val start: Int)
}
