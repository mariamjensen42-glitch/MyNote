package com.cycling.mynote.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.cycling.mynote.R
import com.cycling.mynote.ui.components.MyNoteConfirmDialog
import com.cycling.mynote.ui.components.MyNoteListPickerDialog
import com.cycling.mynote.ui.components.MyNoteNameDialog

/**
 * Everything the library can pop up.
 *
 * The dialog is a single value in the screen state rather than a set of booleans, so two of them
 * can never be open at once and each one's dismissal is unambiguous.
 */
@Composable
fun LibraryDialogs(
    state: LibraryState,
    onEvent: (LibraryEvent) -> Unit,
) {
    when (val dialog = state.dialog) {
        null -> Unit

        is LibraryDialog.Rename -> MyNoteNameDialog(
            title = stringResource(R.string.dialog_rename_title),
            initialValue = dialog.currentTitle,
            confirmLabel = stringResource(R.string.action_confirm),
            onConfirm = { onEvent(LibraryEvent.RenameConfirmed(dialog.noteId, it)) },
            onDismissRequest = { onEvent(LibraryEvent.DialogDismissed(dialog)) },
        )

        is LibraryDialog.NewFolder -> MyNoteNameDialog(
            title = stringResource(R.string.dialog_new_folder_title),
            initialValue = "",
            confirmLabel = stringResource(R.string.action_confirm),
            onConfirm = { onEvent(LibraryEvent.NewFolderConfirmed(dialog.parentPath, it)) },
            onDismissRequest = { onEvent(LibraryEvent.DialogDismissed(dialog)) },
        )

        is LibraryDialog.Move -> MyNoteListPickerDialog(
            title = stringResource(R.string.dialog_move_title),
            options = listOf("") + dialog.folders,
            emptyLabel = stringResource(R.string.library_title),
            onSelect = { onEvent(LibraryEvent.MoveConfirmed(dialog.noteId, it)) },
            onDismissRequest = { onEvent(LibraryEvent.DialogDismissed(dialog)) },
        )

        is LibraryDialog.Delete -> MyNoteConfirmDialog(
            title = stringResource(R.string.dialog_delete_title),
            body = stringResource(R.string.dialog_delete_body, dialog.title),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { onEvent(LibraryEvent.DeleteConfirmed(dialog.noteId)) },
            onDismissRequest = { onEvent(LibraryEvent.DialogDismissed(dialog)) },
        )
    }
}
