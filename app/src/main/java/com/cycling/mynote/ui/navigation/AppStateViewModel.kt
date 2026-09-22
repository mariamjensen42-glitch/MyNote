package com.cycling.mynote.ui.navigation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.mynote.data.repo.RepoSession
import com.cycling.mynote.data.share.ShareInbox
import com.cycling.mynote.domain.repository.RepoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * What the navigation host needs to decide what to show before any screen is composed.
 *
 * @param ready false until the persisted folder grant has been read. Without it the app would
 *   render onboarding for a frame and then jump to the library.
 * @param repoGranted whether a repository is currently available, so a release can route back.
 * @param pendingShare whether another app has handed the app text to capture.
 */
@Immutable
data class AppState(
    val ready: Boolean = false,
    val repoGranted: Boolean = false,
    val pendingShare: Boolean = false,
)

@HiltViewModel
class AppStateViewModel @Inject constructor(
    repoRepository: RepoRepository,
    session: RepoSession,
    shareInbox: ShareInbox,
) : ViewModel() {

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        combine(
            session.isLoaded,
            repoRepository.observeRepo(),
            shareInbox.pending,
        ) { loaded, repo, share -> Triple(loaded, repo, share) }
            .onEach { (loaded, repo, share) ->
                _state.value = AppState(
                    ready = loaded,
                    repoGranted = repo != null,
                    pendingShare = share != null,
                )
            }
            .launchIn(viewModelScope)
    }
}
