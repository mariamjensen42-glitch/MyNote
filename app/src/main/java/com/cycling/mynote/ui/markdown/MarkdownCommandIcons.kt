package com.cycling.mynote.ui.markdown

import androidx.compose.ui.graphics.vector.ImageVector
import com.cycling.mynote.markdown.MarkdownCommand
import com.cycling.mynote.ui.icons.MyNoteIcons

/**
 * The glyph for each format-toolbar action.
 *
 * Kept out of [com.cycling.mynote.markdown.MarkdownCommands] so the Markdown package stays free of
 * Compose types: a command knows what it inserts, and only the screen knows what it looks like.
 */
fun iconFor(command: MarkdownCommand): ImageVector = when (command.id) {
    "heading" -> MyNoteIcons.heading1
    "bold" -> MyNoteIcons.bold
    "italic" -> MyNoteIcons.italic
    "list" -> MyNoteIcons.list
    "task" -> MyNoteIcons.listChecks
    "quote" -> MyNoteIcons.textQuote
    "code" -> MyNoteIcons.code
    "link" -> MyNoteIcons.link
    else -> MyNoteIcons.image
}
