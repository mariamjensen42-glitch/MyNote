package com.cycling.mynote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cycling.mynote.ui.icons.MyNoteIcons
import com.cycling.mynote.ui.theme.MyNoteTheme

/** One selectable chip above the note list. */
@Composable
fun MyNoteFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(dimens.radiusSmall))
            .background(if (isSelected) colors.accentTint else colors.surfaceSunken)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .height(dimens.chipHeight)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = if (isSelected) MyNoteTheme.text.chipActive else MyNoteTheme.text.chip,
            color = if (isSelected) colors.accent else colors.textSecondary,
        )
    }
}

/**
 * The horizontal chip strip.
 *
 * Scrollable rather than evenly distributed: the design shows five chips that only just fit, and
 * wrapping or shrinking them would break the row rhythm at larger font scales.
 */
@Composable
fun MyNoteChipRow(
    modifier: Modifier = Modifier,
    spacing: androidx.compose.ui.unit.Dp = MyNoteTheme.dimens.gapRegular,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

/** A neutral metadata tag, e.g. under a note row. */
@Composable
fun MyNoteTag(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(dimens.radiusSmall))
            .background(colors.surfaceSunken)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .height(dimens.tagHeight)
            .padding(horizontal = dimens.gapCompact),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MyNoteTheme.text.tag, color = colors.textSecondary)
    }
}

/** An accent-tinted tag, used for the tags on a note in the editor and for `#tag` matches. */
@Composable
fun MyNoteAccentTag(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    outlined: Boolean = false,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusMedium)

    Row(
        modifier = modifier
            .clip(shape)
            .background(if (outlined) Color.Transparent else colors.accentTint)
            .then(if (outlined) Modifier.border(1.dp, colors.border, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = dimens.gapCompact, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gapExtraSmall),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(dimens.iconExtraSmall),
            )
        }
        Text(
            text = text,
            style = MyNoteTheme.text.tagAccent,
            color = if (outlined) colors.textSecondary else colors.accent,
        )
    }
}

/**
 * A two-state switch drawn to the design's measurements.
 *
 * Hand-drawn rather than Material's `Switch` because the design's proportions (44x26 track, 20dp
 * knob, no thumb elevation) differ from Material's, and `Switch`'s own metrics are not part of its
 * public API.
 */
@Composable
fun MyNoteSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens
    val trackColor = when {
        !enabled -> colors.border
        checked -> colors.accent
        else -> colors.surfaceSunken
    }

    Row(
        modifier = modifier
            .width(dimens.switchWidth)
            .height(dimens.switchHeight)
            .clip(RoundedCornerShape(dimens.switchHeight / 2))
            .background(trackColor)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (checked) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .size(dimens.switchKnobSize)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** A small round icon tile used for a repository, folder or rebuild action avatar. */
@Composable
fun MyNoteIconTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 38.dp,
    tint: Color = MyNoteTheme.colors.accent,
    background: Color = MyNoteTheme.colors.accentTint,
    iconSize: androidx.compose.ui.unit.Dp = MyNoteTheme.dimens.iconMedium,
) {
    val dimens = MyNoteTheme.dimens
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(dimens.radiusSmall))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** The `· 标签` separators between a note row's tags. */
@Composable
fun MyNoteDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(3.dp)
            .clip(CircleShape)
            .background(MyNoteTheme.colors.textTertiary),
    )
}

/** A compact `全部 12` count pair used by the search screen's scope filters. */
@Composable
fun MyNoteScopeFilter(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens
    val tint = if (isSelected) colors.textPrimary else colors.textSecondary

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(dimens.radiusSmall))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = dimens.gapExtraSmall, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gapExtraSmall),
    ) {
        Text(
            text = label,
            style = if (isSelected) MyNoteTheme.text.chipActive else MyNoteTheme.text.chip,
            color = tint,
        )
        Text(text = count.toString(), style = MyNoteTheme.text.meta, color = tint)
    }
}

/** The clear (`x`) affordance inside the search field. */
@Composable
fun MyNoteClearButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Box(
        modifier = modifier
            .size(18.dp)
            .clip(RoundedCornerShape(dimens.radiusSmall))
            .background(colors.surfaceSunken)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MyNoteIcons.x,
            contentDescription = "清除",
            tint = colors.textSecondary,
            modifier = Modifier.size(dimens.iconExtraSmall),
        )
    }
}
