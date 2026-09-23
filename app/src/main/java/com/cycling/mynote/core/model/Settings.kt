package com.cycling.mynote.core.model

import androidx.compose.runtime.Immutable

/** App theme preference. `SYSTEM` is the default and the option the design shows selected. */
enum class ThemeMode(val key: String, val label: String) {
    LIGHT("light", "浅色"),
    DARK("dark", "深色"),
    SYSTEM("system", "跟随系统"),
    ;

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * Editor font choice.
 *
 * All options resolve to platform families rather than bundled files: the design's body face is
 * Inter but its copy is mostly Chinese, which falls back to the platform CJK face regardless, and
 * the system CJK face *is* Source Han Sans — the `SANS` option the design labels 思源黑体.
 */
enum class EditorFont(val key: String, val label: String) {
    SANS("sans", "思源黑体"),
    SYSTEM("system", "系统默认"),
    SERIF("serif", "衬线"),
    MONO("mono", "等宽"),
    ;

    companion object {
        fun fromKey(key: String?): EditorFont = entries.firstOrNull { it.key == key } ?: SANS
    }
}

/** Everything the settings screen controls, persisted in one preferences file. */
@Immutable
data class EditorSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val font: EditorFont = EditorFont.SANS,
    val fontSizeSp: Int = DEFAULT_FONT_SIZE,
    val lineHeight: Float = DEFAULT_LINE_HEIGHT,
    val softWrap: Boolean = true,
    val autoSave: Boolean = true,
    /**
     * Where a picture picked with the toolbar's 图片 button is filed, as a path inside the
     * repository. Pictures are referenced from the note, never inlined into it, so the markdown file
     * stays readable on its own — the same arrangement Obsidian calls the attachment folder.
     */
    val attachmentFolder: String = DEFAULT_ATTACHMENT_FOLDER,
) {
    companion object {
        const val DEFAULT_FONT_SIZE = 16
        const val MIN_FONT_SIZE = 12
        const val MAX_FONT_SIZE = 24
        const val FONT_SIZE_STEP = 1

        const val DEFAULT_ATTACHMENT_FOLDER = "attachments"

        const val DEFAULT_LINE_HEIGHT = 1.6f
        const val MIN_LINE_HEIGHT = 1.2f
        const val MAX_LINE_HEIGHT = 2.2f
        const val LINE_HEIGHT_STEP = 0.1f
    }
}

/** Which of the editor's three view modes is active. */
enum class EditorViewMode(val label: String, val iconKey: String) {
    SOURCE("编辑", "pencilLine"),
    SPLIT("分屏", "columns"),
    PREVIEW("预览", "eye"),
}
