package com.cycling.mynote.data.markdown

import com.cycling.mynote.core.model.NoteStats

/** Plain-text projections of a Markdown document. */
data class NoteText(
    val title: String,
    val snippet: String,
    val stats: NoteStats,
)

/**
 * Derives the title, the list snippet and the character counts from a note's Markdown.
 *
 * The title falls back the way a Markdown reader would expect: explicit front-matter `title`, then
 * the first heading, then the file name. The snippet is the first line of prose, which is what the
 * design shows under each note title.
 */
object NoteTextExtractor {

    /** How much of the first prose line the list snippet keeps. */
    private const val SNIPPET_MAX_LENGTH = 60

    private val ATX_HEADING = Regex("""^\s{0,3}(#{1,6})\s+(.*?)\s*#*\s*$""")
    private val SETEXT_UNDERLINE = Regex("""^\s{0,3}(=+|-{2,})\s*$""")
    private val FENCE = Regex("""^\s{0,3}(```|~~~)""")
    private val THEMATIC_BREAK = Regex("""^\s{0,3}([-*_])(\s*\1){2,}\s*$""")
    private val HTML_COMMENT = Regex("""^\s*<!--""")
    private val IMAGE_LINE = Regex("""^\s*!\[[^\]]*]\([^)]*\)\s*$""")

    fun extract(
        markdown: String,
        fileName: String,
        frontMatter: FrontMatterParser.ParseResult = FrontMatterParser.parse(markdown),
    ): NoteText {
        val body = markdown.substring(frontMatter.bodyStartOffset.coerceIn(0, markdown.length))
        val lines = body.split('\n')

        val headingFromBody = firstHeading(lines)

        val title = frontMatter.frontMatter.title
            ?: headingFromBody
            ?: fileName.removeSuffix(".md").removeSuffix(".markdown").ifBlank { fileName }

        return NoteText(
            title = title,
            snippet = firstProseLine(lines),
            stats = NoteStats(
                lineCount = markdown.count { it == '\n' } + if (markdown.isEmpty()) 0 else 1,
                characterCount = markdown.count { !it.isWhitespace() },
            ),
        )
    }

    /** The text of the first heading, handling both `# Title` and the underlined Setext form. */
    private fun firstHeading(lines: List<String>): String? {
        var inFence = false
        for ((index, line) in lines.withIndex()) {
            if (FENCE.containsMatchIn(line)) {
                inFence = !inFence
                continue
            }
            if (inFence) continue

            ATX_HEADING.matchEntire(line)?.let { return stripInlineMarkdown(it.groupValues[2]) }

            val next = lines.getOrNull(index + 1)
            if (line.isNotBlank() && next != null && SETEXT_UNDERLINE.matches(next) &&
                !THEMATIC_BREAK.matches(line)
            ) {
                return stripInlineMarkdown(line.trim())
            }
        }
        return null
    }

    /** The first line of actual prose, used as the note's snippet. */
    private fun firstProseLine(lines: List<String>): String {
        var inFence = false
        for ((index, line) in lines.withIndex()) {
            if (FENCE.containsMatchIn(line)) {
                inFence = !inFence
                continue
            }
            if (inFence) continue

            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (ATX_HEADING.matches(trimmed)) continue
            if (THEMATIC_BREAK.matches(trimmed)) continue
            if (HTML_COMMENT.containsMatchIn(trimmed)) continue
            if (IMAGE_LINE.matches(trimmed)) continue
            if (nextLineIsSetextUnderline(lines, index, trimmed)) continue

            val text = stripInlineMarkdown(trimmed)
                .removePrefix("- [ ] ").removePrefix("- [x] ").removePrefix("- [X] ")
                .removePrefix("- ").removePrefix("* ").removePrefix("+ ")
                .removePrefix("> ").removePrefix(">")
                .trim()

            if (text.isEmpty()) continue
            return text.take(SNIPPET_MAX_LENGTH)
        }
        return ""
    }

    private fun nextLineIsSetextUnderline(lines: List<String>, index: Int, line: String): Boolean {
        val next = lines.getOrNull(index + 1) ?: return false
        return SETEXT_UNDERLINE.matches(next) && !THEMATIC_BREAK.matches(line)
    }
}

private val INLINE_CODE = Regex("`+([^`]*)`+")
private val IMAGE = Regex("""!\[([^\]]*)]\([^)]*\)""")
private val LINK = Regex("""\[([^\]]*)]\([^)]*\)""")
private val HTML_TAG = Regex("<[^>]+>")
private val EMPHASIS = Regex("""(\*\*\*|\*\*|\*|___|__|_|~~)""")
private val TASK_MARKER = Regex("""^\[[ xX]]\s*""")

/**
 * Reduces inline Markdown to the words it renders, so the same string can feed a snippet, an index
 * row and a search match without the caller knowing anything about Markdown syntax.
 */
fun stripInlineMarkdown(text: String): String = text
    .replace(IMAGE) { it.groupValues[1] }
    .replace(LINK) { it.groupValues[1] }
    .replace(INLINE_CODE) { it.groupValues[1] }
    .replace(HTML_TAG, "")
    .replace(EMPHASIS, "")
    .replace(TASK_MARKER, "")
    .trim()
