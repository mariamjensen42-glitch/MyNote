package com.cycling.mynote.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.mynote.R
import com.cycling.mynote.ui.components.MyNotePrimaryButton
import com.cycling.mynote.ui.components.MyNoteScreen
import com.cycling.mynote.ui.icons.MyNoteIcons
import com.cycling.mynote.ui.mvi.EffectCollector
import com.cycling.mynote.ui.theme.MyNoteTheme

/**
 * Quick capture.
 *
 * Presented as the design's bottom sheet — a sunken card holding the captured text above a list of
 * destinations — but as a full screen rather than an overlay, because it is also the app's share
 * target: arriving from another app it is the whole reason the app is open, and a sheet over an
 * empty screen would be a sheet over nothing.
 *
 * The text is editable before filing. A share sheet that only offered "append" would make the app
 * the wrong place to fix a truncation or an unwanted URL.
 */
@Composable
fun QuickCaptureScreen(
    onClose: () -> Unit,
    onOpenNote: (String) -> Unit,
    viewModel: QuickCaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    EffectCollector(viewModel.effects) { effect ->
        when (effect) {
            QuickCaptureEffect.Close -> onClose()
            is QuickCaptureEffect.OpenNote -> onOpenNote(effect.noteId)
            is QuickCaptureEffect.ShowMessage -> Unit
        }
    }

    MyNoteScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = dimens.gapLarge),
            verticalArrangement = Arrangement.spacedBy(dimens.gapLarge),
        ) {
            Handle()
            SheetHeader(onClose = { viewModel.onEvent(QuickCaptureEvent.CloseClicked) })
            CapturedText(
                text = state.text,
                sourceLabel = state.sourceLabel,
                onTextChange = { viewModel.onEvent(QuickCaptureEvent.TextChanged(it)) },
            )
            Targets(
                selected = state.target,
                onSelect = { viewModel.onEvent(QuickCaptureEvent.TargetSelected(it)) },
            )

            MyNotePrimaryButton(
                label = if (state.saved) "已保存" else "保存",
                onClick = { viewModel.onEvent(QuickCaptureEvent.SaveClicked) },
                enabled = state.canSave,
            )

            state.error?.let { message ->
                Text(text = message, style = MyNoteTheme.text.caption, color = colors.danger)
            }

            if (state.text.isBlank()) {
                Text(
                    text = stringResource(R.string.capture_empty),
                    style = MyNoteTheme.text.caption,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

/** The sheet's grab handle. */
@Composable
private fun Handle() {
    val colors = MyNoteTheme.colors

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.border),
        )
    }
}

@Composable
private fun SheetHeader(onClose: () -> Unit) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.capture_title),
            style = MyNoteTheme.text.sheetTitle,
            color = colors.textPrimary,
        )
        Icon(
            imageVector = MyNoteIcons.x,
            contentDescription = stringResource(R.string.cd_close),
            tint = colors.textSecondary,
            modifier = Modifier
                .size(dimens.iconLarge)
                .clickable(onClick = onClose),
        )
    }
}

/** The captured text, editable in place. */
@Composable
private fun CapturedText(text: String, sourceLabel: String?, onTextChange: (String) -> Unit) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radiusMedium))
            .background(colors.surfaceSunken)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(dimens.gapCompact),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.gapSmall),
        ) {
            Icon(
                imageVector = MyNoteIcons.globe,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(dimens.iconSmall),
            )
            Text(
                text = sourceLabel?.let { stringResource(R.string.capture_source, it) }
                    ?: stringResource(R.string.capture_source_unknown),
                style = MyNoteTheme.text.caption,
                color = colors.textTertiary,
            )
        }

        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            textStyle = MyNoteTheme.text.bodyTight.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
        )
    }
}

/** The destination list. The selected row is tinted, matching the design's chosen target. */
@Composable
private fun Targets(selected: CaptureTarget, onSelect: (CaptureTarget) -> Unit) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.gapExtraSmall),
    ) {
        CaptureTarget.entries.forEach { target ->
            val isSelected = target == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(dimens.radiusSmall))
                    .background(if (isSelected) colors.accentTint else Color.Transparent)
                    .clickable { onSelect(target) }
                    .padding(horizontal = dimens.gapMedium, vertical = dimens.gapCompact),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.gapRegular),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(dimens.radiusMedium))
                        .background(if (isSelected) colors.accent else colors.surfaceSunken),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = when (target) {
                            CaptureTarget.INBOX -> MyNoteIcons.inbox
                            CaptureTarget.NEW_NOTE -> MyNoteIcons.feather
                        },
                        contentDescription = null,
                        tint = if (isSelected) colors.onAccent else colors.textSecondary,
                        modifier = Modifier.size(dimens.iconRegular),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = target.label,
                        style = MyNoteTheme.text.rowLabelStrong,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = target.description,
                        style = MyNoteTheme.text.monoMicro,
                        color = if (isSelected) colors.accent else colors.textSecondary,
                    )
                }

                if (isSelected) {
                    Icon(
                        imageVector = MyNoteIcons.check,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(dimens.iconRegular),
                    )
                }
            }
        }
    }
}
