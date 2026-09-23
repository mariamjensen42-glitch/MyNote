package com.cycling.mynote.ui.markdown

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cycling.mynote.core.model.EditorSettings
import com.cycling.mynote.markdown.MarkdownBlock
import com.cycling.mynote.markdown.MarkdownDocument
import com.cycling.mynote.markdown.images
import com.cycling.mynote.markdown.isImageOnly
import com.cycling.mynote.ui.theme.MyNoteTheme

/**
 * Renders a parsed Markdown document as prose.
 *
 * The preview is interactive where the source allows it: a task's checkbox toggles the line it came
 * from through [onToggleTask], a link opens through [onLinkClick], and a picture is fetched through
 * [loadImage]. All are optional, so a read-only preview is just a call that omits them — and none is
 * guessed at here, because the screen is what knows whether the note is editable, what may be opened
 * and where the note's files live.
 *
 * Typography follows the user's editor settings: [settings]'s font size and line height are applied
 * as a scale and a leading multiplier over the design's base styles — the same scale the source view
 * uses — so the settings screen's `字号` and `行距` rows visibly change both.
 */
@Composable
fun MarkdownPreview(
    document: MarkdownDocument,
    settings: EditorSettings,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    onToggleTask: ((line: Int) -> Unit)? = null,
    onLinkClick: ((url: String) -> Unit)? = null,
    loadImage: (suspend (reference: String) -> Bitmap?)? = null,
) {
    val scale = MarkdownTypeScale.of(settings)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = contentPadding, vertical = contentPadding),
        verticalArrangement = Arrangement.spacedBy(MyNoteTheme.dimens.gapMedium),
    ) {
        document.blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> MarkdownHeading(block, scale, onLinkClick, loadImage)

                // A paragraph that is nothing but pictures is a picture block, which is how a note
                // shows an image on its own line. A picture mixed into prose is drawn among the words
                // it was written between.
                is MarkdownBlock.Paragraph -> if (block.spans.isImageOnly()) {
                    MarkdownImageBlock(block.spans.images(), loadImage)
                } else {
                    MarkdownParagraph(block.spans, scale, onLinkClick, loadImage)
                }

                is MarkdownBlock.Bullet -> MarkdownListItem(
                    spans = block.spans,
                    depth = block.depth,
                    scale = scale,
                    marker = "•",
                    onLinkClick = onLinkClick,
                    loadImage = loadImage,
                )

                is MarkdownBlock.Ordered -> MarkdownListItem(
                    spans = block.spans,
                    depth = block.depth,
                    scale = scale,
                    marker = "${block.number}.",
                    onLinkClick = onLinkClick,
                    loadImage = loadImage,
                )

                is MarkdownBlock.Task -> MarkdownListItem(
                    spans = block.spans,
                    depth = block.depth,
                    scale = scale,
                    marker = null,
                    checked = block.checked,
                    onLinkClick = onLinkClick,
                    loadImage = loadImage,
                    onToggle = onToggleTask?.let { toggle -> { toggle(block.line) } },
                )

                is MarkdownBlock.Quote -> MarkdownQuote(block.spans, scale, onLinkClick, loadImage)

                is MarkdownBlock.Code -> MarkdownCodeBlock(block)

                is MarkdownBlock.Table -> MarkdownTable(block, scale, onLinkClick)

                MarkdownBlock.ThematicBreak -> MarkdownThematicBreak()
            }
        }
    }
}
