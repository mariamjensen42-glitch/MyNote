package com.cycling.mynote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.cycling.mynote.ui.icons.MyNoteIcons
import com.cycling.mynote.ui.theme.MyNoteTheme

/**
 * The four destinations of the app.
 *
 * A closed enum rather than free-form tab data: the bottom bar and the navigation graph both need
 * to agree on exactly this set, and deriving the nav routes from the same enum is what keeps them
 * from drifting.
 */
enum class BottomTab(
    val label: String,
    val icon: ImageVector,
) {
    LIBRARY("笔记库", MyNoteIcons.notebookText),
    SEARCH("搜索", MyNoteIcons.search),
    DIARY("日记", MyNoteIcons.calendarDays),
    SETTINGS("设置", MyNoteIcons.settings),
}

/**
 * The floating tab capsule.
 *
 * The selected tab gets a sunken fill plus primary-coloured icon and label; the unselected ones
 * fade to secondary text. Two signals rather than one, because the raised capsule is shallow enough
 * that a fill alone is hard to spot in the light theme.
 */
@Composable
fun MyNoteBottomBar(
    selected: BottomTab,
    onSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens
    val capsuleShape = RoundedCornerShape(dimens.radiusCapsule)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gapLarge, vertical = dimens.gapMedium),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.tabBarHeight)
                .shadow(
                    elevation = 8.dp,
                    shape = capsuleShape,
                    ambientColor = Color.Black.copy(alpha = 0.08f),
                    spotColor = Color.Black.copy(alpha = 0.08f),
                )
                .clip(capsuleShape)
                .background(colors.surface)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomTab.entries.forEach { tab ->
                TabItem(
                    tab = tab,
                    isSelected = tab == selected,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: BottomTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens
    val tint = if (isSelected) colors.textPrimary else colors.textSecondary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(dimens.tabBarHeight - 12.dp)
            .clip(RoundedCornerShape(dimens.radiusTab))
            .background(if (isSelected) colors.surfaceSunken else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier.size(dimens.iconLarge),
        )
        Box(Modifier.height(3.dp))
        Text(
            text = tab.label,
            style = if (isSelected) MyNoteTheme.text.tabLabelActive else MyNoteTheme.text.tabLabel,
            color = tint,
        )
    }
}

/**
 * The editor's three-way view switch (`编辑 / 分屏 / 预览`).
 *
 * A segmented control rather than three icon buttons: the modes are mutually exclusive and the
 * continuous sunken track communicates that in a way three separate buttons do not.
 */
@Composable
fun MyNoteSegmentedIconControl(
    options: List<Pair<ImageVector, String>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(dimens.radiusMedium))
            .background(colors.surfaceSunken)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, (icon, label) ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(dimens.radiusSmall))
                    .background(if (isSelected) colors.surface else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(index) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) colors.textPrimary else colors.textSecondary,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/** A labelled row that behaves like a segmented control but shows text, e.g. the theme picker. */
@Composable
fun <T> MyNoteSegmentedTextControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    icon: (T) -> ImageVector,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radiusMedium))
            .background(colors.surfaceSunken)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(dimens.radiusSmall))
                    .background(if (isSelected) colors.surface else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(option) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = icon(option),
                    contentDescription = null,
                    tint = if (isSelected) colors.accent else colors.textTertiary,
                    modifier = Modifier.size(dimens.iconCompact),
                )
                Box(Modifier.width(dimens.gapSmall))
                Text(
                    text = label(option),
                    style = if (isSelected) {
                        MyNoteTheme.text.rowValue.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    } else {
                        MyNoteTheme.text.rowValue
                    },
                    color = if (isSelected) colors.textPrimary else colors.textSecondary,
                )
            }
        }
    }
}
