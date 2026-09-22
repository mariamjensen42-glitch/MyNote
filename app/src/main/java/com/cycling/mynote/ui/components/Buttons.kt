package com.cycling.mynote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cycling.mynote.ui.icons.MyNoteIcons
import com.cycling.mynote.ui.theme.MyNoteTheme

/**
 * The app's button family.
 *
 * These are drawn from primitives rather than wrapping Material's `Button`: the design specifies
 * exact heights, radii and colours, and Material's button brings its own elevation, minimum touch
 * target and content padding that would all have to be overridden anyway.
 */

/** The design's primary call to action: accent fill, 48 tall, medium radius. */
@Composable
fun MyNotePrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusMedium)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dimens.buttonHeight)
            .clip(shape)
            .background(if (enabled) colors.accent else colors.surfaceSunken)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = dimens.gapLarge),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (enabled) colors.onAccent else colors.textTertiary,
                modifier = Modifier.size(dimens.iconMedium),
            )
        }
        Text(
            text = label,
            style = MyNoteTheme.text.button,
            color = if (enabled) colors.onAccent else colors.textTertiary,
            modifier = if (leadingIcon == null) Modifier else Modifier.padding(start = dimens.gapCompact),
        )
    }
}

/** The neutral counterpart: sunken fill, primary text. */
@Composable
fun MyNoteSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusMedium)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dimens.buttonHeight)
            .clip(shape)
            .background(colors.surfaceSunken)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = dimens.gapLarge),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(dimens.iconMedium),
            )
        }
        Text(
            text = label,
            style = MyNoteTheme.text.buttonMedium,
            color = colors.textPrimary,
            modifier = if (leadingIcon == null) Modifier else Modifier.padding(start = dimens.gapCompact),
        )
    }
}

/**
 * A square, transparent, subtly-rounded tap target for a single icon.
 *
 * Ripples are disabled: the design's icon buttons are flat and a ripple reads as a filled state
 * that the rest of the surface does not have. The click target is expanded to the full control size
 * so a 13dp glyph is still comfortably tappable.
 */
@Composable
fun MyNoteIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MyNoteTheme.colors.textSecondary,
    iconSize: Dp = MyNoteTheme.dimens.iconRegular,
    buttonSize: Dp = MyNoteTheme.dimens.iconButtonSize,
    enabled: Boolean = true,
) {
    val dimens = MyNoteTheme.dimens
    Box(
        modifier = modifier
            .size(buttonSize)
            .clip(RoundedCornerShape(dimens.radiusSmall))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** The library's floating "new note" action. */
@Composable
fun MyNoteFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = MyNoteIcons.plus,
    contentDescription: String = "新建笔记",
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Box(
        modifier = modifier
            .size(dimens.fabSize)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(dimens.radiusPill),
                ambientColor = Color.Black.copy(alpha = 0.14f),
                spotColor = Color.Black.copy(alpha = 0.14f),
            )
            .clip(RoundedCornerShape(dimens.radiusPill))
            .background(colors.accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colors.onAccent,
            modifier = Modifier.size(dimens.iconExtraLarge),
        )
    }
}

/**
 * The editor's save indicator: `已保存` in an accent tint once the file is on disk, and a
 * secondary `编辑中` while there are unsaved changes.
 */
@Composable
fun SaveChip(saved: Boolean, saving: Boolean, modifier: Modifier = Modifier) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens
    val tint = when {
        !saved -> colors.textSecondary
        else -> colors.accent
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(dimens.gapSmall))
            .background(if (saved) colors.accentTint else colors.surfaceSunken)
            .padding(horizontal = dimens.gapSmall, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gapExtraSmall),
    ) {
        Icon(
            imageVector = if (saved) MyNoteIcons.check else MyNoteIcons.pencil,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(10.dp),
        )
        Text(
            text = when {
                saving -> "保存中"
                saved -> "已保存"
                else -> "未保存"
            },
            style = MyNoteTheme.text.badge,
            color = tint,
        )
    }
}

/**
 * A bordered card that groups settings rows.
 *
 * The border is drawn with [Modifier.border] rather than as part of the background so it stays one
 * hairline wide regardless of the surface colour behind it.
 */
@Composable
fun MyNoteCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radiusMedium))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(dimens.radiusMedium)),
    ) {
        Column { content() }
    }
}

/** The hairline that separates rows inside a card or list. */
@Composable
fun MyNoteDivider(modifier: Modifier = Modifier, inset: Dp = 0.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = inset)
            .height(MyNoteTheme.dimens.hairline)
            .background(MyNoteTheme.colors.border),
    )
}

/** A small status pill, e.g. the `标题 / 内容 / 标签` badge on a search result. */
@Composable
fun MyNoteBadge(text: String, modifier: Modifier = Modifier) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(dimens.gapSmall))
            .background(colors.surfaceSunken)
            .padding(horizontal = dimens.gapSmall, vertical = 2.dp),
    ) {
        Text(text = text, style = MyNoteTheme.text.badge, color = colors.textSecondary)
    }
}

/** A compact labelled action, used by the folder-tree long-press strip. */
@Composable
fun MyNoteAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MyNoteTheme.colors.textSecondary,
) {
    val dimens = MyNoteTheme.dimens
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 32.dp)
            .clip(RoundedCornerShape(dimens.radiusSmall))
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.gapExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gapExtraSmall),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Text(text = label, style = MyNoteTheme.text.actionLabel, color = tint)
    }
}
