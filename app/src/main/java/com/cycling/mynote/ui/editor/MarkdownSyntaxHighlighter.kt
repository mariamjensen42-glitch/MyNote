package com.cycling.mynote.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Colours Markdown source in the editor.
 *
 * Implemented as a [VisualTransformation] with [OffsetMapping.Identity] rather than by rendering
 * the text into separate composables: an identity mapping means the transformation is purely
 * decorative, so the cursor, the selection, and the text the user is typing at are all unaffected by
 * whether a token happens to be highlighted. Nothing is inserted or removed, which is what makes
 * identity correct here.
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

        var offset = 0
        var inFence = false
        var inFrontMatter = false
        var frontMatterOpened = false

        for (line in source.split('\n')) {
            val lineStart = offset
            val lineEnd = offset + line.length
            offset = lineEnd + 1 // the newline this line was split on

            when {
                FRONT_MATTER_DELIMITER.matches(line) && !frontMatterOpened -> {
                    frontMatterOpened = true
                    inFrontMatter = true
                    builder.addStyle(fenceSpan(), lineStart, lineEnd)
                }

                FRONT_MATTER_DELIMITER.matches(line) && inFrontMatter -> {
                    inFrontMatter = false
                    builder.addStyle(fenceSpan(), lineStart, lineEnd)
                }

                inFrontMatter -> highlightFrontMatterLine(builder, line, lineStart)

                FENCE_OPEN.matches(line) -> {
                    inFence = !inFence
                    builder.addStyle(fenceSpan(), lineStart, lineEnd)
                }

                inFence -> builder.addStyle(
                    SpanStyle(color = textSecondary, background = codeBackground),
                    lineStart,
                    lineEnd,
                )

                else -> highlightBodyLine(builder, line, lineStart)
            }
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }

    private fun highlightBodyLine(builder: AnnotatedString.Builder, line: String, start: Int) {
        if (line.isEmpty()) return

        ATX_HEADING.find(line)?.let { match ->
            builder.addStyle(
                SpanStyle(color = textPrimary, fontWeight = FontWeight.Bold),
                start,
                start + line.length,
            )
            builder.addStyle(
                SpanStyle(color = accent),
                start + match.range.first,
                start + match.range.last + 1,
            )
            return
        }

        QUOTE.find(line)?.let { match ->
            builder.addStyle(SpanStyle(color = textSecondary), start, start + line.length)
            builder.addStyle(
                SpanStyle(color = accent),
                start + match.range.first,
                start + match.range.last + 1,
            )
        }

        TASK.find(line)?.let { match ->
            builder.addStyle(
                SpanStyle(color = accent, fontWeight = FontWeight.Bold),
                start + match.range.first,
                start + match.range.last + 1,
            )
            if (match.groupValues[1] != " ") builder.addStyle(
                SpanStyle(color = textTertiary),
                start + match.range.last + 2,
                start + line.length,
            )
        }

        LIST_MARKER.find(line)?.let { match ->
            builder.addStyle(
                SpanStyle(color = accent),
                start + match.range.first,
                start + match.range.last + 1,
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

    /** Inline code, emphasis and links, applied over the whole line. */
    private fun highlightInline(
        builder: AnnotatedString.Builder,
        line: String,
        start: Int,
    ) {
        INLINE_CODE.findAll(line).forEach { match ->
            builder.addStyle(
                SpanStyle(color = accent, background = codeBackground),
                start + match.range.first,
                start + match.range.last + 1,
            )
        }
        LINK.findAll(line).forEach { match ->
            builder.addStyle(
                SpanStyle(color = accent),
                start + match.range.first,
                start + match.range.last + 1,
            )
        }
        IMAGE.findAll(line).forEach { match ->
            builder.addStyle(
                SpanStyle(color = accent),
                start + match.range.first,
                start + match.range.last + 1,
            )
        }
        BOLD.findAll(line).forEach { match ->
            builder.addStyle(
                SpanStyle(color = textPrimary, fontWeight = FontWeight.Bold),
                start + match.range.first,
                start + match.range.last + 1,
            )
        }
        ITALIC.findAll(line).forEach { match ->
            builder.addStyle(
                SpanStyle(color = textPrimary),
                start + match.range.first,
                start + match.range.last + 1,
            )
        }
    }

    private fun fenceSpan() = SpanStyle(color = textTertiary)

    private companion object {
        val FRONT_MATTER_DELIMITER = Regex("""^(---|\.\.\.)\s*$""")
        val FENCE_OPEN = Regex("""^\s{0,3}(```|~~~)""")
        val ATX_HEADING = Regex("""^\s{0,3}#{1,6}\s""")
        val QUOTE = Regex("""^\s{0,3}>""")
        val LIST_MARKER = Regex("""^\s{0,3}([-*+]|\d+[.)])\s""")
        val TASK = Regex("""^\s{0,3}[-*+]\s(\[[ xX]])""")
        val INLINE_CODE = Regex("`+[^`]*`+")
        val IMAGE = Regex("""!\[[^\]]*]\([^)]*\)""")
        val LINK = Regex("""\[[^\]]*]\([^)]*\)""")
        val BOLD = Regex("""(\*\*|__)[^*_\n]+(\*\*|__)""")
        val ITALIC = Regex("""(?<!\*)\*[^*\n]+\*(?!\*)""")
    }
}
