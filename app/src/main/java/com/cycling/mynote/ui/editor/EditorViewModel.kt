package com.cycling.mynote.ui.editor

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import com.cycling.mynote.core.error.DataError
import com.cycling.mynote.core.model.EditorSettings
import com.cycling.mynote.core.model.EditorViewMode
import com.cycling.mynote.data.attachment.NoteAttachments
import com.cycling.mynote.data.image.NoteImageLoader
import com.cycling.mynote.markdown.MarkdownImages
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

@Immutable
data class EditorState(
    val noteId: String = "",
    val loading: Boolean = true,
    val title: String = "",
    val fileName: String = "",
    val raw: String = "",
    val viewMode: EditorViewMode = EditorViewMode.SOURCE,
    val saved: Boolean = true,
    val saving: Boolean = false,
    val lastSavedAt: Long = 0L,
    val settings: EditorSettings = EditorSettings(),
    val error: String? = null,
) : UiState

sealed interface EditorEvent : UiEvent {
    data class BodyChanged(val text: String) : EditorEvent

    data class ViewModeSelected(val mode: EditorViewMode) : EditorEvent

    /** Leaves the editor, flushing an unsaved draft first. */
    data object BackRequested : EditorEvent

    data object ErrorShown : EditorEvent
}

sealed interface EditorEffect : UiEffect {
    data object NavigateBack : EditorEffect

    data class ShowMessage(val message: String) : EditorEffect
}

/**
 * The editor's state holder.
 *
 * Autosave is a debounced write started from [EditorEvent.BodyChanged] rather than a timer on the
 * screen, so it survives recomposition and is cancelled by the next keystroke instead of firing on
 * every one. Turning autosave off leaves the draft in memory and marks it unsaved; leaving the
 * editor flushes it.
 *
 * The Markdown body lives here as a plain `String` while the text field's own selection lives in
 * the composable. Keeping a cursor position in the view model would mean holding a UI type in
 * state that is otherwise pure data, and nothing outside the field needs to know where the caret is.
 *
 * The note's front matter is deliberately not modelled here: the design raises it into the source
 * itself, so tags, `pinned` and `favorite` are edited as text and re-parsed by the repository on
 * save rather than through a second copy of the metadata in this state.
 */
@HiltViewModel
class EditorViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val settingsRepository: SettingsRepository,
    private val imageLoader: NoteImageLoader,
    private val attachments: NoteAttachments,
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

            EditorEvent.ErrorShown -> setState { copy(error = null) }
        }
    }

    /**
     * Fetches a picture the note refers to.
     *
     * Returns a [Bitmap] rather than a Compose type so the view model keeps holding no UI objects;
     * the preview wraps it. The folder of the note is what makes a relative path in `![](...)`
     * resolve the way a Markdown reader resolves it.
     */
    suspend fun loadImage(reference: String): Bitmap? {
        val note = currentState.noteId
        if (note.isEmpty()) return null
        return imageLoader.load(reference, note.substringBeforeLast('/', ""))
    }

    /**
     * Files a picture picked with the toolbar's 图片 button and returns the reference the note should
     * write for it.
     *
     * The reference is relative to the note, which is what makes it render in any Markdown reader;
     * the app resolves it back the same way. Returns `null` when the picture could not be filed, so
     * the caller can fall back to inserting the syntax on its own.
     */
    suspend fun attachImage(sourceUri: String): String? {
        val note = currentState.noteId
        if (note.isEmpty()) return null

        val folder = currentState.settings.attachmentFolder.ifBlank { "attachments" }
        val saved = attachments.saveImage(sourceUri, folder) ?: return null
        val noteFolder = note.substringBeforeLast('/', "")
        return MarkdownImages.relative(saved, noteFolder)
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
                val note = noteRepository.saveNote(noteId, currentState.raw)
                setState {
                    copy(
                        saving = false,
                        saved = true,
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

    companion object {
        const val ARG_NOTE_ID = "noteId"

        /** Long enough that a burst of typing is one write, short enough to feel automatic. */
        private const val AUTOSAVE_DEBOUNCE_MS = 900L
    }
}
