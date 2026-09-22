package com.cycling.mynote.data.markdown

import com.cycling.mynote.core.model.FrontMatter

/**
 * Reads and writes the YAML front-matter block of a Markdown note.
 *
 * Deliberately not a YAML implementation: the app has to hand back any block it read without
 * understanding it, so unrecognised keys are kept as raw lines and re-emitted verbatim. Only the
 * keys the app actually models are parsed into typed values, and those accept the handful of
 * spellings people commonly write (`tags` as a flow list or a block list, `tag`/`keywords` as
 * aliases for it, and so on).
 */
object FrontMatterParser {

    private val OPENING = Regex("""^---\s*$""")
    private val CLOSING = Regex("""^(---|\.\.\.)\s*$""")
    private val KEY_VALUE = Regex("""^([A-Za-z0-9_.-]+)\s*:\s*(.*)$""")
    private val LIST_ITEM = Regex("""^\s*-\s+(.*)$""")

    private val TAG_KEYS = setOf("tags", "tag", "keywords")
    private val ALIAS_KEYS = setOf("aliases", "alias")
    private val PINNED_KEYS = setOf("pinned", "pin")
    private val FAVORITE_KEYS = setOf("favorite", "favourite", "starred")
    private val KNOWN_KEYS = TAG_KEYS + ALIAS_KEYS + PINNED_KEYS + FAVORITE_KEYS +
        setOf(FrontMatter.KEY_TITLE, FrontMatter.KEY_CREATED, FrontMatter.KEY_UPDATED)

    /**
     * @return the parsed front matter plus the offset where the body starts. When the document has
     *   no front matter the result is [FrontMatter.EMPTY] and offset `0`.
     */
    fun parse(markdown: String): ParseResult {
        val lines = splitLines(markdown)
        if (lines.isEmpty() || !OPENING.matches(lines[0].text)) {
            return ParseResult(FrontMatter.EMPTY, 0)
        }

        val closingIndex = (1 until lines.size).firstOrNull { CLOSING.matches(lines[it].text) }
            ?: return ParseResult(FrontMatter.EMPTY, 0)

        val block = lines.subList(1, closingIndex).map { it.text }
        val frontMatter = parseBlock(block)
        return ParseResult(frontMatter, lines[closingIndex].endOffset)
    }

    /** Renders [frontMatter] as a block including both delimiters, or `""` when there is nothing. */
    fun render(frontMatter: FrontMatter): String {
        if (!frontMatter.hasContent) return ""

        val body = buildList {
            frontMatter.title?.let { add("${FrontMatter.KEY_TITLE}: ${quoteIfNeeded(it)}") }
            if (frontMatter.tags.isNotEmpty()) add(renderList(FrontMatter.KEY_TAGS, frontMatter.tags))
            if (frontMatter.aliases.isNotEmpty()) {
                add(renderList(FrontMatter.KEY_ALIASES, frontMatter.aliases))
            }
            if (frontMatter.isPinned) add("${FrontMatter.KEY_PINNED}: true")
            if (frontMatter.isFavorite) add("${FrontMatter.KEY_FAVORITE}: true")
            frontMatter.created?.let { add("${FrontMatter.KEY_CREATED}: ${quoteIfNeeded(it)}") }
            frontMatter.updated?.let { add("${FrontMatter.KEY_UPDATED}: ${quoteIfNeeded(it)}") }
            addAll(frontMatter.unknownLines)
        }

        return buildString {
            append(FrontMatter.DELIMITER).append('\n')
            body.forEach { append(it).append('\n') }
            append(FrontMatter.DELIMITER).append('\n')
        }
    }

    private fun parseBlock(block: List<String>): FrontMatter {
        var title: String? = null
        var tags: List<String> = emptyList()
        var aliases: List<String> = emptyList()
        var pinned = false
        var favorite = false
        var created: String? = null
        var updated: String? = null
        val unknown = mutableListOf<String>()

        var index = 0
        while (index < block.size) {
            val line = block[index]
            val match = KEY_VALUE.matchEntire(line)
            if (match == null) {
                // A comment, a blank line, or a continuation of something already consumed.
                if (line.isNotBlank()) unknown += line
                index++
                continue
            }

            val key = match.groupValues[1]
            val rawValue = match.groupValues[2].trim()
            val lowerKey = key.lowercase()

            // Consume an indented block list that belongs to this key.
            val blockItems = mutableListOf<String>()
            var cursor = index + 1
            while (cursor < block.size && rawValue.isEmpty()) {
                val item = LIST_ITEM.matchEntire(block[cursor]) ?: break
                blockItems += unquote(item.groupValues[1].trim())
                cursor++
            }

            when {
                lowerKey == FrontMatter.KEY_TITLE -> title = parseScalar(rawValue)
                lowerKey in TAG_KEYS -> tags = parseList(rawValue, blockItems)
                lowerKey in ALIAS_KEYS -> aliases = parseList(rawValue, blockItems)
                lowerKey in PINNED_KEYS -> pinned = parseBoolean(rawValue)
                lowerKey in FAVORITE_KEYS -> favorite = parseBoolean(rawValue)
                lowerKey == FrontMatter.KEY_CREATED -> created = parseScalar(rawValue)
                lowerKey == FrontMatter.KEY_UPDATED -> updated = parseScalar(rawValue)
                else -> {
                    unknown += line
                    for (i in index + 1 until cursor) unknown += block[i]
                }
            }

            index = cursor
        }

        return FrontMatter(
            title = title,
            tags = tags,
            aliases = aliases,
            isPinned = pinned,
            isFavorite = favorite,
            created = created,
            updated = updated,
            unknownLines = unknown,
        )
    }

    /** Reads `[a, b]`, `a, b`, `a` or a block list into a de-duplicated list. */
    private fun parseList(rawValue: String, blockItems: List<String>): List<String> {
        val items = when {
            blockItems.isNotEmpty() -> blockItems
            rawValue.startsWith("[") && rawValue.endsWith("]") ->
                rawValue.substring(1, rawValue.length - 1).split(',')
            rawValue.isEmpty() -> emptyList()
            else -> listOf(rawValue)
        }
        return items.map { unquote(it.trim()) }.filter { it.isNotEmpty() }.distinct()
    }

    private fun parseScalar(raw: String): String? = unquote(raw).ifEmpty { null }

    private fun parseBoolean(raw: String): Boolean = when (unquote(raw).lowercase()) {
        "true", "yes", "y", "on", "1" -> true
        else -> false
    }

    private fun renderList(key: String, values: List<String>): String =
        "$key: [${values.joinToString(", ") { escapeListValue(it) }}]"

    private fun escapeListValue(value: String): String =
        if (value.any { it == ',' || it == '[' || it == ']' || it == ':' }) quoteIfNeeded(value)
        else value

    /** Quotes a scalar only when leaving it bare would change how it parses. */
    private fun quoteIfNeeded(value: String): String {
        val needsQuoting = value.isBlank() ||
            value != value.trim() ||
            value.any { it == ':' } ||
            value.startsWith("[") || value.startsWith("{") ||
            value.startsWith("\"") || value.startsWith("'") ||
            value.lowercase() in setOf("true", "false", "yes", "no", "on", "off", "null", "~") ||
            value.firstOrNull()?.isDigit() == true ||
            value.any { it == '\n' }
        return if (needsQuoting) "\"" + value.replace("\"", "\\\"") + "\"" else value
    }

    private fun unquote(value: String): String {
        if (value.length >= 2) {
            if (value.startsWith("\"") && value.endsWith("\"")) {
                return value.substring(1, value.length - 1).replace("\\\"", "\"")
            }
            if (value.startsWith("'") && value.endsWith("'")) {
                return value.substring(1, value.length - 1).replace("''", "'")
            }
        }
        return value
    }

    /** A line with the absolute offset just past its terminator, so the body slice stays exact. */
    private fun splitLines(text: String): List<Line> {
        val result = mutableListOf<Line>()
        var start = 0
        var index = 0
        while (index <= text.length) {
            if (index == text.length || text[index] == '\n') {
                val end = if (index < text.length) index + 1 else index
                val raw = text.substring(start, index)
                result += Line(raw.removeSuffix("\r"), end)
                start = end
            }
            index++
        }
        // A trailing newline produces one empty sentinel line that is not a real line.
        if (result.isNotEmpty() && result.last().text.isEmpty() && text.endsWith('\n')) {
            result.removeAt(result.size - 1)
        }
        return result
    }

    private data class Line(val text: String, val endOffset: Int)

    data class ParseResult(val frontMatter: FrontMatter, val bodyStartOffset: Int)
}
