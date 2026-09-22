package com.cycling.mynote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.cycling.mynote.ui.icons.MyNoteIcons
import com.cycling.mynote.ui.theme.MyNoteTheme

/**
 * The library's search field.
 *
 * Deliberately not an input: tapping it navigates to the search screen, which is where a query is
 * actually typed. A field that takes focus on the library screen would raise the keyboard over a
 * list the user has not decided to leave, and the design's own library screen leads to a dedicated
 * search destination.
 */
@Composable
fun MyNoteSearchAffordance(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索标题、标签或内容",
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dimens.controlHeight)
            .clip(RoundedCornerShape(dimens.radiusMedium))
            .background(colors.surfaceSunken)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = dimens.gapRegular),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            imageVector = MyNoteIcons.search,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = placeholder,
            style = MyNoteTheme.text.bodyTight,
            color = colors.textSecondary,
        )
    }
}

/**
 * The search screen's input.
 *
 * The emphasised variant gets an accent border because on that screen the field *is* the focus of
 * the layout, and it carries a visible caret so a hardware-keyboard user can see where they are.
 */
@Composable
fun MyNoteSearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索标题、标签或内容",
    onClear: (() -> Unit)? = null,
    onSubmit: () -> Unit = {},
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector = MyNoteIcons.search,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusMedium)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, if (value.isNotEmpty()) colors.accent else colors.border, shape),
        textStyle = MyNoteTheme.text.noteTitle.copy(color = colors.textPrimary),
        singleLine = true,
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.gapCompact),
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MyNoteTheme.text.noteTitle,
                            color = colors.textTertiary,
                        )
                    }
                    innerTextField()
                }
                if (value.isNotEmpty() && onClear != null) {
                    MyNoteClearButton(onClick = onClear)
                }
            }
        },
    )
}

/**
 * A single-line text input styled as a form field, used by the rename / new-folder dialogs.
 *
 * Kept separate from [MyNoteSearchInput] so the dialogs do not inherit the search field's accent
 * border-on-content behaviour, which would read as validation.
 */
@Composable
fun MyNoteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    onSubmit: () -> Unit = {},
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusMedium)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(dimens.buttonHeight)
            .clip(shape)
            .background(colors.surfaceSunken)
            .border(1.dp, colors.border, shape),
        textStyle = MyNoteTheme.text.rowLabel.copy(color = colors.textPrimary),
        singleLine = true,
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimens.buttonHeight)
                    .padding(horizontal = dimens.gapRegular),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MyNoteTheme.text.rowLabel,
                            color = colors.textTertiary,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}
