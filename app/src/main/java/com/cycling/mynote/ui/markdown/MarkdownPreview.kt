package com.cycling.mynote.ui.markdown

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = contentPadding, vertical = contentPadding),
        verticalArrangement = Arrangement.spacedBy(MyNoteTheme.dimens.gapMedium),
    ) {
        MarkdownBlocks(
            blocks = document.blocks,
            scale = MarkdownTypeScale.of(settings),
            bodyColor = MyNoteTheme.colors.textPrimary,
            onToggleTask = onToggleTask,
            onLinkClick = onLinkClick,
            loadImage = loadImage,
        )
    }
}

/**
 * One block after another.
 *
 * Separate from [MarkdownPreview] because a blockquote renders its own blocks through this — the
 * quoted lines were parsed by the same parser, so they are the same kinds of block, and recursing is
 * what makes a quote hold a list, a heading, a code fence or another quote.
 */
@Composable
internal fun MarkdownBlocks(
    blocks: List<MarkdownBlock>,
    scale: MarkdownTypeScale,
    bodyColor: Color,
    onToggleTask: ((line: Int) -> Unit)?,
    onLinkClick: ((url: String) -> Unit)?,
    loadImage: (suspend (String) -> Bitmap?)?,
) {
    blocks.forEach { block ->
        when (block) {
            is MarkdownBlock.Heading -> MarkdownHeading(block, scale, bodyColor, onLinkClick, loadImage)

            // A paragraph that is nothing but pictures is a picture block, which is how a note shows
            // an image on its own line. A picture mixed into prose is drawn among the words it was
            // written between.
            is MarkdownBlock.Paragraph -> if (block.spans.isImageOnly()) {
                MarkdownImageBlock(block.spans.images(), loadImage)
            } else {
                MarkdownParagraph(block.spans, scale, bodyColor, onLinkClick, loadImage)
            }

            is MarkdownBlock.Bullet -> MarkdownListItem(
                spans = block.spans,
                depth = block.depth,
                scale = scale,
                marker = "•",
                bodyColor = bodyColor,
                onLinkClick = onLinkClick,
                loadImage = loadImage,
            )

            is MarkdownBlock.Ordered -> MarkdownListItem(
                spans = block.spans,
                depth = block.depth,
                scale = scale,
                marker = "${block.number}.",
                bodyColor = bodyColor,
                onLinkClick = onLinkClick,
                loadImage = loadImage,
            )

            is MarkdownBlock.Task -> MarkdownListItem(
                spans = block.spans,
                depth = block.depth,
                scale = scale,
                marker = null,
                bodyColor = bodyColor,
                checked = block.checked,
                onLinkClick = onLinkClick,
                loadImage = loadImage,
                onToggle = onToggleTask?.let { toggle -> { toggle(block.line) } },
            )

            is MarkdownBlock.Quote -> MarkdownQuote(
                block = block,
                scale = scale,
                bodyColor = bodyColor,
                onToggleTask = onToggleTask,
                onLinkClick = onLinkClick,
                loadImage = loadImage,
            )

            is MarkdownBlock.Code -> MarkdownCodeBlock(block)

            is MarkdownBlock.Table -> MarkdownTable(block, scale, bodyColor, onLinkClick)

            MarkdownBlock.ThematicBreak -> MarkdownThematicBreak()
        }
    }
}
