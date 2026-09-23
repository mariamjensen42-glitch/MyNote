package com.cycling.mynote.ui.markdown

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cycling.mynote.ui.theme.MyNoteTheme

/**
 * One picture a note refers to, loaded asynchronously.
 *
 * A picture rather than a text token: the note owns the file or the address, and what the reader
 * wants to see is the picture. Until it arrives — and if it never does — the alt text stands in, so
 * the layout does not jump and an unreadable reference still says what it was meant to show.
 *
 * [loadImage] is handed in by the screen, which is what knows the note's folder and how to reach the
 * repository; when it is absent (a read-only preview somewhere else) this stays a placeholder.
 */
@Composable
internal fun MarkdownImage(
    reference: String,
    alt: String,
    loadImage: (suspend (String) -> Bitmap?)?,
    modifier: Modifier = Modifier,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    var bitmap by remember(reference) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(reference) { mutableStateOf(false) }

    LaunchedEffect(reference, loadImage) {
        failed = false
        bitmap = if (loadImage == null) {
            failed = true
            null
        } else {
            loadImage(reference).also { failed = it == null }
        }
    }

    val image = bitmap
    if (image == null) {
        // The alt text doubles as the loading state and the failure state: a spinner that replaces
        // text would shift the paragraph, and there is nothing else useful to say.
        Text(
            text = if (alt.isNotBlank()) alt else reference,
            style = MyNoteTheme.text.caption,
            color = if (failed) colors.textTertiary else colors.textSecondary,
            textAlign = TextAlign.Start,
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(dimens.radiusMedium))
                .background(colors.surfaceSunken)
                .padding(dimens.gapCompact),
        )
        return
    }

    Image(
        bitmap = image.asImageBitmap(),
        contentDescription = alt.ifBlank { null },
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .clip(RoundedCornerShape(dimens.radiusMedium)),
    )
}

/** The pictures of a paragraph that is nothing but pictures, one under the other. */
@Composable
internal fun MarkdownImageBlock(
    images: List<Pair<String, String>>,
    loadImage: (suspend (String) -> Bitmap?)?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MyNoteTheme.dimens.gapCompact),
        horizontalAlignment = Alignment.Start,
    ) {
        images.forEach { (reference, alt) ->
            MarkdownImage(reference = reference, alt = alt, loadImage = loadImage)
        }
    }
}
