package com.cycling.mynote.ui.onboarding

import android.net.Uri
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.mynote.R
import com.cycling.mynote.ui.components.MyNoteDivider
import com.cycling.mynote.ui.components.MyNoteIconTile
import com.cycling.mynote.ui.components.MyNotePrimaryButton
import com.cycling.mynote.ui.components.MyNoteScreen
import com.cycling.mynote.ui.components.MyNoteSecondaryButton
import com.cycling.mynote.ui.components.MyNoteSwitch
import com.cycling.mynote.ui.icons.MyNoteIcons
import com.cycling.mynote.ui.mvi.EffectCollector
import com.cycling.mynote.ui.theme.MyNoteTheme

/**
 * The first-run screen: pick the folder the notes live in.
 *
 * The system folder picker is launched through [ActivityResultContracts.OpenDocumentTree] rather
 * than `ACTION_OPEN_DOCUMENT_TREE` by hand, so the contract handles the result and the
 * configuration-change case for us. The returned URI is passed straight to the view model, which
 * takes the persistable grant — a URI handed back to a screen is only valid for as long as the
 * activity that received it, so it must not be held here.
 */
@Composable
fun RepoAuthScreen(
    onAuthorized: () -> Unit,
    viewModel: RepoAuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        viewModel.onEvent(RepoAuthEvent.FolderPicked(uri?.toString()))
    }

    EffectCollector(viewModel.effects) { effect ->
        when (effect) {
            is RepoAuthEffect.LaunchFolderPicker ->
                // The contract forwards this as the picker's EXTRA_INITIAL_URI, which is what turns
                // "pick a folder" into "confirm this folder".
                picker.launch(effect.initialUri?.let(Uri::parse))

            RepoAuthEffect.OpenLibrary -> onAuthorized()
            is RepoAuthEffect.ShowMessage -> Unit // rendered from state.error below
        }
    }

    MyNoteScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = dimens.gapExtraLarge,
                    end = dimens.gapExtraLarge,
                    top = 8.dp,
                    bottom = 40.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(dimens.gapHero),
        ) {
            Brand()

            Column(verticalArrangement = Arrangement.spacedBy(dimens.gapRegular)) {
                Text(
                    text = stringResource(R.string.onboarding_title),
                    style = MyNoteTheme.text.displayTitle,
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(R.string.onboarding_body),
                    style = MyNoteTheme.text.body,
                    color = colors.textSecondary,
                )
            }

            // The card always describes what the primary button below will use, so the two can
            // never disagree. A previously used folder is offered as the secondary "continue" action.
            state.defaultLocation?.let { location ->
                RepoCard(
                    title = stringResource(R.string.onboarding_default_location),
                    pathLabel = location.pathLabel,
                    icon = MyNoteIcons.folderOpen,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(dimens.gapTiny),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_sample_title),
                        style = MyNoteTheme.text.rowLabelStrong,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = stringResource(R.string.onboarding_sample_subtitle),
                        style = MyNoteTheme.text.screenSubtitle,
                        color = colors.textTertiary,
                    )
                }
                MyNoteSwitch(
                    checked = state.createSampleNote,
                    onCheckedChange = { viewModel.onEvent(RepoAuthEvent.CreateSampleNoteChanged(it)) },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(dimens.gapRegular)) {
                MyNotePrimaryButton(
                    label = stringResource(R.string.onboarding_use_default),
                    onClick = { viewModel.onEvent(RepoAuthEvent.UseDefaultLocationClicked) },
                    enabled = !state.granting,
                )
                MyNoteSecondaryButton(
                    label = stringResource(R.string.onboarding_choose_folder),
                    onClick = { viewModel.onEvent(RepoAuthEvent.ChooseFolderClicked) },
                    enabled = !state.granting,
                )
                state.candidate?.let { candidate ->
                    MyNoteSecondaryButton(
                        label = stringResource(R.string.onboarding_continue_last, candidate.name),
                        onClick = { viewModel.onEvent(RepoAuthEvent.ContinueLastClicked) },
                        enabled = !state.granting,
                        leadingIcon = MyNoteIcons.history,
                    )
                }
            }

            Text(
                text = stringResource(R.string.onboarding_permission_hint),
                style = MyNoteTheme.text.hint,
                color = colors.textTertiary,
            )

            state.error?.let { message ->
                Text(
                    text = message,
                    style = MyNoteTheme.text.caption,
                    color = colors.danger,
                )
            }
        }
    }
}

@Composable
private fun Brand() {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gapRegular),
    ) {
        MyNoteIconTile(
            icon = MyNoteIcons.feather,
            size = 36.dp,
            tint = colors.textPrimary,
            background = colors.surfaceSunken,
            iconSize = 19.dp,
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MyNoteTheme.text.appName,
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun RepoCard(
    title: String,
    pathLabel: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusMedium)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border, shape)
            .padding(dimens.cardPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.gapRegular),
        ) {
            MyNoteIconTile(
                icon = icon,
                background = colors.accentTint,
                tint = colors.accent,
                iconSize = 19.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(dimens.gapTiny),
            ) {
                Text(
                    text = title,
                    style = MyNoteTheme.text.screenSubtitle,
                    color = colors.textTertiary,
                )
                Text(
                    text = pathLabel,
                    style = MyNoteTheme.text.rowValue,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        MyNoteDivider()

        StarterPreview()
    }
}

/** What the starter content will create, listed so the switch above it is not a leap of faith. */
@Composable
private fun StarterPreview() {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        listOf(
            Triple(MyNoteIcons.fileText, "Inbox.md", "今天要记下的三件事…"),
            Triple(MyNoteIcons.folder, "日记/", "按日期归档"),
        ).forEach { (icon, name, hint) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.gapMedium),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(dimens.iconCompact),
                )
                Text(
                    text = name,
                    style = MyNoteTheme.text.monoSmall,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(text = hint, style = MyNoteTheme.text.meta, color = colors.textTertiary)
            }
        }
    }
}