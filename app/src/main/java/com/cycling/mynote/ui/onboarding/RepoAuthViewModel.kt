package com.cycling.mynote.ui.onboarding

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.cycling.mynote.core.error.DataError
import com.cycling.mynote.data.index.IndexOutcome
import com.cycling.mynote.data.repo.DefaultRepoLocation
import com.cycling.mynote.data.repo.DefaultRepoLocationProvider
import com.cycling.mynote.di.IoDispatcher
import com.cycling.mynote.domain.repository.RepoRepository
import com.cycling.mynote.domain.repository.SettingsRepository
import com.cycling.mynote.ui.mvi.MviViewModel
import com.cycling.mynote.ui.mvi.UiEffect
import com.cycling.mynote.ui.mvi.UiEvent
import com.cycling.mynote.ui.mvi.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The previously used folder, offered for a one-tap re-grant. */
@Immutable
data class RepoPreview(
    val treeUri: String,
    val name: String,
    val pathLabel: String,
)

@Immutable
data class RepoAuthState(
    val defaultLocation: DefaultRepoLocation? = null,
    val candidate: RepoPreview? = null,
    val createSampleNote: Boolean = true,
    val granting: Boolean = false,
    val error: String? = null,
) : UiState

sealed interface RepoAuthEvent : UiEvent {
    /** Creates `Documents/Notes` and opens the picker on it. */
    data object UseDefaultLocationClicked : RepoAuthEvent

    /** Opens the picker without a starting folder. */
    data object ChooseFolderClicked : RepoAuthEvent

    /** `treeUri` is null when the user dismissed the system folder picker. */
    data class FolderPicked(val treeUri: String?) : RepoAuthEvent

    data object ContinueLastClicked : RepoAuthEvent

    data class CreateSampleNoteChanged(val enabled: Boolean) : RepoAuthEvent

    data object ErrorShown : RepoAuthEvent
}

sealed interface RepoAuthEffect : UiEffect {
    /** @param initialUri the folder the picker should open on, or null to let it decide. */
    data class LaunchFolderPicker(val initialUri: String?) : RepoAuthEffect

    data object OpenLibrary : RepoAuthEffect

    data class ShowMessage(val message: String) : RepoAuthEffect
}

/**
 * Drives the first-run screen.
 *
 * Granting a folder is the one operation in the app that can fail for a reason the user has to act
 * on — the grant was revoked, the folder was deleted, the provider refused — so a failure becomes
 * a message on this screen rather than a silent no-op: the screen has nothing else to fall back on.
 */
@HiltViewModel
class RepoAuthViewModel @Inject constructor(
    private val repoRepository: RepoRepository,
    private val settingsRepository: SettingsRepository,
    private val defaultLocationProvider: DefaultRepoLocationProvider,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MviViewModel<RepoAuthState, RepoAuthEvent, RepoAuthEffect>(RepoAuthState()) {

    init {
        setState { copy(defaultLocation = defaultLocationProvider.location()) }

        repoRepository.observeLastRepo()
            .onEach { last ->
                setState {
                    copy(
                        candidate = last?.let {
                            RepoPreview(treeUri = it.treeUri, name = it.displayName, pathLabel = it.displayName)
                        },
                    )
                }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            val createSample = settingsRepository.shouldCreateSampleNote()
            setState { copy(createSampleNote = createSample) }
        }
    }

    override fun onEvent(event: RepoAuthEvent) {
        when (event) {
            RepoAuthEvent.UseDefaultLocationClicked -> useDefaultLocation()

            RepoAuthEvent.ChooseFolderClicked ->
                sendEffect(RepoAuthEffect.LaunchFolderPicker(initialUri = null))

            RepoAuthEvent.ContinueLastClicked ->
                currentState.candidate?.let { grant(it.treeUri) }
                    ?: sendEffect(RepoAuthEffect.LaunchFolderPicker(initialUri = null))

            is RepoAuthEvent.FolderPicked -> event.treeUri?.let(::grant)

            is RepoAuthEvent.CreateSampleNoteChanged -> {
                setState { copy(createSampleNote = event.enabled) }
                viewModelScope.launch { settingsRepository.setCreateSampleNote(event.enabled) }
            }

            RepoAuthEvent.ErrorShown -> setState { copy(error = null) }
        }
    }

    /**
     * Seeds the default folder, then asks the user to confirm access to it.
     *
     * The seeding happens on a background thread because it talks to MediaStore, and the picker is
     * only launched once it has finished: the picker navigates to the folder by id, so opening it
     * before the folder exists would land the user nowhere useful.
     */
    private fun useDefaultLocation() {
        if (currentState.granting) return
        setState { copy(granting = true, error = null) }

        viewModelScope.launch {
            val seeded = withContext(ioDispatcher) { defaultLocationProvider.ensureSeeded() }
            setState { copy(granting = false) }

            if (!seeded) {
                // The device refused the write (or Documents is not the primary volume); fall back
                // to an unanchored picker rather than blocking the user on a folder we could not make.
                sendEffect(
                    RepoAuthEffect.ShowMessage(
                        "无法在 Documents 下创建默认文件夹，请手动选择",
                    ),
                )
                sendEffect(RepoAuthEffect.LaunchFolderPicker(initialUri = null))
                return@launch
            }

            sendEffect(
                RepoAuthEffect.LaunchFolderPicker(
                    initialUri = defaultLocationProvider.initialDocumentUri().toString(),
                ),
            )
        }
    }

    private fun grant(treeUri: String) {
        if (currentState.granting) return
        setState { copy(granting = true, error = null) }

        viewModelScope.launch {
            try {
                val outcome = repoRepository.grantAccess(treeUri)
                if (outcome is IndexOutcome.Failure) throw outcome.error
                if (currentState.createSampleNote) repoRepository.ensureStarterContent()
                sendEffect(RepoAuthEffect.OpenLibrary)
            } catch (e: DataError) {
                setState { copy(error = e.message) }
                sendEffect(RepoAuthEffect.ShowMessage(e.message ?: "无法访问这个文件夹"))
            } finally {
                setState { copy(granting = false) }
            }
        }
    }
}
