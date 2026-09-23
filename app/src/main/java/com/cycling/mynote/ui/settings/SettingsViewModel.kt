package com.cycling.mynote.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.cycling.mynote.core.error.DataError
import com.cycling.mynote.core.model.EditorFont
import com.cycling.mynote.core.model.EditorSettings
import com.cycling.mynote.core.model.IndexState
import com.cycling.mynote.core.model.IndexStatus
import com.cycling.mynote.core.model.RepoInfo
import com.cycling.mynote.core.model.RepoStats
import com.cycling.mynote.core.model.ThemeMode
import com.cycling.mynote.core.util.ByteSizeFormatter
import com.cycling.mynote.core.util.RelativeTimeFormatter
import com.cycling.mynote.data.index.IndexOutcome
import com.cycling.mynote.domain.repository.RepoRepository
import com.cycling.mynote.domain.repository.SettingsRepository
import com.cycling.mynote.ui.mvi.MviViewModel
import com.cycling.mynote.ui.mvi.UiEffect
import com.cycling.mynote.ui.mvi.UiEvent
import com.cycling.mynote.ui.mvi.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** The pickers the settings screen can open. */
sealed interface SettingsDialog {
    data object Font : SettingsDialog

    data object FontSize : SettingsDialog

    data object LineHeight : SettingsDialog

    data object AttachmentFolder : SettingsDialog

    data object ReleaseRepo : SettingsDialog
}

@Immutable
data class SettingsState(
    val repo: RepoInfo? = null,
    val stats: RepoStats = RepoStats(0, 0, 0),
    val indexStatus: IndexStatus = IndexStatus(),
    val settings: EditorSettings = EditorSettings(),
    val appVersion: String = "",
    val dialog: SettingsDialog? = null,
    val busy: Boolean = false,
    val message: String? = null,
) : UiState {
    val repoSummary: String
        get() = "${stats.noteCount} 篇 · ${ByteSizeFormatter.format(stats.totalBytes)}"

    val isIndexing: Boolean get() = indexStatus.state == IndexState.BUILDING
}

sealed interface SettingsEvent : UiEvent {
    data object ChangeRepoClicked : SettingsEvent

    /** `null` when the user dismissed the system folder picker. */
    data class RepoPicked(val treeUri: String?) : SettingsEvent

    data object ReleaseRepoRequested : SettingsEvent

    data object ReleaseRepoConfirmed : SettingsEvent

    data class ThemeSelected(val mode: ThemeMode) : SettingsEvent

    data object FontRequested : SettingsEvent

    data class FontConfirmed(val font: EditorFont) : SettingsEvent

    data object FontSizeRequested : SettingsEvent

    data class FontSizeConfirmed(val sizeSp: Int) : SettingsEvent

    data object LineHeightRequested : SettingsEvent

    data class LineHeightConfirmed(val lineHeight: Float) : SettingsEvent

    data object AttachmentFolderRequested : SettingsEvent

    data class AttachmentFolderConfirmed(val folder: String) : SettingsEvent

    data object SoftWrapToggled : SettingsEvent

    data object AutoSaveToggled : SettingsEvent

    data object RebuildIndexClicked : SettingsEvent

    data object RefreshIndexClicked : SettingsEvent

    data object DialogDismissed : SettingsEvent

    data object AboutClicked : SettingsEvent

    data object MessageShown : SettingsEvent

    data class TabSelected(val index: Int) : SettingsEvent
}

sealed interface SettingsEffect : UiEffect {
    data object LaunchFolderPicker : SettingsEffect

    data object OpenLibrary : SettingsEffect

    data object OpenSearch : SettingsEffect

    data object OpenDiary : SettingsEffect

    data class ShowMessage(val message: String) : SettingsEffect
}

/**
 * Settings: the repository, the theme, the editor, the index.
 *
 * Every preference is written through [SettingsRepository] and read back from its flow rather than
 * being held locally, so the screen cannot display a value the app is not actually using — and the
 * editor, which observes the same flow, picks the change up without a restart.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repoRepository: RepoRepository,
    private val settingsRepository: SettingsRepository,
    private val timeFormatter: RelativeTimeFormatter,
    private val packageInfoProvider: PackageInfoProvider,
) : MviViewModel<SettingsState, SettingsEvent, SettingsEffect>(SettingsState()) {

    init {
        combine(
            repoRepository.observeRepo(),
            repoRepository.observeStats(),
            repoRepository.observeIndexStatus(),
            settingsRepository.observeSettings(),
        ) { repo, stats, indexStatus, settings -> Quad(repo, stats, indexStatus, settings) }
            .onEach { quad ->
                setState {
                    copy(
                        repo = quad.repo,
                        stats = quad.stats,
                        indexStatus = quad.indexStatus,
                        settings = quad.settings,
                    )
                }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            val version = packageInfoProvider.versionName()
            setState { copy(appVersion = version) }
        }
    }

    override fun onEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.ChangeRepoClicked -> sendEffect(SettingsEffect.LaunchFolderPicker)

            is SettingsEvent.RepoPicked -> event.treeUri?.let(::grantRepo)

            SettingsEvent.ReleaseRepoRequested -> setState { copy(dialog = SettingsDialog.ReleaseRepo) }

            SettingsEvent.ReleaseRepoConfirmed -> {
                setState { copy(dialog = null, busy = true) }
                viewModelScope.launch {
                    repoRepository.release()
                    setState { copy(busy = false) }
                    sendEffect(SettingsEffect.OpenLibrary)
                }
            }

            is SettingsEvent.ThemeSelected ->
                viewModelScope.launch { settingsRepository.setThemeMode(event.mode) }

            SettingsEvent.FontRequested -> setState { copy(dialog = SettingsDialog.Font) }

            is SettingsEvent.FontConfirmed -> {
                setState { copy(dialog = null) }
                viewModelScope.launch { settingsRepository.setFont(event.font) }
            }

            SettingsEvent.FontSizeRequested -> setState { copy(dialog = SettingsDialog.FontSize) }

            is SettingsEvent.FontSizeConfirmed -> {
                setState { copy(dialog = null) }
                viewModelScope.launch { settingsRepository.setFontSize(event.sizeSp) }
            }

            SettingsEvent.LineHeightRequested -> setState { copy(dialog = SettingsDialog.LineHeight) }

            is SettingsEvent.LineHeightConfirmed -> {
                setState { copy(dialog = null) }
                viewModelScope.launch { settingsRepository.setLineHeight(event.lineHeight) }
            }

            SettingsEvent.AttachmentFolderRequested ->
                setState { copy(dialog = SettingsDialog.AttachmentFolder) }

            is SettingsEvent.AttachmentFolderConfirmed -> {
                setState { copy(dialog = null) }
                if (event.folder.isNotBlank()) {
                    viewModelScope.launch { settingsRepository.setAttachmentFolder(event.folder) }
                }
            }

            SettingsEvent.SoftWrapToggled -> viewModelScope.launch {
                settingsRepository.setSoftWrap(!currentState.settings.softWrap)
            }

            SettingsEvent.AutoSaveToggled -> viewModelScope.launch {
                settingsRepository.setAutoSave(!currentState.settings.autoSave)
            }

            SettingsEvent.RebuildIndexClicked -> refreshIndex(force = true)

            SettingsEvent.RefreshIndexClicked -> refreshIndex(force = false)

            SettingsEvent.DialogDismissed -> setState { copy(dialog = null) }

            SettingsEvent.AboutClicked -> sendEffect(
                SettingsEffect.ShowMessage(
                    buildString {
                        append("纸间 v").append(currentState.appVersion)
                        append(" · 笔记是纯文本 .md，索引可随时重建")
                    },
                ),
            )

            SettingsEvent.MessageShown -> setState { copy(message = null) }

            is SettingsEvent.TabSelected -> Unit // handled by the navigation host
        }
    }

    /** The index row's live status line, e.g. `上次构建 5 分钟前 · 12 篇笔记`. */
    fun indexStatusLabel(): String {
        val status = currentState.indexStatus
        return when {
            status.state == IndexState.BUILDING -> "正在构建索引…"
            status.builtAt == null -> "尚未构建 · ${status.indexedNoteCount} 篇笔记"
            else -> "上次构建 ${timeFormatter.forNoteRow(status.builtAt!!)} · " +
                "${status.indexedNoteCount} 篇笔记"
        }
    }

    /** Available font sizes, as the strings the picker shows. */
    fun fontSizeOptions(): List<Int> = (
        EditorSettings.MIN_FONT_SIZE..EditorSettings.MAX_FONT_SIZE step EditorSettings.FONT_SIZE_STEP
        ).toList()

    /** Available line heights, rounded to one decimal so floating point noise never reaches the UI. */
    fun lineHeightOptions(): List<Float> {
        val steps = ((EditorSettings.MAX_LINE_HEIGHT - EditorSettings.MIN_LINE_HEIGHT) /
            EditorSettings.LINE_HEIGHT_STEP).toInt()
        return (0..steps).map { index ->
            val value = EditorSettings.MIN_LINE_HEIGHT + index * EditorSettings.LINE_HEIGHT_STEP
            Math.round(value * 10) / 10f
        }
    }

    private fun grantRepo(treeUri: String) {
        setState { copy(busy = true) }
        viewModelScope.launch {
            try {
                val outcome = repoRepository.grantAccess(treeUri)
                if (outcome is IndexOutcome.Failure) throw outcome.error
                sendEffect(SettingsEffect.ShowMessage("已切换到新的仓库"))
            } catch (e: DataError) {
                sendEffect(SettingsEffect.ShowMessage(e.message ?: "无法访问这个文件夹"))
            } finally {
                setState { copy(busy = false) }
            }
        }
    }

    private fun refreshIndex(force: Boolean) {
        viewModelScope.launch {
            val message = when (val outcome = repoRepository.refreshIndex(force = force)) {
                is IndexOutcome.Success -> if (force) {
                    "索引已重建 · ${outcome.indexed} 篇"
                } else {
                    "已刷新 · ${outcome.indexed} 处更新"
                }

                is IndexOutcome.Failure -> outcome.error.message ?: "索引更新失败"
            }
            sendEffect(SettingsEffect.ShowMessage(message))
        }
    }

    private data class Quad(
        val repo: RepoInfo?,
        val stats: RepoStats,
        val indexStatus: IndexStatus,
        val settings: EditorSettings,
    )
}
