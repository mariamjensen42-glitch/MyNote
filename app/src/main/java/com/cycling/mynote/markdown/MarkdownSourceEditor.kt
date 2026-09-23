package com.cycling.mynote.markdown

/**
 * A rewritten note source, with where the selection should land afterwards.
 *
 * Every edit in this package returns one of these instead of touching a text field: the operations
 * are pure string work, so they can be reasoned about and tested without a Compose runtime, and the
 * screen is left with the only job that genuinely needs a UI — turning the result into a
 * `TextFieldValue`.
 *
 * [selectionEnd] is exclusive, exactly like a text field's own selection, so an edit never has to
 * translate between two conventions; a collapsed selection has `selectionStart == selectionEnd`.
 */
data class MarkdownEdit(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int = selectionStart,
)

/**
 * Edits that need to know what a line *is*, not just where the selection is: continuing a list on
 * Enter, indenting a block of lines, ticking tasks off from the toolbar.
 *
 * The syntax each one recognises comes from [MarkdownSyntax], so a list is a list here for exactly
 * the same reason it is one in the parser.
 */
object MarkdownSourceEditor {

    /**
     * Flips the checkbox on [line] of [markdown].
     *
     * @return the rewritten Markdown, or `null` when that line does not exist or is not a task, so a
     *   caller acting on a stale line index leaves the note alone instead of corrupting it. The
     *   result is the same length as the input: both marks are one character.
     */
    fun toggleTask(markdown: String, line: Int): String? {
        val lines = markdown.split('\n').toMutableList()
        val target = lines.getOrNull(line) ?: return null
        val marker = markerOf(target) ?: return null
        if (!marker.task) return null

        val bracket = marker.indent.length + (marker.bullet?.length ?: 0)
        lines[line] = target.substring(0, bracket + 1) + (if (marker.done) " " else "x") +
            target.substring(bracket + 2)
        return lines.joinToString("\n")
    }

    /**
     * What a freshly started line should do when the line above it is a list item: put that item's
     * next marker in front of the caret, so Enter inside a list keeps the list going.
     *
     * This is driven by the text changing rather than by the Enter key, because a soft keyboard never
     * sends Enter as a key event — the IME commits the newline straight into the field. So by the time
     * this runs the line break already exists: [caret] is where the caret landed after it.
     *
     * A task continues unchecked even when the one above is done — the next thing to do is by
     * definition not done yet — and an ordered item counts up. Enter on an item with no text yet
     * ends the list instead, which is how every editor lets you out of one.
     *
     * @return `null` when the line above is not a list item, leaving the newline as the user typed it.
     */
    fun continueList(text: String, caret: Int): MarkdownEdit? {
        val lineStart = lineStart(text, caret)
        // Only a line that is still empty continues a list: anything the caret left behind is text the
        // user typed on purpose. The first line has nothing above it to continue.
        if (lineStart == 0 || caret != lineStart) return null
        if (text.substring(lineStart, lineEnd(text, caret)).isNotBlank()) return null

        val previousStart = lineStart(text, lineStart - 1)
        val previous = text.substring(previousStart, lineStart - 1)
        val marker = markerOf(previous) ?: return null

        if (marker.rest.isBlank()) {
            // An empty item ends the list: drop the marker and the blank line it left behind.
            return MarkdownEdit(
                text = text.substring(0, previousStart) + previous.drop(marker.length) +
                    text.substring(lineStart),
                selectionStart = previousStart,
            )
        }

        val inserted = marker.continuation
        return MarkdownEdit(
            text = text.substring(0, caret) + inserted + text.substring(caret),
            selectionStart = caret + inserted.length,
        )
    }

    /**
     * Indents or outdents every line the selection touches by one [MarkdownSyntax.INDENT].
     *
     * @param outdent removes one indent instead of adding one, and leaves a line that has none alone.
     */
    fun indent(text: String, start: Int, end: Int, outdent: Boolean): MarkdownEdit {
        val block = blockOf(text, start, end)
        val deltas = IntArray(block.lines.size)
        val updated = block.lines.mapIndexed { index, line ->
            val removed = if (outdent) removableIndent(line) else 0
            deltas[index] = if (outdent) -removed else MarkdownSyntax.INDENT.length
            if (outdent) line.substring(removed) else MarkdownSyntax.INDENT + line
        }
        return block.rewrite(updated, deltas, start, end)
    }

    /**
     * Gives every line the selection touches [prefix], or takes it away when they all already have
     * it. A block marker behaves as a switch, the same way an inline one does.
     */
    fun toggleLinePrefix(text: String, start: Int, end: Int, prefix: String): MarkdownEdit {
        val block = blockOf(text, start, end)
        // Blank lines are not given a marker, and do not stop the rest from being stripped.
        val marked = block.lines.filter(String::isNotBlank)
        val removing = marked.isNotEmpty() && marked.all { it.startsWith(prefix) }

        val updated = block.lines.map { line ->
            when {
                line.isBlank() -> line
                removing -> line.removePrefix(prefix)
                else -> prefix + line
            }
        }
        val deltas = IntArray(block.lines.size) { updated[it].length - block.lines[it].length }
        return block.rewrite(updated, deltas, start, end)
    }

    /**
     * The toolbar's task button.
     *
     * On lines that are already tasks it flips their marks; on a bullet it puts a checkbox inside the
     * bullet; on anything else — a plain line, an ordered item, a quote — it puts a task marker in
     * front of the line and leaves the rest as the task's text. Only the first two keep the line's own
     * marker, because those are the shapes the parser reads back as a task; prefixing is what makes
     * the rest round-trip instead of silently turning into plain text.
     *
     * Flipping *off* only happens when every selected line is a finished task, so a mixed selection is
     * never left half-checked by accident.
     */
    fun toggleTasks(text: String, start: Int, end: Int): MarkdownEdit {
        val block = blockOf(text, start, end)
        val markers = block.lines.map(::markerOf)
        val allDone = markers.isNotEmpty() && markers.all { it != null && it.task && it.done }
        val mark = if (allDone) " " else "x"

        val updated = block.lines.mapIndexed { index, line ->
            val marker = markers[index]
            if (marker?.bullet != null) {
                marker.indent + marker.bullet + "[$mark] " + marker.rest
            } else {
                "- [$mark] " + line
            }
        }
        val deltas = IntArray(block.lines.size) { updated[it].length - block.lines[it].length }
        return block.rewrite(updated, deltas, start, end)
    }

    /**
     * Writes a picture reference at the selection.
     *
     * Whatever is selected becomes the alt text, which is what the author was describing anyway; with
     * nothing selected the caret lands between the brackets so the description can be typed straight
     * away.
     */
    fun attachImage(text: String, start: Int, end: Int, reference: String): MarkdownEdit =
        wrap(text, start, end, MarkdownSyntax.IMAGE_PREFIX, "]($reference)")

    /**
     * Wraps the selection in [prefix]/[suffix], or inserts the pair with the caret between them when
     * nothing is selected so typing continues inside the emphasis.
     */
    fun wrap(text: String, start: Int, end: Int, prefix: String, suffix: String): MarkdownEdit {
        val from = start.coerceIn(0, text.length)
        val to = end.coerceIn(from, text.length)
        val selected = text.substring(from, to)
        val updated = text.substring(0, from) + prefix + selected + suffix + text.substring(to)
        val caret = if (selected.isEmpty()) from + prefix.length else to + prefix.length + suffix.length
        return MarkdownEdit(updated, caret)
    }

    /** Indents are spaces, but a line pasted in with a tab should lose that tab first. */
    private fun removableIndent(line: String): Int = when {
        line.startsWith("\t") -> 1
        line.startsWith(MarkdownSyntax.INDENT) -> MarkdownSyntax.INDENT.length
        else -> line.takeWhile { it == ' ' }.length
    }

    /**
     * How a line opens: its indentation, the marker itself, and the text after it.
     *
     * [prefix] is everything up to and including the marker (`> `, `1. `, `- [x] `) and [rest] is what
     * follows, so an edit rebuilds a line by putting the two halves back together around whatever it
     * changed.
     */
    private class LineMarker(
        val indent: String,
        val prefix: String,
        val rest: String,
        val length: Int,
        val task: Boolean = false,
        val done: Boolean = false,
        val bullet: String? = null,
        val number: Int? = null,
        val delimiter: String? = null,
    ) {
        /** The marker the next item starts with. */
        val continuation: String
            get() = when {
                task -> "$indent${bullet ?: MarkdownSyntax.BULLET_MARKER}[ ] "
                number != null -> "$indent${number + 1}${delimiter ?: "."} "
                bullet != null -> "$indent$bullet"
                else -> "$indent${MarkdownSyntax.QUOTE_MARKER}"
            }
    }

    private fun markerOf(line: String): LineMarker? {
        val indent = line.takeWhile { it == ' ' || it == '\t' }
        val rest = line.substring(indent.length)
        if (rest.isEmpty()) return null

        val bullet = bulletOf(rest)
        if (bullet != null) {
            val after = rest.substring(bullet.length)
            val task = MarkdownSyntax.TASK_MARKER.matchEntire(after)
            return LineMarker(
                indent = indent,
                prefix = if (task != null) "$indent$bullet[${task.groupValues[1]}] " else "$indent$bullet",
                rest = task?.groupValues?.get(2) ?: after,
                length = indent.length + bullet.length + if (task != null) 4 else 0,
                task = task != null,
                done = task?.groupValues?.get(1)?.equals("x", ignoreCase = true) == true,
                bullet = bullet,
            )
        }

        val ordered = ORDERED_MARK.find(rest)
        if (ordered != null) {
            val marker = rest.substring(0, ordered.range.last + 1)
            return LineMarker(
                indent = indent,
                prefix = indent + marker,
                rest = rest.substring(marker.length),
                length = indent.length + marker.length,
                number = ordered.groupValues[1].toIntOrNull(),
                delimiter = ordered.groupValues[2],
            )
        }

        if (rest.startsWith(">")) {
            val marker = if (rest.startsWith("> ")) "> " else ">"
            return LineMarker(
                indent = indent,
                prefix = indent + marker,
                rest = rest.substring(marker.length),
                length = indent.length + marker.length,
            )
        }
        return null
    }

    private fun bulletOf(rest: String): String? =
        if (rest.length >= 2 && rest[0] in "-*+" && rest[1] == ' ') rest.substring(0, 2) else null

    /**
     * The lines a selection touches, together with how to write them back.
     *
     * [rewrite] keeps a caret on the text it was on — it moves by the change made to its own line —
     * while a real selection ends up covering the lines the edit touched, which are the lines that
     * just changed under it.
     */
    private class LineBlock(val first: Int, val last: Int, val lines: List<String>, val text: String) {
        fun rewrite(updated: List<String>, deltas: IntArray, start: Int, end: Int): MarkdownEdit {
            val joined = updated.joinToString("\n")
            val result = text.substring(0, first) + joined + text.substring(last)
            if (start == end) {
                val index = lineIndexAt(text, first, start)
                return MarkdownEdit(result, (start + deltas[index]).coerceIn(0, result.length))
            }
            return MarkdownEdit(
                text = result,
                selectionStart = first.coerceAtMost(result.length),
                selectionEnd = (last + deltas.sum()).coerceIn(first, result.length),
            )
        }
    }

    /**
     * The lines a selection touches.
     *
     * The last one is found from `end - 1` rather than `end`: a selection that stops exactly at a
     * line break covers that break, which belongs to the line before it, not the line after.
     */
    private fun blockOf(text: String, start: Int, end: Int): LineBlock {
        val first = lineStart(text, start)
        val contentEnd = if (end > start) end - 1 else start
        val last = lineEnd(text, contentEnd)
        return LineBlock(first, last, text.substring(first, last).split('\n'), text)
    }

    /** How many lines into the block an offset falls. */
    private fun lineIndexAt(text: String, blockStart: Int, offset: Int): Int =
        text.substring(blockStart, offset.coerceIn(blockStart, text.length)).count { it == '\n' }

    private fun lineStart(text: String, offset: Int): Int =
        text.lastIndexOf('\n', (offset - 1).coerceAtLeast(0)).let { if (it < 0 || offset == 0) 0 else it + 1 }

    private fun lineEnd(text: String, offset: Int): Int =
        text.indexOf('\n', offset.coerceIn(0, text.length)).let { if (it < 0) text.length else it }

    private val ORDERED_MARK = Regex("""^(\d+)([.)])\s+""")
}
