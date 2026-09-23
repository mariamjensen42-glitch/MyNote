package com.cycling.mynote.ui.markdown

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import com.cycling.mynote.core.model.EditorSettings

/**
 * How much bigger or smaller than the design's base sizes the user asked for.
 *
 * The settings screen's `字号` and `行距` are scales rather than sizes for a reason: the design's
 * editor frame is drawn at 13.5sp over 21sp, so at the default settings the preview *and* the source
 * view render exactly what the design specifies, and a user who changes the size moves away from it
 * deliberately.
 */
@Immutable
data class MarkdownTypeScale(val size: Float, val leading: Float) {

    /** Applies the scale to a design base style. */
    fun applyTo(style: TextStyle): TextStyle = style.copy(
        fontSize = style.fontSize * size,
        lineHeight = style.lineHeight * size * leading,
    )

    companion object {
        fun of(settings: EditorSettings) = MarkdownTypeScale(
            size = settings.fontSizeSp.toFloat() / EditorSettings.DEFAULT_FONT_SIZE,
            leading = settings.lineHeight / EditorSettings.DEFAULT_LINE_HEIGHT,
        )
    }
}
