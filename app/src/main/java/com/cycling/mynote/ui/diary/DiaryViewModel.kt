package com.cycling.mynote.ui.diary

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.cycling.mynote.core.error.DataError
import com.cycling.mynote.core.model.Note
import com.cycling.mynote.core.util.RelativeTimeFormatter
import com.cycling.mynote.domain.repository.NoteRepository
import com.cycling.mynote.ui.mvi.MviViewModel
import com.cycling.mynote.ui.mvi.UiEffect
import com.cycling.mynote.ui.mvi.UiEvent
import com.cycling.mynote.ui.mvi.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** One entry in the diary list: the note itself, plus the date parsed from its file name. */
@Immutable
data class DiaryEntry(
    val note: Note,
    val date: LocalDate,
    val timeLabel: String,
    val isToday: Boolean,
) {
    val noteId: String get() = note.id
}

/** Entries grouped under a `2025 年 3 月` heading. */
@Immutable
data class DiaryMonth(val label: String, val entries: List<DiaryEntry>)

@Immutable
data class DiaryState(
    val months: List<DiaryMonth> = emptyList(),
    val loading: Boolean = true,
    val todayId: String? = null,
    val entryCount: Int = 0,
    val busy: Boolean = false,
) : UiState {
    val isEmpty: Boolean get() = !loading && months.isEmpty()
}

sealed interface DiaryEvent : UiEvent {
    data object TodayClicked : DiaryEvent

    data class EntryClicked(val noteId: String) : DiaryEvent

    data object RefreshRequested : DiaryEvent

    data class TabSelected(val index: Int) : DiaryEvent
}

sealed interface DiaryEffect : UiEffect {
    data class OpenNote(val noteId: String) : DiaryEffect

    data object OpenLibrary : DiaryEffect

    data object OpenSearch : DiaryEffect

    data object OpenSettings : DiaryEffect

    data class ShowMessage(val message: String) : DiaryEffect
}

/**
 * The diary tab.
 *
 * A diary entry is not a special kind of note: it is a `.md` file in `日记/` named after its date.
 * That means this screen is a filtered, date-grouped view of the same index the library reads, and
 * "write today's diary" is just an open-or-create on a well-known path.
 */
@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val timeFormatter: RelativeTimeFormatter,
) : MviViewModel<DiaryState, DiaryEvent, DiaryEffect>(DiaryState()) {

    private val zone: ZoneId = ZoneId.systemDefault()

    init {
        noteRepository.observeNotes()
            .map { notes -> notes.filter { it.folder == DIARY_FOLDER } }
            .onEach { notes -> setState { withDiaryEntries(notes) } }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: DiaryEvent) {
        when (event) {
            DiaryEvent.TodayClicked -> openOrCreateToday()

            is DiaryEvent.EntryClicked -> sendEffect(DiaryEffect.OpenNote(event.noteId))

            DiaryEvent.RefreshRequested -> viewModelScope.launch {
                // The library owns the index refresh; this only re-reads what is already indexed.
                sendEffect(DiaryEffect.ShowMessage("已重新读取日记"))
            }

            is DiaryEvent.TabSelected -> Unit // handled by the navigation host
        }
    }

    private fun openOrCreateToday() {
        if (currentState.busy) return
        setState { copy(busy = true) }

        viewModelScope.launch {
            try {
                val note = noteRepository.openOrCreateDiaryEntry(LocalDate.now(zone))
                sendEffect(DiaryEffect.OpenNote(note.id))
            } catch (e: DataError) {
                sendEffect(DiaryEffect.ShowMessage(e.message ?: "无法创建今天的日记"))
            } finally {
                setState { copy(busy = false) }
            }
        }
    }

    /** Folds the repository's notes into date-grouped months, newest first. */
    private fun DiaryState.withDiaryEntries(notes: List<Note>): DiaryState {
        val today = LocalDate.now(zone)
        val entries = notes.mapNotNull { note -> note.toDiaryEntry(today) }
            .sortedByDescending { it.date }

        val months = entries
            .groupBy { YearMonth.from(it.date) }
            .toSortedMap(compareByDescending { it })
            .map { (month, monthEntries) ->
                DiaryMonth(label = MONTH_FORMAT.format(month), entries = monthEntries)
            }

        return copy(
            months = months,
            loading = false,
            entryCount = entries.size,
            todayId = entries.firstOrNull { it.isToday }?.noteId,
        )
    }

    /**
     * Reads the date from the file name, which is what makes a diary entry a diary entry.
     *
     * A note in `日记/` that is not named `yyyy-MM-dd.md` is skipped rather than guessed at: its
     * date would otherwise have to come from the file's modification time, which changes whenever
     * the note is edited.
     */
    private fun Note.toDiaryEntry(today: LocalDate): DiaryEntry? {
        val name = fileName.substringBeforeLast('.')
        val date = runCatching { LocalDate.parse(name, DATE_FORMAT) }.getOrNull() ?: return null
        return DiaryEntry(
            note = this,
            date = date,
            timeLabel = timeFormatter.forNoteRow(modifiedAt),
            isToday = date == today,
        )
    }

    /** The epoch millis for a diary date, used to sort entries that share a day. */
    private fun LocalDate.toMillis(): Long = atStartOfDay(zone).toInstant().toEpochMilli()

    private companion object {
        const val DIARY_FOLDER = "日记"
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy 年 M 月")
    }
}
