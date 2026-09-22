package com.cycling.mynote.ui.editor

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.cycling.mynote.core.error.DataError
import com.cycling.mynote.core.model.EditorSettings
import com.cycling.mynote.core.model.EditorViewMode
import com.cycling.mynote.core.model.FrontMatter
import com.cycling.mynote.core.model.NoteStats
import com.cycling.mynote.data.markdown.FrontMatterParser
import com.cycling.mynote.data.markdown.MarkdownParser
import com.cycling.mynote.data.markdown.NoteTextExtractor
import com.cycling.mynote.domain.repository.NoteRepository
import com.cycling.mynote.domain.repository.SettingsRepository
import com.cycling.mynote.ui.mvi.MviViewModel
import com.cycling.mynote.ui.mvi.UiEffect
import com.cycling.mynote.ui.mvi.UiEvent
import com.cycling.mynote.ui.mvi.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** What the editor's `⋯` menu can ask for. */
sealed interface EditorDialog {
    data object Rename : EditorDialog

    data object Delete : EditorDialog

    data object AddTag : EditorDialog
}

@Immutable
data class EditorState(
    val noteId: String = "",
    val loading: Boolean = true,
    val title: String = "",
    val fileName: String = "",
    val raw: String = "",
    val frontMatter: FrontMatter = FrontMatter.EMPTY,
    val stats: NoteStats = NoteStats(0, 0),
    val viewMode: EditorViewMode = EditorViewMode.SOURCE,
    val saved: Boolean = true,
    val saving: Boolean = false,
    val lastSavedAt: Long = 0L,
    val settings: EditorSettings = EditorSettings(),
    val moreMenuOpen: Boolean = false,
    val dialog: EditorDialog? = null,
    val error: String? = null,
    val availableTags: List<String> = emptyList(),
) : UiState

sealed interface EditorEvent : UiEvent {
    data class BodyChanged(val text: String) : EditorEvent

    data class ViewModeSelected(val mode: EditorViewMode) : EditorEvent

    /** Leaves the editor, flushing an unsaved draft first. */
    data object BackRequested : EditorEvent

    data object SaveRequested : EditorEvent

    data object PinToggled : EditorEvent

    data object FavoriteToggled : EditorEvent

    data object MoreMenuToggled : EditorEvent

    data object MoreMenuDismissed : EditorEvent

    /** Copies the note's prose, with Markdown syntax stripped, for pasting into a chat or email. */
    data object CopyPlainTextRequested : EditorEvent

    data object RenameRequested : EditorEvent

    data class RenameConfirmed(val newTitle: String) : EditorEvent

    data object DeleteRequested : EditorEvent

    data object DeleteConfirmed : EditorEvent

    data object AddTagRequested : EditorEvent

    data class TagConfirmed(val tag: String) : EditorEvent

    data class TagRemoved(val tag: String) : EditorEvent

    data object DialogDismissed : EditorEvent

    data object ErrorShown : EditorEvent
}

sealed interface EditorEffect : UiEffect {
    data object NavigateBack : EditorEffect

    /** The screen owns the clipboard; the view model only decides what should go on it. */
    data class CopyToClipboard(val text: String) : EditorEffect

    data class ShowMessage(val message: String) : EditorEffect
}

/**
 * The editor's state holder.
 *
 * Autosave is a debounced write started from [EditorEvent.BodyChanged] rather than a timer on the
 * screen, so it survives recomposition and is cancelled by the next keystroke instead of firing on
 * every one. Turning autosave off leaves the draft in memory and marks it unsaved; the explicit
 * save and the back navigation both flush it.
 *
 * The Markdown body lives here as a plain `String` while the text field's own selection lives in
 * the composable. Keeping a cursor position in the view model would mean holding a UI type in
 * state that is otherwise pure data, and nothing outside the field needs to know where the caret is.
 */
@HiltViewModel
class EditorViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val settingsRepository: SettingsRepository,
    private val savedStateHandle: SavedStateHandle,
) : MviViewModel<EditorState, EditorEvent, EditorEffect>(EditorState()) {

    private var autosaveJob: Job? = null
    private var loadedNoteId: String? = null

    init {
        val noteId: String? = savedStateHandle[ARG_NOTE_ID]
        if (noteId == null) {
            setState { copy(loading = false, error = "缺少笔记参数") }
        } else {
            load(noteId)
        }

        settingsRepository.observeSettings()
            .onEach { settings ->
                val previous = currentState.settings
                setState { copy(settings = settings) }
                // Turning autosave on should immediately persist a draft that was waiting for it.
                if (!previous.autoSave && settings.autoSave && !currentState.saved) {
                    save()
                }
            }
            .launchIn(viewModelScope)

        noteRepository.observeAllTags()
            .onEach { tags -> setState { copy(availableTags = tags) } }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: EditorEvent) {
        when (event) {
            is EditorEvent.BodyChanged -> {
                setState { copy(raw = event.text, saved = false) }
                scheduleAutosave()
            }

            is EditorEvent.ViewModeSelected -> setState { copy(viewMode = event.mode) }

            EditorEvent.BackRequested -> {
                autosaveJob?.cancel()
                if (currentState.saved) {
                    sendEffect(EditorEffect.NavigateBack)
                } else {
                    save(navigateAfter = true)
                }
            }

            EditorEvent.SaveRequested -> save()

            EditorEvent.PinToggled -> toggleFrontMatter { it.copy(isPinned = !it.isPinned) }

            EditorEvent.FavoriteToggled -> toggleFrontMatter { it.copy(isFavorite = !it.isFavorite) }

            EditorEvent.MoreMenuToggled -> setState { copy(moreMenuOpen = !moreMenuOpen) }

            EditorEvent.MoreMenuDismissed -> setState { copy(moreMenuOpen = false) }

            EditorEvent.CopyPlainTextRequested -> {
                setState { copy(moreMenuOpen = false) }
                val text = MarkdownParser
                    .toPlainText(MarkdownParser.parse(currentState.raw))
                    .trim()
                if (text.isEmpty()) {
                    sendEffect(EditorEffect.ShowMessage("这篇笔记还没有正文"))
                } else {
                    sendEffect(EditorEffect.CopyToClipboard(text))
                }
            }

            EditorEvent.RenameRequested -> setState {
                copy(dialog = EditorDialog.Rename, moreMenuOpen = false)
            }

            is EditorEvent.RenameConfirmed -> rename(event.newTitle)

            EditorEvent.DeleteRequested -> setState {
                copy(dialog = EditorDialog.Delete, moreMenuOpen = false)
            }

            EditorEvent.DeleteConfirmed -> delete()

            EditorEvent.AddTagRequested -> setState { copy(dialog = EditorDialog.AddTag) }

            is EditorEvent.TagConfirmed -> {
                val tag = event.tag.trim()
                if (tag.isNotEmpty()) {
                    toggleFrontMatter {
                        it.copy(tags = (it.tags + tag).distinct())
                    }
                }
                setState { copy(dialog = null) }
            }

            is EditorEvent.TagRemoved -> toggleFrontMatter {
                it.copy(tags = it.tags - event.tag)
            }

            EditorEvent.DialogDismissed -> setState { copy(dialog = null) }

            EditorEvent.ErrorShown -> setState { copy(error = null) }
        }
    }

    private fun load(noteId: String) {
        if (loadedNoteId == noteId) return
        loadedNoteId = noteId

        viewModelScope.launch {
            try {
                val document = noteRepository.openNote(noteId)
                setState {
                    copy(
                        noteId = noteId,
                        loading = false,
                        title = document.note.title,
                        fileName = document.note.fileName,
                        raw = document.raw,
                        frontMatter = document.frontMatter,
                        stats = document.stats,
                        saved = true,
                        lastSavedAt = document.note.modifiedAt,
                    )
                }
            } catch (e: DataError) {
                setState { copy(loading = false, error = e.message) }
                sendEffect(EditorEffect.ShowMessage(e.message ?: "打开笔记失败"))
            }
        }
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        if (!currentState.settings.autoSave) return
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MS)
            save()
        }
    }

    private fun save(navigateAfter: Boolean = false) {
        val noteId = currentState.noteId
        if (noteId.isEmpty() || currentState.saving) return

        autosaveJob?.cancel()
        setState { copy(saving = true, error = null) }

        viewModelScope.launch {
            try {
                val raw = currentState.raw
                val note = noteRepository.saveNote(noteId, raw)
                val parsed = FrontMatterParser.parse(raw)
                setState {
                    copy(
                        saving = false,
                        saved = true,
                        frontMatter = parsed.frontMatter,
                        stats = NoteTextExtractor.extract(raw, fileName, parsed).stats,
                        lastSavedAt = note.modifiedAt,
                    )
                }
                if (navigateAfter) sendEffect(EditorEffect.NavigateBack)
            } catch (e: DataError) {
                setState { copy(saving = false, saved = false, error = e.message) }
                sendEffect(EditorEffect.ShowMessage(e.message ?: "保存失败"))
            }
        }
    }

    /** Rewrites only the front-matter block, leaving the body byte-identical. */
    private fun toggleFrontMatter(transform: (FrontMatter) -> FrontMatter) {
        val updated = transform(currentState.frontMatter)
        val raw = currentState.raw
        val body = raw.substring(
            FrontMatterParser.parse(raw).bodyStartOffset.coerceIn(0, raw.length),
        )
        val rewritten = FrontMatterParser.render(updated) + body
        setState { copy(raw = rewritten, frontMatter = updated, saved = false) }
        scheduleAutosave()
    }

    private fun rename(newTitle: String) {
        val title = newTitle.trim()
        if (title.isEmpty()) {
            setState { copy(dialog = null) }
            return
        }

        viewModelScope.launch {
            try {
                // Flush the draft under the old name first, then move the file, so the rename
                // cannot race the autosave into recreating the note it just renamed.
                if (!currentState.saved) noteRepository.saveNote(currentState.noteId, currentState.raw)
                val note = noteRepository.renameNote(currentState.noteId, title)
                loadedNoteId = note.id
                setState {
                    copy(
                        dialog = null,
                        noteId = note.id,
                        title = note.title,
                        fileName = note.fileName,
                        saved = true,
                    )
                }
            } catch (e: DataError) {
                setState { copy(dialog = null) }
                sendEffect(EditorEffect.ShowMessage(e.message ?: "重命名失败"))
            }
        }
    }

    private fun delete() {
        viewModelScope.launch {
            try {
                autosaveJob?.cancel()
                noteRepository.deleteNote(currentState.noteId)
                setState { copy(dialog = null) }
                sendEffect(EditorEffect.NavigateBack)
            } catch (e: DataError) {
                setState { copy(dialog = null) }
                sendEffect(EditorEffect.ShowMessage(e.message ?: "删除失败"))
            }
        }
    }

    companion object {
        const val ARG_NOTE_ID = "noteId"

        /** Long enough that a burst of typing is one write, short enough to feel automatic. */
        private const val AUTOSAVE_DEBOUNCE_MS = 900L
    }
}
