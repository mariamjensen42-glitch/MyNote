package com.cycling.mynote.ui.markdown

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.cycling.mynote.markdown.InlineSpan
import com.cycling.mynote.ui.theme.MyNoteTheme

/**
 * A block's inline runs, with the pictures among them drawn as pictures.
 *
 * A block with no pictures is one `Text`, exactly as before — which is every paragraph of nearly
 * every note, and keeping that path single-`Text` is what preserves word wrapping across an emphasis
 * boundary. Only when a run carries a picture does the block become a row of pieces, because a
 * picture cannot be part of an `AnnotatedString`: it is laid out, not drawn as a glyph.
 *
 * Splitting a paragraph into pieces costs a wrap opportunity between them (a piece that does not fit
 * moves to the next row whole). That is the right trade for a thing the reader wants to *see*: the
 * alternative — the `[alt]` token this used to render — made a picture inserted into a line of text
 * invisible, which is indistinguishable from the picture having failed to save.
 */
@Composable
internal fun MarkdownRichText(
    spans: List<InlineSpan>,
    style: TextStyle,
    color: Color,
    onLinkClick: ((String) -> Unit)?,
    loadImage: (suspend (String) -> Bitmap?)?,
    modifier: Modifier = Modifier,
    strike: Boolean = false,
) {
    if (spans.none { it.imageUrl != null }) {
        Text(
            text = spans.toAnnotated(color, strike, onLinkClick),
            style = style,
            modifier = modifier,
        )
        return
    }

    val gap = MyNoteTheme.dimens.gapCompact
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        spans.pieces().forEach { piece ->
            when (piece) {
                is Piece.Prose -> Text(
                    text = piece.spans.toAnnotated(color, strike, onLinkClick),
                    style = style,
                )

                is Piece.Picture -> MarkdownImage(
                    reference = piece.reference,
                    alt = piece.alt,
                    loadImage = loadImage,
                    modifier = InlineImageSize,
                )
            }
        }
    }
}

private sealed interface Piece {
    data class Prose(val spans: List<InlineSpan>) : Piece

    data class Picture(val reference: String, val alt: String) : Piece
}

/**
 * The runs cut into prose and pictures.
 *
 * Whitespace-only prose between two pictures is dropped: it was the line break that separated them
 * in the source, and as a piece of its own it would render as an empty line beside pictures that
 * already carry the block's spacing.
 */
private fun List<InlineSpan>.pieces(): List<Piece> {
    val pieces = mutableListOf<Piece>()
    val prose = mutableListOf<InlineSpan>()

    fun flushProse() {
        if (prose.any { it.text.isNotBlank() }) pieces += Piece.Prose(prose.toList())
        prose.clear()
    }

    forEach { span ->
        val reference = span.imageUrl
        if (reference == null) {
            prose += span
        } else {
            flushProse()
            pieces += Piece.Picture(reference, span.text)
        }
    }
    flushProse()

    return pieces
}
