package com.cycling.mynote.markdown

/**
 * The Markdown vocabulary, declared once.
 *
 * Every construct the app understands — line patterns, front-matter delimiters, the markers the
 * format toolbar inserts — is defined here rather than in each consumer. Before this file existed the
 * same construct was written out in up to eight places (the task marker alone), and the copies had
 * already drifted: the syntax highlighter coloured `**` inside inline code as emphasis while the
 * parser treated code as opaque, and the search index stripped markers with a second, regex-based
 * inline parser that disagreed with the real one.
 *
 * Inline constructs are deliberately *not* here. They are recognised by [InlineMarkdownParser]'s
 * scanner, which is the only inline implementation; a regex per construct next to the scanner is
 * exactly the duplication this file exists to prevent.
 */
object MarkdownSyntax {

    /** One level of nesting in a nested list, and the unit Tab indents by. */
    const val INDENT = "  "

    // Block structure. Patterns that capture (parentheses) are shared with the parsers, so the
    // groups are part of the contract, not an implementation detail.

    /** `# Heading` — group 1 is the hashes, group 2 the text. */
    val ATX_HEADING = Regex("""^\s{0,3}(#{1,6})\s+(.*?)\s*#*\s*$""")

    /** The `===` / `---` line under a title. */
    val SETEXT_UNDERLINE = Regex("""^\s{0,3}(=+|-{2,})\s*$""")

    /** `---`, `***`, `___`. */
    val THEMATIC_BREAK = Regex("""^\s{0,3}([-*_])(\s*\1){2,}\s*$""")

    /** `- item` — group 1 is the indent, group 2 the content (which may be a task marker). */
    val BULLET = Regex("""^(\s*)[-*+]\s+(.*)$""")

    /** `1. item` / `1) item` — group 1 the indent, group 2 the number, group 3 the content. */
    val ORDERED = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""")

    /** `[ ] rest` / `[x] rest`, matched against a bullet's content. Group 1 is the mark. */
    val TASK_MARKER = Regex("""^\[([ xX])]\s*(.*)$""")

    /** `> quoted` — group 1 the indent, group 2 the content. */
    val QUOTE = Regex("""^(\s*)>\s?(.*)$""")

    /** An opening or closing ``` / ~~~ fence, with an optional info string. */
    val FENCE = Regex("""^\s{0,3}(```|~~~)""")

    /** `---` on its own line, which is what opens a front-matter block. */
    val FRONT_MATTER_OPENING = Regex("""^---\s*$""")

    /** `---` or `...`, which closes one. */
    val FRONT_MATTER_CLOSING = Regex("""^(---|\.\.\.)\s*$""")

    /** `key: value` — group 1 the key, group 2 the raw value. */
    val FRONT_MATTER_ENTRY = Regex("""^([A-Za-z0-9_.-]+)\s*:\s*(.*)$""")

    /** An indented `- item` under a front-matter key. */
    val FRONT_MATTER_LIST_ITEM = Regex("""^\s*-\s+(.*)$""")

    // GFM tables.

    /** The `|---|:--:|` row that turns the line above it into a table header. */
    val TABLE_DELIMITER_ROW = Regex("""^\s*\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)*\|?\s*$""")

    /**
     * Splits a table row into cells, dropping the optional leading and trailing pipe.
     *
     * `\|` inside a cell escapes the pipe; it is unescaped here so a cell can contain one.
     */
    fun tableCells(line: String): List<String> {
        val trimmed = line.trim().removePrefix("|").removeSuffix("|")
        return trimmed.split('|').map { it.trim().replace("\\|", "|") }
    }

    /** The one character that makes a line a candidate table row. */
    const val TABLE_SEPARATOR = '|'

    /** The alignment a delimiter cell asks for: `:--` start, `:-:` centre, `--:` end. */
    fun tableAlignment(cell: String): MarkdownBlock.Table.Alignment {
        val left = cell.startsWith(":")
        val right = cell.endsWith(":")
        return when {
            left && right -> MarkdownBlock.Table.Alignment.CENTER
            right -> MarkdownBlock.Table.Alignment.END
            left -> MarkdownBlock.Table.Alignment.START
            else -> MarkdownBlock.Table.Alignment.NONE
        }
    }

    // Markers the editor inserts. Kept beside the patterns that recognise them so a change to one
    // form is impossible to make without seeing the other.

    const val HEADING_MARKER = "# "
    const val BULLET_MARKER = "- "
    const val TASK_MARKER_UNCHECKED = "- [ ] "
    const val ORDERED_MARKER = "1. "
    const val QUOTE_MARKER = "> "
    const val BOLD_MARKER = "**"
    const val ITALIC_MARKER = "*"
    const val STRIKE_MARKER = "~~"
    const val CODE_MARKER = "`"
    const val LINK_PREFIX = "["
    const val LINK_SUFFIX = "](url)"
    const val IMAGE_PREFIX = "!["
    const val IMAGE_SUFFIX = "](path)"

    /** How many [INDENT] levels a line is nested by. */
    fun indentDepth(line: String): Int = line.indexOfFirst { !it.isWhitespace() }
        .let { if (it < 0) 0 else it } / INDENT.length

    /**
     * Whether [char] is punctuation in the sense the emphasis rules use.
     *
     * CommonMark's flanking rules turn on "Unicode punctuation", which is the general categories
     * `P*` — including `_` itself, since that is a connector punctuation character. Deliberately not
     * `Character.isLetterOrDigit` negated: a Chinese full stop or a `·` has to count, and so does the
     * underscore, or `foo_bar_baz` and `foo__bar__baz` come out differently.
     */
    fun isPunctuation(char: Char?): Boolean = when (char) {
        null -> false
        else -> when (Character.getType(char).toByte()) {
            Character.CONNECTOR_PUNCTUATION,
            Character.DASH_PUNCTUATION,
            Character.START_PUNCTUATION,
            Character.END_PUNCTUATION,
            Character.INITIAL_QUOTE_PUNCTUATION,
            Character.FINAL_QUOTE_PUNCTUATION,
            Character.OTHER_PUNCTUATION,
            -> true

            else -> false
        }
    }

    /**
     * An HTML entity standing at [from], or `null` when there is not one.
     *
     * The named forms are the ones that actually turn up in prose — punctuation, currency, gaps and
     * arrows; the numeric forms cover everything else, decimal and hexadecimal alike. A name outside
     * the table is left alone, which is also what CommonMark does with a name it does not know: the
     * text stays as the author typed it rather than turning into an empty hole.
     */
    fun entityAt(text: String, from: Int): Entity? {
        if (from >= text.length || text[from] != '&') return null

        val terminator = text.indexOf(';', from + 1)
        if (terminator < 0 || terminator - from > MAX_ENTITY_LENGTH) return null
        val body = text.substring(from + 1, terminator)
        if (body.isEmpty()) return null

        val decoded = when {
            body.startsWith("#x", ignoreCase = true) ->
                body.drop(2).toIntOrNull(16)?.let(::codePointText)

            body.startsWith("#") -> body.drop(1).toIntOrNull()?.let(::codePointText)

            else -> NAMED_ENTITIES[body]
        } ?: return null

        return Entity(decoded, terminator + 1)
    }

    /** A decoded entity: what it stands for, and the offset just past it in the source. */
    data class Entity(val text: String, val end: Int)

    /** A code point as text, or `null` when it is not one worth printing (NUL, a lone surrogate). */
    private fun codePointText(codePoint: Int): String? = when {
        codePoint <= 0 || codePoint > Character.MAX_CODE_POINT -> null
        codePoint in 0xD800..0xDFFF -> null
        else -> String(Character.toChars(codePoint))
    }

    /** The named entities decoded in prose: punctuation, gaps, currency and the odd arrow. */
    private val NAMED_ENTITIES: Map<String, String> = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to "\u00A0",
        "ensp" to "\u2002",
        "emsp" to "\u2003",
        "thinsp" to "\u2009",
        "shy" to "\u00AD",
        "hellip" to "…",
        "ldquo" to "“",
        "rdquo" to "”",
        "lsquo" to "‘",
        "rsquo" to "’",
        "laquo" to "«",
        "raquo" to "»",
        "mdash" to "—",
        "ndash" to "–",
        "middot" to "·",
        "bull" to "•",
        "dagger" to "†",
        "sect" to "§",
        "para" to "¶",
        "copy" to "©",
        "reg" to "®",
        "trade" to "™",
        "deg" to "°",
        "plusmn" to "±",
        "times" to "×",
        "divide" to "÷",
        "ne" to "≠",
        "le" to "≤",
        "ge" to "≥",
        "larr" to "←",
        "rarr" to "→",
        "harr" to "↔",
        "cent" to "¢",
        "pound" to "£",
        "yen" to "¥",
        "euro" to "€",
        "check" to "✓",
        "star" to "⋆",
        "hearts" to "♥",
    )

    /** `&#xFFFD;` is the longest form worth looking for; anything longer is not an entity. */
    private const val MAX_ENTITY_LENGTH = 32
}
