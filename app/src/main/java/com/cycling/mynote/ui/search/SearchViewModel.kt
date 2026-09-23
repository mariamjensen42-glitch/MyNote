package com.cycling.mynote.ui.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.cycling.mynote.core.model.CommandItem
import com.cycling.mynote.core.model.SearchHit
import com.cycling.mynote.core.model.SearchScope
import com.cycling.mynote.core.model.ThemeMode
import com.cycling.mynote.core.util.RelativeTimeFormatter
import com.cycling.mynote.data.index.IndexOutcome
import com.cycling.mynote.domain.repository.NoteRepository
import com.cycling.mynote.domain.repository.RepoRepository
import com.cycling.mynote.domain.repository.SearchRepository
import com.cycling.mynote.domain.repository.SettingsRepository
import com.cycling.mynote.ui.library.LibraryIntent
import com.cycling.mynote.ui.library.LibraryIntents
import com.cycling.mynote.ui.mvi.MviViewModel
import com.cycling.mynote.ui.mvi.UiEffect
import com.cycling.mynote.ui.mvi.UiEvent
import com.cycling.mynote.ui.mvi.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** A hit plus its rendered age. */
@Immutable
data class SearchResultItem(val hit: SearchHit, val timeLabel: String)

@Immutable
data class SearchState(
    val query: String = "",
    val scope: SearchScope = SearchScope.ALL,
    val results: List<SearchResultItem> = emptyList(),
    // Count per scope, so the filter row's numbers always describe the same query as the list.
    val counts: Map<SearchScope, Int> = emptyMap(),
    val recentSearches: List<String> = emptyList(),
    val recentTags: List<String> = emptyList(),
    val commands: List<CommandItem> = emptyList(),
    val showAllResults: Boolean = false,
    val loading: Boolean = false,
) : UiState {
    /** A leading `>` turns the field into the command palette. */
    val isCommandMode: Boolean get() = query.trimStart().startsWith(">")

    val commandQuery: String get() = query.trimStart().removePrefix(">").trim()

    val visibleCommands: List<CommandItem>
        get() = if (commandQuery.isEmpty()) {
            commands
        } else {
            commands.filter {
                it.label.contains(commandQuery, ignoreCase = true) ||
                    it.description?.contains(commandQuery, ignoreCase = true) == true
            }
        }

    val visibleResults: List<SearchResultItem>
        get() = if (showAllResults) results else results.take(COLLAPSED_RESULT_COUNT)

    val canShowMore: Boolean get() = results.size > visibleResults.size

    val isEmpty: Boolean get() = query.isNotBlank() && !loading && !isCommandMode &&
        visibleResults.isEmpty()

    private companion object {
        /** How many results fit before the design's `查看全部` link appears. */
        const val COLLAPSED_RESULT_COUNT = 4
    }
}

sealed interface SearchEvent : UiEvent {
    data class QueryChanged(val query: String) : SearchEvent

    data class ScopeSelected(val scope: SearchScope) : SearchEvent

    data class NoteOpened(val noteId: String) : SearchEvent

    data class RecentSelected(val query: String) : SearchEvent

    data class TagSelected(val tag: String) : SearchEvent

    data class CommandSelected(val commandId: String) : SearchEvent

    data object ShowAllToggled : SearchEvent

    data object ClearQueryClicked : SearchEvent

    data object ClearRecentClicked : SearchEvent

    data class TabSelected(val index: Int) : SearchEvent
}

sealed interface SearchEffect : UiEffect {
    data class OpenNote(val noteId: String) : SearchEffect

    data object OpenLibrary : SearchEffect

    data object OpenDiary : SearchEffect

    data object OpenSettings : SearchEffect

    data class OpenQuickCapture(val noteId: String?) : SearchEffect

    data class ShowMessage(val message: String) : SearchEffect
}

/**
 * Search and the command palette.
 *
 * Both live in one screen because they share one input: a leading `>` switches the same field from
 * searching notes to listing commands, which is what the design's hint promises. Keeping them
 * together also means the palette inherits the search field's focus and keyboard behaviour for free.
 *
 * Queries are debounced before hitting the index so typing does not run a `LIKE` scan per keystroke.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val noteRepository: NoteRepository,
    private val repoRepository: RepoRepository,
    private val settingsRepository: SettingsRepository,
    private val timeFormatter: RelativeTimeFormatter,
    private val libraryIntents: LibraryIntents,
) : MviViewModel<SearchState, SearchEvent, SearchEffect>(SearchState()) {

    private var searchJob: Job? = null

    init {
        setState { copy(commands = searchRepository.commands()) }

        searchRepository.observeRecentSearches()
            .onEach { recents ->
                setState { copy(recentSearches = recents.map { it.query }) }
            }
            .launchIn(viewModelScope)

        searchRepository.observeRecentTags()
            .onEach { tags -> setState { copy(recentTags = tags) } }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.QueryChanged -> {
                setState { copy(query = event.query, showAllResults = false) }
                runSearch(event.query, currentState.scope)
            }

            is SearchEvent.ScopeSelected -> {
                setState { copy(scope = event.scope, showAllResults = false) }
                runSearch(currentState.query, event.scope)
            }

            is SearchEvent.NoteOpened -> {
                viewModelScope.launch { searchRepository.rememberSearch(currentState.query) }
                sendEffect(SearchEffect.OpenNote(event.noteId))
            }

            is SearchEvent.RecentSelected -> {
                setState { copy(query = event.query) }
                runSearch(event.query, currentState.scope)
            }

            is SearchEvent.TagSelected -> {
                val query = "#${event.tag}"
                setState { copy(query = query) }
                runSearch(query, currentState.scope)
            }

            is SearchEvent.CommandSelected -> executeCommand(event.commandId)

            SearchEvent.ShowAllToggled -> setState { copy(showAllResults = !showAllResults) }

            SearchEvent.ClearQueryClicked -> {
                setState { copy(query = "", results = emptyList(), counts = emptyMap()) }
            }

            SearchEvent.ClearRecentClicked -> viewModelScope.launch {
                searchRepository.clearRecentSearches()
            }

            is SearchEvent.TabSelected -> Unit // handled by the navigation host
        }
    }

    private fun runSearch(query: String, scope: SearchScope) {
        searchJob?.cancel()

        // The `#tag` form is stripped before matching: the index stores tags without the marker.
        val term = query.removePrefix("#").trim()
        if (term.isEmpty() || query.trimStart().startsWith(">")) {
            setState { copy(results = emptyList(), counts = emptyMap(), loading = false) }
            return
        }

        setState { copy(loading = true) }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val all = searchRepository.search(term, SearchScope.ALL)
            val formatted = all.map { SearchResultItem(it, timeFormatter.forSearchResult(it.modifiedAt)) }
            setState {
                copy(
                    loading = false,
                    results = if (scope == SearchScope.ALL) {
                        formatted
                    } else {
                        formatted.filter { it.hit.scope == scope }
                    },
                    counts = SearchScope.entries.associateWith { candidate ->
                        if (candidate == SearchScope.ALL) all.size else all.count { it.scope == candidate }
                    },
                )
            }
        }
    }

    private fun executeCommand(commandId: String) {
        viewModelScope.launch {
            when (commandId) {
                "new-note" -> runCatching { noteRepository.createNote("未命名笔记") }
                    .onSuccess { sendEffect(SearchEffect.OpenNote(it.id)) }
                    .onFailure { sendEffect(SearchEffect.ShowMessage("新建笔记失败")) }

                "new-diary" -> sendEffect(SearchEffect.OpenDiary)

                // Named in the library's own dialog rather than created here: a folder invented with
                // a fixed name could not be named, collided with itself, and looked like it had gone
                // somewhere unexpected.
                "new-folder" -> {
                    libraryIntents.request(LibraryIntent.NEW_FOLDER)
                    sendEffect(SearchEffect.OpenLibrary)
                }

                "rebuild-index" -> rebuildIndex(force = true)

                "refresh" -> rebuildIndex(force = false)

                "quick-capture" -> sendEffect(SearchEffect.OpenQuickCapture(null))

                "toggle-theme" -> {
                    val current = settingsRepository.observeSettings().first().themeMode
                    settingsRepository.setThemeMode(
                        if (current == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK,
                    )
                }

                "open-settings" -> sendEffect(SearchEffect.OpenSettings)
            }
        }
    }

    private fun rebuildIndex(force: Boolean) {
        viewModelScope.launch {
            val outcome = repoRepository.refreshIndex(force = force)
            val message = when (outcome) {
                is IndexOutcome.Success ->
                    if (force) {
                        "索引已重建 · ${outcome.indexed} 篇"
                    } else {
                        "已刷新 · ${outcome.indexed} 处更新"
                    }

                is IndexOutcome.Failure -> outcome.error.message ?: "索引更新失败"
            }
            sendEffect(SearchEffect.ShowMessage(message))
        }
    }

    private companion object {
        /** One frame's worth of typing, in effect: longer would feel laggy, shorter would thrash. */
        const val SEARCH_DEBOUNCE_MS = 160L
    }
}
