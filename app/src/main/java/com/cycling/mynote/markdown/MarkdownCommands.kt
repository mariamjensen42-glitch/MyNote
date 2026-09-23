package com.cycling.mynote.markdown

import androidx.compose.runtime.Immutable

/**
 * One button of the format toolbar: what it inserts, and how it behaves on a selection.
 *
 * The icon is deliberately *not* part of this: an [androidx.compose.ui.graphics.vector.ImageVector]
 * would drag Compose into the Markdown package, so the screen maps [id] to a glyph instead.
 */
@Immutable
data class MarkdownCommand(
    val id: String,
    val label: String,
    val kind: Kind,
) {
    sealed interface Kind {
        /** Wraps the selection, e.g. `**bold**`. */
        data class Wrap(val prefix: String, val suffix: String) : Kind

        /** Prefixes the lines the selection touches, e.g. `# ` or `> `. */
        data class LinePrefix(val prefix: String) : Kind

        /** Ticks off the lines the selection touches, or turns them into tasks. */
        data object TaskToggle : Kind
    }
}

/**
 * The toolbar's nine actions, in the order the design shows them.
 *
 * Applying one is a pure function of the text and the selection, so the whole toolbar is testable
 * without a screen — which it was not while this lived inside `EditorScreen`.
 */
object MarkdownCommands {

    val ALL: List<MarkdownCommand> = listOf(
        MarkdownCommand("heading", "一级标题", MarkdownCommand.Kind.LinePrefix(MarkdownSyntax.HEADING_MARKER)),
        MarkdownCommand("bold", "加粗", MarkdownCommand.Kind.Wrap(MarkdownSyntax.BOLD_MARKER, MarkdownSyntax.BOLD_MARKER)),
        MarkdownCommand("italic", "斜体", MarkdownCommand.Kind.Wrap(MarkdownSyntax.ITALIC_MARKER, MarkdownSyntax.ITALIC_MARKER)),
        MarkdownCommand("list", "无序列表", MarkdownCommand.Kind.LinePrefix(MarkdownSyntax.BULLET_MARKER)),
        MarkdownCommand("task", "任务列表", MarkdownCommand.Kind.TaskToggle),
        MarkdownCommand("quote", "引用", MarkdownCommand.Kind.LinePrefix(MarkdownSyntax.QUOTE_MARKER)),
        MarkdownCommand("code", "代码", MarkdownCommand.Kind.Wrap(MarkdownSyntax.CODE_MARKER, MarkdownSyntax.CODE_MARKER)),
        MarkdownCommand("link", "链接", MarkdownCommand.Kind.Wrap(MarkdownSyntax.LINK_PREFIX, MarkdownSyntax.LINK_SUFFIX)),
        MarkdownCommand("image", "图片", MarkdownCommand.Kind.Wrap(MarkdownSyntax.IMAGE_PREFIX, MarkdownSyntax.IMAGE_SUFFIX)),
    )

    /** @return the source after [command] has been applied to the selection `[start, end)`. */
    fun apply(command: MarkdownCommand, text: String, start: Int, end: Int): MarkdownEdit =
        when (val kind = command.kind) {
            is MarkdownCommand.Kind.Wrap -> MarkdownSourceEditor.wrap(text, start, end, kind.prefix, kind.suffix)
            is MarkdownCommand.Kind.LinePrefix -> MarkdownSourceEditor.toggleLinePrefix(text, start, end, kind.prefix)
            MarkdownCommand.Kind.TaskToggle -> MarkdownSourceEditor.toggleTasks(text, start, end)
        }
}
