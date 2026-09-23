package com.cycling.mynote.ui.editor

import android.graphics.Bitmap

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.mynote.R
import com.cycling.mynote.core.model.EditorViewMode
import com.cycling.mynote.markdown.MarkdownCommand
import com.cycling.mynote.markdown.MarkdownCommands
import com.cycling.mynote.markdown.MarkdownDocument
import com.cycling.mynote.markdown.MarkdownEdit
import com.cycling.mynote.markdown.MarkdownParser
import com.cycling.mynote.markdown.MarkdownSourceEditor
import com.cycling.mynote.ui.components.MyNoteIconButton
import com.cycling.mynote.ui.components.MyNoteScreen
import com.cycling.mynote.ui.components.MyNoteSegmentedIconControl
import com.cycling.mynote.ui.components.SaveChip
import com.cycling.mynote.ui.icons.MyNoteIcons
import com.cycling.mynote.ui.markdown.MarkdownPreview
import com.cycling.mynote.ui.markdown.MarkdownSyntaxHighlighter
import com.cycling.mynote.ui.markdown.MarkdownTypeScale
import com.cycling.mynote.ui.markdown.iconFor
import com.cycling.mynote.ui.mvi.EffectCollector
import com.cycling.mynote.ui.theme.MyNoteTheme
import kotlinx.coroutines.launch

/**
 * The Markdown editor.
 *
 * Three view modes share one draft: `编辑` shows the syntax-highlighted source, `预览` renders the
 * parsed document, and `分屏` shows both. All three read the same [EditorState.raw], so switching
 * modes never round-trips through the file.
 *
 * The design gives the note no metadata panel: the front matter is part of the source, so tags,
 * `pinned` and `favorite` are read and edited where they live, and nothing else in the screen has to
 * mirror them back.
 *
 * The screen owns the cursor, the view model owns the text, and every edit comes from
 * [com.cycling.mynote.markdown]: the format toolbar, the checkbox in the preview, Enter inside a list
 * and Tab on a selection are all one pure function of `(text, selection)` applied to the field. That
 * is what keeps the view model free of a UI type while the field stays the only thing that knows
 * where the caret is.
 */
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MyNoteTheme.colors
    val dimens = MyNoteTheme.dimens

    // The draft is local so the caret survives; it is re-seeded whenever a different note loads.
    var field by remember(state.noteId) { mutableStateOf(TextFieldValue(state.raw)) }
    LaunchedEffect(state.noteId, state.loading) {
        if (!state.loading) field = TextFieldValue(state.raw)
    }

    EffectCollector(viewModel.effects) { effect ->
        when (effect) {
            EditorEffect.NavigateBack -> onBack()
            is EditorEffect.ShowMessage -> Unit
        }
    }

    val highlighter = remember(colors) {
        MarkdownSyntaxHighlighter(
            accent = colors.accent,
            textPrimary = colors.textPrimary,
            textSecondary = colors.textSecondary,
            textTertiary = colors.textTertiary,
            codeBackground = colors.surfaceSunken,
        )
    }
    val document = remember(state.raw) { MarkdownParser.parse(state.raw) }
    val scale = MarkdownTypeScale.of(state.settings)
    val mono = MyNoteTheme.text.monoBody
    val sourceStyle = remember(scale, mono, colors) {
        scale.applyTo(mono).copy(color = colors.textPrimary)
    }

    // Ticking a checkbox in the preview is an edit like any other, so it lands in the same field the
    // source view types into and autosave behaves exactly as it does for a keystroke.
    val applyEdit: (MarkdownEdit) -> Unit = { edit ->
        field = field.applying(edit)
        viewModel.onEvent(EditorEvent.BodyChanged(edit.text))
    }

    val scope = rememberCoroutineScope()
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked == null) return@rememberLauncherForActivityResult
        scope.launch {
            // File the picture first, then write the reference to it. If filing fails the note still
            // gets the syntax, which the user can point at a path themselves.
            val reference = viewModel.attachImage(picked.toString())
            val caret = field.selection.min..field.selection.max
            applyEdit(
                if (reference == null) {
                    MarkdownCommands.apply(
                        command = MarkdownCommands.ALL.single { it.id == "image" },
                        text = field.text,
                        start = caret.first,
                        end = caret.last,
                    )
                } else {
                    MarkdownSourceEditor.attachImage(field.text, caret.first, caret.last, reference)
                },
            )
        }
    }

    // Every keystroke, plus the one thing that has to be inferred from it: Enter inside a list. A
    // soft keyboard never sends Enter as a key event — the IME commits the line break straight into
    // the field — so continuing the list has to hang off the text change to work on both.
    val onDraftChange: (TextFieldValue) -> Unit = { updated ->
        val previous = field
        field = updated
        viewModel.onEvent(EditorEvent.BodyChanged(updated.text))
        if (updated.insertedLineBreakAfter(previous)) {
            val edit = MarkdownSourceEditor.continueList(updated.text, updated.selection.start)
            if (edit != null) applyEdit(edit)
        }
    }

    MyNoteScreen {
        EditorTopBar(
            title = state.title,
            fileName = state.fileName,
            saved = state.saved,
            saving = state.saving,
            viewMode = state.viewMode,
            onBack = { viewModel.onEvent(EditorEvent.BackRequested) },
            onViewMode = { viewModel.onEvent(EditorEvent.ViewModeSelected(it)) },
        )

        EditorSurface(
            state = state,
            document = document,
            field = field,
            highlighter = highlighter,
            sourceStyle = sourceStyle,
            onFieldChange = onDraftChange,
            onEdit = applyEdit,
            loadImage = viewModel::loadImage,
            onToggleTask = { line ->
                MarkdownSourceEditor.toggleTask(field.text, line)?.let { rewritten ->
                    applyEdit(MarkdownEdit(rewritten, field.selection.start))
                }
            },
            modifier = Modifier.weight(1f),
        )

        FormatToolbar(
            onCommand = { command ->
                // 图片 is the one action that needs something from outside the note: a picture to
                // file. Everything else is a pure edit of the text.
                if (command.id == "image") {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } else {
                    applyEdit(
                        MarkdownCommands.apply(
                            command = command,
                            text = field.text,
                            start = field.selection.min,
                            end = field.selection.max,
                        ),
                    )
                }
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
}

private fun TextFieldValue.applying(edit: MarkdownEdit): TextFieldValue = TextFieldValue(
    text = edit.text,
    selection = TextRange(
        edit.selectionStart.coerceIn(0, edit.text.length),
        edit.selectionEnd.coerceIn(0, edit.text.length),
    ),
)

/**
 * True when this value differs from [previous] only by a line break inserted where the caret is —
 * one more character, a caret just past it, and the text on either side untouched.
 *
 * Being this strict is what keeps the list continuation from firing on a paste, on a deletion, or on
 * anything a user would not read as "pressed Enter here".
 */
private fun TextFieldValue.insertedLineBreakAfter(previous: TextFieldValue): Boolean {
    if (!selection.collapsed) return false
    if (text.length != previous.text.length + 1) return false
    val caret = selection.start
    if (caret <= 0 || text[caret - 1] != '\n') return false
    return text.regionMatches(0, previous.text, 0, caret - 1) &&
        text.regionMatches(caret, previous.text, caret - 1, text.length - caret)
}

/**
 * The header: back, then who the note is — title, file and save state — and the view-mode switch.
 *
 * The mode switch sits up here rather than beside the note surface so the writing area is nothing
 * but the text, which is what the design's editor frame shows.
 */
@Composable
private fun EditorTopBar(
    title: String,
    fileName: String,
    saved: Boolean,
    saving: Boolean,
    viewMode: EditorViewMode,
    onBack: () -> Unit,
    onViewMode: (EditorViewMode) -> Unit,
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
        horizontalArrangement = Arrangement.spacedBy(dimens.gapMedium),
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
                Text(
                    text = fileName,
                    style = MyNoteTheme.text.monoMicro,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                SaveChip(saved = saved, saving = saving)
            }
        }

        MyNoteSegmentedIconControl(
            options = listOf(
                MyNoteIcons.pencilLine to EditorViewMode.SOURCE.label,
                MyNoteIcons.columns2 to EditorViewMode.SPLIT.label,
                MyNoteIcons.eye to EditorViewMode.PREVIEW.label,
            ),
            selectedIndex = viewMode.ordinal,
            onSelect = { onViewMode(EditorViewMode.entries[it]) },
        )
    }
}

/**
 * The note surface: the source, the preview, or both.
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
    sourceStyle: TextStyle,
    onFieldChange: (TextFieldValue) -> Unit,
    onEdit: (MarkdownEdit) -> Unit,
    onToggleTask: (Int) -> Unit,
    loadImage: suspend (String) -> Bitmap?,
    modifier: Modifier = Modifier,
) {
    val colors = MyNoteTheme.colors
    val uriHandler = LocalUriHandler.current
    val openLink = remember(uriHandler) { { url: String -> uriHandler.openUri(url) } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface),
    ) {
        when (state.viewMode) {
            EditorViewMode.SOURCE -> SourceEditor(
                value = field,
                highlighter = highlighter,
                textStyle = sourceStyle,
                softWrap = state.settings.softWrap,
                onValueChange = onFieldChange,
                onEdit = onEdit,
                modifier = Modifier.weight(1f),
            )

            EditorViewMode.PREVIEW -> MarkdownPreview(
                document = document,
                settings = state.settings,
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                onToggleTask = onToggleTask,
                onLinkClick = openLink,
                loadImage = loadImage,
            )

            EditorViewMode.SPLIT -> Row(modifier = Modifier.weight(1f)) {
                SourceEditor(
                    value = field,
                    highlighter = highlighter,
                    textStyle = sourceStyle,
                    softWrap = state.settings.softWrap,
                    onValueChange = onFieldChange,
                    onEdit = onEdit,
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
                    onToggleTask = onToggleTask,
                    onLinkClick = openLink,
                    loadImage = loadImage,
                )
            }
        }
    }
}

/**
 * The syntax-highlighted input itself.
 *
 * It also owns the two keys a plain text field cannot know about: Enter inside a list continues it,
 * and Tab indents the selected lines. Both are offered to
 * [com.cycling.mynote.markdown.MarkdownSourceEditor] first and fall through to the field when the
 * caret is not somewhere they apply, so Enter at the end of a paragraph is still just a newline.
 *
 * With `软换行` off the field is laid out wider than the screen instead of wrapping, and the row
 * around it scrolls sideways. The width comes from the longest line's character count times the
 * monospace advance, measured rather than guessed: an exact width means the caret never sits past
 * the end of the text, which is what would let the field scroll somewhere it should not.
 */
@Composable
private fun SourceEditor(
    value: TextFieldValue,
    highlighter: MarkdownSyntaxHighlighter,
    textStyle: TextStyle,
    softWrap: Boolean,
    onValueChange: (TextFieldValue) -> Unit,
    onEdit: (MarkdownEdit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MyNoteTheme.colors
    val fieldModifier = modifier
        .fillMaxSize()
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            // Tab is the one editing key a soft keyboard has no equivalent for, so it is handled
            // here; Enter is not, because on a phone it arrives as text rather than as a key.
            if (event.key != Key.Tab) return@onPreviewKeyEvent false
            onEdit(
                MarkdownSourceEditor.indent(
                    text = value.text,
                    start = value.selection.min,
                    end = value.selection.max,
                    outdent = event.isShiftPressed,
                ),
            )
            true
        }
        .padding(horizontal = 14.dp, vertical = 13.dp)

    val content: @Composable (Modifier) -> Unit = { layout ->
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = layout,
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.accent),
            visualTransformation = highlighter,
            decorationBox = { inner ->
                if (value.text.isEmpty()) {
                    Text(
                        text = "开始写点什么…",
                        style = textStyle,
                        color = colors.textTertiary,
                    )
                }
                inner()
            },
        )
    }

    if (softWrap) {
        content(fieldModifier)
    } else {
        val measurer = rememberTextMeasurer()
        val advance = remember(measurer, textStyle) { measurer.measure("0", textStyle).size.width.toFloat() }
        val width = remember(value.text, advance) {
            val longest = value.text.split('\n').maxOfOrNull(String::length) ?: 0
            (longest * advance).toInt() + 64
        }
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            content(fieldModifier.width(androidx.compose.ui.unit.Dp(width.toFloat())))
        }
    }
}

/** The nine format actions, each wrapping or prefixing the current selection. */
@Composable
private fun FormatToolbar(onCommand: (MarkdownCommand) -> Unit) {
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
            MarkdownCommands.ALL.forEach { command ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(dimens.radiusSmall))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onCommand(command) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = iconFor(command),
                        contentDescription = command.label,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(dimens.iconMedium),
                    )
                }
            }
        }
    }
}
