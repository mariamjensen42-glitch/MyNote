package com.cycling.mynote.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.mynote.R
import com.cycling.mynote.core.model.CommandItem
import com.cycling.mynote.core.model.SearchScope
import com.cycling.mynote.ui.components.BottomTab
import com.cycling.mynote.ui.components.MatchRunsText
import com.cycling.mynote.ui.components.MyNoteAccentTag
import com.cycling.mynote.ui.components.MyNoteBadge
import com.cycling.mynote.ui.components.MyNoteBottomBar
import com.cycling.mynote.ui.components.MyNoteDivider
import com.cycling.mynote.ui.components.MyNoteScreen
import com.cycling.mynote.ui.components.MyNoteScopeFilter
import com.cycling.mynote.ui.components.MyNoteSearchInput
import com.cycling.mynote.ui.components.SearchResultTitle
import com.cycling.mynote.ui.components.ShowMoreRow
import com.cycling.mynote.ui.icons.MyNoteIcons
import com.cycling.mynote.ui.mvi.EffectCollector
import com.cycling.mynote.ui.theme.MyNoteTheme

/**
 * Search, and the `>` command palette behind the same field.
 *
 * The recent-searches strip collapses once a query exists, which is what keeps the results visible
 * without a scroll on the design's 844dp canvas.
 */
@Composable
fun SearchScreen(
    onOpenNote: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenDiary: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCapture: (String?) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    EffectCollector(viewModel.effects) { effect ->
        when (effect) {
            is SearchEffect.OpenNote -> onOpenNote(effect.noteId)
            SearchEffect.OpenLibrary -> onOpenLibrary()
            SearchEffect.OpenDiary -> onOpenDiary()
            SearchEffect.OpenSettings -> onOpenSettings()
            is SearchEffect.OpenQuickCapture -> onOpenCapture(effect.noteId)
            is SearchEffect.ShowMessage -> Unit
        }
    }

    MyNoteScreen(
        bottomBar = {
            MyNoteBottomBar(
                selected = BottomTab.SEARCH,
                onSelect = { tab ->
                    when (tab) {
                        BottomTab.LIBRARY -> onOpenLibrary()
                        BottomTab.SEARCH -> Unit
                        BottomTab.DIARY -> onOpenDiary()
                        BottomTab.SETTINGS -> onOpenSettings()
                    }
                },
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = dimens.gapExtraSmall),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.search_title),
                style = MyNoteTheme.text.screenTitle,
                color = colors.textPrimary,
            )

            MyNoteSearchInput(
                value = state.query,
                onValueChange = { viewModel.onEvent(SearchEvent.QueryChanged(it)) },
                placeholder = stringResource(R.string.search_placeholder),
                onClear = { viewModel.onEvent(SearchEvent.ClearQueryClicked) },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = dimens.gapLarge),
            verticalArrangement = Arrangement.spacedBy(dimens.gapLarge),
        ) {
            if (state.isCommandMode) {
                CommandList(
                    commands = state.visibleCommands,
                    onSelect = { viewModel.onEvent(SearchEvent.CommandSelected(it.id)) },
                )
            } else {
                if (state.query.isBlank()) {
                    RecentStrip(
                        searches = state.recentSearches,
                        tags = state.recentTags,
                        onSearch = { viewModel.onEvent(SearchEvent.RecentSelected(it)) },
                        onTag = { viewModel.onEvent(SearchEvent.TagSelected(it)) },
                    )
                }

                if (state.counts.isNotEmpty()) {
                    ScopeFilters(
                        counts = state.counts,
                        selected = state.scope,
                        onSelect = { viewModel.onEvent(SearchEvent.ScopeSelected(it)) },
                    )
                }

                ResultsList(
                    state = state,
                    onOpen = { viewModel.onEvent(SearchEvent.NoteOpened(it)) },
                    onShowAll = { viewModel.onEvent(SearchEvent.ShowAllToggled) },
                    onTag = { viewModel.onEvent(SearchEvent.TagSelected(it)) },
                )

                if (state.isEmpty) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = dimens.gapExtraLarge),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(dimens.gapCompact),
                        ) {
                            Text(
                                text = stringResource(R.string.search_empty),
                                style = MyNoteTheme.text.rowLabel,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = stringResource(R.string.search_empty_body),
                                style = MyNoteTheme.text.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }

                CommandHint()
            }
        }
    }
}

@Composable
private fun RecentStrip(
    searches: List<String>,
    tags: List<String>,
    onSearch: (String) -> Unit,
    onTag: (String) -> Unit,
) {
    val colors = MyNoteTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(MyNoteTheme.dimens.gapCompact)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.search_recent),
                style = MyNoteTheme.text.screenSubtitle,
                color = colors.textSecondary,
            )
            Box(Modifier.size(12.dp))
            Text(
                text = if (searches.isEmpty() && tags.isEmpty()) {
                    stringResource(R.string.search_recent_empty)
                } else {
                    (searches + tags.map { "#$it" }).joinToString(" · ")
                },
                style = MyNoteTheme.text.screenSubtitle,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable {
                    (searches.firstOrNull() ?: tags.firstOrNull())?.let { onSearch(it) }
                },
            )
        }
        if (tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(MyNoteTheme.dimens.gapCompact)) {
                tags.take(4).forEach { tag ->
                    MyNoteAccentTag(text = "#$tag", onClick = { onTag(tag) })
                }
            }
        }
    }
}

@Composable
private fun ScopeFilters(
    counts: Map<SearchScope, Int>,
    selected: SearchScope,
    onSelect: (SearchScope) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MyNoteTheme.dimens.gapSection),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchScope.entries.forEach { scope ->
            MyNoteScopeFilter(
                label = scope.label,
                count = counts[scope] ?: 0,
                isSelected = scope == selected,
                onClick = { onSelect(scope) },
            )
        }
    }
}

@Composable
private fun ResultsList(
    state: SearchState,
    onOpen: (String) -> Unit,
    onShowAll: () -> Unit,
    onTag: (String) -> Unit,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    if (state.visibleResults.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radiusMedium))
            .background(colors.surface),
    ) {
        state.visibleResults.forEachIndexed { index, item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(item.hit.noteId) }
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.gapCompact),
                ) {
                    Box(Modifier.weight(1f)) {
                        SearchResultTitle(titleRuns = item.hit.titleRuns)
                    }
                    MyNoteBadge(text = item.hit.scope.label)
                }

                MatchRunsText(
                    runs = item.hit.snippetRuns,
                    style = MyNoteTheme.text.snippet,
                    textColor = colors.textSecondary,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.gapCompact),
                ) {
                    Text(
                        text = "${item.hit.location} · ${item.timeLabel}",
                        style = MyNoteTheme.text.meta,
                        color = colors.textSecondary,
                    )
                    item.hit.tags.take(1).forEach { tag ->
                        MyNoteAccentTag(
                            text = "#$tag",
                            onClick = { onTag(tag) },
                        )
                    }
                }
            }
            if (index < state.visibleResults.lastIndex) MyNoteDivider()
        }
    }

    if (state.canShowMore) {
        ShowMoreRow(
            text = stringResource(R.string.search_show_all, state.results.size),
            onClick = onShowAll,
        )
    }
}

@Composable
private fun CommandList(commands: List<CommandItem>, onSelect: (CommandItem) -> Unit) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Column(verticalArrangement = Arrangement.spacedBy(dimens.gapCompact)) {
        Text(
            text = stringResource(R.string.search_command_title),
            style = MyNoteTheme.text.sectionLabel,
            color = colors.textSecondary,
        )
        if (commands.isEmpty()) {
            Text(
                text = stringResource(R.string.search_empty),
                style = MyNoteTheme.text.bodySmall,
                color = colors.textSecondary,
            )
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(dimens.gapExtraSmall),
        ) {
            items(items = commands, key = { it.id }) { command ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(dimens.radiusMedium))
                        .clickable { onSelect(command) }
                        .padding(horizontal = dimens.gapRegular, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.gapRegular),
                ) {
                    Icon(
                        imageVector = commandIcon(command.iconKey),
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(dimens.iconRegular),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = command.label,
                            style = MyNoteTheme.text.rowLabel,
                            color = colors.textPrimary,
                        )
                        command.description?.let { description ->
                            Text(
                                text = description,
                                style = MyNoteTheme.text.sectionMeta,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandHint() {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radiusMedium))
            .background(colors.surfaceSunken)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gapCompact),
    ) {
        Icon(
            imageVector = MyNoteIcons.terminal,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(dimens.iconCompact),
        )
        Text(
            text = stringResource(R.string.search_command_hint),
            style = MyNoteTheme.text.screenSubtitle,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Maps a command's stable icon key onto a glyph, so the data layer stays free of UI types. */
private fun commandIcon(key: String) = when (key) {
    "filePlus" -> MyNoteIcons.filePlus
    "notebookPen" -> MyNoteIcons.notebookPen
    "folderPlus" -> MyNoteIcons.folderPlus
    "rotateCw" -> MyNoteIcons.refreshCw
    "zap" -> MyNoteIcons.zap
    "moon" -> MyNoteIcons.moon
    else -> MyNoteIcons.settings
}
