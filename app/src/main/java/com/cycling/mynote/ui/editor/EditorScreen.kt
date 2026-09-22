package com.cycling.mynote.ui.editor

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.mynote.R
import com.cycling.mynote.core.model.EditorViewMode
import com.cycling.mynote.data.markdown.MarkdownDocument
import com.cycling.mynote.data.markdown.MarkdownParser
import com.cycling.mynote.ui.components.MarkdownPreview
import com.cycling.mynote.ui.components.MyNoteAccentTag
import com.cycling.mynote.ui.components.MyNoteConfirmDialog
import com.cycling.mynote.ui.components.MyNoteDivider
import com.cycling.mynote.ui.components.MyNoteIconButton
import com.cycling.mynote.ui.components.MyNoteNameDialog
import com.cycling.mynote.ui.components.MyNoteScreen
import com.cycling.mynote.ui.components.MyNoteSwitch
import com.cycling.mynote.ui.components.MyNoteSegmentedIconControl
import com.cycling.mynote.ui.components.SaveChip
import com.cycling.mynote.ui.icons.MyNoteIcons
import com.cycling.mynote.ui.mvi.EffectCollector
import com.cycling.mynote.ui.theme.MyNoteTheme

/** One button of the format toolbar: the glyph and the syntax it wraps the selection in. */
private data class FormatAction(
    val icon: ImageVector,
    val label: String,
    val prefix: String,
    val suffix: String = "",
    val linePrefix: String? = null,
)

/**
 * The Markdown editor.
 *
 * Three view modes share one draft: `编辑` shows the syntax-highlighted source, `预览` renders the
 * parsed document, and `分屏` shows both. All three read the same [EditorState.raw], so switching
 * modes never round-trips through the file.
 *
 * The text field owns the cursor while the view model owns the text. That split is why the format
 * toolbar can edit the selection — it reads the field's own value — without the view model ever
 * holding a UI type.
 */
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens
    val context = LocalContext.current

    // The draft is local so the caret survives; it is re-seeded whenever a different note loads.
    var field by remember(state.noteId) { mutableStateOf(TextFieldValue(state.raw)) }
    LaunchedEffect(state.noteId, state.loading) {
        if (!state.loading) field = TextFieldValue(state.raw)
    }

    EffectCollector(viewModel.effects) { effect ->
        when (effect) {
            EditorEffect.NavigateBack -> onBack()
            is EditorEffect.CopyToClipboard -> {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("note", effect.text))
            }

            is EditorEffect.ShowMessage -> Unit
        }
    }

    val highlighter = remember(state.settings.font, colors) {
        MarkdownSyntaxHighlighter(
            accent = colors.accent,
            textPrimary = colors.textPrimary,
            textSecondary = colors.textSecondary,
            textTertiary = colors.textTertiary,
            codeBackground = colors.surfaceSunken,
        )
    }
    val document = remember(state.raw) { MarkdownParser.parse(state.raw) }

    MyNoteScreen {
        EditorTopBar(
            title = state.title,
            fileName = state.fileName,
            saved = state.saved,
            saving = state.saving,
            moreMenuOpen = state.moreMenuOpen,
            onBack = { viewModel.onEvent(EditorEvent.BackRequested) },
            onMore = { viewModel.onEvent(EditorEvent.MoreMenuToggled) },
        )

        if (state.moreMenuOpen) {
            EditorMoreMenu(
                isFavorite = state.frontMatter.isFavorite,
                isSaved = state.saved,
                onSave = { viewModel.onEvent(EditorEvent.SaveRequested) },
                onFavorite = { viewModel.onEvent(EditorEvent.FavoriteToggled) },
                onCopyPlainText = { viewModel.onEvent(EditorEvent.CopyPlainTextRequested) },
                onRename = { viewModel.onEvent(EditorEvent.RenameRequested) },
                onDelete = { viewModel.onEvent(EditorEvent.DeleteRequested) },
            )
        }

        EditorSurface(
            state = state,
            document = document,
            field = field,
            highlighter = highlighter,
            onFieldChange = { updated ->
                field = updated
                viewModel.onEvent(EditorEvent.BodyChanged(updated.text))
            },
            onViewMode = { viewModel.onEvent(EditorEvent.ViewModeSelected(it)) },
            modifier = Modifier.weight(1f),
        )

        MyNoteDivider()

        MetadataPanel(
            state = state,
            onPinToggled = { viewModel.onEvent(EditorEvent.PinToggled) },
            onAddTag = { viewModel.onEvent(EditorEvent.AddTagRequested) },
            onRemoveTag = { viewModel.onEvent(EditorEvent.TagRemoved(it)) },
        )

        FormatToolbar(
            onInsert = { action ->
                val (updated, _) = applyFormat(field, action)
                field = updated
                viewModel.onEvent(EditorEvent.BodyChanged(updated.text))
            },
        )

        state.error?.let { message ->
            Text(
                text = message,
                style = MyNoteTheme.text.caption,
                color = colors.danger,
                modifier = Modifier.padding(horizontal = dimens.gapLarge, vertical = dimens.gapCompact),
            )
        }
    }

    EditorDialogs(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun EditorTopBar(
    title: String,
    fileName: String,
    saved: Boolean,
    saving: Boolean,
    moreMenuOpen: Boolean,
    onBack: () -> Unit,
    onMore: () -> Unit,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = dimens.gapLarge,
                end = dimens.gapLarge,
                top = dimens.gapCompact,
                bottom = dimens.gapRegular,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gapRegular),
    ) {
        MyNoteIconButton(
            icon = MyNoteIcons.chevronLeft,
            contentDescription = stringResource(R.string.cd_back),
            onClick = onBack,
            tint = colors.textPrimary,
            iconSize = 22.dp,
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MyNoteTheme.text.noteTitleLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = MyNoteIcons.fileText,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(dimens.iconExtraSmall),
                )
                Text(
                    text = fileName,
                    style = MyNoteTheme.text.monoMicro,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                SaveChip(saved = saved, saving = saving)
            }
        }

        MyNoteIconButton(
            icon = MyNoteIcons.ellipsisVertical,
            contentDescription = stringResource(R.string.cd_more),
            onClick = onMore,
            tint = if (moreMenuOpen) colors.accent else colors.textSecondary,
        )
    }
}

@Composable
private fun EditorMoreMenu(
    isFavorite: Boolean,
    isSaved: Boolean,
    onSave: () -> Unit,
    onFavorite: () -> Unit,
    onCopyPlainText: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gapLarge)
            .clip(RoundedCornerShape(dimens.radiusMedium))
            .background(colors.surfaceSunken),
    ) {
        // The only way to write the file when 自动保存 is off in settings.
        if (!isSaved) {
            MoreMenuRow(
                icon = MyNoteIcons.save,
                label = "立即保存",
                tint = colors.accent,
                onClick = onSave,
            )
            MyNoteDivider()
        }
        MoreMenuRow(
            icon = MyNoteIcons.tag,
            label = if (isFavorite) "取消收藏" else "收藏",
            onClick = onFavorite,
        )
        MyNoteDivider()
        MoreMenuRow(
            icon = MyNoteIcons.wrapText,
            label = "复制纯文本",
            onClick = onCopyPlainText,
        )
        MyNoteDivider()
        MoreMenuRow(
            icon = MyNoteIcons.pencil,
            label = stringResource(R.string.action_rename),
            onClick = onRename,
        )
        MyNoteDivider()
        MoreMenuRow(
            icon = MyNoteIcons.trash2,
            label = stringResource(R.string.action_delete),
            tint = colors.danger,
            onClick = onDelete,
        )
    }
}

@Composable
private fun MoreMenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MyNoteTheme.colors.textPrimary,
) {
    val dimens = MyNoteTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.gapLarge, vertical = dimens.gapRegular),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.gapRegular),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(dimens.iconMedium),
        )
        Text(text = label, style = MyNoteTheme.text.rowLabel, color = tint)
    }
}

/**
 * The note surface: stats and the view-mode switch, then the source, the preview, or both.
 *
 * The preview is only parsed and composed in the modes that show it, so a keystroke in `编辑` mode
 * never pays for a Markdown parse.
 */
@Composable
private fun EditorSurface(
    state: EditorState,
    document: MarkdownDocument,
    field: TextFieldValue,
    highlighter: MarkdownSyntaxHighlighter,
    onFieldChange: (TextFieldValue) -> Unit,
    onViewMode: (EditorViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = dimens.gapCompact),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(
                    R.string.editor_stats,
                    state.stats.lineCount,
                    state.stats.characterCount,
                ),
                style = MyNoteTheme.text.meta,
                color = colors.textTertiary,
            )
            MyNoteSegmentedIconControl(
                options = listOf(
                    MyNoteIcons.pencilLine to EditorViewMode.SOURCE.label,
                    MyNoteIcons.columns2 to EditorViewMode.SPLIT.label,
                    MyNoteIcons.eye to EditorViewMode.PREVIEW.label,
                ),
                selectedIndex = state.viewMode.ordinal,
                onSelect = { onViewMode(EditorViewMode.entries[it]) },
            )
        }

        MyNoteDivider()

        when (state.viewMode) {
            EditorViewMode.SOURCE -> SourceEditor(field, highlighter, onFieldChange, Modifier.weight(1f))

            EditorViewMode.PREVIEW -> MarkdownPreview(
                document = document,
                settings = state.settings,
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            )

            EditorViewMode.SPLIT -> Row(modifier = Modifier.weight(1f)) {
                SourceEditor(
                    value = field,
                    highlighter = highlighter,
                    onValueChange = onFieldChange,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(colors.border),
                )
                MarkdownPreview(
                    document = document,
                    settings = state.settings,
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    contentPadding = 12.dp,
                )
            }
        }
    }
}

/** The syntax-highlighted input itself. */
@Composable
private fun SourceEditor(
    value: TextFieldValue,
    highlighter: MarkdownSyntaxHighlighter,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 13.dp),
        textStyle = MyNoteTheme.text.monoBody.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(colors.accent),
        visualTransformation = highlighter,
        decorationBox = { inner ->
            if (value.text.isEmpty()) {
                Text(
                    text = "开始写点什么…",
                    style = MyNoteTheme.text.monoBody,
                    color = colors.textTertiary,
                )
            }
            inner()
        },
    )
}

/** Tags, the pin switch, and the format badge. */
@Composable
private fun MetadataPanel(
    state: EditorState,
    onPinToggled: () -> Unit,
    onAddTag: () -> Unit,
    onRemoveTag: (String) -> Unit,
) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = dimens.gapRegular, start = dimens.gapLarge, end = dimens.gapLarge),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.gapCompact),
        ) {
            Text(
                text = stringResource(R.string.editor_tags),
                style = MyNoteTheme.text.screenSubtitle,
                color = colors.textSecondary,
            )
            state.frontMatter.tags.forEach { tag ->
                MyNoteAccentTag(text = tag, onClick = { onRemoveTag(tag) })
            }
            MyNoteAccentTag(
                text = stringResource(R.string.editor_add_tag),
                outlined = true,
                leadingIcon = MyNoteIcons.plus,
                onClick = onAddTag,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.gapCompact),
        ) {
            Text(
                text = stringResource(R.string.editor_pinned),
                style = MyNoteTheme.text.screenSubtitle,
                color = colors.textSecondary,
            )
            MyNoteSwitch(
                checked = state.frontMatter.isPinned,
                onCheckedChange = { onPinToggled() },
                modifier = Modifier.size(width = 40.dp, height = 24.dp),
            )
            Box(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.editor_format_badge),
                style = MyNoteTheme.text.monoMicro,
                color = colors.textTertiary,
            )
        }
    }
}

/** The ten format actions, each wrapping or prefixing the current selection. */
@Composable
private fun FormatToolbar(onInsert: (FormatAction) -> Unit) {
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = dimens.gapLarge, end = dimens.gapLarge, top = 14.dp, bottom = dimens.gapRegular),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.buttonHeight)
                .clip(RoundedCornerShape(dimens.radiusTab))
                .background(colors.surfaceSunken)
                .padding(horizontal = dimens.gapCompact),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FORMAT_ACTIONS.forEach { action ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(dimens.radiusSmall))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onInsert(action) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = action.label,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(dimens.iconMedium),
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorDialogs(state: EditorState, onEvent: (EditorEvent) -> Unit) {
    when (val dialog = state.dialog) {
        null -> Unit

        EditorDialog.Rename -> MyNoteNameDialog(
            title = stringResource(R.string.dialog_rename_title),
            initialValue = state.title,
            confirmLabel = stringResource(R.string.action_confirm),
            onConfirm = { onEvent(EditorEvent.RenameConfirmed(it)) },
            onDismissRequest = { onEvent(EditorEvent.DialogDismissed) },
        )

        EditorDialog.Delete -> MyNoteConfirmDialog(
            title = stringResource(R.string.dialog_delete_title),
            body = stringResource(R.string.dialog_delete_body, state.title),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { onEvent(EditorEvent.DeleteConfirmed) },
            onDismissRequest = { onEvent(EditorEvent.DialogDismissed) },
        )

        EditorDialog.AddTag -> MyNoteNameDialog(
            title = stringResource(R.string.editor_tags),
            initialValue = "",
            confirmLabel = stringResource(R.string.action_confirm),
            onConfirm = { onEvent(EditorEvent.TagConfirmed(it)) },
            onDismissRequest = { onEvent(EditorEvent.DialogDismissed) },
            placeholder = state.availableTags.firstOrNull().orEmpty(),
        )
    }
}

/**
 * Wraps or prefixes the selection.
 *
 * Inline actions wrap the selected text and leave the caret after the inserted marker; block
 * actions prefix the whole line the caret is on. With an empty selection the markers are inserted
 * with the caret between them, so typing continues inside the emphasis.
 */
private fun applyFormat(value: TextFieldValue, action: FormatAction): Pair<TextFieldValue, Int> {
    val text = value.text
    val start = value.selection.min.coerceIn(0, text.length)
    val end = value.selection.max.coerceIn(0, text.length)

    if (action.linePrefix != null) {
        val lineStart = text.lastIndexOf('\n', (start - 1).coerceAtLeast(0))
            .let { if (it < 0 || start == 0) 0 else it + 1 }
        val already = text.startsWith(action.linePrefix, lineStart)
        val updated = if (already) {
            text.removeRange(lineStart, lineStart + action.linePrefix.length)
        } else {
            text.substring(0, lineStart) + action.linePrefix + text.substring(lineStart)
        }
        val caret = (start + if (already) -action.linePrefix.length else action.linePrefix.length)
            .coerceIn(0, updated.length)
        return TextFieldValue(updated, TextRange(caret)) to caret
    }

    val selected = text.substring(start, end)
    val updated = text.substring(0, start) + action.prefix + selected + action.suffix + text.substring(end)
    val caret = if (selected.isEmpty()) {
        start + action.prefix.length
    } else {
        end + action.prefix.length + action.suffix.length
    }
    return TextFieldValue(updated, TextRange(caret)) to caret
}

/** Declared once so the toolbar's order and glyphs match the design exactly. */
private val FORMAT_ACTIONS = listOf(
    FormatAction(MyNoteIcons.heading1, "一级标题", "# ", linePrefix = "# "),
    FormatAction(MyNoteIcons.bold, "加粗", "**", "**"),
    FormatAction(MyNoteIcons.italic, "斜体", "*", "*"),
    FormatAction(MyNoteIcons.list, "无序列表", "- ", linePrefix = "- "),
    FormatAction(MyNoteIcons.listChecks, "任务列表", "- [ ] ", linePrefix = "- [ ] "),
    FormatAction(MyNoteIcons.textQuote, "引用", "> ", linePrefix = "> "),
    FormatAction(MyNoteIcons.code, "代码", "`", "`"),
    FormatAction(MyNoteIcons.link, "链接", "[", "](url)"),
    FormatAction(MyNoteIcons.image, "图片", "![", "](path)"),
)
