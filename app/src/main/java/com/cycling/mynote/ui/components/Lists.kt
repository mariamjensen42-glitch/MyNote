package com.cycling.mynote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cycling.mynote.core.model.Note
import com.cycling.mynote.ui.icons.MyNoteIcons
import com.cycling.mynote.ui.theme.MyNoteTheme

/**
 * One note in the library list.
 *
 * @param highlighted draws the row in the accent tint. The design uses it for the note the user is
 *   currently working in, so the list doubles as an indicator of where they were.
 */
@Composable
fun NoteRow(
    note: Note,
    timeLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    selected: Boolean = false,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                when {
                    selected -> colors.accentTint
                    highlighted -> colors.accentTint
                    else -> colors.surface
                },
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(
                horizontal = dimens.rowPaddingHorizontal,
                vertical = dimens.rowPaddingVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.gapCompact),
        ) {
            Text(
                text = note.title,
                style = MyNoteTheme.text.noteTitle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (note.isPinned) {
                Icon(
                    imageVector = MyNoteIcons.pin,
                    contentDescription = "已置顶",
                    tint = colors.accent,
                    modifier = Modifier.size(dimens.iconSmall),
                )
            }
        }

        if (note.snippet.isNotBlank()) {
            Text(
                text = note.snippet,
                style = MyNoteTheme.text.snippet,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.gapCompact),
        ) {
            Text(text = timeLabel, style = MyNoteTheme.text.meta, color = colors.textSecondary)

            // The design writes each tag as "· 技术", so the separator belongs to the tag rather
            // than sitting between the time and the first one.
            note.tags.take(2).forEach { tag ->
                Text(
                    text = "· $tag",
                    style = MyNoteTheme.text.meta,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (note.isFavorite && note.tags.isEmpty()) {
                MyNoteDot()
                Text(text = "收藏", style = MyNoteTheme.text.meta, color = colors.textSecondary)
            }
        }
    }
}

/** A settings section heading, e.g. `笔记仓库`. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MyNoteTheme.text.sectionLabel,
        color = MyNoteTheme.colors.textSecondary,
        modifier = modifier,
    )
}

/**
 * A row inside a settings card.
 *
 * @param accessor what sits on the trailing edge: a value, a value with a chevron, or a switch.
 */
@Composable
fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = false,
    detail: String? = null,
    leadingIcon: ImageVector? = null,
    leadingTint: Color? = null,
    leadingBackground: Color? = null,
    switchChecked: Boolean? = null,
    onSwitchChange: ((Boolean) -> Unit)? = null,
    titleStyle: androidx.compose.ui.text.TextStyle = MyNoteTheme.text.rowLabel,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.rowMinHeight)
            .then(if (onClick != null && switchChecked == null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = dimens.rowPaddingHorizontal, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gapRegular),
    ) {
        if (leadingIcon != null) {
            MyNoteIconTile(
                icon = leadingIcon,
                tint = leadingTint ?: colors.accent,
                background = leadingBackground ?: colors.accentTint,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = titleStyle, color = colors.textPrimary)
            if (detail != null) {
                Text(text = detail, style = MyNoteTheme.text.sectionMeta, color = colors.textSecondary)
            }
        }

        if (value != null) {
            Text(
                text = value,
                style = MyNoteTheme.text.rowValue,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (switchChecked != null && onSwitchChange != null) {
            MyNoteSwitch(checked = switchChecked, onCheckedChange = onSwitchChange)
        } else if (showChevron) {
            Icon(
                imageVector = MyNoteIcons.chevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(dimens.iconCompact),
            )
        }
    }
}

/**
 * The strip of actions the design shows under a note in the folder tree after a long press.
 *
 * Delete is the only red action, which is why [MyNoteAction] takes a tint and this passes
 * [MyNoteTheme.colors.danger] for it alone.
 */
@Composable
fun NoteActionStrip(
    onPin: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    isPinned: Boolean = false,
) {
    val dimens = MyNoteTheme.dimens
    val colors = MyNoteTheme.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = dimens.gapRegular),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MyNoteAction(
            icon = MyNoteIcons.pin,
            label = if (isPinned) "取消置顶" else "置顶",
            onClick = onPin,
        )
        MyNoteAction(icon = MyNoteIcons.pencil, label = "重命名", onClick = onRename)
        MyNoteAction(icon = MyNoteIcons.folderTree, label = "移动", onClick = onMove)
        MyNoteAction(
            icon = MyNoteIcons.trash2,
            label = "删除",
            onClick = onDelete,
            tint = colors.danger,
        )
    }
}

/** A centred `查看全部 12 条结果` link. */
@Composable
fun ShowMoreRow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radiusSmall))
            .clickable(onClick = onClick)
            .padding(vertical = dimens.gapCompact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(text = text, style = MyNoteTheme.text.sectionTitle, color = colors.accent)
        Box(Modifier.width(dimens.gapSmall))
        Icon(
            imageVector = MyNoteIcons.chevronRight,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(dimens.iconCompact),
        )
    }
}
