package com.cycling.mynote.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.cycling.mynote.R
import com.cycling.mynote.ui.theme.MyNoteTheme

/**
 * The app's dialog chrome.
 *
 * Material's [AlertDialog] is kept for its behaviour — scrim, focus trapping, back handling,
 * accessibility — while the content uses the app's own typography and colours. Reimplementing a
 * dialog host to get the design's exact paddings would trade all of that for a few dp.
 */
@Composable
private fun MyNoteDialog(
    title: String,
    onDismissRequest: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    confirmTint: Color = MyNoteTheme.colors.accent,
    confirmEnabled: Boolean = true,
    dismissLabel: String = androidx.compose.ui.res.stringResource(R.string.action_cancel),
    body: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = title,
                style = MyNoteTheme.text.sheetTitle,
                color = MyNoteTheme.colors.textPrimary,
            )
        },
        text = body,
        confirmButton = {
            MyNoteDialogButton(
                label = confirmLabel,
                tint = if (confirmEnabled) confirmTint else MyNoteTheme.colors.textTertiary,
                onClick = { if (confirmEnabled) onConfirm() },
            )
        },
        dismissButton = {
            MyNoteDialogButton(
                label = dismissLabel,
                tint = MyNoteTheme.colors.textSecondary,
                onClick = onDismissRequest,
            )
        },
        containerColor = MyNoteTheme.colors.surface,
    )
}

@Composable
private fun MyNoteDialogButton(label: String, tint: Color, onClick: () -> Unit) {
    val dimens = MyNoteTheme.dimens

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(dimens.radiusSmall))
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.gapRegular, vertical = dimens.gapCompact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gapSmall),
    ) {
        Text(text = label, style = MyNoteTheme.text.buttonSmall, color = tint)
    }
}

/** A single-field dialog, used for renaming a note or naming a new folder. */
@Composable
fun MyNoteNameDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit,
    placeholder: String = androidx.compose.ui.res.stringResource(R.string.dialog_name_hint),
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }

    MyNoteDialog(
        title = title,
        onDismissRequest = onDismissRequest,
        confirmLabel = confirmLabel,
        onConfirm = { onConfirm(value.trim()) },
        confirmEnabled = value.isNotBlank(),
        body = {
            MyNoteTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = placeholder,
                onSubmit = { if (value.isNotBlank()) onConfirm(value.trim()) },
            )
        },
    )
}

/** A destructive confirmation, e.g. deleting a note. */
@Composable
fun MyNoteConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    destructive: Boolean = true,
) {
    MyNoteDialog(
        title = title,
        onDismissRequest = onDismissRequest,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        confirmTint = if (destructive) MyNoteTheme.colors.danger else MyNoteTheme.colors.accent,
        body = {
            Text(
                text = body,
                style = MyNoteTheme.text.bodySmall,
                color = MyNoteTheme.colors.textSecondary,
            )
        },
    )
}

/**
 * A picker over a flat list of strings.
 *
 * Root-first and indented by depth, which is enough for a destination the user already has in mind;
 * a collapsible tree inside a dialog would add expansion state to a one-shot decision.
 */
@Composable
fun MyNoteListPickerDialog(
    title: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    onDismissRequest: () -> Unit,
    emptyLabel: String,
    labelStyle: TextStyle = MyNoteTheme.text.rowLabel,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens
    val distinct = remember(options) { options.distinct() }

    MyNoteDialog(
        title = title,
        onDismissRequest = onDismissRequest,
        confirmLabel = androidx.compose.ui.res.stringResource(R.string.action_cancel),
        onConfirm = onDismissRequest,
        body = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
            ) {
                items(distinct) { option ->
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(dimens.radiusSmall))
                                .clickable { onSelect(option) }
                                .padding(horizontal = dimens.gapCompact, vertical = dimens.gapRegular),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = option.substringAfterLast('/').ifEmpty { emptyLabel },
                                style = labelStyle,
                                color = colors.textPrimary,
                                modifier = Modifier.padding(
                                    start = (option.count { it == '/' } * 12).dp,
                                ),
                            )
                        }
                        MyNoteDivider()
                    }
                }
            }
        },
    )
}
