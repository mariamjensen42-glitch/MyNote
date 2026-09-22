package com.cycling.mynote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cycling.mynote.core.model.EditorSettings
import com.cycling.mynote.data.markdown.InlineSpan
import com.cycling.mynote.data.markdown.MarkdownBlock
import com.cycling.mynote.data.markdown.MarkdownDocument
import com.cycling.mynote.ui.icons.MyNoteIcons
import com.cycling.mynote.ui.theme.MyNoteTheme

/**
 * Renders a parsed Markdown document as prose.
 *
 * Block layout uses real composables rather than one giant `AnnotatedString`, so a code block can
 * carry its own background and a task item its own checkbox. Inline emphasis is folded into a
 * single `AnnotatedString` per block, which is the only level at which separate `Text` composables
 * would break word wrapping across an emphasis boundary.
 *
 * Typography follows the user's editor settings: [settings]'s font size and line height are applied
 * as a scale and a leading multiplier over the design's base styles, so the settings screen's `字号`
 * and `行距` rows visibly change the preview as well as the source view.
 */
@Composable
fun MarkdownPreview(
    document: MarkdownDocument,
    settings: EditorSettings,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
) {
    val scale = settings.fontSizeSp.toFloat() / EditorSettings.DEFAULT_FONT_SIZE
    val leading = settings.lineHeight / EditorSettings.DEFAULT_LINE_HEIGHT

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = contentPadding, vertical = contentPadding),
        verticalArrangement = Arrangement.spacedBy(MyNoteTheme.dimens.gapMedium),
    ) {
        document.blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> MarkdownHeading(block, scale, leading)
                is MarkdownBlock.Paragraph -> MarkdownParagraph(block.spans, scale, leading)
                is MarkdownBlock.Bullet -> MarkdownListItem(
                    spans = block.spans,
                    depth = block.depth,
                    scale = scale,
                    leading = leading,
                    marker = "•",
                )
                is MarkdownBlock.Ordered -> MarkdownListItem(
                    spans = block.spans,
                    depth = block.depth,
                    scale = scale,
                    leading = leading,
                    marker = "${block.number}.",
                )
                is MarkdownBlock.Task -> MarkdownListItem(
                    spans = block.spans,
                    depth = block.depth,
                    scale = scale,
                    leading = leading,
                    marker = null,
                    checked = block.checked,
                )
                is MarkdownBlock.Quote -> MarkdownQuote(block.spans, scale, leading)
                is MarkdownBlock.Code -> MarkdownCodeBlock(block)
                MarkdownBlock.ThematicBreak -> MyNoteDivider()
            }
        }
    }
}

@Composable
private fun MarkdownHeading(block: MarkdownBlock.Heading, scale: Float, leading: Float) {
    val colors = MyNoteTheme.colors
    val base = when (block.level) {
        1 -> MyNoteTheme.text.screenTitle
        2 -> MyNoteTheme.text.sheetTitle
        3 -> MyNoteTheme.text.noteTitleLarge
        else -> MyNoteTheme.text.noteTitle
    }
    Text(
        text = block.spans.toAnnotated(colors.textPrimary, strike = false),
        style = base.scaled(scale, leading),
        modifier = Modifier.padding(top = if (block.level <= 2) 8.dp else 4.dp),
    )
}

@Composable
private fun MarkdownParagraph(spans: List<InlineSpan>, scale: Float, leading: Float) {
    Text(
        text = spans.toAnnotated(MyNoteTheme.colors.textPrimary, strike = false),
        style = MyNoteTheme.text.body.scaled(scale, leading),
    )
}

@Composable
private fun MarkdownListItem(
    spans: List<InlineSpan>,
    depth: Int,
    scale: Float,
    leading: Float,
    marker: String?,
    checked: Boolean? = null,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens
    val bodyStyle = MyNoteTheme.text.body.scaled(scale, leading)
    val isDone = checked == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp),
        horizontalArrangement = Arrangement.spacedBy(dimens.gapCompact),
        verticalAlignment = Alignment.Top,
    ) {
        if (checked != null) {
            Box(
                modifier = Modifier
                    .padding(top = 3.dp)
                    .size(15.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isDone) colors.accent else colors.surfaceSunken),
                contentAlignment = Alignment.Center,
            ) {
                if (isDone) {
                    Icon(
                        imageVector = MyNoteIcons.check,
                        contentDescription = "已完成",
                        tint = colors.onAccent,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
        } else {
            Text(
                text = marker.orEmpty(),
                style = bodyStyle,
                color = colors.textTertiary,
                modifier = Modifier.width(if (marker == "•") 10.dp else 20.dp),
            )
        }

        Text(
            text = spans.toAnnotated(
                baseColor = if (isDone) colors.textTertiary else colors.textPrimary,
                strike = isDone,
            ),
            style = bodyStyle,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MarkdownQuote(spans: List<InlineSpan>, scale: Float, leading: Float) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.gapRegular),
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(colors.textPrimary),
        )
        Text(
            text = spans.toAnnotated(colors.textSecondary, strike = false),
            style = MyNoteTheme.text.body.scaled(scale, leading),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MarkdownCodeBlock(block: MarkdownBlock.Code) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radiusMedium))
            .background(colors.surfaceSunken)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(dimens.gapExtraSmall),
    ) {
        if (block.language != null) {
            Text(text = block.language, style = MyNoteTheme.text.monoMicro, color = colors.textTertiary)
        }
        Text(
            text = block.lines.joinToString("\n"),
            style = MyNoteTheme.text.monoCode,
            color = colors.textPrimary,
        )
    }
}

/** Folds inline emphasis into one [androidx.compose.ui.text.AnnotatedString]. */
@Composable
private fun List<InlineSpan>.toAnnotated(baseColor: Color, strike: Boolean) =
    buildAnnotatedString {
        val accent = MyNoteTheme.colors.accent
        forEach { span ->
            withStyle(
                SpanStyle(
                    color = if (span.linkUrl != null || span.code) accent else baseColor,
                    fontFamily = if (span.code) FontFamily.Monospace else null,
                    fontWeight = if (span.bold) FontWeight.SemiBold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    textDecoration = when {
                        span.linkUrl != null -> TextDecoration.Underline
                        strike || span.strike -> TextDecoration.LineThrough
                        else -> null
                    },
                ),
            ) {
                append(span.text)
            }
        }
    }

/** Applies the user's font-size scale and line-height multiplier to a design base style. */
private fun TextStyle.scaled(scale: Float, leadingMultiplier: Float): TextStyle = copy(
    fontSize = fontSize * scale,
    lineHeight = lineHeight * scale * leadingMultiplier,
)
