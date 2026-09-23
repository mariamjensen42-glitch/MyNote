package com.cycling.mynote.markdown

/**
 * Turns a line of Markdown into [InlineSpan]s.
 *
 * This is the only inline implementation in the app: the preview, [MarkdownText]'s plain-text
 * projection and the syntax highlighter all read its output, so a construct cannot be recognised one
 * way for rendering and another way for colouring. Each span carries the source range it came from
 * for the highlighter's benefit; the others ignore it.
 *
 * Emphasis nests, so this recurses: the inner text of `**bold *italic***` is parsed again with the
 * outer flags set, which is what makes the flags on [InlineSpan] additive rather than exclusive.
 * Delimiters with no partner are emitted literally, so half-typed text renders as the author typed
 * it instead of disappearing.
 */
object InlineMarkdownParser {

    /**
     * @param baseOffset the offset of [text] inside the line being parsed, so every returned span's
     *   source range is relative to that line rather than to [text].
     */
    fun parse(
        text: String,
        bold: Boolean = false,
        italic: Boolean = false,
        strike: Boolean = false,
        linkUrl: String? = null,
        baseOffset: Int = 0,
    ): List<InlineSpan> = Scanner(text, baseOffset, bold, italic, strike, linkUrl).scan()

    /**
     * One nesting level of the scan. The flags are the emphasis already open around this level, and
     * they are applied to every span the level emits; a nested construct starts a scanner of its own
     * with the flags it adds.
     */
    private class Scanner(
        private val text: String,
        private val baseOffset: Int,
        private val bold: Boolean,
        private val italic: Boolean,
        private val strike: Boolean,
        private val linkUrl: String?,
    ) {
        private val spans = mutableListOf<InlineSpan>()
        private val buffer = StringBuilder()

        /** Where the buffered run starts in [text]; only meaningful while the buffer is not empty. */
        private var runStart = 0

        fun scan(): List<InlineSpan> {
            var index = 0
            while (index < text.length) {
                val char = text[index]
                index = when {
                    char == '\\' && index + 1 < text.length -> {
                        append(text[index + 1], index)
                        index + 2
                    }

                    char == '`' -> codeSpan(index)

                    char == '[' || text.startsWith("![", index) -> link(index)

                    char == '&' -> entity(index)

                    char == '*' || char == '_' -> delimiterRun(index)

                    text.startsWith("~~", index) -> emphasis(index, 2, strike = true)

                    startsBareLink(index) -> bareLink(index)

                    else -> {
                        append(char, index)
                        index + 1
                    }
                }
            }
            flush(text.length)
            return spans
        }

        /**
         * A `&amp;`-style reference, decoded to the character it stands for.
         *
         * The decoded text goes into the surrounding run, the way an escaped character does: the run's
         * source range still points at the reference in the note, so the highlighter colours what the
         * author typed even though the preview shows one character.
         */
        private fun entity(start: Int): Int {
            val entity = MarkdownSyntax.entityAt(text, start)
            if (entity == null) {
                append('&', start)
                return start + 1
            }
            append(entity.text, start)
            return entity.end
        }

        /** A backtick run, whose content is opaque to every other rule. */
        private fun codeSpan(start: Int): Int {
            val ticks = runLength(start, '`')
            val close = findRun(start + ticks, '`', ticks)
            if (close < 0) {
                appendRange(start, start + ticks)
                return start + ticks
            }
            flush(start)
            // The range covers the markers too: the highlighter styles a code span as one unit, and
            // only rendering trims the content's padding.
            spans += InlineSpan(
                text = text.substring(start + ticks, close).trim(),
                sourceStart = baseOffset + start,
                sourceEnd = baseOffset + close + ticks,
                bold = bold,
                italic = italic,
                code = true,
                strike = strike,
                linkUrl = linkUrl,
            )
            return close + ticks
        }

        /** A `[label](url)` link or `![alt](src)` image, or a literal bracket when it is neither. */
        private fun link(start: Int): Int {
            val isImage = text.startsWith("![", start)
            val labelStart = start + if (isImage) 2 else 1
            val labelEnd = text.indexOf(']', labelStart)
            val isLink = labelEnd > 0 && labelEnd + 1 < text.length && text[labelEnd + 1] == '('
            val urlEnd = if (isLink) text.indexOf(')', labelEnd + 2) else -1
            if (!isLink || urlEnd <= 0) {
                appendRange(start, start + 1)
                return start + 1
            }

            flush(start)
            val label = text.substring(labelStart, labelEnd)
            val url = text.substring(labelEnd + 2, urlEnd).trim()
            if (isImage) {
                spans += InlineSpan(
                    text = label,
                    sourceStart = baseOffset + start,
                    sourceEnd = baseOffset + urlEnd + 1,
                    bold = bold,
                    italic = italic,
                    strike = strike,
                    imageUrl = url,
                )
            } else {
                val inner = parse(
                    text = label,
                    bold = bold,
                    italic = italic,
                    strike = strike,
                    linkUrl = url,
                    baseOffset = baseOffset + labelStart,
                )
                spans += inner.stretch(baseOffset + start, baseOffset + urlEnd + 1)
            }
            return urlEnd + 1
        }

        /**
         * A run of `*` or `_`, taken as emphasis when the delimiter rules allow it and left as
         * literal text when they do not.
         *
         * CommonMark decides by flanking, not by counting: a run that is followed by whitespace can
         * never open emphasis, and one preceded by whitespace can never close it, so `2 * 3 * 4` is
         * arithmetic rather than a mangled italic. Underscores have the further restriction that they
         * cannot open or close inside a word, which is what keeps `foo__bar__baz` and `snake_case`
         * literal while `__init__` — at the start of a word — still emphasises. Reading a run as
         * "there must be a partner somewhere" instead of asking those two questions is what turned
         * ordinary prose into wrong italics.
         *
         * The widest reading of the run is tried first: `***x***` is bold *and* italic, `**x**` bold,
         * `*x*` italic. A run with no valid partner stays literal, so half-typed text still shows the
         * markers the author typed.
         */
        private fun delimiterRun(start: Int): Int {
            val marker = text[start]
            val length = runLength(start, marker)
            if (!canOpen(start, length, marker)) {
                appendRange(start, start + length)
                return start + length
            }

            val widths = when {
                length >= 3 -> intArrayOf(3, 2, 1)
                length == 2 -> intArrayOf(2, 1)
                else -> intArrayOf(1)
            }
            for (width in widths) {
                val closing = findClosingRun(start + width, marker, width) ?: continue
                return emphasise(
                    start = start,
                    width = width,
                    closing = closing,
                    // `***x***` is both, `**x**` is bold inside whatever was already open, and `*x*`
                    // is italic — never losing the emphasis an enclosing construct already applied.
                    bold = bold || width >= 2,
                    italic = italic || width == 1 || width == 3,
                    strike = strike,
                )
            }

            appendRange(start, start + length)
            return start + length
        }

        /**
         * A `~~strike~~`, kept on its own because strikethrough has no flanking restriction: it is a
         * GFM extension that pairs whenever two runs can find each other.
         */
        private fun emphasis(
            start: Int,
            markerLength: Int,
            bold: Boolean = this.bold,
            italic: Boolean = this.italic,
            strike: Boolean = this.strike,
        ): Int {
            val marker = text.substring(start, start + markerLength)
            val closing = findClosingRun(start + markerLength, marker[0], markerLength)
            if (closing == null) {
                appendRange(start, start + markerLength)
                return start + markerLength
            }
            return emphasise(
                start = start,
                width = markerLength,
                closing = closing,
                bold = bold,
                italic = italic,
                strike = true,
            )
        }

        /** Emits the emphasised span for a delimiter pair the caller has already matched. */
        private fun emphasise(
            start: Int,
            width: Int,
            closing: ClosingRun,
            bold: Boolean,
            italic: Boolean,
            strike: Boolean,
        ): Int {
            flush(start)
            val inner = parse(
                text = text.substring(start + width, closing.innerEnd),
                bold = bold,
                italic = italic,
                strike = strike,
                linkUrl = linkUrl,
                baseOffset = baseOffset + start + width,
            )
            spans += inner.stretch(baseOffset + start, baseOffset + closing.afterRun)
            return closing.afterRun
        }

        /** True when the run at [start] may open emphasis, by CommonMark's flanking rules. */
        private fun canOpen(start: Int, length: Int, marker: Char): Boolean {
            val before = text.getOrNull(start - 1)
            val after = text.getOrNull(start + length)
            val leftFlanking = !isWhitespace(after) &&
                (!MarkdownSyntax.isPunctuation(after) || isWhitespace(before) || MarkdownSyntax.isPunctuation(before))
            if (leftFlanking && marker == '_') {
                val rightFlanking = !isWhitespace(before) &&
                    (!MarkdownSyntax.isPunctuation(before) || isWhitespace(after) || MarkdownSyntax.isPunctuation(after))
                return !rightFlanking || MarkdownSyntax.isPunctuation(before)
            }
            return leftFlanking
        }

        /** True when the run at [start] may close emphasis, by CommonMark's flanking rules. */
        private fun canClose(start: Int, length: Int, marker: Char): Boolean {
            val before = text.getOrNull(start - 1)
            val after = text.getOrNull(start + length)
            val rightFlanking = !isWhitespace(before) &&
                (!MarkdownSyntax.isPunctuation(before) || isWhitespace(after) || MarkdownSyntax.isPunctuation(after))
            if (rightFlanking && marker == '_') {
                val leftFlanking = !isWhitespace(after) &&
                    (!MarkdownSyntax.isPunctuation(after) || isWhitespace(before) || MarkdownSyntax.isPunctuation(before))
                return !leftFlanking || MarkdownSyntax.isPunctuation(after)
            }
            return rightFlanking
        }

        /** The start and end of the line count as whitespace, which is what the flanking rules mean. */
        private fun isWhitespace(char: Char?): Boolean = char == null || char.isWhitespace()

        private fun startsBareLink(index: Int): Boolean =
            BARE_LINK.containsMatchIn(text.substring(index, minOf(index + 12, text.length)))

        /**
         * A URL written on its own, without the `[label](url)` wrapper.
         *
         * The run stops at whitespace or an angle bracket, and trailing punctuation is given back —
         * `见 https://example.com。` should link the address and keep the full stop in the sentence.
         */
        private fun bareLink(start: Int): Int {
            var end = start
            while (end < text.length && !text[end].isWhitespace() && text[end] !in "<>") end++
            while (end > start && text[end - 1] in TRAILING_PUNCTUATION) end--

            val written = text.substring(start, end)
            flush(start)
            spans += InlineSpan(
                text = written,
                sourceStart = baseOffset + start,
                sourceEnd = baseOffset + end,
                bold = bold,
                italic = italic,
                strike = strike,
                linkUrl = if (written.startsWith("www.")) "https://$written" else written,
            )
            return end
        }

        /**
         * Widens the runs of a construct to cover it end to end.
         *
         * The delimiters belong to the construct: `**bold**` has to be a range the highlighter can
         * colour as one token, even though the only run inside it is `bold`. Only the outer edges are
         * stretched — the runs in between keep the ranges they were parsed at, so a nested
         * `**a *b* c**` still highlights `*b*` where it actually is.
         */
        private fun List<InlineSpan>.stretch(constructStart: Int, constructEnd: Int): List<InlineSpan> =
            mapIndexed { position, span ->
                span.copy(
                    sourceStart = if (position == 0) constructStart else span.sourceStart,
                    sourceEnd = if (position == lastIndex) constructEnd else span.sourceEnd,
                )
            }

        private fun append(char: Char, at: Int) {
            if (buffer.isEmpty()) runStart = at
            buffer.append(char)
        }

        /** Appends text that stands for less source than it occupies, as an entity does. */
        private fun append(decoded: String, at: Int) {
            if (buffer.isEmpty()) runStart = at
            buffer.append(decoded)
        }

        private fun appendRange(from: Int, to: Int) {
            if (buffer.isEmpty()) runStart = from
            buffer.append(text, from, to)
        }

        /** Emits the buffered run, which spans the source from its start up to [end]. */
        private fun flush(end: Int) {
            if (buffer.isEmpty()) return
            spans += InlineSpan(
                text = buffer.toString(),
                sourceStart = baseOffset + runStart,
                sourceEnd = baseOffset + end,
                bold = bold,
                italic = italic,
                strike = strike,
                linkUrl = linkUrl,
            )
            buffer.clear()
        }

        /**
         * Finds the delimiter run that closes an emphasis span.
         *
         * A run only counts if the flanking rules let it close — a run preceded by a space cannot, so
         * `*foo bar *` has no closer and stays literal. A longer run than the opener belongs partly to
         * the inner text: in `**bold *italic***` the closing run is three asterisks, of which the
         * first closes the italic and the last two close the bold. Taking the whole run would swallow
         * the inner delimiter and leave an unmatched one.
         */
        private fun findClosingRun(from: Int, char: Char, minLength: Int): ClosingRun? {
            var index = from
            while (index < text.length) {
                if (text[index] == char) {
                    val length = runLength(index, char)
                    if (length >= minLength && canClose(index, length, char)) {
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

        private fun runLength(start: Int, char: Char): Int {
            var index = start
            while (index < text.length && text[index] == char) index++
            return index - start
        }

        private fun findRun(start: Int, char: Char, length: Int): Int {
            var index = start
            while (index < text.length) {
                if (text[index] == char && runLength(index, char) >= length) return index
                index++
            }
            return -1
        }
    }

    private data class ClosingRun(val innerEnd: Int, val afterRun: Int)

    private val BARE_LINK = Regex("""^(https?://|www\.)\S""")

    /** Punctuation that ends a sentence rather than a URL. */
    private const val TRAILING_PUNCTUATION = ".,;:!?、。，；：！？"
}
