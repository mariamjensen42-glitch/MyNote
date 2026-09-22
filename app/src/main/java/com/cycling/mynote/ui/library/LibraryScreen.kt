package com.cycling.mynote.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.mynote.R
import com.cycling.mynote.core.model.NoteFilter
import com.cycling.mynote.core.model.NoteSort
import com.cycling.mynote.ui.components.BottomTab
import com.cycling.mynote.ui.components.MyNoteBottomBar
import com.cycling.mynote.ui.components.MyNoteChipRow
import com.cycling.mynote.ui.components.MyNoteDivider
import com.cycling.mynote.ui.components.MyNoteEmptyState
import com.cycling.mynote.ui.components.MyNoteFab
import com.cycling.mynote.ui.components.MyNoteFilterChip
import com.cycling.mynote.ui.components.MyNoteIconButton
import com.cycling.mynote.ui.components.MyNotePrimaryButton
import com.cycling.mynote.ui.components.MyNoteScreen
import com.cycling.mynote.ui.components.MyNoteScreenHeader
import com.cycling.mynote.ui.components.MyNoteSearchAffordance
import com.cycling.mynote.ui.components.MyNoteSecondaryButton
import com.cycling.mynote.ui.components.NoteActionStrip
import com.cycling.mynote.ui.components.NoteRow
import com.cycling.mynote.ui.icons.MyNoteIcons
import com.cycling.mynote.ui.mvi.EffectCollector
import com.cycling.mynote.ui.theme.MyNoteTheme

/**
 * The note library: every Markdown file in the granted folder, newest first.
 *
 * The list scrolls independently of the header and chip row, which is what keeps the search
 * affordance reachable with a thumb while a long note list scrolls under it.
 */
@Composable
fun LibraryScreen(
    onOpenNote: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDiary: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCapture: (String?) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    EffectCollector(viewModel.effects) { effect ->
        when (effect) {
            is LibraryEffect.OpenNote -> onOpenNote(effect.noteId)
            LibraryEffect.OpenSearch -> onOpenSearch()
            LibraryEffect.OpenDiary -> onOpenDiary()
            LibraryEffect.OpenSettings -> onOpenSettings()
            is LibraryEffect.OpenQuickCapture -> onOpenCapture(effect.noteId)
            is LibraryEffect.ShowMessage -> Unit
        }
    }

    LibraryWithDrawer(
        state = state,
        timeFormatter = viewModel.relativeTime,
        onEvent = viewModel::onEvent,
    ) {
        MyNoteScreen(
            bottomBar = {
                MyNoteBottomBar(
                    selected = BottomTab.LIBRARY,
                    onSelect = { tab -> handleTab(tab, onOpenSearch, onOpenDiary, onOpenSettings) },
                )
            },
            floatingAction = {
                NewNoteFabArea(
                    menuOpen = state.fabMenuOpen,
                    onFabClick = { viewModel.onEvent(LibraryEvent.NewNoteClicked) },
                    onFabLongPress = { viewModel.onEvent(LibraryEvent.FabLongPressed) },
                    onNewDiary = {
                        viewModel.onEvent(LibraryEvent.FabMenuDismissed)
                        viewModel.onEvent(LibraryEvent.NewDiaryClicked)
                    },
                    onQuickCapture = {
                        viewModel.onEvent(LibraryEvent.FabMenuDismissed)
                        viewModel.onEvent(LibraryEvent.QuickCaptureClicked)
                    },
                )
            },
        ) {
        LibraryHeader(
            title = stringResource(R.string.library_title),
            subtitle = state.repo?.let {
                stringResource(R.string.library_subtitle, it.noteCount)
            } ?: stringResource(R.string.library_subtitle, 0),
            onOpenTree = { viewModel.onEvent(LibraryEvent.DrawerOpened) },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.gapMedium),
        ) {
            MyNoteSearchAffordance(
                onClick = onOpenSearch,
                modifier = Modifier.weight(1f),
                placeholder = stringResource(R.string.library_search_placeholder),
            )
            SortButton(
                sort = state.sort,
                onClick = { viewModel.onEvent(LibraryEvent.SortToggled) },
            )
        }

        MyNoteChipRow(
            // The screen's content column has no vertical arrangement, so each block owns its own
            // top gap. Without this the chips sat directly against the search field.
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = dimens.gapSection),
            spacing = dimens.gapSection,
        ) {
            NoteFilter.entries.forEach { filter ->
                MyNoteFilterChip(
                    label = filter.label,
                    isSelected = state.filter == filter,
                    onClick = { viewModel.onEvent(LibraryEvent.FilterSelected(filter)) },
                )
            }
        }

        // Nothing to sort when the repository is empty, and a `共 0 篇` header above the empty
        // state reads as a bug.
        if (!state.isEmptyRepo) {
            ListHeader(
                sort = state.sort,
                count = state.notes.size,
                modifier = Modifier.padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = dimens.gapSection,
                    bottom = dimens.gapMedium,
                ),
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                state.isEmptyRepo -> Box(
                    modifier = Modifier.fillMaxSize(),
                    // The design centres the empty state in the space below the header rather than
                    // stacking it under it.
                    contentAlignment = Alignment.Center,
                ) {
                    MyNoteEmptyState(
                        title = stringResource(R.string.library_empty_title),
                        body = stringResource(R.string.library_empty_body),
                        hint = stringResource(R.string.library_empty_hint),
                        actions = {
                            MyNotePrimaryButton(
                                label = stringResource(R.string.library_empty_primary),
                                onClick = { viewModel.onEvent(LibraryEvent.CreateFirstNoteClicked) },
                            )
                            MyNoteSecondaryButton(
                                label = stringResource(R.string.library_empty_secondary),
                                onClick = { viewModel.onEvent(LibraryEvent.CreateSampleNoteClicked) },
                            )
                        },
                    )
                }

                state.isFilteredEmpty -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.search_empty),
                        style = MyNoteTheme.text.bodySmall,
                        color = colors.textSecondary,
                    )
                }

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = state.notes, key = { it.note.id }) { item ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            NoteRow(
                                note = item.note,
                                timeLabel = item.timeLabel,
                                onClick = { viewModel.onEvent(LibraryEvent.NoteClicked(item.note.id)) },
                                onLongClick = {
                                    viewModel.onEvent(LibraryEvent.NoteLongPressed(item.note.id))
                                },
                                highlighted = state.actionNoteId == item.note.id,
                            )
                            if (state.actionNoteId == item.note.id) {
                                NoteActionStrip(
                                    isPinned = item.note.isPinned,
                                    onPin = { viewModel.onEvent(LibraryEvent.PinnedToggled(item.note.id)) },
                                    onRename = {
                                        viewModel.onEvent(LibraryEvent.RenameRequested(item.note.id))
                                    },
                                    onMove = { viewModel.onEvent(LibraryEvent.MoveRequested(item.note.id)) },
                                    onDelete = {
                                        viewModel.onEvent(LibraryEvent.DeleteRequested(item.note.id))
                                    },
                                    modifier = Modifier.padding(
                                        horizontal = dimens.rowPaddingHorizontal,
                                        vertical = dimens.gapMedium,
                                    ),
                                )
                            }
                            MyNoteDivider()
                        }
                    }
                }
            }
        }

        if (state.message != null) {
            state.message?.let { message ->
                Text(
                    text = message,
                    style = MyNoteTheme.text.caption,
                    color = colors.danger,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = dimens.gapCompact),
                )
            }
        }
    }

    LibraryDialogs(
        state = state,
        onEvent = viewModel::onEvent,
    )
    }
}

/** The title row, kept separate so the sticky header reads as one unit. */
@Composable
private fun LibraryHeader(
    title: String,
    subtitle: String,
    onOpenTree: () -> Unit,
) {
    val dimens = MyNoteTheme.dimens

    MyNoteScreenHeader(
        title = title,
        subtitle = subtitle,
        titleStyle = MyNoteTheme.text.screenTitleMedium,
        modifier = Modifier.padding(
            start = 20.dp,
            end = 20.dp,
            top = dimens.gapExtraSmall,
            bottom = dimens.gapSection,
        ),
        actions = {
            MyNoteIconButton(
                icon = MyNoteIcons.folderTree,
                contentDescription = stringResource(R.string.cd_folder_tree),
                onClick = onOpenTree,
                buttonSize = dimens.iconButtonSizeLarge,
            )
        },
    )
}

/** The sort square that cycles the list order, matching the design's `arrow-up-down` control. */
@Composable
private fun SortButton(sort: NoteSort, onClick: () -> Unit) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Box(
        modifier = Modifier
            .size(dimens.controlHeight)
            .clip(RoundedCornerShape(dimens.radiusMedium))
            .background(colors.surfaceSunken)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MyNoteIcons.arrowUpDown,
            contentDescription = sort.label,
            tint = colors.textSecondary,
            modifier = Modifier.size(dimens.iconMedium),
        )
    }
}

/** The `最近更新 / 共 N 篇` row that names the current ordering. */
@Composable
private fun ListHeader(sort: NoteSort, count: Int, modifier: Modifier = Modifier) {
    val colors = MyNoteTheme.colors

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = sort.label, style = MyNoteTheme.text.sectionTitle, color = colors.textPrimary)
        Text(
            text = stringResource(R.string.library_total, count),
            style = MyNoteTheme.text.meta,
            color = colors.textSecondary,
        )
    }
}

/** The FAB plus the long-press menu of alternative creations. */
@Composable
private fun NewNoteFabArea(
    menuOpen: Boolean,
    onFabClick: () -> Unit,
    onFabLongPress: () -> Unit,
    onNewDiary: () -> Unit,
    onQuickCapture: () -> Unit,
) {
    val dimens = MyNoteTheme.dimens

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(dimens.gapCompact),
    ) {
        if (menuOpen) {
            FabMenuItem(
                icon = MyNoteIcons.notebookPen,
                label = stringResource(R.string.action_new_diary),
                onClick = onNewDiary,
            )
            FabMenuItem(
                icon = MyNoteIcons.zap,
                label = stringResource(R.string.action_quick_capture),
                onClick = onQuickCapture,
            )
        }
        MyNoteFab(
            onClick = onFabClick,
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(onLongPress = { onFabLongPress() })
            },
        )
    }
}

@Composable
private fun FabMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(dimens.radiusMedium))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .height(dimens.iconButtonSize)
            .padding(horizontal = dimens.gapRegular),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gapCompact),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(dimens.iconCompact),
        )
        Text(text = label, style = MyNoteTheme.text.rowAction, color = colors.textPrimary)
    }
}

/** Routes a bottom-bar tap to the navigation callbacks this screen was given. */
private fun handleTab(
    tab: BottomTab,
    onOpenSearch: () -> Unit,
    onOpenDiary: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    when (tab) {
        BottomTab.LIBRARY -> Unit
        BottomTab.SEARCH -> onOpenSearch()
        BottomTab.DIARY -> onOpenDiary()
        BottomTab.SETTINGS -> onOpenSettings()
    }
}
