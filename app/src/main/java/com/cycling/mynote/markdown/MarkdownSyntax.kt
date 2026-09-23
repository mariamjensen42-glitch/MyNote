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
}
