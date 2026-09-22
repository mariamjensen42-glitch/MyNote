package com.cycling.mynote.data.markdown

/**
 * Turns a line of Markdown into [InlineSpan]s.
 *
 * Emphasis nests, so this recurses: the inner text of `**bold *italic***` is parsed again with the
 * outer flags set, which is what makes the flags on [InlineSpan] additive rather than exclusive.
 * Delimiters with no partner are emitted literally, so half-typed text renders as the author typed
 * it instead of disappearing.
 */
object InlineMarkdownParser {

    private val TAG_NAME = Regex("""^</?[A-Za-z][^>]*>$""")

    fun parse(
        text: String,
        bold: Boolean = false,
        italic: Boolean = false,
        strike: Boolean = false,
        linkUrl: String? = null,
    ): List<InlineSpan> {
        val spans = mutableListOf<InlineSpan>()
        val buffer = StringBuilder()
        var index = 0

        fun flush() {
            if (buffer.isNotEmpty()) {
                spans += InlineSpan(buffer.toString(), bold, italic, false, strike, linkUrl)
                buffer.clear()
            }
        }

        while (index < text.length) {
            val char = text[index]
            when {
                char == '\\' && index + 1 < text.length -> {
                    buffer.append(text[index + 1])
                    index += 2
                }

                char == '`' -> {
                    val ticks = runLength(text, index, '`')
                    val close = findRun(text, index + ticks, '`', ticks)
                    if (close < 0) {
                        buffer.append(text, index, index + ticks)
                        index += ticks
                    } else {
                        flush()
                        spans += InlineSpan(
                            text = text.substring(index + ticks, close).trim(),
                            bold = bold,
                            italic = italic,
                            code = true,
                            strike = strike,
                            linkUrl = linkUrl,
                        )
                        index = close + ticks
                    }
                }

                char == '[' || text.startsWith("![", index) -> {
                    val isImage = text.startsWith("![", index)
                    val labelStart = index + if (isImage) 2 else 1
                    val labelEnd = text.indexOf(']', labelStart)
                    val isLink = labelEnd > 0 && labelEnd + 1 < text.length && text[labelEnd + 1] == '('
                    val urlEnd = if (isLink) text.indexOf(')', labelEnd + 2) else -1
                    if (isLink && urlEnd > 0) {
                        flush()
                        val label = text.substring(labelStart, labelEnd)
                        val url = text.substring(labelEnd + 2, urlEnd).trim()
                        if (isImage) {
                            spans += InlineSpan("[$label]", bold, italic, false, strike, url)
                        } else {
                            spans += parse(label, bold, italic, strike, url)
                        }
                        index = urlEnd + 1
                    } else {
                        buffer.append(char)
                        index++
                    }
                }

                text.startsWith("***", index) || text.startsWith("___", index) ->
                    index = consumeEmphasis(text, index, 3, bold = true, italic = true, strike, linkUrl, { flush() }, spans, buffer)

                text.startsWith("**", index) || text.startsWith("__", index) ->
                    index = consumeEmphasis(text, index, 2, bold = true, italic = italic, strike, linkUrl, { flush() }, spans, buffer)

                text.startsWith("~~", index) ->
                    index = consumeEmphasis(text, index, 2, bold, italic, strike = true, linkUrl, { flush() }, spans, buffer)

                (char == '*' || char == '_') && isEmphasisBoundary(text, index) ->
                    index = consumeEmphasis(text, index, 1, bold, italic = true, strike, linkUrl, { flush() }, spans, buffer)

                char == '<' -> {
                    val close = text.indexOf('>', index)
                    val candidate = if (close > index) text.substring(index, close + 1) else null
                    if (candidate != null && TAG_NAME.matches(candidate)) {
                        index = close + 1
                    } else {
                        buffer.append(char)
                        index++
                    }
                }

                else -> {
                    buffer.append(char)
                    index++
                }
            }
        }

        flush()
        return spans
    }

    private fun consumeEmphasis(
        text: String,
        start: Int,
        markerLength: Int,
        bold: Boolean,
        italic: Boolean,
        strike: Boolean,
        linkUrl: String?,
        flush: () -> Unit,
        spans: MutableList<InlineSpan>,
        buffer: StringBuilder,
    ): Int {
        val marker = text.substring(start, start + markerLength)
        val closing = findClosingRun(text, start + markerLength, marker[0], markerLength)
        if (closing == null) {
            buffer.append(marker)
            return start + markerLength
        }
        flush()
        spans += parse(text.substring(start + markerLength, closing.innerEnd), bold, italic, strike, linkUrl)
        return closing.afterRun
    }

    /**
     * Finds the delimiter run that closes an emphasis span.
     *
     * A longer run than the opener belongs partly to the inner text: in `**bold *italic***` the
     * closing run is three asterisks, of which the first closes the italic and the last two close
     * the bold. Taking the whole run would swallow the inner delimiter and leave an unmatched one.
     */
    private fun findClosingRun(text: String, from: Int, char: Char, minLength: Int): ClosingRun? {
        var index = from
        while (index < text.length) {
            if (text[index] == char) {
                val length = runLength(text, index, char)
                if (length >= minLength) {
                    return ClosingRun(
                        innerEnd = index + length - minLength,
                        afterRun = index + length,
                    )
                }
                index += length
            } else {
                index++
            }
        }
        return null
    }

    private data class ClosingRun(val innerEnd: Int, val afterRun: Int)

    /**
     * `_` inside a word is a literal underscore (`snake_case`), not emphasis; `*` is always a
     * delimiter because it has no other meaning in Markdown.
     */
    private fun isEmphasisBoundary(text: String, index: Int): Boolean {
        if (text[index] == '*') return true
        val previous = text.getOrNull(index - 1)
        return previous == null || !previous.isLetterOrDigit()
    }

    private fun runLength(text: String, start: Int, char: Char): Int {
        var index = start
        while (index < text.length && text[index] == char) index++
        return index - start
    }

    private fun findRun(text: String, start: Int, char: Char, length: Int): Int {
        var index = start
        while (index < text.length) {
            if (text[index] == char && runLength(text, index, char) >= length) return index
            index++
        }
        return -1
    }
}
