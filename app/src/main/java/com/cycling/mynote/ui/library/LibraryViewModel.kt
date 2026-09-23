package com.cycling.mynote.ui.library

import androidx.compose.runtime.Immutable
import com.cycling.mynote.core.model.FolderNode
import com.cycling.mynote.core.model.IndexState
import com.cycling.mynote.core.model.IndexStatus
import com.cycling.mynote.core.model.Note
import com.cycling.mynote.core.model.NoteFilter
import com.cycling.mynote.core.model.NoteSort
import com.cycling.mynote.core.model.RepoInfo
import com.cycling.mynote.core.model.TreeEntry
import com.cycling.mynote.core.model.TreeEntryFactory
import com.cycling.mynote.core.util.RelativeTimeFormatter
import com.cycling.mynote.data.index.IndexOutcome
import com.cycling.mynote.domain.repository.NoteQuery
import com.cycling.mynote.domain.repository.NoteRepository
import com.cycling.mynote.domain.repository.RepoRepository
import com.cycling.mynote.ui.mvi.MviViewModel
import com.cycling.mynote.ui.mvi.UiEffect
import com.cycling.mynote.ui.mvi.UiEvent
import com.cycling.mynote.ui.mvi.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A note plus its rendered relative timestamp, so the row does not reformat on every frame. */
@Immutable
data class NoteListItem(val note: Note, val timeLabel: String)

/** A dialog the library can be showing; modelled as one value so two can never be open at once. */
sealed interface LibraryDialog {
    data class Rename(val noteId: String, val currentTitle: String) : LibraryDialog

    data class Delete(val noteId: String, val title: String) : LibraryDialog

    data class NewFolder(val parentPath: String) : LibraryDialog

    data class Move(val noteId: String, val title: String, val folders: List<String>) : LibraryDialog
}

@Immutable
data class LibraryState(
    val repo: RepoInfo? = null,
    val notes: List<NoteListItem> = emptyList(),
    val filter: NoteFilter = NoteFilter.ALL,
    val sort: NoteSort = NoteSort.UPDATED_DESC,
    val indexStatus: IndexStatus = IndexStatus(),
    val externalChanges: Int = 0,
    val lastRefreshedAt: Long = 0L,
    val loading: Boolean = true,
    val activeFolderPath: String? = null,
    val drawerOpen: Boolean = false,
    val treeEntries: List<TreeEntry> = emptyList(),
    val expandedFolders: Set<String> = emptySet(),
    val actionNoteId: String? = null,
    val dialog: LibraryDialog? = null,
    val fabMenuOpen: Boolean = false,
    val message: String? = null,
) : UiState {
    val isEmptyRepo: Boolean get() = !loading && notes.isEmpty() && filter == NoteFilter.ALL &&
        activeFolderPath == null && (repo?.noteCount ?: 0) == 0

    val isFilteredEmpty: Boolean get() = !loading && notes.isEmpty() && !isEmptyRepo

    val isIndexing: Boolean get() = indexStatus.state == IndexState.BUILDING
}

sealed interface LibraryEvent : UiEvent {
    data class FilterSelected(val filter: NoteFilter) : LibraryEvent

    data object SortToggled : LibraryEvent

    data class NoteClicked(val noteId: String) : LibraryEvent

    data class NoteLongPressed(val noteId: String) : LibraryEvent

    data object ActionsDismissed : LibraryEvent

    data class PinnedToggled(val noteId: String) : LibraryEvent

    data class FavoriteToggled(val noteId: String) : LibraryEvent

    data object NewNoteClicked : LibraryEvent

    data object NewDiaryClicked : LibraryEvent

    data object QuickCaptureClicked : LibraryEvent

    data object FabLongPressed : LibraryEvent

    data object FabMenuDismissed : LibraryEvent

    data object DrawerOpened : LibraryEvent

    data object DrawerClosed : LibraryEvent

    data class FolderToggled(val path: String) : LibraryEvent

    /** `null` means "all folders" — the drawer's root row. */
    data class FolderSelected(val path: String?) : LibraryEvent

    data class RenameRequested(val noteId: String) : LibraryEvent

    data class RenameConfirmed(val noteId: String, val newTitle: String) : LibraryEvent

    data class MoveRequested(val noteId: String) : LibraryEvent

    data class MoveConfirmed(val noteId: String, val targetFolder: String) : LibraryEvent

    data class DeleteRequested(val noteId: String) : LibraryEvent

    data class DeleteConfirmed(val noteId: String) : LibraryEvent

    data class NewFolderRequested(val parentPath: String) : LibraryEvent

    data class NewFolderConfirmed(val parentPath: String, val name: String) : LibraryEvent

    data class DialogDismissed(val dialog: LibraryDialog) : LibraryEvent

    data object RefreshRequested : LibraryEvent

    data object RebuildIndexRequested : LibraryEvent

    data object CreateFirstNoteClicked : LibraryEvent

    data object CreateSampleNoteClicked : LibraryEvent

    /** A message the screen is showing; the screen dismisses it when its time is up. */
    data class MessageShown(val message: String) : LibraryEvent

    data object MessageDismissed : LibraryEvent

    data class TabSelected(val tab: Int) : LibraryEvent
}

sealed interface LibraryEffect : UiEffect {
    data class OpenNote(val noteId: String) : LibraryEffect

    data object OpenSearch : LibraryEffect

    data object OpenDiary : LibraryEffect

    data object OpenSettings : LibraryEffect

    /** `noteId` is null for a blank capture; otherwise the capture targets that note's folder. */
    data class OpenQuickCapture(val noteId: String?) : LibraryEffect

    data class ShowMessage(val message: String) : LibraryEffect
}

/**
 * The library screen's state holder.
 *
 * The note list, the folder tree and the repository header are combined into one state object
 * rather than exposed as three streams, so the drawer's "external changes" count and the list it
 * sits next to can never come from two different scans.
 *
 * Every mutation goes through the repository and then relies on the index flow to update the list,
 * rather than optimistically editing the local list: the `.md` file is the source of truth, and a
 * list that disagrees with it is exactly the bug this app exists to avoid.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val repoRepository: RepoRepository,
    private val timeFormatter: RelativeTimeFormatter,
    private val intents: LibraryIntents,
) : MviViewModel<LibraryState, LibraryEvent, LibraryEffect>(LibraryState()) {

    /**
     * The timing formatter, exposed so the folder-tree drawer renders its trailing column in the
     * same style as the note rows it sits beside without a second instance being injected.
     */
    val relativeTime: RelativeTimeFormatter get() = timeFormatter

    /**
     * Which folders the drawer has open.
     *
     * A flow rather than a plain state field because the drawer's rows are *derived* from it: when
     * this only lived in the state, toggling a folder updated the set but nothing re-flattened the
     * rows that are built from it, so the tree could not be collapsed at all.
     */
    private val expandedFolders = MutableStateFlow<Set<String>>(emptySet())

    /** The message currently on screen, so a second one replaces the first instead of racing it. */
    private var messageJob: Job? = null

    init {
        combine(
            repoRepository.observeRepo(),
            noteRepository.observeNotes(),
            repoRepository.observeFolderTree(),
            repoRepository.observeIndexStatus(),
            expandedFolders,
        ) { repo, notes, tree, indexStatus, expanded ->
            reduce(repo = repo, notes = notes, tree = tree, indexStatus = indexStatus, expanded = expanded)
        }
            .launchIn(viewModelScope)

        // Requests from other screens — the command palette's "新建文件夹" — land in the same dialog
        // the drawer opens, so a folder is always named and always created in the repository root.
        intents.requests
            .onEach { intent ->
                when (intent) {
                    LibraryIntent.NEW_FOLDER ->
                        setState { copy(dialog = LibraryDialog.NewFolder("")) }
                }
            }
            .launchIn(viewModelScope)

        // An automatic refresh on open, recorded through the same path as a manual one so the
        // drawer's "上次刷新" row reports it.
        refresh(force = false)
    }

    /**
     * Folds one frame's worth of repository streams into the screen state.
     *
     * The note list, the folder tree and the header are derived from the same emission rather than
     * from three independent collectors, so the drawer's counts can never describe a different scan
     * than the list next to them.
     */
    private fun reduce(
        repo: RepoInfo?,
        notes: List<Note>,
        tree: FolderNode,
        indexStatus: IndexStatus,
        expanded: Set<String>,
    ) {
        setState {
            val visible = notes.filter { it.matchesFilter(filter, activeFolderPath) }
                .sortedWith(sort.comparator())
                .map { NoteListItem(it, timeFormatter.forNoteRow(it.modifiedAt)) }

            copy(
                repo = repo,
                loading = false,
                indexStatus = indexStatus,
                notes = visible,
                expandedFolders = expanded,
                treeEntries = TreeEntryFactory.flatten(tree, notes, expanded),
            )
        }
    }

    override fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.FilterSelected -> setState {
                copy(
                    filter = event.filter,
                    // 全部 means everything. Without this, a folder picked in the drawer kept
                    // filtering the list even after switching chips, and the drawer — the only place
                    // that could clear it — offered no way back.
                    activeFolderPath = if (event.filter == NoteFilter.ALL) null else activeFolderPath,
                    actionNoteId = null,
                )
            }

            LibraryEvent.SortToggled -> setState { copy(sort = sort.next()) }

            is LibraryEvent.NoteClicked ->
                if (currentState.actionNoteId == event.noteId) {
                    setState { copy(actionNoteId = null) }
                } else {
                    sendEffect(LibraryEffect.OpenNote(event.noteId))
                }

            is LibraryEvent.NoteLongPressed -> setState { copy(actionNoteId = event.noteId) }

            LibraryEvent.ActionsDismissed -> setState { copy(actionNoteId = null) }

            is LibraryEvent.PinnedToggled -> mutate {
                val note = findNote(event.noteId) ?: return@mutate
                noteRepository.setPinned(event.noteId, !note.isPinned)
                setState { copy(actionNoteId = null) }
            }

            is LibraryEvent.FavoriteToggled -> mutate {
                val note = findNote(event.noteId) ?: return@mutate
                noteRepository.setFavorite(event.noteId, !note.isFavorite)
            }

            LibraryEvent.NewNoteClicked -> createNote()

            LibraryEvent.NewDiaryClicked -> sendEffect(LibraryEffect.OpenDiary)

            LibraryEvent.QuickCaptureClicked -> sendEffect(LibraryEffect.OpenQuickCapture(null))

            LibraryEvent.FabLongPressed -> setState { copy(fabMenuOpen = true) }

            LibraryEvent.FabMenuDismissed -> setState { copy(fabMenuOpen = false) }

            LibraryEvent.DrawerOpened -> setState { copy(drawerOpen = true) }

            LibraryEvent.DrawerClosed -> setState { copy(drawerOpen = false, actionNoteId = null) }

            is LibraryEvent.FolderToggled -> expandedFolders.update { open ->
                if (event.path in open) open - event.path else open + event.path
            }

            is LibraryEvent.FolderSelected -> setState {
                copy(
                    activeFolderPath = event.path,
                    filter = if (event.path == null) filter else NoteFilter.FOLDER,
                    drawerOpen = false,
                    actionNoteId = null,
                )
            }

            is LibraryEvent.RenameRequested -> {
                val note = findNote(event.noteId) ?: return
                setState {
                    copy(dialog = LibraryDialog.Rename(event.noteId, note.title), actionNoteId = null)
                }
            }

            is LibraryEvent.RenameConfirmed -> mutate {
                val title = event.newTitle.trim()
                if (title.isEmpty()) return@mutate
                noteRepository.renameNote(event.noteId, title)
                setState { copy(dialog = null, actionNoteId = null) }
            }

            is LibraryEvent.MoveRequested -> {
                val folders = currentState.treeEntries
                    .filterIsInstance<TreeEntry.Folder>()
                    .map { it.path }
                setState {
                    copy(
                        dialog = LibraryDialog.Move(event.noteId, findNote(event.noteId)?.title.orEmpty(), folders),
                        actionNoteId = null,
                    )
                }
            }

            is LibraryEvent.MoveConfirmed -> mutate {
                noteRepository.moveNote(event.noteId, event.targetFolder)
                setState { copy(dialog = null) }
            }

            is LibraryEvent.DeleteRequested -> {
                val note = findNote(event.noteId) ?: return
                setState {
                    copy(
                        dialog = LibraryDialog.Delete(event.noteId, note.title),
                        actionNoteId = null,
                    )
                }
            }

            is LibraryEvent.DeleteConfirmed -> mutate {
                noteRepository.deleteNote(event.noteId)
                setState { copy(dialog = null) }
            }

            is LibraryEvent.NewFolderRequested ->
                setState { copy(dialog = LibraryDialog.NewFolder(event.parentPath)) }

            is LibraryEvent.NewFolderConfirmed -> mutate {
                val name = event.name.trim()
                if (name.isEmpty()) return@mutate
                repoRepository.createFolder(event.parentPath, name)
                setState { copy(dialog = null) }
                expandedFolders.update { it + event.parentPath }
            }

            is LibraryEvent.DialogDismissed -> setState { copy(dialog = null) }

            LibraryEvent.RefreshRequested -> refresh(force = false)

            LibraryEvent.RebuildIndexRequested -> refresh(force = true)

            LibraryEvent.CreateFirstNoteClicked -> createNote()

            LibraryEvent.CreateSampleNoteClicked -> mutate {
                repoRepository.ensureStarterContent()
                refresh(force = false)
            }

            is LibraryEvent.MessageShown -> {
                setState { copy(message = event.message) }
                messageJob?.cancel()
                messageJob = viewModelScope.launch {
                    delay(MESSAGE_DURATION_MS)
                    setState { copy(message = null) }
                }
            }

            LibraryEvent.MessageDismissed -> setState { copy(message = null) }

            is LibraryEvent.TabSelected -> Unit // handled by the navigation host
        }
    }

    /**
     * Creates a note in the repository root, next to the folders rather than inside whichever folder
     * happens to be filtered at the time.
     *
     * A new note going into the folder you are browsing sounds helpful, but it makes the same button
     * put things in two different places — the drawer's 新建文件夹 always uses the root — and a note
     * filed somewhere the user did not choose is worse than one they have to move.
     */
    private fun createNote() {
        viewModelScope.launch {
            try {
                val note = noteRepository.createNote("未命名笔记")
                sendEffect(LibraryEffect.OpenNote(note.id))
            } catch (e: com.cycling.mynote.core.error.DataError) {
                sendEffect(LibraryEffect.ShowMessage(e.message ?: "新建笔记失败"))
            }
        }
    }

    private fun refresh(force: Boolean) {
        viewModelScope.launch {
            when (val outcome = repoRepository.refreshIndex(force = force)) {
                is IndexOutcome.Success -> setState {
                    copy(
                        externalChanges = outcome.externallyModified,
                        lastRefreshedAt = System.currentTimeMillis(),
                    )
                }

                is IndexOutcome.Failure -> sendEffect(
                    LibraryEffect.ShowMessage(outcome.error.message ?: "刷新失败"),
                )
            }
        }
    }

    private fun findNote(noteId: String): Note? =
        currentState.notes.firstOrNull { it.note.id == noteId }?.note

    private inline fun mutate(crossinline block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: com.cycling.mynote.core.error.DataError) {
                sendEffect(LibraryEffect.ShowMessage(e.message ?: "操作失败"))
            }
        }
    }

    /** One frame's worth of everything the library renders. */
    private companion object {

        /** Long enough to read a short failure, short enough not to sit over the list. */
        const val MESSAGE_DURATION_MS = 4_000L

        fun NoteSort.next(): NoteSort = NoteSort.entries[(ordinal + 1) % NoteSort.entries.size]

        fun NoteSort.comparator(): Comparator<Note> = when (this) {
            NoteSort.UPDATED_DESC -> compareByDescending { it.modifiedAt }
            NoteSort.UPDATED_ASC -> compareBy { it.modifiedAt }
            NoteSort.TITLE_ASC -> compareBy { it.title.lowercase() }
            NoteSort.CREATED_DESC -> compareByDescending { it.modifiedAt }
        }

        /**
         * The library's own filtering.
         *
         * Applied here rather than in the repository query because the index stream is already
         * flowing into this screen and re-subscribing per chip change would re-read the database
         * on every tap.
         */
        fun Note.matchesFilter(filter: NoteFilter, activeFolderPath: String?): Boolean {
            val inFolder = activeFolderPath?.let { folder ->
                this.folder == folder || this.folder.startsWith("$folder/")
            } ?: true
            if (!inFolder) return false

            return when (filter) {
                NoteFilter.ALL, NoteFilter.RECENT -> true
                NoteFilter.TAGGED -> tags.isNotEmpty()
                NoteFilter.FAVORITE -> isFavorite
                NoteFilter.FOLDER -> true
            }
        }
    }
}
