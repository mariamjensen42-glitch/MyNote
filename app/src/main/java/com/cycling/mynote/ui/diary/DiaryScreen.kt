package com.cycling.mynote.ui.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.mynote.R
import com.cycling.mynote.ui.components.BottomTab
import com.cycling.mynote.ui.components.MyNoteBottomBar
import com.cycling.mynote.ui.components.MyNoteDivider
import com.cycling.mynote.ui.components.MyNoteEmptyState
import com.cycling.mynote.ui.components.MyNoteFab
import com.cycling.mynote.ui.components.MyNotePrimaryButton
import com.cycling.mynote.ui.components.MyNoteScreen
import com.cycling.mynote.ui.components.MyNoteScreenHeader
import com.cycling.mynote.ui.components.NoteRow
import com.cycling.mynote.ui.components.SectionLabel
import com.cycling.mynote.ui.icons.MyNoteIcons
import com.cycling.mynote.ui.mvi.EffectCollector
import com.cycling.mynote.ui.theme.MyNoteTheme

/**
 * The diary tab.
 *
 * A date-grouped view of the `日记/` folder with one prominent action — open today's entry, creating
 * it if it does not exist yet. Every entry is an ordinary `.md` file, which the empty state says
 * outright so the folder layout is not a surprise.
 */
@Composable
fun DiaryScreen(
    onOpenNote: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: DiaryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    EffectCollector(viewModel.effects) { effect ->
        when (effect) {
            is DiaryEffect.OpenNote -> onOpenNote(effect.noteId)
            DiaryEffect.OpenLibrary -> onOpenLibrary()
            DiaryEffect.OpenSearch -> onOpenSearch()
            DiaryEffect.OpenSettings -> onOpenSettings()
            is DiaryEffect.ShowMessage -> Unit
        }
    }

    MyNoteScreen(
        bottomBar = {
            MyNoteBottomBar(
                selected = BottomTab.DIARY,
                onSelect = { tab ->
                    when (tab) {
                        BottomTab.LIBRARY -> onOpenLibrary()
                        BottomTab.SEARCH -> onOpenSearch()
                        BottomTab.DIARY -> Unit
                        BottomTab.SETTINGS -> onOpenSettings()
                    }
                },
            )
        },
        floatingAction = {
            MyNoteFab(
                onClick = { viewModel.onEvent(DiaryEvent.TodayClicked) },
                icon = MyNoteIcons.notebookPen,
                contentDescription = stringResource(R.string.diary_today),
            )
        },
    ) {
        MyNoteScreenHeader(
            title = stringResource(R.string.diary_title),
            subtitle = stringResource(R.string.diary_subtitle, state.entryCount),
            modifier = Modifier.padding(
                start = 20.dp,
                end = 20.dp,
                top = dimens.headerPaddingTop,
                bottom = dimens.gapSection,
            ),
        )

        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            MyNotePrimaryButton(
                label = if (state.todayId != null) {
                    stringResource(R.string.diary_open_today)
                } else {
                    stringResource(R.string.diary_today)
                },
                onClick = { viewModel.onEvent(DiaryEvent.TodayClicked) },
                enabled = !state.busy,
                leadingIcon = MyNoteIcons.calendarPlus,
            )
        }

        if (state.isEmpty) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                MyNoteEmptyState(
                    title = stringResource(R.string.diary_empty_title),
                    body = stringResource(R.string.diary_empty_body),
                    icon = MyNoteIcons.calendarDays,
                )
            }
            return@MyNoteScreen
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = dimens.gapSection),
        ) {
            state.months.forEach { month ->
                item(key = "month-${month.label}") {
                    SectionLabel(
                        text = month.label,
                        modifier = Modifier.padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = dimens.gapRegular,
                            bottom = dimens.gapCompact,
                        ),
                    )
                }
                items(items = month.entries, key = { it.noteId }) { entry ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        NoteRow(
                            note = entry.note,
                            timeLabel = entry.timeLabel,
                            highlighted = entry.isToday,
                            onClick = { viewModel.onEvent(DiaryEvent.EntryClicked(entry.noteId)) },
                        )
                        MyNoteDivider()                    }
                }
            }
            item(key = "tail-spacer") {
                Box(Modifier.padding(bottom = 24.dp))
            }
        }
    }
}
