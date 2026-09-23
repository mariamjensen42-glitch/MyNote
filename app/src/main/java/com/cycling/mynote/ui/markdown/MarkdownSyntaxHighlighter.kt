package com.cycling.mynote.ui.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.cycling.mynote.markdown.InlineMarkdownParser
import com.cycling.mynote.markdown.MarkdownSyntax

/**
 * Colours Markdown source in the editor.
 *
 * Implemented as a [VisualTransformation] with [OffsetMapping.Identity] rather than by rendering the
 * text into separate composables: an identity mapping means the transformation is purely
 * decorative, so the cursor, the selection, and the text the user is typing at are all unaffected by
 * whether a token happens to be highlighted. Nothing is inserted or removed, which is what makes
 * identity correct here.
 *
 * The inline layer is [InlineMarkdownParser]'s, not a set of patterns of its own. Highlighting used
 * to be a parallel implementation — four regexes per line — and it disagreed with the parser in ways
 * users could see: `**` inside a code span was bolded although the code span is opaque,
 * `snake_case` was italicised although the parser keeps those underscores literal, and
 * `~~strike~~`, Setext headings and thematic breaks were not recognised at all. Reading the same
 * spans the preview renders makes that class of bug impossible.
 *
 * The colours are passed in rather than read from a composition local because a
 * [VisualTransformation] is not a composable; the screen builds one per theme change and remembers
 * it.
 */
class MarkdownSyntaxHighlighter(
    private val accent: Color,
    private val textPrimary: Color,
    private val textSecondary: Color,
    private val textTertiary: Color,
    private val codeBackground: Color,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        // Built from the source verbatim: nothing is inserted or removed, which is what lets the
        // offset mapping stay the identity.
        val builder = AnnotatedString.Builder(source)

        val lines = source.split('\n')
        var offset = 0
        var inFence = false
        var inFrontMatter = false
        var frontMatterOpened = false

        for ((index, line) in lines.withIndex()) {
            val lineStart = offset
            val lineEnd = lineStart + line.length
            offset = lineEnd + 1 // the newline this line was split on

            when {
                MarkdownSyntax.FRONT_MATTER_OPENING.matches(line) && !frontMatterOpened -> {
                    frontMatterOpened = true
                    inFrontMatter = true
                    builder.addStyle(fenceSpan(), lineStart, lineEnd)
                }

                MarkdownSyntax.FRONT_MATTER_CLOSING.matches(line) && inFrontMatter -> {
                    inFrontMatter = false
                    builder.addStyle(fenceSpan(), lineStart, lineEnd)
                }

                inFrontMatter -> highlightFrontMatterLine(builder, line, lineStart)

                MarkdownSyntax.FENCE.containsMatchIn(line) -> {
                    inFence = !inFence
                    builder.addStyle(fenceSpan(), lineStart, lineEnd)
                }

                // Code is opaque, so it is tinted as a block and never scanned for inline markup.
                inFence -> builder.addStyle(
                    SpanStyle(color = textSecondary, background = codeBackground),
                    lineStart,
                    lineEnd,
                )

                else -> highlightBodyLine(builder, line, lineStart, lineEnd, lines.getOrNull(index + 1))
            }
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }

    private fun highlightBodyLine(
        builder: AnnotatedString.Builder,
        line: String,
        start: Int,
        end: Int,
        next: String?,
    ) {
        if (line.isEmpty()) return

        val heading = MarkdownSyntax.ATX_HEADING.matchEntire(line)
        val quote = MarkdownSyntax.QUOTE.find(line)
        val listMarker = LIST_MARKER.find(line)
        val ordered = MarkdownSyntax.ORDERED.find(line)
        val nextIsUnderline = next != null && MarkdownSyntax.SETEXT_UNDERLINE.matches(next) &&
            !MarkdownSyntax.THEMATIC_BREAK.matches(line)

        when {
            heading != null -> {
                builder.addStyle(SpanStyle(color = textPrimary, fontWeight = FontWeight.Bold), start, end)
                builder.addStyle(accentSpan(), start + heading.range.first, start + heading.groups[1]!!.range.last + 1)
            }

            // A Setext title: this line is the heading, the underlined line below is marked when the
            // loop reaches it.
            nextIsUnderline && line.isNotBlank() ->
                builder.addStyle(SpanStyle(color = textPrimary, fontWeight = FontWeight.Bold), start, end)

            MarkdownSyntax.SETEXT_UNDERLINE.matches(line) -> builder.addStyle(fenceSpan(), start, end)

            MarkdownSyntax.THEMATIC_BREAK.matches(line) -> builder.addStyle(fenceSpan(), start, end)

            quote != null -> {
                builder.addStyle(SpanStyle(color = textSecondary), start, end)
                builder.addStyle(accentSpan(), start + quote.range.first, start + quote.range.last + 1)
            }

            listMarker != null -> {
                // A ticked-off task reads as done, the way it does in the preview.
                val done = TASK_AT_START.containsMatchIn(line)
                if (done) builder.addStyle(SpanStyle(color = textTertiary), start + listMarker.range.last + 1, end)
                builder.addStyle(
                    SpanStyle(color = accent, fontWeight = FontWeight.Medium),
                    start + listMarker.range.first,
                    start + listMarker.range.last + 1,
                )
            }

            ordered != null -> builder.addStyle(
                SpanStyle(color = accent, fontWeight = FontWeight.Medium),
                start + ordered.range.first,
                start + ordered.range.last + 1,
            )
        }

        highlightInline(builder, line, start)
    }

    private fun highlightFrontMatterLine(
        builder: AnnotatedString.Builder,
        line: String,
        start: Int,
    ) {
        val colon = line.indexOf(':')
        if (colon < 0) {
            builder.addStyle(SpanStyle(color = textSecondary), start, start + line.length)
            return
        }
        builder.addStyle(SpanStyle(color = accent, fontWeight = FontWeight.Medium), start, start + colon + 1)
        builder.addStyle(SpanStyle(color = textPrimary), start + colon + 1, start + line.length)
    }

    /**
     * The inline markup of one line, taken from the parser rather than from patterns of its own.
     *
     * Offsets come back in source coordinates, so the styles land on the markers as well as on the
     * text — a code span reads as one token, the way an editor shows it.
     */
    private fun highlightInline(builder: AnnotatedString.Builder, line: String, lineStart: Int) {
        for (span in InlineMarkdownParser.parse(line, baseOffset = lineStart)) {
            val style = when {
                span.code -> SpanStyle(color = accent, background = codeBackground)

                span.linkUrl != null || span.imageUrl != null -> accentSpan()

                span.strike -> SpanStyle(color = textPrimary, textDecoration = TextDecoration.LineThrough)

                span.bold -> SpanStyle(color = textPrimary, fontWeight = FontWeight.Bold)

                span.italic -> SpanStyle(color = textPrimary)

                else -> null
            } ?: continue
            builder.addStyle(style, span.sourceStart, span.sourceEnd)
        }
    }

    private fun accentSpan() = SpanStyle(color = accent)

    private fun fenceSpan() = SpanStyle(color = textTertiary)

    private companion object {
        /** A bullet, with the task checkbox when the item has one. */
        val LIST_MARKER = Regex("""^\s{0,3}[-*+]\s(\[[ xX]]\s)?""")

        /** `- [x]` at the start of a line: a task that is done. */
        val TASK_AT_START = Regex("""^\s{0,3}[-*+]\s\[[xX]]""")
    }
}
