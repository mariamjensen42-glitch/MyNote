package com.cycling.mynote.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.cycling.mynote.core.model.MatchRun
import com.cycling.mynote.ui.icons.MyNoteIcons
import com.cycling.mynote.ui.theme.MyNoteTheme

/**
 * Renders a run list with the matched runs tinted and bolded.
 *
 * Uses a single [Text] over an [AnnotatedString] rather than a `Row` of `Text`s: separate
 * composables each lay out independently, so a long snippet could not wrap across the boundary
 * between a match and the text around it.
 */
@Composable
fun MatchRunsText(
    runs: List<MatchRun>,
    style: TextStyle,
    modifier: Modifier = Modifier,
    textColor: Color = MyNoteTheme.colors.textPrimary,
) {
    val accent = MyNoteTheme.colors.accent
    val annotated = buildAnnotatedString {
        runs.forEach { run ->
            if (run.isMatch) {
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) {
                    append(run.text)
                }
            } else {
                withStyle(SpanStyle(color = textColor)) { append(run.text) }
            }
        }
    }
    Text(text = annotated, style = style, modifier = modifier)
}

/** The search result's `Head` row: a file glyph, then the file name with its match highlighted. */
@Composable
fun SearchResultTitle(
    titleRuns: List<MatchRun>,
    modifier: Modifier = Modifier,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gapCompact),
    ) {
        Icon(
            imageVector = MyNoteIcons.fileText,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(dimens.iconCompact),
        )
        MatchRunsText(
            runs = titleRuns,
            style = MyNoteTheme.text.noteTitle,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Builds an [AnnotatedString] for callers that need one outside a composable. */
fun List<MatchRun>.toAnnotatedString(accent: Color): AnnotatedString = buildAnnotatedString {
    forEach { run ->
        if (run.isMatch) {
            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) {
                append(run.text)
            }
        } else {
            append(run.text)
        }
    }
}
