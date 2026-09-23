package com.cycling.mynote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cycling.mynote.ui.icons.MyNoteIcons
import com.cycling.mynote.ui.theme.MyNoteTheme

/**
 * The frame every screen sits in.
 *
 * Two nested columns rather than a `Scaffold`: the design's floating action button must sit above
 * the tab capsule but inside the area the content scrolls through, and Material's `Scaffold`
 * positions its FAB relative to the whole screen. The inner `Box` gives the caller's content the
 * full remaining height while letting the FAB anchor to its bottom-right without overlapping the
 * tab bar.
 *
 * Insets are consumed here so no screen has to reason about the status bar or the keyboard. The
 * window is edge-to-edge, so the system does not resize it for the keyboard: the IME inset is what
 * lifts the bottom of a screen — the editor's format toolbar and the tab capsule — above it, and
 * `imePadding` after the system bars is what keeps the navigation bar from being counted twice.
 */
@Composable
fun MyNoteScreen(
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    floatingAction: (@Composable BoxScope.() -> Unit)? = null,
    fabPadding: androidx.compose.ui.unit.Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MyNoteTheme.colors.bg)
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding(),
    ) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(modifier = Modifier.fillMaxSize()) { content() }
            if (floatingAction != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = fabPadding, bottom = fabPadding),
                    content = floatingAction,
                )
            }
        }
        if (bottomBar != null) bottomBar()
    }
}

/** A screen title with an optional subtitle and trailing actions. */
@Composable
fun MyNoteScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleStyle: TextStyle = MyNoteTheme.text.screenTitle,
    actions: (@Composable () -> Unit)? = null,
) {
    val dimens = MyNoteTheme.dimens

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(dimens.gapExtraSmall),
        ) {
            Text(text = title, style = titleStyle, color = MyNoteTheme.colors.textPrimary)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MyNoteTheme.text.screenSubtitle,
                    color = MyNoteTheme.colors.textSecondary,
                )
            }
        }
        if (actions != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.gapCompact),
            ) {
                actions()
            }
        }
    }
}

/**
 * The empty-repository state.
 *
 * @param hint the smaller line below the actions that explains a side effect, matching the design's
 *   note that the starter content creates both `Inbox.md` and a `日记/` folder.
 */
@Composable
fun MyNoteEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = MyNoteIcons.folderPlus,
    hint: String? = null,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        // The design's gap for this block, which is wider than the screen rhythm because the
        // elements are centred rather than left-aligned.
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        MyNoteIconTile(
            icon = icon,
            size = 56.dp,
            tint = colors.textSecondary,
            background = colors.surfaceSunken,
            iconSize = dimens.iconExtraLarge,
        )
        Text(
            text = title,
            style = MyNoteTheme.text.sheetTitle,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MyNoteTheme.text.bodySmall,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        actions?.invoke(this)
        if (hint != null) {
            Text(
                text = hint,
                style = MyNoteTheme.text.caption,
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** An inline notice, e.g. a failed index build, shown where the affected content would be. */
@Composable
fun MyNoteNotice(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = MyNoteIcons.alertCircle,
    tint: Color = MyNoteTheme.colors.textSecondary,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceSunken)
            .padding(horizontal = dimens.gapLarge, vertical = dimens.gapMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gapCompact),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.padding(top = 1.dp).size(dimens.iconCompact),
        )
        Text(
            text = message,
            style = MyNoteTheme.text.caption,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}
