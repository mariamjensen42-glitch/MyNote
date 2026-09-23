package com.cycling.mynote.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cycling.mynote.R
import com.cycling.mynote.core.model.TreeEntry
import com.cycling.mynote.core.util.RelativeTimeFormatter
import com.cycling.mynote.ui.components.MyNoteDivider
import com.cycling.mynote.ui.components.MyNoteIconTile
import com.cycling.mynote.ui.icons.MyNoteIcons
import com.cycling.mynote.ui.theme.MyNoteTheme

/** The drawer's fixed width, from the design. */
private val DRAWER_WIDTH = 316.dp

/**
 * The folder tree drawer.
 *
 * Renders as a plain panel rather than a Material navigation drawer so its width, corner treatment
 * and top offset match the design exactly, and so it can start below the status bar while the scrim
 * behind it covers it.
 */
@Composable
fun FolderTreeDrawer(
    state: LibraryState,
    onEvent: (LibraryEvent) -> Unit,
    timeFormatter: RelativeTimeFormatter,
    modifier: Modifier = Modifier,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    var filterQuery by rememberSaveable { mutableStateOf("") }

    val visibleEntries = remember(state.treeEntries, filterQuery) {
        if (filterQuery.isBlank()) {
            state.treeEntries
        } else {
            state.treeEntries.filter { entry ->
                entry.path.substringAfterLast('/').contains(filterQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = modifier
            .width(DRAWER_WIDTH)
            .fillMaxHeight()
            .padding(horizontal = dimens.gapLarge, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(dimens.gapRegular),
    ) {
        RepoHeader(state = state, onClose = { onEvent(LibraryEvent.DrawerClosed) })

        DrawerSearchField(
            value = filterQuery,
            onValueChange = { filterQuery = it },
        )

        SyncStatusRow(state = state, timeFormatter = timeFormatter)

        Text(
            text = stringResource(R.string.tree_title),
            style = MyNoteTheme.text.meta.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textSecondary,
        )

        if (visibleEntries.isEmpty()) {
            Text(
                text = if (state.treeEntries.isEmpty()) {
                    stringResource(R.string.tree_empty)
                } else {
                    stringResource(R.string.search_empty)
                },
                style = MyNoteTheme.text.caption,
                color = colors.textTertiary,
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                items(items = visibleEntries, key = { it.path }) { entry ->
                    when (entry) {
                        is TreeEntry.Folder -> TreeFolderRow(
                            entry = entry,
                            isActive = state.activeFolderPath == entry.path,
                            onToggle = { onEvent(LibraryEvent.FolderToggled(entry.path)) },
                            onSelect = { onEvent(LibraryEvent.FolderSelected(entry.path)) },
                        )

                        is TreeEntry.NoteFile -> TreeNoteRow(
                            entry = entry,
                            isSelected = state.actionNoteId == entry.noteId,
                            timeLabel = timeFormatter.forTreeRow(entry.modifiedAt),
                            onClick = { onEvent(LibraryEvent.NoteClicked(entry.noteId)) },
                            onLongClick = { onEvent(LibraryEvent.NoteLongPressed(entry.noteId)) },
                        )
                    }
                }
            }
        }

        MyNoteDivider()
        DrawerActionRow(
            icon = MyNoteIcons.folderPlus,
            label = stringResource(R.string.tree_new_folder),
            onClick = { onEvent(LibraryEvent.NewFolderRequested("")) },
        )
        DrawerActionRow(
            icon = MyNoteIcons.filePlus,
            label = stringResource(R.string.tree_new_note),
            onClick = { onEvent(LibraryEvent.NewNoteClicked) },
        )
    }
}

@Composable
private fun RepoHeader(state: LibraryState, onClose: () -> Unit) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gapMedium),
    ) {
        MyNoteIconTile(
            icon = MyNoteIcons.folderOpen,
            size = 34.dp,
            tint = colors.textSecondary,
            background = colors.surfaceSunken,
            iconSize = dimens.iconMedium,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.repo?.name.orEmpty(),
                style = MyNoteTheme.text.rowLabelStrong,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.repo?.pathLabel.orEmpty(),
                style = MyNoteTheme.text.monoMicro,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = MyNoteIcons.x,
            contentDescription = stringResource(R.string.cd_close),
            tint = colors.textSecondary,
            modifier = Modifier
                .size(dimens.iconMedium)
                .clickable(onClick = onClose),
        )
    }
}

@Composable
private fun DrawerSearchField(value: String, onValueChange: (String) -> Unit) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusMedium)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(shape)
            .background(colors.surfaceSunken),
        textStyle = MyNoteTheme.text.bodyTight.copy(color = colors.textPrimary),
        singleLine = true,
        cursorBrush = SolidColor(colors.accent),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .padding(horizontal = dimens.gapMedium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.gapCompact),
            ) {
                Icon(
                    imageVector = MyNoteIcons.search,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(dimens.iconCompact),
                )
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(R.string.tree_search_placeholder),
                            style = MyNoteTheme.text.bodyTight,
                            color = colors.textTertiary,
                        )
                    }
                    inner()
                }
            }
        },
    )
}

/**
 * The sync row.
 *
 * Reports what the app actually knows — when it last scanned and how many files changed underneath
 * it — rather than naming a sync tool the app has no relationship with.
 */
@Composable
private fun SyncStatusRow(state: LibraryState, timeFormatter: RelativeTimeFormatter) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    val label = when {
        state.isIndexing -> stringResource(R.string.settings_index_building)
        // Zero means no scan has finished yet in this session, which is not "20718 days ago".
        state.lastRefreshedAt == 0L -> "尚未刷新"
        state.externalChanges > 0 -> "上次刷新 ${timeFormatter.forTreeRow(state.lastRefreshedAt)} · " +
            "${state.externalChanges} 处外部修改"

        else -> "上次刷新 ${timeFormatter.forTreeRow(state.lastRefreshedAt)}"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            imageVector = MyNoteIcons.cloud,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(dimens.iconSmall),
        )
        Text(
            text = label,
            style = MyNoteTheme.text.meta,
            color = colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TreeFolderRow(
    entry: TreeEntry.Folder,
    isActive: Boolean,
    onToggle: () -> Unit,
    onSelect: () -> Unit,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(dimens.radiusSmall))
            .background(if (isActive) colors.accentTint else Color.Transparent)
            // The whole row is the target: tapping a folder in a tree opens and closes it, and a row
            // that only responded on its chevron read as one that could not be collapsed. Filtering
            // the note list to a folder — the drawer's other use for a folder — moved to a long
            // press, which is how the note rows beside it already reveal their extra actions.
            .then(
                if (entry.hasChildren) {
                    Modifier.combinedClickable(onClick = onToggle, onLongClick = onSelect)
                } else {
                    // Nothing to open, so the row's only meaningful action is filtering to it.
                    Modifier.clickable(onClick = onSelect)
                },
            )
            .padding(horizontal = dimens.gapCompact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gapCompact),
    ) {
        Spacer(Modifier.width((entry.depth * 16).dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.gapCompact),
        ) {
            Icon(
                imageVector = if (entry.isExpanded) MyNoteIcons.chevronDown else MyNoteIcons.chevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(dimens.iconSmall),
            )
            Icon(
                imageVector = MyNoteIcons.folder,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(dimens.iconCompact),
            )
        }
        Text(
            text = entry.name,
            style = MyNoteTheme.text.bodyTight,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = entry.noteCount.toString(),
            style = MyNoteTheme.text.meta,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun TreeNoteRow(
    entry: TreeEntry.NoteFile,
    isSelected: Boolean,
    timeLabel: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(dimens.radiusSmall))
            .background(if (isSelected) colors.surfaceSunken else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.gapCompact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gapCompact),
    ) {
        Spacer(Modifier.width((entry.depth * 16).dp))
        Spacer(Modifier.width(dimens.iconSmall))
        Icon(
            imageVector = MyNoteIcons.fileText,
            contentDescription = null,
            tint = if (isSelected) colors.textPrimary else colors.textSecondary,
            modifier = Modifier.size(dimens.iconCompact),
        )
        Text(
            text = entry.fileName,
            style = MyNoteTheme.text.bodyTight,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        )
        Text(text = timeLabel, style = MyNoteTheme.text.meta, color = colors.textTertiary)
    }
}

@Composable
private fun DrawerActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(dimens.radiusSmall))
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.gapSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gapMedium),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Text(text = label, style = MyNoteTheme.text.bodyTight, color = colors.textPrimary)
    }
}

/** The scrim's opacity, from the design. */
const val DRAWER_SCRIM_ALPHA = 0.32f

/**
 * Hosts [content] together with the sliding folder-tree drawer.
 *
 * Hand-rolled rather than [androidx.compose.material3.ModalNavigationDrawer] for two reasons the
 * design cares about: the panel starts *below* the status bar while its scrim covers it, and the
 * panel is a plain square-cornered surface rather than a shaped sheet. It also keeps the drawer
 * inside the same composable as the screen it belongs to, so its open/closed state stays in one
 * view model.
 */
@Composable
fun LibraryWithDrawer(
    state: LibraryState,
    timeFormatter: RelativeTimeFormatter,
    onEvent: (LibraryEvent) -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val colors = MyNoteTheme.colors
    val scrimAlpha by animateFloatAsState(
        targetValue = if (state.drawerOpen) DRAWER_SCRIM_ALPHA else 0f,
        label = "drawerScrim",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        content()

        if (scrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.scrim.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onEvent(LibraryEvent.DrawerClosed) },
                    ),
            )
        }

        AnimatedVisibility(
            visible = state.drawerOpen,
            enter = slideInHorizontally(initialOffsetX = { -it }),
            exit = slideOutHorizontally(targetOffsetX = { -it }),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .shadow(
                        elevation = 12.dp,
                        shape = RectangleShape,
                        ambientColor = Color.Black.copy(alpha = 0.12f),
                        spotColor = Color.Black.copy(alpha = 0.12f),
                    )
                    // The surface is painted before the insets, so the panel reaches the status bar
                    // and the bottom edge instead of stopping short of them, while the drawer's own
                    // content is still inset by both. The drawer is drawn beside `MyNoteScreen`
                    // rather than inside it, so it has to do this for itself — including the
                    // keyboard, which otherwise covers its action rows.
                    .background(MyNoteTheme.colors.surface)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .imePadding(),
            ) {
                FolderTreeDrawer(
                    state = state,
                    onEvent = onEvent,
                    timeFormatter = timeFormatter,
                )
            }
        }

        // Above the drawer as well as above the list: the failures this reports usually come *from*
        // the drawer (a folder name that is taken), and a message the panel covers is no better than
        // the silence it replaces.
        overlay()
    }
}
