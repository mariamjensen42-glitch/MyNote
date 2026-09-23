package com.cycling.mynote.ui.markdown

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.cycling.mynote.markdown.InlineSpan
import com.cycling.mynote.markdown.MarkdownBlock
import com.cycling.mynote.ui.components.MyNoteDivider
import com.cycling.mynote.ui.icons.MyNoteIcons
import com.cycling.mynote.ui.theme.MyNoteTheme

/**
 * One composable per block type.
 *
 * Blocks are drawn as real composables rather than as one giant `AnnotatedString`, so a code block
 * can carry its own background and a task item its own checkbox. Inline emphasis is folded into a
 * single `AnnotatedString` per block, which is the only level at which separate `Text` composables
 * would break word wrapping across an emphasis boundary.
 */
@Composable
internal fun MarkdownHeading(block: MarkdownBlock.Heading, scale: MarkdownTypeScale, onLinkClick: ((String) -> Unit)?) {
    val base = when (block.level) {
        1 -> MyNoteTheme.text.screenTitle
        2 -> MyNoteTheme.text.sheetTitle
        3 -> MyNoteTheme.text.noteTitleLarge
        else -> MyNoteTheme.text.noteTitle
    }
    Text(
        text = block.spans.toAnnotated(MyNoteTheme.colors.textPrimary, strike = false, onLinkClick),
        style = scale.applyTo(base),
        modifier = Modifier.padding(top = if (block.level <= 2) 8.dp else 4.dp),
    )
}

@Composable
internal fun MarkdownParagraph(
    spans: List<InlineSpan>,
    scale: MarkdownTypeScale,
    onLinkClick: ((String) -> Unit)?,
) {
    Text(
        text = spans.toAnnotated(MyNoteTheme.colors.textPrimary, strike = false, onLinkClick),
        style = scale.applyTo(MyNoteTheme.text.body),
    )
}

@Composable
internal fun MarkdownListItem(
    spans: List<InlineSpan>,
    depth: Int,
    scale: MarkdownTypeScale,
    marker: String?,
    onLinkClick: ((String) -> Unit)?,
    checked: Boolean? = null,
    onToggle: (() -> Unit)? = null,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens
    val bodyStyle = scale.applyTo(MyNoteTheme.text.body)
    val isDone = checked == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp),
        horizontalArrangement = Arrangement.spacedBy(dimens.gapCompact),
        verticalAlignment = Alignment.Top,
    ) {
        if (checked != null) {
            TaskCheckbox(checked = isDone, onClick = onToggle)
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
                onLinkClick = onLinkClick,
            ),
            style = bodyStyle,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The square checkbox of a task item.
 *
 * A real toggle rather than decoration: it carries the checked state for accessibility, so TalkBack
 * announces it as a checkbox, and it is what turns a rendered task list back into something the user
 * can tick off without leaving the preview.
 */
@Composable
private fun TaskCheckbox(checked: Boolean, onClick: (() -> Unit)?) {
    val colors = MyNoteTheme.colors
    val shape = RoundedCornerShape(4.dp)

    Box(
        modifier = Modifier
            .padding(top = 3.dp)
            .size(15.dp)
            .clip(shape)
            .background(if (checked) colors.accent else colors.surfaceSunken)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.toggleable(
                        value = checked,
                        role = Role.Checkbox,
                        onValueChange = { onClick() },
                    )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = MyNoteIcons.check,
                contentDescription = "已完成",
                tint = colors.onAccent,
                modifier = Modifier.size(11.dp),
            )
        }
    }
}

@Composable
internal fun MarkdownQuote(
    spans: List<InlineSpan>,
    scale: MarkdownTypeScale,
    onLinkClick: ((String) -> Unit)?,
) {
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
            text = spans.toAnnotated(colors.textSecondary, strike = false, onLinkClick),
            style = scale.applyTo(MyNoteTheme.text.body),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun MarkdownCodeBlock(block: MarkdownBlock.Code) {
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

@Composable
internal fun MarkdownThematicBreak() {
    MyNoteDivider()
}

/**
 * A GFM table.
 *
 * Columns share the width equally and the alignment comes from the delimiter row; a hairline opens
 * the table and separates the header from the body, which is as much structure as the design's
 * hairline-and-surface vocabulary has for a grid.
 */
@Composable
internal fun MarkdownTable(
    block: MarkdownBlock.Table,
    scale: MarkdownTypeScale,
    onLinkClick: ((String) -> Unit)?,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens
    val bodyStyle = scale.applyTo(MyNoteTheme.text.bodyTight)

    Column(modifier = Modifier.fillMaxWidth()) {
        TableRow(
            cells = block.header,
            alignments = block.alignments,
            style = bodyStyle,
            weight = FontWeight.SemiBold,
            color = colors.textPrimary,
            onLinkClick = onLinkClick,
        )
        MyNoteDivider()
        block.rows.forEachIndexed { index, row ->
            TableRow(
                cells = row,
                alignments = block.alignments,
                style = bodyStyle,
                weight = FontWeight.Normal,
                color = colors.textSecondary,
                onLinkClick = onLinkClick,
            )
            if (index != block.rows.lastIndex) {
                MyNoteDivider(inset = dimens.hairline)
            }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<List<InlineSpan>>,
    alignments: List<MarkdownBlock.Table.Alignment>,
    style: TextStyle,
    weight: FontWeight,
    color: Color,
    onLinkClick: ((String) -> Unit)?,
) {
    val dimens = MyNoteTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimens.gapCompact),
    ) {
        cells.forEachIndexed { column, spans ->
            Text(
                text = spans.toAnnotated(color, strike = false, onLinkClick),
                style = style.copy(fontWeight = weight),
                textAlign = when (alignments.getOrNull(column)) {
                    MarkdownBlock.Table.Alignment.CENTER -> TextAlign.Center
                    MarkdownBlock.Table.Alignment.END -> TextAlign.End
                    else -> TextAlign.Start
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = if (column == cells.lastIndex) 0.dp else dimens.gapRegular),
            )
        }
    }
}

/**
 * Folds inline emphasis into one [androidx.compose.ui.text.AnnotatedString].
 *
 * A link becomes a [LinkAnnotation], which is what makes it tappable inside a plain `Text` — the
 * same annotation the text field in the editor handles, so a link behaves the same in both views.
 */
@Composable
internal fun List<InlineSpan>.toAnnotated(
    baseColor: Color,
    strike: Boolean,
    onLinkClick: ((String) -> Unit)?,
) = buildAnnotatedString {
    val accent = MyNoteTheme.colors.accent
    forEach { span ->
        val url = span.linkUrl
        if (url != null && onLinkClick != null) {
            withLink(
                LinkAnnotation.Url(
                    url = url,
                    styles = TextLinkStyles(
                        style = SpanStyle(color = accent, textDecoration = TextDecoration.Underline),
                    ),
                    linkInteractionListener = { onLinkClick(url) },
                ),
            ) {
                append(span.text)
            }
            return@forEach
        }
        // Until images render as pictures they keep the placeholder form they have always had.
        val text = if (span.imageUrl != null) "[${span.text}]" else span.text
        withStyle(
            SpanStyle(
                color = if (span.linkUrl != null || span.imageUrl != null || span.code) accent else baseColor,
                fontFamily = if (span.code) FontFamily.Monospace else null,
                fontWeight = if (span.bold) FontWeight.SemiBold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                textDecoration = when {
                    span.linkUrl != null || span.imageUrl != null -> TextDecoration.Underline
                    strike || span.strike -> TextDecoration.LineThrough
                    else -> null
                },
            ),
        ) {
            append(text)
        }
    }
}
