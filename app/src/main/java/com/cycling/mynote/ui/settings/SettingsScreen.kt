package com.cycling.mynote.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.mynote.R
import com.cycling.mynote.core.model.EditorFont
import com.cycling.mynote.core.model.ThemeMode
import com.cycling.mynote.ui.components.BottomTab
import com.cycling.mynote.ui.components.MyNoteBottomBar
import com.cycling.mynote.ui.components.MyNoteCard
import com.cycling.mynote.ui.components.MyNoteConfirmDialog
import com.cycling.mynote.ui.components.MyNoteDivider
import com.cycling.mynote.ui.components.MyNoteIconTile
import com.cycling.mynote.ui.components.MyNoteListPickerDialog
import com.cycling.mynote.ui.components.MyNoteScreen
import com.cycling.mynote.ui.components.MyNoteSegmentedTextControl
import com.cycling.mynote.ui.components.MyNoteTag
import com.cycling.mynote.ui.components.SectionLabel
import com.cycling.mynote.ui.components.SettingsRow
import com.cycling.mynote.ui.icons.MyNoteIcons
import com.cycling.mynote.ui.mvi.EffectCollector
import com.cycling.mynote.ui.theme.MyNoteTheme

/**
 * The settings screen.
 *
 * Rows are grouped into cards by concern, and every card is drawn with the same two composables —
 * [SettingsRow] and [MyNoteDivider] — so a new preference costs one row rather than a new layout.
 */
@Composable
fun SettingsScreen(
    onOpenLibrary: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDiary: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> viewModel.onEvent(SettingsEvent.RepoPicked(uri?.toString())) }

    EffectCollector(viewModel.effects) { effect ->
        when (effect) {
            SettingsEffect.LaunchFolderPicker -> picker.launch(null)
            SettingsEffect.OpenLibrary -> onOpenLibrary()
            SettingsEffect.OpenSearch -> onOpenSearch()
            SettingsEffect.OpenDiary -> onOpenDiary()
            is SettingsEffect.ShowMessage -> Unit
        }
    }

    MyNoteScreen(
        bottomBar = {
            MyNoteBottomBar(
                selected = BottomTab.SETTINGS,
                onSelect = { tab ->
                    when (tab) {
                        BottomTab.LIBRARY -> onOpenLibrary()
                        BottomTab.SEARCH -> onOpenSearch()
                        BottomTab.DIARY -> onOpenDiary()
                        BottomTab.SETTINGS -> Unit
                    }
                },
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = dimens.headerPaddingTop, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(dimens.gapSmall),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MyNoteTheme.text.screenTitle,
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.settings_subtitle),
                style = MyNoteTheme.text.screenSubtitle,
                color = colors.textSecondary,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = dimens.gapExtraLarge),
            verticalArrangement = Arrangement.spacedBy(dimens.gapExtraLarge),
        ) {
            SettingsSection(label = stringResource(R.string.settings_section_repo)) {
                RepoRow(
                    name = state.repo?.name.orEmpty(),
                    pathLabel = state.repo?.pathLabel.orEmpty(),
                    onChange = { viewModel.onEvent(SettingsEvent.ChangeRepoClicked) },
                )
                MyNoteDivider()
                SettingsRow(
                    label = stringResource(R.string.settings_local_files),
                    value = state.repoSummary,
                )
                MyNoteDivider()
                SettingsRow(
                    label = stringResource(R.string.settings_release),
                    detail = stringResource(R.string.settings_release_body),
                    onClick = { viewModel.onEvent(SettingsEvent.ReleaseRepoRequested) },
                    showChevron = true,
                )
            }

            SettingsSection(label = stringResource(R.string.settings_section_theme)) {
                MyNoteSegmentedTextControl(
                    options = THEME_OPTIONS,
                    selected = state.settings.themeMode,
                    label = { it.label },
                    icon = { themeIcon(it) },
                    onSelect = { viewModel.onEvent(SettingsEvent.ThemeSelected(it)) },
                )
            }

            SettingsSection(label = stringResource(R.string.settings_section_editor)) {
                SettingsRow(
                    label = stringResource(R.string.settings_font),
                    value = state.settings.font.label,
                    showChevron = true,
                    onClick = { viewModel.onEvent(SettingsEvent.FontRequested) },
                )
                MyNoteDivider()
                SettingsRow(
                    label = stringResource(R.string.settings_font_size),
                    value = "${state.settings.fontSizeSp} pt",
                    showChevron = true,
                    onClick = { viewModel.onEvent(SettingsEvent.FontSizeRequested) },
                )
                MyNoteDivider()
                SettingsRow(
                    label = stringResource(R.string.settings_line_height),
                    value = formatLineHeight(state.settings.lineHeight),
                    showChevron = true,
                    onClick = { viewModel.onEvent(SettingsEvent.LineHeightRequested) },
                )
                MyNoteDivider()
                SettingsRow(
                    label = stringResource(R.string.settings_soft_wrap),
                    switchChecked = state.settings.softWrap,
                    onSwitchChange = { viewModel.onEvent(SettingsEvent.SoftWrapToggled) },
                )
                MyNoteDivider()
                SettingsRow(
                    label = stringResource(R.string.settings_auto_save),
                    switchChecked = state.settings.autoSave,
                    onSwitchChange = { viewModel.onEvent(SettingsEvent.AutoSaveToggled) },
                )
            }

            SettingsSection(label = stringResource(R.string.settings_section_index)) {
                SettingsRow(
                    label = stringResource(R.string.settings_rebuild_index),
                    detail = viewModel.indexStatusLabel(),
                    leadingIcon = MyNoteIcons.database,
                    leadingTint = colors.textSecondary,
                    leadingBackground = colors.surfaceSunken,
                    showChevron = true,
                    onClick = { viewModel.onEvent(SettingsEvent.RebuildIndexClicked) },
                )
                MyNoteDivider()
                SettingsRow(
                    label = "只读取改动",
                    detail = "比较文件的修改时间与大小，只重读变化的部分",
                    onClick = { viewModel.onEvent(SettingsEvent.RefreshIndexClicked) },
                    showChevron = true,
                )
            }

            SettingsSection(label = stringResource(R.string.settings_section_about)) {
                SettingsRow(
                    label = stringResource(R.string.settings_about_app),
                    detail = stringResource(R.string.settings_about_body),
                    value = stringResource(R.string.settings_version, state.appVersion),
                    showChevron = true,
                    onClick = { viewModel.onEvent(SettingsEvent.AboutClicked) },
                )
            }
        }
    }

    SettingsDialogs(state = state, viewModel = viewModel)
}

/** A section label plus the card that holds its rows. */
@Composable
private fun SettingsSection(
    label: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val dimens = MyNoteTheme.dimens

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.gapMedium),
    ) {
        if (label != null) SectionLabel(text = label)
        MyNoteCard { content() }
    }
}

/** The repository identity row: icon, name, path, and the `更换` action. */
@Composable
private fun RepoRow(name: String, pathLabel: String, onChange: () -> Unit) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.rowPaddingHorizontal, vertical = dimens.rowPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gapRegular),
    ) {
        MyNoteIconTile(
            icon = MyNoteIcons.folderOpen,
            background = colors.accentTint,
            tint = colors.accent,
            iconSize = 19.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = name.ifEmpty { "尚未选择" },
                style = MyNoteTheme.text.rowLabelStrong,
                color = colors.textPrimary,
            )
            Text(
                text = pathLabel,
                style = MyNoteTheme.text.monoSmall,
                color = colors.textSecondary,
            )
        }
        MyNoteTag(text = stringResource(R.string.settings_change), onClick = onChange)
    }
}

@Composable
private fun SettingsDialogs(state: SettingsState, viewModel: SettingsViewModel) {
    when (val dialog = state.dialog) {
        null -> Unit

        SettingsDialog.Font -> MyNoteListPickerDialog(
            title = stringResource(R.string.settings_font),
            options = EditorFont.entries.map { it.label },
            emptyLabel = "默认",
            onSelect = { label ->
                val font = EditorFont.entries.firstOrNull { it.label == label }
                if (font != null) viewModel.onEvent(SettingsEvent.FontConfirmed(font))
            },
            onDismissRequest = { viewModel.onEvent(SettingsEvent.DialogDismissed) },
        )

        SettingsDialog.FontSize -> MyNoteListPickerDialog(
            title = stringResource(R.string.settings_font_size),
            options = viewModel.fontSizeOptions().map { "$it pt" },
            emptyLabel = "",
            onSelect = { label ->
                label.substringBefore(" ").toIntOrNull()?.let {
                    viewModel.onEvent(SettingsEvent.FontSizeConfirmed(it))
                }
            },
            onDismissRequest = { viewModel.onEvent(SettingsEvent.DialogDismissed) },
        )

        SettingsDialog.LineHeight -> MyNoteListPickerDialog(
            title = stringResource(R.string.settings_line_height),
            options = viewModel.lineHeightOptions().map { formatLineHeight(it) },
            emptyLabel = "",
            onSelect = { label ->
                label.toFloatOrNull()?.let {
                    viewModel.onEvent(SettingsEvent.LineHeightConfirmed(it))
                }
            },
            onDismissRequest = { viewModel.onEvent(SettingsEvent.DialogDismissed) },
        )

        SettingsDialog.ReleaseRepo -> MyNoteConfirmDialog(
            title = stringResource(R.string.settings_release),
            body = stringResource(R.string.settings_release_body),
            confirmLabel = stringResource(R.string.settings_release),
            destructive = false,
            onConfirm = { viewModel.onEvent(SettingsEvent.ReleaseRepoConfirmed) },
            onDismissRequest = { viewModel.onEvent(SettingsEvent.DialogDismissed) },
        )
    }
}

/** One decimal place, matching the design's `1.6`. */
private fun formatLineHeight(value: Float): String = String.format(java.util.Locale.US, "%.1f", value)

private fun themeIcon(mode: ThemeMode) = when (mode) {
    ThemeMode.LIGHT -> MyNoteIcons.sun
    ThemeMode.DARK -> MyNoteIcons.moon
    ThemeMode.SYSTEM -> MyNoteIcons.smartphone
}

/** The theme options in the order the design shows them. */
private val THEME_OPTIONS = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)
