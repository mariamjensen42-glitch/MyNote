package com.cycling.mynote.ui.capture

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.cycling.mynote.core.error.DataError
import com.cycling.mynote.data.share.ShareInbox
import com.cycling.mynote.domain.repository.NoteRepository
import com.cycling.mynote.ui.mvi.MviViewModel
import com.cycling.mynote.ui.mvi.UiEffect
import com.cycling.mynote.ui.mvi.UiEvent
import com.cycling.mynote.ui.mvi.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Where the captured text can go. */
enum class CaptureTarget(val label: String, val description: String) {
    INBOX("追加到 Inbox.md", "追加到收件箱，稍后再整理"),
    NEW_NOTE("新建笔记", "用这段文字开一篇新笔记"),
}

@Immutable
data class QuickCaptureState(
    val text: String = "",
    val sourceLabel: String? = null,
    val target: CaptureTarget = CaptureTarget.INBOX,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) : UiState {
    val canSave: Boolean get() = text.isNotBlank() && !saving && !saved
}

sealed interface QuickCaptureEvent : UiEvent {
    data class TextChanged(val text: String) : QuickCaptureEvent

    data class TargetSelected(val target: CaptureTarget) : QuickCaptureEvent

    data object SaveClicked : QuickCaptureEvent

    data object CloseClicked : QuickCaptureEvent

    data object ErrorShown : QuickCaptureEvent
}

sealed interface QuickCaptureEffect : UiEffect {
    data object Close : QuickCaptureEffect

    data class OpenNote(val noteId: String) : QuickCaptureEffect

    data class ShowMessage(val message: String) : QuickCaptureEffect
}

/**
 * Quick capture: what an incoming share becomes.
 *
 * The design's sheet shows the shared text with one highlighted destination. The two destinations
 * here are the two the app can actually carry out — append to `Inbox.md`, or start a new note —
 * rather than a list of other apps, which a capture screen cannot launch into.
 *
 * The pending share is consumed on first read, so returning to this screen from the navigation
 * stack does not resurrect text that was already filed.
 */
@HiltViewModel
class QuickCaptureViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val shareInbox: ShareInbox,
) : MviViewModel<QuickCaptureState, QuickCaptureEvent, QuickCaptureEffect>(QuickCaptureState()) {

    init {
        shareInbox.pending.value?.let { pending ->
            setState { copy(text = pending.text, sourceLabel = pending.sourceLabel) }
        }
    }

    /** Called when the screen leaves, so a stale share cannot be captured twice. */
    fun consumeShare() = shareInbox.consume()

    override fun onEvent(event: QuickCaptureEvent) {
        when (event) {
            is QuickCaptureEvent.TextChanged -> setState { copy(text = event.text, saved = false) }

            is QuickCaptureEvent.TargetSelected -> setState { copy(target = event.target) }

            QuickCaptureEvent.SaveClicked -> save()

            QuickCaptureEvent.CloseClicked -> {
                consumeShare()
                sendEffect(QuickCaptureEffect.Close)
            }

            QuickCaptureEvent.ErrorShown -> setState { copy(error = null) }
        }
    }

    private fun save() {
        if (!currentState.canSave) return
        setState { copy(saving = true, error = null) }

        val text = currentState.text.trim()
        val source = currentState.sourceLabel
        val target = currentState.target

        viewModelScope.launch {
            try {
                when (target) {
                    CaptureTarget.INBOX -> {
                        val note = noteRepository.appendToInbox(text, source)
                        setState { copy(saving = false, saved = true) }
                        consumeShare()
                        sendEffect(QuickCaptureEffect.ShowMessage("已追加到 ${note.fileName}"))
                        sendEffect(QuickCaptureEffect.Close)
                    }

                    CaptureTarget.NEW_NOTE -> {
                        val title = text.lineSequence().firstOrNull().orEmpty().take(40)
                        val note = noteRepository.createNote(title)
                        noteRepository.saveNote(note.id, "# $title\n\n$text\n")
                        setState { copy(saving = false, saved = true) }
                        consumeShare()
                        sendEffect(QuickCaptureEffect.OpenNote(note.id))
                    }
                }
            } catch (e: DataError) {
                setState { copy(saving = false, error = e.message) }
                sendEffect(QuickCaptureEffect.ShowMessage(e.message ?: "保存失败"))
            }
        }
    }
}
